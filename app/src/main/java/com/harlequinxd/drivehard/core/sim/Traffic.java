package com.harlequinxd.drivehard.core.sim;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.Vehicle;
import com.harlequinxd.drivehard.core.model.Cars;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.RoadNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ambient traffic on the spline road network. Cars keep left in a lane of their road, follow the
 * car ahead, slow for bends, and at every node pick a legal movement:
 *  - city intersections: obey the signals (stop at the line on red and late yellow);
 *  - rural junctions: the minor road gives way to the major one; between equal roads, give way
 *    to traffic from the left (Japanese rule); never enter while another car is crossing;
 *  - freeway splits: only smooth movements, take the exit from the kerb lane, merge from the
 *    slip road when the kerb lane is clear;
 *  - dead ends: turn around.
 * Before turning they move to the right lane (kerb lane for left turns and exits, inner lane for
 * right turns), then follow a cubic curve through the junction. Cars spawn on roads around the
 * player out of view and are removed when far away.
 */
public final class Traffic {
    public static final class Car {
        public int style;
        public float x, z, y, yaw, speed;
        float cruise, len, wid;
        // lane state
        int edge, dir, lane;
        float s, off, offTarget;
        // movement planned at the next node
        boolean planned;
        int nextEdge, nextLane;
        float vTurn;
        float waited;
        // turning through a node along a cubic Bezier
        boolean turning;
        int node;
        float t, arc;
        final float[] bez = new float[8];
        float y0, y3;
        public float brake;  // 0..1 for brake lights / debug
        /** Time not yet simulated (cars out of view are updated a few times a second). */
        float lag;
    }

    public final List<Car> cars = new ArrayList<>();
    /** Upper limit on the number of cars (the area's density decides below it when {@link #autoDensity}). */
    public int target = 30;
    /** Pick the number of cars from where the player is: busy city, quieter towns, sparse country. */
    public boolean autoDensity;
    /** Cars wanted for the current area. */
    public int areaTarget = 30;
    private float densityTimer;
    // the camera's view (out-of-view cars are simulated at a lower rate); viewCos -2: no view known
    private float viewX, viewZ, viewFx, viewFz, viewCos = -2f;
    private int frame;
    public float spawnMin = 55f, spawnMax = 150f, despawn = 185f;
    /** Player-driven and parked cars that traffic must queue behind. */
    public List<Vehicle> obstacles;
    /** A car a mission is following: only ever removed when it is really far away. */
    public Car keep;
    /** Statistics: junction movements made, and entries into a city junction on red (should stay 0). */
    public int turnsTotal, redEntries;

    private final City city;
    private final RoadNetwork net;
    private final Random rnd = new Random(42);
    private final StyleSource styles;

    /** Supplies car styles that are ready to draw (baked), or -1 if none yet. */
    public interface StyleSource { int pickReadyStyle(Random r); }

    public Traffic(City city, StyleSource styles) {
        this.city = city;
        this.net = city.roads;
        this.styles = styles;
    }

    private final float[] tmp = new float[5], tmp2 = new float[5], hit = new float[3];

    // ------------------------------------------------------------------ update

    /** The camera this frame: position, horizontal view direction (unit) and half field of view. */
    public void view(float x, float z, float fx, float fz, float halfFov) {
        viewX = x; viewZ = z; viewFx = fx; viewFz = fz;
        viewCos = (float) Math.cos(Math.min(3f, halfFov + 0.3f));
    }

    /** True if the car might be on screen (in the view cone, or close by). */
    private boolean inView(Car c) {
        if (viewCos < -1.5f) return true;
        float dx = c.x - viewX, dz = c.z - viewZ, d2 = dx * dx + dz * dz;
        if (d2 < 30f * 30f) return true;
        return dx * viewFx + dz * viewFz > viewCos * (float) Math.sqrt(d2);
    }

    public void update(float dt, float time, float px, float pz) {
        frame++;
        if (autoDensity) {
            densityTimer -= dt;
            if (densityTimer <= 0f) { densityTimer = 1f; areaTarget = density(px, pz); }
        }
        int want = autoDensity ? Math.min(target, areaTarget) : target;
        for (int i = cars.size() - 1; i >= 0; i--) {
            Car c = cars.get(i);
            float dx = c.x - px, dz = c.z - pz, d2 = dx * dx + dz * dz;
            // far away, or out of view and more than the area needs: gone
            if (d2 > despawn * despawn || (c != keep && cars.size() > want && d2 > spawnMin * spawnMin && !inView(c))) cars.remove(i);
        }
        if (cars.size() < want) spawn(px, pz);
        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            c.lag += dt;
            // out of view: simulate four times less often in bigger steps (no one sees the jumps)
            if (!c.turning && !inView(c) && ((frame + i) & 3) != 0 && c.lag < 0.2f) continue;
            float step = Math.min(c.lag, 0.2f);
            c.lag = 0f;
            drive(c, step, time, px, pz);
        }
    }

    /**
     * How many cars this area has: the city is busy, town streets less so, the freeway and
     * highways carry a steady trickle, country roads only the odd car.
     */
    private int density(float px, float pz) {
        if (city.nearUrban(px, pz, 100f)) return 30;
        for (com.harlequinxd.drivehard.core.road.Town t : net.towns) {
            if (px > t.x0 - 80f && px < t.x1 + 80f && pz > t.z0 - 80f && pz < t.z1 + 80f)
                return t.size == com.harlequinxd.drivehard.core.road.Town.Size.TOWN ? 16 : (t.size == com.harlequinxd.drivehard.core.road.Town.Size.VILLAGE ? 10 : 6);
        }
        if (net.nearest(px, pz, 120f, hit)) {
            RoadClass k = net.edges.get((int) hit[0]).cls;
            if (k == RoadClass.FREEWAY || k == RoadClass.RAMP) return 12;
            if (k == RoadClass.HIGHWAY) return 8;
        }
        return 4;
    }

    private void spawn(float px, float pz) {
        int style = styles.pickReadyStyle(rnd);
        if (style < 0) return;
        double ang = rnd.nextDouble() * Math.PI * 2;
        float r = spawnMin + rnd.nextFloat() * (spawnMax - spawnMin);
        float qx = px + (float) Math.cos(ang) * r, qz = pz + (float) Math.sin(ang) * r;
        if (!net.nearest(qx, qz, 25f, hit)) return;
        RoadEdge e = net.edges.get((int) hit[0]);
        float s = hit[1];
        if (e.cls == RoadClass.LANE || e.len < 30f || s < e.clearA + 16f || s > e.len - e.clearB - 16f) return;
        int dir = e.oneWay || rnd.nextBoolean() ? 1 : -1;
        int lane = rnd.nextInt(e.cls.lanes);
        float off = e.laneOffset(lane);
        e.lanePoint(s, dir, off, tmp);
        float dx = tmp[0] - px, dz = tmp[1] - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < spawnMin || d > spawnMax) return;
        if (viewCos > -1.5f) {
            float vx = tmp[0] - viewX, vz = tmp[1] - viewZ, vd = (float) Math.sqrt(vx * vx + vz * vz);
            if (vd < 110f && vx * viewFx + vz * viewFz > viewCos * vd) return;   // would pop up on screen
        }
        float gap = e.cls.city ? 14f : 32f;
        for (Car o : cars) {
            float ox = o.x - tmp[0], oz = o.z - tmp[1];
            if (ox * ox + oz * oz < gap * gap) return;
        }
        if (obstacles != null) for (Vehicle v : obstacles) {
            float ox = v.x - tmp[0], oz = v.z - tmp[1];
            if (ox * ox + oz * oz < 12f * 12f) return;
        }
        Car c = new Car();
        c.style = style;
        Cars.Spec sp = Cars.spec(style);
        c.len = sp.L;
        c.wid = sp.W;
        c.edge = e.id; c.dir = dir; c.lane = lane; c.s = s; c.off = c.offTarget = off;
        c.cruise = e.cls.speed * (0.82f + rnd.nextFloat() * 0.18f);
        c.speed = c.cruise * 0.7f;
        place(c, e);
        cars.add(c);
    }

    private void place(Car c, RoadEdge e) {
        e.lanePoint(c.s, c.dir, c.off, tmp);
        c.x = tmp[0];
        c.z = tmp[1];
        c.yaw = (float) Math.atan2(tmp[3], tmp[4]);
        c.y = city.surface(c.x, c.z, tmp[2] + 0.5f);      // the lane's own level: under or over bridges
    }

    // ------------------------------------------------------------------ driving

    private void drive(Car c, float dt, float time, float px, float pz) {
        if (c.turning) {
            followTurn(c, dt, time, px, pz);
            return;
        }
        RoadEdge e = net.edges.get(c.edge);
        int nodeId = c.dir > 0 ? e.b : e.a;
        RoadNode node = net.nodes.get(nodeId);
        float sExit = c.dir > 0 ? e.len - e.clear(nodeId) : e.clear(nodeId);
        float remaining = (sExit - c.s) * c.dir;
        float front = remaining - c.len * 0.5f;

        if (!c.planned && remaining < 90f) plan(c, e, node);

        float want = c.cruise;
        // bends ahead on this road
        e.point(Math.max(0f, Math.min(e.len, c.s)), tmp);
        e.point(Math.max(0f, Math.min(e.len, c.s + c.dir * 30f)), tmp2);
        float dot = tmp[3] * tmp2[3] + tmp[4] * tmp2[4];
        float ang = (float) Math.acos(Math.max(-1f, Math.min(1f, dot)));
        if (ang > 0.05f) want = Math.min(want, (float) Math.sqrt(3.2f * 30f / ang));
        // slow for the movement through the next node
        if (c.planned) want = Math.min(want, (float) Math.sqrt(c.vTurn * c.vTurn + 2f * 3f * Math.max(0f, remaining)));

        // rules at the node
        boolean hold = false;
        if (node.kind == RoadNode.Kind.CITY) {
            float hx = tmp[3] * c.dir, hz = tmp[4] * c.dir;
            boolean ns = Math.abs(hz) > Math.abs(hx);
            int st = city.traffic.state(node.signal, ns, time);
            float stopDist = front - 5.0f;
            boolean mustStop = st == TrafficLights.RED || (st == TrafficLights.YELLOW && stopDist > c.speed * 1.2f);
            if (mustStop && stopDist > -0.5f) want = Math.min(want, Math.max(0f, stopDist) * 0.9f);
            else if (st == TrafficLights.RED && front > 0.2f && c.speed < 4f) want = Math.min(want, Math.max(0f, front - 0.3f));
            hold = st == TrafficLights.RED;
        } else if (node.kind == RoadNode.Kind.JUNCTION && remaining < 40f) {
            if (c.waited < 6f && mustYield(c, e, node)) {
                want = Math.min(want, Math.max(0f, front - 0.6f) * 0.9f);
                if (front < 2.5f && c.speed < 0.8f) c.waited += dt;
                hold = true;
            }
        } else if (node.kind == RoadNode.Kind.SPLIT && e.cls == RoadClass.RAMP && remaining < 60f) {
            if (c.waited < 4f && mergeBlocked(c, node)) {
                want = Math.min(want, Math.max(0f, front - 0.5f) * 0.9f);
                if (front < 3f && c.speed < 0.8f) c.waited += dt;
                hold = true;
            }
        }

        want = Math.min(want, followLimit(c, px, pz));
        accelerate(c, want, dt);

        // lane changes ahead of the turn
        if (c.speed > 1f) {
            float step = 1.4f * dt;
            c.off += Math.max(-step, Math.min(step, c.offTarget - c.off));
        }
        float ds = c.speed * dt;
        if (remaining - ds <= 0f) {
            if (hold) {
                c.s = sExit;
                c.speed = 0f;
            } else {
                c.s = sExit;
                beginNode(c, e, node, time);
                return;
            }
        } else {
            c.s += ds * c.dir;
        }
        place(c, e);
    }

    private void accelerate(Car c, float want, float dt) {
        float acc = want > c.speed ? 3.0f : 7.5f;
        c.brake = want < c.speed - 0.3f ? 1f : 0f;
        c.speed += Math.max(-acc * dt, Math.min(acc * dt, want - c.speed));
        if (c.speed < 0) c.speed = 0;
    }

    /** Speed allowed by the nearest thing in front (cars, parked/player cars, the player on foot). */
    private float followLimit(Car c, float px, float pz) {
        float fx = (float) Math.sin(c.yaw), fz = (float) Math.cos(c.yaw);
        float nearest = Float.MAX_VALUE;
        for (Car o : cars) {
            if (o == c) continue;
            float dx = o.x - c.x, dz = o.z - c.z;
            float ahead = dx * fx + dz * fz;
            if (ahead <= 0f || ahead > 35f) continue;
            float side = Math.abs(dx * fz - dz * fx);
            if (side >= 1.8f) continue;
            // inside a junction only queue behind cars going our way (crossing is settled before entering)
            if ((c.turning || o.turning) && fx * (float) Math.sin(o.yaw) + fz * (float) Math.cos(o.yaw) < 0.3f) continue;
            nearest = Math.min(nearest, ahead - (c.len + o.len) * 0.5f);
        }
        if (obstacles != null) for (Vehicle o : obstacles) {
            float dx = o.x - c.x, dz = o.z - c.z;
            float ahead = dx * fx + dz * fz, side = Math.abs(dx * fz - dz * fx);
            if (ahead > 0 && ahead < 30f && side < 2.2f) nearest = Math.min(nearest, ahead - (c.len + o.len) * 0.5f);
        }
        float dx = px - c.x, dz = pz - c.z;
        float ahead = dx * fx + dz * fz, side = Math.abs(dx * fz - dz * fx);
        if (ahead > 0 && ahead < 25f && side < 1.6f) nearest = Math.min(nearest, ahead - c.len * 0.5f - 0.5f);
        if (nearest == Float.MAX_VALUE) return Float.MAX_VALUE;
        return Math.max(0f, (nearest - 2.5f) / 1.1f);
    }

    /** Give-way rules at an unsignalled junction. */
    private boolean mustYield(Car c, RoadEdge e, RoadNode node) {
        int mine = e.cls.priority;
        int top = 0;
        for (int id : node.edges) if (id != e.id) top = Math.max(top, net.edges.get(id).cls.priority);
        boolean minor = mine < top;
        e.point(c.dir > 0 ? e.len : 0f, tmp);
        float hx = tmp[3] * c.dir, hz = tmp[4] * c.dir;
        float lx = hz, lz = -hx;   // left of my travel
        for (Car o : cars) {
            if (o == c) continue;
            if (o.turning) {
                if (o.node == node.id) return true;     // someone is crossing the junction
                continue;
            }
            RoadEdge oe = net.edges.get(o.edge);
            if (oe.id == e.id) continue;
            int on = o.dir > 0 ? oe.b : oe.a;
            if (on != node.id) continue;
            float oRem = ((o.dir > 0 ? oe.len - oe.clear(on) : oe.clear(on)) - o.s) * o.dir;
            if (oRem > 30f) continue;
            int op = oe.cls.priority;
            if (minor) { if (op >= mine) return true; continue; }
            if (op > mine) return true;
            if (op == mine) {
                // equal roads: give way to traffic approaching from the left
                float rx = o.x - node.x, rz = o.z - node.z;
                if (rx * lx + rz * lz > 2f && oRem < 20f) return true;
            }
        }
        return false;
    }

    /** A slip road may merge when no freeway car is about to pass the merge point in the kerb lane. */
    private boolean mergeBlocked(Car c, RoadNode node) {
        for (Car o : cars) {
            if (o == c || o.turning) continue;
            RoadEdge oe = net.edges.get(o.edge);
            if (oe.cls != RoadClass.FREEWAY) continue;
            float d = (float) Math.hypot(o.x - node.x, o.z - node.z);
            if (d > 70f) continue;
            int on = o.dir > 0 ? oe.b : oe.a;
            boolean approaching = on == node.id;
            if (approaching && o.lane == 0) return true;
            if (!approaching && d < 12f && o.lane == 0) return true;   // just past it
        }
        return false;
    }

    // ------------------------------------------------------------------ choosing movements

    private final List<Integer> cand = new ArrayList<>();
    private final List<Float> weight = new ArrayList<>();

    /** Picks the edge and lane to take at the node ahead, and the lane to be in before it. */
    private void plan(Car c, RoadEdge e, RoadNode node) {
        e.point(c.dir > 0 ? e.len : 0f, tmp);
        float hx = tmp[3] * c.dir, hz = tmp[4] * c.dir;
        cand.clear();
        weight.clear();
        float total = 0f;
        for (int id : node.edges) {
            if (id == e.id) continue;
            RoadEdge ne = net.edges.get(id);
            if (!ne.canLeave(node.id)) continue;
            if (ne.cls == RoadClass.LANE) continue;                               // farm lanes are private
            int nd = ne.dirFrom(node.id);
            ne.point(ne.sAt(node.id), tmp2);
            float ox = tmp2[3] * nd, oz = tmp2[4] * nd;
            float dot = hx * ox + hz * oz;
            if (node.kind == RoadNode.Kind.SPLIT && dot < 0.3f) continue;       // no turning across a freeway
            if (dot < -0.85f) continue;                                           // no U-turns
            float w = dot > 0.7f ? 3f : 1f;
            if (ne.cls == RoadClass.RAMP && e.cls == RoadClass.FREEWAY) w = 0.5f;  // most stay on the freeway
            cand.add(id);
            weight.add(w);
            total += w;
        }
        c.planned = true;
        c.waited = 0f;
        if (cand.isEmpty()) {
            // dead end: turn around in place
            c.nextEdge = -1;
            c.vTurn = 2.5f;
            return;
        }
        float r = rnd.nextFloat() * total;
        int pick = cand.get(cand.size() - 1);
        for (int k = 0; k < cand.size(); k++) { r -= weight.get(k); if (r <= 0f) { pick = cand.get(k); break; } }
        RoadEdge ne = net.edges.get(pick);
        int nd = ne.dirFrom(node.id);
        ne.point(ne.sAt(node.id), tmp2);
        float ox = tmp2[3] * nd, oz = tmp2[4] * nd;
        float left = ox * hz + oz * -hx;   // + = left turn (non-crossing in left-hand traffic)
        int lanes = ne.oneWay ? 1 : ne.cls.lanes;
        int myLanes = e.oneWay ? 1 : e.cls.lanes;
        int nl, req;
        if (left > 0.35f || (ne.cls == RoadClass.RAMP && e.cls == RoadClass.FREEWAY)) { nl = 0; req = 0; }
        else if (left < -0.35f) { nl = lanes - 1; req = myLanes - 1; }
        else { nl = Math.min(c.lane, lanes - 1); req = c.lane; }
        if (e.cls == RoadClass.RAMP) nl = 0;   // merge into the kerb lane
        c.nextEdge = pick;
        c.nextLane = nl;
        c.lane = Math.min(req, myLanes - 1);
        c.offTarget = e.laneOffset(c.lane);
        // speed through the turn from its sharpness
        float dot = Math.max(-1f, Math.min(1f, hx * ox + hz * oz));
        float theta = (float) Math.acos(dot);
        if (theta < 0.25f) c.vTurn = Math.min(c.cruise, ne.cls.speed);
        else {
            float chord = Math.max(4f, e.clear(node.id) + ne.clear(node.id));
            float radius = chord / (2f * (float) Math.sin(theta * 0.5f)) * 0.8f;
            c.vTurn = Math.max(3.5f, Math.min(c.cruise, (float) Math.sqrt(3.0f * radius)));
        }
    }

    private void beginNode(Car c, RoadEdge e, RoadNode node, float time) {
        if (c.nextEdge < 0) {
            // turn around at a dead end
            c.dir = -c.dir;
            c.planned = false;
            c.off = c.offTarget = e.laneOffset(Math.min(c.lane, e.cls.lanes - 1));
            place(c, e);
            return;
        }
        if (node.kind == RoadNode.Kind.CITY) {
            e.point(c.dir > 0 ? e.len : 0f, tmp);
            boolean ns = Math.abs(tmp[4]) > Math.abs(tmp[3]);
            if (city.traffic.state(node.signal, ns, time) == TrafficLights.RED
                    && city.traffic.timeToChange(node.signal, ns, time) < TrafficLights.CYCLE * 0.5f - 2.5f) redEntries++;
        }
        RoadEdge ne = net.edges.get(c.nextEdge);
        int nd = ne.dirFrom(node.id);
        e.lanePoint(c.s, c.dir, c.off, tmp);
        float sEnter = node.id == ne.a ? ne.clearA : ne.len - ne.clearB;
        ne.lanePoint(sEnter, nd, ne.laneOffset(c.nextLane), tmp2);
        float chord = (float) Math.hypot(tmp2[0] - tmp[0], tmp2[1] - tmp[1]);
        float k = Math.max(0.5f, chord * 0.42f);
        float[] b = c.bez;
        b[0] = tmp[0]; b[1] = tmp[1];
        b[2] = tmp[0] + tmp[3] * k; b[3] = tmp[1] + tmp[4] * k;
        b[4] = tmp2[0] - tmp2[3] * k; b[5] = tmp2[1] - tmp2[4] * k;
        b[6] = tmp2[0]; b[7] = tmp2[1];
        c.y0 = city.surface(tmp[0], tmp[1], tmp[2] + 0.5f);
        c.y3 = city.surface(tmp2[0], tmp2[1], tmp2[2] + 0.5f);
        float len = 0f, lx = b[0], lz = b[1];
        for (int i = 1; i <= 8; i++) {
            float t = i / 8f;
            float x = bez(b[0], b[2], b[4], b[6], t), z = bez(b[1], b[3], b[5], b[7], t);
            len += (float) Math.hypot(x - lx, z - lz);
            lx = x; lz = z;
        }
        c.arc = Math.max(0.5f, len);
        c.t = 0f;
        c.turning = true;
        c.node = node.id;
        turnsTotal++;
    }

    private static float bez(float a, float b, float c, float d, float t) {
        float u = 1 - t;
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
    }

    private void followTurn(Car c, float dt, float time, float px, float pz) {
        float want = Math.min(c.vTurn, followLimit(c, px, pz));
        accelerate(c, want, dt);
        c.t += c.speed * dt / c.arc;
        if (c.t >= 1f) {
            RoadEdge ne = net.edges.get(c.nextEdge);
            int nd = ne.dirFrom(c.node);
            c.turning = false;
            c.planned = false;
            c.edge = ne.id;
            c.dir = nd;
            c.lane = c.nextLane;
            c.off = c.offTarget = ne.laneOffset(c.lane);
            c.s = c.node == ne.a ? ne.clearA : ne.len - ne.clearB;
            c.cruise = ne.cls.speed * (0.82f + rnd.nextFloat() * 0.18f);
            place(c, ne);
            return;
        }
        float[] b = c.bez;
        float t = c.t, u = 1 - t;
        c.x = bez(b[0], b[2], b[4], b[6], t);
        c.z = bez(b[1], b[3], b[5], b[7], t);
        float tx = 3 * u * u * (b[2] - b[0]) + 6 * u * t * (b[4] - b[2]) + 3 * t * t * (b[6] - b[4]);
        float tz = 3 * u * u * (b[3] - b[1]) + 6 * u * t * (b[5] - b[3]) + 3 * t * t * (b[7] - b[5]);
        if (tx * tx + tz * tz > 1e-6f) c.yaw = (float) Math.atan2(tx, tz);
        c.y = city.surface(c.x, c.z, c.y0 + (c.y3 - c.y0) * t + 0.5f);
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

    /** True if the car is crossing a junction (for tests). */
    public static boolean turning(Car c) { return c.turning; }
}
