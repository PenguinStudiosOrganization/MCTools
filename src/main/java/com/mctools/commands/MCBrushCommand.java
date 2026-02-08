package com.mctools.commands;

import com.mctools.MCTools;
import com.mctools.brush.BrushManager;
import com.mctools.brush.BrushSettings;
import com.mctools.brush.gui.BrushGUI;
import com.mctools.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for the MCBrush terrain brush system ({@code /mcb}).
 *
 * <p>Handles all brush sub-commands: toggle, size, intensity, height,
 * block selection, heightmap selection, mode, auto-smooth, and more.
 * When invoked with no arguments, opens the brush GUI.</p>
 */
public class MCBrushCommand implements CommandExecutor, TabCompleter {

    private final MCTools plugin;

    public MCBrushCommand(MCTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        MessageUtil msg = plugin.getMessageUtil();
        String permission = plugin.getConfigManager().getBrushPermission();

        if (!player.hasPermission(permission)) {
            msg.sendError(player, "You don't have permission to use MCBrush!");
            return true;
        }

        if (!plugin.getConfigManager().isBrushEnabled()) {
            msg.sendError(player, "MCBrush is disabled in the configuration.");
            return true;
        }

        BrushManager brushManager = plugin.getBrushManager();
        BrushSettings settings = brushManager.getSettings(player.getUniqueId());

        if (args.length == 0) {
            new BrushGUI(plugin, player).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "toggle", "on", "off" -> {
                if (subCommand.equals("on")) {
                    settings.setEnabled(true);
                } else if (subCommand.equals("off")) {
                    settings.setEnabled(false);
                } else {
                    settings.toggle();
                }
                
                if (settings.isEnabled()) {
                    msg.sendInfo(player, "Brush <#10b981>enabled</#10b981>. Right-click to apply terrain.");
                } else {
                    msg.sendInfo(player, "Brush <#ef4444>disabled</#ef4444>.");
                }
            }
            
            case "size", "s" -> {
                if (args.length < 2) {
                    msg.sendInfo(player, "Current size: <#10b981>" + settings.getSize() + "</#10b981>");
                    return true;
                }
                try {
                    int size = Integer.parseInt(args[1]);
                    int maxSize = plugin.getConfigManager().getBrushMaxSize();
                    if (size < 1 || size > maxSize) {
                        msg.sendError(player, "Size must be between 1 and " + maxSize);
                        return true;
                    }
                    settings.setSize(size);
                    msg.sendInfo(player, "Brush size set to <#10b981>" + size + "</#10b981>");
                } catch (NumberFormatException e) {
                    msg.sendError(player, "Invalid size: " + args[1]);
                }
            }
            
            case "intensity", "i" -> {
                if (args.length < 2) {
                    msg.sendInfo(player, "Current intensity: <#10b981>" + settings.getIntensity() + "%</#10b981>");
                    return true;
                }
                try {
                    int intensity = Integer.parseInt(args[1]);
                    if (intensity < 1 || intensity > 100) {
                        msg.sendError(player, "Intensity must be between 1 and 100");
                        return true;
                    }
                    settings.setIntensity(intensity);
                    msg.sendInfo(player, "Brush intensity set to <#10b981>" + intensity + "%</#10b981>");
                } catch (NumberFormatException e) {
                    msg.sendError(player, "Invalid intensity: " + args[1]);
                }
            }
            
            case "height", "h" -> {
                if (args.length < 2) {
                    msg.sendInfo(player, "Current max height: <#10b981>" + settings.getMaxHeight() + "</#10b981>");
                    return true;
                }
                try {
                    int height = Integer.parseInt(args[1]);
                    int maxHeight = plugin.getConfigManager().getBrushMaxHeight();
                    if (height < 1 || height > maxHeight) {
                        msg.sendError(player, "Height must be between 1 and " + maxHeight);
                        return true;
                    }
                    settings.setMaxHeight(height);
                    msg.sendInfo(player, "Max height set to <#10b981>" + height + "</#10b981>");
                } catch (NumberFormatException e) {
                    msg.sendError(player, "Invalid height: " + args[1]);
                }
            }
            
            case "block", "b" -> {
                if (args.length < 2) {
                    msg.sendInfo(player, "Current block: <#10b981>" + settings.getBlock().name().toLowerCase() + "</#10b981>");
                    return true;
                }
                Material material = Material.matchMaterial(args[1]);
                if (material == null || !material.isBlock()) {
                    msg.sendError(player, "Invalid block: " + args[1]);
                    return true;
                }
                settings.setBlock(material);
                msg.sendInfo(player, "Block set to <#10b981>" + material.name().toLowerCase() + "</#10b981>");
            }
            
            case "heightmap", "hm" -> {
                if (args.length < 2) {
                    String current = settings.getHeightmapName();
                    msg.sendInfo(player, "Current heightmap: <#10b981>" + (current != null ? current : "None") + "</#10b981>");
                    return true;
                }
                String heightmapName = args[1];
                if (!brushManager.hasHeightmap(heightmapName)) {
                    msg.sendError(player, "Heightmap not found: " + heightmapName);
                    msg.sendInfo(player, "Available: " + String.join(", ", brushManager.getAvailableHeightmaps()));
                    return true;
                }
                BufferedImage image = brushManager.getHeightmap(heightmapName);
                settings.setHeightmapName(heightmapName);
                settings.setHeightmapImage(image);
                msg.sendInfo(player, "Heightmap set to <#10b981>" + heightmapName + "</#10b981>");
            }
            
            case "mode", "m" -> {
                if (args.length < 2) {
                    msg.sendInfo(player, "Current mode: <#10b981>" + settings.getMode().name().toLowerCase() + "</#10b981>");
                    return true;
                }
                try {
                    BrushSettings.BrushMode mode = BrushSettings.BrushMode.valueOf(args[1].toUpperCase());
                    settings.setMode(mode);
                    msg.sendInfo(player, "Mode set to <#10b981>" + mode.name().toLowerCase() + "</#10b981>");
                } catch (IllegalArgumentException e) {
                    msg.sendError(player, "Invalid mode. Available: raise, lower, smooth, flatten");
                }
            }
            
            case "autosmooth", "as" -> {
                if (args.length < 2) {
                    msg.sendInfo(player, "Auto-Smooth: " + (settings.isAutoSmooth() ? "<#10b981>enabled" : "<#ef4444>disabled"));
                    return true;
                }
                String value = args[1].toLowerCase();
                if (value.equals("on") || value.equals("true") || value.equals("1")) {
                    settings.setAutoSmooth(true);
                    msg.sendInfo(player, "Auto-Smooth <#10b981>enabled</#10b981>");
                } else if (value.equals("off") || value.equals("false") || value.equals("0")) {
                    settings.setAutoSmooth(false);
                    msg.sendInfo(player, "Auto-Smooth <#ef4444>disabled</#ef4444>");
                } else {
                    settings.toggleAutoSmooth();
                    msg.sendInfo(player, "Auto-Smooth " + (settings.isAutoSmooth() ? "<#10b981>enabled" : "<#ef4444>disabled"));
                }
            }
            
            case "smoothstrength", "ss" -> {
                if (args.length < 2) {
                    msg.sendInfo(player, "Smooth strength: <#10b981>" + settings.getSmoothStrength() + "</#10b981> (1-5)");
                    return true;
                }
                try {
                    int strength = Integer.parseInt(args[1]);
                    if (strength < 1 || strength > 5) {
                        msg.sendError(player, "Smooth strength must be between 1 and 5");
                        return true;
                    }
                    settings.setSmoothStrength(strength);
                    msg.sendInfo(player, "Smooth strength set to <#10b981>" + strength + "</#10b981>");
                } catch (NumberFormatException e) {
                    msg.sendError(player, "Invalid strength: " + args[1]);
                }
            }
            
            case "list", "l" -> {
                List<String> heightmaps = brushManager.getAvailableHeightmaps();
                if (heightmaps.isEmpty()) {
                    msg.sendInfo(player, "No heightmaps available. Add .png files to the heightmaps folder.");
                } else {
                    msg.sendInfo(player, "Available heightmaps (" + heightmaps.size() + "):");
                    for (String hm : heightmaps) {
                        player.sendMessage(msg.parse("  <#52525b>•</#52525b> <#a1a1aa>" + hm + "</#a1a1aa>"));
                    }
                }
            }
            
            case "reload" -> {
                if (!player.hasPermission("mctools.admin")) {
                    msg.sendError(player, "You don't have permission to reload heightmaps!");
                    return true;
                }
                brushManager.reload();
                msg.sendInfo(player, "Heightmaps reloaded! Found " + brushManager.getAvailableHeightmaps().size() + " heightmaps.");
            }
            
            case "info" -> {
                msg.sendHelpHeader(player);
                player.sendMessage(msg.parse("<#10b981><bold>Current Brush Settings:</bold></#10b981>"));
                player.sendMessage(msg.parse("  <gray>Enabled:</gray> " + (settings.isEnabled() ? "<#10b981>Yes" : "<#ef4444>No")));
                player.sendMessage(msg.parse("  <gray>Heightmap:</gray> <white>" + (settings.getHeightmapName() != null ? settings.getHeightmapName() : "None")));
                player.sendMessage(msg.parse("  <gray>Size:</gray> <white>" + settings.getSize()));
                player.sendMessage(msg.parse("  <gray>Intensity:</gray> <white>" + settings.getIntensity() + "%"));
                player.sendMessage(msg.parse("  <gray>Max Height:</gray> <white>" + settings.getMaxHeight()));
                player.sendMessage(msg.parse("  <gray>Block:</gray> <white>" + settings.getBlock().name().toLowerCase()));
                player.sendMessage(msg.parse("  <gray>Mode:</gray> <white>" + settings.getMode().name().toLowerCase()));
            }
            
            case "help" -> {
                sendHelp(player);
            }
            
            default -> {
                msg.sendError(player, "Unknown command: " + subCommand);
                msg.sendInfo(player, "Use /mcb help for available commands.");
            }
        }

        return true;
    }

    private void sendHelp(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        msg.sendHelpHeader(player);
        
        player.sendMessage(msg.parse("<#f97316><bold>MCBrush Commands:</bold></#f97316>"));
        player.sendMessage(msg.parse("<#10b981>/mcb</#10b981> <dark_gray>-</dark_gray> <gray>Open brush GUI</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb toggle</#10b981> <dark_gray>-</dark_gray> <gray>Toggle brush on/off</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb size <#ef4444><size></#ef4444></#10b981> <dark_gray>-</dark_gray> <gray>Set brush size</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb intensity <#ef4444><1-100></#ef4444></#10b981> <dark_gray>-</dark_gray> <gray>Set intensity</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb height <#ef4444><height></#ef4444></#10b981> <dark_gray>-</dark_gray> <gray>Set max height</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb block <#f97316><block></#f97316></#10b981> <dark_gray>-</dark_gray> <gray>Set block type</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb heightmap <#a855f7><name></#a855f7></#10b981> <dark_gray>-</dark_gray> <gray>Set heightmap</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb mode <#3b82f6><mode></#3b82f6></#10b981> <dark_gray>-</dark_gray> <gray>Set mode (raise/lower/smooth/flatten)</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb list</#10b981> <dark_gray>-</dark_gray> <gray>List available heightmaps</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb info</#10b981> <dark_gray>-</dark_gray> <gray>Show current settings</gray>"));
        player.sendMessage(msg.parse("<#10b981>/mcb reload</#10b981> <dark_gray>-</dark_gray> <gray>Reload heightmaps</gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                    "toggle", "on", "off", "size", "intensity", "height", 
                    "block", "heightmap", "mode", "autosmooth", "smoothstrength",
                    "list", "info", "reload", "help"
            ));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "size", "s" -> completions.addAll(Arrays.asList("5", "10", "15", "20", "25", "30"));
                case "intensity", "i" -> completions.addAll(Arrays.asList("25", "50", "75", "100"));
                case "height", "h" -> completions.addAll(Arrays.asList("10", "20", "30", "40", "50"));
                case "block", "b" -> {
                    String input = args[1].toLowerCase();
                    for (Material mat : Material.values()) {
                        if (mat.isBlock() && mat.name().toLowerCase().startsWith(input)) {
                            completions.add(mat.name().toLowerCase());
                            if (completions.size() > 20) break;
                        }
                    }
                }
                case "heightmap", "hm" -> completions.addAll(plugin.getBrushManager().getAvailableHeightmaps());
                case "mode", "m" -> completions.addAll(Arrays.asList("raise", "lower", "smooth", "flatten"));
                case "autosmooth", "as" -> completions.addAll(Arrays.asList("on", "off", "toggle"));
                case "smoothstrength", "ss" -> completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
            }
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }
}
