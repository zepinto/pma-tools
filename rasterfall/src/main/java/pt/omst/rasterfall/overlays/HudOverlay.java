package pt.omst.rasterfall.overlays;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import pt.omst.neptus.sidescan.SidescanParser;
import pt.omst.neptus.sidescan.SidescanParserFactory;
import pt.omst.rasterfall.MraVehiclePosHud;
import pt.omst.rasterfall.RasterfallTiles;

public class HudOverlay extends AbstractOverlay {

    private RasterfallTiles waterfall;
    private MraVehiclePosHud hud = null;
    @Override
    public void cleanup(RasterfallTiles waterfall) {

    }

    @Override
    public void install(RasterfallTiles waterfall) {
        this.waterfall = waterfall;
        createHud();
    }

    private synchronized void createHud() {
        if (hud == null) {
            SidescanParser parser = SidescanParserFactory.build(waterfall.getRastersFolder().getParentFile());
            hud = new MraVehiclePosHud(parser, 200, 200);
            hud.setPathColor(new Color(255,255,255,128));
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        double timestamp = 0.001 * waterfall.getTimestamp();
        hud.setTimestamp(timestamp);

        int xMargin = (waterfall.getVisibleRect().width - hud.getWidth())/2;
        int yMargin = waterfall.getVisibleRect().height - hud.getHeight();
        BufferedImage image = hud.getImage(timestamp);
        if (image != null) {
            g.drawImage(image, waterfall.getVisibleRect().x + xMargin, waterfall.getVisibleRect().y + yMargin, null);
        }
    }
}
