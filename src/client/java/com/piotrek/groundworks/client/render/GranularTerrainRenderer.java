package com.piotrek.groundworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.CellMesh;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.Quad;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;

/** Renders cached, textured granular heightfield meshes in world space. */
public final class GranularTerrainRenderer {

    private static final Identifier DIRT_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/dirt.png");
    private static final Identifier SAND_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/sand.png");
    private static final Identifier GRAVEL_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/gravel.png");
    private static final Identifier COBBLESTONE_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/cobblestone.png");

    private GranularTerrainRenderer() {}

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Map<Long, GranularCell> cells = ClientGranularStorage.allCells();
        if (cells.isEmpty()) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        if (camera == null) return;

        submitMaterial(context, cells, camera, 1, DIRT_TEXTURE);
        submitMaterial(context, cells, camera, 2, SAND_TEXTURE);
        submitMaterial(context, cells, camera, 3, GRAVEL_TEXTURE);
        submitMaterial(context, cells, camera, 4, COBBLESTONE_TEXTURE);
    }

    private static void submitMaterial(
            LevelRenderContext context,
            Map<Long, GranularCell> cells,
            Vec3 camera,
            int materialId,
            Identifier texture
    ) {
        PoseStack poseStack = context.poseStack();
        Minecraft client = Minecraft.getInstance();

        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(texture),
                (pose, consumer) -> {
                    for (var entry : cells.entrySet()) {
                        GranularCell cell = entry.getValue();
                        if (cell.isEmpty() || cell.unitsOfMaterial(materialId) <= 0) continue;

                        long packedPos = entry.getKey();
                        BlockPos pos = BlockPos.of(packedPos);
                        double dx = pos.getX() + 0.5 - camera.x;
                        double dy = pos.getY() + 0.5 - camera.y;
                        double dz = pos.getZ() + 0.5 - camera.z;
                        if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) continue;

                        CellMesh mesh = GranularMeshCache.getOrBuild(packedPos, cell);
                        if (mesh.isEmpty()) continue;

                        float relX = (float) (pos.getX() - camera.x);
                        float relY = (float) (pos.getY() - camera.y);
                        float relZ = (float) (pos.getZ() - camera.z);

                        int blockLight = client.level.getBrightness(LightLayer.BLOCK, pos.above());
                        int skyLight = client.level.getBrightness(LightLayer.SKY, pos.above());
                        int packedLight = (skyLight << 20) | (blockLight << 4);

                        for (Quad quad : mesh.quads()) {
                            if (visualMaterialForQuad(cell, pos, quad) != materialId) continue;
                            emitVertex(consumer, pose, quad.v0(), quad.n0(), relX, relY, relZ, packedLight);
                            emitVertex(consumer, pose, quad.v1(), quad.n1(), relX, relY, relZ, packedLight);
                            emitVertex(consumer, pose, quad.v2(), quad.n2(), relX, relY, relZ, packedLight);
                            emitVertex(consumer, pose, quad.v3(), quad.n3(), relX, relY, relZ, packedLight);
                        }
                    }
                }
        );
    }

    private static int visualMaterialForQuad(
            GranularCell cell,
            BlockPos pos,
            Quad quad
    ) {
        float cx = (quad.v0().x + quad.v1().x + quad.v2().x + quad.v3().x) * 0.25f;
        float cy = (quad.v0().y + quad.v1().y + quad.v2().y + quad.v3().y) * 0.25f;
        float cz = (quad.v0().z + quad.v1().z + quad.v2().z + quad.v3().z) * 0.25f;

        int mx = clampMicro((int) Math.floor(cx * GranularCell.RESOLUTION));
        int my = clampMicro((int) Math.floor(cy * GranularCell.RESOLUTION));
        int mz = clampMicro((int) Math.floor(cz * GranularCell.RESOLUTION));
        return cell.visualMaterialId(pos.asLong(), mx, my, mz);
    }

    private static int clampMicro(int value) {
        return Math.max(0, Math.min(GranularCell.RESOLUTION - 1, value));
    }

    private static void emitVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f vertex,
            Vector3f normal,
            float relX,
            float relY,
            float relZ,
            int packedLight
    ) {
        float u;
        float v;
        if (Math.abs(normal.y) >= Math.abs(normal.x)
                && Math.abs(normal.y) >= Math.abs(normal.z)) {
            u = vertex.x;
            v = vertex.z;
        } else if (Math.abs(normal.x) > Math.abs(normal.z)) {
            u = vertex.z;
            v = 1.0f - vertex.y;
        } else {
            u = vertex.x;
            v = 1.0f - vertex.y;
        }

        consumer.addVertex(pose, relX + vertex.x, relY + vertex.y, relZ + vertex.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }
}
