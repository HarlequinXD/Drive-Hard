package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.tex.Noise;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Island heightfield (pure function of position once built, so any chunk can be generated on
 * its own).
 *
 * <p>The outline is built the way real volcanic-arc islands look: a chain of overlapping lobes
 * along a gently bent main axis (the island is ~100 km long and 40-55 km wide), a few
 * peninsulas, all pushed around by layered domain warping so the coast gets bays, headlands
 * and coves at every scale. Only the largest landmass is kept and enclosed water is filled.
 * A signed distance field to the coast (250 m grid, interpolated, with fine noise) drives the
 * rest.</p>
 *
 * <p>Coasts come in three kinds along their length: sandy beaches with a gentle profile, rocky
 * shores, and cliffs where high ground drops straight into deep water. Inland the land rises
 * through coastal plains and rolling hill country to mountain ranges in the interior. The city
 * sits on a straight stretch of the east coast on a gently rolling plateau. Roads are cut into
 * the terrain: {@link #height} follows each road's smoothed profile.</p>
 */
public final class Terrain implements java.io.Serializable {
    public static final float SAND_LEVEL = 2.2f;
    /** Side of the square the island is generated in (metres). */
    public final float islandSize;
    /** Coast distance field resolution: one sample every CELL metres. */
    private static final float CELL = 250f;
    private final int N;

    private final int seed;
    private final float cx0, cz0, cx1, cz1;   // city rectangle
    private final float czc;
    /** Extent of the generated area (the island lies inside it with sea all round). */
    public final float islandX0, islandX1, islandZ0, islandZ1;
    /** Set once the road network is built; null while it is being generated. */
    public RoadNetwork roads;
    /** Signed distance to the coast per grid cell: positive in the sea, negative inland (metres). */
    private final float[] sdf;
    /** Every city's grid rectangle {x0, z0, x1, z1}; the first is the main city on the east coast. */
    private float[][] cities = new float[0][];
    /** Island centre and main axis (for placing things around the coast). */
    public final float centreX, centreZ;

    public Terrain(int seed, float cityX0, float cityZ0, float cityX1, float cityZ1) {
        this(seed, 128000f, cityX0, cityZ0, cityX1, cityZ1);
    }

    /**
     * @param islandSize side of the square the island is made in; the island's shape is scaled
     *                   to it (a 32 km island is a quarter the length of a 128 km one)
     */
    public Terrain(int seed, float islandSize, float cityX0, float cityZ0, float cityX1, float cityZ1) {
        this.seed = seed;
        this.islandSize = islandSize;
        this.N = Math.round(islandSize / CELL);
        this.sdf = new float[N * N];
        cities = new float[][]{{cityX0, cityZ0, cityX1, cityZ1}};
        this.cx0 = cityX0;
        this.cz0 = cityZ0;
        this.cx1 = cityX1;
        this.cz1 = cityZ1;
        this.czc = (cityZ0 + cityZ1) * 0.5f;
        Shape shape = new Shape(seed, islandSize / 128000f);
        // place the island so its east coast runs past the city
        float t0 = shape.eastCoast();
        float ox = cityX1 + 60f - t0, oz = czc;
        centreX = ox;
        centreZ = oz;
        islandX0 = ox - islandSize * 0.5f;
        islandX1 = ox + islandSize * 0.5f;
        islandZ0 = oz - islandSize * 0.5f;
        islandZ1 = oz + islandSize * 0.5f;
        buildSdf(shape, ox, oz);
    }

    // ------------------------------------------------------------------ outline

    /** The island outline in its own frame (origin at the island centre). */
    private static final class Shape {
        final int s;
        final float[] lx, lz, la, lb, lang;      // lobes: centre, semi-axes, rotation
        final int n;
        final float[] bx, bz, br, bs;            // bays: centre, radius, strength

        /** Scale of the island against the original 128 km one. */
        final float k;

        Shape(int seed, float scale) {
            s = seed;
            this.k = scale;
            Random r = new Random(seed * 2654435761L + 17);
            float theta = (float) (Math.PI / 2 + (r.nextFloat() - 0.5f) * 0.7f);    // long axis roughly north-south
            float L = (94000f + r.nextFloat() * 16000f) * scale, W = (44000f + r.nextFloat() * 16000f) * scale;
            float bend = (r.nextFloat() - 0.5f) * 0.9f, twist = (r.nextFloat() - 0.5f) * 0.6f;
            int lobes = 6 + r.nextInt(3), pen = 3 + r.nextInt(2);
            n = lobes + pen;
            lx = new float[n]; lz = new float[n]; la = new float[n]; lb = new float[n]; lang = new float[n];
            float ax = (float) Math.cos(theta), az = (float) Math.sin(theta), nx = -az, nz = ax;
            for (int i = 0; i < lobes; i++) {
                float t = -0.44f + 0.88f * i / (lobes - 1) + (r.nextFloat() - 0.5f) * 0.05f;
                float lat = (bend * (4 * t * t - 1) + twist * (float) Math.sin(t * Math.PI * 2)) * W * 0.35f
                        + (r.nextFloat() - 0.5f) * W * 0.22f;
                lx[i] = ax * t * L + nx * lat;
                lz[i] = az * t * L + nz * lat;
                float taper = 1f - 0.55f * (2 * t) * (2 * t);
                la[i] = L / lobes * (0.85f + 0.55f * r.nextFloat());
                lb[i] = W * 0.5f * (0.65f + 0.45f * r.nextFloat()) * Math.max(0.4f, taper);
                // the tangent of the bent axis
                float dl = (bend * 8 * t + twist * (float) (Math.PI * 2 * Math.cos(t * Math.PI * 2))) * W * 0.35f / L;
                lang[i] = theta + (float) Math.atan(dl) + (r.nextFloat() - 0.5f) * 0.3f;
            }
            // peninsulas: long narrow lobes reaching out from the coast
            for (int k = 0; k < pen; k++) {
                int base = r.nextInt(lobes);
                float a = r.nextFloat() * 6.2832f;
                float ca = (float) Math.cos(lang[base]), sa = (float) Math.sin(lang[base]);
                float ex = (float) Math.cos(a) * la[base] * 0.9f, ez = (float) Math.sin(a) * lb[base] * 0.9f;
                float px = lx[base] + ca * ex - sa * ez, pz = lz[base] + sa * ex + ca * ez;
                float dx = px - lx[base], dz = pz - lz[base], dl = (float) Math.sqrt(dx * dx + dz * dz) + 1e-3f;
                float len = (6000f + r.nextFloat() * 9000f) * scale;
                int i = lobes + k;
                lx[i] = px + dx / dl * len * 0.45f;
                lz[i] = pz + dz / dl * len * 0.45f;
                la[i] = len * 0.55f;
                lb[i] = (1600f + r.nextFloat() * 2200f) * Math.max(0.5f, scale);
                lang[i] = (float) Math.atan2(dz, dx);
            }
            int nb = 2 + r.nextInt(3);
            bx = new float[nb]; bz = new float[nb]; br = new float[nb]; bs = new float[nb];
            for (int k = 0; k < nb; k++) {
                int base = r.nextInt(lobes);
                float a = r.nextFloat() * 6.2832f;
                bx[k] = lx[base] + (float) Math.cos(a) * lb[base];
                bz[k] = lz[base] + (float) Math.sin(a) * lb[base];
                br[k] = (4000f + r.nextFloat() * 6000f) * scale;
                bs[k] = 0.6f + r.nextFloat() * 0.6f;
            }
        }

        /** Positive on land (roughly 1 at lobe centres), negative in the sea. */
        float field(float x, float z) {
            // layered domain warp: bays and headlands at several scales
            float wx = warp(x, z, 0), wz = warp(x, z, 3);
            x += wx;
            z += wz;
            float f = -1f;
            for (int i = 0; i < n; i++) {
                float dx = x - lx[i], dz = z - lz[i];
                float c = (float) Math.cos(lang[i]), sn = (float) Math.sin(lang[i]);
                float u = (dx * c + dz * sn) / la[i], v = (-dx * sn + dz * c) / lb[i];
                f = Math.max(f, 1f - (float) Math.sqrt(u * u + v * v));
            }
            for (int k = 0; k < bx.length; k++) {
                float d = (float) Math.hypot(x - bx[k], z - bz[k]) / br[k];
                if (d < 1f) f -= bs[k] * (1f - d) * (1f - d);
            }
            return f;
        }

        private float warp(float x, float z, int k) {
            // the broad bays and headlands scale with the island; the small coves don't
            float K = this.k;
            return (vn(x / (26000f * K), z / (26000f * K), s + 800 + k) - 0.5f) * 14000f * K
                    + (vn(x / (8000f * K), z / (8000f * K), s + 801 + k) - 0.5f) * 4400f * K
                    + (vn(x / 2600f, z / 2600f, s + 802 + k) - 0.5f) * 1400f
                    + (vn(x / 900f, z / 900f, s + 810 + k) - 0.5f) * 420f;
        }

        /** Distance east of the centre to the first coast along the centre line. */
        float eastCoast() {
            for (float x = 0; x < 64000f * k; x += 50f) if (field(x, 0f) <= 0f) return x;
            return 30000f * k;
        }
    }

    private void buildSdf(Shape shape, float ox, float oz) {
        boolean[] land = new boolean[N * N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                float x = islandX0 + (i + 0.5f) * CELL, z = islandZ0 + (j + 0.5f) * CELL;
                boolean l = shape.field(x - ox, z - oz) > 0f;
                // the city's stretch of coast: land behind it, open sea in front of it
                if (x > cx0 - 300f && x < cx1 + 40f && z > cz0 - 300f && z < cz1 + 300f) l = true;
                if (x >= cx1 + 40f && x < cx1 + 2000f && z > cz0 - 300f && z < cz1 + 300f) l = false;
                if (i < 2 || j < 2 || i >= N - 2 || j >= N - 2) l = false;
                land[j * N + i] = l;
            }
        // keep only the landmass the city is on
        int start = (int) ((cx0 - 500f - islandX0) / CELL) + (int) ((czc - islandZ0) / CELL) * N;
        boolean[] keep = flood(land, start, true);
        // fill enclosed water (lakes become land; rivers are not modelled)
        boolean[] sea = flood(invert(keep), 0, true);
        for (int k = 0; k < N * N; k++) land[k] = !sea[k];
        // two-pass chamfer distance to the other side of the coast, in metres
        float[] dIn = chamfer(land), dOut = chamfer(invert(land));
        for (int k = 0; k < N * N; k++) sdf[k] = land[k] ? -(dIn[k] - CELL * 0.5f) : (dOut[k] - CELL * 0.5f);
    }

    private static boolean[] invert(boolean[] a) {
        boolean[] b = new boolean[a.length];
        for (int k = 0; k < a.length; k++) b[k] = !a[k];
        return b;
    }

    /** Cells of value {@code v} connected to {@code start} (4-neighbour flood fill). */
    private boolean[] flood(boolean[] m, int start, boolean v) {
        boolean[] out = new boolean[m.length];
        if (m[start] != v) return out;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(start);
        out[start] = true;
        while (!q.isEmpty()) {
            int k = q.poll(), i = k % N, j = k / N;
            int[] nb = {i > 0 ? k - 1 : -1, i < N - 1 ? k + 1 : -1, j > 0 ? k - N : -1, j < N - 1 ? k + N : -1};
            for (int t : nb) if (t >= 0 && !out[t] && m[t] == v) { out[t] = true; q.add(t); }
        }
        return out;
    }

    /** Distance (metres) from each true cell to the nearest false cell; 0 for false cells. */
    private float[] chamfer(boolean[] in) {
        float big = 1e9f, a = CELL, b = CELL * 1.4142f;
        float[] d = new float[N * N];
        for (int k = 0; k < N * N; k++) d[k] = in[k] ? big : 0f;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int k = j * N + i;
                if (d[k] == 0f) continue;
                float v = d[k];
                if (i > 0) v = Math.min(v, d[k - 1] + a);
                if (j > 0) {
                    v = Math.min(v, d[k - N] + a);
                    if (i > 0) v = Math.min(v, d[k - N - 1] + b);
                    if (i < N - 1) v = Math.min(v, d[k - N + 1] + b);
                }
                d[k] = v;
            }
        for (int j = N - 1; j >= 0; j--)
            for (int i = N - 1; i >= 0; i--) {
                int k = j * N + i;
                if (d[k] == 0f) continue;
                float v = d[k];
                if (i < N - 1) v = Math.min(v, d[k + 1] + a);
                if (j < N - 1) {
                    v = Math.min(v, d[k + N] + a);
                    if (i < N - 1) v = Math.min(v, d[k + N + 1] + b);
                    if (i > 0) v = Math.min(v, d[k + N - 1] + b);
                }
                d[k] = v;
            }
        return d;
    }

    // ------------------------------------------------------------------ noise helpers

    /** Smooth value noise (non-tiling), roughly 0..1. */
    private static float vn(float x, float z, int s) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        float a = Noise.rnd(ix, iz, s), b = Noise.rnd(ix + 1, iz, s), c = Noise.rnd(ix, iz + 1, s), d = Noise.rnd(ix + 1, iz + 1, s);
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Distance from the seed-shaped urban footprint (0 inside). */
    public float cityDistance(float x, float z) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < cities.length; i++) {
            best = Math.min(best, cityDistance(i, x, z));
            if (best == 0f) return 0f;
        }
        return best;
    }

    /** Adds another city's grid rectangle (before the roads are built). */
    public void addCity(float x0, float z0, float x1, float z1) {
        float[][] c = java.util.Arrays.copyOf(cities, cities.length + 1);
        c[cities.length] = new float[]{x0, z0, x1, z1};
        cities = c;
    }

    /** Distance from city i's seed-shaped urban footprint (0 inside). */
    public float cityDistance(int ci, float x, float z) {
        float[] c = cities[ci];
        float cx0 = c[0], cz0 = c[1], cx1 = c[2], cz1 = c[3];
        float rx = Math.max(1f, (cx1 - cx0) * 0.5f);
        float rz = Math.max(1f, (cz1 - cz0) * 0.5f);
        float nx = (x - (cx0 + cx1) * 0.5f) / rx;
        float nz = (z - (cz0 + cz1) * 0.5f) / rz;
        // a quick bound: far outside the rectangle the outline can't be near
        if (Math.abs(nx) > 2.5f || Math.abs(nz) > 2.5f) return ((float) Math.sqrt(nx * nx + nz * nz) - 0.86f) * Math.min(rx, rz);
        float angle = (float) Math.atan2(nz, nx);
        float edge = 0.86f + 0.09f * (float) Math.sin(angle * 3f + seed * 0.00017f + ci * 1.7f)
                + 0.05f * (float) Math.sin(angle * 7f + seed * 0.00031f + ci * 2.3f);
        edge = Math.min(1f, Math.max(0.72f, edge));
        float outside = (float) Math.sqrt(nx * nx + nz * nz) - edge;
        return Math.max(0f, outside * Math.min(rx, rz));
    }

    // ------------------------------------------------------------------ heights

    /** Final ground height: natural terrain shaped by the roads. */
    public float height(float x, float z) {
        RoadNetwork r = roads;
        if (r == null) return exact(x, z);
        // The ground is a height map with a sample every GRID metres; the height anywhere is the
        // bilinear blend of its four corners, exactly what the Mode 7 ground renders, so
        // buildings, cars, people and props sit on the drawn surface.
        float gx = x / GRID, gz = z / GRID;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        float tx = gx - ix, tz = gz - iz;
        float h00 = grid(ix, iz), h10 = grid(ix + 1, iz), h01 = grid(ix, iz + 1), h11 = grid(ix + 1, iz + 1);
        float top = h00 + (h10 - h00) * tx, bot = h01 + (h11 - h01) * tx;
        return top + (bot - top) * tz;
    }

    /** Spacing of the height map samples (metres). */
    public static final float GRID = 8f;

    /** Ground height shaped by roads at an exact point (no interpolation). */
    public float exact(float x, float z) {
        float n = natural(x, z);
        RoadNetwork r = roads;
        return r == null ? n : r.shapeTerrain(x, z, n);
    }

    // direct-mapped cache of height map samples: key and float bits packed in one long
    private final long[] cache = new long[1 << 18];

    /** Height map sample (ix, iz) (cached). */
    public float grid(int ix, int iz) {
        int key = ((ix & 0x7FFF) << 15) | (iz & 0x7FFF);
        int slot = (key * 0x9E3779B1) >>> 14;
        long v = cache[slot];
        if (v != 0L && (int) (v >>> 32) == key + 1) return Float.intBitsToFloat((int) v);
        float h = exact(ix * GRID, iz * GRID);
        cache[slot] = ((long) (key + 1) << 32) | (Float.floatToRawIntBits(h) & 0xFFFFFFFFL);
        return h;
    }

    /** How cliff-like the coast is here (0..1); never near the city. */
    public float cliff(float x, float z) {
        float c = smooth(0.56f, 0.66f, vn(x / 9000f, z / 9000f, seed + 70));
        return c * smooth(2500f, 6000f, cityDistance(x, z));
    }

    /** How rocky (not sandy) a low coast is here (0..1). */
    public float rocky(float x, float z) {
        float r = smooth(0.55f, 0.65f, vn(x / 3800f, z / 3800f, seed + 71));
        return r * (1f - cliff(x, z)) * smooth(2500f, 6000f, cityDistance(x, z));
    }

    /** Ground height without road cuttings and embankments. */
    public float natural(float x, float z) {
        float over = coastOver(x, z);
        float d = Math.max(0f, -over);
        float h = land(x, z, d);
        if (over <= 0f) return h;
        // under water: the coast's own profile falls away, steeply under cliffs
        float drop = over * (0.06f + 0.22f * cliff(x, z)) + Math.max(0f, over - 55f) * 0.35f;
        return Math.max(-40f, h - drop);
    }

    /** Land height at inland distance d from the coast. */
    private float land(float x, float z, float d) {
        float cd = cityDistance(x, z);
        float wReg = smooth(250f, 1100f, cd);
        float local = 0f, regional = 0f;
        if (wReg < 1f) {
            // gentle plateau under the city (max slope a few percent) and the hills around it
            float east = eastness(x, z);
            float base = 3.5f + 4.5f * vn(x / 330f, z / 330f, seed);
            float hill = vn(x / 230f, z / 230f, seed + 11) * 0.65f + vn(x / 90f, z / 90f, seed + 12) * 0.35f;
            hill = hill * hill * 1.4f;
            local = base + hill * 58f * smooth(30f, 200f, cd) * (1f - east);
        }
        if (wReg > 0f) regional = regional(x, z, d);
        return local + (regional - local) * wReg;
    }

    /** Coast, plains, hill country and mountains. */
    private float regional(float x, float z, float d) {
        float cliff = cliff(x, z), rocky = rocky(x, z);
        // coastal profile: sandy beach ramp, rocky shore, or a cliff face
        float beach = 0.4f + d * 0.0045f;
        float rock = 0.4f + 3f * smooth(0f, 40f, d) + d * 0.006f;
        float cliffH = 22f + 70f * vn(x / 3000f, z / 3000f, seed + 72);
        float cliffs = cliffH * smooth(0f, 90f, d) + d * 0.004f;
        float coast = beach + (rock - beach) * rocky;
        coast = coast + (cliffs - coast) * cliff;
        // plains rise gently inland
        float plains = (6f + 20f * vn(x / 7000f, z / 7000f, seed + 30) + 7f * vn(x / 1500f, z / 1500f, seed + 33)) * smooth(200f, 3500f, d);
        // rolling hill country (cliff coasts are backed by hills)
        float hm = Math.max(smooth(0.42f, 0.7f, vn(x / 9000f, z / 9000f, seed + 31)), cliff * 0.8f);
        float hv = vn(x / 700f, z / 700f, seed + 34) * 0.6f + vn(x / 260f, z / 260f, seed + 35) * 0.4f;
        float hills = hm * hv * hv * 110f * smooth(150f, 4000f, d);
        // mountain ranges in the interior, ridged
        float interior = smooth(2500f, 15000f, d);
        float mm = smooth(0.3f, 0.7f, vn(x / 20000f, z / 20000f, seed + 32)) * interior;
        float mountains = 0f;
        if (mm > 0f) {
            // ridges on rotated, warped axes so they don't line up with the noise lattice
            float wx = x + (vn(x / 5200f, z / 5200f, seed + 38) - 0.5f) * 3000f, wz = z + (vn(x / 5200f, z / 5200f, seed + 39) - 0.5f) * 3000f;
            float ax = wx * 0.8f - wz * 0.6f, az = wx * 0.6f + wz * 0.8f;
            float bx = wx * 0.28f + wz * 0.96f, bz = -wx * 0.96f + wz * 0.28f;
            float r1 = 1f - Math.abs(2f * (vn(ax / 3200f, az / 3200f, seed + 36) * 0.7f + vn(bx / 1700f, bz / 1700f, seed + 40) * 0.3f) - 1f);
            float r2 = 1f - Math.abs(2f * vn(bx / 950f, bz / 950f, seed + 37) - 1f);
            mountains = mm * (130f + 560f * r1 * r1 + 110f * r2 * r2);
        }
        return coast + plains + hills + mountains;
    }

    public float eastness(float x, float z) {
        return smooth(cx1 - 120f, cx1 + 20f, x) * (1f - smooth(0f, 260f, Math.max(cz0 - z, z - cz1)));
    }

    /**
     * Signed distance to the coast in metres: positive out at sea, negative inland. Past the city
     * the coast is a straight north-south line; elsewhere it follows the island outline.
     */
    public float coastOver(float x, float z) {
        float gx = (x - islandX0) / CELL - 0.5f, gz = (z - islandZ0) / CELL - 0.5f;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        float s;
        if (ix < 0 || iz < 0 || ix >= N - 1 || iz >= N - 1) s = 20000f;
        else {
            float tx = gx - ix, tz = gz - iz;
            int k = iz * N + ix;
            float top = sdf[k] + (sdf[k + 1] - sdf[k]) * tx, bot = sdf[k + N] + (sdf[k + N + 1] - sdf[k + N]) * tx;
            s = top + (bot - top) * tz;
            // finer wiggles than the grid can hold
            if (Math.abs(s) < 600f) s += (vn(x / 420f, z / 420f, seed + 90) - 0.5f) * 140f + (vn(x / 130f, z / 130f, seed + 91) - 0.5f) * 40f;
        }
        // the city's beach: a straight coast 50 m east of the city, blending into the outline
        float awayZ = Math.max(0f, Math.abs(z - czc) - (cz1 - cz0) * 0.5f - 400f);
        float w = 1f - smooth(0f, 2500f, awayZ);
        if (w > 0f && x > cx0 - 2000f) {
            float straight = x - (cx1 + 50f);
            s = s + (straight - s) * w * smooth(cx0 - 2000f, cx0, x);
        }
        return s;
    }

    /** True for beach sand: the sandy coastal strip, or anything just above sea level. */
    public boolean isSand(float x, float z, float h) {
        if (h < SAND_LEVEL) return rocky(x, z) < 0.5f;
        return coastOver(x, z) > -70f && h < 6f && cliff(x, z) < 0.5f && rocky(x, z) < 0.5f;
    }

    /** Rocky shoreline: low rocky coast or the foot of a cliff. */
    public boolean isShoreRock(float x, float z, float h) {
        float o = coastOver(x, z);
        return o > -60f && h < 12f && (rocky(x, z) > 0.5f || cliff(x, z) > 0.5f);
    }

    /** Surface normal from central differences. */
    public void normal(float x, float z, float[] out) {
        float e = 1.5f;
        float dx = (height(x + e, z) - height(x - e, z)) / (2 * e);
        float dz = (height(x, z + e) - height(x, z - e)) / (2 * e);
        float l = (float) Math.sqrt(dx * dx + 1 + dz * dz);
        out[0] = -dx / l;
        out[1] = 1f / l;
        out[2] = -dz / l;
    }

    /** True if the line of sight from the eye to the point is blocked by terrain. */
    public boolean occluded(float ex, float ey, float ez, float px, float py, float pz) {
        float d = (float) Math.hypot(px - ex, pz - ez);
        int n = Math.max(12, Math.min(48, (int) (d / 6f)));
        // a clear margin over the ground: only a ridge that properly rises above the line counts
        for (int i = 1; i < n; i++) {
            float t = i / (float) n;
            float x = ex + (px - ex) * t, y = ey + (py - ey) * t, z = ez + (pz - ez) * t;
            if (height(x, z) > y + 2f) return true;
        }
        return false;
    }

    /**
     * True only if a box is hidden behind the land from every side: the lines of sight to its top
     * centre, its four top corners and the middle of its sides are all blocked. One blocked line
     * is not enough on a hillside (a slope can cut across the line to the middle of a building
     * whose roof and walls are in plain view); the ground already hides, pixel by pixel, whatever
     * really is behind a hill, so this cull only skips what certainly is.
     */
    public boolean occludedBox(float ex, float ey, float ez, float x0, float z0, float x1, float z1, float y0, float y1) {
        float cx = (x0 + x1) * 0.5f, cz = (z0 + z1) * 0.5f, top = y1 + 1f, mid = (y0 + y1) * 0.5f;
        if (!occluded(ex, ey, ez, cx, top, cz)) return false;
        if (!occluded(ex, ey, ez, x0, top, z0) || !occluded(ex, ey, ez, x1, top, z0)) return false;
        if (!occluded(ex, ey, ez, x0, top, z1) || !occluded(ex, ey, ez, x1, top, z1)) return false;
        return occluded(ex, ey, ez, x0, mid, cz) && occluded(ex, ey, ez, x1, mid, cz)
                && occluded(ex, ey, ez, cx, mid, z0) && occluded(ex, ey, ez, cx, mid, z1);
    }
}
