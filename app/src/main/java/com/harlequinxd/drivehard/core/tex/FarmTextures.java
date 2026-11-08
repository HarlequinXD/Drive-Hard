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

    /**
     * Packed earth for road shoulders and field margins: brown-grey dirt worn smooth, small stones,
     * tyre-darkened patches and a few blades of grass coming through.
     */
    public static Texture dirt(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 930;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float k = 0.84f + n * 0.22f + g * 0.08f;
                int c = rgb((int) (132 * k), (int) (112 * k), (int) (84 * k));
                float st = Noise.rnd(x, y, sd + 2);
                if (st > 0.95f) c = mix(c, rgb(176, 170, 156), 0.7f);          // pebble
                else if (st < 0.03f) c = mix(c, rgb(70, 60, 48), 0.6f);        // shadow of one
                else if (Noise.rnd(x, y, sd + 3) > 0.975f) c = mix(c, rgb(96, 128, 60), 0.75f);   // a blade of grass
                m.px[y * 64 + x] = c;
            }
        return m.texture();
    }

    /** Distance along the road the road-edge texture covers (metres), and across it. */
    public static final float EDGE_ALONG = 10f, EDGE_ACROSS = 2.6f;

    /**
     * The edge of a country road, hand painted: across the texture (u, 0 at the tarmac) the
     * asphalt breaks up into a crumbled lip that casts a thin shadow, then a shoulder of packed
     * dirt and gravel with lit pebbles and tyre-worn patches, grass clumps pushing in more and
     * more, and finally a ragged alpha-clipped edge of tufts and dirt specks over the terrain.
     * Tiles along v. Light from the upper left like everything else; the library's pixel-art
     * finish then dithers it to its own palette.
     */
    public static Texture roadEdge(Texture asphalt, long seed) {
        final int W = 64, H = 256;
        int sd = (int) seed + 960;
        int[] px = new int[W * H];
        // hand-picked ramps, dark to light: dirt, grass
        int[] dirt = {rgb(66, 52, 40), rgb(92, 74, 56), rgb(118, 96, 72), rgb(140, 116, 88), rgb(164, 140, 108), rgb(190, 170, 136)};
        int[] grass = {rgb(30, 54, 30), rgb(44, 78, 36), rgb(64, 104, 44), rgb(90, 134, 54), rgb(124, 164, 72), rgb(164, 192, 98)};
        float[] tarEnd = new float[H], dirtEnd = new float[H];
        for (int y = 0; y < H; y++) {
            tarEnd[y] = 6f + 4f * (Noise.value(0, y, W, H, 1, 20, sd) * 0.65f + Noise.value(0, y, W, H, 1, 64, sd + 1) * 0.35f);
            dirtEnd[y] = 33f + 12f * (Noise.value(0, y, W, H, 1, 12, sd + 2) * 0.6f + Noise.value(0, y, W, H, 1, 40, sd + 3) * 0.4f);
        }
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                float te = tarEnd[y], de = dirtEnd[y];
                if (x < te) {
                    // tarmac, its lip crumbling: a few chips missing right at the edge
                    int a = asphalt.px[(y % asphalt.h) * asphalt.w + (x * 2) % asphalt.w];
                    boolean chip = x > te - 2.5f && Noise.rnd(x, y, sd + 4) > 0.55f;
                    if (!chip) {
                        px[i] = x > te - 1.5f ? mix(a, rgb(150, 150, 150), 0.25f) : a;     // worn, paler lip
                        continue;
                    }
                }
                if (x < de + 6f) {
                    // the shoulder: packed dirt and gravel, patchy, darker in the tyre ruts
                    float n = Noise.fbm(x, y, W, H, sd + 5);
                    float k = 1.6f + n * 2.6f;
                    if (x >= te && x < te + 1.5f) k -= 1.6f;                         // the tarmac's shadow
                    float rut = Math.abs(x - 14f - 3f * Noise.value(0, y, W, H, 1, 8, sd + 6));
                    if (rut < 2.5f) k -= 0.7f * (1f - rut / 2.5f);
                    // past the dirt's edge only specks remain, thinning out (alpha-clipped)
                    if (x >= de) {
                        float keep = 1f - (x - de) / 6f;
                        if (Noise.rnd(x, y, sd + 7) > keep * 0.55f) continue;
                    }
                    px[i] = dirt[Math.max(0, Math.min(5, Math.round(k)))];
                }
            }
        // pebbles on the shoulder: lit from the upper left
        java.util.Random r = new java.util.Random(sd);
        for (int k = 0; k < 180; k++) {
            int x = 2 + r.nextInt(W - 4), y = r.nextInt(H);
            if (x < tarEnd[y] + 1 || x > dirtEnd[y] - 1) continue;
            int s = r.nextInt(3) == 0 ? 2 : 1;
            int base = r.nextBoolean() ? rgb(158, 152, 140) : rgb(132, 120, 104);
            for (int dy = 0; dy < s; dy++)
                for (int dx = 0; dx < s; dx++) put(px, W, H, x + dx, y + dy, base);
            put(px, W, H, x, y, mix(base, rgb(236, 232, 220), 0.5f));            // highlight
            put(px, W, H, x + s, y + s, dirt[0]);                                 // shadow
            put(px, W, H, x + s - 1, y + s, dirt[1]);
        }
        // grass clumps, more of them toward the verge, and the tufts that make the ragged edge
        for (int k = 0; k < 230; k++) {
            int y = r.nextInt(H);
            int x = 16 + r.nextInt(Math.max(1, (int) dirtEnd[y] + 4 - 16));
            if (x >= W - 4) continue;
            float out = Math.min(1f, (x - 16) / Math.max(1f, dirtEnd[y] - 16)); // 0 by the ruts, 1 at the edge
            if (r.nextFloat() > 0.08f + 0.92f * out * out) continue;
            clump(px, W, H, x, y, 1 + r.nextInt(out > 0.75f ? 3 : 2), grass, r);
        }
        return new Texture(W, H, px);
    }

    private static void put(int[] px, int W, int H, int x, int y, int c) {
        if (x < 0 || x >= W) return;
        px[((y % H + H) % H) * W + x] = c;
    }

    /** A clump of grass: a dark base, then blades fanning up with lit tips. */
    private static void clump(int[] px, int W, int H, int cx, int cy, int r, int[] g, java.util.Random rnd) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -r; dx <= r; dx++)
                if (dx * dx + dy * dy * 4 <= r * r) put(px, W, H, cx + dx, cy + dy, g[dx > 0 ? 0 : 1]);
        int blades = 3 + r * 2;
        for (int b = 0; b < blades; b++) {
            int x = cx - r + rnd.nextInt(2 * r + 1);
            int len = 2 + rnd.nextInt(r + 2);
            int lean = rnd.nextInt(3) - 1;
            for (int s = 0; s < len; s++) {
                int yy = cy - s, xx = x + (s * lean) / 2;
                int shade = s == len - 1 ? (xx < cx ? 5 : 4) : s > len / 2 ? (xx < cx ? 4 : 3) : 2;
                put(px, W, H, xx, yy, g[shade]);
            }
        }
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
    /** Metres of road one lane tile covers (128 texels): the ground's tile length. */
    public static final float LANE_PERIOD = 8f;

    /**
     * A lane of asphalt (u across the lane, v along 20 m) with painted lines on its edges.
     * {@code laneW} is the lane width in metres, used to match the asphalt grain scale.
     */
    public static Texture lane(Texture asphalt, float tileM, float laneW, int left, int right) {
        int w = 128, h = 128;
        Img m = new Img(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = (int) (x * laneW / w / tileM * asphalt.w) & (asphalt.w - 1);
                int sy = (int) (y * LANE_PERIOD / h / tileM * asphalt.h) & (asphalt.h - 1);
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

    /** Farm fence: a timber post and three wires on a transparent background (64x32, one 4 m span). */
    public static Texture fence(int seed) {
        Img m = new Img(64, 32);
        java.util.Arrays.fill(m.px, 0x00000000);
        int wood = rgb(120, 96, 70), wire = rgb(150, 150, 146);
        for (int y = 1; y < 32; y++)
            for (int x = 0; x < 3; x++) m.px[y * 64 + x] = mix(wood, rgb(80, 64, 48), x == 2 ? 0.5f : 0f);
        for (int wy : new int[]{4, 13, 22})
            for (int x = 3; x < 64; x++) m.px[wy * 64 + x] = wire;
        return m.texture();
    }

    /**
     * The walking strip of a town road (路側帯), the Japanese way: no raised pavement, the asphalt
     * beside the lane line painted green, worn paler where feet and tyres scuff it, with a gutter of
     * concrete at the edge.
     */
    public static Texture walkStrip(Texture asphalt, float width) {
        int w = 32, h = 64;
        Img m = new Img(w, h);
        java.util.Random r = new java.util.Random(77);
        int green = rgb(64, 138, 86);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int a = asphalt.px[(y % asphalt.h) * asphalt.w + (x * 3) % asphalt.w];
                // the paint takes the asphalt's grain: lighter stones show through
                int g = mix(green, a, 0.28f);
                m.set(x, y, g);
            }
        // scuffed patches where the paint has worn thin
        for (int k = 0; k < 9; k++) {
            int cx = 3 + r.nextInt(w - 8), cy = r.nextInt(h), rw = 2 + r.nextInt(5), rh = 3 + r.nextInt(8);
            for (int y = cy - rh; y <= cy + rh; y++)
                for (int x = cx - rw; x <= cx + rw; x++) {
                    float d = ((x - cx) * (x - cx)) / (float) (rw * rw) + ((y - cy) * (y - cy)) / (float) (rh * rh);
                    if (d < 1f && r.nextInt(3) != 0) {
                        int yy = (y % h + h) % h, xx = Math.max(0, Math.min(w - 1, x));
                        m.set(xx, yy, mix(m.get(xx, yy), asphalt.px[(yy % asphalt.h) * asphalt.w + xx], 0.45f));
                    }
                }
        }
        // the concrete gutter at the edge of the road
        for (int y = 0; y < h; y++)
            for (int x = w - 4; x < w; x++) m.set(x, y, mix(rgb(150, 146, 138), rgb(110, 106, 100), (x - (w - 4)) / 3f));
        for (int y = 0; y < h; y += 16) m.shade(w - 4, y, w, y + 1, 0.75f);
        return m.texture();
    }

    private static void paint(Img m, int x0, int wpx, int style) {
        if (style == LINE_NONE) return;
        int c = style == LINE_YELLOW ? rgb(226, 178, 58) : rgb(236, 236, 228);
        for (int y = 0; y < m.h; y++) {
            float s = y * LANE_PERIOD / m.h;
            boolean on;
            switch (style) {
                case LINE_DASH5: on = s < 4f; break;           // 4 m dash, 4 m gap
                case LINE_DASH8: on = s < 5.5f; break;         // long freeway dash
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
    public static Texture mouth(Texture asphalt, float tileM, float width, float length, float arriving, boolean zebra, boolean stop) {
        int w = 128, h = 64;
        Img m = new Img(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = (int) (x * width / w / tileM * asphalt.w) & (asphalt.w - 1);
                int sy = (int) (y * length / h / tileM * asphalt.h) & (asphalt.h - 1);
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
