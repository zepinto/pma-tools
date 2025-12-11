/*
 * Copyright (c) 2004-2016 Laboratório de Sistemas e Tecnologia Subaquática and Authors
 * All rights reserved.
 * Faculdade de Engenharia da Universidade do Porto
 * Departamento de Engenharia Electrotécnica e de Computadores
 * Rua Dr. Roberto Frias s/n, 4200-465 Porto, Portugal
 *
 * For more information please see <http://whale.fe.up.pt/neptus>.
 *
 * Created by Hugo Dias
 * Nov 14, 2011
 */
package pt.lsts.s57.resources.entities.s52.symbol;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.Serializable;

import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.entities.s52.ColorScheme;
import pt.lsts.s57.resources.entities.s52.ColorTable;
import pt.omst.mapview.StateRenderer2D;

/**
 * @author Hugo Dias
 */
public class RasterPattern extends S52Pattern implements Serializable {

    private static final long serialVersionUID = 3236674722864911893L;
    private BufferedImage image;

    // 0001
    // PATT 10SY00001NIL
    // |code 1|val2 |val3|val4|offx|offy|widt|heig|hotx|hoty
    // PATD 55FOULAR01RLINCON0000000000000050000500009000090000000000
    // PXPO 43area of depth less than the safety contour
    // PCRF 6ACHGRD
    // PBTM 10A@@@@@@@A
    // PBTM 10@A@@@@@A@
    // PBTM 10@@A@@@A@@
    // PBTM 10@@@A@A@@@
    // PBTM 10@@@@A@@@@
    // PBTM 10@@@A@A@@@
    // PBTM 10@@A@@@A@@
    // PBTM 10@A@@@@@A@
    // PBTM 10A@@@@@@@A
    // ****
    public static RasterPattern forge(String bitmap, ColorTable ct) {
        return new RasterPattern(bitmap, ct);
    }

    private RasterPattern(String bitmap, ColorTable ct) {
        this.colortable = ct;
        String[] lines = bitmap.split(System.getProperty("line.separator"));
        for (String line : lines) {
            if (line.startsWith("****"))
                continue;
            String lineStart = line.substring(0, 4).trim();
            String lineValue = line.substring(9).trim();

            if (lineStart.equals("PATT")) {

            }
            if (lineStart.equals("PATD")) {
                this.code = lineValue.substring(0, 8);
                this.symbolType = lineValue.charAt(8);
                this.fill = lineValue.substring(9, 12);
                this.spacing = lineValue.substring(12, 15);
                this.minDistance = Integer.parseInt(lineValue.substring(15, 20));
                this.maxDistance = Integer.parseInt(lineValue.substring(20, 25));
                this.offsetX = Integer.parseInt(lineValue.substring(25, 30));
                this.offsetY = Integer.parseInt(lineValue.substring(30, 35));
                this.width = Integer.parseInt(lineValue.substring(35, 40));
                this.height = Integer.parseInt(lineValue.substring(40, 45));
                this.hotspotX = Integer.parseInt(lineValue.substring(45, 50));
                this.hotspotY = Integer.parseInt(lineValue.substring(50, 55));
            }
            if (lineStart.equals("PXPO")) {
                this.description = lineValue;
            }
            if (lineStart.equals("PCRF")) {
                while (lineValue.length() > 0) {
                    try {
                        this.colors.put(lineValue.charAt(0), colortable.get(lineValue.substring(1, 6)));
                    }
                    catch (Exception e) {
                        System.out.println(
                                "Symbol " + this.code + ". The color " + lineValue.substring(1, 6) + " doesnt exist.");
                    }
                    lineValue = lineValue.substring(6);
                }
            }
            if (lineStart.equals("PBTM")) {
                addLine(lineValue);
            }
        }
    }

    @Override
    public BufferedImage get(StateRenderer2D srend, MarinerControls mc) {
        return getScaled(srend, mc, 1);
    }
    
    @Override
    public BufferedImage getScaled(StateRenderer2D srend, MarinerControls mc, double scaled) {
        ColorScheme scheme = mc.getColorScheme();
        if (scheme != lastColorScheme) {
            lastColorScheme = scheme;
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Color trans = new Color(0, 0, 0, 0);
            int counter = 0;
            for (char pixel : symbolData.toString().replaceAll("[\\r\\n]", "").toCharArray()) {
                if (pixel == TRANSPARENT)

                    image.setRGB(counter % width, counter / width, trans.getRGB());
                else {
                    image.setRGB(counter % width, counter / width, colors.get(pixel).get(mc.getColorScheme()).getRGB());
                }
                counter++;
            }
        }
        return image;
    }

}
