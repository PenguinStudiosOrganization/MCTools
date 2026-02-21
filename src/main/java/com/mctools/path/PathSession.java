package com.mctools.path;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

/**
 * Per-player session data for the MCT Path Tool system.
 *
 * <p>Stores the player's selected positions, active mode, mode-specific settings,
 * and toggle states (tool enabled, particles, preview).</p>
 *
 * <p>Sessions are created on first interaction and cleared on disconnect.</p>
 */
public class PathSession {

    /**
     * Available path tool modes.
     */
    public enum Mode {
        ROAD, BRIDGE, CURVE
    }

    private final UUID playerId;
    private final List<Location> positions = new ArrayList<>();
    private Mode activeMode = null;
    private boolean toolEnabled = false;
    private boolean selectionParticles = true;
    private boolean previewActive = false;

    // Per-mode settings stored as maps
    private final Map<String, Object> roadSettings = new LinkedHashMap<>();
    private final Map<String, Object> bridgeSettings = new LinkedHashMap<>();
    private final Map<String, Object> curveSettings = new LinkedHashMap<>();

    public PathSession(UUID playerId) {
        this.playerId = playerId;
        initDefaults();
    }

    private void initDefaults() {
        // Curve defaults
        curveSettings.put("resolution", 0.5);
        curveSettings.put("algorithm", "catmullrom");

        // Road defaults
        roadSettings.put("width", 5);
        roadSettings.put("material", "STONE_BRICKS");
        roadSettings.put("border", "POLISHED_ANDESITE");
        roadSettings.put("centerline", "none");
        roadSettings.put("use-slabs", true);
        roadSettings.put("use-stairs", true);
        roadSettings.put("terrain-adapt", true);
        roadSettings.put("clearance", 3);
        roadSettings.put("fill-below", 4);
        roadSettings.put("fill-material", "COBBLESTONE");
        roadSettings.put("resolution", 0.5);

        // Bridge defaults
        bridgeSettings.put("width", 5);
        bridgeSettings.put("deck-material", "STONE_BRICK_SLAB");
        bridgeSettings.put("railings", true);
        bridgeSettings.put("railing-material", "STONE_BRICK_WALL");
        bridgeSettings.put("supports", true);
        bridgeSettings.put("support-material", "STONE_BRICKS");
        bridgeSettings.put("support-spacing", 8);
        bridgeSettings.put("support-width", 3);
        bridgeSettings.put("support-max-depth", 40);
        bridgeSettings.put("height-mode", "auto");
        bridgeSettings.put("ramps", true);
        bridgeSettings.put("ramp-material", "STONE_BRICK_STAIRS");
        bridgeSettings.put("resolution", 0.5);
    }

    /**
     * Loads defaults from config values.
     */
    public void loadConfigDefaults(Map<String, Object> curveDefaults,
                                   Map<String, Object> roadDefaults,
                                   Map<String, Object> bridgeDefaults) {
        if (curveDefaults != null) curveSettings.putAll(curveDefaults);
        if (roadDefaults != null) roadSettings.putAll(roadDefaults);
        if (bridgeDefaults != null) bridgeSettings.putAll(bridgeDefaults);
    }

    // ── Positions ──

    public List<Location> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    public int getPositionCount() {
        return positions.size();
    }

    /**
     * Sets Pos1 and clears all other positions (path reset).
     */
    public void setPos1(Location loc) {
        positions.clear();
        positions.add(loc.clone());
    }

    /**
     * Appends a new position to the path.
     * @return the index of the new position (1-based)
     */
    public int addPosition(Location loc) {
        positions.add(loc.clone());
        return positions.size();
    }

    /**
     * Removes the last position.
     * @return the removed location, or null if empty
     */
    public Location removeLastPosition() {
        if (positions.isEmpty()) return null;
        return positions.remove(positions.size() - 1);
    }

    public void clearPositions() {
        positions.clear();
    }

    /**
     * Calculates the total path length (sum of segment distances).
     */
    public double getTotalPathLength() {
        double total = 0;
        for (int i = 1; i < positions.size(); i++) {
            total += positions.get(i).distance(positions.get(i - 1));
        }
        return total;
    }

    /**
     * Checks if all positions are in the same world.
     */
    public boolean allSameWorld() {
        if (positions.size() < 2) return true;
        var world = positions.get(0).getWorld();
        for (int i = 1; i < positions.size(); i++) {
            if (!Objects.equals(positions.get(i).getWorld(), world)) {
                return false;
            }
        }
        return true;
    }

    // ── Mode ──

    public Mode getActiveMode() {
        return activeMode;
    }

    public void setActiveMode(Mode mode) {
        this.activeMode = mode;
    }

    public boolean hasMode() {
        return activeMode != null;
    }

    // ── Tool toggle ──

    public boolean isToolEnabled() {
        return toolEnabled;
    }

    public void setToolEnabled(boolean enabled) {
        this.toolEnabled = enabled;
    }

    // ── Selection particles ──

    public boolean isSelectionParticles() {
        return selectionParticles;
    }

    public void setSelectionParticles(boolean enabled) {
        this.selectionParticles = enabled;
    }

    // ── Preview ──

    public boolean isPreviewActive() {
        return previewActive;
    }

    public void setPreviewActive(boolean active) {
        this.previewActive = active;
    }

    // ── Settings ──

    /**
     * Gets the settings map for the currently active mode.
     * @return the settings map, or null if no mode is selected
     */
    public Map<String, Object> getActiveModeSettings() {
        if (activeMode == null) return null;
        return switch (activeMode) {
            case ROAD -> roadSettings;
            case BRIDGE -> bridgeSettings;
            case CURVE -> curveSettings;
        };
    }

    public Map<String, Object> getRoadSettings() {
        return roadSettings;
    }

    public Map<String, Object> getBridgeSettings() {
        return bridgeSettings;
    }

    public Map<String, Object> getCurveSettings() {
        return curveSettings;
    }

    /**
     * Sets a value in the active mode's settings.
     * @return true if the key was valid for the current mode
     */
    public boolean setSetting(String key, Object value) {
        Map<String, Object> settings = getActiveModeSettings();
        if (settings == null) return false;
        if (!settings.containsKey(key)) return false;
        settings.put(key, value);
        return true;
    }

    /**
     * Gets a setting value, cast to the expected type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getSetting(String key, Class<T> type) {
        Map<String, Object> settings = getActiveModeSettings();
        if (settings == null) return null;
        Object val = settings.get(key);
        if (val == null) return null;
        if (type.isInstance(val)) return (T) val;
        return null;
    }

    /**
     * Gets a setting as int, handling both Integer and Double storage.
     */
    public int getSettingInt(String key, int defaultVal) {
        Map<String, Object> settings = getActiveModeSettings();
        if (settings == null) return defaultVal;
        Object val = settings.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof Double d) return d.intValue();
        if (val instanceof Number n) return n.intValue();
        return defaultVal;
    }

    /**
     * Gets a setting as double.
     */
    public double getSettingDouble(String key, double defaultVal) {
        Map<String, Object> settings = getActiveModeSettings();
        if (settings == null) return defaultVal;
        Object val = settings.get(key);
        if (val instanceof Double d) return d;
        if (val instanceof Integer i) return i.doubleValue();
        if (val instanceof Number n) return n.doubleValue();
        return defaultVal;
    }

    /**
     * Gets a setting as boolean.
     */
    public boolean getSettingBool(String key, boolean defaultVal) {
        Map<String, Object> settings = getActiveModeSettings();
        if (settings == null) return defaultVal;
        Object val = settings.get(key);
        if (val instanceof Boolean b) return b;
        return defaultVal;
    }

    /**
     * Gets a setting as String.
     */
    public String getSettingString(String key, String defaultVal) {
        Map<String, Object> settings = getActiveModeSettings();
        if (settings == null) return defaultVal;
        Object val = settings.get(key);
        if (val instanceof String s) return s;
        return defaultVal;
    }

    /**
     * Resolves a material name setting to a Material, or null if "none".
     */
    public Material getSettingMaterial(String key) {
        String name = getSettingString(key, "none");
        if (name == null || name.equalsIgnoreCase("none")) return null;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Returns valid setting keys for a given mode.
     */
    public static Set<String> getValidKeys(Mode mode) {
        return switch (mode) {
            case ROAD -> Set.of("width", "material", "border", "centerline", "use-slabs", "use-stairs",
                    "terrain-adapt", "clearance", "fill-below", "fill-material", "resolution");
            case BRIDGE -> Set.of("width", "deck-material", "railings", "railing-material", "supports",
                    "support-material", "support-spacing", "support-width", "support-max-depth", "height-mode",
                    "ramps", "ramp-material", "resolution");
            case CURVE -> Set.of("resolution", "algorithm");
        };
    }
}
