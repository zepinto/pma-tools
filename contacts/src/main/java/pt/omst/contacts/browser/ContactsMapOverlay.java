package pt.omst.contacts.browser;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JComponent;

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
        if (e.getID() == MouseEvent.MOUSE_CLICKED) {
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
        List<CompressedContact> contactsToPaint = collection.getFilteredContacts();
        for (CompressedContact contact : contactsToPaint) {
            paintContact(g2d, map, contact, false);
        }
        if (selectedContact != null) {
            paintContact(g2d, map, selectedContact, true);
        }
        g2d.dispose();
    }


}
