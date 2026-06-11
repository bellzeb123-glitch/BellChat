package pl.bell.bellchat.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;

public class BellChatCommand implements CommandExecutor, TabCompleter {

    private final BellChat plugin;
    private final MuteCommand      muteCmd;
    private final UnmuteCommand    unmuteCmd;
    private final ClearChatCommand clearChatCmd;
    private final ChatLockCommand  chatLockCmd;
    private final MsgSpyCommand    msgSpyCmd;

    public BellChatCommand(BellChat plugin) {
        this.plugin       = plugin;
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
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {

            case "gui", "admin" -> {
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

            // /bc lang <en|pl> — zmiana języka
            case "lang" -> {
                if (!sender.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); yield true; }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageManager().getPrefix()
                            + "§cUsage: §f/bch lang <en|pl>");
                    yield true;
                }
                String lang = args[1].toLowerCase();
                if (!lang.equals("en") && !lang.equals("pl")) {
                    sender.sendMessage(plugin.getMessageManager().getPrefix()
                            + "§cAvailable languages: §fen§c, §fpl");
                    yield true;
                }
                plugin.getConfig().set("language", lang);
                plugin.saveConfig();
                plugin.reload();
                sender.sendMessage(plugin.getMessageManager().getPrefix()
                        + "§7Language changed to: §f" + lang.toUpperCase());
                yield true;
            }

            case "mute" -> {
                if (!sender.hasPermission("bellchat.mute")) { msg.send(sender, "no-permission"); yield true; }
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
                if (!sender.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); yield true; }
                sendChannelOverview(sender);
                yield true;
            }

            default -> { sendHelp(sender); yield true; }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== BellChat v2 ===");
        sender.sendMessage("§7/msg §f<player> <msg> §8— §7Private message");
        sender.sendMessage("§7/r §f<msg> §8— §7Reply to PM");
        sender.sendMessage("§7/ignore §f<player> §8— §7Ignore/unignore");
        sender.sendMessage("§7/ch §f[channel] §8— §7Switch channel");
        if (sender.hasPermission("bellchat.admin")) {
            sender.sendMessage("§8--- §6Admin §8---");
            sender.sendMessage("§7/bc gui §8— §7Panel admina (GUI)");
            sender.sendMessage("§7/bc lang §f<en|pl> §8— §7Zmień język");
            sender.sendMessage("§7/bc mute §f<gracz> [czas] [powód]");
            sender.sendMessage("§7/bc unmute §f<gracz>");
            sender.sendMessage("§7/bc clearchat §8(/bc cc)");
            sender.sendMessage("§7/bc chatlock §8(/bc cl)");
            sender.sendMessage("§7/bc spy §8— §7Toggle spy mode");
            sender.sendMessage("§7/bc ch §8— §7Przegląd kanałów");
            sender.sendMessage("§7/bc reload §8— §7Przeładuj config");
        }
    }

    private void sendChannelOverview(CommandSender sender) {
        sender.sendMessage("§6Active channels:");
        plugin.getChannelManager().getChannels().forEach((id, ch) ->
                sender.sendMessage("  §7" + id + " §8| §f" + ch.getType().name()
                        + " §8| " + (ch.isEnabled() ? "§aactive" : "§cdisabled")));
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
            return List.of("help", "gui", "admin", "lang", "reload", "mute",
                    "unmute", "clearchat", "chatlock", "spy", "ch")
                    .stream().filter(x -> x.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("lang"))
                return List.of("en", "pl").stream()
                        .filter(x -> x.startsWith(args[1].toLowerCase())).toList();
            if (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
        }
        return List.of();
    }
}
