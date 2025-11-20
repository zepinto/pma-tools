//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.contacts.ContactChangeListener;
import pt.omst.contacts.ContactSaveListener;
import pt.omst.contacts.ItemList;
import pt.omst.contacts.ObservationsPanel;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;

@Slf4j
public class VerticalContactEditor extends JPanel implements ContactChangeListener {

    private JTextArea descriptionTextArea;
    private JTextField nameTextField;
    private JComboBox<String> confidenceComboBox;
    private JComboBox<String> typeComboBox;
    private final JTextField timestampTextField = new JTextField(15);
    private final JTextArea positionTextArea = new JTextArea(3, 20);
    private final JTextArea measurementsTextArea = new JTextArea(3, 20);
    private final ObservationsPanel observationsPanel;
    private final JButton saveButton;
    private final JButton cancelButton;
    @Getter
    private final JPanel buttonPanel;
    @Getter
    private Contact contact;
    @Getter
    private File zctFile = null;

    // Save listeners
    private final java.util.List<ContactSaveListener> saveListeners = new java.util.ArrayList<>();
    
    // Contact changed listeners
    private final java.util.List<ContactChangedListener> contactChangedListeners = new java.util.ArrayList<>();

    public VerticalContactEditor() {
        setLayout(new BorderLayout());

        observationsPanel = new ObservationsPanel();
        observationsPanel.setPreferredSize(new Dimension(240, 500));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(observationsPanel);
        splitPane.setBottomComponent(createInputsPanel());
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
        observationsPanel.addChangeListener(this);

        buttonPanel = new JPanel();
        saveButton = new JButton("Save");
        cancelButton = new JButton("Revert");
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        saveButton.addActionListener(e -> {
            saveContact();
            saveButton.setEnabled(false);
            cancelButton.setEnabled(false);
        });

        cancelButton.addActionListener(e -> {
            try {
                loadZct(zctFile);
                saveButton.setEnabled(false);
                cancelButton.setEnabled(false);
            } catch (IOException ex) {
                log.error("Error loading ZCT file", ex);
            }
        });

        add(buttonPanel, BorderLayout.SOUTH);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    public void loadObservation(File folder, Observation observation) {
        log.info("Loading observation UUID={}, hasRaster={}, annotationCount={}", 
            observation.getUuid(), 
            observation.getRasterFilename() != null && !observation.getRasterFilename().isEmpty(),
            observation.getAnnotations() != null ? observation.getAnnotations().size() : 0);
            
        // Reset classification fields to default values
        boolean hasClassification = false;
        
        if (observation.getAnnotations() == null || observation.getAnnotations().isEmpty()) {
            log.info("No annotations for observation {}", observation.getUuid());
        } else {
            for (Annotation annot : observation.getAnnotations()) {
                log.info("Processing annotation type={}, text={}", annot.getAnnotationType(), annot.getText());
                if (annot.getAnnotationType().equals(AnnotationType.TEXT)) {
                    descriptionTextArea.setText(annot.getText());
                } else if (annot.getAnnotationType().equals(AnnotationType.CLASSIFICATION)) {
                    hasClassification = true;
                    typeComboBox.setSelectedItem(annot.getCategory());
                    for (int i = 0; i < confidenceComboBox.getItemCount(); i++) {
                        if (sameThing(confidenceComboBox.getItemAt(i), annot.getConfidence())) {
                            confidenceComboBox.setSelectedIndex(i);
                            break;
                        }
                    }
                    log.info("Setting type to {} and confidence to {}", annot.getCategory(), annot.getConfidence());
                }
            }
        }
        
        // If no classification found, set to UNKNOWN/0
        if (!hasClassification) {
            typeComboBox.setSelectedItem("UNKNOWN");
            confidenceComboBox.setSelectedItem("0");
            log.info("No classification found, setting to UNKNOWN/0");
        }
        
        if (observation.getRasterFilename() == null || observation.getRasterFilename().isEmpty()) {
            log.warn("No raster file for observation {}, skipping visual display", observation.getUuid());
        } else {
            log.info("Adding observation {} with raster {} to panel", 
                observation.getUuid(), observation.getRasterFilename());
            observationsPanel.addObservation(folder, observation);
        }
    }

    public void load(File folder, Contact contact) {
        log.info("Loading contact from folder {}", folder.getAbsolutePath());
        log.debug("Contact has {} observations", contact.getObservations().size());
        this.contact = contact;
        nameTextField.setText(contact.getLabel());
        OffsetDateTime timestamp = OffsetDateTime.now();
        observationsPanel.clear();
        log.debug("Cleared observations panel, now loading {} observations", contact.getObservations().size());
        
        // Load first observation immediately on EDT
        if (!contact.getObservations().isEmpty()) {
            Observation firstObs = contact.getObservations().get(0);
            log.debug("Loading first observation immediately: {}", firstObs.getUuid());
            loadObservation(folder, firstObs);
            if (firstObs.getTimestamp().isBefore(timestamp)) {
                timestamp = firstObs.getTimestamp();
            }
            
            // Load remaining observations in background if there are more than 1
            if (contact.getObservations().size() > 1) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    log.info("Loading {} remaining observations in background", 
                        contact.getObservations().size() - 1);
                    
                    for (int i = 1; i < contact.getObservations().size(); i++) {
                        final Observation obs = contact.getObservations().get(i);
                        final int index = i;
                        
                        try {
                            // Load on EDT
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                try {
                                    log.debug("Loading observation {} of {}: {}", 
                                        index + 1, contact.getObservations().size(), obs.getUuid());
                                    loadObservation(folder, obs);
                                } catch (Exception e) {
                                    log.error("Error loading observation {} in background", obs.getUuid(), e);
                                }
                            });
                            
                            // Small delay to avoid overwhelming EDT
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            log.warn("Background observation loading interrupted", e);
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    log.info("Finished loading all {} observations", contact.getObservations().size());
                }).exceptionally(throwable -> {
                    log.error("Error in background observation loading", throwable);
                    return null;
                });
            }
        }

        positionTextArea.setText("Latitude: " + contact.getLatitude() + "\nLongitude: " + contact.getLongitude()
                + "\nDepth: " + contact.getDepth());
        timestampTextField.setText(timestamp.toString());
        setMeasurements(contact);
    }

    public ArrayList<Annotation> getAnnotationsOfType(AnnotationType type) {
        ArrayList<Annotation> annotations = new ArrayList<>();
        try {
            for (Observation observation : contact.getObservations()) {
                for (Annotation annotation : observation.getAnnotations()) {
                    if (annotation.getAnnotationType().equals(type)) {
                        annotations.add(annotation);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting annotations of type {} for contact {}", type, contact.getUuid());
        }
        return annotations;
    }

    public void saveContact() {
        log.info("Saving contact");
        contact.setLabel(nameTextField.getText());
        ArrayList<Annotation> textAnnotations = getAnnotationsOfType(AnnotationType.TEXT);
        if (textAnnotations.isEmpty()) {
            Annotation textAnnotation = new Annotation();
            textAnnotation.setAnnotationType(AnnotationType.TEXT);
            textAnnotation.setText(descriptionTextArea.getText());
            contact.getObservations().getFirst().getAnnotations().add(textAnnotation);
        } else {
            textAnnotations.getFirst().setText(descriptionTextArea.getText());
        }

        ArrayList<Annotation> classificationAnnotations = getAnnotationsOfType(AnnotationType.CLASSIFICATION);

        if (classificationAnnotations.isEmpty()) {
            Annotation classificationAnnotation = new Annotation();
            classificationAnnotation.setAnnotationType(AnnotationType.CLASSIFICATION);
            classificationAnnotation.setCategory((String) typeComboBox.getSelectedItem());
            classificationAnnotation.setConfidence(
                    Double.valueOf((String) Objects.requireNonNull(confidenceComboBox.getSelectedItem())));
            contact.getObservations().getFirst().getAnnotations().add(classificationAnnotation);
        } else {
            classificationAnnotations.getFirst().setCategory((String) typeComboBox.getSelectedItem());
            classificationAnnotations.getFirst().setConfidence(
                    Double.valueOf((String) Objects.requireNonNull(confidenceComboBox.getSelectedItem())));
        }

        if (contact.getUuid() == null) {
            contact.setUuid(java.util.UUID.randomUUID());
        }
        try {
            String json = Converter.ContactToJsonString(contact);
            ZipUtils.updateFileInZip(zctFile.getAbsolutePath(), "contact.json", json);
            log.info("Contact saved");

            // Fire save event
            fireSaveEvent();
        } catch (IOException e) {
            log.error("Error converting contact to JSON", e);
        }
    }

    public void setZctFile(File file) {
        File oldFile = zctFile;
        try {
            loadZct(file);
        } catch (IOException e) {
            log.error("Error loading ZCT file", e);
            zctFile = oldFile;
        }
    }

    public void loadZct(File file) throws IOException {
        this.zctFile = file;
        Path tempDir = Files.createTempDirectory("zct");
        log.info("Unzipping file {} to {}", file.getAbsolutePath(), tempDir);
        ZipUtils.unzip(file.getAbsolutePath(), tempDir);
        File contactFile = new File(tempDir.toFile(), "contact.json");
        if (contactFile.exists()) {
            String json = Files.readString(contactFile.toPath());
            log.debug("Read contact.json with {} bytes", json.length());
            Contact contact = Converter.ContactFromJsonString(json);
            log.debug("Parsed contact with {} observations", contact.getObservations().size());
            load(tempDir.toFile(), contact);
        } else {
            log.error("contact.json not found in {}", tempDir);
        }

        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    private final DocumentListener dummyDocumentListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            fireContactChanged();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            fireContactChanged();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            fireContactChanged();
        }
    };

    private final ActionListener dummyActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
            fireContactChanged();
        }
    };

    private JPanel createInputsPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(createDetailsPanel());
        scrollPane.setBorder(null);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        return mainPanel;
    }

    private JPanel createDetailsPanel() {
        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Contact Details"));

        // Name field
        detailsPanel.add(createFieldPanel("Name:", nameTextField = new JTextField()));
        detailsPanel.add(Box.createVerticalStrut(5));

        // Type field
        typeComboBox = ItemList.getContactTypes().getComboBox();
        detailsPanel.add(createFieldPanel("Type:", typeComboBox));
        detailsPanel.add(Box.createVerticalStrut(5));

        // Confidence field
        confidenceComboBox = ItemList.getConfidenceTypes().getComboBox();
        detailsPanel.add(createFieldPanel("Confidence:", confidenceComboBox));
        detailsPanel.add(Box.createVerticalStrut(5));

        // Description field
        descriptionTextArea = new JTextArea(3, 20);
        descriptionTextArea.setLineWrap(true);
        descriptionTextArea.setWrapStyleWord(true);
        JScrollPane descScrollPane = new JScrollPane(descriptionTextArea);
        descScrollPane.setPreferredSize(new Dimension(300, 60));
        detailsPanel.add(createFieldPanel("Description:", descScrollPane));
        detailsPanel.add(Box.createVerticalStrut(5));

        // Timestamp field (read-only)
        timestampTextField.setEditable(false);
        timestampTextField.setText("2019-10-10 10:10:10");
        detailsPanel.add(createFieldPanel("Timestamp:", timestampTextField));
        detailsPanel.add(Box.createVerticalStrut(5));

        // Position field (read-only)
        positionTextArea.setEditable(false);
        positionTextArea.setText("Latitude: N/A\nLongitude: N/A\nDepth: N/A");
        positionTextArea.setLineWrap(true);
        positionTextArea.setWrapStyleWord(true);
        JScrollPane posScrollPane = new JScrollPane(positionTextArea);
        posScrollPane.setPreferredSize(new Dimension(300, 60));
        detailsPanel.add(createFieldPanel("Position:", posScrollPane));
        detailsPanel.add(Box.createVerticalStrut(5));

        // Size/Measurements field (read-only)
        measurementsTextArea.setEditable(false);
        measurementsTextArea.setText("Length: 0.0\nWidth: 0.0\nHeight: 0.0");
        measurementsTextArea.setLineWrap(true);
        measurementsTextArea.setWrapStyleWord(true);
        JScrollPane measScrollPane = new JScrollPane(measurementsTextArea);
        measScrollPane.setPreferredSize(new Dimension(300, 60));
        detailsPanel.add(createFieldPanel("Size:", measScrollPane));

        // Add listeners
        typeComboBox.addActionListener(dummyActionListener);
        confidenceComboBox.addActionListener(dummyActionListener);
        descriptionTextArea.getDocument().addDocumentListener(dummyDocumentListener);
        nameTextField.getDocument().addDocumentListener(dummyDocumentListener);

        return detailsPanel;
    }

    private JPanel createFieldPanel(String labelText, java.awt.Component component) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(90, 20));
        panel.add(label, BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);

        return panel;
    }

    private void setMeasurements(Contact contact) {

        ArrayList<Double> lengthMeasurements = new ArrayList<>();
        ArrayList<Double> widthMeasurements = new ArrayList<>();
        ArrayList<Double> heightMeasurements = new ArrayList<>();

        for (Annotation annotation : getAnnotationsOfType(AnnotationType.MEASUREMENT)) {

            if (annotation.getAnnotationType().equals(AnnotationType.MEASUREMENT)
                    && annotation.getMeasurementType().equals(MeasurementType.LENGTH)) {
                if (annotation.getValue() != null) {
                    lengthMeasurements.add(annotation.getValue());
                }
            } else if (annotation.getAnnotationType().equals(AnnotationType.MEASUREMENT)
                    && annotation.getMeasurementType().equals(MeasurementType.WIDTH)) {
                if (annotation.getValue() != null) {
                    widthMeasurements.add(annotation.getValue());
                }

            } else if (annotation.getAnnotationType().equals(AnnotationType.MEASUREMENT)
                    && annotation.getMeasurementType().equals(MeasurementType.HEIGHT)) {
                if (annotation.getValue() != null) {
                    heightMeasurements.add(annotation.getValue());
                }
            }
        }
        String length = lengthMeasurements.isEmpty() ? "N/A"
                : lengthMeasurements.stream().map(e -> String.format("%.2f", e)).collect(Collectors.joining(", "));
        String width = widthMeasurements.isEmpty() ? "N/A"
                : widthMeasurements.stream().map(e -> String.format("%.2f", e)).collect(Collectors.joining(", "));
        String height = heightMeasurements.isEmpty() ? "N/A"
                : heightMeasurements.stream().map(e -> String.format("%.2f", e)).collect(Collectors.joining(", "));
        measurementsTextArea.setText(String.format("Length: %s\nWidth: %s\nHeight: %s", length, width, height));
    }

    public boolean sameThing(Object o1, Object o2) {
        return o1 == o2 || (o1 != null && o1.equals(o2))
                || (o1 instanceof Number && o2 instanceof Number
                        && ((Number) o1).doubleValue() == ((Number) o2).doubleValue())
                || (o1 instanceof Number && o2 instanceof String
                        && ((Number) o1).doubleValue() == Double.parseDouble((String) o2))
                || (o1 instanceof String && o2 instanceof Number
                        && Double.parseDouble((String) o1) == ((Number) o2).doubleValue());
    }

    @Override
    public void observationChanged(Observation observation) {
        saveButton.setEnabled(true);
        cancelButton.setEnabled(true);

        // update dimensions
        setMeasurements(contact);
        // update description
        log.info("Observation changed: {}", observation.getRasterFilename());
    }

    @Override
    public void observationDeleted(Observation observation) {
        log.info("Observation deleted: {}", observation.getRasterFilename());
    }

    // ContactSaveListener support

    /**
     * Add a save listener
     */
    public void addSaveListener(ContactSaveListener listener) {
        saveListeners.add(listener);
    }

/**
     * Remove a save listener
     */
    public void removeSaveListener(ContactSaveListener listener) {
        saveListeners.remove(listener);
    }
    
    /**
     * Add a contact changed listener
     */
    public void addContactChangedListener(ContactChangedListener listener) {
        contactChangedListeners.add(listener);
    }
    
    /**
     * Remove a contact changed listener
     */
    public void removeContactChangedListener(ContactChangedListener listener) {
        contactChangedListeners.remove(listener);
    }

    /**
     * Fire save event to all listeners
     */
    private void fireSaveEvent() {
        if (contact != null && zctFile != null) {
            for (ContactSaveListener listener : saveListeners) {
                try {
                    listener.onContactSaved(contact.getUuid(), zctFile);
                } catch (Exception e) {
                    log.error("Error notifying save listener", e);
                }
            }
        }
    }
    
    /**
     * Fire contact changed event to all listeners
     */
    private void fireContactChanged() {
        if (contact != null) {
            for (ContactChangedListener listener : contactChangedListeners) {
                try {
                    listener.onContactChanged(contact);
                } catch (Exception e) {
                    log.error("Error notifying contact changed listener", e);
                }
            }
        }
    }

    public void clearObservations() {
        observationsPanel.clear();
    }

    public static void main(String[] args) throws IOException {
        GuiUtils.setLookAndFeel();
        JFrame frame = new JFrame("Vertical Contact Editor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 800);
        frame.setResizable(true);
        VerticalContactEditor editor = new VerticalContactEditor();
        editor.loadZct(new File("/LOGS/REP/REP24/lauv-omst-2/20240917/131930_RI-OMST-2/contacts/m3.zct"));
        frame.add(editor);
        frame.setVisible(true);
    }
}
