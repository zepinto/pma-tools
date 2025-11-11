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

/**
 * Functional interface for message bus event listeners.
 * 
 * @param <T> The type of event to listen for
 * @author José Pinto
 */
@FunctionalInterface
public interface MessageBusListener<T extends MessageBusEvent> {
    
    /**
     * Called when an event is received.
     * 
     * @param event The received event
     */
    void onEvent(T event);
}
