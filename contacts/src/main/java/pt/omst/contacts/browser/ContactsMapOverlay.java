package pt.omst.contacts.browser;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;
import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

@Slf4j
public class ContactsMapOverlay extends AbstractMapOverlay {

    private CompressedContact selectedContact = null;
    private CompressedContact hoveringContact = null;
    private SlippyMap map = null;
    public static interface ContactSelectionListener {
        public void contactSelected(CompressedContact contact);
    }

    private ContactCollection collection;
    private ContactSelectionListener selectionListener = null;
    
    // Selection rectangle fields
    private Point selectionStart = null;
    private Point selectionEnd = null;
    private boolean isSelecting = false;
    private boolean hasDragged = false; // Track if actual dragging occurred

    public ContactsMapOverlay(ContactCollection collection) {
        this.collection = collection;
        // do in background thread
    }

    public void setContactSelectionListener(ContactSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void refreshContact(File contactFile) {
        log.info("Refreshing "+contactFile);
        if (selectedContact.getZctFile().equals(contactFile)) {
            try {
                selectedContact = new CompressedContact(contactFile);
            }
            catch (Exception e) {
                log.warn("Unable to load contact from disk:", e);
            }            
        }
    }


    @Override
    public void cleanup(SlippyMap map) {
        // nothing to clean up
    }

    @Override
    public void install(SlippyMap map) {
        // nothing to initialize
    }    

    @Override
    public boolean processMouseMotionEvent(MouseEvent e, SlippyMap map) {
        // Handle rectangular selection dragging
        if (isSelecting && e.getID() == MouseEvent.MOUSE_DRAGGED) {
            selectionEnd = e.getPoint();
            
            // Check if we've moved enough to consider this a drag (threshold of 3 pixels)
            if (selectionStart != null) {
                int dx = Math.abs(selectionEnd.x - selectionStart.x);
                int dy = Math.abs(selectionEnd.y - selectionStart.y);
                if (dx > 3 || dy > 3) {
                    hasDragged = true;
                }
            }
            
            map.repaint();
            return true; // Consume the event
        }
        
        // Show tooltips when hovering over contacts
        for (CompressedContact contact : collection.getFilteredContacts()) {
            double[] screenPos = map.latLonToScreen(
                contact.getContact().getLatitude(), 
                contact.getContact().getLongitude());
            double dx = e.getX() - screenPos[0];
            double dy = e.getY() - screenPos[1];
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq <= 100) { // within 10 pixels
                map.setToolTipText(contact.getContact().getLabel());
                hoveringContact = contact;
                return true;
            }
        }
        map.setToolTipText(null);
        hoveringContact = null;
        return false;
    }

    @Override
    public boolean processMouseEvent(MouseEvent e, SlippyMap map) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED && SwingUtilities.isRightMouseButton(e)) {
            // Start potential rectangular selection with right-click
            selectionStart = e.getPoint();
            selectionEnd = e.getPoint();
            isSelecting = true;
            hasDragged = false; // Reset drag flag
            return false; // Don't consume - let other handlers see it
        }
        
        if (e.getID() == MouseEvent.MOUSE_RELEASED && isSelecting) {
            // End rectangular selection
            isSelecting = false;
            selectionEnd = e.getPoint();
            
            // Only show selection popup if user actually dragged
            if (hasDragged) {
                // Find all contacts within the selection rectangle
                List<CompressedContact> selectedContacts = getContactsInRectangle(
                    selectionStart, selectionEnd, map);
                
                // Show popup menu with selected contacts (only if contacts found)
                if (!selectedContacts.isEmpty()) {
                    showSelectionPopup(selectedContacts, e.getPoint(), map);
                    // Clear selection rectangle
                    selectionStart = null;
                    selectionEnd = null;
                    map.repaint();
                    return true; // Consume the event to prevent base map popup
                }
            }
            
            // No drag occurred or no contacts selected - clear selection and allow base map popup
            selectionStart = null;
            selectionEnd = null;
            map.repaint();
            return false; // Don't consume - let base map popup show
        }
        
        if (e.getID() == MouseEvent.MOUSE_CLICKED && SwingUtilities.isLeftMouseButton(e)) {
            // Handle single contact click with left button
            for (CompressedContact contact : collection.getFilteredContacts()) {
                double[] screenPos = map.latLonToScreen(
                    contact.getContact().getLatitude(), 
                    contact.getContact().getLongitude());
                double dx = e.getX() - screenPos[0];
                double dy = e.getY() - screenPos[1];
                double distanceSq = dx * dx + dy * dy;
                if (distanceSq <= 100) { // within 10 pixels
                   selectedContact = contact;
                   if (selectionListener != null) {
                       selectionListener.contactSelected(contact);
                       return true;
                   }                   
                }
            }
        }
        return false;
    }
    
    /**
     * Find all contacts within the rectangular selection area
     */
    private List<CompressedContact> getContactsInRectangle(Point start, Point end, SlippyMap map) {
        List<CompressedContact> result = new ArrayList<>();
        
        // Calculate rectangle bounds
        int minX = Math.min(start.x, end.x);
        int maxX = Math.max(start.x, end.x);
        int minY = Math.min(start.y, end.y);
        int maxY = Math.max(start.y, end.y);
        
        // Check each contact
        for (CompressedContact contact : collection.getFilteredContacts()) {
            double[] screenPos = map.latLonToScreen(
                contact.getContact().getLatitude(), 
                contact.getContact().getLongitude());
            
            if (screenPos[0] >= minX && screenPos[0] <= maxX &&
                screenPos[1] >= minY && screenPos[1] <= maxY) {
                result.add(contact);
            }
        }
        
        return result;
    }
    
    /**
     * Show a popup menu with the names of selected contacts
     */
    private void showSelectionPopup(List<CompressedContact> contacts, Point location, SlippyMap map) {
        JPopupMenu popup = new JPopupMenu();
        
        for (CompressedContact contact : contacts) {
            JMenuItem item = new JMenuItem(contact.getContact().getLabel());
            item.addActionListener(ev -> {
                selectedContact = contact;
                if (selectionListener != null) {
                    selectionListener.contactSelected(contact);
                }
                map.repaint();
            });
            popup.add(item);
        }
        
        SwingUtilities.invokeLater(() -> {
            popup.show(map, location.x, location.y);
        });
    }
    
    private void paintContact(Graphics2D g, SlippyMap map, CompressedContact contact, boolean isSelected) {
        
        double[] screenPos = map.latLonToScreen(
            contact.getContact().getLatitude(), 
            contact.getContact().getLongitude());

        if (screenPos[0] < 0 || screenPos[1] < 0 ||
            screenPos[0] > map.getWidth() || screenPos[1] > map.getHeight()) {
            return; // outside visible area
        }
        String category = "UNKNOWN";
        try {
             category = contact.getClassification().toUpperCase();
        } catch (Exception e) {
             // ignore
        }
        Image icon = IconCache.getInstance().getIcon(category);
                
        if (isSelected) {
            g.setColor(java.awt.Color.RED);
        } else {
            g.setColor(java.awt.Color.BLUE);
        }        
        g.drawImage(
            icon, 
            (int)screenPos[0] - 8, 
            (int)screenPos[1] - 8, 
            null);
        g.drawString(
            contact.getContact().getLabel(),
            (int)screenPos[0] + 6, 
            (int)screenPos[1] - 6);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        this.map = (SlippyMap) c;
        Graphics2D g2d = (Graphics2D) g.create();
        
        // Paint all contacts
        List<CompressedContact> contactsToPaint = collection.getFilteredContacts();
        for (CompressedContact contact : contactsToPaint) {
            paintContact(g2d, map, contact, false);
        }
        
        // Paint selected contact
        if (selectedContact != null) {
            paintContact(g2d, map, selectedContact, true);
        }
        
        // Draw selection rectangle if selecting
        if (isSelecting && selectionStart != null && selectionEnd != null) {
            g2d.setColor(new java.awt.Color(0, 120, 215, 100)); // Semi-transparent blue
            g2d.setStroke(new java.awt.BasicStroke(2.0f));
            
            int x = Math.min(selectionStart.x, selectionEnd.x);
            int y = Math.min(selectionStart.y, selectionEnd.y);
            int width = Math.abs(selectionEnd.x - selectionStart.x);
            int height = Math.abs(selectionEnd.y - selectionStart.y);
            
            g2d.fillRect(x, y, width, height);
            g2d.setColor(new java.awt.Color(0, 120, 215)); // Solid blue border
            g2d.drawRect(x, y, width, height);
        }

        if (hoveringContact != null && hoveringContact != selectedContact) {
            // Highlight hovering contact
            Image thumbnail = hoveringContact.getThumbnail();
            if (thumbnail != null && thumbnail.getWidth(null) > 1) {    
                double[] screenPos = map.latLonToScreen(
                    hoveringContact.getContact().getLatitude(), 
                    hoveringContact.getContact().getLongitude());
                
                int xLoc = (int)screenPos[0] + 10;

                if (xLoc + thumbnail.getWidth(null) > map.getWidth()) {
                    xLoc = (int)screenPos[0] - thumbnail.getWidth(null) - 10;
                }

                int yLoc = (int)screenPos[1];

                if (yLoc + thumbnail.getHeight(null) > map.getHeight()) {
                    yLoc = map.getHeight() - thumbnail.getHeight(null) - 10;
                }
                g2d.drawImage(
                    thumbnail,
                    (int)xLoc,
                    (int)yLoc,
                    null);
                // int x = (int)screenPos[0] - thumbnail.getWidth(null) / 2 - 4;
                // int y = (int)screenPos[1] - thumbnail.getHeight(null) / 2 - 4;
                // int width = thumbnail.getWidth(null) + 8;
                // int height = thumbnail.getHeight(null) + 8;
                
                // g2d.setColor(new java.awt.Color(0, 255, 0, 100)); // Semi-transparent green
                // g2d.setStroke(new java.awt.BasicStroke(2.0f));
                
                // g2d.fillRect(x, y, width, height);
                // g2d.setColor(new java.awt.Color(0, 255, 0)); // Solid green border
                // g2d.drawRect(x, y, width, height);
            }
        }
        
        g2d.dispose();
    }

}
