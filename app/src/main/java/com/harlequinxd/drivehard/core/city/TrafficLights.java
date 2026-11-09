package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.tex.Noise;

/**
 * Signal controllers for every intersection. Each runs a fixed-time cycle:
 * N-S green, N-S yellow, all red, E-W green, E-W yellow, all red. Intersections are offset along
 * the avenues so traffic gets a loose "green wave". Cars and pedestrians query {@link #state}.
 */
public final class TrafficLights implements java.io.Serializable {
    public static final int GREEN = 0, YELLOW = 1, RED = 2;
    public static final float GREEN_T = 14f, YELLOW_T = 3.5f, ALL_RED_T = 1.5f;
    public static final float CYCLE = 2f * (GREEN_T + YELLOW_T + ALL_RED_T);

    private final int nx, seed;

    public TrafficLights(int intersectionsX, int seed) {
        this.nx = intersectionsX;
        this.seed = seed;
    }

    public int id(int i, int j) { return j * nx + i; }

    private float offset(int id) {
        int i = id & 1023, j = (id >> 10) & 1023;
        // green wave along x plus a little jitter
        return (i * 4.5f + j * 9.0f + Noise.rnd(i, j, seed) * 3f) % CYCLE;
    }

    /**
     * @param nsAxis true for traffic travelling north/south (along Z)
     * @return GREEN, YELLOW or RED
     */
    public int state(int id, boolean nsAxis, float time) {
        float t = (time + offset(id)) % CYCLE;
        if (!nsAxis) t = (t + CYCLE * 0.5f) % CYCLE;   // E-W runs half a cycle later
        if (t < GREEN_T) return GREEN;
        if (t < GREEN_T + YELLOW_T) return YELLOW;
        return RED;
    }

    /** Seconds until this approach next changes state (useful for AI). */
    public float timeToChange(int id, boolean nsAxis, float time) {
        float t = (time + offset(id)) % CYCLE;
        if (!nsAxis) t = (t + CYCLE * 0.5f) % CYCLE;
        if (t < GREEN_T) return GREEN_T - t;
        if (t < GREEN_T + YELLOW_T) return GREEN_T + YELLOW_T - t;
        return CYCLE - t;
    }
}
