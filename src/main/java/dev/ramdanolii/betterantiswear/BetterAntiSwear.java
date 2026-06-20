package dev.ramdanolii.betterantiswear;

import dev.ramdanolii.betterantiswear.commands.BASCommand;
import dev.ramdanolii.betterantiswear.config.ConfigManager;
import dev.ramdanolii.betterantiswear.listeners.ChatListener;
import dev.ramdanolii.betterantiswear.managers.NotificationManager;
import dev.ramdanolii.betterantiswear.managers.PunishmentManager;
import dev.ramdanolii.betterantiswear.managers.ViolationManager;
import dev.ramdanolii.betterantiswear.managers.WordManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Better Anti Swear — main entry point.
 *
 *  Initialization order:
 *    1. ConfigManager  — load all YAML files
 *    2. WordManager    — parse badwords.yml, compile detection data
 *    3. ViolationManager — restore persisted violations from disk
 *    4. PunishmentManager — reads punishments.yml on demand (no pre-load needed)
 *    5. NotificationManager — stateless, just needs plugin reference
 *    6. Listeners & Commands
 *    7. Cleanup scheduler
 */
public class BetterAntiSwear extends JavaPlugin {

    private static BetterAntiSwear instance;

    private ConfigManager       configManager;
    private WordManager         wordManager;
    private ViolationManager    violationManager;
    private PunishmentManager   punishmentManager;
    private NotificationManager notificationManager;

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();

        printBanner();

        // 1. Load all config files
        configManager = new ConfigManager(this);
        configManager.loadAll();

        if (!configManager.isEnabled()) {
            getLogger().warning("BetterAntiSwear is disabled via config.yml (optimization.enabled=false). Shutting down.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Word detection engine
        wordManager = new WordManager(this);

        // 3. Violation tracker (restores persisted data)
        violationManager = new ViolationManager(this);

        // 4. Punishment executor
        punishmentManager = new PunishmentManager(this);

        // 5. Admin notification dispatcher
        notificationManager = new NotificationManager(this);

        // 6. Register event listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // 7. Register commands
        BASCommand basCmd = new BASCommand(this);
        getCommand("bas").setExecutor(basCmd);
        getCommand("bas").setTabCompleter(basCmd);

        // 8. Periodic cleanup task
        scheduleCleanup();

        getLogger().info("Successfully enabled. Loaded "
                + wordManager.getTotalWordCount() + " bad word(s) in "
                + wordManager.getGroupNames().size() + " group(s).");
    }

    @Override
    public void onDisable() {
        if (violationManager != null) {
            violationManager.saveViolations();
            getLogger().info("Violation data saved to disk.");
        }
        getLogger().info("BetterAntiSwear disabled. Goodbye!");
    }

    // ----------------------------------------------------------------
    // Scheduler
    // ----------------------------------------------------------------

    private void scheduleCleanup() {
        long intervalTicks = configManager.getConfig()
                .getLong("optimization.cleanup-interval", 30) * 60L * 20L;

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            violationManager.cleanupExpired();
            if (configManager.isPersistViolations()) {
                violationManager.saveViolations();
            }
        }, intervalTicks, intervalTicks);
    }

    // ----------------------------------------------------------------
    // Banner
    // ----------------------------------------------------------------

    private void printBanner() {
        getLogger().info("  ____       _   _              _          _   _  ____                       ");
        getLogger().info(" | __ )  ___| |_| |_ ___ _ __  / \\   _ __ | |_(_)/ ___|_      _____  __ _ _ ");
        getLogger().info(" |  _ \\ / _ \\ __| __/ _ \\ '__| / _ \\ | '_ \\| __| |\\___ \\ \\ /\\ / / _ \\/ _` | |");
        getLogger().info(" | |_) |  __/ |_| ||  __/ |   / ___ \\| | | | |_| | ___) \\ V  V /  __/ (_| | |");
        getLogger().info(" |____/ \\___|\\__|\\__\\___|_|  /_/   \\_\\_| |_|\\__|_||____/ \\_/\\_/ \\___|\\__,_|_|");
        getLogger().info("                                                        v" + getDescription().getVersion());
        getLogger().info("  by ramdanolii | ramdanolii.my.id");
        getLogger().info("─────────────────────────────────────────────────────────────────────────────");
    }

    // ----------------------------------------------------------------
    // Static accessor & getters
    // ----------------------------------------------------------------

    public static BetterAntiSwear getInstance()         { return instance; }
    public ConfigManager       getConfigManager()        { return configManager; }
    public WordManager         getWordManager()          { return wordManager; }
    public ViolationManager    getViolationManager()     { return violationManager; }
    public PunishmentManager   getPunishmentManager()    { return punishmentManager; }
    public NotificationManager getNotificationManager()  { return notificationManager; }
}
