package com.piotrek.groundworks.simulation;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GranularRelaxationTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Repose threshold reflects material cohesion and repose angle")
    void testReposeThresholds() {
        int sandThreshold = GranularRelaxationEngine.computeReposeThreshold(GranularMaterialRegistry.SAND);
        int dirtThreshold = GranularRelaxationEngine.computeReposeThreshold(GranularMaterialRegistry.DIRT);
        int gravelThreshold = GranularRelaxationEngine.computeReposeThreshold(GranularMaterialRegistry.GRAVEL);

        assertTrue(sandThreshold > 0);
        assertTrue(dirtThreshold > 0);
        assertTrue(gravelThreshold > 0);

        // Dirt with cohesion 0.5 must require a steeper gradient to slide than sand with cohesion 0.1
        assertTrue(dirtThreshold > sandThreshold,
                "Cohesive dirt should resist sliding better than loose sand");
    }

    @Test
    @DisplayName("Relaxation evaluates all eight horizontal neighbors")
    void testEightNeighborRing() {
        assertEquals(8, GranularRelaxationEngine.HORIZONTAL_OFFSETS.length);

        Set<String> uniqueOffsets = new HashSet<>();
        Arrays.stream(GranularRelaxationEngine.HORIZONTAL_OFFSETS)
                .forEach(offset -> uniqueOffsets.add(offset[0] + "," + offset[1]));

        assertEquals(8, uniqueOffsets.size());
        assertTrue(uniqueOffsets.contains("1,1"));
        assertTrue(uniqueOffsets.contains("-1,-1"));
        assertFalse(uniqueOffsets.contains("0,0"));
    }

    @Test
    @DisplayName("Direction tie breaking is deterministic and position-sensitive")
    void testDeterministicDirectionRotation() {
        BlockPos origin = new BlockPos(0, 80, 0);
        int first = GranularRelaxationEngine.directionStartIndex(12345L, origin, 7);

        assertEquals(first,
                GranularRelaxationEngine.directionStartIndex(12345L, origin, 7));
        assertNotEquals(first,
                GranularRelaxationEngine.directionStartIndex(12345L, new BlockPos(1, 80, 0), 7));
    }

    @Test
    @DisplayName("Lateral transfer equalizes excess gradient without overshoot")
    void testStableLateralTransfer() {
        int threshold = 64;
        int moved = GranularRelaxationEngine.computeLateralTransfer(200, 40, threshold);

        assertEquals(32, moved, "Transfer remains bounded by the per-pass budget");
        int gradientAfter = (200 - moved) - (40 + moved);
        assertTrue(gradientAfter < 160 && gradientAfter > 0,
                "A bounded pass must reduce the gradient without reversing it");

        int finalMove = GranularRelaxationEngine.computeLateralTransfer(120, 40, threshold);
        assertEquals(8, finalMove);
        assertEquals(threshold, (120 - finalMove) - (40 + finalMove),
                "The final pass settles exactly at the stable threshold");
        assertEquals(0, GranularRelaxationEngine.computeLateralTransfer(104, 40, threshold));
    }

    @Test
    @DisplayName("Unit transfer between two cells preserves total volume exactly")
    void testDirectCellTransferConservation() {
        GranularCell source = GranularCell.full(GranularMaterialRegistry.SAND);
        GranularCell target = GranularCell.empty();
        target.setMaterialId(GranularMaterialRegistry.SAND.id());

        int initialTotal = source.unitCount() + target.unitCount();
        assertEquals(512, initialTotal);

        int transferAmount = 64;
        int removed = source.removeFromTop(transferAmount);
        int added = target.addFromBottom(removed);

        assertEquals(transferAmount, removed);
        assertEquals(removed, added);
        assertEquals(initialTotal, source.unitCount() + target.unitCount(),
                "Combined material must strictly equal initial volume");
        assertTrue(source.validate());
        assertTrue(target.validate());
    }

    @Test
    @DisplayName("Simulated pile formation: multi-step transfer conserves units without loss or creation")
    void testMultiStepPileFormationConservation() {
        GranularCell apex = GranularCell.full(GranularMaterialRegistry.DIRT);
        GranularCell north = GranularCell.empty();
        GranularCell south = GranularCell.empty();
        GranularCell east = GranularCell.empty();
        GranularCell west = GranularCell.empty();

        north.setMaterialId(GranularMaterialRegistry.DIRT.id());
        south.setMaterialId(GranularMaterialRegistry.DIRT.id());
        east.setMaterialId(GranularMaterialRegistry.DIRT.id());
        west.setMaterialId(GranularMaterialRegistry.DIRT.id());

        int initialTotal = apex.unitCount();
        GranularCell[] slopeCells = {north, south, east, west};

        // Simulate successive material relaxation passes
        int totalTransferred = 0;
        for (int step = 0; step < 8; step++) {
            for (GranularCell slope : slopeCells) {
                int gradient = apex.unitCount() - slope.unitCount();
                if (gradient > 64) {
                    int toMove = Math.min(16, gradient / 4);
                    int rem = apex.removeFromTop(toMove);
                    int add = slope.addFromBottom(rem);
                    assertEquals(rem, add);
                    totalTransferred += rem;
                }
            }
        }

        int currentTotal = apex.unitCount() + north.unitCount() + south.unitCount()
                + east.unitCount() + west.unitCount();

        assertEquals(initialTotal, currentTotal,
                "Total units across apex and all 4 slope cells must equal initial 512");
        assertTrue(apex.validate());
        for (GranularCell slope : slopeCells) {
            assertTrue(slope.validate());
        }
    }
}
