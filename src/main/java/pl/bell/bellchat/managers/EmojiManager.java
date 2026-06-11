package pl.bell.bellchat.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bell.bellchat.BellChat;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * EmojiManager — zamienia shortcody (:smile:) na znaki unicode.
 *
 * Konfiguracja: plugins/BellChat/emojis.yml
 * Uprawnienie: bellchat.emoji (default: true)
 */
public class EmojiManager {

    private final BellChat plugin;
    private final Logger log;
    private final File emojiFile;

    private boolean enabled = true;
    private final Map<String, String> emojiMap = new LinkedHashMap<>();

    public EmojiManager(BellChat plugin) {
        this.plugin    = plugin;
        this.log       = plugin.getLogger();
        this.emojiFile = new File(plugin.getDataFolder(), "emojis.yml");
        load();
    }

    // ── Load ──────────────────────────────────────────────────

    public void load() {
        emojiMap.clear();

        if (!emojiFile.exists()) {
            saveDefault();
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(emojiFile);
        this.enabled = cfg.getBoolean("enabled", true);

        if (!enabled) {
            log.info("[EmojiManager] Emoji wyłączone (enabled: false w emojis.yml).");
            return;
        }

        var section = cfg.getConfigurationSection("emojis");
        if (section == null) {
            log.warning("[EmojiManager] Brak sekcji 'emojis' w emojis.yml.");
            return;
        }

        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                String normalized = key.startsWith(":") ? key : ":" + key + ":";
                emojiMap.put(normalized, value);
            }
        }

        log.info("[EmojiManager] Załadowano " + emojiMap.size() + " emoji.");
    }

    public void reload() { load(); }

    // ── Toggle z AdminGUI ─────────────────────────────────────

    /**
     * Ustawia stan enabled i zapisuje do emojis.yml.
     * Wywoływane przez AdminGUI.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(emojiFile);
            cfg.set("enabled", enabled);
            cfg.save(emojiFile);
        } catch (IOException e) {
            log.warning("[EmojiManager] Nie można zapisać emojis.yml: " + e.getMessage());
        }
    }

    // ── Przetwarzanie ─────────────────────────────────────────

    public String process(String message) {
        if (!enabled || emojiMap.isEmpty()) return message;
        for (Map.Entry<String, String> entry : emojiMap.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }

    public boolean isEnabled() { return enabled; }

    // ── Domyślny plik ─────────────────────────────────────────

    private void saveDefault() {
        try {
            if (plugin.getResource("emojis.yml") != null) {
                plugin.saveResource("emojis.yml", false);
            } else {
                generateDefault();
            }
        } catch (Exception e) {
            generateDefault();
        }
    }

    private void generateDefault() {
        try {
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("enabled", true);

            Map<String, String> defaults = new LinkedHashMap<>();
            defaults.put("smile",      "😊"); defaults.put("laugh",      "😂");
            defaults.put("wink",       "😉"); defaults.put("cool",       "😎");
            defaults.put("cry",        "😢"); defaults.put("angry",      "😡");
            defaults.put("shock",      "😱"); defaults.put("think",      "🤔");
            defaults.put("thumbsup",   "👍"); defaults.put("thumbsdown", "👎");
            defaults.put("wave",       "👋"); defaults.put("clap",       "👏");
            defaults.put("eyes",       "👀"); defaults.put("brain",      "🧠");
            defaults.put("heart",      "❤");  defaults.put("star",       "⭐");
            defaults.put("fire",       "🔥"); defaults.put("check",      "✅");
            defaults.put("x",          "❌"); defaults.put("bell",       "🔔");
            defaults.put("lightning",  "⚡"); defaults.put("snowflake",  "❄");
            defaults.put("sun",        "☀");  defaults.put("moon",       "🌙");
            defaults.put("rocket",     "🚀"); defaults.put("sword",      "⚔");
            defaults.put("shield",     "🛡");  defaults.put("crown",      "👑");
            defaults.put("gem",        "💎"); defaults.put("skull",      "💀");

            for (var e : defaults.entrySet()) cfg.set("emojis." + e.getKey(), e.getValue());
            cfg.save(emojiFile);
            log.info("[EmojiManager] Wygenerowano domyślny emojis.yml (" + defaults.size() + " emoji).");
        } catch (IOException e) {
            log.warning("[EmojiManager] Nie można zapisać emojis.yml: " + e.getMessage());
        }
    }
}
