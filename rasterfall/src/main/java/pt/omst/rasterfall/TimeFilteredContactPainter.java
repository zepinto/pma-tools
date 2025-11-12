//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.Graphics2D;
import java.time.Instant;
import java.util.List;

import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

/**
 * A time-filtered wrapper around ContactCollection that only paints
 * contacts within the current time selection.
 */
public class TimeFilteredContactPainter implements MapPainter {
    
    private final ContactCollection delegate;
    private Instant filterStartTime;
    private Instant filterEndTime;
    private List<CompressedContact> visibleContacts;
    
    public TimeFilteredContactPainter(ContactCollection delegate) {
        this.delegate = delegate;
        this.filterStartTime = Instant.EPOCH;
        this.filterEndTime = Instant.now().plus(java.time.Duration.ofDays(365 * 100));
        updateVisibleContacts();
    }
    
    /**
     * Sets the time filter range. Only contacts within this range will be painted.
     */
    public void setTimeFilter(Instant startTime, Instant endTime) {
        this.filterStartTime = startTime;
        this.filterEndTime = endTime;
        updateVisibleContacts();
    }
    
    private void updateVisibleContacts() {
        visibleContacts = delegate.contactsBetween(
            filterStartTime.toEpochMilli(), 
            filterEndTime.toEpochMilli()
        );
    }
    
    public ContactCollection getDelegate() {
        return delegate;
    }
    
    public int getVisibleContactCount() {
        return visibleContacts != null ? visibleContacts.size() : 0;
    }
    
    @Override
    public void paint(Graphics2D g, SlippyMap renderer) {
        if (visibleContacts != null && !visibleContacts.isEmpty()) {
            // Use a temporary ContactCollection-like approach
            // Since contacts don't have individual paint methods, we just mark the area
            // This is a placeholder - actual implementation would require contact rendering logic
            double[] bounds = renderer.getVisibleBounds();
            
            // Filter visible contacts that are also in the current view
            for (CompressedContact contact : visibleContacts) {
                double lat = contact.getLocation().getLatitudeDegs();
                double lon = contact.getLocation().getLongitudeDegs();
                
                // Check if contact is in visible bounds
                if (lat >= bounds[0] && lat <= bounds[2] && 
                    lon >= bounds[1] && lon <= bounds[3]) {
                    // Draw a simple marker for the contact
                    double[] xy = renderer.latLonToPixel(lat, lon);
                    int screenX = (int) xy[0];
                    int screenY = (int) xy[1];
                    
                    // Draw a small circle for the contact
                    g.setColor(java.awt.Color.RED);
                    g.fillOval(screenX - 3, screenY - 3, 6, 6);
                    g.setColor(java.awt.Color.WHITE);
                    g.drawOval(screenX - 3, screenY - 3, 6, 6);
                }
            }
        }
    }
}
