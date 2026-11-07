package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.List;
import java.util.Random;

/**
 * The game's pixel-art finish, shared by everything that is drawn: baked lighting, limited
 * palettes and ordered (Bayer) dithering, as on the hand-drawn cars and trees.
 *
 * Textures: {@link #stylize} treats a texture's brightness as a relief map, so its own detail
 * (brick courses, window frames, tiles, kerbs, lettering) is lit from the upper left with the
 * grooves and recesses in shadow; then it is reduced to a palette of its own colours chosen by
 * k-means and mapped back through a 4x4 Bayer matrix, so gradients become dithered bands.
 *
 * Sprites: {@link #ramp7} and {@link #shadeStep} give a colour's seven-step lighting ramp and the
 * dithered step for a light level, and {@link #outline} adds a selective outline.
 */
public final class PixelArt {
    private PixelArt() {}

    public static final float[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    /** Dither threshold at a pixel, -0.5..0.5. */
    public static float dither(int x, int y) { return (BAYER[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f - 0.5f; }

    // ------------------------------------------------------------------ ramps

    private static final java.util.concurrent.ConcurrentHashMap<Integer, int[]> RAMPS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Seven steps of a colour, index 3 the colour itself: shadows lean blue, highlights warm. */
    public static int[] ramp7(int c) {
        return RAMPS.computeIfAbsent(c & 0xFFFFFF, k -> {
            int r = (k >> 16) & 255, g = (k >> 8) & 255, b = k & 255;
            return new int[]{
                    shade(r, g, b, 0.52f), shade(r, g, b, 0.68f), shade(r, g, b, 0.84f),
                    0xFF000000 | k,
                    mix(r, g, b, 255, 246, 230, 0.16f), mix(r, g, b, 255, 248, 236, 0.34f), mix(r, g, b, 255, 252, 244, 0.58f)};
        });
    }

    private static int mix(int r, int g, int b, int r2, int g2, int b2, float t) {
        return 0xFF000000 | ((int) (r + (r2 - r) * t) << 16) | ((int) (g + (g2 - g) * t) << 8) | (int) (b + (b2 - b) * t);
    }

    private static int shade(int r, int g, int b, float k) {
        int rr = (int) (r * k * 0.94f + 28 * (1 - k) * 0.3f), gg = (int) (g * k * 0.96f + 20 * (1 - k) * 0.3f), bb = (int) (b * k + 70 * (1 - k) * 0.4f);
        return 0xFF000000 | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
    }

    /** A colour lit by {@code steps} (fractional, + brighter), dithered at pixel (x, y). */
    public static int shadeStep(int c, float steps, int x, int y) {
        int k = (int) Math.floor(steps + 0.5f + dither(x, y) * 0.8f);
        return ramp7(c)[3 + Math.max(-3, Math.min(3, k))];
    }

    /**
     * Selective outline round the opaque pixels of a w x h image: dark on the shadow side (right
     * and below), softer on the lit side.
     */
    public static void outline(int[] px, int w, int h) {
        int[] src = px.clone();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((src[i] >>> 24) != 0) continue;
                int c = 0;
                float k = 0f;
                if (x > 0 && (src[i - 1] >>> 24) != 0) { c = src[i - 1]; k = 0.36f; }
                else if (y > 0 && (src[i - w] >>> 24) != 0) { c = src[i - w]; k = 0.36f; }
                else if (x < w - 1 && (src[i + 1] >>> 24) != 0) { c = src[i + 1]; k = 0.56f; }
                else if (y < h - 1 && (src[i + w] >>> 24) != 0) { c = src[i + w]; k = 0.62f; }
                if (c == 0) continue;
                int rr = (int) (((c >> 16) & 255) * k * 0.9f), gg = (int) (((c >> 8) & 255) * k * 0.95f), bb = (int) ((c & 255) * k * 1.08f);
                px[i] = 0xFF000000 | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
            }
    }

    // ------------------------------------------------------------------ textures

    /** Stylizes every texture in a list (in parallel), skipping the given ones. */
    public static void stylizeAll(List<Texture> list, java.util.Set<Integer> skip) {
        java.util.stream.IntStream.range(0, list.size()).parallel().forEach(i -> {
            if (!skip.contains(i)) stylize(list.get(i), i);
        });
    }

    /**
     * Tiles that must meet seamlessly (the ground's terrain tiles and their transitions, the lane
     * and junction textures cut from one asphalt): no relief lighting (it would differ at the
     * tile edges) and one shared palette, so neighbouring tiles dither to the same colours. The
     * dither pattern repeats every 4 texels, so it lines up across tiles too.
     */
    public static void stylizeGroup(List<Texture> group, int seed) {
        int total = 0;
        for (Texture t : group) total += t.px.length;
        int[] all = new int[total];
        int o = 0;
        for (Texture t : group) { System.arraycopy(t.px, 0, all, o, t.px.length); o += t.px.length; }
        int[] pal = palette(all, 40, seed);
        if (pal.length < 2) return;
        float spread = spread(pal);
        group.parallelStream().forEach(t -> map(t.px, t.px.clone(), t.w, t.h, pal, spread));
    }

    private static float spread(int[] pal) {
        float[] gaps = new float[pal.length];
        for (int a = 0; a < pal.length; a++) {
            float best = Float.MAX_VALUE;
            for (int b = 0; b < pal.length; b++) if (a != b) best = Math.min(best, dist(pal[a], pal[b]));
            gaps[a] = (float) Math.sqrt(best);
        }
        java.util.Arrays.sort(gaps);
        return Math.max(6f, Math.min(48f, gaps[gaps.length / 2] * 0.9f));
    }

    private static void map(int[] px, int[] lit, int w, int h, int[] pal, float spread) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x, c = lit[i];
                if ((c >>> 24) != 255) { px[i] = c; continue; }
                float o = dither(x, y) * spread;
                int r = (int) (((c >> 16) & 255) + o), g = (int) (((c >> 8) & 255) + o), b = (int) ((c & 255) + o);
                int best = pal[0];
                int bd = Integer.MAX_VALUE;
                for (int p : pal) {
                    int dr = ((p >> 16) & 255) - r, dg = ((p >> 8) & 255) - g, db = (p & 255) - b;
                    int dd = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                    if (dd < bd) { bd = dd; best = p; }
                }
                px[i] = best;
            }
    }

    /**
     * Bakes relief lighting into a texture and reduces it to a dithered palette of its own
     * colours. Transparent and partly transparent texels are left alone.
     */
    public static void stylize(Texture t, int seed) {
        int w = t.w, h = t.h, n = w * h;
        int[] px = t.px;
        if (n < 16) return;
        boolean wrapY = Integer.bitCount(h) == 1;
        // brightness as a relief map
        float[] Y = new float[n];
        for (int i = 0; i < n; i++) {
            int c = px[i];
            Y[i] = (((c >> 16) & 255) * 0.3f + ((c >> 8) & 255) * 0.59f + (c & 255) * 0.11f) / 255f;
        }
        float[] blur = boxBlur(Y, w, h, 3, wrapY);
        // light from the upper left (texture v runs down the wall)
        final float lx = -0.55f, ly = -0.55f, lz = 0.63f;
        float strength = 2.2f;
        int[] lit = new int[n];
        for (int y = 0; y < h; y++) {
            int ym = wrapY ? (y - 1 + h) % h : Math.max(0, y - 1), yp = wrapY ? (y + 1) % h : Math.min(h - 1, y + 1);
            for (int x = 0; x < w; x++) {
                int i = y * w + x, c = px[i];
                if ((c >>> 24) != 255) { lit[i] = c; continue; }
                int xm = (x - 1 + w) % w, xp = (x + 1) % w;
                float gx = (Y[y * w + xp] - Y[y * w + xm]) * strength;
                float gy = (Y[yp * w + x] - Y[ym * w + x]) * strength;
                // height = brightness: raised where lighter (mortar, frames, highlights)
                float nl = (float) Math.sqrt(gx * gx + gy * gy + 1f);
                float d = (-gx * lx - gy * ly + lz) / nl / lz;          // 1 on flat ground
                float k = 1f + (d - 1f) * 0.55f;
                // cavities: darker than their surroundings sink into shadow
                float cav = Y[i] - blur[i];
                k *= 1f + Math.max(-0.16f, Math.min(0.06f, cav * 0.7f));
                k = Math.max(0.55f, Math.min(1.45f, k));
                int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)),
                        b = Math.min(255, (int) ((c & 255) * k));
                lit[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        // the palette: k-means over a sample of the lit texels
        int K = n >= 128 * 128 ? 28 : n >= 64 * 64 ? 20 : 12;
        int[] pal = withAccents(lit, palette(lit, K, seed), seed);
        if (pal.length < 2) { System.arraycopy(lit, 0, px, 0, n); return; }
        map(px, lit, w, h, pal, spread(pal));
    }

    /**
     * Small, strongly coloured details (signs, goods on shelves, lamps) are few texels and get
     * swallowed by the main palette: the texels it fits worst get colours of their own.
     */
    private static int[] withAccents(int[] px, int[] pal, int seed) {
        if (pal.length == 0) return pal;
        int n = px.length;
        int[] miss = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int c = px[i];
            if ((c >>> 24) != 255) continue;
            float bd = Float.MAX_VALUE;
            for (int p : pal) bd = Math.min(bd, dist(c, p));
            if (bd > 26f * 26f) miss[m++] = c;
        }
        if (m < 6) return pal;
        int[] extra = palette(java.util.Arrays.copyOf(miss, m), Math.min(12, 2 + m / 40), seed + 17);
        int[] out = java.util.Arrays.copyOf(pal, pal.length + extra.length);
        System.arraycopy(extra, 0, out, pal.length, extra.length);
        return out;
    }

    private static float dist(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return dr * dr + dg * dg + db * db;
    }

    /** k-means palette of up to K colours from the opaque texels. */
    private static int[] palette(int[] px, int K, int seed) {
        int n = px.length, step = Math.max(1, n / 6000);
        int m = 0;
        int[] s = new int[n / step + 1];
        for (int i = 0; i < n; i += step) if ((px[i] >>> 24) == 255) s[m++] = px[i];
        if (m == 0) return new int[0];
        // start from luminance quantiles
        Integer[] idx = new Integer[m];
        for (int i = 0; i < m; i++) idx[i] = i;
        final int[] sm = s;
        java.util.Arrays.sort(idx, (a, b) -> Integer.compare(lum(sm[a]), lum(sm[b])));
        K = Math.min(K, m);
        float[] cr = new float[K], cg = new float[K], cb = new float[K];
        Random rnd = new Random(seed);
        for (int k = 0; k < K; k++) {
            int c = s[idx[Math.min(m - 1, (int) ((k + 0.5f) / K * m))]];
            // nudge a few starts to random texels, so small bright accents get colours too
            if (k % 4 == 3) c = s[rnd.nextInt(m)];
            cr[k] = (c >> 16) & 255; cg[k] = (c >> 8) & 255; cb[k] = c & 255;
        }
        int[] asg = new int[m];
        for (int it = 0; it < 7; it++) {
            float[] sr = new float[K], sg = new float[K], sb = new float[K];
            int[] cnt = new int[K];
            for (int i = 0; i < m; i++) {
                int c = s[i], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int best = 0;
                float bd = Float.MAX_VALUE;
                for (int k = 0; k < K; k++) {
                    float dr = cr[k] - r, dg = cg[k] - g, db = cb[k] - b, d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                    if (d < bd) { bd = d; best = k; }
                }
                asg[i] = best;
                sr[best] += r; sg[best] += g; sb[best] += b; cnt[best]++;
            }
            for (int k = 0; k < K; k++)
                if (cnt[k] > 0) { cr[k] = sr[k] / cnt[k]; cg[k] = sg[k] / cnt[k]; cb[k] = sb[k] / cnt[k]; }
                else { int c = s[rnd.nextInt(m)]; cr[k] = (c >> 16) & 255; cg[k] = (c >> 8) & 255; cb[k] = c & 255; }
        }
        java.util.LinkedHashSet<Integer> out = new java.util.LinkedHashSet<>();
        for (int k = 0; k < K; k++) out.add(0xFF000000 | (Math.round(cr[k]) << 16) | (Math.round(cg[k]) << 8) | Math.round(cb[k]));
        int[] p = new int[out.size()];
        int j = 0;
        for (int c : out) p[j++] = c;
        return p;
    }

    private static int lum(int c) { return ((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255); }

    private static float[] boxBlur(float[] a, int w, int h, int r, boolean wrapY) {
        float[] t = new float[a.length], o = new float[a.length];
        int span = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += a[y * w + ((k % w) + w) % w];
            for (int x = 0; x < w; x++) {
                t[y * w + x] = sum / span;
                sum += a[y * w + (x + r + 1) % w] - a[y * w + ((x - r) % w + w) % w];
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += t[row(k, h, wrapY) * w + x];
            for (int y = 0; y < h; y++) {
                o[y * w + x] = sum / span;
                sum += t[row(y + r + 1, h, wrapY) * w + x] - t[row(y - r, h, wrapY) * w + x];
            }
        }
        return o;
    }

    private static int row(int y, int h, boolean wrap) { return wrap ? ((y % h) + h) % h : Math.max(0, Math.min(h - 1, y)); }
}
