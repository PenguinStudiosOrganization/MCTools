package com.mctools.brush;

import org.bukkit.Material;

import java.awt.image.BufferedImage;
import java.util.UUID;

/**
 * Stores brush settings for a player.
 * Terrain brush settings only.
 * 
 * @author MCTools Team
 * @version 3.0.0
 */
public class BrushSettings {

    private final UUID playerUuid;
    
    // Basic brush settings
    private boolean enabled;
    private int size;
    private int intensity;
    private int maxHeight;
    private Material block;
    private String heightmapName;
    private BufferedImage heightmapImage;
    private BrushMode mode;
    private boolean autoSmooth;
    private int smoothStrength;
    private boolean autoRotation;
    private boolean circularMask;
    private boolean previewEnabled;

    public enum BrushMode {
        RAISE,      // Raise terrain
        LOWER,      // Lower terrain
        SMOOTH,     // Smooth terrain
        FLATTEN     // Flatten to a specific height
    }

    public BrushSettings(UUID playerUuid, int defaultSize, int defaultIntensity, 
                        int defaultMaxHeight, Material defaultBlock) {
        this.playerUuid = playerUuid;
        this.enabled = false;
        this.size = defaultSize;
        this.intensity = defaultIntensity;
        this.maxHeight = defaultMaxHeight;
        this.block = defaultBlock;
        this.heightmapName = null;
        this.heightmapImage = null;
        this.mode = BrushMode.RAISE;
        this.autoSmooth = true;
        this.smoothStrength = 2;
        this.autoRotation = true;
        this.circularMask = true;
        this.previewEnabled = true;
    }

    // ═══════════════════════════════════════════════════════════════
    // Basic Getters and Setters
    // ═══════════════════════════════════════════════════════════════
    
    public UUID getPlayerUuid() { return playerUuid; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggle() { this.enabled = !this.enabled; }
    
    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.max(1, size); }
    
    public int getIntensity() { return intensity; }
    public void setIntensity(int intensity) { this.intensity = Math.max(1, Math.min(100, intensity)); }
    
    public int getMaxHeight() { return maxHeight; }
    public void setMaxHeight(int maxHeight) { this.maxHeight = Math.max(1, maxHeight); }
    
    public Material getBlock() { return block; }
    public void setBlock(Material block) { this.block = block; }
    
    public String getHeightmapName() { return heightmapName; }
    public void setHeightmapName(String heightmapName) { this.heightmapName = heightmapName; }
    
    public BufferedImage getHeightmapImage() { return heightmapImage; }
    public void setHeightmapImage(BufferedImage heightmapImage) { this.heightmapImage = heightmapImage; }
    
    public BrushMode getMode() { return mode; }
    public void setMode(BrushMode mode) { this.mode = mode; }
    
    public boolean isAutoSmooth() { return autoSmooth; }
    public void setAutoSmooth(boolean autoSmooth) { this.autoSmooth = autoSmooth; }
    public void toggleAutoSmooth() { this.autoSmooth = !this.autoSmooth; }
    
    public int getSmoothStrength() { return smoothStrength; }
    public void setSmoothStrength(int smoothStrength) { 
        this.smoothStrength = Math.max(1, Math.min(5, smoothStrength)); 
    }
    
    public boolean hasHeightmap() {
        return heightmapImage != null && heightmapName != null;
    }
    
    public boolean isAutoRotation() { return autoRotation; }
    public void setAutoRotation(boolean autoRotation) { this.autoRotation = autoRotation; }
    public void toggleAutoRotation() { this.autoRotation = !this.autoRotation; }
    
    public boolean isCircularMask() { return circularMask; }
    public void setCircularMask(boolean circularMask) { this.circularMask = circularMask; }
    public void toggleCircularMask() { this.circularMask = !this.circularMask; }
    
    public boolean isPreviewEnabled() { return previewEnabled; }
    public void setPreviewEnabled(boolean previewEnabled) { this.previewEnabled = previewEnabled; }
    public void togglePreview() { this.previewEnabled = !this.previewEnabled; }
}
