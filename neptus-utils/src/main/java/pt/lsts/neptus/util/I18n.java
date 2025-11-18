//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
/*
 * Copyright (c) 2004-2025 OceanScan-MST
 * All rights reserved.
 *
 * This file is part of Neptus Utilities.
 */

package pt.lsts.neptus.util;

import java.util.Locale;

/**
 * Static utility class for easy access to internationalization features.
 * Provides convenient static methods that delegate to the global I18nManager instance.
 * 
 * @author OceanScan-MST
 */
public final class I18n {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private I18n() {
        // Utility class
    }
    
    /**
     * Gets a localized string for the given key.
     * 
     * @param key the resource key
     * @return the localized string
     */
    public static String text(String key) {
        return I18nManager.getInstance().getString(key);
    }
    
    /**
     * Gets a localized string for the given key with parameter substitution.
     * 
     * @param key the resource key
     * @param args the arguments for parameter substitution
     * @return the formatted localized string
     */
    public static String text(String key, Object... args) {
        return I18nManager.getInstance().getString(key, args);
    }
    
    /**
     * Gets a localized string with a fallback value if the key is not found.
     * 
     * @param key the resource key
     * @param fallback the fallback value to use if key is not found
     * @return the localized string or fallback value
     */
    public static String textOrDefault(String key, String fallback) {
        return I18nManager.getInstance().getString(key, fallback);
    }
    
    /**
     * Checks if a resource key exists in the current bundle.
     * 
     * @param key the resource key to check
     * @return true if the key exists, false otherwise
     */
    public static boolean hasText(String key) {
        return I18nManager.getInstance().hasKey(key);
    }
    
    /**
     * Sets the current locale for internationalization.
     * 
     * @param locale the new locale to use
     */
    public static void setLocale(Locale locale) {
        I18nManager.getInstance().setLocale(locale);
    }
    
    /**
     * Gets the current locale.
     * 
     * @return the current locale
     */
    public static Locale getLocale() {
        return I18nManager.getInstance().getCurrentLocale();
    }
    
    /**
     * Sets the bundle name to use for localization.
     * 
     * @param bundleName the bundle name (without .properties extension)
     */
    public static void setBundleName(String bundleName) {
        I18nManager.getInstance().setBundleName(bundleName);
    }
    
    /**
     * Gets available locales for the current bundle.
     * 
     * @return array of available locales
     */
    public static Locale[] getAvailableLocales() {
        return I18nManager.getInstance().getAvailableLocales();
    }
    
    /**
     * Clears the bundle cache.
     */
    public static void clearCache() {
        I18nManager.getInstance().clearCache();
    }
}