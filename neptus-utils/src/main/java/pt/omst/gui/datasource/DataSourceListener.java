//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

/**
 * Listener interface for data source changes.
 */
public interface DataSourceListener {
    
    /**
     * Called when a data source is added.
     * 
     * @param event the event containing the added data source
     */
    void sourceAdded(DataSourceEvent event);
    
    /**
     * Called when a data source is removed.
     * 
     * @param event the event containing the removed data source
     */
    void sourceRemoved(DataSourceEvent event);
}
