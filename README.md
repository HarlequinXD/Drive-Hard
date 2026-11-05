# Drive Hard

A PS1-inspired open-world driving and exploration game with a shared Java game engine and CPU software renderer.

Procedural buildings, property walls, bridges and vending machines render as tiled, textured polygons, sorted far-to-near through a PS1-style ordering table; polygons never depth-test each other. The ray-marched ground keeps a ground-only depth buffer that polygons and sprites test (but never write), so hills hide the houses, trees and props behind them.

Each desktop game starts with a fresh seed and generates a different island about 128 km across. The city is a compact downtown with named suburbs spread around it (countryside in between), surrounded by towns, villages, farmland and gravel or dirt farm tracks with farmsteads and country houses along them. Explicitly seeded games remain deterministic.

The load screen shows the title, what is being generated and a progress bar; fast travel shows the same screen while the destination streets are built. The minimap, HUD, island map, fast-travel dialog and pause menu are drawn by the shared core (`core/ui/`), so the desktop and Android builds look the same.

## Android

The Android app is in `android-app/`; it reuses the platform-independent engine in `app/src/main/java/.../core/` and provides touch controls, a touch-operated minimap and island map, and fast travel.

- Build a debug APK with `gradle :android-app:assembleDebug`.
- Find it at `android-app/build/outputs/apk/debug/android-app-debug.apk`.
- The Android GitHub Actions workflow builds and uploads the APK as `DriveHard-Android-debug`.
- Landscape orientation is required. The game renders at a resolution matched to the screen's shape (no letterboxing).
- Touch the left side anywhere to get a floating stick (walk / steer). On foot: JUMP and CAR on the right, drag the rest of the right side to look around. Driving: GAS and BRAKE pedals (hold brake to reverse) and EXIT.
- Tap the minimap to open the island map: drag to pan, pinch or +/- to zoom, < > to step through places nearest first, tap a place and TRAVEL to fast travel. The pause button (top left) or Back opens the menu.

## Windows

See [README-WINDOWS.md](README-WINDOWS.md) for Windows packaging and Xbox/keyboard controls. The desktop app and Android app are built independently; Android does not include the Swing desktop frontend or its JNA dependency.

Build automation: `.github/workflows/android.yml` and `.github/workflows/build-windows.yml`.
