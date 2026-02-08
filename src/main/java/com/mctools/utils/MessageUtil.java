package com.mctools.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Utility class for handling message formatting using MiniMessage.
 * 
 * <p>Provides consistent styling across all plugin messages with
 * support for gradients, colors, and special formatting.</p>
 * 
 * <p>Color scheme:</p>
 * <ul>
 *   <li>Command (cmd): Green #10b981</li>
 *   <li>Block: Orange #f97316</li>
 *   <li>Radius: Red #ef4444</li>
 *   <li>Height: Blue #3b82f6</li>
 *   <li>Thickness: Purple #a855f7</li>
 * </ul>
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    /** The plugin message prefix with gradient styling */
    private static final String PREFIX = "<gradient:#10b981:#059669><bold>MCTools</bold></gradient> <dark_gray>│</dark_gray> ";

    // Color constants for parameters
    public static final String CMD_COLOR = "#10b981";      // Green
    public static final String BLOCK_COLOR = "#f97316";    // Orange
    public static final String RADIUS_COLOR = "#ef4444";   // Red
    public static final String HEIGHT_COLOR = "#3b82f6";   // Blue
    public static final String THICK_COLOR = "#a855f7";    // Purple
    public static final String OPTIONAL_COLOR = "#52525b"; // Gray
    public static final String TEXT_COLOR = "#a1a1aa";     // Light gray

    /** Fallback message shown when MiniMessage parsing fails. */
    private static final String FALLBACK_MESSAGE = "§7Messaggio non disponibile, gentilmente contatta il supporto §b§nhttps://discord.penguinstudios.eu/";

    /**
     * Parses a MiniMessage string into a Component.
     * Falls back to a plain error message if parsing fails.
     * 
     * @param message The MiniMessage formatted string
     * @return The parsed Component
     */
    public Component parse(String message) {
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            return Component.text(stripLegacyCodes(message));
        }
    }

    /**
     * Parses a message with the plugin prefix.
     * 
     * @param message The message to prefix
     * @return The parsed Component with prefix
     */
    public Component parseWithPrefix(String message) {
        try {
            return MINI_MESSAGE.deserialize(PREFIX + message);
        } catch (Exception e) {
            return Component.text("§a§lMCTools §7│ " + stripLegacyCodes(message));
        }
    }

    /**
     * Sends a success message to a player.
     * 
     * @param player The player to send the message to
     * @param shape The shape that was generated
     * @param blockCount The number of blocks placed
     */
    public void sendSuccess(Player player, String shape, int blockCount) {
        try {
            String message = PREFIX + "<" + TEXT_COLOR + ">Generated <" + CMD_COLOR + "><bold>" + shape + 
                    "</bold></" + CMD_COLOR + "> with <" + CMD_COLOR + ">" + blockCount + "</" + CMD_COLOR + "> blocks";
            player.sendMessage(MINI_MESSAGE.deserialize(message));
        } catch (Exception e) {
            player.sendMessage("§a§lMCTools §7│ §aGenerated §f" + shape + " §awith §f" + blockCount + " §ablocks");
        }
    }

    /**
     * Sends an error message to a player.
     * 
     * @param player The player to send the message to
     * @param message The error message
     */
    public void sendError(Player player, String message) {
        try {
            // Sanitize legacy § codes that break MiniMessage
            String sanitized = sanitizeForMiniMessage(message);
            String formatted = PREFIX + "<#ef4444>✗</#ef4444> <" + TEXT_COLOR + ">" + sanitized;
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCTools §7│ §c✗ " + message);
        }
    }

    /**
     * Sends an info message to a player.
     * 
     * @param player The player to send the message to
     * @param message The info message
     */
    public void sendInfo(Player player, String message) {
        try {
            String sanitized = sanitizeForMiniMessage(message);
            String formatted = PREFIX + "<#3b82f6>ℹ</#3b82f6> <" + TEXT_COLOR + ">" + sanitized;
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCTools §7│ §bℹ §7" + message);
        }
    }

    /**
     * Sends an info message to a player by UUID.
     * 
     * @param uuid The UUID of the player
     * @param message The info message
     */
    public void sendInfo(java.util.UUID uuid, String message) {
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null) {
            sendInfo(player, message);
        }
    }

    /**
     * Sends a raw MiniMessage-formatted message with the plugin prefix.
     * Unlike sendInfo(), this does NOT wrap the message in TEXT_COLOR,
     * allowing the caller to provide fully custom-colored content.
     *
     * @param player  The player to send the message to
     * @param miniMsg The MiniMessage formatted string (colors included)
     */
    public void sendRaw(Player player, String miniMsg) {
        try {
            String formatted = PREFIX + miniMsg;
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCTools §7│ §7" + stripLegacyCodes(miniMsg));
        }
    }

    /**
     * Sends a warning message to a player.
     * 
     * @param player The player to send the message to
     * @param message The warning message
     */
    public void sendWarning(Player player, String message) {
        try {
            String sanitized = sanitizeForMiniMessage(message);
            String formatted = PREFIX + "<#f97316>⚠</#f97316> <" + TEXT_COLOR + ">" + sanitized;
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCTools §7│ §6⚠ §7" + message);
        }
    }

    /**
     * Sanitizes a string containing legacy § color codes for use with MiniMessage.
     * Strips § codes since MiniMessage doesn't support them.
     */
    private String sanitizeForMiniMessage(String input) {
        if (input == null) return "";
        // Replace legacy § codes with empty string for MiniMessage compatibility
        return input.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Strips legacy color codes from a string for plain text fallback.
     */
    private String stripLegacyCodes(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-or]", "")
                     .replaceAll("<[^>]+>", ""); // Also strip MiniMessage tags
    }

    /**
     * Sends a preview message to a player.
     * 
     * @param player The player to send the message to
     * @param seconds Seconds until generation starts
     */
    public void sendPreview(Player player, int seconds) {
        String formatted = PREFIX + "<" + CMD_COLOR + ">⬡</" + CMD_COLOR + "> <" + TEXT_COLOR + ">Preview mode - Generation starts in <" + CMD_COLOR + ">" + seconds + "</" + CMD_COLOR + "> seconds...";
        player.sendMessage(MINI_MESSAGE.deserialize(formatted));
    }

    /**
     * Sends a progress bar to a player.
     * 
     * @param player The player to send the progress to
     * @param progress The progress value (0-20)
     * @param percent The percentage complete
     */
    public void sendProgress(Player player, int progress, int percent) {
        progress = Math.max(0, Math.min(20, progress));
        String filled = "█".repeat(progress);
        String empty = "░".repeat(20 - progress);
        String message = PREFIX + "<#52525b>[<" + CMD_COLOR + ">" + filled + "</" + CMD_COLOR + "><#27272a>" + 
                empty + "</#27272a>]</#52525b> <" + TEXT_COLOR + ">" + percent + "%";
        player.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    /**
     * Sends the help header to a player.
     * 
     * @param player The player to send the header to
     */
    public void sendHelpHeader(Player player) {
        String header = "<newline><gradient:#10b981:#059669><bold>━━━ MCTools Help ━━━</bold></gradient><newline>";
        player.sendMessage(MINI_MESSAGE.deserialize(header));
    }

    /**
     * Sends a help command entry to a player with colored parameters.
     * 
     * @param player The player to send the entry to
     * @param command The command (will be colored green)
     * @param description The command description
     */
    public void sendHelpCommand(Player player, String command, String description) {
        String message = "<" + CMD_COLOR + ">/" + command + "</" + CMD_COLOR + "> <#52525b>-</#52525b> <" + TEXT_COLOR + ">" + description;
        player.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    /**
     * Sends a formatted command syntax to a player.
     * 
     * @param player The player to send the syntax to
     * @param syntax The pre-formatted syntax string
     */
    public void sendCommandSyntax(Player player, String syntax) {
        player.sendMessage(MINI_MESSAGE.deserialize(syntax));
    }

    /**
     * Sends a usage message to a player.
     * 
     * @param player The player to send the usage to
     * @param usage The usage syntax
     */
    public void sendUsage(Player player, String usage) {
        String message = PREFIX + "<#f97316>Usage:</#f97316> <" + TEXT_COLOR + ">" + usage;
        player.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    /**
     * Formats a command parameter with the appropriate color.
     * 
     * @param param The parameter name
     * @param color The color to use
     * @return The formatted parameter string
     */
    public String colorParam(String param, String color) {
        return "<" + color + ">" + param + "</" + color + ">";
    }

    /**
     * Formats a required parameter for display.
     * 
     * @param param The parameter name
     * @param color The color to use
     * @return The formatted parameter string
     */
    public String formatRequired(String param, String color) {
        return "<" + color + "><" + param + "></" + color + ">";
    }

    /**
     * Formats an optional parameter for display.
     * 
     * @param param The parameter name
     * @return The formatted parameter string
     */
    public String formatOptional(String param) {
        return "<" + OPTIONAL_COLOR + ">[" + param + "]</" + OPTIONAL_COLOR + ">";
    }

    /**
     * Gets the raw prefix string.
     * 
     * @return The prefix string
     */
    public String getPrefix() {
        return PREFIX;
    }

    /**
     * Builds a colored command syntax string.
     * Example: /mct cir <block> <radius>
     */
    public String buildSyntax(String cmd, String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(CMD_COLOR).append(">/").append(cmd).append("</").append(CMD_COLOR).append(">");
        
        for (String param : params) {
            sb.append(" ");
            if (param.startsWith("[")) {
                // Optional parameter
                sb.append("<").append(OPTIONAL_COLOR).append(">").append(param).append("</").append(OPTIONAL_COLOR).append(">");
            } else if (param.equals("<block>")) {
                sb.append("<").append(BLOCK_COLOR).append(">").append(param).append("</").append(BLOCK_COLOR).append(">");
            } else if (param.contains("radius") || param.contains("size") || param.contains("width") || param.contains("depth") || param.contains("length")) {
                sb.append("<").append(RADIUS_COLOR).append(">").append(param).append("</").append(RADIUS_COLOR).append(">");
            } else if (param.contains("height") || param.contains("turns") || param.contains("sides")) {
                sb.append("<").append(HEIGHT_COLOR).append(">").append(param).append("</").append(HEIGHT_COLOR).append(">");
            } else if (param.contains("thick")) {
                sb.append("<").append(THICK_COLOR).append(">").append(param).append("</").append(THICK_COLOR).append(">");
            } else {
                sb.append("<").append(TEXT_COLOR).append(">").append(param).append("</").append(TEXT_COLOR).append(">");
            }
        }
        
        return sb.toString();
    }
}
