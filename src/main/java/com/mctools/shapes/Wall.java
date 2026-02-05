package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a wall shape (3D).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Wall extends Shape3D {

    private final int width;
    private final int height;

    /**
     * Creates a wall.
     * 
     * @param width The width of the wall
     * @param height The height of the wall
     * @param thickness The thickness of the wall
     */
    public Wall(int width, int height, int thickness) {
        super(false, thickness);
        this.width = width;
        this.height = height;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        int halfWidth = width / 2;
        int halfThickness = thickness / 2;

        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = -halfThickness; z <= halfThickness; z++) {
                    addBlock(blocks, world, centerX, centerY, centerZ, x, y, z);
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return "Wall";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.wall";
    }

    @Override
    public String getUsage() {
        return "/mct wall <block> <width> <height> <thickness>";
    }

    @Override
    public String getDescription() {
        return "Creates a solid wall";
    }

    @Override
    public int getEstimatedBlockCount() {
        return width * height * thickness;
    }
}
