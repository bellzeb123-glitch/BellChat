package pl.bell.bellchat.managers;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import pl.bell.bellchat.BellChat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

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

    /**
     * Returns the chat color code for the player's primary group.
     * Falls back to white if no color mapping found.
     */
    public String getChatColor(Player player) {
        if (luckPerms == null) return "&f";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "&f";
        // Try to get color from prefix first character
        String prefix = getPrefix(player);
        if (prefix != null && prefix.length() >= 2 && prefix.charAt(0) == '\u00a7') {
            return "\u00a7" + prefix.charAt(1);
        }
        // Fallback based on primary group
        return switch (user.getPrimaryGroup().toLowerCase()) {
            case "admin"  -> "\u00a76";
            case "vip"    -> "\u00a75";
            default       -> "\u00a7f";
        };
    }

    public boolean isHooked() { return luckPerms != null; }

    /** Czy gracz dziedziczy grupę LP (nie tylko primary). */
    public boolean inheritsGroup(Player player, String groupName) {
        if (luckPerms == null || groupName == null) return false;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        return user.getInheritedGroups(QueryOptions.nonContextual()).stream()
            .anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
    }

    /** All loaded LuckPerms groups (sorted alphabetically). */
    public List<String> getAllGroupNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (luckPerms != null) {
            for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
                names.add(group.getName());
            }
        }
        if (names.isEmpty()) {
            names.add("default");
        }
        return new ArrayList<>(names);
    }

    /**
     * Inherited groups for a player, highest LP weight first.
     * Falls back to primary group or {@code default}.
     */
    public List<String> getInheritedGroupsByWeight(Player player) {
        if (luckPerms == null) {
            return List.of("default");
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return List.of("default");
        }
        List<Group> sorted = new ArrayList<>(user.getInheritedGroups(QueryOptions.nonContextual()));
        sorted.sort(Comparator
                .comparingInt((Group g) -> g.getWeight().orElse(0))
                .reversed()
                .thenComparing(g -> g.getName(), String.CASE_INSENSITIVE_ORDER));
        return sorted.stream().map(Group::getName).distinct().toList();
    }
}
