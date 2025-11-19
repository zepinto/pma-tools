//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.contacts.sync;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for retrying operations with exponential backoff.
 * 
 * Provides retry logic for network operations that may fail transiently.
 * Uses fixed exponential backoff: 1s, 2s, 4s (3 retries total).
 */
public class RetryHelper {
    
    private static final Logger log = LoggerFactory.getLogger(RetryHelper.class);
    
    // Fixed retry configuration
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000; // 1 second
    
    /**
     * Executes an operation with retry logic.
     * 
     * @param <T> The return type of the operation
     * @param operation The operation to execute
     * @param operationName A descriptive name for logging
     * @return The result of the operation
     * @throws Exception if all retries are exhausted
     */
    public static <T> T retry(Callable<T> operation, String operationName) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {} for: {}", attempt, operationName);
                }
                
                return operation.call();
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < MAX_RETRIES) {
                    long delay = BASE_DELAY_MS * (1L << attempt); // Exponential: 1s, 2s, 4s
                    log.warn("Operation '{}' failed (attempt {}/{}): {}. Retrying in {}ms...", 
                        operationName, attempt + 1, MAX_RETRIES + 1, e.getMessage(), delay);
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    log.error("Operation '{}' failed after {} attempts: {}", 
                        operationName, MAX_RETRIES + 1, e.getMessage());
                }
            }
        }
        
        // All retries exhausted (should never be null at this point)
        if (lastException != null) {
            throw lastException;
        } else {
            throw new RuntimeException("Operation failed: " + operationName);
        }
    }
    
    /**
     * Executes a void operation with retry logic.
     * 
     * @param operation The operation to execute
     * @param operationName A descriptive name for logging
     * @throws Exception if all retries are exhausted
     */
    public static void retryVoid(VoidCallable operation, String operationName) throws Exception {
        retry(() -> {
            operation.call();
            return null;
        }, operationName);
    }
    
    /**
     * Functional interface for void operations.
     */
    @FunctionalInterface
    public interface VoidCallable {
        void call() throws Exception;
    }
}
