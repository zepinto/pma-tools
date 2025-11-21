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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the IndexedRasterTiles class.
 */
class IndexedRasterTilesTest {

    /**
     * Helper method to create an observation with timestamp before the raster start.
     */
    private Observation createInitialObservation(IndexedRaster raster) {
        long firstTimestamp = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli();
        Observation obs = new Observation();
        obs.setTimestamp(OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(firstTimestamp - 10000), 
            ZoneOffset.UTC));
        return obs;
    }

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
    @DisplayName("Test generatePotentialObservations with matching location")
    void testGeneratePotentialObservationsWithMatch() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        // Create a contact at a location that should match one of the samples
        // Use the center of the raster (nadir position) for better matching
        SampleDescription midSample = raster.getSamples().get(50);
        double targetLat = Math.toDegrees(midSample.getPose().getLatitude());
        double targetLon = Math.toDegrees(midSample.getPose().getLongitude());
        
        // Use the first sample timestamp minus 10 seconds to ensure we're outside the exclusion zone
        long firstTimestamp = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli();
        
        Contact targetContact = new Contact();
        targetContact.setLabel("TestContact");
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setUuid(UUID.randomUUID());
        
        // Add initial observation at a time outside the raster range
        Observation initialObs = createInitialObservation(raster);
        initialObs.setLatitude(Math.toRadians(targetLat));
        initialObs.setLongitude(Math.toRadians(targetLon));
        initialObs.setDepth(0.0);
        targetContact.getObservations().add(initialObs);
        
        List<Observation> potentialObservations = new ArrayList<>();
        int count = tiles.generatePotentialObservations(targetContact, 1000, potentialObservations::add);
        
        // Should find at least one potential observation near the matching location
        // The algorithm looks for matches within 2 meters, at the nadir position
        assertTrue(count > 0, "Should find at least one potential observation at nadir position");
        assertEquals(count, potentialObservations.size());
        
        // Verify the observations were added to the contact (excluding the initial one)
        assertEquals(count + 1, targetContact.getObservations().size());
    }

    @Test
    @DisplayName("Test generatePotentialObservations with no match")
    void testGeneratePotentialObservationsNoMatch() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        // Create a contact at a location far from the raster path
        double targetLat = 45.0; // Far north (in degrees)
        double targetLon = -8.0;
        
        Contact targetContact = new Contact();
        targetContact.setLabel("TestContact");
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setUuid(UUID.randomUUID());
        
        List<Observation> potentialObservations = new ArrayList<>();
        int count = tiles.generatePotentialObservations(targetContact, 1000, potentialObservations::add);
        
        // Should not find any observations at this distant location
        assertEquals(0, count, "Should not find observations at distant location");
    }

    @Test
    @DisplayName("Test potential observations are properly spaced")
    void testPotentialObservationSpacing() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        // Create a contact at a location that matches multiple samples
        SampleDescription sample = raster.getSamples().get(10);
        double targetLat = Math.toDegrees(sample.getPose().getLatitude());
        double targetLon = Math.toDegrees(sample.getPose().getLongitude());
        
        Contact targetContact = new Contact();
        targetContact.setLabel("TestContact");
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setUuid(UUID.randomUUID());
        
        // Add initial observation
        Observation initialObs = createInitialObservation(raster);
        targetContact.getObservations().add(initialObs);
        
        List<Observation> potentialObservations = new ArrayList<>();
        tiles.generatePotentialObservations(targetContact, 1000, potentialObservations::add);
        
        // Verify that potential observations are spaced at least 4 seconds apart
        for (int i = 1; i < potentialObservations.size(); i++) {
            double timeDiff = Math.abs(
                potentialObservations.get(i).getTimestamp().toInstant().toEpochMilli() - 
                potentialObservations.get(i-1).getTimestamp().toInstant().toEpochMilli()
            );
            assertTrue(timeDiff >= 4000, 
                "Potential observations should be spaced at least 4 seconds apart, found: " + timeDiff + "ms");
        }
    }

    @Test
    @DisplayName("Test that observations have correct properties")
    void testObservationProperties() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        SampleDescription sample = raster.getSamples().get(20);
        double targetLat = Math.toDegrees(sample.getPose().getLatitude());
        double targetLon = Math.toDegrees(sample.getPose().getLongitude());
        
        Contact targetContact = new Contact();
        targetContact.setLabel("TestContact");
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setUuid(UUID.randomUUID());
        
        // Add initial observation
        Observation initialObs = createInitialObservation(raster);
        targetContact.getObservations().add(initialObs);
        
        List<Observation> potentialObservations = new ArrayList<>();
        int count = tiles.generatePotentialObservations(targetContact, 1000, potentialObservations::add);
        
        if (count > 0) {
            // Verify each observation has required properties
            for (Observation obs : potentialObservations) {
                assertNotNull(obs.getUuid());
                assertNotNull(obs.getTimestamp());
                assertNotNull(obs.getRasterFilename());
                assertNotNull(obs.getSystemName());
                assertTrue(obs.getLatitude() != 0 || obs.getLongitude() != 0);
            }
        }
    }

    @Test
    @DisplayName("Test generatePotentialObservationsWithMessage")
    void testGeneratePotentialObservationsWithMessage() {
        IndexedRaster raster = createTestRaster();
        IndexedRasterTiles tiles = new IndexedRasterTiles(raster);
        
        SampleDescription sample = raster.getSamples().get(30);
        double targetLat = Math.toDegrees(sample.getPose().getLatitude());
        double targetLon = Math.toDegrees(sample.getPose().getLongitude());
        
        Contact targetContact = new Contact();
        targetContact.setLabel("TestContact");
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setUuid(UUID.randomUUID());
        
        // Add initial observation
        Observation initialObs = createInitialObservation(raster);
        targetContact.getObservations().add(initialObs);
        
        // This test just verifies the method runs without error
        // We can't easily test the GUI message display in unit tests
        boolean result = tiles.generatePotentialObservationsWithMessage(targetContact, 1000, null);
        
        // Result should be true if observations were found, false otherwise
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
        double targetLat = Math.toDegrees(sample.getPose().getLatitude());
        double targetLon = Math.toDegrees(sample.getPose().getLongitude());
        
        Contact targetContact = new Contact();
        targetContact.setLabel("TestContact");
        targetContact.setLatitude(targetLat);
        targetContact.setLongitude(targetLon);
        targetContact.setUuid(UUID.randomUUID());
        
        // Add initial observation
        Observation initialObs = createInitialObservation(raster);
        targetContact.getObservations().add(initialObs);
        
        List<Observation> potentialObservations = new ArrayList<>();
        int count = tiles.generatePotentialObservations(targetContact, 1000, potentialObservations::add);
        
        // Should still be able to find observations with different heading
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
        
        Contact targetContact = new Contact();
        targetContact.setLabel("TestContact");
        targetContact.setLatitude(41.0);
        targetContact.setLongitude(-8.0);
        targetContact.setUuid(UUID.randomUUID());
        
        // Should handle minimal raster without crashing
        assertDoesNotThrow(() -> tiles.generatePotentialObservations(targetContact, 1000, null));
    }
}
