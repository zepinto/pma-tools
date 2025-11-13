package pt.omst.contacts.browser;

import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;

import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

public class ContactsMapOverlay extends AbstractMapOverlay {

    private ContactCollection collection;

    public ContactsMapOverlay(ContactCollection collection) {
        this.collection = collection;
    }

    @Override
    public void cleanup(SlippyMap map) {
        // nothing to clean up
    }

    @Override
    public void install(SlippyMap map) {
        // nothing to initialize
    }    

    private void paintContact(Graphics2D g, SlippyMap map, CompressedContact contact) {
        double[] screenPos = map.latLonToPixel(
            contact.getContact().getLatitude(), 
            contact.getContact().getLongitude());
        
        g.fillOval(
            (int)screenPos[0] - 5, 
            (int)screenPos[1] - 5, 
            10, 
            10);
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
        for (CompressedContact contact : collection.getFilteredContacts()) {
            paintContact(g2d, map, contact);
        }
        g2d.dispose();
    }


}
