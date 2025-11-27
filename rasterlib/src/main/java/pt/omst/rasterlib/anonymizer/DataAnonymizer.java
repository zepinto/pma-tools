package pt.omst.rasterlib.anonymizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.SampleDescription;
import pt.omst.rasterlib.contacts.CompressedContact;

/**
 * Utility class for anonymizing marine data by offsetting geographic coordinates,
 * timestamps, and headings to a default starting point.
 * 
 * This anonymizer processes:
 * - Raster files (IndexedRaster JSON with SampleDescription entries)
 * - Contact files (.zct compressed contacts)
 * - Observations within contacts
 * - Annotations with timestamps
 */
@Slf4j
public class DataAnonymizer {

    public static final double DEFAULT_START_LATITUDE = 40.0;
    public static final double DEFAULT_START_LONGITUDE = -10.0;
    public static final Instant DEFAULT_START_TIME = Instant.parse("2020-01-01T00:00:00Z");
    
    private double latitudeOffset;
    private double longitudeOffset;
    private long timeOffsetMillis;
    private boolean offsetsInitialized = false;

    /**
     * Creates a DataAnonymizer instance.
     */
    public DataAnonymizer() {
    }

    /**
     * Creates a DataAnonymizer with a reference point from the source folder.
     * The offsets are computed from the first raster sample found.
     * 
     * @param source the source folder containing raster data
     * @param destination the destination folder (not used in constructor)
     */
    public DataAnonymizer(File source, File destination) {
        initializeOffsetsFromRasterFolder(source);
    }

    /**
     * Initializes the offsets based on the first raster sample found in the folder.
     * 
     * @param rasterFolder the folder containing raster files
     * @return true if offsets were successfully initialized
     */
    public boolean initializeOffsetsFromRasterFolder(File rasterFolder) {
        List<File> rasterFiles = findRasterFiles(rasterFolder);
        if (rasterFiles.isEmpty()) {
            log.warn("No raster files found in {}", rasterFolder.getAbsolutePath());
            return false;
        }
        
        File firstRaster = rasterFiles.get(0);
        try {
            IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(firstRaster.toPath()));
            SampleDescription firstSample = raster.getSamples().getFirst();
            initializeOffsets(
                firstSample.getPose().getLatitude(),
                firstSample.getPose().getLongitude(),
                firstSample.getTimestamp().toInstant().toEpochMilli()
            );
            return true;
        } catch (Exception e) {
            log.error("Error reading first raster file: {}", firstRaster.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Initializes the offsets based on a contact's first observation.
     * 
     * @param contact the contact to use as reference
     * @return true if offsets were successfully initialized
     */
    public boolean initializeOffsetsFromContact(Contact contact) {
        if (contact.getLatitude() == null || contact.getLongitude() == null) {
            log.warn("Contact has no coordinates");
            return false;
        }
        
        long timestamp = DEFAULT_START_TIME.toEpochMilli();
        
        // Try to get timestamp from first observation
        if (contact.getObservations() != null && !contact.getObservations().isEmpty()) {
            Observation firstObs = contact.getObservations().getFirst();
            if (firstObs.getTimestamp() != null) {
                timestamp = firstObs.getTimestamp().toInstant().toEpochMilli();
            }
        }
        
        initializeOffsets(contact.getLatitude(), contact.getLongitude(), timestamp);
        return true;
    }

    /**
     * Manually initialize offsets from a reference point.
     * 
     * @param refLatitude reference latitude
     * @param refLongitude reference longitude
     * @param refTimestampMillis reference timestamp in milliseconds
     */
    public void initializeOffsets(double refLatitude, double refLongitude, long refTimestampMillis) {
        this.latitudeOffset = DEFAULT_START_LATITUDE - refLatitude;
        this.longitudeOffset = DEFAULT_START_LONGITUDE - refLongitude;
        this.timeOffsetMillis = DEFAULT_START_TIME.toEpochMilli() - refTimestampMillis;
        this.offsetsInitialized = true;
        
        log.info("Initialized anonymization offsets: lat={}, lon={}, time={}ms",
                latitudeOffset, longitudeOffset, timeOffsetMillis);
    }

    /**
     * Finds all raster JSON files in the given folder and subfolders.
     * 
     * @param parentFolder the folder to search
     * @return list of raster JSON files
     */
    public static List<File> findRasterFiles(File parentFolder) {
        List<File> files = new ArrayList<>();
        File[] children = parentFolder.listFiles();
        if (children == null) {
            return files;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                files.addAll(findRasterFiles(file));
            } else if (file.getName().endsWith(".json")
                    && file.getParentFile().getName().equalsIgnoreCase("rasterIndex")) {
                files.add(file);                
            }
        }
        return files;
    }

    /**
     * Finds all contact (.zct) files in the given folder and subfolders.
     * 
     * @param parentFolder the folder to search
     * @return list of .zct contact files
     */
    public static List<File> findContactFiles(File parentFolder) {
        List<File> files = new ArrayList<>();
        File[] children = parentFolder.listFiles();
        if (children == null) {
            return files;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                files.addAll(findContactFiles(file));
            } else if (file.getName().endsWith(".zct")) {
                files.add(file);                
            }
        }
        return files;
    }

    /**
     * Anonymizes a raster folder and its associated contacts folder.
     * 
     * @param rasterFolder the source raster folder
     * @param destinationFolder the destination folder
     */
    public void anonymizeRasterFolder(File rasterFolder, File destinationFolder) {
        File contactsFolder = new File(rasterFolder.getParentFile(), "contacts");
        destinationFolder.mkdirs();
        List<File> rasterFiles = findRasterFiles(rasterFolder);
        
        if (rasterFiles.isEmpty()) {
            log.error("No raster files found in {}", rasterFolder.getAbsolutePath());
            return;
        }

        // Initialize offsets from first raster if not already initialized
        if (!offsetsInitialized) {
            if (!initializeOffsetsFromRasterFolder(rasterFolder)) {
                return;
            }
        }
        
        // Process raster files
        for (File rasterFile : rasterFiles) {
            try {
                IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(rasterFile.toPath()));
                anonymizeRaster(raster);

                // Copy associated image file
                File image = new File(rasterFile.getParent(), raster.getFilename());
                if (image.exists()) {
                    Files.copy(image.toPath(), new File(destinationFolder, image.getName()).toPath());
                }
                
                // Save anonymized raster JSON
                File destRasterFile = new File(destinationFolder, rasterFile.getName());
                Files.writeString(destRasterFile.toPath(), Converter.IndexedRasterToJsonString(raster));
                log.info("Anonymized raster: {}", rasterFile.getName());
            } catch (Exception e) {
                log.error("Error processing raster file: {}", rasterFile.getAbsolutePath(), e);
            }           
        }

        // Process contacts folder - store as sibling to destinationFolder (rasterIndex)
        if (contactsFolder.exists() && contactsFolder.isDirectory()) {
            File destContactsFolder = new File(destinationFolder.getParentFile(), "contacts");
            destContactsFolder.mkdirs();
            anonymizeContactsFolder(contactsFolder, destContactsFolder);
        }
    }

    /**
     * Anonymizes all contacts in a folder.
     * 
     * @param contactsFolder the source contacts folder
     * @param destinationFolder the destination folder
     */
    public void anonymizeContactsFolder(File contactsFolder, File destinationFolder) {
        destinationFolder.mkdirs();
        List<File> contactFiles = findContactFiles(contactsFolder);
        
        for (File contactFile : contactFiles) {
            try {
                anonymizeContactFile(contactFile, destinationFolder);
                log.info("Anonymized contact: {}", contactFile.getName());
            } catch (Exception e) {
                log.error("Error anonymizing contact file: {}", contactFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * Anonymizes a single contact file (.zct).
     * Offsets must be initialized before calling this method.
     * 
     * @param contactFile the source .zct file
     * @param destinationFolder the destination folder
     * @throws IOException if an error occurs
     * @throws IllegalStateException if offsets have not been initialized
     */
    public void anonymizeContactFile(File contactFile, File destinationFolder) throws IOException {
        if (!offsetsInitialized) {
            throw new IllegalStateException("Offsets must be initialized before anonymizing contacts. " +
                    "Call initializeOffsets() or process rasters first.");
        }

        // Extract contact from compressed file
        Contact contact = CompressedContact.extractCompressedContact(contactFile);
        if (contact == null) {
            log.warn("Could not extract contact from {}", contactFile.getAbsolutePath());
            return;
        }

        // Anonymize the contact
        anonymizeContact(contact);

        // Create temp directory for the new zct contents
        Path tempDir = Files.createTempDirectory("zct_anon");
        try {
            // Unzip original file to temp directory
            ZipUtils.unzip(contactFile.getAbsolutePath(), tempDir);

            // Write anonymized contact.json
            String anonymizedJson = Converter.ContactToJsonString(contact);
            Files.writeString(tempDir.resolve("contact.json"), anonymizedJson);

            // Anonymize any raster files inside the contact
            anonymizeRasterFilesInDirectory(tempDir.toFile());

            // Create new zct file
            File destFile = new File(destinationFolder, contactFile.getName());
            ZipUtils.zipDir(destFile.getAbsolutePath(), tempDir.toString());
        } finally {
            // Clean up temp directory
            deleteDirectory(tempDir.toFile());
        }
    }

    /**
     * Anonymizes raster JSON files found in a directory.
     * 
     * @param directory the directory to search
     */
    private void anonymizeRasterFilesInDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                anonymizeRasterFilesInDirectory(file);
            } else if (file.getName().endsWith(".json") && !file.getName().equals("contact.json")) {
                try {
                    String content = Files.readString(file.toPath());
                    // Try to parse as IndexedRaster
                    IndexedRaster raster = Converter.IndexedRasterFromJsonString(content);
                    if (raster.getSamples() != null && !raster.getSamples().isEmpty()) {
                        anonymizeRaster(raster);
                        Files.writeString(file.toPath(), Converter.IndexedRasterToJsonString(raster));
                        log.debug("Anonymized embedded raster: {}", file.getName());
                    }
                } catch (Exception e) {
                    // Not a raster file, skip
                    log.trace("Skipping non-raster JSON file: {}", file.getName());
                }
            }
        }
    }

    /**
     * Anonymizes an IndexedRaster by applying offsets to all samples.
     * 
     * @param raster the raster to anonymize
     */
    public void anonymizeRaster(IndexedRaster raster) {
        if (raster.getSamples() == null) return;

        for (SampleDescription sample : raster.getSamples()) {
            if (sample.getPose() != null) {
                sample.getPose().setLatitude(sample.getPose().getLatitude() + latitudeOffset);
                sample.getPose().setLongitude(sample.getPose().getLongitude() + longitudeOffset);
            }
            if (sample.getTimestamp() != null) {
                sample.setTimestamp(applyTimeOffset(sample.getTimestamp()));
            }
        }
    }

    /**
     * Anonymizes a Contact by applying offsets to all location and time data.
     * This includes the contact itself, all observations, and all annotations.
     * 
     * @param contact the contact to anonymize
     */
    public void anonymizeContact(Contact contact) {
        // Anonymize contact-level coordinates
        if (contact.getLatitude() != null) {
            contact.setLatitude(contact.getLatitude() + latitudeOffset);
        }
        if (contact.getLongitude() != null) {
            contact.setLongitude(contact.getLongitude() + longitudeOffset);
        }

        // Anonymize observations
        if (contact.getObservations() != null) {
            for (Observation observation : contact.getObservations()) {
                anonymizeObservation(observation);
            }
        }
    }

    /**
     * Anonymizes an Observation by applying offsets to location, time, and heading data.
     * Also anonymizes all contained annotations.
     * 
     * @param observation the observation to anonymize
     */
    public void anonymizeObservation(Observation observation) {
        // Anonymize observation coordinates
        observation.setLatitude(observation.getLatitude() + latitudeOffset);
        observation.setLongitude(observation.getLongitude() + longitudeOffset);

        // Anonymize timestamp
        if (observation.getTimestamp() != null) {
            observation.setTimestamp(applyTimeOffset(observation.getTimestamp()));
        }

        // Anonymize annotations
        if (observation.getAnnotations() != null) {
            for (Annotation annotation : observation.getAnnotations()) {
                anonymizeAnnotation(annotation);
            }
        }
    }

    /**
     * Anonymizes an Annotation by applying time offset to its timestamp.
     * 
     * @param annotation the annotation to anonymize
     */
    public void anonymizeAnnotation(Annotation annotation) {
        if (annotation.getTimestamp() != null) {
            annotation.setTimestamp(applyTimeOffset(annotation.getTimestamp()));
        }
    }

    /**
     * Applies the time offset to an OffsetDateTime.
     * 
     * @param timestamp the original timestamp
     * @return the anonymized timestamp
     */
    private OffsetDateTime applyTimeOffset(OffsetDateTime timestamp) {
        long newTimeMillis = timestamp.toInstant().toEpochMilli() + timeOffsetMillis;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(newTimeMillis), ZoneOffset.UTC);
    }

    /**
     * Deletes a directory and all its contents.
     * 
     * @param directory the directory to delete
     */
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    /**
     * Returns whether the offsets have been initialized.
     * 
     * @return true if offsets are initialized
     */
    public boolean isOffsetsInitialized() {
        return offsetsInitialized;
    }

    // Getters for offsets (useful for testing)
    public double getLatitudeOffset() { return latitudeOffset; }
    public double getLongitudeOffset() { return longitudeOffset; }
    public long getTimeOffsetMillis() { return timeOffsetMillis; }

    /**
     * Main method for testing the DataAnonymizer.
     * Usage: java DataAnonymizer [sourceFolder] [destinationFolder]
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        File sourceFolder;
        File destinationFolder;
        
        if (args.length >= 2) {
            sourceFolder = new File(args[0]);
            destinationFolder = new File(args[1]);
        } else {
            // Default paths for testing
            sourceFolder = new File("/home/zp/Desktop/Sample-data/rasterIndex");
            destinationFolder = new File("/home/zp/Desktop/Sample-data/anonymized");
        }

        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            System.err.println("Source folder does not exist: " + sourceFolder.getAbsolutePath());
            return;
        }

        System.out.println("Anonymizing: " + sourceFolder.getAbsolutePath());
        System.out.println("Output to: " + destinationFolder.getAbsolutePath());

        DataAnonymizer anonymizer = new DataAnonymizer();
        anonymizer.anonymizeRasterFolder(sourceFolder, destinationFolder);

        System.out.println("Anonymization complete. Output written to: " + destinationFolder.getAbsolutePath());
    }
}
