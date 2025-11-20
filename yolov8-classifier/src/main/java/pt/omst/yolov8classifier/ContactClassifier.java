//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8classifier;

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
 * Classifier for sonar contact snippets using YoloV8-cls models.
 * This class provides cross-platform deep learning inference using ONNX Runtime.
 */
@Slf4j
public class ContactClassifier implements AutoCloseable {
    
    private static final int INPUT_WIDTH = 224;
    private static final int INPUT_HEIGHT = 224;
    
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String[] classLabels;
    
    /**
     * Creates a ContactClassifier from an ONNX model file and class labels.
     * 
     * @param modelPath Path to the ONNX model file
     * @param classLabels Array of class labels in the order expected by the model
     * @throws OrtException if the model cannot be loaded
     * @throws IOException if the model file cannot be read
     */
    public ContactClassifier(Path modelPath, String[] classLabels) throws OrtException, IOException {
        this.classLabels = classLabels.clone();
        this.environment = OrtEnvironment.getEnvironment();
        
        // Create session options
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        
        // Load the ONNX model
        byte[] modelBytes = Files.readAllBytes(modelPath);
        this.session = environment.createSession(modelBytes, options);
        
        log.info("Loaded YoloV8-cls model from {} with {} classes", modelPath, classLabels.length);
    }
    
    /**
     * Creates a ContactClassifier from an ONNX model resource and class labels.
     * 
     * @param modelResourcePath Resource path to the ONNX model (e.g., "/models/yolov8-cls.onnx")
     * @param classLabels Array of class labels in the order expected by the model
     * @throws OrtException if the model cannot be loaded
     * @throws IOException if the model resource cannot be read
     */
    public ContactClassifier(String modelResourcePath, String[] classLabels) throws OrtException, IOException {
        this.classLabels = classLabels.clone();
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
        
        log.info("Loaded YoloV8-cls model from resource {} with {} classes", modelResourcePath, classLabels.length);
    }
    
    /**
     * Classifies a sonar contact image.
     * 
     * @param image The sonar contact snippet as BufferedImage
     * @return List of classifications sorted by confidence (highest first)
     * @throws OrtException if inference fails
     */
    public List<Classification> classify(BufferedImage image) throws OrtException {
        return classify(image, classLabels.length);
    }
    
    /**
     * Classifies a sonar contact image and returns top N results.
     * 
     * @param image The sonar contact snippet as BufferedImage
     * @param topN Number of top classifications to return
     * @return List of top N classifications sorted by confidence (highest first)
     * @throws OrtException if inference fails
     */
    public List<Classification> classify(BufferedImage image, int topN) throws OrtException {
        // Preprocess the image
        float[][][][] input = preprocessImage(image);
        
        // Run inference
        float[] probabilities = runInference(input);
        
        // Convert to Classification objects
        List<Classification> results = new ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            results.add(new Classification(classLabels[i], probabilities[i]));
        }
        
        // Sort by confidence (highest first)
        Collections.sort(results);
        
        // Return top N results
        if (topN < results.size()) {
            return results.subList(0, topN);
        }
        return results;
    }
    
    /**
     * Preprocesses an image for YoloV8-cls inference.
     * Resizes to 224x224, normalizes to [0,1], and converts to NCHW format.
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
     * @return Array of classification probabilities
     * @throws OrtException if inference fails
     */
    private float[] runInference(float[][][][] input) throws OrtException {
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
                
                // Get probabilities (assuming softmax is already applied in the model)
                float[][] outputArray = (float[][]) outputTensor.getValue();
                return outputArray[0];
            } else {
                throw new OrtException("Unexpected output type: " + outputValue.getClass().getName());
            }
        } finally {
            inputTensor.close();
        }
    }
    
    /**
     * Gets the class labels used by this classifier.
     * 
     * @return Array of class labels
     */
    public String[] getClassLabels() {
        return classLabels.clone();
    }
    
    /**
     * Gets the number of classes this classifier can recognize.
     * 
     * @return Number of classes
     */
    public int getNumClasses() {
        return classLabels.length;
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
