//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8detector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Detection class.
 */
class DetectionTest {

    @Test
    @DisplayName("Test basic Detection creation")
    void testDetectionCreation() {
        Detection d = new Detection("rock", 0.85, 0.5, 0.5, 0.2, 0.3);
        
        assertEquals("rock", d.getLabel());
        assertEquals(0.85, d.getConfidence(), 0.001);
        assertEquals(0.5, d.getX(), 0.001);
        assertEquals(0.5, d.getY(), 0.001);
        assertEquals(0.2, d.getWidth(), 0.001);
        assertEquals(0.3, d.getHeight(), 0.001);
    }

    @Test
    @DisplayName("Test Detection corner coordinates")
    void testCornerCoordinates() {
        Detection d = new Detection("mine", 0.90, 0.5, 0.5, 0.2, 0.4);
        
        // x1 = x - width/2 = 0.5 - 0.1 = 0.4
        assertEquals(0.4, d.getX1(), 0.001);
        // y1 = y - height/2 = 0.5 - 0.2 = 0.3
        assertEquals(0.3, d.getY1(), 0.001);
        // x2 = x + width/2 = 0.5 + 0.1 = 0.6
        assertEquals(0.6, d.getX2(), 0.001);
        // y2 = y + height/2 = 0.5 + 0.2 = 0.7
        assertEquals(0.7, d.getY2(), 0.001);
    }

    @Test
    @DisplayName("Test Detection toString")
    void testToString() {
        Detection d = new Detection("debris", 0.923, 0.5, 0.5, 0.1, 0.1);
        String result = d.toString();
        
        assertTrue(result.contains("debris"));
        assertTrue(result.contains("92.30%"));
    }

    @Test
    @DisplayName("Test Detection comparison")
    void testComparison() {
        Detection high = new Detection("rock", 0.90, 0.5, 0.5, 0.1, 0.1);
        Detection medium = new Detection("debris", 0.60, 0.3, 0.3, 0.1, 0.1);
        Detection low = new Detection("sand", 0.30, 0.7, 0.7, 0.1, 0.1);
        
        // Higher confidence should come first
        assertTrue(high.compareTo(medium) < 0);
        assertTrue(medium.compareTo(low) < 0);
        assertTrue(low.compareTo(high) > 0);
    }

    @Test
    @DisplayName("Test Detection sorting")
    void testSorting() {
        List<Detection> detections = new ArrayList<>();
        detections.add(new Detection("debris", 0.40, 0.2, 0.2, 0.1, 0.1));
        detections.add(new Detection("rock", 0.85, 0.5, 0.5, 0.1, 0.1));
        detections.add(new Detection("sand", 0.10, 0.8, 0.8, 0.1, 0.1));
        detections.add(new Detection("mine", 0.95, 0.3, 0.7, 0.1, 0.1));
        
        Collections.sort(detections);
        
        // Should be sorted by confidence descending
        assertEquals("mine", detections.get(0).getLabel());
        assertEquals(0.95, detections.get(0).getConfidence(), 0.001);
        
        assertEquals("rock", detections.get(1).getLabel());
        assertEquals(0.85, detections.get(1).getConfidence(), 0.001);
        
        assertEquals("debris", detections.get(2).getLabel());
        assertEquals("sand", detections.get(3).getLabel());
    }

    @Test
    @DisplayName("Test Detection equality")
    void testEquality() {
        Detection d1 = new Detection("rock", 0.85, 0.5, 0.5, 0.1, 0.1);
        Detection d2 = new Detection("rock", 0.85, 0.5, 0.5, 0.1, 0.1);
        Detection d3 = new Detection("debris", 0.85, 0.5, 0.5, 0.1, 0.1);
        
        assertEquals(d1, d2);
        assertNotEquals(d1, d3);
    }

    @Test
    @DisplayName("Test no-arg constructor")
    void testNoArgConstructor() {
        Detection d = new Detection();
        
        assertNull(d.getLabel());
        assertEquals(0.0, d.getConfidence(), 0.001);
        assertEquals(0.0, d.getX(), 0.001);
        assertEquals(0.0, d.getY(), 0.001);
        assertEquals(0.0, d.getWidth(), 0.001);
        assertEquals(0.0, d.getHeight(), 0.001);
    }

    @Test
    @DisplayName("Test setters")
    void testSetters() {
        Detection d = new Detection();
        
        d.setLabel("test");
        d.setConfidence(0.75);
        d.setX(0.6);
        d.setY(0.4);
        d.setWidth(0.2);
        d.setHeight(0.3);
        
        assertEquals("test", d.getLabel());
        assertEquals(0.75, d.getConfidence(), 0.001);
        assertEquals(0.6, d.getX(), 0.001);
        assertEquals(0.4, d.getY(), 0.001);
        assertEquals(0.2, d.getWidth(), 0.001);
        assertEquals(0.3, d.getHeight(), 0.001);
    }
}
