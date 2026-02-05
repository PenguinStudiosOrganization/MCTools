package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a torus (donut) shape (3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Torus extends Shape3D {

    private final int majorRadius;
    private final int minorRadius;

    /**
     * Creates a filled torus.
     * 
     * @param majorRadius The major radius (distance from center to tube center)
     * @param minorRadius The minor radius (tube radius)
     */
    public Torus(int majorRadius, int minorRadius) {
        super(false, 1);
        this.majorRadius = majorRadius;
        this.minorRadius = minorRadius;
    }

    /**
     * Creates a torus (filled or hollow).
     * 
     * @param majorRadius The major radius (distance from center to tube center)
     * @param minorRadius The minor radius (tube radius)
     * @param hollow Whether the torus is hollow
     * @param thickness The thickness for hollow tori
     */
    public Torus(int majorRadius, int minorRadius, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.majorRadius = majorRadius;
        this.minorRadius = minorRadius;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        int totalRadius = majorRadius + minorRadius;

        for (int x = -totalRadius; x <= totalRadius; x++) {
            for (int y = -minorRadius; y <= minorRadius; y++) {
                for (int z = -totalRadius; z <= totalRadius; z++) {
                    // Distance from Y-axis in XZ plane
                    double distXZ = Math.sqrt(x * x + z * z);
                    
                    // Distance from the ring (tube center)
                    double distToRing = Math.sqrt(Math.pow(distXZ - majorRadius, 2) + y * y);

                    if (hollow) {
                        if (distToRing <= minorRadius && distToRing > minorRadius - thickness) {
                            // Offset Y so torus sits on ground
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + minorRadius, z);
                        }
                    } else {
                        if (distToRing <= minorRadius) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + minorRadius, z);
                        }
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Torus" : "Torus";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.torus";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct htor <block> <majorRadius> <minorRadius> <thickness>" 
                     : "/mct tor <block> <majorRadius> <minorRadius>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow torus (donut) shell" : "Creates a filled torus (donut shape)";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Volume of torus = 2 * pi^2 * R * r^2
        if (hollow) {
            double outer = 2 * Math.PI * Math.PI * majorRadius * minorRadius * minorRadius;
            double inner = 2 * Math.PI * Math.PI * majorRadius * (minorRadius - thickness) * (minorRadius - thickness);
            return (int) (outer - inner);
        }
        return (int) (2 * Math.PI * Math.PI * majorRadius * minorRadius * minorRadius);
    }
}
