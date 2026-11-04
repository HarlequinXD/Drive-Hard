package com.harlequinxd.drivehard.core.map;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.Town;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the minimap and the island map show, prepared once: a shaded relief picture of
 * the island (sea, beaches, rocky shores, farmland, forest, mountains), every road as a
 * simplified polyline with its class, and the names of the city, towns, villages, hamlets and
 * regions. A coarse grid lists which roads cross each 2 km square so a view only draws what
 * it can see.
 */
public final class MapData {
    /** Relief picture: SIZE x SIZE ARGB pixels covering the island square. */
    public static final int SIZE = 512;
    public final int[] relief = new int[SIZE * SIZE];
    public final float x0, z0, extent;

    /** A road: class and polyline (world metres). */
    public static final class Road {
        public final RoadClass cls;
        public final float[] x, z;
        Road(RoadClass cls, float[] x, float[] z) { this.cls = cls; this.x = x; this.z = z; }
    }

    /** A map label: name, position, importance (0 city, 1 town, 2 village, 3 hamlet, 4 region). */
    public static final class Label {
        public final String name;
        public final float x, z;
        public final int rank;
        public final boolean coastal;
        Label(String name, float x, float z, int rank, boolean coastal) { this.name = name; this.x = x; this.z = z; this.rank = rank; this.coastal = coastal; }
    }

    public final List<Road> roads = new ArrayList<>();
    public final List<Label> labels = new ArrayList<>();
    /** Road indices per 2 km cell. */
    public static final float CELL = 2000f;
    public final int cells;
    private final int[][] cellRoads;

    public MapData(City city) {
        Terrain t = city.terrain;
        x0 = t.islandX0;
        z0 = t.islandZ0;
        extent = Terrain.ISLAND_SIZE;
        relief(city);
        RoadNetwork n = city.roads;
        for (RoadEdge e : n.edges) {
            if (e.count() < 2) continue;
            float[][] s = simplify(e.x, e.z, e.cls.city || e.cls == RoadClass.TOWN ? 2f : 12f);
            roads.add(new Road(e.cls, s[0], s[1]));
        }
        cells = (int) Math.ceil(extent / CELL);
        List<List<Integer>> tmp = new ArrayList<>();
        for (int k = 0; k < cells * cells; k++) tmp.add(new ArrayList<>());
        for (int r = 0; r < roads.size(); r++) {
            Road rd = roads.get(r);
            java.util.HashSet<Integer> seen = new java.util.HashSet<>();
            for (int k = 0; k < rd.x.length; k++) {
                int c = cell(rd.x[k], rd.z[k]);
                if (c >= 0 && seen.add(c)) tmp.get(c).add(r);
                if (k + 1 < rd.x.length) {   // long segments: the cell of their middle too
                    int m = cell((rd.x[k] + rd.x[k + 1]) * 0.5f, (rd.z[k] + rd.z[k + 1]) * 0.5f);
                    if (m >= 0 && seen.add(m)) tmp.get(m).add(r);
                }
            }
        }
        cellRoads = new int[cells * cells][];
        for (int k = 0; k < cells * cells; k++) {
            List<Integer> l = tmp.get(k);
            cellRoads[k] = new int[l.size()];
            for (int i = 0; i < l.size(); i++) cellRoads[k][i] = l.get(i);
        }
        labels.add(new Label(City.NAME, (city.cityX0 + city.cityX1) * 0.5f, (city.cityZ0 + city.cityZ1) * 0.5f, 0, true));
        for (Town tw : n.towns) {
            int rank = tw.size == Town.Size.TOWN ? 1 : (tw.size == Town.Size.VILLAGE ? 2 : 3);
            labels.add(new Label(tw.name, tw.centre.x, tw.centre.z, rank, tw.coastal || tw.spur));
        }
        for (Town.Region rg : n.regions) labels.add(new Label(rg.name, rg.x, rg.z, 4, false));
    }

    public int cell(float x, float z) {
        int i = (int) ((x - x0) / CELL), j = (int) ((z - z0) / CELL);
        if (i < 0 || j < 0 || i >= cells || j >= cells) return -1;
        return j * cells + i;
    }

    /** Indices of the roads in the cells overlapping a world rectangle (may repeat). */
    public void roadsIn(float ax, float az, float bx, float bz, List<Integer> out, boolean[] mark) {
        int i0 = Math.max(0, (int) ((ax - x0) / CELL)), i1 = Math.min(cells - 1, (int) ((bx - x0) / CELL));
        int j0 = Math.max(0, (int) ((az - z0) / CELL)), j1 = Math.min(cells - 1, (int) ((bz - z0) / CELL));
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++)
                for (int r : cellRoads[j * cells + i]) if (!mark[r]) { mark[r] = true; out.add(r); }
        for (int r : out) mark[r] = false;
    }

    // ------------------------------------------------------------------ relief

    private void relief(City city) {
        Terrain t = city.terrain;
        float px = extent / SIZE;
        float[] h = new float[SIZE * SIZE];
        for (int j = 0; j < SIZE; j++)
            for (int i = 0; i < SIZE; i++) {
                float x = x0 + (i + 0.5f) * px, z = z0 + (j + 0.5f) * px;
                h[j * SIZE + i] = t.coastOver(x, z) > 600f ? -30f : t.natural(x, z);
            }
        for (int j = 0; j < SIZE; j++)
            for (int i = 0; i < SIZE; i++) {
                float x = x0 + (i + 0.5f) * px, z = z0 + (j + 0.5f) * px;
                float hh = h[j * SIZE + i];
                int c;
                if (hh < 0f) {
                    // shallows lighter near the coast
                    float o = Math.min(1f, t.coastOver(x, z) / 3000f);
                    c = mix(0x5FA8C8, 0x2E6A94, o);
                } else {
                    float o = -t.coastOver(x, z);
                    if (o < 160f && t.isSand(x, z, Math.min(hh, 3f))) c = 0xE6D8A8;
                    else if (o < 160f && t.isShoreRock(x, z, Math.min(hh, 3f))) c = 0x8A8A86;
                    else if (hh > 300f) c = mix(0x5E7A4E, 0x9A9282, Math.min(1f, (hh - 300f) / 500f));
                    else if (city.countryside.farmland(x, z, hh)) c = 0xA8BC70;
                    else c = mix(0x7AA45C, 0x4E7A40, Math.min(1f, hh / 300f));
                    // hill shading: light from the north-west
                    int il = Math.max(0, i - 1), jl = Math.max(0, j - 1), ir = Math.min(SIZE - 1, i + 1), jr = Math.min(SIZE - 1, j + 1);
                    float dx = (h[j * SIZE + ir] - h[j * SIZE + il]) / (2 * px), dz = (h[jr * SIZE + i] - h[jl * SIZE + i]) / (2 * px);
                    float shade = 1f - (dx + dz) * 6f;
                    shade = Math.max(0.6f, Math.min(1.35f, shade));
                    c = scale(c, shade);
                }
                relief[j * SIZE + i] = 0xFF000000 | c;
            }
        // the city
        int ci0 = (int) ((city.cityX0 - x0) / px), ci1 = (int) ((city.cityX1 - x0) / px);
        int cj0 = (int) ((city.cityZ0 - z0) / px), cj1 = (int) ((city.cityZ1 - z0) / px);
        for (int j = cj0; j <= cj1; j++) for (int i = ci0; i <= ci1; i++) relief[j * SIZE + i] = 0xFFB8B2A8;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    /** Douglas-Peucker on a polyline. */
    private static float[][] simplify(float[] xs, float[] zs, float tol) {
        int n = xs.length;
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{0, n - 1});
        while (!st.isEmpty()) {
            int[] r = st.pop();
            float dx = xs[r[1]] - xs[r[0]], dz = zs[r[1]] - zs[r[0]], l = (float) Math.sqrt(dx * dx + dz * dz) + 1e-3f;
            float best = -1f;
            int bi = -1;
            for (int i = r[0] + 1; i < r[1]; i++) {
                float d = Math.abs((xs[i] - xs[r[0]]) * dz - (zs[i] - zs[r[0]]) * dx) / l;
                if (d > best) { best = d; bi = i; }
            }
            if (best > tol) { keep[bi] = true; st.push(new int[]{r[0], bi}); st.push(new int[]{bi, r[1]}); }
        }
        int m = 0;
        for (boolean k : keep) if (k) m++;
        float[] ox = new float[m], oz = new float[m];
        for (int i = 0, j = 0; i < n; i++) if (keep[i]) { ox[j] = xs[i]; oz[j] = zs[i]; j++; }
        return new float[][]{ox, oz};
    }
}
