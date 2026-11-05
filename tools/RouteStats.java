import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.road.*;

import java.util.*;

/**
 * How many ways there are to each town: towns whose only connection to the city runs through a
 * single road (a "bridge" in graph terms: cut it and the town is cut off), and the detour you'd
 * take if each town's quickest road were closed. args: [seed]
 */
public final class RouteStats {
    public static void main(String[] a) {
        long seed = a.length > 0 ? Long.parseLong(a[0]) : com.harlequinxd.drivehard.core.Game.DEFAULT_SEED;
        City c = new City(new com.harlequinxd.drivehard.core.tex.TextureLibrary(seed, true), seed);
        System.out.println(stats(c.roads));
        System.out.printf("added %d short cuts and %d back roads%n", c.roads.shortcuts, c.roads.backRoads);
        System.exit(0);
    }

    public static String stats(RoadNetwork r) {
        int n = r.nodes.size();
        List<int[]> adj = new ArrayList<>();
        int[][] g = new int[n][];
        int[] deg = new int[n];
        for (RoadEdge e : r.edges) if (e.cls != RoadClass.LANE) { deg[e.a]++; deg[e.b]++; }
        for (int i = 0; i < n; i++) g[i] = new int[deg[i] * 2];
        int[] fill = new int[n];
        for (RoadEdge e : r.edges) {
            if (e.cls == RoadClass.LANE) continue;
            g[e.a][fill[e.a]++] = e.b; g[e.a][fill[e.a]++] = e.id;
            g[e.b][fill[e.b]++] = e.a; g[e.b][fill[e.b]++] = e.id;
        }
        // bridges (cut edges): iterative Tarjan
        int[] disc = new int[n], low = new int[n];
        Arrays.fill(disc, -1);
        boolean[] cut = new boolean[r.edges.size()];
        int time = 0;
        for (int s = 0; s < n; s++) {
            if (disc[s] >= 0) continue;
            Deque<int[]> st = new ArrayDeque<>();   // node, parent edge, next index
            st.push(new int[]{s, -1, 0});
            disc[s] = low[s] = time++;
            while (!st.isEmpty()) {
                int[] f = st.peek();
                int u = f[0];
                if (f[2] < g[u].length) {
                    int v = g[u][f[2]], eid = g[u][f[2] + 1];
                    f[2] += 2;
                    if (eid == f[1]) continue;
                    if (disc[v] < 0) { disc[v] = low[v] = time++; st.push(new int[]{v, eid, 0}); }
                    else low[u] = Math.min(low[u], disc[v]);
                } else {
                    st.pop();
                    if (!st.isEmpty()) {
                        int p = st.peek()[0];
                        low[p] = Math.min(low[p], low[u]);
                        if (low[u] > disc[p]) cut[f[1]] = true;
                    }
                }
            }
        }
        // 2-edge-connected components
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int nc = 0;
        for (int s = 0; s < n; s++) {
            if (comp[s] >= 0) continue;
            Deque<Integer> q = new ArrayDeque<>();
            q.add(s); comp[s] = nc;
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int k = 0; k < g[u].length; k += 2) {
                    int v = g[u][k], eid = g[u][k + 1];
                    if (cut[eid] || comp[v] >= 0) continue;
                    comp[v] = nc; q.add(v);
                }
            }
            nc++;
        }
        int cityNode = -1;
        for (RoadNode nd : r.nodes) if (nd.kind == RoadNode.Kind.CITY && g[nd.id].length >= 6) { cityNode = nd.id; break; }
        int single = 0, total = 0, singleTowns = 0, towns = 0;
        StringBuilder sb = new StringBuilder();
        for (Town t : r.towns) {
            total++;
            if (t.size == Town.Size.TOWN) towns++;
            if (comp[t.centre.id] != comp[cityNode]) {
                single++;
                if (t.size == Town.Size.TOWN) singleTowns++;
            }
        }
        int cityComp = 0, cuts = 0;
        for (int i = 0; i < n; i++) if (comp[i] == comp[cityNode]) cityComp++;
        for (boolean b : cut) if (b) cuts++;
        int zeroDeg = 0;
        for (Town t : r.towns) if (g[t.centre.id].length == 0) zeroDeg++;
        System.out.printf("nodes %d, city comp size %d, cut edges %d, components %d, town centres with no roads %d%n", n, cityComp, cuts, nc, zeroDeg);
        // the detour: close the rural roads of each settlement's quickest route to the city (by
        // travel time), and see how much longer the next-best way takes
        int[] buckets = new int[5];
        double sum = 0;
        int counted = 0;
        for (Town t : r.towns) {
            int[] via = new int[n];
            double best = dijkstra(r, g, cityNode, t.centre.id, null, via);
            if (best == Double.MAX_VALUE) continue;
            boolean[] closed = new boolean[r.edges.size()];
            for (int v = t.centre.id; v != cityNode && via[v] >= 0; ) {
                RoadEdge e = r.edges.get(via[v]);
                if (!e.cls.city && e.cls != RoadClass.TOWN) closed[e.id] = true;
                v = e.a == v ? e.b : e.a;
            }
            double alt = dijkstra(r, g, cityNode, t.centre.id, closed, null);
            double ratio = alt == Double.MAX_VALUE ? 99 : alt / best;
            sum += Math.min(ratio, 5);
            counted++;
            buckets[ratio < 1.3 ? 0 : ratio < 1.6 ? 1 : ratio < 2.2 ? 2 : ratio < 99 ? 3 : 4]++;
        }
        System.out.printf("next-best route vs quickest: <1.3x %d, 1.3-1.6x %d, 1.6-2.2x %d, >2.2x %d, none %d (mean %.2fx)%n",
                buckets[0], buckets[1], buckets[2], buckets[3], buckets[4], sum / counted);
        return String.format("%d settlements (%d towns): %d have only one way to the city (%d of the towns)", total, towns, single, singleTowns);
    }

    /** Travel time (s) from a to b by road (speed limits), avoiding closed edges; fills via[] with the edge taken into each node. */
    static double dijkstra(RoadNetwork r, int[][] g, int a, int b, boolean[] closed, int[] via) {
        int n = g.length;
        double[] d = new double[n];
        Arrays.fill(d, Double.MAX_VALUE);
        if (via != null) Arrays.fill(via, -1);
        d[a] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>(Comparator.comparingDouble(x -> x[0]));
        q.add(new double[]{0, a});
        while (!q.isEmpty()) {
            double[] f = q.poll();
            int u = (int) f[1];
            if (f[0] > d[u]) continue;
            if (u == b) return d[u];
            for (int k = 0; k < g[u].length; k += 2) {
                int v = g[u][k], eid = g[u][k + 1];
                if (closed != null && closed[eid]) continue;
                RoadEdge e = r.edges.get(eid);
                double nd = d[u] + e.len / e.cls.speed;
                if (nd < d[v]) { d[v] = nd; if (via != null) via[v] = eid; q.add(new double[]{nd, v}); }
            }
        }
        return Double.MAX_VALUE;
    }
}
