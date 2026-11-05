package com.harlequinxd.drivehard.core.render;

import com.harlequinxd.drivehard.core.Environment;

import java.util.Arrays;

/**
 * Software renderer: a Mode 7 ground map ({@link #drawGround()}), whole polygons with
 * perspective-correct texturing, and sprites drawn as scaled rectangle blits.
 *
 * <ul>
 * <li>The ground is rendered as the background, and polygons and sprites are drawn far-to-near
 *   through a PlayStation-style ordering table grouped by layer and depth (no Z-buffer between
 *   polygons).</li>
 * <li>The ground writes a per-pixel occlusion depth that polygons and sprites only test: hills
 *   hide the houses, trees and cars behind them.</li>
 * <li>Colour is reduced to 15-bit with 4x4 ordered dither; fog by view depth.</li>
 * </ul>
 */
public final class SoftRenderer extends Renderer {
    private static final int BUCKETS = 4096;
    private static final float BUCKET_SCALE = 24f;     // buckets per metre (~170 m range)
    private static final int VA = 8;                    // per screen vertex: x y u v light fog coverage 1/z
    private static final int MAX_PRIMS = 60000;

    /** Current polygon sorts by its farthest vertex (see {@link Geo#FLAG_SORT_FAR}). */
    private boolean sortFar;
    /** Coverage of the polygon being submitted (blended overlays); 1 otherwise. */
    private final float[] curAlpha = {1f, 1f, 1f, 1f};

    // ordering table + primitive pool
    private final int[] head = new int[LAYERS * BUCKETS];
    private final int[] next = new int[MAX_PRIMS];
    private final float[] pdepth = new float[MAX_PRIMS];
    private final int[] ptex = new int[MAX_PRIMS];
    private final int[] pmode = new int[MAX_PRIMS];
    private final float[] P = new float[MAX_PRIMS * 3 * VA];
    private int primCount;

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    public SoftRenderer(int w, int h) {
        super(w, h, new int[w * h]);
        groundZ = new float[w * h];
    }

    // ------------------------------------------------------------------ frame

    /** The painted ground's 15-bit colours after season and light: 24-bit, quantised to 15-bit. */
    private int lutKey = Integer.MIN_VALUE;
    private final int[] groundLut = new int[32768];

    @Override public void begin() {
        readEnvironment();
        int key = (tR << 20) ^ (tG << 10) ^ tB ^ ((int) (dryness * 50f) << 27);
        if (key != lutKey) { lutKey = key; buildGroundLut(); }
        Arrays.fill(head, -1);
        primCount = 0;
        statPolys = 0;
        statPrims = 0;
    }

    /** Fills the background with a dithered sky gradient matching the fog colour at the horizon. */
    private final int[] sky = new int[4 * 4096];
    private final float[] skyR = new float[4096], skyG = new float[4096], skyB = new float[4096];

    @Override public void drawSky() {
        for (int y = 0; y < H; y++) {
            float elev = (float) Math.atan((hy - (y + 0.5f)) / focal);
            int r, g, b;
            if (elev >= 0f) {
                float t = (float) Math.sqrt(Math.min(1f, elev / 0.9f));
                r = (int) (fogR + (zenR - fogR) * t); g = (int) (fogG + (zenG - fogG) * t); b = (int) (fogB + (zenB - fogB) * t);
            } else {
                // below the horizon (past the draw distance) the fogged ground is fog coloured
                r = fogR; g = fogG; b = fogB;
            }
            skyR[y] = r; skyG[y] = g; skyB[y] = b;
            int row = y * W;
            int dy = (y & 3) << 2;
            int c0 = q(r, g, b, BAYER[dy] >> 1), c1 = q(r, g, b, BAYER[dy + 1] >> 1);
            int c2 = q(r, g, b, BAYER[dy + 2] >> 1), c3 = q(r, g, b, BAYER[dy + 3] >> 1);
            sky[y * 4] = c0; sky[y * 4 + 1] = c1; sky[y * 4 + 2] = c2; sky[y * 4 + 3] = c3;
            // with a ground map, the sky is filled per column above the ground instead (no overdraw)
            if (ground != null) continue;
            for (int x = 0; x < W; x += 4) {
                fb[row + x] = c0;
                if (x + 1 < W) fb[row + x + 1] = c1;
                if (x + 2 < W) fb[row + x + 2] = c2;
                if (x + 3 < W) fb[row + x + 3] = c3;
            }
        }
    }

    private static int q(int r, int g, int b, int d) {
        r = Math.min(31, (r + d) >> 3);
        g = Math.min(31, (g + d) >> 3);
        b = Math.min(31, (b + d) >> 3);
        return 0xFF000000 | (((r << 3) | (r >> 2)) << 16) | (((g << 3) | (g >> 2)) << 8) | ((b << 3) | (b >> 2));
    }

    // ------------------------------------------------------------------ Mode 7 ground

    /**
     * Per pixel, 1/depth of the ground drawn there (0 for sky), with 6% slack so things standing
     * on the ground still show. Polygons and sprites behind a hill fail this test; they never write
     * it (polygons are ordered among themselves by the ordering table).
     */
    private final float[] groundZ;
    /** Small tolerance on the ground test (things touching the ground). */
    private static final float GROUND_SLACK = 1.004f;
    private final float[] mipD = new float[Ground.MIPS];
    private Ground.Tile[] snap = new Ground.Tile[0];
    private int snapX, snapZ, snapN;
    private final java.util.List<java.util.concurrent.Callable<Void>> columnBands = new java.util.ArrayList<>();
    private final java.util.List<java.util.concurrent.Callable<Void>> transposeBands = new java.util.ArrayList<>();
    private final java.util.List<TileCache> tileCaches = new java.util.ArrayList<>();
    private final TileCache soloCache = new TileCache();

    /** Per-thread tile cache. */
    private static final class TileCache {
        Ground.Tile t;
        int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;
        float x0, z0;

        boolean at(SoftRenderer r, float x, float z) {
            if (x >= x0 && z >= z0 && x < x0 + Ground.TILE && z < z0 + Ground.TILE && cx != Integer.MIN_VALUE) return t != null;
            float fxx = x * (1f / Ground.TILE), fzz = z * (1f / Ground.TILE);
            int tx = (int) fxx, tz = (int) fzz;
            if (fxx < tx) tx--;
            if (fzz < tz) tz--;
            if (tx != cx || tz != cz) {
                cx = tx; cz = tz;
                int i = tx - r.snapX, j = tz - r.snapZ;
                t = i >= 0 && j >= 0 && i < r.snapN && j < r.snapN ? r.snap[j * r.snapN + i] : null;
                x0 = tx * Ground.TILE; z0 = tz * Ground.TILE;
            }
            return t != null;
        }
    }

    /**
     * Projects the ground map: every screen column is a vertical slice of the world, marched from the
     * camera outwards. Each step's height gives the row where the ground reaches; the rows between it
     * and what is already drawn are filled texel by texel, interpolating along the slice (exact for
     * the flat parts, so the map looks like a proper Mode 7 plane, and hills rise and hide).
     */
    @Override public void drawGround() {
        if (ground == null) { Arrays.fill(groundZ, 0f); return; }
        updateWater();
        for (int k = 0; k < Ground.MIPS; k++) mipD[k] = Ground.TEXEL * focal * (1 << k) * 1.4f;
        // snapshot of the tiles in view range: per-pixel lookups index an array, no shared map
        int reach = (int) Math.ceil(far / Ground.TILE) + 1;
        snapN = reach * 2 + 1;
        if (snap.length < snapN * snapN) snap = new Ground.Tile[snapN * snapN];
        snapX = (int) Math.floor(ex / Ground.TILE) - reach;
        snapZ = (int) Math.floor(ez / Ground.TILE) - reach;
        for (int j = 0; j < snapN; j++)
            for (int i = 0; i < snapN; i++) snap[j * snapN + i] = ground.tile(snapX + i, snapZ + j);
        // tiles stream in and out between frames
        for (TileCache tc : tileCaches) { tc.cx = Integer.MIN_VALUE; tc.t = null; }
        soloCache.cx = Integer.MIN_VALUE;
        soloCache.t = null;
        if (colFb.length != W * H) { colFb = new int[W * H]; colZ = new float[W * H]; }
        if (pool == null) { groundColumns(0, W, soloCache); transposeGround(0, H); }
        else {
            try {
                pool.invokeAll(columnBands);
                pool.invokeAll(transposeBands);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private float groundHeight(TileCache tc, float x, float z) {
        if (!tc.at(this, x, z)) return ground.height(x, z);
        float gx = (x - tc.x0) / Ground.HCELL, gz = (z - tc.z0) / Ground.HCELL;
        int ix = (int) gx, iz = (int) gz;
        if (ix > Ground.HCELLS - 1) ix = Ground.HCELLS - 1;
        if (iz > Ground.HCELLS - 1) iz = Ground.HCELLS - 1;
        float tx = gx - ix, tz = gz - iz;
        float[] h = tc.t.h;
        int o = iz * (Ground.HCELLS + 1) + ix;
        float a = h[o], b = h[o + 1], c = h[o + Ground.HCELLS + 1], d = h[o + Ground.HCELLS + 2];
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    /** 15-bit colour (r5 g5 b5) of the ground map at (x, z), mip chosen by view depth d. */
    private int mipFor(float d) {
        int k = 0;
        if (d > mipD[1]) { k = 1; while (k < Ground.MIPS - 1 && d > mipD[k + 1]) k++; }
        return k;
    }

    private int groundColor(TileCache tc, float x, float z, int k) {
        if (!tc.at(this, x, z)) {
            int c = ground.color(x, z);
            return ((c >> 19) & 31) << 10 | ((c >> 11) & 31) << 5 | ((c >> 3) & 31);
        }
        int n = Ground.RES >> k;
        float sc = n / Ground.TILE;
        int px = (int) ((x - tc.x0) * sc), pz = (int) ((z - tc.z0) * sc);
        if (px >= n) px = n - 1; else if (px < 0) px = 0;
        if (pz >= n) pz = n - 1; else if (pz < 0) pz = 0;
        return tc.t.mips[k][(((pz >> 3) * (n >> 3) + (px >> 3)) << 6) | ((pz & 7) << 3) | (px & 7)];
    }

    /** Season (dry grass turns straw coloured) and scene light applied to the 15-bit ground colours. */
    private void buildGroundLut() {
        float dk = dryness * 0.6f;
        for (int c = 0; c < 32768; c++) {
            int r = (c >> 10) & 31, g = (c >> 5) & 31, b = c & 31;
            float R = r << 3 | r >> 2, G = g << 3 | g >> 2, B = b << 3 | b >> 2;
            if (dk > 0f && G > R && G > B) {
                float k = Math.min(1f, (G - Math.max(R, B)) / 40f) * dk;
                R += (G * 0.95f - R) * k;
                G -= G * 0.1f * k;
                B -= B * 0.35f * k;
            }
            int ri = Math.min(255, (int) (R * tR) >> 8), gi = Math.min(255, (int) (G * tG) >> 8), bi = Math.min(255, (int) (B * tB) >> 8);
            ri >>= 3; gi >>= 3; bi >>= 3;
            groundLut[c] = 0xFF000000 | ((ri << 3 | ri >> 2) << 16) | ((gi << 3 | gi >> 2) << 8) | (bi << 3 | bi >> 2);
        }
    }

    // ------------------------------------------------------------------ animated sea

    /*
     * The sea is animated per pixel over the painted ground. Each coastal tile carries a 1 m map of
     * the sea-floor / sand height; every frame the renderer builds one small tiling wave field
     * (two drifting layers of swell plus ripples, lit by the sun with a glint, as brightness and
     * highlight), and each water pixel then combines: colour by depth (turquoise shallows over sand,
     * blue to navy offshore, the sand showing through the shallowest water), the wave lighting, the
     * sky reflected more at grazing angles, breaking waves as foam bands rolling in toward the beach
     * along the depth contours, and the swash: the water's edge runs up the sand after each breaker
     * and slides back, leaving darker, glistening wet sand.
     */
    public static final int WN = 128, WMASK = WN - 1;
    public static final float[] SWELL_A = new float[WN * WN], SWELL_B = new float[WN * WN], RIPPLE = new float[WN * WN];
    public static final float[] FOAM = new float[WN * WN], ALONG = new float[64 * 64];
    static {
        for (int y = 0; y < WN; y++)
            for (int x = 0; x < WN; x++) {
                int i = y * WN + x;
                SWELL_A[i] = com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 4, 4, 5101) * 0.6f
                        + com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 8, 8, 5102) * 0.4f;
                SWELL_B[i] = com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 6, 6, 5103) * 0.7f
                        + com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 12, 12, 5104) * 0.3f;
                RIPPLE[i] = com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 24, 24, 5105) * 0.6f
                        + com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 48, 48, 5106) * 0.4f;
                // foam: lacy cells, bright edges with holes
                float f = com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 16, 16, 5107) * 0.55f
                        + com.harlequinxd.drivehard.core.tex.Noise.value(x, y, WN, WN, 32, 32, 5108) * 0.3f
                        + com.harlequinxd.drivehard.core.tex.Noise.rnd(x, y, 5109) * 0.15f;
                FOAM[i] = f;
            }
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++)
                ALONG[y * 64 + x] = com.harlequinxd.drivehard.core.tex.Noise.value(x, y, 64, 64, 8, 8, 5110);
    }
    /** This frame's wave field: brightness (256 = 1) and sun highlight (0..255), 16 m tiling. */
    private final int[] waveMod = new int[WN * WN], waveSpec = new int[WN * WN];
    private float seaChop = 1f;
    private int seaSkyR, seaSkyG, seaSkyB;

    private void updateWater() {
        float t = time;
        Environment e0 = env;
        float wind = e0 == null ? 0.3f : Math.min(1f, 0.25f + e0.cloudCover * 0.5f + e0.rain * 0.6f);
        seaChop = 0.7f + wind * 0.9f;
        // two swells drifting in different directions, plus fast fine ripples
        int ax = (int) (t * 9f), az = (int) (t * 5f), bx = (int) (-t * 6f), bz = (int) (t * 11f);
        int rx2 = (int) (t * 23f), rz2 = (int) (-t * 17f);
        float amp = seaChop;
        // light: the sun (or a dim moon-ish sky light at night), viewer looking along the camera
        float lx = sunX, ly = Math.max(0.15f, sunY), lz = sunZ;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll; ly /= ll; lz /= ll;
        float vx = -fx, vy = 0.45f, vz = -fz;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl; vy /= vl; vz /= vl;
        float hx = lx + vx, hy2 = ly + vy, hz = lz + vz;
        float hl = (float) Math.sqrt(hx * hx + hy2 * hy2 + hz * hz);
        hx /= hl; hy2 /= hl; hz /= hl;
        float glint = e0 == null ? 1f : sunVis * (1f - e0.cloudCover * 0.7f);
        for (int y = 0; y < WN; y++)
            for (int x = 0; x < WN; x++) {
                // height and its slope from the three layers (finite differences, wrapping)
                float dX = field(x + 1, y, ax, az, bx, bz, rx2, rz2) - field(x - 1, y, ax, az, bx, bz, rx2, rz2);
                float dZ = field(x, y + 1, ax, az, bx, bz, rx2, rz2) - field(x, y - 1, ax, az, bx, bz, rx2, rz2);
                float nx = -dX * 2.2f * amp, nz = -dZ * 2.2f * amp, ny = 1f;
                float nl = 1f / (float) Math.sqrt(nx * nx + 1f + nz * nz);
                nx *= nl; ny *= nl; nz *= nl;
                float diff = nx * lx + ny * ly + nz * lz;
                int i = y * WN + x;
                waveMod[i] = (int) (256f * (0.72f + 0.42f * diff));
                float sp = nx * hx + ny * hy2 + nz * hz;
                if (sp > 0.97f) {
                    float g = (sp - 0.97f) / 0.03f;
                    g *= g;
                    waveSpec[i] = (int) Math.min(255f, g * 255f * glint);
                } else waveSpec[i] = 0;
            }
        seaSkyR = (fogR * 3 + zenR) >> 2;
        seaSkyG = (fogG * 3 + zenG) >> 2;
        seaSkyB = (fogB * 3 + zenB) >> 2;
    }

    public static float field(int x, int y, int ax, int az, int bx, int bz, int rx, int rz) {
        return SWELL_A[((y + az) & WMASK) * WN + ((x + ax) & WMASK)] * 0.55f
                + SWELL_B[((y * 2 + bz) & WMASK) * WN + ((x * 2 + bx) & WMASK)] * 0.3f
                + RIPPLE[((y * 3 + rz) & WMASK) * WN + ((x * 3 + rx) & WMASK)] * 0.15f;
    }

    /** Smooth along-shore phase offset (bilinear over a 37 m lattice), so breakers arrive unevenly. */
    private static float along(float x, float z) {
        float gx = x * (1f / 37f), gz = z * (1f / 37f);
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        float tx = gx - ix, tz = gz - iz;
        tx = tx * tx * (3f - 2f * tx);
        tz = tz * tz * (3f - 2f * tz);
        float a = ALONG[(iz & 63) * 64 + (ix & 63)], b = ALONG[(iz & 63) * 64 + ((ix + 1) & 63)];
        float c = ALONG[((iz + 1) & 63) * 64 + (ix & 63)], d = ALONG[((iz + 1) & 63) * 64 + ((ix + 1) & 63)];
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    /** Water level at the beach right now: the swash after each breaker (m above sea level). */
    private static float swash(float phase) {
        float p = phase - (float) Math.floor(phase);
        float r = p < 0.15f ? p / 0.15f : Math.max(0f, 1f - (p - 0.15f) / 0.55f);
        return -0.03f + 0.24f * r * r * (3f - 2f * r);
    }

    /**
     * The colour of a ground pixel that may be sea or beach; returns the painted colour c where
     * the water does not reach.
     */
    private int water(TileCache tc, float x, float z, float dist, int c, int mip, int sx, int sy) {
        byte[] wm = tc.t.water;
        float fx2 = x - tc.x0, fz2 = z - tc.z0;
        int ix = (int) fx2, iz = (int) fz2;
        if (ix < 0) ix = 0; else if (ix > 63) ix = 63;
        if (iz < 0) iz = 0; else if (iz > 63) iz = 63;
        float tx = fx2 - ix, tz = fz2 - iz;
        int o = iz * 65 + ix;
        int a = wm[o] & 255, b = wm[o + 1] & 255, cc = wm[o + 65] & 255, d = wm[o + 66] & 255;
        if ((a | b | cc | d) == 0) return c;
        // land the waves never reach counts as a little above their reach
        float ha = a == 0 ? 0.8f : Ground.Tile.waterHeight(a), hb = b == 0 ? 0.8f : Ground.Tile.waterHeight(b);
        float hc = cc == 0 ? 0.8f : Ground.Tile.waterHeight(cc), hd = d == 0 ? 0.8f : Ground.Tile.waterHeight(d);
        float top = ha + (hb - ha) * tx, bot = hc + (hd - hc) * tx;
        float h = top + (bot - top) * tz;
        if (h > 0.5f) return c;
        // breakers roll in along the depth contours; the phase wanders along the shore
        float along = along(x, z);
        float t = time;
        float phase0 = t * 0.11f + along * 2.3f;                  // one set of breakers every ~9 s
        float level = swash(phase0);
        int wi = ((((int) (z * 8f)) & WMASK) << 7) | (((int) (x * 8f)) & WMASK);
        if (h >= level) {
            // dry or wet sand: darker below the highest swash, glistening where the water just left
            if (h < 0.24f) {
                float wet = 1f - h / 0.24f;
                int k = 256 - (int) (wet * 95f);
                int r = ((c >> 16) & 255) * k >> 8, g = ((c >> 8) & 255) * k >> 8, bl = (c & 255) * k >> 8;
                if (h < level + 0.05f) {
                    int sh = 28 + (waveSpec[wi] >> 2);
                    r += sh; g += sh; bl += sh;
                }
                return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, bl);
            }
            return c;
        }
        float depth = level - h;                                  // water depth over the sand / sea floor
        // colour by depth: clear turquoise shallows to deep blue
        int wr, wg, wb;
        if (depth < 1.5f) { float k = depth / 1.5f; wr = (int) (64 + (30 - 64) * k); wg = (int) (196 + (150 - 196) * k); wb = (int) (190 + (178 - 190) * k); }
        else if (depth < 6f) { float k = (depth - 1.5f) / 4.5f; wr = (int) (30 + (20 - 30) * k); wg = (int) (150 + (96 - 150) * k); wb = (int) (178 + (150 - 178) * k); }
        else { float k = Math.min(1f, (depth - 6f) / 6f); wr = (int) (20 + (14 - 20) * k); wg = (int) (96 + (64 - 96) * k); wb = (int) (150 + (112 - 150) * k); }
        // waves: lighting from this frame's field (calmer far away, where they would only shimmer)
        int mod = waveMod[wi], spec = waveSpec[wi];
        if (mip >= 1) { mod = 256 + ((mod - 256) >> mip); spec >>= mip; }
        // daylight tint like the painted ground
        wr = (wr * tR >> 8) * mod >> 8; wg = (wg * tG >> 8) * mod >> 8; wb = (wb * tB >> 8) * mod >> 8;
        // sky reflection, stronger at grazing angles
        float graze = (ey + 2f) / Math.max(1f, dist);
        float refl = Math.max(0f, Math.min(0.62f, 0.7f - graze * 2.2f));
        wr += (int) ((seaSkyR - wr) * refl); wg += (int) ((seaSkyG - wg) * refl); wb += (int) ((seaSkyB - wb) * refl);
        // the sand shows through the shallowest water
        float clear = 1f - Math.min(1f, depth / 0.7f);
        if (clear > 0f) {
            float k = clear * 0.75f;
            wr += (int) ((((c >> 16) & 255) - wr) * k); wg += (int) ((((c >> 8) & 255) - wg) * k); wb += (int) (((c & 255) - wb) * k);
        }
        // foam: breaking crests rolling in, and lace at the water's edge
        float foam = 0f;
        if (depth < 4f) {
            float ph = phase0 + depth * 0.32f;
            float f = ph - (float) Math.floor(ph);
            float crest = f < 0.1f ? 1f - f / 0.1f : (f > 0.96f ? (f - 0.96f) / 0.04f : 0f);
            float wash = f < 0.35f ? (0.35f - f) / 0.35f * 0.35f : 0f;   // the white water behind each crest
            foam = Math.max(crest, wash) * (1f - depth / 4f) * (0.55f + 0.45f * seaChop);
        }
        if (depth < 0.08f) foam = Math.max(foam, 0.85f * (1f - depth / 0.08f));
        if (foam > 0f) {
            int fi = ((((int) (z * 6f + t * 1.3f)) & WMASK) << 7) | (((int) (x * 6f)) & WMASK);
            float lace = FOAM[fi];
            float fa = Math.max(0f, Math.min(1f, (foam - (1f - lace) * 0.75f) * 2.2f));
            if (mip >= 3) fa *= 0.6f;
            int fr = 236 * tR >> 8, fg = 242 * tG >> 8, fb = 240 * tB >> 8;
            wr += (int) ((fr - wr) * fa); wg += (int) ((fg - wg) * fa); wb += (int) ((fb - wb) * fa);
            spec >>= 1;
        }
        wr += spec; wg += spec; wb += spec * 15 / 16;
        int dth = BAYER[((sy & 3) << 2) | (sx & 3)] >> 1;
        return q(Math.max(0, Math.min(255, wr)), Math.max(0, Math.min(255, wg)), Math.max(0, Math.min(255, wb)), dth);
    }

    /** Cloud density, 256x256, tiles (about 24 m per texel). */
    public static final float[] CLOUDS = new float[256 * 256];
    static {
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) {
                float n = com.harlequinxd.drivehard.core.tex.Noise.fbm(x, y, 256, 256, 9127) * 0.7f
                        + com.harlequinxd.drivehard.core.tex.Noise.value(x, y, 256, 256, 64, 64, 9131) * 0.3f;
                CLOUDS[y * 256 + x] = n;
            }
    }
    private static final float CLOUD_H = 900f, CLOUD_SCALE = 1f / 24f;

    /**
     * One sky pixel: the gradient, then stars, moon, sun with its glow, a rainbow and the cloud
     * layer (a plane 900 m up, drifting with the wind).
     */
    private int skyPixel(int x, int y, float dx, float dz, float hl2, float az) {
        float r = skyR[y], g = skyG[y], b = skyB[y];
        float s = (hy - (y + 0.5f)) / focal;
        if (s > 0f) {
            float inv = 1f / (float) Math.sqrt(hl2 + s * s);
            float vx = dx * inv, vy = s * inv, vz = dz * inv;
            if (stars > 0f && vy > 0.06f) {
                int hs = com.harlequinxd.drivehard.core.tex.Noise.hash((int) (az * 300f), (int) (vy * 300f), 77);
                if ((hs & 1023) < 4) {
                    float k = stars * (120 + ((hs >>> 24) & 127));
                    r += k; g += k; b += k * 0.9f;
                }
            }
            if (moonVis > 0f) {
                float d = vx * moonX + vy * moonY + vz * moonZ;
                if (d > 0.99935f) { r += (230 - r) * moonVis; g += (232 - g) * moonVis; b += (220 - b) * moonVis; }
                else if (d > 0.995f) { float k = (d - 0.995f) / 0.00435f * 40f * moonVis; r += k; g += k; b += k; }
            }
            if (sunVis > 0f) {
                float d = vx * sunX + vy * sunY + vz * sunZ;
                if (d > 0.9991f) { r += (255 - r) * sunVis; g += (250 - g) * sunVis; b += (228 - b) * sunVis; }
                else if (d > 0.96f) { float k = (d - 0.96f) / 0.0391f; k = k * k * 130f * sunVis; r += k; g += k * 0.85f; b += k * 0.55f; }
            }
            if (rainbow > 0f) {
                float d = -(vx * sunX + vy * sunY + vz * sunZ);
                if (d > 0.733f && d < 0.778f) {
                    float f = (d - 0.733f) / 0.045f;               // 0 outside (red) .. 1 inside (violet)
                    float k = rainbow * 0.32f * (1f - Math.abs(f - 0.5f) * 1.2f);
                    float cr, cg, cb;
                    if (f < 0.2f) { cr = 255; cg = 40; cb = 30; }
                    else if (f < 0.35f) { cr = 255; cg = 150; cb = 20; }
                    else if (f < 0.5f) { cr = 250; cg = 240; cb = 40; }
                    else if (f < 0.66f) { cr = 40; cg = 220; cb = 60; }
                    else if (f < 0.82f) { cr = 40; cg = 90; cb = 255; }
                    else { cr = 150; cg = 50; cb = 220; }
                    r += (cr - r) * k; g += (cg - g) * k; b += (cb - b) * k;
                }
            }
            if (cover > 0.02f && s > 0.012f) {
                float t = (CLOUD_H - ey) / s;
                float wx = ex + dx * t + cloudOX, wz = ez + dz * t + cloudOZ;
                float n = CLOUDS[(((int) (wz * CLOUD_SCALE)) & 255) << 8 | (((int) (wx * CLOUD_SCALE)) & 255)];
                float th = 1f - cover;
                float a = (n - th * 0.85f - 0.08f) / 0.2f;
                if (a > 0f) {
                    if (a > 1f) a = 1f;
                    a *= Math.min(1f, 1.5f - t / 14000f);
                    if (cover > 0.9f) a = Math.max(a, (cover - 0.9f) * 10f);   // a full grey deck
                    if (a > 0f) {
                        float sh = Math.min(1f, (n - th * 0.85f) * 2.2f);
                        int cl = cloudLit, cs = cloudShade;
                        float cr = ((cl >> 16) & 255) + ((((cs >> 16) & 255) - ((cl >> 16) & 255)) * sh);
                        float cg = ((cl >> 8) & 255) + ((((cs >> 8) & 255) - ((cl >> 8) & 255)) * sh);
                        float cb = (cl & 255) + (((cs & 255) - (cl & 255)) * sh);
                        r += (cr - r) * a; g += (cg - g) * a; b += (cb - b) * a;
                    }
                }
            }
        }
        if (skyHaze > 0f) {
            float k = skyHaze * (s > 0f ? Math.max(0.35f, 1f - s * 0.9f) : 1f);
            r += (fogR - r) * k; g += (fogG - g) * k; b += (fogB - b) * k;
        }
        int d = BAYER[((y & 3) << 2) | (x & 3)] >> 1;
        return q(Math.min(255, (int) r), Math.min(255, (int) g), Math.min(255, (int) b), d);
    }

    /** Column-major scratch (x * H + y): each column is written contiguously, then transposed. */
    private int[] colFb = new int[0];
    private float[] colZ = new float[0];

    /** One screen column at a time, front to back over the height-mapped ground. */
    private void groundColumns(int colA, int colB, TileCache tc) {
        int h = H;
        int[] sk = sky;
        for (int x = colA; x < colB; x++) {
            float xs = (x + 0.5f - cx) / focal;
            float dx = fx + rx * xs, dz = fz + rz * xs;
            int base = x * h;
            int ybuf = h;
            float d = 0.3f, dPrev = 0f, sPrev = 0f;
            boolean first = true;
            while (d <= far) {
                float wx = ex + dx * d, wz = ez + dz * d;
                float gh = groundHeight(tc, wx, wz);
                float sc = hy - (gh - ey) * focal / d;
                ybuf = surfaceSpan(tc, x, base, ybuf, first, dPrev, sPrev, d, sc, dx, dz);
                if (ybuf == 0) break;
                first = false;
                dPrev = d;
                sPrev = sc;
                d += Math.max(0.9f, d * 0.03f);
            }
            if (env == null) {
                for (int y = 0; y < ybuf; y++) {
                    colFb[base + y] = sk[y * 4 + (x & 3)];
                    colZ[base + y] = 0f;
                }
            } else if (ybuf > 0) {
                float hl2 = dx * dx + dz * dz, az = (float) Math.atan2(dx, dz);
                for (int y = 0; y < ybuf; y++) {
                    colFb[base + y] = skyPixel(x, y, dx, dz, hl2, az);
                    colZ[base + y] = 0f;
                }
            }
        }
    }

    /** Fills the rows between the last ground samples, returning the new top of the column. */
    private int surfaceSpan(TileCache tc, int x, int base, int ybuf, boolean first, float dPrev, float sPrev,
                            float d, float s, float dx, float dz) {
        int ys = (int) Math.ceil(s - 0.5f);
        if (ys < 0) ys = 0;
        if (ys >= ybuf) return ybuf;
        float fogK = 1f / (fogEnd - fogStart), fs = fogStart;
        int fr = fogR, fg = fogG, fbb = fogB;
        int[] cf = colFb;
        // 1/depth is linear down the screen between the two samples
        float invC = 1f / d, invP = first ? invC : 1f / dPrev, span = s - sPrev;
        float iz, diz;
        if (first || span > -1e-3f) { iz = invC; diz = 0f; }
        else {
            diz = -(invC - invP) / span;                  // per row upwards
            iz = invP + (invC - invP) * (((ybuf - 1) + 0.5f - sPrev) / span);
        }
        float lo = Math.min(invP, invC), hi = Math.max(invP, invC);
        int mip = mipFor(1f / hi);          // one mip level per span (the nearer end)
        for (int y = ybuf - 1; y >= ys; y--, iz += diz) {
            float izc = iz < lo ? lo : (iz > hi ? hi : iz);
            float dr = 1f / izc;
            float px = ex + dx * dr, pz = ez + dz * dr;
            int k = base + y;
            colZ[k] = izc * 0.94f;
            int c = groundLut[groundColor(tc, px, pz, mip)];
            if (tc.t != null && tc.t.water != null) c = water(tc, px, pz, dr, c, mip, x, y);
            if (dr <= fs) cf[k] = c;
            else {
                int fv = (int) Math.min(256f, (dr - fs) * fogK * 256f);
                cf[k] = shadeRaw(c, 256, fv, fr, fg, fbb, BAYER[((y & 3) << 2) | (x & 3)] >> 1);
            }
        }
        return ys;
    }

    /**
     * A wall seen face on: rows from the top of the wall down to what is already drawn, at depth t.
     * Each row's height picks the storey and the tile (plain wall, window, door, shopfront).
     */
    private int wallSpan(int x, int base, int ybuf, float sTop, float t, Sectors.Wall w, float u, int[] cov, int stamp) {
        int ys = (int) Math.ceil(sTop - 0.5f);
        if (ys < 0) ys = 0;
        if (ys >= ybuf) return ybuf;
        float fogK = 1f / (fogEnd - fogStart);
        int fv = t <= fogStart ? 0 : (int) Math.min(256f, (t - fogStart) * fogK * 256f);
        int lv = (int) (w.light * 256f);
        float k = t / focal;
        float izw = 1f / t * 0.995f;
        Sectors.Tiles tl = w.tiles;
        Sectors.Sector sec = w.front;
        int seed = nightLights ? Float.floatToIntBits(w.x0 * 3.1f + w.z0 * 1.7f + w.uOff) : 0;
        for (int y = ybuf - 1; y >= ys; y--) {
            if (cov[y] == stamp) continue;
            float wy = ey + (hy - (y + 0.5f)) * k;        // world height of this row on the wall
            int c = wallTexel(tl, sec.base, u + w.uOff, wy, w.faceLen, seed);
            int q = base + y;
            int d = BAYER[((y & 3) << 2) | (x & 3)] >> 1;
            // lit windows and shopfronts glow at night: not darkened by the scene light
            colFb[q] = (c >>> 24) == 0xFE ? shadeRaw(c, 256, fv >> 1, fogR, fogG, fogB, d) : shade(c, lv, fv, fogR, fogG, fogB, d);
        }
        return ys;
    }

    private int sampleWrap(int texId, float u, float v) {
        Texture t = textures[texId];
        int tu = (int) Math.floor(u * t.w) & t.wMask, tv = (int) Math.floor(v * t.h) & t.hMask;
        if (tv > t.hMax) tv %= t.h;
        return t.px[(tv << t.wShift) | tu];
    }

    private int sampleClamp(int texId, float u, float v) {
        Texture t = textures[texId];
        int tu = Math.max(0, Math.min(t.w - 1, (int) (u * t.w))), tv = Math.max(0, Math.min(t.hMax, (int) (v * t.h)));
        return t.px[(tv << t.wShift) | tu];
    }

    /** Colour of a wall at (u along its face, height y): foundation, plain wall or the tile of its storey. */
    private int wallTexel(Sectors.Tiles tl, float base, float u, float y, float len, int seed) {
        float yr = y - base;
        if (yr < 0f) return sampleWrap(tl.found, u * 0.5f, -y * 0.5f);
        int st;
        float fy, sh;
        if (yr < tl.groundH) { st = 0; fy = yr; sh = tl.groundH; }
        else {
            st = 1 + (int) ((yr - tl.groundH) / tl.storeyH);
            fy = yr - tl.groundH - (st - 1) * tl.storeyH;
            sh = tl.storeyH;
        }
        int plain = st == 0 ? tl.mat : tl.matUp;
        if (st >= tl.floors) return sampleWrap(plain, u / tl.matScale, -y / tl.matScale);
        int cols = tl.cols[st];
        float tw = len / cols;
        int cidx = Math.min(cols - 1, Math.max(0, (int) (u / tw)));
        byte kind = tl.kind[st][cidx];
        float fu = u - cidx * tw;
        switch (kind) {
            case Sectors.FULL: {
                int c = sampleClamp(tl.tex[st][cidx], fu / tw, 1f - fy / sh);
                if (seed == 0) return c;
                if (st == 0 && tl.litGround) return 0xFE000000 | (c & 0xFFFFFF);   // shops stay open
                if (!tl.glowFacade) return c;
                // facade tiles: the glass of the painted windows (blue-ish or dark) glows
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int l = (r * 3 + g * 6 + b) / 10;
                return b > r + 10 || l < 80 ? glow(c, st, cidx, seed) : c;
            }
            case Sectors.WINDOW: case Sectors.SMALL: case Sectors.DOOR: case Sectors.GARDEN: {
                float ow, oy0, oy1;
                if (kind == Sectors.WINDOW) { ow = Math.min(tl.winW, tw - 0.5f); oy0 = tl.sill; oy1 = tl.sill + tl.winH; }
                else if (kind == Sectors.SMALL) { ow = Math.min(0.7f, tw - 0.4f); oy0 = 1.3f; oy1 = 2.1f; }
                else if (kind == Sectors.DOOR) { ow = Math.min(tl.doorW, tw - 0.3f); oy0 = 0f; oy1 = tl.doorH; }
                else { ow = Math.min(1.8f, tw - 0.5f); oy0 = 0.1f; oy1 = 2.1f; }
                oy1 = Math.min(oy1, sh - 0.05f);
                float ou0 = (tw - ow) * 0.5f;
                if (fu >= ou0 && fu < ou0 + ow && fy >= oy0 && fy < oy1) {
                    int tid = tl.tex[st][cidx];
                    Texture T = textures[tid];
                    float su = (fu - ou0) / ow, sv = 1f - (fy - oy0) / (oy1 - oy0);
                    int idx = (Math.max(0, Math.min(T.hMax, (int) (sv * T.h))) << T.wShift) | Math.max(0, Math.min(T.w - 1, (int) (su * T.w)));
                    int c = T.px[idx];
                    // set into the wall: a darker reveal round the opening
                    float edge = Math.min(Math.min(fu - ou0, ou0 + ow - fu), Math.min(fy - oy0, oy1 - fy));
                    if (edge < 0.07f) return Img2.darken(c);
                    // at night the panes (not the frames or glazing bars) of lit rooms glow
                    if (seed != 0 && kind != Sectors.DOOR && glassMask(tid)[idx] != 0) return glow(c, st, cidx, seed);
                    return c;
                }
                return sampleWrap(plain, u / tl.matScale, -y / tl.matScale);
            }
            default:
                return sampleWrap(plain, u / tl.matScale, -y / tl.matScale);
        }
    }

    /** A pane texel at night: if this room is lit it glows warm (marked 0xFE: no scene light). */
    private int glow(int c, int st, int col, int seed) {
        int h = com.harlequinxd.drivehard.core.tex.Noise.hash(col, st, seed) & 255;
        if (h >= litShare) return c;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int l = (r * 3 + g * 6 + b) / 10;
        float k = 0.78f + l / 255f * 0.3f;
        boolean cool = (h & 7) == 0;                       // a few rooms lit by a television
        int lr = (int) ((cool ? 170 : 255) * k), lg = (int) ((cool ? 200 : 214) * k), lb = (int) ((cool ? 255 : 138) * k);
        return 0xFE000000 | (Math.min(255, lr) << 16) | (Math.min(255, lg) << 8) | Math.min(255, lb);
    }

    private volatile byte[][] glass = new byte[0][];

    /**
     * Which texels of a window texture are panes: the dominant colour of its middle (glass,
     * frosted glass, shoji paper), as opposed to frames and glazing bars. Built once per texture.
     */
    private byte[] glassMask(int tid) {
        byte[][] gm = glass;
        if (tid < gm.length && gm[tid] != null) return gm[tid];
        Texture t = textures[tid];
        int w = t.w, h = Math.min(t.h, t.hMax + 1);
        // most common colour (4-bit per channel bins) in the central area
        int[] count = new int[4096];
        for (int y = h / 6; y < h - h / 6; y++)
            for (int x = w / 6; x < w - w / 6; x++) {
                int c = t.px[(y << t.wShift) | x];
                count[((c >> 12) & 0xF00) | ((c >> 8) & 0xF0) | ((c >> 4) & 0xF)]++;
            }
        int best = 0;
        for (int i = 1; i < 4096; i++) if (count[i] > count[best]) best = i;
        int br = ((best >> 8) << 4) + 8, bg = (((best >> 4) & 15) << 4) + 8, bb = ((best & 15) << 4) + 8;
        // average of the pane colour, then everything close to it
        long sr = 0, sg = 0, sb = 0, n = 0;
        for (int i = 0; i < w * h; i++) {
            int c = t.px[i];
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (Math.abs(r - br) + Math.abs(g - bg) + Math.abs(b - bb) < 60) { sr += r; sg += g; sb += b; n++; }
        }
        if (n > 0) { br = (int) (sr / n); bg = (int) (sg / n); bb = (int) (sb / n); }
        byte[] m = new byte[t.px.length];
        for (int i = 0; i < w * h; i++) {
            int c = t.px[i];
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if ((c >>> 24) > 128 && Math.abs(r - br) + Math.abs(g - bg) + Math.abs(b - bb) < 90) m[i] = 1;
        }
        synchronized (this) {
            byte[][] cur = glass;
            if (cur.length <= tid) cur = java.util.Arrays.copyOf(cur, Math.max(tid + 1, textures.length));
            cur[tid] = m;
            glass = cur;
        }
        return m;
    }

    /** Tiny colour helpers for the wall tiles. */
    private static final class Img2 {
        static int darken(int c) {
            return 0xFF000000 | ((((c >> 16) & 255) * 5 >> 3) << 16) | ((((c >> 8) & 255) * 5 >> 3) << 8) | ((c & 255) * 5 >> 3);
        }
    }

    /** Copies the column-major ground into the framebuffer in cache-sized blocks. */
    private void transposeGround(int y0, int y1) {
        int w = W, h = H;
        int[] cf = colFb, out = fb;
        float[] cz = colZ, zb = groundZ;
        for (int by = y0; by < y1; by += 16) {
            int ey2 = Math.min(y1, by + 16);
            for (int bx = 0; bx < w; bx += 16) {
                int ex2 = Math.min(w, bx + 16);
                for (int y = by; y < ey2; y++) {
                    int row = y * w;
                    for (int x = bx; x < ex2; x++) {
                        out[row + x] = cf[x * h + y];
                        zb[row + x] = cz[x * h + y];
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ parallel raster

    private final int[] order = new int[MAX_PRIMS];
    private final long[] orderKeys = new long[MAX_PRIMS];
    private int orderCount;
    private java.util.concurrent.ExecutorService pool;
    private final java.util.List<java.util.concurrent.Callable<Void>> bands = new java.util.ArrayList<>();

    /** Uses up to {@code threads} cores for rasterisation (1 = single threaded). */
    @Override public void setThreads(int threads) {
        if (pool != null) pool.shutdownNow();
        pool = null;
        bands.clear();
        columnBands.clear();
        transposeBands.clear();
        tileCaches.clear();
        tileCaches.add(soloCache);
        if (threads <= 1) return;
        pool = java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "raster");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
        int nb = threads * 3;
        for (int i = 0; i < nb; i++) {
            final int y0 = H * i / nb, y1 = H * (i + 1) / nb;
            bands.add(() -> { drawBand(y0, y1); return null; });
            transposeBands.add(() -> { transposeGround(y0, y1); return null; });
            final int x0 = W * i / nb, x1 = W * (i + 1) / nb;
            final TileCache tc = new TileCache();
            tileCaches.add(tc);
            columnBands.add(() -> { groundColumns(x0, x1, tc); return null; });
        }
    }

    @Override public void dispose() { setThreads(1); }

    /** Draws the ordering table by layer, with far polygons first so nearer polygons cover them. */
    @Override public void flush() {
        int n = 0;
        for (int layer = 0; layer < LAYERS; layer++) {
            int base = layer * BUCKETS;
            int layerStart = n;
            for (int b = BUCKETS - 1; b >= 0; b--) {
                for (int p = head[base + b]; p >= 0; p = next[p])
                    orderKeys[n++] = ((long) ~Float.floatToIntBits(pdepth[p]) << 32) | (p & 0xffffffffL);
            }
            Arrays.sort(orderKeys, layerStart, n);
            for (int i = layerStart; i < n; i++) order[i] = (int) orderKeys[i];
        }
        orderCount = n;
        statPrims = n;
        if (pool == null) {
            drawBand(0, H);
            return;
        }
        try {
            pool.invokeAll(bands);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drawBand(int y0, int y1) {
        for (int i = 0; i < orderCount; i++) {
            int p = order[i];
            if (pmode[p] == MODE_SPRITE) drawSprite(p, y0, y1);
            else drawTri(p, y0, y1);
        }
    }

    // ------------------------------------------------------------------ culling helpers

    // ------------------------------------------------------------------ submission

    private final float[] wp = new float[12], wuv = new float[8], wl = new float[4];

    /** Draws a batch translated by (ox,oy,oz): moving rigid objects such as trains. */
    @Override public void drawGeoOffset(Geo g, float ox, float oy, float oz) {
        if (g.count == 0 || !boxVisible(g.minX + ox, g.minY + oy, g.minZ + oz, g.maxX + ox, g.maxY + oy, g.maxZ + oz)) return;
        float far2 = far * far, detail2 = detailDist * detailDist;
        for (int i = 0; i < g.count; i++) {
            int o = i * 12;
            float p0x = g.pos[o] + ox, p0y = g.pos[o + 1] + oy, p0z = g.pos[o + 2] + oz;
            float nx = g.nrm[i * 3], ny = g.nrm[i * 3 + 1], nz = g.nrm[i * 3 + 2];
            if ((nx != 0f || ny != 0f || nz != 0f) && nx * (ex - p0x) + ny * (ey - p0y) + nz * (ez - p0z) <= 0f) continue;
            // cheap distance reject on the first vertex (+ generous slack for big polys)
            float dx = p0x - ex, dz = p0z - ez;
            float d2 = dx * dx + dz * dz;
            if (d2 > far2 * 1.6f) continue;
            int fl = g.flags(i);
            if ((fl & Geo.FLAG_DETAIL) != 0 && d2 > detail2) continue;
            Texture t = textures[g.tex[i]];
            float su = t.scrollU * time;
            for (int k = 0; k < 12; k += 3) { wp[k] = g.pos[o + k] + ox; wp[k + 1] = g.pos[o + k + 1] + oy; wp[k + 2] = g.pos[o + k + 2] + oz; }
            for (int k = 0; k < 4; k++) {
                wuv[k * 2] = (g.uv[i * 8 + k * 2] + su) * t.w;
                wuv[k * 2 + 1] = g.uv[i * 8 + k * 2 + 1] * t.h;
                wl[k] = g.light[i * 4 + k];
            }
            sortFar = (fl & Geo.FLAG_SORT_FAR) != 0;
            boolean blend = (fl & Geo.FLAG_BLEND) != 0 && g.alpha != null;
            if (blend) for (int k = 0; k < 4; k++) curAlpha[k] = g.alpha[i * 4 + k];
            submit(wp, wuv, wl, g.vertexCount(i), g.tex[i], g.layer(i),
                    blend ? MODE_BLEND : ((fl & Geo.FLAG_ALPHA) != 0 ? MODE_ALPHA : MODE_OPAQUE), g.bias[i], (fl & Geo.FLAG_NO_SUBDIV) == 0);
            if (blend) for (int k = 0; k < 4; k++) curAlpha[k] = 1f;
            sortFar = false;
        }
    }

    /**
     * Upright sprite facing the camera horizontally. Offsets are in metres relative to (x,y,z) along the
     * camera's horizontal right axis and world up; uv in texels.
     */
    @Override public void billboard(int texId, float x, float y, float z, float left, float right, float bottom, float top,
                          float u0, float v0, float u1, float v1, float bias) {
        float dx = x - ex, dz = z - ez;
        float vz = dx * fx + dz * fz;
        if (vz < near || vz > far) return;
        float vx = dx * rx + dz * rz;
        float k = focal / vz;
        float sx0 = cx + (vx + left) * k, sx1 = cx + (vx + right) * k;
        float sy0 = hy - (y + top - ey) * k, sy1 = hy - (y + bottom - ey) * k;
        if (sx1 < 0 || sx0 > W || sy1 < 0 || sy0 > H || sx1 - sx0 < 0.5f) return;
        if (primCount >= MAX_PRIMS) return;
        statPolys++;
        int p = primCount++;
        int o = p * 3 * VA;
        P[o] = sx0; P[o + 1] = sy0; P[o + 2] = sx1; P[o + 3] = sy1;
        P[o + 4] = u0; P[o + 5] = v0; P[o + 6] = u1; P[o + 7] = v1;
        float f = (vz - fogStart) / (fogEnd - fogStart);
        P[o + 8] = f < 0f ? 0f : (f > 1f ? 1f : f);
        P[o + 9] = 1f / vz;
        P[o + 10] = 1f;
        ptex[p] = texId;
        pmode[p] = MODE_SPRITE;
        float depth = Math.max(0f, vz + bias);
        pdepth[p] = depth;
        int bucket = (int) (depth * BUCKET_SCALE);
        if (bucket < 0) bucket = 0;
        if (bucket >= BUCKETS) bucket = BUCKETS - 1;
        int hb = L_OBJECT * BUCKETS + bucket;
        next[p] = head[hb];
        head[hb] = p;
    }

    /** Upright billboard corner: rotates only about the vertical axis (no tilt toward the camera). */
    private void corner(float[] p, int k, float x, float y, float z, float sx, float sy) {
        p[k * 3] = x + rx * sx;
        p[k * 3 + 1] = y + sy;
        p[k * 3 + 2] = z + rz * sx;
    }

    @Override public void groundQuad(int texId, float x, float y, float z, float r, int mode, int layer, float bias) {
        Texture t = textures[texId];
        float[] p = wp;
        p[0] = x - r; p[1] = y; p[2] = z + r;
        p[3] = x + r; p[4] = y; p[5] = z + r;
        p[6] = x + r; p[7] = y; p[8] = z - r;
        p[9] = x - r; p[10] = y; p[11] = z - r;
        wuv[0] = 0; wuv[1] = t.h; wuv[2] = t.w; wuv[3] = t.h; wuv[4] = t.w; wuv[5] = 0; wuv[6] = 0; wuv[7] = 0;
        wl[0] = wl[1] = wl[2] = wl[3] = 1f;
        submit(p, wuv, wl, 4, texId, layer, mode, bias, false);
    }

    /** Rotated flat quad on the ground (e.g. a car's shadow): half length along yaw, half width across. */
    @Override public void groundQuadRotated(int texId, float x, float y, float z, float halfLen, float halfWid, float yaw, int mode, int layer) {
        Texture t = textures[texId];
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = fz, rz = -fx;
        float[] p = wp;
        p[0] = x - fx * halfLen - rx * halfWid; p[1] = y; p[2] = z - fz * halfLen - rz * halfWid;
        p[3] = x - fx * halfLen + rx * halfWid; p[4] = y; p[5] = z - fz * halfLen + rz * halfWid;
        p[6] = x + fx * halfLen + rx * halfWid; p[7] = y; p[8] = z + fz * halfLen + rz * halfWid;
        p[9] = x + fx * halfLen - rx * halfWid; p[10] = y; p[11] = z + fz * halfLen - rz * halfWid;
        wuv[0] = 0; wuv[1] = t.h; wuv[2] = t.w; wuv[3] = t.h; wuv[4] = t.w; wuv[5] = 0; wuv[6] = 0; wuv[7] = 0;
        wl[0] = wl[1] = wl[2] = wl[3] = 1f;
        submit(p, wuv, wl, 4, texId, layer, mode, 0f, false);
    }

    private final float[] sq = new float[12], suv = new float[8], sl = {1f, 1f, 1f, 1f};

    @Override public void spriteQuad(int texId, float[] corners, float[] uvTexels, float bias) {
        System.arraycopy(corners, 0, sq, 0, 12);
        System.arraycopy(uvTexels, 0, suv, 0, 8);
        submit(sq, suv, sl, 4, texId, L_OBJECT, MODE_ALPHA, bias, false);
    }

    private final float[] vq = new float[4 * VA];

    /**
     * Submits a world-space polygon (3 or 4 verts). UVs in texels. No backface culling here.
     */
    public void submit(float[] p, float[] uvT, float[] l, int nv, int texId, int layer, int mode, float bias, boolean subdiv) {
        int allNear = 1, allFar = 1;
        float zsum = 0f, zmax = -Float.MAX_VALUE;
        // Rebase texture coordinates near zero: the rasteriser steps u/v in 16.16 fixed point, which
        // overflows past 32768 texels (world-mapped textures far from the origin). Textures wrap, so
        // shifting by whole texture sizes changes nothing visible.
        Texture tx = textures[texId];
        float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE;
        for (int k = 0; k < nv; k++) { minU = Math.min(minU, uvT[k * 2]); minV = Math.min(minV, uvT[k * 2 + 1]); }
        float shU = minU < 0f || minU >= tx.w ? (float) Math.floor(minU / tx.w) * tx.w : 0f;
        float shV = minV < 0f || minV >= tx.h ? (float) Math.floor(minV / tx.h) * tx.h : 0f;
        for (int k = 0; k < nv; k++) {
            float dx = p[k * 3] - ex, dy = p[k * 3 + 1] - ey, dz = p[k * 3 + 2] - ez;
            float vx = dx * rx + dy * ry + dz * rz;
            float vy = dx * ux + dy * uy + dz * uz;
            float vz = dx * fx + dy * fy + dz * fz;
            int o = k * VA;
            vq[o] = vx; vq[o + 1] = vy; vq[o + 2] = vz;
            vq[o + 3] = uvT[k * 2] - shU; vq[o + 4] = uvT[k * 2 + 1] - shV; vq[o + 5] = l[k]; vq[o + 6] = curAlpha[k];
            if (vz >= near) allNear = 0;
            if (vz <= far) allFar = 0;
            zsum += vz;
            if (vz > zmax) zmax = vz;
        }
        if (allNear == 1 || allFar == 1) return;
        // side planes
        int allL = 1, allR = 1, allT = 1, allB = 1;
        for (int k = 0; k < nv; k++) {
            float vx = vq[k * VA], vy = vq[k * VA + 1], vz = vq[k * VA + 2];
            if (vx >= -vz * tanX) allL = 0;
            if (vx <= vz * tanX) allR = 0;
            if (vy <= vz * tanTop) allT = 0;
            if (vy >= -vz * tanBot) allB = 0;
        }
        if ((allL | allR | allT | allB) != 0) return;
        statPolys++;
        // whole polygons, no subdivision: perspective-correct texturing and painter sorting
        emit(vq, nv, (sortFar ? zmax : zsum / nv) + bias, texId, layer, mode);
    }

    private final float[] clipA = new float[10 * VA], clipB = new float[10 * VA];
    private final float[] scr = new float[10 * VA];

    /** Near-clips a view-space polygon, projects it and links its triangles into the table. */
    private void emit(float[] v, int nv, float depth, int texId, int layer, int mode) {
        float[] in = v;
        int n = nv;
        boolean needClip = false;
        for (int k = 0; k < n; k++) if (in[k * VA + 2] < near) { needClip = true; break; }
        if (needClip) {
            int m = 0;
            for (int k = 0; k < n; k++) {
                int a = k * VA, b = ((k + 1) % n) * VA;
                float za = in[a + 2], zb = in[b + 2];
                boolean ina = za >= near, inb = zb >= near;
                if (ina) { System.arraycopy(in, a, clipA, m * VA, VA); m++; }
                if (ina != inb) {
                    float t = (near - za) / (zb - za);
                    for (int c = 0; c < VA; c++) clipA[m * VA + c] = in[a + c] + (in[b + c] - in[a + c]) * t;
                    m++;
                }
            }
            if (m < 3) return;
            in = clipA;
            n = m;
        }
        float fogK = 1f / (fogEnd - fogStart);
        int offL = 1, offR = 1, offT = 1, offB = 1;
        for (int k = 0; k < n; k++) {
            int o = k * VA;
            float z = in[o + 2];
            float iz = focal / z;
            float sx = (float) Math.floor(cx + in[o] * iz + 0.5f);
            float sy = (float) Math.floor(hy - in[o + 1] * iz + 0.5f);
            scr[o] = sx; scr[o + 1] = sy;
            scr[o + 2] = in[o + 3]; scr[o + 3] = in[o + 4]; scr[o + 4] = in[o + 5];
            float f = (z - fogStart) * fogK;
            scr[o + 5] = f < 0f ? 0f : (f > 1f ? 1f : f);
            scr[o + 6] = in[o + 6];
            scr[o + 7] = 1f / z;
            if (sx >= 0) offL = 0;
            if (sx <= W) offR = 0;
            if (sy >= 0) offT = 0;
            if (sy <= H) offB = 0;
        }
        if ((offL | offR | offT | offB) != 0) return;
        float orderDepth = Math.max(0f, depth);
        int bucket = (int) (orderDepth * BUCKET_SCALE);
        if (bucket < 0) bucket = 0;
        if (bucket >= BUCKETS) bucket = BUCKETS - 1;
        int hb = layer * BUCKETS + bucket;
        for (int k = 1; k < n - 1; k++) {
            if (primCount >= MAX_PRIMS) return;
            int p = primCount++;
            int o = p * 3 * VA;
            System.arraycopy(scr, 0, P, o, VA);
            System.arraycopy(scr, k * VA, P, o + VA, VA);
            System.arraycopy(scr, (k + 1) * VA, P, o + 2 * VA, VA);
            pdepth[p] = orderDepth;
            ptex[p] = texId;
            pmode[p] = mode;
            next[p] = head[hb];
            head[hb] = p;
        }
    }

    // ------------------------------------------------------------------ rasterizer

    private void drawTri(int p, int bandY0, int bandY1) {
        int o = p * 3 * VA;
        float[] A = P;
        // sort the three vertices by y
        int i0 = o, i1 = o + VA, i2 = o + 2 * VA, t;
        if (A[i1 + 1] < A[i0 + 1]) { t = i0; i0 = i1; i1 = t; }
        if (A[i2 + 1] < A[i0 + 1]) { t = i0; i0 = i2; i2 = t; }
        if (A[i2 + 1] < A[i1 + 1]) { t = i1; i1 = i2; i2 = t; }
        float x0 = A[i0], y0 = A[i0 + 1], x1 = A[i1], y1 = A[i1 + 1], x2 = A[i2], y2 = A[i2 + 1];
        if (y2 - y0 < 0.5f || y2 < bandY0 || y0 > bandY1) return;
        float dx1 = x1 - x0, dy1 = y1 - y0, dx2 = x2 - x0, dy2 = y2 - y0;
        float det = dx1 * dy2 - dx2 * dy1;
        if (det > -1e-4f && det < 1e-4f) return;
        float id = 1f / det;

        Texture tex = textures[ptex[p]];
        int[] tp = tex.px;
        int wm = tex.wMask, hm = tex.hMask, ws = tex.wShift, hmax = tex.hMax;
        // re-base uvs near zero so fixed point never overflows on large repeating polygons
        float u0 = A[i0 + 2], u1 = A[i1 + 2], u2 = A[i2 + 2];
        float v0 = A[i0 + 3], v1 = A[i1 + 3], v2 = A[i2 + 3];
        float ub = (float) Math.floor(Math.min(u0, Math.min(u1, u2)) / tex.w) * tex.w;
        float vb = (float) Math.floor(Math.min(v0, Math.min(v1, v2)) / tex.h) * tex.h;
        u0 -= ub; u1 -= ub; u2 -= ub; v0 -= vb; v1 -= vb; v2 -= vb;
        // perspective-correct texturing: u/z, v/z and 1/z are linear on screen
        float z0i = A[i0 + 7], z1i = A[i1 + 7], z2i = A[i2 + 7];
        float q0 = u0 * z0i, q1 = u1 * z1i, q2 = u2 * z2i;
        float r0 = v0 * z0i, r1 = v1 * z1i, r2 = v2 * z2i;
        float dzdx = ((z1i - z0i) * dy2 - (z2i - z0i) * dy1) * id, dzdy = ((z2i - z0i) * dx1 - (z1i - z0i) * dx2) * id;
        float dqdx = ((q1 - q0) * dy2 - (q2 - q0) * dy1) * id, dqdy = ((q2 - q0) * dx1 - (q1 - q0) * dx2) * id;
        float drdx = ((r1 - r0) * dy2 - (r2 - r0) * dy1) * id, drdy = ((r2 - r0) * dx1 - (r1 - r0) * dx2) * id;
        float l0 = A[i0 + 4], l1 = A[i1 + 4], l2 = A[i2 + 4];
        float f0 = A[i0 + 5], f1 = A[i1 + 5], f2 = A[i2 + 5];
        float a0 = A[i0 + 6], a1 = A[i1 + 6], a2 = A[i2 + 6];
        float dadx = ((a1 - a0) * dy2 - (a2 - a0) * dy1) * id, dady = ((a2 - a0) * dx1 - (a1 - a0) * dx2) * id;
        float dldx = ((l1 - l0) * dy2 - (l2 - l0) * dy1) * id, dldy = ((l2 - l0) * dx1 - (l1 - l0) * dx2) * id;
        float dfdx = ((f1 - f0) * dy2 - (f2 - f0) * dy1) * id, dfdy = ((f2 - f0) * dx1 - (f1 - f0) * dx2) * id;
        int dA = (int) (dadx * 65536f), dL = (int) (dldx * 65536f), dF = (int) (dfdx * 65536f);

        int yStart = (int) Math.ceil(y0 - 0.5f), yEnd = (int) Math.ceil(y2 - 0.5f);
        if (yStart < bandY0) yStart = bandY0;
        if (yEnd > bandY1) yEnd = bandY1;
        if (yStart >= yEnd) return;
        float invL = 1f / dy2;
        float invA = dy1 > 0f ? 1f / dy1 : 0f, invB = (y2 - y1) > 0f ? 1f / (y2 - y1) : 0f;
        int mode = pmode[p];
        boolean shadow = mode == MODE_SHADOW, alpha = mode == MODE_ALPHA, blend = mode == MODE_BLEND;
        // opaque spans overlap their right neighbour by a pixel: seals T-junction hairline cracks
        int seal = mode == MODE_OPAQUE ? 1 : 0;
        int fr = fogR, fg = fogG, fbb = fogB;
        int[] out = fb;
        float[] zb = groundZ;
        int width = W;

        for (int y = yStart; y < yEnd; y++) {
            float yc = y + 0.5f;
            float xa = x0 + dx2 * (yc - y0) * invL;
            float xb = yc < y1 ? x0 + dx1 * (yc - y0) * invA : x1 + (x2 - x1) * (yc - y1) * invB;
            float xl = xa < xb ? xa : xb, xr = xa < xb ? xb : xa;
            int xs = (int) Math.ceil(xl - 0.5f), xe = (int) Math.ceil(xr - 0.5f) + seal;
            if (xs < 0) xs = 0;
            if (xe > width) xe = width;
            if (xs >= xe) continue;
            float ox = xs + 0.5f - x0, oy = yc - y0;
            float IZ = z0i + dzdx * ox + dzdy * oy;
            float Q = q0 + dqdx * ox + dqdy * oy, R = r0 + drdx * ox + drdy * oy;
            int L = (int) ((l0 + dldx * ox + dldy * oy) * 65536f);
            int F = (int) ((f0 + dfdx * ox + dfdy * oy) * 65536f);
            int AL = (int) ((a0 + dadx * ox + dady * oy) * 65536f);
            int row = y * width;
            int db = (y & 3) << 2;
            float iz = IZ > 1e-6f ? IZ : 1e-6f;
            float uA = Q / iz, vA = R / iz;
            // exact texture coordinates every 16 pixels, linear in between
            for (int x = xs; x < xe; ) {
                int n = xe - x < 16 ? xe - x : 16;
                float IZn = IZ + dzdx * n, Qn = Q + dqdx * n, Rn = R + drdx * n;
                float izn = IZn > 1e-6f ? IZn : 1e-6f;
                float uB = Qn / izn, vB = Rn / izn;
                int U = (int) (uA * 65536f), V = (int) (vA * 65536f);
                int dU = (int) ((uB - uA) / n * 65536f), dV = (int) ((vB - vA) / n * 65536f);
                for (int e = x + n; x < e; x++, U += dU, V += dV, L += dL, F += dF, AL += dA, IZ += dzdx) {
                    int k = row + x;
                    if (IZ * GROUND_SLACK < zb[k]) continue;          // behind the ground (a hill)
                    if (shadow) {
                        int ty = V >> 16;
                        ty = ty < 0 ? 0 : (ty > hmax ? hmax : ty);
                        int c = tp[(ty << ws) | ((U >> 16) & wm)];
                        if ((c >>> 24) > (BAYER[db | (x & 3)] << 4) + 8) {
                            int d = out[k];
                            out[k] = 0xFF000000 | ((((d >> 16) & 255) * 9 >> 4) << 16)
                                    | ((((d >> 8) & 255) * 9 >> 4) << 8) | ((d & 255) * 10 >> 4);
                        }
                        continue;
                    }
                    int c;
                    // blended overlays: ordered-dither the coverage (stipple blend)
                    if (blend && (AL >> 12) <= BAYER[db | (x & 3)]) continue;
                    if (alpha) {   // foliage and cut-outs: clamp v (atlases may be non power-of-two tall)
                        int ty = V >> 16;
                        ty = ty < 0 ? 0 : (ty > hmax ? hmax : ty);
                        c = tp[(ty << ws) | ((U >> 16) & wm)];
                        if ((c >>> 24) < 128) continue;
                    } else {
                        c = tp[(((V >> 16) & hm) << ws) | ((U >> 16) & wm)];
                    }
                    int lv = L >> 8, fv = F >> 8;
                    if (lv < 0) lv = 0;
                    if (fv < 0) fv = 0; else if (fv > 256) fv = 256;
                    out[row + x] = shade(c, lv, fv, fr, fg, fbb, BAYER[db | (x & 3)] >> 1);
                }
                Q = Qn;
                R = Rn;
                uA = uB;
                vA = vB;
            }
        }
    }

    /**
     * Rain streaks over the finished frame (falling, slanted by the wind) and the lightning bolt
     * in the sky during thunderstorms. Call after {@link #flush}.
     */
    @Override public void weatherOverlay() {
        Environment e0 = env;
        if (e0 == null) return;
        int[] out = fb;
        if (e0.boltTime > 0f) {
            java.util.Random br = new java.util.Random(e0.boltSeed);
            float bx = W * (0.15f + br.nextFloat() * 0.7f);
            int yEnd = (int) Math.min(H - 1, hy + 4);
            for (int y = 0; y < yEnd; y++) {
                if ((y & 3) == 0) bx += (br.nextFloat() - 0.5f) * 6f;
                int x = (int) bx;
                for (int k = -1; k <= 1; k++) {
                    int xx = x + k;
                    if (xx < 0 || xx >= W || groundZ[y * W + xx] != 0f) continue;
                    out[y * W + xx] = k == 0 ? 0xFFF4F6FF : 0xFFB0B8E0;
                }
            }
        }
        float rain = e0.rain;
        if (rain <= 0.02f) return;
        int n = (int) (rain * 420f);
        float t = time;
        int slant = e0.windX > 0 ? 1 : -1;
        for (int i = 0; i < n; i++) {
            int h1 = com.harlequinxd.drivehard.core.tex.Noise.hash(i, 1, 5), h2 = com.harlequinxd.drivehard.core.tex.Noise.hash(i, 2, 5);
            float speed = 260f + (h2 & 127);
            int len = 4 + (h1 >>> 28);
            int y0 = (int) (((h2 >>> 8) & 1023) + t * speed) % (H + 24) - 12;
            int x0 = ((h1 & 0xFFFF) * W) >> 16;
            x0 = (int) ((x0 - slant * t * 40f) % W);
            if (x0 < 0) x0 += W;
            for (int j = 0; j < len; j++) {
                int y = y0 + j, x = x0 + slant * ((y0 + j) / 4) % 3;
                if (y < 0 || y >= H || x < 0 || x >= W) continue;
                int k = y * W + x, d = out[k];
                int r = (((d >> 16) & 255) * 5 + 200 * 3) >> 3, g = (((d >> 8) & 255) * 5 + 208 * 3) >> 3, b = ((d & 255) * 5 + 222 * 3) >> 3;
                out[k] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    /** Lights (with the scene light), fogs and reduces a texel to 15-bit colour with ordered dither. */
    private int shade(int c, int lv, int fv, int fr, int fg, int fbb, int d) {
        int r = (((c >> 16) & 255) * lv * tR) >> 16;
        int g = (((c >> 8) & 255) * lv * tG) >> 16;
        int b = ((c & 255) * lv * tB) >> 16;
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        r += ((fr - r) * fv) >> 8;
        g += ((fg - g) * fv) >> 8;
        b += ((fbb - b) * fv) >> 8;
        r = (r + d) >> 3; if (r > 31) r = 31;
        g = (g + d) >> 3; if (g > 31) g = 31;
        b = (b + d) >> 3; if (b > 31) b = 31;
        return 0xFF000000 | (((r << 3) | (r >> 2)) << 16) | (((g << 3) | (g >> 2)) << 8) | ((b << 3) | (b >> 2));
    }

    /** As {@link #shade} without the scene light (light sources, pre-lit colours). */
    private static int shadeRaw(int c, int lv, int fv, int fr, int fg, int fbb, int d) {
        int r = (((c >> 16) & 255) * lv) >> 8;
        int g = (((c >> 8) & 255) * lv) >> 8;
        int b = ((c & 255) * lv) >> 8;
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        r += ((fr - r) * fv) >> 8;
        g += ((fg - g) * fv) >> 8;
        b += ((fbb - b) * fv) >> 8;
        r = (r + d) >> 3; if (r > 31) r = 31;
        g = (g + d) >> 3; if (g > 31) g = 31;
        b = (b + d) >> 3; if (b > 31) b = 31;
        return 0xFF000000 | (((r << 3) | (r >> 2)) << 16) | (((g << 3) | (g >> 2)) << 8) | ((b << 3) | (b >> 2));
    }

    /**
     * Sprites are real sprites: an upright billboard facing the camera is parallel to the screen
     * (the camera never pitches), so it is a scaled rectangle blit sorted with the polygons.
     */
    private void drawSprite(int p, int bandY0, int bandY1) {
        int o = p * 3 * VA;
        float[] A = P;
        float sx0 = A[o], sy0 = A[o + 1], sx1 = A[o + 2], sy1 = A[o + 3];
        float u0 = A[o + 4], v0 = A[o + 5], u1 = A[o + 6], v1 = A[o + 7];
        int fv = (int) (A[o + 8] * 256f);
        float test = A[o + 9] * GROUND_SLACK;            // 1/depth of the sprite
        float[] zb = groundZ;
        int lv = (int) (A[o + 10] * 256f);
        int ya = Math.max(bandY0, (int) Math.ceil(sy0 - 0.5f)), yb = Math.min(bandY1, (int) Math.ceil(sy1 - 0.5f));
        int xa = Math.max(0, (int) Math.ceil(sx0 - 0.5f)), xb = Math.min(W, (int) Math.ceil(sx1 - 0.5f));
        if (ya >= yb || xa >= xb) return;
        Texture tex = textures[ptex[p]];
        int[] tp = tex.px;
        int wm = tex.wMask, ws = tex.wShift, hmax = tex.hMax;
        float du = (u1 - u0) / (sx1 - sx0), dv = (v1 - v0) / (sy1 - sy0);
        int dU = (int) (du * 65536f);
        int U0 = (int) ((u0 + (xa + 0.5f - sx0) * du) * 65536f);
        int[] out = fb;
        int fr = fogR, fg = fogG, fbb = fogB;
        for (int y = ya; y < yb; y++) {
            int ty = (int) (v0 + (y + 0.5f - sy0) * dv);
            ty = ty < 0 ? 0 : (ty > hmax ? hmax : ty);
            int trow = ty << ws, row = y * W, db = (y & 3) << 2;
            int U = U0;
            for (int x = xa; x < xb; x++, U += dU) {
                if (test < zb[row + x]) continue;             // behind a hill
                int c = tp[trow | ((U >> 16) & wm)];
                if ((c >>> 24) < 128) continue;
                out[row + x] = shade(c, lv, fv, fr, fg, fbb, BAYER[db | (x & 3)] >> 1);
            }
        }
    }
}
