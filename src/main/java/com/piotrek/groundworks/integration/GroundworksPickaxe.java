package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import net.minecraft.world.item.Item;

/**
 * Groundworks pickaxe that crushes stone into granular cobblestone.
 *
 * <p>Each use removes exactly one quarter of a full block volume.
 */
public class GroundworksPickaxe extends GranularContainerTool {

    public static final int UNITS_PER_USE = GranularCell.TOTAL_UNITS / 4;
    public static final int MAX_CAPACITY = GranularContainerTool.MAX_CAPACITY;

    public GroundworksPickaxe(Item.Properties properties) {
        super(properties, UNITS_PER_USE);
    }

    @Override
    protected boolean acceptsMaterial(GranularMaterial material) {
        return material == GranularMaterialRegistry.COBBLESTONE;
    }
}
