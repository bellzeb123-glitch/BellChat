package pl.bell.bellchat.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.integration.BellLPIntegration;
import pl.bell.bellchat.model.AfkGroupRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-group AFK rules stored in config and merged with LuckPerms group list.
 */
public final class AfkConfigManager {

    private static final String PATH_GROUPS = "afk.groups";
    private static final String DEFAULT_GROUP = "default";

    private record CachedRule(AfkGroupRule rule, long cachedAt) {}

    private final BellChat plugin;
    private final Map<String, AfkGroupRule> configured = new LinkedHashMap<>();
    private final Map<UUID, CachedRule> ruleCache = new ConcurrentHashMap<>();

    public AfkConfigManager(BellChat plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        migrateLegacyConfig();
        configured.clear();
        ruleCache.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(PATH_GROUPS);
        if (section != null) {
            for (String group : section.getKeys(false)) {
                configured.put(normalize(group), readRule(section.getConfigurationSection(group)));
            }
        }
        ensureDefaultGroup();
    }

    public boolean isGlobalEnabled() {
        return plugin.getConfig().getBoolean("afk.enabled", true);
    }

    public void setGlobalEnabled(boolean enabled) {
        plugin.getConfig().set("afk.enabled", enabled);
        plugin.saveConfig();
    }

    public boolean hasConfiguredGroup(String group) {
        return configured.containsKey(normalize(group));
    }

    public AfkGroupRule getConfiguredRule(String group) {
        AfkGroupRule rule = configured.get(normalize(group));
        if (rule != null) return rule.copy();
        return getDefaultRule().copy();
    }

    public AfkGroupRule getDefaultRule() {
        return configured.getOrDefault(DEFAULT_GROUP, new AfkGroupRule(180, false, 900)).copy();
    }

    /**
     * All LuckPerms groups (sorted), plus any groups only present in config.
     * New LP groups appear here after reload without manual YAML edits.
     */
    public List<String> listManageableGroups() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(plugin.getLuckPermsManager().getAllGroupNames());
        names.addAll(configured.keySet());
        if (!names.contains(DEFAULT_GROUP)) {
            names.add(DEFAULT_GROUP);
        }
        return new ArrayList<>(names);
    }

    public void saveGroupRule(String group, AfkGroupRule rule) {
        String id = normalize(group);
        configured.put(id, rule.copy());

        String base = PATH_GROUPS + "." + id + ".";
        plugin.getConfig().set(base + "auto-afk-seconds", rule.getAutoAfkSeconds());
        plugin.getConfig().set(base + "kick-enabled", rule.isKickEnabled());
        plugin.getConfig().set(base + "kick-seconds", rule.getKickSeconds());
        plugin.saveConfig();
        BellLPIntegration.pushAfkToBellLP(id, rule.getAutoAfkSeconds(),
                rule.isKickEnabled(), rule.getKickSeconds());
    }

    public void clearGroupOverride(String group) {
        String id = normalize(group);
        if (DEFAULT_GROUP.equals(id)) return;
        configured.remove(id);
        plugin.getConfig().set(PATH_GROUPS + "." + id, null);
        plugin.saveConfig();
        BellLPIntegration.pushAfkClearedToBellLP(id);
    }

    /**
     * Highest-weight inherited LP group with explicit config, else default rule.
     * Permission bellchat.afk.kick.bypass is handled in AfkManager.
     * Results are cached per player for 30 seconds to avoid repeated LP lookups.
     */
    public AfkGroupRule resolveRule(Player player) {
        if (!isGlobalEnabled()) {
            return AfkGroupRule.disabled();
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        CachedRule cached = ruleCache.get(uuid);
        if (cached != null && now - cached.cachedAt() < 30_000) {
            return cached.rule().copy();
        }

        List<String> inherited = plugin.getLuckPermsManager().getInheritedGroupsByWeight(player);
        AfkGroupRule resolved = null;
        for (String group : inherited) {
            AfkGroupRule rule = configured.get(normalize(group));
            if (rule != null) {
                resolved = rule;
                break;
            }
        }
        if (resolved == null) {
            resolved = getDefaultRule();
        }
        ruleCache.put(uuid, new CachedRule(resolved.copy(), now));
        return resolved.copy();
    }

    public void invalidateCache(UUID uuid) {
        ruleCache.remove(uuid);
    }

    public void invalidateAllCaches() {
        ruleCache.clear();
    }

    public String resolveSourceGroup(Player player) {
        List<String> inherited = plugin.getLuckPermsManager().getInheritedGroupsByWeight(player);
        for (String group : inherited) {
            if (configured.containsKey(normalize(group))) {
                return group;
            }
        }
        return DEFAULT_GROUP;
    }

    private void ensureDefaultGroup() {
        if (!configured.containsKey(DEFAULT_GROUP)) {
            saveGroupRule(DEFAULT_GROUP, new AfkGroupRule(180, false, 900));
        }
    }

    private void migrateLegacyConfig() {
        var cfg = plugin.getConfig();
        if (cfg.contains(PATH_GROUPS)) {
            return;
        }

        int autoAfk = cfg.getInt("afk.auto-afk-seconds", 180);
        boolean kickEnabled = cfg.getBoolean("afk.auto-kick.enabled", false);
        int kickSeconds = cfg.getInt("afk.auto-kick.seconds", 900);

        saveGroupRule(DEFAULT_GROUP, new AfkGroupRule(autoAfk, kickEnabled, kickSeconds));

        List<String> exempt = cfg.getStringList("afk.auto-kick.exempt-groups");
        for (String group : exempt) {
            if (group == null || group.isBlank()) continue;
            String id = normalize(group);
            if (DEFAULT_GROUP.equals(id)) continue;
            AfkGroupRule rule = getConfiguredRule(id);
            rule.setKickEnabled(false);
            saveGroupRule(id, rule);
        }

        cfg.set("afk.auto-afk-seconds", null);
        cfg.set("afk.auto-kick", null);
        plugin.saveConfig();
    }

    private AfkGroupRule readRule(ConfigurationSection section) {
        if (section == null) {
            return getDefaultRule().copy();
        }
        return new AfkGroupRule(
                section.getInt("auto-afk-seconds", getDefaultRule().getAutoAfkSeconds()),
                section.getBoolean("kick-enabled", false),
                section.getInt("kick-seconds", getDefaultRule().getKickSeconds())
        );
    }

    private static String normalize(String group) {
        return group == null ? DEFAULT_GROUP : group.trim().toLowerCase(Locale.ROOT);
    }
}
