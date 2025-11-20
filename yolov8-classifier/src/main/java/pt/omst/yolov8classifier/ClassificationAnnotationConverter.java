//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8classifier;

import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting Classification results to Contact Annotations.
 * This provides integration between the YoloV8 classifier and the existing
 * Contact/Observation data structures.
 */
public class ClassificationAnnotationConverter {
    
    private static final String SYSTEM_NAME = "yolov8-classifier";
    
    /**
     * Converts a Classification to an Annotation.
     * 
     * @param classification The classification result
     * @param userName The name of the user or system that created this annotation
     * @return An Annotation object representing this classification
     */
    public static Annotation toAnnotation(Classification classification, String userName) {
        Annotation annotation = new Annotation();
        annotation.setAnnotationType(AnnotationType.CLASSIFICATION);
        annotation.setCategory(classification.getLabel());
        annotation.setConfidence(classification.getConfidence());
        annotation.setUserName(userName != null ? userName : SYSTEM_NAME);
        annotation.setTimestamp(OffsetDateTime.now());
        return annotation;
    }
    
    /**
     * Converts a Classification to an Annotation with automatic system username.
     * 
     * @param classification The classification result
     * @return An Annotation object representing this classification
     */
    public static Annotation toAnnotation(Classification classification) {
        return toAnnotation(classification, SYSTEM_NAME);
    }
    
    /**
     * Converts a list of Classifications to a list of Annotations.
     * 
     * @param classifications List of classification results
     * @param userName The name of the user or system that created these annotations
     * @return List of Annotation objects
     */
    public static List<Annotation> toAnnotations(List<Classification> classifications, String userName) {
        List<Annotation> annotations = new ArrayList<>();
        for (Classification classification : classifications) {
            annotations.add(toAnnotation(classification, userName));
        }
        return annotations;
    }
    
    /**
     * Converts a list of Classifications to a list of Annotations with automatic system username.
     * 
     * @param classifications List of classification results
     * @return List of Annotation objects
     */
    public static List<Annotation> toAnnotations(List<Classification> classifications) {
        return toAnnotations(classifications, SYSTEM_NAME);
    }
    
    /**
     * Creates an Annotation for the top classification result.
     * 
     * @param classifications List of classification results (should be sorted by confidence)
     * @param userName The name of the user or system that created this annotation
     * @return An Annotation for the highest confidence classification, or null if list is empty
     */
    public static Annotation topClassificationToAnnotation(List<Classification> classifications, String userName) {
        if (classifications == null || classifications.isEmpty()) {
            return null;
        }
        return toAnnotation(classifications.get(0), userName);
    }
    
    /**
     * Creates an Annotation for the top classification result with automatic system username.
     * 
     * @param classifications List of classification results (should be sorted by confidence)
     * @return An Annotation for the highest confidence classification, or null if list is empty
     */
    public static Annotation topClassificationToAnnotation(List<Classification> classifications) {
        return topClassificationToAnnotation(classifications, SYSTEM_NAME);
    }
    
    /**
     * Filters classifications by minimum confidence and converts to annotations.
     * 
     * @param classifications List of classification results
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     * @param userName The name of the user or system that created these annotations
     * @return List of Annotation objects for classifications above the threshold
     */
    public static List<Annotation> toAnnotationsWithMinConfidence(
            List<Classification> classifications, 
            double minConfidence,
            String userName) {
        List<Annotation> annotations = new ArrayList<>();
        for (Classification classification : classifications) {
            if (classification.getConfidence() >= minConfidence) {
                annotations.add(toAnnotation(classification, userName));
            }
        }
        return annotations;
    }
    
    /**
     * Filters classifications by minimum confidence and converts to annotations with automatic system username.
     * 
     * @param classifications List of classification results
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     * @return List of Annotation objects for classifications above the threshold
     */
    public static List<Annotation> toAnnotationsWithMinConfidence(
            List<Classification> classifications, 
            double minConfidence) {
        return toAnnotationsWithMinConfidence(classifications, minConfidence, SYSTEM_NAME);
    }
}
