package com.piotrek.groundworks.terrain.cell;

/**
 * Dirty flag constants for {@link GranularCell}.
 *
 * <p>Flags are combined as a bitmask. Each flag indicates what aspect
 * of the cell has changed since the last synchronization or processing.
 */
public final class DirtyFlags {

    /** Occupancy bitset has been modified (units added or removed). */
    public static final int OCCUPANCY = 1;
    /** Material type has been changed. */
    public static final int MATERIAL  = 1 << 1;
    /** Cell needs network synchronization. */
    public static final int SYNC      = 1 << 2;
    /** Cell needs mesh rebuild on client. */
    public static final int MESH      = 1 << 3;
    /** Cell needs relaxation/simulation check. */
    public static final int SIMULATE  = 1 << 4;

    /** All flags set. */
    public static final int ALL = OCCUPANCY | MATERIAL | SYNC | MESH | SIMULATE;

    private DirtyFlags() {}
}
