# Peterwolf's Groundworks

**Volumetric Deformable Granular Terrain Engine for Minecraft Java 26.3 (Fabric)**

Peterwolf's Groundworks introduces a high-performance granular terrain simulation engine designed as a reusable foundation for heavy machinery mods (excavators, bulldozers, dump trucks, conveyors, wheelbarrows, tunnel boring machines).

---

## Key Features

- **Strict Volume Conservation:** Material is never created or destroyed. Authoritative volume calculations are strictly integer microvoxels ($8 \times 8 \times 8 = 512$ units per block $= 1.000\text{ m}^3$).
- **Compact Bitset Storage:** 512-bit occupancy bitset (`long[8]`, 64 bytes) per active cell.
- **Lazy World Conversion:** The world remains vanilla until excavated, deposited into, or deformed.
- **Built-in Materials:** Dirt, sand, gravel, and granular cobblestone produced by crushing stone.
- **Clean Public API:** `GroundworksApi.excavate(...)` and `GroundworksApi.deposit(...)` ready for external vehicle and machine mods.
- **Developer / Hand Tools:**
  - `debug_excavation_tool` - shovel that removes 32 units on right-click and deposits on sneak-right-click.
  - `groundworks_pickaxe` - pickaxe that crushes stone into granular cobblestone in 128-unit chunks, exactly 1/4 block per use, with the same 16-block internal capacity.
  - `/groundworks debug` and `/groundworks inspect` commands.
- **Full World Persistence:** Granular terrain survives save and reload via Minecraft 26.3 Codec SavedData.

---

## Build & Test

```bash
./gradlew build
./gradlew test
```

---

## License

MIT © 2026 Piotrek (Peterwolf)
