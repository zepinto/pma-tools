//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.mapview;

import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils;

/**
 * Represents a layer containing multiple indexed rasters from a folder.
 * 
 * <p>A RasterLayer groups all rasters found in a folder (via its rasterIndex subdirectory)
 * and provides visibility control and timestamp filtering capabilities. Each layer is painted
 * as a single MapPainter that manages its collection of IndexedRasterPainter instances.</p>
 * 
 * <h3>Visibility and Filtering:</h3>
 * <ul>
 *   <li>Layers can be toggled on/off via the visible property</li>
 *   <li>Time filters can be applied to show only rasters within a specific timestamp range</li>
 *   <li>Filtered rasters are simply not painted, but remain loaded in memory</li>
 * </ul>
 * 
 * @see IndexedRasterPainter
 * @see LayeredRasterViewer
 */
@Slf4j
public class RasterLayer implements MapPainter {
    
    private final String name;
    
    @Getter
    private final File folder;
    
    @Getter
    private final List<IndexedRasterPainter> rasterPainters = new ArrayList<>();
    
    @Getter
    @Setter
    private boolean visible = true;
    
    @Getter
    @Setter
    private Long startTimestampFilter = null;
    
    @Getter
    @Setter
    private Long endTimestampFilter = null;
    
    /**
     * Creates a new raster layer from a folder.
     * @param folder the folder containing raster files
     * @throws IOException if an error occurs while loading rasters
     */
    public RasterLayer(File folder) throws IOException {
        this.folder = folder;
        this.name = folder.getName();
        loadRasters();
    }
    
    /**
     * Loads all raster files from the folder.
     */
    private void loadRasters() throws IOException {
        List<File> rasterFiles = IndexedRasterUtils.findRasterFiles(folder);
        log.info("Loading {} raster files from {}", rasterFiles.size(), folder.getName());
        
        for (File rasterFile : rasterFiles) {
            try {
                IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(rasterFile.toPath()));
                IndexedRasterPainter painter = new IndexedRasterPainter(rasterFile.getParentFile(), raster);
                rasterPainters.add(painter);
            } catch (IOException e) {
                log.warn("Error loading raster from {}: {}", rasterFile.getAbsolutePath(), e.getMessage());
            }
        }
        
        log.info("Loaded {} rasters for layer {}", rasterPainters.size(), name);
    }
    
    @Override
    public void paint(Graphics2D g, SlippyMap map) {
        if (!visible) {
            return;
        }
        
        for (IndexedRasterPainter painter : rasterPainters) {
            // Apply timestamp filter if set
            if (startTimestampFilter != null || endTimestampFilter != null) {
                long rasterTimestamp = painter.getStartTimestamp();
                if (startTimestampFilter != null && rasterTimestamp < startTimestampFilter) {
                    continue;
                }
                if (endTimestampFilter != null && rasterTimestamp > endTimestampFilter) {
                    continue;
                }
            }
            
            painter.paint(g, map);
        }
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Gets the earliest timestamp from all rasters in this layer.
     */
    public long getMinTimestamp() {
        return rasterPainters.stream()
                .mapToLong(IndexedRasterPainter::getStartTimestamp)
                .min()
                .orElse(0);
    }
    
    /**
     * Gets the latest timestamp from all rasters in this layer.
     */
    public long getMaxTimestamp() {
        return rasterPainters.stream()
                .mapToLong(IndexedRasterPainter::getStartTimestamp)
                .max()
                .orElse(0);
    }
}
