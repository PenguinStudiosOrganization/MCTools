package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates an ellipse shape (2D, flat on ground).
 * 
 * <p>The radiusX and radiusZ parameters are half-dimensions (semi-axes).
 * For example, radiusX=10 means the ellipse extends from -10 to +10 on X axis.</p>
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Ellipse extends Shape2D {

    private final int radiusX;
    private final int radiusZ;

    /**
     * Creates a filled ellipse.
     * 
     * @param radiusX The X semi-axis of the ellipse
     * @param radiusZ The Z semi-axis of the ellipse
     */
    public Ellipse(int radiusX, int radiusZ) {
        super(false, 1);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
    }

    /**
     * Creates an ellipse (filled or hollow).
     * 
     * @param radiusX The X semi-axis of the ellipse
     * @param radiusZ The Z semi-axis of the ellipse
     * @param hollow Whether the ellipse is hollow
     * @param thickness The thickness for hollow ellipses
     */
    public Ellipse(int radiusX, int radiusZ, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        // Outer ellipse parameters
        double rxSquared = (double) radiusX * radiusX;
        double rzSquared = (double) radiusZ * radiusZ;
        
        // Inner ellipse parameters for hollow (matching website: irx = max(1, rx - t))
        int innerRx = Math.max(1, radiusX - thickness);
        int innerRz = Math.max(1, radiusZ - thickness);
        double innerRxSquared = (double) innerRx * innerRx;
        double innerRzSquared = (double) innerRz * innerRz;

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                // Ellipse equation: (x/rx)^2 + (z/rz)^2 <= 1
                double normalizedDist = (x * x) / rxSquared + (z * z) / rzSquared;
                boolean insideOuter = normalizedDist <= 1.0;
                
                if (hollow) {
                    // Check if outside inner ellipse
                    double innerNormalizedDist = (x * x) / innerRxSquared + (z * z) / innerRzSquared;
                    boolean outsideInner = innerNormalizedDist > 1.0;
                    
                    if (insideOuter && outsideInner) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                    }
                } else {
                    if (insideOuter) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, z);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Ellipse" : "Ellipse";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.ellipse";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hell <block> <radiusX> <radiusZ> <thickness>" 
                     : "/mct ell <block> <radiusX> <radiusZ>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow ellipse outline" : "Creates a filled ellipse";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            double outer = Math.PI * radiusX * radiusZ;
            int innerRx = Math.max(1, radiusX - thickness);
            int innerRz = Math.max(1, radiusZ - thickness);
            double inner = Math.PI * innerRx * innerRz;
            return (int) (outer - inner);
        }
        return (int) (Math.PI * radiusX * radiusZ);
    }
}
