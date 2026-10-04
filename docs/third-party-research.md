# Third-Party Research & License Audit

Record of external reference repositories inspected, their licenses, and classification for Peterwolf's Groundworks.

**Snapshot Date:** 2026-10-04  
**Architect:** Piotrek (Peterwolf)

---

## Legal & Architectural Policy

1. **NO UNLICENSED CODE:** Never copy code from repositories without an open-source license explicitly granting copy/modification rights for the exact inspected commit.
2. **NO DECOMPILATION:** Never decompile JAR files to extract implementation details.
3. **INDEPENDENT IMPLEMENTATION:** Prefer clean-room reimplementation of algorithms and behaviors.

---

## Inspected Projects

### 1. Chisels & Bits
- **Repository:** `ChiselsAndBits/Chisels-and-Bits`
- **Observed Branch:** `version/26.1`
- **License:** MIT
- **Classification:** `READ_AND_REIMPLEMENT`
- **What Was Studied:** Sub-block indexing, state entry palettes, serialization with versioning, network synchronization of partial blocks.
- **Groundworks Adoption:** We use a compact 512-bit occupancy bitset (`long[8]`, 64 bytes) rather than multi-state palettes because each granular cell in Stage 1 contains only a single material type. We adopted the clean separation between storage, versioning, and dirty flagging.

### 2. NoCubes
- **Repository:** `Cadiboo/NoCubes`
- **License:** LGPL-3.0
- **Classification:** `BEHAVIOR_ONLY`
- **What Was Studied:** Surface Nets, Marching Cubes, SDF mesher, regional mesh caching, separating visual mesh from collision geometry.
- **Groundworks Adoption:** Architectural blueprint for Stage 3 rendering. Physics/authoritative storage is completely decoupled from visual representation. Mesh generation runs on worker threads; GPU uploads on the render thread.

### 3. LittleTiles
- **Repository:** `CreativeMD/LittleTiles`
- **Observed Branch:** `1.21`
- **License:** LGPL-3.0
- **Classification:** `READ_AND_REIMPLEMENT`
- **What Was Studied:** Integer sub-block grids, keeping geometry integer-based as long as possible, avoiding floating-point drift in simulation.
- **Groundworks Adoption:** Authoritative material amounts are strictly integer microvoxels (512 units per block). Floating-point is used only for display/rendering.

### 4. Sand Physics
- **Repository:** `multyfora/sandphysis-1.21.1`
- **License:** MIT
- **Classification:** `READ_AND_REIMPLEMENT`
- **What Was Studied:** Mixin hooks for falling-block interception, physics lifecycle.
- **Groundworks Adoption:** Learned what NOT to do: we do not spawn physical entities for granular units. The terrain remains a cellular grid.

### 5. Physics Mod
- **Repository:** `haubna/PhysicsMod`
- **CurseForge License:** All Rights Reserved
- **Classification:** `BEHAVIOR_ONLY`
- **What Was Studied:** Verification of Fabric 26.3 custom physics capabilities. No source copied.

### 6. Falling Sand
- **Catalog Coordinate:** Modrinth: `falling-sand`
- **License:** All Rights Reserved
- **Classification:** `BEHAVIOR_ONLY`
- **What Was Studied:** Behavioral concept of local cellular automaton relaxation without entity spawning.

### 7. Better Dirt Mining (BDM)
- **Catalog Coordinate:** Modrinth: `Better Dirt Mining - BDM`
- **Observed Catalog License:** MIT
- **Classification:** `BEHAVIOR_ONLY`
- **What Was Studied:** Player interaction UX for layer-by-layer excavation of soil blocks.

### 8. Block Layering
- **Catalog Coordinate:** CurseForge: `Block Layering`
- **License:** All Rights Reserved
- **Classification:** `BEHAVIOR_ONLY`
- **What Was Studied:** Vanilla texture atlas reuse across dynamic granular surfaces.
