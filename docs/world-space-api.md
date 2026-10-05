# Groundworks world-space excavation and grading API

Groundworks exposes machine-facing terrain operations in world coordinates through `GroundworksApi`.

## Goals

- Keep machine mods out of `GranularWorldStorage`, `GranularBlockEntity`, dirty flags, and sync internals.
- Let buckets, blades, graders, loaders, and future machines operate from their physical world-space geometry.
- Preserve strict integer material accounting.
- Allow a brush to cross vanilla block boundaries without clipping the deformation at the block edge.
- Keep legacy block-position excavation and deposition methods backward compatible.

## World-space queries

```java
boolean diggable = GroundworksApi.isDiggable(level, blockPos);
boolean occupied = GroundworksApi.containsMaterialAt(level, worldPoint);

double surfaceY = GroundworksApi.getSurfaceWorldY(
        level,
        blockPos,
        worldPoint.x,
        worldPoint.z
);
```

Unconverted convertible blocks are treated as full terrain for queries.

## World-space excavation

Standard machine contact brush:

```java
ExcavationResult result = GroundworksApi.excavateAt(
        level,
        bucketToothWorldPosition,
        maxUnits
);
```

Custom radius:

```java
ExcavationResult result = GroundworksApi.excavateSphere(
        level,
        contactPoint,
        0.55D,
        maxUnits
);
```

The spherical brush is evaluated in absolute world coordinates and can modify multiple neighboring Groundworks cells in one call.

`ExcavationResult` remains a single-material result. If the brush intersects several materials, one call removes only the first material reached by distance order. A caller can issue another operation for the remaining material.

## Grading

Shave terrain down to an absolute cutting grade:

```java
ExcavationResult cut = GroundworksApi.excavateAbove(
        level,
        cellPos,
        bladeWorldY,
        maxUnits
);
```

Fill a depression without placing material above the absolute target grade:

```java
DepositResult fill = GroundworksApi.fillBelow(
        level,
        cellPos,
        targetWorldY,
        material,
        availableUnits
);
```

Groundworks quantizes the geometric grade to its 1/8 block microvoxel resolution.

## Relaxation

After a machine creates a berm, windrow, pile, or filled depression:

```java
GroundworksApi.markForSimulation(level, changedPos);
```

This schedules normal Groundworks angle-of-repose settling.

## Conservation rule

Geometry uses floating point world coordinates. Material accounting does not.

Every successful terrain mutation is still expressed as integer Groundworks units:

```text
512 units = 1 full Minecraft block = 1.000 m3
```

Machine code must always account for returned `unitsRemoved`, `unitsDeposited`, and `unitsRejected`.
