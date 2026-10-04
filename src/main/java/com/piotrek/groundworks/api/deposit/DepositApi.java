package com.piotrek.groundworks.api.deposit;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.networking.GranularSyncHandler;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.conversion.BlockConverter;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class DepositApi {

    private DepositApi() {}

    public static DepositResult deposit(
            ServerLevel level, BlockPos pos, GranularMaterial material, int units) {

        if (units <= 0) return DepositResult.NONE;

        GranularWorldStorage storage = GranularWorldStorage.get(level);
        GranularCell cell = storage.getCell(pos);

        if (cell == null) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                cell = GranularCell.empty();
                cell.setMaterialId(material.id());
                int added = cell.addFromBottom(units);
                if (added > 0) {
                    storage.putCell(pos, cell);

                    // Place anchor block in world
                    level.setBlock(pos, GroundworksMod.GRANULAR_BLOCK.defaultBlockState(), 3);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof GranularBlockEntity gbe) {
                        gbe.setMaterialId(material.id());
                    }

                    cell.markDirty(DirtyFlags.ALL);
                    storage.enqueueDirty(pos);
                    storage.setDirty();

                    // Immediate client sync
                    GranularSyncHandler.sendCellUpdate(level, pos, cell);

                    return new DepositResult(material, added, units - added,
                            List.of(pos.immutable()));
                }
                return new DepositResult(material, 0, units, List.of());
            }

            if (BlockConverter.isConvertible(state)) {
                cell = storage.getOrConvert(pos);
                if (cell == null) return new DepositResult(material, 0, units, List.of());
            } else {
                return new DepositResult(material, 0, units, List.of());
            }
        }

        if (!cell.isEmpty() && cell.materialId() != material.id()) {
            return new DepositResult(material, 0, units, List.of());
        }

        if (cell.isEmpty()) {
            cell.setMaterialId(material.id());
        }

        int added = cell.addFromBottom(units);
        if (added > 0) {
            cell.markDirty(DirtyFlags.ALL);
            storage.enqueueDirty(pos);
            storage.setDirty();

            // Immediate client sync
            GranularSyncHandler.sendCellUpdate(level, pos, cell);
        }

        return new DepositResult(material, added, units - added,
                added > 0 ? List.of(pos.immutable()) : List.of());
    }

    public static DepositResult depositWithOverflow(
            ServerLevel level, BlockPos origin, GranularMaterial material, int units) {

        if (units <= 0) return DepositResult.NONE;

        int remaining = units;
        int totalDeposited = 0;
        List<BlockPos> affected = new ArrayList<>();
        BlockPos current = origin;

        for (int attempts = 0; attempts < 8 && remaining > 0; attempts++) {
            DepositResult partial = deposit(level, current, material, remaining);
            totalDeposited += partial.unitsDeposited();
            remaining -= partial.unitsDeposited();
            affected.addAll(partial.affectedCells());

            if (remaining > 0) {
                current = current.above();
            }
        }

        return new DepositResult(material, totalDeposited, remaining, affected);
    }
}
