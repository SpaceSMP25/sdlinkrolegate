package com.sdlinkrolegate.discord;

import com.hypherionmc.craterlib.api.events.server.CraterPlayerEvent;
import com.hypherionmc.craterlib.core.event.CraterEventPriority;
import com.hypherionmc.craterlib.core.event.annot.CraterEventListener;
import com.hypherionmc.sdlink.api.accounts.MinecraftAccount;
import com.hypherionmc.sdlink.api.events.SDLinkReadyEvent;
import com.hypherionmc.sdlink.api.events.VerificationEvent;
import com.sdlinkrolegate.gate.RoleGateManager;

/**
 * Listens to SDLink's CraterLib events (bot ready and account link/unlink events).
 */
public final class SDLinkEventListener {

    private final RoleGateManager roleGateManager;

    public SDLinkEventListener(RoleGateManager roleGateManager) {
        this.roleGateManager = roleGateManager;
    }

    @CraterEventListener
    public void onSDLinkReady(SDLinkReadyEvent event) {
        roleGateManager.onBotReady();
    }

    @CraterEventListener
    public void onPlayerVerified(VerificationEvent.PlayerVerified event) {
        MinecraftAccount account = event.getAccount();
        if (account != null && account.getUuid() != null) {
            roleGateManager.onPlayerVerificationChanged(account.getUuid());
        }
    }

    @CraterEventListener
    public void onPlayerUnverified(VerificationEvent.PlayerUnverified event) {
        MinecraftAccount account = event.getAccount();
        if (account != null && account.getUuid() != null) {
            roleGateManager.onPlayerVerificationChanged(account.getUuid());
        }
    }

    @CraterEventListener(priority = CraterEventPriority.LOWEST)
    public void onPlayerLoggedIn(CraterPlayerEvent.PlayerLoggedIn event) {
        if (event.getPlayer() != null && event.getPlayer().getUUID() != null) {
            roleGateManager.onPlayerLoggedInProcessed(event.getPlayer().getUUID());
        }
    }

    @CraterEventListener(priority = CraterEventPriority.LOWEST)
    public void onPlayerLoggedOut(CraterPlayerEvent.PlayerLoggedOut event) {
        if (event.getPlayer() != null && event.getPlayer().getUUID() != null) {
            roleGateManager.onPlayerLoggedOutComplete(event.getPlayer().getUUID());
        }
    }
}
