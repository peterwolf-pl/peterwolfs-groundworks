package com.piotrek.groundworks;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.command.GroundworksCommand;
import com.piotrek.groundworks.integration.GroundworksItems;
import com.piotrek.groundworks.networking.GranularCellSyncPayload;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Peterwolf's Groundworks — main mod entrypoint.
 *
 * <p>This mod introduces a volumetric, deformable granular terrain engine.
 * Normal Minecraft blocks are lazily converted into granular cells when
 * excavation or deposition interacts with them.
 */
public class GroundworksMod implements ModInitializer {

    public static final String MOD_ID = "pw_groundworks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Groundworks] Initializing Peterwolf's Groundworks");

        // Register materials
        GranularMaterialRegistry.bootstrap();

        // Register items
        GroundworksItems.register();

        // Register network payload types
        PayloadTypeRegistry.clientboundPlay().register(
                GranularCellSyncPayload.TYPE,
                GranularCellSyncPayload.CODEC
        );

        // Register commands
        CommandRegistrationCallback.EVENT.register(GroundworksCommand::register);

        // Server lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("[Groundworks] Server started, granular terrain ready");
        });

        // Server tick — process dirty granular cells and simulation relaxation
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getAllLevels().forEach(level -> {
                GranularWorldStorage storage = GranularWorldStorage.get(level);
                if (storage != null) {
                    storage.tick();
                }
            });
        });

        LOGGER.info("[Groundworks] Initialization complete");
    }
}
