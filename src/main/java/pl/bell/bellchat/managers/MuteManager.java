package pl.bell.bellchat.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.model.MuteEntry;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MuteManager {

    private final BellChat plugin;
    private File muteFile;
    private final Map<UUID, MuteEntry> mutes = new HashMap<>();

    public MuteManager(BellChat plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        muteFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (!muteFile.exists()) {
            try { plugin.getDataFolder().mkdirs(); muteFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Cannot create mutes.yml"); }
        }
        reload();
    }

    public void reload() {
        mutes.clear();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(muteFile);
        if (!cfg.isConfigurationSection("mutes")) return;
        for (String uuidStr : cfg.getConfigurationSection("mutes").getKeys(false)) {
            try {
                UUID uuid      = UUID.fromString(uuidStr);
                String name    = cfg.getString("mutes." + uuidStr + ".name", "Unknown");
                long mutedAt   = cfg.getLong("mutes." + uuidStr + ".mutedAt");
                long expiresAt = cfg.getLong("mutes." + uuidStr + ".expiresAt");
                String reason  = cfg.getString("mutes." + uuidStr + ".reason", "No reason");
                String by      = cfg.getString("mutes." + uuidStr + ".mutedBy", "Console");
                MuteEntry entry = new MuteEntry(uuid, name, mutedAt, expiresAt, reason, by);
                if (!entry.isExpired()) mutes.put(uuid, entry);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveAll() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, MuteEntry> e : mutes.entrySet()) {
            if (e.getValue().isExpired()) continue;
            String path = "mutes." + e.getKey();
            cfg.set(path + ".name",      e.getValue().getPlayerName());
            cfg.set(path + ".mutedAt",   e.getValue().getMutedAt());
            cfg.set(path + ".expiresAt", e.getValue().getExpiresAt());
            cfg.set(path + ".reason",    e.getValue().getReason());
            cfg.set(path + ".mutedBy",   e.getValue().getMutedBy());
        }
        try { cfg.save(muteFile); } catch (IOException e) {
            plugin.getLogger().severe("Cannot save mutes.yml");
        }
    }

    public void mute(UUID uuid, String name, long durationMs, String reason, String by) {
        long now = System.currentTimeMillis();
        long expires = durationMs == -1 ? -1 : now + durationMs;
        mutes.put(uuid, new MuteEntry(uuid, name, now, expires, reason, by));
        saveAll();
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
        saveAll();
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry e = mutes.get(uuid);
        if (e == null) return false;
        if (e.isExpired()) { mutes.remove(uuid); return false; }
        return true;
    }

    public MuteEntry getMute(UUID uuid) { return mutes.get(uuid); }

    public Map<UUID, MuteEntry> getAllMutes() { return Collections.unmodifiableMap(mutes); }

    /**
     * Parses duration string like "30m", "2h", "7d", "1d12h" into milliseconds.
     * Returns -1 for permanent (no duration given or "perm").
     */
    public static long parseDuration(String input) {
        if (input == null || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) return -1;
        long total = 0;
        Matcher m = Pattern.compile("(\\d+)([smhd])").matcher(input.toLowerCase());
        while (m.find()) {
            long val = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "s" -> val * 1000L;
                case "m" -> val * 60_000L;
                case "h" -> val * 3_600_000L;
                case "d" -> val * 86_400_000L;
                default  -> 0L;
            };
        }
        return total == 0 ? -1 : total;
    }

    public static String formatDuration(long ms) {
        if (ms == -1) return "permanent";
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0)  return d + "d " + (h % 24) + "h";
        if (h > 0)  return h + "h " + (m % 60) + "m";
        if (m > 0)  return m + "m " + (s % 60) + "s";
        return s + "s";
    }
}
