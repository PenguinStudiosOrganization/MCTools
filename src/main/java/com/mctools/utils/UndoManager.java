package com.mctools.utils;

import com.mctools.MCTools;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player undo/redo history for block operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Store original block data before each operation.</li>
 *   <li>Restore blocks on undo (and save current state for redo).</li>
 *   <li>Limit history size to avoid memory bloat.</li>
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>History is stored per-player using {@link ConcurrentHashMap} for thread safety.</li>
 *   <li>Each operation is a snapshot of {@link Location} → {@link BlockData}.</li>
 *   <li>Redo is cleared when a new operation is saved (standard undo/redo semantics).</li>
 * </ul>
 */
public class UndoManager {

    private final MCTools plugin;
    private final Map<UUID, Deque<UndoOperation>> undoHistory;
    private final Map<UUID, Deque<UndoOperation>> redoHistory;
    
    private static final int MAX_HISTORY = 1000;

    public UndoManager(MCTools plugin) {
        this.plugin = plugin;
        this.undoHistory = new ConcurrentHashMap<>();
        this.redoHistory = new ConcurrentHashMap<>();
    }

    /**
     * Saves an operation for potential undo.
     */
    public void saveOperation(Player player, Map<Location, BlockData> blocks) {
        UUID uuid = player.getUniqueId();
        undoHistory.computeIfAbsent(uuid, k -> new LinkedList<>());
        
        Deque<UndoOperation> history = undoHistory.get(uuid);
        
        // Limit history size
        while (history.size() >= MAX_HISTORY) {
            history.removeLast();
        }
        
        history.addFirst(new UndoOperation(blocks));
        
        // Clear redo history when new operation is performed
        redoHistory.computeIfAbsent(uuid, k -> new LinkedList<>()).clear();
    }

    /**
     * Undoes the last operation for a player.
     * @return Number of blocks restored, or -1 if nothing to undo
     */
    public int undo(Player player) {
        return undo(player, 1);
    }

    /**
     * Undoes multiple operations for a player.
     * @param count Number of operations to undo
     * @return Total number of blocks restored, or -1 if nothing to undo
     */
    public int undo(Player player, int count) {
        UUID uuid = player.getUniqueId();
        Deque<UndoOperation> history = undoHistory.get(uuid);
        
        if (history == null || history.isEmpty()) {
            return -1;
        }
        
        count = Math.min(count, history.size());
        int totalRestored = 0;
        
        Deque<UndoOperation> redo = redoHistory.computeIfAbsent(uuid, k -> new LinkedList<>());
        
        for (int i = 0; i < count; i++) {
            UndoOperation operation = history.removeFirst();
            
            // Save current state for redo before restoring
            Map<Location, BlockData> currentState = new LinkedHashMap<>();
            for (Location loc : operation.getLocations()) {
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    currentState.put(loc.clone(), loc.getBlock().getBlockData().clone());
                }
            }
            
            // Restore original blocks
            int restored = operation.restore();
            totalRestored += restored;
            
            // Save to redo history
            redo.addFirst(new UndoOperation(currentState));
        }
        
        return totalRestored;
    }

    /**
     * Redoes the last undone operation for a player.
     * @return Number of blocks restored, or -1 if nothing to redo
     */
    public int redo(Player player) {
        return redo(player, 1);
    }

    /**
     * Redoes multiple undone operations for a player.
     * @param count Number of operations to redo
     * @return Total number of blocks restored, or -1 if nothing to redo
     */
    public int redo(Player player, int count) {
        UUID uuid = player.getUniqueId();
        Deque<UndoOperation> redo = redoHistory.get(uuid);
        
        if (redo == null || redo.isEmpty()) {
            return -1;
        }
        
        count = Math.min(count, redo.size());
        int totalRestored = 0;
        
        Deque<UndoOperation> history = undoHistory.computeIfAbsent(uuid, k -> new LinkedList<>());
        
        for (int i = 0; i < count; i++) {
            UndoOperation operation = redo.removeFirst();
            
            // Save current state for undo before restoring
            Map<Location, BlockData> currentState = new LinkedHashMap<>();
            for (Location loc : operation.getLocations()) {
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    currentState.put(loc.clone(), loc.getBlock().getBlockData().clone());
                }
            }
            
            // Restore redo blocks
            int restored = operation.restore();
            totalRestored += restored;
            
            // Save to undo history
            history.addFirst(new UndoOperation(currentState));
        }
        
        return totalRestored;
    }

    /**
     * Gets the number of undo operations available for a player.
     */
    public int getUndoCount(Player player) {
        Deque<UndoOperation> history = undoHistory.get(player.getUniqueId());
        return history == null ? 0 : history.size();
    }

    /**
     * Gets the number of redo operations available for a player.
     */
    public int getRedoCount(Player player) {
        Deque<UndoOperation> redo = redoHistory.get(player.getUniqueId());
        return redo == null ? 0 : redo.size();
    }

    /**
     * Clears all history for a specific player.
     */
    public void clearPlayer(Player player) {
        undoHistory.remove(player.getUniqueId());
        redoHistory.remove(player.getUniqueId());
    }

    /**
     * Clears all undo/redo history.
     */
    public void clearAll() {
        undoHistory.clear();
        redoHistory.clear();
    }

    /**
     * Represents a single undo/redo operation.
     */
    private static class UndoOperation {
        private final Map<Location, BlockData> blocks;

        public UndoOperation(Map<Location, BlockData> blocks) {
            this.blocks = new LinkedHashMap<>(blocks);
        }

        public Set<Location> getLocations() {
            return blocks.keySet();
        }

        public int restore() {
            int count = 0;
            for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
                Location loc = entry.getKey();
                if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                    Block block = loc.getBlock();
                    block.setBlockData(entry.getValue(), false);
                    count++;
                }
            }
            return count;
        }
    }
}
