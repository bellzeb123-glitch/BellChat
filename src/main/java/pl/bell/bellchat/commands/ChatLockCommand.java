package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class ChatLockCommand implements CommandExecutor {
    private final BellChat plugin;
    ChatLockCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg   = plugin.getMessageManager();
        var state = plugin.getChatStateManager();
        if (!sender.hasPermission("bellchat.chatlock")) { msg.send(sender, "no-permission"); return true; }
        String name = sender instanceof Player p ? p.getName() : "Console";
        state.setChatLocked(!state.isChatLocked());
        String key = state.isChatLocked() ? "chatlock-locked" : "chatlock-unlocked";
        for (Player p : Bukkit.getOnlinePlayers()) msg.send(p, key, Map.of("player", name));
        return true;
    }
}

// ── /ignore ───────────────────────────────────────────────────────────────────
