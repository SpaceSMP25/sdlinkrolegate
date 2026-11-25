package com.sdlinkrolegate.mixin;

import com.hypherionmc.craterlib.api.game.world.entity.player.CraterPlayer;
import com.hypherionmc.sdlink.platform.SDLinkMCPlatform;
import com.sdlinkrolegate.SDLinkRoleGateMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SDLinkMCPlatform.class, remap = false)
public abstract class SDLinkMCPlatformMixin {

    @Inject(method = "playerIsActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void sdlinkrolegate$hideUnverifiedPlayerFromDiscord(
            CraterPlayer player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (player != null && SDLinkRoleGateMod.shouldSuppressDiscordMessages(player.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
