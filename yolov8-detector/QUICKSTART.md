# YoloV8 Detector Quick Start Guide

## For Application Developers

### 1. Add Dependency

In your module's `build.gradle`:
```gradle
dependencies {
    implementation project(':yolov8-detector')
}
```

### 2. Prepare Your Model

Train and export a YoloV8 detection model:
```python
from ultralytics import YOLO

# Train on your sonar dataset
model = YOLO('yolov8n.pt')  # Start with nano model
model.train(data='dataset.yaml', epochs=100, imgsz=640)

# Export to ONNX
model.export(format='onnx')
```

### 3. Basic Usage

```java
import pt.omst.yolov8detector.ContactDetector;
import pt.omst.yolov8detector.Detection;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public class MyDetector {
    public static void main(String[] args) throws Exception {
        // Define your class labels (must match model order)
        String[] labels = {"rock", "debris", "mine", "wreck"};
        
        // Load detector
        try (ContactDetector detector = new ContactDetector(
                Path.of("path/to/model.onnx"), labels)) {
            
            // Load image
            BufferedImage image = ImageIO.read(new File("sidescan.png"));
            
            // Detect objects
            List<Detection> detections = detector.detect(image);
            
            // Print results
            for (Detection d : detections) {
                System.out.printf("%s at [%.2f, %.2f] (%.1f%% confidence)\n",
                    d.getLabel(), d.getX(), d.getY(), d.getConfidence() * 100);
            }
        }
    }
}
```

### 4. Integration with Contacts

```java
import pt.omst.yolov8detector.DetectionAnnotationConverter;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Annotation;

// Detect objects in sonar patch
List<Detection> detections = detector.detect(sonarPatch);

// Convert to annotations
List<Annotation> annotations = DetectionAnnotationConverter
    .toAnnotationsWithMinConfidence(detections, 0.3);

// Add to contact
contact.getObservations().get(0).getAnnotations().addAll(annotations);

// Set contact label if high confidence
if (!detections.isEmpty() && detections.get(0).getConfidence() > 0.7) {
    contact.setLabel(detections.get(0).getLabel());
}
```

## For Data Scientists

### Dataset Preparation

#### Option 1: From Rasterindex and Contacts

Use the built-in dataset generator:

```bash
java -cp yolov8-detector.jar pt.omst.yolov8detector.DatasetGenerator \
  /path/to/rasterIndex.json \
  /path/to/contacts.zct \
  /path/to/output/dataset \
  rock debris mine wreck
```

#### Option 2: Manual YOLO Format

Organize your training data:
```
sonar_dataset/
  train/
    images/
      img001.jpg
      img002.jpg
      ...
    labels/
      img001.txt  # YOLO format: class_id x_center y_center width height
      img002.txt
      ...
  val/
    images/
    labels/
  test/
    images/
    labels/
  dataset.yaml
```

Example `dataset.yaml`:
```yaml
path: /path/to/sonar_dataset
train: train/images
val: val/images
test: test/images

nc: 4  # number of classes
names:
  0: rock
  1: debris
  2: mine
  3: wreck
```

Example label file (`img001.txt`):
```
0 0.5 0.3 0.1 0.15  # rock at center (0.5, 0.3), size 0.1x0.15
1 0.7 0.8 0.08 0.12 # debris at (0.7, 0.8), size 0.08x0.12
```

### Training Script

```python
from ultralytics import YOLO

# Load pretrained model
model = YOLO('yolov8n.pt')  # nano, small, medium, large, or xlarge

# Train
results = model.train(
    data='sonar_dataset/dataset.yaml',
    epochs=100,
    imgsz=640,
    batch=16,
    patience=20,
    save=True,
    workers=8,
    device=0  # GPU device, or 'cpu'
)

# Validate
metrics = model.val()
print(f"mAP50: {metrics.box.map50}")
print(f"mAP50-95: {metrics.box.map}")

# Export to ONNX
model.export(format='onnx', simplify=True)
```

### Model Requirements

- **Input**: 640x640 RGB images (auto-resized)
- **Output**: Detections with bounding boxes
- **Format**: ONNX
- **Preprocessing**: Handled automatically by ContactDetector

## Testing

### Run Tests
```bash
./gradlew :yolov8-detector:test
```

### Run Demo
```bash
./gradlew :yolov8-detector:build

java -cp yolov8-detector/build/libs/yolov8-detector-2025.11.00.jar \
  pt.omst.yolov8detector.ContactDetectorDemo \
  model.onnx image.png rock debris mine wreck
```

### Integration Test
To test with a real model, set system property:
```bash
./gradlew :yolov8-detector:test -Dyolov8.detect.model.path=/path/to/model.onnx
```

## Advanced Configuration

### Custom Thresholds

```java
// Low confidence, high IoU - more detections, fewer duplicates
ContactDetector detector = new ContactDetector(
    modelPath, labels, 
    0.15,  // confidence threshold
    0.6    // IoU threshold for NMS
);

// High confidence, low IoU - fewer false positives, may miss objects
ContactDetector detector = new ContactDetector(
    modelPath, labels,
    0.5,   // confidence threshold
    0.3    // IoU threshold for NMS
);
```

### Filtering Detections

```java
// Get only high-confidence detections
List<Detection> detections = detector.detect(image);
List<Detection> highConf = detections.stream()
    .filter(d -> d.getConfidence() > 0.8)
    .collect(Collectors.toList());

// Get only specific classes
List<Detection> mines = detections.stream()
    .filter(d -> d.getLabel().equals("mine"))
    .collect(Collectors.toList());
```

## Troubleshooting

### Model Loading Errors
- Verify ONNX file exists and is readable
- Ensure model is YoloV8 detection (not classification or segmentation)
- Check export with `onnxruntime` compatibility

### Poor Detection Performance
- Verify class labels match training order
- Check image preprocessing (should be RGB)
- Validate model accuracy on test set
- Try adjusting confidence threshold

### Performance Issues
- First inference is slower (initialization)
- Consider GPU version of ONNX Runtime for large batches
- Use appropriate confidence threshold to reduce post-processing

### Dataset Generation Issues
- Ensure rasterindex and contacts are properly formatted
- Check that contacts have valid bounding box annotations
- Verify coordinate normalization is correct

## Resources

- [YoloV8 Documentation](https://docs.ultralytics.com/)
- [ONNX Runtime](https://onnxruntime.ai/)
- [Module README](README.md)
- [Main Project README](../README.md)

## Common Use Cases

### 1. Real-time Detection Pipeline

```java
public class RealtimeDetector {
    private final ContactDetector detector;
    
    public void processStream(Stream<BufferedImage> imageStream) {
        imageStream.forEach(image -> {
            try {
                List<Detection> detections = detector.detect(image);
                processDetections(detections);
            } catch (Exception e) {
                log.error("Detection failed", e);
            }
        });
    }
}
```

### 2. Batch Processing

```java
public class BatchProcessor {
    public void processSurvey(List<File> images) throws Exception {
        try (ContactDetector detector = new ContactDetector(...)) {
            for (File imageFile : images) {
                BufferedImage image = ImageIO.read(imageFile);
                List<Detection> detections = detector.detect(image);
                saveResults(imageFile.getName(), detections);
            }
        }
    }
}
```

### 3. Quality Control

```java
public class QualityControl {
    public boolean verifyAnnotations(Contact contact, BufferedImage patch) 
            throws Exception {
        List<Detection> autoDetections = detector.detect(patch);
        List<Annotation> manualAnnotations = getManualAnnotations(contact);
        
        // Compare automated vs manual annotations
        return calculateAgreement(autoDetections, manualAnnotations) > 0.8;
    }
}
```
