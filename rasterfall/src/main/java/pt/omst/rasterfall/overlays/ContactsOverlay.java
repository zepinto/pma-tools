//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JComponent;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.contacts.CompressedContact;
@Slf4j
public class ContactsOverlay extends AbstractOverlay{

    private static class Marker {
        Point2D.Double topLeft;
        Point2D.Double bottomRight;
        String label;
        CompressedContact contact;
    }

    private void reloadContacts() {
        markers.clear();
        log.info("Loading contacts for MarkOverlay: total {}", waterfall.getContacts().getAllContacts().size());
        waterfall.getContacts().getAllContacts().forEach(this::addContact);
    }

    private void addContact(CompressedContact c) {
        // log.info("Loaded contact: {} with {} observations", c.getContact().getUuid(),
        //         c.getContact().getObservations().size());
        // Marker m = new Marker();
        // m.contact = c;
        // m.label = c.getLabel();
        // IndexedRaster raster = c.getFirstRaster();
        // if (raster == null) {
        //     log.warn("Contact {} has no raster, skipping marker creation", c.getContact().getUuid());
        //     return;
        // }
        // Instant tStart = raster.getSamples().getFirst().getTimestamp().toInstant();
        // Instant tEnd = raster.getSamples().getLast().getTimestamp().toInstant();
        // double minRange = raster.getSensorInfo().getMinRange();
        // double maxRange = raster.getSensorInfo().getMaxRange();

        // m.topLeft = waterfall.getScreenPosition(tStart, minRange);
        // m.bottomRight = waterfall.getScreenPosition(tEnd, maxRange);

        // markers.add(m);
        // log.info("Marker at screen coords: TL({}, {}), BR({}, {})", m.topLeft.getX(), m.topLeft.getY(),
        //         m.bottomRight.getX(), m.bottomRight.getY());
    }

    private CopyOnWriteArrayList<Marker> markers = new CopyOnWriteArrayList<>();


    RasterfallTiles waterfall;
    

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;        
        reloadContacts();
    }
    
    @Override
    public void paint(Graphics g, JComponent c) {
        if (waterfall == null)
            return;
        // List<RasterContactInfo> contacts = waterfall.getVisibleContactInfos();
        // for (RasterContactInfo contact : contacts) {
            
        //     Point2D.Double pos = waterfall.getSlantedScreenPosition(Instant.ofEpochMilli(contact.getStartTimeStamp()), contact.getMinRange());
        //     Point2D.Double pos2 = waterfall.getSlantedScreenPosition(Instant.ofEpochMilli(contact.getEndTimeStamp()), contact.getMaxRange());

        //     g.setColor(Color.white);
        //     g.drawRect((int) pos.getX(), (int) pos.getY(), (int)(pos2.getX()-pos.getX()), (int)(pos2.getY()-pos.getY()));
            
        //     System.out.println("Contact info: " + contact);
        // }

    }
}
