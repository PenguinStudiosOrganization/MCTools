package com.mctools.commands;

import com.mctools.MCTools;
import com.mctools.gradient.GradientApplier;
import com.mctools.gradient.GradientEngine;
import com.mctools.shapes.*;
import com.mctools.utils.BlockPlacer;
import com.mctools.utils.ConfigManager;
import com.mctools.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Command executor for {@code /mct} (alias {@code /mctools}).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Parse sub-commands (shapes, admin, gradient, tree).</li>
 *   <li>Validate arguments and permissions.</li>
 *   <li>Delegate block placement to {@link BlockPlacer}.</li>
 *   <li>Provide user feedback (help, errors, suggestions).</li>
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>Cooldowns are tracked per-player to prevent spam.</li>
 *   <li>Shape creation is delegated to {@link Shape} subclasses.</li>
 *   <li>Gradient commands use {@link GradientEngine} for color interpolation.</li>
 * </ul>
 */
public class MCToolsCommand implements CommandExecutor {

    private static final Pattern HEX_COLORS_PATTERN =
        Pattern.compile("^#?[0-9a-fA-F]{6}(,#?[0-9a-fA-F]{6}){1,5}$");

    private final MCTools plugin;
    private final Map<UUID, Long> cooldowns;
    private final GradientEngine gradientEngine;
    private final GradientApplier gradientApplier;

    /**
     * Creates a new MCToolsCommand instance.
     *
     * @param plugin The plugin instance
     */
    public MCToolsCommand(MCTools plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.gradientEngine = new GradientEngine();
        this.gradientApplier = new GradientApplier();
    }
    
    /**
     * Gets the shared BlockPlacer instance from the plugin.
     */
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

        String subCommand = args[0].toLowerCase();

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
            case "cancel" -> {
                boolean cancelled = false;
                BlockPlacer blockPlacer = getBlockPlacer();
                
                // Cancel and restore placement task (includes already placed blocks)
                if (blockPlacer.cancelTask(player)) {
                    cancelled = true;
                }
                
                // Cancel and restore preview
                if (blockPlacer.cancelPreview(player)) {
                    cancelled = true;
                }
                
                // Also cancel terrain brush preview
                if (plugin.getBrushManager().getTerrainBrush().hasActivePreview(player)) {
                    plugin.getBrushManager().getTerrainBrush().cancelPreview(player);
                    cancelled = true;
                }
                
                if (cancelled) {
                    msg.sendSuccess(player, "Operation", 0);
                    msg.sendInfo(player, "§aOperation cancelled and all blocks restored!");
                } else {
                    msg.sendError(player, "No active operation to cancel!");
                }
                return true;
            }
            case "pause" -> {
                BlockPlacer blockPlacer = getBlockPlacer();
                boolean paused = blockPlacer.pauseTask(player);
                // Also pause terrain brush
                if (plugin.getBrushManager().getTerrainBrush().pausePreview(player)) {
                    paused = true;
                }
                if (paused) {
                    msg.sendInfo(player, "§ePaused! §7Use §f/mct resume §7to continue.");
                } else {
                    msg.sendError(player, "No active operation to pause!");
                }
                return true;
            }
            case "resume" -> {
                BlockPlacer blockPlacer = getBlockPlacer();
                boolean resumed = blockPlacer.resumeTask(player);
                // Also resume terrain brush
                if (plugin.getBrushManager().getTerrainBrush().resumePreview(player)) {
                    resumed = true;
                }
                if (resumed) {
                    msg.sendInfo(player, "§aResumed!");
                } else {
                    msg.sendError(player, "No paused operation to resume!");
                }
                return true;
            }
        }

        // Check cooldown
        if (!checkCooldown(player)) {
            return true;
        }

        // Check if player already has an active task
        if (getBlockPlacer().hasActiveTask(player)) {
            msg.sendWarning(player, "You already have an active operation! Use /mct cancel to stop it.");
            return true;
        }

        // Handle shape commands
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
     * Checks if the player is on cooldown.
     */
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

    /**
     * Handles shape generation commands.
     */
    private void handleShapeCommand(Player player, String shapeCmd, String[] args) {
        // Check if this is a tree command
        if (shapeCmd.equals("tree")) {
            handleTreeCommand(player, args);
            return;
        }

        // Check if this is a gradient command
        if (shapeCmd.startsWith("g")) {
            handleGradientCommand(player, shapeCmd, args);
            return;
        }

        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        // Parse block type (always second argument for shapes)
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
            return; // Error already sent
        }

        // Check permission
        if (!player.hasPermission(shape.getPermission())) {
            msg.sendError(player, "You don't have permission to create " + shape.getName() + "s!");
            return;
        }

        // Check estimated block count
        int estimated = shape.getEstimatedBlockCount();
        int maxBlocks = config.getMaxBlocks();

        if (maxBlocks > 0 && estimated > maxBlocks && !player.hasPermission("mctools.bypass.limit")) {
            msg.sendError(player, "Operation would place ~" + estimated + " blocks (max: " + maxBlocks + ")");
            return;
        }

        // Generate and place blocks
        Location center = player.getLocation();
        List<Location> blocks = shape.generate(center);

        if (blocks.isEmpty()) {
            msg.sendError(player, "No blocks to place!");
            return;
        }

        // Final block count check
        if (maxBlocks > 0 && blocks.size() > maxBlocks && !player.hasPermission("mctools.bypass.limit")) {
            msg.sendError(player, "Operation would place " + blocks.size() + " blocks (max: " + maxBlocks + ")");
            return;
        }

        msg.sendInfo(player, "Preparing " + shape.getName() + " with " + blocks.size() + " blocks...");
        getBlockPlacer().placeBlocks(player, blocks, blockData, shape.getName());
    }

    /**
     * Handles gradient shape commands (g-prefixed).
     */
    private void handleGradientCommand(Player player, String cmd, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        // Permission check
        if (!player.hasPermission("mctools.gradient")) {
            msg.sendError(player, "You don't have permission to use gradient shapes!");
            return;
        }

        // Strip 'g' prefix to get base command: "gsph" -> "sph", "ghsph" -> "hsph"
        String baseCmd = cmd.substring(1);

        // Validate minimum args: /mct g<shape> <colors> <params...>
        if (args.length < 3) {
            msg.sendUsage(player, "/mct " + cmd + " <#hex1,#hex2,...> <params...> [-dir y|x|z|radial] [-interp oklab|lab|rgb|hsl] [-unique]");
            return;
        }

        // Parse and validate hex colors
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

        // Scan for optional flags and separate them from positional args
        String direction = "y";
        String interpolation = "oklab";
        boolean uniqueOnly = false;
        List<String> positionalArgs = new ArrayList<>();
        positionalArgs.add(args[0]); // original command (not used by createShape directly)

        // args[1] is colors, skip it; positionalArgs[1] needs to be a placeholder for block
        positionalArgs.add("stone"); // placeholder block (not used for gradient but createShape needs it at index 1)

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

        // Create shape using existing createShape
        Shape shape = createShape(player, baseCmd, rebuiltArgs);
        if (shape == null) {
            return;
        }

        if (!player.hasPermission(shape.getPermission())) {
            msg.sendError(player, "You don't have permission to create " + shape.getName() + "s!");
            return;
        }

        // Generate locations
        Location center = player.getLocation();
        List<Location> blocks = shape.generate(center);

        if (blocks.isEmpty()) {
            msg.sendError(player, "No blocks to place!");
            return;
        }

        int maxBlocks = config.getMaxBlocks();
        if (maxBlocks > 0 && blocks.size() > maxBlocks && !player.hasPermission("mctools.bypass.limit")) {
            msg.sendError(player, "Operation would place " + blocks.size() + " blocks (max: " + maxBlocks + ")");
            return;
        }

        // For 2D shapes with -dir y, auto-fallback to radial
        if (direction.equals("y") && gradientApplier.is2DShape(blocks)) {
            direction = "radial";
        }

        // Count distinct axis values for numSteps
        int distinctValues = gradientApplier.countDistinctAxisValues(blocks, center, direction);
        int numSteps = Math.max(2, Math.min(distinctValues, 50));

        // Generate gradient
        List<GradientEngine.GradientBlock> gradientSteps =
            gradientEngine.generateGradient(hexColors, numSteps, interpolation, uniqueOnly);

        if (gradientSteps.isEmpty()) {
            msg.sendError(player, "Failed to generate gradient!");
            return;
        }

        // Apply gradient to positions
        Map<Location, BlockData> blockMap =
            gradientApplier.applyGradient(blocks, center, gradientSteps, direction);

        // Count unique block types
        long uniqueBlockCount = blockMap.values().stream()
            .map(bd -> bd.getMaterial().name())
            .distinct()
            .count();

        msg.sendInfo(player, "Gradient " + shape.getName() + ": " + numSteps + " steps, "
            + uniqueBlockCount + " block types, " + blocks.size() + " total blocks");

        getBlockPlacer().placeGradientBlocks(player, blockMap, "Gradient " + shape.getName());
    }

    /**
     * Handles tree generation command.
     * Format: /mct tree <woodType> seed:<int> th:<int> tr:<int> bd:<float> fd:<float> fr:<int> [-roots] [-special]
     */
    private void handleTreeCommand(Player player, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        ConfigManager config = plugin.getConfigManager();

        if (args.length < 2) {
            msg.sendUsage(player, "/mct tree <woodType> seed:<seed> th:<trunkHeight> tr:<trunkRadius> bd:<branchDensity> fd:<foliageDensity> fr:<foliageRadius> [-roots] [-special]");
            msg.sendInfo(player, "Wood types: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped");
            return;
        }

        // Parse wood type (positional, always first)
        TreeGenerator.WoodType woodType;
        try {
            woodType = TreeGenerator.WoodType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            msg.sendError(player, "Invalid wood type: " + args[1]);
            msg.sendInfo(player, "Valid: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped");
            return;
        }

        // Permission check
        if (!player.hasPermission("mctools.shapes.tree")) {
            msg.sendError(player, "You don't have permission to generate trees!");
            return;
        }

        // Default values
        long seed = System.currentTimeMillis();
        int trunkHeight = 12;
        int trunkRadius = 2;
        double branchDensity = 0.7;
        double foliageDensity = 0.8;
        int foliageRadius = 6;
        boolean enableRoots = false;
        boolean useSpecialBlocks = false;

        // Parse key:value and flag arguments
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

        // Check block limit
        int estimated = generator.getEstimatedBlockCount();
        int maxBlocks = config.getMaxBlocks();
        if (maxBlocks > 0 && estimated > maxBlocks && !player.hasPermission("mctools.bypass.limit")) {
            msg.sendError(player, "Tree would place ~" + estimated + " blocks (max: " + maxBlocks + ")");
            return;
        }

        Location center = player.getLocation();
        Map<Location, BlockData> blocks = generator.generate(center);

        if (blocks.isEmpty()) {
            msg.sendError(player, "No blocks generated!");
            return;
        }

        // Final block count check
        if (maxBlocks > 0 && blocks.size() > maxBlocks && !player.hasPermission("mctools.bypass.limit")) {
            msg.sendError(player, "Tree would place " + blocks.size() + " blocks (max: " + maxBlocks + ")");
            return;
        }

        msg.sendInfo(player, "Generating " + woodType.name().toLowerCase() + " tree with " + blocks.size() + " blocks (seed: " + seed + ")");
        getBlockPlacer().placeGradientBlocks(player, blocks, generator.getName());
    }

    /**
     * Creates a shape based on the command and arguments.
     */
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
                    int thickness = parseInt(args[3], "thickness");
                    yield new Circle(radius, true, thickness);
                }
                case "hsq" -> {
                    requireArgs(args, 4, "/mct hsq <block> <size> <thickness>");
                    int size = parseSize(args[2], config);
                    int thickness = parseInt(args[3], "thickness");
                    yield new Square(size, true, thickness);
                }
                case "hrect" -> {
                    requireArgs(args, 5, "/mct hrect <block> <radiusX> <radiusZ> <thickness> OR with cornerRadius");
                    int radiusX = parseRadius(args[2], config);
                    int radiusZ = parseRadius(args[3], config);
                    int cornerRadius, thickness;
                    if (args.length > 5) {
                        cornerRadius = parseInt(args[4], "cornerRadius");
                        thickness = parseInt(args[5], "thickness");
                    } else {
                        cornerRadius = 0;
                        thickness = parseInt(args[4], "thickness");
                    }
                    yield new Rectangle(radiusX, radiusZ, cornerRadius, true, thickness);
                }
                case "hell" -> {
                    requireArgs(args, 5, "/mct hell <block> <radiusX> <radiusZ> <thickness>");
                    int rx = parseRadius(args[2], config);
                    int rz = parseRadius(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    yield new Ellipse(rx, rz, true, thickness);
                }
                case "hpoly" -> {
                    requireArgs(args, 5, "/mct hpoly <block> <radius> <sides> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int sides = parseInt(args[3], "sides");
                    int thickness = parseInt(args[4], "thickness");
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
                    int radius = parseRadius(args[3], config);
                    int width = parseSize(args[4], config);
                    yield new Arch(legHeight, radius, width);
                }
                case "tor" -> {
                    requireArgs(args, 4, "/mct tor <block> <majorRadius> <minorRadius>");
                    int majorR = parseRadius(args[2], config);
                    int minorR = parseRadius(args[3], config);
                    yield new Torus(majorR, minorR);
                }
                case "wall" -> {
                    requireArgs(args, 5, "/mct wall <block> <width> <height> <thickness>");
                    int width = parseSize(args[2], config);
                    int height = parseHeight(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    yield new Wall(width, height, thickness);
                }
                case "hel" -> {
                    requireArgs(args, 6, "/mct hel <block> <height> <radius> <turns> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int turns = parseInt(args[4], "turns");
                    int thickness = parseInt(args[5], "thickness");
                    yield new Helix(height, radius, turns, thickness);
                }

                // 3D Shapes - Hollow
                case "hsph" -> {
                    requireArgs(args, 4, "/mct hsph <block> <radius> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int thickness = parseInt(args[3], "thickness");
                    yield new Sphere(radius, true, thickness);
                }
                case "hdome" -> {
                    requireArgs(args, 4, "/mct hdome <block> <radius> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int thickness = parseInt(args[3], "thickness");
                    yield new Dome(radius, true, thickness);
                }
                case "hcyl" -> {
                    requireArgs(args, 5, "/mct hcyl <block> <height> <radius> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    yield new Cylinder(height, radius, true, thickness);
                }
                case "hcone" -> {
                    requireArgs(args, 5, "/mct hcone <block> <height> <radius> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    yield new Cone(height, radius, true, thickness);
                }
                case "hpyr" -> {
                    requireArgs(args, 5, "/mct hpyr <block> <height> <radius> <thickness>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    yield new Pyramid(height, radius, true, thickness);
                }
                case "harch" -> {
                    requireArgs(args, 6, "/mct harch <block> <legHeight> <radius> <width> <thickness>");
                    int legHeight = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int width = parseSize(args[4], config);
                    int thickness = parseInt(args[5], "thickness");
                    yield new Arch(legHeight, radius, width, true, thickness);
                }
                case "htor" -> {
                    requireArgs(args, 5, "/mct htor <block> <majorRadius> <minorRadius> <thickness>");
                    int majorR = parseRadius(args[2], config);
                    int minorR = parseRadius(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    yield new Torus(majorR, minorR, true, thickness);
                }

                // Ellipsoid
                case "ellipsoid" -> {
                    requireArgs(args, 5, "/mct ellipsoid <block> <radiusX> <radiusY> <radiusZ>");
                    int rx = parseRadius(args[2], config);
                    int ry = parseRadius(args[3], config);
                    int rz = parseRadius(args[4], config);
                    yield new Ellipsoid(rx, ry, rz);
                }
                case "hellipsoid" -> {
                    requireArgs(args, 6, "/mct hellipsoid <block> <radiusX> <radiusY> <radiusZ> <thickness>");
                    int rx = parseRadius(args[2], config);
                    int ry = parseRadius(args[3], config);
                    int rz = parseRadius(args[4], config);
                    int thickness = parseInt(args[5], "thickness");
                    yield new Ellipsoid(rx, ry, rz, true, thickness);
                }

                // Tube
                case "tube" -> {
                    requireArgs(args, 5, "/mct tube <block> <height> <radius> <innerRadius>");
                    int height = parseHeight(args[2], config);
                    int radius = parseRadius(args[3], config);
                    int innerRadius = parseRadius(args[4], config);
                    if (innerRadius >= radius) {
                        throw new IllegalArgumentException("Inner radius must be less than outer radius!");
                    }
                    yield new Tube(height, radius, innerRadius);
                }

                // Capsule
                case "capsule" -> {
                    requireArgs(args, 4, "/mct capsule <block> <radius> <height>");
                    int radius = parseRadius(args[2], config);
                    int height = parseHeight(args[3], config);
                    if (height < 2 * radius) {
                        throw new IllegalArgumentException("Height must be at least 2x radius for a capsule!");
                    }
                    yield new Capsule(radius, height);
                }
                case "hcapsule" -> {
                    requireArgs(args, 5, "/mct hcapsule <block> <radius> <height> <thickness>");
                    int radius = parseRadius(args[2], config);
                    int height = parseHeight(args[3], config);
                    int thickness = parseInt(args[4], "thickness");
                    if (height < 2 * radius) {
                        throw new IllegalArgumentException("Height must be at least 2x radius for a capsule!");
                    }
                    yield new Capsule(radius, height, true, thickness);
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

    /**
     * Parses block data from a string.
     */
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

    /**
     * Suggests similar block names.
     */
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

    /**
     * Sends the main help message with colored parameters.
     */
    private void sendHelp(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        msg.sendHelpHeader(player);
        
        // Color legend
        player.sendMessage(msg.parse("<gray>Colors: " +
                "<" + MessageUtil.CMD_COLOR + ">cmd</" + MessageUtil.CMD_COLOR + "> " +
                "<" + MessageUtil.BLOCK_COLOR + ">block</" + MessageUtil.BLOCK_COLOR + "> " +
                "<" + MessageUtil.RADIUS_COLOR + ">radius</" + MessageUtil.RADIUS_COLOR + "> " +
                "<" + MessageUtil.HEIGHT_COLOR + ">height</" + MessageUtil.HEIGHT_COLOR + "> " +
                "<" + MessageUtil.THICK_COLOR + ">thick</" + MessageUtil.THICK_COLOR + "></gray><newline>"));
        
        // 2D Shapes
        player.sendMessage(msg.parse("<" + MessageUtil.BLOCK_COLOR + "><bold>2D Shapes (Flat):</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        
        // Circle
        player.sendMessage(msg.parse(msg.buildSyntax("mct cir", "<block>", "<radius>") + 
                " <dark_gray>-</dark_gray> <gray>Filled circle</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hcir", "<block>", "<radius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow circle</gray>"));
        
        // Square
        player.sendMessage(msg.parse(msg.buildSyntax("mct sq", "<block>", "<size>") + 
                " <dark_gray>-</dark_gray> <gray>Filled square</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hsq", "<block>", "<size>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow square</gray>"));
        
        // Rectangle
        player.sendMessage(msg.parse(msg.buildSyntax("mct rect", "<block>", "<radiusX>", "<radiusZ>", "[cornerR]") + 
                " <dark_gray>-</dark_gray> <gray>Rectangle</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hrect", "<block>", "<radiusX>", "<radiusZ>", "[cornerR]", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow rect</gray>"));
        
        // Ellipse
        player.sendMessage(msg.parse(msg.buildSyntax("mct ell", "<block>", "<radiusX>", "<radiusZ>") + 
                " <dark_gray>-</dark_gray> <gray>Ellipse</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hell", "<block>", "<radiusX>", "<radiusZ>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow ellipse</gray>"));
        
        // Polygon
        player.sendMessage(msg.parse(msg.buildSyntax("mct poly", "<block>", "<radius>", "<sides>") + 
                " <dark_gray>-</dark_gray> <gray>Polygon (3-12)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hpoly", "<block>", "<radius>", "<sides>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow polygon</gray>"));
        
        // Other 2D
        player.sendMessage(msg.parse(msg.buildSyntax("mct star", "<block>", "<radius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>5-pointed star</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct line", "<block>", "<length>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Straight line</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct spi", "<block>", "<radius>", "<turns>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Flat spiral</gray>"));
        
        // 3D Shapes
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>3D Shapes:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        
        // Sphere
        player.sendMessage(msg.parse(msg.buildSyntax("mct sph", "<block>", "<radius>") + 
                " <dark_gray>-</dark_gray> <gray>Sphere</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hsph", "<block>", "<radius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow sphere</gray>"));
        
        // Dome
        player.sendMessage(msg.parse(msg.buildSyntax("mct dome", "<block>", "<radius>") + 
                " <dark_gray>-</dark_gray> <gray>Dome</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hdome", "<block>", "<radius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow dome</gray>"));
        
        // Cylinder
        player.sendMessage(msg.parse(msg.buildSyntax("mct cyl", "<block>", "<height>", "<radius>") + 
                " <dark_gray>-</dark_gray> <gray>Cylinder</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hcyl", "<block>", "<height>", "<radius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow cylinder</gray>"));
        
        // Cone
        player.sendMessage(msg.parse(msg.buildSyntax("mct cone", "<block>", "<height>", "<radius>") + 
                " <dark_gray>-</dark_gray> <gray>Cone</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hcone", "<block>", "<height>", "<radius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow cone</gray>"));
        
        // Pyramid
        player.sendMessage(msg.parse(msg.buildSyntax("mct pyr", "<block>", "<height>", "<radius>") + 
                " <dark_gray>-</dark_gray> <gray>Pyramid</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hpyr", "<block>", "<height>", "<radius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow pyramid</gray>"));
        
        // Arch
        player.sendMessage(msg.parse(msg.buildSyntax("mct arch", "<block>", "<legHeight>", "<radius>", "<width>") + 
                " <dark_gray>-</dark_gray> <gray>Arch</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct harch", "<block>", "<legHeight>", "<radius>", "<width>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow arch</gray>"));
        
        // Torus
        player.sendMessage(msg.parse(msg.buildSyntax("mct tor", "<block>", "<majorRadius>", "<minorRadius>") + 
                " <dark_gray>-</dark_gray> <gray>Torus (donut)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct htor", "<block>", "<majorRadius>", "<minorRadius>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Hollow torus</gray>"));
        
        // Wall
        player.sendMessage(msg.parse(msg.buildSyntax("mct wall", "<block>", "<width>", "<height>", "<thickness>") + 
                " <dark_gray>-</dark_gray> <gray>Wall</gray>"));
        
        // Helix
        player.sendMessage(msg.parse(msg.buildSyntax("mct hel", "<block>", "<height>", "<radius>", "<turns>", "<thickness>") +
                " <dark_gray>-</dark_gray> <gray>Helix (3D spiral)</gray>"));

        // Ellipsoid
        player.sendMessage(msg.parse(msg.buildSyntax("mct ellipsoid", "<block>", "<radiusX>", "<radiusY>", "<radiusZ>") +
                " <dark_gray>-</dark_gray> <gray>Ellipsoid</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hellipsoid", "<block>", "<radiusX>", "<radiusY>", "<radiusZ>", "<thickness>") +
                " <dark_gray>-</dark_gray> <gray>Hollow ellipsoid</gray>"));

        // Tube
        player.sendMessage(msg.parse(msg.buildSyntax("mct tube", "<block>", "<height>", "<radius>", "<innerRadius>") +
                " <dark_gray>-</dark_gray> <gray>Tube (pipe)</gray>"));

        // Capsule
        player.sendMessage(msg.parse(msg.buildSyntax("mct capsule", "<block>", "<radius>", "<height>") +
                " <dark_gray>-</dark_gray> <gray>Capsule (pill)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct hcapsule", "<block>", "<radius>", "<height>", "<thickness>") +
                " <dark_gray>-</dark_gray> <gray>Hollow capsule</gray>"));

        // Procedural Generation
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>Procedural Generation:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct tree", "<woodType>", "[seed:] [th:] [tr:] [bd:] [fd:] [fr:]") +
                " <dark_gray>-</dark_gray> <gray>Procedural tree</gray>"));
        player.sendMessage(msg.parse("<gray>  Flags: seed: th: tr: bd: fd: fr: -roots -special</gray>"));

        // Gradient Shapes
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>Gradient Shapes:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + ">/mct g&lt;shape&gt;</" + MessageUtil.CMD_COLOR + "> "
            + "<" + MessageUtil.BLOCK_COLOR + ">&lt;#hex,#hex,...&gt;</" + MessageUtil.BLOCK_COLOR + "> "
            + "<" + MessageUtil.RADIUS_COLOR + ">&lt;params&gt;</" + MessageUtil.RADIUS_COLOR + "> "
            + "<" + MessageUtil.OPTIONAL_COLOR + ">[-dir y|x|z|radial] [-interp oklab|lab|rgb|hsl] [-unique]</" + MessageUtil.OPTIONAL_COLOR + ">"));
        player.sendMessage(msg.parse("<dark_gray>  Example: /mct gsph #ff0000,#0000ff 12</dark_gray>"));
        player.sendMessage(msg.parse("<gray>  Creates shapes with color gradients using auto-matched blocks</gray>"));

        // Admin Commands
        player.sendMessage(msg.parse("<newline><" + MessageUtil.BLOCK_COLOR + "><bold>Admin Commands:</bold></" + MessageUtil.BLOCK_COLOR + ">"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct undo", "[count]") + " <dark_gray>-</dark_gray> <gray>Undo operations (up to 1000)</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct redo", "[count]") + " <dark_gray>-</dark_gray> <gray>Redo undone operations</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct cancel") + " <dark_gray>-</dark_gray> <gray>Cancel current operation</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct reload") + " <dark_gray>-</dark_gray> <gray>Reload configuration</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct help", "[shape]") + " <dark_gray>-</dark_gray> <gray>Show help</gray>"));
        player.sendMessage(msg.parse(msg.buildSyntax("mct info") + " <dark_gray>-</dark_gray> <gray>Plugin information</gray>"));
    }

    /**
     * Sends detailed help for a specific shape.
     */
    private void sendShapeHelp(Player player, String shape) {
        MessageUtil msg = plugin.getMessageUtil();
        msg.sendHelpHeader(player);

        switch (shape.toLowerCase()) {
            case "cir", "circle", "hcir" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Circle</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a flat circle on the ground.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct cir", "<block>", "<radius>")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hcir", "<block>", "<radius>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct cir stone 10</dark_gray>"));
            }
            case "sq", "square", "hsq" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Square</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a flat square. Size is the full side length.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct sq", "<block>", "<size>")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hsq", "<block>", "<size>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct sq oak_planks 15</dark_gray>"));
            }
            case "rect", "rectangle", "hrect" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Rectangle</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a rectangle. radiusX/Z are half-dimensions.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct rect", "<block>", "<radiusX>", "<radiusZ>", "[cornerRadius]")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hrect", "<block>", "<radiusX>", "<radiusZ>", "[cornerRadius]", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct hrect stone 51 42 7 3</dark_gray>"));
            }
            case "sph", "sphere", "hsph" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Sphere</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a 3D sphere centered on you.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct sph", "<block>", "<radius>")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hsph", "<block>", "<radius>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct hsph glass 15 1</dark_gray>"));
            }
            case "cyl", "cylinder", "hcyl" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Cylinder</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a vertical cylinder.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct cyl", "<block>", "<height>", "<radius>")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hcyl", "<block>", "<height>", "<radius>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct hcyl stone_bricks 30 10 2</dark_gray>"));
            }
            case "tor", "torus", "htor" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Torus</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a donut shape.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct tor", "<block>", "<majorRadius>", "<minorRadius>")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct htor", "<block>", "<majorRadius>", "<minorRadius>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><gray>majorRadius = distance to tube center</gray>"));
                player.sendMessage(msg.parse("<gray>minorRadius = tube radius</gray>"));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct tor gold_block 15 5</dark_gray>"));
            }
            case "wall" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Wall</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a solid wall.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct wall", "<block>", "<width>", "<height>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct wall stone_bricks 20 10 2</dark_gray>"));
            }
            case "hel", "helix" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Helix</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a 3D spiral (helix).</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hel", "<block>", "<height>", "<radius>", "<turns>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct hel gold_block 50 10 5 2</dark_gray>"));
            }
            case "tree" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Procedural Tree</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Generates a procedural tree with trunk, branches, foliage, and optional roots.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct tree", "<woodType>", "[params...]")));
                player.sendMessage(msg.parse("<newline><gray>Wood types: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped</gray>"));
                player.sendMessage(msg.parse("<newline><gray>Parameters (key:value):</gray>"));
                player.sendMessage(msg.parse("<gray>  seed:<1-99999> random seed (default: random)</gray>"));
                player.sendMessage(msg.parse("<gray>  th:<4-40> trunk height (default: 12)</gray>"));
                player.sendMessage(msg.parse("<gray>  tr:<1-6> trunk radius (default: 2)</gray>"));
                player.sendMessage(msg.parse("<gray>  bd:<0.1-1.0> branch density (default: 0.7)</gray>"));
                player.sendMessage(msg.parse("<gray>  fd:<0.1-1.0> foliage density (default: 0.8)</gray>"));
                player.sendMessage(msg.parse("<gray>  fr:<2-15> foliage radius (default: 6)</gray>"));
                player.sendMessage(msg.parse("<gray>  -roots enable root generation</gray>"));
                player.sendMessage(msg.parse("<gray>  -special use fences for thin branches, slabs for root tips</gray>"));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct tree oak seed:12345 th:15 bd:0.8 fd:0.9 fr:8 -roots</dark_gray>"));
            }
            case "ellipsoid", "hellipsoid" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Ellipsoid</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates an ellipsoid with independent radii per axis.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct ellipsoid", "<block>", "<radiusX>", "<radiusY>", "<radiusZ>")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hellipsoid", "<block>", "<radiusX>", "<radiusY>", "<radiusZ>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct ellipsoid stone 10 15 10</dark_gray>"));
            }
            case "tube" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Tube</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a tube (pipe) with inner and outer radius.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct tube", "<block>", "<height>", "<radius>", "<innerRadius>")));
                player.sendMessage(msg.parse("<newline><gray>innerRadius must be less than radius</gray>"));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct tube stone_bricks 20 10 7</dark_gray>"));
            }
            case "capsule", "hcapsule" -> {
                player.sendMessage(msg.parse("<" + MessageUtil.CMD_COLOR + "><bold>Capsule</bold></" + MessageUtil.CMD_COLOR + ">"));
                player.sendMessage(msg.parse("<gray>Creates a capsule (pill shape) with hemispherical caps.</gray><newline>"));
                player.sendMessage(msg.parse(msg.buildSyntax("mct capsule", "<block>", "<radius>", "<height>")));
                player.sendMessage(msg.parse(msg.buildSyntax("mct hcapsule", "<block>", "<radius>", "<height>", "<thickness>")));
                player.sendMessage(msg.parse("<newline><gray>height must be at least 2x radius</gray>"));
                player.sendMessage(msg.parse("<newline><dark_gray>Example: /mct capsule white_concrete 5 20</dark_gray>"));
            }
            default -> {
                msg.sendError(player, "Unknown shape: " + shape);
                msg.sendInfo(player, "Use /mct help for a list of shapes.");
            }
        }
    }

    /**
     * Sends plugin information to the player.
     */
    private void sendInfo(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        String version = plugin.getDescription().getVersion();
        
        // Header with gradient
        player.sendMessage(msg.parse("<newline><gradient:#00d4ff:#0099cc><bold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</bold></gradient>"));
        player.sendMessage(msg.parse(""));
        
        // Logo/Title
        player.sendMessage(msg.parse("        <gradient:#00d4ff:#3b82f6><bold>⬡ MCTools ⬡</bold></gradient>"));
        player.sendMessage(msg.parse("        <gray>Advanced Shape Generation</gray>"));
        player.sendMessage(msg.parse(""));
        
        // Version
        player.sendMessage(msg.parse("  <" + MessageUtil.CMD_COLOR + ">▸</" + MessageUtil.CMD_COLOR + "> <gray>Version:</gray> <white>" + version + "</white>"));
        
        // Creator
        player.sendMessage(msg.parse("  <" + MessageUtil.BLOCK_COLOR + ">▸</" + MessageUtil.BLOCK_COLOR + "> <gray>Created by:</gray> <click:open_url:'https://penguinstudios.eu/'><hover:show_text:'<gray>Visit PenguinStudios!</gray>'><gradient:#00d4ff:#5bcefa><bold>PenguinStudios</bold></gradient></hover></click>"));
        
        // Website
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  <" + MessageUtil.HEIGHT_COLOR + ">▸</" + MessageUtil.HEIGHT_COLOR + "> <gray>Website:</gray> <click:open_url:'https://mcutils.net/'><hover:show_text:'<gray>Click to visit!</gray>'><#3b82f6><underlined>mcutils.net</underlined></#3b82f6></hover></click>"));
        
        // GitHub/Download
        player.sendMessage(msg.parse("  <" + MessageUtil.RADIUS_COLOR + ">▸</" + MessageUtil.RADIUS_COLOR + "> <gray>Download:</gray> <click:open_url:'https://github.com/PenguinStudiosOrganization/MCTools/releases/tag/Release'><hover:show_text:'<gray>Click to download!</gray>'><#00d4ff><underlined>GitHub Releases</underlined></#00d4ff></hover></click>"));
        
        // Spacer
        player.sendMessage(msg.parse(""));
        
        // Thank you message
        player.sendMessage(msg.parse("  <gradient:#00d4ff:#5bcefa>♥</gradient> <gray>Thank you for using</gray> <gradient:#00d4ff:#3b82f6><bold>MCTools</bold></gradient><gray>!</gray>"));
        player.sendMessage(msg.parse("    <dark_gray>Your support helps us create better tools</dark_gray>"));
        player.sendMessage(msg.parse("    <dark_gray>for the Minecraft building community.</dark_gray>"));
        
        // Footer
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("<gradient:#00d4ff:#0099cc><bold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</bold></gradient><newline>"));
    }
}
