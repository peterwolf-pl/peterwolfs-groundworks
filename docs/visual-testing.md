# Visual testing

This document is descriptive and procedural. It records the deterministic visual checks for Groundworks.

## Run the suite

1. Run `./gradlew runClientGameTest` from the project root.
2. Find the new images in `visual-tests/current/`.
3. Run `python3 scripts/compare_visuals.py` to compare matching baseline images.
4. Inspect every changed image. A lower pixel difference does not prove better visual quality.

Use a different output directory when required:

```bash
./gradlew runClientGameTest -PgroundworksVisualOutputDir=visual-tests/candidate
python3 scripts/compare_visuals.py \
  --baseline visual-tests/current \
  --current visual-tests/candidate \
  --output visual-tests/candidate-comparison.json
```

The `visual-tests/` directory is ignored by Git. The production JAR does not contain screenshots.

## Deterministic environment

The client test creates a single-player world with consistent settings. It uses noon, clear weather, fixed platforms, and fixed cameras.

The test force-loads chunks from `-4,-4` through `4,4`. It uses spectator mode and a fixed `854×480` capture size.

The test class is:

`src/gametest/java/com/piotrek/groundworks/gametest/GroundworksVisualGameTest.java`

## Scenario results

All results below are from the final `runClientGameTest` pass on 2026-04-03.

| Scenario | Camera | Material amount | Expected result | Actual result | Screenshot | Result |
|---|---|---:|---|---|---|---|
| Single pile, forming | Perspective, `+7,+4,+9` from origin | 4096 dirt units | Local, progressive settling | Material moved from the temporary bulk stack into the local mound | `visual_test_single_pile_forming.png` | PASS |
| Single pile, settled | Same perspective | 4096 dirt units | Sloped stable mound | 14 cells, radius 2 in X and Z, no dirty cells | `visual_test_single_pile_settled.png` | PASS |
| Single pile, top | Top view at Y 190 | 4096 dirt units | No square or plus-shaped bias | Balanced rounded footprint with small deterministic variation | `visual_test_single_pile_top.png` | PASS |
| Continuous pour, middle | Fixed perspective | 16 deposits of 128 sand units | Progressive growth | The mound grows between deposits without one-unit entities | `visual_test_continuous_pour_mid.png` | PASS |
| Continuous pour, settled | Same perspective | 2048 sand units | Stable conserved pile | 2048 units, 10 cells, no dirty cells | `visual_test_continuous_pour_settled.png` | PASS |
| Dirt material | Fixed relative perspective | 2048 units | Cohesive profile | Apex retained 159 units | `visual_test_material_dirt.png` | PASS |
| Sand material | Fixed relative perspective | 2048 units | Lower and more flowing profile | Apex retained 31 units | `visual_test_material_sand.png` | PASS |
| Gravel material | Fixed relative perspective | 2048 units | Steeper profile | Apex retained 162 units | `visual_test_material_gravel.png` | PASS |
| Four-cell boundary | Top view | Four deposits of 1024 dirt units | One connected pile | One conserved 4096-unit surface crosses all four cells | `visual_test_cell_boundary.png` | PASS |
| Chunk boundary | Fixed perspective at X 16 | 3072 dirt units | Same shape as the reference pile | Radius and height match the reference within one cell | `visual_test_chunk_boundary.png` | PASS |
| Chunk reference | Fixed perspective at X 40 | 3072 dirt units | Stable comparison pile | 10 cells and the same extents as the boundary pile | `visual_test_chunk_reference.png` | PASS |
| Slope pour | Side view | 2048 sand units | Center of mass moves downhill | Center Z moved from 42.5 to 43.431 | `visual_test_slope_pour.png` | PASS |
| Fresh excavation | Top view | 192 units removed from 4608 | Visible local depression | The center contains 320 units after removal and early settling | `visual_test_flat_excavation_fresh.png` | PASS |
| Settled excavation | Same top view | 4416 units remain | Smooth local cavity with conservation | 4416 units remain and the dirty queue is empty | `visual_test_flat_excavation_settled.png` | PASS |

## Numeric acceptance checks

The client test fails if one of these conditions is false:

- Each scenario conserves its expected unit count.
- The settled dirty queue contains zero cells.
- The single pile extents differ by no more than one cell.
- The single pile X and Z radii differ by no more than one cell.
- The chunk-boundary radius differs from the reference radius by no more than one cell.
- The slope pile center moves in the downhill `+Z` direction.

The final world contained 28,992 units in 130 active cells. The dirty queue contained zero cells.

## Image comparison

`scripts/compare_visuals.py` calculates these metrics:

- mean absolute RGB difference,
- changed-pixel percentage with a 12-level threshold,
- image-size normalization status,
- amplified difference images.

The saved baseline and final single-pile images used different capture sizes. The script marked both comparisons as normalized.

| Image | Mean RGB difference | Changed pixels |
|---|---:|---:|
| `visual_test_single_pile_forming.png` | 0.157660 | 78.446% |
| `visual_test_single_pile_settled.png` | 0.070489 | 32.499% |

These large changes are intentional. The renderer, camera framing, textures, and pile geometry all changed.

## Manual visual review

The final Minecraft client review found these improvements:

- Dirt, sand, and gravel use recognizable vanilla textures.
- The pile surface hides most 8×8 microvoxel steps.
- Shared cell edges do not show an open crack.
- Chunk-boundary and reference piles have equivalent geometry.
- Top views do not show the old plus-shaped footprint.
- Smooth normals remove the old flat gray debug shading.
- Mesh swaps do not show an empty frame because mesh creation remains synchronous.

Some distant test platforms remain visible in wide perspective images. They do not overlap the measured scene.

## Excavator coverage

The CurseForge profile supplied the initial excavator and Groundworks baseline. The separate excavator project also passed `./gradlew build`.

The Groundworks client test runtime does not include the sibling excavator mod. Therefore, this suite does not automate an excavator entity path.

Code inspection confirms these properties in the sibling project:

- The deposit origin is the bucket lip.
- The server controls material transfer.
- The flow rate is 8 through 32 units per tick.
- The bucket removes only accepted units.
- Particles are separate from material units.

A future cross-mod test fixture must load both development source sets. It must then drive a fixed bucket pose sequence.
