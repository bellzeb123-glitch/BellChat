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
 * VIPJoinListener v3.0 — komunikaty wejścia/wyjścia z plików językowych.
 *
 * Teksty pobierane z MessageManager (messages_en/pl.yml), więc /bch lang
 * automatycznie przełącza też język komunikatów logowania.
 *
 * Format DYNAMICZNY — kolor i prefix z LP:
 *   Grupa z notification-groups → klucz join-notify-group / quit-notify-group
 *     {prefix}   = prefix grupy z LP (zachowuje kolor, np. "§5[VIP] ")
 *     {lp-color} = ostatni kolor z prefiksu (dla nicku)
 *   Inni gracze → join-normal / quit-normal
 *
 * Dodanie nowej grupy (np. SVIP):
 *   1. /lp group svip meta addprefix 100 "&4[SVIP] "
 *   2. config: notification-groups: [vip, svip]
 *   Komunikat automatycznie dostanie czerwony [SVIP] z LP — bez zmian w kodzie.
 */
public class VIPJoinListener implements Listener {

    private final BellChat plugin;

    public VIPJoinListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String msg    = buildMessage(player, true);
        event.joinMessage(LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String msg    = buildMessage(player, false);
        event.quitMessage(LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    // ── Builder ───────────────────────────────────────────────

    private String buildMessage(Player player, boolean joining) {
        var msg = plugin.getMessageManager();
        boolean notifEnabled = plugin.getConfig()
                .getBoolean("vip-notification.enabled", true);

        if (notifEnabled && isNotifiableGroup(player)) {
            // Komunikat grupowy — dynamiczny prefix + kolor z LP
            String lpPrefix = plugin.getLuckPermsManager().getPrefix(player);
            String prefix   = (lpPrefix != null ? lpPrefix : "").replace("&", "§");
            if (!prefix.isEmpty() && !prefix.endsWith(" ")) prefix = prefix + " ";
            String lpColor  = extractLastColor(prefix);

            String key = joining ? "join-notify-group" : "quit-notify-group";
            return msg.get(key)
                    .replace("{prefix}",   prefix)
                    .replace("{lp-color}", lpColor)
                    .replace("{player}",   player.getName());
        } else {
            // Komunikat standardowy
            String key = joining ? "join-normal" : "quit-normal";
            return msg.get(key).replace("{player}", player.getName());
        }
    }

    private boolean isNotifiableGroup(Player player) {
        var groups = plugin.getConfig()
                .getStringList("vip-notification.notification-groups");
        String primary = plugin.getLuckPermsManager().getPrimaryGroup(player);
        if (primary == null) return false;

        if (groups.isEmpty()) {
            // Backwards compat
            String vipGroup = plugin.getConfig()
                    .getString("vip-notification.vip-group", "vip");
            return vipGroup.equalsIgnoreCase(primary);
        }
        return groups.stream().anyMatch(g -> g.equalsIgnoreCase(primary));
    }

    /**
     * Wyciąga ostatni kod koloru (§x) z prefiksu — dla koloru nicku.
     * Pomija kody formatowania (l/o/n/m/k). Domyślnie §f.
     */
    private String extractLastColor(String s) {
        String last = "§f";
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '§') {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if ("0123456789abcdef".indexOf(code) >= 0) last = "§" + code;
            }
        }
        return last;
    }
}
