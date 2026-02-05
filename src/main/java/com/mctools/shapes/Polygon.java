package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a regular polygon shape (2D, flat on ground).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Polygon extends Shape2D {

    private final int radius;
    private final int sides;

    /**
     * Creates a filled polygon.
     * 
     * @param radius The radius of the polygon
     * @param sides The number of sides (3-12)
     */
    public Polygon(int radius, int sides) {
        super(false, 1);
        this.radius = radius;
        this.sides = Math.max(3, Math.min(12, sides));
    }

    /**
     * Creates a polygon (filled or hollow).
     * 
     * @param radius The radius of the polygon
     * @param sides The number of sides (3-12)
     * @param hollow Whether the polygon is hollow
     * @param thickness The thickness for hollow polygons
     */
    public Polygon(int radius, int sides, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.radius = radius;
        this.sides = Math.max(3, Math.min(12, sides));
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        // Pre-calculate polygon vertices
        double[] verticesX = new double[sides];
        double[] verticesZ = new double[sides];
        double angleStep = 2 * Math.PI / sides;

        for (int i = 0; i < sides; i++) {
            double angle = i * angleStep - Math.PI / 2; // Start from top
            verticesX[i] = radius * Math.cos(angle);
            verticesZ[i] = radius * Math.sin(angle);
        }

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean inside = isInsidePolygon(x, z, verticesX, verticesZ);
                
                if (hollow) {
                    // Calculate inner polygon
                    double innerRadius = radius - thickness;
                    double[] innerVerticesX = new double[sides];
                    double[] innerVerticesZ = new double[sides];
                    for (int i = 0; i < sides; i++) {
                        double angle = i * angleStep - Math.PI / 2;
                        innerVerticesX[i] = innerRadius * Math.cos(angle);
                        innerVerticesZ[i] = innerRadius * Math.sin(angle);
                    }
                    boolean insideInner = innerRadius > 0 && isInsidePolygon(x, z, innerVerticesX, innerVerticesZ);
                    
                    if (inside && !insideInner) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                    }
                } else {
                    if (inside) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Checks if a point is inside the polygon using ray casting algorithm.
     */
    private boolean isInsidePolygon(double x, double z, double[] verticesX, double[] verticesZ) {
        boolean inside = false;
        int j = sides - 1;

        for (int i = 0; i < sides; i++) {
            if ((verticesZ[i] > z) != (verticesZ[j] > z) &&
                x < (verticesX[j] - verticesX[i]) * (z - verticesZ[i]) / (verticesZ[j] - verticesZ[i]) + verticesX[i]) {
                inside = !inside;
            }
            j = i;
        }

        return inside;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Polygon" : "Polygon";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.polygon";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hpoly <block> <radius> <sides> <thickness>" 
                     : "/mct poly <block> <radius> <sides>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow polygon outline" : "Creates a filled polygon";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Approximate area of regular polygon
        double area = 0.5 * sides * radius * radius * Math.sin(2 * Math.PI / sides);
        if (hollow) {
            double innerRadius = radius - thickness;
            double innerArea = 0.5 * sides * innerRadius * innerRadius * Math.sin(2 * Math.PI / sides);
            return (int) (area - innerArea);
        }
        return (int) area;
    }
}
