package com.piotrek.groundworks.client;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.client.storage.ClientGranularStorage;
import com.piotrek.groundworks.networking.GranularCellSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class GroundworksClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GroundworksMod.LOGGER.info("[Groundworks] Initializing client subsystems and networking");

        // Register client payload receiver
        ClientPlayNetworking.registerGlobalReceiver(GranularCellSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientGranularStorage.handlePayload(payload);
            });
        });

        // Clear client cache when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientGranularStorage.clear();
        });
    }
}
