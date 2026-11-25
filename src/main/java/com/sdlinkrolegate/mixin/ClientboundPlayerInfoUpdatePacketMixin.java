package com.sdlinkrolegate.mixin;

import com.sdlinkrolegate.SDLinkRoleGateMod;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public abstract class ClientboundPlayerInfoUpdatePacketMixin {

    @Mutable
    @Shadow
    @Final
    private List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    @Inject(
            method = "<init>(Ljava/util/EnumSet;Ljava/util/Collection;)V",
            at = @At("RETURN")
    )
    private void sdlinkrolegate$hideUnverifiedPlayersInBatch(
            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions,
            Collection<ServerPlayer> players,
            CallbackInfo ci
    ) {
        sdlinkrolegate$rewriteHiddenEntries();
    }

    @Inject(
            method = "<init>(Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket$Action;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("RETURN")
    )
    private void sdlinkrolegate$hideUnverifiedPlayer(
            ClientboundPlayerInfoUpdatePacket.Action action,
            ServerPlayer player,
            CallbackInfo ci
    ) {
        sdlinkrolegate$rewriteHiddenEntries();
    }

    @Unique
    private void sdlinkrolegate$rewriteHiddenEntries() {
        List<ClientboundPlayerInfoUpdatePacket.Entry> current = this.entries;
        if (current == null || current.isEmpty()) {
            return;
        }

        boolean modified = false;
        List<ClientboundPlayerInfoUpdatePacket.Entry> updated = new ArrayList<>(current.size());
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : current) {
            if (entry.listed() && SDLinkRoleGateMod.shouldHideFromPlayerList(entry.profileId())) {
                updated.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId(),
                        entry.profile(),
                        false,
                        entry.latency(),
                        entry.gameMode(),
                        entry.displayName(),
                        entry.showHat(),
                        entry.listOrder(),
                        entry.chatSession()
                ));
                modified = true;
            } else {
                updated.add(entry);
            }
        }

        if (modified) {
            this.entries = List.copyOf(updated);
        }
    }
}
