package com.mctools.commands;

import com.mctools.MCTools;
import com.mctools.gradient.GradientApplier;
import com.mctools.gradient.GradientEngine;
import com.mctools.shapes.*;
import com.mctools.utils.BlockPlacer;
import com.mctools.utils.ConfigManager;
import com.mctools.utils.MessageUtil;
import com.mctools.utils.PerformanceMonitor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Command executor for {@code /mct} (alias {@code /mctools}).
 * Parses sub-commands (shapes, admin, gradient, tree), validates arguments
 * and permissions, and delegates block placement to {@link BlockPlacer}.
 */
public class MCToolsCommand implements CommandExecutor {

    private static final Pattern HEX_COLORS_PATTERN =
        Pattern.compile("^#?[0-9a-fA-F]{6}(,#?[0-9a-fA-F]{6}){1,5}$");

    private final MCTools plugin;
    private final Map<UUID, Long> cooldowns;
    private final GradientEngine gradientEngine;
    private final GradientApplier gradientApplier;

    public MCToolsCommand(MCTools plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.gradientEngine = new GradientEngine();
        this.gradientApplier = new GradientApplier();
    }

    private BlockPlacer getBlockPlacer() {
        return plugin.getBlockPlacer();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        MessageUtil msg = plugin.getMessageUtil();

        if (!player.hasPermission("mctools.use")) {
            msg.sendError(player, "You don't have permission to use MCTools!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        // Handle admin commands
        switch (subCommand) {
            case "help" -> {
                if (args.length > 1) {
                    sendShapeHelp(player, args[1]);
                } else {
                    sendHelp(player);
                }
                return true;
            }
            case "info", "about", "version" -> {
                sendInfo(player);
                return true;
            }
            case "reload" -> {
                if (!player.hasPermission("mctools.admin")) {
                    msg.sendError(player, "You don't have permission to reload the config!");
                    return true;
                }
                plugin.reloadPluginConfig();
                msg.sendInfo(player, "Configuration reloaded successfully!");
                return true;
            }
            case "undo" -> {
                int count = 1;
                if (args.length > 1) {
                    try {
                        count = Integer.parseInt(args[1]);
                        if (count < 1) count = 1;
                        if (count > 100) count = 100; // Limit per command
                    } catch (NumberFormatException e) {
                        msg.sendError(player, "Invalid number: " + args[1]);
                        return true;
                    }
                }
                int available = plugin.getUndoManager().getUndoCount(player);
                if (available == 0) {
                    msg.sendError(player, "No operations to undo!");
                    return true;
                }
                count = Math.min(count, available);
                int restored = plugin.getUndoManager().undo(player, count);
                if (restored < 0) {
                    msg.sendError(player, "No operations to undo!");
                } else {
                    msg.sendInfo(player, "Undone " + count + " operation(s)! Restored " + restored + " blocks.");
                    msg.sendInfo(player, "Remaining: " + plugin.getUndoManager().getUndoCount(player) + " undo, " +
                        plugin.getUndoManager().getRedoCount(player) + " redo");
                }
                return true;
            }
            case "redo" -> {
                int count = 1;
                if (args.length > 1) {
                    try {
                        count = Integer.parseInt(args[1]);
                        if (count < 1) count = 1;
                        if (count > 100) count = 100;
                    } catch (NumberFormatException e) {
                        msg.sendError(player, "Invalid number: " + args[1]);
                        return true;
                    }
                }
                int available = plugin.getUndoManager().getRedoCount(player);
                if (available == 0) {
                    msg.sendError(player, "No operations to redo!");
                    return true;
                }
                count = Math.min(count, available);
                int restored = plugin.getUndoManager().redo(player, count);
                if (restored < 0) {
                    msg.sendError(player, "No operations to redo!");
                } else {
                    msg.sendInfo(player, "Redone " + count + " operation(s)! Restored " + restored + " blocks.");
                    msg.sendInfo(player, "Remaining: " + plugin.getUndoManager().getUndoCount(player) + " undo, " +
                        plugin.getUndoManager().getRedoCount(player) + " redo");
                }
                return true;
            }
            case "center" -> {
                if (!player.hasPermission("mctools.center")) {
                    msg.sendError(player, "You don't have permission to use /mct center!");
                    return true;
                }
                runCenterScan(player);
                return true;
            }
            case "cancel" -> {
                try {
                    boolean cancelled = false;
                    BlockPlacer blockPlacer = getBlockPlacer();

                    if (blockPlacer.cancelTask(player)) {
                        cancelled = true;
                    }

                    if (blockPlacer.cancelPreview(player)) {
                        cancelled = true;
                    }

                    try {
                        if (plugin.getBrushManager() != null
                                && plugin.getBrushManager().getTerrainBrush() != null
                                && plugin.getBrushManager().getTerrainBrush().hasActivePreview(player)) {
                            plugin.getBrushManager().getTerrainBrush().cancelPreview(player);
                            cancelled = true;
                        }
                    } catch (Exception ignored) { /* brush not available */ }

                    if (cancelled) {
                        msg.sendInfo(player, "Operation cancelled and all blocks restored!");
                    } else {
                        msg.sendError(player, "No active operation to cancel!");
                    }
                } catch (Exception e) {
                    msg.sendError(player, "Error cancelling operation: " + e.getMessage());
                    plugin.getLogger().warning("Error in cancel command: " + e.getMessage());
                }
                return true;
            }
            case "stop" -> {
                // Alias for cancel
                try {
                    boolean cancelled = false;
                    BlockPlacer blockPlacer = getBlockPlacer();

                    if (blockPlacer.cancelTask(player)) {
                        cancelled = true;
                    }

                    if (blockPlacer.cancelPreview(player)) {
                        cancelled = true;
                    }

                    try {
                        if (plugin.getBrushManager() != null
                                && plugin.getBrushManager().getTerrainBrush() != null
                                && plugin.getBrushManager().getTerrainBrush().hasActivePreview(player)) {
                            plugin.getBrushManager().getTerrainBrush().cancelPreview(player);
                            cancelled = true;
                        }
                    } catch (Exception ignored) { /* brush not available */ }

                    if (cancelled) {
                        msg.sendInfo(player, "Operation stopped and all blocks restored!");
                    } else {
                        msg.sendError(player, "No active operation to stop!");
                    }
                } catch (Exception e) {
                    msg.sendError(player, "Error stopping operation: " + e.getMessage());
                    plugin.getLogger().warning("Error in stop command: " + e.getMessage());
                }
                return true;
            }
            case "pause" -> {
                try {
                    BlockPlacer blockPlacer = getBlockPlacer();
                    boolean paused = blockPlacer.pauseTask(player);
                    try {
                        if (plugin.getBrushManager() != null
                                && plugin.getBrushManager().getTerrainBrush() != null
                                && plugin.getBrushManager().getTerrainBrush().pausePreview(player)) {
                            paused = true;
                        }
                    } catch (Exception ignored) { /* brush not available */ }

                    if (paused) {
                        msg.sendInfo(player, "Paused! Use /mct resume to continue.");
                    } else {
                        msg.sendError(player, "No active operation to pause!");
                    }
                } catch (Exception e) {
                    msg.sendError(player, "Error pausing operation: " + e.getMessage());
                    plugin.getLogger().warning("Error in pause command: " + e.getMessage());
                }
                return true;
            }
            case "resume" -> {
                try {
                    BlockPlacer blockPlacer = getBlockPlacer();
                    boolean resumed = blockPlacer.resumeTask(player);
                    try {
                        if (plugin.getBrushManager() != null
                                && plugin.getBrushManager().getTerrainBrush() != null
                                && plugin.getBrushManager().getTerrainBrush().resumePreview(player)) {
                            resumed = true;
                        }
                    } catch (Exception ignored) { /* brush not available */ }

                    if (resumed) {
                        msg.sendInfo(player, "Resumed!");
                    } else {
                        msg.sendError(player, "No paused operation to resume!");
                    }
                } catch (Exception e) {
                    msg.sendError(player, "Error resuming operation: " + e.getMessage());
                    plugin.getLogger().warning("Error in resume command: " + e.getMessage());
                }
                return true;
            }
            case "performance", "perf" -> {
                sendPerformanceReport(player);
                return true;
            }
            case "wand" -> {
                if (!player.hasPermission("mctools.brush")) {
                    msg.sendError(player, "You don't have permission to use the terrain brush!");
                    return true;
                }
                giveTerrainWand(player);
                return true;
            }
            case "worldeditwand" -> {
                // Internal command to give WorldEdit wand using their API
                plugin.getPlayerListener().giveWorldEditWand(player);
                return true;
            }
        }

        if (!checkCooldown(player)) {
            return true;
        }

        if (getBlockPlacer().hasActiveTask(player)) {
            msg.sendWarning(player, "You already have an active operation! Use /mct cancel to stop it.");
            return true;
        }

        try {
            handleShapeCommand(player, subCommand, args);
        } catch (IllegalArgumentException e) {
            msg.sendError(player, e.getMessage());
            getBlockPlacer().playErrorEffects(player);
        } catch (Exception e) {
            msg.sendError(player, "An error occurred: " + e.getMessage());
            plugin.getLogger().warning("Error executing command: " + e.getMessage());
        }

        return true;
    }

    /**
     * Safe wrapper for sending messages - catches any exception and sends a fallback.
     */
    private void safeSendMessage(Player player, String message) {
        try {
            plugin.getMessageUtil().sendInfo(player, message);
        } catch (Exception e) {
            player.sendMessage("§a§lMCTools §7│ §7" + message);
        }
    }

    /** Finds the center of a nearby structure within 200 blocks using ray sampling + bounded flood-fill. */
    private void runCenterScan(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        World world = player.getWorld();

        final int maxRadius = 200;
        final int rays = 72;
        final int rayStep = 2;
        final int maxVisited = 60000;
        final long timeoutMs = 2000;

        msg.sendInfo(player, "Scanning for nearby structure (radius " + maxRadius + ")...");

        new BukkitRunnable() {
            final long start = System.currentTimeMillis();
            int rayIndex = 0;

            int bestSize = 0;
            CenterResult best = null;

            final Set<Long> visited = new HashSet<>();

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (System.currentTimeMillis() - start > timeoutMs) {
                    finish();
                    return;
                }

                int raysPerTick = 6;
                while (raysPerTick-- > 0 && rayIndex < rays) {
                    Block seed = sampleRaySeed(player, rayIndex, rays, rayStep, maxRadius);
                    rayIndex++;

                    if (seed == null) continue;

                    long k = pack(seed.getX(), seed.getY(), seed.getZ());
                    if (visited.contains(k)) continue;

                    CenterResult r = floodCluster(world, seed, maxVisited, visited);
                    if (r == null) continue;

                    if (r.size > bestSize) {
                        bestSize = r.size;
                        best = r;
                    }
                }

                if (rayIndex >= rays) {
                    finishWith(best);
                }
            }

            private void finish() {
                finishWith(best);
            }

            private void finishWith(CenterResult best) {
                cancel();

                if (best == null || best.size < 30) {
                    msg.sendError(player, "No structure found within " + maxRadius + " blocks.");
                    return;
                }

                Location centroid = new Location(world,
                    best.sumX / (double) best.size,
                    best.sumY / (double) best.size,
                    best.sumZ / (double) best.size);

                Location bboxCenter = new Location(world,
                    (best.minX + best.maxX) / 2.0 + 0.5,
                    (best.minY + best.maxY) / 2.0 + 0.5,
                    (best.minZ + best.maxZ) / 2.0 + 0.5);

                Location center = new Location(world,
                    centroid.getBlockX() + 0.5,
                    centroid.getBlockY() + 0.5,
                    centroid.getBlockZ() + 0.5);

                msg.sendInfo(player, "Center found (cluster size " + best.size + "):");
                msg.sendInfo(player, "Centroid: " + centroid.getBlockX() + " " + centroid.getBlockY() + " " + centroid.getBlockZ());
                msg.sendInfo(player, "BBox center: " + bboxCenter.getBlockX() + " " + bboxCenter.getBlockY() + " " + bboxCenter.getBlockZ());

                spawnMarker(world, center);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void spawnMarker(World world, Location center) {
        for (int i = 0; i < 30; i++) {
            double y = center.getY() + (i * 0.15);
            world.spawnParticle(Particle.END_ROD, center.getX(), y, center.getZ(), 2, 0.02, 0.02, 0.02, 0);
        }
    }

    private Block sampleRaySeed(Player player, int rayIndex, int rays, int step, int maxDist) {
        Location eye = player.getEyeLocation();
        double baseYaw = eye.getYaw();

        double yaw = baseYaw + (rayIndex * (360.0 / rays));
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);

        World world = player.getWorld();
        int startY = eye.getBlockY();

        for (int d = 2; d <= maxDist; d += step) {
            int x = (int) Math.floor(eye.getX() + dx * d);
            int z = (int) Math.floor(eye.getZ() + dz * d);

            for (int dy = -6; dy <= 6; dy += 2) {
                int y = startY + dy;
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                Block b = world.getBlockAt(x, y, z);
                if (!b.getType().isAir() && b.getType() != Material.WATER && b.getType() != Material.LAVA) {
                    return b;
                }
            }
        }

        return null;
    }

    private CenterResult floodCluster(World world, Block seed, int maxVisited, Set<Long> visited) {
        ArrayDeque<Block> q = new ArrayDeque<>();
        q.add(seed);

        CenterResult res = new CenterResult();

        while (!q.isEmpty() && res.size < maxVisited) {
            Block b = q.poll();
            long key = pack(b.getX(), b.getY(), b.getZ());
            if (!visited.add(key)) continue;

            Material t = b.getType();
            if (t.isAir()) continue;
            if (t == Material.WATER || t == Material.LAVA) continue;

            res.accept(b);

            int x = b.getX(), y = b.getY(), z = b.getZ();
            if (y - 1 >= world.getMinHeight()) q.add(world.getBlockAt(x, y - 1, z));
            if (y + 1 < world.getMaxHeight()) q.add(world.getBlockAt(x, y + 1, z));
            q.add(world.getBlockAt(x + 1, y, z));
            q.add(world.getBlockAt(x - 1, y, z));
            q.add(world.getBlockAt(x, y, z + 1));
            q.add(world.getBlockAt(x, y, z - 1));
        }

        return res.size == 0 ? null : res;
    }

    private long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) y & 0xFFF);
    }

    private static class CenterResult {
        int size = 0;
        long sumX = 0, sumY = 0, sumZ = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        void accept(Block b) {
            int x = b.getX(), y = b.getY(), z = b.getZ();
            size++;
            sumX += x;
            sumY += y;
            sumZ += z;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }

    private boolean checkCooldown(Player player) {
        if (player.hasPermission("mctools.bypass.limit")) {
            return true;
        }

        int cooldownSeconds = plugin.getConfigManager().getCooldown();
        if (cooldownSeconds <= 0) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(uuid);

        if (lastUse != null) {
            long elapsed = (now - lastUse) / 1000;
            if (elapsed < cooldownSeconds) {
                plugin.getMessageUtil().sendWarning(player,
                    "Please wait " + (cooldownSeconds - elapsed) + " seconds before using another command!");
                return false;
            }
        }

        cooldowns.put(uuid, now);
        return true;
    }

    private void handleShapeCommand(Player player, String shapeCmd, String[] args) {
        if (shapeCmd.equals("tree")) {
            handleTreeCommand(player, args);
            return;
        }

        if (shapeCmd.startsWith("g")) {
            handleGradientCommand(player, shapeCmd, args);
            return;
        }

        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        if (args.length < 2) {
            msg.sendUsage(player, "/mct " + shapeCmd + " <block> <parameters...>");
            return;
        }

        BlockData blockData = parseBlockData(args[1]);
        if (blockData == null) {
            msg.sendError(player, "Invalid block type: " + args[1]);
            suggestBlock(player, args[1]);
            return;
        }

        Shape shape = createShape(player, shapeCmd, args);
        if (shape == null) {
            return;
        }

        if (!player.hasPermission(shape.getPermission())) {
            msg.sendError(player, "You don't have permission to create " + shape.getName() + "s!");
            return;
        }

        int estimated = shape.getEstimatedBlockCount();
        int maxBlocks = config.getMaxBlocks();

        // Hard limit - no bypass, even for OPs
        if (maxBlocks > 0 && estimated > maxBlocks) {
            msg.sendError(player, "Operation would place ~" + String.format("%,d", estimated) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the shape size or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        Location center = player.getLocation();
        List<Location> blocks = shape.generate(center);

        if (blocks.isEmpty()) {
            msg.sendError(player, "No blocks to place!");
            return;
        }

        // Hard limit on actual block count - no bypass
        if (maxBlocks > 0 && blocks.size() > maxBlocks) {
            msg.sendError(player, "Operation would place " + String.format("%,d", blocks.size()) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the shape size or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        msg.sendInfo(player, "Preparing " + shape.getName() + " with " + String.format("%,d", blocks.size()) + " blocks...");
        // Show performance info for large shapes
        if (blocks.size() > 1000) {
            msg.sendRaw(player, plugin.getPerformanceMonitor().getFullPerformanceSummaryMiniMessage());
        }
        getBlockPlacer().placeBlocks(player, blocks, blockData, shape.getName());
    }

    private void handleGradientCommand(Player player, String cmd, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        if (!player.hasPermission("mctools.gradient")) {
            msg.sendError(player, "You don't have permission to use gradient shapes!");
            return;
        }

        String baseCmd = cmd.substring(1);

        if (args.length < 3) {
            msg.sendUsage(player, "/mct " + cmd + " <#hex1,#hex2,...> <params...> [-dir y|x|z|radial] [-interp oklab|lab|rgb|hsl] [-unique]");
            return;
        }

        String colorsArg = args[1];
        if (!HEX_COLORS_PATTERN.matcher(colorsArg).matches()) {
            msg.sendError(player, "Invalid colors! Use comma-separated hex: #ff0000,#0000ff");
            return;
        }

        List<String> hexColors = new ArrayList<>();
        for (String c : colorsArg.split(",")) {
            if (!c.startsWith("#")) c = "#" + c;
            hexColors.add(c.toLowerCase());
        }

        if (hexColors.size() < 2 || hexColors.size() > 6) {
            msg.sendError(player, "Provide 2-6 colors!");
            return;
        }

        String direction = "y";
        String interpolation = "oklab";
        boolean uniqueOnly = false;
        List<String> positionalArgs = new ArrayList<>();
        positionalArgs.add(args[0]);
        positionalArgs.add("stone");

        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("-dir") && i + 1 < args.length) {
                direction = args[++i].toLowerCase();
                if (!direction.matches("y|x|z|radial")) {
                    msg.sendError(player, "Invalid direction! Use: y, x, z, or radial");
                    return;
                }
            } else if (a.equalsIgnoreCase("-interp") && i + 1 < args.length) {
                interpolation = args[++i].toLowerCase();
                if (!interpolation.matches("oklab|lab|rgb|hsl")) {
                    msg.sendError(player, "Invalid interpolation! Use: oklab, lab, rgb, or hsl");
                    return;
                }
            } else if (a.equalsIgnoreCase("-unique")) {
                uniqueOnly = true;
            } else {
                positionalArgs.add(a);
            }
        }

        String[] rebuiltArgs = positionalArgs.toArray(new String[0]);

        Shape shape = createShape(player, baseCmd, rebuiltArgs);
        if (shape == null) return;

        if (!player.hasPermission(shape.getPermission())) {
            msg.sendError(player, "You don't have permission to create " + shape.getName() + "s!");
            return;
        }

        Location center = player.getLocation();
        List<Location> blocks = shape.generate(center);

        if (blocks.isEmpty()) {
            msg.sendError(player, "No blocks to place!");
            return;
        }

        int maxBlocks = config.getMaxBlocks();
        // Hard limit - no bypass, even for OPs
        if (maxBlocks > 0 && blocks.size() > maxBlocks) {
            msg.sendError(player, "Operation would place " + String.format("%,d", blocks.size()) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the shape size or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        if (direction.equals("y") && gradientApplier.is2DShape(blocks)) {
            direction = "radial";
        }

        int distinctValues = gradientApplier.countDistinctAxisValues(blocks, center, direction);
        int numSteps = Math.max(2, Math.min(distinctValues, 50));

        List<GradientEngine.GradientBlock> gradientSteps =
            gradientEngine.generateGradient(hexColors, numSteps, interpolation, uniqueOnly);

        if (gradientSteps.isEmpty()) {
            msg.sendError(player, "Failed to generate gradient!");
            return;
        }

        Map<Location, BlockData> blockMap =
            gradientApplier.applyGradient(blocks, center, gradientSteps, direction);

        msg.sendInfo(player, "Gradient " + shape.getName() + ": " + numSteps + " steps, " + blocks.size() + " total blocks");

        getBlockPlacer().placeGradientBlocks(player, blockMap, "Gradient " + shape.getName());
    }

    private void handleTreeCommand(Player player, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        if (args.length < 2) {
            msg.sendUsage(player, "/mct tree <woodType> seed:<seed> th:<trunkHeight> tr:<trunkRadius> bd:<branchDensity> fd:<foliageDensity> fr:<foliageRadius> [-roots] [-special]");
            msg.sendInfo(player, "Wood types: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped");
            return;
        }

        TreeGenerator.WoodType woodType;
        try {
            woodType = TreeGenerator.WoodType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            msg.sendError(player, "Invalid wood type: " + args[1]);
            msg.sendInfo(player, "Valid: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped");
            return;
        }

        if (!player.hasPermission("mctools.shapes.tree")) {
            msg.sendError(player, "You don't have permission to generate trees!");
            return;
        }

        long seed = System.currentTimeMillis();
        int trunkHeight = 12;
        int trunkRadius = 2;
        double branchDensity = 0.7;
        double foliageDensity = 0.8;
        int foliageRadius = 6;
        boolean enableRoots = false;
        boolean useSpecialBlocks = false;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            try {
                if (arg.startsWith("seed:")) {
                    seed = Long.parseLong(arg.substring(5));
                } else if (arg.startsWith("th:")) {
                    trunkHeight = Integer.parseInt(arg.substring(3));
                    if (trunkHeight < 4 || trunkHeight > config.getMaxHeight()) {
                        msg.sendError(player, "Trunk height must be 4-" + config.getMaxHeight());
                        return;
                    }
                } else if (arg.startsWith("tr:")) {
                    trunkRadius = Integer.parseInt(arg.substring(3));
                    if (trunkRadius < 1 || trunkRadius > 6) {
                        msg.sendError(player, "Trunk radius must be 1-6");
                        return;
                    }
                } else if (arg.startsWith("bd:")) {
                    branchDensity = Double.parseDouble(arg.substring(3));
                    if (branchDensity < 0.1 || branchDensity > 1.0) {
                        msg.sendError(player, "Branch density must be 0.1-1.0");
                        return;
                    }
                } else if (arg.startsWith("fd:")) {
                    foliageDensity = Double.parseDouble(arg.substring(3));
                    if (foliageDensity < 0.1 || foliageDensity > 1.0) {
                        msg.sendError(player, "Foliage density must be 0.1-1.0");
                        return;
                    }
                } else if (arg.startsWith("fr:")) {
                    foliageRadius = Integer.parseInt(arg.substring(3));
                    if (foliageRadius < 2 || foliageRadius > 15) {
                        msg.sendError(player, "Foliage radius must be 2-15");
                        return;
                    }
                } else if (arg.equals("-roots")) {
                    enableRoots = true;
                } else if (arg.equals("-special")) {
                    useSpecialBlocks = true;
                } else {
                    msg.sendError(player, "Unknown option: " + arg);
                    msg.sendInfo(player, "Use: seed: th: tr: bd: fd: fr: -roots -special");
                    return;
                }
            } catch (NumberFormatException e) {
                msg.sendError(player, "Invalid value in: " + arg);
                return;
            }
        }

        TreeGenerator generator = new TreeGenerator(
            seed, trunkHeight, trunkRadius,
            branchDensity, foliageDensity, foliageRadius,
            enableRoots, useSpecialBlocks, woodType
        );

        int estimated = generator.getEstimatedBlockCount();
        int maxBlocks = config.getMaxBlocks();
        // Hard limit - no bypass, even for OPs
        if (maxBlocks > 0 && estimated > maxBlocks) {
            msg.sendError(player, "Tree would place ~" + String.format("%,d", estimated) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the tree size or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        Location center = player.getLocation();
        Map<Location, BlockData> blocks = generator.generate(center);

        if (blocks.isEmpty()) {
            msg.sendError(player, "No blocks generated!");
            return;
        }

        // Hard limit - no bypass
        if (maxBlocks > 0 && blocks.size() > maxBlocks) {
            msg.sendError(player, "Tree would place " + String.format("%,d", blocks.size()) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the tree size or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        msg.sendInfo(player, "Generating " + woodType.name().toLowerCase() + " tree with " + String.format("%,d", blocks.size()) + " blocks (seed: " + seed + ")");
        // Show performance info for large trees
        if (blocks.size() > 1000) {
            msg.sendRaw(player, plugin.getPerformanceMonitor().getFullPerformanceSummaryMiniMessage());
        }
        getBlockPlacer().placeGradientBlocks(player, blocks, generator.getName());
    }

    private Shape createShape(Player player, String cmd, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        try {
            return switch (cmd) {
                // 2D Shapes - Filled
                case "cir" -> {
                    requireArgs(args, 3, "/mct cir <block> <radius>");
                    int radius = parseRadius(args[2], config);
                    yield new Circle(radius);
                }
                case "sq" -> {
                    requireArgs(args, 3, "/mct sq <block> <size>");
                    int size = parseSize(args[2], config);
                    yield new Square(size);
                }
                case "rect" -> {
                    requireArgs(args, 4, "/mct rect <block> <radiusX> <radiusZ> [cornerRadius]");
                    int radiusX = parseRadius(args[2], config);
                    int radiusZ = parseRadius(args[3], config);
                    int cornerRadius = args.length > 4 ? parseInt(args[4], "cornerRadius") : 0;
                    yield new Rectangle(radiusX, radiusZ, cornerRadius);
                }
                case "ell" -> {
                    requireArgs(args, 4, "/mct ell <block> <radiusX> <radiusZ>");
                    int rx = parseRadius(args[2], config);
                    int rz = parseRadius(args[3], config);
                    yield new Ellipse(rx, rz);
                }
                case "poly" -> {
                    requireArgs(args, 4, "/mct poly <block> <radius> <sides>");
                    int radius = parseRadius(args[2], config);
                    int sides = parseInt(args[3], "sides");
                    if (sides < 3 || sides > 12) {
                        throw new IllegalArgumentException("Sides must be between 3 and 12!");
                    }
                    yield new Polygon(radius, sides);
                }
                case "star" -> {
                    requireArgs(args, 4, "/mct star <block> <radius> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int thickness = parseInt(args[3], "thickness");
                    yield new Star(radius, thickness);
                }
                case "line" -> {
                    requireArgs(args, 4, "/mct line <block> <length> <thickness>");
                    int length = parseSize(args[2], config);
                    int thickness = parseInt(args[3], "thickness");
                    yield new Line(length, thickness);
                }
                case "spi" -> {
                    requireArgs(args, 5, "/mct spi <block> <radius> <turns> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int turns = parseInt(args[3], "turns");
                    int thickness = parseInt(args[4], "thickness");
                    yield new Spiral(radius, turns, thickness);
                }
                
                // 2D Shapes - Hollow
                case "hcir" -> {
                    requireArgs(args, 4, "/mct hcir <block> <radius> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int thickness = parseThickness(args[3], config);
                    yield new Circle(radius, true, thickness);
                }
                case "hsq" -> {
                    requireArgs(args, 4, "/mct hsq <block> <size> <thickness>");
                    int size = parseSize(args[2], config);
                    int thickness = parseThickness(args[3], config);
                    yield new Square(size, true, thickness);
                }
                case "hrect" -> {
                    requireArgs(args, 5, "/mct hrect <block> <radiusX> <radiusZ> <thickness> [cornerRadius]");
                    int radiusX = parseRadius(args[2], config);
                    int radiusZ = parseRadius(args[3], config);
                    int thickness = parseThickness(args[4], config);
                    int cornerRadius = args.length > 5 ? parseInt(args[5], "cornerRadius") : 0;
                    yield new Rectangle(radiusX, radiusZ, cornerRadius, true, thickness);
                }
                case "hell" -> {
                    requireArgs(args, 5, "/mct hell <block> <radiusX> <radiusZ> <thickness>");
                    int rx = parseRadius(args[2], config);
                    int rz = parseRadius(args[3], config);
                    int thickness = parseThickness(args[4], config);
                    yield new Ellipse(rx, rz, true, thickness);
                }
                case "hpoly" -> {
                    requireArgs(args, 5, "/mct hpoly <block> <radius> <sides> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int sides = parseInt(args[3], "sides");
                    int thickness = parseThickness(args[4], config);
                    if (sides < 3 || sides > 12) {
                        throw new IllegalArgumentException("Sides must be between 3 and 12!");
                    }
                    yield new Polygon(radius, sides, true, thickness);
                }
                
                // 3D Shapes - Filled
                case "sph" -> {
                    requireArgs(args, 3, "/mct sph <block> <radius>");
                    int radius = parseRadius(args[2], config);
                    yield new Sphere(radius);
                }
                case "dome" -> {
                    requireArgs(args, 3, "/mct dome <block> <radius>");
                    int radius = parseRadius(args[2], config);
                    yield new Dome(radius);
                }
                case "cyl" -> {
                    requireArgs(args, 4, "/mct cyl <block> <height> <radius>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    yield new Cylinder(height, radius);
                }
                case "cone" -> {
                    requireArgs(args, 4, "/mct cone <block> <height> <radius>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    yield new Cone(height, radius);
                }
                case "pyr" -> {
                    requireArgs(args, 4, "/mct pyr <block> <height> <radius>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    yield new Pyramid(height, radius);
                }
                case "arch" -> {
                    requireArgs(args, 5, "/mct arch <block> <legHeight> <radius> <width>");
                    int legHeight = parseHeight(args[2], config);
                    int archRadius = parseRadius(args[3], config);
                    int width = parseInt(args[4], "width");
                    yield new Arch(legHeight, archRadius, width);
                }
                case "torus", "tor" -> {
                    requireArgs(args, 4, "/mct tor <block> <majorRadius> <minorRadius>");
                    int majorRadius = parseRadius(args[2], config);
                    int minorRadius = parseInt(args[3], "minorRadius");
                    yield new Torus(majorRadius, minorRadius);
                }
                case "helix", "hel" -> {
                    requireArgs(args, 6, "/mct hel <block> <height> <radius> <turns> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int turns = parseInt(args[4], "turns");
                    int thickness = parseInt(args[5], "thickness");
                    yield new Helix(height, radius, turns, thickness);
                }
                case "wall" -> {
                    requireArgs(args, 5, "/mct wall <block> <width> <height> <thickness>");
                    int width = parseSize(args[2], config);
                    int height = parseHeight(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    yield new Wall(width, height, thickness);
                }
                case "tube" -> {
                    requireArgs(args, 5, "/mct tube <block> <outerRadius> <innerRadius> <height>");
                    int outerRadius = parseRadius(args[2], config);
                    int innerRadius = parseInt(args[3], "innerRadius");
                    int height = parseHeight(args[4], config);
                    yield new Tube(outerRadius, innerRadius, height);
                }
                case "capsule" -> {
                    requireArgs(args, 4, "/mct capsule <block> <radius> <height>");
                    int radius = parseRadius(args[2], config);
                    int height = parseHeight(args[3], config);
                    yield new Capsule(radius, height);
                }
                case "ellipsoid" -> {
                    requireArgs(args, 5, "/mct ellipsoid <block> <radiusX> <radiusY> <radiusZ>");
                    int rx = parseRadius(args[2], config);
                    int ry = parseHeight(args[3], config);
                    int rz = parseRadius(args[4], config);
                    yield new Ellipsoid(rx, ry, rz);
                }
                
                // 3D Shapes - Hollow
                case "hsph" -> {
                    requireArgs(args, 4, "/mct hsph <block> <radius> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int thickness = parseThickness(args[3], config);
                    yield new Sphere(radius, true, thickness);
                }
                case "hdome" -> {
                    requireArgs(args, 4, "/mct hdome <block> <radius> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int thickness = parseThickness(args[3], config);
                    yield new Dome(radius, true, thickness);
                }
                case "hcyl" -> {
                    requireArgs(args, 5, "/mct hcyl <block> <height> <radius> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int thickness = parseThickness(args[4], config);
                    yield new Cylinder(height, radius, true, thickness);
                }
                case "hcone" -> {
                    requireArgs(args, 5, "/mct hcone <block> <height> <radius> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int thickness = parseThickness(args[4], config);
                    yield new Cone(height, radius, true, thickness);
                }
                case "hpyr" -> {
                    requireArgs(args, 5, "/mct hpyr <block> <height> <radius> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int thickness = parseThickness(args[4], config);
                    yield new Pyramid(height, radius, true, thickness);
                }
                case "harch" -> {
                    requireArgs(args, 6, "/mct harch <block> <legHeight> <radius> <width> <thickness>");
                    int legHeight = parseHeight(args[2], config);
                    int archRadius = parseRadius(args[3], config);
                    int width = parseInt(args[4], "width");
                    int thickness = parseThickness(args[5], config);
                    yield new Arch(legHeight, archRadius, width, true, thickness);
                }
                case "htorus", "htor" -> {
                    requireArgs(args, 5, "/mct htor <block> <majorRadius> <minorRadius> <thickness>");
                    int majorRadius = parseRadius(args[2], config);
                    int minorRadius = parseInt(args[3], "minorRadius");
                    int thickness = parseThickness(args[4], config);
                    yield new Torus(majorRadius, minorRadius, true, thickness);
                }
                case "hcapsule" -> {
                    requireArgs(args, 5, "/mct hcapsule <block> <radius> <height> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int height = parseHeight(args[3], config);
                    int thickness = parseThickness(args[4], config);
                    yield new Capsule(radius, height, true, thickness);
                }
                case "hellipsoid" -> {
                    requireArgs(args, 6, "/mct hellipsoid <block> <radiusX> <radiusY> <radiusZ> <thickness>");
                    int rx = parseRadius(args[2], config);
                    int ry = parseHeight(args[3], config);
                    int rz = parseRadius(args[4], config);
                    int thickness = parseThickness(args[5], config);
                    yield new Ellipsoid(rx, ry, rz, true, thickness);
                }
                
                default -> {
                    msg.sendError(player, "Unknown shape: " + cmd);
                    msg.sendInfo(player, "Use /mct help for a list of shapes.");
                    yield null;
                }
            };
        } catch (IllegalArgumentException e) {
            msg.sendError(player, e.getMessage());
            return null;
        }
    }

    private BlockData parseBlockData(String input) {
        try {
            Material material = Material.matchMaterial(input);
            if (material != null && material.isBlock()) {
                return material.createBlockData();
            }

            if (!input.contains(":")) {
                material = Material.matchMaterial("minecraft:" + input);
                if (material != null && material.isBlock()) {
                    return material.createBlockData();
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void suggestBlock(Player player, String input) {
        String lower = input.toLowerCase().replace("minecraft:", "");

        List<String> suggestions = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mat.isBlock() && mat.name().toLowerCase().contains(lower)) {
                suggestions.add(mat.name().toLowerCase());
                if (suggestions.size() >= 3) break;
            }
        }

        if (!suggestions.isEmpty()) {
            plugin.getMessageUtil().sendInfo(player, "Did you mean: " + String.join(", ", suggestions) + "?");
        }
    }

    private void requireArgs(String[] args, int required, String usage) {
        if (args.length < required) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private int parseInt(String value, String name) {
        try {
            int result = Integer.parseInt(value);
            if (result <= 0) {
                throw new IllegalArgumentException(name + " must be positive!");
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
    }

    private int parseRadius(String value, ConfigManager config) {
        int radius = parseInt(value, "radius");
        int max = config.getMaxRadius();
        if (radius > max) {
            throw new IllegalArgumentException("Radius " + radius + " exceeds maximum of " + max);
        }
        return radius;
    }

    private int parseHeight(String value, ConfigManager config) {
        int height = parseInt(value, "height");
        int max = config.getMaxHeight();
        if (height > max) {
            throw new IllegalArgumentException("Height " + height + " exceeds maximum of " + max);
        }
        return height;
    }

    private int parseSize(String value, ConfigManager config) {
        int size = parseInt(value, "size");
        int max = config.getMaxRadius() * 2;
        if (size > max) {
            throw new IllegalArgumentException("Size " + size + " exceeds maximum of " + max);
        }
        return size;
    }

    private int parseThickness(String value, ConfigManager config) {
        int thickness = parseInt(value, "thickness");
        int max = config.getMaxThickness();
        if (thickness > max) {
            throw new IllegalArgumentException("Thickness " + thickness + " exceeds maximum of " + max);
        }
        return thickness;
    }

    /**
     * Sends a detailed, color-coded server performance report to the player.
     * Shows real-time metrics and historical averages over multiple time windows.
     */
    private void sendPerformanceReport(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        PerformanceMonitor perf = plugin.getPerformanceMonitor();

        // ── Header ──
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("<gradient:#10b981:#059669><bold>━━━ Server Performance ━━━</bold></gradient>"));
        player.sendMessage(msg.parse(""));

        // ── Current real-time status ──
        player.sendMessage(msg.parse("<#f97316><bold>▸ Real-time</bold></#f97316>"));
        msg.sendRaw(player, perf.getFullPerformanceSummaryMiniMessage());
        player.sendMessage(msg.parse(""));

        // ── Historical windows ──
        long uptime = perf.getUptimeSeconds();
        player.sendMessage(msg.parse("<#3b82f6><bold>▸ Historical Averages</bold></#3b82f6> <dark_gray>(uptime: " + formatUptime(uptime) + ")</dark_gray>"));

        // Define windows: label, seconds
        String[][] windows = {
                {"10s", "10"},
                {"1m", "60"},
                {"10m", "600"},
                {"30m", "1800"},
                {"1h", "3600"}
        };

        for (String[] w : windows) {
            String label = w[0];
            int seconds = Integer.parseInt(w[1]);
            PerformanceMonitor.WindowAverage avg = perf.getWindowAverage(label, seconds);

            if (avg == null || avg.sampleCount() < 2) {
                player.sendMessage(msg.parse(
                        "  <gray>" + padRight(label, 4) + "</gray> <dark_gray>│</dark_gray> <dark_gray><italic>not enough data</italic></dark_gray>"));
            } else {
                String tpsC = colorForTps(avg.avgTps());
                String msptC = colorForMspt(avg.avgMspt());
                String ramC = colorForRam(avg.avgRamPercent());
                String bptC = colorForBpt(avg.avgBpt());
                int ramPct = (int) (avg.avgRamPercent() * 100);

                player.sendMessage(msg.parse(
                        "  <white><bold>" + padRight(label, 4) + "</bold></white> <dark_gray>│</dark_gray>"
                                + " <gray>TPS:</gray> <" + tpsC + ">" + String.format("%.1f", avg.avgTps()) + "</" + tpsC + ">"
                                + " <dark_gray>│</dark_gray>"
                                + " <gray>MSPT:</gray> <" + msptC + ">" + String.format("%.1f", avg.avgMspt()) + "ms</" + msptC + ">"
                                + " <dark_gray>│</dark_gray>"
                                + " <gray>RAM:</gray> <" + ramC + ">" + ramPct + "%</" + ramC + ">"
                                + " <dark_gray>│</dark_gray>"
                                + " <gray>Speed:</gray> <" + bptC + ">" + avg.avgBpt() + "</" + bptC + "> <gray>b/t</gray>"
                                + " <dark_gray>(" + avg.sampleCount() + " samples)</dark_gray>"
                ));
            }
        }

        // ── RAM details ──
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("<#a855f7><bold>▸ Memory</bold></#a855f7>"
                + " <gray>" + perf.getUsedMemoryMB() + "</gray><dark_gray>/</dark_gray><white>" + perf.getMaxMemoryMB() + " MB</white>"
                + " " + perf.getRamStatusMiniMessage()));

        // ── Bukkit TPS ──
        double[] bukkitTps = org.bukkit.Bukkit.getTPS();
        player.sendMessage(msg.parse("<#10b981><bold>▸ Bukkit TPS</bold></#10b981>"
                + " <gray>1m:</gray> <" + colorForTps(bukkitTps[0]) + ">" + String.format("%.2f", bukkitTps[0]) + "</" + colorForTps(bukkitTps[0]) + ">"
                + " <dark_gray>│</dark_gray>"
                + " <gray>5m:</gray> <" + colorForTps(bukkitTps[1]) + ">" + String.format("%.2f", bukkitTps[1]) + "</" + colorForTps(bukkitTps[1]) + ">"
                + " <dark_gray>│</dark_gray>"
                + " <gray>15m:</gray> <" + colorForTps(bukkitTps[2]) + ">" + String.format("%.2f", bukkitTps[2]) + "</" + colorForTps(bukkitTps[2]) + ">"));

        player.sendMessage(msg.parse(""));
    }

    // ── Helper methods for performance report formatting ──

    private static String colorForTps(double tps) {
        if (tps >= 18.0) return "green";
        if (tps >= 15.0) return "yellow";
        if (tps >= 12.0) return "gold";
        return "red";
    }

    private static String colorForMspt(double mspt) {
        if (mspt <= 30.0) return "green";
        if (mspt <= 45.0) return "yellow";
        if (mspt <= 50.0) return "gold";
        return "red";
    }

    private static String colorForRam(double pct) {
        if (pct >= 0.92) return "dark_red";
        if (pct >= 0.85) return "red";
        if (pct >= 0.75) return "gold";
        return "green";
    }

    private static String colorForBpt(int bpt) {
        if (bpt >= 1000) return "green";
        if (bpt >= 200) return "yellow";
        if (bpt >= 50) return "gold";
        return "red";
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private static String formatUptime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }

    private void sendHelp(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        msg.sendHelpHeader(player);

        // 2D Shapes
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>2D Shapes:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct cir <block> <radius>") + " <dark_gray>-</dark_gray> <gray>Circle</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct sq <block> <size>") + " <dark_gray>-</dark_gray> <gray>Square</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct rect <block> <radiusX> <radiusZ> [cornerRadius]") + " <dark_gray>-</dark_gray> <gray>Rectangle</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct ell <block> <radiusX> <radiusZ>") + " <dark_gray>-</dark_gray> <gray>Ellipse</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct poly <block> <radius> <sides>") + " <dark_gray>-</dark_gray> <gray>Polygon (3-12 sides)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct star <block> <radius> <thickness>") + " <dark_gray>-</dark_gray> <gray>Star</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct line <block> <length> <thickness>") + " <dark_gray>-</dark_gray> <gray>Line</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct spi <block> <radius> <turns> <thickness>") + " <dark_gray>-</dark_gray> <gray>Spiral</gray>"));

        // 3D Shapes
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>3D Shapes:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct sph <block> <radius>") + " <dark_gray>-</dark_gray> <gray>Sphere</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct dome <block> <radius>") + " <dark_gray>-</dark_gray> <gray>Dome (half sphere)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct cyl <block> <radius> <height>") + " <dark_gray>-</dark_gray> <gray>Cylinder</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct cone <block> <radius> <height>") + " <dark_gray>-</dark_gray> <gray>Cone</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct pyr <block> <base> <height>") + " <dark_gray>-</dark_gray> <gray>Pyramid</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct arch <block> <width> <height> <thickness>") + " <dark_gray>-</dark_gray> <gray>Arch</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct torus <block> <majorRadius> <minorRadius>") + " <dark_gray>-</dark_gray> <gray>Torus (donut)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct helix <block> <height> <radius> <turns> <thickness>") + " <dark_gray>-</dark_gray> <gray>Helix (3D spiral)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct wall <block> <width> <height> <thickness>") + " <dark_gray>-</dark_gray> <gray>Wall</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct tube <block> <outerR> <innerR> <height>") + " <dark_gray>-</dark_gray> <gray>Tube (hollow cylinder)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct capsule <block> <radius> <height>") + " <dark_gray>-</dark_gray> <gray>Capsule</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct ellipsoid <block> <rX> <rY> <rZ>") + " <dark_gray>-</dark_gray> <gray>Ellipsoid</gray>"));

        // Gradient Shapes
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>Gradient Shapes:</bold></" + MessageUtil.BLOCK_COLOR + "> <gray>(prefix with 'g')</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct g<shape> <#hex1,#hex2,...> <params> [-dir y|x|z|radial] [-interp oklab|lab|rgb|hsl]")));
        player.sendMessage(msg.parse("<gray>Example: </gray><white>/mct gsph #ff0000,#0000ff 20 -dir radial</white>"));

        // Tree Generator
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>Tree Generator:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct tree <woodType> [options]") + " <dark_gray>-</dark_gray> <gray>Generate custom tree</gray>"));
        player.sendMessage(msg.parse("<gray>Options: seed: th: tr: bd: fd: fr: -roots -special</gray>"));
        player.sendMessage(msg.parse("<gray>Wood types: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped</gray>"));

        // Utility
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>Utility:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct center") + " <dark_gray>-</dark_gray> <gray>Auto-detect nearby structure center</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct performance") + " <dark_gray>-</dark_gray> <gray>Server performance report (TPS, MSPT, RAM)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct undo [count]") + " <dark_gray>-</dark_gray> <gray>Undo last operation(s)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct redo [count]") + " <dark_gray>-</dark_gray> <gray>Redo undone operation(s)</gray>"));

        // Admin
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>Admin:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct cancel") + " <dark_gray>-</dark_gray> <gray>Cancel current operation</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct pause") + " <dark_gray>-</dark_gray> <gray>Pause current operation</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct resume") + " <dark_gray>-</dark_gray> <gray>Resume paused operation</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct reload") + " <dark_gray>-</dark_gray> <gray>Reload configuration</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct info") + " <dark_gray>-</dark_gray> <gray>Plugin info</gray>"));
    }

    private void sendShapeHelp(Player player, String shape) {
        MessageUtil msg = plugin.getMessageUtil();
        String shapeHelp = switch (shape.toLowerCase()) {
            case "cir", "circle" -> "Circle: /mct cir <block> <radius>\nCreates a filled circle at your position.";
            case "sq", "square" -> "Square: /mct sq <block> <size>\nCreates a filled square at your position.";
            case "rect", "rectangle" -> "Rectangle: /mct rect <block> <radiusX> <radiusZ> [cornerRadius]\nCreates a rectangle. Optional corner radius for rounded corners.";
            case "ell", "ellipse" -> "Ellipse: /mct ell <block> <radiusX> <radiusZ>\nCreates an ellipse with different X and Z radii.";
            case "poly", "polygon" -> "Polygon: /mct poly <block> <radius> <sides>\nCreates a regular polygon (3-12 sides).";
            case "star" -> "Star: /mct star <block> <radius> <thickness>\nCreates a 5-pointed star.";
            case "line" -> "Line: /mct line <block> <length> <thickness>\nCreates a line in the direction you're facing.";
            case "spi", "spiral" -> "Spiral: /mct spi <block> <radius> <turns> <thickness>\nCreates a 2D spiral.";
            case "sph", "sphere" -> "Sphere: /mct sph <block> <radius>\nCreates a filled sphere.";
            case "dome" -> "Dome: /mct dome <block> <radius>\nCreates a half-sphere (dome).";
            case "cyl", "cylinder" -> "Cylinder: /mct cyl <block> <radius> <height>\nCreates a filled cylinder.";
            case "cone" -> "Cone: /mct cone <block> <radius> <height>\nCreates a cone.";
            case "pyr", "pyramid" -> "Pyramid: /mct pyr <block> <base> <height>\nCreates a square-based pyramid.";
            case "arch" -> "Arch: /mct arch <block> <width> <height> <thickness>\nCreates an arch.";
            case "torus" -> "Torus: /mct torus <block> <majorRadius> <minorRadius>\nCreates a donut shape.";
            case "helix" -> "Helix: /mct helix <block> <radius> <height> <turns>\nCreates a 3D spiral.";
            case "wall" -> "Wall: /mct wall <block> <length> <height>\nCreates a wall.";
            case "tube" -> "Tube: /mct tube <block> <outerRadius> <innerRadius> <height>\nCreates a hollow cylinder.";
            case "capsule" -> "Capsule: /mct capsule <block> <radius> <height>\nCreates a capsule (cylinder with dome ends).";
            case "ellipsoid" -> "Ellipsoid: /mct ellipsoid <block> <radiusX> <radiusY> <radiusZ>\nCreates an ellipsoid with different radii.";
            case "tree" -> "Tree: /mct tree <woodType> [options]\nGenerates a custom tree.\nOptions: seed: th: tr: bd: fd: fr: -roots -special";
            default -> null;
        };

        if (shapeHelp != null) {
            for (String line : shapeHelp.split("\n")) {
                msg.sendInfo(player, line);
            }
        } else {
            msg.sendError(player, "Unknown shape: " + shape);
            msg.sendInfo(player, "Use /mct help for a list of shapes.");
        }
    }

    private void sendInfo(Player player) {
        MessageUtil msg = plugin.getMessageUtil();

        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("        <gradient:#FF4D4D:#FF8C42:#FFCC00:#4ECDC4:#5865F2><bold>M C T O O L S</bold></gradient>"));
        player.sendMessage(msg.parse("            <gray>version</gray> <white>1.2.0</white>"));
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("   <dark_gray>|</dark_gray> <dark_red><bold><click:open_url:'https://github.com/PenguinStudiosOrganization/MCTools/releases/'><hover:show_text:'<white>Open GitHub Releases</white>'>[GitHub]</hover></click></bold></dark_red> " +
                                     "<dark_gray>|</dark_gray> <#5865F2><bold><click:open_url:'https://discord.penguinstudios.eu/'><hover:show_text:'<white>Join our Discord</white>'>[Discord]</hover></click></bold></#5865F2> " +
                                     "<dark_gray>|</dark_gray> <green><bold><click:open_url:'https://mcutils.net/'><hover:show_text:'<white>Visit MCUtils</white>'>[MCUtils]</hover></click></bold></green> " +
                                     "<dark_gray>|</dark_gray>"));
        player.sendMessage(msg.parse("   <dark_gray>|</dark_gray> <aqua><bold><click:open_url:'https://penguinstudios.eu/'><hover:show_text:'<white>Visit PenguinStudios</white>'>[PenguinStudios]</hover></click></bold></aqua> <dark_gray>|</dark_gray>"));
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("   <dark_gray><italic>Not affiliated with Mojang AB or Microsoft.</italic></dark_gray>"));
        player.sendMessage(msg.parse(""));
    }
    
    /**
     * Gives the player a Terrain Brush wand (bamboo item).
     */
    private void giveTerrainWand(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        
        ItemStack wand = new ItemStack(Material.BAMBOO);
        ItemMeta meta = wand.getItemMeta();
        
        if (meta != null) {
            // Set display name with gradient
            meta.setDisplayName("§b§l⛰ §fTerrain Brush §7§o(MCTools)");
            
            // Set lore with detailed description
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            lore.add("§f§lTERRAIN SCULPTING TOOL");
            lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            lore.add("");
            lore.add("§e⚡ §fRight-click §7to sculpt terrain");
            lore.add("§e⚡ §fLeft-click §7to open settings GUI");
            lore.add("");
            lore.add("§b§lFeatures:");
            lore.add("§7• §fHeightmap-based terrain generation");
            lore.add("§7• §fRaise, Lower, Smooth, Flatten modes");
            lore.add("§7• §fAdjustable size, intensity & height");
            lore.add("§7• §fPreview before applying changes");
            lore.add("§7• §fFull undo/redo support");
            lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            meta.setLore(lore);
            
            // Add enchant glow effect
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            
            wand.setItemMeta(meta);
        }
        
        // Give item to player
        player.getInventory().addItem(wand);
        
        msg.sendInfo(player, "You received the Terrain Brush Wand!");
        msg.sendInfo(player, "Right-click to sculpt, Left-click for settings GUI.");
    }
}
