package com.piotrek.groundworks.simulation;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Stage 2: Cellular Automata Granular Relaxation.
 *
 * <h2>Physics Rules</h2>
 * <ol>
 *   <li><b>Gravity (Vertical Fall):</b> If there is empty space directly below a cell
 *       (e.g., cell at y-1 is empty air or has room), loose material drops downward first.</li>
 *   <li><b>Slope Stability (Angle of Repose):</b> If the unit gradient between adjacent cells
 *       exceeds the material's threshold, excess material transfers down the gradient.</li>
 *   <li><b>Strict Volume Conservation:</b> Material units transferred from Cell A to Cell B
 *       are strictly equal. Integer units only.</li>
 * </ol>
 */
public final class GranularRelaxationEngine {

    /** Maximum units transferred between two cells in a single relaxation step. */
    public static final int MAX_TRANSFER_PER_STEP = 32;

    /** Horizontal neighbor directions for slope checks. */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private GranularRelaxationEngine() {}

    /**
     * Attempt to relax a single granular cell.
     *
     * @param storage the world storage
     * @param pos     the cell position
     * @param cell    the cell instance
     * @return the number of units transferred (0 if stable)
     */
    public static int relaxCell(GranularWorldStorage storage, BlockPos pos, GranularCell cell) {
        if (cell.isEmpty()) return 0;

        ServerLevel level = storage.getLevel();
        if (level == null) return 0;

        GranularMaterial material = cell.material();

        // ── Rule 1: Vertical Gravity (Fall into cell below) ──────────────
        BlockPos belowPos = pos.below();
        GranularCell belowCell = storage.getCell(belowPos);

        if (belowCell == null) {
            BlockState belowState = level.getBlockState(belowPos);
            if (belowState.isAir()) {
                belowCell = GranularCell.empty();
                belowCell.setMaterialId(cell.materialId());
                storage.putCell(belowPos, belowCell);
            }
        }

        if (belowCell != null && !belowCell.isFull()
                && (belowCell.isEmpty() || belowCell.materialId() == cell.materialId())) {

            int spaceBelow = GranularCell.TOTAL_UNITS - belowCell.unitCount();
            int toDrop = Math.min(cell.unitCount(), Math.min(spaceBelow, MAX_TRANSFER_PER_STEP));

            if (toDrop > 0) {
                int removed = cell.removeFromTop(toDrop);
                int added = belowCell.addFromBottom(removed);

                // Invariant check: dropped must equal added
                if (removed != added) {
                    throw new IllegalStateException("Material conservation violated during vertical fall!");
                }

                cell.markDirty(DirtyFlags.ALL);
                belowCell.markDirty(DirtyFlags.ALL);
                storage.enqueueDirty(pos);
                storage.enqueueDirty(belowPos);
                storage.setDirty();
                return removed;
            }
        }

        // ── Rule 2: Slope Stability (Angle of Repose / Lateral Relaxation) ──
        // Compute threshold unit difference based on angle of repose
        // 45 degrees corresponds roughly to 64 units height difference per block
        int reposeThreshold = computeReposeThreshold(material);

        // Find candidate neighbors with the steepest downward gradient
        Direction bestDir = null;
        int maxGradient = 0;
        GranularCell bestNeighbor = null;
        BlockPos bestNeighborPos = null;

        // Shuffle directions to prevent anisotropic directional bias
        List<Direction> dirs = new ArrayList<>(List.of(HORIZONTAL));
        Collections.shuffle(dirs);

        for (Direction dir : dirs) {
            BlockPos nPos = pos.relative(dir);
            GranularCell nCell = storage.getCell(nPos);

            if (nCell == null) {
                BlockState nState = level.getBlockState(nPos);
                if (nState.isAir()) {
                    // Air neighbor can receive material
                    nCell = GranularCell.empty();
                    nCell.setMaterialId(cell.materialId());
                    storage.putCell(nPos, nCell);
                } else {
                    continue; // solid block, cannot relax into it
                }
            }

            if (nCell.isFull() || (!nCell.isEmpty() && nCell.materialId() != cell.materialId())) {
                continue; // full or incompatible material
            }

            int gradient = cell.unitCount() - nCell.unitCount();
            if (gradient > reposeThreshold && gradient > maxGradient) {
                maxGradient = gradient;
                bestDir = dir;
                bestNeighbor = nCell;
                bestNeighborPos = nPos;
            }
        }

        if (bestNeighbor != null && maxGradient > reposeThreshold) {
            // Transfer an amount proportional to the excess gradient, capped at MAX_TRANSFER_PER_STEP
            int excess = (maxGradient - reposeThreshold) / 2;
            int toTransfer = Math.max(1, Math.min(excess, Math.min(MAX_TRANSFER_PER_STEP, cell.unitCount())));

            int space = GranularCell.TOTAL_UNITS - bestNeighbor.unitCount();
            toTransfer = Math.min(toTransfer, space);

            if (toTransfer > 0) {
                int removed = cell.removeFromTop(toTransfer);
                int added = bestNeighbor.addFromBottom(removed);

                if (removed != added) {
                    throw new IllegalStateException("Material conservation violated during lateral relaxation!");
                }

                cell.markDirty(DirtyFlags.ALL);
                bestNeighbor.markDirty(DirtyFlags.ALL);
                storage.enqueueDirty(pos);
                storage.enqueueDirty(bestNeighborPos);
                storage.setDirty();
                return removed;
            }
        }

        return 0; // Stable
    }

    /**
     * Compute unit difference threshold for a material before it starts sliding.
     * Sand slides easier (lower threshold), dirt has cohesion (higher threshold).
     */
    public static int computeReposeThreshold(GranularMaterial material) {
        if (material == null || material == GranularMaterial.EMPTY) return 64;

        // Base repose angle scaled to units:
        // 30 deg (sand) -> ~40 units difference
        // 35 deg (dirt) -> ~64 units difference
        // 38 deg (gravel) -> ~52 units difference
        float angle = material.angleOfRepose();
        float cohesion = material.cohesion();

        int threshold = (int) (angle * 1.5f + cohesion * 40f);
        return Math.max(16, Math.min(threshold, 256));
    }
}
