package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.SoftRenderer;

/**
 * Emits polygons into a grid of chunk {@link Geo}s (by polygon centroid) with simple
 * directional sun lighting baked into vertex light values.
 */
public final class GeoBuilder {
    public static final float CHUNK = 64f;
    // sun direction (normalised), from the south-east and high
    private static final float SX, SY, SZ;
    static {
        float x = 0.55f, y = 0.75f, z = 0.38f, l = (float) Math.sqrt(x * x + y * y + z * z);
        SX = x / l; SY = y / l; SZ = z / l;
    }

    public final Geo[] chunks;
    public final int cw, ch;
    public final float x0, z0;
    public int polyCount;

    private final float[] p4 = new float[12], t4 = new float[8], l4 = new float[4];

    public GeoBuilder(float minX, float minZ, float maxX, float maxZ) {
        x0 = minX;
        z0 = minZ;
        cw = (int) Math.ceil((maxX - minX) / CHUNK);
        ch = (int) Math.ceil((maxZ - minZ) / CHUNK);
        chunks = new Geo[cw * ch];
        for (int i = 0; i < chunks.length; i++) chunks[i] = new Geo();
    }

    public static float sunLight(float nx, float ny, float nz) {
        return 0.6f + 0.52f * Math.max(0f, nx * SX + ny * SY + nz * SZ);
    }

    private Geo chunkFor(float x, float z) {
        int cx = Math.max(0, Math.min(cw - 1, (int) ((x - x0) / CHUNK)));
        int cz = Math.max(0, Math.min(ch - 1, (int) ((z - z0) / CHUNK)));
        return chunks[cz * cw + cx];
    }

    /** Generic quad, points counter-clockwise seen from the front. */
    public void quad(float[] p, float[] uv, float[] l, int tex, int layer, int flags, float bias, boolean doubleSided) {
        float cx = (p[0] + p[3] + p[6] + p[9]) * 0.25f, cz = (p[2] + p[5] + p[8] + p[11]) * 0.25f;
        chunkFor(cx, cz).add(p, uv, l, tex, layer, flags, bias, doubleSided);
        polyCount++;
    }

    /** Single triangle (single sided). */
    public void quad3(float[] p, float[] uv, float[] l, int tex, int layer) {
        float cx = (p[0] + p[3] + p[6]) / 3f, cz = (p[2] + p[5] + p[8]) / 3f;
        chunkFor(cx, cz).add(p, uv, l, tex, layer, Geo.FLAG_NO_SUBDIV, 0f, false);
        polyCount++;
    }

    /** Horizontal cell facing up, uv from world position divided by the given scales. */
    public void floor(float ax, float az, float bx, float bz, float y, int tex, float uScale, float vScale,
                      int layer, float light, float bias) {
        float[] p = p4, t = t4, l = l4;
        p[0] = ax; p[1] = y; p[2] = bz;
        p[3] = bx; p[4] = y; p[5] = bz;
        p[6] = bx; p[7] = y; p[8] = az;
        p[9] = ax; p[10] = y; p[11] = az;
        t[0] = ax / uScale; t[1] = bz / vScale;
        t[2] = bx / uScale; t[3] = bz / vScale;
        t[4] = bx / uScale; t[5] = az / vScale;
        t[6] = ax / uScale; t[7] = az / vScale;
        l[0] = l[1] = l[2] = l[3] = light;
        quad(p, t, l, tex, layer, 0, bias, false);
    }

    /** Floor area split into cells no larger than {@code cell}. */
    public void floorArea(float ax, float az, float bx, float bz, float y, int tex, float uScale, float vScale,
                          int layer, float cell, float bias) {
        float light = sunLight(0, 1, 0);
        for (float z = az; z < bz - 1e-3f; z += cell) {
            float zz = Math.min(bz, z + cell);
            for (float x = ax; x < bx - 1e-3f; x += cell) {
                float xx = Math.min(bx, x + cell);
                floor(x, z, xx, zz, y, tex, uScale, vScale, layer, light, bias);
            }
        }
    }

    /**
     * Vertical wall from A to B (outward normal = (B-A) x up), between heights y0..y1.
     * u runs A->B, v from vTop (at y1) to vBot (at y0). Bottom vertices can be darkened (AO).
     */
    public void wall(float ax, float az, float bx, float bz, float y0, float y1, int tex,
                     float u0, float u1, float vTop, float vBot, int layer, float bias, float ao) {
        float dx = bx - ax, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f || y1 - y0 < 1e-4f) return;
        float nx = -dz / len, nz = dx / len;  // (B-A) x up  = (-dz, 0, dx)
        // cross((dx,0,dz),(0,1,0)) = (0*0 - dz*1, dz*0 - dx*0, dx*1 - 0) = (-dz, 0, dx)
        float lt = sunLight(nx, 0, nz);
        float[] p = p4, t = t4, l = l4;
        p[0] = ax; p[1] = y0; p[2] = az;
        p[3] = bx; p[4] = y0; p[5] = bz;
        p[6] = bx; p[7] = y1; p[8] = bz;
        p[9] = ax; p[10] = y1; p[11] = az;
        t[0] = u0; t[1] = vBot; t[2] = u1; t[3] = vBot; t[4] = u1; t[5] = vTop; t[6] = u0; t[7] = vTop;
        l[0] = l[1] = lt * ao;
        l[2] = l[3] = lt;
        quad(p, t, l, tex, layer, 0, bias, false);
    }

    /**
     * Wall split into a grid: columns of at most {@code maxW} metres, rows of {@code rowH} metres.
     * uPerM / vPerM convert metres to texture repeats; v counts from the top of the wall.
     */
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
                float ao = (groundAO && r == 0 && y0 < 0.5f) ? 0.78f : 1f;
                wall(px0, pz0, px1, pz1, ya, yb, tex, len * s0 * uPerM, len * s1 * uPerM, vT, vB, layer, bias, ao);
            }
        }
    }

    /** Box with four walls and a top (no bottom). Texture repeats per metre scales. */
    public void box(float ax, float ay, float az, float bx, float by, float bz, int sideTex, int topTex,
                    float uPerM, float vPerM, int layer, float bias) {
        wallGrid(ax, bz, bx, bz, ay, by, sideTex, uPerM, vPerM, 8f, 8f, layer, bias, ay < 0.5f);
        wallGrid(bx, az, ax, az, ay, by, sideTex, uPerM, vPerM, 8f, 8f, layer, bias, ay < 0.5f);
        wallGrid(bx, bz, bx, az, ay, by, sideTex, uPerM, vPerM, 8f, 8f, layer, bias, ay < 0.5f);
        wallGrid(ax, az, ax, bz, ay, by, sideTex, uPerM, vPerM, 8f, 8f, layer, bias, ay < 0.5f);
        if (topTex >= 0) {
            float light = sunLight(0, 1, 0);
            for (float z = az; z < bz - 1e-3f; z += 8f)
                for (float x = ax; x < bx - 1e-3f; x += 8f)
                    floor(x, z, Math.min(bx, x + 8f), Math.min(bz, z + 8f), by, topTex, 1f / uPerM, 1f / uPerM, layer, light, bias);
        }
    }

    /** Arbitrary textured quad, double sided, with alpha (foliage). */
    public void foliage(float[] p, float[] uv, int tex, float light) {
        l4[0] = l4[1] = l4[2] = l4[3] = light;
        quad(p, uv, l4, tex, SoftRenderer.L_OBJECT, Geo.FLAG_ALPHA | Geo.FLAG_NO_SUBDIV, 0f, true);
    }
}
