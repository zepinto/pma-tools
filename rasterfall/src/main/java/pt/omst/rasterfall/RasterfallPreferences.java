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
    public static final String PREF_CONTACT_NAME = "contact.name";
    public static final String PREF_CAMPAIGN_NAME = "campaign.name";
    public static final String PREF_SYSTEM_NAME = "system.name";
    public static String PREF_CONTACT_SIZE = "contact.size";

    private static final Preferences prefs = Preferences.userNodeForPackage(RasterfallPreferences.class);

    /**
     * Gets the rendering quality preference.
     * 
     * @return true for quality (slower), false for speed (faster)
     */
    public static boolean isRenderQuality() {
        return prefs.getBoolean(PREF_RENDER_QUALITY, true); // default to quality
    }

    /**
     * Sets the rendering quality preference.
     * 
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

    /**
     * Get the contact name preference. This is the prefix used when creating new contacts.
     * @return The contact name prefix.
     */
    public static String getContactName() {
        return prefs.get(PREF_CONTACT_NAME, "contact");
    }

    public static void setContactName(String name) {
        prefs.put(PREF_CONTACT_NAME, name);
        try {
            prefs.flush();
        } catch (Exception e) {
            log.error("Error flushing preferences", e);
        }
    }

    /**
     * Get the campaign name preference. This will be the campaign name stored in the contacts 
     * @return The campaign name.
     */
    public static String getCampaignName() {
        return prefs.get(PREF_CAMPAIGN_NAME, "campaign");
    }

    public static void setCampaignName(String name) {
        prefs.put(PREF_CAMPAIGN_NAME, name);
        try {
            prefs.flush();
        } catch (Exception e) {
            log.error("Error flushing preferences", e);
        }
    }

    /**
     * Get the system name preference. This will be the system name stored in the contacts
     * @return The system name.
     */
    public static String getSystemName() {
        return prefs.get(PREF_SYSTEM_NAME, "system");
    }

    public static void setSystemName(String name) {
        prefs.put(PREF_SYSTEM_NAME, name);
        try {
            prefs.flush();
        } catch (Exception e) {
            log.error("Error flushing preferences", e);
        }
    }

    /**
     * Get the contact size preference, in meters.
     * @return The contact size.
     */
    public static float getContactSize() {
        return prefs.getInt(PREF_CONTACT_SIZE, 3);
    }

    public static void setContactSize(float size) {
        prefs.putFloat(PREF_CONTACT_SIZE, size);
        try {
            prefs.flush();
        } catch (Exception e) {
            log.error("Error flushing preferences", e);
        }
    }
}
