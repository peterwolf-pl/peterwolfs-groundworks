package com.piotrek.groundworks.api.material;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of all known granular materials.
 *
 * <p>Materials are registered at mod init and looked up by id or by vanilla BlockState.
 * Id 0 is reserved for {@link GranularMaterial#EMPTY}.
 */
public final class GranularMaterialRegistry {

    private static final List<GranularMaterial> BY_ID = new ArrayList<>();
    private static boolean bootstrapped = false;

    // Well-known material ids (assigned during bootstrap)
    public static GranularMaterial DIRT;
    public static GranularMaterial SAND;
    public static GranularMaterial GRAVEL;
    public static GranularMaterial COBBLESTONE;

    private GranularMaterialRegistry() {}

    /**
     * Register the built-in Groundworks materials.
     * Called once from mod init.
     */
    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;

        // Id 0 = empty
        BY_ID.add(GranularMaterial.EMPTY);

        DIRT = register(new GranularMaterial(
                1, "dirt", () -> Blocks.DIRT,
                1500f, 35f, 0.5f, 0.3f
        ));
        SAND = register(new GranularMaterial(
                2, "sand", () -> Blocks.SAND,
                1600f, 30f, 0.1f, 0.6f
        ));
        GRAVEL = register(new GranularMaterial(
                3, "gravel", () -> Blocks.GRAVEL,
                1800f, 38f, 0.2f, 0.4f
        ));
        COBBLESTONE = register(new GranularMaterial(
                4, "cobblestone", () -> Blocks.COBBLESTONE,
                2200f, 42f, 0.35f, 0.2f
        ));
    }

    private static GranularMaterial register(GranularMaterial material) {
        if (material.id() != BY_ID.size()) {
            throw new IllegalArgumentException(
                    "Material id mismatch: expected " + BY_ID.size() + ", got " + material.id());
        }
        BY_ID.add(material);
        return material;
    }

    public static GranularMaterial byId(int id) {
        if (id < 0 || id >= BY_ID.size()) return GranularMaterial.EMPTY;
        return BY_ID.get(id);
    }

    @Nullable
    public static GranularMaterial forBlockState(BlockState state) {
        // Stone is crushed into loose cobblestone instead of producing a dropped item.
        if (COBBLESTONE != null && state.is(Blocks.STONE)) {
            return COBBLESTONE;
        }

        for (int i = 1; i < BY_ID.size(); i++) {
            if (BY_ID.get(i).matchesBlock(state)) {
                return BY_ID.get(i);
            }
        }

        // Grass is the exposed surface state of ordinary dirt. Excavation and
        // deposition must not treat its decorative top as another material.
        if (DIRT != null && state.is(Blocks.GRASS_BLOCK)) {
            return DIRT;
        }
        return null;
    }

    @Nullable
    public static GranularMaterial byName(String name) {
        for (GranularMaterial mat : BY_ID) {
            if (mat.name().equals(name)) return mat;
        }
        return null;
    }

    public static List<GranularMaterial> all() {
        return Collections.unmodifiableList(BY_ID);
    }

    public static int count() {
        return BY_ID.size();
    }
}
