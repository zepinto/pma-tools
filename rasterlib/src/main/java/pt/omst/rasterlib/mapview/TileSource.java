//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterlib.mapview;

/**
 * Enumeration of available tile sources with their URLs and attribution.
 */
public enum TileSource {
    OPENSTREETMAP("OpenStreetMap", 
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors"),
    OPENTOPO("OpenTopoMap",
            "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)"),
    STAMEN_TERRAIN("Stamen Terrain",
            "https://tiles.stadiamaps.com/tiles/stamen_terrain/{z}/{x}/{y}.png",
            "Map tiles by Stamen Design (CC BY 3.0) | © OpenStreetMap contributors"),
    STAMEN_TONER("Stamen Toner",
            "https://tiles.stadiamaps.com/tiles/stamen_toner/{z}/{x}/{y}.png",
            "Map tiles by Stamen Design (CC BY 3.0) | © OpenStreetMap contributors"),
    CARTO_LIGHT("CartoDB Positron",
            "https://cartodb-basemaps-a.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png",
            "Map tiles by Carto (CC BY 3.0) | © OpenStreetMap contributors"),
    CARTO_DARK("CartoDB Dark Matter",
            "https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png",
            "Map tiles by Carto (CC BY 3.0) | © OpenStreetMap contributors"),
    ESRI_WORLD_IMAGERY("ESRI World Imagery",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "Tiles © Esri"),
    HUMANITARIAN("Humanitarian OSM",
            "https://tile-a.openstreetmap.fr/hot/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors | Tiles courtesy of HOT");
    
    private final String displayName;
    private final String urlPattern;
    private final String attribution;
    
    TileSource(String displayName, String urlPattern, String attribution) {
        this.displayName = displayName;
        this.urlPattern = urlPattern;
        this.attribution = attribution;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getUrlPattern() {
        return urlPattern;
    }
    
    public String getAttribution() {
        return attribution;
    }
    
    public String getTileUrl(int z, int x, int y) {
        return urlPattern.replace("{z}", String.valueOf(z))
                       .replace("{x}", String.valueOf(x))
                       .replace("{y}", String.valueOf(y));
    }
    
    public String getCacheName() {
        return name().toLowerCase();
    }
}
