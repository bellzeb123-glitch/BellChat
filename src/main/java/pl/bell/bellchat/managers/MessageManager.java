package pl.bell.bellchat.managers;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bell.bellchat.BellChat;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * MessageManager v3.0 — system języków z merge.
 *
 * Wzorowany na LuckPerms/EssentialsX:
 *   1. Baza z jara (zawsze aktualna, wszystkie klucze)
 *   2. Nadpisanie wartościami z pliku na dysku (customizacje)
 *   3. Nowe klucze z jara auto-dodawane do pliku na dysku
 *
 * API:
 *   send(sender, key)              — wyślij z prefiksem + kolory
 *   send(sender, key, placeholders)— jak wyżej + podmiana {key}
 *   get(key)                       — pobierz pokolorowany tekst (& → §)
 *   getRaw(key)                    — pobierz surowy tekst (& zachowane)
 *   getPrefix()                    — prefiks pluginu
 *   color(s)                       — & → §
 */
public class MessageManager {

    private final BellChat plugin;
    private final Logger log;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(BellChat plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        reload();
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", "en").toLowerCase();
        messages = loadAndMerge(lang);
        prefix   = color(messages.getString("prefix", "&8[&6BellChat&8] "));
        log.info("[MessageManager] Language: " + lang.toUpperCase());
    }

    // ── Merge load ────────────────────────────────────────────

    private FileConfiguration loadAndMerge(String lang) {
        String fileName = "messages_" + lang + ".yml";
        File diskFile   = new File(plugin.getDataFolder(), fileName);

        // 1. Baza z jara
        FileConfiguration base = loadFromJar(fileName);
        if (base == null) {
            log.warning("[MessageManager] " + fileName + " brak w jarze, używam messages_en.yml");
            base = loadFromJar("messages_en.yml");
            if (base == null) {
                log.severe("[MessageManager] Brak messages_en.yml w jarze!");
                return new YamlConfiguration();
            }
        }

        // 2. Plik z dysku (customizacje admina)
        if (diskFile.exists()) {
            FileConfiguration disk = YamlConfiguration.loadConfiguration(diskFile);
            // 3. Merge — nadpisz bazę wartościami z dysku (dla istniejących kluczy)
            for (String key : disk.getKeys(true)) {
                if (disk.isString(key) && base.contains(key)) {
                    base.set(key, disk.getString(key));
                }
            }
        }

        // 4. Zapisz zaktualizowany plik (nowe klucze z jara trafiają na dysk)
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            base.save(diskFile);
        } catch (Exception e) {
            log.warning("[MessageManager] Nie można zapisać " + fileName + ": " + e.getMessage());
        }

        return base;
    }

    private FileConfiguration loadFromJar(String fileName) {
        try (var stream = plugin.getResource(fileName)) {
            if (stream == null) return null;
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warning("[MessageManager] Błąd ładowania " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    // ── API ───────────────────────────────────────────────────

    public void send(CommandSender sender, String key) {
        sender.sendMessage(prefix + get(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String msg = get(key);
        for (var entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        sender.sendMessage(prefix + msg);
    }

    /** Pobiera pokolorowany tekst (& → §). */
    public String get(String key) {
        String raw = messages.getString(key);
        if (raw == null) {
            log.warning("[MessageManager] Brakujący klucz: " + key);
            return "§c[missing: " + key + "]";
        }
        return color(raw);
    }

    /** Pobiera surowy tekst (& zachowane — do dalszego przetwarzania w GUI). */
    public String getRaw(String key) {
        String raw = messages.getString(key);
        if (raw == null) {
            log.warning("[MessageManager] Brakujący klucz: " + key);
            return "[missing: " + key + "]";
        }
        return raw;
    }

    public String getPrefix() { return prefix; }

    public String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
