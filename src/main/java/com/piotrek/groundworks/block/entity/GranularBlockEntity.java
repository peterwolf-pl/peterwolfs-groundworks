package com.piotrek.groundworks.block.entity;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * BlockEntity for {@link com.piotrek.groundworks.block.GranularBlock}.
 *
 * <p>Provides immediate client-side block synchronization via standard Minecraft packets
 * in addition to custom delta streaming.
 */
public class GranularBlockEntity extends BlockEntity {

    private int materialId = 1; // default dirt

    public GranularBlockEntity(BlockPos pos, BlockState state) {
        super(GroundworksMod.GRANULAR_BLOCK_ENTITY, pos, state);
    }

    public int getMaterialId() {
        return materialId;
    }

    public void setMaterialId(int id) {
        this.materialId = id;
        this.setChanged();
    }

    public GranularMaterial getMaterial() {
        return GranularMaterialRegistry.byId(materialId);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("MaterialId", this.materialId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.materialId = input.getInt("MaterialId").orElse(1);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = this.saveWithoutMetadata(registries);
        tag.putInt("MaterialId", this.materialId);
        return tag;
    }
}
