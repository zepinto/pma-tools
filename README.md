# PMA Tools

A collection of OMST (OceanScan Marine Systems and Technology) tools for processing, visualizing, and managing marine survey data, particularly sidescan sonar and contact information.

## Table of Contents

- [Overview](#overview)
- [Projects](#projects)
- [Requirements](#requirements)
- [Building](#building)
- [Running Applications](#running-applications)
- [Publishing](#publishing)
- [Project Structure](#project-structure)

## Overview

PMA Tools is a multi-module Gradle project containing libraries and applications for marine data processing. The project uses Java 21 and includes both library modules (for integration) and standalone applications.

**Version:** 2025.11.00  
**Group:** pt.omst

## Projects

### Libraries

#### 1. **neptus-utils**
Core utilities library containing common components used across OMST applications.

**Features:**
- Logging utilities (SLF4J/Logback)
- FlatLaf UI components
- Compression utilities (Apache Commons Compress)
- Stream utilities (Commons IO)
- Base components for other modules

**Dependencies:** Standalone library with minimal external dependencies

---

#### 2. **rasterlib**
Raster data structures and utilities for sidescan sonar and contact management.

**Features:**
- Raster data structures for marine survey data
- Sidescan sonar data handling
- Contact management utilities
- Image scaling support (imgscalr)
- CSV reading capabilities
- JSON serialization (Jackson)
- Guava utilities

**Dependencies:** `neptus-utils`, SwingX, FlatLaf

---

#### 3. **omst-licences**
Licensing library for OMST Neptus applications.

**Features:**
- License validation and management
- Hardware identification (OSHI)
- License3j integration
- JSON configuration support (Gson)

**Dependencies:** `neptus-utils`, License3j, OSHI

---

#### 4. **pulvis-api**
OpenAPI-generated client for the Pulvis data management API.

**Features:**
- Auto-generated API client from OpenAPI specification
- REST client for Pulvis data manager
- JSON/HTTP communication
- Jakarta EE annotations

**Build Process:**
1. Downloads OpenAPI spec from `http://localhost:8080/v3/api-docs.yaml`
2. Generates Java client code using OpenAPI Generator
3. Compiles generated sources

**Dependencies:** Jackson, Apache HTTP Client, OpenAPI Tools

---

### Applications

#### 5. **rasterfall**
Standalone GUI application for viewing and analyzing sidescan sonar data.

**Features:**
- Sidescan data visualization
- Animated loading screen with OceanScan branding
- License checking integration
- FlatLaf modern UI
- Executable shadow JAR (fat JAR)

**Main Class:** `pt.omst.rasterfall.RasterFallApp`

**Dependencies:** `neptus-utils`, `rasterlib`, `omst-licences`

---

## Requirements

- **Java:** JDK 21 or higher
- **Build Tool:** Gradle (wrapper included)
- **OS:** Linux, macOS, or Windows

### Optional Requirements

For `pulvis-api` development:
- Running Pulvis data manager service on `http://localhost:8080`

## Building

### Build All Projects

```bash
./gradlew build
```

Or use the convenience script:

```bash
./build-all.sh
```

This will:
1. Clean all projects
2. Compile all source code
3. Run tests
4. Generate JARs (regular and shadow JARs)
5. Assemble all JARs into the `dist/` folder

### Build Individual Projects

```bash
# Build a specific module
./gradlew :neptus-utils:build
./gradlew :rasterlib:build
./gradlew :omst-licences:build
./gradlew :pulvis-api:build
./gradlew :rasterfall:build
```

### Build Only Shadow JARs (Applications)

```bash
./gradlew buildShadowJars
```

This generates executable fat JARs for:
- `rasterfall/build/libs/rasterfall-2025.11.00-all.jar`

### Clean Projects

```bash
# Clean all
./gradlew clean

# Clean specific module
./gradlew :rasterfall:clean
```

## Running Applications

### Rasterfall Viewer

**Option 1: Using Gradle**

```bash
./gradlew :rasterfall:run
```

**Option 2: Using Shadow JAR**

```bash
# Build first
./gradlew :rasterfall:shadowJar

# Run
java -jar rasterfall/build/libs/rasterfall-2025.11.00-all.jar
```

**Option 3: After build-all.sh**

```bash
./build-all.sh
java -jar dist/rasterfall-2025.11.00-all.jar
```

### Pulvis API Development

To regenerate the API client from a running server:

```bash
# Ensure Pulvis server is running on localhost:8080
./gradlew :pulvis-api:downloadOpenApiSpec
./gradlew :pulvis-api:build
```

## Publishing

### Publish to Maven Local

Publish all libraries to your local Maven repository (`~/.m2/repository`):

```bash
./gradlew publishToMavenLocalAll
```

Or publish individual modules:

```bash
./gradlew :neptus-utils:publishToMavenLocal
./gradlew :rasterlib:publishToMavenLocal
./gradlew :omst-licences:publishToMavenLocal
./gradlew :pulvis-api:publishToMavenLocal
```

### Publish to Remote Repository

```bash
./gradlew publishAll
```

**Note:** Remote publishing is configured for the OMST Maven repository at `https://omst.direct.quickconnect.to/software/maven`

## Project Structure

```
pma-tools/
├── build.gradle                 # Root build configuration
├── settings.gradle              # Multi-project setup
├── build-all.sh                # Build script
├── gradlew                     # Gradle wrapper (Linux/Mac)
├── gradlew.bat                 # Gradle wrapper (Windows)
├── conf/                       # Configuration files
│   └── licenses/               # License files
├── lib/                        # External JAR dependencies
├── dist/                       # Assembled JARs (after build-all.sh)
│
├── neptus-utils/               # Core utilities library
│   ├── src/main/java/
│   └── build.gradle
│
├── rasterlib/                  # Raster data library
│   ├── src/main/java/
│   └── build.gradle
│
├── omst-licences/              # Licensing library
│   ├── src/main/java/
│   └── build.gradle
│
├── pulvis-api/                 # OpenAPI-generated client
│   ├── src/main/resources/api/
│   └── build.gradle
│
└── rasterfall/                 # Sidescan viewer application
    ├── src/main/java/
    ├── src/main/resources/
    ├── LOADING_PANEL_README.md
    └── build.gradle
```

## Common Gradle Tasks

| Task | Description |
|------|-------------|
| `./gradlew build` | Build all projects |
| `./gradlew clean` | Clean all build outputs |
| `./gradlew test` | Run all tests |
| `./gradlew compileAll` | Compile all Java sources |
| `./gradlew buildAll` | Build all subprojects |
| `./gradlew cleanAll` | Clean all subprojects |
| `./gradlew buildShadowJars` | Build executable JARs for applications |
| `./gradlew publishToMavenLocalAll` | Publish all to local Maven |
| `./gradlew :rasterfall:run` | Run Rasterfall application |
| `./gradlew tasks` | List all available tasks |

## Development Notes

### Encoding
All source files use UTF-8 encoding.

### Lombok Support
Projects using Lombok:
- `neptus-utils`
- `rasterlib`
- `omst-licences`
- `rasterfall`

### Testing
All projects use JUnit 5 (Jupiter). Run tests with:

```bash
./gradlew test
```

### Working Directory
When running Rasterfall via Gradle (`./gradlew :rasterfall:run`), the working directory is set to the root project directory to ensure proper resource loading.

## License

Proprietary - OceanScan Marine Systems and Technology (OMST)  
For more information: https://www.oceanscan-mst.com/

## Contact

**Developer:** OMST Team  
**Email:** dev@omst.pt
