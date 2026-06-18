package pl.bell.bellchat.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.bell.bellchat.BellChat;

/**
 * AfkListener — wykrywa aktywność gracza i czyści/ustawia AFK.
 *
 * Aktywność = ruch (zmiana pozycji, nie sam obrót kamery), czat, komenda
 * (poza samym /afk), interakcja. Każda z nich kasuje status AFK.
 */
@SuppressWarnings("UnstableApiUsage")
public class AfkListener implements Listener {

    private final BellChat plugin;

    public AfkListener(BellChat plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getAfkManager().init(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAfkManager().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        // Ignoruj sam obrót kamery — liczy się realna zmiana pozycji
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        plugin.getAfkManager().markActivity(event.getPlayer());
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        // Czat NIE kasuje AFK (żeby tag [AFK] był widoczny na czacie),
        // ale resetuje timer bezczynności — gracz piszący nie zostanie wyrzucony.
        plugin.getAfkManager().touch(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        // Samo /afk obsługuje komenda — nie traktuj jako aktywności kasującej
        if (msg.equals("/afk") || msg.startsWith("/afk ")) return;
        plugin.getAfkManager().markActivity(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return; // płyty naciskowe itp. nie liczą się
        plugin.getAfkManager().markActivity(event.getPlayer());
    }
}
