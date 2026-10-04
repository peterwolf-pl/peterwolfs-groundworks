package com.piotrek.groundworks.client.render;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.client.render.GranularSurfaceMesher.CellMesh;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("Single microvoxel produces exactly 6 boundary quads (one per face)")
    void testSingleMicrovoxelMesh() {
        GranularCell cell = GranularCell.empty();
        cell.setMaterialId(GranularMaterialRegistry.DIRT.id());
        cell.set(3, 3, 3); // isolated center microvoxel

        CellMesh mesh = GranularSurfaceMesher.generateMesh(cell);

        assertFalse(mesh.isEmpty());
        assertEquals(6, mesh.quads().size(), "An isolated voxel must have exactly 6 exposed faces");
    }

    @Test
    @DisplayName("Full cell produces exactly 384 boundary quads (64 per outer face, 0 internal quads)")
    void testFullCellMeshBoundaryOnly() {
        GranularCell cell = GranularCell.full(GranularMaterialRegistry.DIRT);
        CellMesh mesh = GranularSurfaceMesher.generateMesh(cell);

        // A full 8x8x8 cube has 6 outer faces, each containing 8x8 = 64 boundary voxels.
        // Total external quads = 6 * 64 = 384. All internal faces are culled.
        assertEquals(384, mesh.quads().size(), "Full cell should cull internal faces and emit 384 quads");
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
