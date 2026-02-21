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
 * Bridge generator for the MCT Path Tool system.
 *
 * <p>Generates a bridge deck with optional railings, support pillars, and ramps
 * along a sampled curve path.</p>
 */
public class BridgeGenerator {

    private final CurveEngine curveEngine = new CurveEngine();

    /**
     * Generates the bridge block map from the given session.
     *
     * @param session The player's path session with positions and settings
     * @return Map of Location → BlockData for the bridge
     */
    public Map<Location, BlockData> generate(PathSession session) {
        List<Location> controlPoints = session.getPositions();
        if (controlPoints.size() < 2) return Collections.emptyMap();

        World world = controlPoints.get(0).getWorld();
        if (world == null) return Collections.emptyMap();

        // Read settings
        double resolution = session.getSettingDouble("resolution", 0.5);
        int width = session.getSettingInt("width", 5);
        Material deckMaterial = parseMaterial(session.getSettingString("deck-material", "STONE_BRICK_SLAB"), Material.STONE_BRICK_SLAB);
        boolean railings = session.getSettingBool("railings", true);
        Material railingMaterial = parseMaterial(session.getSettingString("railing-material", "STONE_BRICK_WALL"), Material.STONE_BRICK_WALL);
        boolean supports = session.getSettingBool("supports", true);
        Material supportMaterial = parseMaterial(session.getSettingString("support-material", "STONE_BRICKS"), Material.STONE_BRICKS);
        int supportSpacing = session.getSettingInt("support-spacing", 8);
        int supportWidth = session.getSettingInt("support-width", 3);
        int supportMaxDepth = session.getSettingInt("support-max-depth", 40);
        String heightMode = session.getSettingString("height-mode", "auto");
        boolean ramps = session.getSettingBool("ramps", true);
        Material rampMaterial = parseMaterial(session.getSettingString("ramp-material", "STONE_BRICK_STAIRS"), Material.STONE_BRICK_STAIRS);

        // Sample the curve
        List<Vector> sampledPath = curveEngine.sampleCurve(controlPoints, resolution, "catmullrom");
        if (sampledPath.isEmpty()) return Collections.emptyMap();

        Map<Location, BlockData> blockMap = new LinkedHashMap<>();
        int halfWidth = width / 2;
        boolean oddWidth = (width % 2 == 1);

        // Track distance along path for support spacing
        double distanceAccum = 0;
        Vector prevPoint = null;

        for (int i = 0; i < sampledPath.size(); i++) {
            Vector point = sampledPath.get(i);
            Vector tangent = CurveEngine.getTangent(sampledPath, i);
            Vector perp = CurveEngine.getPerpendicular(tangent);

            // Calculate distance from previous point
            if (prevPoint != null) {
                distanceAccum += point.distance(prevPoint);
            }
            prevPoint = point.clone();

            // Determine deck Y level
            int deckY;
            if ("fixed".equalsIgnoreCase(heightMode)) {
                deckY = (int) Math.round(point.getY());
            } else {
                // Auto mode: use the smooth curve Y
                deckY = (int) Math.round(point.getY());
            }

            // Place deck blocks across width
            for (int w = -halfWidth; w <= halfWidth; w++) {
                if (!oddWidth && w == halfWidth) continue;

                double px = point.getX() + perp.getX() * w;
                double pz = point.getZ() + perp.getZ() * w;
                int bx = (int) Math.floor(px);
                int bz = (int) Math.floor(pz);

                Location deckLoc = new Location(world, bx, deckY, bz);

                // Don't overwrite if already placed (overlapping curves)
                if (!blockMap.containsKey(deckLoc)) {
                    blockMap.put(deckLoc, deckMaterial.createBlockData());
                }

                // Railings on edges
                boolean isEdge = (w == -halfWidth || w == (oddWidth ? halfWidth : halfWidth - 1));
                if (railings && isEdge) {
                    Location railLoc = new Location(world, bx, deckY + 1, bz);
                    if (!blockMap.containsKey(railLoc)) {
                        blockMap.put(railLoc, railingMaterial.createBlockData());
                    }
                }
            }

            // Support pillars at intervals
            if (supports && (i == 0 || distanceAccum >= supportSpacing)) {
                if (i != 0) distanceAccum -= supportSpacing;

                placeSupportPillar(world, point, perp, halfWidth, oddWidth, width,
                        deckY, supportMaterial, supportWidth, supportMaxDepth, blockMap);
            }
        }

        // Ramps at start and end
        if (ramps && sampledPath.size() >= 2) {
            generateRamp(world, sampledPath, 0, true, halfWidth, oddWidth, width,
                    deckMaterial, rampMaterial, blockMap);
            generateRamp(world, sampledPath, sampledPath.size() - 1, false, halfWidth, oddWidth, width,
                    deckMaterial, rampMaterial, blockMap);
        }

        return blockMap;
    }

    /**
     * Places support pillars from deck level down to solid ground.
     * Pillars are cylindrical with configurable radius (support-width).
     * For wide bridges (>= 7), two pillars are placed under the edges.
     *
     * @param supportRadius The radius of each cylindrical pillar (support-width setting)
     */
    private void placeSupportPillar(World world, Vector point, Vector perp,
                                     int halfWidth, boolean oddWidth, int totalWidth,
                                     int deckY, Material supportMaterial,
                                     int supportRadius, int maxDepth,
                                     Map<Location, BlockData> blockMap) {
        // Determine pillar center offsets along the perpendicular axis
        List<Integer> pillarOffsets = new ArrayList<>();
        if (totalWidth >= 7) {
            pillarOffsets.add(-halfWidth + 1);
            pillarOffsets.add(oddWidth ? halfWidth - 1 : halfWidth - 2);
        } else {
            pillarOffsets.add(0); // center
        }

        int r = Math.max(1, supportRadius);
        double rSq = (r - 0.5) * (r - 0.5); // Slightly smaller for clean circle

        for (int offset : pillarOffsets) {
            double centerX = point.getX() + perp.getX() * offset;
            double centerZ = point.getZ() + perp.getZ() * offset;
            int cx = (int) Math.floor(centerX);
            int cz = (int) Math.floor(centerZ);

            // Find the deepest Y where the center column hits solid ground
            int groundY = deckY - maxDepth;
            for (int dy = 1; dy <= maxDepth; dy++) {
                int y = deckY - dy;
                if (y < world.getMinHeight()) { groundY = world.getMinHeight(); break; }
                Block existing = world.getBlockAt(cx, y, cz);
                if (!existing.getType().isAir() && !existing.isLiquid()) {
                    groundY = y;
                    break;
                }
            }

            // Place cylindrical pillar from just below deck down to ground
            for (int dy = 1; dy <= maxDepth; dy++) {
                int y = deckY - dy;
                if (y < world.getMinHeight()) break;
                if (y < groundY) break;

                // Fill a circle of radius r at this Y level
                for (int dx = -(r - 1); dx <= (r - 1); dx++) {
                    for (int dz = -(r - 1); dz <= (r - 1); dz++) {
                        // Cylindrical check: dx² + dz² <= r²
                        if (dx * dx + dz * dz <= rSq) {
                            Location pillarLoc = new Location(world, cx + dx, y, cz + dz);
                            if (!blockMap.containsKey(pillarLoc)) {
                                blockMap.put(pillarLoc, supportMaterial.createBlockData());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates a ramp at the start or end of the bridge.
     */
    private void generateRamp(World world, List<Vector> sampledPath, int pathIndex,
                               boolean isStart, int halfWidth, boolean oddWidth, int totalWidth,
                               Material deckMaterial, Material rampMaterial,
                               Map<Location, BlockData> blockMap) {
        Vector point = sampledPath.get(pathIndex);
        Vector tangent = CurveEngine.getTangent(sampledPath, pathIndex);
        Vector perp = CurveEngine.getPerpendicular(tangent);

        int deckY = (int) Math.round(point.getY());

        // Find ground level below the ramp start
        int groundY = deckY;
        int bx = (int) Math.floor(point.getX());
        int bz = (int) Math.floor(point.getZ());
        for (int dy = 0; dy <= 40; dy++) {
            Block b = world.getBlockAt(bx, deckY - dy, bz);
            if (!b.getType().isAir() && !b.isLiquid()) {
                groundY = deckY - dy;
                break;
            }
            if (dy == 40) groundY = deckY; // No ground found, skip ramp
        }

        int heightDiff = deckY - groundY;
        if (heightDiff <= 0) return; // Already at ground level

        // Direction: ramp extends away from the bridge
        Vector rampDir = isStart ? tangent.clone().multiply(-1) : tangent.clone();

        for (int step = 1; step <= heightDiff; step++) {
            int rampY = deckY - step;
            double rx = point.getX() + rampDir.getX() * step;
            double rz = point.getZ() + rampDir.getZ() * step;

            for (int w = -halfWidth; w <= halfWidth; w++) {
                if (!oddWidth && w == halfWidth) continue;

                int rbx = (int) Math.floor(rx + perp.getX() * w);
                int rbz = (int) Math.floor(rz + perp.getZ() * w);

                Location rampLoc = new Location(world, rbx, rampY, rbz);

                if (!blockMap.containsKey(rampLoc)) {
                    // Use stairs for the ramp surface
                    BlockData stairData = rampMaterial.createBlockData();
                    if (stairData instanceof Stairs stairs) {
                        stairs.setFacing(getStairFacing(rampDir));
                        stairs.setHalf(Stairs.Half.BOTTOM);
                        blockMap.put(rampLoc, stairs);
                    } else {
                        blockMap.put(rampLoc, deckMaterial.createBlockData());
                    }
                }
            }
        }
    }

    /**
     * Gets the stair facing direction from a direction vector.
     */
    private BlockFace getStairFacing(Vector direction) {
        double absX = Math.abs(direction.getX());
        double absZ = Math.abs(direction.getZ());

        if (absX > absZ) {
            return direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            return direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.equalsIgnoreCase("none")) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
