---
name: peterwolfs-groundworks-source-mix
description: >
  Research and implementation skill for Peterwolf's Groundworks / Granular Terrain
  for Minecraft Java 26.3 Fabric. Use this skill when designing, implementing,
  reviewing, or optimizing deformable granular terrain, partial block excavation,
  material-volume conservation, piles/slopes, buckets, wheelbarrows, conveyors,
  excavators, or smooth terrain meshing. It synthesizes implementation patterns
  from existing Minecraft mods without blindly copying incompatible or restricted code.
---

# Peterwolf's Groundworks - source-mix skill

## Mission

Build an original Fabric 26.3 granular-terrain engine that can convert selected
vanilla 1x1x1 blocks into deformable material while preserving material volume.

Target behavior:

- A full vanilla block represents exactly 1.000 m3 of material.
- A converted block can be partially excavated.
- Excavated material can be stored in a tool or vehicle container as volume.
- Dumped material returns to the terrain.
- Loose material relaxes into stable piles.
- The terrain can be rendered smoother than vanilla cubes.
- The server owns the authoritative simulation.
- The implementation must remain performant enough for construction machines.

The intended core is not a collection of falling-block entities. It is a
server-authoritative volumetric terrain system with custom rendering.

## Critical legal rule

Before copying any code from any external repository:

1. Read the LICENSE file in the exact branch and commit being inspected.
2. Record the repository, branch, commit SHA, license, and copied/adapted files.
3. Do not assume the license shown by Modrinth or CurseForge matches the current
   Git repository.
4. If a project is All Rights Reserved, has no source license, or the source
   repository cannot be verified, use it only as behavioral inspiration.
5. Prefer reimplementation from documented behavior and algorithms rather than
   source copying.
6. Never decompile a closed-source JAR to obtain code for Groundworks.

A public repository does not automatically mean its code can be copied.

## Reference projects

### 1. Chisels & Bits

Repository:
`ChiselsAndBits/Chisels-and-Bits`

Observed active branch during research:
`version/26.1`

Observed repository license:
MIT

Important modules and files:

- `api/src/main/java/mod/chiselsandbits/api/block/storage/StateEntryStorage.java`
- `api/src/main/java/mod/chiselsandbits/api/block/storage/StateEntryPalette.java`
- `api/src/main/java/mod/chiselsandbits/api/multistate/StateEntrySize.java`
- multistate accessor and mutator APIs
- storage/versioning code
- compressed block-state persistence patterns

What to learn:

- representing many sub-block states inside one vanilla block position
- palette-based storage
- compact state indices
- serialization and versioning
- mutating a sub-block volume without replacing the whole Minecraft block
- client/server synchronization of detailed block data

Do not copy the entire Chisels & Bits architecture. Groundworks has a simpler
problem because a granular cell normally contains one material plus occupancy
or density, not an arbitrary mixture of hundreds of decorative block states.

Recommended Groundworks adaptation:

- one `GranularMaterialId` per cell for MVP
- 512 logical units per full block
- occupancy stored as a 512-bit bitset or packed density field
- upgrade to palette storage only if mixed materials become a real requirement
- explicit storage format version from day one

### 2. NoCubes

Repository:
`Cadiboo/NoCubes`

Observed repository license:
LGPL-3.0

Most relevant files:

- `common/src/main/java/io/github/cadiboo/nocubes/mesh/Mesher.java`
- `common/src/main/java/io/github/cadiboo/nocubes/mesh/SDFMesher.java`
- `common/src/main/java/io/github/cadiboo/nocubes/mesh/SurfaceNets.java`
- `common/src/main/java/io/github/cadiboo/nocubes/mesh/MarchingCubes.java`
- NoCubes README technical sections on rendering and collisions

Key lessons:

- intercept or augment terrain rendering rather than creating thousands of entities
- generate a mesh from a voxel/density field
- Surface Nets is a strong default for smooth terrain
- use Marching Cubes as a reference/diagnostic option, not automatically as the default
- render meshes should be cached and rebuilt only for dirty regions
- collision geometry can be generated separately from visual geometry

Important warning:

NoCubes documents that recalculating collision meshes repeatedly for overlapping
areas is wasteful. Groundworks should avoid this by caching collision data per
dirty granular region.

Recommended Groundworks adaptation:

- physics grid and visual mesh must be separate systems
- only changed granular regions invalidate mesh caches
- build mesh CPU data outside the render hot path
- upload GPU buffers on the correct client render path
- do not rebuild full vanilla chunks for a single changed microcell if avoidable

### 3. LittleTiles

Repository:
`CreativeMD/LittleTiles`

Observed branch during research:
`1.21`

Observed repository license:
LGPL-3.0

Relevant files:

- `src/main/java/team/creative/littletiles/common/grid/LittleGrid.java`
- `src/main/java/team/creative/littletiles/common/grid/IGridBased.java`
- `src/main/java/team/creative/littletiles/common/math/box/LittleBox.java`
- `src/main/java/team/creative/littletiles/common/math/box/LittleBoxCombiner.java`
- `src/main/java/team/creative/littletiles/common/math/box/collection/*`

Key lessons:

- integer sub-block grids are robust
- keep geometry in integer coordinates as long as possible
- explicitly associate geometry with grid resolution
- merge adjacent small regions where possible
- avoid float-heavy storage for authoritative simulation

Recommended Groundworks adaptation:

- resolution constant for MVP: 8 subdivisions per block axis
- use integer coordinates 0..7 inside a cell
- convert to float only for rendering
- use packed integer indexing:
  `index = x + 8 * (z + 8 * y)` or another fixed documented order
- never allow client floating-point rendering coordinates to become authoritative material data

### 4. Sand Physics

Repository:
`multyfora/sandphysis-1.21.1`

Observed repository license:
MIT

Relevant source tree:

- `src/main/java/net/multyfora/sandphysics/Sandphysis.java`
- `src/main/java/net/multyfora/sandphysics/Config.java`
- `src/main/java/net/multyfora/sandphysics/SubLevelAutoDisassemblyManager.java`
- `src/main/java/net/multyfora/sandphysics/mixin/FallingBlockEntityFallMixin.java`

This project uses Sable's Physics to turn vanilla gravity-related behavior into
physical objects.

What to learn:

- narrow Mixin hooks around vanilla falling-block behavior
- lifecycle management for temporary physical objects
- configuration boundaries
- separation between Minecraft block state and external physics representation

What not to adopt for Groundworks core:

- one rigid-body object per sand fragment
- entity-based representation of the entire terrain
- continuous rigid-body simulation for every granular unit

Use physical entities only for optional visual chunks, debris, or rare large
pieces. The granular mass itself should stay in the terrain grid.

### 5. Physics Mod

Repository:
`haubna/PhysicsMod`

CurseForge project license:
All Rights Reserved

The public repository observed during research contains documentation, shader
integration notes, and example/API material, not the full production source
needed to reimplement its physics system.

Current project status observed during research:

- supports Minecraft 26.3
- Fabric 26.3 build exists
- also supports Forge and NeoForge
- exposes useful compatibility information for rendering and physics features

Rule:

- behavior and public API/documentation reference only
- do not copy proprietary implementation
- do not treat the GitHub repository as the complete Physics Mod source

What to learn:

- 26.3 proves that advanced custom physics/rendering is viable on the target version
- optional future integration with external physics libraries is possible
- keep Groundworks independent from Physics Mod for the core terrain representation

### 6. Falling Sand

Catalog project:
`Modrinth: falling-sand`

Observed license:
All Rights Reserved

Observed behavior:

- rewrites falling-block logic
- blocks remain blocks rather than becoming falling-block entities
- falling materials can move downward
- materials can slide diagonally
- sliding can be probabilistic/configurable
- can create landslides and cave-ins
- server-side implementation

No verified public source repository was found during this research pass.

Rule:

- behavior-only inspiration
- do not copy code
- reimplement granular relaxation independently

The useful concept for Groundworks is not its full-block representation, but
the local-update rule: a material element can move into a lower neighboring
space without becoming an entity.

### 7. Better Dirt Mining - BDM

Catalog project:
`Better Dirt Mining - BDM`

Creator:
Janexi

Observed catalog license:
MIT

Observed version:
Fabric 1.21.11

Observed behavior:

- dirt, sand, gravel and related materials can be mined layer by layer
- later release notes describe layered-block gravity
- unsupported layered blocks can fall
- breaking under another layered block can remove material from the upper block
- full layered blocks can swap positions in some conditions

No verified public source repository was found during this research pass.

Rule:

- do not assume catalog MIT metadata is enough to copy code
- do not decompile the JAR
- use behavior as design validation until a verifiable source repo with LICENSE is found

What to learn:

- players understand partial-height excavation
- partial material should participate in gravity
- the conversion from full block to partial terrain can be incremental

### 8. Block Layering

Catalog project:
`Block Layering`

Creator:
Lothrazar

Observed catalog license:
All Rights Reserved

Observed behavior:

- adds dirt, sand, gravel, clay and other materials as snow-like layers
- reuses textures from the underlying block/model
- supports resource packs without shipping custom textures for every material

No verified public source repository was found during this research pass.

Rule:

- behavior-only inspiration

Useful design idea:

- reuse vanilla block textures for generated granular surfaces
- avoid duplicating the entire vanilla material texture library
- for MVP, sample the source block texture and map it onto generated terrain faces

## Groundworks architecture

### A. Conversion boundary

Do not convert the entire world.

Keep normal terrain as vanilla blocks until one of these happens:

- shovel removes a partial amount
- excavator bucket intersects the block
- bulldozer blade modifies the surface
- conveyor dumps material onto it
- another granular cell relaxes into it

Conversion:

`Vanilla BlockState -> GranularCell`

A full converted cell starts with:

- material id
- volume units = 512
- occupancy/density = full
- storage version
- dirty flags

When a granular cell returns to an exact full stable cube and no extra metadata
is needed, optionally compact it back to the vanilla block.

### B. Authoritative unit system

MVP resolution:

`8 x 8 x 8 = 512 units per vanilla block`

Definitions:

- 1 vanilla full block = 512 units
- 1 unit = 1 / 512 block volume
- logical block volume = 1.000 m3 for gameplay accounting

Do not use `double` as the authoritative material amount.

Use integers:

- terrain cell amount: integer units
- bucket amount: integer units
- wheelbarrow amount: integer units
- conveyor transfer: integer units per simulation step

This guarantees volume conservation.

### C. Storage strategies

Start with one of these:

#### Option 1 - Occupancy bitset

Best MVP choice if each microvoxel is empty/full.

- 512 bits = 64 bytes per converted block before metadata
- one material id per cell
- very fast population counts
- very fast union/subtraction masks

#### Option 2 - Packed density

Use if smoother partial occupancy is needed.

- 4 bits per microvoxel = 256 bytes per block
- 16 density levels
- more natural redistribution
- higher CPU and network cost

Recommendation:

Start with bitset occupancy plus Surface Nets-style visual smoothing.
Do not begin with per-voxel floating density.

### D. Granular simulation

Each material has a profile:

- density
- angle of repose
- cohesion
- slide probability
- wetness response later
- compactability later

MVP examples:

- sand: low cohesion, lower angle
- gravel: medium angle
- dirt: higher cohesion
- clay: high cohesion

Do not simulate every unit individually every tick.

Use dirty-region relaxation:

1. modification marks local cells dirty
2. dirty queue receives affected cells and neighbors
3. a fixed tick budget processes them
4. find unstable gradients
5. transfer integer units toward lower neighbors
6. stop when stable or tick budget is exhausted
7. continue next tick if needed

The exact physical model can evolve. Determinism and volume conservation are
more important than perfect real-world soil mechanics in Stage 1.

### E. Surface representation

For mostly surface soil, maintain fast summaries:

- top occupied y for each local x,z column
- total volume
- occupancy bitset

The bitset remains authoritative. The height summary is a cache.

This allows:

- fast shovel interaction
- fast collision approximation
- cheap angle-of-repose tests
- faster rendering bounds

### F. Tool excavation API

Core interface concept:

`removeVolume(Shape sweptShape, int maxUnits, ExcavationContext ctx)`

Return:

- material id
- units removed
- source positions
- optional composition info

Tool containers expose:

`acceptMaterial(materialId, units)`

Dumping exposes:

`depositMaterial(worldPos, materialId, units, velocityHint)`

The terrain engine, not the excavator mod, owns redistribution and meshing.

### G. Excavator bucket model

Do not test only the bucket's current static AABB.

Use a swept volume:

- previous bucket transform
- current bucket transform
- cutting edge
- bucket interior capacity

Approximate the swept shape using a small number of convex segments or sampled
poses for MVP.

The bucket owns:

- capacity units
- contained material id
- contained units

The terrain owns:

- which microvoxels were removed
- volume conservation

### H. Rendering

Preferred sequence:

1. gather dirty granular cells
2. construct a small scalar/occupancy field
3. run Surface Nets or a custom simplified surface mesher
4. build mesh CPU buffers
5. cache mesh by region/version
6. upload/render on the client
7. invalidate only overlapping dirty regions

Do not create one block entity renderer per microvoxel.

Texture strategy:

- derive material texture from the source Minecraft BlockState
- reuse vanilla atlas sprites where practical
- use triplanar-like or face-projected mapping only if atlas integration permits
- keep resource-pack compatibility as a design goal

### I. Collision

Rendering mesh and collision mesh do not need equal resolution.

MVP collision options:

- cached 8x8 heightfield per surface cell
- merged voxel boxes
- simplified mesh converted to a limited number of VoxelShapes

Prefer a heightfield for ordinary piles.

Use full 3D collision only where an overhang/cavity requires it.

### J. Persistence

Every saved granular payload must contain a format version.

Suggested schema:

- format version
- material registry id
- occupancy/density payload
- optional cached volume checksum
- optional flags

On load:

- validate volume
- reject corrupt payload safely
- never silently erase unknown historical formats
- keep migration handlers for old versions

### K. Networking

Server authoritative.

Do not send 512 values every change.

Prefer:

- cell version number
- changed microvoxel runs or bitset XOR
- changed total units
- dirty region id
- occasional full resync for recovery

Batch updates per tick and region.

Client prediction is optional and should never create material.

### L. Threading

Server simulation:

- deterministic world mutation on server-safe scheduling
- expensive candidate calculations may be parallelized only if world access is copied/snapshotted
- commit results on the server thread

Client meshing:

- CPU mesh generation may be worker-threaded
- Minecraft/renderer resource access must follow the active 26.3 rendering API rules
- GPU buffer upload must use the correct render thread/path

Never mutate live Minecraft world state from arbitrary worker threads.

## Performance budget

Stage 1 target:

- default 8^3 resolution
- simulation limited to dirty granular areas
- no global per-tick scan
- no per-unit entities
- no block entity for every microvoxel
- no full chunk remesh after each unit transfer
- no full-state network packet per micro-update

Add counters:

- active granular cells
- queued dirty cells
- units moved this tick
- simulation time in microseconds
- mesh rebuild count
- mesh generation time
- bytes sent for terrain deltas
- full resync count

Expose a debug overlay or `/groundworks debug`.

## Stage plan

### Stage 0 - research harness

- create a minimal Fabric 26.3 project
- confirm mappings and rendering hooks
- add GameTests
- add a debug command to inspect cell storage
- add benchmark counters

### Stage 1 - granular dirt prototype

Materials:

- dirt
- sand
- gravel

Features:

- lazy vanilla-to-granular conversion
- 512-unit conservation model
- shovel removes a configurable number of units
- right click or debug tool deposits units
- save/load
- basic collision
- ugly but correct debug rendering is acceptable

Exit criteria:

- no material duplication
- no material deletion
- save/load exact
- multiplayer server remains authoritative

### Stage 2 - relaxation

- angle-of-repose profiles
- local dirty queue
- pile formation
- diagonal transfer
- boundary transfer between vanilla blocks
- deterministic simulation tests

### Stage 3 - smooth mesh

- Surface Nets-inspired mesher
- cached regional meshes
- source block texture reuse
- lighting
- simplified collision cache
- client/server visual synchronization

### Stage 4 - containers and machines

- wheelbarrow
- steel wheelbarrow
- bucket API
- conveyor transfer API
- dump points
- stockpiles

### Stage 5 - excavator

- animated bucket
- swept cutting volume
- bucket capacity
- material pickup
- dumping
- machine-terrain collision
- multiplayer validation

### Stage 6 - optional advanced soil

- compacted material
- moisture
- mud
- mixed materials
- rock -> fractured rock -> rubble
- water erosion

Do not implement these before Stage 1-5 are stable.

## Required tests

### Volume conservation

For every operation:

`initial terrain + containers == final terrain + containers`

Use integer unit sums.

Tests:

- remove 1 unit
- remove 511 units
- remove full 512 units
- dump partial bucket
- dump across cell boundary
- relaxation across 10+ cells
- save/reload
- network delta replay

### Determinism

Run the same initial state and operation sequence twice.

Expected:

- identical cell payloads
- identical total units
- identical dirty queue completion result

### Boundary cases

- world/chunk boundary
- negative coordinates
- top/bottom build limits
- unloaded neighboring chunk
- water adjacency
- solid non-granular neighbor
- granular material above air
- two granular materials meeting

### Performance tests

Benchmark:

- 1 changed block
- 10x10 pile
- 32x32 active excavation site
- conveyor continuously depositing
- excavator moving through a wall of granular dirt

Record:

- server tick time
- client mesh time
- packet volume
- allocations if profiler is available

## Source-analysis workflow for agents

When asked to research an external project:

1. Identify repository and exact branch.
2. Pin a commit SHA.
3. Read LICENSE in that commit.
4. Classify:
   - `COPY_ALLOWED_WITH_TERMS`
   - `READ_AND_REIMPLEMENT`
   - `BEHAVIOR_ONLY`
5. Locate only relevant classes.
6. Write a short architecture note before touching Groundworks.
7. Reimplement the smallest useful concept.
8. Add tests proving the concept.
9. Record attribution and license obligations in `docs/third-party-research.md`.
10. Never introduce a dependency solely because a reference mod uses it.

## Current source classification

| Project | Source status | Observed license | Groundworks use |
|---|---|---|---|
| Chisels & Bits | verified public repo | MIT in observed repo branch | storage ideas; code reuse only after exact LICENSE check |
| NoCubes | verified public repo | LGPL-3.0 in observed repo | meshing/collision study; comply with LGPL if adapting code |
| LittleTiles | verified public repo | LGPL-3.0 in observed repo | integer micro-grid and geometry study |
| Sand Physics | verified public repo | MIT | Mixin/physics integration patterns |
| Physics Mod | public docs/example repo, production code not verified open | ARR on CurseForge | behavior/API only |
| Falling Sand | no public source repo verified in research | ARR | behavior only |
| Better Dirt Mining | no public source repo verified in research | MIT catalog metadata | behavior only until repo LICENSE verified |
| Block Layering | no public source repo verified in research | ARR | behavior only |

## Design decision summary

Groundworks should be an original engine, not a dependency stack.

Use these ideas:

- Chisels & Bits: compact sub-block storage and versioning
- LittleTiles: integer grid discipline and box operations
- NoCubes: smooth mesh generation, caching, custom collision
- Falling Sand: local diagonal gravity concept
- Better Dirt Mining: partial excavation UX
- Block Layering: vanilla texture reuse
- Sand Physics: narrow physics hooks and lifecycle separation
- Physics Mod: proof that advanced 26.3 rendering/physics integration is feasible

Do not inherit:

- arbitrary decorative multi-material complexity from Chisels & Bits
- full LittleTiles feature scope
- repeated uncached collision meshing
- thousands of physical entities
- closed-source or ARR implementation details

The core invariant is:

`material units are integers and are never created or destroyed by terrain operations.`

If a proposed feature makes that invariant difficult to test, redesign the feature.
