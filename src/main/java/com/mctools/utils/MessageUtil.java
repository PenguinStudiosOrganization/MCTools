package com.mctools.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Utility class for handling message formatting using MiniMessage.
 *
 * <p>Provides consistent, modern styling across all plugin messages with
 * support for gradients, colors, and special formatting.</p>
 *
 * <p>Color palette — minimal, modern, high-contrast:</p>
 * <ul>
 *   <li>Primary / Accent: Emerald #34d399 → #10b981</li>
 *   <li>Command (cmd): Teal #2dd4bf</li>
 *   <li>Block: Amber #fbbf24</li>
 *   <li>Radius/Size: Rose #fb7185</li>
 *   <li>Height/Turns: Sky #38bdf8</li>
 *   <li>Thickness: Violet #a78bfa</li>
 *   <li>Road mode: Amber #f59e0b</li>
 *   <li>Bridge mode: Cyan #22d3ee</li>
 *   <li>Curve mode: Pink #f472b6</li>
 * </ul>
 *
 * @author MCTools Team
 * @version 2.0.0
 */
public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // ── Prefix ──────────────────────────────────────────────────────────
    /** Clean, minimal prefix with a thin vertical bar separator */
    private static final String PREFIX =
            "<gradient:#34d399:#10b981><bold>MCT</bold></gradient> <#3f3f46>│</#3f3f46> ";

    // ── Parameter colors ────────────────────────────────────────────────
    public static final String CMD_COLOR      = "#2dd4bf";   // Teal
    public static final String BLOCK_COLOR    = "#fbbf24";   // Amber
    public static final String RADIUS_COLOR   = "#fb7185";   // Rose
    public static final String HEIGHT_COLOR   = "#38bdf8";   // Sky
    public static final String THICK_COLOR    = "#a78bfa";   // Violet
    public static final String OPTIONAL_COLOR = "#71717a";   // Zinc-500
    public static final String TEXT_COLOR     = "#a1a1aa";   // Zinc-400

    // ── Mode colors (path tools) ────────────────────────────────────────
    public static final String ROAD_COLOR   = "#f59e0b";    // Amber
    public static final String BRIDGE_COLOR = "#22d3ee";    // Cyan
    public static final String CURVE_COLOR  = "#f472b6";    // Pink

    // ── Semantic colors ─────────────────────────────────────────────────
    public static final String SUCCESS_COLOR = "#34d399";   // Emerald-400
    public static final String ERROR_COLOR   = "#fb7185";   // Rose-400
    public static final String WARN_COLOR    = "#fbbf24";   // Amber-400
    public static final String INFO_COLOR    = "#38bdf8";   // Sky-400
    public static final String ACCENT_COLOR  = "#a78bfa";   // Violet-400
    public static final String MUTED_COLOR   = "#52525b";   // Zinc-600
    public static final String VALUE_COLOR   = "white";     // White for values
    public static final String DIM_COLOR     = "#3f3f46";   // Zinc-700 (separators)

    // ── Unicode helpers ─────────────────────────────────────────────────
    private static final String SEP  = " <" + DIM_COLOR + ">│</" + DIM_COLOR + "> ";
    private static final String DOT  = "<" + DIM_COLOR + ">•</" + DIM_COLOR + "> ";
    private static final String DASH = " <" + DIM_COLOR + ">—</" + DIM_COLOR + "> ";

    // ═══════════════════════════════════════════════════════════════════
    //  Core parsing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parses a MiniMessage string into a Component.
     * Falls back to a plain error message if parsing fails.
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
     */
    public Component parseWithPrefix(String message) {
        try {
            return MINI_MESSAGE.deserialize(PREFIX + message);
        } catch (Exception e) {
            return Component.text("§a§lMCT §7│ " + stripLegacyCodes(message));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Semantic message senders
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends a success message after a shape/operation completes.
     */
    public void sendSuccess(Player player, String shape, int blockCount) {
        try {
            String message = PREFIX
                    + "<" + SUCCESS_COLOR + ">✔</" + SUCCESS_COLOR + "> "
                    + "<white>" + shape + "</white> "
                    + "<" + TEXT_COLOR + ">completed</" + TEXT_COLOR + ">"
                    + DASH
                    + "<white>" + String.format("%,d", blockCount) + "</white> "
                    + "<" + TEXT_COLOR + ">blocks</" + TEXT_COLOR + ">";
            player.sendMessage(MINI_MESSAGE.deserialize(message));
        } catch (Exception e) {
            player.sendMessage("§a§lMCT §7│ §a✔ " + shape + " §7completed — §f" + blockCount + " §7blocks");
        }
    }

    /**
     * Sends an error message to a player.
     */
    public void sendError(Player player, String message) {
        try {
            String sanitized = sanitizeForMiniMessage(message);
            String formatted = PREFIX
                    + "<" + ERROR_COLOR + ">✘ " + sanitized + "</" + ERROR_COLOR + ">";
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCT §7│ §c✘ " + message);
        }
    }

    /**
     * Sends an info message to a player.
     */
    public void sendInfo(Player player, String message) {
        try {
            String sanitized = sanitizeForMiniMessage(message);
            String formatted = PREFIX + "<" + TEXT_COLOR + ">" + sanitized + "</" + TEXT_COLOR + ">";
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCT §7│ §7" + message);
        }
    }

    /**
     * Sends an info message to a player by UUID.
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
     */
    public void sendRaw(Player player, String miniMsg) {
        try {
            String formatted = PREFIX + miniMsg;
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCT §7│ §7" + stripLegacyCodes(miniMsg));
        }
    }

    /**
     * Sends a warning message to a player.
     */
    public void sendWarning(Player player, String message) {
        try {
            String sanitized = sanitizeForMiniMessage(message);
            String formatted = PREFIX
                    + "<" + WARN_COLOR + ">⚠ " + sanitized + "</" + WARN_COLOR + ">";
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCT §7│ §6⚠ " + message);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Sanitisation helpers
    // ═══════════════════════════════════════════════════════════════════

    private String sanitizeForMiniMessage(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-or]", "");
    }

    private String stripLegacyCodes(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-or]", "")
                .replaceAll("<[^>]+>", "");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Specialised senders
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends a preview countdown message.
     */
    public void sendPreview(Player player, int seconds) {
        String formatted = PREFIX
                + "<" + TEXT_COLOR + ">Preview mode</" + TEXT_COLOR + ">"
                + DASH
                + "<" + TEXT_COLOR + ">generating in </" + TEXT_COLOR + ">"
                + "<" + SUCCESS_COLOR + ">" + seconds + "s</" + SUCCESS_COLOR + ">";
        player.sendMessage(MINI_MESSAGE.deserialize(formatted));
    }

    /**
     * Sends a progress bar to a player.
     */
    public void sendProgress(Player player, int progress, int percent) {
        progress = Math.max(0, Math.min(20, progress));
        String filled = "█".repeat(progress);
        String empty = "░".repeat(20 - progress);
        String message = PREFIX
                + "<" + DIM_COLOR + ">[</" + DIM_COLOR + ">"
                + "<" + SUCCESS_COLOR + ">" + filled + "</" + SUCCESS_COLOR + ">"
                + "<" + MUTED_COLOR + ">" + empty + "</" + MUTED_COLOR + ">"
                + "<" + DIM_COLOR + ">]</" + DIM_COLOR + "> "
                + "<white>" + percent + "%</white>";
        player.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    /**
     * Sends the help header to a player.
     */
    public void sendHelpHeader(Player player) {
        String header = "\n"
                + "<gradient:#34d399:#10b981><bold>━━━ MCTools Help ━━━</bold></gradient>"
                + "\n";
        player.sendMessage(MINI_MESSAGE.deserialize(header));
    }

    /**
     * Sends a help command entry to a player with colored parameters.
     */
    public void sendHelpCommand(Player player, String command, String description) {
        String message = "  <" + CMD_COLOR + ">/" + command + "</" + CMD_COLOR + ">"
                + DASH
                + "<" + TEXT_COLOR + ">" + description + "</" + TEXT_COLOR + ">";
        player.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    /**
     * Sends a formatted command syntax to a player.
     */
    public void sendCommandSyntax(Player player, String syntax) {
        player.sendMessage(MINI_MESSAGE.deserialize(syntax));
    }

    /**
     * Sends a usage message to a player.
     */
    public void sendUsage(Player player, String usage) {
        String message = PREFIX
                + "<" + TEXT_COLOR + ">Usage: </" + TEXT_COLOR + ">"
                + "<white>" + usage + "</white>";
        player.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    /**
     * Sends an "unknown argument" error to a player.
     */
    public void sendUnknownArgument(Player player, String argument) {
        try {
            String sanitized = sanitizeForMiniMessage(argument);
            String formatted = PREFIX
                    + "<" + ERROR_COLOR + ">✘</" + ERROR_COLOR + "> "
                    + "<" + TEXT_COLOR + ">Unknown argument </" + TEXT_COLOR + ">"
                    + "<white>\"" + sanitized + "\"</white>"
                    + "<" + TEXT_COLOR + ">. See </" + TEXT_COLOR + ">"
                    + "<" + INFO_COLOR + "><underlined><click:open_url:'https://github.com/PenguinStudiosOrganization/MCTools'>wiki</click></underlined></" + INFO_COLOR + ">"
                    + " <" + TEXT_COLOR + ">or </" + TEXT_COLOR + ">"
                    + "<" + CMD_COLOR + ">/mct help</" + CMD_COLOR + ">";
            player.sendMessage(MINI_MESSAGE.deserialize(formatted));
        } catch (Exception e) {
            player.sendMessage("§a§lMCT §7│ §c✘ Unknown argument \"§f" + argument + "§c\". See the wiki or use §a/mct help");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Formatting builders
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a command parameter with the appropriate color.
     */
    public String colorParam(String param, String color) {
        return "<" + color + ">" + param + "</" + color + ">";
    }

    /**
     * Formats a required parameter for display.
     */
    public String formatRequired(String param, String color) {
        return "<" + color + "><" + param + "></" + color + ">";
    }

    /**
     * Formats an optional parameter for display.
     */
    public String formatOptional(String param) {
        return "<" + OPTIONAL_COLOR + ">[" + param + "]</" + OPTIONAL_COLOR + ">";
    }

    /**
     * Gets the raw prefix string.
     */
    public String getPrefix() {
        return PREFIX;
    }

    /**
     * Returns the color for a path mode name.
     */
    public static String getModeColor(String mode) {
        return switch (mode.toLowerCase()) {
            case "road" -> ROAD_COLOR;
            case "bridge" -> BRIDGE_COLOR;
            case "curve" -> CURVE_COLOR;
            default -> CMD_COLOR;
        };
    }

    /**
     * Builds a colored command syntax string.
     * Example: /mct cir &lt;block&gt; &lt;radius&gt;
     */
    public String buildSyntax(String cmd, String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(CMD_COLOR).append(">/").append(cmd).append("</").append(CMD_COLOR).append(">");

        for (String param : params) {
            sb.append(" ");
            if (param.startsWith("[")) {
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

    /**
     * Builds a section header line for help pages and info displays.
     * Uses a gradient with the given colors and modern box-drawing chars.
     */
    public String buildHeader(String title, String color1, String color2) {
        return "<gradient:" + color1 + ":" + color2 + "><bold>━━━ " + title + " ━━━</bold></gradient>";
    }

    /**
     * Builds a section header using the default emerald gradient.
     */
    public String buildHeader(String title) {
        return buildHeader(title, "#34d399", "#10b981");
    }

    /**
     * Builds a key-value detail line for info displays.
     * Uses a bullet point and clean alignment.
     */
    public String buildDetail(String key, String value) {
        return "  " + DOT + "<" + TEXT_COLOR + ">" + key + "</" + TEXT_COLOR + "> "
                + "<" + DIM_COLOR + ">→</" + DIM_COLOR + "> "
                + "<white>" + value + "</white>";
    }

    /**
     * Builds a category label for help sections.
     */
    public String buildCategory(String label, String color) {
        return "<" + color + "><bold>" + label + "</bold></" + color + ">";
    }

    /**
     * Builds a category label using the default amber color.
     */
    public String buildCategory(String label) {
        return buildCategory(label, BLOCK_COLOR);
    }

    /**
     * Builds a help entry line: command + description, with proper spacing.
     * Intended for use inside help pages.
     */
    public String buildHelpEntry(String syntax, String description) {
        return "  " + syntax + DASH + "<" + TEXT_COLOR + ">" + description + "</" + TEXT_COLOR + ">";
    }

    /**
     * Builds a muted hint/example line.
     */
    public String buildHint(String text) {
        return "    <" + MUTED_COLOR + "><italic>" + text + "</italic></" + MUTED_COLOR + ">";
    }

    /**
     * Builds a bullet-point list item.
     */
    public String buildBullet(String label, String description) {
        return "  " + DOT + "<gray>" + label + "</gray>"
                + DASH
                + "<" + TEXT_COLOR + ">" + description + "</" + TEXT_COLOR + ">";
    }
}
