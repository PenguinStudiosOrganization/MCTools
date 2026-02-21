package com.mctools;

import com.mctools.brush.BrushManager;
import com.mctools.commands.MCBrushCommand;
import com.mctools.commands.MCToolsCommand;
import com.mctools.commands.MCToolsTabCompleter;
import com.mctools.listeners.PlayerListener;
import com.mctools.path.PathToolManager;
import com.mctools.schematic.SchematicManager;
import com.mctools.utils.BlockPlacer;
import com.mctools.utils.ConfigManager;
import com.mctools.utils.MessageUtil;
import com.mctools.utils.PerformanceMonitor;
import com.mctools.utils.UndoManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MCTools – Advanced shape generation tool for Minecraft builders.
 *
 * <p>This plugin provides a comprehensive set of tools for building in Minecraft,
 * including 2D shapes (circles, squares, rectangles, polygons, etc.),
 * 3D shapes (spheres, cylinders, pyramids, torus, etc.),
 * a terrain brush system with heightmap support,
 * preview mode with teleportation, an undo/redo system (up to 1000 operations),
 * and async block placement with adaptive throttling.</p>
 *
 * @see <a href="https://mcutils.net/">Website</a>
 * @see <a href="https://github.com/PenguinStudiosOrganization/MCTools/releases/tag/Release">Releases</a>
 */
public final class MCTools extends JavaPlugin {

    private static MCTools instance;
    private ConfigManager configManager;
    private UndoManager undoManager;
    private MessageUtil messageUtil;
    private BrushManager brushManager;
    private SchematicManager schematicManager;
    private PerformanceMonitor performanceMonitor;
    private BlockPlacer blockPlacer;
    private PathToolManager pathToolManager;
    private PlayerListener playerListener;

    public static MCTools getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        long startTime = System.currentTimeMillis();

        // Core services used by commands/listeners.
        configManager = new ConfigManager(this);
        undoManager = new UndoManager(this);
        messageUtil = new MessageUtil();
        performanceMonitor = new PerformanceMonitor(this);
        blockPlacer = new BlockPlacer(this);
        brushManager = new BrushManager(this);
        schematicManager = new SchematicManager(this);
        pathToolManager = new PathToolManager(this);

        // Commands and listeners.
        registerCommands();
        playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);

        long loadTime = System.currentTimeMillis() - startTime;

        // Keep startup logs simple and consistent with the server logger.
        logStartup(loadTime);
    }
    
    /**
     * Startup log.
     *
     * <p>Keep logs readable in any console (no ANSI art), and use the plugin logger
     * so messages are properly tagged by the server.</p>
     */
    private void logStartup(long loadTimeMs) {
        String version = getDescription().getVersion();

        java.util.logging.Logger log = getLogger();
        log.info("");
        log.info("╔════════════════════════════════════════════╗");
        log.info("║           M C T O O L S                   ║");
        log.info("║       Advanced Shape Generation           ║");
        log.info("╠════════════════════════════════════════════╣");
        log.info("║ Version:  v" + version + padSpaces(32 - version.length()) + "║");
        log.info("║ Author:   PenguinStudios                  ║");
        log.info("║ Web:      https://mcutils.net              ║");
        log.info("║ Discord:  https://discord.penguinstudios.eu║");
        log.info("╠════════════════════════════════════════════╣");
        log.info("║ ✓ Configuration loaded                    ║");
        log.info("║ ✓ Commands registered                     ║");
        log.info("║ ✓ Terrain brush system                    ║");
        log.info("║ ✓ Undo/Redo system (1000 ops)             ║");
        log.info("║ ✓ Preview with teleportation              ║");
        log.info("║ ✓ Schematic system                        ║");
        log.info("╠════════════════════════════════════════════╣");
        log.info("║ Load time: " + loadTimeMs + "ms" + padSpaces(31 - String.valueOf(loadTimeMs).length()) + "║");
        log.info("║ Status:    ● READY                        ║");
        log.info("╚════════════════════════════════════════════╝");
        log.info("");
    }

    private String padSpaces(int count) {
        if (count <= 0) return "";
        return " ".repeat(count);
    }

    @Override
    public void onDisable() {
        // Stop background tasks first.
        if (performanceMonitor != null) {
            performanceMonitor.stop();
        }

        // Free memory used by undo history.
        if (undoManager != null) {
            undoManager.clearAll();
        }

        getLogger().info("Disabled MCTools");
        instance = null;
    }

    private void registerCommands() {
        // MCTools shape commands
        PluginCommand mctCommand = getCommand("mctools");
        if (mctCommand != null) {
            MCToolsCommand executor = new MCToolsCommand(this);
            mctCommand.setExecutor(executor);
            mctCommand.setTabCompleter(new MCToolsTabCompleter(this));
        }
        
        // MCBrush terrain commands
        PluginCommand mcbCommand = getCommand("mcbrush");
        if (mcbCommand != null) {
            MCBrushCommand brushExecutor = new MCBrushCommand(this);
            mcbCommand.setExecutor(brushExecutor);
            mcbCommand.setTabCompleter(brushExecutor);
        }
    }

    /** Reload config and refresh managers that depend on it. */
    public void reloadPluginConfig() {
        reloadConfig();
        configManager.reload();

        if (brushManager != null) {
            brushManager.reload();
        }

        if (pathToolManager != null) {
            pathToolManager.loadConfig();
        }

        getLogger().info("Configuration reloaded");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public MessageUtil getMessageUtil() {
        return messageUtil;
    }
    
    public BrushManager getBrushManager() {
        return brushManager;
    }
    
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }
    
    public BlockPlacer getBlockPlacer() {
        return blockPlacer;
    }
    
    public PlayerListener getPlayerListener() {
        return playerListener;
    }
    
    public SchematicManager getSchematicManager() {
        return schematicManager;
    }
    
    public PathToolManager getPathToolManager() {
        return pathToolManager;
    }
}
