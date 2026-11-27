package com.sdlinkrolegate.discord;

import com.hypherionmc.craterlib.api.game.authlib.CraterGameProfile;
import com.hypherionmc.sdlink.api.accounts.MinecraftAccount;
import com.hypherionmc.sdlink.core.database.SDLinkAccount;
import com.hypherionmc.sdlink.core.discord.BotController;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.JDA;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.entities.Guild;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.ArrayList;
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

    @Override
    public boolean isBotReady() {
        JDA jda = getActiveJda();
        return jda != null
                && BotController.INSTANCE.isBotReady()
                && jda.getStatus() == JDA.Status.CONNECTED
                && !jda.getGuilds().isEmpty();
    }

    @Override
    public boolean isBotConnecting() {
        JDA jda = getActiveJda();
        return jda != null && jda.getStatus() != JDA.Status.CONNECTED;
    }

    /**
     * Attaches the JDA listener to the active JDA instance if not already attached.
     */
    public void ensureJdaListenerAttached(DiscordRoleListener listener) {
        JDA jda = getActiveJda();
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
    @Override
    public Optional<String> getLinkedDiscordId(UUID playerUuid, String playerName) {
        if (getActiveJda() == null) {
            return Optional.empty();
        }
        try {
            CraterGameProfile profile = CraterGameProfile.fromGame(playerName, playerUuid);
            MinecraftAccount account = MinecraftAccount.of(profile);
            SDLinkAccount stored = account.getStoredAccount();
            if (stored == null || stored.getDiscordID() == null || stored.getDiscordID().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(stored.getDiscordID().trim());
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
    @Override
    public boolean checkHasRole(String discordUserId, String requiredRoleId, Consumer<Boolean> onResult) {
        List<String> tokens = parseRoleTokens(requiredRoleId);
        if (!isBotReady() || tokens.isEmpty()) {
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

        List<Guild> guildsToQuery = new ArrayList<>();
        for (Guild guild : guilds) {
            Member cachedMember = guild.getMemberById(discordUserId);
            if (cachedMember != null && memberHasAnyRole(cachedMember, tokens)) {
                onResult.accept(true);
                return true;
            }
            // If member isn't cached yet or cached roles look absent, verify live via REST before denying.
            guildsToQuery.add(guild);
        }

        if (guildsToQuery.isEmpty()) {
            onResult.accept(false);
            return true;
        }

        AtomicInteger remaining = new AtomicInteger(guildsToQuery.size());
        AtomicBoolean completed = new AtomicBoolean(false);

        for (Guild guild : guildsToQuery) {
            guild.retrieveMemberById(discordUserId).queue(
                    member -> {
                        if (memberHasAnyRole(member, tokens)) {
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
        return memberHasAnyRole(member, parseRoleTokens(requiredRoleId));
    }

    public static boolean hasConfiguredRole(String requiredRoleSpec) {
        return !parseRoleTokens(requiredRoleSpec).isEmpty();
    }

    static List<String> parseRoleTokens(String requiredRoleSpec) {
        if (requiredRoleSpec == null || requiredRoleSpec.isBlank()) {
            return List.of();
        }
        String[] parts = requiredRoleSpec.split(",");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            String normalized = normalizeRoleToken(part);
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return List.copyOf(tokens);
    }

    static boolean matchesRole(String roleId, String roleName, List<String> requiredTokens) {
        if (requiredTokens == null || requiredTokens.isEmpty()) {
            return false;
        }
        for (String token : requiredTokens) {
            if (roleId != null && roleId.equals(token)) {
                return true;
            }
            if (roleName != null && roleName.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean memberHasAnyRole(Member member, List<String> requiredTokens) {
        if (member == null || requiredTokens.isEmpty()) {
            return false;
        }
        return member.getRoles().stream()
                .anyMatch(role -> matchesRole(role.getId(), role.getName(), requiredTokens));
    }

    private static String normalizeRoleToken(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        if (s.startsWith("<@&") && s.endsWith(">")) {
            s = s.substring(3, s.length() - 1).trim();
        }
        return s;
    }

    private static JDA getActiveJda() {
        if (BotController.INSTANCE == null) {
            return null;
        }
        JDA jda = BotController.INSTANCE.getJDA();
        if (jda == null) {
            return null;
        }
        JDA.Status status = jda.getStatus();
        if (status == JDA.Status.SHUTTING_DOWN
                || status == JDA.Status.SHUTDOWN
                || status == JDA.Status.FAILED_TO_LOGIN) {
            return null;
        }
        return jda;
    }
}
