package com.piotrek.groundworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.CellMesh;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.Quad;
import com.piotrek.groundworks.client.storage.ClientGranularStorage;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Stage 3 Client Renderer: Renders deformable granular terrain meshes in the world.
 *
 * <p>Uses Fabric 26.3's {@code LevelRenderContext.submitNodeCollector().submitCustomGeometry}.
 * Samples {@link ClientGranularStorage}, checks {@link GranularMeshCache},
 * and draws boundary quads with material-based tinting.
 */
public final class GranularTerrainRenderer {

    private GranularTerrainRenderer() {}

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Map<Long, GranularCell> cells = ClientGranularStorage.allCells();
        if (cells.isEmpty()) return;

        Vec3 camPos = context.levelState().cameraRenderState.pos;
        if (camPos == null) return;

        PoseStack poseStack = context.poseStack();

        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.debugQuads(),
                (pose, consumer) -> {
                    for (var entry : cells.entrySet()) {
                        long packedPos = entry.getKey();
                        GranularCell cell = entry.getValue();
                        if (cell.isEmpty()) continue;

                        BlockPos pos = BlockPos.of(packedPos);

                        // Distance culling: only render within 64 blocks of camera
                        double dx = pos.getX() + 0.5 - camPos.x;
                        double dy = pos.getY() + 0.5 - camPos.y;
                        double dz = pos.getZ() + 0.5 - camPos.z;
                        if (dx * dx + dy * dy + dz * dz > 64 * 64) continue;

                        CellMesh mesh = GranularMeshCache.getOrBuild(packedPos, cell);
                        if (mesh.isEmpty()) continue;

                        float relX = (float) (pos.getX() - camPos.x);
                        float relY = (float) (pos.getY() - camPos.y);
                        float relZ = (float) (pos.getZ() - camPos.z);

                        // Material colors
                        int r, g, b;
                        int matId = cell.materialId();
                        if (matId == 1) {
                            // Dirt: earthy brown
                            r = 134; g = 96; b = 67;
                        } else if (matId == 2) {
                            // Sand: warm golden tan
                            r = 219; g = 207; b = 163;
                        } else if (matId == 3) {
                            // Gravel: textured slate grey
                            r = 136; g = 134; b = 136;
                        } else {
                            r = 180; g = 180; b = 180;
                        }

                        int alpha = 255;

                        for (Quad quad : mesh.quads()) {
                            consumer.addVertex(pose, relX + quad.v0().x, relY + quad.v0().y, relZ + quad.v0().z)
                                    .setColor(r, g, b, alpha);
                            consumer.addVertex(pose, relX + quad.v1().x, relY + quad.v1().y, relZ + quad.v1().z)
                                    .setColor(r, g, b, alpha);
                            consumer.addVertex(pose, relX + quad.v2().x, relY + quad.v2().y, relZ + quad.v2().z)
                                    .setColor(r, g, b, alpha);
                            consumer.addVertex(pose, relX + quad.v3().x, relY + quad.v3().y, relZ + quad.v3().z)
                                    .setColor(r, g, b, alpha);
                        }
                    }
                }
        );
    }
}
