package com.piotrek.groundworks.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;

public final class GranularBlockRenderState extends BlockEntityRenderState {
    public BlockPos pos;
    public int materialId;
    public long[] occupancy;
}
