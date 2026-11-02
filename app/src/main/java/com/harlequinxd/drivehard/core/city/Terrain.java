package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.tex.Noise;

/**
 * Island heightfield (pure function of position, so any chunk can be generated on its own).
 * The city sits on a gently rolling plateau; hills rise around it to the west, north and south;
 * beaches ring the coast and the east side drops quickly to the Ocean Drive beach.
 */
public final class Terrain {
    public static final float SAND_LEVEL = 2.2f;
    private final int seed;
    private final float cx0, cz0, cx1, cz1;   // city rectangle

    public Terrain(int seed, float cityX0, float cityZ0, float cityX1, float cityZ1) {
        this.seed = seed;
        this.cx0 = cityX0;
        this.cz0 = cityZ0;
        this.cx1 = cityX1;
        this.cz1 = cityZ1;
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

    public float height(float x, float z) {
        // gentle plateau under the city (max slope a few percent)
        float base = 3.5f + 4.5f * vn(x / 330f, z / 330f, seed);
        float d = cityDistance(x, z);
        float east = eastness(x, z);
        // hills around the city
        float hill = vn(x / 230f, z / 230f, seed + 11) * 0.65f + vn(x / 90f, z / 90f, seed + 12) * 0.35f;
        hill = hill * hill * 1.4f;
        float over = coastOver(x, z, d, east);
        float hills = hill * 58f * smooth(30f, 200f, d) * (1f - east) * (1f - smooth(-80f, 10f, over));
        // beach profile: gentle sand slope, then a drop-off under the water
        float drop = over > 0f ? over * 0.06f + Math.max(0f, over - 55f) * 0.35f : 0f;
        return Math.max(-14f, base + hills - drop);
    }

    public float eastness(float x, float z) {
        return smooth(cx1 - 120f, cx1 + 20f, x) * (1f - smooth(0f, 260f, Math.max(cz0 - z, z - cz1)));
    }

    /** Metres past the point where the coast starts falling toward the sea (negative inland). */
    private float coastOver(float x, float z, float d, float east) {
        double ang = Math.atan2(z - (cz0 + cz1) * 0.5f, x - (cx0 + cx1) * 0.5f);
        float wobble = vn((float) (ang * 3.0 + 10.0), 0.5f, seed + 21) * 140f + vn((float) (ang * 9.0 + 4.0), 0.5f, seed + 22) * 50f;
        float coast = (300f + wobble) * (1f - east);
        return d - coast;
    }

    /** True for beach sand: the coastal strip, or anything just above sea level. */
    public boolean isSand(float x, float z, float h) {
        if (h < SAND_LEVEL) return true;
        float d = cityDistance(x, z);
        return coastOver(x, z, d, eastness(x, z)) > 28f && h < 6f;
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
