package com.piotrek.groundworks.terrain.cell;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GranularCellSerializationTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("NBT serialization and deserialization preserves full cell state")
    void testFullCellNbtRoundtrip() {
        GranularCell original = GranularCell.full(GranularMaterialRegistry.DIRT);
        CompoundTag tag = original.save();

        GranularCell loaded = GranularCell.load(tag);
        assertNotNull(loaded);
        assertEquals(original.materialId(), loaded.materialId());
        assertEquals(original.unitCount(), loaded.unitCount());
        assertEquals(original.revision(), loaded.revision());
        assertArrayEquals(original.occupancy(), loaded.occupancy());
        assertTrue(loaded.validate());
    }

    @Test
    @DisplayName("NBT serialization and deserialization preserves partially excavated cell")
    void testPartialCellNbtRoundtrip() {
        GranularCell original = GranularCell.full(GranularMaterialRegistry.SAND);
        original.removeFromTop(137);

        CompoundTag tag = original.save();
        GranularCell loaded = GranularCell.load(tag);

        assertNotNull(loaded);
        assertEquals(original.materialId(), loaded.materialId());
        assertEquals(375, loaded.unitCount());
        assertEquals(original.unitCount(), loaded.unitCount());
        assertArrayEquals(original.occupancy(), loaded.occupancy());
        assertTrue(loaded.validate());
    }

    @Test
    @DisplayName("NBT roundtrip preserves exact mixed composition")
    void testMixedCompositionNbtRoundtrip() {
        GranularCell original = GranularCell.empty();
        original.setMaterialId(GranularMaterialRegistry.DIRT.id());
        original.addMaterialFromBottom(GranularMaterialRegistry.DIRT, 205);
        original.addMaterialFromBottom(GranularMaterialRegistry.GRAVEL, 205);
        original.addMaterialFromBottom(GranularMaterialRegistry.SAND, 102);

        CompoundTag tag = original.save();
        GranularCell loaded = GranularCell.load(tag);

        assertNotNull(loaded);
        assertEquals(512, loaded.unitCount());
        assertEquals(205, loaded.unitsOfMaterial(GranularMaterialRegistry.DIRT.id()));
        assertEquals(205, loaded.unitsOfMaterial(GranularMaterialRegistry.GRAVEL.id()));
        assertEquals(102, loaded.unitsOfMaterial(GranularMaterialRegistry.SAND.id()));
        assertFalse(loaded.isPureMaterial());
        assertTrue(loaded.validate());
    }

    @Test
    @DisplayName("Column heights are computed correctly and match microvoxel state")
    void testColumnHeights() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);

        // Initially all columns should be at height 7 (top)
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                assertEquals(7, cell.getColumnHeight(x, z));
            }
        }

        // Remove top layer (64 units)
        cell.removeFromTop(64);

        // Now all columns should be at height 6
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                assertEquals(6, cell.getColumnHeight(x, z));
            }
        }
    }

    @Test
    @DisplayName("Stress test: 1,000 random add/remove operations strictly preserve unit conservation")
    void testStressVolumeConservation() {
        GranularCell cell = GranularCell.empty();
        cell.setMaterialId(GranularMaterialRegistry.GRAVEL.id());

        int externalInventory = 10_000;
        int initialTotal = externalInventory;
        Random rng = new Random(42);

        for (int i = 0; i < 1_000; i++) {
            boolean add = rng.nextBoolean();
            int amount = rng.nextInt(64) + 1;

            if (add) {
                int toAdd = Math.min(amount, externalInventory);
                int added = cell.addFromBottom(toAdd);
                externalInventory -= added;
            } else {
                int removed = cell.removeFromTop(amount);
                externalInventory += removed;
            }

            assertEquals(initialTotal, cell.unitCount() + externalInventory,
                    "Total material must remain invariant at step " + i);
            assertTrue(cell.validate(), "Cell internal state must be valid at step " + i);
        }
    }
}
