//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.utils;

import org.junit.jupiter.api.Test;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Observation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class IndexedRasterTilesTest {

    @Test
    void testPotentialContactsForQuarteiraData() throws Exception {
        // Test location from user's request
        double targetLat = 37.00635308997331;
        double targetLon = -8.099553373095123;
        double targetDepth = 25.739999771118164;
        Instant targetTime = Instant.parse("2025-10-01T11:10:34.680Z");
        
        File rasterIndexDir = new File("/LOGS/QUARTEIRA/20251001/100930_A-08/rasterIndex");
        
        if (!rasterIndexDir.exists()) {
            System.out.println("Data directory not found: " + rasterIndexDir);
            System.out.println("Skipping test - requires actual mission data");
            return;
        }
        
        System.out.println("Loading raster data from: " + rasterIndexDir);
        
        // Load all raster JSON files
        File[] jsonFiles = rasterIndexDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(jsonFiles, "No JSON files found in raster index directory");
        
        System.out.println("Found " + jsonFiles.length + " raster files");
        
        // Create contact at target location
        Contact targetContact = new Contact();
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setDepth(targetDepth);
        
        // Add original observation
        Observation originalObs = new Observation();
        originalObs.setTimestamp(OffsetDateTime.ofInstant(targetTime, ZoneOffset.UTC));
        targetContact.getObservations().add(originalObs);
        
        System.out.println("\nTarget contact: lat=" + targetLat + ", lon=" + targetLon + 
                          ", depth=" + targetDepth + "m, time=" + targetTime);
        
        // Track all observations with details
        Map<String, List<Observation>> observationsByRaster = new HashMap<>();
        List<Observation> allObservations = new ArrayList<>();
        
        // Process each raster file
        for (File jsonFile : jsonFiles) {
            String json = Files.readString(jsonFile.toPath());
            IndexedRaster raster = Converter.IndexedRasterFromJsonString(json);
            
            System.out.println("\n=== Processing raster: " + raster.getFilename() + " ===");
            System.out.println("Samples: " + raster.getSamples().size());
            
            // Get image width from the image file
            File imageFile = new File(jsonFile.getParent(), raster.getFilename());
            int imageWidth = 800; // Default
            if (imageFile.exists()) {
                ImageIO.setUseCache(false);
                BufferedImage img = ImageIO.read(imageFile);
                if (img != null) {
                    imageWidth = img.getWidth();
                    System.out.println("Image width: " + imageWidth);
                }
            }
            
            List<Observation> rasterObservations = new ArrayList<>();
            
            IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
            int count = tiles.generatePotentialObservations(targetContact, imageWidth, obs -> {
                System.out.println("  -> Observation found: time=" + obs.getTimestamp() + 
                                 ", depth=" + obs.getDepth() + "m, raster=" + obs.getRasterFilename());
                rasterObservations.add(obs);
                allObservations.add(obs);
            });
            
            System.out.println("Generated " + count + " observations from this raster");
            observationsByRaster.put(raster.getFilename(), rasterObservations);
        }
        
        // Analyze results
        System.out.println("\n=== ANALYSIS ===");
        System.out.println("Total observations collected: " + allObservations.size());
        
        // Group by timestamp to find duplicates
        Map<OffsetDateTime, List<Observation>> byTimestamp = allObservations.stream()
            .collect(Collectors.groupingBy(Observation::getTimestamp));
        
        System.out.println("Unique timestamps: " + byTimestamp.size());
        
        // Find duplicates
        List<OffsetDateTime> duplicates = byTimestamp.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (!duplicates.isEmpty()) {
            System.out.println("\n=== DUPLICATES FOUND ===");
            System.out.println("Number of duplicate timestamps: " + duplicates.size());
            
            for (OffsetDateTime timestamp : duplicates) {
                List<Observation> dups = byTimestamp.get(timestamp);
                System.out.println("\nTimestamp: " + timestamp + " (appears " + dups.size() + " times)");
                for (Observation obs : dups) {
                    System.out.println("  - UUID: " + obs.getUuid() + 
                                     ", raster: " + obs.getRasterFilename() +
                                     ", depth: " + obs.getDepth() + "m");
                }
            }
        } else {
            System.out.println("No duplicates found - all observations have unique timestamps");
        }
        
        // Summary by raster
        System.out.println("\n=== BY RASTER ===");
        for (Map.Entry<String, List<Observation>> entry : observationsByRaster.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().size() + " observations");
        }
    }

    @Test
    void testPotentialContactsFastMethod() throws Exception {
        // Different test location - in the northern part of the survey area
        double targetLat = 37.0110;
        double targetLon = -8.0971;
        double targetDepth = 22.5;
        Instant targetTime = Instant.parse("2025-10-01T10:20:02.069Z");
        
        File rasterIndexDir = new File("/LOGS/QUARTEIRA/20251001/100930_A-08/rasterIndex");
        
        if (!rasterIndexDir.exists()) {
            System.out.println("Data directory not found: " + rasterIndexDir);
            System.out.println("Skipping test - requires actual mission data");
            return;
        }
        
        System.out.println("=== FAST METHOD TEST ===");
        System.out.println("Loading raster data from: " + rasterIndexDir);
        
        // Load all raster JSON files
        File[] jsonFiles = rasterIndexDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(jsonFiles, "No JSON files found in raster index directory");
        
        System.out.println("Found " + jsonFiles.length + " raster files");
        
        // Create contact at target location
        Contact targetContact = new Contact();
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setDepth(targetDepth);
        
        // Add original observation
        Observation originalObs = new Observation();
        originalObs.setTimestamp(OffsetDateTime.ofInstant(targetTime, ZoneOffset.UTC));
        targetContact.getObservations().add(originalObs);
        
        System.out.println("\nTarget contact: lat=" + targetLat + ", lon=" + targetLon + 
                          ", depth=" + targetDepth + "m, time=" + targetTime);
        
        List<Observation> allObservations = new ArrayList<>();
        int rastersWithinBounds = 0;
        int rastersOutsideBounds = 0;
        
        long startTime = System.currentTimeMillis();
        
        // Process each raster file using the FAST method
        for (File jsonFile : jsonFiles) {
            String json = Files.readString(jsonFile.toPath());
            IndexedRaster raster = Converter.IndexedRasterFromJsonString(json);
            
            IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
            
            // Check bounds first
            IndexedRasterTiles.RasterBounds bounds = tiles.calculateBounds();
            if (bounds != null) {
                double range = raster.getSensorInfo().getMaxRange();
                boolean withinBounds = bounds.contains(targetLat, targetLon, range);
                
                if (withinBounds) {
                    rastersWithinBounds++;
                    System.out.println("\n=== Processing raster (within bounds): " + raster.getFilename() + " ===");
                    System.out.println("  Bounds: lat[" + bounds.minLat + ", " + bounds.maxLat + 
                                     "], lon[" + bounds.minLon + ", " + bounds.maxLon + "]");
                    System.out.println("  Time range: " + bounds.startTimeMs + " to " + bounds.endTimeMs);
                    System.out.println("  Avg speed: " + bounds.avgSpeed + " m/s");
                    
                    int count = tiles.generatePotentialObservationsFast(targetContact, obs -> {
                        System.out.println("  -> FAST: Observation found: time=" + obs.getTimestamp() + 
                                         ", depth=" + obs.getDepth() + "m");
                        allObservations.add(obs);
                    });
                    
                    System.out.println("  Generated " + count + " observations");
                } else {
                    rastersOutsideBounds++;
                }
            }
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Analyze results
        System.out.println("\n=== FAST METHOD ANALYSIS ===");
        System.out.println("Elapsed time: " + elapsedTime + "ms");
        System.out.println("Rasters within bounds: " + rastersWithinBounds);
        System.out.println("Rasters outside bounds (skipped): " + rastersOutsideBounds);
        System.out.println("Total observations collected: " + allObservations.size());
        
        // Group by timestamp to find duplicates
        Map<OffsetDateTime, List<Observation>> byTimestamp = allObservations.stream()
            .collect(Collectors.groupingBy(Observation::getTimestamp));
        
        System.out.println("Unique timestamps: " + byTimestamp.size());
        
        // Find duplicates
        List<OffsetDateTime> duplicates = byTimestamp.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (!duplicates.isEmpty()) {
            System.out.println("\n=== DUPLICATES FOUND ===");
            for (OffsetDateTime timestamp : duplicates) {
                List<Observation> dups = byTimestamp.get(timestamp);
                System.out.println("Timestamp: " + timestamp + " (appears " + dups.size() + " times)");
            }
        } else {
            System.out.println("No duplicates found!");
        }
    }

    public static void main(String[] args) {
        IndexedRasterTilesTest test = new IndexedRasterTilesTest();
        try {
            test.testPotentialContactsForQuarteiraData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
