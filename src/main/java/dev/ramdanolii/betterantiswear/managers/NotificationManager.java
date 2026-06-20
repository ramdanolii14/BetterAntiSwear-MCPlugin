package dev.ramdanolii.betterantiswear.managers;

import dev.ramdanolii.betterantiswear.BetterAntiSwear;
import dev.ramdanolii.betterantiswear.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Handles all admin notifications when a swear is detected.
 *
 * Two notification channels:
 *   1. Chat message  — appears in admin's chat feed
 *   2. Action bar    — displayed above the hotbar (NOT a title/announcement)
 *
 * Both channels are independently togglable via config.yml.
 * Admins must have betterantiswear.admin OR betterantiswear.notify permission.
 */
public class NotificationManager {

    private final BetterAntiSwear plugin;
    private static final DateTimeFormatter LOG_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ----------------------------------------------------------------

    public NotificationManager(BetterAntiSwear plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Notify all eligible admins and log the violation.
     *
     * @param player   The offending player
     * @param word     The detected bad word
     * @param count    Player's violation count after this offense
     */
    public void notifyAdmins(Player player, String word, int count) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getConfig().getBoolean("notifications.notify-admins", true)) return;

        int max = cfg.getMaxViolations();

        // Build messages
        String chatMsg = null;
        String barMsg  = null;

        if (cfg.isChatNotify()) {
            chatMsg = buildMessage(cfg.getMessage("admin.chat-notification"),
                    player.getName(), word, count, max);
        }
        if (cfg.isActionBarNotify()) {
            barMsg = buildMessage(cfg.getMessage("admin.actionbar-notification"),
                    player.getName(), word, count, max);
        }

        final String finalChat = chatMsg;
        final String finalBar  = barMsg;
        final int    duration  = cfg.getActionBarDuration();

        // Send on the main thread (Bukkit API requirement)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (!canReceiveNotification(admin)) continue;

                    if (finalChat != null) {
                        admin.sendMessage(finalChat);
                    }
                    if (finalBar != null) {
                        sendActionBar(admin, finalBar, duration);
                    }
                }
            }
        }.runTask(plugin);

        // Console log
        if (cfg.isLogConsole()) {
            plugin.getLogger().info("[VIOLATION] " + player.getName()
                    + " | word: " + word + " | count: " + count + "/" + max);
        }

        // File log
        if (cfg.isLogFile()) {
            logToFile(player.getName(), player.getUniqueId().toString(), word, count, max);
        }
    }

    // ----------------------------------------------------------------
    // Action bar helper
    // ----------------------------------------------------------------

    /**
     * Sends an action bar message to the player for the given duration.
     * Uses Paper's native Adventure API (Player#sendActionBar), which is the
     * current, properly-supported way to display above-hotbar text.
     *
     * The action bar sits above the hotbar — it is NOT a /title announcement.
     */
    private void sendActionBar(Player player, String message, int durationTicks) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);

        // Send the bar, then keep refreshing it for `durationTicks` ticks
        // (the action bar auto-clears after ~3 seconds without refresh)
        new BukkitRunnable() {
            int remaining = durationTicks;

            @Override
            public void run() {
                if (!player.isOnline() || remaining <= 0) {
                    cancel();
                    return;
                }
                player.sendActionBar(component);
                remaining -= 10; // refresh every 10 ticks
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ----------------------------------------------------------------
    // File logger
    // ----------------------------------------------------------------

    private void logToFile(String playerName, String uuid, String word, int count, int max) {
        File logFile = new File(plugin.getDataFolder(),
                plugin.getConfigManager().getLogFileName());

        String line = String.format("[%s] VIOLATION | Player: %s (%s) | Word: %s | Offense: %d/%d%n",
                LocalDateTime.now().format(LOG_FMT), playerName, uuid, word, count, max);

        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(line);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write to violations log.", e);
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private boolean canReceiveNotification(Player player) {
        return player.hasPermission("betterantiswear.admin")
                || player.hasPermission("betterantiswear.notify");
    }

    private String buildMessage(String template,
                                String playerName, String word, int count, int max) {
        return template
                .replace("{player}", playerName)
                .replace("{word}",   word)
                .replace("{count}",  String.valueOf(count))
                .replace("{max}",    String.valueOf(max));
    }
}
