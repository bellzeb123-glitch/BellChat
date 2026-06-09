package pl.bell.bellchat.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.managers.AntispamManager;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BellChat v2.0 — main chat listener.
 *
 * Replaces v1.0 ChatListener (deprecated AsyncPlayerChatEvent → AsyncChatEvent).
 *
 * Preserved from v1.0: mute, chatlock, antispam, profanity filter,
 * color codes permission, @mention highlight + sound, ignore list.
 *
 * New in v2.0: routes through ChannelManager; message text forced WHITE.
 */
public class ChatListener implements Listener {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w{2,16})");

    private final BellChat plugin;

    public ChatListener(BellChat plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getChannelManager().initPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChannelManager().removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player player  = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        var msg      = plugin.getMessageManager();
        var mutes    = plugin.getMuteManager();
        var antispam = plugin.getAntispamManager();
        var state    = plugin.getChatStateManager();

        // Mute
        if (mutes.isMuted(player.getUniqueId())) {
            var entry = mutes.getMute(player.getUniqueId());
            if (entry.isPermanent()) {
                msg.send(player, "mute-blocked-permanent");
            } else {
                msg.send(player, "mute-blocked", Map.of("remaining", entry.getFormattedRemaining()));
            }
            return;
        }

        // Chat lock
        if (state.isChatLocked() && !player.hasPermission("bellchat.chatlock.bypass")) {
            msg.send(player, "chatlock-blocked");
            return;
        }

        // Antispam
        if (!player.hasPermission("bellchat.antispam.bypass")) {
            AntispamManager.SpamResult result = antispam.check(player.getUniqueId(), message);
            if (result == AntispamManager.SpamResult.COOLDOWN) {
                msg.send(player, "antispam-cooldown", Map.of("seconds", String.valueOf(antispam.getCooldownSeconds())));
                return;
            }
            if (result == AntispamManager.SpamResult.DUPLICATE) {
                msg.send(player, "antispam-duplicate");
                return;
            }
        }

        // Profanity filter
        message = applyProfanityFilter(message);

        // Color codes
        if (player.hasPermission("bellchat.color")) {
            message = msg.color(message);
        }

        // Route on main thread (LuckPerms + events need sync context)
        final String finalMessage = message;
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getChannelManager().routeMessage(player, finalMessage);
            processMentions(player, finalMessage);
        });
    }

    private void processMentions(Player sender, String message) {
        if (!plugin.getConfig().getBoolean("mention.enabled", true)) return;
        var matcher = MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            String name = matcher.group(1);
            Player mentioned = Bukkit.getPlayerExact(name);
            if (mentioned == null || mentioned.equals(sender)) continue;
            if (!mentioned.hasPermission("bellchat.mention")) continue;
            playMentionSound(mentioned);
        }
    }

    private void playMentionSound(Player player) {
        try {
            Sound sound = Sound.valueOf(plugin.getConfig().getString("mention.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid mention sound: " + e.getMessage());
        }
    }

    private String applyProfanityFilter(String message) {
        var cfg = plugin.getConfig().getConfigurationSection("profanity-filter");
        if (cfg == null || !cfg.getBoolean("enabled", false)) return message;
        String replacement = cfg.getString("replacement", "***");
        for (String word : cfg.getStringList("words")) {
            message = message.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), replacement);
        }
        return message;
    }
}
