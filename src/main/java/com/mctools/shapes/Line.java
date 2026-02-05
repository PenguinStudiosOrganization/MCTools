package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a straight line shape (2D, flat on ground).
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class Line extends Shape2D {

    private final int length;

    /**
     * Creates a line.
     * 
     * @param length The length of the line
     * @param thickness The thickness of the line
     */
    public Line(int length, int thickness) {
        super(false, thickness);
        this.length = length;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        int halfThickness = thickness / 2;

        // Line extends in the X direction from center
        for (int x = 0; x < length; x++) {
            for (int z = -halfThickness; z <= halfThickness; z++) {
                addBlock(blocks, world, centerX, centerY, centerZ, x, z);
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return "Line";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.line";
    }

    @Override
    public String getUsage() {
        return "/mct line <block> <length> <thickness>";
    }

    @Override
    public String getDescription() {
        return "Creates a straight line in the X direction";
    }

    @Override
    public int getEstimatedBlockCount() {
        return length * thickness;
    }
}
