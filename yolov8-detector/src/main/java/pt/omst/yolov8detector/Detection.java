//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8detector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a detection result with label, confidence score, and bounding box.
 * Bounding box coordinates are normalized to [0, 1] range.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Detection implements Comparable<Detection> {
    
    /**
     * Detection label (e.g., "rock", "debris", "mine", etc.)
     */
    private String label;
    
    /**
     * Confidence score between 0.0 and 1.0
     */
    private double confidence;
    
    /**
     * Normalized x-coordinate of bounding box center (0.0 to 1.0)
     */
    private double x;
    
    /**
     * Normalized y-coordinate of bounding box center (0.0 to 1.0)
     */
    private double y;
    
    /**
     * Normalized width of bounding box (0.0 to 1.0)
     */
    private double width;
    
    /**
     * Normalized height of bounding box (0.0 to 1.0)
     */
    private double height;
    
    /**
     * Gets the normalized x-coordinate of the top-left corner
     */
    public double getX1() {
        return x - width / 2.0;
    }
    
    /**
     * Gets the normalized y-coordinate of the top-left corner
     */
    public double getY1() {
        return y - height / 2.0;
    }
    
    /**
     * Gets the normalized x-coordinate of the bottom-right corner
     */
    public double getX2() {
        return x + width / 2.0;
    }
    
    /**
     * Gets the normalized y-coordinate of the bottom-right corner
     */
    public double getY2() {
        return y + height / 2.0;
    }
    
    /**
     * Compare by confidence in descending order (highest confidence first)
     */
    @Override
    public int compareTo(Detection other) {
        return Double.compare(other.confidence, this.confidence);
    }
    
    @Override
    public String toString() {
        return String.format("%s (%.2f%%) [x=%.3f, y=%.3f, w=%.3f, h=%.3f]", 
            label, confidence * 100, x, y, width, height);
    }
}
