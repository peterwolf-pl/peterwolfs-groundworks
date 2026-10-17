package com.piotrek.groundworks.api.tree;

import com.peterwolf.realtreefelling.persistence.FallenLogSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Authoritative physics and movement simulation for fallen tree logs.
 *
 * <p>Handles:
 * <ul>
 *   <li>Detection of horizontal / fallen logs vs vertical standing stumps.</li>
 *   <li>Cluster discovery: finding contiguous logs belonging to a fallen trunk.</li>
 *   <li>Physics-based pushing: directional sliding, step-climbing, and gravity drop down ruts/ledges.</li>
 *   <li>Preservation of wood block states and {@link FallenLogSavedData} tracking.</li>
 *   <li>Acoustic and particle feedback for heavy wood sliding across terrain.</li>
 * </ul>
 */
public final class FallenLogPhysics {

    public static final int DEFAULT_MAX_CLUSTER_BLOCKS = 32;
    public static final int MAX_GRAVITY_DROP_BLOCKS = 6;

    private FallenLogPhysics() {}

    /**
     * Determines whether the block at {@code pos} is a fallen or horizontal log.
     */
    public static boolean isFallenLog(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.LOGS)) {
            return false;
        }

        // 1. Check if the log is placed horizontally (axis X or Z)
        if (state.hasProperty(RotatedPillarBlock.AXIS)) {
            Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
            if (axis.isHorizontal()) {
                return true;
            }
        }

        // 2. Check if tracked in Realistic Tree Felling saved data
        if (level instanceof ServerLevel serverLevel) {
            try {
                FallenLogSavedData data = FallenLogSavedData.get(serverLevel);
                if (data.find(serverLevel.dimension(), pos).isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    /**
     * Finds contiguous fallen log blocks forming a trunk or pile around {@code startPos}.
     */
    public static Set<BlockPos> findLogCluster(Level level, BlockPos startPos, int maxBlocks) {
        if (!isFallenLog(level, startPos)) {
            return Collections.emptySet();
        }

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        visited.add(startPos.immutable());
        queue.add(startPos.immutable());

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            BlockPos current = queue.poll();

            // Check 6 direct cardinal neighbors
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && isFallenLog(level, neighbor)) {
                    visited.add(neighbor.immutable());
                    queue.add(neighbor.immutable());
                    if (visited.size() >= maxBlocks) {
                        break;
                    }
                }
            }

            if (visited.size() >= maxBlocks) {
                break;
            }

            // Check 8 diagonal neighbors at y-1 and y+1 (sloping terrain logs)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy : new int[]{-1, 1}) {
                        BlockPos diag = current.offset(dx, dy, dz);
                        if (!visited.contains(diag) && isFallenLog(level, diag)) {
                            visited.add(diag.immutable());
                            queue.add(diag.immutable());
                            if (visited.size() >= maxBlocks) {
                                break;
                            }
                        }
                    }
                    if (visited.size() >= maxBlocks) break;
                }
                if (visited.size() >= maxBlocks) break;
            }
        }

        return visited;
    }

    /**
     * Pushes a single fallen log (and its connected cluster) in {@code pushDirection}.
     *
     * @return true if the cluster was successfully shifted in the world.
     */
    public static boolean pushFallenLog(
            ServerLevel level,
            BlockPos contactPos,
            Direction pushDirection,
            @Nullable Entity pusher
    ) {
        Set<BlockPos> cluster = findLogCluster(level, contactPos, DEFAULT_MAX_CLUSTER_BLOCKS);
        if (cluster.isEmpty()) {
            return false;
        }
        return pushCluster(level, cluster, pushDirection, pusher);
    }

    /**
     * Physically pushes a cluster of fallen logs in the given horizontal direction.
     * Supports sliding along flat ground, climbing 1-block steps, and falling down ruts/ledges.
     */
    public static boolean pushCluster(
            ServerLevel level,
            Set<BlockPos> cluster,
            Direction pushDirection,
            @Nullable Entity pusher
    ) {
        if (cluster.isEmpty() || pushDirection.getAxis().isVertical()) {
            return false;
        }

        Map<BlockPos, BlockPos> sourceToTarget = new LinkedHashMap<>();
        Set<BlockPos> plannedTargets = new HashSet<>();

        // 1. Resolve destination for every block in the cluster
        for (BlockPos source : cluster) {
            BlockPos forward = source.relative(pushDirection);

            BlockPos resolved;
            if (cluster.contains(forward)) {
                // Forward position is occupied by another log in the same cluster that is also moving
                resolved = forward;
            } else {
                BlockState forwardState = level.getBlockState(forward);

                if (isPassable(forwardState)) {
                    // Check gravity: drop down if unsupported below
                    BlockPos drop = forward;
                    int dropCount = 0;
                    while (dropCount < MAX_GRAVITY_DROP_BLOCKS && drop.getY() > level.getMinY()) {
                        BlockPos below = drop.below();
                        BlockState belowState = level.getBlockState(below);
                        if (cluster.contains(below) || !isPassable(belowState)) {
                            break;
                        }
                        drop = below;
                        dropCount++;
                    }
                    resolved = drop;
                } else {
                    // Forward is solid: check for 1-block step climb
                    BlockPos stepUp = forward.above();
                    BlockPos sourceAbove = source.above();
                    BlockState stepUpState = level.getBlockState(stepUp);
                    BlockState sourceAboveState = level.getBlockState(sourceAbove);

                    if (isPassable(stepUpState) && isPassable(sourceAboveState)) {
                        resolved = stepUp;
                    } else {
                        // Obstructed by unyielding obstacle (2+ solid blocks, stone, bedrock, etc.)
                        return false;
                    }
                }
            }

            if (plannedTargets.contains(resolved)) {
                // Two logs cannot collapse into the same destination block
                return false;
            }

            plannedTargets.add(resolved);
            sourceToTarget.put(source, resolved);
        }

        // 2. Read existing states and saved data
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, Integer> hitData = new LinkedHashMap<>();
        FallenLogSavedData savedData = null;
        ResourceKey<Level> dim = level.dimension();
        try {
            savedData = FallenLogSavedData.get(level);
        } catch (Throwable ignored) {
        }

        for (BlockPos source : cluster) {
            states.put(source, level.getBlockState(source));
            if (savedData != null) {
                Optional<FallenLogSavedData.FallenLog> found = savedData.find(dim, source);
                found.ifPresent(fl -> hitData.put(source, fl.requiredHits()));
            }
        }

        // 3. Clear old positions in the world and saved data
        for (BlockPos source : cluster) {
            level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (savedData != null) {
                savedData.remove(dim, source);
            }
        }

        // 4. Write new positions into the world and restore saved data
        for (Map.Entry<BlockPos, BlockPos> entry : sourceToTarget.entrySet()) {
            BlockPos source = entry.getKey();
            BlockPos target = entry.getValue();
            BlockState state = states.get(source);

            level.setBlock(target, state, Block.UPDATE_ALL);

            if (savedData != null && hitData.containsKey(source)) {
                savedData.register(dim, target, hitData.get(source));
            }
        }

        // 5. Sound and particle effects for heavy log movement
        BlockPos soundPos = sourceToTarget.values().iterator().next();
        level.playSound(
                null,
                soundPos,
                SoundEvents.WOOD_STEP,
                SoundSource.BLOCKS,
                1.4F,
                0.70F + level.getRandom().nextFloat() * 0.15F
        );
        level.playSound(
                null,
                soundPos,
                SoundEvents.WOOD_HIT,
                SoundSource.BLOCKS,
                0.9F,
                0.85F
        );

        for (Map.Entry<BlockPos, BlockPos> entry : sourceToTarget.entrySet()) {
            BlockPos target = entry.getValue();
            BlockState state = states.get(entry.getKey());
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    target.getX() + 0.5D,
                    target.getY() + 0.5D,
                    target.getZ() + 0.5D,
                    8,
                    0.25D, 0.25D, 0.25D,
                    0.05D
            );
        }

        return true;
    }

    private static boolean isPassable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
