//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
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
import pt.omst.rasterlib.MeasurementType;
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
    private UUID thumbnailObservationUuid = null;

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

    public IndexedRaster getFirstRaster() {
        try {
            for (Observation obs : contact.getObservations()) {
                if (obs.getRasterFilename() != null) {
                    File rasterFile = new File(getTempDir(), obs.getRasterFilename());
                    if (rasterFile.exists()) {
                        return Converter.IndexedRasterFromJsonString(Files.readString(rasterFile.toPath()));
                    }
                }                
            }
        } catch (IOException e) {
            log.warn("Error reading first raster from {}: {}", zctFile.getAbsolutePath(), e.getMessage());
        }
        return null;
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
                        int newImageHeight = (int)(224 * heightWidthRatio);
                             
                        BufferedImage resized = Scalr.resize(img, Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, 224, newImageHeight);
                        // If taller than 224, crop equally from top and bottom
                        if (resized.getHeight() > 224) {
                            int excess = resized.getHeight() - 224;
                            int cropTop = excess / 2;
                            BufferedImage cropped = resized.getSubimage(0, cropTop, resized.getWidth(), 224);
                            thumbnail = cropped;
                        } else {
                            thumbnail = resized;
                        }                        thumbnailObservationUuid = obs.getUuid();                    }
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
     * Get the thumbnail image for a specific observation.
     * 
     * @param observationUuid The UUID of the observation to get the thumbnail for
     * @return The thumbnail image, or null if not found
     */
    public Image getObservationThumbnail(UUID observationUuid) {
        if (observationUuid == null)
            return getThumbnail();
            
        try {
            for (Observation obs : contact.getObservations()) {
                if (obs.getUuid() != null && obs.getUuid().equals(observationUuid)) {
                    if (obs.getRasterFilename() != null) {
                        File rasterFile = new File(getTempDir(), obs.getRasterFilename());
                        if (rasterFile.exists()) {
                            IndexedRaster indexedRaster = Converter.IndexedRasterFromJsonString(Files.readString(rasterFile.toPath()));
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
                            int newImageHeight = (int)(224 * heightWidthRatio);
                                 
                            BufferedImage resized = Scalr.resize(img, Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, 224, newImageHeight);
                            // If taller than 224, crop equally from top and bottom
                            if (resized.getHeight() > 224) {
                                int excess = resized.getHeight() - 224;
                                int cropTop = excess / 2;
                                return resized.getSubimage(0, cropTop, resized.getWidth(), 224);
                            }
                            return resized;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error loading observation thumbnail for {}: {}", observationUuid, e.getMessage());
        }
        return null;
    }

    /**
     * Get the height/width ratio for an observation's raster.
     * This is needed to properly scale Y coordinates when drawing on thumbnails.
     */
    public Double getObservationHeightProportion(UUID observationUuid) {
        try {
            for (Observation obs : contact.getObservations()) {
                if (obs.getUuid() != null && obs.getUuid().equals(observationUuid)) {
                    if (obs.getRasterFilename() != null) {
                        File rasterFile = new File(getTempDir(), obs.getRasterFilename());
                        if (rasterFile.exists()) {
                            IndexedRaster indexedRaster = Converter.IndexedRasterFromJsonString(Files.readString(rasterFile.toPath()));
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

                            return distanceMeters / widthMeters;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting observation height proportion for {}: {}", observationUuid, e.getMessage());
        }
        return null;
    }

    /**
     * Get a thumbnail image with measurement annotations (WIDTH, LENGTH, HEIGHT) drawn on it.
     * If no measurement annotations are found (excluding BOX), returns the regular thumbnail.
     * 
     * @return the thumbnail image with measurements drawn
     */
    public Image getThumbnailWithMeasurements() {
        Image baseThumbnail = getThumbnail();
        
        // Check if there are any measurement annotations
        boolean hasMeasurements = false;
        for (Observation obs : contact.getObservations()) {
            if (obs.getAnnotations() != null) {
                for (Annotation annotation : obs.getAnnotations()) {
                    if (annotation.getAnnotationType() == AnnotationType.MEASUREMENT) {
                        hasMeasurements = true;
                        break;
                    }
                }
            }
            if (hasMeasurements) break;
        }
        
        // If no measurements found, return the regular thumbnail
        if (!hasMeasurements) {
            return baseThumbnail;
        }
        
        // Create a copy of the thumbnail to draw on
        BufferedImage thumbnailWithMeasurements = new BufferedImage(
            baseThumbnail.getWidth(null), 
            baseThumbnail.getHeight(null), 
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = thumbnailWithMeasurements.createGraphics();
        
        // Enable antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw the base thumbnail
        g2d.drawImage(baseThumbnail, 0, 0, null);
        
        // Draw measurements ONLY from the observation that the thumbnail came from
        // We MUST have a valid thumbnailObservationUuid to ensure we draw measurements
        // on the correct image. If it's null, we can't safely draw measurements.
        System.out.println("DEBUG [getThumbnailWithMeasurements]: Contact: " + contact.getLabel());
        System.out.println("DEBUG [getThumbnailWithMeasurements]: thumbnailObservationUuid: " + thumbnailObservationUuid);
        System.out.println("DEBUG [getThumbnailWithMeasurements]: Total observations: " + contact.getObservations().size());
        
        if (thumbnailObservationUuid != null) {
            boolean foundMatch = false;
            for (Observation obs : contact.getObservations()) {
                System.out.println("DEBUG [getThumbnailWithMeasurements]:   Checking obs UUID: " + obs.getUuid());
                if (obs.getUuid() != null && obs.getUuid().equals(thumbnailObservationUuid)) {
                    foundMatch = true;
                    System.out.println("DEBUG [getThumbnailWithMeasurements]:   MATCH! Processing annotations...");
                    if (obs.getAnnotations() != null) {
                        System.out.println("DEBUG [getThumbnailWithMeasurements]:   Found " + obs.getAnnotations().size() + " annotations");
                        int measurementCount = 0;
                        for (Annotation annotation : obs.getAnnotations()) {
                            System.out.println("DEBUG [getThumbnailWithMeasurements]:     Annotation type: " + annotation.getAnnotationType());
                            if (annotation.getAnnotationType() == AnnotationType.MEASUREMENT) {
                                measurementCount++;
                                System.out.println("DEBUG [getThumbnailWithMeasurements]:     Drawing measurement #" + measurementCount + " type: " + annotation.getMeasurementType());
                                drawMeasurementOnThumbnail(annotation, g2d, 
                                    baseThumbnail.getWidth(null), baseThumbnail.getHeight(null));
                            }
                        }
                        System.out.println("DEBUG [getThumbnailWithMeasurements]:   Drew " + measurementCount + " measurements");
                    } else {
                        System.out.println("DEBUG [getThumbnailWithMeasurements]:   No annotations found");
                    }
                    break; // Found the correct observation
                }
            }
            if (!foundMatch) {
                System.out.println("DEBUG [getThumbnailWithMeasurements]:   NO MATCHING OBSERVATION FOUND!");
            }
        } else {
            System.out.println("DEBUG [getThumbnailWithMeasurements]: thumbnailObservationUuid is NULL - cannot draw measurements");
        }
        
        g2d.dispose();
        return thumbnailWithMeasurements;
    }
    
    /**
     * Draw a measurement annotation on the thumbnail.
     * The thumbnail has been scaled maintaining aspect ratio, so we need to account for that.
     * Normalized coordinates are based on image width for both X and Y.
     * Y is then scaled by heightProportion (distanceMeters / widthMeters).
     */
    private void drawMeasurementOnThumbnail(Annotation annotation, Graphics2D g2d, int width, int height) {
        // Get the height proportion from the observation that was used to create the thumbnail
        Double heightProportion = getObservationHeightProportion(thumbnailObservationUuid);
        
        if (heightProportion == null) {
            heightProportion = 1.0; // fallback to square
        }
        
        System.out.println("DEBUG [drawMeasurementOnThumbnail]: width=" + width + ", height=" + height + ", heightProportion=" + heightProportion);
        System.out.println("DEBUG [drawMeasurementOnThumbnail]: Measurement " + annotation.getMeasurementType());
        System.out.println("DEBUG [drawMeasurementOnThumbnail]: Normalized coords: (" + annotation.getNormalizedX() + "," + annotation.getNormalizedY() + ") to (" + annotation.getNormalizedX2() + "," + annotation.getNormalizedY2() + ")");
        
        // Normalized coordinates map to:
        // X: normalizedX * width
        // Y: normalizedY * width * heightProportion (note: width, not height!)
        int x1 = (int) (annotation.getNormalizedX() * width);
        int y1 = (int) (annotation.getNormalizedY() * width * heightProportion);
        int x2 = (int) (annotation.getNormalizedX2() * width);
        int y2 = (int) (annotation.getNormalizedY2() * width * heightProportion);
        
        System.out.println("DEBUG [drawMeasurementOnThumbnail]: Before crop offset: (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
        
        // If the image was cropped (expected height > actual height), offset Y
        int expectedHeight = (int)(width * heightProportion);
        if (expectedHeight > height) {
            int cropOffset = (expectedHeight - height) / 2;
            y1 -= cropOffset;
            y2 -= cropOffset;
            System.out.println("DEBUG [drawMeasurementOnThumbnail]: Applied crop offset: " + cropOffset);
        }
        
        System.out.println("DEBUG [drawMeasurementOnThumbnail]: Final coords: (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
        
        // Draw based on measurement type
        if (annotation.getMeasurementType() == MeasurementType.BOX) {
            // Draw BOX as a rectangle
            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int boxWidth = Math.abs(x2 - x1);
            int boxHeight = Math.abs(y2 - y1);
            
            // Draw shadow (4px black)
            g2d.setColor(new Color(0, 0, 0, 128));
            g2d.setStroke(new BasicStroke(4f));
            g2d.drawRect(minX, minY, boxWidth, boxHeight);
            
            // Draw box (2px white)
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawRect(minX, minY, boxWidth, boxHeight);
        } else {
            // Draw line measurements
            // Draw shadow (3px black)
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawLine(x1, y1, x2, y2);
            
            // Draw colored line (2px) based on measurement type
            Color lineColor = switch (annotation.getMeasurementType()) {
                case WIDTH -> new Color(255, 0, 0, 200);     // Red
                case LENGTH -> new Color(0, 255, 0, 200);    // Green
                case HEIGHT -> new Color(0, 0, 255, 200);    // Blue
                default -> Color.WHITE;
            };
            g2d.setColor(lineColor);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(x1, y1, x2, y2);
        }
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
