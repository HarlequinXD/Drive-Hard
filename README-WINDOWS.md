# Drive Hard — Windows desktop edition

This is a **complete Windows-only repository**, not an overlay. The game simulation, procedural world, traffic, pedestrians walking on the city and settlement footpaths, and original PS1-style software renderer remain in `app/src/main/java/com/harlequinxd/drivehard/core/`. The Windows Swing frontend is in `.../desktop/`.

## Upload to GitHub

Extract the ZIP, open the `Drive-Hard-main` directory, and upload its **contents** to the root of your `HarlequinXD/Drive-Hard` repository. Keep the directory structure, especially `.github/workflows/build-windows.yml`. The `.github` folder may be hidden by your file manager. For a clean replacement, delete the existing GitHub repository files first or use Git locally to replace the tree; uploading a ZIP as one file does not create a workflow. Remove any existing `.github/workflows/build-apk.yml` if it remains from an older revision.

GitHub Actions automatically runs **Build Lightweight Windows Game** whenever you push to any branch or open/update a pull request. You can also start it manually from Actions → Build Lightweight Windows Game → Run workflow.

Once the build succeeds, open its GitHub Actions run and download the `DriveHard-Lightweight-Windows` artifact from the Artifacts section at the bottom. Extract it, then launch `DriveHard.exe` with `DriveHard.jar` and the `lib` folder kept alongside it. This lightweight package requires Java 17 or newer installed on the target PC.

## Controls

- Xbox controller (XInput): left stick walk/steer; right stick camera; RT accelerate; LT brake/reverse; A jump; Y enter/exit vehicle; Start toggle fullscreen.
- Keyboard: WASD/arrow keys walk or steer/accelerate/brake; Space jump; E enter/exit vehicle; F11 fullscreen.

## Local build

On Windows with JDK 17 and Gradle 8.14.3:

```
gradle :app:clean :app:prepareWindowsPackage
```

Then use JDK 17's `jpackage` to create an app-image from `app/build/windows-input` (the GitHub workflow does this automatically).

## Build notes

The Windows frontend retains software rendering (not OpenGL). Controller support uses the JNA dependency to call Windows XInput. The Android app is a separate `android-app` module and does not change this Windows package or include Swing/JNA. The actual Windows GitHub Actions EXE packaging has **not** been run here.

## Island map and fast travel (Windows)
- The minimap is always visible in the top-right corner while playing.
- Press **Xbox View/Select/Back** (or **M**) to open or close the full island map.
- Pan with **left stick** or **D-pad** (or WASD/arrow keys); on PC, **left-drag** the map.
- Zoom with the **mouse wheel**, on-screen **+ / -** buttons, keyboard **+ / -** or Page Up/Down, or hold controller **LT/RT** or **LB/RB** for repeated zoom steps.
- Move the cursor over a yellow town/city marker and press **A** (or Enter) to open the fast-travel confirmation.
- Press **A** again to fast travel; **B** (or Escape) cancels the menu or closes the map.
- Fast travel uses the existing island map locations and streams the destination area. It is disabled while entering/exiting a car.
