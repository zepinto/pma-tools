# YoloV8 Detector

A cross-platform library for detecting objects in sidescan sonar images using YoloV8 detection models for Automatic Target Recognition (ATR).

## Overview

This library provides a simple Java API for running YoloV8 detection models on sidescan sonar images. It uses ONNX Runtime for cross-platform deep learning inference, making it compatible with Linux, macOS, and Windows.

## Features

- **Cross-platform**: Works on Linux, Mac, and Windows
- **Simple API**: Just pass a `BufferedImage` and get detection results with bounding boxes
- **ONNX Runtime**: Efficient inference using Microsoft's ONNX Runtime
- **Non-Maximum Suppression**: Built-in NMS to filter overlapping detections
- **Resource loading**: Load models from files or embedded resources
- **Auto-closeable**: Implements `AutoCloseable` for proper resource management
- **Dataset Generation**: Tools to generate YOLO format training datasets from rasterindex and contacts

## Usage

### Basic Detection

```java
import pt.omst.yolov8detector.ContactDetector;
import pt.omst.yolov8detector.Detection;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

// Define your class labels (must match the order expected by your model)
String[] classLabels = {"rock", "debris", "mine", "wreck"};

// Load the detector (auto-closeable)
try (ContactDetector detector = new ContactDetector(
        Path.of("/path/to/model.onnx"), classLabels)) {
    
    // Load your sonar image
    BufferedImage sonarImage = ImageIO.read(new File("sidescan.png"));
    
    // Detect objects
    List<Detection> detections = detector.detect(sonarImage);
    
    // Print results
    for (Detection d : detections) {
        System.out.println(d); // Prints: "rock (85.23%) [x=0.500, y=0.300, w=0.100, h=0.150]"
        
        // Access bounding box coordinates (normalized to [0,1])
        System.out.printf("Box: [%.3f, %.3f] to [%.3f, %.3f]\n",
            d.getX1(), d.getY1(), d.getX2(), d.getY2());
    }
}
```

### Custom Confidence and IoU Thresholds

```java
// Set custom confidence and IoU thresholds
double confidenceThreshold = 0.3; // Minimum confidence (0.0 to 1.0)
double iouThreshold = 0.5;        // NMS IoU threshold (0.0 to 1.0)

try (ContactDetector detector = new ContactDetector(
        Path.of("/path/to/model.onnx"), 
        classLabels,
        confidenceThreshold,
        iouThreshold)) {
    
    List<Detection> detections = detector.detect(sonarImage);
}
```

### Loading from Resources

```java
// Load model from JAR resources
String[] labels = {"rock", "debris", "mine", "wreck"};
try (ContactDetector detector = new ContactDetector(
        "/models/yolov8-det.onnx", labels, 0.25, 0.45)) {
    // ... use detector
}
```

## Model Requirements

### Input Format
- **Size**: 640x640 pixels (automatically resized)
- **Format**: RGB image
- **Normalization**: Pixel values normalized to [0, 1]
- **Layout**: NCHW (batch, channels, height, width)

### Output Format
- **Type**: Float array of detections
- **Format**: [batch, num_predictions, 4+num_classes]
  - First 4 values: bounding box (x, y, w, h) in pixels
  - Remaining values: class probabilities
- **Coordinates**: Center-based (x, y, width, height)

### Creating a YoloV8 Detection ONNX Model

To train and export a YoloV8 detection model:

```python
from ultralytics import YOLO

# Train a detection model
model = YOLO('yolov8n.pt')  # or yolov8s, yolov8m, yolov8l, yolov8x
model.train(data='dataset.yaml', epochs=100, imgsz=640)

# Export to ONNX
model.export(format='onnx')
```

The dataset should be organized in YOLO format:
```
dataset/
  train/
    images/
      img1.jpg
      img2.jpg
    labels/
      img1.txt  # Format: class_id x_center y_center width height
      img2.txt
  val/
    images/
    labels/
  test/
    images/
    labels/
  dataset.yaml
```

## Dataset Generation

Generate YOLO format training datasets from rasterindex and contacts:

```java
import pt.omst.yolov8detector.DatasetGenerator;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.contacts.ContactCollection;

// Define class labels
String[] classLabels = {"rock", "debris", "mine", "wreck"};

// Create generator
DatasetGenerator generator = new DatasetGenerator(
    Path.of("/path/to/output/dataset"),
    640,  // patch width
    640,  // patch height
    classLabels
);

// Load data
IndexedRaster rasterIndex = Converter.IndexedRasterFromJsonFile(
    new File("/path/to/rasterIndex.json"));
ContactCollection contacts = ContactCollection.loadFromFile(
    new File("/path/to/contacts.zct"));

// Generate dataset splits
generator.generateDataset(rasterIndex, trainContacts, "train");
generator.generateDataset(rasterIndex, valContacts, "val");
generator.generateDataset(rasterIndex, testContacts, "test");

// Generate dataset.yaml for training
generator.generateDatasetYaml("sidescan-atr");
```

### Command-line Dataset Generation

```bash
java -cp yolov8-detector.jar pt.omst.yolov8detector.DatasetGenerator \
  /path/to/rasterIndex.json \
  /path/to/contacts.zct \
  /path/to/output/dataset \
  rock debris mine wreck
```

## Dependencies

- **ONNX Runtime**: 1.19.2 (automatically included)
- **SLF4J**: Logging API
- **Lombok**: Code generation
- **rasterlib**: OMST raster library for sonar data
- **neptus-utils**: OMST utilities

## Running the Demo

```bash
# Build the module
./gradlew :yolov8-detector:build

# Run the demo
java -cp yolov8-detector/build/libs/yolov8-detector-2025.11.00.jar \
  pt.omst.yolov8detector.ContactDetectorDemo \
  /path/to/model.onnx \
  /path/to/image.png \
  rock debris mine wreck
```

## Integration with Other Modules

To use this library in other PMA Tools modules, add it as a dependency:

```gradle
dependencies {
    implementation project(':yolov8-detector')
}
```

### Example: Integration with Contacts

```java
import pt.omst.yolov8detector.ContactDetector;
import pt.omst.yolov8detector.Detection;
import pt.omst.yolov8detector.DetectionAnnotationConverter;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.Annotation;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public class ContactAnalyzer {
    
    private final ContactDetector detector;
    
    public ContactAnalyzer(Path modelPath, String[] labels) throws Exception {
        this.detector = new ContactDetector(modelPath, labels);
    }
    
    /**
     * Detects objects in a contact and adds detection annotations to it.
     */
    public void analyzeContact(Contact contact, BufferedImage sonarPatch) throws Exception {
        // Run detection
        List<Detection> detections = detector.detect(sonarPatch);
        
        // Convert to annotations
        List<Annotation> annotations = DetectionAnnotationConverter
            .toAnnotations(detections);
        
        // Add to contact's first observation (or create new observation)
        if (contact.getObservations() != null && !contact.getObservations().isEmpty()) {
            Observation obs = contact.getObservations().get(0);
            if (obs.getAnnotations() == null) {
                obs.setAnnotations(new ArrayList<>());
            }
            obs.getAnnotations().addAll(annotations);
        }
        
        // Set contact label to highest confidence detection
        if (!detections.isEmpty() && detections.get(0).getConfidence() > 0.5) {
            contact.setLabel(detections.get(0).getLabel());
        }
    }
    
    public void close() {
        detector.close();
    }
}
```

### Example: Batch Processing

```java
import pt.omst.yolov8detector.ContactDetector;
import pt.omst.yolov8detector.Detection;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class BatchDetector {
    
    private final ContactDetector detector;
    
    public BatchDetector(ContactDetector detector) {
        this.detector = detector;
    }
    
    /**
     * Detects objects in multiple images and returns high-confidence results.
     */
    public Map<String, List<Detection>> detectBatch(
            Map<String, BufferedImage> images,
            double minConfidence) throws Exception {
        
        Map<String, List<Detection>> results = new HashMap<>();
        
        for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
            List<Detection> detections = detector.detect(entry.getValue());
            
            // Filter by confidence
            List<Detection> filtered = detections.stream()
                .filter(d -> d.getConfidence() >= minConfidence)
                .collect(Collectors.toList());
            
            if (!filtered.isEmpty()) {
                results.put(entry.getKey(), filtered);
            }
        }
        
        return results;
    }
}
```

## Performance Notes

- First inference may be slower due to model initialization
- Typical inference time: 50-200ms per image (depending on hardware)
- GPU acceleration available if ONNX Runtime GPU version is used
- NMS adds minimal overhead (~1-5ms)
- Consider using higher confidence thresholds to reduce false positives

## Troubleshooting

### ONNX Runtime Not Found
Ensure the ONNX Runtime dependency is correctly included in your build.gradle.

### Model Loading Errors
- Verify the ONNX model file exists and is readable
- Ensure the model is a valid YoloV8 detection ONNX export
- Check that the number of class labels matches the model output size

### Inference Errors
- Verify input image is not null
- Check that the model was exported correctly from YoloV8
- Ensure class labels are in the correct order

### No Detections
- Try lowering the confidence threshold
- Verify the model was trained on similar data
- Check image preprocessing (should be RGB)

### Too Many False Positives
- Increase the confidence threshold
- Adjust the IoU threshold for NMS
- Retrain the model with better data

## License

Proprietary - OceanScan Marine Systems and Technology (OMST)
