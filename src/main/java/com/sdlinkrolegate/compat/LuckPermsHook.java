package com.sdlinkrolegate.compat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Isolated LuckPerms integration. Only loaded at runtime when the 'luckperms' mod is present.
 */
public final class LuckPermsHook {

    private LuckPermsHook() {
    }

    /**
     * Adds the player to the configured unverified LuckPerms group asynchronously.
     */
    public static void addPlayerToGroup(UUID playerUuid, String groupName, Logger logger) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            InheritanceNode node = InheritanceNode.builder(groupName).build();
            luckPerms.getUserManager().modifyUser(playerUuid, user -> {
                boolean alreadyInGroup = user.getNodes(NodeType.INHERITANCE).stream()
                        .anyMatch(existing -> existing.getGroupName().equalsIgnoreCase(groupName));
                if (!alreadyInGroup) {
                    user.data().add(node);
                }
            }).exceptionally(throwable -> {
                logger.warn("Failed to add player {} to LuckPerms group '{}'", playerUuid, groupName, throwable);
                return null;
            });
        } catch (IllegalStateException e) {
            logger.warn("LuckPerms API is not ready yet while adding {} to group '{}'", playerUuid, groupName, e);
        }
    }

    /**
     * Removes the player from the configured unverified LuckPerms group asynchronously.
     */
    public static void removePlayerFromGroup(UUID playerUuid, String groupName, Logger logger) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            luckPerms.getUserManager().modifyUser(playerUuid, user -> {
                List<InheritanceNode> matching = user.getNodes(NodeType.INHERITANCE).stream()
                        .filter(existing -> existing.getGroupName().equalsIgnoreCase(groupName))
                        .toList();
                matching.forEach(node -> user.data().remove(node));
            }).exceptionally(throwable -> {
                logger.warn("Failed to remove player {} from LuckPerms group '{}'", playerUuid, groupName, throwable);
                return null;
            });
        } catch (IllegalStateException e) {
            logger.warn("LuckPerms API is not ready yet while removing {} from group '{}'", playerUuid, groupName, e);
        }
    }
}
