package com.mctools.path;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Manages particle rendering for the MCT Path Tool system.
 *
 * <p>Handles two types of particles:</p>
 * <ul>
 *   <li><b>Selection particles:</b> Markers at each selected point and lines between them.</li>
 *   <li><b>Preview particles:</b> Full footprint preview of the generated structure.</li>
 * </ul>
 *
 * <p>Particles are shown only to the selecting player by default and are
 * distance-culled for performance.</p>
 */
public class PathParticleTask {

    private final com.mctools.MCTools plugin;
    private BukkitTask selectionTask;
    private BukkitTask previewTask;
    private final UUID playerId;

    // Configurable
    private Particle selectionParticle = Particle.HAPPY_VILLAGER;
    private Particle previewParticle = Particle.FLAME;
    private Particle previewSupportParticle = Particle.SOUL_FIRE_FLAME;
    private int selectionInterval = 10;
    private int previewInterval = 20;
    private double maxDistance = 128;
    private boolean visibleToAll = false;

    public PathParticleTask(com.mctools.MCTools plugin, UUID playerId) {
        this.plugin = plugin;
        this.playerId = playerId;
    }

    // ── Configuration ──

    public void setSelectionParticle(Particle particle) { this.selectionParticle = particle; }
    public void setPreviewParticle(Particle particle) { this.previewParticle = particle; }
    public void setPreviewSupportParticle(Particle particle) { this.previewSupportParticle = particle; }
    public void setSelectionInterval(int ticks) { this.selectionInterval = Math.max(1, ticks); }
    public void setPreviewInterval(int ticks) { this.previewInterval = Math.max(1, ticks); }
    public void setMaxDistance(double distance) { this.maxDistance = distance; }
    public void setVisibleToAll(boolean visibleToAll) { this.visibleToAll = visibleToAll; }

    // ── Selection particles ──

    /**
     * Starts or restarts the selection particle task.
     */
    public void startSelectionParticles(PathSession session) {
        stopSelectionParticles();

        selectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (!session.isSelectionParticles() || !session.isToolEnabled()) {
                    return;
                }

                List<Location> positions = session.getPositions();
                if (positions.isEmpty()) return;

                Location playerLoc = player.getLocation();

                // Draw point markers
                for (Location pos : positions) {
                    if (pos.getWorld() == null || !pos.getWorld().equals(playerLoc.getWorld())) continue;
                    if (pos.distanceSquared(playerLoc) > maxDistance * maxDistance) continue;

                    // Cross pattern at each point
                    spawnParticle(player, pos.clone().add(0.5, 1.2, 0.5), selectionParticle, 3);
                    spawnParticle(player, pos.clone().add(0.8, 1.0, 0.5), selectionParticle, 1);
                    spawnParticle(player, pos.clone().add(0.2, 1.0, 0.5), selectionParticle, 1);
                    spawnParticle(player, pos.clone().add(0.5, 1.0, 0.8), selectionParticle, 1);
                    spawnParticle(player, pos.clone().add(0.5, 1.0, 0.2), selectionParticle, 1);
                }

                // Draw segment lines between consecutive points
                for (int i = 0; i < positions.size() - 1; i++) {
                    Location a = positions.get(i);
                    Location b = positions.get(i + 1);
                    if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) continue;

                    drawLine(player, playerLoc, a, b, selectionParticle, 0.5);
                }
            }
        }.runTaskTimer(plugin, 0L, selectionInterval);
    }

    /**
     * Stops the selection particle task.
     */
    public void stopSelectionParticles() {
        if (selectionTask != null) {
            selectionTask.cancel();
            selectionTask = null;
        }
    }

    // ── Preview particles ──

    /**
     * Starts the preview particle task showing the sampled curve path.
     */
    public void startPreviewParticles(List<Vector> sampledPath, World world, int width) {
        stopPreviewParticles();

        previewTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location playerLoc = player.getLocation();
                if (!playerLoc.getWorld().equals(world)) return;

                int halfWidth = width / 2;

                for (int i = 0; i < sampledPath.size(); i++) {
                    Vector point = sampledPath.get(i);
                    Location loc = new Location(world, point.getX(), point.getY() + 0.5, point.getZ());

                    if (loc.distanceSquared(playerLoc) > maxDistance * maxDistance) continue;

                    // Center line particle
                    if (i % 2 == 0) {
                        spawnParticle(player, loc, previewParticle, 1);
                    }

                    // Edge particles
                    if (halfWidth > 0 && i % 3 == 0) {
                        Vector tangent = CurveEngine.getTangent(sampledPath, i);
                        Vector perp = CurveEngine.getPerpendicular(tangent);

                        Location leftEdge = new Location(world,
                                point.getX() + perp.getX() * (-halfWidth),
                                point.getY() + 0.5,
                                point.getZ() + perp.getZ() * (-halfWidth));
                        Location rightEdge = new Location(world,
                                point.getX() + perp.getX() * halfWidth,
                                point.getY() + 0.5,
                                point.getZ() + perp.getZ() * halfWidth);

                        spawnParticle(player, leftEdge, previewParticle, 1);
                        spawnParticle(player, rightEdge, previewParticle, 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, previewInterval);
    }

    /**
     * Stops the preview particle task.
     */
    public void stopPreviewParticles() {
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
    }

    // ── Cleanup ──

    /**
     * Stops all particle tasks.
     */
    public void stopAll() {
        stopSelectionParticles();
        stopPreviewParticles();
    }

    public boolean hasActivePreview() {
        return previewTask != null;
    }

    // ── Helpers ──

    /**
     * Draws a particle line between two locations.
     */
    private void drawLine(Player player, Location playerLoc, Location a, Location b,
                           Particle particle, double spacing) {
        Vector dir = b.toVector().subtract(a.toVector());
        double dist = dir.length();
        if (dist < 0.1) return;

        dir.normalize();
        int steps = (int) Math.ceil(dist / spacing);

        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            Location point = a.clone().add(
                    dir.getX() * dist * t,
                    dir.getY() * dist * t + 1.0,
                    dir.getZ() * dist * t
            );

            if (point.distanceSquared(playerLoc) > maxDistance * maxDistance) continue;
            spawnParticle(player, point, particle, 1);
        }
    }

    /**
     * Spawns particles either to a specific player or to all nearby players.
     */
    private void spawnParticle(Player player, Location location, Particle particle, int count) {
        if (visibleToAll) {
            location.getWorld().spawnParticle(particle, location, count, 0.02, 0.02, 0.02, 0);
        } else {
            player.spawnParticle(particle, location, count, 0.02, 0.02, 0.02, 0);
        }
    }
}
