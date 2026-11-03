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
public final class Countryside {
    /** Field parcel size (metres), multiples of the 8 m terrain cell. */
    public static final int PARCEL_W = 40, PARCEL_D = 24;

    public enum Crop { NONE, PADDY_WATER, PADDY_GREEN, PADDY_GOLD, CABBAGE, SOIL, FALLOW, TEA }

    private final City city;
    private final Terrain terrain;
    private final RoadNetwork roads;
    private final TextureLibrary lib;
    private final int seed;

    static final int FARM = 0, HOUSE = 1, APAATO = 2, KONBINI = 3;

    /** A building site: footprint, kind, the side facing the road (0=N,1=S,2=W,3=E), driveway end. */
    static final class Site {
        float x0, z0, x1, z1;
        int kind, face, seed;
        float rx, rz;   // driveway end at the road edge
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
        if (terrain.cityDistance(x, z) < 900f || terrain.coastOver(x, z) > -160f) return false;
        return vn(x / 2600f, z / 2600f, seed + 950) > 0.33f;
    }

    /** Crop of the 8 m cell at (cx,cz) (its min corner), given the cell's average height and slope. */
    public Crop crop(float cx, float cz, float h, float slope) {
        if (!farmland(cx + 4f, cz + 4f, h)) return Crop.NONE;
        int px = (int) Math.floor(cx / PARCEL_W), pz = (int) Math.floor(cz / PARCEL_D);
        if (slope > 0.16f) return Crop.NONE;
        if (slope > 0.07f) return Noise.rnd(px, pz, seed + 960) < 0.6f ? Crop.TEA : Crop.FALLOW;
        float r = Noise.rnd(px, pz, seed + 961);
        if (r < 0.66f) {
            // each district is at the same stage of the season
            float stage = vn(cx / 2200f, cz / 2200f, seed + 962);
            return stage < 0.36f ? Crop.PADDY_WATER : (stage < 0.68f ? Crop.PADDY_GREEN : Crop.PADDY_GOLD);
        }
        if (r < 0.78f) return Crop.CABBAGE;
        if (r < 0.9f) return Crop.SOIL;
        return Crop.FALLOW;
    }

    public int texture(Crop c) {
        switch (c) {
            case PADDY_WATER: return lib.paddyWater;
            case PADDY_GREEN: return lib.paddyGreen;
            case PADDY_GOLD: return lib.paddyGold;
            case CABBAGE: return lib.cabbage;
            case SOIL: return lib.soil;
            case TEA: return lib.tea;
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

    private void leveeStrip(GeoBuilder gb, float x0, float z0, float x1, float z1, float lift, int flags) {
        gb.floorT(x0, z0, x1, z1, lift, lib.grass, 2f, 2f, L_OBJECT, flags, -0.4f, true);
    }

    // ------------------------------------------------------------------ site placement

    private final HashSet<Long> taken = new HashSet<>();

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
        // villages: houses around the junction
        for (RoadNode v : roads.villages) {
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
            case APAATO:
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.APAATO, s.x0, s.z0, s.x1, s.z1, street, new boolean[4], city.shopsSuburb);
                break;
            default:
                new BuildingGen(city, lib, gb, r, ch).build(r.nextInt(3) == 0 ? BuildingGen.Type.FARMHOUSE : BuildingGen.Type.HOUSE,
                        s.x0, s.z0, s.x1, s.z1, street, new boolean[4], city.shopsSuburb);
                garden(ch, s, r);
                break;
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
        Flora[] plants = {Flora.BLACK_PINE, Flora.MAPLE, Flora.CAMELLIA, Flora.BOXWOOD, Flora.HYDRANGEA, Flora.AZALEA, Flora.SAKURA};
        int n = 1 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            float x = r.nextBoolean() ? s.x0 - 2.5f : s.x1 + 2.5f;
            float z = s.z0 + r.nextFloat() * (s.z1 - s.z0);
            city.plantAtTerrain(ch, plants[r.nextInt(plants.length)], x, z);
        }
    }

    /** Flat-ish ground cover following the terrain. */
    private void yard(GeoBuilder gb, float x0, float z0, float x1, float z1, int tex) {
        gb.floorAreaT(x0, z0, x1, z1, 0.05f, tex, 2f, 2f, L_OBJECT, 6f, Geo.FLAG_SORT_FAR, -0.6f);
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
