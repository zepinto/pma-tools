# Rasterfall Windows Installer

This directory contains an NSIS (Nullsoft Scriptable Install System) installer script for creating a professional Windows installer for the Rasterfall application.

## Prerequisites

### 1. Build the Application
Before building the installer, you must build the Rasterfall shadow JAR:

```bash
# From the project root directory
./gradlew :rasterfall:shadowJar
```

This will create `rasterfall/build/libs/rasterfall-all.jar`

### 2. Install NSIS
Download and install NSIS from:
- **Official Website:** https://nsis.sourceforge.io/
- **Version Required:** NSIS 3.x or higher

On Windows, the typical installation path is `C:\Program Files (x86)\NSIS\`

## Building the Installer

### On Windows

1. Build the Rasterfall shadow JAR (see Prerequisites above)

2. Open a command prompt and navigate to the rasterfall directory:
   ```cmd
   cd rasterfall
   ```

3. Run the NSIS compiler:
   ```cmd
   "C:\Program Files (x86)\NSIS\makensis.exe" rasterfall-installer.nsi
   ```

4. The installer executable will be created in the same directory:
   `RasterfallSetup-2025.11.00.exe`

### Using NSIS from PATH

If NSIS is in your system PATH, you can simply run:
```cmd
makensis rasterfall-installer.nsi
```

## Installer Features

The Windows installer includes:

1. **Java Runtime Check**
   - Checks if Java is installed on the system
   - If not found, displays a warning message with download instructions
   - Recommends Java JRE 21 or higher

2. **License File Selection**
   - Prompts the user to select a license file during installation
   - Copies the selected `.lic` file to `conf/licenses/<filename>.lic`
   - License file selection is optional (can be added later)

3. **Installation**
   - Copies `rasterfall-all.jar` to the installation directory
   - Creates a launch batch file (`Rasterfall.bat`)
   - Creates the `conf/licenses/` directory structure

4. **Shortcuts**
   - Desktop shortcut to launch Rasterfall
   - Start Menu shortcuts in "OMST" folder
   - Website shortcut to OMST homepage

5. **Uninstaller**
   - Removes all installed files
   - Removes all shortcuts
   - Cleans up registry entries
   - Removes the `conf/licenses/` directory

## Installation Process

When users run the installer, they will see:

1. Welcome page
2. Installation directory selection (default: `C:\Program Files\OMST\Rasterfall\`)
3. License file selection dialog (optional)
4. Java runtime check (with warning if not found)
5. Installation progress
6. Finish page with option to launch Rasterfall

## Default Installation Directory

```
C:\Program Files\OMST\Rasterfall\
├── rasterfall-all.jar       # Main application JAR
├── Rasterfall.bat           # Launch script
├── uninst.exe               # Uninstaller
├── Rasterfall.url           # Website shortcut
└── conf\
    └── licenses\
        └── <your-license>.lic   # User's license file (if selected)
```

## Running Rasterfall After Installation

Users can launch Rasterfall in several ways:

1. **Desktop Shortcut:** Double-click the "Rasterfall" shortcut on the desktop
2. **Start Menu:** Navigate to Start > OMST > Rasterfall
3. **Manually:** Run `Rasterfall.bat` in the installation directory
4. **Direct JAR:** Execute `java -jar rasterfall-all.jar` in the installation directory

## Uninstallation

Users can uninstall Rasterfall:

1. **Control Panel:** Settings > Apps > Rasterfall > Uninstall
2. **Start Menu:** Start > OMST > Uninstall Rasterfall
3. **Manually:** Run `uninst.exe` in the installation directory

## Customization

You can customize the installer by editing `rasterfall-installer.nsi`:

- **Version:** Update `PRODUCT_VERSION` constant
- **Company Info:** Update `PRODUCT_PUBLISHER` and `PRODUCT_WEB_SITE`
- **Installation Path:** Modify `InstallDir` variable
- **Icons:** Replace icon references (requires custom .ico files)
- **Installer Name:** Change `OutFile` to customize the installer executable name

## Troubleshooting

### Java Not Found During Installation
If the installer doesn't detect Java, but it is installed:
- Ensure Java is in the system PATH
- Try running `java -version` in a command prompt
- Reinstall Java and ensure the installer adds it to PATH

### License File Not Copied
- Ensure the license file has a `.lic` extension
- Verify the file exists at the selected path
- License files can be manually copied to `<InstallDir>\conf\licenses\` after installation

### Application Won't Start
- Verify Java JRE 21+ is installed: `java -version`
- Check that `rasterfall-all.jar` exists in the installation directory
- Try running manually: `java -jar rasterfall-all.jar` from the installation directory

## Building on Non-Windows Systems

While NSIS is primarily a Windows tool, you can build installers on Linux using:

```bash
# Install NSIS on Linux (Debian/Ubuntu)
sudo apt-get install nsis

# Build the installer
cd rasterfall
makensis rasterfall-installer.nsi
```

**Note:** Some NSIS features may not work identically on Linux/Wine.

## Support

For issues or questions:
- **Developer:** OMST Team
- **Email:** dev@omst.pt
- **Website:** https://www.oceanscan-mst.com/
