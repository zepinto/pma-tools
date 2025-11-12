//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.Graphics2D;
import java.time.Instant;

import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.mapview.IndexedRasterPainter;

/**
 * A time-filtered wrapper around IndexedRasterPainter that only paints
 * when the raster data overlaps with the current time selection.
 */
public class TimeFilteredRasterPainter implements MapPainter {
    
    private final IndexedRasterPainter delegate;
    private Instant filterStartTime;
    private Instant filterEndTime;
    private boolean visible = true;
    
    public TimeFilteredRasterPainter(IndexedRasterPainter delegate) {
        this.delegate = delegate;
        this.filterStartTime = Instant.EPOCH;
        this.filterEndTime = Instant.now().plus(java.time.Duration.ofDays(365 * 100));
    }
    
    /**
     * Sets the time filter range. Only data overlapping this range will be painted.
     */
    public void setTimeFilter(Instant startTime, Instant endTime) {
        this.filterStartTime = startTime;
        this.filterEndTime = endTime;
        updateVisibility();
    }
    
    private void updateVisibility() {
        try {
            long rasterStart = delegate.getStartTimestamp();
            long rasterEnd = delegate.getEndTimestamp();
            
            // Check if raster overlaps with filter time range
            visible = (rasterEnd >= filterStartTime.toEpochMilli() && 
                      rasterStart <= filterEndTime.toEpochMilli());
        } catch (Exception e) {
            // If no timestamp info, always show
            visible = true;
        }
    }
    
    public IndexedRasterPainter getDelegate() {
        return delegate;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    @Override
    public void paint(Graphics2D g, SlippyMap renderer) {
        if (visible) {
            delegate.paint(g, renderer);
        }
    }
}
