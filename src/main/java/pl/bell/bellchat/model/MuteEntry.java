package pl.bell.bellchat.model;

import java.util.UUID;

public class MuteEntry {

    private final UUID playerUUID;
    private final String playerName;
    private final long mutedAt;
    private final long expiresAt; // -1 = permanent
    private final String reason;
    private final String mutedBy;

    public MuteEntry(UUID playerUUID, String playerName, long mutedAt,
                     long expiresAt, String reason, String mutedBy) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.mutedAt    = mutedAt;
        this.expiresAt  = expiresAt;
        this.reason     = reason;
        this.mutedBy    = mutedBy;
    }

    public boolean isPermanent()  { return expiresAt == -1; }
    public boolean isExpired()    { return !isPermanent() && System.currentTimeMillis() > expiresAt; }

    public String getFormattedRemaining() {
        if (isPermanent()) return "permanent";
        long diff = expiresAt - System.currentTimeMillis();
        if (diff <= 0) return "0s";
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours   = minutes / 60;
        long days    = hours / 24;
        if (days > 0)    return days + "d " + (hours % 24) + "h";
        if (hours > 0)   return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    public UUID getPlayerUUID()  { return playerUUID; }
    public String getPlayerName(){ return playerName; }
    public long getMutedAt()     { return mutedAt; }
    public long getExpiresAt()   { return expiresAt; }
    public String getReason()    { return reason; }
    public String getMutedBy()   { return mutedBy; }
}
