package com.chickensmp.core.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state and persistence for ChickenAC.
 * This first release reports suspicious behaviour only; it does not auto-ban.
 */
public final class ChickenACManager {
    private final JavaPlugin plugin;
    private final File dataFile;
    private final YamlConfiguration data;
    private final Map<UUID, Map<String, Integer>> violations = new HashMap<>();
    private final Set<UUID> exempt = new HashSet<>();
    private final Set<UUID> alertsDisabled = new HashSet<>();
    private final Map<String, Long> alertCooldowns = new HashMap<>();
    private boolean debug;

    public ChickenACManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "chickenac-data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    private void loadData() {
        for (String value : data.getStringList("exempt")) {
            try { exempt.add(UUID.fromString(value)); }
            catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Ungültige UUID in chickenac-data.yml: " + value); }
        }
        ConfigurationSection section = data.getConfigurationSection("violations");
        if (section == null) return;
        for (String uuidText : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                ConfigurationSection checks = section.getConfigurationSection(uuidText);
                if (checks == null) continue;
                Map<String, Integer> values = new HashMap<>();
                for (String check : checks.getKeys(false)) {
                    values.put(check.toLowerCase(Locale.ROOT), Math.max(0, checks.getInt(check)));
                }
                violations.put(uuid, values);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungültige Spieler-UUID in chickenac-data.yml: " + uuidText);
            }
        }
    }

    public boolean isCheckEnabled(String check) {
        return plugin.getConfig().getBoolean("anticheat.checks." + check.toLowerCase(Locale.ROOT) + ".enabled", false);
    }

    public double getDouble(String path, double fallback) { return plugin.getConfig().getDouble(path, fallback); }
    public int getInt(String path, int fallback) { return plugin.getConfig().getInt(path, fallback); }
    public boolean getBoolean(String path, boolean fallback) { return plugin.getConfig().getBoolean(path, fallback); }

    public boolean isExempt(Player player) {
        return exempt.contains(player.getUniqueId()) || player.hasPermission("chickenac.bypass") || player.isOp();
    }

    public void setExempt(UUID uuid, boolean value) {
        if (value) exempt.add(uuid); else exempt.remove(uuid);
        save();
    }

    public boolean isExempt(UUID uuid) { return exempt.contains(uuid); }

    public void setAlertsEnabled(Player player, boolean enabled) {
        if (enabled) alertsDisabled.remove(player.getUniqueId());
        else alertsDisabled.add(player.getUniqueId());
    }

    public boolean areAlertsEnabled(Player player) { return !alertsDisabled.contains(player.getUniqueId()); }

    /** Sends a clearly labelled test message through the same in-game alert audience. */
    public int sendTestAlert() {
        String rendered = color("&8[&bChickenAC&8] &aTEST &7- Alert-System funktioniert. Das ist kein Cheat-Verdacht.");
        int recipients = 0;
        if (getBoolean("anticheat.alerts.in-game", true)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                boolean mayReceive = staff.hasPermission("chickenac.alerts")
                        || staff.hasPermission("chickenac.admin") || staff.isOp();
                if (mayReceive && !alertsDisabled.contains(staff.getUniqueId())) {
                    staff.sendMessage(rendered);
                    recipients++;
                }
            }
        }
        if (getBoolean("anticheat.alerts.console", true)) {
            plugin.getLogger().info("[ChickenAC] TEST - Alert-System funktioniert. Das ist kein Cheat-Verdacht.");
        }
        return recipients;
    }

    public int getOnlineAlertRecipientCount() {
        int recipients = 0;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!alertsDisabled.contains(staff.getUniqueId())
                    && (staff.hasPermission("chickenac.alerts")
                    || staff.hasPermission("chickenac.admin") || staff.isOp())) {
                recipients++;
            }
        }
        return recipients;
    }

    public void setDebug(boolean enabled) { this.debug = enabled; }
    public boolean isDebug() { return debug; }

    public void clearTransientState(UUID uuid) {
        String prefix = uuid + ":";
        alertCooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public int getViolationCount(UUID uuid, String check) {
        return violations.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(check.toLowerCase(Locale.ROOT), 0);
    }

    public Map<String, Integer> getViolations(UUID uuid) {
        Map<String, Integer> stored = violations.get(uuid);
        return stored == null ? Collections.emptyMap() : Collections.unmodifiableMap(stored);
    }

    public void flag(Player player, String check, String details) {
        String normalized = check.toLowerCase(Locale.ROOT);
        if (!isCheckEnabled(normalized) || isExempt(player)) return;

        Map<String, Integer> playerViolations = violations.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        int count = playerViolations.merge(normalized, 1, Integer::sum);
        String message = "&8[&bChickenAC&8] &c" + player.getName() + " &7Check: &e" + normalized
                + " &8| &7VL: &c" + count + " &8| &7" + details;

        if (debug) plugin.getLogger().info(ChatColor.stripColor(color(message)));

        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + normalized;
        long cooldown = Math.max(0L, getInt("anticheat.alerts.cooldown-seconds", 4)) * 1000L;
        long previous = alertCooldowns.getOrDefault(key, 0L);
        if (now - previous >= cooldown) {
            alertCooldowns.put(key, now);
            String rendered = color(message);
            if (getBoolean("anticheat.alerts.console", true)) {
                plugin.getLogger().warning(ChatColor.stripColor(rendered));
            }
            if (getBoolean("anticheat.alerts.in-game", true)) {
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (!alertsDisabled.contains(staff.getUniqueId())
                            && (staff.hasPermission("chickenac.alerts") || staff.hasPermission("chickenac.admin") || staff.isOp())) {
                        staff.sendMessage(rendered);
                    }
                }
            }
        }
    }

    public void save() {
        data.set("exempt", exempt.stream().map(UUID::toString).sorted().toList());
        data.set("violations", null);
        for (Map.Entry<UUID, Map<String, Integer>> entry : violations.entrySet()) {
            for (Map.Entry<String, Integer> check : entry.getValue().entrySet()) {
                data.set("violations." + entry.getKey() + "." + check.getKey(), check.getValue());
            }
        }
        try { data.save(dataFile); }
        catch (IOException exception) {
            plugin.getLogger().severe("ChickenAC-Daten konnten nicht gespeichert werden: " + exception.getMessage());
        }
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
