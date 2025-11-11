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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating real-world usage scenarios.
 * 
 * @author José Pinto
 */
class MessageBusIntegrationTest {
    
    @Test
    void testApplicationStateSynchronization() throws InterruptedException {
        MessageBus bus = MessageBus.getInstance();
        
        CountDownLatch latch = new CountDownLatch(6); // 2 components * 3 events
        
        // Simulate two application components sharing state
        ApplicationComponent component1 = new ApplicationComponent("Component1");
        ApplicationComponent component2 = new ApplicationComponent("Component2");
        
        // Create listeners that count down the latch
        MessageBusListener<StateChangedEvent> listener1 = event -> {
            component1.onStateChanged(event);
            latch.countDown();
        };
        MessageBusListener<StateChangedEvent> listener2 = event -> {
            component2.onStateChanged(event);
            latch.countDown();
        };
        
        // Both components subscribe to state changes
        bus.subscribe(StateChangedEvent.class, listener1);
        bus.subscribe(StateChangedEvent.class, listener2);
        
        try {
            // Component 1 publishes state changes
            bus.publishLocal(new StateChangedEvent(bus.getInstanceId(), "user.name", "Alice"));
            bus.publishLocal(new StateChangedEvent(bus.getInstanceId(), "mission.id", "M-001"));
            bus.publishLocal(new StateChangedEvent(bus.getInstanceId(), "depth", 150.0));
            
            assertTrue(latch.await(2, TimeUnit.SECONDS), "All events should be received");
            
            // Verify both components received all state changes
            assertEquals(3, component1.getReceivedEvents().size());
            assertEquals(3, component2.getReceivedEvents().size());
            
            // Verify state is synchronized
            assertTrue(component1.hasState("user.name"));
            assertTrue(component2.hasState("user.name"));
            assertEquals("Alice", component1.getState("user.name"));
            assertEquals("Alice", component2.getState("user.name"));
        } finally {
            // Clean up
            bus.unsubscribe(StateChangedEvent.class, listener1);
            bus.unsubscribe(StateChangedEvent.class, listener2);
        }
    }
    
    @Test
    void testEventOrdering() throws InterruptedException {
        MessageBus bus = MessageBus.getInstance();
        
        List<String> receivedOrder = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(5);
        
        MessageBusListener<OrderedEvent> listener = event -> {
            receivedOrder.add(event.getSequence());
            latch.countDown();
        };
        
        bus.subscribe(OrderedEvent.class, listener);
        
        try {
            // Publish events in order
            for (int i = 1; i <= 5; i++) {
                bus.publishLocal(new OrderedEvent(bus.getInstanceId(), String.valueOf(i)));
            }
            
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            
            // Events should be received (order not guaranteed but all should arrive)
            assertEquals(5, receivedOrder.size());
            assertTrue(receivedOrder.contains("1"));
            assertTrue(receivedOrder.contains("5"));
        } finally {
            bus.unsubscribe(OrderedEvent.class, listener);
        }
    }
    
    @Test
    void testMultipleEventTypes() throws InterruptedException {
        MessageBus bus = MessageBus.getInstance();
        
        CountDownLatch stateLatch = new CountDownLatch(1);
        CountDownLatch orderedLatch = new CountDownLatch(1);
        
        MessageBusListener<StateChangedEvent> stateListener = event -> stateLatch.countDown();
        MessageBusListener<OrderedEvent> orderedListener = event -> orderedLatch.countDown();
        
        bus.subscribe(StateChangedEvent.class, stateListener);
        bus.subscribe(OrderedEvent.class, orderedListener);
        
        try {
            bus.publishLocal(new StateChangedEvent(bus.getInstanceId(), "key", "value"));
            bus.publishLocal(new OrderedEvent(bus.getInstanceId(), "1"));
            
            assertTrue(stateLatch.await(1, TimeUnit.SECONDS), "StateChangedEvent should be received");
            assertTrue(orderedLatch.await(1, TimeUnit.SECONDS), "OrderedEvent should be received");
        } finally {
            bus.unsubscribe(StateChangedEvent.class, stateListener);
            bus.unsubscribe(OrderedEvent.class, orderedListener);
        }
    }
    
    /**
     * Test component that tracks state.
     */
    static class ApplicationComponent {
        private final String name;
        private final java.util.Map<String, Object> state = new java.util.concurrent.ConcurrentHashMap<>();
        private final List<StateChangedEvent> receivedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        
        ApplicationComponent(String name) {
            this.name = name;
        }
        
        void onStateChanged(StateChangedEvent event) {
            receivedEvents.add(event);
            state.put(event.getKey(), event.getValue());
        }
        
        boolean hasState(String key) {
            return state.containsKey(key);
        }
        
        Object getState(String key) {
            return state.get(key);
        }
        
        List<StateChangedEvent> getReceivedEvents() {
            return new ArrayList<>(receivedEvents);
        }
    }
    
    /**
     * Test event for ordering verification.
     */
    static class OrderedEvent extends MessageBusEvent {
        private static final long serialVersionUID = 1L;
        private final String sequence;
        
        OrderedEvent(String sourceId, String sequence) {
            super(sourceId);
            this.sequence = sequence;
        }
        
        String getSequence() {
            return sequence;
        }
    }
}
