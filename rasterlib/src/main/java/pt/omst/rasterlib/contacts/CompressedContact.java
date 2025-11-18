//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.mapview.MapMarker;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.SampleDescription;

/**
 * A compressed contact is a contact that is stored in a compressed (.zct) file.
 */
@Getter
@Slf4j
public class CompressedContact implements MapMarker, QuadTree.Locatable<CompressedContact> {

    /**
     * The contact object.
     */
    private final Contact contact;
    /**
     * The corresponding compressed file.
     */
    private final File zctFile;

    /**
     * The location of the contact.
     */
    private final LocationType location;
    /**
     * The timestamp of the contact.
     */
    private final long timestamp;

    private Image thumbnail = null;

    /**
     * The category of the contact.
     */
    private String category = null;

    private File tempDir = null;

    public CompressedContact(File zctFile) throws IOException {
        this.zctFile = zctFile;
        this.contact = CompressedContact.extractCompressedContact(zctFile);
        if (contact == null) {
            log.warn("Error reading contact from {}", zctFile.getAbsolutePath());
            throw new IOException("Error reading contact from " + zctFile.getAbsolutePath());
        }
        this.location = new LocationType(contact.getLatitude(), contact.getLongitude());
        this.timestamp = contact.getObservations().stream().min(Comparator.comparing(Observation::getTimestamp))
                .orElseThrow().getTimestamp().toInstant().toEpochMilli();        
    }

    

    /**
     * Set the label of the contact but DOES NOT save it.
     * @param label the label to set
     * @see #save()
     */
    public void setLabel(String label) {
        contact.setLabel(label);
    }

    /**
     * Set the description of the contact but DOES NOT save it.
     * @param description the description to set
     * @see #save()
     */
    public void setDescription(String description) {
        for (Observation obs : contact.getObservations()) {
            for (Annotation annotation : obs.getAnnotations()) {
                if (annotation.getAnnotationType() == AnnotationType.TEXT) {
                    annotation.setText(description);
                    return;
                }
            }
        }

        Annotation annotation = new Annotation();
        annotation.setAnnotationType(AnnotationType.TEXT);
        annotation.setText(description);
        if (contact.getObservations().isEmpty()) {
            Observation obs = new Observation();
            obs.setTimestamp(OffsetDateTime.now());
            obs.setUserName(System.getProperty("user.name"));
            obs.setUuid(UUID.randomUUID());
            contact.getObservations().add(obs);
        }

        contact.getObservations().getFirst().getAnnotations().add(annotation);
    }

    /**
     * Get the description of the contact.
     * @return the description of the contact
     */
    public String getDescription() {
        for (Observation obs : contact.getObservations()) {
            if (obs.getAnnotations() == null) {
                continue;
            }
            for (Annotation annotation : obs.getAnnotations()) {
                if (annotation.getAnnotationType() == AnnotationType.TEXT) {
                    return annotation.getText();
                }
            }
        }
        return null;
    }

    /**
     * Get the label of the contact.
     * @return the label of the contact
     */
    public String getLabel() {
        return contact.getLabel();
    }


    public String getClassification() {
        if (category != null)
            return category;
        
        for (Observation obs : contact.getObservations()) {
            if (obs.getAnnotations() == null) {
                continue;
            }
            for (Annotation annotation : obs.getAnnotations()) {
                if (annotation.getAnnotationType() == AnnotationType.CLASSIFICATION) {
                    category = annotation.getCategory();
                    break;
                }
            }
        }
        return category;
    }

    private File getTempDir() {
        if (tempDir != null)
            return tempDir;
        try {
            tempDir = Files.createTempDirectory("zct").toFile();
            log.info("Unzipping file {} to {}", zctFile.getAbsolutePath(), tempDir);
            ZipUtils.unzip(zctFile.getAbsolutePath(), tempDir.toPath());
        }
        catch (IOException e) {
            log.warn("Error creating temp dir for {}: {}", zctFile.getAbsolutePath(), e.getMessage());
        }
        return tempDir;
    }

    public Image getThumbnail() {
        if (thumbnail != null)
            return thumbnail;        
        try {
            for (Observation obs : contact.getObservations()) {
                if (obs.getRasterFilename() != null) {
                    log.info("Loading thumbnail for contact {} from raster {}", 
                            contact.getLabel(), obs.getRasterFilename());
                    File rasterFile = new File(getTempDir(), obs.getRasterFilename());
                    if (rasterFile.exists()) {
                        log.info("Raster file {} exists, reading...", rasterFile.getAbsolutePath());
                        IndexedRaster indexedRaster =  Converter.IndexedRasterFromJsonString(Files.readString(rasterFile.toPath()));
                        BufferedImage img = ImageIO.read(new File(getTempDir(), indexedRaster.getFilename()));
                        SampleDescription firstSample = indexedRaster.getSamples().getFirst();
                        SampleDescription lastSample = indexedRaster.getSamples().getLast();

                        LocationType topLocation = new LocationType(
                                firstSample.getPose().getLatitude(),
                                firstSample.getPose().getLongitude());
                        LocationType bottomLocation = new LocationType(
                                lastSample.getPose().getLatitude(),
                                lastSample.getPose().getLongitude());

                        double distanceMeters = topLocation.getHorizontalDistanceInMeters(bottomLocation);
                        double widthMeters = indexedRaster.getSensorInfo().getMaxRange() - indexedRaster.getSensorInfo().getMinRange();

                        double heightWidthRatio = distanceMeters / widthMeters;
                        int newImageHeight = (int)(200 * heightWidthRatio);
                             
                        BufferedImage resized = Scalr.resize(img, Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, 200, newImageHeight);
                        // If taller than 300px, crop equally from top and bottom
                        if (resized.getHeight() > 200) {
                            int excess = resized.getHeight() - 200;
                            int cropTop = excess / 2;
                            BufferedImage cropped = resized.getSubimage(0, cropTop, resized.getWidth(), 200);
                            thumbnail = cropped;
                        } else {
                            thumbnail = resized;
                        }
                    }
                    else {
                        log.warn("Raster file {} does not exist", rasterFile.getAbsolutePath());
                    }
                }                
            }
        } catch (IOException e) {
            log.warn("Error reading thumbnail from {}: {}", zctFile.getAbsolutePath(), e.getMessage());
        }
        if (thumbnail == null) {
            log.warn("No thumbnail available for {}", zctFile.getAbsolutePath());
            thumbnail = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        }
        return thumbnail;
    }

    /**
     * Set the classification of the contact but DOES NOT save it.
     * @param category the category to set
     * @param confidence the confidence to set
     * @see #save()
     */
    public void setClassification(String category, Double confidence) {
        for (Observation obs : contact.getObservations()) {
            for (Annotation annotation : obs.getAnnotations()) {
                if (annotation.getAnnotationType() == AnnotationType.CLASSIFICATION) {
                    annotation.setCategory(category);
                    annotation.setConfidence(confidence);
                }
                return;
            }
        }

        Annotation annotation = new Annotation();
        annotation.setAnnotationType(AnnotationType.CLASSIFICATION);
        annotation.setCategory(category);
        annotation.setConfidence(confidence);
        if (contact.getObservations().isEmpty()) {
            Observation obs = new Observation();
            obs.setTimestamp(OffsetDateTime.now());
            obs.setUserName(System.getProperty("user.name"));
            obs.setUuid(UUID.randomUUID());
            contact.getObservations().add(obs);
        }

        contact.getObservations().getFirst().getAnnotations().add(annotation);
    }

    /**
     * Saves the contact to the compressed file.
     */
    public boolean save() {
        try {
            String json = Converter.ContactToJsonString(contact);
            ZipUtils.updateFileInZip(zctFile.getAbsolutePath(), "contact.json", json);
            log.info("Contact saved to {}", zctFile.getAbsolutePath());
            category = null; // reset cached category
            return true;
        }
        catch (IOException e) {
            log.warn("Error saving contact: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts a contact from a compressed file.
     * @param zctFile the compressed file
     * @return the contact
     * @throws IOException if an error occurs while reading the contact
     */
    public static Contact extractCompressedContact(File zctFile) throws IOException {
        InputStream is = ZipUtils.getFileInZip(zctFile.getAbsolutePath(), "contact.json");
        if (is == null) {
            log.warn("No contact.json found in {}", zctFile.getAbsolutePath());
            return null;
        }
        
        String jsonString = new String(Objects.requireNonNull(ZipUtils.getFileInZip(zctFile.getAbsolutePath(), "contact.json")).readAllBytes());
        return Converter.ContactFromJsonString(jsonString);
    }

    @Override
    public double getLatitude() {
        return location.getLatitudeDegs();
    }

    @Override
    public double getLongitude() {
        return location.getLongitudeDegs();
    }

    @Override
    public int compareTo(CompressedContact other) {
        return this.getZctFile().getAbsolutePath().compareTo(other.getZctFile().getAbsolutePath());
    }
}
