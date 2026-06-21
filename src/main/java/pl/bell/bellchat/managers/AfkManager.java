package pl.bell.bellchat.managers;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.bell.bellchat.BellChat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AfkManager — pełna obsługa AFK (zastępuje EssentialsX AFK).
 *
 * Funkcje:
 *  - Ręczne /afk (toggle).
 *  - Auto-AFK po X sekundach bezczynności (config: afk.auto-afk-seconds).
 *  - Komunikaty globalne wejścia/wyjścia z AFK (messages: afk-now / afk-back).
 *  - Znacznik [AFK] w TAB (TablistListener).
 *  - Auto-kick po dłuższym AFK (reguły per grupa LuckPerms w afk.groups),
 *    z wyłączeniem dla permission bellchat.afk.kick.bypass.
 *
 * Wątki:
 *  - markActivity() jest wołane też z wątku asynchronicznego (czat),
 *    dlatego sama aktualizacja czasu jest na ConcurrentHashMap, a faktyczny
 *    powrót z AFK (broadcast + tablist) wykonujemy na głównym wątku.
 */
public class AfkManager {

    private final BellChat plugin;

    /** UUID → czas ostatniej aktywności (millis). */
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    /** Gracze aktualnie AFK. */
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    private BukkitTask checkTask;

    public AfkManager(BellChat plugin) {
        this.plugin = plugin;
        startTask();
    }

    // ── Lifecycle ───────────────────────────────────────────────

    public void reload() {
        stopTask();
        startTask();
    }

    public void shutdown() {
        stopTask();
    }

    private void startTask() {
        if (!plugin.getAfkConfigManager().isGlobalEnabled()) return;
        // co 1 sekundę (20 ticków) — sprawdza bezczynność i auto-kick
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void stopTask() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    // ── Stan / aktywność ────────────────────────────────────────

    public void init(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void remove(UUID uuid) {
        lastActivity.remove(uuid);
        afkPlayers.remove(uuid);
        plugin.getAfkConfigManager().invalidateCache(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    /**
     * Odświeża czas aktywności BEZ kasowania AFK.
     * Używane dla czatu — gracz oznaczony AFK może pisać (i wciąż ma tag [AFK]),
     * ale nie zostanie wyrzucony za bezczynność, bo timer się resetuje.
     */
    public void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Rejestruje aktywność gracza. Jeśli był AFK — przywraca go (powrót).
     * Bezpieczne do wołania z wątku asynchronicznego.
     */
    public void markActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (!isAfk(player.getUniqueId())) return;

        if (Bukkit.isPrimaryThread()) {
            setAfk(player, false, false);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && isAfk(player.getUniqueId())) {
                    setAfk(player, false, false);
                }
            });
        }
    }

    /** Przełącza AFK ręcznie (komenda /afk). */
    public void toggle(Player player) {
        setAfk(player, !isAfk(player.getUniqueId()), false);
    }

    /**
     * Ustawia stan AFK i wysyła komunikat globalny.
     *
     * @param auto true gdy zmiana wynika z auto-afk (inny komunikat wejścia).
     */
    public void setAfk(Player player, boolean afk, boolean auto) {
        UUID uuid = player.getUniqueId();
        boolean was = afkPlayers.contains(uuid);
        if (afk == was) return;

        if (afk) {
            afkPlayers.add(uuid);
            broadcast(auto ? "afk-now-auto" : "afk-now", player);
        } else {
            afkPlayers.remove(uuid);
            lastActivity.put(uuid, System.currentTimeMillis());
            broadcast("afk-back", player);
        }

        // Odśwież TAB (dopisek [AFK])
        if (plugin.getTablistListener() != null) {
            plugin.getTablistListener().updateTablist(player);
        }
    }

    // ── Tick: auto-afk + auto-kick ──────────────────────────────

    private void tick() {
        if (!plugin.getAfkConfigManager().isGlobalEnabled()) return;

        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("bellchat.afk.kick.bypass")) continue;

            UUID uuid = p.getUniqueId();
            long idle = now - lastActivity.getOrDefault(uuid, now);

            var rule = plugin.getAfkConfigManager().resolveRule(p);
            long afkAfter = rule.getAutoAfkSeconds() * 1000L;
            boolean kickOn = rule.isKickEnabled();
            long kickAfter = rule.getKickSeconds() * 1000L;

            if (afkAfter > 0 && !isAfk(uuid) && idle >= afkAfter) {
                setAfk(p, true, true);
            }

            if (kickOn && kickAfter > 0 && isAfk(uuid) && idle >= kickAfter) {
                String reason = plugin.getMessageManager().get("afk-kick-reason");
                p.kick(LegacyComponentSerializer.legacySection().deserialize(reason));
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    /** Komunikat globalny (bez prefiksu pluginu — styl jak wejście/wyjście). */
    private void broadcast(String key, Player player) {
        String text = plugin.getMessageManager().get(key).replace("{player}", player.getName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(text);
        }
        plugin.getLogger().info("[AFK] " + player.getName() + " → " + key);
    }
}
