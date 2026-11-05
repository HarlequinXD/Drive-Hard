package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Ground;
import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.RoadNode;
import com.harlequinxd.drivehard.core.tex.Img;
import com.harlequinxd.drivehard.core.tex.Noise;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.Arrays;

/**
 * Paints one 64 m tile of the Mode 7 ground map, top down, in layers:
 * <ol>
 * <li>ground: grass, hill grass, sand, rock and crop fields (paddy levees included), melting into
 *   each other along irregular noisy borders;</li>
 * <li>sea and shoreline from the height map;</li>
 * <li>farm yards and car parks;</li>
 * <li>every road, traced from its spline: lanes and markings, junction surfaces, stop lines and
 *   zebra crossings, with a dusty shoulder fading into the grass;</li>
 * <li>lighting from the height map's slopes, plus a soft large-scale tint.</li>
 * </ol>
 */
final class GroundPainter {
    private static final int RES = Ground.RES, HN = Ground.HCELLS + 1;
    private static final float TEXEL = Ground.TEXEL, VERGE = 1.4f;

    /** What covers one 8 m cell: texture (-1 = city), metres per texture tile, crop. */
    static final class Surface {
        int tex = -1;
        float uv = 2f;
        Countryside.Crop crop = Countryside.Crop.NONE;
        /** Farmland may reach into this cell (a paddock edge runs through it): resolve per texel. */
        boolean farm;
    }

    private final City city;
    private final TextureLibrary lib;
    private final RoadNetwork roads;
    private final Terrain terrain;

    GroundPainter(City city) {
        this.city = city;
        this.lib = city.lib;
        this.roads = city.roads;
        this.terrain = city.terrain;
    }

    private Texture tex(int id) { return lib.list.get(id); }

    /** Box-filtered copies of library textures at the map's texel size (no moire). */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Texture> FILTERED = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int RECENT = 8;
    private final int[] recentId = {-1, -1, -1, -1, -1, -1, -1, -1};
    private final float[] recentU = new float[RECENT], recentV = new float[RECENT];
    private final Texture[] recentT = new Texture[RECENT];
    private int recentNext;

    /** Colour of library texture {@code id} tiled every {@code m} metres, at world (x, z), filtered to the texel size. */
    private int world(int id, float m, float x, float z) {
        return world(id, m, m, x, z, 0f, 0f);
    }

    private int world(int id, float mu, float mv, float x, float z, float ox, float oz) {
        // a few recently used filtered textures, checked without hashing (this runs per texel)
        for (int k = 0; k < RECENT; k++)
            if (recentId[k] == id && recentU[k] == mu && recentV[k] == mv) return sample(recentT[k], (x - ox) / mu, (z - oz) / mv);
        long key = ((long) id << 32) | (Float.floatToIntBits(mu) * 31L + Float.floatToIntBits(mv)) & 0xFFFFFFFFL;
        Texture f = FILTERED.get(key);
        if (f == null) {
            Texture t = tex(id);
            int fu = 1, fv = 1;
            while (fu * 2 <= t.w && mu / t.w * fu * 2 <= TEXEL * 1.5f) fu *= 2;
            while (fv * 2 <= t.h && mv / t.h * fv * 2 <= TEXEL * 1.5f) fv *= 2;
            int w = t.w / fu, h = Math.max(1, t.h / fv);
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x2 = 0; x2 < w; x2++) {
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int j = 0; j < fv; j++)
                        for (int i = 0; i < fu; i++) {
                            int c = t.px[((y * fv + j) << t.wShift) | (x2 * fu + i)];
                            r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
                        }
                    px[y * w + x2] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
                }
            f = new Texture(w, h, px);
            FILTERED.put(key, f);
        }
        int slot = recentNext++ & (RECENT - 1);
        recentId[slot] = id; recentU[slot] = mu; recentV[slot] = mv; recentT[slot] = f;
        return sample(f, (x - ox) / mu, (z - oz) / mv);
    }

    /** Samples a u/v-mapped texture (lane, mouth) averaged over a texel's footprint across the road. */
    private static int sampleAcross(Texture t, float u, float v, float du) {
        int r = 0, g = 0, b = 0;
        for (int k = 0; k < 4; k++) {
            float uu = Math.max(0f, Math.min(0.999f, u + du * ((k + 0.5f) / 4f - 0.5f)));
            int c = sample(t, uu, v);
            r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
        }
        return 0xFF000000 | ((r >> 2) << 16) | ((g >> 2) << 8) | (b >> 2);
    }

    private static int sample(Texture t, float u, float v) {
        int x = (int) Math.floor(u * t.w) & t.wMask, y = (int) Math.floor(v * t.h) & t.hMask;
        return t.px[(y << t.wShift) | x];
    }

    /** Smooth value noise in 0..1. */
    private static float vn(float x, float z, int s) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        float a = Noise.rnd(ix, iz, s), b = Noise.rnd(ix + 1, iz, s), c = Noise.rnd(ix, iz + 1, s), d = Noise.rnd(ix + 1, iz + 1, s);
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    private static final int JS = 8;

    private static float bilerpN(float[] g, float gx, float gz) {
        int n = RES / JS + 1;
        int ix = Math.min(n - 2, (int) gx), iz = Math.min(n - 2, (int) gz);
        float tx = gx - ix, tz = gz - iz;
        int o = iz * n + ix;
        float top = g[o] + (g[o + 1] - g[o]) * tx, bot = g[o + n] + (g[o + n + 1] - g[o + n]) * tx;
        return top + (bot - top) * tz;
    }

    private static float bilerp(float[] g, float gx, float gz) {
        int ix = Math.min(HN - 2, (int) gx), iz = Math.min(HN - 2, (int) gz);
        float tx = gx - ix, tz = gz - iz;
        int o = iz * HN + ix;
        float top = g[o] + (g[o + 1] - g[o]) * tx, bot = g[o + HN] + (g[o + HN + 1] - g[o + HN]) * tx;
        return top + (bot - top) * tz;
    }

    /**
     * @param grid surfaces of the tile's 8x8 cells plus a one-cell border (10x10)
     */
    Ground.Tile paint(int cx, int cz, Surface[] grid) {
        // cells next to farmed cells may hold part of a paddock too
        for (int j = 0; j < 10; j++)
            for (int i = 0; i < 10; i++) {
                Surface g = grid[j * 10 + i];
                if (g == null) continue;
                boolean f = g.crop != Countryside.Crop.NONE;
                for (int dj = -1; dj <= 1 && !f; dj++)
                    for (int di = -1; di <= 1 && !f; di++) {
                        int ii = i + di, jj = j + dj;
                        if (ii < 0 || jj < 0 || ii > 9 || jj > 9) continue;
                        Surface o = grid[jj * 10 + ii];
                        f = o != null && o.crop != Countryside.Crop.NONE;
                    }
                g.farm = f && g.tex != lib.sand && g.tex != lib.rock;
            }
        float x0 = cx * Ground.TILE, z0 = cz * Ground.TILE;
        int gx0 = cx * Ground.HCELLS, gz0 = cz * Ground.HCELLS;
        // ---- height map (with a border for slopes) and per-sample lighting
        float[] hb = new float[(HN + 2) * (HN + 2)];
        for (int j = -1; j <= HN; j++)
            for (int i = -1; i <= HN; i++) hb[(j + 1) * (HN + 2) + i + 1] = terrain.grid(gx0 + i, gz0 + j);
        float[] h = new float[HN * HN], light = new float[HN * HN];
        for (int j = 0; j < HN; j++)
            for (int i = 0; i < HN; i++) {
                int o = (j + 1) * (HN + 2) + i + 1;
                h[j * HN + i] = hb[o];
                float nx = -(hb[o + 1] - hb[o - 1]) / (2f * Ground.HCELL), nz = -(hb[o + HN + 2] - hb[o - HN - 2]) / (2f * Ground.HCELL);
                float il = 1f / (float) Math.sqrt(nx * nx + 1f + nz * nz);
                light[j * HN + i] = GeoBuilder.sunLight(nx * il, il, nz * il)
                        * (0.86f + 0.26f * GeoBuilder.paint(x0 + i * Ground.HCELL, z0 + j * Ground.HCELL));
            }
        int[] out = new int[RES * RES];
        // ---- ground layer: irregular borders between cell types, softened where two types meet
        Texture asphalt = tex(lib.asphalt), water = tex(lib.water), sand = tex(lib.sand);
        // border jitter on a coarse lattice (smooth noise doesn't need evaluating per texel)
        int jn = RES / JS + 1;
        float[] jitX = new float[jn * jn], jitZ = new float[jn * jn];
        for (int j = 0; j < jn; j++)
            for (int i = 0; i < jn; i++) {
                float x = x0 + i * JS * TEXEL, z = z0 + j * JS * TEXEL;
                jitX[j * jn + i] = (vn(x / 3.1f, z / 3.1f, 31) - 0.5f) * 6f;
                jitZ[j * jn + i] = (vn(x / 3.1f, z / 3.1f, 32) - 0.5f) * 6f;
            }
        for (int py = 0; py < RES; py++) {
            float z = z0 + (py + 0.5f) * TEXEL;
            for (int px = 0; px < RES; px++) {
                float x = x0 + (px + 0.5f) * TEXEL;
                float th = bilerp(h, (px + 0.5f) * TEXEL / Ground.HCELL, (py + 0.5f) * TEXEL / Ground.HCELL);
                int c;
                if (th < 0.05f) {
                    // sea: shallows lighter, deeper water darker
                    c = world(lib.water, 4f, x, z);
                    float depth = Math.min(1f, -th / 4f);
                    // near the shore the sand runs on under the water: the renderer animates the sea over it
                    c = th > -0.6f ? world(lib.sand, 2f, x, z)
                            : th > -1.2f ? Img.mix(world(lib.sand, 2f, x, z), c, (-0.6f - th) / 0.6f) : Img.mix(c, 0xFF1C4A6E, depth * 0.5f);
                } else {
                    Surface b = cell(grid, x - x0, z - z0);
                    if (city.urban(x, z))
                        c = world(lib.asphalt, 2f, x, z);      // city: streets (blocks stand on it)
                    else {
                        float gx = px * (1f / JS), gz = py * (1f / JS);
                        float jx = bilerpN(jitX, gx, gz), jz = bilerpN(jitZ, gx, gz);
                        Surface a = cell(grid, x + jx - x0, z + jz - z0);
                        if (a == null || a.tex < 0) a = b;
                        c = groundColor(a, x, z);
                        if (b.tex != a.tex) c = Img.mix(c, groundColor(b, x, z), 0.35f);
                    }
                }
                out[py * RES + px] = c;
            }
        }
        // ---- farm yards and car parks
        city.countryside.yards(x0, z0, x0 + Ground.TILE, z0 + Ground.TILE, (rx0, rz0, rx1, rz1, t) -> {
            Texture tx = tex(t);
            int ax = Math.max(0, (int) ((rx0 - x0) / TEXEL)), bx = Math.min(RES, (int) Math.ceil((rx1 - x0) / TEXEL));
            int az = Math.max(0, (int) ((rz0 - z0) / TEXEL)), bz = Math.min(RES, (int) Math.ceil((rz1 - z0) / TEXEL));
            for (int py = az; py < bz; py++)
                for (int px = ax; px < bx; px++) {
                    float x = x0 + (px + 0.5f) * TEXEL, z = z0 + (py + 0.5f) * TEXEL;
                    // ragged edge where the gravel meets the grass
                    float edge = Math.min(Math.min(x - rx0, rx1 - x), Math.min(z - rz0, rz1 - z));
                    if (edge < 0.6f && Noise.rnd((int) (x * 4), (int) (z * 4), 9) > edge / 0.6f) continue;
                    out[py * RES + px] = world(t, 2f, x, z);
                }
        });
        // ---- city ground: sidewalks, lots, parks, plazas, car parks, medians
        java.util.List<float[]> areas = new java.util.ArrayList<>();
        city.groundPaint(x0, z0, x0 + Ground.TILE, z0 + Ground.TILE, areas);
        for (float[] r : areas) {
            Texture tx = tex((int) r[4]);
            int ax = Math.max(0, Math.round((r[0] - x0) / TEXEL)), bx = Math.min(RES, Math.round((r[2] - x0) / TEXEL));
            int az = Math.max(0, Math.round((r[1] - z0) / TEXEL)), bz = Math.min(RES, Math.round((r[3] - z0) / TEXEL));
            for (int py = az; py < bz; py++)
                for (int px = ax; px < bx; px++) {
                    float x = x0 + (px + 0.5f) * TEXEL, z = z0 + (py + 0.5f) * TEXEL;
                    out[py * RES + px] = world((int) r[4], r[5], r[6], x, z, r[0], r[1]);
                }
        }
        // ---- roads
        paintRoads(out, cx, cz, x0, z0);
        // ---- lighting
        for (int py = 0; py < RES; py++)
            for (int px = 0; px < RES; px++) {
                float l = bilerp(light, (px + 0.5f) * TEXEL / Ground.HCELL, (py + 0.5f) * TEXEL / Ground.HCELL);
                int c = out[py * RES + px];
                int r = Math.min(255, (int) (((c >> 16) & 255) * l)), g = Math.min(255, (int) (((c >> 8) & 255) * l)), b = Math.min(255, (int) ((c & 255) * l));
                out[py * RES + px] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        Ground.Tile tile = new Ground.Tile(cx, cz, out);
        // sea and beach for the animated water: the real (unclamped) ground height per metre
        float hmin = Float.MAX_VALUE;
        for (float v : h) hmin = Math.min(hmin, v);
        if (hmin < 0.6f) {
            // samples on the 1 m lattice corners (WRES+1 per side), so neighbouring tiles share their edges
            int wn = Ground.WRES + 1;
            byte[] wm = new byte[wn * wn];
            float cellW = Ground.TILE / Ground.WRES;
            boolean any = false;
            for (int j = 0; j < wn; j++)
                for (int i = 0; i < wn; i++) {
                    float wx = x0 + i * cellW, wz = z0 + j * cellW;
                    // the true terrain (not the 8 m grid) plus a little ripple in the sand: a natural waterline
                    float th = terrain.natural(wx, wz) + (vn(wx / 4.3f, wz / 4.3f, 77) - 0.5f) * 0.07f
                            + (vn(wx / 1.7f, wz / 1.7f, 78) - 0.5f) * 0.025f;
                    int code = Ground.Tile.waterCode(th);
                    wm[j * wn + i] = (byte) code;
                    any |= code != 0;
                }
            if (any) tile.water = wm;
        }
        // the sea is drawn as a flat surface at sea level
        for (int k = 0; k < h.length; k++) tile.h[k] = Math.max(h[k], -0.05f);
        return tile;
    }

    /** Flat colour for the far distance where no tile is loaded yet. */
    static int farColor(Terrain terrain, float x, float z) {
        float h = terrain.height(x, z);
        if (h < 0.05f) return 0xFF3F7290;
        if (terrain.isSand(x, z, h)) return 0xFFC8B98E;
        return h > 22f ? 0xFF4E6E38 : 0xFF557A3A;
    }

    private static Surface cell(Surface[] grid, float lx, float lz) {
        int i = (int) Math.floor(lx / 8f) + 1, j = (int) Math.floor(lz / 8f) + 1;
        if (i < 0) i = 0; else if (i > 9) i = 9;
        if (j < 0) j = 0; else if (j > 9) j = 9;
        return grid[j * 10 + i];
    }

    private final Countryside.Parcel parcel = new Countryside.Parcel();
    private final java.util.HashMap<Long, Countryside.Crop> paddockUse = new java.util.HashMap<>();

    /** Crop of the paddock under (x, z), cached per tile; NONE if it isn't farmed. */
    private Countryside.Crop farmAt(float x, float z) {
        city.countryside.parcel(x, z, parcel);
        if (parcel.seam) return null;                       // hedgerow
        Countryside.Crop c = paddockUse.get(parcel.key);
        if (c == null) { c = city.countryside.use(parcel); paddockUse.put(parcel.key, c); }
        return c;
    }

    private int groundColor(Surface s, float x, float z) {
        if (s.farm) {
            // paddocks lined up with the road, rows lined up with the paddock
            Countryside.Crop c = farmAt(x, z);
            if (c == Countryside.Crop.NONE) return s.crop != Countryside.Crop.NONE || s.tex < 0 ? world(lib.grass, 2f, x, z) : world(s.tex, s.uv, x, z);
            if (c == null) c = Countryside.Crop.NONE;
            if (c == Countryside.Crop.NONE) return world(lib.hillGrass, 2f, x, z);           // hedgerow between farm districts
            if (parcel.edge < 1.1f) return world(lib.grass, 2f, x, z);                       // grass margin round the paddock
            if (Countryside.paddy(c) && parcel.inner < 0.4f) return world(lib.grass, 2f, x, z);   // levees between paddies
            if ((c == Countryside.Crop.CABBAGE || c == Countryside.Crop.SOIL) && parcel.inner < 0.6f) return world(lib.grass, 2f, x, z);
            boolean rows = c != Countryside.Crop.PASTURE && c != Countryside.Crop.ORCHARD && c != Countryside.Crop.FALLOW;
            return world(city.countryside.texture(c), rows ? 8f : 2f, parcel.a, parcel.b);
        }
        return world(s.tex, s.uv, x, z);
    }

    // ------------------------------------------------------------------ roads

    private float[] dist;
    private int[] edgeAt;
    private float[] sAt, offAt;
    private boolean[] goreAt;

    private void paintRoads(int[] out, int cx, int cz, float x0, float z0) {
        int n = RES * RES;
        dist = new float[n];
        Arrays.fill(dist, Float.MAX_VALUE);
        edgeAt = new int[n];
        sAt = new float[n];
        offAt = new float[n];
        goreAt = new boolean[n];
        float m = 14f;
        roads.forEachPiece(x0 - m, z0 - m, x0 + Ground.TILE + m, z0 + Ground.TILE + m, (e, i) -> piece(e, i, x0, z0));
        // junction surfaces
        boolean[] junction = new boolean[n];
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                for (RoadNode nd : roads.nodesInChunk(cx + dx, cz + dz)) {
                    if (nd.ring == null) continue;
                    float r = nd.ringRadius + 1f;
                    int ax = Math.max(0, (int) ((nd.x - r - x0) / TEXEL)), bx = Math.min(RES, (int) Math.ceil((nd.x + r - x0) / TEXEL));
                    int az = Math.max(0, (int) ((nd.z - r - z0) / TEXEL)), bz = Math.min(RES, (int) Math.ceil((nd.z + r - z0) / TEXEL));
                    for (int py = az; py < bz; py++)
                        for (int px = ax; px < bx; px++) {
                            float x = x0 + (px + 0.5f) * TEXEL, z = z0 + (py + 0.5f) * TEXEL;
                            if (inside(nd.ring, x, z)) { junction[py * RES + px] = true; dist[py * RES + px] = -1f; }
                        }
                }
        Texture asphalt = tex(lib.asphalt);
        for (int py = 0; py < RES; py++)
            for (int px = 0; px < RES; px++) {
                int k = py * RES + px;
                float d = dist[k];
                if (d > VERGE) continue;
                float x = x0 + (px + 0.5f) * TEXEL, z = z0 + (py + 0.5f) * TEXEL;
                if (junction[k]) { out[k] = world(lib.asphalt, 2f, x, z); continue; }
                RoadEdge e = roads.edges.get(edgeAt[k]);
                if (d > 0f && e.cls.city) continue;        // city streets end at the kerb
                int road = roadColor(e, sAt[k], offAt[k], goreAt[k], x, z);
                if (d <= 0f) out[k] = road;
                else if (e.surface != com.harlequinxd.drivehard.core.road.RoadEdge.PAVED) {
                    // unsealed tracks fade straight into the grass
                    float t = d / VERGE;
                    if (t < 0.5f) out[k] = Img.mix(road, out[k], 0.5f + t);
                } else if (e.cls == com.harlequinxd.drivehard.core.road.RoadClass.TOWN) {
                    // town footpaths end at a neat concrete kerb, then the lots
                    if (d < 0.35f) out[k] = Img.mix(0xFFB4B0A8, 0xFF8E8A84, d / 0.35f);
                } else {
                    // soft verge: a dusty shoulder fading into the ground
                    float t = d / VERGE;
                    out[k] = Img.mix(Img.mix(road, 0xFF8C8270, 0.5f), out[k], t * t);
                }
            }
        dist = null;
        edgeAt = null;
        sAt = offAt = null;
        goreAt = null;
    }

    /** Records the nearest road piece for every texel near this piece. */
    private void piece(RoadEdge e, int i, float x0, float z0) {
        if (e.bridge[i]) return;      // bridge decks are real geometry
        float hw = e.cls.halfWidth();
        float ax = e.x[i], az = e.z[i], bx = e.x[i + 1], bz = e.z[i + 1];
        float vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
        if (l2 < 1e-6f) return;
        float len = (float) Math.sqrt(l2), tx = vx / len, tz = vz / len;
        float reach = hw + VERGE + 0.5f;
        int px0 = Math.max(0, (int) ((Math.min(ax, bx) - reach - x0) / TEXEL)), px1 = Math.min(RES, (int) Math.ceil((Math.max(ax, bx) + reach - x0) / TEXEL));
        int pz0 = Math.max(0, (int) ((Math.min(az, bz) - reach - z0) / TEXEL)), pz1 = Math.min(RES, (int) Math.ceil((Math.max(az, bz) + reach - z0) / TEXEL));
        if (px0 >= px1 || pz0 >= pz1) return;
        // slip road still merged with the freeway (the taper / gore): plain asphalt
        boolean gore = e.cls == RoadClass.RAMP
                && roads.classDistance((ax + bx) * 0.5f, (az + bz) * 0.5f, RoadClass.FREEWAY, 60f) < RoadClass.FREEWAY.halfWidth() + hw - 0.5f;
        for (int py = pz0; py < pz1; py++) {
            float z = z0 + (py + 0.5f) * TEXEL;
            for (int px = px0; px < px1; px++) {
                float x = x0 + (px + 0.5f) * TEXEL;
                float t = ((x - ax) * vx + (z - az) * vz) / l2;
                float tc = t < 0f ? 0f : (t > 1f ? 1f : t);
                float qx = ax + vx * tc - x, qz = az + vz * tc - z;
                float d = (float) Math.sqrt(qx * qx + qz * qz) - hw;
                int k = py * RES + px;
                if (d >= dist[k]) continue;
                dist[k] = d;
                edgeAt[k] = e.id;
                sAt[k] = e.s[i] + tc * len;
                offAt[k] = (x - ax) * tz - (z - az) * tx;   // left of a→b positive, as the road mesh
                goreAt[k] = gore;
            }
        }
    }

    private static boolean inside(float[] r, float x, float z) {
        int m = r.length / 3;
        boolean in = false;
        for (int i = 0, j = m - 1; i < m; j = i++) {
            float xi = r[i * 3], zi = r[i * 3 + 2], xj = r[j * 3], zj = r[j * 3 + 2];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    /** Asphalt colour of a road texel: lane markings, mouths with stop lines, plain junction areas. */
    private int roadColor(RoadEdge e, float s, float off, boolean gore, float x, float z) {
        RoadNode na = roads.nodes.get(e.a), nb = roads.nodes.get(e.b);
        Texture asphalt = tex(lib.asphalt);
        int plain = world(lib.asphalt, 2f, x, z);
        float trimA = na.ring != null ? e.clearA : 0f, trimB = nb.ring != null ? e.clearB : 0f;
        if (gore || s < trimA || s > e.len - trimB) return plain;
        float hw = e.cls.halfWidth();
        if (e.surface != com.harlequinxd.drivehard.core.road.RoadEdge.PAVED) {
            // unsealed track: ruts across, no markings
            int t = e.surface == com.harlequinxd.drivehard.core.road.RoadEdge.DIRT ? lib.dirtTrack : lib.gravelTrack;
            float u = Math.max(0f, Math.min(0.999f, (off + hw) / (2f * hw)));
            return sampleAcross(tex(t), u, (s % 20f) / 20f, TEXEL / (2f * hw));
        }
        float ma = roads.mouth(e, na), mb = roads.mouth(e, nb);
        boolean atA = ma > 0f && s < trimA + ma, atB = mb > 0f && s > e.len - trimB - mb;
        if (atA || atB) {
            RoadNode nd = atA ? na : nb;
            if (nd.kind != RoadNode.Kind.CITY && !givesWay(e, nd)) return plain;
            float mlen = atA ? ma : mb, start = atA ? trimA : e.len - trimB;
            float v = 1f - Math.abs(s - start) / mlen;
            float u = (off + hw) / (2f * hw);
            if (atA) u = 1f - u;
            return sampleAcross(tex(mouthTex(e.cls)), Math.max(0f, Math.min(0.999f, u)), Math.max(0f, Math.min(0.999f, v)), TEXEL / (2f * hw));
        }
        float ao = Math.abs(off);
        float v = (s % 20f) / 20f;
        float[][] bs = bandCache[e.cls.ordinal()];
        if (bs == null) bs = bandCache[e.cls.ordinal()] = bands(e.cls);
        for (float[] bd : bs) {
            if (bd[3] > 0f) {
                if (ao > bd[1]) continue;
                return bd[2] < 0f ? plain : sampleAcross(tex((int) bd[2]), (off + bd[1]) / (2f * bd[1]), v, TEXEL / (2f * bd[1]));
            }
            if (ao >= bd[0] && ao <= bd[1])
                return bd[2] < 0f ? plain : sampleAcross(tex((int) bd[2]), Math.min(0.999f, (ao - bd[0]) / (bd[1] - bd[0])), v, TEXEL / (bd[1] - bd[0]));
        }
        return plain;
    }

    private final float[][][] bandCache = new float[RoadClass.values().length][][];

    private boolean givesWay(RoadEdge e, RoadNode n) {
        if (n.kind != RoadNode.Kind.JUNCTION) return false;
        int best = 0;
        for (int id : n.edges) if (id != e.id) best = Math.max(best, roads.edges.get(id).cls.priority);
        return best > e.cls.priority;
    }

    private int mouthTex(RoadClass c) {
        switch (c) {
            case STREET: return lib.mouthStreet;
            case AVENUE: return lib.mouthAvenue;
            case HIGHWAY: return lib.mouthHighway;
            case LANE: return lib.mouthLane;
            case TOWN: return lib.mouthTown;
            default: return lib.mouthCountry;
        }
    }

    /** Cross-section bands {centre-side offset, kerb-side offset, texture (-1 plain), symmetric}. */
    private float[][] bands(RoadClass c) {
        float hw = c.halfWidth();
        switch (c) {
            case STREET:
                return new float[][]{{0f, 0.25f, lib.laneHwMedian, 1}, {0.25f, 3.75f, lib.laneStreet, 0}, {3.75f, hw, -1, 0}};
            case AVENUE:
                return new float[][]{{0f, 1.3f, -1, 1}, {1.3f, 4.8f, lib.laneAvInner, 0}, {4.8f, 8.3f, lib.laneAvOuter, 0}, {8.3f, hw, -1, 0}};
            case COUNTRY:
                return new float[][]{{0f, 3.1f, lib.laneCountry, 0}, {3.1f, hw, -1, 0}};
            case HIGHWAY:
                return new float[][]{{0f, 0.3f, lib.laneHwMedian, 1}, {0.3f, 3.55f, lib.laneHwInner, 0},
                        {3.55f, 6.8f, lib.laneHwOuter, 0}, {6.8f, hw, -1, 0}};
            case FREEWAY:
                return new float[][]{{0f, 1.4f, -1, 1}, {1.4f, 4.9f, lib.laneFwInner, 0},
                        {4.9f, 8.4f, lib.laneFwOuter, 0}, {8.4f, hw, -1, 0}};
            case LANE:
                return new float[][]{{0f, hw, -1, 1}};
            case TOWN:
                return new float[][]{{0f, 2.75f, lib.laneTown, 0}, {2.75f, hw, lib.walkStrip, 0}};
            default:   // RAMP
                return new float[][]{{0f, 1.875f, lib.laneRamp, 1}, {1.875f, hw, -1, 0}};
        }
    }
}
