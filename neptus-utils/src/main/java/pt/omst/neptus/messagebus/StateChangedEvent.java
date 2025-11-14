//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
/*
 * Copyright (c) 2004-2025 OceanScan-MST
 * All rights reserved.
 *
 * This file is part of Neptus Utilities.
 */

package pt.omst.neptus.messagebus;

import java.util.HashMap;
import java.util.Map;

/**
 * Event for synchronizing state changes across VM instances.
 * Can be used to keep application state synchronized across multiple running instances.
 * 
 * @author José Pinto
 */
public class StateChangedEvent extends MessageBusEvent {
    
    private static final long serialVersionUID = 1L;
    
    private final String key;
    private final Object value;
    private final Map<String, Object> metadata;
    
    /**
     * Creates a state changed event.
     * 
     * @param sourceId The source identifier
     * @param key The state key that changed
     * @param value The new value
     */
    public StateChangedEvent(String sourceId, String key, Object value) {
        super(sourceId);
        this.key = key;
        this.value = value;
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates a state changed event with metadata.
     * 
     * @param sourceId The source identifier
     * @param key The state key that changed
     * @param value The new value
     * @param metadata Additional metadata
     */
    public StateChangedEvent(String sourceId, String key, Object value, Map<String, Object> metadata) {
        super(sourceId);
        this.key = key;
        this.value = value;
        this.metadata = new HashMap<>(metadata);
    }
    
    /**
     * Gets the state key.
     * 
     * @return The key
     */
    public String getKey() {
        return key;
    }
    
    /**
     * Gets the state value.
     * 
     * @return The value
     */
    public Object getValue() {
        return value;
    }
    
    /**
     * Gets the metadata.
     * 
     * @return The metadata map
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    @Override
    public String toString() {
        return String.format("StateChangedEvent[key=%s, value=%s, source=%s]", 
            key, value, getSourceId());
    }
}
