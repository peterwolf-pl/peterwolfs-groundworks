package com.piotrek.groundworks.api.container;

import com.piotrek.groundworks.api.material.GranularComposition;
import com.piotrek.groundworks.api.material.GranularMaterial;

/**
 * Standard mixture-aware in-memory implementation for machinery components.
 *
 * <p>Legacy callers remain safe: storedMaterial() exposes the current dominant
 * component and extractMaterial() removes only that component. A caller that
 * reads storedMaterial(), extracts units, and deposits that material therefore
 * preserves per-material mass even without using the new composition API.</p>
 */
public class SimpleGranularContainer implements IGranularContainer {

    private final int capacity;
    private final GranularComposition composition = new GranularComposition();

    public SimpleGranularContainer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int storedUnits() {
        return composition.totalUnits();
    }

    @Override
    public GranularMaterial storedMaterial() {
        int id = composition.dominantMaterialId();
        return id <= 0
                ? GranularMaterial.EMPTY
                : com.piotrek.groundworks.api.material.GranularMaterialRegistry.byId(id);
    }

    @Override
    public GranularComposition storedComposition() {
        return composition.copy();
    }

    @Override
    public int acceptMaterial(GranularMaterial material, int units) {
        if (units <= 0 || material == null || material == GranularMaterial.EMPTY) return 0;

        int available = capacity - storedUnits();
        int toAdd = Math.min(units, available);
        if (toAdd > 0) {
            composition.add(material, toAdd);
        }
        return toAdd;
    }

    @Override
    public int acceptComposition(GranularComposition incoming) {
        if (incoming == null || incoming.isEmpty() || !hasRoom()) return 0;

        int available = capacity - storedUnits();
        GranularComposition accepted = incoming.copy();
        if (accepted.totalUnits() > available) {
            accepted = accepted.extractProportional(available);
        }
        composition.addAll(accepted);
        return accepted.totalUnits();
    }

    @Override
    public int extractMaterial(int maxUnits) {
        if (maxUnits <= 0 || composition.isEmpty()) return 0;
        int selected = composition.dominantMaterialId();
        return composition.remove(selected, maxUnits);
    }

    @Override
    public GranularComposition extractComposition(int maxUnits) {
        return composition.extractProportional(maxUnits);
    }
}
