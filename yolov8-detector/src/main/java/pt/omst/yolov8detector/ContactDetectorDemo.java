//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8detector;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Example/demo application showing how to use the ContactDetector.
 * 
 * Usage:
 *   java -cp yolov8-detector.jar pt.omst.yolov8detector.ContactDetectorDemo
 *     model-path image-path [class-labels...]
 * 
 * Example:
 *   java -cp yolov8-detector.jar pt.omst.yolov8detector.ContactDetectorDemo
 *     /path/to/model.onnx /path/to/sidescan.png rock debris mine wreck
 */
@Slf4j
public class ContactDetectorDemo {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: ContactDetectorDemo model-path image-path class-label-1 [class-label-2 ...]");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  java -cp yolov8-detector.jar pt.omst.yolov8detector.ContactDetectorDemo \\");
            System.err.println("    model.onnx sidescan.png rock debris mine wreck");
            System.exit(1);
        }
        
        String modelPath = args[0];
        String imagePath = args[1];
        
        // Extract class labels from remaining arguments
        String[] classLabels = new String[args.length - 2];
        System.arraycopy(args, 2, classLabels, 0, args.length - 2);
        
        log.info("Loading YoloV8 detection model from: {}", modelPath);
        log.info("Processing image: {}", imagePath);
        log.info("Class labels: {}", String.join(", ", classLabels));
        
        try {
            // Load the model
            Path modelFilePath = Paths.get(modelPath);
            try (ContactDetector detector = new ContactDetector(modelFilePath, classLabels)) {
                log.info("Model loaded successfully with {} classes", detector.getNumClasses());
                log.info("Confidence threshold: {}", detector.getConfidenceThreshold());
                log.info("IoU threshold: {}", detector.getIouThreshold());
                
                // Load the image
                BufferedImage image = loadImage(imagePath);
                log.info("Image loaded: {}x{} pixels", image.getWidth(), image.getHeight());
                
                // Detect objects
                log.info("Running detection...");
                long startTime = System.currentTimeMillis();
                List<Detection> detections = detector.detect(image);
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                // Display results
                System.out.println();
                System.out.println("=== Detection Results ===");
                System.out.println("Inference time: " + elapsedTime + " ms");
                System.out.println("Detections found: " + detections.size());
                System.out.println();
                
                if (detections.isEmpty()) {
                    System.out.println("No objects detected above confidence threshold.");
                } else {
                    for (int i = 0; i < detections.size(); i++) {
                        Detection d = detections.get(i);
                        System.out.printf("%2d. %s\n", i + 1, d.toString());
                    }
                }
                
            } catch (Exception e) {
                log.error("Error during detection", e);
                System.exit(1);
            }
            
        } catch (Exception e) {
            log.error("Error initializing detector", e);
            System.exit(1);
        }
    }
    
    /**
     * Loads an image from file. If the file doesn't exist, creates a test image.
     */
    private static BufferedImage loadImage(String path) throws Exception {
        File file = new File(path);
        
        if (file.exists()) {
            return ImageIO.read(file);
        } else {
            log.warn("Image file not found: {}, creating test image", path);
            return createTestImage(640, 640);
        }
    }
    
    /**
     * Creates a test image with a simple pattern.
     */
    private static BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // Create a gradient background
        for (int y = 0; y < height; y++) {
            float intensity = (float) y / height;
            g.setColor(new Color(intensity, intensity, intensity));
            g.drawLine(0, y, width, y);
        }
        
        // Draw a simple shape
        g.setColor(Color.WHITE);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        
        g.dispose();
        return image;
    }
}
