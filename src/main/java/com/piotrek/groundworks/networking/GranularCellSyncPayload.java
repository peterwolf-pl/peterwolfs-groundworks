package com.piotrek.groundworks.networking;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client payload carrying a delta update or full state of a single granular cell.
 *
 * <p>To save network bandwidth, this payload supports:
 * <ul>
 *   <li><b>Full state sync:</b> When isDelta is false, sends all 8 occupancy longs.</li>
 *   <li><b>XOR Delta sync:</b> When isDelta is true, sends a mask byte indicating which
 *       of the 8 occupancy words changed, followed only by the changed words.</li>
 *   <li><b>Removal sync:</b> When unitCount is 0, indicates the cell was cleared.</li>
 * </ul>
 */
public record GranularCellSyncPayload(
        BlockPos pos,
        int materialId,
        int unitCount,
        int revision,
        boolean isDelta,
        int changedWordMask,
        long[] words
) implements CustomPacketPayload {

    public static final Type<GranularCellSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GroundworksMod.MOD_ID, "cell_sync")
    );

    /** Factory for a full state sync packet. */
    public static GranularCellSyncPayload full(BlockPos pos, GranularCell cell) {
        long[] occ = cell.occupancy().clone();
        return new GranularCellSyncPayload(
                pos,
                cell.materialId(),
                cell.unitCount(),
                cell.revision(),
                false,
                0xFF, // all 8 words
                occ
        );
    }

    /** Factory for an empty/removed cell sync packet. */
    public static GranularCellSyncPayload remove(BlockPos pos) {
        return new GranularCellSyncPayload(
                pos,
                0,
                0,
                0,
                false,
                0,
                new long[0]
        );
    }

    /**
     * Factory for an XOR delta sync packet relative to a known previous occupancy bitset.
     */
    public static GranularCellSyncPayload delta(BlockPos pos, GranularCell cell, long[] previousOccupancy) {
        long[] current = cell.occupancy();
        int mask = 0;
        int count = 0;

        for (int i = 0; i < GranularCell.LONGS; i++) {
            if (previousOccupancy == null || current[i] != previousOccupancy[i]) {
                mask |= (1 << i);
                count++;
            }
        }

        long[] changedWords = new long[count];
        int idx = 0;
        for (int i = 0; i < GranularCell.LONGS; i++) {
            if ((mask & (1 << i)) != 0) {
                // If previous exists, we send the new word directly (sparse update)
                changedWords[idx++] = current[i];
            }
        }

        return new GranularCellSyncPayload(
                pos,
                cell.materialId(),
                cell.unitCount(),
                cell.revision(),
                true,
                mask,
                changedWords
        );
    }

    /** StreamCodec for network serialization. */
    public static final StreamCodec<RegistryFriendlyByteBuf, GranularCellSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public GranularCellSyncPayload decode(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int materialId = buf.readVarInt();
            int unitCount = buf.readVarInt();
            int revision = buf.readVarInt();
            boolean isDelta = buf.readBoolean();
            int mask = buf.readUnsignedByte();

            int wordCount = Integer.bitCount(mask);
            long[] words = new long[wordCount];
            for (int i = 0; i < wordCount; i++) {
                words[i] = buf.readLong();
            }

            return new GranularCellSyncPayload(pos, materialId, unitCount, revision, isDelta, mask, words);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, GranularCellSyncPayload p) {
            buf.writeBlockPos(p.pos);
            buf.writeVarInt(p.materialId);
            buf.writeVarInt(p.unitCount);
            buf.writeVarInt(p.revision);
            buf.writeBoolean(p.isDelta);
            buf.writeByte(p.changedWordMask);
            for (long word : p.words) {
                buf.writeLong(word);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
