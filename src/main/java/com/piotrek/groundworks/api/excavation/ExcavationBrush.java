package com.piotrek.groundworks.api.excavation;

import com.piotrek.groundworks.terrain.cell.GranularCell;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape brush for spherical/hemispherical excavation at a specific contact point.
 */
public final class ExcavationBrush {

    public record LocalVoxel(int x, int y, int z, double distSq) {}

    private ExcavationBrush() {}

    /**
     * Compute local microvoxels within radius of (hitX, hitY, hitZ), sorted by distance from hit point.
     *
     * @param hitX   microvoxel X (0.0 .. 7.0)
     * @param hitY   microvoxel Y (0.0 .. 7.0)
     * @param hitZ   microvoxel Z (0.0 .. 7.0)
     * @param radius radius in microvoxel units (e.g. 2.5)
     * @return candidate microvoxels within the sphere
     */
    public static List<LocalVoxel> sphere(double hitX, double hitY, double hitZ, double radius) {
        List<LocalVoxel> voxels = new ArrayList<>();
        double rSq = radius * radius;

        int minX = Math.max(0, (int) Math.floor(hitX - radius));
        int maxX = Math.min(GranularCell.RESOLUTION - 1, (int) Math.ceil(hitX + radius));
        int minY = Math.max(0, (int) Math.floor(hitY - radius));
        int maxY = Math.min(GranularCell.RESOLUTION - 1, (int) Math.ceil(hitY + radius));
        int minZ = Math.max(0, (int) Math.floor(hitZ - radius));
        int maxZ = Math.min(GranularCell.RESOLUTION - 1, (int) Math.ceil(hitZ + radius));

        for (int y = maxY; y >= minY; y--) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double dx = (x + 0.5) - hitX;
                    double dy = (y + 0.5) - hitY;
                    double dz = (z + 0.5) - hitZ;
                    double dSq = dx * dx + dy * dy + dz * dz;
                    if (dSq <= rSq) {
                        voxels.add(new LocalVoxel(x, y, z, dSq));
                    }
                }
            }
        }

        // Sort by distance (closest to impact center first)
        voxels.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
        return voxels;
    }
}
