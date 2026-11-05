import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.road.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Around every interchange and every bridge over a freeway: how much ground is steeper than
 * grass can hold (bare rock, slope over 0.6), the steepest ground, and the biggest height step
 * where roads meet at a node. args: [seed]
 */
public final class EarthworkStats {
    static boolean onRoad(RoadNetwork r, float x, float z) {
        for (RoadClass k : new RoadClass[]{RoadClass.FREEWAY, RoadClass.RAMP, RoadClass.HIGHWAY, RoadClass.COUNTRY, RoadClass.TOWN})
            if (r.classDistance(x, z, k, 30f) < k.halfWidth() + 6f) return true;
        return false;
    }

    public static void main(String[] a) {
        long seed = a.length > 0 ? Long.parseLong(a[0]) : com.harlequinxd.drivehard.core.Game.DEFAULT_SEED;
        City c = new City(new com.harlequinxd.drivehard.core.tex.TextureLibrary(seed, true), seed);
        RoadNetwork r = c.roads;
        List<float[]> spots = new ArrayList<>();
        for (RoadEdge e : r.edges) {
            if (e.cls.motorway() || e.cls.city) continue;
            for (int i = 0; i < e.count() - 1; i++)
                if (e.bridge[i]) {
                    float x = e.x[i], z = e.z[i];
                    boolean near = false;
                    for (float[] s : spots) if (Math.hypot(s[0] - x, s[1] - z) < 300f) { near = true; break; }
                    if (!near) spots.add(new float[]{x, z});
                }
        }
        List<float[]> s2 = new ArrayList<>();
        java.util.TreeMap<String, Integer> why = new java.util.TreeMap<>();
        double rockArea = 0, worst = 0;
        int steepSpots = 0;
        for (float[] s : spots) {
            int rock = 0;
            float sMax = 0;
            for (float z = s[1] - 200f; z < s[1] + 200f; z += 4f)
                for (float x = s[0] - 200f; x < s[0] + 200f; x += 4f) {
                    if (onRoad(r, x, z)) continue;
                    float h = c.terrain.height(x, z), hx = c.terrain.height(x + 4f, z), hz = c.terrain.height(x, z + 4f);
                    float sl = (float) Math.hypot(hx - h, hz - h) / 4f;
                    sMax = Math.max(sMax, sl);
                    if (sl > 0.6f) {
                        rock++;
                        RoadClass near = null; float nd = 1e9f;
                        for (RoadClass k : new RoadClass[]{RoadClass.FREEWAY, RoadClass.RAMP, RoadClass.HIGHWAY, RoadClass.COUNTRY, RoadClass.TOWN, RoadClass.LANE}) {
                            float dd = r.classDistance(x, z, k, 120f) - k.halfWidth();
                            if (dd < nd) { nd = dd; near = k; }
                        }
                        String key = (near == null ? "none" : near.name()) + (Math.hypot(x - s[0], z - s[1]) < 45f ? " by the deck" : nd > 90f ? " natural" : "");
                        why.merge(key, 16, Integer::sum);
                    }
                }
            rockArea += rock * 16;
            s2.add(new float[]{s[0], s[1], rock * 16});
            worst = Math.max(worst, sMax);
            if (rock * 16 > 400) steepSpots++;
        }
        float step = 0;
        RoadNode worstN = null; RoadEdge worstE = null;
        for (RoadNode n : r.nodes) {
            if (n.kind == RoadNode.Kind.CITY) continue;
            for (int eid : n.edges) {
                RoadEdge e = r.edges.get(eid);
                float y = e.a == n.id ? e.y[0] : e.y[e.count() - 1];
                if (Math.abs(y - n.y) > step) { step = Math.abs(y - n.y); worstN = n; worstE = e; }
            }
        }
        if (worstN != null) {
            System.out.printf("worst step at %s node (%.0f, %.0f) y %.1f, %s edge end %.1f; node roads:", worstN.kind, worstN.x, worstN.z, worstN.y, worstE.cls,
                    worstE.a == worstN.id ? worstE.y[0] : worstE.y[worstE.count() - 1]);
            for (int eid : worstN.edges) { RoadEdge e = r.edges.get(eid); System.out.printf(" %s %.1f", e.cls, e.a == worstN.id ? e.y[0] : e.y[e.count() - 1]); }
            System.out.println();
        }
        System.out.printf("%d bridges over freeways: %.0f m2 of rock-steep banks (%d sites over 400 m2), steepest ground %.2f, biggest step at a junction %.2f m%n",
                spots.size(), rockArea, steepSpots, worst, step);
        System.out.println("rock-steep ground by nearest road: " + why);
        s2.sort((p, q) -> Float.compare(q[2], p[2]));
        for (int i = 0; i < Math.min(3, s2.size()); i++) System.out.printf("site %.0f %.0f: %.0f m2%n", s2.get(i)[0], s2.get(i)[1], s2.get(i)[2]);
        System.exit(0);
    }
}
