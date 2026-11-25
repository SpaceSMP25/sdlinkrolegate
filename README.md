# Simple Discord Link Role Gate (`sdlinkrolegate`)

A server-side **Fabric mod for Minecraft 26.3** that prevents players from breaking blocks, placing blocks, or interacting with containers/entities until they link their Discord account via **Simple Discord Link (SDLink)** and hold a configured Discord role.

## Requirements

- **Minecraft**: `26.3`
- **Mod Loader**: Fabric Loader `>= 0.20.1`
- **Java**: `25+`
- **Required Server Mods**:
  - [Fabric API](https://modrinth.com/mod/fabric-api)
  - [CraterLib](https://modrinth.com/mod/craterlib) (`>= 3.1.0`)
  - [Simple Discord Link (SDLink)](https://modrinth.com/mod/simple-discord-link) (`>= 3.4.0`)
- **Optional Server Mods**:
  - [LuckPerms](https://modrinth.com/mod/luckperms) (`>= 5.4`, soft dependency)

> **Note:** This is a **server-side only** mod (`"environment": "server"`). Players do not need it installed on their clients.

## Features

- **No Duplicate Discord Bot**: Uses SDLink's `BotController`, `MinecraftAccount` storage, `CraterEventBus` (`SDLinkReadyEvent`, `VerificationEvent.PlayerVerified`, `VerificationEvent.PlayerUnverified`), and shaded JDA instance directly.
- **Role Check on Join + Live Event Updates**:
  - Checks whether joining players are linked and hold `requiredRoleId` in Discord.
  - Reacts immediately to live Discord role additions/removals (`GuildMemberRoleAddEvent` / `GuildMemberRoleRemoveEvent`), Discord leaves/bans, and SDLink `/discordverify` link/unlink events without requiring a relog.
- **Unverified Player Restrictions**:
  - Sets unverified players to **Adventure** mode.
  - Cancels block breaking, block placing, chest/container/block interactions, entity interactions, and placeable/world-modifying item usage.
  - Sends configurable chat messages every time an unverified player attempts a restricted action.
- **Bossbar & Native 26.3 Login Modal**:
  - Shows a customizable bossbar (text, color, style, ID, enabled toggle).
  - Opens a native Minecraft 26.3 `Dialog` popup on join (toggleable, with optional clickable Discord invite button when `discordInviteLink` is configured).
- **LuckPerms Integration (Optional)**:
  - Automatically adds unverified players to `luckperms.unverifiedGroup` and removes them when verified (skipped silently if LuckPerms is not installed).
- **Operator Bypass & Hot Reload**:
  - Permission level 4 operators (`OWNERS`) always bypass restrictions.
  - `/rolegate reload` hot-reloads `config/sdlinkrolegate.toml` and re-evaluates online players.

## Configuration

Generated at `config/sdlinkrolegate.toml` on first launch. All messages support `&` color codes and the `{invite}` placeholder.

```toml
requiredRoleId = ""
discordInviteLink = ""

[bossbar]
enabled = true
id = "sdlinkrolegate:unverified"
text = "To interact with the server, please join our Discord!"
color = "RED"
style = "SOLID"

[messages]
blockBreakDenied = "&cYou cannot break blocks! Join our Discord to get access."
blockPlaceDenied = "&cYou cannot place blocks! Join our Discord to get access."
interactionDenied = "&cYou cannot interact with this! Join our Discord to get access."

[modal]
enabled = true
title = "Discord Verification Required"
body = "Join our Discord server and verify your account to interact with the server."
inviteButtonText = "Join Discord"
closeButtonText = "Close"

[luckperms]
enabled = true
unverifiedGroup = "unverified"
```

## Building

```bash
./gradlew build
```

The built mod JAR will be output to `build/libs/sdlinkrolegate-1.0.0.jar`.
