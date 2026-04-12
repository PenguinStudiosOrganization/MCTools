package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Generates a roof shape (3D).
 * Supports three styles: peaked (gable), hip, and flat (with raised edge).
 *
 * @author MCTools Team
 * @version 1.0.0
 */
public class Roof extends Shape3D {

    private final int width;
    private final int length;
    private final double pitch;
    private final String style;

    /**
     * Creates a filled roof.
     *
     * @param width  The width of the roof
     * @param length The length/depth of the roof
     * @param pitch  The slope (1.0 = 45 degrees, 0.5 = gentle, 2.0 = steep)
     * @param style  The roof style: "peaked", "hip", or "flat"
     */
    public Roof(int width, int length, double pitch, String style) {
        super(false, 1);
        this.width = width;
        this.length = length;
        this.pitch = pitch;
        this.style = style;
    }

    /**
     * Creates a roof (filled or hollow).
     *
     * @param width     The width of the roof
     * @param length    The length/depth of the roof
     * @param pitch     The slope (1.0 = 45 degrees, 0.5 = gentle, 2.0 = steep)
     * @param style     The roof style: "peaked", "hip", or "flat"
     * @param hollow    Whether the roof is hollow
     * @param thickness The thickness for hollow roofs
     */
    public Roof(int width, int length, double pitch, String style, boolean hollow, int thickness) {
        super(hollow, thickness);
        this.width = width;
        this.length = length;
        this.pitch = pitch;
        this.style = style;
    }

    @Override
    public List<Location> generate(Location center) {
        return switch (style) {
            case "hip" -> generateHip(center);
            case "flat" -> generateFlat(center);
            default -> generatePeaked(center);
        };
    }

    /**
     * Peaked (gable) roof: two sloping sides meeting at a ridge along Z axis.
     * For each Y level, compute how far inward from each side the slope has reached.
     * The ridge runs along the full length.
     */
    private List<Location> generatePeaked(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double cx = center.getBlockX();
        double cy = center.getBlockY();
        double cz = center.getBlockZ();

        int halfWidth = width / 2;
        int halfLength = length / 2;
        // Maximum height: when the two slopes meet at the center
        int roofHeight = Math.max(1, (int) Math.ceil(halfWidth * pitch));

        for (int y = 0; y <= roofHeight; y++) {
            // How many blocks inward from each side at this height
            // At y=0, full width. At y=roofHeight, the two sides meet.
            int inset = (int) Math.floor(y / pitch);
            int xStart = -halfWidth + inset;
            int xEnd = halfWidth - inset;

            if (xStart > xEnd) break;

            for (int x = xStart; x <= xEnd; x++) {
                for (int z = -halfLength; z <= halfLength; z++) {
                    if (hollow) {
                        // Shell: only the outer surface
                        boolean isSlope = (x == xStart) || (x == xEnd);
                        boolean isGableEnd = (z == -halfLength) || (z == halfLength);
                        boolean isBase = (y == 0);
                        boolean isRidge = (xStart == xEnd); // top row = ridge
                        if (isSlope || isGableEnd || isBase || isRidge) {
                            addBlock(blocks, world, cx, cy, cz, x, y, z);
                        }
                    } else {
                        addBlock(blocks, world, cx, cy, cz, x, y, z);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Hip roof: slopes inward from all four sides.
     */
    private List<Location> generateHip(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double cx = center.getBlockX();
        double cy = center.getBlockY();
        double cz = center.getBlockZ();

        int halfWidth = width / 2;
        int halfLength = length / 2;
        int roofHeight = Math.max(1, (int) Math.ceil(Math.min(halfWidth, halfLength) * pitch));

        for (int y = 0; y <= roofHeight; y++) {
            int inset = (int) Math.floor(y / pitch);

            int xMin = -halfWidth + inset;
            int xMax = halfWidth - inset;
            int zMin = -halfLength + inset;
            int zMax = halfLength - inset;

            if (xMin > xMax || zMin > zMax) break;

            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (hollow) {
                        boolean isEdge = (x == xMin) || (x == xMax)
                                || (z == zMin) || (z == zMax)
                                || (y == 0)
                                || (xMin == xMax || zMin == zMax); // top ridge/point
                        if (isEdge) {
                            addBlock(blocks, world, cx, cy, cz, x, y, z);
                        }
                    } else {
                        addBlock(blocks, world, cx, cy, cz, x, y, z);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Flat roof with a raised parapet/edge.
     */
    private List<Location> generateFlat(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        double cx = center.getBlockX();
        double cy = center.getBlockY();
        double cz = center.getBlockZ();

        int halfWidth = width / 2;
        int halfLength = length / 2;
        int edgeHeight = Math.max(1, (int) Math.ceil(pitch * 2));

        // Flat top surface
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfLength; z <= halfLength; z++) {
                addBlock(blocks, world, cx, cy, cz, x, 0, z);
            }
        }

        // Raised edge/parapet
        for (int y = 1; y <= edgeHeight; y++) {
            for (int x = -halfWidth; x <= halfWidth; x++) {
                for (int z = -halfLength; z <= halfLength; z++) {
                    boolean isBorder = (x == -halfWidth) || (x == halfWidth)
                            || (z == -halfLength) || (z == halfLength);
                    if (isBorder) {
                        addBlock(blocks, world, cx, cy, cz, x, y, z);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return hollow ? "Hollow Roof (" + style + ")" : "Roof (" + style + ")";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.roof";
    }

    @Override
    public String getUsage() {
        return hollow ? "/mct hroof <block> <width> <length> <pitch> <style> <thickness>"
                     : "/mct roof <block> <width> <length> <pitch> <style>";
    }

    @Override
    public String getDescription() {
        return hollow ? "Creates a hollow roof shell" : "Creates a roof (peaked, hip, or flat)";
    }

    @Override
    public int getEstimatedBlockCount() {
        int halfWidth = width / 2;
        int halfLength = length / 2;
        return switch (style) {
            case "hip" -> {
                int h = (int) Math.ceil(Math.min(halfWidth, halfLength) * pitch);
                yield Math.max(1, (int) (width * length * h * 0.5));
            }
            case "flat" -> {
                int edgeHeight = (int) Math.ceil(pitch * 2);
                yield width * length + 2 * (width + length) * edgeHeight;
            }
            default -> { // peaked
                int h = (int) Math.ceil(halfWidth * pitch);
                yield Math.max(1, (int) (width * length * h * 0.5));
            }
        };
    }
}
