//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.contacts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.lsts.neptus.util.ZipUtils;
import pt.omst.contacts.sync.RetryHelper;
import pt.omst.pulvis.PulvisConnection;
import pt.omst.pulvis.PulvisConnection.ContactEvent;
import pt.omst.pulvis.api.ContactsApi;
import pt.omst.pulvis.invoker.ApiException;
import pt.omst.pulvis.model.ContactResponse;
import pt.omst.pulvis.model.ContactUpdateRequest;
import pt.omst.pulvis.model.PageContactResponse;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

/**
 * Handles bidirectional synchronization of contacts between local storage and a Pulvis server.
 * 
 * Features:
 * - Initial download of all contacts from remote server
 * - Real-time updates via WebSocket events
 * - UUID-based contact matching
 * - Conflict resolution using "last write wins" strategy
 * - Upload of local changes to remote server
 */
public class PulvisContactSynchronizer {
    
    private static final Logger log = LoggerFactory.getLogger(PulvisContactSynchronizer.class);
    
    // Configuration
    private static final int PAGE_SIZE = 50;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    
    private final PulvisConnection connection;
    private final File syncFolder;
    private final ContactCollection contactCollection;
    private final Consumer<AtomicInteger> progressCallback;
    
    // Track which UUIDs are managed by this synchronizer
    private final Map<UUID, File> uuidToFileMap = new ConcurrentHashMap<>();
    
    // Track sync status
    private volatile boolean initialSyncComplete = false;
    private final AtomicInteger downloadedCount = new AtomicInteger(0);
    private final AtomicInteger uploadedCount = new AtomicInteger(0);
    private final AtomicInteger conflictsResolved = new AtomicInteger(0);
    
    // Track failures
    private final Map<String, String> downloadFailures = new ConcurrentHashMap<>();
    private final Map<String, String> uploadFailures = new ConcurrentHashMap<>();
    
    /**
     * Creates a new synchronizer for a Pulvis connection.
     * 
     * @param connection The Pulvis connection to sync with
     * @param syncFolder The local folder to store synchronized contacts
     * @param contactCollection The contact collection to update
     * @param progressCallback Optional callback to receive progress updates (current count)
     */
    public PulvisContactSynchronizer(PulvisConnection connection, File syncFolder, 
                                     ContactCollection contactCollection,
                                     Consumer<AtomicInteger> progressCallback) {
        this.connection = connection;
        this.syncFolder = syncFolder;
        this.contactCollection = contactCollection;
        this.progressCallback = progressCallback;
        
        // Ensure sync folder exists
        if (!syncFolder.exists()) {
            if (syncFolder.mkdirs()) {
                log.info("Created sync folder: {}", syncFolder.getAbsolutePath());
            } else {
                log.error("Failed to create sync folder: {}", syncFolder.getAbsolutePath());
            }
        }
        
        // Build initial UUID map from existing files in sync folder
        buildUuidMap();
    }
    
    /**
     * Builds the UUID to file mapping from existing files in the sync folder.
     */
    private void buildUuidMap() {
        File[] files = syncFolder.listFiles((dir, name) -> name.endsWith(".zct"));
        if (files != null) {
            for (File file : files) {
                try {
                    pt.omst.rasterlib.Contact contact = CompressedContact.extractCompressedContact(file);
                    if (contact.getUuid() != null) {
                        uuidToFileMap.put(contact.getUuid(), file);
                        log.debug("Mapped UUID {} to file {}", contact.getUuid(), file.getName());
                    }
                } catch (IOException e) {
                    log.warn("Failed to read contact UUID from {}: {}", file.getName(), e.getMessage());
                }
            }
            log.info("Built UUID map with {} contacts from sync folder", uuidToFileMap.size());
        }
    }
    
    /**
     * Performs initial download of all contacts from the Pulvis server with pagination.
     * Uses parallel downloads for improved performance.
     * 
     * @return CompletableFuture that completes with the count of downloaded contacts
     */
    public CompletableFuture<Integer> downloadAllContacts() {
        log.info("Starting initial contact download from Pulvis at {}", connection.getHostname());
        
        return CompletableFuture.supplyAsync(() -> {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            AtomicInteger downloaded = new AtomicInteger(0);
            AtomicInteger totalContacts = new AtomicInteger(0);
            
            try {
                ContactsApi api = connection.getContactsApi();
                
                // Fetch first page to get total count
                PageContactResponse firstPage = api.listContacts(0, PAGE_SIZE, null);
                Long total = firstPage.getTotalElements();
                if (total != null) {
                    totalContacts.set(total.intValue());
                    log.info("Found {} contacts on remote server", total);
                }
                
                // Calculate total pages
                int totalPages = firstPage.getTotalPages() != null ? firstPage.getTotalPages() : 1;
                
                // Process first page
                processContactPage(firstPage.getContent(), downloaded, totalContacts, executor);
                
                // Fetch and process remaining pages in parallel
                List<CompletableFuture<Void>> pageFutures = new ArrayList<>();
                for (int page = 1; page < totalPages; page++) {
                    final int currentPage = page;
                    CompletableFuture<Void> pageFuture = CompletableFuture.runAsync(() -> {
                        try {
                            PageContactResponse pageResponse = RetryHelper.retry(
                                () -> api.listContacts(currentPage, PAGE_SIZE, null),
                                "List contacts page " + currentPage
                            );
                            processContactPage(pageResponse.getContent(), downloaded, totalContacts, executor);
                        } catch (Exception e) {
                            log.error("Failed to fetch page {}: {}", currentPage, e.getMessage());
                            downloadFailures.put("Page " + currentPage, e.getMessage());
                        }
                    }, executor);
                    pageFutures.add(pageFuture);
                }
                
                // Wait for all pages to complete
                CompletableFuture.allOf(pageFutures.toArray(new CompletableFuture[0])).join();
                
                // Shutdown executor and wait for completion
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                    log.warn("Executor did not terminate in time, forcing shutdown");
                    executor.shutdownNow();
                }
                
                downloadedCount.set(downloaded.get());
                initialSyncComplete = true;
                log.info("Initial sync complete: {} contacts downloaded", downloaded.get());
                
                // Show error dialog if there were failures
                if (!downloadFailures.isEmpty()) {
                    showErrorDialog("Download Failures", downloadFailures);
                }
                
                return downloaded.get();
                
            } catch (Exception e) {
                log.error("Failed to download contacts from Pulvis: {}", e.getMessage());
                downloadFailures.put("Initial sync", e.getMessage());
                showErrorDialog("Download Failed", downloadFailures);
                throw new RuntimeException("Initial sync failed", e);
            } finally {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                }
            }
        });
    }
    
    /**
     * Processes a page of contacts by submitting download tasks to the executor.
     */
    private void processContactPage(List<ContactResponse> contacts, AtomicInteger downloaded, 
                                     AtomicInteger totalContacts, ExecutorService executor) {
        for (ContactResponse remoteContact : contacts) {
            executor.submit(() -> {
                try {
                    if (downloadContactResponse(remoteContact)) {
                        int current = downloaded.incrementAndGet();
                        if (progressCallback != null) {
                            progressCallback.accept(downloaded);
                        }
                        if (current % 10 == 0) {
                            log.info("Download progress: {}/{}", current, totalContacts.get());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to download contact {}: {}", 
                        remoteContact.getLabel(), e.getMessage());
                    downloadFailures.put(remoteContact.getLabel(), e.getMessage());
                }
            });
        }
    }
    
    /**
     * Downloads a single contact from the server and saves it locally.
     * Applies conflict resolution if a local contact with the same UUID exists.
     * 
     * @param remoteContact The contact response from API to download
     * @return true if contact was downloaded, false if skipped
     * @throws IOException if download fails
     */
    private boolean downloadContactResponse(ContactResponse remoteContact) throws IOException {
        UUID uuid = remoteContact.getUuid();
        if (uuid == null) {
            log.warn("Skipping contact without UUID: {}", remoteContact.getLabel());
            return false;
        }
        
        // Check if we already have this contact locally
        File existingFile = uuidToFileMap.get(uuid);
        
        if (existingFile != null && existingFile.exists()) {
            // Conflict: local and remote both have this UUID
            pt.omst.rasterlib.Contact localContact = CompressedContact.extractCompressedContact(existingFile);
            
            if (needsUpdate(localContact, remoteContact)) {
                log.info("Remote contact {} is newer, updating local version", remoteContact.getLabel());
                conflictsResolved.incrementAndGet();
                // Download will overwrite local file
            } else {
                log.debug("Local contact {} is up to date, skipping download", localContact.getLabel());
                return false;
            }
        }
        
        // Download the contact
        File targetFile = new File(syncFolder, sanitizeFilename(remoteContact.getLabel()) + ".zct");
        
        try {
            // Get the contact with full data including observations using retry
            ContactResponse fullContact = RetryHelper.retry(
                () -> connection.getContactsApi().getContact(uuid),
                "Download contact " + remoteContact.getLabel()
            );
            
            // Create a temporary directory for contact assembly
            File tempDir = Files.createTempDirectory("pulvis_contact_").toFile();
            File contactJsonFile = new File(tempDir, "contact.json");
            
            try {
                // Write contact.json
                String contactJson = pt.omst.rasterlib.Converter.ContactToJsonString(
                    convertPulvisToLocalContact(fullContact)
                );
                Files.writeString(contactJsonFile.toPath(), contactJson);
                
                // TODO: Download raster files if available
                // For now, we create a minimal contact with just the JSON
                
                // Zip the directory to create .zct file
                if (!ZipUtils.zipDir(targetFile.getAbsolutePath(), tempDir.getAbsolutePath())) {
                    throw new IOException("Failed to create contact ZIP file");
                }
                
                log.info("Downloaded contact {} to {}", fullContact.getLabel(), targetFile.getName());
                
                // Update UUID map and contact collection
                uuidToFileMap.put(uuid, targetFile);
                contactCollection.addContact(targetFile);
                
                return true;
                
            } finally {
                // Clean up temp directory
                deleteDirectory(tempDir);
            }
            
        } catch (Exception e) {
            log.error("Failed to download contact {}: {}", remoteContact.getLabel(), e.getMessage());
            throw new IOException("Download failed", e);
        }
    }
    
    /**
     * Converts a Pulvis API ContactResponse to a local Contact object.
     */
    private pt.omst.rasterlib.Contact convertPulvisToLocalContact(ContactResponse pulvisContact) {
        pt.omst.rasterlib.Contact localContact = new pt.omst.rasterlib.Contact();
        localContact.setUuid(pulvisContact.getUuid());
        localContact.setLabel(pulvisContact.getLabel());
        localContact.setLatitude(pulvisContact.getLatitude());
        localContact.setLongitude(pulvisContact.getLongitude());
        localContact.setDepth(pulvisContact.getDepth());
        
        // Convert observations
        if (pulvisContact.getObservations() != null) {
            List<Observation> localObservations = new ArrayList<>();
            for (pt.omst.pulvis.model.ObservationResponse pulvisObs : pulvisContact.getObservations()) {
                Observation localObs = convertPulvisToLocalObservation(pulvisObs);
                localObservations.add(localObs);
            }
            localContact.setObservations(localObservations);
        }
        
        return localContact;
    }
    
    /**
     * Converts a Pulvis API ObservationResponse to a local Observation object.
     */
    private Observation convertPulvisToLocalObservation(pt.omst.pulvis.model.ObservationResponse pulvisObs) {
        Observation localObs = new Observation();
        localObs.setUuid(pulvisObs.getUuid());
        localObs.setLatitude(pulvisObs.getLatitude());
        localObs.setLongitude(pulvisObs.getLongitude());
        localObs.setDepth(pulvisObs.getDepth());
        if (pulvisObs.getTimestamp() != null) {
            try {
                localObs.setTimestamp(OffsetDateTime.parse(pulvisObs.getTimestamp()));
            } catch (Exception e) {
                log.warn("Failed to parse timestamp: {}", pulvisObs.getTimestamp());
            }
        }
        localObs.setSystemName(pulvisObs.getSystemName());
        localObs.setUserName(pulvisObs.getUserName());
        
        // TODO: Convert annotations and other fields as needed
        
        return localObs;
    }
    
    /**
     * Determines if a local contact needs to be updated based on remote version.
     * Uses "last write wins" strategy by comparing observation timestamps.
     * 
     * @param local The local contact
     * @param remote The remote contact response
     * @return true if local should be updated with remote version
     */
    private boolean needsUpdate(pt.omst.rasterlib.Contact local, ContactResponse remote) {
        OffsetDateTime localTimestamp = getLatestTimestamp(local);
        OffsetDateTime remoteTimestamp = getLatestTimestamp(remote);
        
        if (localTimestamp == null && remoteTimestamp == null) {
            return false; // Both have no timestamps, keep local
        }
        if (localTimestamp == null) {
            return true; // Remote has timestamp, local doesn't
        }
        if (remoteTimestamp == null) {
            return false; // Local has timestamp, remote doesn't
        }
        
        // Remote is newer if its timestamp is after local
        return remoteTimestamp.isAfter(localTimestamp);
    }
    
    /**
     * Gets the latest timestamp from a local contact's observations.
     */
    private OffsetDateTime getLatestTimestamp(pt.omst.rasterlib.Contact contact) {
        if (contact.getObservations() == null || contact.getObservations().isEmpty()) {
            return null;
        }
        
        return contact.getObservations().stream()
            .map(Observation::getTimestamp)
            .filter(ts -> ts != null)
            .max(OffsetDateTime::compareTo)
            .orElse(null);
    }
    
    /**
     * Gets the latest timestamp from a remote contact's observations.
     */
    private OffsetDateTime getLatestTimestamp(ContactResponse contact) {
        if (contact.getObservations() == null || contact.getObservations().isEmpty()) {
            return null;
        }
        
        return contact.getObservations().stream()
            .map(obs -> {
                try {
                    String ts = obs.getTimestamp();
                    return ts != null ? OffsetDateTime.parse(ts) : null;
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(ts -> ts != null)
            .max(OffsetDateTime::compareTo)
            .orElse(null);
    }
    
    /**
     * Handles a contact event from the WebSocket connection.
     * 
     * @param event The contact event (ADDED, UPDATED, DELETED)
     */
    public void handleContactEvent(ContactEvent event) {
        log.info("Received contact event: {} for contact {}", 
            event.getEventType(), event.getContactId());
        
        try {
            switch (event.getEventType()) {
                case ADDED, CREATED -> {
                    // Need to fetch full contact from API since event may have partial data
                    try {
                        UUID uuid = UUID.fromString(event.getContactId());
                        ContactResponse fullContact = RetryHelper.retry(
                            () -> connection.getContactsApi().getContact(uuid),
                            "Fetch contact for ADD event: " + event.getContactId()
                        );
                        handleContactAdded(fullContact);
                    } catch (Exception e) {
                        log.error("Error handling ADD event: {}", e.getMessage());
                        downloadFailures.put(event.getContactId(), e.getMessage());
                    }
                }
                case UPDATED -> {
                    try {
                        UUID uuid = UUID.fromString(event.getContactId());
                        ContactResponse fullContact = RetryHelper.retry(
                            () -> connection.getContactsApi().getContact(uuid),
                            "Fetch contact for UPDATE event: " + event.getContactId()
                        );
                        handleContactUpdated(fullContact);
                    } catch (Exception e) {
                        log.error("Error handling UPDATE event: {}", e.getMessage());
                        downloadFailures.put(event.getContactId(), e.getMessage());
                    }
                }
                case DELETED -> handleContactDeleted(UUID.fromString(event.getContactId()));
            }
        } catch (Exception e) {
            log.error("Error handling contact event {}: {}", event.getEventType(), e.getMessage(), e);
        }
    }
    
    /**
     * Handles a contact being added or created on the remote server.
     */
    private void handleContactAdded(ContactResponse contact) throws IOException {
        if (downloadContactResponse(contact)) {
            log.info("Added new contact from remote: {}", contact.getLabel());
        }
    }
    
    /**
     * Handles a contact being updated on the remote server.
     */
    private void handleContactUpdated(ContactResponse contact) throws IOException {
        if (downloadContactResponse(contact)) {
            log.info("Updated contact from remote: {}", contact.getLabel());
        }
    }
    
    /**
     * Handles a contact being deleted on the remote server.
     */
    private void handleContactDeleted(UUID uuid) {
        File localFile = uuidToFileMap.remove(uuid);
        if (localFile != null && localFile.exists()) {
            try {
                Files.delete(localFile.toPath());
                contactCollection.removeContact(localFile);
                log.info("Deleted local contact for UUID: {}", uuid);
            } catch (IOException e) {
                log.error("Failed to delete local contact file: {}", localFile, e);
            }
        }
    }
    
    /**
     * Uploads a local contact file to the Pulvis server.
     * 
     * @param zctFile The local contact file to upload
     * @return CompletableFuture that completes when upload is done
     */
    public CompletableFuture<Void> uploadContact(File zctFile) {
        return CompletableFuture.runAsync(() -> {
            try {
                pt.omst.rasterlib.Contact contact = CompressedContact.extractCompressedContact(zctFile);
                UUID uuid = contact.getUuid();
                
                if (uuid == null) {
                    log.warn("Cannot upload contact without UUID: {}", zctFile.getName());
                    uploadFailures.put(zctFile.getName(), "Missing UUID");
                    return;
                }
                
                ContactsApi api = connection.getContactsApi();
                
                // Check if contact exists on server
                RetryHelper.retryVoid(() -> {
                    try {
                        api.getContact(uuid);
                        // Contact exists, update it
                        log.info("Updating existing contact {} on Pulvis", contact.getLabel());
                        
                        // Build update request from contact data
                        ContactUpdateRequest updateRequest = buildUpdateRequest(contact);
                        api.updateContact(uuid, updateRequest);
                        uploadedCount.incrementAndGet();
                        
                    } catch (ApiException e) {
                        if (e.getCode() == 404) {
                            // Contact doesn't exist, create it
                            log.info("Creating new contact {} on Pulvis", contact.getLabel());
                            api.createContact(zctFile);
                            uploadedCount.incrementAndGet();
                        } else {
                            throw e;
                        }
                    }
                }, "Upload contact " + contact.getLabel());
                
                log.info("Successfully uploaded contact {}", contact.getLabel());
                
            } catch (Exception e) {
                log.error("Failed to upload contact {}: {}", zctFile.getName(), e.getMessage(), e);
                uploadFailures.put(zctFile.getName(), e.getMessage());
            }
        });
    }
    
    /**
     * Builds a ContactUpdateRequest from a local Contact object.
     */
    private ContactUpdateRequest buildUpdateRequest(pt.omst.rasterlib.Contact contact) {
        ContactUpdateRequest request = new ContactUpdateRequest();
        request.setLabel(contact.getLabel());
        request.setLatitude(contact.getLatitude());
        request.setLongitude(contact.getLongitude());
        request.setDepth(contact.getDepth());
        
        // Extract classification and confidence from annotations
        if (contact.getObservations() != null && !contact.getObservations().isEmpty()) {
            Observation firstObs = contact.getObservations().get(0);
            if (firstObs.getAnnotations() != null) {
                for (Annotation annot : firstObs.getAnnotations()) {
                    if (annot.getAnnotationType() == AnnotationType.CLASSIFICATION) {
                        request.setClassification(annot.getCategory());
                        if (annot.getConfidence() != null) {
                            request.setConfidence(annot.getConfidence());
                        }
                    } else if (annot.getAnnotationType() == AnnotationType.LABEL) {
                        // Collect labels as tags
                        if (request.getTags() == null) {
                            request.setTags(new ArrayList<>());
                        }
                        if (annot.getText() != null) {
                            request.getTags().add(annot.getText());
                        }
                    }
                }
            }
        }
        
        return request;
    }
    
    /**
     * Finds a local contact file by its UUID.
     * 
     * @param uuid The UUID to search for
     * @return The contact file, or null if not found
     */
    public File findLocalContactByUuid(UUID uuid) {
        return uuidToFileMap.get(uuid);
    }
    
    /**
     * Gets the sync folder for this synchronizer.
     * 
     * @return The sync folder
     */
    public File getSyncFolder() {
        return syncFolder;
    }
    
    /**
     * Checks if the initial sync is complete.
     * 
     * @return true if initial sync completed
     */
    public boolean isInitialSyncComplete() {
        return initialSyncComplete;
    }
    
    /**
     * Gets statistics about this synchronizer.
     * 
     * @return Map of statistic names to values
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("downloaded", downloadedCount.get());
        stats.put("uploaded", uploadedCount.get());
        stats.put("conflicts_resolved", conflictsResolved.get());
        stats.put("tracked_contacts", uuidToFileMap.size());
        return stats;
    }
    
    /**
     * Sanitizes a filename by removing/replacing invalid characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "contact_" + UUID.randomUUID();
        }
        // Remove invalid filename characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
    
    /**
     * Shows an error dialog with a list of failures.
     */
    private void showErrorDialog(String title, Map<String, String> failures) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder message = new StringBuilder();
            message.append("The following errors occurred:\n\n");
            
            for (Map.Entry<String, String> entry : failures.entrySet()) {
                message.append("• ").append(entry.getKey()).append(":\n");
                message.append("  ").append(entry.getValue()).append("\n\n");
            }
            
            JTextArea textArea = new JTextArea(message.toString());
            textArea.setEditable(false);
            textArea.setRows(15);
            textArea.setColumns(60);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            
            JScrollPane scrollPane = new JScrollPane(textArea);
            JOptionPane.showMessageDialog(null, scrollPane, title, JOptionPane.ERROR_MESSAGE);
        });
    }
}
