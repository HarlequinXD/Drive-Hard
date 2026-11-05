package com.harlequinxd.drivehard.core.tex;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/**
 * Painting helpers that push the procedural textures toward photographs: glass that reflects a
 * sky with clouds and the street below, bevelled frames with real light and shadow, curtains with
 * folds, rain streaks and grime under ledges, sun-faded colour variation and ambient occlusion in
 * corners.
 */
public final class Photo {
    private Photo() {}

    /**
     * Window glass seen from the street: the upper part mirrors the sky (pale toward the
     * horizon, with soft clouds), the lower part the darker buildings opposite; a faint view of the
     * room shows through, and the pane darkens toward its frame.
     */
    public static void glass(Img m, int x0, int y0, int x1, int y1, int tint, Random r) {
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        int seed = r.nextInt();
        float horizon = 0.45f + r.nextFloat() * 0.25f;            // where the reflected skyline cuts the pane
        int sky = mix(0xFF9DB8D2, tint, 0.35f), skyHigh = mix(0xFF6F93B8, tint, 0.3f);
        int city = mix(0xFF3A4048, tint, 0.25f), room = mix(0xFF2A2C30, tint, 0.15f);
        // a reflected skyline: blocky silhouettes along the horizon
        int[] skyline = new int[w];
        for (int x = 0; x < w; x++) {
            int blk = (x + (seed & 7)) / Math.max(2, w / 5);
            skyline[x] = (int) (h * (horizon - 0.18f * Noise.rnd(blk, 3, seed)));
        }
        for (int y = 0; y < h; y++) {
            float t = y / (float) Math.max(1, h - 1);
            for (int x = 0; x < w; x++) {
                int c;
                if (y < skyline[x]) {
                    c = mix(skyHigh, sky, t / Math.max(0.05f, horizon));
                    // clouds: soft low-frequency blobs
                    float cl = Noise.fbm(x + x0 * 3, y + y0 * 3, 64, 64, seed + 5);
                    if (cl > 0.52f) c = mix(c, 0xFFEFF3F6, Math.min(0.7f, (cl - 0.52f) * 2.4f));
                } else {
                    float d = (y - skyline[x]) / (float) Math.max(1, h - skyline[x]);
                    c = mix(city, room, d);
                    // lit windows of the building opposite, tiny and sparse
                    if (Noise.rnd(x / 2 + x0, y / 2 + y0, seed + 11) > 0.93f) c = mix(c, 0xFFB8B0A0, 0.35f);
                }
                // fresnel: brighter toward the top edge, darker in the bottom corners
                float edge = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y)) / (float) Math.max(1, Math.min(w, h) / 4);
                if (edge < 1f) c = mul(c, 0.82f + 0.18f * edge);
                m.set(x0 + x, y0 + y, c);
            }
        }
        // one or two diagonal highlights where the sun catches the pane
        int streaks = 1 + r.nextInt(2);
        for (int k = 0; k < streaks; k++) {
            int off = r.nextInt(w + h), width = 1 + r.nextInt(3);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int d = x + y - off;
                    if (d >= 0 && d < width) m.set(x0 + x, y0 + y, mix(m.get(x0 + x, y0 + y), 0xFFFFFFFF, 0.28f));
                }
        }
    }

    /** Curtains drawn part-way across a pane, with folds that catch the light. */
    public static void curtains(Img m, int x0, int y0, int x1, int y1, int colour, float cover, boolean bothSides) {
        int w = x1 - x0;
        int cw = Math.max(1, (int) (w * cover * (bothSides ? 0.5f : 1f)));
        for (int y = y0; y < y1; y++)
            for (int k = 0; k < cw; k++) {
                float fold = 0.78f + 0.22f * (float) Math.sin(k * 1.9f);
                int c = mul(colour, fold * (0.9f + 0.1f * (y - y0) / (float) Math.max(1, y1 - y0)));
                m.set(x0 + k, y, c);
                if (bothSides) m.set(x1 - 1 - k, y, c);
            }
    }

    /** Venetian blinds lowered to {@code down} (0..1) of the pane. */
    public static void blinds(Img m, int x0, int y0, int x1, int y1, int colour, float down) {
        int by = y0 + (int) ((y1 - y0) * down);
        for (int y = y0; y < by; y++) {
            float slat = ((y - y0) % 3) / 2f;
            int c = mul(colour, 0.82f + 0.25f * slat);
            for (int x = x0; x < x1; x++) m.set(x, y, c);
        }
        if (by < y1) for (int x = x0; x < x1; x++) m.set(x, by, mul(colour, 0.6f));
    }

    /** A frame of thickness t round [x0,x1)x[y0,y1) lit from the top left: bevel highlight and shadow. */
    public static void frame(Img m, int x0, int y0, int x1, int y1, int t, int colour) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                boolean in = x >= x0 + t && x < x1 - t && y >= y0 + t && y < y1 - t;
                if (in) continue;
                int c = colour;
                if (y == y0 || x == x0) c = mul(colour, 1.18f);               // outer highlight
                else if (y == y1 - 1 || x == x1 - 1) c = mul(colour, 0.7f);    // outer shadow
                else if (y == y1 - t - 1 + 1 && x >= x0 + t && x < x1 - t) c = mul(colour, 1.1f);
                else if (y == y0 + t - 1 && x >= x0 + t - 1 && x < x1 - t + 1) c = mul(colour, 0.78f);  // inner shadow under the head
                m.set(x, y, c);
            }
        // reveal shadow cast onto the glass by the head of the frame
        for (int x = x0 + t; x < x1 - t; x++) {
            int y = y0 + t;
            if (y < y1 - t) m.set(x, y, mul(m.get(x, y), 0.72f));
            if (y + 1 < y1 - t) m.set(x, y + 1, mul(m.get(x, y + 1), 0.88f));
        }
    }

    /** Rain streaks running down a wall from a ledge at y, between x0 and x1, fading out. */
    public static void streaks(Img m, int x0, int x1, int y, int length, float strength, int seed) {
        for (int x = Math.max(0, x0); x < Math.min(m.w, x1); x++) {
            float s = Noise.rnd(x, 7, seed);
            if (s < 0.45f) continue;
            int len = (int) (length * (0.4f + 0.6f * Noise.rnd(x, 9, seed)));
            float k = strength * (s - 0.45f) / 0.55f;
            for (int d = 0; d < len && y + d < m.h; d++) {
                float f = k * (1f - d / (float) len);
                m.set(x, y + d, mul(m.get(x, y + d), 1f - f * 0.35f));
            }
        }
    }

    /** Large soft blotches of fading and dirt, so walls are never one flat colour. */
    public static void weather(Img m, float amount, int seed) {
        for (int y = 0; y < m.h; y++)
            for (int x = 0; x < m.w; x++) {
                float n = Noise.fbm(x, y, m.w, m.h, seed + 31) - 0.5f;
                float speck = Noise.rnd(x, y, seed + 37);
                float k = 1f + n * amount * 1.6f;
                if (speck > 0.985f) k *= 0.85f;
                else if (speck < 0.012f) k *= 1.08f;
                m.px[y * m.w + x] = mul(m.px[y * m.w + x], k);
            }
    }

    /** Darkens the bottom rows a little (splash-back dirt / shadow of the slab below). */
    public static void footDirt(Img m, int rows, float k) {
        for (int y = Math.max(0, m.h - rows); y < m.h; y++) {
            float t = (y - (m.h - rows)) / (float) Math.max(1, rows);
            for (int x = 0; x < m.w; x++) m.px[y * m.w + x] = mul(m.px[y * m.w + x], 1f - k * t);
        }
    }
}
