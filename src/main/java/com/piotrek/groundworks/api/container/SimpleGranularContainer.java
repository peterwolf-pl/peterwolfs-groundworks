package com.piotrek.groundworks.api.container;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;

/**
 * Standard in-memory implementation of {@link IGranularContainer} for machinery components
 * (wheelbarrows, excavator buckets, dump truck beds, hopper buffers).
 */
public class SimpleGranularContainer implements IGranularContainer {

    private final int capacity;
    private int storedUnits;
    private GranularMaterial storedMaterial;

    public SimpleGranularContainer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.storedUnits = 0;
        this.storedMaterial = GranularMaterial.EMPTY;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int storedUnits() {
        return storedUnits;
    }

    @Override
    public GranularMaterial storedMaterial() {
        return storedMaterial;
    }

    @Override
    public int acceptMaterial(GranularMaterial material, int units) {
        if (units <= 0 || material == null || material == GranularMaterial.EMPTY) return 0;

        // If not empty, cannot mix different materials
        if (storedUnits > 0 && storedMaterial.id() != material.id()) {
            return 0;
        }

        int available = capacity - storedUnits;
        int toAdd = Math.min(units, available);

        if (toAdd > 0) {
            this.storedMaterial = material;
            this.storedUnits += toAdd;
        }

        return toAdd;
    }

    @Override
    public int extractMaterial(int maxUnits) {
        if (maxUnits <= 0 || storedUnits <= 0) return 0;

        int toExtract = Math.min(maxUnits, storedUnits);
        storedUnits -= toExtract;

        if (storedUnits <= 0) {
            storedMaterial = GranularMaterial.EMPTY;
        }

        return toExtract;
    }
}
