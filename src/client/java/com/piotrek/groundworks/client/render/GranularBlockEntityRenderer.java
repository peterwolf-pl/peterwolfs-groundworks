package com.piotrek.groundworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntityRenderer for {@link GranularBlockEntity}.
 */
public class GranularBlockEntityRenderer implements BlockEntityRenderer<GranularBlockEntity, GranularBlockRenderState> {

    public GranularBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public GranularBlockRenderState createRenderState() {
        return new GranularBlockRenderState();
    }

    @Override
    public void extractRenderState(
            GranularBlockEntity blockEntity,
            GranularBlockRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.pos = blockEntity.getBlockPos();
        state.materialId = blockEntity.getMaterialId();

        GranularCell cell = blockEntity.getCell();
        if (cell != null) {
            state.occupancy = cell.occupancy().clone();
        } else {
            state.occupancy = null;
        }
    }

    @Override
    public void submit(
            GranularBlockRenderState state,
            PoseStack stack,
            SubmitNodeCollector collector,
            CameraRenderState camera
    ) {
        BlockPos pos = state.pos;
        if (pos == null) return;

        // Use direct cell from blockEntity render state or fallback to ClientGranularStorage
        GranularCell cell = null;
        if (state.occupancy != null) {
            cell = new GranularCell();
            cell.setMaterialId(state.materialId);
            System.arraycopy(state.occupancy, 0, cell.occupancy(), 0, GranularCell.LONGS);
            cell.refreshUnitCount();
        } else {
            cell = ClientGranularStorage.getCell(pos);
        }

        if (cell == null || cell.isEmpty()) {
            return;
        }

        GranularSurfaceMesher.CellMesh mesh = GranularSurfaceMesher.generateMesh(cell);
        if (mesh.isEmpty()) return;

        // Material color tinting
        int r, g, b;
        int matId = state.materialId;
        if (matId == 1) {
            // Dirt
            r = 134; g = 96; b = 67;
        } else if (matId == 2) {
            // Sand
            r = 219; g = 207; b = 163;
        } else if (matId == 3) {
            // Gravel: distinct blue-grey gravel stone color
            r = 120; g = 125; b = 135;
        } else {
            r = 180; g = 180; b = 180;
        }

        int finalR = r;
        int finalG = g;
        int finalB = b;
        int alpha = 255;

        collector.submitCustomGeometry(
                stack,
                RenderTypes.debugQuads(),
                (pose, consumer) -> {
                    for (GranularSurfaceMesher.Quad quad : mesh.quads()) {
                        consumer.addVertex(pose, quad.v0().x, quad.v0().y, quad.v0().z)
                                .setColor(finalR, finalG, finalB, alpha);
                        consumer.addVertex(pose, quad.v1().x, quad.v1().y, quad.v1().z)
                                .setColor(finalR, finalG, finalB, alpha);
                        consumer.addVertex(pose, quad.v2().x, quad.v2().y, quad.v2().z)
                                .setColor(finalR, finalG, finalB, alpha);
                        consumer.addVertex(pose, quad.v3().x, quad.v3().y, quad.v3().z)
                                .setColor(finalR, finalG, finalB, alpha);
                    }
                }
        );
    }
}
