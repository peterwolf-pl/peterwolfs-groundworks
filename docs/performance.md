# Performance Guidelines & Budgets

## 1. Per-Tick Simulation Budget

- **Max Cells Processed Per Tick:** 64 cells default.
- **Max Simulation Time Per Tick:** $1,000\,\mu\text{s}$ ($1.0\,\text{ms}$).
- Hard cutoff prevents tick lag or server stutter during large landslides.

## 2. Memory Footprint

- Each `GranularCell`: ~144 bytes heap overhead.
- $1,000$ active converted cells $\approx 144\,\text{KB}$.
- $10,000$ active converted cells $\approx 1.44\,\text{MB}$.
- Unconverted vanilla terrain uses $0$ additional bytes.

## 3. Telemetry & Counters

Exposed via `/groundworks debug`:
- Active granular cells
- Total granular units
- Dirty queue backlog
- Cells processed last tick
- Units moved last tick
- Simulation time ($\mu\text{s}$)
- Sync packets dispatched
