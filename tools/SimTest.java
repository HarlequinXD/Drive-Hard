import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.ChunkManager;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

/** Headless checks: controller, streaming collision, terrain, traffic signals. */
public class SimTest {
    static int fails = 0;

    public static void main(String[] a) {
        check("polygons are sorted far-to-near by the ordering table", orderingTableSortsPolygons());
        TextureLibrary textures = new TextureLibrary(20261103L);
        City c = new City(textures, 20261103L);
        float dt = 1f / 60f;
        OrbitCamera cam = new OrbitCamera();
        float[] mv = new float[2];

        Player p = new Player(c.spawnX, c.spawnZ);
        c.chunks.ensureAround(p.x, p.z);
        p.y = c.surface(p.x, p.z);
        cam.toWorld(0, 1, mv);
        float z0 = p.z;
        for (int i = 0; i < 60; i++) { c.chunks.ensureAround(p.x, p.z); p.update(dt, mv[0], mv[1], false, c); }
        check("stick up moves away from camera", p.z < z0 - 3f);
        check("full stick runs", p.anim.name().equals("RUN"));
        check("on the ground", p.onGround && Math.abs(p.y - c.surface(p.x, p.z)) < 0.05f);

        float maxY = 0, base = p.y;
        p.update(dt, 0, 0, true, c);
        for (int i = 0; i < 120; i++) { p.update(dt, 0, 0, false, c); maxY = Math.max(maxY, p.y - base); }
        System.out.printf("jump apex %.2f m%n", maxY);
        check("jump lands", p.onGround && Math.abs(p.y - base) < 0.05f);

        // cross a street: curb down, road, curb up
        int j0 = 3;
        // pick a crossing line clear of street furniture (poles, trees, vending machines)
        float qx = (c.rx1[2] + c.rx0[3]) * 0.5f;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.chunks.ensureAround(qx + dx * 64f, c.rz1[j0] + dz * 64f);
        search:
        for (float off = 0f; off < 20f; off += 0.5f)
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float x = qx + off * sgn;
                boolean clear = true;
                for (float z = c.rz0[j0] - 5f; z <= c.rz1[j0] + 1.5f && clear; z += 0.5f)
                    for (com.harlequinxd.drivehard.core.game.Box b : c.near(x, z))
                        if (x > b.minX - 0.6f && x < b.maxX + 0.6f && z > b.minZ - 0.6f && z < b.maxZ + 0.6f) { clear = false; break; }
                if (clear) { qx = x; break search; }
            }
        // street furniture can stand in the way: try a few crossing lines
        boolean crossed = false;
        for (int attempt = 0; attempt < 6 && !crossed; attempt++) {
            Player q = new Player(qx + attempt * 2.7f, c.rz1[j0] + 1.0f);
            c.chunks.ensureAround(q.x, q.z);
            q.y = c.surface(q.x, q.z);
            for (int i = 0; i < 700; i++) { c.chunks.ensureAround(q.x, q.z); q.update(dt, 0, -0.5f, false, c); }
            crossed = q.z < c.rz0[j0] && Math.abs(q.y - c.surface(q.x, q.z)) < 0.05f;
        }
        check("crossed the street onto the next sidewalk", crossed);

        // walk east from the promenade into the sea: she swims out and stops about 200 m offshore
        Player w = new Player(c.cityX1, c.spawnZ);
        c.chunks.ensureAround(w.x, w.z);
        w.y = c.surface(w.x, w.z);
        for (int i = 0; i < 60 * 400; i++) { c.chunks.ensureAround(w.x, w.z); w.update(dt, 1f, 0f, false, c); }
        float off = c.terrain.coastOver(w.x, w.z);
        System.out.printf("swam out to x=%.1f, %.0f m offshore, swimming %b, anim %s%n", w.x, off, w.swimming, w.anim);
        check("swims out and stops about 200 m offshore", w.swimming && off > 185f && off <= City.SWIM_REACH + 1f
                && w.anim == com.harlequinxd.drivehard.core.model.Animations.Anim.SWIM_TREAD);

        // hills exist and terrain is continuous
        float maxH = 0;
        for (float x = c.cityX0 - 400; x < c.cityX0; x += 10) maxH = Math.max(maxH, c.terrain.height(x, (c.cityZ0 + c.cityZ1) / 2));
        System.out.printf("highest hill west of city: %.1f m%n", maxH);
        check("hills west of the city", maxH > 15f);
        check("ocean east of the beach", c.terrain.height(c.cityX1 + 200, (c.cityZ0 + c.cityZ1) / 2) < -1f);

        // traffic signals: N-S and E-W never both green, cycle through all states
        boolean conflict = false; int seen = 0;
        for (float t = 0; t < TrafficLights.CYCLE * 2; t += 0.25f) {
            int ns = c.traffic.state(5, true, t), ew = c.traffic.state(5, false, t);
            if (ns != TrafficLights.RED && ew != TrafficLights.RED) conflict = true;
            seen |= 1 << ns;
        }
        check("signals never conflict", !conflict);
        check("signals cycle green/yellow/red", seen == 7);

        // streaming: chunks generated deterministically
        int sampleI = c.nbx / 4, sampleJ = c.nbz / 2;
        float sampleX = (c.rx1[sampleI] + c.rx0[sampleI + 1]) * 0.5f;
        float sampleZ = (c.rz1[sampleJ] + c.rz0[sampleJ + 1]) * 0.5f;
        int sampleCx = ChunkManager.chunkOf(sampleX), sampleCz = ChunkManager.chunkOf(sampleZ);
        Chunk generated = c.generate(sampleCx, sampleCz);
        int polys1 = generated.polyCount, polys2 = c.generate(sampleCx, sampleCz).polyCount;
        check("chunk generation deterministic", polys1 == polys2 && polys1 > 0);
        check("chunk surfaces are emitted as polygon meshes", !generated.batches.isEmpty() && noRaycastSurfaces(generated));
        check("generated buildings contain culled polygons", containsOneSidedPolygons(generated));
        check("generated details retain double-sided polygons", containsDoubleSidedPolygons(generated));
        check("city windows and doors are integrated into wall polygons", cityFacadesUseOpenings(c, textures));
        // ---- traffic: cars keep moving, turn, and never run a red light
        com.harlequinxd.drivehard.core.sim.Traffic tr = new com.harlequinxd.drivehard.core.sim.Traffic(c, r -> r.nextInt(100));
        tr.spawnMin = 5f; tr.spawnMax = 400f; tr.despawn = 2000f; tr.target = 40;
        float cx = (c.cityX0 + c.cityX1) / 2, cz = (c.cityZ0 + c.cityZ1) / 2;
        int redRuns = 0, turns = 0; double speedSum = 0; long samples = 0;
        java.util.Map<Object, Boolean> wasTurning = new java.util.HashMap<>();
        java.util.Map<Object, float[]> prevPos = new java.util.HashMap<>();
        for (int i = 0; i < 60 * 120; i++) {
            float t = i / 60f;
            tr.update(dt, t, cx, cz);
            for (var car : tr.cars) {
                boolean turning = carTurning(car);
                if (turning && !wasTurning.getOrDefault(car, false)) turns++;
                wasTurning.put(car, turning);
                speedSum += car.speed; samples++;
                // entering an intersection on red?
                float[] pp = prevPos.get(car);
                if (pp != null && !turning) redRuns += enteredOnRed(c, car, pp, t);
                prevPos.put(car, new float[]{car.x, car.z});
            }
        }
        System.out.printf("traffic: %d cars, avg speed %.1f m/s, %d turns, %d red-light entries%n", tr.cars.size(), speedSum / samples, turns, redRuns);
        check("traffic flows", speedSum / samples > 3.0);
        check("cars turn at junctions", turns > 20);
        check("no car enters an intersection on red", redRuns == 0 && tr.redEntries == 0);

        // ---- island & road network
        com.harlequinxd.drivehard.core.road.RoadNetwork net = c.roads;
        boolean bridgeFound = false;
        for (com.harlequinxd.drivehard.core.road.RoadEdge edge : net.edges) {
            for (int i = 0; i < edge.bridge.length; i++) {
                if (!edge.bridge[i]) continue;
                float bx = (edge.x[i] + edge.x[i + 1]) * 0.5f, bz = (edge.z[i] + edge.z[i + 1]) * 0.5f;
                Chunk bridge = c.generate(ChunkManager.chunkOf(bx), ChunkManager.chunkOf(bz));
                bridgeFound = true;
                check("bridge decks, parapets and piers use polygon meshes",
                        !bridge.batches.isEmpty() && noRaycastSurfaces(bridge));
                check("bridge road texture is part of the deck mesh", containsLaneSurface(bridge, textures));
                check("bridge chunk contains culled polygons", containsOneSidedPolygons(bridge));
                break;
            }
            if (bridgeFound) break;
        }
        check("procedural world contains a bridge to verify", bridgeFound);
        System.out.printf("island %.0f km, roads: freeway %.0f km, highway %.0f km, country %.0f km, %d villages, %d farmsteads (net built in %.0f ms)%n",
                (c.terrain.islandX1 - c.terrain.islandX0) / 1000f, net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.FREEWAY),
                net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.HIGHWAY), net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.COUNTRY),
                net.villages.size(), c.countryside.farmCount, net.buildTime);
        check("island is 128 km across", Math.abs(c.terrain.islandX1 - c.terrain.islandX0 - 128000f) < 1f);
        check("the island reaches 25 km inland", c.terrain.natural(c.cityX0 - 25000f, (c.cityZ0 + c.cityZ1) / 2) > 1f);
        float freewayKm = net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.FREEWAY);
        long freewayCount = net.edges.stream()
                .filter(edge -> edge.cls == com.harlequinxd.drivehard.core.road.RoadClass.FREEWAY).count();
        check("freeways extend beyond the city", freewayCount >= 2 && freewayKm > 50f);
        int cityRoads = 0, outsideCityRoads = 0;
        float cityRoadX0 = Float.MAX_VALUE, cityRoadX1 = -Float.MAX_VALUE;
        float cityRoadZ0 = Float.MAX_VALUE, cityRoadZ1 = -Float.MAX_VALUE;
        for (var edge : net.edges) {
            if (edge.cls != com.harlequinxd.drivehard.core.road.RoadClass.STREET
                    && edge.cls != com.harlequinxd.drivehard.core.road.RoadClass.AVENUE) continue;
            float mx = (edge.x[0] + edge.x[edge.count() - 1]) * 0.5f;
            float mz = (edge.z[0] + edge.z[edge.count() - 1]) * 0.5f;
            cityRoads++;
            if (c.terrain.cityDistance(mx, mz) > 0f) outsideCityRoads++;
            cityRoadX0 = Math.min(cityRoadX0, mx); cityRoadX1 = Math.max(cityRoadX1, mx);
            cityRoadZ0 = Math.min(cityRoadZ0, mz); cityRoadZ1 = Math.max(cityRoadZ1, mz);
        }
        System.out.printf("city: %d street segments, %d suburbs%n", cityRoads, c.suburbs.size());
        check("the city is a compact core with suburbs spread over its 15 km footprint",
                cityRoads > 1500 && c.suburbs.size() >= 10
                        && cityRoadX1 - cityRoadX0 > 9000f && cityRoadZ1 - cityRoadZ0 > 9000f);
        boolean named = true;
        for (var sb : c.suburbs) named &= sb.name != null && !sb.name.isEmpty();
        check("every suburb has a name", named);
        var cityMap = new com.harlequinxd.drivehard.core.map.MapData(c);
        int mapCentre = mapPixel(cityMap, (c.cityX0 + c.cityX1) * 0.5f, (c.cityZ0 + c.cityZ1) * 0.5f);
        int mapCorner = mapPixel(cityMap, c.cityX0 + 100f, c.cityZ0 + 100f);
        check("island map shows the built-up city and not its empty corners",
                cityMap.urbanAt(c.spawnX, c.spawnZ) == 1 && cityMap.urbanAt(c.cityX0 + 100f, c.cityZ0 + 100f) == 0
                        && cityMap.relief[mapPixel(cityMap, c.spawnX, c.spawnZ)] != cityMap.relief[mapCorner]);
        check("countryside has villages and farms", net.villages.size() > 60 && c.countryside.farmCount > 800);
        {
            int gravel = 0, dirt = 0, longTracks = 0;
            for (var e : net.edges) {
                if (e.cls != com.harlequinxd.drivehard.core.road.RoadClass.LANE) continue;
                if (e.surface == com.harlequinxd.drivehard.core.road.RoadEdge.GRAVEL) gravel++;
                if (e.surface == com.harlequinxd.drivehard.core.road.RoadEdge.DIRT) dirt++;
            }
            // whole tracks: walk from each junction on a main road along LANE edges
            for (var e : net.edges) {
                if (e.cls != com.harlequinxd.drivehard.core.road.RoadClass.LANE || e.surface == 0) continue;
                var na = net.nodes.get(e.a);
                boolean fromMain = false;
                for (int id : na.edges) { var o = net.edges.get(id); fromMain |= o.cls == com.harlequinxd.drivehard.core.road.RoadClass.COUNTRY || o.cls == com.harlequinxd.drivehard.core.road.RoadClass.HIGHWAY; }
                if (!fromMain) continue;
                float run = 0f;
                var cur = e;
                int guard = 0;
                while (cur != null && guard++ < 20) {
                    run += cur.len;
                    var nb = net.nodes.get(cur.b);
                    com.harlequinxd.drivehard.core.road.RoadEdge next = null;
                    for (int id : nb.edges) { var o = net.edges.get(id); if (o != cur && o.a == nb.id && o.surface == cur.surface && o.len > 40f && o.cls == cur.cls) next = o; }
                    cur = next;
                }
                if (run > 250f) longTracks++;
            }
            System.out.printf("farm roads: %d tracks, %d homes, lanes gravel %d dirt %d, %d tracks over 250 m%n",
                    net.trackCount, net.homes.size(), gravel, dirt, longTracks);
            check("gravel and dirt farm tracks with properties along them", net.trackCount > 100 && net.homes.size() > 100
                    && gravel > 200 && dirt > 100 && longTracks > 50);
        }
        {
            boolean[] reach = new boolean[net.nodes.size()];
            java.util.ArrayDeque<Integer> bfs = new java.util.ArrayDeque<>();
            int cityCentre = net.cityNode[c.nbx / 2][c.nbz / 2];
            bfs.add(cityCentre); reach[cityCentre] = true;
            while (!bfs.isEmpty()) {
                int id = bfs.poll();
                for (int e : net.nodes.get(id).edges) {
                    int o = net.edges.get(e).other(id);
                    if (!reach[o]) { reach[o] = true; bfs.add(o); }
                }
            }
            int lost = 0;
            for (var v : net.villages) if (!reach[v.id]) lost++;
            check("every village is reachable by road from the city", lost == 0);
        }
            check("bridge end caps are omitted while longitudinal sides remain",
                    com.harlequinxd.drivehard.core.city.SectorBuilderRegression.bridgeEndCapsOmitted(c.terrain));
        // roads sit on their profile: terrain under a country road equals the road height
        {
            int bad = 0, n = 0;
            for (var e : net.edges) {
                if (e.cls != com.harlequinxd.drivehard.core.road.RoadClass.COUNTRY) continue;
                for (int i = 0; i < e.count() - 1; i += 37) {
                    if (e.bridge[i]) continue;
                    float mx = (e.x[i] + e.x[i + 1]) * 0.5f, mz = (e.z[i] + e.z[i + 1]) * 0.5f;
                    float yr = (e.y[i] + e.y[i + 1]) * 0.5f;
                    if (Math.abs(c.terrain.height(mx, mz) - yr) > 0.5f) bad++;
                    n++;
                }
            }
            System.out.printf("country road samples off the ground: %d of %d%n", bad, n);
            check("terrain is cut and filled under roads", bad < n / 50 + 1);
        }

        // ---- rural traffic: freeway interchange and village junctions keep flowing
        for (String where : new String[]{"interchange", "village"}) {
            float wx = 0, wz = 0;
            if (where.equals("interchange")) {
                for (var nd : net.nodes) if (nd.kind == com.harlequinxd.drivehard.core.road.RoadNode.Kind.SPLIT) { wx = nd.x; wz = nd.z; break; }
            } else { wx = net.villages.get(30).x; wz = net.villages.get(30).z; }
            // watch from a spot off the road (a pedestrian on the road would hold traffic up)
            for (int k = 0; k < 200 && net.roadClearance(wx, wz, 30f) < 12f; k++) { wx += 7f; wz += 5f; }
            com.harlequinxd.drivehard.core.sim.Traffic rt = new com.harlequinxd.drivehard.core.sim.Traffic(c, r -> r.nextInt(100));
            rt.spawnMin = 5f; rt.spawnMax = 700f; rt.despawn = 3000f; rt.target = 30;
            java.util.Map<Object, Float> still = new java.util.HashMap<>();
            float worstStill = 0; double sp = 0; long ns2 = 0; int offRoad = 0;
            for (int i = 0; i < 60 * 150; i++) {
                rt.update(dt, i / 60f, wx, wz);
                for (var car : rt.cars) {
                    float st = car.speed < 0.2f ? still.getOrDefault(car, 0f) + dt : 0f;
                    still.put(car, st);
                    worstStill = Math.max(worstStill, st);
                    sp += car.speed; ns2++;
                    if (!carTurning(car) && Math.abs(car.y - c.surface(car.x, car.z, car.y + 0.5f)) > 1.0f) offRoad++;
                }
            }
            System.out.printf("%s traffic: %d cars, avg %.1f m/s, %d junction movements, longest wait %.0f s, off-road samples %d%n",
                    where, rt.cars.size(), sp / Math.max(1, ns2), rt.turnsTotal, worstStill, offRoad);
            check(where + " traffic flows", sp / Math.max(1, ns2) > 5.0 && rt.turnsTotal > 10);
            check(where + " traffic never gridlocks", worstStill < 40f);
            check(where + " cars drive on the road surface", offRoad < ns2 / 200 + 1);
        }

        // ---- pedestrians: only start crossing on the walk phase
        com.harlequinxd.drivehard.core.sim.Pedestrians pd = new com.harlequinxd.drivehard.core.sim.Pedestrians(c, r -> r.nextInt(100));
        pd.spawnMin = 0f; pd.spawnMax = 500f; pd.target = 60;
        int crossings = 0, badCrossings = 0;
        java.util.Map<Object, Integer> prevState = new java.util.HashMap<>();
        for (int i = 0; i < 60 * 90; i++) {
            float t = i / 60f;
            pd.update(dt, t, cx, cz);
            for (var p2 : pd.peds) {
                int st = p2.state;
                Integer ps = prevState.get(p2);
                if (ps != null && ps == com.harlequinxd.drivehard.core.sim.Pedestrians.WAIT && st == com.harlequinxd.drivehard.core.sim.Pedestrians.CROSS) {
                    crossings++;
                }
                prevState.put(p2, st);
                if (Math.abs(p2.y - c.surface(p2.x, p2.z)) > 0.01f) badCrossings++;
            }
        }
        System.out.printf("pedestrians: %d, %d crossings%n", pd.peds.size(), crossings);
        check("pedestrians cross streets", crossings > 5);
        check("pedestrians stay on the ground", badCrossings == 0);

        for (com.harlequinxd.drivehard.core.road.Town.Size size : new com.harlequinxd.drivehard.core.road.Town.Size[]{
                com.harlequinxd.drivehard.core.road.Town.Size.TOWN, com.harlequinxd.drivehard.core.road.Town.Size.VILLAGE}) {
            com.harlequinxd.drivehard.core.road.Town settlement = null;
            for (var town : c.roads.towns)
                if (town.size == size && town.grid != null) { settlement = town; break; }
            boolean seenInSettlement = false;
            boolean walkedFootpath = false;
            if (settlement != null) {
                com.harlequinxd.drivehard.core.sim.Pedestrians locals =
                        new com.harlequinxd.drivehard.core.sim.Pedestrians(c, r -> r.nextInt(100));
                locals.spawnMax = 125f; locals.target = 40;
                float walkInner=com.harlequinxd.drivehard.core.road.RoadClass.TOWN.laneW;
                float walkOuter=com.harlequinxd.drivehard.core.road.RoadClass.TOWN.halfWidth();
                for (int i = 0; i < 60 * 10; i++) {
                    locals.update(dt, i * dt, settlement.centre.x, settlement.centre.z);
                    for (var pedestrian : locals.peds) {
                        boolean inSettlement = c.roads.townAt(pedestrian.x, pedestrian.z, 0f) == settlement;
                        if (!inSettlement) continue;
                        seenInSettlement = true;
                        if (pedestrian.state == com.harlequinxd.drivehard.core.sim.Pedestrians.CROSS) continue;
                        float lineDistance = Float.MAX_VALUE;
                        for (float x : settlement.gx) lineDistance = Math.min(lineDistance, Math.abs(pedestrian.x - x));
                        for (float z : settlement.gz) lineDistance = Math.min(lineDistance, Math.abs(pedestrian.z - z));
                        walkedFootpath |= lineDistance >= walkInner && lineDistance <= walkOuter;
                    }
                }
            }
            check("pedestrians appear in " + size.name().toLowerCase(), seenInSettlement);
            check("pedestrians use " + size.name().toLowerCase() + " footpaths", walkedFootpath);
        }

        float largestTown = 0f, largestVillage = 0f;
        for (var town : c.roads.towns) {
            if (town.grid == null) continue;
            float span = Math.max(town.x1 - town.x0, town.z1 - town.z0);
            if (town.size == com.harlequinxd.drivehard.core.road.Town.Size.TOWN) largestTown = Math.max(largestTown, span);
            if (town.size == com.harlequinxd.drivehard.core.road.Town.Size.VILLAGE) largestVillage = Math.max(largestVillage, span);
        }
        System.out.printf("largest built-up town %.1f km, village %.1f km%n", largestTown / 1000f, largestVillage / 1000f);
        check("largest town spans about 7 km", largestTown >= 6800f);
        check("largest village spans about 3 km", largestVillage >= 2800f);

        worldCacheTest();
        // the rest runs on the island read back from the game data folder
        vehicleTest();
        fastTravelTest();

        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    /** The game data folder: a second launch reads the island and chunks back, identical to generating them. */
    static void worldCacheTest() {
        java.io.File dir;
        try { dir = java.nio.file.Files.createTempDirectory("drivehard-gamedata").toFile(); }
        catch (java.io.IOException e) { check("temp game data folder", false); return; }
        dir.deleteOnExit();
        com.harlequinxd.drivehard.core.data.WorldCache.root = dir;
        com.harlequinxd.drivehard.core.data.WorldCache.build = "simtest";
        long t0 = System.nanoTime();
        com.harlequinxd.drivehard.core.Game g1 = new com.harlequinxd.drivehard.core.Game(com.harlequinxd.drivehard.core.Game.DEFAULT_SEED, null);
        long t1 = System.nanoTime();
        check("first launch generates the island", !g1.loadedFromDisk && g1.worldCache != null);
        g1.worldCache.flush();
        int written = g1.worldCache.chunkWrites;
        g1.dispose();
        long t2 = System.nanoTime();
        com.harlequinxd.drivehard.core.Game g2 = new com.harlequinxd.drivehard.core.Game(com.harlequinxd.drivehard.core.Game.DEFAULT_SEED, null);
        long t3 = System.nanoTime();
        System.out.printf("first launch %.1f s, second launch %.1f s (%d chunks saved, %d read back)%n",
                (t1 - t0) / 1e9, (t3 - t2) / 1e9, written, g2.worldCache.chunkReads);
        check("second launch reads the island from game data", g2.loadedFromDisk);
        check("second launch reads the chunks from game data", g2.worldCache.chunkReads > 0 && g2.worldCache.chunkReads >= written / 2);
        int cx = com.harlequinxd.drivehard.core.city.ChunkManager.chunkOf(g2.player.x);
        int cz = com.harlequinxd.drivehard.core.city.ChunkManager.chunkOf(g2.player.z);
        int same = 0, total = 0;
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                com.harlequinxd.drivehard.core.city.Chunk d = g2.worldCache.readChunk(cx + dx, cz + dz);
                if (d == null) continue;
                total++;
                if (chunkHash(d) == chunkHash(g2.city.generate(cx + dx, cz + dz))) same++;
            }
        check("saved chunks match freshly generated ones (" + same + "/" + total + ")", total > 0 && same == total);
        g2.dispose();
    }

    static long chunkHash(com.harlequinxd.drivehard.core.city.Chunk c) {
        long h = 17;
        for (com.harlequinxd.drivehard.core.render.Geo g : c.batches) {
            h = h * 31 + g.count;
            for (int i = 0; i < g.count * 12; i++) h = h * 31 + Float.floatToIntBits(g.pos[i]);
            for (int i = 0; i < g.count * 8; i++) h = h * 31 + Float.floatToIntBits(g.uv[i]);
            for (int i = 0; i < g.count; i++) h = h * 31 + g.tex[i] * 7 + g.meta[i];
        }
        h = h * 31 + c.boxes.size() * 7 + c.spriteCount;
        for (int i = 0; i < c.spriteCount * com.harlequinxd.drivehard.core.city.Chunk.SPRITE_STRIDE; i++) h = h * 31 + Float.floatToIntBits(c.sprites[i]);
        if (c.ground != null) {
            for (short[] m : c.ground.mips) for (short v : m) h = h * 31 + v;
            for (float v : c.ground.h) h = h * 31 + Float.floatToIntBits(v);
            if (c.ground.water != null) for (byte v : c.ground.water) h = h * 31 + v;
        }
        return h;
    }

    /** Get into a traffic car, drive it, get out, and take it again. */
    /** Fast travel lands on a street at the destination, with the streets around it built. */
    static void fastTravelTest() {
        com.harlequinxd.drivehard.core.Game g = new com.harlequinxd.drivehard.core.Game(com.harlequinxd.drivehard.core.Game.DEFAULT_SEED, null);
        int ok = 0, tried = 0;
        float[] last = {0f};
        for (var l : g.map.labels) {
            if (l.rank > 2 || tried >= 6) continue;
            tried++;
            last[0] = 0f;
            g.fastTravel(l, (what, done) -> last[0] = done);
            float dx = g.player.x - l.x, dz = g.player.z - l.z;
            float clearance = g.city.roads.roadClearance(g.player.x, g.player.z, 50f);
            boolean nearRoad = clearance < 6f || g.city.urban(g.player.x, g.player.z);
            boolean built = g.city.chunks.get(com.harlequinxd.drivehard.core.city.ChunkManager.chunkOf(g.player.x),
                    com.harlequinxd.drivehard.core.city.ChunkManager.chunkOf(g.player.z)) != null;
            float ground = g.city.surface(g.player.x, g.player.z);
            if (Math.hypot(dx, dz) < 1000f && nearRoad && built && Math.abs(g.player.y - ground) < 1f && last[0] >= 1f) ok++;
            else System.out.printf("  travel to %s: off by %.0f m, clearance %.1f, built %b, y %.1f vs %.1f, progress %.2f%n",
                    l.name, Math.hypot(dx, dz), clearance, built, g.player.y, ground, last[0]);
            for (int i = 0; i < 30; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
        }
        check("fast travel lands on a built street at the destination (" + ok + "/" + tried + ")", ok == tried && tried > 0);
        g.dispose();
    }

    static void vehicleTest() {
        com.harlequinxd.drivehard.core.Game g = new com.harlequinxd.drivehard.core.Game(com.harlequinxd.drivehard.core.Game.DEFAULT_SEED, null);
        for (int i = 0; i < 8; i++) g.requestCar();
        g.cache.bakeAllNow();
        float dt = 1 / 60f;
        for (int i = 0; i < 600; i++) g.update(dt, 0, 0, 0, 0, false);
        g.cache.bakeAllNow();
        for (int i = 0; i < 300 && g.traffic.cars.isEmpty(); i++) { g.update(dt, 0, 0, 0, 0, false); g.cache.bakeAllNow(); }
        com.harlequinxd.drivehard.core.sim.Traffic.Car car = null;
        for (com.harlequinxd.drivehard.core.sim.Traffic.Car c : g.traffic.cars)
            if (g.carReady(c.style)) { car = c; break; }
        check("traffic available to steal", car != null);
        if (car == null) return;
        // stand beside it (the button only shows within reach)
        float rx = -(float) Math.cos(car.yaw), rz = (float) Math.sin(car.yaw);
        g.player.x = car.x + rx * 2.2f; g.player.z = car.z + rz * 2.2f;
        g.player.y = g.city.surface(g.player.x, g.player.z);
        g.traffic.target = 0;
        float far = 0;
        check("get-in button shows next to a car", g.canEnter());
        g.update(dt, 0, 0, 0, 0, false, 0, 0, 0, true);
        for (int i = 0; i < 40; i++) g.update(dt, 0, 0, 0, 0, false, 0, 0, 0, false);
        check("player is driving", g.mode == com.harlequinxd.drivehard.core.Game.Mode.DRIVING && g.vehicle != null);
        check("stolen car left traffic", !g.traffic.cars.contains(car));
        g.traffic.cars.clear();          // an open road ahead (downtown traffic queues at the lights)
        float sx = g.vehicle.x, sz = g.vehicle.z, maxSpeed = 0;
        for (int i = 0; i < 180; i++) { g.update(dt, 0, 0, 0, 0, false, 0, 1, 0, false); maxSpeed = Math.max(maxSpeed, g.vehicle.speed); }
        far = (float) Math.hypot(g.vehicle.x - sx, g.vehicle.z - sz);
        System.out.printf("drove %.1f m, top speed %.1f m/s%n", far, maxSpeed);
        check("gas moves the car", far > 5f);
        float yaw0 = g.vehicle.yaw;
        for (int i = 0; i < 40; i++) g.update(dt, 0, 0, 0, 0, false, 1, 0.5f, 0, false);
        float yawChange = Math.abs(g.vehicle.yaw - yaw0);
        System.out.printf("steering changed yaw by %.3f rad at %.1f m/s%n", yawChange, g.vehicle.speed);
        check("steering turns the car", yawChange > 0.15f);
        float minSpeed = 99f;
        for (int i = 0; i < 300; i++) { g.update(dt, 0, 0, 0, 0, false, 0, 0, 1, false); minSpeed = Math.min(minSpeed, g.vehicle.speed); }
        check("reverse brakes then backs up", minSpeed < -0.5f);
        for (int i = 0; i < 400 && Math.abs(g.vehicle.speed) > 0.01f; i++) g.update(dt, 0, 0, 0, 0, false, 0, 0, 0, false);
        check("car coasts to a stop", Math.abs(g.vehicle.speed) <= 0.01f);
        g.update(dt, 0, 0, 0, 0, false, 0, 0, 0, true);
        for (int i = 0; i < 60; i++) g.update(dt, 0, 0, 0, 0, false);
        check("popped out on foot", g.mode == com.harlequinxd.drivehard.core.Game.Mode.ON_FOOT && g.vehicle == null);
        check("car stays parked", g.parked.size() == 1);
        com.harlequinxd.drivehard.core.game.Vehicle v = g.parked.get(0);
        float gap = v.distanceTo(g.player.x, g.player.z);
        System.out.printf("stepped out %.2f m from the body%n", gap);
        check("standing beside the car", gap > 0.2f && gap < 1.5f && g.player.onGround);
        check("can get back in", g.canEnter());
        g.update(dt, 0, 0, 0, 0, false, 0, 0, 0, true);
        for (int i = 0; i < 40; i++) g.update(dt, 0, 0, 0, 0, false);
        check("back in the parked car", g.vehicle == v && g.parked.isEmpty());
        g.dispose();
    }

    static boolean carTurning(com.harlequinxd.drivehard.core.sim.Traffic.Car car) {
        try {
            java.lang.reflect.Field f = car.getClass().getDeclaredField("turning");
            f.setAccessible(true);
            return f.getBoolean(car);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** 1 if the car's front crossed an intersection edge while its signal was red. */
    static int enteredOnRed(City c, com.harlequinxd.drivehard.core.sim.Traffic.Car car, float[] prev, float t) {
        float s = Math.abs((float) Math.cos(car.yaw)) > 0.7f ? car.z : car.x;
        float ps = Math.abs((float) Math.cos(car.yaw)) > 0.7f ? prev[1] : prev[0];
        boolean ns = Math.abs((float) Math.cos(car.yaw)) > 0.7f;
        float dir = Math.signum(s - ps);
        if (dir == 0) return 0;
        float half = com.harlequinxd.drivehard.core.model.Cars.spec(car.style).L / 2;
        float front = s + dir * half, pfront = ps + dir * half;
        int n = ns ? c.nbz : c.nbx;
        for (int k = 0; k <= n; k++) {
            float edge = dir > 0 ? (ns ? c.rz0[k] : c.rx0[k]) : (ns ? c.rz1[k] : c.rx1[k]);
            if ((pfront - edge) * dir < 0 && (front - edge) * dir >= 0) {
                // which road is the car on?
                float lat = ns ? car.x : car.z;
                int road = -1;
                for (int i = 0; i <= (ns ? c.nbx : c.nbz); i++) {
                    float a = ns ? c.rx0[i] : c.rz0[i], b = ns ? c.rx1[i] : c.rz1[i];
                    if (lat >= a && lat <= b) road = i;
                }
                if (road < 0) return 0;
                int id = ns ? c.traffic.id(road, k) : c.traffic.id(k, road);
                if (c.traffic.state(id, ns, t) == com.harlequinxd.drivehard.core.city.TrafficLights.RED
                        && c.traffic.timeToChange(id, ns, t) < com.harlequinxd.drivehard.core.city.TrafficLights.CYCLE * 0.5f - 2.5f) {
                    System.out.printf("  red entry: t=%.2f car style %d at %.1f,%.1f speed %.1f yaw %.2f road %d junction %d ttc %.1f prev %.1f,%.1f%n",
                            t, car.style, car.x, car.z, car.speed, car.yaw, road, k, c.traffic.timeToChange(id, ns, t), prev[0], prev[1]);
                    return 1;
                }
            }
        }
        return 0;
    }

    static void check(String n, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + n);
        if (!ok) fails++;
    }

    static boolean noRaycastSurfaces(Chunk chunk) {
        return chunk.sectors.sectors.isEmpty() && chunk.sectors.walls.isEmpty() && chunk.sectors.slabs.isEmpty();
    }

    static boolean containsOneSidedPolygons(Chunk chunk) {
        for (var mesh : chunk.batches)
            for (int i = 0; i < mesh.count; i++)
                if (mesh.nrm[i * 3] != 0f || mesh.nrm[i * 3 + 1] != 0f || mesh.nrm[i * 3 + 2] != 0f) return true;
        return false;
    }

    static boolean containsDoubleSidedPolygons(Chunk chunk) {
        for (var mesh : chunk.batches)
            for (int i = 0; i < mesh.count; i++)
                if (mesh.nrm[i * 3] == 0f && mesh.nrm[i * 3 + 1] == 0f && mesh.nrm[i * 3 + 2] == 0f) return true;
        return false;
    }

    static boolean containsLaneSurface(Chunk chunk, TextureLibrary textures) {
        int[] laneTextures = {textures.laneCountry, textures.laneHwInner, textures.laneHwOuter,
                textures.laneHwMedian, textures.laneFwInner, textures.laneFwOuter, textures.laneRamp, textures.laneTown};
        for (var mesh : chunk.batches)
            for (int i = 0; i < mesh.count; i++)
                for (int texture : laneTextures)
                    if (mesh.tex[i] == texture) return true;
        return false;
    }

    static boolean cityFacadesUseOpenings(City city, TextureLibrary textures) {
        boolean windows = false, doors = false;
        int stride = Math.max(1, Math.max(city.nbx, city.nbz) / 20);
        for (int i = 0; i < city.nbx && !(windows && doors); i += stride)
            for (int j = 0; j < city.nbz && !(windows && doors); j += stride) {
                float x = (city.rx1[i] + city.rx0[i + 1]) * 0.5f;
                float z = (city.rz1[j] + city.rz0[j + 1]) * 0.5f;
                Chunk chunk = city.generate(ChunkManager.chunkOf(x), ChunkManager.chunkOf(z));
                for (var batch : chunk.batches)
                    for (int k = 0; k < batch.count; k++) {
                        for (int tex : textures.houseWin) if (batch.tex[k] == tex) windows = true;
                        for (int tex : textures.houseDoor) if (batch.tex[k] == tex) doors = true;
                    }
            }
        return windows && doors;
    }

    private static int mapPixel(com.harlequinxd.drivehard.core.map.MapData map, float x, float z) {
        int i = Math.max(0, Math.min(map.SIZE - 1, (int) ((x - map.x0) / (map.extent / map.SIZE))));
        int j = Math.max(0, Math.min(map.SIZE - 1, (int) ((z - map.z0) / (map.extent / map.SIZE))));
        return j * map.SIZE + i;
    }

    static boolean orderingTableSortsPolygons() {
        var renderer = new com.harlequinxd.drivehard.core.render.SoftRenderer(64, 64);
        renderer.textures = new com.harlequinxd.drivehard.core.render.Texture[]{
                new com.harlequinxd.drivehard.core.render.Texture(1, 1, new int[]{0xffff0000}),
                new com.harlequinxd.drivehard.core.render.Texture(1, 1, new int[]{0xff0000ff})
        };
        renderer.lookAt(0f, 0f, 0f, 0f, 0f, 1f);
        renderer.begin();
        renderer.drawSky();
        float[] uv = {0, 0, 1, 0, 1, 1, 0, 1};
        float[] light = {1, 1, 1, 1};
        renderer.submit(new float[]{-1.2f, -0.9f, 3f, 1.2f, -0.9f, 3f, 1.2f, 0.9f, 3f, -1.2f, 0.9f, 3f},
                uv, light, 4, 0, com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT,
                com.harlequinxd.drivehard.core.render.SoftRenderer.MODE_OPAQUE, 0f, false);
        renderer.submit(new float[]{-2.4f, -1.8f, 6f, 2.4f, -1.8f, 6f, 2.4f, 1.8f, 6f, -2.4f, 1.8f, 6f},
                uv, light, 4, 1, com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT,
                com.harlequinxd.drivehard.core.render.SoftRenderer.MODE_OPAQUE, 0f, false);
        renderer.flush();
        int center = renderer.fb[32 * renderer.W + 33];
        return ((center >> 16) & 255) > (center & 255);
    }
}
