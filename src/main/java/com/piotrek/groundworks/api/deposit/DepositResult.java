package com.piotrek.groundworks.api.deposit;

import com.piotrek.groundworks.api.material.GranularMaterial;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Result of a material deposit operation.
 *
 * @param material       the material that was deposited
 * @param unitsDeposited total units deposited (integer)
 * @param unitsRejected  units that could not be placed (e.g., no room)
 * @param affectedCells  positions that were modified
 */
public record DepositResult(
        GranularMaterial material,
        int unitsDeposited,
        int unitsRejected,
        List<BlockPos> affectedCells
) {
    public static final DepositResult NONE = new DepositResult(
            GranularMaterial.EMPTY, 0, 0, List.of());

    public boolean success() {
        return unitsDeposited > 0;
    }
}
