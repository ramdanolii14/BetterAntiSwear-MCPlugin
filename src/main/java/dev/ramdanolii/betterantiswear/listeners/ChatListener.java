package dev.ramdanolii.betterantiswear.listeners;

import dev.ramdanolii.betterantiswear.BetterAntiSwear;
import dev.ramdanolii.betterantiswear.config.ConfigManager;
import dev.ramdanolii.betterantiswear.data.PlayerViolation;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Intercepts chat messages and runs the full detection → punishment pipeline.
 *
 * Uses Paper's native AsyncChatEvent (Adventure-based) instead of the legacy,
 * deprecated AsyncPlayerChatEvent. The legacy event runs through a compatibility
 * bridge that can misbehave with rapid-fire messages (e.g. spam/macro clients),
 * occasionally letting messages slip through without proper detection.
 * AsyncChatEvent is fired directly by Paper's chat pipeline and does not suffer
 * from this issue.
 */
public class ChatListener implements Listener {

    private final BetterAntiSwear plugin;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public ChatListener(BetterAntiSwear plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Chat event
    // ----------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        ConfigManager cfg = plugin.getConfigManager();

        // Master switch
        if (!cfg.isEnabled()) return;

        Player player = event.getPlayer();

        // Bypass permission
        if (player.hasPermission("betterantiswear.bypass")) return;

        // --- Internal mute check (runs before detection) ---
        String uuid = player.getUniqueId().toString();
        if (plugin.getViolationManager().isMuted(uuid)) {
            event.setCancelled(true);
            long remaining = plugin.getViolationManager().muteMinutesRemaining(uuid);
            player.sendMessage(
                    cfg.getMessage("player.muted")
                            .replace("{minutes}", String.valueOf(remaining)));
            return;
        }

        // --- Extract plain text from the Adventure component for detection ---
        String rawMessage = PLAIN.serialize(event.message());

        // --- Swear detection ---
        String detected = plugin.getWordManager().detectSwear(rawMessage);
        if (detected == null) return; // clean message

        // --- Record violation (thread-safe ConcurrentHashMap) ---
        PlayerViolation pv = plugin.getViolationManager()
                .addViolation(uuid, player.getName());
        int count = pv.getCount();
        int max   = cfg.getMaxViolations();

        // --- Decide what to do with the message ---
        if (cfg.isBlockMessage()) {
            event.setCancelled(true);
            if (cfg.isNotifyOnBlock()) {
                String msg = cfg.getMessage("player.message-blocked");
                if (cfg.isShowViolationCount()) msg += " &8(" + count + "/" + max + ")";
                player.sendMessage(ConfigManager.colorize(msg));
            }
        } else if (cfg.isCensorInChat()) {
            String censored = plugin.getWordManager().censorMessage(rawMessage);
            event.message(Component.text(censored));
            if (cfg.isNotifyOnCensor()) {
                String msg = cfg.getMessage("player.message-censored");
                if (cfg.isShowViolationCount()) msg += " &8(" + count + "/" + max + ")";
                player.sendMessage(ConfigManager.colorize(msg));
            }
        }

        // --- Notify admins (chat + action bar) ---
        plugin.getNotificationManager().notifyAdmins(player, detected, count);

        // --- Apply punishment ---
        plugin.getPunishmentManager().punish(player, pv, detected);
    }

    // ----------------------------------------------------------------
    // Quit event — placeholder for future cleanup hooks
    // ----------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nothing to clean up — ViolationManager persists data on save
    }
}
