//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import com.csvreader.CsvReader;

import lombok.extern.java.Log;
import pt.lsts.neptus.core.Folders;

/**
 * This class is used to load a list of items from a CSV file.
 * The CSV file must have the following columns:
 * - label: the name of the item
 * - description: a description of the item
 * - sub-labels: optional columns with sub-labels for the item
 * The class provides a method to get a JComboBox with the items.
 * The JComboBox will display the item name and description.
 */
@Log
public class ItemList {

    private static final File CONTACT_TYPES_FILE = new File(Folders.getConfigFolder(), "contact_items.csv");
    private static final File CONFIDENCE_TYPES_FILE = new File(Folders.getConfigFolder(), "confidence_items.csv");

    private static ItemList cachedContactTypes = null;
    private static ItemList cachedConfidenceTypes = null;

    private LinkedHashMap<String, String> categories = new LinkedHashMap<String, String>();
    private ArrayList<String> labels = new ArrayList<String>();
    private LinkedHashMap<String, ArrayList<String>> subLabels = new LinkedHashMap<>();

    /**
     * Load the contact types from the default file.
     * @return a list of contact types
     */
    public static ItemList getContactTypes() {
        if (cachedContactTypes == null) {
            try {
                cachedContactTypes = new ItemList(CONTACT_TYPES_FILE);
            } catch (Exception e) {
                log.warning("Error loading contact types: " + e.getMessage());
                cachedContactTypes = new ItemList();
            }
        }
        return cachedContactTypes;
    }

    /**
     * Load the confidence types from the default file.
     * @return a list of confidence types
     */
    public static ItemList getConfidenceTypes() {
        if (cachedConfidenceTypes == null) {
            try {
                cachedConfidenceTypes = new ItemList(CONFIDENCE_TYPES_FILE);
            } catch (Exception e) {
                log.warning("Error loading confidence types: " + e.getMessage());
                cachedConfidenceTypes = new ItemList();
            }
        }
        return cachedConfidenceTypes;
    }

    /**
     * Reload the contact types from the default file.
     * This method should be called after the CSV file has been modified.
     */
    public static void reloadContactTypes() {
        try {
            cachedContactTypes = new ItemList(CONTACT_TYPES_FILE);
            log.info("Contact types reloaded successfully");
        } catch (Exception e) {
            log.warning("Error reloading contact types: " + e.getMessage());
            cachedContactTypes = new ItemList();
        }
    }

    /**
     * Reload the confidence types from the default file.
     * This method should be called after the CSV file has been modified.
     */
    public static void reloadConfidenceTypes() {
        try {
            cachedConfidenceTypes = new ItemList(CONFIDENCE_TYPES_FILE);
            log.info("Confidence types reloaded successfully");
        } catch (Exception e) {
            log.warning("Error reloading confidence types: " + e.getMessage());
            cachedConfidenceTypes = new ItemList();
        }
    }

    public ItemList() {
    }

    /**
     * Load the items from the given file.
     * @param categoriesFile the file to load the items from
     * @throws Exception if an error occurs while loading the items
     */
    public ItemList(File categoriesFile) throws Exception {
        if (!categoriesFile.exists()) {
            throw new Exception("File not found: " + categoriesFile.getAbsolutePath());
        }
        CsvReader reader = new CsvReader(categoriesFile.getAbsolutePath());
        if (!reader.readHeaders()) {
            throw new Exception("Error reading headers from file: " + categoriesFile.getAbsolutePath());
        }

        while (reader.readRecord()) {
            String label = reader.get("label");
            String description = reader.get("description");
            if (reader.getColumnCount() > 2) {
                ArrayList<String> subLabels = new ArrayList<>();
                for (int i = 2; i < reader.getColumnCount(); i++) {
                    subLabels.add(reader.get(i));
                }
                this.subLabels.put(label, subLabels);
            }
            categories.put(label, description);
            labels.add(label);
        }
    }

    /**
     * Get a JComboBox with the items.
     * @return a JComboBox with the items
     */
    public JComboBox<String> getComboBox() {
        JComboBox<String> comboBox = new JComboBox<String>();
        comboBox.setRenderer(new ListCellRenderer<String>() {
            private final JLabel label = new JLabel();
            @Override
            public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value != null) {
                    label.setText("<html><b>" + value + "</b><br><small>" + getDescription(value) + "</small></html>");
                }
                label.setOpaque(true);
                if (isSelected) {
                    label.setBackground(list.getSelectionBackground());
                    label.setForeground(list.getSelectionForeground());
                } else {
                    label.setBackground(list.getBackground());
                    label.setForeground(list.getForeground());
                }
                return label;
            }
        });
        for (String label : labels) {
            comboBox.addItem(label);
        }
        return comboBox;
    }

    /**
     * Get the description of the given item.
     * @param label the item label
     * @return the item description
     */
    public String getDescription(String label) {
        return categories.get(label);
    }

    /**
     * Get the sub-labels of the given item.
     * @return the sub-labels of the given item
     */
    public List<String> getLabels() {
        return labels;
    }
}
