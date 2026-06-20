package dev.ramdanolii.betterantiswear.managers;

import dev.ramdanolii.betterantiswear.BetterAntiSwear;
import dev.ramdanolii.betterantiswear.data.PlayerViolation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tracks player violations in memory and optionally persists them to disk.
 */
public class ViolationManager {

    private final BetterAntiSwear plugin;

    /** uuid → violation data */
    private final Map<String, PlayerViolation> violations = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private File dataFile;

    // ----------------------------------------------------------------

    public ViolationManager(BetterAntiSwear plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (plugin.getConfigManager().isPersistViolations()) {
            loadViolations();
        }
    }

    // ----------------------------------------------------------------
    // Core API
    // ----------------------------------------------------------------

    /**
     * Record a new violation for the given player.
     *
     * @return The updated violation object (after increment).
     */
    public PlayerViolation addViolation(String uuid, String playerName) {
        PlayerViolation pv = violations.computeIfAbsent(uuid,
                k -> new PlayerViolation(uuid, playerName));

        long resetMinutes = plugin.getConfigManager().getResetAfterMinutes();

        // Auto-reset if the window has passed
        if (pv.isExpired(resetMinutes)) {
            pv.resetViolations();
        }

        pv.incrementViolation();

        if (plugin.getConfigManager().isPersistViolations()) {
            saveViolations();
        }
        return pv;
    }

    public PlayerViolation getOrCreate(String uuid, String playerName) {
        return violations.computeIfAbsent(uuid,
                k -> new PlayerViolation(uuid, playerName));
    }

    public Optional<PlayerViolation> get(String uuid) {
        return Optional.ofNullable(violations.get(uuid));
    }

    public void reset(String uuid) {
        PlayerViolation pv = violations.get(uuid);
        if (pv != null) {
            pv.resetViolations();
            pv.unmute(); // /bas reset should be a full clean slate, including any active internal mute
        }
        if (plugin.getConfigManager().isPersistViolations()) saveViolations();
    }

    /** Remove entries whose violation window has already expired. */
    public void cleanupExpired() {
        long resetMinutes = plugin.getConfigManager().getResetAfterMinutes();
        if (resetMinutes <= 0) return;

        int removed = 0;
        Iterator<Map.Entry<String, PlayerViolation>> it = violations.entrySet().iterator();
        while (it.hasNext()) {
            PlayerViolation pv = it.next().getValue();
            if (pv.isExpired(resetMinutes) && !pv.isMuted()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("ViolationManager: cleaned up " + removed + " expired record(s).");
            if (plugin.getConfigManager().isPersistViolations()) saveViolations();
        }
    }

    public int getTotalActiveViolations() { return violations.size(); }

    public Collection<PlayerViolation> getAll() { return violations.values(); }

    // ----------------------------------------------------------------
    // Mute helpers (delegates to PlayerViolation)
    // ----------------------------------------------------------------

    public boolean isMuted(String uuid) {
        PlayerViolation pv = violations.get(uuid);
        return pv != null && pv.isMuted();
    }

    public long muteMinutesRemaining(String uuid) {
        PlayerViolation pv = violations.get(uuid);
        return pv == null ? 0 : pv.muteMinutesRemaining();
    }

    // ----------------------------------------------------------------
    // Persistence
    // ----------------------------------------------------------------

    public void saveViolations() {
        FileConfiguration data = new YamlConfiguration();
        for (PlayerViolation pv : violations.values()) {
            String base = "players." + pv.getUuid();
            data.set(base + ".name", pv.getPlayerName());
            data.set(base + ".count", pv.getCount());
            data.set(base + ".lastViolation",
                    pv.getLastViolation() != null ? pv.getLastViolation().format(DATE_FMT) : "");
            data.set(base + ".muted", pv.isMuted());
            data.set(base + ".muteExpiry",
                    pv.getMuteExpiry() != null ? pv.getMuteExpiry().format(DATE_FMT) : "");
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    private void loadViolations() {
        if (!dataFile.exists()) return;
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("players")) return;

        for (String uuid : Objects.requireNonNull(data.getConfigurationSection("players")).getKeys(false)) {
            String base = "players." + uuid;
            String name = data.getString(base + ".name", "Unknown");
            int count = data.getInt(base + ".count", 0);
            String lastStr = data.getString(base + ".lastViolation", "");
            boolean muted = data.getBoolean(base + ".muted", false);
            String muteStr = data.getString(base + ".muteExpiry", "");

            PlayerViolation pv = new PlayerViolation(uuid, name);
            pv.setCount(count);

            if (!lastStr.isEmpty()) {
                try { pv.setLastViolation(LocalDateTime.parse(lastStr, DATE_FMT)); }
                catch (Exception ignored) {}
            }

            if (muted && !muteStr.isEmpty()) {
                try {
                    LocalDateTime expiry = LocalDateTime.parse(muteStr, DATE_FMT);
                    if (expiry.isAfter(LocalDateTime.now())) {
                        pv.setMuted(true);
                        pv.setMuteExpiry(expiry);
                    }
                } catch (Exception ignored) {}
            }

            violations.put(uuid, pv);
        }

        plugin.getLogger().info("ViolationManager: loaded " + violations.size() + " player record(s) from disk.");
    }
}
