package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReplyCommand implements CommandExecutor {
    private final BellChat plugin;
    ReplyCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();
        if (!(sender instanceof Player player)) { msg.send(sender, "player-only"); return true; }
        if (args.length == 0) { sender.sendMessage(msg.getPrefix() + "&cUsage: /r <message>"); return true; }

        UUID targetUUID = plugin.getChatStateManager().getReplyTarget(player.getUniqueId());
        if (targetUUID == null) { msg.send(sender, "msg-no-reply"); return true; }

        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null) { msg.send(sender, "msg-no-reply"); return true; }

        String message   = String.join(" ", args);
        player.sendMessage(msg.get("msg-format-sender")
                .replace("{receiver}", target.getName()).replace("{message}", message));
        target.sendMessage(msg.get("msg-format-receiver")
                .replace("{sender}", player.getName()).replace("{message}", message));
        plugin.getMsgSpyManager().handle(player.getName(), target.getName(), message);
        plugin.getChatStateManager().setReplyTarget(target.getUniqueId(), player.getUniqueId());
        return true;
    }
}

// ── /mute ─────────────────────────────────────────────────────────────────────
