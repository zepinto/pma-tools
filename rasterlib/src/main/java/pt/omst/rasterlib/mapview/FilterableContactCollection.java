//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.mapview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.core.LocationType;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

/**
 * A wrapper around ContactCollection that adds visibility and time filtering support.
 * 
 * <p>This class extends the basic ContactCollection functionality with the ability to
 * toggle visibility and filter contacts by timestamp. It renders contacts as circular
 * markers on the map, with labels shown at higher zoom levels.</p>
 * 
 * <h3>Rendering:</h3>
 * <ul>
 *   <li>Contacts are drawn as yellow circles with black outlines</li>
 *   <li>Labels are shown when zoom level > 10</li>
 *   <li>Only contacts within the visible map bounds are rendered</li>
 * </ul>
 * 
 * @see ContactCollection
 * @see LayeredRasterViewer
 */
public class FilterableContactCollection implements MapPainter {
    
    @Getter
    private final ContactCollection contactCollection;
    
    @Getter
    @Setter
    private boolean visible = true;
    
    @Getter
    @Setter
    private Long startTimestampFilter = null;
    
    @Getter
    @Setter
    private Long endTimestampFilter = null;
    
    public FilterableContactCollection(ContactCollection contactCollection) {
        this.contactCollection = contactCollection;
    }
    
    @Override
    public int getLayerPriority() {
        return 100; // Paint contacts on top of rasters
    }
    
    @Override
    public void paint(Graphics2D g, SlippyMap map) {
        if (!visible) {
            return;
        }
        
        // Get visible contacts in the current map view
        double[] bounds = map.getVisibleBounds();
        List<CompressedContact> visibleContacts = contactCollection.contactsContainedIn(
            new LocationType(bounds[2], bounds[0]),
            new LocationType(bounds[3], bounds[1])
        );
        
        // Apply time filter
        List<CompressedContact> filteredContacts = visibleContacts;
        if (startTimestampFilter != null || endTimestampFilter != null) {
            filteredContacts = contactCollection.getContactsBetween(startTimestampFilter, endTimestampFilter);
            // Intersect with visible contacts
            filteredContacts.retainAll(visibleContacts);
        }
        
        // Draw each contact as a marker
        g.setColor(Color.YELLOW);
        for (CompressedContact contact : filteredContacts) {
            double[] screenPos = map.latLonToScreen(contact.getLatitude(), contact.getLongitude());
            int x = (int) screenPos[0];
            int y = (int) screenPos[1];
            
            // Draw a circle marker
            int radius = 5;
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            
            // Draw an outline
            g.setColor(Color.BLACK);
            g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            
            // Draw label if zoomed in enough
            if (map.getZoom() > 10) {
                g.setColor(Color.WHITE);
                String label = contact.getLabel();
                if (label != null && !label.isEmpty()) {
                    g.drawString(label, x + radius + 2, y);
                }
            }
            
            g.setColor(Color.YELLOW);
        }
    }
    
    @Override
    public String getName() {
        return "Contacts";
    }
}
