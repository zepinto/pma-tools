//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
/*
 * Copyright (c) 2004-2025 OceanScan-MST
 * All rights reserved.
 *
 * This file is part of Neptus Utilities.
 */

package pt.omst.sidescan.sds;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Logger;

import pt.lsts.neptus.core.SystemPositionAndAttitude;
import pt.lsts.neptus.util.SidescanUtil;
import pt.omst.sidescan.ISidescanLine;
import pt.omst.sidescan.SidescanLine;
import pt.omst.sidescan.SidescanParameters;
import pt.omst.sidescan.SidescanParser;

/**
 * Java translation of the SDS (Sonar Data Stream) parser.
 * Parses SDS format sidescan sonar files.
 * 
 * @author OceanScan-MST
 */
public class SdsParser implements SidescanParser {

    // Type aliases equivalent
    private static final int STARBOARD_IDX = 0;
    private static final int PORT_IDX = 1;
    
    private final Logger logger = Logger.getLogger(SdsParser.class.getName());
    private final TreeMap<Long, Long> syncIndex = new TreeMap<>();
    private final TreeMap<Long, Long> snr2Index = new TreeMap<>();
    private final TreeMap<Long, Long> navIndex = new TreeMap<>();
    private final TreeMap<Long, Long> fthmIndex = new TreeMap<>();
    private final TreeMap<Long, Long> orntIndex = new TreeMap<>();
    
    private long systemStartEpochMs = 0;
    private long firstPingEpochMs = 0;
    private long lastPingEpochMs = 0;
    
    private final TreeMap<Long, File> fileIndexMap = new TreeMap<>();
    private final LinkedHashMap<FileReadCacheKey, SdsPayload> fileReadCache = new LinkedHashMap<FileReadCacheKey, SdsPayload>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<FileReadCacheKey, SdsPayload> eldest) {
            return size() > 1000;
        }
    };
    
    private final LinkedHashMap<SidescanLineCacheKey, ISidescanLine> sidescanLineCache = new LinkedHashMap<SidescanLineCacheKey, ISidescanLine>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SidescanLineCacheKey, ISidescanLine> eldest) {
            return size() > 1000;
        }
    };

    // Sealed class equivalent - base class
    public abstract static class SdsPayload {}

    // Exception classes
    public static class UnknownTagException extends Exception {
        private final long tag;
        private final long packetSize;
        
        public UnknownTagException(long tag, long packetSize) {
            super("Unknown tag: 0x" + Long.toHexString(tag));
            this.tag = tag;
            this.packetSize = packetSize;
        }
        
        public long getTag() { return tag; }
        public long getPacketSize() { return packetSize; }
    }

    // Enum equivalent
    public enum SdsTag {
        SYNC(0x53594E43L),
        SNR2(0x32524E52L),
        NAV(0x4E415600L),
        FTHM(0x46415400L),
        ORNT(0x4F524E54L),
        NMEA(0x4E4D4541L),
        CUBE(0x43554245L),
        ARC(0x00415243L),
        MKII(0x4D4B4949L),
        MAG(0x4D414700L);

        private final long tag;

        SdsTag(long tag) {
            this.tag = tag;
        }

        public long getTag() {
            return tag;
        }
        
        public static SdsTag fromTag(long tagValue) {
            for (SdsTag tag : values()) {
                if (tag.getTag() == tagValue) {
                    return tag;
                }
            }
            return null;
        }
    }

    // Data classes equivalent
    public static class SdsHeader {
        public final long packetSize;
        public final long timestampMs;
        public final SdsTag tag;
        public final byte[] misc;
        public final byte checksum;

        public SdsHeader(long packetSize, long timestampMs, SdsTag tag, byte[] misc, byte checksum) {
            this.packetSize = packetSize;
            this.timestampMs = timestampMs;
            this.tag = tag;
            this.misc = misc;
            this.checksum = checksum;
        }
    }

    public static class SdsSyncPacket extends SdsPayload {
        public final long referenceSeconds;
        public final int intervalMs;

        public SdsSyncPacket(long referenceSeconds, int intervalMs) {
            this.referenceSeconds = referenceSeconds;
            this.intervalMs = intervalMs;
        }
    }

    public static class SdsDataSonarPing extends SdsPayload {
        public final int channelCount;
        public final long pingNumber;
        public final float speedOfSound;
        public final byte[] reserved;

        public SdsDataSonarPing(int channelCount, long pingNumber, float speedOfSound, byte[] reserved) {
            this.channelCount = channelCount;
            this.pingNumber = pingNumber;
            this.speedOfSound = speedOfSound;
            this.reserved = reserved;
        }
    }

    public static class SdsDataSonarPingChannel extends SdsPayload {
        public final int sonarType;
        public final int sonarId;
        public final float freqHz;
        public final float rangeMs;
        public final float rangeDelayMs;
        public final int dataFlags;
        public final int dataType;
        public final int samplesInChannel;

        public SdsDataSonarPingChannel(int sonarType, int sonarId, float freqHz, float rangeMs, 
                                     float rangeDelayMs, int dataFlags, int dataType, int samplesInChannel) {
            this.sonarType = sonarType;
            this.sonarId = sonarId;
            this.freqHz = freqHz;
            this.rangeMs = rangeMs;
            this.rangeDelayMs = rangeDelayMs;
            this.dataFlags = dataFlags;
            this.dataType = dataType;
            this.samplesInChannel = samplesInChannel;
        }
    }

    public static class SdsSNR2 extends SdsPayload {
        public final SdsDataSonarPing sonarPing;
        public final List<SdsDataSonarPingChannel> channels;
        public final List<List<Integer>> samples; // UShort equivalent as Integer

        public SdsSNR2(SdsDataSonarPing sonarPing, List<SdsDataSonarPingChannel> channels, List<List<Integer>> samples) {
            this.sonarPing = sonarPing;
            this.channels = channels;
            this.samples = samples;
        }
    }

    public static class SdsNavigationDataPacket extends SdsPayload {
        public final int source;
        public final double latDeg;
        public final double lonDeg;
        public final float cogDeg;
        public final float headingDeg;
        public final float sogMetersPerS;

        public SdsNavigationDataPacket(int source, double latDeg, double lonDeg, float cogDeg, float headingDeg, float sogMetersPerS) {
            this.source = source;
            this.latDeg = latDeg;
            this.lonDeg = lonDeg;
            this.cogDeg = cogDeg;
            this.headingDeg = headingDeg;
            this.sogMetersPerS = sogMetersPerS;
        }
    }

    public static class SdsOrientationDataPacket extends SdsPayload {
        public final int source;
        public final float xMeters;
        public final float yMeters;
        public final float zMeters;
        public final float rollDeg;
        public final float pitchDeg;
        public final float yawDeg;
        public final float heaveCm;

        public SdsOrientationDataPacket(int source, float xMeters, float yMeters, float zMeters,
                                      float rollDeg, float pitchDeg, float yawDeg, float heaveCm) {
            this.source = source;
            this.xMeters = xMeters;
            this.yMeters = yMeters;
            this.zMeters = zMeters;
            this.rollDeg = rollDeg;
            this.pitchDeg = pitchDeg;
            this.yawDeg = yawDeg;
            this.heaveCm = heaveCm;
        }
    }

    public static class SdsFathometerDataPacket extends SdsPayload {
        public final int source;
        public final float depthMeters;
        public final float altitudeMeters;

        public SdsFathometerDataPacket(int source, float depthMeters, float altitudeMeters) {
            this.source = source;
            this.depthMeters = depthMeters;
            this.altitudeMeters = altitudeMeters;
        }
    }

    // Cache key classes
    public static class FileReadCacheKey {
        public final File file;
        public final long offset;

        public FileReadCacheKey(File file, long offset) {
            this.file = file;
            this.offset = offset;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FileReadCacheKey)) return false;
            FileReadCacheKey that = (FileReadCacheKey) o;
            return offset == that.offset && Objects.equals(file, that.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, offset);
        }
    }

    public static class SidescanLineCacheKey {
        public final long timestampMs;
        public final SidescanParameters config;

        public SidescanLineCacheKey(long timestampMs, SidescanParameters config) {
            this.timestampMs = timestampMs;
            this.config = config;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SidescanLineCacheKey)) return false;
            SidescanLineCacheKey that = (SidescanLineCacheKey) o;
            return timestampMs == that.timestampMs && Objects.equals(config, that.config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(timestampMs, config);
        }
    }

    private SdsPayload readAt(File file, RandomAccessFile raf, long offset) throws IOException, UnknownTagException {
        FileReadCacheKey cacheKey = new FileReadCacheKey(file, offset);
        SdsPayload cached = fileReadCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        raf.seek(offset);
        SdsHeader header = readHeader(raf);
        SdsPayload data = null;
        
        switch (header.tag) {
            case SYNC:
                data = parseSync(raf);
                break;
            case SNR2:
                data = parseSNR2(raf);
                break;
            case NAV:
                data = parseNAV(raf);
                break;
            case FTHM:
                data = parseFTHM(raf);
                break;
            case ORNT:
                data = parseORNT(raf);
                break;
            default:
                return null;
        }
        
        if (data != null) {
            fileReadCache.put(cacheKey, data);
        }
        return data;
    }

    public void parse(File file) throws IOException, UnknownTagException {
        if (file.length() == 0L) return;
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SyncResult syncResult = synchronizeStream(raf);
            systemStartEpochMs = syncResult.syncPacket.referenceSeconds * 1000L - syncResult.header.timestampMs;
            
            while (raf.getFilePointer() < raf.length()) {
                try {
                    long headerOffset = raf.getFilePointer();
                    SdsHeader header = readHeader(raf);
                    long epochMs = header.timestampMs + systemStartEpochMs;
                    
                    switch (header.tag) {
                        case SYNC:
                            syncIndex.put(epochMs, headerOffset);
                            break;
                        case SNR2:
                            snr2Index.put(epochMs, headerOffset);
                            break;
                        case NAV:
                            navIndex.put(epochMs, headerOffset);
                            break;
                        case FTHM:
                            fthmIndex.put(epochMs, headerOffset);
                            break;
                        case ORNT:
                            orntIndex.put(epochMs, headerOffset);
                            break;
                        default:
                            break;
                    }
                    raf.seek(raf.getFilePointer() + header.packetSize);
                } catch (IllegalStateException e) {
                    logger.warning("Corrupt packet, finding next sync pattern");
                    synchronizeStream(raf);
                } catch (UnknownTagException e) {
                    logger.warning(e.getMessage());
                    raf.seek(raf.getFilePointer() + e.getPacketSize());
                }
            }
            
            if (!snr2Index.isEmpty()) {
                firstPingEpochMs = snr2Index.firstKey();
                lastPingEpochMs = snr2Index.lastKey();
                fileIndexMap.put(lastPingEpochMs, file);
            }
        }
    }

    private static class SyncResult {
        public final SdsHeader header;
        public final SdsSyncPacket syncPacket;
        
        public SyncResult(SdsHeader header, SdsSyncPacket syncPacket) {
            this.header = header;
            this.syncPacket = syncPacket;
        }
    }

    private SyncResult synchronizeStream(RandomAccessFile raf) throws IOException, UnknownTagException {
        byte[] syncPattern = {0x43, 0x4E, 0x59, 0x53, (byte)0xAA, (byte)0xAA, (byte)0xAA};
        byte[] buffer = new byte[syncPattern.length];
        
        while (raf.getFilePointer() < raf.length() - syncPattern.length) {
            raf.read(buffer);
            if (Arrays.equals(buffer, syncPattern)) {
                raf.seek(raf.getFilePointer() - syncPattern.length - 8);
                SdsHeader header = readHeader(raf);
                if (header.tag == SdsTag.SYNC) {
                    return new SyncResult(header, parseSync(raf));
                }
            }
            raf.seek(raf.getFilePointer() + 1);
        }
        throw new IllegalStateException("Synchronization pattern not found");
    }

    private SdsHeader readHeader(RandomAccessFile raf) throws IOException, UnknownTagException {
        byte[] buffer = new byte[16];
        raf.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        long packetSize = Integer.toUnsignedLong(byteBuffer.getInt());
        long timestampMs = Integer.toUnsignedLong(byteBuffer.getInt());
        long tagValue = Integer.toUnsignedLong(byteBuffer.getInt());
        
        SdsTag tag = SdsTag.fromTag(tagValue);
        if (tag == null) {
            throw new UnknownTagException(tagValue, packetSize);
        }
        
        byte[] misc = new byte[3];
        byteBuffer.get(misc, 0, 3);
        byte checksum = byteBuffer.get();
        
        validateChecksum(Arrays.copyOfRange(buffer, 0, 15), checksum);
        
        return new SdsHeader(packetSize, timestampMs, tag, misc, checksum);
    }

    private void validateChecksum(byte[] buffer, byte checksum) {
        byte calculatedChecksum = 0;
        for (byte b : buffer) {
            calculatedChecksum += b;
        }
        if (calculatedChecksum != checksum) {
            throw new IllegalStateException("Checksum mismatch: expected " + checksum + ", got " + calculatedChecksum);
        }
    }

    private SdsSNR2 parseSNR2(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[16];
        raf.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        
        int channelCount = Byte.toUnsignedInt(byteBuffer.get());
        long pingNumber = Integer.toUnsignedLong(byteBuffer.getInt());
        float speedOfSound = byteBuffer.getFloat();
        byte[] reserved = new byte[7];
        byteBuffer.get(reserved, 0, 7);

        List<SdsDataSonarPingChannel> channels = new ArrayList<>();
        for (int i = 0; i < channelCount; i++) {
            byte[] channelBuffer = new byte[22];
            raf.read(channelBuffer);
            ByteBuffer channelByteBuffer = ByteBuffer.wrap(channelBuffer).order(ByteOrder.LITTLE_ENDIAN);

            int sonarType = Short.toUnsignedInt(channelByteBuffer.getShort());
            int sonarId = Short.toUnsignedInt(channelByteBuffer.getShort());
            float freqHz = channelByteBuffer.getFloat();
            float rangeMs = channelByteBuffer.getFloat();
            float rangeDelayMs = channelByteBuffer.getFloat();
            int dataFlags = Short.toUnsignedInt(channelByteBuffer.getShort());
            int dataType = Short.toUnsignedInt(channelByteBuffer.getShort());
            int samplesInChannel = Short.toUnsignedInt(channelByteBuffer.getShort());

            channels.add(new SdsDataSonarPingChannel(sonarType, sonarId, freqHz, rangeMs, 
                                                   rangeDelayMs, dataFlags, dataType, samplesInChannel));
        }

        List<List<Integer>> samples = new ArrayList<>();
        int sampleSizeBytes = 2;
        for (int i = 0; i < channelCount; i++) {
            byte[] sampleBuffer = new byte[sampleSizeBytes * channels.get(i).samplesInChannel];
            raf.read(sampleBuffer);
            ByteBuffer sampleByteBuffer = ByteBuffer.wrap(sampleBuffer).order(ByteOrder.LITTLE_ENDIAN);
            List<Integer> sampleLine = new ArrayList<>();
            for (int j = 0; j < channels.get(i).samplesInChannel; j++) {
                sampleLine.add(Short.toUnsignedInt(sampleByteBuffer.getShort()));
            }
            samples.add(sampleLine);
        }

        return new SdsSNR2(new SdsDataSonarPing(channelCount, pingNumber, speedOfSound, reserved), channels, samples);
    }

    private SdsNavigationDataPacket parseNAV(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[30];
        raf.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        int source = Short.toUnsignedInt(byteBuffer.getShort());
        double latDeg = byteBuffer.getDouble();
        double lonDeg = byteBuffer.getDouble();
        float cogDeg = byteBuffer.getFloat();
        float headingDeg = byteBuffer.getFloat();
        float sogMetersPerS = byteBuffer.getFloat();

        return new SdsNavigationDataPacket(source, latDeg, lonDeg, cogDeg, headingDeg, sogMetersPerS);
    }

    private SdsOrientationDataPacket parseORNT(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[30];
        raf.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        int source = Short.toUnsignedInt(byteBuffer.getShort());
        float xMeters = byteBuffer.getFloat();
        float yMeters = byteBuffer.getFloat();
        float zMeters = byteBuffer.getFloat();
        float rollDeg = byteBuffer.getFloat();
        float pitchDeg = byteBuffer.getFloat();
        float yawDeg = byteBuffer.getFloat();
        float heaveCm = byteBuffer.getFloat();

        return new SdsOrientationDataPacket(source, xMeters, yMeters, zMeters, rollDeg, pitchDeg, yawDeg, heaveCm);
    }

    private SdsFathometerDataPacket parseFTHM(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[10];
        raf.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        int source = Short.toUnsignedInt(byteBuffer.getShort());
        float depthMeters = byteBuffer.getFloat();
        float altitudeMeters = byteBuffer.getFloat();

        return new SdsFathometerDataPacket(source, depthMeters, altitudeMeters);
    }

    private SdsSyncPacket parseSync(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[6];
        raf.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        long reference = Integer.toUnsignedLong(byteBuffer.getInt());
        int interval = Short.toUnsignedInt(byteBuffer.getShort());
        return new SdsSyncPacket(reference, interval);
    }

    // SidescanParser interface implementation

    @Override
    public long firstPingTimestamp() {
        return firstPingEpochMs;
    }

    @Override
    public long firstPingTimestamp(int subsystem) {
        return firstPingEpochMs; // assumes only one subsystem
    }

    @Override
    public long lastPingTimestamp() {
        return lastPingEpochMs;
    }

    @Override
    public long lastPingTimestamp(int subsystem) {
        return lastPingEpochMs; // assumes only one subsystem
    }

    @Override
    public ArrayList<SidescanLine> getLinesBetween(long timestamp1, long timestamp2, int subsystem, SidescanParameters config) {
        ArrayList<SidescanLine> lines = new ArrayList<>();
        Long startTimeMs = snr2Index.floorKey(timestamp1);
        Long endTimeMs = snr2Index.floorKey(timestamp2);
        
        if (startTimeMs != null && endTimeMs != null) {
            Long currTimeMs = startTimeMs;
            while (currTimeMs != null && currTimeMs < endTimeMs) {
                try {
                    ISidescanLine line = getLineAtTime(currTimeMs, subsystem, config);
                    if (line != null) {
                        lines.add((SidescanLine) line);
                    }
                } catch (Exception e) {
                    // Log and skip corrupted lines
                    logger.warning("Skipping corrupted line at timestamp " + currTimeMs + ": " + e.getMessage());
                }
                currTimeMs = snr2Index.higherKey(currTimeMs);
            }
        }
        return lines;
    }

    private SystemPositionAndAttitude buildAttitude(SdsOrientationDataPacket orientation, 
                                                   SdsNavigationDataPacket nav, 
                                                   SdsFathometerDataPacket fathometer) {
        SystemPositionAndAttitude attitude = new SystemPositionAndAttitude();
        attitude.setU(nav.sogMetersPerS);
        double vNorth = Math.sin(Math.toRadians(nav.cogDeg)) * nav.sogMetersPerS;
        double vEast = Math.cos(Math.toRadians(nav.cogDeg)) * nav.sogMetersPerS;
        attitude.setVxyz(vNorth, vEast, 0.0);
        attitude.getPosition().setLatitudeDegs(nav.latDeg);
        attitude.getPosition().setLongitudeDegs(nav.lonDeg);
        attitude.setRoll(Math.toRadians(orientation.rollDeg));
        attitude.setPitch(Math.toRadians(orientation.pitchDeg));
        attitude.setYaw(Math.toRadians(nav.headingDeg));
        attitude.setAltitude(fathometer.altitudeMeters);
        attitude.setDepth(fathometer.depthMeters);
        return attitude;
    }

    private ISidescanLine buildSidescanLine(long epochMs, SdsSNR2 snr2, SdsOrientationDataPacket orientation,
                                          SdsNavigationDataPacket nav, SdsFathometerDataPacket fathometer,
                                          SidescanParameters config) {
        double rangeMeters = snr2.channels.get(0).rangeMs / 1000.0 * snr2.sonarPing.speedOfSound;
        double scaleFactor = 1.0;
        SystemPositionAndAttitude attitude = buildAttitude(orientation, nav, fathometer);
        float freqHz = snr2.channels.get(0).freqHz;
        
        // Reverse port samples and combine with starboard
        List<Integer> portSamples = new ArrayList<>(snr2.samples.get(PORT_IDX));
        Collections.reverse(portSamples);
        List<Integer> combinedSamples = new ArrayList<>(portSamples);
        combinedSamples.addAll(snr2.samples.get(STARBOARD_IDX));
        
        double[] samples = combinedSamples.stream().mapToDouble(s -> s / scaleFactor).toArray();
        double[] samplesNormalized = SidescanUtil.applyNormalizationAndTVG(samples, config);
        
        return new SidescanLine(epochMs, (float)rangeMeters, attitude, freqHz, samplesNormalized);
    }

    @Override
    public ISidescanLine getLineAtTime(long timestamp, int subsystem, SidescanParameters config) {
        SidescanParameters effectiveConfig = config != null ? config : getDefaultParams();
        SidescanLineCacheKey cacheKey = new SidescanLineCacheKey(timestamp, effectiveConfig);

        ISidescanLine cached = sidescanLineCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            Map.Entry<Long, File> fileEntry = fileIndexMap.ceilingEntry(timestamp);
            if (fileEntry == null) {
                throw new IllegalStateException("No file found for timestamp " + timestamp);
            }
            File file = fileEntry.getValue();
            
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                Long snr2Key = snr2Index.ceilingKey(timestamp);
                if (snr2Key == null) {
                    throw new IllegalStateException("No SNR2 packet found for timestamp " + timestamp);
                }
                Long snr2Offset = snr2Index.get(snr2Key);
                SdsSNR2 snr2 = (SdsSNR2) readAt(file, raf, snr2Offset);

                Long orntKey = orntIndex.ceilingKey(timestamp);
                if (orntKey == null) {
                    throw new IllegalStateException("No Orientation packet found for timestamp " + timestamp);
                }
                Long orntOffset = orntIndex.get(orntKey);
                SdsOrientationDataPacket orientation = (SdsOrientationDataPacket) readAt(file, raf, orntOffset);

                Long navKey = navIndex.ceilingKey(timestamp);
                if (navKey == null) {
                    throw new IllegalStateException("No Navigation packet found for timestamp " + timestamp);
                }
                Long navOffset = navIndex.get(navKey);
                SdsNavigationDataPacket navigation = (SdsNavigationDataPacket) readAt(file, raf, navOffset);

                Long fthmKey = fthmIndex.ceilingKey(timestamp);
                if (fthmKey == null) {
                    throw new IllegalStateException("No Fathometer packet found for timestamp " + timestamp);
                }
                Long fthmOffset = fthmIndex.get(fthmKey);
                SdsFathometerDataPacket fathometer = (SdsFathometerDataPacket) readAt(file, raf, fthmOffset);

                ISidescanLine sidescanLine = buildSidescanLine(timestamp, snr2, orientation, navigation, fathometer, effectiveConfig);
                sidescanLineCache.put(cacheKey, sidescanLine);
                return sidescanLine;
            }
        } catch (UnknownTagException e) {
            logger.warning("Corrupted data at timestamp " + timestamp + ": " + e.getMessage());
            return null;
        } catch (Exception e) {
            logger.severe("Error getting line at time " + timestamp + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public ArrayList<Integer> getSubsystemList() {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(0);
        return list;
    }

    @Override
    public void cleanup() {
        syncIndex.clear();
        snr2Index.clear();
        navIndex.clear();
        fthmIndex.clear();
        orntIndex.clear();
        fileReadCache.clear();
        sidescanLineCache.clear();
    }

    @Override
    public SidescanParameters getDefaultParams() {
        return new SidescanParameters(0.2, 100.0);
    }
}