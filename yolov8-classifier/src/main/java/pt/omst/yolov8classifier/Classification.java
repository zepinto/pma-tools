//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8classifier;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a classification result with label and confidence score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Classification implements Comparable<Classification> {
    
    /**
     * Classification label (e.g., "rock", "debris", "mine", etc.)
     */
    private String label;
    
    /**
     * Confidence score between 0.0 and 1.0
     */
    private double confidence;
    
    /**
     * Compare by confidence in descending order (highest confidence first)
     */
    @Override
    public int compareTo(Classification other) {
        return Double.compare(other.confidence, this.confidence);
    }
    
    @Override
    public String toString() {
        return String.format("%s (%.2f%%)", label, confidence * 100);
    }
}
