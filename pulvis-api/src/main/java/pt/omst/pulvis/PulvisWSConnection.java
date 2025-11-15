//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.pulvis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import pt.omst.pulvis.model.Contact;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * WebSocket connection client for Pulvis contact updates.
 * Connects to the Pulvis WebSocket endpoint and subscribes to contact events.
 */
public class PulvisWSConnection {
    
    private static final Logger log = LoggerFactory.getLogger(PulvisWSConnection.class);
    private static final String DEFAULT_WS_URL = "http://localhost:8080/ws/contacts";
    private static final String TOPIC_CONTACTS = "/topic/contacts";
    
    private final String wsUrl;
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private boolean connected = false;
    private final List<Consumer<ContactEvent>> eventListeners = new ArrayList<>();
    private final List<Consumer<Boolean>> statusListeners = new ArrayList<>();
    private int eventCount = 0;
    
    /**
     * Creates a WebSocket connection with the default URL.
     */
    public PulvisWSConnection() {
        this(DEFAULT_WS_URL);
    }
    
    /**
     * Creates a WebSocket connection with a custom URL.
     * 
     * @param wsUrl The WebSocket URL (e.g., "http://localhost:8080/ws/contacts")
     */
    public PulvisWSConnection(String wsUrl) {
        this.wsUrl = wsUrl;
        initializeStompClient();
    }
    
    /**
     * Initializes the STOMP client with SockJS transport.
     */
    private void initializeStompClient() {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        
        stompClient = new WebSocketStompClient(sockJsClient);
        
                // Configure message converter with custom ObjectMapper
        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // Accept both camelCase and kebab-case properties
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        messageConverter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(messageConverter);
    }
    
    /**
     * Connects to the WebSocket endpoint.
     * 
     * @return CompletableFuture that completes when connected
     */
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("Connected to WebSocket: {}", wsUrl);
                stompSession = session;
                connected = true;
                updateStatus(true);
                logMessage("Connected to WebSocket", MessageType.INFO);
                
                // Subscribe to contact updates
                session.subscribe(TOPIC_CONTACTS, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ContactEvent.class;
                    }
                    
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        if (payload instanceof ContactEvent) {
                            ContactEvent event = (ContactEvent) payload;
                            log.debug("Received event: {}", event);
                            handleEvent(event);
                        }
                    }
                });
                
                future.complete(null);
            }
            
            @Override
            public void handleException(StompSession session, StompCommand command, 
                                      StompHeaders headers, byte[] payload, Throwable exception) {
                log.error("STOMP exception", exception);
                logMessage("STOMP error: " + exception.getMessage(), MessageType.ERROR);
            }
            
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("Transport error", exception);
                connected = false;
                updateStatus(false);
                logMessage("Connection error: " + exception.getMessage(), MessageType.ERROR);
                future.completeExceptionally(exception);
            }
        };
        
        try {
            stompClient.connectAsync(wsUrl, sessionHandler);
        } catch (Exception e) {
            log.error("Failed to connect", e);
            connected = false;
            updateStatus(false);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Disconnects from the WebSocket endpoint.
     */
    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
            stompSession = null;
        }
        connected = false;
        updateStatus(false);
        logMessage("Disconnected from WebSocket", MessageType.INFO);
        log.info("Disconnected from WebSocket");
    }
    
    /**
     * Checks if currently connected.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected && stompSession != null && stompSession.isConnected();
    }
    
    /**
     * Adds a listener for contact events.
     * 
     * @param listener Consumer that receives ContactEvent objects
     */
    public void addEventListener(Consumer<ContactEvent> listener) {
        eventListeners.add(listener);
    }
    
    /**
     * Removes an event listener.
     * 
     * @param listener The listener to remove
     */
    public void removeEventListener(Consumer<ContactEvent> listener) {
        eventListeners.remove(listener);
    }
    
    /**
     * Adds a listener for connection status changes.
     * 
     * @param listener Consumer that receives boolean (true = connected, false = disconnected)
     */
    public void addStatusListener(Consumer<Boolean> listener) {
        statusListeners.add(listener);
    }
    
    /**
     * Removes a status listener.
     * 
     * @param listener The listener to remove
     */
    public void removeStatusListener(Consumer<Boolean> listener) {
        statusListeners.remove(listener);
    }
    
    /**
     * Gets the current event count.
     * 
     * @return Number of events received
     */
    public int getEventCount() {
        return eventCount;
    }
    
    /**
     * Resets the event count to zero.
     */
    public void clearEventCount() {
        eventCount = 0;
    }
    
    /**
     * Handles incoming contact events.
     */
    private void handleEvent(ContactEvent event) {
        eventCount++;
        for (Consumer<ContactEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in event listener", e);
            }
        }
    }
    
    /**
     * Updates connection status and notifies listeners.
     */
    private void updateStatus(boolean connected) {
        for (Consumer<Boolean> listener : statusListeners) {
            try {
                listener.accept(connected);
            } catch (Exception e) {
                log.error("Error in status listener", e);
            }
        }
    }
    
    /**
     * Logs a message (can be overridden by listeners).
     */
    private void logMessage(String message, MessageType type) {
        switch (type) {
            case ERROR -> log.error(message);
            case INFO -> log.info(message);
            case DEBUG -> log.debug(message);
        }
    }
    
    /**
     * Message type enumeration.
     */
    public enum MessageType {
        INFO, ERROR, DEBUG
    }
    
    /**
     * Contact event class representing WebSocket messages.
     */
    public static class ContactEvent {
        private EventType eventType;
        private String contactId;
        private Contact contact;
        private OffsetDateTime timestamp;
        
        public ContactEvent() {
        }
        
        public ContactEvent(EventType eventType, String contactId, Contact contact, OffsetDateTime timestamp) {
            this.eventType = eventType;
            this.contactId = contactId;
            this.contact = contact;
            this.timestamp = timestamp;
        }
        
        public EventType getEventType() {
            return eventType;
        }
        
        public void setEventType(EventType eventType) {
            this.eventType = eventType;
        }
        
        public String getContactId() {
            return contactId;
        }
        
        public void setContactId(String contactId) {
            this.contactId = contactId;
        }
        
        public Contact getContact() {
            return contact;
        }
        
        public void setContact(Contact contact) {
            this.contact = contact;
        }
        
        public OffsetDateTime getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(OffsetDateTime timestamp) {
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return "ContactEvent{" +
                    "eventType=" + eventType +
                    ", contactId='" + contactId + '\'' +
                    ", timestamp=" + timestamp +
                    ", contact=" + (contact != null ? contact.getLabel() : "null") +
                    '}';
        }
    }
    
    /**
     * Event type enumeration.
     */
    public enum EventType {
        ADDED, CREATED, UPDATED, DELETED
    }
    
    /**
     * Example usage and testing.
     */
    public static void main(String[] args) {
        PulvisWSConnection connection = new PulvisWSConnection();
        
        // Add event listener
        connection.addEventListener(event -> {
            System.out.println("Event received: " + event.getEventType() + " at " + event.getTimestamp());
            if (event.getContact() != null) {
                Contact c = event.getContact();
                System.out.printf("  Contact ID: %s%n", event.getContactId());
                System.out.printf("  Label: %s%n", c.getLabel());
                System.out.printf("  Location: (%.6f, %.6f)%n", c.getLatitude(), c.getLongitude());
                if (c.getDepth() != null) {
                    System.out.printf("  Depth: %.2fm%n", c.getDepth());
                }
                if (c.getObservations() != null) {
                    System.out.printf("  Observations: %d%n", c.getObservations().size());
                }
            }
            System.out.println("  Total events: " + connection.getEventCount());
            System.out.println();
        });
        
        // Add status listener
        connection.addStatusListener(connected -> {
            System.out.println("Status: " + (connected ? "CONNECTED" : "DISCONNECTED"));
        });
        
        // Connect
        System.out.println("Connecting to " + DEFAULT_WS_URL + "...");
        CompletableFuture<Void> connectFuture = connection.connect();
        
        try {
            connectFuture.get(10, TimeUnit.SECONDS);
            System.out.println("Successfully connected. Listening for events...");
            System.out.println("Press Enter to disconnect and exit.");
            
            // Wait for user input
            System.in.read();
            
        } catch (TimeoutException e) {
            System.err.println("Connection timeout");
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Connection failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            connection.disconnect();
            System.out.println("Disconnected. Total events received: " + connection.getEventCount());
        }
    }
}
