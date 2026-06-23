package pl.bell.bellchat.integration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.event.BellChatMessageEvent;
import pl.bell.bellchat.managers.MuteManager;
import pl.bell.bellchat.model.MuteEntry;
import pl.bell.suite.api.ActionDef;
import pl.bell.suite.api.ActionField;
import pl.bell.suite.api.ActionResult;
import pl.bell.suite.api.Actor;
import pl.bell.suite.api.BellModule;
import pl.bell.suite.api.Stat;
import pl.bell.suite.api.SuiteAction;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Modul BellChat w panelu BellSuite. Tylko ODCZYT + strumien na zywo — NIE duplikuje
 * logiki czatu, jedynie nasluchuje istniejacego {@link BellChatMessageEvent} (MONITOR,
 * po naniesieniu kolorow przez BellChatPro) i:
 *  - buforuje ostatnie {@value #BUFFER} wiadomosci (initial load przez {@code view("recent")}),
 *  - publikuje nowe do LiveBus BellSuite (temat {@code "chat"}) → WebSocket → panel.
 *
 * <p>Rejestrowany w {@code onEnable} BellChat tylko gdy BellSuite jest obecny.
 * Publikacja do LiveBus przez reflection na classloaderze BellSuite (zero twardej
 * zaleznosci runtime poza {@code bell-suite-api}, ktore jest {@code provided}).
 */
public final class BellSuiteModule implements BellModule, Listener {

    public static final String TOPIC = "chat";
    private static final int BUFFER = 100;

    /** Serializer &-kodow + HEX (&x&R&R&G&G&B&B) — jak w ChannelManager, do broadcastu z panelu. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final BellChat plugin;
    private final Deque<String> recent = new ArrayDeque<>(BUFFER); // gotowe obiekty JSON
    private Method publish;       // pl.bell.suite.web.LiveBus#publish(String,String)
    private boolean publishResolved;

    public BellSuiteModule(BellChat plugin) {
        this.plugin = plugin;
    }

    @Override public String id()          { return "bellchat"; }
    @Override public String displayName() { return "BellChat"; }
    @Override public String icon()        { return "message-circle"; }

    @Override
    public List<Stat> dashboard() {
        int channels = plugin.getChannelManager().getChannels().size();
        return List.of(
                new Stat("Kanaly", String.valueOf(channels), "#3FC9FF"),
                new Stat("Gracze online", String.valueOf(Bukkit.getOnlinePlayers().size()), "#97C459"),
                new Stat("Bufor wiadomosci", String.valueOf(recent.size()), "#8A5CF6"));
    }

    @Override
    public List<String> liveTopics() {
        return List.of(TOPIC);
    }

    /**
     * Widoki:
     *  - {@code "recent"} → {"messages":[ ... ]} (initial load strumienia czatu),
     *  - {@code "state"}  → {"chatLocked":bool,"mutes":[{player,uuid,reason,by,expires,permanent}]}.
     */
    @Override
    public String view(String viewId, java.util.Map<String, String> params) {
        if ("recent".equals(viewId)) {
            StringBuilder sb = new StringBuilder("{\"messages\":[");
            List<String> snapshot;
            synchronized (recent) { snapshot = new ArrayList<>(recent); }
            for (int i = 0; i < snapshot.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(snapshot.get(i));
            }
            return sb.append("]}").toString();
        }
        if ("channels".equals(viewId)) {
            StringBuilder sb = new StringBuilder("{\"channels\":[");
            boolean first = true;
            for (var ch : plugin.getChannelManager().getChannelsList()) {
                if (!first) sb.append(','); first = false;
                sb.append(Json.obj()
                        .add("id", ch.getId())
                        .add("type", ch.getType().name())
                        .add("displayName", ch.getDisplayName())
                        .add("format", ch.getFormat())
                        .add("localRadius", ch.getLocalRadius())
                        .add("permission", ch.getRequiredPermission() != null ? ch.getRequiredPermission() : "")
                        .add("enabled", ch.isEnabled() ? "1" : "0")
                        .add("default", plugin.getChannelManager().isDefaultChannel(ch.getId()) ? "1" : "0")
                        .end());
            }
            return sb.append("]}").toString();
        }
        if ("config".equals(viewId)) {
            return viewConfig();
        }
        if ("state".equals(viewId)) {
            StringBuilder sb = new StringBuilder("{\"chatLocked\":")
                    .append(plugin.getChatStateManager().isChatLocked())
                    .append(",\"mutes\":[");
            boolean first = true;
            for (MuteEntry e : plugin.getMuteManager().getAllMutes().values()) {
                if (e.isExpired()) continue;
                if (!first) sb.append(','); first = false;
                sb.append(Json.obj()
                        .add("player", e.getPlayerName())
                        .add("uuid", e.getPlayerUUID().toString())
                        .add("reason", e.getReason())
                        .add("by", e.getMutedBy())
                        .add("expires", e.getExpiresAt())
                        .add("permanent", e.isPermanent() ? "1" : "0")
                        .end());
            }
            return sb.append("]}").toString();
        }
        return "{}";
    }

    // ── akcje admina (broadcast + moderacja) ─────────────────────────────────────

    @Override
    public List<ActionDef> actions() {
        return List.of(
                ActionDef.of("broadcast", "Wyślij broadcast", "Wiadomości",
                        ActionField.text("message", "Wiadomość (obsługuje &kolory)")),
                ActionDef.destructive("mute", "Wycisz gracza", "Moderacja",
                        ActionField.text("player", "Gracz"),
                        ActionField.text("duration", "Czas (np. 30m, 2h, 7d, perm)"),
                        ActionField.text("reason", "Powód")),
                ActionDef.of("unmute", "Cofnij wyciszenie", "Moderacja",
                        ActionField.text("player", "Gracz")),
                ActionDef.destructive("clearchat", "Wyczyść czat", "Moderacja"),
                ActionDef.destructive("chatlock", "Blokada czatu", "Moderacja",
                        ActionField.select("state", "Stan", List.of("on", "off"))),
                ActionDef.of("emoji.toggle", "Włącz/Wyłącz emoji", "Ustawienia",
                        ActionField.bool("enabled", "Włączone")),
                ActionDef.of("afk.toggle", "Włącz/Wyłącz AFK", "AFK",
                        ActionField.bool("enabled", "Włączone")),
                ActionDef.of("broadcast.slotToggle", "Włącz/Wyłącz slot broadcastu", "Broadcast",
                        ActionField.text("slot", "ID slotu"),
                        ActionField.bool("enabled", "Włączony")),
                ActionDef.of("broadcast.globalToggle", "Włącz/Wyłącz auto-broadcast", "Broadcast",
                        ActionField.bool("enabled", "Włączony")),
                ActionDef.of("channel.toggle", "Włącz/Wyłącz kanał", "Kanały",
                        ActionField.text("channel", "ID kanału"),
                        ActionField.bool("enabled", "Włączony")),
                ActionDef.destructive("channel.delete", "Usuń kanał", "Kanały",
                        ActionField.text("channel", "ID kanału")));
    }

    @Override
    public ActionResult invoke(SuiteAction action, Actor actor) {
        if (actor == null || !actor.admin()) return ActionResult.error("Brak uprawnień.");
        String a = action.name();
        var p = action.params();
        switch (a) {
            case "broadcast": {
                String msg = p.getOrDefault("message", "").trim();
                if (msg.isEmpty()) return ActionResult.error("Pusta wiadomość.");
                Component comp = LEGACY.deserialize(msg);
                Bukkit.getServer().broadcast(comp);
                // pokaż też w panelu (broadcast nie odpala BellChatMessageEvent)
                String json = Json.obj()
                        .add("ts", System.currentTimeMillis())
                        .add("player", actor.name() != null ? actor.name() : "PANEL")
                        .add("uuid", "")
                        .add("channel", "broadcast")
                        .add("message", msg)
                        .end();
                synchronized (recent) { if (recent.size() >= BUFFER) recent.pollFirst(); recent.addLast(json); }
                publishLive(json);
                return ActionResult.ok("Wysłano broadcast.");
            }
            case "mute": {
                OfflinePlayer t = resolve(p.get("player"));
                if (t == null) return ActionResult.error("Nie znaleziono gracza.");
                long dur = MuteManager.parseDuration(p.getOrDefault("duration", "perm"));
                String reason = p.getOrDefault("reason", "");
                if (reason.isBlank()) reason = "Brak powodu";
                plugin.getMuteManager().mute(t.getUniqueId(),
                        t.getName() != null ? t.getName() : p.get("player"), dur, reason,
                        actor.name() != null ? actor.name() : "Panel");
                return ActionResult.ok("Wyciszono gracza " + p.get("player")
                        + " (" + MuteManager.formatDuration(dur) + ").");
            }
            case "unmute": {
                OfflinePlayer t = resolve(p.get("player"));
                if (t == null) return ActionResult.error("Nie znaleziono gracza.");
                plugin.getMuteManager().unmute(t.getUniqueId());
                return ActionResult.ok("Cofnięto wyciszenie gracza " + p.get("player") + ".");
            }
            case "clearchat": {
                String blank = "\n".repeat(100);
                for (Player pl : Bukkit.getOnlinePlayers()) pl.sendMessage(blank);
                return ActionResult.ok("Wyczyszczono czat.");
            }
            case "chatlock": {
                boolean lock = !"off".equalsIgnoreCase(p.getOrDefault("state", "on"));
                plugin.getChatStateManager().setChatLocked(lock);
                return ActionResult.ok(lock ? "Czat zablokowany." : "Czat odblokowany.");
            }
            case "emoji.toggle": {
                boolean en = "true".equals(p.get("enabled"));
                plugin.getEmojiManager().setEnabled(en);
                return ActionResult.ok(en ? "Emoji włączone." : "Emoji wyłączone.");
            }
            case "afk.toggle": {
                boolean en = "true".equals(p.get("enabled"));
                plugin.getAfkConfigManager().setGlobalEnabled(en);
                if (plugin.getAfkManager() != null) plugin.getAfkManager().reload();
                return ActionResult.ok(en ? "AFK włączony." : "AFK wyłączony.");
            }
            case "broadcast.globalToggle": {
                boolean en = "true".equals(p.get("enabled"));
                plugin.getConfig().set("broadcasts.enabled", en);
                plugin.saveConfig();
                plugin.getBroadcastManager().reload();
                return ActionResult.ok(en ? "Auto-broadcast włączony." : "Auto-broadcast wyłączony.");
            }
            case "broadcast.slotToggle": {
                String slot = p.getOrDefault("slot", "").trim();
                if (slot.isEmpty()) return ActionResult.error("Podaj ID slotu.");
                if (!plugin.getBroadcastManager().hasSlot(slot)) return ActionResult.error("Slot nie istnieje.");
                boolean en = "true".equals(p.get("enabled"));
                plugin.getBroadcastManager().setSlotEnabled(slot, en);
                return ActionResult.ok("Slot " + slot + (en ? " włączony." : " wyłączony."));
            }
            case "channel.toggle": {
                String chId = p.getOrDefault("channel", "").trim();
                if (chId.isEmpty()) return ActionResult.error("Podaj ID kanału.");
                boolean en = "true".equals(p.get("enabled"));
                boolean ok = plugin.getChannelManager().setChannelEnabled(chId, en);
                if (!ok) return ActionResult.error("Nie można zmienić kanału (nie istnieje lub domyślny).");
                return ActionResult.ok("Kanał " + chId + (en ? " włączony." : " wyłączony."));
            }
            case "channel.delete": {
                String chId = p.getOrDefault("channel", "").trim();
                if (chId.isEmpty()) return ActionResult.error("Podaj ID kanału.");
                boolean ok = plugin.getChannelManager().deleteChannel(chId);
                if (!ok) return ActionResult.error("Nie można usunąć kanału (nie istnieje lub domyślny).");
                return ActionResult.ok("Kanał " + chId + " usunięty.");
            }
            default:
                return ActionResult.error("Nieznana akcja: " + a);
        }
    }

    private String viewConfig() {
        var bm = plugin.getBroadcastManager();
        var em = plugin.getEmojiManager();
        var uf = plugin.getUrlFilterManager();
        var as = plugin.getAntispamManager();
        var ac = plugin.getAfkConfigManager();

        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"broadcasts\":{\"enabled\":").append(plugin.getConfig().getBoolean("broadcasts.enabled", false));
        sb.append(",\"slots\":[");
        var keys = bm.getSlotKeys();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            String k = keys.get(i);
            sb.append("{\"key\":").append(q(k))
              .append(",\"enabled\":").append(bm.isSlotEnabled(k))
              .append(",\"interval\":").append(bm.getIntervalSeconds(k))
              .append(",\"random\":").append(bm.isRandom(k))
              .append(",\"messages\":[");
            var msgs = bm.getMessages(k);
            for (int j = 0; j < msgs.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append(q(msgs.get(j)));
            }
            sb.append("]}");
        }
        sb.append("]}");

        sb.append(",\"emoji\":{\"enabled\":").append(em.isEnabled()).append("}");
        sb.append(",\"urlFilter\":{\"enabled\":").append(uf.isEnabled()).append("}");
        sb.append(",\"antispam\":{\"enabled\":").append(as.isEnabled())
          .append(",\"cooldown\":").append(as.getCooldownSeconds()).append("}");
        sb.append(",\"afk\":{\"enabled\":").append(ac.isGlobalEnabled())
          .append(",\"groups\":[");
        var groups = ac.listManageableGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) sb.append(',');
            String g = groups.get(i);
            var rule = ac.getConfiguredRule(g);
            sb.append("{\"name\":").append(q(g))
              .append(",\"autoAfk\":").append(rule.getAutoAfkSeconds())
              .append(",\"kickEnabled\":").append(rule.isKickEnabled())
              .append(",\"kickSeconds\":").append(rule.getKickSeconds()).append("}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder o = new StringBuilder(s.length() + 2);
        o.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> o.append("\\\"");
                case '\\' -> o.append("\\\\");
                case '\n' -> o.append("\\n");
                case '\r' -> o.append("\\r");
                case '\t' -> o.append("\\t");
                default   -> o.append(c);
            }
        }
        o.append('"');
        return o.toString();
    }

    /** Gracz online (dokładny nick) lub offline znany serwerowi. */
    private OfflinePlayer resolve(String name) {
        if (name == null || name.isBlank()) return null;
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(op.getName())) return op;
        }
        return null;
    }

    // ── strumien na zywo ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMessage(BellChatMessageEvent event) {
        Player p = event.getPlayer();
        String channel = event.getChannel() != null ? event.getChannel().getId() : "";
        String json = Json.obj()
                .add("ts", System.currentTimeMillis())
                .add("player", p.getName())
                .add("uuid", p.getUniqueId().toString())
                .add("channel", channel)
                .add("message", event.getMessage())
                .end();

        synchronized (recent) {
            if (recent.size() >= BUFFER) recent.pollFirst();
            recent.addLast(json);
        }
        publishLive(json);
    }

    private void publishLive(String json) {
        Method m = resolvePublish();
        if (m == null) return;
        try {
            m.invoke(null, TOPIC, json);
        } catch (Throwable ignored) {
            // BellSuite wylaczony/przeladowany — bufor i tak trzyma wiadomosci do initial load
        }
    }

    private Method resolvePublish() {
        if (publishResolved) return publish;
        publishResolved = true;
        try {
            var suite = Bukkit.getPluginManager().getPlugin("BellSuite");
            if (suite == null) return null;
            Class<?> bus = suite.getClass().getClassLoader().loadClass("pl.bell.suite.web.LiveBus");
            publish = bus.getMethod("publish", String.class, String.class);
        } catch (Throwable t) {
            publish = null;
        }
        return publish;
    }

    // ── minimalny builder JSON (BellChat nie ma Jacksona) ────────────────────────

    static final class Json {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        static Json obj() { return new Json(); }

        Json add(String key, String val) {
            sep();
            sb.append('"').append(key).append("\":\"").append(esc(val)).append('"');
            return this;
        }

        Json add(String key, long val) {
            sep();
            sb.append('"').append(key).append("\":").append(val);
            return this;
        }

        String end() { return sb.append('}').toString(); }

        private void sep() { if (!first) sb.append(','); first = false; }

        private static String esc(String s) {
            if (s == null) return "";
            StringBuilder o = new StringBuilder(s.length() + 8);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"'  -> o.append("\\\"");
                    case '\\' -> o.append("\\\\");
                    case '\n' -> o.append("\\n");
                    case '\r' -> o.append("\\r");
                    case '\t' -> o.append("\\t");
                    default   -> { if (c < 0x20) o.append(String.format("\\u%04x", (int) c)); else o.append(c); }
                }
            }
            return o.toString();
        }
    }
}
