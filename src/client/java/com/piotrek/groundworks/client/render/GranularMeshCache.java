package com.piotrek.groundworks.client.render;

import com.piotrek.groundworks.client.render.GranularSurfaceMesher.CellMesh;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Neighbor-aware per-cell cache for compiled granular meshes. */
public final class GranularMeshCache {

    private static final Map<Long, CellMesh> CACHE = new ConcurrentHashMap<>();

    private GranularMeshCache() {}

    /** Return a cached mesh until an authoritative cell event invalidates it. */
    public static CellMesh getOrBuild(long packedPos, GranularCell cell) {
        CellMesh cached = CACHE.get(packedPos);
        if (cached != null && cached.revision() == cell.revision()) {
            return cached;
        }

        BlockPos pos = BlockPos.of(packedPos);
        CellMesh fresh = GranularSurfaceMesher.generateMesh(
                pos, cell, ClientGranularStorage::getCell);
        CACHE.put(packedPos, fresh);
        return fresh;
    }

    /**
     * Invalidate every mesh that samples the changed cell. Top vertices and
     * normals sample the same-Y 3×3 region; the cell below samples this
     * position to suppress its covered top surface.
     */
    public static void invalidateNeighborhood(BlockPos changedPos) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                CACHE.remove(changedPos.offset(dx, 0, dz).asLong());
            }
        }
        CACHE.remove(changedPos.below().asLong());
    }

    static boolean isCached(long packedPos) {
        return CACHE.containsKey(packedPos);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int cachedMeshCount() {
        return CACHE.size();
    }
}
