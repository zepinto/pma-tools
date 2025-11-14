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

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Network transport layer for the message bus using UDP multicast.
 * Handles serialization and network transmission of events across VM instances.
 * 
 * @author José Pinto
 */
@Slf4j
class NetworkTransport {
    
    private static final int MAX_PACKET_SIZE = 65507; // Max UDP packet size
    private static final int SOCKET_TIMEOUT = 1000; // 1 second
    
    private final MessageBus messageBus;
    private final String instanceId;
    private final AtomicBoolean running;
    private MulticastSocket socket;
    private InetAddress group;
    private int port;
    private ExecutorService receiverExecutor;
    
    /**
     * Creates a new network transport.
     * 
     * @param messageBus The message bus to deliver received events to
     * @param instanceId The instance ID to filter out own messages
     */
    NetworkTransport(MessageBus messageBus, String instanceId) {
        this.messageBus = messageBus;
        this.instanceId = instanceId;
        this.running = new AtomicBoolean(false);
    }
    
    /**
     * Starts the network transport on the specified multicast group and port.
     * 
     * @param multicastGroup The multicast group address (e.g., "239.0.0.1")
     * @param port The port number
     * @throws Exception if the transport cannot be started
     */
    synchronized void start(String multicastGroup, int port) throws Exception {
        if (running.get()) {
            log.warn("Network transport already running");
            return;
        }
        
        this.port = port;
        this.group = InetAddress.getByName(multicastGroup);
        
        // Create multicast socket
        socket = new MulticastSocket(port);
        socket.setSoTimeout(SOCKET_TIMEOUT);
        socket.setTimeToLive(32); // Allow routing across subnets
        
        // Join multicast group
        NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        if (netIf == null) {
            netIf = NetworkInterface.getNetworkInterfaces().nextElement();
        }
        
        SocketAddress groupAddr = new InetSocketAddress(group, port);
        socket.joinGroup(groupAddr, netIf);
        
        running.set(true);
        
        // Start receiver thread
        receiverExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MessageBus-NetworkReceiver");
            t.setDaemon(true);
            return t;
        });
        receiverExecutor.submit(this::receiveLoop);
        
        log.info("Network transport started on {}:{}", multicastGroup, port);
    }
    
    /**
     * Stops the network transport.
     */
    synchronized void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        if (receiverExecutor != null) {
            receiverExecutor.shutdownNow();
        }
        
        if (socket != null) {
            try {
                SocketAddress groupAddr = new InetSocketAddress(group, port);
                NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                if (netIf == null) {
                    netIf = NetworkInterface.getNetworkInterfaces().nextElement();
                }
                socket.leaveGroup(groupAddr, netIf);
            } catch (Exception e) {
                log.warn("Error leaving multicast group", e);
            }
            socket.close();
        }
        
        log.info("Network transport stopped");
    }
    
    /**
     * Sends an event over the network.
     * 
     * @param event The event to send
     */
    void send(MessageBusEvent event) {
        if (!running.get() || socket == null) {
            log.trace("Network transport not running, skipping send");
            return;
        }
        
        try {
            byte[] data = serialize(event);
            if (data.length > MAX_PACKET_SIZE) {
                log.error("Event too large to send: {} bytes (max {})", data.length, MAX_PACKET_SIZE);
                return;
            }
            
            DatagramPacket packet = new DatagramPacket(data, data.length, group, port);
            socket.send(packet);
            log.trace("Sent event over network: {}", event);
            
        } catch (Exception e) {
            log.error("Error sending event over network", e);
        }
    }
    
    /**
     * Main receive loop for incoming events.
     */
    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                // Deserialize and publish event
                MessageBusEvent event = deserialize(packet.getData(), packet.getLength());
                
                // Filter out own events
                if (!instanceId.equals(event.getSourceId())) {
                    log.trace("Received event from network: {}", event);
                    messageBus.publish(event);
                }
                
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error receiving event from network", e);
                }
            }
        }
    }
    
    /**
     * Serializes an event to bytes.
     * 
     * @param event The event to serialize
     * @return The serialized bytes
     * @throws IOException if serialization fails
     */
    private byte[] serialize(MessageBusEvent event) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(event);
            oos.flush();
            return baos.toByteArray();
        }
    }
    
    /**
     * Deserializes an event from bytes.
     * 
     * @param data The serialized data
     * @param length The length of valid data
     * @return The deserialized event
     * @throws IOException if deserialization fails
     * @throws ClassNotFoundException if event class not found
     */
    private MessageBusEvent deserialize(byte[] data, int length) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data, 0, length);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (MessageBusEvent) ois.readObject();
        }
    }
}
