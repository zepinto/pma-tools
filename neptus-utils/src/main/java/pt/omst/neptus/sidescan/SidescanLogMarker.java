/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: jqcorreia
 * Aug 26, 2013
 */

package pt.omst.neptus.sidescan;

import java.io.Serial;

import lombok.Setter;
import pt.omst.neptus.colormap.ColorMap;
import pt.omst.neptus.colormap.ColorMapFactory;

/**
 * This will be serializable, so no name changes of the fields!
 *
 * @author jqcorreia
 */
public class SidescanLogMarker extends LogMarker {
    @Serial
    private static final long serialVersionUID = 2L;
    private static final int CURRENT_VERSION = 2;
    @Setter
    private double x;
    @Setter
    private double y;
    @Setter
    private int width;
    @Setter
    private int height;
    @Setter
    private double fullRange;// width in meters
    private int subSys;// created on subSys
    private String colorMap;
    @Setter
    private boolean point;
    /**
     * Added version info. For the loaded old marks this value will be 0.
     */
    private int sidescanMarkVersion = CURRENT_VERSION;

    public SidescanLogMarker(String label, double timestamp, double lat, double lon, double x, double y,
                             int w, int h, double range, int subSys, ColorMap colorMap) {
        super(label, timestamp, lat, lon);
        this.setX(x);
        this.setY(y);
        this.setWidth(w);
        this.setHeight(h);
        this.setFullRange(range);
        this.setSubSys(subSys);
        this.setColorMap(colorMap.toString());
    }

    /**
     * Sets the subsystem and the color map to the default values
     * Also, if the width and height are 0, it will set the mark as a point
     * @param subSys the subsystem for which this mark was created
     */
    public void setDefaults(int subSys) {
        if (this.getSubSys() == 0)
            this.setSubSys(subSys);

        if (getColorMap() == null)
            setColorMap(ColorMapFactory.createBronzeColormap().toString());

        if (this.getWidth() == 0 && this.getHeight() == 0)
            this.setPoint(true);
    }

    /**
     * The subsystem for which this mark was created
     * @return the subSystem
     */
    public int getSubSys() {
        return subSys;
    }

    private void setSubSys(int subSys) {
        this.subSys = subSys;
    }

    /**
     * The color map used to draw the mark
     * @return the colorMap
     */
    public String getColorMap() {
        return colorMap;
    }

    /**
     * The width of the image in pixels at the time of drawing the mark (not really useful)
     *
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * The height of the image in pixels (sidescan lines)
     *
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    private void setColorMap(String colorMap) {
        this.colorMap = colorMap;
    }

    public void fixLocation(double latRads, double lonRads) {
        this.setLatRads(latRads);
        this.setLonRads(lonRads);
    }

    /**
     * @return the SidescanMarkVersion
     */
    public int getSidescanMarkVersion() {
        return sidescanMarkVersion;
    }

    /**
     * This will set the version to {@link #CURRENT_VERSION} (currently {@value #CURRENT_VERSION})
     */
    public void resetSidescanMarkVersion() {
        this.sidescanMarkVersion = CURRENT_VERSION;
    }

    /**
     * The ground distance in meters, between nadir and the mark
     *
     * @return the x
     */
    public double getX() {
        return x;
    }

    /**
     * The y coordinate in the screen in pixels (not really useful)
     *
     * @return the y
     */
    public double getY() {
        return y;
    }

    /**
     * The width, in meters, of the mark on the ground
     *
     * @return the width
     */
    public double getFullRange() {
        return fullRange;
    }

    /**
     * If the mark is a point or a rectangle
     * @return the point
     */
    public boolean isPoint() {
        return point;
    }

}
