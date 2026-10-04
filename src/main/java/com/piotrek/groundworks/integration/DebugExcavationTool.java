package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.deposit.DepositApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationApi;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Debug excavation tool for Stage 1C.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li><b>Right-click on block:</b> If tool is empty, excavate 32 units.
 *       If tool has material, deposit 32 units.</li>
 *   <li><b>Sneak + right-click:</b> Deposit mode (deposits material above clicked block).</li>
 * </ul>
 *
 * <p>The tool stores material internally via CustomData on the ItemStack.
 * This is temporary developer functionality to prove remove → store → deposit → conserve.
 */
public class DebugExcavationTool extends Item {

    /** Default units per excavation/deposit operation. */
    public static final int UNITS_PER_USE = 32;
    /** Maximum units the tool can hold. */
    public static final int MAX_CAPACITY = 512 * 16; // 16 blocks worth

    public DebugExcavationTool(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ServerLevel serverLevel = (ServerLevel) level;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();

        int storedUnits = getStoredUnits(stack);
        String storedMat = getStoredMaterial(stack);

        if (player.isShiftKeyDown()) {
            // Sneak-click: deposit mode
            if (storedUnits > 0 && storedMat != null) {
                GranularMaterial material = GranularMaterialRegistry.byName(storedMat);
                if (material == null || material == GranularMaterial.EMPTY) {
                    player.sendOverlayMessage(
                            Component.literal("Unknown stored material: " + storedMat)
                                    .withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }

                // Deposit on the face the player clicked (above the clicked block)
                BlockPos depositPos = pos.relative(context.getClickedFace());
                int toDeposit = Math.min(UNITS_PER_USE, storedUnits);

                DepositResult result = DepositApi.depositWithOverflow(
                        serverLevel, depositPos, material, toDeposit);

                if (result.success()) {
                    int newStored = storedUnits - result.unitsDeposited();
                    setStoredUnits(stack, newStored);
                    if (newStored <= 0) {
                        setStoredMaterial(stack, null);
                    }
                    player.sendOverlayMessage(
                            Component.literal("Deposited " + result.unitsDeposited()
                                    + " " + material.name()
                                    + " units. Stored: " + newStored)
                                    .withStyle(ChatFormatting.GREEN));
                } else {
                    player.sendOverlayMessage(
                            Component.literal("Cannot deposit here")
                                    .withStyle(ChatFormatting.RED));
                }
            } else {
                player.sendOverlayMessage(
                        Component.literal("Tool is empty — excavate first")
                                .withStyle(ChatFormatting.YELLOW));
            }
        } else {
            // Normal click: excavate
            ExcavationResult result = ExcavationApi.excavate(
                    serverLevel, pos, UNITS_PER_USE);

            if (result.success()) {
                // Check material compatibility
                if (storedMat != null && !storedMat.equals(result.material().name())) {
                    player.sendOverlayMessage(
                            Component.literal("Tool contains " + storedMat
                                    + ", cannot mix with " + result.material().name())
                                    .withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }

                int newStored = storedUnits + result.unitsRemoved();
                if (newStored > MAX_CAPACITY) {
                    player.sendOverlayMessage(
                            Component.literal("Tool full!")
                                    .withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }

                setStoredUnits(stack, newStored);
                setStoredMaterial(stack, result.material().name());

                player.sendOverlayMessage(
                        Component.literal("Excavated " + result.unitsRemoved()
                                + " " + result.material().name()
                                + " units. Stored: " + newStored)
                                .withStyle(ChatFormatting.GOLD));
            } else {
                player.sendOverlayMessage(
                        Component.literal("Not a granular block")
                                .withStyle(ChatFormatting.GRAY));
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx,
                                TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        int units = getStoredUnits(stack);
        String mat = getStoredMaterial(stack);

        if (units > 0 && mat != null) {
            tooltip.accept(Component.literal("Material: " + mat)
                    .withStyle(ChatFormatting.AQUA));
            tooltip.accept(Component.literal("Units: " + units + " / " + MAX_CAPACITY)
                    .withStyle(ChatFormatting.AQUA));
            int blocks = units / 512;
            int remainder = units % 512;
            tooltip.accept(Component.literal("= " + blocks + " blocks + "
                    + remainder + "/512")
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.accept(Component.literal("Empty")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.literal("Right-click: excavate " + UNITS_PER_USE + " units")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("Sneak + right-click: deposit " + UNITS_PER_USE + " units")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getStoredUnits(stack) > 0;
    }

    // ── NBT helpers ──────────────────────────────────────────────────

    private static int getStoredUnits(ItemStack stack) {
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return 0;
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getIntOr("gw_units", 0);
    }

    private static void setStoredUnits(ItemStack stack, int units) {
        stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY,
                data -> data.update(tag -> tag.putInt("gw_units", units)));
    }

    private static String getStoredMaterial(ItemStack stack) {
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return null;
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        var tag = customData.copyTag();
        return tag.contains("gw_material") ? tag.getStringOr("gw_material", null) : null;
    }

    private static void setStoredMaterial(ItemStack stack, String material) {
        stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY,
                data -> data.update(tag -> {
                    if (material != null) {
                        tag.putString("gw_material", material);
                    } else {
                        tag.remove("gw_material");
                    }
                }));
    }
}
