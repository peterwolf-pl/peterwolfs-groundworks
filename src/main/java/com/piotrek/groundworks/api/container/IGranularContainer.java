package com.piotrek.groundworks.api.container;

import com.piotrek.groundworks.api.material.GranularComposition;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;

/**
 * Common volumetric material container interface for tools, buckets, wheelbarrows, and conveyors.
 *
 * <h2>Invariant</h2>
 * <p>All volumes are strictly integer microvoxels ($512\text{ units} = 1.000\text{ m}^3$).
 */
public interface IGranularContainer {

    /** Maximum unit capacity of this container. */
    int capacity();

    /** Currently stored units ($0 \dots \text{capacity}$). */
    int storedUnits();

    /** The material currently inside, or {@link GranularMaterial#EMPTY} if empty. */
    GranularMaterial storedMaterial();

    /** Whether the container has room for more material. */
    default boolean hasRoom() {
        return storedUnits() < capacity();
    }

    /** Whether the container is completely empty. */
    default boolean isEmpty() {
        return storedUnits() <= 0;
    }

    /**
     * Add material to this container.
     *
     * @param material the material type
     * @param units    the requested units to add
     * @return the number of units actually accepted
     */
    int acceptMaterial(GranularMaterial material, int units);

    /**
     * Remove material from this container.
     *
     * @param maxUnits maximum units to extract
     * @return the number of units actually removed
     */
    int extractMaterial(int maxUnits);

    /**
     * Snapshot of the exact stored mixture. Legacy implementations default to
     * a pure composition based on storedMaterial().
     */
    default GranularComposition storedComposition() {
        return GranularComposition.pure(storedMaterial(), storedUnits());
    }

    /**
     * Add an exact mixture. Implementations that do not override this method
     * retain their legacy single-material behavior.
     */
    default int acceptComposition(GranularComposition composition) {
        if (composition == null || composition.isEmpty()) return 0;
        int accepted = 0;
        int[] counts = composition.toArray();
        for (int id = 1; id < counts.length && hasRoom(); id++) {
            if (counts[id] <= 0) continue;
            accepted += acceptMaterial(GranularMaterialRegistry.byId(id), counts[id]);
        }
        return accepted;
    }

    /**
     * Extract an exact composition snapshot. The default implementation remains
     * compatible with legacy single-material containers.
     */
    default GranularComposition extractComposition(int maxUnits) {
        GranularMaterial selected = storedMaterial();
        int removed = extractMaterial(maxUnits);
        return GranularComposition.pure(selected, removed);
    }
}
