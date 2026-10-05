package com.piotrek.groundworks.gametest;

import com.piotrek.groundworks.api.GroundworksApi;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.storage.GranularWorldStorage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Deterministic rendered regression suite for Groundworks terrain.
 *
 * <p>Run with {@code ./gradlew runClientGameTest}. Screenshots are development
 * artifacts and are intentionally excluded from the production jar.
 */
public final class GroundworksVisualGameTest implements FabricClientGameTest {

    private static final int SURFACE_Y = 180;
    private static final Path SCREENSHOT_DIR = Path.of(
            System.getProperty("groundworks.visualOutputDir", "../visual-tests/current")
    ).toAbsolutePath().normalize();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.restoreDefaultGameOptions();

        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            TestServerContext server = singleplayer.getServer();
            TestServerConnection connection = singleplayer.getConnection();

            configureWorld(server);
            setupScenes(server);
            connection.waitForChunksRender();
            // Let spawn/camera system messages fade before the first baseline.
            context.waitTicks(200);

            runSinglePile(context, connection, server);
            runContinuousPour(context, connection, server);
            runMaterialComparison(context, connection, server);
            runBoundaryComparison(context, connection, server);
            runFourCellBoundary(context, connection, server);
            runSlopePour(context, connection, server);
            runExcavation(context, connection, server);
            runMaterialAwareExcavation(server);

            PileMetrics all = server.computeOnServer(minecraftServer ->
                    measureRegion(GranularWorldStorage.get(minecraftServer.overworld()), 0, 0, 128));
            System.out.println("[GroundworksVisualTest] final-world=" + all.withoutDistribution());
            PerformanceMetrics performance = server.computeOnServer(minecraftServer -> {
                GranularWorldStorage storage = GranularWorldStorage.get(minecraftServer.overworld());
                return new PerformanceMetrics(
                        storage.activeSimulationTicks(),
                        storage.averageSimulationTimeNanos() / 1_000,
                        storage.peakSimulationTimeNanos() / 1_000,
                        storage.totalCellsProcessed(),
                        storage.totalUnitsMoved());
            });
            System.out.println("[GroundworksVisualTest] performance=" + performance);
            assertEquals(0, all.dirtyCells(), "all scenes must stabilize");
        }
    }

    private static void configureWorld(TestServerContext server) {
        server.runCommand("gamerule sendCommandFeedback false");
        server.runCommand("gamerule commandBlockOutput false");
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("gamerule doWeatherCycle false");
        server.runCommand("time set 6000");
        server.runCommand("weather clear");
        server.runCommand("gamemode spectator @a");
        server.runCommand("forceload add -4 -4 4 4");
    }

    private static void setupScenes(TestServerContext server) {
        setupFlat(server, 0, 0, 8);
        setupFlat(server, 24, 0, 8);
        setupFlat(server, -48, 24, 8);
        setupFlat(server, -24, 24, 8);
        setupFlat(server, 0, 24, 8);
        setupFlat(server, 16, -24, 8);
        setupFlat(server, 40, -24, 8);
        setupFlat(server, 48, 0, 8);
        setupFlat(server, 24, 48, 8);
        setupFlat(server, 72, 24, 4);

        // A stepped incline whose surface descends toward +Z.
        server.runCommand("fill -8 168 40 8 174 56 minecraft:stone");
        server.runCommand("fill -8 175 40 8 179 43 minecraft:stone");
        server.runCommand("fill -8 175 44 8 178 46 minecraft:stone");
        server.runCommand("fill -8 175 47 8 177 49 minecraft:stone");
        server.runCommand("fill -8 175 50 8 176 52 minecraft:stone");
        server.runCommand("fill -8 175 53 8 175 56 minecraft:stone");
        server.runCommand("fill -8 180 40 8 195 56 minecraft:air");
    }

    private static void setupFlat(TestServerContext server, int centerX, int centerZ, int radius) {
        server.runCommand("fill " + (centerX - radius) + " 179 " + (centerZ - radius)
                + " " + (centerX + radius) + " 179 " + (centerZ + radius) + " minecraft:stone");
        server.runCommand("fill " + (centerX - radius) + " 180 " + (centerZ - radius)
                + " " + (centerX + radius) + " 195 " + (centerZ + radius) + " minecraft:air");
    }

    private static void runSinglePile(
            ClientGameTestContext context,
            TestServerConnection connection,
            TestServerContext server
    ) {
        setPerspective(server, 0, 0);
        server.runCommand("groundworks deposit dirt 4096 0 180 0");
        // Bulk debug deposits begin as a temporary vertical stack. Capture after
        // the first visible settling phase; continuous-pour has the true mid-flow frame.
        context.waitTicks(20);
        capture(context, connection, "visual_test_single_pile_forming");

        context.waitTicks(180);
        PileMetrics metrics = metrics(server, 0, 0, 7);
        log("single-pile", metrics);
        assertEquals(4096, metrics.totalUnits(), "single pile material conservation");
        assertEquals(0, metrics.dirtyCells(), "single pile stabilization");
        assertBalanced(metrics, 0, 0, 1);
        capture(context, connection, "visual_test_single_pile_settled");

        setTopView(server, 0, 0);
        capture(context, connection, "visual_test_single_pile_top");
    }

    private static void runContinuousPour(
            ClientGameTestContext context,
            TestServerConnection connection,
            TestServerContext server
    ) {
        setPerspective(server, 24, 0);
        for (int i = 0; i < 16; i++) {
            server.runCommand("groundworks deposit sand 128 24 180 0");
            context.waitTicks(2);
            if (i == 7) {
                capture(context, connection, "visual_test_continuous_pour_mid");
            }
        }
        context.waitTicks(120);
        PileMetrics metrics = metrics(server, 24, 0, 7);
        log("continuous-pour", metrics);
        assertEquals(2048, metrics.totalUnits(), "continuous pour material conservation");
        assertEquals(0, metrics.dirtyCells(), "continuous pour stabilization");
        capture(context, connection, "visual_test_continuous_pour_settled");
    }

    private static void runMaterialComparison(
            ClientGameTestContext context,
            TestServerConnection connection,
            TestServerContext server
    ) {
        server.runCommand("groundworks deposit dirt 2048 -48 180 24");
        server.runCommand("groundworks deposit sand 2048 -24 180 24");
        server.runCommand("groundworks deposit gravel 2048 0 180 24");
        context.waitTicks(200);

        PileMetrics dirt = metrics(server, -48, 24, 7);
        PileMetrics sand = metrics(server, -24, 24, 7);
        PileMetrics gravel = metrics(server, 0, 24, 7);
        log("material-dirt", dirt);
        log("material-sand", sand);
        log("material-gravel", gravel);
        assertEquals(2048, dirt.totalUnits(), "dirt material conservation");
        assertEquals(2048, sand.totalUnits(), "sand material conservation");
        assertEquals(2048, gravel.totalUnits(), "gravel material conservation");
        if (sand.radiusX() > gravel.radiusX() + 1 || sand.radiusZ() > gravel.radiusZ() + 1) {
            throw new AssertionError("sand spread is implausibly wider than gravel");
        }

        setPerspective(server, -48, 24);
        capture(context, connection, "visual_test_material_dirt");
        setPerspective(server, -24, 24);
        capture(context, connection, "visual_test_material_sand");
        setPerspective(server, 0, 24);
        capture(context, connection, "visual_test_material_gravel");
    }

    private static void runBoundaryComparison(
            ClientGameTestContext context,
            TestServerConnection connection,
            TestServerContext server
    ) {
        server.runCommand("groundworks deposit dirt 3072 16 180 -24");
        server.runCommand("groundworks deposit dirt 3072 40 180 -24");
        context.waitTicks(200);

        PileMetrics chunkBoundary = metrics(server, 16, -24, 7);
        PileMetrics reference = metrics(server, 40, -24, 7);
        log("chunk-boundary", chunkBoundary);
        log("chunk-reference", reference);
        assertEquals(3072, chunkBoundary.totalUnits(), "chunk boundary material conservation");
        assertEquals(3072, reference.totalUnits(), "reference material conservation");
        if (Math.abs(chunkBoundary.radiusX() - reference.radiusX()) > 1
                || Math.abs(chunkBoundary.radiusZ() - reference.radiusZ()) > 1) {
            throw new AssertionError("chunk boundary changed pile radius: boundary="
                    + chunkBoundary.withoutDistribution() + ", reference=" + reference.withoutDistribution());
        }

        setPerspective(server, 16, -24);
        capture(context, connection, "visual_test_chunk_boundary");
        setPerspective(server, 40, -24);
        capture(context, connection, "visual_test_chunk_reference");
    }

    private static void runFourCellBoundary(
            ClientGameTestContext context,
            TestServerConnection connection,
            TestServerContext server
    ) {
        server.runCommand("groundworks deposit dirt 1024 47 180 -1");
        server.runCommand("groundworks deposit dirt 1024 48 180 -1");
        server.runCommand("groundworks deposit dirt 1024 47 180 0");
        server.runCommand("groundworks deposit dirt 1024 48 180 0");
        context.waitTicks(200);

        PileMetrics metrics = metrics(server, 48, 0, 8);
        log("four-cell-boundary", metrics);
        assertEquals(4096, metrics.totalUnits(), "four-cell boundary material conservation");
        assertEquals(0, metrics.dirtyCells(), "four-cell boundary stabilization");
        setTopView(server, 48, 0);
        capture(context, connection, "visual_test_cell_boundary");
    }

    private static void runSlopePour(
            ClientGameTestContext context,
            TestServerConnection connection,
            TestServerContext server
    ) {
        server.runCommand("groundworks deposit sand 2048 0 180 42");
        context.waitTicks(220);
        PileMetrics metrics = metrics(server, 0, 48, 10);
        log("slope-pour", metrics);
        assertEquals(2048, metrics.totalUnits(), "slope pour material conservation");
        if (metrics.centerZ() <= 42.5) {
            throw new AssertionError("slope material did not move downhill: centerZ=" + metrics.centerZ());
        }
        server.runCommand("teleport @a 8 184 48 90 18");
        capture(context, connection, "visual_test_slope_pour");
    }

    private static void runExcavation(
            ClientGameTestContext context,
            TestServerConnection connection,
            TestServerContext server
    ) {
        for (int x = 23; x <= 25; x++) {
            for (int z = 47; z <= 49; z++) {
                server.runCommand("groundworks deposit dirt 512 " + x + " 180 " + z);
            }
        }
        context.waitTicks(80);
        setTopView(server, 24, 48);
        server.runCommand("groundworks excavate 192 24 180 48");
        context.waitTick();
        capture(context, connection, "visual_test_flat_excavation_fresh");
        context.waitTicks(100);

        PileMetrics metrics = metrics(server, 24, 48, 7);
        log("flat-excavation", metrics);
        assertEquals(9L * 512L - 192L, metrics.totalUnits(), "excavation material conservation");
        assertEquals(0, metrics.dirtyCells(), "excavation stabilization");
        capture(context, connection, "visual_test_flat_excavation_settled");
    }

    private static void runMaterialAwareExcavation(TestServerContext server) {
        BlockPos dirtPos = new BlockPos(71, SURFACE_Y, 24);
        BlockPos sandPos = new BlockPos(72, SURFACE_Y, 24);
        server.runCommand("setblock 71 " + SURFACE_Y + " 24 minecraft:dirt");
        server.runCommand("setblock 72 " + SURFACE_Y + " 24 minecraft:sand");

        var result = server.computeOnServer(minecraftServer -> GroundworksApi.excavateSphere(
                minecraftServer.overworld(),
                new Vec3(72.0D, SURFACE_Y + 0.5D, 24.5D),
                0.40D,
                128,
                GranularMaterialRegistry.DIRT
        ));

        if (!result.success()
                || result.material().id() != GranularMaterialRegistry.DIRT.id()) {
            throw new AssertionError("Material-aware brush did not excavate requested dirt");
        }

        int dirtAfter = server.computeOnServer(
                minecraftServer -> effectiveUnits(minecraftServer.overworld(), dirtPos));
        int sandAfter = server.computeOnServer(
                minecraftServer -> effectiveUnits(minecraftServer.overworld(), sandPos));

        if (dirtAfter >= 512) {
            throw new AssertionError("Material-aware brush did not remove dirt at boundary");
        }
        if (sandAfter != 512) {
            throw new AssertionError(
                    "Material-aware dirt excavation modified adjacent sand: sandUnits=" + sandAfter);
        }
    }

    private static int effectiveUnits(ServerLevel level, BlockPos pos) {
        var cell = GroundworksApi.queryCell(level, pos);
        if (cell != null) {
            return cell.unitCount();
        }

        GranularMaterial material = GroundworksApi.getMaterial(level, pos);
        return material == null || material.id() == 0 ? 0 : 512;
    }

    private static PileMetrics metrics(TestServerContext server, int centerX, int centerZ, int radius) {
        return server.computeOnServer(minecraftServer -> measureRegion(
                GranularWorldStorage.get(minecraftServer.overworld()), centerX, centerZ, radius));
    }

    private static PileMetrics measureRegion(
            GranularWorldStorage storage,
            int centerX,
            int centerZ,
            int radius
    ) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int activeCells = 0;
        long total = 0;
        double weightedX = 0;
        double weightedZ = 0;

        for (var entry : storage.allCells().entrySet()) {
            int units = entry.getValue().unitCount();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (units <= 0 || Math.abs(pos.getX() - centerX) > radius
                    || Math.abs(pos.getZ() - centerZ) > radius) {
                continue;
            }
            activeCells++;
            total += units;
            weightedX += (pos.getX() + 0.5) * units;
            weightedZ += (pos.getZ() + 0.5) * units;
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            maxY = Math.max(maxY, pos.getY());
        }

        long measuredTotal = total;
        String distribution = storage.allCells().entrySet().stream()
                .filter(entry -> entry.getValue().unitCount() > 0)
                .filter(entry -> {
                    BlockPos pos = BlockPos.of(entry.getKey());
                    return Math.abs(pos.getX() - centerX) <= radius
                            && Math.abs(pos.getZ() - centerZ) <= radius;
                })
                .map(entry -> BlockPos.of(entry.getKey()).toShortString()
                        + "=" + entry.getValue().unitCount())
                .sorted()
                .collect(Collectors.joining(","));

        return new PileMetrics(measuredTotal, activeCells, storage.dirtyQueueSize(),
                minX, maxX, minZ, maxZ, maxY,
                measuredTotal == 0 ? 0 : weightedX / measuredTotal,
                measuredTotal == 0 ? 0 : weightedZ / measuredTotal,
                distribution);
    }

    private static void setPerspective(TestServerContext server, int centerX, int centerZ) {
        server.runCommand("teleport @a " + (centerX + 7) + " 184 " + (centerZ + 9) + " 142 18");
    }

    private static void setTopView(TestServerContext server, int centerX, int centerZ) {
        server.runCommand("teleport @a " + (centerX + 0.5) + " 190 " + (centerZ + 0.5) + " 0 90");
    }

    private static void capture(
            ClientGameTestContext context,
            TestServerConnection connection,
            String name
    ) {
        context.waitTicks(3);
        connection.waitForClientboundPackets();
        context.takeScreenshot(TestScreenshotOptions.of(name)
                .disableCounterPrefix()
                .withSize(854, 480)
                .withDestinationDir(SCREENSHOT_DIR));
    }

    private static void assertBalanced(PileMetrics metrics, int centerX, int centerZ, int tolerance) {
        int positiveX = metrics.maxX() - centerX;
        int negativeX = centerX - metrics.minX();
        int positiveZ = metrics.maxZ() - centerZ;
        int negativeZ = centerZ - metrics.minZ();
        if (Math.abs(positiveX - negativeX) > tolerance
                || Math.abs(positiveZ - negativeZ) > tolerance
                || Math.abs(metrics.radiusX() - metrics.radiusZ()) > tolerance) {
            throw new AssertionError("directionally biased pile: " + metrics.withoutDistribution());
        }
    }

    private static void assertEquals(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void log(String scenario, PileMetrics metrics) {
        System.out.println("[GroundworksVisualTest] " + scenario + "=" + metrics);
    }

    private record PerformanceMetrics(
            long activeTicks,
            long averageMicros,
            long peakMicros,
            long cellsProcessed,
            long unitsMoved
    ) {}

    private record PileMetrics(
            long totalUnits,
            int activeCells,
            int dirtyCells,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int maxY,
            double centerX,
            double centerZ,
            String distribution
    ) {
        int radiusX() {
            return maxX >= minX ? maxX - minX : 0;
        }

        int radiusZ() {
            return maxZ >= minZ ? maxZ - minZ : 0;
        }

        String withoutDistribution() {
            return "PileMetrics[totalUnits=" + totalUnits
                    + ", activeCells=" + activeCells
                    + ", dirtyCells=" + dirtyCells
                    + ", minX=" + minX + ", maxX=" + maxX
                    + ", minZ=" + minZ + ", maxZ=" + maxZ
                    + ", maxY=" + maxY
                    + ", centerX=" + centerX + ", centerZ=" + centerZ + "]";
        }
    }
}
