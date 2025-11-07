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

package pt.omst.neptus.sidescan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import pt.omst.neptus.core.SystemPositionAndAttitude;

/**
 * Interface for sidescan data parsers
 * @author jccorreia
 */
public interface SidescanParser {
    /**
     * Returns the timestamp of the first ping in the file
     * @return timestamp in milliseconds
     */
    long firstPingTimestamp();

    /**
     * Returns the timestamp of the last ping in the file
     * @return timestamp in milliseconds
     */
    long lastPingTimestamp();

    /**
     * Returns the sidescan lines between two timestamps for a given subsystem
     * @param timestamp1 start timestamp in milliseconds
     * @param timestamp2 end timestamp in milliseconds
     * @param subsystem the subsystem to get lines from
     * @param config the sidescan parameters configuration
     * @return list of sidescan lines
     */
    long firstPingTimestamp(int subsystem);

    /**
     * Returns the timestamp of the first ping for a given subsystem
     * @param subsystem the subsystem to query
     * @return timestamp in milliseconds
     */
    long lastPingTimestamp(int subsystem);

    /**
     * Returns the timestamp of the last ping for a given subsystem
     * @param subsystem the subsystem to query
     * @param timestamp1 the start timestamp in milliseconds
     * @param timestamp2 the end timestamp in milliseconds
     * @param subsystem the subsystem to get lines from
     * @param config the sidescan parameters configuration
     * @return list of sidescan lines
     */
    ArrayList<SidescanLine> getLinesBetween(long timestamp1, long timestamp2, int subsystem, SidescanParameters config);

    /**
     * Returns the sidescan line at a given timestamp for a given subsystem
     * @param timestamp in milliseconds
     * @param subsytem the subsystem to get the line from
     * @param config the sidescan parameters configuration
     * @return the sidescan line, or null if not found
     */
    ISidescanLine getLineAtTime(long timestamp, int subsytem, SidescanParameters config);

    /**
     * Returns the list of subsystems available in the sidescan data
     * @return list of subsystem identifiers
     */
    ArrayList<Integer> getSubsystemList();

    /**
     * Cleans up any resources held by the parser
     */
    void cleanup();

    /**
     * Returns a default sidescan parameters configuration
     * @return the default SidescanParameters object
     */
    SidescanParameters getDefaultParams();

    /**
     * Creates a stream that gradually fetches sidescan lines as they are consumed.
     * This lazy evaluation approach only loads lines when the stream is actually read,
     * which is more memory-efficient for large datasets.
     *
     * @param subsystem The subsystem to fetch lines from
     * @param config The sidescan parameters configuration
     * @return A stream of SidescanLine objects
     */
    default Stream<SidescanLine> getLinesStream(int subsystem, SidescanParameters config) {
        long firstTimestamp = firstPingTimestamp(subsystem);
        long lastTimestamp = lastPingTimestamp(subsystem);
        
        Iterator<SidescanLine> iterator = new Iterator<SidescanLine>() {
            private long currentTimestamp = firstTimestamp;
            private SidescanLine nextLine = null;
            private boolean hasCheckedNext = false;
            
            @Override
            public boolean hasNext() {
                if (!hasCheckedNext) {
                    if (currentTimestamp > lastTimestamp) {
                        nextLine = null;
                    } else {
                        ISidescanLine line = getLineAtTime(currentTimestamp, subsystem, config);
                        if (line instanceof SidescanLine) {
                            nextLine = (SidescanLine) line;
                            // Move to next timestamp
                            currentTimestamp = nextLine.getTimestampMillis() + 1;
                        } else {
                            nextLine = null;
                        }
                    }
                    hasCheckedNext = true;
                }
                return nextLine != null;
            }
            
            @Override
            public SidescanLine next() {
                if (!hasCheckedNext) {
                    hasNext();
                }
                hasCheckedNext = false;
                if (nextLine == null) {
                    throw new java.util.NoSuchElementException();
                }
                return nextLine;
            }
        };
        
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
            false
        );
    }
    
    /**
     * Returns a stream of SystemPositionAndAttitude objects for sidescan lines
     * @param subsystem the subsystem to get positions from
     * @return a stream of SystemPositionAndAttitude objects
     */
    default Stream<SystemPositionAndAttitude> getSidescanPositions(int subsystem) {
        return getLinesStream(subsystem, getDefaultParams())
                .map(SidescanLine::getState)
                .filter(state -> state != null);
    }
    
}
