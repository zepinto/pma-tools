//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterfall.RasterfallTiles;
import pt.omst.rasterfall.replay.LogReplay;

@Slf4j
public class ReplayOverlay extends AbstractOverlay implements LogReplay.Listener {

    private final JLabel label = new JLabel();
    private final String html = "<html><body style='color: white; font-family: Helvetica; font-size: 12px;'>";
    private RasterfallTiles waterfall;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    {
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }

    private long lastReplayTime = 0, lastRealTime = 0;
    private double lastSpeed = 0;

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        LogReplay.removeReplayListener(this);
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        label.setText(html + "</body></html>");
        label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        label.setForeground(Color.WHITE);
        label.setBackground(new Color(0, 0, 0, 150));
        label.setOpaque(true);
        LogReplay.addReplayListener(this);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
            long elapsed = System.currentTimeMillis() - lastRealTime;
            long newReplayTime = lastReplayTime + (long) (elapsed * lastSpeed);
            LogReplay.setReplayState(Instant.now(), Instant.ofEpochMilli(newReplayTime), 0);
        }
    }

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        if (e.getID() == MouseEvent.MOUSE_CLICKED) {
            long elapsed = System.currentTimeMillis() - lastRealTime;
            long newReplayTime = lastReplayTime + (long) (elapsed * lastSpeed);
            LogReplay.setReplayState(Instant.now(), Instant.ofEpochMilli(newReplayTime), 0);
        }
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e, JLayer<? extends RasterfallTiles> l) {
        long elapsed = System.currentTimeMillis() - lastRealTime;
        long newReplayTime = lastReplayTime + (long) (elapsed * lastSpeed);
        LogReplay.setReplayState(Instant.now(), Instant.ofEpochMilli(newReplayTime), 0);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255));
        g2.setStroke(new BasicStroke(1.5f));
        g2.translate(waterfall.getVisibleRect().x, waterfall.getVisibleRect().y);
        g2.translate(waterfall.getVisibleRect().getWidth()-250, waterfall.getVisibleRect().getHeight()-90);
        label.setBounds(0, 0, 230, 70);
        label.paint(g2);
        long replayTime = lastReplayTime + (long) ((System.currentTimeMillis() - lastRealTime) * lastSpeed);
        label.setText(html + "R: " + sdf.format(new Date(replayTime)) + "<br>Speed: " + lastSpeed + "X</body></html>");
        g2.translate(-waterfall.getVisibleRect().getWidth()+250, -waterfall.getVisibleRect().getHeight()+20);
    }

    @Override
    public void replayStateChanged(Instant realTime, Instant replayTime, double speed) {
        log.info("Replay time: {}, Speed: {}X", replayTime, speed);
        lastSpeed = speed;
        lastReplayTime = replayTime.toEpochMilli();
        lastRealTime = realTime.toEpochMilli();
        waterfall.repaint();
    }
}


