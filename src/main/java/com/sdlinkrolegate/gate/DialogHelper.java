package com.sdlinkrolegate.gate;

import com.sdlinkrolegate.config.ModConfig;
import com.sdlinkrolegate.util.TextUtil;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Builds and dispatches native Minecraft 26.3 server-driven Dialog modals.
 */
public final class DialogHelper {

    private DialogHelper() {
    }

    /**
     * Opens the configured verification prompt modal for the player.
     */
    public static void showVerificationDialog(ServerPlayer player, ModConfig.ConfigData config) {
        ModConfig.ModalConfig modal = config.modal();
        if (!modal.enabled()) {
            return;
        }

        Component title = TextUtil.format(modal.title(), config.discordInviteLink());
        Component body = TextUtil.format(modal.body(), config.discordInviteLink());

        CommonDialogData commonData = new CommonDialogData(
                title,
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(body, PlainMessage.DEFAULT_WIDTH)),
                List.of()
        );

        Component closeLabel = TextUtil.format(
                modal.closeButtonText().isBlank() ? "Close" : modal.closeButtonText(),
                config.discordInviteLink()
        );
        ActionButton closeButton = new ActionButton(
                new CommonButtonData(closeLabel, CommonButtonData.DEFAULT_WIDTH),
                Optional.empty()
        );

        Optional<URI> inviteUri = parseHttpUri(config.discordInviteLink());
        Dialog dialog;
        if (inviteUri.isPresent()) {
            Component joinLabel = TextUtil.format(
                    modal.inviteButtonText().isBlank() ? "Join Discord" : modal.inviteButtonText(),
                    config.discordInviteLink()
            );
            ActionButton joinButton = new ActionButton(
                    new CommonButtonData(joinLabel, CommonButtonData.DEFAULT_WIDTH),
                    Optional.of(new StaticAction(new ClickEvent.OpenUrl(inviteUri.get())))
            );
            dialog = new MultiActionDialog(commonData, List.of(joinButton), Optional.of(closeButton), 1);
        } else {
            dialog = new NoticeDialog(commonData, closeButton);
        }

        player.openDialog(Holder.direct(dialog));
    }

    /**
     * Dismisses any active server-driven dialog on the player's client.
     */
    public static void clearDialog(ServerPlayer player) {
        if (player != null && !player.hasDisconnected() && player.connection != null) {
            player.connection.send(ClientboundClearDialogPacket.INSTANCE);
        }
    }

    private static Optional<URI> parseHttpUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return Optional.of(uri);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid URL format; omit clickable link button and fall back to NoticeDialog.
        }
        return Optional.empty();
    }
}
