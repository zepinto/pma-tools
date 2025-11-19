//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.reports;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

/**
 * Dialog for generating PDF reports from selected contacts.
 */
@Slf4j
public class GenerateReportDialog extends JDialog {

    private final JTextField reportTitleField;
    private final JTextField missionNameField;
    private final JTextField outputPathField;
    private final JTable contactTable;
    private final ContactTableModel tableModel;
    private final JButton generateButton;
    private final JButton cancelButton;
    private final List<CompressedContact> allContacts;

    public GenerateReportDialog(Window owner, ContactCollection contactCollection) {
        super(owner, "Generate Contact Report", ModalityType.APPLICATION_MODAL);

        // Capture filtered contacts snapshot
        allContacts = contactCollection.getFilteredContacts();

        // Create main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with report configuration
        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Report title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        configPanel.add(new JLabel("Report Title:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        reportTitleField = new JTextField("Contact Report", 30);
        configPanel.add(reportTitleField, gbc);

        // Mission name
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        configPanel.add(new JLabel("Mission Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        missionNameField = new JTextField(30);
        configPanel.add(missionNameField, gbc);

        // Output path
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        configPanel.add(new JLabel("Output PDF:"), gbc);

        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        outputPathField = new JTextField(30);
        pathPanel.add(outputPathField, BorderLayout.CENTER);

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseOutputFile());
        pathPanel.add(browseButton, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        configPanel.add(pathPanel, gbc);

        mainPanel.add(configPanel, BorderLayout.NORTH);

        // Center panel with contact table
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("Select Contacts to Include"));

        // Selection buttons
        JPanel selectionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllButton = new JButton("Select All");
        selectAllButton.addActionListener(e -> selectAll(true));
        JButton deselectAllButton = new JButton("Deselect All");
        deselectAllButton.addActionListener(e -> selectAll(false));
        selectionButtonsPanel.add(selectAllButton);
        selectionButtonsPanel.add(deselectAllButton);
        selectionButtonsPanel.add(new JLabel(String.format(" (%d contacts available)", allContacts.size())));

        tablePanel.add(selectionButtonsPanel, BorderLayout.NORTH);

        // Create table
        tableModel = new ContactTableModel(allContacts);
        contactTable = new JTable(tableModel);
        contactTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        contactTable.getColumnModel().getColumn(0).setMaxWidth(50);
        contactTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        contactTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        contactTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        JScrollPane scrollPane = new JScrollPane(contactTable);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(tablePanel, BorderLayout.CENTER);

        // Bottom panel with buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        generateButton = new JButton("Generate");
        generateButton.addActionListener(e -> generateReport());
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(generateButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(owner);
    }

    private void browseOutputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save PDF Report");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        FileNameExtensionFilter filter = new FileNameExtensionFilter("PDF Files", "pdf");
        fileChooser.setFileFilter(filter);

        // Set default filename
        String defaultName = "ContactsReport_" + System.currentTimeMillis() + ".pdf";
        fileChooser.setSelectedFile(new File(defaultName));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".pdf")) {
                path += ".pdf";
            }
            outputPathField.setText(path);
        }
    }

    private void selectAll(boolean selected) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(selected, i, 0);
        }
        tableModel.fireTableDataChanged();
    }

    private void generateReport() {
        // Validate inputs
        String reportTitle = reportTitleField.getText().trim();
        if (reportTitle.isEmpty()) {
            GuiUtils.errorMessage(this, "Validation Error", "Please enter a report title.");
            return;
        }

        String missionName = missionNameField.getText().trim();
        if (missionName.isEmpty()) {
            GuiUtils.errorMessage(this, "Validation Error", "Please enter a mission name.");
            return;
        }

        String outputPath = outputPathField.getText().trim();
        if (outputPath.isEmpty()) {
            GuiUtils.errorMessage(this, "Validation Error", "Please select an output file.");
            return;
        }

        // Collect selected contacts
        List<CompressedContact> selectedContacts = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if ((Boolean) tableModel.getValueAt(i, 0)) {
                selectedContacts.add(allContacts.get(i));
            }
        }

        if (selectedContacts.isEmpty()) {
            GuiUtils.errorMessage(this, "No Selection", "Please select at least one contact to include in the report.");
            return;
        }

        // Disable buttons during generation
        generateButton.setEnabled(false);
        cancelButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Generate PDF in background
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private Exception exception = null;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    log.info("Generating report with {} contacts", selectedContacts.size());
                    
                    // Convert CompressedContacts to SidescanContacts
                    List<SidescanContact> sidescanContacts = new ArrayList<>();
                    for (CompressedContact cc : selectedContacts) {
                        sidescanContacts.add(SidescanContact.fromCompressedContact(cc));
                    }

                    // Create report data
                    ContactReportGenerator.ReportData reportData = 
                        new ContactReportGenerator.ReportData(reportTitle, missionName, sidescanContacts);

                    // Generate PDF
                    ContactReportGenerator generator = new ContactReportGenerator();
                    generator.generateReport("/templates/report-template.html", outputPath, reportData);
                    
                    log.info("Report generated successfully: {}", outputPath);
                } catch (Exception e) {
                    log.error("Error generating report", e);
                    exception = e;
                }
                return null;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                generateButton.setEnabled(true);
                cancelButton.setEnabled(true);

                if (exception != null) {
                    GuiUtils.errorMessage(GenerateReportDialog.this, "Generation Error", 
                        "Failed to generate report: " + exception.getMessage());
                } else {
                    // Ask if user wants to open the report
                    boolean openReport = GuiUtils.confirmDialog(GenerateReportDialog.this, "Success", 
                        "Report generated successfully.\n\nOpen the report now?");
                    
                    if (openReport) {
                        try {
                            File pdfFile = new File(outputPath);
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                Desktop.getDesktop().open(pdfFile);
                            } else {
                                GuiUtils.infoMessage(GenerateReportDialog.this, "Cannot Open", 
                                    "Desktop operations not supported. File saved at:\n" + outputPath);
                            }
                        } catch (Exception e) {
                            log.error("Error opening PDF", e);
                            GuiUtils.errorMessage(GenerateReportDialog.this, "Open Error", 
                                "Could not open the PDF file: " + e.getMessage());
                        }
                    }
                    
                    dispose();
                }
            }
        };

        worker.execute();
    }

    /**
     * Table model for contact selection.
     */
    private static class ContactTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Select", "Label", "Classification", "Confidence"};
        private final List<CompressedContact> contacts;
        private final boolean[] selected;

        public ContactTableModel(List<CompressedContact> contacts) {
            this.contacts = contacts;
            this.selected = new boolean[contacts.size()];
            // Select all by default
            for (int i = 0; i < selected.length; i++) {
                selected[i] = true;
            }
        }

        @Override
        public int getRowCount() {
            return contacts.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // Only checkbox column is editable
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CompressedContact contact = contacts.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return selected[rowIndex];
                case 1:
                    return contact.getLabel() != null ? contact.getLabel() : "Unknown";
                case 2:
                    return contact.getClassification() != null ? contact.getClassification() : "Unclassified";
                case 3:
                    // Extract confidence from annotations
                    if (contact.getContact().getObservations() != null) {
                        for (var obs : contact.getContact().getObservations()) {
                            if (obs.getAnnotations() != null) {
                                for (var ann : obs.getAnnotations()) {
                                    if (ann.getAnnotationType() == pt.omst.rasterlib.AnnotationType.CLASSIFICATION 
                                        && ann.getConfidence() != null) {
                                        return String.valueOf(ann.getConfidence().intValue());
                                    }
                                }
                            }
                        }
                    }
                    return "";
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                selected[rowIndex] = (Boolean) value;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
