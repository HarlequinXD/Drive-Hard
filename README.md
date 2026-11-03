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
- **Streaming island, 128 x 128 km.** The world is generated in 64 m chunks on a background
  thread around the player and unloaded when far away (~45 chunks in memory). The city sits
  on the east coast on a gently rolling plateau ringed by hills; inland the terrain opens into
  coastal plains, farmland, rolling hill country and mountain ranges up to ~500 m, with
  beaches all around the coast.
- **Spline road network.** Every road is an edge of one connected graph holding a Catmull-Rom
  spline and a smoothed, grade-limited height profile: the city street grid, three freeways
  out of the city (west across the island, north and south up the coast) with diamond
  interchanges and one-way slip roads, coastal highways, and ~3,800 km of highways and winding
  country roads linking ~180 villages. Country roads that cross get a junction; roads that cross
  a freeway go over it on a bridge with piers. Terrain is cut and filled to each road's profile
  (embankments and cuttings). All roads, city streets included, are drawn
  from the graph: lane-wide strips following the spline (markings painted into lane textures),
  trimmed at each junction, where one junction surface joins every road that meets there with
  curved kerb returns; road mouths carry stop lines (and zebra crossings in the city). Roads
  leaving a junction at grazing angles are bent apart so junctions get a proper shape. Farm
  lanes branch off at T-junctions. Outside the city each road gets cut-and-fill verges down
  to the terrain, and the terrain grid is cut around all paving so grass never overlaps
  asphalt and nothing grows on a road. Freeways have median barriers and guardrails; slip
  roads taper off the kerb lane; country roads have utility poles with wires.
- **Ground blending:** neighbouring ground types (grass, hill grass, paddies, cabbage, soil,
  tea, sand) blend across their shared edges with dithered overlays, and the ground is
  vertex-shaded with soft lighter/darker patches.
- **Countryside.** Rice paddies (flooded, green or golden by district) with grass levees,
  cabbage fields, ploughed soil and fallow parcels on the lowland, tea rows on gentle slopes,
  cedar forest on the mountains. ~2,700 farmsteads along the roads (old farmhouse, timber barn,
  vinyl greenhouse, gravel yard and driveway, shelter trees and bamboo) and village clusters of
  houses, small apartments and a convenience store at every village junction.
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
  bus, ambulance, roadster) x 4 paint schemes = **100 styles**, 16 angles each. Traffic AI
  follows the road graph: left-hand lanes, car following, slowing for bends, moving to the kerb
  lane for left turns and exits and the inner lane for right turns, cubic curves through
  junctions; signals in the city, give-way rules at rural junctions (minor road yields to
  major, equal roads yield to the left), freeway exits and merges, turning around at dead ends.
- **Pedestrians:** **100 styles** of men and women — office workers in suits, casual wear,
  dresses, skirts, face masks, caps and sun hats. They walk the sidewalks and cross at
  crosswalks only on the walk phase.
- **Sprite cache:** car, pedestrian and plant sprites are baked on a background thread on
  demand; an LRU cache keeps memory bounded while rotating through all styles over time.
- **Traffic signals:** every intersection has a working controller (N-S green → yellow →
  all-red → E-W green → yellow → all-red, offset for a green wave); horizontal Japanese
  signal heads show the live state.
- **Cars you can drive:** walk up to any car in traffic (or one you left parked) and a GET IN
  button fades in; tap it and she hops in through the driver's door (right-hand drive). While
  driving the controls switch to a D-pad (left/right steer, up gas, down brake/reverse) with GAS
  and REV buttons and a speedometer; the chase camera swings in behind the car. GET OUT pops her
  out beside the car with a little hop, and the car stays where it stopped. Cars collide with
  buildings, poles, trees and other traffic, and traffic queues behind you.
- **Player:** camera-relative touch controls, walk/run/jump, curb step-up, slope following,
  collision with buildings and props, stops at the water's edge; camera collision.

## Layout
```
app/src/main/java/com/harlequinxd/drivehard/
  core/        pure Java (no Android) — runs on desktop too
    Game.java  world + player + camera + renderer
    render/    SoftRenderer (ordering table rasteriser), Geo, Texture
    city/      City (layout + chunk generator), Terrain, ChunkManager (streaming), Chunk,
               BuildingGen, Railway (viaduct, stations, trains), Countryside (fields, farms,
               villages), GeoBuilder, TrafficLights
    road/      RoadNetwork (spline road graph, terrain shaping, spatial index), RoadEdge,
               RoadNode, RoadClass, RoadMesh (lane strips, barriers, bridges, poles)
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
