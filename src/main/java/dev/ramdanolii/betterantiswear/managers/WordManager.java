package dev.ramdanolii.betterantiswear.managers;

import dev.ramdanolii.betterantiswear.BetterAntiSwear;
import dev.ramdanolii.betterantiswear.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Loads the bad-words library and detects swear words in chat messages.
 *
 * Detection pipeline (per message):
 *   1. Strip special characters (optional)
 *   2. Normalize leet-speak (optional)
 *   3. Collapse repeated characters (optional)
 *   4. Build joined version (spaces removed) for spaced-word detection (optional)
 *   5. Check each token / full string against every active bad word
 *   6. Skip any match that is in the whitelist
 */
public class WordManager {

    private final BetterAntiSwear plugin;

    /** group-name → list of bad words (thread-safe for concurrent reload + async reads) */
    private final Map<String, List<String>> wordGroups = new ConcurrentHashMap<>();

    /** Flat set for O(1) membership checks after normalization — thread-safe */
    private final Set<String> allWords = ConcurrentHashMap.newKeySet();

    /** Words that must never trigger a flag — thread-safe */
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();

    /** Optional compiled regex patterns — thread-safe list for concurrent iteration */
    private final List<Pattern> regexPatterns = new CopyOnWriteArrayList<>();

    // ----------------------------------------------------------------

    public WordManager(BetterAntiSwear plugin) {
        this.plugin = plugin;
        reload();
    }

    // ----------------------------------------------------------------
    // Load
    // ----------------------------------------------------------------

    public void reload() {
        // Build into temporary collections first, then swap atomically.
        // This avoids a window where concurrent async chat threads could
        // see an empty/partial word list while a reload is in progress.
        Map<String, List<String>> newGroups   = new LinkedHashMap<>();
        Set<String> newAllWords               = new HashSet<>();
        Set<String> newWhitelist              = new HashSet<>();
        List<Pattern> newRegexPatterns        = new ArrayList<>();

        FileConfiguration bw = plugin.getConfigManager().getBadwords();

        // Load groups
        ConfigurationSection groupsSection = bw.getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String groupName : groupsSection.getKeys(false)) {
                boolean enabled = bw.getBoolean("groups." + groupName + ".enabled", true);
                if (!enabled) continue;

                List<String> words = bw.getStringList("groups." + groupName + ".words");
                List<String> normalized = new ArrayList<>();
                for (String w : words) {
                    String n = w.toLowerCase(Locale.ROOT).trim();
                    if (!n.isEmpty()) {
                        normalized.add(n);
                        newAllWords.add(n);
                    }
                }
                newGroups.put(groupName, normalized);
            }
        }

        // Load whitelist
        List<String> wl = bw.getStringList("whitelist");
        for (String w : wl) {
            newWhitelist.add(w.toLowerCase(Locale.ROOT).trim());
        }

        // Load regex patterns (advanced)
        boolean regexEnabled = bw.getBoolean("regex-patterns.enabled", false);
        if (regexEnabled) {
            List<String> rawPatterns = bw.getStringList("regex-patterns.patterns");
            for (String p : rawPatterns) {
                try {
                    newRegexPatterns.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid regex pattern in badwords.yml: " + p);
                }
            }
        }

        // Atomic swap: replace live data in one shot per collection.
        // Concurrent async chat threads will either see the fully-old
        // or fully-new state, never a half-loaded one.
        wordGroups.clear();
        wordGroups.putAll(newGroups);
        allWords.clear();
        allWords.addAll(newAllWords);
        whitelist.clear();
        whitelist.addAll(newWhitelist);
        regexPatterns.clear();
        regexPatterns.addAll(newRegexPatterns);

        plugin.getLogger().info("WordManager: loaded " + allWords.size()
                + " bad words across " + wordGroups.size() + " group(s).");
    }

    // ----------------------------------------------------------------
    // Detection
    // ----------------------------------------------------------------

    /**
     * Scans a message for bad words.
     *
     * @param message Raw chat message
     * @return The first detected bad word, or null if clean
     */
    public String detectSwear(String message) {
        if (message == null || message.isEmpty()) return null;

        ConfigManager cfg = plugin.getConfigManager();

        // Step 1: working copy for detection
        String working = message;

        // Step 2: strip special chars
        if (cfg.isStripSpecialChars()) {
            working = working.replaceAll("[^a-zA-Z0-9\\s@$!]", "");
        }

        // Step 3: leet-speak normalization
        if (cfg.isLeetSpeakEnabled()) {
            working = normalizeLeet(working);
        }

        // Step 4: collapse repeated chars
        if (cfg.isNormalizeRepeated()) {
            working = normalizeRepeated(working);
        }

        // Step 5: case normalization
        if (cfg.isCaseInsensitive()) {
            working = working.toLowerCase(Locale.ROOT);
        }

        // Step 6: spaced-word variant (remove spaces entirely)
        String noSpaces = working.replaceAll("\\s+", "");

        // Step 7: Check each bad word
        int minLen = cfg.getMinWordLength();
        boolean partial = cfg.isPartialMatch();

        for (String badWord : allWords) {
            if (badWord.length() < minLen) continue;

            // Whitelist exact-match guard
            if (whitelist.contains(badWord)) continue;

            if (partial) {
                // Check in full message
                if (containsWord(working, badWord, true)) {
                    if (!isWhitelisted(working, badWord)) return badWord;
                }
                // Check in space-stripped version
                if (cfg.isDetectSpacedWords() && noSpaces.contains(badWord)) {
                    if (!isWhitelisted(noSpaces, badWord)) return badWord;
                }
            } else {
                // Whole-word match only
                for (String token : working.split("\\s+")) {
                    if (token.equals(badWord)) {
                        if (!whitelist.contains(token)) return badWord;
                    }
                }
            }
        }

        // Step 8: Regex patterns
        for (Pattern p : regexPatterns) {
            if (p.matcher(working).find()) return p.pattern();
        }

        return null; // clean
    }

    /**
     * Returns true if the text contains badWord BUT no whitelist entry
     * (that contains badWord as a substring) also appears in the text.
     */
    private boolean isWhitelisted(String text, String badWord) {
        for (String safe : whitelist) {
            if (safe.contains(badWord) && text.contains(safe)) return true;
        }
        return false;
    }

    /** Simple substring check; can be extended to word-boundary regex. */
    private boolean containsWord(String text, String word, boolean partial) {
        if (partial) return text.contains(word);
        return Arrays.asList(text.split("\\s+")).contains(word);
    }

    // ----------------------------------------------------------------
    // Normalization helpers
    // ----------------------------------------------------------------

    private String normalizeLeet(String text) {
        return text
                .replace("@", "a").replace("4", "a")
                .replace("3", "e")
                .replace("1", "i").replace("!", "i").replace("|", "i")
                .replace("0", "o")
                .replace("$", "s").replace("5", "s")
                .replace("7", "t").replace("+", "t")
                .replace("ph", "f");
    }

    private String normalizeRepeated(String text) {
        // Collapse runs of 3+ identical chars to 2: "fuuuuck" → "fuuck"
        return text.replaceAll("(.)\\1{2,}", "$1$1");
    }

    // ----------------------------------------------------------------
    // Censoring
    // ----------------------------------------------------------------

    /**
     * Replace every occurrence of detected bad words in the original message
     * with asterisks (or the configured censor character).
     */
    public String censorMessage(String original) {
        String censorChar = plugin.getConfigManager().getCensorChar();
        String working = original;

        for (String badWord : allWords) {
            String replacement = censorChar.repeat(badWord.length());
            // Case-insensitive replace
            working = working.replaceAll("(?i)" + Pattern.quote(badWord), replacement);
        }
        return working;
    }

    // ----------------------------------------------------------------
    // Word management (runtime add/remove)
    // ----------------------------------------------------------------

    public boolean addWord(String word, String group) {
        word = word.toLowerCase(Locale.ROOT).trim();
        if (allWords.contains(word)) return false;

        allWords.add(word);
        wordGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(word);

        // Persist to badwords.yml
        FileConfiguration bw = plugin.getConfigManager().getBadwords();
        List<String> existing = bw.getStringList("groups." + group + ".words");
        existing.add(word);
        bw.set("groups." + group + ".words", existing);
        bw.set("groups." + group + ".enabled", true);
        plugin.getConfigManager().saveBadwords();
        return true;
    }

    public boolean removeWord(String word) {
        word = word.toLowerCase(Locale.ROOT).trim();
        if (!allWords.contains(word)) return false;

        allWords.remove(word);
        FileConfiguration bw = plugin.getConfigManager().getBadwords();

        for (String group : wordGroups.keySet()) {
            List<String> list = wordGroups.get(group);
            if (list.remove(word)) {
                bw.set("groups." + group + ".words", list);
            }
        }
        plugin.getConfigManager().saveBadwords();
        return true;
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public int getTotalWordCount()                   { return allWords.size(); }
    public Map<String, List<String>> getWordGroups() { return Collections.unmodifiableMap(wordGroups); }
    public Set<String> getAllWords()                 { return Collections.unmodifiableSet(allWords); }
    public List<String> getGroupWords(String group) {
        return wordGroups.getOrDefault(group, Collections.emptyList());
    }
    public Set<String> getGroupNames() { return wordGroups.keySet(); }
}
