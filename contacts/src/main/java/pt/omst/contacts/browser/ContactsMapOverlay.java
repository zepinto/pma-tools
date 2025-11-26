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
    private ContactGroupingHandler groupingHandler = null;
    
    // Selection rectangle fields
    private Point selectionStart = null;
    private Point selectionEnd = null;
    private boolean isSelecting = false;
    private boolean hasDragged = false; // Track if actual dragging occurred
    
    // Circle selection fields
    private Point circleSelectionStart = null;
    private Point circleSelectionEnd = null;
    private boolean isCircleSelecting = false;
    private boolean hasCircleDragged = false;

    // Fields for cycling through contacts at same location
    private Point lastClickLocation = null;
    private List<CompressedContact> contactsAtLastClick = new ArrayList<>();
    private int currentContactIndex = 0;
    private static final int CLICK_DISTANCE_THRESHOLD = 5; // pixels

    public ContactsMapOverlay(ContactCollection collection) {
        this.collection = collection;
        // do in background thread
    }

    public void setContactCollection(ContactCollection collection) {
        this.collection = collection;
    }

    public void setContactSelectionListener(ContactSelectionListener listener) {
        this.selectionListener = listener;
    }
    
    public void setTargetManager(TargetManager targetManager) {
        this.groupingHandler = targetManager;
    }
    
    public void setGroupingHandler(ContactGroupingHandler groupingHandler) {
        this.groupingHandler = groupingHandler;
    }

    public void refreshContact(File contactFile) {
        log.info("Refreshing "+contactFile);
        if (selectedContact != null && selectedContact.getZctFile().equals(contactFile)) {
            try {
                selectedContact = new CompressedContact(contactFile);
            }
            catch (Exception e) {
                log.warn("Unable to load contact from disk:", e);
            }            
        }
    }

    public void refresh() {
        // Re-apply current filters to pick up any new contacts that were added
        if (collection != null) {
            collection.reapplyCurrentFilters();
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
        // Handle circle selection dragging (SHIFT + right mouse button drag)
        if (isCircleSelecting && e.getID() == MouseEvent.MOUSE_DRAGGED) {
            circleSelectionEnd = e.getPoint();
            
            // Check if we've moved enough to consider this a drag (threshold of 3 pixels)
            if (circleSelectionStart != null) {
                int dx = Math.abs(circleSelectionEnd.x - circleSelectionStart.x);
                int dy = Math.abs(circleSelectionEnd.y - circleSelectionStart.y);
                if (dx > 3 || dy > 3) {
                    hasCircleDragged = true;
                }
            }
            
            map.repaint();
            return true; // Consume the event
        }
        
        // Ignore mouse motion when SHIFT is pressed (except for circle selection)
        if (e.isShiftDown() && !isCircleSelecting) {
            // Clear any hovering state
            if (hoveringContact != null) {
                hoveringContact = null;
                map.setToolTipText(null);
                map.repaint();
            }
            return false;
        }
        
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
        for (CompressedContact contact : collection.getAllContacts()) {
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
            if (e.isShiftDown()) {
                // Start circle selection with SHIFT + right-click
                circleSelectionStart = e.getPoint();
                circleSelectionEnd = e.getPoint();
                isCircleSelecting = true;
                hasCircleDragged = false;
                return true; // Consume the event
            } else {
                // Start rectangular selection with right-click (no SHIFT)
                selectionStart = e.getPoint();
                selectionEnd = e.getPoint();
                isSelecting = true;
                hasDragged = false;
                return false; // Don't consume - let other handlers see it
            }
        }
        
        if (e.getID() == MouseEvent.MOUSE_RELEASED && isCircleSelecting) {
            // End circle selection
            isCircleSelecting = false;
            circleSelectionEnd = e.getPoint();
            
            // Only show selection popup if user actually dragged
            if (hasCircleDragged) {
                // Find all contacts within the circle
                List<CompressedContact> selectedContacts = getContactsInCircle(
                    circleSelectionStart, circleSelectionEnd, map);
                
                // Show popup menu with selected contacts (only if contacts found)
                if (!selectedContacts.isEmpty()) {
                    showSelectionPopup(selectedContacts, e.getPoint(), map);
                    // Clear selection circle
                    circleSelectionStart = null;
                    circleSelectionEnd = null;
                    map.repaint();
                    return true; // Consume the event
                }
            }
            
            // No drag occurred or no contacts selected - clear selection
            circleSelectionStart = null;
            circleSelectionEnd = null;
            map.repaint();
            return false;
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
            Point clickPoint = e.getPoint();
            
            // Check if this is a click at approximately the same location as the last click
            boolean isSameLocation = false;
            if (lastClickLocation != null) {
                int dx = Math.abs(clickPoint.x - lastClickLocation.x);
                int dy = Math.abs(clickPoint.y - lastClickLocation.y);
                isSameLocation = (dx <= CLICK_DISTANCE_THRESHOLD && dy <= CLICK_DISTANCE_THRESHOLD);
            }
            
            if (!isSameLocation) {
                // New location - find all contacts at this location
                contactsAtLastClick.clear();
                for (CompressedContact contact : collection.getAllContacts()) {
                    double[] screenPos = map.latLonToScreen(
                        contact.getContact().getLatitude(), 
                        contact.getContact().getLongitude());
                    double dx = clickPoint.x - screenPos[0];
                    double dy = clickPoint.y - screenPos[1];
                    double distanceSq = dx * dx + dy * dy;
                    if (distanceSq <= 100) { // within 10 pixels
                        contactsAtLastClick.add(contact);
                    }
                }
                
                lastClickLocation = clickPoint;
                currentContactIndex = 0;
                
                // Select first contact if any found
                if (!contactsAtLastClick.isEmpty()) {
                    selectedContact = contactsAtLastClick.get(0);
                    if (selectionListener != null) {
                        selectionListener.contactSelected(selectedContact);
                    }
                    map.repaint();
                    return true;
                }
            } else {
                // Same location - cycle to next contact
                if (!contactsAtLastClick.isEmpty()) {
                    currentContactIndex = (currentContactIndex + 1) % contactsAtLastClick.size();
                    selectedContact = contactsAtLastClick.get(currentContactIndex);
                    if (selectionListener != null) {
                        selectionListener.contactSelected(selectedContact);
                    }
                    map.repaint();
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Find all contacts within the rectangular selection area (from center)
     */
    private List<CompressedContact> getContactsInRectangle(Point center, Point corner, SlippyMap map) {
        List<CompressedContact> result = new ArrayList<>();
        
        // Calculate rectangle bounds from center
        int dx = Math.abs(corner.x - center.x);
        int dy = Math.abs(corner.y - center.y);
        int minX = center.x - dx;
        int maxX = center.x + dx;
        int minY = center.y - dy;
        int maxY = center.y + dy;
        
        // Check each contact
        for (CompressedContact contact : collection.getAllContacts()) {
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
     * Find all contacts within the circular selection area
     */
    private List<CompressedContact> getContactsInCircle(Point center, Point edge, SlippyMap map) {
        List<CompressedContact> result = new ArrayList<>();
        
        // Convert center and edge points to lat/lon using SlippyMap method
        pt.lsts.neptus.core.LocationType centerLocation = map.getRealWorldPosition(center.x, center.y);
        pt.lsts.neptus.core.LocationType edgeLocation = map.getRealWorldPosition(edge.x, edge.y);
        
        // Calculate radius in meters
        double radiusMeters = centerLocation.getDistanceInMeters(edgeLocation);
        
        // Check each contact
        for (CompressedContact contact : collection.getAllContacts()) {
            pt.lsts.neptus.core.LocationType contactLocation = new pt.lsts.neptus.core.LocationType();
            contactLocation.setLatitudeDegs(contact.getContact().getLatitude());
            contactLocation.setLongitudeDegs(contact.getContact().getLongitude());
            
            double distance = centerLocation.getDistanceInMeters(contactLocation);
            
            if (distance <= radiusMeters) {
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
        
        // Add separator and group contacts option if 2+ contacts selected
        if (contacts.size() >= 2 && groupingHandler != null) {
            popup.addSeparator();
            JMenuItem groupItem = new JMenuItem("Group Selected Contacts...");
            groupItem.addActionListener(ev -> {
                showGroupContactsDialog(contacts, map);
            });
            popup.add(groupItem);
        }
        
        SwingUtilities.invokeLater(() -> {
            popup.show(map, location.x, location.y);
        });
    }
    
    /**
     * Show dialog to group selected contacts
     */
    private void showGroupContactsDialog(List<CompressedContact> contacts, SlippyMap map) {
        SwingUtilities.invokeLater(() -> {
            GroupContactsDialog dialog = new GroupContactsDialog(
                SwingUtilities.getWindowAncestor(map), contacts);
            dialog.setVisible(true);
            
            if (dialog.isConfirmed()) {
                CompressedContact mainContact = dialog.getMainContact();
                List<CompressedContact> mergeContacts = dialog.getContactsToMerge();
                
                if (mainContact != null && !mergeContacts.isEmpty()) {
                    log.info("Grouping {} contacts into main contact: {}", 
                        mergeContacts.size(), mainContact.getContact().getLabel());
                    groupingHandler.groupContactsAsync(mainContact, mergeContacts);
                }
            }
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
        int iconSize = IconCache.getInstance().getIconSize();
        int halfSize = iconSize / 2;
                
        if (isSelected) {
            g.setColor(java.awt.Color.RED);
        } else {
            g.setColor(java.awt.Color.BLUE);
        }        
        g.drawImage(
            icon, 
            (int)screenPos[0] - halfSize, 
            (int)screenPos[1] - halfSize, 
            null);
        
        // Draw label with black border for contrast
        String label = contact.getContact().getLabel();
        int labelX = (int)screenPos[0] + halfSize;
        int labelY = (int)screenPos[1] - halfSize;
        
        // Draw black border (4 cardinal directions for efficiency)
        g.setColor(java.awt.Color.BLACK);
        g.drawString(label, labelX - 1, labelY);
        g.drawString(label, labelX + 1, labelY);
        g.drawString(label, labelX, labelY - 1);
        g.drawString(label, labelX, labelY + 1);
        
        // Draw white label on top
        g.setColor(java.awt.Color.WHITE);
        g.drawString(label, labelX, labelY);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        this.map = (SlippyMap) c;
        Graphics2D g2d = (Graphics2D) g.create();
        
        // Paint all contacts
        List<CompressedContact> contactsToPaint = collection.getAllContacts();
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
            
            // Calculate rectangle from center (like circle selection)
            int dx = selectionEnd.x - selectionStart.x;
            int dy = selectionEnd.y - selectionStart.y;
            int width = Math.abs(dx) * 2;
            int height = Math.abs(dy) * 2;
            int x = selectionStart.x - Math.abs(dx);
            int y = selectionStart.y - Math.abs(dy);
            
            g2d.fillRect(x, y, width, height);
            g2d.setColor(new java.awt.Color(0, 120, 215)); // Solid blue border
            g2d.drawRect(x, y, width, height);
        }
        
        // Draw circle selection if selecting with SHIFT
        if (isCircleSelecting && circleSelectionStart != null && circleSelectionEnd != null) {
            // Calculate radius in pixels
            int dx = circleSelectionEnd.x - circleSelectionStart.x;
            int dy = circleSelectionEnd.y - circleSelectionStart.y;
            int radiusPixels = (int) Math.sqrt(dx * dx + dy * dy);
            
            // Calculate radius in meters for display
            pt.lsts.neptus.core.LocationType centerLocation = map.getRealWorldPosition(circleSelectionStart.x, circleSelectionStart.y);
            pt.lsts.neptus.core.LocationType edgeLocation = map.getRealWorldPosition(circleSelectionEnd.x, circleSelectionEnd.y);
            double radiusMeters = centerLocation.getDistanceInMeters(edgeLocation);
            
            // Draw the circle
            g2d.setColor(new java.awt.Color(0, 120, 215, 100)); // Semi-transparent blue
            g2d.setStroke(new java.awt.BasicStroke(2.0f));
            g2d.fillOval(
                circleSelectionStart.x - radiusPixels,
                circleSelectionStart.y - radiusPixels,
                radiusPixels * 2,
                radiusPixels * 2);
            g2d.setColor(new java.awt.Color(0, 120, 215)); // Solid blue border
            g2d.drawOval(
                circleSelectionStart.x - radiusPixels,
                circleSelectionStart.y - radiusPixels,
                radiusPixels * 2,
                radiusPixels * 2);
            
            // Draw radius text in meters
            String radiusText = String.format("%.1f m", radiusMeters);
            g2d.setColor(java.awt.Color.WHITE);
            g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
            
            // Draw text with shadow for better visibility
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(radiusText);
            int textX = circleSelectionStart.x + radiusPixels / 2 - textWidth / 2;
            int textY = circleSelectionStart.y - radiusPixels / 2;
            
            // Draw shadow
            g2d.setColor(java.awt.Color.BLACK);
            g2d.drawString(radiusText, textX + 1, textY + 1);
            
            // Draw text
            g2d.setColor(java.awt.Color.WHITE);
            g2d.drawString(radiusText, textX, textY);
        }

        if (hoveringContact != null) {
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
            }
        }
        g2d.dispose();
    }

}
