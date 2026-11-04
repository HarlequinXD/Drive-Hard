package com.harlequinxd.drivehard.core.road;

import com.harlequinxd.drivehard.core.render.Sectors;

import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.GeoBuilder;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.Arrays;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_GROUND;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Builds every visible road surface from the spline network, city streets included.
 *
 * Each road is drawn between its two junctions as lane-wide strips that follow the spline, with
 * the lane lines painted into the lane textures. Where it reaches a junction it is trimmed back
 * to the junction's clearance, a short "mouth" strip carries the stop line (and zebra crossing in
 * the city), and the junction itself is one polygon joining all the roads that meet there, with
 * curved kerb returns between neighbouring roads. Freeway slip roads taper off the kerb lane.
 *
 * Outside the city each road also gets a cut-and-fill corridor: verges that run from the road
 * edge down (or up) to the shaped terrain, replacing the coarse terrain grid next to the road so
 * roads sit cleanly in cuttings and on embankments. Bridges get decks, parapets and piers;
 * freeways get a median barrier and guardrails; country roads get utility poles and wires.
 *
 * City streets live in the ground layer (they are the city's ground). Rural roads share the
 * object layer with the terrain and sort by their farthest corner with a lead over the ground
 * they lie on, so hills still hide them.
 */
public final class RoadMesh {
    // Paving never overlaps grass (verges and terrain are cut around it), so roads need no lead in the
    // sort: anything standing on them (cars, people) draws over them naturally.
    private static final float ROAD_BIAS = 0.1f, WALL_BIAS = -1.0f, VERGE_BIAS = 0.2f;
    private static final float POLE_STEP = 45f;

    private final RoadNetwork net;
    private final TextureLibrary lib;
    private final Terrain terrain;

    private final float[] nA = new float[2], nB = new float[2];
    private final float[] p = new float[12], uv = new float[8], l = new float[4];
    private final float[] p3 = new float[9], uv3 = new float[6], l3 = new float[3];
    private final float[] tmp = new float[5];

    public RoadMesh(RoadNetwork net, TextureLibrary lib, Terrain terrain) {
        this.net = net;
        this.lib = lib;
        this.terrain = terrain;
    }

    public void generate(Chunk ch, GeoBuilder gb, int cx, int cz, float x0, float z0, float x1, float z1) {
        gb.begin(false);
        net.forEachPieceIn(x0, z0, x1, z1, (e, i) -> piece(ch, gb, e, i));
        // junction surfaces, lanes and verges are painted into the ground map (GroundPainter)
        gb.end();
    }

    // ------------------------------------------------------------------ geometry along a piece

    private RoadEdge e;
    private int pi;
    private boolean city;

    /** Mitred left normal at sample k. */
    private static void normal(RoadEdge e, int k, float[] out) {
        int n = e.count();
        int a = Math.max(0, k - 1), b = Math.min(n - 1, k + 1);
        float dpx = e.x[k] - e.x[a], dpz = e.z[k] - e.z[a];
        float dnx = e.x[b] - e.x[k], dnz = e.z[b] - e.z[k];
        float lp = (float) Math.sqrt(dpx * dpx + dpz * dpz), ln = (float) Math.sqrt(dnx * dnx + dnz * dnz);
        if (lp > 1e-4f) { dpx /= lp; dpz /= lp; }
        if (ln > 1e-4f) { dnx /= ln; dnz /= ln; }
        if (lp <= 1e-4f) { dpx = dnx; dpz = dnz; }
        if (ln <= 1e-4f) { dnx = dpx; dnz = dpz; }
        float ax = dpx + dnx, az = dpz + dnz, la = (float) Math.sqrt(ax * ax + az * az);
        if (la < 1e-4f) { ax = dnx; az = dnz; la = 1f; }
        ax /= la; az /= la;
        float mitre = 1f / Math.max(0.6f, ax * dnx + az * dnz);
        out[0] = az * mitre;
        out[1] = -ax * mitre;
    }

    /** Point at distance s and lateral offset off (left of a→b) into p[k*3]: road height + lift (city: ground). */
    private void at(float s, float off, float lift, int k) {
        float s0 = e.s[pi], s1 = e.s[pi + 1];
        float t = s1 > s0 ? (s - s0) / (s1 - s0) : 0f;
        float bx = e.x[pi] + (e.x[pi + 1] - e.x[pi]) * t, bz = e.z[pi] + (e.z[pi + 1] - e.z[pi]) * t;
        float nx = nA[0] + (nB[0] - nA[0]) * t, nz = nA[1] + (nB[1] - nA[1]) * t;
        float x = bx + nx * off, z = bz + nz * off;
        p[k * 3] = x;
        // bridges follow their own profile; everything else stands on the painted ground
        p[k * 3 + 1] = !e.bridge[pi] ? terrain.height(x, z) + lift : e.y[pi] + (e.y[pi + 1] - e.y[pi]) * t + lift;
        p[k * 3 + 2] = z;
    }

    private int layer() { return city ? L_GROUND : L_OBJECT; }
    private int sortFlags() { return city ? 0 : Geo.FLAG_SORT_FAR; }
    private float bias(float rural) { return city ? 0f : rural; }

    /** Flat strip between offsets o0..o1 from s a..b with world-ish uv (plain asphalt). */
    private void strip(GeoBuilder gb, float a, float b, float o0, float o1, float lift, int tex, float bias, int flags, float uScale, float vBase) {
        at(a, o0, lift, 0);
        at(a, o1, lift, 1);
        at(b, o1, lift, 2);
        at(b, o0, lift, 3);
        uv[0] = o0 * uScale; uv[1] = (a - vBase) * 0.5f;
        uv[2] = o1 * uScale; uv[3] = (a - vBase) * 0.5f;
        uv[4] = o1 * uScale; uv[5] = (b - vBase) * 0.5f;
        uv[6] = o0 * uScale; uv[7] = (b - vBase) * 0.5f;
        float lt = GeoBuilder.sunLight(0, 1, 0);
        l[0] = l[1] = l[2] = l[3] = lt;
        gb.quad(p, uv, l, tex, layer(), sortFlags() | flags, bias(bias), true);
    }

    /** Lane strip: u 0 at the centre-side offset oc to 1 at the kerb-side offset ok; v repeats every 20 m. */
    private void laneStrip(GeoBuilder gb, float a, float b, float oc, float ok, int tex, float vBase) {
        at(a, oc, 0.04f, 0);
        at(a, ok, 0.04f, 1);
        at(b, ok, 0.04f, 2);
        at(b, oc, 0.04f, 3);
        float va = (a - vBase) / 20f, vb = (b - vBase) / 20f;
        uv[0] = 0f; uv[1] = va;
        uv[2] = 1f; uv[3] = va;
        uv[4] = 1f; uv[5] = vb;
        uv[6] = 0f; uv[7] = vb;
        float lt = GeoBuilder.sunLight(0, 1, 0);
        l[0] = l[1] = l[2] = l[3] = lt;
        gb.quad(p, uv, l, tex, layer(), sortFlags(), bias(ROAD_BIAS), true);
    }

    /** Vertical strip at offset o from height lift0 to lift1 (double sided). */
    private void wallStrip(GeoBuilder gb, float a, float b, float o, float lift0, float lift1, int tex, float light, int flags) {
        at(a, o, lift0, 0);
        at(b, o, lift0, 1);
        at(b, o, lift1, 2);
        at(a, o, lift1, 3);
        float len = b - a;
        uv[0] = 0; uv[1] = 1; uv[2] = len / 4f; uv[3] = 1; uv[4] = len / 4f; uv[5] = 0; uv[6] = 0; uv[7] = 0;
        l[0] = l[1] = light * 0.85f;
        l[2] = l[3] = light;
        gb.quad(p, uv, l, tex, L_OBJECT, flags, WALL_BIAS, true);
    }

    /**
     * Cross-section bands {centre-side offset, kerb-side offset, texture (-1 plain asphalt), symmetric}.
     * Painted lines live in the lane textures, so markings sort exactly with the road surface.
     */
    private float[][] bands(RoadClass c) {
        float hw = c.halfWidth();
        switch (c) {
            case STREET:
                return new float[][]{{0f, 0.25f, lib.laneHwMedian, 1}, {0.25f, 3.75f, lib.laneStreet, 0}, {3.75f, hw, -1, 0}};
            case AVENUE:
                return new float[][]{{0f, 1.3f, -1, 1}, {1.3f, 4.8f, lib.laneAvInner, 0}, {4.8f, 8.3f, lib.laneAvOuter, 0}, {8.3f, hw, -1, 0}};
            case COUNTRY:
                return new float[][]{{0f, 3.1f, lib.laneCountry, 0}, {3.1f, hw, -1, 0}};
            case HIGHWAY:
                return new float[][]{{0f, 0.3f, lib.laneHwMedian, 1}, {0.3f, 3.55f, lib.laneHwInner, 0},
                        {3.55f, 6.8f, lib.laneHwOuter, 0}, {6.8f, hw, -1, 0}};
            case FREEWAY:
                return new float[][]{{0f, 1.4f, -1, 1}, {1.4f, 4.9f, lib.laneFwInner, 0},
                        {4.9f, 8.4f, lib.laneFwOuter, 0}, {8.4f, hw, -1, 0}};
            case LANE:
                return new float[][]{{0f, hw, -1, 1}};
            case TOWN:
                return new float[][]{{0f, 2.75f, lib.laneTown, 0}, {2.75f, hw, lib.walkStrip, 0}};
            default:   // RAMP
                return new float[][]{{0f, 1.875f, lib.laneRamp, 1}, {1.875f, hw, -1, 0}};
        }
    }

    private int mouthTex(RoadClass c) {
        switch (c) {
            case STREET: return lib.mouthStreet;
            case AVENUE: return lib.mouthAvenue;
            case HIGHWAY: return lib.mouthHighway;
            case LANE: return lib.mouthLane;
            default: return lib.mouthCountry;
        }
    }

    private void piece(Chunk ch, GeoBuilder gb, RoadEdge edge, int i) {
        e = edge;
        pi = i;
        city = edge.cls.city;
        RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
        float s0 = e.s[i], s1 = e.s[i + 1];
        float ma = net.mouth(e, na), mb = net.mouth(e, nb);
        // roads stop at a junction outline; through freeway splits and at dead ends they run on
        float trimA = na.ring != null ? e.clearA : 0f, trimB = nb.ring != null ? e.clearB : 0f;
        float a = Math.max(s0, trimA), b = Math.min(s1, e.len - trimB);
        normal(e, i, nA);
        normal(e, i + 1, nB);
        RoadClass cls = e.cls;
        float hw = cls.halfWidth();
        boolean bridge = e.bridge[i];
        // ramp still merged with the freeway (the taper / gore): plain asphalt, no rails
        boolean gore = cls == RoadClass.RAMP && rampOverlap();
        if (city) return;        // city streets are painted flat into the ground map
        if (b - a > 0.05f && bridge) {
            // bridge decks are slabs in the sector renderer (no polygons): one per lane band,
            // with the road below passing underneath
            float vBase = (float) Math.floor(s0 / 64f) * 64f;
            float laneBase = (float) Math.floor(s0 / 320f) * 320f;
            for (float[] bd : bands(cls)) {
                int tex = gore ? -1 : (int) bd[2];
                if (bd[3] > 0f) { deckSlab(ch, a, b, -bd[1], bd[1], tex, laneBase, vBase); continue; }
                for (int side = -1; side <= 1; side += 2) {
                    float oc = bd[0] * side, ok = bd[1] * side;
                    if (tex < 0) deckSlab(ch, a, b, Math.min(oc, ok), Math.max(oc, ok), -1, laneBase, vBase);
                    else deckSlab(ch, a, b, oc, ok, tex, laneBase, vBase);
                }
            }
        }
        if (b - a > 0.05f) {
            if (!city) {
                if (cls == RoadClass.FREEWAY) {
                    wallStrip(gb, a, b, 0f, 0f, 0.85f, lib.concrete, 0.95f, 0);
                    for (int side = -1; side <= 1; side += 2)
                        if (!rampAlongside(a, b, side * hw)) wallStrip(gb, a, b, side * (hw - 0.3f), 0.35f, 0.8f, lib.guardrail, 1f, Geo.FLAG_DETAIL);
                }
                if (cls == RoadClass.RAMP && !gore && !bridge) {
                    wallStrip(gb, a, b, hw - 0.3f, 0.35f, 0.8f, lib.guardrail, 1f, Geo.FLAG_DETAIL);
                    wallStrip(gb, a, b, -hw + 0.3f, 0.35f, 0.8f, lib.guardrail, 1f, Geo.FLAG_DETAIL);
                }
                if (bridge) bridgePiece(ch, gb, a, b, hw);
                if ((cls == RoadClass.COUNTRY || cls == RoadClass.TOWN) && !bridge) poles(ch, gb, a, b, hw);
            }
        }
        // cut-and-fill verges run the whole piece (through junction areas too, under the junction surface)
    }

    /** Stop line / zebra strip at a junction mouth (atA: the mouth at node a). */
    private void mouthStrip(GeoBuilder gb, float a, float b, boolean atA, float hw, RoadClass cls) {
        RoadNode n = net.nodes.get(atA ? e.a : e.b);
        float m = net.mouth(e, n);
        boolean painted = n.kind == RoadNode.Kind.CITY || givesWay(n);
        int tex = painted ? mouthTex(cls) : -1;
        if (tex < 0) {
            strip(gb, a, b, -hw, hw, 0.04f, lib.asphalt, ROAD_BIAS, 0, 0.5f, (float) Math.floor(a / 64f) * 64f);
            return;
        }
        float start = atA ? e.clearA : e.len - e.clearB;   // junction-side end of the mouth
        at(a, -hw, 0.04f, 0);
        at(a, hw, 0.04f, 1);
        at(b, hw, 0.04f, 2);
        at(b, -hw, 0.04f, 3);
        // arriving traffic keeps left: at node b it's on the + side, at node a on the - side
        float u0 = atA ? 1f : 0f, u1 = atA ? 0f : 1f;
        float va = 1f - Math.abs(a - start) / m, vb = 1f - Math.abs(b - start) / m;
        uv[0] = u0; uv[1] = va;
        uv[2] = u1; uv[3] = va;
        uv[4] = u1; uv[5] = vb;
        uv[6] = u0; uv[7] = vb;
        float lt = GeoBuilder.sunLight(0, 1, 0);
        l[0] = l[1] = l[2] = l[3] = lt;
        gb.quad(p, uv, l, tex, layer(), sortFlags(), bias(ROAD_BIAS), true);
    }

    /** True if traffic on this edge gives way at node n (a higher-priority road meets it). */
    private boolean givesWay(RoadNode n) {
        if (n.kind != RoadNode.Kind.JUNCTION) return false;
        int mine = e.cls.priority, best = 0;
        for (int id : n.edges) if (id != e.id) best = Math.max(best, net.edges.get(id).cls.priority);
        return best > mine;
    }

    /** The slip-road piece still overlaps the freeway it branches from. */
    private boolean rampOverlap() {
        float mx = (e.x[pi] + e.x[pi + 1]) * 0.5f, mz = (e.z[pi] + e.z[pi + 1]) * 0.5f;
        float d = net.classDistance(mx, mz, RoadClass.FREEWAY, 60f);
        return d < RoadClass.FREEWAY.halfWidth() + e.cls.halfWidth() - 0.5f;
    }

    /** A slip road runs right beside this freeway edge (skip the guardrail there). */
    private boolean rampAlongside(float a, float b, float off) {
        at((a + b) * 0.5f, off, 0f, 0);
        return net.classDistance(p[0], p[2], RoadClass.RAMP, 30f) < RoadClass.RAMP.halfWidth() + 2.5f;
    }

    // ------------------------------------------------------------------ cut-and-fill verges

    private static final float[] VERGE = {0f, 0.8f, 2.6f, 5.5f, RoadNetwork.CORRIDOR_WIDTH};

    private void corridor(GeoBuilder gb, float s0, float s1, float hw, boolean crowded) {
        int nsub = Math.max(1, (int) Math.ceil((s1 - s0) / 3.4f));
        for (int k = 0; k < nsub; k++) {
            float sa = s0 + (s1 - s0) * k / nsub, sb = s0 + (s1 - s0) * (k + 1) / nsub;
            for (int side = -1; side <= 1; side += 2) {
                for (int q = 0; q < VERGE.length - 1; q++) {
                    float o0 = (hw + VERGE[q]) * side, o1 = (hw + VERGE[q + 1]) * side;
                    vergePoint(sa, o0, q == 0, 0);
                    vergePoint(sa, o1, false, 1);
                    vergePoint(sb, o1, false, 2);
                    vergePoint(sb, o0, q == 0, 3);
                    // never lay grass over another road or a junction
                    if (crowded && (net.onAsphalt(p[0], p[2], 0.05f, e.id) || net.onAsphalt(p[3], p[5], 0.05f, e.id)
                            || net.onAsphalt(p[6], p[8], 0.05f, e.id) || net.onAsphalt(p[9], p[11], 0.05f, e.id)
                            || net.onAsphalt((p[0] + p[6]) * 0.5f, (p[2] + p[8]) * 0.5f, 0.05f, e.id))) continue;
                    float dy = Math.abs(p[4] - p[1]) / Math.abs(o1 - o0);
                    int tex = dy > 0.9f ? lib.rock : lib.grass;
                    for (int c = 0; c < 4; c++) { uv[c * 2] = p[c * 3] / 2f; uv[c * 2 + 1] = p[c * 3 + 2] / 2f; }
                    // light from the bank slope, facing away from the road
                    float slope = (p[4] - p[1]) / Math.abs(o1 - o0);
                    float lt = Math.max(0.55f, GeoBuilder.sunLight(0, 1, 0) - slope * 0.25f);
                    l[0] = l[1] = l[2] = l[3] = lt;
                    gb.quad(p, uv, l, tex, L_OBJECT, Geo.FLAG_SORT_FAR, VERGE_BIAS, true);
                }
            }
        }
    }

    /** Verge vertex: on the road edge it takes the road's height, further out the shaped terrain. */
    private void vergePoint(float s, float off, boolean edge, int k) {
        at(s, off, 0.0f, k);
        if (!edge) p[k * 3 + 1] = terrain.height(p[k * 3], p[k * 3 + 2]);
        else p[k * 3 + 1] -= 0.02f;
    }

    // ------------------------------------------------------------------ junctions

    private final float[] u = new float[2];

    /**
     * One polygon where roads meet: the trimmed road ends' corners in angular order, joined by
     * curved kerb returns (straight corners in the city grid), filled from the centre.
     */
    private void junction(GeoBuilder gb, RoadNode n) {
        float[] ring = n.ring;
        if (ring == null) return;
        boolean cityNode = n.kind == RoadNode.Kind.CITY;
        int m = ring.length / 3;
        float lift = 0.04f;
        float cy = cityNode ? terrain.height(n.x, n.z) : n.y + lift;
        int lay = cityNode ? L_GROUND : L_OBJECT;
        int flags = cityNode ? 0 : Geo.FLAG_SORT_FAR;
        float bias = cityNode ? 0f : ROAD_BIAS;
        float lt = GeoBuilder.sunLight(0, 1, 0);
        if (cityNode) {
            for (int q = 0; q < m; q++) {
                int r = (q + 1) % m;
                tri(gb, n.x, cy, n.z, ring[q * 3], ring[q * 3 + 1] + lift, ring[q * 3 + 2], ring[r * 3], ring[r * 3 + 1] + lift, ring[r * 3 + 2],
                        lay, flags, bias, lt, true);
            }
        } else {
            // ear clipping: the outline may be concave (kerb returns curve in)
            int[] idx = new int[m];
            for (int q = 0; q < m; q++) idx[q] = q;
            float area = 0f;
            for (int q = 0; q < m; q++) {
                int r = (q + 1) % m;
                area += ring[q * 3] * ring[r * 3 + 2] - ring[r * 3] * ring[q * 3 + 2];
            }
            float sgn = area >= 0f ? 1f : -1f;
            int cnt = m, guard = 0;
            while (cnt > 3 && guard++ < m * m) {
                boolean clipped = false;
                for (int q = 0; q < cnt; q++) {
                    int a0 = idx[(q + cnt - 1) % cnt], a1 = idx[q], a2 = idx[(q + 1) % cnt];
                    float cr = cross(ring, a0, a1, a2) * sgn;
                    if (cr <= 1e-4f) continue;
                    boolean inside = false;
                    for (int t = 0; t < cnt && !inside; t++) {
                        int v = idx[t];
                        if (v == a0 || v == a1 || v == a2) continue;
                        if (inTri(ring, v, a0, a1, a2, sgn)) inside = true;
                    }
                    if (inside) continue;
                    earTri(gb, ring, a0, a1, a2, lift, lay, flags, bias, lt);
                    System.arraycopy(idx, q + 1, idx, q, cnt - q - 1);
                    cnt--;
                    clipped = true;
                    break;
                }
                if (!clipped) break;
            }
            if (cnt == 3) earTri(gb, ring, idx[0], idx[1], idx[2], lift, lay, flags, bias, lt);
            else if (cnt > 3) {
                // degenerate leftovers: fall back to a fan from the centre
                for (int q = 0; q < cnt; q++) {
                    int a1 = idx[q], a2 = idx[(q + 1) % cnt];
                    tri(gb, n.x, cy, n.z, ring[a1 * 3], ring[a1 * 3 + 1] + lift, ring[a1 * 3 + 2], ring[a2 * 3], ring[a2 * 3 + 1] + lift, ring[a2 * 3 + 2],
                            lay, flags, bias, lt, false);
                }
            }
        }
        if (!cityNode) apron(gb, n);
    }

    private static float cross(float[] r, int a, int b, int c) {
        return (r[b * 3] - r[a * 3]) * (r[c * 3 + 2] - r[a * 3 + 2]) - (r[b * 3 + 2] - r[a * 3 + 2]) * (r[c * 3] - r[a * 3]);
    }

    private static boolean inTri(float[] r, int p, int a, int b, int c, float sgn) {
        float px = r[p * 3], pz = r[p * 3 + 2];
        float d1 = ((r[b * 3] - r[a * 3]) * (pz - r[a * 3 + 2]) - (r[b * 3 + 2] - r[a * 3 + 2]) * (px - r[a * 3])) * sgn;
        float d2 = ((r[c * 3] - r[b * 3]) * (pz - r[b * 3 + 2]) - (r[c * 3 + 2] - r[b * 3 + 2]) * (px - r[b * 3])) * sgn;
        float d3 = ((r[a * 3] - r[c * 3]) * (pz - r[c * 3 + 2]) - (r[a * 3 + 2] - r[c * 3 + 2]) * (px - r[c * 3])) * sgn;
        return d1 >= 0f && d2 >= 0f && d3 >= 0f;
    }

    private void earTri(GeoBuilder gb, float[] r, int a, int b, int c, float lift, int lay, int flags, float bias, float lt) {
        tri(gb, r[a * 3], r[a * 3 + 1] + lift, r[a * 3 + 2], r[b * 3], r[b * 3 + 1] + lift, r[b * 3 + 2], r[c * 3], r[c * 3 + 1] + lift, r[c * 3 + 2],
                lay, flags, bias, lt, false);
    }

    /** Grass skirt outside the kerb returns, from the junction edge down to the terrain. */
    private void apron(GeoBuilder gb, RoadNode n) {
        float[] r = n.ring;
        int m = r.length / 3;
        for (int q = 0; q < m; q++) {
            if (!n.kerb[q]) continue;
            int j = (q + 1) % m;
            float ax = r[q * 3], az = r[q * 3 + 2], bx = r[j * 3], bz = r[j * 3 + 2];
            float dx = bx - ax, dz = bz - az, len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len < 0.05f) continue;
            // outward normal: away from the node centre
            float nx = dz / len, nz = -dx / len;
            float mx = (ax + bx) * 0.5f - n.x, mz = (az + bz) * 0.5f - n.z;
            if (nx * mx + nz * mz < 0f) { nx = -nx; nz = -nz; }
            for (int band = 0; band < 2; band++) {
                float o0 = band == 0 ? 0f : 1.5f, o1 = band == 0 ? 1.5f : 4f;
                p[0] = ax + nx * o0; p[2] = az + nz * o0;
                p[3] = bx + nx * o0; p[5] = bz + nz * o0;
                p[6] = bx + nx * o1; p[8] = bz + nz * o1;
                p[9] = ax + nx * o1; p[11] = az + nz * o1;
                if (net.onAsphalt(p[6], p[8], 0.05f, -2) && net.onAsphalt(p[9], p[11], 0.05f, -2)) continue;
                p[1] = band == 0 ? r[q * 3 + 1] + 0.02f : terrain.height(p[0], p[2]);
                p[4] = band == 0 ? r[j * 3 + 1] + 0.02f : terrain.height(p[3], p[5]);
                p[7] = terrain.height(p[6], p[8]);
                p[10] = terrain.height(p[9], p[11]);
                for (int c = 0; c < 4; c++) { uv[c * 2] = p[c * 3] / 2f; uv[c * 2 + 1] = p[c * 3 + 2] / 2f; }
                l[0] = l[1] = l[2] = l[3] = GeoBuilder.sunLight(0, 1, 0);
                gb.quad(p, uv, l, lib.grass, L_OBJECT, Geo.FLAG_SORT_FAR, VERGE_BIAS, true);
            }
        }
    }

    /** Fan triangle; split into small pieces outside the city so it sorts finely with the verges. */
    private void tri(GeoBuilder gb, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                     int lay, int flags, float bias, float lt, boolean cityNode) {
        float e1 = (float) Math.hypot(bx - ax, bz - az), e2 = (float) Math.hypot(cx - ax, cz - az), e3 = (float) Math.hypot(cx - bx, cz - bz);
        int n = Math.max(1, (int) Math.ceil(Math.max(e1, Math.max(e2, e3)) / (cityNode ? 6f : 4f)));
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n - i; j++) {
                emitTri(gb, n, i, j, i + 1, j, i, j + 1, ax, ay, az, bx, by, bz, cx, cy, cz, lay, flags, bias, lt, cityNode);
                if (i + j < n - 1)
                    emitTri(gb, n, i + 1, j, i + 1, j + 1, i, j + 1, ax, ay, az, bx, by, bz, cx, cy, cz, lay, flags, bias, lt, cityNode);
            }
    }

    private void emitTri(GeoBuilder gb, int n, int i0, int j0, int i1, int j1, int i2, int j2,
                         float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                         int lay, int flags, float bias, float lt, boolean cityNode) {
        int[][] ij = {{i0, j0}, {i1, j1}, {i2, j2}};
        for (int v = 0; v < 3; v++) {
            float fb = ij[v][0] / (float) n, fc = ij[v][1] / (float) n, fa = 1 - fb - fc;
            float x = ax * fa + bx * fb + cx * fc, z = az * fa + bz * fb + cz * fc;
            p3[v * 3] = x;
            p3[v * 3 + 1] = cityNode ? terrain.height(x, z) : ay * fa + by * fb + cy * fc;
            p3[v * 3 + 2] = z;
            uv3[v * 2] = x / 2f;
            uv3[v * 2 + 1] = z / 2f;
            l3[v] = lt;
        }
        gb.quad(p3, uv3, l3, lib.asphalt, lay, flags | Geo.FLAG_NO_SUBDIV, bias, true);
    }


    // ------------------------------------------------------------------ bridges

    /** Deck thickness under the road surface. */
    private static final float DECK = 1.3f;

    /**
     * One band of a bridge deck as a slab: offsets o0..o1 across the road from a to b, its top
     * the road surface (lane texture across the band, or plain asphalt), 1.3 m of concrete below.
     */
    private void deckSlab(Chunk ch, float a, float b, float o0, float o1, int laneTex, float laneBase, float vBase) {
        at(a, o0, 0.04f, 0);
        at(a, o1, 0.04f, 1);
        at(b, o1, 0.04f, 2);
        at(b, o0, 0.04f, 3);
        Sectors.Slab sl = slab(0f, -DECK);
        float ax = p[0], az = p[2];
        float uxr = p[3] - p[0], uzr = p[5] - p[2], ul = (float) Math.sqrt(uxr * uxr + uzr * uzr);
        float vxr = p[9] - p[0], vzr = p[11] - p[2], vl = (float) Math.sqrt(vxr * vxr + vzr * vzr);
        sl.ox = ax; sl.oz = az;
        if (ul > 1e-4f) { sl.ux = uxr / ul; sl.uz = uzr / ul; }
        if (vl > 1e-4f) { sl.vx = vxr / vl; sl.vz = vzr / vl; }
        if (laneTex >= 0) {
            sl.topTex = laneTex;
            sl.us = Math.max(0.05f, ul);
            sl.clampU = true;
            sl.vs = 20f;
            sl.vOff = (a - laneBase) / 20f;
        } else {
            sl.topTex = lib.asphalt;
            sl.us = 2f; sl.vs = 2f;
            sl.vOff = (a - vBase) * 0.5f;
        }
        ch.sectors.slabs.add(sl);
    }

    /** Slab over the quad in p[0..11] (corners 0..3), top lifted by topLift, bottom at drop below the corners. */
    private Sectors.Slab slab(float topLift, float drop) {
        Sectors.Slab sl = new Sectors.Slab(new float[]{p[0], p[2], p[3], p[5], p[6], p[8], p[9], p[11]});
        float[] t = Sectors.Slab.plane(p[0], p[1] + topLift, p[2], p[3], p[4] + topLift, p[5], p[6], p[7] + topLift, p[8]);
        float[] u = Sectors.Slab.plane(p[0], p[1] + drop, p[2], p[3], p[4] + drop, p[5], p[6], p[7] + drop, p[8]);
        sl.ta = t[0]; sl.tb = t[1]; sl.tc = t[2];
        sl.ba = u[0]; sl.bb = u[1]; sl.bc = u[2];
        sl.topTex = lib.concrete; sl.sideTex = lib.concrete; sl.botTex = lib.concrete;
        sl.lightTop = GeoBuilder.sunLight(0, 1, 0);
        sl.lightSide = 0.8f;
        sl.lightBot = 0.5f;
        return sl;
    }

    /** Parapets along both edges and piers down to the ground, all slabs. */
    private void bridgePiece(Chunk ch, GeoBuilder gb, float a, float b, float hw) {
        for (int side = -1; side <= 1; side += 2) {
            float o0 = side * hw, o1 = side * (hw + 0.3f);
            at(a, Math.min(o0, o1), 0f, 0);
            at(a, Math.max(o0, o1), 0f, 1);
            at(b, Math.max(o0, o1), 0f, 2);
            at(b, Math.min(o0, o1), 0f, 3);
            Sectors.Slab sl = slab(1.0f, -DECK);
            sl.us = sl.vs = 1f;
            ch.sectors.slabs.add(sl);
        }
        float first = (float) Math.ceil(a / 18f) * 18f;
        for (float s = first; s < b; s += 18f) {
            e.point(s, tmp);
            float px = tmp[0], pz = tmp[1], top = tmp[2] - DECK;
            if (net.motorwayClearance(px, pz, 6f) < 3f) continue;
            float g = terrain.height(px, pz);
            if (top - g < 1f) continue;
            float r = 0.8f;
            Sectors.Slab pier = new Sectors.Slab(new float[]{px - r, pz - r, px + r, pz - r, px + r, pz + r, px - r, pz + r});
            pier.ta = top; pier.ba = g - 0.5f;
            pier.topTex = pier.sideTex = pier.botTex = lib.concrete;
            pier.lightSide = 0.75f;
            ch.sectors.slabs.add(pier);
            ch.boxes.add(new Box(px - r, g - 1f, pz - r, px + r, top, pz + r));
        }
    }

    // ------------------------------------------------------------------ utility poles

    private void poles(Chunk ch, GeoBuilder gb, float a, float b, float hw) {
        float first = (float) Math.ceil(a / POLE_STEP) * POLE_STEP;
        for (float s = first; s < b; s += POLE_STEP) {
            if (s < e.clearA + 8f || s > e.len - e.clearB - 8f) continue;
            float off = hw + 1.4f;
            polePos(s, off, tmp);
            float x = tmp[0], z = tmp[1];
            if (net.roadClearance(x, z, 4f) < 0.8f) continue;     // never on another road (e.g. a farm lane)
            float y = terrain.height(x, z);
            float yaw = (float) Math.atan2(tmp[3], tmp[4]);
            ch.sprite(Chunk.SPRITE_UTILITY, 0, x, y, z, yaw + (float) (Math.PI / 2));
            ch.boxes.add(new Box(x - 0.18f, y - 1f, z - 0.18f, x + 0.18f, y + 10.5f, z + 0.18f));
            float ps = s - POLE_STEP;
            if (ps < e.clearA + 8f) continue;
            float[] q = new float[5];
            polePos(ps, off, q);
            if (net.roadClearance(q[0], q[1], 4f) < 0.8f) continue;
            float qy = terrain.height(q[0], q[1]);
            float sx = tmp[4], sz = -tmp[3];
            for (float w : new float[]{-0.6f, 0f, 0.6f})
                wire(gb, q[0] + sx * w, qy + 10.02f, q[1] + sz * w, x + sx * w, y + 10.02f, z + sz * w);
        }
    }

    private void polePos(float s, float off, float[] out) {
        e.point(s, out);
        float tx = out[3], tz = out[4];
        out[0] += tz * off;
        out[1] += -tx * off;
    }

    private void wire(GeoBuilder gb, float ax, float ay, float az, float bx, float by, float bz) {
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f - 0.6f, mz = (az + bz) * 0.5f;
        float th = 0.05f;
        float[] lt = {0.5f, 0.5f, 0.5f, 0.5f};
        float[] t = {0, 1, 1, 1, 1, 0, 0, 0};
        gb.quad(new float[]{ax, ay - th, az, mx, my - th, mz, mx, my + th, mz, ax, ay + th, az}, t, lt, lib.pole, L_OBJECT,
                Geo.FLAG_DETAIL | Geo.FLAG_NO_SUBDIV, -0.2f, true);
        gb.quad(new float[]{mx, my - th, mz, bx, by - th, bz, bx, by + th, bz, mx, my + th, mz}, t, lt, lib.pole, L_OBJECT,
                Geo.FLAG_DETAIL | Geo.FLAG_NO_SUBDIV, -0.2f, true);
    }
}
