package com.sdlinkrolegate;

import com.hypherionmc.craterlib.core.event.CraterEventBus;
import com.sdlinkrolegate.command.RoleGateCommand;
import com.sdlinkrolegate.config.ModConfig;
import com.sdlinkrolegate.discord.SDLinkEventListener;
import com.sdlinkrolegate.discord.SDLinkRoleChecker;
import com.sdlinkrolegate.gate.InteractionProtectionHandler;
import com.sdlinkrolegate.gate.RoleGateManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated server entry point for Simple Discord Link Role Gate.
 */
public final class SDLinkRoleGateMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "sdlinkrolegate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static volatile RoleGateManager activeManager;

    public static boolean shouldBlockItemPickup(Player player) {
        RoleGateManager manager = activeManager;
        return manager != null && manager.shouldBlockItemPickup(player);
    }

    @Override
    public void onInitializeServer() {
        ModConfig modConfig = new ModConfig(FabricLoader.getInstance().getConfigDir(), LOGGER);
        modConfig.load();

        SDLinkRoleChecker roleChecker = new SDLinkRoleChecker(LOGGER);
        RoleGateManager roleGateManager = new RoleGateManager(modConfig, roleChecker, LOGGER);
        activeManager = roleGateManager;

        ServerLifecycleEvents.SERVER_STARTED.register(roleGateManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(roleGateManager::onServerStopping);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                roleGateManager.onPlayerJoin(handler.getPlayer())
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                roleGateManager.onPlayerDisconnect(handler.getPlayer())
        );
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                roleGateManager.onPlayerRespawn(oldPlayer, newPlayer)
        );

        new InteractionProtectionHandler(roleGateManager, modConfig).register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RoleGateCommand.register(dispatcher, roleGateManager)
        );

        CraterEventBus.INSTANCE.registerEventListener(new SDLinkEventListener(roleGateManager));

        LOGGER.info("Simple Discord Link Role Gate initialized.");
    }
}
