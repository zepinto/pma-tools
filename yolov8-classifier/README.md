# YoloV8 Classifier

A cross-platform library for classifying sonar contact snippets using YoloV8-cls models.

## Overview

This library provides a simple Java API for running YoloV8 classification models on sonar contact images. It uses ONNX Runtime for cross-platform deep learning inference, making it compatible with Linux, macOS, and Windows.

## Features

- **Cross-platform**: Works on Linux, Mac, and Windows
- **Simple API**: Just pass a `BufferedImage` and get classification results
- **ONNX Runtime**: Efficient inference using Microsoft's ONNX Runtime
- **Top-N results**: Get the top N most confident classifications
- **Resource loading**: Load models from files or embedded resources
- **Auto-closeable**: Implements `AutoCloseable` for proper resource management

## Usage

### Basic Classification

```java
import pt.omst.yolov8classifier.ContactClassifier;
import pt.omst.yolov8classifier.Classification;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

// Define your class labels (must match the order expected by your model)
String[] classLabels = {"rock", "debris", "sand", "mine", "wreck", "unknown"};

// Load the classifier (auto-closeable)
try (ContactClassifier classifier = new ContactClassifier(
        Path.of("/path/to/model.onnx"), classLabels)) {
    
    // Load your sonar contact image
    BufferedImage contactImage = ImageIO.read(new File("contact.png"));
    
    // Classify the image
    List<Classification> results = classifier.classify(contactImage);
    
    // Print results
    for (Classification c : results) {
        System.out.println(c); // Prints: "rock (85.23%)"
    }
    
    // Get top prediction
    Classification topPrediction = results.get(0);
    System.out.println("Best match: " + topPrediction.getLabel());
    System.out.println("Confidence: " + topPrediction.getConfidence());
}
```

### Top-N Classification

```java
// Get only top 3 classifications
List<Classification> top3 = classifier.classify(contactImage, 3);
```

### Loading from Resources

```java
// Load model from JAR resources
String[] labels = {"class1", "class2", "class3"};
try (ContactClassifier classifier = new ContactClassifier(
        "/models/yolov8-cls.onnx", labels)) {
    // ... use classifier
}
```

## Model Requirements

### Input Format
- **Size**: 224x224 pixels (automatically resized)
- **Format**: RGB image
- **Normalization**: Pixel values normalized to [0, 1]
- **Layout**: NCHW (batch, channels, height, width)

### Output Format
- **Type**: Float array of classification probabilities
- **Size**: One probability per class
- **Range**: [0, 1] (should sum to ~1.0)

### Creating a YoloV8-cls ONNX Model

To train and export a YoloV8-cls model:

```python
from ultralytics import YOLO

# Train a classification model
model = YOLO('yolov8n-cls.pt')
model.train(data='/path/to/dataset', epochs=100)

# Export to ONNX
model.export(format='onnx')
```

The dataset should be organized as:
```
dataset/
  train/
    class1/
      image1.jpg
      image2.jpg
    class2/
      image1.jpg
  val/
    class1/
      image1.jpg
    class2/
      image1.jpg
```

## Dependencies

- **ONNX Runtime**: 1.19.2 (automatically included)
- **SLF4J**: Logging API
- **Lombok**: Code generation
- **neptus-utils**: OMST utilities

## Running the Demo

```bash
# Build the module
./gradlew :yolov8-classifier:build

# Run the demo
java -cp yolov8-classifier/build/libs/yolov8-classifier-2025.11.00.jar \
  pt.omst.yolov8classifier.ContactClassifierDemo \
  /path/to/model.onnx \
  /path/to/image.png \
  rock debris sand mine wreck unknown
```

## Integration with Other Modules

To use this library in other PMA Tools modules, add it as a dependency:

```gradle
dependencies {
    implementation project(':yolov8-classifier')
}
```

Example integration with contacts:

```java
// In your contact analysis code
import pt.omst.yolov8classifier.ContactClassifier;
import pt.omst.yolov8classifier.Classification;
import pt.omst.rasterlib.Contact;

// Initialize classifier once
ContactClassifier classifier = new ContactClassifier(modelPath, labels);

// For each contact
BufferedImage snippet = loadContactSnippet(contact);
List<Classification> predictions = classifier.classify(snippet, 3);

// Use predictions to tag or filter contacts
String bestGuess = predictions.get(0).getLabel();
double confidence = predictions.get(0).getConfidence();
```

## Performance Notes

- First inference may be slower due to model initialization
- Typical inference time: 10-50ms per image (depending on hardware)
- GPU acceleration available if ONNX Runtime GPU version is used
- Consider batching for processing many contacts

## Troubleshooting

### ONNX Runtime Not Found
Ensure the ONNX Runtime dependency is correctly included in your build.gradle.

### Model Loading Errors
- Verify the ONNX model file exists and is readable
- Ensure the model is a valid YoloV8-cls ONNX export
- Check that the number of class labels matches the model output size

### Inference Errors
- Verify input image is not null
- Check that the model was exported correctly from YoloV8
- Ensure class labels are in the correct order

## License

Proprietary - OceanScan Marine Systems and Technology (OMST)
