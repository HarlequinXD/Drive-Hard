package com.harlequinxd.drivehard.core.road;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.tex.Noise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * The island's road graph. Every road is an edge holding a spline (sampled into a polyline) and
 * a smoothed height profile; edges meet at nodes. It contains the city street grid, villages
 * scattered across the farmland linked by country roads and highways, coastal highways, and
 * three freeways leaving the city with diamond interchanges (one-way slip roads). Country roads
 * that cross each other get a junction; roads that cross a freeway go over it on a bridge.
 *
 * Roads outside the city are cut into the terrain: {@link #shapeTerrain} pulls the ground toward
 * each road's profile (embankments and cuttings).
 */
public final class RoadNetwork implements java.io.Serializable {
    public final List<RoadNode> nodes = new ArrayList<>();
    public final List<RoadEdge> edges = new ArrayList<>();
    /** Village centre nodes (for farms and houses). */
    public final List<RoadNode> villages = new ArrayList<>();
    public final int[][] cityNode;

    private final City city;
    private final Terrain terrain;
    private final Random rnd;
    private final int seed;

    // spatial index over road pieces: CSR lists per 256 m cell, items = edge << 16 | piece
    public static final float CELL = 256f;
    private float gx0, gz0;
    private int gnx, gnz;
    private int[] cellStart, cellItems;
    private static final float MARGIN = 84f;
    /** How far from a road's edge its embankments and cuttings may reach. */
    private static final float BANK_REACH = 80f;

    public RoadNetwork(City city, Terrain terrain, long seed) {
        this.city = city;
        this.terrain = terrain;
        this.seed = (int) seed;
        this.rnd = new Random(seed * 31 + 7);
        cityNode = new int[city.nbx + 1][city.nbz + 1];
        long t0 = System.nanoTime();
        cityGrid();
        settlements();
        List<RoadEdge> fw = new ArrayList<>();
        network(fw);
        for (RoadEdge f : fw) interchanges(f);
        bridgeInterchanges();
        router = null;
        resolveCrossings();
        profiles();
        buildIndex();
        farms();
        buildIndex();
        farmTracks();
        splay();
        clearances();
        buildIndex();
        indexNodes();
        for (RoadNode nd : nodes) ring(nd);
        farmCells = null;                 // only needed while placing farms
        buildTime = (System.nanoTime() - t0) / 1e6f;
    }

    public float buildTime;

    // ------------------------------------------------------------------ construction helpers

    private RoadNode addNode(float x, float z, RoadNode.Kind kind) {
        RoadNode nd = new RoadNode(nodes.size(), x, z, Math.max(2.5f, terrain.natural(x, z)), kind);
        nodes.add(nd);
        return nd;
    }

    private RoadEdge addEdge(RoadClass cls, int a, int b, float[] px, float[] pz) {
        RoadEdge e = new RoadEdge(edges.size(), cls, a, b, px, pz, cls.oneWay());
        edges.add(e);
        nodes.get(a).addEdge(e.id);
        nodes.get(b).addEdge(e.id);
        return e;
    }

    private void cityGrid() {
        City c = city;
        for (int j = 0; j <= c.nbz; j++)
            for (int i = 0; i <= c.nbx; i++) {
                RoadNode nd = addNode((c.rx0[i] + c.rx1[i]) * 0.5f, (c.rz0[j] + c.rz1[j]) * 0.5f, RoadNode.Kind.CITY);
                nd.y = terrain.natural(nd.x, nd.z);
                nd.ci = i; nd.cj = j;
                nd.signal = c.traffic.id(i, j);
                cityNode[i][j] = nd.id;
            }
        for (int i = 0; i <= c.nbx; i++)
            for (int j = 0; j < c.nbz; j++) {
                RoadNode a = nodes.get(cityNode[i][j]), b = nodes.get(cityNode[i][j + 1]);
                if (!c.nsStreet(i, j)) continue;
                RoadEdge e = addEdge(c.avenueX[i] ? RoadClass.AVENUE : RoadClass.STREET, a.id, b.id,
                        new float[]{a.x, b.x}, new float[]{a.z, b.z});
                e.ns = true;
                e.clearA = (c.rz1[j] - c.rz0[j]) * 0.5f;
                e.clearB = (c.rz1[j + 1] - c.rz0[j + 1]) * 0.5f;
            }
        for (int j = 0; j <= c.nbz; j++)
            for (int i = 0; i < c.nbx; i++) {
                RoadNode a = nodes.get(cityNode[i][j]), b = nodes.get(cityNode[i + 1][j]);
                if (!c.ewStreet(i, j)) continue;
                RoadEdge e = addEdge(c.avenueZ[j] ? RoadClass.AVENUE : RoadClass.STREET, a.id, b.id,
                        new float[]{a.x, b.x}, new float[]{a.z, b.z});
                e.ns = false;
                e.clearA = (c.rx1[i] - c.rx0[i]) * 0.5f;
                e.clearB = (c.rx1[i + 1] - c.rx0[i + 1]) * 0.5f;
            }
    }

    // ------------------------------------------------------------------ links

    /** Joins a node to the nearest village, preferring villages in direction (hx,hz) if given. */
    private void linkToVillage(int nodeId, RoadClass cls, float hx, float hz) {
        RoadNode a = nodes.get(nodeId);
        RoadNode best = null;
        float bd = Float.MAX_VALUE;
        for (RoadNode v : villages) {
            float dx = v.x - a.x, dz = v.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (!Float.isNaN(hx) && (dx * hx + dz * hz) < d * 0.3f) continue;
            if (d < bd) { bd = d; best = v; }
        }
        if (best == null || bd > 22000f) return;
        float sx = Float.isNaN(hx) ? 0f : hx, sz = Float.isNaN(hz) ? 0f : hz;
        route(cls, a, gate(best, a.x, a.z), sx, sz, 0f, 0f);
    }

    private void cityConnectors() {
        City c = city;
        for (float offset : new float[]{-2500f, 0f, 2500f})
            linkToVillage(cityBoundaryNode(-1f, 0f, offset), RoadClass.COUNTRY, -1f, 0f);
        for (float offset : new float[]{-2500f, 2500f})
            linkToVillage(cityBoundaryNode(0f, -1f, offset), RoadClass.COUNTRY, 0f, -1f);
        for (float offset : new float[]{-2500f, 2500f})
            linkToVillage(cityBoundaryNode(0f, 1f, offset), RoadClass.COUNTRY, 0f, 1f);
    }

    /** Finds a connected city-grid node on the seed-shaped urban edge in the requested direction. */
    private int cityBoundaryNode(float dx, float dz, float lateralOffset) {
        float len = (float) Math.hypot(dx, dz);
        dx /= len; dz /= len;
        float lx = -dz, lz = dx;
        float cx = (city.cityX0 + city.cityX1) * 0.5f, cz = (city.cityZ0 + city.cityZ1) * 0.5f;
        int best = -1;
        float bestScore = -Float.MAX_VALUE;
        for (int i = 0; i <= city.nbx; i++)
            for (int j = 0; j <= city.nbz; j++) {
                RoadNode node = nodes.get(cityNode[i][j]);
                if (node.edges.length == 0 || !city.urban(node.x, node.z)) continue;
                float rx = node.x - cx, rz = node.z - cz;
                float along = rx * dx + rz * dz;
                float lateral = rx * lx + rz * lz;
                float score = along - 0.5f * Math.abs(lateral - lateralOffset);
                if (score > bestScore) { bestScore = score; best = node.id; }
            }
        if (best < 0) throw new IllegalStateException("No connected city road at the urban boundary");
        return best;
    }

    // ------------------------------------------------------------------ towns & regions

    public final List<Town> towns = new ArrayList<>();
    public final List<Town.Region> regions = new ArrayList<>();
    private final java.util.HashMap<Integer, Town> townByNode = new java.util.HashMap<>();
    private PlaceNames names;

    /** A fresh unique place name (suburbs of the city). */
    public String newPlaceName() { return names.town(); }
    /** Street spacing in towns (centre line to centre line). */
    public static final float TOWN_SX = 145f, TOWN_SZ = 145f;
    private static final int LARGE_TOWN_GRID = 49, TOWN_GRID = 15;
    private static final int LARGE_VILLAGE_GRID = 21, VILLAGE_GRID = 9;
    private boolean hasLargeTownGrid, hasLargeVillageGrid;

    /** Names the island's regions: the 3x3 parts of the island, each centred on land. */
    private void regions() {
        int rn = 3;
        float cell = Terrain.ISLAND_SIZE / rn;
        for (int j = 0; j < rn; j++)
            for (int i = 0; i < rn; i++) {
                float bx = 0, bz = 0, bd = -1;
                for (int k = 0; k < 40; k++) {
                    float x = terrain.islandX0 + cell * (i + Noise.rnd(i * 41 + k, j, seed + 1500));
                    float z = terrain.islandZ0 + cell * (j + Noise.rnd(i, j * 37 + k, seed + 1501));
                    float d = -terrain.coastOver(x, z);
                    if (d > bd) { bd = d; bx = x; bz = z; }
                }
                if (bd < 1500f) continue;          // this part of the square is (nearly) all sea
                boolean coastal = bd < 7000f;
                boolean high = terrain.natural(bx, bz) > 140f;
                regions.add(new Town.Region(names.region(coastal, high), bx, bz));
            }
    }

    /**
     * Places towns, villages and hamlets where people would build them: flat land below the
     * hills, in valleys and on the coastal plain, around bays, spaced out like real settlements.
     * The best sites become towns, then villages; the rest are hamlets.
     */
    private void settlements() {
        names = new PlaceNames(seed);
        regions();
        Random r = new Random(seed * 41L + 9);
        List<float[]> cand = new ArrayList<>();           // x, z, score, inland distance
        float step = 1300f;
        for (float z = terrain.islandZ0 + step * 0.5f; z < terrain.islandZ1; z += step)
            for (float x = terrain.islandX0 + step * 0.5f; x < terrain.islandX1; x += step) {
                if (terrain.coastOver(x, z) > 2000f) continue;
                float best = -1f, bx = 0, bz = 0, bd = 0;
                for (int k = 0; k < 4; k++) {
                    float px = x + (r.nextFloat() - 0.5f) * step, pz = z + (r.nextFloat() - 0.5f) * step;
                    float d = -terrain.coastOver(px, pz);
                    if (d < 550f || terrain.cityDistance(px, pz) < 6500f) continue;
                    float h = terrain.natural(px, pz);
                    if (h < 3f || h > 180f) continue;
                    float sl = slopeAround(px, pz, 200f);
                    if (sl > 0.08f) continue;
                    float sc = (1f / (1f + sl * 30f)) * (1f - h / 400f) * (d < 3000f ? 1.45f : 1f)
                            * (0.7f + 0.6f * Noise.rnd((int) px, (int) pz, seed + 1520));
                    if (sc > best) { best = sc; bx = px; bz = pz; bd = d; }
                }
                if (best > 0f) cand.add(new float[]{bx, bz, best, bd});
            }
        cand.sort((A, B) -> Float.compare(B[2], A[2]));
        List<float[]> chosen = new ArrayList<>();
        for (float[] c : cand) {
            float min = c[3] < 3000f ? 4000f : 4600f;
            boolean ok = true;
            for (float[] o : chosen) if (Math.hypot(o[0] - c[0], o[1] - c[1]) < min) { ok = false; break; }
            if (ok) chosen.add(c);
        }
        int n = chosen.size();
        for (int i = 0; i < n; i++) {
            float[] c = chosen.get(i);
            Town.Size size = i < n * 0.3f ? Town.Size.TOWN : (i < n * 0.62f ? Town.Size.VILLAGE : Town.Size.HAMLET);
            RoadNode nd = addNode(c[0], c[1], RoadNode.Kind.JUNCTION);
            villages.add(nd);
            Town t = makeTown(nd, size, false);
            t.coastal = c[3] < 3000f;
        }
        float[] largeTownSite = largestTownSite();
        if (largeTownSite != null) {
            RoadNode centre = addNode(largeTownSite[0], largeTownSite[1], RoadNode.Kind.JUNCTION);
            villages.add(centre);
            Town largeTown = makeTown(centre, Town.Size.TOWN, false);
            largeTown.coastal = largeTownSite[3] < 3000f;
        }
    }

    /** Reserves one gently sloped, well-separated site for the island's largest town. */
    private float[] largestTownSite() {
        float step = 3000f;
        float[] best = null;
        float bestScore = -Float.MAX_VALUE;
        for (float z = terrain.islandZ0 + step * 0.5f; z < terrain.islandZ1; z += step)
            for (float x = terrain.islandX0 + step * 0.5f; x < terrain.islandX1; x += step) {
                if (!gridFits(x, z, LARGE_TOWN_GRID)) continue;
                float inland = -terrain.coastOver(x, z);
                float score = Noise.rnd((int) (x / 100f), (int) (z / 100f), seed + 1540)
                        - Math.abs(inland - 4500f) / 20000f;
                if (score > bestScore) {
                    bestScore = score;
                    best = new float[]{x, z, score, inland};
                }
            }
        return best;
    }

    /** Largest height difference across a +-r cross, as a slope. */
    private float slopeAround(float x, float z, float r) {
        float h = terrain.natural(x, z);
        float m = 0f;
        m = Math.max(m, Math.abs(terrain.natural(x + r, z) - h));
        m = Math.max(m, Math.abs(terrain.natural(x - r, z) - h));
        m = Math.max(m, Math.abs(terrain.natural(x, z + r) - h));
        m = Math.max(m, Math.abs(terrain.natural(x, z - r) - h));
        return m / r;
    }

    public Town.Region regionAt(float x, float z) {
        Town.Region best = null;
        float bd = Float.MAX_VALUE;
        for (Town.Region rg : regions) {
            float d = (rg.x - x) * (rg.x - x) + (rg.z - z) * (rg.z - z);
            if (d < bd) { bd = d; best = rg; }
        }
        return best;
    }

    /** The settlement covering (x, z) (within margin metres of its built-up area), or null. */
    public Town townAt(float x, float z, float margin) {
        for (Town t : towns) if (t.contains(x, z, margin)) return t;
        return null;
    }

    public Town townOf(RoadNode n) { return townByNode.get(n.id); }

    private Town makeTown(RoadNode centre, Town.Size size, boolean exit) {
        int nx = gridSize(centre.x, centre.z, size);
        if (nx == 0 && size != Town.Size.HAMLET) {
            size = Town.Size.HAMLET;
        }
        Town t = new Town(towns.size(), names.town(), regionAt(centre.x, centre.z), size, centre);
        t.exitTown = exit;
        towns.add(t);
        townByNode.put(centre.id, t);
        if (nx > 0) {
            townGrid(t, nx, nx);
            if (size == Town.Size.TOWN && nx == LARGE_TOWN_GRID) hasLargeTownGrid = true;
            if (size == Town.Size.VILLAGE && nx == LARGE_VILLAGE_GRID) hasLargeVillageGrid = true;
        }
        return t;
    }

    private int gridSize(float cx, float cz, Town.Size size) {
        if (size == Town.Size.TOWN) {
            int large = hasLargeTownGrid ? 0 : LARGE_TOWN_GRID;
            for (int candidate : new int[]{large, 27, TOWN_GRID})
                if (candidate > 0 && gridFits(cx, cz, candidate)) return candidate;
        } else if (size == Town.Size.VILLAGE) {
            int large = hasLargeVillageGrid ? 0 : LARGE_VILLAGE_GRID;
            for (int candidate : new int[]{large, 15, VILLAGE_GRID})
                if (candidate > 0 && gridFits(cx, cz, candidate)) return candidate;
        }
        return 0;
    }

    /** A street grid needs gentle ground and no freeway or coast through it. */
    private boolean gridFits(float cx, float cz, int nx) {
        float hx = (nx - 1) * 0.5f * TOWN_SX + 40f, hz = (nx - 1) * 0.5f * TOWN_SZ + 40f;
        float[][] heights = new float[5][5];
        float stepX = hx * 0.5f, stepZ = hz * 0.5f;
        for (int j = 0; j < 5; j++)
            for (int i = 0; i < 5; i++) {
                float x = cx + (i - 2) * stepX, z = cz + (j - 2) * stepZ;
                if (terrain.coastOver(x, z) > -400f || terrain.cityDistance(x, z) == 0f) return false;
                float h = terrain.natural(x, z);
                if (h < 3f) return false;
                heights[j][i] = h;
            }
        for (int j = 0; j < 5; j++)
            for (int i = 0; i < 5; i++) {
                if (i < 4 && Math.abs(heights[j][i + 1] - heights[j][i]) / stepX > 0.08f) return false;
                if (j < 4 && Math.abs(heights[j + 1][i] - heights[j][i]) / stepZ > 0.08f) return false;
            }
        float reach = Math.max(hx, hz) + 150f;
        for (RoadEdge e : edges)
            if ((e.cls == RoadClass.FREEWAY || e.cls == RoadClass.RAMP || e.cls == RoadClass.HIGHWAY) && distTo(e, cx, cz) < reach) return false;
        for (Town o : towns)
            if (o.size != Town.Size.HAMLET && Math.abs(o.centre.x - cx) < hx + (o.x1 - o.x0) * 0.5f + 300f
                    && Math.abs(o.centre.z - cz) < hz + (o.z1 - o.z0) * 0.5f + 300f) return false;
        return true;
    }

    /** Streets on a regular grid centred on the town's centre node. */
    private void townGrid(Town t, int nx, int nz) {
        RoadNode c = t.centre;
        t.grid = new RoadNode[nx][nz];
        t.gx = new float[nx];
        t.gz = new float[nz];
        for (int i = 0; i < nx; i++) t.gx[i] = c.x + (i - (nx - 1) / 2) * TOWN_SX;
        for (int j = 0; j < nz; j++) t.gz[j] = c.z + (j - (nz - 1) / 2) * TOWN_SZ;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                boolean centre = i == (nx - 1) / 2 && j == (nz - 1) / 2;
                t.grid[i][j] = centre ? c : addNode(t.gx[i], t.gz[j], RoadNode.Kind.JUNCTION);
                townByNode.put(t.grid[i][j].id, t);
            }
        List<float[]> wp = new ArrayList<>();
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                RoadNode a = t.grid[i][j];
                if (i + 1 < nx) townStreet(a, t.grid[i + 1][j], wp);
                if (j + 1 < nz) townStreet(a, t.grid[i][j + 1], wp);
            }
        t.x0 = t.gx[0] - 26f; t.x1 = t.gx[nx - 1] + 26f;
        t.z0 = t.gz[0] - 26f; t.z1 = t.gz[nz - 1] + 26f;
    }

    private void townStreet(RoadNode a, RoadNode b, List<float[]> wp) {
        wp.clear();
        wp.add(new float[]{a.x, a.z});
        wp.add(new float[]{b.x, b.z});
        float[][] pl = spline(wp, 8f); // straight grid streets: one piece per height-map sample
        addEdge(RoadClass.TOWN, a.id, b.id, pl[0], pl[1]);
    }

    /**
     * Where a road from (fx, fz) enters a settlement: the grid node on the town's edge nearest to
     * it (roads don't cut through the streets), or the junction itself for a hamlet.
     */
    private RoadNode gate(RoadNode v, float fx, float fz) {
        Town t = townByNode.get(v.id);
        if (t == null || t.grid == null) return v;
        RoadNode best = v;
        float bd = Float.MAX_VALUE;
        int nx = t.grid.length, nz = t.grid[0].length;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                if (i != 0 && j != 0 && i != nx - 1 && j != nz - 1) continue;
                if ((i == 0 || i == nx - 1) && (j == 0 || j == nz - 1)) continue;    // not at the corners
                RoadNode g = t.grid[i][j];
                float d = (g.x - fx) * (g.x - fx) + (g.z - fz) * (g.z - fz);
                if (d < bd) { bd = d; best = g; }
            }
        return best;
    }

    /** A town ~800 m off a freeway exit on side (sx, sz) of terminal t, if the ground allows. */
    private boolean exitTown(RoadNode t, float sx, float sz) {
        for (float dist : new float[]{800f, 1100f, 1400f}) {
            float x = t.x + sx * dist, z = t.z + sz * dist;
            if (terrain.coastOver(x, z) > -1500f || terrain.cityDistance(x, z) < 3500f || terrain.natural(x, z) > 140f) continue;
            boolean near = false;
            for (Town o : towns) if (Math.hypot(o.centre.x - x, o.centre.z - z) < 4500f) { near = true; break; }
            if (near) continue;
            if (gridSize(x, z, Town.Size.TOWN) == 0) continue;
            RoadNode c = addNode(x, z, RoadNode.Kind.JUNCTION);
            villages.add(c);
            makeTown(c, Town.Size.TOWN, true);
            route(RoadClass.COUNTRY, t, gate(c, t.x, t.z), sx, sz, 0f, 0f);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ routing

    /**
     * Builds a winding road from a to b. (sx,sz)/(ex,ez): optional unit directions the road leaves
     * a / arrives at b with (zero = free), used for straight exits from the city and ramps.
     */
    private RoadEdge route(RoadClass cls, RoadNode a, RoadNode b, float sx, float sz, float ex, float ez) {
        if (a == b || Math.hypot(a.x - b.x, a.z - b.z) < 60f) return null;
        if (router != null) {
            float lead = (sx != 0f || sz != 0f) ? Math.min(400f, (float) Math.hypot(b.x - a.x, b.z - a.z) * 0.2f) : 0f;
            float tail = (ex != 0f || ez != 0f) ? Math.min(400f, (float) Math.hypot(b.x - a.x, b.z - a.z) * 0.2f) : 0f;
            float ax = a.x + sx * lead, az = a.z + sz * lead, bx = b.x - ex * tail, bz = b.z - ez * tail;
            List<float[]> path = router.path(ax, az, bx, bz, cls, ringRouting);
            if (path == null) return null;
            List<float[]> wp = new ArrayList<>();
            wp.add(new float[]{a.x, a.z});
            List<float[]> simple = simplify(path, cls == RoadClass.HIGHWAY ? 80f : 60f);
            for (float[] q : simple) {
                float[] l = wp.get(wp.size() - 1);
                if (Math.hypot(q[0] - l[0], q[1] - l[1]) > 30f) wp.add(q);
            }
            float[] l = wp.get(wp.size() - 1);
            if (Math.hypot(b.x - l[0], b.z - l[1]) > 30f) wp.add(new float[]{b.x, b.z});
            else { l[0] = b.x; l[1] = b.z; }
            if (wp.size() < 2) return null;
            float[][] pl = spline(wp, cls.step);
            router.mark(pl[0], pl[1], 3);
            return addEdge(cls, a.id, b.id, pl[0], pl[1]);
        }
        return routeDirect(cls, a, b, sx, sz, ex, ez);
    }

    private RoadEdge routeDirect(RoadClass cls, RoadNode a, RoadNode b, float sx, float sz, float ex, float ez) {
        List<float[]> wp = new ArrayList<>();
        wp.add(new float[]{a.x, a.z});
        float dx = b.x - a.x, dz = b.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        float ux = dx / d, uz = dz / d, nx = -uz, nz = ux;
        float lead = 0f;
        if (sx != 0f || sz != 0f) {
            lead = Math.min(500f, d * 0.2f);
            wp.add(new float[]{a.x + sx * lead, a.z + sz * lead});
        }
        int n = Math.max(1, (int) (d / 1400f));
        float amp = cls == RoadClass.HIGHWAY ? 350f : 600f;
        for (int k = 1; k < n; k++) {
            float u = k / (float) n;
            float bx = a.x + dx * u, bz = a.z + dz * u;
            float taper = (float) Math.sin(Math.PI * u);
            float off = (Noise.rnd(a.id * 131 + k, b.id, seed + 700) - 0.5f) * 2f * amp * taper;
            // pick the lowest of a few lateral positions so roads follow valleys
            float bestH = Float.MAX_VALUE, px = bx, pz = bz;
            for (int t = -1; t <= 1; t++) {
                float o = off + t * 450f * taper;
                float qx = bx + nx * o, qz = bz + nz * o;
                if (terrain.coastOver(qx, qz) > -300f) continue;
                float h = terrain.natural(qx, qz) + Math.abs(t) * 6f;
                if (h < bestH) { bestH = h; px = qx; pz = qz; }
            }
            wp.add(new float[]{px, pz});
        }
        if (ex != 0f || ez != 0f) {
            float l = Math.min(400f, d * 0.2f);
            wp.add(new float[]{b.x - ex * l, b.z - ez * l});
        }
        wp.add(new float[]{b.x, b.z});
        float[][] pl = spline(wp, cls.step);
        return addEdge(cls, a.id, b.id, pl[0], pl[1]);
    }

    /** Catmull-Rom through the waypoints, sampled roughly every {@code step} metres. */
    /**
     * A smooth curve through the waypoints, sampled every {@code step} metres. Each waypoint's
     * tangent is scaled to the legs either side of it (a non-uniform Catmull-Rom), so a short
     * leg next to a long straight bends gently instead of overshooting into a loop.
     */
    static float[][] spline(List<float[]> wp, float step) {
        int m = wp.size();
        float[] xs = new float[64], zs = new float[64];
        int cnt = 0;
        for (int i = 0; i < m - 1; i++) {
            float[] p0 = wp.get(Math.max(0, i - 1)), p1 = wp.get(i), p2 = wp.get(i + 1), p3 = wp.get(Math.min(m - 1, i + 2));
            float l01 = (float) Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
            float l12 = (float) Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            float l23 = (float) Math.hypot(p3[0] - p2[0], p3[1] - p2[1]);
            // tangents in units of this leg: (p2 - p0) shared out by leg length
            float k1 = l12 / Math.max(1e-3f, l01 + l12), k2 = l12 / Math.max(1e-3f, l12 + l23);
            float m1x = (p2[0] - p0[0]) * k1, m1z = (p2[1] - p0[1]) * k1;
            float m2x = (p3[0] - p1[0]) * k2, m2z = (p3[1] - p1[1]) * k2;
            int n = Math.max(1, (int) Math.ceil(l12 / step));
            for (int k = 0; k < n; k++) {
                float t = k / (float) n, t2 = t * t, t3 = t2 * t;
                float h00 = 2 * t3 - 3 * t2 + 1, h10 = t3 - 2 * t2 + t, h01 = -2 * t3 + 3 * t2, h11 = t3 - t2;
                float x = h00 * p1[0] + h10 * m1x + h01 * p2[0] + h11 * m2x;
                float z = h00 * p1[1] + h10 * m1z + h01 * p2[1] + h11 * m2z;
                if (cnt == xs.length) { xs = Arrays.copyOf(xs, cnt * 2); zs = Arrays.copyOf(zs, cnt * 2); }
                xs[cnt] = x; zs[cnt] = z; cnt++;
            }
        }
        float[] last = wp.get(m - 1);
        if (cnt == xs.length) { xs = Arrays.copyOf(xs, cnt + 1); zs = Arrays.copyOf(zs, cnt + 1); }
        xs[cnt] = last[0]; zs[cnt] = last[1]; cnt++;
        return new float[][]{Arrays.copyOf(xs, cnt), Arrays.copyOf(zs, cnt)};
    }

    /** Cubic Bezier sampled every {@code step} metres. */
    static float[][] bezier(float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3, float step) {
        float approx = (float) (Math.hypot(x1 - x0, z1 - z0) + Math.hypot(x2 - x1, z2 - z1) + Math.hypot(x3 - x2, z3 - z2));
        int n = Math.max(2, (int) Math.ceil(approx / step));
        float[] xs = new float[n + 1], zs = new float[n + 1];
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n, u = 1 - t;
            xs[k] = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
            zs[k] = u * u * u * z0 + 3 * u * u * t * z1 + 3 * u * t * t * z2 + t * t * t * z3;
        }
        return new float[][]{xs, zs};
    }

    // ------------------------------------------------------------------ freeways & highways

    /**
     * The road network, built the way real ones grow: a highway round the coast linking the
     * coastal towns and the city, freeways from the city to the most important distant towns,
     * highways joining the towns across the island, and country roads to every neighbouring
     * settlement. Every road is routed over the terrain: it follows valleys and the coastal plain,
     * avoids steep climbs, cliffs, beaches and other towns, and keeps away from roads already
     * there (they meet at junctions where they cross).
     */
    private void network(List<RoadEdge> fw) {
        router = new Router();
        for (Town t : towns) if (t.grid != null) router.markArea(t.x0 - 60f, t.z0 - 60f, t.x1 + 60f, t.z1 + 60f, 4);
        java.util.HashSet<Long> linked = new java.util.HashSet<>();
        // 1. coastal highway round the island, through the city
        List<Town> ring = new ArrayList<>();
        for (Town t : towns) if (t.coastal) ring.add(t);
        float ccx = terrain.centreX, ccz = terrain.centreZ;
        ring.sort((A, B) -> Float.compare((float) Math.atan2(A.centre.z - ccz, A.centre.x - ccx), (float) Math.atan2(B.centre.z - ccz, B.centre.x - ccx)));
        RoadNode cityN = nodes.get(cityBoundaryNode(1f, -1f, 0f));
        RoadNode cityS = nodes.get(cityBoundaryNode(1f, 1f, 0f));
        float cityAng = (float) Math.atan2((cityN.z + cityS.z) * 0.5f - ccz, cityN.x - ccx);
        int at = 0;
        while (at < ring.size() && Math.atan2(ring.get(at).centre.z - ccz, ring.get(at).centre.x - ccx) < cityAng) at++;
        List<Town> order = new ArrayList<>();
        for (int k = 0; k < ring.size(); k++) order.add(ring.get((at + k) % ring.size()));
        // towns well off the line of the coast (out on a peninsula) get a spur road instead
        for (int pass = 0; pass < 2; pass++)
            for (int k = 0; k < order.size(); k++) {
                float px = k == 0 ? cityS.x : order.get(k - 1).centre.x, pz = k == 0 ? cityS.z : order.get(k - 1).centre.z;
                float nx = k == order.size() - 1 ? cityN.x : order.get(k + 1).centre.x, nz = k == order.size() - 1 ? cityN.z : order.get(k + 1).centre.z;
                Town t = order.get(k);
                double detour = Math.hypot(t.centre.x - px, t.centre.z - pz) + Math.hypot(nx - t.centre.x, nz - t.centre.z) - Math.hypot(nx - px, nz - pz);
                if (detour > 3500) { t.coastal = false; t.spur = true; order.remove(k); k--; }
            }
        // order starts just south of the city and runs round to just north of it
        ringRouting = true;
        RoadNode prev = cityS;
        Town prevTown = null;
        for (Town t : order) {
            RoadNode in = gate(t.centre, prev.x, prev.z);
            if (route(RoadClass.HIGHWAY, prev, in, 0f, prev == cityS ? 1f : 0f, 0f, 0f) != null && prevTown != null)
                linked.add(pair(prevTown.id, t.id));
            prev = gate(t.centre, prev.x + (t.centre.x - prev.x) * 3f, prev.z + (t.centre.z - prev.z) * 3f);
            prevTown = t;
        }
        route(RoadClass.HIGHWAY, prev, cityN, 0f, 0f, 0f, 1f);
        ringRouting = false;
        // 2. freeways from the city to the most important towns far away
        List<Town> majors = new ArrayList<>();
        for (Town t : towns) if (t.size == Town.Size.TOWN) majors.add(t);
        freewayTo(cityBoundaryNode(-1f, 0f, 0f), -1f, 0f, majors, fw);
        freewayTo(cityBoundaryNode(0f, -1f, 0f), 0f, -1f, majors, fw);
        freewayTo(cityBoundaryNode(0f, 1f, 0f), 0f, 1f, majors, fw);
        // 3. highways joining the towns: a spanning tree, plus short cut links
        int m = majors.size();
        boolean[] in = new boolean[m];
        if (m > 0) in[0] = true;
        for (int it = 1; it < m; it++) {
            int bi = -1, bj = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < m; i++) {
                if (!in[i]) continue;
                for (int j = 0; j < m; j++) {
                    if (in[j]) continue;
                    double d = dist(majors.get(i), majors.get(j));
                    if (d < bd) { bd = d; bi = i; bj = j; }
                }
            }
            if (bj < 0) break;
            in[bj] = true;
            highwayLink(majors.get(bi), majors.get(bj), linked);
        }
        for (int i = 0; i < m; i++)
            for (int j = i + 1; j < m; j++) {
                Town a = majors.get(i), b = majors.get(j);
                double d = dist(a, b);
                if (d > 22000 || linked.contains(pair(a.id, b.id))) continue;
                if (rngNeighbours(majors, a, b, d) && Noise.rnd(a.id, b.id, seed + 1530) < 0.5f) highwayLink(a, b, linked);
            }
        // 4. country roads between neighbouring settlements
        for (int i = 0; i < towns.size(); i++)
            for (int j = i + 1; j < towns.size(); j++) {
                Town a = towns.get(i), b = towns.get(j);
                if (a.exitTown || b.exitTown) continue;
                double d = dist(a, b);
                if (d > 14000 || linked.contains(pair(a.id, b.id))) continue;
                if (a.coastal && b.coastal && d > 3000) continue;     // the coast road joins them
                if (!rngNeighbours(towns, a, b, d)) continue;
                if (route(RoadClass.COUNTRY, gate(a.centre, b.centre.x, b.centre.z), gate(b.centre, a.centre.x, a.centre.z), 0f, 0f, 0f, 0f) != null)
                    linked.add(pair(a.id, b.id));
            }
        // 4b. more than one way between neighbours: short cuts over the hills where the road goes
        // the long way round, and back roads beside the main roads
        alternatives(linked);
        // 5. the city's own roads out west, north and south
        cityConnectors();
        // 6. fishing harbours below the coastal towns
        for (Town t : towns) if (t.coastal || t.spur) harbour(t);
    }

    /** Harbours: {quay x, quay z, seaward x, seaward z (unit, along an axis), coast x, coast z, town id}. */
    public final List<float[]> harbours = new ArrayList<>();

    /** A short road from the town down to a quay where the coast is low (not under cliffs). */
    private void harbour(Town t) {
        float best = Float.MAX_VALUE;
        float[] bestDir = null;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dv : dirs) {
            for (float r = 200f; r < 3600f; r += 25f) {
                float x = t.centre.x + dv[0] * r, z = t.centre.z + dv[1] * r;
                if (terrain.coastOver(x, z) > 0f) {
                    if (r < best && terrain.cliff(x, z) < 0.4f && terrain.cityDistance(x, z) > 3000f) { best = r; bestDir = new float[]{dv[0], dv[1], x, z}; }
                    break;
                }
            }
        }
        if (bestDir == null) return;
        float sx = bestDir[0], sz = bestDir[1];
        // the coast point, then a quay node ~45 m inland of it
        float cx = bestDir[2], cz = bestDir[3];
        float qx = cx - sx * 45f, qz = cz - sz * 45f;
        for (float[] h : harbours) if (Math.hypot(h[0] - qx, h[1] - qz) < 1500f) return;
        RoadNode q = addNode(qx, qz, RoadNode.Kind.END);
        RoadNode from = gate(t.centre, qx, qz);
        if (route(RoadClass.TOWN, from, q, 0f, 0f, sx, sz) == null && route(RoadClass.COUNTRY, from, q, 0f, 0f, 0f, 0f) == null) {
            q.dead = true;
            return;
        }
        harbours.add(new float[]{qx, qz, sx, sz, cx, cz, t.id});
    }

    public int shortcuts, backRoads;

    /**
     * Gives neighbouring settlements a choice of route. Where the road between them is much
     * longer than the crow flies (round a headland, the far side of a ridge), a country road takes
     * the short way over the hills. Where they are joined directly, often a back road takes the
     * long way through the next valley inland — slower, quieter, and a way round a jam or a chase.
     */
    private void alternatives(java.util.HashSet<Long> linked) {
        List<Town> set = new ArrayList<>();
        set.addAll(towns);
        List<long[]> pairs = new ArrayList<>();
        for (int i = 0; i < set.size(); i++)
            for (int j = i + 1; j < set.size(); j++) {
                Town a = set.get(i), b = set.get(j);
                double d = dist(a, b);
                if (d < 2500 || d > 13000) continue;
                boolean blocked = false;           // neighbours: nobody much closer to both in between
                for (Town c : set) {
                    if (c == a || c == b) continue;
                    if (Math.max(dist(a, c), dist(b, c)) < d * 0.8) { blocked = true; break; }
                }
                if (!blocked) pairs.add(new long[]{i, j, (long) d});
            }
        pairs.sort((x, y) -> Long.compare(x[2], y[2]));
        int maxBack = Math.max(2, set.size() / 4);
        for (long[] pr : pairs) {
            Town a = set.get((int) pr[0]), b = set.get((int) pr[1]);
            float d = pr[2];
            float rd = roadDistance(a.centre.id, b.centre.id);
            if (rd > d * 1.8f) {
                // the short way
                if (route(RoadClass.COUNTRY, gate(a.centre, b.centre.x, b.centre.z), gate(b.centre, a.centre.x, a.centre.z), 0f, 0f, 0f, 0f) != null) {
                    linked.add(pair(a.id, b.id));
                    shortcuts++;
                }
            } else if (rd < d * 1.7f && backRoads < maxBack && (a.size != Town.Size.HAMLET || b.size != Town.Size.HAMLET)
                    && Noise.rnd(a.id, b.id, seed + 1610) < 0.6f) {
                // the long way: through a point well off to the side, on the inland side
                float mx = (a.centre.x + b.centre.x) * 0.5f, mz = (a.centre.z + b.centre.z) * 0.5f;
                float ux = (b.centre.x - a.centre.x) / d, uz = (b.centre.z - a.centre.z) / d, nx = -uz, nz = ux;
                float off = d * (0.3f + 0.12f * Noise.rnd(b.id, a.id, seed + 1611));
                float[] best = null;
                float bestInland = -1e9f;
                for (int side = -1; side <= 1; side += 2) {
                    float px = mx + nx * off * side, pz = mz + nz * off * side;
                    float inland = -terrain.coastOver(px, pz);
                    if (inland < 700f || terrain.cityDistance(px, pz) < 2500f || terrain.natural(px, pz) > 420f) continue;
                    if (router.roadNear(px, pz, 3)) continue;                  // a road already runs there
                    if (inland > bestInland) { bestInland = inland; best = new float[]{px, pz}; }
                }
                if (best == null) continue;
                RoadNode ga = gate(a.centre, best[0], best[1]), gb = gate(b.centre, best[0], best[1]);
                List<float[]> p1 = router.path(ga.x, ga.z, best[0], best[1], RoadClass.COUNTRY, false);
                List<float[]> p2 = p1 == null ? null : router.path(best[0], best[1], gb.x, gb.z, RoadClass.COUNTRY, false);
                if (p2 == null) continue;
                float len = pathLength(p1) + pathLength(p2);
                if (len > d * 2.4f) continue;          // a real alternative, not a huge detour
                RoadNode mid = addNode(best[0], best[1], RoadNode.Kind.JUNCTION);
                RoadEdge e1 = route(RoadClass.COUNTRY, ga, mid, 0f, 0f, 0f, 0f);
                RoadEdge e2 = e1 == null ? null : route(RoadClass.COUNTRY, mid, gb, 0f, 0f, 0f, 0f);
                if (e2 != null) backRoads++;
            }
        }
    }

    private static float pathLength(List<float[]> p) {
        float l = 0f;
        for (int i = 1; i < p.size(); i++) l += (float) Math.hypot(p.get(i)[0] - p.get(i - 1)[0], p.get(i)[1] - p.get(i - 1)[1]);
        return l;
    }

    /** Shortest distance by road between two nodes over the roads built so far (infinity if none). */
    private float roadDistance(int from, int to) {
        int n = nodes.size();
        float[] d = new float[n];
        Arrays.fill(d, Float.MAX_VALUE);
        d[from] = 0f;
        java.util.PriorityQueue<float[]> q = new java.util.PriorityQueue<>((x, y) -> Float.compare(x[0], y[0]));
        q.add(new float[]{0f, from});
        while (!q.isEmpty()) {
            float[] f = q.poll();
            int u = (int) f[1];
            if (f[0] > d[u]) continue;
            if (u == to) return d[u];
            for (int eid : nodes.get(u).edges) {
                RoadEdge e = edges.get(eid);
                int v = e.a == u ? e.b : e.a;
                float nd = d[u] + e.len;
                if (nd < d[v]) { d[v] = nd; q.add(new float[]{nd, v}); }
            }
        }
        return Float.MAX_VALUE;
    }

    private static long pair(int a, int b) { return a < b ? ((long) a << 32) | b : ((long) b << 32) | a; }

    private static double dist(Town a, Town b) { return Math.hypot(a.centre.x - b.centre.x, a.centre.z - b.centre.z); }

    /** Relative neighbourhood test: no third settlement closer to both a and b than they are to each other. */
    private static boolean rngNeighbours(List<Town> set, Town a, Town b, double d) {
        for (Town c : set) {
            if (c == a || c == b) continue;
            if (Math.max(dist(a, c), dist(b, c)) < d) return false;
        }
        return true;
    }

    private void highwayLink(Town a, Town b, java.util.HashSet<Long> linked) {
        if (linked.contains(pair(a.id, b.id))) return;
        if (a.coastal && b.coastal) return;            // the coastal highway already joins them
        if (route(RoadClass.HIGHWAY, gate(a.centre, b.centre.x, b.centre.z), gate(b.centre, a.centre.x, a.centre.z), 0f, 0f, 0f, 0f) != null)
            linked.add(pair(a.id, b.id));
    }

    /**
     * Freeway from a city node heading (dx, dz) to the most important distant town that way. The
     * freeway ends ~1.5 km short of the town and a highway carries on into it.
     */
    private void freewayTo(int startId, float dx, float dz, List<Town> majors, List<RoadEdge> out) {
        RoadNode a = nodes.get(startId);
        Town best = null;
        double bs = 0;
        for (Town t : majors) {
            double ex = t.centre.x - a.x, ez = t.centre.z - a.z, d = Math.hypot(ex, ez);
            double along = (ex * dx + ez * dz) / d;
            if (d < 18000 || along < 0.55) continue;
            double sc = Math.min(d, 60000) * along;
            if (sc > bs) { bs = sc; best = t; }
        }
        if (best == null) return;
        float lx = a.x + dx * 1100f, lz = a.z + dz * 1100f;
        List<float[]> path = router.path(lx, lz, best.centre.x, best.centre.z, RoadClass.FREEWAY, false);
        if (path == null) return;
        // stop short of the town
        float left = 0f;
        int cut = path.size() - 1;
        while (cut > 1) {
            left += (float) Math.hypot(path.get(cut)[0] - path.get(cut - 1)[0], path.get(cut)[1] - path.get(cut - 1)[1]);
            if (left > 1500f) break;
            cut--;
        }
        List<float[]> wp = new ArrayList<>();
        wp.add(new float[]{a.x, a.z});
        wp.add(new float[]{a.x + dx * 350f, a.z + dz * 350f});
        wp.add(new float[]{lx, lz});
        List<float[]> simple = simplify(path.subList(0, cut + 1), 90f);
        for (int i = 1; i < simple.size(); i++) wp.add(simple.get(i));
        straighten(wp, 1, 1400f);
        // a freeway doesn't end in a hook: stop it before a last sharp turn (the highway on to the town takes over)
        while (wp.size() > 4) {
            int i = wp.size() - 2;
            float[] p = wp.get(i - 1), q = wp.get(i), r = wp.get(i + 1);
            float ax = q[0] - p[0], az = q[1] - p[1], bx = r[0] - q[0], bz = r[1] - q[1];
            float cos = (ax * bx + az * bz) / (float) (Math.hypot(ax, az) * Math.hypot(bx, bz) + 1e-3);
            if (cos > 0.35f && radiusAt(wp, i) > 500f) break;
            wp.remove(wp.size() - 1);
        }
        float[] last = wp.get(wp.size() - 1);
        RoadNode end = addNode(last[0], last[1], RoadNode.Kind.END);
        float[][] pl = spline(wp, RoadClass.FREEWAY.step);
        RoadEdge e = addEdge(RoadClass.FREEWAY, a.id, end.id, pl[0], pl[1]);
        router.mark(pl[0], pl[1], 3);
        out.add(e);
        route(RoadClass.HIGHWAY, end, gate(best.centre, end.x, end.z), 0f, 0f, 0f, 0f);
    }

    /**
     * Drops waypoints that make curves tighter than {@code minRadius} (tightest first), as long
     * as the straighter line stays on land, out of the city and close enough to the ground that
     * cuttings and embankments can carry it. Waypoints before {@code keep} stay.
     */
    private void straighten(List<float[]> wp, int keep, float minRadius) {
        while (wp.size() > keep + 2) {
            int worst = -1;
            float worstR = minRadius;
            for (int i = Math.max(1, keep); i < wp.size() - 1; i++) {
                float r = radiusAt(wp, i);
                if (r < worstR && router.clearLine(wp.get(i - 1), wp.get(i + 1), wp.get(0))) { worstR = r; worst = i; }
            }
            if (worst < 0) break;
            wp.remove(worst);
        }
    }

    /** Rough radius a spline through waypoint i will have: the shorter neighbouring leg over the turn angle. */
    private static float radiusAt(List<float[]> wp, int i) {
        float[] p = wp.get(i - 1), q = wp.get(i), r = wp.get(i + 1);
        float ax = q[0] - p[0], az = q[1] - p[1], bx = r[0] - q[0], bz = r[1] - q[1];
        float la = (float) Math.hypot(ax, az), lb = (float) Math.hypot(bx, bz);
        if (la < 1f || lb < 1f) return 0f;
        float cos = Math.max(-1f, Math.min(1f, (ax * bx + az * bz) / (la * lb)));
        float th = (float) Math.acos(cos);
        if (th < 1e-3f) return Float.MAX_VALUE;
        return Math.min(la, lb) * 0.5f / (float) Math.tan(th * 0.5f) ;
    }

    // ------------------------------------------------------------------ terrain routing

    private Router router;
    /** Routing the coastal highway: stay near the coast. */
    private boolean ringRouting;

    /**
     * A* over a 250 m grid of the island. Moving costs distance times a factor for the grade
     * (beyond what the road class allows it grows fast), being near the shore, being far from
     * the coast for the coastal highway, height for freeways, and other roads and towns nearby.
     */
    private final class Router {
        final int n = 512;
        final float cell = Terrain.ISLAND_SIZE / n;
        final float[] h = new float[n * n], inland = new float[n * n], steep = new float[n * n];
        final byte[] used = new byte[n * n];
        final float[] g = new float[n * n];
        final int[] from = new int[n * n], stamp = new int[n * n];
        int[] heap = new int[1 << 16];
        float[] hf = new float[1 << 16];
        int cur, size;

        Router() {
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    float x = x(i), z = z(j);
                    float d = -terrain.coastOver(x, z);
                    inland[j * n + i] = d;
                    h[j * n + i] = d > -100f ? terrain.natural(x, z) : -50f;
                    // local steepness (40 m scale): roads keep off cliffs and steep hillsides
                    if (d > -100f) {
                        float gx = (terrain.natural(x + 40f, z) - terrain.natural(x - 40f, z)) / 80f;
                        float gz = (terrain.natural(x, z + 40f) - terrain.natural(x, z - 40f)) / 80f;
                        steep[j * n + i] = (float) Math.sqrt(gx * gx + gz * gz);
                    }
                }
            // the built-up city is off limits to rural roads (they join it at its edge)
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++)
                    if (city.nearUrban(x(i), z(j), cell * 0.7f)) used[j * n + i] = 100;
        }

        float x(int i) { return terrain.islandX0 + (i + 0.5f) * cell; }
        float z(int j) { return terrain.islandZ0 + (j + 0.5f) * cell; }
        int ci(float x) { return Math.max(0, Math.min(n - 1, (int) ((x - terrain.islandX0) / cell))); }
        int cj(float z) { return Math.max(0, Math.min(n - 1, (int) ((z - terrain.islandZ0) / cell))); }

        void markArea(float x0, float z0, float x1, float z1, int v) {
            for (int j = cj(z0); j <= cj(z1); j++)
                for (int i = ci(x0); i <= ci(x1); i++) used[j * n + i] = (byte) Math.min(120, Math.max(used[j * n + i], v));
        }

        /** Marks the cells a road runs through so later roads keep their distance. */
        void mark(float[] xs, float[] zs, int v) {
            for (int k = 0; k < xs.length; k++) {
                int i0 = ci(xs[k]), j0 = cj(zs[k]);
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        int i = i0 + di, j = j0 + dj;
                        if (i < 0 || j < 0 || i >= n || j >= n) continue;
                        int c = j * n + i, w = di == 0 && dj == 0 ? v * 2 : v;
                        if (used[c] < 100) used[c] = (byte) Math.min(99, Math.max(used[c], w));
                    }
            }
        }

        float stepCost(int a, int b, float len, RoadClass cls, int sa, int sb) {
            float d = inland[b];
            boolean end = near(b, sa) || near(b, sb);
            if (d < 120f && !end) return Float.POSITIVE_INFINITY;               // sea, beach and cliff edge
            if (used[b] >= 100 && !end) return Float.POSITIVE_INFINITY;          // the city
            float grade = Math.abs(h[b] - h[a]) / len;
            float gmax = cls == RoadClass.FREEWAY ? 0.06f : (cls == RoadClass.HIGHWAY ? 0.06f : 0.09f);
            float q = grade / gmax;
            float c = 1f + q * q * 2.5f + (q > 2f ? (q - 2f) * 30f : 0f);
            // steep ground: a road there would be a shelf cut into a cliff; cliffs are impassable
            float st = steep[b];
            if (st > 0.55f && !end) return Float.POSITIVE_INFINITY;
            if (st > 0.15f) c += (st - 0.15f) * (cls == RoadClass.FREEWAY ? 25f : 35f);
            if (d < 350f) c += 1.5f;                                      // keep off the shore
            if (ringRouting && d > 2500f) c += (d - 2500f) / 1200f;      // the coastal road stays coastal
            if (cls == RoadClass.FREEWAY) c += Math.max(0f, h[b] - 60f) / 120f;
            int u = used[b];
            if (u > 0 && !end) c += u * 0.6f;
            return len * c;
        }

        /**
         * True if a straight freeway from p to q stays on land, out of the city and other
         * roads' way, and never more than ~40 m from the ground (or the grade allows it).
         */
        boolean clearLine(float[] p, float[] q, float[] start) {
            float len = (float) Math.hypot(q[0] - p[0], q[1] - p[1]);
            int steps = Math.max(2, (int) (len / (cell * 0.5f)));
            float h0 = h[cj(p[1]) * n + ci(p[0])], h1 = h[cj(q[1]) * n + ci(q[0])];
            for (int k = 1; k < steps; k++) {
                float t = k / (float) steps;
                int c = cj(p[1] + (q[1] - p[1]) * t) * n + ci(p[0] + (q[0] - p[0]) * t);
                float sx = p[0] + (q[0] - p[0]) * t, sz = p[1] + (q[1] - p[1]) * t;
                // leaving the city the freeway may cross its fringe (it starts at the city's edge)
                boolean fringe = Math.hypot(sx - start[0], sz - start[1]) < 1500f;
                if (inland[c] < 150f || (used[c] >= 100 && !fringe)) return false;
                if (steep[c] > 0.5f) return false;
                if (Math.abs(h[c] - (h0 + (h1 - h0) * t)) > 40f) return false;
            }
            return true;
        }

        /** True if a road has been routed through any cell within {@code r} cells of (x, z). */
        boolean roadNear(float x, float z, int r) {
            int i0 = ci(x), j0 = cj(z);
            for (int j = Math.max(0, j0 - r); j <= Math.min(n - 1, j0 + r); j++)
                for (int i = Math.max(0, i0 - r); i <= Math.min(n - 1, i0 + r); i++) {
                    int u = used[j * n + i];
                    if (u > 0 && u < 100) return true;
                }
            return false;
        }

        boolean near(int a, int b) { return Math.abs(a % n - b % n) <= 2 && Math.abs(a / n - b / n) <= 2; }

        /** Grid path from (ax, az) to (bx, bz) as world points (cell centres, ends exact), or null. */
        List<float[]> path(float ax, float az, float bx, float bz, RoadClass cls, boolean ring) {
            int sa = cj(az) * n + ci(ax), sb = cj(bz) * n + ci(bx);
            cur++;
            size = 0;
            g[sa] = 0f;
            stamp[sa] = cur;
            from[sa] = -1;
            push(sa, heuristic(sa, sb));
            int[] di = {1, -1, 0, 0, 1, 1, -1, -1}, dj = {0, 0, 1, -1, 1, -1, 1, -1};
            int expanded = 0;
            boolean found = false;
            while (size > 0) {
                float fk = hf[0];
                int k = pop();
                if (fk > g[k] + heuristic(k, sb) + 1e-2f) continue;          // stale heap entry
                if (k == sb) { found = true; break; }
                if (++expanded > 300000) break;
                int i = k % n, j = k / n;
                for (int t = 0; t < 8; t++) {
                    int ni = i + di[t], nj = j + dj[t];
                    if (ni < 0 || nj < 0 || ni >= n || nj >= n) continue;
                    int nb = nj * n + ni;
                    float len = t < 4 ? cell : cell * 1.4142f;
                    float c = stepCost(k, nb, len, cls, sa, sb);
                    if (c == Float.POSITIVE_INFINITY) continue;
                    // fast roads don't switch back: turning costs, sharply more for tighter turns
                    if (cls == RoadClass.FREEWAY || cls == RoadClass.HIGHWAY) {
                        int p = from[k];
                        if (p >= 0) {
                            int pi = k % n - p % n, pj = k / n - p / n;
                            float dot = (pi * di[t] + pj * dj[t]) / (float) (Math.sqrt(pi * pi + pj * pj) * Math.sqrt(di[t] * di[t] + dj[t] * dj[t]));
                            if (dot < 0.99f) {
                                float turn = cls == RoadClass.FREEWAY ? 1f : 0.4f;
                                c += len * turn * (dot > 0.6f ? 0.35f : dot > -0.1f ? 4f : 40f);
                            }
                        }
                    }
                    float ng = g[k] + c;
                    if (stamp[nb] != cur || ng < g[nb] - 1e-3f) {
                        stamp[nb] = cur;
                        g[nb] = ng;
                        from[nb] = k;
                        push(nb, ng + heuristic(nb, sb));
                    }
                }
            }
            if (!found) return null;
            List<float[]> out = new ArrayList<>();
            for (int k = sb; k >= 0; k = from[k]) out.add(new float[]{x(k % n), z(k / n)});
            java.util.Collections.reverse(out);
            out.get(0)[0] = ax; out.get(0)[1] = az;
            out.get(out.size() - 1)[0] = bx; out.get(out.size() - 1)[1] = bz;
            return out;
        }

        float heuristic(int a, int b) {
            float dx = (a % n - b % n) * cell, dz = (a / n - b / n) * cell;
            return (float) Math.sqrt(dx * dx + dz * dz) * 1.2f;
        }

        void push(int k, float f) {
            if (size == heap.length) { heap = Arrays.copyOf(heap, size * 2); hf = Arrays.copyOf(hf, size * 2); }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (hf[p] <= f) break;
                heap[i] = heap[p]; hf[i] = hf[p];
                i = p;
            }
            heap[i] = k; hf[i] = f;
        }

        int pop() {
            int top = heap[0];
            size--;
            if (size > 0) {
                int k = heap[size];
                float f = hf[size];
                int i = 0;
                while (true) {
                    int l = 2 * i + 1, r = l + 1, m = i;
                    float fm = f;
                    if (l < size && hf[l] < fm) { m = l; fm = hf[l]; }
                    if (r < size && hf[r] < fm) { m = r; }
                    if (m == i) break;
                    heap[i] = heap[m]; hf[i] = hf[m];
                    i = m;
                }
                heap[i] = k; hf[i] = f;
            }
            return top;
        }
    }

    /** Douglas-Peucker: drops grid points within {@code tol} metres of the simplified line. */
    private static List<float[]> simplify(List<float[]> pts, float tol) {
        int n = pts.size();
        if (n < 3) return new ArrayList<>(pts);
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{0, n - 1});
        while (!st.isEmpty()) {
            int[] r = st.pop();
            float[] a = pts.get(r[0]), b = pts.get(r[1]);
            float dx = b[0] - a[0], dz = b[1] - a[1], l = (float) Math.sqrt(dx * dx + dz * dz) + 1e-3f;
            float best = -1f;
            int bi = -1;
            for (int i = r[0] + 1; i < r[1]; i++) {
                float[] p = pts.get(i);
                float d = Math.abs((p[0] - a[0]) * dz - (p[1] - a[1]) * dx) / l;
                if (d > best) { best = d; bi = i; }
            }
            if (best > tol) {
                keep[bi] = true;
                st.push(new int[]{r[0], bi});
                st.push(new int[]{bi, r[1]});
            }
        }
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    /** Where interchanges are, so new ones keep their distance. */
    private final List<float[]> interchangeSpots = new ArrayList<>();

    /** Ramp terminals sit this far either side of the freeway's centreline, on the crossroad. */
    private static final float IC_SIDE = 100f;
    /** The slip roads leave and join the freeway this far before and after the crossroad. */
    private static final float IC_REACH = 340f;

    /**
     * Diamond interchanges every ~8-11 km: a crossroad bridges the freeway, and four one-way slip
     * roads join it at junctions either side: off the freeway along a deceleration lane, up to
     * the crossroad, and from it back down along an acceleration lane to merge.
     */
    private void interchanges(RoadEdge f) {
        List<Float> spots = new ArrayList<>();
        for (float s = 5000f; s < f.len - 3000f; s += 8000f + Noise.rnd(f.id, (int) s, seed + 800) * 3000f) spots.add(s);
        // split from the far end so earlier distances stay valid on the remaining first piece
        RoadEdge cur = f;
        float[] p = new float[5];
        for (int k = spots.size() - 1; k >= 0; k--) {
            float sc = spots.get(k);
            cur.point(sc, p);
            float px = p[0], pz = p[1], tx = p[3], tz = p[4];
            float lx = tz, lz = -tx;              // left of travel a→b
            RoadNode tL = addNode(px + lx * IC_SIDE, pz + lz * IC_SIDE, RoadNode.Kind.JUNCTION);
            RoadNode tR = addNode(px - lx * IC_SIDE, pz - lz * IC_SIDE, RoadNode.Kind.JUNCTION);
            // the crossroad over the freeway (it becomes a bridge where it crosses)
            List<float[]> wp = new ArrayList<>();
            wp.add(new float[]{tR.x, tR.z});
            wp.add(new float[]{tL.x, tL.z});
            float[][] pl = spline(wp, RoadClass.COUNTRY.step);
            addEdge(RoadClass.COUNTRY, tR.id, tL.id, pl[0], pl[1]);
            diamond(cur, sc, tL, tR);
            // a town just off the exit on one side (where there is room), the other side heads to the nearest village
            boolean townL = exitTown(tL, lx, lz), townR = !townL && exitTown(tR, -lx, -lz);
            if (!townL) linkToVillage(tL.id, RoadClass.COUNTRY, lx, lz);
            if (!townR) linkToVillage(tR.id, RoadClass.COUNTRY, -lx, -lz);
        }
    }

    /**
     * The four slip roads of a diamond at distance {@code sc} along freeway piece {@code f},
     * joining terminals tL (left of a→b travel) and tR on the crossroad. Splits the freeway
     * where the slip roads leave and join it (the piece before stays {@code f}).
     */
    private void diamond(RoadEdge f, float sc, RoadNode tL, RoadNode tR) {
        float[] p = new float[5];
        f.point(sc, p);
        float tx = p[3], tz = p[4], lx = tz, lz = -tx;
        interchangeSpots.add(new float[]{p[0], p[1]});
        int nUp = splitAt(f, sc + IC_REACH, RoadNode.Kind.SPLIT);   // later piece
        int nDn = splitAt(f, sc - IC_REACH, RoadNode.Kind.SPLIT);
        RoadNode up = nodes.get(nUp), dn = nodes.get(nDn);
        float lane = RoadClass.FREEWAY.median + (RoadClass.FREEWAY.lanes - 0.5f) * RoadClass.FREEWAY.laneW;
        // traffic keeps left: a→b travel uses the left carriageway, off at dn, back on at up
        ramp(dn, tL, lx, lz, tx, tz, lane, true);
        ramp(up, tL, lx, lz, tx, tz, lane, false);
        // b→a travel: off at up, on at dn
        ramp(up, tR, -lx, -lz, -tx, -tz, lane, true);
        ramp(dn, tR, -lx, -lz, -tx, -tz, lane, false);
    }

    /**
     * Interchanges where a highway or country road already bridges a freeway, so you can get on
     * and off there too: the road becomes the crossroad of a diamond. Skipped where the road
     * crosses too obliquely, too near another interchange or the freeway's end.
     */
    private void bridgeInterchanges() {
        // grid of freeway segments
        HashMap<Long, List<int[]>> grid = new HashMap<>();
        for (RoadEdge e : edges) {
            if (e.cls != RoadClass.FREEWAY) continue;
            for (int i = 0; i < e.count() - 1; i++) {
                long key = cellKey(e.x[i], e.z[i]);
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[]{e.id, i});
                long key2 = cellKey(e.x[i + 1], e.z[i + 1]);
                if (key2 != key) grid.computeIfAbsent(key2, k -> new ArrayList<>()).add(new int[]{e.id, i});
            }
        }
        // crossings of highways and country roads with them
        List<float[]> hits = new ArrayList<>();      // x, z, freeway origin, road origin
        int nEdges = edges.size();
        for (int ri = 0; ri < nEdges; ri++) {
            RoadEdge r = edges.get(ri);
            if (r.cls != RoadClass.HIGHWAY && r.cls != RoadClass.COUNTRY) continue;
            for (int i = 0; i < r.count() - 1; i++) {
                List<int[]> list = grid.get(cellKey(r.x[i], r.z[i]));
                if (list == null) continue;
                for (int[] fs : list) {
                    RoadEdge f = edges.get(fs[0]);
                    float[] h = intersect(r.x[i], r.z[i], r.x[i + 1], r.z[i + 1], f.x[fs[1]], f.z[fs[1]], f.x[fs[1] + 1], f.z[fs[1] + 1]);
                    if (h != null) hits.add(new float[]{h[0], h[1], f.origin, r.origin});
                }
            }
        }
        float[] p = new float[5], q = new float[5];
        for (float[] h : hits) {
            boolean crowded = false;
            for (float[] s : interchangeSpots) if (Math.hypot(s[0] - h[0], s[1] - h[1]) < 3000f) { crowded = true; break; }
            if (crowded) continue;
            RoadEdge f = pieceNear(h[0], h[1], (int) h[2]), r = pieceNear(h[0], h[1], (int) h[3]);
            if (f == null || r == null || f.cls != RoadClass.FREEWAY) continue;
            float sf = project(f, h[0], h[1]), sr = project(r, h[0], h[1]);
            if (sf < IC_REACH + 80f || sf > f.len - IC_REACH - 80f) continue;
            if (nearEnd(f, h[0], h[1], 1200f)) continue;
            f.point(sf, p);
            r.point(sr, q);
            float tx = p[3], tz = p[4], lx = tz, lz = -tx;
            float sin = Math.abs(tx * q[4] - tz * q[3]);
            if (sin < 0.75f) continue;                    // too oblique for a tidy diamond
            float dd = IC_SIDE / sin;
            if (sr - dd < 40f || sr + dd > r.len - 40f) continue;
            // the road's own bends: the terminals must really be ~100 m either side
            float[] a0 = new float[5], a1 = new float[5];
            r.point(sr + dd, a0);
            r.point(sr - dd, a1);
            float side0 = (a0[0] - p[0]) * lx + (a0[1] - p[1]) * lz, side1 = (a1[0] - p[0]) * lx + (a1[1] - p[1]) * lz;
            if (Math.abs(Math.abs(side0) - IC_SIDE) > 25f || Math.abs(Math.abs(side1) - IC_SIDE) > 25f || side0 * side1 > 0) continue;
            int hi = splitAt(r, sr + dd, RoadNode.Kind.JUNCTION);
            int lo = splitAt(r, sr - dd, RoadNode.Kind.JUNCTION);
            RoadNode tL = nodes.get(side0 > 0 ? hi : lo), tR = nodes.get(side0 > 0 ? lo : hi);
            diamond(f, sf, tL, tR);
            bridgeIcCount++;
        }
    }

    public int bridgeIcCount;

    private static long cellKey(float x, float z) {
        return ((long) Math.floor(x / 256f) << 32) ^ ((long) Math.floor(z / 256f) & 0xFFFFFFFFL);
    }

    /** True if (x, z) is within {@code r} of the end of the freeway that {@code f} is part of. */
    private boolean nearEnd(RoadEdge f, float x, float z, float r) {
        for (RoadNode n : nodes)
            if (n.kind == RoadNode.Kind.END && Math.hypot(n.x - x, n.z - z) < r) return true;
        return false;
    }

    /**
     * One-way slip road between freeway node fn and terminal t on side (sx,sz) of travel (hx,hz).
     * off: freeway→terminal; else terminal→freeway. Leaving, it first runs alongside the freeway
     * (the deceleration lane) before curving away and up to the crossroad, which it meets at a
     * right angle to the freeway; joining, the mirror image (an acceleration lane to merge).
     */
    private void ramp(RoadNode fn, RoadNode t, float sx, float sz, float hx, float hz, float lane, boolean off) {
        float ox = fn.x + sx * lane, oz = fn.z + sz * lane;
        // how far along the freeway the terminal is from where the slip road meets it
        float along = Math.abs((t.x - ox) * hx + (t.z - oz) * hz);
        float run = Math.min(190f, along * 0.55f), approach = Math.min(120f, along * 0.35f);
        float[][] pl;
        if (off) pl = bezier(ox, oz, ox + hx * run + sx * 2f, oz + hz * run + sz * 2f,
                t.x - hx * approach, t.z - hz * approach, t.x, t.z, RoadClass.RAMP.step);
        else pl = bezier(t.x, t.z, t.x + hx * approach, t.z + hz * approach,
                ox - hx * run + sx * 2f, oz - hz * run + sz * 2f, ox, oz, RoadClass.RAMP.step);
        // the slip road starts/ends on the freeway's kerb lane (a taper), not at the node centre
        RoadEdge e = off ? addEdge(RoadClass.RAMP, fn.id, t.id, pl[0], pl[1]) : addEdge(RoadClass.RAMP, t.id, fn.id, pl[0], pl[1]);
        e.rampSide = lane;
    }

    /**
     * Splits edge e at distance d into [a..new] (kept as e) and [new..b] (new edge). Returns the
     * new node id. Samples are copied; the split point is inserted exactly.
     */
    private int splitAt(RoadEdge e, float d, RoadNode.Kind kind) {
        float[] p = new float[5];
        e.point(d, p);
        int i = e.seg(d);
        RoadNode nd = addNode(p[0], p[1], kind);
        int n = e.count();
        float[] ax = new float[i + 2], az = new float[i + 2];
        System.arraycopy(e.x, 0, ax, 0, i + 1);
        System.arraycopy(e.z, 0, az, 0, i + 1);
        ax[i + 1] = p[0]; az[i + 1] = p[1];
        float[] bx = new float[n - i], bz = new float[n - i];
        bx[0] = p[0]; bz[0] = p[1];
        System.arraycopy(e.x, i + 1, bx, 1, n - i - 1);
        System.arraycopy(e.z, i + 1, bz, 1, n - i - 1);
        // drop zero-length pieces next to the cut
        float[][] A = dedupe(ax, az), B = dedupe(bx, bz);
        int oldB = e.b;
        nodes.get(oldB).removeEdge(e.id);
        e.b = nd.id;
        float[] oy = e.y;
        boolean[] ob = e.bridge;
        float oyAt = p[2];
        e.setPoints(A[0], A[1]);
        nd.addEdge(e.id);
        RoadEdge ne = addEdge(e.cls, nd.id, oldB, B[0], B[1]);
        // carry the height profile (and bridge flags) over to both pieces
        copyProfile(oy, ob, 0, e, A[0].length, i, oyAt, true);
        copyProfile(oy, ob, i + 1, ne, B[0].length, i, oyAt, false);
        nd.y = oyAt;
        ne.rampSide = e.rampSide;
        ne.origin = e.origin;
        ne.ns = e.ns;
        return nd.id;
    }

    /** Copies heights from the unsplit profile; the split point gets the interpolated height. */
    private static void copyProfile(float[] oy, boolean[] ob, int from, RoadEdge e, int n, int cut, float cutY, boolean first) {
        int on = oy.length;
        for (int k = 0; k < n; k++) {
            int src = first ? k : from + k - 1;
            float v;
            if (first) v = k < n - 1 ? oy[Math.min(on - 1, src)] : cutY;
            else v = k == 0 ? cutY : oy[Math.min(on - 1, src)];
            e.y[k] = v;
        }
        for (int k = 0; k < n - 1; k++) {
            int src = first ? Math.min(ob.length - 1, k) : Math.min(ob.length - 1, cut + k);
            e.bridge[k] = ob[src];
        }
    }

    private static float[][] dedupe(float[] x, float[] z) {
        int n = x.length, m = 0;
        float[] ox = new float[n], oz = new float[n];
        for (int i = 0; i < n; i++) {
            if (m > 0 && Math.abs(x[i] - ox[m - 1]) < 0.05f && Math.abs(z[i] - oz[m - 1]) < 0.05f) {
                if (i == n - 1) { ox[m - 1] = x[i]; oz[m - 1] = z[i]; }
                continue;
            }
            ox[m] = x[i]; oz[m] = z[i]; m++;
        }
        if (m < 2) { ox[1] = x[n - 1] + 0.1f; oz[1] = z[n - 1]; m = 2; }
        return new float[][]{Arrays.copyOf(ox, m), Arrays.copyOf(oz, m)};
    }

    // ------------------------------------------------------------------ crossings

    /** Pending bridge: a road passing over a motorway near (x,z). */
    private static final class BridgeMark implements java.io.Serializable {
        final float x, z, overY;
        final RoadEdge under;
        final float underS;
        BridgeMark(float x, float z, RoadEdge under, float underS) { this.x = x; this.z = z; this.under = under; this.underS = underS; this.overY = 0f; }
    }

    private final List<BridgeMark> bridgeMarks = new ArrayList<>();

    private void resolveCrossings() {
        // grid of pieces of rural roads
        HashMap<Long, List<int[]>> grid = new HashMap<>();
        for (RoadEdge e : edges) {
            if (e.cls.city) continue;
            for (int i = 0; i < e.count() - 1; i++) {
                int cx0 = (int) Math.floor(Math.min(e.x[i], e.x[i + 1]) / 512f), cx1 = (int) Math.floor(Math.max(e.x[i], e.x[i + 1]) / 512f);
                int cz0 = (int) Math.floor(Math.min(e.z[i], e.z[i + 1]) / 512f), cz1 = (int) Math.floor(Math.max(e.z[i], e.z[i + 1]) / 512f);
                for (int cz = cz0; cz <= cz1; cz++)
                    for (int cx = cx0; cx <= cx1; cx++)
                        grid.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), k -> new ArrayList<>()).add(new int[]{e.id, i});
            }
        }
        // find crossings
        List<float[]> junctions = new ArrayList<>();          // x, z, edgeA, edgeB
        HashMap<Long, Boolean> seen = new HashMap<>();
        for (List<int[]> list : grid.values()) {
            for (int p = 0; p < list.size(); p++)
                for (int q = p + 1; q < list.size(); q++) {
                    int[] A = list.get(p), B = list.get(q);
                    if (A[0] == B[0]) continue;
                    RoadEdge ea = edges.get(A[0]), eb = edges.get(B[0]);
                    float[] hit = intersect(ea.x[A[1]], ea.z[A[1]], ea.x[A[1] + 1], ea.z[A[1] + 1], eb.x[B[1]], eb.z[B[1]], eb.x[B[1] + 1], eb.z[B[1] + 1]);
                    if (hit == null) continue;
                    long key = ((long) Math.round(hit[0] / 2f) << 32) ^ (Math.round(hit[1] / 2f) & 0xFFFFFFFFL);
                    if (seen.put(key, Boolean.TRUE) != null) continue;
                    // ignore meeting at a shared node
                    if (nearNode(ea, hit[0], hit[1], 40f) || nearNode(eb, hit[0], hit[1], 40f)) continue;
                    boolean ma = ea.cls.motorway(), mb = eb.cls.motorway();
                    if (ma && mb) continue;
                    if (ma != mb) {
                        RoadEdge under = ma ? ea : eb;
                        float us = under.s[ma ? A[1] : B[1]] + hit[ma ? 2 : 3] * (under.s[(ma ? A[1] : B[1]) + 1] - under.s[ma ? A[1] : B[1]]);
                        bridgeMarks.add(new BridgeMark(hit[0], hit[1], under, us));
                    } else {
                        junctions.add(new float[]{hit[0], hit[1], ea.id, eb.id});
                    }
                }
        }
        // split crossing country roads into junctions (process each crossing on the current pieces)
        for (float[] j : junctions) {
            RoadNode nd = null;
            for (int k = 2; k < 4; k++) {
                RoadEdge e = pieceNear(j[0], j[1], (int) j[k]);
                if (e == null) continue;
                float d = project(e, j[0], j[1]);
                if (d < 30f || d > e.len - 30f) continue;
                if (nd == null) {
                    int id = splitAt(e, d, RoadNode.Kind.JUNCTION);
                    nd = nodes.get(id);
                } else {
                    splitAtNode(e, d, nd);
                }
            }
        }
    }

    /** Finds the piece (after earlier splits) of an original edge that passes near (x,z). */
    private RoadEdge pieceNear(float x, float z, int originalId) {
        RoadEdge best = null;
        float bd = 3f;
        for (RoadEdge e : edges) {
            if (e.origin != originalId) continue;
            float d = distTo(e, x, z);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private void splitAtNode(RoadEdge e, float d, RoadNode nd) {
        int tmp = splitAt(e, d, RoadNode.Kind.JUNCTION);
        // merge the temporary node into nd
        RoadNode t = nodes.get(tmp);
        for (int eid : t.edges) {
            RoadEdge ee = edges.get(eid);
            if (ee.a == tmp) { ee.a = nd.id; ee.x[0] = nd.x; ee.z[0] = nd.z; }
            if (ee.b == tmp) { ee.b = nd.id; ee.x[ee.count() - 1] = nd.x; ee.z[ee.count() - 1] = nd.z; }
            ee.setPoints(ee.x, ee.z);
            nd.addEdge(eid);
        }
        t.edges = new int[0];
        t.kind = RoadNode.Kind.END;   // orphan, never used
        t.dead = true;
    }

    private boolean nearNode(RoadEdge e, float x, float z, float r) {
        RoadNode a = nodes.get(e.a), b = nodes.get(e.b);
        return Math.hypot(a.x - x, a.z - z) < r || Math.hypot(b.x - x, b.z - z) < r;
    }

    private static float[] intersect(float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz) {
        float rx = bx - ax, rz = bz - az, sx = dx - cx, sz = dz - cz;
        float den = rx * sz - rz * sx;
        if (Math.abs(den) < 1e-6f) return null;
        float t = ((cx - ax) * sz - (cz - az) * sx) / den;
        float u = ((cx - ax) * rz - (cz - az) * rx) / den;
        if (t < 0f || t > 1f || u < 0f || u > 1f) return null;
        return new float[]{ax + rx * t, az + rz * t, t, u};
    }

    /** Distance along e of the closest point to (x,z). */
    static float project(RoadEdge e, float x, float z) {
        float best = Float.MAX_VALUE, bs = 0f;
        for (int i = 0; i < e.count() - 1; i++) {
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            float d = qx * qx + qz * qz;
            if (d < best) { best = d; bs = e.s[i] + t * (e.s[i + 1] - e.s[i]); }
        }
        return bs;
    }

    static float distTo(RoadEdge e, float x, float z) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < e.count() - 1; i++) {
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            best = Math.min(best, qx * qx + qz * qz);
        }
        return (float) Math.sqrt(best);
    }

    // ------------------------------------------------------------------ height profiles

    private void profiles() {
        // node heights: natural ground (above sea), freeway split nodes are refined below
        for (RoadNode n : nodes) if (n.kind != RoadNode.Kind.CITY) n.y = Math.max(2.5f, terrain.natural(n.x, n.z));
        // city streets follow the ground
        for (RoadEdge e : edges) if (e.cls.city) for (int i = 0; i < e.count(); i++) e.y[i] = terrain.natural(e.x[i], e.z[i]);
        // freeways first (their split nodes take the freeway's height), then slip roads, then the rest
        RoadClass[] order = {RoadClass.FREEWAY, RoadClass.RAMP, RoadClass.HIGHWAY, RoadClass.COUNTRY, RoadClass.TOWN};
        for (RoadClass cls : order) {
            if (cls == RoadClass.FREEWAY) {
                // smooth whole freeway chains across their split nodes
                for (RoadEdge e : edges) if (e.cls == cls) profile(e);
                for (RoadEdge e : edges) if (e.cls == cls) {
                    if (nodes.get(e.a).kind == RoadNode.Kind.SPLIT) nodes.get(e.a).y = e.y[0];
                    if (nodes.get(e.b).kind == RoadNode.Kind.SPLIT) nodes.get(e.b).y = e.y[e.count() - 1];
                }
                for (RoadEdge e : edges) if (e.cls == cls) pin(e);
            } else {
                for (RoadEdge e : edges) if (e.cls == cls) { profile(e); pin(e); }
            }
        }
        // bridges: lift roads over motorways
        java.util.Set<Long> spans = new java.util.HashSet<>();
        for (BridgeMark m : bridgeMarks) {
            float[] p = new float[5];
            m.under.point(m.underS, p);
            float clearY = p[2] + 7.2f;
            RoadEdge over = null;
            float bd = 3f;
            for (RoadEdge e : edges) {
                if (e.cls.city || e.cls.motorway()) continue;
                float d = distTo(e, m.x, m.z);
                if (d < bd) { bd = d; over = e; }
            }
            if (over == null) continue;
            float sc = project(over, m.x, m.z);
            float flat = m.under.cls.halfWidth() + 14f;
            liftAround(over, sc, clearY, flat);
            for (int i = 0; i < over.count() - 1; i++) {
                float d = Math.abs((over.s[i] + over.s[i + 1]) * 0.5f - sc);
                if (d < flat) over.bridge[i] = true;
                // the span over the motorway itself (and a margin each side) always stays a deck
                if (d < m.under.cls.halfWidth() + 10f) spans.add(((long) over.id << 32) | i);
            }
        }
        // a deck approach that would sit within 2 m of the natural ground is an embankment instead:
        // the road is painted on the shaped ground there, not a slab floating just above it
        for (RoadEdge e : edges)
            for (int i = 0; i < e.count() - 1; i++) {
                if (!e.bridge[i] || spans.contains(((long) e.id << 32) | i)) continue;
                float g0 = e.y[i] - terrain.natural(e.x[i], e.z[i]), g1 = e.y[i + 1] - terrain.natural(e.x[i + 1], e.z[i + 1]);
                float gm = (e.y[i] + e.y[i + 1]) * 0.5f - terrain.natural((e.x[i] + e.x[i + 1]) * 0.5f, (e.z[i] + e.z[i + 1]) * 0.5f);
                if (Math.max(g0, Math.max(g1, gm)) < 2f) e.bridge[i] = false;
            }
    }

    /** Steepest grade of a bridge approach (and of slip roads climbing to a raised crossroad). */
    private static final float APPROACH_GRADE = 0.055f;

    /**
     * Height a bridge approach needs {@code d} metres from the middle of a deck at {@code top}
     * (level for {@code flat} metres): a rounded crest, then a steady {@link #APPROACH_GRADE} down.
     */
    private static float approach(float d, float top, float flat) {
        float u = d - flat, crest = 40f;
        if (u <= 0f) return top;
        if (u < crest) return top - APPROACH_GRADE * u * u / (2f * crest);
        return top - APPROACH_GRADE * (u - crest * 0.5f);
    }

    /**
     * Raises a road to clear a motorway at distance sc along {@code over}, and carries the climb on
     * through the junctions at its ends into every road that meets them (slip roads included), so
     * that nothing joins a raised road at a step: the approaches come down at a steady grade until
     * they meet each road's own profile.
     */
    private void liftAround(RoadEdge over, float sc, float top, float flat) {
        for (int i = 0; i < over.count(); i++) over.y[i] = Math.max(over.y[i], approach(Math.abs(over.s[i] - sc), top, flat));
        java.util.ArrayDeque<float[]> queue = new java.util.ArrayDeque<>();     // node, distance from the deck
        queue.add(new float[]{over.a, sc});
        queue.add(new float[]{over.b, over.len - sc});
        while (!queue.isEmpty()) {
            float[] q = queue.poll();
            RoadNode nd = nodes.get((int) q[0]);
            float h = approach(q[1], top, flat);
            if (h <= nd.y + 0.01f) continue;
            nd.y = h;
            // every road at this junction climbs to it (a node only rises, so this ends)
            for (int eid : nd.edges) {
                RoadEdge e = edges.get(eid);
                if (e.cls == RoadClass.FREEWAY || e.cls.city) continue;
                boolean fromA = e.a == nd.id;
                for (int i = 0; i < e.count(); i++) {
                    float d = q[1] + (fromA ? e.s[i] : e.len - e.s[i]);
                    e.y[i] = Math.max(e.y[i], approach(d, top, flat));
                }
                queue.add(new float[]{fromA ? e.b : e.a, q[1] + e.len});
            }
        }
    }

    // ------------------------------------------------------------------ farm lanes

    /** Farmsteads: {x0, z0, x1, z1, face (0=N,1=S,2=W,3=E: side toward the lane), seed}. */
    public final List<float[]> farms = new ArrayList<>();
    private transient java.util.HashSet<Long> farmCells = new java.util.HashSet<>();

    /**
     * Places farmsteads beside country roads and highways. Each gets its own access lane that
     * branches off the road at a T-junction and ends in the farm yard.
     */
    private void farms() {
        Random r = new Random(seed * 13L + 5);
        float[] p = new float[5];
        List<float[]> picks = new ArrayList<>();           // edge, s, side, farm index
        int edgeCount = edges.size();
        for (int ei = 0; ei < edgeCount; ei++) {
            RoadEdge e = edges.get(ei);
            if (e.cls != RoadClass.COUNTRY && e.cls != RoadClass.HIGHWAY) continue;
            float s = 140f + r.nextFloat() * 300f;
            while (s < e.len - 140f) {
                e.point(s, p);
                int side = r.nextBoolean() ? 1 : -1;
                float lx = p[4] * side, lz = -p[3] * side;
                float off = e.cls.halfWidth() + 20f + r.nextFloat() * 14f;
                float cx = p[0] + lx * off, cz = p[1] + lz * off;
                // the yard faces the lane (dominant axis toward the road)
                int face = Math.abs(lx) > Math.abs(lz) ? (lx > 0 ? 2 : 3) : (lz > 0 ? 0 : 1);
                boolean wide = face < 2;
                float hu = 15f, hv = 13f;
                float x0 = cx - (wide ? hu : hv), x1 = cx + (wide ? hu : hv), z0 = cz - (wide ? hv : hu), z1 = cz + (wide ? hv : hu);
                if (!e.bridge[e.seg(s)] && laneClear(p[0] + lx * (e.cls.halfWidth() + 4f), p[1] + lz * (e.cls.halfWidth() + 4f), cx, cz)
                        && farmSite(x0, z0, x1, z1)) {
                    farms.add(new float[]{x0, z0, x1, z1, face, r.nextInt()});
                    picks.add(new float[]{ei, s, side, farms.size() - 1});
                }
                s += 300f + r.nextFloat() * 560f;
            }
        }
        // split roads from the far end of each edge so earlier distances stay valid
        picks.sort((A, B) -> A[0] != B[0] ? Float.compare(A[0], B[0]) : Float.compare(B[1], A[1]));
        for (float[] pk : picks) {
            RoadEdge e = edges.get((int) pk[0]);
            float[] f = farms.get((int) pk[3]);
            int jn = splitAt(e, pk[1], RoadNode.Kind.JUNCTION);
            RoadNode j = nodes.get(jn);
            int face = (int) f[4];
            float fx, fz, dx, dz;   // yard gate and the direction into the yard
            switch (face) {
                case 0: fx = (f[0] + f[2]) * 0.5f; fz = f[1]; dx = 0; dz = 1; break;
                case 1: fx = (f[0] + f[2]) * 0.5f; fz = f[3]; dx = 0; dz = -1; break;
                case 2: fx = f[0]; fz = (f[1] + f[3]) * 0.5f; dx = 1; dz = 0; break;
                default: fx = f[2]; fz = (f[1] + f[3]) * 0.5f; dx = -1; dz = 0; break;
            }
            RoadNode end = addNode(fx + dx * 3f, fz + dz * 3f, RoadNode.Kind.END);
            e.point(e.len, p);   // at the junction (end of the first piece)
            float side = pk[2];
            float lx = p[4] * side, lz = -p[3] * side;
            float hw = e.cls.halfWidth();
            float[][] pl = bezier(j.x, j.z, j.x + lx * (hw + 10f), j.z + lz * (hw + 10f),
                    fx - dx * 10f, fz - dz * 10f, end.x, end.z, RoadClass.LANE.step);
            RoadEdge lane = addEdge(RoadClass.LANE, j.id, end.id, pl[0], pl[1]);
            lane.surface = r.nextInt(3) == 0 ? RoadEdge.DIRT : RoadEdge.GRAVEL;
            profile(lane);
            pin(lane);
        }
    }

    /** Rural homes off the farm tracks: {x0, z0, x1, z1, face toward the driveway, seed}. */
    public final List<float[]> homes = new ArrayList<>();

    /**
     * Unsealed farm roads: a gravel or dirt track leaves a country road or highway at a T-junction
     * and winds up to 500 m into the fields, with several properties along it (farmsteads and
     * country houses, alternating sides), each reached by its own short driveway, and a farm at
     * the end of the road.
     */
    private void farmTracks() {
        Random r = new Random(seed * 41L + 9);
        float[] p = new float[5];
        List<float[]> picks = new ArrayList<>();     // edge, s, side
        int edgeCount = edges.size();
        for (int ei = 0; ei < edgeCount; ei++) {
            RoadEdge e = edges.get(ei);
            if (e.cls != RoadClass.COUNTRY && e.cls != RoadClass.HIGHWAY) continue;
            float s = 260f + r.nextFloat() * 500f;
            while (s < e.len - 120f) {
                if (!e.bridge[e.seg(s)] && s > e.clearA + 80f && s < e.len - e.clearB - 80f)
                    picks.add(new float[]{ei, s, r.nextBoolean() ? 1 : -1});
                s += 700f + r.nextFloat() * 1100f;
            }
        }
        picks.sort((A, B) -> A[0] != B[0] ? Float.compare(A[0], B[0]) : Float.compare(B[1], A[1]));
        List<Object[]> plans = new ArrayList<>();
        for (float[] pk : picks) {
            RoadEdge e = edges.get((int) pk[0]);
            float s = pk[1], side = pk[2];
            e.point(s, p);
            float hw = e.cls.halfWidth();
            float nx = p[4] * side, nz = -p[3] * side;
            // plan the track: straight out from the road, then wandering gently
            float length = 220f + r.nextFloat() * 280f;
            List<float[]> wp = new ArrayList<>();
            wp.add(new float[]{p[0], p[1]});
            float x = p[0] + nx * (hw + 14f), z = p[1] + nz * (hw + 14f);
            wp.add(new float[]{x, z});
            float hx = nx, hz = nz, run = hw + 14f;
            boolean ok = true;
            List<Long> cells = new ArrayList<>();
            while (run < length) {
                float turn = (r.nextFloat() - 0.5f) * 0.7f;
                float c = (float) Math.cos(turn), sn = (float) Math.sin(turn);
                float tx = hx * c - hz * sn, tz = hx * sn + hz * c;
                hx = tx; hz = tz;
                float step = 55f + r.nextFloat() * 25f;
                float qx = x + hx * step, qz = z + hz * step;
                float h0 = terrain.natural(x, z), h1 = terrain.natural(qx, qz);
                if (Math.abs(h1 - h0) / step > 0.11f || h1 < 3f || terrain.coastOver(qx, qz) > -120f
                        || terrain.cityDistance(qx, qz) < 1300f || city.nearUrban(qx, qz, 300f)
                        || roadClearance(qx, qz, 40f) < 30f || townNear(qx, qz, 80f) || !trackCellsFree(qx, qz, cells)) break;
                x = qx; z = qz;
                run += step;
                wp.add(new float[]{x, z});
            }
            if (run < 160f) continue;
            float[][] pl = spline(wp, RoadClass.LANE.step);
            float[] ls = new float[pl[0].length];
            for (int i = 1; i < ls.length; i++) ls[i] = ls[i - 1] + (float) Math.hypot(pl[0][i] - pl[0][i - 1], pl[1][i] - pl[1][i - 1]);
            float total = ls[ls.length - 1];
            // properties along the way (alternating sides) and one at the end
            List<int[]> stops = new ArrayList<>();     // vertex index, side (0 = end of road)
            int sideNow = r.nextBoolean() ? 1 : -1;
            for (float d = 70f + r.nextFloat() * 40f; d < total - 70f; d += 95f + r.nextFloat() * 70f) {
                int vi = 1;
                while (vi < ls.length - 2 && ls[vi] < d) vi++;
                stops.add(new int[]{vi, sideNow});
                sideNow = -sideNow;
            }
            stops.add(new int[]{ls.length - 1, 0});
            // place the sites first; drop the stops that do not fit
            List<float[]> placed = new ArrayList<>();   // vi, side, kind (0 farm, 1 home), gate x, z, dx, dz, list index
            for (int[] st : stops) {
                int vi = st[0];
                float px = pl[0][vi], pz = pl[1][vi];
                int a = Math.max(0, vi - 1), b = Math.min(ls.length - 1, vi + 1);
                float tx = pl[0][b] - pl[0][a], tz = pl[1][b] - pl[1][a], tl = (float) Math.hypot(tx, tz);
                tx /= tl; tz /= tl;
                float ox, oz, off;
                if (st[1] == 0) { ox = tx; oz = tz; off = 6f; }       // straight on at the end of the road
                else { ox = tz * st[1]; oz = -tx * st[1]; off = 0f; }
                boolean farm = st[1] == 0 || r.nextInt(5) < 2;
                float dist = st[1] == 0 ? off : 16f + r.nextFloat() * (farm ? 30f : 22f);
                float half = farm ? 15f : 9f, deep = farm ? 13f : 8f;
                float cx = px + ox * (dist + deep), cz = pz + oz * (dist + deep);
                int face = Math.abs(ox) > Math.abs(oz) ? (ox > 0 ? 2 : 3) : (oz > 0 ? 0 : 1);
                boolean wide = face < 2;
                float x0 = cx - (wide ? half : deep), x1 = cx + (wide ? half : deep), z0 = cz - (wide ? deep : half), z1 = cz + (wide ? deep : half);
                if (!propertySite(x0, z0, x1, z1, pl, vi)) continue;
                float[] rec = {x0, z0, x1, z1, face, r.nextInt()};
                if (farm) { farms.add(rec); placed.add(new float[]{vi, st[1], 0, farms.size() - 1}); }
                else { homes.add(rec); placed.add(new float[]{vi, st[1], 1, homes.size() - 1}); }
            }
            if (placed.isEmpty()) continue;
            for (long k : cells) farmCells.add(k);
            plans.add(new Object[]{pk, pl, placed, r.nextInt(5) < 3 ? RoadEdge.GRAVEL : RoadEdge.DIRT, r.nextInt()});
        }
        // now build them: split the roads from the far end of each edge so earlier distances stay valid
        for (Object[] plan : plans) {
            float[] pk = (float[]) plan[0];
            float[][] pl = (float[][]) plan[1];
            @SuppressWarnings("unchecked") List<float[]> placed = (List<float[]>) plan[2];
            int surface = (Integer) plan[3];
            Random r2 = new Random((Integer) plan[4]);
            RoadEdge e = edges.get((int) pk[0]);
            float s = pk[1];
            // cut the track short after the last property
            int lastV = 0;
            for (float[] q : placed) lastV = Math.max(lastV, (int) q[0]);
            int jn = splitAt(e, s, RoadNode.Kind.JUNCTION);
            int prev = jn, prevV = 0;
            placed.sort((A, B) -> Float.compare(A[0], B[0]));
            List<int[]> branchNodes = new ArrayList<>();
            for (int qi = 0; qi < placed.size(); qi++) {
                int vi = (int) placed.get(qi)[0];
                if (vi <= prevV) vi = prevV + 1;
                if (vi >= pl[0].length) break;
                boolean end = vi >= lastV;
                RoadNode nd = addNode(pl[0][vi], pl[1][vi], end ? RoadNode.Kind.END : RoadNode.Kind.JUNCTION);
                RoadEdge tr = addEdge(RoadClass.LANE, prev, nd.id, Arrays.copyOfRange(pl[0], prevV, vi + 1), Arrays.copyOfRange(pl[1], prevV, vi + 1));
                tr.surface = surface;
                profile(tr);
                pin(tr);
                branchNodes.add(new int[]{nd.id, qi});
                prev = nd.id;
                prevV = vi;
                if (end) break;
            }
            // driveways from the track to each gate
            for (int[] bn : branchNodes) {
                float[] q = placed.get(bn[1]);
                float[] f = q[2] == 0 ? farms.get((int) q[3]) : homes.get((int) q[3]);
                RoadNode j = nodes.get(bn[0]);
                int face = (int) f[4];
                float fx, fz, dx, dz;
                switch (face) {
                    case 0: fx = (f[0] + f[2]) * 0.5f; fz = f[1]; dx = 0; dz = 1; break;
                    case 1: fx = (f[0] + f[2]) * 0.5f; fz = f[3]; dx = 0; dz = -1; break;
                    case 2: fx = f[0]; fz = (f[1] + f[3]) * 0.5f; dx = 1; dz = 0; break;
                    default: fx = f[2]; fz = (f[1] + f[3]) * 0.5f; dx = -1; dz = 0; break;
                }
                if (Math.hypot(fx - j.x, fz - j.z) < 5f) continue;
                RoadNode gate = addNode(fx + dx * 3f, fz + dz * 3f, RoadNode.Kind.END);
                float mx = (j.x + fx) * 0.5f, mz = (j.z + fz) * 0.5f;
                float[][] dw = bezier(j.x, j.z, mx, mz, fx - dx * 6f, fz - dz * 6f, gate.x, gate.z, RoadClass.LANE.step);
                RoadEdge drive = addEdge(RoadClass.LANE, j.id, gate.id, dw[0], dw[1]);
                drive.surface = q[2] == 0 ? surface : (r2.nextInt(3) == 0 ? RoadEdge.PAVED : RoadEdge.GRAVEL);
                profile(drive);
                pin(drive);
            }
            trackCount++;
        }
    }

    public int trackCount;

    private boolean townNear(float x, float z, float margin) {
        for (Town t : towns) if (t.size != Town.Size.HAMLET && x > t.x0 - margin && x < t.x1 + margin && z > t.z0 - margin && z < t.z1 + margin) return true;
        return false;
    }

    /** The 32 m cells around a track point are not used by a farm or another track yet (collects them). */
    private boolean trackCellsFree(float x, float z, List<Long> cells) {
        int a = (int) Math.floor(x / 32f), b = (int) Math.floor(z / 32f);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                long k = ((long) (a + dx) << 32) ^ ((b + dz) & 0xFFFFFFFFL);
                if (farmCells.contains(k)) return false;
            }
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) cells.add(((long) (a + dx) << 32) ^ ((b + dz) & 0xFFFFFFFFL));
        return true;
    }

    /** A property beside a track: on gentle open land, clear of every road (the track included). */
    private boolean propertySite(float x0, float z0, float x1, float z1, float[][] track, int vi) {
        // keep 5 m off the track itself
        for (int i = 0; i < track[0].length; i++) {
            float tx = track[0][i], tz = track[1][i];
            if (tx > x0 - 5f && tx < x1 + 5f && tz > z0 - 5f && tz < z1 + 5f) return false;
        }
        return farmSite(x0, z0, x1, z1);
    }

    /** The straight run from the road to the yard crosses no other road. */
    private boolean laneClear(float ax, float az, float bx, float bz) {
        for (int k = 0; k <= 8; k++) {
            float t = k / 8f;
            if (roadClearance(ax + (bx - ax) * t, az + (bz - az) * t, 20f) < 2f) return false;
        }
        return true;
    }

    private boolean farmSite(float x0, float z0, float x1, float z1) {
        if (terrain.cityDistance((x0 + x1) * 0.5f, (z0 + z1) * 0.5f) < 1300f) return false;
        for (Town t : towns) if (t.size != Town.Size.HAMLET && x1 > t.x0 - 60f && x0 < t.x1 + 60f && z1 > t.z0 - 60f && z0 < t.z1 + 60f) return false;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        float[][] pts = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {(x0 + x1) * 0.5f, (z0 + z1) * 0.5f}};
        for (float[] q : pts) {
            if (terrain.coastOver(q[0], q[1]) > -150f) return false;
            if (roadClearance(q[0], q[1], 40f) < 6f) return false;
            float h = terrain.natural(q[0], q[1]);
            lo = Math.min(lo, h); hi = Math.max(hi, h);
        }
        if (hi - lo > 4.5f || lo < 3f) return false;
        int a = (int) Math.floor(x0 / 32f) - 1, b = (int) Math.floor(x1 / 32f) + 1, c = (int) Math.floor(z0 / 32f) - 1, d = (int) Math.floor(z1 / 32f) + 1;
        for (int z = c; z <= d; z++) for (int x = a; x <= b; x++) if (farmCells.contains(((long) x << 32) ^ (z & 0xFFFFFFFFL))) return false;
        for (int z = c; z <= d; z++) for (int x = a; x <= b; x++) farmCells.add(((long) x << 32) ^ (z & 0xFFFFFFFFL));
        return true;
    }

    // ------------------------------------------------------------------ junction geometry

    /** Smallest angle between two roads leaving a junction. */
    private static final float MIN_JUNCTION_ANGLE = (float) Math.toRadians(38);

    /**
     * Real junctions don't have roads leaving at grazing angles: where two roads leave a junction
     * closer than ~38 degrees, their first stretch is bent apart (the bend fades out over ~120 m)
     * so the junction gets a proper shape instead of a long sliver.
     */
    private void splay() {
        float[] u = new float[2];
        for (int pass = 0; pass < 3; pass++) {
            for (RoadNode n : nodes) {
                if (n.dead || n.kind == RoadNode.Kind.CITY || n.kind == RoadNode.Kind.SPLIT || n.edges.length < 2) continue;
                int k = n.edges.length;
                float[] ang = new float[k];
                Integer[] order = new Integer[k];
                for (int i = 0; i < k; i++) {
                    outwardFar(edges.get(n.edges[i]), n, u);
                    ang[i] = (float) Math.atan2(u[1], u[0]);
                    order[i] = i;
                }
                java.util.Arrays.sort(order, (A, B) -> Float.compare(ang[A], ang[B]));
                for (int q = 0; q < k; q++) {
                    int i = order[q], j = order[(q + 1) % k];
                    float th = ang[j] - ang[i];
                    if (q == k - 1) th += (float) (2 * Math.PI);
                    if (th >= MIN_JUNCTION_ANGLE) continue;
                    float need = (MIN_JUNCTION_ANGLE - th) * 0.5f + 0.01f;
                    RoadEdge ei = edges.get(n.edges[i]), ej = edges.get(n.edges[j]);
                    // the motorway system and the more important road move less
                    float wi = ei.cls.motorway() || ei.cls == RoadClass.TOWN ? 0f : (ei.cls.priority > ej.cls.priority ? 0.5f : 1f);
                    float wj = ej.cls.motorway() || ej.cls == RoadClass.TOWN ? 0f : (ej.cls.priority > ei.cls.priority ? 0.5f : 1f);
                    if (wi + wj == 0f) continue;
                    float tot = need * 2f / (wi + wj);
                    bend(ei, n, -tot * wi);
                    bend(ej, n, tot * wj);
                }
            }
        }
    }

    /** Outward direction measured further out (25 m), robust to tiny kinks at the node. */
    private void outwardFar(RoadEdge e, RoadNode n, float[] out) {
        float d = Math.min(25f, e.len * 0.45f);
        float[] p = new float[5], q = new float[5];
        e.point(n.id == e.a ? 0f : e.len, p);
        e.point(n.id == e.a ? d : e.len - d, q);
        float dx = q[0] - p[0], dz = q[1] - p[1], l = (float) Math.sqrt(dx * dx + dz * dz);
        out[0] = l > 1e-4f ? dx / l : 1f;
        out[1] = l > 1e-4f ? dz / l : 0f;
    }

    /** Rotates the start of an edge about node n by an angle that fades out along the road. */
    private void bend(RoadEdge e, RoadNode n, float angle) {
        if (Math.abs(angle) < 1e-4f) return;
        float L = Math.min(140f, e.len * 0.45f);
        int cnt = e.count();
        for (int k = 0; k < cnt; k++) {
            float d = n.id == e.a ? e.s[k] : e.len - e.s[k];
            if (d <= 0f || d >= L) continue;
            float f = 1f - d / L;
            float a = angle * f * f * (3f - 2f * f) ;
            float dx = e.x[k] - n.x, dz = e.z[k] - n.z;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            e.x[k] = n.x + dx * c - dz * sn;
            e.z[k] = n.z + dx * sn + dz * c;
        }
        // distances change a little; heights stay with their samples
        float[] y = e.y;
        boolean[] br = e.bridge;
        e.setPoints(e.x, e.z);
        e.y = y;
        e.bridge = br;
    }

    /** Mouth length (stop line / crossing strip) at the end of an edge arriving at node n. */
    public float mouth(RoadEdge e, RoadNode n) {
        if (n.kind == RoadNode.Kind.CITY) return e.cls.city ? 5f : 3f;
        if (n.kind == RoadNode.Kind.JUNCTION) return 3f;
        return 0f;
    }

    /** Junction corner fillet radius at a node. */
    public float fillet(RoadNode n) {
        if (n.kind == RoadNode.Kind.CITY) return 0f;
        float w = 0f;
        boolean town = true;
        for (int id : n.edges) {
            w = Math.max(w, edges.get(id).cls.halfWidth());
            if (edges.get(id).cls != RoadClass.TOWN) town = false;
        }
        if (town) return 1.5f;          // tight town corners
        return Math.min(9f, 2.5f + w * 0.5f);
    }

    /** Outward unit direction of edge e at node n (toward the rest of the road). */
    public void outward(RoadEdge e, RoadNode n, float[] out) {
        float d = Math.min(8f, e.len * 0.5f);
        float[] p = new float[5], q = new float[5];
        e.point(n.id == e.a ? 0f : e.len, p);
        e.point(n.id == e.a ? d : e.len - d, q);
        float dx = q[0] - p[0], dz = q[1] - p[1], l = (float) Math.sqrt(dx * dx + dz * dz);
        out[0] = l > 1e-4f ? dx / l : 1f;
        out[1] = l > 1e-4f ? dz / l : 0f;
    }

    /**
     * Sets every edge's clearance at each end from the junction geometry: far enough back that the
     * kerb lines of neighbouring roads have separated by the corner fillet radius.
     */
    private void clearances() {
        float[] u = new float[2];
        for (RoadNode n : nodes) {
            if (n.dead || n.edges.length == 0) continue;
            int k = n.edges.length;
            if (n.kind == RoadNode.Kind.SPLIT) {
                for (int id : n.edges) setClear(edges.get(id), n, 1f);
                continue;
            }
            if (k == 1) { setClear(edges.get(n.edges[0]), n, n.kind == RoadNode.Kind.END ? 2f : 1.5f); continue; }
            float[] ang = new float[k], cl = new float[k], hw = new float[k];
            Integer[] order = new Integer[k];
            for (int i = 0; i < k; i++) {
                RoadEdge e = edges.get(n.edges[i]);
                outward(e, n, u);
                ang[i] = (float) Math.atan2(u[1], u[0]);
                hw[i] = e.cls.halfWidth();
                cl[i] = n.kind == RoadNode.Kind.CITY ? 0f : 1.5f;
                order[i] = i;
            }
            java.util.Arrays.sort(order, (A, B) -> Float.compare(ang[A], ang[B]));
            float r = fillet(n);
            for (int q = 0; q < k; q++) {
                int i = order[q], j = order[(q + 1) % k];
                float th = ang[j] - ang[i];
                if (q == k - 1) th += (float) (2 * Math.PI);
                if (th >= Math.PI - 0.02f) continue;               // open side: kerbs never meet
                th = Math.max(0.12f, th);
                float sin = (float) Math.sin(th), cos = (float) Math.cos(th);
                float di = (hw[j] + hw[i] * cos) / sin, dj = (hw[i] + hw[j] * cos) / sin;
                cl[i] = Math.max(cl[i], di + r);
                cl[j] = Math.max(cl[j], dj + r);
            }
            for (int i = 0; i < k; i++) {
                RoadEdge e = edges.get(n.edges[i]);
                setClear(e, n, Math.min(Math.min(70f, e.len * 0.4f), cl[i]));
            }
        }
    }

    private static void setClear(RoadEdge e, RoadNode n, float c) {
        if (n.id == e.a) e.clearA = c;
        if (n.id == e.b) e.clearB = c;
    }

    /**
     * Junction outline: each road's two kerb corners at its clearance, in angular order, joined by
     * curved kerb returns (square corners in the city grid).
     */
    private void ring(RoadNode n) {
        if (n.dead || n.kind == RoadNode.Kind.SPLIT || n.edges.length < 2) return;
        int k = n.edges.length;
        boolean cityNode = n.kind == RoadNode.Kind.CITY;
        float[] ang = new float[k], u = new float[2], p = new float[5];
        float[][] L = new float[k][], R = new float[k][], dir = new float[k][];
        Integer[] order = new Integer[k];
        for (int i = 0; i < k; i++) {
            RoadEdge ed = edges.get(n.edges[i]);
            outward(ed, n, u);
            ang[i] = (float) Math.atan2(u[1], u[0]);
            ed.point(n.id == ed.a ? ed.clearA : ed.len - ed.clearB, p);
            float hw = ed.cls.halfWidth(), lx = u[1], lz = -u[0];
            L[i] = new float[]{p[0] + lx * hw, p[2], p[1] + lz * hw};
            R[i] = new float[]{p[0] - lx * hw, p[2], p[1] - lz * hw};
            dir[i] = new float[]{u[0], u[1]};
            order[i] = i;
        }
        java.util.Arrays.sort(order, (A, B) -> Float.compare(ang[A], ang[B]));
        float[] ring = new float[k * 24 * 3];
        boolean[] kerb = new boolean[k * 24];
        int m = 0;
        for (int q = 0; q < k; q++) {
            int i = order[q], j = order[(q + 1) % k];
            System.arraycopy(L[i], 0, ring, m * 3, 3); kerb[m] = false; m++;
            System.arraycopy(R[i], 0, ring, m * 3, 3); kerb[m] = true; m++;
            float th = ang[j] - ang[i];
            if (q == k - 1) th += (float) (2 * Math.PI);
            if (th >= Math.PI - 0.05f) {
                // open side: follow road i's kerb back to the node, round the node, out along road j's
                // kerb, so the trimmed road ends between the node and the mouths are paved too
                RoadEdge ei = edges.get(n.edges[i]), ej = edges.get(n.edges[j]);
                float hi = ei.cls.halfWidth(), hj = ej.cls.halfWidth();
                float a0 = ang[i] + (float) (Math.PI / 2), a1 = ang[j] - (float) (Math.PI / 2);
                if (q == k - 1) a1 += (float) (2 * Math.PI);
                int steps = Math.max(1, (int) Math.ceil((a1 - a0) / 0.5f));
                for (int t = 0; t <= steps; t++) {
                    float f = t / (float) steps, an = a0 + (a1 - a0) * f, rad = hi + (hj - hi) * f;
                    ring[m * 3] = n.x + (float) Math.cos(an) * rad;
                    ring[m * 3 + 1] = n.y;
                    ring[m * 3 + 2] = n.z + (float) Math.sin(an) * rad;
                    kerb[m] = true;
                    m++;
                }
                continue;
            }
            float[] c = kerbHit(R[i], dir[i], L[j], dir[j]);
            if (c == null) continue;
            if (cityNode) {
                ring[m * 3] = c[0]; ring[m * 3 + 1] = (R[i][1] + L[j][1]) * 0.5f; ring[m * 3 + 2] = c[1]; kerb[m] = true; m++;
            } else {
                for (int t = 1; t <= 4; t++) {
                    float f = t / 5f, g = 1 - f;
                    ring[m * 3] = g * g * R[i][0] + 2 * g * f * c[0] + f * f * L[j][0];
                    ring[m * 3 + 1] = R[i][1] + (L[j][1] - R[i][1]) * f;
                    ring[m * 3 + 2] = g * g * R[i][2] + 2 * g * f * c[1] + f * f * L[j][2];
                    kerb[m] = true;
                    m++;
                }
            }
        }
        n.ring = java.util.Arrays.copyOf(ring, m * 3);
        n.kerb = java.util.Arrays.copyOf(kerb, m);
        float r = 0f;
        for (int v = 0; v < m; v++) r = Math.max(r, (float) Math.hypot(ring[v * 3] - n.x, ring[v * 3 + 2] - n.z));
        n.ringRadius = r;
    }

    /** Where two kerb lines meet, behind the corners (toward the junction); null if they don't. */
    private static float[] kerbHit(float[] pp, float[] d, float[] q, float[] e2) {
        float den = d[0] * e2[1] - d[1] * e2[0];
        if (Math.abs(den) < 1e-4f) return null;
        float t = ((q[0] - pp[0]) * e2[1] - (q[2] - pp[2]) * e2[0]) / den;
        if (t > 0.5f) return null;
        return new float[]{pp[0] + d[0] * t, pp[2] + d[1] * t};
    }

    /** True if (x,z) is inside a junction outline, or within {@code margin} of it. */
    public boolean inJunction(float x, float z, float margin) {
        int cx = (int) Math.floor(x / 64f), cz = (int) Math.floor(z / 64f);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                List<RoadNode> l = nodeChunks.get(((long) (cx + dx) << 32) ^ ((cz + dz) & 0xFFFFFFFFL));
                if (l == null) continue;
                for (RoadNode n : l) {
                    if (n.ring == null) continue;
                    float ddx = x - n.x, ddz = z - n.z;
                    if (ddx * ddx + ddz * ddz > (n.ringRadius + margin) * (n.ringRadius + margin)) continue;
                    if (insideRing(n.ring, x, z) || ringDistance(n.ring, x, z) < margin) return true;
                }
            }
        return false;
    }

    /** True if (x,z) is within {@code margin} of a junction's outline radius (near enough to need care). */
    public boolean nearJunction(float x, float z, float margin) {
        int cx = (int) Math.floor(x / 64f), cz = (int) Math.floor(z / 64f);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                List<RoadNode> l = nodeChunks.get(((long) (cx + dx) << 32) ^ ((cz + dz) & 0xFFFFFFFFL));
                if (l == null) continue;
                for (RoadNode n : l) {
                    float r = (n.ring != null ? n.ringRadius : 30f) + margin;
                    if (n.kind == RoadNode.Kind.CITY) continue;
                    float ddx = x - n.x, ddz = z - n.z;
                    if (ddx * ddx + ddz * ddz < r * r) return true;
                }
            }
        return false;
    }

    private static boolean insideRing(float[] r, float x, float z) {
        int m = r.length / 3;
        boolean in = false;
        for (int i = 0, j = m - 1; i < m; j = i++) {
            float xi = r[i * 3], zi = r[i * 3 + 2], xj = r[j * 3], zj = r[j * 3 + 2];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    private static float ringDistance(float[] r, float x, float z) {
        int m = r.length / 3;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            int j = (i + 1) % m;
            float ax = r[i * 3], az = r[i * 3 + 2], vx = r[j * 3] - ax, vz = r[j * 3 + 2] - az;
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - ax) * vx + (z - az) * vz) / l2)) : 0f;
            float qx = ax + vx * t - x, qz = az + vz * t - z;
            best = Math.min(best, qx * qx + qz * qz);
        }
        return (float) Math.sqrt(best);
    }

    /**
     * True if (x,z) is on (or within {@code margin} of) any paved surface: a road outside the
     * city other than {@code except}, a city street, or a junction.
     */
    public boolean onAsphalt(float x, float z, float margin, int except) {
        int c = cellIndex(x, z);
        if (c >= 0) {
            for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                int it = cellItems[k];
                int eid = it >>> 16;
                if (eid == except) continue;
                RoadEdge e = edges.get(eid);
                int i = it & 0xFFFF;
                if (e.bridge[i]) continue;
                if (pieceDist(e, i, x, z) < e.cls.halfWidth() + margin) return true;
            }
        }
        return inJunction(x, z, margin);
    }

    /** How a terrain cell next to roads is drawn: 0 normally, 1 not at all (road verges cover it), 2 finely around the paving. */
    public int cellMode(float cx, float cz) {
        int c = ruralIndex(cx, cz);
        int near = -1;
        boolean other = false, ramp = false;
        int[] reach = new int[8];
        int nr = 0;
        if (c >= 0) {
            for (int k = ruralStart[c]; k < ruralStart[c + 1]; k++) {
                int it = ruralItems[k];
                RoadEdge e = edges.get(it >>> 16);
                if (e.cls.city) continue;
                int i = it & 0xFFFF;
                if (e.bridge[i]) continue;
                float d = pieceDist(e, i, cx, cz), hw = e.cls.halfWidth();
                if (d < hw + 5.8f) {
                    if (near >= 0 && near != e.id) other = true;
                    near = e.id;
                    if (e.cls == RoadClass.RAMP) ramp = true;
                }
                if (d < hw + CORRIDOR_WIDTH + 6f && nr < reach.length) {
                    boolean seen = false;
                    for (int q = 0; q < nr; q++) if (reach[q] == e.id) seen = true;
                    if (!seen) reach[nr++] = e.id;
                }
            }
        }
        boolean nearJ = nearJunction(cx, cz, CORRIDOR_WIDTH + 6f);
        if (near < 0) return nearJ && inJunction(cx, cz, 6f) ? 2 : 0;
        for (int q = 0; q < nr; q++) if (reach[q] != near) other = true;
        if (other || nearJ || ramp) return 2;
        return 1;
    }

    /** True if another road (or a junction) comes close to this piece, so its verges need clipping. */
    public boolean crowded(RoadEdge self, int piece) {
        float mx = (self.x[piece] + self.x[piece + 1]) * 0.5f, mz = (self.z[piece] + self.z[piece + 1]) * 0.5f;
        float reach = self.cls.halfWidth() + CORRIDOR_WIDTH + 6f;
        if (nearJunction(mx, mz, reach)) return true;
        int c = cellIndex(mx, mz);
        if (c < 0) return false;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            if ((it >>> 16) == self.id) continue;
            RoadEdge e = edges.get(it >>> 16);
            if (pieceDist(e, it & 0xFFFF, mx, mz) < reach + e.cls.halfWidth()) return true;
        }
        return false;
    }

    private final HashMap<Long, List<RoadNode>> nodeChunks = new HashMap<>();

    private void indexNodes() {
        for (RoadNode n : nodes) {
            if (n.dead || n.edges.length == 0) continue;
            long k = ((long) Math.floorDiv((int) Math.floor(n.x), 64) << 32) ^ (Math.floorDiv((int) Math.floor(n.z), 64) & 0xFFFFFFFFL);
            nodeChunks.computeIfAbsent(k, kk -> new ArrayList<>()).add(n);
        }
    }

    /** Junction nodes whose centre lies in the 64 m chunk (cx, cz). */
    public List<RoadNode> nodesInChunk(int cx, int cz) {
        List<RoadNode> l = nodeChunks.get(((long) cx << 32) ^ (cz & 0xFFFFFFFFL));
        return l == null ? java.util.Collections.emptyList() : l;
    }

    private float clearance(RoadEdge e, RoadNode n) {
        if (n.kind == RoadNode.Kind.SPLIT) return 1f;
        if (n.kind == RoadNode.Kind.CITY) {
            // stop at the edge of the city street it joins
            float[] p = new float[5];
            e.point(n.id == e.a ? 0f : e.len, p);
            boolean alongX = Math.abs(p[3]) > Math.abs(p[4]);
            return alongX ? (city.rx1[n.ci] - city.rx0[n.ci]) * 0.5f : (city.rz1[n.cj] - city.rz0[n.cj]) * 0.5f;
        }
        if (n.kind == RoadNode.Kind.END) return 2f;
        float w = 0f;
        for (int id : n.edges) if (id != e.id) w = Math.max(w, edges.get(id).cls.halfWidth());
        if (n.kind == RoadNode.Kind.CITY) w = Math.max(w, 6f);
        return Math.min(e.len * 0.45f, w + 2.5f);
    }

    private static float smooth01(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    /** Smoothed, grade-limited copy of the ground along the road. */
    private void profile(RoadEdge e) {
        int n = e.count();
        float[] h = new float[n];
        for (int i = 0; i < n; i++) h[i] = Math.max(2.5f, terrain.natural(e.x[i], e.z[i]));
        float step = Math.max(1f, e.len / Math.max(1, n - 1));
        int r = Math.max(1, (int) (e.cls.smoothWindow / step / 2));
        float[] tmp = new float[n];
        for (int pass = 0; pass < 3; pass++) {
            // running-sum box filter
            float sum = 0f;
            int lo = 0, hi = -1;
            for (int i = 0; i < n; i++) {
                int a = Math.max(0, i - r), b = Math.min(n - 1, i + r);
                while (hi < b) sum += h[++hi];
                while (lo < a) sum -= h[lo++];
                tmp[i] = sum / (hi - lo + 1);
            }
            System.arraycopy(tmp, 0, h, 0, n);
        }
        // grade limit both ways
        float g = e.cls.grade;
        for (int i = 1; i < n; i++) {
            float ds = e.s[i] - e.s[i - 1];
            h[i] = Math.max(h[i - 1] - g * ds, Math.min(h[i - 1] + g * ds, h[i]));
        }
        for (int i = n - 2; i >= 0; i--) {
            float ds = e.s[i + 1] - e.s[i];
            h[i] = Math.max(h[i + 1] - g * ds, Math.min(h[i + 1] + g * ds, h[i]));
        }
        for (int i = 0; i < n; i++) e.y[i] = Math.max(2.5f, h[i]);
    }

    /** Shifts the profile linearly so its ends meet the node heights. */
    private void pin(RoadEdge e) {
        int n = e.count();
        float da = nodes.get(e.a).y - e.y[0], db = nodes.get(e.b).y - e.y[n - 1];
        for (int i = 0; i < n; i++) {
            float t = e.len > 0f ? e.s[i] / e.len : 0f;
            // blend the correction in over the first/last stretch only, so long roads keep their shape
            float wa = 1f - smooth01(e.s[i] / Math.min(600f, e.len)), wb = 1f - smooth01((e.len - e.s[i]) / Math.min(600f, e.len));
            e.y[i] += da * wa + db * wb + 0f * t;
        }
    }

    // ------------------------------------------------------------------ spatial index

    private void buildIndex() {
        gx0 = terrain.islandX0 - 8000f;
        gz0 = terrain.islandZ0 - 8000f;
        gnx = (int) ((Terrain.ISLAND_SIZE + 16000f) / CELL) + 1;
        gnz = gnx;
        rnx = (int) ((Terrain.ISLAND_SIZE + 16000f) / RURAL_CELL) + 1;
        int[][] all = index(false, CELL, gnx), rural = index(true, RURAL_CELL, rnx);
        cellStart = all[0];
        cellItems = all[1];
        ruralStart = rural[0];
        ruralItems = rural[1];
    }

    // rural pieces only: terrain shaping runs per height sample and never looks at city streets,
    // and the 100x100 city grid would otherwise put hundreds of pieces in every downtown cell
    private int[] ruralStart, ruralItems;
    private int rnx;
    private static final float RURAL_CELL = 128f;

    private int ruralIndex(float x, float z) {
        int cx = (int) Math.floor((x - gx0) / RURAL_CELL), cz = (int) Math.floor((z - gz0) / RURAL_CELL);
        if (cx < 0 || cz < 0 || cx >= rnx || cz >= rnx) return -1;
        return cz * rnx + cx;
    }

    private int[][] index(boolean ruralOnly, float size, int n) {
        int[] counts = new int[n * n + 1];
        int[] items = null;
        for (int pass = 0; pass < 2; pass++) {
            int[] fill = pass == 1 ? new int[n * n] : null;
            for (RoadEdge e : edges) {
                if (ruralOnly && e.cls.city) continue;
                float m = e.cls.halfWidth() + MARGIN;
                for (int i = 0; i < e.count() - 1; i++) {
                    int cx0 = cell(Math.min(e.x[i], e.x[i + 1]) - m, gx0, size), cx1 = cell(Math.max(e.x[i], e.x[i + 1]) + m, gx0, size);
                    int cz0 = cell(Math.min(e.z[i], e.z[i + 1]) - m, gz0, size), cz1 = cell(Math.max(e.z[i], e.z[i + 1]) + m, gz0, size);
                    for (int cz = Math.max(0, cz0); cz <= Math.min(n - 1, cz1); cz++)
                        for (int cx = Math.max(0, cx0); cx <= Math.min(n - 1, cx1); cx++) {
                            int c = cz * n + cx;
                            if (pass == 0) counts[c + 1]++;
                            else items[counts[c] + fill[c]++] = (e.id << 16) | i;
                        }
                }
            }
            if (pass == 0) {
                for (int c = 0; c < n * n; c++) counts[c + 1] += counts[c];
                items = new int[counts[n * n]];
            }
        }
        return new int[][]{counts, items};
    }

    private static int cell(float v, float o) { return (int) Math.floor((v - o) / CELL); }
    private static int cell(float v, float o, float size) { return (int) Math.floor((v - o) / size); }

    /** Road pieces whose influence may reach cell containing (x,z): packed edge<<16|piece, or null. */
    private int cellIndex(float x, float z) {
        int cx = cell(x, gx0), cz = cell(z, gz0);
        if (cx < 0 || cz < 0 || cx >= gnx || cz >= gnz) return -1;
        return cz * gnx + cx;
    }

    /** Calls back every road piece (edge, index) registered in the cells touching the rectangle. */
    public void forEachPiece(float x0, float z0, float x1, float z1, PieceVisitor v) {
        int cx0 = Math.max(0, cell(x0, gx0)), cx1 = Math.min(gnx - 1, cell(x1, gx0));
        int cz0 = Math.max(0, cell(z0, gz0)), cz1 = Math.min(gnz - 1, cell(z1, gz0));
        for (int cz = cz0; cz <= cz1; cz++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cz * gnx + cx;
                for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                    int it = cellItems[k];
                    v.visit(edges.get(it >>> 16), it & 0xFFFF);
                }
            }
    }

    public interface PieceVisitor { void visit(RoadEdge e, int piece); }

    /** Visits each road piece whose midpoint lies inside the rectangle exactly once. */
    public void forEachPieceIn(float x0, float z0, float x1, float z1, PieceVisitor v) {
        int cx0 = Math.max(0, cell(x0, gx0)), cx1 = Math.min(gnx - 1, cell(x1, gx0));
        int cz0 = Math.max(0, cell(z0, gz0)), cz1 = Math.min(gnz - 1, cell(z1, gz0));
        for (int cz = cz0; cz <= cz1; cz++)
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cz * gnx + cx;
                for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                    int it = cellItems[k];
                    RoadEdge e = edges.get(it >>> 16);
                    int i = it & 0xFFFF;
                    float mx = (e.x[i] + e.x[i + 1]) * 0.5f, mz = (e.z[i] + e.z[i + 1]) * 0.5f;
                    if (mx < x0 || mx >= x1 || mz < z0 || mz >= z1) continue;
                    if (cell(mx, gx0) != cx || cell(mz, gz0) != cz) continue;
                    v.visit(e, i);
                }
            }
    }

    // ------------------------------------------------------------------ terrain shaping & queries

    /** Pulls natural ground toward nearby rural roads (embankments and cuttings). */
    public float shapeTerrain(float x, float z, float nat) {
        if (city.urban(x, z)) return nat;
        int c = ruralIndex(x, z);
        if (c < 0) return nat;
        // on a road's own surface and shoulder that road decides the height exactly; elsewhere the
        // banks of nearby roads blend (weighted toward the stronger), so two roads at different
        // heights side by side meet in one smooth slope rather than a step
        float onW = 0f, onY = nat, onD = Float.MAX_VALUE, sumW = 0f, sumH = 0f;
        for (int k = ruralStart[c]; k < ruralStart[c + 1]; k++) {
            int it = ruralItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (e.cls.city) continue;
            int i = it & 0xFFFF;
            if (e.bridge[i]) continue;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2 : 0f;
            // an embankment ends in a vertical abutment where its bridge begins: no fill under the deck
            // (the fill runs on 6 m under the deck end so the road meets the deck level)
            float segL = (float) Math.sqrt(l2);
            if (t > 1f && i + 1 < e.bridge.length && e.bridge[i + 1] && (t - 1f) * segL > 6f) continue;
            if (t < 0f && i > 0 && e.bridge[i - 1] && -t * segL > 6f) continue;
            t = Math.max(0f, Math.min(1f, t));
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            float d = (float) Math.sqrt(qx * qx + qz * qz);
            float hw = e.cls.halfWidth() + 0.6f;
            if (d > hw + BANK_REACH) continue;
            float yr = e.y[i] + (e.y[i + 1] - e.y[i]) * t;
            // cuttings and embankments are graded banks about 1 in 2 (grassed, like real road
            // earthworks), easing into the natural ground rather than meeting it at an edge
            float dh = Math.abs(nat - yr);
            // the verge is wide enough that the 8 m height map keeps the whole road surface flat
            // (the bank's eased profile is steepest mid-way, 1.5x its average: 3x the height keeps
            // that under 1 in 2)
            float shoulder = 6f, bank = Math.max(4f, Math.min(BANK_REACH - shoulder - 1f, dh * 3f));
            if (d > hw + shoulder + bank + 0.5f) continue;
            float u = (d - hw - shoulder) / bank;
            float w = u <= 0f ? 1f : 1f - smooth01(Math.min(1f, u));
            if (w >= 0.999f) {
                if (d < onD) { onD = d; onY = yr - 0.03f; onW = 1f; }
                continue;
            }
            if (w <= 0f) continue;
            float kw = w * w / (1.02f - w);
            sumW += kw;
            sumH += kw * (nat + (yr - 0.03f - nat) * w);
        }
        if (onW > 0f) return onY;
        return sumW > 0f ? sumH / sumW : nat;
    }

    /** How much a road shapes the ground here (0 = untouched, 1 = road surface). */
    public float influence(float x, float z) {
        int c = ruralIndex(x, z);
        if (c < 0) return 0f;
        float best = 0f;
        for (int k = ruralStart[c]; k < ruralStart[c + 1]; k++) {
            int it = ruralItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (e.cls.city) continue;
            int i = it & 0xFFFF;
            float d = pieceDist(e, i, x, z);
            float hw = e.cls.halfWidth() + 0.6f;
            float w = d <= hw ? 1f : 1f - smooth01((d - hw) / 12f);
            best = Math.max(best, w);
        }
        return best;
    }

    /** Distance from (x,z) to the nearest rural road edge (centreline minus half width), capped. */
    public float roadClearance(float x, float z, float cap) {
        float best = cap;
        int c = ruralIndex(x, z);
        if (c >= 0)
            for (int k = ruralStart[c]; k < ruralStart[c + 1]; k++) {
                int it = ruralItems[k];
                RoadEdge e = edges.get(it >>> 16);
                best = Math.min(best, pieceDist(e, it & 0xFFFF, x, z) - e.cls.halfWidth());
            }
        // city streets only matter on the urban fringe
        if (!city.nearUrban(x, z, MARGIN)) return best;
        c = cellIndex(x, z);
        if (c < 0) return best;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (!e.cls.city) continue;
            best = Math.min(best, pieceDist(e, it & 0xFFFF, x, z) - e.cls.halfWidth());
        }
        return best;
    }

    /**
     * True if (x,z) lies in the cut-and-fill corridor drawn along a rural road (within its
     * half width + 3 m), where the plain terrain grid is replaced by road-aligned verges.
     */
    public boolean corridorCovers(float x, float z) {
        int c = ruralIndex(x, z);
        if (c < 0) return false;
        for (int k = ruralStart[c]; k < ruralStart[c + 1]; k++) {
            int it = ruralItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (e.cls.city || e.cls == RoadClass.RAMP) continue;
            int i = it & 0xFFFF;
            if (e.bridge[i]) continue;
            if (pieceDist(e, i, x, z) < e.cls.halfWidth() + CORRIDOR_SKIP) {
                // keep the plain ground around junctions, where several roads' verges meet
                float sm = e.s[i];
                if (sm < e.clearA + 18f || sm > e.len - e.clearB - 18f) return false;
                return true;
            }
        }
        return false;
    }

    /** Terrain cells whose centre is this close to a road edge are replaced by the corridor verges. */
    public static final float CORRIDOR_SKIP = 3f, CORRIDOR_WIDTH = 12f;

    /** Distance from (x,z) to the nearest centreline of the given class (capped). */
    public float classDistance(float x, float z, RoadClass cls, float cap) {
        int c = cellIndex(x, z);
        if (c < 0) return cap;
        float best = cap;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (e.cls != cls) continue;
            best = Math.min(best, pieceDist(e, it & 0xFFFF, x, z));
        }
        return best;
    }

    /** Distance from (x,z) to the nearest freeway or slip road surface edge, capped. */
    public float motorwayClearance(float x, float z, float cap) {
        int c = cellIndex(x, z);
        if (c < 0) return cap;
        float best = cap;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            if (!e.cls.motorway()) continue;
            best = Math.min(best, pieceDist(e, it & 0xFFFF, x, z) - e.cls.halfWidth());
        }
        return best;
    }

    private static float pieceDist(RoadEdge e, int i, float x, float z) {
        float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
        float l2 = vx * vx + vz * vz;
        float t = l2 > 0f ? ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2 : 0f;
        t = Math.max(0f, Math.min(1f, t));
        float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
        return (float) Math.sqrt(qx * qx + qz * qz);
    }

    /** Deck height if (x,z) is on a bridge, else NaN. */
    public float bridgeHeight(float x, float z) {
        int c = cellIndex(x, z);
        if (c < 0) return Float.NaN;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            int i = it & 0xFFFF;
            if (!e.bridge[i]) continue;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            if (qx * qx + qz * qz < (e.cls.halfWidth() + 0.3f) * (e.cls.halfWidth() + 0.3f))
                return e.y[i] + (e.y[i + 1] - e.y[i]) * t + 0.03f;
        }
        return Float.NaN;
    }

    /** Nearest road (any class) within maxD: out = {edge id, s, distance}; false if none. */
    public boolean nearest(float x, float z, float maxD, float[] out) {
        int c = cellIndex(x, z);
        if (c < 0) return false;
        float best = maxD;
        boolean found = false;
        for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
            int it = cellItems[k];
            RoadEdge e = edges.get(it >>> 16);
            int i = it & 0xFFFF;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            float d = (float) Math.sqrt(qx * qx + qz * qz);
            if (d < best) {
                best = d;
                out[0] = e.id;
                out[1] = e.s[i] + t * (e.s[i + 1] - e.s[i]);
                out[2] = d;
                found = true;
            }
        }
        return found;
    }

    /**
     * Nearest ordinary road piece within maxD (no motorways, farm lanes or bridges), searching every
     * index cell the radius touches: out = {edge id, s, distance}; false if none.
     */
    public boolean nearestDrivable(float x, float z, float maxD, float[] out) {
        float[] best = {maxD};
        boolean[] found = {false};
        forEachPiece(x - maxD, z - maxD, x + maxD, z + maxD, (e, i) -> {
            if (e.cls.motorway() || e.cls == RoadClass.LANE || e.bridge[i] || e.len < 20f) return;
            float vx = e.x[i + 1] - e.x[i], vz = e.z[i + 1] - e.z[i];
            float l2 = vx * vx + vz * vz;
            float t = l2 > 0f ? Math.max(0f, Math.min(1f, ((x - e.x[i]) * vx + (z - e.z[i]) * vz) / l2)) : 0f;
            float qx = e.x[i] + vx * t - x, qz = e.z[i] + vz * t - z;
            float d = (float) Math.sqrt(qx * qx + qz * qz);
            if (d < best[0]) {
                best[0] = d;
                out[0] = e.id;
                out[1] = e.s[i] + t * (e.s[i + 1] - e.s[i]);
                out[2] = d;
                found[0] = true;
            }
        });
        return found[0];
    }

    /** Total length by class (km), for stats and tests. */
    public float lengthKm(RoadClass cls) {
        double t = 0;
        for (RoadEdge e : edges) if (e.cls == cls) t += e.len;
        return (float) (t / 1000.0);
    }

    public int pieceCount() {
        int n = 0;
        for (RoadEdge e : edges) n += e.count() - 1;
        return n;
    }
}
