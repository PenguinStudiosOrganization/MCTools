package com.mctools.utils;

import com.mctools.MCTools;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Handles block placement with preview, animation, and performance-aware generation.
 * 
 * Features:
 * - Preview mode with configurable glass blocks
 * - Teleports player above preview to avoid getting stuck
 * - Adaptive speed based on server TPS and RAM
 * - Auto-cancellation if server is under heavy load
 * 
 * @author MCTools Team
 * @version 2.1.0
 */
public class BlockPlacer {

    private final MCTools plugin;
    private final Map<UUID, PlacementTask> activeTasks;
    private final Map<UUID, PreviewTask> previewTasks;

    public BlockPlacer(MCTools plugin) {
        this.plugin = plugin;
        this.activeTasks = new HashMap<>();
        this.previewTasks = new HashMap<>();
    }

    /**
     * Places blocks with different materials (for trees and other multi-block structures).
     */
    public void placeMultiBlocks(Player player, Map<Location, Material> blocks, String shapeName) {
        Map<Location, BlockData> blockDataMap = new LinkedHashMap<>();
        for (Map.Entry<Location, Material> entry : blocks.entrySet()) {
            blockDataMap.put(entry.getKey(), entry.getValue().createBlockData());
        }
        placeGradientBlocks(player, blockDataMap, shapeName);
    }

    /**
     * Places gradient blocks (different block type per position) with optional preview.
     */
    public void placeGradientBlocks(Player player, Map<Location, BlockData> blockMap, String shapeName) {
        if (blockMap.isEmpty()) {
            plugin.getMessageUtil().sendError(player, "No blocks to place!");
            return;
        }

        List<Location> blocks = new ArrayList<>(blockMap.keySet());

        String safetyCheck = plugin.getPerformanceMonitor().checkOperationSafety(blocks.size());
        if (safetyCheck != null) {
            plugin.getMessageUtil().sendError(player, safetyCheck);
            playErrorEffects(player);
            return;
        }

        cancelTask(player);
        cancelPreview(player);

        Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
        for (Location loc : blocks) {
            if (loc.getWorld() != null) {
                originalBlocks.put(loc.clone(), loc.getBlock().getBlockData().clone());
            }
        }

        teleportAboveStructure(player, blocks);

        ConfigManager config = plugin.getConfigManager();
        if (config.isPreviewEnabled()) {
            PreviewTask previewTask = new PreviewTask(player, blocks, null, originalBlocks, shapeName, blockMap);
            previewTasks.put(player.getUniqueId(), previewTask);
            previewTask.start();
        } else {
            startPlacement(player, blocks, null, originalBlocks, shapeName, blockMap);
        }
    }

    /**
     * Places blocks with optional preview animation.
     * Teleports player above the structure to avoid getting stuck.
     */
    public void placeBlocks(Player player, List<Location> blocks, BlockData blockData, String shapeName) {
        if (blocks.isEmpty()) {
            plugin.getMessageUtil().sendError(player, "No blocks to place!");
            return;
        }

        // Check if operation is safe to start
        String safetyCheck = plugin.getPerformanceMonitor().checkOperationSafety(blocks.size());
        if (safetyCheck != null) {
            plugin.getMessageUtil().sendError(player, safetyCheck);
            playErrorEffects(player);
            return;
        }

        cancelTask(player);
        cancelPreview(player);

        // Store original blocks for undo
        Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
        for (Location loc : blocks) {
            if (loc.getWorld() != null) {
                originalBlocks.put(loc.clone(), loc.getBlock().getBlockData().clone());
            }
        }

        ConfigManager config = plugin.getConfigManager();

        // Calculate highest point and teleport player above it
        teleportAboveStructure(player, blocks);

        if (config.isPreviewEnabled()) {
            PreviewTask previewTask = new PreviewTask(player, blocks, blockData, originalBlocks, shapeName);
            previewTasks.put(player.getUniqueId(), previewTask);
            previewTask.start();
        } else {
            startPlacement(player, blocks, blockData, originalBlocks, shapeName);
        }
    }

    /**
     * Teleports the player above the structure to avoid getting stuck inside blocks.
     */
    private void teleportAboveStructure(Player player, List<Location> blocks) {
        if (blocks.isEmpty()) return;
        
        int maxY = Integer.MIN_VALUE;
        double avgX = 0, avgZ = 0;
        
        for (Location loc : blocks) {
            if (loc.getBlockY() > maxY) {
                maxY = loc.getBlockY();
            }
            avgX += loc.getX();
            avgZ += loc.getZ();
        }
        
        avgX /= blocks.size();
        avgZ /= blocks.size();
        
        Location teleportLoc = new Location(
            player.getWorld(),
            avgX,
            maxY + 3,
            avgZ,
            player.getLocation().getYaw(),
            player.getLocation().getPitch()
        );
        
        while (!teleportLoc.getBlock().getType().isAir() && teleportLoc.getY() < player.getWorld().getMaxHeight()) {
            teleportLoc.add(0, 1, 0);
        }
        
        player.teleport(teleportLoc);
        plugin.getMessageUtil().sendInfo(player, "Teleported above structure.");
    }

    /**
     * Starts the actual block placement (called after preview or directly).
     */
    private void startPlacement(Player player, List<Location> blocks, BlockData blockData,
                                Map<Location, BlockData> originalBlocks, String shapeName) {
        startPlacement(player, blocks, blockData, originalBlocks, shapeName, null);
    }

    private void startPlacement(Player player, List<Location> blocks, BlockData blockData,
                                Map<Location, BlockData> originalBlocks, String shapeName,
                                Map<Location, BlockData> gradientMap) {

        PerformanceMonitor perfMon = plugin.getPerformanceMonitor();

        // Professional start message
        plugin.getMessageUtil().sendInfo(player, "<gradient:#10b981:#059669>▶ Starting generation...</gradient>");
        plugin.getMessageUtil().sendInfo(player, "<dark_gray>┃</dark_gray> <gray>Blocks:</gray> <white>" + formatNumber(blocks.size()) + "</white> <dark_gray>│</dark_gray> " + perfMon.getCompactStatusMiniMessage());

        // Create and start the placement task
        PlacementTask task = new PlacementTask(player, blocks, blockData, originalBlocks, shapeName, gradientMap);
        activeTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Formats a number with thousands separator.
     */
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }
    
    /**
     * Formats time in a human-readable way.
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        } else {
            return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        }
    }

    public boolean cancelTask(Player player) {
        PlacementTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancelAndRestore();
            return true;
        }
        return false;
    }

    public boolean cancelPreview(Player player) {
        PreviewTask preview = previewTasks.remove(player.getUniqueId());
        if (preview != null) {
            preview.cancel();
            return true;
        }
        return false;
    }

    public boolean hasActiveTask(Player player) {
        return activeTasks.containsKey(player.getUniqueId()) || 
               previewTasks.containsKey(player.getUniqueId());
    }
    
    public boolean pauseTask(Player player) {
        PlacementTask task = activeTasks.get(player.getUniqueId());
        if (task != null) {
            task.setPaused(true);
            return true;
        }
        PreviewTask preview = previewTasks.get(player.getUniqueId());
        if (preview != null) {
            preview.setPaused(true);
            return true;
        }
        return false;
    }
    
    public boolean resumeTask(Player player) {
        PlacementTask task = activeTasks.get(player.getUniqueId());
        if (task != null && task.isPaused()) {
            task.setPaused(false);
            return true;
        }
        PreviewTask preview = previewTasks.get(player.getUniqueId());
        if (preview != null && preview.isPaused()) {
            preview.setPaused(false);
            return true;
        }
        return false;
    }

    private void playCompletionEffects(Player player) {
        ConfigManager config = plugin.getConfigManager();

        if (config.isSoundsEnabled()) {
            try {
                Sound sound = Sound.valueOf(config.getCompleteSound());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {}
        }

        if (config.isParticlesEnabled()) {
            try {
                Particle particle = Particle.valueOf(config.getParticleType());
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
            } catch (IllegalArgumentException e) {}
        }
    }

    public void playErrorEffects(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (config.isSoundsEnabled()) {
            try {
                Sound sound = Sound.valueOf(config.getErrorSound());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {}
        }
    }

    /**
     * Preview task - shows preview blocks for configured duration.
     */
    private class PreviewTask {
        private final Player player;
        private final List<Location> blocks;
        private final BlockData finalBlockData;
        private final Map<Location, BlockData> originalBlocks;
        private final String shapeName;
        private final Map<Location, BlockData> gradientMap;
        private BukkitTask countdownTask;
        private BukkitTask previewPlacementTask;
        private int secondsRemaining;
        private boolean cancelled = false;
        private boolean generationStarted = false;
        private boolean paused = false;

        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { this.paused = paused; }

        public PreviewTask(Player player, List<Location> blocks, BlockData finalBlockData,
                          Map<Location, BlockData> originalBlocks, String shapeName) {
            this(player, blocks, finalBlockData, originalBlocks, shapeName, null);
        }

        public PreviewTask(Player player, List<Location> blocks, BlockData finalBlockData,
                          Map<Location, BlockData> originalBlocks, String shapeName,
                          Map<Location, BlockData> gradientMap) {
            this.player = player;
            this.blocks = blocks;
            this.finalBlockData = finalBlockData;
            this.originalBlocks = originalBlocks;
            this.shapeName = shapeName;
            this.gradientMap = gradientMap;
            this.secondsRemaining = plugin.getConfigManager().getPreviewDuration();
        }

        public void start() {
            ConfigManager config = plugin.getConfigManager();
            Material previewMaterial = config.getPreviewBlock();
            
            plugin.getMessageUtil().sendInfo(player, "Showing preview. Generation in <#10b981>" + secondsRemaining + "</#10b981>s...");
            plugin.getMessageUtil().sendInfo(player, "Use <#10b981>/mct cancel</#10b981> to abort.");

            BlockData previewData = previewMaterial.createBlockData();
            
            // Place preview blocks quickly
            previewPlacementTask = new BukkitRunnable() {
                int index = 0;

                @Override
                public void run() {
                    if (cancelled || !player.isOnline()) {
                        cancel();
                        return;
                    }

                    // Fast preview: complete in ~10 ticks max
                    int blocksPerTick = Math.max(100, (int) Math.ceil(blocks.size() / 10.0));

                    int endIndex = Math.min(index + blocksPerTick, blocks.size());
                    for (int i = index; i < endIndex; i++) {
                        Location loc = blocks.get(i);
                        if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                            loc.getBlock().setBlockData(previewData, false);
                        }
                    }
                    index = endIndex;

                    if (index >= blocks.size()) {
                        cancel();
                        if (config.isSoundsEnabled()) {
                            try {
                                Sound sound = Sound.valueOf(config.getPreviewSound());
                                player.playSound(player.getLocation(), sound, 0.5f, 1.5f);
                            } catch (IllegalArgumentException e) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
                            }
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);

            // Countdown timer
            countdownTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancelPreviewAndRestore();
                        return;
                    }
                    
                    if (cancelled) {
                        cancel();
                        return;
                    }
                    
                    // Check server performance during preview
                    if (plugin.getPerformanceMonitor().shouldCancelOperation()) {
                        plugin.getMessageUtil().sendError(player, "<red>⚠ Operation cancelled due to server overload!</red>");
                        plugin.getMessageUtil().sendInfo(player, plugin.getPerformanceMonitor().getPerformanceStatusMiniMessage());
                        cancelPreviewAndRestore();
                        return;
                    }

                    secondsRemaining--;

                    if (secondsRemaining <= 0) {
                        cancel();
                        if (previewPlacementTask != null) previewPlacementTask.cancel();
                        previewTasks.remove(player.getUniqueId());
                        startGenerationPhase();
                    } else if (secondsRemaining <= 3) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                        plugin.getMessageUtil().sendInfo(player, "Generating in <#10b981>" + secondsRemaining + "</#10b981>...");
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }
        
        private void cancelPreviewAndRestore() {
            cancel();
            previewTasks.remove(player.getUniqueId());
        }

        private void startGenerationPhase() {
            generationStarted = true;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

            // Start the actual placement
            startPlacement(player, blocks, finalBlockData, originalBlocks, shapeName, gradientMap);
        }

        private void restoreOriginal() {
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    loc.getBlock().setBlockData(entry.getValue(), false);
                }
            }
        }

        public void cancel() {
            if (generationStarted) {
                cancelled = true;
                if (countdownTask != null) countdownTask.cancel();
                if (previewPlacementTask != null) previewPlacementTask.cancel();
                return;
            }
            cancelled = true;
            if (countdownTask != null) countdownTask.cancel();
            if (previewPlacementTask != null) previewPlacementTask.cancel();
            restoreOriginal();
        }
    }

    /**
     * Task for adaptive block placement based on server performance.
     */
    private class PlacementTask extends BukkitRunnable {
        private final Player player;
        private final List<Location> blocks;
        private final BlockData blockData;
        private final Map<Location, BlockData> originalBlocks;
        private final String shapeName;
        private final Map<Location, BlockData> gradientMap;

        private int currentIndex = 0;
        private int lastProgressPercent = -1;
        private boolean paused = false;
        private long startTime;
        private long lastStatusUpdate = 0;
        private int totalBlocksPlaced = 0;

        public PlacementTask(Player player, List<Location> blocks, BlockData blockData,
                           Map<Location, BlockData> originalBlocks, String shapeName) {
            this(player, blocks, blockData, originalBlocks, shapeName, null);
        }

        public PlacementTask(Player player, List<Location> blocks, BlockData blockData,
                           Map<Location, BlockData> originalBlocks, String shapeName,
                           Map<Location, BlockData> gradientMap) {
            this.player = player;
            this.blocks = new ArrayList<>(blocks);
            this.blockData = blockData;
            this.originalBlocks = originalBlocks;
            this.shapeName = shapeName;
            this.gradientMap = gradientMap;
            this.startTime = System.currentTimeMillis();

            // Sort blocks by distance from player for nice animation
            Location center = player.getLocation();
            this.blocks.sort((a, b) -> {
                double distA = a.distanceSquared(center);
                double distB = b.distanceSquared(center);
                return Double.compare(distA, distB);
            });
        }
        
        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { this.paused = paused; }
        
        public void cancelAndRestore() {
            cancel();
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    loc.getBlock().setBlockData(entry.getValue(), false);
                }
            }
        }

        @Override
        public void run() {
            try {
                // Check if player is online
                if (!player.isOnline()) {
                    cancel();
                    activeTasks.remove(player.getUniqueId());
                    return;
                }
                
                // Skip if paused
                if (paused) return;
                
                // Check for emergency - cancel operation
                PerformanceMonitor perfMon = plugin.getPerformanceMonitor();
                if (perfMon.shouldCancelOperation()) {
                    plugin.getMessageUtil().sendError(player, "<dark_red>⚠ EMERGENCY: Operation cancelled due to server overload!</dark_red>");
                    plugin.getMessageUtil().sendInfo(player, perfMon.getPerformanceStatusMiniMessage());
                    cancelAndRestore();
                    activeTasks.remove(player.getUniqueId());
                    playErrorEffects(player);
                    return;
                }

                // Get adaptive blocks per tick from performance monitor
                int blocksPerTick = perfMon.calculateBlocksPerTick();
                
                // If server is struggling, skip this tick entirely
                if (blocksPerTick == 0) {
                    return;
                }

                // Place blocks
                int endIndex = Math.min(currentIndex + blocksPerTick, blocks.size());
                int blocksThisTick = 0;
                
                for (int i = currentIndex; i < endIndex; i++) {
                    Location loc = blocks.get(i);
                    if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                        Block block = loc.getBlock();
                        BlockData data = gradientMap != null ? gradientMap.get(loc) : blockData;
                        if (data != null) {
                            block.setBlockData(data, false);
                        }
                        blocksThisTick++;
                    }
                }

                currentIndex = endIndex;
                totalBlocksPlaced += blocksThisTick;

                // Update progress every 3 seconds
                long now = System.currentTimeMillis();
                if (now - lastStatusUpdate >= 3000) {
                    lastStatusUpdate = now;
                    
                    int percent = (currentIndex * 100) / blocks.size();
                    long elapsed = (now - startTime) / 1000;
                    int blocksPerSecond = elapsed > 0 ? (int) (totalBlocksPlaced / elapsed) : 0;
                    int remaining = blocks.size() - currentIndex;
                    int etaSeconds = blocksPerSecond > 0 ? remaining / blocksPerSecond : 0;
                    
                    // Build progress bar
                    int barLength = 20;
                    int filled = (percent * barLength) / 100;
                    StringBuilder bar = new StringBuilder();
                    for (int i = 0; i < barLength; i++) {
                        if (i < filled) {
                            bar.append("<green>█</green>");
                        } else {
                            bar.append("<dark_gray>░</dark_gray>");
                        }
                    }
                    
                    // Professional progress message
                    plugin.getMessageUtil().sendInfo(player, 
                        "<dark_gray>┃</dark_gray> " + bar.toString() + " <white>" + percent + "%</white>");
                    plugin.getMessageUtil().sendInfo(player, 
                        "<dark_gray>┃</dark_gray> <gray>Progress:</gray> <white>" + formatNumber(currentIndex) + "</white><gray>/</gray><white>" + formatNumber(blocks.size()) + "</white>" +
                        " <dark_gray>│</dark_gray> <gray>Speed:</gray> <yellow>" + formatNumber(blocksPerSecond) + "</yellow><gray>/s</gray>" +
                        " <dark_gray>│</dark_gray> <gray>ETA:</gray> <white>" + formatTime(etaSeconds) + "</white>");
                    plugin.getMessageUtil().sendInfo(player, perfMon.getDetailedStatusMiniMessage());
                }

                // Show progress bar at milestones
                if (plugin.getConfigManager().isShowProgress()) {
                    int percent = (currentIndex * 100) / blocks.size();
                    if (percent != lastProgressPercent && percent % 10 == 0) {
                        lastProgressPercent = percent;
                        int progress = percent / 5; // 0-20 scale
                        plugin.getMessageUtil().sendProgress(player, progress, percent);
                    }
                }

                // Check if complete
                if (currentIndex >= blocks.size()) {
                    cancel();
                    activeTasks.remove(player.getUniqueId());
                    
                    long totalTime = (System.currentTimeMillis() - startTime) / 1000;
                    long avgSpeed = totalTime > 0 ? blocks.size() / totalTime : blocks.size();
                    
                    plugin.getUndoManager().saveOperation(player, originalBlocks);
                    
                    // Professional completion message
                    plugin.getMessageUtil().sendInfo(player, "<gradient:#10b981:#059669>✓ Generation Complete!</gradient>");
                    plugin.getMessageUtil().sendInfo(player, 
                        "<dark_gray>┃</dark_gray> <gray>Shape:</gray> <white>" + shapeName + "</white>" +
                        " <dark_gray>│</dark_gray> <gray>Blocks:</gray> <white>" + formatNumber(blocks.size()) + "</white>");
                    plugin.getMessageUtil().sendInfo(player, 
                        "<dark_gray>┃</dark_gray> <gray>Time:</gray> <white>" + formatTime(totalTime) + "</white>" +
                        " <dark_gray>│</dark_gray> <gray>Avg Speed:</gray> <yellow>" + formatNumber((int)avgSpeed) + "</yellow><gray>/s</gray>");
                    
                    playCompletionEffects(player);
                }
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error in PlacementTask: " + e.getMessage());
                e.printStackTrace();
                cancel();
                activeTasks.remove(player.getUniqueId());
                plugin.getMessageUtil().sendError(player, "<red>Error during placement: " + e.getMessage() + "</red>");
            }
        }
    }
}
