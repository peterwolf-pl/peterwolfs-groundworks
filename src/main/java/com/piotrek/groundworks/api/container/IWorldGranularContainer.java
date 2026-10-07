package com.piotrek.groundworks.api.container;

import com.piotrek.groundworks.api.material.GranularComposition;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A granular container that occupies the world and exposes a physical intake area.
 *
 * <p>Machine mods can transfer material to any implementation without depending
 * on the receiving mod's concrete entity class.</p>
 */
public interface IWorldGranularContainer extends IGranularContainer {

    /**
     * Returns true when material released at the given world-space point should
     * enter, hit, or overflow this container.
     */
    boolean canReceiveAt(Vec3 worldPoint);

    /**
     * Receive material released at a physical world-space point.
     *
     * <p>The default implementation stores as much as possible and leaves the
     * remainder in the source container. Specialized receivers may override this
     * to model overflow. A dump truck can, for example, store material until full
     * and then spill excess to the ground beside its body.</p>
     *
     * @return units consumed from the source, including units deliberately
     *         overflowed into the world by the receiver
     */
    default int receiveMaterialAt(
            ServerLevel level,
            Vec3 worldPoint,
            GranularMaterial material,
            int units
    ) {
        return acceptMaterial(material, units);
    }

    /**
     * Mixture-aware equivalent of {@link #receiveMaterialAt}.
     */
    default int receiveCompositionAt(
            ServerLevel level,
            Vec3 worldPoint,
            GranularComposition composition
    ) {
        if (composition == null || composition.isEmpty()) {
            return 0;
        }

        int consumed = 0;
        int[] counts = composition.toArray();
        for (int id = 1; id < counts.length; id++) {
            if (counts[id] <= 0) continue;
            consumed += receiveMaterialAt(
                    level,
                    worldPoint,
                    GranularMaterialRegistry.byId(id),
                    counts[id]
            );
        }
        return consumed;
    }
}
