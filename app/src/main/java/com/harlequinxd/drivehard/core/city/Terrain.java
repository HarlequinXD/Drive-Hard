package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.tex.Noise;

/**
 * Island heightfield (pure function of position, so any chunk can be generated on its own).
 *
 * The island is 128 x 128 km. The city sits on its east coast on a gently rolling plateau
 * ringed by hills; beyond them the land opens into coastal plains and farmland, rolling
 * hill country and a few mountain ranges, with beaches all around the coast. Roads are cut
 * into the terrain: {@link #height} blends the natural ground toward each road's smoothed
 * profile so roads sit on embankments and in cuttings.
 */
public final class Terrain {
    public static final float SAND_LEVEL = 2.2f;
    public static final float ISLAND_SIZE = 128000f;
    private final int seed;
    private final float cx0, cz0, cx1, cz1;   // city rectangle
    private final float czc;
    /** Island extents (before coastline wobble). */
    public final float islandX0, islandX1, islandZ0, islandZ1;
    /** Set once the road network is built; null while it is being generated. */
    public RoadNetwork roads;

    public Terrain(int seed, float cityX0, float cityZ0, float cityX1, float cityZ1) {
        this.seed = seed;
        this.cx0 = cityX0;
        this.cz0 = cityZ0;
        this.cx1 = cityX1;
        this.cz1 = cityZ1;
        this.czc = (cityZ0 + cityZ1) * 0.5f;
        islandX1 = cityX1;
        islandX0 = cityX1 - ISLAND_SIZE;
        islandZ0 = czc - ISLAND_SIZE * 0.5f;
        islandZ1 = czc + ISLAND_SIZE * 0.5f;
    }

    /** Smooth value noise (non-tiling), roughly 0..1. */
    private float vn(float x, float z, int s) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        float a = Noise.rnd(ix, iz, s), b = Noise.rnd(ix + 1, iz, s), c = Noise.rnd(ix, iz + 1, s), d = Noise.rnd(ix + 1, iz + 1, s);
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Distance from the city rectangle (0 inside). */
    public float cityDistance(float x, float z) {
        float dx = Math.max(Math.max(cx0 - x, 0f), x - cx1);
        float dz = Math.max(Math.max(cz0 - z, 0f), z - cz1);
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** Final ground height: natural terrain shaped by the roads. */
    public float height(float x, float z) {
        float n = natural(x, z);
        RoadNetwork r = roads;
        return r == null ? n : r.shapeTerrain(x, z, n);
    }

    /** Ground height without road cuttings and embankments. */
    public float natural(float x, float z) {
        float d = cityDistance(x, z);
        float east = eastness(x, z);
        float over = coastOver(x, z);
        float local = 0f, regional = 0f;
        float wReg = smooth(250f, 1100f, d);
        if (wReg < 1f) {
            // gentle plateau under the city (max slope a few percent) and the hills around it
            float base = 3.5f + 4.5f * vn(x / 330f, z / 330f, seed);
            float hill = vn(x / 230f, z / 230f, seed + 11) * 0.65f + vn(x / 90f, z / 90f, seed + 12) * 0.35f;
            hill = hill * hill * 1.4f;
            local = base + hill * 58f * smooth(30f, 200f, d) * (1f - east);
        }
        if (wReg > 0f) {
            regional = regional(x, z);
            // the interior sinks toward sea level near the coast
            if (over > -2500f) regional = 3f + (regional - 3f) * smooth(-200f, 2500f, -over);
        }
        float h = local + (regional - local) * wReg;
        // beach profile: gentle sand slope, then a drop-off under the water
        float drop = over > 0f ? over * 0.06f + Math.max(0f, over - 55f) * 0.35f : 0f;
        return Math.max(-14f, h - drop);
    }

    /** Plains, hill country and mountain ranges of the island interior. */
    private float regional(float x, float z) {
        float plains = 5f + 16f * vn(x / 6000f, z / 6000f, seed + 30) + 6f * vn(x / 1400f, z / 1400f, seed + 33);
        // rolling hill country
        float hm = smooth(0.42f, 0.7f, vn(x / 9000f, z / 9000f, seed + 31));
        float hv = vn(x / 700f, z / 700f, seed + 34) * 0.6f + vn(x / 260f, z / 260f, seed + 35) * 0.4f;
        float hills = hm * hv * hv * 95f;
        // mountain ranges with ridges
        float mm = smooth(0.6f, 0.85f, vn(x / 16000f, z / 16000f, seed + 32));
        float mountains = 0f;
        if (mm > 0f) {
            float r1 = 1f - Math.abs(2f * vn(x / 2600f, z / 2600f, seed + 36) - 1f);
            float r2 = 1f - Math.abs(2f * vn(x / 900f, z / 900f, seed + 37) - 1f);
            mountains = mm * (120f + 260f * r1 * r1 + 70f * r2 * r2);
        }
        return plains + hills + mountains;
    }

    public float eastness(float x, float z) {
        return smooth(cx1 - 120f, cx1 + 20f, x) * (1f - smooth(0f, 260f, Math.max(cz0 - z, z - cz1)));
    }

    /** Wobbly coastline offset (metres) along an edge coordinate. */
    private float wobble(float t, int s, float amp) {
        return (vn(t / 5200f, 0.5f, seed + s) - 0.5f) * amp + (vn(t / 1300f, 7.5f, seed + s + 1) - 0.5f) * amp * 0.3f;
    }

    /** Metres past the point where the coast starts falling toward the sea (negative inland). */
    public float coastOver(float x, float z) {
        // east coast: straight past the city, bays and headlands further north and south
        float awayZ = Math.max(0f, Math.abs(z - czc) - 900f);
        float eastEdge = islandX1 + wobble(z, 40, 2600f) * smooth(0f, 3000f, awayZ) + 0f;
        float westEdge = islandX0 + wobble(z, 42, 6000f);
        float northEdge = islandZ0 + wobble(x, 44, 6000f);
        float southEdge = islandZ1 + wobble(x, 46, 6000f);
        float o = x - eastEdge;
        o = Math.max(o, westEdge - x);
        o = Math.max(o, northEdge - z);
        o = Math.max(o, z - southEdge);
        return o;
    }

    /** True for beach sand: the coastal strip, or anything just above sea level. */
    public boolean isSand(float x, float z, float h) {
        if (h < SAND_LEVEL) return true;
        return coastOver(x, z) > -40f && h < 6f;
    }

    /** Surface normal from central differences. */
    public void normal(float x, float z, float[] out) {
        float e = 1.5f;
        float dx = (height(x + e, z) - height(x - e, z)) / (2 * e);
        float dz = (height(x, z + e) - height(x, z - e)) / (2 * e);
        float l = (float) Math.sqrt(dx * dx + 1 + dz * dz);
        out[0] = -dx / l;
        out[1] = 1f / l;
        out[2] = -dz / l;
    }

    /** True if the line of sight from the eye to the point is blocked by terrain. */
    public boolean occluded(float ex, float ey, float ez, float px, float py, float pz) {
        for (int i = 1; i < 12; i++) {
            float t = i / 12f;
            float x = ex + (px - ex) * t, y = ey + (py - ey) * t, z = ez + (pz - ez) * t;
            if (height(x, z) > y + 0.6f) return true;
        }
        return false;
    }
}
