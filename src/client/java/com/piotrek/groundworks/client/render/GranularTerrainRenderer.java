package com.piotrek.groundworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.CellMesh;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.Quad;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Renders cached, textured granular heightfield meshes in world space. */
public final class GranularTerrainRenderer {

    private static final int DIRT_ID = 1;
    private static final int SAND_ID = 2;
    private static final int GRAVEL_ID = 3;
    private static final int COBBLESTONE_ID = 4;

    private static final Identifier DIRT_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/dirt.png");
    private static final Identifier SAND_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/sand.png");
    private static final Identifier GRAVEL_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/gravel.png");
    private static final Identifier COBBLESTONE_TEXTURE =
            Identifier.withDefaultNamespace("textures/block/cobblestone.png");

    private static final double MAX_RENDER_DISTANCE = 64.0;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    private record CellDraw(
            float relX,
            float relY,
            float relZ,
            int packedLight,
            List<Quad> quads
    ) {}

    private GranularTerrainRenderer() {}

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Map<Long, GranularCell> cells = ClientGranularStorage.allCells();
        if (cells.isEmpty()) return;

        var cameraState = context.levelState().cameraRenderState;
        if (cameraState == null || cameraState.pos == null) return;
        Vec3 camera = cameraState.pos;

        Frustum cullFrustum = context.levelState().cameraRenderState.cullFrustum;

        List<CellDraw> dirtDraws = new ArrayList<>();
        List<CellDraw> sandDraws = new ArrayList<>();
        List<CellDraw> gravelDraws = new ArrayList<>();
        List<CellDraw> cobblestoneDraws = new ArrayList<>();

        // Single pass over all client cells with distance and frustum culling
        for (var entry : cells.entrySet()) {
            GranularCell cell = entry.getValue();
            if (cell.isEmpty()) continue;

            long packedPos = entry.getKey();
            BlockPos pos = BlockPos.of(packedPos);

            double dx = pos.getX() + 0.5 - camera.x;
            double dy = pos.getY() + 0.5 - camera.y;
            double dz = pos.getZ() + 0.5 - camera.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) continue;

            // Frustum culling: skip cells outside camera view
            if (cullFrustum != null && !cullFrustum.isVisible(new AABB(pos))) continue;

            CellMesh mesh = GranularMeshCache.getOrBuild(packedPos, cell);
            if (mesh.isEmpty()) continue;

            float relX = (float) (pos.getX() - camera.x);
            float relY = (float) (pos.getY() - camera.y);
            float relZ = (float) (pos.getZ() - camera.z);

            int blockLight = client.level.getBrightness(LightLayer.BLOCK, pos.above());
            int skyLight = client.level.getBrightness(LightLayer.SKY, pos.above());
            int packedLight = (skyLight << 20) | (blockLight << 4);

            List<Quad> dirtQuads = mesh.quadsForMaterial(DIRT_ID);
            if (!dirtQuads.isEmpty()) {
                dirtDraws.add(new CellDraw(relX, relY, relZ, packedLight, dirtQuads));
            }

            List<Quad> sandQuads = mesh.quadsForMaterial(SAND_ID);
            if (!sandQuads.isEmpty()) {
                sandDraws.add(new CellDraw(relX, relY, relZ, packedLight, sandQuads));
            }

            List<Quad> gravelQuads = mesh.quadsForMaterial(GRAVEL_ID);
            if (!gravelQuads.isEmpty()) {
                gravelDraws.add(new CellDraw(relX, relY, relZ, packedLight, gravelQuads));
            }

            List<Quad> cobbleQuads = mesh.quadsForMaterial(COBBLESTONE_ID);
            if (!cobbleQuads.isEmpty()) {
                cobblestoneDraws.add(new CellDraw(relX, relY, relZ, packedLight, cobbleQuads));
            }
        }

        // Submit only batches that have geometry to render
        if (!dirtDraws.isEmpty()) {
            submitBatch(context, DIRT_TEXTURE, dirtDraws);
        }
        if (!sandDraws.isEmpty()) {
            submitBatch(context, SAND_TEXTURE, sandDraws);
        }
        if (!gravelDraws.isEmpty()) {
            submitBatch(context, GRAVEL_TEXTURE, gravelDraws);
        }
        if (!cobblestoneDraws.isEmpty()) {
            submitBatch(context, COBBLESTONE_TEXTURE, cobblestoneDraws);
        }
    }

    private static void submitBatch(
            LevelRenderContext context,
            Identifier texture,
            List<CellDraw> draws
    ) {
        PoseStack poseStack = context.poseStack() != null ? context.poseStack() : new PoseStack();
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(texture),
                (pose, consumer) -> {
                    for (CellDraw draw : draws) {
                        for (Quad quad : draw.quads()) {
                            emitVertex(consumer, pose, quad.v0(), quad.n0(), draw.relX(), draw.relY(), draw.relZ(), draw.packedLight());
                            emitVertex(consumer, pose, quad.v1(), quad.n1(), draw.relX(), draw.relY(), draw.relZ(), draw.packedLight());
                            emitVertex(consumer, pose, quad.v2(), quad.n2(), draw.relX(), draw.relY(), draw.relZ(), draw.packedLight());
                            emitVertex(consumer, pose, quad.v3(), quad.n3(), draw.relX(), draw.relY(), draw.relZ(), draw.packedLight());
                        }
                    }
                }
        );
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
