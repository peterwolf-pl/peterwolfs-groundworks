package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.api.deposit.DepositApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationApi;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.api.world.WorldSpaceApi;
import com.piotrek.groundworks.terrain.cell.GranularCell;
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
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Shared Groundworks hand tool with internal granular-material storage.
 *
 * <p>Normal right-click excavates material into the tool. Sneak + right-click
 * deposits the stored material back into the world. Subclasses decide which
 * granular materials they are allowed to excavate and how much volume one use removes.
 */
public abstract class GranularContainerTool extends Item {

    public static final int MAX_CAPACITY = GranularCell.TOTAL_UNITS * 16;

    private final int unitsPerUse;

    protected GranularContainerTool(Properties properties, int unitsPerUse) {
        super(properties);
        if (unitsPerUse <= 0 || unitsPerUse > GranularCell.TOTAL_UNITS) {
            throw new IllegalArgumentException("unitsPerUse must be between 1 and "
                    + GranularCell.TOTAL_UNITS);
        }
        this.unitsPerUse = unitsPerUse;
    }

    protected abstract boolean acceptsMaterial(GranularMaterial material);

    protected int unitsPerUse() {
        return unitsPerUse;
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
        Vec3 hitLocation = context.getClickLocation();

        int storedUnits = getStoredUnits(stack);
        String storedMat = getStoredMaterial(stack);

        if (player.isShiftKeyDown()) {
            return deposit(serverLevel, player, stack, context, pos, storedUnits, storedMat);
        }

        GranularMaterial targetMaterial = WorldSpaceApi.getMaterial(serverLevel, pos);
        if (targetMaterial == null
                || targetMaterial == GranularMaterial.EMPTY
                || !acceptsMaterial(targetMaterial)) {
            player.sendOverlayMessage(
                    Component.literal("This tool cannot excavate this material")
                            .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }

        if (storedMat != null && !storedMat.equals(targetMaterial.name())) {
            player.sendOverlayMessage(
                    Component.literal("Tool contains " + storedMat
                            + ", cannot mix with " + targetMaterial.name())
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        int freeCapacity = MAX_CAPACITY - storedUnits;
        if (freeCapacity <= 0) {
            player.sendOverlayMessage(
                    Component.literal("Tool full!")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        int requestUnits = Math.min(unitsPerUse, freeCapacity);
        ExcavationResult result = ExcavationApi.excavateAt(
                serverLevel, pos, hitLocation, requestUnits);

        if (!result.success()) {
            player.sendOverlayMessage(
                    Component.literal("Nothing excavated")
                            .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }

        if (!acceptsMaterial(result.material())) {
            player.sendOverlayMessage(
                    Component.literal("Unexpected material: " + result.material().name())
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        int newStored = storedUnits + result.unitsRemoved();
        setStoredUnits(stack, newStored);
        setStoredMaterial(stack, result.material().name());

        player.sendOverlayMessage(
                Component.literal("Excavated " + result.unitsRemoved()
                        + " " + result.material().name()
                        + " units. Stored: " + newStored)
                        .withStyle(ChatFormatting.GOLD));

        return InteractionResult.SUCCESS;
    }

    private InteractionResult deposit(
            ServerLevel serverLevel,
            Player player,
            ItemStack stack,
            UseOnContext context,
            BlockPos pos,
            int storedUnits,
            String storedMat
    ) {
        if (storedUnits <= 0 || storedMat == null) {
            player.sendOverlayMessage(
                    Component.literal("Tool is empty - excavate first")
                            .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }

        GranularMaterial material = GranularMaterialRegistry.byName(storedMat);
        if (material == null || material == GranularMaterial.EMPTY) {
            player.sendOverlayMessage(
                    Component.literal("Unknown stored material: " + storedMat)
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        BlockPos depositPos = pos.relative(context.getClickedFace());
        int toDeposit = Math.min(unitsPerUse, storedUnits);

        DepositResult result = DepositApi.depositWithOverflow(
                serverLevel, depositPos, material, toDeposit);

        if (!result.success()) {
            player.sendOverlayMessage(
                    Component.literal("Cannot deposit here")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

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
            int blocks = units / GranularCell.TOTAL_UNITS;
            int remainder = units % GranularCell.TOTAL_UNITS;
            tooltip.accept(Component.literal("= " + blocks + " blocks + "
                    + remainder + "/" + GranularCell.TOTAL_UNITS)
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.accept(Component.literal("Empty")
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.accept(Component.literal("Right-click: excavate " + unitsPerUse + " units")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("Sneak + right-click: deposit " + unitsPerUse + " units")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getStoredUnits(stack) > 0;
    }

    protected static int getStoredUnits(ItemStack stack) {
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return 0;
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getIntOr("gw_units", 0);
    }

    protected static void setStoredUnits(ItemStack stack, int units) {
        stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY,
                data -> data.update(tag -> tag.putInt("gw_units", units)));
    }

    protected static String getStoredMaterial(ItemStack stack) {
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return null;
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        var tag = customData.copyTag();
        return tag.contains("gw_material") ? tag.getStringOr("gw_material", null) : null;
    }

    protected static void setStoredMaterial(ItemStack stack, String material) {
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
