package com.piotrek.groundworks.terrain.cell;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.material.GranularComposition;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

/**
 * A single granular cell occupying one vanilla block position.
 *
 * <h2>Storage model</h2>
 * <p>Internal resolution: 8x8x8 = 512 microvoxels. Occupancy answers where
 * material exists. {@link GranularComposition} independently tracks what the
 * occupied volume contains, allowing exact dirt/sand/gravel/cobblestone mixes
 * without storing a material id for every microvoxel.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@code unitCount == bitCount(occupancy)}</li>
 *   <li>{@code unitCount == composition.totalUnits()}</li>
 *   <li>Material must never be created or destroyed by cell operations.</li>
 * </ul>
 */
public final class GranularCell {

    public static final int RESOLUTION = 8;
    public static final int TOTAL_UNITS = RESOLUTION * RESOLUTION * RESOLUTION;
    public static final int LONGS = TOTAL_UNITS / Long.SIZE;

    /** Version 2 adds exact per-material composition counts. */
    public static final int DATA_VERSION = 2;

    /**
     * Compatibility material id. For a mixture this is always the dominant
     * material. Existing machinery can continue using materialId()/material().
     */
    private int materialId;
    private final GranularComposition composition = new GranularComposition();
    private final long[] occupancy = new long[LONGS];
    private int unitCount;
    private int revision;
    private int dirtyFlags;

    private final byte[] columnHeights = new byte[RESOLUTION * RESOLUTION];

    public GranularCell() {
        Arrays.fill(columnHeights, (byte) -1);
    }

    public static GranularCell full(GranularMaterial material) {
        GranularCell cell = new GranularCell();
        cell.materialId = material.id();
        cell.composition.add(material, TOTAL_UNITS);
        Arrays.fill(cell.occupancy, -1L);
        cell.unitCount = TOTAL_UNITS;
        cell.revision = 1;
        cell.dirtyFlags = DirtyFlags.ALL;
        return cell;
    }

    public static GranularCell empty() {
        return new GranularCell();
    }

    public static int index(int x, int y, int z) {
        return x + RESOLUTION * (z + RESOLUTION * y);
    }

    public static int indexX(int index) { return index & 7; }
    public static int indexZ(int index) { return (index >> 3) & 7; }
    public static int indexY(int index) { return (index >> 6) & 7; }

    public boolean isSet(int x, int y, int z) {
        int idx = index(x, y, z);
        return (occupancy[idx >> 6] & (1L << (idx & 63))) != 0;
    }

    /**
     * Set one occupied microvoxel using the current compatibility material.
     * Call setMaterialId() first when constructing a cell through this low-level API.
     */
    public boolean set(int x, int y, int z) {
        if (materialId <= 0) return false;

        int idx = index(x, y, z);
        int word = idx >> 6;
        long bit = 1L << (idx & 63);
        if ((occupancy[word] & bit) != 0) return false;

        occupancy[word] |= bit;
        unitCount++;
        composition.add(materialId, 1);
        refreshDominantMaterial();
        invalidateColumn(x, z);
        markDirty(DirtyFlags.OCCUPANCY | DirtyFlags.MATERIAL);
        return true;
    }

    /**
     * Clear one microvoxel. Composition is consumed from the dominant material,
     * which preserves the single-material contract of legacy excavation calls.
     */
    public boolean clear(int x, int y, int z) {
        int idx = index(x, y, z);
        int word = idx >> 6;
        long bit = 1L << (idx & 63);
        if ((occupancy[word] & bit) == 0) return false;

        int removedMaterial = materialId > 0 ? materialId : composition.dominantMaterialId();
        if (removedMaterial <= 0 || composition.remove(removedMaterial, 1) != 1) {
            return false;
        }

        occupancy[word] &= ~bit;
        unitCount--;
        refreshDominantMaterial();
        invalidateColumn(x, z);
        markDirty(DirtyFlags.OCCUPANCY | DirtyFlags.MATERIAL);
        return true;
    }

    /**
     * Legacy single-material extraction. For mixtures this removes only the
     * current dominant material so callers which pair material() with the
     * returned count do not silently transmute other components.
     */
    public int removeFromTop(int maxUnits) {
        int selectedMaterial = materialId > 0 ? materialId : composition.dominantMaterialId();
        return removeMaterialFromTop(selectedMaterial, maxUnits);
    }

    public int removeMaterialFromTop(int selectedMaterialId, int maxUnits) {
        if (selectedMaterialId <= 0 || maxUnits <= 0 || unitCount == 0) return 0;

        int available = composition.unitsOf(selectedMaterialId);
        int target = Math.min(Math.min(maxUnits, unitCount), available);
        if (target <= 0) return 0;

        int removed = removeOccupancyFromTop(target);
        int compositionRemoved = composition.remove(selectedMaterialId, removed);
        if (compositionRemoved != removed) {
            throw new IllegalStateException("Composition conservation violated while excavating");
        }
        refreshDominantMaterial();
        dirtyFlags |= DirtyFlags.MATERIAL;
        return removed;
    }

    /**
     * Extract a proportional sample for internal granular flow. This is the
     * path used when a mixed cell relaxes into another cell.
     */
    public GranularComposition extractCompositionFromTop(int maxUnits) {
        if (maxUnits <= 0 || unitCount == 0) return new GranularComposition();

        int target = Math.min(maxUnits, unitCount);
        GranularComposition extracted = composition.extractProportional(target);
        int removed = removeOccupancyFromTop(extracted.totalUnits());
        if (removed != extracted.totalUnits()) {
            throw new IllegalStateException("Occupancy/composition extraction mismatch");
        }
        refreshDominantMaterial();
        dirtyFlags |= DirtyFlags.MATERIAL;
        return extracted;
    }

    public int addFromBottom(int maxUnits) {
        if (materialId <= 0) {
            materialId = defaultMaterialId();
        }
        return addMaterialFromBottom(GranularMaterialRegistry.byId(materialId), maxUnits);
    }

    public int addMaterialFromBottom(GranularMaterial material, int maxUnits) {
        if (material == null || material.id() <= 0 || maxUnits <= 0) return 0;
        int added = addOccupancyFromBottom(maxUnits);
        if (added > 0) {
            composition.add(material, added);
            refreshDominantMaterial();
            dirtyFlags |= DirtyFlags.MATERIAL;
        }
        return added;
    }

    /**
     * Add an exact mixture, proportionally clipping it when the cell has less
     * free volume than the incoming composition.
     */
    public GranularComposition addCompositionFromBottom(GranularComposition incoming) {
        if (incoming == null || incoming.isEmpty() || unitCount >= TOTAL_UNITS) {
            return new GranularComposition();
        }

        int capacity = TOTAL_UNITS - unitCount;
        GranularComposition accepted = incoming.copy();
        if (accepted.totalUnits() > capacity) {
            accepted = accepted.extractProportional(capacity);
        }

        int added = addOccupancyFromBottom(accepted.totalUnits());
        if (added != accepted.totalUnits()) {
            throw new IllegalStateException("Occupancy/composition deposit mismatch");
        }

        composition.addAll(accepted);
        refreshDominantMaterial();
        dirtyFlags |= DirtyFlags.MATERIAL;
        return accepted;
    }

    private int removeOccupancyFromTop(int target) {
        if (target <= 0 || unitCount == 0) return 0;

        int boundedTarget = Math.min(target, unitCount);
        int removed = 0;
        for (int y = RESOLUTION - 1; y >= 0 && removed < boundedTarget; y--) {
            long occupied = occupancy[y];
            long selected = takeLowestBits(occupied, boundedTarget - removed);
            if (selected == 0L) continue;
            occupancy[y] = occupied & ~selected;
            removed += Long.bitCount(selected);
        }

        if (removed > 0) {
            unitCount -= removed;
            invalidateAllColumns();
            markBulkDirty(DirtyFlags.OCCUPANCY, removed);
        }
        return removed;
    }

    private int addOccupancyFromBottom(int maxUnits) {
        if (maxUnits <= 0 || unitCount >= TOTAL_UNITS) return 0;

        int target = Math.min(maxUnits, TOTAL_UNITS - unitCount);
        int added = 0;
        for (int y = 0; y < RESOLUTION && added < target; y++) {
            long occupied = occupancy[y];
            long selected = takeLowestBits(~occupied, target - added);
            if (selected == 0L) continue;
            occupancy[y] = occupied | selected;
            added += Long.bitCount(selected);
        }

        if (added > 0) {
            unitCount += added;
            invalidateAllColumns();
            markBulkDirty(DirtyFlags.OCCUPANCY, added);
        }
        return added;
    }

    private static long takeLowestBits(long candidates, int limit) {
        if (limit <= 0 || candidates == 0L) return 0L;
        if (Long.bitCount(candidates) <= limit) return candidates;

        int start = Long.numberOfTrailingZeros(candidates);
        if (limit < Long.SIZE && start <= Long.SIZE - limit) {
            long consecutive = ((1L << limit) - 1L) << start;
            if ((candidates & consecutive) == consecutive) return consecutive;
        }

        long selected = 0L;
        for (int selectedCount = 0; selectedCount < limit; selectedCount++) {
            long bit = candidates & -candidates;
            selected |= bit;
            candidates ^= bit;
        }
        return selected;
    }

    private void markBulkDirty(int flag, int mutationCount) {
        dirtyFlags |= flag;
        revision += mutationCount;
    }

    public int recount() {
        int count = 0;
        for (long word : occupancy) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /**
     * Recompute count after a bulk occupancy copy. If no composition has been
     * supplied yet, reconstruct a pure composition from the compatibility id.
     */
    public int refreshUnitCount() {
        unitCount = recount();
        if (unitCount == 0) {
            composition.clear();
            materialId = 0;
        } else if (composition.totalUnits() != unitCount) {
            composition.clear();
            int fallbackId = materialId > 0 ? materialId : defaultMaterialId();
            if (fallbackId > 0) {
                composition.add(fallbackId, unitCount);
            }
        }
        refreshDominantMaterial();
        invalidateAllColumns();
        return unitCount;
    }

    public boolean validate() {
        return unitCount == recount() && unitCount == composition.totalUnits();
    }

    public int getColumnHeight(int x, int z) {
        int ci = x + RESOLUTION * z;
        if (columnHeights[ci] == -1) {
            columnHeights[ci] = (byte) computeColumnHeight(x, z);
        }
        return columnHeights[ci];
    }

    private int computeColumnHeight(int x, int z) {
        for (int y = RESOLUTION - 1; y >= 0; y--) {
            if (isSet(x, y, z)) return y;
        }
        return -1;
    }

    private void invalidateColumn(int x, int z) {
        columnHeights[x + RESOLUTION * z] = -1;
    }

    public void invalidateAllColumns() {
        Arrays.fill(columnHeights, (byte) -1);
    }

    public void markDirty(int flag) {
        dirtyFlags |= flag;
        revision++;
    }

    public boolean isDirty(int flag) {
        return (dirtyFlags & flag) != 0;
    }

    public boolean isDirty() {
        return dirtyFlags != 0;
    }

    public void clearDirtyFlags() {
        dirtyFlags = 0;
    }

    public void clearDirtyFlag(int flag) {
        dirtyFlags &= ~flag;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putString("material", material().name());
        tag.putLongArray("occupancy", occupancy.clone());
        tag.putInt("unitCount", unitCount);
        tag.putInt("revision", revision);

        int[] counts = composition.toArray();
        for (int id = 1; id < counts.length; id++) {
            if (counts[id] > 0) {
                tag.putInt("materialUnits_" + id, counts[id]);
            }
        }
        return tag;
    }

    public static GranularCell load(CompoundTag tag) {
        if (!tag.contains("dataVersion")) return null;
        int version = tag.getIntOr("dataVersion", 0);
        if (version < 1 || version > DATA_VERSION) return null;

        String matName = tag.getStringOr("material", "");
        GranularMaterial legacyMaterial = GranularMaterialRegistry.byName(matName);
        if (legacyMaterial == null) legacyMaterial = GranularMaterial.EMPTY;

        var optOcc = tag.getLongArray("occupancy");
        if (optOcc.isEmpty() || optOcc.get().length != LONGS) return null;

        GranularCell cell = new GranularCell();
        System.arraycopy(optOcc.get(), 0, cell.occupancy, 0, LONGS);
        cell.unitCount = cell.recount();

        int storedCount = tag.getIntOr("unitCount", 0);
        if (cell.unitCount != storedCount) {
            GroundworksMod.LOGGER.warn(
                    "[Groundworks] Unit count mismatch on load: stored={}, actual={}. Using actual.",
                    storedCount, cell.unitCount);
        }

        if (version >= 2) {
            int[] counts = new int[Math.max(1, GranularMaterialRegistry.count())];
            for (int id = 1; id < counts.length; id++) {
                counts[id] = Math.max(0, tag.getIntOr("materialUnits_" + id, 0));
            }
            cell.composition.replaceWith(counts);
        }

        if (cell.composition.totalUnits() != cell.unitCount) {
            if (version >= 2 && cell.unitCount > 0) {
                GroundworksMod.LOGGER.warn(
                        "[Groundworks] Composition mismatch on load: composition={}, occupancy={}. "
                                + "Falling back to legacy material {}.",
                        cell.composition.totalUnits(), cell.unitCount, legacyMaterial.name());
            }
            cell.composition.clear();
            if (legacyMaterial.id() > 0 && cell.unitCount > 0) {
                cell.composition.add(legacyMaterial, cell.unitCount);
            }
        }

        cell.refreshDominantMaterial();
        cell.revision = tag.getIntOr("revision", 0);
        cell.invalidateAllColumns();
        return cell;
    }

    public int materialId() { return materialId; }
    public GranularMaterial material() { return GranularMaterialRegistry.byId(materialId); }
    public int unitCount() { return unitCount; }
    public int revision() { return revision; }
    public int dirtyFlags() { return dirtyFlags; }
    public long[] occupancy() { return occupancy; }
    public boolean isEmpty() { return unitCount == 0; }
    public boolean isFull() { return unitCount == TOTAL_UNITS; }

    public GranularComposition composition() {
        return composition.copy();
    }

    public int[] compositionUnits() {
        return composition.toArray();
    }

    public int unitsOfMaterial(int id) {
        return composition.unitsOf(id);
    }

    public boolean isPureMaterial() {
        return composition.isPure();
    }

    public int pureMaterialId() {
        return composition.pureMaterialId();
    }

    public float effectiveAngleOfRepose() {
        return composition.weightedAngleOfRepose();
    }

    public float effectiveCohesion() {
        return composition.weightedCohesion();
    }

    public float effectiveDensity() {
        return composition.weightedDensity();
    }

    public float effectiveSlideProbability() {
        return composition.weightedSlideProbability();
    }

    public int visualMaterialId(long seed, int microX, int microY, int microZ) {
        return composition.sampleVisualMaterial(seed, microX, microY, microZ);
    }

    /**
     * Compatibility setter. On a populated cell this intentionally converts the
     * composition to a pure material. Network code should follow it with
     * setCompositionUnits() when reconstructing a mixed cell.
     */
    public void setMaterialId(int id) {
        this.materialId = id;
        if (unitCount > 0) {
            composition.clear();
            if (id > 0) composition.add(id, unitCount);
        }
        markDirty(DirtyFlags.MATERIAL);
    }

    public void setCompositionUnits(int[] counts) {
        GranularComposition replacement = new GranularComposition();
        replacement.replaceWith(counts);
        if (replacement.totalUnits() != unitCount) {
            throw new IllegalArgumentException(
                    "Composition total " + replacement.totalUnits()
                            + " does not match occupancy " + unitCount);
        }
        composition.replaceWith(replacement.toArray());
        refreshDominantMaterial();
        markDirty(DirtyFlags.MATERIAL);
    }

    private void refreshDominantMaterial() {
        materialId = composition.dominantMaterialId();
        if (unitCount == 0) materialId = 0;
    }

    private static int defaultMaterialId() {
        return GranularMaterialRegistry.DIRT != null
                ? GranularMaterialRegistry.DIRT.id()
                : 1;
    }

    @Override
    public String toString() {
        return "GranularCell{material=" + material().name()
                + ", composition=" + composition
                + ", units=" + unitCount + "/" + TOTAL_UNITS
                + ", rev=" + revision
                + ", dirty=" + Integer.toBinaryString(dirtyFlags) + "}";
    }
}
