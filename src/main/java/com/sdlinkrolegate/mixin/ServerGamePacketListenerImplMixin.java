package com.sdlinkrolegate.mixin;

import com.sdlinkrolegate.SDLinkRoleGateMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "tryHandleChat",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"
            ),
            cancellable = true
    )
    private void sdlinkrolegate$blockUnverifiedChat(
            String message,
            boolean isCommand,
            Runnable chatFunction,
            CallbackInfo ci
    ) {
        if (isCommand) {
            return;
        }
        ServerPlayer sender = this.player;
        if (SDLinkRoleGateMod.shouldBlockChat(sender)) {
            MinecraftServer server = sender.level().getServer();
            if (server != null) {
                server.execute(() -> SDLinkRoleGateMod.sendChatDeniedMessage(sender));
            }
            ci.cancel();
        }
    }
}
