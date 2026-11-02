package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.World;
import com.harlequinxd.drivehard.core.model.Flora;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.tex.Facades.Kind;
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
 * Procedural Miami-style island city, generated chunk by chunk.
 *
 * The street grid and the terrain are cheap pure functions of the seed; {@link #generate}
 * builds the geometry, collision boxes and sprite props of any 64 m chunk on demand, so the
 * {@link ChunkManager} can stream the island in and out of memory.
 *
 * +X is east (toward the Ocean Drive beach), +Z is south.
 */
public final class City extends World {
    public static final float CURB = 0.15f, SIDEWALK = 4f;
    public static final float FLOOR_H = 3.4f, SHOP_H = 4.6f;

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

    public City(TextureLibrary lib, long seed) {
        this.lib = lib;
        this.seed = seed;
        layout(new Random(seed));
        terrain = new Terrain((int) seed, cityX0, cityZ0, cityX1, cityZ1);
        traffic = new TrafficLights(nbx + 1, (int) seed);
        chunks = new ChunkManager(this);
        minX = cityX0 - 700; maxX = cityX1 + 700; minZ = cityZ0 - 700; maxZ = cityZ1 + 700;
        int mj = nbz / 2;
        spawnX = cityX1 + 7f;
        spawnZ = (rz1[mj] + rz0[mj + 1]) * 0.5f;
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
            if (j < nbz) z += 56 + 4 * rnd.nextInt(7);
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
        return terrain.height(x, z) + (isRaised(x, z) ? CURB : 0f);
    }

    @Override
    public boolean blocked(float x, float z) {
        return terrain.height(x, z) < -0.5f;
    }

    /** Sidewalks/blocks and avenue medians sit one curb height above the road. */
    public boolean isRaised(float x, float z) {
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
        roadSurfaces(gb, x0, z0, x1, z1);
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
        gb.end();
        ch.finish();
        return ch;
    }

    private static boolean in(float x, float z, float x0, float z0, float x1, float z1) {
        return x >= x0 && x < x1 && z >= z0 && z < z1;
    }

    // ------------------------------------------------------------------ terrain, water, vegetation

    private void terrainAndWater(Chunk ch, GeoBuilder gb, float x0, float z0, float x1, float z1) {
        float wl = GeoBuilder.sunLight(0, 1, 0);
        for (float qz = z0; qz < z1; qz += 32f)
            for (float qx = x0; qx < x1; qx += 32f) {
                float m = Math.min(Math.min(terrain.height(qx, qz), terrain.height(qx + 32, qz)),
                        Math.min(terrain.height(qx, qz + 32), terrain.height(qx + 32, qz + 32)));
                m = Math.min(m, terrain.height(qx + 16, qz + 16));
                if (m < 0.4f) gb.floor(qx, qz, qx + 32f, qz + 32f, 0f, lib.water, 4f, 4f, L_WATER, wl, 0f);
            }
        for (float z = z0; z < z1; z += 8f) {
            for (float x = x0; x < x1; x += 8f) {
                float ax = x, az = z, bx = x + 8f, bz = z + 8f;
                boolean overlaps = bx > cityX0 && ax < cityX1 && bz > cityZ0 && az < cityZ1;
                if (!overlaps) {
                    terrainCell(gb, ax, az, bx, bz);
                    vegetation(ch, gb, ax, az);
                    continue;
                }
                // pieces of the cell outside the city rectangle
                if (ax < cityX0) terrainCell(gb, ax, az, Math.min(bx, cityX0), bz);
                if (bx > cityX1) terrainCell(gb, Math.max(ax, cityX1), az, bx, bz);
                float mx0 = Math.max(ax, cityX0), mx1 = Math.min(bx, cityX1);
                if (mx1 > mx0) {
                    if (az < cityZ0) terrainCell(gb, mx0, az, mx1, Math.min(bz, cityZ0));
                    if (bz > cityZ1) terrainCell(gb, mx0, Math.max(az, cityZ1), mx1, bz);
                }
            }
        }
    }

    private void terrainCell(GeoBuilder gb, float ax, float az, float bx, float bz) {
        if (bx - ax < 0.01f || bz - az < 0.01f) return;
        float h0 = terrain.height(ax, az), h1 = terrain.height(bx, az), h2 = terrain.height(ax, bz), h3 = terrain.height(bx, bz);
        float max = Math.max(Math.max(h0, h1), Math.max(h2, h3)), min = Math.min(Math.min(h0, h1), Math.min(h2, h3));
        if (max < -0.6f) return;
        float avg = (h0 + h1 + h2 + h3) * 0.25f;
        float slope = (max - min) / Math.max(bx - ax, bz - az);
        float mx = (ax + bx) * 0.5f, mz = (az + bz) * 0.5f;
        int tex = slope > 0.6f ? lib.rock : (terrain.isSand(mx, mz, avg) ? lib.sand : (avg > 22f ? lib.hillGrass : lib.grass));
        gb.floorT(ax, az, bx, bz, 0f, tex, 2f, 2f, L_GROUND, 0, 0f, true);
    }

    private void vegetation(Chunk ch, GeoBuilder gb, float ax, float az) {
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
            if (r < 0.42f) plant(ch, Flora.COCONUT, ix, iz, jx, h, jz);
            else if (r < 0.52f) plant(ch, Flora.SEA_GRAPE, ix, iz, jx, h, jz);
            else if (r < 0.58f) plant(ch, Flora.SABAL_PALM, ix, iz, jx, h, jz);
            return;
        }
        if (h < 0.3f) return;
        if (terrain.isSand(jx, jz, h)) {
            boolean eastBeach = terrain.eastness(jx, jz) > 0.5f;
            if (eastBeach && h < 2.0f) {
                if (r < 0.08f && h > 0.5f) {
                    ch.sprite(Chunk.SPRITE_UMBRELLA, (int) (r2 * 4), jx, h, jz, Noise.rnd(ix, iz, sd + 504) * 6.2832f);
                    ch.boxes.add(new Box(jx - 0.12f, h - 1, jz - 0.12f, jx + 0.12f, h + 2.3f, jz + 0.12f));
                } else if (r > 0.993f) lifeguard(ch, gb, jx, h, jz);
                return;
            }
            // dunes and wild coast
            if (h < 1.0f && !eastBeach) { if (r < 0.25f) plant(ch, Flora.MANGROVE, ix, iz, jx, h, jz); }
            else if (r < 0.22f) plant(ch, Flora.SEA_OATS, ix, iz, jx, h, jz);
            else if (r < 0.32f) plant(ch, Flora.SEA_GRAPE, ix, iz, jx, h, jz);
            else if (r < 0.36f) plant(ch, Flora.COCONUT, ix, iz, jx, h, jz);
            return;
        }
        if (slope > 0.5f) {
            if (r < 0.12f) plant(ch, Flora.AGAVE, ix, iz, jx, h, jz);
            return;
        }
        // hills and countryside: mixed hammock
        float p = h < 7f ? 0.16f : (h < 25f ? 0.11f : 0.06f);
        if (r >= p) {
            if (r < p + 0.05f) plant(ch, r2 < 0.5f ? Flora.CROTON : Flora.SEA_GRAPE, ix, iz, jx, h, jz);
            return;
        }
        Flora f;
        if (r2 < 0.22f) f = Flora.LIVE_OAK;
        else if (r2 < 0.38f) f = Flora.GUMBO_LIMBO;
        else if (r2 < 0.58f) f = Flora.SABAL_PALM;
        else if (r2 < 0.74f) f = Flora.COCONUT;
        else if (r2 < 0.82f) f = Flora.POINCIANA;
        else if (r2 < 0.86f) f = Flora.BANYAN;
        else f = Flora.WASHINGTONIA;
        plant(ch, f, ix, iz, jx, h, jz);
    }

    /** Places a plant sprite (variant and facing from a position hash); trees get a trunk collider. */
    void plant(Chunk ch, Flora f, int hx, int hz, float x, float y, float z) {
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
        int[] blank = lib.facades(Kind.BLANK);
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
        gb.floorAreaT(c0, a, c1, b, 0.04f, lib.pavers, 2f, 2f, L_DECAL, 8f, 0, 0f);
    }

    // ------------------------------------------------------------------ roads

    private void roadSurfaces(GeoBuilder gb, float x0, float z0, float x1, float z1) {
        for (int i = 0; i <= nbx; i++)
            if (rx1[i] > x0 && rx0[i] < x1) roadCells(gb, rx0[i], cityZ0, rx1[i], cityZ1, x0, z0, x1, z1);
        for (int j = 0; j <= nbz; j++) {
            if (rz1[j] <= z0 || rz0[j] >= z1) continue;
            for (int i = 0; i < nbx; i++) roadCells(gb, rx1[i], rz0[j], rx0[i + 1], rz1[j], x0, z0, x1, z1);
        }
    }

    private void roadCells(GeoBuilder gb, float ax, float az, float bx, float bz, float x0, float z0, float x1, float z1) {
        for (float z = az; z < bz - 1e-3f; z += 8f) {
            float zz = Math.min(bz, z + 8f);
            for (float x = ax; x < bx - 1e-3f; x += 8f) {
                float xx = Math.min(bx, x + 8f);
                if (!in((x + xx) * 0.5f, (z + zz) * 0.5f, x0, z0, x1, z1)) continue;
                int t = Noise.rnd((int) x, (int) z, (int) seed + 9) < 0.07f ? lib.asphaltPatched : lib.asphalt;
                gb.floorT(x, z, xx, zz, 0f, t, 2f, 2f, L_GROUND, 0, 0f, false);
            }
        }
    }

    /**
     * One road segment between two intersections. {@code alongZ}: road runs N-S, crossing axis is x.
     * w0..w1 = road extent across, s0..s1 = extent along.
     */
    private void roadSegment(Chunk ch, GeoBuilder gb, boolean alongZ, float w0, float w1, float s0, float s1, boolean avenue) {
        float c = (w0 + w1) * 0.5f;
        if (avenue) {
            float m0 = c - 1.2f, m1 = c + 1.2f, a = s0 + 6f, b = s1 - 6f;
            if (b > a) {
                if (alongZ) median(ch, gb, m0, a, m1, b, true);
                else median(ch, gb, a, m0, b, m1, false);
            }
            line(gb, alongZ, c - 1.55f, c - 1.37f, s0 + 4f, s1 - 4f, lib.paintYellow);
            line(gb, alongZ, c + 1.37f, c + 1.55f, s0 + 4f, s1 - 4f, lib.paintYellow);
            dashes(gb, alongZ, c - 5.1f, s0 + 4f, s1 - 4f);
            dashes(gb, alongZ, c + 5.1f, s0 + 4f, s1 - 4f);
            line(gb, alongZ, w0 + 1.6f, w0 + 1.78f, s0 + 4f, s1 - 4f, lib.paintWhite);
            line(gb, alongZ, w1 - 1.78f, w1 - 1.6f, s0 + 4f, s1 - 4f, lib.paintWhite);
        } else {
            line(gb, alongZ, c - 0.26f, c - 0.08f, s0 + 4f, s1 - 4f, lib.paintYellow);
            line(gb, alongZ, c + 0.08f, c + 0.26f, s0 + 4f, s1 - 4f, lib.paintYellow);
            line(gb, alongZ, w0 + 2.4f, w0 + 2.58f, s0 + 4f, s1 - 4f, lib.paintWhite);
            line(gb, alongZ, w1 - 2.58f, w1 - 2.4f, s0 + 4f, s1 - 4f, lib.paintWhite);
        }
        for (int end = 0; end < 2; end++) {
            float cs0 = end == 0 ? s0 + 0.6f : s1 - 3.6f;
            for (float x = w0 + 0.8f; x < w1 - 1.2f; x += 1.2f) {
                if (avenue && x + 0.6f > c - 1.3f && x < c + 1.3f) continue;
                line(gb, alongZ, x, x + 0.6f, cs0, cs0 + 3f, lib.paintWhite);
            }
            float sl = end == 0 ? s0 + 4.3f : s1 - 4.7f;
            if (end == 0) line(gb, alongZ, w0 + 0.5f, c - (avenue ? 1.5f : 0.2f), sl, sl + 0.4f, lib.paintWhite);
            else line(gb, alongZ, c + (avenue ? 1.5f : 0.2f), w1 - 0.5f, sl, sl + 0.4f, lib.paintWhite);
        }
    }

    private void dashes(GeoBuilder gb, boolean alongZ, float c, float s0, float s1) {
        for (float s = s0 + 2f; s + 3f < s1; s += 9f) line(gb, alongZ, c - 0.1f, c + 0.1f, s, s + 3f, lib.paintWhite);
    }

    /** Painted strip following the road surface, split into 8 m pieces (detail: drops out at range). */
    private void line(GeoBuilder gb, boolean alongZ, float a, float b, float s0, float s1, int tex) {
        if (s1 <= s0 || b <= a) return;
        for (float s = s0; s < s1 - 1e-3f; s += 8f) {
            float e = Math.min(s1, s + 8f);
            if (alongZ) gb.floorT(a, s, b, e, 0.03f, tex, 1f, 1f, L_DECAL, Geo.FLAG_DETAIL | Geo.FLAG_NO_SUBDIV, 0f, false);
            else gb.floorT(s, a, e, b, 0.03f, tex, 1f, 1f, L_DECAL, Geo.FLAG_DETAIL | Geo.FLAG_NO_SUBDIV, 0f, false);
        }
    }

    private void median(Chunk ch, GeoBuilder gb, float ax, float az, float bx, float bz, boolean alongZ) {
        gb.floorAreaT(ax, az, bx, bz, CURB, lib.grass, 2f, 2f, L_RAISED, 8f, 0, 0f);
        curbs(gb, ax, az, bx, bz);
        int k = 0;
        if (alongZ) for (float z = az + 3f; z < bz - 1f; z += 11f, k++)
            plantAt(ch, k % 3 == 2 ? Flora.COCONUT : Flora.ROYAL_PALM, (ax + bx) * 0.5f, terrain.height((ax + bx) * 0.5f, z) + CURB, z);
        else for (float x = ax + 3f; x < bx - 1f; x += 11f, k++)
            plantAt(ch, k % 3 == 2 ? Flora.COCONUT : Flora.ROYAL_PALM, x, terrain.height(x, (az + bz) * 0.5f) + CURB, (az + bz) * 0.5f);
    }

    /** Curb faces (bottom on the road, top at sidewalk height) around a raised rectangle. */
    private void curbs(GeoBuilder gb, float ax, float az, float bx, float bz) {
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
            float rxv = -d[1], rzv = d[0];                       // right of travel
            float px = cx - d[0] * (along + 1.3f) + rxv * (across + 1.3f);
            float pz = cz - d[1] * (along + 1.3f) + rzv * (across + 1.3f);
            float yaw = (float) Math.atan2(-d[0], -d[1]);         // signal faces oncoming traffic
            float y = terrain.height(px, pz) + CURB;
            ch.sprite(Chunk.SPRITE_SIGNAL, id * 2 + (ns ? 1 : 0), px, y, pz, yaw);
            ch.boxes.add(new Box(px - 0.2f, y - 1f, pz - 0.2f, px + 0.2f, y + 6f, pz + 0.2f));
        }
    }

    // ------------------------------------------------------------------ blocks & lots

    private Kind district(int i, int j, Random rnd) {
        float bx = (rx1[i] + rx0[i + 1]) * 0.5f, bz = (rz1[j] + rz0[j + 1]) * 0.5f;
        float dx = (bx - (cityX0 + (cityX1 - cityX0) * 0.36f)) / (cityX1 - cityX0);
        float dz = (bz - (cityZ0 + (cityZ1 - cityZ0) * 0.42f)) / (cityZ1 - cityZ0);
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (i >= nbx - 2) return rnd.nextInt(3) == 0 ? Kind.HOTEL : Kind.DECO;
        if (i <= 1 && j >= nbz - 3) return Kind.INDUSTRIAL;
        if (d < 0.17f) return Kind.OFFICE;
        if (d < 0.3f) return rnd.nextBoolean() ? Kind.COMMERCIAL : (rnd.nextInt(3) == 0 ? Kind.OFFICE : Kind.RESIDENTIAL);
        return rnd.nextInt(3) == 0 ? Kind.COMMERCIAL : (rnd.nextInt(3) == 0 ? Kind.DECO : Kind.RESIDENTIAL);
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

        Kind dist = district(i, j, rnd);
        Flora street;
        switch (dist) {
            case DECO: case HOTEL: street = rnd.nextBoolean() ? Flora.COCONUT : Flora.WASHINGTONIA; break;
            case RESIDENTIAL: street = rnd.nextInt(3) == 0 ? Flora.POINCIANA : (rnd.nextBoolean() ? Flora.SABAL_PALM : Flora.LIVE_OAK); break;
            case OFFICE: street = rnd.nextBoolean() ? Flora.ROYAL_PALM : Flora.FOXTAIL_PALM; break;
            case COMMERCIAL: street = rnd.nextInt(3) == 0 ? null : (rnd.nextBoolean() ? Flora.SABAL_PALM : Flora.COCONUT); break;
            default: street = null; break;
        }
        streetFurniture(ch, bx0, bz0, bx1, bz1, street);

        float ix0 = bx0 + s, ix1 = bx1 - s, iz0 = bz0 + s, iz1 = bz1 - s;
        float depth = iz1 - iz0;
        boolean twoRows = depth >= 36f;
        float zMid = twoRows ? iz0 + Math.round(depth * (0.42f + rnd.nextFloat() * 0.16f)) : iz1;
        Ctx c = new Ctx(ch, gb, rnd);
        lotRow(c, ix0, iz0, ix1, zMid, true, !twoRows, dist);
        if (twoRows) lotRow(c, ix0, zMid, ix1, iz1, false, true, dist);
        gb.end();
    }

    private static final class Ctx {
        final Chunk ch;
        final GeoBuilder gb;
        final Random rnd;
        Ctx(Chunk ch, GeoBuilder gb, Random rnd) { this.ch = ch; this.gb = gb; this.rnd = rnd; }
    }

    private void streetFurniture(Chunk ch, float bx0, float bz0, float bx1, float bz1, Flora tree) {
        float in = 1.0f;
        boolean palms = tree != null;
        boolean wide = tree == Flora.LIVE_OAK || tree == Flora.POINCIANA;
        float step = palms ? (wide ? 16f : 12f) : 26f;
        for (int side = 0; side < 4; side++) {
            boolean horiz = side < 2;
            float a = horiz ? bx0 + 7f : bz0 + 7f, b = horiz ? bx1 - 7f : bz1 - 7f;
            int k = 0;
            for (float t = a; t <= b; t += step, k++) {
                float x, z, yaw;
                if (side == 0) { x = t; z = bz0 + in; yaw = (float) Math.PI; }
                else if (side == 1) { x = t; z = bz1 - in; yaw = 0f; }
                else if (side == 2) { x = bx0 + in; z = t; yaw = (float) (-Math.PI / 2); }
                else { x = bx1 - in; z = t; yaw = (float) (Math.PI / 2); }
                if (palms) {
                    plantAt(ch, tree, x, terrain.height(x, z) + CURB, z);
                    if ((k % 3) == 1) lampAt(ch, x + (horiz ? step * 0.5f : 0), z + (horiz ? 0 : step * 0.5f), yaw);
                } else if ((k & 1) == 0) lampAt(ch, x, z, yaw);
            }
        }
    }

    void lampAt(Chunk ch, float x, float z, float yaw) {
        float y = terrain.height(x, z) + CURB;
        ch.sprite(Chunk.SPRITE_LAMP, 0, x, y, z, yaw);
        ch.boxes.add(new Box(x - 0.12f, y - 1f, z - 0.12f, x + 0.12f, y + 7f, z + 0.12f));
    }

    private void lotRow(Ctx c, float x0, float z0, float x1, float z1, boolean facesNorth, boolean facesSouth, Kind dist) {
        float x = x0;
        while (x < x1 - 0.5f) {
            float w = 14f + 2f * c.rnd.nextInt(10);
            if (dist == Kind.OFFICE) w += 14f;
            if (x1 - (x + w) < 12f) w = x1 - x;
            float lx1 = Math.min(x1, x + w);
            lot(c, x, z0, lx1, z1, facesNorth, facesSouth, x <= x0 + 0.1f, lx1 >= x1 - 0.1f, dist);
            x = lx1;
        }
    }

    private void lot(Ctx c, float x0, float z0, float x1, float z1, boolean fN, boolean fS, boolean fW, boolean fE, Kind dist) {
        Random rnd = c.rnd;
        GeoBuilder gb = c.gb;
        int use = rnd.nextInt(100);
        if (use < 6) { parkingLot(c, x0, z0, x1, z1); return; }
        if (use < 11) { garden(c, x0, z0, x1, z1, rnd.nextBoolean()); return; }

        Kind kind = dist;
        if (kind == Kind.OFFICE && rnd.nextInt(4) == 0) kind = rnd.nextBoolean() ? Kind.GARAGE : Kind.HOTEL;
        if (kind == Kind.COMMERCIAL && rnd.nextInt(6) == 0) kind = Kind.GARAGE;
        if (kind == Kind.RESIDENTIAL && rnd.nextInt(5) == 0) kind = Kind.COMMERCIAL;

        float front = (kind == Kind.RESIDENTIAL || kind == Kind.HOTEL) ? 2f + rnd.nextInt(3) : 0f;
        float sideGap = (kind == Kind.RESIDENTIAL || kind == Kind.HOTEL || kind == Kind.OFFICE) ? 1f + rnd.nextInt(3) : 0f;
        float fx0 = x0 + (fW ? 0 : sideGap * 0.5f), fx1 = x1 - (fE ? 0 : sideGap * 0.5f);
        float fz0 = z0 + (fN ? front : 1f), fz1 = z1 - (fS ? front : 1f);
        if (fx1 - fx0 < 6f || fz1 - fz0 < 6f) { garden(c, x0, z0, x1, z1, false); return; }

        int lotTex = kind == Kind.RESIDENTIAL || kind == Kind.HOTEL ? lib.grass : lib.concrete;
        if (fz0 > z0 + 0.01f) gb.floorAreaT(x0, z0, x1, fz0, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0, 0f);
        if (fz1 < z1 - 0.01f) gb.floorAreaT(x0, fz1, x1, z1, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0, 0f);
        if (fx0 > x0 + 0.01f) gb.floorAreaT(x0, fz0, fx0, fz1, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0, 0f);
        if (fx1 < x1 - 0.01f) gb.floorAreaT(fx1, fz0, x1, fz1, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0, 0f);
        if (front >= 2f && lotTex == lib.grass) {
            boolean hedges = rnd.nextBoolean();
            Flora[] shrubs = {Flora.HIBISCUS, Flora.BOUGAINVILLEA, Flora.CROTON, Flora.BIRD_OF_PARADISE};
            for (int side = 0; side < 2; side++) {
                if (side == 0 ? !fN : !fS) continue;
                float zz0 = side == 0 ? z0 + 0.4f : z1 - 1.2f, zz1 = zz0 + 0.8f;
                if (hedges) hedge(c, fx0, zz0, fx1, zz1);
                else for (float x = fx0 + 1f; x < fx1 - 0.5f; x += 2.5f + rnd.nextFloat() * 2f)
                    plantAt(c.ch, shrubs[rnd.nextInt(shrubs.length)], x, terrain.height(x, zz0) + CURB, (zz0 + zz1) * 0.5f);
            }
            if (kind == Kind.HOTEL || rnd.nextInt(3) == 0) {
                float tz = fN ? z0 + (fz0 - z0) * 0.6f : z1 - (z1 - fz1) * 0.6f;
                plantAt(c.ch, kind == Kind.HOTEL ? Flora.DATE_PALM : Flora.FOXTAIL_PALM, fx0 + 1.2f, terrain.height(fx0 + 1.2f, tz) + CURB, tz);
            }
        }
        new BuildingGen(this, lib, gb, rnd, c.ch).build(kind, fx0, fz0, fx1, fz1,
                fN && front < 0.5f, fS && front < 0.5f, fW, fE, fN, fS);
    }

    private void parkingLot(Ctx c, float x0, float z0, float x1, float z1) {
        c.gb.floorAreaT(x0, z0, x1, z1, CURB, lib.parking, 2.5f, 5f, L_RAISED, 8f, 0, 0f);
        for (float x = x0 + 2f; x < x1 - 1f; x += 10f) lampAt(c.ch, x, (z0 + z1) * 0.5f, 0f);
    }

    private void garden(Ctx c, float x0, float z0, float x1, float z1, boolean plaza) {
        c.gb.floorAreaT(x0, z0, x1, z1, CURB, plaza ? lib.pavers : lib.grass, 2f, 2f, L_RAISED, 8f, 0, 0f);
        Flora[] trees = {Flora.LIVE_OAK, Flora.GUMBO_LIMBO, Flora.POINCIANA, Flora.SABAL_PALM, Flora.COCONUT, Flora.ROYAL_PALM};
        Flora[] shrubs = {Flora.HIBISCUS, Flora.BOUGAINVILLEA, Flora.CROTON, Flora.BIRD_OF_PARADISE, Flora.SEA_GRAPE};
        boolean bigTree = !plaza && (x1 - x0) > 18f && (z1 - z0) > 16f && c.rnd.nextInt(3) == 0;
        if (bigTree) {   // a park banyan
            float bx = (x0 + x1) * 0.5f, bz = (z0 + z1) * 0.5f;
            plantAt(c.ch, Flora.BANYAN, bx, terrain.height(bx, bz) + CURB, bz);
        }
        int k = 0;
        for (float z = z0 + 3f; z < z1 - 2f; z += 7f + c.rnd.nextInt(4))
            for (float x = x0 + 3f; x < x1 - 2f; x += 7f + c.rnd.nextInt(4), k++) {
                if (bigTree && Math.abs(x - (x0 + x1) * 0.5f) < 8f && Math.abs(z - (z0 + z1) * 0.5f) < 8f) continue;
                float y = terrain.height(x, z) + CURB;
                if (plaza) planter(c, x, z, k);
                else if (c.rnd.nextInt(3) > 0) plantAt(c.ch, trees[c.rnd.nextInt(trees.length)], x + c.rnd.nextFloat() * 2f, y, z + c.rnd.nextFloat() * 2f);
                else plantAt(c.ch, shrubs[c.rnd.nextInt(shrubs.length)], x, y, z);
            }
    }

    private void hedge(Ctx c, float x0, float z0, float x1, float z1) {
        float y = terrain.height((x0 + x1) * 0.5f, (z0 + z1) * 0.5f) + CURB;
        c.gb.box(x0, y - 0.2f, z0, x1, y + 1.0f, z1, lib.hedge, lib.hedge, 0.5f, 0.5f, L_OBJECT, 0f, 0);
        c.ch.boxes.add(new Box(x0, y - 1f, z0, x1, y + 1.0f, z1));
    }

    private void planter(Ctx c, float x, float z, int k) {
        float y = terrain.height(x, z) + CURB;
        c.gb.box(x - 1f, y - 0.2f, z - 1f, x + 1f, y + 0.6f, z + 1f, lib.planter, lib.hedge, 0.5f, 0.5f, L_OBJECT, 0f, 0);
        c.ch.boxes.add(new Box(x - 1f, y - 1f, z - 1f, x + 1f, y + 0.6f, z + 1f));
        plantAt(c.ch, (k & 1) == 0 ? Flora.DATE_PALM : Flora.FOXTAIL_PALM, x, y + 0.6f, z);
        if ((k % 3) == 0) plantAt(c.ch, Flora.BIRD_OF_PARADISE, x + 1.6f, y, z + 0.4f);
    }
}
