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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageBus functionality.
 * 
 * @author José Pinto
 */
class MessageBusTest {
    
    private MessageBus messageBus;
    
    @BeforeEach
    void setUp() {
        messageBus = MessageBus.getInstance();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up but don't shutdown the singleton
    }
    
    @Test
    void testInstanceIdGeneration() {
        assertNotNull(messageBus.getInstanceId());
        assertFalse(messageBus.getInstanceId().isEmpty());
    }
    
    @Test
    void testSubscribeAndPublish() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TestEvent> receivedEvent = new AtomicReference<>();
        
        messageBus.subscribe(TestEvent.class, event -> {
            receivedEvent.set(event);
            latch.countDown();
        });
        
        TestEvent testEvent = new TestEvent(messageBus.getInstanceId(), "test-data");
        messageBus.publishLocal(testEvent);
        
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Event should be received within timeout");
        assertNotNull(receivedEvent.get());
        assertEquals("test-data", receivedEvent.get().getData());
    }
    
    @Test
    void testMultipleSubscribers() throws InterruptedException {
        int subscriberCount = 5;
        CountDownLatch latch = new CountDownLatch(subscriberCount);
        
        for (int i = 0; i < subscriberCount; i++) {
            messageBus.subscribe(TestEvent.class, event -> latch.countDown());
        }
        
        messageBus.publishLocal(new TestEvent(messageBus.getInstanceId(), "test"));
        
        assertTrue(latch.await(2, TimeUnit.SECONDS), "All subscribers should receive event");
    }
    
    @Test
    void testUnsubscribe() throws InterruptedException {
        AtomicInteger eventCount = new AtomicInteger(0);
        MessageBusListener<TestEvent> listener = event -> eventCount.incrementAndGet();
        
        messageBus.subscribe(TestEvent.class, listener);
        messageBus.publishLocal(new TestEvent(messageBus.getInstanceId(), "test1"));
        Thread.sleep(100); // Wait for async dispatch
        
        messageBus.unsubscribe(TestEvent.class, listener);
        messageBus.publishLocal(new TestEvent(messageBus.getInstanceId(), "test2"));
        Thread.sleep(100); // Wait to ensure no delivery
        
        assertEquals(1, eventCount.get(), "Should only receive event before unsubscribe");
    }
    
    @Test
    void testDuplicateEventFiltering() throws InterruptedException {
        AtomicInteger eventCount = new AtomicInteger(0);
        
        messageBus.subscribe(TestEvent.class, event -> eventCount.incrementAndGet());
        
        TestEvent event = new TestEvent(messageBus.getInstanceId(), "test");
        messageBus.publishLocal(event);
        messageBus.publishLocal(event); // Publish same event twice
        
        Thread.sleep(200); // Wait for async dispatch
        
        assertEquals(1, eventCount.get(), "Duplicate event should be filtered");
    }
    
    @Test
    void testStateChangedEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StateChangedEvent> receivedEvent = new AtomicReference<>();
        
        messageBus.subscribe(StateChangedEvent.class, event -> {
            receivedEvent.set(event);
            latch.countDown();
        });
        
        StateChangedEvent stateEvent = new StateChangedEvent(
            messageBus.getInstanceId(), "config.setting", "newValue");
        messageBus.publishLocal(stateEvent);
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(receivedEvent.get());
        assertEquals("config.setting", receivedEvent.get().getKey());
        assertEquals("newValue", receivedEvent.get().getValue());
    }
    
    @Test
    void testEventProperties() {
        TestEvent event = new TestEvent(messageBus.getInstanceId(), "test");
        
        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getSourceId());
        assertEquals("TestEvent", event.getEventType());
        assertTrue(event.toString().contains("TestEvent"));
    }
    
    @Test
    void testConcurrentPublishing() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(threadCount * eventsPerThread);
        
        messageBus.subscribe(TestEvent.class, event -> latch.countDown());
        
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    messageBus.publishLocal(new TestEvent(messageBus.getInstanceId(), "test"));
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS), "All events should be received");
    }
    
    @Test
    void testNullEventHandling() {
        // Should not throw exception
        assertDoesNotThrow(() -> messageBus.publish(null));
        assertDoesNotThrow(() -> messageBus.publishLocal(null));
    }
    
    /**
     * Test event class.
     */
    static class TestEvent extends MessageBusEvent {
        private static final long serialVersionUID = 1L;
        private final String data;
        
        TestEvent(String sourceId, String data) {
            super(sourceId);
            this.data = data;
        }
        
        String getData() {
            return data;
        }
    }
}
