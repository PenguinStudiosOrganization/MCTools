package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates an ellipsoid shape (3D).
 * An ellipsoid is a sphere with independent radii on each axis.
 */
public class Ellipsoid extends Shape3D {

    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;

    /**
     * Creates a filled ellipsoid.
     */
    public Ellipsoid(int radiusX, int radiusY, int radiusZ) {
        super(false, 1);
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
    }

    /**
     * Creates an ellipsoid (filled or hollow).
     */
    public Ellipsoid(int radiusX, int radiusY, int radiusZ, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        double rxSq = (double) radiusX * radiusX;
        double rySq = (double) radiusY * radiusY;
        double rzSq = (double) radiusZ * radiusZ;

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    double dist = (x * x) / rxSq + (y * y) / rySq + (z * z) / rzSq;

                    if (hollow) {
                        double irx = Math.max(1, radiusX - thickness);
                        double iry = Math.max(1, radiusY - thickness);
                        double irz = Math.max(1, radiusZ - thickness);
                        double innerDist = (x * x) / (irx * irx) + (y * y) / (iry * iry) + (z * z) / (irz * irz);
                        if (dist <= 1 && innerDist > 1) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radiusY, z);
                        }
                    } else {
                        if (dist <= 1) {
                            addBlock(blocks, world, centerX, centerY, centerZ, x, y + radiusY, z);
                        }
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Ellipsoid" : "Ellipsoid";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.ellipsoid";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hellipsoid <block> <radiusX> <radiusY> <radiusZ> <thickness>"
                     : "/mct ellipsoid <block> <radiusX> <radiusY> <radiusZ>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow ellipsoid shell" : "Creates a filled ellipsoid";
    }

    @Override
    public int getEstimatedBlockCount() {
        if (hollow) {
            double outer = (4.0 / 3.0) * Math.PI * radiusX * radiusY * radiusZ;
            double irx = Math.max(1, radiusX - thickness);
            double iry = Math.max(1, radiusY - thickness);
            double irz = Math.max(1, radiusZ - thickness);
            double inner = (4.0 / 3.0) * Math.PI * irx * iry * irz;
            return (int) (outer - inner);
        }
        return (int) ((4.0 / 3.0) * Math.PI * radiusX * radiusY * radiusZ);
    }
}
