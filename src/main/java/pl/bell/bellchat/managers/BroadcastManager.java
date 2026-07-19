package pl.bell.bellchat.managers;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;
import pl.bell.bellchat.BellChat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * BroadcastManager — cykliczne wiadomości broadcastowe.
 *
 * Konfiguracja w config.yml sekcja broadcasts.
 * Każdy slot ma własny scheduler. reload() NIE restartuje timerów,
 * jeśli sekcja broadcasts się nie zmieniła (Chroni przed BellLP sync
 * wołającym BellChat.reload() przy każdej zmianie grupy).
 */
public class BroadcastManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final BellChat plugin;
    private final Logger log;
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** Fingerprint sekcji broadcasts — pomija restart timerów gdy bez zmian. */
    private String lastFingerprint = "";
    /** Opcjonalny sink do BellHub (live chat). */
    private Consumer<String> hubSink;

    public BroadcastManager(BellChat plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        load(false);
    }

    public void setHubSink(Consumer<String> hubSink) {
        this.hubSink = hubSink;
    }

    // ── Load / reload ──────────────────────────────────────────

    public void load() {
        load(false);
    }

    /**
     * @param forceRestart true = zawsze anuluj i odtwórz taski (np. po ręcznej zmianie z Huba)
     */
    public void load(boolean forceRestart) {
        var cfg = plugin.getConfig();
        String fp = fingerprint(cfg);
        if (!forceRestart && fp.equals(lastFingerprint) && !tasks.isEmpty()) {
            log.fine("[BroadcastManager] Config broadcasts bez zmian — zostawiam działające timery.");
            return;
        }

        cancelAll();
        lastFingerprint = fp;

        if (!cfg.getBoolean("broadcasts.enabled", false)) {
            log.info("[BroadcastManager] Auto-broadcasts wyłączone (broadcasts.enabled=false).");
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

            int intervalSeconds = Math.max(30, slot.getInt("interval-seconds", 300));
            boolean random = slot.getBoolean("random", false);
            long intervalTicks = intervalSeconds * 20L;
            long initialDelayTicks = 40L; // 2s — pierwszy broadcast szybko po starcie

            int[] index = {0};
            List<String> slotMessages = List.copyOf(messages);
            String slotName = slotKey;

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                try {
                    if (Bukkit.getOnlinePlayers().isEmpty()) return;
                    if (slotMessages.isEmpty()) return;

                    String raw;
                    if (random) {
                        raw = slotMessages.get((int) (Math.random() * slotMessages.size()));
                    } else {
                        raw = slotMessages.get(index[0] % slotMessages.size());
                        index[0]++;
                    }
                    sendBroadcast(raw, "auto:" + slotName);
                } catch (Throwable t) {
                    log.warning("[BroadcastManager] Błąd slotu '" + slotName + "': " + t.getMessage());
                }
            }, initialDelayTicks, intervalTicks);

            tasks.add(task);
            loaded++;
            log.info("[BroadcastManager] Slot '" + slotKey + "' → co " + intervalSeconds
                    + "s (pierwszy za 2s), " + slotMessages.size() + " wiad., "
                    + (random ? "losowo" : "kolejno") + ".");
        }

        if (loaded > 0) {
            log.info("[BroadcastManager] Załadowano " + loaded + " aktywnych slotów.");
        } else {
            log.warning("[BroadcastManager] broadcasts.enabled=true, ale brak aktywnych slotów z wiadomościami.");
        }
    }

    public void reload() {
        load(false);
    }

    /** Wymusza restart timerów (toggle / edycja slotów z panelu). */
    public void reloadForced() {
        load(true);
    }

    public void cancelAll() {
        for (BukkitTask task : tasks) {
            if (!task.isCancelled()) task.cancel();
        }
        tasks.clear();
    }

    public void shutdown() {
        cancelAll();
        lastFingerprint = "";
    }

    public String statusLine() {
        return "enabled=" + isGlobalEnabled()
                + " tasks=" + tasks.size()
                + " slots=" + getSlotKeys().size()
                + " fp=" + (lastFingerprint.isEmpty() ? "-" : lastFingerprint.substring(0, Math.min(8, lastFingerprint.length())));
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
        reloadForced();
    }

    public int getIntervalSeconds(String key) {
        return plugin.getConfig().getInt("broadcasts.slots." + key + ".interval-seconds", 300);
    }

    public void setIntervalSeconds(String key, int seconds) {
        plugin.getConfig().set("broadcasts.slots." + key + ".interval-seconds", Math.max(30, seconds));
        plugin.saveConfig();
        reloadForced();
    }

    public boolean isRandom(String key) {
        return plugin.getConfig().getBoolean("broadcasts.slots." + key + ".random", false);
    }

    public void setRandom(String key, boolean random) {
        plugin.getConfig().set("broadcasts.slots." + key + ".random", random);
        plugin.saveConfig();
        reloadForced();
    }

    public List<String> getMessages(String key) {
        return new ArrayList<>(plugin.getConfig().getStringList("broadcasts.slots." + key + ".messages"));
    }

    public void addMessage(String key, String message) {
        List<String> messages = getMessages(key);
        messages.add(message);
        plugin.getConfig().set("broadcasts.slots." + key + ".messages", messages);
        plugin.saveConfig();
        reloadForced();
    }

    public void removeMessage(String key, int index) {
        List<String> messages = getMessages(key);
        if (index < 0 || index >= messages.size()) return;
        messages.remove(index);
        plugin.getConfig().set("broadcasts.slots." + key + ".messages", messages);
        plugin.saveConfig();
        reloadForced();
    }

    public void editMessage(String key, int index, String newMessage) {
        List<String> messages = getMessages(key);
        if (index < 0 || index >= messages.size()) return;
        messages.set(index, newMessage);
        plugin.getConfig().set("broadcasts.slots." + key + ".messages", messages);
        plugin.saveConfig();
        reloadForced();
    }

    public void createSlot(String key) {
        String path = "broadcasts.slots." + key;
        plugin.getConfig().set(path + ".enabled", true);
        plugin.getConfig().set(path + ".interval-seconds", 300);
        plugin.getConfig().set(path + ".random", false);
        plugin.getConfig().set(path + ".messages", List.of(
                "&8[&6Bell&8] &fEdit this message in /bch gui → Broadcasts"));
        plugin.saveConfig();
        reloadForced();
    }

    public void deleteSlot(String key) {
        plugin.getConfig().set("broadcasts.slots." + key, null);
        plugin.saveConfig();
        reloadForced();
    }

    public boolean isGlobalEnabled() {
        return plugin.getConfig().getBoolean("broadcasts.enabled", false);
    }

    public void sendTest(String key) {
        List<String> messages = getMessages(key);
        if (messages.isEmpty()) return;
        sendBroadcast(messages.get(0), "test:" + key);
    }

    private void sendBroadcast(String raw, String source) {
        Bukkit.getServer().broadcast(LEGACY.deserialize(raw));
        Consumer<String> sink = hubSink;
        if (sink != null) {
            try {
                sink.accept(raw);
            } catch (Throwable ignored) {
            }
        }
        log.fine("[BroadcastManager] Wysłano (" + source + ")");
    }

    private static String fingerprint(org.bukkit.configuration.file.FileConfiguration cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.getBoolean("broadcasts.enabled", false)).append('|');
        ConfigurationSection slots = cfg.getConfigurationSection("broadcasts.slots");
        if (slots == null) return sb.toString();
        for (String key : slots.getKeys(false)) {
            ConfigurationSection slot = slots.getConfigurationSection(key);
            if (slot == null) continue;
            sb.append(key).append(':')
                    .append(slot.getBoolean("enabled", true)).append(':')
                    .append(slot.getInt("interval-seconds", 300)).append(':')
                    .append(slot.getBoolean("random", false)).append(':')
                    .append(Objects.hash(slot.getStringList("messages").toArray())).append(';');
        }
        return sb.toString();
    }
}
