package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;

/**
 * /msg (/tell /w /pm) — private message.
 *
 * v2.0 fix: message text is WHITE (&f) instead of the default gray.
 * SPY format remains gray — handled in MsgSpyManager.
 */
public class MsgCommand implements CommandExecutor, TabCompleter {

    private final BellChat plugin;

    public MsgCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();

        if (!(sender instanceof Player player)) { msg.send(sender, "player-only"); return true; }
        if (args.length < 2) { sender.sendMessage(msg.getPrefix() + msg.color("&cUżycie: /msg <gracz> <wiadomość>")); return true; }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            msg.send(sender, "msg-player-offline", Map.of("player", args[0])); return true;
        }
        if (target.equals(player)) { msg.send(sender, "msg-self"); return true; }

        if (plugin.getIgnoreManager().isIgnoring(player.getUniqueId(), target.getUniqueId())) {
            msg.send(sender, "msg-ignored", Map.of("player", target.getName())); return true;
        }
        if (plugin.getIgnoreManager().isIgnoring(target.getUniqueId(), player.getUniqueId())) {
            msg.send(sender, "msg-ignored-by", Map.of("player", target.getName())); return true;
        }

        String message = String.join(" ", List.of(args).subList(1, args.length));

        // v2.0: message text forced WHITE (&f) — sender/receiver name colors from LuckPerms
        String senderColor   = plugin.getLuckPermsManager().getChatColor(player);
        String receiverColor = plugin.getLuckPermsManager().getChatColor(target);

        // Replace {message} placeholder, then append &f before the actual text
        String toSender = msg.get("msg-format-sender")
                .replace("{receiver}", receiverColor + target.getName())
                .replace("{message}",  "&f" + message);   // WHITE
        String toReceiver = msg.get("msg-format-receiver")
                .replace("{sender}",  senderColor + player.getName())
                .replace("{message}", "&f" + message);    // WHITE

        player.sendMessage(msg.color(toSender));
        target.sendMessage(msg.color(toReceiver));

        plugin.getMsgSpyManager().handle(player.getName(), target.getName(), message);

        plugin.getChatStateManager().setReplyTarget(player.getUniqueId(), target.getUniqueId());
        plugin.getChatStateManager().setReplyTarget(target.getUniqueId(), player.getUniqueId());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1)
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        return List.of();
    }
}
