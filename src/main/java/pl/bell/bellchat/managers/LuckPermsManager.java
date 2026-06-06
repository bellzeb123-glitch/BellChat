package pl.bell.bellchat.managers;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import pl.bell.bellchat.BellChat;

public class LuckPermsManager {

    private final BellChat plugin;
    private LuckPerms luckPerms;

    public LuckPermsManager(BellChat plugin) {
        this.plugin = plugin;
        tryHook();
    }

    private void tryHook() {
        RegisteredServiceProvider<LuckPerms> provider =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            plugin.getLogger().info("LuckPerms hooked successfully.");
        } else {
            plugin.getLogger().warning("LuckPerms not found. Group-based chat formats disabled.");
        }
    }

    /** Returns the player's primary group name, or "default" if LP unavailable */
    public String getPrimaryGroup(Player player) {
        if (luckPerms == null) return "default";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        return user.getPrimaryGroup();
    }

    /** Returns the player's prefix from LP meta, or empty string */
    public String getPrefix(Player player) {
        if (luckPerms == null) return "";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        var meta = user.getCachedData().getMetaData(QueryOptions.nonContextual());
        String prefix = meta.getPrefix();
        return prefix != null ? prefix : "";
    }

    /** Returns the player's suffix from LP meta, or empty string */
    public String getSuffix(Player player) {
        if (luckPerms == null) return "";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        var meta = user.getCachedData().getMetaData(QueryOptions.nonContextual());
        String suffix = meta.getSuffix();
        return suffix != null ? suffix : "";
    }

    public boolean isHooked() { return luckPerms != null; }
}
