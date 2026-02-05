package com.mctools.commands;

import com.mctools.MCTools;
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

/**
 * Main command executor for MCTools plugin.
 * 
 * <p>Handles all shape generation commands, admin commands,
 * and provides comprehensive error handling and feedback.</p>
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class MCToolsCommand implements CommandExecutor {

    private final MCTools plugin;
    private final Map<UUID, Long> cooldowns;

    /**
     * Creates a new MCToolsCommand instance.
     * 
     * @param plugin The plugin instance
     */
    public MCToolsCommand(MCTools plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
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
        player.sendMessage(msg.parse("<newline><gradient:#10b981:#059669><bold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</bold></gradient>"));
        player.sendMessage(msg.parse(""));
        
        // Logo/Title
        player.sendMessage(msg.parse("        <gradient:#10b981:#3b82f6><bold>⬡ MCTools ⬡</bold></gradient>"));
        player.sendMessage(msg.parse("        <gray>Advanced Shape Generation</gray>"));
        player.sendMessage(msg.parse(""));
        
        // Version
        player.sendMessage(msg.parse("  <" + MessageUtil.CMD_COLOR + ">▸</" + MessageUtil.CMD_COLOR + "> <gray>Version:</gray> <white>" + version + "</white>"));
        
        // Creator
        player.sendMessage(msg.parse("  <" + MessageUtil.BLOCK_COLOR + ">▸</" + MessageUtil.BLOCK_COLOR + "> <gray>Created by:</gray> <gradient:#f97316:#ef4444><bold>PenguinStudios Development</bold></gradient>"));
        
        // Website
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  <" + MessageUtil.HEIGHT_COLOR + ">▸</" + MessageUtil.HEIGHT_COLOR + "> <gray>Website:</gray> <click:open_url:'https://mcutils.net'><hover:show_text:'<gray>Click to visit!</gray>'><#3b82f6><underlined>mcutils.net</underlined></#3b82f6></hover></click>"));
        
        // Wiki
        player.sendMessage(msg.parse("  <" + MessageUtil.THICK_COLOR + ">▸</" + MessageUtil.THICK_COLOR + "> <gray>Wiki:</gray> <click:open_url:'https://docs.mcutils.net'><hover:show_text:'<gray>Click to visit!</gray>'><#a855f7><underlined>docs.mcutils.net</underlined></#a855f7></hover></click>"));
        
        // GitHub
        player.sendMessage(msg.parse("  <" + MessageUtil.RADIUS_COLOR + ">▸</" + MessageUtil.RADIUS_COLOR + "> <gray>GitHub:</gray> <click:open_url:'https://github.com/PenguinStudios/MCTools'><hover:show_text:'<gray>Click to visit!</gray>'><#ef4444><underlined>github.com/PenguinStudios/MCTools</underlined></#ef4444></hover></click>"));
        
        // Spacer
        player.sendMessage(msg.parse(""));
        
        // Thank you message
        player.sendMessage(msg.parse("  <gradient:#10b981:#059669>♥</gradient> <gray>Thank you for using</gray> <gradient:#10b981:#3b82f6><bold>MCTools</bold></gradient><gray>!</gray>"));
        player.sendMessage(msg.parse("    <dark_gray>Your support helps us create better tools</dark_gray>"));
        player.sendMessage(msg.parse("    <dark_gray>for the Minecraft building community.</dark_gray>"));
        
        // Footer
        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("<gradient:#10b981:#059669><bold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</bold></gradient><newline>"));
    }
}
