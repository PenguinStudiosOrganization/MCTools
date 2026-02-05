package com.mctools.utils;

import com.mctools.MCTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Centralized performance monitor for adaptive block placement.
 * Monitors TPS, RAM usage, and calculates optimal blocks per tick.
 * Will automatically cancel operations if server is under heavy load.
 * 
 * @author MCTools Team
 * @version 2.0.0
 */
public class PerformanceMonitor {

    private final MCTools plugin;
    
    // Performance thresholds for TPS
    private static final double TPS_EXCELLENT = 19.5;
    private static final double TPS_GOOD = 18.0;
    private static final double TPS_MEDIUM = 15.0;
    private static final double TPS_LOW = 12.0;
    private static final double TPS_CRITICAL = 8.0;
    
    // RAM thresholds (percentage of max)
    private static final double RAM_SAFE = 0.60;      // 60% - full speed
    private static final double RAM_WARNING = 0.75;   // 75% - slow down
    private static final double RAM_DANGER = 0.85;    // 85% - very slow
    private static final double RAM_CRITICAL = 0.92;  // 92% - cancel operations!
    
    // Block placement limits - much more conservative
    private static final int MIN_BLOCKS_PER_TICK = 5;
    private static final int MAX_BLOCKS_PER_TICK = 500;
    private static final int DEFAULT_BLOCKS_PER_TICK = 50;
    
    // TPS averaging (3 second window for faster response)
    private static final int TPS_SAMPLE_COUNT = 60;
    private final Queue<Double> tpsSamples = new LinkedList<>();
    private double averageTps = 20.0;
    private double lastTps = 20.0;
    
    // RAM monitoring
    private double ramUsagePercent = 0.0;
    private long usedMemoryMB = 0;
    private long maxMemoryMB = 0;
    
    // Performance state
    private PerformanceState currentState = PerformanceState.EXCELLENT;
    private boolean criticalMode = false;
    
    // Monitoring task
    private BukkitTask monitorTask;

    public enum PerformanceState {
        EXCELLENT("§a⚡⚡", "§aExcellent", "<green>⚡⚡</green>", "<green>Excellent</green>", 1.0),
        GOOD("§a⚡", "§aGood", "<green>⚡</green>", "<green>Good</green>", 0.8),
        MEDIUM("§e◆", "§eMedium", "<yellow>◆</yellow>", "<yellow>Medium</yellow>", 0.5),
        LOW("§6◇", "§6Low", "<gold>◇</gold>", "<gold>Low</gold>", 0.25),
        CRITICAL("§c⚠", "§cCritical", "<red>⚠</red>", "<red>Critical</red>", 0.1),
        EMERGENCY("§4☠", "§4EMERGENCY", "<dark_red>☠</dark_red>", "<dark_red>EMERGENCY</dark_red>", 0.0);
        
        public final String icon;
        public final String name;
        public final String iconMini;
        public final String nameMini;
        public final double speedMultiplier;
        
        PerformanceState(String icon, String name, String iconMini, String nameMini, double speedMultiplier) {
            this.icon = icon;
            this.name = name;
            this.iconMini = iconMini;
            this.nameMini = nameMini;
            this.speedMultiplier = speedMultiplier;
        }
    }

    public PerformanceMonitor(MCTools plugin) {
        this.plugin = plugin;
        startMonitoring();
    }
    
    /**
     * Starts the TPS and RAM monitoring task.
     */
    private void startMonitoring() {
        monitorTask = new BukkitRunnable() {
            private long lastTime = System.nanoTime();
            
            @Override
            public void run() {
                long now = System.nanoTime();
                double elapsed = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;
                
                // Calculate instantaneous TPS
                double instantTps = Math.min(20.0, 1.0 / elapsed);
                
                // Get Bukkit's TPS (more reliable)
                double[] bukkitTps = Bukkit.getTPS();
                double combinedTps = (instantTps * 0.3 + bukkitTps[0] * 0.7); // Weight Bukkit TPS more
                
                // Add to samples
                tpsSamples.add(combinedTps);
                if (tpsSamples.size() > TPS_SAMPLE_COUNT) {
                    tpsSamples.poll();
                }
                
                // Calculate average TPS
                double sum = 0;
                for (double sample : tpsSamples) {
                    sum += sample;
                }
                lastTps = averageTps;
                averageTps = sum / tpsSamples.size();
                
                // Update RAM usage
                Runtime runtime = Runtime.getRuntime();
                maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                usedMemoryMB = (totalMemory - freeMemory) / (1024 * 1024);
                ramUsagePercent = (double) usedMemoryMB / maxMemoryMB;
                
                // Update performance state
                updatePerformanceState();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    
    /**
     * Updates the current performance state based on TPS and RAM.
     */
    private void updatePerformanceState() {
        // Check RAM first - it's more critical
        if (ramUsagePercent >= RAM_CRITICAL) {
            currentState = PerformanceState.EMERGENCY;
            criticalMode = true;
            return;
        }
        
        if (averageTps < TPS_CRITICAL || ramUsagePercent >= RAM_DANGER) {
            currentState = PerformanceState.CRITICAL;
            criticalMode = true;
            return;
        }
        
        criticalMode = false;
        
        if (averageTps < TPS_LOW || ramUsagePercent >= RAM_WARNING) {
            currentState = PerformanceState.LOW;
        } else if (averageTps < TPS_MEDIUM) {
            currentState = PerformanceState.MEDIUM;
        } else if (averageTps < TPS_GOOD || ramUsagePercent >= RAM_SAFE) {
            currentState = PerformanceState.GOOD;
        } else {
            currentState = PerformanceState.EXCELLENT;
        }
    }
    
    /**
     * Stops the monitoring task.
     */
    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
    }
    
    /**
     * Gets the current average TPS.
     */
    public double getAverageTps() {
        return averageTps;
    }
    
    /**
     * Gets the raw Bukkit TPS.
     */
    public double getBukkitTps() {
        return Bukkit.getTPS()[0];
    }
    
    /**
     * Gets RAM usage as percentage (0.0 - 1.0).
     */
    public double getRamUsagePercent() {
        return ramUsagePercent;
    }
    
    /**
     * Gets used memory in MB.
     */
    public long getUsedMemoryMB() {
        return usedMemoryMB;
    }
    
    /**
     * Gets max memory in MB.
     */
    public long getMaxMemoryMB() {
        return maxMemoryMB;
    }
    
    /**
     * Gets current performance state.
     */
    public PerformanceState getCurrentState() {
        return currentState;
    }
    
    /**
     * Checks if server is in critical mode (should cancel operations).
     */
    public boolean isCriticalMode() {
        return criticalMode;
    }
    
    /**
     * Checks if an operation should be cancelled due to performance.
     */
    public boolean shouldCancelOperation() {
        return currentState == PerformanceState.EMERGENCY;
    }
    
    /**
     * Calculates optimal blocks per tick based on current server performance.
     * This is the main method used by BlockPlacer.
     */
    public int calculateBlocksPerTick() {
        // Emergency - stop everything
        if (currentState == PerformanceState.EMERGENCY) {
            return 0;
        }
        
        // Calculate base blocks from TPS
        double tpsRatio = Math.max(0, (averageTps - TPS_CRITICAL) / (20.0 - TPS_CRITICAL));
        int tpsBasedBlocks = (int) (MIN_BLOCKS_PER_TICK + (MAX_BLOCKS_PER_TICK - MIN_BLOCKS_PER_TICK) * tpsRatio);
        
        // Apply RAM penalty
        double ramPenalty = 1.0;
        if (ramUsagePercent >= RAM_DANGER) {
            ramPenalty = 0.1; // 90% reduction
        } else if (ramUsagePercent >= RAM_WARNING) {
            ramPenalty = 0.3; // 70% reduction
        } else if (ramUsagePercent >= RAM_SAFE) {
            ramPenalty = 0.6; // 40% reduction
        }
        
        // Apply state multiplier
        double stateMultiplier = currentState.speedMultiplier;
        
        // Calculate final blocks per tick
        int finalBlocks = (int) (tpsBasedBlocks * ramPenalty * stateMultiplier);
        
        // Ensure within bounds
        return Math.max(MIN_BLOCKS_PER_TICK, Math.min(MAX_BLOCKS_PER_TICK, finalBlocks));
    }
    
    /**
     * Gets a compact, professional performance status line (MiniMessage format).
     * Format: ⚡ Good | 19.5 TPS | 240 b/t
     */
    public String getCompactStatusMiniMessage() {
        int blocksPerTick = calculateBlocksPerTick();
        String tpsColor = averageTps >= 18 ? "green" : (averageTps >= 15 ? "yellow" : "red");
        
        return currentState.iconMini + " " + currentState.nameMini + 
               " <dark_gray>│</dark_gray> <" + tpsColor + ">" + String.format("%.1f", averageTps) + "</" + tpsColor + "> <gray>TPS</gray>" +
               " <dark_gray>│</dark_gray> <white>" + blocksPerTick + "</white> <gray>b/t</gray>";
    }
    
    /**
     * Gets a detailed performance status with RAM info (MiniMessage format).
     * Used for periodic updates during generation.
     */
    public String getDetailedStatusMiniMessage() {
        int blocksPerTick = calculateBlocksPerTick();
        
        String tpsColor = averageTps >= 18 ? "green" : (averageTps >= 15 ? "yellow" : "red");
        String ramColor = ramUsagePercent >= RAM_DANGER ? "red" : (ramUsagePercent >= RAM_WARNING ? "gold" : "green");
        int ramPercent = (int)(ramUsagePercent * 100);
        
        return "<dark_gray>┃</dark_gray> " + currentState.iconMini + " " + currentState.nameMini + 
               " <dark_gray>│</dark_gray> <gray>TPS:</gray> <" + tpsColor + ">" + String.format("%.1f", averageTps) + "</" + tpsColor + ">" +
               " <dark_gray>│</dark_gray> <gray>RAM:</gray> <" + ramColor + ">" + ramPercent + "%</" + ramColor + ">" +
               " <dark_gray>│</dark_gray> <gray>Speed:</gray> <white>" + blocksPerTick + "</white><gray>/t</gray>";
    }
    
    /**
     * Gets a colored performance status indicator for chat (MiniMessage format).
     * @deprecated Use getCompactStatusMiniMessage() or getDetailedStatusMiniMessage() instead
     */
    @Deprecated
    public String getPerformanceStatusMiniMessage() {
        return getDetailedStatusMiniMessage();
    }
    
    /**
     * Gets a colored performance status indicator for chat (legacy format for error messages).
     */
    public String getPerformanceStatus() {
        int blocksPerTick = calculateBlocksPerTick();
        
        String ramColor;
        if (ramUsagePercent >= RAM_CRITICAL) {
            ramColor = "§4";
        } else if (ramUsagePercent >= RAM_DANGER) {
            ramColor = "§c";
        } else if (ramUsagePercent >= RAM_WARNING) {
            ramColor = "§6";
        } else {
            ramColor = "§a";
        }
        
        return currentState.icon + " " + currentState.name + 
               " §7| TPS: §f" + String.format("%.1f", averageTps) + 
               " §7| RAM: " + ramColor + usedMemoryMB + "§7/§f" + maxMemoryMB + "MB" +
               " §7(" + ramColor + String.format("%.0f", ramUsagePercent * 100) + "%§7)" +
               " §7| §f" + blocksPerTick + "§7 b/t";
    }
    
    /**
     * Gets a short performance indicator (MiniMessage format).
     */
    public String getShortStatusMiniMessage() {
        int blocksPerTick = calculateBlocksPerTick();
        String tpsColor = averageTps >= 18 ? "green" : (averageTps >= 15 ? "yellow" : "red");
        return currentState.iconMini + " <" + tpsColor + ">" + String.format("%.1f", averageTps) + "</" + tpsColor + "> <gray>TPS</gray> <dark_gray>│</dark_gray> <white>" + blocksPerTick + "</white> <gray>b/t</gray>";
    }
    
    /**
     * Gets a short performance indicator for action bar (legacy format).
     */
    public String getShortStatus() {
        int blocksPerTick = calculateBlocksPerTick();
        return currentState.icon + " §f" + String.format("%.1f", averageTps) + " TPS §7| §f" + blocksPerTick + " §7b/t";
    }
    
    /**
     * Gets detailed RAM info (MiniMessage format).
     */
    public String getRamStatusMiniMessage() {
        String color;
        if (ramUsagePercent >= RAM_CRITICAL) {
            color = "dark_red";
        } else if (ramUsagePercent >= RAM_DANGER) {
            color = "red";
        } else if (ramUsagePercent >= RAM_WARNING) {
            color = "gold";
        } else {
            color = "green";
        }
        
        return "<" + color + ">" + usedMemoryMB + "</" + color + "><gray>/</gray><white>" + maxMemoryMB + "MB</white> <gray>(<" + color + ">" + String.format("%.1f", ramUsagePercent * 100) + "%</" + color + ">)</gray>";
    }
    
    /**
     * Gets detailed RAM info (legacy format).
     */
    public String getRamStatus() {
        String color;
        if (ramUsagePercent >= RAM_CRITICAL) {
            color = "§4";
        } else if (ramUsagePercent >= RAM_DANGER) {
            color = "§c";
        } else if (ramUsagePercent >= RAM_WARNING) {
            color = "§6";
        } else {
            color = "§a";
        }
        
        return color + usedMemoryMB + "§7/§f" + maxMemoryMB + "MB §7(" + color + String.format("%.1f", ramUsagePercent * 100) + "%§7)";
    }
    
    /**
     * Checks if it's safe to start a new operation with estimated block count.
     * Returns error message if not safe, null if safe.
     */
    public String checkOperationSafety(int estimatedBlocks) {
        // Check current state
        if (currentState == PerformanceState.EMERGENCY) {
            return "§cServer is under extreme load! RAM: " + getRamStatus() + " §c- Operation cancelled.";
        }
        
        if (currentState == PerformanceState.CRITICAL) {
            return "§cServer performance is critical! TPS: §f" + String.format("%.1f", averageTps) + 
                   " §c| RAM: " + getRamStatus() + " §c- Please wait.";
        }
        
        // Estimate memory needed (rough: ~100 bytes per block for Location + BlockData)
        long estimatedMemoryMB = (estimatedBlocks * 100L) / (1024 * 1024);
        long availableMemoryMB = maxMemoryMB - usedMemoryMB;
        
        if (estimatedMemoryMB > availableMemoryMB * 0.5) {
            return "§cOperation too large! Estimated memory: §f" + estimatedMemoryMB + "MB §c| Available: §f" + availableMemoryMB + "MB";
        }
        
        return null; // Safe to proceed
    }
}
