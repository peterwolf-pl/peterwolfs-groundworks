package com.piotrek.groundworks.api.world;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.deposit.DepositApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.networking.GranularSyncHandler;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.conversion.BlockConverter;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World-space terrain operations for construction machinery.
 *
 * <p>Coordinates are expressed in Minecraft world units. Authoritative material
 * accounting remains integer microvoxels. Geometry may use floating point values,
 * but every mutation removes or adds complete Groundworks units.
 */
public final class WorldSpaceApi {

    public static final double MICROVOXEL_SIZE = 1.0D / GranularCell.RESOLUTION;
    public static final double DEFAULT_EXCAVATION_RADIUS = 3.2D / GranularCell.RESOLUTION;

    private static final double EPSILON = 1.0E-9D;

    private WorldSpaceApi() {}

    /**
     * Returns true when the position contains existing granular terrain or a
     * vanilla block that Groundworks can lazily convert.
     */
    public static boolean isDiggable(ServerLevel level, BlockPos pos) {
        GranularCell cell = GranularWorldStorage.get(level).getCell(pos);
        if (cell != null) {
            return !cell.isEmpty();
        }
        return BlockConverter.isConvertible(level.getBlockState(pos));
    }

    /**
     * Tests the exact world point against granular occupancy.
     *
     * <p>Unconverted convertible blocks are treated as fully occupied.
     */
    public static boolean containsMaterialAt(ServerLevel level, Vec3 worldPoint) {
        BlockPos pos = BlockPos.containing(worldPoint);
        GranularCell cell = GranularWorldStorage.get(level).getCell(pos);
        if (cell != null) {
            int x = microCoordinate(worldPoint.x - pos.getX());
            int y = microCoordinate(worldPoint.y - pos.getY());
            int z = microCoordinate(worldPoint.z - pos.getZ());
            return cell.isSet(x, y, z);
        }
        return BlockConverter.isConvertible(level.getBlockState(pos));
    }

    /**
     * Returns the exact top surface Y for the selected local X/Z column.
     *
     * @return absolute world Y, or negative infinity when no material exists
     */
    public static double getSurfaceWorldY(
            ServerLevel level,
            BlockPos pos,
            double worldX,
            double worldZ
    ) {
        GranularCell cell = GranularWorldStorage.get(level).getCell(pos);
        if (cell != null) {
            int x = microCoordinate(worldX - pos.getX());
            int z = microCoordinate(worldZ - pos.getZ());
            int top = cell.getColumnHeight(x, z);
            return top < 0
                    ? Double.NEGATIVE_INFINITY
                    : pos.getY() + (top + 1) * MICROVOXEL_SIZE;
        }

        return BlockConverter.isConvertible(level.getBlockState(pos))
                ? pos.getY() + 1.0D
                : Double.NEGATIVE_INFINITY;
    }

    /**
     * Excavate around an exact world contact point using the standard Groundworks
     * machine brush.
     */
    public static ExcavationResult excavateAt(
            ServerLevel level,
            Vec3 worldCenter,
            int maxUnits
    ) {
        return excavateSphere(level, worldCenter, DEFAULT_EXCAVATION_RADIUS, maxUnits);
    }

    /**
     * Excavate a spherical world-space brush across block boundaries.
     *
     * <p>The operation chooses the material of the first occupied microvoxel hit
     * and removes only that material for the remainder of the call. This preserves
     * the single-material contract of {@link ExcavationResult}.
     */
    public static ExcavationResult excavateSphere(
            ServerLevel level,
            Vec3 worldCenter,
            double radius,
            int maxUnits
    ) {
        if (maxUnits <= 0 || !Double.isFinite(radius) || radius <= 0.0D) {
            return ExcavationResult.NONE;
        }

        List<WorldVoxel> candidates = collectSphereCandidates(worldCenter, radius);
        if (candidates.isEmpty()) {
            return ExcavationResult.NONE;
        }

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        Map<Long, GranularCell> cellCache = new HashMap<>();
        Set<BlockPos> changed = new LinkedHashSet<>();

        GranularMaterial selectedMaterial = null;
        int removed = 0;

        for (WorldVoxel candidate : candidates) {
            if (removed >= maxUnits) {
                break;
            }

            BlockPos pos = candidate.pos();
            GranularMaterial candidateMaterial = materialAt(level, storage, pos);
            if (candidateMaterial == null || candidateMaterial.id() == 0) {
                continue;
            }
            if (selectedMaterial != null && selectedMaterial.id() != candidateMaterial.id()) {
                continue;
            }

            GranularCell cell = cellCache.get(pos.asLong());
            if (cell == null) {
                cell = storage.getCell(pos);
                if (cell == null) {
                    cell = storage.getOrConvert(pos);
                }
                if (cell == null || cell.isEmpty()) {
                    continue;
                }
                cellCache.put(pos.asLong(), cell);
            }

            if (!cell.isSet(candidate.x(), candidate.y(), candidate.z())) {
                continue;
            }

            if (selectedMaterial == null) {
                selectedMaterial = cell.material();
            }
            if (cell.materialId() != selectedMaterial.id()) {
                continue;
            }

            if (cell.clear(candidate.x(), candidate.y(), candidate.z())) {
                removed++;
                changed.add(pos.immutable());
            }
        }

        if (removed == 0 || selectedMaterial == null) {
            return ExcavationResult.NONE;
        }

        for (BlockPos pos : changed) {
            GranularCell cell = storage.getCell(pos);
            if (cell != null) {
                syncCell(level, storage, pos, cell);
            }
        }

        return new ExcavationResult(selectedMaterial, removed, List.copyOf(changed));
    }

    /**
     * Shaves all occupied microvoxels at or above an absolute world-space grade.
     *
     * <p>The grade is quantized to the 1/8 block Groundworks resolution.
     */
    public static ExcavationResult excavateAbove(
            ServerLevel level,
            BlockPos pos,
            double worldCutY,
            int maxUnits
    ) {
        if (maxUnits <= 0 || !Double.isFinite(worldCutY)) {
            return ExcavationResult.NONE;
        }

        double localMicroY = (worldCutY - pos.getY()) * GranularCell.RESOLUTION;
        int startY = firstLayerAtOrAbove(localMicroY);
        if (startY >= GranularCell.RESOLUTION) {
            return ExcavationResult.NONE;
        }

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getOrConvert(pos);
        if (cell == null || cell.isEmpty()) {
            return ExcavationResult.NONE;
        }

        GranularMaterial material = cell.material();
        int removed = 0;

        for (int y = GranularCell.RESOLUTION - 1; y >= startY && removed < maxUnits; y--) {
            for (int z = 0; z < GranularCell.RESOLUTION && removed < maxUnits; z++) {
                for (int x = 0; x < GranularCell.RESOLUTION && removed < maxUnits; x++) {
                    if (cell.clear(x, y, z)) {
                        removed++;
                    }
                }
            }
        }

        if (removed <= 0) {
            return ExcavationResult.NONE;
        }

        syncCell(level, storage, pos, cell);
        return new ExcavationResult(material, removed, List.of(pos.immutable()));
    }

    /**
     * Fills only the volume fully below an absolute world-space target grade.
     *
     * <p>This never deposits above the requested grade. Rejected units remain with
     * the caller so machine-side material conservation is explicit.
     */
    public static DepositResult fillBelow(
            ServerLevel level,
            BlockPos pos,
            double targetWorldY,
            GranularMaterial material,
            int availableUnits
    ) {
        if (availableUnits <= 0
                || material == null
                || material.id() == 0
                || !Double.isFinite(targetWorldY)) {
            return DepositResult.NONE;
        }

        double localMicroY = (targetWorldY - pos.getY()) * GranularCell.RESOLUTION;
        int fullLayers = fullLayersBelow(localMicroY);
        if (fullLayers <= 0) {
            return new DepositResult(material, 0, availableUnits, List.of());
        }

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getCell(pos);

        int freeBelowGrade;
        if (cell == null) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                return new DepositResult(material, 0, availableUnits, List.of());
            }
            freeBelowGrade = fullLayers * GranularCell.RESOLUTION * GranularCell.RESOLUTION;
        } else {
            if (!cell.isEmpty() && cell.materialId() != material.id()) {
                return new DepositResult(material, 0, availableUnits, List.of());
            }
            freeBelowGrade = countEmptyBelow(cell, fullLayers);
        }

        if (freeBelowGrade <= 0) {
            return new DepositResult(material, 0, availableUnits, List.of());
        }

        int request = Math.min(availableUnits, freeBelowGrade);
        DepositResult partial = DepositApi.deposit(level, pos, material, request);
        return new DepositResult(
                material,
                partial.unitsDeposited(),
                availableUnits - partial.unitsDeposited(),
                partial.affectedCells()
        );
    }

    /**
     * Marks an existing granular cell for natural relaxation.
     */
    public static void markForSimulation(ServerLevel level, BlockPos pos) {
        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getCell(pos);
        if (cell == null) {
            return;
        }

        cell.markDirty(DirtyFlags.SIMULATE);
        storage.enqueueDirty(pos);
        storage.setDirty();
    }

    @Nullable
    private static GranularMaterial materialAt(
            ServerLevel level,
            GranularWorldStorage storage,
            BlockPos pos
    ) {
        GranularCell existing = storage.getCell(pos);
        if (existing != null) {
            return existing.isEmpty() ? null : existing.material();
        }
        return GranularMaterialRegistry.forBlockState(level.getBlockState(pos));
    }

    private static int countEmptyBelow(GranularCell cell, int fullLayers) {
        int empty = 0;
        for (int y = 0; y < fullLayers; y++) {
            for (int z = 0; z < GranularCell.RESOLUTION; z++) {
                for (int x = 0; x < GranularCell.RESOLUTION; x++) {
                    if (!cell.isSet(x, y, z)) {
                        empty++;
                    }
                }
            }
        }
        return empty;
    }

    private static List<WorldVoxel> collectSphereCandidates(Vec3 center, double radius) {
        double radiusSq = radius * radius;
        int minBlockX = (int) Math.floor(center.x - radius);
        int maxBlockX = (int) Math.floor(center.x + radius);
        int minBlockY = (int) Math.floor(center.y - radius);
        int maxBlockY = (int) Math.floor(center.y + radius);
        int minBlockZ = (int) Math.floor(center.z - radius);
        int maxBlockZ = (int) Math.floor(center.z + radius);

        List<WorldVoxel> result = new ArrayList<>();

        for (int blockY = minBlockY; blockY <= maxBlockY; blockY++) {
            for (int blockZ = minBlockZ; blockZ <= maxBlockZ; blockZ++) {
                for (int blockX = minBlockX; blockX <= maxBlockX; blockX++) {
                    BlockPos pos = new BlockPos(blockX, blockY, blockZ);

                    for (int y = 0; y < GranularCell.RESOLUTION; y++) {
                        double worldY = blockY + (y + 0.5D) * MICROVOXEL_SIZE;
                        double dy = worldY - center.y;

                        for (int z = 0; z < GranularCell.RESOLUTION; z++) {
                            double worldZ = blockZ + (z + 0.5D) * MICROVOXEL_SIZE;
                            double dz = worldZ - center.z;

                            for (int x = 0; x < GranularCell.RESOLUTION; x++) {
                                double worldX = blockX + (x + 0.5D) * MICROVOXEL_SIZE;
                                double dx = worldX - center.x;
                                double distSq = dx * dx + dy * dy + dz * dz;

                                if (distSq <= radiusSq + EPSILON) {
                                    result.add(new WorldVoxel(pos, x, y, z, distSq));
                                }
                            }
                        }
                    }
                }
            }
        }

        result.sort(Comparator.comparingDouble(WorldVoxel::distanceSq));
        return result;
    }

    static int microCoordinate(double localCoordinate) {
        return Math.max(0, Math.min(
                GranularCell.RESOLUTION - 1,
                (int) Math.floor(localCoordinate * GranularCell.RESOLUTION)
        ));
    }

    static int firstLayerAtOrAbove(double localMicroY) {
        if (localMicroY <= 0.0D) {
            return 0;
        }
        if (localMicroY >= GranularCell.RESOLUTION) {
            return GranularCell.RESOLUTION;
        }
        return Math.max(0, Math.min(
                GranularCell.RESOLUTION,
                (int) Math.ceil(localMicroY - EPSILON)
        ));
    }

    static int fullLayersBelow(double localMicroY) {
        if (localMicroY <= 0.0D) {
            return 0;
        }
        if (localMicroY >= GranularCell.RESOLUTION) {
            return GranularCell.RESOLUTION;
        }
        return Math.max(0, Math.min(
                GranularCell.RESOLUTION,
                (int) Math.floor(localMicroY + EPSILON)
        ));
    }

    private static void syncCell(
            ServerLevel level,
            GranularWorldStorage storage,
            BlockPos pos,
            GranularCell cell
    ) {
        cell.markDirty(DirtyFlags.SYNC | DirtyFlags.MESH | DirtyFlags.SIMULATE);
        storage.enqueueDirty(pos);
        storage.setDirty();

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof GranularBlockEntity granularBlockEntity) {
            granularBlockEntity.setMaterialId(cell.materialId());
            granularBlockEntity.setCell(cell);
            granularBlockEntity.setChanged();
            level.sendBlockUpdated(
                    pos,
                    blockEntity.getBlockState(),
                    blockEntity.getBlockState(),
                    Block.UPDATE_ALL_IMMEDIATE
            );
        }

        GranularSyncHandler.sendCellUpdate(level, pos, cell);

        if (cell.isEmpty()) {
            storage.removeCell(pos);
            level.removeBlock(pos, false);
        } else if (!level.getBlockState(pos).is(GroundworksMod.GRANULAR_BLOCK)) {
            level.setBlock(
                    pos,
                    GroundworksMod.GRANULAR_BLOCK.defaultBlockState(),
                    Block.UPDATE_ALL_IMMEDIATE
            );
        }
    }

    private record WorldVoxel(
            BlockPos pos,
            int x,
            int y,
            int z,
            double distanceSq
    ) {}
}
