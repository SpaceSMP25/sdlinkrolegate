package com.sdlinkrolegate.gate;

import com.sdlinkrolegate.config.ModConfig;
import com.sdlinkrolegate.discord.RoleLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleGateManagerTest {

    @TempDir
    Path tempDir;

    private ModConfig modConfig;
    private FakeRoleLookup roleLookup;
    private FakeGateHost host;
    private RoleGateManager manager;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tempDir.resolve("sdlinkrolegate.toml"), """
                requiredRoleId = "999888777"
                discordInviteLink = "https://discord.gg/test"
                """);
        modConfig = new ModConfig(tempDir, LoggerFactory.getLogger("test"));
        modConfig.load();
        roleLookup = new FakeRoleLookup();
        host = new FakeGateHost();
        manager = new RoleGateManager(modConfig, roleLookup, host, LoggerFactory.getLogger("test"));
    }

    @Test
    void verifiedUserWithoutRoleIsRestrictedOnJoinEvenBeforePlayerListRegistersUuid() {
        UUID uuid = UUID.randomUUID();
        TestPlayer player = new TestPlayer(uuid, "VerifiedNoRole", false, false);
        roleLookup.linkedIds.put(uuid, "discord-123");
        roleLookup.membersWithRole.put("discord-123", false);

        // Simulate Fabric ServerPlayConnectionEvents.JOIN timing: player is NOT yet in PlayerList map!
        assertFalse(host.onlinePlayers.containsKey(uuid));
        manager.onPlayerJoin(player);

        assertTrue(manager.isRestricted(uuid));
        assertEquals(1, host.restrictedCalls.getOrDefault(uuid, 0));
        assertEquals(1, host.modalCalls.getOrDefault(uuid, 0));
        assertEquals(0, host.verifiedCalls.getOrDefault(uuid, 0));
    }

    @Test
    void verifiedUserWithRoleIsAllowedOnJoinBeforePlayerListRegistersUuid() {
        UUID uuid = UUID.randomUUID();
        TestPlayer player = new TestPlayer(uuid, "VerifiedWithRole", false, false);
        roleLookup.linkedIds.put(uuid, "discord-456");
        roleLookup.membersWithRole.put("discord-456", true);

        manager.onPlayerJoin(player);

        assertFalse(manager.isRestricted(uuid));
        assertEquals(0, host.restrictedCalls.getOrDefault(uuid, 0));
        assertEquals(1, host.verifiedCalls.getOrDefault(uuid, 0));
    }

    @Test
    void unlinkedUserIsRestrictedWithModalOnJoin() {
        UUID uuid = UUID.randomUUID();
        TestPlayer player = new TestPlayer(uuid, "UnlinkedUser", false, false);

        manager.onPlayerJoin(player);

        assertTrue(manager.isRestricted(uuid));
        assertEquals(1, host.restrictedCalls.getOrDefault(uuid, 0));
        assertEquals(1, host.modalCalls.getOrDefault(uuid, 0));
    }

    @Test
    void asyncRoleCheckLocksFailSafeThenUnlocksOrShowsModalWhenResolved() {
        UUID noRoleUuid = UUID.randomUUID();
        TestPlayer noRolePlayer = new TestPlayer(noRoleUuid, "AsyncNoRole", false, false);
        roleLookup.linkedIds.put(noRoleUuid, "discord-async-no");
        roleLookup.sync = false;

        manager.onPlayerJoin(noRolePlayer);
        // Immediately fail-safe restricted without modal while REST call is pending.
        assertTrue(manager.isRestricted(noRoleUuid));
        assertEquals(1, host.restrictedCalls.getOrDefault(noRoleUuid, 0));
        assertEquals(0, host.modalCalls.getOrDefault(noRoleUuid, 0));

        // Player finishes joining into PlayerList before next tick runs async callback.
        host.onlinePlayers.put(noRoleUuid, noRolePlayer);
        roleLookup.completeAsync(false);
        host.drainTasks();

        assertTrue(manager.isRestricted(noRoleUuid));
        assertEquals(1, host.modalCalls.getOrDefault(noRoleUuid, 0));

        // Now test async unlock when member does have the role.
        UUID hasRoleUuid = UUID.randomUUID();
        TestPlayer hasRolePlayer = new TestPlayer(hasRoleUuid, "AsyncHasRole", false, false);
        roleLookup.linkedIds.put(hasRoleUuid, "discord-async-yes");

        manager.onPlayerJoin(hasRolePlayer);
        assertTrue(manager.isRestricted(hasRoleUuid));

        host.onlinePlayers.put(hasRoleUuid, hasRolePlayer);
        roleLookup.completeAsync(true);
        host.drainTasks();

        assertFalse(manager.isRestricted(hasRoleUuid));
        assertEquals(1, host.verifiedCalls.getOrDefault(hasRoleUuid, 0));
        assertEquals(0, host.modalCalls.getOrDefault(hasRoleUuid, 0));
    }

    @Test
    void onlineUserWhoVerifiesWithoutRoleRemainsRestrictedUntilRoleGranted() {
        UUID uuid = UUID.randomUUID();
        TestPlayer player = new TestPlayer(uuid, "LiveVerifyUser", false, false);
        host.onlinePlayers.put(uuid, player);

        // Joins unlinked.
        manager.onPlayerJoin(player);
        assertTrue(manager.isRestricted(uuid));
        assertEquals(1, host.modalCalls.getOrDefault(uuid, 0));

        // Links SDLink account without the required role.
        roleLookup.linkedIds.put(uuid, "discord-789");
        roleLookup.membersWithRole.put("discord-789", false);
        manager.onPlayerVerificationChanged(uuid);
        host.drainTasks();

        assertTrue(manager.isRestricted(uuid));
        assertEquals(1, host.modalCalls.getOrDefault(uuid, 0));

        // Gains the required Discord role while online -> immediately unlocked.
        roleLookup.membersWithRole.put("discord-789", true);
        manager.onDiscordMemberUpdated("discord-789");
        host.drainTasks();

        assertFalse(manager.isRestricted(uuid));
        assertEquals(1, host.verifiedCalls.getOrDefault(uuid, 0));

        // Loses the required Discord role while online -> immediately restricted + modal shown.
        roleLookup.membersWithRole.put("discord-789", false);
        manager.onDiscordMemberUpdated("discord-789");
        host.drainTasks();

        assertTrue(manager.isRestricted(uuid));
        assertEquals(2, host.modalCalls.getOrDefault(uuid, 0));
    }

    @Test
    void operatorBypassesAllRoleChecks() {
        UUID uuid = UUID.randomUUID();
        TestPlayer op = new TestPlayer(uuid, "ServerAdmin", false, true);

        manager.onPlayerJoin(op);

        assertFalse(manager.isRestricted(uuid));
        assertEquals(0, host.restrictedCalls.getOrDefault(uuid, 0));
        assertEquals(1, host.verifiedCalls.getOrDefault(uuid, 0));
    }

    @Test
    void reloadUpdatesEffectsAndPickupConfigSnapshot() throws Exception {
        ModConfig.ConfigData initial = modConfig.get();
        assertTrue(initial.restrictions().blockItemPickup());
        assertTrue(initial.effects().enabled());
        assertFalse(initial.effects().showParticles());
        assertEquals(2, initial.effects().effects().size());

        Files.writeString(tempDir.resolve("sdlinkrolegate.toml"), """
                requiredRoleId = "999888777"

                [restrictions]
                blockItemPickup = false

                [effects]
                enabled = false
                showParticles = true
                list = ["minecraft:slowness:1"]
                """);

        manager.reload();

        ModConfig.ConfigData reloaded = modConfig.get();
        assertFalse(reloaded.restrictions().blockItemPickup());
        assertFalse(reloaded.effects().enabled());
        assertTrue(reloaded.effects().showParticles());
        assertEquals(1, reloaded.effects().effects().size());
        assertEquals("minecraft:slowness", reloaded.effects().effects().get(0).effectId().toString());
        assertEquals(1, reloaded.effects().effects().get(0).amplifier());
    }

    private record TestPlayer(UUID uuid, String name, boolean isDisconnected, boolean isOperator)
            implements RoleGateManager.PlayerRef {
    }

    private static final class FakeRoleLookup implements RoleLookup {
        final Map<UUID, String> linkedIds = new HashMap<>();
        final Map<String, Boolean> membersWithRole = new HashMap<>();
        final Queue<Consumer<Boolean>> pendingCallbacks = new ArrayDeque<>();
        boolean botReady = true;
        boolean sync = true;

        @Override
        public boolean isBotReady() {
            return botReady;
        }

        @Override
        public Optional<String> getLinkedDiscordId(UUID playerUuid, String playerName) {
            return Optional.ofNullable(linkedIds.get(playerUuid));
        }

        @Override
        public boolean checkHasRole(String discordUserId, String requiredRoleId, Consumer<Boolean> onResult) {
            if (sync) {
                onResult.accept(membersWithRole.getOrDefault(discordUserId, false));
                return true;
            }
            pendingCallbacks.add(onResult);
            return false;
        }

        void completeAsync(boolean result) {
            Consumer<Boolean> cb = pendingCallbacks.poll();
            if (cb != null) {
                cb.accept(result);
            }
        }
    }

    private static final class FakeGateHost implements RoleGateManager.GateHost {
        final Map<UUID, RoleGateManager.PlayerRef> onlinePlayers = new HashMap<>();
        final Map<UUID, Integer> restrictedCalls = new HashMap<>();
        final Map<UUID, Integer> verifiedCalls = new HashMap<>();
        final Map<UUID, Integer> modalCalls = new HashMap<>();
        final Queue<Runnable> queuedTasks = new ArrayDeque<>();
        boolean sameThread = true;

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public boolean isSameThread() {
            return sameThread;
        }

        @Override
        public void execute(Runnable task) {
            queuedTasks.add(task);
        }

        void drainTasks() {
            boolean prev = sameThread;
            sameThread = true;
            try {
                while (!queuedTasks.isEmpty()) {
                    queuedTasks.poll().run();
                }
            } finally {
                sameThread = prev;
            }
        }

        @Override
        public RoleGateManager.PlayerRef getOnlinePlayer(UUID uuid) {
            return onlinePlayers.get(uuid);
        }

        @Override
        public Collection<RoleGateManager.PlayerRef> getOnlinePlayers() {
            return onlinePlayers.values();
        }

        @Override
        public void applyRestricted(RoleGateManager.PlayerRef player, ModConfig.ConfigData config, boolean showModal) {
            restrictedCalls.merge(player.uuid(), 1, Integer::sum);
            if (showModal) {
                modalCalls.merge(player.uuid(), 1, Integer::sum);
            }
        }

        @Override
        public void applyVerified(RoleGateManager.PlayerRef player, ModConfig.ConfigData config, boolean wasRestricted) {
            verifiedCalls.merge(player.uuid(), 1, Integer::sum);
        }

        @Override
        public void removeLuckPermsGroup(UUID uuid, String groupName) {
        }

        @Override
        public void onConfigUpdated(ModConfig.ConfigData config) {
        }
    }
}
