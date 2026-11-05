package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.SoftRenderer;

/**
 * Emits polygons into render batches for one chunk, with sun lighting baked into vertex light
 * values. Floors can follow the terrain heightfield.
 */
public final class GeoBuilder {
    private static final float SX, SY, SZ;
    static {
        float x = 0.55f, y = 0.75f, z = 0.38f, l = (float) Math.sqrt(x * x + y * y + z * z);
        SX = x / l; SY = y / l; SZ = z / l;
    }

    public final Terrain terrain;
    private final Chunk chunk;
    private Geo cur;
    private boolean curIsObject;

    private final float[] p4 = new float[12], t4 = new float[8], l4 = new float[4], n3 = new float[3];

    public GeoBuilder(Terrain terrain, Chunk chunk) {
        this.terrain = terrain;
        this.chunk = chunk;
    }

    public static float sunLight(float nx, float ny, float nz) {
        return 0.6f + 0.52f * Math.max(0f, nx * SX + ny * SY + nz * SZ);
    }

    /** Starts a new render batch. Object batches (buildings) get terrain-occlusion tests. */
    public void begin(boolean object) {
        end();
        cur = new Geo();
        curIsObject = object;
    }

    public void end() {
        if (cur != null && cur.count > 0) {
            chunk.batches.add(cur);
            if (curIsObject) { chunk.objectBatches.add(cur); cur.object = true; }
        }
        cur = null;
    }

    private Geo target() {
        if (cur == null) begin(false);
        return cur;
    }

    /** Dry run: no geometry at all, only ground paint is recorded (see {@link #floorAreaT}). */
    boolean dry;
    /** Where ground areas go: {x0, z0, x1, z1, texture, uScale, vScale}; null drops them. */
    java.util.List<float[]> paint;

    public void quad(float[] p, float[] uv, float[] l, int tex, int layer, int flags, float bias, boolean doubleSided) {
        if (!dry) target().add(p, uv, l, tex, layer, flags, bias, doubleSided);
    }

    /** Dither-blended overlay quad (coverage per vertex), drawn over what lies beneath it. */
    public void quadBlend(float[] p, float[] uv, float[] l, float[] a, int tex, int layer, int flags, float bias) {
        if (!dry) target().addBlend(p, uv, l, a, tex, layer, flags, bias);
    }

    public void quad3(float[] p, float[] uv, float[] l, int tex, int layer) {
        if (!dry) target().add(p, uv, l, tex, layer, Geo.FLAG_NO_SUBDIV, 0f, false);
    }

    // ------------------------------------------------------------------ wall props

    /**
     * A box standing against a wall (outdoor a/c unit, meter box): its back is on the wall and
     * has no polygon. (ax, az) is the point on the wall's face under its centre, (nx, nz) the
     * wall's outward normal; front, two sides and top.
     */
    public void wallUnit(float ax, float az, float nx, float nz, float width, float y0, float height, float depth,
                         int frontTex, int sideTex) {
        float tx = -nz, tz = nx;                         // along the wall
        float hw = width * 0.5f, y1 = y0 + height;
        float bx0 = ax - tx * hw, bz0 = az - tz * hw, bx1 = ax + tx * hw, bz1 = az + tz * hw;   // back corners
        float fx0 = bx0 + nx * depth, fz0 = bz0 + nz * depth, fx1 = bx1 + nx * depth, fz1 = bz1 + nz * depth;
        float lf = sunLight(nx, 0f, nz), ls0 = sunLight(-tx, 0f, -tz), ls1 = sunLight(tx, 0f, tz), lt = sunLight(0f, 1f, 0f);
        // front: counter-clockwise seen from outside (from +n)
        quad(new float[]{fx1, y0, fz1, fx0, y0, fz0, fx0, y1, fz0, fx1, y1, fz1}, new float[]{0, 1, 1, 1, 1, 0, 0, 0},
                new float[]{lf, lf, lf, lf}, frontTex, com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT, Geo.FLAG_NO_SUBDIV | Geo.FLAG_DETAIL, 0f, true);
        quad(new float[]{fx0, y0, fz0, bx0, y0, bz0, bx0, y1, bz0, fx0, y1, fz0}, new float[]{0, 1, 1, 1, 1, 0, 0, 0},
                new float[]{ls0, ls0, ls0, ls0}, sideTex, com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT, Geo.FLAG_NO_SUBDIV | Geo.FLAG_DETAIL, 0f, true);
        quad(new float[]{bx1, y0, bz1, fx1, y0, fz1, fx1, y1, fz1, bx1, y1, bz1}, new float[]{0, 1, 1, 1, 1, 0, 0, 0},
                new float[]{ls1, ls1, ls1, ls1}, sideTex, com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT, Geo.FLAG_NO_SUBDIV | Geo.FLAG_DETAIL, 0f, true);
        quad(new float[]{bx0, y1, bz0, bx1, y1, bz1, fx1, y1, fz1, fx0, y1, fz0}, new float[]{0, 0, 1, 0, 1, 1, 0, 1},
                new float[]{lt, lt, lt, lt}, sideTex, com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT, Geo.FLAG_NO_SUBDIV | Geo.FLAG_DETAIL, 0f, true);
    }

    /**
     * A balcony on a wall from (ax, az) to (bx, bz) at floor height y: the slab (floor and
     * underside), the two side panels and the front; nothing where it meets the wall.
     */
    public void balcony(float ax, float az, float bx, float bz, float nx, float nz, float y, float depth, float rail,
                        int frontTex, int slabTex) {
        float fx0 = ax + nx * depth, fz0 = az + nz * depth, fx1 = bx + nx * depth, fz1 = bz + nz * depth;
        float len = (float) Math.hypot(bx - ax, bz - az);
        int L = com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;
        float lt = sunLight(0f, 1f, 0f) * 0.85f, lf = sunLight(nx, 0f, nz);
        float tx = (bx - ax) / Math.max(1e-4f, len), tz = (bz - az) / Math.max(1e-4f, len);
        float la = sunLight(-tx, 0f, -tz), lb = sunLight(tx, 0f, tz);
        // floor slab, seen from above and from below
        quad(new float[]{ax, y, az, bx, y, bz, fx1, y, fz1, fx0, y, fz0}, new float[]{0, 0, len / 2f, 0, len / 2f, depth / 2f, 0, depth / 2f},
                new float[]{lt, lt, lt, lt}, slabTex, L, Geo.FLAG_NO_SUBDIV, 0f, true);
        float top = y + rail;
        float[] uvF = {0, 1, len / 4f, 1, len / 4f, 0, 0, 0};
        quad(new float[]{fx0, y - 0.15f, fz0, fx1, y - 0.15f, fz1, fx1, top, fz1, fx0, top, fz0}, uvF,
                new float[]{lf, lf, lf, lf}, frontTex, L, Geo.FLAG_NO_SUBDIV, 0f, true);
        float[] uvS = {0, 1, depth / 4f, 1, depth / 4f, 0, 0, 0};
        quad(new float[]{ax, y - 0.15f, az, fx0, y - 0.15f, fz0, fx0, top, fz0, ax, top, az}, uvS,
                new float[]{la, la, la, la}, frontTex, L, Geo.FLAG_NO_SUBDIV, 0f, true);
        quad(new float[]{fx1, y - 0.15f, fz1, bx, y - 0.15f, bz, bx, top, bz, fx1, top, fz1}, uvS,
                new float[]{lb, lb, lb, lb}, frontTex, L, Geo.FLAG_NO_SUBDIV, 0f, true);
    }

    /**
     * Overhead power lines between two cross-arms as one ribbon: an alpha-cut texture of three
     * wires 0.6 m apart, tiled along the span, sagging in a parabola (4 pieces). (sx, sz) is the
     * horizontal direction across the wires (along the cross-arm).
     */
    public void powerLine(float ax, float ay, float az, float bx, float by, float bz, float sx, float sz, float sag, int tex) {
        float half = 0.8f;
        float px = sx * half, pz = sz * half;
        float len = (float) Math.hypot(bx - ax, bz - az);
        if (len < 0.5f) return;
        int n = 4;
        float[] l = {0.55f, 0.55f, 0.55f, 0.55f};
        int L = SoftRenderer.L_OBJECT;
        for (int k = 0; k < n; k++) {
            float t0 = k / (float) n, t1 = (k + 1) / (float) n;
            float x0 = ax + (bx - ax) * t0, z0 = az + (bz - az) * t0, y0 = ay + (by - ay) * t0 - sag * 4f * t0 * (1f - t0);
            float x1 = ax + (bx - ax) * t1, z1 = az + (bz - az) * t1, y1 = ay + (by - ay) * t1 - sag * 4f * t1 * (1f - t1);
            float u0 = len * t0 / 4f, u1 = len * t1 / 4f;
            quad(new float[]{x0 - px, y0, z0 - pz, x1 - px, y1, z1 - pz, x1 + px, y1, z1 + pz, x0 + px, y0, z0 + pz},
                    new float[]{u0, 0f, u1, 0f, u1, 1f, u0, 1f}, l, tex, L, Geo.FLAG_ALPHA | Geo.FLAG_DETAIL | Geo.FLAG_NO_SUBDIV, -0.2f, true);
        }
    }

    public float h(float x, float z) { return terrain.height(x, z); }

    // ------------------------------------------------------------------ floors

    /** Flat horizontal cell at height y. */
    public void floor(float ax, float az, float bx, float bz, float y, int tex, float uScale, float vScale,
                      int layer, float light, float bias) {
        floor4(ax, az, bx, bz, y, y, y, y, tex, uScale, vScale, layer, 0, bias, light, light, light, light);
    }

    /** Cell whose corners follow the terrain plus an offset (sidewalks, roads, markings). */
    public void floorT(float ax, float az, float bx, float bz, float off, int tex, float uScale, float vScale,
                       int layer, int flags, float bias, boolean terrainLight) {
        float y0 = h(ax, bz) + off, y1 = h(bx, bz) + off, y2 = h(bx, az) + off, y3 = h(ax, az) + off;
        float l0, l1, l2, l3;
        if (terrainLight) {
            l0 = tl(ax, bz); l1 = tl(bx, bz); l2 = tl(bx, az); l3 = tl(ax, az);
        } else {
            l0 = l1 = l2 = l3 = sunLight(0, 1, 0);
        }
        floor4(ax, az, bx, bz, y0, y1, y2, y3, tex, uScale, vScale, layer, flags, bias, l0, l1, l2, l3);
    }

    private float tl(float x, float z) {
        terrain.normal(x, z, n3);
        // "vertex painting": broad, soft patches of lighter and darker ground
        return sunLight(n3[0], n3[1], n3[2]) * (0.86f + 0.26f * paint(x, z));
    }

    /** Smooth value noise in 0..1 with ~30 m features, for per-vertex ground tint. */
    static float paint(float x, float z) {
        float fx = x / 31f, fz = z / 31f;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        float tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        float a = com.harlequinxd.drivehard.core.tex.Noise.rnd(ix, iz, 77), b = com.harlequinxd.drivehard.core.tex.Noise.rnd(ix + 1, iz, 77);
        float c = com.harlequinxd.drivehard.core.tex.Noise.rnd(ix, iz + 1, 77), d = com.harlequinxd.drivehard.core.tex.Noise.rnd(ix + 1, iz + 1, 77);
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    /** Terrain-following overlay cell with per-corner coverage (corners as in floorT), dither-blended. */
    public void floorBlend(float ax, float az, float bx, float bz, int tex, float uvScale, float[] cov, int layer, int flags, float bias) {
        float[] p = new float[12], t = new float[8], l = new float[4], a = new float[4];
        float[] xs = {ax, bx, bx, ax}, zs = {bz, bz, az, az};
        for (int k = 0; k < 4; k++) {
            p[k * 3] = xs[k]; p[k * 3 + 1] = h(xs[k], zs[k]) + 0.01f; p[k * 3 + 2] = zs[k];
            t[k * 2] = xs[k] / uvScale; t[k * 2 + 1] = zs[k] / uvScale;
            l[k] = tl(xs[k], zs[k]);
            a[k] = cov[k];
        }
        if (!dry) target().addBlend(p, t, l, a, tex, layer, flags, bias);
    }

    /** Corners: 0=(ax,bz) 1=(bx,bz) 2=(bx,az) 3=(ax,az). */
    private void floor4(float ax, float az, float bx, float bz, float y0, float y1, float y2, float y3, int tex,
                        float uScale, float vScale, int layer, int flags, float bias, float l0, float l1, float l2, float l3) {
        float[] p = p4, t = t4, l = l4;
        p[0] = ax; p[1] = y0; p[2] = bz;
        p[3] = bx; p[4] = y1; p[5] = bz;
        p[6] = bx; p[7] = y2; p[8] = az;
        p[9] = ax; p[10] = y3; p[11] = az;
        t[0] = ax / uScale; t[1] = bz / vScale;
        t[2] = bx / uScale; t[3] = bz / vScale;
        t[4] = bx / uScale; t[5] = az / vScale;
        t[6] = ax / uScale; t[7] = az / vScale;
        l[0] = l0; l[1] = l1; l[2] = l2; l[3] = l3;
        if (!dry) target().add(p, t, l, tex, layer, flags, bias, false);
    }

    /**
     * Flat ground cover (sidewalks, lots, parks, car parks, paths). The ground is a painted map, so
     * this is not geometry: the area is recorded for the ground painter.
     */
    public void floorAreaT(float ax, float az, float bx, float bz, float off, int tex, float uScale, float vScale,
                           int layer, float cell, int flags, float bias) {
        if (paint != null) paint.add(new float[]{ax, az, bx, bz, tex, uScale, vScale});
    }

    // ------------------------------------------------------------------ walls

    /**
     * Vertical wall from A to B (outward normal = (B-A) x up), between heights y0..y1.
     * u runs A->B, v from vTop (at y1) to vBot (at y0).
     */
    public void wall(float ax, float az, float bx, float bz, float y0, float y1, int tex,
                     float u0, float u1, float vTop, float vBot, int layer, float bias, float ao) {
        wallSloped(ax, az, bx, bz, y0, y0, y1, y1, tex, u0, u1, vTop, vBot, layer, bias, ao, 0);
    }

    /** Wall whose bottom/top heights differ at each end (curbs and foundations on slopes). */
    public void wallSloped(float ax, float az, float bx, float bz, float ya0, float yb0, float ya1, float yb1, int tex,
                           float u0, float u1, float vTop, float vBot, int layer, float bias, float ao, int flags) {
        float dx = bx - ax, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f || (ya1 - ya0 < 1e-3f && yb1 - yb0 < 1e-3f)) return;
        float nx = -dz / len, nz = dx / len;
        float lt = sunLight(nx, 0, nz);
        float[] p = p4, t = t4, l = l4;
        p[0] = ax; p[1] = ya0; p[2] = az;
        p[3] = bx; p[4] = yb0; p[5] = bz;
        p[6] = bx; p[7] = yb1; p[8] = bz;
        p[9] = ax; p[10] = ya1; p[11] = az;
        t[0] = u0; t[1] = vBot; t[2] = u1; t[3] = vBot; t[4] = u1; t[5] = vTop; t[6] = u0; t[7] = vTop;
        l[0] = l[1] = lt * ao;
        l[2] = l[3] = lt;
        if (!dry) target().add(p, t, l, tex, layer, flags, bias, false);
    }

    /** Flat-based wall split into columns of at most {@code maxW} m and rows of {@code rowH} m. */
    public void wallGrid(float ax, float az, float bx, float bz, float y0, float y1, int tex,
                         float uPerM, float vPerM, float maxW, float rowH, int layer, float bias, boolean groundAO) {
        float dx = bx - ax, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        int cols = Math.max(1, (int) Math.ceil(len / maxW));
        int rows = Math.max(1, (int) Math.ceil((y1 - y0) / rowH - 1e-3f));
        for (int c = 0; c < cols; c++) {
            float s0 = c / (float) cols, s1 = (c + 1) / (float) cols;
            float px0 = ax + dx * s0, pz0 = az + dz * s0, px1 = ax + dx * s1, pz1 = az + dz * s1;
            for (int r = 0; r < rows; r++) {
                float ya = y0 + r * rowH, yb = Math.min(y1, ya + rowH);
                float vB = (y1 - ya) * vPerM, vT = (y1 - yb) * vPerM;
                float ao = (groundAO && r == 0) ? 0.78f : 1f;
                wall(px0, pz0, px1, pz1, ya, yb, tex, len * s0 * uPerM, len * s1 * uPerM, vT, vB, layer, bias, ao);
            }
        }
    }

    /** Box with four walls and a top (no bottom). */
    public void box(float ax, float ay, float az, float bx, float by, float bz, int sideTex, int topTex,
                    float uPerM, float vPerM, int layer, float bias, int flags) {
        boxWall(ax, bz, bx, bz, ay, by, sideTex, uPerM, vPerM, layer, bias, flags);
        boxWall(bx, az, ax, az, ay, by, sideTex, uPerM, vPerM, layer, bias, flags);
        boxWall(bx, bz, bx, az, ay, by, sideTex, uPerM, vPerM, layer, bias, flags);
        boxWall(ax, az, ax, bz, ay, by, sideTex, uPerM, vPerM, layer, bias, flags);
        if (topTex >= 0) {
            float light = sunLight(0, 1, 0);
            for (float z = az; z < bz - 1e-3f; z += 8f)
                for (float x = ax; x < bx - 1e-3f; x += 8f)
                    floor4(x, z, Math.min(bx, x + 8f), Math.min(bz, z + 8f), by, by, by, by, topTex,
                            1f / uPerM, 1f / uPerM, layer, flags, bias, light, light, light, light);
        }
    }

    private void boxWall(float ax, float az, float bx, float bz, float y0, float y1, int tex, float uPerM, float vPerM,
                         int layer, float bias, int flags) {
        float dx = bx - ax, dz = bz - az, len = (float) Math.sqrt(dx * dx + dz * dz);
        wallSloped(ax, az, bx, bz, y0, y0, y1, y1, tex, 0f, len * uPerM, 0f, (y1 - y0) * vPerM, layer, bias, 1f, flags);
    }

    public static int objectLayer() { return SoftRenderer.L_OBJECT; }
}
