package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.GroundworksMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

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
    public static final Identifier WHITE_HARD_HAT_ID = Identifier.fromNamespaceAndPath(
            GroundworksMod.MOD_ID, "white_hard_hat"
    );

    public static final ResourceKey<Item> DEBUG_EXCAVATION_TOOL_KEY = ResourceKey.create(
            Registries.ITEM, DEBUG_EXCAVATION_TOOL_ID
    );
    public static final ResourceKey<Item> GROUNDWORKS_PICKAXE_KEY = ResourceKey.create(
            Registries.ITEM, GROUNDWORKS_PICKAXE_ID
    );
    public static final ResourceKey<Item> WHITE_HARD_HAT_KEY = ResourceKey.create(
            Registries.ITEM, WHITE_HARD_HAT_ID
    );

    public static final ResourceKey<EquipmentAsset> WHITE_HARD_HAT_ASSET_KEY = ResourceKey.create(
            EquipmentAssets.ROOT_ID, WHITE_HARD_HAT_ID
    );

    public static final ArmorMaterial WHITE_HARD_HAT_MATERIAL = new ArmorMaterial(
            15,
            Map.of(ArmorType.HELMET, 2),
            9,
            SoundEvents.ARMOR_EQUIP_IRON,
            1.0F,
            0.05F,
            ItemTags.REPAIRS_IRON_ARMOR,
            WHITE_HARD_HAT_ASSET_KEY
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
    public static final Item WHITE_HARD_HAT = new Item(
            new Item.Properties()
                    .setId(WHITE_HARD_HAT_KEY)
                    .humanoidArmor(WHITE_HARD_HAT_MATERIAL, ArmorType.HELMET)
    );

    public static final CreativeModeTab TAB = FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.pw_groundworks.group"))
            .icon(DEBUG_EXCAVATION_TOOL::getDefaultInstance)
            .displayItems((parameters, output) -> {
                output.accept(WHITE_HARD_HAT);
                output.accept(DEBUG_EXCAVATION_TOOL);
                output.accept(GROUNDWORKS_PICKAXE);
            })
            .build();

    private GroundworksItems() {}

    public static boolean shouldMitigateDamage(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        if (entity == null || source == null) {
            return false;
        }
        if (source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_STALACTITE)) {
            return entity.getItemBySlot(EquipmentSlot.HEAD).is(WHITE_HARD_HAT);
        }
        return false;
    }

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
                BuiltInRegistries.ITEM,
                WHITE_HARD_HAT_KEY,
                WHITE_HARD_HAT
        );

        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(GroundworksMod.MOD_ID, "main"),
                TAB
        );

        // Register safety helmet protection against falling excavation debris and rocks
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (shouldMitigateDamage(entity, source)) {
                entity.getItemBySlot(EquipmentSlot.HEAD).hurtAndBreak(1, entity, EquipmentSlot.HEAD);
                if (!entity.level().isClientSide()) {
                    entity.level().playSound(
                            null,
                            entity.getX(),
                            entity.getY(),
                            entity.getZ(),
                            SoundEvents.ANVIL_PLACE,
                            SoundSource.PLAYERS,
                            0.6F,
                            1.5F
                    );
                }
                return false;
            }
            return true;
        });

        GroundworksMod.LOGGER.info("[Groundworks] Registered items and creative tab");
    }
}
