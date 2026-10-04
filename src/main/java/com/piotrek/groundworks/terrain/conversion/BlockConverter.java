package com.piotrek.groundworks.terrain.conversion;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Converts vanilla blocks into granular cells.
 *
 * <p>This is the lazy conversion boundary. Normal blocks remain vanilla
 * until an excavation tool or material deposit requires conversion.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>A full converted block starts with exactly 512 units.</li>
 *   <li>The vanilla block is replaced with air (in MVP; later with a custom block).</li>
 *   <li>Only registered materials can be converted.</li>
 * </ul>
 */
public final class BlockConverter {

    private BlockConverter() {}

    /**
     * Convert a vanilla block at the given position into a granular cell.
     *
     * @param level the server level
     * @param pos   the block position
     * @return a full granular cell, or null if the block is not convertible
     */
    @Nullable
    public static GranularCell convert(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return convert(level, pos, state);
    }

    /**
     * Convert a vanilla block with a known state.
     *
     * @param level the server level
     * @param pos   the block position
     * @param state the block state (already read)
     * @return a full granular cell, or null if not convertible
     */
    @Nullable
    public static GranularCell convert(ServerLevel level, BlockPos pos, BlockState state) {
        GranularMaterial material = GranularMaterialRegistry.forBlockState(state);
        if (material == null) return null;

        // Create the full cell
        GranularCell cell = GranularCell.full(material);

        // Replace vanilla block with air to avoid visual duplication.
        // In Stage 3, this will be replaced with a custom granular block.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                3 /* NOTIFY_CLIENTS | BLOCK_UPDATE */);

        GroundworksMod.LOGGER.debug(
                "[Groundworks] Converted {} at {} to granular {} ({} units)",
                state.getBlock(), pos, material.name(), cell.unitCount());

        return cell;
    }

    /**
     * Check if a block state can be converted without actually converting it.
     */
    public static boolean isConvertible(BlockState state) {
        return GranularMaterialRegistry.forBlockState(state) != null;
    }
}
