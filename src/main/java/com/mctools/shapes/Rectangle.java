package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a rectangle shape (2D, flat on ground) with optional rounded corners.
 * 
 * <p>The radiusX and radiusZ parameters are HALF-DIMENSIONS (semi-axes).
 * For example, radiusX=51 means the rectangle extends from -51 to +51 on X axis (103 blocks total).
 * This matches the website's coordinate system.</p>
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Rectangle extends Shape2D {

    private final int radiusX;
    private final int radiusZ;
    private final int cornerRadius;

    /**
     * Creates a filled rectangle.
     * 
     * @param radiusX The half-width of the rectangle (X axis semi-dimension)
     * @param radiusZ The half-depth of the rectangle (Z axis semi-dimension)
     * @param cornerRadius The corner radius (0 for sharp corners)
     */
    public Rectangle(int radiusX, int radiusZ, int cornerRadius) {
        super(false, 1);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.cornerRadius = Math.max(0, Math.min(cornerRadius, Math.min(radiusX, radiusZ)));
    }

    /**
     * Creates a rectangle (filled or hollow).
     * 
     * @param radiusX The half-width of the rectangle (X axis semi-dimension)
     * @param radiusZ The half-depth of the rectangle (Z axis semi-dimension)
     * @param cornerRadius The corner radius (0 for sharp corners)
     * @param hollow Whether the rectangle is hollow
     * @param thickness The thickness for hollow rectangles
     */
    public Rectangle(int radiusX, int radiusZ, int cornerRadius, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.cornerRadius = Math.max(0, Math.min(cornerRadius, Math.min(radiusX, radiusZ)));
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        // Iterate from -radiusX to +radiusX and -radiusZ to +radiusZ
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                boolean shouldPlace;
                
                if (hollow) {
                    shouldPlace = isOnEdge(x, z);
                } else {
                    shouldPlace = isInside(x, z);
                }
                
                if (shouldPlace) {
                    addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                }
            }
        }

        return blocks;
    }

    /**
     * Checks if a point is inside the rectangle (considering rounded corners).
     */
    private boolean isInside(int x, int z) {
        if (cornerRadius <= 0) {
            // No rounded corners - always inside the iteration bounds
            return true;
        }

        // Check corner regions
        int cornerX = radiusX - cornerRadius;
        int cornerZ = radiusZ - cornerRadius;

        // If we're in a corner region, check circular distance
        if (Math.abs(x) > cornerX && Math.abs(z) > cornerZ) {
            double dx = Math.abs(x) - cornerX;
            double dz = Math.abs(z) - cornerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            return dist <= cornerRadius;
        }

        return true;
    }

    /**
     * Checks if a point is on the edge of the rectangle (for hollow).
     * Matches the website's logic exactly.
     */
    private boolean isOnEdge(int x, int z) {
        // First check if inside the outer bounds
        if (!isInside(x, z)) {
            return false;
        }

        if (cornerRadius <= 0) {
            // No rounded corners - simple edge check
            // Edge if |x| > radiusX - thickness OR |z| > radiusZ - thickness
            return Math.abs(x) > radiusX - thickness || Math.abs(z) > radiusZ - thickness;
        }

        // With rounded corners
        int cornerX = radiusX - cornerRadius;
        int cornerZ = radiusZ - cornerRadius;

        // Check if we're in a corner region
        if (Math.abs(x) > cornerX && Math.abs(z) > cornerZ) {
            // In corner region - use circular ring logic
            double dx = Math.abs(x) - cornerX;
            double dz = Math.abs(z) - cornerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            // Edge if dist is in (cornerRadius - thickness, cornerRadius]
            return dist > cornerRadius - thickness && dist <= cornerRadius;
        } else {
            // Not in corner region - use rectangular edge logic
            return Math.abs(x) > radiusX - thickness || Math.abs(z) > radiusZ - thickness;
        }
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Rectangle" : "Rectangle";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.rectangle";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hrect <block> <radiusX> <radiusZ> [cornerRadius] <thickness>" 
                     : "/mct rect <block> <radiusX> <radiusZ> [cornerRadius]";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow rectangle outline" : "Creates a filled rectangle";
    }

    @Override
    public int getEstimatedBlockCount() {
        // Full dimensions
        int fullWidth = 2 * radiusX + 1;
        int fullDepth = 2 * radiusZ + 1;
        int total = fullWidth * fullDepth;
        
        // Subtract corner areas if rounded
        if (cornerRadius > 0) {
            // Each corner: square area minus quarter circle
            int cornerSquare = cornerRadius * cornerRadius;
            double quarterCircle = Math.PI * cornerRadius * cornerRadius / 4.0;
            total -= (int) (4 * (cornerSquare - quarterCircle));
        }
        
        // For hollow, subtract inner area
        if (hollow) {
            int innerRadiusX = radiusX - thickness;
            int innerRadiusZ = radiusZ - thickness;
            
            if (innerRadiusX > 0 && innerRadiusZ > 0) {
                int innerWidth = 2 * innerRadiusX + 1;
                int innerDepth = 2 * innerRadiusZ + 1;
                int innerTotal = innerWidth * innerDepth;
                
                // Subtract inner corner adjustments if rounded
                if (cornerRadius > 0) {
                    int innerCornerRadius = Math.max(0, cornerRadius - thickness);
                    if (innerCornerRadius > 0) {
                        int innerCornerSquare = innerCornerRadius * innerCornerRadius;
                        double innerQuarterCircle = Math.PI * innerCornerRadius * innerCornerRadius / 4.0;
                        innerTotal -= (int) (4 * (innerCornerSquare - innerQuarterCircle));
                    }
                }
                
                total -= innerTotal;
            }
        }
        
        return Math.max(0, total);
    }
}
