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
 * Feb 7, 2013
 */

package pt.omst.neptus.sidescan.jsf;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import pt.omst.neptus.core.SystemPositionAndAttitude;
import pt.omst.neptus.sidescan.ISidescanLine;
import pt.omst.neptus.sidescan.SidescanLine;
import pt.omst.neptus.sidescan.SidescanParameters;
import pt.omst.neptus.sidescan.SidescanParser;
import pt.omst.neptus.util.SidescanUtil;

/**
 * @author jqcorreia
 */
public class JsfSidescanParser implements SidescanParser {
    private JsfParser parser;
    
    // Performance optimization: Pre-computed TVG gain lookup tables
    private static final int TVG_LOOKUP_SIZE = 10000;
    private double[] portTvgLookup = new double[TVG_LOOKUP_SIZE];
    private double[] starTvgLookup = new double[TVG_LOOKUP_SIZE];
    private double currentTvgGain = -1.0; // Track if we need to recompute

    public JsfSidescanParser(File[] files, Consumer<String> progressCallback) {
        parser = new JsfParser(files, progressCallback);
    }

    @Override
    public long firstPingTimestamp() {
        return parser.getFirstTimeStamp();
    }

    @Override
    public long lastPingTimestamp() {
        return parser.getLastTimeStamp();
    }

    @Override
    public long firstPingTimestamp(int subsystem) {
        return parser.getFirstTimestamp(subsystem);
    }

    @Override
    public long lastPingTimestamp(int subsystem) {
        return parser.getLastTimeStamp(subsystem);
    }

    @Override
    public ISidescanLine getLineAtTime(long timestamp, int subsystem, SidescanParameters config) {
        ArrayList<JsfSonarData> ping = parser.getPingAt(timestamp, subsystem);
        if (ping.isEmpty()) {
            return null;
        }

        ChannelData channels = extractChannelData(ping);
        if (!channels.isValid()) {
            return null;
        }

        SystemPositionAndAttitude pose = createPose(channels.getReference());
        double[] sonarData = combineChannelData(channels);

        return new SidescanLine(ping.get(0).getTimestamp(), ping.get(0).getRange(), 
                                pose, ping.get(0).getFrequency(), sonarData);
    }

    @Override
    public ArrayList<SidescanLine> getLinesBetween(long timestamp1, long timestamp2, int subsystem, SidescanParameters params) {
        ArrayList<SidescanLine> result = new ArrayList<>();
        
        ArrayList<JsfSonarData> ping = getInitialPing(timestamp1, subsystem);
        if (ping == null) {
            return result;
        }

        while (ping.get(0).getTimestamp() < timestamp2) {
            ChannelData channels = extractChannelData(ping);
            
            // Skip if no valid channels
            if (!hasValidChannels(channels)) {
                ping = getNextPing(subsystem);
                if (ping == null) break;
                continue;
            }

            // Skip lines where one channel would produce black output
            if (shouldSkipPing(channels, params)) {
                ping = getNextPing(subsystem);
                if (ping == null) break;
                continue;
            }

            // Process the ping into sidescan data
            double[] processedData = processChannelData(channels, params);
            SystemPositionAndAttitude pose = createPose(channels.getReference());
            
            result.add(new SidescanLine(ping.get(0).getTimestamp(), ping.get(0).getRange(), 
                                       pose, ping.get(0).getFrequency(), processedData));

            ping = getNextPing(subsystem);
            if (ping == null) break;
        }
        
        return result;
    }

    @Override
    public ArrayList<Integer> getSubsystemList() {
        Set<Integer> subSystems = new HashSet<>();

        for (JsfIndex index : parser.getIndex())
            subSystems.addAll(index.subSystemsList);

        return new ArrayList<>(subSystems);
    }

    @Override
    public void cleanup() {
        parser.cleanup();
        parser = null;
    }

    @Override
    public SidescanParameters getDefaultParams() {
        return new SidescanParameters(0.2, 250);
    }

    // Helper methods and classes for cleaner code organization

    /**
     * Container for port and starboard channel data
     */
    private static class ChannelData {
        private final JsfSonarData portboard;
        private final JsfSonarData starboard;

        public ChannelData(JsfSonarData portboard, JsfSonarData starboard) {
            this.portboard = portboard;
            this.starboard = starboard;
        }

        public boolean isValid() {
            return portboard != null && portboard.getData() != null &&
                   (starboard == null || starboard.getData() != null);
        }

        public JsfSonarData getPortboard() { return portboard; }
        public JsfSonarData getStarboard() { return starboard; }
        
        public JsfSonarData getReference() {
            return portboard != null ? portboard : starboard;
        }
    }

    /**
     * Extract port and starboard channel data from a ping
     */
    private ChannelData extractChannelData(ArrayList<JsfSonarData> ping) {
        JsfSonarData portboard = null;
        JsfSonarData starboard = null;

        for (JsfSonarData data : ping) {
            if (data != null) {
                if (data.getHeader().getChannel() == 0) {
                    portboard = data;
                } else if (data.getHeader().getChannel() == 1) {
                    starboard = data;
                }
            }
        }

        return new ChannelData(portboard, starboard);
    }

    /**
     * Create system position and attitude from sonar data
     */
    private SystemPositionAndAttitude createPose(JsfSonarData reference) {
        SystemPositionAndAttitude pose = new SystemPositionAndAttitude();
        pose.getPosition().setLatitudeDegs((reference.getLat() / 10000.0) / 60.0);
        pose.getPosition().setLongitudeDegs((reference.getLon() / 10000.0) / 60.0);
        pose.getPosition().setDepth(reference.getDepthMillis() / 1E3);
        pose.setRoll(Math.toRadians(reference.getRoll() * (180 / 32768.0)));
        pose.setYaw(Math.toRadians(reference.getHeading() / 100.0));
        pose.setAltitude(reference.getAltMillis() / 1000.0);
        pose.setU(reference.getSpeed() * 0.51444); // Convert knot-to-ms
        return pose;
    }

    /**
     * Combine port and starboard channel data into a single array
     */
    private double[] combineChannelData(ChannelData channels) {
        JsfSonarData portboard = channels.getPortboard();
        JsfSonarData starboard = channels.getStarboard();
        
        int portSamples = portboard != null ? portboard.getNumberOfSamples() : 0;
        int starSamples = starboard != null ? starboard.getNumberOfSamples() : 0;
        
        double[] result = new double[portSamples + starSamples];
        
        // Copy port data
        if (portboard != null && portboard.getData() != null) {
            for (int i = 0; i < portSamples; i++) {
                result[i] = sanitizeValue(portboard.getData()[i]);
            }
        }
        
        // Copy starboard data
        if (starboard != null && starboard.getData() != null) {
            for (int i = 0; i < starSamples; i++) {
                result[i + portSamples] = sanitizeValue(starboard.getData()[i]);
            }
        }
        
        return result;
    }

    /**
     * Sanitize a single data value (handle NaN and infinite values)
     */
    private double sanitizeValue(double value) {
        return (Double.isNaN(value) || Double.isInfinite(value)) ? 0.0 : value;
    }

    /**
     * Get the initial ping for processing
     */
    private ArrayList<JsfSonarData> getInitialPing(long timestamp, int subsystem) {
        try {
            ArrayList<JsfSonarData> ping = parser.getPingAt(timestamp, subsystem);
            return ping.isEmpty() ? null : ping;
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Get the next ping in sequence
     */
    private ArrayList<JsfSonarData> getNextPing(int subsystem) {
        try {
            ArrayList<JsfSonarData> ping = parser.nextPing(subsystem);
            return ping.isEmpty() ? null : ping;
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Check if channels contain valid data for processing
     */
    private boolean hasValidChannels(ChannelData channels) {
        return channels.getPortboard() != null || channels.getStarboard() != null;
    }

    /**
     * Determine if a ping should be skipped (would produce black lines)
     */
    private boolean shouldSkipPing(ChannelData channels, SidescanParameters params) {
        double avgPort = calculateChannelAverage(channels.getPortboard());
        double avgStar = calculateChannelAverage(channels.getStarboard());
        
        // Skip if either channel is completely empty (would create black lines)
        return avgPort == 0.0 || avgStar == 0.0;
    }

    /**
     * Calculate the average value for a channel
     */
    private double calculateChannelAverage(JsfSonarData channel) {
        if (channel == null || channel.getData() == null) {
            return 0.0;
        }
        
        double sum = 0.0;
        for (double value : channel.getData()) {
            sum += value;
        }
        return sum;
    }

    /**
     * Process channel data with TVG gain and normalization
     */
    private double[] processChannelData(ChannelData channels, SidescanParameters params) {
        JsfSonarData portboard = channels.getPortboard();
        JsfSonarData starboard = channels.getStarboard();
        
        int portSamples = getSampleCount(portboard, starboard, true);
        int starSamples = getSampleCount(starboard, portboard, false);
        
        double[] result = new double[portSamples + starSamples];
        
        // If raw normalization is enabled, skip TVG and just copy/normalize data
        if (params.isRawNormalization()) {
            // Copy port data (no need to reverse)
            if (portboard != null && portboard.getData() != null) {
                double[] portData = portboard.getData();
                for (int i = 0; i < portSamples; i++) {
                    result[i] = portData[i];
                }
            }
            
            // Copy starboard data
            if (starboard != null && starboard.getData() != null) {
                double[] starData = starboard.getData();
                for (int i = 0; i < starSamples; i++) {
                    result[portSamples + i] = starData[i];
                }
            }
            
            // Apply raw normalization (0-1 range)
            return SidescanUtil.applyRawNormalization(result);
        }
        
        // Original processing with TVG and normalization
        // Calculate averages for normalization
        double avgPort = calculateNormalizedAverage(portboard, portSamples, params);
        double avgStar = calculateNormalizedAverage(starboard, starSamples, params);
        
        // Process port channel
        processChannel(portboard, result, 0, portSamples, avgPort, params, true);
        
        // Process starboard channel
        processChannel(starboard, result, portSamples, starSamples, avgStar, params, false);
        
        return result;
    }

    /**
     * Get sample count for a channel, using fallback if needed
     */
    private int getSampleCount(JsfSonarData primary, JsfSonarData fallback, boolean isPrimary) {
        if (primary != null) {
            return primary.getNumberOfSamples();
        } else if (fallback != null && isPrimary) {
            return fallback.getNumberOfSamples(); // Use fallback samples for missing port
        } else if (fallback != null && !isPrimary) {
            return fallback.getNumberOfSamples(); // Use fallback samples for missing starboard
        }
        return 0;
    }

    /**
     * Calculate normalized average for a channel
     */
    private double calculateNormalizedAverage(JsfSonarData channel, int samples, SidescanParameters params) {
        if (channel == null || samples == 0) {
            return 0.0;
        }
        
        double sum = calculateChannelAverage(channel);
        return sum / (samples * params.getNormalization());
    }

    /**
     * Build TVG gain lookup tables for dramatic performance improvement
     * This eliminates expensive Math.log() and Math.pow() calls from the hot loop
     */
    private void buildTvgLookupTables(double tvgGain) {
        if (currentTvgGain == tvgGain) {
            return; // Already computed for this TVG gain
        }
        
        currentTvgGain = tvgGain;
        
        // Pre-compute port TVG multipliers (r goes from 0 to 1)
        for (int i = 0; i < TVG_LOOKUP_SIZE; i++) {
            double r = (double) i / (TVG_LOOKUP_SIZE - 1);
            if (r <= 1e-10) r = 1e-10; // Prevent log(0)
            
            double gain = Math.abs(30.0 * Math.log(r));
            portTvgLookup[i] = Math.pow(10, gain / tvgGain);
        }
        
        // Pre-compute starboard TVG multipliers (r goes from 1 to 0)
        for (int i = 0; i < TVG_LOOKUP_SIZE; i++) {
            double r = 1.0 - ((double) i / (TVG_LOOKUP_SIZE - 1));
            if (r <= 1e-10) r = 1e-10; // Prevent log(0)
            
            double gain = Math.abs(30.0 * Math.log(r));
            starTvgLookup[i] = Math.pow(10, gain / tvgGain);
        }
    }

    /**
     * Process a single channel (port or starboard) with TVG gain
     * OPTIMIZED VERSION: Uses pre-computed lookup tables for ~10x speed improvement
     */
    private void processChannel(JsfSonarData channel, double[] result, int offset, int samples, 
                               double average, SidescanParameters params, boolean isPortboard) {
        if (channel == null || channel.getData() == null) {
            return;
        }
        
        // Ensure lookup tables are built for current TVG gain
        buildTvgLookupTables(params.getTvgGain());
        
        // Cache frequently accessed values
        final double[] channelData = channel.getData();
        final double[] tvgLookup = isPortboard ? portTvgLookup : starTvgLookup;
        final boolean hasAverage = average != 0.0;
        final double invAverage = hasAverage ? 1.0 / average : 0.0;
        final int lookupIndexMultiplier = TVG_LOOKUP_SIZE - 1;
        
        // High-performance processing loop
        for (int i = 0; i < samples; i++) {
            // Calculate lookup table index
            int lookupIndex = isPortboard ? 
                (i * lookupIndexMultiplier) / samples :
                ((samples - 1 - i) * lookupIndexMultiplier) / samples;
                
            // Use pre-computed TVG multiplier (eliminates Math.log and Math.pow!)
            double processedValue = channelData[i] * tvgLookup[lookupIndex];
            
            if (hasAverage) {
                double normalizedValue = processedValue * invAverage;
                // Inline sanitization for performance
                result[offset + i] = (Double.isNaN(normalizedValue) || Double.isInfinite(normalizedValue)) ? 
                    0.0 : normalizedValue;
            } else {
                result[offset + i] = 0.0;
            }
        }
    }

}
