//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.prefs.Preferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized preferences for RasterFall application.
 * Manages rendering quality and other application-specific settings.
 * 
 * Mission-specific settings (campaign, system, contact prefix, contact size)
 * are stored in a JSON file in the rasterIndex folder for each mission.
 */
@Slf4j
public class RasterfallPreferences {

    private static final String PREF_RENDER_QUALITY = "render.quality";
    public static final String PREF_CONTACT_NAME = "contact.name";
    public static final String PREF_CAMPAIGN_NAME = "campaign.name";
    public static final String PREF_SYSTEM_NAME = "system.name";
    public static String PREF_CONTACT_SIZE = "contact.size";
    
    private static final String MISSION_PREFS_FILENAME = "mission-preferences.txt";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Preferences prefs = Preferences.userNodeForPackage(RasterfallPreferences.class);
    
    // Current mission folder (rasterIndex folder)
    private static File currentMissionFolder = null;
    // Cached mission preferences
    private static MissionPrefs currentMissionPrefs = null;

    /**
     * Mission-specific preferences stored in JSON file.
     */
    @Data
    public static class MissionPrefs {
        private String campaignName = "campaign";
        private String systemName = "system";
        private String contactName = "contact";
        private float contactSize = 3.0f;
    }

    /**
     * Sets the current mission folder and loads preferences from it.
     * @param rasterIndexFolder The rasterIndex folder for the current mission
     */
    public static void setCurrentMissionFolder(File rasterIndexFolder) {
        currentMissionFolder = rasterIndexFolder;
        currentMissionPrefs = loadMissionPrefs(rasterIndexFolder);
        log.info("Loaded mission preferences from: {}", rasterIndexFolder);
    }

    /**
     * Gets the current mission folder.
     * @return The current rasterIndex folder, or null if not set
     */
    public static File getCurrentMissionFolder() {
        return currentMissionFolder;
    }

    /**
     * Loads mission preferences from a JSON file in the given folder.
     * @param folder The rasterIndex folder
     * @return The loaded preferences, or defaults if file doesn't exist
     */
    public static MissionPrefs loadMissionPrefs(File folder) {
        if (folder == null) {
            return new MissionPrefs();
        }
        
        // Get the default campaign name from parent folder
        String defaultCampaignName = "campaign";
        File missionFolder = folder.getParentFile();
        if (missionFolder != null) {
            defaultCampaignName = missionFolder.getName();
        }
        
        File prefsFile = new File(folder, MISSION_PREFS_FILENAME);
        if (prefsFile.exists()) {
            try {
                String json = Files.readString(prefsFile.toPath());
                MissionPrefs loaded = objectMapper.readValue(json, MissionPrefs.class);
                log.info("Loaded mission preferences from {}", prefsFile.getAbsolutePath());
                // If campaign name is still the generic default, update to folder name
                if ("campaign".equals(loaded.getCampaignName())) {
                    loaded.setCampaignName(defaultCampaignName);
                }
                return loaded;
            } catch (IOException e) {
                log.warn("Error loading mission preferences from {}: {}", prefsFile.getAbsolutePath(), e.getMessage());
            }
        }
        
        // Create new preferences with folder name as default campaign name
        MissionPrefs defaults = new MissionPrefs();
        defaults.setCampaignName(defaultCampaignName);
        return defaults;
    }

    /**
     * Saves mission preferences to a JSON file in the given folder.
     * @param folder The rasterIndex folder
     * @param missionPrefs The preferences to save
     */
    public static void saveMissionPrefs(File folder, MissionPrefs missionPrefs) {
        if (folder == null) {
            log.warn("Cannot save mission preferences: folder is null");
            return;
        }
        
        File prefsFile = new File(folder, MISSION_PREFS_FILENAME);
        try {
            String json = objectMapper.writeValueAsString(missionPrefs);
            Files.writeString(prefsFile.toPath(), json);
            log.info("Saved mission preferences to {}", prefsFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Error saving mission preferences to {}: {}", prefsFile.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Saves the current mission preferences to the current mission folder.
     */
    public static void saveCurrentMissionPrefs() {
        if (currentMissionFolder != null && currentMissionPrefs != null) {
            saveMissionPrefs(currentMissionFolder, currentMissionPrefs);
        }
    }

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
     * Uses mission-specific preferences if available, otherwise falls back to global preferences.
     * @return The contact name prefix.
     */
    public static String getContactName() {
        if (currentMissionPrefs != null) {
            return currentMissionPrefs.getContactName();
        }
        return prefs.get(PREF_CONTACT_NAME, "contact");
    }

    public static void setContactName(String name) {
        if (currentMissionPrefs != null) {
            currentMissionPrefs.setContactName(name);
            saveCurrentMissionPrefs();
        } else {
            prefs.put(PREF_CONTACT_NAME, name);
            try {
                prefs.flush();
            } catch (Exception e) {
                log.error("Error flushing preferences", e);
            }
        }
    }

    /**
     * Get the campaign name preference. This will be the campaign name stored in the contacts 
     * Uses mission-specific preferences if available, otherwise falls back to global preferences.
     * @return The campaign name.
     */
    public static String getCampaignName() {
        if (currentMissionPrefs != null) {
            return currentMissionPrefs.getCampaignName();
        }
        return prefs.get(PREF_CAMPAIGN_NAME, "campaign");
    }

    public static void setCampaignName(String name) {
        if (currentMissionPrefs != null) {
            currentMissionPrefs.setCampaignName(name);
            saveCurrentMissionPrefs();
        } else {
            prefs.put(PREF_CAMPAIGN_NAME, name);
            try {
                prefs.flush();
            } catch (Exception e) {
                log.error("Error flushing preferences", e);
            }
        }
    }

    /**
     * Get the system name preference. This will be the system name stored in the contacts
     * Uses mission-specific preferences if available, otherwise falls back to global preferences.
     * @return The system name.
     */
    public static String getSystemName() {
        if (currentMissionPrefs != null) {
            return currentMissionPrefs.getSystemName();
        }
        return prefs.get(PREF_SYSTEM_NAME, "system");
    }

    public static void setSystemName(String name) {
        if (currentMissionPrefs != null) {
            currentMissionPrefs.setSystemName(name);
            saveCurrentMissionPrefs();
        } else {
            prefs.put(PREF_SYSTEM_NAME, name);
            try {
                prefs.flush();
            } catch (Exception e) {
                log.error("Error flushing preferences", e);
            }
        }
    }

    /**
     * Get the contact size preference, in meters.
     * Uses mission-specific preferences if available, otherwise falls back to global preferences.
     * @return The contact size.
     */
    public static float getContactSize() {
        if (currentMissionPrefs != null) {
            return currentMissionPrefs.getContactSize();
        }
        return prefs.getFloat(PREF_CONTACT_SIZE, 3.0f);
    }

    public static void setContactSize(float size) {
        if (currentMissionPrefs != null) {
            currentMissionPrefs.setContactSize(size);
            saveCurrentMissionPrefs();
        } else {
            prefs.putFloat(PREF_CONTACT_SIZE, size);
            try {
                prefs.flush();
            } catch (Exception e) {
                log.error("Error flushing preferences", e);
            }
        }
    }
}
