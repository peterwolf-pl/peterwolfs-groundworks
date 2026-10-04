# Networking Design

## 1. Authority Model

- **Server is Authoritative:** The server owns the true state of every microvoxel.
- **Client Predicts & Renders:** Clients receive updates and generate visual meshes.

## 2. Bandwidth Conservation Protocol (Stage 1D Roadmap)

Sending 512 bits (64 bytes) for every micro-excavation is wasteful. We use a multi-tiered sync protocol:

1. **Cell Revision Check:** Clients and servers track a monotonically increasing `revision` per cell.
2. **Bitset XOR Delta:**
   $$\Delta_{\text{bits}} = \text{occupancy}_{\text{new}} \oplus \text{occupancy}_{\text{old}}$$
   Only changed words and bits are encoded.
3. **Full State Resync:** Used only on chunk load, client join, or revision divergence.
