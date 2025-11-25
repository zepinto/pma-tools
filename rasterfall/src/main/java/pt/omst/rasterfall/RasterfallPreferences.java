//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.util.prefs.Preferences;

import lombok.extern.slf4j.Slf4j;

/**
 * Centralized preferences for RasterFall application.
 * Manages rendering quality and other application-specific settings.
 */
@Slf4j
public class RasterfallPreferences {
    
    private static final String PREF_RENDER_QUALITY = "render.quality";
    private static final Preferences prefs = Preferences.userNodeForPackage(RasterfallPreferences.class);
    
    /**
     * Gets the rendering quality preference.
     * @return true for quality (slower), false for speed (faster)
     */
    public static boolean isRenderQuality() {
        return prefs.getBoolean(PREF_RENDER_QUALITY, true); // default to quality
    }
    
    /**
     * Sets the rendering quality preference.
     * @param quality true for quality (slower), false for speed (faster)
     */
    public static void setRenderQuality(boolean quality) {
        prefs.putBoolean(PREF_RENDER_QUALITY, quality);
        log.info("Rendering quality preference set to: {}", quality ? "Quality" : "Speed");
        try {
            prefs.flush();
        } catch (Exception e) {
            log.error("Error flushing preferences", e);
        }
    }
}
