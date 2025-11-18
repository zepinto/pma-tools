//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

import javax.swing.Icon;
import javax.swing.UIManager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.I18n;

/**
 * A data source representing a Pulvis server connection.
 */
@Slf4j
public class PulvisConnection implements DataSource {
    
    @Getter
    private final String host;
    
    @Getter
    private final int port;
    
    @Getter
    private volatile boolean connected = false;
    
    private volatile long lastCheckTime = 0;
    private static final long CHECK_CACHE_DURATION_MS = 5000; // Cache status for 5 seconds
    
    /**
     * Creates a new Pulvis connection data source.
     * 
     * @param host the Pulvis server host
     * @param port the Pulvis server port
     */
    public PulvisConnection(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        
        this.host = host;
        this.port = port;
    }
    
    @Override
    public String getDisplayName() {
        return "Pulvis @ " + host + ":" + port;
    }
    
    @Override
    public String getTooltipText() {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.type", "Type")).append(":</b> Pulvis Server<br>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.dialog.host", "Host")).append(":</b> ");
        tooltip.append(host).append("<br>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.dialog.port", "Port")).append(":</b> ");
        tooltip.append(port);
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    @Override
    public Icon getIcon() {
        // Try to get a network/server-like icon, fallback to computer icon
        Icon icon = UIManager.getIcon("FileView.computerIcon");
        if (icon == null) {
            icon = UIManager.getIcon("FileView.hardDriveIcon");
        }
        return icon;
    }
    
    /**
     * Gets the LED status indicator icon showing connection state.
     * 
     * @return an LED icon (green if connected, gray if not)
     */
    public Icon getStatusIcon() {
        checkConnection();
        return connected ? LedIcon.createActive(12) : LedIcon.createInactive(12);
    }
    
    /**
     * Checks if the connection to the Pulvis server is active.
     * Uses cached result if checked recently.
     */
    private void checkConnection() {
        long now = System.currentTimeMillis();
        
        // Use cached result if checked recently
        if (now - lastCheckTime < CHECK_CACHE_DURATION_MS) {
            return;
        }
        
        lastCheckTime = now;
        
        // Perform connection check in a non-blocking way
        try {
            URI uri = URI.create(getBaseUrl() + "/api/health");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000); // 2 second timeout
            connection.setReadTimeout(2000);
            
            int responseCode = connection.getResponseCode();
            connected = (responseCode >= 200 && responseCode < 300);
            
            connection.disconnect();
        } catch (IOException e) {
            connected = false;
            log.debug("Connection check failed for {}:{} - {}", host, port, e.getMessage());
        }
    }
    
    /**
     * Forces a connection check, bypassing the cache.
     * 
     * @return true if the connection is active
     */
    public boolean refreshConnectionStatus() {
        lastCheckTime = 0;
        checkConnection();
        return connected;
    }
    
    /**
     * Gets the base URL for this Pulvis server connection.
     * 
     * @return the base URL (e.g., "http://host:port")
     */
    public String getBaseUrl() {
        return String.format("http://%s:%d", host, port);
    }
    
    @Override
    public String toString() {
        return String.format("PulvisConnection[%s:%d]", host, port);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PulvisConnection)) return false;
        PulvisConnection other = (PulvisConnection) obj;
        return host.equals(other.host) && port == other.port;
    }
    
    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + port;
        return result;
    }
}
