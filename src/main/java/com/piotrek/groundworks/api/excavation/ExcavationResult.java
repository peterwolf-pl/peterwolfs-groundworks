package com.piotrek.groundworks.api.excavation;

import com.piotrek.groundworks.api.material.GranularMaterial;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Result of an excavation operation.
 *
 * @param material      the material that was removed
 * @param unitsRemoved  total units removed (integer, never fractional)
 * @param affectedCells positions of cells that were modified
 */
public record ExcavationResult(
        GranularMaterial material,
        int unitsRemoved,
        List<BlockPos> affectedCells
) {
    public static final ExcavationResult NONE = new ExcavationResult(
            GranularMaterial.EMPTY, 0, List.of());

    public boolean success() {
        return unitsRemoved > 0;
    }
}
