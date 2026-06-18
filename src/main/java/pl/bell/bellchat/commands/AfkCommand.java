package pl.bell.bellchat.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;

/**
 * /afk — ręczne przełączanie statusu AFK.
 *
 * TabCompleter zwraca pustą listę → brak podpowiadania nicków
 * (komenda nie przyjmuje argumentów).
 */
public class AfkCommand implements CommandExecutor, TabCompleter {

    private final BellChat plugin;

    public AfkCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "player-only");
            return true;
        }
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) {
            plugin.getMessageManager().send(player, "afk-disabled");
            return true;
        }
        plugin.getAfkManager().toggle(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return List.of();
    }
}
