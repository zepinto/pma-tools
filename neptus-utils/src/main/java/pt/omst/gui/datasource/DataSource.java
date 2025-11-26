//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import javax.swing.Icon;

/**
 * Interface for data sources that can be managed by the DataSourceManagerPanel.
 * Implementations can represent different types of data sources such as folders,
 * databases, or any other source of data.
 */
public interface DataSource {
    
    /**
     * Gets the display name for this data source.
     * This is the primary text shown on the chip.
     * 
     * @return the display name (e.g., "C:\Users\data" or "Production DB")
     */
    String getDisplayName();
    
    /**
     * Gets the detailed tooltip text for this data source.
     * This information is shown when hovering over the chip.
     * 
     * @return detailed information about the source
     */
    String getTooltipText();
    
    /**
     * Gets the icon representing this data source type.
     * 
     * @return an icon for the source type, or null if no icon is desired
     */
    Icon getIcon();
    
    /**
     * Returns whether this data source can be deleted/removed by the user.
     * Some data sources (e.g., RasterfallDataSource) cannot be removed.
     * 
     * @return true if the data source can be deleted, false otherwise
     */
    default boolean isDeletable() {
        return true;
    }
}
