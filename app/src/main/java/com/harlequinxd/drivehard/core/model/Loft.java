package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a smooth body by lofting rounded-rectangle (superellipse) cross sections placed along
 * the Z axis. Used for car bodies and cabins.
 */
public final class Loft {
    private final List<float[]> st = new ArrayList<>();   // z, halfWidth, y0, y1
    private int sides = 12;
    private float power = 4f;
    private Lathe.ColorFn color = (x, y, z) -> 0xFF00FF;
    private Mat4 transform = new Mat4();
    private boolean emissive;

    public Loft sides(int n) { sides = n; return this; }
    /** 2 = ellipse, higher = boxier. */
    public Loft power(float p) { power = p; return this; }
    public Loft station(float z, float hw, float y0, float y1) { st.add(new float[]{z, hw, y0, y1}); return this; }
    public Loft color(Lathe.ColorFn c) { color = c; return this; }
    public Loft color(int c) { final int cc = c; color = (x, y, z) -> cc; return this; }
    public Loft transform(Mat4 m) { transform = m; return this; }
    public Loft emissive() { emissive = true; return this; }

    public Part build() {
        int nr = st.size(), n = sides;
        int ringVerts = nr * n, vcount = ringVerts + 2;
        float[] p = new float[vcount * 3];
        float e = 2f / power;
        for (int r = 0; r < nr; r++) {
            float[] s = st.get(r);
            float cy = (s[2] + s[3]) * 0.5f, ry = (s[3] - s[2]) * 0.5f;
            for (int k = 0; k < n; k++) {
                double a = Math.PI * 2 * k / n;
                double c = Math.cos(a), sn = Math.sin(a);
                float x = (float) (Math.signum(sn) * Math.pow(Math.abs(sn), e)) * s[1];
                float y = cy + (float) (Math.signum(c) * Math.pow(Math.abs(c), e)) * ry;
                int i = (r * n + k) * 3;
                p[i] = x; p[i + 1] = y; p[i + 2] = s[0];
            }
        }
        float[] f = st.get(0), l = st.get(nr - 1);
        int pole0 = ringVerts, pole1 = ringVerts + 1;
        p[pole0 * 3] = 0; p[pole0 * 3 + 1] = (f[2] + f[3]) * 0.5f; p[pole0 * 3 + 2] = f[0];
        p[pole1 * 3] = 0; p[pole1 * 3 + 1] = (l[2] + l[3]) * 0.5f; p[pole1 * 3 + 2] = l[0];

        List<int[]> tris = new ArrayList<>();
        for (int r = 0; r < nr - 1; r++)
            for (int k = 0; k < n; k++) {
                int a = r * n + k, b = r * n + (k + 1) % n, c = (r + 1) * n + k, d = (r + 1) * n + (k + 1) % n;
                tris.add(new int[]{a, c, b});
                tris.add(new int[]{b, c, d});
            }
        for (int k = 0; k < n; k++) tris.add(new int[]{pole0, k, (k + 1) % n});
        int base = (nr - 1) * n;
        for (int k = 0; k < n; k++) tris.add(new int[]{pole1, base + (k + 1) % n, base + k});

        float[] nrm = new float[vcount * 3];
        for (int[] t : tris) {
            float ax = p[t[1] * 3] - p[t[0] * 3], ay = p[t[1] * 3 + 1] - p[t[0] * 3 + 1], az = p[t[1] * 3 + 2] - p[t[0] * 3 + 2];
            float bx = p[t[2] * 3] - p[t[0] * 3], by = p[t[2] * 3 + 1] - p[t[0] * 3 + 1], bz = p[t[2] * 3 + 2] - p[t[0] * 3 + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            for (int k = 0; k < 3; k++) { nrm[t[k] * 3] += nx; nrm[t[k] * 3 + 1] += ny; nrm[t[k] * 3 + 2] += nz; }
        }
        // orient outward (compare with direction from the station centre)
        float dot = 0;
        for (int r = 0; r < nr; r++) {
            float cy = (st.get(r)[2] + st.get(r)[3]) * 0.5f;
            for (int k = 0; k < n; k++) {
                int i = (r * n + k) * 3;
                dot += nrm[i] * p[i] + nrm[i + 1] * (p[i + 1] - cy);
            }
        }
        if (dot < 0) for (int i = 0; i < nrm.length; i++) nrm[i] = -nrm[i];
        for (int v = 0; v < vcount; v++) {
            float x = nrm[v * 3], y = nrm[v * 3 + 1], z = nrm[v * 3 + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-9f) { x = 0; y = 1; z = 0; len = 1; }
            nrm[v * 3] = x / len; nrm[v * 3 + 1] = y / len; nrm[v * 3 + 2] = z / len;
        }
        float[] tp = new float[vcount * 3], tn = new float[vcount * 3];
        for (int v = 0; v < vcount; v++) {
            transform.transformPoint(p[v * 3], p[v * 3 + 1], p[v * 3 + 2], tp, v * 3);
            transform.transformDir(nrm[v * 3], nrm[v * 3 + 1], nrm[v * 3 + 2], tn, v * 3);
        }
        int[] ti = new int[tris.size() * 3], tc = new int[tris.size()];
        for (int i = 0; i < tris.size(); i++) {
            int[] t = tris.get(i);
            ti[i * 3] = t[0]; ti[i * 3 + 1] = t[1]; ti[i * 3 + 2] = t[2];
            float cx = (p[t[0] * 3] + p[t[1] * 3] + p[t[2] * 3]) / 3f;
            float cy = (p[t[0] * 3 + 1] + p[t[1] * 3 + 1] + p[t[2] * 3 + 1]) / 3f;
            float cz = (p[t[0] * 3 + 2] + p[t[1] * 3 + 2] + p[t[2] * 3 + 2]) / 3f;
            tc[i] = color.color(cx, cy, cz);
        }
        Part part = new Part(0, tp, tn, ti, tc, 0.35f, false);
        part.emissive = emissive;
        return part;
    }
}
