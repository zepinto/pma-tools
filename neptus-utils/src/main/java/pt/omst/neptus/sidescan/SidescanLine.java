//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
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
 * Author: José Correia
 * Oct 26, 2012
 */

package pt.omst.neptus.sidescan;

import java.awt.Color;
import java.awt.image.BufferedImage;

import lombok.Getter;
import lombok.Setter;
import pt.omst.neptus.colormap.ColorMap;
import pt.omst.neptus.core.SystemPositionAndAttitude;

/**
 * @author jqcorreia
 * @author pdias
 */
public class SidescanLine implements ISidescanLine, Comparable<SidescanLine> {
    /** The sonar data timestamp (milliseconds) */
    private final long timestampMillis;

    /** The sonar data size (size of {@link #data}) */
    private final int xSize;
    /**
     * The sonar y data size (1 for normal and >1 for speed correction, which means line data
     * extends more then one line)
     */
    private int ySize;

    /**
     * The sonar y pos in relation to an external list (for the next line pos one can add to this
     * the {@link #ySize})
     */
    private int yPos;

    /** The sonar frequency
     * -- GETTER --
     *
     * @return the frequency
     */
    @Getter
    private float frequency;
    /** The sonar range */
    private final float range;
    /** The state of the sensor */
    private final SystemPositionAndAttitude state;

    /** The image created from data
     * -- GETTER --
     *
     * @return the image
     */
    @Getter
    private BufferedImage image;
    /** Holds information if the image has slant correction */
        private boolean imageWithSlantCorrection = false;
    /** The sonar data
     * -- GETTER --
     *
     * @return the data
     */
    @Getter @Setter
    private double[] data;

    /**
     * Initializes the sidescan line
     *
     * @param timestamp The timestamp.
     * @param range     The range.
     * @param state     the sensor state (see {@link SystemPositionAndAttitude}).
     * @param frequency The sonar frequency.
     * @param data      The array with collected data.
     */
    public SidescanLine(long timestamp, float range, SystemPositionAndAttitude state, float frequency, double[] data) {
        super();
        this.timestampMillis = timestamp;
        this.xSize = data.length;
        this.range = range;
        this.state = state;
        this.data = data;
        this.frequency = frequency;
    }

    /**
     * @return the timestampMillis
     */
    @Override
    public long getTimestampMillis() {
        return timestampMillis;
    }

    /**
     * @return the range
     */
    @Override
    public float getRange() {
        return range;
    }

    /**
     * @return the state
     */
    @Override
    public SystemPositionAndAttitude getState() {
        return state;
    }

    /**
     * @return the xSize
     */
    public int getXSize() {
        return xSize;
    }

    /**
     * @return the ySize
     */
    @Override
    public int getYSize() {
        return ySize;
    }

    /**
     * @param ySize the ySize to set
     */
    @Override
    public void setYSize(int ySize) {
        this.ySize = ySize;
    }

    /**
     * @return the yPos
     */
    @Override
    public int getYPos() {
        return yPos;
    }

    /**
     * @param yPos the yPos to set
     */
    @Override
    public void setYPos(int yPos) {
        this.yPos = yPos;
    }

    /**
     * @param image the image to set
     */
    public void setImage(BufferedImage image, boolean slantCorrected) {
        this.image = image;
        this.imageWithSlantCorrection = slantCorrected;
    }

    public void drawSlantedImage(ColorMap cmap) {
        if (image == null) {
            image = new BufferedImage(xSize, 1, BufferedImage.TYPE_INT_RGB);
            drawSlantedImage(cmap, image);
        }
        imageWithSlantCorrection = false;
    }

    public void drawSlantedImage(ColorMap cmap, BufferedImage image) {
        for (int i = 0; i < xSize; i++) {
            Color color = cmap.getColor(data[i]);
            image.setRGB(i, 0, color.getRGB());
        }
    }

    /**
     * @return the imageWithSlantCorrection
     */
    @Override
    public boolean isImageWithSlantCorrection() {
        return imageWithSlantCorrection;
    }

    @Override
    public void setImageWithSlantCorrection(boolean imageWithSlantCorrection) {
        this.imageWithSlantCorrection = imageWithSlantCorrection;
    }

    @Override
    public int compareTo(SidescanLine o) {
        return Long.compare(timestampMillis, o.timestampMillis);
    }

    public void applyEGN(SidescanHistogramNormalizer egnNormalization, int subsystem) {
        if (egnNormalization != null) {
            data = egnNormalization.normalize(data, subsystem);
        }
    }

}
