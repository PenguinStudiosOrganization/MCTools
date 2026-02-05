package com.mctools.gradient;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.*;

/**
 * Maps shape positions to gradient blocks based on direction.
 */
public class GradientApplier {

    /**
     * Applies gradient blocks to shape locations.
     *
     * @param shapeBlocks    The locations of the shape
     * @param center         The center point of the shape
     * @param gradientSteps  The gradient block sequence
     * @param direction      The gradient direction: "y", "x", "z", or "radial"
     * @return Map of Location to BlockData for each position
     */
    public Map<Location, BlockData> applyGradient(List<Location> shapeBlocks, Location center,
                                                   List<GradientEngine.GradientBlock> gradientSteps,
                                                   String direction) {
        if (shapeBlocks.isEmpty() || gradientSteps.isEmpty()) {
            return Collections.emptyMap();
        }

        int numSteps = gradientSteps.size();

        // Compute position values along the gradient axis
        double[] values = new double[shapeBlocks.size()];
        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;

        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();

        for (int i = 0; i < shapeBlocks.size(); i++) {
            Location loc = shapeBlocks.get(i);
            double v = switch (direction) {
                case "x" -> loc.getBlockX() - cx;
                case "z" -> loc.getBlockZ() - cz;
                case "radial" -> Math.sqrt(
                    Math.pow(loc.getBlockX() - cx, 2) +
                    Math.pow(loc.getBlockZ() - cz, 2)
                );
                default -> loc.getBlockY() - cy; // "y"
            };
            values[i] = v;
            if (v < minVal) minVal = v;
            if (v > maxVal) maxVal = v;
        }

        double range = maxVal - minVal;

        // Build result map
        Map<Location, BlockData> result = new LinkedHashMap<>(shapeBlocks.size());

        // Pre-resolve materials for each gradient step
        BlockData[] stepData = new BlockData[numSteps];
        for (int i = 0; i < numSteps; i++) {
            String blockId = gradientSteps.get(i).blockId();
            Material mat = Material.matchMaterial(blockId);
            if (mat == null) mat = Material.matchMaterial("minecraft:" + blockId);
            if (mat == null) mat = Material.STONE; // fallback
            stepData[i] = mat.createBlockData();
        }

        for (int i = 0; i < shapeBlocks.size(); i++) {
            double t = range > 0 ? (values[i] - minVal) / range : 0;
            int stepIndex = Math.min((int) Math.floor(t * numSteps), numSteps - 1);
            result.put(shapeBlocks.get(i), stepData[stepIndex]);
        }

        return result;
    }

    /**
     * Counts distinct integer values along an axis for the shape blocks.
     * Used to determine optimal number of gradient steps.
     */
    public int countDistinctAxisValues(List<Location> blocks, Location center, String direction) {
        Set<Integer> distinct = new HashSet<>();
        double cx = center.getX();
        double cz = center.getZ();

        for (Location loc : blocks) {
            int v = switch (direction) {
                case "x" -> loc.getBlockX();
                case "z" -> loc.getBlockZ();
                case "radial" -> (int) Math.round(Math.sqrt(
                    Math.pow(loc.getBlockX() - cx, 2) +
                    Math.pow(loc.getBlockZ() - cz, 2)
                ));
                default -> loc.getBlockY(); // "y"
            };
            distinct.add(v);
        }
        return distinct.size();
    }

    /**
     * Determines if this is a 2D shape (all blocks at the same Y level).
     */
    public boolean is2DShape(List<Location> blocks) {
        if (blocks.isEmpty()) return true;
        int y = blocks.get(0).getBlockY();
        for (Location loc : blocks) {
            if (loc.getBlockY() != y) return false;
        }
        return true;
    }
}
