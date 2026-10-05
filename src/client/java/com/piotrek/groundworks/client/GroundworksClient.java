package com.piotrek.groundworks.client;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.client.render.GranularMeshCache;
import com.piotrek.groundworks.client.render.GranularTerrainRenderer;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import com.piotrek.groundworks.networking.GranularCellSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class GroundworksClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GroundworksMod.LOGGER.info("[Groundworks] Initializing client subsystems and renderer");

        // Register client payload receiver
        ClientPlayNetworking.registerGlobalReceiver(GranularCellSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientGranularStorage.handlePayload(payload);
                GranularMeshCache.invalidateNeighborhood(payload.pos());
            });
        });

        // The world-level renderer is the single geometry path. It renders both
        // anchored and relaxation-created cells from the authoritative client
        // mirror. Registering the block-entity renderer as well drew anchored
        // cells twice and could leave stale vertical towers after relaxation.
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(GranularTerrainRenderer::render);

        // Clear client caches when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientGranularStorage.clear();
            GranularMeshCache.clear();
        });
    }
}
