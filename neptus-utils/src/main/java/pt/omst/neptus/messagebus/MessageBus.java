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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Lightweight message bus for cross-application event distribution and state synchronization.
 * Supports both local (in-VM) and network-based (cross-VM) event delivery.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Type-safe event subscription and publishing</li>
 *   <li>Thread-safe event dispatching</li>
 *   <li>UDP multicast for cross-VM communication</li>
 *   <li>Automatic duplicate event filtering</li>
 *   <li>Asynchronous event delivery</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>
 * MessageBus bus = MessageBus.getInstance();
 * 
 * // Subscribe to events
 * bus.subscribe(MyEvent.class, event -> {
 *     System.out.println("Received: " + event);
 * });
 * 
 * // Publish event (local and network)
 * bus.publish(new MyEvent("data"));
 * </pre>
 * 
 * @author José Pinto
 */
@Slf4j
public class MessageBus {
    
    private static final MessageBus INSTANCE = new MessageBus();
    
    private final String instanceId;
    private final Map<Class<? extends MessageBusEvent>, List<MessageBusListener<?>>> listeners;
    private final ExecutorService eventDispatcher;
    private final NetworkTransport networkTransport;
    private final Map<String, String> processedEventIds;
    private final int maxProcessedEvents = 10000;
    
    /**
     * Private constructor for singleton pattern.
     */
    private MessageBus() {
        this.instanceId = generateInstanceId();
        this.listeners = new ConcurrentHashMap<>();
        this.eventDispatcher = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MessageBus-Dispatcher");
            t.setDaemon(true);
            return t;
        });
        this.processedEventIds = Collections.synchronizedMap(new LinkedHashMap<String, String>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxProcessedEvents;
            }
        });
        this.networkTransport = new NetworkTransport(this, instanceId);
        
        log.info("MessageBus initialized with instance ID: {}", instanceId);
    }
    
    /**
     * Gets the singleton instance of the message bus.
     * 
     * @return The message bus instance
     */
    public static MessageBus getInstance() {
        return INSTANCE;
    }
    
    /**
     * Gets the unique identifier for this message bus instance.
     * 
     * @return The instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * Subscribes a listener to events of a specific type.
     * 
     * @param <T> The event type
     * @param eventType The class of events to listen for
     * @param listener The listener to call when events are received
     */
    public <T extends MessageBusEvent> void subscribe(Class<T> eventType, MessageBusListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("Subscribed listener for event type: {}", eventType.getSimpleName());
    }
    
    /**
     * Unsubscribes a listener from events of a specific type.
     * 
     * @param <T> The event type
     * @param eventType The class of events to stop listening for
     * @param listener The listener to remove
     */
    public <T extends MessageBusEvent> void unsubscribe(Class<T> eventType, MessageBusListener<T> listener) {
        List<MessageBusListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            log.debug("Unsubscribed listener for event type: {}", eventType.getSimpleName());
        }
    }
    
    /**
     * Publishes an event to all local subscribers and across the network.
     * 
     * @param event The event to publish
     */
    public void publish(MessageBusEvent event) {
        if (event == null) {
            return;
        }
        
        // Check for duplicate events
        if (processedEventIds.containsKey(event.getEventId())) {
            log.trace("Ignoring duplicate event: {}", event.getEventId());
            return;
        }
        processedEventIds.put(event.getEventId(), "");
        
        log.debug("Publishing event: {}", event);
        
        // Dispatch to local listeners
        dispatchToLocal(event);
        
        // Send over network if source is local
        if (instanceId.equals(event.getSourceId())) {
            networkTransport.send(event);
        }
    }
    
    /**
     * Publishes an event only to local subscribers (not across the network).
     * 
     * @param event The event to publish locally
     */
    public void publishLocal(MessageBusEvent event) {
        if (event == null) {
            return;
        }
        
        if (processedEventIds.containsKey(event.getEventId())) {
            log.trace("Ignoring duplicate event: {}", event.getEventId());
            return;
        }
        processedEventIds.put(event.getEventId(), "");
        
        log.debug("Publishing local event: {}", event);
        dispatchToLocal(event);
    }
    
    /**
     * Dispatches an event to local listeners asynchronously.
     * 
     * @param event The event to dispatch
     */
    @SuppressWarnings("unchecked")
    private void dispatchToLocal(MessageBusEvent event) {
        List<MessageBusListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (MessageBusListener<?> listener : eventListeners) {
                eventDispatcher.submit(() -> {
                    try {
                        ((MessageBusListener<MessageBusEvent>) listener).onEvent(event);
                    } catch (Exception e) {
                        log.error("Error dispatching event {} to listener", event, e);
                    }
                });
            }
        }
    }
    
    /**
     * Enables network transport for cross-VM communication.
     * 
     * @param multicastGroup The multicast group address (e.g., "239.0.0.1")
     * @param port The port number for communication
     * @throws Exception if network transport cannot be started
     */
    public void enableNetworkTransport(String multicastGroup, int port) throws Exception {
        networkTransport.start(multicastGroup, port);
        log.info("Network transport enabled on {}:{}", multicastGroup, port);
    }
    
    /**
     * Disables network transport.
     */
    public void disableNetworkTransport() {
        networkTransport.stop();
        log.info("Network transport disabled");
    }
    
    /**
     * Shuts down the message bus, stopping all event delivery.
     */
    public void shutdown() {
        log.info("Shutting down MessageBus");
        disableNetworkTransport();
        eventDispatcher.shutdown();
        try {
            if (!eventDispatcher.awaitTermination(5, TimeUnit.SECONDS)) {
                eventDispatcher.shutdownNow();
            }
        } catch (InterruptedException e) {
            eventDispatcher.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Generates a unique instance ID based on hostname and timestamp.
     * 
     * @return A unique instance identifier
     */
    private static String generateInstanceId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String mac = getFirstMacAddress();
            return String.format("%s-%s-%d", hostname, mac, System.currentTimeMillis());
        } catch (Exception e) {
            return String.format("unknown-%d", System.currentTimeMillis());
        }
    }
    
    /**
     * Gets the first available MAC address for instance identification.
     * 
     * @return MAC address as string or "nomac" if unavailable
     */
    private static String getFirstMacAddress() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface netint = nets.nextElement();
                byte[] mac = netint.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(3, mac.length); i++) {
                        sb.append(String.format("%02X", mac[i]));
                    }
                    return sb.toString();
                }
            }
        } catch (SocketException e) {
            log.warn("Could not get MAC address", e);
        }
        return "nomac";
    }
}
