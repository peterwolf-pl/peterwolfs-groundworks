package com.piotrek.groundworks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.piotrek.groundworks.api.GroundworksApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Commands for inspecting, debugging, and testing Peterwolf's Groundworks.
 *
 * <pre>
 * /groundworks debug
 * /groundworks inspect [pos]
 * /groundworks excavate <units> [pos]
 * /groundworks deposit <material> <units> [pos]
 * /groundworks stats
 * </pre>
 */
public final class GroundworksCommand {

    private GroundworksCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext,
                                Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("groundworks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .executes(GroundworksCommand::executeDebug))
                .then(Commands.literal("stats")
                        .executes(GroundworksCommand::executeDebug))
                .then(Commands.literal("inspect")
                        .executes(ctx -> executeInspect(ctx, getTargetedOrSourcePos(ctx)))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> executeInspect(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                .then(Commands.literal("excavate")
                        .then(Commands.argument("units", IntegerArgumentType.integer(1, 5120))
                                .executes(ctx -> executeExcavate(ctx, IntegerArgumentType.getInteger(ctx, "units"), getTargetedOrSourcePos(ctx)))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> executeExcavate(ctx, IntegerArgumentType.getInteger(ctx, "units"), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
                .then(Commands.literal("deposit")
                        .then(Commands.literal("dirt")
                                .then(Commands.argument("units", IntegerArgumentType.integer(1, 5120))
                                        .executes(ctx -> executeDeposit(ctx, GranularMaterialRegistry.DIRT, IntegerArgumentType.getInteger(ctx, "units"), getTargetedOrSourcePos(ctx)))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> executeDeposit(ctx, GranularMaterialRegistry.DIRT, IntegerArgumentType.getInteger(ctx, "units"), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
                        .then(Commands.literal("sand")
                                .then(Commands.argument("units", IntegerArgumentType.integer(1, 5120))
                                        .executes(ctx -> executeDeposit(ctx, GranularMaterialRegistry.SAND, IntegerArgumentType.getInteger(ctx, "units"), getTargetedOrSourcePos(ctx)))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> executeDeposit(ctx, GranularMaterialRegistry.SAND, IntegerArgumentType.getInteger(ctx, "units"), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
                        .then(Commands.literal("gravel")
                                .then(Commands.argument("units", IntegerArgumentType.integer(1, 5120))
                                        .executes(ctx -> executeDeposit(ctx, GranularMaterialRegistry.GRAVEL, IntegerArgumentType.getInteger(ctx, "units"), getTargetedOrSourcePos(ctx)))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> executeDeposit(ctx, GranularMaterialRegistry.GRAVEL, IntegerArgumentType.getInteger(ctx, "units"), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))));
    }

    private static int executeDebug(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        GranularWorldStorage storage = GranularWorldStorage.get(level);

        long micros = storage.simulationTimeNanos() / 1_000;
        ctx.getSource().sendSuccess(() -> Component.literal("=== Peterwolf's Groundworks Debug ===")
                .withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Active granular cells: %d", storage.activeCellCount())), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Total granular units: %d (approx %.3f m³)",
                        storage.totalUnits(), storage.totalUnits() / 512.0)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Dirty queue size: %d", storage.dirtyQueueSize())), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Last tick processed: %d cells, %d units moved",
                        storage.cellsProcessedLastTick(), storage.unitsMovedLastTick())), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Simulation time: %d µs", micros)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Sync packets sent: %d", storage.syncPacketsSent())), false);

        return 1;
    }

    private static int executeInspect(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getCell(pos);

        if (cell == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No granular cell at " + pos.toShortString() + " (vanilla block: "
                            + level.getBlockState(pos).getBlock().getName().getString() + ")"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "--- Granular Cell at " + pos.toShortString() + " ---").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Material: " + cell.material().name() + " (id: " + cell.materialId() + ")"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Units: " + cell.unitCount() + " / 512 (" + String.format("%.2f%%", (cell.unitCount() / 512.0) * 100) + ")"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Revision: " + cell.revision()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Dirty flags: " + Integer.toBinaryString(cell.dirtyFlags())), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Integrity valid: " + cell.validate()), false);

        return 1;
    }

    private static int executeExcavate(CommandContext<CommandSourceStack> ctx, int units, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        ExcavationResult result = GroundworksApi.excavate(level, pos, units);

        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal("Could not excavate at " + pos.toShortString()));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Excavated %d %s units at %s",
                        result.unitsRemoved(), result.material().name(), pos.toShortString()))
                .withStyle(ChatFormatting.GREEN), true);
        return result.unitsRemoved();
    }

    private static int executeDeposit(CommandContext<CommandSourceStack> ctx, GranularMaterial material, int units, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        DepositResult result = GroundworksApi.depositWithOverflow(level, pos, material, units);

        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal("Could not deposit at " + pos.toShortString()));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("Deposited %d %s units at %s (rejected: %d)",
                        result.unitsDeposited(), material.name(), pos.toShortString(), result.unitsRejected()))
                .withStyle(ChatFormatting.GREEN), true);
        return result.unitsDeposited();
    }

    private static BlockPos getTargetedOrSourcePos(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (source.getEntity() instanceof Player player) {
            HitResult hit = player.pick(5.0D, 0.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                return blockHit.getBlockPos();
            }
        }
        return BlockPos.containing(source.getPosition());
    }
}
