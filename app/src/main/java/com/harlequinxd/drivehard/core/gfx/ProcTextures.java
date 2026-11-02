package com.harlequinxd.drivehard.core.gfx;

/**
 * Procedurally generated 32x32 world textures packed into a 256x256 atlas (8x8 tiles).
 * 32 texels = 1 world metre. All ARGB.
 */
public final class ProcTextures {
    public static final int TILE = 32, GRID = 8, SIZE = TILE * GRID;

    public static final int CONCRETE = 0, PAVERS = 1, ASPHALT = 2, ASPHALT_CENTER = 3, ASPHALT_DASH = 4,
            GRASS = 5, SAND = 6, CURB = 7, CONCRETE_WALL = 8, CRATE = 9, STUCCO_PINK = 10, CROSSWALK = 11,
            STUCCO_TEAL = 12, WHITE = 13, STUCCO_CREAM = 14, METAL = 15;

    public final int[] pixels = new int[SIZE * SIZE];

    public ProcTextures() {
        for (int t = 0; t < GRID * GRID; t++) {
            int[] tile = generate(t);
            if (tile == null) continue;
            int ox = (t % GRID) * TILE, oy = (t / GRID) * TILE;
            for (int y = 0; y < TILE; y++)
                System.arraycopy(tile, y * TILE, pixels, (oy + y) * SIZE + ox, TILE);
        }
    }

    /** UV rectangle of a tile, inset half a texel to avoid bleeding: {u0, v0, u1, v1}. */
    public static float[] uv(int tile) {
        float inset = 0.5f / SIZE;
        float u0 = (tile % GRID) / (float) GRID, v0 = (tile / GRID) / (float) GRID;
        float s = 1f / GRID;
        return new float[]{u0 + inset, v0 + inset, u0 + s - inset, v0 + s - inset};
    }

    // ------------------------------------------------------------------ noise helpers

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /** White noise in [0,1). */
    private static float rnd(int x, int y, int seed) {
        return (hash(x, y, seed) & 0xFFFF) / 65536f;
    }

    /** Tileable value noise with period {@code period} cells over the 32px tile. */
    private static float vnoise(int x, int y, int period, int seed) {
        float cell = TILE / (float) period;
        float fx = x / cell, fy = y / cell;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        float tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        float a = rnd(ix % period, iy % period, seed), b = rnd((ix + 1) % period, iy % period, seed);
        float c = rnd(ix % period, (iy + 1) % period, seed), d = rnd((ix + 1) % period, (iy + 1) % period, seed);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }

    private static float fbm(int x, int y, int seed) {
        return vnoise(x, y, 4, seed) * 0.5f + vnoise(x, y, 8, seed + 1) * 0.3f + vnoise(x, y, 16, seed + 2) * 0.2f;
    }

    private static int rgb(float r, float g, float b) {
        return 0xFF000000 | (c8(r) << 16) | (c8(g) << 8) | c8(b);
    }

    private static int c8(float v) { return v < 0 ? 0 : v > 1 ? 255 : (int) (v * 255f + 0.5f); }

    private static int tint(int base, float k) {
        return rgb(((base >> 16) & 255) / 255f * k, ((base >> 8) & 255) / 255f * k, (base & 255) / 255f * k);
    }

    // ------------------------------------------------------------------ tiles

    private static int[] generate(int t) {
        int[] p = new int[TILE * TILE];
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int c = texel(t, x, y);
                if (c == 0) return null;
                p[y * TILE + x] = c;
            }
        }
        return p;
    }

    private static int texel(int t, int x, int y) {
        float n = fbm(x, y, t * 31 + 7);
        float w = rnd(x, y, t * 13 + 3);
        switch (t) {
            case CONCRETE: {
                float k = 0.70f + n * 0.18f + w * 0.05f;
                if (x == 0 || y == 0) k *= 0.72f;                        // slab joints
                if (crack(x, y, 11)) k *= 0.86f;
                return rgb(k * 0.98f, k * 0.97f, k * 0.93f);
            }
            case PAVERS: {
                int bx = x % 16, by = y % 8;
                int shift = ((y / 8) & 1) * 8;
                bx = (x + shift) % 16;
                float k = 0.72f + rnd((x + shift) / 16, y / 8, 41) * 0.12f + n * 0.08f;
                if (bx == 0 || by == 0) k *= 0.62f;
                return rgb(k * 0.95f, k * 0.86f, k * 0.76f);
            }
            case ASPHALT: case ASPHALT_CENTER: case ASPHALT_DASH: case CROSSWALK: {
                float k = 0.24f + n * 0.08f + w * 0.07f;
                if (rnd(x, y, 99) > 0.96f) k += 0.12f;                    // aggregate
                int c = rgb(k, k, k * 1.04f);
                if (t == ASPHALT_CENTER && (y == 13 || y == 14 || y == 17 || y == 18))
                    return paint(0xE0B030, n, w);
                if (t == ASPHALT_DASH && (y == 15 || y == 16) && x >= 4 && x < 28)
                    return paint(0xE8E8E0, n, w);
                if (t == CROSSWALK && (x % 16) >= 3 && (x % 16) < 12)
                    return paint(0xE8E8E0, n, w);
                return c;
            }
            case GRASS: {
                float k = 0.55f + n * 0.3f + w * 0.2f;
                boolean blade = rnd(x, y, 5) > 0.82f;
                return blade ? rgb(0.45f * k, 0.70f * k, 0.25f * k) : rgb(0.26f * k, 0.50f * k, 0.18f * k);
            }
            case SAND: {
                float k = 0.82f + n * 0.12f + w * 0.08f;
                return rgb(0.93f * k, 0.84f * k, 0.64f * k);
            }
            case CURB: {
                float k = 0.78f + n * 0.1f + w * 0.05f;
                if (y < 3) k *= 0.8f;
                if (x == 0) k *= 0.7f;
                return rgb(k, k, k * 0.97f);
            }
            case CONCRETE_WALL: {
                float k = 0.62f + n * 0.16f + w * 0.05f;
                if (y == 0 || y == 16) k *= 0.75f;                        // form lines
                if ((x == 8 || x == 24) && (y == 4 || y == 20)) k *= 0.5f; // tie holes
                float stain = Math.max(0, vnoise(x, y, 4, 77) - 0.6f) * (y / 32f);
                k -= stain * 0.5f;
                return rgb(k * 0.97f, k * 0.96f, k * 0.93f);
            }
            case CRATE: {
                float k = 0.58f + n * 0.15f + (float) Math.sin(y * 1.7 + n * 6) * 0.04f;
                boolean frame = x < 3 || x > 28 || y < 3 || y > 28 || Math.abs(x - y) < 2;
                if (frame) k *= 0.8f;
                if ((y % 8) == 0) k *= 0.75f;
                return rgb(0.80f * k, 0.58f * k, 0.34f * k);
            }
            case STUCCO_PINK: return stucco(0xF2B8B0, n, w);
            case STUCCO_TEAL: return stucco(0x8FD3CB, n, w);
            case STUCCO_CREAM: return stucco(0xF2E6C8, n, w);
            case WHITE: return 0xFFFFFFFF;
            case METAL: {
                float k = 0.55f + n * 0.1f + ((x % 8) == 0 ? -0.12f : 0f);
                return rgb(k * 0.95f, k, k * 1.05f);
            }
            default: return 0;
        }
    }

    private static int stucco(int base, float n, float w) {
        return tint(base, 0.84f + n * 0.12f + w * 0.06f);
    }

    private static int paint(int base, float n, float w) {
        return tint(base, 0.82f + n * 0.12f + w * 0.08f);
    }

    private static boolean crack(int x, int y, int seed) {
        // a single wandering crack line
        float cy = 6 + rnd(0, 0, seed) * 20 + (float) Math.sin(x * 0.45f + seed) * 3f;
        return x > 5 && x < 26 && Math.abs(y - cy) < 0.6f;
    }
}
