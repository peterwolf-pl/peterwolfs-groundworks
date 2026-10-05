# Rendering review

This document is descriptive. It records the renderer audit and the implemented changes.

## Original renderer

The original mesher emitted every exposed microvoxel face. It was a voxel-box mesher, not Surface Nets or Marching Cubes.

A full cell produced 384 quads. Each visible step was one eighth of a block high.

The renderer used `RenderTypes.debugQuads()`. It supplied a fixed RGB color but no material texture, UV coordinates, packed light, or useful normals.

The client registered two geometry paths:

- `GranularBlockEntityRenderer`,
- `GranularTerrainRenderer`.

An anchor cell had two render paths. The paths also used different cell revisions in some frames.

The cache used only the local cell revision. A changed neighbor did not invalidate a shared edge.

## Problems found

The initial CurseForge review showed large gray surfaces, hard steps, visible cell edges, and poor transitions to vanilla gravel.

The renderer exposed the `8×8×8` simulation grid directly. The result looked like slabs and small cubes.

The client synchronization defect also made some valid cells appear empty. Duplicate rendering hid part of this defect.

The mesher did not sample neighboring cells. Each block calculated its border independently.

Lighting was flat because the renderer ignored generated normals and world light.

## Selected mesher

The new renderer uses a heightfield fast path over authoritative microvoxel occupancy.

It does not change storage. Each `8×8` column reads its top occupied microvoxel.

A shared corner averages the four surrounding world-space column heights. The lookup crosses block boundaries with floor division.

This method gives both cells the same border vertex. It also preserves deterministic excavation detail.

The heightfield creates these surfaces:

- one smoothed top quad for each visible occupied column,
- side skirts at exposed granular boundaries,
- no hidden underside,
- no top surface under an occupied cell above.

The renderer does not use a scalar-density volume or an isosurface threshold. Surface Nets was not required for the current surface-only terrain.

## Normal calculation

Each grid vertex samples the height to its left, right, north, and south.

The normal uses the local height gradient:

```text
normal = normalize(left - right, 2 × step, north - south)
```

Neighbor cells use the same world samples. Therefore, a shared edge receives matching positions and matching normal inputs.

Side skirts use stable axis normals. They do not attempt smooth shading across a vertical material boundary.

## Textures and UV mapping

The renderer uses vanilla resource identifiers:

```text
textures/block/dirt.png
textures/block/sand.png
textures/block/gravel.png
```

`RenderTypes.entityCutout(texture)` supplies the textured render pass. Resource packs can replace these vanilla textures.

Top surfaces use world-aligned planar UV coordinates. Side surfaces use the dominant vertical axis.

Each vertex also supplies:

- `OverlayTexture.NO_OVERLAY`,
- packed block and sky light,
- a per-vertex normal,
- white color modulation.

The final client screenshots show recognizable dirt, sand, and gravel.

## Border handling

The mesher receives a `CellLookup`. It samples neighboring cells for corner height and surface normals.

This lookup works across these borders:

- granular block borders,
- negative coordinates,
- Minecraft chunk borders,
- a granular-to-empty boundary.

Side skirts close a granular edge against empty space or a vanilla support surface. They prevent a thin open crack.

The four-cell test produced one connected surface. The chunk-boundary pile matched the reference pile geometry.

## Cache strategy

`GranularMeshCache` stores one mesh per world cell.

Authoritative client update events invalidate the changed cell, its same-level `3×3` sampling neighborhood, and the cell directly below it. The renderer no longer hashes 18 neighboring cells for every visible cell in every frame.

An unchanged cell now needs only one local revision comparison for a cache hit. A border update still rebuilds every mesh that sampled the changed column.

The old valid mesh stays in the cache until synchronous replacement. The renderer does not expose a frame with no cached mesh.

The implementation does not use worker threads. It reads the client mirror and builds the replacement on the render path.

Async meshing was not added because the tested scenes did not show a frame gap. A worker implementation requires immutable snapshots.

## Renderer ownership

`GranularTerrainRenderer` is now the only terrain geometry path.

The client no longer registers `GranularBlockEntityRenderer`. The class remains in the source tree for compatibility and possible removal later.

The global renderer is required because relaxation can create a granular cell outside the original anchor location.

The renderer groups cells by dirt, sand, and gravel texture. It culls cells farther than 64 blocks from the camera.

## Geometry measurements

Unit tests record these counts:

| Shape | Original quads | Current quads | Change |
|---|---:|---:|---:|
| Full cell | 384 | 96 | -75% |
| One isolated occupied column | 6 | 5 | -16.7% |

The full-cell count includes 64 top quads and 32 perimeter skirts. It excludes hidden bottom faces.

The border unit test checks bit-identical shared heights. It also checks cache replacement when a neighbor revision changes.

## Visual results

The final Minecraft client pass confirmed these results:

- The old gray debug material is gone.
- Vanilla material textures remain visible on slopes.
- Pile silhouettes are smooth and irregular.
- The top view hides most microvoxel stair steps.
- No open crack appears at the tested cell boundary.
- No special seam appears at X 16.
- Lighting follows the smoothed top normals.
- Duplicate anchor geometry is gone.

The smoothed shape still shows local facets at close range. These facets retain excavation marks and avoid excessive shrinkage.

## Performance

The full-cell quad count decreased by 75 percent. The renderer also removed duplicate block-entity geometry.

Event-driven cache invalidation removes the previous 18-cell neighborhood hash from steady-state rendering. A mesh rebuild still samples neighboring columns and calculates normals. This work costs more per emitted top vertex than the old box path.

The reduced face count and hidden-face removal offset that added work in dense cells.

The visual suite did not show a missing-mesh frame or a client crash. It did not collect GPU frame-time or allocation profiles.

The server simulation profile is separate. A repeated post-optimization run measured 443 microseconds on average for active ticks. Client mesh-cache and collision-cache costs are not included in that timer.

## Known limitations

- The visual surface is a heightfield. It cannot display caves or overhangs inside a cell.
- Side skirts can look vertical at a sharp granular-to-vanilla boundary.
- True triplanar texture blending is not implemented.
- Side UVs can show repetition on a tall temporary bulk stack.
- Mesh construction is synchronous.
- There is no distance LOD.
- Collision uses stepped `8×8` columns, not render triangles.
- The debug command reports statistics but has no normal, transfer-arrow, or dirty-cell world overlay.

## Recommended rendering work

1. Add a small edge blend for vertical granular-to-vanilla transitions.
2. Measure mesh rebuild time and allocation count for a 32×32 active site.
3. Add immutable mesh snapshots before any async experiment.
4. Add optional overlays for normals, dirty cells, and transfer arrows.
5. Add a close-range collision mismatch test.
6. Remove `GranularBlockEntityRenderer` after one compatibility release.
