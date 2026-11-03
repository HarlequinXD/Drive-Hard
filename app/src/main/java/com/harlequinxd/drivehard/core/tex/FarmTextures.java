package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.rgb;

/**
 * Countryside surfaces. Field textures tile every 8 m (one terrain cell): 8 px per metre, so a
 * row of rice, cabbage or tea every half metre reads as crop rows from the road.
 */
public final class FarmTextures {
    private FarmTextures() { }

    /** Rice paddy: 0 = flooded with seedlings, 1 = lush green, 2 = golden before harvest. */
    public static Texture paddy(int stage, long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 900 + stage;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                boolean row = (x % 4) < 2 && (y % 4) != 3;
                int c;
                if (stage == 0) {
                    // sky-blue water with mud showing through, little tufts on the planting grid
                    int water = mix(rgb(120, 150, 150), rgb(160, 190, 205), n);
                    c = row && (y % 4) < 2 ? mix(rgb(96, 150, 60), rgb(130, 180, 70), g) : mix(water, rgb(110, 105, 80), g * 0.25f);
                } else if (stage == 1) {
                    float k = 0.75f + n * 0.25f + g * 0.12f;
                    c = row ? rgb((int) (92 * k), (int) (168 * k), (int) (56 * k)) : rgb((int) (70 * k), (int) (138 * k), (int) (52 * k));
                } else {
                    float k = 0.8f + n * 0.2f + g * 0.15f;
                    c = row ? rgb((int) (214 * k), (int) (176 * k), (int) (82 * k)) : rgb((int) (180 * k), (int) (148 * k), (int) (70 * k));
                }
                m.px[y * 64 + x] = c;
            }
        return m.texture();
    }

    /** Rows of cabbages on dark soil. */
    public static Texture cabbage(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 910;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float g = Noise.rnd(x, y, sd);
                m.px[y * 64 + x] = mix(rgb(92, 70, 50), rgb(112, 88, 62), g);
            }
        for (int row = 0; row < 8; row++)
            for (int k = 0; k < 8; k++) {
                float cx = row * 8 + 3.5f, cy = k * 8 + 3.5f + (row & 1) * 4;
                int c = mix(rgb(120, 170, 90), rgb(170, 205, 130), Noise.rnd(row, k, sd + 1));
                m.disc(cx, cy % 64, 2.6f, c);
                m.disc(cx - 0.6f, (cy - 0.6f) % 64, 1.2f, mix(c, 0xFFFFFFFF, 0.25f));
            }
        return m.texture();
    }

    /** Freshly ploughed soil with furrows. */
    public static Texture soil(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 920;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float furrow = (x % 4) < 2 ? 0.82f : 1f;
                float k = (0.8f + n * 0.25f + g * 0.12f) * furrow;
                m.px[y * 64 + x] = rgb((int) (128 * k), (int) (98 * k), (int) (70 * k));
            }
        return m.texture();
    }

    /** Clipped tea hedgerows with narrow paths. */
    public static Texture tea(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 930;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                int p = x % 16;
                if (p < 3) {
                    m.px[y * 64 + x] = mix(rgb(120, 100, 76), rgb(140, 118, 90), g);
                } else {
                    float edge = (p == 3 || p == 15) ? 0.72f : 1f;
                    float k = (0.7f + n * 0.25f + g * 0.2f) * edge;
                    m.px[y * 64 + x] = rgb((int) (60 * k), (int) (118 * k), (int) (52 * k));
                }
            }
        return m.texture();
    }

    /** Milky ribbed plastic sheeting for greenhouses. */
    public static Texture vinyl(long seed) {
        Img m = new Img(32, 32);
        int sd = (int) seed + 940;
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                float g = Noise.rnd(x, y, sd);
                boolean rib = x % 8 == 0;
                int c = rib ? rgb(170, 176, 172) : mix(rgb(214, 222, 220), rgb(236, 240, 238), g);
                m.px[y * 32 + x] = c;
            }
        return m.texture();
    }

    /** Gravel for farm yards and driveways. */
    public static Texture gravel(long seed) {
        Img m = new Img(32, 32);
        int sd = (int) seed + 950;
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                float g = Noise.rnd(x, y, sd), h = Noise.rnd(x / 2, y / 2, sd + 1);
                m.px[y * 32 + x] = mix(rgb(150, 144, 132), rgb(196, 190, 176), g * 0.6f + h * 0.4f);
            }
        return m.texture();
    }

    /** Galvanised guardrail: a corrugated beam on a dark background (alpha-free, used on posts). */
    public static Texture guardrail(long seed) {
        Img m = new Img(32, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 32; x++) {
                int band = y < 4 || y > 11 ? rgb(150, 156, 160) : ((y / 2) % 2 == 0 ? rgb(214, 220, 224) : rgb(180, 186, 190));
                m.px[y * 32 + x] = band;
            }
        return m.texture();
    }

    // ------------------------------------------------------------------ road lanes

    public static final int LINE_NONE = 0, LINE_SOLID = 1, LINE_DASH5 = 2, LINE_DASH8 = 3, LINE_YELLOW = 4, LINE_DOUBLE_YELLOW = 5, LINE_THICK = 6;
    /** Lane textures repeat every 20 m along the road. */
    public static final float LANE_PERIOD = 20f;

    /**
     * A lane of asphalt (u across the lane, v along 20 m) with painted lines on its edges.
     * {@code laneW} is the lane width in metres, used to match the asphalt grain scale.
     */
    public static Texture lane(Texture asphalt, float laneW, int left, int right) {
        int w = 64, h = 256;
        Img m = new Img(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = (int) (x * laneW / w / 2f * asphalt.w) & (asphalt.w - 1);
                int sy = (int) (y * LANE_PERIOD / h / 2f * asphalt.h) & (asphalt.h - 1);
                m.px[y * w + x] = asphalt.px[sy * asphalt.w + sx];
            }
        int lw = Math.max(2, Math.round(0.15f / laneW * w)), tw = Math.max(3, Math.round(0.2f / laneW * w));
        if (left == LINE_DOUBLE_YELLOW) {
            int yw = Math.max(2, Math.round(0.12f / laneW * w));
            paint(m, Math.round(w * 0.27f) - yw / 2, yw, LINE_YELLOW);
            paint(m, Math.round(w * 0.73f) - yw / 2, yw, LINE_YELLOW);
            return m.texture();
        }
        paint(m, 1, left == LINE_THICK ? tw : lw, left);
        paint(m, w - 1 - (right == LINE_THICK ? tw : lw), right == LINE_THICK ? tw : lw, right);
        return m.texture();
    }

    /**
     * Coloured walking strip at the edge of a town street (u 0 at the lane edge line to 1 at the
     * road edge): green-tinted asphalt, as painted along school routes.
     */
    public static Texture walkStrip(Texture asphalt, float width) {
        int w = 32, h = 64;
        Img m = new Img(w, h);
        int green = rgb(78, 128, 92);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = (int) (x * width / w / 2f * asphalt.w) & (asphalt.w - 1);
                int sy = (int) (y * 5f / h / 2f * asphalt.h) & (asphalt.h - 1);
                m.px[y * w + x] = mix(green, asphalt.px[sy * asphalt.w + sx], 0.55f);
            }
        return m.texture();
    }

    private static void paint(Img m, int x0, int wpx, int style) {
        if (style == LINE_NONE) return;
        int c = style == LINE_YELLOW ? rgb(226, 178, 58) : rgb(236, 236, 228);
        for (int y = 0; y < m.h; y++) {
            float s = y * LANE_PERIOD / m.h;
            boolean on;
            switch (style) {
                case LINE_DASH5: on = (s % 10f) < 5f; break;
                case LINE_DASH8: on = s < 8f; break;
                default: on = true; break;
            }
            if (!on) continue;
            for (int x = x0; x < x0 + wpx; x++) m.px[y * m.w + x] = mix(c, m.px[y * m.w + x], 0.12f);
        }
    }

    /**
     * The last stretch of a road before a junction ("mouth"), u across the whole road (0..1 =
     * right..left edge for traffic arriving), v along it (1 = junction side). Optional zebra
     * crossing next to the junction and a stop line across the arriving half.
     *
     * @param width     road width in metres
     * @param length    mouth length in metres
     * @param arriving  width of the arriving half that gets the stop line (metres from the centre)
     * @param zebra     paint a pedestrian crossing (city streets)
     */
    public static Texture mouth(Texture asphalt, float width, float length, float arriving, boolean zebra, boolean stop) {
        int w = 128, h = 64;
        Img m = new Img(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = (int) (x * width / w / 2f * asphalt.w) & (asphalt.w - 1);
                int sy = (int) (y * length / h / 2f * asphalt.h) & (asphalt.h - 1);
                m.px[y * w + x] = asphalt.px[sy * asphalt.w + sx];
            }
        int white = rgb(236, 236, 228);
        float pxPerM = h / length;
        if (zebra) {
            // stripes 0.45 m wide every 0.9 m, 0.6..3.6 m from the junction edge
            int y0 = h - Math.round(3.6f * pxPerM), y1 = h - Math.round(0.6f * pxPerM);
            float stripe = 0.45f / width * w, period = 0.9f / width * w;
            for (float x = period * 0.5f; x < w - 1; x += period)
                for (int y = y0; y < y1; y++)
                    for (int xx = (int) x; xx < Math.min(w, (int) (x + stripe)); xx++) m.px[y * w + xx] = mix(white, m.px[y * w + xx], 0.1f);
        }
        if (stop) {
            float d0 = zebra ? 4.3f : 0.3f, d1 = zebra ? 4.75f : 0.75f;
            int y0 = h - Math.round(d1 * pxPerM), y1 = h - Math.round(d0 * pxPerM);
            int x0 = w / 2 + 1, x1 = Math.min(w, Math.round((0.5f + arriving / width) * w));
            for (int y = Math.max(0, y0); y < Math.min(h, y1); y++)
                for (int x = x0; x < x1; x++) m.px[y * w + x] = mix(white, m.px[y * w + x], 0.1f);
        }
        return m.texture();
    }
}
