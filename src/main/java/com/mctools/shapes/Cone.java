package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a cone shape (3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Cone extends Shape3D {

    private final int height;
    private final int radius;

    /**
     * Creates a filled cone.
     * 
     * @param height The height of the cone
     * @param radius The base radius of the cone
     */
    public Cone(int height, int radius) {
        super(false, 1);
        this.height = height;
        this.radius = radius;
    }

    /**
     * Creates a cone (filled or hollow).
     * 
     * @param height The height of the cone
     * @param radius The base radius of the cone
     * @param hollow Whether the cone is hollow
     * @param thickness The thickness for hollow cones
     */
    public Cone(int height, int radius, boolean hollow, int thickness) {
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

        for (int y = 0; y < height; y++) {
            // Radius decreases linearly from base to tip
            double currentRadius = radius * (1.0 - (double) y / height);
            double innerRadius = hollow ? Math.max(0, currentRadius - thickness) : 0;

            double currentRadiusSquared = currentRadius * currentRadius;
            double innerRadiusSquared = innerRadius * innerRadius;

            int maxR = (int) Math.ceil(currentRadius);

            for (int x = -maxR; x <= maxR; x++) {
                for (int z = -maxR; z <= maxR; z++) {
                    double distSquared = x * x + z * z;

                    if (hollow) {
                        if (distSquared <= currentRadiusSquared && distSquared > innerRadiusSquared) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                        }
                    } else {
                        if (distSquared <= currentRadiusSquared) {
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
        return hollow ? "Hollow Cone" : "Cone";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.cone";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hcone <block> <height> <radius> <thickness>" 
                     : "/mct cone <block> <height> <radius>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow cone shell" : "Creates a filled cone";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            double outer = (1.0 / 3.0) * Math.PI * radius * radius * height;
            double innerR = Math.max(0, radius - thickness);
            double inner = (1.0 / 3.0) * Math.PI * innerR * innerR * height;
            return (int) (outer - inner);
        }
        return (int) ((1.0 / 3.0) * Math.PI * radius * radius * height);
    }
}
