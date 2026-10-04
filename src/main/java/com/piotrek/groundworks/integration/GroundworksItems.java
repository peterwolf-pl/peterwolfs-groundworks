package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.GroundworksMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/**
 * Registers all Groundworks items.
 */
public final class GroundworksItems {

    public static final DebugExcavationTool DEBUG_EXCAVATION_TOOL = new DebugExcavationTool(
            new Item.Properties().stacksTo(1)
    );

    public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.pw_groundworks.group"))
            .icon(DEBUG_EXCAVATION_TOOL::getDefaultInstance)
            .displayItems((parameters, output) -> {
                output.accept(DEBUG_EXCAVATION_TOOL);
            })
            .build();

    private GroundworksItems() {}

    public static void register() {
        Registry.register(
                BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath(GroundworksMod.MOD_ID, "debug_excavation_tool"),
                DEBUG_EXCAVATION_TOOL
        );

        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(GroundworksMod.MOD_ID, "main"),
                TAB
        );

        GroundworksMod.LOGGER.info("[Groundworks] Registered items and creative tab");
    }
}
