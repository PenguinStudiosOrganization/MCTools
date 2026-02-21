package com.mctools.path;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Road generator for the MCT Path Tool system.
 *
 * <p>Generates a road surface along a sampled curve path with configurable
 * width, materials, borders, centerline, terrain adaptation, and slope handling.</p>
 */
public class RoadGenerator {

    private final CurveEngine curveEngine = new CurveEngine();

    /**
     * Generates the road block map from the given session.
     *
     * @param session The player's path session with positions and settings
     * @return Map of Location → BlockData for the road
     */
    public Map<Location, BlockData> generate(PathSession session) {
        List<Location> controlPoints = session.getPositions();
        if (controlPoints.size() < 2) return Collections.emptyMap();

        World world = controlPoints.get(0).getWorld();
        if (world == null) return Collections.emptyMap();

        // Read settings
        double resolution = session.getSettingDouble("resolution", 0.5);
        String algorithm = session.getSettingString("algorithm", "catmullrom");
        // Road uses curve settings for algorithm if not set in road settings
        if (session.getActiveMode() == PathSession.Mode.ROAD) {
            algorithm = "catmullrom"; // Road always uses catmullrom for smooth paths
        }
        int width = session.getSettingInt("width", 5);
        Material mainMaterial = parseMaterial(session.getSettingString("material", "STONE_BRICKS"), Material.STONE_BRICKS);
        Material borderMaterial = parseMaterialOrNull(session.getSettingString("border", "POLISHED_ANDESITE"));
        Material centerlineMaterial = parseMaterialOrNull(session.getSettingString("centerline", "none"));
        boolean useSlabs = session.getSettingBool("use-slabs", true);
        boolean useStairs = session.getSettingBool("use-stairs", true);
        boolean terrainAdapt = session.getSettingBool("terrain-adapt", true);
        int clearance = session.getSettingInt("clearance", 3);
        int fillBelow = session.getSettingInt("fill-below", 4);
        Material fillMaterial = parseMaterial(session.getSettingString("fill-material", "COBBLESTONE"), Material.COBBLESTONE);

        // Sample the curve
        List<Vector> sampledPath = curveEngine.sampleCurve(controlPoints, resolution, algorithm);
        if (sampledPath.isEmpty()) return Collections.emptyMap();

        Map<Location, BlockData> blockMap = new LinkedHashMap<>();
        int halfWidth = width / 2;
        boolean oddWidth = (width % 2 == 1);

        for (int i = 0; i < sampledPath.size(); i++) {
            Vector point = sampledPath.get(i);
            Vector tangent = CurveEngine.getTangent(sampledPath, i);
            Vector perp = CurveEngine.getPerpendicular(tangent);

            // Determine the Y level for this point
            int baseY = (int) Math.round(point.getY());

            // Determine slope for slab/stair placement
            double slopeDelta = 0;
            BlockFace stairFacing = null;
            if (i > 0) {
                slopeDelta = point.getY() - sampledPath.get(i - 1).getY();
                if (useStairs && Math.abs(slopeDelta) >= 0.4) {
                    stairFacing = getStairFacing(tangent, slopeDelta > 0);
                }
            }

            for (int w = -halfWidth; w <= halfWidth; w++) {
                // Skip the extra column for even widths
                if (!oddWidth && w == halfWidth) continue;

                double px = point.getX() + perp.getX() * w;
                double pz = point.getZ() + perp.getZ() * w;
                int bx = (int) Math.floor(px);
                int bz = (int) Math.floor(pz);

                Location surfaceLoc = new Location(world, bx, baseY, bz);

                // Determine which material to use
                boolean isBorder = (w == -halfWidth || w == halfWidth - (oddWidth ? 0 : 1));
                boolean isCenter = oddWidth && w == 0;

                Material surfaceMat;
                if (isBorder && borderMaterial != null) {
                    surfaceMat = borderMaterial;
                } else if (isCenter && centerlineMaterial != null) {
                    surfaceMat = centerlineMaterial;
                } else {
                    surfaceMat = mainMaterial;
                }

                // Place surface block (with slab/stair logic)
                BlockData surfaceData = createSurfaceBlock(surfaceMat, slopeDelta, stairFacing,
                        useSlabs, useStairs, isBorder);
                blockMap.put(surfaceLoc, surfaceData);

                // Terrain adaptation
                if (terrainAdapt) {
                    // Fill below
                    for (int dy = 1; dy <= fillBelow; dy++) {
                        Location below = new Location(world, bx, baseY - dy, bz);
                        Block existingBlock = below.getBlock();
                        if (existingBlock.getType().isAir() || existingBlock.isLiquid()) {
                            blockMap.put(below, fillMaterial.createBlockData());
                        } else {
                            break; // Hit solid ground, stop filling
                        }
                    }

                    // Clear above
                    for (int dy = 1; dy <= clearance; dy++) {
                        Location above = new Location(world, bx, baseY + dy, bz);
                        Block existingBlock = above.getBlock();
                        if (!existingBlock.getType().isAir()) {
                            blockMap.put(above, Material.AIR.createBlockData());
                        }
                    }
                }
            }
        }

        return blockMap;
    }

    /**
     * Creates the appropriate surface block data (full block, slab, or stair).
     */
    private BlockData createSurfaceBlock(Material material, double slopeDelta, BlockFace stairFacing,
                                          boolean useSlabs, boolean useStairs, boolean isBorder) {
        double absDelta = Math.abs(slopeDelta);

        // Try stairs for steep slopes
        if (useStairs && stairFacing != null && absDelta >= 0.4 && !isBorder) {
            Material stairMat = findStairVariant(material);
            if (stairMat != null) {
                BlockData data = stairMat.createBlockData();
                if (data instanceof Stairs stairs) {
                    stairs.setFacing(stairFacing);
                    if (slopeDelta < 0) {
                        stairs.setHalf(Stairs.Half.TOP);
                    } else {
                        stairs.setHalf(Stairs.Half.BOTTOM);
                    }
                    return stairs;
                }
            }
        }

        // Try slabs for gentle slopes
        if (useSlabs && absDelta >= 0.2 && absDelta < 0.6) {
            Material slabMat = findSlabVariant(material);
            if (slabMat != null) {
                BlockData data = slabMat.createBlockData();
                if (data instanceof Slab slab) {
                    slab.setType(slopeDelta > 0 ? Slab.Type.BOTTOM : Slab.Type.TOP);
                    return slab;
                }
            }
        }

        return material.createBlockData();
    }

    /**
     * Determines the stair facing direction based on the tangent and slope direction.
     */
    private BlockFace getStairFacing(Vector tangent, boolean ascending) {
        // The stair should face the direction of travel when ascending
        double absX = Math.abs(tangent.getX());
        double absZ = Math.abs(tangent.getZ());

        if (absX > absZ) {
            if (ascending) {
                return tangent.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
            } else {
                return tangent.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
            }
        } else {
            if (ascending) {
                return tangent.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            } else {
                return tangent.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
            }
        }
    }

    /**
     * Finds the stair variant for a given material.
     */
    private Material findStairVariant(Material material) {
        String name = material.name();
        // Common stair mappings
        String[] suffixes = {"_STAIRS", "S_STAIRS"};
        String baseName = name.replace("_BRICKS", "_BRICK").replace("_PLANKS", "");

        for (String suffix : suffixes) {
            try {
                return Material.valueOf(baseName + suffix);
            } catch (IllegalArgumentException ignored) {}
        }

        // Direct name + _STAIRS
        try {
            return Material.valueOf(name + "_STAIRS");
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    /**
     * Finds the slab variant for a given material.
     */
    private Material findSlabVariant(Material material) {
        String name = material.name();
        String baseName = name.replace("_BRICKS", "_BRICK").replace("_PLANKS", "");

        try {
            return Material.valueOf(baseName + "_SLAB");
        } catch (IllegalArgumentException ignored) {}

        try {
            return Material.valueOf(name + "_SLAB");
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.equalsIgnoreCase("none")) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private Material parseMaterialOrNull(String name) {
        if (name == null || name.equalsIgnoreCase("none")) return null;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
