package dev.ramdanolii.betterantiswear.commands;

import dev.ramdanolii.betterantiswear.BetterAntiSwear;
import dev.ramdanolii.betterantiswear.config.ConfigManager;
import dev.ramdanolii.betterantiswear.data.PlayerViolation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all /bas sub-commands.
 *
 * Sub-commands:
 *   /bas help                     — show help menu
 *   /bas reload                   — reload all config files
 *   /bas check <player>           — view violation count
 *   /bas reset <player>           — reset violations
 *   /bas addword <word> [group]   — add word to bad words list
 *   /bas removeword <word>        — remove word from list
 *   /bas list [group]             — list bad words
 *   /bas info                     — plugin stats
 */
public class BASCommand implements CommandExecutor, TabCompleter {

    private final BetterAntiSwear plugin;

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "help", "reload", "check", "reset",
            "addword", "removeword", "list", "info"
    );

    public BASCommand(BetterAntiSwear plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // CommandExecutor
    // ----------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        ConfigManager cfg = plugin.getConfigManager();

        if (args.length == 0) {
            sendHelp(sender, cfg);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            // ── /bas help ──────────────────────────────────────────
            case "help" -> sendHelp(sender, cfg);

            // ── /bas info ──────────────────────────────────────────
            case "info" -> {
                if (!sender.hasPermission("betterantiswear.info")) {
                    sender.sendMessage(cfg.getMessage("commands.no-permission")); return true;
                }
                sendInfo(sender, cfg);
            }

            // ── /bas reload ────────────────────────────────────────
            case "reload" -> {
                if (!sender.hasPermission("betterantiswear.reload")) {
                    sender.sendMessage(cfg.getMessage("commands.no-permission")); return true;
                }
                try {
                    cfg.loadAll();
                    plugin.getWordManager().reload();
                    sender.sendMessage(cfg.getMessage("commands.reload-success"));
                } catch (Exception e) {
                    sender.sendMessage(cfg.getMessage("commands.reload-fail"));
                    plugin.getLogger().severe("Reload failed: " + e.getMessage());
                }
            }

            // ── /bas check <player> ────────────────────────────────
            case "check" -> {
                if (!sender.hasPermission("betterantiswear.check")) {
                    sender.sendMessage(cfg.getMessage("commands.no-permission")); return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.getMessage("commands.usage-check")); return true;
                }
                String targetName = args[1];
                Player target = Bukkit.getPlayerExact(targetName);
                String uuid = target != null
                        ? target.getUniqueId().toString()
                        : findUUIDByName(targetName);

                if (uuid == null) {
                    sender.sendMessage(cfg.getMessage("commands.player-not-found")
                            .replace("{player}", targetName));
                    return true;
                }

                Optional<PlayerViolation> pvOpt = plugin.getViolationManager().get(uuid);
                if (pvOpt.isEmpty() || pvOpt.get().getCount() == 0) {
                    sender.sendMessage(cfg.getMessage("commands.check-none")
                            .replace("{player}", targetName));
                } else {
                    PlayerViolation pv = pvOpt.get();
                    long resetIn = pv.minutesUntilReset(cfg.getResetAfterMinutes());
                    sender.sendMessage(cfg.getMessage("commands.check-result")
                            .replace("{player}",   pv.getPlayerName())
                            .replace("{count}",    String.valueOf(pv.getCount()))
                            .replace("{reset_in}", resetIn < 0 ? "never" : String.valueOf(resetIn)));
                }
            }

            // ── /bas reset <player> ────────────────────────────────
            case "reset" -> {
                if (!sender.hasPermission("betterantiswear.reset")) {
                    sender.sendMessage(cfg.getMessage("commands.no-permission")); return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.getMessage("commands.usage-reset")); return true;
                }
                String targetName = args[1];
                Player target = Bukkit.getPlayerExact(targetName);
                String uuid = target != null
                        ? target.getUniqueId().toString()
                        : findUUIDByName(targetName);

                if (uuid == null) {
                    sender.sendMessage(cfg.getMessage("commands.player-not-found")
                            .replace("{player}", targetName));
                    return true;
                }

                plugin.getViolationManager().reset(uuid);
                sender.sendMessage(cfg.getMessage("commands.reset-success")
                        .replace("{player}", targetName));
            }

            // ── /bas addword <word> [group] ────────────────────────
            case "addword" -> {
                if (!sender.hasPermission("betterantiswear.addword")) {
                    sender.sendMessage(cfg.getMessage("commands.no-permission")); return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.getMessage("commands.usage-addword")); return true;
                }
                String word  = args[1].toLowerCase(Locale.ROOT);
                String group = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "custom";

                if (plugin.getWordManager().addWord(word, group)) {
                    sender.sendMessage(cfg.getMessage("commands.word-added")
                            .replace("{word}", word).replace("{group}", group));
                } else {
                    sender.sendMessage(cfg.getMessage("commands.word-exists")
                            .replace("{word}", word));
                }
            }

            // ── /bas removeword <word> ─────────────────────────────
            case "removeword" -> {
                if (!sender.hasPermission("betterantiswear.removeword")) {
                    sender.sendMessage(cfg.getMessage("commands.no-permission")); return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.getMessage("commands.usage-removeword")); return true;
                }
                String word = args[1].toLowerCase(Locale.ROOT);
                if (plugin.getWordManager().removeWord(word)) {
                    sender.sendMessage(cfg.getMessage("commands.word-removed")
                            .replace("{word}", word));
                } else {
                    sender.sendMessage(cfg.getMessage("commands.word-not-found")
                            .replace("{word}", word));
                }
            }

            // ── /bas list [group] ──────────────────────────────────
            case "list" -> {
                if (!sender.hasPermission("betterantiswear.listwords")) {
                    sender.sendMessage(cfg.getMessage("commands.no-permission")); return true;
                }
                String group = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";

                sender.sendMessage(cfg.getMessage("commands.list-header")
                        .replace("{group}", group));

                if (group.equals("all")) {
                    plugin.getWordManager().getWordGroups().forEach((grp, words) -> {
                        sender.sendMessage(ConfigManager.colorize("&7&l  [" + grp + "]"));
                        words.forEach(w -> sender.sendMessage(
                                cfg.getMessage("commands.list-entry").replace("{word}", w)));
                    });
                } else {
                    List<String> words = plugin.getWordManager().getGroupWords(group);
                    if (words.isEmpty()) {
                        sender.sendMessage(cfg.getMessage("commands.list-empty")
                                .replace("{group}", group));
                    } else {
                        words.forEach(w -> sender.sendMessage(
                                cfg.getMessage("commands.list-entry").replace("{word}", w)));
                    }
                }
                sender.sendMessage(cfg.getMessage("commands.list-footer"));
            }

            default -> sender.sendMessage(cfg.getMessage("commands.unknown-command"));
        }

        return true;
    }

    // ----------------------------------------------------------------
    // TabCompleter
    // ----------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "check", "reset" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "removeword" -> {
                    return plugin.getWordManager().getAllWords().stream()
                            .filter(w -> w.startsWith(args[1].toLowerCase()))
                            .sorted()
                            .collect(Collectors.toList());
                }
                case "list" -> {
                    List<String> groups = new ArrayList<>(plugin.getWordManager().getGroupNames());
                    groups.add("all");
                    return filterStartsWith(groups, args[1]);
                }
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addword")) {
            return filterStartsWith(new ArrayList<>(plugin.getWordManager().getGroupNames()), args[2]);
        }
        return Collections.emptyList();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void sendHelp(CommandSender sender, ConfigManager cfg) {
        sender.sendMessage(cfg.getMessage("help.header"));
        List<String> entries = cfg.getMessages().getStringList("help.entries");
        for (String entry : entries) {
            sender.sendMessage(ConfigManager.colorize(entry));
        }
        sender.sendMessage(cfg.getMessage("help.footer"));
    }

    private void sendInfo(CommandSender sender, ConfigManager cfg) {
        List<String> lines = cfg.getMessages().getStringList("commands.info");
        for (String line : lines) {
            line = line
                    .replace("{version}",         plugin.getDescription().getVersion())
                    .replace("{status}",          cfg.isEnabled() ? "&aEnabled" : "&cDisabled")
                    .replace("{word_count}",       String.valueOf(plugin.getWordManager().getTotalWordCount()))
                    .replace("{violation_count}",  String.valueOf(plugin.getViolationManager().getTotalActiveViolations()))
                    .replace("{async}",            cfg.isAsync() ? "&aYes" : "&cNo")
                    .replace("{leet}",             cfg.isLeetSpeakEnabled() ? "&aYes" : "&cNo");
            sender.sendMessage(ConfigManager.colorize(line));
        }
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    /** Find UUID from saved violation data (for offline player lookup). */
    private String findUUIDByName(String name) {
        return plugin.getViolationManager().getAll().stream()
                .filter(pv -> pv.getPlayerName().equalsIgnoreCase(name))
                .map(PlayerViolation::getUuid)
                .findFirst()
                .orElse(null);
    }
}
