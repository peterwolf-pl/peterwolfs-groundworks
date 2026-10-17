package com.piotrek.groundworks.networking;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
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

    @Test
    @DisplayName("GranularSyncHandler sends full sync first, then delta on subsequent changes")
    void testGranularSyncHandlerLifecycle() {
        ClientGranularStorage.clear();
        BlockPos pos = new BlockPos(12, 65, 34);
        GranularCell serverCell = GranularCell.full(GranularMaterialRegistry.DIRT);

        // 1. Initial sync: should be FULL
        GranularCellSyncPayload p1 = GranularSyncHandler.createPayload(pos, serverCell);
        assertFalse(p1.isDelta(), "First sync must be full");
        assertEquals(8, p1.words().length);
        assertEquals(512, p1.unitCount());

        // Apply on client
        ClientGranularStorage.handlePayload(p1);
        GranularCell clientCell = ClientGranularStorage.getCell(pos);
        assertNotNull(clientCell);
        assertEquals(512, clientCell.unitCount());
        assertArrayEquals(serverCell.occupancy(), clientCell.occupancy());

        // 2. Modify top units on server: should produce DELTA
        serverCell.removeFromTop(16);
        GranularCellSyncPayload p2 = GranularSyncHandler.createPayload(pos, serverCell);
        assertTrue(p2.isDelta(), "Subsequent sync must be delta");
        assertEquals(1, p2.words().length, "Sparse transmission should only send the changed word");
        assertEquals(serverCell.unitCount(), p2.unitCount());

        // Apply delta on client and verify consistency
        ClientGranularStorage.handlePayload(p2);
        clientCell = ClientGranularStorage.getCell(pos);
        assertNotNull(clientCell);
        assertEquals(serverCell.unitCount(), clientCell.unitCount());
        assertEquals(serverCell.revision(), clientCell.revision());
        assertArrayEquals(serverCell.occupancy(), clientCell.occupancy(),
                "Client occupancy must match server occupancy after applying delta");

        // 3. Clear server cell: should produce REMOVE payload
        serverCell.removeFromTop(serverCell.unitCount());
        assertTrue(serverCell.isEmpty());
        GranularCellSyncPayload p3 = GranularSyncHandler.createPayload(pos, serverCell);
        assertEquals(0, p3.unitCount());
        assertNull(serverCell.lastSyncedOccupancy(), "Empty cell clears last synced state");

        // Apply on client: cell should be removed
        ClientGranularStorage.handlePayload(p3);
        assertNull(ClientGranularStorage.getCell(pos), "Client storage should remove empty cell");

        // 4. Refill cell: should produce FULL payload again
        serverCell.addFromBottom(256);
        GranularCellSyncPayload p4 = GranularSyncHandler.createPayload(pos, serverCell);
        assertFalse(p4.isDelta(), "Refilled cell with no previous sync state must send full payload");
        assertEquals(8, p4.words().length);
        assertEquals(256, p4.unitCount());
    }
}
