package pl.bell.bellchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.managers.AntispamManager;

import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class ChatListener implements Listener {

    private final BellChat plugin;

    public ChatListener(BellChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player  = event.getPlayer();
        String message = event.getMessage();
        var msg        = plugin.getMessageManager();
        var mutes      = plugin.getMuteManager();
        var antispam   = plugin.getAntispamManager();
        var state      = plugin.getChatStateManager();

        // ── Mute check ────────────────────────────────────────
        if (mutes.isMuted(player.getUniqueId())) {
            var entry = mutes.getMute(player.getUniqueId());
            event.setCancelled(true);
            if (entry.isPermanent()) {
                msg.send(player, "mute-blocked-permanent");
            } else {
                msg.send(player, "mute-blocked",
                        Map.of("remaining", entry.getFormattedRemaining()));
            }
            return;
        }

        // ── Chat lock check ───────────────────────────────────
        if (state.isChatLocked() && !player.hasPermission("bellchat.chatlock.bypass")) {
            event.setCancelled(true);
            msg.send(player, "chatlock-blocked");
            return;
        }

        // ── Antispam check ────────────────────────────────────
        if (!player.hasPermission("bellchat.antispam.bypass")) {
            AntispamManager.SpamResult result = antispam.check(player.getUniqueId(), message);
            if (result == AntispamManager.SpamResult.COOLDOWN) {
                event.setCancelled(true);
                msg.send(player, "antispam-cooldown",
                        Map.of("seconds", String.valueOf(antispam.getCooldownSeconds())));
                return;
            }
            if (result == AntispamManager.SpamResult.DUPLICATE) {
                event.setCancelled(true);
                msg.send(player, "antispam-duplicate");
                return;
            }
        }

        // ── Profanity filter ──────────────────────────────────
        message = applyProfanityFilter(message);

        // ── Color codes ───────────────────────────────────────
        if (player.hasPermission("bellchat.color")) {
            message = msg.color(message);
        }

        // ── Build format ──────────────────────────────────────
        String format = buildFormat(player, message);

        event.setCancelled(true);

        // ── Send to recipients (respecting ignore) ────────────
        final String finalMessage = message;
        final String finalFormat  = format;

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (plugin.getIgnoreManager().isIgnoring(recipient.getUniqueId(), player.getUniqueId())) continue;
            String displayed = applyMentionHighlight(finalFormat, recipient);
            recipient.sendMessage(displayed);
            // Mention ping sound
            if (isMentioned(finalMessage, recipient) && recipient.hasPermission("bellchat.mention")) {
                playMentionSound(recipient);
            }
        }

        // Log to console
        plugin.getLogger().info("[Chat] " + player.getName() + ": " + finalMessage);
    }

    // ── Helpers ───────────────────────────────────────────────

    private String buildFormat(Player player, String message) {
        var lp = plugin.getLuckPermsManager();
        String group       = lp.getPrimaryGroup(player);
        String prefix      = lp.getPrefix(player);
        String suffix      = lp.getSuffix(player);
        String displayName = player.getDisplayName();
        String world       = player.getWorld().getName();

        // Check for group-specific format
        var groupFormats = plugin.getConfig().getConfigurationSection("group-formats");
        String format = plugin.getConfig().getString("chat-format", "{prefix}&7{displayname}&r&7: {message}");
        if (groupFormats != null && groupFormats.contains(group)) {
            format = groupFormats.getString(group, format);
        }

        return plugin.getMessageManager().color(format
                .replace("{prefix}",      prefix)
                .replace("{suffix}",      suffix)
                .replace("{name}",        player.getName())
                .replace("{displayname}", displayName)
                .replace("{world}",       world)
                .replace("{message}",     message));
    }

    private String applyProfanityFilter(String message) {
        var cfg = plugin.getConfig().getConfigurationSection("profanity-filter");
        if (cfg == null || !cfg.getBoolean("enabled", false)) return message;
        String replacement = cfg.getString("replacement", "***");
        List<String> words = cfg.getStringList("words");
        for (String word : words) {
            message = message.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), replacement);
        }
        return message;
    }

    private boolean isMentioned(String message, Player player) {
        if (!plugin.getConfig().getBoolean("mention.enabled", true)) return false;
        return message.toLowerCase().contains("@" + player.getName().toLowerCase()) ||
               message.toLowerCase().contains(player.getName().toLowerCase());
    }

    private String applyMentionHighlight(String format, Player player) {
        if (!plugin.getConfig().getBoolean("mention.enabled", true)) return format;
        String highlight = plugin.getMessageManager().color(
                plugin.getConfig().getString("mention.highlight-color", "&e@") + player.getName());
        return format.replace("@" + player.getName(), highlight);
    }

    private void playMentionSound(Player player) {
        try {
            String soundName = plugin.getConfig().getString("mention.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid mention sound: " + e.getMessage());
        }
    }
}
