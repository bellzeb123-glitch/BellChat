package pl.bell.bellchat.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

public class MsgSpyCommand implements CommandExecutor {

    private final BellChat plugin;

    public MsgSpyCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();
        if (!(sender instanceof Player player)) { msg.send(sender, "player-only"); return true; }
        if (!player.hasPermission("bellchat.spy")) { msg.send(sender, "no-permission"); return true; }

        boolean nowEnabled = plugin.getMsgSpyManager().toggle(player.getUniqueId());
        if (nowEnabled) {
            player.sendMessage(msg.getPrefix() + msg.color("&aSpy mode &2enabled&a. You will see all private messages."));
        } else {
            player.sendMessage(msg.getPrefix() + msg.color("&cSpy mode &4disabled&c."));
        }
        return true;
    }
}
