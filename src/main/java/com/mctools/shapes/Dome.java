package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a dome shape (half sphere, 3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Dome extends Shape3D {

    private final int radius;

    /**
     * Creates a filled dome.
     * 
     * @param radius The radius of the dome
     */
    public Dome(int radius) {
        super(false, 1);
        this.radius = radius;
    }

    /**
     * Creates a dome (filled or hollow).
     * 
     * @param radius The radius of the dome
     * @param hollow Whether the dome is hollow
     * @param thickness The thickness for hollow domes
     */
    public Dome(int radius, boolean hollow, int thickness) {
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
            for (int y = 0; y <= radius; y++) { // Only positive Y (upper half)
                for (int z = -radius; z <= radius; z++) {
                    double distSquared = x * x + y * y + z * z;

                    if (hollow) {
                        if (distSquared <= radiusSquared && distSquared > innerRadiusSquared) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                        }
                    } else {
                        if (distSquared <= radiusSquared) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                        }
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Dome" : "Dome";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.dome";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hdome <block> <radius> <thickness>" : "/mct dome <block> <radius>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow dome shell" : "Creates a filled dome (half sphere)";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            double outer = (2.0 / 3.0) * Math.PI * radius * radius * radius;
            double inner = (2.0 / 3.0) * Math.PI * (radius - thickness) * (radius - thickness) * (radius - thickness);
            return (int) (outer - inner);
        }
        return (int) ((2.0 / 3.0) * Math.PI * radius * radius * radius);
    }
}
