package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UnmuteCommand implements CommandExecutor, TabCompleter {
    private final BellChat plugin;
    public UnmuteCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();
        if (!sender.hasPermission("bellchat.mute")) { msg.send(sender, "no-permission"); return true; }
        if (args.length < 1) { sender.sendMessage(msg.getPrefix() + "&cUsage: /unmute <player>"); return true; }

        @SuppressWarnings("deprecation")
        var target = Bukkit.getOfflinePlayer(args[0]);
        if (!plugin.getMuteManager().isMuted(target.getUniqueId())) {
            msg.send(sender, "unmute-not-muted", Map.of("player", args[0])); return true;
        }
        plugin.getMuteManager().unmute(target.getUniqueId());
        msg.send(sender, "unmute-success", Map.of("player", args[0]));

        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) online.sendMessage(msg.getPrefix() + msg.get("unmute-success").replace("{player}", "you"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return plugin.getMuteManager().getAllMutes().values().stream()
                .map(e -> e.getPlayerName())
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}

// ── /clearchat ────────────────────────────────────────────────────────────────
