# Granular simulation review

This document is descriptive. It records the simulation audit and the implemented changes.

## Authoritative representation

Each Minecraft block stores an `8×8×8` occupancy field. One full cell contains 512 integer material units.

The server owns all material movement. Particles do not represent authoritative units.

The storage key is the packed `BlockPos`. The existing local dirty queue remains the simulation scheduler.

## Original algorithm

The original relaxation code used this sequence:

1. Move at most 32 units into the block directly below.
2. Compare total cell counts for north, south, east, and west.
3. Select one steepest neighbor.
4. Move units when the count difference exceeds one material threshold.

The implementation used `Collections.shuffle`. Therefore, two equal server states sometimes selected different transfer directions.

The model used whole-cell counts instead of world surface heights. It did not compare slopes across different Y levels correctly.

`depositWithOverflow` filled the origin cell and then filled cells above it. A large deposit therefore made a vertical tower.

## Problems found

### Critical

Client payload code copied occupancy words and called `recount()`. That method returned a count but did not update `unitCount`.

The client sometimes treated valid synchronized terrain as empty. The block-entity fallback hid part of this defect.

### High

`GranularWorldStorage.tick()` cleared the `SIMULATE` flag after relaxation. A transfer requested another pass, but the tick removed that request.

This defect stopped a pile after one transfer per cell. It produced a tall tower with a small plus-shaped base.

A newly exposed lower cell entered the queue without a `SIMULATE` flag. The scheduler then skipped that cell.

### Visual and logic

The four-neighbor model produced axis bias. The uncontrolled shuffle prevented repeatable tests.

An early eight-neighbor gravity version flattened 4096 units into a `3×3` slab. Unrestricted downward flow removed all metastable slopes.

Relaxation-created cells had no anchor block. They rendered through client storage but had no dynamic collision.

Empty source cells sometimes left invisible anchor blocks. Those blocks caused stale collision.

## Implemented algorithm

`GranularRelaxationEngine` now uses the following process:

1. Search downward for a receiving surface, up to 32 blocks.
2. Move unsupported material toward that surface in bounded transfers.
3. Stop buried cells. The top occupied cell owns surface flow.
4. Inspect north, northeast, east, southeast, south, southwest, west, and northwest.
5. Find the real surface in each neighboring world column.
6. Compare continuous surface heights across Y levels.
7. Select the steepest unstable receiver.
8. Move an integer amount toward the stable slope.
9. Cap one transfer at 32 units.
10. Mark only the source, receiver, and newly exposed source cell dirty.

A cardinal repose threshold uses this expression:

```text
tan(angleOfRepose) × 8 + cohesion × 2
```

A diagonal threshold multiplies this value by `√2`. This adjustment accounts for the longer diagonal distance.

A transfer lowers the source and raises the receiver. The transfer amount targets the stable threshold.

Bulk occupancy operations update complete 64-bit Y layers instead of calling `set()` or `clear()` for every unit. They preserve the original bottom-fill order, top-removal order, unit count, dirty flags, and per-unit revision progression. Column-height caches are invalidated once per bulk operation.

Every transfer checks `removed == added`. A violation throws this exact error:

```text
Material conservation violated: removed=..., added=...
```

## Material parameters

The registry remains the single source for material parameters. This review did not duplicate values in the engine.

| Material | Density | Angle of repose | Cohesion | Slide probability | Observed 2048-unit apex |
|---|---:|---:|---:|---:|---:|
| Dirt | 1500 | 35° | 0.5 | 0.3 | 159 units |
| Sand | 1600 | 30° | 0.1 | 0.6 | 31 units |
| Gravel | 1800 | 38° | 0.2 | 0.4 | 162 units |

The lower sand threshold makes a flatter pile. Dirt and gravel retain more material in the apex.

`slideProbability` remains available in the material record. The deterministic engine does not use a random probability gate.

## Determinism

The engine does not use `Collections.shuffle` or client randomness.

Equal candidates use a deterministic ring start. The hash uses the world seed, packed position, and cell revision.

The same server state and update sequence produces the same direction order. The server remains authoritative.

The test suite checks these properties:

- The neighbor ring contains eight unique directions.
- The tie-break function returns the same value for equal inputs.
- The tie-break result stays in the range 0 through 7.
- A stable difference produces no transfer.
- A transfer does not exceed 32 units.
- Every visual scenario conserves its expected mass.

## Scheduler and locality

The simulation still uses `GranularWorldStorage.dirtyQueue`. It does not scan all stored terrain.

The tick consumes the old `SIMULATE` request before relaxation. A transfer can then add a new request safely.

The default limits remain:

```text
maxCellsPerTick = 64
maxSimulationMicros = 1000
```

A tick checks both limits. One cell operation can finish after the time limit, so the measured peak can exceed 1000 microseconds.

## Measured results

The final client GameTest executed seven scenario groups in one deterministic world.

| Metric | Result |
|---|---:|
| Authoritative units | 28,992 |
| Active cells | 130 |
| Dirty cells after settling | 0 |
| Active simulation ticks | 43–44 |
| Average active simulation time | 443–623 µs |
| Peak active simulation time | 1,469–4,387 µs |
| Cells processed | 1,519 |
| Units moved | 18,374 |

Two post-optimization client runs produced the ranges above. One run contained a 4,387 µs outlier; the repeat completed at 443 µs average and 1,469 µs peak. The same scenes, cell count, moved-unit count, final geometry, and zero dirty queue were preserved.

The earlier pre-optimization reference run measured 413 µs average and 1,396 µs peak. This end-to-end test is too noisy to claim a server-time speedup from bulk occupancy alone. The mesh and collision optimizations target client steady-state work and are not represented by this server simulation timer.

The original code before this review had timing counters but no cumulative average or peak. A valid numerical value for the original tower algorithm is not available.

The original visual baseline did show stalled work. A 4096-unit deposit remained a tall tower after 200 ticks.

The final 4096-unit pile stabilized with these values:

```text
activeCells = 14
dirtyCells = 0
minX = -2
maxX = 2
minZ = -2
maxZ = 2
maxY = 181
centerX = 0.502197265625
centerZ = 0.501220703125
```

The chunk-boundary and reference piles each used 10 cells. Their X and Z extents were identical.

The slope test moved its center Z from 42.5 to 43.431. Positive Z was the downhill direction.

## Collision and anchors

`GranularWorldStorage.putCell()` now places an invisible `GRANULAR_BLOCK` anchor for each nonempty receiver.

The anchor uses the existing `8×8` stepped collision shape. The authoritative cell remains the collision source.

The storage tick removes the anchor when a cell becomes empty. This action prevents invisible collision after a transfer.

Collision shapes are cached by `GranularCell` identity and revision. Repeated movement and selection queries reuse the same `VoxelShape` until occupancy changes.

The cache uses weak cell keys, so removed cells do not remain reachable only because of collision data. The smoothed render surface and stepped collision are not identical. Their maximum local difference is approximately one microvoxel step.

## Excavator integration

The sibling excavator project deposits from `BucketPose.lip()`. Its flow rate ranges from 8 through 32 units per tick.

The controller removes only accepted units from the bucket. Rejected units remain in the bucket.

The sibling project passed its full Gradle build after the Groundworks changes.

The controller has no angle hysteresis. A bucket near the pour threshold can still alternate between start and stop.

## Known limitations

- `depositWithOverflow` still accepts a bulk request by filling vertical cells before relaxation.
- A large debug deposit can form a short-lived stack before local settling.
- The engine applies one transfer intent at a time. It does not use a global two-phase intent buffer.
- The deterministic revision order can cause small local asymmetry. The measured center error stayed below 0.01 blocks.
- Surface lookup scans at most 32 blocks downward.
- Different granular materials do not mix inside one cell.
- Adaptive TPS-based limits are not implemented.
- Explicit A-to-B-to-A oscillation counters are not implemented. Final dirty queues reached zero in all test scenes.

## Recommended simulation work

1. Add a cross-mod bucket pose GameTest with both development source sets.
2. Add pour-angle hysteresis in the excavator controller.
3. Add a velocity hint to the deposit API only when a caller can supply reliable motion.
4. Record transfer arrows and oscillation pairs in a development overlay.
5. Compare the current scheduler with a small regional two-phase intent buffer.
6. Test 10×10 and 32×32 active sites under continuous multi-source dumping.
