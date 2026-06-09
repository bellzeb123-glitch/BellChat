package pl.bell.bellchat;

import org.bukkit.plugin.java.JavaPlugin;
import pl.bell.bellchat.api.BellChatAPI;
import pl.bell.bellchat.channel.ChannelManager;
import pl.bell.bellchat.commands.*;
import pl.bell.bellchat.gui.AdminGUI;
import pl.bell.bellchat.listeners.ChatListener;
import pl.bell.bellchat.listeners.VIPJoinListener;
import pl.bell.bellchat.managers.*;

public class BellChat extends JavaPlugin {

    private static BellChat instance;

    // v1.0 managers — unchanged
    private MessageManager messageManager;
    private LuckPermsManager luckPermsManager;
    private MuteManager muteManager;
    private IgnoreManager ignoreManager;
    private AntispamManager antispamManager;
    private ChatStateManager chatStateManager;
    private AdminGUI adminGUI;
    private MsgSpyManager msgSpyManager;

    // v2.0 new
    private ChannelManager channelManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // ── Managers (v1.0 order preserved) ──────────────────────────────────
        this.messageManager   = new MessageManager(this);
        this.luckPermsManager = new LuckPermsManager(this);
        this.muteManager      = new MuteManager(this);
        this.ignoreManager    = new IgnoreManager(this);
        this.antispamManager  = new AntispamManager(this);
        this.chatStateManager = new ChatStateManager(this);
        this.msgSpyManager    = new MsgSpyManager(this);
        this.adminGUI         = new AdminGUI(this);

        // ── v2.0: ChannelManager + API ────────────────────────────────────────
        this.channelManager = new ChannelManager(this);
        this.channelManager.load();
        BellChatAPI.init(this);

        // ── Listeners ─────────────────────────────────────────────────────────
        // ChatListener v2.0 replaces the old one — uses AsyncChatEvent
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new VIPJoinListener(this), this);

        // ── Commands ──────────────────────────────────────────────────────────
        // /bellchat (/bc) — admin hub (absorbs mute/unmute/clearchat/chatlock/spy)
        reg("bellchat",  new BellChatCommand(this));
        // /ch — channel switcher
        reg("ch",        new ChannelCommand(this));
        // Player commands — no prefix, standard MC
        reg("msg",       new MsgCommand(this));
        reg("reply",     new ReplyCommand(this));
        reg("ignore",    new IgnoreCommand(this));
        // Legacy standalone commands still registered for backwards compat
        // (admins used to /mute directly — still works)
        reg("mute",      new MuteCommand(this));
        reg("unmute",    new UnmuteCommand(this));
        reg("clearchat", new ClearChatCommand(this));
        reg("chatlock",  new ChatLockCommand(this));
        reg("msgspy",    new MsgSpyCommand(this));

        getLogger().info("BellChat v" + getDescription().getVersion() + " enabled! "
                + "Channels: " + channelManager.getChannels().keySet());
    }

    @Override
    public void onDisable() {
        BellChatAPI.shutdown();
        if (muteManager   != null) muteManager.saveAll();
        if (ignoreManager != null) ignoreManager.saveAll();
        getLogger().info("BellChat disabled. Data saved.");
    }

    public void reload() {
        reloadConfig();
        messageManager.reload();
        muteManager.reload();
        ignoreManager.reload();
        antispamManager.reload();
        chatStateManager.reload();
        channelManager.reload();
    }

    private void reg(String name, org.bukkit.command.CommandExecutor exec) {
        var cmd = getCommand(name);
        if (cmd == null) { getLogger().warning("Command '" + name + "' not in plugin.yml!"); return; }
        cmd.setExecutor(exec);
        if (exec instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
    }

    public static BellChat getInstance()          { return instance; }
    public MessageManager getMessageManager()      { return messageManager; }
    public LuckPermsManager getLuckPermsManager()  { return luckPermsManager; }
    public MuteManager getMuteManager()            { return muteManager; }
    public IgnoreManager getIgnoreManager()        { return ignoreManager; }
    public AntispamManager getAntispamManager()    { return antispamManager; }
    public ChatStateManager getChatStateManager()  { return chatStateManager; }
    public AdminGUI getAdminGUI()                  { return adminGUI; }
    public MsgSpyManager getMsgSpyManager()        { return msgSpyManager; }
    public ChannelManager getChannelManager()      { return channelManager; }
}
