# Rendering Design

## 1. Separation of Physics & Visual Geometry

Physics/simulation is an $8 \times 8 \times 8$ bitset. Rendering will NOT be 512 mini-cubes.

In Stage 3:
1. Microvoxel bitset is sampled as a scalar density field.
2. A **Surface Nets** (or Dual Contouring) algorithm extracts a smooth, continuous surface.
3. Meshes are cached per regional chunk.
4. Meshes are only regenerated when `cell.isDirty(DirtyFlags.MESH)`.

## 2. Worker Threading

- **CPU Mesh Extraction:** Worker thread pool (non-blocking).
- **GPU Upload:** Main client render thread.
- Thread-safety: Meshers receive an immutable snapshot of local cells; never mutate live world state.

## 3. Texture Atlas Reuse

Generated faces map UVs from the source vanilla block's atlas sprite (dirt, sand, gravel) using triplanar or projected mapping.
