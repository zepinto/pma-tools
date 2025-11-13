//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;

import javax.swing.JPanel;

import lombok.extern.slf4j.Slf4j;
import pt.omst.mapview.SlippyMap;
import pt.omst.neptus.core.LocationType;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.mapview.IndexedRasterPainter;

@Slf4j
public class MapViewer extends JPanel {

    private final SlippyMap renderer2D = new SlippyMap();

    public MapViewer(File folder, IndexedRaster raster) {
        setLayout(new BorderLayout());
        add(renderer2D, BorderLayout.CENTER);
        if (raster.getSamples().isEmpty()) {
            log.warn("Raster has no samples.");
            return;
        }

        try {
            IndexedRasterPainter painter = new IndexedRasterPainter(folder, raster);
            Pose pose = raster.getSamples().get(raster.getSamples().size()/2).getPose();
            LocationType loc = new LocationType(pose.getLatitude(), pose.getLongitude());
            renderer2D.focus(loc.getLatitudeDegs(), loc.getLongitudeDegs(), 19);
            renderer2D.addPainter(painter);
        }
        catch (IOException e) {
            log.warn("Error creating IndexedRasterPainter: " + e.getMessage());
        }
    }

    @Override
    public void removeNotify() {
        renderer2D.close();        
    }
}
