//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.util.GuiUtils;

/**
 * A collection of contacts.
 */
@Slf4j
public class ContactCollection implements MapPainter {
    private QuadTree<File, CompressedContact> quadTree = new QuadTree<>();
    private ArrayList<File> filteredContacts = new ArrayList<>();
    private QuadTree.Region currentRegion = null;
    private Instant currentStart = null;
    private Instant currentEnd = null;

    public static ContactCollection empty() {
        return new ContactCollection();
    }

    public void addRootFolder(File folder) {
        
        List<File> contactFiles = findContacts(folder);
        for (File contactFile : contactFiles) {
            try {
                addContact(contactFile);              
            }
            catch (IOException e) {
                log.error("Error on contact {}", contactFile.getAbsolutePath(), e);                
            }
        }        
    }

    public void removeRootFolder(File folder) {
        List<File> contactFiles = findContacts(folder);
        for (File contactFile : contactFiles) {
            removeContact(contactFile);            
        }        
    }

    public void applyFilters(QuadTree.Region region, Instant start, Instant end) {
        this.currentRegion = region;
        this.currentStart = start;
        this.currentEnd = end;
        filteredContacts.clear();
        for (CompressedContact contact : quadTree.query(region)) {
            if (contact.getTimestamp() >= start.toEpochMilli() && contact.getTimestamp() <= end.toEpochMilli()) {
                filteredContacts.add(contact.getZctFile());
            }
        }
    }

    public List<CompressedContact> getFilteredContacts() {
        List<CompressedContact> contacts = new ArrayList<>();
        for (File file : filteredContacts) {
            CompressedContact contact = quadTree.get(file);
            if (contact != null) {
                contacts.add(contact);
            }
        }
        return contacts;
    }

    public static ContactCollection fromFolder(File folder) {        
        try {
            return new ContactCollection(folder);
        } catch (IOException e) {
            log.error("Error reading contacts from folder: {}", folder.getAbsolutePath(), e);
            return new ContactCollection();
        }
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
    }

    /**
     * Removes a contact from the collection.
     * @param label the label of the contact to remove
     */
    public CompressedContact removeContact(File zctContact) {
        return quadTree.remove(zctContact);
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
        renderer.addPainter(collection);
        GuiUtils.testFrame(renderer, "Contacts Viewer");
       
    }
}
