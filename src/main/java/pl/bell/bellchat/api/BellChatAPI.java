package pl.bell.bellchat.api;

import org.bukkit.entity.Player;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.channel.Channel;
import pl.bell.bellchat.channel.ChannelManager;

/**
 * Public API for BellChat. Access via {@link BellChatAPI#get()}.
 *
 * Usage from external plugin:
 * <pre>
 *   Bukkit.getScheduler().runTask(this, () -> {
 *       if (!BellChatAPI.isReady()) return;
 *       BellChatAPI.get().switchChannel(player, "vip");
 *   });
 * </pre>
 */
public class BellChatAPI {

    private static BellChatAPI instance;
    private final BellChat plugin;

    private BellChatAPI(BellChat plugin) { this.plugin = plugin; }

    public static void init(BellChat plugin)  { instance = new BellChatAPI(plugin); }
    public static void shutdown()             { instance = null; }
    public static boolean isReady()           { return instance != null; }

    public static BellChatAPI get() {
        if (instance == null) throw new IllegalStateException("BellChatAPI not initialized.");
        return instance;
    }

    public ChannelManager getChannelManager()               { return plugin.getChannelManager(); }
    public Channel getPlayerChannel(Player player)          { return plugin.getChannelManager().getPlayerChannel(player); }
    public boolean switchChannel(Player player, String id)  { return plugin.getChannelManager().switchChannel(player, id); }
    public boolean isMuted(Player player)                   { return plugin.getMuteManager().isMuted(player.getUniqueId()); }
}
