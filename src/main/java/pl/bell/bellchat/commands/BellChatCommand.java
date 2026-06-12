package pl.bell.bellchat.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;

/**
 * /bch — główna komenda admina BellChat.
 *
 * Wszystkie teksty pomocy pobierane są z MessageManager (plik językowy).
 * Hardkodowanych tekstów BRAK — wszystko respektuje ustawienie language: w config.
 */
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

            case "lang" -> {
                if (!sender.hasPermission("bellchat.admin")) { msg.send(sender, "no-permission"); yield true; }
                if (args.length < 2) {
                    msg.send(sender, "language-usage");
                    yield true;
                }
                String lang = args[1].toLowerCase();
                if (!lang.equals("en") && !lang.equals("pl")) {
                    msg.send(sender, "language-invalid");
                    yield true;
                }
                plugin.getConfig().set("language", lang);
                plugin.saveConfig();
                plugin.reload();
                msg.send(sender, "language-changed", Map.of("lang", lang.toUpperCase()));
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

    /**
     * Wyświetla pomoc używając WYŁĄCZNIE kluczy z pliku językowego.
     * Żadnych hardkodowanych tekstów — pełne respektowanie language: en|pl.
     */
    private void sendHelp(CommandSender sender) {
        var msg = plugin.getMessageManager();
        String version = plugin.getDescription().getVersion();

        // Nagłówek z wersją
        sender.sendMessage(msg.get("help-header").replace("{version}", version));

        // Komendy graczy
        sender.sendMessage(msg.get("help-msg"));
        sender.sendMessage(msg.get("help-reply"));
        sender.sendMessage(msg.get("help-ignore"));
        sender.sendMessage(msg.get("help-ch"));

        // Sekcja admina — tylko jeśli ma uprawnienia
        if (sender.hasPermission("bellchat.admin")) {
            sender.sendMessage(msg.get("help-admin-header"));
            sender.sendMessage(msg.get("help-admin-gui"));
            sender.sendMessage(msg.get("help-admin-lang"));
            sender.sendMessage(msg.get("help-admin-mute"));
            sender.sendMessage(msg.get("help-admin-unmute"));
            sender.sendMessage(msg.get("help-admin-clearchat"));
            sender.sendMessage(msg.get("help-admin-chatlock"));
            sender.sendMessage(msg.get("help-admin-spy"));
            sender.sendMessage(msg.get("help-admin-ch"));
            sender.sendMessage(msg.get("help-admin-reload"));
        }
    }

    private void sendChannelOverview(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().get("channel-overview-header"));
        plugin.getChannelManager().getChannels().forEach((id, ch) ->
                sender.sendMessage("  §7" + id + " §8| §f" + ch.getType().name()
                        + " §8| " + (ch.isEnabled() ? "§a✔" : "§c✘")));
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
