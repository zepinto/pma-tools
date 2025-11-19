//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser.filtering;

/**
 * Represents an item that can be checked/unchecked in a checkbox list.
 * Used for filter selections in the contact filter panel.
 */
public class CheckableItem {
    private final String label;
    private final String description;
    private boolean selected;

    /**
     * Creates a new checkable item.
     * 
     * @param label The main label text
     * @param description Optional description text (can be null or empty)
     */
    public CheckableItem(String label, String description) {
        this.label = label;
        this.description = description;
        this.selected = false;
    }

    /**
     * Creates a new checkable item with initial selection state.
     * 
     * @param label The main label text
     * @param description Optional description text (can be null or empty)
     * @param selected Initial selection state
     */
    public CheckableItem(String label, String description, boolean selected) {
        this.label = label;
        this.description = description;
        this.selected = selected;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return label;
    }
}
