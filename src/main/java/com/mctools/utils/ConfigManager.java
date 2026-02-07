package com.mctools.utils;

import com.mctools.MCTools;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Centralized access to plugin configuration values.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Load and cache values from {@code config.yml}.</li>
 *   <li>Provide typed getters for all configurable options.</li>
 *   <li>Handle reload requests from admin commands.</li>
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>Values are cached in fields to avoid repeated YAML lookups.</li>
 *   <li>Invalid material names fall back to safe defaults.</li>
 * </ul>
 */
public class ConfigManager {

    private final MCTools plugin;
    private FileConfiguration config;

    // Shape generation settings
    private int maxBlocks;
    private int maxRadius;
    private int maxHeight;
    private boolean asyncPlacement;
    private int blocksPerTick;
    private boolean enableUndo;
    private int maxUndoHistory;
    private int cooldown;
    private boolean showProgress;
    private boolean soundsEnabled;
    private String completeSound;
    private String errorSound;
    private String previewSound;
    private boolean particlesEnabled;
    private String particleType;
    private boolean previewEnabled;
    private int previewDuration;
    private Material previewBlock;
    
    // Brush settings
    private boolean brushEnabled;
    private String brushPermission;
    private int brushMaxSize;
    private int brushMaxHeight;
    private int brushDefaultSize;
    private int brushDefaultIntensity;
    private int brushDefaultMaxHeight;
    private Material brushDefaultBlock;
    private String heightmapsFolder;

    public ConfigManager(MCTools plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadValues();
    }

    private void loadValues() {
        // Shape generation
        maxBlocks = config.getInt("max-blocks", 50000);
        maxRadius = config.getInt("max-radius", 64);
        maxHeight = config.getInt("max-height", 128);
        asyncPlacement = config.getBoolean("async-placement", true);
        blocksPerTick = config.getInt("blocks-per-tick", 1000);
        enableUndo = config.getBoolean("enable-undo", true);
        maxUndoHistory = config.getInt("max-undo-history", 1000);
        cooldown = config.getInt("cooldown", 0);
        showProgress = config.getBoolean("show-progress", true);
        soundsEnabled = config.getBoolean("sounds.enabled", true);
        completeSound = config.getString("sounds.on-complete", "BLOCK_BEACON_ACTIVATE");
        errorSound = config.getString("sounds.on-error", "BLOCK_ANVIL_LAND");
        previewSound = config.getString("sounds.on-preview", "BLOCK_NOTE_BLOCK_CHIME");
        particlesEnabled = config.getBoolean("particles.enabled", true);
        particleType = config.getString("particles.type", "HAPPY_VILLAGER");
        previewEnabled = config.getBoolean("preview.enabled", true);
        previewDuration = config.getInt("preview.duration", 10);
        
        String previewBlockName = config.getString("preview.block", "LIME_STAINED_GLASS");
        try {
            previewBlock = Material.valueOf(previewBlockName);
        } catch (IllegalArgumentException e) {
            previewBlock = Material.LIME_STAINED_GLASS;
        }
        
        // Brush settings
        brushEnabled = config.getBoolean("brush.enabled", true);
        brushPermission = config.getString("brush.permission", "mctools.brush");
        brushMaxSize = config.getInt("brush.max-size", 50);
        brushMaxHeight = config.getInt("brush.max-height", 64);
        brushDefaultSize = config.getInt("brush.default-size", 10);
        brushDefaultIntensity = config.getInt("brush.default-intensity", 50);
        brushDefaultMaxHeight = config.getInt("brush.default-max-height", 20);
        heightmapsFolder = config.getString("brush.heightmaps-folder", "heightmaps");
        
        String defaultBlockName = config.getString("brush.default-block", "GRASS_BLOCK");
        try {
            brushDefaultBlock = Material.valueOf(defaultBlockName);
        } catch (IllegalArgumentException e) {
            brushDefaultBlock = Material.GRASS_BLOCK;
        }
    }

    // Shape generation getters
    public int getMaxBlocks() { return maxBlocks; }
    public int getMaxRadius() { return maxRadius; }
    public int getMaxHeight() { return maxHeight; }
    public boolean isAsyncPlacement() { return asyncPlacement; }
    public int getBlocksPerTick() { return blocksPerTick; }
    public boolean isUndoEnabled() { return enableUndo; }
    public int getMaxUndoHistory() { return maxUndoHistory; }
    public int getCooldown() { return cooldown; }
    public boolean isShowProgress() { return showProgress; }
    public boolean isSoundsEnabled() { return soundsEnabled; }
    public String getCompleteSound() { return completeSound; }
    public String getErrorSound() { return errorSound; }
    public String getPreviewSound() { return previewSound; }
    public boolean isParticlesEnabled() { return particlesEnabled; }
    public String getParticleType() { return particleType; }
    public boolean isPreviewEnabled() { return previewEnabled; }
    public int getPreviewDuration() { return previewDuration; }
    public Material getPreviewBlock() { return previewBlock; }
    
    // Brush getters
    public boolean isBrushEnabled() { return brushEnabled; }
    public String getBrushPermission() { return brushPermission; }
    public int getBrushMaxSize() { return brushMaxSize; }
    public int getBrushMaxHeight() { return brushMaxHeight; }
    public int getBrushDefaultSize() { return brushDefaultSize; }
    public int getBrushDefaultIntensity() { return brushDefaultIntensity; }
    public int getBrushDefaultMaxHeight() { return brushDefaultMaxHeight; }
    public Material getBrushDefaultBlock() { return brushDefaultBlock; }
    public String getHeightmapsFolder() { return heightmapsFolder; }
}
