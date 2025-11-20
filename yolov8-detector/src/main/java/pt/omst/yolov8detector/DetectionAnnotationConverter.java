//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8detector;

import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting Detection results to Contact Annotations.
 * This provides integration between the YoloV8 detector and the existing
 * Contact/Observation data structures.
 */
public class DetectionAnnotationConverter {
    
    private static final String SYSTEM_NAME = "yolov8-detector";
    
    /**
     * Converts a Detection to an Annotation with bounding box information.
     * 
     * @param detection The detection result
     * @param userName The name of the user or system that created this annotation
     * @return An Annotation object representing this detection
     */
    public static Annotation toAnnotation(Detection detection, String userName) {
        Annotation annotation = new Annotation();
        annotation.setAnnotationType(AnnotationType.LABEL);
        annotation.setCategory(detection.getLabel());
        annotation.setConfidence(detection.getConfidence());
        annotation.setUserName(userName != null ? userName : SYSTEM_NAME);
        annotation.setTimestamp(OffsetDateTime.now());
        
        // Set bounding box coordinates (normalized)
        annotation.setNormalizedX(detection.getX1());
        annotation.setNormalizedY(detection.getY1());
        annotation.setNormalizedX2(detection.getX2());
        annotation.setNormalizedY2(detection.getY2());
        
        return annotation;
    }
    
    /**
     * Converts a Detection to an Annotation with automatic system username.
     * 
     * @param detection The detection result
     * @return An Annotation object representing this detection
     */
    public static Annotation toAnnotation(Detection detection) {
        return toAnnotation(detection, SYSTEM_NAME);
    }
    
    /**
     * Converts a list of Detections to a list of Annotations.
     * 
     * @param detections List of detection results
     * @param userName The name of the user or system that created these annotations
     * @return List of Annotation objects
     */
    public static List<Annotation> toAnnotations(List<Detection> detections, String userName) {
        List<Annotation> annotations = new ArrayList<>();
        for (Detection detection : detections) {
            annotations.add(toAnnotation(detection, userName));
        }
        return annotations;
    }
    
    /**
     * Converts a list of Detections to a list of Annotations with automatic system username.
     * 
     * @param detections List of detection results
     * @return List of Annotation objects
     */
    public static List<Annotation> toAnnotations(List<Detection> detections) {
        return toAnnotations(detections, SYSTEM_NAME);
    }
    
    /**
     * Filters detections by minimum confidence and converts to annotations.
     * 
     * @param detections List of detection results
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     * @param userName The name of the user or system that created these annotations
     * @return List of Annotation objects for detections above the threshold
     */
    public static List<Annotation> toAnnotationsWithMinConfidence(
            List<Detection> detections, 
            double minConfidence,
            String userName) {
        List<Annotation> annotations = new ArrayList<>();
        for (Detection detection : detections) {
            if (detection.getConfidence() >= minConfidence) {
                annotations.add(toAnnotation(detection, userName));
            }
        }
        return annotations;
    }
    
    /**
     * Filters detections by minimum confidence and converts to annotations with automatic system username.
     * 
     * @param detections List of detection results
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     * @return List of Annotation objects for detections above the threshold
     */
    public static List<Annotation> toAnnotationsWithMinConfidence(
            List<Detection> detections, 
            double minConfidence) {
        return toAnnotationsWithMinConfidence(detections, minConfidence, SYSTEM_NAME);
    }
    
    /**
     * Filters detections by specific class label and converts to annotations.
     * 
     * @param detections List of detection results
     * @param label Class label to filter by
     * @param userName The name of the user or system that created these annotations
     * @return List of Annotation objects for detections matching the label
     */
    public static List<Annotation> toAnnotationsForLabel(
            List<Detection> detections,
            String label,
            String userName) {
        List<Annotation> annotations = new ArrayList<>();
        for (Detection detection : detections) {
            if (detection.getLabel().equals(label)) {
                annotations.add(toAnnotation(detection, userName));
            }
        }
        return annotations;
    }
    
    /**
     * Filters detections by specific class label and converts to annotations with automatic system username.
     * 
     * @param detections List of detection results
     * @param label Class label to filter by
     * @return List of Annotation objects for detections matching the label
     */
    public static List<Annotation> toAnnotationsForLabel(
            List<Detection> detections,
            String label) {
        return toAnnotationsForLabel(detections, label, SYSTEM_NAME);
    }
}
