//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.types.coord.LocationType;

/**
 * A collection of contacts.
 */
@Slf4j
public class ContactCollection {

    private final ArrayList<CompressedContact> contacts = new ArrayList<>();
    private final File folder;

    public static ContactCollection fromFolder(File folder) {
        
        try {
            return new ContactCollection(folder);
        } catch (IOException e) {
            log.error("Error reading contacts from folder: {}", folder.getAbsolutePath(), e);
            return new ContactCollection();
        }
    }

    private ContactCollection() {
        this.folder = null; // No folder associated with an empty collection
        // Empty constructor for creating an empty collection
    }

    /**
     * Creates a new contact collection from a folder.
     * @param folder the folder to search for contacts (recursively)
     * @throws IOException if an error occurs while reading the contacts
     */
    public ContactCollection(File folder) throws IOException{
        this.folder = folder;
        List<File> contactFiles = findContacts(folder);
        for (File contactFile : contactFiles) {
            try {
                addContact(contactFile);
            }
            catch (IOException e) {
                log.warn("Error reading contact from {}", contactFile.getAbsolutePath());
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
        contacts.add(new CompressedContact(zctContact));
    }

    /**
     * Removes a contact from the collection.
     * @param index the index of the contact to remove
     */
    public void removeContact(int index) {
        if (index >= 0 && index < contacts.size()) {
            contacts.remove(index);
        }
    }

    /**
     * Returns the contacts between two timestamps.
     * @param start the start timestamp
     * @param end the end timestamp
     * @return the contacts between the two timestamps
     */
    public List<CompressedContact> contactsBetween(long start, long end) {
        ArrayList<CompressedContact> contactsBetween = new ArrayList<>();
        for (CompressedContact contact : contacts) {
            if (contact.getTimestamp() >= start && contact.getTimestamp() <= end) {
                contactsBetween.add(contact);
            }
        }
        return contactsBetween;
    }

    /**
     * Returns the contact with a given label.
     * @param label the label of the contact
     * @return the contact with the given label or null if not found
     */
    public CompressedContact getContact(String label) {
        for (CompressedContact contact : contacts) {
            if (contact.getLabel().equals(label)) {
                return contact;
            }
        }
        return null;
    }

    /**
     * Returns the contact at a given index.
     * @param index the index of the contact
     * @return the contact at the given index or null if the index is out of bounds
     */
    public CompressedContact getContact(int index) {
        if (index >= 0 && index < contacts.size()) {
            return contacts.get(index);
        }
        return null;
    }

    /**
     * Returns the contacts contained in a given area.
     * @param sw the south-west corner of the area
     * @param ne the north-east corner of the area
     * @return the contacts contained in the area
     */
    public List<CompressedContact> contactsContainedIn(LocationType sw, LocationType ne) {
        ArrayList<CompressedContact> contactsContained = new ArrayList<>();
        for (CompressedContact contact : contacts) {
            if (contact.getLocation().getLatitudeDegs() >= sw.getLatitudeDegs() &&
                    contact.getLocation().getLatitudeDegs() <= ne.getLatitudeDegs() &&
                    contact.getLocation().getLongitudeDegs() >= sw.getLongitudeDegs() &&
                    contact.getLocation().getLongitudeDegs() <= ne.getLongitudeDegs()) {
                contactsContained.add(contact);
            }
        }
        return contactsContained;
    }

    /**
     * Returns the contacts contained in a given time range.
     * @param startTimestamp  the start of the time range or null for no limit
     * @param endTimestamp the end of the time range or null for no limit
     * @return the contacts contained in the time range
     */
    public List<CompressedContact> getContactsBetween(Long startTimestamp, Long endTimestamp) {
        ArrayList<CompressedContact> contactsIn = new ArrayList<>();
        for (CompressedContact contact : contacts) {
            if ((startTimestamp == null || contact.getTimestamp() >= startTimestamp) && (endTimestamp == null || contact.getTimestamp() <= endTimestamp)) {
                contactsIn.add(contact);
            }
        }
        return contactsIn;
    }

    public List<CompressedContact> getAllContacts() {
        return contacts;
    }

    public void reload() {
        List<File> allContacts = findContacts(folder);
        
        for (File contactFile : allContacts) {
            String label = contactFile.getName().replace(".zct", "");
            CompressedContact contact = getContact(label);
            if (contact != null) {
                contacts.remove(contact);
            }
            try {
                addContact(contactFile);
            } catch (IOException e) {
                log.warn("Error reading contact from {}", contactFile.getAbsolutePath());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        File folder = new File("/LOGS/");
        long startMillis = System.currentTimeMillis();
        ContactCollection collection = new ContactCollection(folder);
        System.out.println("Found " + collection.contacts.size() + " contacts in " + (System.currentTimeMillis() - startMillis) + "ms");
    }
}
