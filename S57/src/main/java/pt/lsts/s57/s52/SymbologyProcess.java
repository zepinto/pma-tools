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
 * Oct 26, 2011
 */
package pt.lsts.s57.s52;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.List;

import pt.lsts.s57.entities.S57ObjectPainter;
import pt.lsts.s57.entities.geometry.S57Geometry;
import pt.lsts.s57.entities.geometry.S57GeometryType;
import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.Resources;
import pt.lsts.s57.resources.entities.s52.Instruction;
import pt.lsts.s57.resources.entities.s52.symbol.S52Pattern;
import pt.lsts.s57.resources.entities.s52.symbol.S52Symbol;
import pt.omst.mapview.StateRenderer2D;

/**
 * @author Hugo Dias
 */
public class SymbologyProcess {

    public static double CONV_INCH_2_MILIMETERS = 25.4;
    public static double DPI = Toolkit.getDefaultToolkit().getScreenResolution();
    private static final float DASH_LENGTH = (float) ((3.6 / 25.4f) * DPI);
    private static final float DOTT_LENGTH = (float) ((0.6 / 25.4f) * DPI);
    private static final float DASH_SPACE = (float) ((1.8 / 25.4f) * DPI);
    private static final float DOTT_SPACE = (float) ((1.2 / 25.4f) * DPI);

    /**
     * Convert unit of 0.01 mm to pixels
     * 
     * @param mm
     * @param srend
     * @return
     */
    public static double units2px(double mm, StateRenderer2D srend) {
        return (mm * 0.01 / 25.4f) * DPI;
    }

    /**
     * Convert mm to pixels
     * 
     * @param mm
     * @param srend
     * @return
     */
    public static double mm2px(double mm, StateRenderer2D srend) {
        return (mm / 25.4f) * DPI;
    }

    public static void process(List<Instruction> intructions, S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend,
            Resources resources, MarinerControls mc) {
        for (Instruction instruction : intructions) {
            process(instruction, obj, g, srend, resources, mc);
        }

    }

    public static void process(Instruction instruction, S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend,
            Resources resources, MarinerControls mc) {
        switch (instruction.getType()) {
            case TX -> TX(obj, g, srend, instruction, resources, mc);
            case TE -> TE(obj, g, srend, instruction, resources, mc);
            case SY -> SY(obj, g, srend, instruction, resources, mc);
            case LS -> LS(obj, g, srend, instruction, resources, mc);
            case LC -> LC(obj, g, srend, instruction, resources, mc);
            case AC -> AC(obj, g, srend, instruction, resources, mc);
            case AP -> AP(obj, g, srend, instruction, resources, mc);
            default -> {
                System.out.println(obj.getObject().toStringLup(mc));
                throw new IllegalArgumentException("Invalid symbology " + instruction.getType());
            }
        }
    }

    private static void TX(S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend, Instruction instruction,
            Resources resources, MarinerControls mc) {
        // Parse paramenters
        var params = instruction.getParams();
        var attrib = params.get(0);
        // int hjust = Integer.parseInt(params.get(1)); // 1 center, 2 right, 3 left
        // int vjust = Integer.parseInt(params.get(2)); // 1 bottom, 2 center, 3 top
        // int spacing = Integer.parseInt(params.get(3)); // 1 fit, 2 standard , 3 standard w/word wrap > 8 chars do
        // newline
        var chars = params.get(4).replace("'", "");
        // int style = Integer.parseInt(chars.substring(0, 1)); // CHARS
        int weight = Integer.parseInt(chars.substring(1, 2)); // CHARS
        // int width = Integer.parseInt(chars.substring(2, 3)); // CHARS
        double bsize = Integer.parseInt(chars.substring(3));
        double bsizeP = (bsize * 0.01 / 25.4f) * DPI;
        float offX = (float) (Integer.valueOf(params.get(5)) * bsizeP);
        float offY = (float) (Integer.parseInt(params.get(6)) * bsizeP);
        var color = resources.getColor(params.get(7), mc.getColorScheme());
        int group = Integer.parseInt(params.get(8));

        // Filter and only draw important and other groups available
        if (group != 10 && group != 20 && group != 21 && group != 25)
            return;
        if (group == 10 && !mc.isShowImportantText())
            return;
        if (group == 20 && !mc.isShowOtherText())
            return;

        // Create graphics to draw on
        var g2d = (Graphics2D) g.create();
        var objO = obj.getObject();
        var point = srend.getScreenPosition(objO.getGeometry().getCenter());
        g2d.translate(point.getX(), point.getY() - 15);

        // get the attribute
        var attribute = obj.getAttribute(attrib);
        var type = attribute.getAttributeType();

        String text;
        if ("L".equals(type) || "E".equals(type)) {
            text = attribute.getMeaning();
        } else {
            text = attribute.getValue().get(0);
        }

        if (text != null && !"".equalsIgnoreCase(text)) {
            var as = new AttributedString(text);
            as.addAttribute(TextAttribute.SIZE, bsize);
            as.addAttribute(TextAttribute.WIDTH, TextAttribute.WIDTH_REGULAR);
            if (weight == 4)
                as.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_LIGHT);
            if (weight == 5)
                as.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_MEDIUM);
            if (weight == 6)
                as.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
            TextLayout tl = new TextLayout(as.getIterator(), g2d.getFontRenderContext());
            // g2d.setColor(pt.up.fe.dceg.neptus.util.ColorUtils.invertColor(color));
            // tl.draw(g2d, 1+offX, 1+offY);
            g2d.setColor(color);
            tl.draw(g2d, 0 + offX, 0 + offY);
        }
        g2d.dispose();
    }

    private static void TE(S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend, Instruction instruction,
            Resources resources, MarinerControls mc) {
        // Parse paramenters
        var params = instruction.getParams();
        var text1 = params.get(0).replace("'", "");
        var text = params.get(1).replace("'", "");
        // int hjust = Integer.parseInt(params.get(2)); // 1 center, 2 right, 3 left
        // int vjust = Integer.parseInt(params.get(3)); // 1 bottom, 2 center, 3 top
        // int spacing = Integer.parseInt(params.get(4)); // 1 fit, 2 standard , 3 standard w/word wrap > 8 chars do
        // newline
        var chars = params.get(5).replace("'", "");
        // int style = Integer.parseInt(chars.substring(0, 1)); // CHARS
        int weight = Integer.parseInt(chars.substring(1, 2)); // CHARS
        // int width = Integer.parseInt(chars.substring(2, 3)); // CHARS
        double bsize = Integer.parseInt(chars.substring(3, 5));
        double bsizeP = (bsize * 0.351 / 25.4f) * DPI;
        float offX = (float) (Integer.valueOf(params.get(6)) * bsizeP);
        float offY = (float) (Integer.parseInt(params.get(7)) * bsizeP);
        var color = resources.getColor(params.get(8), mc.getColorScheme());
        int group = Integer.parseInt(params.get(9));

        // Filter and only draw important and other groups available
        if (group != 10 && group != 20 && group != 21 && group != 25)
            return;
        if (group == 10 && !mc.isShowImportantText())
            return;
        if (group == 20 && !mc.isShowOtherText())
            return;

        // Create graphics to draw on
        var g2d = (Graphics2D) g.create();
        var objO = obj.getObject();
        var point = srend.getScreenPosition(objO.getGeometry().getCenter());
        g2d.translate(point.getX(), point.getY() - 15);

        // get the attribute
        var attribute = obj.getAttribute(text);
        var type = attribute.getAttributeType();
        if ("L".equals(type) || "E".equals(type)) {
            text = attribute.getMeaning();
        } else {
            text = attribute.getValue().get(0);
        }

        int index = text1.indexOf("%");
        var textFinal = text1.substring(0, index).concat(text);

        if (text != null && !"".equalsIgnoreCase(text)) {
            var as = new AttributedString(textFinal);
            as.addAttribute(TextAttribute.SIZE, bsize);
            as.addAttribute(TextAttribute.WIDTH, TextAttribute.WIDTH_REGULAR);
            switch (weight) {
                case 4 -> as.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_LIGHT);
                case 5 -> as.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_MEDIUM);
                case 6 -> as.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
                default -> { }
            }
            var tl = new TextLayout(as.getIterator(), g2d.getFontRenderContext());
            g2d.setColor(color);
            tl.draw(g2d, 0 + offX, 0 + offY);
        }
        g2d.dispose();
    }

    private static void AC(S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend, Instruction instruction,
            Resources resources, MarinerControls mc) {
        // Copy graphics, translate and scale to actual level of detail
        var g2d = (Graphics2D) g.create();
        var start = srend.getScreenPosition(obj.getObject().getGeometry().getLocation());
        g2d.translate(start.getX(), start.getY());
        double res = Math.pow(2.0, srend.getLevelOfDetail() - S57Geometry.DEFAULT_LOD);
        g2d.scale(res, res);

        var params = instruction.getParams();
        Color color;
        var originalColor = resources.getColor(instruction.getParams().get(0), mc.getColorScheme());
        // Transparency param
        if (params.size() == 2 && !params.get(1).isEmpty()) {
            double percent = (Double.parseDouble(params.get(1)) * 0.25);
            color = new Color(originalColor.getRed(), originalColor.getGreen(), originalColor.getBlue(),
                    (int) (255 * percent));
        } else {
            // No transparency param
            color = originalColor;
        }
        var shape = obj.getObject().getShape();

        g2d.setColor(color);

        g2d.rotate(-srend.getRotation());
        g2d.fill(shape);
        // g2d.draw(shape);

        // flush graphics copy
        g2d.dispose();
    }

    private static void LS(S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend, Instruction instruction,
            Resources resources, MarinerControls mc) {
        // Copy graphics, translate and scale to actual level of detail
        var g2d = (Graphics2D) g.create();
        var start = srend.getScreenPosition(obj.getObject().getGeometry().getLocation());
        g2d.translate(start.getX(), start.getY());
        float res = (float) Math.pow(2.0, srend.getLevelOfDetail() - S57Geometry.DEFAULT_LOD);
        g2d.scale(res, res);

        var params = instruction.getParams();
        Stroke stroke;
        var strokeType = params.get(0);
        float strokeWidth = (float) (((Float.parseFloat(params.get(1)) * 0.32) / 25.4f) * DPI);

        // TEST contains renderer
        var topLeft = srend.getRealWorldLocation(new Point2D.Double(0, 0));
        var bottomRight = srend
                .getRealWorldLocation(new Point2D.Double(srend.getWidth() - 1, srend.getHeight() - 1));

        if (obj.getObject().getGeoType() == S57GeometryType.AREA && "DASH".equals(strokeType)) {
            var path = new Path2D.Double();
            var first = obj.getObject().getGeometry().getList().get(0).get(0);
            path.moveTo(first.getX(), first.getY());
            for (var p : obj.getObject().getGeometry().getList().get(0)) {
                path.lineTo(p.getX(), p.getY());
            }
            var r = path.getBounds2D();
            if (r.contains(topLeft.getLatitudeDegs(), topLeft.getLongitudeDegs())
                    && r.contains(bottomRight.getLatitudeDegs(), bottomRight.getLongitudeDegs())) {
                return;
            }
        }

        stroke = switch (strokeType) {
            case "SOLD" -> new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            case "DASH" -> {
                float[] dashPattern = { DASH_LENGTH / res, DASH_SPACE / res };
                yield new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, dashPattern, 0f);
            }
            case "DOTT" -> {
                float[] dottPattern = { DOTT_LENGTH / res, DOTT_SPACE / res };
                yield new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, dottPattern, 0f);
            }
            default -> {
                System.out.println("Unexpected stroke option: " + instruction.getParams().get(0));
                yield g2d.getStroke();
            }
        };
        // System.out.println("second stop " + ((System.nanoTime() - starttime) / 1E6) + "ms");
        g2d.setStroke(stroke);
        g2d.setColor(resources.getColor(params.get(2), mc.getColorScheme()));
        g2d.rotate(-srend.getRotation());
        g2d.draw(obj.getObject().getShape());
        // flush graphics copy
        g2d.dispose();
    }

    private static void AP(S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend, Instruction instruction,
            Resources resources, MarinerControls mc) {
        var g2d = (Graphics2D) g.create();
        var start = srend.getScreenPosition(obj.getObject().getGeometry().getLocation());
        g2d.translate(start.getX(), start.getY());
        double zoom = Math.pow(2.0, srend.getLevelOfDetail() - S57Geometry.DEFAULT_LOD);
        g2d.scale(zoom, zoom);

        var params = instruction.getParams();
        var patternName = params.get(0);

        S52Pattern pattern;
        try {
            pattern = resources.getPattern(patternName);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
        var patternImg = pattern.get(srend, mc);

        // TODO rotation only rotate the symbol
        var fillType = pattern.getFill();
        var spacingType = pattern.getSpacing();
        int minDistance = (int) units2px(pattern.getMinDistance(), srend);
        int maxDistance = (int) units2px(pattern.getMaxDistance(), srend);
        int spacing = switch (spacingType) {
            case "CON" -> (int) units2px(pattern.getMinDistance(), srend);
            case "SCL" -> {
                int sp = maxDistance - minDistance;
                yield Math.max(minDistance, Math.min(sp, maxDistance));
            }
            default -> 0;
        };
        BufferedImage bi = null;
        int finalSpacing = spacing;
        bi = switch (fillType) {
            case "LIN" -> {
                var img = new BufferedImage(pattern.getWidth() + finalSpacing, pattern.getHeight() + finalSpacing,
                        BufferedImage.TYPE_INT_ARGB);
                var gi = img.createGraphics();
                gi.drawImage(patternImg, null, finalSpacing / 2, finalSpacing / 2);
                gi.dispose();
                yield img;
            }
            case "STG" -> {
                // TODO implement staggered properly
                var img = new BufferedImage(pattern.getWidth() + finalSpacing, pattern.getHeight() + finalSpacing,
                        BufferedImage.TYPE_INT_ARGB);
                var gi = img.createGraphics();
                gi.drawImage(patternImg, null, finalSpacing / 2, finalSpacing / 2);
                gi.dispose();
                yield img;
            }
            default -> null;
        };

        if (bi == null) return;
        var triangles = new TexturePaint(bi,
                new Rectangle(0, 0, (int) (bi.getWidth() / zoom), (int) (bi.getHeight() / zoom)));
        g2d.setPaint(triangles);
        g2d.rotate(-srend.getRotation());
        g2d.fill(obj.getObject().getShape());

        g2d.dispose();
    }

    private static void SY(S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend, Instruction instruction,
            Resources resources, MarinerControls mc) {
        var g2d = (Graphics2D) g.create();
        var objO = obj.getObject();
        var params = instruction.getParams();
        var symbolName = params.get(0);
        S52Symbol raster;
        try {
            raster = resources.getSymbol(symbolName);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        // draw symbol at the center of the object if its an AREA
        if (objO.getGeoType() == S57GeometryType.AREA) {
            var point = srend.getScreenPosition(objO.getGeometry().getCenter());
            g2d.translate(point.getX(), point.getY());
        } else {
            var start = srend.getScreenPosition(objO.getGeometry().getLocation());
            g2d.translate(start.getX(), start.getY());
        }

        double orientation = 0;
        if (params.size() == 2 && !params.get(1).isEmpty()) {
            var orientValue = params.get(1).replace("ORIENT", "");
            var orientAttr = objO.getAttribute("ORIENT");
            if (orientAttr.isEmpty() || orientAttr == null) {
                if (!orientValue.isEmpty()) {
                    orientation = Double.parseDouble(orientValue);
                }
            } else {
                orientation = Double.parseDouble(obj.getAttribute("ORIENT").getValue().get(0));
            }

            try {
                g2d.rotate(Math.toRadians(orientation));
            } catch (Exception e) {
                System.out.println("SY intruction rotation error");
            }
        }

        // g2d.rotate(-srend.getRotation());
        g2d.drawImage(raster.get(srend, mc, 0), (int) -raster.getOffsetX(), (int) -raster.getOffsetY(),
                raster.getWidth(), raster.getHeight(), null);

        g2d.dispose();
    }

    private static void LC(S57ObjectPainter obj, Graphics2D g, StateRenderer2D srend, Instruction instruction,
            Resources resources, MarinerControls mc) {
        var g2d = (Graphics2D) g.create();
        var objO = obj.getObject();
        var shape = objO.getShape();
        var start = srend.getScreenPosition(objO.getGeometry().getLocation());
        g2d.translate(start.getX(), start.getY());
        g2d.rotate(-srend.getRotation());
        var renderHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        renderHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.addRenderingHints(renderHints);

        double res = Math.pow(2.0, srend.getLevelOfDetail() - S57Geometry.DEFAULT_LOD);
        var lineName = instruction.getParams().get(0);
        S52Symbol line;
        try {
            line = resources.getLine(lineName);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
        var lineImg = line.get(srend, mc, 0);

        float advance = line.getWidth();
        var color = line.getColor(mc);
        var it = new FlatteningPathIterator(shape.getPathIterator(null), 1);

        var points = new float[6];
        float lastX = 0, lastY = 0;
        float thisX = 0, thisY = 0;
        int type;
        float next = 0;
        int currentShape = 0;
        int length = 1;
        while (currentShape < length && !it.isDone()) {

            type = it.currentSegment(points);
            switch (type) {
                case PathIterator.SEG_MOVETO -> {
                    lastX = (float) (points[0] * res);
                    lastY = (float) (points[1] * res);
                    next = 0;
                }
                case PathIterator.SEG_CLOSE -> { }
                case PathIterator.SEG_LINETO -> {
                    thisX = (float) (points[0] * res);
                    thisY = (float) (points[1] * res);
                    if (thisX != lastX || thisY != lastY) {
                        float dx = thisX - lastX;
                        float dy = thisY - lastY;
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);
                        if (distance >= advance) {
                            float r = 1.0f / distance;
                            float angle = (float) Math.atan2(dy, dx);
                            angle = (float) (Math.PI + angle);

                            while (currentShape < length && distance >= next) {
                                float x = lastX + next * dx * r;
                                float y = lastY + next * dy * r;
                                if (distance >= next + advance) {
                                    var g3 = (Graphics2D) g2d.create();
                                    g3.translate(x, y);
                                    g3.rotate(angle);
                                    g3.drawImage(lineImg, null, -line.getWidth() - line.getOffsetX(), -line.getOffsetY());
                                    g3.dispose();
                                } else {
                                    g2d.setColor(color);
                                    g2d.drawLine((int) thisX, (int) thisY, (int) x, (int) y);
                                }

                                next += advance;
                                currentShape++;
                                currentShape %= length;
                            }
                        } else {
                            float r = 1.0f / distance;
                            float x = lastX + next * dx * r;
                            float y = lastY + next * dy * r;
                            g2d.setColor(color);
                            g2d.drawLine((int) thisX, (int) thisY, (int) x, (int) y);
                        }
                        next = 0;
                        lastX = thisX;
                        lastY = thisY;
                    }
                }
                default -> { }
            }
            it.next();
        }
        g2d.dispose();
    }

}
