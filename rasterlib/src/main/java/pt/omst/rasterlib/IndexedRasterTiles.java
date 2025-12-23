//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.I18n;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for finding potential markers in IndexedRaster data using gradient descent algorithms.
 * This class adapts sidescan marker finding algorithms to work with IndexedRaster and SampleDescription data.
 */
@Slf4j
public class IndexedRasterTiles {

    private final IndexedRaster raster;

    public IndexedRasterTiles(IndexedRaster raster) {
        this.raster = raster;
    }

    /**
     * Calculate location from a sample at a given range index.
     * Adapted from ISidescanLine.calcPointFromIndex to work with IndexedRaster.
     * 
     * @param sample The sample description containing pose information
     * @param xIndex The x index in the raster (0 to image width)
     * @param imageWidth The width of the raster image
     * @param slantCorrection Whether to apply slant correction
     * @return LocationType at the calculated position
     */
    private LocationType calcPointFromIndex(SampleDescription sample, int xIndex, int imageWidth, boolean slantCorrection) {
        Pose pose = sample.getPose();
        LocationType location = new LocationType(pose.getLatitude(), pose.getLongitude());
        
        double range = raster.getSensorInfo().getMaxRange();
        // Calculate slant range from x index
        double slantRange = xIndex * (range * 2.0 / imageWidth) - range;
        
        // Convert slant range to ground range if requested
        double distance = slantRange;
        if (slantCorrection) {
            double alt = pose.getAltitude() != null ? pose.getAltitude() : 0;
            alt = Math.max(alt, 0);
            // Only apply correction if slant range exceeds altitude
            if (Math.abs(slantRange) > alt) {
                distance = Math.signum(slantRange) * Math.sqrt(slantRange * slantRange - alt * alt);
            } else {
                distance = 0; // Within nadir zone
            }
        }

        // If distance is essentially zero, return the vehicle position
        if (Math.abs(distance) < 0.01) {
            return location;
        }

        // Use polar coordinates: azimuth and offset distance
        double heading = pose.getPsi() != null ? pose.getPsi() : 0;
        if (slantRange >= 0) {
            // Starboard side (positive range) - perpendicular to heading (+ 90 degrees)
            location.setAzimuth(Math.toDegrees(heading) + 90);
        } else {
            // Port side (negative range) - perpendicular to heading (- 90 degrees)
            location.setAzimuth(Math.toDegrees(heading) - 90);
        }
        
        location.setOffsetDistance(Math.abs(distance));
        location.convertToAbsoluteLatLonDepth();

        return location;
    }

    /**
     * Get the distance from nadir for a given x index.
     * 
     * @param xIndex The x index in the raster
     * @param imageWidth The width of the raster image
     * @param slantCorrection Whether to apply slant correction
     * @param altitude The altitude of the sensor
     * @return Distance from nadir (negative means port-side)
     */
    private double getDistanceFromIndex(int xIndex, int imageWidth, boolean slantCorrection, double altitude) {
        double range = raster.getSensorInfo().getMaxRange();
        double distance = xIndex * (range * 2.0 / imageWidth) - range;
        
        if (slantCorrection) {
            altitude = Math.max(altitude, 0);
            double distanceG = Math.signum(distance) * Math.sqrt(Math.abs(distance * distance - altitude * altitude));
            distance = Double.isNaN(distanceG) ? 0 : distanceG;
        }
        return distance;
    }

    /**
     * Find the minimum distance x index to target point using gradient descent.
     * Adapted from the original findMinX algorithm to work with IndexedRaster.
     * 
     * @param targetPoint The target location to find
     * @param sample The sample description (equivalent to a sidescan line)
     * @param imageWidth The width of the raster image
     * @return The x index with minimum distance to target
     */
    private int findMinX(LocationType targetPoint, SampleDescription sample, int imageWidth) {
        int x = 0;
        int step = 500;
        double learningRate = 100;
        
        LocationType point0 = calcPointFromIndex(sample, x, imageWidth, true);
        double prevDistance = targetPoint.getHorizontalDistanceInMeters(point0);
        double distance = prevDistance;
        
        while (step > 1) {
            int prevX = x;
            int testX = Math.max(0, Math.min(imageWidth - 1, x + step));
            LocationType pointTest = calcPointFromIndex(sample, testX, imageWidth, true);
            double newDistance = targetPoint.getHorizontalDistanceInMeters(pointTest);
            double prevDistanceDerivative = ((newDistance * newDistance) - (prevDistance * prevDistance)) / step;
            
            x = (int) (x - learningRate * prevDistanceDerivative);
            x = Math.max(0, Math.min(imageWidth - 1, x));
            
            LocationType pointCurrent = calcPointFromIndex(sample, x, imageWidth, true);
            distance = targetPoint.getHorizontalDistanceInMeters(pointCurrent);
            
            if (distance > prevDistance) {
                learningRate /= 2;
                x = prevX;
            }
            
            step = Math.abs(prevX - x);
            prevDistance = distance;
        }
        return x;
    }

    /**
     * Find the sample index closest to the target point using gradient descent.
     * Adapted from the original findClosestTimestamp algorithm.
     * 
     * @param initialIndex Starting sample index
     * @param targetPoint The target location
     * @param imageWidth The width of the raster image
     * @return The sample index closest to the target
     */
    private int findClosestSampleIndex(int initialIndex, LocationType targetPoint, int imageWidth) {
        int sampleIndex = initialIndex;
        int step = 100;
        double learningRate = 1;
        
        SampleDescription sample = raster.getSamples().get(sampleIndex);
        LocationType point0 = calcPointFromIndex(sample, 0, imageWidth, true);
        double prevDistance = targetPoint.getHorizontalDistanceInMeters(point0);
        
        while (step > 1) {
            int prevIndex = sampleIndex;
            sampleIndex = sampleIndex + step;
            
            if (sampleIndex >= raster.getSamples().size()) {
                sampleIndex = raster.getSamples().size() - 1;
                break;
            }
            if (sampleIndex < 0) {
                sampleIndex = 0;
                break;
            }
            
            sample = raster.getSamples().get(sampleIndex);
            LocationType pointTest = calcPointFromIndex(sample, 0, imageWidth, true);
            double newDistance = targetPoint.getHorizontalDistanceInMeters(pointTest);
            
            if (newDistance > prevDistance) {
                learningRate /= 2;
                sampleIndex = prevIndex;
            } else {
                double prevDistanceDerivative = ((newDistance * newDistance) - (prevDistance * prevDistance)) / step;
                sampleIndex = (int) Math.max(0, (sampleIndex - learningRate * prevDistanceDerivative));
            }
            
            step = Math.abs(prevIndex - sampleIndex);
            prevDistance = newDistance;
        }
        
        return Math.max(0, Math.min(raster.getSamples().size() - 1, sampleIndex));
    }

    /**
     * Generate potential observations for a given target contact.
     * Finds all locations in the raster that match the contact's latitude/longitude,
     * separated by at least 5 seconds.
     * 
     * @param targetContact The target contact to find matches for
     * @param imageWidth The width of the raster image
     * @param observationConsumer Consumer to receive generated observations
     * @return Number of observations found
     */
    public int generatePotentialObservations(Contact targetContact, int imageWidth, 
                                            java.util.function.Consumer<Observation> observationConsumer) {
        LocationType targetPoint = new LocationType(
            Math.toRadians(targetContact.getLatitude()), 
            Math.toRadians(targetContact.getLongitude())
        );
        
        // Get the earliest observation timestamp from the target contact to use as exclusion reference
        long nextMarkTimeMs = targetContact.getObservations().isEmpty() 
            ? System.currentTimeMillis()
            : targetContact.getObservations().get(0).getTimestamp().toInstant().toEpochMilli();
        
        int potentialObservationIdx = 0;
        int timeOffsetMs = 5000;
        
        long firstTimestamp = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli();
        
        int sampleIndex = 0;
        
        while (sampleIndex < raster.getSamples().size()) {
            SampleDescription sample = raster.getSamples().get(sampleIndex);
            long ts = sample.getTimestamp().toInstant().toEpochMilli();
            
            // Skip if too close to the original mark
            if (ts > nextMarkTimeMs - timeOffsetMs && ts < nextMarkTimeMs + timeOffsetMs) {
                sampleIndex++;
                continue;
            }
            
            int midX = imageWidth / 2;
            LocationType midPoint = calcPointFromIndex(sample, midX, imageWidth, true);
            double range = raster.getSensorInfo().getMaxRange();
            
            double distanceToMid = targetPoint.getHorizontalDistanceInMeters(midPoint);
            if (distanceToMid > range) {
                sampleIndex++;
                continue;
            }
            
            int minX = findMinX(targetPoint, sample, imageWidth);
            LocationType minP = calcPointFromIndex(sample, minX, imageWidth, true);
            double minDistance = targetPoint.getHorizontalDistanceInMeters(minP);
            
            if (log.isDebugEnabled()) {
                log.debug("Sample {}: distanceToMid={}, minDistance={}", sampleIndex, distanceToMid, minDistance);
            }
            
            if (minDistance < 2) {
                int bestIndex = findClosestSampleIndex(sampleIndex, targetPoint, imageWidth);
                SampleDescription bestSample = raster.getSamples().get(bestIndex);
                long bestTs = bestSample.getTimestamp().toInstant().toEpochMilli();
                
                if (bestTs > nextMarkTimeMs - timeOffsetMs && bestTs < nextMarkTimeMs + timeOffsetMs) {
                    sampleIndex++;
                    continue;
                }
                
                minX = findMinX(targetPoint, bestSample, imageWidth);
                double altitude = bestSample.getPose().getAltitude() != null ? bestSample.getPose().getAltitude() : 0;
                double distNadir = getDistanceFromIndex(minX, imageWidth, true, altitude);
                minP = calcPointFromIndex(bestSample, minX, imageWidth, true);
                
                log.info("Min Distance improved from " + minDistance + " to " + 
                         targetPoint.getHorizontalDistanceInMeters(minP));
                
                // Create an observation for this potential match
                Observation observation = new Observation();
                observation.setUuid(UUID.randomUUID());
                observation.setLatitude(targetPoint.getLatitudeRads());
                observation.setLongitude(targetPoint.getLongitudeRads());
                observation.setDepth(bestSample.getPose().getDepth() != null ? bestSample.getPose().getDepth() : 0.0);
                observation.setTimestamp(bestSample.getTimestamp());
                observation.setRasterFilename(raster.getFilename());
                observation.setSystemName(raster.getSensorInfo() != null && raster.getSensorInfo().getSystemName() != null 
                    ? raster.getSensorInfo().getSystemName() 
                    : "unknown");
                
                potentialObservationIdx++;
                sampleIndex += 4; // Skip ahead a bit
                
                // Add the observation to the target contact
                targetContact.getObservations().add(observation);
                
                if (observationConsumer != null) {
                    observationConsumer.accept(observation);
                }
            }
            sampleIndex++;
        }
        
        return potentialObservationIdx;
    }

    /**
     * Generate potential observations with a message dialog.
     * 
     * @param targetContact The target contact to find matches for
     * @param imageWidth The width of the raster image
     * @param observationConsumer Consumer to receive generated observations
     * @return true if observations were found
     */
    public boolean generatePotentialObservationsWithMessage(Contact targetContact, int imageWidth,
                                                            java.util.function.Consumer<Observation> observationConsumer) {
        int count = generatePotentialObservations(targetContact, imageWidth, observationConsumer);
        
        String message = I18n.text("Found " + count + " potential observations for " + targetContact.getLabel());
        try {
            GuiUtils.infoMessage(null, I18n.text("Potential Observations"), message);
        } catch (java.awt.HeadlessException e) {
            // Running in headless mode (e.g., during tests), just log the message
            log.info(message);
        }
        
        return count > 0;
    }
}
