# Map Viewer Application

## Overview

The Map Viewer application is a comprehensive visualization tool that combines multiple marine data components into a single integrated interface. It allows users to view and explore sidescan sonar raster data and contacts on an interactive map with time-based filtering capabilities.

## Key Features

### 1. Interactive Map Display (SlippyMap)
- **Base Maps**: Support for multiple tile sources including OpenStreetMap, Esri Satellite, and CartoDB
- **Zoom & Pan**: Mouse wheel zoom and drag-to-pan navigation
- **Dark Mode**: Automatic adaptation to system theme
- **Real-time Coordinates**: Mouse cursor position displayed in lat/lon format
- **Level of Detail**: Automatic resolution adjustment based on zoom level

### 2. Data Source Management (DataSourceManagerPanel)
- **Folder Sources**: Add local folders containing raster and contact data
- **Visual Chips**: Each data source displayed as a removable chip
- **Duplicate Detection**: Prevents adding overlapping folder hierarchies
- **Dynamic Loading**: Data loaded automatically when sources are added

### 3. Time Interval Selection (ZoomableTimeIntervalSelector)
- **Interactive Timeline**: Visual timeline showing data time range
- **Adjustable Handles**: Drag handles to select start and end times
- **Zoom Controls**: Mouse wheel to zoom in/out on timeline
- **Pan Support**: Drag timeline to navigate through time
- **Smart Labels**: Automatic label formatting based on zoom level (years, months, days, hours, minutes, seconds)

### 4. Raster Data Visualization (IndexedRasterPainter)
- **Sidescan Sonar**: Displays indexed raster files from JSON metadata
- **Mosaic Creation**: Automatic mosaic generation at appropriate zoom levels
- **Bounding Shapes**: Polygon outlines showing raster coverage
- **Performance Optimized**: Background processing for mosaic generation
- **Time-based**: Includes timestamp methods for temporal filtering

### 5. Contact Display (ContactCollection)
- **Compressed Contacts**: Support for .zct (compressed contact) files
- **Map Markers**: Visual representation of contacts on the map
- **Time Filtering**: Contacts filtered by selected time range

## Architecture

### Component Integration

```
MapViewerApp
├── SlippyMap (CENTER)
│   ├── Map Tiles (base layer)
│   ├── IndexedRasterPainter[] (raster overlays)
│   └── ContactCollection[] (contact markers)
└── Bottom Panel (SOUTH)
    ├── DataSourceManagerPanel (CENTER)
    │   ├── Folder Source Chips
    │   └── Add Source Buttons
    └── ZoomableTimeIntervalSelector (SOUTH)
        ├── Timeline View
        └── Selection Handles
```

### Data Flow

1. **User adds data source** → DataSourceManagerPanel
2. **Panel fires sourceAdded event** → MapViewerApp.loadDataFromSource()
3. **App scans folder** → Finds .json (rasters) and .zct (contacts) files
4. **App creates painters** → IndexedRasterPainter and ContactCollection instances
5. **Painters added to map** → SlippyMap.addRasterPainter()
6. **Time bounds calculated** → From raster/contact timestamps
7. **Timeline updated** → ZoomableTimeIntervalSelector shows data range
8. **Map repaints** → All painters render on map

### Time-based Filtering

The application now features **fully interactive time-based filtering**:

- **Real-time Updates**: When you adjust the time selector handles, the displayed rasters and contacts update immediately
- **Visibility Control**: Only rasters and contacts within the selected time range are painted on the map
- **Performance Optimized**: Uses wrapper classes (TimeFilteredRasterPainter, TimeFilteredContactPainter) that check visibility before painting
- **Visual Feedback**: Console logs show count of visible rasters and contacts for the current time range

#### How It Works:
1. User drags time selector handles
2. `updateDisplayedData()` is called with new time range
3. Each `TimeFilteredRasterPainter` checks if its raster overlaps the time range
4. Each `TimeFilteredContactPainter` filters contacts within the time range
5. Map repaints, showing only visible data
6. Status logged: "Updated display: X visible rasters, Y visible contacts"

## Usage

### Running the Application

```bash
# From project root
./gradlew :rasterfall:run -PmainClass=pt.omst.rasterfall.MapViewerApp

# Or build and run the shadow JAR
./gradlew :rasterfall:shadowJar
java -jar rasterfall/build/libs/rasterfall-2025.11.00-all.jar
```

### Adding Data Sources

1. Click the **folder icon** button in the bottom panel
2. Select a folder containing:
   - `*.json` files (raster index files)
   - Corresponding image files (PNG, etc.)
   - `*.zct` files (compressed contact files)
3. Data loads automatically and appears on the map

### Navigating the Map

- **Zoom**: Mouse wheel up/down
- **Pan**: Click and drag
- **Change Base Map**: Right-click → Select tile source
- **View Coordinates**: Bottom-right corner shows mouse position

### Time Selection

- **Adjust Range**: Drag the blue handles on the timeline
- **Zoom Timeline**: Mouse wheel over timeline
- **Pan Timeline**: Click and drag timeline background
- **View Data Range**: Timeline automatically spans loaded data

## File Structure

### Source Files
```
rasterfall/src/main/java/pt/omst/rasterfall/
├── MapViewerApp.java                    # Main application class
├── TimeFilteredRasterPainter.java       # Time-based raster visibility wrapper
└── TimeFilteredContactPainter.java      # Time-based contact visibility wrapper
```

### Dependencies
- `neptus-utils`: Base utilities, SlippyMap, DataSourceManagerPanel, ZoomableTimeIntervalSelector
- `rasterlib`: IndexedRaster data structures, IndexedRasterPainter
- `contacts`: ContactCollection, CompressedContact
- `omst-licences`: License validation

### Data Format

#### Raster Index JSON
```json
{
  "filename": "sidescan_image.png",
  "raster-type": "SIDESCAN",
  "samples": [
    {
      "index": 0,
      "offset": 0,
      "timestamp": "2024-01-01T12:00:00Z",
      "pose": {
        "latitude": 41.0,
        "longitude": -8.0,
        "psi": 45.0,
        "r": 0.0
      }
    }
  ],
  "sensor-info": {
    "min-range": 0.0,
    "max-range": 50.0
  }
}
```

#### Contact Files
- Format: `.zct` (compressed contact format)
- Contains: Location, timestamp, label, observations

## Implementation Notes

### Design Decisions

1. **Time-Filtered Wrappers**: Created `TimeFilteredRasterPainter` and `TimeFilteredContactPainter` wrapper classes that:
   - Check visibility before painting
   - Can be updated with new time ranges without recreating the painters
   - Maintain reference to delegate painters/collections
   - Provide visibility status methods

2. **Efficient Filtering**: Time filtering happens at paint time, avoiding the need to add/remove painters from the map dynamically.

3. **Contact Rendering**: Implements simple circle markers for contacts since `CompressedContact` doesn't have a built-in paint method.

4. **License Integration**: Follows existing pattern from RasterFallApp with RASTERFALL license check.

5. **Layout**: BorderLayout with map in center and controls at bottom for maximum map visibility.

### Future Enhancements

1. ~~**Dynamic Time Filtering**~~: ✅ **IMPLEMENTED** - Rasters and contacts now filter based on time selection
2. **Enhanced Contact Rendering**: Add more detailed contact visualizations (labels, icons, details)
3. **Database Sources**: Extend to support database data sources (already in DataSourceManagerPanel)
4. **Export**: Add export functionality for selected data ranges
5. **Layer Control**: Add layer visibility toggles for individual rasters and contacts
6. **Search**: Add search functionality for contacts and locations
7. **Statistics Panel**: Show counts of visible vs total data in UI

## Technical Details

### Added Functionality

#### IndexedRasterPainter Enhancements
```java
public long getStartTimestamp()  // Returns first sample timestamp
public long getEndTimestamp()    // Returns last sample timestamp
```

These methods enable time-based queries on raster data.

#### Time Filtering Wrappers

**TimeFilteredRasterPainter**
```java
public TimeFilteredRasterPainter(IndexedRasterPainter delegate)
public void setTimeFilter(Instant startTime, Instant endTime)
public boolean isVisible()  // Returns true if raster overlaps time range
public void paint(Graphics2D g, SlippyMap renderer)  // Only paints if visible
```

**TimeFilteredContactPainter**
```java
public TimeFilteredContactPainter(ContactCollection delegate)
public void setTimeFilter(Instant startTime, Instant endTime)
public int getVisibleContactCount()  // Returns number of visible contacts
public void paint(Graphics2D g, SlippyMap renderer)  // Paints visible contacts as circles
```

### Thread Safety
- SlippyMap uses `CopyOnWriteArrayList` for painters
- Background tile loading in separate daemon thread
- Mosaic creation in background executor service
- All UI updates via SwingUtilities.invokeLater()

### Performance Considerations
- Lazy loading of raster images
- Automatic mosaic resolution adjustment
- Tile caching (memory + disk)
- Background processing for expensive operations

## Troubleshooting

### No Data Appears
- Check that folder contains valid .json files
- Verify .json files have corresponding image files
- Check console logs for loading errors

### Map Not Loading
- Ensure internet connection for tile download
- Check tile cache directory: `~/tile_cache/`
- Try changing base map source (right-click menu)

### License Issues
- Ensure license file exists in `conf/licenses/`
- Check license expiration date
- Contact OceanScan for license renewal

## See Also

- **RasterFallApp**: Original sidescan viewer application
- **ContactBrowser**: Standalone contact browsing interface
- **SlippyMap**: Base map component documentation
- **DataSourceManagerPanel**: Data source management component
