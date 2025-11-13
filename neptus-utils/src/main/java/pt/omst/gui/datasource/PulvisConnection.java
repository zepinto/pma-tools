//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import javax.swing.Icon;
import javax.swing.UIManager;

import lombok.Getter;
import pt.omst.neptus.util.I18n;

/**
 * A data source representing a Pulvis server connection.
 */
public class PulvisConnection implements DataSource {
    
    @Getter
    private final String host;
    
    @Getter
    private final int port;
    
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
