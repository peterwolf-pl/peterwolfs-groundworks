package com.piotrek.groundworks.tree;

import com.piotrek.groundworks.api.tree.FallenLogPhysics;
import com.piotrek.groundworks.api.tree.GroundworksTreeApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class GroundworksTreeApiTest {

    @Test
    @DisplayName("Break threshold scales reasonably with tree height")
    void breakThresholdScalesWithHeight() {
        float h2 = GroundworksTreeApi.calculateBreakThreshold(2);
        float h4 = GroundworksTreeApi.calculateBreakThreshold(4);
        float h10 = GroundworksTreeApi.calculateBreakThreshold(10);
        float h30 = GroundworksTreeApi.calculateBreakThreshold(30);

        assertEquals(5.0F, h2, 1e-4F, "Short saplings/trees have base threshold");
        assertEquals(5.0F, h4, 1e-4F, "Base height trees (<=4) have base threshold");
        assertTrue(h10 > h4, "Taller tree (height 10) must require more push effort than base");
        assertTrue(h30 >= h10, "Giant tree (height 30) must be capped or higher");
        assertTrue(h30 <= 10.0F, "Threshold must not exceed reasonable machine limits");
    }

    @Test
    @DisplayName("Pushing cluster with vertical direction returns false immediately")
    void verticalPushRejected() {
        assertFalse(
                FallenLogPhysics.pushCluster(null, Collections.singleton(BlockPos.ZERO), Direction.UP, null),
                "Vertical UP push must be rejected"
        );
        assertFalse(
                FallenLogPhysics.pushCluster(null, Collections.singleton(BlockPos.ZERO), Direction.DOWN, null),
                "Vertical DOWN push must be rejected"
        );
        assertFalse(
                FallenLogPhysics.pushCluster(null, Collections.emptySet(), Direction.NORTH, null),
                "Empty cluster must return false"
        );
    }
}
