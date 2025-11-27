//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;

/**
 * A composite contact collection that aggregates multiple ContactCollection instances.
 * This allows viewing and managing contacts from multiple sources (e.g., rasterfall data
 * plus additional folder sources) as a unified collection.
 * 
 * Changes in any underlying collection are propagated to listeners of this composite.
 */
@Slf4j
public class CompositeContactCollection extends ContactCollection {

    private final List<ContactCollection> collections = new CopyOnWriteArrayList<>();
    private final List<Runnable> collectionChangeListeners = new CopyOnWriteArrayList<>();

    public CompositeContactCollection() {
        super();
    }

    /**
     * Adds a contact collection to this composite.
     * 
     * @param collection The collection to add
     */
    public void addCollection(ContactCollection collection) {
        if (collection != null && !collections.contains(collection)) {
            collections.add(collection);
            
            // Listen for changes in the added collection
            collection.addChangeListener(() -> {
                log.debug("Child collection changed, notifying composite listeners");
                fireCompositeChangeEvent();
            });
            
            log.info("Added collection to composite, now have {} collections", collections.size());
            fireCompositeChangeEvent();
        }
    }

    /**
     * Removes a contact collection from this composite.
     * 
     * @param collection The collection to remove
     */
    public void removeCollection(ContactCollection collection) {
        if (collection != null && collections.remove(collection)) {
            log.info("Removed collection from composite, now have {} collections", collections.size());
            fireCompositeChangeEvent();
        }
    }

    /**
     * Gets all underlying collections.
     * 
     * @return List of all collections in this composite
     */
    public List<ContactCollection> getCollections() {
        return new ArrayList<>(collections);
    }

    /**
     * Clears all collections from this composite.
     */
    public void clearCollections() {
        collections.clear();
        fireCompositeChangeEvent();
    }

    /**
     * Fires a change event for the composite collection.
     */
    private void fireCompositeChangeEvent() {
        for (Runnable listener : collectionChangeListeners) {
            listener.run();
        }
    }

    @Override
    public void addChangeListener(Runnable listener) {
        collectionChangeListeners.add(listener);
    }

    @Override
    public List<CompressedContact> getAllContacts() {
        List<CompressedContact> allContacts = new ArrayList<>();
        for (ContactCollection collection : collections) {
            allContacts.addAll(collection.getAllContacts());
        }
        return allContacts;
    }

    @Override
    public List<CompressedContact> contactsContainedIn(LocationType sw, LocationType ne) {
        List<CompressedContact> contacts = new ArrayList<>();
        for (ContactCollection collection : collections) {
            contacts.addAll(collection.contactsContainedIn(sw, ne));
        }
        return contacts;
    }

    @Override
    public CompressedContact getContact(File zctContact) {
        for (ContactCollection collection : collections) {
            CompressedContact contact = collection.getContact(zctContact);
            if (contact != null) {
                return contact;
            }
        }
        return null;
    }

    @Override
    public void refreshContact(File zctFile) throws IOException {
        for (ContactCollection collection : collections) {
            CompressedContact contact = collection.getContact(zctFile);
            if (contact != null) {
                collection.refreshContact(zctFile);
                return;
            }
        }
    }

    @Override
    public void updateContact(File zctContact, CompressedContact compressedContact) throws IOException {
        for (ContactCollection collection : collections) {
            CompressedContact existing = collection.getContact(zctContact);
            if (existing != null) {
                collection.updateContact(zctContact, compressedContact);
                return;
            }
        }
    }

    @Override
    public void updateContact(File zctContact) throws IOException {
        for (ContactCollection collection : collections) {
            CompressedContact existing = collection.getContact(zctContact);
            if (existing != null) {
                collection.updateContact(zctContact);
                return;
            }
        }
    }

    @Override
    public ContactsSelection select() {
        return new ContactsSelection(this);
    }

    @Override
    public ContactsSelection select(QuadTree.Region region,
                                    Instant startTime, 
                                    Instant endTime,
                                    Set<String> classifications, 
                                    Set<String> confidences, 
                                    Set<String> labels) {
        return new ContactsSelection(this, region, startTime, endTime, 
                                     classifications, confidences, labels);
    }

    /**
     * Note: Adding contacts directly to a composite is not supported.
     * Add contacts to the underlying collections instead.
     */
    @Override
    public void addContact(File zctContact) throws IOException {
        throw new UnsupportedOperationException(
            "Cannot add contacts directly to a composite collection. " +
            "Add contacts to the underlying collections instead.");
    }

    /**
     * Note: Removing contacts directly from a composite is not supported.
     * Remove contacts from the underlying collections instead.
     */
    @Override
    public CompressedContact removeContact(File zctContact) {
        // Find which collection has this contact and remove from there
        for (ContactCollection collection : collections) {
            CompressedContact contact = collection.getContact(zctContact);
            if (contact != null) {
                return collection.removeContact(zctContact);
            }
        }
        return null;
    }

    /**
     * Note: Adding root folders directly to a composite is not supported.
     * Create a new ContactCollection for the folder and add it via addCollection().
     */
    @Override
    public void addRootFolder(File folder) {
        throw new UnsupportedOperationException(
            "Cannot add root folders directly to a composite collection. " +
            "Create a new ContactCollection and add it via addCollection().");
    }

    /**
     * Note: Removing root folders directly from a composite is not supported.
     * Remove the corresponding ContactCollection via removeCollection().
     */
    @Override
    public void removeRootFolder(File folder) {
        throw new UnsupportedOperationException(
            "Cannot remove root folders directly from a composite collection. " +
            "Remove the corresponding ContactCollection via removeCollection().");
    }

    @Override
    public void applyFilters(QuadTree.Region region, Instant start, Instant end,
                            Set<String> classifications, Set<String> confidences, Set<String> labels) {
        // Apply filters to all underlying collections
        for (ContactCollection collection : collections) {
            collection.applyFilters(region, start, end, classifications, confidences, labels);
        }
    }
}
