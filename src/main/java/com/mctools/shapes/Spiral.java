package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a flat spiral shape (2D, flat on ground).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Spiral extends Shape2D {

    private final int radius;
    private final int turns;

    /**
     * Creates a spiral.
     * 
     * @param radius The outer radius of the spiral
     * @param turns The number of turns
     * @param thickness The thickness of the spiral line
     */
    public Spiral(int radius, int turns, int thickness) {
        super(false, thickness);
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

        // Total angle for all turns
        double totalAngle = turns * 2 * Math.PI;
        int steps = (int) (radius * turns * 10); // More steps for smoother spiral

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double angle = t * totalAngle;
            double currentRadius = t * radius;

            int x = (int) Math.round(currentRadius * Math.cos(angle));
            int z = (int) Math.round(currentRadius * Math.sin(angle));

            // Add thickness
            for (int tx = -halfT; tx <= halfT; tx++) {
                for (int tz = -halfT; tz <= halfT; tz++) {
                    int blockX = x + tx;
                    int blockZ = z + tz;
                    
                    // Use position hash to avoid duplicates
                    long posHash = ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
                    if (addedPositions.add(posHash)) {
                        addBlock(blocks, world, centerX, centerY, centerZ, blockX, blockZ);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return "Spiral";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.spiral";
    }

    @Override
    public String getUsage() {
        return "/mct spi <block> <radius> <turns> <thickness>";
    }

    @Override
    public String getDescription() {
        return "Creates a flat spiral pattern";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Approximate arc length of spiral
        double arcLength = 0;
        for (int i = 1; i <= turns; i++) {
            arcLength += 2 * Math.PI * (radius * i / turns);
        }
        return (int) (arcLength * thickness);
    }
}
