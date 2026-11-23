package com.chickensmp.core.commands;

import com.chickensmp.core.ChickenSMP;
import com.chickensmp.core.manager.ChickenPermsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ChickenPermsCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "chickenperms.admin";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN) && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Dazu hast du keine Rechte (" + ADMIN + ").");
            return true;
        }
        if (args.length == 0) { help(sender); return true; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "creategroup", "create" -> {
                    require(args, 2, "/chicken creategroup <rang>");
                    sender.sendMessage(msg(ChickenPermsManager.createGroup(args[1])
                            ? "&aGruppe &e" + args[1] + " &aerstellt." : "&cGruppe ungültig oder existiert bereits."));
                }
                case "deletegroup", "delete" -> {
                    require(args, 2, "/chicken deletegroup <rang>");
                    sender.sendMessage(msg(ChickenPermsManager.deleteGroup(args[1])
                            ? "&aGruppe gelöscht." : "&cGruppe nicht gefunden oder default kann nicht gelöscht werden."));
                }
                case "list", "groups" -> sender.sendMessage(msg("&eGruppen: &f" + String.join(", ", ChickenPermsManager.getGroups())));
                case "group" -> group(sender, args);
                case "user" -> user(sender, args);
                case "parent" -> {
                    require(args, 4, "/chicken parent add <rang> <elternrang>");
                    if (!args[1].equalsIgnoreCase("add")) { sender.sendMessage(msg("&cVerwendung: /chicken parent add <rang> <elternrang>")); return true; }
                    sender.sendMessage(msg(ChickenPermsManager.addParent(args[2], args[3])
                            ? "&aVererbung hinzugefügt." : "&cUngültige Gruppe oder Vererbungsschleife."));
                }
                case "info" -> {
                    require(args, 2, "/chicken info <spieler|gruppe>");
                    String groupInfo = ChickenPermsManager.getGroupInfo(args[1]);
                    if (!groupInfo.equals("Gruppe nicht gefunden.")) sender.sendMessage(msg("&e" + groupInfo));
                    else {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                        sender.sendMessage(msg("&eSpieler: &f" + target.getName() + " &7| Gruppe: &f" + ChickenPermsManager.getUserGroup(target.getUniqueId())));
                    }
                }
                case "editor" -> sender.sendMessage(msg("&eWebeditor ist in dieser Version noch nicht enthalten. Nutze /chicken group und /chicken user."));
                case "sync" -> { ChickenPermsManager.refreshOnline(); sender.sendMessage(msg("&aOnline-Berechtigungen neu geladen.")); }
                case "reload" -> {
                    ChickenSMP.getInstance().reloadSettings();
                    sender.sendMessage(msg("&aChickenGames-Konfiguration neu geladen."));
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(msg("&c" + ex.getMessage()));
        }
        return true;
    }

    private void group(CommandSender sender, String[] a) {
        require(a, 4, "/chicken group <rang> permission set|unset <permission> | prefix|suffix|weight <wert>");
        String group = a[1];
        String action = a[2].toLowerCase(Locale.ROOT);
        if (action.equals("permission")) {
            require(a, 5, "/chicken group <rang> permission set|unset <permission>");
            boolean ok = a[3].equalsIgnoreCase("set")
                    ? ChickenPermsManager.addGroupPermission(group, a[4])
                    : a[3].equalsIgnoreCase("unset") && ChickenPermsManager.removeGroupPermission(group, a[4]);
            sender.sendMessage(msg(ok ? "&aGruppen-Permission aktualisiert." : "&cAktion fehlgeschlagen."));
        } else if (action.equals("prefix") || action.equals("suffix")) {
            String value = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
            if (action.equals("prefix")) ChickenPermsManager.setGroupPrefix(group, value);
            else ChickenPermsManager.setGroupSuffix(group, value);
            sender.sendMessage(msg("&a" + action + " gesetzt."));
        } else if (action.equals("weight")) {
            int weight = Integer.parseInt(a[3]);
            ChickenPermsManager.setGroupWeight(group, weight);
            sender.sendMessage(msg("&aGewichtung gesetzt."));
        } else throw new IllegalArgumentException("Unbekannte Gruppenaktion.");
    }

    private void user(CommandSender sender, String[] a) {
        require(a, 4, "/chicken user <spieler> parent set <rang> | permission set|unset <permission> [dauer]");
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        UUID uuid = target.getUniqueId();
        String action = a[2].toLowerCase(Locale.ROOT);
        if (action.equals("parent")) {
            require(a, 5, "/chicken user <spieler> parent set <rang>");
            if (!a[3].equalsIgnoreCase("set") || !ChickenPermsManager.setUserGroup(uuid, a[4]))
                throw new IllegalArgumentException("Gruppe nicht gefunden oder konnte nicht gesetzt werden.");
            sender.sendMessage(msg("&aRang von " + a[1] + " geändert."));
        } else if (action.equals("permission")) {
            require(a, 5, "/chicken user <spieler> permission set|unset <permission> [dauer]");
            if (a[3].equalsIgnoreCase("unset")) {
                sender.sendMessage(msg(ChickenPermsManager.removeUserPermission(uuid, a[4]) ? "&aPermission entfernt." : "&cPermission nicht gefunden."));
            } else if (a[3].equalsIgnoreCase("set")) {
                long expiry = a.length >= 6 ? System.currentTimeMillis() + parseDuration(a[5]) : 0L;
                ChickenPermsManager.setUserPermission(uuid, a[4], true, expiry);
                sender.sendMessage(msg("&aPermission gesetzt" + (expiry > 0 ? " (temporär)." : ".")));
            } else throw new IllegalArgumentException("Nutze set oder unset.");
        } else throw new IllegalArgumentException("Unbekannte Spieleraktion.");
    }

    private long parseDuration(String input) {
        try {
            long n = Long.parseLong(input.substring(0, input.length() - 1));
            return Math.multiplyExact(n, switch (input.substring(input.length() - 1).toLowerCase(Locale.ROOT)) {
                case "s" -> 1000L; case "m" -> 60_000L; case "h" -> 3_600_000L; case "d" -> 86_400_000L;
                default -> throw new IllegalArgumentException("Dauer muss z.B. 30m, 2h oder 7d sein.");
            });
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Ungültige Dauer. Beispiele: 30m, 2h, 7d."); }
    }

    private void require(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException("Verwendung: " + usage);
    }
    private String msg(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private void help(CommandSender sender) {
        sender.sendMessage(msg("&6ChickenPerms &7- Befehle"));
        sender.sendMessage(msg("&e/chicken creategroup <rang> &7| /chicken deletegroup <rang>"));
        sender.sendMessage(msg("&e/chicken group <rang> permission set|unset <permission>"));
        sender.sendMessage(msg("&e/chicken group <rang> prefix|suffix <text> &7| weight <zahl>"));
        sender.sendMessage(msg("&e/chicken parent add <rang> <elternrang>"));
        sender.sendMessage(msg("&e/chicken user <spieler> parent set <rang>"));
        sender.sendMessage(msg("&e/chicken user <spieler> permission set|unset <permission> [30m|2h|7d]"));
        sender.sendMessage(msg("&e/chicken list | info <spieler|gruppe> | sync | reload"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN) && !sender.isOp()) return Collections.emptyList();
        List<String> choices = new ArrayList<>();
        if (args.length == 1) choices.addAll(List.of("creategroup", "deletegroup", "group", "user", "parent", "info", "list", "editor", "sync", "reload"));
        else if (args.length == 2 && (args[0].equalsIgnoreCase("deletegroup") || args[0].equalsIgnoreCase("group") || args[0].equalsIgnoreCase("parent"))) choices.addAll(ChickenPermsManager.getGroups());
        else if (args.length == 2 && args[0].equalsIgnoreCase("user")) choices.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        else if (args.length == 3 && args[0].equalsIgnoreCase("group")) choices.addAll(List.of("permission", "prefix", "suffix", "weight"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("user")) choices.addAll(List.of("parent", "permission"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("parent")) choices.add("add");
        else if (args.length == 4 && args[0].equalsIgnoreCase("group") && args[2].equalsIgnoreCase("permission")) choices.addAll(List.of("set", "unset"));
        else if (args.length == 4 && args[0].equalsIgnoreCase("user") && args[2].equalsIgnoreCase("parent")) choices.add("set");
        else if (args.length == 4 && args[0].equalsIgnoreCase("user") && args[2].equalsIgnoreCase("permission")) choices.addAll(List.of("set", "unset"));
        else if (args.length == 4 && args[0].equalsIgnoreCase("parent")) choices.addAll(ChickenPermsManager.getGroups());
        else if (args.length == 5 && args[0].equalsIgnoreCase("user") && args[2].equalsIgnoreCase("parent")) choices.addAll(ChickenPermsManager.getGroups());
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().collect(Collectors.toList());
    }
}
