import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

/** Headless checks: controller, streaming collision, terrain, traffic signals. */
public class SimTest {
    static int fails = 0;

    public static void main(String[] a) {
        City c = new City(new TextureLibrary(20261103L), 20261103L);
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

        // walk east from the promenade toward the sea: must stop at the water line
        Player w = new Player(c.spawnX, c.spawnZ);
        c.chunks.ensureAround(w.x, w.z);
        w.y = c.surface(w.x, w.z);
        for (int i = 0; i < 60 * 30; i++) { c.chunks.ensureAround(w.x, w.z); w.update(dt, 1f, 0f, false, c); }
        System.out.printf("beach walk stopped at x=%.1f (city edge %.1f), ground %.2f%n", w.x, c.cityX1, c.terrain.height(w.x, w.z));
        check("stopped at the sea", c.terrain.height(w.x, w.z) > -0.6f && w.x > c.cityX1 + 30f);

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
        int polys1 = c.generate(10, 6).polyCount, polys2 = c.generate(10, 6).polyCount;
        check("chunk generation deterministic", polys1 == polys2 && polys1 > 0);
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
        System.out.printf("island %.0f km, roads: freeway %.0f km, highway %.0f km, country %.0f km, %d villages, %d farmsteads (net built in %.0f ms)%n",
                (c.terrain.islandX1 - c.terrain.islandX0) / 1000f, net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.FREEWAY),
                net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.HIGHWAY), net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.COUNTRY),
                net.villages.size(), c.countryside.farmCount, net.buildTime);
        check("island is 128 km across", Math.abs(c.terrain.islandX1 - c.terrain.islandX0 - 128000f) < 1f);
        check("the island reaches 25 km inland", c.terrain.natural(c.cityX0 - 25000f, (c.cityZ0 + c.cityZ1) / 2) > 1f);
        check("freeways leave the city", net.lengthKm(com.harlequinxd.drivehard.core.road.RoadClass.FREEWAY) > 150f);
        check("countryside has villages and farms", net.villages.size() > 60 && c.countryside.farmCount > 800);
        {
            boolean[] reach = new boolean[net.nodes.size()];
            java.util.ArrayDeque<Integer> bfs = new java.util.ArrayDeque<>();
            bfs.add(net.cityNode[0][0]); reach[net.cityNode[0][0]] = true;
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
                    if (!carTurning(car) && Math.abs(car.y - c.surface(car.x, car.z)) > 1.0f) offRoad++;
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

        vehicleTest();

        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    /** Get into a traffic car, drive it, get out, and take it again. */
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
        float sx = g.vehicle.x, sz = g.vehicle.z, maxSpeed = 0;
        for (int i = 0; i < 180; i++) { g.update(dt, 0, 0, 0, 0, false, 0, 1, 0, false); maxSpeed = Math.max(maxSpeed, g.vehicle.speed); }
        far = (float) Math.hypot(g.vehicle.x - sx, g.vehicle.z - sz);
        System.out.printf("drove %.1f m, top speed %.1f m/s%n", far, maxSpeed);
        check("gas moves the car", far > 5f);
        float yaw0 = g.vehicle.yaw;
        for (int i = 0; i < 40; i++) g.update(dt, 0, 0, 0, 0, false, 1, 0.5f, 0, false);
        check("steering turns the car", Math.abs(g.vehicle.yaw - yaw0) > 0.15f);
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
}
