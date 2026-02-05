package com.mctools.shapes;

import org.bukkit.Location;
import java.util.List;

/**
 * Interface for all geometric shapes.
 * 
 * <p>Defines the contract for shape generation, including
 * block position calculation and shape metadata.</p>
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public interface Shape {

    /**
     * Generates the list of block locations for this shape.
     * 
     * @param center The center/origin point of the shape
     * @return A list of locations where blocks should be placed
     */
    List<Location> generate(Location center);

    /**
     * Gets the name of this shape.
     * 
     * @return The shape name
     */
    String getName();

    /**
     * Gets the permission node required to use this shape.
     * 
     * @return The permission node
     */
    String getPermission();

    /**
     * Gets the usage syntax for this shape.
     * 
     * @return The usage string
     */
    String getUsage();

    /**
     * Gets a description of this shape.
     * 
     * @return The description
     */
    String getDescription();

    /**
     * Checks if this is a hollow variant.
     * 
     * @return true if hollow
     */
    boolean isHollow();

    /**
     * Gets the estimated block count for this shape.
     * 
     * @return The estimated number of blocks
     */
    int getEstimatedBlockCount();
}
