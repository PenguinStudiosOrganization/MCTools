package com.mctools.path;

import com.mctools.MCTools;
import com.mctools.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for the MCT Path Tool system.
 *
 * <p>Manages per-player sessions, coordinates generators, handles commands,
 * and delegates block placement to the existing {@link com.mctools.utils.BlockPlacer}.</p>
 */
public class PathToolManager {

    private final MCTools plugin;
    private final Map<UUID, PathSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, PathParticleTask> particleTasks = new ConcurrentHashMap<>();

    private final CurveEngine curveEngine = new CurveEngine();
    private final RoadGenerator roadGenerator = new RoadGenerator();
    private final BridgeGenerator bridgeGenerator = new BridgeGenerator();

    // Config values (loaded from ConfigManager)
    private Set<Material> allowedShovels = Set.of(Material.WOODEN_SHOVEL);
    private int maxPoints = 50;
    private int maxPathLength = 2000;
    private int maxPreviewPoints = 5000;

    public PathToolManager(MCTools plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads path-tool configuration from config.yml.
     * Only operational limits are read from config; mode defaults are hardcoded.
     */
    public void loadConfig() {
        var config = plugin.getConfig();

        // Shovel materials
        List<String> shovelList = config.getStringList("path-tool.shovel-materials");
        if (shovelList.isEmpty()) {
            allowedShovels = Set.of(Material.WOODEN_SHOVEL);
        } else {
            Set<Material> shovels = new HashSet<>();
            for (String name : shovelList) {
                try {
                    shovels.add(Material.valueOf(name.toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
            if (shovels.isEmpty()) shovels.add(Material.WOODEN_SHOVEL);
            allowedShovels = shovels;
        }

        maxPoints = config.getInt("path-tool.max-points", 50);
        maxPathLength = config.getInt("path-tool.max-path-length", 2000);
    }

    // ── Session management ──

    /**
     * Gets or creates a session for the given player.
     */
    public PathSession getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), uuid -> {
            PathSession session = new PathSession(uuid);
            // Load config defaults
            loadSessionDefaults(session);
            return session;
        });
    }

    /**
     * Loads hardcoded defaults into a session.
     * Mode-specific defaults are not stored in config — they are defined here.
     */
    private void loadSessionDefaults(PathSession session) {
        Map<String, Object> curveDefaults = new LinkedHashMap<>();
        curveDefaults.put("resolution", 0.5);
        curveDefaults.put("algorithm", "catmullrom");

        Map<String, Object> roadDefaults = new LinkedHashMap<>();
        roadDefaults.put("width", 5);
        roadDefaults.put("material", "STONE_BRICKS");
        roadDefaults.put("border", "POLISHED_ANDESITE");
        roadDefaults.put("centerline", "none");
        roadDefaults.put("use-slabs", true);
        roadDefaults.put("use-stairs", true);
        roadDefaults.put("terrain-adapt", true);
        roadDefaults.put("clearance", 3);
        roadDefaults.put("fill-below", 4);
        roadDefaults.put("fill-material", "COBBLESTONE");
        roadDefaults.put("resolution", 0.5);

        Map<String, Object> bridgeDefaults = new LinkedHashMap<>();
        bridgeDefaults.put("width", 5);
        bridgeDefaults.put("deck-material", "STONE_BRICK_SLAB");
        bridgeDefaults.put("railings", true);
        bridgeDefaults.put("railing-material", "STONE_BRICK_WALL");
        bridgeDefaults.put("supports", true);
        bridgeDefaults.put("support-material", "STONE_BRICKS");
        bridgeDefaults.put("support-spacing", 8);
        bridgeDefaults.put("support-width", 3);
        bridgeDefaults.put("support-max-depth", 40);
        bridgeDefaults.put("height-mode", "auto");
        bridgeDefaults.put("ramps", true);
        bridgeDefaults.put("ramp-material", "STONE_BRICK_STAIRS");
        bridgeDefaults.put("resolution", 0.5);

        session.loadConfigDefaults(curveDefaults, roadDefaults, bridgeDefaults);
    }

    /**
     * Removes a player's session and stops their particle tasks.
     */
    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
        PathParticleTask task = particleTasks.remove(playerId);
        if (task != null) {
            task.stopAll();
        }
    }

    /**
     * Checks if a material is an allowed shovel.
     */
    public boolean isAllowedShovel(Material material) {
        return allowedShovels.contains(material);
    }

    // ── Particle management ──

    private PathParticleTask getParticleTask(Player player) {
        return particleTasks.computeIfAbsent(player.getUniqueId(), uuid -> {
            PathParticleTask task = new PathParticleTask(plugin, uuid);
            // Hardcoded particle defaults (not configurable via config.yml)
            task.setSelectionParticle(Particle.HAPPY_VILLAGER);
            task.setPreviewParticle(Particle.FLAME);
            task.setPreviewSupportParticle(Particle.SOUL_FIRE_FLAME);
            task.setSelectionInterval(10);
            task.setPreviewInterval(20);
            task.setMaxDistance(128);
            task.setVisibleToAll(false);
            return task;
        });
    }

    /**
     * Refreshes selection particles for a player (call after position changes).
     */
    public void refreshSelectionParticles(Player player) {
        PathSession session = getSession(player);
        if (session.isSelectionParticles() && session.isToolEnabled() && session.getPositionCount() > 0) {
            getParticleTask(player).startSelectionParticles(session);
        } else {
            getParticleTask(player).stopSelectionParticles();
        }
    }

    /**
     * Stops all particles for a player.
     */
    public void stopAllParticles(Player player) {
        PathParticleTask task = particleTasks.get(player.getUniqueId());
        if (task != null) {
            task.stopAll();
        }
    }

    // ── Command handlers ──

    /**
     * Handles /mct tool enable|disable
     */
    public void handleToolToggle(Player player, boolean enable) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        if (enable && session.isToolEnabled()) {
            msg.sendInfo(player, "Path tool is already enabled.");
            return;
        }
        if (!enable && !session.isToolEnabled()) {
            msg.sendInfo(player, "Path tool is already disabled.");
            return;
        }

        session.setToolEnabled(enable);
        if (enable) {
            msg.sendInfo(player, "Path tool enabled. Use a shovel to select points.");
            refreshSelectionParticles(player);
        } else {
            msg.sendInfo(player, "Path tool disabled.");
            stopAllParticles(player);
        }
    }

    /**
     * Handles /mct mode road|bridge|curve
     */
    public void handleModeSet(Player player, String modeName) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        PathSession.Mode mode;
        try {
            mode = PathSession.Mode.valueOf(modeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            msg.sendError(player, "Unknown mode: " + modeName + ". Use: road, bridge, curve");
            return;
        }

        session.setActiveMode(mode);
        String displayName = mode.name().charAt(0) + mode.name().substring(1).toLowerCase();
        msg.sendInfo(player, "Mode set to " + displayName + ".");
    }

    /**
     * Handles /mct pos list|undo|clear
     */
    public void handlePos(Player player, String action) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        switch (action.toLowerCase()) {
            case "list" -> {
                List<Location> positions = session.getPositions();
                if (positions.isEmpty()) {
                    msg.sendInfo(player, "No positions stored. Use a shovel to select points.");
                    return;
                }
                msg.sendRaw(player, "");
                msg.sendRaw(player, msg.buildHeader("Path Positions"));
                msg.sendRaw(player, "");
                for (int i = 0; i < positions.size(); i++) {
                    Location loc = positions.get(i);
                    msg.sendRaw(player, msg.buildDetail("Pos" + (i + 1),
                            "<white>(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")</white>"));
                }
                msg.sendRaw(player, "");
                msg.sendInfo(player, positions.size() + " points total.");
            }
            case "undo" -> {
                Location removed = session.removeLastPosition();
                if (removed == null) {
                    msg.sendError(player, "No positions to remove.");
                    return;
                }
                int remaining = session.getPositionCount();
                msg.sendInfo(player, "Removed Pos" + (remaining + 1) + " at (" +
                        removed.getBlockX() + ", " + removed.getBlockY() + ", " + removed.getBlockZ() +
                        "). (" + remaining + " points remaining)");
                refreshSelectionParticles(player);
                // Stop preview if active (positions changed)
                if (session.isPreviewActive()) {
                    session.setPreviewActive(false);
                    getParticleTask(player).stopPreviewParticles();
                }
            }
            case "clear" -> {
                session.clearPositions();
                msg.sendInfo(player, "All positions cleared.");
                refreshSelectionParticles(player);
                if (session.isPreviewActive()) {
                    session.setPreviewActive(false);
                    getParticleTask(player).stopPreviewParticles();
                }
            }
            default -> msg.sendUsage(player, "/mct pos <list|undo|clear>");
        }
    }

    /**
     * Handles /mct set [key] [value]
     */
    public void handleSet(Player player, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        if (!session.hasMode()) {
            sendNoModeError(player, msg);
            return;
        }

        PathSession.Mode mode = session.getActiveMode();
        String modeName = mode.name().charAt(0) + mode.name().substring(1).toLowerCase();

        // No args: open the settings GUI
        if (args.length == 0) {
            new com.mctools.path.gui.PathSettingsGUI(plugin, player).open();
            return;
        }

        if (args.length < 2) {
            msg.sendUsage(player, "/mct set <key> <value>");
            return;
        }

        String key = args[0].toLowerCase();
        String value = args[1];

        // Validate key
        Set<String> validKeys = PathSession.getValidKeys(mode);
        if (!validKeys.contains(key)) {
            msg.sendError(player, "Unknown setting \"" + key + "\" for " + modeName + " mode. Valid keys: " +
                    String.join(", ", validKeys));
            return;
        }

        // Parse and validate value based on key type
        Object parsedValue = parseSettingValue(key, value, mode, msg, player);
        if (parsedValue == null) return; // Error already sent

        session.setSetting(key, parsedValue);
        msg.sendInfo(player, modeName + " " + key + " set to " + parsedValue + ".");

        // Refresh preview if active
        if (session.isPreviewActive()) {
            startPreview(player);
        }
    }

    /**
     * Parses a setting value string into the correct type.
     * Returns null and sends an error if invalid.
     */
    private Object parseSettingValue(String key, String value, PathSession.Mode mode,
                                      MessageUtil msg, Player player) {
        // Integer settings
        Set<String> intKeys = Set.of("width", "clearance", "fill-below", "support-spacing", "support-width", "support-max-depth");
        if (intKeys.contains(key)) {
            try {
                int intVal = Integer.parseInt(value);
                // Validate ranges
                return switch (key) {
                    case "width" -> {
                        if (intVal < 1 || intVal > 32) {
                            msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: integer 1-32.");
                            yield null;
                        }
                        yield intVal;
                    }
                    case "clearance" -> {
                        if (intVal < 1 || intVal > 10) {
                            msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: integer 1-10.");
                            yield null;
                        }
                        yield intVal;
                    }
                    case "fill-below" -> {
                        if (intVal < 0 || intVal > 20) {
                            msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: integer 0-20.");
                            yield null;
                        }
                        yield intVal;
                    }
                    case "support-spacing" -> {
                        if (intVal < 3 || intVal > 50) {
                            msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: integer 3-50.");
                            yield null;
                        }
                        yield intVal;
                    }
                    case "support-width" -> {
                        if (intVal < 1 || intVal > 10) {
                            msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: integer 1-10.");
                            yield null;
                        }
                        yield intVal;
                    }
                    case "support-max-depth" -> {
                        if (intVal < 1 || intVal > 128) {
                            msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: integer 1-128.");
                            yield null;
                        }
                        yield intVal;
                    }
                    default -> intVal;
                };
            } catch (NumberFormatException e) {
                msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: integer.");
                return null;
            }
        }

        // Double settings
        if (key.equals("resolution")) {
            try {
                double dVal = Double.parseDouble(value);
                if (dVal < 0.1 || dVal > 2.0) {
                    msg.sendError(player, "Invalid value \"" + value + "\" for resolution. Expected: decimal 0.1-2.0.");
                    return null;
                }
                return dVal;
            } catch (NumberFormatException e) {
                msg.sendError(player, "Invalid value \"" + value + "\" for resolution. Expected: decimal.");
                return null;
            }
        }

        // Boolean settings
        Set<String> boolKeys = Set.of("use-slabs", "use-stairs", "terrain-adapt", "railings", "supports", "ramps");
        if (boolKeys.contains(key)) {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(value);
            }
            msg.sendError(player, "Invalid value \"" + value + "\" for " + key + ". Expected: true or false.");
            return null;
        }

        // String enum settings
        if (key.equals("algorithm")) {
            if (value.equalsIgnoreCase("catmullrom") || value.equalsIgnoreCase("bezier")) {
                return value.toLowerCase();
            }
            msg.sendError(player, "Invalid value \"" + value + "\" for algorithm. Expected: catmullrom or bezier.");
            return null;
        }

        if (key.equals("height-mode")) {
            if (value.equalsIgnoreCase("fixed") || value.equalsIgnoreCase("auto")) {
                return value.toLowerCase();
            }
            msg.sendError(player, "Invalid value \"" + value + "\" for height-mode. Expected: fixed or auto.");
            return null;
        }

        // Material settings
        Set<String> materialKeys = Set.of("material", "border", "centerline", "fill-material",
                "deck-material", "railing-material", "support-material", "ramp-material");
        if (materialKeys.contains(key)) {
            if (value.equalsIgnoreCase("none")) {
                return "none";
            }
            try {
                Material.valueOf(value.toUpperCase());
                return value.toUpperCase();
            } catch (IllegalArgumentException e) {
                msg.sendError(player, "Unknown block type \"" + value + "\". Use a valid Minecraft material name.");
                return null;
            }
        }

        return value;
    }

    /**
     * Handles /mct preview on|off
     */
    public void handlePreview(Player player, String state) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        if (!session.hasMode()) {
            sendNoModeError(player, msg);
            return;
        }

        if (state.equalsIgnoreCase("on")) {
            if (session.getPositionCount() < 2) {
                msg.sendError(player, "Not enough points. Select at least 2 positions with the shovel.");
                return;
            }
            if (!session.allSameWorld()) {
                msg.sendError(player, "All positions must be in the same world.");
                return;
            }
            startPreview(player);
        } else if (state.equalsIgnoreCase("off")) {
            session.setPreviewActive(false);
            getParticleTask(player).stopPreviewParticles();
            msg.sendInfo(player, "Preview disabled.");
        } else {
            msg.sendUsage(player, "/mct preview <on|off>");
        }
    }

    /**
     * Starts the preview particle rendering.
     */
    private void startPreview(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        double resolution = session.getSettingDouble("resolution", 0.5);
        String algorithm = session.getSettingString("algorithm", "catmullrom");
        int width = 1;

        if (session.getActiveMode() == PathSession.Mode.ROAD) {
            width = session.getSettingInt("width", 5);
        } else if (session.getActiveMode() == PathSession.Mode.BRIDGE) {
            width = session.getSettingInt("width", 5);
        }

        List<Vector> sampledPath = curveEngine.sampleCurve(session.getPositions(), resolution, algorithm);

        if (sampledPath.size() > maxPreviewPoints) {
            // Downsample
            List<Vector> downsampled = new ArrayList<>();
            double step = (double) sampledPath.size() / maxPreviewPoints;
            for (double idx = 0; idx < sampledPath.size(); idx += step) {
                downsampled.add(sampledPath.get((int) idx));
            }
            sampledPath = downsampled;
            msg.sendWarning(player, "Preview downsampled for performance (" + maxPreviewPoints + " point limit).");
        }

        World world = session.getPositions().get(0).getWorld();
        getParticleTask(player).startPreviewParticles(sampledPath, world, width);
        session.setPreviewActive(true);

        String modeName = session.getActiveMode().name().charAt(0) +
                session.getActiveMode().name().substring(1).toLowerCase();
        msg.sendInfo(player, "Preview enabled for " + modeName + " mode. Use /mct preview off to hide.");
    }

    /**
     * Handles /mct generate
     */
    public void handleGenerate(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        // Validate mode
        if (!session.hasMode()) {
            sendNoModeError(player, msg);
            return;
        }

        // Validate positions
        if (session.getPositionCount() < 2) {
            msg.sendError(player, "Not enough points. Select at least 2 positions with the shovel.");
            return;
        }

        if (!session.allSameWorld()) {
            msg.sendError(player, "All positions must be in the same world.");
            return;
        }

        // Check path length
        double pathLength = session.getTotalPathLength();
        if (pathLength > maxPathLength && !player.hasPermission("mctools.path.bypass.limit")) {
            msg.sendError(player, "Path too long (" + String.format("%,.0f", pathLength) +
                    " blocks). Maximum allowed: " + String.format("%,d", maxPathLength) +
                    ". Remove points or increase the limit.");
            return;
        }

        // Check for active task
        if (plugin.getBlockPlacer().hasActiveTask(player)) {
            msg.sendError(player, "A generation is already in progress. Use /mct cancel first.");
            return;
        }

        // Curve mode is preview-only
        if (session.getActiveMode() == PathSession.Mode.CURVE) {
            msg.sendInfo(player, "Curve mode is preview-only. Switch to road or bridge to generate blocks.");
            return;
        }

        // Stop preview particles
        if (session.isPreviewActive()) {
            session.setPreviewActive(false);
            getParticleTask(player).stopPreviewParticles();
        }

        // Generate block map
        Map<Location, BlockData> blockMap;
        String shapeName;

        if (session.getActiveMode() == PathSession.Mode.ROAD) {
            blockMap = roadGenerator.generate(session);
            shapeName = "Road";
        } else {
            blockMap = bridgeGenerator.generate(session);
            shapeName = "Bridge";
        }

        if (blockMap.isEmpty()) {
            msg.sendError(player, "No blocks to place!");
            return;
        }

        // Check block limit
        int maxBlocks = plugin.getConfigManager().getMaxBlocks();
        if (maxBlocks > 0 && blockMap.size() > maxBlocks) {
            msg.sendError(player, "Operation would place " + String.format("%,d", blockMap.size()) +
                    " blocks (max: " + String.format("%,d", maxBlocks) + ")");
            msg.sendInfo(player, "Reduce the path length/width or ask an admin to increase max-blocks in config.");
            plugin.getBlockPlacer().playErrorEffects(player);
            return;
        }

        // Delegate to BlockPlacer
        msg.sendInfo(player, "Preparing " + shapeName + " with " + String.format("%,d", blockMap.size()) + " blocks...");
        if (blockMap.size() > 1000) {
            msg.sendRaw(player, plugin.getPerformanceMonitor().getFullPerformanceSummaryMiniMessage());
        }
        plugin.getBlockPlacer().placeGradientBlocks(player, blockMap, shapeName);
    }

    /**
     * Handles /mct particles on|off
     */
    public void handleParticlesToggle(Player player, String state) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        if (state.equalsIgnoreCase("on")) {
            session.setSelectionParticles(true);
            msg.sendInfo(player, "Selection particles enabled.");
            refreshSelectionParticles(player);
        } else if (state.equalsIgnoreCase("off")) {
            session.setSelectionParticles(false);
            msg.sendInfo(player, "Selection particles disabled.");
            getParticleTask(player).stopSelectionParticles();
        } else {
            msg.sendUsage(player, "/mct particles <on|off>");
        }
    }

    // ── Shovel click handling (called from PlayerListener) ──

    /**
     * Handles /mct sel — resets the selection (clears all positions and stops particles/preview).
     */
    public void handleSelectionReset(Player player) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        session.clearPositions();
        msg.sendInfo(player, "Selection reset. All positions cleared.");

        refreshSelectionParticles(player);
        if (session.isPreviewActive()) {
            session.setPreviewActive(false);
            getParticleTask(player).stopPreviewParticles();
        }
    }

    /**
     * Handles left-click with shovel: set Pos1 and reset path.
     * If shift is held, resets the entire selection instead.
     */
    public void handleLeftClick(Player player, Location blockLocation, boolean shift) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        if (!session.isToolEnabled()) return;

        // Shift + left-click = reset selection
        if (shift) {
            handleSelectionReset(player);
            return;
        }

        session.setPos1(blockLocation);
        msg.sendInfo(player, "Pos1 set at (" + blockLocation.getBlockX() + ", " +
                blockLocation.getBlockY() + ", " + blockLocation.getBlockZ() + "). Path reset.");

        refreshSelectionParticles(player);

        // Stop preview if active
        if (session.isPreviewActive()) {
            session.setPreviewActive(false);
            getParticleTask(player).stopPreviewParticles();
        }
    }

    /**
     * Handles right-click with shovel: append a new position.
     */
    public void handleRightClick(Player player, Location blockLocation) {
        MessageUtil msg = plugin.getMessageUtil();
        PathSession session = getSession(player);

        if (!session.isToolEnabled()) return;

        // Check max points
        if (session.getPositionCount() >= maxPoints && !player.hasPermission("mctools.path.bypass.limit")) {
            msg.sendError(player, "Maximum points reached (" + maxPoints +
                    "). Remove a point with /mct pos undo or clear with /mct pos clear.");
            return;
        }

        int posNum = session.addPosition(blockLocation);
        msg.sendInfo(player, "Pos" + posNum + " added at (" + blockLocation.getBlockX() + ", " +
                blockLocation.getBlockY() + ", " + blockLocation.getBlockZ() +
                "). (" + posNum + " points total)");

        refreshSelectionParticles(player);

        // Stop preview if active (positions changed)
        if (session.isPreviewActive()) {
            session.setPreviewActive(false);
            getParticleTask(player).stopPreviewParticles();
        }
    }

    /**
     * Sends the path tools help page.
     */
    public void sendHelp(Player player) {
        MessageUtil msg = plugin.getMessageUtil();

        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse(msg.buildHeader("Path Tools Help")));
        player.sendMessage(msg.parse(""));

        player.sendMessage(msg.parse("  " + msg.buildCategory("Selection")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct tool enable|disable", "Enable/disable shovel selection tool")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct pos list", "List all selected positions")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct pos undo", "Remove the last position")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct pos clear", "Clear all positions")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct particles on|off", "Toggle selection particles")));

        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse("  " + msg.buildCategory("Mode & Generation")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct mode <" + MessageUtil.ROAD_COLOR + ">road</" + MessageUtil.ROAD_COLOR + ">|<" + MessageUtil.BRIDGE_COLOR + ">bridge</" + MessageUtil.BRIDGE_COLOR + ">|<" + MessageUtil.CURVE_COLOR + ">curve</" + MessageUtil.CURVE_COLOR + ">", "Set the active generator mode")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct set [key] [value]", "View/change mode settings")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct preview on|off", "Toggle particle preview")));
        player.sendMessage(msg.parse(msg.buildHelpEntry("/mct generate", "Generate the structure along the path")));

        player.sendMessage(msg.parse(""));
        player.sendMessage(msg.parse(msg.buildHint("Left-click with shovel = set Pos1 (reset path)")));
        player.sendMessage(msg.parse(msg.buildHint("Right-click with shovel = add next position")));
        player.sendMessage(msg.parse(""));
    }

    // ── Shared error messages ──

    /**
     * Sends a well-formatted "no mode selected" error with the command highlighted.
     */
    private void sendNoModeError(Player player, MessageUtil msg) {
        msg.sendError(player, "Select a mode first: <" + MessageUtil.CMD_COLOR + ">/mct mode</" + MessageUtil.CMD_COLOR + "> <" + MessageUtil.ROAD_COLOR + ">road</" + MessageUtil.ROAD_COLOR + ">|<" + MessageUtil.BRIDGE_COLOR + ">bridge</" + MessageUtil.BRIDGE_COLOR + ">|<" + MessageUtil.CURVE_COLOR + ">curve</" + MessageUtil.CURVE_COLOR + ">");
    }

    // ── Getters ──

    public int getMaxPoints() { return maxPoints; }
    public int getMaxPathLength() { return maxPathLength; }
    public Set<Material> getAllowedShovels() { return allowedShovels; }
}
