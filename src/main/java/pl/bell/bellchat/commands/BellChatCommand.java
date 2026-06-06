package pl.bell.bellchat.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;

public class BellChatCommand implements CommandExecutor, TabCompleter {

    private final BellChat plugin;

    public BellChatCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender); return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> {
                if (!(sender instanceof Player p)) { msg.send(sender, "player-only"); return true; }
                if (!p.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); return true; }
                plugin.getAdminGUI().open(p);
            }
            case "reload" -> {
                if (!sender.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); return true; }
                plugin.reload();
                msg.send(sender, "reload-done");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        String p = plugin.getMessageManager().getPrefix();
        sender.sendMessage(p + "&6=== BellChat ===");
        sender.sendMessage("&7/msg <player> <message> &f— Send private message");
        sender.sendMessage("&7/r <message> &f— Reply to last message");
        sender.sendMessage("&7/ignore <player> &f— Ignore/unignore a player");
        if (sender.hasPermission("bellchat.admin")) {
            sender.sendMessage("&7/mute <player> [duration] [reason] &f— Mute §8[Admin]");
            sender.sendMessage("&7/unmute <player> &f— Unmute §8[Admin]");
            sender.sendMessage("&7/clearchat &f— Clear chat §8[Admin]");
            sender.sendMessage("&7/chatlock &f— Lock/unlock chat §8[Admin]");
            sender.sendMessage("&7/bchat gui &f— Open admin panel §8[Admin]");
            sender.sendMessage("&7/bchat reload &f— Reload config §8[Admin]");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("gui", "reload", "help").stream()
                .filter(x -> x.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
