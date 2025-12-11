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
 */
public class VectorPattern extends S52Pattern implements Serializable {
    private static final long serialVersionUID = 8358356452982281276L;
    private boolean valid = true;
    private double reduction = 1;

    public static VectorPattern forge(String bitmap, ColorTable ct) {
        return new VectorPattern(bitmap, ct);
    }

    private VectorPattern(String bitmap, ColorTable ct) {
        this.colortable = ct;
        String[] lines = bitmap.split(System.getProperty("line.separator"));
        for (String line : lines) {
            if (line.startsWith("****"))
                continue;
            String lineStart = line.substring(0, 4).trim();
            String lineValue = line.substring(9).trim();

            if (lineStart.equals("PATT")) {

            }
            else if (lineStart.equals("PATD")) {
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

                this.offsetX = (int) (SymbologyProcess.units2px(this.offsetX, null));
                this.offsetY = (int) (SymbologyProcess.units2px(this.offsetY, null));
                this.width = (int) (SymbologyProcess.units2px(this.width, null) + 1);
                this.height = (int) (SymbologyProcess.units2px(this.height, null) + 1);
                this.hotspotX = (int) (SymbologyProcess.units2px(this.hotspotX, null));
                this.hotspotY = (int) (SymbologyProcess.units2px(this.hotspotY, null));

                this.offsetX = (this.offsetX < 0 ? this.offsetX + hotspotX : this.offsetX - hotspotX);
                this.offsetY = (this.offsetY < 0 ? this.offsetY + hotspotY : this.offsetY - hotspotY);
            }
            else if (lineStart.equals("PXPO")) {
                this.description = lineValue;
            }
            else if (lineStart.equals("PCRF")) {
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
            else if (lineStart.equals("PVCT")) {
                addLine(lineValue);
            }
        }
        // System.out.println(this.toString());
        
        // Fix sizes to account pen width
        int ad = VectorSymbol.getMaxPenWidthInPixelsFromCommands(symbolData.toString());
        this.offsetX += ad;
        this.offsetY += ad;
        this.width += ad * 2;
        this.height += ad * 2;
        this.hotspotX -= ad;
        this.hotspotY -= ad;
    }

    @Override
    public BufferedImage get(StateRenderer2D srend, MarinerControls mc) {
        return getScaled(srend, mc, 1);
    }

    @Override
    public BufferedImage getScaled(StateRenderer2D srend, MarinerControls mc, double scaled) {
        double reduction = (scaled == 0 ? 1 : 1 / scaled) * this.reduction;
        
        String[] commands = symbolData.toString().replaceAll("[\\r\\n]", "").split(";");
        BufferedImage image = new BufferedImage((int) Math.round(width / reduction),
                (int) Math.round(height / reduction), BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Point2D curpos = new Point2D.Double();
        Point2D newpos = new Point2D.Double();
        Color curpen = new Color(0, 0, 0);
        double curpenWidth = 0;
        Polygon polygon = new Polygon();
        boolean polygonMode = false;
        for (String cmd : commands) {
            if (cmd.length() >= 2) {
                String[] points;
                String type = cmd.substring(0, 2);
                if (type.equals("SP")) {
                    curpen = colors.get(cmd.charAt(2)).get(mc.getColorScheme());
                    continue;
                }
                if (type.equals("SW")) {
                    curpenWidth = Integer.valueOf(String.valueOf(cmd.charAt(2))) * 0.3;
                    curpenWidth = (curpenWidth / 25.4f) * Toolkit.getDefaultToolkit().getScreenResolution();
                    curpenWidth = curpenWidth / reduction;
                    continue;
                }
                if (type.equals("ST")) {
                    // TODO
                    // curpen = new Color(curpen.getRGB(), hasalpha)
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
                        canvas.setColor(curpen);
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

                        canvas.setColor(curpen);
                        canvas.fill(polygon);

                        polygon.reset();
                    }
                    continue;
                }
                else {
                    System.out.println("command " + type + " unknown- > " + cmd);
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
