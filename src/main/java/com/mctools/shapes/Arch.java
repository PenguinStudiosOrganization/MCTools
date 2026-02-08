package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates an arch shape (3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Arch extends Shape3D {

    private final int legHeight;
    private final int archRadius;
    private final int width;

    /**
     * Creates a filled arch.
     * 
     * @param legHeight The height of the vertical legs
     * @param archRadius The radius of the arch curve
     * @param width The width (depth) of the arch
     */
    public Arch(int legHeight, int archRadius, int width) {
        super(false, 1);
        this.legHeight = legHeight;
        this.archRadius = archRadius;
        this.width = width;
    }

    /**
     * Creates an arch (filled or hollow).
     * 
     * @param legHeight The height of the vertical legs
     * @param archRadius The radius of the arch curve
     * @param width The width (depth) of the arch
     * @param hollow Whether the arch is hollow
     * @param thickness The thickness for hollow arches
     */
    public Arch(int legHeight, int archRadius, int width, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.legHeight = legHeight;
        this.archRadius = archRadius;
        this.width = width;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        int halfWidth = width / 2;
        int legThickness = hollow ? thickness : archRadius; // filled legs span full archRadius width

        // Generate left leg - ALWAYS solid (filled) regardless of hollow setting
        for (int y = 0; y < legHeight; y++) {
            for (int x = -archRadius; x <= -archRadius + legThickness - 1; x++) {
                for (int z = -halfWidth; z <= halfWidth; z++) {
                    addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                }
            }
        }

        // Generate right leg - ALWAYS solid (filled) regardless of hollow setting
        for (int y = 0; y < legHeight; y++) {
            for (int x = archRadius - legThickness + 1; x <= archRadius; x++) {
                for (int z = -halfWidth; z <= halfWidth; z++) {
                    addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                }
            }
        }

        // Generate arch (semicircle) on top of the legs
        double radiusSquared = archRadius * archRadius;
        double innerRadiusSquared = hollow ? (archRadius - thickness) * (archRadius - thickness) : 0;

        for (int x = -archRadius; x <= archRadius; x++) {
            for (int y = 0; y <= archRadius; y++) {
                double distSquared = x * x + y * y;

                boolean inArch;
                if (hollow) {
                    inArch = distSquared <= radiusSquared && distSquared > innerRadiusSquared;
                } else {
                    inArch = distSquared <= radiusSquared;
                }

                if (inArch) {
                    for (int z = -halfWidth; z <= halfWidth; z++) {
                        if (hollow) {
                            boolean onZEdge = Math.abs(z) > halfWidth - thickness;
                            boolean onArchSurface = distSquared <= radiusSquared && distSquared > innerRadiusSquared;
                            if (onZEdge || onArchSurface) {
                                addBlock(blocks, world, centerX, centerY + legHeight, centerZ, x, y, z);
                            }
                        } else {
                            addBlock(blocks, world, centerX, centerY + legHeight, centerZ, x, y, z);
                        }
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Arch" : "Arch";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.arch";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct harch <block> <legHeight> <radius> <width> <thickness>" 
                     : "/mct arch <block> <legHeight> <radius> <width>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow arch structure" : "Creates a filled arch with legs and curved top";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Legs + semicircle
        int legBlocks = 2 * legHeight * width;
        int archBlocks = (int) (0.5 * Math.PI * archRadius * archRadius * width);
        
        if (hollow) {
            int innerLegBlocks = 2 * legHeight * Math.max(0, width - 2 * thickness);
            int innerArchBlocks = (int) (0.5 * Math.PI * (archRadius - thickness) * (archRadius - thickness) * Math.max(0, width - 2 * thickness));
            return (legBlocks - innerLegBlocks) + (archBlocks - innerArchBlocks);
        }
        
        return legBlocks + archBlocks;
    }
}
