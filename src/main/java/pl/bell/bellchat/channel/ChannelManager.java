package pl.bell.bellchat.channel;

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

public class ChannelManager {

    private static final String DEFAULT_CHANNEL = "global";

    private final BellChat plugin;
    private final Logger log;

    private final Map<String, Channel> channels = new LinkedHashMap<>();
    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

    public ChannelManager(BellChat plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
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
            log.warning("[ChannelManager] Brak kanałów po załadowaniu — ładuję domyślne.");
            loadDefaults();
        } else {
            log.info("[ChannelManager] Załadowano kanały: " + channels.keySet());
        }
    }

    public void reload() {
        load();
        playerChannels.entrySet().removeIf(e -> !channels.containsKey(e.getValue()));
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
        Channel channel = getPlayerChannel(sender);
        return routeMessageToChannel(sender, channel, rawMessage);
    }

    public boolean routeMessageToChannel(Player sender, Channel channel, String rawMessage) {
        BellChatMessageEvent event = new BellChatMessageEvent(sender, channel, rawMessage);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        String message = event.getMessage();
        String formatted = buildFormat(sender, channel, message);

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

    // ── Format builder ─────────────────────────────────────────────────────────

    /**
     * Buduje finalny string wiadomości dla kanału.
     *
     * Kolory:
     *   - {message} jest zawsze BIAŁY (&f) — wymuszony z kodu
     *   - {prefix} i {player} dziedziczą kolor z formatu (np. &5 dla VIP)
     *   - prefix z LP może mieć własne kody §x — są stripowane żeby nie
     *     nadpisywały koloru zdefiniowanego w formacie kanału
     *
     * Przykład dla VIP: "&8[&5VIP&8] &5{prefix}&5{player}&5:&f {message}"
     *   → prefix LP "[VIP] " jest stripowany z §-kodów → "VIP " → malowany &5 z formatu
     *   → nick gracza "Steve" → malowany &5 z formatu  
     *   → ": " fioletowe, spacja + wiadomość biała
     */
    private String buildFormat(Player sender, Channel channel, String message) {
        var lp     = plugin.getLuckPermsManager();
        String group = lp.getPrimaryGroup(sender);

        // Pobierz prefix i suffix z LP — null-safe, strip §-kodów
        // Stripujemy §-kody żeby kolor z formatu (np. &5) faktycznie zadziałał.
        // Bez stripa: LP daje "§5[VIP] " → §5 nadpisuje &5 z formatu → OK
        // Ale jeśli LP daje "§f[VIP] " (biały prefix) → §f nadpisuje &5 → nick biały
        // Dlatego stripujemy §-kody z prefixu i pozwalamy formatowi decydować o kolorze.
        String prefix = stripSectionCodes(lp.getPrefix(sender));
        String suffix = stripSectionCodes(lp.getSuffix(sender));

        // Wybór formatu: group-formats (tylko GLOBAL) albo format kanału
        String format = channel.getFormat();
        if (channel.getType() == ChannelType.GLOBAL) {
            var groupFormats = plugin.getConfig().getConfigurationSection("group-formats");
            if (groupFormats != null && groupFormats.contains(group)) {
                format = groupFormats.getString(group, format);
            }
        }

        // Podmień placeholdery.
        // {message} → zawsze "&f" + message (biały tekst wiadomości)
        String built = format
                .replace("{prefix}",      prefix)
                .replace("{suffix}",      suffix)
                .replace("{player}",      sender.getName())
                .replace("{displayname}", sender.getName())   // displayname = nazwa (bez §-kodów)
                .replace("{channel}",     stripSectionCodes(stripAmpCodes(channel.getDisplayName())))
                .replace("{message}",     "&f" + message);

        return plugin.getMessageManager().color(built);
    }

    /** Usuwa §x kody (już wyrenderowane kolory Minecrafta). */
    private String stripSectionCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /** Usuwa &x kody (szablonowe kody kolorów). */
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
