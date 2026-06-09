package pl.bell.bellchat.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bell.bellchat.BellChat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * EmojiManager — zamienia shortcody (:smile:) na znaki unicode.
 *
 * Konfiguracja w plugins/BellChat/emojis.yml.
 * Pierwsze uruchomienie: generuje domyślny plik z 30 emojis.
 *
 * Uprawnienia:
 *   bellchat.emoji        — może używać emoji (default: true)
 *   bellchat.emoji.bypass — emoji nie są zamieniane (default: op)
 *
 * Wyłączenie: emoji.enabled: false w emojis.yml
 */
public class EmojiManager {

    private final BellChat plugin;
    private final Logger log;
    private final File emojiFile;

    private boolean enabled = true;
    // Mapa shortcode → unicode, zachowuje kolejność wstawiania
    private final Map<String, String> emojiMap = new LinkedHashMap<>();

    public EmojiManager(BellChat plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        this.emojiFile = new File(plugin.getDataFolder(), "emojis.yml");
        load();
    }

    // ── Load ──────────────────────────────────────────────────

    public void load() {
        emojiMap.clear();

        // Generuj domyślny plik jeśli nie istnieje
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
                // Klucz może być z dwukropkami lub bez — normalizujemy do :key:
                String normalized = key.startsWith(":") ? key : ":" + key + ":";
                emojiMap.put(normalized, value);
            }
        }

        log.info("[EmojiManager] Załadowano " + emojiMap.size() + " emoji.");
    }

    public void reload() {
        load();
    }

    // ── Przetwarzanie ─────────────────────────────────────────

    /**
     * Zamienia shortcody w wiadomości na unicode emoji.
     * Zwraca oryginalną wiadomość jeśli emoji wyłączone lub gracz
     * nie ma uprawnienia bellchat.emoji.
     */
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
            InputStream in = plugin.getResource("emojis.yml");
            if (in != null) {
                // Kopiuj z jara
                plugin.saveResource("emojis.yml", false);
            } else {
                // Generuj programowo
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
            cfg.set("# Dodaj własne emoji: shortcode: 'unicode'", null);

            // 30 podstawowych emoji
            Map<String, String> defaults = new LinkedHashMap<>();
            defaults.put(":smile:",       "😊");
            defaults.put(":heart:",       "❤");
            defaults.put(":thumbsup:",    "👍");
            defaults.put(":thumbsdown:",  "👎");
            defaults.put(":fire:",        "🔥");
            defaults.put(":star:",        "⭐");
            defaults.put(":check:",       "✅");
            defaults.put(":x:",           "❌");
            defaults.put(":cry:",         "😢");
            defaults.put(":laugh:",       "😂");
            defaults.put(":wink:",        "😉");
            defaults.put(":cool:",        "😎");
            defaults.put(":angry:",       "😡");
            defaults.put(":shock:",       "😱");
            defaults.put(":wave:",        "👋");
            defaults.put(":clap:",        "👏");
            defaults.put(":eyes:",        "👀");
            defaults.put(":brain:",       "🧠");
            defaults.put(":money:",       "💰");
            defaults.put(":sword:",       "⚔");
            defaults.put(":shield:",      "🛡");
            defaults.put(":crown:",       "👑");
            defaults.put(":gem:",         "💎");
            defaults.put(":bell:",        "🔔");
            defaults.put(":lightning:",   "⚡");
            defaults.put(":snowflake:",   "❄");
            defaults.put(":sun:",         "☀");
            defaults.put(":moon:",        "🌙");
            defaults.put(":rocket:",      "🚀");
            defaults.put(":skull:",       "💀");

            for (Map.Entry<String, String> e : defaults.entrySet()) {
                cfg.set("emojis." + e.getKey().replace(":", ""), e.getValue());
            }

            cfg.save(emojiFile);
            log.info("[EmojiManager] Wygenerowano domyślny emojis.yml (" + defaults.size() + " emoji).");
        } catch (IOException e) {
            log.warning("[EmojiManager] Nie można zapisać emojis.yml: " + e.getMessage());
        }
    }
}
