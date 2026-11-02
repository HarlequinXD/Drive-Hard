# Drive Hard

Open-world 2.5D action/driving game for Android phones (Driver 3-style), set in a procedurally
generated Miami-like city with a PS1 low-poly look. Written in plain Java + OpenGL ES 2.0, no engine.

## Current state
- **Software renderer, PS1 style (no OpenGL).** Everything is rasterised on the CPU into a
  ~240p framebuffer and scaled up with nearest-neighbour. No z-buffer: polygons are depth-sorted
  with a PlayStation-style **ordering table** (4096 depth buckets per draw layer). Draw layers
  (road → road markings → raised sidewalks/curbs → shadows → objects) plus per-polygon bias
  remove the usual z-fighting; sprites sort by an upright mid-height point. Vertex snapping,
  affine texture mapping, 15-bit colour with 4x4 ordered dither, fog, near-plane clipping,
  near-camera subdivision, chunk frustum culling, multi-threaded band rasterisation.
- **Procedural Miami city.** Street grid with palm-lined avenues and planted medians,
  crosswalks, lane markings, raised sidewalks with curbs, lots with art-deco low-rises,
  apartments, beach hotels, downtown office towers on podiums, parking garages (open concrete
  decks), warehouses, parks, plazas, parking lots, street lights, a beachfront promenade, sand,
  umbrellas, lifeguard huts and ocean.
- **~390 generated textures:** 64x64 base materials (2 m tiles), 232 facades in 8 styles
  (deco, residential, hotel, office glass, commercial/brick, garage, industrial, blank concrete
  walls) and 120 shopfronts whose signs carry generated, made-up business names.
- **Player:** procedural 3D model baked to 8-direction sprites (idle/walk/run/jump), orbit
  camera, camera-relative touch controls, jump, curb step-up, collision against the city.

## Layout
```
app/src/main/java/com/harlequinxd/drivehard/
  core/        pure Java (no Android) — runs on desktop too
    Game.java  world + player + camera + renderer
    render/    SoftRenderer (ordering table rasteriser), Geo, Texture
    city/      City (layout, roads, blocks, props), BuildingGen, GeoBuilder (chunks)
    tex/       TextureLibrary, Facades, Storefronts, ShopNames, PixelFont, Noise, Img
    model/     CharacterSpec, CharacterModel, Animations, Pose
    sprite/    SpriteBaker, SpriteSheet
    game/      Player, OrbitCamera, World (grid-indexed collision), Box
  GameView     SurfaceView loop: game thread, framebuffer -> Bitmap -> screen, touch HUD
  input/       TouchControls
tools/         RenderShot (desktop screenshots + timing), TexPreview, BakePreview, SimTest
```

## Building
Every push to `main` builds the APK on GitHub Actions and publishes it as the **latest**
release (`DriveHard.apk`). The APK is signed with a fixed key in the repo, so new builds
install over old ones.

Each CI run also uploads a `previews` artifact with desktop-rendered screenshots (same renderer
as the phone), texture sheets and sprite sheets.
