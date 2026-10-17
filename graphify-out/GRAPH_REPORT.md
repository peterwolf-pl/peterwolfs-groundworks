# Graph Report - Peterwolfs-Groundworks  (2026-10-09)

## Corpus Check
- Corpus is ~35,246 words - fits in a single context window. You may not need a graph.

## Summary
- 875 nodes · 2839 edges · 45 communities (19 shown, 26 thin omitted)
- Extraction: 82% EXTRACTED · 18% INFERRED · 0% AMBIGUOUS · INFERRED: 503 edges (avg confidence: 0.86)
- Token cost: 500 input · 300 output

## Community Hubs (Navigation)
- Test Suites & Verification
- Test Suites & Verification
- Component Subsystem 2
- Camera Zoom & Panning
- Granular Surface Meshing
- World Storage & Spatial Coordinates
- Material Composition & API
- Granular Surface Meshing
- World Storage & Spatial Coordinates
- Granular Surface Meshing
- Component Subsystem 10
- Client Networking & Tick Sync
- Material Composition & API
- Prior Art & Research Audits
- Client Networking & Tick Sync
- Material Composition & API
- Test Suites & Verification
- Component Subsystem 17
- Block Registration & Items
- Component Subsystem 20
- Material Composition & API
- Mobile Container API
- Component Subsystem 24
- Material Composition & API
- Component Subsystem 26
- Component Subsystem 27
- Component Subsystem 29
- Material Composition & API
- Material Composition & API
- Component Subsystem 32
- Material Composition & API
- Test Suites & Verification
- Mobile Container API
- Component Subsystem 38
- Material Composition & API
- Material Composition & API
- Component Subsystem 41
- Test Suites & Verification

## God Nodes (most connected - your core abstractions)
1. `GranularCell` - 129 edges
2. `GranularMaterial` - 68 edges
3. `GranularWorldStorage` - 56 edges
4. `GranularComposition` - 46 edges
5. `GranularMaterialRegistry` - 29 edges
6. `GroundworksVisualGameTest` - 25 edges
7. `GranularBlockEntity` - 24 edges
8. `CameraZoomManager` - 23 edges
9. `WorldSpaceApi` - 23 edges
10. `GroundworksApi` - 21 edges

## Surprising Connections (you probably didn't know these)
- `Event-Driven Mesh Cache Invalidation` --implements--> `GranularMeshCache`  [INFERRED]
  docs/rendering-review.md → src/client/java/com/piotrek/groundworks/client/render/GranularMeshCache.java
- `GranularCell Architecture` --implements--> `GranularCell`  [INFERRED]
  docs/architecture.md → src/main/java/com/piotrek/groundworks/terrain/cell/GranularCell.java
- `Single Terrain Renderer Ownership` --implements--> `GranularTerrainRenderer`  [INFERRED]
  docs/rendering-review.md → src/client/java/com/piotrek/groundworks/client/render/GranularTerrainRenderer.java
- `Groundworks Public API` --references--> `GroundworksApi`  [EXTRACTED]
  README.md → src/main/java/com/piotrek/groundworks/api/GroundworksApi.java
- `World-Space Excavation and Grading API` --implements--> `WorldSpaceApi`  [INFERRED]
  docs/world-space-api.md → src/main/java/com/piotrek/groundworks/api/world/WorldSpaceApi.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Universal Volume Conservation Invariant Pattern** — readme_volume_conservation, docs_architecture_volume_conservation_invariant, docs_simulation_material_conservation_invariant, skill_integer_authority, docs_world_space_api_integer_accounting_invariant [INFERRED 0.95]
- **Deterministic Cellular Relaxation Pipeline** — skill_cellular_relaxation_engine, docs_granular_simulation_review_implemented_relaxation_algorithm, docs_granular_simulation_review_deterministic_neighbor_ring, docs_granular_simulation_review_repose_threshold_formulation, docs_granular_simulation_review_bulk_occupancy_operations [INFERRED 0.95]
- **Continuous Terrain Meshing and Shading Architecture** — skill_heightfield_meshing, docs_rendering_review_heightfield_fast_path_mesher, docs_rendering_review_local_gradient_normals, docs_rendering_review_vanilla_texture_atlas_reuse, docs_rendering_review_event_driven_mesh_cache [INFERRED 0.95]

## Communities (45 total, 26 thin omitted)

### Community 0 - "Test Suites & Verification"
Cohesion: 0.08
Nodes (10): GranularMeshCache, CellMesh, GranularCompositionTest, GranularMaterialRegistryTest, WorldSpaceApiTest, GranularSurfaceMesherTest, GranularNetworkingTest, GranularRelaxationTest (+2 more)

### Community 1 - "Test Suites & Verification"
Cohesion: 0.09
Nodes (9): DepositResult, ExcavationApi, ExcavationResult, GroundworksApi, WorldSpaceApi, WorldVoxel, GranularRelaxationEngine, Receiver (+1 more)

### Community 2 - "Component Subsystem 2"
Cohesion: 0.09
Nodes (8): Memory Footprint Budget, Performance Guidelines and Budgets, Telemetry and Diagnostic Counters, GroundworksVisualGameTest, PerformanceMetrics, PileMetrics, CellEntry, GranularWorldStorage

### Community 3 - "Camera Zoom & Panning"
Cohesion: 0.08
Nodes (7): 3rd Person Camera Lateral & Vertical Panning, High-Performance Adaptive Camera Zoom, Release 0.1.2+26.3 Camera Controls & Panning, CameraZoomManager, CameraMixin, MouseHandlerMixin, CameraZoomManagerTest

### Community 4 - "Granular Surface Meshing"
Cohesion: 0.07
Nodes (13): Rendering Design, Heightfield Fast-Path Mesher, 75% Quad Count Reduction, Rendering Review, Vanilla Texture Atlas Reuse, Surface Nets Extraction Concept, Texture Atlas Reuse and Triplanar Mapping, Continuous Heightfield Meshing Standard (+5 more)

### Community 7 - "Granular Surface Meshing"
Cohesion: 0.08
Nodes (3): GranularBlockEntityRenderer, GranularBlockRenderState, GranularBlockEntity

### Community 8 - "World Storage & Spatial Coordinates"
Cohesion: 0.13
Nodes (3): IWorldGranularContainer, GranularMaterial, GranularMaterialRegistry

### Community 9 - "Granular Surface Meshing"
Cohesion: 0.13
Nodes (3): GranularBlock, Entry, GranularCollisionShapeCache

### Community 10 - "Component Subsystem 10"
Cohesion: 0.17
Nodes (4): DepositApi, GranularSyncHandler, DirtyFlags, BlockConverter

### Community 11 - "Client Networking & Tick Sync"
Cohesion: 0.13
Nodes (5): Bandwidth Conservation Protocol, Cell Revision Tracking, Networking Design, GroundworksClient, GranularCellSyncPayload

### Community 13 - "Prior Art & Research Audits"
Cohesion: 0.15
Nodes (17): Better Dirt Mining Audit, Block Layering Audit, Chisels & Bits Audit, Falling Sand Audit, LittleTiles Audit, NoCubes Audit, Sand Physics Audit, Third-Party Research and License Audit (+9 more)

### Community 16 - "Test Suites & Verification"
Cohesion: 0.16
Nodes (6): Cross-Mod Excavator Testing Plan, Image Comparison Metrics, Automated Visual Regression Pipeline, Visual Testing Framework, compare(), main()

### Community 25 - "Material Composition & API"
Cohesion: 0.22
Nodes (6): Grading Primitives (Cut and Fill), Simulation Marking Hook, Multi-Cell Spherical Excavation Brush, World-Space Excavation and Grading API, World-Space Terrain Queries, Groundworks API Surface

### Community 26 - "Component Subsystem 26"
Cohesion: 0.22
Nodes (5): Modular Platform Architecture, Peterwolf's Groundworks Bulldozer, Peterwolf's Groundworks Excavator, Peterwolf's Groundworks Loader, Peterwolf's Groundworks Core

### Community 29 - "Component Subsystem 29"
Cohesion: 0.25
Nodes (4): Peterwolf's Groundworks — Architecture, GranularCell Architecture, GranularWorldStorage Component, Volumetric Granular Simulation Architecture

### Community 30 - "Material Composition & API"
Cohesion: 0.25
Nodes (3): Bitset Occupancy Model, Codec SavedData Serialization Schema, Granular Storage Design

### Community 32 - "Component Subsystem 32"
Cohesion: 0.33
Nodes (5): Developer and Debug Tools, Granular Cobblestone Material, Volumetric Granular Terrain Engine, Groundworks Public API, README: Peterwolf's Groundworks

### Community 33 - "Material Composition & API"
Cohesion: 0.33
Nodes (4): Granular Materials Registry, Minecraft Groundworks Skill, Positional Diesel Engine Audio, Vehicle Physics Standards

### Community 41 - "Component Subsystem 41"
Cohesion: 0.67
Nodes (3): White Hard Hat Baby Humanoid Equipment Texture, White Hard Hat Humanoid Equipment Texture, White Hard Hat Item Texture

## Knowledge Gaps
- **25 isolated node(s):** `README: Peterwolf's Groundworks`, `Granular Cobblestone Material`, `Developer and Debug Tools`, `Physics Mod Reference`, `Peterwolf's Groundworks Core` (+20 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 163 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **26 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GranularCell` connect `Material Composition & API` to `Test Suites & Verification`, `Test Suites & Verification`, `Material Composition & API`, `Component Subsystem 2`, `Granular Surface Meshing`, `World Storage & Spatial Coordinates`, `Test Suites & Verification`, `Granular Surface Meshing`, `World Storage & Spatial Coordinates`, `Granular Surface Meshing`, `Component Subsystem 10`, `Client Networking & Tick Sync`, `Material Composition & API`, `Client Networking & Tick Sync`, `Material Composition & API`, `Component Subsystem 27`, `Component Subsystem 29`, `Material Composition & API`?**
  _High betweenness centrality (0.237) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `GranularCell` (e.g. with `GranularCell Architecture` and `Bitset Occupancy Model`) actually correct?**
  _`GranularCell` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `README: Peterwolf's Groundworks`, `Granular Cobblestone Material`, `Developer and Debug Tools` to the rest of the system?**
  _25 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Test Suites & Verification` be split into smaller, more focused modules?**
  _Cohesion score 0.0799543118218161 - nodes in this community are weakly interconnected._
- **Why does `GranularWorldStorage` connect `Component Subsystem 2` to `Test Suites & Verification`, `Granular Surface Meshing`, `World Storage & Spatial Coordinates`, `Material Composition & API`, `Granular Surface Meshing`, `Component Subsystem 10`, `Client Networking & Tick Sync`, `Block Registration & Items`, `Component Subsystem 20`, `Component Subsystem 29`?**
  _High betweenness centrality (0.074) - this node is a cross-community bridge._
- **Should `Test Suites & Verification` be split into smaller, more focused modules?**
  _Cohesion score 0.09131652661064425 - nodes in this community are weakly interconnected._
- **Why does `GranularMaterial` connect `World Storage & Spatial Coordinates` to `Test Suites & Verification`, `Test Suites & Verification`, `Component Subsystem 2`, `World Storage & Spatial Coordinates`, `Material Composition & API`, `Material Composition & API`, `Material Composition & API`, `Granular Surface Meshing`, `Component Subsystem 10`, `Material Composition & API`, `Client Networking & Tick Sync`, `Material Composition & API`, `Material Composition & API`, `Material Composition & API`, `Mobile Container API`, `Component Subsystem 27`?**
  _High betweenness centrality (0.071) - this node is a cross-community bridge._