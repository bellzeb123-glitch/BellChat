package pl.bell.bellchat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import pl.bell.bellchat.channel.Channel;

/** Fired (main thread) before a channel message is delivered. Cancellable. */
public class BellChatMessageEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Channel channel;
    private String message;
    private boolean cancelled;

    public BellChatMessageEvent(Player player, Channel channel, String message) {
        super(player);
        this.channel = channel;
        this.message = message;
    }

    public Channel getChannel()            { return channel; }
    public String getMessage()             { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override public boolean isCancelled()        { return cancelled; }
    @Override public void setCancelled(boolean c) { cancelled = c; }
    @Override public HandlerList getHandlers()     { return HANDLERS; }
    public static HandlerList getHandlerList()     { return HANDLERS; }
}
