package com.harlequinxd.drivehard.core.tex;

/** Hash-based tileable noise for texture synthesis. */
public final class Noise {
    private Noise() {}

    public static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /** White noise in [0,1). */
    public static float rnd(int x, int y, int seed) {
        return (hash(x, y, seed) & 0xFFFF) / 65536f;
    }

    /** Value noise that tiles over a w x h texture with {@code cells} cells across the width. */
    public static float value(int x, int y, int w, int h, int cellsX, int cellsY, int seed) {
        float fx = x * cellsX / (float) w, fy = y * cellsY / (float) h;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        float tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        int x0 = ix % cellsX, x1 = (ix + 1) % cellsX, y0 = iy % cellsY, y1 = (iy + 1) % cellsY;
        float a = rnd(x0, y0, seed), b = rnd(x1, y0, seed), c = rnd(x0, y1, seed), d = rnd(x1, y1, seed);
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * ty;
    }

    /** 3-octave fractal noise in [0,1), tileable. */
    public static float fbm(int x, int y, int w, int h, int seed) {
        int cx = Math.max(1, w / 16), cy = Math.max(1, h / 16);
        return value(x, y, w, h, cx, cy, seed) * 0.5f
                + value(x, y, w, h, cx * 2, cy * 2, seed + 1) * 0.3f
                + value(x, y, w, h, cx * 4, cy * 4, seed + 2) * 0.2f;
    }
}
