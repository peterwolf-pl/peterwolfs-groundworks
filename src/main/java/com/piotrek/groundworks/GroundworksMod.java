package com.piotrek.groundworks;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.block.GranularBlock;
import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.command.GroundworksCommand;
import com.piotrek.groundworks.integration.GroundworksItems;
import com.piotrek.groundworks.networking.GranularCellSyncPayload;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroundworksMod implements ModInitializer {

    public static final String MOD_ID = "pw_groundworks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Granular Block and BlockEntity registration
    public static final Identifier GRANULAR_BLOCK_ID = Identifier.fromNamespaceAndPath(MOD_ID, "granular_block");
    public static final ResourceKey<Block> GRANULAR_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, GRANULAR_BLOCK_ID);

    public static final GranularBlock GRANULAR_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            GRANULAR_BLOCK_KEY,
            new GranularBlock(BlockBehaviour.Properties.of()
                    .setId(GRANULAR_BLOCK_KEY)
                    .strength(0.5F)
                    .noOcclusion())
    );

    public static final ResourceKey<BlockEntityType<?>> GRANULAR_BE_KEY = ResourceKey.create(
            Registries.BLOCK_ENTITY_TYPE, GRANULAR_BLOCK_ID
    );

    public static final BlockEntityType<GranularBlockEntity> GRANULAR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            GRANULAR_BE_KEY,
            FabricBlockEntityTypeBuilder.create(GranularBlockEntity::new, GRANULAR_BLOCK).build()
    );

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

        // Server tick
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
