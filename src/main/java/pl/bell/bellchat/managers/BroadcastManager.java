package pl.bell.bellchat.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;
import pl.bell.bellchat.BellChat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * BroadcastManager — cykliczne wiadomości broadcastowe.
 *
 * Konfiguracja w config.yml sekcja broadcasts:
 *
 *   broadcasts:
 *     enabled: true
 *     slots:
 *       slot1:
 *         enabled: true
 *         interval-seconds: 300
 *         messages:
 *           - "&6[Bell] &fZapraszamy na nasz Discord!"
 *           - "&6[Bell] &fSprawdź /warp sklep!"
 *         random: false   # false = kolejno, true = losowo
 *
 * Każdy slot ma własny scheduler i własną listę wiadomości.
 * Wiadomości w slocie rotują kolejno (lub losowo jeśli random: true).
 */
public class BroadcastManager {

    private final BellChat plugin;
    private final Logger log;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public BroadcastManager(BellChat plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        load();
    }

    // ── Load / reload ──────────────────────────────────────────

    public void load() {
        // Anuluj istniejące taski przed przeładowaniem
        cancelAll();

        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("broadcasts.enabled", false)) {
            log.info("[BroadcastManager] Auto-broadcasts wyłączone.");
            return;
        }

        ConfigurationSection slots = cfg.getConfigurationSection("broadcasts.slots");
        if (slots == null) {
            log.warning("[BroadcastManager] Brak sekcji broadcasts.slots w config.yml.");
            return;
        }

        int loaded = 0;
        for (String slotKey : slots.getKeys(false)) {
            ConfigurationSection slot = slots.getConfigurationSection(slotKey);
            if (slot == null || !slot.getBoolean("enabled", true)) continue;

            List<String> messages = slot.getStringList("messages");
            if (messages.isEmpty()) continue;

            int intervalSeconds = slot.getInt("interval-seconds", 300);
            boolean random      = slot.getBoolean("random", false);
            long intervalTicks  = intervalSeconds * 20L;

            // Indeks rotacji dla tego slotu (tablica jednoelementowa żeby lambda mogła mutować)
            int[] index = {0};

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;

                String raw;
                if (random) {
                    raw = messages.get((int)(Math.random() * messages.size()));
                } else {
                    raw = messages.get(index[0] % messages.size());
                    index[0]++;
                }

                String colored = raw.replace("&", "§");
                Bukkit.broadcastMessage(colored);

            }, intervalTicks, intervalTicks);

            tasks.add(task);
            loaded++;
            log.info("[BroadcastManager] Slot '" + slotKey + "' → co " + intervalSeconds + "s, "
                    + messages.size() + " wiad., " + (random ? "losowo" : "kolejno") + ".");
        }

        if (loaded > 0) {
            log.info("[BroadcastManager] Załadowano " + loaded + " aktywnych slotów.");
        }
    }

    public void reload() {
        load(); // cancelAll() wywołane wewnątrz load()
    }

    public void cancelAll() {
        for (BukkitTask task : tasks) {
            if (!task.isCancelled()) task.cancel();
        }
        tasks.clear();
    }

    public void shutdown() {
        cancelAll();
    }

    // ── Admin GUI helpers ────────────────────────────────────────

    public List<String> getSlotKeys() {
        ConfigurationSection slots = plugin.getConfig().getConfigurationSection("broadcasts.slots");
        if (slots == null) return List.of();
        return new ArrayList<>(slots.getKeys(false));
    }

    public boolean hasSlot(String key) {
        return plugin.getConfig().contains("broadcasts.slots." + key);
    }

    public boolean isSlotEnabled(String key) {
        return plugin.getConfig().getBoolean("broadcasts.slots." + key + ".enabled", true);
    }

    public void setSlotEnabled(String key, boolean enabled) {
        plugin.getConfig().set("broadcasts.slots." + key + ".enabled", enabled);
        plugin.saveConfig();
        reload();
    }

    public int getIntervalSeconds(String key) {
        return plugin.getConfig().getInt("broadcasts.slots." + key + ".interval-seconds", 300);
    }

    public void setIntervalSeconds(String key, int seconds) {
        plugin.getConfig().set("broadcasts.slots." + key + ".interval-seconds", Math.max(30, seconds));
        plugin.saveConfig();
        reload();
    }

    public boolean isRandom(String key) {
        return plugin.getConfig().getBoolean("broadcasts.slots." + key + ".random", false);
    }

    public void setRandom(String key, boolean random) {
        plugin.getConfig().set("broadcasts.slots." + key + ".random", random);
        plugin.saveConfig();
        reload();
    }

    public List<String> getMessages(String key) {
        return new ArrayList<>(plugin.getConfig().getStringList("broadcasts.slots." + key + ".messages"));
    }

    public void addMessage(String key, String message) {
        List<String> messages = getMessages(key);
        messages.add(message);
        plugin.getConfig().set("broadcasts.slots." + key + ".messages", messages);
        plugin.saveConfig();
        reload();
    }

    public void removeMessage(String key, int index) {
        List<String> messages = getMessages(key);
        if (index < 0 || index >= messages.size()) return;
        messages.remove(index);
        plugin.getConfig().set("broadcasts.slots." + key + ".messages", messages);
        plugin.saveConfig();
        reload();
    }

    public void createSlot(String key) {
        String path = "broadcasts.slots." + key;
        plugin.getConfig().set(path + ".enabled", true);
        plugin.getConfig().set(path + ".interval-seconds", 300);
        plugin.getConfig().set(path + ".random", false);
        plugin.getConfig().set(path + ".messages", List.of(
                "&8[&6Bell&8] &fEdit this message in /bch gui → Broadcasts"));
        plugin.saveConfig();
        reload();
    }

    public void deleteSlot(String key) {
        plugin.getConfig().set("broadcasts.slots." + key, null);
        plugin.saveConfig();
        reload();
    }

    /** Sends one message from the slot immediately (for admin preview). */
    public void sendTest(String key) {
        List<String> messages = getMessages(key);
        if (messages.isEmpty()) return;
        String raw = messages.get(0);
        Bukkit.broadcastMessage(raw.replace("&", "§"));
    }
}
