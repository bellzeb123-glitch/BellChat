package pl.bell.bellchat.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.bell.bellchat.BellChat;

/**
 * Routes chat input to admin GUIs (AFK, broadcasts, channels).
 */
public final class AdminChatInputListener implements Listener {

    private final BellChat plugin;

    public AdminChatInputListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isAwaitingAny(player)) return;

        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.getAfkAdminGUI().handleChatInput(player, message)) return;
            if (plugin.getBroadcastAdminGUI().handleChatInput(player, message)) return;
            plugin.getChannelAdminGUI().handleChatInput(player, message);
        });
    }

    private boolean isAwaitingAny(Player player) {
        return plugin.getAfkAdminGUI().isAwaitingInput(player)
                || plugin.getBroadcastAdminGUI().isAwaitingInput(player)
                || plugin.getChannelAdminGUI().isAwaitingInput(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPending(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearPending(event.getPlayer());
    }

    private void clearPending(Player player) {
        plugin.getAfkAdminGUI().cancelPendingInput(player);
        plugin.getBroadcastAdminGUI().cancelPendingInput(player);
        plugin.getChannelAdminGUI().cancelPendingInput(player);
    }
}
