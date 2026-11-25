package com.sdlinkrolegate.gate;

import com.sdlinkrolegate.config.ModConfig;
import com.sdlinkrolegate.util.TextUtil;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Prevents unverified players from breaking blocks, placing blocks, or interacting with containers/entities.
 */
public final class InteractionProtectionHandler {

    private final RoleGateManager roleGateManager;
    private final ModConfig modConfig;

    public InteractionProtectionHandler(RoleGateManager roleGateManager, ModConfig modConfig) {
        this.roleGateManager = roleGateManager;
        this.modConfig = modConfig;
    }

    public void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide() || !roleGateManager.isRestricted(player)) {
                return true;
            }
            sendDeniedMessage(player, modConfig.get().messages().blockBreakDenied());
            return false;
        });

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide() || !roleGateManager.isRestricted(player)) {
                return InteractionResult.PASS;
            }
            sendDeniedMessage(player, modConfig.get().messages().blockBreakDenied());
            return InteractionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() || !roleGateManager.isRestricted(player)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            ItemStack held = player.getItemInHand(hand);

            ModConfig.MessagesConfig messages = modConfig.get().messages();
            boolean targetingInteractiveBlock = isInteractiveBlock(level, pos, state) && !player.isSecondaryUseActive();
            if (!targetingInteractiveBlock && held.getItem() instanceof BlockItem) {
                sendDeniedMessage(player, messages.blockPlaceDenied());
            } else {
                sendDeniedMessage(player, messages.interactionDenied());
            }
            return InteractionResult.FAIL;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() || !roleGateManager.isRestricted(player)) {
                return InteractionResult.PASS;
            }
            sendDeniedMessage(player, modConfig.get().messages().interactionDenied());
            return InteractionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() || !roleGateManager.isRestricted(player)) {
                return InteractionResult.PASS;
            }
            Item item = player.getItemInHand(hand).getItem();
            ModConfig.MessagesConfig messages = modConfig.get().messages();

            if (isPlaceableEntityOrFluidItem(item)) {
                sendDeniedMessage(player, messages.blockPlaceDenied());
                return InteractionResult.FAIL;
            }
            if (isWorldModifyingToolItem(item)) {
                sendDeniedMessage(player, messages.interactionDenied());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private void sendDeniedMessage(Player player, String template) {
        player.sendSystemMessage(TextUtil.format(template, modConfig.get().discordInviteLink()));
    }

    private static boolean isInteractiveBlock(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) != null || state.getMenuProvider(level, pos) != null) {
            return true;
        }
        if (state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.ANVIL)) {
            return true;
        }
        var block = state.getBlock();
        return block instanceof LeverBlock
                || block instanceof EnderChestBlock
                || block instanceof NoteBlock
                || block instanceof BellBlock
                || block instanceof ComposterBlock
                || block instanceof CauldronBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof FlowerPotBlock
                || block instanceof CakeBlock;
    }

    private static boolean isPlaceableEntityOrFluidItem(Item item) {
        return item instanceof BucketItem
                || item instanceof SolidBucketItem
                || item instanceof PlaceOnWaterBlockItem
                || item instanceof BoatItem
                || item instanceof MinecartItem
                || item instanceof ArmorStandItem
                || item instanceof HangingEntityItem
                || item instanceof EndCrystalItem
                || item instanceof SpawnEggItem;
    }

    private static boolean isWorldModifyingToolItem(Item item) {
        return item instanceof FlintAndSteelItem
                || item instanceof FireChargeItem
                || item instanceof BoneMealItem;
    }
}
