package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

/** Small painting helper over an ARGB pixel array. */
public final class Img {
    public final int w, h;
    public final int[] px;

    public Img(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public Texture texture() { return new Texture(w, h, px); }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    public static int mul(int c, float k) {
        return (c & 0xFF000000) | (clamp((int) (((c >> 16) & 255) * k)) << 16)
                | (clamp((int) (((c >> 8) & 255) * k)) << 8) | clamp((int) ((c & 255) * k));
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (clamp(Math.round(ar + (br - ar) * t)) << 16)
                | (clamp(Math.round(ag + (bg - ag) * t)) << 8) | clamp(Math.round(ab + (bb - ab) * t));
    }

    public int get(int x, int y) { return px[y * w + x]; }

    public void set(int x, int y, int c) {
        if (x >= 0 && x < w && y >= 0 && y < h) px[y * w + x] = c;
    }

    public void fill(int c) { java.util.Arrays.fill(px, c); }

    /** Fills [x0,x1) x [y0,y1). */
    public void rect(int x0, int y0, int x1, int y1, int c) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * w + x] = c;
    }

    /** Multiplies brightness in a rectangle. */
    public void shade(int x0, int y0, int x1, int y1, float k) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * w + x] = mul(px[y * w + x], k);
    }

    /** Blends a colour over a rectangle. */
    public void tint(int x0, int y0, int x1, int y1, int c, float t) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * w + x] = mix(px[y * w + x], c, t);
    }

    /** Applies brightness noise (fbm + grain) to a rectangle. */
    public void grain(int x0, int y0, int x1, int y1, float fbmAmt, float grainAmt, int seed) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                float n = Noise.fbm(x, y, w, h, seed) - 0.5f, g = Noise.rnd(x, y, seed + 9) - 0.5f;
                px[y * w + x] = mul(px[y * w + x], 1f + n * fbmAmt * 2f + g * grainAmt * 2f);
            }
        }
    }

    public void frame(int x0, int y0, int x1, int y1, int t, int c) {
        rect(x0, y0, x1, y0 + t, c);
        rect(x0, y1 - t, x1, y1, c);
        rect(x0, y0, x0 + t, y1, c);
        rect(x1 - t, y0, x1, y1, c);
    }

    public void disc(float cx, float cy, float r, int c) {
        for (int y = (int) (cy - r - 1); y <= cy + r + 1; y++)
            for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                if (dx * dx + dy * dy <= r * r) set(x, y, c);
            }
    }
}
