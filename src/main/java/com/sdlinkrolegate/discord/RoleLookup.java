package com.sdlinkrolegate.discord;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abstraction over SDLink/Discord verification and role lookups.
 */
public interface RoleLookup {

    boolean isBotReady();

    /**
     * Returns {@code true} when SDLink's JDA instance exists and is still connecting or
     * reconnecting rather than permanently disabled or shut down.
     */
    default boolean isBotConnecting() {
        return false;
    }

    Optional<String> getLinkedDiscordId(UUID playerUuid, String playerName);

    /**
     * Checks whether {@code discordUserId} holds {@code requiredRoleId}.
     * Returns {@code true} if {@code onResult} was invoked synchronously, or {@code false}
     * if an async lookup is in progress.
     */
    boolean checkHasRole(String discordUserId, String requiredRoleId, Consumer<Boolean> onResult);
}
