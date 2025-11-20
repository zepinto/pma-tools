//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.contacts.services;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.contacts.PulvisContactSynchronizer;
import pt.omst.gui.datasource.PulvisDataSource;
import pt.omst.gui.jobs.BackgroundJob;
import pt.omst.gui.jobs.JobManager;
import pt.omst.pulvis.PulvisConnection;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Pulvis Data Manager connections, synchronization, and contact upload operations.
 * Handles WebSocket connections, bulk uploads, and error reporting.
 */
@Slf4j
public class PulvisConnectionManager {
    private final ContactCollection contactCollection;
    private final Map<PulvisDataSource, PulvisConnection> pulvisConnections = new HashMap<>();
    private final Map<PulvisDataSource, PulvisContactSynchronizer> pulvisSynchronizers = new HashMap<>();

    public PulvisConnectionManager(ContactCollection contactCollection) {
        this.contactCollection = contactCollection;
    }

    /**
     * Connects to a Pulvis server via WebSocket and initiates synchronization.
     * 
     * @param pcs the Pulvis connection configuration
     */
    public void connectToServer(PulvisDataSource pcs) {
        // Don't connect if already connected
        if (pulvisConnections.containsKey(pcs)) {
            log.warn("Already connected to Pulvis at {}", pcs.getBaseUrl());
            return;
        }

        // Create connection
        PulvisConnection connection = new PulvisConnection(pcs.getHost(), pcs.getPort());
        pulvisConnections.put(pcs, connection);

        // Create sync folder for this Pulvis instance
        String syncFolderName = String.format(".pulvis-%s-%d", pcs.getHost(), pcs.getPort());
        File syncFolder = new File("conf", syncFolderName);

        // Create synchronizer
        PulvisContactSynchronizer synchronizer = new PulvisContactSynchronizer(
                connection,
                syncFolder,
                contactCollection,
                progress -> {
                    // Progress callback - could update UI here if needed
                    log.debug("Sync progress: {} contacts", progress.get());
                });
        pulvisSynchronizers.put(pcs, synchronizer);

        connection.connect().thenRun(() -> {
            log.info("Connected to Pulvis WS for contacts at {}", pcs.getBaseUrl());

            // Start initial download in background job
            BackgroundJob downloadJob = new BackgroundJob("Download from " + pcs.getHost()) {
                @Override
                protected Void doInBackground() throws Exception {
                    updateStatus("Downloading contacts from server...");
                    setProgress(0);

                    // Download all contacts asynchronously
                    try {
                        int count = synchronizer.downloadAllContacts().get();
                        updateStatus(String.format("Downloaded %d contacts", count));
                        setProgress(100);
                    } catch (Exception e) {
                        updateStatus("Download failed: " + e.getMessage());
                        log.error("Error downloading contacts", e);
                        throw e;
                    }

                    return null;
                }
            };

            JobManager.getInstance().submit(downloadJob);

        }).exceptionally(ex -> {
            log.error("Error connecting to Pulvis WS at {}", pcs.getBaseUrl(), ex);
            pulvisConnections.remove(pcs);
            pulvisSynchronizers.remove(pcs);
            return null;
        });

        connection.addEventListener(ce -> {
            log.info("Received contact event from Pulvis: {}", ce.getEventType());
            synchronizer.handleContactEvent(ce);
        });
    }

    /**
     * Disconnects from a Pulvis server.
     * 
     * @param pcs the Pulvis connection to disconnect
     */
    public void disconnectFromServer(PulvisDataSource pcs) {
        PulvisConnection connection = pulvisConnections.remove(pcs);
        if (connection != null) {
            try {
                connection.disconnect();
                log.info("Disconnected from Pulvis at {}", pcs.getBaseUrl());
            } catch (Exception e) {
                log.error("Error disconnecting from Pulvis at {}", pcs.getBaseUrl(), e);
            }
        }
        pulvisSynchronizers.remove(pcs);
    }

    /**
     * Sends all contacts to connected Pulvis Data Manager servers.
     * Creates a BackgroundJob that uploads all contacts with error handling.
     * 
     * @param parentWindow Parent window for dialogs
     */
    public void sendAllContactsToPulvis(Window parentWindow) {
        // Check if any Pulvis connections exist
        if (pulvisSynchronizers.isEmpty()) {
            GuiUtils.infoMessage(parentWindow, "No Data Manager Connected", 
                "No Pulvis Data Manager connections found.\n\n" +
                "Please add a Pulvis connection in the data sources panel first.");
            return;
        }
        
        // Get all contact files
        List<CompressedContact> allContacts = contactCollection.getAllContacts();
        
        if (allContacts.isEmpty()) {
            GuiUtils.infoMessage(parentWindow, "No Contacts", 
                "No contacts found to send.");
            return;
        }
        
        // Confirm with user
        int result = javax.swing.JOptionPane.showConfirmDialog(
            parentWindow,
            String.format("Send %d contact(s) to %d Pulvis Data Manager server(s)?\n\n" +
                "This may take some time. Existing contacts will be updated.",
                allContacts.size(), pulvisSynchronizers.size()),
            "Confirm Send All",
            javax.swing.JOptionPane.OK_CANCEL_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE
        );
        
        if (result != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        
        // Create background job
        BackgroundJob uploadJob = new BackgroundJob("Send All to Data Manager") {
            @Override
            protected Void doInBackground() throws Exception {
                int totalContacts = allContacts.size();
                int totalServers = pulvisSynchronizers.size();
                int totalUploads = totalContacts * totalServers;
                int completed = 0;
                
                updateStatus(String.format("Uploading %d contacts to %d server(s)...", 
                    totalContacts, totalServers));
                
                // Track failures per server
                Map<String, List<String>> serverFailures = new ConcurrentHashMap<>();
                
                // Upload to each server
                for (Map.Entry<PulvisDataSource, PulvisContactSynchronizer> entry : 
                        pulvisSynchronizers.entrySet()) {
                    
                    if (isCancelled()) {
                        updateStatus("Cancelled by user");
                        return null;
                    }
                    
                    PulvisDataSource dataSource = entry.getKey();
                    PulvisContactSynchronizer synchronizer = entry.getValue();
                    String serverName = dataSource.getHost() + ":" + dataSource.getPort();
                    List<String> failures = new ArrayList<>();
                    serverFailures.put(serverName, failures);
                    
                    updateStatus(String.format("Uploading to %s...", serverName));
                    
                    // Upload each contact
                    for (int i = 0; i < allContacts.size(); i++) {
                        if (isCancelled()) {
                            updateStatus("Cancelled by user");
                            return null;
                        }
                        
                        CompressedContact contact = allContacts.get(i);
                        File zctFile = contact.getZctFile();
                        
                        try {
                            // Upload synchronously and wait for completion
                            synchronizer.uploadContact(zctFile).get(30, java.util.concurrent.TimeUnit.SECONDS);
                            
                        } catch (java.util.concurrent.TimeoutException e) {
                            String msg = "Upload timeout after 30 seconds";
                            failures.add(contact.getContact().getLabel() + ": " + msg);
                            log.warn("Timeout uploading {} to {}", 
                                contact.getContact().getLabel(), serverName);
                                
                        } catch (Exception e) {
                            String errorMsg = e.getCause() != null ? 
                                e.getCause().getMessage() : e.getMessage();
                            
                            // Ignore "already exists" type errors
                            if (errorMsg != null && 
                                (errorMsg.contains("409") || 
                                 errorMsg.toLowerCase().contains("conflict") ||
                                 errorMsg.toLowerCase().contains("already exists"))) {
                                log.debug("Contact {} already exists on {}, skipping", 
                                    contact.getContact().getLabel(), serverName);
                            } else {
                                failures.add(contact.getContact().getLabel() + ": " + errorMsg);
                                log.error("Error uploading {} to {}: {}", 
                                    contact.getContact().getLabel(), serverName, errorMsg);
                            }
                        }
                        
                        completed++;
                        setProgress((completed * 100) / totalUploads);
                        updateStatus(String.format("Uploaded %d/%d contacts...", 
                            completed, totalUploads));
                    }
                }
                
                // Report results
                SwingUtilities.invokeLater(() -> {
                    int totalFailures = serverFailures.values().stream()
                        .mapToInt(List::size).sum();
                    
                    if (totalFailures == 0) {
                        GuiUtils.infoMessage(parentWindow, "Upload Complete", 
                            String.format("Successfully uploaded %d contact(s) to %d server(s).",
                                totalContacts, totalServers));
                    } else {
                        // Show detailed failure report
                        StringBuilder report = new StringBuilder();
                        report.append(String.format("Uploaded with %d failure(s):\n\n", totalFailures));
                        
                        for (Map.Entry<String, List<String>> entry : serverFailures.entrySet()) {
                            if (!entry.getValue().isEmpty()) {
                                report.append(String.format("%s (%d failures):\n", 
                                    entry.getKey(), entry.getValue().size()));
                                for (String failure : entry.getValue()) {
                                    report.append("  • ").append(failure).append("\n");
                                }
                                report.append("\n");
                            }
                        }
                        
                        GuiUtils.errorMessage(parentWindow, "Upload Completed with Errors", 
                            report.toString());
                    }
                });
                
                setProgress(100);
                updateStatus("Completed");
                return null;
            }
        };
        
        JobManager.getInstance().submit(uploadJob);
    }

    /**
     * Saves a contact to all connected Pulvis servers asynchronously.
     * 
     * @param contactId The UUID of the contact
     * @param zctFile The .zct file to upload
     */
    public void saveContact(UUID contactId, File zctFile) {
        log.info("Saving contact {} to Pulvis...", contactId);
        // Upload to all connected Pulvis servers in background
        new Thread(() -> {
            for (PulvisContactSynchronizer synchronizer : pulvisSynchronizers.values()) {
                try {
                    synchronizer.uploadContact(zctFile);
                } catch (Exception e) {
                    log.error("Error uploading contact {} to Pulvis", contactId, e);
                }
            }
        }, "Pulvis-Upload-" + contactId).start();
    }

    /**
     * Closes all Pulvis connections.
     */
    public void closeAll() {
        for (Map.Entry<PulvisDataSource, PulvisConnection> entry : pulvisConnections.entrySet()) {
            try {
                entry.getValue().disconnect();
                log.info("Closed Pulvis connection to {}", entry.getKey().getBaseUrl());
            } catch (Exception e) {
                log.error("Error closing Pulvis connection to {}", entry.getKey().getBaseUrl(), e);
            }
        }
        pulvisConnections.clear();
        pulvisSynchronizers.clear();
    }

    /**
     * Checks if there are any active Pulvis connections.
     */
    public boolean hasConnections() {
        return !pulvisSynchronizers.isEmpty();
    }

    /**
     * Gets the number of active Pulvis connections.
     */
    public int getConnectionCount() {
        return pulvisSynchronizers.size();
    }

    /**
     * Gets a map of all active Pulvis synchronizers.
     */
    public Map<PulvisDataSource, PulvisContactSynchronizer> getSynchronizers() {
        return Collections.unmodifiableMap(pulvisSynchronizers);
    }
}
