package pl.bell.bellchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.channel.Channel;
import pl.bell.bellchat.channel.ChannelType;

import java.util.*;

/**
 * /ch — channel switcher.
 *
 * Usage:
 *  /ch              — show current channel
 *  /ch list         — list available channels
 *  /ch <name>       — switch to channel
 *  /ch <name> <msg> — send one message to channel without switching
 */
public class ChannelCommand implements CommandExecutor, TabCompleter {

    private final BellChat plugin;

    public ChannelCommand(BellChat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /ch.");
            return true;
        }

        var msg = plugin.getMessageManager();

        if (args.length == 0) {
            Channel current = plugin.getChannelManager().getPlayerChannel(player);
            msg.send(player, "channel-current", Map.of("channel", msg.color(current.getDisplayName())));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sendList(player);
            return true;
        }

        String channelId = args[0].toLowerCase();

        // /ch <name> <message> — one-shot send without switching
        if (args.length > 1) {
            plugin.getChannelManager().getChannel(channelId).ifPresentOrElse(ch -> {
                if (ch.requiresPermission() && !player.hasPermission(ch.getRequiredPermission())) {
                    msg.send(player, "no-permission");
                    return;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.getChannelManager().routeMessageToChannel(player, ch, message);
            }, () -> msg.send(player, "channel-not-found", Map.of("channel", channelId)));
            return true;
        }

        // /ch <name> — switch
        plugin.getChannelManager().switchChannel(player, channelId);
        return true;
    }

    private void sendList(Player player) {
        var msg = plugin.getMessageManager();
        Channel current = plugin.getChannelManager().getPlayerChannel(player);
        player.sendMessage(msg.color(msg.getPrefix() + "&7Dostępne kanały:"));

        plugin.getChannelManager().getChannels().values().stream()
                .filter(Channel::isEnabled)
                .filter(ch -> ch.getType() != ChannelType.PARTY)
                .forEach(ch -> {
                    if (ch.requiresPermission() && !player.hasPermission(ch.getRequiredPermission())) return;
                    boolean active = ch.getId().equals(current.getId());
                    String marker = active ? "&a► " : "&7● ";
                    String suffix = active ? " &a← aktualny" : "";
                    player.sendMessage(msg.color("  " + marker + ch.getDisplayName()
                            + " &8(&7/ch " + ch.getId() + "&8)" + suffix));
                });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>(List.of("list"));
            plugin.getChannelManager().getChannels().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .forEach(completions::add);
            return completions;
        }
        return List.of();
    }
}
