package com.piotrek.groundworks.terrain.cell;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GranularCellTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("A full cell must contain exactly 512 units and match bit count")
    void testFullCellUnits() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);
        assertEquals(512, cell.unitCount());
        assertEquals(512, cell.recount());
        assertTrue(cell.validate());
        assertTrue(cell.isFull());
        assertFalse(cell.isEmpty());
    }

    @Test
    @DisplayName("An empty cell must contain exactly 0 units")
    void testEmptyCellUnits() {
        GranularCell cell = GranularCell.empty();
        assertEquals(0, cell.unitCount());
        assertEquals(0, cell.recount());
        assertTrue(cell.validate());
        assertTrue(cell.isEmpty());
        assertFalse(cell.isFull());
    }

    @Test
    @DisplayName("Microvoxel indexing roundtrip preserves 3D coordinates")
    void testIndexingRoundtrip() {
        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 8; z++) {
                for (int x = 0; x < 8; x++) {
                    int idx = GranularCell.index(x, y, z);
                    assertTrue(idx >= 0 && idx < 512, "Index must be in [0, 511]");
                    assertEquals(x, GranularCell.indexX(idx));
                    assertEquals(y, GranularCell.indexY(idx));
                    assertEquals(z, GranularCell.indexZ(idx));
                }
            }
        }
    }

    @Test
    @DisplayName("Partial excavation removes exact requested units and preserves unit conservation")
    void testPartialExcavation() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);

        int removed1 = cell.removeFromTop(32);
        assertEquals(32, removed1);
        assertEquals(480, cell.unitCount());
        assertEquals(480, cell.recount());
        assertTrue(cell.validate());

        int removed2 = cell.removeFromTop(100);
        assertEquals(100, removed2);
        assertEquals(380, cell.unitCount());
        assertEquals(380, cell.recount());
        assertTrue(cell.validate());
    }

    @Test
    @DisplayName("Full excavation reduces cell to exactly 0 units")
    void testFullExcavation() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);
        int removed = cell.removeFromTop(512);

        assertEquals(512, removed);
        assertEquals(0, cell.unitCount());
        assertEquals(0, cell.recount());
        assertTrue(cell.isEmpty());
        assertTrue(cell.validate());
    }

    @Test
    @DisplayName("Over-excavation stops at 0 and does not invent negative volume")
    void testOverExcavation() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);
        int removed = cell.removeFromTop(1000);

        assertEquals(512, removed);
        assertEquals(0, cell.unitCount());
        assertTrue(cell.isEmpty());
    }

    @Test
    @DisplayName("Deposition fills empty cell up to capacity and respects ceiling")
    void testDeposition() {
        GranularCell cell = GranularCell.empty();
        cell.setMaterialId(GranularMaterialRegistry.SAND.id());

        int added1 = cell.addFromBottom(128);
        assertEquals(128, added1);
        assertEquals(128, cell.unitCount());
        assertEquals(128, cell.recount());
        assertTrue(cell.validate());

        int added2 = cell.addFromBottom(400);
        assertEquals(384, added2, "Should only accept remaining 384 units to hit 512");
        assertEquals(512, cell.unitCount());
        assertTrue(cell.isFull());
        assertTrue(cell.validate());
    }

    @Test
    @DisplayName("Bulk add and remove preserve per-unit revision semantics")
    void testBulkOperationsPreserveRevisionSemantics() {
        GranularCell cell = GranularCell.empty();
        cell.setMaterialId(GranularMaterialRegistry.DIRT.id());

        int beforeAdd = cell.revision();
        assertEquals(32, cell.addFromBottom(32));
        assertEquals(beforeAdd + 32, cell.revision());

        int beforeRemove = cell.revision();
        assertEquals(17, cell.removeFromTop(17));
        assertEquals(beforeRemove + 17, cell.revision());
        assertEquals(15, cell.unitCount());
        assertTrue(cell.validate());
    }

    @Test
    @DisplayName("Bulk operations preserve bottom-fill and top-removal ordering")
    void testBulkOperationsPreserveOccupancyOrdering() {
        GranularCell cell = GranularCell.empty();

        assertEquals(65, cell.addFromBottom(65));
        assertEquals(-1L, cell.occupancy()[0]);
        assertEquals(1L, cell.occupancy()[1]);

        assertEquals(1, cell.removeFromTop(1));
        assertEquals(0L, cell.occupancy()[1]);
        assertEquals(-1L, cell.occupancy()[0]);
    }

    @Test
    @DisplayName("Refreshing a copied occupancy bitset restores the unit-count invariant")
    void testRefreshUnitCountAfterOccupancyCopy() {
        GranularCell cell = GranularCell.empty();
        cell.occupancy()[0] = 0b10101L;
        cell.occupancy()[7] = Long.MIN_VALUE;

        assertFalse(cell.validate(), "Directly copied network data must initially expose the stale count");
        assertEquals(4, cell.refreshUnitCount());
        assertEquals(4, cell.unitCount());
        assertTrue(cell.validate());
    }

    @Test
    @DisplayName("Selected component excavation does not stop when dominant material changes")
    void testSelectedComponentExcavationAcrossDominanceChange() {
        GranularCell cell = GranularCell.empty();
        cell.setMaterialId(GranularMaterialRegistry.DIRT.id());
        cell.addMaterialFromBottom(GranularMaterialRegistry.DIRT, 205);
        cell.addMaterialFromBottom(GranularMaterialRegistry.GRAVEL, 205);
        cell.addMaterialFromBottom(GranularMaterialRegistry.SAND, 102);

        int removed = cell.removeMaterialFromTop(GranularMaterialRegistry.DIRT.id(), 128);

        assertEquals(128, removed);
        assertEquals(77, cell.unitsOfMaterial(GranularMaterialRegistry.DIRT.id()));
        assertEquals(205, cell.unitsOfMaterial(GranularMaterialRegistry.GRAVEL.id()));
        assertEquals(102, cell.unitsOfMaterial(GranularMaterialRegistry.SAND.id()));
        assertEquals(384, cell.unitCount());
        assertTrue(cell.validate());
        assertEquals(GranularMaterialRegistry.GRAVEL.id(), cell.materialId());
    }

    @Test
    @DisplayName("Strict volume conservation: excavated units plus remaining units equal 512")
    void testVolumeConservationLaw() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.GRAVEL);
        int initialVolume = cell.unitCount();
        int inventory = 0;

        int[] steps = {32, 64, 16, 128, 50, 100, 122};
        for (int step : steps) {
            int taken = cell.removeFromTop(step);
            inventory += taken;
            assertEquals(initialVolume, cell.unitCount() + inventory,
                    "Total material (cell + inventory) must remain constant");
            assertTrue(cell.validate());
        }

        // Return material back
        GranularCell targetCell = GranularCell.empty();
        int deposited = targetCell.addFromBottom(inventory);
        inventory -= deposited;

        assertEquals(0, inventory);
        assertEquals(initialVolume, cell.unitCount() + targetCell.unitCount(),
                "Combined material across both cells must equal initial 512");
    }
}
