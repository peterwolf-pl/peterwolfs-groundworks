# Graph Report - Peterwolfs-Groundworks  (2026-10-08)

## Corpus Check
- 78 files · ~33,109 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 7 file(s) not represented in the graph (top: (none) 2, .properties 2, .jar 1)

## Summary
- 788 nodes · 2643 edges · 32 communities (17 shown, 15 thin omitted)
- Extraction: 81% EXTRACTED · 19% INFERRED · 0% AMBIGUOUS · INFERRED: 489 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `93ee5708`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- net.minecraft.core.BlockPos
- GranularCell
- org.junit.jupiter.api.Test
- GranularContainerTool.java
- GranularWorldStorage
- .generateMesh
- GranularMaterialRegistry
- WorldSpaceApi.java
- GranularBlock.java
- GranularBlockEntityRenderer.java
- Groundworks Research Reference Matrix
- GroundworksMod.java
- compare_visuals.py
- GranularCellSyncPayload
- ClientGranularStorage.java
- Implemented Relaxation Algorithm
- World-Space Excavation and Grading API
- Modular Platform Architecture
- Volumetric Granular Simulation Architecture
- Granular Storage Design
- Volume Conservation Invariant
- Volumetric Granular Terrain Engine
- Minecraft Groundworks Skill
- org.junit.jupiter.api.BeforeAll
- GranularComposition
- ExcavationBrush
- IMobileWorldGranularContainer
- list

## God Nodes (most connected - your core abstractions)
1. `GranularCell` - 125 edges
2. `GranularMaterial` - 68 edges
3. `GranularWorldStorage` - 55 edges
4. `GranularComposition` - 46 edges
5. `GranularMaterialRegistry` - 29 edges
6. `GroundworksVisualGameTest` - 25 edges
7. `GranularBlockEntity` - 24 edges
8. `WorldSpaceApi` - 23 edges
9. `GroundworksApi` - 21 edges
10. `GroundworksMod` - 19 edges

## Surprising Connections (you probably didn't know these)
- `GranularCell Architecture` --implements--> `GranularCell`  [INFERRED]
  docs/architecture.md → src/main/java/com/piotrek/groundworks/terrain/cell/GranularCell.java
- `Event-Driven Mesh Cache Invalidation` --implements--> `GranularMeshCache`  [INFERRED]
  docs/rendering-review.md → src/client/java/com/piotrek/groundworks/client/render/GranularMeshCache.java
- `Single Terrain Renderer Ownership` --implements--> `GranularTerrainRenderer`  [INFERRED]
  docs/rendering-review.md → src/client/java/com/piotrek/groundworks/client/render/GranularTerrainRenderer.java
- `Groundworks Public API` --references--> `GroundworksApi`  [EXTRACTED]
  README.md → src/main/java/com/piotrek/groundworks/api/GroundworksApi.java
- `World-Space Excavation and Grading API` --implements--> `WorldSpaceApi`  [INFERRED]
  docs/world-space-api.md → src/main/java/com/piotrek/groundworks/api/world/WorldSpaceApi.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Deterministic Cellular Relaxation Pipeline** — skill_cellular_relaxation_engine, docs_granular_simulation_review_implemented_relaxation_algorithm, docs_granular_simulation_review_deterministic_neighbor_ring, docs_granular_simulation_review_repose_threshold_formulation, docs_granular_simulation_review_bulk_occupancy_operations [INFERRED 0.95]
- **Continuous Terrain Meshing and Shading Architecture** — skill_heightfield_meshing, docs_rendering_review_heightfield_fast_path_mesher, docs_rendering_review_local_gradient_normals, docs_rendering_review_vanilla_texture_atlas_reuse, docs_rendering_review_event_driven_mesh_cache [INFERRED 0.95]
- **Universal Volume Conservation Invariant Pattern** — readme_volume_conservation, docs_architecture_volume_conservation_invariant, docs_simulation_material_conservation_invariant, skill_integer_authority, docs_world_space_api_integer_accounting_invariant [INFERRED 0.95]

## Communities (32 total, 15 thin omitted)

### Community 0 - "net.minecraft.core.BlockPos"
Cohesion: 0.07
Nodes (10): GranularContainerTransferApi, IWorldGranularContainer, DepositResult, ExcavationApi, ExcavationResult, GroundworksApi, GranularMaterial, WorldSpaceApi (+2 more)

### Community 1 - "GranularCell"
Cohesion: 0.06
Nodes (6): Entry, GranularCollisionShapeCache, GranularRelaxationEngine, Receiver, SurfaceReceiver, GranularCell

### Community 2 - "org.junit.jupiter.api.Test"
Cohesion: 0.09
Nodes (8): GranularCompositionTest, GranularMaterialRegistryTest, WorldSpaceApiTest, GranularSurfaceMesherTest, GranularNetworkingTest, GranularRelaxationTest, GranularCellSerializationTest, GranularCellTest

### Community 3 - "GranularContainerTool.java"
Cohesion: 0.09
Nodes (4): DebugExcavationTool, GranularContainerTool, GroundworksItems, GroundworksPickaxe

### Community 4 - "GranularWorldStorage"
Cohesion: 0.09
Nodes (8): Memory Footprint Budget, Performance Guidelines and Budgets, Telemetry and Diagnostic Counters, GroundworksVisualGameTest, PerformanceMetrics, PileMetrics, CellEntry, GranularWorldStorage

### Community 5 - ".generateMesh"
Cohesion: 0.06
Nodes (16): Rendering Design, Heightfield Fast-Path Mesher, 75% Quad Count Reduction, Rendering Review, Vanilla Texture Atlas Reuse, Surface Nets Extraction Concept, Texture Atlas Reuse and Triplanar Mapping, Continuous Heightfield Meshing Standard (+8 more)

### Community 7 - "WorldSpaceApi.java"
Cohesion: 0.16
Nodes (5): DepositApi, GranularBlockEntity, GranularSyncHandler, DirtyFlags, BlockConverter

### Community 11 - "Groundworks Research Reference Matrix"
Cohesion: 0.15
Nodes (17): Better Dirt Mining Audit, Block Layering Audit, Chisels & Bits Audit, Falling Sand Audit, LittleTiles Audit, NoCubes Audit, Sand Physics Audit, Third-Party Research and License Audit (+9 more)

### Community 13 - "compare_visuals.py"
Cohesion: 0.16
Nodes (6): Cross-Mod Excavator Testing Plan, Image Comparison Metrics, Automated Visual Regression Pipeline, Visual Testing Framework, compare(), main()

### Community 14 - "GranularCellSyncPayload"
Cohesion: 0.20
Nodes (4): Bandwidth Conservation Protocol, Cell Revision Tracking, Networking Design, GranularCellSyncPayload

### Community 17 - "World-Space Excavation and Grading API"
Cohesion: 0.22
Nodes (6): Grading Primitives (Cut and Fill), Simulation Marking Hook, Multi-Cell Spherical Excavation Brush, World-Space Excavation and Grading API, World-Space Terrain Queries, Groundworks API Surface

### Community 18 - "Modular Platform Architecture"
Cohesion: 0.22
Nodes (5): Modular Platform Architecture, Peterwolf's Groundworks Bulldozer, Peterwolf's Groundworks Excavator, Peterwolf's Groundworks Loader, Peterwolf's Groundworks Core

### Community 19 - "Volumetric Granular Simulation Architecture"
Cohesion: 0.25
Nodes (4): Peterwolf's Groundworks — Architecture, GranularCell Architecture, GranularWorldStorage Component, Volumetric Granular Simulation Architecture

### Community 20 - "Granular Storage Design"
Cohesion: 0.25
Nodes (3): Bitset Occupancy Model, Codec SavedData Serialization Schema, Granular Storage Design

### Community 22 - "Volumetric Granular Terrain Engine"
Cohesion: 0.33
Nodes (5): Developer and Debug Tools, Granular Cobblestone Material, Volumetric Granular Terrain Engine, Groundworks Public API, README: Peterwolf's Groundworks

### Community 23 - "Minecraft Groundworks Skill"
Cohesion: 0.33
Nodes (4): Granular Materials Registry, Minecraft Groundworks Skill, Positional Diesel Engine Audio, Vehicle Physics Standards

### Community 26 - "GranularComposition"
Cohesion: 0.08
Nodes (4): IGranularContainer, SimpleGranularContainer, GranularComposition, SimpleGranularContainerTest

## Knowledge Gaps
- **22 isolated node(s):** `Physics Mod Reference`, `Cross-Mod Excavator Testing Plan`, `Image Comparison Metrics`, `Cell Revision Tracking`, `Grading Primitives (Cut and Fill)` (+17 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 147 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **15 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GranularCell` connect `GranularCell` to `net.minecraft.core.BlockPos`, `list`, `GranularContainerTool.java`, `org.junit.jupiter.api.Test`, `.generateMesh`, `GranularMaterialRegistry`, `WorldSpaceApi.java`, `GranularBlock.java`, `GranularBlockEntityRenderer.java`, `GranularBlockEntity.java`, `GranularWorldStorage`, `GranularCellSyncPayload`, `ClientGranularStorage.java`, `Volumetric Granular Simulation Architecture`, `Granular Storage Design`, `org.junit.jupiter.api.BeforeAll`, `GranularComposition`?**
  _High betweenness centrality (0.248) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `GranularCell` (e.g. with `GranularCell Architecture` and `Bitset Occupancy Model`) actually correct?**
  _`GranularCell` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Physics Mod Reference`, `Cross-Mod Excavator Testing Plan`, `Image Comparison Metrics` to the rest of the system?**
  _22 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `net.minecraft.core.BlockPos` be split into smaller, more focused modules?**
  _Cohesion score 0.06958762886597938 - nodes in this community are weakly interconnected._
- **Why does `GranularMaterial` connect `net.minecraft.core.BlockPos` to `GranularCell`, `org.junit.jupiter.api.Test`, `GranularContainerTool.java`, `GranularWorldStorage`, `GranularMaterialRegistry`, `WorldSpaceApi.java`, `GranularBlock.java`, `GranularBlockEntity.java`, `GroundworksMod.java`, `org.junit.jupiter.api.BeforeAll`, `GranularComposition`?**
  _High betweenness centrality (0.081) - this node is a cross-community bridge._
- **Should `GranularCell` be split into smaller, more focused modules?**
  _Cohesion score 0.0635814889336016 - nodes in this community are weakly interconnected._
- **Why does `GranularWorldStorage` connect `GranularWorldStorage` to `net.minecraft.core.BlockPos`, `GranularCell`, `.generateMesh`, `WorldSpaceApi.java`, `GranularBlock.java`, `GranularBlockEntity.java`, `GroundworksMod.java`, `Volumetric Granular Simulation Architecture`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._