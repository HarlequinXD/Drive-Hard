package com.harlequinxd.drivehard.core.gfx;

import java.util.Arrays;

/**
 * Growable, non-indexed triangle list. Vertex layout (8 floats):
 * x, y, z, u, v, r, g, b  — colours are pre-baked vertex lighting (1 = full bright).
 */
public final class MeshData {
    public static final int STRIDE = 8;
    private float[] data = new float[4096];
    private int size;

    public float[] array() { return data; }
    public int floatCount() { return size; }
    public int vertexCount() { return size / STRIDE; }
    public float[] toArray() { return Arrays.copyOf(data, size); }

    private void ensure(int more) {
        if (size + more > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + more));
    }

    public void vertex(float x, float y, float z, float u, float v, float r, float g, float b) {
        ensure(STRIDE);
        data[size++] = x; data[size++] = y; data[size++] = z;
        data[size++] = u; data[size++] = v;
        data[size++] = r; data[size++] = g; data[size++] = b;
    }

    /**
     * Quad p0..p3 (counter-clockwise when viewed from the front). The tile's UV rect is mapped
     * p0=(u0,v1) p1=(u1,v1) p2=(u1,v0) p3=(u0,v0), rotated by {@code rot} quarter turns.
     * {@code c} holds one brightness per corner.
     */
    public void quad(float[] p0, float[] p1, float[] p2, float[] p3, int tile, int rot, float[] c) {
        float[] r = ProcTextures.uv(tile);
        float[][] uv = {{r[0], r[3]}, {r[2], r[3]}, {r[2], r[1]}, {r[0], r[1]}};
        float[][] p = {p0, p1, p2, p3};
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int i : order) {
            float[] t = uv[(i + rot) & 3];
            vertex(p[i][0], p[i][1], p[i][2], t[0], t[1], c[i], c[i], c[i]);
        }
    }
}
