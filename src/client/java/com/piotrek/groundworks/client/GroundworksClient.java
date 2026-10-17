package com.piotrek.groundworks.client;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.client.camera.CameraZoomManager;
import com.piotrek.groundworks.client.render.GranularMeshCache;
import com.piotrek.groundworks.client.render.GranularTerrainRenderer;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import com.piotrek.groundworks.networking.GranularCellSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

        // The world-level renderer submits granular terrain meshes during the
        // collect-submits phase of the render graph. Submitting here (instead of
        // after translucent terrain) ensures the geometry is compiled in the same
        // frame as vanilla terrain and entities, completely eliminating 1-frame latency
        // and visual lag when rotating the camera quickly.
        LevelRenderEvents.COLLECT_SUBMITS.register(GranularTerrainRenderer::render);

        // Register camera zoom controls & tick handler
        CameraZoomManager.init();
        ClientTickEvents.END_CLIENT_TICK.register(CameraZoomManager::clientTick);

        // Clear client caches when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientGranularStorage.clear();
            GranularMeshCache.clear();
        });
    }
}
