package com.piotrek.groundworks.block;

import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Anchor block for volumetric granular terrain.
 *
 * <p>Does NOT use naive {@code LEVEL 1..8}. Instead, this block is an anchor container
 * for 512-microvoxel granular data, dynamic continuous surface rendering, and dynamic
 * collision matching excavated craters and slopes.
 */
public class GranularBlock extends Block implements EntityBlock {

    public GranularBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GranularBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Invisible block model: actual terrain geometry is rendered dynamically by GranularTerrainRenderer
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getDynamicShape(level, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getDynamicShape(level, pos);
    }

    /**
     * Compute a dynamic collision shape matching the granular surface.
     * Uses the 8x8 column height cache to construct an accurate stepped heightfield shape.
     */
    private VoxelShape getDynamicShape(BlockGetter level, BlockPos pos) {
        GranularCell cell = null;

        if (level instanceof ServerLevel serverLevel) {
            cell = GranularWorldStorage.get(serverLevel).getCell(pos);
        } else {
            cell = ClientGranularStorage.getCell(pos);
        }

        if (cell == null || cell.isEmpty()) {
            return Shapes.empty();
        }

        // Build a merged VoxelShape from the 8x8 heightfield
        VoxelShape combined = Shapes.empty();
        double step = 1.0 / GranularCell.RESOLUTION; // 0.125m

        for (int z = 0; z < GranularCell.RESOLUTION; z++) {
            for (int x = 0; x < GranularCell.RESOLUTION; x++) {
                int height = cell.getColumnHeight(x, z);
                if (height >= 0) {
                    double minY = 0.0;
                    double maxY = (height + 1) * step;
                    double minX = x * step;
                    double maxX = minX + step;
                    double minZ = z * step;
                    double maxZ = minZ + step;

                    VoxelShape columnBox = Block.box(
                            minX * 16.0, minY * 16.0, minZ * 16.0,
                            maxX * 16.0, maxY * 16.0, maxZ * 16.0
                    );
                    combined = Shapes.or(combined, columnBox);
                }
            }
        }

        return combined.isEmpty() ? Shapes.block() : combined;
    }
}
