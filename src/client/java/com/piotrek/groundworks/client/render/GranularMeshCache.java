package com.piotrek.groundworks.client.render;

import com.piotrek.groundworks.client.render.GranularSurfaceMesher.CellMesh;
import com.piotrek.groundworks.terrain.cell.GranularCell;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Regional/per-cell cache for compiled granular meshes.
 *
 * <p>Avoids re-generating meshes when the cell revision has not changed.
 */
public final class GranularMeshCache {

    private static final Map<Long, CellMesh> CACHE = new ConcurrentHashMap<>();

    private GranularMeshCache() {}

    /**
     * Get or build mesh for a cell.
     *
     * @param packedPos the cell position as long
     * @param cell      the cell instance
     * @return the cached or newly generated mesh
     */
    public static CellMesh getOrBuild(long packedPos, GranularCell cell) {
        CellMesh cached = CACHE.get(packedPos);
        if (cached != null && cached.revision() == cell.revision()) {
            return cached;
        }

        CellMesh fresh = GranularSurfaceMesher.generateMesh(cell);
        CACHE.put(packedPos, fresh);
        return fresh;
    }

    /** Invalidate cache for a specific cell. */
    public static void invalidate(long packedPos) {
        CACHE.remove(packedPos);
    }

    /** Clear entire cache (on level disconnect/change). */
    public static void clear() {
        CACHE.clear();
    }

    public static int cachedMeshCount() {
        return CACHE.size();
    }
}
