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
package pt.lsts.s57.resources.entities.s52;

import java.awt.Color;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class S52Color implements Serializable {

    private static final long serialVersionUID = 1L;

    protected class ColorValue implements Serializable {
        private static final long serialVersionUID = 7880434047283615759L;
        protected final ColorScheme scheme;
        protected final float x, y, lum;
        protected final Color color;

        protected ColorValue(ColorScheme type, float x, float y, float lum) {
            this.scheme = type;
            this.x = (x > 1.0f) ? 1.0f : ((x < 0) ? 0 : x);
            this.y = (y > 1.0f) ? 1.0f : ((y < 0) ? 0 : y);
            this.lum = (lum > 100) ? 1 : ((lum < 0) ? 0 : lum);

            // calculations to convert Yxy to RGB
            // Yxy to XYZ
            float x2, y2, z2;
            if (this.y == 0) {
                x2 = y2 = z2 = 0;
            }
            else {
                x2 = lum * x / y / 100;
                y2 = lum / 100;
                z2 = lum * (1 - x - y) / y / 100;
            }

            // XYZ to RGB
            float[] Clinear = new float[3];
            Clinear[0] = (float) (x2 * 3.2410 - y2 * 1.5374 - z2 * 0.4986); // red
            Clinear[1] = (float) (-x2 * 0.9692 + y2 * 1.8760 - z2 * 0.0416); // green
            Clinear[2] = (float) (x2 * 0.0556 - y2 * 0.2040 + z2 * 1.0570); // blue

            Clinear[0] = (Clinear[0] > 1.0f) ? 1.0f : ((Clinear[0] < 0) ? 0 : Clinear[0]);
            Clinear[1] = (Clinear[1] > 1.0f) ? 1.0f : ((Clinear[1] < 0) ? 0 : Clinear[1]);
            Clinear[2] = (Clinear[2] > 1.0f) ? 1.0f : ((Clinear[2] < 0) ? 0 : Clinear[2]);

            for (int i = 0; i < 3; i++) {
                Clinear[i] = (float) ((Clinear[i] <= 0.0031308) ? 12.92 * Clinear[i]
                        : (1 + 0.055) * Math.pow(Clinear[i], (1.0 / 2.4)) - 0.055);
            }

            int r = (int) (Clinear[0] * 255);
            int g = (int) (Clinear[1] * 255);
            int b = (int) (Clinear[2] * 255);
            color = new Color(r, g, b);
        }
    }

    private final Map<ColorScheme, ColorValue> records = new HashMap<ColorScheme, ColorValue>();
    private final String code;
    private final String name;

    public static S52Color forge(String code, Map<ColorScheme, String[]> schemes) {
        return new S52Color(code, schemes);
    }

    private S52Color(String code, Map<ColorScheme, String[]> schemes) {
        this.code = code;
        String nameTemp = "";
        for (Entry<ColorScheme, String[]> entry : schemes.entrySet()) {
            String[] value = entry.getValue();
            // System.out.println(code + " " +value[0] + " " + value[1] + " " +value[2] + " " + value[3]);
            records.put(entry.getKey(), new ColorValue(entry.getKey(), Float.valueOf(value[0]), Float.valueOf(value[1]),
                    Float.valueOf(value[2])));
            nameTemp = value[3];
        }
        this.name = nameTemp;
    }

    /**
     * Gets the color in the specified scheme {@link ColorScheme}
     * 
     * @param scheme
     * @return Color color
     */
    public Color get(ColorScheme scheme) {
        return records.get(scheme).color;
    }

    /**
     * Gets color in the default {@link ColorScheme} DAY_BRIGHT
     * 
     * @return
     */
    public Color get() {
        return records.get(ColorScheme.DAY_BRIGHT).color;
    }

    /**
     * @return the records
     */
    public Map<ColorScheme, ColorValue> getRecords() {
        return records;
    }

    /**
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return super.toString() + " :: " + getName() + ":" + getCode();
    }
}
