//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.BevelBorder;
import javax.swing.plaf.LayerUI;

import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterfall.replay.LogReplay;

public class RasterfallOverlays extends LayerUI<RasterfallTiles> implements LogReplay.Listener, Closeable {

    private final CopyOnWriteArrayList<AbstractOverlay> activeOverlays = new CopyOnWriteArrayList<>();
    private final RasterfallTiles tiles;

    private JButton playButton;
    private JToggleButton measureButton, heightButton, markButton, infoButton, gridButton, rulerButton, hudButton;
    private JSpinner speedSpinner;

    private final RulerOverlay rulerOverlay = new RulerOverlay();
    private final GridOverlay gridOverlay = new GridOverlay();
    private final InfoOverlay infoOverlay = new InfoOverlay();
    private final MarkOverlay markOverlay = new MarkOverlay();
    private final HudOverlay hudOverlay = new HudOverlay();
    private final LengthOverlay lengthOverlay = new LengthOverlay();
    private final HeightOverlay heightOverlay = new HeightOverlay();
    private final ReplayOverlay replayOverlay = new ReplayOverlay();
    private final ContactsOverlay contactsOverlay = new ContactsOverlay();
    private final ButtonGroup group = new ButtonGroup();
    private final JPanel controlsPanel = new JPanel(new BorderLayout());
    private final JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

    private boolean replaying = false;

    double replaySpeed = 0;
    Instant replayTime = null, realTime = null;

    public RasterfallOverlays(RasterfallTiles tiles) {
        this.tiles = tiles;
        LogReplay.addReplayListener(this);
        contactsOverlay.install(tiles);
    }

    public double getSpeed() {
        if (replaying)
            return Double.parseDouble(speedSpinner.getValue().toString());
        else
            return 0;
    }

    public void addOverlay(AbstractOverlay overlay, AbstractButton button, boolean exclusive) {
        activeOverlays.add(overlay);
        overlay.install(tiles);
        button.setMargin(new Insets(0,0,0,0));
        if (exclusive)
            group.add(button);
        leftPanel.add(button);
    }

    public JPanel getToolsPanel() {
        controlsPanel.add(leftPanel, BorderLayout.WEST);
        controlsPanel.add(rightPanel, BorderLayout.EAST);

        playButton = new JButton("<html><h2>&#x25B6;</h2></html>");
        playButton.setPreferredSize(new Dimension(33, 33));
        playButton.addActionListener(e -> playAction());
        playButton.setMargin(new Insets(0, 0, 0, 0));

        measureButton = new JToggleButton("<html><h3>&#x27F7; length</h3></html>");
        measureButton.setPreferredSize(new Dimension(80, 33));
        measureButton.addChangeListener(e -> overlayAction(lengthOverlay, measureButton.isSelected()));
        measureButton.setMargin(new Insets(0, 0, 0, 0));

        heightButton = new JToggleButton("<html><h3>&#x2912; &nbsp; height</h3></html>");
        heightButton.setPreferredSize(new Dimension(80, 33));
        heightButton.addChangeListener(e -> overlayAction(heightOverlay, heightButton.isSelected()));
        heightButton.setMargin(new Insets(0, 0, 0, 0));

        infoButton = new JToggleButton("<html><h3>&#x2139; &nbsp; info</h3></html>");
        infoButton.setPreferredSize(new Dimension(80, 33));
        infoButton.addChangeListener(e -> overlayAction(infoOverlay, infoButton.isSelected()));
        infoButton.setMargin(new Insets(0, 0, 0, 0));

        markButton = new JToggleButton("<html><h3>&#x26f6; &nbsp; mark</h3></html>");
        markButton.setPreferredSize(new Dimension(80, 33));
        markButton.addChangeListener(e -> overlayAction(markOverlay, markButton.isSelected()));
        markButton.setMargin(new Insets(0, 0, 0, 0));

        gridButton = new JToggleButton("<html><h3>&#x2317; &nbsp; grid</h3></html>");
        gridButton.setPreferredSize(new Dimension(80, 33));
        gridButton.addChangeListener(e -> overlayAction(gridOverlay, gridButton.isSelected()));
        gridButton.setMargin(new Insets(0, 0, 0, 0));

        rulerButton = new JToggleButton("<html><h3>&#x2057; &nbsp; ruler</h3></html>");
        rulerButton.setPreferredSize(new Dimension(80, 33));
        rulerButton.addChangeListener(e -> overlayAction(rulerOverlay, rulerButton.isSelected()));
        rulerButton.setMargin(new Insets(0, 0, 0, 0));

        hudButton = new JToggleButton("<html><h3>&#x2608; &nbsp; hud</h3></html>");
        hudButton.setPreferredSize(new Dimension(80, 33));
        hudButton.addChangeListener(e -> overlayAction(hudOverlay, hudButton.isSelected()));
        hudButton.setMargin(new Insets(0, 0, 0, 0));
        hudButton.setSelected(true);

        speedSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 250, 1));
        speedSpinner.setPreferredSize(new Dimension(75, 33));
        speedSpinner.setFont(new Font("Arial", Font.PLAIN, 16));
        speedSpinner.addChangeListener(e -> {
            long timestamp = tiles.getTimestamp();
            if (replaying)
                LogReplay.setReplayState(Instant.now(), Instant.ofEpochMilli(timestamp), Double.parseDouble(speedSpinner.getValue().toString()));
            else
                LogReplay.setReplayState(Instant.now(), Instant.ofEpochMilli(timestamp), 0);
        });

        leftPanel.add(infoButton);
        leftPanel.add(hudButton);
        leftPanel.add(gridButton);
        leftPanel.add(rulerButton);
        leftPanel.add(markButton);
        leftPanel.add(measureButton);
        leftPanel.add(heightButton);

        rightPanel.add(speedSpinner);
        rightPanel.add(playButton);
        controlsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        group.add(measureButton);
        group.add(heightButton);
        group.add(markButton);

        return controlsPanel;
    }

    public void playAction() {
        replaying = !replaying;
        if (replaying) {
            playButton.setText("<html><h2>&#x25A0;</h2></html>");
            long timestamp = tiles.getTimestamp(new Point(0, tiles.getVisibleRect().y + tiles.getVisibleRect().height / 2));
            LogReplay.setReplayState(Instant.now(), Instant.ofEpochMilli(timestamp), Double.parseDouble(speedSpinner.getValue().toString()));
        } else {
            playButton.setText("<html><h2>&#x25B6;</h2></html>");
            long timestamp = tiles.getTimestamp(new Point(0, tiles.getVisibleRect().y + tiles.getVisibleRect().height / 2));
            LogReplay.setReplayState(Instant.now(), Instant.ofEpochMilli(timestamp), 0);
        }
    }

    @Override
    public void replayStateChanged(Instant realTime, Instant replayTime, double speed) {
        this.replayTime = replayTime;
        this.realTime = realTime;
        this.replaySpeed = speed;

        replaying = (speed != 0);

        if (replaying) {
            speedSpinner.setValue((int)speed);
            replayOverlay.cleanup(tiles);
            replayOverlay.install(tiles);
            activeOverlays.add(replayOverlay);
            playButton.setText("<html><h2>&#x25A0;</h2></html>");
        }
        else {
            playButton.setText("<html><h2>&#x25B6;</h2></html>");
            replayOverlay.cleanup(tiles);
            activeOverlays.remove(replayOverlay);
        }
    }


    void overlayAction(AbstractOverlay overlay, boolean selected) {
        if (selected && !activeOverlays.contains(overlay)) {
            activeOverlays.add(overlay);
            overlay.install(tiles);
            //System.out.println(overlay.getToolbarName()+": "+selected);
        } else if (!selected && activeOverlays.contains(overlay)) {
            overlay.cleanup(tiles);
            activeOverlays.remove(overlay);
            //System.out.println(overlay.getToolbarName()+": "+selected);
        }

        tiles.repaint();
    }

    @Override
    public void paint(java.awt.Graphics g, javax.swing.JComponent c) {
        super.paint(g, c);
        
        for (AbstractOverlay overlay : activeOverlays) {
            overlay.paint(g, c);
        }
       
        contactsOverlay.paint(g, c);
    }

    @Override
    protected void processMouseEvent(java.awt.event.MouseEvent e, javax.swing.JLayer<? extends RasterfallTiles> l) {
        for (AbstractOverlay overlay : activeOverlays)
            overlay.processMouseEvent(e, l);
    }

    @Override
    protected void processMouseMotionEvent(java.awt.event.MouseEvent e, javax.swing.JLayer<? extends RasterfallTiles> l) {
        for (AbstractOverlay overlay : activeOverlays)
            overlay.processMouseMotionEvent(e, l);
    }

    @Override
    protected void processMouseWheelEvent(java.awt.event.MouseWheelEvent e, javax.swing.JLayer<? extends RasterfallTiles> l) {
        for (AbstractOverlay overlay : activeOverlays)
            overlay.processMouseWheelEvent(e, l);
    }

    @Override
    protected void processComponentEvent(java.awt.event.ComponentEvent e, javax.swing.JLayer<? extends RasterfallTiles> l) {
        for (AbstractOverlay overlay : activeOverlays)
            overlay.processComponentEvent(e, l);
    }

    @Override
    public void close() throws IOException {
        LogReplay.removeReplayListener(this);
    }
}
