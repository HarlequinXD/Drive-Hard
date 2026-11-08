package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.model.Flora;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.RoadNode;
import com.harlequinxd.drivehard.core.tex.Noise;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Farmland and rural settlement outside the city.
 *
 * Land use: on flat lowland the ground is divided into rectangular field parcels (rice paddies
 * with grass levees, flooded, green or golden depending on the district; cabbage, ploughed
 * soil, fallow grass), tea rows on gentle slopes, and woodland on hills and mountains.
 *
 * Settlement: farmsteads line the country roads (an old farmhouse, a barn, a greenhouse, a
 * gravel yard and a driveway to the road, trees and a bamboo clump), and every village junction
 * has a cluster of houses, small apartments and a convenience store. Sites are placed once
 * (deterministically) and built chunk by chunk.
 */
public final class Countryside implements java.io.Serializable {
    /** Field parcel size (metres), multiples of the 8 m terrain cell. */
    public static final int PARCEL_W = 40, PARCEL_D = 24;

    public enum Crop { NONE, PADDY_WATER, PADDY_GREEN, PADDY_GOLD, CABBAGE, SOIL, FALLOW, TEA, PASTURE, ORCHARD }

    private final City city;
    private final Terrain terrain;
    private final RoadNetwork roads;
    private final TextureLibrary lib;
    private final int seed;

    static final int FARM = 0, HOUSE = 1, APAATO = 2, KONBINI = 3, LOT = 4, TOWN_HALL = 5, POST = 6, CLINIC = 7, SHOP = 8,
            SUPERMARKET = 9, SCHOOL = 10, SHRINE = 11, GAS = 12, TOWN_KONBINI = 13, HARBOUR = 14;

    /** A building site: footprint, kind, the side facing the road (0=N,1=S,2=W,3=E), driveway end. */
    static final class Site implements java.io.Serializable {
        float x0, z0, x1, z1;
        int kind, face, seed, design;
        boolean westNeighbor;
        float rx, rz;   // driveway end at the road edge
        /** Painted ground areas {x0, z0, x1, z1, texture}. */
        List<float[]> ground;
        /** The painted ground areas packed {x0, z0, x1, z1, texture}* once placement is done. */
        float[] groundPacked;
    }

    private final HashMap<Long, List<Site>> sites = new HashMap<>();
    public int siteCount, farmCount;

    Countryside(City city, TextureLibrary lib, long seed) {
        this.city = city;
        this.terrain = city.terrain;
        this.roads = city.roads;
        this.lib = lib;
        this.seed = (int) seed;
        place();
        // placement bookkeeping is no longer needed; pack every site's ground into one array
        taken = null;
        for (List<Site> list : sites.values()) {
            for (Site st : list) {
                if (st.ground == null) continue;
                // x0, z0, x1, z1, texture, side facing the street (car parks: where cars drive in; -1 none)
                float[] pk = new float[st.ground.size() * 6];
                for (int i = 0; i < st.ground.size(); i++) {
                    float[] g = st.ground.get(i);
                    System.arraycopy(g, 0, pk, i * 6, 5);
                    pk[i * 6 + 5] = g.length > 5 ? g[5] : -1f;
                }
                st.groundPacked = pk;
                st.ground = null;
            }
            if (list instanceof ArrayList) ((ArrayList<Site>) list).trimToSize();
        }
    }

    // ------------------------------------------------------------------ land use

    private float vn(float x, float z, int s) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        float a = Noise.rnd(ix, iz, s), b = Noise.rnd(ix + 1, iz, s), c = Noise.rnd(ix, iz + 1, s), d = Noise.rnd(ix + 1, iz + 1, s);
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    /** True where the land is farmed (lowland away from the city and the coast). */
    public boolean farmland(float x, float z, float h) {
        if (h > 95f || h < 2.6f) return false;
        if (terrain.coastOver(x, z) > -160f) return false;
        // fields reach in between the suburbs, but not right up to the houses
        float cd = terrain.cityDistance(x, z);
        if (cd > 0f ? cd < 600f : city.nearUrban(x, z, 250f)) return false;
        return vn(x / 2600f, z / 2600f, seed + 950) > 0.33f;
    }

    /** Crop of the 8 m cell at (cx,cz) (its min corner), given the cell's average height and slope. */
    public Crop crop(float cx, float cz, float h, float slope) {
        Parcel p = new Parcel();
        parcel(cx + 4f, cz + 4f, p);
        return use(p);
    }

    /**
     * What a whole paddock is used for, decided at its centre so paddocks are farmed (or not)
     * edge to edge: NONE off the farmland, in towns, or on steep ground; tea on gentle slopes.
     */
    public Crop use(Parcel p) {
        if (p.crop == Crop.NONE) return Crop.NONE;
        float h = terrain.natural(p.cx, p.cz);
        if (!farmland(p.cx, p.cz, h) || inTown(p.cx, p.cz, 30f)) return Crop.NONE;
        float r = 25f;
        float sl = Math.max(Math.abs(terrain.natural(p.cx + r, p.cz) - terrain.natural(p.cx - r, p.cz)),
                Math.abs(terrain.natural(p.cx, p.cz + r) - terrain.natural(p.cx, p.cz - r))) / (2f * r);
        if (sl > 0.16f) return Crop.NONE;
        if (sl > 0.07f) return paddy(p.crop) || p.crop == Crop.CABBAGE ? Crop.TEA : p.crop;
        return p.crop;
    }

    // ------------------------------------------------------------------ paddocks

    /** What a point of farmland belongs to. */
    static final class Parcel {
        Crop crop = Crop.NONE;
        /** Distance to the paddock's edge (m), and to the nearest levee or track inside it. */
        float edge, inner;
        /** Coordinates along and across the paddock grid (for lining up crop rows). */
        float a, b;
        boolean fenced, seam;
        /** Paddock key and centre (world). */
        long key;
        float cx, cz;
    }

    /** Farm districts: each has its own paddock grid, lined up with the nearest road. */
    static final float DISTRICT = 900f, PADDOCK_A = 140f, PADDOCK_B = 100f;
    private final java.util.concurrent.ConcurrentHashMap<Long, float[]> districts = new java.util.concurrent.ConcurrentHashMap<>();

    /** {cos, sin, origin x, origin z, seed} of district (dx, dz). */
    float[] district(int dx, int dz) {
        long key = ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
        float[] d = districts.get(key);
        if (d != null) return d;
        float ox = (dx + 0.5f) * DISTRICT, oz = (dz + 0.5f) * DISTRICT;
        final float[] best = {Float.MAX_VALUE, 0f, 0f};
        roads.forEachPiece(ox - 700f, oz - 700f, ox + 700f, oz + 700f, (e, i) -> {
            if (e.cls == RoadClass.LANE || e.cls == RoadClass.TOWN) return;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i], l2 = vx * vx + vz * vz;
            if (l2 < 1e-4f) return;
            float t = Math.max(0f, Math.min(1f, ((ox - e.x[i]) * vx + (oz - e.z[i]) * vz) / l2));
            float qx = e.x[i] + vx * t - ox, qz = e.z[i] + vz * t - oz, dd = qx * qx + qz * qz;
            if (dd < best[0]) { best[0] = dd; best[1] = vx; best[2] = vz; }
        });
        double ang = best[0] < Float.MAX_VALUE ? Math.atan2(best[2], best[1]) : Noise.rnd(dx, dz, seed + 970) * Math.PI;
        d = new float[]{(float) Math.cos(ang), (float) Math.sin(ang), ox, oz, seed + 3000 + dx * 7919 + dz * 104729};
        districts.put(key, d);
        return d;
    }

    private static float edgeA(int k, int s) { return k * PADDOCK_A + (Noise.rnd(k, 17, s) - 0.5f) * 60f; }
    private static float edgeB(int k, int s) { return k * PADDOCK_B + (Noise.rnd(23, k, s) - 0.5f) * 44f; }

    /** Land use of paddock (i, j) of a district. */
    Crop paddockCrop(int i, int j, float[] d, float wx, float wz) {
        int s = (int) d[4];
        float r = Noise.rnd(i, j, s + 5);
        if (r < 0.36f) {
            // each area is at the same stage of the season
            float stage = vn(wx / 2200f, wz / 2200f, seed + 962);
            return stage < 0.36f ? Crop.PADDY_WATER : (stage < 0.68f ? Crop.PADDY_GREEN : Crop.PADDY_GOLD);
        }
        if (r < 0.58f) return Crop.PASTURE;
        if (r < 0.68f) return Crop.CABBAGE;
        if (r < 0.76f) return Crop.SOIL;
        if (r < 0.84f) return Crop.ORCHARD;
        return Crop.FALLOW;
    }

    /**
     * The paddock at (x, z): farm districts are divided into paddocks of roughly 140 x 100 m
     * (a few acres) on a grid lined up with the nearest road. Rice paddocks are split into small
     * levee-bounded paddies, vegetable paddocks into strips with grass tracks; pasture is fenced.
     * Where two districts meet runs a hedgerow.
     */
    void parcel(float x, float z, Parcel out) {
        int dx = (int) Math.floor(x / DISTRICT), dz = (int) Math.floor(z / DISTRICT);
        float[] d = district(dx, dz);
        int s = (int) d[4];
        float rx = x - d[2], rz = z - d[3];
        float a = rx * d[0] + rz * d[1], b = -rx * d[1] + rz * d[0];
        out.a = a;
        out.b = b;
        float seam = Math.min(Math.min(x - dx * DISTRICT, (dx + 1) * DISTRICT - x), Math.min(z - dz * DISTRICT, (dz + 1) * DISTRICT - z));
        out.seam = seam < 5f;
        int i = (int) Math.floor(a / PADDOCK_A);
        if (a < edgeA(i, s)) i--; else if (a >= edgeA(i + 1, s)) i++;
        int j = (int) Math.floor(b / PADDOCK_B);
        if (b < edgeB(j, s)) j--; else if (b >= edgeB(j + 1, s)) j++;
        float a0 = edgeA(i, s), a1 = edgeA(i + 1, s), b0 = edgeB(j, s), b1 = edgeB(j + 1, s);
        out.edge = Math.min(Math.min(a - a0, a1 - a), Math.min(b - b0, b1 - b));
        float ca = (a0 + a1) * 0.5f, cb = (b0 + b1) * 0.5f;
        out.cx = d[2] + ca * d[0] - cb * d[1];
        out.cz = d[3] + ca * d[1] + cb * d[0];
        out.key = ((long) s * 31 + i) * 1000003L + j;
        Crop c = paddockCrop(i, j, d, x, z);
        out.crop = out.seam ? Crop.NONE : c;
        out.fenced = c == Crop.PASTURE;
        if (paddy(c)) {
            float pa = (a - a0) % 30f, pb = (b - b0) % 18f;
            out.inner = Math.min(Math.min(pa, 30f - pa), Math.min(pb, 18f - pb));
        } else if (c == Crop.CABBAGE || c == Crop.SOIL) {
            float pb = (b - b0) % 24f;
            out.inner = Math.min(pb, 24f - pb);
        } else out.inner = 99f;
    }

    /**
     * Post-and-wire fences round the pasture paddocks in this chunk: one textured strip per 4 m
     * span, following the ground, left out across roads, yards and towns.
     */
    void fences(Chunk ch, GeoBuilder gb, float x0, float z0, float x1, float z1) {
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        float[][] corners = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        gb.begin(false);
        for (float[] c : corners) {
            int dx = (int) Math.floor(c[0] / DISTRICT), dz = (int) Math.floor(c[1] / DISTRICT);
            if (!seen.add(((long) dx << 32) ^ (dz & 0xFFFFFFFFL))) continue;
            float[] d = district(dx, dz);
            int s = (int) d[4];
            float amin = Float.MAX_VALUE, amax = -Float.MAX_VALUE, bmin = Float.MAX_VALUE, bmax = -Float.MAX_VALUE;
            for (float[] q : corners) {
                float rx = q[0] - d[2], rz = q[1] - d[3];
                float a = rx * d[0] + rz * d[1], b = -rx * d[1] + rz * d[0];
                amin = Math.min(amin, a); amax = Math.max(amax, a); bmin = Math.min(bmin, b); bmax = Math.max(bmax, b);
            }
            int i0 = (int) Math.floor(amin / PADDOCK_A) - 1, i1 = (int) Math.floor(amax / PADDOCK_A) + 1;
            int j0 = (int) Math.floor(bmin / PADDOCK_B) - 1, j1 = (int) Math.floor(bmax / PADDOCK_B) + 1;
            for (int i = i0; i <= i1 + 1; i++)
                for (int j = j0; j <= j1; j++) {
                    // boundary a = edgeA(i) between paddocks (i-1, j) and (i, j)
                    float ea = edgeA(i, s), eb0 = edgeB(j, s), eb1 = edgeB(j + 1, s);
                    if (fencedSide(i - 1, j, d) || fencedSide(i, j, d)) fenceLine(gb, d, dx, dz, ea, eb0, ea, eb1, x0, z0, x1, z1);
                    // boundary b = edgeB(j) between paddocks (i, j-1) and (i, j)
                    float eb = edgeB(j, s), ea0 = edgeA(i, s), ea1 = edgeA(i + 1, s);
                    if (fencedSide(i, j - 1, d) || fencedSide(i, j, d)) fenceLine(gb, d, dx, dz, ea0, eb, ea1, eb, x0, z0, x1, z1);
                }
        }
        gb.end();
    }

    /** Paddock (i, j) of district d is farmed pasture. */
    private boolean fencedSide(int i, int j, float[] d) {
        float r = Noise.rnd(i, j, (int) d[4] + 5);
        if (r < 0.36f || r >= 0.58f) return false;
        int s = (int) d[4];
        float ca = (edgeA(i, s) + edgeA(i + 1, s)) * 0.5f, cb = (edgeB(j, s) + edgeB(j + 1, s)) * 0.5f;
        Parcel p = new Parcel();
        p.crop = Crop.PASTURE;
        p.cx = d[2] + ca * d[0] - cb * d[1];
        p.cz = d[3] + ca * d[1] + cb * d[0];
        return use(p) == Crop.PASTURE;
    }

    private void fenceLine(GeoBuilder gb, float[] d, int dx, int dz, float a0, float b0, float a1, float b1,
                           float x0, float z0, float x1, float z1) {
        float len = (float) Math.hypot(a1 - a0, b1 - b0);
        int n = Math.max(1, (int) Math.ceil(len / 4f));
        float[] p = new float[12], t = new float[8], l = {1f, 1f, 1f, 1f};
        for (int k = 0; k < n; k++) {
            float ta = k / (float) n, tb = (k + 1) / (float) n;
            float pa = a0 + (a1 - a0) * ta, pb = b0 + (b1 - b0) * ta, qa = a0 + (a1 - a0) * tb, qb = b0 + (b1 - b0) * tb;
            float px = d[2] + pa * d[0] - pb * d[1], pz = d[3] + pa * d[1] + pb * d[0];
            float qx = d[2] + qa * d[0] - qb * d[1], qz = d[3] + qa * d[1] + qb * d[0];
            float mx = (px + qx) * 0.5f, mz = (pz + qz) * 0.5f;
            // each span belongs to the chunk (and district) its middle is in
            if (mx < x0 || mx >= x1 || mz < z0 || mz >= z1) continue;
            if ((int) Math.floor(mx / DISTRICT) != dx || (int) Math.floor(mz / DISTRICT) != dz) continue;
            float h = terrain.natural(mx, mz);
            if (!farmland(mx, mz, h) || inTown(mx, mz, 10f)) continue;
            if (roads.roadClearance(mx, mz, 6f) < 2.5f || roads.roadClearance(px, pz, 6f) < 2f || roads.roadClearance(qx, qz, 6f) < 2f) continue;
            float hp = terrain.height(px, pz), hq = terrain.height(qx, qz);
            p[0] = px; p[1] = hp; p[2] = pz;
            p[3] = qx; p[4] = hq; p[5] = qz;
            p[6] = qx; p[7] = hq + 1.15f; p[8] = qz;
            p[9] = px; p[10] = hp + 1.15f; p[11] = pz;
            float span = len / n;
            t[0] = 0f; t[1] = 1f; t[2] = span / 4f; t[3] = 1f; t[4] = span / 4f; t[5] = 0f; t[6] = 0f; t[7] = 0f;
            gb.quad(p, t, l, lib.fence, L_OBJECT, Geo.FLAG_ALPHA | Geo.FLAG_DETAIL, 0f, true);
        }
    }

    public int texture(Crop c) {
        switch (c) {
            case PADDY_WATER: return lib.paddyWater;
            case PADDY_GREEN: return lib.paddyGreen;
            case PADDY_GOLD: return lib.paddyGold;
            case CABBAGE: return lib.cabbage;
            case SOIL: return lib.soil;
            case TEA: return lib.tea;
            case FALLOW: return lib.hillGrass;
            default: return lib.grass;
        }
    }

    public static boolean paddy(Crop c) { return c == Crop.PADDY_WATER || c == Crop.PADDY_GREEN || c == Crop.PADDY_GOLD; }

    /** Grass levees along the parcel's north and west edges within this cell. */
    void levees(GeoBuilder gb, float ax, float az, float bx, float bz) {
        float w = 0.55f, lift = 0.22f;
        int flags = Geo.FLAG_DETAIL | Geo.FLAG_SORT_FAR;
        if (Math.floorMod((int) ax, PARCEL_W) == 0) leveeStrip(gb, ax, az, ax + w, bz, lift, flags);
        if (Math.floorMod((int) az, PARCEL_D) == 0) leveeStrip(gb, ax, az, bx, az + w, lift, flags);
    }

    /** A levee, in pieces, left out where a road crosses the paddies (roads are painted into the ground). */
    private void leveeStrip(GeoBuilder gb, float x0, float z0, float x1, float z1, float lift, int flags) {
        boolean alongX = x1 - x0 > z1 - z0;
        float len = alongX ? x1 - x0 : z1 - z0;
        int n = Math.max(1, (int) Math.ceil(len / 1f));
        int run = -1;
        for (int k = 0; k <= n; k++) {
            boolean clear = k < n && !onRoad(alongX ? x0 + (k + 0.5f) * len / n : (x0 + x1) * 0.5f,
                    alongX ? (z0 + z1) * 0.5f : z0 + (k + 0.5f) * len / n);
            if (clear && run < 0) run = k;
            if (!clear && run >= 0) {
                float a = run * len / n, b = k * len / n;
                if (alongX) gb.floorT(x0 + a, z0, x0 + b, z1, lift, lib.grass, 2f, 2f, L_OBJECT, flags, -0.4f, true);
                else gb.floorT(x0, z0 + a, x1, z0 + b, lift, lib.grass, 2f, 2f, L_OBJECT, flags, -0.4f, true);
                run = -1;
            }
        }
    }

    private boolean onRoad(float x, float z) { return roads.onAsphalt(x, z, 0.8f, -1); }

    // ------------------------------------------------------------------ site placement

    private transient HashSet<Long> taken = new HashSet<>();

    private static long cellKey(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    /** Reserves a footprint on a 16 m occupancy grid; false if anything is there already. */
    private boolean reserve(float x0, float z0, float x1, float z1) {
        int a = (int) Math.floor(x0 / 16f), b = (int) Math.floor(x1 / 16f), c = (int) Math.floor(z0 / 16f), d = (int) Math.floor(z1 / 16f);
        for (int z = c; z <= d; z++) for (int x = a; x <= b; x++) if (taken.contains(cellKey(x, z))) return false;
        for (int z = c; z <= d; z++) for (int x = a; x <= b; x++) taken.add(cellKey(x, z));
        return true;
    }

    /** Ground is buildable: away from roads, coast and city, not too steep. */
    private boolean buildable(float x0, float z0, float x1, float z1, float clearance) {
        if (terrain.cityDistance((x0 + x1) * 0.5f, (z0 + z1) * 0.5f) < 1200f) return false;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        float[][] pts = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {(x0 + x1) * 0.5f, (z0 + z1) * 0.5f}};
        for (float[] q : pts) {
            if (terrain.coastOver(q[0], q[1]) > -120f) return false;
            if (roads.roadClearance(q[0], q[1], 40f) < clearance) return false;
            float h = terrain.natural(q[0], q[1]);
            lo = Math.min(lo, h); hi = Math.max(hi, h);
        }
        return hi - lo < 4.5f && lo > 3f;
    }

    private void add(Site s) {
        float cx = (s.x0 + s.x1) * 0.5f, cz = (s.z0 + s.z1) * 0.5f;
        long k = ChunkManager.key(ChunkManager.chunkOf(cx), ChunkManager.chunkOf(cz));
        sites.computeIfAbsent(k, kk -> new ArrayList<>()).add(s);
        siteCount++;
    }

    private void place() {
        Random rnd = new Random(seed * 7L + 3);
        float[] p = new float[5];
        // farmsteads: placed with the road network, each with its own access lane
        for (float[] f : roads.farms) {
            Site st = new Site();
            st.x0 = f[0]; st.z0 = f[1]; st.x1 = f[2]; st.z1 = f[3];
            st.kind = FARM; st.face = (int) f[4]; st.seed = (int) f[5];
            st.rx = Float.NaN;
            reserve(st.x0, st.z0, st.x1, st.z1);
            add(st);
            farmCount++;
        }
        // country houses up the farm tracks, each at the end of its own driveway
        for (float[] f : roads.homes) {
            Site st = new Site();
            st.x0 = f[0]; st.z0 = f[1]; st.x1 = f[2]; st.z1 = f[3];
            st.kind = LOT; st.face = (int) f[4]; st.seed = (int) f[5];
            st.design = rnd.nextInt(HouseGen.SUBURB_DESIGNS);
            st.rx = Float.NaN;
            st.ground = lotGround(st);
            // the driveway arrives at the middle of the frontage: gravel across to the carport
            float w = st.face < 2 ? st.x1 - st.x0 : st.z1 - st.z0;
            float[] cp = st.ground.get(0);
            float[] lu = toLocal(st, cp);
            float[] pad = local(st, Math.min(w * 0.5f - 1.6f, lu[0]), 0f, Math.max(w * 0.5f + 1.6f, lu[2]), 3.2f);
            st.ground.add(0, new float[]{pad[0], pad[1], pad[2], pad[3], lib.gravel});
            reserve(st.x0, st.z0, st.x1, st.z1);
            add(st);
        }
        // fishing harbours: quay, breakwaters, boats and sheds
        for (float[] h : roads.harbours) {
            Site st = new Site();
            float qx = h[0], qz = h[1], sx = h[2], sz = h[3];
            // quay apron round the end of the harbour road, out to the water's edge
            float ax = Math.min(qx - sx * 25f, h[4] + sx * 4f), bx = Math.max(qx - sx * 25f, h[4] + sx * 4f);
            float az = Math.min(qz - sz * 25f, h[5] + sz * 4f), bz = Math.max(qz - sz * 25f, h[5] + sz * 4f);
            if (sx == 0f) { ax = qx - 32f; bx = qx + 32f; } else { az = qz - 32f; bz = qz + 32f; }
            st.x0 = qx - 1f; st.z0 = qz - 1f; st.x1 = qx + 1f; st.z1 = qz + 1f;
            st.kind = HARBOUR; st.seed = rnd.nextInt(); st.rx = sx; st.rz = sz;
            st.ground = new ArrayList<>();
            st.ground.add(new float[]{ax, az, bx, bz, lib.concrete});
            add(st);
        }
        // villages and towns with street grids: lots along every street, a centre with civic buildings
        for (com.harlequinxd.drivehard.core.road.Town t : roads.towns) if (t.grid != null) townLots(t, rnd);
        // hamlets: houses around the junction
        for (com.harlequinxd.drivehard.core.road.Town t : roads.towns) {
            if (t.grid != null) continue;
            RoadNode v = t.centre;
            int houses = 9 + rnd.nextInt(8);
            boolean shop = false;
            for (int k = 0; k < houses * 3 && houses > 0; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                float r = 28f + rnd.nextFloat() * 140f;
                float cx = v.x + (float) Math.cos(ang) * r, cz = v.z + (float) Math.sin(ang) * r;
                int kind = !shop && r < 70f ? KONBINI : (rnd.nextInt(7) == 0 ? APAATO : HOUSE);
                float hw = kind == KONBINI ? 8f : (kind == APAATO ? 9f : 6f), hd = kind == APAATO ? 6f : 5.5f;
                // face the village centre's road (the junction) along the dominant axis
                float dx = v.x - cx, dz = v.z - cz;
                int face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 3 : 2) : (dz > 0 ? 1 : 0);
                boolean wide = face < 2;
                float x0 = cx - (wide ? hw : hd), x1 = cx + (wide ? hw : hd), z0 = cz - (wide ? hd : hw), z1 = cz + (wide ? hd : hw);
                if (!buildable(x0 - 2f, z0 - 2f, x1 + 2f, z1 + 2f, 3f) || !reserve(x0 - 2f, z0 - 2f, x1 + 2f, z1 + 2f)) continue;
                Site st = new Site();
                st.x0 = x0; st.z0 = z0; st.x1 = x1; st.z1 = z1;
                st.kind = kind; st.face = face; st.seed = rnd.nextInt();
                st.rx = Float.NaN;
                add(st);
                if (kind == KONBINI) shop = true;
                houses--;
            }
        }
    }

    // ------------------------------------------------------------------ towns

    /** True on a harbour quay (no trees). */
    public boolean inHarbour(float x, float z) {
        for (float[] h : roads.harbours) if (Math.abs(x - h[0]) < 70f && Math.abs(z - h[1]) < 70f) return true;
        return false;
    }

    /** True inside the built-up area of a village or town (no fields or forest there). */
    public boolean inTown(float x, float z, float margin) {
        com.harlequinxd.drivehard.core.road.Town t = roads.townAt(x, z, margin);
        return t != null && t.grid != null;
    }

    /**
     * Lots of a village or town. Every block between four streets has two rows of lots, one facing
     * the street on each long side. Blocks around the centre hold the town office, post office,
     * clinic, a row of shops, the supermarket and a convenience store; a school with its sports
     * ground, a shrine and a petrol station take blocks further out. Everything else is houses
     * (with a few small apartment buildings), each picking one of the house designs.
     */
    private void townLots(com.harlequinxd.drivehard.core.road.Town t, Random rnd) {
        int nbx = t.gx.length - 1, nbz = t.gz.length - 1;
        boolean town = t.size == com.harlequinxd.drivehard.core.road.Town.Size.TOWN;
        int ci = (t.gx.length - 1) / 2, cj = (t.gz.length - 1) / 2;   // centre node: blocks ci-1..ci, cj-1..cj touch it
        float edge = RoadClass.TOWN.halfWidth() + 0.6f;
        for (int bj = 0; bj < nbz; bj++)
            for (int bi = 0; bi < nbx; bi++) {
                float bx0 = t.gx[bi] + edge, bx1 = t.gx[bi + 1] - edge, bz0 = t.gz[bj] + edge, bz1 = t.gz[bj + 1] - edge;
                float rowD = (bz1 - bz0) * 0.5f;
                String role = blockRole(town, bi, bj, ci, cj, nbx, nbz, t.id);
                switch (role) {
                    case "civic":
                        civicPart(TOWN_HALL, bx0, bz0, bx1, bz0 + rowD, 0, lib.concrete, 0.5f, 52f, 34f, rnd);
                        civicPart(POST, bx0, bz1 - rowD, (bx0 + bx1) * 0.5f, bz1, 1, lib.concrete, 1f, 24f, 22f, rnd);
                        civicPart(CLINIC, (bx0 + bx1) * 0.5f, bz1 - rowD, bx1, bz1, 1, lib.concrete, 0f, 24f, 22f, rnd);
                        break;
                    case "shops":
                        shopRow(bx0, bz0, bx1, bz0 + rowD, 0, rnd);
                        shopRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd);
                        break;
                    case "market":
                        civicPart(SUPERMARKET, bx0, bz0, bx1, bz0 + rowD, 0, lib.parking, 0.5f, 70f, Math.min(rowD, 52f), rnd);
                        houseRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd, true);
                        break;
                    case "school":
                        civicPart(SCHOOL, bx0, bz0, bx1, bz1, 0, lib.field, 0.5f, 110f, Math.min(bz1 - bz0, 96f), rnd);
                        break;
                    case "shrine":
                        civicPart(SHRINE, bx0, bz1 - rowD, bx1, bz1, 1, lib.gravel, 0.5f, 56f, Math.min(rowD, 56f), rnd);
                        houseRow(bx0, bz0, bx1, bz0 + rowD, 0, rnd, true);
                        break;
                    case "village_centre":
                        shopRow(bx0, bz0, bx1 - 14f, bz0 + rowD, 0, rnd);
                        civic(POST, bx1 - 14f, bz0, bx1, bz0 + rowD, 0, lib.concrete, rnd);
                        houseRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd, false);
                        break;
                    default:
                        boolean gas = role.equals("gas");
                        boolean kon = role.equals("konbini");
                        float cut = bx0;
                        if (gas) { civic(GAS, bx0, bz0, bx0 + 26f, bz0 + rowD, 0, lib.concrete, rnd); cut = bx0 + 26f; }
                        if (kon) { civic(TOWN_KONBINI, bx0, bz0, bx0 + 22f, bz0 + rowD, 0, lib.parking, rnd); cut = bx0 + 22f; }
                        houseRow(cut, bz0, bx1, bz0 + rowD, 0, rnd, true);
                        houseRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd, true);
                        break;
                }
            }
    }

    private static String blockRole(boolean town, int bi, int bj, int ci, int cj, int nbx, int nbz, int id) {
        if (town) {
            if (bi == ci - 1 && bj == cj - 1) return "civic";
            if (bi == ci && bj == cj - 1) return "shops";
            if (bi == ci - 1 && bj == cj) return "market";
            if (bi == ci && bj == cj) return "konbini";
            int corner = id & 3;
            int sx = (corner & 1) == 0 ? 0 : nbx - 1, sz = (corner & 2) == 0 ? 0 : nbz - 1;
            if (bi == sx && bj == sz) return "school";
            if (bi == nbx - 1 - sx && bj == nbz - 1 - sz) return "shrine";
            if (bi == nbx - 1 - sx && bj == sz) return "gas";
            return "houses";
        }
        if (bi == 0 && bj == 0) return "village_centre";
        if (bi == nbx - 1 && bj == nbz - 1) return "shrine";
        if (bi == nbx - 1 && bj == 0) return "konbini";
        return "houses";
    }

    /** Lots of 11-15 m along a row; each gets a house design (sometimes a small apartment block). */
    private void houseRow(float x0, float z0, float x1, float z1, int face, Random rnd, boolean apartments) {
        float x = x0;
        Site previous = null;
        while (x1 - x > 9.5f) {
            float w = 11f + rnd.nextFloat() * 4f;
            if (x1 - x - w < 9.5f) w = x1 - x;
            float lx0 = x, lx1 = x + w;
            x += w;
            if (!lotClear(lx0, z0, lx1, z1)) continue;
            Site st = new Site();
            st.x0 = lx0; st.z0 = z0; st.x1 = lx1; st.z1 = z1;
            st.face = face;
            st.seed = rnd.nextInt();
            st.rx = Float.NaN;
            if (apartments && rnd.nextFloat() < 0.07f && w > 12f) {
                st.kind = APAATO;
                st.ground = new ArrayList<>();
                // a row of bays between the street and the building (set back 6 m), driven into from the street
                float[] cp = local(st, 0f, 0f, w, 6f);
                st.ground.add(new float[]{cp[0], cp[1], cp[2], cp[3], lib.parking, face});
            } else {
                st.kind = LOT;
                st.design = rnd.nextInt(HouseGen.SUBURB_DESIGNS);
                st.ground = lotGround(st);
            }
            if (st.kind == LOT && previous != null && previous.kind == LOT && Math.abs(previous.x1 - st.x0) < 0.01f) {
                st.westNeighbor = true;
            }
            add(st);
            previous = st.kind == LOT ? st : null;
        }
    }

    /** Shops two or three storeys tall, 8-11 m frontages, built to the pavement. */
    private void shopRow(float x0, float z0, float x1, float z1, int face, Random rnd) {
        float x = x0;
        float depth = Math.min(z1 - z0, 13f);
        while (x1 - x > 7.5f) {
            float w = 8f + rnd.nextFloat() * 3f;
            if (x1 - x - w < 7.5f) w = x1 - x;
            float lx0 = x, lx1 = x + w;
            x += w;
            float sz0 = face == 0 ? z0 + 1f : z1 - depth, sz1 = face == 0 ? z0 + depth : z1 - 1f;
            if (!lotClear(lx0, sz0, lx1, sz1)) continue;
            Site st = new Site();
            st.x0 = lx0 + 0.2f; st.z0 = sz0; st.x1 = lx1 - 0.2f; st.z1 = sz1;
            st.kind = SHOP; st.face = face; st.seed = rnd.nextInt(); st.rx = Float.NaN;
            st.ground = new ArrayList<>();
            st.ground.add(new float[]{lx0, face == 0 ? z0 : sz1 - 0.5f, lx1, face == 0 ? sz0 + 0.5f : z1, lib.pavers});
            add(st);
        }
    }

    /**
     * A civic building on part of a row: a plot {@code w} wide (placed at {@code at} 0 = left,
     * 0.5 = middle, 1 = right of the row) and {@code d} deep from the street; the rest of the row
     * becomes houses, so no block turns into one huge car park or concrete apron.
     */
    private void civicPart(int kind, float x0, float z0, float x1, float z1, int face, int groundTex, float at,
                           float w, float d, Random rnd) {
        float rw = x1 - x0;
        w = Math.min(w, rw);
        float px0 = x0 + (rw - w) * at, px1 = px0 + w;
        float pz0 = face == 0 ? z0 : z1 - d, pz1 = face == 0 ? z0 + d : z1;
        boolean ok = civic(kind, px0, pz0, px1, pz1, face, groundTex, rnd);
        if (!ok) { houseRow(x0, z0, x1, z1, face, rnd, true); return; }
        if (px0 - x0 > 10f) houseRow(x0, z0, px0, z1, face, rnd, true);
        if (x1 - px1 > 10f) houseRow(px1, z0, x1, z1, face, rnd, true);
    }

    private boolean civic(int kind, float x0, float z0, float x1, float z1, int face, int groundTex, Random rnd) {
        if (!lotClear(x0, z0, x1, z1)) return false;
        Site st = new Site();
        st.x0 = x0; st.z0 = z0; st.x1 = x1; st.z1 = z1;
        st.kind = kind; st.face = face; st.seed = rnd.nextInt(); st.rx = Float.NaN;
        st.ground = new ArrayList<>();
        float w = st.face < 2 ? x1 - x0 : z1 - z0, d = st.face < 2 ? z1 - z0 : x1 - x0;
        if (groundTex == lib.parking || kind == TOWN_HALL) {
            // the car park lies between the street and the building, open to the street; the building
            // stands on a concrete apron behind it
            float back = kind == SUPERMARKET ? 22f : kind == TOWN_KONBINI ? d - 7f : kind == TOWN_HALL ? d - 7f : 0f;
            float[] cp = local(st, 0f, 0f, w, d - back), ap = local(st, 0f, d - back, w, d);
            st.ground.add(new float[]{cp[0], cp[1], cp[2], cp[3], lib.parking, face});
            if (back > 0f) st.ground.add(new float[]{ap[0], ap[1], ap[2], ap[3], lib.concrete});
        } else st.ground.add(new float[]{x0, z0, x1, z1, groundTex});
        if (kind == SCHOOL) st.ground.add(face == 0 ? new float[]{x0, z0, x1, z0 + 15f, lib.concrete} : new float[]{x0, z1 - 15f, x1, z1, lib.concrete});
        add(st);
        return true;
    }

    /** Lot ground: a concrete drive in front of the carport (or a path to the door). */
    private List<float[]> lotGround(Site st) {
        List<float[]> g = new ArrayList<>();
        HouseGen.Design d = HouseGen.design(lib, false, st.design);
        float W = st.face < 2 ? st.x1 - st.x0 : st.z1 - st.z0, D = st.face < 2 ? st.z1 - st.z0 : st.x1 - st.x0;
        float[] lay = HouseGen.layout(d, W, D, true);
        float u0, u1;
        if (!Float.isNaN(lay[4])) { u0 = lay[4]; u1 = lay[5]; }
        else { u0 = d.leftDoor ? lay[2] - 3.2f : lay[0] + 0.2f; u1 = u0 + 3f; }
        float v1 = Float.isNaN(lay[4]) ? lay[1] : Math.min(D - 1f, lay[1] + 5.4f);
        float[] q = local(st, u0, 0f, u1, v1);
        float x0 = q[0], z0 = q[1], x1 = q[2], z1 = q[3];
        g.add(new float[]{x0, z0, x1, z1, lib.concrete});
        return g;
    }

    /** A lot is usable if no road crosses it and the ground is gentle. */
    private boolean lotClear(float x0, float z0, float x1, float z1) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        // one query at the centre clears the whole lot when the nearest road is beyond its corners
        float half = (float) Math.hypot(x1 - x0, z1 - z0) * 0.5f;
        boolean open = roads.roadClearance((x0 + x1) * 0.5f, (z0 + z1) * 0.5f, half + 1f) > half + 0.5f;
        // the four corners and the middle; the natural ground is enough to judge the slope
        for (int k = 0; k < 5; k++) {
            float fi = k == 4 ? 0.5f : (k & 1), fj = k == 4 ? 0.5f : (k >> 1);
            float x = x0 + 1f + (x1 - x0 - 2f) * fi, z = z0 + 1f + (z1 - z0 - 2f) * fj;
            if (!open && roads.roadClearance(x, z, 20f) < 0.5f) return false;
            float h = terrain.natural(x, z);
            lo = Math.min(lo, h); hi = Math.max(hi, h);
        }
        return hi - lo < 3f;
    }

    // ------------------------------------------------------------------ building

    void generate(Chunk ch, GeoBuilder gb, int cx, int cz) {
        List<Site> list = sites.get(ChunkManager.key(cx, cz));
        if (list == null) return;
        for (Site s : list) build(ch, gb, s);
    }

    private void build(Chunk ch, GeoBuilder gb, Site s) {
        Random r = new Random(s.seed);
        boolean[] street = new boolean[4];
        street[s.face] = true;
        switch (s.kind) {
            case FARM: farm(ch, gb, s, r); break;
            case KONBINI:
                yard(gb, s.x0 - 3f, s.z0 - 3f, s.x1 + 3f, s.z1 + 3f, lib.parking);
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.KONBINI, s.x0, s.z0, s.x1, s.z1, street, street, city.shopsKonbini);
                break;
            case APAATO: {
                float ax0 = s.x0, ax1 = s.x1, az0 = s.z0, az1 = s.z1;
                if (s.groundPacked != null) {      // on a town lot: set back behind a car park
                    ax0 += 1f; ax1 -= 1f;
                    if (s.face == 0) { az0 += 6f; az1 = Math.min(az1 - 1f, az0 + 10f); } else { az1 -= 6f; az0 = Math.max(az0 + 1f, az1 - 10f); }
                }
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.APAATO, ax0, az0, ax1, az1, street, new boolean[4], city.shopsSuburb);
                break;
            }
            case LOT:
                new BuildingGen(city, lib, gb, r, ch).lotHouse(false, s.design, s.x0, s.z0, s.x1, s.z1, s.face,
                        s.westNeighbor);
                garden(ch, s, r);
                break;
            case SHOP:
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.SHOPHOUSE, s.x0, s.z0, s.x1, s.z1, street, street, city.shopsSuburb);
                break;
            case TOWN_KONBINI:
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.KONBINI, s.x0 + 3f, s.face == 0 ? s.z0 + 7f : s.z0 + 1f,
                        s.x1 - 2f, s.face == 0 ? s.z1 - 1f : s.z1 - 7f, street, street, city.shopsKonbini);
                break;
            case HARBOUR: {
                float qx = (s.x0 + s.x1) * 0.5f, qz = (s.z0 + s.z1) * 0.5f;
                float[] h = null;
                for (float[] hh : roads.harbours) if (Math.abs(hh[0] - qx) < 0.1f && Math.abs(hh[1] - qz) < 0.1f) h = hh;
                if (h != null) new TownGen(city, lib, gb, r, ch).harbour(h[0], h[1], h[2], h[3], h[4], h[5]);
                break;
            }
            case TOWN_HALL: case POST: case CLINIC: case SUPERMARKET: case SCHOOL: case SHRINE: case GAS:
                new TownGen(city, lib, gb, r, ch).build(s.kind, s.x0, s.z0, s.x1, s.z1, s.face);
                break;
            default:
                new BuildingGen(city, lib, gb, r, ch).build(r.nextInt(3) == 0 ? BuildingGen.Type.FARMHOUSE : BuildingGen.Type.HOUSE,
                        s.x0, s.z0, s.x1, s.z1, street, new boolean[4], city.shopsSuburb);
                garden(ch, s, r);
                break;
        }
    }

    /** Inverse of {@link #local}: a world rectangle {x0, z0, x1, z1} to {u0, v0, u1, v1}. */
    private static float[] toLocal(Site s, float[] r) {
        switch (s.face) {
            case 0: return new float[]{r[0] - s.x0, r[1] - s.z0, r[2] - s.x0, r[3] - s.z0};
            case 1: return new float[]{s.x1 - r[2], s.z1 - r[3], s.x1 - r[0], s.z1 - r[1]};
            case 2: return new float[]{s.z1 - r[3], r[0] - s.x0, s.z1 - r[1], r[2] - s.x0};
            default: return new float[]{r[1] - s.z0, s.x1 - r[2], r[3] - s.z0, s.x1 - r[0]};
        }
    }

    /** Maps a rectangle given in compound-local (u across the frontage, v away from the road) to world x0,z0,x1,z1. */
    private static float[] local(Site s, float u0, float v0, float u1, float v1) {
        float w = s.face < 2 ? s.x1 - s.x0 : s.z1 - s.z0;   // frontage length
        float d = s.face < 2 ? s.z1 - s.z0 : s.x1 - s.x0;   // depth
        u0 = Math.max(0f, Math.min(w, u0)); u1 = Math.max(0f, Math.min(w, u1));
        v0 = Math.max(0f, Math.min(d, v0)); v1 = Math.max(0f, Math.min(d, v1));
        switch (s.face) {
            case 0: return new float[]{s.x0 + u0, s.z0 + v0, s.x0 + u1, s.z0 + v1};          // road to the north
            case 1: return new float[]{s.x1 - u1, s.z1 - v1, s.x1 - u0, s.z1 - v0};          // road to the south
            case 2: return new float[]{s.x0 + v0, s.z1 - u1, s.x0 + v1, s.z1 - u0};          // road to the west
            default: return new float[]{s.x1 - v1, s.z0 + u0, s.x1 - v0, s.z0 + u1};         // road to the east
        }
    }

    private void farm(Chunk ch, GeoBuilder gb, Site s, Random r) {
        float w = s.face < 2 ? s.x1 - s.x0 : s.z1 - s.z0;
        boolean[] street = new boolean[4];
        street[s.face] = true;
        // gravel yard and a driveway out to the road
        float[] yd = local(s, w * 0.25f, 4f, w * 0.75f, 14f);
        yard(gb, yd[0], yd[1], yd[2], yd[3], lib.gravel);
        // farmhouse at the back of the yard, barn to one side, greenhouse on the other
        float hx = w * 0.5f + (r.nextFloat() - 0.5f) * 4f;
        float[] hs = local(s, hx - 6.5f, 14f, hx + 6.5f, 23f);
        new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.FARMHOUSE, hs[0], hs[1], hs[2], hs[3], street, new boolean[4], city.shopsSuburb);
        boolean barnLeft = r.nextBoolean();
        float[] bs = barnLeft ? local(s, 1f, 5f, 7.5f, 12f) : local(s, w - 7.5f, 5f, w - 1f, 12f);
        new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.SHED, bs[0], bs[1], bs[2], bs[3], new boolean[4], new boolean[4], city.shopsSuburb);
        if (r.nextInt(3) > 0) {
            float[] gs = barnLeft ? local(s, w - 7f, 1.5f, w - 1f, 13f) : local(s, 1f, 1.5f, 7f, 13f);
            new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.GREENHOUSE, gs[0], gs[1], gs[2], gs[3], new boolean[4], new boolean[4], city.shopsSuburb);
        }
        // shelter trees behind and a bamboo clump
        Flora[] trees = {Flora.CAMPHOR, Flora.BLACK_PINE, Flora.ZELKOVA, Flora.MAPLE, Flora.CEDAR, Flora.SAKURA};
        for (int k = 0; k < 3 + r.nextInt(3); k++) {
            float u = 2f + r.nextFloat() * (w - 4f), v = 23.5f + r.nextFloat() * 2f;
            float[] tp = local(s, u, v, u, v);
            city.plantAtTerrain(ch, trees[r.nextInt(trees.length)], tp[0], tp[1]);
        }
        float[] bp = local(s, barnLeft ? w - 3f : 3f, 21f, barnLeft ? w - 3f : 3f, 21f);
        city.plantAtTerrain(ch, Flora.BAMBOO, bp[0], bp[1]);
        float[] sp = local(s, w * 0.5f - 7f, 3f, w * 0.5f - 7f, 3f);
        city.plantAtTerrain(ch, r.nextBoolean() ? Flora.CAMELLIA : Flora.HYDRANGEA, sp[0], sp[1]);
    }

    private void garden(Chunk ch, Site s, Random r) {
        if (s.kind == LOT) {
            Flora[] plants = {Flora.BLACK_PINE, Flora.MAPLE, Flora.CAMELLIA, Flora.BOXWOOD, Flora.HYDRANGEA, Flora.AZALEA, Flora.SAKURA};
            int n = 1 + r.nextInt(2);
            for (int k = 0; k < n; k++) {
                float x = s.x0 + 1.2f + r.nextFloat() * (s.x1 - s.x0 - 2.4f);
                float z = s.face == 0 ? s.z1 - 1.4f : s.z0 + 1.4f;     // back garden
                city.plantAtTerrain(ch, plants[r.nextInt(plants.length)], x, z);
            }
            return;
        }
        Flora[] plants = {Flora.BLACK_PINE, Flora.MAPLE, Flora.CAMELLIA, Flora.BOXWOOD, Flora.HYDRANGEA, Flora.AZALEA, Flora.SAKURA};
        int n = 1 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            float x = r.nextBoolean() ? s.x0 - 2.5f : s.x1 + 2.5f;
            float z = s.z0 + r.nextFloat() * (s.z1 - s.z0);
            city.plantAtTerrain(ch, plants[r.nextInt(plants.length)], x, z);
        }
    }

    /** Yards are painted into the ground map (see {@link #yards}), not built. */
    private void yard(GeoBuilder gb, float x0, float z0, float x1, float z1, int tex) { }

    /** A yard: its rectangle, texture, and (car parks) the side facing the street, 0 north 1 south 2 west 3 east, or -1. */
    interface YardSink { void yard(float x0, float z0, float x1, float z1, int tex, int face); }

    /** Every gravel yard and car park overlapping the area, for the ground painter. */
    void yards(float ax0, float az0, float ax1, float az1, YardSink sink) {
        int c0 = ChunkManager.chunkOf(ax0) - 1, c1 = ChunkManager.chunkOf(ax1) + 1;
        int d0 = ChunkManager.chunkOf(az0) - 1, d1 = ChunkManager.chunkOf(az1) + 1;
        for (int cz = d0; cz <= d1; cz++)
            for (int cx = c0; cx <= c1; cx++) {
                List<Site> list = sites.get(ChunkManager.key(cx, cz));
                if (list == null) continue;
                for (Site s : list) {
                    if (s.groundPacked != null) {
                        float[] g = s.groundPacked;
                        for (int i = 0; i < g.length; i += 6)
                            if (!(g[i + 2] < ax0 || g[i] > ax1 || g[i + 3] < az0 || g[i + 1] > az1)) sink.yard(g[i], g[i + 1], g[i + 2], g[i + 3], (int) g[i + 4], (int) g[i + 5]);
                        continue;
                    }
                    float[] r;
                    int t;
                    if (s.kind == FARM) {
                        float w = s.face < 2 ? s.x1 - s.x0 : s.z1 - s.z0;
                        r = local(s, w * 0.25f, 4f, w * 0.75f, 14f);
                        t = lib.gravel;
                    } else if (s.kind == KONBINI) {
                        // the convenience store's car park: a row of bays along its front
                        r = s.face == 0 ? new float[]{s.x0 - 3f, s.z0 - 7f, s.x1 + 3f, s.z0}
                                : s.face == 1 ? new float[]{s.x0 - 3f, s.z1, s.x1 + 3f, s.z1 + 7f}
                                : s.face == 2 ? new float[]{s.x0 - 7f, s.z0 - 3f, s.x0, s.z1 + 3f}
                                : new float[]{s.x1, s.z0 - 3f, s.x1 + 7f, s.z1 + 3f};
                        t = lib.parking;
                    } else continue;
                    if (r[2] < ax0 || r[0] > ax1 || r[3] < az0 || r[1] > az1) continue;
                    sink.yard(r[0], r[1], r[2], r[3], t, t == lib.parking ? s.face : -1);
                }
            }
    }

    /** Gravel track from the yard to the road edge. */
    private void driveway(GeoBuilder gb, Site s, float w) {
        if (Float.isNaN(s.rx)) return;
        float[] a = local(s, w * 0.5f - 1.6f, 0f, w * 0.5f + 1.6f, 4f);
        float sx = (a[0] + a[2]) * 0.5f, sz = (a[1] + a[3]) * 0.5f;
        float dx = s.rx - sx, dz = s.rz - sz, len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1f) return;
        float ux = dx / len, uz = dz / len, nx = -uz * 1.6f, nz = ux * 1.6f;
        int n = Math.max(1, (int) Math.ceil(len / 4f));
        float[] p = new float[12], t = new float[8], l = new float[4];
        float lt = GeoBuilder.sunLight(0, 1, 0);
        for (int k = 0; k < n; k++) {
            float a0 = len * k / n, a1 = len * (k + 1) / n;
            float x0 = sx + ux * a0, z0 = sz + uz * a0, x1 = sx + ux * a1, z1 = sz + uz * a1;
            p[0] = x0 - nx; p[2] = z0 - nz; p[1] = terrain.height(p[0], p[2]) + 0.06f;
            p[3] = x0 + nx; p[5] = z0 + nz; p[4] = terrain.height(p[3], p[5]) + 0.06f;
            p[6] = x1 + nx; p[8] = z1 + nz; p[7] = terrain.height(p[6], p[8]) + 0.06f;
            p[9] = x1 - nx; p[11] = z1 - nz; p[10] = terrain.height(p[9], p[11]) + 0.06f;
            t[0] = 0; t[1] = a0 / 2f; t[2] = 1.6f; t[3] = a0 / 2f; t[4] = 1.6f; t[5] = a1 / 2f; t[6] = 0; t[7] = a1 / 2f;
            l[0] = l[1] = l[2] = l[3] = lt;
            gb.quad(p, t, l, lib.gravel, L_OBJECT, Geo.FLAG_SORT_FAR, -1.2f, true);
        }
    }
}
