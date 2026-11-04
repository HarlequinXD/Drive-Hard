# Drive Hard — Windows desktop edition

This is a **complete Windows-only repository**, not an overlay. The game simulation, procedural world, traffic, pedestrians, and original PS1-style software renderer remain in `app/src/main/java/com/harlequinxd/drivehard/core/`. The Windows Swing frontend is in `.../desktop/`.

## Upload to GitHub

Extract the ZIP, open the `Drive-Hard-main` directory, and upload its **contents** to the root of your `HarlequinXD/Drive-Hard` repository. Keep the directory structure, especially `.github/workflows/build-windows.yml`. The `.github` folder may be hidden by your file manager. For a clean replacement, delete the existing GitHub repository files first or use Git locally to replace the tree; uploading a ZIP as one file does not create a workflow. Remove any existing `.github/workflows/build-apk.yml` if it remains from an older revision.

Commit to the `main` branch. GitHub Actions automatically runs **Build Windows EXE** on pushes to `main`; it can also be run via Actions → Build Windows EXE → Run workflow.

Once the build succeeds, download the `DriveHard-Windows` artifact, unzip it, then unzip `DriveHard-Windows.zip`. Launch `DriveHard/DriveHard.exe`. Keep the adjacent `app` and `runtime` directories with the EXE. No Java installation is needed on the target PC.

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

The Android UI, Android manifest, and APK workflow are removed. The Windows version retains software rendering (not OpenGL). Controller support uses the JNA dependency to call Windows XInput. This package has been source-compiled and the core simulation tests passed in a Linux Java environment; the actual Windows GitHub Actions EXE packaging has **not** been run here.
