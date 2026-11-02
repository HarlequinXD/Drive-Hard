# Drive Hard

Open-world 2.5D action/driving game for Android phones (Driver 3-style), set in a procedurally
generated modern Japanese island city with a PS1 low-poly look. Written in plain Java with a
software renderer, no engine.

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
- **City (modern Japan):** street grid following the terrain, vehicles keep left, signals on
  the far-left corner, crosswalks, curbs, sidewalks. Districts: a central business district
  of office towers and department stores around the main station, an entertainment quarter of
  narrow multi-tenant bar buildings with vertical signboards, pachinko parlours, karaoke and
  clubs, mixed streets of apartment mansions and shophouses, suburbs of detached houses with
  tiled gable roofs, block walls and garden trees plus small apartment blocks, and a seaside
  strip of warehouses. Fire stations, police boxes (koban), convenience stores, drink vending
  machines, concrete utility poles with sagging overhead wires, parks and coin parking.
- **Railway:** an elevated line runs east-west through the city on a concrete viaduct with two
  tracks and three stations (MINATO-CHUO, SAKURAGAOKA, HIGASHI-HAMA): side platforms with
  canopies and name boards, glass station halls at street level, izakaya and ramen shops
  tucked under the tracks. Two four-car trains run the line, stopping at each station.
- **Textures:** ~810 generated at startup, including **520 shopfronts** across 59 kinds of
  business (ramen, udon, sushi, izakaya, yakitori, kissaten, bakeries, bars, snack bars,
  clubs, karaoke, game centres, convenience stores, drugstores, 100-yen shops, greengrocers,
  fishmongers, butchers, tofu, sake, florists, bookshops, electronics, fashion, kimono,
  barbers, coin laundries, estate agents, clinics, dentists, banks, cram schools, cycle shops,
  capsule hotels and more) with invented names and pseudo-kanji signage, plus apartment,
  office, glass, zakkyo, house and warehouse facades, tiled roofs and train liveries.
- **Sprites, front-on with no tilt:** everything is baked from procedural 3D models seen
  straight from the side and drawn as upright billboards that only rotate about the vertical
  axis — the player (idle/walk/run/jump, 8 directions), plants (8 directions), street lamps,
  traffic signals, utility poles, beach umbrellas, cars (16 directions) and pedestrians.
- **Japanese flora (12 species):** sakura, ginkgo, zelkova, black pine, Japanese maple, bamboo,
  cedar, camphor, azalea, hydrangea, boxwood and camellia — ginkgo-lined avenues, zelkova and
  sakura on side streets, pines on the coast, cedar forest and bamboo on the hills.
- **Cars:** 25 Japanese-style models (kei cars, kei trucks and vans, compacts, sedans, a rotary
  wedge, minivans, wagons, SUVs, taxis, patrol car, delivery vans, trucks, fire engine, city
  bus, ambulance, roadster) x 4 paint schemes = **100 styles**, 16 angles each. Traffic AI:
  left-hand lanes, car following, braking for the player, turns on smooth curves, stops at red
  / late-yellow signals.
- **Pedestrians:** **100 styles** of men and women — office workers in suits, casual wear,
  dresses, skirts, face masks, caps and sun hats. They walk the sidewalks and cross at
  crosswalks only on the walk phase.
- **Sprite cache:** car, pedestrian and plant sprites are baked on a background thread on
  demand; an LRU cache keeps memory bounded while rotating through all styles over time.
- **Traffic signals:** every intersection has a working controller (N-S green → yellow →
  all-red → E-W green → yellow → all-red, offset for a green wave); horizontal Japanese
  signal heads show the live state.
- **Player:** camera-relative touch controls, walk/run/jump, curb step-up, slope following,
  collision with buildings and props, stops at the water's edge; camera collision.

## Layout
```
app/src/main/java/com/harlequinxd/drivehard/
  core/        pure Java (no Android) — runs on desktop too
    Game.java  world + player + camera + renderer
    render/    SoftRenderer (ordering table rasteriser), Geo, Texture
    city/      City (layout + chunk generator), Terrain, ChunkManager (streaming), Chunk,
               BuildingGen, Railway (viaduct, stations, trains), GeoBuilder, TrafficLights
    tex/       TextureLibrary, JpTextures (shops, facades, signs), Kanji, PixelFont, Noise, Img
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
