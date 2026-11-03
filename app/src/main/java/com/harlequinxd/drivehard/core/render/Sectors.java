package com.harlequinxd.drivehard.core.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Buildings as a 2.5D sector map, in the manner of Doom and Build: no polygons. Each chunk
 * holds sectors (convex footprints whose floor is a plane: a flat or sloped roof, raised above
 * the ground) and walls (linedefs round each sector, textured in tiles per storey: plain wall,
 * windows, doors, shopfronts). A BSP tree over the walls answers "which walls does this ray
 * cross, nearest first" for the column renderer, which draws the walls and roofs together
 * with the Mode 7 ground.
 */
public final class Sectors {

    /** A raised area: footprint polygon and floor plane y = a + b x + c z (the roof surface). */
    public static final class Sector {
        public final float[] poly;           // x, z pairs (convex)
        public float a, b, c;
        /** Ground floor level: wall storeys count from here. */
        public float base;
        /** Roof covering: texture, tile axes (unit), metres per tile along and down the slope. */
        public int tex;
        public float ux, uz, vx, vz, us, vs;
        public float light = 1f;
        public float minX, minZ, maxX, maxZ;

        public Sector(float[] poly) {
            this.poly = poly;
            minX = minZ = Float.MAX_VALUE;
            maxX = maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < poly.length; i += 2) {
                minX = Math.min(minX, poly[i]); maxX = Math.max(maxX, poly[i]);
                minZ = Math.min(minZ, poly[i + 1]); maxZ = Math.max(maxZ, poly[i + 1]);
            }
        }

        public float y(float x, float z) { return a + b * x + c * z; }

        public boolean contains(float x, float z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
            boolean in = false;
            int n = poly.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float xi = poly[i * 2], zi = poly[i * 2 + 1], xj = poly[j * 2], zj = poly[j * 2 + 1];
                if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
            }
            return in;
        }

        public float area() {
            float s = 0f;
            int n = poly.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) s += poly[j * 2] * poly[i * 2 + 1] - poly[i * 2] * poly[j * 2 + 1];
            return Math.abs(s) * 0.5f;
        }
    }

    /** Opening kinds of a wall tile. */
    public static final byte PLAIN = 0, FULL = 1, WINDOW = 2, DOOR = 3, SMALL = 4, GARDEN = 5;

    /**
     * How a wall face is tiled: storeys of {@code storeyH} from the sector's base, each split
     * into columns of tiles; above the top storey (gables, parapets) and between openings the
     * wall is plain material, below the base it is the foundation.
     */
    public static final class Tiles {
        /** Plain wall material on the ground storey, above it, and the foundation below the base. */
        public int mat, matUp, found;
        public float matScale = 2f;
        /** Height of the ground storey (shops are taller) and of the storeys above. */
        public float groundH = 3f, storeyH = 3f;
        /** Length of the whole wall face the tile columns are laid along (pieces of it share it). */
        public float len;
        public int floors;
        public int[] cols;
        public byte[][] kind;
        public int[][] tex;
        public float winW = 1.4f, winH = 1.2f, sill = 0.95f, doorW = 0.95f, doorH = 2.15f;

        public Tiles(int floors) {
            this.floors = Math.max(0, floors);
            floors = this.floors;
            cols = new int[floors];
            kind = new byte[floors][];
            tex = new int[floors][];
        }

        /** Storey s gets n tiles, all of one kind and texture to start with. */
        public void storey(int s, int n, byte k, int t) {
            cols[s] = Math.max(1, n);
            kind[s] = new byte[cols[s]];
            tex[s] = new int[cols[s]];
            java.util.Arrays.fill(kind[s], k);
            java.util.Arrays.fill(tex[s], t);
        }
    }

    /** A wall: the edge (x0,z0)-(x1,z1) of {@code front}; outward normal (nx, nz); back = what is outside. */
    public static final class Wall {
        public final float x0, z0, x1, z1, nx, nz, len;
        public final Sector front;
        /** The sector beyond this wall (null: open ground), for walls of sectors standing inside others. */
        public final Sector back;
        public final Tiles tiles;
        public float light = 1f;
        /** Where this wall starts along its tiled face (a face cut into pieces keeps its tiling). */
        public float uOff;

        public Wall(float x0, float z0, float x1, float z1, float nx, float nz, Sector front, Sector back, Tiles tiles) {
            this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1;
            this.nx = nx; this.nz = nz;
            this.len = (float) Math.hypot(x1 - x0, z1 - z0);
            this.front = front; this.back = back; this.tiles = tiles;
        }
    }

    /** A piece of a wall after BSP splitting (u0: distance of its start along the wall). */
    static final class Seg {
        final Wall w;
        final float ax, az, bx, bz, u0;
        Seg(Wall w, float ax, float az, float bx, float bz, float u0) { this.w = w; this.ax = ax; this.az = az; this.bx = bx; this.bz = bz; this.u0 = u0; }
    }

    /** BSP node: partition line through (px, pz) along (dx, dz); segs lying on it; bounds of the subtree. */
    static final class Node {
        float px, pz, dx, dz;
        Node front, back;
        Seg[] on;
        float minX, minZ, maxX, maxZ;
    }

    public final List<Sector> sectors = new ArrayList<>();
    public final List<Wall> walls = new ArrayList<>();
    Node root;
    public float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    public boolean isEmpty() { return walls.isEmpty(); }

    /** Builds the BSP tree (call once all walls are in). */
    public void build() {
        List<Seg> segs = new ArrayList<>();
        for (Wall w : walls) {
            segs.add(new Seg(w, w.x0, w.z0, w.x1, w.z1, 0f));
            minX = Math.min(minX, Math.min(w.x0, w.x1)); maxX = Math.max(maxX, Math.max(w.x0, w.x1));
            minZ = Math.min(minZ, Math.min(w.z0, w.z1)); maxZ = Math.max(maxZ, Math.max(w.z0, w.z1));
        }
        root = segs.isEmpty() ? null : node(segs, 0);
    }

    private static Node node(List<Seg> segs, int depth) {
        Node n = new Node();
        n.minX = n.minZ = Float.MAX_VALUE;
        n.maxX = n.maxZ = -Float.MAX_VALUE;
        for (Seg s : segs) {
            n.minX = Math.min(n.minX, Math.min(s.ax, s.bx)); n.maxX = Math.max(n.maxX, Math.max(s.ax, s.bx));
            n.minZ = Math.min(n.minZ, Math.min(s.az, s.bz)); n.maxZ = Math.max(n.maxZ, Math.max(s.az, s.bz));
        }
        // pick the splitter: few segs cut in two, sides balanced (a sample of candidates)
        Seg best = null;
        int bestScore = Integer.MAX_VALUE;
        int step = Math.max(1, segs.size() / 12);
        for (int i = 0; i < segs.size(); i += step) {
            Seg c = segs.get(i);
            float dx = c.bx - c.ax, dz = c.bz - c.az;
            int f = 0, b = 0, split = 0;
            for (Seg s : segs) {
                float sa = side(c.ax, c.az, dx, dz, s.ax, s.az), sb = side(c.ax, c.az, dx, dz, s.bx, s.bz);
                if (Math.abs(sa) < 1e-3f && Math.abs(sb) < 1e-3f) continue;
                if (sa >= -1e-3f && sb >= -1e-3f) f++;
                else if (sa <= 1e-3f && sb <= 1e-3f) b++;
                else split++;
            }
            int score = split * 4 + Math.abs(f - b);
            if (score < bestScore) { bestScore = score; best = c; }
        }
        if (segs.size() <= 2 || depth > 40) {
            n.on = segs.toArray(new Seg[0]);
            n.dx = 0f; n.dz = 0f;
            return n;
        }
        n.px = best.ax; n.pz = best.az; n.dx = best.bx - best.ax; n.dz = best.bz - best.az;
        List<Seg> fr = new ArrayList<>(), bk = new ArrayList<>(), on = new ArrayList<>();
        for (Seg s : segs) {
            float sa = side(n.px, n.pz, n.dx, n.dz, s.ax, s.az), sb = side(n.px, n.pz, n.dx, n.dz, s.bx, s.bz);
            boolean za = Math.abs(sa) < 1e-3f, zb = Math.abs(sb) < 1e-3f;
            if (za && zb) on.add(s);
            else if ((sa >= 0f || za) && (sb >= 0f || zb)) fr.add(s);
            else if ((sa <= 0f || za) && (sb <= 0f || zb)) bk.add(s);
            else {
                // cut the seg where it crosses the partition line
                float t = sa / (sa - sb);
                float mx = s.ax + (s.bx - s.ax) * t, mz = s.az + (s.bz - s.az) * t;
                float um = s.u0 + (float) Math.hypot(mx - s.ax, mz - s.az);
                Seg p = new Seg(s.w, s.ax, s.az, mx, mz, s.u0), q = new Seg(s.w, mx, mz, s.bx, s.bz, um);
                if (sa > 0f) { fr.add(p); bk.add(q); } else { bk.add(p); fr.add(q); }
            }
        }
        if (fr.isEmpty() && bk.isEmpty()) { n.on = on.toArray(new Seg[0]); n.dx = 0f; n.dz = 0f; return n; }
        n.on = on.toArray(new Seg[0]);
        if (!fr.isEmpty()) n.front = node(fr, depth + 1);
        if (!bk.isEmpty()) n.back = node(bk, depth + 1);
        return n;
    }

    /** Signed side of (x, z) relative to the line through (px, pz) along (dx, dz) (positive: left). */
    static float side(float px, float pz, float dx, float dz, float x, float z) {
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        return l < 1e-6f ? 0f : (dx * (z - pz) - dz * (x - px)) / l;
    }

    /** Innermost sector containing (x, z), or null. */
    public Sector find(float x, float z) {
        Sector best = null;
        float ba = Float.MAX_VALUE;
        for (Sector s : sectors) {
            if (!s.contains(x, z)) continue;
            float a = s.area();
            if (a < ba) { ba = a; best = s; }
        }
        return best;
    }

    // ------------------------------------------------------------------ ray queries

    /** Collected wall crossings of one ray (per rendering thread). */
    public static final class Hits {
        public int n;
        public float[] t = new float[64], u = new float[64];
        public Wall[] w = new Wall[64];

        void add(float tt, float uu, Wall ww) {
            if (n == t.length) {
                t = java.util.Arrays.copyOf(t, n * 2);
                u = java.util.Arrays.copyOf(u, n * 2);
                w = java.util.Arrays.copyOf(w, n * 2);
            }
            t[n] = tt; u[n] = uu; w[n] = ww; n++;
        }

        /** Sorts by distance; at equal distance, leaving a sector comes before entering the next. */
        public void sort(float dx, float dz) {
            for (int i = 1; i < n; i++) {
                float ti = t[i], ui = u[i];
                Wall wi = w[i];
                boolean enterI = wi.nx * dx + wi.nz * dz < 0f;
                int j = i - 1;
                while (j >= 0) {
                    boolean enterJ = w[j].nx * dx + w[j].nz * dz < 0f;
                    boolean after = t[j] > ti + 1e-3f || (Math.abs(t[j] - ti) <= 1e-3f && enterJ && !enterI);
                    if (!after) break;
                    t[j + 1] = t[j]; u[j + 1] = u[j]; w[j + 1] = w[j];
                    j--;
                }
                t[j + 1] = ti; u[j + 1] = ui; w[j + 1] = wi;
            }
        }
    }

    /** Adds every crossing of the ray (ox, oz) + t (dx, dz), 0 &lt; t &lt; tMax, nearest subtree first. */
    public void cast(float ox, float oz, float dx, float dz, float tMax, Hits out) {
        if (root == null || !boxHit(minX, minZ, maxX, maxZ, ox, oz, dx, dz, tMax)) return;
        cast(root, ox, oz, dx, dz, tMax, out);
    }

    private void cast(Node n, float ox, float oz, float dx, float dz, float tMax, Hits out) {
        if (!boxHit(n.minX, n.minZ, n.maxX, n.maxZ, ox, oz, dx, dz, tMax)) return;
        boolean leaf = n.dx == 0f && n.dz == 0f;
        boolean frontSide = leaf || side(n.px, n.pz, n.dx, n.dz, ox, oz) >= 0f;
        Node near = frontSide ? n.front : n.back, far = frontSide ? n.back : n.front;
        if (near != null) cast(near, ox, oz, dx, dz, tMax, out);
        for (Seg s : n.on) hit(s, ox, oz, dx, dz, tMax, out);
        if (far != null) cast(far, ox, oz, dx, dz, tMax, out);
    }

    private static void hit(Seg s, float ox, float oz, float dx, float dz, float tMax, Hits out) {
        float ex = s.bx - s.ax, ez = s.bz - s.az;
        float den = dx * ez - dz * ex;
        if (Math.abs(den) < 1e-9f) return;
        float wx = s.ax - ox, wz = s.az - oz;
        float t = (wx * ez - wz * ex) / den;
        float v = (wx * dz - wz * dx) / den;
        if (t <= 0.05f || t >= tMax || v < -1e-4f || v > 1f + 1e-4f) return;
        float segLen = (float) Math.sqrt(ex * ex + ez * ez);
        out.add(t, s.u0 + v * segLen, s.w);
    }

    /** Slab test: does the ray hit the box within (0, tMax)? */
    static boolean boxHit(float x0, float z0, float x1, float z1, float ox, float oz, float dx, float dz, float tMax) {
        float t0 = 0f, t1 = tMax;
        if (Math.abs(dx) < 1e-9f) { if (ox < x0 || ox > x1) return false; }
        else {
            float a = (x0 - ox) / dx, b = (x1 - ox) / dx;
            if (a > b) { float t = a; a = b; b = t; }
            t0 = Math.max(t0, a); t1 = Math.min(t1, b);
            if (t0 > t1) return false;
        }
        if (Math.abs(dz) < 1e-9f) { return oz >= z0 && oz <= z1; }
        float a = (z0 - oz) / dz, b = (z1 - oz) / dz;
        if (a > b) { float t = a; a = b; b = t; }
        t0 = Math.max(t0, a); t1 = Math.min(t1, b);
        return t0 <= t1;
    }
}
