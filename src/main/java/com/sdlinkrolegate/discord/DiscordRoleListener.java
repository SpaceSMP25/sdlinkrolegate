package com.sdlinkrolegate.discord;

import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.events.guild.GuildBanEvent;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import com.hypherionmc.sdlink.shaded.dv8tion.jda.api.hooks.ListenerAdapter;
import com.sdlinkrolegate.gate.RoleGateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Listens directly to JDA role and membership events on SDLink's shared bot for live in-game updates.
 */
public final class DiscordRoleListener extends ListenerAdapter {

    private final RoleGateManager roleGateManager;

    public DiscordRoleListener(RoleGateManager roleGateManager) {
        this.roleGateManager = roleGateManager;
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        roleGateManager.onDiscordMemberUpdated(event.getMember().getId());
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        roleGateManager.onDiscordMemberUpdated(event.getMember().getId());
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        roleGateManager.onDiscordMemberUpdated(event.getUser().getId());
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        roleGateManager.onDiscordMemberUpdated(event.getUser().getId());
    }
}
