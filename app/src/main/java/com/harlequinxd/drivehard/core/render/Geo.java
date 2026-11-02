package com.harlequinxd.drivehard.core.render;

import java.util.Arrays;

/**
 * Compact static polygon list (triangles or quads). Per polygon:
 * 4 xyz positions, 4 uv pairs (in texture repeats), 4 vertex light values, face normal
 * (zero = double sided), texture id, draw layer, flags and an ordering-table depth bias.
 */
public final class Geo {
    public static final int FLAG_ALPHA = 1, FLAG_NO_SUBDIV = 2;
    /** Small detail (markings, rooftop clutter): culled beyond the renderer's detail distance. */
    public static final int FLAG_DETAIL = 4;
    /** Sort by the polygon's farthest point instead of its average (terrain under standing objects). */
    public static final int FLAG_SORT_FAR = 8;

    public int count;
    /** Tall object batch (building): eligible for terrain occlusion culling. */
    public boolean object;
    public float[] pos = new float[12 * 64];
    public float[] uv = new float[8 * 64];
    public float[] light = new float[4 * 64];
    public float[] nrm = new float[3 * 64];
    public float[] bias = new float[64];
    public int[] tex = new int[64];
    /** bits 0-2: vertex count (3/4), bits 4-7: layer, bits 8+: flags */
    public int[] meta = new int[64];

    public float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
    public float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    private void grow() {
        int n = tex.length * 2;
        pos = Arrays.copyOf(pos, n * 12);
        uv = Arrays.copyOf(uv, n * 8);
        light = Arrays.copyOf(light, n * 4);
        nrm = Arrays.copyOf(nrm, n * 3);
        bias = Arrays.copyOf(bias, n);
        tex = Arrays.copyOf(tex, n);
        meta = Arrays.copyOf(meta, n);
    }

    public int vertexCount(int i) { return meta[i] & 7; }
    public int layer(int i) { return (meta[i] >> 4) & 15; }
    public int flags(int i) { return meta[i] >>> 8; }

    /**
     * Adds a polygon. {@code p} has 3 or 4 xyz points (counter-clockwise seen from the front),
     * {@code t} matching uv pairs, {@code l} matching light values. Normal is computed from the
     * winding unless {@code doubleSided}.
     */
    public int add(float[] p, float[] t, float[] l, int texId, int layer, int flags, float depthBias, boolean doubleSided) {
        if (count == tex.length) grow();
        int nv = p.length / 3;
        int i = count++;
        for (int k = 0; k < 4; k++) {
            int s = Math.min(k, nv - 1);
            pos[i * 12 + k * 3] = p[s * 3];
            pos[i * 12 + k * 3 + 1] = p[s * 3 + 1];
            pos[i * 12 + k * 3 + 2] = p[s * 3 + 2];
            uv[i * 8 + k * 2] = t[s * 2];
            uv[i * 8 + k * 2 + 1] = t[s * 2 + 1];
            light[i * 4 + k] = l[s];
            float x = p[s * 3], y = p[s * 3 + 1], z = p[s * 3 + 2];
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }
        if (doubleSided) {
            nrm[i * 3] = nrm[i * 3 + 1] = nrm[i * 3 + 2] = 0f;
        } else {
            float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
            float bx = p[6] - p[0], by = p[7] - p[1], bz = p[8] - p[2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9f) len = 1f;
            nrm[i * 3] = nx / len; nrm[i * 3 + 1] = ny / len; nrm[i * 3 + 2] = nz / len;
        }
        tex[i] = texId;
        bias[i] = depthBias;
        meta[i] = nv | (layer << 4) | (flags << 8);
        return i;
    }
}
