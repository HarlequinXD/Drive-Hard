package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Sectors;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.World;
import com.harlequinxd.drivehard.core.model.Flora;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.road.RoadMesh;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.tex.JpTextures;
import com.harlequinxd.drivehard.core.tex.Noise;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_DECAL;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_GROUND;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_RAISED;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_WATER;

/**
 * Procedural modern-Japanese island city, generated chunk by chunk.
 *
 * The street grid and the terrain are cheap pure functions of the seed; {@link #generate}
 * builds the geometry, collision boxes and sprite props of any 64 m chunk on demand, so the
 * {@link ChunkManager} can stream the island in and out of memory.
 *
 * +X is east (toward the beach), +Z is south; vehicles keep left.
 */
public final class City extends World implements java.io.Serializable {
    /** Everything on the ground is painted flat: no kerbs. */
    public static final float CURB = 0f, SIDEWALK = 4f;
    public static final float FLOOR_H = 3.4f, SHOP_H = 4.6f;
    /** The island's big city. */
    public static final String NAME = "Minato City";

    final TextureLibrary lib;
    private final long seed;
    public final Terrain terrain;
    public final TrafficLights traffic;
    public transient ChunkManager chunks;

    public final int nbx = 100, nbz = 100;
    public final float[] rx0 = new float[nbx + 1], rx1 = new float[nbx + 1];
    public final float[] rz0 = new float[nbz + 1], rz1 = new float[nbz + 1];
    public final boolean[] avenueX = new boolean[nbx + 1], avenueZ = new boolean[nbz + 1];
    public float cityX0, cityX1, cityZ0, cityZ1;
    public float spawnX, spawnZ;
    /** The central block row and column. */
    public final int jCentre = nbz / 2, iCentre = nbx / 2;
    /** Every road on the island as a spline graph (city grid included). */
    public final RoadNetwork roads;
    public final Countryside countryside;
    final int[] shopsCentral, shopsEnt, shopsMixed, shopsSuburb, shopsSeaside, shopsKonbini, shopsPachinko;

    public City(TextureLibrary lib, long seed) { this(lib, seed, null); }

    public City(TextureLibrary lib, long seed, com.harlequinxd.drivehard.core.Game.Progress progress) {
        this.lib = lib;
        this.seed = seed;
        if (progress != null) progress.step("Laying out the city blocks", 0f);
        layout(new Random(seed));
        if (progress != null) progress.step("Shaping hills, rivers and coast", 0.02f);
        terrain = new Terrain((int) seed, cityX0, cityZ0, cityX1, cityZ1);
        if (progress != null) progress.step("Founding the suburbs", 0.20f);
        footprint(new Random(seed * 31L + 7));
        traffic = new TrafficLights(nbx + 1, (int) seed);
        if (progress != null) progress.step("Surveying highways and country roads", 0.25f);
        roads = new RoadNetwork(this, terrain, seed);
        if (progress != null) progress.step("Naming towns and suburbs", 0.85f);
        terrain.roads = roads;
        for (Suburb sb : suburbs) sb.name = roads.newPlaceName();
        shopsCentral = shopSet("CAFE", "BAKERY", "CAKES", "FASHION", "SHOES", "ELECTRONICS", "BOOKS", "BANK", "TRAVEL",
                "DRUG", "SUSHI", "TEMPURA", "KISSATEN", "MEGANE", "PHOTO", "STATIONERY", "TEA", "WAGASHI", "CLINIC");
        shopsEnt = shopSet("IZAKAYA", "YAKITORI", "BAR", "SNACK", "CLUB", "KARAOKE", "GAME CENTER", "MAHJONG", "RAMEN",
                "GYOZA", "OKONOMIYAKI", "GYUDON", "CURRY", "CAPSULE HOTEL", "HAPPY 24", "SAKE", "SUSHI", "UDON");
        shopsMixed = shopSet("RAMEN", "UDON", "SOBA", "CURRY", "GYUDON", "BENTO", "CAFE", "KISSATEN", "BAKERY", "DRUG",
                "100 YEN", "BARBER", "HAIR SALON", "COIN LAUNDRY", "FUDOSAN", "CLINIC", "DENTAL", "PHARMACY", "JUKU",
                "CYCLE", "FLOWERS", "BOOKS", "IZAKAYA", "CLOSED", "OKONOMIYAKI", "STATIONERY");
        shopsSuburb = shopSet("YAOYA", "SAKANA", "NIKU", "TOFU", "SAKE", "OKOME", "FLOWERS", "BARBER", "COIN LAUNDRY",
                "CLINIC", "DENTAL", "JUKU", "CYCLE", "SUPERMARKET", "WAGASHI", "CLOSED", "KIMONO", "SOBA", "TEA");
        shopsSeaside = shopSet("SUSHI", "SAKANA", "CAFE", "BAR", "IZAKAYA", "SAKE", "TEMPURA", "CLOSED", "CYCLE");
        shopsKonbini = shopSet("DAILY MART", "HAPPY 24", "LUCKY STORE");
        shopsPachinko = shopSet("PACHINKO", "GAME CENTER");
        if (progress != null) progress.step("Planting farms and forests", 0.88f);
        countryside = new Countryside(this, lib, seed);
        if (progress != null) progress.step("Painting road markings", 0.94f);
        streetPaint = streetPaint();
        chunks = new ChunkManager(this);
        minX = terrain.islandX0 - 9000; maxX = terrain.islandX1 + 3000; minZ = terrain.islandZ0 - 9000; maxZ = terrain.islandZ1 + 9000;
        // start downtown: on the sidewalk of the central avenue, beside the most central built-up block
        int bi = iCentre, bj = jCentre;
        float bestD = Float.MAX_VALUE;
        for (int j = 0; j < nbz; j++)
            for (int i = 0; i < nbx; i++) {
                if (!blockActive(i, j)) continue;
                float d = (i - iCentre) * (i - iCentre) + (j - jCentre) * (j - jCentre);
                if (d < bestD) { bestD = d; bi = i; bj = j; }
            }
        spawnX = rx1[bi] + SIDEWALK * 0.5f;
        spawnZ = (rz1[bj] + rz0[bj + 1]) * 0.5f;
    }

    /** Island read back from the game data cache: the chunk streamer starts empty. */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        chunks = new ChunkManager(this);
    }

    private int[] shopSet(String... labels) {
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k < lib.shops.length; k++) {
            String l = JpTextures.CATEGORIES[lib.shopCategory[k]].label;
            for (String w : labels) if (w.equals(l)) { out.add(lib.shops[k]); break; }
        }
        int[] a = new int[out.size()];
        for (int k = 0; k < a.length; k++) a[k] = out.get(k);
        return a;
    }

    private void layout(Random rnd) {
        float x = 0;
        for (int i = 0; i <= nbx; i++) {
            avenueX[i] = i % 8 == 0 && i != nbx;
            rx0[i] = x;
            x += avenueX[i] ? 22f : 13f;
            rx1[i] = x;
            if (i < nbx) x += 118 + 4 * rnd.nextInt(9);
        }
        float z = 0;
        for (int j = 0; j <= nbz; j++) {
            avenueZ[j] = j % 9 == 0;
            rz0[j] = z;
            z += avenueZ[j] ? 22f : 13f;
            rz1[j] = z;
            if (j < nbz) z += j == nbz / 2 ? 136 : 118 + 4 * rnd.nextInt(9);
        }
        cityX0 = rx0[0]; cityX1 = rx1[nbx];
        cityZ0 = rz0[0]; cityZ1 = rz1[nbz];
    }

    // ------------------------------------------------------------------ World (collision)

    private final List<Box> nearList = new ArrayList<>();

    /** Moving colliders (cars) supplied by the simulation. */
    public interface Dynamic { void boxesNear(float x, float z, List<Box> out); }
    public transient Dynamic dynamic;

    // ------------------------------------------------------------------ the urban footprint

    /** Blocks that are built up (inside the seed-shaped city outline). */
    private boolean[] active;
    /** Blocks of the city grid inside the outline (built up or the green land between suburbs). */
    private boolean[] inside;

    /** A named suburb: a cluster of residential blocks on the outskirts, with green land around it. */
    public static final class Suburb implements java.io.Serializable {
        public String name = "";
        public final float x, z, r;
        /** A street corner near its middle (fast travel, map label). */
        public float sx, sz;
        Suburb(float x, float z, float r) { this.x = x; this.z = z; this.r = r; }
    }

    public final List<Suburb> suburbs = new ArrayList<>();
    /** Normalised distance from the centre beyond which the city is suburbs. */
    public static final float CORE = 0.24f;

    /** Distance from the centre in units of the grid's half size (0 centre, about 1 at the outline). */
    private float radial(float x, float z) {
        float dx = (x - (cityX0 + cityX1) * 0.5f) / ((cityX1 - cityX0) * 0.5f);
        float dz = (z - (cityZ0 + cityZ1) * 0.5f) / ((cityZ1 - cityZ0) * 0.5f);
        return (float) Math.hypot(dx, dz);
    }

    /**
     * A realistic city: a dense core a few km across, then suburbs spread over the outskirts,
     * each a cluster of blocks with fields and woods between them, joined by the avenues.
     */
    private void footprint(Random r) {
        float half = Math.min(cityX1 - cityX0, cityZ1 - cityZ0) * 0.5f;
        // suburb centres: spaced out over the ring outside the core (inside the outline)
        for (int tries = 0; tries < 4000 && suburbs.size() < 26; tries++) {
            float x = cityX0 + r.nextFloat() * (cityX1 - cityX0), z = cityZ0 + r.nextFloat() * (cityZ1 - cityZ0);
            float rad = radial(x, z);
            if (rad < CORE + 0.08f || terrain.cityDistance(x, z) > 0f) continue;
            float rr = half * (0.11f + r.nextFloat() * 0.07f);
            boolean ok = true;
            for (Suburb o : suburbs) if (Math.hypot(o.x - x, o.z - z) < (o.r + rr) * 1.25f) { ok = false; break; }
            if (ok) suburbs.add(new Suburb(x, z, rr));
        }
        active = new boolean[nbx * nbz];
        inside = new boolean[nbx * nbz];
        for (int j = 0; j < nbz; j++)
            for (int i = 0; i < nbx; i++) {
                float cx = (rx1[i] + rx0[i + 1]) * 0.5f, cz = (rz1[j] + rz0[j + 1]) * 0.5f;
                boolean in = terrain.cityDistance(cx, cz) <= 0f;
                inside[j * nbx + i] = in;
                if (!in) continue;
                if (radial(cx, cz) < CORE + 0.05f * (float) Math.sin(i * 0.7f + j * 1.3f)) { active[j * nbx + i] = true; continue; }
                for (Suburb sb : suburbs) {
                    float d = (float) Math.hypot(sb.x - cx, sb.z - cz);
                    float wobble = 1f + 0.18f * (float) Math.sin(Math.atan2(cz - sb.z, cx - sb.x) * 3f + sb.x * 0.001f);
                    if (d < sb.r * wobble) { active[j * nbx + i] = true; break; }
                }
            }
        // each suburb's label sits on a street corner near its middle
        for (Suburb sb : suburbs) {
            int bi = 0, bj = 0;
            float best = Float.MAX_VALUE;
            for (int j = 0; j <= nbz; j++)
                for (int i = 0; i <= nbx; i++) {
                    float x = (rx0[i] + rx1[i]) * 0.5f, z = (rz0[j] + rz1[j]) * 0.5f;
                    float d = (x - sb.x) * (x - sb.x) + (z - sb.z) * (z - sb.z);
                    if (d < best && (blockActive(i, j) || blockActive(i - 1, j) || blockActive(i, j - 1) || blockActive(i - 1, j - 1))) {
                        best = d; bi = i; bj = j;
                    }
                }
            sb.sx = (rx0[bi] + rx1[bi]) * 0.5f;
            sb.sz = (rz0[bj] + rz1[bj]) * 0.5f;
        }
    }

    /** The suburb at (x, z), or null (the core, or countryside). */
    public Suburb suburbAt(float x, float z) {
        if (radial(x, z) < CORE + 0.06f) return null;
        Suburb best = null;
        float bd = Float.MAX_VALUE;
        for (Suburb sb : suburbs) {
            float d = (float) Math.hypot(sb.x - x, sb.z - z) / sb.r;
            if (d < bd) { bd = d; best = sb; }
        }
        return bd < 1.35f ? best : null;
    }

    private boolean insideGrid(int i, int j) { return i >= 0 && j >= 0 && i < nbx && j < nbz && inside[j * nbx + i]; }

    public boolean blockActive(int i, int j) {
        return i >= 0 && j >= 0 && i < nbx && j < nbz && active[j * nbx + i];
    }

    /** N-S street segment at road column i along block row j: exists if a built-up block is beside it. */
    public boolean nsStreet(int i, int j) {
        // avenues run on through the green land between suburbs and join them up
        return blockActive(i - 1, j) || blockActive(i, j) || (avenueX[i] && (insideGrid(i - 1, j) || insideGrid(i, j)));
    }

    /** E-W street segment at road row j along block column i. */
    public boolean ewStreet(int i, int j) {
        return blockActive(i, j - 1) || blockActive(i, j) || (avenueZ[j] && (insideGrid(i, j - 1) || insideGrid(i, j)));
    }

    /** Position along an axis: block index (>= 0) or -(road index) - 1, given road start/end arrays. */
    private static int slot(float v, float[] r0, float[] r1, int n) {
        int lo = 0, hi = n;                       // last road index with r0 <= v
        while (lo < hi) { int mid = (lo + hi + 1) >>> 1; if (r0[mid] <= v) lo = mid; else hi = mid - 1; }
        return v < r1[lo] ? -lo - 1 : lo;
    }

    /**
     * True where the ground belongs to the city: a built-up block, or a street beside one. The
     * city's grid rectangle is much larger than its outline; outside the outline it is countryside.
     */
    public boolean urban(float x, float z) {
        if (x < cityX0 || x > cityX1 || z < cityZ0 || z > cityZ1) return false;
        int sx = slot(x, rx0, rx1, nbx), sz = slot(z, rz0, rz1, nbz);
        if (sx >= 0 && sx >= nbx || sz >= 0 && sz >= nbz) return false;
        if (sx >= 0 && sz >= 0) return blockActive(sx, sz);
        if (sx < 0 && sz >= 0) return nsStreet(-sx - 1, sz);
        if (sx >= 0) return ewStreet(sx, -sz - 1);
        int i = -sx - 1, j = -sz - 1;
        return nsStreet(i, j - 1) || nsStreet(i, j) || ewStreet(i - 1, j) || ewStreet(i, j);
    }

    /** Within {@code margin} metres of the city's ground (a few samples). */
    public boolean nearUrban(float x, float z, float margin) {
        return urban(x, z) || urban(x - margin, z) || urban(x + margin, z) || urban(x, z - margin) || urban(x, z + margin);
    }

    @Override
    public List<Box> near(float x, float z) {
        nearList.clear();
        int cx = ChunkManager.chunkOf(x), cz = ChunkManager.chunkOf(z);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                Chunk c = chunks.get(cx + dx, cz + dz);
                if (c == null) continue;
                for (Box b : c.boxes)
                    if (x > b.minX - 1.5f && x < b.maxX + 1.5f && z > b.minZ - 1.5f && z < b.maxZ + 1.5f) nearList.add(b);
            }
        if (dynamic != null) dynamic.boxesNear(x, z, nearList);
        return nearList;
    }

    @Override
    public float surface(float x, float z) {
        float b = roads.bridgeHeight(x, z);
        if (!Float.isNaN(b)) return b;
        return terrain.height(x, z) + (isRaised(x, z) ? CURB : 0f);
    }

    @Override
    public float surface(float x, float z, float y) {
        float b = roads.bridgeHeight(x, z);
        if (!Float.isNaN(b) && y > b - 2f) return b;               // on (or stepping onto) the deck
        return terrain.height(x, z) + (isRaised(x, z) ? CURB : 0f);  // under it: the road below
    }

    @Override
    public boolean blocked(float x, float z) {
        return terrain.height(x, z) < -0.5f;
    }

    /** How far out a swimmer may go: about 200 m from the shore. */
    public static final float SWIM_REACH = 200f;

    @Override public boolean swimBlocked(float x, float z) {
        return terrain.coastOver(x, z) > SWIM_REACH;
    }

    /** Sidewalks/blocks and avenue medians sit one curb height above the road. */
    public boolean isRaised(float x, float z) {
        if (CURB == 0f) return false;
        if (!urban(x, z)) return false;
        int bi = -1, bj = -1;
        for (int i = 0; i < nbx; i++) if (x > rx1[i] && x < rx0[i + 1]) { bi = i; break; }
        for (int j = 0; j < nbz; j++) if (z > rz1[j] && z < rz0[j + 1]) { bj = j; break; }
        if (bi >= 0 && bj >= 0) return true;
        if (bi < 0 && bj >= 0) {   // on a N-S road
            for (int i = 0; i <= nbx; i++) {
                if (!avenueX[i]) continue;
                float c = (rx0[i] + rx1[i]) * 0.5f;
                if (Math.abs(x - c) <= 1.2f && z > rz1[bj] + 6f && z < rz0[bj + 1] - 6f) return true;
            }
        }
        if (bj < 0 && bi >= 0) {   // on an E-W road
            for (int j = 0; j <= nbz; j++) {
                if (!avenueZ[j]) continue;
                float c = (rz0[j] + rz1[j]) * 0.5f;
                if (Math.abs(z - c) <= 1.2f && x > rx1[bi] + 6f && x < rx0[bi + 1] - 6f) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ chunk generation

    /** Builds one chunk. Thread-safe: only reads shared immutable data. */
    public Chunk generate(int cx, int cz) {
        Chunk ch = new Chunk(cx, cz);
        GeoBuilder gb = new GeoBuilder(terrain, ch);
        float x0 = cx * ChunkManager.SIZE, z0 = cz * ChunkManager.SIZE;
        float x1 = x0 + ChunkManager.SIZE, z1 = z0 + ChunkManager.SIZE;

        gb.begin(false);
        terrainAndWater(ch, gb, x0, z0, x1, z1);
        promenadePath(gb, x0, z0, x1, z1);
        gb.end();

        for (int i = 0; i <= nbx; i++)
            for (int j = 0; j < nbz; j++) {
                float mx = (rx0[i] + rx1[i]) * 0.5f, mz = (rz1[j] + rz0[j + 1]) * 0.5f;
                if (nsStreet(i, j) && in(mx, mz, x0, z0, x1, z1)) {
                    gb.begin(false);
                    roadSegment(ch, gb, true, rx0[i], rx1[i], rz1[j], rz0[j + 1], avenueX[i]);
                    gb.end();
                }
            }
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i < nbx; i++) {
                float mx = (rx1[i] + rx0[i + 1]) * 0.5f, mz = (rz0[j] + rz1[j]) * 0.5f;
                if (ewStreet(i, j) && in(mx, mz, x0, z0, x1, z1)) {
                    gb.begin(false);
                    roadSegment(ch, gb, false, rz0[j], rz1[j], rx1[i], rx0[i + 1], avenueZ[j]);
                    gb.end();
                }
            }
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i <= nbx; i++) {
                float mx = (rx0[i] + rx1[i]) * 0.5f, mz = (rz0[j] + rz1[j]) * 0.5f;
                if (urban(mx, mz) && in(mx, mz, x0, z0, x1, z1)) signals(ch, i, j);
            }
        for (int j = 0; j < nbz; j++)
            for (int i = 0; i < nbx; i++) {
                float mx = (rx1[i] + rx0[i + 1]) * 0.5f, mz = (rz1[j] + rz0[j + 1]) * 0.5f;
                if (in(mx, mz, x0, z0, x1, z1)) block(ch, gb, i, j);
            }
        // a fresh mesh builder per chunk: chunks are generated on several threads
        new RoadMesh(roads, lib, terrain).generate(ch, gb, cx, cz, x0, z0, x1, z1);
        countryside.generate(ch, gb, cx, cz);
        countryside.fences(ch, gb, x0, z0, x1, z1);
        gb.end();
        gb.begin(false);
        gb.maxEdge = GeoBuilder.BUILDING_TILE;                  // city blocks: tiled at 8 m like every building
        SectorBuilder.toMeshes(ch.sectors, gb);
        gb.end();
        gb.maxEdge = 0f;
        ch.finish();
        return ch;
    }

    /** The Mode 7 ground map: loaded chunks' painted tiles, flat colours beyond. */
    public com.harlequinxd.drivehard.core.render.Ground.Source groundSource() {
        return new com.harlequinxd.drivehard.core.render.Ground.Source() {
            @Override public com.harlequinxd.drivehard.core.render.Ground.Tile tile(int cx, int cz) {
                Chunk c = chunks.get(cx, cz);
                return c == null ? null : c.ground;
            }
            @Override public float height(float x, float z) { return Math.max(-0.05f, terrain.height(x, z)); }
            @Override public int color(float x, float z) { return GroundPainter.farColor(terrain, x, z); }
        };
    }

    // ------------------------------------------------------------------ painted city ground

    /** Street medians and the promenade, bucketed by 256 m cells for quick lookup. */
    private final java.util.Map<Long, List<float[]>> streetPaint;

    private static long paintCell(int i, int j) { return ((long) i << 32) ^ (j & 0xFFFFFFFFL); }
    private final java.util.concurrent.ConcurrentHashMap<Integer, List<float[]>> blockPaint = new java.util.concurrent.ConcurrentHashMap<>();

    /** Avenue medians and the seaside promenade path. */
    private java.util.Map<Long, List<float[]>> streetPaint() {
        List<float[]> out = new ArrayList<>();
        GeoBuilder gb = new GeoBuilder(terrain, new Chunk(0, 0));
        gb.dry = true;
        gb.paint = out;
        Chunk dummy = new Chunk(0, 0);
        for (int i = 0; i <= nbx; i++)
            for (int j = 0; j < nbz; j++)
                if (nsStreet(i, j)) roadSegment(dummy, gb, true, rx0[i], rx1[i], rz1[j], rz0[j + 1], avenueX[i]);
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i < nbx; i++)
                if (ewStreet(i, j)) roadSegment(dummy, gb, false, rz0[j], rz1[j], rx1[i], rx0[i + 1], avenueZ[j]);
        // the seaside promenade beside the built-up blocks of the east edge
        for (int j = 0; j < nbz; j++)
            if (blockActive(nbx - 1, j)) out.add(new float[]{cityX1 + 10f, rz0[j], cityX1 + 15f, rz1[j + 1], lib.pavers, 2f, 2f});
        java.util.Map<Long, List<float[]>> map = new java.util.HashMap<>();
        for (float[] r : out)
            for (int j = (int) Math.floor(r[1] / 256f); j <= (int) Math.floor(r[3] / 256f); j++)
                for (int i = (int) Math.floor(r[0] / 256f); i <= (int) Math.floor(r[2] / 256f); i++)
                    map.computeIfAbsent(paintCell(i, j), k -> new ArrayList<>()).add(r);
        return map;
    }

    /**
     * Ground areas painted into the map in this rectangle: sidewalks, lots, parks, car parks,
     * plazas and medians, each {x0, z0, x1, z1, texture, uScale, vScale}. A block's areas come from a
     * dry run of its generator (same seed, no geometry), cached.
     */
    void groundPaint(float x0, float z0, float x1, float z1, List<float[]> out) {
        if (x1 < cityX0 - 30f || x0 > cityX1 + 30f || z1 < cityZ0 || z0 > cityZ1) return;
        java.util.Set<float[]> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int cj = (int) Math.floor(z0 / 256f); cj <= (int) Math.floor(z1 / 256f); cj++)
            for (int ci = (int) Math.floor(x0 / 256f); ci <= (int) Math.floor(x1 / 256f); ci++) {
                List<float[]> l = streetPaint.get(paintCell(ci, cj));
                if (l == null) continue;
                for (float[] r : l) if (r[2] > x0 && r[0] < x1 && r[3] > z0 && r[1] < z1 && seen.add(r)) out.add(r);
            }
        for (int j = 0; j < nbz; j++) {
            if (rz0[j + 1] < z0 || rz1[j] > z1) continue;
            for (int i = 0; i < nbx; i++) {
                if (rx0[i + 1] < x0 || rx1[i] > x1) continue;
                if (!blockActive(i, j)) continue;
                final int bi = i, bj = j;
                List<float[]> list = blockPaint.computeIfAbsent(j * nbx + i, k -> {
                    List<float[]> l = new ArrayList<>();
                    GeoBuilder gb = new GeoBuilder(terrain, new Chunk(0, 0));
                    gb.dry = true;
                    gb.paint = l;
                    block(new Chunk(0, 0), gb, bi, bj);
                    return l;
                });
                for (float[] r : list) if (r[2] > x0 && r[0] < x1 && r[3] > z0 && r[1] < z1) out.add(r);
            }
        }
    }

    private static boolean in(float x, float z, float x0, float z0, float x1, float z1) {
        return x >= x0 && x < x1 && z >= z0 && z < z1;
    }

    // ------------------------------------------------------------------ terrain, water, vegetation

    private void terrainAndWater(Chunk ch, GeoBuilder gb, float x0, float z0, float x1, float z1) {
        // what covers every 8 m cell of the chunk plus a one-cell border (for blending with neighbours)
        GroundPainter.Surface[] grid = new GroundPainter.Surface[100];
        for (int j = -1; j <= 8; j++)
            for (int i = -1; i <= 8; i++) {
                float ax = x0 + i * 8f, az = z0 + j * 8f;
                grid[(j + 1) * 10 + (i + 1)] = surface(ax, az, ax + 8f, az + 8f);
            }
        // the ground itself is a painted map tile (Mode 7), not geometry
        ch.ground = new GroundPainter(this).paint(ch.cx, ch.cz, grid);
        for (int j = 0; j < 8; j++)
            for (int i = 0; i < 8; i++) {
                float ax = x0 + i * 8f, az = z0 + j * 8f;
                boolean overlaps = urban(ax + 4f, az + 4f) || urban(ax, az) || urban(ax + 8f, az + 8f);
                GroundPainter.Surface sf = grid[(j + 1) * 10 + (i + 1)];
                if (!overlaps && sf.tex >= 0) vegetation(ch, gb, ax, az, sf.crop);
            }
    }

    /** What a foot lands on, for footstep sounds: 0 hard (roads, pavements, rock), 1 grass, 2 sand. */
    public int footing(float x, float z) {
        if (urban(x, z) || roads.onAsphalt(x, z, 0.4f, -1)) return 0;
        float h = terrain.height(x, z);
        float dx = (terrain.height(x + 4f, z) - terrain.height(x - 4f, z)) / 8f;
        float dz = (terrain.height(x, z + 4f) - terrain.height(x, z - 4f)) / 8f;
        if (dx * dx + dz * dz > 0.36f) return 0;
        if (roads.influence(x, z) <= 0.35f && (terrain.isSand(x, z, h) || h < 0.6f)) return 2;
        return 1;
    }

    private GroundPainter.Surface surface(float ax, float az, float bx, float bz) {
        GroundPainter.Surface s = new GroundPainter.Surface();
        float h0 = terrain.height(ax, az), h1 = terrain.height(bx, az), h2 = terrain.height(ax, bz), h3 = terrain.height(bx, bz);
        float max = Math.max(Math.max(h0, h1), Math.max(h2, h3)), min = Math.min(Math.min(h0, h1), Math.min(h2, h3));
        float avg = (h0 + h1 + h2 + h3) * 0.25f;
        float slope = (max - min) / Math.max(bx - ax, bz - az);
        float mx = (ax + bx) * 0.5f, mz = (az + bz) * 0.5f;
        int tex = slope > 0.6f ? lib.rock : (terrain.isSand(mx, mz, avg) || max < 0.6f ? lib.sand : (avg > 22f ? lib.hillGrass : lib.grass));
        if (tex != lib.rock && tex != lib.sand) {
            s.crop = countryside.crop(ax, az, avg, slope);
            if (s.crop != Countryside.Crop.NONE) { tex = countryside.texture(s.crop); s.uv = 8f; }
            else if (roads.influence(mx, mz) > 0.35f) tex = lib.grass;          // road verges, cuttings and embankments
        }
        s.tex = tex;
        return s;
    }

    private void vegetation(Chunk ch, GeoBuilder gb, float ax, float az, Countryside.Crop crop) {
        int ix = (int) Math.floor(ax / 8f), iz = (int) Math.floor(az / 8f);
        int sd = (int) seed;
        float r = Noise.rnd(ix, iz, sd + 500), r2 = Noise.rnd(ix, iz, sd + 506);
        float jx = ax + 1.5f + Noise.rnd(ix, iz, sd + 501) * 5f;
        float jz = az + 1.5f + Noise.rnd(ix, iz, sd + 502) * 5f;
        float h = terrain.height(jx, jz);
        float slope = Math.abs(terrain.height(jx + 2, jz) - terrain.height(jx - 2, jz)) / 4f
                + Math.abs(terrain.height(jx, jz + 2) - terrain.height(jx, jz - 2)) / 4f;
        boolean promenade = jx > cityX1 + 2f && jx < cityX1 + 26f && jz > cityZ0 && jz < cityZ1 && urban(cityX1 - 30f, jz);
        if (promenade) {
            boolean onPath = jx > cityX1 + 9f && jx < cityX1 + 16f;
            if (onPath || h < 0.5f) return;
            if (r < 0.34f) plant(ch, Flora.BLACK_PINE, ix, iz, jx, h, jz);
            else if (r < 0.42f) plant(ch, Flora.SAKURA, ix, iz, jx, h, jz);
            else if (r < 0.5f) plant(ch, Flora.AZALEA, ix, iz, jx, h, jz);
            return;
        }
        if (h < 0.3f) return;
        if (countryside.inTown(jx, jz, 6f) || countryside.inHarbour(jx, jz)) return;   // gardens are planted by the houses
        boolean rural = terrain.cityDistance(jx, jz) > 1100f;
        if (rural && roads.influence(jx, jz) > 0.05f) return;      // keep roads and their banks clear
        if (crop != Countryside.Crop.NONE) {
            Countryside.Parcel pc = new Countryside.Parcel();
            countryside.parcel(ax + 4f, az + 4f, pc);
            if (pc.seam) {
                // hedgerow trees between farm districts
                if (r < 0.45f) plant(ch, r2 < 0.4f ? Flora.CAMPHOR : (r2 < 0.7f ? Flora.ZELKOVA : Flora.BOXWOOD), ix, iz, jx, h, jz);
                return;
            }
            if (pc.crop == Countryside.Crop.ORCHARD && pc.edge > 3f) {
                // orchard rows: a tree every 6 m along the rows, rows 5 m apart
                float[] d = countryside.district((int) Math.floor((ax + 4f) / Countryside.DISTRICT), (int) Math.floor((az + 4f) / Countryside.DISTRICT));
                float ra = (float) Math.floor(pc.a / 6f) * 6f + 3f, rb = (float) Math.floor(pc.b / 5f) * 5f + 2.5f;
                float tx = d[2] + ra * d[0] - rb * d[1], tz = d[3] + ra * d[1] + rb * d[0];
                if (tx >= ax && tx < ax + 8f && tz >= az && tz < az + 8f)
                    plant(ch, r2 < 0.5f ? Flora.SAKURA : Flora.MAPLE, ix, iz, tx, terrain.height(tx, tz), tz);
                return;
            }
            if (pc.crop == Countryside.Crop.PASTURE && r < 0.004f) plant(ch, Flora.ZELKOVA, ix, iz, jx, h, jz);   // a shade tree
            return;
        }
        if (terrain.isSand(jx, jz, h)) {
            boolean eastBeach = terrain.eastness(jx, jz) > 0.5f;
            if (eastBeach && h < 2.0f) {
                if (r < 0.08f && h > 0.5f) {
                    ch.sprite(Chunk.SPRITE_UMBRELLA, (int) (r2 * 4), jx, h, jz, Noise.rnd(ix, iz, sd + 504) * 6.2832f);
                    ch.boxes.add(new Box(jx - 0.12f, h - 1, jz - 0.12f, jx + 0.12f, h + 2.3f, jz + 0.12f));
                } else if (r > 0.993f) lifeguard(ch, gb, jx, h, jz);
                return;
            }
            // pine-lined coast
            if (r < 0.2f) plant(ch, Flora.BLACK_PINE, ix, iz, jx, h, jz);
            else if (r < 0.26f) plant(ch, Flora.BOXWOOD, ix, iz, jx, h, jz);
            return;
        }
        if (slope > 0.5f) {
            if (r < 0.14f) plant(ch, r2 < 0.5f ? Flora.BAMBOO : Flora.CEDAR, ix, iz, jx, h, jz);
            return;
        }
        // hills: cedar and cypress forest up high, mixed woodland, bamboo groves and maples lower down
        float p = h < 7f ? 0.15f : (h < 25f ? 0.2f : 0.24f);
        if (rural) p = h < 20f ? 0.14f : (h < 80f ? 0.26f : 0.34f);   // forests thicken up the hills
        if (r >= p) {
            if (r < p + 0.05f) plant(ch, r2 < 0.4f ? Flora.AZALEA : (r2 < 0.7f ? Flora.CAMELLIA : Flora.HYDRANGEA), ix, iz, jx, h, jz);
            return;
        }
        Flora f;
        if (rural && h > 110f) f = r2 < 0.75f ? Flora.CEDAR : (r2 < 0.9f ? Flora.BLACK_PINE : Flora.MAPLE);
        else if (h > 25f) f = r2 < 0.7f ? Flora.CEDAR : (r2 < 0.85f ? Flora.BLACK_PINE : Flora.MAPLE);
        else if (r2 < 0.2f) f = Flora.CAMPHOR;
        else if (r2 < 0.38f) f = Flora.BAMBOO;
        else if (r2 < 0.55f) f = Flora.MAPLE;
        else if (r2 < 0.7f) f = Flora.CEDAR;
        else if (r2 < 0.82f) f = Flora.SAKURA;
        else if (r2 < 0.92f) f = Flora.ZELKOVA;
        else f = Flora.BLACK_PINE;
        plant(ch, f, ix, iz, jx, h, jz);
    }

    /** Places a plant sprite (variant and facing from a position hash); trees get a trunk collider. */
    void plant(Chunk ch, Flora f, int hx, int hz, float x, float y, float z) {
        // nothing ever grows on a road (city streets, rural roads, farm lanes)
        if (roads.roadClearance(x, z, 4f) < (f.tree ? 0.6f : 0.3f)) return;
        int sd = (int) seed;
        int v = (int) (Noise.rnd(hx, hz, sd + 503 + f.ordinal()) * f.variants);
        float yaw = Noise.rnd(hx, hz, sd + 504) * 6.2832f;
        ch.sprite(f.ordinal(), Math.min(v, f.variants - 1), x, y, z, yaw);
        if (f.tree) {
            float tr = f.trunkRadius;
            ch.boxes.add(new Box(x - tr, y - 1f, z - tr, x + tr, y + 8f, z + tr));
        }
    }

    void plantAt(Chunk ch, Flora f, float x, float y, float z) {
        plant(ch, f, (int) (x * 3.1f), (int) (z * 2.7f), x, y, z);
    }

    private void lifeguard(Chunk ch, GeoBuilder gb, float x, float h, float z) {
        int[] blank = lib.facades(JpTextures.Kind.BLANK);
        int t = blank[(int) (Noise.rnd((int) x, (int) z, 7) * blank.length)];
        for (int k = 0; k < 4; k++) {
            float lx = x + ((k & 1) == 0 ? -1.1f : 1.1f), lz = z + ((k & 2) == 0 ? -1.1f : 1.1f);
            gb.box(lx - 0.08f, h - 0.5f, lz - 0.08f, lx + 0.08f, h + 1.6f, lz + 0.08f, lib.planter, -1, 1f, 1f, L_OBJECT, 0f, 0);
        }
        gb.box(x - 1.4f, h + 1.6f, z - 1.4f, x + 1.4f, h + 3.8f, z + 1.4f, t, lib.roofMembrane, 0.5f, 0.45f, L_OBJECT, 0f, 0);
        ch.boxes.add(new Box(x - 1.4f, h - 1, z - 1.4f, x + 1.4f, h + 3.8f, z + 1.4f));
    }

    private void promenadePath(GeoBuilder gb, float x0, float z0, float x1, float z1) {
        float px0 = cityX1 + 10f, px1 = cityX1 + 15f;
        float a = Math.max(z0, cityZ0), b = Math.min(z1, cityZ1);
        float c0 = Math.max(x0, px0), c1 = Math.min(x1, px1);
        if (b <= a || c1 <= c0) return;
        gb.floorAreaT(c0, a, c1, b, 0.04f, lib.pavers, 2f, 2f, L_OBJECT, 8f, 0, -0.6f);
    }

    // ------------------------------------------------------------------ roads



    /**
     * One road segment between two intersections. {@code alongZ}: road runs N-S, crossing axis is x.
     * w0..w1 = road extent across, s0..s1 = extent along.
     */
    /** Raised, planted median along an avenue segment (the road surface itself comes from the road mesh). */
    private void roadSegment(Chunk ch, GeoBuilder gb, boolean alongZ, float w0, float w1, float s0, float s1, boolean avenue) {
        if (!avenue) return;
        float c = (w0 + w1) * 0.5f;
        float m0 = c - 1.2f, m1 = c + 1.2f, a = s0 + 6f, b = s1 - 6f;
        if (b > a) {
            if (alongZ) median(ch, gb, m0, a, m1, b, true);
            else median(ch, gb, a, m0, b, m1, false);
        }
    }



    private void median(Chunk ch, GeoBuilder gb, float ax, float az, float bx, float bz, boolean alongZ) {
        gb.floorAreaT(ax, az, bx, bz, CURB, lib.grass, 2f, 2f, L_RAISED, 8f, 0, 0f);
        curbs(gb, ax, az, bx, bz);
        int k = 0;
        if (alongZ) for (float z = az + 3f; z < bz - 1f; z += 11f, k++)
            plantAt(ch, k % 3 == 2 ? Flora.ZELKOVA : Flora.GINKGO, (ax + bx) * 0.5f, terrain.height((ax + bx) * 0.5f, z) + CURB, z);
        else for (float x = ax + 3f; x < bx - 1f; x += 11f, k++)
            plantAt(ch, k % 3 == 2 ? Flora.ZELKOVA : Flora.GINKGO, x, terrain.height(x, (az + bz) * 0.5f) + CURB, (az + bz) * 0.5f);
    }

    /** Curb faces (bottom on the road, top at sidewalk height) around a raised rectangle. */
    private void curbs(GeoBuilder gb, float ax, float az, float bx, float bz) {
        if (CURB == 0f) return;      // flat painted ground
        int t = lib.curb;
        float v = CURB / 2f;
        for (float s = ax; s < bx - 1e-3f; s += 8f) {
            float e = Math.min(bx, s + 8f);
            curb(gb, s, bz, e, bz, t, s / 2f, e / 2f, v);
            curb(gb, e, az, s, az, t, e / 2f, s / 2f, v);
        }
        for (float s = az; s < bz - 1e-3f; s += 8f) {
            float e = Math.min(bz, s + 8f);
            curb(gb, bx, e, bx, s, t, e / 2f, s / 2f, v);
            curb(gb, ax, s, ax, e, t, s / 2f, e / 2f, v);
        }
    }

    private void curb(GeoBuilder gb, float ax, float az, float bx, float bz, int t, float u0, float u1, float v) {
        float ha = terrain.height(ax, az), hb = terrain.height(bx, bz);
        gb.wallSloped(ax, az, bx, bz, ha, hb, ha + CURB, hb + CURB, t, u0, u1, 0, v, L_RAISED, 0f, 0.85f, 0);
    }

    // ------------------------------------------------------------------ traffic signals

    private void signals(Chunk ch, int i, int j) {
        float cx = (rx0[i] + rx1[i]) * 0.5f, cz = (rz0[j] + rz1[j]) * 0.5f;
        float hx = (rx1[i] - rx0[i]) * 0.5f, hz = (rz1[j] - rz0[j]) * 0.5f;
        int id = traffic.id(i, j);
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] d : dirs) {
            boolean ns = d[0] == 0;
            // edge approaches that come from outside the city have no road behind them
            if (ns && ((d[1] > 0 && j == 0) || (d[1] < 0 && j == nbz))) continue;
            if (!ns && ((d[0] > 0 && i == 0) || (d[0] < 0 && i == nbx))) continue;
            float along = ns ? hz : hx, across = ns ? hx : hz;
            float lxv = d[1], lzv = -d[0];                       // left of travel (traffic keeps left)
            float px = cx + d[0] * (along + 1.3f) + lxv * (across + 1.3f);   // far-left corner, Japanese style
            float pz = cz + d[1] * (along + 1.3f) + lzv * (across + 1.3f);
            float yaw = (float) Math.atan2(-d[0], -d[1]);         // signal faces oncoming traffic
            float y = terrain.height(px, pz) + CURB;
            ch.sprite(Chunk.SPRITE_SIGNAL, id * 2 + (ns ? 1 : 0), px, y, pz, yaw);
            ch.boxes.add(new Box(px - 0.2f, y - 1f, pz - 0.2f, px + 0.2f, y + 6f, pz + 0.2f));
        }
    }

    // ------------------------------------------------------------------ zones, blocks & lots

    public enum Zone { CENTRAL, ENTERTAINMENT, MIXED, SUBURB, SEASIDE }

    public Zone zone(int i, int j) {
        float dx = (i - iCentre) / (nbx * 0.5f);
        float dz = (j - jCentre) / (nbz * 0.5f);
        float radius = (float) Math.hypot(dx, dz);
        if (radius < 0.05f) return Zone.CENTRAL;
        if (radius < 0.10f) return Zone.ENTERTAINMENT;
        if (radius < CORE - 0.04f) return Zone.MIXED;
        if (i >= nbx - 3) return Zone.SEASIDE;
        return Zone.SUBURB;
    }

    private boolean isFireBlock(int i, int j) {
        return (i == 2 && j == 2) || (i == nbx - 3 && j == nbz - 3);
    }

    private static final class Ctx {
        final Chunk ch;
        final GeoBuilder gb;
        final Random rnd;
        Ctx(Chunk ch, GeoBuilder gb, Random rnd) { this.ch = ch; this.gb = gb; this.rnd = rnd; }
    }

    private void block(Chunk ch, GeoBuilder gb, int i, int j) {
        if (!blockActive(i, j)) return;
        Random rnd = new Random(seed * 1000003L + i * 7919L + j * 104729L);
        float bx0 = rx1[i], bx1 = rx0[i + 1], bz0 = rz1[j], bz1 = rz0[j + 1];
        float s = SIDEWALK;
        gb.begin(false);
        curbs(gb, bx0, bz0, bx1, bz1);
        int sw = lib.sidewalk;
        gb.floorAreaT(bx0, bz0, bx1, bz0 + s, CURB, sw, 2f, 2f, L_RAISED, 8f, 0, 0f);
        gb.floorAreaT(bx0, bz1 - s, bx1, bz1, CURB, sw, 2f, 2f, L_RAISED, 8f, 0, 0f);
        gb.floorAreaT(bx0, bz0 + s, bx0 + s, bz1 - s, CURB, sw, 2f, 2f, L_RAISED, 8f, 0, 0f);
        gb.floorAreaT(bx1 - s, bz0 + s, bx1, bz1 - s, CURB, sw, 2f, 2f, L_RAISED, 8f, 0, 0f);

        Zone zone = zone(i, j);
        Ctx c = new Ctx(ch, gb, rnd);
        streetFurniture(c, bx0, bz0, bx1, bz1, zone);
        float ix0 = bx0 + s, ix1 = bx1 - s, iz0 = bz0 + s, iz1 = bz1 - s;
        float depth = iz1 - iz0;
        // lots are only as deep as the buildings of the area need; a deep block keeps a courtyard
        // in the middle (gardens, a park or car parks) instead of 60 m long buildings
        float rowMax;
        switch (zone) {
            case CENTRAL: rowMax = 34f; break;
            case ENTERTAINMENT: rowMax = 20f; break;
            case SEASIDE: rowMax = 28f; break;
            case SUBURB: rowMax = 22f; break;
            default: rowMax = 24f; break;
        }
        boolean twoRows = depth >= 30f;
        if (!twoRows) {
            lotRow(c, ix0, iz0, ix1, iz1, true, true, zone, isFireBlock(i, j), i == iCentre && j == jCentre + 1);
        } else {
            float half = depth * (0.45f + rnd.nextFloat() * 0.1f);
            float dn = Math.min(rowMax, half), ds = Math.min(rowMax, depth - half);
            lotRow(c, ix0, iz0, ix1, iz0 + dn, true, false, zone, isFireBlock(i, j), i == iCentre && j == jCentre + 1);
            lotRow(c, ix0, iz1 - ds, ix1, iz1, false, true, zone, false, false);
            float m0 = iz0 + dn, m1 = iz1 - ds, mid = m1 - m0;
            if (mid >= 2f * 12f + 5f) {
                // a deep block gets a back lane through the middle with buildings facing it
                float lane0 = (m0 + m1) * 0.5f - 2.5f, lane1 = lane0 + 5f;
                float inner = Math.min(rowMax, lane0 - m0);
                gb.floorAreaT(ix0, lane0, ix1, lane1, CURB, lib.asphaltPatched, 2f, 2f, L_RAISED, 8f, 0, 0f);
                lotRow(c, ix0, lane0 - inner, ix1, lane0, false, true, zone, false, false);
                lotRow(c, ix0, lane1, ix1, lane1 + inner, true, false, zone, false, false);
                if (lane0 - inner - m0 > 4f) courtyard(c, ix0, m0, ix1, lane0 - inner, zone);
                if (m1 - (lane1 + inner) > 4f) courtyard(c, ix0, lane1 + inner, ix1, m1, zone);
            } else if (mid > 4f) courtyard(c, ix0, m0, ix1, m1, zone);
        }
        gb.end();
    }

    // ------------------------------------------------------------------ street furniture

    private void streetFurniture(Ctx c, float bx0, float bz0, float bx1, float bz1, Zone zone) {
        Flora tree;
        switch (zone) {
            case CENTRAL: tree = Flora.GINKGO; break;
            case MIXED: tree = c.rnd.nextInt(3) == 0 ? null : (c.rnd.nextBoolean() ? Flora.ZELKOVA : Flora.SAKURA); break;
            case SEASIDE: tree = Flora.BLACK_PINE; break;
            default: tree = null; break;
        }
        boolean poles = zone == Zone.MIXED || zone == Zone.SUBURB || zone == Zone.SEASIDE || zone == Zone.ENTERTAINMENT;
        for (int side = 0; side < 4; side++) {
            boolean horiz = side < 2;
            float a = horiz ? bx0 + 7f : bz0 + 7f, b = horiz ? bx1 - 7f : bz1 - 7f;
            float yaw = side == 0 ? (float) Math.PI : side == 1 ? 0f : side == 2 ? (float) (-Math.PI / 2) : (float) (Math.PI / 2);
            if (tree != null) {
                int k = 0;
                for (float t = a; t <= b; t += 14f, k++) {
                    float[] p = sidePoint(side, t, 1.0f, bx0, bz0, bx1, bz1);
                    plantAt(c.ch, tree, p[0], terrain.height(p[0], p[1]) + CURB, p[1]);
                    if (!poles && (k & 1) == 1) {
                        float[] q = sidePoint(side, t + 7f, 1.0f, bx0, bz0, bx1, bz1);
                        if (t + 7f <= b) lampAt(c.ch, q[0], q[1], yaw);
                    }
                }
            } else if (!poles) {
                for (float t = a; t <= b; t += 26f) {
                    float[] p = sidePoint(side, t, 1.0f, bx0, bz0, bx1, bz1);
                    lampAt(c.ch, p[0], p[1], yaw);
                }
            }
            if (poles) utilityPoles(c, side, a - 3f, b + 3f, bx0, bz0, bx1, bz1);
            if (c.rnd.nextInt(100) < 45) {
                float t = a + c.rnd.nextFloat() * (b - a);
                vendingMachine(c, side, t, bx0, bz0, bx1, bz1);
            }
        }
    }

    /** Point along block side {@code side} (0=N,1=S,2=W,3=E) at parameter t, {@code in} metres in from the curb. */
    private static float[] sidePoint(int side, float t, float in, float bx0, float bz0, float bx1, float bz1) {
        switch (side) {
            case 0: return new float[]{t, bz0 + in};
            case 1: return new float[]{t, bz1 - in};
            case 2: return new float[]{bx0 + in, t};
            default: return new float[]{bx1 - in, t};
        }
    }

    /** Concrete utility poles along a block side with sagging overhead wires between them. */
    private void utilityPoles(Ctx c, int side, float a, float b, float bx0, float bz0, float bx1, float bz1) {
        float step = 26f;
        int n = Math.max(1, (int) ((b - a) / step));
        float prevX = 0, prevZ = 0, prevY = 0;
        boolean horiz = side < 2;
        for (int k = 0; k <= n; k++) {
            float t = a + (b - a) * k / n;
            float[] p = sidePoint(side, t, 0.45f, bx0, bz0, bx1, bz1);
            float y = terrain.height(p[0], p[1]) + CURB;
            c.ch.sprite(Chunk.SPRITE_UTILITY, 0, p[0], y, p[1], horiz ? 0f : (float) (Math.PI / 2));
            c.ch.boxes.add(new Box(p[0] - 0.18f, y - 1f, p[1] - 0.18f, p[0] + 0.18f, y + 10.5f, p[1] + 0.18f));
            if (k > 0) {
                // two ribbons of three wires: power on top, telephone below
                float sx = horiz ? 0f : 1f, sz = horiz ? 1f : 0f;
                c.gb.powerLine(prevX, prevY + 10.02f, prevZ, p[0], y + 10.02f, p[1], sx, sz, 0.45f, lib.powerLines);
                c.gb.powerLine(prevX, prevY + 9.32f, prevZ, p[0], y + 9.32f, p[1], sx, sz, 0.55f, lib.powerLines);
            }
            prevX = p[0]; prevZ = p[1]; prevY = y;
        }
    }

    /** A drink vending machine standing against the lot line, facing the street. */
    private void vendingMachine(Ctx c, int side, float t, float bx0, float bz0, float bx1, float bz1) {
        float[] p = sidePoint(side, t, SIDEWALK - 0.45f, bx0, bz0, bx1, bz1);
        float y = terrain.height(p[0], p[1]) + CURB, top = y + 1.83f;
        float hw = 0.5f, hd = 0.37f;
        boolean horiz = side < 2;
        float x0 = p[0] - (horiz ? hw : hd), x1 = p[0] + (horiz ? hw : hd), z0 = p[1] - (horiz ? hd : hw), z1 = p[1] + (horiz ? hd : hw);
        // A metal shell with a textured front facing the street.
        Sectors.Tiles metal = SectorBuilder.plain(lib.metal, lib.metal);
        metal.matScale = 1f;
        Sectors.Tiles front = new Sectors.Tiles(1);
        front.mat = front.matUp = front.found = lib.metal;
        front.groundH = front.storeyH = 1.83f;
        front.storey(0, 1, Sectors.FULL, lib.vending[c.rnd.nextInt(lib.vending.length)]);
        Sectors.Tiles[] t4 = {metal, metal, metal, metal};
        t4[side] = front;                                     // N S W E
        sectorBox(c.ch, x0, z0, x1, z1, y, top, t4, lib.metal);
        c.ch.boxes.add(new Box(x0, y - 1f, z0, x1, top, z1));
    }

    /** A box with walls tiled per side (N, S, W, E) and a flat top. */
    void sectorBox(Chunk ch, float x0, float z0, float x1, float z1, float base, float top, Sectors.Tiles[] sides, int topTex) {
        SectorBuilder sb = new SectorBuilder(ch.sectors);
        SectorBuilder.Face[] f = new SectorBuilder.Face[4];
        for (int k = 0; k < 4; k++) {
            float[] e = BuildingGen.edge(k, x0, z0, x1, z1);
            f[k] = sb.face(e[0], e[1], e[2], e[3], sides[k]);
        }
        sb.piece(new float[]{x0, z0, x1, z0, x1, z1, x0, z1}, top, 0f, 0f, base, topTex, 1f, -1f, f, sides[0]);
    }

    /** A property wall (block wall or hedge) standing on the ground. */
    private void propertyWall(Ctx c, float x0, float z0, float x1, float z1, float h, int tex) {
        if (x1 - x0 < 0.05f || z1 - z0 < 0.05f) return;
        float ax, az, bx, bz;
        if (x1 - x0 >= z1 - z0) {
            ax = x0; az = (z0 + z1) * 0.5f;
            bx = x1; bz = az;
        } else {
            ax = (x0 + x1) * 0.5f; az = z0;
            bx = ax; bz = z1;
        }
        float g0 = terrain.height(ax, az), g1 = terrain.height(bx, bz);
        float dx = bx - ax, dz = bz - az, len = (float) Math.hypot(dx, dz);
        float nx = -dz / len, nz = dx / len;
        float light = GeoBuilder.sunLight(nx, 0f, nz);
        float scale = tex == lib.blockWall ? 1.2f : 2f;
        float[] lights = {light, light, light, light};
        // in tiles of at most 2.5 m that follow the ground
        int n = Math.max(1, (int) Math.ceil(len / 2.5f));
        for (int k = 0; k < n; k++) {
            float t0 = k / (float) n, t1 = (k + 1) / (float) n;
            float px = ax + dx * t0, pz = az + dz * t0, qx = ax + dx * t1, qz = az + dz * t1;
            float y0 = terrain.height(px, pz) - 0.1f, y1 = terrain.height(qx, qz) - 0.1f;
            float[] p = {px, y0, pz, qx, y1, qz, qx, y1 + h + 0.1f, qz, px, y0 + h + 0.1f, pz};
            float s0 = len * t0 / scale, s1 = len * t1 / scale;
            float[] uv = {s0, (h + 0.1f) / scale, s1, (h + 0.1f) / scale, s1, 0f, s0, 0f};
            c.gb.quad(p, uv, lights, tex, L_OBJECT, Geo.FLAG_NO_SUBDIV, 0f, true);
        }
        c.ch.boxes.add(new Box(x0, Math.min(g0, g1) - 1f, z0, x1, Math.max(g0, g1) + h, z1));
    }

    void lampAt(Chunk ch, float x, float z, float yaw) {
        float y = terrain.height(x, z) + CURB;
        ch.sprite(Chunk.SPRITE_LAMP, 0, x, y, z, yaw);
        ch.boxes.add(new Box(x - 0.12f, y - 1f, z - 0.12f, x + 0.12f, y + 7f, z + 0.12f));
    }

    // ------------------------------------------------------------------ lots

    private void lotRow(Ctx c, float x0, float z0, float x1, float z1, boolean facesNorth, boolean facesSouth, Zone zone,
                        boolean fireStation, boolean koban) {
        float x = x0;
        boolean first = true;
        while (x < x1 - 0.5f) {
            float w;
            int special = 0;
            if (first && fireStation) { w = 24f; special = 1; }
            else if (first && koban) { w = 8f; special = 2; }
            else switch (zone) {
                case CENTRAL: w = 22f + 2f * c.rnd.nextInt(8); break;
                case ENTERTAINMENT: w = 7f + c.rnd.nextInt(5); break;
                case SUBURB: w = 9f + c.rnd.nextInt(4); break;
                case SEASIDE: w = 14f + 2f * c.rnd.nextInt(8); break;
                default: w = 10f + 2f * c.rnd.nextInt(6); break;
            }
            if (x1 - (x + w) < 6f) w = x1 - x;
            float lx1 = Math.min(x1, x + w);
            lot(c, x, z0, lx1, z1, facesNorth, facesSouth, x <= x0 + 0.1f, lx1 >= x1 - 0.1f, zone, special);
            x = lx1;
            first = false;
            // narrow alleys between buildings in the busy older districts (not the suburbs)
            float alleyP = zone == Zone.ENTERTAINMENT ? 0.4f : zone == Zone.MIXED ? 0.22f : zone == Zone.SEASIDE ? 0.18f
                    : zone == Zone.CENTRAL ? 0.1f : 0f;
            float aw = 2.4f + c.rnd.nextFloat() * 0.8f;
            if (x < x1 - aw - 8f && c.rnd.nextFloat() < alleyP) {
                alley(c, x, z0, x + aw, z1, facesNorth);
                x += aw;
            }
        }
    }

    /**
     * An alley between two buildings, from the street (north side if {@code fromNorth}) into
     * the block: worn concrete, and the clutter of a back alley along both walls: rubbish
     * bins, stacked beer crates, cardboard, gas bottles, a/c units and drain pipes.
     */
    private void alley(Ctx c, float x0, float z0, float x1, float z1, boolean fromNorth) {
        GeoBuilder gb = c.gb;
        Random r = c.rnd;
        gb.floorAreaT(x0, z0, x1, z1, CURB, lib.asphaltPatched, 2f, 2f, L_RAISED, 8f, 0, 0f);
        float z = 1.5f;
        float len = z1 - z0;
        while (z < len - 1f) {
            float pz = fromNorth ? z0 + z : z1 - z;
            boolean west = r.nextBoolean();
            float wallX = west ? x0 : x1, in = west ? 1f : -1f;
            float g = terrain.height((x0 + x1) * 0.5f, pz) + CURB;
            int kind = r.nextInt(6);
            float step;
            switch (kind) {
                case 0: {   // wheeled rubbish bin
                    float bx = wallX + in * 0.4f;
                    prop(c, bx - 0.35f, g, pz - 0.3f, bx + 0.35f, g + 1.0f, pz + 0.3f, lib.bin, lib.bin);
                    step = 1.4f;
                    break;
                }
                case 1: {   // stacked beer crates
                    float bx = wallX + in * 0.3f;
                    int n = 2 + r.nextInt(3);
                    for (int k = 0; k < n; k++) {
                        int tex = r.nextBoolean() ? lib.crateYellow : lib.crateRed;
                        prop(c, bx - 0.22f, g + k * 0.31f, pz - 0.18f, bx + 0.22f, g + (k + 1) * 0.31f, pz + 0.18f, tex, tex);
                    }
                    step = 1.0f;
                    break;
                }
                case 2: {   // cardboard boxes
                    float bx = wallX + in * 0.45f;
                    prop(c, bx - 0.4f, g, pz - 0.35f, bx + 0.4f, g + 0.5f, pz + 0.35f, lib.cardboard, lib.cardboard);
                    if (r.nextBoolean()) prop(c, bx - 0.3f, g + 0.5f, pz - 0.25f, bx + 0.25f, g + 0.85f, pz + 0.25f, lib.cardboard, lib.cardboard);
                    step = 1.2f;
                    break;
                }
                case 3: {   // gas bottles chained to the wall
                    float bx = wallX + in * 0.25f;
                    prop(c, bx - 0.16f, g, pz - 0.36f, bx + 0.16f, g + 1.1f, pz - 0.04f, lib.gasBottle, lib.metal);
                    prop(c, bx - 0.16f, g, pz + 0.04f, bx + 0.16f, g + 1.1f, pz + 0.36f, lib.gasBottle, lib.metal);
                    step = 1.0f;
                    break;
                }
                case 4: {   // a/c units stacked up the wall
                    float nx = in;
                    gb.wallUnit(wallX, pz, nx, 0f, 0.8f, g + 0.25f, 0.62f, 0.3f, lib.aircon, lib.airconSide);
                    if (r.nextBoolean()) gb.wallUnit(wallX, pz + 0.2f, nx, 0f, 0.8f, g + 3.6f, 0.62f, 0.3f, lib.aircon, lib.airconSide);
                    if (r.nextBoolean()) gb.wallUnit(wallX, pz - 0.2f, nx, 0f, 0.8f, g + 6.6f, 0.62f, 0.3f, lib.aircon, lib.airconSide);
                    step = 1.3f;
                    break;
                }
                default: {  // drain pipe down the wall
                    float bx = wallX + in * 0.08f;
                    gb.box(Math.min(bx, bx + in * 0.1f), g, pz - 0.05f, Math.max(bx, bx + in * 0.1f), g + 8f, pz + 0.05f, lib.metal, lib.metal,
                            1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
                    step = 0.8f;
                    break;
                }
            }
            z += step + r.nextFloat() * 2.2f;
        }
    }

    /** A small solid prop box (with collision). */
    private void prop(Ctx c, float x0, float y0, float z0, float x1, float y1, float z1, int side, int top) {
        float ax = Math.min(x0, x1), bx = Math.max(x0, x1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
        c.gb.box(ax, y0, az, bx, y1, bz, side, top, 1f / Math.max(0.1f, bx - ax), 1f / Math.max(0.1f, y1 - y0), L_OBJECT, 0f, Geo.FLAG_DETAIL);
        c.ch.boxes.add(new Box(ax, y0 - 0.5f, az, bx, y1, bz));
    }

    private void lot(Ctx c, float x0, float z0, float x1, float z1, boolean fN, boolean fS, boolean fW, boolean fE,
                     Zone zone, int special) {
        Random rnd = c.rnd;
        boolean[] street = {fN, fS, fW, fE};
        BuildingGen.Type type;
        int[] set;
        float roll = rnd.nextFloat();
        if (special == 1) { type = BuildingGen.Type.FIRE_STATION; set = shopsMixed; }
        else if (special == 2) { type = BuildingGen.Type.KOBAN; set = shopsMixed; }
        else switch (zone) {
            case CENTRAL:
                if (roll < 0.55f) { type = BuildingGen.Type.TOWER; set = shopsCentral; }
                else if (roll < 0.75f) { type = BuildingGen.Type.DEPT_STORE; set = shopsCentral; }
                else if (roll < 0.83f) { type = BuildingGen.Type.PACHINKO; set = shopsPachinko; }
                else if (roll < 0.92f) { park(c, x0, z0, x1, z1, true); return; }
                else { type = BuildingGen.Type.KONBINI; set = shopsKonbini; }
                break;
            case ENTERTAINMENT:
                if (roll < 0.68f) { type = BuildingGen.Type.ZAKKYO; set = shopsEnt; }
                else if (roll < 0.78f) { type = BuildingGen.Type.PACHINKO; set = shopsPachinko; }
                else if (roll < 0.92f) { type = BuildingGen.Type.SHOPHOUSE; set = shopsEnt; }
                else { type = BuildingGen.Type.KONBINI; set = shopsKonbini; }
                break;
            case SUBURB:
                if (roll < 0.72f) { house(c, x0, z0, x1, z1, street); return; }
                else if (roll < 0.84f) { type = BuildingGen.Type.APAATO; set = shopsSuburb; }
                else if (roll < 0.9f) { type = BuildingGen.Type.SHOPHOUSE; set = shopsSuburb; }
                else if (roll < 0.96f) { park(c, x0, z0, x1, z1, false); return; }
                else { parking(c, x0, z0, x1, z1); return; }
                break;
            case SEASIDE:
                if (roll < 0.4f) { type = BuildingGen.Type.WAREHOUSE; set = shopsSeaside; }
                else if (roll < 0.62f) { type = BuildingGen.Type.SHOPHOUSE; set = shopsSeaside; }
                else if (roll < 0.82f) { type = BuildingGen.Type.MANSION; set = shopsSeaside; }
                else { parking(c, x0, z0, x1, z1); return; }
                break;
            default:
                if (roll < 0.42f) { type = BuildingGen.Type.MANSION; set = shopsMixed; }
                else if (roll < 0.67f) { type = BuildingGen.Type.SHOPHOUSE; set = shopsMixed; }
                else if (roll < 0.73f) { type = BuildingGen.Type.KONBINI; set = shopsKonbini; }
                else if (roll < 0.8f) { type = BuildingGen.Type.ZAKKYO; set = shopsMixed; }
                else if (roll < 0.88f) { parking(c, x0, z0, x1, z1); return; }
                else if (roll < 0.95f) { park(c, x0, z0, x1, z1, false); return; }
                else { type = BuildingGen.Type.APAATO; set = shopsMixed; }
                break;
        }
        boolean urban = type != BuildingGen.Type.APAATO && type != BuildingGen.Type.WAREHOUSE;
        float front = type == BuildingGen.Type.MANSION ? 1.5f : (type == BuildingGen.Type.APAATO ? 2f : 0f);
        float fx0 = x0, fx1 = x1;
        if (!urban) { fx0 += fW ? 0.5f : 0.8f; fx1 -= fE ? 0.5f : 0.8f; }
        float fz0 = z0 + (fN ? front : 0.6f), fz1 = z1 - (fS ? front : 0.6f);
        if (type == BuildingGen.Type.KOBAN) { fz1 = Math.min(fz1, fz0 + 6f); }
        if (fx1 - fx0 < 5f || fz1 - fz0 < 5f) { park(c, x0, z0, x1, z1, false); return; }
        int lotTex = urban ? lib.concrete : lib.grass;
        groundAround(c, x0, z0, x1, z1, fx0, fz0, fx1, fz1, lotTex);
        boolean shopsOk = type != BuildingGen.Type.APAATO && type != BuildingGen.Type.WAREHOUSE
                && type != BuildingGen.Type.FIRE_STATION && type != BuildingGen.Type.KOBAN;
        boolean[] shops = {shopsOk && fN && front < 0.5f, shopsOk && fS && front < 0.5f,
                shopsOk && fW && (fN || fS) && type != BuildingGen.Type.MANSION, shopsOk && fE && (fN || fS) && type != BuildingGen.Type.MANSION};
        if (type == BuildingGen.Type.MANSION && rnd.nextBoolean()) { shops[0] = fN; shops[1] = fS; fz0 = fN ? z0 : fz0; fz1 = fS ? z1 : fz1; }
        new BuildingGen(this, lib, c.gb, rnd, c.ch).build(type, fx0, fz0, fx1, fz1, street, shops, set.length > 0 ? set : lib.shops);
    }

    /** The inside of a deep block: gardens and trees in the suburbs, car parks and pocket parks elsewhere. */
    private void courtyard(Ctx c, float x0, float z0, float x1, float z1, Zone zone) {
        float x = x0;
        while (x < x1 - 1f) {
            float w = Math.min(x1 - x, 24f + c.rnd.nextInt(5) * 4f);
            if (x1 - (x + w) < 10f) w = x1 - x;
            float roll = c.rnd.nextFloat();
            boolean parkingLot = zone == Zone.CENTRAL || zone == Zone.ENTERTAINMENT ? roll < 0.6f : roll < 0.25f;
            if (parkingLot) c.gb.floorAreaT(x, z0, x + w, z1, CURB, lib.parking, 2.5f, 5f, L_RAISED, 8f, 0, 0f);
            else {
                // a back garden / green: lawn with a few trees
                c.gb.floorAreaT(x, z0, x + w, z1, CURB, lib.grass, 2f, 2f, L_RAISED, 8f, 0, 0f);
                Flora[] trees = {Flora.SAKURA, Flora.CAMPHOR, Flora.ZELKOVA, Flora.MAPLE, Flora.BLACK_PINE};
                for (float tz = z0 + 5f; tz < z1 - 4f; tz += 13f + c.rnd.nextInt(5))
                    for (float tx = x + 5f; tx < x + w - 4f; tx += 13f + c.rnd.nextInt(5))
                        if (c.rnd.nextInt(3) > 0) plantAt(c.ch, trees[c.rnd.nextInt(trees.length)], tx, terrain.height(tx, tz) + CURB, tz);
            }
            x += w;
        }
    }

    private void groundAround(Ctx c, float x0, float z0, float x1, float z1, float fx0, float fz0, float fx1, float fz1, int tex) {
        GeoBuilder gb = c.gb;
        if (fz0 > z0 + 0.01f) gb.floorAreaT(x0, z0, x1, fz0, CURB, tex, 2f, 2f, L_RAISED, 8f, 0, 0f);
        if (fz1 < z1 - 0.01f) gb.floorAreaT(x0, fz1, x1, z1, CURB, tex, 2f, 2f, L_RAISED, 8f, 0, 0f);
        if (fx0 > x0 + 0.01f) gb.floorAreaT(x0, fz0, fx0, fz1, CURB, tex, 2f, 2f, L_RAISED, 8f, 0, 0f);
        if (fx1 < x1 - 0.01f) gb.floorAreaT(fx1, fz0, x1, fz1, CURB, tex, 2f, 2f, L_RAISED, 8f, 0, 0f);
    }

    /** Detached house: front garden behind a block wall with a gate, house, backyard with a tree. */
    private void house(Ctx c, float x0, float z0, float x1, float z1, boolean[] street) {
        Random rnd = c.rnd;
        boolean faceN = street[0] || !street[1];
        float front = 2.6f, houseDepth = 8.5f + rnd.nextFloat() * 2.5f;
        float fx0 = x0 + 0.7f, fx1 = x1 - 0.7f;
        float fz0, fz1;
        if (faceN) { fz0 = z0 + front; fz1 = Math.min(z1 - 1f, fz0 + houseDepth); }
        else { fz1 = z1 - front; fz0 = Math.max(z0 + 1f, fz1 - houseDepth); }
        if (fx1 - fx0 < 5f || fz1 - fz0 < 5f) { park(c, x0, z0, x1, z1, false); return; }
        groundAround(c, x0, z0, x1, z1, fx0, fz0, fx1, fz1, lib.grass);
        boolean[] st = {faceN, !faceN, street[2], street[3]};
        new BuildingGen(this, lib, c.gb, rnd, c.ch).build(BuildingGen.Type.HOUSE, fx0, fz0, fx1, fz1, st, new boolean[4], shopsSuburb);
        // block wall with a gate gap along the street edge
        float wz = faceN ? z0 + 0.15f : z1 - 0.15f;
        float gate0 = x0 + (x1 - x0) * (0.3f + rnd.nextFloat() * 0.3f), gate1 = gate0 + 1.8f;
        blockWall(c, x0, wz, gate0, wz, faceN);
        blockWall(c, gate1, wz, x1, wz, faceN);
        // side and back walls round the property
        float t = 0.08f, zf = faceN ? z0 + 0.25f : z0 + 0.1f, zb = faceN ? z1 - 0.1f : z1 - 0.25f;
        propertyWall(c, x0 + 0.1f, zf, x0 + 0.1f + 2 * t, zb, 1.2f, lib.blockWall);
        propertyWall(c, x1 - 0.1f - 2 * t, zf, x1 - 0.1f, zb, 1.2f, lib.blockWall);
        float zw = faceN ? z1 - 0.1f - 2 * t : z0 + 0.1f;
        propertyWall(c, x0 + 0.3f, zw, x1 - 0.3f, zw + 2 * t, 1.2f, lib.blockWall);
        // garden planting
        Flora[] garden = {Flora.BLACK_PINE, Flora.MAPLE, Flora.CAMELLIA, Flora.BOXWOOD, Flora.HYDRANGEA, Flora.AZALEA};
        float gz = faceN ? z0 + 1.3f : z1 - 1.3f;
        plantAt(c.ch, garden[rnd.nextInt(garden.length)], x0 + 1.4f, terrain.height(x0 + 1.4f, gz) + CURB, gz);
        if (rnd.nextBoolean()) plantAt(c.ch, garden[rnd.nextInt(garden.length)], x1 - 1.4f, terrain.height(x1 - 1.4f, gz) + CURB, gz);
        float bz = faceN ? (fz1 + z1) * 0.5f : (z0 + fz0) * 0.5f;
        if (Math.abs((faceN ? z1 - fz1 : fz0 - z0)) > 4f && rnd.nextInt(3) > 0)
            plantAt(c.ch, rnd.nextBoolean() ? Flora.MAPLE : (rnd.nextBoolean() ? Flora.SAKURA : Flora.BAMBOO),
                    (x0 + x1) * 0.5f, terrain.height((x0 + x1) * 0.5f, bz) + CURB, bz);
    }

    private void blockWall(Ctx c, float ax, float z, float bx, float z2, boolean faceN) {
        if (bx - ax < 0.3f) return;
        propertyWall(c, ax, z - 0.08f, bx, z + 0.08f, 1.2f, lib.blockWall);
    }

    private void parking(Ctx c, float x0, float z0, float x1, float z1) {
        c.gb.floorAreaT(x0, z0, x1, z1, CURB, lib.parking, 2.5f, 5f, L_RAISED, 8f, 0, 0f);
        lampAt(c.ch, x0 + 1.5f, (z0 + z1) * 0.5f, 0f);
    }

    private void park(Ctx c, float x0, float z0, float x1, float z1, boolean plaza) {
        c.gb.floorAreaT(x0, z0, x1, z1, CURB, plaza ? lib.pavers : lib.grass, 2f, 2f, L_RAISED, 8f, 0, 0f);
        Flora[] trees = {Flora.SAKURA, Flora.SAKURA, Flora.CAMPHOR, Flora.ZELKOVA, Flora.MAPLE, Flora.BLACK_PINE, Flora.GINKGO};
        Flora[] shrubs = {Flora.AZALEA, Flora.HYDRANGEA, Flora.BOXWOOD, Flora.CAMELLIA};
        for (float z = z0 + 3f; z < z1 - 2f; z += 7f + c.rnd.nextInt(4))
            for (float x = x0 + 3f; x < x1 - 2f; x += 7f + c.rnd.nextInt(4)) {
                float y = terrain.height(x, z) + CURB;
                if (c.rnd.nextInt(3) > 0) plantAt(c.ch, trees[c.rnd.nextInt(trees.length)], x + c.rnd.nextFloat() * 2f, y, z + c.rnd.nextFloat() * 2f);
                else plantAt(c.ch, shrubs[c.rnd.nextInt(shrubs.length)], x, y, z);
            }
    }

    void plantAtTerrain(Chunk ch, Flora f, float x, float z) {
        plantAt(ch, f, x, terrain.height(x, z) + CURB, z);
    }
}
