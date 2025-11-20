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

### Example 1: Basic Integration with Contacts

```java
import pt.omst.yolov8classifier.ContactClassifier;
import pt.omst.yolov8classifier.Classification;
import pt.omst.yolov8classifier.ClassificationAnnotationConverter;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.Annotation;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public class ContactAnalyzer {
    
    private final ContactClassifier classifier;
    
    public ContactAnalyzer(Path modelPath, String[] labels) throws Exception {
        this.classifier = new ContactClassifier(modelPath, labels);
    }
    
    /**
     * Classifies a contact and adds classification annotations to it.
     */
    public void classifyContact(Contact contact, BufferedImage snippet) throws Exception {
        // Run classification
        List<Classification> results = classifier.classify(snippet, 3);
        
        // Convert to annotations
        List<Annotation> annotations = ClassificationAnnotationConverter
            .toAnnotations(results, "automated-classifier");
        
        // Add to contact's first observation (or create new observation)
        if (contact.getObservations() != null && !contact.getObservations().isEmpty()) {
            Observation obs = contact.getObservations().get(0);
            if (obs.getAnnotations() == null) {
                obs.setAnnotations(new ArrayList<>());
            }
            obs.getAnnotations().addAll(annotations);
        }
        
        // Set contact label to top classification
        Classification topResult = results.get(0);
        if (topResult.getConfidence() > 0.5) {
            contact.setLabel(topResult.getLabel());
        }
    }
    
    public void close() {
        classifier.close();
    }
}
```

### Example 2: Batch Processing with Confidence Filtering

```java
import pt.omst.yolov8classifier.ContactClassifier;
import pt.omst.yolov8classifier.Classification;
import pt.omst.yolov8classifier.ClassificationAnnotationConverter;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class BatchContactClassifier {
    
    private final ContactClassifier classifier;
    private final double confidenceThreshold;
    
    public BatchContactClassifier(ContactClassifier classifier, double confidenceThreshold) {
        this.classifier = classifier;
        this.confidenceThreshold = confidenceThreshold;
    }
    
    /**
     * Classifies multiple contacts and returns high-confidence results.
     */
    public Map<String, Classification> classifyBatch(Map<String, BufferedImage> contacts) 
            throws Exception {
        Map<String, Classification> results = new HashMap<>();
        
        for (Map.Entry<String, BufferedImage> entry : contacts.entrySet()) {
            List<Classification> classifications = classifier.classify(entry.getValue());
            
            // Only keep high-confidence results
            Classification topResult = classifications.get(0);
            if (topResult.getConfidence() >= confidenceThreshold) {
                results.put(entry.getKey(), topResult);
            }
        }
        
        return results;
    }
}
```

### Example 3: Real-time Classification UI Integration

```java
import pt.omst.yolov8classifier.ContactClassifier;
import pt.omst.yolov8classifier.Classification;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class ClassificationPanel extends JPanel {
    
    private final ContactClassifier classifier;
    private JLabel resultLabel;
    private JProgressBar confidenceBar;
    
    public ClassificationPanel(ContactClassifier classifier) {
        this.classifier = classifier;
        initUI();
    }
    
    private void initUI() {
        setLayout(new BorderLayout());
        
        resultLabel = new JLabel("No classification");
        resultLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(resultLabel, BorderLayout.NORTH);
        
        confidenceBar = new JProgressBar(0, 100);
        confidenceBar.setStringPainted(true);
        add(confidenceBar, BorderLayout.CENTER);
    }
    
    /**
     * Classifies an image and updates the UI with results.
     */
    public void classifyAndDisplay(BufferedImage image) {
        SwingWorker<List<Classification>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Classification> doInBackground() throws Exception {
                return classifier.classify(image, 1);
            }
            
            @Override
            protected void done() {
                try {
                    List<Classification> results = get();
                    if (!results.isEmpty()) {
                        Classification top = results.get(0);
                        resultLabel.setText(top.getLabel());
                        confidenceBar.setValue((int)(top.getConfidence() * 100));
                        
                        // Color code by confidence
                        if (top.getConfidence() > 0.8) {
                            confidenceBar.setForeground(Color.GREEN);
                        } else if (top.getConfidence() > 0.5) {
                            confidenceBar.setForeground(Color.YELLOW);
                        } else {
                            confidenceBar.setForeground(Color.ORANGE);
                        }
                    }
                } catch (Exception e) {
                    resultLabel.setText("Classification failed");
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }
}
```

### Example 4: Integration with Annotation System

```java
// For each contact
BufferedImage snippet = loadContactSnippet(contact);
List<Classification> predictions = classifier.classify(snippet, 3);

// Convert to annotations using the helper
List<Annotation> annotations = ClassificationAnnotationConverter
    .toAnnotationsWithMinConfidence(predictions, 0.3);

// Add to observation
if (!observations.isEmpty()) {
    Observation obs = observations.get(0);
    obs.getAnnotations().addAll(annotations);
}

// Use top prediction for labeling
String bestGuess = predictions.get(0).getLabel();
double confidence = predictions.get(0).getConfidence();
if (confidence > 0.6) {
    contact.setLabel(bestGuess);
}
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
