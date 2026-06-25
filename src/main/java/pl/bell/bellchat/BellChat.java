package pl.bell.bellchat;

import org.bukkit.plugin.java.JavaPlugin;
import pl.bell.bellchat.api.BellChatAPI;
import pl.bell.bellchat.channel.ChannelManager;
import pl.bell.bellchat.commands.*;
import pl.bell.bellchat.gui.AdminGUI;
import pl.bell.bellchat.gui.AfkAdminGUI;
import pl.bell.bellchat.gui.BroadcastAdminGUI;
import pl.bell.bellchat.gui.ChannelAdminGUI;
import pl.bell.bellchat.listeners.AdminChatInputListener;
import pl.bell.bellchat.listeners.AfkListener;
import pl.bell.bellchat.listeners.ChatListener;
import pl.bell.bellchat.listeners.TablistListener;
import pl.bell.bellchat.listeners.VIPJoinListener;
import pl.bell.bellchat.managers.*;

/**
 * BellChat v1.26.1.2
 *
 * NAPRAWY:
 * - reload() przeładowuje WSZYSTKIE managery (w tym MessageManager → /bch lang działa)
 * - TablistListener — czysty format nicku w TAB ([VIP] Nick z kolorem z LP)
 */
public class BellChat extends JavaPlugin {

    private static BellChat instance;

    private MessageManager    messageManager;
    private LuckPermsManager  luckPermsManager;
    private MuteManager       muteManager;
    private IgnoreManager     ignoreManager;
    private AntispamManager   antispamManager;
    private ChatStateManager  chatStateManager;
    private AdminGUI          adminGUI;
    private MsgSpyManager     msgSpyManager;
    private ChannelManager    channelManager;
    private EmojiManager      emojiManager;
    private UrlFilterManager  urlFilterManager;
    private BroadcastManager  broadcastManager;
    private TablistListener   tablistListener;
    private AfkManager        afkManager;
    private AfkConfigManager  afkConfigManager;
    private AfkAdminGUI       afkAdminGUI;
    private BroadcastAdminGUI broadcastAdminGUI;
    private ChannelAdminGUI   channelAdminGUI;
    private ChatListener      chatListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getServer().getScheduler().runTaskLater(this, this::printBanner, 1L);

        this.messageManager   = new MessageManager(this);
        this.luckPermsManager = new LuckPermsManager(this);
        this.muteManager      = new MuteManager(this);
        this.ignoreManager    = new IgnoreManager(this);
        this.antispamManager  = new AntispamManager(this);
        this.chatStateManager = new ChatStateManager(this);
        this.msgSpyManager    = new MsgSpyManager(this);
        this.adminGUI         = new AdminGUI(this);
        this.afkConfigManager = new AfkConfigManager(this);
        this.afkAdminGUI      = new AfkAdminGUI(this);
        this.broadcastAdminGUI = new BroadcastAdminGUI(this);
        this.channelAdminGUI  = new ChannelAdminGUI(this);

        this.channelManager   = new ChannelManager(this);
        this.channelManager.load();
        BellChatAPI.init(this);

        this.emojiManager     = new EmojiManager(this);
        this.urlFilterManager = new UrlFilterManager(this);
        this.broadcastManager = new BroadcastManager(this);
        this.tablistListener  = new TablistListener(this);
        this.afkManager       = new AfkManager(this);

        pl.bell.bellchat.integration.BellLPIntegration.tryRegister(this);
        getServer().getScheduler().runTaskLater(this, () ->
            pl.bell.bellchat.integration.BellLPIntegration.tryRegister(this), 60L);

        // Listeners
        this.chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new VIPJoinListener(this), this);
        getServer().getPluginManager().registerEvents(tablistListener, this);
        getServer().getPluginManager().registerEvents(new AfkListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminChatInputListener(this), this);

        // Commands
        reg("bellchat",  new BellChatCommand(this));
        reg("ch",        new ChannelCommand(this));
        reg("msg",       new MsgCommand(this));
        reg("reply",     new ReplyCommand(this));
        reg("ignore",    new IgnoreCommand(this));
        reg("mute",      new MuteCommand(this));
        reg("unmute",    new UnmuteCommand(this));
        reg("clearchat", new ClearChatCommand(this));
        reg("chatlock",  new ChatLockCommand(this));
        reg("msgspy",    new MsgSpyCommand(this));
        reg("afk",       new AfkCommand(this));

        // Integracja z panelem BellHub (podglad czatu na zywo) — tylko gdy obecny.
        // Lazy: klasy bell-hub-api ladowane dopiero w tym bloku (provided, brak w runtime bez BellHub).
        if (getServer().getPluginManager().getPlugin("BellHub") != null) {
            try {
                var module = new pl.bell.bellchat.integration.BellHubModule(this);
                getServer().getPluginManager().registerEvents(module, this);
                getServer().getServicesManager().register(
                        pl.bell.hub.api.BellModule.class, module, this,
                        org.bukkit.plugin.ServicePriority.Normal);
                getLogger().info("Zarejestrowano modul BellChat w panelu BellHub (czat na zywo).");
            } catch (Throwable t) {
                getLogger().warning("Nie udalo sie zarejestrowac modulu BellHub: " + t.getMessage());
            }
        }

        getLogger().info("BellChat v" + getDescription().getVersion() + " enabled! "
                + "Channels: " + channelManager.getChannels().keySet());
    }

    @Override
    public void onDisable() {
        BellChatAPI.shutdown();
        if (broadcastManager != null) broadcastManager.shutdown();
        if (afkManager       != null) afkManager.shutdown();
        if (muteManager      != null) muteManager.saveAll();
        if (ignoreManager    != null) ignoreManager.saveAll();
        getLogger().info("BellChat disabled. Data saved.");
    }

    /**
     * FIX: reload() przeładowuje WSZYSTKIE managery.
     * Bez tego /bch lang nie działało (MessageManager nie był przeładowywany).
     */
    public void reload() {
        reloadConfig();
        if (messageManager   != null) messageManager.reload();
        if (channelManager   != null) channelManager.reload();
        if (emojiManager     != null) emojiManager.reload();
        if (urlFilterManager != null) urlFilterManager.reload();
        if (broadcastManager != null) broadcastManager.reload();
        if (antispamManager  != null) antispamManager.reload();
        if (afkConfigManager != null) afkConfigManager.reload();
        if (afkManager       != null) afkManager.reload();
        if (chatListener     != null) chatListener.reloadProfanityPatterns();
        if (tablistListener  != null) tablistListener.refreshAll();
        getLogger().info("BellChat reloaded.");
    }

    private void printBanner() {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("BellChatPro") != null) return;
        var c = org.bukkit.Bukkit.getConsoleSender();
        c.sendMessage("§r");
        c.sendMessage("§6  ██████╗ ███████╗██╗     ██╗          ");
        c.sendMessage("§6  ██╔══██╗██╔════╝██║     ██║          ");
        c.sendMessage("§6  ██████╔╝█████╗  ██║     ██║          ");
        c.sendMessage("§6  ██╔══██╗██╔══╝  ██║     ██║          ");
        c.sendMessage("§6  ██████╔╝███████╗███████╗███████╗§r§f Chat");
        c.sendMessage("§6  ╚═════╝ ╚══════╝╚══════╝╚══════╝     ");
        c.sendMessage("§r");
        c.sendMessage("§7  Version §f" + getDescription().getVersion()
                + "  §7│  Author §bBellzeb");
        c.sendMessage("§7  Status  §aFree §7│ §7Multi-Channel Chat System");
        c.sendMessage("§r");
    }

    private void reg(String name, org.bukkit.command.CommandExecutor exec) {
        var cmd = getCommand(name);
        if (cmd == null) { getLogger().warning("Command '" + name + "' not in plugin.yml!"); return; }
        cmd.setExecutor(exec);
        if (exec instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
    }

    public static BellChat getInstance()             { return instance; }
    public MessageManager getMessageManager()        { return messageManager; }
    public LuckPermsManager getLuckPermsManager()    { return luckPermsManager; }
    public MuteManager getMuteManager()              { return muteManager; }
    public IgnoreManager getIgnoreManager()          { return ignoreManager; }
    public AntispamManager getAntispamManager()      { return antispamManager; }
    public ChatStateManager getChatStateManager()    { return chatStateManager; }
    public AdminGUI getAdminGUI()                    { return adminGUI; }
    public MsgSpyManager getMsgSpyManager()          { return msgSpyManager; }
    public ChannelManager getChannelManager()        { return channelManager; }
    public EmojiManager getEmojiManager()            { return emojiManager; }
    public UrlFilterManager getUrlFilterManager()    { return urlFilterManager; }
    public BroadcastManager getBroadcastManager()    { return broadcastManager; }
    public TablistListener getTablistListener()      { return tablistListener; }
    public AfkManager getAfkManager()                { return afkManager; }
    public AfkConfigManager getAfkConfigManager()   { return afkConfigManager; }
    public AfkAdminGUI getAfkAdminGUI()             { return afkAdminGUI; }
    public BroadcastAdminGUI getBroadcastAdminGUI() { return broadcastAdminGUI; }
    public ChannelAdminGUI getChannelAdminGUI()     { return channelAdminGUI; }
}
