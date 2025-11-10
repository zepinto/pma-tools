//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.contacts;

import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.renderer2d.StateRenderer2D;
import pt.omst.neptus.types.coord.LocationType;
import pt.omst.neptus.plugins.mra.exporters.indexraster.IndexedRasterPainter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.Pose;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

@Slf4j
public class MapViewer extends JPanel {

    private final StateRenderer2D renderer2D = new StateRenderer2D();

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
            renderer2D.setCenter(loc);
            renderer2D.addPostRenderPainter(painter, "IndexedRasterPainter");
        }
        catch (IOException e) {
            log.warn("Error creating IndexedRasterPainter: " + e.getMessage());
        }
    }

    @Override
    public void removeNotify() {
        try {
            renderer2D.close();
        } catch (IOException e) {
            log.warn("Error closing MapViewer: " + e.getMessage());
        }
    }
}
