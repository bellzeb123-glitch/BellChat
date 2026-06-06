package pl.bell.bellchat.managers;

import pl.bell.bellchat.BellChat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntispamManager {

    private final BellChat plugin;
    private final Map<UUID, Long> lastMessage   = new HashMap<>();
    private final Map<UUID, String> lastContent = new HashMap<>();

    private boolean enabled;
    private int cooldownSeconds;
    private boolean blockDuplicate;

    public AntispamManager(BellChat plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg    = plugin.getConfig().getConfigurationSection("antispam");
        enabled        = cfg != null && cfg.getBoolean("enabled", false);
        cooldownSeconds= cfg != null ? cfg.getInt("cooldown-seconds", 3) : 3;
        blockDuplicate = cfg != null && cfg.getBoolean("block-duplicate", true);
    }

    public enum SpamResult { ALLOWED, COOLDOWN, DUPLICATE }

    public SpamResult check(UUID uuid, String message) {
        if (!enabled) return SpamResult.ALLOWED;

        long now = System.currentTimeMillis();
        long last = lastMessage.getOrDefault(uuid, 0L);
        long diff = (now - last) / 1000;

        if (diff < cooldownSeconds) return SpamResult.COOLDOWN;
        if (blockDuplicate && message.equalsIgnoreCase(lastContent.getOrDefault(uuid, "")))
            return SpamResult.DUPLICATE;

        lastMessage.put(uuid, now);
        lastContent.put(uuid, message);
        return SpamResult.ALLOWED;
    }

    public int getCooldownSeconds() { return cooldownSeconds; }
    public boolean isEnabled()      { return enabled; }
    public void clearPlayer(UUID uuid) { lastMessage.remove(uuid); lastContent.remove(uuid); }
}
