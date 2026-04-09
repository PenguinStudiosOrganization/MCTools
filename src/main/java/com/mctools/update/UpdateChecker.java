package com.mctools.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mctools.MCTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Async update checker that queries GitHub Releases API.
 * Notifies operators on the console and in-game when a new version is available.
 */
public class UpdateChecker implements Listener {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/%s/releases/latest";
    private static final long CHECK_INTERVAL_TICKS = 20L * 10; // 10 seconds

    private static final String GITHUB_REPO    = "PenguinStudiosOrganization/MCTools";
    private static final String ANNOUNCE_PERM  = "mctools.update.notify";
    private static final String DOWNLOAD_URL   = "https://github.com/PenguinStudiosOrganization/MCTools/releases/latest";
    private static final String DISCORD_URL    = "https://discord.penguinstudios.eu/";
    private static final String GITHUB_URL     = "https://github.com/PenguinStudiosOrganization/MCTools";

    // Token obfuscato con XOR + Base64 (3 parti).
    // La chiave XOR è separata dai dati — Base64 da solo è invertibile in 2 secondi,
    // XOR+Base64 richiede di capire lo schema prima di poterlo invertire.
    // Per aggiornare: XOR ogni byte con KEY[i % KEY.length], poi Base64-encoda.
    private static final byte[] TOKEN_KEY = "MCTools_UpdateChecker_2024".getBytes(StandardCharsets.UTF_8);
    private static final String[] TOKEN_PARTS = {
        "KiogBxoOLC80BDtQRScJIT9RIyRCPXx+VFMZNT0cWw==",
        "OC0LKT1dIgs0KFZSDBUFIAg2KVYDCwpqCl8OImJdNg==",
        "eBVgOT0PKhYxRRcILV05MDE0IDQ5HHlbWHwlFQYnPQ=="
    };

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Title color — bright mint green
    private static final String TITLE_COLOR  = "#a7f3d0";
    // Button colors
    private static final String BTN_DOWNLOAD = "#fbbf24"; // amber/gold
    private static final String BTN_DISCORD  = "#818cf8"; // indigo/blurple (Discord)
    private static final String BTN_GITHUB   = "#e2e8f0"; // near-white
    // Accent color (separator, inline highlights)
    private static final String ACCENT       = "#34d399";

    private final MCTools plugin;
    private final String githubToken;

    private BukkitTask scheduledTask;
    private String latestVersion;
    private boolean updateAvailable;
    private boolean isTestVersion;
    private String lastAnnouncedVersion;
    private boolean hasLoggedError;

    public UpdateChecker(MCTools plugin) {
        this.plugin = plugin;
        this.githubToken = decodeToken();
    }

    private static String decodeToken() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String part : TOKEN_PARTS) {
                byte[] bytes = Base64.getDecoder().decode(part);
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] ^= TOKEN_KEY[i % TOKEN_KEY.length];
                }
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Starts the update checker: immediate check on startup and periodic re-checks every 6 hours.
     * Also registers the PlayerJoinEvent listener for in-game notifications.
     */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        checkForUpdate();
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkForUpdate, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
        plugin.getLogger().info("Update checker started.");
    }

    /**
     * Cancels the scheduled update check task.
     */
    public void shutdown() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    /**
     * Performs an async update check against the GitHub Releases API.
     * Only announces when a new (previously unannounced) version is detected.
     */
    public void checkForUpdate() {
        CompletableFuture.runAsync(() -> {
            try {
                URI uri = URI.create(String.format(GITHUB_API_URL, GITHUB_REPO));
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setRequestProperty("User-Agent", "MCTools-UpdateChecker");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);

                if (githubToken != null && !githubToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + githubToken);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    if (!hasLoggedError) {
                        hasLoggedError = true;
                        plugin.getLogger().warning("Update check failed: HTTP " + responseCode);
                    }
                    return;
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                String tagName = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
                if (tagName == null || tagName.isEmpty()) return;

                latestVersion = cleanTagName(tagName);
                String currentVersion = plugin.getDescription().getVersion();
                isTestVersion = isTestOrDevVersion(tagName);

                if (isNewerVersion(currentVersion, latestVersion) || isTestVersion) {
                    if (lastAnnouncedVersion == null || !lastAnnouncedVersion.equals(latestVersion)) {
                        updateAvailable = true;
                        lastAnnouncedVersion = latestVersion;
                        Bukkit.getScheduler().runTask(plugin, this::broadcastUpdate);
                    }
                } else {
                    updateAvailable = false;
                }

            } catch (Exception e) {
                if (!hasLoggedError) {
                    hasLoggedError = true;
                    plugin.getLogger().warning("Update check failed: " + e.getMessage());
                }
            }
        });
    }

    private void broadcastUpdate() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(ANNOUNCE_PERM)) {
                sendUpdateMessage(player);
            }
        }
    }

    /**
     * Logs a formatted update banner to the server console using legacy color codes.
     */
    private void logUpdateToConsole(String currentVersion, String newVersion) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var c = Bukkit.getConsoleSender();
            c.sendMessage("");
            c.sendMessage("  §2§m                                                          ");
            c.sendMessage("");

            if (isTestVersion) {
                c.sendMessage("  §a§l⚠ UPDATE AVAILABLE §fv" + newVersion + " §8(§7test build§8)");
                c.sendMessage("");
                c.sendMessage("  §fThis is a §atest version§f — the plugin is §anot publicly downloadable§f yet.");
                c.sendMessage("  §fCheck Discord for changelogs and early access.");
                c.sendMessage("");
                c.sendMessage("  §fDiscord: §a§n" + DISCORD_URL);
                c.sendMessage("  §fGitHub:  §a§n" + GITHUB_URL);
            } else {
                c.sendMessage("  §a§l⚠ UPDATE AVAILABLE §fv" + newVersion);
                c.sendMessage("");
                c.sendMessage("  §fA new version of §a§lMCTools §fis available!");
                c.sendMessage("  §8(§7current: §f" + currentVersion + "§8)");
                c.sendMessage("");
                c.sendMessage("  §fDownload: §a§n" + DOWNLOAD_URL);
                c.sendMessage("  §fDiscord:  §a§n" + DISCORD_URL);
                c.sendMessage("  §fGitHub:   §a§n" + GITHUB_URL);
            }

            c.sendMessage("");
            c.sendMessage("  §2§m                                                          ");
            c.sendMessage("");
        });
    }

    /**
     * Sends a formatted in-game update notification with clickable links to a player.
     */
    private void sendUpdateMessage(Player player) {
        String sep = "<" + ACCENT + "><strikethrough>                                                    </strikethrough></" + ACCENT + ">";

        Component discordBtn = MM.deserialize(
                "<" + BTN_DISCORD + "><bold>[DISCORD]</bold></" + BTN_DISCORD + ">")
                .clickEvent(ClickEvent.openUrl(DISCORD_URL))
                .hoverEvent(HoverEvent.showText(MM.deserialize("<" + BTN_DISCORD + ">Join Discord</" + BTN_DISCORD + ">")));

        Component githubBtn = MM.deserialize(
                "<" + BTN_GITHUB + "><bold>[GITHUB]</bold></" + BTN_GITHUB + ">")
                .clickEvent(ClickEvent.openUrl(GITHUB_URL))
                .hoverEvent(HoverEvent.showText(MM.deserialize("<" + BTN_GITHUB + ">View source on GitHub</" + BTN_GITHUB + ">")));

        player.sendMessage(MM.deserialize(""));
        player.sendMessage(MM.deserialize(sep));
        player.sendMessage(MM.deserialize(""));

        if (isTestVersion) {
            player.sendMessage(MM.deserialize(
                    "  <" + TITLE_COLOR + "><bold>⚠ UPDATE AVAILABLE</bold></" + TITLE_COLOR + "> <white>v" + latestVersion + "</white> <gray>(test build)</gray>"));
            player.sendMessage(MM.deserialize(""));
            player.sendMessage(MM.deserialize(
                    "  <gray>This is a <" + ACCENT + ">test version</" + ACCENT + "> — not publicly downloadable yet.</gray>"));
            player.sendMessage(MM.deserialize(
                    "  <gray>Check Discord for changelogs and early access.</gray>"));
            player.sendMessage(MM.deserialize(""));
            player.sendMessage(
                    MM.deserialize("  <gray>Links: </gray>").append(discordBtn)
                            .append(MM.deserialize("  ")).append(githubBtn));
        } else {
            Component downloadBtn = MM.deserialize(
                    "<" + BTN_DOWNLOAD + "><bold>[DOWNLOAD]</bold></" + BTN_DOWNLOAD + ">")
                    .clickEvent(ClickEvent.openUrl(DOWNLOAD_URL))
                    .hoverEvent(HoverEvent.showText(MM.deserialize("<" + BTN_DOWNLOAD + ">Open GitHub Releases</" + BTN_DOWNLOAD + ">")));

            player.sendMessage(MM.deserialize(
                    "  <" + TITLE_COLOR + "><bold>⚠ UPDATE AVAILABLE</bold></" + TITLE_COLOR + "> <white>v" + latestVersion + "</white>"));
            player.sendMessage(MM.deserialize(""));
            player.sendMessage(MM.deserialize(
                    "  <gray>A new version of <" + ACCENT + "><bold>MCTools</bold></" + ACCENT + "> is available!</gray>"));
            player.sendMessage(MM.deserialize(""));
            player.sendMessage(
                    MM.deserialize("  <gray>Download: </gray>").append(downloadBtn)
                            .append(MM.deserialize("  ")).append(discordBtn)
                            .append(MM.deserialize("  ")).append(githubBtn));
        }

        player.sendMessage(MM.deserialize(""));
        player.sendMessage(MM.deserialize(sep));
        player.sendMessage(MM.deserialize(""));
    }

    /**
     * Notifies a player with the update permission when they join, if an update is available.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable || latestVersion == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(ANNOUNCE_PERM)) return;

        // Delay slightly so it appears after the MOTD / join messages
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) sendUpdateMessage(player);
        }, 40L);
    }

    // ── Version comparison helpers ───────────────────────────────────────

    /**
     * Returns true if {@code remoteVersion} is newer than {@code currentVersion}.
     */
    public static boolean isNewerVersion(String currentVersion, String remoteVersion) {
        if (currentVersion == null || remoteVersion == null) return false;

        String current = stripVersionSuffix(currentVersion);
        String remote  = stripVersionSuffix(remoteVersion);

        int[] cur = parseVersion(current);
        int[] rem = parseVersion(remote);

        int maxLen = Math.max(cur.length, rem.length);
        for (int i = 0; i < maxLen; i++) {
            int c = i < cur.length ? cur[i] : 0;
            int r = i < rem.length ? rem[i] : 0;
            if (r > c) return true;
            if (r < c) return false;
        }

        return isPreRelease(currentVersion) && !isPreRelease(remoteVersion);
    }

    private static String cleanTagName(String tagName) {
        if (tagName == null || tagName.isEmpty()) return tagName;
        String lower = tagName.toLowerCase();
        if (lower.startsWith("release-")) return tagName.substring("release-".length());
        if (lower.startsWith("rel-"))     return tagName.substring("rel-".length());
        if (lower.startsWith("v"))        return tagName.substring(1);
        return tagName;
    }

    private static String stripVersionSuffix(String version) {
        int idx = version.indexOf('-');
        return idx > 0 ? version.substring(0, idx) : version;
    }

    private static boolean isPreRelease(String version) {
        String u = version.toUpperCase();
        return u.contains("-BETA") || u.contains("-SNAPSHOT")
                || u.contains("-ALPHA") || u.contains("-RC")
                || u.contains("-DEV")   || u.contains("-TEST");
    }

    private static boolean isTestOrDevVersion(String tagName) {
        if (tagName == null || tagName.isEmpty()) return false;
        String u = tagName.toUpperCase();
        return u.contains("TEST") || u.contains("DEV");
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i].trim()); }
            catch (NumberFormatException ignored) { result[i] = 0; }
        }
        return result;
    }

    // ── Debug ────────────────────────────────────────────────────────────

    /**
     * Sends a fake update notification to {@code player} using {@code fakeVersion}.
     * Does not alter the real update state. Used for testing via /mct debug update.
     */
    public void sendDebugUpdate(Player player, String fakeVersion) {
        String saved = latestVersion;
        boolean savedTest = isTestVersion;

        latestVersion = fakeVersion;
        isTestVersion = false;
        sendUpdateMessage(player);

        latestVersion = saved;
        isTestVersion = savedTest;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String  getLatestVersion()  { return latestVersion; }
    public String  getDownloadUrl()    { return DOWNLOAD_URL; }
}
