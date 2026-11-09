package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.RoadNode;
import com.harlequinxd.drivehard.core.road.Town;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the whole island and lists what would look or drive wrong: roads above or under the
 * ground, bridge decks barely off the ground or buried, roads too steep, junctions meeting at a
 * knife angle, settlements cut off from the network, buildings on a road or on top of each other,
 * lots on a slope, buildings and car parks with no way in from a road.
 *
 * Errors are things a player would see as a bug; warnings are things that look odd.
 */
public final class MapAudit {
    public static final class Problem {
        public final String kind;
        public final boolean error;
        public final float x, z;
        public final String what;

        Problem(String kind, boolean error, float x, float z, String what) {
            this.kind = kind; this.error = error; this.x = x; this.z = z; this.what = what;
        }
    }

    public final List<Problem> problems = new ArrayList<>();
    public final Map<String, int[]> counts = new LinkedHashMap<>();
    private final City city;
    private final RoadNetwork roads;
    private final Terrain terrain;

    public MapAudit(City city) {
        this.city = city;
        this.roads = city.roads;
        this.terrain = city.terrain;
    }

    private void add(String kind, boolean error, float x, float z, String what) {
        problems.add(new Problem(kind, error, x, z, what));
        counts.computeIfAbsent(kind, k -> new int[2])[error ? 0 : 1]++;
    }

    public City city() { return city; }

    public int errors() { int n = 0; for (Problem p : problems) if (p.error) n++; return n; }

    public int warnings() { return problems.size() - errors(); }

    public MapAudit run() {
        roadSurfaces();
        grades();
        junctionAngles();
        connectivity();
        sites();
        return this;
    }

    // ------------------------------------------------------------------ roads

    /** Painted roads must lie on the ground they are painted on; decks must clear it. */
    private void roadSurfaces() {
        for (RoadEdge e : roads.edges) {
            if (e.cls.city) continue;
            int last = -99;
            for (int i = 0; i < e.count() - 1; i++) {
                float mx = (e.x[i] + e.x[i + 1]) * 0.5f, mz = (e.z[i] + e.z[i + 1]) * 0.5f;
                float my = (e.y[i] + e.y[i + 1]) * 0.5f;
                if (e.bridge[i]) {
                    // the first and last deck pieces rest on their abutments
                    if (i == 0 || i == e.bridge.length - 1 || !e.bridge[i - 1] || !e.bridge[i + 1]) continue;
                    // (the ground under a deck is as the roads shape it: a deck may span a cutting)
                    float gap = my - terrain.height(mx, mz);
                    if (gap < 0.5f && i - last > 6) { add("buried deck", true, mx, mz, String.format("%s e%d deck %.1f m vs ground", e.cls, e.id, gap)); last = i; }
                    continue;
                }
                // the junction areas at the ends are drawn from the node, not the road
                float sm = (e.s[i] + e.s[i + 1]) * 0.5f;
                if (sm < e.clearA + 3f || sm > e.len - e.clearB - 3f) continue;
                float g = terrain.exact(mx, mz);
                float d = my - g;
                if (Math.abs(d) > 0.45f && i - last > 6) {
                    add(d > 0 ? "road above ground" : "road under ground", true, mx, mz, String.format("%s e%d %.2f m", e.cls, e.id, d));
                    last = i;
                }
            }
        }
    }

    /** Steepest stretch of every road compared with what its class allows. */
    private void grades() {
        for (RoadEdge e : roads.edges) {
            if (e.cls.city) continue;
            float worst = 0f, wx = 0, wz = 0;
            // over 20 m so single samples don't count
            for (int i = 0, j = 0; i < e.count(); i++) {
                while (j < e.count() - 1 && e.s[j] - e.s[i] < 20f) j++;
                float ds = e.s[j] - e.s[i];
                if (ds < 15f) break;
                float g = Math.abs(e.y[j] - e.y[i]) / ds;
                if (g > worst) { worst = g; wx = e.x[i]; wz = e.z[i]; }
            }
            float allowed = Math.max(e.cls.grade, 0.055f);
            if (worst > allowed * 1.6f + 0.01f)
                add("too steep", worst > 0.2f, wx, wz, String.format("%s e%d %.0f%% (allowed %.0f%%)", e.cls, e.id, worst * 100, allowed * 100));
        }
    }

    /** Roads meeting at a junction must not run into each other at a knife angle. */
    private void junctionAngles() {
        float[] o = new float[2];
        for (RoadNode n : roads.nodes) {
            if (n.dead || n.kind == RoadNode.Kind.SPLIT || n.kind == RoadNode.Kind.CITY || n.edges.length < 2) continue;
            int m = n.edges.length;
            float[] ax = new float[m], az = new float[m];
            boolean motor = false;
            for (int k = 0; k < m; k++) {
                RoadEdge e = roads.edges.get(n.edges[k]);
                motor |= e.cls.motorway();
                far(e, n, o);
                ax[k] = o[0]; az[k] = o[1];
            }
            if (motor) continue;
            float worst = 180f;
            for (int a = 0; a < m; a++)
                for (int b = a + 1; b < m; b++) {
                    float c = ax[a] * ax[b] + az[a] * az[b];
                    worst = Math.min(worst, (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, c)))));
                }
            if (worst < 25f) add("sharp junction", worst < 12f, n.x, n.z, String.format("node %d roads meet at %.0f deg", n.id, worst));
        }
    }

    /** Unit direction a road leaves node n, judged 15 m out. */
    private static void far(RoadEdge e, RoadNode n, float[] out) {
        float[] p = new float[5];
        float d = Math.min(15f, e.len * 0.5f);
        e.point(n.id == e.a ? d : e.len - d, p);
        float dx = p[0] - n.x, dz = p[1] - n.z, l = (float) Math.sqrt(dx * dx + dz * dz);
        out[0] = l > 0 ? dx / l : 1f; out[1] = l > 0 ? dz / l : 0f;
    }

    /** Every settlement can be driven to from the city, and the freeways reach out across the island. */
    private void connectivity() {
        float fw = roads.lengthKm(RoadClass.FREEWAY);
        if (fw < 50f) add("network", true, terrain.centreX, terrain.centreZ, String.format("only %.0f km of freeway", fw));
        float hw = roads.lengthKm(RoadClass.HIGHWAY);
        if (hw < 150f) add("network", true, terrain.centreX, terrain.centreZ, String.format("only %.0f km of highway", hw));
        int nn = roads.nodes.size();
        int[] comp = new int[nn];
        java.util.Arrays.fill(comp, -1);
        int[] stack = new int[nn];
        int nc = 0;
        int[] size = new int[nn];
        for (int s = 0; s < nn; s++) {
            if (comp[s] >= 0 || roads.nodes.get(s).dead || roads.nodes.get(s).edges.length == 0) continue;
            int sp = 0;
            stack[sp++] = s;
            comp[s] = nc;
            while (sp > 0) {
                RoadNode n = roads.nodes.get(stack[--sp]);
                size[nc]++;
                for (int eid : n.edges) {
                    int o = roads.edges.get(eid).other(n.id);
                    if (comp[o] < 0) { comp[o] = nc; stack[sp++] = o; }
                }
            }
            nc++;
        }
        int main = 0;
        for (int c = 1; c < nc; c++) if (size[c] > size[main]) main = c;
        for (Town t : roads.towns)
            if (comp[t.centre.id] != main) add("cut off", true, t.centre.x, t.centre.z, t.size + " " + t.name + " has no road to the rest of the island");
        for (int c = 0; c < nc; c++) {
            if (c == main) continue;
            for (int s = 0; s < nn; s++) if (comp[s] == c) {
                RoadNode n = roads.nodes.get(s);
                add("cut off", true, n.x, n.z, "road network piece of " + size[c] + " nodes not joined to the rest");
                break;
            }
        }
    }

    // ------------------------------------------------------------------ buildings

    private static final String[] KIND = {"farm", "house", "apartment", "convenience store", "lot", "town hall", "post office", "clinic",
            "shop", "supermarket", "school", "shrine", "petrol station", "convenience store", "harbour", "vegetable plot", "work shed", "greenhouse", "inn"};

    private void sites() {
        List<Countryside.Site> all = new ArrayList<>();
        city.countryside.forEachSite(all::add);
        // overlaps: a grid of 32 m cells
        java.util.HashMap<Long, List<Countryside.Site>> grid = new java.util.HashMap<>();
        for (Countryside.Site s : all) {
            if (s.kind == Countryside.HARBOUR) continue;
            for (int gz = (int) Math.floor(s.z0 / 32f); gz <= (int) Math.floor(s.z1 / 32f); gz++)
                for (int gx = (int) Math.floor(s.x0 / 32f); gx <= (int) Math.floor(s.x1 / 32f); gx++)
                    grid.computeIfAbsent(((long) gx << 32) ^ (gz & 0xFFFFFFFFL), k -> new ArrayList<>()).add(s);
        }
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (List<Countryside.Site> cell : grid.values())
            for (int a = 0; a < cell.size(); a++)
                for (int b = a + 1; b < cell.size(); b++) {
                    Countryside.Site p = cell.get(a), q = cell.get(b);
                    float ox = Math.min(p.x1, q.x1) - Math.max(p.x0, q.x0), oz = Math.min(p.z1, q.z1) - Math.max(p.z0, q.z0);
                    if (ox > 0.5f && oz > 0.5f) {
                        long key = ((long) System.identityHashCode(p) << 32) ^ System.identityHashCode(q);
                        if (seen.add(key)) add("overlap", true, (Math.max(p.x0, q.x0) + Math.min(p.x1, q.x1)) * 0.5f,
                                (Math.max(p.z0, q.z0) + Math.min(p.z1, q.z1)) * 0.5f, KIND[p.kind] + " overlaps " + KIND[q.kind]);
                    }
                }
        for (Countryside.Site s : all) {
            if (s.kind == Countryside.HARBOUR) continue;
            float cx = (s.x0 + s.x1) * 0.5f, cz = (s.z0 + s.z1) * 0.5f;
            String k = KIND[s.kind];
            // a road through the plot
            boolean onRoad = crossedByRoad(s);
            if (onRoad) add("on a road", true, cx, cz, k + " plot has a road through it");
            // slope across the plot
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int j = 0; j <= 2; j++)
                for (int i = 0; i <= 2; i++) {
                    float h = terrain.height(s.x0 + (s.x1 - s.x0) * i / 2f, s.z0 + (s.z1 - s.z0) * j / 2f);
                    lo = Math.min(lo, h); hi = Math.max(hi, h);
                }
            float grade = (hi - lo) / (float) Math.hypot(s.x1 - s.x0, s.z1 - s.z0);
            if (hi - lo > 2.5f && grade > 0.10f) add("steep plot", grade > 0.14f, cx, cz, String.format("%s plot %.1f m out of level (%.0f%%)", k, hi - lo, grade * 100));
            // a way in from a road on the side the building faces
            float[] f = front(s);
            float gap = roads.roadClearance(f[0], f[1], 40f);
            if (gap > 6f) add("no access", true, f[0], f[1], String.format("%s faces no road (nearest %.0f m)", k, gap));
            else {
                float step = Math.abs(terrain.height(f[0], f[1]) - roadHeightNear(f[0], f[1]));
                if (step > 2.5f) add("steep driveway", step > 5f, f[0], f[1], String.format("%s %.1f m above/below its road", k, step));
            }
            // car parks open onto a road
            if (s.groundPacked != null) {
                float[] g = s.groundPacked;
                for (int i = 0; i < g.length; i += 6) {
                    if ((int) g[i + 4] != city.lib.parking) continue;
                    int face = (int) g[i + 5];
                    if (face < 0) { add("car park", false, (g[i] + g[i + 2]) * 0.5f, (g[i + 1] + g[i + 3]) * 0.5f, k + " car park has no entrance side"); continue; }
                    float ex = face < 2 ? (g[i] + g[i + 2]) * 0.5f : (face == 2 ? g[i] : g[i + 2]);
                    float ez = face >= 2 ? (g[i + 1] + g[i + 3]) * 0.5f : (face == 0 ? g[i + 1] : g[i + 3]);
                    if (roads.roadClearance(ex, ez, 20f) > 4f) add("car park", true, ex, ez, k + " car park entrance is not on a road");
                }
            }
        }
    }

    /**
     * True if some road's surface runs over the plot, apart from the plot's own driveway lane
     * (a farm or house lane ends inside its yard).
     */
    private boolean crossedByRoad(Countryside.Site s) {
        final boolean[] hit = {false};
        float x0 = s.x0 + 0.4f, z0 = s.z0 + 0.4f, x1 = s.x1 - 0.4f, z1 = s.z1 - 0.4f;
        roads.forEachPiece(x0 - 20f, z0 - 20f, x1 + 20f, z1 + 20f, (e, i) -> {
            if (hit[0] || e.cls.city) return;
            if (e.cls == RoadClass.LANE) {
                RoadNode a = roads.nodes.get(e.a), b = roads.nodes.get(e.b);
                if (inside(s, a.x, a.z, 4f) || inside(s, b.x, b.z, 4f)) return;
            }
            float hw = e.cls.halfWidth();
            // closest approach of the piece to the rectangle
            for (int k = 0; k <= 8; k++) {
                float t = k / 8f, x = e.x[i] + (e.x[i + 1] - e.x[i]) * t, z = e.z[i] + (e.z[i + 1] - e.z[i]) * t;
                float dx = Math.max(0f, Math.max(x0 - x, x - x1)), dz = Math.max(0f, Math.max(z0 - z, z - z1));
                if (dx * dx + dz * dz < hw * hw) { hit[0] = true; return; }
            }
        });
        return hit[0];
    }

    private static boolean inside(Countryside.Site s, float x, float z, float m) {
        return x > s.x0 - m && x < s.x1 + m && z > s.z0 - m && z < s.z1 + m;
    }

    /** Middle of the edge of a plot facing its road (a little outside it). */
    private static float[] front(Countryside.Site s) {
        // a roadside convenience store's car park is in front of its plot
        float o = 0.5f + (s.kind == Countryside.KONBINI ? 7f : 0f);
        switch (s.face) {
            case 0: return new float[]{(s.x0 + s.x1) * 0.5f, s.z0 - o};
            case 1: return new float[]{(s.x0 + s.x1) * 0.5f, s.z1 + o};
            case 2: return new float[]{s.x0 - o, (s.z0 + s.z1) * 0.5f};
            default: return new float[]{s.x1 + o, (s.z0 + s.z1) * 0.5f};
        }
    }

    private float roadHeightNear(float x, float z) {
        float[] o = new float[3];
        if (!roads.nearest(x, z, 40f, o)) return terrain.height(x, z);
        RoadEdge e = roads.edges.get((int) o[0]);
        float[] p = new float[5];
        e.point(o[1], p);
        return p[2];
    }

    // ------------------------------------------------------------------ report

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("map check: %d errors, %d warnings%n", errors(), warnings()));
        for (Map.Entry<String, int[]> e : counts.entrySet())
            sb.append(String.format("  %-20s %6d errors %6d warnings%n", e.getKey(), e.getValue()[0], e.getValue()[1]));
        return sb.toString();
    }

    /** A plot as the map drawing sees it. */
    public interface PlotVisitor { void plot(float x0, float z0, float x1, float z1, int kind, float frontX, float frontZ, float[] ground); }

    /** Every building plot (for drawing the map). */
    public void forEachPlot(PlotVisitor v) {
        city.countryside.forEachSite(s -> {
            float[] f = front(s);
            v.plot(s.x0, s.z0, s.x1, s.z1, s.kind, f[0], f[1], s.groundPacked);
        });
    }

}
