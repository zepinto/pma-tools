package pt.omst.gui.datasource;

import java.io.File;

public class RasterfallDataSource extends FolderDataSource {

    private File folder;
    public RasterfallDataSource(File folder) {
        super(folder);
        this.folder = folder;
    }

    @Override
    public String getDisplayName() {
        return "Rasterfall";
    }

    @Override
    public String getTooltipText() {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html>");
        tooltip.append("<b>Type:</b> Rasterfall Data Source<br>");
        tooltip.append("<b>Path:</b> ").append(folder.getAbsolutePath()).append("<br>");
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    @Override
    public boolean isDeletable() {
        return false;
    }
}
