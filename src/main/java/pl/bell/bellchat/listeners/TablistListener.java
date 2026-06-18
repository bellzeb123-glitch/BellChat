package pl.bell.bellchat.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import pl.bell.bellchat.BellChat;

/**
 * TablistListener — ustawia czysty format nicku w TAB liście.
 *
 * Problem: domyślnie TAB pokazuje "VIPBellzebuhVIP" — zlepiony prefix+nick+suffix
 * bez spacji, czasem zduplikowany przez różne pluginy/scoreboard teams.
 *
 * Rozwiązanie: ustawiamy player list name jako:
 *   <LP prefix><nick>
 * np. "[VIP] Bellzebuh" z zachowanym kolorem z LP.
 *
 * Konfiguracja:
 *   tablist:
 *     enabled: true
 *     format: "{prefix}{player}"   # {prefix} z LP, {player} = nick
 *
 * Wyłączenie: tablist.enabled: false (zostawia domyślny format serwera).
 *
 * Uwaga: aktualizujemy z opóźnieniem 1 tick po join, bo LP może
 * potrzebować chwili na załadowanie danych gracza.
 */
public class TablistListener implements Listener {

    private final BellChat plugin;

    public TablistListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) return;

        Player player = event.getPlayer();
        // Opóźnienie — LP musi załadować dane gracza
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) updateTablist(player);
            }
        }.runTaskLater(plugin, 10L); // 0.5 sekundy
    }

    /**
     * Ustawia format nicku w TAB dla gracza.
     */
    public void updateTablist(Player player) {
        String format = plugin.getConfig()
                .getString("tablist.format", "{prefix}{player}");

        String lpPrefix = plugin.getLuckPermsManager().getPrefix(player);
        String lpSuffix = plugin.getLuckPermsManager().getSuffix(player);

        String prefix = lpPrefix != null ? lpPrefix : "";
        String suffix = lpSuffix != null ? lpSuffix : "";

        // Zbuduj format. Jeśli prefix nie kończy się spacją, dodaj.
        if (!prefix.isEmpty() && !prefix.endsWith(" ")) {
            prefix = prefix + " ";
        }

        String listName = format
                .replace("{prefix}", prefix)
                .replace("{suffix}", suffix)
                .replace("{player}", player.getName())
                .replace("&", "§");

        // Dopisek [AFK] gdy gracz jest AFK
        if (plugin.getAfkManager() != null
                && plugin.getAfkManager().isAfk(player.getUniqueId())) {
            String afkSuffix = plugin.getConfig()
                    .getString("afk.tablist-suffix", " &7☾ AFK")
                    .replace("&", "§");
            listName = listName + afkSuffix;
        }

        Component component = LegacyComponentSerializer.legacySection().deserialize(listName);
        player.playerListName(component);
    }

    /**
     * Odświeża TAB dla wszystkich online (np. po reload).
     */
    public void refreshAll() {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTablist(p);
        }
    }
}
