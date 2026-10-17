package com.piotrek.groundworks.api.tree;

import com.peterwolf.realtreefelling.cutting.TreeCutManager;
import com.peterwolf.realtreefelling.detection.DetectedTree;
import com.peterwolf.realtreefelling.detection.TreeDetector;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Public Groundworks Tree and Fallen Log Interaction API.
 *
 * <p>Provides unified machinery and tool access to:
 * <ul>
 *   <li>Directional tree felling via Realistic Tree Felling.</li>
 *   <li>Physics-based pushing and displacement of fallen tree logs.</li>
 *   <li>Cluster querying and break threshold estimation.</li>
 * </ul>
 */
public final class GroundworksTreeApi {

    public static final String RTF_MOD_ID = "peterwolfs_realistic_tree_felling";
    public static final float BASE_BREAK_THRESHOLD = 5.0F;

    public record GroundworksTreeInfo(
            BlockPos base,
            int height,
            Set<BlockPos> allBlocks,
            @Nullable DetectedTree detectedTree
    ) {
        public boolean contains(BlockPos pos) {
            return allBlocks.contains(pos);
        }
    }

    private GroundworksTreeApi() {}

    /**
     * @return true if Realistic Tree Felling is present in the runtime environment.
     */
    public static boolean isRealisticTreeFellingLoaded() {
        return FabricLoader.getInstance().isModLoaded(RTF_MOD_ID);
    }

    /**
     * Checks if the given position is a fallen or horizontal log.
     */
    public static boolean isFallenLog(Level level, BlockPos pos) {
        return FallenLogPhysics.isFallenLog(level, pos);
    }

    /**
     * Finds contiguous fallen log blocks forming a trunk around {@code startPos}.
     */
    public static Set<BlockPos> findFallenLogCluster(Level level, BlockPos startPos, int maxBlocks) {
        return FallenLogPhysics.findLogCluster(level, startPos, maxBlocks);
    }

    /**
     * Pushes a fallen log (and its connected cluster) in {@code direction}.
     */
    public static boolean pushFallenLog(
            ServerLevel level,
            BlockPos contactPos,
            Direction direction,
            @Nullable Entity pusher
    ) {
        return FallenLogPhysics.pushFallenLog(level, contactPos, direction, pusher);
    }

    /**
     * Physically pushes a pre-collected cluster of fallen logs in {@code direction}.
     */
    public static boolean pushFallenLogCluster(
            ServerLevel level,
            Set<BlockPos> cluster,
            Direction direction,
            @Nullable Entity pusher
    ) {
        return FallenLogPhysics.pushCluster(level, cluster, direction, pusher);
    }

    /**
     * Detects a valid standing tree from any contacted trunk/branch log block.
     */
    public static @Nullable GroundworksTreeInfo detectTree(ServerLevel level, BlockPos hitPos) {
        if (!level.getBlockState(hitPos).is(BlockTags.LOGS)) {
            return null;
        }

        try {
            Optional<DetectedTree> detected = TreeDetector.detect(level, hitPos);
            if (detected.isEmpty()) {
                return null;
            }

            DetectedTree dt = detected.get();
            Set<BlockPos> worldBlocks = new HashSet<>();
            for (DetectedTree.TreeBlockData b : dt.blocks()) {
                worldBlocks.add(dt.base().offset(b.relative()));
            }
            return new GroundworksTreeInfo(dt.base(), dt.height(), worldBlocks, dt);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Fells a detected tree in the specified horizontal direction.
     */
    public static boolean fellTree(
            ServerLevel level,
            @Nullable ServerPlayer player,
            GroundworksTreeInfo tree,
            Direction direction
    ) {
        if (direction.getAxis().isVertical()) {
            return false;
        }

        if (tree.detectedTree() != null) {
            try {
                TreeCutManager.startFallWithDirection(level, player, tree.detectedTree(), direction);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /**
     * Calculates required strain threshold to topple a tree based on its height.
     */
    public static float calculateBreakThreshold(int treeHeight) {
        return BASE_BREAK_THRESHOLD + Math.min(5.0F, Math.max(0, treeHeight - 4) * 0.25F);
    }
}
