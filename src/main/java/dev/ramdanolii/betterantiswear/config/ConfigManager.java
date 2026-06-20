package dev.ramdanolii.betterantiswear.config;

import dev.ramdanolii.betterantiswear.BetterAntiSwear;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Loads and exposes all plugin configuration files.
 */
public class ConfigManager {

    private final BetterAntiSwear plugin;

    private FileConfiguration config;
    private FileConfiguration badwords;
    private FileConfiguration punishments;
    private FileConfiguration messages;

    private File badwordsFile;
    private File punishmentsFile;
    private File messagesFile;

    public ConfigManager(BetterAntiSwear plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Load / reload
    // ----------------------------------------------------------------

    public void loadAll() {
        loadConfig();
        loadBadwords();
        loadPunishments();
        loadMessages();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    private void loadBadwords() {
        badwordsFile = saveDefaultIfAbsent("badwords.yml");
        badwords = YamlConfiguration.loadConfiguration(badwordsFile);
    }

    private void loadPunishments() {
        punishmentsFile = saveDefaultIfAbsent("punishments.yml");
        punishments = YamlConfiguration.loadConfiguration(punishmentsFile);
    }

    private void loadMessages() {
        messagesFile = saveDefaultIfAbsent("messages.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    /** Save default resource file from JAR if it doesn't exist yet. */
    private File saveDefaultIfAbsent(String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }
        return file;
    }

    // ----------------------------------------------------------------
    // Save helpers (for runtime edits, e.g. /bas addword)
    // ----------------------------------------------------------------

    public void saveBadwords() {
        try {
            badwords.save(badwordsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save badwords.yml", e);
        }
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public FileConfiguration getConfig()       { return config; }
    public FileConfiguration getBadwords()     { return badwords; }
    public FileConfiguration getPunishments()  { return punishments; }
    public FileConfiguration getMessages()     { return messages; }

    /** Alias kept for BASCommand readability. */
    public FileConfiguration messages()        { return messages; }

    // ── Convenience: read from messages.yml with placeholder ─────────

    public String getMessage(String path) {
        String raw = messages.getString(path, "&c[BAS] Missing message: " + path);
        return colorize(raw);
    }

    public String getMessage(String path, String[][] replacements) {
        String msg = getMessage(path);
        for (String[] pair : replacements) {
            msg = msg.replace(pair[0], pair[1]);
        }
        return msg;
    }

    public String getPrefix() {
        return colorize(messages.getString("prefix", "&8[&cBAS&8] "));
    }

    // ── Convenience: read from config.yml ────────────────────────────

    public boolean isEnabled()         { return config.getBoolean("optimization.enabled", true); }
    public boolean isAsync()           { return config.getBoolean("optimization.async-processing", true); }
    public boolean isCaseInsensitive() { return config.getBoolean("detection.case-insensitive", true); }
    public boolean isLeetSpeakEnabled(){ return config.getBoolean("detection.leet-speak-detection", true); }
    public boolean isPartialMatch()    { return config.getBoolean("detection.partial-word-matching", true); }
    public boolean isNormalizeRepeated(){ return config.getBoolean("detection.normalize-repeated-chars", true); }
    public boolean isStripSpecialChars(){ return config.getBoolean("detection.strip-special-chars", true); }
    public boolean isDetectSpacedWords(){ return config.getBoolean("detection.detect-spaced-words", true); }
    public int     getMinWordLength()  { return config.getInt("detection.min-word-length", 3); }
    public boolean isChatNotify()      { return config.getBoolean("notifications.chat-notification", true); }
    public boolean isActionBarNotify() { return config.getBoolean("notifications.actionbar-notification", true); }
    public int     getActionBarDuration(){ return config.getInt("notifications.actionbar-duration", 80); }
    public boolean isLogConsole()      { return config.getBoolean("notifications.log-to-console", true); }
    public boolean isLogFile()         { return config.getBoolean("notifications.log-to-file", true); }
    public String  getLogFileName()    { return config.getString("notifications.log-file-name", "violations.log"); }
    public long    getResetAfterMinutes(){ return config.getLong("violations.reset-after-minutes", 60); }
    public boolean isPersistViolations(){ return config.getBoolean("violations.persist-across-restarts", true); }
    public int     getMaxViolations()  { return config.getInt("violations.max-violations", 5); }
    public boolean isBlockMessage()    { return config.getBoolean("chat.block-message", false); }
    public boolean isCensorInChat()    { return config.getBoolean("chat.censor-in-chat", true); }
    public String  getCensorChar()     { return config.getString("chat.censor-character", "*"); }
    public boolean isNotifyOnCensor()  { return config.getBoolean("chat.notify-player-on-censor", true); }
    public boolean isNotifyOnBlock()   { return config.getBoolean("chat.notify-player-on-block", true); }
    public boolean isShowViolationCount(){ return config.getBoolean("chat.show-violation-count", true); }

    // ── Utility ──────────────────────────────────────────────────────

    public static String colorize(String text) {
        if (text == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
