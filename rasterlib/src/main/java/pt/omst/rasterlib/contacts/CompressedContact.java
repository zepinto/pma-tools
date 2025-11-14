//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.mapview.MapMarker;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.util.ZipUtils;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.Observation;

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

    /**
     * The category of the contact.
     */
    private String category = null;

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
