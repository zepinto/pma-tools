//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.I18n;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A reusable panel for editing CSV entries with label, description, and sub-labels.
 * Features master-detail layout with real-time duplicate detection.
 */
@Slf4j
public class CSVEntryEditorPanel extends JPanel {
    
    private final File csvFile;
    private final JList<CSVEntry> entriesList;
    private final DefaultListModel<CSVEntry> listModel;
    private final JTextField labelField;
    private final JTextField descriptionField;
    private final JList<String> subLabelsList;
    private final DefaultListModel<String> subLabelsModel;
    private final JButton saveButton;
    private final JButton cancelButton;
    private final JButton newButton;
    private final JButton deleteButton;
    
    private CSVEntry currentEntry = null;
    private int previousSelection = -1;
    private boolean isModified = false;
    private final Border normalBorder;
    private final Border errorBorder;
    
    private Runnable onSaveCallback = null;

    public CSVEntryEditorPanel(File csvFile) {
        this.csvFile = csvFile;
        this.listModel = new DefaultListModel<>();
        this.entriesList = new JList<>(listModel);
        this.subLabelsModel = new DefaultListModel<>();
        this.subLabelsList = new JList<>(subLabelsModel);
        this.labelField = new JTextField(20);
        this.descriptionField = new JTextField(20);
        this.saveButton = new JButton(I18n.text("Save"));
        this.cancelButton = new JButton(I18n.text("Cancel"));
        this.newButton = new JButton(I18n.text("New"));
        this.deleteButton = new JButton(I18n.text("Delete"));
        
        this.normalBorder = labelField.getBorder();
        this.errorBorder = BorderFactory.createLineBorder(Color.RED, 2);
        
        initComponents();
        loadFromFile();
    }
    
    /**
     * Set a callback to be invoked after successful save.
     */
    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(newButton);
        toolbar.add(deleteButton);
        toolbar.addSeparator();
        toolbar.add(saveButton);
        toolbar.add(cancelButton);
        add(toolbar, BorderLayout.NORTH);
        
        // Split pane: list on left, form on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(250);
        
        // Left: List of entries
        entriesList.setCellRenderer(new EntryListCellRenderer());
        entriesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entriesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onEntrySelected();
            }
        });
        JScrollPane listScroll = new JScrollPane(entriesList);
        listScroll.setBorder(BorderFactory.createTitledBorder("Entries"));
        splitPane.setLeftComponent(listScroll);
        
        // Right: Form panel
        JPanel formPanel = createFormPanel();
        splitPane.setRightComponent(formPanel);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Button actions
        newButton.addActionListener(e -> onNew());
        deleteButton.addActionListener(e -> onDelete());
        saveButton.addActionListener(e -> onSave());
        cancelButton.addActionListener(e -> onCancel());
        
        // Initially disable buttons
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        deleteButton.setEnabled(false);
    }
    
    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Entry Details"));
        
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Label field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel labelLabel = new JLabel("Label:");
        fieldsPanel.add(labelLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        fieldsPanel.add(labelField, gbc);
        
        // Description field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        JLabel descLabel = new JLabel("Description:");
        fieldsPanel.add(descLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        fieldsPanel.add(descriptionField, gbc);
        
        // Sub-labels section
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel subLabelsPanel = createSubLabelsPanel();
        fieldsPanel.add(subLabelsPanel, gbc);
        
        panel.add(fieldsPanel, BorderLayout.CENTER);
        
        // Add listeners for change detection
        labelField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onFieldChanged();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                onFieldChanged();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                onFieldChanged();
            }
        });
        
        descriptionField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onFieldChanged();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                onFieldChanged();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                onFieldChanged();
            }
        });
        
        subLabelsModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                onFieldChanged();
            }
            @Override
            public void intervalRemoved(ListDataEvent e) {
                onFieldChanged();
            }
            @Override
            public void contentsChanged(ListDataEvent e) {
                onFieldChanged();
            }
        });
        
        return panel;
    }
    
    private JPanel createSubLabelsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Sub-labels"));
        
        JScrollPane scroll = new JScrollPane(subLabelsList);
        panel.add(scroll, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        JButton addButton = new JButton("Add");
        JButton removeButton = new JButton("Remove");
        
        addButton.addActionListener(e -> onAddSubLabel());
        removeButton.addActionListener(e -> onRemoveSubLabel());
        
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        panel.add(buttonPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void onFieldChanged() {
        isModified = true;
        validateAndUpdateButtons();
    }
    
    private void validateAndUpdateButtons() {
        String label = labelField.getText().trim();
        boolean hasErrors = false;
        
        // Check for empty label
        if (label.isEmpty()) {
            hasErrors = true;
            labelField.setBorder(errorBorder);
        } else {
            // Check for duplicate label
            boolean isDuplicate = false;
            for (int i = 0; i < listModel.size(); i++) {
                CSVEntry entry = listModel.get(i);
                if (entry != currentEntry && entry.getLabel().equals(label)) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (isDuplicate) {
                hasErrors = true;
                labelField.setBorder(errorBorder);
            } else {
                labelField.setBorder(normalBorder);
            }
        }
        
        saveButton.setEnabled(isModified && !hasErrors);
        cancelButton.setEnabled(isModified);
    }
    
    private void onEntrySelected() {
        if (isModified) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "You have unsaved changes. Do you want to save them?",
                "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );
            
            if (result == JOptionPane.YES_OPTION) {
                if (!saveCurrentEntry()) {
                    entriesList.setSelectedIndex(previousSelection);
                    return;
                }
            } else if (result == JOptionPane.CANCEL_OPTION) {
                entriesList.setSelectedIndex(previousSelection);
                return;
            }
        }
        
        int selectedIndex = entriesList.getSelectedIndex();
        if (selectedIndex >= 0) {
            previousSelection = selectedIndex;
            currentEntry = listModel.get(selectedIndex);
            loadEntryToForm(currentEntry);
            deleteButton.setEnabled(true);
        } else {
            currentEntry = null;
            clearForm();
            deleteButton.setEnabled(false);
        }
        
        isModified = false;
        validateAndUpdateButtons();
    }
    
    private void loadEntryToForm(CSVEntry entry) {
        labelField.setText(entry.getLabel());
        descriptionField.setText(entry.getDescription());
        subLabelsModel.clear();
        for (String subLabel : entry.getSubLabels()) {
            subLabelsModel.addElement(subLabel);
        }
    }
    
    private void clearForm() {
        labelField.setText("");
        descriptionField.setText("");
        subLabelsModel.clear();
    }
    
    private void onNew() {
        if (isModified) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "You have unsaved changes. Do you want to save them?",
                "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );
            
            if (result == JOptionPane.YES_OPTION) {
                if (!saveCurrentEntry()) {
                    return;
                }
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }
        
        previousSelection = entriesList.getSelectedIndex();
        entriesList.clearSelection();
        currentEntry = new CSVEntry();
        clearForm();
        labelField.requestFocusInWindow();
        isModified = false;
        deleteButton.setEnabled(false);
        validateAndUpdateButtons();
    }
    
    private void onDelete() {
        if (currentEntry == null) {
            return;
        }
        
        int result = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete the entry '" + currentEntry.getLabel() + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION
        );
        
        if (result == JOptionPane.YES_OPTION) {
            listModel.removeElement(currentEntry);
            currentEntry = null;
            clearForm();
            isModified = false;
            deleteButton.setEnabled(false);
            validateAndUpdateButtons();
            saveToFile();
        }
    }
    
    private void onSave() {
        if (saveCurrentEntry()) {
            saveToFile();
        }
    }
    
    private boolean saveCurrentEntry() {
        String label = labelField.getText().trim();
        String description = descriptionField.getText().trim();
        
        if (label.isEmpty()) {
            GuiUtils.errorMessage(this, "Validation Error", "Label cannot be empty");
            return false;
        }
        
        // Check for duplicate label
        for (int i = 0; i < listModel.size(); i++) {
            CSVEntry entry = listModel.get(i);
            if (entry != currentEntry && entry.getLabel().equals(label)) {
                GuiUtils.errorMessage(this, "Duplicate Label", "Label '" + label + "' already exists");
                return false;
            }
        }
        
        // Collect sub-labels
        List<String> subLabels = new ArrayList<>();
        for (int i = 0; i < subLabelsModel.size(); i++) {
            String subLabel = subLabelsModel.get(i).trim();
            if (!subLabel.isEmpty()) {
                subLabels.add(subLabel);
            }
        }
        
        // Update or create entry
        if (currentEntry.getLabel().isEmpty() && !listModel.contains(currentEntry)) {
            // New entry
            currentEntry.setLabel(label);
            currentEntry.setDescription(description);
            currentEntry.setSubLabels(subLabels);
            listModel.addElement(currentEntry);
            entriesList.setSelectedValue(currentEntry, true);
        } else {
            // Existing entry
            currentEntry.setLabel(label);
            currentEntry.setDescription(description);
            currentEntry.setSubLabels(subLabels);
            // Refresh the list
            int index = listModel.indexOf(currentEntry);
            listModel.set(index, currentEntry);
        }
        
        isModified = false;
        validateAndUpdateButtons();
        return true;
    }
    
    private void onCancel() {
        if (currentEntry != null && !currentEntry.getLabel().isEmpty()) {
            loadEntryToForm(currentEntry);
        } else {
            // Restore previous selection
            if (previousSelection >= 0 && previousSelection < listModel.size()) {
                entriesList.setSelectedIndex(previousSelection);
            } else {
                clearForm();
                currentEntry = null;
            }
        }
        isModified = false;
        validateAndUpdateButtons();
    }
    
    private void onAddSubLabel() {
        String subLabel = JOptionPane.showInputDialog(this, "Enter sub-label:", "Add Sub-label", JOptionPane.PLAIN_MESSAGE);
        if (subLabel != null) {
            subLabel = subLabel.trim();
            if (subLabel.isEmpty()) {
                GuiUtils.errorMessage(this, "Validation Error", "Sub-label cannot be empty");
                return;
            }
            
            // Check for duplicate in current entry
            for (int i = 0; i < subLabelsModel.size(); i++) {
                if (subLabelsModel.get(i).equals(subLabel)) {
                    GuiUtils.errorMessage(this, "Duplicate Sub-label", "Sub-label '" + subLabel + "' already exists in this entry");
                    return;
                }
            }
            
            // Check for duplicate globally
            String owner = findSubLabelOwner(subLabel);
            if (owner != null) {
                GuiUtils.errorMessage(this, "Duplicate Sub-label", "Sub-label '" + subLabel + "' already exists in entry '" + owner + "'");
                return;
            }
            
            subLabelsModel.addElement(subLabel);
        }
    }
    
    private void onRemoveSubLabel() {
        int selectedIndex = subLabelsList.getSelectedIndex();
        if (selectedIndex >= 0) {
            subLabelsModel.remove(selectedIndex);
        }
    }
    
    private String findSubLabelOwner(String subLabel) {
        for (int i = 0; i < listModel.size(); i++) {
            CSVEntry entry = listModel.get(i);
            if (entry != currentEntry) {
                for (String sl : entry.getSubLabels()) {
                    if (sl.equals(subLabel)) {
                        return entry.getLabel();
                    }
                }
            }
        }
        return null;
    }
    
    private void loadFromFile() {
        if (!csvFile.exists()) {
            log.warn("CSV file does not exist: " + csvFile.getAbsolutePath());
            return;
        }
        
        try {
            CsvReader reader = new CsvReader(csvFile.getAbsolutePath());
            if (!reader.readHeaders()) {
                throw new IOException("Error reading headers from file");
            }
            
            listModel.clear();
            while (reader.readRecord()) {
                String label = reader.get("label").trim();
                String description = reader.get("description").trim();
                List<String> subLabels = new ArrayList<>();
                
                if (reader.getColumnCount() > 2) {
                    for (int i = 2; i < reader.getColumnCount(); i++) {
                        String subLabel = reader.get(i).trim();
                        if (!subLabel.isEmpty()) {
                            subLabels.add(subLabel);
                        }
                    }
                }
                
                CSVEntry entry = new CSVEntry(label, description, subLabels);
                listModel.addElement(entry);
            }
            reader.close();
            
            log.info("Loaded " + listModel.size() + " entries from " + csvFile.getName());
        } catch (Exception e) {
            log.error("Error loading CSV file", e);
            GuiUtils.errorMessage(this, "Load Error", "Error loading file: " + e.getMessage());
        }
    }
    
    private void saveToFile() {
        try {
            CsvWriter writer = new CsvWriter(csvFile.getAbsolutePath());
            
            // Write header
            writer.write("label");
            writer.write("description");
            writer.write("sub-labels");
            writer.endRecord();
            
            // Write entries
            for (int i = 0; i < listModel.size(); i++) {
                CSVEntry entry = listModel.get(i);
                writer.write(entry.getLabel());
                writer.write(entry.getDescription());
                
                // Write sub-labels
                for (String subLabel : entry.getSubLabels()) {
                    writer.write(subLabel);
                }
                
                writer.endRecord();
            }
            
            writer.close();
            log.info("Saved " + listModel.size() + " entries to " + csvFile.getName());
            
            // Call the callback if set
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            
            JOptionPane.showMessageDialog(this, "File saved successfully", "Save Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            log.error("Error saving CSV file", e);
            GuiUtils.errorMessage(this, "Save Error", "Error saving file: " + e.getMessage());
        }
    }
    
    /**
     * Custom cell renderer for the entries list.
     */
    private class EntryListCellRenderer extends JLabel implements ListCellRenderer<CSVEntry> {
        public EntryListCellRenderer() {
            setOpaque(true);
        }
        
        @Override
        public Component getListCellRendererComponent(JList<? extends CSVEntry> list, CSVEntry value, 
                                                     int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                String description = value.getDescription();
                if (description == null || description.isEmpty()) {
                    description = "(no description)";
                }
                setText("<html><b>" + value.getLabel() + "</b><br><small>" + description + "</small></html>");
            }
            
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            
            return this;
        }
    }
}
