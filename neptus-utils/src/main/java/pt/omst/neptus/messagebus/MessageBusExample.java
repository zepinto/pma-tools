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

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Example demonstrating MessageBus usage for cross-application communication.
 * 
 * @author José Pinto
 */
@Slf4j
public class MessageBusExample {
    
    /**
     * Simple custom event for the example.
     */
    static class DataUpdateEvent extends MessageBusEvent {
        private static final long serialVersionUID = 1L;
        private final String dataType;
        private final String content;
        
        DataUpdateEvent(String sourceId, String dataType, String content) {
            super(sourceId);
            this.dataType = dataType;
            this.content = content;
        }
        
        public String getDataType() {
            return dataType;
        }
        
        public String getContent() {
            return content;
        }
        
        @Override
        public String toString() {
            return String.format("DataUpdateEvent[type=%s, content=%s]", dataType, content);
        }
    }
    
    /**
     * Demonstrates basic local message bus usage.
     */
    public static void demonstrateLocalMessaging() {
        log.info("=== Local Messaging Example ===");
        
        MessageBus bus = MessageBus.getInstance();
        
        // Subscribe to state changes
        bus.subscribe(StateChangedEvent.class, event -> {
            log.info("State changed: {} = {}", event.getKey(), event.getValue());
        });
        
        // Subscribe to custom events
        bus.subscribe(DataUpdateEvent.class, event -> {
            log.info("Data update received: {}", event);
        });
        
        // Publish events
        bus.publishLocal(new StateChangedEvent(
            bus.getInstanceId(), "temperature", 23.5));
        
        bus.publishLocal(new DataUpdateEvent(
            bus.getInstanceId(), "sonar", "New sonar data available"));
        
        // Wait for async processing
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Demonstrates network-based cross-VM messaging.
     */
    public static void demonstrateNetworkMessaging() {
        log.info("=== Network Messaging Example ===");
        
        MessageBus bus = MessageBus.getInstance();
        
        try {
            // Enable network transport
            String multicastGroup = "239.0.0.1";
            int port = 15000;
            bus.enableNetworkTransport(multicastGroup, port);
            log.info("Network transport enabled on {}:{}", multicastGroup, port);
            
            // Subscribe to events from other instances
            bus.subscribe(StateChangedEvent.class, event -> {
                if (!bus.getInstanceId().equals(event.getSourceId())) {
                    log.info("Received state from {}: {} = {}", 
                        event.getSourceId(), event.getKey(), event.getValue());
                }
            });
            
            // Publish events that will be sent over network
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("priority", "high");
            metadata.put("timestamp", System.currentTimeMillis());
            
            bus.publish(new StateChangedEvent(
                bus.getInstanceId(), "mission.status", "active", metadata));
            
            log.info("Event published to network");
            
            // Keep alive to receive events from other instances
            log.info("Listening for events from other instances... (Press Ctrl+C to stop)");
            Thread.sleep(30000); // Listen for 30 seconds
            
        } catch (Exception e) {
            log.error("Error in network messaging", e);
        } finally {
            bus.disableNetworkTransport();
        }
    }
    
    /**
     * Example application state synchronization.
     */
    public static void demonstrateStateSynchronization() {
        log.info("=== State Synchronization Example ===");
        
        MessageBus bus = MessageBus.getInstance();
        
        // Simulate application state
        Map<String, Object> applicationState = new HashMap<>();
        
        // Listen for state changes from any source
        bus.subscribe(StateChangedEvent.class, event -> {
            applicationState.put(event.getKey(), event.getValue());
            log.info("Application state updated: {} = {} (from {})", 
                event.getKey(), event.getValue(), event.getSourceId());
        });
        
        // Publish state changes
        bus.publishLocal(new StateChangedEvent(bus.getInstanceId(), "user.name", "John Doe"));
        bus.publishLocal(new StateChangedEvent(bus.getInstanceId(), "mission.id", "MISSION-001"));
        bus.publishLocal(new StateChangedEvent(bus.getInstanceId(), "depth", 150.0));
        
        // Wait for processing
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("Final application state: {}", applicationState);
    }
    
    /**
     * Main method to run examples.
     */
    public static void main(String[] args) {
        log.info("MessageBus Examples");
        log.info("==================");
        
        // Run local messaging example
        demonstrateLocalMessaging();
        
        // Run state synchronization example
        demonstrateStateSynchronization();
        
        // Uncomment to test network messaging (requires multiple instances)
        // demonstrateNetworkMessaging();
        
        log.info("Examples completed");
    }
}
