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
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import lombok.Getter;
import lombok.Setter;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.util.GuiUtils;
import pt.omst.rasterlib.Annotation;
import pt.omst.rasterlib.AnnotationType;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.MeasurementType;
import pt.omst.rasterlib.Observation;
import pt.omst.rasterlib.Pose;
import pt.omst.rasterlib.SampleDescription;

public class SidescanObservationPanel extends JPanel implements Closeable {

    @Getter
    private final Observation observation;
    @Getter
    private final IndexedRaster raster;
    @Getter
    private final BufferedImage image;
    @Getter
    private double widthMeters, heightMeters, headingDegrees;

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
            widthMeters = raster.getSensorInfo().getMaxRange() * 2;

            double worldHeight = 0;
            double headingSum = 0;
            for (int i = 1; i < raster.getSamples().size(); i++) {
                SampleDescription thisSample = raster.getSamples().get(i);
                SampleDescription lastSample = raster.getSamples().get(i-1);
                worldHeight += thisSample.getPose().getU() * (0.001 * (thisSample.getTimestamp().toInstant().toEpochMilli()
                        - lastSample.getTimestamp().toInstant().toEpochMilli()));
                headingSum += thisSample.getPose().getPsi();
            }
            heightMeters = Math.abs(worldHeight * 2);
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
        int index = (int) (raster.getSamples().size() * imageCoords.x);
        index = Math.min(index, raster.getSamples().size() - 1);
        index = Math.max(index, 0);
        SampleDescription sample = raster.getSamples().get(index);
        Pose pose = sample.getPose();
        double minX = raster.getSensorInfo().getMinRange();
        double maxX = raster.getSensorInfo().getMaxRange();
        double xOffset = minX + (maxX - minX) * imageCoords.x;
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
        System.out.println("MinX: " + minX);
        System.out.println("MaxX: " + maxX);
        return minX + (maxX - minX) * imageCoords.x;
    }

    public Double getShadowHeight(Point2D.Double imageCoords1, Point2D.Double imageCoords2) {
        Double slantRangeShadowStart = getSlantRange(imageCoords1);
        Double slantRangeShadowEnd = getSlantRange(imageCoords2);
        System.out.println(imageCoords1+" -> "+imageCoords2);
        System.out.println("SlantRangeShadowStart: " + slantRangeShadowStart);
        System.out.println("SlantRangeShadowEnd: " + slantRangeShadowEnd);
        int sampleIndex = (int) (raster.getSamples().size() * imageCoords1.y);
        sampleIndex = Math.min(sampleIndex, raster.getSamples().size() - 1);
        sampleIndex = Math.max(sampleIndex, 0);
        double altitude = raster.getSamples().get(sampleIndex).getPose().getAltitude();
        System.out.println("Altitude: " + altitude);
        double S = Math.sqrt(slantRangeShadowStart * slantRangeShadowStart - altitude * altitude) -
                Math.sqrt(slantRangeShadowEnd * slantRangeShadowEnd - altitude * altitude);
        S = Math.abs(S);
        System.out.println("S: " + S);
        double result = (altitude * S) / Math.sqrt(slantRangeShadowStart * slantRangeShadowStart - altitude * altitude);
        System.out.println("Result: " + result);
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
        }

        if (interaction != null)
            interaction.paint((Graphics2D)g.create());
        super.paintComponent(g);
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
                graphics2D.setColor(new Color(255,255,255,128));
                graphics2D.drawRect(minX, minY, width, height);
                break;
                
            case WIDTH:
                graphics2D.setColor(new Color(255,0,0,128));
                graphics2D.setStroke(new BasicStroke(2f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                break;
            case LENGTH:
                graphics2D.setColor(new Color(0,255,0,128));
                graphics2D.setStroke(new BasicStroke(2f));
                graphics2D.drawLine((int) screen1.x, (int) screen1.y, (int) screen2.x, (int) screen2.y);
                break;
            case HEIGHT:
                graphics2D.setColor(new Color(0,0,255,128));
                graphics2D.setStroke(new BasicStroke(2f));
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
