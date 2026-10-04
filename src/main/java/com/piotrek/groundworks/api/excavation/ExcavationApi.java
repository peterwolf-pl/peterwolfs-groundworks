package com.piotrek.groundworks.api.excavation;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The excavation API for removing material from granular terrain.
 *
 * <p>This is the single entry point for all excavation operations.
 * Shovels, excavator buckets, drills, and tunnel boring machines
 * all go through this API.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Returns exactly how many units were removed.</li>
 *   <li>The caller is responsible for storing the removed material.</li>
 *   <li>Material is never created or destroyed — only transferred.</li>
 * </ul>
 */
public final class ExcavationApi {

    private ExcavationApi() {}

    /**
     * Remove up to {@code maxUnits} from a single block position.
     *
     * <p>If the position is a vanilla convertible block, it is lazily
     * converted first.
     *
     * @param level    the server level
     * @param pos      the target block position
     * @param maxUnits maximum units to remove
     * @return the excavation result
     */
    public static ExcavationResult excavate(ServerLevel level, BlockPos pos, int maxUnits) {
        if (maxUnits <= 0) return ExcavationResult.NONE;

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getOrConvert(pos);
        if (cell == null) return ExcavationResult.NONE;

        GranularMaterial material = cell.material();
        int removed = cell.removeFromTop(maxUnits);

        if (removed > 0) {
            cell.markDirty(DirtyFlags.SYNC | DirtyFlags.MESH | DirtyFlags.SIMULATE);
            storage.enqueueDirty(pos);
            storage.setDirty(); // mark SavedData dirty

            // If cell is now empty, clean up
            if (cell.isEmpty()) {
                storage.removeCell(pos);
            }
        }

        List<BlockPos> affected = removed > 0 ? List.of(pos.immutable()) : List.of();
        return new ExcavationResult(material, removed, affected);
    }

    /**
     * Remove up to {@code maxUnits} from multiple adjacent positions.
     * Useful for large excavation shapes (bucket, blade).
     *
     * @param level     the server level
     * @param positions the target positions
     * @param maxUnits  total maximum units to remove across all positions
     * @return the combined excavation result
     */
    public static ExcavationResult excavateMulti(
            ServerLevel level, List<BlockPos> positions, int maxUnits) {

        if (maxUnits <= 0 || positions.isEmpty()) return ExcavationResult.NONE;

        GranularMaterial material = null;
        int totalRemoved = 0;
        List<BlockPos> affected = new ArrayList<>();

        for (BlockPos pos : positions) {
            if (totalRemoved >= maxUnits) break;

            int remaining = maxUnits - totalRemoved;
            ExcavationResult partial = excavate(level, pos, remaining);

            if (partial.success()) {
                if (material == null) material = partial.material();
                totalRemoved += partial.unitsRemoved();
                affected.addAll(partial.affectedCells());
            }
        }

        if (material == null) return ExcavationResult.NONE;
        return new ExcavationResult(material, totalRemoved, affected);
    }
}
