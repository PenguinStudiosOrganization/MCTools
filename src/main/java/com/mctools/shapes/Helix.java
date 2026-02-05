package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a 3D helix (spiral staircase) shape.
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Helix extends Shape3D {

    private final int height;
    private final int radius;
    private final int turns;

    /**
     * Creates a helix.
     * 
     * @param height The total height of the helix
     * @param radius The radius of the helix
     * @param turns The number of complete turns
     * @param thickness The thickness of the helix line
     */
    public Helix(int height, int radius, int turns, int thickness) {
        super(false, thickness);
        this.height = height;
        this.radius = radius;
        this.turns = Math.max(1, turns);
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        Set<Long> addedPositions = new HashSet<>();
        int halfT = thickness / 2;

        // Calculate points along the helix
        int steps = height * 10; // More steps for smoother helix

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int y = (int) Math.round(t * height);
            double angle = t * turns * 2 * Math.PI;
            
            int x = (int) Math.round(radius * Math.cos(angle));
            int z = (int) Math.round(radius * Math.sin(angle));

            // Add thickness
            for (int tx = -halfT; tx <= halfT; tx++) {
                for (int tz = -halfT; tz <= halfT; tz++) {
                    int blockX = x + tx;
                    int blockZ = z + tz;
                    
                    // Use position hash to avoid duplicates
                    long posHash = ((long) blockX << 40) | ((long) y << 20) | (blockZ & 0xFFFFF);
                    if (addedPositions.add(posHash)) {
                        addBlock(blocks, world, centerX, centerY, centerZ, blockX, y, blockZ);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return "Helix";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.helix";
    }

    @Override
    public String getUsage() {
        return "/mct hel <block> <height> <radius> <turns> <thickness>";
    }

    @Override
    public String getDescription() {
        return "Creates a 3D helix (spiral) shape";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Approximate arc length of helix
        double circumference = 2 * Math.PI * radius;
        double arcLength = Math.sqrt(height * height + (circumference * turns) * (circumference * turns));
        return (int) (arcLength * thickness * thickness);
    }
}
