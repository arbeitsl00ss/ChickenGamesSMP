package com.chickensmp.core.gui;

import com.chickensmp.core.ChickenSMP;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.RankManager;
import com.chickensmp.core.manager.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard im Chicken-Games-Stil. Logo und Icons kommen aus dem Resource Pack (assets/.../font/default.json).
 * Die Zeilen werden ueber Teams gesetzt und jede Sekunde aktualisiert, ohne zu flackern.
 */
public class ScoreboardManager {

    // Zeichen aus dem Pack
    private static final String LOGO = "\uE210";
    private static final String[] PACK_ICONS = {"\uE201", "\uE202", "\uE203", "\uE204", "\uE205", "\uE206", "\uE207", "\uE208", "\uE209"};
    // Ersatz ohne Pack: Geld, Kills, Tode, Rang, Ping, Spielzeit, Online, Huhn
    private static final String[] TEXT_ICONS = {"$", "\u2694", "\u2620", "\u2605", "\u26A1", "\u231A", "\u263A", "\u263A", "\u2764"};

    private static final int COIN = 0, SWORD = 1, SKULL = 2, CROWN = 3, PING = 4, CLOCK = 5, PEOPLE = 6, CHICK = 7, HEART = 8;
    private static final int LINES = 11;

    // Rahmen aus dem Pack (Breite = Logo + 5 Pixel). Er wird nur gezeigt, wenn die roten Zahlen ausgeblendet sind.
    private static final String FRAME = "\uE220";
    private static final int FRAME_WIDTH = 141;
    private static final String PAD = "\uF904";   // 4 Pixel Abstand zum Rand

    // Zarte Pastellfarben (Hex-Farben, ab 1.16)
    private static final net.md_5.bungee.api.ChatColor LABEL = net.md_5.bungee.api.ChatColor.of("#FFD9EA");
    private static final net.md_5.bungee.api.ChatColor MINT = net.md_5.bungee.api.ChatColor.of("#B5F5C8");
    private static final net.md_5.bungee.api.ChatColor BUTTER = net.md_5.bungee.api.ChatColor.of("#FFE98A");
    private static final net.md_5.bungee.api.ChatColor CORAL = net.md_5.bungee.api.ChatColor.of("#FFA3A3");
    private static final net.md_5.bungee.api.ChatColor PEACH = net.md_5.bungee.api.ChatColor.of("#FFC999");
    private static final net.md_5.bungee.api.ChatColor SKY = net.md_5.bungee.api.ChatColor.of("#A8E6FF");
    private static final net.md_5.bungee.api.ChatColor LAVENDER = net.md_5.bungee.api.ChatColor.of("#CBB8FF");
    private static final net.md_5.bungee.api.ChatColor ROSE = net.md_5.bungee.api.ChatColor.of("#FF9CC8");

    private static final Map<UUID, Scoreboard> boards = new HashMap<>();
    private static boolean numbersHidden = false;
    private static boolean numbersChecked = false;

    /** Legt das Scoreboard an oder aktualisiert es. Wird von Stats/Economy bei Aenderungen aufgerufen. */
    public static void createScoreboard(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = build(player);
            boards.put(player.getUniqueId(), board);
        }
        if (player.getScoreboard() != board) player.setScoreboard(board);
        update(player);
    }

    public static void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                createScoreboard(player);
            } catch (Exception ignored) {
            }
        }
    }

    public static void remove(Player player) {
        boards.remove(player.getUniqueId());
    }

    private static boolean packIcons() {
        return ChickenSMP.getInstance().getConfig().getBoolean("scoreboard.pack-icons", true);
    }

    private static String icon(int index) {
        return packIcons() ? ChatColor.WHITE + PACK_ICONS[index] + " " : ChatColor.GOLD + TEXT_ICONS[index] + " ";
    }

    private static Scoreboard build(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        String title = packIcons()
                ? ChatColor.WHITE + LOGO
                : ChatColor.YELLOW + "" + ChatColor.BOLD + "CHICKEN " + ChatColor.GOLD + ChatColor.BOLD + "GAMES";
        Objective obj = board.registerNewObjective("chickensmp", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        hideNumbers(obj);

        for (int i = 0; i < LINES; i++) {
            String entry = entry(i);
            Team team = board.registerNewTeam("l" + i);
            team.addEntry(entry);
            obj.getScore(entry).setScore(LINES - i);
        }
        return board;
    }

    // Blendet die roten Zahlen rechts aus (Paper 1.20.3+). Per Reflection, damit es nie einen Kompilierfehler gibt.
    private static void hideNumbers(Objective obj) {
        boolean first = !numbersChecked;
        try {
            Class<?> nf = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = nf.getMethod("blank").invoke(null);
            Objective.class.getMethod("numberFormat", nf).invoke(obj, blank);
            numbersHidden = true;
        } catch (Throwable t) {
            numbersHidden = false;
            if (first) {
                ChickenSMP.getInstance().getLogger().warning("Scoreboard: Die roten Zahlen koennen nicht ausgeblendet werden ("
                        + t.getClass().getSimpleName() + "). Server-Software: " + Bukkit.getName() + " " + Bukkit.getVersion()
                        + " - der Rahmen wird deshalb nicht angezeigt.");
            }
        }
        if (first && numbersHidden) {
            ChickenSMP.getInstance().getLogger().info("Scoreboard: Zahlen ausgeblendet, Rahmen aktiv.");
        }
        numbersChecked = true;
    }

    private static boolean frameEnabled() {
        return numbersHidden && packIcons()
                && ChickenSMP.getInstance().getConfig().getBoolean("scoreboard.frame", true);
    }

    // Unsichtbarer, eindeutiger Name je Zeile
    private static String entry(int index) {
        return ChatColor.COLOR_CHAR + Integer.toHexString(index) + ChatColor.COLOR_CHAR + "r";
    }

    private static void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        String[] lines = new String[LINES];
        lines[0] = frameEnabled()
                ? ChatColor.WHITE + GuiTitles.shiftLeft(2) + FRAME + GuiTitles.shiftLeft(FRAME_WIDTH - 1)
                : "";
        String currency = ChickenSMP.getInstance().getConfig().getString("economy.currency-symbol", "$");
        lines[1] = icon(COIN) + LABEL + "Geld " + MINT + currency + formatMoney(EconomyManager.getBalance(player));
        lines[2] = icon(SWORD) + LABEL + "Kills " + BUTTER + StatsManager.getKills(player);
        lines[3] = icon(SKULL) + LABEL + "Tode " + CORAL + StatsManager.getDeaths(player);
        lines[4] = icon(CROWN) + LABEL + "Rang " + PEACH + RankManager.getRank(player);
        lines[5] = icon(PING) + LABEL + "Ping " + SKY + player.getPing() + "ms";
        lines[6] = icon(CLOCK) + LABEL + "Spielzeit " + LAVENDER + formatTime(playTicks(player));
        lines[7] = "";
        lines[8] = icon(PEOPLE) + LABEL + "Online " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size();
        lines[9] = "";
        lines[10] = icon(CHICK) + BUTTER + "Chicken " + ROSE + "Games " + icon(HEART).trim();

        boolean frame = frameEnabled();
        for (int i = 0; i < LINES; i++) {
            Team team = board.getTeam("l" + i);
            String text = (frame && i > 0 && !lines[i].isEmpty()) ? PAD + lines[i] : lines[i];
            if (team != null) team.setPrefix(text);
        }
    }

    private static long playTicks(Player player) {
        for (String name : new String[]{"PLAY_ONE_MINUTE", "PLAY_TIME"}) {
            try {
                return player.getStatistic(Statistic.valueOf(name));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static String formatTime(long ticks) {
        long minutes = ticks / 20 / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }

    private static String formatMoney(double money) {
        if (money >= 1_000_000_000) {
            return String.format("%.2fB", money / 1_000_000_000);
        } else if (money >= 1_000_000) {
            return String.format("%.2fM", money / 1_000_000);
        } else if (money >= 1_000) {
            return String.format("%.2fk", money / 1_000);
        }
        return String.format("%.0f", money);
    }
}
