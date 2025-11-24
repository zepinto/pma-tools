#!/bin/bash
#***************************************************************************
# Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
#***************************************************************************
# Author: José Pinto                                                       *
#***************************************************************************

# Script to build Windows executables using Launch4j from Linux

set -e  # Exit on error

echo "======================================"
echo "Building Windows Executables"
echo "======================================"
echo ""

# Check if launch4j is available
if ! command -v launch4j &> /dev/null; then
    echo "WARNING: launch4j command not found in PATH"
    echo "The Gradle plugin will download and use its own Launch4j binary"
    echo ""
fi

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build all projects and create Windows executables
echo ""
echo "Building projects and creating Windows executables..."
./gradlew build :rasterfall:createExe :contacts:createExe

echo ""
echo "======================================"
echo "Creating distribution folder"
echo "======================================"

# Create dist directory
rm -rf dist
mkdir -p dist

# Copy shadow JARs
echo "Copying JAR files..."
find . -type f -name "*-all.jar" -path "*/build/libs/*" -exec cp -v {} dist/ \;

# Copy Windows executables
echo ""
echo "Copying Windows executables..."
find . -type f -name "*.exe" -path "*/build/launch4j/*" -exec cp -v {} dist/ \;

# Copy required resources
echo ""
echo "Copying configuration and resources..."
cp -r conf dist/ 2>/dev/null || true
cp -r sample-data dist/ 2>/dev/null || true

echo ""
echo "======================================"
echo "Build Complete!"
echo "======================================"
echo ""
echo "Distribution contents:"
ls -lh dist/
echo ""
echo "Windows executables created:"
ls -lh dist/*.exe 2>/dev/null || echo "No .exe files found (check for errors above)"
echo ""
echo "To run on Windows:"
echo "  1. Copy the dist/ folder to your Windows machine"
echo "  2. Ensure Java 21+ is installed (or bundle a JRE in jre/ folder)"
echo "  3. Run: rasterfall.exe or target-manager.exe"
echo ""
