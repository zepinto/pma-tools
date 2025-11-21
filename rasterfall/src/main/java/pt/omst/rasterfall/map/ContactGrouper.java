//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.map;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.ZipUtils;
import pt.omst.contacts.browser.ContactGroupingHandler;
import pt.omst.rasterlib.contacts.CompressedContact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handles grouping/merging of multiple contacts into a single contact.
 * Similar to TargetManager grouping but standalone without Pulvis synchronization.
 */
@Slf4j
public class ContactGrouper implements ContactGroupingHandler {
    
    private Runnable onGroupingComplete;
    
    public ContactGrouper() {
    }
    
    /**
     * Sets a callback to be invoked when grouping completes successfully.
     */
    public void setOnGroupingComplete(Runnable callback) {
        this.onGroupingComplete = callback;
    }
    
    /**
     * Groups multiple contacts into a main contact asynchronously.
     * Merges observations, raster files, and annotations from merge contacts into main contact.
     * Deletes merged contacts on success, rolls back on failure.
     * 
     * @param mainContact   The contact to merge into
     * @param mergeContacts The contacts to merge (will be deleted)
     */
    public void groupContactsAsync(CompressedContact mainContact, List<CompressedContact> mergeContacts) {
        CompletableFuture.runAsync(() -> {
            List<File> backupFiles = new ArrayList<>();
            Path tempDirMutable = null;

            try {
                // Step 1: Extract main contact to temp directory
                tempDirMutable = Files.createTempDirectory("contact_group_");
                final Path tempDir = tempDirMutable; // Make effectively final for lambda
                log.info("Extracting main contact to temp directory: {}", tempDir);

                if (!ZipUtils.unZip(mainContact.getZctFile().getAbsolutePath(), tempDir.toString())) {
                    throw new IOException("Failed to extract main contact ZIP");
                }

                // Step 2: Load main contact data
                pt.omst.rasterlib.Contact mainContactData = pt.omst.rasterlib.contacts.CompressedContact
                        .extractCompressedContact(mainContact.getZctFile());

                if (mainContactData.getObservations() == null) {
                    mainContactData.setObservations(new ArrayList<>());
                }

                int initialObsCount = mainContactData.getObservations().size();
                log.debug("Initial main contact has {} observations", initialObsCount);

                // Step 3: Collect all labels for deduplication
                Set<String> labelSet = new HashSet<>();
                for (pt.omst.rasterlib.Observation obs : mainContactData.getObservations()) {
                    if (obs.getAnnotations() != null) {
                        for (pt.omst.rasterlib.Annotation ann : obs.getAnnotations()) {
                            if (ann.getAnnotationType() == pt.omst.rasterlib.AnnotationType.LABEL
                                    && ann.getText() != null && !ann.getText().trim().isEmpty()) {
                                labelSet.add(ann.getText().toLowerCase());
                            }
                        }
                    }
                }

                // Step 4: Process each merge contact
                for (CompressedContact mergeContact : mergeContacts) {
                    log.info("Processing merge contact: {}", mergeContact.getContact().getLabel());

                    // Extract merge contact data
                    pt.omst.rasterlib.Contact mergeContactData = pt.omst.rasterlib.contacts.CompressedContact
                            .extractCompressedContact(mergeContact.getZctFile());

                    if (mergeContactData.getObservations() == null) {
                        log.debug("Merge contact has no observations, skipping");
                        continue;
                    }

                    log.debug("Merge contact has {} observations", mergeContactData.getObservations().size());

                    // Extract merge contact to temp location for raster file access
                    Path mergeTempDir = Files.createTempDirectory("contact_merge_");
                    try {
                        if (!ZipUtils.unZip(mergeContact.getZctFile().getAbsolutePath(), mergeTempDir.toString())) {
                            log.warn("Failed to extract merge contact ZIP: {}", mergeContact.getZctFile());
                            continue;
                        }

                        // Process each observation
                        for (pt.omst.rasterlib.Observation obs : mergeContactData.getObservations()) {
                            // Ensure observation has a UUID (generate if missing)
                            if (obs.getUuid() == null) {
                                obs.setUuid(java.util.UUID.randomUUID());
                                log.debug("Generated UUID for observation without UUID");
                            }

                            log.debug("Processing observation {} with raster: {}", obs.getUuid(), obs.getRasterFilename());

                            // Create new observation copy (preserve UUID)
                            pt.omst.rasterlib.Observation newObs = new pt.omst.rasterlib.Observation();
                            newObs.setUuid(obs.getUuid());
                            newObs.setDepth(obs.getDepth());
                            newObs.setLatitude(obs.getLatitude());
                            newObs.setLongitude(obs.getLongitude());
                            newObs.setSystemName(obs.getSystemName());
                            newObs.setTimestamp(obs.getTimestamp());
                            newObs.setUserName(obs.getUserName());

                            // Copy raster file if exists
                            if (obs.getRasterFilename() != null && !obs.getRasterFilename().isEmpty()) {
                                copyRasterFile(obs, newObs, mergeTempDir, tempDir, sourceRasterFile -> {
                                    // Copy associated image file
                                    copyImageFile(sourceRasterFile, mergeTempDir, tempDir, obs.getUuid());
                                });
                            }

                            // Filter and copy annotations (exclude CLASSIFICATION, deduplicate labels)
                            List<pt.omst.rasterlib.Annotation> newAnnotations = new ArrayList<>();
                            if (obs.getAnnotations() != null) {
                                for (pt.omst.rasterlib.Annotation ann : obs.getAnnotations()) {
                                    // Skip CLASSIFICATION type
                                    if (ann.getAnnotationType() == pt.omst.rasterlib.AnnotationType.CLASSIFICATION) {
                                        continue;
                                    }

                                    // For LABEL annotations, deduplicate case-insensitively
                                    if (ann.getAnnotationType() == pt.omst.rasterlib.AnnotationType.LABEL) {
                                        if (ann.getText() == null || ann.getText().trim().isEmpty()) {
                                            continue;
                                        }
                                        String labelKey = ann.getText().toLowerCase();
                                        if (labelSet.contains(labelKey)) {
                                            continue;
                                        }
                                        labelSet.add(labelKey);
                                    }

                                    newAnnotations.add(ann);
                                }
                            }
                            newObs.setAnnotations(newAnnotations);

                            // Add observation to main contact
                            mainContactData.getObservations().add(newObs);
                            log.debug("Added observation {} to main contact, total now: {}",
                                    newObs.getUuid(), mainContactData.getObservations().size());
                        }
                    } finally {
                        // Clean up merge temp directory
                        cleanupDirectory(mergeTempDir);
                    }
                }

                // Step 5: Save updated contact.json
                int finalObsCount = mainContactData.getObservations().size();
                log.debug("Saving merged contact with {} observations (started with {}, added {})",
                        finalObsCount, initialObsCount, finalObsCount - initialObsCount);
                String updatedJson = pt.omst.rasterlib.Converter.ContactToJsonString(mainContactData);
                File contactJsonFile = tempDir.resolve("contact.json").toFile();
                Files.writeString(contactJsonFile.toPath(), updatedJson);

                // Step 6: Repackage main contact ZIP
                File mainZctFile = mainContact.getZctFile();
                File backupMainZct = new File(mainZctFile.getParentFile(), mainZctFile.getName() + ".backup");
                Files.copy(mainZctFile.toPath(), backupMainZct.toPath(), StandardCopyOption.REPLACE_EXISTING);
                backupFiles.add(backupMainZct);

                if (!ZipUtils.zipDir(mainZctFile.getAbsolutePath(), tempDir.toString())) {
                    throw new IOException("Failed to repackage main contact ZIP");
                }

                log.info("Successfully saved merged contact: {}", mainZctFile);

                // Step 7: Backup and delete merged contacts
                for (CompressedContact mergeContact : mergeContacts) {
                    File mergeZctFile = mergeContact.getZctFile();
                    File backupMergeZct = new File(mergeZctFile.getParentFile(), mergeZctFile.getName() + ".backup");
                    Files.copy(mergeZctFile.toPath(), backupMergeZct.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    backupFiles.add(backupMergeZct);

                    if (!mergeZctFile.delete()) {
                        log.warn("Failed to delete merged contact: {}", mergeZctFile);
                    } else {
                        log.info("Deleted merged contact: {}", mergeZctFile);
                    }
                }

                // Step 8: Clean up backup files on success
                for (File backup : backupFiles) {
                    if (backup.exists() && !backup.delete()) {
                        log.warn("Failed to delete backup file: {}", backup);
                    }
                }

                log.info("Contact grouping completed successfully");
                
                // Notify completion (caller should reload ContactCollection from disk)
                if (onGroupingComplete != null) {
                    onGroupingComplete.run();
                }

            } catch (Exception e) {
                log.error("Error grouping contacts, rolling back changes", e);

                // Rollback: restore from backups
                for (File backup : backupFiles) {
                    try {
                        if (backup.exists()) {
                            String originalPath = backup.getAbsolutePath().replace(".backup", "");
                            Files.copy(backup.toPath(), new File(originalPath).toPath(),
                                    StandardCopyOption.REPLACE_EXISTING);
                            backup.delete();
                            log.info("Restored from backup: {}", backup);
                        }
                    } catch (Exception rollbackEx) {
                        log.error("Failed to rollback file: {}", backup, rollbackEx);
                    }
                }

                throw new RuntimeException("Failed to group contacts: " + e.getMessage(), e);
            } finally {
                // Clean up temp directory
                if (tempDirMutable != null) {
                    cleanupDirectory(tempDirMutable);
                }
            }
        });
    }
    
    private void copyRasterFile(pt.omst.rasterlib.Observation obs, pt.omst.rasterlib.Observation newObs,
                                Path mergeTempDir, Path tempDir, java.util.function.Consumer<File> onSuccess) {
        File sourceRasterFile = mergeTempDir.resolve(obs.getRasterFilename()).toFile();
        if (sourceRasterFile.exists()) {
            try {
                String targetFilename = obs.getRasterFilename();
                File targetRasterFile = tempDir.resolve(targetFilename).toFile();

                // Handle filename conflict by postfixing observation UUID
                if (targetRasterFile.exists()) {
                    String baseName = targetFilename;
                    String extension = "";
                    int lastDot = targetFilename.lastIndexOf('.');
                    if (lastDot > 0) {
                        baseName = targetFilename.substring(0, lastDot);
                        extension = targetFilename.substring(lastDot);
                    }
                    targetFilename = baseName + "_" + obs.getUuid().toString() + extension;
                    targetRasterFile = tempDir.resolve(targetFilename).toFile();
                    log.info("Raster filename conflict resolved: {} -> {}", obs.getRasterFilename(), targetFilename);
                }

                Files.copy(sourceRasterFile.toPath(), targetRasterFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                newObs.setRasterFilename(targetFilename);
                log.debug("Copied raster file: {} -> {}", obs.getRasterFilename(), targetFilename);
                
                if (onSuccess != null) {
                    onSuccess.accept(sourceRasterFile);
                }
            } catch (Exception e) {
                log.warn("Failed to copy raster file: {}", sourceRasterFile, e);
                newObs.setRasterFilename(obs.getRasterFilename());
            }
        } else {
            log.warn("Raster file not found: {}", sourceRasterFile);
            newObs.setRasterFilename(obs.getRasterFilename());
        }
    }
    
    private void copyImageFile(File sourceRasterFile, Path mergeTempDir, Path tempDir, UUID obsUuid) {
        try {
            String rasterJson = Files.readString(sourceRasterFile.toPath());
            pt.omst.rasterlib.IndexedRaster indexedRaster = 
                pt.omst.rasterlib.Converter.IndexedRasterFromJsonString(rasterJson);
            
            if (indexedRaster.getFilename() != null) {
                File sourceImageFile = mergeTempDir.resolve(indexedRaster.getFilename()).toFile();
                if (sourceImageFile.exists()) {
                    String imageFilename = indexedRaster.getFilename();
                    File targetImageFile = tempDir.resolve(imageFilename).toFile();

                    // Handle image filename conflict
                    if (targetImageFile.exists()) {
                        String imageBaseName = imageFilename;
                        String imageExtension = "";
                        int imageDot = imageFilename.lastIndexOf('.');
                        if (imageDot > 0) {
                            imageBaseName = imageFilename.substring(0, imageDot);
                            imageExtension = imageFilename.substring(imageDot);
                        }
                        imageFilename = imageBaseName + "_" + obsUuid.toString() + imageExtension;
                        targetImageFile = tempDir.resolve(imageFilename).toFile();

                        // Update the raster JSON with new image filename
                        indexedRaster.setFilename(imageFilename);
                        String updatedRasterJson = pt.omst.rasterlib.Converter.IndexedRasterToJsonString(indexedRaster);
                        Files.writeString(new File(tempDir.toFile(), 
                            new File(sourceRasterFile.getName()).getName()).toPath(), updatedRasterJson);
                        log.debug("Image filename conflict resolved: {} -> {}", 
                            sourceImageFile.getName(), imageFilename);
                    }

                    Files.copy(sourceImageFile.toPath(), targetImageFile.toPath(), 
                        StandardCopyOption.REPLACE_EXISTING);
                    log.debug("Copied image file: {}", imageFilename);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to copy associated image file for raster {}: {}", 
                sourceRasterFile.getName(), e.getMessage());
        }
    }
    
    private void cleanupDirectory(Path directory) {
        try {
            Files.walk(directory)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            log.warn("Failed to clean up directory: {}", directory, e);
        }
    }
}
