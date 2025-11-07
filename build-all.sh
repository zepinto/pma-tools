#!/bin/bash

# script to compile all projects using Gradle

set -e  # Exit on error

echo "Starting  compilation..."

# Compile all projects
./gradlew clean build

echo "Compilation completed successfully!"

# Create dist directory
echo "Assembling jars into dist/ folder..."
rm -rf dist
mkdir -p dist

# Find and copy all jars and shadow jars to dist/ (excluding javadoc and sources)
find . -type f \( -name "*.jar" -o -name "*-all.jar" \) \
    -path "*/build/libs/*" \
    ! -name "*-javadoc.jar" \
    ! -name "*-sources.jar" \
    -exec cp -rv {} dist/ \;

echo "All jars assembled in dist/ folder"
ls -lh dist/
