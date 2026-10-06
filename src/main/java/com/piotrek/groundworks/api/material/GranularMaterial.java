package com.piotrek.groundworks.api.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Describes a granular material that can fill a {@link com.piotrek.groundworks.terrain.cell.GranularCell}.
 *
 * <p>Each material has a unique integer id and a lazy reference to its vanilla source block.
 *
 * @param id                unique integer id (0 = empty/air)
 * @param name              human-readable name, e.g. "dirt"
 * @param blockSupplier     supplier for the vanilla block (lazy to avoid early classloading in unit tests)
 * @param density           kg/m³ (informational, not used in MVP simulation)
 * @param angleOfRepose     degrees (used in Stage 2 relaxation)
 * @param cohesion          0.0–1.0 (used in Stage 2 relaxation)
 * @param slideProbability 0.0–1.0 per tick (used in Stage 2 relaxation)
 * @param visualClusterSizeMicrovoxels stable visual cluster size in the 8x8x8 cell grid
 */
public record GranularMaterial(
        int id,
        String name,
        Supplier<Block> blockSupplier,
        float density,
        float angleOfRepose,
        float cohesion,
        float slideProbability,
        int visualClusterSizeMicrovoxels
) {

    /**
     * Backward-compatible constructor for external material registrations.
     * Fine materials default to one-microvoxel visual sampling.
     */
    public GranularMaterial(
            int id,
            String name,
            Supplier<Block> blockSupplier,
            float density,
            float angleOfRepose,
            float cohesion,
            float slideProbability
    ) {
        this(id, name, blockSupplier, density, angleOfRepose, cohesion, slideProbability, 1);
    }

    /** The empty/air material. No real block. */
    public static final GranularMaterial EMPTY = new GranularMaterial(
            0, "empty", () -> null, 0f, 0f, 0f, 0f, 1
    );

    public Block sourceBlock() {
        return blockSupplier != null ? blockSupplier.get() : null;
    }

    /**
     * Check whether a given BlockState can be converted to this material.
     */
    public boolean matchesBlock(BlockState state) {
        Block block = sourceBlock();
        return block != null && state.is(block);
    }
}
