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

    /** All defined channels. LinkedHashMap keeps config order. */
    private final Map<String, Channel> channels = new LinkedHashMap<>();

    /**
     * Player → current channel id.
     * ConcurrentHashMap: channel switches can happen from AsyncChatEvent thread.
     */
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
                log.warning("[ChannelManager] Nieznany typ kanału '" + ch.getString("type") + "' dla '" + key + "', pomijam.");
                continue;
            }

            channels.put(key.toLowerCase(), new Channel(
                    key.toLowerCase(),
                    type,
                    ch.getString("display-name", "&f" + key),
                    ch.getString("format", "{prefix} {player}&7: {message}"),
                    ch.getInt("local-radius", -1),
                    ch.getString("required-permission", null),
                    ch.getBoolean("enabled", true)
            ));
        }

        if (channels.isEmpty()) {
            log.warning("[ChannelManager] Żadne kanały nie zostały załadowane — ładuję domyślne.");
            loadDefaults();
        } else {
            log.info("[ChannelManager] Załadowano kanały: " + channels.keySet());
        }
    }

    public void reload() {
        load();
        // Gracze będący na kanale który zniknął — cofają się do global
        playerChannels.entrySet().removeIf(e -> !channels.containsKey(e.getValue()));
    }

    private void loadDefaults() {
        channels.put("global", new Channel("global", ChannelType.GLOBAL,
                "&aGlobal", "{prefix} {player}&7: {message}", -1, null, true));
        channels.put("local", new Channel("local", ChannelType.LOCAL,
                "&eLocal", "&7[Local] {prefix} {player}&7: {message}", 100, null, true));
        channels.put("vip", new Channel("vip", ChannelType.VIP,
                "&6VIP", "&6[VIP] {prefix} {player}&7: {message}", -1, "bellchat.channel.vip", true));
        channels.put("admin", new Channel("admin", ChannelType.ADMIN,
                "&cAdmin", "&c[Admin] {prefix} {player}&7: {message}", -1, "bellchat.channel.admin", true));
    }

    // ── Player ↔ channel ───────────────────────────────────────────────────────

    public void initPlayer(UUID uuid) {
        playerChannels.putIfAbsent(uuid, DEFAULT_CHANNEL);
    }

    public void removePlayer(UUID uuid) {
        playerChannels.remove(uuid);
    }

    /** Returns the player's current channel. Falls back to global safely. */
    public Channel getPlayerChannel(Player player) {
        String id = playerChannels.getOrDefault(player.getUniqueId(), DEFAULT_CHANNEL);
        Channel ch = channels.get(id);
        if (ch == null || !ch.isEnabled()) {
            return channels.getOrDefault(DEFAULT_CHANNEL, channels.values().iterator().next());
        }
        return ch;
    }

    /**
     * Switches a player to the given channel.
     * Fires BellChatChannelSwitchEvent (cancellable).
     * Sends feedback via MessageManager using existing lang keys.
     */
    public boolean switchChannel(Player player, String channelId) {
        Channel target = channels.get(channelId.toLowerCase());
        if (target == null) {
            plugin.getMessageManager().send(player, "channel-not-found",
                    Map.of("channel", channelId));
            return false;
        }
        if (!target.isEnabled()) {
            plugin.getMessageManager().send(player, "channel-disabled",
                    Map.of("channel", target.getDisplayName()));
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

    /**
     * Routes a message from player through their current channel.
     * Called from ChatListener (main thread).
     * Fires BellChatMessageEvent (cancellable).
     */
    public boolean routeMessage(Player sender, String rawMessage) {
        Channel channel = getPlayerChannel(sender);
        return routeMessageToChannel(sender, channel, rawMessage);
    }

    /**
     * Routes a message to a specific channel (for /ch global <msg> one-shot sends).
     */
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
     * Builds the final chat string for a channel message.
     *
     * Message text is WHITE (&f) — this is the v2.0 fix.
     * Channel/prefix/player colors come from the format template.
     * SPY messages are formatted separately (gray) in MsgSpyManager.
     */
    private String buildFormat(Player sender, Channel channel, String message) {
        var lp = plugin.getLuckPermsManager();
        String prefix      = lp.getPrefix(sender);
        String suffix      = lp.getSuffix(sender);
        String group       = lp.getPrimaryGroup(sender);
        String displayName = sender.getDisplayName();

        // Group-specific format override (same logic as old ChatListener)
        String format = channel.getFormat();
        var groupFormats = plugin.getConfig().getConfigurationSection("group-formats");
        if (groupFormats != null && groupFormats.contains(group)) {
            // Use group format only for GLOBAL channel, others keep their own format
            if (channel.getType() == ChannelType.GLOBAL) {
                format = groupFormats.getString(group, format);
                // Inject message placeholder if group format uses {message}
                // Message forced to WHITE below
            }
        }

        // Replace placeholders — message is forced &f (WHITE)
        String built = format
                .replace("{prefix}",      prefix)
                .replace("{suffix}",      suffix)
                .replace("{player}",      sender.getName())
                .replace("{displayname}", displayName)
                .replace("{channel}",     stripColor(channel.getDisplayName()))
                .replace("{message}",     "&f" + message);  // WHITE — v2.0 fix

        return plugin.getMessageManager().color(built);
    }

    private String stripColor(String s) {
        return s.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    // ── Party management ───────────────────────────────────────────────────────

    public Channel createParty(Player owner) {
        String partyId = "party_" + owner.getUniqueId().toString().substring(0, 8);
        Channel party = new Channel(partyId, ChannelType.PARTY,
                "&b" + owner.getName() + "'s party",
                "&b[Party] {prefix} {player}&f: {message}",
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

    public Map<String, Channel> getChannels()          { return Collections.unmodifiableMap(channels); }
    public Optional<Channel> getChannel(String id)     { return Optional.ofNullable(channels.get(id.toLowerCase())); }
}
