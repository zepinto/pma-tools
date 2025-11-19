//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser.filtering;

import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

/**
 * Custom cell renderer for displaying CheckableItem objects as interactive checkboxes
 * with formatted labels and descriptions.
 */
public class CheckboxListCellRenderer extends JCheckBox implements ListCellRenderer<CheckableItem> {

    public CheckboxListCellRenderer() {
        setOpaque(true);
        setBorder(new EmptyBorder(2, 5, 2, 5));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends CheckableItem> list,
            CheckableItem value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        if (value != null) {
            setSelected(value.isSelected());
            
            // Format the text with HTML for better presentation
            String description = value.getDescription();
            if (description != null && !description.isEmpty()) {
                setText("<html><b>" + value.getLabel() + "</b><br>" +
                       "<small style='color: gray;'>" + description + "</small></html>");
            } else {
                setText("<html><b>" + value.getLabel() + "</b></html>");
            }
        } else {
            setText("");
            setSelected(false);
        }

        // Set background color based on selection/focus
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
