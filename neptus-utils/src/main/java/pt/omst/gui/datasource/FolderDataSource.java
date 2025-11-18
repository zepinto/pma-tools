//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui.datasource;

import java.io.File;
import javax.swing.Icon;
import javax.swing.UIManager;

import lombok.Getter;
import pt.lsts.neptus.util.I18n;

/**
 * A data source representing a folder on the file system.
 */
public class FolderDataSource implements DataSource {
    
    @Getter
    private final File folder;
    
    /**
     * Creates a new folder data source.
     * 
     * @param folder the folder this data source represents
     */
    public FolderDataSource(File folder) {
        if (folder == null) {
            throw new IllegalArgumentException("Folder cannot be null");
        }
        this.folder = folder;
    }
    
    @Override
    public String getDisplayName() {
        return folder.getName().isEmpty() ? folder.getAbsolutePath() : folder.getName();
    }
    
    @Override
    public String getTooltipText() {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.type", "Type")).append(":</b> ");
        tooltip.append(I18n.textOrDefault("datasource.type.folder", "Folder")).append("<br>");
        tooltip.append("<b>").append(I18n.textOrDefault("datasource.path", "Path")).append(":</b> ");
        tooltip.append(folder.getAbsolutePath()).append("<br>");
        
        if (folder.exists()) {
            tooltip.append("<b>").append(I18n.textOrDefault("datasource.exists", "Exists")).append(":</b> ");
            tooltip.append(I18n.textOrDefault("button.yes", "Yes")).append("<br>");
            tooltip.append("<b>").append(I18n.textOrDefault("datasource.readable", "Readable")).append(":</b> ");
            tooltip.append(folder.canRead() ? I18n.textOrDefault("button.yes", "Yes") : I18n.textOrDefault("button.no", "No"));
        } else {
            tooltip.append("<b>").append(I18n.textOrDefault("datasource.exists", "Exists")).append(":</b> ");
            tooltip.append(I18n.textOrDefault("button.no", "No"));
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    @Override
    public Icon getIcon() {
        // Try to get system folder icon, fallback to default
        return UIManager.getIcon("FileView.directoryIcon");
    }
    
    @Override
    public String toString() {
        return "FolderDataSource[" + folder.getAbsolutePath() + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FolderDataSource)) return false;
        FolderDataSource other = (FolderDataSource) obj;
        return folder.equals(other.folder);
    }
    
    @Override
    public int hashCode() {
        return folder.hashCode();
    }
}
