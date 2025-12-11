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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.text.StrBuilder;

import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.entities.s52.ColorScheme;
import pt.lsts.s57.resources.entities.s52.ColorTable;
import pt.lsts.s57.resources.entities.s52.S52Color;
import pt.omst.mapview.StateRenderer2D;

/**
 * @author Hugo Dias
 *
 */
public abstract class S52Symbol {

    public final static char TRANSPARENT = '@';
    protected ColorTable colortable;
    protected ColorScheme lastColorScheme;
    protected String code;
    protected String description;
    protected char symbolType; // V or R
    protected int offsetX;
    protected int offsetY;
    protected int width;
    protected int height;
    protected int hotspotX;
    protected int hotspotY;
    protected Map<Character, S52Color> colors = new HashMap<Character, S52Color>();
    protected StrBuilder symbolData = new StrBuilder();

    public abstract BufferedImage get(StateRenderer2D srend, MarinerControls mc, float rotation);
    public abstract BufferedImage getScaled(StateRenderer2D srend, MarinerControls mc, float rotation, double scaled);

    protected void addLine(String line) {
        symbolData.appendln(line);
    }

    /**
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * @return the offsetX
     */
    public int getOffsetX() {
        return offsetX;
    }

    /**
     * @return the offsetY
     */
    public int getOffsetY() {
        return offsetY;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    public Color getColor(MarinerControls mc) {
        Color color = null;
        for (Entry<Character, S52Color> s52color : colors.entrySet()) {
            color = s52color.getValue().get(mc.getColorScheme());
        }
        return color;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Symbol [code=" + code + ", description=" + description + ", symbolType=" + symbolType + ", offsetX="
                + offsetX + ", offsetY=" + offsetY + ", width=" + width + ", height=" + height + ", hotspotX="
                + hotspotX + ", hotspotY=" + hotspotY + ", colors=" + colors + "]";
    }

}
