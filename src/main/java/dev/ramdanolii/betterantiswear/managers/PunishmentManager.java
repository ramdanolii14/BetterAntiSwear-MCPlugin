package dev.ramdanolii.betterantiswear.managers;

import dev.ramdanolii.betterantiswear.BetterAntiSwear;
import dev.ramdanolii.betterantiswear.config.ConfigManager;
import dev.ramdanolii.betterantiswear.data.PlayerViolation;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Reads punishment entries from punishments.yml and executes the correct
 * one for a player's current violation count.
 *
 * Supported action types in punishments.yml:
 *   player-message         — private message to the offender
 *   kick + kick-reason     — disconnect the player with a reason
 *   internal-mute-minutes  — BAS built-in mute (no external plugin needed)
 *   console-commands       — run as console; works with EssentialsX, LiteBans,
 *                            AdvancedBan, CMI, or any command-based plugin
 *   broadcast + broadcast-message — server-wide announcement
 */
public class PunishmentManager {

    private final BetterAntiSwear plugin;

    public PunishmentManager(BetterAntiSwear plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Core
    // ----------------------------------------------------------------

    /**
     * Execute the punishment that corresponds to the player's violation count.
     * All Bukkit API calls are dispatched on the main thread automatically.
     *
     * @param player  Offending player
     * @param pv      Their updated violation record
     * @param word    Detected bad word (for placeholder substitution)
     */
    public void punish(Player player, PlayerViolation pv, String word) {
        FileConfiguration punishments = plugin.getConfigManager().getPunishments();
        int max = plugin.getConfigManager().getMaxViolations();
        int count = pv.getCount();

        // Pick the matching punishment section
        String key = count <= max ? String.valueOf(count) : "default";
        ConfigurationSection section = punishments.getConfigurationSection("punishments." + key);
        if (section == null) {
            plugin.getLogger().warning("PunishmentManager: no entry for key '" + key + "' in punishments.yml");
            return;
        }

        // Run on main thread — required for Bukkit player API calls
        new BukkitRunnable() {
            @Override
            public void run() {
                applySection(player, pv, section, word, count, max);
            }
        }.runTask(plugin);
    }

    // ----------------------------------------------------------------
    // Apply a single punishment section
    // ----------------------------------------------------------------

    private void applySection(Player player, PlayerViolation pv,
                               ConfigurationSection section,
                               String word, int count, int max) {

        String serverName = Bukkit.getServer().getName();

        // ── Player message ────────────────────────────────────────────
        String playerMsg = section.getString("player-message", "");
        if (!playerMsg.isEmpty() && player.isOnline()) {
            player.sendMessage(ConfigManager.colorize(
                resolve(playerMsg, player.getName(), word, count, max, serverName)
            ));
        }

        // ── Internal mute ─────────────────────────────────────────────
        int muteMins = section.getInt("internal-mute-minutes", 0);
        if (muteMins > 0) {
            pv.mute(muteMins);
            plugin.getViolationManager(); // ensure reference is alive
            if (plugin.getConfigManager().isPersistViolations()) {
                plugin.getViolationManager().saveViolations();
            }
        }

        // ── Kick ──────────────────────────────────────────────────────
        boolean doKick = section.getBoolean("kick", false);
        if (doKick && player.isOnline()) {
            String kickReason = section.getString("kick-reason", "&c[BetterAntiSwear] Kicked for inappropriate language.");
            kickReason = resolve(kickReason, player.getName(), word, count, max, serverName);
            player.kickPlayer(ConfigManager.colorize(kickReason));
        }

        // ── Console commands ──────────────────────────────────────────
        List<String> commands = section.getStringList("console-commands");
        for (String cmd : commands) {
            if (cmd.isBlank()) continue;
            String resolved = resolve(cmd, player.getName(), word, count, max, serverName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        // ── Broadcast ─────────────────────────────────────────────────
        boolean broadcast = section.getBoolean("broadcast", false);
        if (broadcast) {
            String broadcastMsg = section.getString("broadcast-message", "");
            if (!broadcastMsg.isEmpty()) {
                String resolved = resolve(broadcastMsg, player.getName(), word, count, max, serverName);
                Bukkit.broadcastMessage(ConfigManager.colorize(resolved));
            }
        }

        // ── Console log ───────────────────────────────────────────────
        if (plugin.getConfigManager().isLogConsole()) {
            plugin.getLogger().info("[PUNISHMENT] Applied punishment '"
                    + section.getName() + "' to " + player.getName()
                    + " (violation #" + count + "/" + max + ")");
        }
    }

    // ----------------------------------------------------------------
    // Placeholder resolution
    // ----------------------------------------------------------------

    private String resolve(String template, String playerName, String word,
                            int count, int max, String serverName) {
        return template
                .replace("{player}", playerName)
                .replace("{word}",   word)
                .replace("{count}",  String.valueOf(count))
                .replace("{max}",    String.valueOf(max))
                .replace("{server}", serverName);
    }
}
