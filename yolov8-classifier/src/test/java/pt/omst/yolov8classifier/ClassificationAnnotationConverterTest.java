//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8classifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ClassificationAnnotationConverter.
 */
class ClassificationAnnotationConverterTest {

    @Test
    @DisplayName("Test converting single Classification to Annotation")
    void testToAnnotation() {
        Classification classification = new Classification("rock", 0.85);
        
        Annotation annotation = ClassificationAnnotationConverter.toAnnotation(classification);
        
        assertNotNull(annotation);
        assertEquals(AnnotationType.CLASSIFICATION, annotation.getAnnotationType());
        assertEquals("rock", annotation.getCategory());
        assertEquals(0.85, annotation.getConfidence(), 0.001);
        assertEquals("yolov8-classifier", annotation.getUserName());
        assertNotNull(annotation.getTimestamp());
    }

    @Test
    @DisplayName("Test converting Classification to Annotation with custom username")
    void testToAnnotationWithUsername() {
        Classification classification = new Classification("debris", 0.72);
        
        Annotation annotation = ClassificationAnnotationConverter.toAnnotation(classification, "test-user");
        
        assertNotNull(annotation);
        assertEquals("debris", annotation.getCategory());
        assertEquals("test-user", annotation.getUserName());
    }

    @Test
    @DisplayName("Test converting list of Classifications to Annotations")
    void testToAnnotations() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("debris", 0.10));
        classifications.add(new Classification("sand", 0.05));
        
        List<Annotation> annotations = ClassificationAnnotationConverter.toAnnotations(classifications);
        
        assertNotNull(annotations);
        assertEquals(3, annotations.size());
        
        assertEquals("rock", annotations.get(0).getCategory());
        assertEquals(0.85, annotations.get(0).getConfidence(), 0.001);
        
        assertEquals("debris", annotations.get(1).getCategory());
        assertEquals(0.10, annotations.get(1).getConfidence(), 0.001);
        
        assertEquals("sand", annotations.get(2).getCategory());
        assertEquals(0.05, annotations.get(2).getConfidence(), 0.001);
    }

    @Test
    @DisplayName("Test converting top classification to Annotation")
    void testTopClassificationToAnnotation() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("debris", 0.10));
        classifications.add(new Classification("sand", 0.05));
        
        Annotation annotation = ClassificationAnnotationConverter.topClassificationToAnnotation(classifications);
        
        assertNotNull(annotation);
        assertEquals("rock", annotation.getCategory());
        assertEquals(0.85, annotation.getConfidence(), 0.001);
    }

    @Test
    @DisplayName("Test top classification with empty list")
    void testTopClassificationWithEmptyList() {
        List<Classification> classifications = new ArrayList<>();
        
        Annotation annotation = ClassificationAnnotationConverter.topClassificationToAnnotation(classifications);
        
        assertNull(annotation);
    }

    @Test
    @DisplayName("Test top classification with null list")
    void testTopClassificationWithNullList() {
        Annotation annotation = ClassificationAnnotationConverter.topClassificationToAnnotation(null);
        
        assertNull(annotation);
    }

    @Test
    @DisplayName("Test filtering by minimum confidence")
    void testToAnnotationsWithMinConfidence() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("debris", 0.45));
        classifications.add(new Classification("sand", 0.05));
        classifications.add(new Classification("mine", 0.03));
        
        List<Annotation> annotations = ClassificationAnnotationConverter
            .toAnnotationsWithMinConfidence(classifications, 0.40);
        
        assertNotNull(annotations);
        assertEquals(2, annotations.size());
        
        assertEquals("rock", annotations.get(0).getCategory());
        assertEquals("debris", annotations.get(1).getCategory());
    }

    @Test
    @DisplayName("Test filtering with confidence threshold of 1.0")
    void testMinConfidenceThresholdMax() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("debris", 0.10));
        
        List<Annotation> annotations = ClassificationAnnotationConverter
            .toAnnotationsWithMinConfidence(classifications, 1.0);
        
        assertNotNull(annotations);
        assertEquals(0, annotations.size());
    }

    @Test
    @DisplayName("Test filtering with confidence threshold of 0.0")
    void testMinConfidenceThresholdMin() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("debris", 0.10));
        
        List<Annotation> annotations = ClassificationAnnotationConverter
            .toAnnotationsWithMinConfidence(classifications, 0.0);
        
        assertNotNull(annotations);
        assertEquals(2, annotations.size());
    }

    @Test
    @DisplayName("Test all annotations have CLASSIFICATION type")
    void testAnnotationType() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("debris", 0.10));
        
        List<Annotation> annotations = ClassificationAnnotationConverter.toAnnotations(classifications);
        
        for (Annotation annotation : annotations) {
            assertEquals(AnnotationType.CLASSIFICATION, annotation.getAnnotationType());
        }
    }

    @Test
    @DisplayName("Test all annotations have timestamps")
    void testTimestamps() {
        List<Classification> classifications = new ArrayList<>();
        classifications.add(new Classification("rock", 0.85));
        classifications.add(new Classification("debris", 0.10));
        
        List<Annotation> annotations = ClassificationAnnotationConverter.toAnnotations(classifications);
        
        for (Annotation annotation : annotations) {
            assertNotNull(annotation.getTimestamp());
        }
    }
}
