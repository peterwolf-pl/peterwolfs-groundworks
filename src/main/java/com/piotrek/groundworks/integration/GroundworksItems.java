package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.GroundworksMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/**
 * Registers all Groundworks items.
 */
public final class GroundworksItems {

    public static final Identifier DEBUG_EXCAVATION_TOOL_ID = Identifier.fromNamespaceAndPath(
            GroundworksMod.MOD_ID, "debug_excavation_tool"
    );
    public static final Identifier GROUNDWORKS_PICKAXE_ID = Identifier.fromNamespaceAndPath(
            GroundworksMod.MOD_ID, "groundworks_pickaxe"
    );

    public static final ResourceKey<Item> DEBUG_EXCAVATION_TOOL_KEY = ResourceKey.create(
            Registries.ITEM, DEBUG_EXCAVATION_TOOL_ID
    );
    public static final ResourceKey<Item> GROUNDWORKS_PICKAXE_KEY = ResourceKey.create(
            Registries.ITEM, GROUNDWORKS_PICKAXE_ID
    );

    public static final DebugExcavationTool DEBUG_EXCAVATION_TOOL = new DebugExcavationTool(
            new Item.Properties()
                    .setId(DEBUG_EXCAVATION_TOOL_KEY)
                    .stacksTo(1)
    );
    public static final GroundworksPickaxe GROUNDWORKS_PICKAXE = new GroundworksPickaxe(
            new Item.Properties()
                    .setId(GROUNDWORKS_PICKAXE_KEY)
                    .stacksTo(1)
    );

    public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.pw_groundworks.group"))
            .icon(DEBUG_EXCAVATION_TOOL::getDefaultInstance)
            .displayItems((parameters, output) -> {
                output.accept(DEBUG_EXCAVATION_TOOL);
                output.accept(GROUNDWORKS_PICKAXE);
            })
            .build();

    private GroundworksItems() {}

    public static void register() {
        Registry.register(
                BuiltInRegistries.ITEM,
                DEBUG_EXCAVATION_TOOL_KEY,
                DEBUG_EXCAVATION_TOOL
        );
        Registry.register(
                BuiltInRegistries.ITEM,
                GROUNDWORKS_PICKAXE_KEY,
                GROUNDWORKS_PICKAXE
        );

        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(GroundworksMod.MOD_ID, "main"),
                TAB
        );

        GroundworksMod.LOGGER.info("[Groundworks] Registered items and creative tab");
    }
}
