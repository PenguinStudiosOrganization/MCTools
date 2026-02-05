package com.mctools.brush;

import com.mctools.MCTools;
import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages heightmap files and player brush settings.
 * 
 * @author MCTools Team
 * @version 1.0.0
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

    /**
     * Initializes the heightmaps folder.
     */
    private void initializeHeightmapsFolder() {
        String folderName = plugin.getConfigManager().getHeightmapsFolder();
        heightmapsFolder = new File(plugin.getDataFolder(), folderName);
        
        if (!heightmapsFolder.exists()) {
            heightmapsFolder.mkdirs();
            plugin.getLogger().info("Created heightmaps folder: " + heightmapsFolder.getPath());
        }
    }

    /**
     * Loads all heightmap files from the folder.
     */
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
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    String name = file.getName();
                    heightmapCache.put(name, image);
                    availableHeightmaps.add(name);
                    plugin.getLogger().info("Loaded heightmap: " + name + 
                            " (" + image.getWidth() + "x" + image.getHeight() + ")");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load heightmap: " + file.getName());
            }
        }

        // Sort alphabetically
        Collections.sort(availableHeightmaps);
        plugin.getLogger().info("Loaded " + availableHeightmaps.size() + " heightmaps.");
    }

    /**
     * Gets or creates brush settings for a player.
     */
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

    /**
     * Removes brush settings for a player.
     */
    public void removeSettings(UUID playerUuid) {
        playerSettings.remove(playerUuid);
    }

    /**
     * Gets a heightmap image by name.
     */
    public BufferedImage getHeightmap(String name) {
        return heightmapCache.get(name);
    }

    /**
     * Gets all available heightmap names.
     */
    public List<String> getAvailableHeightmaps() {
        return new ArrayList<>(availableHeightmaps);
    }

    /**
     * Checks if a heightmap exists.
     */
    public boolean hasHeightmap(String name) {
        return heightmapCache.containsKey(name);
    }

    /**
     * Gets the heightmaps folder.
     */
    public File getHeightmapsFolder() {
        return heightmapsFolder;
    }

    /**
     * Reloads all heightmaps.
     */
    public void reload() {
        initializeHeightmapsFolder();
        loadHeightmaps();
    }

    /**
     * Gets the height value from a heightmap at specific coordinates.
     * Returns a value between 0.0 and 1.0.
     */
    public double getHeightAt(BufferedImage heightmap, double normalizedX, double normalizedZ) {
        if (heightmap == null) return 0;
        
        int imgWidth = heightmap.getWidth();
        int imgHeight = heightmap.getHeight();
        
        // Convert normalized coordinates (-1 to 1) to image coordinates
        int imgX = (int) ((normalizedX + 1) / 2 * (imgWidth - 1));
        int imgY = (int) ((normalizedZ + 1) / 2 * (imgHeight - 1));
        
        // Clamp to image bounds
        imgX = Math.max(0, Math.min(imgWidth - 1, imgX));
        imgY = Math.max(0, Math.min(imgHeight - 1, imgY));
        
        // Get pixel value (grayscale)
        int rgb = heightmap.getRGB(imgX, imgY);
        int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
        
        return gray / 255.0;
    }

    public TerrainBrush getTerrainBrush() {
        return terrainBrush;
    }
}
