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
public final class City extends World {
    /** Everything on the ground is painted flat: no kerbs. */
    public static final float CURB = 0f, SIDEWALK = 4f;
    public static final float FLOOR_H = 3.4f, SHOP_H = 4.6f;
    /** The island's big city. */
    public static final String NAME = "Minato City";

    final TextureLibrary lib;
    private final long seed;
    public final Terrain terrain;
    public final TrafficLights traffic;
    public final ChunkManager chunks;

    public final int nbx = 9, nbz = 10;
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

    public City(TextureLibrary lib, long seed) {
        this.lib = lib;
        this.seed = seed;
        layout(new Random(seed));
        terrain = new Terrain((int) seed, cityX0, cityZ0, cityX1, cityZ1);
        traffic = new TrafficLights(nbx + 1, (int) seed);
        roads = new RoadNetwork(this, terrain, seed);
        terrain.roads = roads;
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
        countryside = new Countryside(this, lib, seed);
        streetPaint = streetPaint();
        chunks = new ChunkManager(this);
        minX = terrain.islandX0 - 9000; maxX = terrain.islandX1 + 3000; minZ = terrain.islandZ0 - 9000; maxZ = terrain.islandZ1 + 9000;
        int mj = nbz / 2;
        spawnX = cityX1 + 7f;
        spawnZ = (rz1[mj] + rz0[mj + 1]) * 0.5f;
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
            avenueX[i] = i % 3 == 0 && i != nbx;
            rx0[i] = x;
            x += avenueX[i] ? 22f : 13f;
            rx1[i] = x;
            if (i < nbx) x += 52 + 4 * rnd.nextInt(8);
        }
        float z = 0;
        for (int j = 0; j <= nbz; j++) {
            avenueZ[j] = j % 4 == 0;
            rz0[j] = z;
            z += avenueZ[j] ? 22f : 13f;
            rz1[j] = z;
            if (j < nbz) z += j == nbz / 2 ? 64 : 56 + 4 * rnd.nextInt(7);
        }
        cityX0 = rx0[0]; cityX1 = rx1[nbx];
        cityZ0 = rz0[0]; cityZ1 = rz1[nbz];
    }

    // ------------------------------------------------------------------ World (collision)

    private final List<Box> nearList = new ArrayList<>();

    /** Moving colliders (cars) supplied by the simulation. */
    public interface Dynamic { void boxesNear(float x, float z, List<Box> out); }
    public Dynamic dynamic;

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
    public boolean blocked(float x, float z) {
        return terrain.height(x, z) < -0.5f;
    }

    /** Sidewalks/blocks and avenue medians sit one curb height above the road. */
    public boolean isRaised(float x, float z) {
        if (CURB == 0f) return false;
        if (x <= cityX0 || x >= cityX1 || z <= cityZ0 || z >= cityZ1) return false;
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
                if (in(mx, mz, x0, z0, x1, z1)) {
                    gb.begin(false);
                    roadSegment(ch, gb, true, rx0[i], rx1[i], rz1[j], rz0[j + 1], avenueX[i]);
                    gb.end();
                }
            }
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i < nbx; i++) {
                float mx = (rx1[i] + rx0[i + 1]) * 0.5f, mz = (rz0[j] + rz1[j]) * 0.5f;
                if (in(mx, mz, x0, z0, x1, z1)) {
                    gb.begin(false);
                    roadSegment(ch, gb, false, rz0[j], rz1[j], rx1[i], rx0[i + 1], avenueZ[j]);
                    gb.end();
                }
            }
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i <= nbx; i++) {
                float mx = (rx0[i] + rx1[i]) * 0.5f, mz = (rz0[j] + rz1[j]) * 0.5f;
                if (in(mx, mz, x0, z0, x1, z1)) signals(ch, i, j);
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
            @Override public com.harlequinxd.drivehard.core.render.Sectors sectors(int cx, int cz) {
                Chunk c = chunks.get(cx, cz);
                return c == null ? null : c.sectors;
            }
            @Override public float height(float x, float z) { return Math.max(-0.05f, terrain.height(x, z)); }
            @Override public int color(float x, float z) { return GroundPainter.farColor(terrain, x, z); }
        };
    }

    // ------------------------------------------------------------------ painted city ground

    private final List<float[]> streetPaint;
    private final java.util.concurrent.ConcurrentHashMap<Integer, List<float[]>> blockPaint = new java.util.concurrent.ConcurrentHashMap<>();

    /** Avenue medians and the seaside promenade path. */
    private List<float[]> streetPaint() {
        List<float[]> out = new ArrayList<>();
        GeoBuilder gb = new GeoBuilder(terrain, new Chunk(0, 0));
        gb.dry = true;
        gb.paint = out;
        Chunk dummy = new Chunk(0, 0);
        for (int i = 0; i <= nbx; i++)
            for (int j = 0; j < nbz; j++) roadSegment(dummy, gb, true, rx0[i], rx1[i], rz1[j], rz0[j + 1], avenueX[i]);
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i < nbx; i++) roadSegment(dummy, gb, false, rz0[j], rz1[j], rx1[i], rx0[i + 1], avenueZ[j]);
        out.add(new float[]{cityX1 + 10f, cityZ0, cityX1 + 15f, cityZ1, lib.pavers, 2f, 2f});
        return out;
    }

    /**
     * Ground areas painted into the map in this rectangle: sidewalks, lots, parks, car parks,
     * plazas and medians, each {x0, z0, x1, z1, texture, uScale, vScale}. A block's areas come from a
     * dry run of its generator (same seed, no geometry), cached.
     */
    void groundPaint(float x0, float z0, float x1, float z1, List<float[]> out) {
        if (x1 < cityX0 - 30f || x0 > cityX1 + 30f || z1 < cityZ0 || z0 > cityZ1) return;
        for (float[] r : streetPaint) if (r[2] > x0 && r[0] < x1 && r[3] > z0 && r[1] < z1) out.add(r);
        for (int j = 0; j < nbz; j++) {
            if (rz0[j + 1] < z0 || rz1[j] > z1) continue;
            for (int i = 0; i < nbx; i++) {
                if (rx0[i + 1] < x0 || rx1[i] > x1) continue;
                final int bi = i, bj = j;
                List<float[]> list = blockPaint.computeIfAbsent(j * 64 + i, k -> {
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
                boolean overlaps = ax + 8f > cityX0 && ax < cityX1 && az + 8f > cityZ0 && az < cityZ1;
                GroundPainter.Surface sf = grid[(j + 1) * 10 + (i + 1)];
                if (!overlaps && sf.tex >= 0) vegetation(ch, gb, ax, az, sf.crop);
            }
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
        boolean promenade = jx > cityX1 + 2f && jx < cityX1 + 26f && jz > cityZ0 && jz < cityZ1;
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
        int di = Math.abs(i - iCentre), dj = Math.abs(j - jCentre);
        if (i >= nbx - 1) return Zone.SEASIDE;
        if (di <= 1 && dj <= 1) return Zone.CENTRAL;
        if (di <= 2 && dj <= 2) return Zone.ENTERTAINMENT;
        if (i == 0 || j == 0 || j >= nbz - 2 || di + dj >= 6) return Zone.SUBURB;
        return Zone.MIXED;
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
        boolean twoRows = depth >= 30f;
        float zMid = twoRows ? iz0 + Math.round(depth * (0.45f + rnd.nextFloat() * 0.1f)) : iz1;
        lotRow(c, ix0, iz0, ix1, zMid, true, !twoRows, zone, isFireBlock(i, j), i == iCentre && j == jCentre + 1);
        if (twoRows) lotRow(c, ix0, zMid, ix1, iz1, false, true, zone, false, false);
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
                float[][] wires = {{10.02f, -0.6f}, {10.02f, 0f}, {10.02f, 0.6f}, {9.32f, -0.6f}, {9.32f, 0.6f}};
                for (float[] w : wires) {
                    float ox = horiz ? 0f : w[1], oz = horiz ? w[1] : 0f;
                    wire(c.gb, prevX + ox, prevY + w[0], prevZ + oz, p[0] + ox, y + w[0], p[1] + oz);
                }
            }
            prevX = p[0]; prevZ = p[1]; prevY = y;
        }
    }

    private void wire(GeoBuilder gb, float ax, float ay, float az, float bx, float by, float bz) {
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f - 0.45f, mz = (az + bz) * 0.5f;
        float th = 0.05f;
        float[] l = {0.5f, 0.5f, 0.5f, 0.5f};
        float[] t = {0, 1, 1, 1, 1, 0, 0, 0};
        gb.quad(new float[]{ax, ay - th, az, mx, my - th, mz, mx, my + th, mz, ax, ay + th, az}, t, l, lib.pole, L_OBJECT,
                Geo.FLAG_DETAIL | Geo.FLAG_NO_SUBDIV, -0.2f, true);
        gb.quad(new float[]{mx, my - th, mz, bx, by - th, bz, bx, by + th, bz, mx, my + th, mz}, t, l, lib.pole, L_OBJECT,
                Geo.FLAG_DETAIL | Geo.FLAG_NO_SUBDIV, -0.2f, true);
    }

    /** A drink vending machine standing against the lot line, facing the street. */
    private void vendingMachine(Ctx c, int side, float t, float bx0, float bz0, float bx1, float bz1) {
        float[] p = sidePoint(side, t, SIDEWALK - 0.45f, bx0, bz0, bx1, bz1);
        float y = terrain.height(p[0], p[1]) + CURB, top = y + 1.83f;
        float hw = 0.5f, hd = 0.37f;
        boolean horiz = side < 2;
        float x0 = p[0] - (horiz ? hw : hd), x1 = p[0] + (horiz ? hw : hd), z0 = p[1] - (horiz ? hd : hw), z1 = p[1] + (horiz ? hd : hw);
        // a sector: metal all round, the lit front (one tile) facing the street
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

    /** A box as a sector: walls tiled per side (N, S, W, E), flat top. */
    void sectorBox(Chunk ch, float x0, float z0, float x1, float z1, float base, float top, Sectors.Tiles[] sides, int topTex) {
        SectorBuilder sb = new SectorBuilder(ch.sectors);
        SectorBuilder.Face[] f = new SectorBuilder.Face[4];
        for (int k = 0; k < 4; k++) {
            float[] e = BuildingGen.edge(k, x0, z0, x1, z1);
            f[k] = sb.face(e[0], e[1], e[2], e[3], sides[k]);
        }
        sb.piece(new float[]{x0, z0, x1, z0, x1, z1, x0, z1}, top, 0f, 0f, base, topTex, 1f, -1f, f, sides[0]);
    }

    /** A property wall (block wall or hedge) as a sector, standing on the ground. */
    void propertyWall(Chunk ch, float x0, float z0, float x1, float z1, float h, int tex) {
        if (x1 - x0 < 0.05f || z1 - z0 < 0.05f) return;
        float g0 = terrain.height(x0, z0), g1 = terrain.height(x1, z1);
        float base = Math.min(g0, g1), top = Math.max(g0, g1) + h;
        Sectors.Tiles t = SectorBuilder.plain(tex, tex);
        t.matScale = tex == lib.blockWall ? 1.2f : 2f;
        sectorBox(ch, x0, z0, x1, z1, base - 0.1f, top, new Sectors.Tiles[]{t, t, t, t}, tex == lib.blockWall ? lib.concrete : tex);
        ch.boxes.add(new Box(x0, base - 1f, z0, x1, top, z1));
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
        }
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
        propertyWall(c.ch, x0 + 0.1f, zf, x0 + 0.1f + 2 * t, zb, 1.2f, lib.blockWall);
        propertyWall(c.ch, x1 - 0.1f - 2 * t, zf, x1 - 0.1f, zb, 1.2f, lib.blockWall);
        float zw = faceN ? z1 - 0.1f - 2 * t : z0 + 0.1f;
        propertyWall(c.ch, x0 + 0.3f, zw, x1 - 0.3f, zw + 2 * t, 1.2f, lib.blockWall);
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
        propertyWall(c.ch, ax, z - 0.08f, bx, z + 0.08f, 1.2f, lib.blockWall);
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
