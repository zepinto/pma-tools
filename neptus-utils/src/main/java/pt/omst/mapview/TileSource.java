//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

/**
 * Enumeration of available tile sources with their URLs and attribution.
 */
public enum TileSource {
        OPENSTREETMAP("OpenStreetMap", 
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors", 19),
        OPENTOPO("OpenTopoMap",
            "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)", 17),
        CARTO_LIGHT("CartoDB Positron",
            "https://cartodb-basemaps-a.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png",
            "Map tiles by Carto (CC BY 3.0) | © OpenStreetMap contributors", 19),
        CARTO_DARK("CartoDB Dark Matter",
            "https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png",
            "Map tiles by Carto (CC BY 3.0) | © OpenStreetMap contributors", 19),
        ESRI_WORLD_IMAGERY("ESRI World Imagery",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "Tiles © Esri", 19),
        VIRTUAL_EARTH("Virtual Earth",
            "https://t{server}.tiles.virtualearth.net/tiles/a{qk}.jpeg?g=1",
            "© Microsoft Corporation", 20);
                
    private final String displayName;
    private final String urlPattern;
    private final String attribution;
    private final int maxZoom;
    
    TileSource(String displayName, String urlPattern, String attribution, int maxZoom) {
        this.displayName = displayName;
        this.urlPattern = urlPattern;
        this.attribution = attribution;
        this.maxZoom = maxZoom;
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
        if (this == VIRTUAL_EARTH) {
            // Virtual Earth uses quad key tile indexing
            String quadKey = tileXYToQuadKey(x, y, z);
            int server = (int) (Math.random() * 4); // Random server r0-r3
            return urlPattern.replace("{server}", String.valueOf(server))
                           .replace("{qk}", quadKey);
        }
        
        return urlPattern.replace("{z}", String.valueOf(z))
                       .replace("{x}", String.valueOf(x))
                       .replace("{y}", String.valueOf(y));
    }
    
    /**
     * Converts tile XY coordinates into a QuadKey at a specified level of detail.
     * Based on Virtual Earth tile system documentation.
     * 
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param levelOfDetail Level of detail (zoom level)
     * @return A string containing the QuadKey
     */
    private static String tileXYToQuadKey(int tileX, int tileY, int levelOfDetail) {
        StringBuilder quadKey = new StringBuilder();
        for (int i = levelOfDetail; i > 0; i--) {
            char digit = '0';
            int mask = 1 << (i - 1);
            if ((tileX & mask) != 0) {
                digit++;
            }
            if ((tileY & mask) != 0) {
                digit++;
                digit++;
            }
            quadKey.append(digit);
        }
        return quadKey.toString();
    }
    
    public String getCacheName() {
        return name().toLowerCase();
    }

    /**
     * Maximum supported zoom level for this tile source. Requests for tiles beyond this
     * zoom level should be avoided to prevent HTTP errors (e.g. OpenStreetMap supports up to z=19).
     */
    public int getMaxZoom() {
        return maxZoom;
    }
}
