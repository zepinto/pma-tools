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

    private final Map<String, Image> cache = new LinkedHashMap<>();
    private final Object lock = new Object();

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

    public Image getIcon(String icon) {
        if (icon == null) {
            log.warn("Icon name is null");
            return null;
        }
         
        // Check cache first without locking
        Image cachedImage = cache.get(icon);
        if (cachedImage != null) {
            return cachedImage;
        }
        
        // Double-checked locking for cache miss
        synchronized (lock) {
            // Check again in case another thread loaded it
            cachedImage = cache.get(icon);
            if (cachedImage != null) {
                return cachedImage;
            }
            
            try {
                BufferedImage image = GuiUtils.getImage("/icons/" + icon + ".png");
                if (image == null) {
                    log.warn("Image not found: /icons/{}.png", icon);
                    return null;
                }
                ImageIcon scaledIcon = GuiUtils.getScaledIcon(image, 12, 12);
                if (scaledIcon == null) {
                    log.warn("Failed to scale icon: {}", icon);
                    return null;
                }
                Image scaledImage = scaledIcon.getImage();
                cache.put(icon, scaledImage);
                return scaledImage;
            } catch (Exception e) {
                log.error("Error loading icon: {}", icon, e);
                return null;
            }
        }
    }

}
