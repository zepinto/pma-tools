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

package pt.omst.neptus.util;

import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global internationalization manager for string localization.
 * Provides centralized access to localized strings using ResourceBundle.
 * 
 * @author OceanScan-MST
 */
@Slf4j
public class I18nManager {
    private static final String DEFAULT_BUNDLE_NAME = "messages";
    private static final String MISSING_KEY_PREFIX = "!";
    private static final String MISSING_KEY_SUFFIX = "!";
    
    private static volatile I18nManager instance;
    private static final Object lock = new Object();
    
    private final ConcurrentHashMap<String, ResourceBundle> bundleCache = new ConcurrentHashMap<>();
    private volatile Locale currentLocale;
    private volatile String bundleName;
    
    /**
     * Private constructor for singleton pattern.
     */
    private I18nManager() {
        this.currentLocale = Locale.getDefault();
        this.bundleName = DEFAULT_BUNDLE_NAME;
    }
    
    /**
     * Gets the singleton instance of I18nManager.
     * 
     * @return the I18nManager instance
     */
    public static I18nManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new I18nManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Sets the current locale and clears the bundle cache.
     * 
     * @param locale the new locale to use
     */
    public void setLocale(Locale locale) {
        if (locale == null) {
            log.warn("Attempted to set null locale, ignoring");
            return;
        }
        
        if (!locale.equals(this.currentLocale)) {
            this.currentLocale = locale;
            bundleCache.clear();
            log.info("Locale changed to: {}", locale);
        }
    }
    
    /**
     * Gets the current locale.
     * 
     * @return the current locale
     */
    public Locale getCurrentLocale() {
        return currentLocale;
    }
    
    /**
     * Sets the bundle name to use for localization.
     * 
     * @param bundleName the bundle name (without .properties extension)
     */
    public void setBundleName(String bundleName) {
        if (bundleName == null || bundleName.trim().isEmpty()) {
            log.warn("Attempted to set null or empty bundle name, ignoring");
            return;
        }
        
        if (!bundleName.equals(this.bundleName)) {
            this.bundleName = bundleName;
            bundleCache.clear();
            log.info("Bundle name changed to: {}", bundleName);
        }
    }
    
    /**
     * Gets a localized string for the given key.
     * 
     * @param key the resource key
     * @return the localized string, or a placeholder if key is not found
     */
    public String getString(String key) {
        if (key == null || key.trim().isEmpty()) {
            log.warn("Attempted to get string for null or empty key");
            return MISSING_KEY_PREFIX + "null" + MISSING_KEY_SUFFIX;
        }
        
        try {
            ResourceBundle bundle = getBundle();
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            log.warn("Missing resource key: {} for locale: {}", key, currentLocale);
            return MISSING_KEY_PREFIX + key + MISSING_KEY_SUFFIX;
        }
    }
    
    /**
     * Gets a localized string for the given key with parameter substitution.
     * 
     * @param key the resource key
     * @param args the arguments for parameter substitution
     * @return the formatted localized string
     */
    public String getString(String key, Object... args) {
        String pattern = getString(key);
        
        if (args == null || args.length == 0) {
            return pattern;
        }
        
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to format message for key '{}' with args: {}", key, args, e);
            return pattern;
        }
    }
    
    /**
     * Gets a localized string with a fallback value if the key is not found.
     * 
     * @param key the resource key
     * @param fallback the fallback value to use if key is not found
     * @return the localized string or fallback value
     */
    public String getString(String key, String fallback) {
        try {
            ResourceBundle bundle = getBundle();
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            log.debug("Using fallback for missing key: {}", key);
            return fallback != null ? fallback : key;
        }
    }
    
    /**
     * Checks if a resource key exists in the current bundle.
     * 
     * @param key the resource key to check
     * @return true if the key exists, false otherwise
     */
    public boolean hasKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        
        try {
            ResourceBundle bundle = getBundle();
            return bundle.containsKey(key);
        } catch (MissingResourceException e) {
            return false;
        }
    }
    
    /**
     * Gets the ResourceBundle for the current locale and bundle name.
     * Uses caching to improve performance.
     * 
     * @return the ResourceBundle
     * @throws MissingResourceException if the bundle cannot be found
     */
    private ResourceBundle getBundle() throws MissingResourceException {
        String cacheKey = bundleName + "_" + currentLocale.toString();
        
        return bundleCache.computeIfAbsent(cacheKey, k -> {
            try {
                return ResourceBundle.getBundle(bundleName, currentLocale);
            } catch (MissingResourceException e) {
                // Try with default locale as fallback
                log.warn("Bundle '{}' not found for locale '{}', trying default locale", bundleName, currentLocale);
                try {
                    return ResourceBundle.getBundle(bundleName, Locale.getDefault());
                } catch (MissingResourceException e2) {
                    // Try without any locale (just the base bundle)
                    log.warn("Bundle '{}' not found for default locale, trying base bundle", bundleName);
                    return ResourceBundle.getBundle(bundleName);
                }
            }
        });
    }
    
    /**
     * Clears the bundle cache. Useful when resource files are updated at runtime.
     */
    public void clearCache() {
        bundleCache.clear();
        log.info("Bundle cache cleared");
    }
    
    /**
     * Gets available locales for the current bundle.
     * Note: This is a basic implementation that tries common locales.
     * 
     * @return array of available locales
     */
    public Locale[] getAvailableLocales() {
        // This is a simplified implementation
        // In a real application, you might scan the classpath for available bundles
        Locale[] commonLocales = {
            Locale.ENGLISH,
            new Locale("pt"),  // Portuguese
            new Locale("es"),  // Spanish
            new Locale("fr"),  // French
            new Locale("de"),  // German
            new Locale("it"),  // Italian
        };
        
        return java.util.Arrays.stream(commonLocales)
            .filter(locale -> {
                try {
                    ResourceBundle.getBundle(bundleName, locale);
                    return true;
                } catch (MissingResourceException e) {
                    return false;
                }
            })
            .toArray(Locale[]::new);
    }
}