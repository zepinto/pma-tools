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

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.text.StrBuilder;

import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.entities.s52.ColorScheme;
import pt.lsts.s57.resources.entities.s52.ColorTable;
import pt.lsts.s57.resources.entities.s52.S52Color;
import pt.omst.mapview.StateRenderer2D;

/**
 * @author Hugo Dias
 */
public abstract class S52Pattern {

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
    protected String fill;
    protected String spacing;
    protected int minDistance;
    protected int maxDistance;

    protected Map<Character, S52Color> colors = new HashMap<Character, S52Color>();
    protected StrBuilder symbolData = new StrBuilder();

    public abstract BufferedImage get(StateRenderer2D srend, MarinerControls mc);
    public abstract BufferedImage getScaled(StateRenderer2D srend, MarinerControls mc, double scaled);

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
     * Type
     * 
     * @return the symbolType
     */
    public char getSymbolType() {
        return symbolType;
    }

    /**
     * type of the fill pattern: STG staggered pattern LIN linear pattern
     * 
     * @return the fill
     */
    public String getFill() {
        return fill;
    }

    /**
     * pattern symbol spacing: CON constant space SCL scale dependent spacing
     * 
     * @return the spacing
     */
    public String getSpacing() {
        return spacing;
    }

    /**
     * minimum distance (units of 0.01 mm) between pattern symbols covers (bounding box + pivot point); where 0 <= PAMI
     * <= 32767
     * 
     * @return the minDistance
     */
    public int getMinDistance() {
        return minDistance;
    }

    /**
     * maximum distance (units of 0.01 mm) between pattern symbols covers(bounding box + pivot point); where 0 <= PAMA
     * <= 32767; PAMA is meaningless if PASP = 'CON'
     * 
     * @return the maxDistance
     */
    public int getMaxDistance() {
        return maxDistance;
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "S52Pattern [code=" + code + ", description=" + description + ", symbolType=" + symbolType + ", offsetX="
                + offsetX + ", offsetY=" + offsetY + ", width=" + width + ", height=" + height + ", hotspotX="
                + hotspotX + ", hotspotY=" + hotspotY + ", fill=" + fill + ", spacing=" + spacing + ", minDistance="
                + minDistance + ", maxDistance=" + maxDistance + "]";
    }

}
