package com.mctools.commands;

import com.mctools.MCTools;
import com.mctools.gradient.GradientApplier;
import com.mctools.gradient.GradientEngine;
import com.mctools.schematic.SchematicManager;
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

    /** Maps readable shape names and short aliases → internal abbreviation used by createShape(). */
    private static final Map<String, String> SHAPE_ALIAS_MAP;
    /** Shape names (full or alias) that support the {@code -h} hollow flag. */
    private static final Set<String> HOLLOW_CAPABLE;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("circle",          "cir");       m.put("cir",         "cir");
        m.put("square",          "sq");        m.put("sq",          "sq");
        m.put("rectangle",       "rect");      m.put("rect",        "rect");
        m.put("ellipse",         "ell");       m.put("ell",         "ell");
        m.put("polygon",         "poly");      m.put("poly",        "poly");
        m.put("star",            "star");
        m.put("line",            "line");
        m.put("spiral",          "spi");       m.put("spi",         "spi");
        m.put("sphere",          "sph");       m.put("sph",         "sph");
        m.put("dome",            "dome");
        m.put("cylinder",        "cyl");       m.put("cyl",         "cyl");
        m.put("cone",            "cone");
        m.put("pyramid",         "pyr");       m.put("pyr",         "pyr");
        m.put("arch",            "arch");
        m.put("torus",           "tor");       m.put("tor",         "tor");
        m.put("wall",            "wall");
        m.put("helix",           "hel");       m.put("hel",         "hel");
        m.put("tube",            "tube");
        m.put("capsule",         "capsule");
        m.put("ellipsoid",       "ellipsoid");
        m.put("sectioncylinder", "scyl");      m.put("scyl",        "scyl");
        m.put("tree",            "tree");
        m.put("staircase",       "stair");     m.put("stair",       "stair");
        m.put("roof",            "roof");
        SHAPE_ALIAS_MAP = Collections.unmodifiableMap(m);

        HOLLOW_CAPABLE = Set.of(
            "circle", "cir", "square", "sq", "rectangle", "rect",
            "ellipse", "ell", "polygon", "poly",
            "sphere", "sph", "dome", "cylinder", "cyl",
            "cone", "pyramid", "pyr", "arch",
            "torus", "tor", "capsule", "ellipsoid",
            "roof"
        );
    }

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
                    if (args[1].equalsIgnoreCase("path")) {
                        plugin.getPathToolManager().sendHelp(player);
                    } else {
                        sendShapeHelp(player, args[1]);
                    }
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
            case "debug" -> {
                if (!player.hasPermission("mctools.admin")) {
                    msg.sendError(player, "You don't have permission to use debug commands!");
                    return true;
                }
                if (args.length < 3 || !args[1].equalsIgnoreCase("update")) {
                    msg.sendError(player, "Usage: /mct debug update <version>");
                    return true;
                }
                String fakeVersion = args[2];
                plugin.getUpdateChecker().sendDebugUpdate(player, fakeVersion);
                msg.sendInfo(player, "Sent debug update notification for version <white>" + fakeVersion + "</white>.");
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

                    try {
                        if (plugin.getSchematicManager().cancelPreview(player)) {
                            cancelled = true;
                        }
                    } catch (Exception ignored) { /* schematic not available */ }

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

                    try {
                        if (plugin.getSchematicManager().cancelPreview(player)) {
                            cancelled = true;
                        }
                    } catch (Exception ignored) { /* schematic not available */ }

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
            case "pathshovel" -> {
                // Internal command to give path tool shovel and auto-enable the tool
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                givePathShovel(player);
                return true;
            }
            case "schematic", "schem" -> {
                handleSchematicCommand(player, args);
                return true;
            }
            case "sel" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                plugin.getPathToolManager().handleSelectionReset(player);
                return true;
            }
            case "tool" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendUsage(player, "/mct tool <enable|disable>");
                    return true;
                }
                boolean enable = args[1].equalsIgnoreCase("enable");
                boolean disable = args[1].equalsIgnoreCase("disable");
                if (!enable && !disable) {
                    msg.sendUsage(player, "/mct tool <enable|disable>");
                    return true;
                }
                plugin.getPathToolManager().handleToolToggle(player, enable);
                return true;
            }
            case "mode" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendUsage(player, "/mct mode <road|bridge|curve>");
                    return true;
                }
                plugin.getPathToolManager().handleModeSet(player, args[1]);
                return true;
            }
            case "pos" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendUsage(player, "/mct pos <list|undo|clear>");
                    return true;
                }
                plugin.getPathToolManager().handlePos(player, args[1]);
                return true;
            }
            case "set" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                String[] setArgs = new String[Math.max(0, args.length - 1)];
                System.arraycopy(args, 1, setArgs, 0, setArgs.length);
                plugin.getPathToolManager().handleSet(player, setArgs);
                return true;
            }
            case "preview" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendUsage(player, "/mct preview <on|off>");
                    return true;
                }
                plugin.getPathToolManager().handlePreview(player, args[1]);
                return true;
            }
            case "generate" -> {
                if (!player.hasPermission("mctools.path.generate")) {
                    msg.sendError(player, "You don't have permission to generate path structures!");
                    return true;
                }
                plugin.getPathToolManager().handleGenerate(player);
                return true;
            }
            case "particles" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                if (args.length < 2) {
                    msg.sendUsage(player, "/mct particles <on|off>");
                    return true;
                }
                plugin.getPathToolManager().handleParticlesToggle(player, args[1]);
                return true;
            }
            case "paste" -> {
                if (!player.hasPermission("mctools.admin")) {
                    msg.sendError(player, "You don't have permission to paste schematics!");
                    return true;
                }
                boolean ignoreAir = false;
                boolean skipPreview = false;
                for (int i = 1; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("-a")) {
                        ignoreAir = true;
                    } else if (args[i].equalsIgnoreCase("-p")) {
                        skipPreview = true;
                    }
                }
                plugin.getSchematicManager().paste(player, ignoreAir, skipPreview);
                return true;
            }
            case "scene" -> {
                if (args.length < 3) {
                    msg.sendUsage(player, "/mct scene <block> <shape> <params...> [+offset <x> <y> <z> <shape> <params...>] ...");
                    return true;
                }
                if (!checkCooldown(player)) return true;
                if (getBlockPlacer().hasActiveTask(player)) {
                    msg.sendWarning(player, "You already have an active operation! Use /mct cancel to stop it.");
                    return true;
                }
                try {
                    handleSceneCommand(player, args);
                } catch (IllegalArgumentException e) {
                    msg.sendError(player, e.getMessage());
                    getBlockPlacer().playErrorEffects(player);
                }
                return true;
            }
            case "shape" -> {
                if (args.length < 3) {
                    msg.sendUsage(player, "/mct shape <name> <block> [params...] [-h [thickness]]");
                    return true;
                }
                if (!checkCooldown(player)) return true;
                if (getBlockPlacer().hasActiveTask(player)) {
                    msg.sendWarning(player, "You already have an active operation! Use /mct cancel to stop it.");
                    return true;
                }
                try {
                    handleNewShapeCommand(player, args);
                } catch (IllegalArgumentException e) {
                    msg.sendError(player, e.getMessage());
                    getBlockPlacer().playErrorEffects(player);
                }
                return true;
            }
            case "gradient" -> {
                if (args.length < 3) {
                    msg.sendUsage(player, "/mct gradient <shape> <#hex1,#hex2,...> [params...] [-h [thickness]]");
                    return true;
                }
                if (!checkCooldown(player)) return true;
                if (getBlockPlacer().hasActiveTask(player)) {
                    msg.sendWarning(player, "You already have an active operation! Use /mct cancel to stop it.");
                    return true;
                }
                try {
                    handleNewGradientCommand(player, args);
                } catch (IllegalArgumentException e) {
                    msg.sendError(player, e.getMessage());
                    getBlockPlacer().playErrorEffects(player);
                }
                return true;
            }
            case "path" -> {
                if (!player.hasPermission("mctools.path.use")) {
                    msg.sendError(player, "You don't have permission to use path tools!");
                    return true;
                }
                if (args.length < 2) {
                    plugin.getPathToolManager().sendHelp(player);
                    return true;
                }
                handlePathSubCommand(player, args);
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

    // ── New structured command handlers ──────────────────────────────────

    private void handleNewShapeCommand(Player player, String[] args) {
        // args: ["shape", <name>, <block>, [params...], [-h [thick]]]
        MessageUtil msg = plugin.getMessageUtil();

        String shapeName = args[1].toLowerCase();
        String abbrev = SHAPE_ALIAS_MAP.get(shapeName);
        if (abbrev == null) {
            msg.sendUnknownArgument(player, args[1]);
            return;
        }

        if (abbrev.equals("tree")) {
            String[] treeArgs = new String[args.length - 1];
            treeArgs[0] = "tree";
            System.arraycopy(args, 2, treeArgs, 1, args.length - 2);
            handleTreeCommand(player, treeArgs);
            return;
        }
        if (abbrev.equals("scyl")) {
            String[] scylArgs = new String[args.length - 1];
            scylArgs[0] = "scyl";
            System.arraycopy(args, 2, scylArgs, 1, args.length - 2);
            handleSectionCylinderCommand(player, scylArgs);
            return;
        }

        boolean hollow = false;
        String hollowThickness = "1";
        List<String> positional = new ArrayList<>();
        positional.add(""); // placeholder for final abbrev

        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-h")) {
                hollow = true;
                if (i + 1 < args.length && isPositiveInt(args[i + 1])) {
                    hollowThickness = args[++i];
                }
            } else {
                positional.add(args[i]);
            }
        }

        if (hollow) {
            if (!HOLLOW_CAPABLE.contains(shapeName)) {
                msg.sendError(player, "<white>" + args[1] + "</white> does not support hollow mode.");
                return;
            }
            positional.add(hollowThickness);
            positional.set(0, "h" + abbrev);
        } else {
            positional.set(0, abbrev);
        }

        String finalAbbrev = positional.get(0);
        String[] shapeArgs = positional.toArray(new String[0]);
        handleShapeCommand(player, finalAbbrev, shapeArgs);
    }

    private void handleSceneCommand(Player player, String[] args) {
        // New format: /mct scene <shape> <block_or_colors> <params> [flags] +offset x y z <shape> <block_or_colors> <params> [flags] ...
        // Each shape group has its own block or gradient colors.
        // If the block_or_colors arg contains '#' and ',', it's treated as gradient colors.
        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        if (!player.hasPermission("mctools.shapes.scene")) {
            msg.sendError(player, "You don't have permission to use scene!");
            return;
        }

        if (args.length < 4) {
            msg.sendUsage(player, "/mct scene <shape> <block_or_colors> <params...> [+offset x y z <shape> <block_or_colors> <params...>] ...");
            return;
        }

        // Split args into groups by "+offset"
        List<List<String>> groups = new ArrayList<>();
        List<int[]> offsets = new ArrayList<>();
        List<String> currentGroup = new ArrayList<>();
        int[] currentOffset = {0, 0, 0};

        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("+offset")) {
                if (!currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                    offsets.add(currentOffset);
                    currentGroup = new ArrayList<>();
                }
                if (i + 3 >= args.length) {
                    throw new IllegalArgumentException("Expected 3 numbers after +offset (x y z)");
                }
                currentOffset = new int[]{
                    parseIntAllowNegative(args[i + 1], "offset x"),
                    parseIntAllowNegative(args[i + 2], "offset y"),
                    parseIntAllowNegative(args[i + 3], "offset z")
                };
                i += 3;
            } else {
                currentGroup.add(args[i]);
            }
        }
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
            offsets.add(currentOffset);
        }

        if (groups.isEmpty()) {
            throw new IllegalArgumentException("No shapes specified!");
        }
        if (groups.size() > 10) {
            throw new IllegalArgumentException("Maximum 10 shapes per scene command (got " + groups.size() + ")");
        }

        Location baseLocation = player.getLocation();
        // Combined block map: Location → BlockData (supports mixed gradient + solid)
        Map<Long, Map.Entry<Location, BlockData>> allBlocks = new LinkedHashMap<>();
        int totalEstimated = 0;

        for (int g = 0; g < groups.size(); g++) {
            List<String> group = groups.get(g);
            int[] offset = offsets.get(g);

            if (group.size() < 2) {
                throw new IllegalArgumentException("Each shape needs at least: <shape> <block_or_colors> <params...>");
            }

            // group[0] = shape name, group[1] = block or gradient colors
            String shapeName = group.get(0).toLowerCase();
            String abbrev = SHAPE_ALIAS_MAP.get(shapeName);
            if (abbrev == null) {
                throw new IllegalArgumentException("Unknown shape: " + group.get(0));
            }

            String blockOrColors = group.get(1);
            boolean isGradient = blockOrColors.contains("#") && blockOrColors.contains(",");

            // Parse flags: -h, -dir, -interp, -unique from remaining args
            boolean hollow = false;
            String hollowThickness = "1";
            String direction = "y";
            String interpolation = "oklab";
            boolean uniqueOnly = false;
            List<String> positional = new ArrayList<>();

            for (int i = 2; i < group.size(); i++) {
                String a = group.get(i);
                if (a.equalsIgnoreCase("-h")) {
                    hollow = true;
                    if (i + 1 < group.size() && isPositiveInt(group.get(i + 1))) {
                        hollowThickness = group.get(++i);
                    }
                } else if (a.equalsIgnoreCase("-dir") && i + 1 < group.size()) {
                    direction = group.get(++i).toLowerCase();
                } else if (a.equalsIgnoreCase("-interp") && i + 1 < group.size()) {
                    interpolation = group.get(++i).toLowerCase();
                } else if (a.equalsIgnoreCase("-unique")) {
                    uniqueOnly = true;
                } else {
                    positional.add(a);
                }
            }

            String finalAbbrev;
            if (hollow) {
                if (!HOLLOW_CAPABLE.contains(shapeName)) {
                    throw new IllegalArgumentException(group.get(0) + " does not support hollow mode.");
                }
                positional.add(hollowThickness);
                finalAbbrev = "h" + abbrev;
            } else {
                finalAbbrev = abbrev;
            }

            // Build fake args for createShape: [abbrev, block_placeholder, ...positional]
            String[] shapeArgs = new String[positional.size() + 2];
            shapeArgs[0] = finalAbbrev;
            shapeArgs[1] = "stone"; // placeholder block for parameter parsing
            for (int i = 0; i < positional.size(); i++) {
                shapeArgs[i + 2] = positional.get(i);
            }

            Shape shape = createShape(player, finalAbbrev, shapeArgs);
            if (shape == null) return;

            totalEstimated += shape.getEstimatedBlockCount();

            Location offsetLocation = baseLocation.clone().add(offset[0], offset[1], offset[2]);
            List<Location> shapeBlocks = shape.generate(offsetLocation);

            if (isGradient) {
                // Gradient shape
                if (!HEX_COLORS_PATTERN.matcher(blockOrColors).matches()) {
                    throw new IllegalArgumentException("Invalid gradient colors for shape " + shapeName + ": " + blockOrColors);
                }
                List<String> hexColors = new ArrayList<>();
                for (String c : blockOrColors.split(",")) {
                    if (!c.startsWith("#")) c = "#" + c;
                    hexColors.add(c.toLowerCase());
                }
                if (hexColors.size() < 2 || hexColors.size() > 6) {
                    throw new IllegalArgumentException("Gradient needs 2-6 colors for shape " + shapeName);
                }

                if (direction.equals("y") && gradientApplier.is2DShape(shapeBlocks)) {
                    direction = "radial";
                }
                int distinctValues = gradientApplier.countDistinctAxisValues(shapeBlocks, offsetLocation, direction);
                int numSteps = Math.max(2, Math.min(distinctValues, 50));

                List<GradientEngine.GradientBlock> gradientSteps =
                    gradientEngine.generateGradient(hexColors, numSteps, interpolation, uniqueOnly);

                if (!gradientSteps.isEmpty()) {
                    Map<Location, BlockData> gradientMap =
                        gradientApplier.applyGradient(shapeBlocks, offsetLocation, gradientSteps, direction);
                    for (Map.Entry<Location, BlockData> entry : gradientMap.entrySet()) {
                        long key = packLocation(entry.getKey());
                        allBlocks.putIfAbsent(key, entry);
                    }
                }
            } else {
                // Solid block shape
                BlockData blockData = parseBlockData(blockOrColors);
                if (blockData == null) {
                    throw new IllegalArgumentException("Invalid block type for shape " + shapeName + ": " + blockOrColors);
                }
                for (Location loc : shapeBlocks) {
                    long key = packLocation(loc);
                    allBlocks.putIfAbsent(key, Map.entry(loc, blockData));
                }
            }
        }

        int maxBlocks = config.getMaxBlocks();
        if (maxBlocks > 0 && totalEstimated > maxBlocks) {
            msg.sendError(player, "Scene would place ~" + String.format("%,d", totalEstimated) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce shape sizes or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        if (allBlocks.isEmpty()) {
            msg.sendError(player, "No blocks to place!");
            return;
        }

        if (maxBlocks > 0 && allBlocks.size() > maxBlocks) {
            msg.sendError(player, "Scene would place " + String.format("%,d", allBlocks.size()) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce shape sizes or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        // Build the final Location→BlockData map for gradient-aware placement
        Map<Location, BlockData> finalBlockMap = new LinkedHashMap<>();
        for (Map.Entry<Location, BlockData> entry : allBlocks.values()) {
            finalBlockMap.put(entry.getKey(), entry.getValue());
        }

        msg.sendInfo(player, "Preparing Scene with " + groups.size() + " shapes, " + String.format("%,d", finalBlockMap.size()) + " blocks...");
        if (finalBlockMap.size() > 1000) {
            msg.sendRaw(player, plugin.getPerformanceMonitor().getFullPerformanceSummaryMiniMessage());
        }
        getBlockPlacer().placeGradientBlocks(player, finalBlockMap, "Scene (" + groups.size() + " shapes)");
    }

    private static long packLocation(Location loc) {
        // x,z range ±30M → +30M gives 0..60M (26 bits each).
        // y range -64..319 → +64 gives 0..383 (9 bits). Total 61 bits.
        // Layout: [x:26][y:9][z:26]
        long x = loc.getBlockX() + 30_000_000L;
        long y = loc.getBlockY() + 64L;
        long z = loc.getBlockZ() + 30_000_000L;
        return (x << 35) | (y << 26) | z;
    }

    private void handleNewGradientCommand(Player player, String[] args) {
        // args: ["gradient", <name>, <colors>, [params...], [-h [thick]], [flags...]]
        MessageUtil msg = plugin.getMessageUtil();

        String shapeName = args[1].toLowerCase();
        String abbrev = SHAPE_ALIAS_MAP.get(shapeName);
        if (abbrev == null || abbrev.equals("tree") || abbrev.equals("scyl")) {
            msg.sendError(player, "Gradient is not supported for: " + args[1]);
            return;
        }

        boolean hollow = false;
        String hollowThickness = "1";
        List<String> positional = new ArrayList<>();
        List<String> flags = new ArrayList<>();

        for (int i = 3; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("-h")) {
                hollow = true;
                if (i + 1 < args.length && isPositiveInt(args[i + 1])) {
                    hollowThickness = args[++i];
                }
            } else if ((a.equalsIgnoreCase("-dir") || a.equalsIgnoreCase("-interp")) && i + 1 < args.length) {
                flags.add(a);
                flags.add(args[++i]);
            } else if (a.equalsIgnoreCase("-unique")) {
                flags.add(a);
            } else {
                positional.add(a);
            }
        }

        if (hollow) {
            if (!HOLLOW_CAPABLE.contains(shapeName)) {
                msg.sendError(player, "<white>" + args[1] + "</white> does not support hollow mode.");
                return;
            }
            positional.add(hollowThickness);
        }

        String gradAbbrev = (hollow ? "gh" : "g") + abbrev;

        List<String> finalArgs = new ArrayList<>();
        finalArgs.add(gradAbbrev);
        finalArgs.add(args[2]);
        finalArgs.addAll(positional);
        finalArgs.addAll(flags);

        handleGradientCommand(player, gradAbbrev, finalArgs.toArray(new String[0]));
    }

    private void handlePathSubCommand(Player player, String[] args) {
        // args: ["path", <sub>, [sub-args...]]
        MessageUtil msg = plugin.getMessageUtil();
        String sub = args[1].toLowerCase();

        switch (sub) {
            case "help" -> plugin.getPathToolManager().sendHelp(player);
            case "tool" -> {
                if (args.length < 3) { msg.sendUsage(player, "/mct path tool <enable|disable>"); return; }
                boolean enable = args[2].equalsIgnoreCase("enable");
                boolean disable = args[2].equalsIgnoreCase("disable");
                if (!enable && !disable) { msg.sendUsage(player, "/mct path tool <enable|disable>"); return; }
                plugin.getPathToolManager().handleToolToggle(player, enable);
            }
            case "mode" -> {
                if (args.length < 3) { msg.sendUsage(player, "/mct path mode <road|bridge|curve>"); return; }
                plugin.getPathToolManager().handleModeSet(player, args[2]);
            }
            case "pos" -> {
                if (args.length < 3) { msg.sendUsage(player, "/mct path pos <list|undo|clear>"); return; }
                plugin.getPathToolManager().handlePos(player, args[2]);
            }
            case "set" -> {
                String[] setArgs = new String[Math.max(0, args.length - 2)];
                System.arraycopy(args, 2, setArgs, 0, setArgs.length);
                plugin.getPathToolManager().handleSet(player, setArgs);
            }
            case "preview" -> {
                if (args.length < 3) { msg.sendUsage(player, "/mct path preview <on|off>"); return; }
                plugin.getPathToolManager().handlePreview(player, args[2]);
            }
            case "generate" -> {
                if (!player.hasPermission("mctools.path.generate")) {
                    msg.sendError(player, "You don't have permission to generate path structures!");
                    return;
                }
                plugin.getPathToolManager().handleGenerate(player);
            }
            case "particles" -> {
                if (args.length < 3) { msg.sendUsage(player, "/mct path particles <on|off>"); return; }
                plugin.getPathToolManager().handleParticlesToggle(player, args[2]);
            }
            case "clear", "sel" -> plugin.getPathToolManager().handleSelectionReset(player);
            default -> msg.sendError(player, "Unknown path subcommand. Use /mct path help.");
        }
    }

    private static boolean isPositiveInt(String s) {
        try { return Integer.parseInt(s) > 0; } catch (NumberFormatException e) { return false; }
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

    /** All valid shape command names (used to distinguish shapes from unknown arguments). */
    private static final Set<String> VALID_SHAPE_NAMES = Set.of(
        "cir", "sq", "rect", "ell", "poly", "star", "line", "spi",
        "hcir", "hsq", "hrect", "hell", "hpoly",
        "sph", "dome", "cyl", "cone", "pyr", "arch", "torus", "tor", "helix", "hel", "wall", "tube", "capsule", "ellipsoid",
        "hsph", "hdome", "hcyl", "hcone", "hpyr", "harch", "htorus", "htor", "hcapsule", "hellipsoid",
        "scyl",
        "stair", "roof", "hroof"
    );

    private void handleShapeCommand(Player player, String shapeCmd, String[] args) {
        if (shapeCmd.equals("tree")) {
            handleTreeCommand(player, args);
            return;
        }

        if (shapeCmd.startsWith("g")) {
            handleGradientCommand(player, shapeCmd, args);
            return;
        }

        // Section Cylinder uses dual-block placement (outline + fill), handle separately
        if (shapeCmd.equals("scyl")) {
            handleSectionCylinderCommand(player, args);
            return;
        }

        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        // If the subcommand is not a known shape, send unknown argument message immediately
        if (!VALID_SHAPE_NAMES.contains(shapeCmd)) {
            msg.sendUnknownArgument(player, shapeCmd);
            return;
        }

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

    /**
     * Handles the Section Cylinder command which uses two block types:
     * one for the outline/dividers and one for the fill sections.
     * Syntax: /mct scyl <block> <radius> <sections> <sectionBlock>
     */
    private void handleSectionCylinderCommand(Player player, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        if (args.length < 5) {
            msg.sendUsage(player, "/mct scyl <block> <radius> <sections> <sectionBlock>");
            return;
        }

        // Parse outline block
        BlockData outlineBlock = parseBlockData(args[1]);
        if (outlineBlock == null) {
            msg.sendError(player, "Invalid block type: " + args[1]);
            suggestBlock(player, args[1]);
            return;
        }

        // Parse radius (scyl supports up to 500)
        int radius;
        try {
            radius = parseInt(args[2], "radius");
            if (radius > 500) {
                msg.sendError(player, "Radius " + radius + " exceeds maximum of 500!");
                return;
            }
        } catch (IllegalArgumentException e) {
            msg.sendError(player, e.getMessage());
            return;
        }

        // Parse sections
        int sections;
        try {
            sections = parseInt(args[3], "sections");
            if (sections < 2) {
                msg.sendError(player, "Sections must be at least 2!");
                return;
            }
            if (sections > 64) {
                msg.sendError(player, "Sections must be at most 64!");
                return;
            }
        } catch (IllegalArgumentException e) {
            msg.sendError(player, e.getMessage());
            return;
        }

        // Parse section fill block
        BlockData sectionBlock = parseBlockData(args[4]);
        if (sectionBlock == null) {
            msg.sendError(player, "Invalid section block type: " + args[4]);
            suggestBlock(player, args[4]);
            return;
        }

        SectionCylinder shape = new SectionCylinder(radius, sections);
        shape.setSectionBlockData(sectionBlock);

        if (!player.hasPermission(shape.getPermission())) {
            msg.sendError(player, "You don't have permission to create " + shape.getName() + "s!");
            return;
        }

        int estimated = shape.getEstimatedBlockCount();
        int maxBlocks = config.getMaxBlocks();

        if (maxBlocks > 0 && estimated > maxBlocks) {
            msg.sendError(player, "Operation would place ~" + String.format("%,d", estimated) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the shape size or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        Location center = player.getLocation();
        Map<Location, BlockData> blockMap = shape.generateWithSections(center, outlineBlock);

        if (blockMap.isEmpty()) {
            msg.sendError(player, "No blocks to place!");
            return;
        }

        if (maxBlocks > 0 && blockMap.size() > maxBlocks) {
            msg.sendError(player, "Operation would place " + String.format("%,d", blockMap.size()) + " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the shape size or ask an admin to increase max-blocks in config.");
            getBlockPlacer().playErrorEffects(player);
            return;
        }

        msg.sendInfo(player, "Preparing " + shape.getName() + " (" + sections + " sections) with " + String.format("%,d", blockMap.size()) + " blocks...");
        if (blockMap.size() > 1000) {
            msg.sendRaw(player, plugin.getPerformanceMonitor().getFullPerformanceSummaryMiniMessage());
        }
        getBlockPlacer().placeGradientBlocks(player, blockMap, shape.getName());
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
                    requireArgs(args, 5, "/mct hrect <block> <radiusX> <radiusZ> <thickness> or /mct hrect <block> <radiusX> <radiusZ> <cornerRadius> <thickness>");
                    int radiusX = parseRadius(args[2], config);
                    int radiusZ = parseRadius(args[3], config);
                    if (args.length > 5) {
                        // /mct hrect <block> <radiusX> <radiusZ> <cornerRadius> <thickness>
                        int cornerRadius = parseInt(args[4], "cornerRadius");
                        int thickness = parseThickness(args[5], config);
                        yield new Rectangle(radiusX, radiusZ, cornerRadius, true, thickness);
                    } else {
                        // /mct hrect <block> <radiusX> <radiusZ> <thickness>
                        int thickness = parseThickness(args[4], config);
                        yield new Rectangle(radiusX, radiusZ, 0, true, thickness);
                    }
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
                    requireArgs(args, 5, "/mct tube <block> <radius> <height> <innerRadius>");
                    int radius = parseRadius(args[2], config);
                    int height = parseHeight(args[3], config);
                    int innerRadius = parseInt(args[4], "innerRadius");
                    if (innerRadius >= radius) {
                        throw new IllegalArgumentException("Inner radius must be less than outer radius!");
                    }
                    yield new Tube(height, radius, innerRadius);
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

                // Staircase
                case "stair" -> {
                    requireArgs(args, 5, "/mct stair <block> <radius> <height> <stepWidth>");
                    int radius = parseRadius(args[2], config);
                    int height = parseHeight(args[3], config);
                    int stepWidth = parseInt(args[4], "stepWidth");
                    if (stepWidth > radius) {
                        throw new IllegalArgumentException("Step width cannot exceed radius!");
                    }
                    yield new Staircase(radius, height, stepWidth);
                }

                // Roof
                case "roof" -> {
                    requireArgs(args, 6, "/mct roof <block> <width> <length> <pitch> <style>");
                    int width = parseSize(args[2], config);
                    int length = parseSize(args[3], config);
                    double pitch = parseFloat(args[4], "pitch", 0.1, 3.0);
                    String style = parseRoofStyle(args[5]);
                    yield new Roof(width, length, pitch, style);
                }
                case "hroof" -> {
                    requireArgs(args, 7, "/mct hroof <block> <width> <length> <pitch> <style> <thickness>");
                    int width = parseSize(args[2], config);
                    int length = parseSize(args[3], config);
                    double pitch = parseFloat(args[4], "pitch", 0.1, 3.0);
                    String style = parseRoofStyle(args[5]);
                    int thickness = parseThickness(args[6], config);
                    yield new Roof(width, length, pitch, style, true, thickness);
                }

                default -> {
                    msg.sendUnknownArgument(player, cmd);
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

    private double parseFloat(String value, String name, double min, double max) {
        try {
            double result = Double.parseDouble(value);
            if (result < min || result > max) {
                throw new IllegalArgumentException(name + " must be between " + min + " and " + max + "!");
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
    }

    private int parseIntAllowNegative(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
    }

    private String parseRoofStyle(String value) {
        String style = value.toLowerCase();
        if (!style.equals("peaked") && !style.equals("hip") && !style.equals("flat")) {
            throw new IllegalArgumentException("Invalid roof style: " + value + " (must be peaked, hip, or flat)");
        }
        return style;
    }

    /**
     * Sends a detailed, color-coded server performance report to the player.
     * Shows real-time metrics and historical averages over multiple time windows.
     */
    private void sendPerformanceReport(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        PerformanceMonitor perf = plugin.getPerformanceMonitor();

        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse(msg.buildHeader("Server Performance")));
        player.sendMessage(msg.parse(""));

        // Real-time
        player.sendMessage(msg.parse("  <" + MessageUtil.BLOCK_COLOR + "><bold>▸ Real-time</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        msg.sendRaw(player, perf.getFullPerformanceSummaryMiniMessage());
        player.sendMessage(msg.parse(""));

        // Historical windows
        long uptime = perf.getUptimeSeconds();
        player.sendMessage(msg.parse("  <" + MessageUtil.INFO_COLOR + "><bold>▸ Historical Averages</bold></" + MessageUtil.INFO_COLOR + "> <" + MessageUtil.MUTED_COLOR + ">(uptime: " + formatUptime(uptime) + ")</" + MessageUtil.MUTED_COLOR + ">"));

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
                        "    <gray>" + padRight(label, 4) + "</gray> <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + "> <" + MessageUtil.MUTED_COLOR + "><italic>not enough data</italic></" + MessageUtil.MUTED_COLOR + ">"));
            } else {
                String tpsC = colorForTps(avg.avgTps());
                String msptC = colorForMspt(avg.avgMspt());
                String ramC = colorForRam(avg.avgRamPercent());
                String bptC = colorForBpt(avg.avgBpt());
                int ramPct = (int) (avg.avgRamPercent() * 100);

                player.sendMessage(msg.parse(
                        "    <white><bold>" + padRight(label, 4) + "</bold></white> <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + ">"
                                + " <gray>TPS:</gray> <" + tpsC + ">" + String.format("%.1f", avg.avgTps()) + "</" + tpsC + ">"
                                + " <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + ">"
                                + " <gray>MSPT:</gray> <" + msptC + ">" + String.format("%.1f", avg.avgMspt()) + "ms</" + msptC + ">"
                                + " <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + ">"
                                + " <gray>RAM:</gray> <" + ramC + ">" + ramPct + "%</" + ramC + ">"
                                + " <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + ">"
                                + " <gray>Speed:</gray> <" + bptC + ">" + avg.avgBpt() + "</" + bptC + "> <gray>b/t</gray>"
                                + " <" + MessageUtil.MUTED_COLOR + ">(" + avg.sampleCount() + " samples)</" + MessageUtil.MUTED_COLOR + ">"
                ));
            }
        }

        // RAM details
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  <" + MessageUtil.ACCENT_COLOR + "><bold>▸ Memory</bold></" + MessageUtil.ACCENT_COLOR + ">"
                + " <gray>" + perf.getUsedMemoryMB() + "</gray><" + MessageUtil.DIM_COLOR + ">/</" + MessageUtil.DIM_COLOR + "><white>" + perf.getMaxMemoryMB() + " MB</white>"
                + " " + perf.getRamStatusMiniMessage()));

        // Bukkit TPS
        double[] bukkitTps = org.bukkit.Bukkit.getTPS();
        player.sendMessage(msg.parse("  <" + MessageUtil.SUCCESS_COLOR + "><bold>▸ Bukkit TPS</bold></" + MessageUtil.SUCCESS_COLOR + ">"
                + " <gray>1m:</gray> <" + colorForTps(bukkitTps[0]) + ">" + String.format("%.2f", bukkitTps[0]) + "</" + colorForTps(bukkitTps[0]) + ">"
                + " <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + ">"
                + " <gray>5m:</gray> <" + colorForTps(bukkitTps[1]) + ">" + String.format("%.2f", bukkitTps[1]) + "</" + colorForTps(bukkitTps[1]) + ">"
                + " <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + ">"
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

        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse(msg.buildHeader("MCTools Help")));
        player.sendMessage(msg.parse(""));

        // 2D Shapes
        player.sendMessage(msg.parse("  " + msg.buildCategory("2D Shapes")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct cir", "<block>", "<radius>"), "Circle")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct sq", "<block>", "<size>"), "Square")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct rect", "<block>", "<radiusX>", "<radiusZ>", "[cornerRadius]"), "Rectangle")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct ell", "<block>", "<radiusX>", "<radiusZ>"), "Ellipse")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct poly", "<block>", "<radius>", "<sides>"), "Polygon (3-12 sides)")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct star", "<block>", "<radius>", "<thickness>"), "Star")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct line", "<block>", "<length>", "<thickness>"), "Line")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct spi", "<block>", "<radius>", "<turns>", "<thickness>"), "Spiral")));

        // 3D Shapes
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("3D Shapes")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct sph", "<block>", "<radius>"), "Sphere")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct dome", "<block>", "<radius>"), "Dome")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct cyl", "<block>", "<height>", "<radius>"), "Cylinder")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct cone", "<block>", "<height>", "<radius>"), "Cone")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct pyr", "<block>", "<height>", "<radius>"), "Pyramid")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct arch", "<block>", "<height>", "<radius>", "<width>"), "Arch")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct torus", "<block>", "<radius>", "<radius>"), "Torus")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct helix", "<block>", "<height>", "<radius>", "<turns>", "<thickness>"), "Helix")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct wall", "<block>", "<width>", "<height>", "<thickness>"), "Wall")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct tube", "<block>", "<radius>", "<height>", "<radius>"), "Tube")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct capsule", "<block>", "<radius>", "<height>"), "Capsule")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct ellipsoid", "<block>", "<radius>", "<height>", "<radius>"), "Ellipsoid")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct scyl", "<block>", "<radius>", "<sections>", "<sectionBlock>"), "Section Cylinder")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct stair", "<block>", "<radius>", "<height>", "<stepWidth>"), "Spiral Staircase")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct roof", "<block>", "<width>", "<length>", "<pitch>", "<style>"), "Roof (peaked/hip/flat)")));

        // Scene (composite)
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Composite Shapes")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct scene", "<block>", "<shape>", "<params>", "[+offset x y z ...]"), "Combine multiple shapes")));
        player.sendMessage(msg.parse(msg.buildHint("Example: /mct scene stone cylinder 20 5 +offset 0 20 0 cone 8 6")));

        // Gradient Shapes
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Gradient Shapes") + " <" + MessageUtil.TEXT_COLOR + ">(prefix shape with 'g')</" + MessageUtil.TEXT_COLOR + ">"));
        player.sendMessage(msg.parse("  " + msg.buildSyntax("mct g<shape> <#hex1,#hex2,...> <params> [-dir y|x|z|radial] [-interp oklab|lab|rgb|hsl]")));
        player.sendMessage(msg.parse(msg.buildHint("Example: /mct gsph #ff0000,#0000ff 20 -dir radial")));

        // Tree Generator
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Tree Generator")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct tree", "<woodType>", "[options]"), "Generate custom tree")));
        player.sendMessage(msg.parse(msg.buildHint("Options: seed: th: tr: bd: fd: fr: -roots -special")));
        player.sendMessage(msg.parse(msg.buildHint("Types: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped")));

        // Utility
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Utility")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct center"), "Auto-detect nearby structure center")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct performance"), "Server performance report")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct undo", "[count]"), "Undo last operation(s)")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct redo", "[count]"), "Redo undone operation(s)")));

        // Schematics
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Schematics")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct schematic list"), "List available schematics")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct schematic load", "<name>"), "Load a schematic")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct schematic info"), "Info about loaded schematic")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct schematic rotate", "<degrees>"), "Rotate schematic (90/180/270)")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct schematic unload"), "Unload current schematic")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct schematic remove", "<name>"), "Delete a schematic file")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct paste", "[-a]", "[-p]"), "Paste schematic")));

        // Path Tools
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Path Tools") + " <" + MessageUtil.DIM_COLOR + ">(</>" + "<" + MessageUtil.ROAD_COLOR + ">Road</" + MessageUtil.ROAD_COLOR + "> <" + MessageUtil.DIM_COLOR + ">/</" + MessageUtil.DIM_COLOR + "> <" + MessageUtil.BRIDGE_COLOR + ">Bridge</" + MessageUtil.BRIDGE_COLOR + "> <" + MessageUtil.DIM_COLOR + ">/</" + MessageUtil.DIM_COLOR + "> <" + MessageUtil.CURVE_COLOR + ">Curve</" + MessageUtil.CURVE_COLOR + "><" + MessageUtil.DIM_COLOR + ">)</" + MessageUtil.DIM_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct tool enable|disable"), "Enable/disable shovel selection")));
        player.sendMessage(msg.parse("  " + msg.buildSyntax("mct mode") + " <" + MessageUtil.ROAD_COLOR + ">road</" + MessageUtil.ROAD_COLOR + "><" + MessageUtil.DIM_COLOR + ">|</" + MessageUtil.DIM_COLOR + "><" + MessageUtil.BRIDGE_COLOR + ">bridge</" + MessageUtil.BRIDGE_COLOR + "><" + MessageUtil.DIM_COLOR + ">|</" + MessageUtil.DIM_COLOR + "><" + MessageUtil.CURVE_COLOR + ">curve</" + MessageUtil.CURVE_COLOR + "> <" + MessageUtil.DIM_COLOR + ">—</" + MessageUtil.DIM_COLOR + "> <" + MessageUtil.TEXT_COLOR + ">Set generator mode</" + MessageUtil.TEXT_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct pos list|undo|clear"), "Manage selected positions")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct set", "[key]", "[value]"), "View/change mode settings")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct preview on|off"), "Toggle particle preview")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct generate"), "Generate structure along path")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct particles on|off"), "Toggle selection particles")));
        player.sendMessage(msg.parse(msg.buildHint("Workflow: tool enable → select mode → click points → preview → generate")));
        player.sendMessage(msg.parse("    <" + MessageUtil.MUTED_COLOR + ">Detailed help: </" + MessageUtil.MUTED_COLOR + "><" + MessageUtil.INFO_COLOR + "><click:run_command:'/mct help path'><hover:show_text:'Click for path tools help'>/mct help path</hover></click></" + MessageUtil.INFO_COLOR + ">"));

        // Admin
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Admin")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct cancel"), "Cancel current operation")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct pause"), "Pause current operation")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct resume"), "Resume paused operation")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct reload"), "Reload configuration")));
        player.sendMessage(msg.parse(msg.buildHelpEntry(msg.buildSyntax("mct info"), "Plugin info")));
        player.sendMessage(msg.parse(""));
    }

    private void sendShapeHelp(Player player, String shape) {
        MessageUtil msg = plugin.getMessageUtil();

        record ShapeInfo(String name, String syntax, String description) {}

        ShapeInfo info = switch (shape.toLowerCase()) {
            case "cir", "circle" -> new ShapeInfo("Circle", "/mct cir <block> <radius>", "Creates a filled circle at your position.");
            case "sq", "square" -> new ShapeInfo("Square", "/mct sq <block> <size>", "Creates a filled square at your position.");
            case "rect", "rectangle" -> new ShapeInfo("Rectangle", "/mct rect <block> <radiusX> <radiusZ> [cornerRadius]", "Creates a rectangle. Optional corner radius for rounded corners.");
            case "ell", "ellipse" -> new ShapeInfo("Ellipse", "/mct ell <block> <radiusX> <radiusZ>", "Creates an ellipse with different X and Z radii.");
            case "poly", "polygon" -> new ShapeInfo("Polygon", "/mct poly <block> <radius> <sides>", "Creates a regular polygon (3-12 sides).");
            case "star" -> new ShapeInfo("Star", "/mct star <block> <radius> <thickness>", "Creates a 5-pointed star.");
            case "line" -> new ShapeInfo("Line", "/mct line <block> <length> <thickness>", "Creates a line in the direction you're facing.");
            case "spi", "spiral" -> new ShapeInfo("Spiral", "/mct spi <block> <radius> <turns> <thickness>", "Creates a 2D spiral.");
            case "sph", "sphere" -> new ShapeInfo("Sphere", "/mct sph <block> <radius>", "Creates a filled sphere.");
            case "dome" -> new ShapeInfo("Dome", "/mct dome <block> <radius>", "Creates a half-sphere (dome).");
            case "cyl", "cylinder" -> new ShapeInfo("Cylinder", "/mct cyl <block> <height> <radius>", "Creates a filled cylinder.");
            case "cone" -> new ShapeInfo("Cone", "/mct cone <block> <height> <radius>", "Creates a cone.");
            case "pyr", "pyramid" -> new ShapeInfo("Pyramid", "/mct pyr <block> <height> <radius>", "Creates a square-based pyramid.");
            case "arch" -> new ShapeInfo("Arch", "/mct arch <block> <height> <radius> <width>", "Creates an arch.");
            case "torus" -> new ShapeInfo("Torus", "/mct torus <block> <majorRadius> <minorRadius>", "Creates a donut shape.");
            case "helix" -> new ShapeInfo("Helix", "/mct helix <block> <height> <radius> <turns> <thickness>", "Creates a 3D spiral.");
            case "wall" -> new ShapeInfo("Wall", "/mct wall <block> <width> <height> <thickness>", "Creates a wall.");
            case "tube" -> new ShapeInfo("Tube", "/mct tube <block> <radius> <height> <innerRadius>", "Creates a tube with inner and outer radius.");
            case "capsule" -> new ShapeInfo("Capsule", "/mct capsule <block> <radius> <height>", "Creates a capsule (cylinder with dome ends).");
            case "ellipsoid" -> new ShapeInfo("Ellipsoid", "/mct ellipsoid <block> <radiusX> <radiusY> <radiusZ>", "Creates an ellipsoid with different radii.");
            case "tree" -> new ShapeInfo("Tree", "/mct tree <woodType> [options]", "Generates a custom tree. Options: seed: th: tr: bd: fd: fr: -roots -special");
            case "scyl", "sectioncylinder" -> new ShapeInfo("Section Cylinder", "/mct scyl <block> <radius> <sections> <sectionBlock>", "Creates a flat disc divided into equal angular sections with divider lines.");
            case "stair", "staircase" -> new ShapeInfo("Staircase", "/mct stair <block> <radius> <height> <stepWidth>", "Creates a spiral staircase.");
            case "roof" -> new ShapeInfo("Roof", "/mct roof <block> <width> <length> <pitch> <style>", "Creates a roof. Styles: peaked, hip, flat. Pitch: 0.1-3.0 (1.0 = 45 degrees).");
            case "scene" -> new ShapeInfo("Scene", "/mct scene <shape> <block_or_colors> <params> [flags] [+offset x y z ...] ...", "Combines multiple shapes (solid or gradient) into one operation. Max 10 shapes.");
            default -> null;
        };

        if (info != null) {
            player.sendMessage(msg.parse(""));
            player.sendMessage(msg.parse(msg.buildHeader(info.name)));
            player.sendMessage(msg.parse(""));
            player.sendMessage(msg.parse("  <" + MessageUtil.TEXT_COLOR + ">Syntax:</> <" + MessageUtil.CMD_COLOR + ">" + info.syntax + "</" + MessageUtil.CMD_COLOR + ">"));
            player.sendMessage(msg.parse("  <" + MessageUtil.TEXT_COLOR + ">" + info.description + "</" + MessageUtil.TEXT_COLOR + ">"));
            player.sendMessage(msg.parse(""));
        } else {
            msg.sendError(player, "Unknown shape: " + shape);
            msg.sendInfo(player, "Use /mct help for a list of shapes.");
        }
    }

    private void sendInfo(Player player) {
        MessageUtil msg = plugin.getMessageUtil();

        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("              <gradient:#34d399:#10b981><bold>M C T O O L S</bold></gradient>"));
        player.sendMessage(msg.parse("                 <" + MessageUtil.MUTED_COLOR + ">version</" + MessageUtil.MUTED_COLOR + "> <white>" + plugin.getDescription().getVersion() + "</white>"));
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("       <" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + "> <" + MessageUtil.ERROR_COLOR + "><bold><click:open_url:'https://github.com/PenguinStudiosOrganization/MCTools/releases/'><hover:show_text:'<white>Open GitHub Releases</white>'>[GitHub]</hover></click></bold></" + MessageUtil.ERROR_COLOR + "> " +
                                     "<" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + "> <" + MessageUtil.INFO_COLOR + "><bold><click:open_url:'https://discord.penguinstudios.eu/'><hover:show_text:'<white>Join our Discord</white>'>[Discord]</hover></click></bold></" + MessageUtil.INFO_COLOR + "> " +
                                     "<" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + "> <" + MessageUtil.SUCCESS_COLOR + "><bold><click:open_url:'https://penguinstudios.eu/'><hover:show_text:'<white>Visit PenguinStudios</white>'>[Website]</hover></click></bold></" + MessageUtil.SUCCESS_COLOR + "> " +
                                     "<" + MessageUtil.DIM_COLOR + ">│</" + MessageUtil.DIM_COLOR + ">"));
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("           <" + MessageUtil.MUTED_COLOR + ">a</" + MessageUtil.MUTED_COLOR + "> <gradient:#38bdf8:#2563eb>PenguinStudios</gradient> <" + MessageUtil.MUTED_COLOR + ">product</" + MessageUtil.MUTED_COLOR + ">"));
        player.sendMessage(msg.parse("          <" + MessageUtil.MUTED_COLOR + ">developed by</" + MessageUtil.MUTED_COLOR + "> <white>D3xts</white> <" + MessageUtil.MUTED_COLOR + ">&</" + MessageUtil.MUTED_COLOR + "> <white>Raffymimi</white>"));
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("     <" + MessageUtil.MUTED_COLOR + "><italic>Not affiliated with Mojang AB or Microsoft.</italic></" + MessageUtil.MUTED_COLOR + ">"));
        player.sendMessage(msg.parse(""));
    }
    
    /**
     * Handles /mct schematic list|load|unload|remove
     */
    private void handleSchematicCommand(Player player, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        SchematicManager sm = plugin.getSchematicManager();

        if (!player.hasPermission("mctools.admin")) {
            msg.sendError(player, "You don't have permission to manage schematics!");
            return;
        }

        if (args.length < 2) {
            msg.sendUsage(player, "/mct schematic <list|load|info|rotate|unload|remove> [name]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list" -> {
                List<SchematicManager.SchematicInfo> schematics = sm.listSchematics();
                if (schematics.isEmpty()) {
                    player.sendMessage(msg.parse(""));
                    player.sendMessage(msg.parse(msg.buildHeader("Schematics", "#a855f7", "#7c3aed")));
                    player.sendMessage(msg.parse(""));
                    player.sendMessage(msg.parse("  <" + MessageUtil.TEXT_COLOR + ">No schematics found.</" + MessageUtil.TEXT_COLOR + ">"));
                    player.sendMessage(msg.parse("  <" + MessageUtil.MUTED_COLOR + ">Place .schem or .schematic files in:</" + MessageUtil.MUTED_COLOR + ">"));
                    player.sendMessage(msg.parse("  <dark_gray>" + sm.getSchematicsFolder().getPath() + "</dark_gray>"));
                    player.sendMessage(msg.parse(""));
                    return;
                }

                long totalSize = schematics.stream().mapToLong(SchematicManager.SchematicInfo::sizeBytes).sum();

                player.sendMessage(msg.parse(""));
                player.sendMessage(msg.parse(msg.buildHeader("Schematics", "#a855f7", "#7c3aed")
                        + " <dark_gray>(" + schematics.size() + " file" + (schematics.size() != 1 ? "s" : "")
                        + ", " + SchematicManager.formatSize(totalSize) + " total)</dark_gray>"));
                player.sendMessage(msg.parse(""));

                for (SchematicManager.SchematicInfo info : schematics) {
                    String nameNoExt = info.name().contains(".")
                            ? info.name().substring(0, info.name().lastIndexOf('.'))
                            : info.name();
                    String size = SchematicManager.formatSize(info.sizeBytes());

                    player.sendMessage(msg.parse(
                            "  <" + MessageUtil.ACCENT_COLOR + ">-</" + MessageUtil.ACCENT_COLOR + "> <white>" + nameNoExt + "</white>"
                            + " <dark_gray>(" + size + ")</dark_gray> "
                            + "<" + MessageUtil.SUCCESS_COLOR + "><bold><click:suggest_command:'/mct schematic load " + nameNoExt + "'>"
                            + "<hover:show_text:'Load schematic " + nameNoExt + "'>[load]</hover></click></bold></" + MessageUtil.SUCCESS_COLOR + ">"
                    ));
                }

                player.sendMessage(msg.parse(""));
                player.sendMessage(msg.parse("  <" + MessageUtil.MUTED_COLOR + ">Click [load] to load, then /mct paste to place.</" + MessageUtil.MUTED_COLOR + ">"));
                player.sendMessage(msg.parse(""));
            }
            case "load" -> {
                if (args.length < 3) {
                    msg.sendUsage(player, "/mct schematic load <name>");
                    return;
                }
                String name = args[2];
                if (sm.loadSchematic(player, name)) {
                    SchematicManager.LoadedSchematic loaded = sm.getLoaded(player);

                    player.sendMessage(msg.parse(""));
                    player.sendMessage(msg.parse(msg.buildHeader("Schematic Loaded")));
                    player.sendMessage(msg.parse(""));
                    player.sendMessage(msg.parse(msg.buildDetail("Name", loaded.name())
                            + " <dark_gray>|</dark_gray> "
                            + "<" + MessageUtil.TEXT_COLOR + ">Size:</> <white>" + SchematicManager.formatSize(loaded.sizeBytes()) + "</white>"));
                    player.sendMessage(msg.parse(msg.buildDetail("Dimensions", loaded.sizeX() + " x " + loaded.sizeY() + " x " + loaded.sizeZ() + " blocks")));
                    player.sendMessage(msg.parse(""));
                    player.sendMessage(msg.parse("  <" + MessageUtil.MUTED_COLOR + ">Run</> <" + MessageUtil.WARN_COLOR + "><click:suggest_command:'/mct paste'>"
                            + "<hover:show_text:'Click to paste'>/mct paste</hover></click></" + MessageUtil.WARN_COLOR + "> <" + MessageUtil.MUTED_COLOR + ">to place at your position.</>"));
                    player.sendMessage(msg.parse("  <" + MessageUtil.MUTED_COLOR + ">Use /mct paste -a to skip air, -p to skip preview.</>"));
                    player.sendMessage(msg.parse(""));
                } else {
                    msg.sendError(player, "Schematic '" + name + "' not found!");
                    msg.sendInfo(player, "Use /mct schematic list to see available files.");
                }
            }
            case "unload" -> {
                if (sm.unloadSchematic(player)) {
                    player.sendMessage(msg.parse(
                            "<#f97316>\u2716</#f97316> <gray>Schematic</gray> <white>unloaded</white><gray>.</gray>"));
                } else {
                    msg.sendError(player, "No schematic loaded!");
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    msg.sendUsage(player, "/mct schematic remove <name>");
                    return;
                }
                String name = args[2];
                if (sm.removeSchematic(name)) {
                    player.sendMessage(msg.parse(
                            "<red><bold>\ud83d\uddd1</bold></red> <gray>Schematic</gray> <white>" + name + "</white> <red>deleted</red> <gray>from disk.</gray>"));
                } else {
                    msg.sendError(player, "Schematic '" + name + "' not found!");
                }
            }
            case "info" -> {
                SchematicManager.LoadedSchematic loaded = sm.getLoaded(player);
                if (loaded == null) {
                    msg.sendError(player, "No schematic loaded! Use /mct schematic load <name>");
                    return;
                }

                player.sendMessage(msg.parse(""));
                player.sendMessage(msg.parse(
                        "<gradient:#a855f7:#6d28d9><bold>\ud83d\udccb Schematic Info</bold></gradient>"));
                player.sendMessage(msg.parse(""));
                player.sendMessage(msg.parse(
                        "  <gray>Name:</gray>       <white><bold>" + loaded.name() + "</bold></white>"));
                player.sendMessage(msg.parse(
                        "  <gray>File size:</gray>  <white>" + SchematicManager.formatSize(loaded.sizeBytes()) + "</white>"));
                player.sendMessage(msg.parse(
                        "  <gray>Dimensions:</gray> <aqua>" + loaded.sizeX() + "</aqua>"
                        + " <dark_gray>\u00d7</dark_gray> <aqua>" + loaded.sizeY() + "</aqua>"
                        + " <dark_gray>\u00d7</dark_gray> <aqua>" + loaded.sizeZ() + "</aqua>"
                        + " <gray>blocks</gray>"));
                player.sendMessage(msg.parse(
                        "  <gray>Volume:</gray>     <white>" + String.format("%,d", (long) loaded.sizeX() * loaded.sizeY() * loaded.sizeZ())
                        + "</white> <gray>blocks (bounding box)</gray>"));
                player.sendMessage(msg.parse(""));
                player.sendMessage(msg.parse(
                        "  <yellow><click:suggest_command:'/mct paste'><hover:show_text:'<yellow>Paste schematic</yellow>'>"
                        + "[\u25b6 Paste]</hover></click></yellow>"
                        + " <dark_gray>\u2502</dark_gray> "
                        + "<yellow><click:suggest_command:'/mct paste -a'><hover:show_text:'<yellow>Paste without air</yellow>'>"
                        + "[\u25b6 Paste -a]</hover></click></yellow>"
                        + " <dark_gray>\u2502</dark_gray> "
                        + "<red><click:run_command:'/mct schematic unload'><hover:show_text:'<red>Unload schematic</red>'>"
                        + "[\u2716 Unload]</hover></click></red>"));
                player.sendMessage(msg.parse(""));
            }
            case "rotate" -> {
                if (args.length < 3) {
                    msg.sendUsage(player, "/mct schematic rotate <degrees>");
                    msg.sendInfo(player, "Degrees must be a multiple of 90 (e.g. 90, 180, 270)");
                    return;
                }
                int degrees;
                try {
                    degrees = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    msg.sendError(player, "Invalid degrees: " + args[2]);
                    return;
                }
                if (degrees % 90 != 0) {
                    msg.sendError(player, "Degrees must be a multiple of 90!");
                    return;
                }
                if (sm.getLoaded(player) == null) {
                    msg.sendError(player, "No schematic loaded! Use /mct schematic load <name>");
                    return;
                }
                if (sm.rotateSchematic(player, degrees)) {
                    SchematicManager.LoadedSchematic rotated = sm.getLoaded(player);
                    int normalized = ((degrees % 360) + 360) % 360;
                    player.sendMessage(msg.parse(""));
                    player.sendMessage(msg.parse(
                            "<gradient:#f59e0b:#d97706><bold>\u21bb Schematic Rotated</bold></gradient>"));
                    player.sendMessage(msg.parse(
                            "  <gray>Name:</gray> <white><bold>" + rotated.name() + "</bold></white>"
                            + " <dark_gray>\u2502</dark_gray>"
                            + " <gray>Rotation:</gray> <yellow>" + normalized + "\u00b0</yellow>"));
                    player.sendMessage(msg.parse(
                            "  <gray>New dimensions:</gray> <aqua>" + rotated.sizeX() + "</aqua>"
                            + "<dark_gray>\u00d7</dark_gray><aqua>" + rotated.sizeY() + "</aqua>"
                            + "<dark_gray>\u00d7</dark_gray><aqua>" + rotated.sizeZ() + "</aqua>"
                            + " <gray>blocks</gray>"));
                    player.sendMessage(msg.parse(""));
                    player.sendMessage(msg.parse(
                            "  <dark_gray>\u21b3</dark_gray> <gray>Run</gray> <yellow><click:suggest_command:'/mct paste'>"
                            + "<hover:show_text:'<yellow>Click to paste</yellow>'>/mct paste</hover></click></yellow>"
                            + " <gray>to place at your position.</gray>"));
                    player.sendMessage(msg.parse(""));
                } else {
                    msg.sendError(player, "Failed to rotate schematic!");
                }
            }
            default -> msg.sendUsage(player, "/mct schematic <list|load|info|rotate|unload|remove> [name]");
        }
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

    /**
     * Gives the player a path tool shovel and auto-enables the tool.
     * Uses the first allowed shovel material from config.
     */
    private void givePathShovel(Player player) {
        MessageUtil msg = plugin.getMessageUtil();

        // Get the first allowed shovel material from config
        Material shovelMat = plugin.getPathToolManager().getAllowedShovels().stream()
                .findFirst().orElse(Material.WOODEN_SHOVEL);

        ItemStack shovel = new ItemStack(shovelMat);
        ItemMeta meta = shovel.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§b§l⛏ §fPath Tool §7§o(MCTools)");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7━━━━━━━━━━━━━━━━━━━━━━��━━━━━");
            lore.add("§f§lPATH SELECTION TOOL");
            lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            lore.add("");
            lore.add("§e⚡ §fLeft-click §7to set Pos1 (reset path)");
            lore.add("§e⚡ §fRight-click §7to add next position");
            lore.add("");
            lore.add("§b§lModes:");
            lore.add("§7• §fRoad §7- Generate roads along path");
            lore.add("§7• §fBridge §7- Generate bridges along path");
            lore.add("§7• §fCurve §7- Preview smooth curves");
            lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            meta.setLore(lore);

            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            shovel.setItemMeta(meta);
        }

        player.getInventory().addItem(shovel);

        // Auto-enable the tool
        plugin.getPathToolManager().handleToolToggle(player, true);

        msg.sendInfo(player, "You received the Path Tool Shovel!");
        msg.sendInfo(player, "Left-click = set Pos1, Right-click = add point. Use /mct help path for commands.");
    }
}
