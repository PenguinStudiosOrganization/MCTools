package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a tube (pipe) shape (3D).
 * A tube is a cylinder with an inner radius cut out.
 */
public class Tube extends Shape3D {

    private final int height;
    private final int radius;
    private final int innerRadius;

    /**
     * Creates a tube.
     *
     * @param height      The height of the tube
     * @param radius      The outer radius
     * @param innerRadius The inner radius (must be less than radius)
     */
    public Tube(int height, int radius, int innerRadius) {
        super(false, 1);
        this.height = height;
        this.radius = radius;
        this.innerRadius = innerRadius;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist <= radius && dist >= innerRadius) {
                        addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return "Tube";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.tube";
    }

    @Override
    public String getUsage() {
        return "/mct tube <block> <radius> <height> <innerRadius>";
    }

    @Override
    public String getDescription() {
        return "Creates a tube (pipe) with inner and outer radius";
    }

    @Override
    public boolean isHollow() {
        return false;
    }

    @Override
    public int getEstimatedBlockCount() {
        double outer = Math.PI * radius * radius * height;
        double inner = Math.PI * innerRadius * innerRadius * height;
        return (int) (outer - inner);
    }
}
