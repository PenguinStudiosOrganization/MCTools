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

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for selecting blocks for the brush.
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class BlockSelectorGUI implements InventoryHolder {

    public static final String GUI_TITLE = "Select Block";
    
    private final MCTools plugin;
    private final Player player;
    private final Inventory inventory;
    private final List<Material> blocks;
    private int page;

    // Common terrain blocks
    private static final Material[] TERRAIN_BLOCKS = {
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
            Material.ROOTED_DIRT, Material.MUD, Material.STONE, Material.COBBLESTONE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.GRANITE, Material.DIORITE,
            Material.ANDESITE, Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
            Material.SANDSTONE, Material.RED_SANDSTONE, Material.TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.SNOW_BLOCK, Material.PACKED_ICE, Material.BLUE_ICE, Material.ICE,
            Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL, Material.BASALT,
            Material.BLACKSTONE, Material.END_STONE, Material.MOSS_BLOCK, Material.MYCELIUM,
            Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.PRISMARINE, Material.DARK_PRISMARINE
    };

    public BlockSelectorGUI(MCTools plugin, Player player) {
        this(plugin, player, 0);
    }

    public BlockSelectorGUI(MCTools plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
        this.blocks = new ArrayList<>(List.of(TERRAIN_BLOCKS));
        
        this.inventory = Bukkit.createInventory(this, 54, 
                Component.text(GUI_TITLE).color(NamedTextColor.AQUA));
        
        setupItems();
    }

    private void setupItems() {
        inventory.clear();
        
        int slots = 45; // 5 rows for blocks
        int startIndex = page * slots;
        int endIndex = Math.min(startIndex + slots, blocks.size());

        BrushSettings settings = plugin.getBrushManager().getSettings(player.getUniqueId());
        Material currentBlock = settings.getBlock();

        // Add block items
        for (int i = startIndex; i < endIndex; i++) {
            Material block = blocks.get(i);
            boolean isSelected = block == currentBlock;
            
            ItemStack item = new ItemStack(block);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = (isSelected ? "§a§l" : "§f") + formatMaterialName(block);
                meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                if (isSelected) {
                    lore.add(Component.text("§a✓ Currently selected").decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("§eClick to select").decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            
            inventory.setItem(i - startIndex, item);
        }

        // Navigation row (row 6)
        int navRow = 45;
        
        // Fill navigation row with dark glass
        ItemStack navBg = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = navRow; i < 54; i++) {
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
                    null
            );
            inventory.setItem(navRow + 3, prevItem);
        }

        // Page indicator
        int totalPages = (int) Math.ceil((double) blocks.size() / slots);
        ItemStack pageItem = createItem(
                Material.PAPER,
                "§f§lPage " + (page + 1) + "/" + totalPages,
                null
        );
        inventory.setItem(navRow + 4, pageItem);

        // Next page
        if (page < totalPages - 1) {
            ItemStack nextItem = createItem(
                    Material.SPECTRAL_ARROW,
                    "§e§lNext Page →",
                    null
            );
            inventory.setItem(navRow + 5, nextItem);
        }
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

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void nextPage() {
        int totalPages = (int) Math.ceil((double) blocks.size() / 45);
        if (page < totalPages - 1) {
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

    public Material getBlockAt(int slot) {
        int index = page * 45 + slot;
        if (index >= 0 && index < blocks.size()) {
            return blocks.get(index);
        }
        return null;
    }

    /**
     * Called when a block is selected. Override this to customize behavior.
     */
    public void onBlockSelected(Material block) {
        // Default implementation - can be overridden
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}
