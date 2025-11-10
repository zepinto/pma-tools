//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
/*
 * Copyright (c) 2004-2014 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Manuel R.
 * Oct 21, 2014
 */

package pt.omst.neptus.sidescan.sdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.omst.neptus.util.I18n;

public class SdfParser {
    private static final Logger LOG = LoggerFactory.getLogger(SdfParser.class);
    public final static int SUBSYS_LOW = 3501;
    public final static int SUBSYS_HIGH = 3502;
    // Minimum valid timestamp (2000-01-01 00:00:00).
    private static final long minimumValidTimestamp = 946684800000L;
    private File file;
    private FileInputStream fis;
    private FileChannel channel;
    private long curPosition = 0;

    private String indexPath;
    private final Map<Integer, Long[]> tslist = new ConcurrentHashMap<>();
    private final Map<Integer, Long> nextTimestamp = new ConcurrentHashMap<>();
    private final Map<File, SdfIndex> fileIndex = new ConcurrentHashMap<>();

    SdfParser(File[] files, Consumer<String> progressCallback) {
        Arrays.sort(files);

        for (File file : files) {
            System.out.println("Parsing file: " + file.getAbsolutePath());
            try {
                this.file = file;
                fis = new FileInputStream(file);
                channel = fis.getChannel();
                indexPath = file.getParent() + "/mra/sdf" + file.getName() + ".index";

                if (!new File(indexPath).exists()) {
                    if (progressCallback != null) {
                        progressCallback.accept(I18n.text("Generating SDF index for ") + file.getAbsolutePath());
                    }
                    LOG.info("{}{}", I18n.text("Generating SDF index for "), file.getAbsolutePath());
                    generateIndex();
                } else {
                    if (progressCallback != null) {
                        progressCallback.accept(I18n.text("Loading SDF index for ") + file.getAbsolutePath());
                    }
                    LOG.info("{}{}", I18n.text("Loading SDF index for "), file.getAbsolutePath());
                    if (!loadIndex(file)) {
                        if (progressCallback != null) {
                            progressCallback.accept(I18n.text("Corrupted SDF index file. Trying to create a new index."));
                        }
                        LOG.error(I18n.text("Corrupted SDF index file. Trying to create a new index."));
                        generateIndex();
                    }
                }
            } catch (FileNotFoundException e) {
                LOG.error("exception: ", e);
            }
        }
        // Merge timestamps from all files
        for (int subsystem : new int[]{SUBSYS_LOW, SUBSYS_HIGH}) {
            List<Long> mergedTimestamps = new ArrayList<>();

            for (SdfIndex index : fileIndex.values()) {
                if (subsystem == SUBSYS_LOW && index.hasLow) {
                    mergedTimestamps.addAll(index.positionMapLow.keySet());
                }
                else if (subsystem == SUBSYS_HIGH && index.hasHigh) {
                    mergedTimestamps.addAll(index.positionMapHigh.keySet());
                }
            }

            if (!mergedTimestamps.isEmpty()) {
                Long[] sortedTimestamps = mergedTimestamps.toArray(new Long[0]);
                Arrays.sort(sortedTimestamps);
                tslist.put(subsystem, sortedTimestamps);
            }
        }
    }

    private void generateIndex() {
        SdfHeader header = new SdfHeader();
        SdfData ping = new SdfData();
        SdfIndex index2 = new SdfIndex();

        long maxTimestampHigh = 0;
        long maxTimestampLow = 0;
        long minTimestampHigh = Long.MAX_VALUE;
        long minTimestampLow = Long.MAX_VALUE;

        HashSet<Integer> unknownPages = new HashSet<>();
        long count = 0;
        int count_low = 0;
        int count_high = 0;
        long pos;
        curPosition = 0;

        try {
            while (true) {
                // Read the header
                ByteBuffer buf = channel.map(MapMode.READ_ONLY, curPosition, 512); // header size 512bytes
                buf.order(ByteOrder.LITTLE_ENDIAN);
                header.parse(buf);
                curPosition += header.getHeaderSize();

                if (header.getPageVersion() == SUBSYS_HIGH || header.getPageVersion() == SUBSYS_LOW) {
                    // set header of this ping
                    ping.setHeader(header);
                    ping.calculateTimeStamp(false);
                    pos = curPosition - header.getHeaderSize();
                } else {
                    // ignore other pageVersions
                    if (!unknownPages.contains(header.getPageVersion())) {
                        LOG.warn(I18n.text("SDF file contains data not supported (page version #") + header.getPageVersion() + ")");
                        unknownPages.add(header.getPageVersion());
                    }
                    curPosition += (header.getNumberBytes() + 4) - header.getHeaderSize();
                    if (curPosition >= channel.size()) // check if curPosition is at the end of file
                        break;

                    continue;
                }

                // get timestamp, freq and subsystem used
                long t = ping.getTimestamp(); // Timestamp
                if (t < minimumValidTimestamp) {
                    curPosition += (header.getNumberBytes() + 4) - header.getHeaderSize();
                    count++;
                    continue;
                }
                int f = ping.getHeader().getSonarFreq(); // Frequency
                int subsystem = ping.getHeader().getPageVersion();

                if (!index2.frequenciesList.contains(f))
                    index2.frequenciesList.add(f);

                if (!index2.subSystemsList.contains(subsystem))
                    index2.subSystemsList.add(subsystem);

                if (subsystem == SUBSYS_LOW) {
                    if (!index2.hasLow)
                        index2.hasLow = true;

                    ArrayList<Long> l = index2.positionMapLow.get(t);
                    if (l == null) {
                        l = new ArrayList<>();
                        l.add(pos);
                        index2.positionMapLow.put(t, l);
                    } else {
                        l.add(pos);
                    }
                    index2.timestampToPingIndexLow.put(t, count_low++);
                    minTimestampLow = Math.min(minTimestampLow, t);
                    maxTimestampLow = Math.max(maxTimestampLow, t);
                }

                if (subsystem == SUBSYS_HIGH) {
                    if (!index2.hasHigh)
                        index2.hasHigh = true;

                    ArrayList<Long> l = index2.positionMapHigh.get(t);
                    if (l == null) {
                        l = new ArrayList<>();
                        l.add(pos);
                        index2.positionMapHigh.put(t, l);
                    } else {
                        l.add(pos);
                    }
                    index2.timestampToPingIndexHigh.put(t, count_high++);
                    minTimestampHigh = Math.min(minTimestampHigh, t);
                    maxTimestampHigh = Math.max(maxTimestampHigh, t);
                }

                curPosition += (header.getNumberBytes() + 4) - header.getHeaderSize();
                count++;

                if (curPosition >= channel.size())
                    break;
            }

            index2.firstTimestampHigh = minTimestampHigh;
            index2.firstTimestampLow = minTimestampLow;

            index2.lastTimestampHigh = maxTimestampHigh;
            index2.lastTimestampLow = maxTimestampLow;

            Long[] tslisthigh = index2.positionMapHigh.keySet().toArray(new Long[]{});
            Long[] tslistlow = index2.positionMapLow.keySet().toArray(new Long[]{});

            Arrays.sort(tslisthigh);
            Arrays.sort(tslistlow);

            tslist.put(SUBSYS_LOW, tslistlow);
            tslist.put(SUBSYS_HIGH, tslisthigh);

            index2.numberOfPackets = count;

            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(indexPath));
            out.writeObject(index2);
            out.close();

            fileIndex.put(file, index2);
        } catch (IOException e) {
            LOG.error("exception: ", e);
        }
    }

    private boolean loadIndex(File file) {
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(indexPath));
            SdfIndex indexN = (SdfIndex) in.readObject();

            Long[] tslisthigh = indexN.positionMapHigh.keySet().toArray(new Long[]{});
            Long[] tslistlow = indexN.positionMapLow.keySet().toArray(new Long[]{});

            Arrays.sort(tslisthigh);
            Arrays.sort(tslistlow);

            // Add timestamps to tslist if not empty
            if (tslistlow.length > 0)
                tslist.put(SUBSYS_LOW, tslistlow);
            if (tslisthigh.length > 0)
                tslist.put(SUBSYS_HIGH, tslisthigh);

            fileIndex.put(file, indexN);

            in.close();
        } catch (Exception e) {
            LOG.error("exception: ", e);
            return false;
        }
        return true;
    }

    long getFirstTimeStamp() {
        long firstTimeStamp = Long.MAX_VALUE;

        for (Entry<File, SdfIndex> entry : fileIndex.entrySet())
            firstTimeStamp = Math.min(firstTimeStamp, Math.min(entry.getValue().firstTimestampHigh, entry.getValue().firstTimestampLow));

        return firstTimeStamp;
    }

    long getLastTimeStamp() {
        long lastTimeStamp = 0;

        for (Entry<File, SdfIndex> entry : fileIndex.entrySet())
            lastTimeStamp = Math.max(lastTimeStamp, Math.max(entry.getValue().lastTimestampHigh, entry.getValue().lastTimestampLow));

        return lastTimeStamp;
    }

    SdfData nextPing(int subsystem) {
        return getPingAt(nextTimestamp.get(subsystem)+1, subsystem); // This fetches the next ping and updates nextTimestamp
    }

    SdfData getPingAt(Long timestamp, int subsystem) {
        if (timestamp == null) {
            return null;
        }

        SdfIndex index = fileIndex.get(file);
        if (index == null || !existsTimestamp(timestamp, index, subsystem)) {
            redirectIndex(timestamp, subsystem);
            index = fileIndex.get(file);
            if (index == null) {
                return null;  // No suitable index found
            }
        }

        LinkedHashMap<Long, ArrayList<Long>> positionMap = (subsystem == SUBSYS_LOW)
                ? index.positionMapLow
                : index.positionMapHigh;

        Long[] timestamps = tslist.get(subsystem);
        if (timestamps == null) {
            return null;
        }

        int timestampIndex = findTimestampIndexBinary(timestamps, timestamp);
        if (timestampIndex < 0) {
            return null;
        }

        long targetTimestamp = timestamps[timestampIndex];
        if ((subsystem == SUBSYS_LOW && targetTimestamp > index.lastTimestampLow) ||
                (subsystem == SUBSYS_HIGH && targetTimestamp > index.lastTimestampHigh)) {
            return getPingAt(targetTimestamp, subsystem);
        }

        if (timestampIndex + 1 < timestamps.length) {
            nextTimestamp.put(subsystem, timestamps[timestampIndex + 1]);
        }

        ArrayList<Long> positions = positionMap.get(targetTimestamp);
        if (positions == null || positions.isEmpty()) {
            return null;
        }

        return getPingAtPosition(positions.getFirst(), subsystem);
    }

    private int findTimestampIndexBinary(Long[] timestamps, long targetTimestamp) {
        int index = Arrays.binarySearch(timestamps, targetTimestamp);
        // If exact match found, return it
        if (index >= 0) {
            return index;
        }
        // If no exact match, binarySearch returns (-(insertion point) - 1)
        // We want the insertion point as it's the next greater timestamp
        int insertionPoint = -(index + 1);
        return insertionPoint < timestamps.length ? insertionPoint : -1;
    }

    private boolean existsTimestamp(long timestamp, SdfIndex searchIndex, int subsystem) {
        if (subsystem == SUBSYS_LOW) {
            return timestamp >= searchIndex.firstTimestampLow && timestamp <= searchIndex.lastTimestampLow;
        } else {
            return timestamp >= searchIndex.firstTimestampHigh && timestamp <= searchIndex.lastTimestampHigh;
        }
    }

    // There may be an issue if an index file i0 ends with timestamp t0 and i1 starts with timestamp t0 as well
    private void redirectIndex(Long timestamp, int subsystem) {
        for (Entry<File, SdfIndex> entry : fileIndex.entrySet()) {
            if (subsystem == SUBSYS_LOW) {
                if (timestamp >= entry.getValue().firstTimestampLow && timestamp < entry.getValue().lastTimestampLow) {
                    file = entry.getKey();
                    return;
                }
            } else if (subsystem == SUBSYS_HIGH) {
                if (timestamp >= entry.getValue().firstTimestampHigh && timestamp < entry.getValue().lastTimestampHigh) {
                    file = entry.getKey();
                    return;
                }
            }
        }
    }

    private SdfData getPingAtPosition(long pos, int subsystem) {
        // Save the current thread's interrupted status and clear it temporarily
        boolean wasInterrupted = Thread.interrupted();

        try (FileInputStream localFis = new FileInputStream(file);
             FileChannel localChannel = localFis.getChannel()) {

            SdfHeader header = new SdfHeader();
            SdfData ping = new SdfData();

            // First memory-mapped operation - reading the header
            ByteBuffer buf = localChannel.map(MapMode.READ_ONLY, pos, 512);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            header.parse(buf);
            pos += header.getHeaderSize();

            if (header.getPageVersion() != subsystem) {
                return null;
            }

            ping.setHeader(header);
            ping.calculateTimeStamp(false);

            // Second memory-mapped operation - reading the data
            int dataSize = header.getNumberBytes() - header.getHeaderSize() - header.getSDFExtensionSize() + 4;
            buf = localChannel.map(MapMode.READ_ONLY, pos, dataSize);
            buf.order(ByteOrder.LITTLE_ENDIAN);

            ping.parseData(buf);
            return ping;
        } catch (IOException e) {
            if (e instanceof java.nio.channels.ClosedByInterruptException) {
                LOG.warn("Thread was interrupted while accessing SDF file at position {}", pos);
            } else {
                LOG.error("Failed to get ping at position {}", pos, e);
            }
            return null;
        } finally {
            // Restore the interrupted status if it was set before
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * @return the index
     */
    public List<SdfIndex> getIndex() {
        List<SdfIndex> indices = new ArrayList<>();
        Set<Entry<File, SdfIndex>> entries = fileIndex.entrySet();
        for (Entry<File, SdfIndex> entry : entries)
            indices.add(entry.getValue());
        return indices;
    }

    public void cleanup() {
        try {
            if (fis != null)
                fis.close();

            if (channel != null)
                channel.close();
        } catch (IOException e) {
            LOG.error("exception: ", e);
        }
    }
}
