package com.piotrek.groundworks.block;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GranularCollisionShapeCacheTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Collision shape is reused until the cell revision changes")
    void testRevisionCachedCollisionShape() {
        GranularCollisionShapeCache.clear();
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);

        VoxelShape first = GranularCollisionShapeCache.get(cell);
        VoxelShape cached = GranularCollisionShapeCache.get(cell);
        assertSame(first, cached);

        cell.removeFromTop(32);
        VoxelShape rebuilt = GranularCollisionShapeCache.get(cell);
        assertNotSame(first, rebuilt);
    }
}
