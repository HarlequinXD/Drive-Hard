# Drive Hard

Open-world 2.5D action/driving game for Android phones (Driver 3-style), set in a procedurally
generated Miami-like city with a PS1 low-poly look. Written in plain Java + OpenGL ES 2.0, no engine.

## Current state
- **Software renderer, PS1 style (no OpenGL, no z-buffer).** CPU rasteriser into a ~240p
  framebuffer, scaled up with nearest-neighbour. Depth uses a PlayStation-style **ordering
  table** (4096 depth buckets) per draw layer: water → ground/roads → road markings & paths →
  raised sidewalks/curbs → shadows → objects, plus per-polygon bias. Vertex snapping, affine
  texturing, 15-bit dithered colour, fog, near-plane clipping, PS1-style draw distances
  (polygons 120 m, small detail 55 m, sprites 105 m, umbrellas 60 m), near-camera subdivision,
  crack sealing, multi-threaded band rasterisation.
- **Culling:** per-chunk and per-batch (each building is its own batch) frustum culling,
  per-polygon backface/distance culling, per-sprite frustum culling, and terrain occlusion
  culling (buildings and sprites hidden behind hills are skipped via heightfield ray tests).
- **Streaming island.** The world is a procedural island generated in 64 m chunks on a
  background thread around the player and unloaded when far away (~45 chunks in memory).
  Terrain heightfield: gently rolling city plateau, hills up to ~40 m around it, beaches all
  around the coast, the Ocean Drive beach on the east, open sea.
- **City:** street grid following the terrain, palm-lined avenues with medians, crosswalks,
  stop lines, curbs, sidewalks, art-deco / residential / hotel / office / commercial / garage /
  industrial buildings with foundations on slopes, parks, plazas, parking lots, beach
  promenade, lifeguard huts. ~390 generated textures incl. 120 shopfronts with invented names.
- **8-direction sprites** baked from procedural 3D models: the player (idle/walk/run/jump),
  palm trees (3 variants), street lamps, traffic signals (green/yellow/red frames) and beach
  umbrellas (4 colours).
- **Miami flora (18 species, 8-direction sprites baked from 3D models):** coconut, royal, sabal,
  Washingtonia, Canary date and foxtail palms; banyan, live oak with Spanish moss, royal poinciana,
  gumbo limbo; sea grape, red mangrove, bougainvillea, hibiscus, croton, bird of paradise,
  sea oats and agave — placed by zone (royal palms on medians, oaks/poincianas on residential
  streets, banyans in parks, shrubs in front gardens, dunes with sea oats and sea grape,
  mangroves on the wild coast, mixed hammock on the hills).
- **Cars:** 25 procedural models (hatchbacks, sedans, 80s boxy, luxury, coupes, sports, wedge,
  muscle, wagon, SUVs, pickups, minivan, cargo van, box truck, convertible, jeep, limo, taxi,
  police, 50s finned cruiser, bug, old pickup) x 4 paint schemes = **100 styles**, 16 angles each.
  Traffic AI: right-hand lanes, car following, braking for the player, left/right/straight
  turns on smooth curves, stops at red / late-yellow signals.
- **Pedestrians:** **100 styles** of men and women (all skin tones, heights, builds, elders;
  tank tops, tees, long sleeves, dresses, shorts, trousers, skirts; ponytails, buns, afros,
  long/short hair; caps, sun hats, sunglasses, beards). They walk the sidewalks and cross at
  crosswalks only on the walk phase.
- **Sprite cache:** car, pedestrian and plant sprites are baked on a background thread on
  demand; an LRU cache keeps memory bounded while rotating through all styles over time.
- **Traffic signals:** every intersection has a working controller (N-S green → yellow →
  all-red → E-W green → yellow → all-red, offset for a green wave); mast-arm signals show the
  live state. `TrafficLights.state(...)` / `timeToChange(...)` are ready for cars.
- **Player:** camera-relative touch controls, walk/run/jump, curb step-up, slope following,
  collision with buildings and props, stops at the water's edge; camera collision.

## Layout
```
app/src/main/java/com/harlequinxd/drivehard/
  core/        pure Java (no Android) — runs on desktop too
    Game.java  world + player + camera + renderer
    render/    SoftRenderer (ordering table rasteriser), Geo, Texture
    city/      City (layout + chunk generator), Terrain, ChunkManager (streaming), Chunk,
               BuildingGen, GeoBuilder, TrafficLights
    tex/       TextureLibrary, Facades, Storefronts, ShopNames, PixelFont, Noise, Img
    model/     CharacterSpec, CharacterModel, Animations, Pose, PropModels, Flora, Cars, Loft
    sim/       Traffic (car AI), Pedestrians
    sprite/    SpriteBaker, SpriteSheet, PropSprites, SpriteCache (background baking, LRU)
    game/      Player, OrbitCamera, World (grid-indexed collision), Box
  GameView     SurfaceView loop: game thread, framebuffer -> Bitmap -> screen, touch HUD
  input/       TouchControls
tools/         RenderShot (screenshots + timing), Tex/Prop/Flora/Car/Ped previews, BakePreview, SimTest
```

## Building
Every push to `main` builds the APK on GitHub Actions and publishes it as the **latest**
release (`DriveHard.apk`). The APK is signed with a fixed key in the repo, so new builds
install over old ones.

Each CI run also uploads a `previews` artifact with desktop-rendered screenshots (same renderer
as the phone), texture sheets and sprite sheets.
