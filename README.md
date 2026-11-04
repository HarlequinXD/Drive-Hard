# Drive Hard

A PS1-inspired open-world driving and exploration game with a shared Java game engine and CPU software renderer.

## Android

The Android app is in `android-app/`; it reuses the platform-independent engine in `app/src/main/java/.../core/` and provides touch controls, a touch-operated minimap and island map, and fast travel.

- Build a debug APK with `gradle :android-app:assembleDebug`.
- Find it at `android-app/build/outputs/apk/debug/android-app-debug.apk`.
- The Android GitHub Actions workflow builds and uploads the APK as `DriveHard-Android-debug`.
- Landscape orientation is required. Drag the left stick to walk or steer, use the right-side gas/brake/jump/car buttons, and drag the right side of the screen to orbit the camera. Tap the minimap or MAP button to open the island map; drag to pan, pinch or tap +/- to zoom, and tap a town followed by TRAVEL to fast travel.

## Windows

See [README-WINDOWS.md](README-WINDOWS.md) for Windows packaging and Xbox/keyboard controls. The desktop app and Android app are built independently; Android does not include the Swing desktop frontend or its JNA dependency.

Build automation: `.github/workflows/android.yml` and `.github/workflows/build-windows.yml`.
