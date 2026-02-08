package com.mctools.brush.gui;

import com.mctools.MCTools;
import com.mctools.brush.BrushSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Terrain brush GUI – a 54-slot chest inventory with clean, spacious layout.
 *
 * <p>Row 1: main controls (toggle, heightmap, block, mode).
 * Row 3: numeric values (size, intensity, max height).
 * Row 4–5: options (auto-smooth, smooth strength, auto-rotation, circular mask, preview).
 * Row 6: usage info.</p>
 */
public class BrushGUI implements InventoryHolder {

    public static final String GUI_TITLE = "Terrain Brush";
    
    private final MCTools plugin;
    private final Player player;
    private final Inventory inventory;

    // Row 1 - Main (slots 0-8)
    private static final int SLOT_TOGGLE = 1;
    private static final int SLOT_HEIGHTMAP = 3;
    private static final int SLOT_BLOCK = 5;
    private static final int SLOT_MODE = 7;
    
    // Row 3 - Values (slots 18-26)
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_INTENSITY = 22;
    private static final int SLOT_MAX_HEIGHT = 24;
    
    // Row 5 - Options (slots 36-44)
    private static final int SLOT_AUTO_SMOOTH = 19;
    private static final int SLOT_SMOOTH_STRENGTH = 21;
    private static final int SLOT_AUTO_ROTATION = 23;
    private static final int SLOT_CIRCULAR = 25;
    private static final int SLOT_PREVIEW = 37;
    
    // Row 6 - Info
    private static final int SLOT_INFO = 49;

    public BrushGUI(MCTools plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54, Component.text(GUI_TITLE)
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        
        setupItems();
    }

    private void setupItems() {
        BrushSettings settings = plugin.getBrushManager().getSettings(player.getUniqueId());
        
        fillBackground();

        // ═══════════════════════════════════════════════════════════════
        // ROW 1: Main Controls
        // ═══════════════════════════════════���═══════════════════════════
        
        // Toggle
        inventory.setItem(SLOT_TOGGLE, createItem(
                settings.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                settings.isEnabled() ? "§a§lBRUSH ON" : "§c§lBRUSH OFF",
                List.of(
                        "",
                        "§7Enable or disable the terrain brush.",
                        "",
                        "§8├ §7Status: " + (settings.isEnabled() ? "§a● Active" : "§c● Inactive"),
                        "",
                        "§e➤ Click to toggle"
                )
        ));
        
        // Heightmap
        String hmName = settings.getHeightmapName();
        List<String> hmLore = new ArrayList<>();
        hmLore.add("");
        hmLore.add("§7Select a heightmap image to use");
        hmLore.add("§7as the brush shape.");
        hmLore.add("");
        hmLore.add("§8├ §7Current: " + (hmName != null ? "§f" + hmName : "§cNone"));
        if (settings.hasHeightmap()) {
            hmLore.add("");
            hmLore.addAll(generatePreview(settings.getHeightmapImage()));
        }
        hmLore.add("");
        hmLore.add("§e➤ Click to browse");
        
        inventory.setItem(SLOT_HEIGHTMAP, createItem(Material.FILLED_MAP, "§6§lHEIGHTMAP", hmLore));
        
        // Block
        inventory.setItem(SLOT_BLOCK, createItem(
                settings.getBlock(),
                "§b§lBLOCK",
                List.of(
                        "",
                        "§7The block used for terrain.",
                        "",
                        "§8├ §7Current: §f" + formatName(settings.getBlock()),
                        "",
                        "§e➤ Click to change"
                )
        ));
        
        // Mode
        inventory.setItem(SLOT_MODE, createItem(
                getModeIcon(settings.getMode()),
                "§d§lMODE: §f" + formatMode(settings.getMode()),
                List.of(
                        "",
                        getModeDesc(settings.getMode()),
                        "",
                        "§8├ §aRAISE  §8- §7Build up terrain",
                        "§8├ §cLOWER  §8- §7Dig down terrain",
                        "§8├ §bSMOOTH §8- §7Blend surfaces",
                        "§8├ §eFLAT   §8- §7Level to height",
                        "",
                        "§e➤ Click to cycle"
                )
        ));

        // ═══════════════════════════════════════════════════════════════
        // ROW 3: Values
        // ═══════════════════════════════════════════════════════════════
        
        int maxSize = plugin.getConfigManager().getBrushMaxSize();
        inventory.setItem(SLOT_SIZE, createItem(
                Material.BROWN_MUSHROOM,
                "§b§lSIZE: §f" + settings.getSize(),
                List.of(
                        "",
                        "§7Brush radius in blocks.",
                        "",
                        createBar(settings.getSize(), maxSize, "§b"),
                        "",
                        "§e➤ Left: §f+1 §8| §e➤ Right: §f-1",
                        "§e➤ Shift: §f±5"
                )
        ));
        
        inventory.setItem(SLOT_INTENSITY, createItem(
                Material.BLAZE_POWDER,
                "§6§lINTENSITY: §f" + settings.getIntensity() + "%",
                List.of(
                        "",
                        "§7How strong the brush affects terrain.",
                        "",
                        createBar(settings.getIntensity(), 100, "§6"),
                        "",
                        "§e➤ Left: §f+10% §8| §e➤ Right: §f-10%",
                        "§e➤ Shift: §f±1%"
                )
        ));
        
        int maxH = plugin.getConfigManager().getBrushMaxHeight();
        inventory.setItem(SLOT_MAX_HEIGHT, createItem(
                Material.LADDER,
                "§a§lMAX HEIGHT: §f" + settings.getMaxHeight(),
                List.of(
                        "",
                        "§7Maximum height the brush can build.",
                        "",
                        createBar(settings.getMaxHeight(), maxH, "§a"),
                        "",
                        "§e➤ Left: §f+1 §8| §e➤ Right: §f-1",
                        "§e➤ Shift: §f±5"
                )
        ));

        // ═══════════════════════════════════════════════════════════════
        // ROW 4: Options
        // ═══════════════════════════════════════════════════════════════
        
        inventory.setItem(SLOT_AUTO_SMOOTH, createItem(
                settings.isAutoSmooth() ? Material.LIME_DYE : Material.GRAY_DYE,
                settings.isAutoSmooth() ? "§a§lAUTO-SMOOTH ON" : "§c§lAUTO-SMOOTH OFF",
                List.of(
                        "",
                        "§7Automatically smooths terrain edges",
                        "§7for natural-looking results.",
                        "",
                        "§e➤ Click to toggle"
                )
        ));
        
        inventory.setItem(SLOT_SMOOTH_STRENGTH, createItem(
                Material.FEATHER,
                "§d§lSMOOTH: §f" + settings.getSmoothStrength(),
                List.of(
                        "",
                        "§7Smoothing intensity level.",
                        "",
                        createDots(settings.getSmoothStrength(), 5, "§d"),
                        "",
                        "§e➤ Left: §f+1 §8| §e➤ Right: §f-1"
                )
        ));
        
        inventory.setItem(SLOT_AUTO_ROTATION, createItem(
                settings.isAutoRotation() ? Material.LIME_DYE : Material.GRAY_DYE,
                settings.isAutoRotation() ? "§a§lAUTO-ROTATE ON" : "§c§lAUTO-ROTATE OFF",
                List.of(
                        "",
                        "§7Rotates the brush based on",
                        "§7your facing direction.",
                        "",
                        "§e➤ Click to toggle"
                )
        ));
        
        inventory.setItem(SLOT_CIRCULAR, createItem(
                settings.isCircularMask() ? Material.ENDER_PEARL : Material.FIREWORK_STAR,
                settings.isCircularMask() ? "§a§lCIRCULAR" : "§c§lSQUARE",
                List.of(
                        "",
                        "§7Brush shape type.",
                        "",
                        "§8├ §7Current: " + (settings.isCircularMask() ? "§a● Circle" : "§c■ Square"),
                        "",
                        "§e➤ Click to toggle"
                )
        ));
        
        inventory.setItem(SLOT_PREVIEW, createItem(
                settings.isPreviewEnabled() ? Material.LIME_STAINED_GLASS : Material.GRAY_STAINED_GLASS,
                settings.isPreviewEnabled() ? "§a§lPREVIEW ON" : "§c§lPREVIEW OFF",
                List.of(
                        "",
                        "§7Shows a preview with green glass",
                        "§7before placing final blocks.",
                        "",
                        "§8├ §7Status: " + (settings.isPreviewEnabled() ? "§a● Active" : "§c● Inactive"),
                        "§8├ §7Duration: §f10 seconds",
                        "",
                        "§7When enabled, right-click shows",
                        "§7green glass preview first, then",
                        "§7converts to your selected block.",
                        "",
                        "§e➤ Click to toggle"
                )
        ));

        // ═══════════════════════════════════════════════════════════════
        // ROW 6: Info
        // ═══════════════════════════════════════════════════════════════
        
        inventory.setItem(SLOT_INFO, createItem(
                Material.BOOK,
                "§3§lHOW TO USE",
                List.of(
                        "",
                        "§7Hold §fBamboo §7in hand:",
                        "",
                        "§8├ §eLeft-click  §8→ §7Open this menu",
                        "§8├ §eRight-click §8→ §7Apply brush",
                        "",
                        "§7Use §f/mcb §7commands for quick settings."
                )
        ));
    }
    
    private void fillBackground() {
        ItemStack dark = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = dark.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            dark.setItemMeta(meta);
        }
        
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, dark);
        }
        
        // Add decorative borders
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        meta = border.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            border.setItemMeta(meta);
        }
        
        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
    }
    
    private String createBar(int value, int max, String color) {
        int len = 20;
        int filled = (int) ((double) value / max * len);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < len; i++) {
            bar.append(i < filled ? color + "▌" : "§7▌");
        }
        bar.append("§8] §f").append(value).append("§7/").append(max);
        return bar.toString();
    }
    
    private String createDots(int value, int max, String color) {
        StringBuilder dots = new StringBuilder("§8[ ");
        for (int i = 1; i <= max; i++) {
            dots.append(i <= value ? color + "●" : "§7○");
            if (i < max) dots.append(" ");
        }
        dots.append(" §8]");
        return dots.toString();
    }
    
    private Material getModeIcon(BrushSettings.BrushMode mode) {
        return switch (mode) {
            case RAISE -> Material.LIME_CONCRETE;
            case LOWER -> Material.RED_CONCRETE;
            case SMOOTH -> Material.LIGHT_BLUE_CONCRETE;
            case FLATTEN -> Material.YELLOW_CONCRETE;
        };
    }
    
    private String formatMode(BrushSettings.BrushMode mode) {
        return mode.name().charAt(0) + mode.name().substring(1).toLowerCase();
    }
    
    private String getModeDesc(BrushSettings.BrushMode mode) {
        return switch (mode) {
            case RAISE -> "§a▲ §7Raises terrain upward";
            case LOWER -> "§c▼ §7Lowers terrain downward";
            case SMOOTH -> "§b≈ §7Smooths terrain surface";
            case FLATTEN -> "§e═ §7Flattens to click height";
        };
    }

    private List<String> generatePreview(BufferedImage img) {
        List<String> lines = new ArrayList<>();
        if (img == null) return lines;
        
        int w = img.getWidth(), h = img.getHeight();
        int pw = 16, ph = 6;
        
        for (int y = 0; y < ph; y++) {
            StringBuilder line = new StringBuilder("§8  ");
            for (int x = 0; x < pw; x++) {
                int ix = (int) ((double) x / pw * w);
                int iy = (int) ((double) y / ph * h);
                ix = Math.min(ix, w - 1);
                iy = Math.min(iy, h - 1);
                
                int rgb = img.getRGB(ix, iy);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                
                String c = gray < 64 ? "§0" : gray < 128 ? "§8" : gray < 192 ? "§7" : "§f";
                line.append(c).append("█");
            }
            lines.add(line.toString());
        }
        return lines;
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

    private String formatName(Material mat) {
        String n = mat.name().toLowerCase().replace("_", " ");
        StringBuilder r = new StringBuilder();
        for (String w : n.split(" ")) {
            if (!w.isEmpty()) r.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return r.toString().trim();
    }

    public void refresh() { setupItems(); }
    public void open() { player.openInventory(inventory); }
    @Override public Inventory getInventory() { return inventory; }
    public Player getPlayer() { return player; }
    
    public static int getSlotToggle() { return SLOT_TOGGLE; }
    public static int getSlotHeightmap() { return SLOT_HEIGHTMAP; }
    public static int getSlotBlock() { return SLOT_BLOCK; }
    public static int getSlotMode() { return SLOT_MODE; }
    public static int getSlotSize() { return SLOT_SIZE; }
    public static int getSlotIntensity() { return SLOT_INTENSITY; }
    public static int getSlotMaxHeight() { return SLOT_MAX_HEIGHT; }
    public static int getSlotAutoSmooth() { return SLOT_AUTO_SMOOTH; }
    public static int getSlotSmoothStrength() { return SLOT_SMOOTH_STRENGTH; }
    public static int getSlotAutoRotation() { return SLOT_AUTO_ROTATION; }
    public static int getSlotCircular() { return SLOT_CIRCULAR; }
    public static int getSlotPreview() { return SLOT_PREVIEW; }
}
