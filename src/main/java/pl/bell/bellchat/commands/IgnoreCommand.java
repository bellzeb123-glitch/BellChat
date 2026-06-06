package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class IgnoreCommand implements CommandExecutor, TabCompleter {
    private final BellChat plugin;
    IgnoreCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();
        if (!(sender instanceof Player player)) { msg.send(sender, "player-only"); return true; }
        if (args.length < 1) {
            // Show ignore list
            var ignored = plugin.getIgnoreManager().getIgnored(player.getUniqueId());
            player.sendMessage(msg.get("ignore-list-header"));
            if (ignored.isEmpty()) { player.sendMessage(msg.get("ignore-list-empty")); return true; }
            for (UUID uuid : ignored) {
                @SuppressWarnings("deprecation")
                var op = Bukkit.getOfflinePlayer(uuid);
                player.sendMessage("  &7- &f" + (op.getName() != null ? op.getName() : uuid));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { msg.send(sender, "msg-player-offline", Map.of("player", args[0])); return true; }
        if (target.equals(player)) { msg.send(sender, "ignore-self"); return true; }

        boolean nowIgnoring = plugin.getIgnoreManager().toggle(player.getUniqueId(), target.getUniqueId());
        msg.send(player, nowIgnoring ? "ignore-added" : "ignore-removed", Map.of("player", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
