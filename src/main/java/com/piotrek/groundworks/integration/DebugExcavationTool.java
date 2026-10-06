package com.piotrek.groundworks.integration;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.world.item.Item;

/**
 * Debug excavation shovel for dirt, sand and gravel.
 */
public class DebugExcavationTool extends GranularContainerTool {

    public static final int UNITS_PER_USE = 32;
    public static final int MAX_CAPACITY = GranularContainerTool.MAX_CAPACITY;

    public DebugExcavationTool(Item.Properties properties) {
        super(properties, UNITS_PER_USE);
    }

    @Override
    protected boolean acceptsMaterial(GranularMaterial material) {
        return material == GranularMaterialRegistry.DIRT
                || material == GranularMaterialRegistry.SAND
                || material == GranularMaterialRegistry.GRAVEL;
    }
}
