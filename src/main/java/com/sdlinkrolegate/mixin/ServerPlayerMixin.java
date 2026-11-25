package com.sdlinkrolegate.mixin;

import com.sdlinkrolegate.SDLinkRoleGateMod;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void sdlinkrolegate$hideFromObserver(ServerPlayer observer, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (SDLinkRoleGateMod.shouldHideEntityFrom(self, observer)) {
            cir.setReturnValue(false);
        }
    }
}
