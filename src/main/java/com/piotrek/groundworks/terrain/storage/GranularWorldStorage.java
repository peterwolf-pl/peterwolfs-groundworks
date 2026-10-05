package com.piotrek.groundworks.terrain.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.block.entity.GranularBlockEntity;
import com.piotrek.groundworks.networking.GranularSyncHandler;
import com.piotrek.groundworks.simulation.GranularRelaxationEngine;
import com.piotrek.groundworks.terrain.cell.DirtyFlags;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.conversion.BlockConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-dimension storage of all granular cells.
 *
 * <p>Uses Minecraft 26.3's {@link SavedDataType} with a Codec.
 * Persists granular terrain across save/load.
 */
public class GranularWorldStorage extends SavedData {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath(
            GroundworksMod.MOD_ID, "terrain");

    /** Single cell serialized format for Codec. */
    public record CellEntry(long pos, CompoundTag tag) {
        public static final Codec<CellEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.LONG.fieldOf("pos").forGetter(CellEntry::pos),
                        CompoundTag.CODEC.fieldOf("tag").forGetter(CellEntry::tag)
                ).apply(instance, CellEntry::new)
        );
    }

    /** Storage Codec for saving/loading the list of all granular cells. */
    public static final Codec<GranularWorldStorage> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(CellEntry.CODEC).fieldOf("cells").forGetter(GranularWorldStorage::toEntries)
            ).apply(instance, GranularWorldStorage::fromEntries)
    );

    public static final SavedDataType<GranularWorldStorage> TYPE = new SavedDataType<>(
            TYPE_ID,
            GranularWorldStorage::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    /** All active granular cells. Key = BlockPos.asLong(). */
    private final Map<Long, GranularCell> cells = new ConcurrentHashMap<>();

    /** Queue of positions that need simulation processing. */
    private final Deque<Long> dirtyQueue = new ArrayDeque<>();
    private final Set<Long> dirtySet = new HashSet<>();

    private ServerLevel level;

    // Performance counters
    private int cellsProcessedLastTick;
    private int unitsMovedLastTick;
    private long simulationTimeNanos;
    private int syncPacketsSent;
    private long activeSimulationTicks;
    private long totalSimulationTimeNanos;
    private long peakSimulationTimeNanos;
    private long totalCellsProcessed;
    private long totalUnitsMoved;

    private int maxCellsPerTick = 64;
    private long maxSimulationMicros = 1000;

    public GranularWorldStorage() {}

    public static GranularWorldStorage get(ServerLevel level) {
        GranularWorldStorage storage = level.getDataStorage().computeIfAbsent(TYPE);
        storage.level = level;
        return storage;
    }

    // ── Codec serialization helpers ──────────────────────────────────

    private List<CellEntry> toEntries() {
        List<CellEntry> list = new ArrayList<>(cells.size());
        for (var entry : cells.entrySet()) {
            list.add(new CellEntry(entry.getKey(), entry.getValue().save()));
        }
        return list;
    }

    private static GranularWorldStorage fromEntries(List<CellEntry> entries) {
        GranularWorldStorage storage = new GranularWorldStorage();
        for (CellEntry entry : entries) {
            GranularCell cell = GranularCell.load(entry.tag());
            if (cell != null) {
                storage.cells.put(entry.pos(), cell);
            }
        }
        GroundworksMod.LOGGER.info("[Groundworks] Loaded {} granular cells", storage.cells.size());
        return storage;
    }

    // ── Cell access ──────────────────────────────────────────────────

    @Nullable
    public GranularCell getCell(BlockPos pos) {
        return cells.get(pos.asLong());
    }

    @Nullable
    public GranularCell getOrConvert(BlockPos pos) {
        long key = pos.asLong();
        GranularCell existing = cells.get(key);
        if (existing != null) return existing;

        if (level == null) return null;

        GranularCell cell = BlockConverter.convert(level, pos);
        if (cell != null) {
            cells.put(key, cell);
            enqueueDirty(key);
            setDirty();
        }
        return cell;
    }

    public void putCell(BlockPos pos, GranularCell cell) {
        long key = pos.asLong();
        cells.put(key, cell);
        enqueueDirty(key);
        setDirty();

        if (level != null && cell.unitCount() > 0) {
            level.setBlock(pos, GroundworksMod.GRANULAR_BLOCK.defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof GranularBlockEntity blockEntity) {
                blockEntity.setMaterialId(cell.materialId());
                blockEntity.setCell(cell);
                blockEntity.setChanged();
            }
        }
    }

    public void removeCell(BlockPos pos) {
        long key = pos.asLong();
        cells.remove(key);
        dirtySet.remove(key);
        setDirty();
    }

    public boolean hasCell(BlockPos pos) {
        return cells.containsKey(pos.asLong());
    }

    // ── Dirty queue ──────────────────────────────────────────────────

    public void enqueueDirty(long packedPos) {
        if (dirtySet.add(packedPos)) {
            dirtyQueue.add(packedPos);
        }
    }

    public void enqueueDirty(BlockPos pos) {
        enqueueDirty(pos.asLong());
    }

    // ── Tick processing ──────────────────────────────────────────────

    public void tick() {
        boolean hadQueuedWork = !dirtyQueue.isEmpty();
        cellsProcessedLastTick = 0;
        unitsMovedLastTick = 0;
        syncPacketsSent = 0;
        long startNanos = System.nanoTime();

        int processed = 0;
        long maxNanos = maxSimulationMicros * 1000;

        while (!dirtyQueue.isEmpty() && processed < maxCellsPerTick) {
            if (System.nanoTime() - startNanos > maxNanos) break;

            long packedPos = dirtyQueue.poll();
            dirtySet.remove(packedPos);

            GranularCell cell = cells.get(packedPos);
            if (cell == null) continue;

            BlockPos pos = BlockPos.of(packedPos);

            // Consume the current simulation request before relaxing. Any transfer
            // made by the engine marks the source and receivers for another pass.
            // Clearing after relax used to erase that request and stopped piles
            // after exactly one transfer.
            if (cell.isDirty(DirtyFlags.SIMULATE)) {
                cell.clearDirtyFlag(DirtyFlags.SIMULATE);
                int transferred = GranularRelaxationEngine.relaxCell(this, pos, cell);
                unitsMovedLastTick += transferred;
            }

            // Sync dirty cells to clients
            if (cell.isDirty(DirtyFlags.SYNC) && level != null) {
                GranularSyncHandler.sendCellUpdate(level, pos, cell);
                cell.clearDirtyFlag(DirtyFlags.SYNC);
                syncPacketsSent++;
            }

            if (cell.isEmpty()) {
                cells.remove(packedPos);
                if (level != null && level.getBlockState(pos).is(GroundworksMod.GRANULAR_BLOCK)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                continue;
            }

            // OCCUPANCY/MATERIAL/MESH describe the change already synchronized
            // above. Preserve only a newly requested simulation pass.
            cell.clearDirtyFlag(DirtyFlags.OCCUPANCY | DirtyFlags.MATERIAL | DirtyFlags.MESH);
            if (cell.isDirty(DirtyFlags.SIMULATE)) {
                enqueueDirty(packedPos);
            }
            processed++;
        }

        simulationTimeNanos = System.nanoTime() - startNanos;
        cellsProcessedLastTick = processed;
        if (hadQueuedWork) {
            activeSimulationTicks++;
            totalSimulationTimeNanos += simulationTimeNanos;
            peakSimulationTimeNanos = Math.max(peakSimulationTimeNanos, simulationTimeNanos);
            totalCellsProcessed += processed;
            totalUnitsMoved += unitsMovedLastTick;
        }
    }

    // ── Queries ──────────────────────────────────────────────────────

    public int activeCellCount() { return cells.size(); }

    public long totalUnits() {
        long sum = 0;
        for (GranularCell cell : cells.values()) {
            sum += cell.unitCount();
        }
        return sum;
    }

    public int dirtyQueueSize() { return dirtyQueue.size(); }
    public int cellsProcessedLastTick() { return cellsProcessedLastTick; }
    public int unitsMovedLastTick() { return unitsMovedLastTick; }
    public long simulationTimeNanos() { return simulationTimeNanos; }
    public int syncPacketsSent() { return syncPacketsSent; }
    public long activeSimulationTicks() { return activeSimulationTicks; }
    public long averageSimulationTimeNanos() {
        return activeSimulationTicks == 0 ? 0 : totalSimulationTimeNanos / activeSimulationTicks;
    }
    public long peakSimulationTimeNanos() { return peakSimulationTimeNanos; }
    public long totalCellsProcessed() { return totalCellsProcessed; }
    public long totalUnitsMoved() { return totalUnitsMoved; }

    public Map<Long, GranularCell> allCells() {
        return Collections.unmodifiableMap(cells);
    }

    public ServerLevel getLevel() { return level; }
}
