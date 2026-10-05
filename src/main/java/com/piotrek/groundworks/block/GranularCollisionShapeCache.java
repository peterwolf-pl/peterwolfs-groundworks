package com.piotrek.groundworks.block;

import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.WeakHashMap;

/** Revision-keyed collision shapes for granular cells. */
final class GranularCollisionShapeCache {

    private static final Map<GranularCell, Entry> CACHE = new WeakHashMap<>();

    private GranularCollisionShapeCache() {}

    static VoxelShape get(GranularCell cell) {
        synchronized (CACHE) {
            Entry cached = CACHE.get(cell);
            if (cached != null && cached.revision() == cell.revision()) {
                return cached.shape();
            }
        }

        int revision = cell.revision();
        VoxelShape shape = build(cell);
        synchronized (CACHE) {
            CACHE.put(cell, new Entry(revision, shape));
        }
        return shape;
    }

    private static VoxelShape build(GranularCell cell) {
        if (cell.isEmpty()) return Shapes.empty();

        VoxelShape combined = Shapes.empty();
        double step = 1.0 / GranularCell.RESOLUTION;
        for (int z = 0; z < GranularCell.RESOLUTION; z++) {
            for (int x = 0; x < GranularCell.RESOLUTION; x++) {
                int height = cell.getColumnHeight(x, z);
                if (height < 0) continue;

                double minX = x * step;
                double minZ = z * step;
                VoxelShape column = Block.box(
                        minX * 16.0, 0.0, minZ * 16.0,
                        (minX + step) * 16.0, (height + 1) * step * 16.0,
                        (minZ + step) * 16.0
                );
                combined = Shapes.or(combined, column);
            }
        }
        return combined.isEmpty() ? Shapes.block() : combined;
    }

    static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private record Entry(int revision, VoxelShape shape) {}
}
