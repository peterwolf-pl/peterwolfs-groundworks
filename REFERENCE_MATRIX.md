# Groundworks research reference matrix

Research snapshot: 2026-10-04

This file is a companion to `SKILL.md`.

## Verified public repositories

### Chisels & Bits

Repository coordinate:
`ChiselsAndBits/Chisels-and-Bits`

Observed branch:
`version/26.1`

Useful paths:
- `api/src/main/java/mod/chiselsandbits/api/block/storage/StateEntryStorage.java`
- `api/src/main/java/mod/chiselsandbits/api/block/storage/StateEntryPalette.java`
- `api/src/main/java/mod/chiselsandbits/api/multistate/StateEntrySize.java`

Research value:
sub-block storage, palettes, persistence, versioning, synchronization.

Observed GitHub repository license:
MIT.

Note:
CurseForge metadata for some releases may show a different license. Always trust the
LICENSE file in the exact source checkout you actually use.

### NoCubes

Repository coordinate:
`Cadiboo/NoCubes`

Useful paths:
- `common/src/main/java/io/github/cadiboo/nocubes/mesh/SurfaceNets.java`
- `common/src/main/java/io/github/cadiboo/nocubes/mesh/MarchingCubes.java`
- `common/src/main/java/io/github/cadiboo/nocubes/mesh/SDFMesher.java`
- `common/src/main/java/io/github/cadiboo/nocubes/mesh/Mesher.java`

Research value:
Surface Nets, Marching Cubes, terrain rendering hooks, custom collisions, dirty mesh concepts.

Observed GitHub/Modrinth source license:
LGPL-3.0.

### LittleTiles

Repository coordinate:
`CreativeMD/LittleTiles`

Observed branch:
`1.21`

Useful paths:
- `src/main/java/team/creative/littletiles/common/grid/LittleGrid.java`
- `src/main/java/team/creative/littletiles/common/math/box/LittleBox.java`
- `src/main/java/team/creative/littletiles/common/math/box/LittleBoxCombiner.java`
- `src/main/java/team/creative/littletiles/common/math/box/collection/`

Research value:
integer sub-block grids, compact box geometry, merging and partitioning.

Observed GitHub repository license:
LGPL-3.0.

Note:
CurseForge metadata may display another LGPL version for published artifacts.
Verify the source commit license before adapting code.

### Sand Physics

Repository coordinate:
`multyfora/sandphysis-1.21.1`

Useful paths:
- `src/main/java/net/multyfora/sandphysics/Sandphysis.java`
- `src/main/java/net/multyfora/sandphysics/Config.java`
- `src/main/java/net/multyfora/sandphysics/SubLevelAutoDisassemblyManager.java`
- `src/main/java/net/multyfora/sandphysics/mixin/FallingBlockEntityFallMixin.java`

Research value:
small physics integration, Mixins, lifecycle handling.

Observed source license:
MIT.

## Behavior-only references

### Physics Mod

Repository coordinate:
`haubna/PhysicsMod`

Important:
The public GitHub repository found during research contains API/docs/examples and shader
information, not a verified copy of the full production mod source.

CurseForge license:
All Rights Reserved.

Useful information:
- Minecraft 26.3 support exists
- Fabric 26.3 build exists
- custom shader/physics integration is viable on the target generation

Classification:
BEHAVIOR_ONLY except explicitly licensed example/API files.

### Falling Sand

Catalog coordinate:
`Modrinth: falling-sand`

Observed license:
All Rights Reserved.

Useful behavior:
- server-side
- avoids falling-block entities
- moves blocks down
- diagonal sliding
- configurable slide chance
- landslides/cave-ins

No verified public source repository found during the research pass.

Classification:
BEHAVIOR_ONLY.

### Better Dirt Mining - BDM

Catalog coordinate:
`Modrinth/CurseForge: Better Dirt Mining - BDM`

Creator:
Janexi

Observed catalog license:
MIT.

Useful behavior:
- layer-by-layer mining
- partial block terrain
- layer gravity
- upper layer changes when support is removed

No verified source repository found during the research pass.

Classification:
READ_AND_REIMPLEMENT from documented behavior only until source+LICENSE is verified.

### Block Layering

Catalog coordinate:
`CurseForge: Block Layering`

Creator:
Lothrazar

Observed catalog license:
All Rights Reserved.

Useful behavior:
- snow-like layers for dirt/sand/gravel/clay/etc.
- reuses source block textures
- resource-pack-friendly concept

No verified public source repository found during the research pass.

Classification:
BEHAVIOR_ONLY.

## Recommended repo checkout folder

Keep references outside the Groundworks production source tree:

```text
research/
  external/
    chisels-and-bits/
    nocubes/
    littletiles/
    sandphysics/
  notes/
    third-party-research.md
    license-audit.md
```

Do not vendor these repositories into the shipped mod.

## Required license audit record

For every code adaptation record:

```text
Project:
Repository:
Branch:
Commit:
LICENSE identifier:
Files inspected:
Files adapted:
What was copied:
What was reimplemented:
Attribution required:
Distribution obligations:
Reviewer:
Date:
```
