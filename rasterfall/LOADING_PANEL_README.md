# LoadingPanel - Animated Splash Screen

A sleek, modern loading panel component for RasterFall applications featuring the OceanScan logo with smooth animations.

## Features

- **OceanScan Logo**: Displays the official OceanScan branding
- **Animated Spinner**: Smooth rotating circular spinner with glow effects
- **Gradient Background**: Modern dark blue gradient background
- **Status Messages**: Dynamic status text that can be updated during loading
- **Easy Integration**: Simple static methods for showing/hiding splash screens

## Visual Design

- **Background**: Dark blue gradient (from RGB(10,25,45) to RGB(20,40,70))
- **Spinner**: Cyan-colored animated arc with pulsing glow effect
- **Logo**: OceanScan logo centered at 400x120px
- **Text**: White status text at 14pt Arial

## Usage

### Basic Splash Screen

```java
// Show splash screen
JWindow splash = LoadingPanel.showSplashScreen();

// ... perform loading operations ...

// Hide splash screen
LoadingPanel.hideSplashScreen(splash);
```

### Splash Screen with Status Updates

```java
// Show splash screen with initial message
JWindow splash = LoadingPanel.showSplashScreen("Loading application...");
LoadingPanel loadingPanel = LoadingPanel.getLoadingPanel(splash);

// Update status during loading
if (loadingPanel != null) {
    loadingPanel.setStatus("Loading modules...");
    // ... load modules ...
    
    loadingPanel.setStatus("Initializing components...");
    // ... initialize components ...
}

// Close splash screen
LoadingPanel.hideSplashScreen(splash);
```

### Integration Example (from RasterFallApp)

```java
public static void main(String[] args) {
    // Show splash screen during startup
    JWindow splash = LoadingPanel.showSplashScreen("Starting Sidescan RasterFall...");
    LoadingPanel loadingPanel = LoadingPanel.getLoadingPanel(splash);
    
    // Initialize application in background
    new Thread(() -> {
        try {
            if (loadingPanel != null) loadingPanel.setStatus("Setting look and feel...");
            GuiUtils.setLookAndFeel();
            
            if (loadingPanel != null) loadingPanel.setStatus("Checking license...");
            LicenseChecker.checkLicense(NeptusLicense.RASTERFALL);
            
            if (loadingPanel != null) loadingPanel.setStatus("Initializing application...");
            
            SwingUtilities.invokeLater(() -> {
                RasterFallApp app = new RasterFallApp();
                app.setVisible(true);
                LoadingPanel.hideSplashScreen(splash);
            });
        } catch (Exception ex) {
            LoadingPanel.hideSplashScreen(splash);
            // Handle error
        }
    }).start();
}
```

## API Reference

### Static Methods

#### `showSplashScreen()`
Shows a loading splash screen window with default "Initializing..." message.

**Returns:** `JWindow` - the splash screen window

#### `showSplashScreen(String status)`
Shows a loading splash screen with a custom initial status message.

**Parameters:**
- `status` - initial status message to display

**Returns:** `JWindow` - the splash screen window

#### `getLoadingPanel(JWindow splash)`
Gets the LoadingPanel instance from a splash screen window.

**Parameters:**
- `splash` - the splash screen window

**Returns:** `LoadingPanel` - the LoadingPanel instance, or null if not found

#### `hideSplashScreen(JWindow splash)`
Hides and disposes the splash screen, stopping all animations.

**Parameters:**
- `splash` - the splash screen window to hide

### Instance Methods

#### `setStatus(String status)`
Updates the status message displayed on the loading panel.

**Parameters:**
- `status` - the new status message

#### `stopAnimation()`
Stops the loading animation (automatically called when hiding the splash screen).

## Testing

Run the demo main method to see the LoadingPanel in action:

```bash
cd /home/zp/workspace/pma-tools
./gradlew :rasterfall:run -PmainClass=pt.omst.rasterfall.LoadingPanel
```

This will show a 6-second demo with changing status messages.

## Technical Details

- **Animation Rate**: ~60 FPS (16ms timer)
- **Spinner Rotation**: 4 degrees per frame
- **Glow Pulse**: 0.02 alpha change per frame
- **Default Size**: 600x400 pixels
- **Thread-Safe**: All UI updates use SwingUtilities.invokeLater()

## Resource Requirements

The LoadingPanel requires:
- `images/oceanscan.png` in the classpath (src/main/resources/)
- ImageUtils class from neptus-utils module

## Notes

- The splash screen is undecorated (no title bar or borders)
- Automatically centers on screen
- Always appears on top of other windows
- Animation stops automatically when hidden to free resources
- Safe to call `hideSplashScreen()` with null parameter
