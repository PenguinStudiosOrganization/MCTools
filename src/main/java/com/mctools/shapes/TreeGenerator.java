package com.mctools.shapes;

import com.mctools.utils.SeededRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.*;

/**
 * Procedural tree generator with customizable parameters.
 * Generates trunk, branches, foliage, and optional roots.
 */
public class TreeGenerator {

    public enum WoodType {
        OAK(Material.OAK_LOG, Material.OAK_FENCE, Material.OAK_SLAB, Material.OAK_LEAVES),
        SPRUCE(Material.SPRUCE_LOG, Material.SPRUCE_FENCE, Material.SPRUCE_SLAB, Material.SPRUCE_LEAVES),
        BIRCH(Material.BIRCH_LOG, Material.BIRCH_FENCE, Material.BIRCH_SLAB, Material.BIRCH_LEAVES),
        JUNGLE(Material.JUNGLE_LOG, Material.JUNGLE_FENCE, Material.JUNGLE_SLAB, Material.JUNGLE_LEAVES),
        ACACIA(Material.ACACIA_LOG, Material.ACACIA_FENCE, Material.ACACIA_SLAB, Material.ACACIA_LEAVES),
        DARK_OAK(Material.DARK_OAK_LOG, Material.DARK_OAK_FENCE, Material.DARK_OAK_SLAB, Material.DARK_OAK_LEAVES),
        MANGROVE(Material.MANGROVE_LOG, Material.MANGROVE_FENCE, Material.MANGROVE_SLAB, Material.MANGROVE_LEAVES),
        CHERRY(Material.CHERRY_LOG, Material.CHERRY_FENCE, Material.CHERRY_SLAB, Material.CHERRY_LEAVES),
        CRIMSON(Material.CRIMSON_STEM, Material.CRIMSON_FENCE, Material.CRIMSON_SLAB, Material.NETHER_WART_BLOCK),
        WARPED(Material.WARPED_STEM, Material.WARPED_FENCE, Material.WARPED_SLAB, Material.WARPED_WART_BLOCK);

        public final Material log, fence, slab, leaves;

        WoodType(Material log, Material fence, Material slab, Material leaves) {
            this.log = log;
            this.fence = fence;
            this.slab = slab;
            this.leaves = leaves;
        }
    }

    private final long seed;
    private final int trunkHeight;
    private final int trunkRadius;
    private final double branchDensity;
    private final double foliageDensity;
    private final int foliageRadius;
    private final boolean enableRoots;
    private final boolean useSpecialBlocks;
    private final WoodType woodType;

    public TreeGenerator(long seed, int trunkHeight, int trunkRadius,
                         double branchDensity, double foliageDensity, int foliageRadius,
                         boolean enableRoots, boolean useSpecialBlocks, WoodType woodType) {
        this.seed = seed;
        this.trunkHeight = trunkHeight;
        this.trunkRadius = trunkRadius;
        this.branchDensity = Math.max(0, Math.min(1, branchDensity));
        this.foliageDensity = Math.max(0, Math.min(1, foliageDensity));
        this.foliageRadius = foliageRadius;
        this.enableRoots = enableRoots;
        this.useSpecialBlocks = useSpecialBlocks;
        this.woodType = woodType;
    }

    /**
     * Generates the tree and returns a map of Location to BlockData.
     */
    public Map<Location, BlockData> generate(Location center) {
        Map<Location, BlockData> blocks = new LinkedHashMap<>();
        Set<String> placedKeys = new HashSet<>();
        SeededRandom rng = new SeededRandom(seed);
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // === TRUNK ===
        for (int y = 0; y < trunkHeight; y++) {
            double t = (double) y / trunkHeight;
            int currentRadius = Math.max(1, (int) Math.floor(trunkRadius * (1 - t * 0.3)));
            for (int x = -currentRadius; x <= currentRadius; x++) {
                for (int z = -currentRadius; z <= currentRadius; z++) {
                    if (Math.sqrt(x * x + z * z) <= currentRadius) {
                        addBlock(blocks, placedKeys, world, cx, cy, cz, x, y, z, woodType.log);
                    }
                }
            }
        }

        // === BRANCHES ===
        List<int[]> branchEndpoints = new ArrayList<>();
        int numBranches = (int) Math.floor(4 + rng.next() * 4 * branchDensity);

        for (int i = 0; i < numBranches; i++) {
            int startY = (int) Math.floor(trunkHeight * 0.5 + rng.next() * trunkHeight * 0.4);
            double angle = rng.nextAngle();
            double length = 3 + rng.next() * 5;
            double pitch = 0.3 + rng.next() * 0.4;

            double bx = 0, by = startY, bz = 0;
            double dx = Math.cos(angle) * Math.cos(pitch);
            double dy = Math.sin(pitch);
            double dz = Math.sin(angle) * Math.cos(pitch);

            for (int step = 0; step < (int) length; step++) {
                bx += dx;
                by += dy;
                bz += dz;
                int ix = (int) Math.round(bx);
                int iy = (int) Math.round(by);
                int iz = (int) Math.round(bz);
                boolean isSpecial = useSpecialBlocks && step > length * 0.5;
                Material mat = isSpecial ? woodType.fence : woodType.log;
                addBlock(blocks, placedKeys, world, cx, cy, cz, ix, iy, iz, mat);
            }
            branchEndpoints.add(new int[]{(int) Math.round(bx), (int) Math.round(by), (int) Math.round(bz)});
        }

        // === FOLIAGE ===
        List<int[]> foliagePoints = new ArrayList<>();
        foliagePoints.add(new int[]{0, trunkHeight, 0});
        foliagePoints.addAll(branchEndpoints);

        for (int[] fp : foliagePoints) {
            double fr = foliageRadius * (0.5 + rng.next() * 0.5);
            int frInt = (int) Math.ceil(fr);

            for (int fdx = -frInt; fdx <= frInt; fdx++) {
                for (int fdy = -frInt; fdy <= frInt; fdy++) {
                    for (int fdz = -frInt; fdz <= frInt; fdz++) {
                        if (Math.sqrt(fdx * fdx + fdy * fdy + fdz * fdz) <= fr && rng.next() < foliageDensity) {
                            int fx = fp[0] + fdx;
                            int fy = fp[1] + fdy;
                            int fz = fp[2] + fdz;
                            addBlock(blocks, placedKeys, world, cx, cy, cz, fx, fy, fz, woodType.leaves);
                        }
                    }
                }
            }
        }

        // === ROOTS ===
        if (enableRoots) {
            int rootCount = 3 + (int) Math.floor(rng.next() * 3);

            for (int i = 0; i < rootCount; i++) {
                double angle = (double) i / rootCount * Math.PI * 2 + rng.vary(0, 0.3);
                double length = 3 + rng.next() * 5;

                for (int step = 0; step <= (int) length; step++) {
                    double t = step / length;
                    int rx = (int) Math.floor(Math.cos(angle) * t * length);
                    int ry = (int) Math.floor(-t * length * 0.3);
                    int rz = (int) Math.floor(Math.sin(angle) * t * length);
                    boolean isSpecial = useSpecialBlocks && t > 0.7;
                    Material mat = isSpecial ? woodType.slab : woodType.log;
                    addBlock(blocks, placedKeys, world, cx, cy, cz, rx, ry, rz, mat);
                }
            }
        }

        return blocks;
    }

    private void addBlock(Map<Location, BlockData> blocks, Set<String> placedKeys,
                          World world, int cx, int cy, int cz,
                          int ox, int oy, int oz, Material material) {
        String key = ox + "," + oy + "," + oz;
        if (!placedKeys.contains(key)) {
            placedKeys.add(key);
            blocks.put(new Location(world, cx + ox, cy + oy, cz + oz), material.createBlockData());
        }
    }

    public int getEstimatedBlockCount() {
        int trunk = (int) (Math.PI * trunkRadius * trunkRadius * trunkHeight);
        int branches = (int) (5 * branchDensity * 6);
        int foliage = (int) ((4.0 / 3.0) * Math.PI * foliageRadius * foliageRadius * foliageRadius
                * foliageDensity * (1 + 4 * branchDensity));
        int roots = enableRoots ? 20 : 0;
        return trunk + branches + foliage + roots;
    }

    public String getName() {
        return "Procedural Tree";
    }

    public String getPermission() {
        return "mctools.shapes.tree";
    }
}
