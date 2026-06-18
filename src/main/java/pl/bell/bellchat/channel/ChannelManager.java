package pl.bell.bellchat.channel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.event.BellChatChannelSwitchEvent;
import pl.bell.bellchat.event.BellChatMessageEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelManager {

    private static final String DEFAULT_CHANNEL = "global";

    // Regex do wyodrębnienia {player} lub {displayname} z formatu
    // Używamy go żeby wiedzieć gdzie wstawić klikalny komponent nicka
    private static final Pattern PLAYER_PLACEHOLDER = Pattern.compile("\\{player}|\\{displayname}");

    // Serializer czatu z obsługą kolorów HEX (format &x&R&R&G&G&B&B — Bungee/Essentials).
    // Dzięki temu addon BellChatPro może wstrzykiwać kolory HEX/gradienty przez
    // BellChatMessageEvent.setMessage(...). W pełni kompatybilny wstecz ze zwykłymi &-kodami.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final BellChat plugin;
    private final Logger log;

    private final Map<String, Channel> channels = new LinkedHashMap<>();
    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

    public ChannelManager(BellChat plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── Load / reload ──────────────────────────────────────────────────────────

    public void load() {
        channels.clear();

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("channels");
        if (sec == null) {
            log.warning("[ChannelManager] Brak sekcji 'channels' w config.yml — ładuję domyślne.");
            loadDefaults();
            return;
        }

        for (String key : sec.getKeys(false)) {
            ConfigurationSection ch = sec.getConfigurationSection(key);
            if (ch == null) continue;

            ChannelType type;
            try {
                type = ChannelType.valueOf(ch.getString("type", "GLOBAL").toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warning("[ChannelManager] Nieznany typ '" + ch.getString("type") + "' dla '" + key + "', pomijam.");
                continue;
            }

            channels.put(key.toLowerCase(), new Channel(
                    key.toLowerCase(),
                    type,
                    ch.getString("display-name", "&f" + key),
                    ch.getString("format", "&f{player}: {message}"),
                    ch.getInt("local-radius", -1),
                    ch.getString("required-permission", null),
                    ch.getBoolean("enabled", true)
            ));
        }

        if (channels.isEmpty()) {
            log.warning("[ChannelManager] Brak kanałów — ładuję domyślne.");
            loadDefaults();
        } else {
            log.info("[ChannelManager] Załadowano kanały: " + channels.keySet());
        }
    }

    public void reload() {
        load();
        playerChannels.entrySet().removeIf(e -> !channels.containsKey(e.getValue()));
    }

    /**
     * Włącza/wyłącza kanał z poziomu admina (GUI).
     *
     * - Zapisuje stan do config.yml (channels.<id>.enabled) → trwałe po restarcie.
     * - Po WYŁĄCZENIU: każdy gracz aktualnie na tym kanale jest natychmiast
     *   przenoszony z powrotem na kanał domyślny (global) i dostaje komunikat.
     *   Dzięki temu nikt nie zostaje "uwięziony" na wyłączonym kanale.
     *
     * Kanału domyślnego (global) nie da się wyłączyć — to fallback dla wszystkich.
     *
     * @return false jeśli kanał nie istnieje lub jest to kanał domyślny.
     */
    public boolean setChannelEnabled(String id, boolean enabled) {
        Channel ch = channels.get(id.toLowerCase());
        if (ch == null) return false;
        if (ch.getId().equals(DEFAULT_CHANNEL)) return false; // global zawsze włączony

        ch.setEnabled(enabled);
        plugin.getConfig().set("channels." + ch.getId() + ".enabled", enabled);
        plugin.saveConfig();

        if (!enabled) {
            for (Map.Entry<UUID, String> e : playerChannels.entrySet()) {
                if (!e.getValue().equals(ch.getId())) continue;
                e.setValue(DEFAULT_CHANNEL);
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    plugin.getMessageManager().send(p, "channel-disabled-moved",
                            Map.of("channel", plugin.getMessageManager().color(ch.getDisplayName())));
                }
            }
        }
        return true;
    }

    /** Czy kanał o danym id to kanał domyślny (global), którego nie wolno wyłączyć. */
    public boolean isDefaultChannel(String id) {
        return DEFAULT_CHANNEL.equalsIgnoreCase(id);
    }

    private void loadDefaults() {
        channels.put("global", new Channel("global", ChannelType.GLOBAL,
                "&aGlobal", "&f{player}&f: {message}", -1, null, true));
        channels.put("local", new Channel("local", ChannelType.LOCAL,
                "&eLocal", "&8[&eL&8] &f{player}&f: {message}", 100, null, true));
        channels.put("vip", new Channel("vip", ChannelType.VIP,
                "&5VIP", "&8[&5VIP&8] &5{prefix}&5{player}&5:&f {message}", -1, "bellchat.channel.vip", true));
        channels.put("admin", new Channel("admin", ChannelType.ADMIN,
                "&cAdmin", "&8[&cADM&8] &6{prefix}&6{player}&6:&f {message}", -1, "bellchat.channel.admin", true));
    }

    // ── Player ↔ channel ───────────────────────────────────────────────────────

    public void initPlayer(UUID uuid) {
        playerChannels.putIfAbsent(uuid, DEFAULT_CHANNEL);
    }

    public void removePlayer(UUID uuid) {
        playerChannels.remove(uuid);
    }

    public Channel getPlayerChannel(Player player) {
        String id = playerChannels.getOrDefault(player.getUniqueId(), DEFAULT_CHANNEL);
        Channel ch = channels.get(id);
        if (ch == null || !ch.isEnabled()) {
            return channels.getOrDefault(DEFAULT_CHANNEL, channels.values().iterator().next());
        }
        return ch;
    }

    public boolean switchChannel(Player player, String channelId) {
        Channel target = channels.get(channelId.toLowerCase());
        if (target == null) {
            plugin.getMessageManager().send(player, "channel-not-found", Map.of("channel", channelId));
            return false;
        }
        if (!target.isEnabled()) {
            plugin.getMessageManager().send(player, "channel-disabled", Map.of("channel", target.getDisplayName()));
            return false;
        }
        if (target.requiresPermission() && !player.hasPermission(target.getRequiredPermission())) {
            plugin.getMessageManager().send(player, "no-permission");
            return false;
        }
        if (target.getType() == ChannelType.PARTY && !target.hasMember(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "channel-party-not-member");
            return false;
        }

        Channel previous = getPlayerChannel(player);
        BellChatChannelSwitchEvent event = new BellChatChannelSwitchEvent(player, previous, target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        playerChannels.put(player.getUniqueId(), target.getId());
        plugin.getMessageManager().send(player, "channel-switched",
                Map.of("channel", plugin.getMessageManager().color(target.getDisplayName())));
        return true;
    }

    // ── Message routing ────────────────────────────────────────────────────────

    public boolean routeMessage(Player sender, String rawMessage) {
        return routeMessageToChannel(sender, getPlayerChannel(sender), rawMessage);
    }

    public boolean routeMessageToChannel(Player sender, Channel channel, String rawMessage) {
        BellChatMessageEvent event = new BellChatMessageEvent(sender, channel, rawMessage);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        String message = event.getMessage();

        // Buduj Component z hover/click na nicku
        Component formatted = buildComponent(sender, channel, message);

        for (Player recipient : resolveRecipients(sender, channel)) {
            if (plugin.getIgnoreManager().isIgnoring(recipient.getUniqueId(), sender.getUniqueId())) continue;
            recipient.sendMessage(formatted);
        }

        log.info("[" + channel.getId().toUpperCase() + "] " + sender.getName() + ": " + message);
        return true;
    }

    // ── Recipients ─────────────────────────────────────────────────────────────

    public Collection<? extends Player> resolveRecipients(Player sender, Channel channel) {
        return switch (channel.getType()) {
            case GLOBAL, ADMIN -> Bukkit.getOnlinePlayers();
            case LOCAL -> {
                int radius = channel.getLocalRadius();
                if (radius <= 0) yield Bukkit.getOnlinePlayers();
                Location loc = sender.getLocation();
                yield Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getWorld().equals(loc.getWorld())
                                && p.getLocation().distanceSquared(loc) <= (long) radius * radius)
                        .toList();
            }
            case VIP -> Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("bellchat.channel.vip"))
                    .toList();
            case PARTY -> Bukkit.getOnlinePlayers().stream()
                    .filter(p -> channel.hasMember(p.getUniqueId()))
                    .toList();
        };
    }

    // ── Component builder (hover + click) ──────────────────────────────────────

    /**
     * Buduje Adventure Component dla wiadomości czatowej.
     *
     * Jeśli hover-click włączony w config (chat.hover-click.enabled: true):
     *   - Nick gracza → klikalny komponent z hover (ranga + podpowiedź)
     *   - Klik lewym przyciskiem → sugeruje /msg <nick>
     *
     * Jeśli wyłączony → zwraca zwykły legacy string jako Component.
     *
     * Podział formatu:
     *   "PRZED {player} PO {message}"
     *   → Component: [PRZED][NICK_Z_HOVER][PO][WIADOMOŚĆ]
     */
    private Component buildComponent(Player sender, Channel channel, String message) {
        var lp     = plugin.getLuckPermsManager();

        // FIX: NIE stripujemy koloru z LP prefix.
        // Wcześniej stripSectionCodes(lp.getPrefix()) usuwał §-kody z LP,
        // przez co w formacie "&5{prefix}" hardcoded &5 nadkładał kolor.
        // Teraz zachowujemy oryginalny kolor z LP (np. "§5[VIP] ").
        String rawLpPrefix = lp.getPrefix(sender);
        String prefix      = rawLpPrefix != null ? rawLpPrefix : "";
        String suffix      = lp.getSuffix(sender);
        if (suffix == null) suffix = "";
        String group       = lp.getPrimaryGroup(sender);

        // Wyciągnij kolor LP — używany dla {lp-color} placeholdera.
        // Dzięki temu config może być uniwersalny:
        //   format: "{prefix}{lp-color}{player}{lp-color}: &f{message}"
        // i każda grupa LP dostaje swój własny kolor automatycznie.
        String lpColor = extractLastColorCode(prefix);

        // Wybór formatu (group-formats tylko dla GLOBAL)
        String format = channel.getFormat();
        if (channel.getType() == ChannelType.GLOBAL) {
            var groupFormats = plugin.getConfig().getConfigurationSection("group-formats");
            if (groupFormats != null && groupFormats.contains(group)) {
                format = groupFormats.getString(group, format);
            }
        }

        // Podmień wszystkie placeholdery OPRÓCZ {player}/{displayname}
        String withPlaceholders = format
                .replace("{prefix}",   prefix)
                .replace("{suffix}",   suffix)
                .replace("{lp-color}", lpColor)
                .replace("{channel}",  stripSectionCodes(stripAmpCodes(channel.getDisplayName())))
                .replace("{message}",  "&f" + message);

        // Sprawdź czy hover/click jest włączony
        boolean hoverEnabled = plugin.getConfig().getBoolean("chat.hover-click.enabled", true);

        if (!hoverEnabled || !PLAYER_PLACEHOLDER.matcher(withPlaceholders).find()) {
            // Brak {player} w formacie lub hover wyłączony — zwróć zwykły string
            String full = withPlaceholders
                    .replace("{player}",      sender.getName())
                    .replace("{displayname}", sender.getName());
            return withAfkTag(sender, LEGACY.deserialize(full));
        }

        // Podziel format na część PRZED nickiem i PO nicku
        Matcher m = PLAYER_PLACEHOLDER.matcher(withPlaceholders);
        if (!m.find()) {
            // Fallback
            return withAfkTag(sender, LEGACY
                    .deserialize(withPlaceholders.replace("{player}", sender.getName())));
        }

        String beforePlayer = withPlaceholders.substring(0, m.start());
        String afterPlayer  = withPlaceholders.substring(m.end());

        // Zbuduj komponent nicku z hover + click
        Component nickComponent = buildNickComponent(sender, group, format, beforePlayer, lpColor);

        // Złącz wszystko
        return withAfkTag(sender, LEGACY.deserialize(beforePlayer)
                .append(nickComponent)
                .append(LEGACY.deserialize(afterPlayer)));
    }

    /**
     * Dokleja znacznik [AFK] na początku wiadomości (przed nickiem),
     * jeśli nadawca jest oznaczony jako AFK.
     * Kolor z config (afk.chat-prefix) — domyślnie szary, by nie kolidować
     * z kolorami rang (żółty/niebieski/fioletowy).
     */
    private Component withAfkTag(Player sender, Component base) {
        if (plugin.getAfkManager() == null
                || !plugin.getAfkManager().isAfk(sender.getUniqueId())) {
            return base;
        }
        String tag = plugin.getConfig().getString("afk.chat-prefix", "&7☾ AFK ");
        return LEGACY.deserialize(tag).append(base);
    }

    /**
     * Buduje klikalny komponent nicku gracza.
     *
     * Hover pokazuje:
     *   ──────────────
     *   ✦ Ranga: VIP
     *   ⚔ Kliknij aby napisać
     *   ──────────────
     *
     * Klik: sugeruje /msg <nick> (gracz musi tylko dopisać wiadomość)
     */
    private Component buildNickComponent(Player sender, String group, String format,
                                         String beforePlayer, String lpColor) {
        // Wyciągnij kolor nicku z formatu (& kod przed {player}).
        // Jeśli format używa "{lp-color}{player}" → kolor pochodzi z LP.
        // Jeśli format ma hardcoded "&5{player}" → kolor jest fioletowy.
        String nickColorCode;
        // Sprawdź czy bezpośrednio przed {player} jest {lp-color}
        int playerIdx = format.indexOf("{player}");
        if (playerIdx < 0) playerIdx = format.indexOf("{displayname}");
        if (playerIdx > 0 && format.substring(0, playerIdx).endsWith("{lp-color}")) {
            nickColorCode = lpColor;
        } else {
            nickColorCode = extractColorBeforePlayer(format);
        }

        Component nickText = LEGACY
                .deserialize(nickColorCode + sender.getName());

        // Zbuduj hover
        String rankDisplay = formatGroupName(group);
        String hoverLine1  = plugin.getConfig().getString(
                "chat.hover-click.hover-line1", "&7✦ Ranga: &f{rank}");
        String hoverLine2  = plugin.getConfig().getString(
                "chat.hover-click.hover-line2", "&7⚔ &7Kliknij aby napisać");
        String separator   = plugin.getConfig().getString(
                "chat.hover-click.separator", "&8──────────────");

        Component hoverComponent = LEGACY
                .deserialize(separator)
                .append(Component.newline())
                .append(LEGACY
                        .deserialize(hoverLine1.replace("{rank}", rankDisplay)))
                .append(Component.newline())
                .append(LEGACY
                        .deserialize(hoverLine2))
                .append(Component.newline())
                .append(LEGACY
                        .deserialize(separator));

        // Click: /msg <nick> (suggest — gracz musi dopisać treść)
        String clickCommand = plugin.getConfig().getString(
                "chat.hover-click.click-command", "/msg {player} ");

        return nickText
                .hoverEvent(HoverEvent.showText(hoverComponent))
                .clickEvent(ClickEvent.suggestCommand(
                        clickCommand.replace("{player}", sender.getName())));
    }

    /**
     * Wyciąga ostatni kod koloru (§ lub &) z LP prefix.
     * Zwracany jako string w formacie & — np. "&5" dla fioletowego.
     *
     * Dlaczego: LP prefix może mieć kolor np. "§5[VIP] " albo "&5[VIP] ".
     * Wyciągamy ten kolor, by w formacie czatu nick gracza miał taki sam
     * kolor jak jego prefix LP — bez hardcoded wartości w config.
     *
     * Pomija kody formatowania (l/o/n/m/k) — bierze tylko prawdziwe kolory (0-9, a-f).
     * Jeśli prefix nie ma żadnego koloru → zwraca "&f" (biały).
     */
    private String extractLastColorCode(String s) {
        if (s == null || s.isEmpty()) return "&f";
        String lastColor = "&f";
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '§' || c == '&') {
                char code = Character.toLowerCase(s.charAt(i + 1));
                // Tylko kolory, nie formatowania
                if ("0123456789abcdef".indexOf(code) >= 0) {
                    lastColor = "&" + code;
                }
            }
        }
        return lastColor;
    }

    /**
     * Wyciąga ostatni kod koloru/formatu (&x) bezpośrednio przed {player} w formacie.
     * Np. "&5{prefix}&5{player}" → "&5"
     * Jeśli brak kodu — zwraca "&f" (biały jako fallback).
     */
    private String extractColorBeforePlayer(String format) {
        // Znajdź pozycję {player} lub {displayname}
        int idx = format.indexOf("{player}");
        if (idx < 0) idx = format.indexOf("{displayname}");
        if (idx < 0) return "&f";

        // Szukaj wstecz kodu &x (dokładnie 2 znaki)
        String before = format.substring(0, idx);
        // Zbierz wszystkie kolejne kody &x na końcu stringa 'before'
        StringBuilder codes = new StringBuilder();
        int i = before.length() - 2;
        while (i >= 0) {
            if (before.charAt(i) == '&' && i + 1 < before.length()) {
                char code = before.charAt(i + 1);
                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(code) >= 0) {
                    codes.insert(0, "&" + code);
                    i -= 2;
                    continue;
                }
            }
            break;
        }
        return codes.isEmpty() ? "&f" : codes.toString();
    }

    /** Formatuje nazwę grupy LP na ładny wyświetlany string (pierwsza litera wielka). */
    private String formatGroupName(String group) {
        if (group == null || group.isBlank()) return "Gracz";
        return Character.toUpperCase(group.charAt(0)) + group.substring(1).toLowerCase();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String stripSectionCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private String stripAmpCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    // ── Party management ───────────────────────────────────────────────────────

    public Channel createParty(Player owner) {
        String partyId = "party_" + owner.getUniqueId().toString().substring(0, 8);
        Channel party = new Channel(partyId, ChannelType.PARTY,
                "&b" + owner.getName() + "'s party",
                "&8[&bP&8] &f{player}&f: {message}",
                -1, null, true);
        party.setOwner(owner.getUniqueId());
        channels.put(partyId, party);
        return party;
    }

    public void disbandParty(String partyId) {
        Channel party = channels.remove(partyId);
        if (party == null) return;
        for (UUID uuid : party.getMembers()) {
            playerChannels.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) plugin.getMessageManager().send(p, "channel-party-disbanded");
        }
    }

    // ── Read access ────────────────────────────────────────────────────────────

    public Map<String, Channel> getChannels()      { return Collections.unmodifiableMap(channels); }
    public Optional<Channel> getChannel(String id) { return Optional.ofNullable(channels.get(id.toLowerCase())); }
}
