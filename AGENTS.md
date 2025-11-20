# PMA Tools Development Guide

## Project Overview

Multi-module Gradle project for marine data processing and visualization, developed by OceanScan Marine Systems & Technology (OMST). Built with Java 21, focusing on sidescan sonar data and contact management.

**Key modules:**
- `neptus-utils`: Core utilities (logging, UI, I18n)
- `rasterlib`: Marine raster data structures and visualization
- `omst-licences`: License validation using License3j
- `pulvis-api`: OpenAPI-generated client (auto-generated code)
- `rasterfall`: GUI application for sidescan data visualization (uses shadow JAR)
- `contacts`: Contact management library

**Dependency graph:** `rasterfall` → `rasterlib` + `omst-licences` → `neptus-utils`

## Build & Development Workflow

### Essential Commands
```bash
./gradlew build                    # Build all modules
./build-all.sh                     # Build + assemble to dist/
./gradlew :rasterfall:run          # Run application (working dir = project root)
./gradlew :rasterfall:shadowJar    # Create fat JAR
./gradlew publishToMavenLocalAll   # Publish to local Maven
```

### Module-Specific Patterns
- **Applications** (`rasterfall`): Use `shadowJar` plugin for executable JARs, skip standard Maven publication
- **Libraries**: Use `java-library` plugin with `withJavadocJar()` and `withSourcesJar()`
- **OpenAPI modules** (`pulvis-api`): Generate sources via `openApiGenerate` task before compilation

### Running Tests
Standard JUnit 5 across all modules: `./gradlew test`

## Code Conventions

### File Headers
All source files use this standard header:
```java
//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
```

### Logging
- Use `@Slf4j` (Lombok) for SLF4J logger injection
- Logger instance: `log.info()`, `log.error()`, etc.
- Logback configured as implementation (see individual module configs)

### UI Development Patterns
- **Look & Feel**: Always call `GuiUtils.setLookAndFeel()` before creating UI (FlatLaf dark/light themes)
- **Quick testing**: Use `GuiUtils.testFrame(component, "Title", width, height)` for component demos
- **Main methods**: Many UI components include `main()` for standalone testing (see `IndexedRasterPainter`, `SlippyMap`, `ContactEditor`)
- **Splash screens**: Use `LoadingPanel.showSplashScreen()` for app initialization (see `RasterFallApp`)

### Data Serialization
- Use `Converter` class in `rasterlib` for JSON serialization of marine data types
- Jackson configured with custom `OffsetDateTime` deserializers
- Example: `Converter.IndexedRasterFromJsonString(jsonString)`

### Internationalization (I18n)
- Use `I18n.text("key")` from `neptus-utils` for UI strings
- Supports Portuguese (`pt`) and English locales
- Key format: `button.ok`, `menu.file`, `message.error.file.notfound`

## Project-Specific Patterns

### Licensing Integration
All applications must check license on startup:
```java
LicenseChecker.checkLicense(NeptusLicense.RASTERFALL);
```
License files stored in `conf/licenses/`

### Working with Marine Data
- **Raster data**: Stored as JSON indexes (`rasterIndex`) with companion image files
- **Location handling**: Use `LocationType` from `neptus-utils` (lat/lon with NED offsets)
- **Map visualization**: `SlippyMap` component with `MapPainter` interface for overlays
- **Contact management**: Compressed contact format (`.zct` files)

### Testing UI Components
Pattern seen throughout codebase - add `main()` methods to panels/components for quick visual testing:
```java
public static void main(String[] args) {
    GuiUtils.setLookAndFeel();
    JFrame frame = new JFrame("Component Test");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(800, 600);
    frame.setContentPane(new YourComponent());
    frame.setVisible(true);
}
```

### Parallel Processing
For raster file processing, use executor pattern with half available processors:
```java
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors() / 2);
```

## External Dependencies

### Flat JAR Dependencies (`lib/`)
- `flatlaf-3.5.2.jar`: Modern UI look-and-feel (not in Maven Central)

### Custom Maven Repository
OMST repository: `https://omst.direct.quickconnect.to/software/maven`

### OpenAPI Integration
`pulvis-api` requires running Pulvis server on `localhost:8080` to download spec:
```bash
./gradlew :pulvis-api:downloadOpenApiSpec
```

## Module Architecture

**Library layers:**
1. `neptus-utils`: Foundation (no internal dependencies)
2. `rasterlib`: Depends on `neptus-utils` (marine data structures)
3. `omst-licences`: Depends on `neptus-utils` (license checking)
4. Applications: Compose libraries (e.g., `rasterfall` uses all three)

**Key directories:**
- `dist/`: Assembled JARs after `build-all.sh`
- `conf/licenses/`: License activation files
- Application working directory: Project root (for resource loading)
