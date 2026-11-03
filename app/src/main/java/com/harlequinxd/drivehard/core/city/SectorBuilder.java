package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Sectors;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a building as sectors, from the ground up. A building is a set of masses (blocks with a
 * footprint rectangle, wall faces tiled per storey and a roof); each roof is a few convex roof
 * pieces whose floor plane is the roof surface (two for a gable, four for a hip, one for a mono
 * pitch, a flat deck inside a parapet ring). Masses are added tallest first and later masses are
 * cut away where an earlier one stands, so no two sectors overlap: where a lower roof meets a
 * higher mass, the ray caster draws the higher mass's wall above the lower roof.
 *
 * <p>Pieces can be given in a local frame (u along the frontage, v away from the street) that
 * maps to the world by a quarter turn, so lots facing any street use the same code.</p>
 */
final class SectorBuilder {
    private final Sectors out;
    /** Footprints of the masses already built (world rectangles x0, z0, x1, z1). */
    private final List<float[]> taken = new ArrayList<>();
    // local frame: world = o + u U + v V
    private float ox, oz, Ux = 1f, Uz = 0f, Vx = 0f, Vz = 1f;

    /** A tiled wall face: world endpoints A to B (tiles counted from A) and its tiling. */
    static final class Face {
        final float ax, az, bx, bz;
        final Sectors.Tiles tiles;
        Face(float ax, float az, float bx, float bz, Sectors.Tiles tiles) {
            this.ax = ax; this.az = az; this.bx = bx; this.bz = bz; this.tiles = tiles;
            tiles.len = (float) Math.hypot(bx - ax, bz - az);
        }
    }

    SectorBuilder(Sectors out) { this.out = out; }

    /** World coordinates = local (u, v); call {@link #frame} for rotated lots. */
    void world() { ox = 0f; oz = 0f; Ux = 1f; Uz = 0f; Vx = 0f; Vz = 1f; }

    /** Local frame: origin and the world directions of u and v (unit, perpendicular). */
    void frame(float ox, float oz, float ux, float uz, float vx, float vz) {
        this.ox = ox; this.oz = oz; Ux = ux; Uz = uz; Vx = vx; Vz = vz;
    }

    float wx(float u, float v) { return ox + u * Ux + v * Vx; }
    float wz(float u, float v) { return oz + u * Uz + v * Vz; }

    /** A wall face from local A to local B. */
    Face face(float au, float av, float bu, float bv, Sectors.Tiles t) {
        return new Face(wx(au, av), wz(au, av), wx(bu, bv), wz(bu, bv), t);
    }

    /**
     * Adds a roof piece: local convex polygon (u, v pairs), roof plane y = A + B u + C v, roof
     * texture with tiles of {@code tile} metres down the slope, faces of the mass it belongs to
     * (edges lying on them get their tiles) and the tiling of its other edges.
     */
    void piece(float[] local, float A, float B, float C, float base, int roofTex, float tile, float light,
               Face[] faces, Sectors.Tiles inner) {
        float[] poly = new float[local.length];
        for (int i = 0; i < local.length; i += 2) {
            poly[i] = wx(local[i], local[i + 1]);
            poly[i + 1] = wz(local[i], local[i + 1]);
        }
        float b = B * Ux + C * Vx, c = B * Uz + C * Vz;
        float a = A - b * ox - c * oz;
        List<float[]> parts = new ArrayList<>();
        parts.add(poly);
        for (float[] r : taken) {
            List<float[]> next = new ArrayList<>();
            for (float[] p : parts) subtract(p, r, next);
            parts = next;
        }
        for (float[] p : parts) {
            p = clean(p);
            if (p == null) continue;
            Sectors.Sector s = new Sectors.Sector(p);
            s.a = a; s.b = b; s.c = c;
            s.base = base;
            s.tex = roofTex;
            float g = (float) Math.sqrt(b * b + c * c);
            if (g < 1e-4f) { s.ux = 1f; s.uz = 0f; s.vx = 0f; s.vz = 1f; s.us = s.vs = tile; }
            else {
                s.vx = -b / g; s.vz = -c / g;          // down the slope
                s.ux = -s.vz; s.uz = s.vx;             // along the contour
                s.us = tile;
                s.vs = tile / (float) Math.sqrt(1f + g * g);
            }
            float nl = (float) Math.sqrt(b * b + 1f + c * c);
            s.light = light >= 0f ? light : GeoBuilder.sunLight(-b / nl, 1f / nl, -c / nl);
            out.sectors.add(s);
            walls(s, faces, inner);
        }
    }

    /** The mass with this local footprint is complete: later masses are cut away where it stands. */
    void taken(float u0, float v0, float u1, float v1) {
        float x0 = wx(u0, v0), z0 = wz(u0, v0), x1 = wx(u1, v1), z1 = wz(u1, v1);
        taken.add(new float[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
    }

    /** Four faces of a local rectangle, outside on the left going round: front (v0), u0 side, back, u1 side. */
    Face[] rectFaces(float u0, float v0, float u1, float v1, Sectors.Tiles front, Sectors.Tiles left, Sectors.Tiles back, Sectors.Tiles right) {
        return new Face[]{face(u1, v0, u0, v0, front), face(u0, v0, u0, v1, left), face(u0, v1, u1, v1, back), face(u1, v1, u1, v0, right)};
    }

    /** A flat-topped block (local rectangle): one sector at height top, the same tiles all round. */
    void block(float u0, float v0, float u1, float v1, float base, float top, int topTex, Sectors.Tiles sides) {
        Face[] f = rectFaces(u0, v0, u1, v1, sides, sides, sides, sides);
        piece(new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, top, 0f, 0f, base, topTex, 2f, -1f, f, sides);
        taken(u0, v0, u1, v1);
    }

    /** A wall face of plain material only (no storeys): parapets, sheds, fences, roof boxes. */
    static Sectors.Tiles plain(int mat, int found) {
        Sectors.Tiles t = new Sectors.Tiles(0);
        t.mat = mat; t.matUp = mat; t.found = found;
        return t;
    }

    // ------------------------------------------------------------------ walls

    private void walls(Sectors.Sector s, Face[] faces, Sectors.Tiles inner) {
        float[] p = s.poly;
        int n = p.length / 2;
        float cx = 0f, cz = 0f;
        for (int i = 0; i < n; i++) { cx += p[i * 2]; cz += p[i * 2 + 1]; }
        cx /= n; cz /= n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float x0 = p[i * 2], z0 = p[i * 2 + 1], x1 = p[j * 2], z1 = p[j * 2 + 1];
            float dx = x1 - x0, dz = z1 - z0, len = (float) Math.hypot(dx, dz);
            if (len < 0.02f) continue;
            float nx = dz / len, nz = -dx / len;
            if (nx * ((x0 + x1) * 0.5f - cx) + nz * ((z0 + z1) * 0.5f - cz) < 0f) { nx = -nx; nz = -nz; }
            Face f = onFace(faces, x0, z0, x1, z1);
            Sectors.Wall w;
            if (f != null) {
                // lay the wall along its face, so the tiles line up across pieces
                float fdx = f.bx - f.ax, fdz = f.bz - f.az;
                if (dx * fdx + dz * fdz < 0f) { float t = x0; x0 = x1; x1 = t; t = z0; z0 = z1; z1 = t; }
                w = new Sectors.Wall(x0, z0, x1, z1, nx, nz, s, null, f.tiles);
                w.uOff = (float) Math.hypot(x0 - f.ax, z0 - f.az);
            } else {
                w = new Sectors.Wall(x0, z0, x1, z1, nx, nz, s, null, inner);
            }
            w.light = GeoBuilder.sunLight(nx, 0f, nz);
            out.walls.add(w);
        }
    }

    private static Face onFace(Face[] faces, float x0, float z0, float x1, float z1) {
        if (faces == null) return null;
        for (Face f : faces) {
            float dx = f.bx - f.ax, dz = f.bz - f.az, l = (float) Math.hypot(dx, dz);
            if (l < 1e-4f) continue;
            float d0 = Math.abs(dx * (z0 - f.az) - dz * (x0 - f.ax)) / l, d1 = Math.abs(dx * (z1 - f.az) - dz * (x1 - f.ax)) / l;
            if (d0 > 0.02f || d1 > 0.02f) continue;
            float t0 = (dx * (x0 - f.ax) + dz * (z0 - f.az)) / l, t1 = (dx * (x1 - f.ax) + dz * (z1 - f.az)) / l;
            if (Math.min(t0, t1) > -0.02f && Math.max(t0, t1) < l + 0.02f) return f;
        }
        return null;
    }

    // ------------------------------------------------------------------ polygon clipping

    /** Convex polygon minus an axis-aligned rectangle: up to four convex parts outside it. */
    private static void subtract(float[] p, float[] r, List<float[]> out) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < p.length; i += 2) {
            minX = Math.min(minX, p[i]); maxX = Math.max(maxX, p[i]);
            minZ = Math.min(minZ, p[i + 1]); maxZ = Math.max(maxZ, p[i + 1]);
        }
        if (maxX <= r[0] + 1e-3f || minX >= r[2] - 1e-3f || maxZ <= r[1] + 1e-3f || minZ >= r[3] - 1e-3f) { out.add(p); return; }
        float[] rest = p;
        // x < r0 | x > r2 | z < r1 | z > r3, each cut off what is left
        float[][] cut = {{-1, 0, -r[0]}, {1, 0, r[2]}, {0, -1, -r[1]}, {0, 1, r[3]}};
        for (float[] c : cut) {
            if (rest == null) return;
            // outside part: c.x*x + c.z*z >= c.k ; inside remainder: <= c.k
            float[] o = clip(rest, c[0], c[1], c[2]);
            if (o != null) out.add(o);
            rest = clip(rest, -c[0], -c[1], -c[2]);
        }
    }

    /** Sutherland-Hodgman: keeps the part with nx*x + nz*z >= k (null if nothing left). */
    private static float[] clip(float[] p, float nx, float nz, float k) {
        int n = p.length / 2;
        float[] o = new float[(n + 2) * 2];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = p[i * 2], az = p[i * 2 + 1], bx = p[j * 2], bz = p[j * 2 + 1];
            float da = nx * ax + nz * az - k, db = nx * bx + nz * bz - k;
            if (da >= 0f) { o = grow(o, m); o[m++] = ax; o[m++] = az; }
            if ((da >= 0f) != (db >= 0f)) {
                float t = da / (da - db);
                o = grow(o, m);
                o[m++] = ax + (bx - ax) * t; o[m++] = az + (bz - az) * t;
            }
        }
        if (m < 6) return null;
        float[] q = java.util.Arrays.copyOf(o, m);
        return area(q) < 0.01f ? null : q;
    }

    private static float[] grow(float[] o, int m) { return m + 2 > o.length ? java.util.Arrays.copyOf(o, o.length * 2) : o; }

    private static float area(float[] p) {
        float s = 0f;
        int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) s += p[j * 2] * p[i * 2 + 1] - p[i * 2] * p[j * 2 + 1];
        return Math.abs(s) * 0.5f;
    }

    /** Drops repeated points; null for slivers. */
    private static float[] clean(float[] p) {
        int n = p.length / 2;
        float[] o = new float[p.length];
        int m = 0;
        for (int i = 0; i < n; i++) {
            float x = p[i * 2], z = p[i * 2 + 1];
            if (m > 0 && Math.abs(o[m - 2] - x) < 1e-3f && Math.abs(o[m - 1] - z) < 1e-3f) continue;
            o[m++] = x; o[m++] = z;
        }
        if (m >= 4 && Math.abs(o[0] - o[m - 2]) < 1e-3f && Math.abs(o[1] - o[m - 1]) < 1e-3f) m -= 2;
        if (m < 6) return null;
        float[] q = java.util.Arrays.copyOf(o, m);
        return area(q) < 0.02f ? null : q;
    }
}
