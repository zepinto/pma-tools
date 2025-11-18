//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.pulvis;

import pt.omst.pulvis.PulvisConnection.ContactEvent;
import pt.omst.pulvis.model.Contact;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Demo application showing how to use the PulvisWSConnection to receive contact updates.
 */
public class PulvisWSDemo {
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static int eventCount = 0;
    
    public static void main(String[] args) {
        String wsUrl = "http://localhost:8080/ws/contacts";
        
        // Allow custom URL from command line
        if (args.length > 0) {
            wsUrl = args[0];
        }
        
        System.out.println("========================================");
        System.out.println("Pulvis WebSocket Connection Demo");
        System.out.println("========================================");
        System.out.println("Connecting to: " + wsUrl);
        System.out.println();
        
        PulvisConnection connection = new PulvisConnection("localhost", 8080);
        
        // Add event listener to display incoming contact events
        connection.addEventListener(event -> displayEvent(event));
        
        // Add status listener to display connection status changes
        connection.addStatusListener(connected -> {
            String status = connected ? "✓ CONNECTED" : "✗ DISCONNECTED";
            System.out.println("\n[STATUS] " + status);
            System.out.println();
        });
        
        // Connect to the WebSocket
        System.out.println("Initiating connection...");
        CompletableFuture<Void> connectFuture = connection.connect();
        
        try {
            // Wait for connection with timeout
            connectFuture.get(10, TimeUnit.SECONDS);
            System.out.println("Successfully connected!");
            System.out.println("Listening for contact events...");
            System.out.println("Press Enter to disconnect and exit.");
            System.out.println("----------------------------------------");
            System.out.println();
            
            // Wait for user input
            System.in.read();
            
        } catch (java.util.concurrent.TimeoutException e) {
            System.err.println("\n✗ Connection timeout - make sure the server is running at " + wsUrl);
        } catch (java.util.concurrent.ExecutionException e) {
            System.err.println("\n✗ Connection failed: " + e.getCause().getMessage());
            System.err.println("  Make sure the Pulvis server is running and accessible.");
        } catch (Exception e) {
            System.err.println("\n✗ Error: " + e.getMessage());
        } finally {
            // Disconnect and cleanup
            System.out.println("\n----------------------------------------");
            System.out.println("Disconnecting...");
            connection.disconnect();
            
            // Summary
            System.out.println("\n========================================");
            System.out.println("Session Summary");
            System.out.println("========================================");
            System.out.println("Total events received: " + connection.getEventCount());
            System.out.println("Goodbye!");
        }
    }
    
    /**
     * Displays a contact event in a readable format.
     */
    private static void displayEvent(ContactEvent event) {
        eventCount++;
        
        System.out.println("┌─ Event #" + eventCount + " ─────────────────────────────");
        System.out.println("│ Type: " + event.getEventType());
        System.out.println("│ Time: " + event.getTimestamp().format(TIME_FORMATTER));
        System.out.println("│ Contact ID: " + event.getContactId());
        
        if (event.getContact() != null) {
            Contact contact = event.getContact();
            System.out.println("│");
            System.out.println("│ Contact Details:");
            System.out.println("│   Label: " + contact.getLabel());
            System.out.printf("│   Location: %.6f°N, %.6f°E%n", 
                contact.getLatitude(), contact.getLongitude());
            
            if (contact.getDepth() != null) {
                System.out.printf("│   Depth: %.2f m%n", contact.getDepth());
            }
            
            if (contact.getObservations() != null && !contact.getObservations().isEmpty()) {
                System.out.println("│   Observations: " + contact.getObservations().size());
            }
            
            if (contact.getUuid() != null) {
                System.out.println("│   UUID: " + contact.getUuid());
            }
        }
        
        System.out.println("└────────────────────────────────────────");
        System.out.println();
    }
}
