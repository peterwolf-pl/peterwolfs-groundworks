package com.piotrek.groundworks.api.container;

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
     * enter this container.
     */
    boolean canReceiveAt(Vec3 worldPoint);
}
