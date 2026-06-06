package pl.bell.bellchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.bell.bellchat.BellChat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Detects when a player joins with VIP group and broadcasts a notification
 * if this is their first join as VIP (tracked in memory per session).
 *
 * For persistent tracking (only notify once ever), extend with a data file.
 */
public class VIPJoinListener implements Listener {

    private final BellChat plugin;
    private final Set<UUID> notifiedThisSession = new HashSet<>();

    public VIPJoinListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("vip-notification.enabled", true)) return;

        Player player = event.getPlayer();
        String vipGroup = plugin.getConfig().getString("vip-notification.vip-group", "vip");

        // Check LP group
        String group = plugin.getLuckPermsManager().getPrimaryGroup(player);
        if (!group.equalsIgnoreCase(vipGroup)) return;

        // Only notify once per session per player
        if (notifiedThisSession.contains(player.getUniqueId())) return;
        notifiedThisSession.add(player.getUniqueId());

        String template = plugin.getConfig().getString(
                "vip-notification.message",
                "&6✦ &e{player} &6has joined the VIP ranks! &6✦");
        String message = plugin.getMessageManager().color(
                template.replace("{player}", player.getName()));

        // Broadcast after 1 tick so join message appears first
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> Bukkit.broadcastMessage(message), 2L);
    }
}
