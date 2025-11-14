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

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all message bus events.
 * Events can be published locally or across virtual machine instances.
 * 
 * @author José Pinto
 */
public abstract class MessageBusEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String eventId;
    private final Instant timestamp;
    private final String sourceId;
    
    /**
     * Creates a new message bus event.
     * 
     * @param sourceId Identifier of the event source (typically a VM or application instance ID)
     */
    protected MessageBusEvent(String sourceId) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.sourceId = sourceId;
    }
    
    /**
     * Gets the unique identifier of this event.
     * 
     * @return The event ID
     */
    public String getEventId() {
        return eventId;
    }
    
    /**
     * Gets the timestamp when this event was created.
     * 
     * @return The event timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the identifier of the source that created this event.
     * 
     * @return The source ID
     */
    public String getSourceId() {
        return sourceId;
    }
    
    /**
     * Gets the event type name.
     * By default, returns the simple class name.
     * 
     * @return The event type
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
    
    @Override
    public String toString() {
        return String.format("%s[id=%s, source=%s, timestamp=%s]", 
            getEventType(), eventId, sourceId, timestamp);
    }
}
