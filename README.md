# Drive Hard

A PS1-inspired open-world driving and exploration game with a shared Java game engine and CPU software renderer.

Procedural buildings, property walls, bridges and vending machines render as tiled, textured polygons, sorted far-to-near through a PS1-style ordering table; polygons never depth-test each other. The ray-marched ground keeps a ground-only depth buffer that polygons and sprites test (but never write), so hills hide the houses, trees and props behind them.

The first desktop launch picks a random seed and generates an island about 128 km across; later launches return to the same island until **NEW ISLAND** is chosen in the pause menu (or the game is started with `--new-island`). The city is a compact downtown with named suburbs spread around it (countryside in between), surrounded by towns, villages, farmland and gravel or dirt farm tracks with farmsteads and country houses along them. Explicitly seeded games remain deterministic.

The load screen shows the title, what is being generated and a progress bar; fast travel shows the same screen while the destination streets are built. The minimap, HUD, island map, fast-travel dialog and pause menu are drawn by the shared core (`core/ui/`), so the desktop and Android builds look the same.

## Game data (world cache)

Like Minecraft's region files, the generated world is saved in a `GameData` folder (beside the game on Windows, in the app's private storage on Android):

- `world-<seed>-.../island.bin`: the island (terrain, roads, towns, farms and building textures), written in the background on the first launch and read back on later launches instead of being generated (about 1 s instead of 5 s here).
- `world-<seed>-.../chunks/<x>_<z>.bin`: each 64 m chunk (meshes, collision, props and painted ground), written the first time it is generated and read from disk after that.

Only the chunks around the player are in memory; they are dropped as the player moves on. Nothing in the world changes during play, so the cache only saves time: it is limited in size (400 MB on PC, 160 MB on phones; the least recently used chunks go first), a new build of the game starts a fresh cache, and deleting `GameData` is always safe.

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
