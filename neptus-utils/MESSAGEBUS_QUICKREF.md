# MessageBus Quick Reference

## Quick Start (30 seconds)

```java
// Get instance
MessageBus bus = MessageBus.getInstance();

// Subscribe
bus.subscribe(StateChangedEvent.class, event -> {
    System.out.println(event.getKey() + " = " + event.getValue());
});

// Publish (local only)
bus.publishLocal(new StateChangedEvent(
    bus.getInstanceId(), "temperature", 23.5));
```

## Enable Network Communication (1 minute)

```java
MessageBus bus = MessageBus.getInstance();

// Enable UDP multicast
bus.enableNetworkTransport("239.0.0.1", 15000);

// Subscribe to events from all VMs
bus.subscribe(StateChangedEvent.class, event -> {
    if (!bus.getInstanceId().equals(event.getSourceId())) {
        System.out.println("Remote: " + event);
    }
});

// Publish to local + network
bus.publish(new StateChangedEvent(
    bus.getInstanceId(), "status", "active"));

// When done
bus.disableNetworkTransport();
```

## Custom Events

```java
// Define event
public class MyEvent extends MessageBusEvent {
    private static final long serialVersionUID = 1L;
    private final String data;
    
    public MyEvent(String sourceId, String data) {
        super(sourceId);
        this.data = data;
    }
    
    public String getData() { return data; }
}

// Use it
bus.subscribe(MyEvent.class, event -> 
    System.out.println(event.getData()));

bus.publishLocal(new MyEvent(bus.getInstanceId(), "hello"));
```

## Common Patterns

### Application State Sync

```java
Map<String, Object> appState = new ConcurrentHashMap<>();

bus.subscribe(StateChangedEvent.class, event -> {
    appState.put(event.getKey(), event.getValue());
});

// Update state (syncs across VMs if network enabled)
bus.publish(new StateChangedEvent(
    bus.getInstanceId(), "user.name", "Alice"));
```

### Component Communication

```java
// Component A publishes
class ComponentA {
    void notifyChange() {
        bus.publish(new StateChangedEvent(
            bus.getInstanceId(), "data.updated", true));
    }
}

// Component B listens
class ComponentB {
    void init() {
        bus.subscribe(StateChangedEvent.class, event -> {
            if ("data.updated".equals(event.getKey())) {
                refresh();
            }
        });
    }
}
```

### Cleanup

```java
// Save reference to listener
MessageBusListener<MyEvent> listener = event -> {...};

// Subscribe
bus.subscribe(MyEvent.class, listener);

// Later, unsubscribe
bus.unsubscribe(MyEvent.class, listener);
```

## API Cheat Sheet

| Method | Description | Thread-Safe |
|--------|-------------|-------------|
| `getInstance()` | Get singleton | Yes |
| `getInstanceId()` | Get VM instance ID | Yes |
| `subscribe(Class, Listener)` | Add listener | Yes |
| `unsubscribe(Class, Listener)` | Remove listener | Yes |
| `publishLocal(Event)` | Local only | Yes |
| `publish(Event)` | Local + Network | Yes |
| `enableNetworkTransport(group, port)` | Start UDP | Yes |
| `disableNetworkTransport()` | Stop UDP | Yes |
| `shutdown()` | Cleanup all | Yes |

## Event Properties

Every `MessageBusEvent` has:
- `eventId` - Unique UUID
- `timestamp` - Creation time
- `sourceId` - VM instance ID
- `eventType` - Class name

## Network Configuration

### Recommended Settings
- **Multicast Group**: `239.0.0.1` to `239.0.0.255`
- **Port**: `15000` to `15999`
- **Max Event Size**: 8 KB (UDP limit: 65 KB)

### Firewall Rules
- Allow UDP outbound/inbound on configured port
- Enable multicast on network interfaces

## Best Practices

1. **Keep events small** - Max 8 KB recommended
2. **Use specific event types** - Better than generic events
3. **Unsubscribe when done** - Prevent memory leaks
4. **Handle exceptions** - Listener code should be defensive
5. **Use publishLocal()** - When network not needed
6. **Log appropriately** - TRACE for high-frequency events
7. **Test network isolation** - Different apps = different groups

## Troubleshooting

### Events not received
- Check listener is subscribed before publishing
- Verify event type matches exactly
- Look for exceptions in logs

### Network issues
- Verify multicast enabled: `ip maddress show`
- Check firewall allows UDP on port
- Ensure same multicast group and port
- Test with `tcpdump -i any udp port 15000`

### Performance
- Use `publishLocal()` when possible
- Batch related state changes
- Profile listener execution time
- Monitor thread pool size

## Testing

```java
@Test
void testEventDelivery() throws InterruptedException {
    MessageBus bus = MessageBus.getInstance();
    CountDownLatch latch = new CountDownLatch(1);
    
    MessageBusListener<MyEvent> listener = event -> {
        assertEquals("test", event.getData());
        latch.countDown();
    };
    
    bus.subscribe(MyEvent.class, listener);
    
    try {
        bus.publishLocal(new MyEvent(bus.getInstanceId(), "test"));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    } finally {
        bus.unsubscribe(MyEvent.class, listener);
    }
}
```

## Integration Example

```java
public class MyApplication {
    private MessageBus bus;
    
    public void init() {
        bus = MessageBus.getInstance();
        
        // Enable networking
        try {
            bus.enableNetworkTransport("239.0.0.1", 15000);
        } catch (Exception e) {
            log.warn("Network transport unavailable", e);
        }
        
        // Setup listeners
        bus.subscribe(StateChangedEvent.class, this::handleState);
    }
    
    private void handleState(StateChangedEvent event) {
        // Process event
    }
    
    public void shutdown() {
        bus.disableNetworkTransport();
    }
}
```

## Performance Tips

- **Async by default** - Publishing never blocks
- **Thread pool scales** - Handles burst traffic
- **Duplicate filtering** - O(1) hash lookup
- **Network overhead** - ~1-10ms cross-VM latency
- **Memory usage** - ~100 bytes per cached event

## Documentation

- **Full Guide**: `MESSAGEBUS_README.md`
- **Architecture**: `MESSAGEBUS_ARCHITECTURE.md`
- **Examples**: `MessageBusExample.java`
- **Tests**: `src/test/java/.../messagebus/`

## Support

For issues or questions, see project documentation or check logs at TRACE level for detailed event flow.
