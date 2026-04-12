package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a spiral staircase shape (3D).
 *
 * @author MCTools Team
 * @version 1.0.0
 */
public class Staircase extends Shape3D {

    private final int radius;
    private final int height;
    private final int stepWidth;

    /**
     * Creates a spiral staircase.
     *
     * @param radius    The radius of the spiral
     * @param height    The total height (number of steps)
     * @param stepWidth The width of each step (radial depth from outer edge inward)
     */
    public Staircase(int radius, int height, int stepWidth) {
        super(false, 1);
        this.radius = radius;
        this.height = height;
        this.stepWidth = Math.max(1, Math.min(stepWidth, radius));
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double centerX = center.getBlockX();
        double centerY = center.getBlockY();
        double centerZ = center.getBlockZ();

        // Each step occupies an angular slice and rises by 1 block
        // One full revolution = height steps (so the staircase spirals up)
        double anglePerStep = (2 * Math.PI) / Math.max(1, height);
        // Angular width of each step: enough to leave no gaps
        // At the outer edge, arc length per step = radius * anglePerStep
        // We need at least 1 block of arc, so we slightly oversize the angular fill
        double stepAngleWidth = anglePerStep * 1.5;

        Set<String> placed = new HashSet<>();

        for (int step = 0; step < height; step++) {
            double startAngle = step * anglePerStep;
            int y = step;

            // Sample the angular range densely to avoid gaps
            int angularSamples = Math.max(8, radius * 4);
            for (int a = 0; a <= angularSamples; a++) {
                double angle = startAngle + (stepAngleWidth * a / angularSamples);
                double cosA = Math.cos(angle);
                double sinA = Math.sin(angle);

                // Fill from outer radius inward by stepWidth
                int rMin = Math.max(1, radius - stepWidth + 1);
                for (int r = rMin; r <= radius; r++) {
                    int bx = (int) Math.round(r * cosA);
                    int bz = (int) Math.round(r * sinA);
                    String key = bx + "," + y + "," + bz;
                    if (placed.add(key)) {
                        addBlock(blocks, world, centerX, centerY, centerZ, bx, y, bz);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return "Staircase";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.staircase";
    }

    @Override
    public String getUsage() {
        return "/mct stair <block> <radius> <height> <stepWidth>";
    }

    @Override
    public String getDescription() {
        return "Creates a spiral staircase";
    }

    @Override
    public int getEstimatedBlockCount() {
        return height * stepWidth * (radius + 1);
    }
}
