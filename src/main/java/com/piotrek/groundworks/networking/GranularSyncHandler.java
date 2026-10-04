package com.piotrek.groundworks.networking;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Handles synchronization of granular cell state from server to clients.
 *
 * <p>Stage 1D will add proper delta encoding and revision-based sync.
 * This MVP stub logs updates for development purposes.
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>Server is always authoritative.</li>
 *   <li>Client never decides material amounts.</li>
 *   <li>Avoid sending full 512-bit state after every micro-update.</li>
 *   <li>Batch updates per tick and region.</li>
 * </ul>
 */
public final class GranularSyncHandler {

    private GranularSyncHandler() {}

    /**
     * Send a cell update to all nearby clients.
     *
     * <p>Stage 1 implementation: log only.
     * Stage 1D will replace with real packet sending.
     */
    public static void sendCellUpdate(ServerLevel level, BlockPos pos, GranularCell cell) {
        // TODO [Stage 1D]: Send actual network packet with delta encoding.
        //
        // Future packet design:
        //   - cell revision number
        //   - XOR delta of occupancy bitset vs last-known client revision
        //   - OR: changed run-length ranges
        //   - material id
        //   - unit count
        //
        // For now, log for debugging.
        GroundworksMod.LOGGER.debug(
                "[Groundworks] Sync cell at {} — material={}, units={}, rev={}",
                pos, cell.material().name(), cell.unitCount(), cell.revision());
    }

    /**
     * Send full resync of a cell (for initial load or recovery).
     */
    public static void sendFullResync(ServerLevel level, BlockPos pos, GranularCell cell) {
        // TODO [Stage 1D]: Full state packet for recovery.
        GroundworksMod.LOGGER.debug(
                "[Groundworks] Full resync at {} — units={}", pos, cell.unitCount());
    }
}
