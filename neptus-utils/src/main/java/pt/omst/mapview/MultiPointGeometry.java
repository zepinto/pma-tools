//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.LocationType;

/**
 * A geometry class that holds a list of coordinates (LocationType + offsets),
 * a color, and implements MapPainter to paint it on a map.
 * 
 * Inspired by PathElement from Neptus.
 * 
 * @author José Pinto
 */
@Slf4j
public class MultiPointGeometry implements MapPainter {
    
    @Getter @Setter
    private LocationType centerLocation = new LocationType();
    
    @Getter @Setter
    private Color color = Color.BLUE;
    
    @Getter @Setter
    private Stroke stroke = new BasicStroke(2);
    
    @Getter @Setter
    private boolean filled = false;
    
    @Getter @Setter
    private boolean shape = false;
    
    @Getter @Setter
    private boolean opaque = false;
    
    private final List<Point2D.Double> offsets = new ArrayList<>();
    
    /**
     * The shape in meters [east, -north] point tuples relative to center location.
     */
    private GeneralPath path;
    
    private boolean firstPoint = true;
    
    /**
     * Creates an empty MultiPointGeometry.
     */
    public MultiPointGeometry() {
        this.path = new GeneralPath();
    }
    
    /**
     * Creates a MultiPointGeometry with a center location.
     * 
     * @param centerLocation The center location
     */
    public MultiPointGeometry(LocationType centerLocation) {
        this.centerLocation = new LocationType(centerLocation);
        this.path = new GeneralPath();
        this.path.moveTo(0, 0);
    }
    
    /**
     * Creates a MultiPointGeometry with a center location and color.
     * 
     * @param centerLocation The center location
     * @param color The color to use for rendering
     */
    public MultiPointGeometry(LocationType centerLocation, Color color) {
        this(centerLocation);
        this.color = color;
    }
    
    /**
     * Clears all points from the geometry.
     */
    public void clear() {
        this.path = new GeneralPath();
        this.offsets.clear();
        this.firstPoint = true;
    }
    
    /**
     * Adds a point using east/north offsets from the center location.
     * 
     * @param eastOffset Offset in meters to the east
     * @param northOffset Offset in meters to the north
     */
    public void addPoint(double eastOffset, double northOffset) {
        if (!firstPoint) {
            path.lineTo((float) eastOffset, -(float) northOffset);
        } else {
            firstPoint = false;
            path.moveTo((float) eastOffset, -(float) northOffset);
        }
        offsets.add(new Point2D.Double(eastOffset, northOffset));
    }
    
    /**
     * Adds a point by its absolute location.
     * The offset from the center location is calculated automatically.
     * 
     * @param point The absolute location to add
     */
    public void addPoint(LocationType point) {
        double[] offsetsNED = point.getOffsetFrom(centerLocation);
        addPoint(offsetsNED[1], offsetsNED[0]); // [1] is east, [0] is north
    }
    
    /**
     * Sets all locations at once.
     * The first location becomes the center location.
     * 
     * @param locations Collection of locations
     */
    public void setLocations(Collection<LocationType> locations) {
        clear();
        
        if (locations.isEmpty()) {
            return;
        }
        
        List<LocationType> locList = new ArrayList<>(locations);
        this.centerLocation = new LocationType(locList.get(0));
        
        for (int i = 1; i < locList.size(); i++) {
            addPoint(locList.get(i));
        }
    }
    
    /**
     * Gets all points as absolute LocationType objects.
     * 
     * @return List of locations
     */
    public List<LocationType> getLocations() {
        List<LocationType> locations = new ArrayList<>();
        
        for (Point2D.Double offset : offsets) {
            LocationType loc = new LocationType(centerLocation);
            loc.translatePosition(offset.y, offset.x, 0); // y is north, x is east
            locations.add(loc);
        }
        
        return locations;
    }
    
    /**
     * Gets the number of points in this geometry.
     * 
     * @return Number of points
     */
    public int getPointCount() {
        return offsets.size();
    }
    
    /**
     * Checks if a point is contained within this geometry.
     * 
     * @param point The point to check
     * @return true if the point is contained
     */
    public boolean containsPoint(LocationType point) {
        double[] offsetsNED = point.getOffsetFrom(centerLocation);
        Point2D pt = new Point2D.Double(offsetsNED[1], -offsetsNED[0]);
        return path.contains(pt);
    }
    
    @Override
    public void paint(Graphics2D g, SlippyMap map) {
        if (offsets.isEmpty()) {
            log.info("No points to paint in MultiPointGeometry");
            return;
        }
        
        if (opaque) {
            log.info("Painting opaque MultiPointGeometry");
            paintOpaque(g, map);
            return;
        }
        
        Color c = color;
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 175));
        
        // Draw the path by converting each point to screen coordinates
        GeneralPath screenPath = buildScreenPath(map);
        log.info("Painting MultiPointGeometry with {} points", offsets.size());
        g.setStroke(stroke);
        g.draw(screenPath);
        
        if (shape && filled) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
            g.fill(screenPath);
        }
    }
    
    /**
     * Paints the geometry with opaque fill.
     * 
     * @param g The graphics context
     * @param map The map to paint on
     */
    private void paintOpaque(Graphics2D g, SlippyMap map) {
        GeneralPath screenPath = buildScreenPath(map);        
        g.setColor(color.darker().darker());
        g.setStroke(stroke);
        g.draw(screenPath);
        g.setColor(color);
        g.fill(screenPath);
    }
    
    /**
     * Builds a GeneralPath in screen coordinates from the geometry points.
     * 
     * @param map The map to use for coordinate conversion
     * @return A GeneralPath in screen coordinates
     */
    private GeneralPath buildScreenPath(SlippyMap map) {
        GeneralPath screenPath = new GeneralPath();
        boolean first = true;

        // Get all absolute locations (don't add center as separate point)
        List<LocationType> allPoints = getLocations();

        for (LocationType loc : allPoints) {
            double[] coords = map.latLonToScreen(loc.getLatitudeDegs(), loc.getLongitudeDegs());
            if (first) {
                screenPath.moveTo(coords[0], coords[1]);
                first = false;
            } else {
                screenPath.lineTo(coords[0], coords[1]);
            }
        }

        if (shape) {
            screenPath.closePath();
        }

        return screenPath;
    }
    
   
    @Override
    public int getLayerPriority() {
        return 5;
    }
}
