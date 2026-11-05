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
- Phones with OpenGL ES 3.0 (nearly all) draw the game on the GPU (`core/gl/GlRenderer`): the same frame as the CPU renderer (same sky, ground map, sea, 15-bit colour and dither, ported to shaders), drawn at the game's low resolution and scaled up unfiltered, 30 frames a second. The CPU only runs the game, so the phone stays much cooler. Chunk meshes and ground tiles are uploaded to video memory as they stream in and freed when they stream out. Phones without OpenGL ES 3.0, or where the GPU set-up fails, use the CPU renderer.
- Landscape orientation is required. The game renders at a resolution matched to the screen's shape (no letterboxing).
- Touch the left side anywhere to get a floating stick (walk / steer). On foot: JUMP and CAR on the right, drag the rest of the right side to look around. Driving: GAS and BRAKE pedals (hold brake to reverse) and EXIT.
- Tap the minimap to open the island map: drag to pan, pinch or +/- to zoom, < > to step through places nearest first, tap a place and TRAVEL to fast travel. The pause button (top left) or Back opens the menu.

## Windows

See [README-WINDOWS.md](README-WINDOWS.md) for Windows packaging and Xbox/keyboard controls. The desktop app and Android app are built independently; Android does not include the Swing desktop frontend or its JNA dependency.

GPU renderer test (desktop, no phone needed): `tools/gl/` runs the GPU renderer headless on the system's OpenGL ES driver (Mesa works) through Java's foreign function API, and writes CPU-vs-GPU screenshots (`GlShot`), the HUD composited on a phone-sized screen (`GlHudShot`) and a streaming soak test (`GlStream`). Needs JDK 21 with `--enable-preview`:

```
javac --release 21 --enable-preview -d build/gl $(find app/src/main/java/com/harlequinxd/drivehard/core -name '*.java') tools/gl/*.java
java --enable-preview --enable-native-access=ALL-UNNAMED -cp build/gl GlShot build/glshot
```

Build automation: `.github/workflows/android.yml` and `.github/workflows/build-windows.yml`.
