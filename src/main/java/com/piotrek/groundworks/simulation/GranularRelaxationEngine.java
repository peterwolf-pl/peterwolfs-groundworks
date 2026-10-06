package com.piotrek.groundworks.simulation;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.conversion.BlockConverter;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Local, deterministic cellular-automata relaxation for granular terrain. */
public final class GranularRelaxationEngine {

    public static final int MAX_TRANSFER_PER_STEP = 32;
    static final int MAX_VERTICAL_TRANSFER_PER_STEP = GranularCell.TOTAL_UNITS;
    private static final int RECEIVER_SEARCH_DEPTH = 32;
    private static final float UNITS_PER_HEIGHT_STEP =
            (float) GranularCell.TOTAL_UNITS / GranularCell.RESOLUTION;

    /** N, NE, E, SE, S, SW, W, NW. Package-private for geometry tests. */
    static final int[][] HORIZONTAL_OFFSETS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    private GranularRelaxationEngine() {}

    /**
     * Relax one cell by at most one bounded, mass-conserving transfer.
     * Only the top occupied cell in a world column performs surface flow.
     */
    public static int relaxCell(GranularWorldStorage storage, BlockPos pos, GranularCell cell) {
        if (cell.isEmpty()) return 0;

        ServerLevel level = storage.getLevel();
        if (level == null) return 0;

        // Unsupported material falls toward the first sensible receiving surface.
        // This avoids creating a chain of temporary one-block cells under a high
        // bucket dump; visual stream particles remain independent of unit transfer.
        Receiver below = findVerticalReceiver(storage, level, pos, cell.materialId());
        if (below != null) {
            // Vertical compaction is structural, not surface relaxation. Fill the
            // receiving cell in one pass so material cannot remain suspended above
            // a partially filled cell and expose an internal void in a tall pile.
            int moved = transfer(
                    storage,
                    pos,
                    cell,
                    below,
                    cell.unitCount(),
                    MAX_VERTICAL_TRANSFER_PER_STEP);
            if (moved > 0) return moved;
        }

        // A buried cell is structural support; its topmost cell owns relaxation.
        GranularCell above = storage.getCell(pos.above());
        if (above != null && !above.isEmpty()) return 0;

        float sourceSurface = surfaceHeight(pos, cell.unitCount());
        float cardinalThreshold = computeReposeHeightSteps(cell);
        int start = directionStartIndex(level.getSeed(), pos, cell.revision());

        SurfaceReceiver steepest = null;
        float steepestExcess = 0.0f;

        for (int i = 0; i < HORIZONTAL_OFFSETS.length; i++) {
            int direction = (start + i) & 7;
            int[] offset = HORIZONTAL_OFFSETS[direction];
            SurfaceReceiver candidate = findSurfaceReceiver(
                    storage, level, pos, offset[0], offset[1], cell.materialId());
            if (candidate == null) continue;

            boolean diagonal = offset[0] != 0 && offset[1] != 0;
            float threshold = diagonal
                    ? cardinalThreshold * 1.41421356f
                    : cardinalThreshold;
            float excess = sourceSurface - candidate.surfaceHeight() - threshold;
            if (excess > steepestExcess) {
                steepest = candidate;
                steepestExcess = excess;
            }
        }

        if (steepest == null) return 0;

        // Moving one unit lowers the source and raises the receiver by 1/64 of
        // a micro-height step in total. This amount settles at the threshold
        // without a reverse transfer; the cap keeps formation progressive.
        int requested = Math.max(1, Math.min(MAX_TRANSFER_PER_STEP,
                (int) Math.ceil(steepestExcess * UNITS_PER_HEIGHT_STEP * 0.5f)));
        return transfer(
                storage,
                pos,
                cell,
                steepest.receiver(),
                requested,
                MAX_TRANSFER_PER_STEP);
    }

    /** Stable equalization helper retained for unit-level transfer tests. */
    static int computeLateralTransfer(int sourceUnits, int receiverUnits, int threshold) {
        int excess = sourceUnits - receiverUnits - threshold;
        if (excess <= 0) return 0;
        return Math.min(MAX_TRANSFER_PER_STEP, Math.max(1, (excess + 1) / 2));
    }

    /**
     * Compute a bounded transfer amount without mutating either cell.
     *
     * <p>Vertical compaction uses a one-cell cap (512 units), while lateral
     * angle-of-repose relaxation keeps the progressive 32-unit cap.
     */
    static int computeTransferAmount(
            int sourceUnits,
            int receiverUnits,
            int requested,
            int maxTransfer
    ) {
        if (sourceUnits <= 0 || requested <= 0 || maxTransfer <= 0) return 0;

        int capacity = Math.max(0, GranularCell.TOTAL_UNITS - receiverUnits);
        return Math.min(maxTransfer,
                Math.min(requested, Math.min(sourceUnits, capacity)));
    }

    /** Deterministic tie-break start for the eight-neighbor ring. */
    static int directionStartIndex(long worldSeed, BlockPos pos, int revision) {
        long value = worldSeed ^ pos.asLong() ^ ((long) revision * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (int) value & 7;
    }

    /** Material angle/cohesion represented as vertical eighth-block steps. */
    static float computeReposeHeightSteps(GranularMaterial material) {
        if (material == null || material == GranularMaterial.EMPTY) return 6.0f;
        double radians = Math.toRadians(material.angleOfRepose());
        return (float) (Math.tan(radians) * GranularCell.RESOLUTION
                + material.cohesion() * 2.0f);
    }

    static float computeReposeHeightSteps(GranularCell cell) {
        if (cell == null || cell.isEmpty()) return 6.0f;
        double radians = Math.toRadians(cell.effectiveAngleOfRepose());
        return (float) (Math.tan(radians) * GranularCell.RESOLUTION
                + cell.effectiveCohesion() * 2.0f);
    }

    private static float surfaceHeight(BlockPos pos, int units) {
        return pos.getY() * GranularCell.RESOLUTION + units / UNITS_PER_HEIGHT_STEP;
    }

    /**
     * Find the actual receiving surface in a neighboring world column. This
     * avoids creating intermediate floating cells while material falls from a
     * tall dump and makes repose comparisons use surface height, not block order.
     */
    private static SurfaceReceiver findSurfaceReceiver(
            GranularWorldStorage storage,
            ServerLevel level,
            BlockPos sourcePos,
            int offsetX,
            int offsetZ,
            int materialId
    ) {
        int x = sourcePos.getX() + offsetX;
        int z = sourcePos.getZ() + offsetZ;
        int minimumY = Math.max(level.getMinY(), sourcePos.getY() - RECEIVER_SEARCH_DEPTH);

        for (int y = sourcePos.getY() + 1; y >= minimumY; y--) {
            BlockPos scanPos = new BlockPos(x, y, z);
            GranularCell granular = storage.getCell(scanPos);
            if (granular != null && !granular.isEmpty()) {
                if (!granular.isFull()) {
                    return new SurfaceReceiver(
                            new Receiver(scanPos, granular, granular.unitCount()),
                            surfaceHeight(scanPos, granular.unitCount()));
                }

                BlockPos abovePos = scanPos.above();
                Receiver receiver = inspectReceiver(storage, level, abovePos, materialId);
                return receiver == null ? null : new SurfaceReceiver(
                        receiver, (scanPos.getY() + 1) * GranularCell.RESOLUTION);
            }

            BlockState state = level.getBlockState(scanPos);
            if (!state.isAir()) {
                BlockPos abovePos = scanPos.above();
                Receiver receiver = inspectReceiver(storage, level, abovePos, materialId);
                return receiver == null ? null : new SurfaceReceiver(
                        receiver, (scanPos.getY() + 1) * GranularCell.RESOLUTION);
            }
        }
        return null;
    }

    private static Receiver findVerticalReceiver(
            GranularWorldStorage storage,
            ServerLevel level,
            BlockPos sourcePos,
            int materialId
    ) {
        int minimumY = Math.max(level.getMinY(), sourcePos.getY() - RECEIVER_SEARCH_DEPTH);
        for (int y = sourcePos.getY() - 1; y >= minimumY; y--) {
            BlockPos scanPos = new BlockPos(sourcePos.getX(), y, sourcePos.getZ());
            GranularCell granular = storage.getCell(scanPos);
            if (granular != null && !granular.isEmpty()) {
                if (granular.materialId() != materialId) return null;
                if (!granular.isFull()) return new Receiver(scanPos, granular, granular.unitCount());

                BlockPos abovePos = scanPos.above();
                return abovePos.equals(sourcePos)
                        ? null
                        : inspectReceiver(storage, level, abovePos, materialId);
            }

            if (!level.getBlockState(scanPos).isAir()) {
                BlockPos abovePos = scanPos.above();
                return abovePos.equals(sourcePos)
                        ? null
                        : inspectReceiver(storage, level, abovePos, materialId);
            }
        }
        return null;
    }

    private static Receiver inspectReceiver(
            GranularWorldStorage storage,
            ServerLevel level,
            BlockPos pos,
            int materialId
    ) {
        GranularCell existing = storage.getCell(pos);
        if (existing != null) {
            if (existing.isFull()) return null;
            return new Receiver(pos, existing, existing.unitCount());
        }

        return level.getBlockState(pos).isAir() ? new Receiver(pos, null, 0) : null;
    }

    private static int transfer(
            GranularWorldStorage storage,
            BlockPos sourcePos,
            GranularCell source,
            Receiver receiver,
            int requested,
            int maxTransfer
    ) {
        if (requested <= 0 || source.isEmpty()) return 0;

        int amount = computeTransferAmount(
                source.unitCount(),
                receiver.units(),
                requested,
                maxTransfer);
        if (amount <= 0) return 0;

        GranularCell destination = receiver.cell();
        boolean createdDestination = destination == null;
        if (createdDestination) {
            destination = GranularCell.empty();
            destination.setMaterialId(source.materialId());
        }

        var movedComposition = source.extractCompositionFromTop(amount);
        var acceptedComposition = destination.addCompositionFromBottom(movedComposition);
        int removed = movedComposition.totalUnits();
        int added = acceptedComposition.totalUnits();
        if (createdDestination && added > 0) {
            storage.putCell(receiver.pos(), destination);
        }
        if (removed != added) {
            throw new IllegalStateException(
                    "Material conservation violated: removed=" + removed + ", added=" + added);
        }

        source.markDirty(DirtyFlags.ALL);
        destination.markDirty(DirtyFlags.ALL);
        storage.enqueueDirty(sourcePos);
        storage.enqueueDirty(receiver.pos());

        if (storage.getLevel() != null && destination.isFull()
                && BlockConverter.canSolidify(storage.getLevel(), storage, receiver.pos(), destination)) {
            BlockConverter.solidify(storage.getLevel(), storage, receiver.pos(), destination);
        }

        // When the top cell drains, explicitly wake the newly exposed cell.
        // Queueing a clean cell alone is insufficient because storage ticks only
        // invoke relaxation for cells carrying SIMULATE.
        GranularCell newlyExposed = storage.getCell(sourcePos.below());
        if (newlyExposed != null && !newlyExposed.isEmpty()) {
            newlyExposed.markDirty(DirtyFlags.SIMULATE);
            storage.enqueueDirty(sourcePos.below());
        }

        storage.setDirty();
        return removed;
    }

    /**
     * Legacy volume threshold retained for API/tests. New surface flow uses
     * {@link #computeReposeHeightSteps(GranularMaterial)} directly.
     */
    public static int computeReposeThreshold(GranularMaterial material) {
        if (material == null || material == GranularMaterial.EMPTY) return 64;
        int threshold = (int) (material.angleOfRepose() * 1.5f
                + material.cohesion() * 40f);
        return Math.max(16, Math.min(threshold, 256));
    }

    private record Receiver(BlockPos pos, GranularCell cell, int units) {}
    private record SurfaceReceiver(Receiver receiver, float surfaceHeight) {}
}
