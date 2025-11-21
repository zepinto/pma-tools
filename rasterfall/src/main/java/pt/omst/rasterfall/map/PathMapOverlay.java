package pt.omst.rasterfall.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import javax.swing.JComponent;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SampleDescription;

@Slf4j
public class PathMapOverlay extends AbstractMapOverlay {

    private RasterfallTiles waterfall;
    private GeneralPath offsetPath = new GeneralPath();
    private LocationType startLocation = null;
    private volatile boolean pathReady = false;

    private ArrayList<LocationType> pathLocations = new ArrayList<>();

    public void setWaterfall(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        offsetPath = new GeneralPath();
        startLocation = null;
        pathReady = false;
        Thread.ofVirtual().start(this::createPath);        
    }

    private void createPath() {
        OffsetDateTime nextTime = null;
        int sampleCount = 0;
        int totalCount = 0;
        ArrayList<IndexedRaster> rasters = new ArrayList<>(waterfall.getRasters());
        rasters.sort((r1, r2) -> r1.getSamples().get(0).getTimestamp().compareTo(r2.getSamples().get(0).getTimestamp()));
        for (IndexedRaster raster : rasters) {
            for (SampleDescription sample : raster.getSamples()) {
                if (sample.getPose() != null) {
                    totalCount++;
                    Pose pose = sample.getPose();
                    
                    if (nextTime == null) {
                        startLocation = new LocationType(pose.getLatitude(), pose.getLongitude());
                        offsetPath.moveTo(0, 0);
                        nextTime = sample.getTimestamp().plusSeconds(1);
                       
                    } else if (sample.getTimestamp().isAfter(nextTime)) {
                        LocationType loc = new LocationType(pose.getLatitude(), pose.getLongitude());
                        double[] offsets = loc.getOffsetFrom(startLocation);
                        offsetPath.lineTo(offsets[1], offsets[0]);
                        pathLocations.add(loc);
                        sampleCount++;
                        nextTime = sample.getTimestamp().plusSeconds(1);
                       
                    }
                }
            }
        }        
        
        pathReady = true;
        log.info("Path created with {} samples from {} total samples", sampleCount, totalCount);
    }

    @Override
    public boolean processMouseEvent(MouseEvent e, SlippyMap map) {
        if (!pathReady || startLocation == null) {
            return false;
        }

        if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getClickCount() == 2) {
            LocationType clickLoc = map.getRealWorldPosition(e.getX(), e.getY());
            double closestDistance = Double.MAX_VALUE;
            LocationType closestLoc = null;
            for (LocationType loc : pathLocations) {
                double distance = loc.getDistanceInMeters(clickLoc);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestLoc = loc;
                }
            }

            log.info("Closest location to click is at {} (distance: {} meters)",
                    closestLoc != null ? closestLoc.toString() : "N/A",
                    closestDistance != Double.MAX_VALUE ? String.format("%.2f", closestDistance) : "N/A");
        }
        return super.processMouseEvent(e, map);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (!pathReady || startLocation == null) {
            return;
        }
        SlippyMap map = (SlippyMap) c;
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Get screen position of start location
        double[] startScreen = map.latLonToScreen(startLocation.getLatitudeDegs(), 
                                                   startLocation.getLongitudeDegs());
        
        // Calculate scale based on current zoom level
        // At each zoom level, the scale doubles
        int zoom = map.getLevelOfDetail();
        double metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(startLocation.getLatitudeDegs())) 
                                / Math.pow(2, zoom);
        double scale = 1.0 / metersPerPixel;
        
        // Create transform: translate to start position, then scale from meters to pixels
        AffineTransform transform = new AffineTransform();
        transform.translate(startScreen[0], startScreen[1]);
        transform.scale(scale, -scale); // Negative Y scale because screen Y increases downward
        
        // Apply transform and draw
        GeneralPath transformedPath = (GeneralPath) offsetPath.clone();
        transformedPath.transform(transform);
        
        g2d.setColor(new Color(165, 65, 0, 180)); // Orange with transparency
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.draw(transformedPath);
    }

    @Override
    public void cleanup(SlippyMap map) {
        // Nothing to cleanup
    }

    @Override
    public void install(SlippyMap map) {
       // Nothing to install
    }

}
