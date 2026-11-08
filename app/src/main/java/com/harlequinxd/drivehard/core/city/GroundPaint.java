package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Ground;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.RoadNode;
import com.harlequinxd.drivehard.core.tex.TerrainTiles;

import java.util.List;

/**
 * The painted terrain of one chunk, for the GPU (Unity-style texture layering on one terrain mesh):
 * <ul>
 * <li>layer weights on a 1 m lattice for up to eight natural layers (grass, hill grass, sand,
 *   rock, the crops, bare dirt, the city's asphalt), with dirt worn along field edges and around
 *   yards; the GPU picks a layer per texel with an ordered dither, so layers fray into each other;</li>
 * <li>a 0.25 m material map of the painted cover (lots, sidewalks, yards, parks, plazas);</li>
 * <li>the roads projected onto the ground: on a 1 m lattice, the signed distance to the nearest
 *   asphalt edge (junctions included, so roads that meet or cross blend into one surface) and the
 *   owning road's own coordinates (offset across, distance along), from which the GPU paints its
 *   lanes, markings, stop lines and the ragged dirt edge, following its curve exactly.</li>
 * </ul>
 */
final class GroundPaint {
    private static final int N = Ground.Tile.SPLAT, L = Ground.Tile.LAYERS, RN = Ground.Tile.ROAD, MN = Ground.Tile.MAT;
    /** Roads paint this far past their asphalt (the shoulder), metres. */
    static final float REACH = 2.4f;

    private final City city;
    private final Terrain terrain;
    private final RoadNetwork net;

    GroundPaint(City city) {
        this.city = city;
        this.terrain = city.terrain;
        this.net = city.roads;
    }

    void paint(Ground.Tile t, GeoBuilder gb, GroundPainter.Surface[] grid, List<float[]> paint) {
        float x0 = t.cx * Ground.TILE, z0 = t.cz * Ground.TILE;
        mesh(t, gb, x0, z0);
        layers(t, x0, z0, grid);
        materials(t, x0, z0, paint);
        roads(t, x0, z0);
    }

    // ------------------------------------------------------------------ the mesh

    private void mesh(Ground.Tile t, GeoBuilder gb, float x0, float z0) {
        int m = Ground.Tile.MESH;
        t.land = new float[m * m];
        t.lit = new float[m * m];
        float step = Ground.TILE / (m - 1);
        for (int j = 0; j < m; j++)
            for (int i = 0; i < m; i++) {
                float x = x0 + i * step, z = z0 + j * step;
                t.land[j * m + i] = terrain.height(x, z);
                t.lit[j * m + i] = gb.groundLight(x, z);
            }
    }

    // ------------------------------------------------------------------ natural layers

    private int[] slotTex;
    private float[] slotScale;
    private int slots;

    private int slot(int tex, float scale) {
        for (int k = 0; k < slots; k++) if (slotTex[k] == tex) return k;
        if (slots == L) return 0;                          // more than eight: the first takes it
        slotTex[slots] = tex;
        slotScale[slots] = scale;
        return slots++;
    }

    private void layers(Ground.Tile t, float x0, float z0, GroundPainter.Surface[] grid) {
        slotTex = new int[L];
        slotScale = new float[L];
        java.util.Arrays.fill(slotTex, -1);
        slots = 0;
        TerrainTiles tt = city.lib.tiles;
        float tileM = TerrainTiles.METRES;
        int[] typeSlot = new int[TerrainTiles.TYPES];
        java.util.Arrays.fill(typeSlot, -1);
        // natural ground type at the 8 m grid corners
        int[] ct = new int[81];
        for (int j = 0; j <= 8; j++)
            for (int i = 0; i <= 8; i++) ct[j * 9 + i] = city.cornerType(x0 + i * 8f, z0 + j * 8f);
        // yards (their dirt fringe) near the chunk
        List<float[]> yards = new java.util.ArrayList<>();
        city.countryside.yards(x0 - 2f, z0 - 2f, x0 + 66f, z0 + 66f, (a, b, c, d, tex) -> yards.add(new float[]{a, b, c, d}));
        float[] w = new float[N * N * L];
        float[] one = new float[L];
        for (int sj = 0; sj < N; sj++)
            for (int si = 0; si < N; si++) {
                float x = x0 + si, z = z0 + sj;
                java.util.Arrays.fill(one, 0f);
                if (city.urban(x, z)) {
                    one[slot(tt.asphalt, tileM)] = 1f;
                } else {
                    // crops of the cells this sample lies in (half each on a cell edge)
                    float crop = 0f;
                    int ci0 = si == 64 ? 7 : si / 8, cj0 = sj == 64 ? 7 : sj / 8;
                    boolean edgeX = si % 8 == 0, edgeZ = sj % 8 == 0;
                    for (int dz = 0; dz <= (edgeZ ? 1 : 0); dz++)
                        for (int dx = 0; dx <= (edgeX ? 1 : 0); dx++) {
                            int ci = edgeX ? si / 8 - 1 + dx : ci0, cj = edgeZ ? sj / 8 - 1 + dz : cj0;
                            GroundPainter.Surface sf = grid[(cj + 1) * 10 + (ci + 1)];
                            float share = (edgeX ? 0.5f : 1f) * (edgeZ ? 0.5f : 1f);
                            if (sf.crop != Countryside.Crop.NONE) {
                                one[slot(city.countryside.texture(sf.crop), tileM)] += share;
                                crop += share;
                            }
                        }
                    // the natural ground: the cell's corner types, blended across it
                    if (crop < 1f) {
                        int ci = Math.min(7, si / 8), cj = Math.min(7, sj / 8);
                        float fx = (si - ci * 8) / 8f, fz = (sj - cj * 8) / 8f;
                        int o = cj * 9 + ci;
                        float[] cw = {(1 - fx) * (1 - fz), fx * (1 - fz), (1 - fx) * fz, fx * fz};
                        int[] cty = {ct[o], ct[o + 1], ct[o + 9], ct[o + 10]};
                        for (int k = 0; k < 4; k++) {
                            int ty = cty[k];
                            if (typeSlot[ty] < 0) typeSlot[ty] = slot(tt.base[ty], tileM);
                            one[typeSlot[ty]] += cw[k] * (1f - crop);
                        }
                    }
                    // bare dirt worn along field edges and round yards
                    float dirt = Math.max(fieldMargin(grid, si, sj), yardFringe(yards, x, z));
                    if (dirt > 0f) {
                        for (int k = 0; k < L; k++) one[k] *= 1f - dirt;
                        one[slot(city.lib.dirt, 2f)] += dirt;
                    }
                }
                System.arraycopy(one, 0, w, (sj * N + si) * L, L);
            }
        byte[] splat = new byte[N * N * L];
        for (int s = 0; s < N * N; s++) {
            float sum = 0f;
            for (int k = 0; k < L; k++) sum += w[s * L + k];
            if (sum <= 0f) { splat[s * L] = (byte) 255; continue; }
            for (int k = 0; k < L; k++) splat[s * L + k] = (byte) Math.round(Math.min(1f, w[s * L + k] / sum) * 255f);
        }
        t.splat = splat;
        t.layerTex = slotTex;
        t.layerScale = slotScale;
    }

    /** Dirt at a field's edge: solid at the boundary, fading 1.6 m into the field and 1.1 m into grass. */
    private static float fieldMargin(GroundPainter.Surface[] grid, int si, int sj) {
        final float in = 1.6f, out = 1.1f;
        float best = 0f;
        int ci = Math.min(7, si / 8), cj = Math.min(7, sj / 8);
        for (int dj = -1; dj <= 1; dj++)
            for (int di = -1; di <= 1; di++) {
                int i = ci + di, j = cj + dj;
                if (i < -1 || i > 8 || j < -1 || j > 8) continue;
                Countryside.Crop c = grid[(j + 1) * 10 + (i + 1)].crop;
                if (c == Countryside.Crop.NONE) continue;
                float ax = i * 8f, az = j * 8f, bx = ax + 8f, bz = az + 8f;
                for (int side = 0; side < 4; side++) {
                    int ni = i + (side == 0 ? -1 : side == 1 ? 1 : 0), nj = j + (side == 2 ? -1 : side == 3 ? 1 : 0);
                    if (ni < -1 || ni > 8 || nj < -1 || nj > 8) continue;
                    Countryside.Crop other = grid[(nj + 1) * 10 + (ni + 1)].crop;
                    if (other == c) continue;
                    // signed distance into this field from that edge, and the position along the edge
                    float d, along, lo, hi;
                    switch (side) {
                        case 0: d = si - ax; along = sj; lo = az; hi = bz; break;
                        case 1: d = bx - si; along = sj; lo = az; hi = bz; break;
                        case 2: d = sj - az; along = si; lo = ax; hi = bx; break;
                        default: d = bz - sj; along = si; lo = ax; hi = bx; break;
                    }
                    if (along < lo || along > hi || d > 8f) continue;
                    float k;
                    if (d >= 0f) k = 1f - d / in;
                    else if (other == Countryside.Crop.NONE) k = 1f + d / out;   // out into the grass
                    else continue;                                                // the neighbouring field draws its own
                    best = Math.max(best, k);
                }
            }
        return Math.max(0f, best);
    }

    private static float yardFringe(List<float[]> yards, float x, float z) {
        final float f = 1.2f;
        float best = 0f;
        for (float[] r : yards) {
            float dx = Math.max(Math.max(r[0] - x, x - r[2]), 0f), dz = Math.max(Math.max(r[1] - z, z - r[3]), 0f);
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < f) best = Math.max(best, 1f - d / f);
        }
        return best;
    }

    // ------------------------------------------------------------------ painted cover

    private void materials(Ground.Tile t, float x0, float z0, List<float[]> paint) {
        if (paint.isEmpty()) return;
        byte[] mat = new byte[MN * MN];
        int[] tex = new int[Ground.Tile.MATS];
        float[] sc = new float[Ground.Tile.MATS * 2];
        int n = 1;
        boolean any = false;
        final float px = Ground.TILE / MN;
        for (float[] r : paint) {
            int i0 = Math.max(0, (int) Math.ceil((r[0] - x0) / px - 0.5f)), i1 = Math.min(MN - 1, (int) Math.floor((r[2] - x0) / px - 0.5f));
            int j0 = Math.max(0, (int) Math.ceil((r[1] - z0) / px - 0.5f)), j1 = Math.min(MN - 1, (int) Math.floor((r[3] - z0) / px - 0.5f));
            if (i0 > i1 || j0 > j1) continue;
            int id = (int) r[4];
            int m = -1;
            for (int k = 1; k < n; k++) if (tex[k] == id && sc[k * 2] == r[5] && sc[k * 2 + 1] == r[6]) { m = k; break; }
            if (m < 0) {
                if (n == Ground.Tile.MATS) m = n - 1;
                else { m = n++; tex[m] = id; sc[m * 2] = r[5]; sc[m * 2 + 1] = r[6]; }
            }
            for (int j = j0; j <= j1; j++) java.util.Arrays.fill(mat, j * MN + i0, j * MN + i1 + 1, (byte) m);
            any = true;
        }
        if (!any) return;
        t.mat = mat;
        t.matTex = java.util.Arrays.copyOf(tex, n);
        t.matScale = java.util.Arrays.copyOf(sc, n * 2);
    }

    // ------------------------------------------------------------------ roads

    private float[] rd, key;
    private short[] ri;
    private boolean anyRoad;

    private void roads(Ground.Tile t, float x0, float z0) {
        rd = new float[RN * RN * 4];
        ri = new short[RN * RN * 4];
        key = new float[RN * RN];
        anyRoad = false;
        for (int k = 0; k < RN * RN; k++) { rd[k * 4] = 99f; key[k] = 99f; }
        float pad = 32f;
        net.forEachPieceIn(x0 - pad, z0 - pad, x0 + 64f + pad, z0 + 64f + pad, (e, i) -> piece(e, i, x0, z0));
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                for (RoadNode n : net.nodesInChunk(t.cx + dx, t.cz + dz)) junction(n, x0, z0);
        if (anyRoad) { t.road = rd; t.roadInfo = ri; }
        key = null;
        rd = null;
        ri = null;
    }

    private void piece(RoadEdge e, int i, float x0, float z0) {
        if (e.bridge[i]) return;
        RoadClass cls = e.cls;
        if (cls.ordinal() >= 8) return;
        float hw = cls.halfWidth();
        float ax = e.x[i], az = e.z[i], bx = e.x[i + 1], bz = e.z[i + 1];
        float dx = bx - ax, dz = bz - az, len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) return;
        float ux = dx / len, uz = dz / len;
        float nx = uz, nz = -ux;                                  // left of a -> b
        float reach = hw + REACH + 1f;
        int i0 = Math.max(0, (int) Math.floor(Math.min(ax, bx) - reach - x0)), i1 = Math.min(RN - 1, (int) Math.ceil(Math.max(ax, bx) + reach - x0));
        int j0 = Math.max(0, (int) Math.floor(Math.min(az, bz) - reach - z0)), j1 = Math.min(RN - 1, (int) Math.ceil(Math.max(az, bz) + reach - z0));
        if (i0 > i1 || j0 > j1) return;
        RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
        float trimA = na.ring != null ? e.clearA : 0f, trimB = nb.ring != null ? e.clearB : 0f;
        float ma = net.mouth(e, na), mb = net.mouth(e, nb);
        int flags = 0;
        if (ma > 0f && (na.kind == RoadNode.Kind.CITY || givesWay(e, na))) flags |= Ground.Tile.RF_PAINT_A;
        if (mb > 0f && (nb.kind == RoadNode.Kind.CITY || givesWay(e, nb))) flags |= Ground.Tile.RF_PAINT_B;
        if (cls == RoadClass.RAMP && gore(e, i)) flags |= Ground.Tile.RF_PLAIN;
        if (e.surface == RoadEdge.GRAVEL) flags |= Ground.Tile.RF_GRAVEL;
        else if (e.surface == RoadEdge.DIRT) flags |= Ground.Tile.RF_DIRT;
        else if (!cls.city && cls != RoadClass.FREEWAY && cls != RoadClass.RAMP) flags |= Ground.Tile.RF_SHOULDER;
        // ends where other roads join (a junction): no dirt there, and the through road owns the overlap
        boolean joinA = joins(na), joinB = joins(nb);
        boolean yieldA = joinA && minor(e, na), yieldB = joinB && minor(e, nb);
        if (joinA) flags |= Ground.Tile.RF_JOIN_A;
        if (joinB) flags |= Ground.Tile.RF_JOIN_B;
        float sa = e.s[i], sb = e.s[i + 1];
        // a bridge next to this piece: the painted road stops square where the deck begins
        boolean first = i == 0 || e.bridge[i - 1], last = i == e.count() - 2 || e.bridge[i + 1];
        float endA = i > 0 && e.bridge[i - 1] ? sa : trimA, endB = i < e.count() - 2 && e.bridge[i + 1] ? sb : e.len - trimB;
        int mouths = Math.min(255, Math.round(ma * 16f)) | Math.min(255, Math.round(mb * 16f)) << 8;
        for (int j = j0; j <= j1; j++)
            for (int k = i0; k <= i1; k++) {
                float px = x0 + k - ax, pz = z0 + j - az;
                float tl = (px * ux + pz * uz) / len;
                float tc = Math.max(0f, Math.min(1f, tl));
                // the nearest point on the piece (rounded at its joints, flat at the road's ends)
                float qx = px - dx * tc, qz = pz - dz * tc;
                float side = px * nx + pz * nz;
                float across = (float) Math.sqrt(qx * qx + qz * qz) * (side < 0f ? -1f : 1f);
                if ((tl < 0f && first) || (tl > 1f && last)) across = side;
                float s = sa + (sb - sa) * ((tl < 0f && first) || (tl > 1f && last) ? tl : tc);
                float sdf = Math.abs(across) - hw;
                float cut = Math.max(endA - s, s - endB);
                sdf = Math.max(sdf, cut);
                int o = j * RN + k;
                if (sdf > REACH) continue;
                // the asphalt is the union of all roads; the sample belongs to the road it lies deepest in,
                // except that a road arriving at a junction yields to the one it joins
                float dA = s - trimA, dB = e.len - trimB - s;
                float k2 = sdf + (yieldA ? Math.max(0f, 3f - dA) : 0f) + (yieldB ? Math.max(0f, 3f - dB) : 0f);
                if (sdf < rd[o * 4]) rd[o * 4] = sdf;
                if (k2 >= key[o]) continue;
                key[o] = k2;
                rd[o * 4 + 1] = across;
                rd[o * 4 + 2] = s - trimA;
                rd[o * 4 + 3] = e.len - trimB - s;
                ri[o * 4] = (short) e.id;
                ri[o * 4 + 1] = (short) ((e.id >>> 16) & 0x7FFF);
                ri[o * 4 + 2] = (short) (cls.ordinal() | flags << 8);
                ri[o * 4 + 3] = (short) mouths;
                anyRoad = true;
            }
    }

    private void junction(RoadNode n, float x0, float z0) {
        float[] ring = n.ring;
        if (ring == null) return;
        int m = ring.length / 3;
        float minx = Float.MAX_VALUE, minz = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, maxz = -Float.MAX_VALUE;
        for (int q = 0; q < m; q++) {
            minx = Math.min(minx, ring[q * 3]); maxx = Math.max(maxx, ring[q * 3]);
            minz = Math.min(minz, ring[q * 3 + 2]); maxz = Math.max(maxz, ring[q * 3 + 2]);
        }
        float reach = REACH + 1f;
        int i0 = Math.max(0, (int) Math.floor(minx - reach - x0)), i1 = Math.min(RN - 1, (int) Math.ceil(maxx + reach - x0));
        int j0 = Math.max(0, (int) Math.floor(minz - reach - z0)), j1 = Math.min(RN - 1, (int) Math.ceil(maxz + reach - z0));
        if (i0 > i1 || j0 > j1) return;
        boolean shoulder = n.kind != RoadNode.Kind.CITY;
        int cls = 0;
        for (int id : n.edges) {
            RoadClass c = net.edges.get(id).cls;
            if (c.city || c == RoadClass.FREEWAY || c == RoadClass.RAMP) shoulder = false;
            cls = c.ordinal();
        }
        int flags = 0;                       // no dirt edge where roads meet
        // where only farm tracks meet, the junction is gravel or dirt too
        int unpaved = -1;
        for (int id : n.edges) {
            RoadEdge e = net.edges.get(id);
            if (e.surface == RoadEdge.PAVED) { unpaved = -1; break; }
            if (unpaved < 0) unpaved = e.surface;
        }
        if (unpaved == RoadEdge.GRAVEL) flags |= Ground.Tile.RF_GRAVEL;
        else if (unpaved == RoadEdge.DIRT) flags |= Ground.Tile.RF_DIRT;
        for (int j = j0; j <= j1; j++)
            for (int k = i0; k <= i1; k++) {
                float px = x0 + k, pz = z0 + j;
                float best = Float.MAX_VALUE;
                boolean inside = false;
                for (int q = 0; q < m; q++) {
                    int r = (q + 1) % m;
                    float ax = ring[q * 3], az = ring[q * 3 + 2], bx = ring[r * 3], bz = ring[r * 3 + 2];
                    float ex = bx - ax, ez = bz - az, l2 = ex * ex + ez * ez;
                    float tt = l2 > 1e-8f ? Math.max(0f, Math.min(1f, ((px - ax) * ex + (pz - az) * ez) / l2)) : 0f;
                    float qx = px - ax - ex * tt, qz = pz - az - ez * tt;
                    best = Math.min(best, qx * qx + qz * qz);
                    if ((az > pz) != (bz > pz) && px < ax + (pz - az) / (bz - az) * ex) inside = !inside;
                }
                float sdf = (float) Math.sqrt(best) * (inside ? -1f : 1f);
                int o = j * RN + k;
                if (sdf > REACH) continue;
                if (sdf < rd[o * 4]) rd[o * 4] = sdf;
                if (sdf >= key[o]) continue;
                key[o] = sdf;
                rd[o * 4 + 1] = 0f;
                rd[o * 4 + 2] = 0f;
                rd[o * 4 + 3] = 0f;
                ri[o * 4] = (short) n.id;
                ri[o * 4 + 1] = (short) (0x8000 | ((n.id >>> 16) & 0x7FFF));
                ri[o * 4 + 2] = (short) (cls | flags << 8);
                ri[o * 4 + 3] = 0;
                anyRoad = true;
            }
    }

    /** Other roads meet at n (not a freeway split, not just the road running on). */
    private static boolean joins(RoadNode n) {
        return n.kind != RoadNode.Kind.SPLIT && (n.ring != null || n.edges.length >= 3);
    }

    /** A more important road meets e at n (e arrives at it, rather than running through). */
    private boolean minor(RoadEdge e, RoadNode n) {
        int best = 0;
        for (int id : n.edges) if (id != e.id) best = Math.max(best, net.edges.get(id).cls.priority);
        return best > e.cls.priority;
    }

    /** Traffic on e gives way at n: a higher-priority road meets it there. */
    private boolean givesWay(RoadEdge e, RoadNode n) {
        if (n.kind != RoadNode.Kind.JUNCTION) return false;
        int best = 0;
        for (int id : n.edges) if (id != e.id) best = Math.max(best, net.edges.get(id).cls.priority);
        return best > e.cls.priority;
    }

    /** A slip-road piece still overlapping the freeway it branches from. */
    private boolean gore(RoadEdge e, int i) {
        float mx = (e.x[i] + e.x[i + 1]) * 0.5f, mz = (e.z[i] + e.z[i + 1]) * 0.5f;
        float d = net.classDistance(mx, mz, RoadClass.FREEWAY, 60f);
        return d < RoadClass.FREEWAY.halfWidth() + e.cls.halfWidth() - 0.5f;
    }

    // ------------------------------------------------------------------ road style for the GPU

    static float[] roadStyle(com.harlequinxd.drivehard.core.tex.TextureLibrary lib) {
        float[] out = new float[8 * 20 + 6];
        for (RoadClass c : RoadClass.values()) {
            if (c.ordinal() >= 8) continue;
            int o = c.ordinal() * 20;
            float[][] bands = com.harlequinxd.drivehard.core.road.RoadMesh.bands(c, lib);
            out[o] = c.halfWidth();
            out[o + 1] = com.harlequinxd.drivehard.core.road.RoadMesh.mouthTex(c, lib);
            out[o + 2] = Math.min(4, bands.length);
            for (int b = 0; b < Math.min(4, bands.length); b++)
                System.arraycopy(bands[b], 0, out, o + 4 + b * 4, 4);
        }
        out[160] = lib.tiles.asphalt;
        out[161] = lib.roadEdge;
        out[162] = lib.gravelTrack;
        out[163] = lib.dirtTrack;
        out[164] = lib.gravel;
        out[165] = lib.dirt;
        return out;
    }
}
