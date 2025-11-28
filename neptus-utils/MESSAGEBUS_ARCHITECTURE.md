# MessageBus Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Application Layer                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐         ┌──────────────┐         ┌─────────────┐ │
│  │ Component A  │         │ Component B  │         │ Component C │ │
│  │              │         │              │         │             │ │
│  │ publish()    │         │ subscribe()  │         │ subscribe() │ │
│  └──────┬───────┘         └──────▲───────┘         └──────▲──────┘ │
│         │                        │                         │        │
│         │                        │                         │        │
└─────────┼────────────────────────┼─────────────────────────┼────────┘
          │                        │                         │
          ▼                        │                         │
┌─────────────────────────────────────────────────────────────────────┐
│                            MessageBus (Singleton)                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    Subscriber Registry                         │ │
│  │  Map<Class<Event>, List<MessageBusListener>>                  │ │
│  │  - Thread-safe (ConcurrentHashMap)                            │ │
│  │  - Type-safe subscription                                     │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    Event Dispatcher                            │ │
│  │  ExecutorService (CachedThreadPool)                           │ │
│  │  - Asynchronous delivery                                      │ │
│  │  - Non-blocking publish                                       │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    Duplicate Filter                            │ │
│  │  Map<EventId, String> (LRU, max 10,000)                       │ │
│  │  - Prevents duplicate processing                              │ │
│  │  - Automatic pruning                                          │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
│         │                                                    ▲       │
│         │ send()                                    receive()│       │
│         ▼                                                    │       │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    NetworkTransport                            │ │
│  │  - UDP Multicast (239.0.0.x)                                  │ │
│  │  - Serialization/Deserialization                             │ │
│  │  - Max packet size: 65,507 bytes                             │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
└──────────────────────────┬────────────────────▲─────────────────────┘
                           │                    │
                           │ UDP Multicast      │
                           │                    │
                           ▼                    │
┌─────────────────────────────────────────────────────────────────────┐
│                         Network Layer                                │
│                    (239.0.0.x:port)                                 │
└─────────────────────────────────────────────────────────────────────┘
                           │                    ▲
                           │                    │
                           ▼                    │
┌─────────────────────────────────────────────────────────────────────┐
│                    Other VM Instances                                │
│                                                                      │
│  ┌─────────────────┐         ┌─────────────────┐                   │
│  │   MessageBus    │         │   MessageBus    │                   │
│  │   Instance 2    │         │   Instance 3    │                   │
│  │                 │         │                 │                   │
│  │  Applications   │         │  Applications   │                   │
│  └─────────────────┘         └─────────────────┘                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘


Event Flow (Local Publishing):
────────────────────────────────

1. Component A calls: bus.publishLocal(event)
2. MessageBus adds event ID to duplicate filter
3. Event dispatcher submits async tasks for each subscriber
4. Listeners receive event: onEvent(event) called
5. Event processing completes independently per listener


Event Flow (Network Publishing):
─────────────────────────────────

1. Component A calls: bus.publish(event)
2. MessageBus adds event ID to duplicate filter
3. Event dispatched to LOCAL subscribers (async)
4. Event serialized and sent via NetworkTransport
5. UDP multicast packet transmitted to network
6. Other VM instances receive packet
7. NetworkTransport deserializes event
8. Remote MessageBus verifies sourceId (filters own)
9. Remote MessageBus publishes to local subscribers
10. Duplicate filter prevents reprocessing


Thread Safety:
──────────────

• ConcurrentHashMap for subscriber registry
• CopyOnWriteArrayList for subscriber lists
• Synchronized Map for duplicate filter
• Atomic operations for state management
• Thread-safe event dispatch via ExecutorService


Performance Characteristics:
────────────────────────────

• O(1) event publishing (async)
• O(n) notification (n = subscribers per event type)
• O(1) duplicate detection (hash map lookup)
• UDP multicast: ~1-10ms cross-VM latency
• Memory: ~100 bytes per event in cache
• Auto-pruning at 10,000 events (LRU)
```
