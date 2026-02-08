package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a sphere shape (3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Sphere extends Shape3D {

    private final int radius;

    /**
     * Creates a filled sphere.
     * 
     * @param radius The radius of the sphere
     */
    public Sphere(int radius) {
        super(false, 1);
        this.radius = radius;
    }

    /**
     * Creates a sphere (filled or hollow).
     * 
     * @param radius The radius of the sphere
     * @param hollow Whether the sphere is hollow
     * @param thickness The thickness for hollow spheres
     */
    public Sphere(int radius, boolean hollow, int thickness) {
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
        // Ensure thickness doesn't exceed radius for hollow sphere
        int effectiveThickness = hollow ? Math.min(thickness, radius) : thickness;
        double innerRadius = Math.max(0, radius - effectiveThickness);
        double innerRadiusSquared = innerRadius * innerRadius;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distSquared = x * x + y * y + z * z;

                    if (hollow) {
                        if (distSquared <= radiusSquared && distSquared > innerRadiusSquared) {
                            // Offset Y by radius so sphere sits on ground
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius, z);
                        }
                    } else {
                        if (distSquared <= radiusSquared) {
                            // Offset Y by radius so sphere sits on ground
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radius, z);
                        }
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Sphere" : "Sphere";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.sphere";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hsph <block> <radius> <thickness>" : "/mct sph <block> <radius>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow sphere shell" : "Creates a filled sphere";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            double outer = (4.0 / 3.0) * Math.PI * radius * radius * radius;
            double inner = (4.0 / 3.0) * Math.PI * (radius - thickness) * (radius - thickness) * (radius - thickness);
            return (int) (outer - inner);
        }
        return (int) ((4.0 / 3.0) * Math.PI * radius * radius * radius);
    }
}
