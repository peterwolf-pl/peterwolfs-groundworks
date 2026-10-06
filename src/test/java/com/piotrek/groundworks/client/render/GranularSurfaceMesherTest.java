package com.piotrek.groundworks.client.render;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.CellMesh;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class GranularSurfaceMesherTest {

    @BeforeAll
    static void setUp() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Empty cell produces an empty mesh with 0 quads")
    void testEmptyCellMesh() {
        GranularCell cell = GranularCell.empty();
        CellMesh mesh = GranularSurfaceMesher.generateMesh(cell);

        assertTrue(mesh.isEmpty());
        assertEquals(0, mesh.quads().size());
    }

    @Test
    @DisplayName("Single occupied column produces one surface and four edge skirts")
    void testSingleMicrovoxelMesh() {
        GranularCell cell = GranularCell.empty();
        cell.setMaterialId(GranularMaterialRegistry.DIRT.id());
        cell.set(3, 3, 3);

        CellMesh mesh = GranularSurfaceMesher.generateMesh(cell);

        assertFalse(mesh.isEmpty());
        assertEquals(5, mesh.quads().size(),
                "Heightfield fast path omits the hidden underside");
    }

    @Test
    @DisplayName("Full cell emits smooth top grid and only perimeter skirts")
    void testFullCellMeshBoundaryOnly() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);
        CellMesh mesh = GranularSurfaceMesher.generateMesh(cell);

        // 64 top quads + 32 perimeter skirts. The old voxel-box mesher emitted
        // 384 quads, so this also guards the intended 75% geometry reduction.
        assertEquals(96, mesh.quads().size());
    }


    @Test
    @DisplayName("Buried full cell keeps exposed perimeter walls in a multi-block pile")
    void testBuriedCellKeepsExposedPerimeterWalls() {
        BlockPos lowerPos = BlockPos.ZERO;
        BlockPos upperPos = lowerPos.above();

        GranularCell lower = GranularCell.full(GranularMaterialRegistry.DIRT);
        GranularCell upper = GranularCell.full(GranularMaterialRegistry.DIRT);

        Map<BlockPos, GranularCell> cells = new HashMap<>();
        cells.put(lowerPos, lower);
        cells.put(upperPos, upper);

        CellMesh lowerMesh = GranularSurfaceMesher.generateMesh(lowerPos, lower, cells::get);

        // The 64 top quads are hidden by the cell above, but the 32 perimeter
        // skirts must remain. The old code returned an empty mesh here, making
        // the lower tier of any pile taller than one block appear transparent.
        assertEquals(32, lowerMesh.quads().size());
        assertFalse(lowerMesh.isEmpty());

        lowerMesh.quads().forEach(quad -> {
            assertEquals(0.0f, quad.n0().y, 0.0001f,
                    "Buried lower-cell geometry should contain only vertical walls");
            float maxY = Math.max(
                    Math.max(quad.v0().y, quad.v1().y),
                    Math.max(quad.v2().y, quad.v3().y));
            assertEquals(1.0f, maxY, 0.0001f,
                    "Buried full-cell wall must reach the upper block boundary without a gap");
        });
    }

    @Test
    @DisplayName("Neighboring cells calculate identical shared-border heights")
    void testSharedBorderContinuity() {
        BlockPos leftPos = BlockPos.ZERO;
        BlockPos rightPos = leftPos.east();
        GranularCell left = GranularCell.full(GranularMaterialRegistry.DIRT);
        GranularCell right = GranularCell.empty();
        right.setMaterialId(GranularMaterialRegistry.DIRT.id());
        right.addFromBottom(256);

        Map<BlockPos, GranularCell> cells = new HashMap<>();
        cells.put(leftPos, left);
        cells.put(rightPos, right);

        CellMesh leftMesh = GranularSurfaceMesher.generateMesh(leftPos, left, cells::get);
        CellMesh rightMesh = GranularSurfaceMesher.generateMesh(rightPos, right, cells::get);

        Set<Integer> leftHeights = boundaryHeights(leftMesh, 1.0f);
        Set<Integer> rightHeights = boundaryHeights(rightMesh, 0.0f);
        assertEquals(leftHeights, rightHeights,
                "Both sides of a cell border must use bit-identical height samples");
    }

    private static Set<Integer> boundaryHeights(CellMesh mesh, float x) {
        Set<Integer> heights = new TreeSet<>();
        mesh.quads().forEach(quad -> {
            if (quad.n0().y > 0.0f && Math.abs(quad.v0().x - x) < 0.0001f) {
                heights.add(Float.floatToIntBits(quad.v0().y));
            }
            if (quad.n1().y > 0.0f && Math.abs(quad.v1().x - x) < 0.0001f) {
                heights.add(Float.floatToIntBits(quad.v1().y));
            }
            if (quad.n2().y > 0.0f && Math.abs(quad.v2().x - x) < 0.0001f) {
                heights.add(Float.floatToIntBits(quad.v2().y));
            }
            if (quad.n3().y > 0.0f && Math.abs(quad.v3().x - x) < 0.0001f) {
                heights.add(Float.floatToIntBits(quad.v3().y));
            }
        });
        return heights;
    }

    @Test
    @DisplayName("A changed cell invalidates only meshes that sample its neighborhood")
    void testEventDrivenNeighborhoodInvalidation() {
        GranularMeshCache.clear();
        ClientGranularStorage.clear();

        BlockPos changed = new BlockPos(10, 70, 10);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos pos = changed.offset(dx, 0, dz);
                GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);
                ClientGranularStorage.putCell(pos, cell);
                GranularMeshCache.getOrBuild(pos.asLong(), cell);
            }
        }

        BlockPos below = changed.below();
        GranularCell belowCell = GranularCell.full(GranularMaterialRegistry.DIRT);
        ClientGranularStorage.putCell(below, belowCell);
        GranularMeshCache.getOrBuild(below.asLong(), belowCell);

        BlockPos far = changed.offset(3, 0, 0);
        GranularCell farCell = GranularCell.full(GranularMaterialRegistry.DIRT);
        ClientGranularStorage.putCell(far, farCell);
        GranularMeshCache.getOrBuild(far.asLong(), farCell);

        assertEquals(11, GranularMeshCache.cachedMeshCount());
        GranularMeshCache.invalidateNeighborhood(changed);
        assertEquals(1, GranularMeshCache.cachedMeshCount());
        assertTrue(GranularMeshCache.isCached(far.asLong()));
    }

    @Test
    @DisplayName("Mesh cache stores generated mesh and invalidates properly on revision bump")
    void testMeshCache() {
        GranularMeshCache.clear();
        assertEquals(0, GranularMeshCache.cachedMeshCount());

        long pos = 123456L;
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.SAND);

        CellMesh mesh1 = GranularMeshCache.getOrBuild(pos, cell);
        assertEquals(1, GranularMeshCache.cachedMeshCount());

        // Cache hit (same revision)
        CellMesh mesh2 = GranularMeshCache.getOrBuild(pos, cell);
        assertSame(mesh1, mesh2, "Should return cached mesh instance on matching revision");

        // Bump revision
        cell.removeFromTop(32);
        assertTrue(cell.revision() > mesh1.revision());

        // Rebuild on revision divergence
        CellMesh mesh3 = GranularMeshCache.getOrBuild(pos, cell);
        assertNotSame(mesh1, mesh3, "Should build fresh mesh on revision mismatch");
        assertEquals(cell.revision(), mesh3.revision());
    }
}
