//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.util.RecursiveFileWatcher;

/**
 * A collection of contacts.
 */
@Slf4j
public class ContactCollection implements MapPainter {
    private QuadTree<File, CompressedContact> quadTree = new QuadTree<>();
    private List<File> filteredContacts = new ArrayList<>();
    private CopyOnWriteArrayList<RecursiveFileWatcher> folderWatchers = new CopyOnWriteArrayList<>();
    
    private QuadTree.Region currentRegion = null;
    private Instant currentStart = null;
    private Instant currentEnd = null;
    private Set<String> currentClassifications = null;
    private Set<String> currentConfidences = null;
    private Set<String> currentLabels = null;

    private CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    
    // Track selections using weak references to avoid memory leaks
    private final List<WeakReference<ContactsSelection>> selections = new CopyOnWriteArrayList<>();
    
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * Registers a selection to receive change notifications.
     * Uses weak references to avoid memory leaks.
     * 
     * @param selection The selection to register
     */
    void registerSelection(ContactsSelection selection) {
        // Clean up any garbage-collected references first
        cleanupSelections();
        selections.add(new WeakReference<>(selection));
    }

    /**
     * Removes garbage-collected selection references.
     */
    private void cleanupSelections() {
        selections.removeIf(ref -> ref.get() == null);
    }

    /**
     * Notifies all registered selections that the collection has changed.
     */
    private void notifySelections() {
        // Collect stale references to remove after iteration
        List<WeakReference<ContactsSelection>> staleRefs = new ArrayList<>();
        
        for (WeakReference<ContactsSelection> ref : selections) {
            ContactsSelection selection = ref.get();
            if (selection != null) {
                selection.invalidateCache();
            } else {
                staleRefs.add(ref);
            }
        }
        
        // Remove stale references
        selections.removeAll(staleRefs);
    }

    /**
     * Notifies all change listeners and selections.
     */
    private void fireChangeEvent() {
        // Notify selections first (they use weak references)
        notifySelections();
        
        // Then notify regular listeners
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    public void updateContact(File zctContact, CompressedContact compressedContact) throws IOException {
        quadTree.update(zctContact, compressedContact);       
        fireChangeEvent();
    }

    public void updateContact(File zctContact) throws IOException {
        quadTree.update(zctContact, new CompressedContact(zctContact));       
        fireChangeEvent();
    }

    public static ContactCollection empty() {
        return new ContactCollection();
    }

    public void addRootFolder(File folder) {        
        List<File> contactFiles = findContacts(folder);
        log.info("Found {} contacts in folder {}", contactFiles.size(), folder.getAbsolutePath());
        for (File contactFile : contactFiles) {
            try {
                addContact(contactFile);              
            }
            catch (IOException e) {
                log.error("Error on contact {}", contactFile.getAbsolutePath(), e);                
            }
        }
        fireChangeEvent();

        log.info("Starting folder watcher for {}", folder.getAbsolutePath());
        folderWatchers.add(                        
            RecursiveFileWatcher.watchFolder(folder, "zct",
                (file) -> {
                    try {
                        log.info("Contact file added: {}", file.getAbsolutePath());
                        addContact(file);
                    } catch (IOException e) {
                        log.error("Error adding contact from file: {}", file.getAbsolutePath(), e);
                    }
                },
                (file) -> {
                    log.info("Contact file removed: {}", file.getAbsolutePath());
                    removeContact(file);                    
                },
                (file) -> {
                    try {
                        log.info("Contact file modified: {}", file.getAbsolutePath());
                        updateContact(file);
                    } catch (IOException e) {
                        log.error("Error updating contact from file: {}", file.getAbsolutePath(), e);
                    }
                }
            ));
    }

    public void removeRootFolder(File folder) {
        List<File> contactFiles = findContacts(folder);
        for (File contactFile : contactFiles) {
            removeContact(contactFile);            
        }        
        fireChangeEvent();
    }
/* 
    public void applyFilters(QuadTree.Region region, Instant start, Instant end) {
        this.currentRegion = region;
        this.currentStart = start;
        this.currentEnd = end;
        List<CompressedContact> contactsInRegion = quadTree.query(region);

        long startMillis = start.toEpochMilli();
        long endMillis = end.toEpochMilli();

        log.info("Applying filters: region {}, between {} and {}", region, start, end);
        List<File> newFilteredContacts = contactsInRegion.stream().filter(c -> 
            c.getTimestamp() >= startMillis && c.getTimestamp() <= endMillis
        ).map(CompressedContact::getZctFile).toList();

        
        log.info("Filtered contacts: {} in region {} between {} and {}", 
            newFilteredContacts.size(), region, start, end);
        this.filteredContacts = newFilteredContacts;

        // for (Runnable listener : changeListeners) {
        //     listener.run();
        // }
    }
*/


    /**
     * Applies filters to the contacts based on region, time, and classification/confidence/label criteria.
     * Empty sets for classification/confidence/label filters mean "show all" for that criterion.
     * 
     * @param region The geographic region to filter by
     * @param start Start time for filtering
     * @param end End time for filtering
     * @param classifications Set of classification types to include (empty = show all)
     * @param confidences Set of confidence levels to include (empty = show all)
     * @param labels Set of label annotations to include (empty = show all)
     */
    public void applyFilters(QuadTree.Region region, Instant start, Instant end,
                            Set<String> classifications, Set<String> confidences, Set<String> labels) {
        this.currentRegion = region;
        this.currentStart = start;
        this.currentEnd = end;
        this.currentClassifications = classifications;
        this.currentConfidences = confidences;
        this.currentLabels = labels;
        
        // Run filtering in background thread to avoid UI blocking
        CompletableFuture.runAsync(() -> {
            // If region is null, get all contacts
            List<CompressedContact> contactsInRegion = region != null 
                ? quadTree.query(region)
                : new ArrayList<>(getAllContacts());

            // If time filters are null, use all time
            long startMillis = start != null ? start.toEpochMilli() : Long.MIN_VALUE;
            long endMillis = end != null ? end.toEpochMilli() : Long.MAX_VALUE;

            log.info("Applying filters: region {}, time {} to {}, classifications: {}, confidences: {}, labels: {}",
                    region, start, end, 
                    classifications == null || classifications.isEmpty() ? "all" : classifications,
                    confidences == null || confidences.isEmpty() ? "all" : confidences,
                    labels == null || labels.isEmpty() ? "all" : labels);

            List<File> newFilteredContacts = contactsInRegion.stream()
                .filter(c -> c.getTimestamp() >= startMillis && c.getTimestamp() <= endMillis)
                .filter(c -> matchesClassificationFilter(c, classifications))
                .filter(c -> matchesConfidenceFilter(c, confidences))
                .filter(c -> matchesLabelFilter(c, labels))
                .map(CompressedContact::getZctFile)
                .toList();

            log.info("Filtered contacts: {} match all criteria", newFilteredContacts.size());
            this.filteredContacts = newFilteredContacts;
        });

        fireChangeEvent();
    }

    /**
     * Checks if a contact matches the classification filter.
     * Empty filter set means show all.
     */
    private boolean matchesClassificationFilter(CompressedContact contact, Set<String> classifications) {
        if (classifications == null || classifications.isEmpty()) {
            return true; // Show all if no filter
        }
        String contactClassification = contact.getClassification();
        return contactClassification != null && classifications.contains(contactClassification);
    }

    /**
     * Checks if a contact matches the confidence filter.
     * Empty filter set means show all.
     */
    private boolean matchesConfidenceFilter(CompressedContact contact, Set<String> confidences) {
        if (confidences == null || confidences.isEmpty()) {
            return true; // Show all if no filter
        }
        
        // Extract confidence from CLASSIFICATION annotations
        if (contact.getContact().getObservations() != null) {
            return contact.getContact().getObservations().stream()
                .filter(obs -> obs.getAnnotations() != null)
                .flatMap(obs -> obs.getAnnotations().stream())
                .filter(ann -> ann.getAnnotationType() == AnnotationType.CLASSIFICATION)
                .filter(ann -> ann.getConfidence() != null)
                .anyMatch(ann -> {
                    String confStr = String.valueOf(ann.getConfidence().intValue());
                    return confidences.contains(confStr);
                });
        }
        return false;
    }

    /**
     * Checks if a contact matches the label filter (AnnotationType.LABEL annotations).
     * Empty filter set means show all.
     */
    private boolean matchesLabelFilter(CompressedContact contact, Set<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return true; // Show all if no filter
        }
        
        // Check if contact has any LABEL annotations matching the filter
        if (contact.getContact().getObservations() != null) {
            return contact.getContact().getObservations().stream()
                .filter(obs -> obs.getAnnotations() != null)
                .flatMap(obs -> obs.getAnnotations().stream())
                .filter(ann -> ann.getAnnotationType() == AnnotationType.LABEL)
                .filter(ann -> ann.getCategory() != null)
                .anyMatch(ann -> labels.contains(ann.getCategory()));
        }
        return false;
    }

    /**
     * Re-applies the current filters to regenerate the filtered contacts list.
     * This is useful when new contacts are added to the collection and the
     * filtered list needs to be updated without changing the filter criteria.
     * This method does NOT trigger change listeners to avoid infinite loops.
     */
    public void reapplyCurrentFilters() {
        // Run filtering in background thread to avoid UI blocking
        CompletableFuture.runAsync(() -> {
            // If region is null, get all contacts
            List<CompressedContact> contactsInRegion = currentRegion != null 
                ? quadTree.query(currentRegion)
                : new ArrayList<>(getAllContacts());

            // If time filters are null, use all time
            long startMillis = currentStart != null ? currentStart.toEpochMilli() : Long.MIN_VALUE;
            long endMillis = currentEnd != null ? currentEnd.toEpochMilli() : Long.MAX_VALUE;

            log.debug("Reapplying current filters: region {}, time {} to {}, classifications: {}, confidences: {}, labels: {}",
                    currentRegion, currentStart, currentEnd, 
                    currentClassifications == null || currentClassifications.isEmpty() ? "all" : currentClassifications,
                    currentConfidences == null || currentConfidences.isEmpty() ? "all" : currentConfidences,
                    currentLabels == null || currentLabels.isEmpty() ? "all" : currentLabels);

            List<File> newFilteredContacts = contactsInRegion.stream()
                .filter(c -> c.getTimestamp() >= startMillis && c.getTimestamp() <= endMillis)
                .filter(c -> matchesClassificationFilter(c, currentClassifications))
                .filter(c -> matchesConfidenceFilter(c, currentConfidences))
                .filter(c -> matchesLabelFilter(c, currentLabels))
                .map(CompressedContact::getZctFile)
                .toList();

            log.debug("Reapplied filters: {} contacts match criteria", newFilteredContacts.size());
            this.filteredContacts = newFilteredContacts;
        });
        // NOTE: Do NOT fire change listeners here to avoid infinite loop
    }

    // public List<CompressedContact> getFilteredContacts() {        
    //     List<CompressedContact> contacts = new ArrayList<>();
    //     for (File file : filteredContacts) {
    //         CompressedContact contact = quadTree.get(file);
    //         if (contact != null) {
    //             contacts.add(contact);
    //         }
    //     }
    //     return contacts;
    // }

    public void refreshContact(File zctFile) throws IOException {
        quadTree.update(zctFile, new CompressedContact(zctFile));       
    }

    public static ContactCollection fromFolder(File folder) {    
        
        ContactCollection collection = new ContactCollection();
        collection.addRootFolder(folder);
        // try {
        //     return new ContactCollection(folder);
        // } catch (IOException e) {
        //     log.error("Error reading contacts from folder: {}", folder.getAbsolutePath(), e);
        //     return new ContactCollection();
        // }
        return collection;
    }

    public ContactCollection() {
        
    }

    /**
     * Creates a new contact collection from a folder.
     * @param folder the folder to search for contacts (recursively)
     * @throws IOException if an error occurs while reading the contacts
     */
    public ContactCollection(File folder) throws IOException{
        List<File> contactFiles = findContacts(folder);
        for (File contactFile : contactFiles) {
            try {
                addContact(contactFile);
            }
            catch (IOException e) {
                log.error("Error reading contact from {}", contactFile.getAbsolutePath(), e);
            }
        }
    }

    private static List<File> findContacts(File parentFolder) {
        List<File> files = new ArrayList<>();
        for (File file : Objects.requireNonNull(parentFolder.listFiles())) {
            if (file.isDirectory()) {
                files.addAll(findContacts(file));
            }
            else if (file.getName().endsWith(".zct")) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * Adds a contact to the collection.
     * @param zctContact the contact file
     * @throws IOException if an error occurs while reading the contact
     */
    public void addContact(File zctContact) throws IOException {
        quadTree.add(zctContact, new CompressedContact(zctContact));       
        
        fireChangeEvent();
    }

    /**
     * Removes a contact from the collection.
     * @param label the label of the contact to remove
     */
    public CompressedContact removeContact(File zctContact) {
        CompressedContact removed = quadTree.remove(zctContact);

        fireChangeEvent();
        return removed;
    }

    /**
     * Returns the contact with a given label.
     * @param label the label of the contact
     * @return the contact with the given label or null if not found
     */
    public CompressedContact getContact(File zctContact) {
        return quadTree.get(zctContact);
    }

    /**
     * Returns the contacts contained in a given area.
     * @param sw the south-west corner of the area
     * @param ne the north-east corner of the area
     * @return the contacts contained in the area
     */
    public List<CompressedContact> contactsContainedIn(LocationType sw, LocationType ne) {
        return quadTree.query(
                new QuadTree.Region(sw.getLatitudeDegs(), sw.getLongitudeDegs(), ne.getLatitudeDegs(),
                        ne.getLongitudeDegs()));        
    }

 
    public List<CompressedContact> getAllContacts() {
        return quadTree.getAll();
    }

    /**
     * Creates a new selection view of this collection with no filters.
     * @return A new ContactsSelection containing all contacts
     */
    public ContactsSelection select() {
        return new ContactsSelection(this);
    }

    /**
     * Creates a new selection view of this collection with the specified filters.
     * 
     * @param region Geographic region filter (null = no region filter)
     * @param startTime Start time filter (null = no start time filter)
     * @param endTime End time filter (null = no end time filter)
     * @param classifications Classification types to include (null/empty = all)
     * @param confidences Confidence levels to include (null/empty = all)
     * @param labels Labels to include (null/empty = all)
     * @return A new ContactsSelection with the specified filters
     */
    public ContactsSelection select(QuadTree.Region region,
                                    Instant startTime, 
                                    Instant endTime,
                                    Set<String> classifications, 
                                    Set<String> confidences, 
                                    Set<String> labels) {
        return new ContactsSelection(this, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    @Override
    public void paint(Graphics2D g, SlippyMap map) {
        double[] bounds = map.getVisibleBounds();
        List<CompressedContact> visibleContacts = contactsContainedIn(
            new LocationType(bounds[2], bounds[0]),
            new LocationType(bounds[3], bounds[1])
        );

        System.out.println("Painting " + visibleContacts.size() + " contacts in view");
        
        MapPainter.super.paint(g, map);
    }

    public static void main(String[] args) throws IOException {
        
        long startMillis = System.currentTimeMillis();
        File folder = new File("/LOGS/");
        ContactCollection collection = new ContactCollection(folder);
        System.out.println("Found " + collection.getAllContacts().size() + " contacts in " + (System.currentTimeMillis() - startMillis) + "ms");
        SlippyMap renderer = new SlippyMap();
        renderer.addRasterPainter(collection);
        GuiUtils.testFrame(renderer, "Contacts Viewer");
       
    }
}
