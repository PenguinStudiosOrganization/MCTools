package com.mctools.schematic;

import com.mctools.MCTools;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WorldEdit schematics for MCTools.
 *
 * <p>Provides loading, listing, and pasting of .schem/.schematic files
 * stored in the plugin's schematics folder. Supports a preview system
 * that shows the schematic as colored glass blocks for 10 seconds before
 * committing the final paste.</p>
 *
 * <p>Preview colors:
 * <ul>
 *   <li><b>Light Blue Glass</b> – solid/full blocks</li>
 *   <li><b>Magenta Glass</b> – special blocks (slabs, fences, stairs, grass, etc.)</li>
 * </ul>
 */
public class SchematicManager {

    private final MCTools plugin;
    private File schematicsFolder;

    /** Loaded schematics per player (player UUID -> clipboard). */
    private final Map<UUID, LoadedSchematic> loadedSchematics = new ConcurrentHashMap<>();

    /** Active paste previews per player. */
    private final Map<UUID, PastePreview> activePreviews = new ConcurrentHashMap<>();

    private static final Material PREVIEW_SOLID   = Material.LIGHT_BLUE_STAINED_GLASS;
    private static final Material PREVIEW_SPECIAL  = Material.MAGENTA_STAINED_GLASS;
    private static final int PREVIEW_DURATION_SECONDS = 10;

    public SchematicManager(MCTools plugin) {
        this.plugin = plugin;
        initFolder();
    }

    private void initFolder() {
        schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
            plugin.getLogger().info("Created schematics folder: " + schematicsFolder.getPath());
        }
    }

    // ── List ──

    /**
     * Returns a list of available schematic files with their sizes.
     */
    public List<SchematicInfo> listSchematics() {
        List<SchematicInfo> list = new ArrayList<>();
        if (!schematicsFolder.exists() || !schematicsFolder.isDirectory()) return list;

        File[] files = schematicsFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".schem") || lower.endsWith(".schematic");
        });

        if (files == null) return list;

        for (File file : files) {
            list.add(new SchematicInfo(file.getName(), file.length()));
        }

        list.sort(Comparator.comparing(SchematicInfo::name));
        return list;
    }

    // ── Load / Unload ──

    /**
     * Loads a schematic file into memory for the given player.
     *
     * @return true if loaded successfully
     */
    public boolean loadSchematic(Player player, String name) {
        File file = findSchematicFile(name);
        if (file == null) return false;

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            BlockVector3 dim = clipboard.getDimensions();
            loadedSchematics.put(player.getUniqueId(),
                    new LoadedSchematic(name, clipboard, file.length(), dim.x(), dim.y(), dim.z()));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load schematic '" + name + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Unloads the schematic for the given player.
     */
    public boolean unloadSchematic(Player player) {
        return loadedSchematics.remove(player.getUniqueId()) != null;
    }

    /**
     * Returns the loaded schematic for the player, or null.
     */
    public LoadedSchematic getLoaded(Player player) {
        return loadedSchematics.get(player.getUniqueId());
    }

    // ── Remove ──

    /**
     * Deletes a schematic file from disk.
     */
    public boolean removeSchematic(String name) {
        File file = findSchematicFile(name);
        if (file == null) return false;
        return file.delete();
    }

    // ── Rotate ──

    /**
     * Rotates the loaded schematic clipboard by the given degrees (must be a multiple of 90).
     * Uses WorldEdit's FlattenedClipboardTransform to properly rotate blocks
     * (including directional block states like stairs, doors, etc.).
     *
     * @param player  the player whose loaded schematic to rotate
     * @param degrees rotation in degrees (90, 180, 270, -90, etc.)
     * @return true if rotation succeeded
     */
    public boolean rotateSchematic(Player player, int degrees) {
        LoadedSchematic loaded = loadedSchematics.get(player.getUniqueId());
        if (loaded == null) return false;

        // Normalize degrees to 0-359
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) return true; // no-op

        try {
            Clipboard original = loaded.clipboard();
            AffineTransform transform = new AffineTransform().rotateY(-normalized);

            // Use FlattenedClipboardTransform for proper rotation (handles block states)
            com.sk89q.worldedit.extent.transform.BlockTransformExtent transformExtent =
                    new com.sk89q.worldedit.extent.transform.BlockTransformExtent(original, transform);

            // Compute new bounds by transforming all 8 corners
            BlockVector3 origin = original.getOrigin();
            BlockVector3 min = original.getMinimumPoint();
            BlockVector3 max = original.getMaximumPoint();

            int newMinX = Integer.MAX_VALUE, newMinY = Integer.MAX_VALUE, newMinZ = Integer.MAX_VALUE;
            int newMaxX = Integer.MIN_VALUE, newMaxY = Integer.MIN_VALUE, newMaxZ = Integer.MIN_VALUE;

            int[][] corners = {
                    {min.x(), min.y(), min.z()}, {min.x(), min.y(), max.z()},
                    {min.x(), max.y(), min.z()}, {min.x(), max.y(), max.z()},
                    {max.x(), min.y(), min.z()}, {max.x(), min.y(), max.z()},
                    {max.x(), max.y(), min.z()}, {max.x(), max.y(), max.z()}
            };

            for (int[] c : corners) {
                com.sk89q.worldedit.math.Vector3 rel = com.sk89q.worldedit.math.Vector3.at(
                        c[0] - origin.x(), c[1] - origin.y(), c[2] - origin.z());
                com.sk89q.worldedit.math.Vector3 rotated = transform.apply(rel);
                int nx = (int) Math.floor(rotated.x() + origin.x());
                int ny = (int) Math.floor(rotated.y() + origin.y());
                int nz = (int) Math.floor(rotated.z() + origin.z());
                newMinX = Math.min(newMinX, nx); newMinY = Math.min(newMinY, ny); newMinZ = Math.min(newMinZ, nz);
                newMaxX = Math.max(newMaxX, nx); newMaxY = Math.max(newMaxY, ny); newMaxZ = Math.max(newMaxZ, nz);
            }

            // Create new clipboard with rotated bounds
            com.sk89q.worldedit.regions.CuboidRegion newRegion = new com.sk89q.worldedit.regions.CuboidRegion(
                    BlockVector3.at(newMinX, newMinY, newMinZ),
                    BlockVector3.at(newMaxX, newMaxY, newMaxZ));
            com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard rotatedClip =
                    new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(newRegion);
            rotatedClip.setOrigin(origin);

            // Copy each block with position + state rotation
            for (int x = min.x(); x <= max.x(); x++) {
                for (int y = min.y(); y <= max.y(); y++) {
                    for (int z = min.z(); z <= max.z(); z++) {
                        com.sk89q.worldedit.world.block.BaseBlock block =
                                original.getFullBlock(BlockVector3.at(x, y, z));
                        if (block.getBlockType().getMaterial().isAir()) continue;

                        // Compute rotated position
                        com.sk89q.worldedit.math.Vector3 rel = com.sk89q.worldedit.math.Vector3.at(
                                x - origin.x(), y - origin.y(), z - origin.z());
                        com.sk89q.worldedit.math.Vector3 rotated = transform.apply(rel);
                        BlockVector3 newPos = BlockVector3.at(
                                (int) Math.floor(rotated.x() + origin.x()),
                                (int) Math.floor(rotated.y() + origin.y()),
                                (int) Math.floor(rotated.z() + origin.z()));

                        // Use BlockTransformExtent to rotate block state (stairs, logs, etc.)
                        com.sk89q.worldedit.world.block.BaseBlock transformedBlock;
                        try {
                            transformedBlock = transformExtent.getBlock(BlockVector3.at(x, y, z)).toBaseBlock();
                        } catch (Exception e) {
                            transformedBlock = block; // fallback: no state rotation
                        }

                        rotatedClip.setBlock(newPos, transformedBlock);
                    }
                }
            }

            BlockVector3 dim = rotatedClip.getDimensions();
            loadedSchematics.put(player.getUniqueId(),
                    new LoadedSchematic(loaded.name(), rotatedClip, loaded.sizeBytes(), dim.x(), dim.y(), dim.z()));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to rotate schematic: " + e.getMessage());
            return false;
        }
    }

    // ── Paste with preview ──

    /**
     * Determines if a Bukkit Material is a "special" (non-full-cube) block.
     * Slabs, stairs, fences, walls, doors, trapdoors, signs, buttons, pressure plates,
     * torches, lanterns, chains, carpets, rails, flowers, grass, saplings, etc.
     */
    private static boolean isSpecialBlock(Material mat) {
        String n = mat.name();
        return n.contains("SLAB") || n.contains("STAIR") || n.contains("FENCE") || n.contains("WALL")
                || n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("SIGN") || n.contains("BUTTON")
                || n.contains("PRESSURE_PLATE") || n.contains("TORCH") || n.contains("LANTERN")
                || n.contains("CHAIN") || n.contains("CARPET") || n.contains("RAIL") || n.contains("FLOWER")
                || n.contains("SAPLING") || n.contains("BANNER") || n.contains("HEAD") || n.contains("SKULL")
                || n.contains("CANDLE") || n.contains("AMETHYST_CLUSTER") || n.contains("POINTED_DRIPSTONE")
                || n.contains("LEVER") || n.contains("TRIPWIRE") || n.contains("REPEATER")
                || n.contains("COMPARATOR") || n.contains("REDSTONE_WIRE") || n.contains("PANE")
                || n.contains("BARS") || n.contains("ANVIL") || n.contains("BREWING_STAND")
                || n.contains("CAULDRON") || n.contains("BELL") || n.contains("GRINDSTONE")
                || n.contains("STONECUTTER") || n.contains("CAMPFIRE") || n.contains("LADDER")
                || n.contains("VINE") || n.contains("LILY_PAD") || n.contains("SNOW")
                || mat == Material.SHORT_GRASS || mat == Material.TALL_GRASS
                || mat == Material.FERN || mat == Material.LARGE_FERN
                || mat == Material.DEAD_BUSH || mat == Material.SEAGRASS || mat == Material.TALL_SEAGRASS
                || mat == Material.KELP || mat == Material.KELP_PLANT
                || mat == Material.SUGAR_CANE || mat == Material.BAMBOO
                || mat == Material.COBWEB || mat == Material.HANGING_ROOTS
                || mat == Material.GLOW_LICHEN || mat == Material.SCULK_VEIN
                || mat == Material.END_ROD || mat == Material.LIGHTNING_ROD
                || mat == Material.FLOWER_POT || mat == Material.TURTLE_EGG
                || mat == Material.SEA_PICKLE;
    }

    /**
     * Starts the paste process: shows a colored preview for 10 seconds, then commits.
     *
     * @param player    the player
     * @param ignoreAir if true, air blocks from the schematic are skipped (flag -a)
     */
    public void paste(Player player, boolean ignoreAir, boolean skipPreview) {
        LoadedSchematic loaded = loadedSchematics.get(player.getUniqueId());
        if (loaded == null) {
            plugin.getMessageUtil().sendError(player, "No schematic loaded! Use §e/mct schematic load <name>");
            return;
        }

        // Cancel any existing preview
        cancelPreview(player);

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Clipboard clipboard = loaded.clipboard();
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();

        int offX = loc.getBlockX() - origin.x();
        int offY = loc.getBlockY() - origin.y();
        int offZ = loc.getBlockZ() - origin.z();

        // Collect blocks from clipboard
        Map<Location, BlockData> finalBlocks = new LinkedHashMap<>();
        Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
        int solidCount = 0;
        int specialCount = 0;

        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    com.sk89q.worldedit.world.block.BlockState state = clipboard.getBlock(BlockVector3.at(x, y, z));
                    if (state.getBlockType().getMaterial().isAir()) continue;

                    int wx = x + offX;
                    int wy = y + offY;
                    int wz = z + offZ;
                    if (wy < world.getMinHeight() || wy > world.getMaxHeight()) continue;

                    BlockData adapted = BukkitAdapter.adapt(state);

                    // -a flag: skip air-like replacements (the schematic block replaces world air)
                    // Actually -a means "don't paste air from schematic" which we already handle above.
                    // But if ignoreAir is false, we also include the schematic's structure blocks that
                    // would replace existing world blocks with air — not applicable here since we skip air.

                    Location blockLoc = new Location(world, wx, wy, wz);
                    Block block = blockLoc.getBlock();
                    originalBlocks.put(blockLoc.clone(), block.getBlockData().clone());
                    finalBlocks.put(blockLoc.clone(), adapted);

                    if (isSpecialBlock(adapted.getMaterial())) {
                        specialCount++;
                    } else {
                        solidCount++;
                    }
                }
            }
        }

        if (finalBlocks.isEmpty()) {
            plugin.getMessageUtil().sendError(player, "Schematic is empty!");
            return;
        }

        // -p flag: skip preview, paste immediately
        if (skipPreview) {
            PastePreview instant = new PastePreview(player, world, finalBlocks, originalBlocks,
                    loaded, solidCount, specialCount, ignoreAir);
            instant.commit();
            return;
        }

        // Place colored preview blocks
        BlockData solidPreview  = PREVIEW_SOLID.createBlockData();
        BlockData specialPreview = PREVIEW_SPECIAL.createBlockData();

        for (Map.Entry<Location, BlockData> entry : finalBlocks.entrySet()) {
            Material mat = entry.getValue().getMaterial();
            entry.getKey().getBlock().setBlockData(
                    isSpecialBlock(mat) ? specialPreview : solidPreview, false);
        }

        // Create preview state
        PastePreview preview = new PastePreview(player, world, finalBlocks, originalBlocks,
                loaded, solidCount, specialCount, ignoreAir);
        activePreviews.put(player.getUniqueId(), preview);
        preview.startCountdown();

        // ── Pretty preview message ──
        var msgUtil = plugin.getMessageUtil();
        player.sendMessage(msgUtil.parse(""));
        player.sendMessage(msgUtil.parse(
                "<gradient:#a855f7:#6d28d9><bold>\u2591\u2592\u2593 SCHEMATIC PREVIEW \u2593\u2592\u2591</bold></gradient>"));
        player.sendMessage(msgUtil.parse(""));
        player.sendMessage(msgUtil.parse(
                "  <gray>Name:</gray> <white><bold>" + loaded.name() + "</bold></white>"));
        player.sendMessage(msgUtil.parse(
                "  <gray>Blocks:</gray> <aqua>" + String.format("%,d", finalBlocks.size()) + "</aqua>"
                + " <dark_gray>\u2502</dark_gray>"
                + " <#55cdfc>\u2588</#55cdfc> <gray>Solid:</gray> <white>" + String.format("%,d", solidCount) + "</white>"
                + " <dark_gray>\u2502</dark_gray>"
                + " <#d946ef>\u2588</#d946ef> <gray>Special:</gray> <white>" + String.format("%,d", specialCount) + "</white>"));
        if (ignoreAir) {
            player.sendMessage(msgUtil.parse(
                    "  <yellow>\u26a0</yellow> <gray>Mode:</gray> <yellow>Ignore air</yellow> <dark_gray>(-a)</dark_gray>"));
        }
        player.sendMessage(msgUtil.parse(
                "  <gray>Pasting in</gray> <green><bold>" + PREVIEW_DURATION_SECONDS + "s</bold></green>"
                + " <dark_gray>\u2502</dark_gray>"
                + " <red><click:run_command:'/mct cancel'><hover:show_text:'<red>Cancel paste</red>'>[Cancel]</hover></click></red>"));
        player.sendMessage(msgUtil.parse(""));

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
    }

    /** Overload: ignoreAir only, no skipPreview. */
    public void paste(Player player, boolean ignoreAir) {
        paste(player, ignoreAir, false);
    }

    /** Overload for backward compatibility (no flags). */
    public void paste(Player player) {
        paste(player, false, false);
    }

    /**
     * Cancels an active paste preview.
     */
    public boolean cancelPreview(Player player) {
        PastePreview preview = activePreviews.remove(player.getUniqueId());
        if (preview != null) {
            preview.cancel();
            return true;
        }
        return false;
    }

    /**
     * Checks if a player has an active paste preview.
     */
    public boolean hasActivePreview(Player player) {
        return activePreviews.containsKey(player.getUniqueId());
    }

    // ── Helpers ──

    private File findSchematicFile(String name) {
        File file = new File(schematicsFolder, name);
        if (file.exists()) return file;

        for (String ext : new String[]{".schem", ".schematic"}) {
            file = new File(schematicsFolder, name + ext);
            if (file.exists()) return file;
        }

        File[] files = schematicsFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                String fName = f.getName();
                String fNameNoExt = fName.contains(".") ? fName.substring(0, fName.lastIndexOf('.')) : fName;
                if (fName.equalsIgnoreCase(name) || fNameNoExt.equalsIgnoreCase(name)) {
                    return f;
                }
            }
        }
        return null;
    }

    public File getSchematicsFolder() {
        return schematicsFolder;
    }

    /**
     * Returns the list of available schematic names (without extension) for tab completion.
     */
    public List<String> getSchematicNames() {
        List<String> names = new ArrayList<>();
        for (SchematicInfo info : listSchematics()) {
            String name = info.name();
            if (name.contains(".")) {
                name = name.substring(0, name.lastIndexOf('.'));
            }
            names.add(name);
        }
        return names;
    }

    /**
     * Formats a file size in human-readable form.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ── Records ──

    public record SchematicInfo(String name, long sizeBytes) {}

    public record LoadedSchematic(String name, Clipboard clipboard, long sizeBytes,
                                  int sizeX, int sizeY, int sizeZ) {}

    // ── Preview state ──

    private class PastePreview {
        private final Player player;
        private final World world;
        private final Map<Location, BlockData> finalBlocks;
        private final Map<Location, BlockData> originalBlocks;
        private final LoadedSchematic schematic;
        private final int solidCount;
        private final int specialCount;
        private final boolean ignoreAir;
        private BukkitTask countdownTask;
        private BukkitTask commitTask;
        private BossBar bossBar;
        private boolean committed = false;
        private int secondsRemaining = PREVIEW_DURATION_SECONDS;

        PastePreview(Player player, World world,
                     Map<Location, BlockData> finalBlocks,
                     Map<Location, BlockData> originalBlocks,
                     LoadedSchematic schematic,
                     int solidCount, int specialCount, boolean ignoreAir) {
            this.player = player;
            this.world = world;
            this.finalBlocks = finalBlocks;
            this.originalBlocks = originalBlocks;
            this.schematic = schematic;
            this.solidCount = solidCount;
            this.specialCount = specialCount;
            this.ignoreAir = ignoreAir;
            createBossbar();
        }

        private void createBossbar() {
            if (!plugin.getConfigManager().isEtaBossbarEnabled()) return;
            bossBar = Bukkit.createBossBar(
                    "\u00a7d\u00a7l\ud83d\udccb \u00a7fSchematic \u00a77\u2502 \u00a7a" + secondsRemaining + "s \u00a77\u2502 \u00a7e" +
                            String.format("%,d", finalBlocks.size()) + " blocks",
                    BarColor.PURPLE, BarStyle.SEGMENTED_10);
            bossBar.setProgress(1.0);
            bossBar.addPlayer(player);
            bossBar.setVisible(true);
        }

        private void updateBossbar() {
            if (bossBar == null) return;
            double progress = Math.max(0.0, (double) secondsRemaining / PREVIEW_DURATION_SECONDS);
            bossBar.setProgress(progress);

            String icon;
            if (secondsRemaining <= 2) {
                icon = "\u00a7c\u00a7l\u26a1";
                bossBar.setColor(BarColor.RED);
            } else if (secondsRemaining <= 4) {
                icon = "\u00a76\u00a7l\u23f0";
                bossBar.setColor(BarColor.YELLOW);
            } else {
                icon = "\u00a7d\u00a7l\ud83d\udccb";
                bossBar.setColor(BarColor.PURPLE);
            }

            bossBar.setTitle(icon + " \u00a7fSchematic \u00a77\u2502 \u00a7a" + secondsRemaining + "s \u00a77\u2502 \u00a7e" +
                    String.format("%,d", finalBlocks.size()) + " blocks");
        }

        private void removeBossbar() {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
        }

        void startCountdown() {
            countdownTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || committed) {
                        cancel();
                        return;
                    }
                    secondsRemaining--;
                    updateBossbar();

                    if (secondsRemaining <= 3 && secondsRemaining > 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                    }

                    if (secondsRemaining <= 0) {
                        cancel();
                        commit();
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);

            commitTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || committed) return;
                    commit();
                }
            }.runTaskLater(plugin, (PREVIEW_DURATION_SECONDS + 1) * 20L);
        }

        private void commit() {
            if (committed) return;
            committed = true;

            if (countdownTask != null) countdownTask.cancel();
            if (commitTask != null) commitTask.cancel();
            removeBossbar();
            activePreviews.remove(player.getUniqueId());

            // Restore originals so WorldEdit sees the real state
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    loc.getBlock().setBlockData(entry.getValue(), false);
                }
            }

            // Paste using WorldEdit for //undo support
            int placed = pasteWithWorldEdit();

            // Also save for MCTools undo
            plugin.getUndoManager().saveOperation(player, new LinkedHashMap<>(originalBlocks));

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

            // ── Pretty completion message ──
            var msgUtil = plugin.getMessageUtil();
            player.sendMessage(msgUtil.parse(""));
            player.sendMessage(msgUtil.parse(
                    "<gradient:#10b981:#059669><bold>\u2714 SCHEMATIC PASTED</bold></gradient>"));
            player.sendMessage(msgUtil.parse(
                    "  <gray>Name:</gray> <white>" + schematic.name() + "</white>"
                    + " <dark_gray>\u2502</dark_gray>"
                    + " <gray>Blocks:</gray> <green>" + String.format("%,d", placed) + "</green>"));
            player.sendMessage(msgUtil.parse(
                    "  <dark_gray>\u21b3</dark_gray> <gray>Use</gray> <yellow>//undo</yellow> <gray>or</gray> <yellow>/mct undo</yellow> <gray>to revert.</gray>"));
            player.sendMessage(msgUtil.parse(""));
        }

        private int pasteWithWorldEdit() {
            int placed = 0;
            EditSession editSession = null;
            try {
                BukkitPlayer wePlayer = BukkitAdapter.adapt(player);
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

                editSession = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(weWorld)
                        .actor(wePlayer)
                        .maxBlocks(finalBlocks.size())
                        .build();

                for (Map.Entry<Location, BlockData> entry : finalBlocks.entrySet()) {
                    Location loc = entry.getKey();
                    BlockVector3 pt = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    editSession.setBlock(pt, BukkitAdapter.adapt(entry.getValue()));
                    placed++;
                }

                editSession.close();

                LocalSession localSession = WorldEdit.getInstance().getSessionManager().get(wePlayer);
                if (localSession != null) {
                    localSession.remember(editSession);
                }
            } catch (WorldEditException e) {
                plugin.getLogger().warning("WorldEdit error pasting schematic: " + e.getMessage());
                if (editSession != null) {
                    try { editSession.close(); } catch (Exception ignored) {}
                }
                return pasteWithBukkit();
            }
            return placed;
        }

        private int pasteWithBukkit() {
            int placed = 0;
            for (Map.Entry<Location, BlockData> entry : finalBlocks.entrySet()) {
                Location loc = entry.getKey();
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    loc.getBlock().setBlockData(entry.getValue(), false);
                    placed++;
                }
            }
            return placed;
        }

        void cancel() {
            if (committed) return;
            committed = true;

            if (countdownTask != null) countdownTask.cancel();
            if (commitTask != null) commitTask.cancel();
            removeBossbar();

            // Restore original blocks (only if still preview glass)
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    Material type = loc.getBlock().getType();
                    if (type == PREVIEW_SOLID || type == PREVIEW_SPECIAL) {
                        loc.getBlock().setBlockData(entry.getValue(), false);
                    }
                }
            }

            var msgUtil = plugin.getMessageUtil();
            player.sendMessage(msgUtil.parse(
                    "<red><bold>\u2718</bold></red> <gray>Schematic paste</gray> <red>cancelled</red><gray>, blocks restored.</gray>"));
        }
    }
}
