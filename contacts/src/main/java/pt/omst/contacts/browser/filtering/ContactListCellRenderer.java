//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser.filtering;

import java.awt.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.contacts.CompressedContact;

/**
 * Custom cell renderer for displaying CompressedContact objects in a list
 * with formatted contact information including name, classification, confidence, and timestamp.
 */
public class ContactListCellRenderer extends JLabel implements ListCellRenderer<CompressedContact> {

    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public ContactListCellRenderer() {
        setOpaque(true);
        setBorder(new EmptyBorder(3, 5, 3, 5));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends CompressedContact> list,
            CompressedContact contact,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        if (contact != null) {
            StringBuilder html = new StringBuilder("<html>");
            
            // Contact name (bold)
            String label = contact.getContact().getLabel();
            if (label == null || label.isEmpty()) {
                label = "Unnamed Contact";
            }
            html.append("<b>").append(label).append("</b><br>");
            
            // Classification and confidence (small font)
            html.append("<small>");
            
            String classification = contact.getClassification();
            if (classification != null && !classification.isEmpty()) {
                html.append("<span style='color: #0066cc;'>").append(classification).append("</span>");
            } else {
                html.append("<span style='color: gray;'>Unknown</span>");
            }
            
            // Try to get confidence
            String confidence = getConfidenceString(contact);
            if (confidence != null) {
                html.append(" | Conf: ").append(confidence);
            }
            
            // Timestamp
            Instant timestamp = Instant.ofEpochMilli(contact.getTimestamp());
            html.append("<br>").append(TIME_FORMATTER.format(timestamp));
            
            html.append("</small></html>");
            
            setText(html.toString());
        } else {
            setText("");
        }

        // Set background color based on selection
        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

        return this;
    }

    /**
     * Extract confidence level from contact annotations.
     */
    private String getConfidenceString(CompressedContact contact) {
        if (contact.getContact().getObservations() != null) {
            return contact.getContact().getObservations().stream()
                .filter(obs -> obs.getAnnotations() != null)
                .flatMap(obs -> obs.getAnnotations().stream())
                .filter(ann -> ann.getAnnotationType() == AnnotationType.CLASSIFICATION)
                .filter(ann -> ann.getConfidence() != null)
                .findFirst()
                .map(ann -> {
                    int conf = ann.getConfidence().intValue();
                    return switch (conf) {
                        case 0 -> "Unknown";
                        case 1 -> "Low";
                        case 2 -> "Medium";
                        case 3 -> "High";
                        default -> String.valueOf(conf);
                    };
                })
                .orElse(null);
        }
        return null;
    }
}
