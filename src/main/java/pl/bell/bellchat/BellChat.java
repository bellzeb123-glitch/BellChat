package pl.bell.bellchat;

import org.bukkit.plugin.java.JavaPlugin;
import pl.bell.bellchat.api.BellChatAPI;
import pl.bell.bellchat.channel.ChannelManager;
import pl.bell.bellchat.commands.*;
import pl.bell.bellchat.gui.AdminGUI;
import pl.bell.bellchat.listeners.ChatListener;
import pl.bell.bellchat.listeners.VIPJoinListener;
import pl.bell.bellchat.managers.*;

/**
 * BellChat v1.26.1.2
 *
 * Główna klasa pluginu.
 *
 * NAPRAWY w tej wersji:
 * - reload() faktycznie przeładowuje WSZYSTKIE managery (nie tylko config)
 * - dzięki temu /bch lang en|pl faktycznie zmienia język
 * - banner gold ASCII art zachowany
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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        printBanner();

        // ── Managers ──────────────────────────────────────────
        this.messageManager   = new MessageManager(this);
        this.luckPermsManager = new LuckPermsManager(this);
        this.muteManager      = new MuteManager(this);
        this.ignoreManager    = new IgnoreManager(this);
        this.antispamManager  = new AntispamManager(this);
        this.chatStateManager = new ChatStateManager(this);
        this.msgSpyManager    = new MsgSpyManager(this);
        this.adminGUI         = new AdminGUI(this);

        this.channelManager   = new ChannelManager(this);
        this.channelManager.load();
        BellChatAPI.init(this);

        this.emojiManager     = new EmojiManager(this);
        this.urlFilterManager = new UrlFilterManager(this);
        this.broadcastManager = new BroadcastManager(this);

        // ── Listeners ─────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new VIPJoinListener(this), this);

        // ── Commands ──────────────────────────────────────────
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

        getLogger().info("BellChat v" + getDescription().getVersion() + " enabled! "
                + "Channels: " + channelManager.getChannels().keySet());
    }

    @Override
    public void onDisable() {
        BellChatAPI.shutdown();
        if (broadcastManager != null) broadcastManager.shutdown();
        if (muteManager      != null) muteManager.saveAll();
        if (ignoreManager    != null) ignoreManager.saveAll();
        getLogger().info("BellChat disabled. Data saved.");
    }

    /**
     * FIX: reload() teraz faktycznie przeładowuje WSZYSTKIE managery.
     * Poprzednia wersja wywoływała tylko reloadConfig() — przez co
     * /bch lang en|pl nie miało żadnego efektu (MessageManager
     * nadal trzymał stary plik językowy w pamięci).
     */
    public void reload() {
        // 1. Najpierw przeładuj config z dysku
        reloadConfig();

        // 2. Potem każdy manager — kolejność ważna:
        //    - MessageManager jako pierwszy (inne używają komunikatów)
        if (messageManager   != null) messageManager.reload();
        if (channelManager   != null) channelManager.reload();
        if (emojiManager     != null) emojiManager.reload();
        if (urlFilterManager != null) urlFilterManager.reload();
        if (broadcastManager != null) broadcastManager.reload();
        if (antispamManager  != null) antispamManager.reload();

        getLogger().info("BellChat reloaded.");
    }

    // ── Banner ────────────────────────────────────────────────

    private void printBanner() {
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
        c.sendMessage("§7  Status  §aFree §7│ §7Pro §5Coming Soon");
        c.sendMessage("§r");
    }

    private void reg(String name, org.bukkit.command.CommandExecutor exec) {
        var cmd = getCommand(name);
        if (cmd == null) { getLogger().warning("Command '" + name + "' not in plugin.yml!"); return; }
        cmd.setExecutor(exec);
        if (exec instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
    }

    // ── Getters ───────────────────────────────────────────────

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
}
