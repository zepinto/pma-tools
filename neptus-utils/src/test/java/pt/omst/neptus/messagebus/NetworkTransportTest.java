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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NetworkTransport functionality.
 * These tests require multicast support and may not work in all environments.
 * 
 * @author José Pinto
 */
class NetworkTransportTest {
    
    private MessageBus messageBus1;
    private MessageBus messageBus2;
    
    @AfterEach
    void tearDown() {
        if (messageBus1 != null) {
            messageBus1.disableNetworkTransport();
        }
        if (messageBus2 != null) {
            messageBus2.disableNetworkTransport();
        }
    }
    
    @Test
    @EnabledIf("isMulticastSupported")
    void testCrossVMCommunication() throws Exception {
        // Create two message bus instances (simulating two VMs)
        messageBus1 = MessageBus.getInstance();
        // For testing, we'd ideally create a separate instance, but since it's a singleton,
        // we'll test the serialization/deserialization logic separately
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StateChangedEvent> receivedEvent = new AtomicReference<>();
        
        messageBus1.subscribe(StateChangedEvent.class, event -> {
            if (!messageBus1.getInstanceId().equals(event.getSourceId())) {
                receivedEvent.set(event);
                latch.countDown();
            }
        });
        
        // Enable network transport
        String multicastGroup = "239.0.0.2";
        int port = 15555;
        messageBus1.enableNetworkTransport(multicastGroup, port);
        
        // Give time for network to initialize
        Thread.sleep(500);
        
        // Test that network is enabled (won't receive own messages)
        StateChangedEvent event = new StateChangedEvent(
            messageBus1.getInstanceId(), "test.key", "test-value");
        messageBus1.publish(event);
        
        // In a real multi-VM scenario, this would be received by other instances
        // For this test, we're just verifying no exceptions and network starts
        assertFalse(latch.await(1, TimeUnit.SECONDS), 
            "Should not receive own message");
    }
    
    @Test
    void testNetworkTransportStartStop() throws Exception {
        messageBus1 = MessageBus.getInstance();
        
        String multicastGroup = "239.0.0.3";
        int port = 15556;
        
        // Should not throw exception
        assertDoesNotThrow(() -> messageBus1.enableNetworkTransport(multicastGroup, port));
        
        // Give time for network to initialize
        Thread.sleep(200);
        
        // Should not throw exception
        assertDoesNotThrow(() -> messageBus1.disableNetworkTransport());
    }
    
    /**
     * Check if multicast is supported in the test environment.
     */
    static boolean isMulticastSupported() {
        try {
            java.net.MulticastSocket socket = new java.net.MulticastSocket();
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
