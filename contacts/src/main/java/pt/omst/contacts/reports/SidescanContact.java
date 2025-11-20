//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.reports;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.contacts.CompressedContact;

public class SidescanContact {
    String id;
    double lat;
    double lon;
    double width;
    double length;
    double depth;
    double altitude;
    String classification; // e.g., "Rock", "Wreck"
    String confidence; // "High", "Medium", "Low"
    String remarks;
    String base64Image; // The image converted to string
    List<ObservationData> observations;

    // Constructor
    public SidescanContact(String id, double lat, double lon, String classification, String confidence,
            String imagePath) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.classification = classification;
        this.confidence = confidence;
        // In a real scenario, you convert the imagePath to Base64 here immediately
        // or lazily. See the helper method below.
    }

    public void setImage(BufferedImage image) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            this.base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            e.printStackTrace();
            this.base64Image = "";
        }
    }

    // Getters represent the {{tags}} in the HTML
    // Mustache uses getters automatically (e.g., {{lat}} calls getLat())
    public String getId() {
        return id;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String getClassification() {
        return classification;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getBase64Image() {
        return base64Image;
    }

    // Setters for width/length etc...
    public void setDims(double w, double l, double depth) {
        this.width = w;
        this.length = l;
        this.depth = depth;
    }

    public void setBase64Image(String b64) {
        this.base64Image = b64;
    }

    public double getWidth() {
        return width;
    }

    public double getLength() {
        return length;
    }

    public double getDepth() {
        return depth;
    }

    public double getAltitude() {
        return altitude;
    }

    public String getRemarks() {
        return remarks != null ? remarks : "";
    }

    public List<ObservationData> getObservations() {
        return observations != null ? observations : new ArrayList<>();
    }

    // Inner class for observation data
    public static class ObservationData {
        String formattedTimestamp;
        String userName;
        String systemName;
        List<AnnotationData> annotations;
        String base64Image;

        public ObservationData(String formattedTimestamp, String userName, String systemName, List<AnnotationData> annotations, String base64Image) {
            this.formattedTimestamp = formattedTimestamp;
            this.userName = userName;
            this.systemName = systemName;
            this.annotations = annotations;
            this.base64Image = base64Image;
        }

        public String getFormattedTimestamp() {
            return formattedTimestamp != null ? formattedTimestamp : "";
        }

        public String getUserName() {
            return userName != null ? userName : "";
        }

        public String getSystemName() {
            return systemName != null ? systemName : "";
        }

        public List<AnnotationData> getAnnotations() {
            return annotations != null ? annotations : new ArrayList<>();
        }

        public String getBase64Image() {
            return base64Image != null ? base64Image : "";
        }
    }

    // Inner class for annotation data
    public static class AnnotationData {
        String type;
        String text;
        String category;
        String confidence;
        String measurements;

        public AnnotationData(String type, String text, String category, String confidence, String measurements) {
            this.type = type;
            this.text = text;
            this.category = category;
            this.confidence = confidence;
            this.measurements = measurements;
        }

        public String getType() {
            return type != null ? type : "";
        }

        public String getText() {
            return text != null ? text : "";
        }

        public String getCategory() {
            return category != null ? category : "";
        }

        public String getConfidence() {
            return confidence != null ? confidence : "";
        }

        public String getMeasurements() {
            return measurements != null ? measurements : "";
        }
    }

    // Factory method to convert CompressedContact to SidescanContact
    public static SidescanContact fromCompressedContact(CompressedContact cc) {
        String id = cc.getLabel() != null ? cc.getLabel() : "Unknown";
        double lat = cc.getContact().getLatitude() != null ? cc.getContact().getLatitude() : 0.0;
        double lon = cc.getContact().getLongitude() != null ? cc.getContact().getLongitude() : 0.0;
        String classification = cc.getClassification() != null ? cc.getClassification() : "Unclassified";
        
        // Extract confidence from annotations
        String confidence = "";
        if (cc.getContact().getObservations() != null) {
            for (Observation obs : cc.getContact().getObservations()) {
                if (obs.getAnnotations() != null) {
                    for (Annotation ann : obs.getAnnotations()) {
                        if (ann.getAnnotationType() == AnnotationType.CLASSIFICATION && ann.getConfidence() != null) {
                            confidence = String.valueOf(ann.getConfidence().intValue());
                            break;
                        }
                    }
                }
                if (!confidence.isEmpty()) break;
            }
        }

        SidescanContact contact = new SidescanContact(id, lat, lon, classification, confidence, null);
        
        // Set depth and altitude
        if (cc.getContact().getDepth() != null) {
            contact.depth = cc.getContact().getDepth();
            contact.altitude = cc.getContact().getDepth(); // Altitude = depth for now
        }

        // Set description as remarks
        String description = cc.getDescription();
        contact.remarks = description != null ? description : "";

        // Convert thumbnail to Base64 (using thumbnail with measurements)
        try {
            Image thumbnail = cc.getThumbnailWithMeasurements();
            if (thumbnail != null) {
                BufferedImage bufferedImage;
                if (thumbnail instanceof BufferedImage) {
                    bufferedImage = (BufferedImage) thumbnail;
                } else {
                    bufferedImage = new BufferedImage(thumbnail.getWidth(null), thumbnail.getHeight(null), BufferedImage.TYPE_INT_RGB);
                    bufferedImage.getGraphics().drawImage(thumbnail, 0, 0, null);
                }
                contact.setImage(bufferedImage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            contact.base64Image = "";
        }

        // Process observations
        List<ObservationData> observationsList = new ArrayList<>();
        if (cc.getContact().getObservations() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            for (Observation obs : cc.getContact().getObservations()) {
                String formattedTimestamp = obs.getTimestamp() != null 
                    ? obs.getTimestamp().format(formatter) 
                    : "";
                String userName = obs.getUserName() != null ? obs.getUserName() : "";
                String systemName = obs.getSystemName() != null ? obs.getSystemName() : "";
                
                List<AnnotationData> annotationsList = new ArrayList<>();
                if (obs.getAnnotations() != null) {
                    for (Annotation ann : obs.getAnnotations()) {
                        String type = ann.getAnnotationType() != null ? ann.getAnnotationType().toString() : "";
                        String text = ann.getText() != null ? ann.getText() : "";
                        String cat = ann.getCategory() != null ? ann.getCategory() : "";
                        String conf = ann.getConfidence() != null ? String.valueOf(ann.getConfidence()) : "";
                        
                        // Build measurements string
                        StringBuilder measurements = new StringBuilder();
                        if (ann.getMeasurementType() != null) {
                            measurements.append(ann.getMeasurementType().toString());
                            if (ann.getValue() != null) {
                                measurements.append(": ").append(String.format("%.2f", ann.getValue()));
                            }
                        }
                        
                        annotationsList.add(new AnnotationData(type, text, cat, conf, measurements.toString()));
                    }
                }
                
                // Get thumbnail for this observation with measurements
                String obsBase64 = "";
                try {
                    Image obsImage = cc.getObservationThumbnail(obs.getUuid());
                    Double heightProportion = cc.getObservationHeightProportion(obs.getUuid());
                    
                    System.out.println("DEBUG: Processing observation " + obs.getUuid());
                    System.out.println("DEBUG: Image size: " + (obsImage != null ? obsImage.getWidth(null) + "x" + obsImage.getHeight(null) : "null"));
                    System.out.println("DEBUG: heightProportion: " + heightProportion);
                    
                    if (obsImage != null && heightProportion != null) {
                        // Draw measurements on the observation image
                        BufferedImage imgWithMeasurements = new BufferedImage(
                            obsImage.getWidth(null), obsImage.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2d = imgWithMeasurements.createGraphics();
                        g2d.drawImage(obsImage, 0, 0, null);
                        
                        // Draw measurements if present
                        if (obs.getAnnotations() != null) {
                            for (Annotation ann : obs.getAnnotations()) {
                                if (ann.getAnnotationType() == AnnotationType.MEASUREMENT && 
                                    ann.getMeasurementType() != MeasurementType.BOX &&
                                    ann.getNormalizedX() != null && ann.getNormalizedY() != null &&
                                    ann.getNormalizedX2() != null && ann.getNormalizedY2() != null) {
                                    
                                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                    
                                    // Normalized coordinates are based on image width for both X and Y
                                    // Y is then scaled by heightProportion (see SidescanObservationPanel.imageToScreenCoords)
                                    int imgWidth = obsImage.getWidth(null);
                                    int imgHeight = obsImage.getHeight(null);
                                    
                                    // Y = normalizedY * width * heightProportion
                                    int x1 = (int)(ann.getNormalizedX() * imgWidth);
                                    int y1 = (int)(ann.getNormalizedY() * imgWidth * heightProportion);
                                    int x2 = (int)(ann.getNormalizedX2() * imgWidth);
                                    int y2 = (int)(ann.getNormalizedY2() * imgWidth * heightProportion);
                                    
                                    System.out.println("DEBUG: Measurement " + ann.getMeasurementType());
                                    System.out.println("DEBUG: Normalized coords: (" + ann.getNormalizedX() + "," + ann.getNormalizedY() + ") to (" + ann.getNormalizedX2() + "," + ann.getNormalizedY2() + ")");
                                    System.out.println("DEBUG: Before crop offset: (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
                                    
                                    // If image was cropped, offset Y coordinates
                                    int expectedHeight = (int)(imgWidth * heightProportion);
                                    if (expectedHeight > imgHeight) {
                                        int cropOffset = (expectedHeight - imgHeight) / 2;
                                        y1 -= cropOffset;
                                        y2 -= cropOffset;
                                        System.out.println("DEBUG: Applied crop offset: " + cropOffset);
                                    }
                                    System.out.println("DEBUG: Final coords: (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
                                    
                                    // Draw shadow
                                    g2d.setColor(Color.BLACK);
                                    g2d.setStroke(new BasicStroke(3));
                                    g2d.drawLine(x1, y1, x2, y2);
                                    
                                    // Draw colored line
                                    Color lineColor = switch (ann.getMeasurementType()) {
                                        case WIDTH -> new Color(255, 0, 0, 200);
                                        case LENGTH -> new Color(0, 255, 0, 200);
                                        case HEIGHT -> new Color(0, 0, 255, 200);
                                        default -> Color.WHITE;
                                    };
                                    g2d.setColor(lineColor);
                                    g2d.setStroke(new BasicStroke(2));
                                    g2d.drawLine(x1, y1, x2, y2);
                                }
                            }
                        }
                        g2d.dispose();
                        
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(imgWithMeasurements, "png", baos);
                        obsBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                    }
                } catch (Exception e) {
                    // If image loading fails, continue without image
                }
                
                observationsList.add(new ObservationData(formattedTimestamp, userName, systemName, annotationsList, obsBase64));
            }
        }
        contact.observations = observationsList;

        // Calculate dimensions if observations have measurement annotations
        double maxWidth = 0, maxLength = 0;
        if (cc.getContact().getObservations() != null) {
            for (Observation obs : cc.getContact().getObservations()) {
                if (obs.getAnnotations() != null) {
                    for (Annotation ann : obs.getAnnotations()) {
                        if (ann.getMeasurementType() != null && ann.getValue() != null) {
                            String measureType = ann.getMeasurementType().toString();
                            if (measureType.contains("WIDTH") || measureType.contains("DIAMETER")) {
                                maxWidth = Math.max(maxWidth, ann.getValue());
                            } else if (measureType.contains("LENGTH") || measureType.contains("HEIGHT")) {
                                maxLength = Math.max(maxLength, ann.getValue());
                            }
                        }
                    }
                }
            }
        }
        if (maxWidth > 0 || maxLength > 0) {
            contact.width = maxWidth;
            contact.length = maxLength;
        }

        return contact;
    }
}