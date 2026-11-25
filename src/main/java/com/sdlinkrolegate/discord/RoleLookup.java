package com.sdlinkrolegate.discord;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abstraction over SDLink/Discord verification and role lookups.
 */
public interface RoleLookup {

    boolean isBotReady();

    Optional<String> getLinkedDiscordId(UUID playerUuid, String playerName);

    /**
     * Checks whether {@code discordUserId} holds {@code requiredRoleId}.
     * Returns {@code true} if {@code onResult} was invoked synchronously, or {@code false}
     * if an async lookup is in progress.
     */
    boolean checkHasRole(String discordUserId, String requiredRoleId, Consumer<Boolean> onResult);
}
