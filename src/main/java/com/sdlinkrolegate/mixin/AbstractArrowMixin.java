package com.sdlinkrolegate.mixin;

import com.sdlinkrolegate.SDLinkRoleGateMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents unverified players from picking up arrows/tridents off the ground while restricted.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void sdlinkrolegate$preventArrowPickup(Player player, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (!self.level().isClientSide() && SDLinkRoleGateMod.shouldBlockItemPickup(player)) {
            ci.cancel();
        }
    }
}
