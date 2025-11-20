//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8detector;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Detector for sidescan sonar ATR (Automatic Target Recognition) using YoloV8 models.
 * This class provides cross-platform deep learning inference using ONNX Runtime.
 */
@Slf4j
public class ContactDetector implements AutoCloseable {
    
    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;
    
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String[] classLabels;
    private final double confidenceThreshold;
    private final double iouThreshold;
    
    /**
     * Creates a ContactDetector from an ONNX model file and class labels.
     * 
     * @param modelPath Path to the ONNX model file
     * @param classLabels Array of class labels in the order expected by the model
     * @param confidenceThreshold Minimum confidence threshold for detections (0.0 to 1.0)
     * @param iouThreshold IoU threshold for Non-Maximum Suppression (0.0 to 1.0)
     * @throws OrtException if the model cannot be loaded
     * @throws IOException if the model file cannot be read
     */
    public ContactDetector(Path modelPath, String[] classLabels, double confidenceThreshold, double iouThreshold) 
            throws OrtException, IOException {
        this.classLabels = classLabels.clone();
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;
        this.environment = OrtEnvironment.getEnvironment();
        
        // Create session options
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        
        // Load the ONNX model
        byte[] modelBytes = Files.readAllBytes(modelPath);
        this.session = environment.createSession(modelBytes, options);
        
        log.info("Loaded YoloV8 detection model from {} with {} classes", modelPath, classLabels.length);
    }
    
    /**
     * Creates a ContactDetector with default thresholds.
     * 
     * @param modelPath Path to the ONNX model file
     * @param classLabels Array of class labels in the order expected by the model
     * @throws OrtException if the model cannot be loaded
     * @throws IOException if the model file cannot be read
     */
    public ContactDetector(Path modelPath, String[] classLabels) throws OrtException, IOException {
        this(modelPath, classLabels, 0.25, 0.45);
    }
    
    /**
     * Creates a ContactDetector from an ONNX model resource and class labels.
     * 
     * @param modelResourcePath Resource path to the ONNX model (e.g., "/models/yolov8-det.onnx")
     * @param classLabels Array of class labels in the order expected by the model
     * @param confidenceThreshold Minimum confidence threshold for detections (0.0 to 1.0)
     * @param iouThreshold IoU threshold for Non-Maximum Suppression (0.0 to 1.0)
     * @throws OrtException if the model cannot be loaded
     * @throws IOException if the model resource cannot be read
     */
    public ContactDetector(String modelResourcePath, String[] classLabels, double confidenceThreshold, double iouThreshold) 
            throws OrtException, IOException {
        this.classLabels = classLabels.clone();
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;
        this.environment = OrtEnvironment.getEnvironment();
        
        // Create session options
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        
        // Load the ONNX model from resources
        try (InputStream is = getClass().getResourceAsStream(modelResourcePath)) {
            if (is == null) {
                throw new IOException("Model resource not found: " + modelResourcePath);
            }
            byte[] modelBytes = is.readAllBytes();
            this.session = environment.createSession(modelBytes, options);
        }
        
        log.info("Loaded YoloV8 detection model from resource {} with {} classes", modelResourcePath, classLabels.length);
    }
    
    /**
     * Detects objects in a sidescan sonar image.
     * 
     * @param image The sonar image as BufferedImage
     * @return List of detections sorted by confidence (highest first)
     * @throws OrtException if inference fails
     */
    public List<Detection> detect(BufferedImage image) throws OrtException {
        // Preprocess the image
        float[][][][] input = preprocessImage(image);
        
        // Run inference
        float[][] rawOutput = runInference(input);
        
        // Parse detections
        List<Detection> detections = parseDetections(rawOutput);
        
        // Apply Non-Maximum Suppression
        detections = applyNMS(detections, iouThreshold);
        
        // Sort by confidence (highest first)
        Collections.sort(detections);
        
        return detections;
    }
    
    /**
     * Preprocesses an image for YoloV8 detection inference.
     * Resizes to 640x640, normalizes to [0,1], and converts to NCHW format.
     * 
     * @param image Input image
     * @return Preprocessed image data in NCHW format (batch, channels, height, width)
     */
    private float[][][][] preprocessImage(BufferedImage image) {
        // Create scaled image
        BufferedImage scaledImage = new BufferedImage(INPUT_WIDTH, INPUT_HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = scaledImage.createGraphics();
        g.drawImage(image, 0, 0, INPUT_WIDTH, INPUT_HEIGHT, null);
        g.dispose();
        
        // Convert to float array in NCHW format
        float[][][][] input = new float[1][3][INPUT_HEIGHT][INPUT_WIDTH];
        
        for (int y = 0; y < INPUT_HEIGHT; y++) {
            for (int x = 0; x < INPUT_WIDTH; x++) {
                int rgb = scaledImage.getRGB(x, y);
                
                // Extract RGB components
                int r = (rgb >> 16) & 0xFF;
                int g_val = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Normalize to [0, 1] and store in CHW format
                input[0][0][y][x] = r / 255.0f;
                input[0][1][y][x] = g_val / 255.0f;
                input[0][2][y][x] = b / 255.0f;
            }
        }
        
        return input;
    }
    
    /**
     * Runs ONNX inference on preprocessed image data.
     * 
     * @param input Preprocessed image in NCHW format
     * @return Raw detection output (flattened array)
     * @throws OrtException if inference fails
     */
    private float[][] runInference(float[][][][] input) throws OrtException {
        // Create ONNX tensor from input
        long[] shape = {1, 3, INPUT_HEIGHT, INPUT_WIDTH};
        
        // Flatten the 4D array to FloatBuffer for ONNX
        java.nio.FloatBuffer buffer = java.nio.FloatBuffer.allocate(1 * 3 * INPUT_HEIGHT * INPUT_WIDTH);
        for (int b = 0; b < 1; b++) {
            for (int c = 0; c < 3; c++) {
                for (int h = 0; h < INPUT_HEIGHT; h++) {
                    for (int w = 0; w < INPUT_WIDTH; w++) {
                        buffer.put(input[b][c][h][w]);
                    }
                }
            }
        }
        buffer.rewind();
        
        OnnxTensor inputTensor = OnnxTensor.createTensor(environment, buffer, shape);
        
        // Get input name from model
        String inputName = session.getInputNames().iterator().next();
        
        // Run inference
        try (OrtSession.Result results = session.run(Map.of(inputName, inputTensor))) {
            // Get output tensor
            OnnxValue outputValue = results.get(0);
            
            if (outputValue instanceof OnnxTensor) {
                OnnxTensor outputTensor = (OnnxTensor) outputValue;
                
                // YoloV8 output format: [batch, num_predictions, num_classes + 4]
                // where first 4 values are box coordinates (x, y, w, h)
                float[][][] output3D = (float[][][]) outputTensor.getValue();
                
                // For YoloV8, output might be transposed: [1, 4+num_classes, num_predictions]
                // We need to transpose it to [1, num_predictions, 4+num_classes]
                return transposeOutput(output3D);
            } else {
                throw new OrtException("Unexpected output type: " + outputValue.getClass().getName());
            }
        } finally {
            inputTensor.close();
        }
    }
    
    /**
     * Transposes YoloV8 output from [1, 4+num_classes, num_predictions] 
     * to [num_predictions, 4+num_classes]
     */
    private float[][] transposeOutput(float[][][] output) {
        int numPredictions = output[0][0].length;
        int numFeatures = output[0].length;
        
        float[][] transposed = new float[numPredictions][numFeatures];
        for (int i = 0; i < numPredictions; i++) {
            for (int j = 0; j < numFeatures; j++) {
                transposed[i][j] = output[0][j][i];
            }
        }
        return transposed;
    }
    
    /**
     * Parses raw detection output into Detection objects.
     * 
     * @param output Raw detection output [num_predictions, 4+num_classes]
     * @return List of detections above confidence threshold
     */
    private List<Detection> parseDetections(float[][] output) {
        List<Detection> detections = new ArrayList<>();
        
        for (float[] prediction : output) {
            // First 4 values are box coordinates (x, y, w, h) - normalized to image size
            double x = prediction[0] / INPUT_WIDTH;
            double y = prediction[1] / INPUT_HEIGHT;
            double w = prediction[2] / INPUT_WIDTH;
            double h = prediction[3] / INPUT_HEIGHT;
            
            // Remaining values are class probabilities
            int bestClassIdx = -1;
            double maxConfidence = 0.0;
            
            for (int i = 4; i < prediction.length; i++) {
                if (prediction[i] > maxConfidence) {
                    maxConfidence = prediction[i];
                    bestClassIdx = i - 4;
                }
            }
            
            // Only keep detections above threshold
            if (maxConfidence >= confidenceThreshold && bestClassIdx >= 0 && bestClassIdx < classLabels.length) {
                Detection detection = new Detection(
                    classLabels[bestClassIdx],
                    maxConfidence,
                    x, y, w, h
                );
                detections.add(detection);
            }
        }
        
        return detections;
    }
    
    /**
     * Applies Non-Maximum Suppression to remove overlapping detections.
     * 
     * @param detections List of detections
     * @param iouThreshold IoU threshold for suppression
     * @return Filtered list of detections
     */
    private List<Detection> applyNMS(List<Detection> detections, double iouThreshold) {
        if (detections.isEmpty()) {
            return detections;
        }
        
        // Sort by confidence (highest first)
        List<Detection> sorted = new ArrayList<>(detections);
        Collections.sort(sorted);
        
        List<Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[sorted.size()];
        
        for (int i = 0; i < sorted.size(); i++) {
            if (suppressed[i]) {
                continue;
            }
            
            Detection det1 = sorted.get(i);
            result.add(det1);
            
            // Suppress overlapping detections
            for (int j = i + 1; j < sorted.size(); j++) {
                if (suppressed[j]) {
                    continue;
                }
                
                Detection det2 = sorted.get(j);
                
                // Only suppress if same class
                if (det1.getLabel().equals(det2.getLabel())) {
                    double iou = calculateIoU(det1, det2);
                    if (iou > iouThreshold) {
                        suppressed[j] = true;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Calculates Intersection over Union (IoU) between two detections.
     * 
     * @param det1 First detection
     * @param det2 Second detection
     * @return IoU value (0.0 to 1.0)
     */
    private double calculateIoU(Detection det1, Detection det2) {
        double x1_min = det1.getX1();
        double y1_min = det1.getY1();
        double x1_max = det1.getX2();
        double y1_max = det1.getY2();
        
        double x2_min = det2.getX1();
        double y2_min = det2.getY1();
        double x2_max = det2.getX2();
        double y2_max = det2.getY2();
        
        // Calculate intersection
        double intersect_x_min = Math.max(x1_min, x2_min);
        double intersect_y_min = Math.max(y1_min, y2_min);
        double intersect_x_max = Math.min(x1_max, x2_max);
        double intersect_y_max = Math.min(y1_max, y2_max);
        
        double intersect_width = Math.max(0, intersect_x_max - intersect_x_min);
        double intersect_height = Math.max(0, intersect_y_max - intersect_y_min);
        double intersect_area = intersect_width * intersect_height;
        
        // Calculate union
        double area1 = det1.getWidth() * det1.getHeight();
        double area2 = det2.getWidth() * det2.getHeight();
        double union_area = area1 + area2 - intersect_area;
        
        if (union_area == 0) {
            return 0;
        }
        
        return intersect_area / union_area;
    }
    
    /**
     * Gets the class labels used by this detector.
     * 
     * @return Array of class labels
     */
    public String[] getClassLabels() {
        return classLabels.clone();
    }
    
    /**
     * Gets the number of classes this detector can recognize.
     * 
     * @return Number of classes
     */
    public int getNumClasses() {
        return classLabels.length;
    }
    
    /**
     * Gets the confidence threshold used by this detector.
     * 
     * @return Confidence threshold
     */
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
    
    /**
     * Gets the IoU threshold used for NMS.
     * 
     * @return IoU threshold
     */
    public double getIouThreshold() {
        return iouThreshold;
    }
    
    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException e) {
            log.error("Error closing ONNX session", e);
        }
    }
}
