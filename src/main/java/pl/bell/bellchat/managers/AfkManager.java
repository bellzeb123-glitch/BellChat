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
 *  - Auto-kick po dłuższym AFK (config: afk.auto-kick.*), z wyłączeniem
 *    dla wybranych grup LuckPerms (exempt-groups) lub permission bypass.
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
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) return;
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
        afkPlayers.remove(uuid); // ciche czyszczenie przy wyjściu — bez komunikatu "wrócił"
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
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) return;

        long now      = System.currentTimeMillis();
        long afkAfter  = plugin.getConfig().getInt("afk.auto-afk-seconds", 180) * 1000L;
        boolean kickOn = plugin.getConfig().getBoolean("afk.auto-kick.enabled", false);
        long kickAfter = plugin.getConfig().getInt("afk.auto-kick.seconds", 900) * 1000L;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            long idle = now - lastActivity.getOrDefault(uuid, now);

            // Auto-AFK
            if (afkAfter > 0 && !isAfk(uuid) && idle >= afkAfter) {
                setAfk(p, true, true);
            }

            // Auto-kick (tylko gdy AFK i po przekroczeniu progu)
            if (kickOn && kickAfter > 0 && isAfk(uuid) && idle >= kickAfter && !isKickExempt(p)) {
                String reason = plugin.getMessageManager().get("afk-kick-reason");
                p.kick(LegacyComponentSerializer.legacySection().deserialize(reason));
            }
        }
    }

    /**
     * Czy gracz jest zwolniony z auto-kicka.
     * - permission bellchat.afk.kick.bypass, lub
     * - jego główna grupa LP jest na liście afk.auto-kick.exempt-groups (np. VIP).
     */
    public boolean isKickExempt(Player p) {
        if (p.hasPermission("bellchat.afk.kick.bypass")) return true;

        var exempt = plugin.getConfig().getStringList("afk.auto-kick.exempt-groups");
        if (exempt.isEmpty()) return false;

        String group = plugin.getLuckPermsManager().getPrimaryGroup(p);
        if (group == null) return false;
        return exempt.stream().anyMatch(g -> g.equalsIgnoreCase(group));
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
