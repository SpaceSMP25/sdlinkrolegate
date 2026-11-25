package com.sdlinkrolegate.discord;

import com.hypherionmc.craterlib.api.game.authlib.CraterGameProfile;
import com.hypherionmc.sdlink.api.accounts.MinecraftAccount;
import com.hypherionmc.sdlink.core.database.SDLinkAccount;
import com.hypherionmc.sdlink.core.discord.BotController;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.JDA;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.entities.Guild;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Queries SDLink account links and Discord guild roles through SDLink's shared BotController / JDA instance.
 */
public final class SDLinkRoleChecker implements RoleLookup {

    private final Logger logger;
    private final AtomicReference<JDA> attachedJda = new AtomicReference<>();

    public SDLinkRoleChecker(Logger logger) {
        this.logger = logger;
    }

    public boolean isBotReady() {
        return BotController.INSTANCE != null && BotController.INSTANCE.isBotReady();
    }

    /**
     * Attaches the JDA listener to the active JDA instance if not already attached.
     */
    public void ensureJdaListenerAttached(DiscordRoleListener listener) {
        if (!isBotReady()) {
            return;
        }
        JDA jda = BotController.INSTANCE.getJDA();
        if (jda == null) {
            return;
        }
        JDA previous = attachedJda.getAndSet(jda);
        if (previous != jda) {
            jda.addEventListener(listener);
            logger.info("Attached DiscordRoleListener to SDLink JDA instance");
        }
    }

    /**
     * Detaches the JDA listener on server shutdown.
     */
    public void detachJdaListener(DiscordRoleListener listener) {
        JDA jda = attachedJda.getAndSet(null);
        if (jda != null) {
            jda.removeEventListener(listener);
        }
    }

    /**
     * Looks up the player's linked Discord user ID from SDLink's account storage.
     */
    public Optional<String> getLinkedDiscordId(UUID playerUuid, String playerName) {
        if (!isBotReady()) {
            return Optional.empty();
        }
        try {
            CraterGameProfile profile = CraterGameProfile.fromGame(playerName, playerUuid);
            MinecraftAccount account = MinecraftAccount.of(profile);
            if (!account.isAccountVerified()) {
                return Optional.empty();
            }
            SDLinkAccount stored = account.getStoredAccount();
            if (stored == null || stored.getDiscordID() == null || stored.getDiscordID().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(stored.getDiscordID());
        } catch (Exception e) {
            logger.warn("Failed to look up SDLink account for {} ({})", playerName, playerUuid, e);
            return Optional.empty();
        }
    }

    /**
     * Checks whether the given Discord user holds {@code requiredRoleId} in any guild the SDLink bot belongs to.
     * Returns {@code true} if resolved synchronously from JDA's cache, or {@code false} if an async REST lookup
     * is in flight (in which case {@code onResult} will be invoked later on JDA's callback thread).
     */
    public boolean checkHasRole(String discordUserId, String requiredRoleId, Consumer<Boolean> onResult) {
        if (!isBotReady() || requiredRoleId == null || requiredRoleId.isBlank()) {
            onResult.accept(false);
            return true;
        }

        JDA jda = BotController.INSTANCE.getJDA();
        if (jda == null) {
            onResult.accept(false);
            return true;
        }

        List<Guild> guilds = jda.getGuilds();
        if (guilds.isEmpty()) {
            onResult.accept(false);
            return true;
        }

        List<Guild> uncachedGuilds = new java.util.ArrayList<>();
        for (Guild guild : guilds) {
            Member cachedMember = guild.getMemberById(discordUserId);
            if (cachedMember != null) {
                if (memberHasRole(cachedMember, requiredRoleId)) {
                    onResult.accept(true);
                    return true;
                }
            } else {
                uncachedGuilds.add(guild);
            }
        }

        if (uncachedGuilds.isEmpty()) {
            onResult.accept(false);
            return true;
        }

        AtomicInteger remaining = new AtomicInteger(uncachedGuilds.size());
        AtomicBoolean completed = new AtomicBoolean(false);

        for (Guild guild : uncachedGuilds) {
            guild.retrieveMemberById(discordUserId).queue(
                    member -> {
                        if (memberHasRole(member, requiredRoleId)) {
                            if (completed.compareAndSet(false, true)) {
                                onResult.accept(true);
                            }
                        } else if (remaining.decrementAndGet() == 0 && completed.compareAndSet(false, true)) {
                            onResult.accept(false);
                        }
                    },
                    error -> {
                        // User may not belong to this guild (or lookup failed); finish once all guilds report.
                        if (remaining.decrementAndGet() == 0 && completed.compareAndSet(false, true)) {
                            onResult.accept(false);
                        }
                    }
            );
        }

        return false;
    }

    public static boolean memberHasRole(Member member, String requiredRoleId) {
        return member != null
                && requiredRoleId != null
                && !requiredRoleId.isBlank()
                && member.getRoles().stream().anyMatch(role -> role.getId().equals(requiredRoleId));
    }
}
