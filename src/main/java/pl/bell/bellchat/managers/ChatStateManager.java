package pl.bell.bellchat.managers;

import pl.bell.bellchat.BellChat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatStateManager {

    private final BellChat plugin;
    private boolean chatLocked = false;
    private final Map<UUID, UUID> replyTargets = new HashMap<>();

    public ChatStateManager(BellChat plugin) {
        this.plugin = plugin;
    }

    public void reload() { /* stateless — nothing to reload */ }

    public boolean isChatLocked()         { return chatLocked; }
    public void setChatLocked(boolean v)  { chatLocked = v; }

    public void setReplyTarget(UUID sender, UUID target) { replyTargets.put(sender, target); }
    public UUID getReplyTarget(UUID sender)              { return replyTargets.get(sender); }
    public void clearReplyTarget(UUID sender)            { replyTargets.remove(sender); }
}
