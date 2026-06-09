package pl.bell.bellchat.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;

/**
 * /bellchat (/bc) — admin command hub.
 *
 * v2.0: absorbs /mute, /unmute, /clearchat, /chatlock, /msgspy as subcommands.
 * These commands remain registered in plugin.yml as aliases for compatibility,
 * but players are encouraged to use /bc <sub> to know which plugin they're using.
 *
 * Subcommands:
 *  /bc help
 *  /bc gui          — open AdminGUI
 *  /bc reload       — reload config
 *  /bc mute <player> [duration] [reason]
 *  /bc unmute <player>
 *  /bc clearchat    (/bc cc)
 *  /bc chatlock     (/bc cl)
 *  /bc spy          — toggle spy mode
 *  /bc ch           — channel overview (admin)
 */
public class BellChatCommand implements CommandExecutor, TabCompleter {

    private final BellChat plugin;

    // Delegate to existing command classes — avoids duplicating logic
    private final MuteCommand muteCmd;
    private final UnmuteCommand unmuteCmd;
    private final ClearChatCommand clearChatCmd;
    private final ChatLockCommand chatLockCmd;
    private final MsgSpyCommand msgSpyCmd;

    public BellChatCommand(BellChat plugin) {
        this.plugin = plugin;
        this.muteCmd      = new MuteCommand(plugin);
        this.unmuteCmd    = new UnmuteCommand(plugin);
        this.clearChatCmd = new ClearChatCommand(plugin);
        this.chatLockCmd  = new ChatLockCommand(plugin);
        this.msgSpyCmd    = new MsgSpyCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender); return true;
        }

        return switch (args[0].toLowerCase()) {

            case "gui" -> {
                if (!(sender instanceof Player p)) { msg.send(sender, "player-only"); yield true; }
                if (!p.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); yield true; }
                plugin.getAdminGUI().open(p);
                yield true;
            }

            case "reload" -> {
                if (!sender.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); yield true; }
                plugin.reload();
                msg.send(sender, "reload-done");
                yield true;
            }

            case "mute" -> {
                if (!sender.hasPermission("bellchat.mute")) { msg.send(sender, "no-permission"); yield true; }
                // Pass sub-args (drop "mute" from front)
                muteCmd.onCommand(sender, cmd, label, dropFirst(args));
                yield true;
            }

            case "unmute" -> {
                if (!sender.hasPermission("bellchat.mute")) { msg.send(sender, "no-permission"); yield true; }
                unmuteCmd.onCommand(sender, cmd, label, dropFirst(args));
                yield true;
            }

            case "clearchat", "cc" -> {
                if (!sender.hasPermission("bellchat.clearchat")) { msg.send(sender, "no-permission"); yield true; }
                clearChatCmd.onCommand(sender, cmd, label, dropFirst(args));
                yield true;
            }

            case "chatlock", "cl" -> {
                if (!sender.hasPermission("bellchat.chatlock")) { msg.send(sender, "no-permission"); yield true; }
                chatLockCmd.onCommand(sender, cmd, label, dropFirst(args));
                yield true;
            }

            case "spy" -> {
                if (!sender.hasPermission("bellchat.spy")) { msg.send(sender, "no-permission"); yield true; }
                msgSpyCmd.onCommand(sender, cmd, label, dropFirst(args));
                yield true;
            }

            case "ch" -> {
                // Admin channel overview
                if (!sender.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); yield true; }
                sendChannelOverview(sender);
                yield true;
            }

            default -> { sendHelp(sender); yield true; }
        };
    }

    private void sendHelp(CommandSender sender) {
        String p = plugin.getMessageManager().getPrefix();
        sender.sendMessage(p + "&6=== BellChat v2.0 ===");
        sender.sendMessage("&7/msg <gracz> <wiad.> &f— Prywatna wiadomość");
        sender.sendMessage("&7/r <wiad.> &f— Odpowiedź na ostatnią PM");
        sender.sendMessage("&7/ignore <gracz> &f— Ignoruj/odignoruj");
        sender.sendMessage("&7/ch [kanał] &f— Zmień kanał czatu");
        if (sender.hasPermission("bellchat.admin")) {
            sender.sendMessage("&8--- Admin ---");
            sender.sendMessage("&7/bc mute <gracz> [czas] [powód]");
            sender.sendMessage("&7/bc unmute <gracz>");
            sender.sendMessage("&7/bc clearchat &8(/bc cc)");
            sender.sendMessage("&7/bc chatlock &8(/bc cl)");
            sender.sendMessage("&7/bc spy &f— Toggle spy mode");
            sender.sendMessage("&7/bc ch &f— Przegląd kanałów");
            sender.sendMessage("&7/bc gui &f— Panel admina");
            sender.sendMessage("&7/bc reload &f— Przeładuj config");
        }
    }

    private void sendChannelOverview(CommandSender sender) {
        String p = plugin.getMessageManager().getPrefix();
        sender.sendMessage(p + "&6Aktywne kanały:");
        plugin.getChannelManager().getChannels().forEach((id, ch) ->
                sender.sendMessage("  &7" + id + " &8| &f" + ch.getType().name()
                        + " &8| " + (ch.isEnabled() ? "&aaktywny" : "&cwył."))
        );
    }

    private String[] dropFirst(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("help", "gui", "reload", "mute", "unmute",
                    "clearchat", "chatlock", "spy", "ch");
            return subs.stream().filter(x -> x.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
