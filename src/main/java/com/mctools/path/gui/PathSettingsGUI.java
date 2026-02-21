package com.mctools.path.gui;

import com.mctools.MCTools;
import com.mctools.path.PathSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settings GUI for Path Tool modes (Road, Bridge, Curve).
 *
 * <p>Opens a chest inventory with clickable items for each setting.
 * Left-click increments / cycles forward, right-click decrements / cycles backward.
 * Shift-click uses larger steps for numeric values.</p>
 *
 * <p>One class handles all three modes — the layout adapts based on the active mode.</p>
 */
public class PathSettingsGUI implements InventoryHolder {

    private final MCTools plugin;
    private final Player player;
    private final PathSession session;
    private final PathSession.Mode mode;
    private final Inventory inventory;

    // ── Slot assignments per mode ──

    // Road slots (54-slot chest)
    public static final int ROAD_WIDTH = 10;
    public static final int ROAD_MATERIAL = 12;
    public static final int ROAD_BORDER = 14;
    public static final int ROAD_CENTERLINE = 16;
    public static final int ROAD_USE_SLABS = 19;
    public static final int ROAD_USE_STAIRS = 21;
    public static final int ROAD_TERRAIN_ADAPT = 23;
    public static final int ROAD_CLEARANCE = 25;
    public static final int ROAD_FILL_BELOW = 28;
    public static final int ROAD_FILL_MATERIAL = 30;
    public static final int ROAD_RESOLUTION = 32;

    // Bridge slots (54-slot chest)
    public static final int BRIDGE_WIDTH = 10;
    public static final int BRIDGE_DECK_MATERIAL = 12;
    public static final int BRIDGE_RAILINGS = 14;
    public static final int BRIDGE_RAILING_MAT = 16;
    public static final int BRIDGE_SUPPORTS = 19;
    public static final int BRIDGE_SUPPORT_MAT = 21;
    public static final int BRIDGE_SUPPORT_SPACING = 23;
    public static final int BRIDGE_SUPPORT_WIDTH = 25;
    public static final int BRIDGE_SUPPORT_DEPTH = 28;
    public static final int BRIDGE_HEIGHT_MODE = 30;
    public static final int BRIDGE_RAMPS = 32;
    public static final int BRIDGE_RAMP_MAT = 34;
    public static final int BRIDGE_RESOLUTION = 37;

    // Curve slots (27-slot chest)
    public static final int CURVE_RESOLUTION = 11;
    public static final int CURVE_ALGORITHM = 15;

    public PathSettingsGUI(MCTools plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.session = plugin.getPathToolManager().getSession(player);
        this.mode = session.getActiveMode();

        int size = (mode == PathSession.Mode.CURVE) ? 27 : 54;
        String title = switch (mode) {
            case ROAD -> "§6§lRoad Settings";
            case BRIDGE -> "§b§lBridge Settings";
            case CURVE -> "§d§lCurve Settings";
        };

        this.inventory = Bukkit.createInventory(this, size,
                Component.text(title).decoration(TextDecoration.ITALIC, false));

        setupItems();
    }

    private void setupItems() {
        fillBackground();

        switch (mode) {
            case ROAD -> setupRoadItems();
            case BRIDGE -> setupBridgeItems();
            case CURVE -> setupCurveItems();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ROAD
    // ═══════════════════════════════════════════════════════════════

    private void setupRoadItems() {
        Map<String, Object> s = session.getRoadSettings();

        int width = toInt(s.get("width"), 5);
        String material = toStr(s.get("material"), "STONE_BRICKS");
        String border = toStr(s.get("border"), "POLISHED_ANDESITE");
        String centerline = toStr(s.get("centerline"), "none");
        boolean useSlabs = toBool(s.get("use-slabs"), true);
        boolean useStairs = toBool(s.get("use-stairs"), true);
        boolean terrainAdapt = toBool(s.get("terrain-adapt"), true);
        int clearance = toInt(s.get("clearance"), 3);
        int fillBelow = toInt(s.get("fill-below"), 4);
        String fillMaterial = toStr(s.get("fill-material"), "COBBLESTONE");
        double resolution = toDbl(s.get("resolution"), 0.5);

        inventory.setItem(ROAD_WIDTH, createItem(Material.RAIL, "§b§lWIDTH: §f" + width, List.of(
                "", "§7Road width in blocks.", "",
                createBar(width, 32, "§b"), "",
                "§e➤ Left: §f+1 §8| §e➤ Right: §f-1",
                "§e➤ Shift: §f±2")));

        inventory.setItem(ROAD_MATERIAL, createItem(safeMaterial(material, Material.STONE_BRICKS),
                "§6§lMATERIAL", List.of("", "§7Main road surface block.", "",
                        "§8├ §7Current: §f" + formatMat(material), "",
                        "§e➤ Click to cycle materials",
                        "§b➤ Hold a block to set custom")));

        inventory.setItem(ROAD_BORDER, createItem(safeMaterial(border, Material.POLISHED_ANDESITE),
                "§e§lBORDER", List.of("", "§7Edge block on both sides.", "",
                        "§8├ §7Current: §f" + formatMat(border), "",
                        "§e➤ Click to cycle §8| §e➤ Right: §fnone",
                        "§b➤ Hold a block to set custom")));

        inventory.setItem(ROAD_CENTERLINE, createItem(
                centerline.equalsIgnoreCase("none") ? Material.BARRIER : safeMaterial(centerline, Material.YELLOW_CONCRETE),
                "§d§lCENTERLINE", List.of("", "§7Center stripe block.", "",
                        "§8├ §7Current: §f" + formatMat(centerline), "",
                        "§e➤ Click to cycle §8| §e➤ Right: §fnone",
                        "§b➤ Hold a block to set custom")));

        inventory.setItem(ROAD_USE_SLABS, createToggle("USE SLABS", useSlabs,
                "§7Use slabs for smooth height transitions."));

        inventory.setItem(ROAD_USE_STAIRS, createToggle("USE STAIRS", useStairs,
                "§7Use stairs for smooth height transitions."));

        inventory.setItem(ROAD_TERRAIN_ADAPT, createToggle("TERRAIN ADAPT", terrainAdapt,
                "§7Adapt road surface to terrain height."));

        inventory.setItem(ROAD_CLEARANCE, createItem(Material.GLASS, "§a§lCLEARANCE: §f" + clearance, List.of(
                "", "§7Blocks cleared above road surface.", "",
                createBar(clearance, 10, "§a"), "",
                "§e➤ Left: §f+1 §8| §e➤ Right: §f-1")));

        inventory.setItem(ROAD_FILL_BELOW, createItem(Material.COBBLESTONE, "§7§lFILL BELOW: §f" + fillBelow, List.of(
                "", "§7Blocks filled below road surface.", "",
                createBar(fillBelow, 20, "§7"), "",
                "§e➤ Left: §f+1 §8| §e➤ Right: §f-1",
                "§e➤ Shift: §f±2")));

        inventory.setItem(ROAD_FILL_MATERIAL, createItem(safeMaterial(fillMaterial, Material.COBBLESTONE),
                "§8§lFILL MATERIAL", List.of("", "§7Block used to fill below road.", "",
                        "§8├ §7Current: §f" + formatMat(fillMaterial), "",
                        "§e➤ Click to cycle materials")));

        inventory.setItem(ROAD_RESOLUTION, createItem(Material.SPYGLASS, "§5§lRESOLUTION: §f" + resolution, List.of(
                "", "§7Curve sampling density.", "§7Lower = smoother, more blocks.", "",
                "§e➤ Left: §f+0.25 §8| §e➤ Right: §f-0.25")));
    }

    // ═══════════════════════════════════════════════════════════════
    // BRIDGE
    // ═══════════════════════════════════════════════════════════════

    private void setupBridgeItems() {
        Map<String, Object> s = session.getBridgeSettings();

        int width = toInt(s.get("width"), 5);
        String deckMat = toStr(s.get("deck-material"), "STONE_BRICK_SLAB");
        boolean railings = toBool(s.get("railings"), true);
        String railMat = toStr(s.get("railing-material"), "STONE_BRICK_WALL");
        boolean supports = toBool(s.get("supports"), true);
        String supMat = toStr(s.get("support-material"), "STONE_BRICKS");
        int supSpacing = toInt(s.get("support-spacing"), 8);
        int supWidth = toInt(s.get("support-width"), 3);
        int supDepth = toInt(s.get("support-max-depth"), 40);
        String heightMode = toStr(s.get("height-mode"), "auto");
        boolean ramps = toBool(s.get("ramps"), true);
        String rampMat = toStr(s.get("ramp-material"), "STONE_BRICK_STAIRS");
        double resolution = toDbl(s.get("resolution"), 0.5);

        inventory.setItem(BRIDGE_WIDTH, createItem(Material.RAIL, "§b§lWIDTH: §f" + width, List.of(
                "", "§7Bridge deck width in blocks.", "",
                createBar(width, 32, "§b"), "",
                "§e➤ Left: §f+1 §8| §e➤ Right: §f-1",
                "§e➤ Shift: §f±2")));

        inventory.setItem(BRIDGE_DECK_MATERIAL, createItem(safeMaterial(deckMat, Material.STONE_BRICK_SLAB),
                "§6§lDECK MATERIAL", List.of("", "§7Block used for the bridge deck.", "",
                        "§8├ §7Current: §f" + formatMat(deckMat), "",
                        "§e➤ Click to cycle materials")));

        inventory.setItem(BRIDGE_RAILINGS, createToggle("RAILINGS", railings,
                "§7Place railings on bridge edges."));

        inventory.setItem(BRIDGE_RAILING_MAT, createItem(safeMaterial(railMat, Material.STONE_BRICK_WALL),
                "§e§lRAILING MATERIAL", List.of("", "§7Block used for railings.", "",
                        "§8├ §7Current: §f" + formatMat(railMat), "",
                        "§e➤ Click to cycle materials")));

        inventory.setItem(BRIDGE_SUPPORTS, createToggle("SUPPORTS", supports,
                "§7Generate support pillars below deck."));

        inventory.setItem(BRIDGE_SUPPORT_MAT, createItem(safeMaterial(supMat, Material.STONE_BRICKS),
                "§7§lSUPPORT MATERIAL", List.of("", "§7Block used for support pillars.", "",
                        "§8├ §7Current: §f" + formatMat(supMat), "",
                        "§e➤ Click to cycle materials")));

        inventory.setItem(BRIDGE_SUPPORT_SPACING, createItem(Material.CHAIN, "§a§lSUPPORT SPACING: §f" + supSpacing, List.of(
                "", "§7Distance between support pillars.", "",
                createBar(supSpacing, 50, "§a"), "",
                "§e➤ Left: §f+1 §8| §e➤ Right: §f-1",
                "§e➤ Shift: §f±2")));

        inventory.setItem(BRIDGE_SUPPORT_WIDTH, createItem(Material.STONE_BRICKS, "§3§lSUPPORT WIDTH: §f" + supWidth, List.of(
                "", "§7Pillar radius (cylindrical).", "§7Higher = thicker pillars.", "",
                createBar(supWidth, 10, "§3"), "",
                "§e➤ Left: §f+1 §8| §e➤ Right: §f-1")));

        inventory.setItem(BRIDGE_SUPPORT_DEPTH, createItem(Material.POINTED_DRIPSTONE, "§c§lMAX DEPTH: §f" + supDepth, List.of(
                "", "§7Max depth pillars extend down.", "",
                createBar(supDepth, 128, "§c"), "",
                "§e➤ Left: §f+5 §8| §e➤ Right: §f-5",
                "§e➤ Shift: §f±10")));

        inventory.setItem(BRIDGE_HEIGHT_MODE, createItem(
                heightMode.equals("auto") ? Material.COMPASS : Material.IRON_BARS,
                "§d§lHEIGHT MODE: §f" + heightMode.toUpperCase(), List.of(
                        "", "§7How the deck height is determined.", "",
                        "§8├ §aAUTO  §8- §7Follow curve Y smoothly",
                        "§8├ §eFIXED §8- §7Use exact point Y values", "",
                        "§e➤ Click to toggle")));

        inventory.setItem(BRIDGE_RAMPS, createToggle("RAMPS", ramps,
                "§7Generate ramps at bridge ends."));

        inventory.setItem(BRIDGE_RAMP_MAT, createItem(safeMaterial(rampMat, Material.STONE_BRICK_STAIRS),
                "§6§lRAMP MATERIAL", List.of("", "§7Block used for ramp stairs.", "",
                        "§8├ §7Current: §f" + formatMat(rampMat), "",
                        "§e➤ Click to cycle materials")));

        inventory.setItem(BRIDGE_RESOLUTION, createItem(Material.SPYGLASS, "§5§lRESOLUTION: §f" + resolution, List.of(
                "", "§7Curve sampling density.", "§7Lower = smoother, more blocks.", "",
                "§e➤ Left: §f+0.25 §8| §e➤ Right: §f-0.25")));
    }

    // ═══════════════════════════════════════════════════════════════
    // CURVE
    // ═══════════════════════════════════════════════════════════════

    private void setupCurveItems() {
        Map<String, Object> s = session.getCurveSettings();

        double resolution = toDbl(s.get("resolution"), 0.5);
        String algorithm = toStr(s.get("algorithm"), "catmullrom");

        inventory.setItem(CURVE_RESOLUTION, createItem(Material.SPYGLASS, "§5§lRESOLUTION: §f" + resolution, List.of(
                "", "§7Curve sampling density.", "§7Lower = smoother curve.", "",
                "§e➤ Left: §f+0.25 §8| §e➤ Right: §f-0.25")));

        inventory.setItem(CURVE_ALGORITHM, createItem(
                algorithm.equals("catmullrom") ? Material.LEAD : Material.STRING,
                "§d§lALGORITHM: §f" + algorithm.toUpperCase(), List.of(
                        "", "§7Interpolation algorithm.", "",
                        "§8├ §aCATMULLROM §8- §7Smooth, passes through points",
                        "§8├ §eBEZIER     §8- §7Smooth, control-point based", "",
                        "§e➤ Click to toggle")));
    }

    // ═══════════════════════════════════════════════════════════════
    // Click handling (called from PlayerListener)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Processes a click on a slot. Returns true if the click was handled.
     */
    public boolean handleClick(int slot, boolean left, boolean right, boolean shift) {
        switch (mode) {
            case ROAD -> { return handleRoadClick(slot, left, right, shift); }
            case BRIDGE -> { return handleBridgeClick(slot, left, right, shift); }
            case CURVE -> { return handleCurveClick(slot, left, right, shift); }
        }
        return false;
    }

    private boolean handleRoadClick(int slot, boolean left, boolean right, boolean shift) {
        Map<String, Object> s = session.getRoadSettings();
        switch (slot) {
            case ROAD_WIDTH -> adjustInt(s, "width", left, right, shift ? 2 : 1, 1, 32);
            case ROAD_MATERIAL -> cycleMaterial(s, "material", ROAD_MATERIALS, right);
            case ROAD_BORDER -> cycleMaterialOrNone(s, "border", ROAD_MATERIALS, right);
            case ROAD_CENTERLINE -> cycleMaterialOrNone(s, "centerline", CENTERLINE_MATERIALS, right);
            case ROAD_USE_SLABS -> toggleBool(s, "use-slabs");
            case ROAD_USE_STAIRS -> toggleBool(s, "use-stairs");
            case ROAD_TERRAIN_ADAPT -> toggleBool(s, "terrain-adapt");
            case ROAD_CLEARANCE -> adjustInt(s, "clearance", left, right, 1, 1, 10);
            case ROAD_FILL_BELOW -> adjustInt(s, "fill-below", left, right, shift ? 2 : 1, 0, 20);
            case ROAD_FILL_MATERIAL -> cycleMaterial(s, "fill-material", FILL_MATERIALS, right);
            case ROAD_RESOLUTION -> adjustDouble(s, "resolution", left, right, 0.25, 0.1, 2.0);
            default -> { return false; }
        }
        refresh();
        return true;
    }

    private boolean handleBridgeClick(int slot, boolean left, boolean right, boolean shift) {
        Map<String, Object> s = session.getBridgeSettings();
        switch (slot) {
            case BRIDGE_WIDTH -> adjustInt(s, "width", left, right, shift ? 2 : 1, 1, 32);
            case BRIDGE_DECK_MATERIAL -> cycleMaterial(s, "deck-material", DECK_MATERIALS, right);
            case BRIDGE_RAILINGS -> toggleBool(s, "railings");
            case BRIDGE_RAILING_MAT -> cycleMaterial(s, "railing-material", RAILING_MATERIALS, right);
            case BRIDGE_SUPPORTS -> toggleBool(s, "supports");
            case BRIDGE_SUPPORT_MAT -> cycleMaterial(s, "support-material", SUPPORT_MATERIALS, right);
            case BRIDGE_SUPPORT_SPACING -> adjustInt(s, "support-spacing", left, right, shift ? 2 : 1, 3, 50);
            case BRIDGE_SUPPORT_WIDTH -> adjustInt(s, "support-width", left, right, 1, 1, 10);
            case BRIDGE_SUPPORT_DEPTH -> adjustInt(s, "support-max-depth", left, right, shift ? 10 : 5, 1, 128);
            case BRIDGE_HEIGHT_MODE -> toggleEnum(s, "height-mode", new String[]{"auto", "fixed"});
            case BRIDGE_RAMPS -> toggleBool(s, "ramps");
            case BRIDGE_RAMP_MAT -> cycleMaterial(s, "ramp-material", RAMP_MATERIALS, right);
            case BRIDGE_RESOLUTION -> adjustDouble(s, "resolution", left, right, 0.25, 0.1, 2.0);
            default -> { return false; }
        }
        refresh();
        return true;
    }

    private boolean handleCurveClick(int slot, boolean left, boolean right, boolean shift) {
        Map<String, Object> s = session.getCurveSettings();
        switch (slot) {
            case CURVE_RESOLUTION -> adjustDouble(s, "resolution", left, right, 0.25, 0.1, 2.0);
            case CURVE_ALGORITHM -> toggleEnum(s, "algorithm", new String[]{"catmullrom", "bezier"});
            default -> { return false; }
        }
        refresh();
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // Value manipulation helpers
    // ═══════════════════════════════════════════════════════════════

    private void adjustInt(Map<String, Object> settings, String key, boolean left, boolean right, int step, int min, int max) {
        int current = toInt(settings.get(key), min);
        int delta = left ? step : -step;
        settings.put(key, clamp(current + delta, min, max));
    }

    private void adjustDouble(Map<String, Object> settings, String key, boolean left, boolean right, double step, double min, double max) {
        double current = toDbl(settings.get(key), min);
        double delta = left ? step : -step;
        double newVal = Math.round((current + delta) * 100.0) / 100.0;
        settings.put(key, clamp(newVal, min, max));
    }

    private void toggleBool(Map<String, Object> settings, String key) {
        boolean current = toBool(settings.get(key), false);
        settings.put(key, !current);
    }

    private void toggleEnum(Map<String, Object> settings, String key, String[] values) {
        String current = toStr(settings.get(key), values[0]);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) {
                settings.put(key, values[(i + 1) % values.length]);
                return;
            }
        }
        settings.put(key, values[0]);
    }

    private void cycleMaterial(Map<String, Object> settings, String key, String[] materials, boolean reverse) {
        // Check if player is holding a block — if so, use that block
        org.bukkit.inventory.ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.getType().isBlock() && handItem.getType() != Material.AIR) {
            settings.put(key, handItem.getType().name());
            return;
        }

        String current = toStr(settings.get(key), materials[0]);
        int idx = -1;
        for (int i = 0; i < materials.length; i++) {
            if (materials[i].equalsIgnoreCase(current)) { idx = i; break; }
        }
        if (idx == -1) idx = 0;
        int next = reverse ? (idx - 1 + materials.length) % materials.length : (idx + 1) % materials.length;
        settings.put(key, materials[next]);
    }

    private void cycleMaterialOrNone(Map<String, Object> settings, String key, String[] materials, boolean reverse) {
        // Right-click always sets to "none"
        if (reverse) {
            settings.put(key, "none");
            return;
        }

        // Check if player is holding a block — if so, use that block
        org.bukkit.inventory.ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.getType().isBlock() && handItem.getType() != Material.AIR) {
            settings.put(key, handItem.getType().name());
            return;
        }

        String current = toStr(settings.get(key), "none");
        // Build list: none + materials
        String[] all = new String[materials.length + 1];
        all[0] = "none";
        System.arraycopy(materials, 0, all, 1, materials.length);

        int idx = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i].equalsIgnoreCase(current)) { idx = i; break; }
        }
        int next = (idx + 1) % all.length;
        settings.put(key, all[next]);
    }

    // ═══════════════════════════════════════════════════════════════
    // Material presets for cycling
    // ═══════════════════════════════════════════════════════════════

    private static final String[] ROAD_MATERIALS = {
            "STONE_BRICKS", "COBBLESTONE", "STONE", "ANDESITE", "DIORITE", "GRANITE",
            "POLISHED_ANDESITE", "POLISHED_DIORITE", "POLISHED_GRANITE",
            "DEEPSLATE_BRICKS", "POLISHED_DEEPSLATE", "BRICKS",
            "SANDSTONE", "RED_SANDSTONE", "SMOOTH_STONE",
            "OAK_PLANKS", "SPRUCE_PLANKS", "DARK_OAK_PLANKS",
            "QUARTZ_BLOCK", "SMOOTH_QUARTZ", "BLACKSTONE", "POLISHED_BLACKSTONE"
    };

    private static final String[] CENTERLINE_MATERIALS = {
            "YELLOW_CONCRETE", "WHITE_CONCRETE", "YELLOW_TERRACOTTA",
            "WHITE_TERRACOTTA", "GOLD_BLOCK", "GLOWSTONE"
    };

    private static final String[] FILL_MATERIALS = {
            "COBBLESTONE", "STONE", "DIRT", "GRAVEL", "ANDESITE",
            "DEEPSLATE", "COBBLED_DEEPSLATE", "TUFF"
    };

    private static final String[] DECK_MATERIALS = {
            "STONE_BRICK_SLAB", "COBBLESTONE_SLAB", "STONE_SLAB", "SMOOTH_STONE_SLAB",
            "SANDSTONE_SLAB", "OAK_SLAB", "SPRUCE_SLAB", "DARK_OAK_SLAB",
            "DEEPSLATE_BRICK_SLAB", "BRICK_SLAB", "QUARTZ_SLAB",
            "STONE_BRICKS", "COBBLESTONE", "OAK_PLANKS", "SPRUCE_PLANKS"
    };

    private static final String[] RAILING_MATERIALS = {
            "STONE_BRICK_WALL", "COBBLESTONE_WALL", "ANDESITE_WALL",
            "DEEPSLATE_BRICK_WALL", "BRICK_WALL", "SANDSTONE_WALL",
            "OAK_FENCE", "SPRUCE_FENCE", "DARK_OAK_FENCE",
            "NETHER_BRICK_FENCE", "IRON_BARS"
    };

    private static final String[] SUPPORT_MATERIALS = {
            "STONE_BRICKS", "COBBLESTONE", "STONE", "DEEPSLATE_BRICKS",
            "POLISHED_DEEPSLATE", "BRICKS", "SANDSTONE", "OAK_LOG",
            "SPRUCE_LOG", "DARK_OAK_LOG", "QUARTZ_BLOCK", "BLACKSTONE"
    };

    private static final String[] RAMP_MATERIALS = {
            "STONE_BRICK_STAIRS", "COBBLESTONE_STAIRS", "STONE_STAIRS",
            "SANDSTONE_STAIRS", "OAK_STAIRS", "SPRUCE_STAIRS", "DARK_OAK_STAIRS",
            "DEEPSLATE_BRICK_STAIRS", "BRICK_STAIRS", "QUARTZ_STAIRS"
    };

    // ═══════════════════════════════════════════════════════════════
    // UI helpers
    // ═══════════════════════════════════════════════════════════════

    private void fillBackground() {
        Material bgMat = switch (mode) {
            case ROAD -> Material.ORANGE_STAINED_GLASS_PANE;
            case BRIDGE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case CURVE -> Material.MAGENTA_STAINED_GLASS_PANE;
        };

        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            bg.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, bg);
        }

        // Accent border on top and bottom rows
        ItemStack accent = new ItemStack(bgMat);
        meta = accent.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            accent.setItemMeta(meta);
        }
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, accent);
            inventory.setItem(inventory.getSize() - 9 + i, accent);
        }
    }

    private ItemStack createToggle(String label, boolean value, String description) {
        return createItem(
                value ? Material.LIME_DYE : Material.GRAY_DYE,
                (value ? "§a§l" : "§c§l") + label + (value ? " ON" : " OFF"),
                List.of("", description, "",
                        "§8├ §7Status: " + (value ? "§a● Enabled" : "§c● Disabled"), "",
                        "§e➤ Click to toggle"));
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            if (lore != null) {
                List<Component> lc = new ArrayList<>();
                for (String l : lore) {
                    lc.add(Component.text(l).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lc);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String createBar(int value, int max, String color) {
        int len = 20;
        int filled = Math.max(0, Math.min(len, (int) ((double) value / max * len)));
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < len; i++) {
            bar.append(i < filled ? color + "▌" : "§7▌");
        }
        bar.append("§8] §f").append(value).append("§7/").append(max);
        return bar.toString();
    }

    private String formatMat(String mat) {
        if (mat == null || mat.equalsIgnoreCase("none")) return "None";
        return mat.toLowerCase().replace("_", " ");
    }

    private Material safeMaterial(String name, Material fallback) {
        if (name == null || name.equalsIgnoreCase("none")) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ── Type conversion helpers ──

    private int toInt(Object val, int def) {
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    private double toDbl(Object val, double def) {
        if (val instanceof Double d) return d;
        if (val instanceof Number n) return n.doubleValue();
        return def;
    }

    private boolean toBool(Object val, boolean def) {
        if (val instanceof Boolean b) return b;
        return def;
    }

    private String toStr(Object val, String def) {
        if (val instanceof String s) return s;
        return def;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ── Public API ──

    public void refresh() { setupItems(); }
    public void open() { player.openInventory(inventory); }
    @Override public Inventory getInventory() { return inventory; }
    public Player getPlayer() { return player; }
    public PathSession.Mode getMode() { return mode; }
}
