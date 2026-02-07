package com.mctools.brush;

import com.mctools.MCTools;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Terrain editing brush based on a grayscale heightmap.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Compute terrain changes (raise/lower/smooth/flatten) around a target location.</li>
 *   <li>Optionally show a temporary in-world preview and auto-convert it after a timeout.</li>
 *   <li>Integrate with undo so every operation can be reverted safely.</li>
 * </ul>
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Uses a small surface-height cache to reduce repeated scans.</li>
 *   <li>Applies edge falloff to avoid harsh cut-offs on chunk/void borders.</li>
 *   <li>Preview state is tracked per-player and automatically cleaned up.</li>
 * </ul>
 */
public class TerrainBrush {

    private final MCTools plugin;
    private final BrushManager brushManager;
    
    private final Map<Long, Integer> surfaceHeightCache = new ConcurrentHashMap<>();
    private static final int CACHE_MAX_SIZE = 10000;
    private static final int EDGE_BLEND_RADIUS = 3;
    private static final double MOUNTAIN_EDGE_MARGIN = 0.15;
    private static final int TERRAIN_EDGE_FALLOFF_RADIUS = 5; // Blocks to check for terrain edge falloff
    
    // Preview system (temporary blocks placed to show the result before committing).
    private static final Material PREVIEW_BLOCK = Material.LIME_STAINED_GLASS;
    private static final int PREVIEW_TIMEOUT_SECONDS = 5;

    // Per-player preview state.
    private final Map<UUID, PreviewState> playerPreviews = new ConcurrentHashMap<>();

    public TerrainBrush(MCTools plugin, BrushManager brushManager) {
        this.plugin = plugin;
        this.brushManager = brushManager;
    }

    /**
     * Entry point for the brush.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate settings (enabled + heightmap).</li>
     *   <li>Run a safety check against server performance.</li>
     *   <li>Compute a list of block changes (no world edits yet).</li>
     *   <li>Apply changes immediately or show a preview.</li>
     * </ol>
     */
    public void apply(Player player, Location targetLocation) {
        BrushSettings settings = brushManager.getSettings(player.getUniqueId());
        
        if (!settings.isEnabled()) {
            return;
        }

        if (!settings.hasHeightmap()) {
            plugin.getMessageUtil().sendError(player, "No heightmap selected!");
            return;
        }
        
        // Check server performance before starting
        String safetyCheck = plugin.getPerformanceMonitor().checkOperationSafety(settings.getSize() * settings.getSize() * settings.getMaxHeight());
        if (safetyCheck != null) {
            plugin.getMessageUtil().sendError(player, safetyCheck);
            return;
        }

        if (surfaceHeightCache.size() > CACHE_MAX_SIZE) {
            surfaceHeightCache.clear();
        }

        float playerYaw = player.getLocation().getYaw();
        
        // Calculate all block changes
        List<BlockChange> changes = calculateChanges(targetLocation, settings, playerYaw);
        
        if (changes.isEmpty()) {
            return;
        }
        
        World world = targetLocation.getWorld();
        if (world == null) return;
        
        // Decide between preview mode and direct placement.
        if (settings.isPreviewEnabled()) {
            showPreview(player, world, changes, settings.getBlock());
        } else {
            applyChangesDirectly(player, world, changes, settings.getBlock());
        }
    }
    
    /**
     * Shows preview by placing all blocks instantly.
     * Accumulates multiple brush strokes into the same preview.
     */
    private void showPreview(Player player, World world, List<BlockChange> changes, Material finalBlock) {
        UUID playerId = player.getUniqueId();
        
        // Get existing preview or create new one
        PreviewState state = playerPreviews.get(playerId);
        boolean isNewPreview = (state == null);
        
        if (isNewPreview) {
            state = new PreviewState(player, world, finalBlock);
            playerPreviews.put(playerId, state);
        } else {
            // Update final block in case it changed
            state.setFinalBlock(finalBlock);
        }
        
        // Place ALL new preview blocks instantly
        BlockData previewData = PREVIEW_BLOCK.createBlockData();
        int placed = 0;
        
        for (BlockChange change : changes) {
            Location loc = new Location(world, change.x, change.y, change.z);
            Block block = loc.getBlock();
            Material existing = block.getType();
            
            // Skip if already tracked in this preview
            if (state.previewLocations.containsKey(loc)) continue;
            
            boolean shouldChange = change.requiresSolid 
                ? !existing.isAir() && !block.isLiquid() && existing != PREVIEW_BLOCK
                : existing.isAir() || existing == PREVIEW_BLOCK || block.isLiquid();
            
            if (shouldChange) {
                // Save original only if not already tracked
                state.originalBlocks.putIfAbsent(loc.clone(), block.getBlockData().clone());
                
                if (change.requiresSolid) {
                    block.setType(Material.AIR, false);
                    state.previewLocations.put(loc.clone(), true);
                } else {
                    block.setBlockData(previewData, false);
                    state.previewLocations.put(loc.clone(), false);
                }
                placed++;
            }
        }
        
        state.totalPlaced += placed;
        
        // Reset conversion timer (extends the timeout with each new stroke)
        state.startConversionTimer();
        
        plugin.getMessageUtil().sendInfo(
                player,
                "Preview: +" + placed + " (total: " + state.totalPlaced + "). Auto-convert in " + PREVIEW_TIMEOUT_SECONDS + "s..."
        );
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
    }
    
    /**
     * Applies changes directly without preview.
     */
    private void applyChangesDirectly(Player player, World world, List<BlockChange> changes, Material block) {
        Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
        int changed = applyChanges(world, changes, originalBlocks, block);
        
        if (changed > 0) {
            plugin.getUndoManager().saveOperation(player, originalBlocks);
            plugin.getMessageUtil().sendInfo(player, "Terrain modified: " + changed + " blocks changed.");
        }
    }
    
    /**
     * Cancels any active preview for the player and restores original blocks.
     */
    public void cancelPreview(Player player) {
        PreviewState state = playerPreviews.remove(player.getUniqueId());
        if (state != null) {
            state.cancel();
        }
    }
    
    /**
     * Pauses the preview for the player (delays auto-conversion).
     */
    public boolean pausePreview(Player player) {
        PreviewState state = playerPreviews.get(player.getUniqueId());
        if (state != null && !state.isPaused()) {
            state.setPaused(true);
            return true;
        }
        return false;
    }
    
    /**
     * Resumes the preview for the player.
     */
    public boolean resumePreview(Player player) {
        PreviewState state = playerPreviews.get(player.getUniqueId());
        if (state != null && state.isPaused()) {
            state.setPaused(false);
            state.startConversionTimer();
            return true;
        }
        return false;
    }
    
    /**
     * Checks if player has an active preview.
     */
    public boolean hasActivePreview(Player player) {
        return playerPreviews.containsKey(player.getUniqueId());
    }
    
    /**
     * Preview state for a player - stores preview blocks for instant placement.
     */
    private class PreviewState {
        private final Player player;
        private final World world;
        private Material finalBlock;
        private final Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
        private final Map<Location, Boolean> previewLocations = new LinkedHashMap<>();
        private BukkitTask conversionTask;
        private boolean converted = false;
        private boolean paused = false;
        private int totalPlaced = 0;

        public PreviewState(Player player, World world, Material finalBlock) {
            this.player = player;
            this.world = world;
            this.finalBlock = finalBlock;
        }
        
        public void setFinalBlock(Material block) {
            this.finalBlock = block;
        }
        
        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { 
            this.paused = paused;
            if (paused && conversionTask != null) {
                conversionTask.cancel();
                conversionTask = null;
            }
        }
        
        public void startConversionTimer() {
            if (conversionTask != null) {
                conversionTask.cancel();
            }
            
            conversionTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || converted || paused) {
                        return;
                    }
                    convertToFinal();
                }
            }.runTaskLater(plugin, PREVIEW_TIMEOUT_SECONDS * 20L);
        }
        
        private void convertToFinal() {
            if (converted) return;
            converted = true;
            
            if (conversionTask != null) {
                conversionTask.cancel();
            }
            
            playerPreviews.remove(player.getUniqueId());
            
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            
            // Convert preview blocks to final
            BlockData finalData = finalBlock.createBlockData();
            int convertedCount = 0;
            
            for (Map.Entry<Location, Boolean> entry : previewLocations.entrySet()) {
                if (!entry.getValue()) { // Not air (LOWER mode)
                    Block block = entry.getKey().getBlock();
                    if (block.getType() == PREVIEW_BLOCK) {
                        block.setBlockData(finalData, false);
                        convertedCount++;
                    }
                } else {
                    convertedCount++; // Air blocks already done
                }
            }
            
            // Save for undo
            plugin.getUndoManager().saveOperation(player, new LinkedHashMap<>(originalBlocks));
            plugin.getMessageUtil().sendInfo(player, "Terrain generated: " + convertedCount + " blocks placed.");
        }
        
        public void cancel() {
            if (converted) return;
            converted = true;
            
            if (conversionTask != null) {
                conversionTask.cancel();
            }
            
            // Restore original blocks
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    loc.getBlock().setBlockData(entry.getValue(), false);
                }
            }
            
            plugin.getMessageUtil().sendInfo(player, "Preview cancelled and terrain restored.");
        }
    }

    /**
     * Builds a list of block changes without editing the world.
     *
     * <p>This method is intentionally "pure" (calculation only) so we can:
     * <ul>
     *   <li>preview changes safely</li>
     *   <li>measure performance before committing</li>
     *   <li>apply/undo operations consistently</li>
     * </ul>
     */
    private List<BlockChange> calculateChanges(Location center, BrushSettings settings, float playerYaw) {
        World world = center.getWorld();
        if (world == null) return Collections.emptyList();

        int size = settings.getSize();
        int maxHeight = settings.getMaxHeight();
        double intensity = settings.getIntensity() / 100.0;
        BufferedImage heightmap = settings.getHeightmapImage();
        BrushSettings.BrushMode mode = settings.getMode();
        boolean circular = settings.isCircularMask();
        boolean autoRotate = settings.isAutoRotation();

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        int imgW = heightmap.getWidth();
        int imgH = heightmap.getHeight();

        int extSize = size + EDGE_BLEND_RADIUS;
        int worldW = extSize * 2;

        double rotAngle = autoRotate ? Math.toRadians(-playerYaw) : 0;
        double cos = Math.cos(rotAngle);
        double sin = Math.sin(rotAngle);

        double[][] brushHeights = new double[worldW + 1][worldW + 1];
        int[][] surfaceHeights = new int[worldW + 1][worldW + 1];
        boolean[][] hasSolidGround = new boolean[worldW + 1][worldW + 1];
        
        // First pass: Calculate surface heights and check for solid ground
        for (int wx = 0; wx <= worldW; wx++) {
            for (int wz = 0; wz <= worldW; wz++) {
                int worldX = centerX - extSize + wx;
                int worldZ = centerZ - extSize + wz;
                
                surfaceHeights[wx][wz] = getCachedSurfaceHeight(world, worldX, centerY, worldZ);
                
                // Check if there's solid ground at this position
                Block surfaceBlock = world.getBlockAt(worldX, surfaceHeights[wx][wz], worldZ);
                hasSolidGround[wx][wz] = !surfaceBlock.getType().isAir() && !surfaceBlock.isLiquid();
            }
        }
        
        // Second pass: Calculate edge distance for each position (distance to nearest air/void)
        double[][] edgeFalloff = new double[worldW + 1][worldW + 1];
        for (int wx = 0; wx <= worldW; wx++) {
            for (int wz = 0; wz <= worldW; wz++) {
                if (!hasSolidGround[wx][wz]) {
                    edgeFalloff[wx][wz] = 0; // No ground = no generation
                } else {
                    // Find minimum distance to edge (position without solid ground)
                    int minDistToEdge = TERRAIN_EDGE_FALLOFF_RADIUS + 1;
                    
                    for (int dx = -TERRAIN_EDGE_FALLOFF_RADIUS; dx <= TERRAIN_EDGE_FALLOFF_RADIUS; dx++) {
                        for (int dz = -TERRAIN_EDGE_FALLOFF_RADIUS; dz <= TERRAIN_EDGE_FALLOFF_RADIUS; dz++) {
                            int nx = wx + dx;
                            int nz = wz + dz;
                            
                            // Check bounds
                            if (nx < 0 || nx > worldW || nz < 0 || nz > worldW) {
                                // Out of bounds = treat as edge
                                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                                minDistToEdge = Math.min(minDistToEdge, dist);
                            } else if (!hasSolidGround[nx][nz]) {
                                // Found air/void nearby
                                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                                minDistToEdge = Math.min(minDistToEdge, dist);
                            }
                        }
                    }
                    
                    // Calculate falloff based on distance to edge
                    if (minDistToEdge <= TERRAIN_EDGE_FALLOFF_RADIUS) {
                        // Smooth falloff using hermite interpolation
                        double t = (double) minDistToEdge / TERRAIN_EDGE_FALLOFF_RADIUS;
                        edgeFalloff[wx][wz] = t * t * (3 - 2 * t); // Smooth step
                    } else {
                        edgeFalloff[wx][wz] = 1.0; // Full height, far from edge
                    }
                }
            }
        }
        
        // Third pass: Calculate brush heights with edge falloff applied
        for (int wx = 0; wx <= worldW; wx++) {
            for (int wz = 0; wz <= worldW; wz++) {
                // Skip if no solid ground
                if (!hasSolidGround[wx][wz]) {
                    brushHeights[wx][wz] = 0;
                    continue;
                }
                
                double normX = (double) (wx - extSize) / size;
                double normZ = (double) (wz - extSize) / size;
                double dist = Math.sqrt(normX * normX + normZ * normZ);
                
                double maxDist = circular ? 1.0 + (EDGE_BLEND_RADIUS / (double) size) : 
                                           Math.sqrt(2) + (EDGE_BLEND_RADIUS / (double) size);
                
                if (dist > maxDist) {
                    brushHeights[wx][wz] = 0;
                    continue;
                }
                
                double rotX = autoRotate ? normX * cos - normZ * sin : normX;
                double rotZ = autoRotate ? normX * sin + normZ * cos : normZ;
                
                double imgX = (rotX + 1.0) / 2.0 * (imgW - 1);
                double imgZ = (rotZ + 1.0) / 2.0 * (imgH - 1);
                
                double heightVal = 0;
                if (imgX >= 0 && imgX < imgW && imgZ >= 0 && imgZ < imgH) {
                    heightVal = sampleBilinear(heightmap, imgX, imgZ);
                }
                
                // Brush circle falloff
                double effectiveMaxDist = circular ? (1.0 - MOUNTAIN_EDGE_MARGIN) : (Math.sqrt(2) - MOUNTAIN_EDGE_MARGIN);
                if (dist > effectiveMaxDist * 0.5) {
                    double falloffStart = effectiveMaxDist * 0.5;
                    double falloff = 1.0 - ((dist - falloffStart) / (effectiveMaxDist - falloffStart));
                    falloff = Math.max(0, Math.min(1, falloff));
                    falloff = falloff * falloff * (3 - 2 * falloff);
                    heightVal *= falloff;
                }
                
                if (dist > effectiveMaxDist) {
                    heightVal = 0;
                }
                
                // Apply terrain edge falloff - this creates the smooth slope at terrain borders
                heightVal *= edgeFalloff[wx][wz];
                
                brushHeights[wx][wz] = heightVal;
            }
        }

        // Smooth if enabled
        if (settings.isAutoSmooth()) {
            brushHeights = gaussianSmooth(brushHeights, settings.getSmoothStrength());
        }

        // Collect changes
        List<BlockChange> changes = new ArrayList<>();
        
        for (int wx = 0; wx <= worldW; wx++) {
            for (int wz = 0; wz <= worldW; wz++) {
                double heightVal = brushHeights[wx][wz];
                if (heightVal < 0.01) continue;
                
                int targetH = (int) Math.round(heightVal * maxHeight * intensity);
                if (targetH <= 0) continue;

                int worldX = centerX - extSize + wx;
                int worldZ = centerZ - extSize + wz;
                int surfaceY = surfaceHeights[wx][wz];

                // Only generate if there's solid ground
                if (hasSolidGround[wx][wz]) {
                    collectChanges(world, worldX, surfaceY, worldZ, targetH, mode, centerY, changes);
                }
            }
        }

        return changes;
    }

    private void collectChanges(World world, int x, int surfaceY, int z, int targetH,
                                BrushSettings.BrushMode mode, int centerY,
                                List<BlockChange> changes) {
        switch (mode) {
            case RAISE -> {
                for (int y = 1; y <= targetH; y++) {
                    int worldY = surfaceY + y;
                    if (worldY > world.getMaxHeight()) break;
                    changes.add(new BlockChange(x, worldY, z, false));
                }
            }
            case LOWER -> {
                for (int y = 0; y < targetH; y++) {
                    int worldY = surfaceY - y;
                    if (worldY < world.getMinHeight()) break;
                    changes.add(new BlockChange(x, worldY, z, true));
                }
            }
            case SMOOTH -> {
                int avgY = getAvgHeight(world, x, z, 2);
                int diff = avgY - surfaceY;
                if (diff > 0) {
                    int amt = Math.min(diff, targetH);
                    for (int y = surfaceY + 1; y <= surfaceY + amt; y++) {
                        changes.add(new BlockChange(x, y, z, false));
                    }
                } else if (diff < 0) {
                    int amt = Math.min(-diff, targetH);
                    for (int y = surfaceY; y > surfaceY - amt; y--) {
                        changes.add(new BlockChange(x, y, z, true));
                    }
                }
            }
            case FLATTEN -> {
                if (surfaceY < centerY) {
                    int amt = Math.min(centerY - surfaceY, targetH);
                    for (int y = surfaceY + 1; y <= surfaceY + amt; y++) {
                        changes.add(new BlockChange(x, y, z, false));
                    }
                } else if (surfaceY > centerY) {
                    int amt = Math.min(surfaceY - centerY, targetH);
                    for (int y = surfaceY; y > surfaceY - amt; y--) {
                        changes.add(new BlockChange(x, y, z, true));
                    }
                }
            }
        }
    }

    private int applyChanges(World world, List<BlockChange> changes,
                             Map<Location, BlockData> originalBlocks, Material block) {
        int changed = 0;
        BlockData blockData = block.createBlockData();
        
        for (BlockChange change : changes) {
            Location loc = new Location(world, change.x, change.y, change.z);
            Block blk = loc.getBlock();
            Material existing = blk.getType();
            
            boolean shouldChange = change.requiresSolid 
                ? !existing.isAir() && !blk.isLiquid()
                : existing.isAir() || blk.isLiquid();
            
            if (shouldChange) {
                originalBlocks.putIfAbsent(loc.clone(), blk.getBlockData().clone());
                if (change.requiresSolid) {
                    blk.setType(Material.AIR, false);
                } else {
                    blk.setBlockData(blockData, false);
                }
                changed++;
            }
        }
        
        return changed;
    }

    private double sampleBilinear(BufferedImage img, double x, double y) {
        int w = img.getWidth(), h = img.getHeight();
        int x0 = Math.max(0, Math.min((int) x, w - 1));
        int y0 = Math.max(0, Math.min((int) y, h - 1));
        int x1 = Math.min(x0 + 1, w - 1);
        int y1 = Math.min(y0 + 1, h - 1);
        
        double fx = x - Math.floor(x);
        double fy = y - Math.floor(y);
        
        double v00 = getBrightness(img, x0, y0);
        double v10 = getBrightness(img, x1, y0);
        double v01 = getBrightness(img, x0, y1);
        double v11 = getBrightness(img, x1, y1);
        
        return (v00 * (1-fx) + v10 * fx) * (1-fy) + (v01 * (1-fx) + v11 * fx) * fy;
    }

    private double getBrightness(BufferedImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / (3.0 * 255.0);
    }

    private double[][] gaussianSmooth(double[][] heights, int strength) {
        int w = heights.length, d = heights[0].length;
        double[][] result = new double[w][d];
        int ks = strength * 2 + 1;
        double[][] kernel = createKernel(ks);
        int r = ks / 2;
        
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                double sum = 0, wSum = 0;
                for (int kx = -r; kx <= r; kx++) {
                    for (int kz = -r; kz <= r; kz++) {
                        int nx = x + kx, nz = z + kz;
                        if (nx >= 0 && nx < w && nz >= 0 && nz < d) {
                            double wt = kernel[kx + r][kz + r];
                            sum += heights[nx][nz] * wt;
                            wSum += wt;
                        }
                    }
                }
                result[x][z] = wSum > 0 ? sum / wSum : heights[x][z];
            }
        }
        return result;
    }

    private double[][] createKernel(int size) {
        double[][] k = new double[size][size];
        double sigma = size / 4.0, sum = 0;
        int c = size / 2;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                double dx = x - c, dy = y - c;
                k[x][y] = Math.exp(-(dx*dx + dy*dy) / (2 * sigma * sigma));
                sum += k[x][y];
            }
        }
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                k[x][y] /= sum;
            }
        }
        return k;
    }

    private int getCachedSurfaceHeight(World world, int x, int startY, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        return surfaceHeightCache.computeIfAbsent(key, k -> findSurface(world, x, startY, z));
    }

    private int findSurface(World world, int x, int startY, int z) {
        for (int y = startY; y < world.getMaxHeight(); y++) {
            Block b = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (!b.getType().isAir() && !b.isLiquid() && (above.getType().isAir() || above.isLiquid())) {
                return y;
            }
        }
        for (int y = startY; y >= world.getMinHeight(); y--) {
            Block b = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (!b.getType().isAir() && !b.isLiquid() && (above.getType().isAir() || above.isLiquid())) {
                return y;
            }
        }
        return startY;
    }

    private int getAvgHeight(World world, int cx, int cz, int r) {
        int total = 0, count = 0;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                total += world.getHighestBlockYAt(cx + x, cz + z);
                count++;
            }
        }
        return count > 0 ? total / count : 64;
    }

    public void clearCache() { surfaceHeightCache.clear(); }

    private record BlockChange(int x, int y, int z, boolean requiresSolid) {}
}
