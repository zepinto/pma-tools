//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import lombok.extern.java.Log;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.rasterfall.overlays.RasterfallOverlays;
import pt.omst.rasterlib.contacts.CompressedContact;

@Log
public class RasterfallPanel extends JPanel implements Closeable {

    private final RasterfallScrollbar scrollbar;
    private final RasterfallTiles waterfall;
    private final RasterfallOverlays overlays;
    private final RasterfallReplay replay;
    private final JViewport viewport = new JViewport();

    public RasterfallPanel(File rastersFolder, Consumer<String> progressCallback) {
        setLayout(new BorderLayout());
        this.waterfall = new RasterfallTiles(rastersFolder, progressCallback);
        overlays = new RasterfallOverlays(waterfall);
        JLayer<RasterfallTiles> layer = new JLayer<>(waterfall, overlays);
        overlays.installUI(layer);
        layer.setLayerEventMask(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
        viewport.setView(layer);
        this.scrollbar = new RasterfallScrollbar(waterfall, viewport);
        add(viewport, BorderLayout.CENTER);
        add(scrollbar, BorderLayout.EAST);
        add(overlays.getToolsPanel(), BorderLayout.NORTH);
        this.replay = new RasterfallReplay(scrollbar);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    doLayout();
                    revalidate();
                    waterfall.revalidate();
                    waterfall.repaint();
                });
            }
        });
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

    public void reloadContacts() {
        long start = System.currentTimeMillis();
        waterfall.getContacts().reload();
        System.out.println("Reloaded contacts in " + (System.currentTimeMillis() - start) + "ms");
    }

    public void focusContact(String label) {
        CompressedContact contact = waterfall.getContacts().getContact(label);
        if (contact != null) {
            scrollbar.scrollToTime(contact.getTimestamp(), true);
            System.out.println("Contact " + label + " found at " + contact.getTimestamp());
        }
        else {
            System.out.println("Contact " + label + " not found");
        }
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
