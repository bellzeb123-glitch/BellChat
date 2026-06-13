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
import java.util.UUID;

/**
 * /r (/reply) — odpowiedź na ostatnią prywatną wiadomość.
 *
 * NAPRAWY:
 * - Implementuje TabCompleter zwracający PUSTĄ listę → brak podpowiadania nicków
 *   (domyślnie Bukkit podpowiadał nazwy graczy przy każdym argumencie).
 * - Wysyła TYLKO dwie wiadomości: jedną do nadawcy (msg-format-sender),
 *   jedną do odbiorcy (msg-format-receiver). Bez dodatkowego "-> nick" duplikatu.
 */
public class ReplyCommand implements CommandExecutor, TabCompleter {

    private final BellChat plugin;

    public ReplyCommand(BellChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.getMessageManager();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(msg.getPrefix() + msg.color("&cUżycie: /r <wiadomość>"));
            return true;
        }

        // Znajdź ostatni cel odpowiedzi
        UUID targetUUID = plugin.getChatStateManager().getReplyTarget(player.getUniqueId());
        if (targetUUID == null) {
            msg.send(sender, "msg-no-reply");
            return true;
        }

        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            msg.send(sender, "msg-no-reply");
            return true;
        }

        // Sprawdź ignorowanie
        if (plugin.getIgnoreManager().isIgnoring(target.getUniqueId(), player.getUniqueId())) {
            msg.send(sender, "msg-ignored-by", Map.of("player", target.getName()));
            return true;
        }

        String message = String.join(" ", args);

        // Wiadomość do nadawcy: [PRIV] You → Target: msg
        String toSender = msg.get("msg-format-sender")
                .replace("{receiver}", target.getName())
                .replace("{message}", message);
        player.sendMessage(toSender);

        // Wiadomość do odbiorcy: [PRIV] Sender → You: msg
        String toReceiver = msg.get("msg-format-receiver")
                .replace("{sender}", player.getName())
                .replace("{message}", message);
        target.sendMessage(toReceiver);

        // Ustaw cel odpowiedzi w obie strony
        plugin.getChatStateManager().setReplyTarget(target.getUniqueId(), player.getUniqueId());
        plugin.getChatStateManager().setReplyTarget(player.getUniqueId(), target.getUniqueId());

        // Spy
        plugin.getMsgSpyManager().handle(player.getName(), target.getName(), message);

        return true;
    }

    /**
     * Pusta lista — wyłącza domyślne podpowiadanie nazw graczy.
     * /r oczekuje treści wiadomości, nie nicku.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return List.of();
    }
}
