package pl.bell.bellchat.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.bell.bellchat.BellChat;

/**
 * VIPJoinListener v2.2
 *
 * Join message:
 *   VIP gracz  → "§6✦ §5VIP §eNick §6joined the server! §6✦"  (jeden komunikat)
 *   Inni       → "§6✦ §eNick §6joined the game. §6✦"
 *
 * Quit message (wszyscy):
 *   → "§6✦ §eNick §6left the game. §6✦"
 */
public class VIPJoinListener implements Listener {

    private final BellChat plugin;

    public VIPJoinListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player   = event.getPlayer();
        String vipGroup = plugin.getConfig().getString("vip-notification.vip-group", "vip");
        String primary  = plugin.getLuckPermsManager().getPrimaryGroup(player);
        boolean isVip   = vipGroup.equalsIgnoreCase(primary);
        boolean notifEnabled = plugin.getConfig().getBoolean("vip-notification.enabled", true);

        String joinMsg;

        if (isVip && notifEnabled) {
            // VIP — jeden komunikat zastępuje zarówno join message jak i broadcast
            String defMsg = "§6✦ §5VIP §e{player} §6joined the server! §6✦";
            joinMsg = plugin.getConfig()
                    .getString("vip-notification.message", defMsg)
                    .replace("{player}", player.getName())
                    .replace("&", "§");
        } else {
            // Zwykły gracz — czysty join bez LP prefix/suffix
            joinMsg = "§6✦ §e" + player.getName() + " §6joined the game. §6✦";
        }

        event.joinMessage(LegacyComponentSerializer.legacySection()
                .deserialize(joinMsg));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player  = event.getPlayer();
        String quitMsg = "§6✦ §e" + player.getName() + " §6left the game. §6✦";
        event.quitMessage(LegacyComponentSerializer.legacySection()
                .deserialize(quitMsg));
    }
}
