package pt.omst.rasterfall.mosaic;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils;

@Slf4j
public class SidescanMosaic implements MapPainter, AutoCloseable {
    private final ArrayList<IndexedRaster> rasters;
    private LocationType topLeft = new LocationType(-90, 180);
    private LocationType bottomRight = new LocationType(90, -180);

    private ArrayList<RasterMosaicPainter> rasterPainters = new ArrayList<>();
    
    public SidescanMosaic(Collection<IndexedRaster> rasters, File folder) {
        this.rasters = new ArrayList<>();
        this.rasters.addAll(rasters);
        Collections.sort(this.rasters, (r1, r2) -> r1.getSamples().get(0).getTimestamp().compareTo(
                r2.getSamples().get(0).getTimestamp()));
        
        // Use this.rasters (sorted) instead of rasters (unsorted) for correct paint order
        for (IndexedRaster raster : this.rasters) {
            try {
                RasterMosaicPainter painter = new RasterMosaicPainter(folder, raster);
                rasterPainters.add(painter);
            } catch (Exception e) {
                log.error("Error creating RasterMosaicPainter for raster {}", raster.getFilename(), e);
            }
        }

        // Just add logging without changing order
        for (int i = 0; i < this.rasters.size(); i++) {
            IndexedRaster r = this.rasters.get(i);
            log.info("Paint order [{}]: {} - timestamp: {}", i, r.getFilename(),
                    r.getSamples().get(0).getTimestamp());
        }
    }

    public Rectangle2D.Double getBounds() {
        // Rectangle2D uses (x=lon, y=lat, width=lonRange, height=latRange)
        return new Rectangle2D.Double(topLeft.getLongitudeDegs(), bottomRight.getLatitudeDegs(),
                bottomRight.getLongitudeDegs() - topLeft.getLongitudeDegs(),
                topLeft.getLatitudeDegs() - bottomRight.getLatitudeDegs());
    }

    @Override
    public void paint(Graphics2D g, SlippyMap map) {
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        for (RasterMosaicPainter painter : rasterPainters) {
            painter.paint(g, map);
        }
    }

    @Override
    public void close() {
        for (RasterMosaicPainter painter : rasterPainters) {
            painter.close();
        }
        rasterPainters.clear();
        rasters.clear();
        log.debug("Closed SidescanMosaic");
    }

    public static void main(String[] args) {
        SlippyMap map = new SlippyMap();
        File folder = new File("/LOGS/091526_harbour-survey/rasterIndex");
        //File folder = new File("/LOGS/102124_survey3/rasterIndex");
        List<IndexedRaster> rasters = IndexedRasterUtils.loadRasters(folder);
        SidescanMosaic mosaic = new SidescanMosaic(rasters, folder);
        // Don't use CachedMapPainter - SidescanMosaic has its own caching/regeneration
        // logic
        map.addRasterPainter(mosaic);

        GuiUtils.testFrame(map, "Sidescan Mosaic", 800, 600);
    }
}