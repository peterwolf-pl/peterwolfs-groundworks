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

        return cell == null ? Shapes.empty() : GranularCollisionShapeCache.get(cell);
    }
}
