package com.piotrek.groundworks.api.material;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GranularMaterialRegistryTest {

    @BeforeAll
    static void bootstrapRegistry() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Grass block is converted to the same granular material as dirt")
    void grassBlockMapsToDirt() {
        assertSame(
                GranularMaterialRegistry.DIRT,
                GranularMaterialRegistry.forBlockState(Blocks.GRASS_BLOCK.defaultBlockState())
        );
    }

    @Test
    @DisplayName("Stone is crushed into granular cobblestone")
    void stoneMapsToCobblestone() {
        assertSame(
                GranularMaterialRegistry.COBBLESTONE,
                GranularMaterialRegistry.forBlockState(Blocks.STONE.defaultBlockState())
        );
    }

    @Test
    @DisplayName("Cobblestone block maps to the same granular cobblestone material")
    void cobblestoneMapsToCobblestone() {
        assertSame(
                GranularMaterialRegistry.COBBLESTONE,
                GranularMaterialRegistry.forBlockState(Blocks.COBBLESTONE.defaultBlockState())
        );
    }

    @Test
    @DisplayName("Unrelated solid blocks remain non-convertible")
    void unrelatedBlockDoesNotMapToGranularMaterial() {
        assertNull(GranularMaterialRegistry.forBlockState(Blocks.OAK_PLANKS.defaultBlockState()));
    }
}
