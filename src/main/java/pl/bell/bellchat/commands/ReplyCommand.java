package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.Map;
import java.util.UUID;

/**
 * /reply (/r) — reply to last private message.
 *
 * v2.0 fixes:
 *  1. Message text is WHITE (&f) instead of gray.
 *  2. Before sending, shows "→ PlayerName" so sender knows who they're replying to.
 *  3. SPY format remains gray (unchanged — handled in MsgSpyManager).
 */
public class ReplyCommand implements CommandExecutor {

    private final BellChat plugin;

    public ReplyCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();

        if (!(sender instanceof Player player)) { msg.send(sender, "player-only"); return true; }
        if (args.length == 0) { sender.sendMessage(msg.getPrefix() + msg.color("&cUżycie: /r <wiadomość>")); return true; }

        UUID targetUUID = plugin.getChatStateManager().getReplyTarget(player.getUniqueId());
        if (targetUUID == null) { msg.send(sender, "msg-no-reply"); return true; }

        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            msg.send(sender, "msg-no-reply"); return true;
        }

        // v2.0 FIX #2: inform sender who they're replying to BEFORE the message line
        player.sendMessage(msg.color("&8→ &e" + target.getName()));

        String message = String.join(" ", args);

        String senderColor   = plugin.getLuckPermsManager().getChatColor(player);
        String receiverColor = plugin.getLuckPermsManager().getChatColor(target);

        // v2.0 FIX #1: message text is WHITE (&f)
        String toSender = msg.get("msg-format-sender")
                .replace("{receiver}", receiverColor + target.getName())
                .replace("{message}",  "&f" + message);   // WHITE
        String toReceiver = msg.get("msg-format-receiver")
                .replace("{sender}",  senderColor + player.getName())
                .replace("{message}", "&f" + message);    // WHITE

        player.sendMessage(msg.color(toSender));
        target.sendMessage(msg.color(toReceiver));

        plugin.getMsgSpyManager().handle(player.getName(), target.getName(), message);

        // Update reply target — target can now /r back
        plugin.getChatStateManager().setReplyTarget(target.getUniqueId(), player.getUniqueId());
        return true;
    }
}
