package pt.omst.contacts.browser;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.ImageIcon;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.util.GuiUtils;

@Slf4j
public class IconCache {

    private static volatile IconCache instance = null;

    private final Map<String, Map<Integer, Image>> cache = new LinkedHashMap<>();
    private final Object lock = new Object();
    private int currentIconSize = 24; // Default size

    private IconCache() {
        // Private constructor to prevent instantiation
    }

    public static IconCache getInstance() {
        if (instance == null) {
            synchronized (IconCache.class) {
                if (instance == null) {
                    instance = new IconCache();
                }
            }
        }
        return instance;
    }

    /**
     * Get the current icon size.
     */
    public int getIconSize() {
        return currentIconSize;
    }
    
    /**
     * Set the icon size and clear cache to force reload.
     */
    public void setIconSize(int size) {
        if (size < 8 || size > 64) {
            log.warn("Invalid icon size: {}, must be between 8 and 64", size);
            return;
        }
        synchronized (lock) {
            this.currentIconSize = size;
            log.info("Icon size changed to: {}", size);
        }
    }
    
    /**
     * Get icon with current size.
     */
    public Image getIcon(String icon) {
        return getIcon(icon, currentIconSize);
    }

    /**
     * Get icon with specific size.
     */
    public Image getIcon(String icon, int size) {
        if (icon == null) {
            log.warn("Icon name is null");
            return null;
        }
         
        // Check cache first without locking
        Map<Integer, Image> sizeCache = cache.get(icon);
        if (sizeCache != null) {
            Image cachedImage = sizeCache.get(size);
            if (cachedImage != null) {
                return cachedImage;
            }
        }
        
        // Double-checked locking for cache miss
        synchronized (lock) {
            // Check again in case another thread loaded it
            sizeCache = cache.get(icon);
            if (sizeCache != null) {
                Image cachedImage = sizeCache.get(size);
                if (cachedImage != null) {
                    return cachedImage;
                }
            }
            
            try {
                BufferedImage image = GuiUtils.getImage("/icons/" + icon + ".png");
                if (image == null) {
                    log.warn("Image not found: /icons/{}.png", icon);
                    image = GuiUtils.getImage("/icons/OTHER.png");                    
                }
                ImageIcon scaledIcon = GuiUtils.getScaledIcon(image, size, size);
                if (scaledIcon == null) {
                    log.warn("Failed to scale icon: {}", icon);
                    return null;
                }
                Image scaledImage = scaledIcon.getImage();
                
                // Store in cache
                if (sizeCache == null) {
                    sizeCache = new LinkedHashMap<>();
                    cache.put(icon, sizeCache);
                }
                sizeCache.put(size, scaledImage);
                
                return scaledImage;
            } catch (Exception e) {
                log.error("Error loading icon: {}", icon, e);
                return null;
            }
        }
    }

}
