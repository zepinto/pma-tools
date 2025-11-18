//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.lsts.neptus.colormap;

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

    public double getValue(Color color) {
        // Reverse the formula: blue = 255 * value^3
        // Therefore: value = (blue / 255)^(1/3)
        double blueNormalized = color.getBlue() / 255.0;
        double estimatedValue = Math.cbrt(blueNormalized);
        
        // Clamp to valid range
        if (estimatedValue < 0) estimatedValue = 0;
        if (estimatedValue > 1) estimatedValue = 1;
        
        // Find the closest cache index
        int estimatedIndex = (int) Math.round(estimatedValue * cacheSize);
        if (estimatedIndex >= cacheSize) estimatedIndex = cacheSize - 1;
        if (estimatedIndex < 0) estimatedIndex = 0;
        
        // Refine by checking a few neighbors for best RGB match
        int closestIndex = estimatedIndex;
        double minDistance = Double.MAX_VALUE;
        int searchRadius = 5; // Small radius since estimation is accurate
        
        int searchStart = Math.max(0, estimatedIndex - searchRadius);
        int searchEnd = Math.min(cacheSize - 1, estimatedIndex + searchRadius);
        
        for (int i = searchStart; i <= searchEnd; i++) {
            Color cached = cache.get(i);
            int dr = cached.getRed() - color.getRed();
            int dg = cached.getGreen() - color.getGreen();
            int db = cached.getBlue() - color.getBlue();
            double distance = dr * dr + dg * dg + db * db;
            
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
                
                // Early exit if exact match
                if (distance == 0) break;
            }
        }
        
        return closestIndex / (double) cacheSize;
    }

    @Override
    public String toString() {
        return "Bronze";
    }
}
