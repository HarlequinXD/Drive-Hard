package com.harlequinxd.drivehard.core.road;

import java.util.Arrays;

/** Where roads meet: a city intersection, a rural junction, a freeway split/merge, or a dead end. */
public final class RoadNode implements java.io.Serializable {
    public enum Kind {
        /** Signalised city intersection. */
        CITY,
        /** Unsignalised junction: lower-priority roads give way; equal roads give way to the left. */
        JUNCTION,
        /** Freeway diverge/merge: only smooth movements (no turning across the median). */
        SPLIT,
        /** Road ends: traffic turns around. */
        END
    }

    public final int id;
    public float x, z, y;
    public Kind kind;
    /** City grid indices and signal controller id (CITY only). */
    public int ci = -1, cj = -1, signal = -1;
    /** Which city's grid a CITY node belongs to. */
    public int metro;
    public int[] edges = new int[0];
    /** Merged away during construction; ignore. */
    public boolean dead;
    /** Junction surface outline (x, y, z triples, in order), or null where there is none. */
    public float[] ring;
    /** Radius of the ring around the node centre. */
    public float ringRadius;
    /** Per ring vertex: true if the segment from this vertex to the next is a kerb return (not a road mouth). */
    public boolean[] kerb;

    RoadNode(int id, float x, float z, float y, Kind kind) {
        this.id = id; this.x = x; this.z = z; this.y = y; this.kind = kind;
    }

    void addEdge(int e) {
        edges = Arrays.copyOf(edges, edges.length + 1);
        edges[edges.length - 1] = e;
    }

    void removeEdge(int e) {
        int n = 0;
        int[] out = new int[edges.length];
        for (int v : edges) if (v != e) out[n++] = v;
        edges = Arrays.copyOf(out, n);
    }
}
