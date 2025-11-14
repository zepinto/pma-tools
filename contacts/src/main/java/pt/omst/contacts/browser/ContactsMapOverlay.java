package pt.omst.contacts.browser;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

public class ContactsMapOverlay extends AbstractMapOverlay {

    private CompressedContact selectedContact = null;
    public static interface ContactSelectionListener {
        public void contactSelected(CompressedContact contact);
    }

    private ContactCollection collection;
    private ContactSelectionListener selectionListener = null;
    
    // Selection rectangle fields
    private Point selectionStart = null;
    private Point selectionEnd = null;
    private boolean isSelecting = false;

    public ContactsMapOverlay(ContactCollection collection) {
        this.collection = collection;
    }

    public void setContactSelectionListener(ContactSelectionListener listener) {
        this.selectionListener = listener;
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
                return true;
            }
        }
        map.setToolTipText(null);
        return false;
    }

    @Override
    public boolean processMouseEvent(MouseEvent e, SlippyMap map) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED && e.isShiftDown()) {
            // Start rectangular selection
            selectionStart = e.getPoint();
            selectionEnd = e.getPoint();
            isSelecting = true;
            return true; // Consume the event
        }
        
        if (e.getID() == MouseEvent.MOUSE_RELEASED && isSelecting) {
            // End rectangular selection
            isSelecting = false;
            selectionEnd = e.getPoint();
            
            // Find all contacts within the selection rectangle
            List<CompressedContact> selectedContacts = getContactsInRectangle(
                selectionStart, selectionEnd, map);
            
            // Show popup menu with selected contacts
            if (!selectedContacts.isEmpty()) {
                showSelectionPopup(selectedContacts, e.getPoint(), map);
            }
            
            // Clear selection rectangle
            selectionStart = null;
            selectionEnd = null;
            map.repaint();
            return true; // Consume the event
        }
        
        if (e.getID() == MouseEvent.MOUSE_CLICKED && !e.isShiftDown()) {
            // Handle single contact click (existing behavior)
            for (CompressedContact contact : collection.getFilteredContacts()) {
                double[] screenPos = map.latLonToScreen(
                    contact.getContact().getLatitude(), 
                    contact.getContact().getLongitude());
                double dx = e.getX() - screenPos[0];
                double dy = e.getY() - screenPos[1];
                double distanceSq = dx * dx + dy * dy;
                if (distanceSq <= 100) { // within 10 pixels
                   System.out.println("Clicked on contact: " + contact.getContact().getLabel());
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
        SlippyMap map = (SlippyMap) c;
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
        
        g2d.dispose();
    }


}
