package com.piotrek.groundworks.client.render;

import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a smooth heightfield surface from authoritative microvoxel occupancy.
 *
 * <p>The 8×8 occupancy columns remain the source of truth. Their top heights
 * are averaged at shared grid corners, which hides voxel steps while retaining
 * excavation detail. Neighbor sampling uses world-space columns, so both sides
 * of a block boundary calculate identical edge vertices and normals.</p>
 */
public final class GranularSurfaceMesher {

    @FunctionalInterface
    public interface CellLookup {
        GranularCell get(BlockPos pos);
    }

    /** A quad with per-vertex positions and smooth normals. */
    public record Quad(
            Vector3f v0,
            Vector3f v1,
            Vector3f v2,
            Vector3f v3,
            Vector3f n0,
            Vector3f n1,
            Vector3f n2,
            Vector3f n3
    ) {}

    /** Compiled mesh data for one cell and its sampled neighborhood. */
    public record CellMesh(
            int revision,
            List<Quad> quads
    ) {
        public static final CellMesh EMPTY = new CellMesh(-1, List.of());

        public boolean isEmpty() {
            return quads.isEmpty();
        }
    }

    private static final float STEP = 1.0f / GranularCell.RESOLUTION;

    private GranularSurfaceMesher() {}

    /** Generate an isolated-cell mesh, primarily for unit tests. */
    public static CellMesh generateMesh(GranularCell cell) {
        BlockPos origin = BlockPos.ZERO;
        return generateMesh(origin, cell,
                samplePos -> samplePos.equals(origin) ? cell : null);
    }

    /** Generate a neighbor-aware smooth surface mesh. */
    public static CellMesh generateMesh(
            BlockPos pos,
            GranularCell cell,
            CellLookup lookup
    ) {
        if (cell.isEmpty()) return CellMesh.EMPTY;

        List<Quad> quads = new ArrayList<>(96);

        for (int z = 0; z < GranularCell.RESOLUTION; z++) {
            for (int x = 0; x < GranularCell.RESOLUTION; x++) {
                int columnHeight = cell.getColumnHeight(x, z);
                if (columnHeight < 0 || isCoveredFromAbove(pos, x, z, lookup)) continue;

                float x0 = x * STEP;
                float x1 = (x + 1) * STEP;
                float z0 = z * STEP;
                float z1 = (z + 1) * STEP;

                float h00 = vertexHeight(pos, x, z, lookup);
                float h01 = vertexHeight(pos, x, z + 1, lookup);
                float h11 = vertexHeight(pos, x + 1, z + 1, lookup);
                float h10 = vertexHeight(pos, x + 1, z, lookup);

                quads.add(new Quad(
                        new Vector3f(x0, h00, z0),
                        new Vector3f(x0, h01, z1),
                        new Vector3f(x1, h11, z1),
                        new Vector3f(x1, h10, z0),
                        surfaceNormal(pos, x, z, lookup),
                        surfaceNormal(pos, x, z + 1, lookup),
                        surfaceNormal(pos, x + 1, z + 1, lookup),
                        surfaceNormal(pos, x + 1, z, lookup)
                ));

                if (sampleColumnHeight(pos, x, z - 1, lookup) <= 0.0f) {
                    quads.add(sideQuad(x1, h10, x0, h00, z0, 0, 0, -1));
                }
                if (sampleColumnHeight(pos, x, z + 1, lookup) <= 0.0f) {
                    quads.add(sideQuad(x0, h01, x1, h11, z1, 0, 0, 1));
                }
                if (sampleColumnHeight(pos, x - 1, z, lookup) <= 0.0f) {
                    quads.add(sideQuadZ(z0, h00, z1, h01, x0, -1, 0, 0));
                }
                if (sampleColumnHeight(pos, x + 1, z, lookup) <= 0.0f) {
                    quads.add(sideQuadZ(z1, h11, z0, h10, x1, 1, 0, 0));
                }
            }
        }

        return new CellMesh(cell.revision(), List.copyOf(quads));
    }

    private static Quad sideQuad(
            float x0, float h0, float x1, float h1, float z,
            float nx, float ny, float nz
    ) {
        Vector3f normal = new Vector3f(nx, ny, nz);
        return new Quad(
                new Vector3f(x0, 0, z),
                new Vector3f(x1, 0, z),
                new Vector3f(x1, h1, z),
                new Vector3f(x0, h0, z),
                normal, new Vector3f(normal), new Vector3f(normal), new Vector3f(normal)
        );
    }

    private static Quad sideQuadZ(
            float z0, float h0, float z1, float h1, float x,
            float nx, float ny, float nz
    ) {
        Vector3f normal = new Vector3f(nx, ny, nz);
        return new Quad(
                new Vector3f(x, 0, z0),
                new Vector3f(x, 0, z1),
                new Vector3f(x, h1, z1),
                new Vector3f(x, h0, z0),
                normal, new Vector3f(normal), new Vector3f(normal), new Vector3f(normal)
        );
    }

    private static boolean isCoveredFromAbove(
            BlockPos pos,
            int localX,
            int localZ,
            CellLookup lookup
    ) {
        GranularCell above = lookup.get(pos.above());
        return above != null && above.getColumnHeight(localX, localZ) >= 0;
    }

    /** Height at a shared 8×8 grid vertex, averaged from four surrounding columns. */
    private static float vertexHeight(BlockPos pos, int vertexX, int vertexZ, CellLookup lookup) {
        return (sampleColumnHeight(pos, vertexX - 1, vertexZ - 1, lookup)
                + sampleColumnHeight(pos, vertexX, vertexZ - 1, lookup)
                + sampleColumnHeight(pos, vertexX - 1, vertexZ, lookup)
                + sampleColumnHeight(pos, vertexX, vertexZ, lookup)) * 0.25f;
    }

    private static Vector3f surfaceNormal(BlockPos pos, int vertexX, int vertexZ, CellLookup lookup) {
        float left = vertexHeight(pos, vertexX - 1, vertexZ, lookup);
        float right = vertexHeight(pos, vertexX + 1, vertexZ, lookup);
        float north = vertexHeight(pos, vertexX, vertexZ - 1, lookup);
        float south = vertexHeight(pos, vertexX, vertexZ + 1, lookup);
        return new Vector3f(left - right, 2.0f * STEP, north - south).normalize();
    }

    /** Sample one micro-column, crossing block borders with floor division. */
    private static float sampleColumnHeight(
            BlockPos origin,
            int localX,
            int localZ,
            CellLookup lookup
    ) {
        int blockOffsetX = Math.floorDiv(localX, GranularCell.RESOLUTION);
        int blockOffsetZ = Math.floorDiv(localZ, GranularCell.RESOLUTION);
        int x = Math.floorMod(localX, GranularCell.RESOLUTION);
        int z = Math.floorMod(localZ, GranularCell.RESOLUTION);

        GranularCell sampled = lookup.get(origin.offset(blockOffsetX, 0, blockOffsetZ));
        if (sampled == null) return 0.0f;

        int top = sampled.getColumnHeight(x, z);
        return top < 0 ? 0.0f : (top + 1) * STEP;
    }
}
