package pt.omst.contacts.browser;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterlib.Contact;

/**
 * Panel for displaying contact details in a form layout.
 */
@Slf4j
public class ContactDetailsFormPanel extends JPanel {
    
    private final JLabel nameLabel;
    private final JLabel timestampLabel;
    private final JLabel positionLabel;
    private final JLabel confidenceLabel;
    private final JLabel typeLabel;
    private final JLabel measurementsLabel;
    private final JLabel descriptionLabel;
    
    private Contact currentContact;
    
    public ContactDetailsFormPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Initialize labels
        nameLabel = new JLabel("—");
        timestampLabel = new JLabel("—");
        positionLabel = new JLabel("—");
        confidenceLabel = new JLabel("—");
        typeLabel = new JLabel("—");
        measurementsLabel = new JLabel("—");
        descriptionLabel = new JLabel("—");
        
        // Make value labels wrap text
        nameLabel.setVerticalAlignment(SwingConstants.TOP);
        descriptionLabel.setVerticalAlignment(SwingConstants.TOP);
        
        int row = 0;
        
        // Name
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        add(new JLabel("Name:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(nameLabel, gbc);
        row++;
        
        // Timestamp
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        add(new JLabel("Timestamp:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(timestampLabel, gbc);
        row++;
        
        // Position
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        add(new JLabel("Position:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(positionLabel, gbc);
        row++;
        
        // Type
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        add(new JLabel("Type:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(typeLabel, gbc);
        row++;
        
        // Confidence
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        add(new JLabel("Confidence:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(confidenceLabel, gbc);
        row++;
        
        // Measurements
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        add(new JLabel("Measurements:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(measurementsLabel, gbc);
        row++;
        
        // Description
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        add(new JLabel("Description:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(descriptionLabel, gbc);
        
        // Show empty state initially
        showEmptyState();
    }
    
    /**
     * Sets the contact to display.
     */
    public void setContact(Contact contact) {
        this.currentContact = contact;
        
        if (contact == null) {
            showEmptyState();
            return;
        }
        
        // Update labels with contact data
        nameLabel.setText(contact.getLabel() != null ? contact.getLabel() : "—");
        
        if (contact.getObservations() != null && !contact.getObservations().isEmpty()) {
            var firstObs = contact.getObservations().get(0);
            
            // Timestamp
            if (firstObs.getTimestamp() != null) {
                timestampLabel.setText(firstObs.getTimestamp().toString());
            } else {
                timestampLabel.setText("—");
            }
            
            // Position
            if (firstObs.getLatitude() != 0.0 || firstObs.getLongitude() != 0.0) {
                positionLabel.setText(String.format("%.6f, %.6f", 
                    firstObs.getLatitude(),
                    firstObs.getLongitude()));
            } else {
                positionLabel.setText("—");
            }
            
            // Extract classification and description from annotations
            String typeText = "—";
            String confidenceText = "—";
            String descriptionText = "—";
            
            if (firstObs.getAnnotations() != null) {
                for (var annot : firstObs.getAnnotations()) {
                    if (annot.getAnnotationType() != null) {
                        switch (annot.getAnnotationType()) {
                            case CLASSIFICATION:
                                if (annot.getCategory() != null) {
                                    typeText = annot.getCategory();
                                }
                                if (annot.getConfidence() != null) {
                                    confidenceText = String.format("%.1f%%", annot.getConfidence() * 100);
                                }
                                break;
                            case TEXT:
                                if (annot.getText() != null) {
                                    descriptionText = annot.getText();
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
            
            typeLabel.setText(typeText);
            confidenceLabel.setText(confidenceText);
            descriptionLabel.setText("<html>" + descriptionText.replace("\n", "<br>") + "</html>");
            
            // Measurements - extract from annotations with MEASUREMENT type
            StringBuilder measurements = new StringBuilder();
            int measurementCount = 0;
            if (firstObs.getAnnotations() != null) {
                for (var annot : firstObs.getAnnotations()) {
                    if (annot.getAnnotationType() == pt.omst.rasterlib.AnnotationType.MEASUREMENT 
                        && annot.getMeasurementType() != null && annot.getValue() != null) {
                        if (measurementCount > 0) {
                            measurements.append(", ");
                        }
                        measurements.append(annot.getMeasurementType()).append(": ")
                                   .append(String.format("%.2f", annot.getValue()));
                        measurementCount++;
                    }
                }
            }
            
            if (measurementCount > 0) {
                measurementsLabel.setText(measurements.toString());
            } else {
                measurementsLabel.setText("—");
            }
        } else {
            showEmptyState();
        }
        
        revalidate();
        repaint();
    }
    
    /**
     * Shows empty state when no contact is selected.
     */
    private void showEmptyState() {
        nameLabel.setText("—");
        timestampLabel.setText("—");
        positionLabel.setText("—");
        confidenceLabel.setText("—");
        typeLabel.setText("—");
        measurementsLabel.setText("—");
        descriptionLabel.setText("<html><i>No contact selected</i></html>");
    }
    
    /**
     * Gets the currently displayed contact.
     */
    public Contact getContact() {
        return currentContact;
    }
}