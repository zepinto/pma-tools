# Pulvis WebSocket Connection

This module provides a Java client for connecting to the Pulvis WebSocket endpoint to receive real-time contact updates.

## Overview

The `PulvisWSConnection` class implements a WebSocket client using Spring's STOMP protocol over SockJS to connect to the Pulvis server and subscribe to contact events.

## Features

- **Automatic connection management** with SockJS fallback
- **Event-driven architecture** with listener support
- **Connection status monitoring**
- **Type-safe event handling** using generated Contact models
- **Comprehensive error handling** and logging

## Dependencies

The following dependencies are required and already configured in `build.gradle`:

```gradle
implementation 'org.springframework:spring-websocket:6.1.1'
implementation 'org.springframework:spring-messaging:6.1.1'
implementation 'org.springframework:spring-context:6.1.1'
implementation 'jakarta.websocket:jakarta.websocket-api:2.1.0'
implementation 'org.glassfish.tyrus.bundles:tyrus-standalone-client:2.1.3'
```

## Quick Start

### Basic Usage

```java
import pt.omst.pulvis.PulvisWSConnection;
import pt.omst.pulvis.PulvisWSConnection.ContactEvent;

// Create connection
PulvisWSConnection connection = new PulvisWSConnection();

// Add event listener
connection.addEventListener(event -> {
    System.out.println("Received: " + event.getEventType() + 
                       " for contact " + event.getContactId());
});

// Connect
connection.connect().get(10, TimeUnit.SECONDS);

// ... do work ...

// Disconnect
connection.disconnect();
```

### Running the Demo

A demo application is provided to test the WebSocket connection:

```bash
# Run with default URL (http://localhost:8080/ws/contacts)
./gradlew :pulvis-api:run

# Or run the main class directly
java -cp pulvis-api/build/libs/pulvis-api-2025.11.00.jar \
     pt.omst.pulvis.PulvisWSDemo

# With custom URL
java -cp pulvis-api/build/libs/pulvis-api-2025.11.00.jar \
     pt.omst.pulvis.PulvisWSDemo http://myserver:8080/ws/contacts
```

## API Reference

### PulvisWSConnection

Main class for managing WebSocket connections.

#### Constructors

```java
// Connect to default URL (http://localhost:8080/ws/contacts)
PulvisWSConnection()

// Connect to custom URL
PulvisWSConnection(String wsUrl)
```

#### Methods

**Connection Management:**

```java
// Connect to WebSocket (async)
CompletableFuture<Void> connect()

// Disconnect from WebSocket
void disconnect()

// Check connection status
boolean isConnected()
```

**Event Handling:**

```java
// Add event listener
void addEventListener(Consumer<ContactEvent> listener)

// Remove event listener
void removeEventListener(Consumer<ContactEvent> listener)

// Add connection status listener
void addStatusListener(Consumer<Boolean> listener)

// Remove status listener
void removeStatusListener(Consumer<Boolean> listener)
```

**Statistics:**

```java
// Get total events received
int getEventCount()

// Reset event counter
void clearEventCount()
```

### ContactEvent

Event object received when contacts are created, updated, or deleted.

#### Properties

```java
EventType eventType    // CREATED, UPDATED, or DELETED
String contactId       // Unique contact identifier
Contact contact        // Full contact data (may be null for DELETE)
OffsetDateTime timestamp // Event timestamp
```

#### Event Types

- `CREATED` - A new contact was created
- `UPDATED` - An existing contact was modified
- `DELETED` - A contact was deleted

### Contact Model

The `Contact` class is auto-generated from the OpenAPI specification and includes:

- `UUID uuid` - Unique identifier
- `String label` - Contact label/name
- `Double latitude` - Geographic latitude
- `Double longitude` - Geographic longitude
- `Double depth` - Depth in meters (optional)
- `List<Observation> observations` - Associated observations
- `OffsetDateTime createdAt` - Creation timestamp
- `OffsetDateTime updatedAt` - Last update timestamp

## Advanced Usage

### Multiple Event Listeners

You can register multiple listeners to handle events differently:

```java
PulvisWSConnection connection = new PulvisWSConnection();

// Logger listener
connection.addEventListener(event -> 
    log.info("Event: {} at {}", event.getEventType(), event.getTimestamp())
);

// Database persistence listener
connection.addEventListener(event -> 
    database.save(event.getContact())
);

// UI update listener
connection.addEventListener(event -> 
    SwingUtilities.invokeLater(() -> updateMap(event.getContact()))
);

connection.connect();
```

### Connection Status Monitoring

Monitor connection status changes:

```java
connection.addStatusListener(connected -> {
    if (connected) {
        System.out.println("Connected - ready to receive events");
        startDataProcessing();
    } else {
        System.out.println("Disconnected - pausing operations");
        pauseDataProcessing();
    }
});
```

### Error Handling

The connection handles errors automatically and logs them via SLF4J. Configure logging in your `logback.xml`:

```xml
<logger name="pt.omst.pulvis" level="DEBUG"/>
```

### Reconnection Strategy

For production use, implement automatic reconnection:

```java
public class RobustWSConnection {
    private final PulvisWSConnection connection;
    private final ScheduledExecutorService scheduler;
    
    public void connectWithRetry() {
        connection.connect().exceptionally(ex -> {
            log.warn("Connection failed, retrying in 5 seconds...");
            scheduler.schedule(this::connectWithRetry, 5, TimeUnit.SECONDS);
            return null;
        });
    }
}
```

## WebSocket Endpoint

The client connects to the following endpoint structure:

- **Connection URL:** `http://localhost:8080/ws/contacts` (configurable)
- **Protocol:** SockJS + STOMP
- **Topic:** `/topic/contacts`
- **Message Format:** JSON

### Message Format

Events are published as JSON messages matching this structure:

```json
{
  "eventType": "CREATED",
  "contactId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-11-15T10:30:45.123Z",
  "contact": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "label": "Target-001",
    "latitude": 41.185063,
    "longitude": -8.706253,
    "depth": 25.5,
    "observations": [],
    "createdAt": "2025-11-15T10:30:45.123Z",
    "updatedAt": "2025-11-15T10:30:45.123Z"
  }
}
```

## Integration with UI Applications

### Swing Example

```java
public class ContactMapPanel extends JPanel {
    private final PulvisWSConnection wsConnection;
    
    public ContactMapPanel() {
        wsConnection = new PulvisWSConnection();
        
        wsConnection.addEventListener(event -> {
            // Update UI on Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                if (event.getContact() != null) {
                    addContactToMap(event.getContact());
                }
            });
        });
        
        wsConnection.connect();
    }
    
    public void dispose() {
        wsConnection.disconnect();
    }
}
```

### JavaFX Example

```java
public class ContactController {
    private final PulvisWSConnection wsConnection;
    
    @FXML
    private void initialize() {
        wsConnection = new PulvisWSConnection();
        
        wsConnection.addEventListener(event -> {
            // Update UI on JavaFX Application Thread
            Platform.runLater(() -> {
                updateContactList(event.getContact());
            });
        });
        
        wsConnection.connect();
    }
}
```

## Troubleshooting

### Connection Timeout

If you get a connection timeout:

1. Verify the Pulvis server is running: `curl http://localhost:8080/actuator/health`
2. Check the WebSocket endpoint is accessible
3. Verify no firewall is blocking WebSocket connections
4. Check server logs for errors

### No Events Received

If connected but not receiving events:

1. Verify the subscription topic is correct (`/topic/contacts`)
2. Trigger a contact creation/update on the server
3. Enable DEBUG logging to see incoming messages
4. Check server is publishing to the correct topic

### Compilation Issues

If you get compilation errors:

```bash
# Refresh dependencies
./gradlew :pulvis-api:clean :pulvis-api:build --refresh-dependencies

# Regenerate OpenAPI code
./gradlew :pulvis-api:downloadOpenApiSpec :pulvis-api:openApiGenerate
```

## Testing

### Unit Testing with Mock Events

```java
@Test
public void testEventHandler() {
    PulvisWSConnection connection = new PulvisWSConnection();
    AtomicInteger eventCount = new AtomicInteger(0);
    
    connection.addEventListener(event -> {
        eventCount.incrementAndGet();
    });
    
    // Test would require mocking the STOMP connection
    // or integration testing with a running server
}
```

### Integration Testing

For integration testing, ensure:
1. Pulvis server is running on localhost:8080
2. WebSocket endpoint is enabled
3. Test data can be created via REST API

```java
@Test
public void testRealConnection() throws Exception {
    PulvisWSConnection connection = new PulvisWSConnection();
    CountDownLatch latch = new CountDownLatch(1);
    
    connection.addEventListener(event -> latch.countDown());
    connection.connect().get(5, TimeUnit.SECONDS);
    
    // Create a contact via REST API to trigger event
    createTestContact();
    
    // Wait for event
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    connection.disconnect();
}
```

## Performance Considerations

- **Thread Safety:** Event listeners are called on STOMP client threads. Synchronize access to shared state.
- **Blocking Operations:** Avoid long-running operations in event listeners to prevent blocking message processing.
- **Memory:** Remove listeners when no longer needed to prevent memory leaks.
- **Connection Pooling:** Reuse connections instead of creating new ones frequently.

## License

Copyright 2025 OceanScan - Marine Systems & Technology, Lda.
