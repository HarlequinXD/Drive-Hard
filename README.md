# Drive Hard

Open-world 2.5D action/driving game for Android phones (Driver 3-style), set in a procedurally
generated modern Japanese island city with a PS1 low-poly look. Written in plain Java with a
software renderer, no engine.

## Current state
- **Mode 7 ground + 3D models and sprites (no OpenGL).** CPU renderer into a 360x240
  framebuffer (the Game Boy Advance's 3:2 shape), scaled up with nearest-neighbour to fill the
  screen height. The ground is not geometry: the island is a
  flat, top-down **painted map** (streamed in 64 m tiles at 12.5 cm per texel (512x512, 15-bit colour, with mipmaps)) plus
  a height map, projected **Mode 7 style**, one screen column at a time (a height-mapped Mode 7,
  like voxel-space engines), so hills rise out of the map and hide what lies behind them. The
  camera never really pitches; it shifts the horizon (a y-shear), so vertical lines stay
  vertical and the ground projects exactly under the models standing on it.
- **Buildings are a BSP sector ray caster, not geometry.** Like Doom/Build maps, every
  building is a set of **sectors** (convex footprints whose floor is a plane, raised from the
  ground up: flat decks or sloped roof planes) bounded by **linedefs** (walls). Generators
  build a 2D blueprint: masses with storeys, walls tiled per storey with **wall, window,
  door, shopfront and facade tiles**, roofs made of sloped sectors (two for a gable, four for
  a hip, a hipped skirt with a gable wall above for irimoya, one for a mono-pitch, a deck in a
  parapet ring for flat roofs), roof plant and towers as smaller sectors on top. Property walls (block walls and hedges round
  house lots, with gate openings) and drink vending machines (a lit front tile) are sectors too. Each chunk
  keeps a **BSP tree** over its walls; every screen column's ray walks the trees front to back
  as it marches the Mode 7 ground, so walls and roofs are drawn in the same pass as the
  terrain and stand on it (walls run down to the actual ground, foundation below the floor).
  Bridges, barriers, signs and small props are whole polygons with **perspective-correct**
  texturing; trees, cars and people are true **sprites**, scaled rectangle blits (with the
  camera level, an upright billboard is always parallel to the screen). The ground fills a
  depth buffer that polygons and sprites test and write, so hills hide things and nothing
  mis-sorts; a draw-layer ordering table still settles coplanar ties (shadows, decals).
  15-bit dithered colour, fog, near-plane clipping, draw distances, multi-threaded column
  (ground) and band (polygons) rendering. The game runs at 30 fps (a software renderer at 60 fps overheats phones); when Android reports the phone heating up (thermal status / headroom) it drops to 24 then 20 fps with fewer render threads, and it asks for sustained-performance clocks.
- **Day and night, seasons and weather.** A full day passes in 24 minutes (one game hour a
  minute). The sky, sun light and fog colour follow the clock: dawn glow, blue noon, golden
  sunset, dusk, and a starry night with the moon. At night windows light up room by room (warm
  lamps, the odd blue television), and shopfronts stay lit. The year has two seasons of four days:
  the **wet season** (showers, rain, afternoon thunderstorms with lightning and bolts in the
  sky, overcast days, morning fog, rainbows after the rain) and the **dry season** (clear and
  fair-weather days, dusty haze, the grass and paddocks browning toward its end). Weather spells
  last one to four hours and fade into each other; clouds are a drifting layer 900 m up; rain
  falls as streaks across the screen. The HUD shows the clock, day, season and weather.
- **Culling:** per-chunk and per-batch frustum culling, BSP/bounding-box culling of wall rays,
  per-polygon backface/distance culling, per-sprite frustum culling, and terrain occlusion
  culling (buildings and sprites hidden behind hills are skipped via heightfield ray tests).
- **Streaming island.** A ~100 km long island (seed 20261103) generated the way real
  volcanic-arc islands look: a chain of lobes along a bent axis, peninsulas, bays and coves
  from layered domain warping, mountain ranges in the interior, hill country and coastal plains
  around them. The coast changes along its length between sandy beaches, rocky shores and
  cliffs dropping into deep water. The world is generated in 64 m chunks on background
  threads around the player and dropped when far away (~45 chunks in memory). The city sits on
  a straight stretch of the east coast.
- **Spline road network that grows like a real one.** Settlements are placed where people
  build: flat ground in valleys, on the coastal plain and round bays, spaced out like real
  towns. A coastal highway rings the island through the coastal towns and the city; freeways
  run from the city to the most important distant towns with diamond interchanges (and exit
  towns); highways join the towns across the island; country roads link neighbouring
  settlements. Every road is routed over the terrain with A*: it follows valleys and the
  coastal plain, avoids steep grades, cliffs, beaches and towns, and keeps clear of roads already
  built, meeting them at junctions; roads that cross a freeway bridge over it. The height map
  is cut and filled to each road's profile. Roads are splines that **paint** into the ground
  map: lanes and markings, junction surfaces with kerb returns, stop lines and zebra crossings,
  and a dusty shoulder fading into the grass. Only bridge decks are real road geometry;
  freeways have median barriers and guardrails, slip roads taper off the kerb lane, and country
  roads and town streets have utility poles with wires.
- **Painted ground, in layers:** grass, hill grass, rock, sand, sea and shallows, crop fields
  (with paddy levees), farm yards and car parks, the city's sidewalks, lots, parks, plazas and
  medians, then the roads. Ground types melt into each other along irregular noisy borders,
  and the map is lit from the height map's slopes with soft lighter and darker patches.
- **Countryside in paddocks.** Farm districts are divided into paddocks of a few acres on a
  grid lined up with the nearest road, with hedgerows where districts meet: rice paddocks split
  into small paddies with levees (flooded, green or golden by area), vegetable strips, ploughed
  soil, orchards in rows, fallow ground, tea on gentle slopes, and pasture fenced with
  post-and-wire fences. Farmsteads along the roads (farmhouse, timber barn, vinyl greenhouse,
  gravel yard, shelter trees and bamboo).
- **Named towns and regions.** The island is divided into named regions, and ~100
  settlements each get a generated Japanese name (Takahara, Shimo-Kuronuma, ...). Hamlets are
  a junction with scattered houses; villages and towns have their own grid of narrow streets
  with green walking strips, and roads from elsewhere arrive at the edge of the grid. Every
  block has two rows of house lots. Towns have a centre with the town office, post office,
  clinic, a row of shops, a supermarket with its car park and a convenience store, plus a school
  with its sports ground, a shrine with a torii and stone lanterns, and a petrol station.
  Freeway exits lead to towns built just off the interchange. Coastal towns have a fishing
  harbour: a quay, two breakwaters with an entrance, moored boats and net sheds. Arriving
  somewhere shows its name and region on screen.
- **Minimap and island map.** A north-up minimap in the corner shows the roads and places
  around you; tap it to open the island map (shaded relief, every road, names of the city,
  towns, villages and regions appearing as you zoom), drag to move, pinch to zoom.
- **Procedural houses.** 250 suburban and 150 farmhouse designs, each a seeded set of
  parameters: plan (rectangle, L with a front or back wing, offset blocks, annex, upper floor
  over part of the ground floor), storeys, roof (gable, hip, hip-and-gable irimoya,
  mono-pitch, flat with parapet), pitch and overhang, one or two-tone walls (siding, stucco,
  boards, tile, brick, panels, plaster, charred cedar), roof covering (kawara, metal, slate,
  copper), window and door styles, balcony, porch canopy, skirt roof, carport and front fence.
  Walls are built around their openings (no decals, no z-fighting) and doors sit in a recess;
  gable ends are plain wall material.
- **City (modern Japan):** street grid following the terrain, vehicles keep left, signals on
  the far-left corner, crosswalks, curbs, sidewalks. Districts: a central business district
  of office towers and department stores in the centre, an entertainment quarter of
  narrow multi-tenant bar buildings with vertical signboards, pachinko parlours, karaoke and
  clubs, mixed streets of apartment mansions and shophouses, suburbs of detached houses with
  tiled gable roofs, block walls and garden trees plus small apartment blocks, and a seaside
  strip of warehouses. Fire stations, police boxes (koban), convenience stores, drink vending
  machines, concrete utility poles with sagging overhead wires, parks and coin parking.
- **Textures:** ~810 generated at startup, including **520 shopfronts** across 59 kinds of
  business (ramen, udon, sushi, izakaya, yakitori, kissaten, bakeries, bars, snack bars,
  clubs, karaoke, game centres, convenience stores, drugstores, 100-yen shops, greengrocers,
  fishmongers, butchers, tofu, sake, florists, bookshops, electronics, fashion, kimono,
  barbers, coin laundries, estate agents, clinics, dentists, banks, cram schools, cycle shops,
  capsule hotels and more) with invented names and pseudo-kanji signage, plus apartment,
  office, glass, zakkyo, house and warehouse facades, tiled roofs.
- **Sprites, front-on with no tilt:** everything is baked from procedural 3D models seen
  straight from the side and drawn as upright billboards that only rotate about the vertical
  axis — the player (idle/walk/run/jump, 8 directions), plants (one view: they look alike from every side), street lamps,
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
  Traffic density follows the area (about 30 cars in the city, 16 in a town, 10 in a village, 12
  on the freeway, 8 on highways, 4 on country roads); cars spawn and are removed out of view,
  and cars off screen are simulated four times less often. New car and pedestrian styles are
  baked slowly once the sprite cache is full, to spare the CPU.
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
    render/    SoftRenderer (Mode 7 ground + sector ray casting, perspective-correct polygons, sprite blits),
               Sectors (sectors, tiled walls, BSP tree, ray queries), Ground (map tiles), Geo, Texture
    city/      City (layout + chunk generator), Terrain, ChunkManager (streaming), Chunk,
               BuildingGen, HouseGen (procedural houses), TownGen (town-centre buildings),
               SectorBuilder (building blueprints to sectors: masses, roof pieces, wall tiles),
               Countryside (fields, farms, town lots), GroundPainter (paints the map tiles), GeoBuilder, TrafficLights
    road/      RoadNetwork (spline road graph, terrain shaping, spatial index), RoadEdge,
               RoadNode, RoadClass, Town + PlaceNames (named towns and regions), RoadMesh (bridge decks, barriers, poles)
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
