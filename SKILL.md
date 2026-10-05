---
name: minecraft-groundworks
description: "Volumetric deformable granular terrain engine, heavy machinery integration, vehicle physics, and visual regression testing for Minecraft Java 26.3 Fabric (Peterwolf's Groundworks, Excavator, Bulldozer). Use when working on granular terrain, soil excavation, bucket digging, bulldozer grading, material conservation, smooth terrain meshing, cellular relaxation, piles/berms, or construction vehicles."
---

# Minecraft Groundworks (Granular Terrain & Machinery Engine)

## 1. System Architecture

Peterwolf's Groundworks is a modular volumetric granular terrain and heavy machinery platform for Minecraft Java 26.3 Fabric. It consists of three tightly integrated mods:

| Project | Mod ID | Primary Responsibilities |
|---|---|---|
| **Peterwolf's Groundworks** | `pw_groundworks` | Volumetric $8\times 8\times 8$ granular cell storage, lazy world conversion, deterministic cellular relaxation, continuous heightfield meshing, texture mapping, and public excavation/deposition API. |
| **Peterwolf's Groundworks Excavator** | `pw_groundworks_excavator` | Tracked hydraulic crawler excavator, closed-form forward kinematics, swept tooth cutting, bucket dumping, granular terrain contact constraints, 1x1 autotrenching state machine, and diesel engine sound. |
| **Peterwolf's Groundworks Bulldozer** | `pw_groundworks_bulldozer` | Heavy crawler bulldozer, differential track steering, 3.0m physical grading moldboard blade, 1536-unit capacity, active rolling surcharge, reversing heap discharge, windrow spillage, and deep diesel engine sound. |

---

## 2. Core Invariants & Mathematical Standards

1. **Volume Conservation**:
   Material is strictly conserved across all operations:
   $$\sum \text{terrain units} + \sum \text{machine containers} = \text{const}$$
   Material must never be created or destroyed.
2. **Integer Authority**:
   Authoritative material volume is strictly measured in integer microvoxels ($0 \dots 512$):
   $$\text{Full Block} = 8 \times 8 \times 8 = 512\text{ units} = 1.000\text{ m}^3$$
   $$\text{One Microvoxel} = 1\text{ unit} \approx 0.001953\text{ m}^3$$
   $$\text{Excavator Standard Bucket} = 256\text{ units} = 0.500\text{ m}^3$$
   $$\text{Excavator Large Bulk Bucket} = 512\text{ units} = 1.000\text{ m}^3$$
   $$\text{Bulldozer Blade Capacity} = 1536\text{ units} = 3.000\text{ m}^3\text{ (3 full blocks)}$$
   Floating-point arithmetic is strictly forbidden for authoritative material accounting.
3. **Lazy World Conversion**:
   Vanilla terrain remains ordinary Minecraft blocks until modified by an excavator bucket, bulldozer blade, shovel, or granular flow.
4. **Microvoxel Indexing**:
   Bitset layout uses **XZY** order ($y$ is the major axis):
   $$\text{index} = x + 8 \cdot (z + 8 \cdot y)$$
   - $x = \text{index} \ \& \ 7$
   - $z = (\text{index} \gg 3) \ \& \ 7$
   - $y = (\text{index} \gg 6) \ \& \ 7$
   - `occupancy[y]` represents a complete $8\times 8$ horizontal slice as a 64-bit `long`.

---

## 3. Public Groundworks API (`GroundworksApi`)

All external mods interact with Groundworks exclusively through `com.piotrek.groundworks.api.GroundworksApi`:

```java
// Legacy block-local removal
ExcavationResult result = GroundworksApi.excavate(level, pos, maxUnits);

// Preferred machine API: world-space brush, allowed to cross block boundaries
ExcavationResult brush = GroundworksApi.excavateAt(level, hitLocation, maxUnits);
ExcavationResult sphere = GroundworksApi.excavateSphere(level, hitLocation, radius, maxUnits);

// Grading primitives using absolute world-space Y
ExcavationResult cut = GroundworksApi.excavateAbove(level, pos, worldCutY, maxUnits);
DepositResult fill = GroundworksApi.fillBelow(level, pos, targetWorldY, material, availableUnits);

// Deposit with upward overflow
DepositResult deposit = GroundworksApi.depositWithOverflow(level, pos, material, units);

// Non-mutating machine queries
GranularMaterial terrainMaterial = GroundworksApi.getMaterial(level, pos);
boolean occupied = GroundworksApi.containsMaterialAt(level, worldPoint);
double surfaceY = GroundworksApi.getSurfaceWorldY(level, pos, worldX, worldZ);

// Request natural angle-of-repose settling after machine deposition
GroundworksApi.markForSimulation(level, pos);
```

### Material Registry (`GranularMaterialRegistry`)
- `DIRT` (id 1): $\theta_{\text{repose}} = 35^\circ$, cohesion $= 0.5$, density $= 1500\text{ kg/m}^3$.
  **Note**: `minecraft:grass_block` is the surface state of soil and converts directly to `DIRT`.
- `SAND` (id 2): $\theta_{\text{repose}} = 30^\circ$, cohesion $= 0.1$, density $= 1600\text{ kg/m}^3$.
- `GRAVEL` (id 3): $\theta_{\text{repose}} = 38^\circ$, cohesion $= 0.2$, density $= 1800\text{ kg/m}^3$.

---

## 4. Continuous Heightfield Rendering (`GranularSurfaceMesher`)

To hide the discrete 8x8x8 voxel steps while avoiding heavy isosurface density meshes:
- The visual mesh is an **interpolated heightfield fast-path** extracted from the 8x8 microvoxel column heights.
- Shared 8x8 grid corners average the top heights of the four surrounding world-space columns across block boundaries using floor division.
- Smooth per-vertex normals are computed from horizontal gradient differences:
  $$\vec{n} = \text{normalize}(h_{\text{left}} - h_{\text{right}}, \ 2 \cdot \text{step}, \ h_{\text{north}} - h_{\text{south}})$$
- Exposed vertical edges receive side skirts down to the cell base.
- Hidden undersides and faces occluded by an occupied cell directly above are culled.
- Textures are mapped using vanilla cutout block sprites (`textures/block/dirt.png`, `sand.png`, `gravel.png`), supporting resource packs seamlessly.
- Mesh caching (`GranularMeshCache`) uses **event-driven invalidation**: client network sync invalidates the changed cell, its 3x3 same-level neighborhood, and the cell below.

---

## 5. Cellular Relaxation Engine (`GranularRelaxationEngine`)

Authoritative physical settling operates on local dirty cells without scanning the entire world:
1. **Vertical Fall**: Unsupported material searches down up to 32 blocks to find a receiving surface and transfers in bounded steps (max 32 units/tick).
2. **Top-Surface Ownership**: Buried cells act as structural base; only the topmost cell in an active vertical column flows.
3. **8-Neighbor Lateral Slope Flow**: Evaluates N, NE, E, SE, S, SW, W, NW columns.
   - Cardinal threshold: $\tan(\theta) \cdot 8 + \text{cohesion} \cdot 2$
   - Diagonal threshold: cardinal $\times \sqrt{2}$
   - Direction tie-breaking is deterministic: hash of `worldSeed ^ packedPos ^ revision`.
4. **Conservation Enforcement**: Transfers strictly check `removed == added` before returning.

---

## 6. Universal Machinery & Vehicle Physics Standards

When designing any heavy construction vehicle or machine entity for Minecraft 26.3 Fabric, the following patterns are mandatory:

### A. Crosshair Raycasting & Targetability (`isPickable`)
In vanilla Minecraft (`Entity.java`), `isPickable()` returns `false` by default. **If not overridden, player crosshairs pass through the entity like a ghost**, hitting the ground block behind it. Both mounting (`interact`) and attacking/breaking (`hurtServer`) fail completely. Always override:
```java
@Override
public boolean isPickable() {
    return !this.isRemoved();
}

@Override
public boolean isAttackable() {
    return true;
}

@Override
public boolean canBeCollidedWith(@Nullable Entity other) {
    return other != null && !this.hasPassenger(other);
}

@Override
public boolean hurtClient(DamageSource source) {
    return true; // Allows client attack animation & sends attack packet to server
}
```

### B. Strict Server Authority over Ridden Vehicles
In Minecraft 26.3, when a player rides any entity, `Entity.isClientAuthoritative()` returns `true` by default because the passenger is a player.
- **The Bug**: The server stops simulating vehicle physics and waits for client movement packets. The client sends `ServerboundMoveVehiclePacket` containing the old, unmoved position. The server continuously resets the vehicle to the old position, freezing the machine in place while only `SynchedEntityData` (arms, blades) animates!
- **The Fix**: Explicitly override both authority methods on the entity:
```java
@Override
public boolean isClientAuthoritative() {
    return false; // Server owns position & movement
}

@Override
protected boolean isLocalClientAuthoritative() {
    return false; // Client will NOT send conflicting ServerboundMoveVehiclePacket
}
```
During motion ticks, force server-to-client position and velocity synchronization:
```java
if (hasDriver || Math.abs(trackSpeed) > 0.001F || Math.abs(yawDelta) > 0.01F) {
    this.syncPosition = true;
    this.needsSync = true;
    this.syncVelocity = true;
}
```

### C. Obstacle Clearance & Passenger Collision
- **Step Height (`maxUpStep`)**: Default is `0.0F`. Any 1-pixel height difference, micro-slab, or granular layer completely blocks horizontal movement. For crawler tracks, set:
  ```java
  @Override public float maxUpStep() { return 1.25F; }
  ```
- **Self-Collision**: Disable entity collision with riders:
  ```java
  @Override public boolean canCollideWith(Entity other) { return false; }
  ```

### D. Multi-Layer WASD Input Fallback
Do not rely exclusively on custom network packets. Network packets can suffer jitter, causing driver input decay timers to zero out and instantly brake the vehicle. Implement a two-layer strategy:
1. **Client**: Read physical GLFW keys (`InputConstants.isKeyDown(KEY_W)`), `KeyMapping.isDown()`, and `player.input.keyPresses`. Send packet with keepalive countdown $\le 3$ ticks.
2. **Server**: In `entity.tick()`, directly fallback to `player.getLastClientInput()` (`forward()`, `backward()`, `left()`, `right()`) whenever driver is seated.

### E. Standard 3-Way Vehicle Removal UX
1. **Survival Punch (LPM)**: Drops machine item and plays metallic clonk (`SoundEvents.ANVIL_HIT`).
2. **Creative Punch (LPM)**: Instantly deletes the machine.
3. **Shift + Right-Click with empty hand**: Instantly retrieves the machine directly into the player's inventory (`SoundEvents.ITEM_PICKUP`).
4. **Commands (`/kill`)**: Safely ejects passengers and discards entity.

---

## 7. Excavator Engineering (`pw_groundworks_excavator`)

### A. Machine Ground Push-Up Physics (Podnoszenie koparki na łyżce)
Real hydraulic excavators use boom down-pressure and bucket curling against solid ground/rock to jack the undercarriage upward:
- Compute penetration depth of cutting edge into ground: `depth = groundHeight - teeth.y`.
- If `depth > 0.05 m`:
  - Vertical lift: `liftY = min(1.6 m, depth * 1.25) * 0.35` applied directly to entity movement.
  - Chassis pitch reaction: `pitch = clamp(pitch + cos(cabYaw) * (lift * 18.0), -35°, 40°)`.
  - Tilts the front tracks up when the arm is facing forward ($+35^\circ$), or rear tracks up when facing rearward.
  - Allows climbing trench ledges and self-recovery.

### B. 1:1 Kinematic Matrix Synchronization (0mm Error)
- Never mix separate trigonometric kinematics with rendering transformations.
- `ArmKinematics` builds `computeBucketMatrix(...)` using the exact `Matrix4f` transformations as `ExcavatorRenderer`:
  $$\text{Mat} = \text{Translate}(\text{BasePos}) \times \text{RotY}(-\text{Yaw}) \times \text{Scale}(-1, -1, 1) \times \text{Translate}(0, -1.5, 0) \times \text{Turntable} \times \text{Boom} \times \text{Stick} \times \text{Bucket}$$
- Teeth coordinates, cutting edge, and lip position calculated from matrix transformations match visual polygons with $< 0.001\text{ mm}$ error.

### C. Multi-Bucket Interchangeability (Key `Z`)
- `BUCKET_STANDARD` (256u = $0.500\text{ m}^3$): 5 chisel teeth, 0.75m width, 32 units/tick intake for trenching.
- `BUCKET_LARGE` (512u = $1.000\text{ m}^3$): 7 heavy teeth, 1.25m width, 128 units/tick intake, multi-layer ground penetration (`targetPos.below()`) for bulk mass earthmoving.

### D. Excavator Controls & Two-Handed ISO Layout
- Dual joysticks: Left Joystick (Swing + Stick), Right Joystick (Boom + Bucket).
- **Mode Toggle (`Key X`)**:
  - **Drive Mode**: `WASD` drives and differential-steers tracks. Arrow keys control boom and bucket.
  - **Arm Mode**: Tracks locked. `WASD` controls cab swing (`A`/`D`) and dipper stick (`W`/`S`). Arrow keys control boom (`↑`/`↓`) and bucket (`←`/`→`).
- **Bucket Ergonomics**:
  - `Left Arrow` / `T`: Curl inward towards cab (holds and scoops material).
  - `Right Arrow` / `G`: Curl outward away from cab (fully opens to $145^\circ$ for complete gravity dump).

### E. Automated Trenching (`/excavator autotrench`)
- State machine cycle: `POSITION_FOR_CUT` $\to$ `PENETRATE_FOR_CUT` ($-0.65\text{ m}$) $\to$ `CUT_AND_CURL` ($-1.0\text{ m}$) $\to$ `SCOOP_AND_CURL` $\to$ `RELIEVE_STALL` $\to$ `LIFT_AND_SWING_RIGHT` ($52^\circ, 90^\circ\text{ yaw}$) $\to$ `DUMP_RIGHT` ($60^\circ$) $\to$ `RESET_AND_REVERSE` (1.0m crawl).

---

## 8. Bulldozer Engineering (`pw_groundworks_bulldozer`)

### A. Heavy Blade Capacity & Intake Throughput
- **Capacity**: `MAX_BLADE_CAPACITY = 1536` units ($3.000\text{ m}^3$ = 3 full blocks of granular material).
- **Intake Throughput**: Up to 128 units/tick per contact point across 9 cutting edge sample points. Slices through deep soil banks without slowing down.
- **Engine Load Reaction**: When carried surcharge exceeds 50% capacity ($> 768$ units), track speed reduces to 75%, reflecting realistic diesel engine torque under heavy push.

### B. World-Space Blade Kinematics (`BladeTransform`)
- Coordinate system orientation:
  - Base forward: $\vec{f}_{\text{base}} = (-\sin(\text{yaw}), 0, \cos(\text{yaw}))$
  - Base right: $\vec{r}_{\text{base}} = (\cos(\text{yaw}), 0, \sin(\text{yaw}))$
  - Up vector: $\vec{u} = \text{normalize}(\vec{f} \times \vec{r})$
  - Rotated forward vector: $\vec{f} = \text{normalize}(\vec{r} \times \vec{u}_{\text{blade}})$
  *(Never use $\vec{u} \times \vec{r}$, which inverts the forward pushing direction!)*
- Cutting edge center: $\vec{p}_{\text{edge}} = \vec{p}_{\text{vehicle}} + \vec{f} \cdot \text{armLength} + \vec{u} \cdot \text{bladeHeight}$.
- Bounding box encompasses 9 cutting edge points, top moldboard points, and depth envelope.

### C. Model Space Inversion Rule
In Minecraft's entity model system (`EntityModel`), the Y axis is inverted (positive $+Y$ points downwards towards the ground, negative $-Y$ points upwards).
- **Correct Push Arm Pitch**: $\theta = \text{bladeHeight} \cdot 0.45\text{ rad}$.
- Positive `bladeHeight` ($+0.80\text{ m}$) rotates arms upward away from ground.
- Negative `bladeHeight` ($-0.60\text{ m}$) rotates arms downward into the dirt.
- A negative sign in `setupAnim` inverts the visual blade relative to the physical cutting edge.

### D. Active Reversing Heap Formation (Hałda przy cofaniu)
When pushing forward, material accumulates in front of the moldboard. When the operator shifts to reverse (**`S`** / $\vec{v} \cdot \vec{f} < -0.05$):
- The moldboard retreats and **deposits 100% of the carried load onto the ground as a 3-meter-wide heap**.
- Distributed across 5 columns spanning the full width of the blade:
  - Center: ~32% of units (mound apex)
  - Mid-Left / Mid-Right: ~24% of units each (mound shoulders)
  - Outer-Left / Outer-Right: ~10% of units each (mound edges)
- Every position is marked `SIMULATE` so Groundworks' `GranularRelaxationEngine` naturally settles the pile into its angle of repose.
- Full capacity gauge in HUD drops from 100% to 0%, accompanied by `DIRT_PLACE` / `SAND_PLACE` / `GRAVEL_PLACE` settling audio.
- Strictly conserves volume: $\Delta \text{WorldUnits} + \Delta \text{CarriedUnits} = 0$.

### E. Terrain Leveling & Depression Filling
- **Grading Peaks**: Microvoxels above the blade cutting line are shaved off and added to carried units.
- **Filling Ruts**: Carried material fills depressions **behind** the blade cutting edge ($\vec{p}_{\text{edge}} - \vec{f} \cdot 0.65\text{ m}$) flush with the grade, leaving a flat, graded floor behind the bulldozer.
- **Lateral Spill (Windrows)**: When the blade reaches capacity ($1536$ units), excess material spills around the left and right wings (48 units/side), leaving characteristic side windrows.

### F. Differential Track Steering Physics
- **In-Place Pivot**: Throttle neutral ($W/S = 0$), Steer active ($A/D$):
  - Steer Right ($D$): Left track drives forward ($+0.85 \cdot V_{\text{max}}$), Right track reverses ($-0.85 \cdot V_{\text{max}}$) $\to$ clockwise pivot.
  - Steer Left ($A$): Left track reverses ($-0.85 \cdot V_{\text{max}}$), Right track drives forward ($+0.85 \cdot V_{\text{max}}$) $\to$ counterclockwise pivot.
- **Curvature Driving**: Under throttle, outer track runs faster than inner track ($1.0$ vs $0.35$ speed).

---

## 9. Positional Diesel Audio (`EngineSoundProfile`)

Both construction vehicles implement custom client-side positional looping engine audio:
- Excavator: 12 Hz diesel pulse rate (`engine_loop.ogg`).
- Bulldozer: 10 Hz deep industrial diesel rumble (`engine_loop.ogg`).
- Sound instances derive from `AbstractTickableSoundInstance`, loop while operating, track vehicle coordinates in 3D, and dynamically scale volume and pitch with crawler track speed:
  $$\text{Volume} = \text{lerp}(\text{speedRatio}, \ V_{\text{idle}}, \ V_{\text{load}})$$
  $$\text{Pitch} = \text{lerp}(\text{speedRatio}, \ P_{\text{idle}}, \ P_{\text{load}})$$
- Attenuation is linear up to 48 blocks.
- Scraping sounds (`ROOTED_DIRT_BREAK`, `SAND_BREAK`, `GRAVEL_BREAK`) trigger periodically during active terrain excavation.

---

## 10. Texture Atlas Architecture (512x512 Non-Overlapping UV)

- Master entity textures must be formatted at **$512 \times 512$** using 2D non-overlapping bin packing:
  - Row 1 ($v=0..70$): Tracks, deck plate, chassis frame.
  - Row 2 ($v=74..118$): Machinery house, engine hood, radiators, louvers.
  - Row 3 ($v=122..148$): Cab roof, glass frames, counterweight with hazard stripes, push arms, rollers.
  - Row 4 ($v=152..230$): Blade moldboard, cutting lip, side wings, hydraulic cylinders.
  - Row 5 ($v=234..290$): Dynamic granular material surcharge layer, operator seat, controls.
- Every cube box in `ModelPart` must reference its unique UV offset to guarantee crisp, undistorted visuals.

---

## 11. Verification & Testing Workflow

Always verify all modifications through the automated pipelines:

```bash
# 1. Full compilation and unit tests (all 15 tests)
./gradlew test

# 2. Automated graphical client visual regression tests
./gradlew runClientGameTest

# 3. Complete build verification
./gradlew build
```

- Invariant assertions verify volume conservation across multi-tick grading passes, chunk-boundary pushing, reversing heap formation, and differential track kinematics.
