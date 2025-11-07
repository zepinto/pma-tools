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
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
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
 * Author: José Pinto
 * Dec 4, 2012
 */

package pt.omst.rasterfall;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.Vector;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.core.LocationType;
import pt.omst.neptus.core.SystemPositionAndAttitude;
import pt.omst.neptus.sidescan.SidescanParameters;
import pt.omst.neptus.sidescan.SidescanParser;

/**
 * @author zp
 */
@Slf4j
public class MraVehiclePosHud {

    protected Vector<SystemPositionAndAttitude> states = new Vector<>();
    protected double startTime, endTime;
    protected double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE,
            maxY = -Double.MAX_VALUE;
    protected LocationType ref = null;

    protected BufferedImage map = null;
    protected BufferedImage img = null;
    @Getter
    protected int currentPosition = -1, width, height;

    protected Color pathColor = Color.black;

    public MraVehiclePosHud(SidescanParser ssparser, int width, int height) {
        this.width = width;
        this.height = height;
        loadSidescanPositions(ssparser);
    }

    protected void loadSidescanPositions(SidescanParser ssparser) {
        int subsystem = ssparser.getSubsystemList().getLast();
        SidescanParameters params = ssparser.getDefaultParams();
        startTime = ssparser.firstPingTimestamp() / 1000.0;
        endTime = ssparser.lastPingTimestamp() / 1000.0;
        for (long time = ssparser.firstPingTimestamp(); time < ssparser.lastPingTimestamp(); time += 1000) {
            SystemPositionAndAttitude state = null;
            try {
                 state = ssparser.getLineAtTime(time, subsystem, params).getState();
            }
            catch (Exception e) {
                log.error("Error loading position at time " + time);
                state = null;
                continue;
            }
            
            if (state != null) {
                LocationType loc = state.getPosition();
                if (ref == null) {
                    ref = new LocationType(loc);
                    loc.convertToAbsoluteLatLonDepth();
                }
                states.add(state);
            } else
                states.add(null);
        }
        System.out.println("Loaded " + states.size() + " positions");
    }

    public BufferedImage getImage(double timestamp) {
        double desiredWidth = maxX - minX;
        double desiredHeight = maxY - minY;
        double zoom = (width - 20) / desiredWidth;
        zoom = Math.min(zoom, (height - 20) / desiredHeight);
        setTimestamp(timestamp);
        SystemPositionAndAttitude state = states.get(currentPosition);

        if (map == null) {
            map = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            createMap();
        }
        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = img.createGraphics();
        g.drawImage(map, 0, 0, null);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(zoom, zoom);
        g.translate(-minX + 10, -minY + 10);

        g.setColor(Color.red);
        if (state != null) {
            LocationType position = state.getPosition();
            if (position != null) {
                double offsets[] = position.getOffsetFrom(ref);
                Ellipse2D.Double tmp = new Ellipse2D.Double(offsets[1] - 3 / zoom, -offsets[0] - 3 / zoom, 6 / zoom, 6 / zoom);
                g.fill(tmp);
                g.setColor(Color.black);
                g.draw(tmp);
            }
        }

        return img;
    }

    /**
     * @return the startTime
     */
    public final double getStartTime() {
        return startTime;
    }

    /**
     * @param startTime the startTime to set
     */
    public final void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public BufferedImage getImage(double startTimestamp, double endTimestamp, double timestep) {
        double desiredWidth = maxX - minX;
        double desiredHeight = maxY - minY;
        double zoom = (width - 20) / desiredWidth;
        zoom = Math.min(zoom, (height - 20) / desiredHeight);
        if (map == null) {
            map = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            createMap();
        }
        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = img.createGraphics();
        g2.drawImage(map, 0, 0, null);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (double timestamp = startTimestamp; timestamp < endTimestamp; timestamp += timestep) {
            Graphics2D g = (Graphics2D) g2.create();
            setTimestamp(timestamp);
            SystemPositionAndAttitude state = states.get(currentPosition);

            g.scale(zoom, zoom);
            g.translate(-minX + 10, -minY + 10);
            double offsets[] = state.getPosition().getOffsetFrom(ref);
            g.setColor(Color.red);
            Ellipse2D.Double tmp = new Ellipse2D.Double(offsets[1] - 3 / zoom, -offsets[0] - 3 / zoom, 6 / zoom, 6 / zoom);
            g.fill(tmp);
        }
        return img;

    }

    protected void createMap() {
        GeneralPath path = new GeneralPath();
        path.moveTo(0, 0);

        for (int i = 0; i < states.size(); i++) {
            SystemPositionAndAttitude state = states.get(i);
            if (state == null)
                continue;

            LocationType loc = state.getPosition();

            double[] offsets = loc.getOffsetFrom(ref);
            double x = offsets[1];
            double y = -offsets[0];
            maxX = Math.max(x, maxX);
            maxY = Math.max(y, maxY);
            minX = Math.min(x, minX);
            minY = Math.min(y, minY);
            path.lineTo(x, y);
        }

        double desiredWidth = maxX - minX;
        double desiredHeight = maxY - minY;

        double zoom = (width - 20) / desiredWidth;
        zoom = Math.min(zoom, (height - 20) / desiredHeight);

        Graphics2D g = map.createGraphics();//offscreen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.scale(zoom, zoom);
        g.translate(-minX + 10, -minY + 10);

        g.setColor(new Color(pathColor.getRed(), pathColor.getGreen(), pathColor.getBlue(), 64));
        g.setStroke(new BasicStroke(20f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);

        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(pathColor.getRed(), pathColor.getGreen(), pathColor.getBlue(), 200));
        g.draw(path);
    }

    public double getTimestamp() {
        return currentPosition + startTime;
    }

    public void setTimestamp(double timeSecs) {
        if (timeSecs <= startTime)
            currentPosition = 0;
        else if (timeSecs >= endTime)
            currentPosition = states.size() - 1;
        else {
            currentPosition = (int) (timeSecs - startTime);
        }
    }

    /**
     * @return the endTime
     */
    public final double getEndTime() {
        return endTime;
    }

    /**
     * @param endTime the endTime to set
     */
    public final void setEndTime(double endTime) {
        this.endTime = endTime;
    }

    /**
     * @param pathColor the pathColor to set
     */
    public final void setPathColor(Color pathColor) {
        if (this.pathColor.getRGB() != pathColor.getRGB()) {
            this.pathColor = pathColor;
            map = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            createMap();
        }
    }
}
