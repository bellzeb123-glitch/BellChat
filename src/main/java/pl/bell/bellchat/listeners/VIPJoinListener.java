package pl.bell.bellchat.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.bell.bellchat.BellChat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * VIPJoinListener v2.1
 *
 * Dwie funkcje:
 * 1. Nadpisuje domyślny join message ("X joined the game") dla WSZYSTKICH graczy
 *    na czysty format bez LP prefix/suffix w nicku.
 *    Format: "§e✦ §f<nick> §7dołączył do gry. §e✦"
 *
 * 2. Dla graczy VIP dodatkowo broadcastuje specjalny komunikat.
 *    Format z config: "§6✦ §5VIP §e{player} §6dołączył do gry! §6✦"
 *
 * Dlaczego nadpisujemy join message:
 *    Purpur domyślnie używa getDisplayName() w komunikacie dołączenia,
 *    co powoduje że LP prefix/suffix wchodzi do wiadomości join.
 *    Nadpisujemy event.joinMessage() żeby wyświetlać tylko czysty nick.
 */
public class VIPJoinListener implements Listener {

    private final BellChat plugin;
    private final Set<UUID> notifiedThisSession = new HashSet<>();

    public VIPJoinListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String lang   = plugin.getConfig().getString("language", "en").toLowerCase();
        boolean isPl  = lang.equals("pl");

        // ── 1. Nadpisz domyślny join message (czysty nick, bez LP prefix) ──
        // Join message — jeden format dla wszystkich, bez rozróżnienia języka
        String joinMsg = "§6✦ §e" + player.getName() + " §6joined the game. §6✦";
        event.joinMessage(LegacyComponentSerializer.legacySection()
                .deserialize(joinMsg));

        // ── 2. VIP broadcast ─────────────────────────────────────────────
        if (!plugin.getConfig().getBoolean("vip-notification.enabled", true)) return;

        String vipGroup = plugin.getConfig()
                .getString("vip-notification.vip-group", "vip");
        String primary  = plugin.getLuckPermsManager().getPrimaryGroup(player);

        if (!vipGroup.equalsIgnoreCase(primary)) return;
        if (notifiedThisSession.contains(player.getUniqueId())) return;
        notifiedThisSession.add(player.getUniqueId());

        // Domyślny komunikat zależny od języka
        String defMsg = isPl
                ? "§6✦ §5VIP §e{player} §6dołączył do gry! §6✦"
                : "§6✦ §5VIP §e{player} §6joined the server! §6✦";

        String vipMsg = plugin.getConfig()
                .getString("vip-notification.message", defMsg)
                .replace("{player}", player.getName())
                .replace("&", "§");

        Bukkit.broadcastMessage(vipMsg);
    }
}