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
    @DisplayName("Unrelated solid blocks remain non-convertible")
    void unrelatedBlockDoesNotMapToDirt() {
        assertNull(GranularMaterialRegistry.forBlockState(Blocks.STONE.defaultBlockState()));
    }
}
