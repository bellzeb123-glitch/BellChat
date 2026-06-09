package pl.bell.bellchat.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import pl.bell.bellchat.channel.Channel;

/** Fired when a player switches channels. Cancellable. */
public class BellChatChannelSwitchEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Channel from;
    private final Channel to;
    private boolean cancelled;

    public BellChatChannelSwitchEvent(Player player, Channel from, Channel to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Channel getFrom()                      { return from; }
    public Channel getTo()                        { return to; }
    @Override public boolean isCancelled()        { return cancelled; }
    @Override public void setCancelled(boolean c) { cancelled = c; }
    @Override public HandlerList getHandlers()    { return HANDLERS; }
    public static HandlerList getHandlerList()    { return HANDLERS; }
}
