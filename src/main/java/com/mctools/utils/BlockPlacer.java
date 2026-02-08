package com.mctools.utils;

import com.mctools.MCTools;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EditSessionBuilder;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Block placement engine for MCTools.
 *
 * <p>When WorldEdit/FAWE is present, block edits are executed through WorldEdit
 * {@link EditSession} so they are recorded in WorldEdit per-player history.
 * This enables true universal undo via {@code //undo}.</p>
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

    @SuppressWarnings("unused")
    public void placeMultiBlocks(Player player, Map<Location, Material> blocks, String shapeName) {
        Map<Location, BlockData> blockDataMap = new LinkedHashMap<>();
        for (Map.Entry<Location, Material> entry : blocks.entrySet()) {
            blockDataMap.put(entry.getKey(), entry.getValue().createBlockData());
        }
        placeGradientBlocks(player, blockDataMap, shapeName);
    }

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

        Map<Location, BlockData> originalBlocks = captureOriginal(blocks);

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

    public void placeBlocks(Player player, List<Location> blocks, BlockData blockData, String shapeName) {
        if (blocks.isEmpty()) {
            plugin.getMessageUtil().sendError(player, "No blocks to place!");
            return;
        }

        String safetyCheck = plugin.getPerformanceMonitor().checkOperationSafety(blocks.size());
        if (safetyCheck != null) {
            plugin.getMessageUtil().sendError(player, safetyCheck);
            playErrorEffects(player);
            return;
        }

        cancelTask(player);
        cancelPreview(player);

        Map<Location, BlockData> originalBlocks = captureOriginal(blocks);

        teleportAboveStructure(player, blocks);

        ConfigManager config = plugin.getConfigManager();
        if (config.isPreviewEnabled()) {
            PreviewTask previewTask = new PreviewTask(player, blocks, blockData, originalBlocks, shapeName);
            previewTasks.put(player.getUniqueId(), previewTask);
            previewTask.start();
        } else {
            startPlacement(player, blocks, blockData, originalBlocks, shapeName);
        }
    }

    private Map<Location, BlockData> captureOriginal(List<Location> blocks) {
        Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
        for (Location loc : blocks) {
            if (loc.getWorld() != null) {
                originalBlocks.put(loc.clone(), loc.getBlock().getBlockData().clone());
            }
        }
        return originalBlocks;
    }

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
        plugin.getMessageUtil().sendInfo(player, "Teleported above the structure.");
    }

    private void startPlacement(Player player, List<Location> blocks, BlockData blockData,
                                Map<Location, BlockData> originalBlocks, String shapeName) {
        startPlacement(player, blocks, blockData, originalBlocks, shapeName, null);
    }

    private void startPlacement(Player player, List<Location> blocks, BlockData blockData,
                                Map<Location, BlockData> originalBlocks, String shapeName,
                                Map<Location, BlockData> gradientMap) {

        plugin.getMessageUtil().sendInfo(player, "Generating " + formatNumber(blocks.size()) + " blocks...");

        PlacementTask task = new PlacementTask(player, blocks, blockData, originalBlocks, shapeName, gradientMap);
        activeTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

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
        return activeTasks.containsKey(player.getUniqueId()) || previewTasks.containsKey(player.getUniqueId());
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
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (config.isParticlesEnabled()) {
            try {
                Particle particle = Particle.valueOf(config.getParticleType());
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void playErrorEffects(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (config.isSoundsEnabled()) {
            try {
                Sound sound = Sound.valueOf(config.getErrorSound());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private boolean isWorldEditAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    }

    private EditSession openEditSession(Player player, int expectedChanges) {
        // Ensure history is recorded for this player
        BukkitPlayer wePlayer = BukkitAdapter.adapt(player);
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());

        EditSessionBuilder builder = WorldEdit.getInstance().newEditSessionBuilder();
        return builder
            .world(weWorld)
            .actor(wePlayer)
            // Let WorldEdit enforce its own limits (max-changes). We also have plugin-side max-blocks.
            .maxBlocks(expectedChanges)
            .build();
    }

    private BlockState toWEBlockState(BlockData data) {
        return BukkitAdapter.adapt(data);
    }

    /**
     * Preview task (Bukkit-side glass preview). Not recorded in WorldEdit history.
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
        private int initialSeconds;
        private boolean cancelled = false;
        private boolean generationStarted = false;
        private boolean paused = false;
        private BossBar previewBossBar;

        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { 
            this.paused = paused;
            if (paused) {
                updateBossbarPaused();
            }
        }

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
            this.initialSeconds = this.secondsRemaining;
            createBossbar();
        }
        
        private void createBossbar() {
            if (!plugin.getConfigManager().isEtaBossbarEnabled()) return;
            previewBossBar = Bukkit.createBossBar(
                "§b§l⏳ §f" + shapeName + " §7│ §a" + secondsRemaining + "s §7│ §e" + formatNumber(blocks.size()) + " blocks",
                BarColor.BLUE,
                BarStyle.SEGMENTED_10
            );
            previewBossBar.setProgress(1.0);
            previewBossBar.addPlayer(player);
            previewBossBar.setVisible(true);
        }
        
        private void updateBossbar() {
            if (previewBossBar == null) return;
            double progress = Math.max(0.0, (double) secondsRemaining / initialSeconds);
            previewBossBar.setProgress(progress);
            
            // Dynamic icon based on time remaining
            String icon;
            if (secondsRemaining <= 2) {
                icon = "§c§l⚡";
                previewBossBar.setColor(BarColor.RED);
            } else if (secondsRemaining <= 3) {
                icon = "§6§l⏰";
                previewBossBar.setColor(BarColor.YELLOW);
            } else {
                icon = "§b§l⏳";
                previewBossBar.setColor(BarColor.BLUE);
            }
            
            previewBossBar.setTitle(icon + " §f" + shapeName + " §7│ §a" + secondsRemaining + "s §7│ §e" + formatNumber(blocks.size()) + " blocks");
        }
        
        private void updateBossbarPaused() {
            if (previewBossBar == null) return;
            previewBossBar.setTitle("§d§l⏸ §fPAUSED §7│ §f" + shapeName + " §7│ §e" + formatNumber(blocks.size()) + " blocks");
            previewBossBar.setColor(BarColor.PURPLE);
        }
        
        private void removeBossbar() {
            if (previewBossBar != null) {
                previewBossBar.removeAll();
                previewBossBar = null;
            }
        }

        public void start() {
            ConfigManager config = plugin.getConfigManager();
            Material previewMaterial = config.getPreviewBlock();

            plugin.getMessageUtil().sendInfo(player, "Showing preview. Generation in " + secondsRemaining + "s...");
            plugin.getMessageUtil().sendInfo(player, "Use /mct cancel to abort.");

            BlockData previewData = previewMaterial.createBlockData();

            previewPlacementTask = new BukkitRunnable() {
                int index = 0;

                @Override
                public void run() {
                    if (cancelled || !player.isOnline()) {
                        cancel();
                        return;
                    }

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
                    
                    if (paused) {
                        return;
                    }

                    if (plugin.getPerformanceMonitor().shouldCancelOperation()) {
                        plugin.getMessageUtil().sendError(player, "Operation cancelled due to server overload.");
                        plugin.getMessageUtil().sendInfo(player, plugin.getPerformanceMonitor().getPerformanceStatusMiniMessage());
                        cancelPreviewAndRestore();
                        return;
                    }

                    secondsRemaining--;
                    updateBossbar();

                    if (secondsRemaining <= 0) {
                        cancel();
                        if (previewPlacementTask != null) previewPlacementTask.cancel();
                        removeBossbar();
                        previewTasks.remove(player.getUniqueId());
                        startGenerationPhase();
                    } else if (secondsRemaining <= 3) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                        plugin.getMessageUtil().sendInfo(player, "Generating in " + secondsRemaining + "...");
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }

        private void cancelPreviewAndRestore() {
            removeBossbar();
            cancel();
            previewTasks.remove(player.getUniqueId());
        }

        private void startGenerationPhase() {
            generationStarted = true;
            
            // Keep preview blocks visible during generation - they will be gradually
            // replaced by the actual blocks as placement progresses.
            // The originalBlocks map already captured the state BEFORE preview,
            // so WorldEdit undo will still work correctly.
            
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
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
                removeBossbar();
                return;
            }
            cancelled = true;
            if (countdownTask != null) countdownTask.cancel();
            if (previewPlacementTask != null) previewPlacementTask.cancel();
            removeBossbar();
            restoreOriginal();
        }
    }

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
        private final long startTime;
        private long lastStatusUpdate = 0;
        private int totalBlocksPlaced = 0;

        private BossBar etaBossBar;
        private long lastBossbarUpdate = 0;
        private int lastBossbarBlocksPlaced = 0;
        private long lastBossbarSampleTime = 0;
        private double smoothedBps = 0.0;

        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        // Single EditSession for the entire operation (for proper //undo support)
        private EditSession editSession;
        private boolean editSessionClosed = false;

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

            for (Location loc : this.blocks) {
                minX = Math.min(minX, loc.getBlockX());
                minY = Math.min(minY, loc.getBlockY());
                minZ = Math.min(minZ, loc.getBlockZ());
                maxX = Math.max(maxX, loc.getBlockX());
                maxY = Math.max(maxY, loc.getBlockY());
                maxZ = Math.max(maxZ, loc.getBlockZ());
            }

            Location center = player.getLocation();
            this.blocks.sort(Comparator.comparingDouble(a -> a.distanceSquared(center)));

            // Initialize single EditSession for WorldEdit/FAWE integration
            if (isWorldEditAvailable()) {
                try {
                    this.editSession = openEditSession(player, this.blocks.size());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to create WorldEdit EditSession: " + e.getMessage());
                    this.editSession = null;
                }
            }

            createBossbar();
        }

        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { this.paused = paused; }

        public void cancelAndRestore() {
            cancel();
            removeBossbar();

            // Restore using Bukkit directly (cancel is not an edit operation)
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
                if (!player.isOnline()) {
                    cancel();
                    removeBossbar();
                    activeTasks.remove(player.getUniqueId());
                    return;
                }

                if (paused) return;

                PerformanceMonitor perfMon = plugin.getPerformanceMonitor();
                if (perfMon.shouldCancelOperation()) {
                    plugin.getMessageUtil().sendError(player, "Emergency: operation cancelled due to server overload.");
                    plugin.getMessageUtil().sendInfo(player, perfMon.getPerformanceStatusMiniMessage());
                    cancelAndRestore();
                    activeTasks.remove(player.getUniqueId());
                    playErrorEffects(player);
                    return;
                }

                int blocksPerTick = perfMon.calculateBlocksPerTick();
                if (blocksPerTick == 0) {
                    updateBossbar(System.currentTimeMillis());
                    return;
                }

                int endIndex = Math.min(currentIndex + blocksPerTick, blocks.size());

                // If WorldEdit is present, apply through EditSession for history
                if (isWorldEditAvailable()) {
                    applyWithWorldEdit(currentIndex, endIndex);
                } else {
                    applyWithBukkit(currentIndex, endIndex);
                }

                int placedThisTick = endIndex - currentIndex;
                currentIndex = endIndex;
                totalBlocksPlaced += placedThisTick;

                long now = System.currentTimeMillis();
                // Progress updates every 5 seconds
                if (now - lastStatusUpdate >= 5000 && blocks.size() > 1000) {
                    lastStatusUpdate = now;
                    int percent = (currentIndex * 100) / blocks.size();
                    long elapsed = (now - startTime) / 1000;
                    int blocksPerSecond = elapsed > 0 ? (int) (totalBlocksPlaced / elapsed) : 0;

                    String progressColor = percent >= 75 ? "green" : (percent >= 40 ? "yellow" : "aqua");
                    String bpsColor = blocksPerSecond >= 5000 ? "green" : (blocksPerSecond >= 1000 ? "yellow" : "gold");

                    plugin.getMessageUtil().sendRaw(player,
                        "<" + progressColor + ">" + percent + "%</" + progressColor + "> <gray>complete</gray>" +
                        " <dark_gray>│</dark_gray> <" + bpsColor + ">" + formatNumber(blocksPerSecond) + "</" + bpsColor + "> <gray>blocks/s</gray>" +
                        " <dark_gray>│</dark_gray> " + plugin.getPerformanceMonitor().getFullPerformanceSummaryMiniMessage());
                }

                updateBossbar(now);

                if (plugin.getConfigManager().isShowProgress()) {
                    int percent = (currentIndex * 100) / blocks.size();
                    if (percent != lastProgressPercent && percent % 10 == 0) {
                        lastProgressPercent = percent;
                        plugin.getMessageUtil().sendProgress(player, percent / 5, percent);
                    }
                }

                if (currentIndex >= blocks.size()) {
                    cancel();
                    activeTasks.remove(player.getUniqueId());
                    removeBossbar();

                    // Close WorldEdit EditSession and register in history for //undo support
                    closeEditSession();

                    // Save operation to MCTools undo history
                    plugin.getUndoManager().saveOperation(player, originalBlocks);

                    long totalTime = (System.currentTimeMillis() - startTime) / 1000;
                    long avgSpeed = totalTime > 0 ? blocks.size() / totalTime : blocks.size();

                    // Single completion message
                    plugin.getMessageUtil().sendSuccess(player, shapeName, blocks.size());
                    plugin.getMessageUtil().sendInfo(player, 
                        formatNumber(blocks.size()) + " blocks in " + formatTime(totalTime) + 
                        " (" + formatNumber((int) avgSpeed) + "/s) - /mct undo to revert");

                    scheduleFloatingCleanupIfEnabled();
                    playCompletionEffects(player);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Error in PlacementTask: " + e.getMessage());
                cancel();
                activeTasks.remove(player.getUniqueId());
                removeBossbar();
                plugin.getMessageUtil().sendError(player, "Error during placement: " + e.getMessage());
            }
        }

        private void applyWithBukkit(int from, int to) {
            for (int i = from; i < to; i++) {
                Location loc = blocks.get(i);
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    BlockData data = gradientMap != null ? gradientMap.get(loc) : blockData;
                    if (data != null) {
                        loc.getBlock().setBlockData(data, false);
                    }
                }
            }
        }

        private void applyWithWorldEdit(int from, int to) throws WorldEditException {
            // Use the single EditSession for the entire operation
            // This ensures //undo will revert the complete operation, not just one tick batch
            if (editSession == null || editSessionClosed) {
                // Fallback to Bukkit if EditSession is not available
                applyWithBukkit(from, to);
                return;
            }

            for (int i = from; i < to; i++) {
                Location loc = blocks.get(i);
                if (loc.getWorld() == null || !loc.getChunk().isLoaded()) continue;

                BlockData data = gradientMap != null ? gradientMap.get(loc) : blockData;
                if (data == null) continue;

                BlockVector3 pt = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                BlockState state = toWEBlockState(data);
                editSession.setBlock(pt, state);
                
                // Also set via Bukkit for immediate visual feedback during animation
                loc.getBlock().setBlockData(data, false);
            }
        }

        /**
         * Closes the EditSession and registers it in WorldEdit/FAWE history.
         * Must be called when the operation completes or is cancelled.
         */
        private void closeEditSession() {
            if (editSession == null || editSessionClosed) return;
            editSessionClosed = true;

            try {
                // Close the session - this commits changes and records history
                editSession.close();

                // Register the EditSession in the player's LocalSession history
                // This is crucial for //undo to work properly with FAWE
                BukkitPlayer wePlayer = BukkitAdapter.adapt(player);
                LocalSession localSession = WorldEdit.getInstance().getSessionManager().get(wePlayer);
                if (localSession != null) {
                    localSession.remember(editSession);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to close WorldEdit EditSession: " + e.getMessage());
            }
        }

        private void createBossbar() {
            if (!plugin.getConfigManager().isEtaBossbarEnabled()) return;
            if (etaBossBar != null) return;
            etaBossBar = Bukkit.createBossBar(
                "§a§l⚙ §fGenerating §7│ §e" + shapeName + " §7│ §b0%",
                BarColor.GREEN,
                BarStyle.SEGMENTED_20
            );
            etaBossBar.setProgress(0.0);
            etaBossBar.addPlayer(player);
            etaBossBar.setVisible(true);
            lastBossbarSampleTime = System.currentTimeMillis();
            lastBossbarBlocksPlaced = 0;
        }

        private void removeBossbar() {
            if (etaBossBar == null) return;
            try {
                etaBossBar.removeAll();
            } finally {
                etaBossBar = null;
            }
        }

        private void updateBossbar(long now) {
            if (etaBossBar == null) return;
            long updateEveryMs = plugin.getConfigManager().getEtaBossbarUpdateTicks() * 50L;
            if (now - lastBossbarUpdate < updateEveryMs) return;
            lastBossbarUpdate = now;

            double progress = blocks.isEmpty() ? 0.0 : Math.min(1.0, Math.max(0.0, currentIndex / (double) blocks.size()));
            etaBossBar.setProgress(progress);

            long dtMs = Math.max(1, now - lastBossbarSampleTime);
            int dBlocks = Math.max(0, totalBlocksPlaced - lastBossbarBlocksPlaced);
            double instBps = dBlocks / (dtMs / 1000.0);

            double alpha = 0.35;
            smoothedBps = (smoothedBps <= 0.0) ? instBps : (alpha * instBps + (1.0 - alpha) * smoothedBps);

            lastBossbarSampleTime = now;
            lastBossbarBlocksPlaced = totalBlocksPlaced;

            long elapsedSec = Math.max(0, (now - startTime) / 1000);

            int etaSec;
            if (progress > 0.01) {
                etaSec = (int) Math.max(0, (elapsedSec / progress) - elapsedSec);
            } else if (smoothedBps > 0.5) {
                int remaining = Math.max(0, blocks.size() - currentIndex);
                etaSec = (int) Math.max(0, remaining / smoothedBps);
            } else {
                etaSec = 0;
            }

            int percent = (int) Math.round(progress * 100.0);
            String etaText = formatEta(etaSec);
            int bps = (int) Math.round(smoothedBps);
            
            // Dynamic color based on progress
            String icon;
            if (percent >= 90) {
                icon = "§a§l✔";
                etaBossBar.setColor(BarColor.GREEN);
            } else if (percent >= 50) {
                icon = "§e§l⚙";
                etaBossBar.setColor(BarColor.YELLOW);
            } else {
                icon = "§b§l⚙";
                etaBossBar.setColor(BarColor.BLUE);
            }
            
            // Build beautiful title
            StringBuilder title = new StringBuilder();
            title.append(icon).append(" §f").append(shapeName);
            title.append(" §7│ §a").append(percent).append("%");
            title.append(" §7│ §e").append(formatNumber(bps)).append("/s");
            if (etaSec > 0) {
                title.append(" §7│ §b").append(etaText);
            }
            
            etaBossBar.setTitle(title.toString());
        }

        private String formatEta(int seconds) {
            if (seconds <= 0) return "";
            if (seconds < 60) return seconds + "s";
            int m = seconds / 60;
            int s = seconds % 60;
            return String.format("%d:%02d", m, s);
        }

        private void scheduleFloatingCleanupIfEnabled() {
            if (!plugin.getConfigManager().isFloatingCleanupEnabled()) return;

            int maxBlocks = plugin.getConfigManager().getFloatingCleanupMaxBlocks();
            int blocksPerTick = Math.max(50, plugin.getConfigManager().getFloatingCleanupBlocksPerTick());

            int pad = plugin.getConfigManager().getFloatingCleanupPadding();
            int fx1 = minX - pad;
            int fy1 = Math.max(player.getWorld().getMinHeight(), minY - pad);
            int fz1 = minZ - pad;
            int fx2 = maxX + pad;
            int fy2 = Math.min(player.getWorld().getMaxHeight() - 1, maxY + pad);
            int fz2 = maxZ + pad;

            new BukkitRunnable() {
                int x = fx1, y = fy1, z = fz1;
                int scanned = 0;
                int removed = 0;

                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }

                    int budget = blocksPerTick;
                    while (budget-- > 0) {
                        if (maxBlocks > 0 && scanned >= maxBlocks) {
                            finish();
                            return;
                        }

                        Block b = player.getWorld().getBlockAt(x, y, z);
                        scanned++;

                        if (shouldRemoveFloating(b)) {
                            b.setType(Material.AIR, false);
                            removed++;
                        }

                        x++;
                        if (x > fx2) {
                            x = fx1;
                            z++;
                            if (z > fz2) {
                                z = fz1;
                                y++;
                                if (y > fy2) {
                                    finish();
                                    return;
                                }
                            }
                        }
                    }
                }

                private void finish() {
                    cancel();
                    if (removed > 0) {
                        plugin.getMessageUtil().sendInfo(player, "Cleanup: removed " + removed + " floating block(s)." );
                    }
                }

                private boolean shouldRemoveFloating(Block block) {
                    Material type = block.getType();
                    if (type.isAir()) return false;
                    if (type == Material.BEDROCK) return false;

                    Set<Material> whitelist = plugin.getConfigManager().getFloatingCleanupWhitelist();
                    if (!whitelist.isEmpty() && !whitelist.contains(type)) return false;

                    Set<Material> blacklist = plugin.getConfigManager().getFloatingCleanupBlacklist();
                    if (blacklist.contains(type)) return false;

                    if (type.hasGravity()) return false;

                    Block below = block.getRelative(0, -1, 0);
                    if (!below.getType().isAir()) return false;

                    return block.getRelative(1, 0, 0).getType().isAir()
                        && block.getRelative(-1, 0, 0).getType().isAir()
                        && block.getRelative(0, 0, 1).getType().isAir()
                        && block.getRelative(0, 0, -1).getType().isAir()
                        && block.getRelative(0, 1, 0).getType().isAir();
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }
}
