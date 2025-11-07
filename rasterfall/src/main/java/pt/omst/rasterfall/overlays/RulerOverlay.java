package pt.omst.rasterfall.overlays;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.JLayer;

import pt.omst.rasterfall.RasterfallTiles;

public class RulerOverlay extends AbstractOverlay {

    private double range;
    private BufferedImage ruler = null;
    private final Point2D lastPoint = new Point2D.Double();
    private RasterfallTiles waterfall;

    @Override
    public void cleanup(RasterfallTiles waterfall) {
        // TODO Auto-generated method stub
    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        range = waterfall.getRasters().getFirst().getSensorInfo().getMaxRange();
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        super.processMouseMotionEvent(e, l);

        if (e.getID() == MouseEvent.MOUSE_MOVED) {
            lastPoint.setLocation(e.getPoint());
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        if (ruler == null || ruler.getWidth() != waterfall.getWidth()) {
            ruler = generateRuler(range, waterfall.getWidth(), 22);
        }
        g.drawImage(ruler, 0, waterfall.getVisibleRect().y, null);
    }


    private static BufferedImage generateRuler(double rangeInMeters, int width, int height) {
        // Create a transparent image
        BufferedImage rulerImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rulerImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Set up translucent background
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        g2d.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 255), 0, height, new Color(0, 0, 0, 0)));
        //g2d.setColor(new Color(90, 50, 0, 180)); // Semi-transparent black
        g2d.fillRect(0, 0, width, height);

        // Set up drawing styles
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(1));
        g2d.setFont(new Font("Helvetica", Font.BOLD, 12));

        // Calculate spacing and scaling
        int spacing = 5;//calculateSpacing(rangeInMeters); // Dynamic spacing for readability
        double pixelsPerMeter = (double) width / (2 * rangeInMeters);
        // Draw centerline and label for "0"
        g2d.setColor(Color.RED);
        int centerX = width / 2;
        g2d.drawLine(centerX, height -10, centerX, height); // Center tick
        g2d.drawString("0", centerX - g2d.getFontMetrics().stringWidth("0") / 2, 12);

        // Draw ticks and labels
        g2d.setColor(Color.WHITE);
        for (int r = spacing; r <= rangeInMeters; r += spacing) {
            int pos1 = (int) (centerX + r * pixelsPerMeter);
            int pos2 = (int) (centerX - r * pixelsPerMeter);

            // Draw label above the line
            String label = Integer.toString(r);
            g2d.drawString(label, pos1 - g2d.getFontMetrics().stringWidth(label) / 2, 12);
            g2d.drawString(label, pos2 - g2d.getFontMetrics().stringWidth(label) / 2, 12);
            // Draw tick mark below the line
            g2d.drawLine(pos1, height -7, pos1, height);
            g2d.drawLine(pos2, height -7, pos2, height);
        }

        g2d.dispose();
        return rulerImage;
    }
}
