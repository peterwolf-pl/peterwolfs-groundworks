# Granular Storage Design

## 1. Bitset Occupancy Model

Each converted block is represented by a `GranularCell`:

| Field | Type | Size | Description |
|---|---|---|---|
| `materialId` | `int` | 4 bytes | Compatibility id of the dominant material |
| `composition` | small integer table | variable | Exact unit count per material; total equals `unitCount` |
| `occupancy` | `long[8]` | 64 bytes | 512-bit occupancy bitset |
| `unitCount` | `int` | 4 bytes | Cached population count ($0 \dots 512$) |
| `revision` | `int` | 4 bytes | Monotonically increasing edit counter |
| `dirtyFlags` | `int` | 4 bytes | Bitmask of dirty state flags |
| `columnHeights`| `byte[64]` | 64 bytes | Cached surface height per $(x, z)$ column |

The occupancy footprint remains fixed at 64 bytes. Composition adds only a small material-count table instead of a material id for each of the 512 microvoxels.

## 2. Microvoxel Indexing Layout

We use the **XZY** layout:

$$\text{index} = x + 8 \cdot (z + 8 \cdot y)$$

- $x \in [0, 7]$: lowest 3 bits
- $z \in [0, 7]$: middle 3 bits
- $y \in [0, 7]$: highest 3 bits (each $y$-slice is 64 bits = exactly one `long`)

### Benefits:
- Bitwise extraction:
  - `x = index & 7`
  - `z = (index >> 3) & 7`
  - `y = (index >> 6) & 7`
- A whole horizontal slice ($8 \times 8$) maps directly to `occupancy[y]`.
- Top-down clearing (`removeFromTop`) checks words directly.

## 3. Serialization Schema

Stored in world `SavedData` via Minecraft 26.3 Codec:

```snbt
{
  "pos": 1234567890L,
  "tag": {
    "dataVersion": 1,
    "material": "dirt",
    "occupancy": [ -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L ],
    "unitCount": 512,
    "revision": 1,
    "materialUnits_1": 205,
    "materialUnits_2": 102,
    "materialUnits_3": 205
  }
}
```


## 4. Mixed-material invariant

Geometry and material identity are deliberately separate:

- `occupancy` answers where granular volume exists.
- `composition` answers what that volume contains.
- The sum of all composition counts must always equal `unitCount`.
- `materialId()` remains a backward-compatible view of the dominant component.
- Mixed full cells stay granular and are not solidified into a vanilla block.
- Cobblestone uses a visual cluster size of 4x4x4 microvoxels while physical volume accounting remains on the 8x8x8 Groundworks grid.
