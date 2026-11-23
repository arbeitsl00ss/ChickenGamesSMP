package com.chickensmp.core.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Lightweight, standalone permission/group manager for ChickenSMP.
 * Supports group inheritance, user/group nodes, wildcard nodes, prefixes,
 * suffixes, weights and expiring user/group permission nodes.
 */
public final class ChickenPermsManager {
    private static JavaPlugin plugin;
    private static File file;
    private static YamlConfiguration data;
    private static final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    private ChickenPermsManager() {}

    public static void init(JavaPlugin instance) {
        plugin = instance;
        file = new File(plugin.getDataFolder(), "chickenperms.yml");
        if (!file.exists()) {
            data = new YamlConfiguration();
            createGroup("default");
            setGroupPrefix("default", "&7");
            setGroupWeight("default", 1);
            save();
        } else {
            data = YamlConfiguration.loadConfiguration(file);
            if (!data.isConfigurationSection("groups.default")) {
                data.createSection("groups.default");
                data.set("groups.default.weight", 1);
                data.set("groups.default.permissions", new ArrayList<String>());
                data.set("groups.default.parents", new ArrayList<String>());
                save();
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
    }

    public static void save() {
        if (data == null || file == null) return;
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("ChickenPerms konnte nicht gespeichert werden: " + ex.getMessage()); }
    }

    private static String norm(String value) { return value.toLowerCase(Locale.ROOT); }
    private static String groupPath(String group) { return "groups." + norm(group); }
    private static String userPath(UUID uuid) { return "users." + uuid; }

    public static Set<String> getGroups() {
        ConfigurationSection section = data.getConfigurationSection("groups");
        return section == null ? Collections.emptySet() : new TreeSet<>(section.getKeys(false));
    }

    public static boolean groupExists(String group) { return data.isConfigurationSection(groupPath(group)); }

    public static boolean createGroup(String group) {
        if (group == null || !group.matches("[A-Za-z0-9_-]{1,32}") || groupExists(group)) return false;
        data.createSection(groupPath(group));
        data.set(groupPath(group) + ".weight", 1);
        data.set(groupPath(group) + ".permissions", new ArrayList<String>());
        data.set(groupPath(group) + ".parents", new ArrayList<String>());
        save();
        return true;
    }

    public static boolean deleteGroup(String group) {
        if (group.equalsIgnoreCase("default") || !groupExists(group)) return false;
        data.set(groupPath(group), null);
        for (String key : data.getKeys(false)) {
            // no-op; user assignments are cleaned below
        }
        ConfigurationSection users = data.getConfigurationSection("users");
        if (users != null) for (String uuid : users.getKeys(false)) {
            String assigned = data.getString("users." + uuid + ".group", "default");
            if (assigned.equalsIgnoreCase(group)) data.set("users." + uuid + ".group", "default");
        }
        save();
        refreshOnline();
        return true;
    }

    public static String getUserGroup(UUID uuid) {
        String group = data.getString(userPath(uuid) + ".group", "default");
        return groupExists(group) ? group : "default";
    }

    public static boolean setUserGroup(UUID uuid, String group) {
        if (!groupExists(group)) return false;
        data.set(userPath(uuid) + ".group", norm(group));
        save();
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) apply(online);
        return true;
    }

    public static String getPrefix(UUID uuid) {
        String group = highestGroup(uuid);
        return color(data.getString(groupPath(group) + ".prefix", ""));
    }
    public static String getSuffix(UUID uuid) {
        String group = highestGroup(uuid);
        return color(data.getString(groupPath(group) + ".suffix", ""));
    }

    public static void setGroupPrefix(String group, String prefix) {
        if (groupExists(group)) { data.set(groupPath(group) + ".prefix", prefix); save(); refreshOnline(); }
    }
    public static void setGroupSuffix(String group, String suffix) {
        if (groupExists(group)) { data.set(groupPath(group) + ".suffix", suffix); save(); refreshOnline(); }
    }
    public static void setGroupWeight(String group, int weight) {
        if (groupExists(group)) { data.set(groupPath(group) + ".weight", weight); save(); refreshOnline(); }
    }

    public static boolean addParent(String child, String parent) {
        if (!groupExists(child) || !groupExists(parent) || child.equalsIgnoreCase(parent)) return false;
        Set<String> inherited = getParents(child);
        if (inherited.contains(norm(parent)) || reaches(parent, child, new HashSet<>())) return false;
        inherited.add(norm(parent));
        data.set(groupPath(child) + ".parents", new ArrayList<>(inherited));
        save(); refreshOnline(); return true;
    }

    private static boolean reaches(String from, String target, Set<String> seen) {
        if (!seen.add(norm(from))) return false;
        if (from.equalsIgnoreCase(target)) return true;
        for (String parent : getParents(from)) if (reaches(parent, target, seen)) return true;
        return false;
    }

    private static Set<String> getParents(String group) {
        return new LinkedHashSet<>(data.getStringList(groupPath(group) + ".parents"));
    }

    public static boolean addGroupPermission(String group, String permission) {
        if (!groupExists(group) || permission == null || permission.isBlank()) return false;
        List<String> nodes = data.getStringList(groupPath(group) + ".permissions");
        if (!nodes.contains(permission.toLowerCase(Locale.ROOT))) nodes.add(permission.toLowerCase(Locale.ROOT));
        data.set(groupPath(group) + ".permissions", nodes); save(); refreshOnline(); return true;
    }

    public static boolean removeGroupPermission(String group, String permission) {
        if (!groupExists(group)) return false;
        List<String> nodes = data.getStringList(groupPath(group) + ".permissions");
        boolean changed = nodes.removeIf(n -> n.equalsIgnoreCase(permission));
        data.set(groupPath(group) + ".permissions", nodes); save(); refreshOnline(); return changed;
    }

    public static void setUserPermission(UUID uuid, String permission, boolean value, long expiresAt) {
        String path = userPath(uuid) + ".permissions." + permission.toLowerCase(Locale.ROOT);
        data.set(path + ".value", value);
        data.set(path + ".expires", expiresAt);
        save();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) apply(player);
    }

    public static boolean removeUserPermission(UUID uuid, String permission) {
        String path = userPath(uuid) + ".permissions." + permission.toLowerCase(Locale.ROOT);
        boolean exists = data.contains(path);
        data.set(path, null); save();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) apply(player);
        return exists;
    }

    public static boolean hasPermission(UUID uuid, String permission) {
        String wanted = permission.toLowerCase(Locale.ROOT);
        Map<String, Boolean> nodes = effectiveNodes(uuid);
        if (nodes.containsKey(wanted)) return nodes.get(wanted);
        if (nodes.getOrDefault("*", false)) return true;
        String[] parts = wanted.split("\\.");
        for (int i = parts.length - 1; i >= 0; i--) {
            String wildcard = String.join(".", Arrays.copyOfRange(parts, 0, i)) + (i == 0 ? "*" : ".*");
            if (nodes.containsKey(wildcard) && nodes.get(wildcard)) return true;
        }
        return false;
    }

    private static Map<String, Boolean> effectiveNodes(UUID uuid) {
        Map<String, Boolean> result = new HashMap<>();
        collectGroupNodes(getUserGroup(uuid), result, new HashSet<>());
        ConfigurationSection perms = data.getConfigurationSection(userPath(uuid) + ".permissions");
        if (perms != null) for (String node : perms.getKeys(false)) {
            String path = userPath(uuid) + ".permissions." + node;
            long expires = data.getLong(path + ".expires", 0L);
            if (expires > 0 && expires <= System.currentTimeMillis()) {
                data.set(path, null);
            } else result.put(node.toLowerCase(Locale.ROOT), data.getBoolean(path + ".value"));
        }
        return result;
    }

    private static void collectGroupNodes(String group, Map<String, Boolean> result, Set<String> seen) {
        if (!seen.add(norm(group)) || !groupExists(group)) return;
        for (String node : data.getStringList(groupPath(group) + ".permissions")) result.put(node.toLowerCase(Locale.ROOT), true);
        for (String parent : getParents(group)) collectGroupNodes(parent, result, seen);
    }

    private static String highestGroup(UUID uuid) {
        String group = getUserGroup(uuid);
        int weight = data.getInt(groupPath(group) + ".weight", 1);
        for (String candidate : allGroupsFor(group, new HashSet<>())) {
            int candidateWeight = data.getInt(groupPath(candidate) + ".weight", 1);
            if (candidateWeight > weight) { group = candidate; weight = candidateWeight; }
        }
        return group;
    }

    private static Set<String> allGroupsFor(String group, Set<String> found) {
        if (!found.add(norm(group))) return found;
        for (String parent : getParents(group)) allGroupsFor(parent, found);
        return found;
    }

    public static void apply(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) player.removeAttachment(old);
        PermissionAttachment attachment = player.addAttachment(plugin);
        Map<String, Boolean> nodes = effectiveNodes(player.getUniqueId());
        for (Map.Entry<String, Boolean> node : nodes.entrySet()) {
            attachment.setPermission(node.getKey(), node.getValue());
        }
        // Resolve wildcard nodes against permissions currently registered by Bukkit plugins.
        for (Permission registered : Bukkit.getPluginManager().getPermissions()) {
            String node = registered.getName().toLowerCase(Locale.ROOT);
            if (!nodes.containsKey(node) && hasPermission(player.getUniqueId(), node)) {
                attachment.setPermission(node, true);
            }
        }
        attachments.put(player.getUniqueId(), attachment);
        String prefix = getPrefix(player.getUniqueId());
        String suffix = getSuffix(player.getUniqueId());
        String tab = (prefix + player.getName() + suffix);
        player.setPlayerListName(tab.length() > 64 ? tab.substring(0, 64) : tab);
    }

    public static void remove(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) player.removeAttachment(attachment);
    }

    public static void refreshOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
    }

    public static String getGroupInfo(String group) {
        if (!groupExists(group)) return "Gruppe nicht gefunden.";
        return "Gruppe: " + group + " | Gewicht: " + data.getInt(groupPath(group) + ".weight")
                + " | Prefix: " + data.getString(groupPath(group) + ".prefix", "")
                + " | Suffix: " + data.getString(groupPath(group) + ".suffix", "")
                + " | Eltern: " + String.join(", ", getParents(group))
                + " | Permissions: " + String.join(", ", data.getStringList(groupPath(group) + ".permissions"));
    }

    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
