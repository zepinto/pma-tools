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
 * Nov 11, 2011
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
 *
 */
public class RasterSymbol extends S52Symbol implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -8056279790704240873L;
    private BufferedImage image;

    public static RasterSymbol forge(String bitmap, ColorTable ct) {
        return new RasterSymbol(bitmap, ct);
    }

    // 0001
    // SYMB 10SY00032NIL
    // |code 1|offx|offy|widt|heig|hotx|hoty
    // SYMD 39OBSTRN11R000030000300009000090000000000
    // SXPO 59obstruction in the water which is always above water level
    // SCRF 12ACSTLNBLANDA
    // SBTM 10AAAAAAAAA
    // SBTM 10AAAAAAAAA
    // SBTM 10AABBBBBAA
    // SBTM 10AABBBBBAA
    // SBTM 10AABBBBBAA
    // SBTM 10AABBBBBAA
    // SBTM 10AABBBBBAA
    // SBTM 10AAAAAAAAA
    // SBTM 10AAAAAAAAA
    // ****
    private RasterSymbol(String bitmap, ColorTable ct) {
        this.colortable = ct;
        String[] lines = bitmap.split(System.getProperty("line.separator"));
        for (String line : lines) {
            if (line.startsWith("****"))
                continue;
            String lineStart = line.substring(0, 4).trim();
            String lineValue = line.substring(9).trim();

            if (lineStart.equals("SYMB")) {

            }
            if (lineStart.equals("SYMD")) {
                this.code = lineValue.substring(0, 8);
                this.symbolType = lineValue.charAt(8);
                this.offsetX = Integer.parseInt(lineValue.substring(9, 14));
                this.offsetY = Integer.parseInt(lineValue.substring(14, 19));
                this.width = Integer.parseInt(lineValue.substring(19, 24));
                this.height = Integer.parseInt(lineValue.substring(24, 29));
                this.hotspotX = Integer.parseInt(lineValue.substring(29, 34));
                this.hotspotY = Integer.parseInt(lineValue.substring(34, 39));
            }
            if (lineStart.equals("SXPO")) {
                this.description = lineValue;
            }
            if (lineStart.equals("SCRF")) {
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
            if (lineStart.equals("SBTM")) {
                addLine(lineValue);
            }
        }
    }

    @Override
    public BufferedImage get(StateRenderer2D srend, MarinerControls mc, float rotation) {
        return getScaled(srend, mc, rotation, 1);
    }
    
    @Override
    public BufferedImage getScaled(StateRenderer2D srend, MarinerControls mc, float rotation, double scaled) {
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
