package pl.bell.bellchat.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import pl.bell.bellchat.managers.UrlFilterManager;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * BellChat v2.2 — main chat listener.
 *
 * Zmiany:
 * - FIX URL filter: używamy plain text deserializacji która stripuje markdown
 *   żeby regex łapał rzeczywisty URL a nie [text](url) format
 */
@SuppressWarnings("UnstableApiUsage")
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

        Player player = event.getPlayer();

        // FIX: używamy plain text żeby rozwinąć markdown [text](url) → url
        // PlainTextComponentSerializer stripuje formatowanie i daje czysty tekst
        // włącznie z URL-ami ukrytymi w linkach markdown
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        var msg      = plugin.getMessageManager();
        var mutes    = plugin.getMuteManager();
        var antispam = plugin.getAntispamManager();
        var state    = plugin.getChatStateManager();

        // 1. Mute
        if (mutes.isMuted(player.getUniqueId())) {
            var entry = mutes.getMute(player.getUniqueId());
            if (entry.isPermanent()) {
                msg.send(player, "mute-blocked-permanent");
            } else {
                msg.send(player, "mute-blocked", Map.of("remaining", entry.getFormattedRemaining()));
            }
            return;
        }

        // 2. Chat lock
        if (state.isChatLocked() && !player.hasPermission("bellchat.chatlock.bypass")) {
            msg.send(player, "chatlock-blocked");
            return;
        }

        // 3. Antispam
        if (!player.hasPermission("bellchat.antispam.bypass")) {
            AntispamManager.SpamResult result = antispam.check(player.getUniqueId(), message);
            if (result == AntispamManager.SpamResult.COOLDOWN) {
                msg.send(player, "antispam-cooldown",
                        Map.of("seconds", String.valueOf(antispam.getCooldownSeconds())));
                return;
            }
            if (result == AntispamManager.SpamResult.DUPLICATE) {
                msg.send(player, "antispam-duplicate");
                return;
            }
        }

        // 4. Profanity filter
        message = applyProfanityFilter(message);

        // 5. URL filter — FIX: teraz message jest czystym plain textem, regex działa poprawnie
        if (!player.hasPermission("bellchat.url.bypass")) {
            UrlFilterManager.Result urlResult = plugin.getUrlFilterManager().process(message);
            if (urlResult.blocked()) {
                msg.send(player, "url-blocked");
                return;
            }
            message = urlResult.message();
        }

        // 6. Emoji
        if (player.hasPermission("bellchat.emoji")) {
            message = plugin.getEmojiManager().process(message);
        }

        // 7. Color codes
        if (player.hasPermission("bellchat.color")) {
            message = message.replace("&", "§");
        }

        // 8. Route na main thread
        final String finalMessage = message;
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getChannelManager().routeMessage(player, finalMessage);
            processMentions(player, finalMessage);
        });
    }

    // ── Mention sounds ────────────────────────────────────────

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
            Sound sound = Sound.valueOf(
                    plugin.getConfig().getString("mention.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid mention sound: " + e.getMessage());
        }
    }

    // ── Profanity filter ──────────────────────────────────────

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
