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
 * VIPJoinListener v2.4 — dynamiczne komunikaty per-grupa.
 *
 * Zamiast hardkodować grupy, automatycznie czyta z LuckPerms:
 *   - prefix grupy (np. "&5[VIP]" lub "&4[SVIP]") → kolor + nazwa
 *   - primaryGroup gracza
 *
 * Config: notification-groups — lista grup które dostają specjalny komunikat.
 * Jeśli grupy nie ma na liście → standardowy komunikat.
 *
 * Przykład config:
 *   vip-notification:
 *     enabled: true
 *     notification-groups: [vip, svip, mvp]
 *
 * Format komunikatu (automatyczny):
 *   Join: §6✦ <prefix_grupy><nick> §6joined the server! §6✦
 *   Quit: §6✦ <prefix_grupy><nick> §6left the server! §6✦
 *
 * Gdzie <prefix_grupy> pochodzi bezpośrednio z LP prefix grupy.
 * Np. LP prefix VIP = "&5[VIP] " → komunikat: §6✦ §5[VIP] §5Nick §6joined the server! §6✦
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
        boolean notifEnabled = plugin.getConfig()
                .getBoolean("vip-notification.enabled", true);

        if (notifEnabled && isNotifiableGroup(player)) {
            return buildGroupMessage(player, joining);
        } else {
            // Standardowy komunikat dla zwykłych graczy
            String action = joining ? "joined the game" : "left the game";
            return "§6✦ §e" + player.getName() + " §6" + action + ". §6✦";
        }
    }

    /**
     * Buduje komunikat używając prefiksu grupy z LuckPerms.
     *
     * LP prefix np. "&5[VIP] " jest konwertowany do §-kodów.
     * Kolor ostatniego §x z prefiksu jest używany dla nicku gracza.
     */
    private String buildGroupMessage(Player player, boolean joining) {
        String lpPrefix = plugin.getLuckPermsManager().getPrefix(player);
        String action   = joining ? "joined the server" : "left the server";

        if (lpPrefix == null || lpPrefix.isBlank()) {
            // Brak prefiksu w LP — użyj nazwy grupy z wielką literą
            String group = capitalize(plugin.getLuckPermsManager().getPrimaryGroup(player));
            return "§6✦ §e" + group + " §e" + player.getName()
                    + " §6" + action + "! §6✦";
        }

        // Konwertuj &-kody z LP na §-kody
        String prefix = lpPrefix.replace("&", "§").trim();

        // Wyciągnij ostatni kod koloru z prefiksu żeby nick był tym samym kolorem
        String nickColor = extractLastColor(prefix);

        return "§6✦ " + prefix + " " + nickColor + player.getName()
                + " §6" + action + "! §6✦";
    }

    /**
     * Sprawdza czy gracz należy do grupy z listy notification-groups.
     * Jeśli lista pusta lub nieobecna → używa starego klucza vip-group.
     */
    private boolean isNotifiableGroup(Player player) {
        var groups = plugin.getConfig()
                .getStringList("vip-notification.notification-groups");

        String primary = plugin.getLuckPermsManager().getPrimaryGroup(player);
        if (primary == null) return false;

        if (groups.isEmpty()) {
            // Backwards compat — stary klucz vip-group
            String vipGroup = plugin.getConfig()
                    .getString("vip-notification.vip-group", "vip");
            return vipGroup.equalsIgnoreCase(primary);
        }

        return groups.stream().anyMatch(g -> g.equalsIgnoreCase(primary));
    }

    /**
     * Wyciąga ostatni kod koloru (§x) z końca stringa.
     * Np. "§5[VIP]" → "§5", "§4[SVIP]§l" → "§4"
     * Pomija kody formatowania (§l §o §n §m §k), bierze tylko kolor.
     */
    private String extractLastColor(String s) {
        String lastColor = "§f"; // domyślnie biały
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '§') {
                char code = s.charAt(i + 1);
                // Tylko kody kolorów (0-9, a-f), nie formatowania (k,l,m,n,o,r)
                if ("0123456789abcdef".indexOf(code) >= 0) {
                    lastColor = "§" + code;
                }
            }
        }
        return lastColor;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
