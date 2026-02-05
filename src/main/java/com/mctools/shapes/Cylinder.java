package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a cylinder shape (3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Cylinder extends Shape3D {

    private final int height;
    private final int radius;

    /**
     * Creates a filled cylinder.
     * 
     * @param height The height of the cylinder
     * @param radius The radius of the cylinder
     */
    public Cylinder(int height, int radius) {
        super(false, 1);
        this.height = height;
        this.radius = radius;
    }

    /**
     * Creates a cylinder (filled or hollow).
     * 
     * @param height The height of the cylinder
     * @param radius The radius of the cylinder
     * @param hollow Whether the cylinder is hollow
     * @param thickness The thickness for hollow cylinders
     */
    public Cylinder(int height, int radius, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.height = height;
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

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double distSquared = x * x + z * z;

                    if (hollow) {
                        // Hollow cylinder - only walls, no top/bottom caps
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
        return hollow ? "Hollow Cylinder" : "Cylinder";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.cylinder";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hcyl <block> <height> <radius> <thickness>" 
                     : "/mct cyl <block> <height> <radius>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow cylinder tube" : "Creates a filled cylinder";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            double outer = Math.PI * radius * radius * height;
            double inner = Math.PI * (radius - thickness) * (radius - thickness) * height;
            return (int) (outer - inner);
        }
        return (int) (Math.PI * radius * radius * height);
    }
}
