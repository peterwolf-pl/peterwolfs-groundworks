package com.piotrek.groundworks.api.container;

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
}
