package com.piotrek.groundworks.api.deposit;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.conversion.BlockConverter;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The deposit API for adding material to granular terrain.
 *
 * <p>This is the single entry point for all deposit operations.
 * The terrain engine determines where material physically fits.
 * Containers and machines must not modify internal occupancy arrays directly.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Returns how many units were deposited and how many were rejected.</li>
 *   <li>The caller must handle rejected units (keep them in container).</li>
 *   <li>Material is never created or destroyed.</li>
 * </ul>
 */
public final class DepositApi {

    private DepositApi() {}

    /**
     * Deposit material at a single position.
     *
     * <p>If the position is air or an empty granular cell, material fills from bottom.
     * If the position is a convertible vanilla block that is already full, no units
     * are deposited. If partial, remaining capacity is filled.
     *
     * @param level    the server level
     * @param pos      the target position
     * @param material the material to deposit
     * @param units    the number of units to deposit
     * @return the deposit result
     */
    public static DepositResult deposit(
            ServerLevel level, BlockPos pos, GranularMaterial material, int units) {

        if (units <= 0) return DepositResult.NONE;

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getCell(pos);

        if (cell == null) {
            // Check if position is air — we can create a new cell there
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                cell = GranularCell.empty();
                cell.setMaterialId(material.id());
                int added = cell.addFromBottom(units);
                if (added > 0) {
                    storage.putCell(pos, cell);
                    cell.markDirty(DirtyFlags.ALL);
                    storage.enqueueDirty(pos);
                    storage.setDirty();
                    return new DepositResult(material, added, units - added,
                            List.of(pos.immutable()));
                }
                return new DepositResult(material, 0, units, List.of());
            }

            // Try converting existing block (e.g., adding dirt on top of dirt)
            if (BlockConverter.isConvertible(state)) {
                cell = storage.getOrConvert(pos);
                if (cell == null) return new DepositResult(material, 0, units, List.of());
            } else {
                // Solid non-convertible block — can't deposit here
                return new DepositResult(material, 0, units, List.of());
            }
        }

        // Check material compatibility
        if (!cell.isEmpty() && cell.materialId() != material.id()) {
            // Different material — don't mix in MVP
            return new DepositResult(material, 0, units, List.of());
        }

        if (cell.isEmpty()) {
            cell.setMaterialId(material.id());
        }

        int added = cell.addFromBottom(units);
        if (added > 0) {
            cell.markDirty(DirtyFlags.ALL);
            storage.enqueueDirty(pos);
            storage.setDirty();
        }

        return new DepositResult(material, added, units - added,
                added > 0 ? List.of(pos.immutable()) : List.of());
    }

    /**
     * Deposit material starting at {@code origin} and spilling upward if needed.
     * Tries the origin first, then the block above, etc.
     *
     * @param level    the server level
     * @param origin   the starting position
     * @param material the material to deposit
     * @param units    the number of units to deposit
     * @return the combined deposit result
     */
    public static DepositResult depositWithOverflow(
            ServerLevel level, BlockPos origin, GranularMaterial material, int units) {

        if (units <= 0) return DepositResult.NONE;

        int remaining = units;
        int totalDeposited = 0;
        List<BlockPos> affected = new ArrayList<>();
        BlockPos current = origin;

        for (int attempts = 0; attempts < 8 && remaining > 0; attempts++) {
            DepositResult partial = deposit(level, current, material, remaining);
            totalDeposited += partial.unitsDeposited();
            remaining -= partial.unitsDeposited();
            affected.addAll(partial.affectedCells());

            if (remaining > 0) {
                current = current.above();
            }
        }

        return new DepositResult(material, totalDeposited, remaining, affected);
    }
}
