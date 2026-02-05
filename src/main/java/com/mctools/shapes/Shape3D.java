package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for 3D shapes.
 * 
 * <p>Provides common functionality for shapes that exist
 * in three-dimensional space.</p>
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public abstract class Shape3D implements Shape {

    protected final boolean hollow;
    protected final int thickness;

    /**
     * Creates a new 3D shape.
     * 
     * @param hollow Whether the shape is hollow
     * @param thickness The thickness for hollow shapes
     */
    protected Shape3D(boolean hollow, int thickness) {
        this.hollow = hollow;
        this.thickness = Math.max(1, thickness);
    }

    @Override
    public boolean isHollow() {
        return hollow;
    }

    /**
     * Gets the thickness of the shape (for hollow variants).
     * 
     * @return The thickness
     */
    public int getThickness() {
        return thickness;
    }

    /**
     * Adds a block position to the list.
     * 
     * @param blocks The list to add to
     * @param world The world
     * @param centerX The center X coordinate
     * @param centerY The center Y coordinate
     * @param centerZ The center Z coordinate
     * @param offsetX The X offset from center
     * @param offsetY The Y offset from center
     * @param offsetZ The Z offset from center
     */
    protected void addBlock(List<Location> blocks, World world,
                           double centerX, double centerY, double centerZ,
                           int offsetX, int offsetY, int offsetZ) {
        blocks.add(new Location(world,
                centerX + offsetX,
                centerY + offsetY,
                centerZ + offsetZ));
    }

    /**
     * Creates a new location list.
     * 
     * @return A new ArrayList for locations
     */
    protected List<Location> createBlockList() {
        return new ArrayList<>();
    }
}
