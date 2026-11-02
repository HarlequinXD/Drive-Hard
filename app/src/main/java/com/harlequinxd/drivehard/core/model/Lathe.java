package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds smooth low-poly "lathe" shapes: a stack of elliptical rings along the local Y axis,
 * closed with a pole vertex at each end. Every body part, the hair and the shoes are built
 * from these, which keeps the modeller tiny while still giving rounded organic forms.
 *
 * Ring angle 0 points toward +Z (the front of the character).
 */
public final class Lathe {

    /** Colour of a triangle, given its centroid in the lathe's local space. */
    public interface ColorFn { int color(float x, float y, float z); }

    /** Whether to keep a triangle (used to cut the face opening out of the hair). */
    public interface KeepFn { boolean keep(float x, float y, float z); }

    private static final class Ring {
        final float y, rx, rz, cx, cz;
        Ring(float y, float rx, float rz, float cx, float cz) {
            this.y = y; this.rx = rx; this.rz = rz; this.cx = cx; this.cz = cz;
        }
    }

    private final List<Ring> rings = new ArrayList<>();
    private int sides = 10;
    private ColorFn colorFn = (x, y, z) -> 0xFF00FF;
    private KeepFn keepFn = null;
    private Mat4 transform = new Mat4();
    private float shine = 0f;
    private boolean foot = false;
    private boolean capStart = true, capEnd = true;

    public Lathe sides(int n) { sides = n; return this; }
    public Lathe ring(float y, float rx, float rz) { rings.add(new Ring(y, rx, rz, 0f, 0f)); return this; }
    public Lathe ring(float y, float rx, float rz, float cx, float cz) { rings.add(new Ring(y, rx, rz, cx, cz)); return this; }
    public Lathe color(int rgb) { final int c = rgb; colorFn = (x, y, z) -> c; return this; }
    public Lathe color(ColorFn fn) { colorFn = fn; return this; }
    public Lathe keep(KeepFn fn) { keepFn = fn; return this; }
    public Lathe transform(Mat4 m) { transform = m; return this; }
    public Lathe shine(float s) { shine = s; return this; }
    public Lathe foot() { foot = true; return this; }
    public Lathe openEnds() { capStart = false; capEnd = false; return this; }

    public Part build(int bone) {
        int nr = rings.size();
        int n = sides;
        int ringVerts = nr * n;
        int vcount = ringVerts + 2;
        float[] p = new float[vcount * 3];
        for (int r = 0; r < nr; r++) {
            Ring g = rings.get(r);
            for (int s = 0; s < n; s++) {
                double a = (Math.PI * 2.0 * s) / n;
                int i = (r * n + s) * 3;
                p[i] = g.cx + g.rx * (float) Math.sin(a);
                p[i + 1] = g.y;
                p[i + 2] = g.cz + g.rz * (float) Math.cos(a);
            }
        }
        Ring first = rings.get(0), last = rings.get(nr - 1);
        int pole0 = ringVerts, pole1 = ringVerts + 1;
        p[pole0 * 3] = first.cx; p[pole0 * 3 + 1] = first.y; p[pole0 * 3 + 2] = first.cz;
        p[pole1 * 3] = last.cx; p[pole1 * 3 + 1] = last.y; p[pole1 * 3 + 2] = last.cz;

        List<int[]> tris = new ArrayList<>();
        for (int r = 0; r < nr - 1; r++) {
            for (int s = 0; s < n; s++) {
                int a = r * n + s, b = r * n + (s + 1) % n;
                int c = (r + 1) * n + s, d = (r + 1) * n + (s + 1) % n;
                tris.add(new int[]{a, c, b});
                tris.add(new int[]{b, c, d});
            }
        }
        if (capStart) for (int s = 0; s < n; s++) tris.add(new int[]{pole0, s, (s + 1) % n});
        if (capEnd) {
            int base = (nr - 1) * n;
            for (int s = 0; s < n; s++) tris.add(new int[]{pole1, base + (s + 1) % n, base + s});
        }

        // Colour + keep, evaluated in local space.
        List<int[]> kept = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (int[] t : tris) {
            float cx = (p[t[0] * 3] + p[t[1] * 3] + p[t[2] * 3]) / 3f;
            float cy = (p[t[0] * 3 + 1] + p[t[1] * 3 + 1] + p[t[2] * 3 + 1]) / 3f;
            float cz = (p[t[0] * 3 + 2] + p[t[1] * 3 + 2] + p[t[2] * 3 + 2]) / 3f;
            if (keepFn != null && !keepFn.keep(cx, cy, cz)) continue;
            kept.add(t);
            cols.add(colorFn.color(cx, cy, cz));
        }

        // Smooth normals (area weighted), in local space.
        float[] nrm = new float[vcount * 3];
        for (int[] t : tris) {
            float ax = p[t[1] * 3] - p[t[0] * 3], ay = p[t[1] * 3 + 1] - p[t[0] * 3 + 1], az = p[t[1] * 3 + 2] - p[t[0] * 3 + 2];
            float bx = p[t[2] * 3] - p[t[0] * 3], by = p[t[2] * 3 + 1] - p[t[0] * 3 + 1], bz = p[t[2] * 3 + 2] - p[t[0] * 3 + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            for (int k = 0; k < 3; k++) {
                nrm[t[k] * 3] += nx; nrm[t[k] * 3 + 1] += ny; nrm[t[k] * 3 + 2] += nz;
            }
        }
        // Make normals point outward: winding depends on profile direction, so test against
        // the radial direction from each ring centre and flip everything if needed.
        float dot = 0f;
        for (int r = 0; r < nr; r++) {
            Ring g = rings.get(r);
            for (int s = 0; s < n; s++) {
                int i = (r * n + s) * 3;
                dot += nrm[i] * (p[i] - g.cx) + nrm[i + 2] * (p[i + 2] - g.cz);
            }
        }
        if (dot < 0f) for (int i = 0; i < nrm.length; i++) nrm[i] = -nrm[i];
        for (int v = 0; v < vcount; v++) {
            float nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9f) { nx = 0; ny = 1; nz = 0; len = 1; }
            nrm[v * 3] = nx / len; nrm[v * 3 + 1] = ny / len; nrm[v * 3 + 2] = nz / len;
        }

        // Transform into bone space.
        float[] tp = new float[vcount * 3];
        float[] tn = new float[vcount * 3];
        for (int v = 0; v < vcount; v++) {
            transform.transformPoint(p[v * 3], p[v * 3 + 1], p[v * 3 + 2], tp, v * 3);
            transform.transformDir(nrm[v * 3], nrm[v * 3 + 1], nrm[v * 3 + 2], tn, v * 3);
        }
        int[] ti = new int[kept.size() * 3];
        int[] tc = new int[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            int[] t = kept.get(i);
            ti[i * 3] = t[0]; ti[i * 3 + 1] = t[1]; ti[i * 3 + 2] = t[2];
            tc[i] = cols.get(i);
        }
        return new Part(bone, tp, tn, ti, tc, shine, foot);
    }

    /** Convenience: an ellipsoid built from latitude rings. */
    public static Lathe ellipsoid(float rx, float ry, float rz, int latRings, int sides) {
        Lathe l = new Lathe().sides(sides);
        for (int i = 1; i < latRings; i++) {
            double t = Math.PI * i / latRings;           // 0..PI from bottom to top
            float y = (float) (-Math.cos(t)) * ry;
            float s = (float) Math.sin(t);
            l.ring(y, rx * s, rz * s);
        }
        // poles placed by ring list ends; extend them to the true tips
        l.rings.add(0, new Ring(-ry, 0.0001f, 0.0001f, 0, 0));
        l.rings.add(new Ring(ry, 0.0001f, 0.0001f, 0, 0));
        return l;
    }
}
