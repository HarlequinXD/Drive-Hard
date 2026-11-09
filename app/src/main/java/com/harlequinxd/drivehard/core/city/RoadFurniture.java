package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.RoadNode;
import com.harlequinxd.drivehard.core.road.Town;
import com.harlequinxd.drivehard.core.tex.Noise;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * What lines a Japanese country road besides its poles: guard rails where it runs along an
 * embankment, concrete retaining walls at the foot of deep cuttings, blue direction boards
 * before junctions, speed limit discs, curve mirrors at blind junctions in towns and villages,
 * and a bus stop at every village. Built chunk by chunk (pieces and nodes whose middle is in
 * the chunk).
 */
final class RoadFurniture {
    private final City city;
    private final RoadNetwork net;
    private final Terrain terrain;
    private final TextureLibrary lib;
    private final Chunk ch;
    private final GeoBuilder gb;
    private final float[] p = new float[12], uv = new float[8], l = {1f, 1f, 1f, 1f}, q = new float[5];

    RoadFurniture(City city, Chunk ch, GeoBuilder gb) {
        this.city = city;
        this.net = city.roads;
        this.terrain = city.terrain;
        this.lib = city.lib;
        this.ch = ch;
        this.gb = gb;
    }

    void build(int cx, int cz, float x0, float z0, float x1, float z1) {
        gb.begin(false);
        net.forEachPieceIn(x0, z0, x1, z1, this::piece);
        for (RoadNode n : net.nodesInChunk(cx, cz)) node(n);
        gb.end();
    }

    // ------------------------------------------------------------------ along the road

    private void piece(RoadEdge e, int i) {
        if (e.cls != RoadClass.COUNTRY && e.cls != RoadClass.HIGHWAY) return;
        if (e.bridge[i]) return;
        float sa = e.s[i], sb = e.s[i + 1], sm = (sa + sb) * 0.5f;
        if (sm < e.clearA + 14f || sm > e.len - e.clearB - 14f) return;
        float ax = e.x[i], az = e.z[i], bx = e.x[i + 1], bz = e.z[i + 1];
        float len = (float) Math.hypot(bx - ax, bz - az);
        if (len < 0.5f) return;
        float tx = (bx - ax) / len, tz = (bz - az) / len, nx = tz, nz = -tx;   // left of a->b
        // offsets follow the bend: each end is pushed out along the normal shared with the next piece
        float[] na = vertexNormal(e, i), nb = vertexNormal(e, i + 1);
        float mx = (ax + bx) * 0.5f, mz = (az + bz) * 0.5f, my = (e.y[i] + e.y[i + 1]) * 0.5f;
        if (city.urban(mx, mz)) return;
        float hw = e.cls.halfWidth();
        for (int side = -1; side <= 1; side += 2) {
            float ox = nx * side, oz = nz * side;
            float drop = my - terrain.natural(mx + ox * (hw + 8f), mz + oz * (hw + 8f));
            float rise = terrain.natural(mx + ox * (hw + 11f), mz + oz * (hw + 11f)) - my;
            if (drop > 2.2f) {
                float o = side * (hw - 0.25f);
                guardRail(ax + na[0] * o, az + na[1] * o, bx + nb[0] * o, bz + nb[1] * o, len);
            } else if (rise > 3.2f) {
                float off = hw + 7f, o = side * off;
                if (net.roadClearance(mx + ox * off, mz + oz * off, 4f) < 1.5f) continue;
                retainingWall(ax + na[0] * o, az + na[1] * o, bx + nb[0] * o, bz + nb[1] * o, Math.min(2.6f, 0.9f + rise * 0.15f), len);
            }
        }
        // a speed limit disc on the left, 80 m out of each junction, facing the traffic leaving it
        for (int end = 0; end < 2; end++) {
            if (e.len < 400f) break;
            float target = end == 0 ? e.clearA + 80f : e.len - e.clearB - 80f;
            if (target < sa || target >= sb) continue;
            int dir = end == 0 ? 1 : -1;
            e.point(target, q);
            float trx = q[3] * dir, trz = q[4] * dir, lx = trz, lz = -trx;
            float x = q[0] + lx * (hw + 1.0f), z = q[1] + lz * (hw + 1.0f);
            if (net.roadClearance(x, z, 4f) < 0.5f) continue;
            float y = terrain.height(x, z);
            pole(x, y, z, 2.6f, lib.pole);
            disc(x - trx * 0.06f, y + 2.25f, z - trz * 0.06f, -trx, -trz, 0.6f, e.cls == RoadClass.HIGHWAY ? lib.speed50 : lib.speed40);
        }
    }

    /** Unit left normal at sample i of e, averaged over the pieces either side (mitred joins). */
    private static float[] vertexNormal(RoadEdge e, int i) {
        int a = Math.max(0, i - 1), b = Math.min(e.count() - 1, i + 1);
        float tx = 0f, tz = 0f;
        if (i > 0) { float dx = e.x[i] - e.x[a], dz = e.z[i] - e.z[a], d = (float) Math.hypot(dx, dz); if (d > 1e-4f) { tx += dx / d; tz += dz / d; } }
        if (i < e.count() - 1) { float dx = e.x[b] - e.x[i], dz = e.z[b] - e.z[i], d = (float) Math.hypot(dx, dz); if (d > 1e-4f) { tx += dx / d; tz += dz / d; } }
        float d = (float) Math.hypot(tx, tz);
        if (d < 1e-4f) return new float[]{0f, 0f};
        return new float[]{tz / d, -tx / d};
    }

    /** W-beam rail on posts along the road edge where it runs on an embankment. */
    private void guardRail(float px0, float pz0, float px1, float pz1, float len) {
        float y0 = terrain.height(px0, pz0), y1 = terrain.height(px1, pz1);
        quad(px0, pz0, px1, pz1, y0 + 0.35f, y1 + 0.35f, y0 + 0.8f, y1 + 0.8f, lib.guardrail, len / 4f, 1f, Geo.FLAG_DETAIL);
        for (float s = 0f; s < len; s += 4f) {
            float t = s / len, x = px0 + (px1 - px0) * t, z = pz0 + (pz1 - pz0) * t, y = terrain.height(x, z);
            gb.box(x - 0.05f, y - 0.2f, z - 0.05f, x + 0.05f, y + 0.75f, z + 0.05f, lib.pole, lib.pole, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        }
        ch.boxes.add(new Box(Math.min(px0, px1) - 0.08f, Math.min(y0, y1) - 1f, Math.min(pz0, pz1) - 0.08f,
                Math.max(px0, px1) + 0.08f, Math.max(y0, y1) + 0.8f, Math.max(pz0, pz1) + 0.08f));
    }

    /** Concrete wall at the foot of a cutting, holding the bank back from the verge. */
    private void retainingWall(float ax, float az, float bx, float bz, float h, float len) {
        float ya = terrain.height(ax, az), yb = terrain.height(bx, bz);
        quad(ax, az, bx, bz, ya - 0.3f, yb - 0.3f, ya + h, yb + h, lib.concrete, len / 4f, (h + 0.3f) / 4f, 0);
        // the top of the wall
        ch.boxes.add(new Box(Math.min(ax, bx) - 0.15f, Math.min(ya, yb) - 1f, Math.min(az, bz) - 0.15f,
                Math.max(ax, bx) + 0.15f, Math.max(ya, yb) + h, Math.max(az, bz) + 0.15f));
    }

    // ------------------------------------------------------------------ at junctions

    private void node(RoadNode n) {
        if (n.dead || n.kind != RoadNode.Kind.JUNCTION || n.edges.length < 2) return;
        Town t = net.townOf(n);
        boolean inGrid = t != null && t.grid != null;
        int rural = 0;
        for (int id : n.edges) {
            RoadClass c = net.edges.get(id).cls;
            if (c == RoadClass.COUNTRY || c == RoadClass.HIGHWAY) rural++;
        }
        // direction boards on the main roads approaching a junction out in the country
        if (!inGrid && n.edges.length >= 3 && rural >= 1)
            for (int id : n.edges) {
                RoadEdge e = net.edges.get(id);
                if (e.cls != RoadClass.COUNTRY && e.cls != RoadClass.HIGHWAY) continue;
                float c = e.clear(n.id), d = c + 32f;
                if (e.len < d + 30f) continue;
                directionBoard(e, n, d);
            }
        // a bus stop at the heart of every settlement
        if (t != null && t.centre == n) busStop(n);
        // curve mirrors at blind junctions on town and village streets and lanes
        if (n.edges.length >= 3 && (inGrid || t != null) && Noise.rnd(n.id, 17, 991) < (inGrid ? 0.3f : 0.8f)) curveMirror(n);
    }

    /** Blue board on two posts, left of the traffic coming toward the junction, facing it. */
    private void directionBoard(RoadEdge e, RoadNode n, float d) {
        boolean fromA = e.a == n.id;
        e.point(fromA ? d : e.len - d, q);
        float ux = fromA ? q[3] : -q[3], uz = fromA ? q[4] : -q[4];       // away from the junction
        float trx = -ux, trz = -uz, lx = trz, lz = -trx;                   // travel toward it; its left
        float off = e.cls.halfWidth() + 1.6f;
        float x = q[0] + lx * off, z = q[1] + lz * off;
        if (net.roadClearance(x, z, 4f) < 1f) return;
        float y = terrain.height(x, z);
        float w = 2.8f, h = 1.4f, base = y + 2.3f;
        for (int k = -1; k <= 1; k += 2) pole(x + lx * k * w * 0.35f, terrain.height(x + lx * k * w * 0.35f, z + lz * k * w * 0.35f), z + lz * k * w * 0.35f, base - y + h, lib.pole);
        // the face looks back down the road at the oncoming traffic
        float fx = x + ux * 0.08f, fz = z + uz * 0.08f;
        int tex = lib.dirSign[(int) (Noise.rnd(n.id, e.id, 993) * lib.dirSign.length) % lib.dirSign.length];
        quad(fx - lx * w * 0.5f, fz - lz * w * 0.5f, fx + lx * w * 0.5f, fz + lz * w * 0.5f, base, base, base + h, base + h, tex, 1f, 1f, 0);
    }

    private void busStop(RoadNode n) {
        RoadEdge best = null;
        for (int id : n.edges) {
            RoadEdge e = net.edges.get(id);
            if (e.cls.motorway() || e.cls == RoadClass.LANE) continue;
            if (best == null || e.cls.priority > best.cls.priority) best = e;
        }
        if (best == null) return;
        float d = best.clear(n.id) + 22f;
        if (best.len < d + 10f) return;
        boolean fromA = best.a == n.id;
        best.point(fromA ? d : best.len - d, q);
        float ux = fromA ? q[3] : -q[3], uz = fromA ? q[4] : -q[4];
        // traffic leaving the village stops on its left
        float lx = uz, lz = -ux;
        float off = best.cls.halfWidth() + 1.0f;
        float x = q[0] + lx * off, z = q[1] + lz * off;
        if (net.roadClearance(x, z, 4f) < 0.6f) return;
        float y = terrain.height(x, z);
        pole(x, y, z, 2.5f, lib.pole);
        disc(x, y + 2.35f, z, ux, uz, 0.55f, lib.busStop);
        // a small shelter behind the stop: back wall, roof, bench
        float sx = x + lx * 1.6f + ux * 2.5f, sz = z + lz * 1.6f + uz * 2.5f, sy = terrain.height(sx, sz);
        float ax = sx - ux * 1.5f, az = sz - uz * 1.5f, bx = sx + ux * 1.5f, bz = sz + uz * 1.5f;
        quad(ax + lx * 0.6f, az + lz * 0.6f, bx + lx * 0.6f, bz + lz * 0.6f, sy, sy, sy + 2.2f, sy + 2.2f, lib.concrete, 0.75f, 0.55f, 0);
        float rx0 = Math.min(ax, bx) - Math.abs(lx) * 0.8f, rx1 = Math.max(ax, bx) + Math.abs(lx) * 0.8f;
        float rz0 = Math.min(az, bz) - Math.abs(lz) * 0.8f, rz1 = Math.max(az, bz) + Math.abs(lz) * 0.8f;
        gb.box(rx0, sy + 2.2f, rz0, rx1, sy + 2.35f, rz1, lib.metal, lib.metal, 1f, 1f, L_OBJECT, 0f, 0);
        float cx0 = Math.min(ax, bx) + Math.abs(lx) * 0.1f, cx1 = Math.max(ax, bx) + Math.abs(lx) * 0.5f;
        float cz0 = Math.min(az, bz) + Math.abs(lz) * 0.1f, cz1 = Math.max(az, bz) + Math.abs(lz) * 0.5f;
        gb.box(Math.min(cx0, cx1), sy + 0.4f, Math.min(cz0, cz1), Math.max(cx0, cx1), sy + 0.5f, Math.max(cz0, cz1), lib.barnWall[0], lib.barnWall[0], 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        ch.boxes.add(new Box(Math.min(ax, bx) - 0.3f, sy - 1f, Math.min(az, bz) - 0.3f, Math.max(ax, bx) + 0.3f, sy + 2.35f, Math.max(az, bz) + 0.3f));
    }

    /** Orange-rimmed mirror on a post at the corner between the first two roads. */
    private void curveMirror(RoadNode n) {
        int m = n.edges.length;
        float[] ang = new float[m];
        for (int k = 0; k < m; k++) {
            RoadEdge e = net.edges.get(n.edges[k]);
            float d = Math.min(12f, e.len * 0.5f);
            e.point(e.a == n.id ? d : e.len - d, q);
            ang[k] = (float) Math.atan2(q[1] - n.z, q[0] - n.x);
        }
        java.util.Arrays.sort(ang);
        // the widest gap between neighbouring roads: the mirror stands in that corner
        int bk = 0;
        float bg = -1f;
        for (int k = 0; k < m; k++) {
            float a0 = ang[k], a1 = k + 1 < m ? ang[k + 1] : ang[0] + (float) (Math.PI * 2);
            if (a1 - a0 > bg) { bg = a1 - a0; bk = k; }
        }
        float mid = ang[bk] + bg * 0.5f;
        float r = Math.max(6f, n.ringRadius + 1.8f);
        float x = n.x + (float) Math.cos(mid) * r, z = n.z + (float) Math.sin(mid) * r;
        if (net.roadClearance(x, z, 4f) < 0.4f) return;
        float y = terrain.height(x, z);
        pole(x, y, z, 2.9f, lib.paintOrange);
        float fx = (float) -Math.cos(mid), fz = (float) -Math.sin(mid);
        disc(x + fx * 0.1f, y + 2.6f, z + fz * 0.1f, fx, fz, 0.8f, lib.curveMirror);
    }

    // ------------------------------------------------------------------ pieces

    private void pole(float x, float y, float z, float h, int tex) {
        gb.box(x - 0.05f, y - 0.2f, z - 0.05f, x + 0.05f, y + h, z + 0.05f, tex, tex, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        ch.boxes.add(new Box(x - 0.08f, y - 1f, z - 0.08f, x + 0.08f, y + h, z + 0.08f));
    }

    /** Round plate of diameter d centred at (x, y, z), facing (fx, fz), alpha-clipped. */
    private void disc(float x, float y, float z, float fx, float fz, float d, int tex) {
        float rx = -fz, rz = fx, h = d * 0.5f;
        quad(x - rx * h, z - rz * h, x + rx * h, z + rz * h, y - h, y - h, y + h, y + h, tex, 1f, 1f, Geo.FLAG_ALPHA);
    }

    /** Upright quad from (ax, az) to (bx, bz): bottom heights ya0/yb0, top ya1/yb1, double-sided. */
    private void quad(float ax, float az, float bx, float bz, float ya0, float yb0, float ya1, float yb1, int tex, float u, float v, int flags) {
        p[0] = ax; p[1] = ya0; p[2] = az;
        p[3] = bx; p[4] = yb0; p[5] = bz;
        p[6] = bx; p[7] = yb1; p[8] = bz;
        p[9] = ax; p[10] = ya1; p[11] = az;
        uv[0] = 0f; uv[1] = v; uv[2] = u; uv[3] = v; uv[4] = u; uv[5] = 0f; uv[6] = 0f; uv[7] = 0f;
        float lt = 0.95f;
        l[0] = l[1] = lt * 0.85f;
        l[2] = l[3] = lt;
        gb.quad(p, uv, l, tex, L_OBJECT, flags, -0.1f, true);
    }
}
