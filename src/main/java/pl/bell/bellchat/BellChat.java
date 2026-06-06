package pl.bell.bellchat;

import org.bukkit.plugin.java.JavaPlugin;
import pl.bell.bellchat.commands.*;
import pl.bell.bellchat.managers.MsgSpyManager;
import pl.bell.bellchat.gui.AdminGUI;
import pl.bell.bellchat.listeners.ChatListener;
import pl.bell.bellchat.listeners.VIPJoinListener;
import pl.bell.bellchat.managers.*;

public class BellChat extends JavaPlugin {

    private static BellChat instance;

    private MessageManager messageManager;
    private LuckPermsManager luckPermsManager;
    private MuteManager muteManager;
    private IgnoreManager ignoreManager;
    private AntispamManager antispamManager;
    private ChatStateManager chatStateManager;
    private AdminGUI adminGUI;
    private MsgSpyManager msgSpyManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Managers
        this.messageManager  = new MessageManager(this);
        this.luckPermsManager= new LuckPermsManager(this);
        this.muteManager     = new MuteManager(this);
        this.ignoreManager   = new IgnoreManager(this);
        this.antispamManager = new AntispamManager(this);
        this.chatStateManager= new ChatStateManager(this);
        this.msgSpyManager   = new MsgSpyManager(this);
        this.adminGUI        = new AdminGUI(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new VIPJoinListener(this), this);

        // Commands
        reg("bellchat", new BellChatCommand(this));
        reg("msg",      new MsgCommand(this));
        reg("reply",    new ReplyCommand(this));
        reg("mute",     new MuteCommand(this));
        reg("unmute",   new UnmuteCommand(this));
        reg("clearchat",new ClearChatCommand(this));
        reg("chatlock", new ChatLockCommand(this));
        reg("ignore",   new IgnoreCommand(this));
        reg("msgspy",   new MsgSpyCommand(this));

        getLogger().info("BellChat v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (muteManager != null)   muteManager.saveAll();
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
    }

    private void reg(String name, org.bukkit.command.CommandExecutor exec) {
        var cmd = getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor(exec);
        if (exec instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
    }

    public static BellChat getInstance()           { return instance; }
    public MessageManager getMessageManager()       { return messageManager; }
    public LuckPermsManager getLuckPermsManager()   { return luckPermsManager; }
    public MuteManager getMuteManager()             { return muteManager; }
    public IgnoreManager getIgnoreManager()         { return ignoreManager; }
    public AntispamManager getAntispamManager()     { return antispamManager; }
    public ChatStateManager getChatStateManager()   { return chatStateManager; }
    public AdminGUI getAdminGUI()                   { return adminGUI; }
    public MsgSpyManager getMsgSpyManager()         { return msgSpyManager; }
}
