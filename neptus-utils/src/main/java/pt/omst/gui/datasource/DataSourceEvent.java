//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import java.util.EventObject;

import lombok.Getter;

/**
 * Event fired when a data source is added or removed.
 */
@Getter
public class DataSourceEvent extends EventObject {
    
    private final DataSource dataSource;
    
    /**
     * Creates a new DataSourceEvent.
     * 
     * @param source the component that fired the event
     * @param dataSource the data source that was added or removed
     */
    public DataSourceEvent(Object source, DataSource dataSource) {
        super(source);
        this.dataSource = dataSource;
    }
}
