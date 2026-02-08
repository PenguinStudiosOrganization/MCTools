package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a pyramid shape with square base (3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Pyramid extends Shape3D {

    private final int height;
    private final int baseSize;

    /**
     * Creates a filled pyramid.
     * 
     * @param height The height of the pyramid
     * @param baseSize The base size (half-width) of the pyramid
     */
    public Pyramid(int height, int baseSize) {
        super(false, 1);
        this.height = height;
        this.baseSize = baseSize;
    }

    /**
     * Creates a pyramid (filled or hollow).
     * 
     * @param height The height of the pyramid
     * @param baseSize The base size (half-width) of the pyramid
     * @param hollow Whether the pyramid is hollow
     * @param thickness The thickness for hollow pyramids
     */
    public Pyramid(int height, int baseSize, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.height = height;
        this.baseSize = baseSize;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        for (int y = 0; y < height; y++) {
            // Size decreases linearly from base to tip
            double ratio = 1.0 - (double) y / height;
            int currentSize = (int) Math.round(baseSize * ratio);
            int innerSize = hollow ? Math.max(0, currentSize - thickness) : 0;

            for (int x = -currentSize; x <= currentSize; x++) {
                for (int z = -currentSize; z <= currentSize; z++) {
                    if (hollow) {
                        // Base layer (y=0) is always filled for hollow pyramids
                        boolean isBase = (y == 0);
                        // Check if on the edge (walls)
                        boolean isEdge = Math.abs(x) > innerSize || Math.abs(z) > innerSize;
                        if (isBase || isEdge) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                        }
                    } else {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Pyramid" : "Pyramid";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.pyramid";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hpyr <block> <height> <baseSize> <thickness>" 
                     : "/mct pyr <block> <height> <baseSize>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow pyramid shell" : "Creates a filled pyramid with square base";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Sum of squares from base to tip
        int total = 0;
        for (int y = 0; y < height; y++) {
            double ratio = 1.0 - (double) y / height;
            int currentSize = (int) Math.round(baseSize * ratio);
            int layerSize = (2 * currentSize + 1) * (2 * currentSize + 1);
            
            if (hollow) {
                int innerSize = Math.max(0, currentSize - thickness);
                int innerLayerSize = (2 * innerSize + 1) * (2 * innerSize + 1);
                total += layerSize - innerLayerSize;
            } else {
                total += layerSize;
            }
        }
        return total;
    }
}
