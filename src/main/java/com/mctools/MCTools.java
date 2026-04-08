package com.mctools;

import com.mctools.brush.BrushManager;
import com.mctools.commands.MCBrushCommand;
import com.mctools.commands.MCToolsCommand;
import com.mctools.commands.MCToolsTabCompleter;
import com.mctools.listeners.PlayerListener;
import com.mctools.path.PathToolManager;
import com.mctools.schematic.SchematicManager;
import com.mctools.update.UpdateChecker;
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
 * @see <a href="https://penguinstudios.eu/">Website</a>
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
    private UpdateChecker updateChecker;

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

        // Update checker (async, non-blocking).
        updateChecker = new UpdateChecker(this);
        updateChecker.start();

        long loadTime = System.currentTimeMillis() - startTime;

        // Keep startup logs simple and consistent with the server logger.
        logStartup(loadTime);
    }
    
    /**
     * Startup log — colored banner via legacy § codes sent to the console sender.
     */
    private void logStartup(long loadTimeMs) {
        String version = getDescription().getVersion();
        org.bukkit.command.ConsoleCommandSender console = org.bukkit.Bukkit.getConsoleSender();

        console.sendMessage("§r");
        console.sendMessage("§a  __  __  ___ _____         _    ");
        console.sendMessage("§a |  \\/  |/ __|_   _|__  ___| |___");
        console.sendMessage("§a | |\\/| | (__  | |/ _ \\/ _ \\ (_-<");
        console.sendMessage("§a |_|  |_|\\___| |_|\\___/\\___/_/__/");
        console.sendMessage("§r");
        console.sendMessage("§8┌─────────────────────────────────────────────┐");
        console.sendMessage("§8│  §aVersion   §f" + version + padSpaces(33 - version.length()) + "§8│");
        console.sendMessage("§8│  §aAuthor    §fPenguinStudios" + padSpaces(19) + "§8│");
        console.sendMessage("§8│  §aWebsite   §fpenguinstudios.eu" + padSpaces(17) + "§8│");
        console.sendMessage("§8│  §aDiscord   §fdiscord.penguinstudios.eu" + padSpaces(7) + "§8│");
        console.sendMessage("§8├─────────────────────────────────────────────┤");
        console.sendMessage("§8│  §a✔ §fConfiguration loaded" + padSpaces(21) + "§8│");
        console.sendMessage("§8│  §a✔ §fCommands registered" + padSpaces(22) + "§8│");
        console.sendMessage("§8│  §a✔ §fTerrain brush system" + padSpaces(21) + "§8│");
        console.sendMessage("§8│  §a✔ §fUndo / Redo  §7(up to 1000 ops)§f" + padSpaces(12) + "§8│");
        console.sendMessage("§8│  §a✔ §fPreview with teleportation" + padSpaces(15) + "§8│");
        console.sendMessage("§8│  §a✔ §fPath tool  §7(road / bridge / curve)§f" + padSpaces(7) + "§8│");
        console.sendMessage("§8│  §a✔ §fSchematic system" + padSpaces(25) + "§8│");
        console.sendMessage("§8│  §a✔ §fAsync block placement" + padSpaces(20) + "§8│");
        console.sendMessage("§8│  §a✔ §fUpdate checker" + padSpaces(27) + "§8│");
        console.sendMessage("§8├─────────────────────────────────────────────┤");
        String loadStr = loadTimeMs + "ms";
        console.sendMessage("§8│  §7Load time  §f" + loadStr + padSpaces(32 - loadStr.length()) + "§8│");
        console.sendMessage("§8│  §7Status     §a● READY" + padSpaces(25) + "§8│");
        console.sendMessage("§8└─────────────────────────────────────────────┘");
        console.sendMessage("§r");
    }

    private String padSpaces(int count) {
        if (count <= 0) return "";
        return " ".repeat(count);
    }

    @Override
    public void onDisable() {
        // Stop background tasks first.
        if (updateChecker != null) {
            updateChecker.shutdown();
        }

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

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
