package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a circle shape (2D, flat on ground).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Circle extends Shape2D {

    private final int radius;

    /**
     * Creates a filled circle.
     * 
     * @param radius The radius of the circle
     */
    public Circle(int radius) {
        super(false, 1);
        this.radius = radius;
    }

    /**
     * Creates a circle (filled or hollow).
     * 
     * @param radius The radius of the circle
     * @param hollow Whether the circle is hollow
     * @param thickness The thickness for hollow circles
     */
    public Circle(int radius, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.radius = radius;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        double radiusSquared = radius * radius;
        double innerRadiusSquared = hollow ? (radius - thickness) * (radius - thickness) : 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distSquared = x * x + z * z;
                
                if (hollow) {
                    if (distSquared <= radiusSquared && distSquared > innerRadiusSquared) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                    }
                } else {
                    if (distSquared <= radiusSquared) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Circle" : "Circle";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.circle";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hcir <block> <radius> <thickness>" : "/mct cir <block> <radius>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow circle outline" : "Creates a filled circle";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            return (int) (Math.PI * radius * radius - Math.PI * (radius - thickness) * (radius - thickness));
        }
        return (int) (Math.PI * radius * radius);
    }
}
