package com.harlequinxd.drivehard.core.road;

/**
 * A road between two nodes: a spline sampled into a polyline with a height profile. Distances
 * {@code s} run from node {@code a} (0) to node {@code b} ({@link #len}). Travel direction +1 goes
 * a→b, -1 goes b→a; lanes sit to the left of travel (traffic keeps left). Lane 0 is the kerb lane.
 */
public final class RoadEdge {
    public final int id;
    public final RoadClass cls;
    public int a, b;
    /** True for one-way roads (a→b only). */
    public final boolean oneWay;
    public float[] x, z, y, s;
    /** bridge[i]: the piece from sample i to i+1 is a bridge deck (terrain is not shaped under it). */
    public boolean[] bridge;
    public float len;
    /** Distance from each end node to where the junction area ends (stop line / turn start). */
    public float clearA, clearB;
    /** City streets: runs north-south (signal phase). */
    public boolean ns;
    /** Slip roads: lateral offset of the freeway lane they join. */
    public float rampSide;
    /** The edge this piece was split from (itself if never split). */
    public int origin;

    RoadEdge(int id, RoadClass cls, int a, int b, float[] x, float[] z, boolean oneWay) {
        this.id = id;
        this.cls = cls;
        this.a = a;
        this.b = b;
        this.oneWay = oneWay;
        this.origin = id;
        setPoints(x, z);
    }

    void setPoints(float[] px, float[] pz) {
        x = px;
        z = pz;
        int n = px.length;
        s = new float[n];
        for (int i = 1; i < n; i++) {
            float dx = px[i] - px[i - 1], dz = pz[i] - pz[i - 1];
            s[i] = s[i - 1] + (float) Math.sqrt(dx * dx + dz * dz);
        }
        len = s[n - 1];
        y = new float[n];
        bridge = new boolean[Math.max(1, n - 1)];
    }

    public int count() { return x.length; }

    /** Index i of the piece [i, i+1] containing distance s. */
    public int seg(float d) {
        int lo = 0, hi = s.length - 2;
        if (d <= 0f) return 0;
        if (d >= len) return hi;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (s[mid] <= d) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** Point at distance d: out = {x, z, y, tx, tz} (unit tangent toward b). */
    public void point(float d, float[] out) {
        int i = seg(d);
        float l = s[i + 1] - s[i];
        float t = l > 1e-5f ? (d - s[i]) / l : 0f;
        t = Math.max(0f, Math.min(1f, t));
        float dx = x[i + 1] - x[i], dz = z[i + 1] - z[i];
        out[0] = x[i] + dx * t;
        out[1] = z[i] + dz * t;
        out[2] = y[i] + (y[i + 1] - y[i]) * t;
        float il = l > 1e-5f ? 1f / l : 0f;
        out[3] = dx * il;
        out[4] = dz * il;
    }

    /** Lateral offset of lane {@code lane} from the centreline, to the left of travel. */
    public float laneOffset(int lane) {
        if (oneWay) return (lane - (cls.lanes - 1) * 0.5f) * -cls.laneW;
        return cls.median + (cls.lanes - lane - 0.5f) * cls.laneW;
    }

    /** Lane position: out = {x, z, y, hx, hz} with (hx,hz) the heading of travel. */
    public void lanePoint(float d, int dir, float offset, float[] out) {
        point(d, out);
        float tx = out[3] * dir, tz = out[4] * dir;
        // left of travel = (tz, -tx)
        out[0] += tz * offset;
        out[1] += -tx * offset;
        out[3] = tx;
        out[4] = tz;
    }

    public float clear(int node) { return node == a ? clearA : clearB; }

    public int other(int node) { return node == a ? b : a; }

    /** Travel direction that leaves {@code node} along this edge. */
    public int dirFrom(int node) { return node == a ? 1 : -1; }

    /** Whether traffic may leave {@code node} along this edge. */
    public boolean canLeave(int node) { return !oneWay || node == a; }

    /** Whether traffic may arrive at {@code node} along this edge. */
    public boolean canArrive(int node) { return !oneWay || node == b; }

    public float sAt(int node) { return node == a ? 0f : len; }
}
