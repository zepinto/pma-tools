# Rasterfall: Advanced Marine Data Visualization

> 🎯 **Presentation Guide**: Each slide includes speaker notes. Suggested duration: 15-20 minutes.

---

## Slide 1: Title

# 🌊 RASTERFALL

### Advanced Marine Data Visualization & Analysis

**Presenter**: [Your Name]  
**Organization**: OceanScan - Marine Systems & Technology (OMST)  
**Date**: [Presentation Date]

> *Speaker Notes*: Introduce yourself and OMST's role in marine technology. Set expectations for the demo.

---

## Slide 2: The Challenge

### 🔍 Marine Survey Data is Complex

| Challenge | Impact |
|-----------|--------|
| 📦 **Massive Datasets** | Single missions generate GBs of sidescan imagery |
| 🔗 **Disconnected Data** | Rasters, contacts, and metadata live in separate silos |
| ⏱️ **Temporal Correlation** | Hard to answer: "What did we see at 14:32?" |
| 📁 **Scattered Files** | Mission data spread across dozens of folders |

### Pain Points for Operators:
*   ❌ Hours spent manually correlating data
*   ❌ No unified view of survey coverage
*   ❌ Difficult to filter by time or region

> *Speaker Notes*: Ask audience if they've experienced these challenges. Build empathy before solution.

---

## Slide 3: Introducing Rasterfall

### ✅ The Solution

> **Rasterfall** is a desktop application that unifies marine survey visualization with intelligent time and space filtering.

### Core Design Principles:

| Principle | Description |
|-----------|-------------|
| 🔄 **Integration** | Map + Raster + Contacts in a single synchronized view |
| ⚡ **Performance** | Lazy loading & background processing for large datasets |
| 🎯 **Usability** | Intuitive drag-to-filter timeline controls |
| 🔧 **Extensibility** | Modular architecture for future enhancements |

### One-Liner:
> *"See your entire survey mission on a map, then slice through time to find exactly what you need."*

> *Speaker Notes*: This is the "aha moment" slide. Emphasize the unified view concept.

---

## Slide 4: Feature Deep-Dive — Interactive Mapping

### 🗺️ SlippyMap Component

**Base Map Options**:
*   🌍 OpenStreetMap (detailed coastlines)
*   🛰️ Esri Satellite (visual context)
*   🗺️ CartoDB (minimal, clean)

**Navigation**:
*   🖱️ Scroll to zoom (smooth, continuous)
*   ✋ Drag to pan
*   📍 Real-time Lat/Lon under cursor
*   🔍 Automatic LOD adjustment

**Overlays**:
*   📊 Sidescan raster mosaics
*   📌 Contact markers with labels
*   🔲 Coverage polygons

> *Speaker Notes*: Demo the map here. Show switching base maps, zooming, panning.

---

## Slide 5: Feature Deep-Dive — Time-Travel Analysis

### ⏰ Interactive Timeline

```
[====|==============|====]
   Start           End
     ↑               ↑
   Drag handles to filter
```

**Capabilities**:
*   📅 Visual timeline spanning entire dataset
*   🔎 Zoom into specific hours/minutes
*   ↔️ Pan through long missions
*   🏷️ Smart labels (auto-format: years → seconds)

### Dynamic Filtering:
*   🎬 **Real-time updates** as handles move
*   🗺️ Map instantly shows only matching rasters
*   📌 Contacts filtered to selected time window
*   📊 Console shows: `"Updated: 5 rasters, 23 contacts visible"`

> *Speaker Notes*: This is the "wow" feature. Demo dragging the timeline and watching the map update live.

---

## Slide 6: Feature Deep-Dive — Data Management

### 📂 DataSource Manager

**Adding Data**:
```
[📁 Add Folder] [🗄️ Add Database]
```
↓
```
[Mission_001 ✕] [Mission_002 ✕] [Survey_North ✕]
```

**Smart Features**:
*   ✅ Recursive folder scanning
*   ✅ Duplicate/overlap detection
*   ✅ Visual "chip" management
*   ✅ One-click source removal

### Supported Formats:

| Format | Extension | Description |
|--------|-----------|-------------|
| Raster Index | `.json` | Metadata + image reference |
| Raster Image | `.png` | Sidescan waterfall image |
| Contact | `.zct` | Compressed contact archive |

> *Speaker Notes*: Show adding a sample data folder. Explain the chip UI.

---

## Slide 7: Contact Management

### 📌 Rich Contact Visualization

**On the Map**:
*   🔴 Color-coded markers by classification
*   🏷️ Labels with high-contrast borders
*   🖼️ Hover to preview thumbnail
*   🔄 Click-cycle through stacked contacts

**Selection Tools**:
*   🖱️ **Click**: Select single contact
*   ⬜ **Right-drag**: Rectangle selection
*   ⭕ **Shift+Right-drag**: Circle selection (with radius in meters!)

**Grouping**:
*   Select multiple contacts → "Group Selected..."
*   Merge related detections into unified targets

> *Speaker Notes*: Demo the circle selection showing the radius. This is a unique feature.

---

## Slide 8: Technical Architecture

### 🏗️ Modular Design

```
┌─────────────────────────────────────────────┐
│              RASTERFALL APP                 │
│         (Main GUI Application)              │
└─────────────────┬───────────────────────────┘
                  │
    ┌─────────────┼─────────────┬─────────────┐
    ▼             ▼             ▼             ▼
┌─────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐
│contacts │ │rasterlib │ │omst-     │ │neptus-utils│
│         │ │          │ │licences  │ │            │
│ • Browser│ │ • Raster │ │ • License│ │ • SlippyMap│
│ • Groups │ │   Types  │ │   Check  │ │ • I18n     │
│ • ZCT    │ │ • Painter│ │          │ │ • GuiUtils │
└─────────┘ └──────────┘ └──────────┘ └────────────┘
```

### Tech Stack:

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Build | Gradle (multi-module) |
| UI Framework | Swing + FlatLaf |
| Serialization | Jackson JSON |
| Packaging | Shadow JAR |

> *Speaker Notes*: For technical audiences. Skip for end-users.

---

## Slide 9: Performance Engineering

### ⚡ Built for Scale

| Technique | Benefit |
|-----------|---------|
| **Lazy Loading** | Images load only when visible |
| **Background Mosaics** | Heavy rendering off UI thread |
| **Tile Caching** | Memory + disk cache for map tiles |
| **Visibility Wrappers** | `TimeFilteredRasterPainter` toggles without recreation |
| **Parallel Processing** | Uses `N/2` CPU cores for raster scanning |

### Real-World Performance:
*   ✅ 100s of rasters — smooth scrolling
*   ✅ 1000s of contacts — instant filtering
*   ✅ Large images — adaptive mosaic resolution

> *Speaker Notes*: Mention specific numbers if you have benchmarks.

---

## Slide 10: Debug & Quality Assurance

### 🔧 Built-in Debug Mode

**Toggle**: `Ctrl+D` or `-Drasterfall.debug=true`

**Debug Overlays**:
*   📍 Coordinate transformation accuracy
*   🔲 Tile boundary visualization
*   📊 Viewport state metrics
*   🧪 Screen↔World roundtrip tests

**Why It Matters**:
*   Validates geospatial accuracy
*   Essential for certification workflows
*   Helps diagnose rendering issues

> *Speaker Notes*: Quick demo of debug mode if time permits.

---

## Slide 11: Roadmap & Vision

### 🚀 Future Enhancements

| Phase | Feature | Status |
|-------|---------|--------|
| Q1 | Statistics panel (visible/total counts) | 📋 Planned |
| Q2 | Export filtered datasets | 📋 Planned |
| Q2 | Database data sources | 📋 Planned |
| Q3 | Advanced contact search | 📋 Planned |
| Q3 | Layer visibility toggles | 📋 Planned |
| Q4 | AI-assisted contact classification | 🔬 Research |

### Community Input:
> *What features would help YOUR workflow?*

> *Speaker Notes*: Invite discussion. Collect feature requests.

---

## Slide 12: Live Demo

### 🎬 Let's See It in Action!

**Demo Script**:
1.  Launch Rasterfall
2.  Add sample data folder
3.  Explore map (zoom, pan, base maps)
4.  Filter by time range
5.  Select and inspect contacts
6.  Show circle selection with radius
7.  Toggle debug mode

> *Speaker Notes*: Keep demo under 5 minutes. Have backup screenshots if live demo fails.

---

## Slide 13: Getting Started

### 📥 Installation

```bash
# Clone repository
git clone <repository-url>
cd pma-tools

# Build
./gradlew build

# Run
./gradlew :rasterfall:run
```

### Requirements:
*   ☕ Java 21+
*   📄 Valid OMST License
*   💾 4GB+ RAM recommended

### Documentation:
*   📖 `MAP_VIEWER_README.md`
*   📖 `IMPLEMENTATION_SUMMARY.md`
*   📖 `DEBUG_MODE.md`

---

## Slide 14: Q&A

# ❓ Questions?

### Contact:
*   📧 [email@oceanscan.pt]
*   🌐 [www.oceanscan.pt]
*   📍 Leça da Palmeira, Portugal

### Thank You!

> *"Bringing clarity to the depths."*
