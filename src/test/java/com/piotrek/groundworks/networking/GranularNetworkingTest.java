package com.piotrek.groundworks.networking;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GranularNetworkingTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Full sync payload captures all 8 occupancy words and properties")
    void testFullPayloadCreation() {
        BlockPos pos = new BlockPos(10, 64, -20);
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);

        GranularCellSyncPayload payload = GranularCellSyncPayload.full(pos, cell);

        assertEquals(pos, payload.pos());
        assertEquals(cell.materialId(), payload.materialId());
        assertEquals(512, payload.unitCount());
        assertFalse(payload.isDelta());
        assertEquals(0xFF, payload.changedWordMask());
        assertEquals(8, payload.words().length);
    }

    @Test
    @DisplayName("Delta sync payload detects only modified words (sparse transmission)")
    void testDeltaPayloadSparseWords() {
        BlockPos pos = new BlockPos(5, 70, 15);
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.SAND);
        long[] initialOccupancy = cell.occupancy().clone();

        // Remove 10 units from top (affects only top Y word, word index 7)
        cell.removeFromTop(10);

        GranularCellSyncPayload payload = GranularCellSyncPayload.delta(pos, cell, initialOccupancy);

        assertTrue(payload.isDelta());
        assertEquals(1, Integer.bitCount(payload.changedWordMask()),
                "Only 1 word should have changed from removing top units");
        assertEquals(1, payload.words().length,
                "Sparse transmission should send only 1 word across network");
    }

    @Test
    @DisplayName("Removal payload carries unitCount=0 and empty words array")
    void testRemovePayload() {
        BlockPos pos = new BlockPos(0, 50, 0);
        GranularCellSyncPayload payload = GranularCellSyncPayload.remove(pos);

        assertEquals(pos, payload.pos());
        assertEquals(0, payload.unitCount());
        assertEquals(0, payload.words().length);
    }
}
