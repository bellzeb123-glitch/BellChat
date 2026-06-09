package pl.bell.bellchat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import pl.bell.bellchat.channel.Channel;

/** Fired when a player is @-mentioned in chat. Cancellable. */
public class BellChatMentionEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player mentioned;
    private final Channel channel;
    private boolean cancelled;

    public BellChatMentionEvent(Player sender, Player mentioned, Channel channel) {
        super(sender);
        this.mentioned = mentioned;
        this.channel = channel;
    }

    public Player getSender()                     { return getPlayer(); }
    public Player getMentioned()                  { return mentioned; }
    public Channel getChannel()                   { return channel; }
    @Override public boolean isCancelled()        { return cancelled; }
    @Override public void setCancelled(boolean c) { cancelled = c; }
    @Override public HandlerList getHandlers()    { return HANDLERS; }
    public static HandlerList getHandlerList()    { return HANDLERS; }
}
