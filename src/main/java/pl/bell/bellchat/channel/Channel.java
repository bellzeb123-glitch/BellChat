package pl.bell.bellchat.channel;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Channel {

    private final String id;
    private final ChannelType type;
    private final String displayName;
    private final String format;
    private final int localRadius;           // -1 = unlimited
    private final String requiredPermission; // null = open to all
    private boolean enabled;

    // PARTY only
    private final Set<UUID> members = new HashSet<>();
    private UUID owner;

    public Channel(String id, ChannelType type, String displayName, String format,
                   int localRadius, String requiredPermission, boolean enabled) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.format = format;
        this.localRadius = localRadius;
        this.requiredPermission = requiredPermission;
        this.enabled = enabled;
    }

    public String getId()                  { return id; }
    public ChannelType getType()           { return type; }
    public String getDisplayName()         { return displayName; }
    public String getFormat()              { return format; }
    public int getLocalRadius()            { return localRadius; }
    public String getRequiredPermission()  { return requiredPermission; }
    public boolean isEnabled()             { return enabled; }
    public void setEnabled(boolean v)      { enabled = v; }

    public boolean requiresPermission() {
        return requiredPermission != null && !requiredPermission.isEmpty();
    }

    // ── Party members ──────────────────────────────────────────────────────────
    public Set<UUID> getMembers()          { return Collections.unmodifiableSet(members); }
    public void addMember(UUID uuid)       { members.add(uuid); }
    public void removeMember(UUID uuid)    { members.remove(uuid); }
    public boolean hasMember(UUID uuid)    { return members.contains(uuid); }
    public UUID getOwner()                 { return owner; }
    public void setOwner(UUID owner)       { this.owner = owner; members.add(owner); }

    @Override
    public String toString() { return "Channel{id='" + id + "', type=" + type + "}"; }
}
