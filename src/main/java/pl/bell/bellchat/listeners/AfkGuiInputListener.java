package pl.bell.bellchat.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pl.bell.bellchat.BellChat;

/**
 * Chat input for AFK admin GUI time values.
 */
public final class AfkGuiInputListener implements Listener {

    private final BellChat plugin;

    public AfkGuiInputListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAfkAdminGUI().isAwaitingInput(player)) return;

        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getAfkAdminGUI().handleChatInput(player, message));
    }
}
