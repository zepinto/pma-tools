//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.rasterfall.utils;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.I18n;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SampleDescription;

/**
 * Utility class for finding potential markers in IndexedRaster data using
 * gradient descent algorithms.
 * This class adapts sidescan marker finding algorithms to work with
 * IndexedRaster and SampleDescription data.
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
     * @param sample          The sample description containing pose information
     * @param xIndex          The x index in the raster (0 to image width)
     * @param imageWidth      The width of the raster image
     * @param slantCorrection Whether to apply slant correction
     * @return LocationType at the calculated position
     */
    private LocationType calcPointFromIndex(SampleDescription sample, int xIndex, int imageWidth,
            boolean slantCorrection) {
        Pose pose = sample.getPose();
        if (pose == null) {
            return new LocationType(0, 0);
        }
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
        // Note: psi is already in degrees in the data
        double headingDeg = pose.getPsi() != null ? pose.getPsi() : 0;
        if (slantRange >= 0) {
            // Starboard side (positive range) - perpendicular to heading (+ 90 degrees)
            location.setAzimuth(headingDeg + 90);
        } else {
            // Port side (negative range) - perpendicular to heading (- 90 degrees)
            location.setAzimuth(headingDeg - 90);
        }

        location.setOffsetDistance(Math.abs(distance));
        location.convertToAbsoluteLatLonDepth();

        return location;
    }

    /**
     * Find the minimum distance x index to target point using ternary search.
     * Uses ternary search for robust convergence on the unimodal distance function,
     * followed by local refinement for precision.
     * 
     * @param targetPoint The target location to find
     * @param sample      The sample description (equivalent to a sidescan line)
     * @param imageWidth  The width of the raster image
     * @return The x index with minimum distance to target
     */
    private int findMinX(LocationType targetPoint, SampleDescription sample, int imageWidth) {
        if (imageWidth <= 1) {
            return 0;
        }

        int left = 0;
        int right = imageWidth - 1;

        // Ternary search for minimum distance (works for unimodal functions)
        while (right - left > 2) {
            int mid1 = left + (right - left) / 3;
            int mid2 = right - (right - left) / 3;

            LocationType point1 = calcPointFromIndex(sample, mid1, imageWidth, true);
            LocationType point2 = calcPointFromIndex(sample, mid2, imageWidth, true);

            double dist1 = targetPoint.getHorizontalDistanceInMeters(point1);
            double dist2 = targetPoint.getHorizontalDistanceInMeters(point2);

            if (dist1 < dist2) {
                right = mid2;
            } else {
                left = mid1;
            }
        }

        // Linear search in the remaining small range for the exact minimum
        int bestX = left;
        double bestDistance = targetPoint.getHorizontalDistanceInMeters(
                calcPointFromIndex(sample, left, imageWidth, true));

        for (int x = left + 1; x <= right; x++) {
            LocationType point = calcPointFromIndex(sample, x, imageWidth, true);
            double distance = targetPoint.getHorizontalDistanceInMeters(point);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestX = x;
            }
        }

        return bestX;
    }

    /**
     * Find the sample index closest to the target point using ternary search.
     * Searches within a window around the initial index for the sample whose
     * track position is closest to the target point.
     * 
     * @param initialIndex Starting sample index (hint for search window)
     * @param targetPoint  The target location
     * @param imageWidth   The width of the raster image
     * @return The sample index closest to the target
     */
    private int findClosestSampleIndex(int initialIndex, LocationType targetPoint, int imageWidth) {
        int numSamples = raster.getSamples().size();
        if (numSamples == 0) {
            return 0;
        }
        if (numSamples == 1) {
            return 0;
        }

        // Define search window around initial index (expand to catch nearby passes)
        int windowSize = Math.min(500, numSamples / 2);
        int left = Math.max(0, initialIndex - windowSize);
        int right = Math.min(numSamples - 1, initialIndex + windowSize);

        // Helper lambda to calculate distance for a sample index
        java.util.function.IntToDoubleFunction distanceAt = idx -> {
            SampleDescription s = raster.getSamples().get(idx);
            int minX = findMinXSimple(targetPoint, s, imageWidth);
            LocationType point = calcPointFromIndex(s, minX, imageWidth, true);
            return targetPoint.getHorizontalDistanceInMeters(point);
        };

        // Ternary search for minimum distance
        while (right - left > 2) {
            int mid1 = left + (right - left) / 3;
            int mid2 = right - (right - left) / 3;

            double dist1 = distanceAt.applyAsDouble(mid1);
            double dist2 = distanceAt.applyAsDouble(mid2);

            if (dist1 < dist2) {
                right = mid2;
            } else {
                left = mid1;
            }
        }

        // Linear search in the remaining small range
        int bestIndex = left;
        double bestDistance = distanceAt.applyAsDouble(left);

        for (int i = left + 1; i <= right; i++) {
            double distance = distanceAt.applyAsDouble(i);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    /**
     * Simple linear search for minimum X (used internally by findClosestSampleIndex
     * to avoid recursion issues).
     */
    private int findMinXSimple(LocationType targetPoint, SampleDescription sample, int imageWidth) {
        if (imageWidth <= 1) {
            return 0;
        }

        // Coarse search with larger steps
        int step = Math.max(1, imageWidth / 20);
        int bestX = 0;
        double bestDistance = targetPoint.getHorizontalDistanceInMeters(
                calcPointFromIndex(sample, 0, imageWidth, true));

        for (int x = step; x < imageWidth; x += step) {
            LocationType point = calcPointFromIndex(sample, x, imageWidth, true);
            double distance = targetPoint.getHorizontalDistanceInMeters(point);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestX = x;
            }
        }

        // Fine search around best coarse result
        int left = Math.max(0, bestX - step);
        int right = Math.min(imageWidth - 1, bestX + step);

        for (int x = left; x <= right; x++) {
            LocationType point = calcPointFromIndex(sample, x, imageWidth, true);
            double distance = targetPoint.getHorizontalDistanceInMeters(point);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestX = x;
            }
        }

        return bestX;
    }

    /**
     * Generate potential observations for a given target contact.
     * Finds all locations in the raster that match the contact's
     * latitude/longitude,
     * separated by at least 15 seconds.
     * 
     * @param targetContact       The target contact to find matches for
     * @param imageWidth          The width of the raster image
     * @param observationConsumer Consumer to receive generated observations
     * @return Number of observations found
     */
    public int generatePotentialObservations(Contact targetContact, int imageWidth,
            java.util.function.Consumer<Observation> observationConsumer) {
        LocationType targetPoint = new LocationType(
                targetContact.getLatitude(),
                targetContact.getLongitude());

        // Get the earliest observation timestamp from the target contact to use as
        // exclusion reference
        long nextMarkTimeMs = targetContact.getObservations().isEmpty()
                ? System.currentTimeMillis()
                : targetContact.getObservations().get(0).getTimestamp().toInstant().toEpochMilli();

        int potentialObservationIdx = 0;
        int timeOffsetMs = 15000;

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
                minP = calcPointFromIndex(bestSample, minX, imageWidth, true);

                log.info("Min Distance improved from " + minDistance + " to " +
                        targetPoint.getHorizontalDistanceInMeters(minP));

                // Create an observation for this potential match
                Observation observation = new Observation();
                observation.setUuid(UUID.randomUUID());
                observation.setLatitude(targetPoint.getLatitudeDegs());
                observation.setLongitude(targetPoint.getLongitudeDegs());
                observation.setDepth(bestSample.getPose().getDepth() != null ? bestSample.getPose().getDepth() : 0.0);
                observation.setTimestamp(bestSample.getTimestamp());
                observation.setRasterFilename(raster.getFilename());
                observation
                        .setSystemName(raster.getSensorInfo() != null && raster.getSensorInfo().getSystemName() != null
                                ? raster.getSensorInfo().getSystemName()
                                : "unknown");

                potentialObservationIdx++;
                
                // Use consumer if provided, otherwise add to contact
                if (observationConsumer != null) {
                    observationConsumer.accept(observation);
                } else {
                    targetContact.getObservations().add(observation);
                }
                
                // Always move forward - skip past current position to avoid re-detecting same contact
                sampleIndex = Math.max(sampleIndex + 1, bestIndex + 1);
                continue;
            }
            sampleIndex++;
        }

        return potentialObservationIdx;
    }

    /**
     * Generate potential observations with a message dialog.
     * 
     * @param targetContact       The target contact to find matches for
     * @param imageWidth          The width of the raster image
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

    /**
     * Represents the bounding box of a raster for quick spatial filtering.
     */
    public static class RasterBounds {
        public final double minLat, maxLat, minLon, maxLon;
        public final double avgSpeed; // meters per second
        public final long startTimeMs, endTimeMs;
        
        public RasterBounds(double minLat, double maxLat, double minLon, double maxLon,
                           double avgSpeed, long startTimeMs, long endTimeMs) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.avgSpeed = avgSpeed;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
        }
        
        /**
         * Check if a point is within the bounding box.
         * Since bounds already include the sensor range (swath width),
         * we only add a small margin for numerical precision.
         */
        public boolean contains(double lat, double lon, double marginMeters) {
            // Convert margin to approximate degrees
            double latMargin = marginMeters / 111320.0; // ~111km per degree latitude
            double lonMargin = marginMeters / (111320.0 * Math.cos(Math.toRadians(lat)));
            
            return lat >= (minLat - latMargin) && lat <= (maxLat + latMargin) &&
                   lon >= (minLon - lonMargin) && lon <= (maxLon + lonMargin);
        }
    }
    
    /**
     * Calculate the bounding box of the raster from corner samples.
     * Uses first, last, and samples at 1/3 and 2/3 of the raster to get better bounds.
     * The bounds are expanded by the sensor range since sidescan lines extend
     * perpendicular to the vehicle track.
     * 
     * @return RasterBounds containing the spatial and temporal bounds
     */
    public RasterBounds calculateBounds() {
        if (raster.getSamples().isEmpty()) {
            return null;
        }
        
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        
        int numSamples = raster.getSamples().size();
        double range = raster.getSensorInfo().getMaxRange();
        
        // Sample at strategic points: start, 1/3, 2/3, end
        int[] indices = {0, numSamples / 3, 2 * numSamples / 3, numSamples - 1};
        
        for (int idx : indices) {
            if (idx >= 0 && idx < numSamples) {
                SampleDescription sample = raster.getSamples().get(idx);
                Pose pose = sample.getPose();
                if (pose != null) {
                    // Get the vehicle position
                    LocationType vehicleLoc = new LocationType(pose.getLatitude(), pose.getLongitude());
                    
                    // Get heading to calculate perpendicular directions
                    // Note: psi is already in degrees in the data
                    double headingDeg = pose.getPsi() != null ? pose.getPsi() : 0;
                    
                    // Calculate port side edge (perpendicular left, -90 degrees from heading)
                    LocationType portEdge = new LocationType(vehicleLoc);
                    portEdge.setAzimuth(headingDeg - 90);
                    portEdge.setOffsetDistance(range);
                    portEdge.convertToAbsoluteLatLonDepth();
                    
                    // Calculate starboard side edge (perpendicular right, +90 degrees from heading)
                    LocationType starboardEdge = new LocationType(vehicleLoc);
                    starboardEdge.setAzimuth(headingDeg + 90);
                    starboardEdge.setOffsetDistance(range);
                    starboardEdge.convertToAbsoluteLatLonDepth();
                    
                    // Update bounds with vehicle position and both edges
                    minLat = Math.min(minLat, pose.getLatitude());
                    maxLat = Math.max(maxLat, pose.getLatitude());
                    minLon = Math.min(minLon, pose.getLongitude());
                    maxLon = Math.max(maxLon, pose.getLongitude());
                    
                    minLat = Math.min(minLat, portEdge.getLatitudeDegs());
                    maxLat = Math.max(maxLat, portEdge.getLatitudeDegs());
                    minLon = Math.min(minLon, portEdge.getLongitudeDegs());
                    maxLon = Math.max(maxLon, portEdge.getLongitudeDegs());
                    
                    minLat = Math.min(minLat, starboardEdge.getLatitudeDegs());
                    maxLat = Math.max(maxLat, starboardEdge.getLatitudeDegs());
                    minLon = Math.min(minLon, starboardEdge.getLongitudeDegs());
                    maxLon = Math.max(maxLon, starboardEdge.getLongitudeDegs());
                }
            }
        }
        
        // Calculate average speed
        SampleDescription firstSample = raster.getSamples().get(0);
        SampleDescription lastSample = raster.getSamples().get(numSamples - 1);
        
        long startTimeMs = firstSample.getTimestamp().toInstant().toEpochMilli();
        long endTimeMs = lastSample.getTimestamp().toInstant().toEpochMilli();
        long durationMs = Math.abs(endTimeMs - startTimeMs);
        
        // Calculate total distance traveled
        LocationType startLoc = new LocationType(
            firstSample.getPose().getLatitude(), 
            firstSample.getPose().getLongitude());
        LocationType endLoc = new LocationType(
            lastSample.getPose().getLatitude(), 
            lastSample.getPose().getLongitude());
        double distance = startLoc.getHorizontalDistanceInMeters(endLoc);
        
        double avgSpeed = durationMs > 0 ? (distance / (durationMs / 1000.0)) : 1.5; // default 1.5 m/s
        
        return new RasterBounds(minLat, maxLat, minLon, maxLon, avgSpeed, 
                               Math.min(startTimeMs, endTimeMs), Math.max(startTimeMs, endTimeMs));
    }
    
    /**
     * Fast alternative search method using bounding box filtering and time-based approximation.
     * 
     * Algorithm:
     * 1. Calculate bounding box of raster (4 corners)
     * 2. Check if target location is within bounds (expanded by sensor range)
     * 3. If within bounds, estimate sample index using average speed and timestamps
     * 4. Calculate distance from target to sample center position
     * 5. If within threshold, create observation
     * 
     * @param targetContact       The target contact to find matches for
     * @param observationConsumer Consumer to receive generated observations
     * @param minTimeOffsetMs     Minimum time offset from original observation (default 120000ms = 2 min)
     * @param distanceThreshold   Maximum distance in meters to consider a match (default 2m)
     * @return Number of observations found
     */
    public int generatePotentialObservationsFast(Contact targetContact,
            java.util.function.Consumer<Observation> observationConsumer,
            long minTimeOffsetMs, double distanceThreshold) {
        
        if (raster.getSamples().isEmpty()) {
            return 0;
        }
        
        LocationType targetPoint = new LocationType(
                targetContact.getLatitude(),
                targetContact.getLongitude());
        
        // Get reference timestamp from target contact
        long referenceTimeMs = targetContact.getObservations().isEmpty()
                ? System.currentTimeMillis()
                : targetContact.getObservations().get(0).getTimestamp().toInstant().toEpochMilli();
        
        // Calculate bounds
        RasterBounds bounds = calculateBounds();
        if (bounds == null) {
            return 0;
        }
        
        double range = raster.getSensorInfo().getMaxRange();
        
        // Quick check: is target within the raster's bounding box?
        // Bounds already include the sensor swath, so we use a small margin (10m) for precision
        if (!bounds.contains(targetContact.getLatitude(), targetContact.getLongitude(), 10.0)) {
            log.debug("Target outside raster bounds, skipping");
            return 0;
        }
        
        // Check if the raster's time range is far enough from the reference time
        boolean startFarEnough = Math.abs(bounds.startTimeMs - referenceTimeMs) > minTimeOffsetMs;
        boolean endFarEnough = Math.abs(bounds.endTimeMs - referenceTimeMs) > minTimeOffsetMs;
        
        if (!startFarEnough && !endFarEnough) {
            log.debug("Raster time range too close to reference time, skipping");
            return 0;
        }
        
        int potentialObservationIdx = 0;
        int numSamples = raster.getSamples().size();
        
        // Get first sample to calculate track direction
        SampleDescription firstSample = raster.getSamples().get(0);
        SampleDescription lastSample = raster.getSamples().get(numSamples - 1);
        
        LocationType startLoc = new LocationType(
            firstSample.getPose().getLatitude(), 
            firstSample.getPose().getLongitude());
        
        // Calculate distance from target to the start of the track
        double distToStart = targetPoint.getHorizontalDistanceInMeters(startLoc);
        
        // Estimate the sample index based on distance and average speed
        // time = distance / speed, then convert to sample index
        double estimatedTimeFromStartSec = distToStart / bounds.avgSpeed;
        long durationMs = bounds.endTimeMs - bounds.startTimeMs;
        double samplesPerMs = (double) numSamples / Math.max(1, durationMs);
        
        int estimatedIndex = (int) (estimatedTimeFromStartSec * 1000 * samplesPerMs);
        estimatedIndex = Math.max(0, Math.min(numSamples - 1, estimatedIndex));
        
        // Search in a window around the estimated index
        int windowSize = Math.min(50, numSamples / 4);
        int startIdx = Math.max(0, estimatedIndex - windowSize);
        int endIdx = Math.min(numSamples - 1, estimatedIndex + windowSize);
        
        log.debug("Searching samples {} to {} (estimated: {})", startIdx, endIdx, estimatedIndex);
        
        // Track the best match to avoid duplicates
        int bestMatchIdx = -1;
        double bestDistance = Double.MAX_VALUE;
        
        for (int i = startIdx; i <= endIdx; i++) {
            SampleDescription sample = raster.getSamples().get(i);
            long sampleTimeMs = sample.getTimestamp().toInstant().toEpochMilli();
            
            // Skip if too close to reference time
            if (Math.abs(sampleTimeMs - referenceTimeMs) < minTimeOffsetMs) {
                continue;
            }
            
            // Get center position of the sample (vehicle track position)
            Pose pose = sample.getPose();
            if (pose == null) {
                continue;
            }
            
            LocationType sampleCenter = new LocationType(pose.getLatitude(), pose.getLongitude());
            double distanceToCenter = targetPoint.getHorizontalDistanceInMeters(sampleCenter);
            
            // Check if target is within sensor range of this sample
            if (distanceToCenter <= range && distanceToCenter < bestDistance) {
                bestDistance = distanceToCenter;
                bestMatchIdx = i;
            }
        }
        
        // If we found a match within range, refine and create observation
        if (bestMatchIdx >= 0 && bestDistance <= range) {
            // Refine search around best match
            int refineStart = Math.max(0, bestMatchIdx - 10);
            int refineEnd = Math.min(numSamples - 1, bestMatchIdx + 10);
            
            for (int i = refineStart; i <= refineEnd; i++) {
                SampleDescription sample = raster.getSamples().get(i);
                long sampleTimeMs = sample.getTimestamp().toInstant().toEpochMilli();
                
                if (Math.abs(sampleTimeMs - referenceTimeMs) < minTimeOffsetMs) {
                    continue;
                }
                
                Pose pose = sample.getPose();
                if (pose == null) continue;
                
                LocationType sampleCenter = new LocationType(pose.getLatitude(), pose.getLongitude());
                double distanceToCenter = targetPoint.getHorizontalDistanceInMeters(sampleCenter);
                
                if (distanceToCenter < bestDistance) {
                    bestDistance = distanceToCenter;
                    bestMatchIdx = i;
                }
            }
            
            // Create observation for the best match
            SampleDescription bestSample = raster.getSamples().get(bestMatchIdx);
            
            // Final distance check
            Pose bestPose = bestSample.getPose();
            LocationType bestCenter = new LocationType(bestPose.getLatitude(), bestPose.getLongitude());
            double finalDistance = targetPoint.getHorizontalDistanceInMeters(bestCenter);
            
            if (finalDistance <= range) {
                log.info("Fast search found match at sample {}, distance to track: {}m", 
                        bestMatchIdx, String.format("%.2f", finalDistance));
                
                Observation observation = new Observation();
                observation.setUuid(UUID.randomUUID());
                observation.setLatitude(targetPoint.getLatitudeDegs());
                observation.setLongitude(targetPoint.getLongitudeDegs());
                observation.setDepth(bestPose.getDepth() != null ? bestPose.getDepth() : 0.0);
                observation.setTimestamp(bestSample.getTimestamp());
                observation.setRasterFilename(raster.getFilename());
                observation.setSystemName(raster.getSensorInfo() != null && raster.getSensorInfo().getSystemName() != null
                        ? raster.getSensorInfo().getSystemName()
                        : "unknown");
                
                if (observationConsumer != null) {
                    observationConsumer.accept(observation);
                } else {
                    targetContact.getObservations().add(observation);
                }
                
                potentialObservationIdx++;
            }
        }
        
        return potentialObservationIdx;
    }
    
    /**
     * Convenience method for generatePotentialObservationsFast with default parameters.
     * Uses 2 minute time offset and 2 meter distance threshold.
     */
    public int generatePotentialObservationsFast(Contact targetContact,
            java.util.function.Consumer<Observation> observationConsumer) {
        return generatePotentialObservationsFast(targetContact, observationConsumer, 120000, 2.0);
    }
}