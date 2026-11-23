package com.chickensmp.core.gui;

import org.bukkit.ChatColor;

/**
 * Baut Menue-Titel, die das Resource Pack "ChickenGames-Pack" als Hintergrundbild nutzt.
 * Ohne Pack sieht der Spieler nur ein paar unsichtbare Zeichen vor dem Titel und das normale Menue.
 */
public final class GuiTitles {

    // Zeichen aus assets/minecraft/font/default.json des Packs
    private static final String BG_1_ROW = "\uE101";
    private static final String BG_3_ROWS = "\uE103";
    private static final String BG_6_ROWS = "\uE106";

    private static final int BG_WIDTH = 176;   // Breite des Hintergrundbilds
    private static final int TITLE_X = 8;      // hier beginnt normalerweise der Titel

    private GuiTitles() {
    }

    public static String rows1(String title) {
        return build(BG_1_ROW, title);
    }

    public static String rows3(String title) {
        return build(BG_3_ROWS, title);
    }

    public static String rows6(String title) {
        return build(BG_6_ROWS, title);
    }

    private static String build(String background, String title) {
        // Weiss, damit das Bild nicht eingefaerbt wird. Erst nach ganz links, Bild zeichnen
        // (Breite + 1 Pixel Vorschub), dann zurueck an die normale Titel-Position.
        return ChatColor.WHITE + shiftLeft(TITLE_X) + background + shiftLeft(BG_WIDTH + 1 - TITLE_X) + title;
    }

    // \uF801 = 1 Pixel nach links, \uF802 = 2, \uF804 = 4, ... \uF880 = 128
    static String shiftLeft(int pixels) {
        StringBuilder sb = new StringBuilder();
        for (int size = 128; size >= 1; size /= 2) {
            while (pixels >= size) {
                sb.append((char) (0xF800 + size));
                pixels -= size;
            }
        }
        return sb.toString();
    }
}
