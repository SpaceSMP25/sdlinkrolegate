package com.sdlinkrolegate.gate;

import com.hypherionmc.craterlib.api.events.server.CraterPlayerEvent;
import com.hypherionmc.craterlib.impl.api.world.entity.player.BridgedPlayer;
import com.hypherionmc.sdlink.server.ServerEvents;
import com.sdlinkrolegate.compat.LuckPermsHook;
import com.sdlinkrolegate.config.ModConfig;
import com.sdlinkrolegate.discord.DiscordRoleListener;
import com.sdlinkrolegate.discord.RoleLookup;
import com.sdlinkrolegate.discord.SDLinkRoleChecker;
import com.sdlinkrolegate.util.TextUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates role verification, Adventure mode enforcement, item pickup blocking,
 * status effects, bossbar display, login modals, and optional LuckPerms group updates.
 */
public final class RoleGateManager {

    interface PlayerRef {
        UUID uuid();

        String name();

        boolean isDisconnected();

        boolean isOperator();
    }

    interface GateHost {
        boolean isRunning();

        boolean isSameThread();

        void execute(Runnable task);

        PlayerRef getOnlinePlayer(UUID uuid);

        Collection<PlayerRef> getOnlinePlayers();

        void applyRestricted(PlayerRef player, ModConfig.ConfigData config, boolean showModal);

        void applyVerified(PlayerRef player, ModConfig.ConfigData config, boolean wasRestricted);

        void removeLuckPermsGroup(UUID uuid, String groupName);

        void onConfigUpdated(ModConfig.ConfigData config);

        void announceDiscordJoin(PlayerRef player);
    }

    private final ModConfig modConfig;
    private final RoleLookup roleLookup;
    private final SDLinkRoleChecker jdaLifecycle;
    private final DiscordRoleListener discordRoleListener;
    private final GateHost host;
    private final Logger logger;

    private final Set<UUID> restrictedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> suppressedJoinPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> suppressedLeavePlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean missingRoleWarningLogged = new AtomicBoolean(false);

    public RoleGateManager(ModConfig modConfig, SDLinkRoleChecker roleChecker, Logger logger) {
        this.modConfig = modConfig;
        this.roleLookup = roleChecker;
        this.jdaLifecycle = roleChecker;
        this.discordRoleListener = new DiscordRoleListener(this);
        this.logger = logger;
        this.host = new MinecraftGateHost(logger);
    }

    RoleGateManager(ModConfig modConfig, RoleLookup roleLookup, GateHost host, Logger logger) {
        this.modConfig = modConfig;
        this.roleLookup = roleLookup;
        this.jdaLifecycle = roleLookup instanceof SDLinkRoleChecker checker ? checker : null;
        this.discordRoleListener = this.jdaLifecycle != null ? new DiscordRoleListener(this) : null;
        this.host = host;
        this.logger = logger;
    }

    public void onServerStarted(MinecraftServer minecraftServer) {
        if (host instanceof MinecraftGateHost mcHost) {
            mcHost.setServer(minecraftServer);
        }
        host.onConfigUpdated(modConfig.get());
        if (roleLookup.isBotReady()) {
            onBotReady();
        }
    }

    public void onServerStopping(MinecraftServer minecraftServer) {
        if (jdaLifecycle != null) {
            jdaLifecycle.detachJdaListener(discordRoleListener);
        }
        if (host instanceof MinecraftGateHost mcHost) {
            mcHost.shutdown();
        }
        restrictedPlayers.clear();
        suppressedJoinPlayers.clear();
        suppressedLeavePlayers.clear();
    }

    public void onBotReady() {
        if (jdaLifecycle != null) {
            jdaLifecycle.ensureJdaListenerAttached(discordRoleListener);
        }
        if (host.isRunning()) {
            host.execute(() -> evaluateAllOnlinePlayers(false));
        }
    }

    public void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        suppressedLeavePlayers.remove(uuid);
        suppressedJoinPlayers.remove(uuid);
        evaluatePlayer(new MinecraftPlayerRef(player), true);
    }

    void onPlayerJoin(PlayerRef player) {
        suppressedLeavePlayers.remove(player.uuid());
        suppressedJoinPlayers.remove(player.uuid());
        evaluatePlayer(player, true);
    }

    public void onPlayerLoggedInProcessed(UUID uuid) {
        if (uuid != null && restrictedPlayers.contains(uuid) && modConfig.get().restrictions().suppressDiscordMessages()) {
            suppressedJoinPlayers.add(uuid);
        }
    }

    public void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        if (host instanceof MinecraftGateHost mcHost) {
            mcHost.removeBossbarFrom(oldPlayer);
            mcHost.forgetGrantedState(oldPlayer.getUUID());
        }
        if (isOperator(newPlayer)) {
            markVerified(new MinecraftPlayerRef(newPlayer));
            return;
        }
        if (restrictedPlayers.contains(newPlayer.getUUID())) {
            markUnverified(new MinecraftPlayerRef(newPlayer), false, "respawn while unverified");
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        onPlayerDisconnect(player.getUUID());
        if (host instanceof MinecraftGateHost mcHost) {
            mcHost.cleanupDisconnected(player);
        }
    }

    void onPlayerDisconnect(UUID uuid) {
        suppressedJoinPlayers.remove(uuid);
        boolean wasRestricted = restrictedPlayers.remove(uuid);
        if (wasRestricted && modConfig.get().restrictions().suppressDiscordMessages()) {
            suppressedLeavePlayers.add(uuid);
        }
    }

    public void onPlayerLoggedOutComplete(UUID uuid) {
        if (uuid != null) {
            suppressedLeavePlayers.remove(uuid);
        }
    }

    public void onPlayerVerificationChanged(UUID playerUuid) {
        if (!host.isRunning()) {
            return;
        }
        host.execute(() -> {
            PlayerRef player = host.getOnlinePlayer(playerUuid);
            if (player != null && !player.isDisconnected()) {
                boolean wasRestricted = restrictedPlayers.contains(playerUuid);
                evaluatePlayer(player, !wasRestricted);
            }
        });
    }

    public void onDiscordMemberUpdated(String discordUserId) {
        if (!host.isRunning() || discordUserId == null || discordUserId.isBlank()) {
            return;
        }
        host.execute(() -> {
            for (PlayerRef player : host.getOnlinePlayers()) {
                if (player.isDisconnected()) {
                    continue;
                }
                Optional<String> linkedId = roleLookup.getLinkedDiscordId(player.uuid(), player.name());
                if (linkedId.isPresent() && linkedId.get().equals(discordUserId)) {
                    boolean wasRestricted = restrictedPlayers.contains(player.uuid());
                    evaluatePlayer(player, !wasRestricted);
                }
            }
        });
    }

    /**
     * Reloads configuration from disk, updates bossbar/effects styling, and re-evaluates all online players.
     */
    public synchronized void reload() {
        ModConfig.ConfigData previousConfig = modConfig.get();
        String previousGroup = previousConfig.luckPerms().unverifiedGroup();

        missingRoleWarningLogged.set(false);
        modConfig.load();
        ModConfig.ConfigData newConfig = modConfig.get();

        host.onConfigUpdated(newConfig);

        if (!host.isRunning()) {
            return;
        }

        if (previousGroup != null && !previousGroup.isBlank()
                && (!newConfig.luckPerms().enabled() || !previousGroup.equalsIgnoreCase(newConfig.luckPerms().unverifiedGroup()))) {
            for (UUID restrictedUuid : restrictedPlayers) {
                host.removeLuckPermsGroup(restrictedUuid, previousGroup);
            }
        }

        evaluateAllOnlinePlayers(false);
    }

    public boolean isRestricted(Player player) {
        if (player == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && isOperator(serverPlayer)) {
            return false;
        }
        return restrictedPlayers.contains(player.getUUID());
    }

    public boolean isRestricted(UUID uuid) {
        return uuid != null && restrictedPlayers.contains(uuid);
    }

    public boolean shouldBlockItemPickup(Player player) {
        return modConfig.get().restrictions().blockItemPickup() && isRestricted(player);
    }

    public boolean shouldBlockChat(Player player) {
        return modConfig.get().restrictions().blockChat() && isRestricted(player);
    }

    boolean shouldBlockChat(UUID uuid) {
        return modConfig.get().restrictions().blockChat() && isRestricted(uuid);
    }

    public boolean shouldSuppressDiscordMessages(UUID uuid) {
        if (uuid == null || !modConfig.get().restrictions().suppressDiscordMessages()) {
            return false;
        }
        return restrictedPlayers.contains(uuid) || suppressedLeavePlayers.contains(uuid);
    }

    public void sendChatDeniedMessage(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ModConfig.ConfigData config = modConfig.get();
        player.sendSystemMessage(TextUtil.format(config.messages().chatDenied(), config.discordInviteLink()));
    }

    public boolean shouldHideFromPlayerList(UUID uuid) {
        ModConfig.InvisibilityConfig invisibility = modConfig.get().invisibility();
        return invisibility.enabled() && invisibility.hideFromPlayerList() && isRestricted(uuid);
    }

    public boolean shouldHideEntityFrom(ServerPlayer target, ServerPlayer observer) {
        if (target == null || observer == null) {
            return false;
        }
        return isRestricted(target) && shouldHideEntity(target.getUUID(), observer.getUUID());
    }

    boolean shouldHideEntity(UUID targetUuid, UUID observerUuid) {
        if (targetUuid == null || observerUuid == null || targetUuid.equals(observerUuid)) {
            return false;
        }
        ModConfig.InvisibilityConfig invisibility = modConfig.get().invisibility();
        return invisibility.enabled() && invisibility.hideNametag() && isRestricted(targetUuid);
    }

    public boolean isOperator(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }

    private void evaluateAllOnlinePlayers(boolean allowModal) {
        if (!host.isRunning()) {
            return;
        }
        for (PlayerRef player : host.getOnlinePlayers()) {
            if (!player.isDisconnected()) {
                evaluatePlayer(player, allowModal);
            }
        }
    }

    private void evaluatePlayer(PlayerRef player, boolean allowModal) {
        if (player.isDisconnected()) {
            return;
        }

        if (player.isOperator()) {
            markVerified(player);
            return;
        }

        ModConfig.ConfigData config = modConfig.get();
        String requiredRoleId = config.requiredRoleId();
        if (requiredRoleId == null || requiredRoleId.isBlank()) {
            if (missingRoleWarningLogged.compareAndSet(false, true)) {
                logger.warn("requiredRoleId is empty in config/sdlinkrolegate.toml; treating non-OP players as unverified.");
            }
            markUnverified(player, allowModal, "requiredRoleId not configured");
            return;
        }

        if (!roleLookup.isBotReady()) {
            markUnverified(player, allowModal, "SDLink bot is not ready");
            return;
        }

        Optional<String> linkedDiscordId = roleLookup.getLinkedDiscordId(player.uuid(), player.name());
        if (linkedDiscordId.isEmpty()) {
            markUnverified(player, allowModal, "player not linked in SDLink");
            return;
        }

        UUID playerUuid = player.uuid();
        boolean resolvedSynchronously = roleLookup.checkHasRole(
                linkedDiscordId.get(),
                requiredRoleId,
                hasRole -> {
                    if (!host.isRunning()) {
                        return;
                    }
                    // If invoked synchronously (e.g. during JOIN before playersByUUID is populated), use `player` directly.
                    final boolean sameThreadCallback = host.isSameThread();
                    Runnable apply = () -> {
                        PlayerRef target = sameThreadCallback
                                ? (player.isDisconnected() ? null : player)
                                : host.getOnlinePlayer(playerUuid);
                        if (target == null || target.isDisconnected()) {
                            return;
                        }
                        if (target.isOperator() || hasRole) {
                            markVerified(target);
                        } else {
                            markUnverified(target, allowModal, "missing required Discord role " + requiredRoleId);
                        }
                    };
                    if (sameThreadCallback) {
                        apply.run();
                    } else {
                        host.execute(apply);
                    }
                }
        );

        // If an async Discord REST lookup is pending, apply fail-safe restriction immediately without flashing the modal yet.
        if (!resolvedSynchronously) {
            markUnverified(player, false, "pending async Discord role lookup");
        }
    }

    private void markUnverified(PlayerRef player, boolean showModal, String reason) {
        boolean newlyRestricted = restrictedPlayers.add(player.uuid());
        if (newlyRestricted) {
            logger.info("Restricting {} ({}): {}", player.name(), player.uuid(), reason);
        }
        host.applyRestricted(player, modConfig.get(), showModal);
    }

    private void markVerified(PlayerRef player) {
        boolean wasRestricted = restrictedPlayers.remove(player.uuid());
        boolean hadSuppressedJoin = suppressedJoinPlayers.remove(player.uuid());
        if (wasRestricted) {
            logger.info("Verified {} ({}); lifting restrictions.", player.name(), player.uuid());
        }
        host.applyVerified(player, modConfig.get(), wasRestricted);
        if (hadSuppressedJoin && modConfig.get().restrictions().suppressDiscordMessages()) {
            host.announceDiscordJoin(player);
        }
    }

    private record MinecraftPlayerRef(ServerPlayer player) implements PlayerRef {
        @Override
        public UUID uuid() {
            return player.getUUID();
        }

        @Override
        public String name() {
            return player.nameAndId().name();
        }

        @Override
        public boolean isDisconnected() {
            return player.hasDisconnected();
        }

        @Override
        public boolean isOperator() {
            return player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
        }
    }

    private static final class MinecraftGateHost implements GateHost {
        private final Logger logger;
        private final boolean luckPermsLoaded;
        private final Map<UUID, Set<Holder<MobEffect>>> grantedEffects = new ConcurrentHashMap<>();
        private final Set<UUID> grantedInvisibility = ConcurrentHashMap.newKeySet();
        private final Set<UUID> hiddenInPlayerList = ConcurrentHashMap.newKeySet();
        private final Set<Identifier> warnedUnknownEffects = ConcurrentHashMap.newKeySet();

        private volatile MinecraftServer server;
        private volatile ServerBossEvent bossBar;
        private volatile String activeBossbarId = "";

        private MinecraftGateHost(Logger logger) {
            this.logger = logger;
            this.luckPermsLoaded = FabricLoader.getInstance().isModLoaded("luckperms");
            if (this.luckPermsLoaded) {
                logger.info("LuckPerms detected; group sync for unverified players is enabled (respecting config).");
            }
        }

        private void setServer(MinecraftServer server) {
            this.server = server;
        }

        private void shutdown() {
            MinecraftServer srv = this.server;
            if (srv != null) {
                for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
                    clearGrantedEffects(player);
                    clearGrantedInvisibility(player);
                }
            }
            grantedEffects.clear();
            grantedInvisibility.clear();
            hiddenInPlayerList.clear();
            ServerBossEvent bar = this.bossBar;
            if (bar != null) {
                bar.removeAllPlayers();
            }
            this.server = null;
        }

        private void removeBossbarFrom(ServerPlayer player) {
            ServerBossEvent bar = this.bossBar;
            if (bar != null) {
                bar.removePlayer(player);
            }
        }

        private void forgetGrantedState(UUID uuid) {
            grantedEffects.remove(uuid);
            grantedInvisibility.remove(uuid);
        }

        private void cleanupDisconnected(ServerPlayer player) {
            removeBossbarFrom(player);
            clearGrantedEffects(player);
            clearGrantedInvisibility(player);
            hiddenInPlayerList.remove(player.getUUID());
        }

        @Override
        public boolean isRunning() {
            return server != null;
        }

        @Override
        public boolean isSameThread() {
            MinecraftServer srv = this.server;
            return srv != null && srv.isSameThread();
        }

        @Override
        public void execute(Runnable task) {
            MinecraftServer srv = this.server;
            if (srv != null) {
                srv.execute(task);
            }
        }

        @Override
        public PlayerRef getOnlinePlayer(UUID uuid) {
            MinecraftServer srv = this.server;
            if (srv == null) {
                return null;
            }
            ServerPlayer player = srv.getPlayerList().getPlayer(uuid);
            return player != null ? new MinecraftPlayerRef(player) : null;
        }

        @Override
        public Collection<PlayerRef> getOnlinePlayers() {
            MinecraftServer srv = this.server;
            if (srv == null) {
                return java.util.List.of();
            }
            return srv.getPlayerList().getPlayers().stream()
                    .<PlayerRef>map(MinecraftPlayerRef::new)
                    .toList();
        }

        @Override
        public void applyRestricted(PlayerRef playerRef, ModConfig.ConfigData config, boolean showModal) {
            ServerPlayer player = ((MinecraftPlayerRef) playerRef).player();
            if (player.gameMode() != GameType.ADVENTURE) {
                player.setGameMode(GameType.ADVENTURE);
            }

            syncRestrictedEffects(player, config.effects());
            syncStealth(player, config.invisibility(), true);

            ServerBossEvent bar = this.bossBar;
            if (bar != null) {
                if (config.bossbar().enabled()) {
                    bar.addPlayer(player);
                } else {
                    bar.removePlayer(player);
                }
            }

            if (luckPermsLoaded && config.luckPerms().enabled()) {
                LuckPermsHook.addPlayerToGroup(player.getUUID(), config.luckPerms().unverifiedGroup(), logger);
            }

            if (showModal && config.modal().enabled()) {
                DialogHelper.showVerificationDialog(player, config);
            }
        }

        @Override
        public void applyVerified(PlayerRef playerRef, ModConfig.ConfigData config, boolean wasRestricted) {
            ServerPlayer player = ((MinecraftPlayerRef) playerRef).player();
            ServerBossEvent bar = this.bossBar;
            if (bar != null) {
                bar.removePlayer(player);
            }

            clearGrantedEffects(player);
            syncStealth(player, config.invisibility(), false);
            if (wasRestricted || (!playerRef.isOperator() && player.gameMode() == GameType.ADVENTURE)) {
                removeConfiguredInfiniteEffects(player, config.effects());
                removeInfiniteInvisibility(player);
                if (player.gameMode() != GameType.SURVIVAL) {
                    player.setGameMode(GameType.SURVIVAL);
                }
            }

            if (luckPermsLoaded && config.luckPerms().enabled()) {
                LuckPermsHook.removePlayerFromGroup(player.getUUID(), config.luckPerms().unverifiedGroup(), logger);
            }
        }

        @Override
        public void removeLuckPermsGroup(UUID uuid, String groupName) {
            if (luckPermsLoaded) {
                LuckPermsHook.removePlayerFromGroup(uuid, groupName, logger);
            }
        }

        @Override
        public void announceDiscordJoin(PlayerRef playerRef) {
            ServerPlayer player = ((MinecraftPlayerRef) playerRef).player();
            MinecraftServer srv = this.server;
            if (srv == null || player.hasDisconnected() || srv.getPlayerList().getPlayer(player.getUUID()) == null) {
                return;
            }
            try {
                ServerEvents.getInstance().playerJoinEvent(
                        new CraterPlayerEvent.PlayerLoggedIn(BridgedPlayer.wrap(player))
                );
            } catch (Exception e) {
                logger.warn("Failed to replay deferred SDLink join announcement for {} ({})",
                        player.nameAndId().name(), player.getUUID(), e);
            }
        }

        @Override
        public void onConfigUpdated(ModConfig.ConfigData config) {
            warnedUnknownEffects.clear();
            ModConfig.BossbarConfig bb = config.bossbar();
            String desiredId = (bb.id() == null || bb.id().isBlank()) ? "sdlinkrolegate:unverified" : bb.id();
            Component title = TextUtil.format(bb.text(), config.discordInviteLink());

            ServerBossEvent existing = this.bossBar;
            if (existing == null || !desiredId.equals(this.activeBossbarId)) {
                if (existing != null) {
                    existing.removeAllPlayers();
                }
                UUID uuid = UUID.nameUUIDFromBytes(desiredId.getBytes(StandardCharsets.UTF_8));
                ServerBossEvent created = new ServerBossEvent(uuid, title, bb.color(), bb.style());
                created.setProgress(1.0f);
                created.setVisible(bb.enabled());
                this.activeBossbarId = desiredId;
                this.bossBar = created;
            } else {
                existing.setName(title);
                existing.setColor(bb.color());
                existing.setOverlay(bb.style());
                existing.setProgress(1.0f);
                existing.setVisible(bb.enabled());
                if (!bb.enabled()) {
                    existing.removeAllPlayers();
                }
            }
        }

        private void syncRestrictedEffects(ServerPlayer player, ModConfig.EffectsConfig effectsConfig) {
            if (!effectsConfig.enabled()) {
                clearGrantedEffects(player);
                return;
            }

            Map<Holder<MobEffect>, Integer> desired = new LinkedHashMap<>();
            for (ModConfig.EffectSpec spec : effectsConfig.effects()) {
                Optional<Holder.Reference<MobEffect>> resolved = BuiltInRegistries.MOB_EFFECT.get(spec.effectId());
                if (resolved.isEmpty()) {
                    if (warnedUnknownEffects.add(spec.effectId())) {
                        logger.warn("Unknown status effect '{}' in config/sdlinkrolegate.toml (effects.list), skipping.", spec.effectId());
                    }
                    continue;
                }
                desired.put(resolved.get(), spec.amplifier());
            }

            UUID uuid = player.getUUID();
            Set<Holder<MobEffect>> previous = grantedEffects.getOrDefault(uuid, Set.of());
            for (Holder<MobEffect> oldEffect : previous) {
                if (!desired.containsKey(oldEffect)) {
                    player.removeEffect(oldEffect);
                }
            }

            boolean showParticles = effectsConfig.showParticles();
            boolean showIcon = effectsConfig.showIcon();
            for (Map.Entry<Holder<MobEffect>, Integer> entry : desired.entrySet()) {
                Holder<MobEffect> effectHolder = entry.getKey();
                int amplifier = entry.getValue();
                MobEffectInstance current = player.getEffect(effectHolder);
                if (current == null
                        || !current.isInfiniteDuration()
                        || current.getAmplifier() != amplifier
                        || current.isVisible() != showParticles
                        || current.showIcon() != showIcon) {
                    player.addEffect(new MobEffectInstance(
                            effectHolder,
                            MobEffectInstance.INFINITE_DURATION,
                            amplifier,
                            false,
                            showParticles,
                            showIcon
                    ));
                }
            }

            grantedEffects.put(uuid, Set.copyOf(desired.keySet()));
        }

        private void clearGrantedEffects(ServerPlayer player) {
            Set<Holder<MobEffect>> granted = grantedEffects.remove(player.getUUID());
            if (granted != null) {
                for (Holder<MobEffect> effect : granted) {
                    player.removeEffect(effect);
                }
            }
        }

        private void removeConfiguredInfiniteEffects(ServerPlayer player, ModConfig.EffectsConfig effectsConfig) {
            for (ModConfig.EffectSpec spec : effectsConfig.effects()) {
                BuiltInRegistries.MOB_EFFECT.get(spec.effectId()).ifPresent(holder -> {
                    MobEffectInstance existing = player.getEffect(holder);
                    if (existing != null && existing.isInfiniteDuration()) {
                        player.removeEffect(holder);
                    }
                });
            }
        }

        private void syncStealth(ServerPlayer player, ModConfig.InvisibilityConfig invisConfig, boolean restricted) {
            UUID uuid = player.getUUID();
            boolean shouldBeInvisible = restricted && invisConfig.enabled();
            if (shouldBeInvisible) {
                MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
                if (current == null
                        || !current.isInfiniteDuration()
                        || current.getAmplifier() != 0
                        || current.isVisible()
                        || current.showIcon()) {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.INVISIBILITY,
                            MobEffectInstance.INFINITE_DURATION,
                            0,
                            false,
                            false,
                            false
                    ));
                }
                grantedInvisibility.add(uuid);
            } else {
                clearGrantedInvisibility(player);
            }

            boolean shouldHideListed = restricted && invisConfig.enabled() && invisConfig.hideFromPlayerList();
            boolean currentlyHidden = hiddenInPlayerList.contains(uuid);
            if (shouldHideListed != currentlyHidden) {
                if (shouldHideListed) {
                    hiddenInPlayerList.add(uuid);
                } else {
                    hiddenInPlayerList.remove(uuid);
                }
                MinecraftServer srv = this.server;
                if (srv != null && srv.getPlayerList().getPlayer(uuid) != null) {
                    srv.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                            player
                    ));
                }
            }
        }

        private void clearGrantedInvisibility(ServerPlayer player) {
            UUID uuid = player.getUUID();
            if (grantedInvisibility.remove(uuid)) {
                Set<Holder<MobEffect>> activeEffects = grantedEffects.get(uuid);
                if (activeEffects == null || !activeEffects.contains(MobEffects.INVISIBILITY)) {
                    player.removeEffect(MobEffects.INVISIBILITY);
                }
            }
        }

        private void removeInfiniteInvisibility(ServerPlayer player) {
            MobEffectInstance existing = player.getEffect(MobEffects.INVISIBILITY);
            if (existing != null && existing.isInfiniteDuration()) {
                player.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }
}
