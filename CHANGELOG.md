# Changelog

All notable changes to **Peterwolf's Groundworks** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.2+26.3] - 2026-10-09

### Added
- **3rd Person Camera Lateral & Vertical Panning (Przesuwanie kamery względem gracza)**:
  - While holding the camera modifier key (**`V`**, `Ctrl`, or custom keybind) in 3rd person view (`F5`), keyboard **Arrow Keys** shift the camera relative to the player:
    - **Arrow Left / Right**: Lateral strafe / horizontal camera offset ($\pm 12.0$ blocks).
    - **Arrow Up / Down**: Vertical camera height elevation / lowering ($\pm 12.0$ blocks).
  - Smooth lerp interpolation for camera offsets ensures cinematic, fluid camera panning.
  - Middle mouse click (ŚPM / Middle Click) resets both distance and lateral/vertical offsets back to default center.
- **Dedicated Rebindable Keybinding**:
  - Registered under **Options $\to$ Controls $\to$ Key Binds $\to$ Peterwolf's Groundworks**: `key.pw_groundworks.camera_zoom` ("Third Person Zoom Modifier", default `V`).
  - Completely eliminates key conflict with Axiom's context wheel / container palette under `Alt`.
- **High-Performance Adaptive Zoom**:
  - Expanded camera zoom distance up to **24.0 blocks** (ideal for large excavators, bulldozers, and cranes).
  - Dynamic step scaling for fast, responsive mouse wheel zooming.
  - Direct mouse wheel zoom enabled automatically while seated in any vehicle without requiring any modifier key.

---

## [0.1.1+26.3] - 2026-10-09

### Added
- **Smooth 3rd Person Mouse Wheel Zoom**:
  - Continuous zoom range from 2.0 to 16.0 blocks with smooth lerp interpolation.
  - Native collision raycast integration via `Camera.getMaxZoom()`.
  - Automatic hotbar scroll event cancellation while zooming in 3rd person.

---

## [0.1.0+26.3] - 2026-10-08

### Added
- Initial release of **Peterwolf's Groundworks** core volumetric granular terrain engine for Minecraft 26.3 Fabric.
- $8 \times 8 \times 8$ granular cell storage, continuous heightfield meshing, cellular relaxation engine, and public excavation/deposition API.
