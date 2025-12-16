//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.mapview;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link MapPainter} wrapper that caches the delegate rendering into fixed-size tiles
 * per {@link SlippyMap#getLevelOfDetail()} and persists them to disk.
 *
 * <p>
 * This is designed for painters that render purely based on the {@link SlippyMap}
 * state and the provided {@link Graphics2D}, typically using
 * {@link SlippyMap#latLonToScreen(double, double)}.
 */
@Slf4j
public class CachedMapPainter implements MapPainter {

    public static final int DEFAULT_TILE_SIZE_PX = 256;
    public static final int DEFAULT_MEMORY_TILE_LIMIT = 256;

    private final MapPainter delegate;
    private final Path cacheRoot;
    private final String variant;
    private final int tileSizePx;

    private final Map<String, BufferedImage> memoryLru;

    public CachedMapPainter(MapPainter delegate, Path cacheRoot) {
        this(delegate, cacheRoot, "default", DEFAULT_TILE_SIZE_PX, DEFAULT_MEMORY_TILE_LIMIT);
    }

    public CachedMapPainter(MapPainter delegate, Path cacheRoot, String variant) {
        this(delegate, cacheRoot, variant, DEFAULT_TILE_SIZE_PX, DEFAULT_MEMORY_TILE_LIMIT);
    }

    public CachedMapPainter(MapPainter delegate, Path cacheRoot, String variant, int tileSizePx, int memoryTileLimit) {
        if (delegate == null)
            throw new IllegalArgumentException("delegate must not be null");
        if (cacheRoot == null)
            throw new IllegalArgumentException("cacheRoot must not be null");
        if (variant == null || variant.isBlank())
            variant = "default";
        if (tileSizePx <= 0)
            throw new IllegalArgumentException("tileSizePx must be > 0");

        this.delegate = delegate;
        this.cacheRoot = cacheRoot;
        this.variant = variant;
        this.tileSizePx = tileSizePx;

        int limit = Math.max(0, memoryTileLimit);
        this.memoryLru = new LinkedHashMap<>(limit + 1, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                boolean remove = size() > limit;
                if (remove && eldest.getValue() != null)
                    eldest.getValue().flush();
                return remove;
            }
        };

        try {
            Files.createDirectories(getPainterCacheRoot());
        } catch (IOException e) {
            log.warn("Could not create cache directory {}: {}", cacheRoot, e.getMessage());
        }
    }

    public static Path defaultCacheRoot() {
        // Keep it independent from SlippyMap base tile cache.
        return Path.of(System.getProperty("user.home"), ".cache", "pma-tools", "map-painter-cache");
    }

    @Override
    public int getLayerPriority() {
        return delegate.getLayerPriority();
    }

    @Override
    public java.awt.geom.Rectangle2D.Double getBounds() {
        return delegate.getBounds();
    }

    @Override
    public String getName() {
        return "Cached(" + delegate.getName() + ")";
    }

    @Override
    public void paint(Graphics2D g, SlippyMap map) {
        if (g == null || map == null)
            return;

        int z = map.getLevelOfDetail();
        double[] bounds = map.getVisibleBounds();
        if (bounds == null || bounds.length < 4)
            return;

        double minLat = bounds[0];
        double minLon = bounds[1];
        double maxLat = bounds[2];
        double maxLon = bounds[3];

        // Visible bounds in global map pixel space for current zoom (WebMercator pixel coordinates).
        // top-left uses maxLat/minLon; bottom-right uses minLat/maxLon.
        double[] topLeftPx = map.latLonToPixel(maxLat, minLon);
        double[] bottomRightPx = map.latLonToPixel(minLat, maxLon);

        int startTileX = (int) Math.floor(topLeftPx[0] / tileSizePx);
        int endTileX = (int) Math.floor(bottomRightPx[0] / tileSizePx);
        int startTileY = (int) Math.floor(topLeftPx[1] / tileSizePx);
        int endTileY = (int) Math.floor(bottomRightPx[1] / tileSizePx);

        // Safety clamp to avoid pathological loops if something goes wrong.
        int maxTiles = 2000;
        int tilesDrawn = 0;

        for (int tx = startTileX; tx <= endTileX; tx++) {
            for (int ty = startTileY; ty <= endTileY; ty++) {
                if (++tilesDrawn > maxTiles) {
                    log.warn("Aborting CachedMapPainter paint: too many tiles ({})", tilesDrawn);
                    return;
                }

                drawTile(g, map, z, tx, ty);
            }
        }
    }

    private void drawTile(Graphics2D g, SlippyMap map, int z, int tileX, int tileY) {
        long mapX = (long) tileX * tileSizePx;
        long mapY = (long) tileY * tileSizePx;

        // Determine where this tile lands on screen.
        double[] latLon = map.pixelToLatLon(mapX, mapY);
        double[] screen = map.latLonToScreen(latLon[0], latLon[1]);
        int screenX = (int) Math.round(screen[0]);
        int screenY = (int) Math.round(screen[1]);

        String cacheKey = cacheKey(z, tileX, tileY);
        BufferedImage img = getFromMemory(cacheKey);
        if (img == null) {
            Path tilePath = tilePath(z, tileX, tileY);
            img = readTile(tilePath);
            if (img == null) {
                img = renderTile(map, screenX, screenY);
                if (img != null)
                    writeTile(tilePath, img);
            }
            if (img != null)
                putInMemory(cacheKey, img);
        }

        if (img != null) {
            g.drawImage(img, screenX, screenY, null);
        }
    }

    private BufferedImage renderTile(SlippyMap map, int tileScreenX, int tileScreenY) {
        BufferedImage tile = new BufferedImage(tileSizePx, tileSizePx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tg = tile.createGraphics();
        try {
            tg.setClip(0, 0, tileSizePx, tileSizePx);
            // Translate so that drawing at (tileScreenX, tileScreenY) lands at (0,0) in the tile image.
            tg.translate(-tileScreenX, -tileScreenY);
            delegate.paint(tg, map);
            return tile;
        } catch (RuntimeException e) {
            log.warn("Error rendering cached tile: {}", e.getMessage());
            tile.flush();
            return null;
        } finally {
            tg.dispose();
        }
    }

    private BufferedImage readTile(Path path) {
        if (path == null)
            return null;
        if (!Files.exists(path))
            return null;

        try {
            return ImageIO.read(path.toFile());
        } catch (IOException e) {
            log.debug("Failed to read cached tile {}: {}", path, e.getMessage());
            return null;
        }
    }

    private void writeTile(Path path, BufferedImage img) {
        if (path == null || img == null)
            return;

        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            ImageIO.write(img, "png", tmp.toFile());
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // Fallback for file systems that don't support atomic move.
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.debug("Failed to write cached tile {}: {}", path, e.getMessage());
        }
    }

    private Path tilePath(int z, int tileX, int tileY) {
        // Layout: <cacheRoot>/<painterHash>/<variant>/z<z>/x<tileX>/y<tileY>.png
        return getPainterCacheRoot()
                .resolve(safeSegment(variant))
                .resolve("z" + z)
                .resolve("x" + tileX)
                .resolve("y" + tileY + ".png");
    }

    private Path getPainterCacheRoot() {
        return cacheRoot.resolve(hashId(delegate.getClass().getName() + ":" + delegate.getName()));
    }

    private String cacheKey(int z, int x, int y) {
        return z + "/" + x + "/" + y;
    }

    private BufferedImage getFromMemory(String key) {
        synchronized (memoryLru) {
            return memoryLru.get(key);
        }
    }

    private void putInMemory(String key, BufferedImage img) {
        synchronized (memoryLru) {
            memoryLru.put(key, img);
        }
    }

    private static String safeSegment(String s) {
        // Keep directory names stable and filesystem-friendly.
        return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private static String hashId(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] out = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 is always present in the JRE, but keep a safe fallback.
            return Integer.toHexString(input.hashCode());
        }
    }
}
