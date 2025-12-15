# RasterFall Coordinate and Zoom Management

This document describes the coordinate systems, transformations, and zoom management used in the RasterFall sidescan sonar visualization system.

## Table of Contents
1. [Coordinate Systems Overview](#coordinate-systems-overview)
2. [RasterfallTile Coordinate Management](#rasterfalltile-coordinate-management)
3. [RasterfallTiles Container Coordinate Management](#rasterfalltiles-container-coordinate-management)
4. [RasterfallScrollbar Coordinate Management](#rasterfallscrollbar-coordinate-management)
5. [Zoom Management](#zoom-management)
6. [Known Issues and Potential Errors](#known-issues-and-potential-errors)

---

## Coordinate Systems Overview

RasterFall uses multiple coordinate systems that need to be converted between:

1. **Screen Coordinates** - Pixel coordinates in the UI (JPanel bounds)
2. **Tile Coordinates** - Pixel coordinates within an individual tile (relative to tile bounds)
3. **World Coordinates** - Geographic coordinates (latitude/longitude) and ranges (meters)
4. **Time/Range Space** - Timestamp (milliseconds) + slant/ground range (meters)
5. **ScrollImage Coordinates** - Pixel coordinates in the scrollbar's miniature image
6. **Viewport Coordinates** - Visible portion of the waterfall display

### Key Dimensions

- **X-axis (horizontal)**: Represents range across the sonar swath
  - Center = vehicle track
  - Negative X = Port side
  - Positive X = Starboard side
  - Full width = 2 × maxRange

- **Y-axis (vertical)**: Represents time progression
  - Top = Newest data (latest timestamp)
  - Bottom = Oldest data (earliest timestamp)
  - **CRITICAL**: Y-axis is inverted relative to time for visualization

---

## RasterfallTile Coordinate Management

### Tile Overview
A `RasterfallTile` represents a single continuous segment of sidescan data with:
- An `IndexedRaster` containing samples (vehicle poses + timestamps)
- A `BufferedImage` displaying the sonar data
- Samples ordered chronologically (oldest first in list)

### Key Properties

```java
private final IndexedRaster raster;        // Contains samples and sensor info
private BufferedImage image;               // Sonar image to display
private final double heightProportition;   // Calculated aspect ratio
private double zoom = 1.0;                 // Current zoom level
```

### Coordinate Transformation Formulas

#### 1. Screen Position → Timestamp

**Formula:**
```java
long getTimestamp(int x, int y)
```

```
index = (y / height) × samplesCount
index = samplesCount - index - 1    // Invert: bottom = oldest
timestamp = raster.samples[index].timestamp
```

**Example:**
- y = 0 (top) → index = samplesCount - 1 → newest sample
- y = height (bottom) → index = 0 → oldest sample

**Potential Issue:** Integer division may lose precision for small Y values.

---

#### 2. Screen X → Range

**Formula:**
```java
double getRange(double x)
```

```
worldWidth = maxRange × 2
range = ((x - width/2) / width) × worldWidth
```

**Coordinate mapping:**
- x = 0 → range = -maxRange (port side)
- x = width/2 → range = 0 (nadir/vehicle track)
- x = width → range = +maxRange (starboard side)

**Note:** This returns **slant range** (direct distance), not ground range.

---

#### 3. Slant Range → Screen Position

**Formula (without timestamp):**
```java
Point2D.Double getSlantedRangePosition(double slantRange)
```

```
xx = slantRange + getRange()           // Shift to [0, 2×range]
xx = (xx / (getRange() × 2)) × width  // Normalize to screen width
xx = clamp(xx, 0, width)              // Ensure within bounds
yy = ((samplesCount - 1) / samplesCount) × height  // Last sample
```

**Formula (with timestamp):**
```java
Point2D.Double getSlantedRangePosition(Instant timestamp, double slantRange)
```

```
1. Find sample index where samples[index].timestamp > timestamp
2. xx = (slantRange + getRange()) / (getRange() × 2) × width
3. yy = ((samplesCount - index) / samplesCount) × height
```

**Key Insight:** Y position inverts the index because:
- index = 0 (oldest) should appear at bottom (high Y)
- index = max (newest) should appear at top (low Y)

---

#### 4. Ground Range → Screen Position

**Formula:**
```java
Point2D.Double getGroundPosition(Instant timestamp, double slantRange)
```

```
1. Find sample index for timestamp
2. Get pose altitude at that index
3. groundRange = √(slantRange² - altitude²)   // Pythagorean theorem
4. xx = (groundRange + getRange()) / (getRange() × 2) × width
5. yy = (samplesCount - index) / samplesCount × height
```

**Conversion diagram:**
```
    altitude
       |
       |
       +----- slantRange
      /|
     / |
    /  |
vehicle groundRange
```

**Potential Issue:** If `slantRange < altitude`, the square root becomes imaginary. The code doesn't explicitly handle this case.

---

#### 5. Screen Position → World Location

**Formula:**
```java
LocationType getWorldPosition(int x, int y)
```

```
1. slantRange = (x / width) × (getRange() × 2) - getRange()
2. index = samplesCount - (y / height × samplesCount) - 1
3. Call getLocation(index, slantRange)
```

**getLocation(index, slantRange):**
```
1. pose = raster.samples[index].pose
2. loc = LocationType(pose.latitude, pose.longitude)
3. altitude = pose.altitude
4. groundRange = √(slantRange² - altitude²)
5. IF slantRange >= 0:
     azimuth = pose.psi + 90°  (starboard)
   ELSE:
     azimuth = pose.psi - 90°  (port)
6. loc.setOffsetDistance(groundRange)
7. loc.convertToAbsoluteLatLonDepth()
```

**Key Points:**
- Positive range = starboard = +90° from heading
- Negative range = port = -90° from heading
- `convertToAbsoluteLatLonDepth()` applies the offset to get final lat/lon

**Potential Issue:** No explicit NaN check if `groundRange` calculation fails.

---

#### 6. World Location → Screen Position (Inverse Transformation)

**Formula (in RasterfallTiles, delegates to tile):**
```java
Point2D.Double getSlantedScreenPositionFromLocation(Instant timestamp, 
                                                     double targetLat, 
                                                     double targetLon)
```

```
1. Get pose at timestamp
2. vehicleLoc = LocationType(pose.latitude, pose.longitude)
3. targetLoc = LocationType(targetLat, targetLon)
4. groundRange = vehicleLoc.getHorizontalDistanceInMeters(targetLoc)

5. Calculate bearing from vehicle to target:
   bearing = vehicleLoc.getXYAngle(targetLoc)  // radians
   
6. Determine port/starboard:
   headingRad = pose.psi (degrees → radians)
   angleDiff = bearing - headingRad
   angleDiff = normalize to [-π, π]
   
7. IF angleDiff >= 0:
     signedGroundRange = +groundRange  (starboard)
   ELSE:
     signedGroundRange = -groundRange  (port)

8. Convert to slant range:
   altitude = pose.altitude
   slantRange = √(signedGroundRange² + altitude²)
   IF signedGroundRange < 0:
     slantRange = -slantRange
   
9. Call tile.getSlantedRangePosition(timestamp, slantRange)
```

**Potential Issues:**
- Angle normalization could fail if bearing/heading values are not in expected ranges
- The sign of slantRange is determined by groundRange sign, but the magnitude includes altitude - this asymmetry could cause issues

---

### Size and Aspect Ratio

**Height Proportion Calculation:**
```java
// In constructor
worldWidth = maxRange × 2
speed = average(samples[*].pose.u)  // Forward velocity
startTime = samples[0].timestamp (seconds)
endTime = samples[last].timestamp (seconds)
worldHeight = speed × (endTime - startTime)
heightProportition = worldHeight / worldWidth
```

**Preferred Size:**
```java
Dimension getPreferredSize()
```

```
viewportWidth = getParent().getParent().getParent().getWidth()
width = viewportWidth × zoom
height = width × heightProportition
```

**Issue:** The parent hierarchy navigation is fragile - if component structure changes, this breaks.

---

### Resolution Calculations

**Horizontal Resolution (meters per pixel):**
```java
double getHorizontalResolution()
```

```
totalRange = maxRange - minRange
resolution = totalRange / width
```

**Vertical Resolution (meters per pixel):**
```java
double getVerticalResolution()
```

```
locStart = getLocation(0, 0)              // Bottom-left
locEnd = getLocation(samplesCount-1, 0)  // Top-left (track)
distanceTravelled = locEnd.getDistanceInMeters(locStart)
resolution = distanceTravelled / height
```

**Note:** Uses actual traveled distance, accounts for vehicle path curvature.

---

## RasterfallTiles Container Coordinate Management

### Container Overview
`RasterfallTiles` is a `JPanel` containing multiple `RasterfallTile` instances arranged vertically.

### Layout Strategy

**Vertical Stacking:**
```java
void doLayout()
```

```
yPosition = 0
FOR each tile in tiles:
    tileSize = tile.getPreferredSize()
    tile.setBounds(0, yPosition, tileSize.width, tileSize.height)
    yPosition += tileSize.height
```

**Tiles are ordered by timestamp (newest first):**
```java
tiles.sort(Comparator.naturalOrder())  // Uses RasterfallTile.compareTo()
// compareTo returns o.getStartTime().compareTo(this.getStartTime()) - inverted
```

### Container-Level Coordinate Transformations

#### 1. Screen Point → TilesPosition

**Formula:**
```java
TilesPosition getPosition(Point2D point)
```

```
FOR each tile:
    IF tile.bounds.contains(point):
        x = point.x - tile.bounds.x   // Convert to tile coords
        y = point.y - tile.bounds.y
        timestamp = tile.getTimestamp(x, y)
        range = tile.getRange(x)
        location = tile.getWorldPosition(x, y)
        pose = tile.getPose(x, y)
        RETURN TilesPosition(timestamp, range, location, pose)
```

**Returns null if point is outside all tiles.**

---

#### 2. Timestamp → Screen Y Position

**Formula:**
```java
Instant getTimeAtScreenY(int screenY)
```

```
FOR each tile:
    IF tile.bounds.contains(0, screenY):
        y = screenY - tile.bounds.y
        RETURN Instant.ofEpochMilli(tile.getTimestamp(0, y))
```

---

#### 3. Screen X → Range

**Formula:**
```java
double getRangeAtScreenX(int screenX)
```

```
FOR each tile:
    IF tile.bounds.contains(screenX, 0):
        x = screenX - tile.bounds.x
        RETURN tile.getRange(x)
```

**Issue:** Returns `Double.NaN` if no tile contains the X coordinate.

---

#### 4. Slant Range + Timestamp → Screen Position

**Formula:**
```java
Point2D.Double getSlantedScreenPosition(Instant timestamp, double range)
```

```
FOR each tile:
    IF tile.containsTime(timestamp):
        relativePos = tile.getSlantedRangePosition(timestamp, range)
        absolutePos = (tile.bounds.x + relativePos.x, 
                      tile.bounds.y + relativePos.y)
        RETURN absolutePos
```

**Returns null if timestamp is outside all loaded tiles (not logged as error).**

---

### Visible Timestamps

The container tracks which part is visible via the viewport:

```java
long getTopTimestamp()    // Top edge of visible rect
long getBottomTimestamp() // Bottom edge of visible rect
long getMiddleTimestamp() // Center of visible rect
```

**Formula (generic):**
```
x = visibleRect.x + visibleRect.width/2   // Center X
y = visibleRect.y + <offset>              // Variable Y
timestamp = getTimestamp(Point2D(x, y))
```

---

## RasterfallScrollbar Coordinate Management

### Scrollbar Overview
The scrollbar displays a miniaturized view of the entire waterfall with a draggable position indicator.

### Key Components

```java
private BufferedImage scrollImage;     // Miniature of entire waterfall
private double position;               // Y position of scroll indicator
private final JViewport viewport;      // Main waterfall viewport
private final RasterfallTiles waterfall;
```

### Scrollbar Image Generation

**Formula:**
```java
BufferedImage getScrollImage(int width, Consumer<Void> callback)
```

```
1. Calculate total vertical size in samples:
   verticalSize = sum(tile.samplesCount for all tiles)
   
2. Get first tile image to determine aspect ratio:
   firstImage = tiles[0].getImageSync()
   scale = width / firstImage.width
   
3. Create composite image:
   verticalSize *= scale
   image = new BufferedImage(width, verticalSize, ARGB)
   
4. Draw tiles from bottom to top:
   y = verticalSize
   FOR tile in tiles (reversed order):
       tileImage = tile.getImageSync()
       y -= tileImage.height × scale
       drawImage(tileImage, 0, y, width, height×scale)
```

**Note:** Images are drawn in reverse order because the display is time-inverted.

---

### Time ↔ Position Conversions

#### 1. Scrollbar Y → Timestamp

**Formula:**
```java
Date yToTimestamp(double y)
```

```
1. Calculate relative scroll position:
   relativePosition = position / getHeight()
   
2. Calculate visible portion of scrollImage:
   extraHeight = scrollImage.height - getHeight()
   startPixel = relativePosition × extraHeight
   startPixel = max(0, startPixel)
   
3. Adjust y for current scroll offset:
   y += startPixel
   
4. Map to timestamp:
   startTime = waterfall.getStartTime()
   endTime = waterfall.getEndTime()
   time = (y / scrollImage.height) × (endTime - startTime) + startTime
```

**Key Insight:** `scrollImage.height > getHeight()` because the image contains all data, but only a portion is visible at once.

---

#### 2. Timestamp → Scrollbar Position

**Formula:**
```java
void scrollToTime(long timestamp, boolean update)
```

```
1. Validate bounds:
   IF timestamp < startTime OR timestamp > endTime:
     RETURN  // Out of bounds
   
2. Calculate fractional position in time range:
   fracIndex = (timestamp - startTime) / (endTime - startTime)
   
3. Convert to scrollbar position (inverted Y):
   position = getHeight() - (fracIndex × getHeight())
   position -= scrollHeight × fracIndex
   
4. Clamp to valid range:
   position = clamp(position, 0, getHeight() - scrollHeight)
   
5. IF update:
     updateViewPort(position)
```

**Time Inversion:** 
- `fracIndex = 0` (oldest) → `position = getHeight()` (bottom)
- `fracIndex = 1` (newest) → `position = 0` (top)

---

#### 3. Timestamp → ScrollImage Y (for contact drawing)

**Formula:**
```java
private double timestampToScrollbarY(long timestamp)
```

```
startTime = waterfall.getStartTime()
endTime = waterfall.getEndTime()
height = scrollImage.height - scrollHeight
fracIndex = (timestamp - startTime) / (endTime - startTime)
return (height - (fracIndex × scrollImage.height)) + scrollHeight
```

**Issue:** This formula differs slightly from `scrollToTime` - it uses `scrollImage.height` instead of `getHeight()`, which could cause inconsistencies.

---

### Position → Viewport Mapping

**Formula:**
```java
private void updateViewPort(double position)
```

```
1. Calculate relative position on scrollbar:
   relativePosition = position / (getHeight() - scrollHeight)
   
2. Map to viewport Y position:
   viewportHeight = viewport.getViewSize().height
   visibleHeight = viewport.getSize().height
   y = (viewportHeight - visibleHeight) × relativePosition
   
3. Clamp to valid range:
   y = min(y, viewportHeight - visibleHeight)
   
4. Update viewport:
   viewport.setViewPosition(Point(viewport.x, y))
```

**Ratio preservation:** The scrollbar position directly controls the viewport position proportionally.

---

### Contact Drawing on Scrollbar

**Formula:**
```java
private void drawContacts(Graphics2D g2d)
```

```
For each contact:
    1. Get contact info (time range + range):
       centerTime = (startTimeStamp + endTimeStamp) / 2
       centerRange = (minRange + maxRange) / 2
       
    2. Convert time to scrollImage Y:
       scrollImageY = timestampToScrollbarY(centerTime)
       
    3. Check visibility:
       visibleStart = startPixel
       visibleEnd = startPixel + getHeight()
       IF scrollImageY < visibleStart OR > visibleEnd:
         SKIP
       
    4. Convert to screen Y:
       screenY = scrollImageY - startPixel
       
    5. Map range to X position:
       rangeNorm = (centerRange + maxRange) / (2 × maxRange)
       screenX = rangeNorm × getWidth()
       screenX = clamp(screenX, 1, getWidth() - 2)
       
    6. Draw 3×3 cross at (screenX, screenY)
```

**Range Mapping:**
- `centerRange = -maxRange` → `rangeNorm = 0` → `screenX = 0` (left edge, port)
- `centerRange = 0` → `rangeNorm = 0.5` → `screenX = width/2` (center, nadir)
- `centerRange = +maxRange` → `rangeNorm = 1` → `screenX = width` (right edge, starboard)

---

## Zoom Management

### Zoom in RasterfallTile

**Single Tile Zoom:**
```java
void setZoom(double zoom)
```

```
this.zoom = zoom
revalidate()  // Triggers layout recalculation
```

**Preferred Size with Zoom:**
```java
Dimension getPreferredSize()
```

```
viewportWidth = getParent().getParent().getParent().getWidth()
width = viewportWidth × zoom
height = width × heightProportition
RETURN Dimension(width, height)
```

---

### Zoom in RasterfallTiles (Container)

**Batch Zoom Update:**
```java
void setZoom(double zoom)
```

```
this.zoom = zoom

// Batch update - don't trigger individual revalidations
FOR each tile:
    tile.setZoomQuiet(zoom)

// Single layout update for entire container
revalidate()
repaint()
```

**setZoomQuiet():** Sets zoom without calling `revalidate()` - used for batch updates.

---

### Interactive Zoom (Mouse Wheel)

**Formula (in RasterfallScrollbar mouse wheel handler):**
```java
waterfall.addMouseWheelListener(e -> {
    mousePosition = e.getPoint()
    viewPosition = viewport.getViewPosition()
    previousZoom = waterfall.getZoom()
    
    // Adjust zoom
    IF wheelRotation > 0:
        zoom = max(previousZoom × 0.9, 1)    // Zoom out, min 1
    ELSE:
        zoom = min(previousZoom × 1.1, 10)   // Zoom in, max 10
    
    // Calculate scale factor
    scale = zoom / previousZoom
    
    // Calculate new viewport size
    newViewPortSize = viewport.size × scale
    viewPortSizeDifference = newViewPortSize - viewport.size
    
    // Calculate mouse position ratios
    mouseRatioX = (mousePosition.x - viewPosition.x) / viewport.width
    mouseRatioY = (mousePosition.y - viewPosition.y) / viewport.height
    
    // Update view position to keep mouse point stable
    viewPosition.x = viewPosition.x × (zoom/previousZoom) + 
                     viewPortSizeDifference.width × mouseRatioX
    viewPosition.y = viewPosition.y × (zoom/previousZoom) + 
                     viewPortSizeDifference.height × mouseRatioY
    
    // Clamp to bounds when zooming out
    IF scale < 1:
        viewPosition.x = clamp(...)
        viewPosition.y = clamp(...)
    
    // Apply changes
    waterfall.setZoom(zoom)
    viewport.setViewPosition(viewPosition)
})
```

**Goal:** Keep the pixel under the mouse cursor fixed in screen space during zoom.

**Issue:** The clamping logic is only applied when `scale < 1` (zooming out), which could allow invalid positions when zooming in.

---

### Zoom Constraints

- **Minimum zoom:** 1.0 (100% - original size)
- **Maximum zoom:** 10.0 (1000% - 10× magnification)
- **Zoom step:** ×0.9 (zoom out) or ×1.1 (zoom in) - approximately 10% per wheel click

---

## Known Issues and Potential Errors

### 1. **Coordinate Conversion Edge Cases**

#### Issue: Ground Range Calculation
**Location:** `RasterfallTile.getLocation()`

```java
double groundRange = Math.sqrt(slantRange*slantRange - altitude*altitude);
if (Double.isNaN(groundRange))
    groundRange = 0;
```

**Problem:** If `slantRange < altitude` (target is in nadir zone below vehicle), the square root returns NaN. The code sets it to 0, but this is a silent failure that could place the target incorrectly at the vehicle position.

**Better approach:** Explicitly check before calculation:
```java
if (Math.abs(slantRange) <= altitude) {
    groundRange = 0;  // Nadir zone
} else {
    groundRange = Math.sqrt(slantRange*slantRange - altitude*altitude);
}
```

---

#### Issue: Integer Division Precision Loss
**Location:** `RasterfallTile.getTimestamp()`

```java
int index = (int)(((float)y/getHeight()) * getSamplesCount());
```

**Problem:** Uses float division, then converts to int. For small Y values or large sample counts, precision loss could select wrong sample.

**Better approach:** Use double precision:
```java
int index = (int)(((double)y / getHeight()) * getSamplesCount());
```

---

#### Issue: Slant Range Sign Handling
**Location:** `RasterfallTiles.getSlantedScreenPositionFromLocation()`

```java
double slantRange = Math.sqrt(signedGroundRange * signedGroundRange + altitude * altitude);
if (signedGroundRange < 0) {
    slantRange = -slantRange;
}
```

**Problem:** The magnitude calculation uses `signedGroundRange²`, which is always positive, so the slant range magnitude is the same for port and starboard. The sign is then applied based on groundRange sign. However, this doesn't account for the fact that slant range should be the hypotenuse, and the sign should represent the side.

**Potential fix:** Calculate unsigned values first:
```java
double absGroundRange = Math.abs(signedGroundRange);
double absSlantRange = Math.sqrt(absGroundRange * absGroundRange + altitude * altitude);
double slantRange = Math.copySign(absSlantRange, signedGroundRange);
```

---

### 2. **Fragile Parent Hierarchy Navigation**

**Location:** `RasterfallTile.getPreferredSize()`

```java
if (getParent() == null || getParent().getParent() == null || 
    getParent().getParent().getParent() == null) {
    return getFullResolutionSize();
}
int viewportWidth = getParent().getParent().getParent().getWidth();
```

**Problem:** Assumes specific component hierarchy: `Tile → Container → ScrollPane → Viewport`. If the UI structure changes, this breaks.

**Better approach:** Walk up the tree looking for a `JViewport`:
```java
Container parent = getParent();
while (parent != null && !(parent instanceof JViewport)) {
    parent = parent.getParent();
}
if (parent != null) {
    viewportWidth = parent.getWidth();
} else {
    viewportWidth = defaultWidth;
}
```

---

### 3. **Time Coordinate Inconsistencies**

#### Issue: Different Formulas for Same Conversion
**Locations:**
- `RasterfallScrollbar.scrollToTime()`
- `RasterfallScrollbar.timestampToScrollbarY()`

**scrollToTime:**
```java
position = getHeight() - (fracIndex × getHeight());
position -= scrollHeight × fracIndex;
```

**timestampToScrollbarY:**
```java
return (height - (fracIndex × scrollImage.height)) + scrollHeight;
```

**Problem:** These use different reference heights (`getHeight()` vs `scrollImage.height`) and apply `scrollHeight` differently (subtract vs add). This could cause contacts to appear at slightly wrong positions.

**Should be unified** to use the same calculation.

---

### 4. **Zoom Viewport Clamping**

**Location:** `RasterfallScrollbar` mouse wheel listener

```java
if (scale < 1) {
    viewPosition.x = Math.max(0, Math.min(viewPosition.x, waterfall.getPreferredSize().width - newViewPortSize.width));
    viewPosition.y = Math.max(0, Math.min(viewPosition.y, waterfall.getPreferredSize().height - newViewPortSize.height));
    viewPosition.x = Math.min(viewPosition.x, waterfall.getPreferredSize().width - viewport.getSize().width);
    viewPosition.y = Math.min(viewPosition.y, waterfall.getPreferredSize().height - viewport.getSize().height);
}
```

**Problem:** Clamping only happens when zooming out (`scale < 1`). When zooming in (`scale > 1`), the viewport could be positioned outside valid bounds.

**Fix:** Always clamp, regardless of zoom direction.

---

### 5. **Contact Visibility Calculation**

**Location:** `RasterfallTiles.getVisibleContacts()`

```java
long visibleStart = getBottomTimestamp();
long visibleEnd = getTopTimestamp();
if (contactEnd < visibleStart || contactStart > visibleEnd)
    continue;
```

**Problem:** Variable names are confusing:
- `visibleStart` = bottom timestamp = **older** time
- `visibleEnd` = top timestamp = **newer** time

But in normal time ordering, "start" implies earlier (older) and "end" implies later (newer), which matches. However, the visual layout is inverted (top = newer), so this could cause confusion during maintenance.

**Recommendation:** Add clarifying comments or rename:
```java
long visibleOldest = getBottomTimestamp();  // Bottom of screen
long visibleNewest = getTopTimestamp();     // Top of screen
```

---

### 6. **Null Pointer Risks**

**Location:** Multiple methods return null when timestamp/position is out of bounds:
- `RasterfallTile.getSlantedRangePosition()`
- `RasterfallTiles.getSlantedScreenPosition()`
- `RasterfallTiles.getWorldPosition()`

**Problem:** Callers must null-check, but this isn't enforced by types. Missing null checks could cause NPEs.

**Better approach:** Use `Optional<Point2D.Double>` or similar to make nullability explicit.

---

### 7. **Aspect Ratio Calculation**

**Location:** `RasterfallTile` constructor

```java
double speed = raster.getSamples().stream()
    .collect(Collectors.averagingDouble(sample -> sample.getPose().getU()));
double startTime = raster.getSamples().getFirst().getTimestamp().toInstant().toEpochMilli()/1000.0;
double endTime = raster.getSamples().getLast().getTimestamp().toInstant().toEpochMilli()/1000.0;
worldHeight = speed * (endTime - startTime);
heightProportition = worldHeight / worldWidth;
```

**Problem:** Uses **average** forward velocity (`u`), but this doesn't account for:
- Variable speed during the survey
- Turns or non-straight paths
- Actual ground track distance vs. time-based distance

**Result:** Aspect ratio may not accurately represent true spatial proportions.

**Better approach:** Calculate actual distance traveled:
```java
double worldHeight = 0;
for (int i = 1; i < samples.size(); i++) {
    LocationType loc1 = new LocationType(samples.get(i-1).getPose()...);
    LocationType loc2 = new LocationType(samples.get(i).getPose()...);
    worldHeight += loc1.getDistanceInMeters(loc2);
}
```

(Note: This is more expensive computationally, so averaging may be a reasonable trade-off.)

---

### 8. **IndexedRasterTiles Coordinate Calculations**

**Location:** `IndexedRasterTiles.calcPointFromIndex()`

```java
double slantRange = xIndex * (range * 2.0 / imageWidth) - range;
```

**Problem:** Assumes linear mapping from image pixels to range, but actual sonar data may have non-linear range bins or varying resolution.

**Assumption:** The `IndexedRaster` image pixel columns are uniformly distributed across `[-maxRange, +maxRange]`.

**Validation needed:** Verify that this matches how the images are generated.

---

### 9. **Fast Contact Search Approximation**

**Location:** `IndexedRasterTiles.generatePotentialObservationsFast()`

```java
double estimatedTimeFromStartSec = distToStart / bounds.avgSpeed;
```

**Problem:** Assumes straight-line distance from target to track start can be divided by average speed to estimate time offset. This breaks if:
- The vehicle track is curved
- The target is perpendicular to the start position
- Speed varies significantly

**Result:** The estimated index could be far off, and the fixed window size (50 samples) might miss the actual match.

**Mitigation:** The code does refine the search around the best match, which helps recover from poor initial estimates.

---

## Summary of Critical Formulas

### Tile-Level

| Conversion | Formula |
|------------|---------|
| Screen Y → Index | `index = samplesCount - (y/height × samplesCount) - 1` |
| Index → Screen Y | `y = (samplesCount - index) / samplesCount × height` |
| Screen X → Slant Range | `range = (x - width/2) / width × (2 × maxRange)` |
| Slant Range → Screen X | `x = (range + maxRange) / (2 × maxRange) × width` |
| Slant → Ground Range | `ground = √(slant² - altitude²)` |
| Range → World Position | `loc.setAzimuth(heading ± 90°); loc.setOffsetDistance(groundRange)` |

### Scrollbar

| Conversion | Formula |
|------------|---------|
| Position → Viewport Y | `y = (viewSize.h - visible.h) × (pos / (scrollbar.h - scrollHeight))` |
| Timestamp → Position | `pos = height - (fracTime × height) - (scrollHeight × fracTime)` |
| Timestamp → ScrollImage Y | `y = height - (fracTime × scrollImage.h) + scrollHeight` |

### Zoom

| Operation | Formula |
|-----------|---------|
| Tile Width | `width = viewportWidth × zoom` |
| Tile Height | `height = width × heightProportion` |
| Zoom Step | `zoom × 1.1` (in) or `zoom × 0.9` (out) |

---

## Recommendations

1. **Add explicit bounds checking** in all coordinate conversions
2. **Use double precision** consistently (avoid float)
3. **Unify time-to-position formulas** in scrollbar
4. **Replace parent hierarchy navigation** with proper component queries
5. **Add null-safety** via `Optional` or `@Nullable` annotations
6. **Validate slant-to-ground conversions** with unit tests
7. **Consider caching** frequently-used conversions (e.g., resolution calculations)
8. **Document coordinate system assumptions** in javadoc for each method

---

## References

- `RasterfallTile.java` - Individual tile coordinate management
- `RasterfallTiles.java` - Container-level coordinates and layout
- `RasterfallScrollbar.java` - Scrollbar position and time mapping
- `IndexedRasterTiles.java` - Raster-based location search algorithms
