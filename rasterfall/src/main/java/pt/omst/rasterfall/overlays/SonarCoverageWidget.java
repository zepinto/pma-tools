//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: Jose Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SensorInfo;

/**
 * A Swing widget that visualizes the vehicle's roll, altitude, and sidescan
 * sonar coverage.
 */
public class SonarCoverageWidget extends JPanel {

    private Pose pose;
    private SensorInfo sensorInfo;
    private BufferedImage vehicleBackImage;
    // Constants for visualization
    private static final double BEAM_WIDTH_FACTOR = 10.0; // Coverage is 10x altitude
    private static final Color WATER_COLOR = new Color(135, 206, 235);
    private static final Color BOTTOM_COLOR = new Color(139, 69, 19);
    private static final Color BEAM_COLOR = new Color(255, 255, 0, 100); // Semi-transparent yellow
    private static final Color NADIR_COLOR = Color.RED;

    public SonarCoverageWidget() {
        loadImages();
        setPreferredSize(new Dimension(400, 400));
        setBackground(WATER_COLOR);
    }

    private void loadImages() {
        try {
            URL backUrl = getClass().getClassLoader().getResource("images/lauv-back.png");
            if (backUrl != null) {
                vehicleBackImage = ImageIO.read(backUrl);
            } else {
                System.err.println("Could not find lauv-back.png");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setPose(Pose pose) {
        this.pose = pose;
    }

    public void setSensorInfo(SensorInfo sensorInfo) {
        this.sensorInfo = sensorInfo;
    }

    private static final Color NADIR_GAP_COLOR = new Color(255, 165, 0, 150); // Orange
    private static final Color SURFACE_COLOR = new Color(0, 0, 139); // Dark Blue

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable high-quality rendering and antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (pose == null) {
            g2d.setColor(Color.BLACK);
            g2d.drawString("No Pose Data", getWidth() / 2 - 40, getHeight() / 2);
            return;
        }

        double altitude = pose.getAltitude() != null ? pose.getAltitude() : 0.0;
        double depth = pose.getDepth() != null ? pose.getDepth() : 0.0;
        double roll = pose.getPhi() != null ? pose.getPhi() : 0.0;
        double rollRad = Math.toRadians(roll); // Convert to radians for calculations

        // Coordinate system setup
        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;

        // Determine the maximum range we need to display
        double maxRange = 50.0; // Default
        if (sensorInfo != null && sensorInfo.getMaxRange() != null) {
            maxRange = sensorInfo.getMaxRange();
        } else {
            // Estimate based on altitude (10x coverage)
            maxRange = altitude * 10;
        }

        // Calculate the total extent we need to fit
        // Horizontally: maxRange on each side
        // Vertically: depth (to surface) + maxRange (for the beam length)
        double horizontalExtent = maxRange * 2; // Port + Starboard
        double verticalExtent = depth + maxRange;

        // Calculate scale to fit within widget bounds (with margins)
        double horizontalScale = (width * 0.9) / horizontalExtent;
        double verticalScale = (height * 0.9) / verticalExtent;

        // Use the smaller scale to ensure everything fits
        double pixelsPerMeter = Math.min(horizontalScale, verticalScale);

        // Position vehicle in upper portion to leave room for beams below
        int centerY = (int) (depth * pixelsPerMeter) + (int) (height * 0.15);

        // Draw Surface
        int surfaceY = centerY - (int) (depth * pixelsPerMeter);
        // Only draw if within bounds (visible on screen)
        if (surfaceY >= 0) {
            g2d.setColor(SURFACE_COLOR);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(0, surfaceY, width, surfaceY);
            // Surface text removed as requested
        }

        // Draw Bottom
        int bottomY = centerY + (int) (altitude * pixelsPerMeter);
        g2d.setColor(BOTTOM_COLOR);
        g2d.fillRect(0, bottomY, width, height - bottomY);

        // Draw Sonar Beams
        double halfBeamAngleRad;
        double beamLength;

        if (sensorInfo != null && sensorInfo.getMaxRange() != null && sensorInfo.getMaxRange() > altitude) {
            // Calculate angle based on max range and altitude
            // cos(theta) = altitude / maxRange
            halfBeamAngleRad = Math.acos(altitude / sensorInfo.getMaxRange());
            beamLength = sensorInfo.getMaxRange() * pixelsPerMeter;
        } else {
            // Default to 10x altitude per side (approx 84.3 degrees)
            halfBeamAngleRad = Math.atan(10.0);
            // Length = altitude / cos(theta)
            beamLength = (altitude / Math.cos(halfBeamAngleRad)) * pixelsPerMeter;
        }

        double halfBeamAngleDeg = Math.toDegrees(halfBeamAngleRad);

        // Nadir Gap Angle (e.g., 45 degrees total -> 22.5 per side)
        double nadirGapAngleDeg = 45.0;
        double halfNadirGapDeg = nadirGapAngleDeg / 2.0;

        AffineTransform oldTransform = g2d.getTransform();
        g2d.translate(centerX, centerY);
        g2d.rotate(rollRad);

        // Set clipping to keep arcs within widget bounds
        // Create a clip rectangle in the rotated coordinate system
        // g2d.setClip(-centerX, -centerY, width, height);

        // Ensure minimum length for visibility
        if (beamLength < 40)
            beamLength = 40;

        // Draw Beams (Yellow)
        g2d.setColor(BEAM_COLOR);

        // Starboard (Right side, CCW from 270)
        // Start at 270 + halfNadirGap? No, 270 is Down.
        // Starboard is towards 0 (Right). So 270 -> 360.
        // Gap is centered at 270.
        // Starboard Beam starts at 270 + 22.5? No, 270 is 6 o'clock.
        // CCW is positive. 270 + 22.5 = 292.5 (Towards Right).
        // Extent: (halfBeamAngle - halfNadirGap)?
        // Total coverage is +/- 78.7.
        // So Starboard goes from 22.5 to 78.7 (relative to nadir).
        // Start Angle: 270 + 22.5 = 292.5.
        // Extent: -(78.7 - 22.5) = -56.2 (CW towards Right? No wait).
        // Arc2D: Start Angle, Extent.
        // Starboard: We want to fill from (270 - 22.5) to (270 - 78.7)? No, Starboard
        // is Right.
        // 0 is Right. 90 is Down (Screen).
        // Java Arc: 0 is Right. 90 is Up. 270 is Down.
        // Starboard (Right of Nadir): Angle < 270. e.g. 260, 250... No, that's CW.
        // 270 is Down. 0 is Right.
        // So Starboard is 270 -> 360. (CCW).
        // Wait, 270 + 10 = 280. That's towards Right.
        // So Starboard is [270, 360].
        // Nadir Gap is [270 - 22.5, 270 + 22.5].
        // Starboard Beam: Starts at 270 + 22.5. Extent: (78.7 - 22.5) = 56.2 (CCW).
        // Port Beam: Starts at 270 - 22.5. Extent: -(78.7 - 22.5) = -56.2 (CW).

        // Starboard Beam (Right)
        // Start: 270 + halfNadirGapDeg. Extent: (halfBeamAngleDeg - halfNadirGapDeg).
        // Note: halfBeamAngleDeg is ~78.7. halfNadirGapDeg is 22.5.
        // Extent is positive (CCW).
        g2d.fill(new Arc2D.Double(-beamLength, -beamLength, beamLength * 2, beamLength * 2,
                270 + halfNadirGapDeg, halfBeamAngleDeg - halfNadirGapDeg, Arc2D.PIE));

        // Port Beam (Left)
        // Start: 270 - halfNadirGapDeg. Extent: -(halfBeamAngleDeg - halfNadirGapDeg).
        // Extent is negative (CW).
        g2d.fill(new Arc2D.Double(-beamLength, -beamLength, beamLength * 2, beamLength * 2,
                270 - halfNadirGapDeg, -(halfBeamAngleDeg - halfNadirGapDeg), Arc2D.PIE));

        // Draw Nadir Gap (Orange)
        g2d.setColor(NADIR_GAP_COLOR);
        // Gap from 270 - 22.5 to 270 + 22.5.
        // Start: 270 - 22.5. Extent: 45.
        g2d.fill(new Arc2D.Double(-beamLength, -beamLength, beamLength * 2, beamLength * 2,
                270 - halfNadirGapDeg, nadirGapAngleDeg, Arc2D.PIE));

        // Draw Nadir Line (Sensor Frame) - extending downwards
        g2d.setColor(NADIR_COLOR);
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 9 }, 0));
        g2d.draw(new Line2D.Double(0, 0, 0, beamLength));

        // Draw Vehicle
        if (vehicleBackImage != null) {
            double vehicleScale = 0.125;
            int vW = (int) (vehicleBackImage.getWidth() * vehicleScale);
            int vH = (int) (vehicleBackImage.getHeight() * vehicleScale);
            g2d.drawImage(vehicleBackImage, -vW / 2, -vH / 2, vW, vH, null);
        } else {
            g2d.setColor(Color.YELLOW);
            g2d.fillOval(-5, -5, 10, 10);
        }

        g2d.setClip(null); // Remove clipping
        g2d.setTransform(oldTransform);

        // Draw Ground Coverage Strip (only outside nadir gap)
        g2d.setColor(new Color(0, 255, 0, 150));
        g2d.setStroke(new BasicStroke(4));

        // Calculate nadir gap edges
        double nadirGapAngleRad = Math.toRadians(nadirGapAngleDeg);
        double angleStbdGap = rollRad + nadirGapAngleRad / 2;
        double anglePortGap = rollRad - nadirGapAngleRad / 2;

        double angleStbd = rollRad - halfBeamAngleRad;
        double anglePort = rollRad + halfBeamAngleRad;

        double stbdX = getIntersectionX(centerX, centerY, bottomY, angleStbd);
        double portX = getIntersectionX(centerX, centerY, bottomY, anglePort);
        double stbdGapX = getIntersectionX(centerX, centerY, bottomY, angleStbdGap);
        double portGapX = getIntersectionX(centerX, centerY, bottomY, anglePortGap);

        // Draw port side (left) coverage line - from port beam edge to port gap edge
        if (!Double.isNaN(portX) && !Double.isNaN(portGapX)) {
            // Clip to widget bounds
            int x1 = Math.max(0, Math.min(width, (int) portX));
            int x2 = Math.max(0, Math.min(width, (int) portGapX));
            if (x1 != x2) { // Only draw if there's something to draw
                g2d.drawLine(x1, bottomY, x2, bottomY);
            }
        }

        // Draw starboard side (right) coverage line - from starboard gap edge to
        // starboard beam edge
        if (!Double.isNaN(stbdGapX) && !Double.isNaN(stbdX)) {
            // Clip to widget bounds
            int x1 = Math.max(0, Math.min(width, (int) stbdGapX));
            int x2 = Math.max(0, Math.min(width, (int) stbdX));
            if (x1 != x2) { // Only draw if there's something to draw
                g2d.drawLine(x1, bottomY, x2, bottomY);
            }
        }

        // Draw Nadir intersection
        double nadirX = getIntersectionX(centerX, centerY, bottomY, rollRad);
        if (!Double.isNaN(nadirX)) {
            g2d.setColor(Color.RED);
            g2d.fillOval((int) nadirX - 3, bottomY - 3, 6, 6);
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2d.drawString("Nadir", (int) nadirX - 10, bottomY + 15);
        }

        // Draw Roll and Altitude text
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.drawString(String.format("Roll: %.1f°", roll), 10, 20);
        g2d.drawString(String.format("Alt: %.1f m", altitude), 10, 40);
    }

    private double getIntersectionX(int cx, int cy, int bottomY, double angleRad) {
        // angleRad is 0 for straight down.
        // In Java 2D rotation:
        // +Y is Down.
        // Rotation is Clockwise.
        // If we rotate by +angle, the +Y axis moves to the LEFT.
        // So the vector is (-sin(angle), cos(angle)).

        double dy = Math.cos(angleRad);
        if (dy <= 0.001)
            return Double.NaN; // Pointing up or horizontal

        double dx = -Math.sin(angleRad);
        double t = (bottomY - cy) / dy;
        return cx + t * dx;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Sonar Coverage Widget Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        SonarCoverageWidget widget = new SonarCoverageWidget();
        frame.add(widget);
        frame.pack();
        frame.setVisible(true);

        // Simulate data
        new Thread(() -> {
            Pose p = new Pose();
            p.setAltitude(10.0);
            p.setPhi(0.0);

            double t = 0;
            while (true) {
                try {
                    Thread.sleep(50);
                    t += 0.05;
                    p.setPhi(Math.sin(t) * 0.5); // Roll +/- ~28 degrees
                    p.setAltitude(10.0 + Math.sin(t * 0.3) * 2.0);
                    widget.setPose(p);
                    widget.repaint();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
