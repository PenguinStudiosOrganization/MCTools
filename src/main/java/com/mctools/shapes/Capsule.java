package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a capsule (pill) shape (3D).
 * A capsule is a cylinder with hemispherical caps on top and bottom.
 */
public class Capsule extends Shape3D {

    private final int radius;
    private final int height;

    /**
     * Creates a filled capsule.
     *
     * @param radius The radius of the capsule
     * @param height The total height (must be >= 2 * radius)
     */
    public Capsule(int radius, int height) {
        super(false, 1);
        this.radius = radius;
        this.height = height;
    }

    /**
     * Creates a capsule (filled or hollow).
     *
     * @param radius    The radius of the capsule
     * @param height    The total height (must be >= 2 * radius)
     * @param hollow    Whether the capsule is hollow
     * @param thickness The thickness for hollow capsules
     */
    public Capsule(int radius, int height, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.radius = radius;
        this.height = height;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        int cylinderHeight = Math.max(0, height - 2 * radius);
        double innerRadius = Math.max(0, radius - thickness);

        // Bottom hemisphere
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y < 0; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (hollow) {
                        if (dist <= radius && dist > innerRadius) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius, z);
                        }
                    } else {
                        if (dist <= radius) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius, z);
                        }
                    }
                }
            }
        }

        // Cylinder middle
        for (int y = 0; y < cylinderHeight; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (hollow) {
                        if (dist <= radius && dist > innerRadius) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius, z);
                        }
                    } else {
                        if (dist <= radius) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius, z);
                        }
                    }
                }
            }
        }

        // Top hemisphere
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (hollow) {
                        if (dist <= radius && dist > innerRadius) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius + cylinderHeight, z);
                        }
                    } else {
                        if (dist <= radius) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius + cylinderHeight, z);
                        }
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Capsule" : "Capsule";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.capsule";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hcapsule <block> <radius> <height> <thickness>"
                     : "/mct capsule <block> <radius> <height>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow capsule shell" : "Creates a filled capsule (pill shape)";
    }

    @Override
    public int getEstimatedBlockCount() {
        int cylinderHeight = Math.max(0, height - 2 * radius);
        if (hollow) {
            double ir = Math.max(0, radius - thickness);
            double outerSphere = (4.0 / 3.0) * Math.PI * radius * radius * radius;
            double innerSphere = (4.0 / 3.0) * Math.PI * ir * ir * ir;
            double outerCyl = Math.PI * radius * radius * cylinderHeight;
            double innerCyl = Math.PI * ir * ir * cylinderHeight;
            return (int) ((outerSphere - innerSphere) + (outerCyl - innerCyl));
        }
        double sphere = (4.0 / 3.0) * Math.PI * radius * radius * radius;
        double cyl = Math.PI * radius * radius * cylinderHeight;
        return (int) (sphere + cyl);
    }
}
