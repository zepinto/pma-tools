//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser.filtering;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.Folders;
import pt.omst.contacts.ItemList;
import pt.omst.rasterlib.contacts.CompressedContact;

/**
 * Vertical panel for filtering and listing contacts.
 * Contains checkbox filters for classification, confidence, and labels,
 * plus a scrollable list of matching contacts.
 */
@Slf4j
public class ContactFilterPanel extends JPanel {

    private static final int DEBOUNCE_DELAY_MS = 250;
    private static final File LABELS_FILE = new File(Folders.getConfigFolder(), "labels.csv");

    private final JList<CheckableItem> classificationList;
    private final JList<CheckableItem> confidenceList;
    private final JList<CheckableItem> labelList;
    private final JList<CompressedContact> contactList;
    private final JLabel contactCountLabel;
    
    private final DefaultListModel<CheckableItem> classificationModel;
    private final DefaultListModel<CheckableItem> confidenceModel;
    private final DefaultListModel<CheckableItem> labelModel;
    private final DefaultListModel<CompressedContact> contactModel;
    
    private final List<ContactFilterListener> listeners;
    private final Timer debounceTimer;

    public ContactFilterPanel() {
        setLayout(new BorderLayout(0, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        listeners = new ArrayList<>();
        
        // Create debounce timer for filter changes
        debounceTimer = new Timer(DEBOUNCE_DELAY_MS, e -> notifyFilterChanged());
        debounceTimer.setRepeats(false);
        
        // Initialize list models
        classificationModel = new DefaultListModel<>();
        confidenceModel = new DefaultListModel<>();
        labelModel = new DefaultListModel<>();
        contactModel = new DefaultListModel<>();
        
        // Create filter panel (top section)
        JPanel filtersPanel = new JPanel();
        filtersPanel.setLayout(new BoxLayout(filtersPanel, BoxLayout.Y_AXIS));
        
        // Classification filter section
        classificationList = createCheckboxList(classificationModel);
        loadClassificationItems();
        JPanel classificationPanel = createFilterSection("Classification", classificationList);
        filtersPanel.add(classificationPanel);
        filtersPanel.add(Box.createVerticalStrut(5));
        
        // Confidence filter section
        confidenceList = createCheckboxList(confidenceModel);
        loadConfidenceItems();
        JPanel confidencePanel = createFilterSection("Confidence", confidenceList);
        filtersPanel.add(confidencePanel);
        filtersPanel.add(Box.createVerticalStrut(5));
        
        // Labels filter section
        labelList = createCheckboxList(labelModel);
        loadLabelItems();
        JPanel labelPanel = createFilterSection("Labels", labelList);
        filtersPanel.add(labelPanel);
        
        // Contact count label
        contactCountLabel = new JLabel("0 contacts");
        contactCountLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        filtersPanel.add(Box.createVerticalStrut(5));
        filtersPanel.add(contactCountLabel);
        
        // Create contact list (center section)
        contactList = new JList<>(contactModel);
        contactList.setCellRenderer(new ContactListCellRenderer());
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contactList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CompressedContact selected = contactList.getSelectedValue();
                if (selected != null) {
                    notifyContactSelected(selected);
                }
            }
        });
        
        JScrollPane contactScrollPane = new JScrollPane(contactList);
        contactScrollPane.setBorder(BorderFactory.createTitledBorder("Contacts"));
        
        // Wrap filters in scroll pane
        JScrollPane filtersScrollPane = new JScrollPane(filtersPanel);
        filtersScrollPane.setBorder(BorderFactory.createEmptyBorder());
        filtersScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Assemble layout
        add(filtersScrollPane, BorderLayout.NORTH);
        add(contactScrollPane, BorderLayout.CENTER);
    }

    /**
     * Creates a checkbox list with custom renderer and mouse listener.
     */
    private JList<CheckableItem> createCheckboxList(DefaultListModel<CheckableItem> model) {
        JList<CheckableItem> list = new JList<>(model);
        list.setCellRenderer(new CheckboxListCellRenderer());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Handle mouse clicks to toggle checkbox state
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) {
                    CheckableItem item = model.getElementAt(index);
                    item.setSelected(!item.isSelected());
                    list.repaint();
                    onFilterChanged();
                }
            }
        });
        
        return list;
    }

    /**
     * Creates a filter section with title, checkbox list, and Select/Clear All buttons.
     */
    private JPanel createFilterSection(String title, JList<CheckableItem> list) {
        JPanel panel = new JPanel(new BorderLayout(2, 2));
        TitledBorder border = BorderFactory.createTitledBorder(title);
        panel.setBorder(border);
        
        // Create scroll pane for the list
        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(200, 80));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Create buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        
        JButton selectAllButton = new JButton("All");
        selectAllButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
        selectAllButton.addActionListener(e -> {
            DefaultListModel<CheckableItem> model = (DefaultListModel<CheckableItem>) list.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                model.getElementAt(i).setSelected(true);
            }
            list.repaint();
            onFilterChanged();
        });
        
        JButton clearAllButton = new JButton("Clear");
        clearAllButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
        clearAllButton.addActionListener(e -> {
            DefaultListModel<CheckableItem> model = (DefaultListModel<CheckableItem>) list.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                model.getElementAt(i).setSelected(false);
            }
            list.repaint();
            onFilterChanged();
        });
        
        buttonsPanel.add(selectAllButton);
        buttonsPanel.add(clearAllButton);
        
        panel.add(buttonsPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Loads classification items from ItemList.
     */
    private void loadClassificationItems() {
        ItemList itemList = ItemList.getContactTypes();
        for (String label : itemList.getLabels()) {
            String description = itemList.getDescription(label);
            classificationModel.addElement(new CheckableItem(label, description));
        }
    }

    /**
     * Loads confidence items from ItemList.
     */
    private void loadConfidenceItems() {
        ItemList itemList = ItemList.getConfidenceTypes();
        for (String label : itemList.getLabels()) {
            String description = itemList.getDescription(label);
            confidenceModel.addElement(new CheckableItem(label, description));
        }
    }

    /**
     * Loads label items from labels CSV file.
     */
    private void loadLabelItems() {
        try {
            ItemList itemList = new ItemList(LABELS_FILE);
            for (String label : itemList.getLabels()) {
                String description = itemList.getDescription(label);
                labelModel.addElement(new CheckableItem(label, description));
            }
        } catch (Exception e) {
            log.warn("Error loading labels from {}: {}", LABELS_FILE, e.getMessage());
        }
    }

    /**
     * Called when filter checkboxes change. Starts debounce timer.
     */
    private void onFilterChanged() {
        debounceTimer.restart();
    }

    /**
     * Gets the selected classification types.
     * @return Set of selected classification labels, empty if none selected
     */
    public Set<String> getSelectedClassifications() {
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < classificationModel.getSize(); i++) {
            CheckableItem item = classificationModel.getElementAt(i);
            if (item.isSelected()) {
                selected.add(item.getLabel());
            }
        }
        return selected;
    }

    /**
     * Gets the selected confidence levels.
     * @return Set of selected confidence labels, empty if none selected
     */
    public Set<String> getSelectedConfidences() {
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < confidenceModel.getSize(); i++) {
            CheckableItem item = confidenceModel.getElementAt(i);
            if (item.isSelected()) {
                selected.add(item.getLabel());
            }
        }
        return selected;
    }

    /**
     * Gets the selected labels (AnnotationType.LABEL annotations).
     * @return Set of selected label names, empty if none selected
     */
    public Set<String> getSelectedLabels() {
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < labelModel.getSize(); i++) {
            CheckableItem item = labelModel.getElementAt(i);
            if (item.isSelected()) {
                selected.add(item.getLabel());
            }
        }
        return selected;
    }

    /**
     * Sets the list of contacts to display.
     * @param contacts List of contacts matching current filters
     */
    public void setContacts(List<CompressedContact> contacts) {
        SwingUtilities.invokeLater(() -> {
            contactModel.clear();
            if (contacts != null) {
                for (CompressedContact contact : contacts) {
                    contactModel.addElement(contact);
                }
            }
            updateContactCount();
        });
    }

    /**
     * Updates the contact count label.
     */
    private void updateContactCount() {
        int count = contactModel.getSize();
        contactCountLabel.setText(count + (count == 1 ? " contact" : " contacts"));
    }

    /**
     * Sets the selected state of classification items.
     * @param classifications Set of classification labels to select
     */
    public void setSelectedClassifications(Set<String> classifications) {
        for (int i = 0; i < classificationModel.getSize(); i++) {
            CheckableItem item = classificationModel.getElementAt(i);
            item.setSelected(classifications.contains(item.getLabel()));
        }
        classificationList.repaint();
    }

    /**
     * Sets the selected state of confidence items.
     * @param confidences Set of confidence labels to select
     */
    public void setSelectedConfidences(Set<String> confidences) {
        for (int i = 0; i < confidenceModel.getSize(); i++) {
            CheckableItem item = confidenceModel.getElementAt(i);
            item.setSelected(confidences.contains(item.getLabel()));
        }
        confidenceList.repaint();
    }

    /**
     * Sets the selected state of label items.
     * @param labels Set of label names to select
     */
    public void setSelectedLabels(Set<String> labels) {
        for (int i = 0; i < labelModel.getSize(); i++) {
            CheckableItem item = labelModel.getElementAt(i);
            item.setSelected(labels.contains(item.getLabel()));
        }
        labelList.repaint();
    }

    /**
     * Adds a filter listener.
     */
    public void addFilterListener(ContactFilterListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a filter listener.
     */
    public void removeFilterListener(ContactFilterListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies listeners that filters have changed.
     */
    private void notifyFilterChanged() {
        for (ContactFilterListener listener : listeners) {
            listener.onFilterChanged();
        }
    }

    /**
     * Notifies listeners that a contact has been selected.
     */
    private void notifyContactSelected(CompressedContact contact) {
        for (ContactFilterListener listener : listeners) {
            listener.onContactSelected(contact);
        }
    }
}
