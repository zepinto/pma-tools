//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.lsts.neptus.mra;

import java.io.Serial;

import lombok.Setter;
import pt.omst.neptus.colormap.ColorMap;
import pt.omst.neptus.colormap.ColorMapFactory;

/**
 * Specialized log marker for sidescan sonar data annotations.
 * 
 * <p>Extends {@link LogMarker} with sidescan-specific properties including spatial dimensions,
 * ground range, subsystem identification, and color map rendering preferences. Markers can
 * represent either point features or rectangular regions of interest in sidescan imagery.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Screen coordinates (x, y) for pixel-based positioning</li>
 *   <li>Dimensions (width, height) in both pixels and ground meters</li>
 *   <li>Subsystem tracking for multi-sonar configurations</li>
 *   <li>Customizable color map for visualization</li>
 *   <li>Point vs. rectangular annotation mode</li>
 *   <li>Version tracking for backward compatibility</li>
 * </ul>
 * 
 * <p>The marker stores both pixel coordinates (for display) and ground range measurements
 * (for real-world metric analysis). This dual representation allows accurate annotation
 * of sidescan features across different zoom levels and display configurations.</p>
 * 
 * @author José Pinto
 * @see LogMarker
 * @see ColorMap
 */
public class SidescanLogMarker extends LogMarker {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final int CURRENT_VERSION = 2;
    @Setter
    private double x;
    @Setter
    private double y;
    @Setter
    private int width;
    @Setter
    private int height;
    @Setter
    private double fullRange;// width in meters
    private int subSys;// created on subSys
    private String colorMap;
    @Setter
    private boolean point;
   
    /**
     * Added version info. For old marks this value will be 0.
     */
    private int sidescanMarkVersion = CURRENT_VERSION;

    /**
     * Creates a new sidescan log marker with full specifications.
     * 
     * @param label Text label for the marker
     * @param timestamp Timestamp in milliseconds since epoch
     * @param lat Latitude in radians
     * @param lon Longitude in radians
     * @param x Ground distance in meters from nadir to the mark
     * @param y Y coordinate in screen pixels
     * @param w Width of the marked region in pixels
     * @param h Height of the marked region in pixels (sidescan lines)
     * @param range Full ground range (swath width) in meters
     * @param subSys Subsystem identifier (for multi-sonar systems)
     * @param colorMap Color map to use for visualization
     */
    public SidescanLogMarker(String label, double timestamp, double lat, double lon, double x, double y,
                             int w, int h, double range, int subSys, ColorMap colorMap) {
        super(label, timestamp, lat, lon);
        this.setX(x);
        this.setY(y);
        this.setWidth(w);
        this.setHeight(h);
        this.setFullRange(range);
        this.setSubSys(subSys);
        this.setColorMap(colorMap.toString());
    }

    /**
     * Initializes default values for subsystem and color map if not already set.
     * 
     * <p>If the subsystem is unset (0), it will be set to the provided value.
     * If no color map is specified, a bronze color map will be applied.
     * Markers with zero width and height are automatically classified as point marks.</p>
     * 
     * @param subSys The subsystem identifier to use as default
     */
    public void setDefaults(int subSys) {
        if (this.getSubSys() == 0)
            this.setSubSys(subSys);

        if (getColorMap() == null)
            setColorMap(ColorMapFactory.createBronzeColormap().toString());

        if (this.getWidth() == 0 && this.getHeight() == 0)
            this.setPoint(true);
    }

    /**
     * Gets the subsystem identifier for which this marker was created.
     * Used to distinguish marks from different sonar heads in multi-sonar configurations.
     * 
     * @return The subsystem identifier
     */
    public int getSubSys() {
        return subSys;
    }

    /**
     * Sets the subsystem identifier.
     * 
     * @param subSys The subsystem identifier
     */
    private void setSubSys(int subSys) {
        this.subSys = subSys;
    }

    /**
     * Gets the color map name used for visualizing this marker.
     * 
     * @return The color map identifier string
     */
    public String getColorMap() {
        return colorMap;
    }

    /**
     * Gets the width of the marked region in pixels at the time of creation.
     * This is a screen-space measurement and may not be directly useful for analysis.
     * For ground measurements, use {@link #getFullRange()}.
     * 
     * @return The width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the height of the marked region in pixels (number of sidescan lines).
     * Represents the temporal extent of the mark in the waterfall display.
     * 
     * @return The height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Sets the color map identifier for this marker.
     * 
     * @param colorMap The color map identifier string
     */
    private void setColorMap(String colorMap) {
        this.colorMap = colorMap;
    }

    /**
     * Updates the geographic location of this marker.
     * Useful for correcting marker positions based on improved navigation data.
     * 
     * @param latRads Corrected latitude in radians
     * @param lonRads Corrected longitude in radians
     */
    public void fixLocation(double latRads, double lonRads) {
        this.setLatRads(latRads);
        this.setLonRads(lonRads);
    }

    /**
     * Gets the version number of this sidescan marker's data format.
     * Used for backward compatibility when loading older marker files.
     * Old marks will have version 0.
     * 
     * @return The marker format version number
     */
    public int getSidescanMarkVersion() {
        return sidescanMarkVersion;
    }

    /**
     * Resets the marker version to the current version ({@value #CURRENT_VERSION}).
     * Should be called after migrating or updating marker data to the latest format.
     */
    public void resetSidescanMarkVersion() {
        this.sidescanMarkVersion = CURRENT_VERSION;
    }

    /**
     * Gets the ground distance in meters from the sonar nadir (vehicle track) to the mark.
     * This is the across-track distance representing the true ground range to the feature.
     * 
     * @return The ground distance in meters
     */
    public double getX() {
        return x;
    }

    /**
     * Gets the Y coordinate in screen pixels.
     * This is a display-space measurement primarily used for rendering.
     * 
     * @return The Y coordinate in pixels
     */
    public double getY() {
        return y;
    }

    /**
     * Gets the full swath width (ground range) in meters.
     * Represents the total across-track coverage of the sidescan at the time of marking.
     * 
     * @return The swath width in meters
     */
    public double getFullRange() {
        return fullRange;
    }

    /**
     * Checks if this marker represents a point feature or a rectangular region.
     * Point markers have zero width and height and typically mark specific targets.
     * 
     * @return true if this is a point marker, false if it's a rectangular region
     */
    public boolean isPoint() {
        return point;
    }

}
