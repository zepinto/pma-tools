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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Vector;

public class InterpolationColorMap implements ColorMap {
    protected double[] values = new double[]{0f, 1f};
    protected Color[] colors = new Color[]{Color.BLACK, Color.WHITE};
    protected String name;

    public InterpolationColorMap(double[] values, Color[] colors) {
        this("Unknown", values, colors);
    }

    InterpolationColorMap(String name, double[] values, Color[] colors) {
        this.name = name;
        if (values.length != colors.length) {
            System.err.println("The values[] and colors[] sizes don't match!");
            return;
        }
        this.values = values;
        this.colors = colors;
    }

    InterpolationColorMap(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        String line;
        Vector<Color> colorsV = new Vector<>();

        while ((line = br.readLine()) != null) {
            if (line.charAt(0) == '#')
                continue;

            String[] parts = line.split("[ \t,]+");

            if (parts.length < 3)
                continue;
            int r = (int) (Double.parseDouble(parts[0]) * 255);
            int g = (int) (Double.parseDouble(parts[1]) * 255);
            int b = (int) (Double.parseDouble(parts[2]) * 255);

            colorsV.add(new Color(r, g, b));
        }

        this.colors = colorsV.toArray(new Color[0]);
        this.values = new double[colorsV.size()];
        for (int i = 0; i < values.length; i++)
            values[i] = (double) i / (double) (values.length - 1);
    }

    @Override
    public String toString() {
        return name;
    }

    public Color getColor(double value) {
        if (value >= values[values.length - 1])
            return colors[values.length - 1];

        if (value <= values[0])
            return colors[0];

        value = Math.min(value, values[values.length - 1]);
        value = Math.max(value, values[0]);

        int pos = 0;
        while (pos < values.length && value > values[pos])
            pos++;

        if (pos == 0)
            return colors[0];
        else if (pos == values.length)
            return colors[colors.length - 1];
        else
            return interpolate(values[pos - 1], colors[pos - 1], value, values[pos], colors[pos]);
    }

    private Color interpolate(double belowValue, Color belowColor, double value, double aboveValue, Color aboveColor) {
        double totalDist = aboveValue - belowValue;

        double aboveDist = (value - belowValue) / totalDist;
        double belowDist = (aboveValue - value) / totalDist;

        return new Color((int) (belowColor.getRed() * belowDist + aboveColor.getRed() * aboveDist),
                (int) (belowColor.getGreen() * belowDist + aboveColor.getGreen() * aboveDist),
                (int) (belowColor.getBlue() * belowDist + aboveColor.getBlue() * aboveDist));
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the values
     */
    public double[] getValues() {
        return values;
    }

    /**
     * @param values the values to set
     */
    public void setValues(double[] values) {
        this.values = values;
    }
}
