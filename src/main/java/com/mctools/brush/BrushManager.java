package com.mctools.brush;

import com.mctools.MCTools;
import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Manages heightmap files and per-player brush settings.
 *
 * <p>Loads heightmap images (.png/.jpg) from the plugin's heightmaps folder,
 * caches them in memory, and provides access to per-player {@link BrushSettings}.
 * Also owns the {@link TerrainBrush} instance used for terrain editing.</p>
 */
public class BrushManager {

    private final MCTools plugin;
    private final Map<UUID, BrushSettings> playerSettings;
    private final Map<String, BufferedImage> heightmapCache;
    private final List<String> availableHeightmaps;
    private File heightmapsFolder;

    private final TerrainBrush terrainBrush;

    public BrushManager(MCTools plugin) {
        this.plugin = plugin;
        this.playerSettings = new ConcurrentHashMap<>();
        this.heightmapCache = new ConcurrentHashMap<>();
        this.availableHeightmaps = new ArrayList<>();
        this.terrainBrush = new TerrainBrush(plugin, this);
        
        initializeHeightmapsFolder();
        loadHeightmaps();
    }

    private void initializeHeightmapsFolder() {
        String folderName = plugin.getConfigManager().getHeightmapsFolder();
        heightmapsFolder = new File(plugin.getDataFolder(), folderName);
        
        if (!heightmapsFolder.exists()) {
            heightmapsFolder.mkdirs();
            plugin.getLogger().info("Created heightmaps folder: " + heightmapsFolder.getPath());
        }
    }

    public void loadHeightmaps() {
        availableHeightmaps.clear();
        heightmapCache.clear();
        
        if (!heightmapsFolder.exists() || !heightmapsFolder.isDirectory()) {
            return;
        }

        File[] files = heightmapsFolder.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".png") || 
                name.toLowerCase().endsWith(".jpg") || 
                name.toLowerCase().endsWith(".jpeg"));

        if (files == null) return;

        for (File file : files) {
            try {
                BufferedImage rawImage = ImageIO.read(file);
                if (rawImage != null) {
                    // Convert to TYPE_INT_ARGB to normalise all image formats
                    // (grayscale, 16-bit, premultiplied alpha, indexed, etc.)
                    // so that getRGB() always returns correct, non-premultiplied values.
                    BufferedImage image = normalizeImage(rawImage);
                    String name = file.getName();
                    heightmapCache.put(name, image);
                    availableHeightmaps.add(name);
                    plugin.getLogger().info("Loaded heightmap: " + name + 
                            " (" + image.getWidth() + "x" + image.getHeight() + 
                            ", type " + rawImage.getType() + " → normalized)");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load heightmap: " + file.getName());
            }
        }

        Collections.sort(availableHeightmaps);
        plugin.getLogger().info("Loaded " + availableHeightmaps.size() + " heightmaps.");
    }

    public BrushSettings getSettings(UUID playerUuid) {
        return playerSettings.computeIfAbsent(playerUuid, uuid -> {
            var config = plugin.getConfigManager();
            return new BrushSettings(
                    uuid,
                    config.getBrushDefaultSize(),
                    config.getBrushDefaultIntensity(),
                    config.getBrushDefaultMaxHeight(),
                    config.getBrushDefaultBlock()
            );
        });
    }

    public void removeSettings(UUID playerUuid) {
        playerSettings.remove(playerUuid);
    }

    public BufferedImage getHeightmap(String name) {
        return heightmapCache.get(name);
    }

    public List<String> getAvailableHeightmaps() {
        return new ArrayList<>(availableHeightmaps);
    }

    public boolean hasHeightmap(String name) {
        return heightmapCache.containsKey(name);
    }

    public File getHeightmapsFolder() {
        return heightmapsFolder;
    }

    public void reload() {
        initializeHeightmapsFolder();
        loadHeightmaps();
    }

    /**
     * Normalizes any BufferedImage to TYPE_INT_ARGB so that getRGB()
     * always returns correct, non-premultiplied ARGB values regardless
     * of the source format (grayscale, 16-bit, indexed, premultiplied, etc.).
     */
    private BufferedImage normalizeImage(BufferedImage src) {
        // If already TYPE_INT_ARGB, no conversion needed
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    /** Returns the height value (0.0–1.0) from a heightmap at normalized coordinates. */
    public double getHeightAt(BufferedImage heightmap, double normalizedX, double normalizedZ) {
        if (heightmap == null) return 0;
        
        int imgWidth = heightmap.getWidth();
        int imgHeight = heightmap.getHeight();
        
        int imgX = (int) ((normalizedX + 1) / 2 * (imgWidth - 1));
        int imgY = (int) ((normalizedZ + 1) / 2 * (imgHeight - 1));
        
        imgX = Math.max(0, Math.min(imgWidth - 1, imgX));
        imgY = Math.max(0, Math.min(imgHeight - 1, imgY));
        
        return getPixelBrightness(heightmap, imgX, imgY);
    }

    /**
     * Reads the brightness of a pixel, taking into account the alpha channel.
     * If the RGB channels are all zero but alpha is non-zero, the alpha
     * channel is used as the height value (some heightmaps encode depth in alpha).
     * Otherwise the standard luminance-weighted average of RGB is returned.
     */
    public static double getPixelBrightness(BufferedImage img, int x, int y) {
        int argb = img.getRGB(x, y);
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        // If the pixel is fully transparent, treat as zero height
        if (a == 0) return 0.0;

        // If RGB is all zero but alpha is present, use alpha as height
        if (r == 0 && g == 0 && b == 0 && a > 0) {
            return a / 255.0;
        }

        // Standard luminance-weighted brightness (Rec. 709)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    }

    public TerrainBrush getTerrainBrush() {
        return terrainBrush;
    }
}
