package pl.bell.bellchat.managers;

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
 * MessageManager v2.1 — poprawiony system języków.
 *
 * Problem ze starą wersją:
 *   saveResource(file, false) nie nadpisuje istniejącego pliku.
 *   Stary messages_en.yml z v1.0 nie miał kluczy v2.x → polskie fallbacki.
 *
 * Rozwiązanie (wzorowane na EssentialsX / LuckPerms):
 *   1. Ładuj BAZĘ z jara (zawsze aktualna, wszystkie klucze)
 *   2. Ładuj plik z dysku (customizacje admina)
 *   3. MERGE: dla każdego klucza z dysku — nadpisz bazę
 *   4. Nowe klucze których nie ma na dysku → zostają z jara
 *   5. Zapisz plik na dysk z nowymi kluczami (aktualizacja)
 *
 * Efekt: admin może customizować wiadomości, a nowe klucze
 * pojawiają się automatycznie przy każdym starcie/reloadzie.
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
    }

    // ── Merge load ────────────────────────────────────────────

    /**
     * Ładuje plik językowy przez merge:
     * baza z jara + nadpisanie wartościami z dysku.
     * Nowe klucze z jara są automatycznie dodawane do pliku na dysku.
     */
    private FileConfiguration loadAndMerge(String lang) {
        String fileName = "messages_" + lang + ".yml";
        File diskFile   = new File(plugin.getDataFolder(), fileName);

        // ── Krok 1: załaduj bazę z jara ───────────────────────
        FileConfiguration base = loadFromJar(fileName);
        if (base == null) {
            log.warning("[MessageManager] Brak " + fileName + " w jarze, próbuję messages_en.yml");
            base = loadFromJar("messages_en.yml");
            if (base == null) {
                log.severe("[MessageManager] Brak messages_en.yml w jarze!");
                return new YamlConfiguration();
            }
        }

        // ── Krok 2: załaduj plik z dysku (jeśli istnieje) ─────
        FileConfiguration disk = null;
        if (diskFile.exists()) {
            disk = YamlConfiguration.loadConfiguration(diskFile);
        }

        // ── Krok 3: merge — nakładaj wartości z dysku na bazę ─
        if (disk != null) {
            for (String key : disk.getKeys(true)) {
                if (disk.isString(key) && base.contains(key)) {
                    base.set(key, disk.getString(key));
                }
            }
        }

        // ── Krok 4: zapisz zaktualizowany plik na dysk ────────
        // Nowe klucze z jara są teraz w 'base' i zostaną zapisane
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            base.save(diskFile);
        } catch (Exception e) {
            log.warning("[MessageManager] Nie można zapisać " + fileName + ": " + e.getMessage());
        }

        log.info("[MessageManager] Załadowano język: " + lang.toUpperCase()
                + " (" + base.getKeys(false).size() + " kluczy)");
        return base;
    }

    /**
     * Ładuje plik YML bezpośrednio z jara (zawsze aktualny).
     */
    private FileConfiguration loadFromJar(String fileName) {
        try (var stream = plugin.getResource(fileName)) {
            if (stream == null) return null;
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warning("[MessageManager] Błąd ładowania " + fileName + " z jara: " + e.getMessage());
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

    public String get(String key) {
        String raw = messages.getString(key);
        if (raw == null) {
            log.warning("[MessageManager] Brakujący klucz: " + key);
            return "&c[missing: " + key + "]";
        }
        return color(raw);
    }

    public String getPrefix() { return prefix; }

    public String color(String s) {
        if (s == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
