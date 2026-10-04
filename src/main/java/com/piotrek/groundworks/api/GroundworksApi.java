package com.piotrek.groundworks.api;

import com.piotrek.groundworks.api.deposit.DepositApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationApi;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Public API for Peterwolf's Groundworks.
 *
 * <p>Future mods (Excavators, Conveyors, Mining, Dump Trucks) call this API.
 * They never need to know internal terrain storage details.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   ExcavationResult result = GroundworksApi.excavate(level, pos, 32);
 *   if (result.success()) {
 *       bucket.addMaterial(result.material(), result.unitsRemoved());
 *   }
 *
 *   DepositResult deposit = GroundworksApi.deposit(level, pos, material, units);
 *   int leftover = deposit.unitsRejected();
 * }</pre>
 */
public final class GroundworksApi {

    private GroundworksApi() {}

    // ── Excavation ───────────────────────────────────────────────────

    /**
     * Remove up to {@code maxUnits} from a single position.
     */
    public static ExcavationResult excavate(ServerLevel level, BlockPos pos, int maxUnits) {
        return ExcavationApi.excavate(level, pos, maxUnits);
    }

    /**
     * Remove material from multiple positions.
     */
    public static ExcavationResult excavateMulti(
            ServerLevel level, List<BlockPos> positions, int maxUnits) {
        return ExcavationApi.excavateMulti(level, positions, maxUnits);
    }

    // ── Deposition ───────────────────────────────────────────────────

    /**
     * Deposit material at a position.
     */
    public static DepositResult deposit(
            ServerLevel level, BlockPos pos, GranularMaterial material, int units) {
        return DepositApi.deposit(level, pos, material, units);
    }

    /**
     * Deposit with upward overflow.
     */
    public static DepositResult depositWithOverflow(
            ServerLevel level, BlockPos origin, GranularMaterial material, int units) {
        return DepositApi.depositWithOverflow(level, origin, material, units);
    }

    // ── Queries ──────────────────────────────────────────────────────

    /**
     * Query the material at a position.
     *
     * @return the granular cell, or null if not converted
     */
    @Nullable
    public static GranularCell queryCell(ServerLevel level, BlockPos pos) {
        return GranularWorldStorage.get(level).getCell(pos);
    }

    /**
     * Get the highest occupied microvoxel Y at (x,z) within a granular cell.
     *
     * @return 0..7 if occupied, -1 if empty or not a granular cell
     */
    public static int getSurfaceHeight(ServerLevel level, BlockPos pos, int localX, int localZ) {
        GranularCell cell = GranularWorldStorage.get(level).getCell(pos);
        if (cell == null) return -1;
        return cell.getColumnHeight(localX, localZ);
    }
}
