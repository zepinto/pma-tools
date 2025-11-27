//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IndexedRasterAnonymizer.
 */
class IndexedRasterAnonymizerTest {

    private Path tempDir;
    private File rasterIndexFolder;

    @BeforeEach
    void setUp() throws IOException {
        // Create temporary directory structure
        tempDir = Files.createTempDirectory("raster-test");
        rasterIndexFolder = new File(tempDir.toFile(), "rasterIndex");
        assertTrue(rasterIndexFolder.mkdirs());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temporary files
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    @DisplayName("Test anonymization creates anonymized folder")
    void testAnonymizationCreatesFolder() throws IOException {
        // Create a sample raster file
        createSampleRasterFile("test_raster.json", 40.0, -8.0);

        // Run anonymization
        IndexedRasterAnonymizer.anonymizeFolder(rasterIndexFolder);

        // Check that anonymized folder was created
        File anonymizedFolder = new File(tempDir.toFile(), "anonymized");
        assertTrue(anonymizedFolder.exists());
        assertTrue(anonymizedFolder.isDirectory());
    }

    @Test
    @DisplayName("Test anonymization copies JSON files")
    void testAnonymizationCopiesJsonFiles() throws IOException {
        // Create sample raster files
        createSampleRasterFile("test_raster_1.json", 40.0, -8.0);
        createSampleRasterFile("test_raster_2.json", 41.0, -9.0);

        // Run anonymization
        IndexedRasterAnonymizer.anonymizeFolder(rasterIndexFolder);

        // Check that JSON files were copied
        File anonymizedFolder = new File(tempDir.toFile(), "anonymized");
        assertTrue(new File(anonymizedFolder, "test_raster_1.json").exists());
        assertTrue(new File(anonymizedFolder, "test_raster_2.json").exists());
    }

    @Test
    @DisplayName("Test anonymization translates coordinates correctly")
    void testAnonymizationTranslatesCoordinates() throws IOException {
        // Create a sample raster file with known coordinates
        double originalLat = 40.0;
        double originalLon = -8.0;
        createSampleRasterFile("test_raster.json", originalLat, originalLon);

        // Run anonymization
        IndexedRasterAnonymizer.anonymizeFolder(rasterIndexFolder);

        // Read the anonymized file
        File anonymizedFolder = new File(tempDir.toFile(), "anonymized");
        File anonymizedFile = new File(anonymizedFolder, "test_raster.json");
        String anonymizedJson = Files.readString(anonymizedFile.toPath());
        IndexedRaster anonymizedRaster = Converter.IndexedRasterFromJsonString(anonymizedJson);

        // Check that the first position is at the anonymized start location
        SampleDescription firstSample = anonymizedRaster.getSamples().get(0);
        assertEquals(45.0, firstSample.getPose().getLatitude(), 0.0001);
        assertEquals(-15.0, firstSample.getPose().getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("Test anonymization preserves offsets between samples")
    void testAnonymizationPreservesOffsets() throws IOException {
        // Create a raster file with multiple samples at different positions
        File jsonFile = new File(rasterIndexFolder, "test_multi.json");
        IndexedRaster raster = createRasterWithMultipleSamples();
        String jsonContent = Converter.IndexedRasterToJsonString(raster);
        Files.writeString(jsonFile.toPath(), jsonContent);

        // Calculate original offset between first and second sample
        Pose firstPose = raster.getSamples().get(0).getPose();
        Pose secondPose = raster.getSamples().get(1).getPose();
        pt.lsts.neptus.core.LocationType loc1 = new pt.lsts.neptus.core.LocationType(
                firstPose.getLatitude(), firstPose.getLongitude());
        pt.lsts.neptus.core.LocationType loc2 = new pt.lsts.neptus.core.LocationType(
                secondPose.getLatitude(), secondPose.getLongitude());
        double[] originalOffset = loc2.getOffsetFrom(loc1);

        // Run anonymization
        IndexedRasterAnonymizer.anonymizeFolder(rasterIndexFolder);

        // Read anonymized data
        File anonymizedFolder = new File(tempDir.toFile(), "anonymized");
        File anonymizedFile = new File(anonymizedFolder, "test_multi.json");
        String anonymizedJson = Files.readString(anonymizedFile.toPath());
        IndexedRaster anonymizedRaster = Converter.IndexedRasterFromJsonString(anonymizedJson);

        // Calculate anonymized offset
        Pose anonymizedFirstPose = anonymizedRaster.getSamples().get(0).getPose();
        Pose anonymizedSecondPose = anonymizedRaster.getSamples().get(1).getPose();
        pt.lsts.neptus.core.LocationType anonLoc1 = new pt.lsts.neptus.core.LocationType(
                anonymizedFirstPose.getLatitude(), anonymizedFirstPose.getLongitude());
        pt.lsts.neptus.core.LocationType anonLoc2 = new pt.lsts.neptus.core.LocationType(
                anonymizedSecondPose.getLatitude(), anonymizedSecondPose.getLongitude());
        double[] anonymizedOffset = anonLoc2.getOffsetFrom(anonLoc1);

        // Offsets should be preserved
        assertEquals(originalOffset[0], anonymizedOffset[0], 0.1); // North offset
        assertEquals(originalOffset[1], anonymizedOffset[1], 0.1); // East offset
    }

    @Test
    @DisplayName("Test anonymization preserves velocities and angles")
    void testAnonymizationPreservesVelocitiesAndAngles() throws IOException {
        // Create a raster with velocity and angle data
        File jsonFile = new File(rasterIndexFolder, "test_vel_angle.json");
        IndexedRaster raster = createRasterWithVelocityAndAngles();
        String jsonContent = Converter.IndexedRasterToJsonString(raster);
        Files.writeString(jsonFile.toPath(), jsonContent);

        // Store original values
        Pose originalPose = raster.getSamples().get(0).getPose();
        Double originalU = originalPose.getU();
        Double originalV = originalPose.getV();
        Double originalPsi = originalPose.getPsi();

        // Run anonymization
        IndexedRasterAnonymizer.anonymizeFolder(rasterIndexFolder);

        // Read anonymized data
        File anonymizedFolder = new File(tempDir.toFile(), "anonymized");
        File anonymizedFile = new File(anonymizedFolder, "test_vel_angle.json");
        String anonymizedJson = Files.readString(anonymizedFile.toPath());
        IndexedRaster anonymizedRaster = Converter.IndexedRasterFromJsonString(anonymizedJson);

        // Check that velocities and angles are preserved
        Pose anonymizedPose = anonymizedRaster.getSamples().get(0).getPose();
        assertEquals(originalU, anonymizedPose.getU());
        assertEquals(originalV, anonymizedPose.getV());
        assertEquals(originalPsi, anonymizedPose.getPsi());
    }

    @Test
    @DisplayName("Test anonymization copies image files")
    void testAnonymizationCopiesImageFiles() throws IOException {
        // Create a sample image file
        String imageFilename = "test_image.png";
        File imageFile = new File(rasterIndexFolder, imageFilename);
        Files.writeString(imageFile.toPath(), "fake image content");

        // Create a raster file referencing the image
        createSampleRasterFileWithImage("test_raster.json", 40.0, -8.0, imageFilename);

        // Run anonymization
        IndexedRasterAnonymizer.anonymizeFolder(rasterIndexFolder);

        // Check that image file was copied
        File anonymizedFolder = new File(tempDir.toFile(), "anonymized");
        File copiedImageFile = new File(anonymizedFolder, imageFilename);
        assertTrue(copiedImageFile.exists());
        assertEquals("fake image content", Files.readString(copiedImageFile.toPath()));
    }

    @Test
    @DisplayName("Test invalid folder throws exception")
    void testInvalidFolderThrowsException() {
        File nonExistentFolder = new File("/path/that/does/not/exist");
        assertThrows(IllegalArgumentException.class, () -> 
            IndexedRasterAnonymizer.anonymizeFolder(nonExistentFolder));
    }

    // Helper methods to create test data

    private void createSampleRasterFile(String filename, double lat, double lon) throws IOException {
        File jsonFile = new File(rasterIndexFolder, filename);
        IndexedRaster raster = createBasicRaster(lat, lon);
        String jsonContent = Converter.IndexedRasterToJsonString(raster);
        Files.writeString(jsonFile.toPath(), jsonContent);
    }

    private void createSampleRasterFileWithImage(String filename, double lat, double lon, String imageFilename) 
            throws IOException {
        File jsonFile = new File(rasterIndexFolder, filename);
        IndexedRaster raster = createBasicRaster(lat, lon);
        raster.setFilename(imageFilename);
        String jsonContent = Converter.IndexedRasterToJsonString(raster);
        Files.writeString(jsonFile.toPath(), jsonContent);
    }

    private IndexedRaster createBasicRaster(double lat, double lon) {
        IndexedRaster raster = new IndexedRaster();
        raster.setFilename("test_image.png");
        raster.setRasterType(RasterType.SCANLINE);

        SensorInfo sensorInfo = new SensorInfo();
        sensorInfo.setFrequency(900.0);
        sensorInfo.setMinRange(-50.0);
        sensorInfo.setMaxRange(50.0);
        raster.setSensorInfo(sensorInfo);

        List<SampleDescription> samples = new ArrayList<>();
        SampleDescription sample = new SampleDescription();
        sample.setIndex(0L);
        sample.setOffset(0L);
        sample.setTimestamp(OffsetDateTime.now());

        Pose pose = new Pose();
        pose.setLatitude(lat);
        pose.setLongitude(lon);
        pose.setPsi(0.0);
        sample.setPose(pose);

        samples.add(sample);
        raster.setSamples(samples);

        return raster;
    }

    private IndexedRaster createRasterWithMultipleSamples() {
        IndexedRaster raster = new IndexedRaster();
        raster.setFilename("test_image.png");
        raster.setRasterType(RasterType.SCANLINE);

        SensorInfo sensorInfo = new SensorInfo();
        sensorInfo.setFrequency(900.0);
        sensorInfo.setMinRange(-50.0);
        sensorInfo.setMaxRange(50.0);
        raster.setSensorInfo(sensorInfo);

        List<SampleDescription> samples = new ArrayList<>();
        
        // First sample at (40.0, -8.0)
        SampleDescription sample1 = new SampleDescription();
        sample1.setIndex(0L);
        sample1.setOffset(0L);
        sample1.setTimestamp(OffsetDateTime.now());
        Pose pose1 = new Pose();
        pose1.setLatitude(40.0);
        pose1.setLongitude(-8.0);
        pose1.setPsi(0.0);
        sample1.setPose(pose1);
        samples.add(sample1);

        // Second sample at (40.01, -8.01) - approximately 1.5km away
        SampleDescription sample2 = new SampleDescription();
        sample2.setIndex(1L);
        sample2.setOffset(100L);
        sample2.setTimestamp(OffsetDateTime.now());
        Pose pose2 = new Pose();
        pose2.setLatitude(40.01);
        pose2.setLongitude(-8.01);
        pose2.setPsi(45.0);
        sample2.setPose(pose2);
        samples.add(sample2);

        raster.setSamples(samples);
        return raster;
    }

    private IndexedRaster createRasterWithVelocityAndAngles() {
        IndexedRaster raster = createBasicRaster(40.0, -8.0);
        Pose pose = raster.getSamples().get(0).getPose();
        
        // Set velocity components
        pose.setU(1.5); // North velocity
        pose.setV(0.5); // East velocity
        pose.setW(0.1); // Down velocity
        
        // Set angles
        pose.setPsi(45.0);  // Yaw
        pose.setTheta(5.0); // Pitch
        pose.setPhi(2.0);   // Roll
        
        return raster;
    }

    // ========== Contact Anonymization Tests ==========

    @Test
    @DisplayName("Test contact anonymization creates anonymized-contacts folder")
    void testContactAnonymizationCreatesFolder() throws IOException {
        // Create contacts folder and a sample .zct file
        File contactsFolder = new File(tempDir.toFile(), "contacts");
        assertTrue(contactsFolder.mkdirs());

        // Create a mock .zct file with a contact
        File zctFile = new File(contactsFolder, "test_contact.zct");
        createMockZctFile(zctFile, 40.0, -8.0);

        // Create reference location
        pt.lsts.neptus.core.LocationType refLocation = new pt.lsts.neptus.core.LocationType(40.0, -8.0);

        // Run contact anonymization
        IndexedRasterAnonymizer.anonymizeContactsFolder(contactsFolder, refLocation);

        // Check that anonymized-contacts folder was created
        File anonymizedContactsFolder = new File(tempDir.toFile(), "anonymized-contacts");
        assertTrue(anonymizedContactsFolder.exists());
        assertTrue(anonymizedContactsFolder.isDirectory());
    }

    @Test
    @DisplayName("Test contact anonymization copies .zct files")
    void testContactAnonymizationCopiesFiles() throws IOException {
        // Create contacts folder and sample .zct files
        File contactsFolder = new File(tempDir.toFile(), "contacts");
        assertTrue(contactsFolder.mkdirs());

        createMockZctFile(new File(contactsFolder, "contact1.zct"), 40.0, -8.0);
        createMockZctFile(new File(contactsFolder, "contact2.zct"), 40.01, -8.01);

        // Create reference location
        pt.lsts.neptus.core.LocationType refLocation = new pt.lsts.neptus.core.LocationType(40.0, -8.0);

        // Run contact anonymization
        IndexedRasterAnonymizer.anonymizeContactsFolder(contactsFolder, refLocation);

        // Check that .zct files were copied
        File anonymizedContactsFolder = new File(tempDir.toFile(), "anonymized-contacts");
        assertTrue(new File(anonymizedContactsFolder, "contact1.zct").exists());
        assertTrue(new File(anonymizedContactsFolder, "contact2.zct").exists());
    }

    @Test
    @DisplayName("Test contact coordinates are anonymized")
    void testContactCoordinatesAnonymized() throws IOException {
        // Create contacts folder and a sample .zct file
        File contactsFolder = new File(tempDir.toFile(), "contacts");
        assertTrue(contactsFolder.mkdirs());

        double originalLat = 40.0;
        double originalLon = -8.0;
        File zctFile = new File(contactsFolder, "test_contact.zct");
        createMockZctFile(zctFile, originalLat, originalLon);

        // Create reference location (same as the contact)
        pt.lsts.neptus.core.LocationType refLocation = new pt.lsts.neptus.core.LocationType(originalLat, originalLon);

        // Run contact anonymization
        IndexedRasterAnonymizer.anonymizeContactsFolder(contactsFolder, refLocation);

        // Read the anonymized contact
        File anonymizedZctFile = new File(tempDir.toFile(), "anonymized-contacts/test_contact.zct");
        assertTrue(anonymizedZctFile.exists());

        // Extract and verify the contact
        Contact anonymizedContact = pt.omst.rasterlib.contacts.CompressedContact.extractCompressedContact(anonymizedZctFile);
        assertNotNull(anonymizedContact);

        // Since the contact was at the same location as reference, it should be at the anonymized start
        assertEquals(45.0, anonymizedContact.getLatitude(), 0.0001);
        assertEquals(-15.0, anonymizedContact.getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("Test contact relative positions preserved")
    void testContactRelativePositionsPreserved() throws IOException {
        // Create contacts folder
        File contactsFolder = new File(tempDir.toFile(), "contacts");
        assertTrue(contactsFolder.mkdirs());

        // Create two contacts at different locations
        double lat1 = 40.0, lon1 = -8.0;
        double lat2 = 40.01, lon2 = -8.01;
        
        createMockZctFile(new File(contactsFolder, "contact1.zct"), lat1, lon1);
        createMockZctFile(new File(contactsFolder, "contact2.zct"), lat2, lon2);

        // Calculate original distance
        pt.lsts.neptus.core.LocationType loc1 = new pt.lsts.neptus.core.LocationType(lat1, lon1);
        pt.lsts.neptus.core.LocationType loc2 = new pt.lsts.neptus.core.LocationType(lat2, lon2);
        double originalDistance = loc1.getHorizontalDistanceInMeters(loc2);

        // Run contact anonymization with first location as reference
        IndexedRasterAnonymizer.anonymizeContactsFolder(contactsFolder, loc1);

        // Read anonymized contacts
        File anonymizedFolder = new File(tempDir.toFile(), "anonymized-contacts");
        Contact contact1 = pt.omst.rasterlib.contacts.CompressedContact.extractCompressedContact(
            new File(anonymizedFolder, "contact1.zct"));
        Contact contact2 = pt.omst.rasterlib.contacts.CompressedContact.extractCompressedContact(
            new File(anonymizedFolder, "contact2.zct"));

        // Calculate anonymized distance
        pt.lsts.neptus.core.LocationType anonLoc1 = new pt.lsts.neptus.core.LocationType(
            contact1.getLatitude(), contact1.getLongitude());
        pt.lsts.neptus.core.LocationType anonLoc2 = new pt.lsts.neptus.core.LocationType(
            contact2.getLatitude(), contact2.getLongitude());
        double anonymizedDistance = anonLoc1.getHorizontalDistanceInMeters(anonLoc2);

        // Distances should be preserved (within 10m due to coordinate system differences)
        assertEquals(originalDistance, anonymizedDistance, 10.0);
    }

    @Test
    @DisplayName("Test missing contacts folder handled gracefully")
    void testMissingContactsFolderHandled() throws IOException {
        File nonExistentFolder = new File(tempDir.toFile(), "nonexistent");
        pt.lsts.neptus.core.LocationType refLocation = new pt.lsts.neptus.core.LocationType(40.0, -8.0);

        // Should not throw exception
        assertDoesNotThrow(() -> 
            IndexedRasterAnonymizer.anonymizeContactsFolder(nonExistentFolder, refLocation));
    }

    // Helper method to create a mock .zct file
    private void createMockZctFile(File zctFile, double lat, double lon) throws IOException {
        // Create a contact
        Contact contact = new Contact();
        contact.setLabel("Test Contact");
        contact.setLatitude(lat);
        contact.setLongitude(lon);
        contact.setDepth(10.0);

        // Create a temporary directory for the contact
        Path tempContactDir = Files.createTempDirectory("zct-temp");
        File contactJsonFile = new File(tempContactDir.toFile(), "contact.json");
        
        // Write contact.json
        String contactJson = Converter.ContactToJsonString(contact);
        Files.writeString(contactJsonFile.toPath(), contactJson);

        // Create the .zct file (zip it) - arguments are (zipFile, sourceDir)
        pt.lsts.neptus.util.ZipUtils.zipDir(zctFile.getAbsolutePath(), tempContactDir.toString());

        // Clean up temp directory
        Files.walk(tempContactDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
