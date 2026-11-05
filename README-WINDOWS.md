# Drive Hard — Windows desktop edition

This is a **complete Windows-only repository**, not an overlay. The game simulation, procedural world, traffic, pedestrians walking on the city and settlement footpaths, and original PS1-style software renderer remain in `app/src/main/java/com/harlequinxd/drivehard/core/`. The Windows Swing frontend is in `.../desktop/`.

## Upload to GitHub

Extract the ZIP, open the `Drive-Hard-main` directory, and upload its **contents** to the root of your `HarlequinXD/Drive-Hard` repository. Keep the directory structure, especially `.github/workflows/build-windows.yml`. The `.github` folder may be hidden by your file manager. For a clean replacement, delete the existing GitHub repository files first or use Git locally to replace the tree; uploading a ZIP as one file does not create a workflow. Remove any existing `.github/workflows/build-apk.yml` if it remains from an older revision.

GitHub Actions automatically runs **Build Lightweight Windows Game** whenever you push to any branch or open/update a pull request. You can also start it manually from Actions → Build Lightweight Windows Game → Run workflow.

Once the build succeeds, open its GitHub Actions run and download the `DriveHard-Lightweight-Windows` artifact from the Artifacts section at the bottom. Extract it, then launch `DriveHard.exe` with `DriveHard.jar` and the `lib` folder kept alongside it. This lightweight package requires Java 17 or newer installed on the target PC.

## Controls

- Xbox controller (XInput): left stick walk/steer; right stick camera; RT accelerate; LT brake/reverse; A jump; Y enter/exit vehicle; View map; Start pause menu (resume, map, fullscreen, quit).
- Keyboard and mouse: click the window to capture the mouse for the camera; WASD/arrow keys walk or steer, W/S gas/brake; Space jump; E or F enter/exit vehicle; M or Tab map; Esc pause menu (releases the mouse); F11 fullscreen.

## Local build

On Windows with JDK 17 and Gradle 8.14.3:

```
gradle :app:clean :app:prepareWindowsPackage
```

Then use JDK 17's `jpackage` to create an app-image from `app/build/windows-input` (the GitHub workflow does this automatically).

## Build notes

The Windows frontend retains software rendering (not OpenGL). Buildings, shared property walls, vending machines and bridges are emitted as tiled polygon meshes and sorted through a PS1-style ordering table; polygons do not depth-test each other, but the terrain writes a ground-only depth buffer so hills hide what is behind them. The height-mapped ground remains ray-marched. Controller support uses the JNA dependency to call Windows XInput. The Android app is a separate `android-app` module and does not change this Windows package or include Swing/JNA. The actual Windows GitHub Actions EXE packaging has **not** been run here.

## Island map and fast travel (Windows)
- A heading-up minimap sits in the top-right corner while playing, with the place name and speed top left.
- Press **View** (or **M** / **Tab**) to open or close the full island map.
- Pan with the **left stick** / **D-pad**, **WASD** / arrows, or **left-drag**; zoom with **LT/RT**, the **mouse wheel**, **+ / -** or the on-screen buttons.
- **LB / RB** (or **Q / E**, or the **< >** buttons) step through places nearest first; **Y** (or **C**, or **ME**) re-centres on you.
- Press **A** / **Enter** or click a place to open the fast-travel dialog, then **A** / **Enter** or **TRAVEL** to go; **B** / **Esc** goes back.
- Fast travel lands you on a street at the destination (in a lane if you are driving), builds the streets around it behind a travel screen, and repopulates traffic and pedestrians there.
