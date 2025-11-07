package pt.omst.rasterfall;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import lombok.extern.java.Log;
import pt.omst.rasterlib.Converter;
import pt.omst.rasterlib.IndexedRaster;
import pt.omst.rasterlib.IndexedRasterUtils;
import pt.omst.rasterlib.SampleDescription;

@Log
public class RasterfallSideView extends JPanel {

    private final ArrayList<SampleDescription> samples = new ArrayList<>();
    int width = 2000;
    int height = 80;

    private BufferedImage image = null;

    private double maxBathym = 0;

    public RasterfallSideView(File folder) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        IndexedRasterUtils.findRasterFiles(folder).forEach(index -> {
            try {
                IndexedRaster raster = Converter.IndexedRasterFromJsonString(Files.readString(index.toPath()));
                samples.addAll(raster.getSamples());
            } catch (IOException e) {
                log.warning("Error reading raster file: " + e.getMessage());
            }
        });
        samples.sort(Comparator.comparingLong(s -> s.getTimestamp().toInstant().toEpochMilli()));
        for (SampleDescription sample : samples) {
            if (sample.getPose().getDepth() + sample.getPose().getAltitude() > maxBathym)
                maxBathym = sample.getPose().getDepth() + sample.getPose().getAltitude();
        }
        IndexedRasterUtils.background(this::buildImage);
    }

    private void buildImage() {
        if (samples.size() < width)
            width = samples.size();
        height = (int) Math.round(maxBathym) + 1;
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double step = samples.size() / (double) width;
        for (double i = 0; i < samples.size(); i+=step) {
            int x = (int) Math.round(i/step);
            if (x >= width)
                break;
            int idx = (int)Math.round(i);
            SampleDescription sample = samples.get(idx);
            double depth = sample.getPose().getDepth();
            double bottom = depth + sample.getPose().getAltitude();
            g2.setColor(Color.black.darker());
            System.out.println("Drawing line at: " + x + " Depth: " + depth + " Bottom: " + bottom);
            g2.drawLine(x, 0, x, (int)Math.round(bottom));
            g2.setColor(new Color(90,60,30));
            g2.drawLine(x, (int)Math.round(bottom), x, height-1);
            g2.setColor(Color.orange);
            g2.draw(new Rectangle2D.Double(x-0.25, depth-0.25, 0.5, 0.5));
        }
        g2.dispose();
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (image != null)
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
    }

    public static void main(String[] args) {

        //IndexedRasterTimeline timeline = new IndexedRasterTimeline(new File("/LOGS/REP/REP24/lauv-omst-2/20240913/075900_mwm-omst2/rasterIndex"));
        RasterfallSideView sideView = new RasterfallSideView(new File("/LOGS/REP/REP24/lauv-omst-3/20240918/133113_mwk-omst3/rasterIndex"));
        JFrame frame = new JFrame("Side View");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setContentPane(sideView);

        // center on screen
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

}

