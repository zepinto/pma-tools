package pt.omst.rasterlib.contacts;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.util.FileUtil;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.neptus.util.ZipUtils;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Contact;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;

@Slf4j
public class ContactEditor extends JPanel implements ContactChangeListener {

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

    public ContactEditor() {
        setLayout(new BorderLayout());

        observationsPanel = new ObservationsPanel();
        observationsPanel.setPreferredSize(new Dimension(400, 400));
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(observationsPanel);
        splitPane.setRightComponent(createInputsPanel());
        splitPane.setDividerLocation(0.35);
        add(splitPane, BorderLayout.CENTER);
        observationsPanel.addChangeListener(this);

        ObservationsListPanel observationsListPanel = new ObservationsListPanel();
        observationsListPanel.setPreferredSize(new Dimension(800, 100));
        add(observationsListPanel, BorderLayout.SOUTH);

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
        if (observation.getAnnotations() == null || observation.getAnnotations().isEmpty()) {
            log.info("No annotations for observation {}", observation.getUuid());
            return;
        }
        for (Annotation annot : observation.getAnnotations()) {
            if (annot.getAnnotationType().equals(AnnotationType.TEXT)) {
                descriptionTextArea.setText(annot.getText());
            } else if (annot.getAnnotationType().equals(AnnotationType.CLASSIFICATION)) {
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
        if (observation.getRasterFilename() == null) {
            log.info("No raster file for observation {}", observation.getUuid());
        } else {
            observationsPanel.addObservation(folder, observation);
        }
    }

    public void load(File folder, Contact contact) {
        log.info("Loading contact from folder {}", folder.getAbsolutePath());
        this.contact = contact;
        nameTextField.setText(contact.getLabel());
        OffsetDateTime timestamp = OffsetDateTime.now();
        observationsPanel.clear();
        for (Observation observation : contact.getObservations()) {
            loadObservation(folder, observation);
            if (observation.getTimestamp().isBefore(timestamp)) {
                timestamp = observation.getTimestamp();
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

        System.out.println("confidenceComboBox.getSelectedItem() = " + confidenceComboBox.getSelectedItem());
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
            Contact contact = Converter.ContactFromJsonString(FileUtil.getFileAsString(contactFile));
            load(tempDir.toFile(), contact);
        }

        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    private final DocumentListener dummyDocumentListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
        }
    };

    private final ActionListener dummyActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            saveButton.setEnabled(true);
            cancelButton.setEnabled(true);
        }
    };

    private JPanel createInputsPanel() {
        JPanel userInputsPanel = new JPanel();
        userInputsPanel.setLayout(new GridBagLayout());
        userInputsPanel.setBorder(BorderFactory.createTitledBorder("Contact Details"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setPreferredSize(new Dimension(100, 20));
        nameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        userInputsPanel.add(nameLabel, gbc);

        nameTextField = new JTextField(15);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        userInputsPanel.add(nameTextField, gbc);

        JLabel typeLabel = new JLabel("Type:");
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        typeLabel.setPreferredSize(new Dimension(100, 20));
        typeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        userInputsPanel.add(typeLabel, gbc);

        typeComboBox = ItemList.getContactTypes().getComboBox();
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        userInputsPanel.add(typeComboBox, gbc);

        JLabel confidenceLabel = new JLabel("Confidence:");
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        confidenceLabel.setPreferredSize(new Dimension(100, 20));
        confidenceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        userInputsPanel.add(confidenceLabel, gbc);

        confidenceComboBox = ItemList.getConfidenceTypes().getComboBox();
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 2;

        userInputsPanel.add(confidenceComboBox, gbc);
        JLabel descriptionLabel = new JLabel("Description:");
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        descriptionLabel.setPreferredSize(new Dimension(100, 20));
        descriptionLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        userInputsPanel.add(descriptionLabel, gbc);

        descriptionTextArea = new JTextArea(3, 30);
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionTextArea);
        gbc.gridx = 2;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        userInputsPanel.add(descriptionScrollPane, gbc);

        JLabel timestampLabel = new JLabel("Timestamp:");
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        timestampLabel.setPreferredSize(new Dimension(100, 20));
        timestampLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        userInputsPanel.add(timestampLabel, gbc);

        timestampTextField.setEditable(false);
        timestampTextField.setText("2019-10-10 10:10:10");
        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        userInputsPanel.add(timestampTextField, gbc);

        JLabel positionLabel = new JLabel("Position:");
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        positionLabel.setPreferredSize(new Dimension(100, 20));
        positionLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        userInputsPanel.add(positionLabel, gbc);

        positionTextArea.setEditable(false);
        positionTextArea.setText("Latitude: N/A\nLongitude: N/A\nDepth: N/A");
        JScrollPane positionScrollPane = new JScrollPane(positionTextArea);
        gbc.gridx = 2;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        userInputsPanel.add(positionScrollPane, gbc);

        JLabel measurementsLabel = new JLabel("Size:");
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        measurementsLabel.setPreferredSize(new Dimension(100, 20));
        measurementsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        userInputsPanel.add(measurementsLabel, gbc);

        measurementsTextArea.setEditable(false);
        measurementsTextArea.setText("Length: 0.0\nWidth: 0.0\nHeight: 0.0");
        JScrollPane measurementsScrollPane = new JScrollPane(measurementsTextArea);
        gbc.gridx = 2;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        userInputsPanel.add(measurementsScrollPane, gbc);

        typeComboBox.addActionListener(dummyActionListener);
        confidenceComboBox.addActionListener(dummyActionListener);
        descriptionTextArea.getDocument().addDocumentListener(dummyDocumentListener);
        nameTextField.getDocument().addDocumentListener(dummyDocumentListener);

        return userInputsPanel;
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
        String length = lengthMeasurements.isEmpty() ? "N/A" : lengthMeasurements.stream().map(e -> String.format("%.2f", e)).collect(Collectors.joining(", "));
        String width = widthMeasurements.isEmpty() ? "N/A" : widthMeasurements.stream().map(e -> String.format("%.2f", e)).collect(Collectors.joining(", "));
        String height = heightMeasurements.isEmpty() ? "N/A" : heightMeasurements.stream().map(e -> String.format("%.2f", e)).collect(Collectors.joining(", "));
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

    @Override
    public void doLayout() {
        super.doLayout();
        

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

    public static void main(String[] args) throws IOException {
        //GeneralPreferences.uiLookAndFeel = GuiUtils.UILookAndFeel.FLAT_MAC_DARK;
        GuiUtils.setLookAndFeel();
        JFrame frame = new JFrame("Contact Editor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1050, 700);
        frame.setResizable(true);
        ContactEditor editor = new ContactEditor();
        editor.loadZct(new File("/LOGS/REP/REP24/lauv-omst-2/20240917/131930_RI-OMST-2/contacts/m3.zct"));
        frame.add(editor);
        frame.setVisible(true);
    }
}
