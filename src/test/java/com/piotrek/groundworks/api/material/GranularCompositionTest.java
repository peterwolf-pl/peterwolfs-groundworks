package com.piotrek.groundworks.api.material;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GranularCompositionTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Composition preserves exact integer material counts")
    void testExactCounts() {
        GranularComposition composition = new GranularComposition();
        composition.add(GranularMaterialRegistry.SAND, 102);
        composition.add(GranularMaterialRegistry.GRAVEL, 205);
        composition.add(GranularMaterialRegistry.DIRT, 205);

        assertEquals(512, composition.totalUnits());
        assertEquals(102, composition.unitsOf(GranularMaterialRegistry.SAND));
        assertEquals(205, composition.unitsOf(GranularMaterialRegistry.GRAVEL));
        assertEquals(205, composition.unitsOf(GranularMaterialRegistry.DIRT));
    }

    @Test
    @DisplayName("Proportional extraction preserves exact total volume")
    void testProportionalExtraction() {
        GranularComposition composition = new GranularComposition();
        composition.add(GranularMaterialRegistry.SAND, 102);
        composition.add(GranularMaterialRegistry.GRAVEL, 205);
        composition.add(GranularMaterialRegistry.DIRT, 205);

        GranularComposition extracted = composition.extractProportional(64);

        assertEquals(64, extracted.totalUnits());
        assertEquals(448, composition.totalUnits());
        assertEquals(512, extracted.totalUnits() + composition.totalUnits());
    }

    @Test
    @DisplayName("Cobblestone uses 4x4x4 visual clusters")
    void testCobblestoneClusterSize() {
        assertEquals(4, GranularMaterialRegistry.COBBLESTONE.visualClusterSizeMicrovoxels());

        GranularComposition composition = new GranularComposition();
        composition.add(GranularMaterialRegistry.COBBLESTONE, 256);
        composition.add(GranularMaterialRegistry.DIRT, 256);

        long seed = 123456789L;
        int first = composition.sampleVisualMaterial(seed, 0, 0, 0);

        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    assertEquals(first, composition.sampleVisualMaterial(seed, x, y, z),
                            "A 4x4x4 coarse cluster must remain visually stable");
                }
            }
        }
    }
}
