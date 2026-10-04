package com.piotrek.groundworks.client;

import com.piotrek.groundworks.GroundworksMod;
import net.fabricmc.api.ClientModInitializer;

public class GroundworksClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GroundworksMod.LOGGER.info("[Groundworks] Initializing client subsystems");
    }
}
