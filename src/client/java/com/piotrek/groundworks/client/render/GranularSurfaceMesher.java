package com.piotrek.groundworks.client.render;

import com.piotrek.groundworks.terrain.cell.GranularCell;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 3 Mesher: Extracts a polygonal surface mesh from a {@link GranularCell}.
 *
 * <p>Uses microvoxel occupancy to generate quadrilaterals for exposed faces.
 * Keeps meshes cached per revision.
 */
public final class GranularSurfaceMesher {

    /** A single 3D quad with vertex positions, normal, and UV coordinates. */
    public record Quad(
            Vector3f v0,
            Vector3f v1,
            Vector3f v2,
            Vector3f v3,
            Vector3f normal
    ) {}

    /** Compiled mesh data for a single cell. */
    public record CellMesh(
            int revision,
            List<Quad> quads
    ) {
        public static final CellMesh EMPTY = new CellMesh(-1, List.of());

        public boolean isEmpty() {
            return quads.isEmpty();
        }
    }

    private GranularSurfaceMesher() {}

    /**
     * Build or retrieve a mesh for the given cell.
     * Generates exposed microvoxel boundary quads.
     *
     * @param cell the granular cell
     * @return the generated mesh
     */
    public static CellMesh generateMesh(GranularCell cell) {
        if (cell.isEmpty()) return CellMesh.EMPTY;

        List<Quad> quads = new ArrayList<>();
        float step = 1.0f / GranularCell.RESOLUTION; // 0.125m per microvoxel

        for (int y = 0; y < GranularCell.RESOLUTION; y++) {
            for (int z = 0; z < GranularCell.RESOLUTION; z++) {
                for (int x = 0; x < GranularCell.RESOLUTION; x++) {
                    if (!cell.isSet(x, y, z)) continue;

                    float x0 = x * step;
                    float y0 = y * step;
                    float z0 = z * step;
                    float x1 = x0 + step;
                    float y1 = y0 + step;
                    float z1 = z0 + step;

                    // UP face (y + 1)
                    if (y == GranularCell.RESOLUTION - 1 || !cell.isSet(x, y + 1, z)) {
                        quads.add(new Quad(
                                new Vector3f(x0, y1, z0),
                                new Vector3f(x0, y1, z1),
                                new Vector3f(x1, y1, z1),
                                new Vector3f(x1, y1, z0),
                                new Vector3f(0, 1, 0)
                        ));
                    }

                    // DOWN face (y - 1)
                    if (y == 0 || !cell.isSet(x, y - 1, z)) {
                        quads.add(new Quad(
                                new Vector3f(x0, y0, z1),
                                new Vector3f(x0, y0, z0),
                                new Vector3f(x1, y0, z0),
                                new Vector3f(x1, y0, z1),
                                new Vector3f(0, -1, 0)
                        ));
                    }

                    // NORTH face (z - 1)
                    if (z == 0 || !cell.isSet(x, y, z - 1)) {
                        quads.add(new Quad(
                                new Vector3f(x1, y0, z0),
                                new Vector3f(x0, y0, z0),
                                new Vector3f(x0, y1, z0),
                                new Vector3f(x1, y1, z0),
                                new Vector3f(0, 0, -1)
                        ));
                    }

                    // SOUTH face (z + 1)
                    if (z == GranularCell.RESOLUTION - 1 || !cell.isSet(x, y, z + 1)) {
                        quads.add(new Quad(
                                new Vector3f(x0, y0, z1),
                                new Vector3f(x1, y0, z1),
                                new Vector3f(x1, y1, z1),
                                new Vector3f(x0, y1, z1),
                                new Vector3f(0, 0, 1)
                        ));
                    }

                    // WEST face (x - 1)
                    if (x == 0 || !cell.isSet(x - 1, y, z)) {
                        quads.add(new Quad(
                                new Vector3f(x0, y0, z0),
                                new Vector3f(x0, y0, z1),
                                new Vector3f(x0, y1, z1),
                                new Vector3f(x0, y1, z0),
                                new Vector3f(-1, 0, 0)
                        ));
                    }

                    // EAST face (x + 1)
                    if (x == GranularCell.RESOLUTION - 1 || !cell.isSet(x + 1, y, z)) {
                        quads.add(new Quad(
                                new Vector3f(x1, y0, z1),
                                new Vector3f(x1, y0, z0),
                                new Vector3f(x1, y1, z0),
                                new Vector3f(x1, y1, z1),
                                new Vector3f(1, 0, 0)
                        ));
                    }
                }
            }
        }

        return new CellMesh(cell.revision(), quads);
    }
}
