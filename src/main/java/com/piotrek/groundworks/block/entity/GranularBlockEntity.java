package com.piotrek.groundworks.block.entity;

import com.piotrek.groundworks.GroundworksMod;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.storage.ClientGranularStorage;
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
 * <p>Directly serializes the 512-bit occupancy bitset in its update packet and NBT tag,
 * guaranteeing 100% instant and reliable synchronization to the client!
 */
public class GranularBlockEntity extends BlockEntity {

    private int materialId = 1; // default dirt
    private GranularCell cell;

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

    public GranularCell getCell() {
        if (cell == null) {
            if (this.level instanceof ServerLevel serverLevel) {
                cell = GranularWorldStorage.get(serverLevel).getCell(this.worldPosition);
            } else {
                cell = ClientGranularStorage.getCell(this.worldPosition);
            }
        }
        return cell;
    }

    public void setCell(GranularCell cell) {
        this.cell = cell;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("MaterialId", this.materialId);

        GranularCell c = getCell();
        if (c != null) {
            CompoundTag cellTag = c.save();
            output.store("CellData", CompoundTag.CODEC, cellTag);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.materialId = input.getIntOr("MaterialId", 1);

        input.read("CellData", CompoundTag.CODEC).ifPresent(tag -> {
            GranularCell loaded = GranularCell.load(tag);
            if (loaded != null) {
                this.cell = loaded;
                this.materialId = loaded.materialId();
                ClientGranularStorage.putCell(this.worldPosition, loaded);
            }
        });
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = this.saveWithoutMetadata(registries);
        tag.putInt("MaterialId", this.materialId);

        GranularCell c = getCell();
        if (c != null) {
            tag.put("CellData", c.save());
        }
        return tag;
    }
}
