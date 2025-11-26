//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.core.LocationType;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SampleDescription;
import pt.omst.rasterlib.mapview.IndexedRasterViewer;

@Slf4j
public class SidescanObservationPanel extends JPanel implements Closeable {

    @Getter
    private final Observation observation;
    @Getter
    private final IndexedRaster raster;
    @Getter
    private final BufferedImage image;
    @Getter
    private double widthMeters, heightMeters, headingDegrees;
    @Getter
    private final File folder;

    @Getter @Setter
    private double zoomFactorX = 1.0, zoomFactorY = 1.0;
    @Getter @Setter
    private int offsetX = 0;
    @Getter @Setter
    private int offsetY = 0;
    @Getter @Setter
    private Point mouseDragPosition, mousePosition;

    protected final ArrayList<ContactChangeListener> changeListeners = new ArrayList<>();

    private final JToggleButton lengthButton = new JToggleButton("<html>&#x2B65;</html>");
    private final JToggleButton widthButton = new JToggleButton("<html>&#x2B64;</html>");
    private final JToggleButton heightButton = new JToggleButton("<html>&#x2912;</html>");
    private final JToggleButton zoomButton = new JToggleButton("<html>&#x1F50D;</html>");
    private final JToggleButton markButton = new JToggleButton("<html>&#x26F6;</html>");

    private AbstractInteraction<SidescanObservationPanel> interaction = null;

    private void addButtons() {
        ButtonGroup buttonGroup = new ButtonGroup();

        final MeasurementEditor mouseMarkEditor = new MeasurementEditor(this, MeasurementType.BOX, Color.YELLOW);
        final MeasurementEditor mouseWidthEditor = new MeasurementEditor(this, MeasurementType.WIDTH, Color.RED);
        final MeasurementEditor mouseLengthEditor = new MeasurementEditor(this, MeasurementType.LENGTH, Color.GREEN);
        final MeasurementEditor mouseHeightEditor = new MeasurementEditor(this, MeasurementType.HEIGHT, Color.BLUE);
        
        final MouseZoomAndDrag mouseZoomAndDrag = new MouseZoomAndDrag(this);

        lengthButton.setPreferredSize(new Dimension(25, 25));
        lengthButton.setMargin(new Insets(0, 0, 0, 0));
        lengthButton.addActionListener(e -> {
            if (lengthButton.isSelected())
                setInteraction(mouseLengthEditor);
            repaint();
            requestFocusInWindow();
        });
        lengthButton.setToolTipText("<html><u>L</u>ength measurement");
        add(lengthButton);
        buttonGroup.add(lengthButton);

        widthButton.setPreferredSize(new Dimension(25, 25));
        widthButton.addActionListener(e -> {
            if (widthButton.isSelected())
                setInteraction(mouseWidthEditor);
            repaint();
            requestFocusInWindow();
        });
        widthButton.setMargin(new Insets(0, 0, 0, 0));
        widthButton.setToolTipText("<html><u>W</u>idth measurement");
        add(widthButton);
        buttonGroup.add(widthButton);

        heightButton.setPreferredSize(new Dimension(25, 25));
        heightButton.addActionListener(e -> {
            if (heightButton.isSelected())
                setInteraction(mouseHeightEditor);
            repaint();
            requestFocusInWindow();
        });
        heightButton.setMargin(new Insets(0, 0, 0, 0));
        heightButton.setToolTipText("<html><u>H</u>eight measurement");
        add(heightButton);
        buttonGroup.add(heightButton);

        markButton.setPreferredSize(new Dimension(25, 25));
        markButton.addActionListener(e -> {
            if (markButton.isSelected())
                setInteraction(mouseMarkEditor);
            repaint();
            requestFocusInWindow();
        });
        markButton.setMargin(new Insets(0, 0, 0, 0));
        markButton.setToolTipText("<html><u>E</u>dit Marker</html>");
        add(markButton);
        buttonGroup.add(markButton);

        zoomButton.setPreferredSize(new Dimension(25, 25));
        zoomButton.addActionListener(e -> {
            if (zoomButton.isSelected())
                setInteraction(mouseZoomAndDrag);
            repaint();
            requestFocusInWindow();
        });
        zoomButton.setMargin(new Insets(0, 0, 0, 0));
        zoomButton.setToolTipText("<html><u>Z</u>oom and Pan");
        add(zoomButton);
        buttonGroup.add(zoomButton);
    }

    public SidescanObservationPanel(File folder, Observation observation) {
        this.folder = folder;
        this.observation = observation;
        if (observation.getRasterFilename() == null) {
            throw new RuntimeException("Observation does not have a raster file");
        }
        setBackground(Color.GRAY.darker().darker());
        setLayout(null);
        setOpaque(false);
        addButtons();
        zoomButton.doClick();

        File rasterFile = new File(folder, observation.getRasterFilename());
        try {
            raster = Converter.IndexedRasterFromJsonString(Files.readString(rasterFile.toPath()));
            image = ImageIO.read(new File(folder, raster.getFilename()));
            widthMeters = raster.getSensorInfo().getMaxRange() - raster.getSensorInfo().getMinRange();
            log.info("Calculated widthMeters: " + widthMeters);
            double worldHeight = 0;
            double headingSum = 0;
            for (int i = 1; i < raster.getSamples().size(); i++) {
                SampleDescription thisSample = raster.getSamples().get(i);
                SampleDescription lastSample = raster.getSamples().get(i-1);
                worldHeight += thisSample.getPose().getU() * (0.001 * (thisSample.getTimestamp().toInstant().toEpochMilli()
                        - lastSample.getTimestamp().toInstant().toEpochMilli()));
                headingSum += thisSample.getPose().getPsi();
            }
            heightMeters = Math.abs(worldHeight);
            log.info("Calculated heightMeters: " + heightMeters);
            headingDegrees = headingSum / raster.getSamples().size();
        }
        catch (Exception e) {
            throw new RuntimeException("Error reading raster", e);
        }

        setInteraction(new MouseZoomAndDrag(this));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                
                // Show context menu on right-click
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> offsetX -= 10;
                    case KeyEvent.VK_RIGHT -> offsetX += 10;
                    case KeyEvent.VK_UP -> offsetY -= 10;
                    case KeyEvent.VK_DOWN -> offsetY += 10;
                    case KeyEvent.VK_L -> lengthButton.doClick();
                    case KeyEvent.VK_W -> widthButton.doClick();
                    case KeyEvent.VK_H -> heightButton.doClick();
                    case KeyEvent.VK_E -> markButton.doClick();
                    case KeyEvent.VK_Z -> zoomButton.doClick();
                    case KeyEvent.VK_PLUS, KeyEvent.VK_PAGE_UP, KeyEvent.VK_ADD -> {
                        zoomFactorY += 0.1;
                        zoomFactorX += 0.1;
                    }
                    case KeyEvent.VK_MINUS, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_SUBTRACT -> {
                        zoomFactorY -= 0.1;
                        zoomFactorX -= 0.1;
                        zoomFactorX = Math.max(0.1, zoomFactorX);
                        zoomFactorY = Math.max(0.1, zoomFactorY);
                    }
                }
                repaint();
            }
        });
    }

    public void setHeightMeters(double heightMeters) {
        this.heightMeters = heightMeters;
    }
    public void setWidthMeters(double widthMeters) {
        this.widthMeters = widthMeters;
    }

    @Override
    public void doLayout() {
        super.doLayout();
        heightButton.setBounds(3, getHeight()-1-25, 25, 25);
        widthButton.setBounds(3, getHeight()-2-50, 25, 25);
        lengthButton.setBounds(3, getHeight()-3-75, 25, 25);
        markButton.setBounds(3, getHeight()-4-100, 25, 25);
        zoomButton.setBounds(3, getHeight()-5-125, 25, 25);
    }

    Point2D.Double screenToImageCoords(Point2D.Double screenCoords) {
        double heightProportion = heightMeters / widthMeters;
        double zoomX = zoomFactorX;
        double zoomY = zoomFactorY * heightProportion;
        int newWidth = (int) (image.getWidth() * zoomX);
        int newHeight = (int) (image.getWidth() * zoomY);

        int x = (getWidth() - newWidth) / 2 + offsetX;
        int y = (getHeight() - newHeight) / 2 + offsetY;

        double imgX = ((screenCoords.x - x) / zoomX);
        double imgY = ((screenCoords.y - y) / zoomY);

        imgX /= image.getWidth();
        imgY /= image.getWidth();

        return new Point2D.Double(imgX, imgY);
    }

    Point2D.Double imageToScreenCoords(Point2D.Double imageCoords) {
        double heightProportion = heightMeters / widthMeters;
        double zoom = zoomFactorX;
        double zoomY = zoomFactorY * heightProportion;
        int newWidth = (int) (image.getWidth() * zoom);
        int newHeight = (int) (image.getWidth() * zoomY);
        int x = (getWidth() - newWidth) / 2 + offsetX;
        int y = (getHeight() - newHeight) / 2 + offsetY;
        double screenX = imageCoords.x * image.getWidth() * zoom + x;
        double screenY = imageCoords.y * image.getWidth() * heightProportion * zoom + y;
        return new Point2D.Double(screenX, screenY);
    }

    Point2D.Double imageToWorldCoords(Point2D.Double imageCoords) {
        int index = (int) (raster.getSamples().size() * imageCoords.y);
        index = Math.min(index, raster.getSamples().size() - 1);
        index = Math.max(index, 0);
        SampleDescription sample = raster.getSamples().get(index);
        Pose pose = sample.getPose();
        double minX = raster.getSensorInfo().getMinRange();
        double maxX = raster.getSensorInfo().getMaxRange();
        double slantRange = minX + (maxX - minX) * imageCoords.x;
        
        // Apply slant range correction: ground range = sqrt(slant_range² - altitude²)
        double altitude = pose.getAltitude();
        double xOffset;
        if (Math.abs(slantRange) > altitude) {
            xOffset = Math.signum(slantRange) * Math.sqrt(slantRange * slantRange - altitude * altitude);
        } else {
            // If slant range is less than altitude (nadir zone), ground range is near zero
            xOffset = 0;
        }
        
        LocationType loc = new LocationType(pose.getLatitude(), pose.getLongitude());
        loc.setOffsetDistance(xOffset);
        if (xOffset < 0) {
            loc.setAzimuth(Math.PI / 2 + Math.toRadians(pose.getPsi()));
        }
        else {
            loc.setAzimuth(Math.PI / 2 - Math.toRadians(pose.getPsi()));
        }
        loc.convertToAbsoluteLatLonDepth();
        return new Point2D.Double(loc.getLatitudeDegs(), loc.getLongitudeDegs());
    }

    private Double getSlantRange(Point2D.Double imageCoords) {
        double minX = raster.getSensorInfo().getMinRange();
        double maxX = raster.getSensorInfo().getMaxRange();
        return minX + (maxX - minX) * imageCoords.x;
    }

    public Double getShadowHeight(Point2D.Double imageCoords1, Point2D.Double imageCoords2) {
        Double slantRangeShadowStart = getSlantRange(imageCoords1);
        Double slantRangeShadowEnd = getSlantRange(imageCoords2);
        int sampleIndex = (int) (raster.getSamples().size() * imageCoords1.y);
        sampleIndex = Math.min(sampleIndex, raster.getSamples().size() - 1);
        sampleIndex = Math.max(sampleIndex, 0);
        double altitude = raster.getSamples().get(sampleIndex).getPose().getAltitude();
        double S = Math.sqrt(slantRangeShadowStart * slantRangeShadowStart - altitude * altitude) -
                Math.sqrt(slantRangeShadowEnd * slantRangeShadowEnd - altitude * altitude);
        S = Math.abs(S);
        double result = (altitude * S) / Math.sqrt(slantRangeShadowStart * slantRangeShadowStart - altitude * altitude);
        return result;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        double heightProportion = heightMeters / widthMeters;

        if (image != null) {
            Graphics2D g2d = (Graphics2D) g.create();
            if (GuiUtils.isDarkTheme())
                g2d.setColor(Color.BLACK);
            else
                g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            // Enable smooth scaling
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Calculate the position and size of the image based on zoom and pan
            double zoom = zoomFactorX;
            double zoomY = zoomFactorY * heightProportion;
            int newWidth = (int) (image.getWidth() * zoom);
            int newHeight = (int) (image.getWidth() * zoomY);
            int x = (getWidth() - newWidth) / 2 + offsetX;
            int y = (getHeight() - newHeight) / 2 + offsetY;

            g2d.drawImage(image, x, y, newWidth, newHeight, null);

            for (Annotation a : observation.getAnnotations())
                if (a.getAnnotationType() == AnnotationType.MEASUREMENT)
                    drawMeasurement(a, g2d);

            // Draw north arrow
            g2d.setColor(Color.WHITE);
            g2d.translate(getWidth()-25, 25);
            g2d.rotate(-Math.toRadians(headingDegrees));
            GeneralPath gp = new GeneralPath();
            gp.moveTo(0, -15);
            gp.lineTo(-8, 10);
            gp.lineTo(0, 7);
            gp.lineTo(8, 10);
            gp.closePath();
            g2d.setColor(Color.gray);
            g2d.fill(gp);

            g2d.setColor(Color.BLACK);
            g2d.draw(gp);
            g2d.setFont(new Font("Arial", Font.BOLD, 9));
            Rectangle2D nBounds = g.getFontMetrics().getStringBounds("N", g);
            g2d.setColor(Color.BLACK);
            g2d.drawString("N", -(int) nBounds.getWidth() / 2+1, 5);
            
            // Reset transform for scale bar
            g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw scale bar below north arrow
            drawScaleBar(g2d);
        }

        if (interaction != null)
            interaction.paint((Graphics2D)g.create());
        super.paintComponent(g);
    }
    
    private void drawScaleBar(Graphics2D g2d) {
        // Determine appropriate scale bar length in meters
        double[] scaleOptions = {0.5, 1, 2, 5, 10, 20, 50, 100, 200, 500};
        double targetWidthPixels = 80; // Target scale bar width in pixels
        double metersPerPixel = widthMeters / (image.getWidth() * zoomFactorX);
        double targetMeters = targetWidthPixels * metersPerPixel;
        
        // Find the closest nice number
        double scaleMeters = scaleOptions[0];
        for (double option : scaleOptions) {
            if (option <= targetMeters * 1.5) {
                scaleMeters = option;
            }
        }
        
        // Calculate actual pixel width for this scale
        int scaleBarWidth = (int) (scaleMeters / metersPerPixel);
        
        // Format scale text
        String scaleText = scaleMeters < 1 ? 
            String.format("%.1f m", scaleMeters) : 
            String.format("%d m", (int) scaleMeters);
        
        // Set font and measure text
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        Rectangle2D textBounds = g2d.getFontMetrics().getStringBounds(scaleText, g2d);
        
        // Calculate total width needed (text + padding + bar)
        int totalWidth = Math.max(scaleBarWidth, (int) textBounds.getWidth()) + 10;
        
        // Position scale bar in bottom right, ensuring it stays within bounds
        int margin = 10;
        int scaleBarX = Math.max(margin, getWidth() - totalWidth - margin);
        int scaleBarY = getHeight() - 35;
        
        // Adjust bar width and position if still too wide
        if (scaleBarX < margin) {
            scaleBarX = margin;
            scaleBarWidth = Math.min(scaleBarWidth, getWidth() - 2 * margin - 10);
        }
        
        int textX = scaleBarX + (scaleBarWidth - (int) textBounds.getWidth()) / 2;
        
        // Draw translucent dark shadow for visibility
        g2d.setColor(new Color(0, 0, 0, 128));
        g2d.setStroke(new BasicStroke(4f));
        g2d.drawLine(scaleBarX, scaleBarY + 10, scaleBarX + scaleBarWidth, scaleBarY + 10);
        g2d.drawLine(scaleBarX, scaleBarY + 5, scaleBarX, scaleBarY + 15);
        g2d.drawLine(scaleBarX + scaleBarWidth, scaleBarY + 5, scaleBarX + scaleBarWidth, scaleBarY + 15);
        g2d.drawString(scaleText, textX + 1, scaleBarY + 4);
        
        // Draw scale bar line (same color as coordinates)
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawLine(scaleBarX, scaleBarY + 10, scaleBarX + scaleBarWidth, scaleBarY + 10);
        
        // Draw tick marks
        g2d.drawLine(scaleBarX, scaleBarY + 5, scaleBarX, scaleBarY + 15);
        g2d.drawLine(scaleBarX + scaleBarWidth, scaleBarY + 5, scaleBarX + scaleBarWidth, scaleBarY + 15);
        
        // Draw scale label centered above the bar
        g2d.drawString(scaleText, textX, scaleBarY + 3);
    }

    void drawMeasurement(Annotation annotation, Graphics2D graphics2D) {
        Point2D.Double pt1 = new Point2D.Double(annotation.getNormalizedX(), annotation.getNormalizedY());
        Point2D.Double pt2 = new Point2D.Double(annotation.getNormalizedX2(), annotation.getNormalizedY2());
        Point2D.Double screen1 = imageToScreenCoords(pt1);
        Point2D.Double screen2 = imageToScreenCoords(pt2);

        switch (annotation.getMeasurementType()) {
            case BOX:
                int minX = (int) Math.min(screen1.x, screen2.x);
                int minY = (int) Math.min(screen1.y, screen2.y);
                int width = (int) Math.abs(screen1.x - screen2.x);
                int height = (int) Math.abs(screen1.y - screen2.y);
                
                // Draw shadow
                graphics2D.setColor(new Color(0, 0, 0, 128));
                graphics2D.setStroke(new BasicStroke(4f));
                graphics2D.drawRect(minX, minY, width, height);
                
                // Draw box
                graphics2D.setColor(new Color(255, 255, 255, 200));
                graphics2D.setStroke(new BasicStroke(2f));
                graphics2D.drawRect(minX, minY, width, height);
                break;
                
            case WIDTH:
                // Draw shadow
                graphics2D.setColor(new Color(0, 0, 0, 128));
                graphics2D.setStroke(new BasicStroke(5f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                
                // Draw line
                graphics2D.setColor(new Color(255, 0, 0, 200));
                graphics2D.setStroke(new BasicStroke(3f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                break;
            case LENGTH:
                // Draw shadow
                graphics2D.setColor(new Color(0, 0, 0, 128));
                graphics2D.setStroke(new BasicStroke(5f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                
                // Draw line
                graphics2D.setColor(new Color(0, 255, 0, 200));
                graphics2D.setStroke(new BasicStroke(3f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                break;
            case HEIGHT:
                // Draw shadow
                graphics2D.setColor(new Color(0, 0, 0, 128));
                graphics2D.setStroke(new BasicStroke(5f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                
                // Draw line
                graphics2D.setColor(new Color(0, 0, 255, 200));
                graphics2D.setStroke(new BasicStroke(3f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                break;
            case SIZE:
                break;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return image != null ? new Dimension(image.getWidth(), image.getHeight()) : super.getPreferredSize();
    }

    public void addChangeListener(ContactChangeListener listener) {
        if (!changeListeners.contains(listener))
            changeListeners.add(listener);
    }

    public void removeObservationChangeListener(ContactChangeListener listener) {
        changeListeners.remove(listener);
    }

    public void fireObservationChanged() {
        for (ContactChangeListener listener : changeListeners)
            listener.observationChanged(observation);
    }

    public void fireObservationDeleted() {
        for (ContactChangeListener listener : changeListeners)
            listener.observationDeleted(observation);
    }

    public void setInteraction(AbstractInteraction<SidescanObservationPanel> interaction) {
        if (this.interaction != null) {
            removeMouseListener(this.interaction);
            removeMouseMotionListener(this.interaction);
            removeMouseWheelListener(this.interaction);
        }
        addMouseListener(interaction);
        addMouseMotionListener(interaction);
        addMouseWheelListener(interaction);
        addKeyListener(interaction);
        this.interaction = interaction;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        requestFocusInWindow();
    }

    /**
     * Shows a context menu with option to view sample in IndexedRasterViewer.
     */
    private void showContextMenu(MouseEvent e) {
        JPopupMenu contextMenu = new JPopupMenu();
        
        JMenuItem viewSampleItem = new JMenuItem("View Sample with IndexedRasterViewer");
        viewSampleItem.addActionListener(evt -> openIndexedRasterViewer());
        
        contextMenu.add(viewSampleItem);
        contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    /**
     * Opens the IndexedRasterViewer in a new window.
     */
    private void openIndexedRasterViewer() {
        try {
            // Create the viewer
            IndexedRasterViewer viewer = new IndexedRasterViewer(raster, image);
            
            // Create frame
            JFrame frame = new JFrame("IndexedRaster Viewer - " + observation.getRasterFilename());
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1200, 800);
            frame.setContentPane(viewer);
            frame.setLocationRelativeTo(this);
            frame.setVisible(true);
            
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(this,
                "Error opening IndexedRaster Viewer: " + ex.getMessage(),
                "Error",
                javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void close() throws IOException {
        if (interaction != null) {
            removeKeyListener(interaction);
            removeMouseListener(interaction);
            removeMouseMotionListener(interaction);
            removeMouseWheelListener(interaction);
        }
        interaction = null;
    }
}
