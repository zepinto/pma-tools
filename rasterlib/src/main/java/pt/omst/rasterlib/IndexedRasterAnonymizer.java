//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Utility class to anonymize IndexedRaster files by translating all coordinates
 * while keeping velocities, angles, and images unchanged.
 * 
 * The anonymization translates all coordinates so that the vehicle starts at
 * lat=45, lon=-15 (Atlantic Ocean) and calculates remaining coordinates based
 * on their offsets from the first position.
 */
@Slf4j
public class IndexedRasterAnonymizer {

    private static final double ANONYMIZED_START_LAT = 45.0;
    private static final double ANONYMIZED_START_LON = -15.0;

    /**
     * Anonymizes a folder containing rasterIndex data.
     * Creates an "anonymized" folder as a sibling to the input folder.
     * 
     * @param rasterIndexFolder the folder containing rasterIndex JSON and image files
     * @throws IOException if file operations fail
     */
    public static void anonymizeFolder(File rasterIndexFolder) throws IOException {
        if (!rasterIndexFolder.exists() || !rasterIndexFolder.isDirectory()) {
            throw new IllegalArgumentException("Input must be an existing directory: " + rasterIndexFolder);
        }

        if (!rasterIndexFolder.getName().equals("rasterIndex")) {
            log.warn("Folder name is not 'rasterIndex', got: {}", rasterIndexFolder.getName());
        }

        // Create anonymized folder as a sibling
        File parentFolder = rasterIndexFolder.getParentFile();
        File anonymizedFolder = new File(parentFolder, "anonymized");
        
        if (!anonymizedFolder.exists()) {
            if (!anonymizedFolder.mkdirs()) {
                throw new IOException("Failed to create anonymized folder: " + anonymizedFolder);
            }
        }

        log.info("Anonymizing raster files from {} to {}", rasterIndexFolder, anonymizedFolder);

        // Process all JSON files in the folder
        File[] files = rasterIndexFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            log.warn("No JSON files found in {}", rasterIndexFolder);
            return;
        }

        int processed = 0;
        for (File jsonFile : files) {
            try {
                anonymizeRasterFile(jsonFile, anonymizedFolder);
                processed++;
            } catch (Exception e) {
                log.error("Failed to anonymize file: " + jsonFile.getName(), e);
            }
        }

        log.info("Anonymization complete. Processed {} files.", processed);
    }

    /**
     * Anonymizes a single raster file (JSON + associated image).
     * 
     * @param jsonFile the JSON file to anonymize
     * @param outputFolder the folder where anonymized files will be saved
     * @throws IOException if file operations fail
     */
    private static void anonymizeRasterFile(File jsonFile, File outputFolder) throws IOException {
        log.debug("Processing file: {}", jsonFile.getName());

        // Read the raster
        String jsonContent = Files.readString(jsonFile.toPath());
        IndexedRaster raster = Converter.IndexedRasterFromJsonString(jsonContent);

        // Find and copy the image file
        String imageFilename = raster.getFilename();
        File imageFile = new File(jsonFile.getParentFile(), imageFilename);
        if (imageFile.exists()) {
            File outputImageFile = new File(outputFolder, imageFilename);
            Files.copy(imageFile.toPath(), outputImageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied image file: {}", imageFilename);
        } else {
            log.warn("Image file not found: {}", imageFile);
        }

        // Anonymize coordinates
        anonymizeCoordinates(raster);

        // Write anonymized JSON
        String anonymizedJson = Converter.IndexedRasterToJsonString(raster);
        File outputJsonFile = new File(outputFolder, jsonFile.getName());
        Files.writeString(outputJsonFile.toPath(), anonymizedJson);
        
        log.debug("Anonymized JSON written to: {}", outputJsonFile.getName());
    }

    /**
     * Anonymizes the coordinates in a raster by translating them.
     * The first position becomes (45, -15) and all other positions are translated
     * based on their NED offsets from the first position.
     * 
     * @param raster the raster to anonymize (modified in place)
     */
    private static void anonymizeCoordinates(IndexedRaster raster) {
        List<SampleDescription> samples = raster.getSamples();
        if (samples == null || samples.isEmpty()) {
            log.warn("No samples to anonymize");
            return;
        }

        // Get the first position as reference
        SampleDescription firstSample = samples.get(0);
        Pose firstPose = firstSample.getPose();
        LocationType firstLocation = new LocationType(firstPose.getLatitude(), firstPose.getLongitude());

        // Create the new first location at the anonymized starting point
        LocationType anonymizedStart = new LocationType(ANONYMIZED_START_LAT, ANONYMIZED_START_LON);

        // Translate all coordinates
        for (SampleDescription sample : samples) {
            Pose pose = sample.getPose();
            
            // Calculate offset from the first position
            LocationType currentLocation = new LocationType(pose.getLatitude(), pose.getLongitude());
            double[] offset = currentLocation.getOffsetFrom(firstLocation);
            
            // Create new location by applying the offset to the anonymized start
            LocationType anonymizedLocation = new LocationType(anonymizedStart);
            anonymizedLocation.translatePosition(offset[0], offset[1], offset[2]);
            anonymizedLocation.convertToAbsoluteLatLonDepth();

            // Update the pose with anonymized coordinates
            pose.setLatitude(anonymizedLocation.getLatitudeDegs());
            pose.setLongitude(anonymizedLocation.getLongitudeDegs());
        }

        log.debug("Anonymized {} samples", samples.size());
    }

    /**
     * Main method for command-line usage.
     * 
     * Usage: java IndexedRasterAnonymizer <rasterIndex-folder-path>
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java IndexedRasterAnonymizer <rasterIndex-folder-path>");
            System.exit(1);
        }

        File rasterIndexFolder = new File(args[0]);
        
        try {
            anonymizeFolder(rasterIndexFolder);
            System.out.println("Anonymization completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during anonymization: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
