package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a square shape (2D, flat on ground).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Square extends Shape2D {

    private final int size;

    /**
     * Creates a filled square.
     * 
     * @param size The size (side length) of the square
     */
    public Square(int size) {
        super(false, 1);
        this.size = size;
    }

    /**
     * Creates a square (filled or hollow).
     * 
     * @param size The size (side length) of the square
     * @param hollow Whether the square is hollow
     * @param thickness The thickness for hollow squares
     */
    public Square(int size, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.size = size;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        int halfSize = size / 2;

        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                if (hollow) {
                    // Check if on edge
                    boolean isEdge = Math.abs(x) > halfSize - thickness || 
                                    Math.abs(z) > halfSize - thickness;
                    if (isEdge) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                    }
                } else {
                    addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Square" : "Square";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.square";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hsq <block> <size> <thickness>" : "/mct sq <block> <size>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow square outline" : "Creates a filled square";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            int inner = Math.max(0, size - 2 * thickness);
            return size * size - inner * inner;
        }
        return size * size;
    }
}
