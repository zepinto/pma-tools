package pt.omst.neptus.colormap;

import java.awt.Color;
import java.util.LinkedHashMap;

public class BronzeColorMap implements ColorMap {

    private final LinkedHashMap<Integer, Color> cache = new LinkedHashMap<>();
    private final int cacheSize = 1000;

    public BronzeColorMap() {
        
        for (int i = 0; i < cacheSize; i++) {
            double value = i / (double) cacheSize;
            int red = (int) (245 * (0.25*Math.log(value) + 1));
            int green = (int) (255 * 1*value-1*(value*value)+1.75*(value*value*value));
            int blue = (int) (255 * value*value*value);
            red = Math.max(0, red);
            red = Math.min(255, red);
            green = Math.max(0, green);
            green = Math.min(255, green);
            blue = Math.max(0, blue);
            blue = Math.min(255, blue);
            cache.put(i, new Color(red, green, blue));
        }
    }

    @Override
    public Color getColor(double value) {
        int i = (int) (value * cacheSize);
        if (i < 0)
            i = 0;
        if (i >= cacheSize)
            i = cacheSize - 1;
        return cache.get(i);
    }

    @Override
    public String toString() {
        return "Bronze";
    }
}
