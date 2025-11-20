# RasterFall Debug Mode

## Overview

RasterFall now includes a comprehensive debug mode to help diagnose coordinate transformation and measurement issues across the application.

## Enabling Debug Mode

### Option 1: System Property (at startup)
```bash
./gradlew :rasterfall:run -Drasterfall.debug=true
```

**Note**: The `rasterfall` run task is configured to pass all system properties to the JVM. You should see "RasterfallDebug initialized: ENABLED" in the console output when the app starts.

### Option 2: Keyboard Shortcut (at runtime)
Press **Ctrl+D** while the application is running to toggle debug mode on/off.

### Option 3: Programmatically
```java
RasterfallDebug.debug = true;  // Enable
RasterfallDebug.toggle();       // Toggle
```

## Debug Features

When debug mode is enabled, the following additional information is displayed:

### 1. Enhanced Info Overlay (Top-Right Corner)

The info overlay expands to show comprehensive debugging data:

- **Mouse & Screen Coordinates**
  - Absolute screen position
  - Viewport-relative position

- **Viewport State**
  - Viewport position and size
  - Visible rectangle bounds
  - Current zoom level

- **Time Information**
  - Center timestamp (viewport center)
  - Top timestamp (viewport top edge)
  - Bottom timestamp (viewport bottom edge)
  - Mouse position timestamp

- **Position Data**
  - World location (lat/lon)
  - Slant range in meters
  - Vehicle altitude and heading (from pose data)

- **Tile Details**
  - Current tile index at mouse position
  - Tile dimensions (width × height)
  - Tile boundary coordinates
  - Total sample count
  - Current sample index at mouse

- **Scrollbar State**
  - Scrollbar height
  - Start/end timestamps
  - Current scroll position

- **Transformation Metrics**
  - Pixels per second ratio
  - Pixels per meter ratio
  - Maximum sensor range

- **Coordinate Roundtrip Test**
  - Screen→World→Screen transformation error
  - Shows coordinate transformation accuracy

### 2. Tile Boundary Visualization

Colored rectangles are drawn around each tile with labels showing:
- Tile index number
- Tile dimensions
- Start and end timestamps

Each tile uses a different color for easy identification.

### 3. Scrollbar Debug Overlay

The scrollbar displays additional information:
- Time markers at regular intervals (10 markers)
- Current scroll position indicator (red line)
- Position value in pixels
- Current timestamp at scroll center (green)

### 4. Performance Considerations

Debug mode adds visual overlays and text rendering which may impact performance during:
- Fast scrolling
- High zoom levels
- Mouse movement

If performance is an issue, toggle debug mode off when not needed.

## Use Cases

### Debugging Coordinate Transformations
1. Enable debug mode
2. Move mouse over area of interest
3. Check "Roundtrip Test" error values
4. Verify screen↔world coordinate conversions

### Diagnosing Time Calculations
1. Enable debug mode
2. Compare timestamps at different positions
3. Verify scrollbar time markers align with data
4. Check pixels/second ratio for scaling issues

### Verifying Tile Boundaries
1. Enable debug mode
2. Observe colored tile boundaries
3. Verify mouse crosses tile boundaries correctly
4. Check sample indices transition smoothly between tiles

### Checking Viewport State
1. Enable debug mode
2. Zoom in/out and observe zoom level
3. Scroll and watch viewport position update
4. Verify visible rectangle bounds

## Troubleshooting

**Q: Debug info doesn't appear**
- Ensure you've pressed Ctrl+D or set the system property
- Check console for "RasterfallDebug: ENABLED" message
- Try toggling the info overlay on (press the info button)

**Q: Debug info is too small to read**
- The debug panel uses 10px Monospaced font
- Consider adjusting your display scaling

**Q: Keyboard shortcut doesn't work**
- Ensure the rasterfall panel has focus
- Click on the visualization area before pressing Ctrl+D

**Q: Performance is slow with debug mode**
- This is expected with extensive debug rendering
- Toggle debug mode off when not actively debugging
- Avoid rapid mouse movement when debug is enabled

## Implementation Details

Debug mode is controlled by the `RasterfallDebug` class with a global static flag:

```java
public class RasterfallDebug {
    public static boolean debug = Boolean.getBoolean("rasterfall.debug");
    
    public static void toggle() {
        debug = !debug;
        System.out.println("RasterfallDebug: " + (debug ? "ENABLED" : "DISABLED"));
    }
}
```

All rasterfall components check this flag during rendering:
- `InfoOverlay` - Expanded debug information panel
- `RasterfallTiles` - Tile boundary overlays
- `RasterfallScrollbar` - Time markers and position indicators
