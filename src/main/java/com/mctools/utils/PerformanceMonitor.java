package com.mctools.utils;

import com.mctools.MCTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Server performance guard and adaptive throttling helper.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Expose a lightweight snapshot of server health (TPS/memory) for user feedback.</li>
 *   <li>Decide if an operation should be allowed to start ("safety check").</li>
 *   <li>Provide an adaptive "blocks per tick" rate to reduce lag during large edits.</li>
 *   <li>Trigger emergency cancellation when the server is under heavy load.</li>
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>All thresholds should be treated as heuristics; they may vary per server.</li>
 *   <li>This class must stay fast: it can be queried frequently during placement.</li>
 * </ul>
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

    // Block placement limits - adaptive based on server state
    private static final int MIN_BLOCKS_PER_TICK = 5;
    private static final int MAX_BLOCKS_PER_TICK_NORMAL = 500;
    private static final int MAX_BLOCKS_PER_TICK_EXCELLENT = 5000; // Near-instant when server is healthy
    private static final int DEFAULT_BLOCKS_PER_TICK = 50;

    // TPS averaging (3 second window for faster response)
    private static final int TPS_SAMPLE_COUNT = 60;
    private final Queue<Double> tpsSamples = new LinkedList<>();
    private double averageTps = 20.0;
    private double lastTps = 20.0;

    // MSPT (milliseconds per tick) monitoring
    private static final int MSPT_SAMPLE_COUNT = 60;
    private final Queue<Double> msptSamples = new LinkedList<>();
    private double averageMspt = 50.0;

    // RAM monitoring
    private double ramUsagePercent = 0.0;
    private long usedMemoryMB = 0;
    private long maxMemoryMB = 0;

    // Performance state
    private PerformanceState currentState = PerformanceState.EXCELLENT;
    private boolean criticalMode = false;

    // Monitoring task
    private BukkitTask monitorTask;
    private BukkitTask snapshotTask;

    // ── Historical performance snapshots ──
    // A snapshot is recorded every second; older entries are pruned automatically.
    // Max retention: 1 hour (3600 entries).
    private static final int MAX_HISTORY_SECONDS = 3600;
    private final ConcurrentLinkedDeque<PerformanceSnapshot> history = new ConcurrentLinkedDeque<>();

    /** Immutable snapshot of server metrics at a point in time. */
    public record PerformanceSnapshot(long timestampMs, double tps, double mspt, double ramPercent, int blocksPerTick) {}

    /** Averaged metrics over a time window. */
    public record WindowAverage(String label, double avgTps, double avgMspt, double avgRamPercent, int avgBpt, int sampleCount) {}

    public enum PerformanceState {
        EXCELLENT("§a++", "§aExcellent", "<green>++</green>", "<green>Excellent</green>", 1.0),
        GOOD("§a+", "§aGood", "<green>+</green>", "<green>Good</green>", 0.8),
        MEDIUM("§e~", "§eMedium", "<yellow>~</yellow>", "<yellow>Medium</yellow>", 0.5),
        LOW("§6-", "§6Low", "<gold>-</gold>", "<gold>Low</gold>", 0.25),
        CRITICAL("§c!", "§cCritical", "<red>!</red>", "<red>Critical</red>", 0.1),
        EMERGENCY("§4!!", "§4EMERGENCY", "<dark_red>!!</dark_red>", "<dark_red>EMERGENCY</dark_red>", 0.0);

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
        startSnapshotRecording();
    }

    /** Starts the TPS and RAM monitoring task. */
    private void startMonitoring() {
        monitorTask = new BukkitRunnable() {
            private long lastTime = System.nanoTime();

            @Override
            public void run() {
                long now = System.nanoTime();
                double elapsed = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                // Calculate instantaneous TPS.
                double instantTps = Math.min(20.0, 1.0 / elapsed);

                // Calculate MSPT (milliseconds per tick).
                double instantMspt = elapsed * 1000.0; // convert seconds to ms
                msptSamples.add(instantMspt);
                if (msptSamples.size() > MSPT_SAMPLE_COUNT) {
                    msptSamples.poll();
                }
                double msptSum = 0;
                for (double sample : msptSamples) {
                    msptSum += sample;
                }
                averageMspt = msptSum / msptSamples.size();

                // Use Bukkit's TPS as the main signal (more reliable than local timing).
                double[] bukkitTps = Bukkit.getTPS();
                double combinedTps = (instantTps * 0.3 + bukkitTps[0] * 0.7);

                tpsSamples.add(combinedTps);
                if (tpsSamples.size() > TPS_SAMPLE_COUNT) {
                    tpsSamples.poll();
                }

                double sum = 0;
                for (double sample : tpsSamples) {
                    sum += sample;
                }
                lastTps = averageTps;
                averageTps = sum / tpsSamples.size();

                // Update RAM usage.
                Runtime runtime = Runtime.getRuntime();
                maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                usedMemoryMB = (totalMemory - freeMemory) / (1024 * 1024);
                ramUsagePercent = (double) usedMemoryMB / maxMemoryMB;

                updatePerformanceState();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Updates the current performance state based on TPS and RAM. */
    private void updatePerformanceState() {
        // RAM is checked first because memory pressure is usually more dangerous.
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

    /** Records a performance snapshot every second and prunes old entries. */
    private void startSnapshotRecording() {
        snapshotTask = new BukkitRunnable() {
            @Override
            public void run() {
                history.addLast(new PerformanceSnapshot(
                        System.currentTimeMillis(), averageTps, averageMspt, ramUsagePercent, calculateBlocksPerTick()));

                // Prune entries older than MAX_HISTORY_SECONDS
                long cutoff = System.currentTimeMillis() - (MAX_HISTORY_SECONDS * 1000L);
                while (!history.isEmpty() && history.peekFirst().timestampMs() < cutoff) {
                    history.pollFirst();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // every 1 second (20 ticks)
    }

    /**
     * Computes the average metrics over the last {@code seconds} seconds.
     *
     * @param label   human-readable label (e.g. "10s", "1m")
     * @param seconds window size in seconds
     * @return a {@link WindowAverage}, or {@code null} if no data is available for that window
     */
    public WindowAverage getWindowAverage(String label, int seconds) {
        long cutoff = System.currentTimeMillis() - (seconds * 1000L);
        double sumTps = 0, sumMspt = 0, sumRam = 0;
        long sumBpt = 0;
        int count = 0;

        // Iterate from newest to oldest; stop early once we pass the cutoff
        for (var it = history.descendingIterator(); it.hasNext(); ) {
            PerformanceSnapshot snap = it.next();
            if (snap.timestampMs() < cutoff) break;
            sumTps += snap.tps();
            sumMspt += snap.mspt();
            sumRam += snap.ramPercent();
            sumBpt += snap.blocksPerTick();
            count++;
        }

        if (count == 0) return null;

        return new WindowAverage(label,
                sumTps / count,
                sumMspt / count,
                sumRam / count,
                (int) (sumBpt / count),
                count);
    }

    /** @return the uptime of the monitor in seconds (based on snapshot history). */
    public long getUptimeSeconds() {
        if (history.isEmpty()) return 0;
        return (System.currentTimeMillis() - history.peekFirst().timestampMs()) / 1000;
    }

    /** Stops the monitoring task. */
    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        if (snapshotTask != null) {
            snapshotTask.cancel();
        }
    }

    /** @return the current average TPS computed by this monitor. */
    public double getAverageTps() {
        return averageTps;
    }

    /** @return Bukkit's 1-minute TPS average (index 0). */
    public double getBukkitTps() {
        return Bukkit.getTPS()[0];
    }

    /** @return the current average MSPT (milliseconds per tick). */
    public double getAverageMspt() {
        return averageMspt;
    }

    /**
     * Returns a performance label based on current state (English).
     */
    public String getPerformanceLabel() {
        return switch (currentState) {
            case EXCELLENT -> "Excellent";
            case GOOD -> "Good";
            case MEDIUM -> "Average";
            case LOW -> "Poor";
            case CRITICAL -> "Critical";
            case EMERGENCY -> "EMERGENCY";
        };
    }

    /**
     * Returns a full performance summary as a plain text string (no legacy codes).
     * Suitable for passing to MessageUtil.sendInfo() which adds the MCTools prefix.
     * Example: "Excellent | TPS: 19.8 | MSPT: 12.3ms | RAM: 1024/4096MB (25%)"
     */
    public String getFullPerformanceSummary() {
        int ramPercent = (int) (ramUsagePercent * 100);
        int bpt = calculateBlocksPerTick();

        return getPerformanceLabel() + " | TPS: " + String.format("%.1f", averageTps) +
                " | MSPT: " + String.format("%.1f", averageMspt) + "ms" +
                " | RAM: " + usedMemoryMB + "/" + maxMemoryMB + "MB (" + ramPercent + "%)" +
                " | Speed: " + bpt + " b/t";
    }

    /**
     * Returns a full performance summary formatted with MiniMessage colors.
     * Each metric (TPS, MSPT, RAM, Speed) is color-coded based on its value.
     * Suitable for sending via MessageUtil.sendRaw() with the plugin prefix.
     */
    public String getFullPerformanceSummaryMiniMessage() {
        int ramPercent = (int) (ramUsagePercent * 100);
        int bpt = calculateBlocksPerTick();

        // TPS color: green >= 18, yellow >= 15, gold >= 12, red < 12
        String tpsColor;
        if (averageTps >= 18.0) tpsColor = "green";
        else if (averageTps >= 15.0) tpsColor = "yellow";
        else if (averageTps >= 12.0) tpsColor = "gold";
        else tpsColor = "red";

        // MSPT color: green <= 30, yellow <= 45, gold <= 50, red > 50
        String msptColor;
        if (averageMspt <= 30.0) msptColor = "green";
        else if (averageMspt <= 45.0) msptColor = "yellow";
        else if (averageMspt <= 50.0) msptColor = "gold";
        else msptColor = "red";

        // RAM color based on existing thresholds
        String ramColor;
        if (ramUsagePercent >= RAM_CRITICAL) ramColor = "dark_red";
        else if (ramUsagePercent >= RAM_DANGER) ramColor = "red";
        else if (ramUsagePercent >= RAM_WARNING) ramColor = "gold";
        else ramColor = "green";

        // Speed color: based on performance state
        String speedColor;
        if (bpt >= 1000) speedColor = "green";
        else if (bpt >= 200) speedColor = "yellow";
        else if (bpt >= 50) speedColor = "gold";
        else speedColor = "red";

        return currentState.iconMini + " " + currentState.nameMini +
                " <dark_gray>│</dark_gray> <gray>TPS:</gray> <" + tpsColor + ">" + String.format("%.1f", averageTps) + "</" + tpsColor + ">" +
                " <dark_gray>│</dark_gray> <gray>MSPT:</gray> <" + msptColor + ">" + String.format("%.1f", averageMspt) + "ms</" + msptColor + ">" +
                " <dark_gray>│</dark_gray> <gray>RAM:</gray> <" + ramColor + ">" + usedMemoryMB + "</" + ramColor + "><gray>/</gray><white>" + maxMemoryMB + "MB</white> <gray>(</gray><" + ramColor + ">" + ramPercent + "%</" + ramColor + "><gray>)</gray>" +
                " <dark_gray>│</dark_gray> <gray>Speed:</gray> <" + speedColor + ">" + bpt + "</" + speedColor + "> <gray>b/t</gray>";
    }

    /** @return RAM usage as percentage (0.0 - 1.0). */
    public double getRamUsagePercent() {
        return ramUsagePercent;
    }

    /** @return used memory in MB. */
    public long getUsedMemoryMB() {
        return usedMemoryMB;
    }

    /** @return max memory in MB. */
    public long getMaxMemoryMB() {
        return maxMemoryMB;
    }

    /** @return current performance state. */
    public PerformanceState getCurrentState() {
        return currentState;
    }

    /** @return true if server is in critical mode (operations should be throttled/cancelled). */
    public boolean isCriticalMode() {
        return criticalMode;
    }

    /** @return true if operation should be cancelled due to performance. */
    public boolean shouldCancelOperation() {
        return currentState == PerformanceState.EMERGENCY;
    }

    /**
     * Calculates optimal blocks per tick based on current server performance.
     *
     * <p>This is the main method used by {@link BlockPlacer} to throttle placement.</p>
     */
    public int calculateBlocksPerTick() {
        if (currentState == PerformanceState.EMERGENCY) {
            return 0;
        }

        int maxBlocks = (currentState == PerformanceState.EXCELLENT)
                ? MAX_BLOCKS_PER_TICK_EXCELLENT
                : MAX_BLOCKS_PER_TICK_NORMAL;

        double tpsRatio = Math.max(0, (averageTps - TPS_CRITICAL) / (20.0 - TPS_CRITICAL));
        int tpsBasedBlocks = (int) (MIN_BLOCKS_PER_TICK + (maxBlocks - MIN_BLOCKS_PER_TICK) * tpsRatio);

        double ramPenalty = 1.0;
        if (ramUsagePercent >= RAM_DANGER) {
            ramPenalty = 0.1;
        } else if (ramUsagePercent >= RAM_WARNING) {
            ramPenalty = 0.3;
        } else if (ramUsagePercent >= RAM_SAFE) {
            ramPenalty = 0.6;
        }

        double stateMultiplier = currentState.speedMultiplier;
        int finalBlocks = (int) (tpsBasedBlocks * ramPenalty * stateMultiplier);

        return Math.max(MIN_BLOCKS_PER_TICK, Math.min(maxBlocks, finalBlocks));
    }

    /**
     * Compact status line (MiniMessage).
     * Example: ⚡ Good | 19.5 TPS | 240 b/t
     */
    public String getCompactStatusMiniMessage() {
        int blocksPerTick = calculateBlocksPerTick();
        String tpsColor = averageTps >= 18 ? "green" : (averageTps >= 15 ? "yellow" : "red");

        return currentState.iconMini + " " + currentState.nameMini +
                " <dark_gray>│</dark_gray> <" + tpsColor + ">" + String.format("%.1f", averageTps) + "</" + tpsColor + "> <gray>TPS</gray>" +
                " <dark_gray>│</dark_gray> <white>" + blocksPerTick + "</white> <gray>b/t</gray>";
    }

    /**
     * Detailed status line with RAM info (MiniMessage).
     * Intended for periodic updates during generation.
     */
    public String getDetailedStatusMiniMessage() {
        int blocksPerTick = calculateBlocksPerTick();

        String tpsColor = averageTps >= 18 ? "green" : (averageTps >= 15 ? "yellow" : "red");
        String ramColor = ramUsagePercent >= RAM_DANGER ? "red" : (ramUsagePercent >= RAM_WARNING ? "gold" : "green");
        int ramPercent = (int) (ramUsagePercent * 100);

        return "<dark_gray>|</dark_gray> " + currentState.iconMini + " " + currentState.nameMini +
                " <dark_gray>│</dark_gray> <gray>TPS:</gray> <" + tpsColor + ">" + String.format("%.1f", averageTps) + "</" + tpsColor + ">" +
                " <dark_gray>│</dark_gray> <gray>RAM:</gray> <" + ramColor + ">" + ramPercent + "%</" + ramColor + ">" +
                " <dark_gray>│</dark_gray> <gray>Speed:</gray> <white>" + blocksPerTick + "</white><gray>/t</gray>";
    }

    /**
     * @deprecated Use {@link #getCompactStatusMiniMessage()} or {@link #getDetailedStatusMiniMessage()}.
     */
    @Deprecated
    public String getPerformanceStatusMiniMessage() {
        return getDetailedStatusMiniMessage();
    }

    /**
     * Colored status indicator for legacy chat formatting (section sign codes).
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

    /** Short status indicator (MiniMessage). */
    public String getShortStatusMiniMessage() {
        int blocksPerTick = calculateBlocksPerTick();
        String tpsColor = averageTps >= 18 ? "green" : (averageTps >= 15 ? "yellow" : "red");
        return currentState.iconMini + " <" + tpsColor + ">" + String.format("%.1f", averageTps) + "</" + tpsColor + "> <gray>TPS</gray> <dark_gray>│</dark_gray> <white>" + blocksPerTick + "</white> <gray>b/t</gray>";
    }

    /** Short status indicator for legacy chat formatting. */
    public String getShortStatus() {
        int blocksPerTick = calculateBlocksPerTick();
        return currentState.icon + " §f" + String.format("%.1f", averageTps) + " TPS §7| §f" + blocksPerTick + " §7b/t";
    }

    /** Detailed RAM info (MiniMessage). */
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

    /** Detailed RAM info (legacy formatting). */
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
     * Safety check before starting a heavy operation.
     *
     * @param estimatedBlocks estimated number of blocks that will be touched
     * @return an error message if not safe; otherwise {@code null}
     */
    public String checkOperationSafety(int estimatedBlocks) {
        int ramPercent = (int) (ramUsagePercent * 100);

        if (currentState == PerformanceState.EMERGENCY) {
            return "Server is under extreme load! RAM: " + usedMemoryMB + "/" + maxMemoryMB + "MB (" + ramPercent + "%) - Operation cancelled.";
        }

        if (currentState == PerformanceState.CRITICAL) {
            return "Server performance is critical! TPS: " + String.format("%.1f", averageTps) +
                    " | RAM: " + usedMemoryMB + "/" + maxMemoryMB + "MB (" + ramPercent + "%) - Please wait.";
        }

        // Rough memory estimate (~100 bytes per block for Location + BlockData).
        long estimatedMemoryMB = (estimatedBlocks * 100L) / (1024 * 1024);
        long availableMemoryMB = maxMemoryMB - usedMemoryMB;

        if (estimatedMemoryMB > availableMemoryMB * 0.5) {
            return "Operation too large! Estimated memory: " + estimatedMemoryMB + "MB | Available: " + availableMemoryMB + "MB";
        }

        return null;
    }
}
