//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.services;

import java.time.Instant;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;
import pt.omst.contacts.browser.IconCache;
import pt.omst.contacts.browser.filtering.ContactFilterPanel;
import pt.omst.gui.DataSourceManagerPanel;
import pt.omst.gui.ZoomableTimeIntervalSelector;

/**
 * Service class responsible for managing preferences persistence for TargetManager.
 * Handles saving and loading of UI state including:
 * - Window layout (split pane divider locations, panel visibility)
 * - Filter selections (classifications, confidences, labels)
 * - Auto-refresh settings
 * - Icon size preferences
 * - Time range selections
 * - Data source configurations
 */
@Slf4j
public class TargetManagerPreferences {

    private final JSplitPane mainSplitPane;
    private final JSplitPane outerSplitPane;
    private final ContactFilterPanel filterPanel;
    private final ZoomableTimeIntervalSelector timeSelector;
    private final DataSourceManagerPanel dataSourceManager;
    private final ContactFileWatcherService fileWatcherService;
    
    // References to panel visibility state holders
    private final BooleanHolder eastPanelVisible;
    private final BooleanHolder westPanelVisible;
    
    // Callback for triggering UI updates
    private final Runnable updateVisibleContactsCallback;
    private final Runnable showContactDetailsCallback;
    private final Runnable hideContactDetailsCallback;
    private final Runnable showFiltersCallback;
    
    /**
     * Simple holder class for boolean values that can be updated by reference.
     */
    public static class BooleanHolder {
        private boolean value;
        
        public BooleanHolder(boolean initialValue) {
            this.value = initialValue;
        }
        
        public boolean get() {
            return value;
        }
        
        public void set(boolean value) {
            this.value = value;
        }
    }
    
    public TargetManagerPreferences(
            JSplitPane mainSplitPane,
            JSplitPane outerSplitPane,
            ContactFilterPanel filterPanel,
            ZoomableTimeIntervalSelector timeSelector,
            DataSourceManagerPanel dataSourceManager,
            ContactFileWatcherService fileWatcherService,
            BooleanHolder eastPanelVisible,
            BooleanHolder westPanelVisible,
            Runnable updateVisibleContactsCallback,
            Runnable showContactDetailsCallback,
            Runnable hideContactDetailsCallback,
            Runnable showFiltersCallback) {
        this.mainSplitPane = mainSplitPane;
        this.outerSplitPane = outerSplitPane;
        this.filterPanel = filterPanel;
        this.timeSelector = timeSelector;
        this.dataSourceManager = dataSourceManager;
        this.fileWatcherService = fileWatcherService;
        this.eastPanelVisible = eastPanelVisible;
        this.westPanelVisible = westPanelVisible;
        this.updateVisibleContactsCallback = updateVisibleContactsCallback;
        this.showContactDetailsCallback = showContactDetailsCallback;
        this.hideContactDetailsCallback = hideContactDetailsCallback;
        this.showFiltersCallback = showFiltersCallback;
    }
    
    /**
     * Saves all TargetManager preferences to the Java Preferences API.
     * Includes window layout, filters, auto-refresh settings, icon size, time selection, and data sources.
     */
    public void savePreferences() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());

        // Save main divider location
        int dividerLocation = mainSplitPane.getDividerLocation();
        prefs.putInt("mainSplitPane.dividerLocation", dividerLocation);
        log.debug("Saved divider location: {}", dividerLocation);

        // Save east panel visibility
        prefs.putBoolean("eastPanel.visible", eastPanelVisible.get());
        log.debug("Saved east panel visibility: {}", eastPanelVisible.get());

        // Save west panel state
        prefs.putBoolean("westPanel.visible", westPanelVisible.get());
        if (westPanelVisible.get()) {
            int westDivider = outerSplitPane.getDividerLocation();
            prefs.putInt("westPanel.dividerLocation", westDivider);
            log.debug("Saved west panel divider location: {}", westDivider);
        }
        log.debug("Saved west panel visibility: {}", westPanelVisible.get());

        // Save filter selections
        Set<String> classifications = filterPanel.getSelectedClassifications();
        Set<String> confidences = filterPanel.getSelectedConfidences();
        Set<String> labels = filterPanel.getSelectedLabels();
        prefs.put("filters.classifications", String.join(",", classifications));
        prefs.put("filters.confidence", String.join(",", confidences));
        prefs.put("filters.labels", String.join(",", labels));
        log.debug("Saved filter selections");

        // Save auto-refresh preference
        prefs.putBoolean("autoRefresh.enabled", fileWatcherService.isAutoRefreshEnabled());
        log.debug("Saved auto-refresh preference: {}", fileWatcherService.isAutoRefreshEnabled());

        // Save icon size preference
        int iconSize = IconCache.getInstance().getIconSize();
        prefs.putInt("iconSize", iconSize);
        log.debug("Saved icon size preference: {}", iconSize);

        // Save time selection
        Instant startTime = timeSelector.getSelectedStartTime();
        Instant endTime = timeSelector.getSelectedEndTime();
        if (startTime != null && endTime != null) {
            prefs.putLong("timeSelector.startTime", startTime.toEpochMilli());
            prefs.putLong("timeSelector.endTime", endTime.toEpochMilli());
            log.debug("Saved time selection: {} to {}", startTime, endTime);
        }

        try {
            dataSourceManager.saveToPreferences();
            // Force flush to ensure preferences are written to disk
            prefs.flush();
        } catch (Exception e) {
            log.error("Error saving preferences", e);
        }
    }

    /**
     * Loads all TargetManager preferences from the Java Preferences API.
     * Restores window layout, filters, auto-refresh settings, icon size, time selection, and data sources.
     * Most UI updates are deferred to the EDT to ensure proper timing.
     */
    public void loadPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());

        // Load main divider location (default to -1 which means use default)
        int dividerLocation = prefs.getInt("mainSplitPane.dividerLocation", -1);
        if (dividerLocation > 0) {
            // Defer setting divider location until after component is visible
            SwingUtilities.invokeLater(() -> {
                mainSplitPane.setDividerLocation(dividerLocation);
                log.debug("Loaded main divider location: {}", dividerLocation);
            });
        }

        // Load east panel visibility (default to true)
        boolean eastVisible = prefs.getBoolean("eastPanel.visible", true);
        log.debug("Loaded east panel visibility: {}", eastVisible);
        SwingUtilities.invokeLater(() -> {
            if (eastVisible && !eastPanelVisible.get()) {
                showContactDetailsCallback.run();
            } else if (!eastVisible && eastPanelVisible.get()) {
                hideContactDetailsCallback.run();
            }
        });

        // Load west panel visibility (default to false - collapsed)
        boolean westVisible = prefs.getBoolean("westPanel.visible", false);
        int westDivider = prefs.getInt("westPanel.dividerLocation", 300);
        log.debug("Loaded west panel visibility: {}, divider: {}", westVisible, westDivider);
        SwingUtilities.invokeLater(() -> {
            if (westVisible) {
                showFiltersCallback.run();
                if (westDivider > 0) {
                    outerSplitPane.setDividerLocation(westDivider);
                }
            }
        });

        // Load filter selections
        String classificationsStr = prefs.get("filters.classifications", "");
        String confidencesStr = prefs.get("filters.confidence", "");
        String labelsStr = prefs.get("filters.labels", "");

        SwingUtilities.invokeLater(() -> {
            if (!classificationsStr.isEmpty()) {
                Set<String> classifications = Set.of(classificationsStr.split(","));
                filterPanel.setSelectedClassifications(classifications);
                log.debug("Loaded classification filters: {}", classifications);
            }
            if (!confidencesStr.isEmpty()) {
                Set<String> confidences = Set.of(confidencesStr.split(","));
                filterPanel.setSelectedConfidences(confidences);
                log.debug("Loaded confidence filters: {}", confidences);
            }
            if (!labelsStr.isEmpty()) {
                Set<String> labels = Set.of(labelsStr.split(","));
                filterPanel.setSelectedLabels(labels);
                log.debug("Loaded label filters: {}", labels);
            }
        });

        // Load auto-refresh preference (default to true)
        boolean autoRefreshEnabled = prefs.getBoolean("autoRefresh.enabled", true);
        fileWatcherService.setAutoRefreshEnabled(autoRefreshEnabled);
        log.debug("Loaded auto-refresh preference: {}", autoRefreshEnabled);

        // Load icon size preference (default to 12)
        int iconSize = prefs.getInt("iconSize", 12);
        IconCache.getInstance().setIconSize(iconSize);
        log.debug("Loaded icon size preference: {}", iconSize);

        // Load time selection
        long startTimeMillis = prefs.getLong("timeSelector.startTime", -1);
        long endTimeMillis = prefs.getLong("timeSelector.endTime", -1);

        if (startTimeMillis > 0 && endTimeMillis > 0) {
            Instant startTime = Instant.ofEpochMilli(startTimeMillis);
            Instant endTime = Instant.ofEpochMilli(endTimeMillis);
            log.debug("Loaded time selection: {} to {}", startTime, endTime);
            SwingUtilities.invokeLater(() -> {
                timeSelector.setSelectedInterval(startTime, endTime);
                updateVisibleContactsCallback.run();
            });
        }

        SwingUtilities.invokeLater(() -> {
            updateVisibleContactsCallback.run();
            log.info("Applied contact filters on load");
        });

        try {
            log.info("Loading data sources from preferences...");
            dataSourceManager.loadFromPreferences();
            log.info("Data sources loaded. Current count: {}", dataSourceManager.getDataSources().size());
        } catch (Exception e) {
            log.error("Error loading data sources from preferences", e);
        }
    }
}
