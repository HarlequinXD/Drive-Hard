package com.harlequinxd.drivehard.core.tex;

import java.util.Random;

/**
 * Procedural kanji-like glyphs for signage. Each glyph is built from strokes arranged in the
 * usual component layouts (single, left|right radical, top/bottom, enclosure), which reads as
 * Japanese lettering at low resolution without needing a font.
 */
public final class Kanji {
    private Kanji() {}

    /** Draws glyph {@code id} of size n x n pixels (scaled) at x,y. */
    public static void glyph(int[] px, int w, int h, int id, int x, int y, int n, int scale, int color) {
        boolean[] g = bitmap(id, n);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++)
                if (g[j * n + i])
                    for (int sy = 0; sy < scale; sy++)
                        for (int sx = 0; sx < scale; sx++) {
                            int X = x + i * scale + sx, Y = y + j * scale + sy;
                            if (X >= 0 && X < w && Y >= 0 && Y < h) px[Y * w + X] = color;
                        }
    }

    /** A horizontal run of glyphs; returns the width drawn. */
    public static int row(int[] px, int w, int h, int seed, int count, int x, int y, int n, int scale, int color) {
        int adv = (n + 1) * scale;
        for (int i = 0; i < count; i++) glyph(px, w, h, seed * 31 + i * 7, x + i * adv, y, n, scale, color);
        return count * adv - scale;
    }

    /** A vertical column of glyphs (vertical signboards). */
    public static void column(int[] px, int w, int h, int seed, int count, int x, int y, int n, int scale, int color) {
        int adv = (n + 1) * scale;
        for (int i = 0; i < count; i++) glyph(px, w, h, seed * 31 + i * 7, x, y + i * adv, n, scale, color);
    }

    static boolean[] bitmap(int id, int n) {
        Random r = new Random(id * 2654435761L + 12345L);
        boolean[] g = new boolean[n * n];
        int layout = r.nextInt(5);
        int m = n - 1;
        switch (layout) {
            case 0: region(g, n, r, 0, 0, m, m, 3 + r.nextInt(2)); break;
            case 1: {                                   // left radical | right part
                int split = n / 3;
                region(g, n, r, 0, 0, split - 1, m, 2);
                region(g, n, r, split + 1, 0, m, m, 3);
                break;
            }
            case 2: {                                   // top / bottom
                int split = n / 2 - 1;
                region(g, n, r, 0, 0, m, split, 2);
                region(g, n, r, 0, split + 2, m, m, 2 + r.nextInt(2));
                break;
            }
            case 3: {                                   // enclosure
                for (int i = 0; i < n; i++) { g[i] = true; g[i * n] = true; g[i * n + m] = true; }
                if (r.nextBoolean()) for (int i = 0; i < n; i++) g[m * n + i] = true;
                region(g, n, r, 2, 2, m - 2, m - 2, 2);
                break;
            }
            default: {                                  // katakana-like: few strokes with a slant
                hline(g, n, 1, 0, m - 1);
                int x0 = 1 + r.nextInt(m - 2);
                for (int j = 1; j < n; j++) set(g, n, Math.max(0, x0 - (j * (r.nextInt(2) + 1)) / 3), j);
                if (r.nextBoolean()) for (int j = 2; j < n; j++) set(g, n, Math.min(m, x0 + j / 2), j);
                else hline(g, n, m - 1, 1, m);
                break;
            }
        }
        return g;
    }

    private static void region(boolean[] g, int n, Random r, int x0, int y0, int x1, int y1, int strokes) {
        boolean hasH = false, hasV = false;
        for (int s = 0; s < strokes || !hasH || !hasV; s++) {
            if (s > strokes + 3) break;
            int k = r.nextInt(6);
            if (k <= 2 || !hasH && s >= strokes) {          // horizontal
                int y = y0 + r.nextInt(Math.max(1, y1 - y0 + 1));
                int a = x0 + (r.nextInt(3) == 0 ? r.nextInt(Math.max(1, (x1 - x0) / 2 + 1)) : 0);
                int b = x1 - (r.nextInt(3) == 0 ? r.nextInt(Math.max(1, (x1 - x0) / 2 + 1)) : 0);
                hline(g, n, y, a, b);
                hasH = true;
            } else if (k <= 4) {                             // vertical
                int x = x0 + r.nextInt(Math.max(1, x1 - x0 + 1));
                int a = y0 + (r.nextInt(3) == 0 ? r.nextInt(Math.max(1, (y1 - y0) / 2 + 1)) : 0);
                for (int y = a; y <= y1; y++) set(g, n, x, y);
                hasV = true;
            } else {                                         // short diagonal / dot
                int x = x0 + r.nextInt(Math.max(1, x1 - x0 + 1)), y = y0 + r.nextInt(Math.max(1, y1 - y0 + 1));
                int dx = r.nextBoolean() ? 1 : -1;
                for (int i = 0; i < 2 + r.nextInt(2); i++) set(g, n, x + i * dx, y + i);
            }
        }
    }

    private static void hline(boolean[] g, int n, int y, int a, int b) {
        for (int x = a; x <= b; x++) set(g, n, x, y);
    }

    private static void set(boolean[] g, int n, int x, int y) {
        if (x >= 0 && x < n && y >= 0 && y < n) g[y * n + x] = true;
    }
}
