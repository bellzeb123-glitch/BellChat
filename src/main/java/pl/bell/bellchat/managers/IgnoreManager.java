package pl.bell.bellchat.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bell.bellchat.BellChat;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class IgnoreManager {

    private final BellChat plugin;
    private File ignoreFile;
    private final Map<UUID, Set<UUID>> ignoreMap = new HashMap<>();

    public IgnoreManager(BellChat plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ignoreFile = new File(plugin.getDataFolder(), "ignores.yml");
        if (!ignoreFile.exists()) {
            try { plugin.getDataFolder().mkdirs(); ignoreFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Cannot create ignores.yml"); }
        }
        reload();
    }

    public void reload() {
        ignoreMap.clear();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(ignoreFile);
        if (!cfg.isConfigurationSection("ignores")) return;
        for (String uuidStr : cfg.getConfigurationSection("ignores").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> ignored = cfg.getStringList("ignores." + uuidStr);
                Set<UUID> set = new HashSet<>();
                for (String s : ignored) {
                    try { set.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored2) {}
                }
                ignoreMap.put(uuid, set);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveAll() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> e : ignoreMap.entrySet()) {
            List<String> list = e.getValue().stream().map(UUID::toString).toList();
            cfg.set("ignores." + e.getKey(), list);
        }
        try { cfg.save(ignoreFile); }
        catch (IOException e) { plugin.getLogger().severe("Cannot save ignores.yml"); }
    }

    public boolean isIgnoring(UUID player, UUID target) {
        Set<UUID> set = ignoreMap.get(player);
        return set != null && set.contains(target);
    }

    public boolean toggle(UUID player, UUID target) {
        ignoreMap.computeIfAbsent(player, k -> new HashSet<>());
        Set<UUID> set = ignoreMap.get(player);
        if (set.contains(target)) { set.remove(target); saveAll(); return false; }
        else { set.add(target); saveAll(); return true; }
    }

    public Set<UUID> getIgnored(UUID player) {
        return ignoreMap.getOrDefault(player, Collections.emptySet());
    }
}
