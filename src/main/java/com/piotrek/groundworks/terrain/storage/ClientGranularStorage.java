package com.piotrek.groundworks.terrain.storage;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.networking.GranularCellSyncPayload;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of granular cells for rendering and collision prediction.
 *
 * <p>Receives authoritative updates from the server via {@link GranularCellSyncPayload}.
 * The client never modifies authoritative terrain units on its own.
 */
public final class ClientGranularStorage {

    private static final Map<Long, GranularCell> CELLS = new ConcurrentHashMap<>();

    private ClientGranularStorage() {}

    /** Apply an authoritative update payload received from the server. */
    public static void handlePayload(GranularCellSyncPayload payload) {
        BlockPos pos = payload.pos();
        long key = pos.asLong();

        if (payload.unitCount() <= 0) {
            // Removal
            CELLS.remove(key);
            return;
        }

        GranularCell cell = CELLS.get(key);

        if (cell == null || !payload.isDelta()) {
            // Full sync or new cell
            cell = new GranularCell();
            cell.setMaterialId(payload.materialId());
            if (payload.words().length == GranularCell.LONGS) {
                System.arraycopy(payload.words(), 0, cell.occupancy(), 0, GranularCell.LONGS);
            }
            cell.invalidateAllColumns();
            cell.markDirty(DirtyFlags.MESH);
            CELLS.put(key, cell);
        } else {
            // Delta update
            cell.setMaterialId(payload.materialId());
            long[] occ = cell.occupancy();
            int mask = payload.changedWordMask();
            int wordIdx = 0;

            for (int i = 0; i < GranularCell.LONGS; i++) {
                if ((mask & (1 << i)) != 0 && wordIdx < payload.words().length) {
                    occ[i] = payload.words()[wordIdx++];
                }
            }
            cell.invalidateAllColumns();
            cell.markDirty(DirtyFlags.MESH);
        }

        GroundworksMod.LOGGER.debug(
                "[Groundworks-Client] Updated cell at {} (units={}, rev={})",
                pos, cell.unitCount(), payload.revision());
    }

    /** Query cell on client. */
    public static GranularCell getCell(BlockPos pos) {
        return CELLS.get(pos.asLong());
    }

    /** Clear client cache (e.g. on disconnect/dimension change). */
    public static void clear() {
        CELLS.clear();
    }

    public static Map<Long, GranularCell> allCells() {
        return Collections.unmodifiableMap(CELLS);
    }
}
