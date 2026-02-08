package com.mctools.listeners;

import com.mctools.MCTools;
import com.mctools.brush.BrushSettings;
import com.mctools.brush.gui.BlockSelectorGUI;
import com.mctools.brush.gui.BrushGUI;
import com.mctools.brush.gui.HeightmapSelectorGUI;
import com.mctools.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit event listener for MCTools.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Handle bamboo tool interactions (left-click → GUI, right-click → brush apply).</li>
 *   <li>Route GUI clicks to the appropriate handler (BrushGUI, HeightmapSelector, BlockSelector).</li>
 *   <li>Clean up player data on quit (undo history).</li>
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>All GUI inventories are non-movable; clicks are cancelled and handled manually.</li>
 *   <li>Event priority is HIGHEST to ensure we intercept before other plugins.</li>
 * </ul>
 */
public class PlayerListener implements Listener {

    private final MCTools plugin;

    public PlayerListener(MCTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getUndoManager().clearPlayer(player);
    }

    /**
     * Bamboo tool:
     * - Left click: open brush GUI
     * - Right click: apply brush on target block
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBambooUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.BAMBOO) return;

        Action action = event.getAction();

        // Left click - always open GUI (even if event was cancelled by other plugins)
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            new BrushGUI(plugin, player).open();
            return;
        }

        // Right click - apply brush
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);

            var target = player.getTargetBlockExact(120);
            if (target == null) return;

            Location targetLoc = target.getLocation();
            plugin.getBrushManager().getTerrainBrush().apply(player, targetLoc);
        }
    }

    /**
     * Hard-blocks ALL inventory interactions for MCTools GUIs.
     * Prevents: pickup, move-to-other-inventory, shift-click, hotbar swap,
     * double click collect, dragging, etc.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BrushGUI || holder instanceof HeightmapSelectorGUI || holder instanceof BlockSelectorGUI)) {
            return;
        }

        // Always cancel - GUI is non-movable
        event.setCancelled(true);

        // Ignore clicks in the player inventory area
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }

        // Execute button actions only for top inventory
        if (holder instanceof BrushGUI) {
            handleBrushMenuClick(player, (BrushGUI) holder, event.getSlot(), event.isLeftClick(), event.isRightClick(), event.isShiftClick());
        } else if (holder instanceof HeightmapSelectorGUI) {
            handleHeightmapSelectorClick(player, (HeightmapSelectorGUI) holder, event.getSlot());
        } else if (holder instanceof BlockSelectorGUI) {
            handleBlockSelectorClick(player, (BlockSelectorGUI) holder, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuiDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof BrushGUI || holder instanceof HeightmapSelectorGUI || holder instanceof BlockSelectorGUI) {
            event.setCancelled(true);
        }
    }

    private void handleBrushMenuClick(Player player, BrushGUI gui, int slot, boolean left, boolean right, boolean shift) {
        BrushSettings settings = plugin.getBrushManager().getSettings(player.getUniqueId());

        if (slot == BrushGUI.getSlotToggle()) {
            settings.toggle();
            gui.refresh();
            return;
        }

        if (slot == BrushGUI.getSlotHeightmap()) {
            new HeightmapSelectorGUI(plugin, player).open();
            return;
        }

        if (slot == BrushGUI.getSlotBlock()) {
            new BlockSelectorGUI(plugin, player).open();
            return;
        }

        if (slot == BrushGUI.getSlotMode()) {
            BrushSettings.BrushMode[] modes = BrushSettings.BrushMode.values();
            int idx = settings.getMode().ordinal();
            settings.setMode(modes[(idx + 1) % modes.length]);
            gui.refresh();
            return;
        }

        // Values
        if (slot == BrushGUI.getSlotSize()) {
            int delta = shift ? 5 : 1;
            if (right) delta = -delta;
            settings.setSize(clamp(settings.getSize() + delta, 1, plugin.getConfigManager().getBrushMaxSize()));
            gui.refresh();
            return;
        }

        if (slot == BrushGUI.getSlotIntensity()) {
            int delta = shift ? 1 : 10;
            if (right) delta = -delta;
            settings.setIntensity(clamp(settings.getIntensity() + delta, 1, 100));
            gui.refresh();
            return;
        }

        if (slot == BrushGUI.getSlotMaxHeight()) {
            int delta = shift ? 5 : 1;
            if (right) delta = -delta;
            settings.setMaxHeight(clamp(settings.getMaxHeight() + delta, 1, plugin.getConfigManager().getBrushMaxHeight()));
            gui.refresh();
            return;
        }

        // Options
        if (slot == BrushGUI.getSlotAutoSmooth()) {
            settings.toggleAutoSmooth();
            gui.refresh();
            return;
        }

        if (slot == BrushGUI.getSlotSmoothStrength()) {
            int delta = right ? -1 : 1;
            settings.setSmoothStrength(clamp(settings.getSmoothStrength() + delta, 1, 5));
            gui.refresh();
            return;
        }

        if (slot == BrushGUI.getSlotAutoRotation()) {
            settings.toggleAutoRotation();
            gui.refresh();
            return;
        }

        if (slot == BrushGUI.getSlotCircular()) {
            settings.toggleCircularMask();
            gui.refresh();
            return;
        }
        
        if (slot == BrushGUI.getSlotPreview()) {
            settings.togglePreview();
            gui.refresh();
        }
    }

    private void handleHeightmapSelectorClick(Player player, HeightmapSelectorGUI gui, int slot) {
        int navRow = gui.getInventory().getSize() - 9;

        // Back
        if (slot == navRow) {
            new BrushGUI(plugin, player).open();
            return;
        }

        // Prev / Next
        if (slot == navRow + 3) {
            gui.previousPage();
            return;
        }
        if (slot == navRow + 5) {
            gui.nextPage();
            return;
        }

        // Reload
        if (slot == navRow + 8) {
            plugin.getBrushManager().reload();
            new HeightmapSelectorGUI(plugin, player, gui.getPage()).open();
            return;
        }

        // Select
        if (slot >= 0 && slot < navRow) {
            String hm = gui.getHeightmapAt(slot);
            if (hm == null) return;
            if (!plugin.getBrushManager().hasHeightmap(hm)) return;

            BrushSettings settings = plugin.getBrushManager().getSettings(player.getUniqueId());
            settings.setHeightmapName(hm);
            settings.setHeightmapImage(plugin.getBrushManager().getHeightmap(hm));

            // Auto-return to main menu
            new BrushGUI(plugin, player).open();
        }
    }

    private void handleBlockSelectorClick(Player player, BlockSelectorGUI gui, int slot) {
        int navRow = 45;

        // Back
        if (slot == navRow) {
            new BrushGUI(plugin, player).open();
            return;
        }

        // Prev / Next
        if (slot == navRow + 3) {
            gui.previousPage();
            return;
        }
        if (slot == navRow + 5) {
            gui.nextPage();
            return;
        }

        // Select
        if (slot >= 0 && slot < navRow) {
            Material block = gui.getBlockAt(slot);
            if (block == null) return;

            BrushSettings settings = plugin.getBrushManager().getSettings(player.getUniqueId());
            settings.setBlock(block);

            // Auto-return to main menu
            new BrushGUI(plugin, player).open();
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
    
    /**
     * Intercepts //wand command from WorldEdit and offers a choice between
     * WorldEdit wand and MCTools Terrain Brush wand.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldEditWandCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase().trim();
        
        // Check if it's the //wand command
        if (!message.equals("//wand")) {
            return;
        }
        
        // Check if WorldEdit is installed
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            return;
        }
        
        // Check if player has MCTools brush permission
        if (!player.hasPermission("mctools.brush")) {
            return; // Let WorldEdit handle it normally
        }
        
        // Cancel the original command
        event.setCancelled(true);
        
        // Send beautiful choice message
        sendWandChoiceMenu(player);
    }
    
    /**
     * Sends a simple wand selection menu to the player.
     */
    private void sendWandChoiceMenu(Player player) {
        player.sendMessage("");
        player.sendMessage("§e§lCHOOSE YOUR WAND");
        player.sendMessage("");
        
        // WorldEdit option
        Component weOption = Component.text("§6§l[WorldEdit] §7- §fWorldEdit's Wand")
            .clickEvent(ClickEvent.runCommand("/mct worldeditwand"))
            .hoverEvent(HoverEvent.showText(Component.text("Click to get WorldEdit Wand", NamedTextColor.YELLOW)));
        player.sendMessage(weOption);
        
        // MCTools option
        Component mctOption = Component.text("§a§l[MCTools] §7- §fMCTools' Brush")
            .clickEvent(ClickEvent.runCommand("/mct wand"))
            .hoverEvent(HoverEvent.showText(Component.text("Click to get MCTools Terrain Brush", NamedTextColor.GREEN)));
        player.sendMessage(mctOption);
        
        player.sendMessage("");
    }
    
    /**
     * Gives the WorldEdit wand to a player using WorldEdit's API.
     * Falls back to wooden axe if API is not available.
     */
    public void giveWorldEditWand(Player player) {
        Material wandMaterial = Material.WOODEN_AXE; // Default fallback
        
        try {
            // Try to use WorldEdit API to get the configured wand item
            com.sk89q.worldedit.LocalConfiguration config = com.sk89q.worldedit.WorldEdit.getInstance().getConfiguration();
            
            // Get the wand item from WorldEdit config
            String wandItem = config.wandItem;
            
            // Parse the item type (format is usually "minecraft:wooden_axe")
            if (wandItem != null && !wandItem.isEmpty()) {
                String itemName = wandItem.replace("minecraft:", "").toUpperCase().replace(" ", "_");
                try {
                    wandMaterial = Material.valueOf(itemName);
                } catch (IllegalArgumentException ignored) {
                    // Keep default wooden axe
                }
            }
        } catch (NoClassDefFoundError | Exception ignored) {
            // WorldEdit API not available, use default wooden axe
        }
        
        // Create and give the wand
        ItemStack wand = new ItemStack(wandMaterial);
        player.getInventory().addItem(wand);
        
        plugin.getMessageUtil().sendInfo(player, "You received the WorldEdit Wand!");
    }
}
