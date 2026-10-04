package com.piotrek.groundworks.terrain.cell;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

/**
 * A single granular cell occupying one vanilla block position.
 *
 * <h2>Storage model</h2>
 * <p>Internal resolution: 8×8×8 = 512 microvoxels.
 * Occupancy is stored as a 512-bit bitset ({@code long[8]}, 64 bytes).
 * One bit per microvoxel: 1 = occupied, 0 = empty.
 *
 * <h2>Indexing convention</h2>
 * <pre>
 *   index = x + 8 * (z + 8 * y)
 *   x = index % 8
 *   z = (index / 8) % 8
 *   y = index / 64
 * </pre>
 * <p>This is XZY order. Y is the major axis (highest bits), which makes
 * vertical column operations cheaper (one contiguous 64-bit long per y-layer).
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@code unitCount == Long.bitCount(occupancy[0]) + ... + Long.bitCount(occupancy[7])}</li>
 *   <li>Material must never be created or destroyed by cell operations.</li>
 * </ul>
 */
public final class GranularCell {

    /** Microvoxels per axis. */
    public static final int RESOLUTION = 8;
    /** Total microvoxels per cell. */
    public static final int TOTAL_UNITS = RESOLUTION * RESOLUTION * RESOLUTION; // 512
    /** Number of longs in the occupancy bitset. */
    public static final int LONGS = TOTAL_UNITS / Long.SIZE; // 8

    /** Current data format version for serialization. */
    public static final int DATA_VERSION = 1;

    private int materialId;
    private final long[] occupancy = new long[LONGS];
    private int unitCount;
    private int revision;
    private int dirtyFlags;

    // Cached column heights for fast surface queries. -1 = not cached.
    private final byte[] columnHeights = new byte[RESOLUTION * RESOLUTION];

    public GranularCell() {
        Arrays.fill(columnHeights, (byte) -1);
    }

    // ── Factory methods ──────────────────────────────────────────────

    /**
     * Create a fully occupied cell for the given material.
     * This is the result of converting a vanilla block.
     */
    public static GranularCell full(GranularMaterial material) {
        GranularCell cell = new GranularCell();
        cell.materialId = material.id();
        Arrays.fill(cell.occupancy, -1L); // all bits set
        cell.unitCount = TOTAL_UNITS;
        cell.revision = 1;
        cell.dirtyFlags = DirtyFlags.ALL;
        return cell;
    }

    /**
     * Create an empty cell (air). Material id 0.
     */
    public static GranularCell empty() {
        GranularCell cell = new GranularCell();
        cell.materialId = 0;
        cell.unitCount = 0;
        cell.revision = 0;
        cell.dirtyFlags = 0;
        return cell;
    }

    // ── Microvoxel indexing ──────────────────────────────────────────

    /**
     * Compute the flat index for microvoxel coordinates.
     * <pre>index = x + 8 * (z + 8 * y)</pre>
     *
     * @param x 0..7
     * @param y 0..7
     * @param z 0..7
     * @return flat index 0..511
     */
    public static int index(int x, int y, int z) {
        return x + RESOLUTION * (z + RESOLUTION * y);
    }

    /** Extract x from flat index. */
    public static int indexX(int index) { return index & 7; }
    /** Extract z from flat index. */
    public static int indexZ(int index) { return (index >> 3) & 7; }
    /** Extract y from flat index. */
    public static int indexY(int index) { return (index >> 6) & 7; }

    // ── Bit-level access ─────────────────────────────────────────────

    /** Check if a specific microvoxel is occupied. */
    public boolean isSet(int x, int y, int z) {
        int idx = index(x, y, z);
        return (occupancy[idx >> 6] & (1L << (idx & 63))) != 0;
    }

    /** Set a microvoxel as occupied. Returns true if it was previously empty. */
    public boolean set(int x, int y, int z) {
        int idx = index(x, y, z);
        int word = idx >> 6;
        long bit = 1L << (idx & 63);
        if ((occupancy[word] & bit) != 0) return false; // already set
        occupancy[word] |= bit;
        unitCount++;
        invalidateColumn(x, z);
        markDirty(DirtyFlags.OCCUPANCY);
        return true;
    }

    /** Clear a microvoxel. Returns true if it was previously occupied. */
    public boolean clear(int x, int y, int z) {
        int idx = index(x, y, z);
        int word = idx >> 6;
        long bit = 1L << (idx & 63);
        if ((occupancy[word] & bit) == 0) return false; // already clear
        occupancy[word] &= ~bit;
        unitCount--;
        invalidateColumn(x, z);
        markDirty(DirtyFlags.OCCUPANCY);
        return true;
    }

    // ── Bulk operations ──────────────────────────────────────────────

    /**
     * Remove up to {@code maxUnits} from the top of the cell.
     * Removes from highest Y first, within each layer scans XZ.
     *
     * @return the number of units actually removed
     */
    public int removeFromTop(int maxUnits) {
        if (maxUnits <= 0 || unitCount == 0) return 0;
        int removed = 0;
        // Scan from top Y layer down
        for (int y = RESOLUTION - 1; y >= 0 && removed < maxUnits; y--) {
            for (int z = 0; z < RESOLUTION && removed < maxUnits; z++) {
                for (int x = 0; x < RESOLUTION && removed < maxUnits; x++) {
                    if (clear(x, y, z)) {
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Add up to {@code maxUnits} to the cell, filling from bottom Y up.
     *
     * @return the number of units actually added
     */
    public int addFromBottom(int maxUnits) {
        if (maxUnits <= 0 || unitCount >= TOTAL_UNITS) return 0;
        int added = 0;
        for (int y = 0; y < RESOLUTION && added < maxUnits; y++) {
            for (int z = 0; z < RESOLUTION && added < maxUnits; z++) {
                for (int x = 0; x < RESOLUTION && added < maxUnits; x++) {
                    if (set(x, y, z)) {
                        added++;
                    }
                }
            }
        }
        return added;
    }

    /**
     * Recount units from the occupancy bitset. Used for validation.
     */
    public int recount() {
        int count = 0;
        for (long word : occupancy) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /**
     * Validate that {@code unitCount} matches the actual bitset population.
     *
     * @return true if consistent
     */
    public boolean validate() {
        return unitCount == recount();
    }

    // ── Column height cache ──────────────────────────────────────────

    /**
     * Get the top occupied Y for a given (x,z) column, 0-indexed.
     * Returns -1 if the column is empty.
     */
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

    /** Invalidate all cached column heights. */
    public void invalidateAllColumns() {
        Arrays.fill(columnHeights, (byte) -1);
    }

    // ── Dirty flags ──────────────────────────────────────────────────

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

    // ── Serialization ────────────────────────────────────────────────

    /** Serialize to NBT. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putString("material", material().name());
        tag.putLongArray("occupancy", occupancy.clone());
        tag.putInt("unitCount", unitCount);
        tag.putInt("revision", revision);
        return tag;
    }

    /** Deserialize from NBT. Returns null on corrupt data. */
    public static GranularCell load(CompoundTag tag) {
        if (!tag.contains("dataVersion")) return null;
        int version = tag.getIntOr("dataVersion", 0);
        if (version < 1 || version > DATA_VERSION) return null;

        String matName = tag.getStringOr("material", "");
        GranularMaterial mat = GranularMaterialRegistry.byName(matName);
        if (mat == null) {
            // Unknown material — preserve data but mark as empty
            mat = GranularMaterial.EMPTY;
        }

        var optOcc = tag.getLongArray("occupancy");
        if (optOcc.isEmpty() || optOcc.get().length != LONGS) return null;
        long[] occ = optOcc.get();

        int storedCount = tag.getIntOr("unitCount", 0);

        GranularCell cell = new GranularCell();
        cell.materialId = mat.id();
        System.arraycopy(occ, 0, cell.occupancy, 0, LONGS);
        cell.unitCount = cell.recount();

        // Validate stored count
        if (cell.unitCount != storedCount) {
            com.piotrek.groundworks.GroundworksMod.LOGGER.warn(
                    "[Groundworks] Unit count mismatch on load: stored={}, actual={}. Using actual.",
                    storedCount, cell.unitCount);
        }

        cell.revision = tag.getIntOr("revision", 0);
        cell.invalidateAllColumns();
        return cell;
    }

    // ── Accessors ────────────────────────────────────────────────────

    public int materialId() { return materialId; }
    public GranularMaterial material() { return GranularMaterialRegistry.byId(materialId); }
    public int unitCount() { return unitCount; }
    public int revision() { return revision; }
    public int dirtyFlags() { return dirtyFlags; }
    public long[] occupancy() { return occupancy; }
    public boolean isEmpty() { return unitCount == 0; }
    public boolean isFull() { return unitCount == TOTAL_UNITS; }

    public void setMaterialId(int id) {
        this.materialId = id;
        markDirty(DirtyFlags.MATERIAL);
    }

    @Override
    public String toString() {
        return "GranularCell{material=" + material().name()
                + ", units=" + unitCount + "/" + TOTAL_UNITS
                + ", rev=" + revision
                + ", dirty=" + Integer.toBinaryString(dirtyFlags) + "}";
    }
}
