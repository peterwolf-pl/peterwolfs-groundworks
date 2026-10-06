package com.piotrek.groundworks.terrain.conversion;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.networking.GranularSyncHandler;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Converts vanilla blocks into granular cells and solidifies full buried cells back.
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
                Block.UPDATE_ALL_IMMEDIATE);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GranularBlockEntity granularBe) {
            granularBe.setMaterialId(material.id());
            granularBe.setCell(cell);
            granularBe.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        }

        GroundworksMod.LOGGER.info(
                "[Groundworks] Successfully converted {} at {} to granular {} ({} units)",
                state.getBlock(), pos, material.name(), cell.unitCount());

        return cell;
    }

    public static boolean isConvertible(BlockState state) {
        return GranularMaterialRegistry.forBlockState(state) != null;
    }

    /**
     * Checks whether a full granular cell can be solidified back into a vanilla block.
     * A cell can solidify when it is 100% full (512 units) and buried inside terrain or a pile:
     * - either it has granular material of the same type above it,
     * - or it has a solid vanilla block above it.
     */
    public static boolean canSolidify(
            @Nullable ServerLevel level,
            @Nullable GranularCell aboveCell,
            BlockPos pos,
            GranularCell cell
    ) {
        if (!cell.isFull() || !cell.isPureMaterial()) return false;
        GranularMaterial material = GranularMaterialRegistry.byId(cell.pureMaterialId());
        if (material == null || material == GranularMaterial.EMPTY || material.sourceBlock() == null) {
            return false;
        }

        if (aboveCell != null && !aboveCell.isEmpty()) {
            return aboveCell.isPureMaterial()
                    && aboveCell.pureMaterialId() == cell.pureMaterialId();
        }

        if (level == null) return false;
        BlockState aboveState = level.getBlockState(pos.above());
        return aboveState.isSolid() && !aboveState.is(GroundworksMod.GRANULAR_BLOCK);
    }

    public static boolean canSolidify(
            @Nullable ServerLevel level,
            GranularWorldStorage storage,
            BlockPos pos,
            GranularCell cell
    ) {
        GranularCell aboveCell = storage.getCell(pos.above());
        return canSolidify(level, aboveCell, pos, cell);
    }

    /**
     * Converts a full granular cell back into its corresponding solid vanilla block.
     * Replaces GranularBlock with the vanilla block in the world and removes it from storage.
     */
    public static boolean solidify(
            ServerLevel level,
            GranularWorldStorage storage,
            BlockPos pos,
            GranularCell cell
    ) {
        if (!canSolidify(level, storage, pos, cell)) return false;

        GranularMaterial material = GranularMaterialRegistry.byId(cell.pureMaterialId());
        Block solidBlock = material.sourceBlock();

        // 1. Remove from granular storage
        storage.removeCell(pos);

        // 2. Replace anchor block with vanilla block
        level.setBlock(pos, solidBlock.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);

        // 3. Notify clients to remove cell from client storage & mesh cache
        GranularSyncHandler.sendCellRemoval(level, pos);

        // 4. Wake cell above if present so it rests on solid ground
        BlockPos abovePos = pos.above();
        GranularCell above = storage.getCell(abovePos);
        if (above != null && !above.isEmpty()) {
            above.markDirty(DirtyFlags.SIMULATE | DirtyFlags.SYNC | DirtyFlags.MESH);
            storage.enqueueDirty(abovePos);
        }

        GroundworksMod.LOGGER.info(
                "[Groundworks] Solidified full cell at {} back to vanilla {} ({} units)",
                pos, solidBlock.getName().getString(), cell.unitCount());
        return true;
    }
}
