package com.piotrek.groundworks.api.excavation;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.networking.GranularSyncHandler;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ExcavationApi {

    private ExcavationApi() {}

    public static ExcavationResult excavateAt(
            ServerLevel level, BlockPos pos, Vec3 hitLocation, int maxUnits) {

        if (maxUnits <= 0) return ExcavationResult.NONE;

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getOrConvert(pos);
        if (cell == null) return ExcavationResult.NONE;

        // Convert world hit location to local microvoxel coordinates (0.0 .. 8.0)
        double localX = (hitLocation.x - pos.getX()) * GranularCell.RESOLUTION;
        double localY = (hitLocation.y - pos.getY()) * GranularCell.RESOLUTION;
        double localZ = (hitLocation.z - pos.getZ()) * GranularCell.RESOLUTION;

        localX = Math.max(0.0, Math.min(GranularCell.RESOLUTION - 0.01, localX));
        localY = Math.max(0.0, Math.min(GranularCell.RESOLUTION - 0.01, localY));
        localZ = Math.max(0.0, Math.min(GranularCell.RESOLUTION - 0.01, localZ));

        // Use a spherical crater brush with radius ~3.2 microvoxels (~40cm)
        List<ExcavationBrush.LocalVoxel> brushVoxels = ExcavationBrush.sphere(localX, localY, localZ, 3.2);

        int removed = 0;
        for (ExcavationBrush.LocalVoxel v : brushVoxels) {
            if (removed >= maxUnits) break;
            if (cell.clear(v.x(), v.y(), v.z())) {
                removed++;
            }
        }

        if (removed < maxUnits) {
            removed += cell.removeFromTop(maxUnits - removed);
        }

        if (removed > 0) {
            cell.markDirty(DirtyFlags.SYNC | DirtyFlags.MESH | DirtyFlags.SIMULATE);
            storage.enqueueDirty(pos);
            storage.setDirty();

            // Broadcast immediate sync to clients right after excavation
            GranularSyncHandler.sendCellUpdate(level, pos, cell);

            if (cell.isEmpty()) {
                storage.removeCell(pos);
            }
        }

        List<BlockPos> affected = removed > 0 ? List.of(pos.immutable()) : List.of();
        return new ExcavationResult(cell.material(), removed, affected);
    }

    public static ExcavationResult excavate(ServerLevel level, BlockPos pos, int maxUnits) {
        if (maxUnits <= 0) return ExcavationResult.NONE;

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getOrConvert(pos);
        if (cell == null) return ExcavationResult.NONE;

        GranularMaterial material = cell.material();
        int removed = cell.removeFromTop(maxUnits);

        if (removed > 0) {
            cell.markDirty(DirtyFlags.SYNC | DirtyFlags.MESH | DirtyFlags.SIMULATE);
            storage.enqueueDirty(pos);
            storage.setDirty();

            // Immediate sync to clients
            GranularSyncHandler.sendCellUpdate(level, pos, cell);

            if (cell.isEmpty()) {
                storage.removeCell(pos);
            }
        }

        List<BlockPos> affected = removed > 0 ? List.of(pos.immutable()) : List.of();
        return new ExcavationResult(material, removed, affected);
    }

    public static ExcavationResult excavateMulti(
            ServerLevel level, List<BlockPos> positions, int maxUnits) {

        if (maxUnits <= 0 || positions.isEmpty()) return ExcavationResult.NONE;

        GranularMaterial material = null;
        int totalRemoved = 0;
        List<BlockPos> affected = new ArrayList<>();

        for (BlockPos pos : positions) {
            if (totalRemoved >= maxUnits) break;

            int remaining = maxUnits - totalRemoved;
            ExcavationResult partial = excavate(level, pos, remaining);

            if (partial.success()) {
                if (material == null) material = partial.material();
                totalRemoved += partial.unitsRemoved();
                affected.addAll(partial.affectedCells());
            }
        }

        if (material == null) return ExcavationResult.NONE;
        return new ExcavationResult(material, totalRemoved, affected);
    }
}
