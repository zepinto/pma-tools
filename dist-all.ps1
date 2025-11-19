#***************************************************************************
# Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
#***************************************************************************
# Distribution script for creating native installers with bundled JRE      *
# for Rasterfall and Target Manager applications on Windows                *
#***************************************************************************

# Exit on error
$ErrorActionPreference = "Stop"

# Project metadata
$PROJECT_VERSION = "2025.11.00"
$VENDOR = "OceanScan Marine Systems & Technology"
$COPYRIGHT = "Copyright 2025 OceanScan - Marine Systems & Technology, Lda."

# Application configurations
$APP_NAMES = @{
    "rasterfall" = "rasterfall"
    "target-manager" = "target-manager"
}

$APP_MAIN_CLASSES = @{
    "rasterfall" = "pt.omst.rasterfall.RasterFallApp"
    "target-manager" = "pt.omst.contacts.browser.TargetManager"
}

$APP_DESCRIPTIONS = @{
    "rasterfall" = "Sidescan Sonar Data Viewer and Analyzer"
    "target-manager" = "Marine Contact Management and Visualization"
}

$APP_JAR_PATHS = @{
    "rasterfall" = "rasterfall/build/libs/rasterfall-$PROJECT_VERSION-all.jar"
    "target-manager" = "contacts/build/libs/target-manager-$PROJECT_VERSION-all.jar"
}

$APP_ICON_NAMES = @{
    "rasterfall" = "rasterfall"
    "target-manager" = "tgtmanager"
}

# Build status tracking
$BUILD_STATUS = @{}
$BUILD_PACKAGES = @{}

# Color output functions
function Write-Error-Msg {
    param([string]$Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
}

function Write-Success {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Green
}

function Write-Info {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Cyan
}

function Write-Warning-Msg {
    param([string]$Message)
    Write-Host "WARNING: $Message" -ForegroundColor Yellow
}

# Check prerequisites
function Test-Prerequisites {
    Write-Info "Checking prerequisites..."
    
    # Check for Java 21
    # Note: java -version outputs to stderr, so we capture with 2>&1
    $javaVersion = $null
    
    try {
        # Capture java version output (goes to stderr)
        $versionOutput = & java -version 2>&1 | Out-String
        $javaVersion = $versionOutput | Select-String -Pattern 'version "([^"]+)"' | Select-Object -First 1
        
        if (-not $javaVersion) {
            throw "Could not get Java version"
        }
    } catch {
        Write-Error-Msg "Java not found in PATH. Please ensure JDK 21 is installed and 'java' command is available."
        Write-Host "Error: $_" -ForegroundColor Yellow
        exit 1
    }
    
    # Parse version - extract the version number from the match
    if ($javaVersion -match 'version "(\d+)\.') {
        $majorVersion = [int]$Matches[1]
    } elseif ($javaVersion -match 'version "(\d+)"') {
        $majorVersion = [int]$Matches[1]
    } else {
        Write-Error-Msg "Could not parse Java version from: $javaVersion"
        exit 1
    }
    
    if ($majorVersion -lt 21) {
        Write-Error-Msg "Java 21 or higher required. Found Java $majorVersion."
        exit 1
    }
    
    Write-Success "Found Java $majorVersion"
    
    # Check for jpackage
    try {
        $null = & jpackage --version 2>&1
    } catch {
        Write-Error-Msg "jpackage not found. Please ensure JDK 21 is properly installed."
        exit 1
    }
    
    # Check for jlink
    try {
        $null = & jlink --version 2>&1
    } catch {
        Write-Error-Msg "jlink not found. Please ensure JDK 21 is properly installed."
        exit 1
    }
    
    # Check for WiX Toolset
    try {
        $null = & where.exe candle.exe 2>&1
    } catch {
        Write-Warning-Msg "WiX Toolset not found. MSI creation may fail."
        Write-Warning-Msg "Download from: https://wixtoolset.org/"
    }
    
    Write-Success "Prerequisites check completed"
}

# Build shadow JARs
function Build-ShadowJars {
    Write-Info "Building shadow JARs..."
    
    $gradleCmd = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { "gradle" }
    
    & $gradleCmd buildShadowJars
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Msg "Failed to build shadow JARs"
        exit 1
    }
    
    Write-Success "Shadow JARs built successfully"
}

# Create custom JRE using jlink
function New-CustomJRE {
    $runtimeDir = "dist\runtime"
    
    if (Test-Path $runtimeDir) {
        Write-Info "Using existing custom JRE at $runtimeDir"
        return
    }
    
    Write-Info "Creating custom JRE with jlink..."
    
    # Comprehensive module list for both applications
    $modules = "java.base,java.desktop,java.sql,java.logging,java.naming,java.xml,java.management,java.instrument,java.prefs,java.rmi,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,java.net.http,java.scripting"
    
    New-Item -ItemType Directory -Force -Path "dist" | Out-Null
    
    & jlink `
        --add-modules $modules `
        --strip-debug `
        --no-header-files `
        --no-man-pages `
        --compress=2 `
        --output $runtimeDir
        
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Msg "Failed to create custom JRE"
        exit 1
    }
    
    $jreSize = (Get-ChildItem $runtimeDir -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Success "Custom JRE created successfully (size: $([math]::Round($jreSize, 2)) MB)"
}

# Prepare resources for an application
function Copy-AppResources {
    param([string]$AppKey)
    
    $appName = $APP_NAMES[$AppKey]
    $jarPath = $APP_JAR_PATHS[$AppKey]
    
    Write-Info "Preparing resources for $appName..."
    
    # Check if JAR exists
    if (-not (Test-Path $jarPath)) {
        Write-Error-Msg "Shadow JAR not found: $jarPath"
        return $false
    }
    
    # Create staging directory
    $stagingDir = "dist\$AppKey\input"
    if (Test-Path $stagingDir) {
        Remove-Item -Recurse -Force $stagingDir
    }
    New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
    
    # Copy shadow JAR
    Copy-Item $jarPath $stagingDir\
    
    # Copy conf directory
    if (Test-Path "conf") {
        Copy-Item -Recurse "conf" $stagingDir\
    } else {
        Write-Warning-Msg "conf/ directory not found, skipping"
    }
    
    Write-Success "Resources prepared for $appName"
    return $true
}

# Get icon path for application
function Get-IconPath {
    param([string]$AppKey)
    
    $iconName = $APP_ICON_NAMES[$AppKey]
    return "app-icons\$iconName.ico"
}

# Create portable archive for an application
function New-PortableArchive {
    param([string]$AppKey)
    
    $appName = $APP_NAMES[$AppKey]
    $jarPath = $APP_JAR_PATHS[$AppKey]
    
    Write-Info "Creating portable archive for $appName..."
    
    $portableDir = "dist\portable\$AppKey"
    if (Test-Path $portableDir) {
        Remove-Item -Recurse -Force $portableDir
    }
    New-Item -ItemType Directory -Force -Path $portableDir | Out-Null
    
    # Copy shadow JAR
    $jarName = Split-Path $jarPath -Leaf
    Copy-Item $jarPath $portableDir\
    
    # Copy conf directory
    if (Test-Path "conf") {
        Copy-Item -Recurse "conf" $portableDir\
    }
    
    # Create launch script for Windows
    $batContent = @"
@echo off
REM Launch script for $appName

cd /d "%~dp0"
java -Xmx2048m -jar $jarName %*
"@
    Set-Content -Path "$portableDir\run-$appName.bat" -Value $batContent
    
    # Create launch script for Unix
    $shContent = @"
#!/bin/bash
# Launch script for $appName

SCRIPT_DIR="`$( cd "`$( dirname "`${BASH_SOURCE[0]}" )" && pwd )"
cd "`$SCRIPT_DIR"

java -Xmx2048m -jar $jarName "`$@"
"@
    Set-Content -Path "$portableDir\run-$appName.sh" -Value $shContent
    
    # Create README
    $readmeContent = @"
$appName $PROJECT_VERSION - Portable Edition
================================================

Requirements:
- Java 21 or higher

Usage:

  Windows:
    run-$appName.bat
  
  Linux/macOS:
    ./run-$appName.sh

Or directly:
    java -jar $jarName

Note: The application expects to find the 'conf' directory in the same
      location as the JAR file. Make sure not to separate them.

For more information, visit: https://www.oceanscan-mst.com/

$COPYRIGHT
"@
    Set-Content -Path "$portableDir\README.txt" -Value $readmeContent
    
    # Create ZIP archive
    $archiveBase = "$appName-$PROJECT_VERSION-portable"
    
    Push-Location "dist\portable"
    
    if (Get-Command Compress-Archive -ErrorAction SilentlyContinue) {
        Compress-Archive -Path $AppKey -DestinationPath "$archiveBase.zip" -Force
        Write-Success "Created $archiveBase.zip"
    } else {
        Write-Warning-Msg "Compress-Archive not available, skipping ZIP archive"
    }
    
    Pop-Location
}

# Build installer for an application
function Build-AppInstaller {
    param([string]$AppKey)
    
    $appName = $APP_NAMES[$AppKey]
    $mainClass = $APP_MAIN_CLASSES[$AppKey]
    $description = $APP_DESCRIPTIONS[$AppKey]
    $jarPath = $APP_JAR_PATHS[$AppKey]
    $jarName = Split-Path $jarPath -Leaf
    
    Write-Info "Building $appName installer (MSI)..."
    
    # Prepare resources
    if (-not (Copy-AppResources $AppKey)) {
        $BUILD_STATUS[$AppKey] = "FAILED"
        return $false
    }
    
    $stagingDir = "dist\$AppKey\input"
    $outputDir = "dist\installers\windows"
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    
    # Get icon path
    $iconPath = Get-IconPath $AppKey
    $iconArg = @()
    if (Test-Path $iconPath) {
        $iconArg = @("--icon", $iconPath)
    } else {
        Write-Warning-Msg "Icon not found: $iconPath"
    }
    
    # Build jpackage command
    $jpackageArgs = @(
        "--type", "msi",
        "--name", $appName,
        "--app-version", $PROJECT_VERSION,
        "--vendor", $VENDOR,
        "--copyright", $COPYRIGHT,
        "--description", $description,
        "--input", $stagingDir,
        "--main-jar", $jarName,
        "--main-class", $mainClass,
        "--runtime-image", "dist\runtime",
        "--dest", $outputDir,
        "--java-options", "-Xmx2048m",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--win-menu",
        "--win-shortcut",
        "--win-dir-chooser"
    ) + $iconArg
    
    # Execute jpackage
    & jpackage @jpackageArgs
    
    if ($LASTEXITCODE -eq 0) {
        $BUILD_STATUS[$AppKey] = "SUCCESS"
        
        # Find the created package
        $packageFile = Get-ChildItem "$outputDir\$appName*.msi" | Select-Object -First 1
        if ($packageFile) {
            $BUILD_PACKAGES[$AppKey] = $packageFile.FullName
            
            # Generate checksum
            $hash = Get-FileHash -Algorithm SHA256 -Path $packageFile.FullName
            Set-Content -Path "$($packageFile.FullName).sha256" -Value "$($hash.Hash)  $($packageFile.Name)"
            
            Write-Success "Built $appName installer: $($packageFile.Name)"
        }
        return $true
    } else {
        $BUILD_STATUS[$AppKey] = "FAILED"
        Write-Error-Msg "Failed to build $appName installer"
        return $false
    }
}

# Print build summary
function Show-BuildSummary {
    Write-Host ""
    Write-Info "=========================================="
    Write-Info "           BUILD SUMMARY"
    Write-Info "=========================================="
    Write-Host ""
    Write-Host "Platform:     Windows"
    Write-Host "Format:       MSI"
    Write-Host "Version:      $PROJECT_VERSION"
    Write-Host ""
    
    Write-Host ("{0,-20} {1,-10} {2,-50} {3,-10}" -f "Application", "Status", "Package", "Size")
    Write-Host ("-" * 100)
    
    foreach ($appKey in $APP_NAMES.Keys) {
        $appName = $APP_NAMES[$appKey]
        $status = if ($BUILD_STATUS.ContainsKey($appKey)) { $BUILD_STATUS[$appKey] } else { "SKIPPED" }
        $package = if ($BUILD_PACKAGES.ContainsKey($appKey)) { $BUILD_PACKAGES[$appKey] } else { "N/A" }
        $size = "N/A"
        
        if (Test-Path $package) {
            $sizeBytes = (Get-Item $package).Length
            $sizeMB = [math]::Round($sizeBytes / 1MB, 2)
            $size = "$sizeMB MB"
            $package = Split-Path $package -Leaf
        }
        
        if ($status -eq "SUCCESS") {
            Write-Host ("{0,-20} {1,-10} {2,-50} {3,-10}" -f $appName, $status, $package, $size) -ForegroundColor Green
        } else {
            Write-Host ("{0,-20} {1,-10} {2,-50} {3,-10}" -f $appName, $status, $package, $size) -ForegroundColor Red
        }
    }
    
    Write-Host ""
    
    # Show portable archives
    if (Test-Path "dist\portable") {
        Write-Info "Portable Archives:"
        Get-ChildItem "dist\portable\*.zip" | ForEach-Object {
            $sizeMB = [math]::Round($_.Length / 1MB, 2)
            Write-Host "  - $($_.Name) ($sizeMB MB)"
        }
        Write-Host ""
    }
    
    # Show shadow JARs
    if (Test-Path "dist\jars") {
        Write-Info "Shadow JARs:"
        Get-ChildItem "dist\jars\*-all.jar" | ForEach-Object {
            $sizeMB = [math]::Round($_.Length / 1MB, 2)
            Write-Host "  - $($_.Name) ($sizeMB MB)"
        }
        Write-Host ""
    }
    
    Write-Info "=========================================="
}

# Main execution
function Main {
    Write-Host ""
    Write-Info "=========================================="
    Write-Info "  PMA Tools Distribution Builder"
    Write-Info "=========================================="
    Write-Host ""
    
    Write-Info "Platform: Windows"
    Write-Info "Package format: MSI"
    Write-Host ""
    
    # Check prerequisites
    Test-Prerequisites
    Write-Host ""
    
    # Build shadow JARs
    Build-ShadowJars
    Write-Host ""
    
    # Create custom JRE
    New-CustomJRE
    Write-Host ""
    
    # Copy shadow JARs to dist/jars
    Write-Info "Copying shadow JARs to dist\jars..."
    New-Item -ItemType Directory -Force -Path "dist\jars" | Out-Null
    foreach ($appKey in $APP_JAR_PATHS.Keys) {
        $jarPath = $APP_JAR_PATHS[$appKey]
        if (Test-Path $jarPath) {
            Copy-Item $jarPath "dist\jars\"
        }
    }
    Write-Success "Shadow JARs copied"
    Write-Host ""
    
    # Build installers for each application
    foreach ($appKey in @("rasterfall", "target-manager")) {
        Build-AppInstaller $appKey
        Write-Host ""
    }
    
    # Create portable archives
    Write-Info "Creating portable archives..."
    foreach ($appKey in @("rasterfall", "target-manager")) {
        try {
            New-PortableArchive $appKey
        } catch {
            Write-Warning-Msg "Failed to create portable archive for $($APP_NAMES[$appKey])"
        }
    }
    Write-Host ""
    
    # Clean up staging directories
    Write-Info "Cleaning up staging directories..."
    Remove-Item -Recurse -Force "dist\rasterfall\input" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "dist\target-manager\input" -ErrorAction SilentlyContinue
    Write-Success "Cleanup completed"
    Write-Host ""
    
    # Print summary
    Show-BuildSummary
    
    # Final message
    $successCount = ($BUILD_STATUS.Values | Where-Object { $_ -eq "SUCCESS" }).Count
    $totalApps = $APP_NAMES.Count
    
    if ($successCount -eq $totalApps) {
        Write-Success "All applications built successfully!"
        exit 0
    } elseif ($successCount -gt 0) {
        Write-Warning-Msg "Some applications built successfully, but others failed."
        exit 1
    } else {
        Write-Error-Msg "All builds failed!"
        exit 1
    }
}

# Run main
Main
