package com.mctools.utils;

import com.mctools.MCTools;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private int maxThickness;
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

    // ETA bossbar
    private boolean etaBossbarEnabled;
    private int etaBossbarUpdateTicks;
    private boolean etaBossbarShowPercent;

    // Floating blocks cleanup (post-process)
    private boolean floatingCleanupEnabled;
    private int floatingCleanupBlocksPerTick;
    private int floatingCleanupMaxBlocks;
    private int floatingCleanupPadding;
    private Set<Material> floatingCleanupWhitelist;
    private Set<Material> floatingCleanupBlacklist;

    // AI Build settings
    private boolean aiBuildEnabled;
    private String aiBuildApiUrl;
    private int aiBuildTimeout;

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
        maxBlocks = config.getInt("max-blocks", 500000);
        maxRadius = config.getInt("max-radius", 128);
        maxHeight = config.getInt("max-height", 128);
        maxThickness = config.getInt("max-thickness", 32);
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

        // ETA bossbar
        etaBossbarEnabled = config.getBoolean("eta-bossbar.enabled", true);
        etaBossbarUpdateTicks = Math.max(1, config.getInt("eta-bossbar.update-ticks", 10));
        etaBossbarShowPercent = config.getBoolean("eta-bossbar.show-percent", true);

        // Floating cleanup
        floatingCleanupEnabled = config.getBoolean("floating-cleanup.enabled", true);
        floatingCleanupBlocksPerTick = Math.max(10, config.getInt("floating-cleanup.blocks-per-tick", 2000));
        floatingCleanupMaxBlocks = Math.max(0, config.getInt("floating-cleanup.max-blocks-scan", 250000));
        floatingCleanupPadding = Math.max(0, config.getInt("floating-cleanup.padding", 2));

        floatingCleanupWhitelist = parseMaterialList(config.getStringList("floating-cleanup.whitelist"));
        floatingCleanupBlacklist = parseMaterialList(config.getStringList("floating-cleanup.blacklist"));

        // AI Build
        aiBuildEnabled = config.getBoolean("ai-build.enabled", true);
        aiBuildApiUrl = config.getString("ai-build.api-url", "https://mcutils.net/api/tools/shape-ai/structures");
        aiBuildTimeout = config.getInt("ai-build.timeout", 10);

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
    public int getMaxThickness() { return maxThickness; }
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

    // ETA bossbar getters
    public boolean isEtaBossbarEnabled() { return etaBossbarEnabled; }
    public int getEtaBossbarUpdateTicks() { return etaBossbarUpdateTicks; }
    public boolean isEtaBossbarShowPercent() { return etaBossbarShowPercent; }

    // Floating cleanup getters
    public boolean isFloatingCleanupEnabled() { return floatingCleanupEnabled; }
    public int getFloatingCleanupBlocksPerTick() { return floatingCleanupBlocksPerTick; }
    public int getFloatingCleanupMaxBlocks() { return floatingCleanupMaxBlocks; }
    public int getFloatingCleanupPadding() { return floatingCleanupPadding; }
    public Set<Material> getFloatingCleanupWhitelist() { return floatingCleanupWhitelist; }
    public Set<Material> getFloatingCleanupBlacklist() { return floatingCleanupBlacklist; }

    // AI Build getters
    public boolean isAiBuildEnabled() { return aiBuildEnabled; }
    public String getAiBuildApiUrl() { return aiBuildApiUrl; }
    public int getAiBuildTimeout() { return aiBuildTimeout; }

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

    private Set<Material> parseMaterialList(List<String> list) {
        if (list == null || list.isEmpty()) return EnumSet.noneOf(Material.class);
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String raw : list) {
            if (raw == null) continue;
            String name = raw.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            try {
                set.add(Material.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // ignore invalid materials
            }
        }
        return set;
    }
}
