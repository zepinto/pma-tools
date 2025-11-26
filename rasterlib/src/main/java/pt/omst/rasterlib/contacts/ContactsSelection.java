//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterlib.AnnotationType;

/**
 * A filtered view of a ContactCollection.
 * This class provides a subset of contacts based on filter criteria
 * without modifying the underlying collection's state.
 */
@Slf4j
public class ContactsSelection {

    @Getter
    private final ContactCollection collection;
    
    private final QuadTree.Region region;
    private final Instant startTime;
    private final Instant endTime;
    private final Set<String> classifications;
    private final Set<String> confidences;
    private final Set<String> labels;
    
    // Cached filtered results
    private List<CompressedContact> filteredContacts;
    private boolean cacheValid = false;

    /**
     * Creates a new ContactsSelection with the specified filters.
     * 
     * @param collection The source contact collection
     * @param region Geographic region filter (null = no region filter)
     * @param startTime Start time filter (null = no start time filter)
     * @param endTime End time filter (null = no end time filter)
     * @param classifications Classification types to include (null/empty = all)
     * @param confidences Confidence levels to include (null/empty = all)
     * @param labels Labels to include (null/empty = all)
     */
    public ContactsSelection(ContactCollection collection, 
                            QuadTree.Region region,
                            Instant startTime, 
                            Instant endTime,
                            Set<String> classifications, 
                            Set<String> confidences, 
                            Set<String> labels) {
        this.collection = collection;
        this.region = region;
        this.startTime = startTime;
        this.endTime = endTime;
        this.classifications = classifications;
        this.confidences = confidences;
        this.labels = labels;
        
        // Listen for changes to invalidate cache
        collection.addChangeListener(this::invalidateCache);
    }

    /**
     * Creates a selection with no filters (returns all contacts).
     * 
     * @param collection The source contact collection
     */
    public ContactsSelection(ContactCollection collection) {
        this(collection, null, null, null, null, null, null);
    }

    /**
     * Creates a new selection with updated filters.
     * 
     * @param region Geographic region filter (null = no region filter)
     * @param startTime Start time filter (null = no start time filter)
     * @param endTime End time filter (null = no end time filter)
     * @param classifications Classification types to include (null/empty = all)
     * @param confidences Confidence levels to include (null/empty = all)
     * @param labels Labels to include (null/empty = all)
     * @return A new ContactsSelection with the specified filters
     */
    public ContactsSelection withFilters(QuadTree.Region region,
                                         Instant startTime, 
                                         Instant endTime,
                                         Set<String> classifications, 
                                         Set<String> confidences, 
                                         Set<String> labels) {
        return new ContactsSelection(collection, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    /**
     * Creates a new selection with only classification filter updated.
     */
    public ContactsSelection withClassifications(Set<String> classifications) {
        return new ContactsSelection(collection, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    /**
     * Creates a new selection with only confidence filter updated.
     */
    public ContactsSelection withConfidences(Set<String> confidences) {
        return new ContactsSelection(collection, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    /**
     * Creates a new selection with only labels filter updated.
     */
    public ContactsSelection withLabels(Set<String> labels) {
        return new ContactsSelection(collection, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    /**
     * Creates a new selection with only region filter updated.
     */
    public ContactsSelection withRegion(QuadTree.Region region) {
        return new ContactsSelection(collection, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    /**
     * Creates a new selection with only time range filter updated.
     */
    public ContactsSelection withTimeRange(Instant startTime, Instant endTime) {
        return new ContactsSelection(collection, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    /**
     * Invalidates the cached results, forcing recomputation on next access.
     */
    public void invalidateCache() {
        cacheValid = false;
        filteredContacts = null;
    }

    /**
     * Returns the filtered contacts based on the current filter criteria.
     * Results are cached until the underlying collection changes.
     * 
     * @return List of contacts matching all filter criteria
     */
    public List<CompressedContact> getContacts() {
        if (cacheValid && filteredContacts != null) {
            return Collections.unmodifiableList(filteredContacts);
        }

        // Get base contacts (filtered by region if specified)
        List<CompressedContact> contacts = region != null 
            ? contactsContainedIn(region)
            : new ArrayList<>(collection.getAllContacts());

        // Apply time filters
        long startMillis = startTime != null ? startTime.toEpochMilli() : Long.MIN_VALUE;
        long endMillis = endTime != null ? endTime.toEpochMilli() : Long.MAX_VALUE;

        log.debug("Applying selection filters: region {}, time {} to {}, classifications: {}, confidences: {}, labels: {}",
                region, startTime, endTime, 
                classifications == null || classifications.isEmpty() ? "all" : classifications,
                confidences == null || confidences.isEmpty() ? "all" : confidences,
                labels == null || labels.isEmpty() ? "all" : labels);

        filteredContacts = contacts.stream()
            .filter(c -> c.getTimestamp() >= startMillis && c.getTimestamp() <= endMillis)
            .filter(c -> matchesClassificationFilter(c))
            .filter(c -> matchesConfidenceFilter(c))
            .filter(c -> matchesLabelFilter(c))
            .toList();

        log.debug("Selection contains {} contacts matching all criteria", filteredContacts.size());
        cacheValid = true;
        
        return Collections.unmodifiableList(filteredContacts);
    }

    /**
     * Returns the count of filtered contacts.
     */
    public int size() {
        return getContacts().size();
    }

    /**
     * Returns true if there are no contacts matching the filters.
     */
    public boolean isEmpty() {
        return getContacts().isEmpty();
    }

    /**
     * Returns the total count of contacts in the underlying collection.
     */
    public int getTotalCount() {
        return collection.getAllContacts().size();
    }

    /**
     * Checks if a contact matches the classification filter.
     */
    private boolean matchesClassificationFilter(CompressedContact contact) {
        if (classifications == null || classifications.isEmpty()) {
            return true;
        }
        String contactClassification = contact.getClassification();
        return contactClassification != null && classifications.contains(contactClassification);
    }

    /**
     * Checks if a contact matches the confidence filter.
     */
    private boolean matchesConfidenceFilter(CompressedContact contact) {
        if (confidences == null || confidences.isEmpty()) {
            return true;
        }
        
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
     * Checks if a contact matches the label filter.
     */
    private boolean matchesLabelFilter(CompressedContact contact) {
        if (labels == null || labels.isEmpty()) {
            return true;
        }
        
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
     * Helper method to query contacts within a region.
     */
    private List<CompressedContact> contactsContainedIn(QuadTree.Region region) {
        return collection.contactsContainedIn(
            new pt.lsts.neptus.core.LocationType(region.getMinLat(), region.getMinLon()),
            new pt.lsts.neptus.core.LocationType(region.getMaxLat(), region.getMaxLon())
        );
    }

    @Override
    public String toString() {
        return String.format("ContactsSelection[%d of %d contacts]", size(), getTotalCount());
    }
}
