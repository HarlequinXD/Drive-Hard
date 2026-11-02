package com.harlequinxd.drivehard.core.road;

import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.GeoBuilder;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Builds the visible roads outside the city from the spline network: the paved ribbon split into
 * lane-wide strips that follow each road's profile, lane markings and dashes, stop lines where a
 * road gives way, freeway median barriers and guardrails, bridge decks with parapets and piers,
 * and utility poles with wires along country roads.
 *
 * Roads share the object layer with the terrain and sort by their farthest corner with a lead
 * over the ground they lie on (see Geo.FLAG_SORT_FAR), so hills still hide them.
 */
public final class RoadMesh {
    private static final float ROAD_BIAS = -3.6f, MARK_BIAS = -4.0f, WALL_BIAS = -4.2f;
    private static final float POLE_STEP = 45f;

    private final RoadNetwork net;
    private final TextureLibrary lib;
    private final Terrain terrain;

    private final float[] nA = new float[2], nB = new float[2];
    private final float[] p = new float[12], uv = new float[8], l = new float[4];
    private final float[] tmp = new float[5];

    public RoadMesh(RoadNetwork net, TextureLibrary lib, Terrain terrain) {
        this.net = net;
        this.lib = lib;
        this.terrain = terrain;
    }

    public void generate(Chunk ch, GeoBuilder gb, float x0, float z0, float x1, float z1) {
        gb.begin(false);
        net.forEachPieceIn(x0, z0, x1, z1, (e, i) -> piece(ch, gb, e, i));
        gb.end();
    }

    // ------------------------------------------------------------------ geometry along a piece

    private RoadEdge e;
    private int pi;

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

    /** Writes the point at distance s, lateral offset off (left of a→b), height + lift, into p[k*3]. */
    private void at(float s, float off, float lift, int k) {
        float s0 = e.s[pi], s1 = e.s[pi + 1];
        float t = s1 > s0 ? (s - s0) / (s1 - s0) : 0f;
        float bx = e.x[pi] + (e.x[pi + 1] - e.x[pi]) * t, bz = e.z[pi] + (e.z[pi + 1] - e.z[pi]) * t;
        float nx = nA[0] + (nB[0] - nA[0]) * t, nz = nA[1] + (nB[1] - nA[1]) * t;
        p[k * 3] = bx + nx * off;
        p[k * 3 + 1] = e.y[pi] + (e.y[pi + 1] - e.y[pi]) * t + lift;
        p[k * 3 + 2] = bz + nz * off;
    }

    /** Flat strip between offsets o0..o1 from s a..b. */
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
        gb.quad(p, uv, l, tex, L_OBJECT, Geo.FLAG_SORT_FAR | flags, bias, true);
    }

    /**
     * Cross-section bands {centre-side offset, kerb-side offset, texture (-1 plain asphalt), symmetric}.
     * Painted lines live in the lane textures, so markings sort exactly with the road surface.
     */
    private float[][] bands(RoadClass c) {
        switch (c) {
            case COUNTRY:
                return new float[][]{{0f, 3.1f, lib.laneCountry, 0}, {3.1f, c.halfWidth(), -1, 0}};
            case HIGHWAY:
                return new float[][]{{0f, 0.3f, lib.laneHwMedian, 1}, {0.3f, 3.55f, lib.laneHwInner, 0},
                        {3.55f, 6.8f, lib.laneHwOuter, 0}, {6.8f, c.halfWidth(), -1, 0}};
            case FREEWAY:
                return new float[][]{{0f, 1.4f, -1, 1}, {1.4f, 4.9f, lib.laneFwInner, 0},
                        {4.9f, 8.4f, lib.laneFwOuter, 0}, {8.4f, c.halfWidth(), -1, 0}};
            default:   // RAMP
                return new float[][]{{0f, 1.875f, lib.laneRamp, 1}, {1.875f, c.halfWidth(), -1, 0}};
        }
    }

    /** Lane strip: u runs 0 at the centre-side offset oc to 1 at the kerb-side offset ok; v repeats every 20 m. */
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
        gb.quad(p, uv, l, tex, L_OBJECT, Geo.FLAG_SORT_FAR, ROAD_BIAS, true);
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

    private void piece(Chunk ch, GeoBuilder gb, RoadEdge edge, int i) {
        if (edge.cls.city) return;
        e = edge;
        pi = i;
        RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
        float s0 = e.s[i], s1 = e.s[i + 1];
        float c0 = na.kind == RoadNode.Kind.CITY ? e.clearA : 0f;
        float c1 = nb.kind == RoadNode.Kind.CITY ? e.len - e.clearB : e.len;
        float a = Math.max(s0, c0), b = Math.min(s1, c1);
        if (b - a < 0.05f) return;
        normal(e, i, nA);
        normal(e, i + 1, nB);
        RoadClass cls = e.cls;
        float hw = cls.halfWidth();
        float vBase = (float) Math.floor(s0 / 64f) * 64f;
        int nsub = Math.max(1, (int) Math.ceil((b - a) / 3.4f));
        boolean bridge = e.bridge[i];
        float laneBase = (float) Math.floor(s0 / 320f) * 320f;
        float[][] bands = bands(cls);
        for (int k = 0; k < nsub; k++) {
            float sa = a + (b - a) * k / nsub, sb = a + (b - a) * (k + 1) / nsub;
            for (float[] bd : bands) {
                int tex = (int) bd[2];
                boolean plain = tex < 0;
                if (bd[3] > 0f) {   // symmetric band across the centreline
                    if (plain) strip(gb, sa, sb, -bd[1], bd[1], 0.04f, lib.asphalt, ROAD_BIAS, 0, 0.5f, vBase);
                    else laneStrip(gb, sa, sb, -bd[1], bd[1], tex, laneBase);
                    continue;
                }
                for (int side = -1; side <= 1; side += 2) {
                    float oc = bd[0] * side, ok = bd[1] * side;
                    if (plain) strip(gb, sa, sb, Math.min(oc, ok), Math.max(oc, ok), 0.04f, lib.asphalt, ROAD_BIAS, 0, 0.5f, vBase);
                    else laneStrip(gb, sa, sb, oc, ok, tex, laneBase);
                }
            }
        }
        markings(gb, a, b, vBase);
        if (cls == RoadClass.FREEWAY)
            wallStrip(gb, a, b, 0f, 0f, 0.85f, lib.concrete, 0.95f, 0);
        if (cls == RoadClass.FREEWAY || cls == RoadClass.RAMP || (bridge && cls == RoadClass.HIGHWAY)) {
            if (!bridge) {
                wallStrip(gb, a, b, hw - 0.3f, 0.35f, 0.8f, lib.guardrail, 1f, Geo.FLAG_DETAIL);
                wallStrip(gb, a, b, -hw + 0.3f, 0.35f, 0.8f, lib.guardrail, 1f, Geo.FLAG_DETAIL);
            }
        }
        if (bridge) bridgePiece(ch, gb, a, b, hw);
        if (cls == RoadClass.COUNTRY && !bridge) poles(ch, gb, a, b, hw);
    }

    // ------------------------------------------------------------------ markings

    private void markings(GeoBuilder gb, float a, float b, float vBase) {
        RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
        float ma = na.kind == RoadNode.Kind.SPLIT ? 0f : e.clearA, mb = nb.kind == RoadNode.Kind.SPLIT ? e.len : e.len - e.clearB;
        float ca = Math.max(a, ma), cb = Math.min(b, mb);
        RoadClass cls = e.cls;
        int white = lib.paintWhite;
        // stop lines where this road gives way at a junction
        float inner = cls.oneWay() ? cls.laneW * 0.5f : cls.median + cls.lanes * cls.laneW;
        if (na.kind == RoadNode.Kind.JUNCTION && givesWay(na)) {
            float s = e.clearA;
            if (s >= a && s + 0.45f <= b) strip(gb, s, s + 0.45f, -inner, 0f, 0.06f, white, MARK_BIAS, Geo.FLAG_DETAIL, 0.5f, 0f);
        }
        if (nb.kind == RoadNode.Kind.JUNCTION && givesWay(nb) && !e.oneWay) {
            float s = e.len - e.clearB;
            if (s - 0.45f >= a && s <= b) strip(gb, s - 0.45f, s, 0f, inner, 0.06f, white, MARK_BIAS, Geo.FLAG_DETAIL, 0.5f, 0f);
        }
    }

    /** True if traffic on this edge must give way at node n (a higher-priority road meets it). */
    private boolean givesWay(RoadNode n) {
        int mine = e.cls.priority, best = 0;
        for (int id : n.edges) if (id != e.id) best = Math.max(best, net.edges.get(id).cls.priority);
        return best > mine;
    }

    // ------------------------------------------------------------------ bridges

    private void bridgePiece(Chunk ch, GeoBuilder gb, float a, float b, float hw) {
        // parapets with the deck fascia below
        wallStrip(gb, a, b, hw + 0.05f, -1.3f, 1.0f, lib.concrete, 1f, 0);
        wallStrip(gb, a, b, -hw - 0.05f, -1.3f, 1.0f, lib.concrete, 1f, 0);
        // underside
        at(a, -hw, -1.3f, 0);
        at(a, hw, -1.3f, 1);
        at(b, hw, -1.3f, 2);
        at(b, -hw, -1.3f, 3);
        uv[0] = 0; uv[1] = 0; uv[2] = 2f * hw / 2f; uv[3] = 0; uv[4] = 2f * hw / 2f; uv[5] = (b - a) / 2f; uv[6] = 0; uv[7] = (b - a) / 2f;
        l[0] = l[1] = l[2] = l[3] = 0.55f;
        gb.quad(p, uv, l, lib.concrete, L_OBJECT, 0, 0f, true);
        // piers every 18 m, never standing on the motorway below
        float first = (float) Math.ceil(a / 18f) * 18f;
        for (float s = first; s < b; s += 18f) {
            e.point(s, tmp);
            float px = tmp[0], pz = tmp[1], top = tmp[2] - 1.3f;
            if (net.motorwayClearance(px, pz, 6f) < 3f) continue;
            float g = terrain.height(px, pz);
            if (top - g < 1f) continue;
            float r = 0.8f;
            gb.box(px - r, g - 0.5f, pz - r, px + r, top, pz + r, lib.concrete, -1, 0.5f, 0.5f, L_OBJECT, 0f, 0);
            ch.boxes.add(new Box(px - r, g - 1f, pz - r, px + r, top, pz + r));
        }
    }

    // ------------------------------------------------------------------ utility poles

    private void poles(Chunk ch, GeoBuilder gb, float a, float b, float hw) {
        float first = (float) Math.ceil(a / POLE_STEP) * POLE_STEP;
        for (float s = first; s < b; s += POLE_STEP) {
            if (s < e.clearA + 6f || s > e.len - e.clearB - 6f) continue;
            float off = hw + 1.1f;
            polePos(s, off, tmp);
            float x = tmp[0], z = tmp[1];
            float y = terrain.height(x, z);
            float yaw = (float) Math.atan2(tmp[3], tmp[4]);
            ch.sprite(Chunk.SPRITE_UTILITY, 0, x, y, z, yaw + (float) (Math.PI / 2));
            ch.boxes.add(new Box(x - 0.18f, y - 1f, z - 0.18f, x + 0.18f, y + 10.5f, z + 0.18f));
            // wires back to the previous pole
            float ps = s - POLE_STEP;
            if (ps < e.clearA + 6f) continue;
            float[] q = new float[5];
            polePos(ps, off, q);
            float qy = terrain.height(q[0], q[1]);
            float sx = tmp[4], sz = -tmp[3];   // left normal for wire spread
            for (float w : new float[]{-0.6f, 0f, 0.6f}) {
                wire(gb, q[0] + sx * w, qy + 10.02f, q[1] + sz * w, x + sx * w, y + 10.02f, z + sz * w);
            }
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
