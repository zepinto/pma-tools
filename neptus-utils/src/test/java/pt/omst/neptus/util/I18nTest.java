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

import org.junit.jupiter.api.Test;

import pt.lsts.neptus.util.I18n;

import org.junit.jupiter.api.BeforeEach;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for I18nManager and I18n utility classes.
 * 
 * @author OceanScan-MST
 */
class I18nTest {
    
    @BeforeEach
    void setUp() {
        // Reset to default locale before each test
        I18n.setLocale(Locale.ENGLISH);
        I18n.clearCache();
    }
    
    @Test
    void testBasicStringRetrieval() {
        String okButton = I18n.text("button.ok");
        assertEquals("OK", okButton);
        
        String cancelButton = I18n.text("button.cancel");
        assertEquals("Cancel", cancelButton);
    }
    
    @Test
    void testPortugueseLocale() {
        I18n.setLocale(new Locale("pt"));
        
        String cancelButton = I18n.text("button.cancel");
        assertEquals("Cancelar", cancelButton);
        
        String fileMenu = I18n.text("menu.file");
        assertEquals("Ficheiro", fileMenu);
    }
    
    @Test
    void testParameterSubstitution() {
        String message = I18n.text("message.error.file.notfound", "test.txt");
        assertEquals("File not found: test.txt", message);
        
        String aboutTitle = I18n.text("dialog.title.about", "MyApp");
        assertEquals("About MyApp", aboutTitle);
    }
    
    @Test
    void testMissingKey() {
        String missing = I18n.text("nonexistent.key");
        assertEquals("!nonexistent.key!", missing);
    }
    
    @Test
    void testFallback() {
        String fallback = I18n.textOrDefault("nonexistent.key", "Default Value");
        assertEquals("Default Value", fallback);
        
        String existing = I18n.textOrDefault("button.ok", "Should not see this");
        assertEquals("OK", existing);
    }
    
    @Test
    void testHasKey() {
        assertTrue(I18n.hasText("button.ok"));
        assertFalse(I18n.hasText("nonexistent.key"));
    }
    
    @Test
    void testLocaleSwitch() {
        assertEquals("Cancel", I18n.text("button.cancel"));
        
        I18n.setLocale(new Locale("pt"));
        assertEquals("Cancelar", I18n.text("button.cancel"));
        
        I18n.setLocale(Locale.ENGLISH);
        assertEquals("Cancel", I18n.text("button.cancel"));
    }
}