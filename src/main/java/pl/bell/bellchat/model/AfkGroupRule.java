package pl.bell.bellchat.model;

/**
 * AFK / auto-kick rules for a single LuckPerms group.
 */
public final class AfkGroupRule {

    private int autoAfkSeconds;
    private boolean kickEnabled;
    private int kickSeconds;

    public AfkGroupRule(int autoAfkSeconds, boolean kickEnabled, int kickSeconds) {
        this.autoAfkSeconds = Math.max(0, autoAfkSeconds);
        this.kickEnabled = kickEnabled;
        this.kickSeconds = Math.max(0, kickSeconds);
    }

    public static AfkGroupRule disabled() {
        return new AfkGroupRule(0, false, 0);
    }

    public int getAutoAfkSeconds() {
        return autoAfkSeconds;
    }

    public void setAutoAfkSeconds(int autoAfkSeconds) {
        this.autoAfkSeconds = Math.max(0, autoAfkSeconds);
    }

    public boolean isKickEnabled() {
        return kickEnabled;
    }

    public void setKickEnabled(boolean kickEnabled) {
        this.kickEnabled = kickEnabled;
    }

    public int getKickSeconds() {
        return kickSeconds;
    }

    public void setKickSeconds(int kickSeconds) {
        this.kickSeconds = Math.max(0, kickSeconds);
    }

    public boolean isAutoAfkEnabled() {
        return autoAfkSeconds > 0;
    }

    public AfkGroupRule copy() {
        return new AfkGroupRule(autoAfkSeconds, kickEnabled, kickSeconds);
    }
}
