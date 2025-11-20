//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.contacts.services;

import lombok.extern.slf4j.Slf4j;
import pt.omst.contacts.watcher.RecursiveFileWatcher;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.contacts.ContactCollection;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages file system watching for contact files (.zct) with automatic refresh,
 * debouncing, and retry logic for handling files still being written.
 */
@Slf4j
public class ContactFileWatcherService {
    private static final long IGNORE_WINDOW_MS = 500; // 500ms after save
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_INITIAL_DELAY_MS = 100;

    private final ContactCollection contactCollection;
    private final Map<File, Long> ignoreOwnWritesUntil = new ConcurrentHashMap<>();
    private RecursiveFileWatcher fileWatcher;
    private boolean autoRefreshEnabled = true;
    private int activeConversionJobs = 0;
    private Consumer<Runnable> edtExecutor = SwingUtilities::invokeLater;

    public ContactFileWatcherService(ContactCollection contactCollection) {
        this.contactCollection = contactCollection;
    }

    /**
     * Starts watching the specified folders for .zct file changes.
     * 
     * @param folders Folders to watch
     * @throws IOException If file watcher cannot be started
     */
    public void startWatching(File... folders) throws IOException {
        if (fileWatcher != null) {
            log.warn("File watcher already started");
            return;
        }

        try {
            fileWatcher = new RecursiveFileWatcher(this::handleFileChange);
            fileWatcher.addExtension("zct");
        } catch (IOException e) {
            log.error("Failed to create file watcher", e);
            throw new RuntimeException("Failed to initialize file watching", e);
        }

        for (File folder : folders) {
            fileWatcher.addRoot(folder);
            log.info("Started watching folder: {}", folder);
        }
    }

    /**
     * Adds a folder to the file watcher.
     * 
     * @param folder Folder to watch
     * @throws IOException If folder cannot be added
     */
    public void addFolder(File folder) throws IOException {
        if (fileWatcher != null && autoRefreshEnabled) {
            fileWatcher.addRoot(folder);
            log.info("Added folder to file watcher: {}", folder);
        }
    }

    /**
     * Removes a folder from the file watcher.
     * 
     * @param folder Folder to stop watching
     */
    public void removeFolder(File folder) {
        if (fileWatcher != null) {
            fileWatcher.removeRoot(folder);
            log.info("Removed folder from file watcher: {}", folder);
        }
    }

    /**
     * Stops the file watcher and releases resources.
     */
    public void stopWatching() {
        if (fileWatcher != null) {
            try {
                fileWatcher.close();
                log.info("File watcher closed");
            } catch (Exception e) {
                log.error("Error closing file watcher", e);
            }
            fileWatcher = null;
        }
    }

    /**
     * Handles file system changes detected by the file watcher.
     * Processes CREATE, MODIFY, and DELETE events for .zct files.
     */
    private void handleFileChange(String eventType, File file) {
        if (!autoRefreshEnabled) {
            log.debug("Auto-refresh disabled, ignoring file change: {}", file);
            return;
        }
        
        // Skip auto-refresh during bulk conversion operations
        if (activeConversionJobs > 0) {
            log.debug("Bulk conversion in progress, ignoring file change: {}", file);
            return;
        }

        // Must run on EDT for UI updates
        edtExecutor.accept(() -> {
            log.debug("File change detected: {} - {}", eventType, file.getName());

            switch (eventType) {
                case "CREATE":
                    if (!shouldIgnoreOwnWrite(file)) {
                        log.info("New contact file created: {}", file.getName());
                        addContactWithRetry(file, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY_MS);
                    }
                    break;

                case "MODIFY":
                    if (!shouldIgnoreOwnWrite(file)) {
                        log.info("Contact file modified: {}", file.getName());
                        refreshContactWithRetry(file, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY_MS);
                    }
                    break;

                case "DELETE":
                    log.info("Contact file deleted: {}", file.getName());
                    contactCollection.removeContact(file);
                    break;

                default:
                    log.warn("Unknown file event type: {}", eventType);
            }
        });
    }

    /**
     * Checks if a file modification should be ignored because it was caused by this
     * application.
     */
    private boolean shouldIgnoreOwnWrite(File file) {
        Long ignoreUntil = ignoreOwnWritesUntil.get(file);
        if (ignoreUntil != null) {
            if (System.currentTimeMillis() < ignoreUntil) {
                log.debug("Ignoring own write for file: {}", file.getName());
                return true;
            }
            ignoreOwnWritesUntil.remove(file); // Expired
        }
        return false;
    }

    /**
     * Adds a contact with retry logic to handle files still being written.
     * Uses a background thread with delays to avoid blocking the EDT.
     * 
     * @param file The .zct file to add
     * @param maxRetries Maximum number of retry attempts
     * @param delayMs Initial delay in milliseconds before first attempt
     */
    private void addContactWithRetry(File file, int maxRetries, int delayMs) {
        IndexedRasterUtils.background(() -> {
            int attempt = 0;
            Exception lastException = null;
            
            while (attempt < maxRetries) {
                try {
                    Thread.sleep(delayMs * (1 << attempt)); // Exponential backoff
                    
                    edtExecutor.accept(() -> {
                        try {
                            contactCollection.addContact(file);
                            log.info("Successfully added contact: {}", file.getName());
                        } catch (IOException e) {
                            log.error("Failed to add contact: {}", file.getName(), e);
                        }
                    });
                    
                    return; // Success
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while adding contact: {}", file.getName());
                    return;
                } catch (Exception e) {
                    lastException = e;
                    attempt++;
                    log.debug("Attempt {}/{} failed to add contact: {}", 
                        attempt, maxRetries, file.getName());
                }
            }
            
            // All retries failed
            log.warn("Failed to add contact {} after {} attempts", 
                file.getName(), maxRetries, lastException);
        });
    }

    /**
     * Refreshes a contact with retry logic to handle files still being written.
     * Uses a background thread with delays to avoid blocking the EDT.
     * 
     * @param file The .zct file to refresh
     * @param maxRetries Maximum number of retry attempts
     * @param delayMs Initial delay in milliseconds before first attempt
     */
    private void refreshContactWithRetry(File file, int maxRetries, int delayMs) {
        IndexedRasterUtils.background(() -> {
            int attempt = 0;
            Exception lastException = null;
            
            while (attempt < maxRetries) {
                try {
                    Thread.sleep(delayMs * (1 << attempt)); // Exponential backoff
                    
                    edtExecutor.accept(() -> {
                        try {
                            contactCollection.refreshContact(file);
                            log.info("Successfully refreshed contact: {}", file.getName());
                        } catch (IOException e) {
                            log.error("Failed to refresh contact: {}", file.getName(), e);
                        }
                    });
                    
                    return; // Success
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while refreshing contact: {}", file.getName());
                    return;
                } catch (Exception e) {
                    lastException = e;
                    attempt++;
                    log.debug("Attempt {}/{} failed to refresh contact: {}", 
                        attempt, maxRetries, file.getName());
                }
            }
            
            // All retries failed
            log.warn("Failed to refresh contact {} after {} attempts", 
                file.getName(), maxRetries, lastException);
        });
    }

    /**
     * Registers a file to ignore for the specified time window.
     * Used to prevent processing our own file modifications.
     * 
     * @param file File to ignore
     * @param windowMs Time window in milliseconds
     */
    public void ignoreFileTemporarily(File file, long windowMs) {
        ignoreOwnWritesUntil.put(file, System.currentTimeMillis() + windowMs);
        log.debug("Ignoring file {} for {}ms", file.getName(), windowMs);
    }

    /**
     * Registers a file to ignore using the default time window.
     * 
     * @param file File to ignore
     */
    public void ignoreFileTemporarily(File file) {
        ignoreFileTemporarily(file, IGNORE_WINDOW_MS);
    }

    /**
     * Increments the active conversion jobs counter.
     * While conversion jobs are active, file changes are ignored.
     */
    public void incrementConversionJobs() {
        synchronized (this) {
            activeConversionJobs++;
            log.debug("Active conversion jobs: {}", activeConversionJobs);
        }
    }

    /**
     * Decrements the active conversion jobs counter.
     */
    public void decrementConversionJobs() {
        synchronized (this) {
            activeConversionJobs--;
            log.debug("Active conversion jobs: {}", activeConversionJobs);
        }
    }

    /**
     * Gets the current count of active conversion jobs.
     */
    public int getActiveConversionJobs() {
        synchronized (this) {
            return activeConversionJobs;
        }
    }

    /**
     * Sets whether auto-refresh is enabled.
     */
    public void setAutoRefreshEnabled(boolean enabled) {
        this.autoRefreshEnabled = enabled;
        log.info("Auto-refresh: {}", enabled);
    }

    /**
     * Checks if auto-refresh is enabled.
     */
    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }

    /**
     * Sets a custom EDT executor for testing purposes.
     */
    void setEdtExecutor(Consumer<Runnable> executor) {
        this.edtExecutor = executor;
    }
}
