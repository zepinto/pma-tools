//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
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
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
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
 * Author: José Correia
 * Feb 5, 2013
 */

package pt.omst.neptus.sidescan.jsf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.*;
import java.util.function.Consumer;

/**
 * @author jqcorreia
 */
public class JsfParser {
    private static final Logger LOG = LoggerFactory.getLogger(JsfParser.class);
    final static int SUBSYS_LOW = 20;
    final static int SUBSYS_HIGH = 21;
    @SuppressWarnings("unused")
    private File file;
    private FileInputStream fis;
    private FileChannel channel;
    private long curPosition = 0;
    private JsfIndex index = new JsfIndex();
    private String indexPath;
    private LinkedHashMap<Integer, Long[]> tslist = new LinkedHashMap<>();
    private LinkedHashMap<Integer, Long> nextTimestamp = new LinkedHashMap<>();

    private final LinkedHashMap<File, JsfIndex> fileIndex = new LinkedHashMap<>();

    public JsfParser(File[] files, Consumer<String> progressCallback) {
        Arrays.sort(files);
        for (File file : files) {
            try {
                if (file.length() == 0)
                    continue; // ignore if file is empty
                this.file = file;
                fis = new FileInputStream(file);

                channel = fis.getChannel();
                indexPath = file.getParent() + "/mra/jsf" + file.getName() + ".index";

                if (!new File(indexPath).exists()) {
                    if (progressCallback != null) {
                        progressCallback.accept("Generating JSF index for " + file.getName());
                    }
                    LOG.info("generating JSF index for " + file.getAbsolutePath());
                    generateIndex();
                } else {
                    if (progressCallback != null) {
                        progressCallback.accept("Loading JSF index for " + file.getName());
                    }
                    LOG.info("loading JSF index for " + file.getAbsolutePath());
                    if (!loadIndex()) {
                        LOG.error("Corrupted JSF index file. Trying to create a new index.");
                        generateIndex();
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void generateIndex() {
        JsfHeader header = new JsfHeader();
        JsfSonarData ping = new JsfSonarData();
        JsfIndex index2 = new JsfIndex();

        long count = 0;
        long pos = 0;

        long maxTimestampHigh = 0;
        long maxTimestampLow = 0;
        long minTimestampHigh = Long.MAX_VALUE;
        long minTimestampLow = Long.MAX_VALUE;

        try {
            while (true) {
                int headerSize = 16;
                if (curPosition + headerSize >= channel.size())
                    break;
                // Read ONLY the header
                ByteBuffer buf = channel.map(MapMode.READ_ONLY, curPosition, headerSize);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                header.parse(buf);
                curPosition += headerSize;
                if (header.getType() == 80) {
                    int mapSize = 240;
                    if (curPosition + mapSize >= channel.size())
                        break;

                    ping.setHeader(header);

                    buf = channel.map(MapMode.READ_ONLY, curPosition, mapSize);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    ping.parseHeader(buf);
                    curPosition += header.getMessageSize();
                } else { // Ignore other messages;
                    curPosition += header.getMessageSize();
                    pos = curPosition;
                    if (curPosition >= channel.size())
                        break;
                    else
                        continue;
                }

                // Common processing to both subsystems
                long t = ping.getTimestamp(); // Timestamp
                float f = ping.getFrequency(); // Frequency
                int subsystem = ping.getHeader().getSubsystem();

                if (!index2.frequenciesList.contains(f))
                    index2.frequenciesList.add(f);

                if (!index2.subSystemsList.contains(subsystem))
                    index2.subSystemsList.add(subsystem);

                if (subsystem == SUBSYS_LOW) {
                    if (!index2.hasLow) index2.hasLow = true;

                    ArrayList<Long> l = index2.positionMapLow.get(t);
                    if (l == null) {
                        l = new ArrayList<>();
                        l.add(pos);
                        index2.positionMapLow.put(t, l);
                    } else {
                        l.add(pos);
                    }
                    minTimestampLow = Math.min(minTimestampLow, t);
                    maxTimestampLow = Math.max(maxTimestampLow, t);
                }

                if (subsystem == SUBSYS_HIGH) {
                    if (!index2.hasHigh) index2.hasHigh = true;

                    ArrayList<Long> l = index2.positionMapHigh.get(t);
                    if (l == null) {
                        l = new ArrayList<>();
                        l.add(pos);
                        index2.positionMapHigh.put(t, l);
                        // System.out.println(t);
                    } else {
                        l.add(pos);
                    }
                    minTimestampHigh = Math.min(minTimestampHigh, t);
                    maxTimestampHigh = Math.max(maxTimestampHigh, t);
                }

                count++;
                pos = curPosition;

                if (curPosition >= channel.size())
                    break;
            }

            index2.firstTimestampHigh = minTimestampHigh;
            index2.firstTimestampLow = minTimestampLow;

            index2.lastTimestampHigh = maxTimestampHigh;
            index2.lastTimestampLow = maxTimestampLow;

            // Save timestamp list
            Long[] tslisthigh;
            Long[] tslistlow;

            tslisthigh = index2.positionMapHigh.keySet().toArray(new Long[]{});
            tslistlow = index2.positionMapLow.keySet().toArray(new Long[]{});

            Arrays.sort(tslisthigh);
            Arrays.sort(tslistlow);

            tslist.put(SUBSYS_LOW, tslistlow);
            tslist.put(SUBSYS_HIGH, tslisthigh);

            index2.numberOfPackets = count;

            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(indexPath));
            out.writeObject(index2);
            out.close();

            fileIndex.put(file, index2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean loadIndex() {
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(indexPath));
            JsfIndex indexN = (JsfIndex) in.readObject();

            Long[] tslisthigh;
            Long[] tslistlow;

            tslisthigh = indexN.positionMapHigh.keySet().toArray(new Long[]{});
            tslistlow = indexN.positionMapLow.keySet().toArray(new Long[]{});

            Arrays.sort(tslisthigh);
            Arrays.sort(tslistlow);

            tslist.put(SUBSYS_LOW, tslistlow);
            tslist.put(SUBSYS_HIGH, tslisthigh);

            fileIndex.put(file, indexN);

            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    long getFirstTimeStamp() {
        Set<Map.Entry<File, JsfIndex>> entries = fileIndex.entrySet();
        long firstTimeStamp = Long.MAX_VALUE;

        for (Map.Entry<File, JsfIndex> entry : entries) {
            long minTimeStamp = Math.min(entry.getValue().firstTimestampHigh, entry.getValue().firstTimestampLow);
            if (minTimeStamp < firstTimeStamp)
                firstTimeStamp = minTimeStamp;
        }

        return firstTimeStamp;
    }

    long getFirstTimestamp(int subsystem) {
        Set<Map.Entry<File, JsfIndex>> entries = fileIndex.entrySet();
        long firstTimeStamp = Long.MAX_VALUE;

        for (Map.Entry<File, JsfIndex> entry : entries) {
            if (subsystem == SUBSYS_LOW) {
                long minTimeStamp = entry.getValue().firstTimestampLow;
                if (minTimeStamp < firstTimeStamp)
                    firstTimeStamp = minTimeStamp;
            } else {
                long minTimeStamp = entry.getValue().firstTimestampHigh;
                if (minTimeStamp < firstTimeStamp)
                    firstTimeStamp = minTimeStamp;
                }   
        }

        return firstTimeStamp;
    }

    long getLastTimeStamp() {
        Map.Entry<File, JsfIndex> lastEntry = null;
        for (Map.Entry<File, JsfIndex> entry : fileIndex.entrySet())
            lastEntry = entry;

        return Math.max(lastEntry.getValue().lastTimestampHigh, lastEntry.getValue().lastTimestampLow);
    }

    long getLastTimeStamp(int subsystem) {
        Set<Map.Entry<File, JsfIndex>> entries = fileIndex.entrySet();
        long lastTimestamp = 0;

        for (Map.Entry<File, JsfIndex> entry : entries) {
            if (subsystem == SUBSYS_LOW) {
                long maxTimestamp = entry.getValue().lastTimestampLow;
                if (maxTimestamp > lastTimestamp)
                    lastTimestamp = maxTimestamp;
            } else {
                long maxTimestamp = entry.getValue().lastTimestampHigh;
                if (maxTimestamp > lastTimestamp)
                    lastTimestamp = maxTimestamp;
            }
        }

        return lastTimestamp;
    }

    /**
     * @return the index
     */
    public List<JsfIndex> getIndex() {
        List<JsfIndex> indices = new ArrayList<>();
        Set<Map.Entry<File, JsfIndex>> entries = fileIndex.entrySet();
        for (Map.Entry<File, JsfIndex> entry : entries)
            indices.add(entry.getValue());
        return indices;
    }

    public ArrayList<JsfSonarData> nextPing(int subsystem) {
        return getPingAt(nextTimestamp.get(subsystem), subsystem); // This fetches the next ping and updates nextTimestamp
    }

    private boolean existsTimestamp(long timestamp, JsfIndex searchIndex, int subsystem) {
        if (subsystem == SUBSYS_LOW) {
            return timestamp >= searchIndex.firstTimestampLow && timestamp <= searchIndex.lastTimestampLow;
        } else {
            return timestamp >= searchIndex.firstTimestampHigh && timestamp <= searchIndex.lastTimestampHigh;
        }
    }

    
    public ArrayList<JsfSonarData> getPingAt(Long timestamp, int subsystem) {
        if (index != null) {
            if (!existsTimestamp(timestamp, index, subsystem))
                redirectIndex(timestamp, subsystem);
        }
        curPosition = 0;
        ArrayList<JsfSonarData> ping = new ArrayList<>();
        assert index != null;
        LinkedHashMap<Long, ArrayList<Long>> positionMap = (subsystem == SUBSYS_LOW ? index.positionMapLow : index.positionMapHigh);

        long ts = 0;
        int c = 0;

        for (Long time : tslist.get(subsystem)) {
            if (time >= timestamp) {
                ts = time;
                break;
            }
            c++;
        }
        if (ts == 0) {
            return ping;
        }

        nextTimestamp.put(subsystem, tslist.get(subsystem)[c + 1]);
        ArrayList<Long> positions = positionMap.get(ts);
        if (positions == null)
            return ping;
        for (Long pos : positions)
            ping.add(getPingAtPosition(pos, subsystem));

        return ping;
    }

    private void redirectIndex(Long timestamp, int subsystem) {
        for (Map.Entry<File, JsfIndex> entry : fileIndex.entrySet()) {
            if (subsystem == SUBSYS_LOW) {
                if (timestamp >= entry.getValue().firstTimestampLow && timestamp < entry.getValue().lastTimestampLow) {
                    index = entry.getValue();
                    file = entry.getKey();
                    return;
                }
            } else if (subsystem == SUBSYS_HIGH) {
                if (timestamp >= entry.getValue().firstTimestampHigh && timestamp < entry.getValue().lastTimestampHigh) {
                    index = entry.getValue();
                    file = entry.getKey();
                    return;
                }
            }
        }
    }

    public JsfSonarData getPingAtPosition(long pos, int subsystem) {
        JsfHeader header = new JsfHeader();
        JsfSonarData ping = new JsfSonarData();
        try {
            ByteBuffer buf = channel.map(MapMode.READ_ONLY, pos, 16);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            header.parse(buf);
            pos += 16;

            if (header.getSubsystem() != subsystem)
                return null;

            ping.setHeader(header);

            buf = channel.map(MapMode.READ_ONLY, pos, 240);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            ping.parseHeader(buf);
            pos += 240;
            buf = channel.map(MapMode.READ_ONLY, pos, header.getMessageSize() - 240);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            ping.parseData(buf);
            pos += header.getMessageSize() - 240;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ping;
    }

    public void cleanup() {
        try {
            if (fis != null)
                fis.close();

            if (channel != null)
                channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
