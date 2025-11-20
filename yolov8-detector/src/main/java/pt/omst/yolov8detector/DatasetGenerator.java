//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.yolov8detector;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterlib.*;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates YOLOv8 training datasets from rasterindex data and contacts.
 * Creates image patches with corresponding YOLO format annotation files.
 */
@Slf4j
public class DatasetGenerator {
    
    private final Path outputDir;
    private final int patchWidth;
    private final int patchHeight;
    private final String[] classLabels;
    private final Map<String, Integer> classToIndex;
    
    /**
     * Creates a dataset generator.
     * 
     * @param outputDir Directory to save the generated dataset
     * @param patchWidth Width of image patches
     * @param patchHeight Height of image patches
     * @param classLabels Array of class labels (determines class indices)
     */
    public DatasetGenerator(Path outputDir, int patchWidth, int patchHeight, String[] classLabels) {
        this.outputDir = outputDir;
        this.patchWidth = patchWidth;
        this.patchHeight = patchHeight;
        this.classLabels = classLabels.clone();
        
        // Create class label to index mapping
        this.classToIndex = new HashMap<>();
        for (int i = 0; i < classLabels.length; i++) {
            classToIndex.put(classLabels[i], i);
        }
    }
    
    /**
     * Generates a dataset from a raster index and contact collection.
     * 
     * @param rasterIndex The raster index containing sonar data
     * @param contacts The contact collection with annotations
     * @param split Dataset split ("train", "val", or "test")
     * @return Number of patches generated
     * @throws IOException if file operations fail
     */
    public int generateDataset(IndexedRaster rasterIndex, ContactCollection contacts, String split) 
            throws IOException {
        Path splitDir = outputDir.resolve(split);
        Path imagesDir = splitDir.resolve("images");
        Path labelsDir = splitDir.resolve("labels");
        
        // Create directories
        Files.createDirectories(imagesDir);
        Files.createDirectories(labelsDir);
        
        log.info("Generating {} dataset in {}", split, splitDir);
        
        int patchCount = 0;
        
        // For each contact, generate a patch
        for (CompressedContact compressedContact : contacts.getAllContacts()) {
            Contact contact = compressedContact.getContact();
            if (contact.getLatitude() == null || contact.getLongitude() == null) {
                log.warn("Skipping contact with missing coordinates: {}", contact.getUuid());
                continue;
            }
            
            // Generate patch centered on contact
            try {
                BufferedImage patch = extractPatch(rasterIndex, contact);
                if (patch == null) {
                    continue;
                }
                
                // Generate YOLO format annotations
                List<String> annotations = generateAnnotations(contact, rasterIndex);
                if (annotations.isEmpty()) {
                    log.debug("No valid annotations for contact {}", contact.getUuid());
                    // You may still want to save patches without annotations for background
                }
                
                // Save image and annotations
                String baseName = String.format("%s_%s", split, contact.getUuid().toString());
                savePatch(imagesDir, labelsDir, baseName, patch, annotations);
                patchCount++;
                
                if (patchCount % 100 == 0) {
                    log.info("Generated {} patches", patchCount);
                }
                
            } catch (Exception e) {
                log.error("Error generating patch for contact {}: {}", contact.getUuid(), e.getMessage());
            }
        }
        
        log.info("Generated {} patches for {} split", patchCount, split);
        return patchCount;
    }
    
    /**
     * Extracts a patch from the raster index centered on a contact.
     * 
     * @param rasterIndex The raster index
     * @param contact The contact to extract
     * @return Extracted patch image, or null if not available
     */
    private BufferedImage extractPatch(IndexedRaster rasterIndex, Contact contact) {
        // This is a simplified implementation
        // In a real scenario, you would use the raster index to extract the actual sonar data
        
        // For now, create a placeholder implementation
        // You would typically:
        // 1. Convert lat/lon to raster coordinates
        // 2. Extract a patch of the specified size
        // 3. Return the patch as BufferedImage
        
        log.debug("Extracting patch for contact at {}, {}", contact.getLatitude(), contact.getLongitude());
        
        // Placeholder: return null to skip contacts we can't extract
        // In real implementation, this would extract from the raster
        return null;
    }
    
    /**
     * Generates YOLO format annotations for a contact.
     * Format: <class_id> <x_center> <y_center> <width> <height> (all normalized to [0,1])
     * 
     * @param contact The contact with observations
     * @param rasterIndex The raster index (for coordinate conversion)
     * @return List of YOLO format annotation strings
     */
    private List<String> generateAnnotations(Contact contact, IndexedRaster rasterIndex) {
        List<String> annotations = new ArrayList<>();
        
        if (contact.getObservations() == null) {
            return annotations;
        }
        
        for (Observation obs : contact.getObservations()) {
            if (obs.getAnnotations() == null) {
                continue;
            }
            
            for (Annotation ann : obs.getAnnotations()) {
                // Skip annotations without bounding boxes
                if (ann.getNormalizedX() == null || ann.getNormalizedY() == null ||
                    ann.getNormalizedX2() == null || ann.getNormalizedY2() == null) {
                    continue;
                }
                
                // Get class index
                String category = ann.getCategory();
                if (category == null || !classToIndex.containsKey(category)) {
                    log.debug("Skipping annotation with unknown category: {}", category);
                    continue;
                }
                
                int classId = classToIndex.get(category);
                
                // Convert from corner coordinates to center + size
                double x1 = ann.getNormalizedX();
                double y1 = ann.getNormalizedY();
                double x2 = ann.getNormalizedX2();
                double y2 = ann.getNormalizedY2();
                
                double xCenter = (x1 + x2) / 2.0;
                double yCenter = (y1 + y2) / 2.0;
                double width = Math.abs(x2 - x1);
                double height = Math.abs(y2 - y1);
                
                // Format: class_id x_center y_center width height
                String yoloAnnotation = String.format("%d %.6f %.6f %.6f %.6f",
                    classId, xCenter, yCenter, width, height);
                annotations.add(yoloAnnotation);
            }
        }
        
        return annotations;
    }
    
    /**
     * Saves a patch and its annotations.
     * 
     * @param imagesDir Directory for images
     * @param labelsDir Directory for labels
     * @param baseName Base name for files (without extension)
     * @param patch The image patch
     * @param annotations List of YOLO format annotations
     * @throws IOException if save fails
     */
    private void savePatch(Path imagesDir, Path labelsDir, String baseName, 
                          BufferedImage patch, List<String> annotations) throws IOException {
        // Save image
        Path imagePath = imagesDir.resolve(baseName + ".jpg");
        ImageIO.write(patch, "jpg", imagePath.toFile());
        
        // Save annotations
        Path labelPath = labelsDir.resolve(baseName + ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(labelPath)) {
            for (String annotation : annotations) {
                writer.write(annotation);
                writer.newLine();
            }
        }
    }
    
    /**
     * Generates a dataset.yaml file for YOLOv8 training.
     * 
     * @param datasetName Name of the dataset
     * @throws IOException if write fails
     */
    public void generateDatasetYaml(String datasetName) throws IOException {
        Path yamlPath = outputDir.resolve("dataset.yaml");
        
        try (BufferedWriter writer = Files.newBufferedWriter(yamlPath)) {
            writer.write("# YOLOv8 Dataset Configuration\n");
            writer.write("# Generated by OMST PMA Tools\n");
            writer.write("\n");
            writer.write("path: " + outputDir.toAbsolutePath() + "\n");
            writer.write("train: train/images\n");
            writer.write("val: val/images\n");
            writer.write("test: test/images\n");
            writer.write("\n");
            writer.write("# Classes\n");
            writer.write("nc: " + classLabels.length + "\n");
            writer.write("names:\n");
            for (int i = 0; i < classLabels.length; i++) {
                writer.write("  " + i + ": " + classLabels[i] + "\n");
            }
        }
        
        log.info("Generated dataset.yaml at {}", yamlPath);
    }
    
    /**
     * Command-line interface for dataset generation.
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: DatasetGenerator rasterindex-path contacts-path output-dir class-labels...");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  java -cp yolov8-detector.jar pt.omst.yolov8detector.DatasetGenerator \\");
            System.err.println("    /path/to/rasterIndex.json /path/to/contacts.zct \\");
            System.err.println("    /path/to/dataset rock debris mine wreck");
            System.exit(1);
        }
        
        String rasterIndexPath = args[0];
        String contactsPath = args[1];
        String outputDir = args[2];
        
        String[] classLabels = new String[args.length - 3];
        System.arraycopy(args, 3, classLabels, 0, args.length - 3);
        
        log.info("Raster index: {}", rasterIndexPath);
        log.info("Contacts: {}", contactsPath);
        log.info("Output directory: {}", outputDir);
        log.info("Class labels: {}", String.join(", ", classLabels));
        
        try {
            // Load data
            log.info("Loading raster index...");
            String rasterJson = Files.readString(Paths.get(rasterIndexPath));
            IndexedRaster rasterIndex = Converter.IndexedRasterFromJsonString(rasterJson);
            
            log.info("Loading contacts...");
            ContactCollection contacts = ContactCollection.fromFolder(new File(contactsPath));
            
            // Create generator
            DatasetGenerator generator = new DatasetGenerator(
                Paths.get(outputDir), 640, 640, classLabels
            );
            
            // Generate splits
            // In a real scenario, you would split contacts into train/val/test
            // For now, put all in train
            log.info("Generating training dataset...");
            int trainCount = generator.generateDataset(rasterIndex, contacts, "train");
            
            // Generate dataset.yaml
            generator.generateDatasetYaml("sidescan-atr");
            
            log.info("Dataset generation complete. Generated {} training patches.", trainCount);
            
        } catch (Exception e) {
            log.error("Error generating dataset", e);
            System.exit(1);
        }
    }
}
