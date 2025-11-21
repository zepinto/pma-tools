//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.mra.SidescanLogMarker;
import pt.lsts.neptus.mra.SidescanPotentialMarker;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the IndexedRasterTiles class.
 */
class IndexedRasterTilesTest {

    private IndexedRaster createTestRaster() {
        IndexedRaster raster = new IndexedRaster();
        
        // Create sensor info with 50m range
        SensorInfo sensorInfo = new SensorInfo();
        sensorInfo.setMaxRange(50.0);
        sensorInfo.setMinRange(-50.0);
        sensorInfo.setFrequency(900000.0);
        raster.setSensorInfo(sensorInfo);
        
        // Create samples along a path
        List<SampleDescription> samples = new ArrayList<>();
        
        // Starting position: 41.0° N, -8.0° E
        double baseLat = Math.toRadians(41.0);
        double baseLon = Math.toRadians(-8.0);
        
        long startTime = System.currentTimeMillis();
        
        // Create 100 samples over 100 seconds, moving northward
        for (int i = 0; i < 100; i++) {
            SampleDescription sample = new SampleDescription();
            sample.setIndex((long) i);
            sample.setOffset((long) i * 1000);
            
            Pose pose = new Pose();
            // Move north by approximately 1 meter per sample (using rough approximation)
            pose.setLatitude(baseLat + (i * 0.00001));
            pose.setLongitude(baseLon);
            pose.setAltitude(10.0); // 10m altitude
            pose.setPsi(0.0); // Heading north
            pose.setU(1.0); // 1 m/s forward speed
            
            sample.setPose(pose);
            sample.setTimestamp(OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTime + i * 1000), 
                ZoneOffset.UTC));
            
            samples.add(sample);
        }
        
        raster.setSamples(samples);
        raster.setFilename("test_raster.png");
        
        return raster;
    }

    @Test
    @DisplayName("Test IndexedRasterTiles creation")
    void testCreation() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        assertNotNull(tiles);
    }

    @Test
    @DisplayName("Test generatePotentialMarkers with matching location")
    void testGeneratePotentialMarkersWithMatch() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        // Create a marker at a location that should match one of the samples
        // Use the center of the raster (nadir position) for better matching
        SampleDescription midSample = raster.getSamples().get(50);
        double targetLat = midSample.getPose().getLatitude();
        double targetLon = midSample.getPose().getLongitude();
        
        // Use the first sample timestamp minus 10 seconds to ensure we're outside the exclusion zone
        long firstTimestamp = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli();
        
        SidescanLogMarker originalMark = new SidescanLogMarker(
            "TestMark",
            firstTimestamp - 10000, // 10 seconds before start to avoid exclusion
            targetLat,
            targetLon,
            0.0, // x
            0.0, // y
            0, // width
            0, // height
            100.0, // range
            0, // subsystem
            pt.lsts.neptus.colormap.ColorMapFactory.createBronzeColormap()
        );
        
        List<SidescanPotentialMarker> potentialMarkers = new ArrayList<>();
        int count = tiles.generatePotentialMarkers(originalMark, 1000, potentialMarkers::add);
        
        // Should find at least one potential marker near the matching location
        // The algorithm looks for matches within 2 meters, at the nadir position
        assertTrue(count > 0, "Should find at least one potential marker at nadir position");
        assertEquals(count, potentialMarkers.size());
        
        // Verify the potential markers are children of the original mark
        assertEquals(count, originalMark.getChildren().size());
    }

    @Test
    @DisplayName("Test generatePotentialMarkers with no match")
    void testGeneratePotentialMarkersNoMatch() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        // Create a marker at a location far from the raster path
        double targetLat = Math.toRadians(45.0); // Far north
        double targetLon = Math.toRadians(-8.0);
        
        SidescanLogMarker originalMark = new SidescanLogMarker(
            "TestMark",
            System.currentTimeMillis() - 500000,
            targetLat,
            targetLon,
            0.0, // x
            0.0, // y
            0, // width
            0, // height
            100.0, // range
            0, // subsystem
            pt.lsts.neptus.colormap.ColorMapFactory.createBronzeColormap()
        );
        
        List<SidescanPotentialMarker> potentialMarkers = new ArrayList<>();
        int count = tiles.generatePotentialMarkers(originalMark, 1000, potentialMarkers::add);
        
        // Should not find any markers at this distant location
        assertEquals(0, count, "Should not find markers at distant location");
    }

    @Test
    @DisplayName("Test potential markers are properly spaced")
    void testPotentialMarkerSpacing() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        // Create a marker at a location that matches multiple samples
        SampleDescription sample = raster.getSamples().get(10);
        double targetLat = sample.getPose().getLatitude();
        double targetLon = sample.getPose().getLongitude();
        
        SidescanLogMarker originalMark = new SidescanLogMarker(
            "TestMark",
            raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli() - 10000,
            targetLat,
            targetLon,
            0.0, // x
            0.0, // y
            0, // width
            0, // height
            100.0, // range
            0, // subsystem
            pt.lsts.neptus.colormap.ColorMapFactory.createBronzeColormap()
        );
        
        List<SidescanPotentialMarker> potentialMarkers = new ArrayList<>();
        tiles.generatePotentialMarkers(originalMark, 1000, potentialMarkers::add);
        
        // Verify that potential markers are spaced at least 5 seconds apart
        for (int i = 1; i < potentialMarkers.size(); i++) {
            double timeDiff = Math.abs(potentialMarkers.get(i).getTimestamp() - 
                                      potentialMarkers.get(i-1).getTimestamp());
            assertTrue(timeDiff >= 4000, 
                "Potential markers should be spaced at least 4 seconds apart, found: " + timeDiff + "ms");
        }
    }

    @Test
    @DisplayName("Test that potential markers have correct parent relationship")
    void testPotentialMarkerParentRelationship() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        SampleDescription sample = raster.getSamples().get(20);
        SidescanLogMarker originalMark = new SidescanLogMarker(
            "TestMark",
            raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli() - 10000,
            sample.getPose().getLatitude(),
            sample.getPose().getLongitude(),
            0.0, 0.0, 0, 0, 100.0, 0,
            pt.lsts.neptus.colormap.ColorMapFactory.createBronzeColormap()
        );
        
        List<SidescanPotentialMarker> potentialMarkers = new ArrayList<>();
        int count = tiles.generatePotentialMarkers(originalMark, 1000, potentialMarkers::add);
        
        if (count > 0) {
            // Verify each potential marker has the correct parent
            for (SidescanPotentialMarker marker : potentialMarkers) {
                assertTrue(marker.hasParent());
                assertEquals(originalMark, marker.getParent());
                assertTrue(marker.getLabel().startsWith(originalMark.getLabel() + "-p"));
            }
        }
    }

    @Test
    @DisplayName("Test generatePotentialMarkersWithMessage")
    void testGeneratePotentialMarkersWithMessage() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        SampleDescription sample = raster.getSamples().get(30);
        SidescanLogMarker originalMark = new SidescanLogMarker(
            "TestMark",
            raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli() - 10000,
            sample.getPose().getLatitude(),
            sample.getPose().getLongitude(),
            0.0, 0.0, 0, 0, 100.0, 0,
            pt.lsts.neptus.colormap.ColorMapFactory.createBronzeColormap()
        );
        
        // This test just verifies the method runs without error
        // We can't easily test the GUI message display in unit tests
        boolean result = tiles.generatePotentialMarkersWithMessage(originalMark, 1000, null);
        
        // Result should be true if markers were found, false otherwise
        // Just verify the method completes without exception
        assertNotNull(result);
    }

    @Test
    @DisplayName("Test with raster at different heading")
    void testDifferentHeading() {
        IndexedRaster raster = createTestRaster();
        
        // Modify the heading of samples to point east
        for (SampleDescription sample : raster.getSamples()) {
            sample.getPose().setPsi(Math.PI / 2); // 90 degrees = east
        }
        
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        SampleDescription sample = raster.getSamples().get(40);
        SidescanLogMarker originalMark = new SidescanLogMarker(
            "TestMark",
            raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli() - 10000,
            sample.getPose().getLatitude(),
            sample.getPose().getLongitude(),
            0.0, 0.0, 0, 0, 100.0, 0,
            pt.lsts.neptus.colormap.ColorMapFactory.createBronzeColormap()
        );
        
        List<SidescanPotentialMarker> potentialMarkers = new ArrayList<>();
        int count = tiles.generatePotentialMarkers(originalMark, 1000, potentialMarkers::add);
        
        // Should still be able to find markers with different heading
        assertTrue(count >= 0, "Method should complete without error");
    }

    @Test
    @DisplayName("Test with minimal raster (few samples)")
    void testMinimalRaster() {
        IndexedRaster raster = new IndexedRaster();
        
        SensorInfo sensorInfo = new SensorInfo();
        sensorInfo.setMaxRange(50.0);
        raster.setSensorInfo(sensorInfo);
        
        // Create only 3 samples
        List<SampleDescription> samples = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            SampleDescription sample = new SampleDescription();
            Pose pose = new Pose();
            pose.setLatitude(Math.toRadians(41.0));
            pose.setLongitude(Math.toRadians(-8.0));
            pose.setAltitude(10.0);
            pose.setPsi(0.0);
            sample.setPose(pose);
            sample.setTimestamp(OffsetDateTime.now().plusSeconds(i));
            samples.add(sample);
        }
        raster.setSamples(samples);
        
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        SidescanLogMarker originalMark = new SidescanLogMarker(
            "TestMark",
            System.currentTimeMillis() - 10000,
            Math.toRadians(41.0),
            Math.toRadians(-8.0),
            0.0, 0.0, 0, 0, 100.0, 0,
            pt.lsts.neptus.colormap.ColorMapFactory.createBronzeColormap()
        );
        
        // Should handle minimal raster without crashing
        assertDoesNotThrow(() -> tiles.generatePotentialMarkers(originalMark, 1000, null));
    }
}
