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
 * A data source representing a database connection.
 */
public class DatabaseDataSource implements DataSource {
    
    @Getter
    private final String host;
    
    @Getter
    private final int port;
    
    @Getter
    private final String databaseName;
    
    @Getter
    private final String username;
    
    @Getter
    private final String databaseType;
    
    // Password is not exposed via getter for security
    private final String password;
    
    /**
     * Creates a new database data source.
     * 
     * @param host the database host
     * @param port the database port
     * @param databaseName the name of the database
     * @param username the username for connection
     * @param password the password for connection
     * @param databaseType the type of database (e.g., "MySQL", "PostgreSQL")
     */
    public DatabaseDataSource(String host, int port, String databaseName, 
                             String username, String password, String databaseType) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException("Database name cannot be null or empty");
        }
        
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.databaseType = databaseType != null ? databaseType : "Database";
    }
    
    @Override
    public String getDisplayName() {
        return databaseName + " @ " + host;
    }
    
    @Override
    public String getTooltipText() {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.type", "Type")).append(":</b> ");
        tooltip.append(databaseType).append("<br>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.dialog.host", "Host")).append(":</b> ");
        tooltip.append(host).append("<br>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.dialog.port", "Port")).append(":</b> ");
        tooltip.append(port).append("<br>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.dialog.database", "Database")).append(":</b> ");
        tooltip.append(databaseName).append("<br>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.dialog.user", "User")).append(":</b> ");
        tooltip.append(username != null ? username : I18n.textOrDefault("datasource.none", "None"));
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    @Override
    public Icon getIcon() {
        // Try to get a database-like icon, fallback to computer icon
        Icon icon = UIManager.getIcon("FileView.computerIcon");
        if (icon == null) {
            icon = UIManager.getIcon("FileView.hardDriveIcon");
        }
        return icon;
    }
    
    /**
     * Gets the connection URL for this database.
     * 
     * @return a JDBC-style connection URL
     */
    public String getConnectionUrl() {
        return String.format("jdbc:%s://%s:%d/%s", 
                           databaseType.toLowerCase(), host, port, databaseName);
    }
    
    /**
     * Gets the password (use with caution).
     * 
     * @return the password
     */
    public String getPassword() {
        return password;
    }
    
    @Override
    public String toString() {
        return String.format("DatabaseDataSource[%s@%s:%d/%s]", 
                           username, host, port, databaseName);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DatabaseDataSource)) return false;
        DatabaseDataSource other = (DatabaseDataSource) obj;
        return host.equals(other.host) 
            && port == other.port 
            && databaseName.equals(other.databaseName)
            && (username == null ? other.username == null : username.equals(other.username));
    }
    
    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + port;
        result = 31 * result + databaseName.hashCode();
        result = 31 * result + (username != null ? username.hashCode() : 0);
        return result;
    }
}
