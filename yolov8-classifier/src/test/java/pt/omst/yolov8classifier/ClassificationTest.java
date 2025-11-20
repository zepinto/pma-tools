//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8classifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Classification class.
 */
class ClassificationTest {

    @Test
    @DisplayName("Test basic Classification creation")
    void testClassificationCreation() {
        Classification c = new Classification("rock", 0.85);
        
        assertEquals("rock", c.getLabel());
        assertEquals(0.85, c.getConfidence(), 0.001);
    }

    @Test
    @DisplayName("Test Classification toString")
    void testToString() {
        Classification c = new Classification("debris", 0.923);
        String result = c.toString();
        
        assertTrue(result.contains("debris"));
        assertTrue(result.contains("92.30%"));
    }

    @Test
    @DisplayName("Test Classification comparison")
    void testComparison() {
        Classification high = new Classification("rock", 0.90);
        Classification medium = new Classification("debris", 0.60);
        Classification low = new Classification("sand", 0.30);
        
        // Higher confidence should come first
        assertTrue(high.compareTo(medium) < 0);
        assertTrue(medium.compareTo(low) < 0);
        assertTrue(low.compareTo(high) > 0);
    }

    @Test
    @DisplayName("Test Classification sorting")
    void testSorting() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("debris", 0.40));
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("sand", 0.10));
        classifications.add(new Classification("mine", 0.95));
        
        Collections.sort(classifications);
        
        // Should be sorted by confidence descending
        assertEquals("mine", classifications.get(0).getLabel());
        assertEquals(0.95, classifications.get(0).getConfidence(), 0.001);
        
        assertEquals("rock", classifications.get(1).getLabel());
        assertEquals(0.85, classifications.get(1).getConfidence(), 0.001);
        
        assertEquals("debris", classifications.get(2).getLabel());
        assertEquals("sand", classifications.get(3).getLabel());
    }

    @Test
    @DisplayName("Test Classification equality")
    void testEquality() {
        Classification c1 = new Classification("rock", 0.85);
        Classification c2 = new Classification("rock", 0.85);
        Classification c3 = new Classification("debris", 0.85);
        
        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
    }

    @Test
    @DisplayName("Test no-arg constructor")
    void testNoArgConstructor() {
        Classification c = new Classification();
        
        assertNull(c.getLabel());
        assertEquals(0.0, c.getConfidence(), 0.001);
    }

    @Test
    @DisplayName("Test setters")
    void testSetters() {
        Classification c = new Classification();
        
        c.setLabel("test");
        c.setConfidence(0.75);
        
        assertEquals("test", c.getLabel());
        assertEquals(0.75, c.getConfidence(), 0.001);
    }
}
