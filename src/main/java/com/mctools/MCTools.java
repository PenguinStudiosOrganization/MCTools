package com.mctools;

import com.mctools.brush.BrushManager;
import com.mctools.commands.MCBrushCommand;
import com.mctools.commands.MCToolsCommand;
import com.mctools.commands.MCToolsTabCompleter;
import com.mctools.listeners.PlayerListener;
import com.mctools.utils.BlockPlacer;
import com.mctools.utils.ConfigManager;
import com.mctools.utils.MessageUtil;
import com.mctools.utils.PerformanceMonitor;
import com.mctools.utils.UndoManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class.
 *
 * <p>This plugin provides advanced tools for Minecraft builders:
 * shapes (2D/3D), terrain brush, preview, undo/redo and async placement.</p>
 *
 * <p>Project:
 * <ul>
 *   <li>Team: PenguinStudios</li>
 *   <li>Website: https://mcutils.net/</li>
 *   <li>Releases: https://github.com/PenguinStudiosOrganization/MCTools/releases/tag/Release</li>
 * </ul>
 * </p>
 * MCTools - Advanced shape generation tool for Minecraft builders.
 * 
 * Features:
 * - 2D shapes (circles, squares, rectangles, polygons, etc.)
 * - 3D shapes (spheres, cylinders, pyramids, torus, etc.)
 * - Terrain brush with heightmap support
 * - Preview mode with teleportation
 * - Advanced undo/redo system (up to 1000 operations)
 * - Async block placement
 * 
 * @author PenguinStudios
 * @version 2.0.0
 */
public final class MCTools extends JavaPlugin {

    private static MCTools instance;
    private ConfigManager configManager;
    private UndoManager undoManager;
    private MessageUtil messageUtil;
    private BrushManager brushManager;
    private PerformanceMonitor performanceMonitor;
    private BlockPlacer blockPlacer;

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

        // Commands and listeners.
        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

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
        getLogger().info("Enabled MCTools v" + version + " (" + loadTimeMs + "ms)");
        getLogger().info("Team: PenguinStudios");
        getLogger().info("Website: https://mcutils.net/");
        getLogger().info("Download: https://github.com/PenguinStudiosOrganization/MCTools/releases/tag/Release");

        final String R = "\u001B[0m";
        final String B = "\u001B[1m";
        final String C = "\u001B[36m";
        final String G = "\u001B[32m";
        final String Y = "\u001B[33m";
        final String W = "\u001B[37m";
        final String GR = "\u001B[90m";
        final String BL = "\u001B[34m";
        final String M = "\u001B[35m";
        
        System.out.println();
        System.out.println(C + B + "  ╔════════════════════════════════════════════╗" + R);
        System.out.println(C + B + "  ║" + R + Y + B + "           M C T O O L S                   " + R + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + W + "       Advanced Shape Generation           " + R + C + B + "║" + R);
        System.out.println(C + B + "  ╠════════════════════════════════════════════╣" + R);
        System.out.println(C + B + "  ║" + R + GR + " Version:  " + W + "v" + version + R + padSpaces(32 - version.length()) + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + GR + " Author:   " + W + "PenguinStudios" + R + "                   " + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + GR + " Web:      " + BL + "https://mcutils.net/shapegenerator" + R + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + GR + " Discord:  " + M + "https://discord.penguinstudios.eu" + R + C + B + "║" + R);
        System.out.println(C + B + "  ╠════════════════════════════════════════════╣" + R);
        System.out.println(C + B + "  ║" + R + G + " ✓ " + W + "Configuration loaded" + R + "                   " + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + G + " ✓ " + W + "Commands registered" + R + "                    " + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + G + " ✓ " + W + "Terrain brush system" + R + "                   " + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + G + " ✓ " + W + "Undo/Redo system (1000 ops)" + R + "            " + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + G + " ✓ " + W + "Preview with teleportation" + R + "             " + C + B + "║" + R);
        System.out.println(C + B + "  ╠════════════════════════════════════════════╣" + R);
        System.out.println(C + B + "  ║" + R + GR + " Load time: " + G + loadTime + "ms" + R + padSpaces(30 - String.valueOf(loadTime).length()) + C + B + "║" + R);
        System.out.println(C + B + "  ║" + R + GR + " Status:    " + G + B + "● READY" + R + "                          " + C + B + "║" + R);
        System.out.println(C + B + "  ╚════════════════════════════════════════════╝" + R);
        System.out.println();
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
}
