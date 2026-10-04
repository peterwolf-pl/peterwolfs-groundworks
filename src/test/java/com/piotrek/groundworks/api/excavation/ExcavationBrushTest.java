package com.piotrek.groundworks.api.excavation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcavationBrushTest {

    @Test
    @DisplayName("Sphere brush selects microvoxels within radius and sorts closest first")
    void testSphereBrush() {
        // Center of cell (3.5, 3.5, 3.5) with radius 1.5 microvoxels
        List<ExcavationBrush.LocalVoxel> voxels = ExcavationBrush.sphere(3.5, 3.5, 3.5, 1.5);

        assertFalse(voxels.isEmpty());

        // The very closest voxel is (3, 3, 3) whose center is exactly (3.5, 3.5, 3.5), so distSq = 0.0
        assertEquals(0.0, voxels.get(0).distSq(), 1e-4);
        assertEquals(3, voxels.get(0).x());
        assertEquals(3, voxels.get(0).y());
        assertEquals(3, voxels.get(0).z());

        // Verify monotonic sorted order (closest to hit point first)
        for (int i = 1; i < voxels.size(); i++) {
            assertTrue(voxels.get(i).distSq() >= voxels.get(i - 1).distSq(),
                    "Brush voxels must be sorted ascending by distance from hit point");
        }
    }
}
