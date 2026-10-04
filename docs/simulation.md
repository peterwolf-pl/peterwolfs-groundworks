# Simulation Design

## 1. Dirty Region Philosophy

Never scan every granular cell in the world. Instead, all modifications trigger dirty propagation:

1. Cell is modified (e.g. excavated or deposited into).
2. Cell is enqueued into `GranularWorldStorage.dirtyQueue`.
3. Neighboring cells are enqueued if gradient stability must be checked.
4. Server tick processes up to `maxCellsPerTick` or until `maxSimulationMicros` is exceeded.
5. Unprocessed cells remain in the queue for subsequent ticks (no freeze, graceful degradation).

## 2. Material Conservation Invariant

Every transfer between two cells $A$ and $B$:

$$A_{\text{units}}' = A_{\text{units}} - \Delta$$
$$B_{\text{units}}' = B_{\text{units}} + \Delta$$

where $\Delta \in \mathbb{Z}^+$. No material is created or destroyed.

## 3. Stage 2 Roadmap: Cellular Automata Relaxation

When a column height difference exceeds the angle of repose:

$$\Delta h > \tan(\theta_{\text{repose}}) \cdot \text{distance}$$

Units slide along the steepest gradient towards the lower neighbor.
- Deterministic evaluation order (shuffled or fixed directional sweep to prevent directional bias).
- Integer unit transfers only.
