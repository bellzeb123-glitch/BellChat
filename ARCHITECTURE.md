# BellChat — Architecture

## Overview

BellChat is a multi-channel chat plugin for the **Bell ecosystem** (Minecraft Paper 1.21+).
It replaces the default chat with a configurable channel system, moderation tools, AFK management, tablist formatting, and a public API for companion plugins (e.g. BellChatPro, BellLP).

Main entry point: `pl.bell.bellchat.BellChat` (extends `JavaPlugin`).

---

## Channel System

Channels are loaded from `config.yml` (`channels` section) and managed by `ChannelManager`.
Each channel has a type, display name, format string, optional permission, and optional local radius.

### Channel Types (`ChannelType`)

| Type     | Scope                                       | Default permission         |
|----------|---------------------------------------------|----------------------------|
| `GLOBAL` | All online players                          | `bellchat.channel.global`  |
| `LOCAL`  | Players within configurable block radius    | `bellchat.channel.local`   |
| `PARTY`  | Invite-only group (runtime, not persisted)  | `bellchat.channel.party`   |
| `VIP`    | Players with VIP permission                 | `bellchat.channel.vip`     |
| `ADMIN`  | All online players (staff channel)          | `bellchat.channel.admin`   |

### Message Flow

1. Player sends a chat message.
2. `ChatListener` intercepts the event, runs antispam / URL filter / emoji replacement.
3. `ChannelManager.routeMessage()` fires a `BellChatMessageEvent` (cancellable).
4. Message is formatted using the channel format with LuckPerms prefix/suffix, hover/click components.
5. Recipients are resolved per channel type; ignored players are filtered out.

### Format Placeholders

`{prefix}`, `{suffix}`, `{lp-color}`, `{player}`, `{displayname}`, `{channel}`, `{message}`

Group-specific formats can override the global channel format via `group-formats` in config.

---

## BellLP Integration

**File:** `integration/BellLPIntegration.java`

BellChat integrates with BellLP via its `GroupSyncHandler` API using a reflective proxy (`java.lang.reflect.Proxy`). This avoids a hard dependency — BellLP is a soft-depend.

### Handled Callbacks

| Callback             | Action                                          |
|----------------------|-------------------------------------------------|
| `onGroupSynced`      | Full plugin reload (formats, channels, tablist)  |
| `onAllGroupsSynced`  | Full plugin reload                               |
| `refreshPlayer`      | Update single player's tablist entry             |
| `onVipGranted`       | Reload + update player's tablist (VIP prefix)    |
| `onVipRevoked`       | Reload + update player's tablist (remove prefix) |

Registration is attempted at startup and again after 60 ticks (3 seconds) to handle load-order race conditions.

---

## Moderation Features

### Muting (`MuteManager`)
- Persistent mute storage (survives restarts).
- Supports timed mutes with optional reason.
- Commands: `/mute <player> [duration] [reason]`, `/unmute <player>`.

### Ignore (`IgnoreManager`)
- Per-player ignore list (persistent).
- Ignored players' messages are silently hidden from the ignoring player.
- Command: `/ignore <player>` (toggle).

### Antispam (`AntispamManager`)
- Configurable cooldowns and duplicate-message detection.
- Bypass permission: `bellchat.antispam.bypass`.

### URL Filter (`UrlFilterManager`)
- Blocks or censors URLs/links in chat.
- Bypass permission: `bellchat.url.bypass`.

### Chat State (`ChatStateManager`)
- Global chat lock (`/chatlock`) — only staff with `bellchat.chatlock.bypass` can chat.
- Clear chat (`/clearchat`) — floods blank lines to all players.

### Private Messages
- `/msg`, `/reply` with tab-completion.
- Staff spy mode (`/msgspy`) via `MsgSpyManager`.

---

## AFK System

**Files:** `managers/AfkManager.java`, `managers/AfkConfigManager.java`, `model/AfkGroupRule.java`

Replaces EssentialsX AFK with a built-in system.

| Feature               | Details                                                    |
|-----------------------|------------------------------------------------------------|
| Manual toggle         | `/afk` command                                             |
| Auto-AFK              | After configurable idle seconds (`afk.auto-afk-seconds`)   |
| Auto-kick             | Per-group rules via LuckPerms (`afk.groups`)               |
| Kick bypass           | Permission `bellchat.afk.kick.bypass`                      |
| Tablist indicator     | `[AFK]` suffix appended to player list name                |
| Chat prefix           | Configurable `☾ AFK` tag prepended to chat messages        |
| Global broadcasts     | "Player is now AFK" / "Player is back" messages            |

The AFK check runs every second (20 ticks) on the main thread. Activity tracking (`ConcurrentHashMap`) is thread-safe for async chat events.

---

## Tablist (`TablistListener`)

Formats player list names using LuckPerms prefix + player name.
Configurable format: `tablist.format: "{prefix}{player}"`.

Updated on:
- Player join (delayed 10 ticks for LP data load)
- AFK state change
- BellLP group sync / VIP grant/revoke
- Plugin reload

---

## BellChatAPI

**File:** `api/BellChatAPI.java`

Public API for external plugins. Access via `BellChatAPI.get()`.

```java
BellChatAPI api = BellChatAPI.get();
api.switchChannel(player, "vip");
api.getPlayerChannel(player);
api.isMuted(player);
api.getChannelManager();
```

Safety: `BellChatAPI.isReady()` returns `false` before `onEnable` and after `onDisable`.

---

## Custom Events

| Event                        | When                              | Cancellable |
|------------------------------|-----------------------------------|-------------|
| `BellChatMessageEvent`       | Before a channel message is sent  | Yes         |
| `BellChatChannelSwitchEvent` | Before a player switches channels | Yes         |
| `BellChatMentionEvent`       | When a player is @mentioned       | —           |

---

## Commands

| Command      | Aliases          | Description                  | Permission              |
|--------------|------------------|------------------------------|-------------------------|
| `/bellchat`  | `/bch`           | Admin panel (GUI/reload/etc) | `bellchat.admin`        |
| `/ch`        |                  | Switch chat channel          | per-channel             |
| `/msg`       | `/tell /w /pm`   | Private message              | —                       |
| `/reply`     | `/r`             | Reply to last PM             | —                       |
| `/ignore`    |                  | Toggle ignore on a player    | —                       |
| `/mute`      |                  | Mute a player                | `bellchat.mute`         |
| `/unmute`    |                  | Unmute a player              | `bellchat.mute`         |
| `/clearchat` | `/cc`            | Clear chat for all           | `bellchat.clearchat`    |
| `/chatlock`  | `/cl`            | Lock/unlock global chat      | `bellchat.chatlock`     |
| `/msgspy`    | `/spy`           | Toggle PM spy mode           | `bellchat.spy`          |
| `/afk`       |                  | Toggle AFK status            | —                       |

---

## Admin GUI

`AdminGUI` provides an in-game chest-based GUI (`/bch gui`) for managing channels (enable/disable) and viewing plugin status.

`AfkAdminGUI` provides a separate GUI for configuring AFK rules per LuckPerms group.

---

## Integration Points

| Plugin           | Type        | Purpose                                                  |
|------------------|-------------|----------------------------------------------------------|
| **LuckPerms**    | soft-depend | Prefix, suffix, primary group, color extraction          |
| **PlaceholderAPI**| soft-depend | PAPI placeholder expansion in formats                   |
| **BellLP**       | soft-depend | Group sync, VIP grant/revoke → tablist & format refresh  |
| **BellChatPro**  | detected    | Premium features (HEX gradients, extended emoji, etc.)   |

---

## Package Structure

```
pl.bell.bellchat
├── BellChat.java              # Main plugin class
├── api/
│   └── BellChatAPI.java       # Public API
├── channel/
│   ├── Channel.java           # Channel model
│   ├── ChannelManager.java    # Channel lifecycle & message routing
│   └── ChannelType.java       # GLOBAL, LOCAL, PARTY, VIP, ADMIN
├── commands/
│   ├── AfkCommand.java
│   ├── BellChatCommand.java   # /bch (admin panel)
│   ├── ChannelCommand.java    # /ch
│   ├── ChatLockCommand.java
│   ├── ClearChatCommand.java
│   ├── IgnoreCommand.java
│   ├── MsgCommand.java
│   ├── MsgSpyCommand.java
│   ├── MuteCommand.java
│   ├── ReplyCommand.java
│   └── UnmuteCommand.java
├── event/
│   ├── BellChatChannelSwitchEvent.java
│   ├── BellChatMentionEvent.java
│   └── BellChatMessageEvent.java
├── gui/
│   ├── AdminGUI.java
│   └── AfkAdminGUI.java
├── integration/
│   └── BellLPIntegration.java # Reflective BellLP hook
├── listeners/
│   ├── AfkGuiInputListener.java
│   ├── AfkListener.java
│   ├── ChatListener.java
│   ├── TablistListener.java
│   └── VIPJoinListener.java
├── managers/
│   ├── AfkConfigManager.java
│   ├── AfkManager.java
│   ├── AntispamManager.java
│   ├── BroadcastManager.java
│   ├── ChatStateManager.java
│   ├── EmojiManager.java
│   ├── IgnoreManager.java
│   ├── LuckPermsManager.java
│   ├── MessageManager.java
│   ├── MsgSpyManager.java
│   ├── MuteManager.java
│   └── UrlFilterManager.java
└── model/
    ├── AfkGroupRule.java
    └── MuteEntry.java
```
