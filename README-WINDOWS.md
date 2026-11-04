# Drive Hard — Windows conversion

This archive is an **in-place conversion overlay** for the existing `HarlequinXD/Drive-Hard` repository. It uses the exact existing `app/src/main/java/com/harlequinxd/drivehard/` package layout, and does not introduce a `windows/` subproject.

## Apply
1. Open your Drive-Hard repository on your computer.
2. Copy the contents of this archive into the repository **root**, merging folders and replacing `settings.gradle`, `build.gradle`, and `app/build.gradle`.
3. Delete `.github/workflows/build-apk.yml` from the repository (otherwise the old Android workflow will fail after the Gradle conversion).
4. Commit and push. Go to GitHub **Actions → Build Windows EXE**.
5. Download the `DriveHard-Windows` artifact, unzip `DriveHard-Windows.zip`, and run `DriveHard/DriveHard.exe`. Keep all extracted files together: it bundles a Java runtime.

## Controls
- Xbox left stick: walk / steering; right stick: orbit camera
- Xbox RT: accelerate; LT: brake/reverse; A: jump; Y: enter/exit car; Start: fullscreen
- Keyboard WASD / arrow keys: movement / driving; Space: jump; E: enter/exit; F11: fullscreen

## Important
This overlay intentionally keeps the existing `core/**` Java files unchanged, since they contain the complete game. Android-only Java files and resources can remain in the repo, but are excluded from the Windows build. This ZIP is not a standalone full copy of all repository files: it must be merged with the original repository.

The Windows workflow is prepared but has not been executed in GitHub Actions yet. Windows controller polling uses JNA and XInput.
