package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class MuteCommand implements CommandExecutor, TabCompleter {
    private final BellChat plugin;
    MuteCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();
        if (!sender.hasPermission("bellchat.mute")) { msg.send(sender, "no-permission"); return true; }
        if (args.length < 1) { sender.sendMessage(msg.getPrefix() + "&cUsage: /mute <player> [duration] [reason]"); return true; }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { msg.send(sender, "msg-player-offline", Map.of("player", args[0])); return true; }

        if (plugin.getMuteManager().isMuted(target.getUniqueId())) {
            msg.send(sender, "mute-already", Map.of("player", target.getName())); return true;
        }

        long duration = args.length >= 2 ? pl.bell.bellchat.managers.MuteManager.parseDuration(args[1]) : -1;
        String reason = args.length >= 3 ? String.join(" ", List.of(args).subList(2, args.length)) : "No reason specified";
        String by     = sender instanceof Player p ? p.getName() : "Console";

        plugin.getMuteManager().mute(target.getUniqueId(), target.getName(), duration, reason, by);

        String durationStr = pl.bell.bellchat.managers.MuteManager.formatDuration(duration);
        if (duration == -1) {
            msg.send(sender, "mute-permanent", Map.of("player", target.getName(), "reason", reason));
            msg.send(target, "mute-notify-permanent");
        } else {
            msg.send(sender, "mute-success", Map.of("player", target.getName(), "duration", durationStr, "reason", reason));
            msg.send(target, "mute-notify", Map.of("duration", durationStr, "reason", reason));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2) return List.of("30s", "5m", "1h", "1d", "7d", "perm");
        return List.of();
    }
}

// ── /unmute ───────────────────────────────────────────────────────────────────
