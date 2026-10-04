package com.piotrek.groundworks.terrain.conversion;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Converts vanilla blocks into granular cells.
 */
public final class BlockConverter {

    private BlockConverter() {}

    @Nullable
    public static GranularCell convert(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return convert(level, pos, state);
    }

    @Nullable
    public static GranularCell convert(ServerLevel level, BlockPos pos, BlockState state) {
        GranularMaterial material = GranularMaterialRegistry.forBlockState(state);
        if (material == null) return null;

        // Create the full cell
        GranularCell cell = GranularCell.full(material);

        // Replace vanilla block with the anchor GranularBlock immediately
        level.setBlock(pos, GroundworksMod.GRANULAR_BLOCK.defaultBlockState(),
                3 /* NOTIFY_CLIENTS | BLOCK_UPDATE */);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GranularBlockEntity granularBe) {
            granularBe.setMaterialId(material.id());
            granularBe.setCell(cell);
            granularBe.setChanged();
        }

        GroundworksMod.LOGGER.info(
                "[Groundworks] Successfully converted {} at {} to granular {} ({} units)",
                state.getBlock(), pos, material.name(), cell.unitCount());

        return cell;
    }

    public static boolean isConvertible(BlockState state) {
        return GranularMaterialRegistry.forBlockState(state) != null;
    }
}
