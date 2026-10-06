package com.piotrek.groundworks.networking;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client payload carrying occupancy plus exact material composition.
 */
public record GranularCellSyncPayload(
        BlockPos pos,
        int materialId,
        int unitCount,
        int revision,
        boolean isDelta,
        int changedWordMask,
        long[] words,
        int[] compositionUnits
) implements CustomPacketPayload {

    public static final Type<GranularCellSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GroundworksMod.MOD_ID, "cell_sync")
    );

    public static GranularCellSyncPayload full(BlockPos pos, GranularCell cell) {
        return new GranularCellSyncPayload(
                pos,
                cell.materialId(),
                cell.unitCount(),
                cell.revision(),
                false,
                0xFF,
                cell.occupancy().clone(),
                cell.compositionUnits()
        );
    }

    public static GranularCellSyncPayload remove(BlockPos pos) {
        return new GranularCellSyncPayload(
                pos, 0, 0, 0, false, 0, new long[0], new int[0]
        );
    }

    public static GranularCellSyncPayload delta(
            BlockPos pos,
            GranularCell cell,
            long[] previousOccupancy
    ) {
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
                changedWords,
                cell.compositionUnits()
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, GranularCellSyncPayload> CODEC =
            new StreamCodec<>() {
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

            int compositionLength = buf.readVarInt();
            int[] compositionUnits = new int[compositionLength];
            for (int i = 0; i < compositionLength; i++) {
                compositionUnits[i] = buf.readVarInt();
            }

            return new GranularCellSyncPayload(
                    pos, materialId, unitCount, revision, isDelta, mask, words, compositionUnits);
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

            buf.writeVarInt(p.compositionUnits.length);
            for (int units : p.compositionUnits) {
                buf.writeVarInt(units);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
