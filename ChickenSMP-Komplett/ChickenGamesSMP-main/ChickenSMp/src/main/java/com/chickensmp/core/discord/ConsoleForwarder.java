package com.chickensmp.core.discord;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Schickt die Server-Konsole gebuendelt per HTTPS an deinen Discord-Bot (Render).
 * Der Bot postet die Zeilen dann in einen Discord-Channel.
 */
public class ConsoleForwarder {

    private static final String THREAD_NAME = "ChickenSMP-ConsoleForwarder";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*[A-Za-z]");
    private static final Pattern MC_COLOR = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");

    private static final int MAX_QUEUE = 2000;     // aeltere Zeilen werden verworfen, wenn der Bot lange nicht erreichbar ist
    private static final int MAX_PENDING = 50;     // max. Pakete, die auf einen neuen Sendeversuch warten
    private static final int MAX_LINE = 500;       // lange Zeilen werden gekuerzt
    private static final int CHUNK_SIZE = 1500;    // Zeichen pro HTTP-Request

    private final JavaPlugin plugin;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger();
    private final Deque<String> pending = new ArrayDeque<>(); // nur vom Sende-Thread benutzt

    private ScheduledExecutorService executor;
    private HttpClient http;
    private ConsoleAppender appender;
    private boolean failing = false;

    private String url;
    private String secret;
    private Level minLevel;
    private boolean redactIps;

    public ConsoleForwarder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("discord-console.enabled", false)) {
            plugin.getLogger().info("Discord-Konsole ist ausgeschaltet (discord-console.enabled: false).");
            return;
        }

        url = cfg.getString("discord-console.url", "").trim();
        secret = cfg.getString("discord-console.secret", "").trim();
        minLevel = Level.toLevel(cfg.getString("discord-console.min-level", "INFO"), Level.INFO);
        redactIps = cfg.getBoolean("discord-console.redact-ips", true);
        int interval = Math.max(1, cfg.getInt("discord-console.send-interval-seconds", 3));

        if (url.isEmpty() || secret.isEmpty() || url.contains("DEIN-BOT") || secret.startsWith("HIER-")) {
            plugin.getLogger().warning("Discord-Konsole: bitte url und secret in der config.yml eintragen.");
            return;
        }
        if (!url.startsWith("https://")) {
            plugin.getLogger().warning("Discord-Konsole: die URL nutzt kein HTTPS - das Passwort wuerde unverschluesselt gesendet!");
        }

        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, THREAD_NAME);
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::flushSafely, interval, interval, TimeUnit.SECONDS);

        appender = new ConsoleAppender();
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);

        plugin.getLogger().info("Discord-Konsole aktiv (Level ab " + minLevel.name() + ", alle " + interval + "s).");
    }

    public void stop() {
        if (appender != null) {
            ((Logger) LogManager.getRootLogger()).removeAppender(appender);
            appender.stop();
            appender = null;
        }
        if (executor != null) {
            try {
                executor.submit(this::flushSafely).get(8, TimeUnit.SECONDS); // letzte Zeilen noch senden
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
            executor = null;
        }
    }

    // ---------------------------------------------------------------

    private class ConsoleAppender extends AbstractAppender {
        ConsoleAppender() {
            super("ChickenSMPConsoleAppender", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            // Eigene Meldungen des Senders nie weiterleiten (sonst Endlosschleife bei Fehlern)
            if (THREAD_NAME.equals(Thread.currentThread().getName())) return;
            if (!event.getLevel().isMoreSpecificThan(minLevel)) return;

            String message = event.getMessage().getFormattedMessage();
            if (message == null) return;
            if (event.getThrown() != null) message += " | " + event.getThrown();

            String line = "[" + LocalTime.now().format(TIME) + " " + event.getLevel().name() + "] " + clean(message);
            if (line.length() > MAX_LINE) line = line.substring(0, MAX_LINE) + "...";

            queue.add(line);
            if (queueSize.incrementAndGet() > MAX_QUEUE) {
                if (queue.poll() != null) queueSize.decrementAndGet();
            }
        }
    }

    private String clean(String text) {
        text = ANSI.matcher(text).replaceAll("");
        text = MC_COLOR.matcher(text).replaceAll("");
        if (redactIps) text = IPV4.matcher(text).replaceAll("x.x.x.x");
        return text.replace("\r", "");
    }

    // ---------------------------------------------------------------

    private void flushSafely() {
        try {
            flush();
        } catch (Throwable t) {
            // Nichts loggen, das den Forwarder selbst wieder triggern koennte
        }
    }

    private void flush() {
        // 1) neue Zeilen in Pakete verpacken
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = queue.poll()) != null) {
            queueSize.decrementAndGet();
            if (sb.length() + line.length() + 1 > CHUNK_SIZE && sb.length() > 0) {
                addPending(sb.toString());
                sb.setLength(0);
            }
            sb.append(line).append('\n');
        }
        if (sb.length() > 0) addPending(sb.toString());

        // 2) der Reihe nach senden, bei Fehler beim naechsten Durchlauf erneut versuchen
        while (!pending.isEmpty()) {
            if (!send(pending.peekFirst())) return;
            pending.pollFirst();
        }
    }

    private void addPending(String chunk) {
        pending.addLast(chunk);
        while (pending.size() > MAX_PENDING) pending.pollFirst();
    }

    private boolean send(String text) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "Bearer " + secret)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"content\":" + jsonString(text) + "}", StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                if (failing) {
                    failing = false;
                    plugin.getLogger().info("Discord-Konsole: Verbindung zum Bot wieder hergestellt.");
                }
                return true;
            }
            reportFailure("HTTP " + code + (code == 401 || code == 403 ? " (Passwort falsch?)" : ""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            reportFailure(e.getClass().getSimpleName());
        }
        return false;
    }

    // Meldet nur den ersten Fehler, bis es wieder klappt (kein Spam)
    private void reportFailure(String reason) {
        if (failing) return;
        failing = true;
        plugin.getLogger().warning("Discord-Konsole: Senden an den Bot fehlgeschlagen (" + reason + "). Ich versuche es weiter.");
    }

    private static String jsonString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
