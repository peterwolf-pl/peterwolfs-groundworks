# Peterwolf's Groundworks — Architecture

## 1. System Overview

Peterwolf's Groundworks is not a block mod — it is a **volumetric granular terrain simulation engine** running inside Minecraft. Minecraft blocks serve only as the outer world grid. Inside affected terrain, Groundworks owns the material representation.

```
+─────────────────────────────────────────────────────────+
|                 Peterwolf's Groundworks                 |
+─────────────────────────────────────────────────────────+
|  Public API: GroundworksApi.excavate / deposit / query  |
+────────────────────────────┬────────────────────────────+
                             │
       ┌─────────────────────┼─────────────────────┐
       ▼                     ▼                     ▼
+─────────────+       +─────────────+       +─────────────+
| Excavation  |       | Deposition  |       | Relaxation  |
|     API     |       |     API     |       |  (Stage 2)  |
+──────┬──────+       +──────┬──────+       +──────┬──────+
       │                     │                     │
       └─────────────────────┼─────────────────────┘
                             ▼
              +─────────────────────────────+
              |    GranularWorldStorage     |
              |  - Per-dimension SavedData  |
              |  - ConcurrentHashMap<Pos>   |
              |  - Dirty queue (tick budget)|
              +──────────────┬──────────────+
                             ▼
              +─────────────────────────────+
              |        GranularCell         |
              |  - 8x8x8 = 512 microvoxels  |
              |  - 512-bit bitset (long[8]) |
              |  - Integer conservation     |
              +─────────────────────────────+
```

## 2. Core Invariants

1. **Volume Conservation:** Material is never created or destroyed.
   $$\sum \text{terrain units} + \sum \text{container units} = \text{const}$$
2. **Integer Authority:** Authoritative volume is strictly measured in integer microvoxels ($0 \dots 512$). Floating point numbers are strictly forbidden for authoritative calculations.
3. **Lazy Conversion:** Vanilla blocks remain untouched vanilla blocks until excavated, deposited into, or relaxed into.

## 3. Data Representation

- Resolution: $8 \times 8 \times 8 = 512$ microvoxels per block ($1.000\text{ m}^3$).
- Occupancy: `long[8]` (64 bytes).
- Flat index: `index = x + 8 * (z + 8 * y)`.
- Y is the major axis so each vertical layer is contiguous in memory.

## 4. Lazy Conversion Boundary

```
Vanilla BlockState (dirt / sand / gravel)
                    │
                    ▼ (lazy trigger: shovel / bucket / conveyor)
GranularCell (material, 512 units, full bitset, rev=1)
                    │
                    ▼
Vanilla block replaced with air / marker
```
