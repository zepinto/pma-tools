# MessageBus - Cross-Application Lightweight Message Bus

## Overview

The MessageBus is a lightweight, thread-safe publish-subscribe messaging system designed for cross-application event distribution and state synchronization. It supports both local (in-VM) and network-based (cross-VM) communication using UDP multicast.

## Features

- **Type-safe event subscription** - Subscribe to specific event types with compile-time safety
- **Thread-safe** - Concurrent event publishing and dispatching
- **Cross-VM communication** - UDP multicast for distributed messaging
- **Duplicate filtering** - Automatic detection and filtering of duplicate events
- **Asynchronous delivery** - Non-blocking event dispatch
- **Singleton pattern** - Single global instance per JVM
- **Extensible** - Easy to create custom event types

## Architecture

### Core Components

1. **MessageBus** - Central hub for event publishing and subscription
2. **MessageBusEvent** - Base class for all events (serializable)
3. **MessageBusListener** - Functional interface for event handlers
4. **NetworkTransport** - UDP multicast transport layer
5. **StateChangedEvent** - Built-in event for state synchronization

### Event Flow

```
Publisher → MessageBus → [Local Dispatch] → Subscribers
                      ↓
                [Network Transport] → Other VMs → MessageBus → Subscribers
```

## Usage Examples

### Basic Local Messaging

```java
MessageBus bus = MessageBus.getInstance();

// Subscribe to events
bus.subscribe(StateChangedEvent.class, event -> {
    System.out.println("State changed: " + event.getKey() + " = " + event.getValue());
});

// Publish event (local only)
bus.publishLocal(new StateChangedEvent(
    bus.getInstanceId(), "temperature", 23.5));
```

### Custom Event Types

```java
// Define custom event
public class DataUpdateEvent extends MessageBusEvent {
    private static final long serialVersionUID = 1L;
    private final String dataType;
    private final String content;
    
    public DataUpdateEvent(String sourceId, String dataType, String content) {
        super(sourceId);
        this.dataType = dataType;
        this.content = content;
    }
    
    public String getDataType() { return dataType; }
    public String getContent() { return content; }
}

// Subscribe and publish
bus.subscribe(DataUpdateEvent.class, event -> {
    System.out.println("Data: " + event.getContent());
});

bus.publishLocal(new DataUpdateEvent(
    bus.getInstanceId(), "sonar", "New data available"));
```

### Cross-VM Communication

```java
MessageBus bus = MessageBus.getInstance();

// Enable network transport
bus.enableNetworkTransport("239.0.0.1", 15000);

// Subscribe to events from all sources
bus.subscribe(StateChangedEvent.class, event -> {
    if (!bus.getInstanceId().equals(event.getSourceId())) {
        System.out.println("Received from " + event.getSourceId() + ": " + event);
    }
});

// Publish event (local + network)
bus.publish(new StateChangedEvent(
    bus.getInstanceId(), "mission.status", "active"));

// When done
bus.disableNetworkTransport();
```

### State Synchronization

```java
MessageBus bus = MessageBus.getInstance();
Map<String, Object> appState = new HashMap<>();

// Sync state across instances
bus.subscribe(StateChangedEvent.class, event -> {
    appState.put(event.getKey(), event.getValue());
    System.out.println("State synchronized: " + event.getKey());
});

// Enable network for cross-VM sync
bus.enableNetworkTransport("239.0.0.1", 15000);

// Publish state changes
bus.publish(new StateChangedEvent(bus.getInstanceId(), "user.name", "John"));
bus.publish(new StateChangedEvent(bus.getInstanceId(), "depth", 150.0));
```

## API Reference

### MessageBus Methods

| Method | Description |
|--------|-------------|
| `getInstance()` | Get singleton instance |
| `getInstanceId()` | Get unique instance identifier |
| `subscribe(Class<T>, MessageBusListener<T>)` | Subscribe to event type |
| `unsubscribe(Class<T>, MessageBusListener<T>)` | Unsubscribe listener |
| `publishLocal(MessageBusEvent)` | Publish to local subscribers only |
| `publish(MessageBusEvent)` | Publish to local + network |
| `enableNetworkTransport(String, int)` | Enable cross-VM communication |
| `disableNetworkTransport()` | Disable network transport |
| `shutdown()` | Shutdown message bus |

### MessageBusEvent Properties

| Property | Type | Description |
|----------|------|-------------|
| `eventId` | String | Unique event identifier (UUID) |
| `timestamp` | Instant | Event creation time |
| `sourceId` | String | Source instance identifier |
| `eventType` | String | Event type name (class name) |

### StateChangedEvent Properties

| Property | Type | Description |
|----------|------|-------------|
| `key` | String | State key |
| `value` | Object | State value (must be serializable) |
| `metadata` | Map<String, Object> | Optional metadata |

## Network Configuration

### Multicast Groups

- Use multicast addresses in range `239.0.0.0` to `239.255.255.255`
- Recommended: `239.0.0.1` for general use
- Different applications should use different groups to avoid cross-talk

### Port Selection

- Use ports above 1024 (non-privileged)
- Recommended range: 15000-15999
- Ensure firewall allows UDP multicast traffic

### Network Requirements

- Multicast must be enabled on network interfaces
- All instances must be on same subnet or properly routed
- Firewalls must allow UDP traffic on configured port

## Performance Considerations

### Event Size

- Maximum event size: 65,507 bytes (UDP packet limit)
- Recommended: Keep events under 8 KB for reliability
- Large data should be passed by reference, not value

### Throughput

- Asynchronous dispatch prevents blocking
- Uses cached thread pool for scalability
- Duplicate detection limited to last 10,000 events

### Memory

- Processed events are automatically pruned (LRU)
- Network buffers are fixed size
- Listeners use CopyOnWriteArrayList for thread safety

## Thread Safety

All MessageBus operations are thread-safe:
- Concurrent subscription/unsubscription
- Concurrent event publishing
- Asynchronous event dispatch
- Synchronized network I/O

## Error Handling

- Listener exceptions are logged but don't affect other listeners
- Network errors are logged and transport continues
- Serialization failures skip affected events
- Graceful degradation on network unavailability

## Testing

Run tests with:
```bash
./gradlew :neptus-utils:test
```

Network tests require multicast support and may be skipped in restricted environments.

## Example Application

See `MessageBusExample.java` for complete usage examples:
```bash
cd neptus-utils
java -cp build/libs/neptus-utils-2025.11.00.jar \
  pt.omst.neptus.messagebus.MessageBusExample
```

## Integration with Applications

### Rasterfall Integration

```java
public class RasterFallApp {
    private MessageBus messageBus;
    
    public void initialize() {
        messageBus = MessageBus.getInstance();
        messageBus.enableNetworkTransport("239.0.0.1", 15000);
        
        // Listen for raster updates
        messageBus.subscribe(StateChangedEvent.class, this::handleStateChange);
    }
    
    private void handleStateChange(StateChangedEvent event) {
        if ("raster.selected".equals(event.getKey())) {
            // Update UI
        }
    }
}
```

## Best Practices

1. **Use descriptive event types** - Create specific event classes for different purposes
2. **Keep events small** - Pass references or IDs instead of large objects
3. **Handle exceptions** - Listener code should be defensive
4. **Clean up listeners** - Unsubscribe when components are destroyed
5. **Use metadata** - Add context without creating new event types
6. **Test network isolation** - Ensure events don't leak between applications
7. **Log appropriately** - Use TRACE for high-frequency events
8. **Consider latency** - Network events have ~1-10ms overhead

## Troubleshooting

### Events not received across VMs

- Check multicast is enabled: `ip maddress show`
- Verify firewall allows UDP on configured port
- Ensure all instances use same multicast group and port
- Check network routing allows multicast

### High memory usage

- Reduce processed event cache size (modify `maxProcessedEvents`)
- Ensure large data is not embedded in events
- Check for memory leaks in listener callbacks

### Performance issues

- Use `publishLocal()` when network distribution not needed
- Batch related state changes
- Consider rate limiting high-frequency events
- Profile listener execution time

## License

Copyright 2025 OceanScan - Marine Systems & Technology, Lda.

Proprietary - All rights reserved.
