package pt.omst.rasterfall.map;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.time.OffsetDateTime;

import javax.swing.JComponent;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.SampleDescription;

@Slf4j
public class PathMapOverlay extends AbstractMapOverlay {

    private RasterfallTiles waterfall;
    GeneralPath path = new GeneralPath();
    LocationType start = null;
   

    public void setWaterfall(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        path = new GeneralPath();
        path.moveTo(0, 0);        
        Thread.ofVirtual().start(this::createPath);
    }


    private void createPath() {
        OffsetDateTime nextTime = null;
        double height = waterfall.getHeight();
        for (IndexedRaster raster : waterfall.getRasters()) {
            for (SampleDescription sample : raster.getSamples()) {
                if (nextTime == null) {
                    double lat = sample.getPose().getLatitude();
                    double lon = sample.getPose().getLongitude();
                    start = new LocationType(lat, lon);
                    nextTime = sample.getTimestamp().plusSeconds(1);
                }
                if (sample.getTimestamp().isAfter(nextTime)) {
                    double lat = sample.getPose().getLatitude();
                    double lon = sample.getPose().getLongitude();
                    LocationType loc = new LocationType(lat, lon);
                    double[] offsets = loc.getOffsetFrom(start);
                    double x = offsets[1]; // Easting
                    double y = height - offsets[0]; // Northing (inverted Y axis)
                    path.lineTo(x, y);
                    nextTime = sample.getTimestamp().plusSeconds(1);
                }
            }
        }
        log.info("Path created with {} points", path.getCurrentPoint());
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        SlippyMap map = (SlippyMap) c;
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(java.awt.Color.RED);
        g2d.drawString("Path Overlay", 10, 20);
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
