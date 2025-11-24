#!/bin/bash
#***************************************************************************
# Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
#***************************************************************************
# Distribution script for creating native installers with bundled JRE      *
# for Rasterfall and Target Manager applications                           *
#***************************************************************************

set -e  # Exit on error

# Color output functions
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

error() {
    echo -e "${RED}ERROR: $1${NC}" >&2
}

success() {
    echo -e "${GREEN}$1${NC}"
}

info() {
    echo -e "${BLUE}$1${NC}"
}

warning() {
    echo -e "${YELLOW}WARNING: $1${NC}"
}

# Project metadata
PROJECT_VERSION="2025.11.00"
# Windows-compatible version (major version must be 0-255 for MSI)
WINDOWS_VERSION="25.11.0"
VENDOR="OceanScan Marine Systems & Technology"
COPYRIGHT="Copyright 2025 OceanScan - Marine Systems & Technology, Lda."

# Application configurations
declare -A APP_NAMES=(
    ["rasterfall"]="rasterfall"
    ["target-manager"]="target-manager"
)

declare -A APP_MAIN_CLASSES=(
    ["rasterfall"]="pt.omst.rasterfall.RasterFallApp"
    ["target-manager"]="pt.omst.contacts.browser.TargetManager"
)

declare -A APP_DESCRIPTIONS=(
    ["rasterfall"]="Sidescan Sonar Data Viewer and Analyzer"
    ["target-manager"]="Marine Contact Management and Visualization"
)

declare -A APP_JAR_PATHS=(
    ["rasterfall"]="rasterfall/build/libs/rasterfall-${PROJECT_VERSION}-all.jar"
    ["target-manager"]="contacts/build/libs/target-manager-${PROJECT_VERSION}-all.jar"
)

declare -A APP_ICON_NAMES=(
    ["rasterfall"]="rasterfall"
    ["target-manager"]="tgtmanager"
)

# Build status tracking
declare -A BUILD_STATUS=()
declare -A BUILD_PACKAGES=()

# Detect platform
detect_platform() {
    local os_name=$(uname -s)
    case "$os_name" in
        Linux*)
            echo "linux"
            ;;
        Darwin*)
            echo "macos"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            echo "windows"
            ;;
        *)
            error "Unsupported platform: $os_name"
            exit 1
            ;;
    esac
}

# Auto-detect package format based on platform
detect_package_format() {
    local platform=$1
    
    case "$platform" in
        linux)
            # Check /etc/os-release for distro-specific format
            if [ -f /etc/os-release ]; then
                . /etc/os-release
                case "$ID" in
                    ubuntu|debian|linuxmint|pop)
                        echo "deb"
                        return
                        ;;
                    fedora|rhel|centos|rocky|almalinux)
                        echo "rpm"
                        return
                        ;;
                esac
            fi
            # Default to deb for Linux
            echo "deb"
            ;;
        macos)
            echo "dmg"
            ;;
        windows)
            echo "msi"
            ;;
        *)
            error "Cannot detect format for platform: $platform"
            exit 1
            ;;
    esac
}

# Check prerequisites
check_prerequisites() {
    local platform=$1
    local format=$2
    
    info "Checking prerequisites..."
    
    # Check for Java 21
    if ! command -v java &> /dev/null; then
        error "Java not found. Please install JDK 21."
        exit 1
    fi
    
    local java_version=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
    if [ "$java_version" -lt 21 ]; then
        error "Java 21 or higher required. Found Java $java_version."
        exit 1
    fi
    
    # Check for jpackage
    if ! command -v jpackage &> /dev/null; then
        error "jpackage not found. Please ensure JDK 21 is properly installed."
        exit 1
    fi
    
    # Check for jlink
    if ! command -v jlink &> /dev/null; then
        error "jlink not found. Please ensure JDK 21 is properly installed."
        exit 1
    fi
    
    # Check platform-specific tools
    case "$platform" in
        linux)
            if [ "$format" = "deb" ]; then
                if ! command -v dpkg-deb &> /dev/null; then
                    warning "dpkg-deb not found. DEB package creation may fail."
                    warning "Install with: sudo apt-get install dpkg"
                fi
            elif [ "$format" = "rpm" ]; then
                if ! command -v rpmbuild &> /dev/null; then
                    warning "rpmbuild not found. RPM package creation may fail."
                    warning "Install with: sudo dnf install rpm-build"
                fi
            fi
            ;;
        windows)
            if [ "$format" = "msi" ]; then
                if ! command -v candle.exe &> /dev/null && ! command -v wix &> /dev/null; then
                    warning "WiX Toolset not found. MSI creation may fail."
                    warning "Download from: https://wixtoolset.org/"
                fi
            fi
            ;;
    esac
    
    success "Prerequisites check completed"
}

# Build shadow JARs
build_shadow_jars() {
    info "Building shadow JARs..."
    
    if ! ./gradlew buildShadowJars; then
        error "Failed to build shadow JARs"
        exit 1
    fi
    
    success "Shadow JARs built successfully"
}

# Create custom JRE using jlink
create_custom_jre() {
    local runtime_dir="dist/runtime"
    
    if [ -d "$runtime_dir" ]; then
        info "Using existing custom JRE at $runtime_dir"
        return 0
    fi
    
    info "Creating custom JRE with jlink..."
    
    # Comprehensive module list for both applications
    local modules="java.base,java.desktop,java.sql,java.logging,java.naming,java.xml,java.management,java.instrument,java.prefs,java.rmi,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,java.net.http,java.scripting"
    
    mkdir -p "$(dirname "$runtime_dir")"
    
    if ! jlink \
        --add-modules "$modules" \
        --strip-debug \
        --no-header-files \
        --no-man-pages \
        --compress=2 \
        --output "$runtime_dir"; then
        error "Failed to create custom JRE"
        exit 1
    fi
    
    local jre_size=$(du -sh "$runtime_dir" | cut -f1)
    success "Custom JRE created successfully (size: $jre_size)"
}

# Prepare resources for an application
prepare_app_resources() {
    local app_key=$1
    local app_name=${APP_NAMES[$app_key]}
    local jar_path=${APP_JAR_PATHS[$app_key]}
    
    info "Preparing resources for $app_name..."
    
    # Check if JAR exists
    if [ ! -f "$jar_path" ]; then
        error "Shadow JAR not found: $jar_path"
        return 1
    fi
    
    # Create staging directory
    local staging_dir="dist/${app_key}/input"
    rm -rf "$staging_dir"
    mkdir -p "$staging_dir"
    
    # Copy shadow JAR
    cp "$jar_path" "$staging_dir/"
    
    # Copy conf directory
    if [ -d "conf" ]; then
        cp -r conf "$staging_dir/"
    else
        warning "conf/ directory not found, skipping"
    fi
    
    success "Resources prepared for $app_name"
    return 0
}

# Get icon path for application
get_icon_path() {
    local app_key=$1
    local platform=$2
    local icon_name=${APP_ICON_NAMES[$app_key]}
    
    case "$platform" in
        windows)
            echo "app-icons/${icon_name}.ico"
            ;;
        linux|macos)
            # Use PNG for both Linux and macOS (fallback for macOS)
            echo "app-icons/${icon_name}.png"
            ;;
    esac
}

# Create portable archive for an application
create_portable_archive() {
    local app_key=$1
    local app_name=${APP_NAMES[$app_key]}
    local jar_path=${APP_JAR_PATHS[$app_key]}
    
    info "Creating portable archive for $app_name..."
    
    local portable_dir="dist/portable/${app_key}"
    rm -rf "$portable_dir"
    mkdir -p "$portable_dir"
    
    # Copy shadow JAR
    local jar_name=$(basename "$jar_path")
    cp "$jar_path" "$portable_dir/"
    
    # Copy conf directory
    if [ -d "conf" ]; then
        cp -r conf "$portable_dir/"
    fi
    
    # Create launch script for Unix
    cat > "$portable_dir/run-${app_name}.sh" << 'EOF'
#!/bin/bash
# Launch script for APPLICATION_NAME

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

java -Xmx2048m -jar JAR_NAME "$@"
EOF
    sed -i "s/APPLICATION_NAME/$app_name/g" "$portable_dir/run-${app_name}.sh"
    sed -i "s/JAR_NAME/$jar_name/g" "$portable_dir/run-${app_name}.sh"
    chmod +x "$portable_dir/run-${app_name}.sh"
    
    # Create launch script for Windows
    cat > "$portable_dir/run-${app_name}.bat" << 'EOF'
@echo off
REM Launch script for APPLICATION_NAME

cd /d "%~dp0"
java -Xmx2048m -jar JAR_NAME %*
EOF
    sed -i "s/APPLICATION_NAME/$app_name/g" "$portable_dir/run-${app_name}.bat"
    sed -i "s/JAR_NAME/$jar_name/g" "$portable_dir/run-${app_name}.bat"
    
    # Create README
    cat > "$portable_dir/README.txt" << EOF
${app_name} ${PROJECT_VERSION} - Portable Edition
================================================

Requirements:
- Java 21 or higher

Usage:

  Linux/macOS:
    ./run-${app_name}.sh
  
  Windows:
    run-${app_name}.bat

Or directly:
    java -jar ${jar_name}

Note: The application expects to find the 'conf' directory in the same
      location as the JAR file. Make sure not to separate them.

For more information, visit: https://www.oceanscan-mst.com/

${COPYRIGHT}
EOF
    
    # Create archives
    local archive_base="${app_name}-${PROJECT_VERSION}-portable"
    
    cd "dist/portable"
    
    # Create ZIP
    if command -v zip &> /dev/null; then
        zip -r "${archive_base}.zip" "${app_key}" > /dev/null
        success "Created ${archive_base}.zip"
    else
        warning "zip command not found, skipping ZIP archive"
    fi
    
    # Create TAR.GZ
    if command -v tar &> /dev/null; then
        tar -czf "${archive_base}.tar.gz" "${app_key}"
        success "Created ${archive_base}.tar.gz"
    else
        warning "tar command not found, skipping TAR.GZ archive"
    fi
    
    cd ../..
}

# Build installer for an application
build_app_installer() {
    local app_key=$1
    local platform=$2
    local format=$3
    
    local app_name=${APP_NAMES[$app_key]}
    local main_class=${APP_MAIN_CLASSES[$app_key]}
    local description=${APP_DESCRIPTIONS[$app_key]}
    local jar_path=${APP_JAR_PATHS[$app_key]}
    local jar_name=$(basename "$jar_path")
    
    info "Building $app_name installer ($format)..."
    
    # Prepare resources
    if ! prepare_app_resources "$app_key"; then
        BUILD_STATUS["$app_key"]="FAILED"
        return 1
    fi
    
    local staging_dir="dist/${app_key}/input"
    local output_dir="dist/installers/${platform}"
    mkdir -p "$output_dir"
    
    # Get icon path
    local icon_path=$(get_icon_path "$app_key" "$platform")
    if [ ! -f "$icon_path" ]; then
        warning "Icon not found: $icon_path"
        icon_path=""
    fi
    
    # Use Windows-compatible version for MSI format
    local app_version="$PROJECT_VERSION"
    if [ "$format" = "msi" ]; then
        app_version="$WINDOWS_VERSION"
        info "Using Windows-compatible version: $app_version"
    fi
    
    # Build jpackage command
    local jpackage_cmd=(
        jpackage
        --type "$format"
        --name "$app_name"
        --app-version "$app_version"
        --vendor "$VENDOR"
        --copyright "$COPYRIGHT"
        --description "$description"
        --input "$staging_dir"
        --main-jar "$jar_name"
        --main-class "$main_class"
        --runtime-image "dist/runtime"
        --dest "$output_dir"
        --java-options "-Xmx2048m"
        --java-options "-Dfile.encoding=UTF-8"
    )
    
    # Add icon if available
    if [ -n "$icon_path" ]; then
        jpackage_cmd+=(--icon "$icon_path")
    fi
    
    # Platform-specific options
    case "$platform" in
        linux)
            jpackage_cmd+=(
                --linux-shortcut
                --linux-menu-group "Development"
            )
            ;;
        macos)
            jpackage_cmd+=(
                --mac-package-name "$app_name"
            )
            ;;
        windows)
            jpackage_cmd+=(
                --win-menu
                --win-shortcut
                --win-dir-chooser
            )
            ;;
    esac
    
    # Execute jpackage
    if "${jpackage_cmd[@]}"; then
        BUILD_STATUS["$app_key"]="SUCCESS"
        
        # Find the created package
        local package_file=$(find "$output_dir" -maxdepth 1 -name "${app_name}*" -type f | head -n 1)
        if [ -n "$package_file" ]; then
            BUILD_PACKAGES["$app_key"]="$package_file"
            
            # Generate checksum
            if command -v sha256sum &> /dev/null; then
                sha256sum "$package_file" > "${package_file}.sha256"
            elif command -v shasum &> /dev/null; then
                shasum -a 256 "$package_file" > "${package_file}.sha256"
            fi
            
            success "Built $app_name installer: $(basename "$package_file")"
        fi
        return 0
    else
        BUILD_STATUS["$app_key"]="FAILED"
        error "Failed to build $app_name installer"
        return 1
    fi
}

# Generate build summary
print_build_summary() {
    local platform=$1
    local format=$2
    
    echo ""
    info "=========================================="
    info "           BUILD SUMMARY"
    info "=========================================="
    echo ""
    echo "Platform:     $platform"
    echo "Format:       $format"
    echo "Version:      $PROJECT_VERSION"
    echo ""
    
    printf "%-20s %-10s %-50s %-10s\n" "Application" "Status" "Package" "Size"
    echo "--------------------------------------------------------------------------------------------------------"
    
    for app_key in "${!APP_NAMES[@]}"; do
        local app_name=${APP_NAMES[$app_key]}
        local status=${BUILD_STATUS[$app_key]:-"SKIPPED"}
        local package=${BUILD_PACKAGES[$app_key]:-"N/A"}
        local size="N/A"
        
        if [ -f "$package" ]; then
            size=$(du -h "$package" | cut -f1)
            package=$(basename "$package")
        fi
        
        if [ "$status" = "SUCCESS" ]; then
            printf "${GREEN}%-20s %-10s${NC} %-50s %-10s\n" "$app_name" "$status" "$package" "$size"
        else
            printf "${RED}%-20s %-10s${NC} %-50s %-10s\n" "$app_name" "$status" "$package" "$size"
        fi
    done
    
    echo ""
    
    # Show portable archives
    if [ -d "dist/portable" ]; then
        info "Portable Archives:"
        find dist/portable -maxdepth 1 -type f \( -name "*.zip" -o -name "*.tar.gz" \) | while read -r archive; do
            local size=$(du -h "$archive" | cut -f1)
            echo "  - $(basename "$archive") ($size)"
        done
        echo ""
    fi
    
    # Show shadow JARs
    if [ -d "dist/jars" ]; then
        info "Shadow JARs:"
        find dist/jars -maxdepth 1 -type f -name "*-all.jar" | while read -r jar; do
            local size=$(du -h "$jar" | cut -f1)
            echo "  - $(basename "$jar") ($size)"
        done
        echo ""
    fi
    
    info "=========================================="
}

# Main execution
main() {
    echo ""
    info "=========================================="
    info "  PMA Tools Distribution Builder"
    info "=========================================="
    echo ""
    
    # Detect platform and format
    local platform=$(detect_platform)
    local format=$(detect_package_format "$platform")
    
    info "Detected platform: $platform"
    info "Package format: $format"
    echo ""
    
    # Check prerequisites
    check_prerequisites "$platform" "$format"
    echo ""
    
    # Build shadow JARs
    build_shadow_jars
    echo ""
    
    # Create custom JRE
    create_custom_jre
    echo ""
    
    # Copy shadow JARs to dist/jars
    info "Copying shadow JARs to dist/jars..."
    mkdir -p dist/jars
    for app_key in "${!APP_JAR_PATHS[@]}"; do
        local jar_path=${APP_JAR_PATHS[$app_key]}
        if [ -f "$jar_path" ]; then
            cp "$jar_path" dist/jars/
        fi
    done
    success "Shadow JARs copied"
    echo ""
    
    # Build installers for each application
    for app_key in rasterfall target-manager; do
        build_app_installer "$app_key" "$platform" "$format" || true
        echo ""
    done
    
    # Create portable archives
    info "Creating portable archives..."
    for app_key in rasterfall target-manager; do
        create_portable_archive "$app_key" || warning "Failed to create portable archive for ${APP_NAMES[$app_key]}"
    done
    echo ""
    
    # Clean up staging directories
    info "Cleaning up staging directories..."
    rm -rf dist/rasterfall/input
    rm -rf dist/target-manager/input
    success "Cleanup completed"
    echo ""
    
    # Print summary
    print_build_summary "$platform" "$format"
    
    # Final message
    local success_count=0
    for status in "${BUILD_STATUS[@]}"; do
        if [ "$status" = "SUCCESS" ]; then
            ((success_count++))
        fi
    done
    
    if [ $success_count -eq ${#APP_NAMES[@]} ]; then
        success "All applications built successfully!"
        exit 0
    elif [ $success_count -gt 0 ]; then
        warning "Some applications built successfully, but others failed."
        exit 1
    else
        error "All builds failed!"
        exit 1
    fi
}

# Run main
main "$@"
