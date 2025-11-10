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
 * Author:
 * 20??/??/??
 */

package pt.omst.neptus.colormap;

import pt.omst.neptus.util.I18n;

import java.awt.Color;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Vector;

public class ColorMapFactory {
    public static Vector<String> colorMapNamesList = new Vector<>();
    private static InterpolationColorMap storeDataColormap = null;
    private static InterpolationColorMap grayscale = null;
    private static InterpolationColorMap white = null;
    private static InterpolationColorMap hot = null;
    private static InterpolationColorMap allColors = null;
    private static InterpolationColorMap rgb = null;
    private static InterpolationColorMap bluered = null;
    private static InterpolationColorMap autumn = null;
    private static InterpolationColorMap cool = null;
    private static InterpolationColorMap jet = null;
    private static InterpolationColorMap spring = null;
    private static InterpolationColorMap bone = null;
    private static InterpolationColorMap copper = null;
    private static ColorMap summer = null;
    private static InterpolationColorMap winter = null;
    private static InterpolationColorMap pink = null;
    private static InterpolationColorMap greenradar = null;
    private static InterpolationColorMap redYellowGreen = null;
    private static InterpolationColorMap rainbow = null;
    private static InterpolationColorMap bronze = null;

    static {
        colorMapNamesList.add("Autumn");
        colorMapNamesList.add("Bone");
        colorMapNamesList.add("Cool");
        colorMapNamesList.add("Copper");
        colorMapNamesList.add("Gray");
        colorMapNamesList.add("Hot");
        colorMapNamesList.add("Jet");
        colorMapNamesList.add("Pink");
        colorMapNamesList.add("Spring");
        colorMapNamesList.add("Summer");
        colorMapNamesList.add("Winter");
        colorMapNamesList.add("AllColors");
        colorMapNamesList.add("RedGreenBlue");
        colorMapNamesList.add("BlueToRed");
        colorMapNamesList.add("White");
        colorMapNamesList.add("Rainbow");
        colorMapNamesList.add("Bronze");
        colorMapNamesList.add("StoreData");

        Collections.sort(colorMapNamesList);
    }

    private ColorMapFactory() {
    }

    public static ColorMap getColorMapByName(String name) {
        if ("autumn".equalsIgnoreCase(name))
            return createAutumnColorMap();
        else if ("bone".equalsIgnoreCase(name))
            return createBoneColorMap();
        else if ("cool".equalsIgnoreCase(name))
            return createCoolColorMap();
        else if ("copper".equalsIgnoreCase(name))
            return createCopperColorMap();
        else if ("gray".equalsIgnoreCase(name))
            return createGrayScaleColorMap();
        else if ("hot".equalsIgnoreCase(name))
            return createHotColorMap();
        else if ("jet".equalsIgnoreCase(name))
            return createJetColorMap();
        else if ("pink".equalsIgnoreCase(name))
            return createPinkColorMap();
        else if ("spring".equalsIgnoreCase(name))
            return createSpringColorMap();
        else if ("summer".equalsIgnoreCase(name))
            return createSummerColorMap();
        else if ("winter".equalsIgnoreCase(name))
            return createWinterColorMap();
        else if ("allColors".equalsIgnoreCase(name))
            return createAllColorsColorMap();
        else if ("redGreenBlue".equalsIgnoreCase(name))
            return createRedGreenBlueColorMap();
        else if ("blueToRed".equalsIgnoreCase(name))
            return createRedGreenBlueColorMap();
        else if ("white".equalsIgnoreCase(name))
            return createWhiteColorMap();
        else if ("greenRadar".equalsIgnoreCase(name))
            return createGreenRadarColorMap();
        else if ("rainbow".equalsIgnoreCase(name))
            return createRainbowColormap();
        else if ("redYellowGreen".equalsIgnoreCase(name))
            return createRedYellowGreenColorMap();
        else if ("bronze".equalsIgnoreCase(name))
            return createBronzeColormap();

        else {
            for (int i = 0; i < ColorMap.cmaps.length; i++) {
                if (ColorMap.cmaps[i].toString().equalsIgnoreCase(name))
                    return ColorMap.cmaps[i];
            }
        }
        return createJetColorMap();
    }

    public static InterpolationColorMap createAutumnColorMap() {
        if (autumn == null)
            autumn = new InterpolationColorMap(I18n.text("colormap.autumn"),
                    new double[]{0.0, 1.0}, new Color[]{Color.RED, Color.YELLOW});
        return autumn;
    }

    public static InterpolationColorMap createBoneColorMap() {
        if (bone == null)
            bone = new InterpolationColorMap(I18n.text("colormap.bone"),
                    new double[]{0.0, 0.375, 0.75, 1.0},
                    new Color[]{new Color(0, 0, 1), new Color(81, 81, 113), new Color(166, 198, 198), Color.white});
        return bone;
    }

    public static InterpolationColorMap createCoolColorMap() {
        if (cool == null)
            cool = new InterpolationColorMap(I18n.text("colormap.cool"),
                    new double[]{0.0, 1.0}, new Color[]{Color.CYAN, Color.MAGENTA});
        return cool;
    }

    public static InterpolationColorMap createCopperColorMap() {
        if (copper == null)
            copper = new InterpolationColorMap(I18n.text("colormap.copper"),
                    new double[]{0.0, 0.7869, 0.8125, 1.0}, new Color[]{Color.black, new Color(253, 158, 100),
                    new Color(255, 161, 103), new Color(255, 199, 127)});
        return copper;
    }

    public static InterpolationColorMap createGrayScaleColorMap() {
        if (grayscale == null)
            grayscale = new InterpolationColorMap(I18n.text("colormap.gray"),
                    new double[]{0.0, 1.0}, new Color[]{new Color(0, 0, 0), new Color(255, 255, 255)});

        return grayscale;
    }

    public static InterpolationColorMap createHotColorMap() {
        if (hot == null) {
            InputStreamReader isr = new InputStreamReader(
                    ClassLoader.getSystemResourceAsStream("colormaps/hot.colormap"));
            try {
                hot = new TabulatedColorMap(isr);
            } catch (Exception e) {
                hot = new InterpolationColorMap(I18n.text("colormap.hot"),
                        new double[]{0.0, 0.3333333, 0.66666666, 1.0},
                        new Color[]{Color.BLACK, Color.RED, Color.YELLOW, Color.WHITE});
            }
        }
        return hot;
    }

    public static ColorMap createJetColorMap() {
        if (jet == null) {
            try {
                InputStreamReader isr = new InputStreamReader(
                        ClassLoader.getSystemResourceAsStream("colormaps/jet.colormap"));
                jet = new TabulatedColorMap(isr);
            } catch (Exception e) {
                jet = new InterpolationColorMap(I18n.text("colormap.jet"),
                        new double[]{0.0, 0.25, 0.5, 0.75, 1.0},
                        new Color[]{Color.blue, Color.cyan, Color.yellow, Color.red, new Color(128, 0, 0)});
            }
        }

        return jet;
    }

    public static InterpolationColorMap createPinkColorMap() {
        if (pink == null)
            pink = new InterpolationColorMap(I18n.text("colormap.pink"),
                    new double[]{0.0, 1 / 64.0, 2 / 64.0, 3 / 64.0, 24 / 64.0, 48 / 64.0, 1.0},
                    new Color[]{new Color(30, 0, 0), new Color(50, 26, 26), new Color(64, 37, 37),
                            new Color(75, 45, 45), new Color(194, 126, 126), new Color(232, 232, 180),
                            new Color(255, 255, 255)});
        return pink;
    }

    public static InterpolationColorMap createSpringColorMap() {
        if (spring == null)
            spring = new InterpolationColorMap(I18n.text("colormap.spring"),
                    new double[]{0.0, 1.0}, new Color[]{Color.magenta, Color.yellow});
        return spring;
    }

    public static ColorMap createSummerColorMap() {
        if (summer == null)
            summer = new InterpolationColorMap(I18n.text("colormap.summer"),
                    new double[]{0.0, 1.0}, new Color[]{new Color(0, 128, 102), new Color(255, 255, 102)});
        return summer;
    }

    public static InterpolationColorMap createWinterColorMap() {
        if (winter == null)
            winter = new InterpolationColorMap(I18n.text("colormap.winter"),
                    new double[]{0.0, 1.0}, new Color[]{Color.blue, new Color(0, 255, 128)});
        return winter;
    }

    public static InterpolationColorMap createAllColorsColorMap() {
        if (allColors == null)
            allColors = new InterpolationColorMap(I18n.text("colormap.allcolors"),
                    new double[]{0.0, 0.3333333, 0.66666666, 1.0},
                    new Color[]{Color.BLACK, Color.BLUE, Color.YELLOW, Color.WHITE});
        return allColors;
    }

    public static InterpolationColorMap createRedGreenBlueColorMap() {
        if (rgb == null)
            rgb = new InterpolationColorMap(I18n.text("colormap.rgb"), new double[]{0.0, 0.5, 1.0}, new Color[]{
                    new Color(255, 0, 0), new Color(0, 255, 0), new Color(0, 0, 255)});
        return rgb;
    }

    public static InterpolationColorMap createWhiteColorMap() {
        if (white == null)
            white = new InterpolationColorMap(I18n.text("colormap.white"),
                    new double[]{0.0, 1.0}, new Color[]{Color.white, Color.white});
        return white;
    }

    public static InterpolationColorMap createGreenRadarColorMap() {
        if (greenradar == null)
            greenradar = new InterpolationColorMap(I18n.text("colormap.greenradar"),
                    new double[]{0.0, 1.0}, new Color[]{Color.black, Color.green});
        return greenradar;
    }

    public static InterpolationColorMap createRainbowColormap() {
        if (rainbow == null)
            rainbow = new InterpolationColorMap(I18n.text("colormap.rainbow"),
                    new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 1.0},
                    new Color[]{Color.magenta.darker().darker().darker(), Color.magenta, Color.blue,
                            Color.cyan, Color.green, Color.yellow, Color.orange, Color.red,
                            Color.red.darker().darker().darker()});
        return rainbow;
    }

    public static InterpolationColorMap createRedYellowGreenColorMap() {
        if (redYellowGreen == null)
            redYellowGreen = new InterpolationColorMap(I18n.text("colormap.redyellowgreen"),
                    new double[]{0.0, 0.5, 1.0}, new Color[]{Color.red, Color.yellow, Color.green});
        return redYellowGreen;
    }

    public static ColorMap createBronzeColormap() {
        if (bronze == null) {
            return new BronzeColorMap();
        }
        return bronze;
    }

    public static InterpolationColorMap createStoreDataColormap() {
        if (storeDataColormap == null)
            storeDataColormap = new InterpolationColorMap(I18n.text("colormap.storedata"), new double[]{0.0,
                    0.333333, 0.666666, 1.0}, new Color[]{new Color(0, 0, 0), new Color(255, 0, 0),
                    new Color(255, 255, 0), new Color(255, 255, 255)});
        return storeDataColormap;
    }

    public static InterpolationColorMap createBlueToRedColorMap() {
        if (bluered == null)
            bluered = new InterpolationColorMap(I18n.text("colormap.bluetored"),
                    new double[]{0.0, 1.0}, new Color[]{Color.BLUE, Color.RED});
        return bluered;
    }

    public static InterpolationColorMap createInvertedColorMap(InterpolationColorMap original) {
        double[] inv = new double[original.getValues().length];
        Color[] colors = new Color[inv.length];

        int j = inv.length;
        for (int i = 0; i < inv.length; i++) {
            inv[i] = original.getValues()[i];
            colors[i] = original.getColor(Math.max(0.001, original.getValues()[--j]));
        }

        return new InterpolationColorMap(I18n.text("colormap.inverted") + " " + original.toString(), inv, colors);
    }
}
