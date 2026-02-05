package com.mctools.brush.gui;

import com.mctools.MCTools;
import com.mctools.brush.BrushManager;
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
 * GUI for selecting heightmaps with ASCII preview in lore.
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class HeightmapSelectorGUI implements InventoryHolder {

    public static final String GUI_TITLE = "Select Heightmap";
    
    // Preview dimensions
    private static final int PREVIEW_WIDTH = 16;
    private static final int PREVIEW_HEIGHT = 8;
    
    private final MCTools plugin;
    private final Player player;
    private final Inventory inventory;
    private final List<String> heightmaps;
    private int page;

    public HeightmapSelectorGUI(MCTools plugin, Player player) {
        this(plugin, player, 0);
    }

    public HeightmapSelectorGUI(MCTools plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.heightmaps = plugin.getBrushManager().getAvailableHeightmaps();
        this.page = page;
        
        int rows = Math.min(6, Math.max(3, (int) Math.ceil((heightmaps.size() + 9) / 9.0)));
        this.inventory = Bukkit.createInventory(this, rows * 9, 
                Component.text(GUI_TITLE).color(NamedTextColor.YELLOW));
        
        setupItems();
    }

    private void setupItems() {
        inventory.clear();
        
        int slots = inventory.getSize() - 9; // Reserve bottom row for navigation
        int startIndex = page * slots;
        int endIndex = Math.min(startIndex + slots, heightmaps.size());

        BrushSettings settings = plugin.getBrushManager().getSettings(player.getUniqueId());
        String currentHeightmap = settings.getHeightmapName();

        // Add heightmap items
        for (int i = startIndex; i < endIndex; i++) {
            String heightmapName = heightmaps.get(i);
            boolean isSelected = heightmapName.equals(currentHeightmap);
            
            BufferedImage img = plugin.getBrushManager().getHeightmap(heightmapName);
            String dimensions = img != null ? img.getWidth() + "x" + img.getHeight() : "Unknown";
            
            // Build lore with preview
            List<String> lore = new ArrayList<>();
            lore.add("§7Size: §f" + dimensions);
            lore.add("");
            
            // Add ASCII preview
            if (img != null) {
                lore.add("§7Preview:");
                List<String> preview = generateAsciiPreview(img);
                lore.addAll(preview);
                lore.add("");
            }
            
            lore.add(isSelected ? "§a✓ Currently selected" : "§eClick to select");
            
            ItemStack item = createItem(
                    isSelected ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    (isSelected ? "§a§l" : "§f") + heightmapName,
                    lore
            );
            
            inventory.setItem(i - startIndex, item);
        }

        // Navigation row
        int navRow = inventory.getSize() - 9;
        
        // Fill navigation row with dark glass
        ItemStack navBg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = navRow; i < inventory.getSize(); i++) {
            inventory.setItem(i, navBg);
        }

        // Back button
        ItemStack backItem = createItem(
                Material.ARROW,
                "§c§lBack to Menu",
                List.of("§7Return to brush menu")
        );
        inventory.setItem(navRow, backItem);

        // Previous page
        if (page > 0) {
            ItemStack prevItem = createItem(
                    Material.SPECTRAL_ARROW,
                    "§e§l← Previous Page",
                    List.of("§7Page " + page + "/" + getTotalPages())
            );
            inventory.setItem(navRow + 3, prevItem);
        }

        // Page indicator
        ItemStack pageItem = createItem(
                Material.PAPER,
                "§f§lPage " + (page + 1) + "/" + getTotalPages(),
                List.of("§7" + heightmaps.size() + " heightmaps available")
        );
        inventory.setItem(navRow + 4, pageItem);

        // Next page
        if (page < getTotalPages() - 1) {
            ItemStack nextItem = createItem(
                    Material.SPECTRAL_ARROW,
                    "§e§lNext Page →",
                    List.of("§7Page " + (page + 2) + "/" + getTotalPages())
            );
            inventory.setItem(navRow + 5, nextItem);
        }

        // Reload heightmaps
        ItemStack reloadItem = createItem(
                Material.ENDER_EYE,
                "§b§lReload Heightmaps",
                List.of("§7Reload heightmaps from folder")
        );
        inventory.setItem(navRow + 8, reloadItem);
    }

    /**
     * Generates an ASCII art preview of the heightmap using ■ symbols.
     * Uses §f (white), §7 (gray), §8 (dark gray), §0 (black) for different brightness levels.
     */
    private List<String> generateAsciiPreview(BufferedImage img) {
        List<String> lines = new ArrayList<>();
        
        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();
        
        // Calculate sampling step
        double stepX = (double) imgWidth / PREVIEW_WIDTH;
        double stepY = (double) imgHeight / PREVIEW_HEIGHT;
        
        for (int y = 0; y < PREVIEW_HEIGHT; y++) {
            StringBuilder line = new StringBuilder("§7  "); // Indent
            for (int x = 0; x < PREVIEW_WIDTH; x++) {
                // Sample the image at this position
                int imgX = (int) (x * stepX);
                int imgY = (int) (y * stepY);
                
                // Clamp to bounds
                imgX = Math.min(imgX, imgWidth - 1);
                imgY = Math.min(imgY, imgHeight - 1);
                
                // Get pixel brightness
                int rgb = img.getRGB(imgX, imgY);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                
                // Map grayscale to 4 color levels using ■ symbol
                // 0-63: §0 (black), 64-127: §8 (dark gray), 128-191: §7 (gray), 192-255: §f (white)
                String colorCode;
                if (gray < 64) {
                    colorCode = "§0";      // Black
                } else if (gray < 128) {
                    colorCode = "§8";      // Dark gray
                } else if (gray < 192) {
                    colorCode = "§7";      // Gray
                } else {
                    colorCode = "§f";      // White
                }
                
                line.append(colorCode).append("■");
            }
            lines.add(line.toString());
        }
        
        return lines;
    }

    private int getTotalPages() {
        int slots = inventory.getSize() - 9;
        return Math.max(1, (int) Math.ceil((double) heightmaps.size() / slots));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            if (lore != null) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreComponents);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void nextPage() {
        if (page < getTotalPages() - 1) {
            page++;
            setupItems();
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            setupItems();
        }
    }

    public String getHeightmapAt(int slot) {
        int slots = inventory.getSize() - 9;
        int index = page * slots + slot;
        if (index >= 0 && index < heightmaps.size()) {
            return heightmaps.get(index);
        }
        return null;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}
