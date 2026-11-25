package com.sdlinkrolegate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.sdlinkrolegate.gate.RoleGateManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers the '/rolegate reload' administrative command.
 */
public final class RoleGateCommand {

    private RoleGateCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RoleGateManager roleGateManager) {
        dispatcher.register(
                Commands.literal("rolegate")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    roleGateManager.reload();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Reloaded Simple Discord Link Role Gate configuration and re-checked online players."),
                                            true
                                    );
                                    return 1;
                                }))
        );
    }
}
