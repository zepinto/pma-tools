package pt.lsts.s57;

import java.awt.Graphics2D;
import java.io.File;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.painters.S57MapPainter;
import pt.omst.mapview.MapPainter;
import pt.omst.mapview.SlippyMap;

public class S57MapViewer {

    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        SwingUtilities.invokeLater(() -> {
            // Create the SlippyMap
            SlippyMap map = new SlippyMap();
            
            // Initialize S57 with resources folder
            // Working directory when running via Gradle is the module directory (S57/)
            File resourcesFolder = new File(".");
            File gdalFolder = new File("resources/gdal/linux/x64");
            S57 s57 = S57Factory.build(resourcesFolder, gdalFolder);
            
            // Create MarinerControls with default settings
            MarinerControls mc = MarinerControls.forge();
            
            // Create the S57MapPainter
            S57MapPainter s57Painter = S57MapPainter.forge(s57, mc);
            s57.addPainter(s57Painter);
            
            // Load S57 data from the specified folder
            File s57DataFolder = new File("/home/zp/Documents/S57-Portugal-20240808T141725Z-001/S57-Portugal");
            s57.loadFolders(List.of(s57DataFolder), new S57Listener() {
                @Override
                public void publishResult(Object progress) {
                    System.out.println("Loading S57 maps: " + progress + "%");
                }

                @Override
                public void setMessage(String message) {
                    System.out.println("S57: " + message);
                }
            });
            
            // Wrap S57MapPainter as a MapPainter for SlippyMap
            MapPainter s57MapPainterWrapper = new MapPainter() {
                @Override
                public void paint(Graphics2D g, SlippyMap slippyMap) {
                    s57Painter.paint(g, slippyMap);
                }

                @Override
                public String getName() {
                    return "S57 Charts";
                }

                @Override
                public int getLayerPriority() {
                    return -100; // Paint below other layers
                }
            };
            
            map.addRasterPainter(s57MapPainterWrapper);
            
            // Focus on Portugal
            map.focus(38.7, -9.1, 10);
            
            // Create and show the frame
            JFrame frame = new JFrame("S57 Map Viewer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 800);
            frame.add(map);
            frame.setVisible(true);
        });
    }
}