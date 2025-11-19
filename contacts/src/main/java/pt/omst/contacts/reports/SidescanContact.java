package pt.omst.contacts.reports;

import java.awt.image.BufferedImage;

public class SidescanContact {
    String id;
    double lat;
    double lon;
    double width;
    double length;
    double depth;
    String classification; // e.g., "Rock", "Wreck"
    String confidence; // "High", "Medium", "Low"
    String remarks;
    String base64Image; // The image converted to string

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
}