package com.piotrek.groundworks.client;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.client.render.GranularBlockEntityRenderer;
import com.piotrek.groundworks.client.render.GranularMeshCache;
import com.piotrek.groundworks.client.render.GranularTerrainRenderer;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import com.piotrek.groundworks.networking.GranularCellSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class GroundworksClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GroundworksMod.LOGGER.info("[Groundworks] Initializing client subsystems and renderer");

        // Register client payload receiver
        ClientPlayNetworking.registerGlobalReceiver(GranularCellSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientGranularStorage.handlePayload(payload);
                GranularMeshCache.invalidate(payload.pos().asLong());
            });
        });

        // Register BlockEntityRenderer for GranularBlock
        BlockEntityRendererRegistry.register(
                GroundworksMod.GRANULAR_BLOCK_ENTITY,
                GranularBlockEntityRenderer::new
        );

        // Register world-level fallback renderer hook
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(GranularTerrainRenderer::render);

        // Clear client caches when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientGranularStorage.clear();
            GranularMeshCache.clear();
        });
    }
}
