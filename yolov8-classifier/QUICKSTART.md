# YoloV8 Classifier Quick Start Guide

## For Application Developers

### 1. Add Dependency

In your module's `build.gradle`:
```gradle
dependencies {
    implementation project(':yolov8-classifier')
}
```

### 2. Prepare Your Model

Train and export a YoloV8-cls model:
```python
from ultralytics import YOLO

# Train on your sonar contact dataset
model = YOLO('yolov8n-cls.pt')
model.train(data='/path/to/dataset', epochs=100)

# Export to ONNX
model.export(format='onnx')
```

### 3. Basic Usage

```java
import pt.omst.yolov8classifier.ContactClassifier;
import pt.omst.yolov8classifier.Classification;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public class MyClassifier {
    public static void main(String[] args) throws Exception {
        // Define your class labels (must match model order)
        String[] labels = {"rock", "debris", "sand", "mine", "wreck", "unknown"};
        
        // Load classifier
        try (ContactClassifier classifier = new ContactClassifier(
                Path.of("path/to/model.onnx"), labels)) {
            
            // Load image
            BufferedImage image = ImageIO.read(new File("contact.png"));
            
            // Classify
            List<Classification> results = classifier.classify(image);
            
            // Print top result
            Classification top = results.get(0);
            System.out.printf("Classification: %s (%.2f%% confidence)\n",
                top.getLabel(), top.getConfidence() * 100);
        }
    }
}
```

### 4. Integration with Contacts

```java
import pt.omst.yolov8classifier.ClassificationAnnotationConverter;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Annotation;

// Classify contact snippet
List<Classification> results = classifier.classify(contactImage);

// Convert to annotations
List<Annotation> annotations = ClassificationAnnotationConverter
    .toAnnotationsWithMinConfidence(results, 0.3);

// Add to contact
contact.getObservations().get(0).getAnnotations().addAll(annotations);

// Set contact label if high confidence
if (results.get(0).getConfidence() > 0.7) {
    contact.setLabel(results.get(0).getLabel());
}
```

## For Data Scientists

### Dataset Structure

Organize your training data:
```
sonar_dataset/
  train/
    rock/
      contact001.png
      contact002.png
      ...
    debris/
      contact001.png
      ...
    mine/
      contact001.png
      ...
  val/
    rock/
      ...
    debris/
      ...
    mine/
      ...
```

### Training Script

```python
from ultralytics import YOLO

# Load pretrained model
model = YOLO('yolov8n-cls.pt')

# Train
results = model.train(
    data='sonar_dataset',
    epochs=100,
    imgsz=224,
    batch=32,
    patience=20,
    save=True
)

# Validate
metrics = model.val()
print(f"Accuracy: {metrics.top1}")

# Export to ONNX
model.export(format='onnx', simplify=True)
```

### Model Requirements

- **Input**: 224x224 RGB images
- **Output**: Softmax probabilities (one per class)
- **Format**: ONNX
- **Preprocessing**: Handled automatically by ContactClassifier

## Testing

### Run Tests
```bash
./gradlew :yolov8-classifier:test
```

### Run Demo
```bash
./gradlew :yolov8-classifier:build

java -cp yolov8-classifier/build/libs/yolov8-classifier-2025.11.00.jar \
  pt.omst.yolov8classifier.ContactClassifierDemo \
  model.onnx image.png rock debris sand mine wreck
```

### Integration Test
To test with a real model, set system property:
```bash
./gradlew :yolov8-classifier:test -Dyolov8.model.path=/path/to/model.onnx
```

## Troubleshooting

### Model Loading Errors
- Verify ONNX file exists and is readable
- Ensure model is YoloV8-cls (not detection or segmentation)
- Check export with `onnxruntime` compatibility

### Wrong Predictions
- Verify class labels match training order
- Check image preprocessing (should be RGB)
- Validate model accuracy on test set

### Performance Issues
- First inference is slower (initialization)
- Consider GPU version of ONNX Runtime for large batches
- Use top-N filtering to reduce processing

## Resources

- [YoloV8 Documentation](https://docs.ultralytics.com/)
- [ONNX Runtime](https://onnxruntime.ai/)
- [Module README](README.md)
- [Main Project README](../README.md)
