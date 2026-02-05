package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a 5-pointed star shape (2D, flat on ground).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Star extends Shape2D {

    private final int radius;

    /**
     * Creates a star outline.
     * 
     * @param radius The outer radius of the star
     * @param thickness The thickness of the star lines
     */
    public Star(int radius, int thickness) {
        super(true, thickness); // Stars are always outline-based
        this.radius = radius;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        int points = 5;
        double innerRadius = radius * 0.38; // Inner radius ratio for 5-pointed star
        
        // Calculate star vertices (alternating outer and inner points)
        double[] verticesX = new double[points * 2];
        double[] verticesZ = new double[points * 2];
        
        for (int i = 0; i < points * 2; i++) {
            double angle = i * Math.PI / points - Math.PI / 2;
            double r = (i % 2 == 0) ? radius : innerRadius;
            verticesX[i] = r * Math.cos(angle);
            verticesZ[i] = r * Math.sin(angle);
        }

        // Draw lines between consecutive vertices
        for (int i = 0; i < points * 2; i++) {
            int next = (i + 1) % (points * 2);
            drawLine(blocks, world, centerX, centerY, centerZ,
                    verticesX[i], verticesZ[i],
                    verticesX[next], verticesZ[next]);
        }

        return blocks;
    }

    /**
     * Draws a line between two points with the specified thickness.
     */
    private void drawLine(List<Location> blocks, World world,
                         double centerX, double centerY, double centerZ,
                         double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dz * dz);
        
        if (length == 0) return;

        int steps = (int) Math.ceil(length * 2);
        
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = x1 + dx * t;
            double z = z1 + dz * t;
            
            // Add thickness
            int halfT = thickness / 2;
            for (int tx = -halfT; tx <= halfT; tx++) {
                for (int tz = -halfT; tz <= halfT; tz++) {
                    int blockX = (int) Math.round(x) + tx;
                    int blockZ = (int) Math.round(z) + tz;
                    Location loc = new Location(world, centerX + blockX, centerY, centerZ + blockZ);
                    if (!blocks.contains(loc)) {
                        blocks.add(loc);
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Star";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.star";
    }

    @Override
    public String getUsage() {
        return "/mct star <block> <radius> <thickness>";
    }

    @Override
    public String getDescription() {
        return "Creates a 5-pointed star outline";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Approximate: 10 lines of average length
        double avgLineLength = radius * 0.8;
        return (int) (10 * avgLineLength * thickness);
    }
}
