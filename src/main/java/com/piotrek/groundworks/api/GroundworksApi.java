package com.piotrek.groundworks.api;

import com.piotrek.groundworks.api.deposit.DepositApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationApi;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.world.WorldSpaceApi;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Public API for Peterwolf's Groundworks.
 *
 * <p>Future mods such as excavators, loaders, bulldozers, conveyors, mining
 * machines, and dump trucks should call this API instead of touching internal
 * terrain storage or synchronization details.
 */
public final class GroundworksApi {

    private GroundworksApi() {}

    // Excavation

    /**
     * Remove up to {@code maxUnits} from a single position.
     */
    public static ExcavationResult excavate(ServerLevel level, BlockPos pos, int maxUnits) {
        return ExcavationApi.excavate(level, pos, maxUnits);
    }

    /**
     * Remove material from multiple positions.
     */
    public static ExcavationResult excavateMulti(
            ServerLevel level, List<BlockPos> positions, int maxUnits) {
        return ExcavationApi.excavateMulti(level, positions, maxUnits);
    }

    /**
     * Excavate around an exact world-space contact point using the standard
     * Groundworks machine brush. The brush can cross block boundaries.
     */
    public static ExcavationResult excavateAt(
            ServerLevel level,
            Vec3 worldCenter,
            int maxUnits
    ) {
        return WorldSpaceApi.excavateAt(level, worldCenter, maxUnits);
    }

    /**
     * Excavate a spherical brush in world space.
     */
    public static ExcavationResult excavateSphere(
            ServerLevel level,
            Vec3 worldCenter,
            double radius,
            int maxUnits
    ) {
        return WorldSpaceApi.excavateSphere(level, worldCenter, radius, maxUnits);
    }

    /**
     * Shave granular terrain at or above an absolute world-space cutting grade.
     */
    public static ExcavationResult excavateAbove(
            ServerLevel level,
            BlockPos pos,
            double worldCutY,
            int maxUnits
    ) {
        return WorldSpaceApi.excavateAbove(level, pos, worldCutY, maxUnits);
    }

    // Deposition and grading fill

    /**
     * Deposit material at a position.
     */
    public static DepositResult deposit(
            ServerLevel level, BlockPos pos, GranularMaterial material, int units) {
        return DepositApi.deposit(level, pos, material, units);
    }

    /**
     * Deposit with upward overflow.
     */
    public static DepositResult depositWithOverflow(
            ServerLevel level, BlockPos origin, GranularMaterial material, int units) {
        return DepositApi.depositWithOverflow(level, origin, material, units);
    }

    /**
     * Fill only the microvoxel volume fully below an absolute world-space grade.
     */
    public static DepositResult fillBelow(
            ServerLevel level,
            BlockPos pos,
            double targetWorldY,
            GranularMaterial material,
            int availableUnits
    ) {
        return WorldSpaceApi.fillBelow(level, pos, targetWorldY, material, availableUnits);
    }

    // World-space queries

    /**
     * Returns true when a cell is granular or can be lazily converted.
     */
    public static boolean isDiggable(ServerLevel level, BlockPos pos) {
        return WorldSpaceApi.isDiggable(level, pos);
    }

    /**
     * Returns the material at a position without forcing lazy conversion.
     */
    @Nullable
    public static GranularMaterial getMaterial(ServerLevel level, BlockPos pos) {
        return WorldSpaceApi.getMaterial(level, pos);
    }

    /**
     * Returns true when the exact world point lies inside material.
     */
    public static boolean containsMaterialAt(ServerLevel level, Vec3 worldPoint) {
        return WorldSpaceApi.containsMaterialAt(level, worldPoint);
    }

    /**
     * Returns the absolute world-space surface Y for a cell column.
     */
    public static double getSurfaceWorldY(
            ServerLevel level,
            BlockPos pos,
            double worldX,
            double worldZ
    ) {
        return WorldSpaceApi.getSurfaceWorldY(level, pos, worldX, worldZ);
    }

    /**
     * Mark a granular cell for natural relaxation.
     */
    public static void markForSimulation(ServerLevel level, BlockPos pos) {
        WorldSpaceApi.markForSimulation(level, pos);
    }

    // Low-level queries retained for compatibility

    /**
     * Query the material at a position.
     *
     * @return the granular cell, or null if not converted
     */
    @Nullable
    public static GranularCell queryCell(ServerLevel level, BlockPos pos) {
        return GranularWorldStorage.get(level).getCell(pos);
    }

    /**
     * Get the highest occupied microvoxel Y at (x,z) within a granular cell.
     *
     * @return 0..7 if occupied, -1 if empty or not a granular cell
     */
    public static int getSurfaceHeight(ServerLevel level, BlockPos pos, int localX, int localZ) {
        GranularCell cell = GranularWorldStorage.get(level).getCell(pos);
        if (cell == null) return -1;
        return cell.getColumnHeight(localX, localZ);
    }
}
