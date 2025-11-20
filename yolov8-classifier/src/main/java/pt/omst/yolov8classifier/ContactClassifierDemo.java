//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8classifier;

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
 * Example/demo application showing how to use the ContactClassifier.
 * 
 * Usage:
 *   java -cp yolov8-classifier.jar pt.omst.yolov8classifier.ContactClassifierDemo
 *     model-path image-path [class-labels...]
 * 
 * Example:
 *   java -cp yolov8-classifier.jar pt.omst.yolov8classifier.ContactClassifierDemo
 *     /path/to/model.onnx /path/to/sonar-contact.png rock debris sand mine wreck unknown
 */
@Slf4j
public class ContactClassifierDemo {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: ContactClassifierDemo model-path image-path class-label-1 [class-label-2 ...]");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  java -cp yolov8-classifier.jar pt.omst.yolov8classifier.ContactClassifierDemo \\");
            System.err.println("    model.onnx contact.png rock debris sand mine wreck unknown");
            System.exit(1);
        }
        
        String modelPath = args[0];
        String imagePath = args[1];
        
        // Extract class labels from remaining arguments
        String[] classLabels = new String[args.length - 2];
        System.arraycopy(args, 2, classLabels, 0, args.length - 2);
        
        log.info("Loading YoloV8-cls model from: {}", modelPath);
        log.info("Processing image: {}", imagePath);
        log.info("Class labels: {}", String.join(", ", classLabels));
        
        try {
            // Load the model
            Path modelFilePath = Paths.get(modelPath);
            try (ContactClassifier classifier = new ContactClassifier(modelFilePath, classLabels)) {
                log.info("Model loaded successfully with {} classes", classifier.getNumClasses());
                
                // Load the image
                BufferedImage image = loadImage(imagePath);
                log.info("Image loaded: {}x{} pixels", image.getWidth(), image.getHeight());
                
                // Classify the image
                log.info("Running classification...");
                long startTime = System.currentTimeMillis();
                List<Classification> results = classifier.classify(image);
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                // Display results
                System.out.println();
                System.out.println("=== Classification Results ===");
                System.out.println("Inference time: " + elapsedTime + " ms");
                System.out.println();
                
                for (int i = 0; i < results.size(); i++) {
                    Classification c = results.get(i);
                    System.out.printf("%2d. %s\n", i + 1, c.toString());
                }
                
                System.out.println();
                System.out.println("Top prediction: " + results.get(0).toString());
                
            } catch (Exception e) {
                log.error("Error during classification", e);
                System.exit(1);
            }
            
        } catch (Exception e) {
            log.error("Error initializing classifier", e);
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
            return createTestImage(224, 224);
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
