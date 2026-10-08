package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.GroundworksMod;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class WhiteHardHatTest {

    @BeforeAll
    static void init() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        java.lang.reflect.Field intrusiveField = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusiveField.setAccessible(true);
        intrusiveField.set(net.minecraft.core.registries.BuiltInRegistries.ITEM, new java.util.IdentityHashMap<>());

        java.lang.reflect.Field frozenField = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen");
        frozenField.setAccessible(true);
        frozenField.set(net.minecraft.core.registries.BuiltInRegistries.ITEM, false);
    }

    @Test
    void testWhiteHardHatMaterial() {
        assertNotNull(GroundworksItems.WHITE_HARD_HAT_MATERIAL, "Hard hat armor material must be defined");
        assertEquals(15, GroundworksItems.WHITE_HARD_HAT_MATERIAL.durability(), "Durability factor must be 15");
        assertEquals(165, ArmorType.HELMET.getDurability(GroundworksItems.WHITE_HARD_HAT_MATERIAL.durability()),
                "Helmet durability must be 165");
        assertEquals(2, GroundworksItems.WHITE_HARD_HAT_MATERIAL.defense().get(ArmorType.HELMET),
                "Armor defense points for helmet must be 2");
        assertEquals(1.0F, GroundworksItems.WHITE_HARD_HAT_MATERIAL.toughness(),
                "Armor toughness must be 1.0");
        assertEquals(0.05F, GroundworksItems.WHITE_HARD_HAT_MATERIAL.knockbackResistance(),
                "Knockback resistance must be 0.05");
        assertEquals(SoundEvents.ARMOR_EQUIP_IRON, GroundworksItems.WHITE_HARD_HAT_MATERIAL.equipSound(),
                "Equip sound must be ARMOR_EQUIP_IRON");
        assertEquals(GroundworksItems.WHITE_HARD_HAT_ASSET_KEY, GroundworksItems.WHITE_HARD_HAT_MATERIAL.assetId(),
                "Equipment asset key must match");
    }

    @Test
    void testDamageMitigationCheck() {
        // Without an entity wearing the hard hat, returns false
        assertFalse(GroundworksItems.shouldMitigateDamage(null, null) == true);
    }

    @Test
    void testAssetFilesExist() {
        // Equipment JSON definition
        try (InputStream stream = getClass().getResourceAsStream("/assets/pw_groundworks/equipment/white_hard_hat.json")) {
            assertNotNull(stream, "Equipment JSON definition must exist");
        } catch (Exception e) {
            fail("Failed to open equipment JSON: " + e.getMessage());
        }

        // Humanoid textures
        try (InputStream stream = getClass().getResourceAsStream("/assets/pw_groundworks/textures/entity/equipment/humanoid/white_hard_hat.png")) {
            assertNotNull(stream, "Humanoid armor texture must exist");
        } catch (Exception e) {
            fail("Failed to open humanoid texture: " + e.getMessage());
        }

        try (InputStream stream = getClass().getResourceAsStream("/assets/pw_groundworks/textures/entity/equipment/humanoid_baby/white_hard_hat.png")) {
            assertNotNull(stream, "Humanoid baby armor texture must exist");
        } catch (Exception e) {
            fail("Failed to open humanoid baby texture: " + e.getMessage());
        }

        // Item 2D icon texture
        try (InputStream stream = getClass().getResourceAsStream("/assets/pw_groundworks/textures/item/white_hard_hat.png")) {
            assertNotNull(stream, "Item 2D texture must exist");
        } catch (Exception e) {
            fail("Failed to open item texture: " + e.getMessage());
        }

        // Item model and definition JSON
        try (InputStream stream = getClass().getResourceAsStream("/assets/pw_groundworks/items/white_hard_hat.json")) {
            assertNotNull(stream, "Item definition JSON must exist");
        } catch (Exception e) {
            fail("Failed to open items/white_hard_hat.json: " + e.getMessage());
        }

        try (InputStream stream = getClass().getResourceAsStream("/assets/pw_groundworks/models/item/white_hard_hat.json")) {
            assertNotNull(stream, "Item model JSON must exist");
        } catch (Exception e) {
            fail("Failed to open models/item/white_hard_hat.json: " + e.getMessage());
        }
    }
}
