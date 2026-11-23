package com.chickensmp.core.commands;

import com.chickensmp.core.ChickenSMP;
import com.chickensmp.core.anticheat.ChickenACManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class ChickenACCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "chickenac.admin";
    private final ChickenSMP plugin;
    private final ChickenACManager manager;

    public ChickenACCommand(ChickenSMP plugin, ChickenACManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("alerts")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(msg("&cDieser Befehl ist nur für Spieler."));
                return true;
            }
            if (!player.hasPermission("chickenac.alerts") && !player.hasPermission(ADMIN) && !player.isOp()) {
                sender.sendMessage(msg("&cDafür hast du keine Berechtigung. Benötigt: chickenac.alerts"));
                return true;
            }

            String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "on";
            switch (mode) {
                case "on", "enable", "an" -> {
                    manager.setAlertsEnabled(player, true);
                    player.sendMessage(msg("&aChickenAC-Alerts sind jetzt eingeschaltet."));
                }
                case "off", "disable", "aus" -> {
                    manager.setAlertsEnabled(player, false);
                    player.sendMessage(msg("&eChickenAC-Alerts sind jetzt ausgeschaltet."));
                }
                case "toggle" -> {
                    boolean enabled = !manager.areAlertsEnabled(player);
                    manager.setAlertsEnabled(player, enabled);
                    player.sendMessage(msg(enabled ? "&aChickenAC-Alerts eingeschaltet." : "&eChickenAC-Alerts ausgeschaltet."));
                }
                case "status" -> player.sendMessage(msg("&7Deine ChickenAC-Alerts: "
                        + (manager.areAlertsEnabled(player) ? "&aAN" : "&cAUS")));
                default -> player.sendMessage(msg("&cVerwendung: /chickenac alerts <on|off|toggle|status>"));
            }
            return true;
        }

        if (sub.equals("test")) {
            if (!sender.hasPermission("chickenac.alerts") && !isAdmin(sender)) {
                sender.sendMessage(msg("&cDafür hast du keine Berechtigung. Benötigt: chickenac.alerts"));
                return true;
            }
            int recipients = manager.sendTestAlert();
            sender.sendMessage(msg("&aChickenAC-Test ausgeführt. Ingame-Empfänger: &f" + recipients
                    + "&a. Konsolenmeldung: &f" + (plugin.getConfig().getBoolean("anticheat.alerts.console", true) ? "AN" : "AUS")
                    + "&a. Ingame-Meldungen: &f" + (plugin.getConfig().getBoolean("anticheat.alerts.in-game", true) ? "AN" : "AUS")));
            if (sender instanceof Player player && !manager.areAlertsEnabled(player)) {
                sender.sendMessage(msg("&eDeine eigenen Alerts sind ausgeschaltet. Aktiviere sie mit /chickenac alerts on."));
            }
            return true;
        }


        if (!isAdmin(sender)) {
            sender.sendMessage(msg("&cDafür hast du keine Berechtigung. (chickenac.admin)"));
            return true;
        }

        switch (sub) {
            case "status" -> {
                sender.sendMessage(msg("&8&m---------------- &bChickenAC Status &8&m----------------"));
                sender.sendMessage(msg("&7Ingame-Alerts: " + (plugin.getConfig().getBoolean("anticheat.alerts.in-game", true) ? "&aAN" : "&cAUS")));
                sender.sendMessage(msg("&7Konsolen-Alerts: " + (plugin.getConfig().getBoolean("anticheat.alerts.console", true) ? "&aAN" : "&cAUS")));
                sender.sendMessage(msg("&7Online-Teammitglieder mit aktivierten Alerts: &f" + manager.getOnlineAlertRecipientCount()));
                for (String check : List.of("fly", "speed", "reach", "autoclicker", "fastplace")) {
                    sender.sendMessage(msg("&7" + check + ": " + (manager.isCheckEnabled(check) ? "&aAN" : "&cAUS")));
                }
                sender.sendMessage(msg("&8&m------------------------------------------------"));
            }
            case "reload" -> {
                plugin.reloadSettings();
                sender.sendMessage(msg("&aChickenAC- und ChickenGames-Konfiguration neu geladen."));
            }
            case "info", "check" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("&cVerwendung: /chickenac " + sub + " <spieler>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("&cDer Spieler muss online sein."));
                    return true;
                }
                sender.sendMessage(msg("&8&m---------------- &bChickenAC &8&m----------------"));
                sender.sendMessage(msg("&7Spieler: &f" + target.getName() + " &8| &7Ping: &f" + target.getPing() + "ms"));
                sender.sendMessage(msg("&7Ausgenommen: " + (manager.isExempt(target) ? "&aJa" : "&cNein")));
                Map<String, Integer> values = manager.getViolations(target.getUniqueId());
                if (values.isEmpty()) sender.sendMessage(msg("&7Noch keine gespeicherten Auffälligkeiten."));
                else values.forEach((check, count) -> sender.sendMessage(msg("&e" + check + "&7: &f" + count + " VL")));
                sender.sendMessage(msg("&8&m------------------------------------------------"));
            }
            case "exempt", "unexempt" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("&cVerwendung: /chickenac " + sub + " <spieler>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("&cDer Spieler muss online sein."));
                    return true;
                }
                boolean value = sub.equals("exempt");
                manager.setExempt(target.getUniqueId(), value);
                sender.sendMessage(msg(value ? "&a" + target.getName() + " ist jetzt von ChickenAC ausgenommen."
                        : "&eChickenAC-Ausnahme für " + target.getName() + " entfernt."));
            }
            case "debug" -> {
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("on")) manager.setDebug(true);
                    else if (args[1].equalsIgnoreCase("off")) manager.setDebug(false);
                    else {
                        sender.sendMessage(msg("&cVerwendung: /chickenac debug <on|off>"));
                        return true;
                    }
                } else manager.setDebug(!manager.isDebug());
                sender.sendMessage(msg("&7ChickenAC-Debug: " + (manager.isDebug() ? "&aAN" : "&cAUS")));
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN) || sender.isOp();
    }

    private void help(CommandSender sender) {
        sender.sendMessage(msg("&8&m---------------- &bChickenAC &8&m----------------"));
        sender.sendMessage(msg("&e/chickenac alerts [on|off|toggle|status] &7- Eigene Alerts einstellen"));
        sender.sendMessage(msg("&e/chickenac test &7- Testmeldung an Alert-Empfänger senden"));
        if (isAdmin(sender)) {
            sender.sendMessage(msg("&e/chickenac status &7- Aktivierte Checks und Alert-Empfänger anzeigen"));
            sender.sendMessage(msg("&e/chickenac reload &7- Konfiguration neu laden"));
            sender.sendMessage(msg("&e/chickenac info <spieler> &7- Ping und Auffälligkeiten"));
            sender.sendMessage(msg("&e/chickenac check <spieler> &7- Spielerstatus anzeigen"));
            sender.sendMessage(msg("&e/chickenac exempt <spieler> &7- Spieler ausnehmen"));
            sender.sendMessage(msg("&e/chickenac unexempt <spieler> &7- Ausnahme entfernen"));
            sender.sendMessage(msg("&e/chickenac debug <on|off> &7- Debug-Ausgabe"));
        }
        sender.sendMessage(msg("&8&m------------------------------------------------"));
    }

    private String msg(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            choices.add("alerts");
            if (sender.hasPermission("chickenac.alerts") || isAdmin(sender)) choices.add("test");
            if (isAdmin(sender)) choices.addAll(List.of("help", "status", "reload", "info", "check", "exempt", "unexempt", "debug"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("alerts")
                && (sender.hasPermission("chickenac.alerts") || isAdmin(sender))) {
            choices.addAll(List.of("on", "off", "toggle", "status"));
        } else if (args.length == 2 && isAdmin(sender)
                && List.of("info", "check", "exempt", "unexempt").contains(args[0].toLowerCase(Locale.ROOT))) {
            choices.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug") && isAdmin(sender)) {
            choices.addAll(List.of("on", "off"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().collect(Collectors.toList());
    }
}
