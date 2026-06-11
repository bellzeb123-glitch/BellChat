package pl.bell.bellchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wyświetla powiadomienie gdy gracz z grupy VIP dołącza do serwera.
 *
 * Config:
 *   vip-notification.enabled: true
 *   vip-notification.vip-group: "vip"        ← musi zgadzać się z nazwą grupy LP
 *   vip-notification.message: "..."
 *
 * Domyślna wiadomość PL: "§6✦ §5VIP §e{player} §6dołączył do gry! §6✦"
 * Domyślna wiadomość EN: "§6✦ §5VIP §e{player} §6joined the server! §6✦"
 *
 * Powiadomienie pojawia się raz na sesję — nie za każdym razem gdy gracz
 * zmienia świat lub reconnectuje w ciągu tej samej sesji serwera.
 */
public class VIPJoinListener implements Listener {

    private final BellChat plugin;
    /** UUID graczy którym już pokazano powiadomienie w tej sesji serwera. */
    private final Set<UUID> notifiedThisSession = new HashSet<>();

    public VIPJoinListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("vip-notification.enabled", true)) return;

        Player player   = event.getPlayer();
        String vipGroup = plugin.getConfig().getString("vip-notification.vip-group", "vip");

        // Sprawdź czy gracz jest w grupie VIP
        String primaryGroup = plugin.getLuckPermsManager().getPrimaryGroup(player);
        if (!vipGroup.equalsIgnoreCase(primaryGroup)) return;

        // Pokaż raz na sesję
        if (notifiedThisSession.contains(player.getUniqueId())) return;
        notifiedThisSession.add(player.getUniqueId());

        // Pobierz wiadomość z config — z fallbackiem
        String lang    = plugin.getConfig().getString("language", "en");
        String defMsg  = lang.equalsIgnoreCase("pl")
                ? "§6✦ §5VIP §e{player} §6dołączył do gry! §6✦"
                : "§6✦ §5VIP §e{player} §6joined the server! §6✦";

        String message = plugin.getConfig()
                .getString("vip-notification.message", defMsg)
                .replace("{player}", player.getName())
                .replace("&", "§");

        Bukkit.broadcastMessage(message);
    }
}
