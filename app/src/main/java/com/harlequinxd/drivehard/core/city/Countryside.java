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

    static final int FARM = 0, HOUSE = 1, APAATO = 2, KONBINI = 3, LOT = 4, TOWN_HALL = 5, POST = 6, CLINIC = 7, SHOP = 8,
            SUPERMARKET = 9, SCHOOL = 10, SHRINE = 11, GAS = 12, TOWN_KONBINI = 13;

    /** A building site: footprint, kind, the side facing the road (0=N,1=S,2=W,3=E), driveway end. */
    static final class Site {
        float x0, z0, x1, z1;
        int kind, face, seed, design;
        float rx, rz;   // driveway end at the road edge
        /** Painted ground areas {x0, z0, x1, z1, texture}. */
        List<float[]> ground;
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
        if (inTown(cx + 4f, cz + 4f, 20f)) return Crop.NONE;
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
                        civic(TOWN_HALL, bx0, bz0, bx1, bz0 + rowD, 0, lib.concrete, rnd);
                        civic(POST, bx0, bz1 - rowD, (bx0 + bx1) * 0.5f, bz1, 1, lib.concrete, rnd);
                        civic(CLINIC, (bx0 + bx1) * 0.5f, bz1 - rowD, bx1, bz1, 1, lib.concrete, rnd);
                        break;
                    case "shops":
                        shopRow(bx0, bz0, bx1, bz0 + rowD, 0, rnd);
                        shopRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd);
                        break;
                    case "market":
                        if (!civic(SUPERMARKET, bx0, bz0, bx1, bz1, 0, lib.parking, rnd)) {
                            houseRow(bx0, bz0, bx1, bz0 + rowD, 0, rnd, true);
                            houseRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd, true);
                        }
                        break;
                    case "school":
                        if (!civic(SCHOOL, bx0, bz0, bx1, bz1, 0, lib.field, rnd)) {
                            houseRow(bx0, bz0, bx1, bz0 + rowD, 0, rnd, true);
                            houseRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd, true);
                        }
                        break;
                    case "shrine":
                        if (!civic(SHRINE, bx0, bz0, bx1, bz1, 1, lib.gravel, rnd)) {
                            houseRow(bx0, bz0, bx1, bz0 + rowD, 0, rnd, true);
                            houseRow(bx0, bz1 - rowD, bx1, bz1, 1, rnd, true);
                        }
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
                st.ground.add(new float[]{lx0, z0, lx1, z1, lib.parking});
            } else {
                st.kind = LOT;
                st.design = rnd.nextInt(HouseGen.SUBURB_DESIGNS);
                st.ground = lotGround(st);
            }
            add(st);
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

    private boolean civic(int kind, float x0, float z0, float x1, float z1, int face, int groundTex, Random rnd) {
        if (!lotClear(x0, z0, x1, z1)) return false;
        Site st = new Site();
        st.x0 = x0; st.z0 = z0; st.x1 = x1; st.z1 = z1;
        st.kind = kind; st.face = face; st.seed = rnd.nextInt(); st.rx = Float.NaN;
        st.ground = new ArrayList<>();
        st.ground.add(new float[]{x0, z0, x1, z1, groundTex});
        if (kind == SCHOOL) st.ground.add(new float[]{x0, z0, x1, z0 + 15f, lib.concrete});
        if (kind == SUPERMARKET) st.ground.add(new float[]{x0, z1 - 22f, x1, z1, lib.concrete});
        add(st);
        return true;
    }

    /** Lot ground: a concrete drive in front of the carport (or a path to the door). */
    private List<float[]> lotGround(Site st) {
        List<float[]> g = new ArrayList<>();
        HouseGen.Design d = HouseGen.design(lib, false, st.design);
        float W = st.x1 - st.x0, D = st.z1 - st.z0;
        float[] lay = HouseGen.layout(d, W, D, true);
        float u0, u1;
        if (!Float.isNaN(lay[4])) { u0 = lay[4]; u1 = lay[5]; }
        else { u0 = d.leftDoor ? lay[2] - 3.2f : lay[0] + 0.2f; u1 = u0 + 3f; }
        float v1 = Float.isNaN(lay[4]) ? lay[1] : Math.min(D - 1f, lay[1] + 5.4f);
        // local u along the frontage: rows face N (u = x - x0) or S (u = x1 - x)
        float x0 = st.face == 0 ? st.x0 + u0 : st.x1 - u1, x1 = st.face == 0 ? st.x0 + u1 : st.x1 - u0;
        float z0 = st.face == 0 ? st.z0 : st.z1 - v1, z1 = st.face == 0 ? st.z0 + v1 : st.z1;
        g.add(new float[]{x0, z0, x1, z1, lib.concrete});
        return g;
    }

    /** A lot is usable if no road crosses it and the ground is gentle. */
    private boolean lotClear(float x0, float z0, float x1, float z1) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int j = 0; j <= 2; j++)
            for (int i = 0; i <= 2; i++) {
                float x = x0 + 1f + (x1 - x0 - 2f) * i / 2f, z = z0 + 1f + (z1 - z0 - 2f) * j / 2f;
                if (roads.roadClearance(x, z, 20f) < 0.5f) return false;
                float h = terrain.height(x, z);
                lo = Math.min(lo, h); hi = Math.max(hi, h);
            }
        return hi - lo < 4f;
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
                if (s.ground != null) {      // on a town lot: set back behind a car park
                    ax0 += 1f; ax1 -= 1f;
                    if (s.face == 0) { az0 += 6f; az1 -= 1f; } else { az0 += 1f; az1 -= 6f; }
                }
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.APAATO, ax0, az0, ax1, az1, street, new boolean[4], city.shopsSuburb);
                break;
            }
            case LOT:
                new BuildingGen(city, lib, gb, r, ch).lotHouse(false, s.design, s.x0, s.z0, s.x1, s.z1, s.face);
                garden(ch, s, r);
                break;
            case SHOP:
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.SHOPHOUSE, s.x0, s.z0, s.x1, s.z1, street, street, city.shopsSuburb);
                break;
            case TOWN_KONBINI:
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.KONBINI, s.x0 + 3f, s.face == 0 ? s.z0 + 7f : s.z0 + 1f,
                        s.x1 - 2f, s.face == 0 ? s.z1 - 1f : s.z1 - 7f, street, street, city.shopsKonbini);
                break;
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

    interface YardSink { void yard(float x0, float z0, float x1, float z1, int tex); }

    /** Every gravel yard and car park overlapping the area, for the ground painter. */
    void yards(float ax0, float az0, float ax1, float az1, YardSink sink) {
        int c0 = ChunkManager.chunkOf(ax0) - 1, c1 = ChunkManager.chunkOf(ax1) + 1;
        int d0 = ChunkManager.chunkOf(az0) - 1, d1 = ChunkManager.chunkOf(az1) + 1;
        for (int cz = d0; cz <= d1; cz++)
            for (int cx = c0; cx <= c1; cx++) {
                List<Site> list = sites.get(ChunkManager.key(cx, cz));
                if (list == null) continue;
                for (Site s : list) {
                    if (s.ground != null) {
                        for (float[] g : s.ground)
                            if (!(g[2] < ax0 || g[0] > ax1 || g[3] < az0 || g[1] > az1)) sink.yard(g[0], g[1], g[2], g[3], (int) g[4]);
                        continue;
                    }
                    float[] r;
                    int t;
                    if (s.kind == FARM) {
                        float w = s.face < 2 ? s.x1 - s.x0 : s.z1 - s.z0;
                        r = local(s, w * 0.25f, 4f, w * 0.75f, 14f);
                        t = lib.gravel;
                    } else if (s.kind == KONBINI) {
                        r = new float[]{s.x0 - 3f, s.z0 - 3f, s.x1 + 3f, s.z1 + 3f};
                        t = lib.parking;
                    } else continue;
                    if (r[2] < ax0 || r[0] > ax1 || r[3] < az0 || r[1] > az1) continue;
                    sink.yard(r[0], r[1], r[2], r[3], t);
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
