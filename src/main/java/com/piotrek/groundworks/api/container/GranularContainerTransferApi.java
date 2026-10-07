package com.piotrek.groundworks.api.container;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Shared lookup helpers for machine-to-machine granular transfers.
 */
public final class GranularContainerTransferApi {

    private GranularContainerTransferApi() {}

    /**
     * Find the nearest world container whose intake contains the supplied point.
     *
     * @param level server world
     * @param worldPoint exact release point, usually a bucket lip
     * @param source entity performing the transfer, excluded from candidates
     * @param searchRadius broad-phase search radius in blocks
     */
    @Nullable
    public static IWorldGranularContainer findReceiver(
            ServerLevel level,
            Vec3 worldPoint,
            @Nullable Entity source,
            double searchRadius
    ) {
        double radius = Math.max(0.5D, searchRadius);
        AABB area = new AABB(
                worldPoint.x - radius,
                worldPoint.y - radius,
                worldPoint.z - radius,
                worldPoint.x + radius,
                worldPoint.y + radius,
                worldPoint.z + radius
        );

        return level.getEntitiesOfClass(
                        Entity.class,
                        area,
                        entity -> entity != source
                                && entity instanceof IWorldGranularContainer container
                                && container.canReceiveAt(worldPoint)
                )
                .stream()
                .min(Comparator.comparingDouble(entity ->
                        entity.distanceToSqr(
                                worldPoint.x,
                                worldPoint.y,
                                worldPoint.z
                        )
                ))
                .map(entity -> (IWorldGranularContainer) entity)
                .orElse(null);
    }
}
