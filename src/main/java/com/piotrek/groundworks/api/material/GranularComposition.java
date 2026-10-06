package com.piotrek.groundworks.api.material;

import java.util.Arrays;

/**
 * Exact integer composition of granular material.
 *
 * <p>The composition is intentionally independent from cell occupancy:
 * occupancy answers <em>where</em> material exists, while this class answers
 * <em>what</em> the occupied volume contains. Counts are expressed in the same
 * integer Groundworks units as {@code GranularCell}.</p>
 */
public final class GranularComposition {

    private static final int MIN_CAPACITY = 8;
    private int[] unitsByMaterial;
    private int totalUnits;

    public GranularComposition() {
        this.unitsByMaterial = new int[MIN_CAPACITY];
    }

    public static GranularComposition pure(GranularMaterial material, int units) {
        GranularComposition composition = new GranularComposition();
        if (material != null && material.id() > 0 && units > 0) {
            composition.add(material.id(), units);
        }
        return composition;
    }

    public int totalUnits() {
        return totalUnits;
    }

    public boolean isEmpty() {
        return totalUnits <= 0;
    }

    public int unitsOf(int materialId) {
        return materialId >= 0 && materialId < unitsByMaterial.length
                ? unitsByMaterial[materialId]
                : 0;
    }

    public int unitsOf(GranularMaterial material) {
        return material == null ? 0 : unitsOf(material.id());
    }

    public int add(GranularMaterial material, int units) {
        return material == null ? 0 : add(material.id(), units);
    }

    public int add(int materialId, int units) {
        if (materialId <= 0 || units <= 0) return 0;
        ensureCapacity(materialId + 1);
        unitsByMaterial[materialId] += units;
        totalUnits += units;
        return units;
    }

    public int remove(int materialId, int maxUnits) {
        if (materialId <= 0 || maxUnits <= 0 || materialId >= unitsByMaterial.length) return 0;
        int removed = Math.min(maxUnits, unitsByMaterial[materialId]);
        if (removed <= 0) return 0;
        unitsByMaterial[materialId] -= removed;
        totalUnits -= removed;
        return removed;
    }

    /**
     * Extract a single material. Useful for legacy machine APIs which expose
     * {@code storedMaterial()} plus an integer extraction amount.
     */
    public GranularComposition extractMaterial(int materialId, int maxUnits) {
        GranularComposition extracted = new GranularComposition();
        int removed = remove(materialId, maxUnits);
        if (removed > 0) extracted.add(materialId, removed);
        return extracted;
    }

    /**
     * Extract an exact proportional sample of this composition.
     *
     * <p>Largest-remainder rounding keeps the result deterministic and guarantees
     * that the extracted counts sum exactly to the requested integer amount.</p>
     */
    public GranularComposition extractProportional(int maxUnits) {
        GranularComposition extracted = new GranularComposition();
        int requested = Math.min(Math.max(maxUnits, 0), totalUnits);
        if (requested <= 0) return extracted;

        if (requested == totalUnits) {
            extracted.replaceWith(toArray());
            clear();
            return extracted;
        }

        int originalTotal = totalUnits;
        int[] take = new int[unitsByMaterial.length];
        long[] remainders = new long[unitsByMaterial.length];
        int assigned = 0;

        for (int id = 1; id < unitsByMaterial.length; id++) {
            int count = unitsByMaterial[id];
            if (count <= 0) continue;
            long scaled = (long) requested * count;
            int base = (int) (scaled / originalTotal);
            take[id] = Math.min(base, count);
            remainders[id] = scaled % originalTotal;
            assigned += take[id];
        }

        while (assigned < requested) {
            int bestId = 0;
            long bestRemainder = Long.MIN_VALUE;
            for (int id = 1; id < unitsByMaterial.length; id++) {
                if (take[id] >= unitsByMaterial[id]) continue;
                long remainder = remainders[id];
                if (remainder > bestRemainder) {
                    bestRemainder = remainder;
                    bestId = id;
                }
            }
            if (bestId == 0) break;
            take[bestId]++;
            remainders[bestId] = Long.MIN_VALUE;
            assigned++;
        }

        for (int id = 1; id < take.length; id++) {
            int amount = take[id];
            if (amount <= 0) continue;
            unitsByMaterial[id] -= amount;
            extracted.add(id, amount);
        }
        totalUnits -= extracted.totalUnits();
        return extracted;
    }

    public int dominantMaterialId() {
        int bestId = 0;
        int bestCount = 0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            int count = unitsByMaterial[id];
            if (count > bestCount) {
                bestCount = count;
                bestId = id;
            }
        }
        return bestId;
    }

    public boolean isPure() {
        return pureMaterialId() > 0;
    }

    public int pureMaterialId() {
        int found = 0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            if (unitsByMaterial[id] <= 0) continue;
            if (found != 0) return 0;
            found = id;
        }
        return found;
    }

    public int materialKinds() {
        int count = 0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            if (unitsByMaterial[id] > 0) count++;
        }
        return count;
    }

    public GranularComposition copy() {
        GranularComposition copy = new GranularComposition();
        copy.replaceWith(toArray());
        return copy;
    }

    public void clear() {
        Arrays.fill(unitsByMaterial, 0);
        totalUnits = 0;
    }

    public int[] toArray() {
        int last = 0;
        for (int id = unitsByMaterial.length - 1; id > 0; id--) {
            if (unitsByMaterial[id] > 0) {
                last = id;
                break;
            }
        }
        return Arrays.copyOf(unitsByMaterial, Math.max(1, last + 1));
    }

    public void replaceWith(int[] counts) {
        clear();
        if (counts == null || counts.length == 0) return;
        ensureCapacity(counts.length);
        for (int id = 1; id < counts.length; id++) {
            int count = Math.max(0, counts[id]);
            unitsByMaterial[id] = count;
            totalUnits += count;
        }
    }

    public float weightedDensity() {
        if (totalUnits <= 0) return 0.0f;
        double weighted = 0.0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            if (unitsByMaterial[id] <= 0) continue;
            weighted += GranularMaterialRegistry.byId(id).density() * unitsByMaterial[id];
        }
        return (float) (weighted / totalUnits);
    }

    public float weightedAngleOfRepose() {
        if (totalUnits <= 0) return 0.0f;
        double weighted = 0.0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            if (unitsByMaterial[id] <= 0) continue;
            weighted += GranularMaterialRegistry.byId(id).angleOfRepose() * unitsByMaterial[id];
        }
        return (float) (weighted / totalUnits);
    }

    public float weightedCohesion() {
        if (totalUnits <= 0) return 0.0f;
        double weighted = 0.0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            if (unitsByMaterial[id] <= 0) continue;
            weighted += GranularMaterialRegistry.byId(id).cohesion() * unitsByMaterial[id];
        }
        return (float) (weighted / totalUnits);
    }

    public float weightedSlideProbability() {
        if (totalUnits <= 0) return 0.0f;
        double weighted = 0.0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            if (unitsByMaterial[id] <= 0) continue;
            weighted += GranularMaterialRegistry.byId(id).slideProbability() * unitsByMaterial[id];
        }
        return (float) (weighted / totalUnits);
    }

    /**
     * Deterministically choose the visual material for a microvoxel-sized sample.
     *
     * <p>Materials with a visual cluster size greater than one are sampled on a
     * coarser 3D grid. Cobblestone uses 4x4x4 microvoxel clusters, producing
     * visible rubble chunks while preserving its expected percentage over many
     * cells. Fine materials are then selected from the remaining share.</p>
     */
    public int sampleVisualMaterial(long seed, int microX, int microY, int microZ) {
        if (totalUnits <= 0) return 0;
        int pure = pureMaterialId();
        if (pure > 0) return pure;

        int coarseTotal = 0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            int count = unitsByMaterial[id];
            if (count <= 0) continue;
            GranularMaterial material = GranularMaterialRegistry.byId(id);
            int cluster = Math.max(1, material.visualClusterSizeMicrovoxels());
            if (cluster <= 1) continue;
            coarseTotal += count;

            int qx = Math.floorDiv(microX, cluster);
            int qy = Math.floorDiv(microY, cluster);
            int qz = Math.floorDiv(microZ, cluster);
            long hash = mix(seed
                    ^ ((long) id * 0x9E3779B97F4A7C15L)
                    ^ ((long) qx * 0x632BE59BD9B4E019L)
                    ^ ((long) qy * 0x94D049BB133111EBL)
                    ^ ((long) qz * 0xBF58476D1CE4E5B9L));
            if (Math.floorMod(hash, (long) totalUnits) < count) {
                return id;
            }
        }

        int fineTotal = totalUnits - coarseTotal;
        if (fineTotal <= 0) {
            return dominantMaterialId();
        }

        long fineHash = mix(seed
                ^ ((long) microX * 0xD6E8FEB86659FD93L)
                ^ ((long) microY * 0xA5A3564E27F8862DL)
                ^ ((long) microZ * 0x8CB92BA72F3D8DD7L));
        long pick = Math.floorMod(fineHash, (long) fineTotal);

        int cursor = 0;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            int count = unitsByMaterial[id];
            if (count <= 0) continue;
            if (GranularMaterialRegistry.byId(id).visualClusterSizeMicrovoxels() > 1) continue;
            cursor += count;
            if (pick < cursor) return id;
        }
        return dominantMaterialId();
    }

    private void ensureCapacity(int required) {
        if (required <= unitsByMaterial.length) return;
        int newLength = unitsByMaterial.length;
        while (newLength < required) newLength *= 2;
        unitsByMaterial = Arrays.copyOf(unitsByMaterial, newLength);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("GranularComposition{");
        boolean first = true;
        for (int id = 1; id < unitsByMaterial.length; id++) {
            if (unitsByMaterial[id] <= 0) continue;
            if (!first) builder.append(", ");
            first = false;
            builder.append(GranularMaterialRegistry.byId(id).name())
                    .append('=')
                    .append(unitsByMaterial[id]);
        }
        return builder.append('}').toString();
    }
}
