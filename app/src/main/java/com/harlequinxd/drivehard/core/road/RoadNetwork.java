package com.harlequinxd.drivehard.core.road;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.tex.Noise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * The island's road graph. Every road is an edge holding a spline (sampled into a polyline) and
 * a smoothed height profile; edges meet at nodes. It contains the city street grid, villages
 * scattered across the farmland linked by country roads and highways, coastal highways, and
 * three freeways leaving the city with diamond interchanges (one-way slip roads). Country roads
 * that cross each other get a junction; roads that cross a freeway go over it on a bridge.
 *
 * Roads outside the city are cut into the terrain: {@link #shapeTerrain} pulls the ground toward
 * each road's profile (embankments and cuttings).
 */
public final class RoadNetwork {
    public final List<RoadNode> nodes = new ArrayList<>();
    public final List<RoadEdge> edges = new ArrayList<>();
    /** Village centre nodes (for farms and houses). */
    public final List<RoadNode> villages = new ArrayList<>();
    public final int[][] cityNode;

    private final City city;
    private final Terrain terrain;
    private final Random rnd;
    private final int seed;

    // spatial index over road pieces: CSR lists per 256 m cell, items = edge << 16 | piece
    public static final float CELL = 256f;
    private float gx0, gz0;
    private int gnx, gnz;
    private int[] cellStart, cellItems;
    private static final float MARGIN = 56f;

    public RoadNetwork(City city, Terrain terrain, long seed) {
        this.city = city;
        this.terrain = terrain;
        this.seed = (int) seed;
        this.rnd = new Random(seed * 31 + 7);
        cityNode = new int[city.nbx + 1][city.nbz + 1];
        long t0 = System.nanoTime();
        cityGrid();
        RoadNode[][] vil = villageGrid();
        List<RoadEdge> fw = new ArrayList<>();
        int w = freeway(cityNode[0][4], -1, 0, fw);
        int n = freeway(cityNode[6][0], 0, -1, fw);
        int s = freeway(cityNode[3][city.nbz], 0, 1, fw);
        int hn = coastHighway(cityNode[city.nbx][0], -1);
        int hs = coastHighway(cityNode[city.nbx][city.nbz], 1);
        villageLinks(vil);
        for (int end : new int[]{w, n, s, hn, hs}) linkToVillage(end, RoadClass.HIGHWAY, Float.NaN, Float.NaN);
        cityConnectors();
        for (RoadEdge f : fw) interchanges(f);
        resolveCrossings();
        profiles();
        buildIndex();
        buildTime = (System.nanoTime() - t0) / 1e6f;
    }

    public float buildTime;

    // ------------------------------------------------------------------ construction helpers

    private RoadNode addNode(float x, float z, RoadNode.Kind kind) {
        RoadNode nd = new RoadNode(nodes.size(), x, z, Math.max(2.5f, terrain.natural(x, z)), kind);
        nodes.add(nd);
        return nd;
    }

    private RoadEdge addEdge(RoadClass cls, int a, int b, float[] px, float[] pz) {
        RoadEdge e = new RoadEdge(edges.size(), cls, a, b, px, pz, cls.oneWay());
        edges.add(e);
        nodes.get(a).addEdge(e.id);
        nodes.get(b).addEdge(e.id);
        return e;
    }

    private void cityGrid() {
        City c = city;
        for (int j = 0; j <= c.nbz; j++)
            for (int i = 0; i <= c.nbx; i++) {
                RoadNode nd = addNode((c.rx0[i] + c.rx1[i]) * 0.5f, (c.rz0[j] + c.rz1[j]) * 0.5f, RoadNode.Kind.CITY);
                nd.y = terrain.natural(nd.x, nd.z);
                nd.ci = i; nd.cj = j;
                nd.signal = c.traffic.id(i, j);
                cityNode[i][j] = nd.id;
            }
        for (int i = 0; i <= c.nbx; i++)
            for (int j = 0; j < c.nbz; j++) {
                RoadNode a = nodes.get(cityNode[i][j]), b = nodes.get(cityNode[i][j + 1]);
                RoadEdge e = addEdge(c.avenueX[i] ? RoadClass.AVENUE : RoadClass.STREET, a.id, b.id,
                        new float[]{a.x, b.x}, new float[]{a.z, b.z});
                e.ns = true;
                e.clearA = (c.rz1[j] - c.rz0[j]) * 0.5f;
                e.clearB = (c.rz1[j + 1] - c.rz0[j + 1]) * 0.5f;
            }
        for (int j = 0; j <= c.nbz; j++)
            for (int i = 0; i < c.nbx; i++) {
                RoadNode a = nodes.get(cityNode[i][j]), b = nodes.get(cityNode[i + 1][j]);
                RoadEdge e = addEdge(c.avenueZ[j] ? RoadClass.AVENUE : RoadClass.STREET, a.id, b.id,
                        new float[]{a.x, b.x}, new float[]{a.z, b.z});
                e.ns = false;
                e.clearA = (c.rx1[i] - c.rx0[i]) * 0.5f;
                e.clearB = (c.rx1[i + 1] - c.rx0[i + 1]) * 0.5f;
            }
    }

    // ------------------------------------------------------------------ villages

    public static final float VILLAGE_SPACING = 9000f;

    private RoadNode[][] villageGrid() {
        int n = (int) (Terrain.ISLAND_SIZE / VILLAGE_SPACING);
        RoadNode[][] v = new RoadNode[n][n];
        for (int gz = 0; gz < n; gz++)
            for (int gx = 0; gx < n; gx++) {
                float bx = terrain.islandX0 + VILLAGE_SPACING * (gx + 0.5f), bz = terrain.islandZ0 + VILLAGE_SPACING * (gz + 0.5f);
                float best = Float.MAX_VALUE, vx = 0, vz = 0;
                for (int k = 0; k < 6; k++) {
                    float x = bx + (Noise.rnd(gx, gz, seed + 600 + k) - 0.5f) * 6500f;
                    float z = bz + (Noise.rnd(gx, gz, seed + 610 + k) - 0.5f) * 6500f;
                    if (terrain.coastOver(x, z) > -1500f || terrain.cityDistance(x, z) < 4500f) continue;
                    float h = terrain.natural(x, z);
                    if (h < best) { best = h; vx = x; vz = z; }
                }
                if (best > 150f) continue;
                RoadNode nd = addNode(vx, vz, RoadNode.Kind.JUNCTION);
                v[gx][gz] = nd;
                villages.add(nd);
            }
        return v;
    }

    private void villageLinks(RoadNode[][] v) {
        int n = v.length;
        for (int gz = 0; gz < n; gz++)
            for (int gx = 0; gx < n; gx++) {
                RoadNode a = v[gx][gz];
                if (a == null) continue;
                boolean hwX = gz % 4 == 1, hwZ = gx % 4 == 1;
                // a few country links are left out so the grid doesn't read as a grid
                boolean skipX = !hwX && Noise.rnd(gx, gz, seed + 640) < 0.22f;
                boolean skipZ = !hwZ && Noise.rnd(gx, gz, seed + 641) < 0.22f;
                for (int k = 1; k <= 2 && gx + k < n && !skipX; k++) {
                    RoadNode b = v[gx + k][gz];
                    if (b == null) continue;
                    route(hwX ? RoadClass.HIGHWAY : RoadClass.COUNTRY, a, b, 0f, 0f, 0f, 0f);
                    break;
                }
                for (int k = 1; k <= 2 && gz + k < n && !skipZ; k++) {
                    RoadNode b = v[gx][gz + k];
                    if (b == null) continue;
                    route(hwZ ? RoadClass.HIGHWAY : RoadClass.COUNTRY, a, b, 0f, 0f, 0f, 0f);
                    break;
                }
                // occasional diagonal lanes
                if (gx + 1 < n && gz + 1 < n && Noise.rnd(gx, gz, seed + 642) < 0.25f) {
                    RoadNode b = v[gx + 1][gz + 1];
                    if (b != null) route(RoadClass.COUNTRY, a, b, 0f, 0f, 0f, 0f);
                }
            }
    }

    /** Joins a node to the nearest village, preferring villages in direction (hx,hz) if given. */
    private void linkToVillage(int nodeId, RoadClass cls, float hx, float hz) {
        RoadNode a = nodes.get(nodeId);
        RoadNode best = null;
        float bd = Float.MAX_VALUE;
        for (RoadNode v : villages) {
            float dx = v.x - a.x, dz = v.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (!Float.isNaN(hx) && (dx * hx + dz * hz) < d * 0.3f) continue;
            if (d < bd) { bd = d; best = v; }
        }
        if (best == null || bd > 22000f) return;
        float sx = Float.isNaN(hx) ? 0f : hx, sz = Float.isNaN(hz) ? 0f : hz;
        route(cls, a, best, sx, sz, 0f, 0f);
    }

    private void cityConnectors() {
        City c = city;
        int[][] west = {{0, 1}, {0, 7}, {0, 9}};
        for (int[] p : west) linkToVillage(cityNode[p[0]][p[1]], RoadClass.COUNTRY, -1f, 0f);
        int[] north = {2, 8};
        for (int i : north) linkToVillage(cityNode[i][0], RoadClass.COUNTRY, 0f, -1f);
        int[] south = {1, 8};
        for (int i : south) linkToVillage(cityNode[i][c.nbz], RoadClass.COUNTRY, 0f, 1f);
    }

    // ------------------------------------------------------------------ routing

    /**
     * Builds a winding road from a to b. (sx,sz)/(ex,ez): optional unit directions the road leaves
     * a / arrives at b with (zero = free), used for straight exits from the city and ramps.
     */
    private RoadEdge route(RoadClass cls, RoadNode a, RoadNode b, float sx, float sz, float ex, float ez) {
        List<float[]> wp = new ArrayList<>();
        wp.add(new float[]{a.x, a.z});
        float dx = b.x - a.x, dz = b.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        float ux = dx / d, uz = dz / d, nx = -uz, nz = ux;
        float lead = 0f;
        if (sx != 0f || sz != 0f) {
            lead = Math.min(500f, d * 0.2f);
            wp.add(new float[]{a.x + sx * lead, a.z + sz * lead});
        }
        int n = Math.max(1, (int) (d / 1400f));
        float amp = cls == RoadClass.HIGHWAY ? 350f : 600f;
        for (int k = 1; k < n; k++) {
            float u = k / (float) n;
            float bx = a.x + dx * u, bz = a.z + dz * u;
            float taper = (float) Math.sin(Math.PI * u);
            float off = (Noise.rnd(a.id * 131 + k, b.id, seed + 700) - 0.5f) * 2f * amp * taper;
            // pick the lowest of a few lateral positions so roads follow valleys
            float bestH = Float.MAX_VALUE, px = bx, pz = bz;
            for (int t = -1; t <= 1; t++) {
                float o = off + t * 450f * taper;
                float qx = bx + nx * o, qz = bz + nz * o;
                if (terrain.coastOver(qx, qz) > -300f) continue;
                float h = terrain.natural(qx, qz) + Math.abs(t) * 6f;
                if (h < bestH) { bestH = h; px = qx; pz = qz; }
            }
            wp.add(new float[]{px, pz});
        }
        if (ex != 0f || ez != 0f) {
            float l = Math.min(400f, d * 0.2f);
            wp.add(new float[]{b.x - ex * l, b.z - ez * l});
        }
        wp.add(new float[]{b.x, b.z});
        float[][] pl = spline(wp, cls.step);
        return addEdge(cls, a.id, b.id, pl[0], pl[1]);
    }

    /** Catmull-Rom through the waypoints, sampled roughly every {@code step} metres. */
    static float[][] spline(List<float[]> wp, float step) {
        int m = wp.size();
        float[] xs = new float[64], zs = new float[64];
        int cnt = 0;
        for (int i = 0; i < m - 1; i++) {
            float[] p0 = wp.get(Math.max(0, i - 1)), p1 = wp.get(i), p2 = wp.get(i + 1), p3 = wp.get(Math.min(m - 1, i + 2));
            float segLen = (float) Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int n = Math.max(1, (int) Math.ceil(segLen / step));
            for (int k = 0; k < n; k++) {
                float t = k / (float) n, t2 = t * t, t3 = t2 * t;
                float x = 0.5f * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                float z = 0.5f * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                if (cnt == xs.length) { xs = Arrays.copyOf(xs, cnt * 2); zs = Arrays.copyOf(zs, cnt * 2); }
                xs[cnt] = x; zs[cnt] = z; cnt++;
            }
        }
        float[] last = wp.get(m - 1);
        if (cnt == xs.length) { xs = Arrays.copyOf(xs, cnt + 1); zs = Arrays.copyOf(zs, cnt + 1); }
        xs[cnt] = last[0]; zs[cnt] = last[1]; cnt++;
        return new float[][]{Arrays.copyOf(xs, cnt), Arrays.copyOf(zs, cnt)};
    }

    /** Cubic Bezier sampled every {@code step} metres. */
    static float[][] bezier(float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3, float step) {
        float approx = (float) (Math.hypot(x1 - x0, z1 - z0) + Math.hypot(x2 - x1, z2 - z1) + Math.hypot(x3 - x2, z3 - z2));
        int n = Math.max(2, (int) Math.ceil(approx / step));
        float[] xs = new float[n + 1], zs = new float[n + 1];
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n, u = 1 - t;
            xs[k] = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
            zs[k] = u * u * u * z0 + 3 * u * u * t * z1 + 3 * u * t * t * z2 + t * t * t * z3;
        }
        return new float[][]{xs, zs};
    }

    // ------------------------------------------------------------------ freeways & highways

    /** Freeway from a city node heading (dx,dz) to near the coast. Returns the far end node. */
    private int freeway(int startId, int dx, int dz, List<RoadEdge> out) {
        RoadNode a = nodes.get(startId);
        List<float[]> wp = new ArrayList<>();
        wp.add(new float[]{a.x, a.z});
        wp.add(new float[]{a.x + dx * 350f, a.z + dz * 350f});
        wp.add(new float[]{a.x + dx * 1100f, a.z + dz * 1100f});
        float px = a.x + dx * 1100f, pz = a.z + dz * 1100f;
        for (int k = 0; k < 40; k++) {
            float nx = px + dx * 5000f, nz = pz + dz * 5000f;
            if (nx < terrain.islandX0 + 6000f || nz < terrain.islandZ0 + 6000f || nz > terrain.islandZ1 - 6000f) break;
            float best = Float.MAX_VALUE, bx = nx, bz = nz;
            // north/south freeways start on the coast, so they may only drift inland (west)
            int tLo = dz != 0 ? -3 : -2, tHi = dz != 0 ? 1 : 2;
            for (int t = tLo; t <= tHi; t++) {
                // candidate positions shifted sideways (perpendicular to the heading)
                float qx = nx + (dz != 0 ? t * 1300f : 0f), qz = nz + (dx != 0 ? t * 1200f : 0f);
                if (terrain.coastOver(qx, qz) > -1500f) continue;
                float h = 0f;
                for (int q = 0; q <= 4; q++) {
                    float u = q / 4f;
                    h += terrain.natural(px + (qx - px) * u, pz + (qz - pz) * u);
                }
                h += Math.abs(t) * 25f;
                if (h < best) { best = h; bx = qx; bz = qz; }
            }
            if (best == Float.MAX_VALUE) break;
            wp.add(new float[]{bx, bz});
            px = bx; pz = bz;
        }
        RoadNode end = addNode(px, pz, RoadNode.Kind.END);
        float[][] pl = spline(wp, RoadClass.FREEWAY.step);
        out.add(addEdge(RoadClass.FREEWAY, a.id, end.id, pl[0], pl[1]));
        return end.id;
    }

    /** Coastal highway from a city corner heading north (dz=-1) or south (+1), ~900 m inland. */
    private int coastHighway(int startId, int dz) {
        RoadNode a = nodes.get(startId);
        List<float[]> wp = new ArrayList<>();
        wp.add(new float[]{a.x, a.z});
        wp.add(new float[]{a.x, a.z + dz * 400f});
        float z = a.z + dz * 400f, px = a.x;
        for (int k = 0; k < 30; k++) {
            z += dz * 2500f;
            if (Math.abs(z - (terrain.islandZ0 + terrain.islandZ1) * 0.5f) > Terrain.ISLAND_SIZE * 0.5f - 6000f) break;
            // walk inland from the east edge until ~900 m from the coast
            float x = terrain.islandX1 + 3000f;
            while (x > terrain.islandX1 - 20000f && terrain.coastOver(x, z) > -900f) x -= 150f;
            wp.add(new float[]{x, z});
            px = x;
        }
        float[] last = wp.get(wp.size() - 1);
        RoadNode end = addNode(last[0], last[1], RoadNode.Kind.END);
        float[][] pl = spline(wp, RoadClass.HIGHWAY.step);
        addEdge(RoadClass.HIGHWAY, a.id, end.id, pl[0], pl[1]);
        return end.id;
    }

    /** Diamond interchanges every ~9 km: one-way slip roads to junctions either side of the freeway. */
    private void interchanges(RoadEdge f) {
        List<Float> spots = new ArrayList<>();
        for (float s = 5000f; s < f.len - 3000f; s += 8000f + Noise.rnd(f.id, (int) s, seed + 800) * 3000f) spots.add(s);
        // split from the far end so earlier distances stay valid on the remaining first piece
        RoadEdge cur = f;
        float[] p = new float[5];
        for (int k = spots.size() - 1; k >= 0; k--) {
            float sc = spots.get(k);
            cur.point(sc, p);
            float px = p[0], pz = p[1], tx = p[3], tz = p[4];
            float lx = tz, lz = -tx;              // left of travel a→b
            int nUp = splitAt(cur, sc + 260f, RoadNode.Kind.SPLIT);   // later piece
            int nDn = splitAt(cur, sc - 260f, RoadNode.Kind.SPLIT);
            RoadNode up = nodes.get(nUp), dn = nodes.get(nDn);
            float lane = RoadClass.FREEWAY.median + (RoadClass.FREEWAY.lanes - 0.5f) * RoadClass.FREEWAY.laneW;
            RoadNode tL = addNode(px + lx * 85f, pz + lz * 85f, RoadNode.Kind.JUNCTION);
            RoadNode tR = addNode(px - lx * 85f, pz - lz * 85f, RoadNode.Kind.JUNCTION);
            // a→b travel (left side): off-ramp at dn, on-ramp to up
            ramp(dn, tL, lx, lz, tx, tz, lane, true);
            ramp(up, tL, lx, lz, tx, tz, lane, false);
            // b→a travel (right side): off at up, on to dn
            ramp(up, tR, -lx, -lz, -tx, -tz, lane, true);
            ramp(dn, tR, -lx, -lz, -tx, -tz, lane, false);
            linkToVillage(tL.id, RoadClass.COUNTRY, lx, lz);
            linkToVillage(tR.id, RoadClass.COUNTRY, -lx, -lz);
        }
    }

    /**
     * One-way slip road between freeway node fn and terminal t on side (sx,sz) of travel (hx,hz).
     * off: freeway→terminal (diverging forward); else terminal→freeway (merging forward).
     */
    private void ramp(RoadNode fn, RoadNode t, float sx, float sz, float hx, float hz, float lane, boolean off) {
        float ox = fn.x + sx * lane, oz = fn.z + sz * lane;
        float[][] pl;
        if (off) pl = bezier(ox, oz, ox + hx * 140f, oz + hz * 140f, t.x - sx * 45f, t.z - sz * 45f, t.x, t.z, RoadClass.RAMP.step);
        else pl = bezier(t.x, t.z, t.x - sx * 45f, t.z - sz * 45f, ox - hx * 140f, oz - hz * 140f, ox, oz, RoadClass.RAMP.step);
        // the slip road starts/ends on the freeway's kerb lane (a taper), not at the node centre
        RoadEdge e = off ? addEdge(RoadClass.RAMP, fn.id, t.id, pl[0], pl[1]) : addEdge(RoadClass.RAMP, t.id, fn.id, pl[0], pl[1]);
        e.rampSide = lane;
    }

    /**
     * Splits edge e at distance d into [a..new] (kept as e) and [new..b] (new edge). Returns the
     * new node id. Samples are copied; the split point is inserted exactly.
     */
    private int splitAt(RoadEdge e, float d, RoadNode.Kind kind) {
        float[] p = new float[5];
        e.point(d, p);
        int i = e.seg(d);
        RoadNode nd = addNode(p[0], p[1], kind);
        int n = e.count();
        float[] ax = new float[i + 2], az = new float[i + 2];
        System.arraycopy(e.x, 0, ax, 0, i + 1);
        System.arraycopy(e.z, 0, az, 0, i + 1);
        ax[i + 1] = p[0]; az[i + 1] = p[1];
        float[] bx = new float[n - i], bz = new float[n - i];
        bx[0] = p[0]; bz[0] = p[1];
        System.arraycopy(e.x, i + 1, bx, 1, n - i - 1);
        System.arraycopy(e.z, i + 1, bz, 1, n - i - 1);
        // drop zero-length pieces next to the cut
        float[][] A = dedupe(ax, az), B = dedupe(bx, bz);
        int oldB = e.b;
        nodes.get(oldB).removeEdge(e.id);
        e.b = nd.id;
        e.setPoints(A[0], A[1]);
        nd.addEdge(e.id);
        RoadEdge ne = addEdge(e.cls, nd.id, oldB, B[0], B[1]);
        ne.rampSide = e.rampSide;
        ne.origin = e.origin;
        ne.ns = e.ns;
        return nd.id;
    }

    private static float[][] dedupe(float[] x, float[] z) {
        int n = x.length, m = 0;
        float[] ox = new float[n], oz = new float[n];
        for (int i = 0; i < n; i++) {
            if (m > 0 && Math.abs(x[i] - ox[m - 1]) < 0.05f && Math.abs(z[i] - oz[m - 1]) < 0.05f) {
                if (i == n - 1) { ox[m - 1] = x[i]; oz[m - 1] = z[i]; }
                continue;
            }
            ox[m] = x[i]; oz[m] = z[i]; m++;
        }
        if (m < 2) { ox[1] = x[n - 1] + 0.1f; oz[1] = z[n - 1]; m = 2; }
        return new float[][]{Arrays.copyOf(ox, m), Arrays.copyOf(oz, m)};
    }

    // ------------------------------------------------------------------ crossings

    /** Pending bridge: a road passing over a motorway near (x,z). */
    private static final class BridgeMark {
        final float x, z, overY;
        final RoadEdge under;
        final float underS;
        BridgeMark(float x, float z, RoadEdge under, float underS) { this.x = x; this.z = z; this.under = under; this.underS = underS; this.overY = 0f; }
    }

    private final List<BridgeMark> bridgeMarks = new ArrayList<>();

    private void resolveCrossings() {
        // grid of pieces of rural roads
        HashMap<Long, List<int[]>> grid = new HashMap<>();
        for (RoadEdge e : edges) {
            if (e.cls.city) continue;
            for (int i = 0; i < e.count() - 1; i++) {
                int cx0 = (int) Math.floor(Math.min(e.x[i], e.x[i + 1]) / 512f), cx1 = (int) Math.floor(Math.max(e.x[i], e.x[i + 1]) / 512f);
                int cz0 = (int) Math.floor(Math.min(e.z[i], e.z[i + 1]) / 512f), cz1 = (int) Math.floor(Math.max(e.z[i], e.z[i + 1]) / 512f);
                for (int cz = cz0; cz <= cz1; cz++)
                    for (int cx = cx0; cx <= cx1; cx++)
                        grid.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), k -> new ArrayList<>()).add(new int[]{e.id, i});
            }
        }
        // find crossings
        List<float[]> junctions = new ArrayList<>();          // x, z, edgeA, edgeB
        HashMap<Long, Boolean> seen = new HashMap<>();
        for (List<int[]> list : grid.values()) {
            for (int p = 0; p < list.size(); p++)
                for (int q = p + 1; q < list.size(); q++) {
                    int[] A = list.get(p), B = list.get(q);
                    if (A[0] == B[0]) continue;
                    RoadEdge ea = edges.get(A[0]), eb = edges.get(B[0]);
                    float[] hit = intersect(ea.x[A[1]], ea.z[A[1]], ea.x[A[1] + 1], ea.z[A[1] + 1], eb.x[B[1]], eb.z[B[1]], eb.x[B[1] + 1], eb.z[B[1] + 1]);
                    if (hit == null) continue;
                    long key = ((long) Math.round(hit[0] / 2f) << 32) ^ (Math.round(hit[1] / 2f) & 0xFFFFFFFFL);
                    if (seen.put(key, Boolean.TRUE) != null) continue;
                    // ignore meeting at a shared node
                    if (nearNode(ea, hit[0], hit[1], 40f) || nearNode(eb, hit[0], hit[1], 40f)) continue;
                    boolean ma = ea.cls.motorway(), mb = eb.cls.motorway();
                    if (ma && mb) continue;
                    if (ma != mb) {
                        RoadEdge under = ma ? ea : eb;
                        float us = under.s[ma ? A[1] : B[1]] + hit[ma ? 2 : 3] * (under.s[(ma ? A[1] : B[1]) + 1] - under.s[ma ? A[1] : B[1]]);
                        bridgeMarks.add(new BridgeMark(hit[0], hit[1], under, us));
                    } else {
                        junctions.add(new float[]{hit[0], hit[1], ea.id, eb.id});
                    }
                }
        }
        // split crossing country roads into junctions (process each crossing on the current pieces)
        for (float[] j : junctions) {
            RoadNode nd = null;
            for (int k = 2; k < 4; k++) {
                RoadEdge e = pieceNear(j[0], j[1], (int) j[k]);
                if (e == null) continue;
                float d = project(e, j[0], j[1]);
                if (d < 30f || d > e.len - 30f) continue;
                if (nd == null) {
                    int id = splitAt(e, d, RoadNode.Kind.JUNCTION);
                    nd = nodes.get(id);
                } else {
                    splitAtNode(e, d, nd);
                }
            }
        }
    }

    /** Finds the piece (after earlier splits) of an original edge that passes near (x,z). */
    private RoadEdge pieceNear(float x, float z, int originalId) {
        RoadEdge best = null;
        float bd = 3f;
        for (RoadEdge e : edges) {
            if (e.origin != originalId) continue;
            float d = distTo(e, x, z);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private void splitAtNode(RoadEdge e, float d, RoadNode nd) {
        int tmp = splitAt(e, d, RoadNode.Kind.JUNCTION);
        // merge the temporary node into nd
        RoadNode t = nodes.get(tmp);
        for (int eid : t.edges) {
            RoadEdge ee = edges.get(eid);
            if (ee.a == tmp) { ee.a = nd.id; ee.x[0] = nd.x; ee.z[0] = nd.z; }
            if (ee.b == tmp) { ee.b = nd.id; ee.x[ee.count() - 1] = nd.x; ee.z[ee.count() - 1] = nd.z; }
            ee.setPoints(ee.x, ee.z);
            nd.addEdge(eid);
        }
        t.edges = new int[0];
        t.kind = RoadNode.Kind.END;   // orphan, never used
        t.dead = true;
    }

    private boolean nearNode(RoadEdge e, float x, float z, float r) {
        RoadNode a = nodes.get(e.a), b = nodes.get(e.b);
        return Math.hypot(a.x - x, a.z - z) < r || Math.hypot(b.x - x, b.z - z) < r;
    }

    private static float[] intersect(float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz) {
        float rx = bx - ax, rz = bz - az, sx = dx - cx, sz = dz - cz;
        float den = rx * sz - rz * sx;
        if (Math.abs(den) < 1e-6f) return null;
        float t = ((cx - ax) * sz - (cz - az) * sx) / den;
        float u = ((cx - ax) * rz - (cz - az) * rx) / den;
        if (t < 0f || t > 1f || u < 0f || u > 1f) return null;
        return new float[]{ax + rx * t, az + rz * t, t, u};
    }

    /** Distance along e of the closest point to (x,z). */
    static float project(RoadEdge e, float x, float z) {
        float best = Float.MAX_VALUE, bs = 0f;
        for (int i = 0; i < e.count() - 1; i++) {
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            float d = qx * qx + qz * qz;
            if (d < best) { best = d; bs = e.s[i] + t * (e.s[i + 1] - e.s[i]); }
        }
        return bs;
    }

    static float distTo(RoadEdge e, float x, float z) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < e.count() - 1; i++) {
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            best = Math.min(best, qx * qx + qz * qz);
        }
        return (float) Math.sqrt(best);
    }

    // ------------------------------------------------------------------ height profiles

    private void profiles() {
        // node heights: natural ground (above sea), freeway split nodes are refined below
        for (RoadNode n : nodes) if (n.kind != RoadNode.Kind.CITY) n.y = Math.max(2.5f, terrain.natural(n.x, n.z));
        // city streets follow the ground
        for (RoadEdge e : edges) if (e.cls.city) for (int i = 0; i < e.count(); i++) e.y[i] = terrain.natural(e.x[i], e.z[i]);
        // freeways first (their split nodes take the freeway's height), then slip roads, then the rest
        RoadClass[] order = {RoadClass.FREEWAY, RoadClass.RAMP, RoadClass.HIGHWAY, RoadClass.COUNTRY};
        for (RoadClass cls : order) {
            if (cls == RoadClass.FREEWAY) {
                // smooth whole freeway chains across their split nodes
                for (RoadEdge e : edges) if (e.cls == cls) profile(e);
                for (RoadEdge e : edges) if (e.cls == cls) {
                    if (nodes.get(e.a).kind == RoadNode.Kind.SPLIT) nodes.get(e.a).y = e.y[0];
                    if (nodes.get(e.b).kind == RoadNode.Kind.SPLIT) nodes.get(e.b).y = e.y[e.count() - 1];
                }
                for (RoadEdge e : edges) if (e.cls == cls) pin(e);
            } else {
                for (RoadEdge e : edges) if (e.cls == cls) { profile(e); pin(e); }
            }
        }
        // bridges: lift roads over motorways
        for (BridgeMark m : bridgeMarks) {
            float[] p = new float[5];
            m.under.point(m.underS, p);
            float clearY = p[2] + 7.2f;
            RoadEdge over = null;
            float bd = 3f;
            for (RoadEdge e : edges) {
                if (e.cls.city || e.cls.motorway()) continue;
                float d = distTo(e, m.x, m.z);
                if (d < bd) { bd = d; over = e; }
            }
            if (over == null) continue;
            float sc = project(over, m.x, m.z);
            float flat = m.under.cls.halfWidth() + 14f, ramp = 230f;
            for (int i = 0; i < over.count(); i++) {
                float d = Math.abs(over.s[i] - sc);
                float w = d < flat ? 1f : 1f - smooth01((d - flat) / ramp);
                if (w > 0f) over.y[i] = Math.max(over.y[i], over.y[i] + (clearY - over.y[i]) * w);
            }
            for (int i = 0; i < over.count() - 1; i++) {
                float d = Math.abs((over.s[i] + over.s[i + 1]) * 0.5f - sc);
                if (d < flat) over.bridge[i] = true;
            }
        }
        // clearances at nodes
        for (RoadEdge e : edges) {
            if (e.cls.city) continue;
            e.clearA = clearance(e, nodes.get(e.a));
            e.clearB = clearance(e, nodes.get(e.b));
        }
    }

    private float clearance(RoadEdge e, RoadNode n) {
        if (n.kind == RoadNode.Kind.SPLIT) return 1f;
        if (n.kind == RoadNode.Kind.CITY) {
            // stop at the edge of the city street it joins
            float[] p = new float[5];
            e.point(n.id == e.a ? 0f : e.len, p);
            boolean alongX = Math.abs(p[3]) > Math.abs(p[4]);
            return alongX ? (city.rx1[n.ci] - city.rx0[n.ci]) * 0.5f : (city.rz1[n.cj] - city.rz0[n.cj]) * 0.5f;
        }
        if (n.kind == RoadNode.Kind.END) return 2f;
        float w = 0f;
        for (int id : n.edges) if (id != e.id) w = Math.max(w, edges.get(id).cls.halfWidth());
        if (n.kind == RoadNode.Kind.CITY) w = Math.max(w, 6f);
        return Math.min(e.len * 0.45f, w + 2.5f);
    }

    private static float smooth01(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    /** Smoothed, grade-limited copy of the ground along the road. */
    private void profile(RoadEdge e) {
        int n = e.count();
        float[] h = new float[n];
        for (int i = 0; i < n; i++) h[i] = Math.max(2.5f, terrain.natural(e.x[i], e.z[i]));
        float step = Math.max(1f, e.len / Math.max(1, n - 1));
        int r = Math.max(1, (int) (e.cls.smoothWindow / step / 2));
        float[] tmp = new float[n];
        for (int pass = 0; pass < 3; pass++) {
            // running-sum box filter
            float sum = 0f;
            int lo = 0, hi = -1;
            for (int i = 0; i < n; i++) {
                int a = Math.max(0, i - r), b = Math.min(n - 1, i + r);
                while (hi < b) sum += h[++hi];
                while (lo < a) sum -= h[lo++];
                tmp[i] = sum / (hi - lo + 1);
            }
            System.arraycopy(tmp, 0, h, 0, n);
        }
        // grade limit both ways
        float g = e.cls.grade;
        for (int i = 1; i < n; i++) {
            float ds = e.s[i] - e.s[i - 1];
            h[i] = Math.max(h[i - 1] - g * ds, Math.min(h[i - 1] + g * ds, h[i]));
        }
        for (int i = n - 2; i >= 0; i--) {
            float ds = e.s[i + 1] - e.s[i];
            h[i] = Math.max(h[i + 1] - g * ds, Math.min(h[i + 1] + g * ds, h[i]));
        }
        for (int i = 0; i < n; i++) e.y[i] = Math.max(2.5f, h[i]);
    }

    /** Shifts the profile linearly so its ends meet the node heights. */
    private void pin(RoadEdge e) {
        int n = e.count();
        float da = nodes.get(e.a).y - e.y[0], db = nodes.get(e.b).y - e.y[n - 1];
        for (int i = 0; i < n; i++) {
            float t = e.len > 0f ? e.s[i] / e.len : 0f;
            // blend the correction in over the first/last stretch only, so long roads keep their shape
            float wa = 1f - smooth01(e.s[i] / Math.min(600f, e.len)), wb = 1f - smooth01((e.len - e.s[i]) / Math.min(600f, e.len));
            e.y[i] += da * wa + db * wb + 0f * t;
        }
    }

    // ------------------------------------------------------------------ spatial index

    private void buildIndex() {
        gx0 = terrain.islandX0 - 8000f;
        gz0 = terrain.islandZ0 - 8000f;
        gnx = (int) ((Terrain.ISLAND_SIZE + 16000f) / CELL) + 1;
        gnz = gnx;
        int[] counts = new int[gnx * gnz + 1];
        for (int pass = 0; pass < 2; pass++) {
            int[] fill = pass == 1 ? new int[gnx * gnz] : null;
            for (RoadEdge e : edges) {
                float m = e.cls.halfWidth() + MARGIN;
                for (int i = 0; i < e.count() - 1; i++) {
                    int cx0 = cell(Math.min(e.x[i], e.x[i + 1]) - m, gx0), cx1 = cell(Math.max(e.x[i], e.x[i + 1]) + m, gx0);
                    int cz0 = cell(Math.min(e.z[i], e.z[i + 1]) - m, gz0), cz1 = cell(Math.max(e.z[i], e.z[i + 1]) + m, gz0);
                    for (int cz = Math.max(0, cz0); cz <= Math.min(gnz - 1, cz1); cz++)
                        for (int cx = Math.max(0, cx0); cx <= Math.min(gnx - 1, cx1); cx++) {
                            int c = cz * gnx + cx;
                            if (pass == 0) counts[c + 1]++;
                            else cellItems[cellStart[c] + fill[c]++] = (e.id << 16) | i;
                        }
                }
            }
            if (pass == 0) {
                for (int c = 0; c < gnx * gnz; c++) counts[c + 1] += counts[c];
                cellStart = counts;
                cellItems = new int[counts[gnx * gnz]];
            }
        }
    }

    private static int cell(float v, float o) { return (int) Math.floor((v - o) / CELL); }

    /** Road pieces whose influence may reach cell containing (x,z): packed edge<<16|piece, or null. */
    private int cellIndex(float x, float z) {
        int cx = cell(x, gx0), cz = cell(z, gz0);
        if (cx < 0 || cz < 0 || cx >= gnx || cz >= gnz) return -1;
        return cz * gnx + cx;
    }

    /** Calls back every road piece (edge, index) registered in the cells touching the rectangle. */
    public void forEachPiece(float x0, float z0, float x1, float z1, PieceVisitor v) {
        int cx0 = Math.max(0, cell(x0, gx0)), cx1 = Math.min(gnx - 1, cell(x1, gx0));
        int cz0 = Math.max(0, cell(z0, gz0)), cz1 = Math.min(gnz - 1, cell(z1, gz0));
        for (int cz = cz0; cz <= cz1; cz++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cz * gnx + cx;
                for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                    int it = cellItems[k];
                    v.visit(edges.get(it >>> 16), it & 0xFFFF);
                }
            }
    }

    public interface PieceVisitor { void visit(RoadEdge e, int piece); }

    /** Visits each road piece whose midpoint lies inside the rectangle exactly once. */
    public void forEachPieceIn(float x0, float z0, float x1, float z1, PieceVisitor v) {
        int cx0 = Math.max(0, cell(x0, gx0)), cx1 = Math.min(gnx - 1, cell(x1, gx0));
        int cz0 = Math.max(0, cell(z0, gz0)), cz1 = Math.min(gnz - 1, cell(z1, gz0));
        for (int cz = cz0; cz <= cz1; cz++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cz * gnx + cx;
                for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                    int it = cellItems[k];
                    RoadEdge e = edges.get(it >>> 16);
                    int i = it & 0xFFFF;
                    float mx = (e.x[i] + e.x[i + 1]) * 0.5f, mz = (e.z[i] + e.z[i + 1]) * 0.5f;
                    if (mx < x0 || mx >= x1 || mz < z0 || mz >= z1) continue;
                    if (cell(mx, gx0) != cx || cell(mz, gz0) != cz) continue;
                    v.visit(e, i);
                }
            }
    }

    // ------------------------------------------------------------------ terrain shaping & queries

    /** Pulls natural ground toward nearby rural roads (embankments and cuttings). */
    public float shapeTerrain(float x, float z, float nat) {
        if (x > city.cityX0 && x < city.cityX1 && z > city.cityZ0 && z < city.cityZ1) return nat;
        int c = cellIndex(x, z);
        if (c < 0) return nat;
        float bestW = 0f, bestY = nat;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (e.cls.city) continue;
            int i = it & 0xFFFF;
            if (e.bridge[i]) continue;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2 : 0f;
            t = Math.max(0f, Math.min(1f, t));
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            float d = (float) Math.sqrt(qx * qx + qz * qz);
            float hw = e.cls.halfWidth() + 0.6f;
            if (d > hw + 50f) continue;
            float yr = e.y[i] + (e.y[i + 1] - e.y[i]) * t;
            float bank = Math.max(5f, Math.min(48f, 5f + Math.abs(nat - yr) * 1.5f));
            float w = d <= hw ? 1f : 1f - smooth01((d - hw) / bank);
            if (w > bestW || (w == 1f && bestW == 1f && yr > bestY)) { bestW = w; bestY = yr - 0.03f; }
        }
        return nat + (bestY - nat) * bestW;
    }

    /** How much a road shapes the ground here (0 = untouched, 1 = road surface). */
    public float influence(float x, float z) {
        int c = cellIndex(x, z);
        if (c < 0) return 0f;
        float best = 0f;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (e.cls.city) continue;
            int i = it & 0xFFFF;
            float d = pieceDist(e, i, x, z);
            float hw = e.cls.halfWidth() + 0.6f;
            float w = d <= hw ? 1f : 1f - smooth01((d - hw) / 12f);
            best = Math.max(best, w);
        }
        return best;
    }

    /** Distance from (x,z) to the nearest rural road edge (centreline minus half width), capped. */
    public float roadClearance(float x, float z, float cap) {
        int c = cellIndex(x, z);
        if (c < 0) return cap;
        float best = cap;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            float d = pieceDist(e, it & 0xFFFF, x, z) - e.cls.halfWidth();
            best = Math.min(best, d);
        }
        return best;
    }

    /** Distance from (x,z) to the nearest freeway or slip road surface edge, capped. */
    public float motorwayClearance(float x, float z, float cap) {
        int c = cellIndex(x, z);
        if (c < 0) return cap;
        float best = cap;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (!e.cls.motorway()) continue;
            best = Math.min(best, pieceDist(e, it & 0xFFFF, x, z) - e.cls.halfWidth());
        }
        return best;
    }

    private static float pieceDist(RoadEdge e, int i, float x, float z) {
        float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
        float l2 = vx * vx + vz * vz;
        float t = l2 > 0f ? ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2 : 0f;
        t = Math.max(0f, Math.min(1f, t));
        float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
        return (float) Math.sqrt(qx * qx + qz * qz);
    }

    /** Deck height if (x,z) is on a bridge, else NaN. */
    public float bridgeHeight(float x, float z) {
        int c = cellIndex(x, z);
        if (c < 0) return Float.NaN;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            int i = it & 0xFFFF;
            if (!e.bridge[i]) continue;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            if (qx * qx + qz * qz < (e.cls.halfWidth() + 0.3f) * (e.cls.halfWidth() + 0.3f))
                return e.y[i] + (e.y[i + 1] - e.y[i]) * t + 0.03f;
        }
        return Float.NaN;
    }

    /** Nearest road (any class) within maxD: out = {edge id, s, distance}; false if none. */
    public boolean nearest(float x, float z, float maxD, float[] out) {
        int c = cellIndex(x, z);
        if (c < 0) return false;
        float best = maxD;
        boolean found = false;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            int i = it & 0xFFFF;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            float d = (float) Math.sqrt(qx * qx + qz * qz);
            if (d < best) {
                best = d;
                out[0] = e.id;
                out[1] = e.s[i] + t * (e.s[i + 1] - e.s[i]);
                out[2] = d;
                found = true;
            }
        }
        return found;
    }

    /** Total length by class (km), for stats and tests. */
    public float lengthKm(RoadClass cls) {
        double t = 0;
        for (RoadEdge e : edges) if (e.cls == cls) t += e.len;
        return (float) (t / 1000.0);
    }

    public int pieceCount() {
        int n = 0;
        for (RoadEdge e : edges) n += e.count() - 1;
        return n;
    }
}
