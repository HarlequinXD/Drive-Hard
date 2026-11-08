package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.rgb;

/**
 * The ground's tile set: 128 x 128 texel tiles, each laid on one 8 m terrain cell (16 texels a
 * metre), the way PS1 / GBA games built their ground from repeating tiles.
 *
 * <p>Natural ground types (grass, hill grass, sand, rock) are given at the grid's corners. A cell
 * whose four corners agree takes that type's seamless tile; where two types meet it takes a
 * transition tile from a marching-squares set: for every pair of types, one tile per pattern of
 * corners (14 mixed patterns), the upper type laid over the lower along a ragged, noisy border
 * with a dithered fringe. The border crosses each cell edge exactly halfway between two differing
 * corners and the noise fades to nothing at the edges, so neighbouring cells always join up.</p>
 *
 * <p>Asphalt is a seamless 8 m tile too, for city ground, junctions and the plain road bands.</p>
 */
public final class TerrainTiles implements java.io.Serializable {
    public static final int N = 128;
    /** Metres covered by one tile. */
    public static final float METRES = 8f;
    /** Natural ground types, lowest priority first: a higher type is laid over a lower one. */
    public static final int GRASS = 0, HILL = 1, SAND = 2, ROCK = 3, TYPES = 4;

    /** Texture ids of the seamless tiles. */
    public final int[] base = new int[TYPES];
    public final int asphalt;
    /** [pair][corner mask 0..15]: pair = lower * TYPES + upper. */
    private final int[][] trans = new int[TYPES * TYPES][];
    /** Only needed while the transitions are painted. */
    private transient Texture[] baseTex = new Texture[TYPES];

    public TerrainTiles(TextureLibrary lib, long seed) {
        int s = (int) seed;
        baseTex[GRASS] = grass(s + 7001);
        baseTex[HILL] = hill(s + 7002);
        baseTex[SAND] = sand(s + 7003);
        baseTex[ROCK] = rock(s + 7004);
        for (int t = 0; t < TYPES; t++) base[t] = lib.add(baseTex[t]);
        asphalt = lib.add(asphalt(s + 7005));
        for (int lo = 0; lo < TYPES; lo++)
            for (int hi = lo + 1; hi < TYPES; hi++) {
                int[] ids = new int[16];
                ids[0] = base[lo];
                ids[15] = base[hi];
                for (int m = 1; m < 15; m++) ids[m] = lib.add(blend(baseTex[lo], baseTex[hi], m, s + 7100 + lo * 16 + hi));
                trans[lo * TYPES + hi] = ids;
            }
        baseTex = null;
    }

    /**
     * The tile for a cell from its corner types: c00 at (x0, z0), c10 at (x1, z0), c11 at (x1, z1),
     * c01 at (x0, z1); u runs along x and v along z. Three or four types in one cell fall back to
     * the lowest and highest of them (a middle type joins whichever it is closer to).
     */
    public int tile(int c00, int c10, int c11, int c01) {
        int lo = Math.min(Math.min(c00, c10), Math.min(c11, c01));
        int hi = Math.max(Math.max(c00, c10), Math.max(c11, c01));
        if (lo == hi) return base[lo];
        int m = bit(c00, lo, hi) | bit(c10, lo, hi) << 1 | bit(c11, lo, hi) << 2 | bit(c01, lo, hi) << 3;
        return trans[lo * TYPES + hi][m];
    }

    private static int bit(int c, int lo, int hi) {
        if (c == hi) return 1;
        if (c == lo) return 0;
        return (c - lo) * 2 >= hi - lo ? 1 : 0;
    }

    // ------------------------------------------------------------------ transitions

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    /** The upper tile over the lower where the corner mask says, along a ragged dithered border. */
    private static Texture blend(Texture lo, Texture hi, int mask, int seed) {
        float c00 = mask & 1, c10 = (mask >> 1) & 1, c11 = (mask >> 2) & 1, c01 = (mask >> 3) & 1;
        Img m = new Img(N, N);
        for (int y = 0; y < N; y++) {
            float v = (y + 0.5f) / N;
            for (int x = 0; x < N; x++) {
                float u = (x + 0.5f) / N;
                float w = (c00 * (1 - u) + c10 * u) * (1 - v) + (c01 * (1 - u) + c11 * u) * v;
                // the border wanders, but never at the cell's edges (neighbours must meet it)
                float edge = (float) (Math.sin(Math.PI * u) * Math.sin(Math.PI * v));
                float n = (Noise.fbm(x, y, N, N, seed) - 0.5f) * 0.9f + (Noise.value(x, y, N, N, 32, 32, seed + 5) - 0.5f) * 0.2f;
                w += n * Math.min(1f, edge * 2.2f);
                // a dithered fringe two texels wide
                float t = (w - 0.5f) * 16f + 0.5f;
                boolean top = t * 16f > BAYER[((y & 3) << 2) | (x & 3)] + 0.5f;
                int c = top ? hi.px[(y << hi.wShift) | x] : lo.px[(y << lo.wShift) | x];
                // a slightly darker rim where the upper ground starts (a little relief)
                if (top && t < 1.6f) c = Img.mul(c, 0.9f);
                m.px[y * N + x] = c;
            }
        }
        return m.texture();
    }

    // ------------------------------------------------------------------ seamless base tiles

    private static Texture grass(int sd) {
        Img m = new Img(N, N);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float patch = Noise.fbm(x, y, N, N, sd);                       // lusher and thinner patches
                float clump = Noise.value(x, y, N, N, 16, 16, sd + 1);
                float g = Noise.rnd(x, y, sd + 2);
                float k = 0.66f + patch * 0.3f + clump * 0.12f + g * 0.14f;
                int c = rgb((int) (72 * k), (int) (130 * k), (int) (48 * k));
                if (g > 0.86f) c = rgb((int) (112 * k), (int) (168 * k), (int) (62 * k));      // lit blades
                else if (g < 0.08f) c = rgb((int) (44 * k), (int) (86 * k), (int) (36 * k));    // shadow between blades
                m.px[y * N + x] = c;
            }
        return m.texture();
    }

    private static Texture hill(int sd) {
        Img m = new Img(N, N);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float n = Noise.fbm(x, y, N, N, sd), g = Noise.rnd(x, y, sd + 1);
                float dry = Noise.value(x, y, N, N, 6, 6, sd + 2);
                float k = 0.62f + n * 0.32f + g * 0.14f;
                // upland grass: a shade darker and cooler than the lowland, with a little dry straw in it
                int green = rgb((int) (66 * k), (int) (118 * k), (int) (48 * k));
                int straw = rgb((int) (112 * k), (int) (124 * k), (int) (64 * k));
                int c = mix(green, straw, Math.max(0f, Math.min(1f, (dry - 0.45f) * 1.5f)) * 0.3f);
                if (g > 0.9f) c = Img.mul(c, 1.18f);
                else if (g < 0.07f) c = Img.mul(c, 0.72f);
                m.px[y * N + x] = c;
            }
        return m.texture();
    }

    private static Texture sand(int sd) {
        Img m = new Img(N, N);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float n = Noise.fbm(x, y, N, N, sd), g = Noise.rnd(x, y, sd + 1);
                // wind ripples: whole numbers of waves across the tile so it repeats seamlessly
                double ph = 2 * Math.PI * (x * 3 + y * 7) / N + n * 5.0;
                float ripple = (float) Math.sin(ph) * 0.035f;
                float k = 0.86f + n * 0.1f + g * 0.07f + ripple;
                int c = rgb((int) (232 * k), (int) (214 * k), (int) (172 * k));
                if (g > 0.985f) c = rgb(150, 140, 120);                        // grit and shell
                m.px[y * N + x] = c;
            }
        return m.texture();
    }

    private static Texture rock(int sd) {
        Img m = new Img(N, N);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float n = Noise.fbm(x, y, N, N, sd), g = Noise.rnd(x, y, sd + 1);
                double ph = 2 * Math.PI * y * 5 / N + n * 7.0;
                float strata = (float) Math.sin(ph) * 0.07f;
                float k = 0.56f + n * 0.32f + g * 0.1f + strata;
                // cracks: where a finer noise crosses a narrow band
                float cr = Noise.value(x, y, N, N, 12, 12, sd + 2);
                if (Math.abs(cr - 0.5f) < 0.018f) k *= 0.62f;
                m.px[y * N + x] = rgb((int) (148 * k), (int) (138 * k), (int) (120 * k));
            }
        return m.texture();
    }

    private static Texture asphalt(int sd) {
        Img m = new Img(N, N);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float n = Noise.fbm(x, y, N, N, sd), g = Noise.rnd(x, y, sd + 1);
                float k = 0.84f + n * 0.12f + g * 0.1f;
                int c = rgb((int) (92 * k), (int) (92 * k), (int) (96 * k));
                if (g > 0.97f) c = rgb((int) (112 * k), (int) (110 * k), (int) (110 * k));    // aggregate
                // faint tar-sealed patches
                float st = Noise.value(x, y, N, N, 3, 3, sd + 2);
                c = Img.mul(c, 1f - Math.max(0f, st - 0.6f) * 0.12f);
                m.px[y * N + x] = c;
            }
        return m.texture();
    }
}
