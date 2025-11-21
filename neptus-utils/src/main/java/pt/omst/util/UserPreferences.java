//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.util;

import java.util.prefs.Preferences;

import lombok.extern.slf4j.Slf4j;

/**
 * Centralized user preferences for OMST applications.
 * Provides access to common user settings like username that are shared across applications.
 */
@Slf4j
public class UserPreferences {
    
    private static final String PREF_USERNAME = "user.name";
    private static final Preferences prefs = Preferences.userNodeForPackage(UserPreferences.class);
    
    /**
     * Gets the configured username, falling back to system username if not set.
     * @return The username to use for annotations and observations
     */
    public static String getUsername() {
        String username = prefs.get(PREF_USERNAME, null);
        if (username == null || username.trim().isEmpty()) {
            username = System.getProperty("user.name", "unknown");
        }
        return username;
    }
    
    /**
     * Sets the username preference.
     * @param username The username to save, or null to clear and use system default
     */
    public static void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            prefs.remove(PREF_USERNAME);
            log.debug("Cleared username preference, will use system default");
        } else {
            prefs.put(PREF_USERNAME, username.trim());
            log.debug("Saved username preference: {}", username);
        }
        try {
            prefs.flush();
        } catch (Exception e) {
            log.error("Error flushing preferences", e);
        }
    }
}
