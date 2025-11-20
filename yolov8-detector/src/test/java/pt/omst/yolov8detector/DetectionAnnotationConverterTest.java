//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8detector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the DetectionAnnotationConverter class.
 */
class DetectionAnnotationConverterTest {

    @Test
    @DisplayName("Test single detection to annotation conversion")
    void testToAnnotation() {
        Detection detection = new Detection("rock", 0.85, 0.5, 0.5, 0.2, 0.3);
        Annotation annotation = DetectionAnnotationConverter.toAnnotation(detection);
        
        assertNotNull(annotation);
        assertEquals(AnnotationType.LABEL, annotation.getAnnotationType());
        assertEquals("rock", annotation.getCategory());
        assertEquals(0.85, annotation.getConfidence(), 0.001);
        assertNotNull(annotation.getUserName());
        assertNotNull(annotation.getTimestamp());
        
        // Check bounding box coordinates
        assertEquals(0.4, annotation.getNormalizedX(), 0.001); // x1
        assertEquals(0.35, annotation.getNormalizedY(), 0.001); // y1
        assertEquals(0.6, annotation.getNormalizedX2(), 0.001); // x2
        assertEquals(0.65, annotation.getNormalizedY2(), 0.001); // y2
    }

    @Test
    @DisplayName("Test detection to annotation with custom username")
    void testToAnnotationWithUsername() {
        Detection detection = new Detection("mine", 0.90, 0.3, 0.7, 0.1, 0.15);
        Annotation annotation = DetectionAnnotationConverter.toAnnotation(detection, "test-user");
        
        assertEquals("test-user", annotation.getUserName());
    }

    @Test
    @DisplayName("Test list of detections to annotations")
    void testToAnnotations() {
        List<Detection> detections = List.of(
            new Detection("rock", 0.85, 0.5, 0.5, 0.1, 0.1),
            new Detection("debris", 0.70, 0.3, 0.3, 0.1, 0.1),
            new Detection("mine", 0.90, 0.7, 0.7, 0.1, 0.1)
        );
        
        List<Annotation> annotations = DetectionAnnotationConverter.toAnnotations(detections);
        
        assertEquals(3, annotations.size());
        assertEquals("rock", annotations.get(0).getCategory());
        assertEquals("debris", annotations.get(1).getCategory());
        assertEquals("mine", annotations.get(2).getCategory());
    }

    @Test
    @DisplayName("Test filtering by minimum confidence")
    void testToAnnotationsWithMinConfidence() {
        List<Detection> detections = List.of(
            new Detection("rock", 0.85, 0.5, 0.5, 0.1, 0.1),
            new Detection("debris", 0.40, 0.3, 0.3, 0.1, 0.1),
            new Detection("mine", 0.90, 0.7, 0.7, 0.1, 0.1),
            new Detection("sand", 0.20, 0.8, 0.8, 0.1, 0.1)
        );
        
        List<Annotation> annotations = DetectionAnnotationConverter
            .toAnnotationsWithMinConfidence(detections, 0.5);
        
        assertEquals(2, annotations.size());
        assertEquals("rock", annotations.get(0).getCategory());
        assertEquals("mine", annotations.get(1).getCategory());
    }

    @Test
    @DisplayName("Test filtering by label")
    void testToAnnotationsForLabel() {
        List<Detection> detections = List.of(
            new Detection("rock", 0.85, 0.5, 0.5, 0.1, 0.1),
            new Detection("debris", 0.70, 0.3, 0.3, 0.1, 0.1),
            new Detection("rock", 0.90, 0.7, 0.7, 0.1, 0.1),
            new Detection("mine", 0.80, 0.2, 0.2, 0.1, 0.1)
        );
        
        List<Annotation> annotations = DetectionAnnotationConverter
            .toAnnotationsForLabel(detections, "rock");
        
        assertEquals(2, annotations.size());
        assertEquals("rock", annotations.get(0).getCategory());
        assertEquals("rock", annotations.get(1).getCategory());
    }

    @Test
    @DisplayName("Test empty detection list")
    void testEmptyList() {
        List<Detection> detections = List.of();
        List<Annotation> annotations = DetectionAnnotationConverter.toAnnotations(detections);
        
        assertTrue(annotations.isEmpty());
    }

    @Test
    @DisplayName("Test bounding box coordinate conversion")
    void testBoundingBoxConversion() {
        // Test with detection at origin
        Detection detection = new Detection("test", 0.95, 0.1, 0.1, 0.1, 0.2);
        Annotation annotation = DetectionAnnotationConverter.toAnnotation(detection);
        
        // x1 = 0.1 - 0.05 = 0.05
        assertEquals(0.05, annotation.getNormalizedX(), 0.001);
        // y1 = 0.1 - 0.1 = 0.0
        assertEquals(0.0, annotation.getNormalizedY(), 0.001);
        // x2 = 0.1 + 0.05 = 0.15
        assertEquals(0.15, annotation.getNormalizedX2(), 0.001);
        // y2 = 0.1 + 0.1 = 0.2
        assertEquals(0.2, annotation.getNormalizedY2(), 0.001);
    }
}
