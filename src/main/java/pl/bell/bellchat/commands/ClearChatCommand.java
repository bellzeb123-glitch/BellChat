package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class ClearChatCommand implements CommandExecutor {
    private final BellChat plugin;
    ClearChatCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();
        if (!sender.hasPermission("bellchat.clearchat")) { msg.send(sender, "no-permission"); return true; }
        String name = sender instanceof Player p ? p.getName() : "Console";
        String blank = "\n".repeat(100);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(blank);
            msg.send(p, "clearchat-done", Map.of("player", name));
        }
        return true;
    }
}

// ── /chatlock ─────────────────────────────────────────────────────────────────
