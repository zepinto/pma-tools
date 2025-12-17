//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.map;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.util.List;

import javax.swing.JComponent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.omst.mapview.AbstractMapOverlay;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterfall.mosaic.SidescanMosaic;
import pt.omst.rasterlib.IndexedRaster;

/**
 * Map overlay that displays sidescan mosaic from waterfall tiles.
 * This overlay creates and manages a SidescanMosaic painter for the rasters.
 */
@Slf4j
public class SidescanMosaicMapOverlay extends AbstractMapOverlay {

    private SlippyMap map;
    
    @Getter @Setter
    private List<IndexedRaster> rasters;
    
    @Getter @Setter
    private File rastersFolder;
    
    private SidescanMosaic mosaicPainter;
    
    @Override
    public String getTooltip() {
        return "Show sidescan mosaic overlay";
    }

    @Override
    public String getToolbarName() {
        return "Mosaic";
    }

    @Override
    public void cleanup(SlippyMap map) {
        if (mosaicPainter != null) {
            mosaicPainter.close();
            mosaicPainter = null;
        }
        this.map = null;
    }

    @Override
    public void install(SlippyMap map) {
        this.map = map;
        // Recreate mosaic painter if we have rasters and folder
        if (rasters != null && !rasters.isEmpty() && rastersFolder != null) {
            createMosaicPainter();
        }
    }
    
    /**
     * Set the rasters and folder to use for the mosaic.
     * This will create the mosaic painter and add it to the map.
     * 
     * @param rasters the list of IndexedRaster objects
     * @param folder the folder containing the raster images
     */
    public void setRastersAndFolder(List<IndexedRaster> rasters, File folder) {
        this.rasters = rasters;
        this.rastersFolder = folder;
        
        if (map != null && rasters != null && !rasters.isEmpty() && folder != null) {
            createMosaicPainter();
        }
    }
    
    /**
     * Create the mosaic painter.
     */
    private void createMosaicPainter() {
        // Clean up existing painter
        if (mosaicPainter != null) {
            mosaicPainter.close();
        }
        
        // Create new mosaic painter
        mosaicPainter = new SidescanMosaic(rasters, rastersFolder);
        
        if (map != null) {
            map.repaint();
        }
                
        log.info("Created sidescan mosaic with {} rasters", rasters.size());
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (mosaicPainter != null && map != null) {
            mosaicPainter.paint((Graphics2D) g, map);
        }
    }
}
