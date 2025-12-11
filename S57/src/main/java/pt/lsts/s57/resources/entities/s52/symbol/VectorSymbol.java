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
 * 
 */
package pt.lsts.s57.resources.entities.s52.symbol;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;

import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.entities.s52.ColorTable;
import pt.lsts.s57.s52.SymbologyProcess;
import pt.omst.mapview.StateRenderer2D;

/**
 * @author Hugo Dias
 * @author Paulo Dias
 */
public class VectorSymbol extends S52Symbol implements Serializable {

    private static final long serialVersionUID = 7468860604815859507L;
    private boolean valid = true;
    private double reduction = 1;

    public static VectorSymbol forge(String bitmap, ColorTable ct) {
        return new VectorSymbol(bitmap, ct);
    }

    // 0001 501656
    // SYMB 10SY03216NIL
    // SYMD 39AISONE01V073710161500405001710717401445
    // SXPO 31one minute mark for AIS vector
    // SCRF 6AARPAT
    // SVCT 57SPA;SW1;PU7174,1616;PD7579,1616;PD7372,1445;PD7174,1616;

    private VectorSymbol(String bitmap, ColorTable ct) {
        this.colortable = ct;
        String[] lines = bitmap.split(System.getProperty("line.separator"));
        for (String line : lines) {
            if (line.startsWith("****"))
                continue;
            String lineStart = line.substring(0, 4).trim();
            String lineValue = line.substring(9).trim();

            if (lineStart.equals("SYMB")) {

            }
            else if (lineStart.equals("SYMD")) {
                this.code = lineValue.substring(0, 8);
                this.symbolType = lineValue.charAt(8);
                this.offsetX = Integer.parseInt(lineValue.substring(9, 14));
                this.offsetY = Integer.parseInt(lineValue.substring(14, 19));
                this.width = Integer.parseInt(lineValue.substring(19, 24));
                this.height = Integer.parseInt(lineValue.substring(24, 29));
                this.hotspotX = Integer.parseInt(lineValue.substring(29, 34));
                this.hotspotY = Integer.parseInt(lineValue.substring(34, 39));

                this.offsetX = (int) (SymbologyProcess.units2px(this.offsetX, null));
                this.offsetY = (int) (SymbologyProcess.units2px(this.offsetY, null));
                this.width = (int) (SymbologyProcess.units2px(this.width, null) + 1);
                this.height = (int) (SymbologyProcess.units2px(this.height, null) + 1);
                this.hotspotX = (int) (SymbologyProcess.units2px(this.hotspotX, null));
                this.hotspotY = (int) (SymbologyProcess.units2px(this.hotspotY, null));

                this.offsetX = (this.offsetX < 0 ? this.offsetX + hotspotX : this.offsetX - hotspotX);
                this.offsetY = (this.offsetY < 0 ? this.offsetY + hotspotY : this.offsetY - hotspotY);
            }
            else if (lineStart.equals("SXPO")) {
                this.description = lineValue;
            }
            else if (lineStart.equals("SCRF")) {
                while (lineValue.length() > 0) {
                    try {
                        this.colors.put(lineValue.charAt(0), colortable.get(lineValue.substring(1, 6)));
                    }
                    catch (Exception e) {
                        valid = false;
                        System.out.println(
                                "Symbol " + this.code + ". The color " + lineValue.substring(1, 6) + " doesnt exist.");
                    }
                    lineValue = lineValue.substring(6);
                }
            }
            else if (lineStart.equals("SVCT")) {
                addLine(lineValue);
            }
        }
        // System.out.println(this.toString());
        
        // Fix sizes to account pen width
        int ad = getMaxPenWidthInPixelsFromCommands(symbolData.toString());
        this.offsetX += ad;
        this.offsetY += ad;
        this.width += ad * 2;
        this.height += ad * 2;
        this.hotspotX -= ad;
        this.hotspotY -= ad;
    }

    /**
     * @param symbolData 
     * @return
     */
    static int getMaxPenWidthInPixelsFromCommands(String symbolData) {
        String[] commands = symbolData.replaceAll("[\\r\\n]", "").split(";");
        int ad = 0;
        for (String str : commands) {
            if (!str.startsWith("SW"))
                continue;
            int i = Integer.parseInt(str.replace("SW", ""));
            ad = Math.max(ad, i);
        }
        ad = (int) (SymbologyProcess.mm2px(ad * 0.3, null));
        return ad;
    }

    @Override
    public BufferedImage get(StateRenderer2D srend, MarinerControls mc, float rotation) {
        return getScaled(srend, mc, rotation, 1);
    }
    
    @Override
    public BufferedImage getScaled(StateRenderer2D srend, MarinerControls mc, float rotation, double scaled) {
        double reduction = (scaled == 0 ? 1 : 1 / scaled) * this.reduction;
        
        String[] commands = symbolData.toString().replaceAll("[\\r\\n]", "").split(";");
        BufferedImage image = new BufferedImage((int) Math.round(width / reduction),
                (int) Math.round(height / reduction), BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Point2D curpos = new Point2D.Double();
        Point2D newpos = new Point2D.Double();
        Color curpen = new Color(0, 0, 0);
        Color curpenAlfa = curpen;
        int alfaForPolyFill = 255;
        double curpenWidth = 0;
        Polygon polygon = new Polygon();
        boolean polygonMode = false;
        for (String cmd : commands) {
            if (cmd.length() >= 2) {
                String[] points;
                String type = cmd.substring(0, 2);
                if (type.equals("SP")) {
                    curpen = colors.get(cmd.charAt(2)).get(mc.getColorScheme());
                    curpenAlfa = curpen;
                    alfaForPolyFill = 255;
                    continue;
                }
                if (type.equals("SW")) {
                    curpenWidth = Integer.valueOf(String.valueOf(cmd.charAt(2))) * 0.3;
                    curpenWidth = (curpenWidth / 25.4f) * Toolkit.getDefaultToolkit().getScreenResolution();
                    curpenWidth = curpenWidth / reduction;
                    continue;
                }
                if (type.equals("ST")) {
                    alfaForPolyFill = 255 * (4 - Integer.valueOf(String.valueOf(cmd.charAt(2)))) / 4;
                    if (alfaForPolyFill != 255)
                        curpenAlfa = new Color(curpen.getRed(), curpen.getGreen(), curpen.getBlue(), alfaForPolyFill);
                    continue;
                }
                if (type.equals("PU")) {
                    points = cmd.substring(2).split(",");
                    for (int i = 0; i < points.length / 2; i++) {
                        double x = SymbologyProcess.units2px(Integer.valueOf(points[2 * i]), srend) - hotspotX;
                        double y = SymbologyProcess.units2px(Integer.valueOf(points[2 * i + 1]), srend) - hotspotY;
                        curpos.setLocation(x / reduction, y / reduction);
                        if (polygonMode)
                            polygon.addPoint((int) curpos.getX(), (int) curpos.getY());
                    }
                    continue;
                }
                if (type.equals("PD")) {
                    if (cmd.length() > 2) {
                        points = cmd.substring(2).split(",");
                        for (int i = 0; i < points.length / 2; i++) {

                            double x = SymbologyProcess.units2px(Integer.valueOf(points[2 * i]), srend) - hotspotX;
                            double y = SymbologyProcess.units2px(Integer.valueOf(points[2 * i + 1]), srend) - hotspotY;

                            newpos.setLocation(x / reduction, y / reduction);
                            canvas.setColor(curpen);
                            canvas.setStroke(new BasicStroke((float) curpenWidth, BasicStroke.CAP_ROUND,
                                    BasicStroke.JOIN_ROUND));
                            canvas.draw(new Line2D.Double(curpos, newpos));
                            canvas.fill(new Line2D.Double(curpos, newpos));
                            curpos.setLocation(newpos);
                            if (polygonMode)
                                polygon.addPoint((int) curpos.getX(), (int) curpos.getY());
                        }
                        continue;
                    }
                    else {
                        canvas.setColor(curpen);
                        canvas.setStroke(new BasicStroke((float) curpenWidth));
                        canvas.drawOval((int) curpos.getX(), (int) curpos.getY(), 1, 1);
                    }
                    continue;
                }
                if (type.equals("CI")) {
                    int radius = (int) (SymbologyProcess.units2px(Integer.valueOf(cmd.substring(2)), srend));
                    radius = (int) (radius / reduction);
                    if (polygonMode) {
                        canvas.setColor(curpenAlfa);
                        canvas.fill(new Ellipse2D.Double(curpos.getX() - radius, curpos.getY() - radius, radius * 2,
                                radius * 2));
                    }
                    else {
                        canvas.setColor(curpen);
                        canvas.setStroke(new BasicStroke((float) curpenWidth));
                        canvas.draw(new Ellipse2D.Double(curpos.getX() - radius, curpos.getY() - radius, radius * 2,
                                radius * 2));
                    }
                    continue;
                }
                if (type.equals("PM")) {
                    polygonMode = !polygonMode;
                    if (polygonMode)
                        polygon.addPoint((int) curpos.getX(), (int) curpos.getY());
                    continue;
                }
                if (type.equals("FP")) {
                    if (polygon.npoints > 0) {
                        canvas.setColor(curpenAlfa);
                        canvas.fill(polygon);

                        polygon.reset();
                    }
                    continue;
                }
                else {
                    System.out.println("command " + type + " unknown- > " + cmd);
//                    System.out.println(Arrays.toString(commands));
                }
            }
        }
        return image;
    }

    protected boolean isValid() {
        if (this.code.length() != 8) {
            valid = false;
            System.out.println("vector discarded code invalid " + this.toString());
        }
        if (this.colors.size() < 1) {
            valid = false;
            System.out.println("vector discarded no color " + this.toString());
        }
        if (this.height < 1 || this.width < 1) {
            valid = false;
            System.out.println("vector discarded height or width <1  " + this.toString());
        }
        return valid;
    }
}
