package com.harlequinxd.drivehard.core.sim;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.Vehicle;
import com.harlequinxd.drivehard.core.model.Cars;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ambient traffic on the street grid. Cars drive on the right in a lane, keep their distance to
 * the car ahead, stop for red (and late yellow) signals at the stop line, brake for the player,
 * and turn left/right/straight at intersections along smooth curves. Cars are spawned around
 * the player out of view and removed when far away.
 */
public final class Traffic {
    public static final class Car {
        public int style;
        public float x, z, y, yaw, speed;
        float cruise, len, wid;
        // lane state
        boolean ns;          // travelling along Z (north/south)
        int dir;             // +1 / -1 along the axis
        int road;            // N-S road index i (ns) or E-W road index j
        float lane;          // lateral offset from the road centre line (to the right of travel)
        // turning along a quadratic curve
        boolean turning;
        float t, ax, az, cx, cz, bx, bz, arc;
        boolean nextNs; int nextDir, nextRoad; float nextLane;
        int lastJunction = -1;
        public float brake;  // 0..1 for brake lights / debug
    }

    public final List<Car> cars = new ArrayList<>();
    /** Player-driven and parked cars that traffic must queue behind. */
    public List<Vehicle> obstacles;
    public int target = 26;
    public float spawnMin = 55f, spawnMax = 150f, despawn = 185f;
    private final City city;
    private final Random rnd = new Random(42);
    private final StyleSource styles;

    /** Supplies car styles that are ready to draw (baked), or -1 if none yet. */
    public interface StyleSource { int pickReadyStyle(Random r); }

    public Traffic(City city, StyleSource styles) {
        this.city = city;
        this.styles = styles;
    }

    // ------------------------------------------------------------------ geometry helpers

    private float roadCentre(boolean ns, int road) {
        return ns ? (city.rx0[road] + city.rx1[road]) * 0.5f : (city.rz0[road] + city.rz1[road]) * 0.5f;
    }

    private boolean avenue(boolean ns, int road) { return ns ? city.avenueX[road] : city.avenueZ[road]; }

    private float laneFor(boolean ns, int road) {
        if (avenue(ns, road)) return rnd.nextBoolean() ? 3.1f : 6.6f;
        return 2.0f;
    }

    /** World position of a lane point: axis coordinate s on the road, lateral lane offset. */
    private void lanePoint(boolean ns, int dir, int road, float lane, float s, float[] out) {
        float c = roadCentre(ns, road);
        // keep left (Japan): for +Z travel, left is +X; for +X travel, left is -Z
        if (ns) { out[0] = c + dir * lane; out[1] = s; }
        else { out[0] = s; out[1] = c - dir * lane; }
    }

    private int crossCount(boolean ns) { return ns ? city.nbz : city.nbx; }

    private float crossLo(boolean ns, int k) { return ns ? city.rz0[k] : city.rx0[k]; }
    private float crossHi(boolean ns, int k) { return ns ? city.rz1[k] : city.rx1[k]; }

    // ------------------------------------------------------------------ update

    private final float[] tmp = new float[2];

    public void update(float dt, float time, float px, float pz) {
        // despawn far cars
        for (int i = cars.size() - 1; i >= 0; i--) {
            Car c = cars.get(i);
            float dx = c.x - px, dz = c.z - pz;
            if (dx * dx + dz * dz > despawn * despawn) cars.remove(i);
        }
        // spawn
        for (int tries = 0; tries < 4 && cars.size() < target; tries++) spawn(px, pz);
        for (Car c : cars) drive(c, dt, time, px, pz);
    }

    private void spawn(float px, float pz) {
        int style = styles.pickReadyStyle(rnd);
        if (style < 0) return;
        boolean ns = rnd.nextBoolean();
        int road = rnd.nextInt((ns ? city.nbx : city.nbz) + 1);
        int dir = rnd.nextBoolean() ? 1 : -1;
        float lane = laneFor(ns, road);
        float lo = ns ? city.cityZ0 : city.cityX0, hi = ns ? city.cityZ1 : city.cityX1;
        float s = lo + 10f + rnd.nextFloat() * (hi - lo - 20f);
        // not inside an intersection
        for (int k = 0; k <= crossCount(ns); k++) if (s > crossLo(ns, k) - 18f && s < crossHi(ns, k) + 18f) return;
        lanePoint(ns, dir, road, lane, s, tmp);
        float dx = tmp[0] - px, dz = tmp[1] - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < spawnMin || d > spawnMax) return;
        for (Car o : cars) {
            float ox = o.x - tmp[0], oz = o.z - tmp[1];
            if (ox * ox + oz * oz < 14f * 14f) return;
        }
        Car c = new Car();
        c.style = style;
        Cars.Spec sp = Cars.spec(style);
        c.len = sp.L;
        c.wid = sp.W;
        c.ns = ns; c.dir = dir; c.road = road; c.lane = lane;
        c.x = tmp[0]; c.z = tmp[1];
        c.cruise = 10f + rnd.nextFloat() * 4f;
        c.speed = c.cruise * 0.7f;
        c.yaw = ns ? (dir > 0 ? 0f : (float) Math.PI) : (dir > 0 ? (float) (Math.PI / 2) : (float) (-Math.PI / 2));
        cars.add(c);
    }

    private void drive(Car c, float dt, float time, float px, float pz) {
        float want = c.cruise;
        float fx = (float) Math.sin(c.yaw), fz = (float) Math.cos(c.yaw);

        // car ahead (anything in a narrow cone in front)
        float nearest = Float.MAX_VALUE;
        for (Car o : cars) {
            if (o == c) continue;
            float dx = o.x - c.x, dz = o.z - c.z;
            float ahead = dx * fx + dz * fz, side = Math.abs(dx * fz - dz * fx);
            if (ahead > 0 && ahead < 30f && side < 1.8f) nearest = Math.min(nearest, ahead - (c.len + o.len) * 0.5f);
        }
        if (obstacles != null) for (Vehicle o : obstacles) {
            float dx = o.x - c.x, dz = o.z - c.z;
            float ahead = dx * fx + dz * fz, side = Math.abs(dx * fz - dz * fx);
            if (ahead > 0 && ahead < 30f && side < 2.2f) nearest = Math.min(nearest, ahead - (c.len + o.len) * 0.5f);
        }
        // the player in front
        {
            float dx = px - c.x, dz = pz - c.z;
            float ahead = dx * fx + dz * fz, side = Math.abs(dx * fz - dz * fx);
            if (ahead > 0 && ahead < 25f && side < 1.6f) nearest = Math.min(nearest, ahead - c.len * 0.5f - 0.5f);
        }
        if (nearest < Float.MAX_VALUE) want = Math.min(want, Math.max(0f, (nearest - 2.5f) / 1.1f));

        if (!c.turning) {
            // next junction ahead and its signal
            float s = c.ns ? c.z : c.x;
            int n = crossCount(c.ns);
            int junction = -1;
            float edge = 0;
            for (int k = 0; k <= n; k++) {
                int kk = c.dir > 0 ? k : n - k;
                float e = c.dir > 0 ? crossLo(c.ns, kk) : crossHi(c.ns, kk);
                if ((e - s) * c.dir > c.len * 0.5f - 0.5f) { junction = kk; edge = e; break; }
            }
            if (junction >= 0) {
                float front = s + c.dir * c.len * 0.5f;
                float distToEdge = (edge - front) * c.dir;
                float stopDist = distToEdge - 5.0f;          // stop line ~4.5 m before the junction
                int id = c.ns ? city.traffic.id(c.road, junction) : city.traffic.id(junction, c.road);
                int st = city.traffic.state(id, c.ns, time);
                boolean mustStop = st == TrafficLights.RED || (st == TrafficLights.YELLOW && stopDist > c.speed * 1.2f);
                if (mustStop && stopDist > -0.5f) want = Math.min(want, Math.max(0f, stopDist) * 0.9f);
                    // crept past the line in a queue: hold at the junction edge instead of entering on red
                else if (st == TrafficLights.RED && distToEdge > 0.2f && c.speed < 4f) want = Math.min(want, Math.max(0f, distToEdge - 0.3f));
                // decide what to do when reaching the junction
                if (distToEdge < 0.6f && c.lastJunction != junction * 2 + (c.ns ? 1 : 0) && st != TrafficLights.RED) {
                    c.lastJunction = junction * 2 + (c.ns ? 1 : 0);
                    beginJunction(c, junction);
                }
            } else {
                // end of the grid: turn around by re-spawning direction
                c.dir = -c.dir;
                c.yaw += (float) Math.PI;
            }
        }

        // accelerate / brake
        float acc = want > c.speed ? 3.2f : 7.5f;
        c.brake = want < c.speed - 0.3f ? 1f : 0f;
        c.speed += Math.max(-acc * dt, Math.min(acc * dt, want - c.speed));
        if (c.speed < 0) c.speed = 0;
        float step = c.speed * dt;

        if (c.turning) {
            c.t += step / Math.max(1f, c.arc);
            if (c.t >= 1f) {
                c.turning = false;
                c.ns = c.nextNs; c.dir = c.nextDir; c.road = c.nextRoad; c.lane = c.nextLane;
                c.x = c.bx; c.z = c.bz;
                c.yaw = c.ns ? (c.dir > 0 ? 0f : (float) Math.PI) : (c.dir > 0 ? (float) (Math.PI / 2) : (float) (-Math.PI / 2));
            } else {
                float t = c.t, u = 1 - t;
                float nx = u * u * c.ax + 2 * u * t * c.cx + t * t * c.bx;
                float nz = u * u * c.az + 2 * u * t * c.cz + t * t * c.bz;
                float tx = 2 * u * (c.cx - c.ax) + 2 * t * (c.bx - c.cx), tz = 2 * u * (c.cz - c.az) + 2 * t * (c.bz - c.cz);
                c.yaw = (float) Math.atan2(tx, tz);
                c.x = nx; c.z = nz;
            }
        } else {
            if (c.ns) c.z += step * c.dir; else c.x += step * c.dir;
        }
        c.y = city.terrain.height(c.x, c.z);
    }

    /** Picks straight / left / right and sets up the curve through the junction. */
    private void beginJunction(Car c, int k) {
        // crossing road index and its geometry
        boolean crossNs = !c.ns;
        int maxCross = crossNs ? city.nbx : city.nbz;
        int n = crossCount(c.ns);
        boolean canStraight = c.dir > 0 ? k < n : k > 0;
        // turning right: travel +Z (south) -> right is west (-X); travel +X (east) -> right is south (+Z)
        int rightDir = c.ns ? -c.dir : c.dir;
        int leftDir = -rightDir;
        int crossRoad = k;
        boolean canRight = canGo(crossNs, crossRoad, rightDir, c);
        boolean canLeft = canGo(crossNs, crossRoad, leftDir, c);
        int roll = rnd.nextInt(10);
        int choice;   // 0 straight, 1 right, 2 left
        if (roll < 6 && canStraight) choice = 0;
        else if (roll < 8 && canRight) choice = 1;
        else if (canLeft) choice = 2;
        else if (canStraight) choice = 0;
        else if (canRight) choice = 1;
        else choice = 2;
        if (choice == 0) return;
        int newDir = choice == 1 ? rightDir : leftDir;
        float newLane = laneFor(crossNs, crossRoad);
        // entry point: current lane at the near junction edge; exit: new lane at the far edge of the crossing road
        float entryS = c.dir > 0 ? crossLo(c.ns, k) : crossHi(c.ns, k);
        lanePoint(c.ns, c.dir, c.road, c.lane, entryS, tmp);
        c.ax = tmp[0]; c.az = tmp[1];
        float exitS = newDir > 0 ? crossHi(crossNs, c.road) : crossLo(crossNs, c.road);
        lanePoint(crossNs, newDir, crossRoad, newLane, exitS, tmp);
        c.bx = tmp[0]; c.bz = tmp[1];
        // control point: where the two lane lines cross
        if (c.ns) { c.cx = c.ax; c.cz = c.bz; } else { c.cx = c.bx; c.cz = c.az; }
        c.arc = (float) (Math.hypot(c.cx - c.ax, c.cz - c.az) + Math.hypot(c.bx - c.cx, c.bz - c.cz)) * 0.85f;
        c.x = c.ax; c.z = c.az;
        c.t = 0;
        c.turning = true;
        c.nextNs = crossNs; c.nextDir = newDir; c.nextRoad = crossRoad; c.nextLane = newLane;
        c.lastJunction = -1;
    }

    /** True if the crossing road continues in that direction from the current road. */
    private boolean canGo(boolean crossNs, int crossRoad, int dir, Car c) {
        int idx = c.road;                                    // our road is a junction index on the crossing road
        int max = crossNs ? city.nbz : city.nbx;
        return dir > 0 ? idx < max : idx > 0;
    }

    /** Collision boxes for the cars near a point (axis-aligned bounds of each car). */
    public void boxesNear(float x, float z, List<Box> out) {
        for (Car c : cars) {
            float dx = c.x - x, dz = c.z - z;
            if (dx * dx + dz * dz > 100f) continue;
            float s = Math.abs((float) Math.sin(c.yaw)), co = Math.abs((float) Math.cos(c.yaw));
            float hx = (c.len * s + c.wid * co) * 0.5f, hz = (c.len * co + c.wid * s) * 0.5f;
            out.add(new Box(c.x - hx, c.y, c.z - hz, c.x + hx, c.y + 1.5f, c.z + hz));
        }
    }
}
