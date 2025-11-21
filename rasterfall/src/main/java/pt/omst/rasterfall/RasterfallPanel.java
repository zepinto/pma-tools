//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JViewport;

import lombok.extern.java.Log;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.rasterfall.overlays.RasterfallOverlays;

@Log
public class RasterfallPanel extends JPanel implements Closeable {

    private final RasterfallScrollbar scrollbar;
    private final RasterfallTiles waterfall;
    private final RasterfallOverlays overlays;
    private final RasterfallReplay replay;
    private final JViewport viewport = new JViewport() {
        @Override
        public void doLayout() {
            super.doLayout();
            // Force waterfall to recalculate tile sizes after viewport layout
            if (waterfall != null) {
                waterfall.invalidate();
                waterfall.revalidate();
            }
        }
    };

    public RasterfallPanel(File rastersFolder, Consumer<String> progressCallback) {
        setLayout(new BorderLayout());
        this.waterfall = new RasterfallTiles(rastersFolder, progressCallback);
        if (progressCallback != null) {
            progressCallback.accept("Setting up overlays...");
        }
        overlays = new RasterfallOverlays(waterfall);
        JLayer<RasterfallTiles> layer = new JLayer<>(waterfall, overlays);
        if (progressCallback != null) {
            progressCallback.accept("Overlays set up.");
        }
        overlays.installUI(layer);
        layer.setLayerEventMask(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
        viewport.setView(layer);
        this.scrollbar = new RasterfallScrollbar(waterfall, viewport);
        if (progressCallback != null) {
            progressCallback.accept("Viewport and scrollbar set up.");
        }
        add(viewport, BorderLayout.CENTER);
        if (progressCallback != null) {
            progressCallback.accept("Adding scrollbar...");
        }
        add(scrollbar, BorderLayout.EAST);
        if (progressCallback != null) {
            progressCallback.accept("Adding tools...");
        }
        add(overlays.getToolsPanel(progressCallback), BorderLayout.NORTH);
        if (progressCallback != null) {
            progressCallback.accept("Tools panel added.");
        }

        if (progressCallback != null) {
            progressCallback.accept("Setting up replay controller...");
        }
        this.replay = new RasterfallReplay(scrollbar);
        if (progressCallback != null) {
            progressCallback.accept("Replay controller set up.");
        }
    }

    @Override
    public void close() throws IOException {
        scrollbar.close();
        overlays.close();
        replay.close();
        System.out.println("RasterfallPanel closed.");
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
    }

    @Override
    public void addNotify() {
        viewport.revalidate();
        waterfall.repaint();
        super.addNotify();
    }

    public void scrollToTime(long time) {
        scrollbar.scrollToTime(time, true);
    }

    public RasterfallTiles getWaterfall() {
        return waterfall;
    }

    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        RasterfallPanel rasterfall = new RasterfallPanel(new File("/LOGS/REP/REP24/lauv-omst-2/20240910/093214_omst-mwm/rasterIndex"), null);
        JFrame frame = new JFrame("OceanScan Rasterfall");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setContentPane(rasterfall);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
}
