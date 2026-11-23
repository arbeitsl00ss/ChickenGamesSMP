package com.chickensmp.core.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Conservative, alert-only movement and interaction checks. */
public final class ChickenACListener implements Listener {
    private final JavaPlugin plugin;
    private final ChickenACManager manager;
    private final Map<UUID, FlightSample> flightSamples = new HashMap<>();
    private final Map<UUID, Deque<Long>> swings = new HashMap<>();
    private final Map<UUID, Deque<Long>> placements = new HashMap<>();
    private final Map<String, Long> localCooldowns = new HashMap<>();
    private final Map<UUID, Long> recentVelocity = new HashMap<>();
    private final BukkitTask flightTask;

    public ChickenACListener(JavaPlugin plugin, ChickenACManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            flightSamples.put(player.getUniqueId(), new FlightSample(player.getLocation().getY()));
        }
        this.flightTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sampleFlight, 2L, 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        flightSamples.put(event.getPlayer().getUniqueId(), new FlightSample(event.getPlayer().getLocation().getY()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        flightSamples.remove(id);
        swings.remove(id);
        placements.remove(id);
        recentVelocity.remove(id);
        String cooldownPrefix = id + ":";
        localCooldowns.keySet().removeIf(key -> key.startsWith(cooldownPrefix));
        manager.clearTransientState(id);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            flightSamples.put(event.getPlayer().getUniqueId(), new FlightSample(event.getTo().getY()));
        }
        recentVelocity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1200L);
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent event) {
        recentVelocity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1200L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            recentVelocity.put(player.getUniqueId(), System.currentTimeMillis() + 900L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        if (!canMovementCheck(player) || recentlyPushed(player)) return;
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.01) return;

        double limit = manager.getDouble("anticheat.checks.speed.max-horizontal-distance-per-move", 0.95);
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            limit += Math.min(0.45, amplifier * 0.12);
        }
        if (isOnIce(from) || isOnIce(to)) limit += 0.30;

        if (horizontal > limit && manager.isCheckEnabled("speed") && allowLocalFlag(player, "speed", 900L)) {
            manager.flag(player, "speed", String.format(java.util.Locale.US, "%.2f blocks/event (Limit %.2f)", horizontal, limit));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onReach(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) return;
        if (!manager.isCheckEnabled("reach") || !canMovementCheck(attacker)) return;
        if (attacker.getWorld() != target.getWorld()) return;

        Location eye = attacker.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double closestX = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double closestY = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double closestZ = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        double distance = eye.toVector().distance(new Vector(closestX, closestY, closestZ));
        double limit = manager.getDouble("anticheat.checks.reach.max-distance", 3.55);
        if (distance > limit && allowLocalFlag(attacker, "reach", 1200L)) {
            manager.flag(attacker, "reach", String.format(java.util.Locale.US, "%.2f blocks (Limit %.2f)", distance, limit));
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (!manager.isCheckEnabled("autoclicker") || !canMovementCheck(player)) return;
        long now = System.currentTimeMillis();
        Deque<Long> window = swings.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        window.addLast(now);
        while (!window.isEmpty() && now - window.peekFirst() > 1000L) window.removeFirst();
        int max = manager.getInt("anticheat.checks.autoclicker.max-swings-per-second", 24);
        if (window.size() > max && allowLocalFlag(player, "autoclicker", 1200L)) {
            manager.flag(player, "autoclicker", window.size() + " swings in the last second");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!manager.isCheckEnabled("fastplace") || !canMovementCheck(player)) return;
        long now = System.currentTimeMillis();
        Deque<Long> window = placements.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        window.addLast(now);
        while (!window.isEmpty() && now - window.peekFirst() > 1000L) window.removeFirst();
        int max = manager.getInt("anticheat.checks.fastplace.max-blocks-per-second", 26);
        if (window.size() > max && allowLocalFlag(player, "fastplace", 1200L)) {
            manager.flag(player, "fastplace", window.size() + " block placements in the last second");
        }
    }

    private void sampleFlight() {
        if (!manager.isCheckEnabled("fly")) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            FlightSample sample = flightSamples.computeIfAbsent(id, key -> new FlightSample(player.getLocation().getY()));
            double currentY = player.getLocation().getY();
            double deltaY = currentY - sample.lastY;
            sample.lastY = currentY;

            if (!canMovementCheck(player) || recentlyPushed(player) || isFlightEnvironment(player)) {
                sample.resetAir();
                continue;
            }
            if (player.isOnGround()) {
                sample.resetAir();
                continue;
            }

            sample.airSamples++;
            if (deltaY > 0.075) sample.risingSamples++;
            else sample.risingSamples = Math.max(0, sample.risingSamples - 1);

            if (Math.abs(deltaY) < 0.025) sample.hoverSamples++;
            else sample.hoverSamples = 0;

            int risingLimit = manager.getInt("anticheat.checks.fly.max-rising-samples", 9);
            int hoverLimit = manager.getInt("anticheat.checks.fly.max-hover-samples", 24);
            if (sample.risingSamples >= risingLimit && allowLocalFlag(player, "fly", 1800L)) {
                manager.flag(player, "fly", "sustained upward movement while airborne");
                sample.risingSamples = 0;
            } else if (sample.airSamples > 30 && sample.hoverSamples >= hoverLimit && allowLocalFlag(player, "fly", 1800L)) {
                manager.flag(player, "fly", "unusually long hovering while airborne");
                sample.hoverSamples = 0;
            }
        }
    }

    private boolean canMovementCheck(Player player) {
        if (manager.isExempt(player)) return false;
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
        return !player.isFlying() && !player.getAllowFlight() && !player.isInsideVehicle() && !player.isGliding();
    }

    private boolean isFlightEnvironment(Player player) {
        if (player.isSwimming() || player.isInWater() || player.isInLava()) return true;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        Material type = player.getLocation().getBlock().getType();
        return type == Material.LADDER || type == Material.VINE || type == Material.SCAFFOLDING
                || type == Material.WEEPING_VINES || type == Material.TWISTING_VINES;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isOnIce(Location location) {
        Material type = location.getBlock().getRelative(0, -1, 0).getType();
        return type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE
                || type == Material.FROSTED_ICE;
    }

    private boolean recentlyPushed(Player player) {
        return recentVelocity.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private boolean allowLocalFlag(Player player, String check, long cooldownMillis) {
        String key = player.getUniqueId() + ":" + check;
        long now = System.currentTimeMillis();
        long previous = localCooldowns.getOrDefault(key, 0L);
        if (now - previous < cooldownMillis) return false;
        localCooldowns.put(key, now);
        return true;
    }

    public void shutdown() {
        flightTask.cancel();
    }

    private static final class FlightSample {
        private double lastY;
        private int airSamples;
        private int risingSamples;
        private int hoverSamples;

        private FlightSample(double initialY) { this.lastY = initialY; }
        private void resetAir() {
            airSamples = 0;
            risingSamples = 0;
            hoverSamples = 0;
        }
    }
}
