# Drive Hard

Open-world 2.5D action/driving game for Android phones (Driver 3-style), set in a procedurally
generated Miami-like city with a PS1 low-poly look. Written in plain Java + OpenGL ES 2.0, no engine.

## Current milestone: player framework
- **Procedural character → sprites.** The player (woman, tank top, denim shorts, sneakers,
  honey blonde ponytail) is modelled in code as a low-poly 3D rig and baked on the phone at
  startup into an **8-direction sprite atlas** (48x64 px frames, 32 px = 1 m):
  idle (4), walk (8), run (8), jump (rise/apex/fall/land).
- **Pedestrian-ready generator.** `CharacterSpec.randomPedestrian(seed)` varies sex, height,
  build, skin, hair style/colour, top, bottoms and shoes; same baker.
- **PS1 renderer.** 240p offscreen buffer upscaled with nearest filtering, vertex snapping,
  affine texture mapping, 15-bit colour with ordered dithering, screen-door transparency, fog.
- **Gameplay.** Orbit third-person camera (drag right side), camera-relative movement
  (floating joystick on the left; push past ~70% to run), jump with coyote time, step-up
  onto curbs/stairs, box collision.
- **Test level.** Crossroads, sidewalks, plaza, grass/sand, stairs, crates, pillars, buildings,
  all textured with procedurally generated 32x32 textures.

## Layout
```
app/src/main/java/com/harlequinxd/drivehard/
  core/        pure Java (no Android) — runs on desktop too
    model/     CharacterSpec, CharacterModel (rig + lathe meshes), Animations, Pose
    sprite/    SpriteBaker (CPU rasterizer), SpriteSheet
    game/      Player, OrbitCamera, World, TestWorld, Box
    gfx/       ProcTextures, MeshData
  gl/          GameRenderer, Shaders, Hud, GlUtil (GLES 2.0)
  input/       TouchControls
tools/         BakePreview (writes sprite PNGs), SimTest (controller checks)
```

## Building
Every push to `main` builds the APK on GitHub Actions and publishes it as the **latest**
release (`DriveHard.apk`). The APK is signed with a fixed key in the repo, so new builds
install over old ones.

Desktop preview of the sprite baker (JDK only): `tools/preview.sh` → `build/preview/*.png`.
