package com.piotrek.groundworks.networking;

import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles sending authoritative granular updates from the server to tracking clients.
 */
public final class GranularSyncHandler {

    private GranularSyncHandler() {}

    /**
     * Send a cell update (full or delta) to all players tracking the cell's chunk.
     */
    public static void sendCellUpdate(ServerLevel level, BlockPos pos, GranularCell cell) {
        GranularCellSyncPayload payload = cell.isEmpty()
                ? GranularCellSyncPayload.remove(pos)
                : GranularCellSyncPayload.full(pos, cell);

        for (ServerPlayer player : PlayerLookup.tracking(level, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * Send full resync for a specific player (e.g. upon entering view distance).
     */
    public static void sendFullResync(ServerPlayer player, BlockPos pos, GranularCell cell) {
        GranularCellSyncPayload payload = GranularCellSyncPayload.full(pos, cell);
        ServerPlayNetworking.send(player, payload);
    }
}
