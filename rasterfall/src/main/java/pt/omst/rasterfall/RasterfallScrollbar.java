//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import lombok.extern.java.Log;
import pt.omst.rasterfall.replay.LogReplay;

@Log
public class RasterfallScrollbar extends JComponent implements LogReplay.Listener, Closeable {
    private final int scrollWidth = 80;
    private final int scrollHeight = 35;
    private BufferedImage scrollImage = null;
    private double position = 0;
    private final JViewport viewport;
    private Point2D scrollDragStart = null, waterfallDragStart = null;
    private final RasterfallTiles waterfall;

    public long getStartTime() {
        return waterfall.getStartTime();
    }

    public long getEndTime() {
        return waterfall.getEndTime();
    }

    public RasterfallScrollbar(RasterfallTiles waterfall, JViewport viewport) {
        this.viewport = viewport;
        this.waterfall = waterfall;
        int height = 0;
        addGuiHooks();
        setFocusable(true);
        requestFocusInWindow();

        addFocusListener(new FocusAdapter() {

            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                SwingUtilities.invokeLater(() -> {
                    try {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException e1) {
                        log.warning("Error sleeping: " + e1.getMessage());
                    }
                    requestFocusInWindow();

                });
            }
        });


        scrollImage = waterfall.getScrollImage(scrollWidth, (Void) -> {
            repaint();
        });
        setPreferredSize(new Dimension(scrollWidth, height));
    }

    public void addGuiHooks() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    position -= 0.2;
                    position = Math.max(0, position);
                    updateViewPort(position);
                    repaint();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    position += 0.2;
                    position = Math.min(getHeight() - scrollHeight, position);
                    updateViewPort(position);
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                scrollDragStart = new Point2D.Double(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                scrollDragStart = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int y = e.getY();
                    y = Math.max(0, y);
                    double timePerPixel = (double) (getStartTime() - getEndTime()) / scrollImage.getHeight();
                    double diff = y - position;
                    double timeSpan = diff * timePerPixel;
                    double newTime = getTime().toEpochMilli() + timeSpan;
                    scrollToTime((long)newTime, true);
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                //System.out.println(yToTimestamp(e.getY()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (scrollDragStart != null) {
                    double dy = e.getY() - scrollDragStart.getY();
                    position += dy;
                    position = Math.min(getHeight() - scrollHeight, position);
                    position = Math.max(0, position);
                    scrollDragStart.setLocation(e.getX(), e.getY());
                    //ReplayListener.publishState(Instant.now(), getTime(), 0);
                    updateViewPort(position);
                    repaint();
                }
            }
        });

        addMouseWheelListener(e -> {
            position += e.getWheelRotation();
            position = Math.min(getHeight() - scrollHeight, position);
            position = Math.max(0, position);
            updateViewPort(position);
            repaint();
        });

        waterfall.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                waterfallDragStart = null;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                waterfallDragStart = e.getPoint();
            }
        });

        waterfall.addMouseWheelListener(e -> {
            Point mousePosition = e.getPoint(); // Mouse position in viewport
            Point viewPosition = viewport.getViewPosition();
            double previousZoom = waterfall.getZoom();
            double zoom = waterfall.getZoom();

            // Adjust zoom level based on wheel rotation
            if (e.getWheelRotation() > 0) {
                zoom = Math.max(zoom * 0.9, 1); // Min zoom level of 1
            } else {
                zoom = Math.min(zoom * 1.1, 10); // Max zoom level of 5
            }

            double scale = zoom / previousZoom;

            Dimension newViewPortSize = new Dimension((int) (viewport.getSize().width * scale),
                    (int) (viewport.getSize().height * scale));

            Dimension viewPortSizeDifference = new Dimension(newViewPortSize.width - viewport.getSize().width,
                    newViewPortSize.height - viewport.getSize().height);

            double mouseRatioX = (mousePosition.x - viewport.getViewPosition().x) / (double)viewport.getSize().width;
            double mouseRatioY = (mousePosition.y - viewport.getViewPosition().y) / (double)viewport.getSize().height;
            viewPosition.x = (int) (viewPosition.x * zoom / previousZoom) + (int) (viewPortSizeDifference.width*mouseRatioX);//* mousePosition.x / viewport.getSize().width);
            viewPosition.y = (int) (viewPosition.y * zoom / previousZoom) + (int) (viewPortSizeDifference.height*mouseRatioY);// * mousePosition.y / viewport.getSize().height);
            if (scale < 1) {
                viewPosition.x = Math.max(0, Math.min(viewPosition.x, waterfall.getPreferredSize().width - newViewPortSize.width));
                viewPosition.y = Math.max(0, Math.min(viewPosition.y, waterfall.getPreferredSize().height - newViewPortSize.height));
                viewPosition.x = Math.min(viewPosition.x, waterfall.getPreferredSize().width - viewport.getSize().width);
                viewPosition.y = Math.min(viewPosition.y, waterfall.getPreferredSize().height - viewport.getSize().height);
            }
            waterfall.setZoom(zoom);
            viewport.setViewPosition(new Point(viewPosition.x, viewPosition.y));

            SwingUtilities.invokeLater(() -> {
                waterfall.revalidate();
                waterfall.repaint();
            });
        });

        waterfall.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (waterfallDragStart == null) {
                    waterfallDragStart = new Point2D.Double(e.getX(), e.getY());
                    return;
                }
                double dy = (e.getY() - waterfallDragStart.getY());
                double dx = (e.getX() - waterfallDragStart.getX());

                double newX = viewport.getViewPosition().x - dx;
                if (newX < 0)
                    newX = 0;

                double newY = viewport.getViewPosition().y - dy;
                if (newY < 0)
                    newY = 0;
                viewport.setViewPosition(new Point((int)newX, (int) newY));
                waterfall.revalidate();
                scrollToTime(waterfall.getTimestamp(new Point(0, (int)newY)), false);
                waterfallDragStart.setLocation(e.getX()-dx, e.getY()-dy);
            }
        });
    }

    public Date yToTimestamp(double y) {
        double relativePosition = (double) position / (getHeight());
        int extraHeight = scrollImage.getHeight() - getHeight();
        int startPixel = (int) (relativePosition * extraHeight);
        startPixel = Math.max(0, startPixel);
        y += startPixel;
        long startTime = getStartTime();
        long endTime = getEndTime();
        long time = (long) ((y / getHeight()) * (endTime - startTime) + startTime);
        return new Date(time);
    }

    public Instant getTime() {
        return yToTimestamp(position).toInstant();
    }

    public void scrollToTime(long timestamp, boolean update) {
        if (timestamp > getEndTime() || timestamp < getStartTime()) {
            log.info("Timestamp out of bounds: " + timestamp);
            return;
        }

        long startTime = getStartTime();
        long endTime = getEndTime();
        double fracIndex = (double) (timestamp - startTime) / (endTime - startTime);
        position = getHeight() - (fracIndex * getHeight());
        position -= scrollHeight * fracIndex;
        if (position < 0)
            position = 0;
        if (position > getHeight() - scrollHeight)
            position = getHeight() - scrollHeight;
        if (update)
            updateViewPort(position);
        repaint();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        position = (getHeight()-scrollHeight);
        position = Math.min(getHeight() - scrollHeight, position);
        position = Math.max(0, position);
        updateViewPort(position);
        repaint();
    }

    private void updateViewPort(double position) {
        double relativePosition = position / (getHeight()-scrollHeight);
        double y = ((viewport.getViewSize().getHeight()-viewport.getSize().getHeight()) * relativePosition);
        if (y > viewport.getViewSize().getHeight() - viewport.getSize().getHeight()) {
            y = (int) (viewport.getViewSize().getHeight() - viewport.getSize().getHeight());
        }
        viewport.setViewPosition(new Point(viewport.getViewPosition().x, (int)y));
        //viewport.revalidate();

        waterfall.repaint();
    }

    @Override
    public void paint(Graphics g) {
        double relativePosition = (double) position / (getHeight()-scrollHeight);
        int extraHeight = scrollImage.getHeight() - getHeight();
        int startPixel = (int) (relativePosition * extraHeight);
        startPixel = Math.max(0, startPixel);
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(scrollImage, 0, 0, getWidth(), getHeight(),
                0, startPixel, scrollImage.getWidth(), Math.min(scrollImage.getHeight(), startPixel+getHeight()),
                null);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, 1, getHeight());
        g2d.setColor(new Color(128, 64, 0));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(1, 1, scrollWidth-2, getHeight()-2);
        g2d.setStroke(new BasicStroke(1));
        g2d.setColor(new Color(255,200,64, 100));
        g2d.fill(new RoundRectangle2D.Double(3, position+1, getWidth() - 7, scrollHeight-2, 10, 10));
        g2d.setColor(new Color(255,160,0, 255));
        g2d.draw(new RoundRectangle2D.Double(3, position+1, getWidth() - 7, scrollHeight-2, 10, 10));
        g2d.setColor(new Color(28,28,28, 255));
        g2d.draw(new RoundRectangle2D.Double(2, position, getWidth() - 4, scrollHeight, 10, 10));
    }

    @Override
    public void replayStateChanged(Instant realTime, Instant replayTime, double speed) {
        scrollToTime(replayTime.toEpochMilli(), true);
    }

    @Override
    public void close() throws IOException {
        waterfall.close();
    }

    public static void main(String[] args) {
        RasterfallTiles waterfall = new RasterfallTiles(new File("/LOGS/REP/REP24/lauv-omst-2/20240913/075900_mwm-omst2/rasterIndex"), null);
        JPanel panel = new JPanel(new BorderLayout());
        JViewport viewport = new JViewport();
        viewport.setView(waterfall);
        panel.add(viewport, BorderLayout.CENTER);
        panel.add(new RasterfallScrollbar(waterfall, viewport), BorderLayout.EAST);
        JFrame frame = new JFrame("Timeline");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 500);
        frame.setContentPane(panel);
        // center on screen
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
