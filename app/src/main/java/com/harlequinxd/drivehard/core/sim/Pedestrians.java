package com.harlequinxd.drivehard.core.sim;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.TrafficLights;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sidewalk pedestrians. Each walks a loop around its block about 2 m in from the curb; at a
 * corner it may cross to the next block on the crosswalk, waiting for the walk phase (green for
 * the parallel traffic) first. Spawned around the player out of view, removed when far away.
 */
public final class Pedestrians {
    public static final int WALK = 0, WAIT = 1, CROSS = 2;
    private static final float INSET = 2.2f;

    public static final class Ped {
        public int style;
        public float x, y, z, yaw;
        public int state;
        public float phase, idleTime;
        float speed;
        int bi, bj;          // block
        float p;             // distance along the block loop
        int dir;             // +1 clockwise (seen from above: E along north edge), -1 reverse
        // crossing
        float sx, sz, ex, ez, t;
        int tbi, tbj, cornerAfter;
        int sigId; boolean sigNs;
    }

    public interface StyleSource { int pickReadyStyle(Random r); }

    public final List<Ped> peds = new ArrayList<>();
    public int target = 40;
    public float spawnMin = 40f, spawnMax = 115f;
    private final City city;
    private final StyleSource styles;
    private final Random rnd = new Random(99);

    public Pedestrians(City city, StyleSource styles) {
        this.city = city;
        this.styles = styles;
    }

    // loop rectangle of block (bi,bj)
    private float lx0(int bi) { return city.rx1[bi] + INSET; }
    private float lx1(int bi) { return city.rx0[bi + 1] - INSET; }
    private float lz0(int bj) { return city.rz1[bj] + INSET; }
    private float lz1(int bj) { return city.rz0[bj + 1] - INSET; }

    private float perimeter(int bi, int bj) {
        return 2f * ((lx1(bi) - lx0(bi)) + (lz1(bj) - lz0(bj)));
    }

    /** Corner index c (0=NW,1=NE,2=SE,3=SW) distance along the loop (clockwise from NW). */
    private float cornerP(int bi, int bj, int c) {
        float w = lx1(bi) - lx0(bi), h = lz1(bj) - lz0(bj);
        switch (c) { case 0: return 0; case 1: return w; case 2: return w + h; default: return 2 * w + h; }
    }

    private void loopPoint(int bi, int bj, float p, float[] out) {
        float x0 = lx0(bi), x1 = lx1(bi), z0 = lz0(bj), z1 = lz1(bj);
        float w = x1 - x0, h = z1 - z0, per = 2 * (w + h);
        p = ((p % per) + per) % per;
        if (p < w) { out[0] = x0 + p; out[1] = z0; out[2] = 1; out[3] = 0; }
        else if (p < w + h) { out[0] = x1; out[1] = z0 + (p - w); out[2] = 0; out[3] = 1; }
        else if (p < 2 * w + h) { out[0] = x1 - (p - w - h); out[1] = z1; out[2] = -1; out[3] = 0; }
        else { out[0] = x0; out[1] = z1 - (p - 2 * w - h); out[2] = 0; out[3] = -1; }
    }

    private final float[] lp = new float[4];

    public void update(float dt, float time, float px, float pz) {
        for (int i = peds.size() - 1; i >= 0; i--) {
            Ped p = peds.get(i);
            float dx = p.x - px, dz = p.z - pz;
            if (dx * dx + dz * dz > 135f * 135f) peds.remove(i);
        }
        for (int tries = 0; tries < 6 && peds.size() < target; tries++) spawn(px, pz);
        for (Ped p : peds) step(p, dt, time, px, pz);
    }

    private void spawn(float px, float pz) {
        int style = styles.pickReadyStyle(rnd);
        if (style < 0) return;
        int bi = rnd.nextInt(city.nbx), bj = rnd.nextInt(city.nbz);
        float per = perimeter(bi, bj);
        float p = rnd.nextFloat() * per;
        loopPoint(bi, bj, p, lp);
        float dx = lp[0] - px, dz = lp[1] - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < spawnMin || d > spawnMax) return;
        Ped q = new Ped();
        q.style = style;
        q.bi = bi; q.bj = bj; q.p = p;
        q.dir = rnd.nextBoolean() ? 1 : -1;
        q.speed = 1.15f + rnd.nextFloat() * 0.45f;
        q.x = lp[0]; q.z = lp[1];
        peds.add(q);
    }

    private void step(Ped q, float dt, float time, float px, float pz) {
        // stop for the player standing right in front
        float fx = (float) Math.sin(q.yaw), fz = (float) Math.cos(q.yaw);
        float dpx = px - q.x, dpz = pz - q.z;
        boolean blocked = dpx * fx + dpz * fz > 0 && dpx * dpx + dpz * dpz < 1.2f;

        if (q.state == WAIT) {
            q.idleTime += dt;
            int st = city.traffic.state(q.sigId, q.sigNs, time);
            if (st == TrafficLights.GREEN && city.traffic.timeToChange(q.sigId, q.sigNs, time) > 5f) q.state = CROSS;
        } else if (q.state == CROSS) {
            if (!blocked) {
                float len = (float) Math.hypot(q.ex - q.sx, q.ez - q.sz);
                q.t += q.speed * 1.15f * dt / Math.max(0.5f, len);
                q.phase += q.speed * 1.15f * dt / 1.4f;
            }
            if (q.t >= 1f) {
                q.bi = q.tbi; q.bj = q.tbj;
                q.p = cornerP(q.bi, q.bj, q.cornerAfter);
                q.state = WALK;
                loopPoint(q.bi, q.bj, q.p, lp);
                q.x = lp[0]; q.z = lp[1];
            } else {
                q.x = q.sx + (q.ex - q.sx) * q.t;
                q.z = q.sz + (q.ez - q.sz) * q.t;
                q.yaw = (float) Math.atan2(q.ex - q.sx, q.ez - q.sz);
            }
        } else {
            if (!blocked) {
                float before = q.p;
                q.p += q.dir * q.speed * dt;
                q.phase += q.speed * dt / 1.4f;
                // reached a corner?
                for (int c = 0; c < 4; c++) {
                    float cp = cornerP(q.bi, q.bj, c), per = perimeter(q.bi, q.bj);
                    float a = ((before % per) + per) % per, b = ((q.p % per) + per) % per;
                    boolean passed = q.dir > 0 ? (a < cp && b >= cp) || (c == 0 && b < a)
                            : (a > cp && b <= cp) || (c == 0 && b > a);
                    if (passed && rnd.nextInt(10) < 4) {
                        tryCross(q, c);
                        break;
                    }
                }
            }
            loopPoint(q.bi, q.bj, q.p, lp);
            q.x = lp[0]; q.z = lp[1];
            q.yaw = (float) Math.atan2(lp[2] * q.dir, lp[3] * q.dir);
        }
        q.y = city.surface(q.x, q.z);
    }

    /** At corner c, cross straight ahead (in the walking direction) to the neighbouring block. */
    private void tryCross(Ped q, int c) {
        // walking direction at this corner (the edge we were on)
        int edgeBefore = q.dir > 0 ? (c + 3) % 4 : c;      // edge index: 0=N(+x) 1=E(+z) 2=S(-x) 3=W(-z) clockwise
        float dx = 0, dz = 0;
        switch (edgeBefore) { case 0: dx = 1; break; case 1: dz = 1; break; case 2: dx = -1; break; default: dz = -1; }
        dx *= q.dir; dz *= q.dir;
        int tbi = q.bi + (int) dx, tbj = q.bj + (int) dz;
        if (tbi < 0 || tbj < 0 || tbi >= city.nbx || tbj >= city.nbz) return;
        loopPoint(q.bi, q.bj, cornerP(q.bi, q.bj, c), lp);
        q.sx = lp[0]; q.sz = lp[1];
        // mirrored corner on the target block
        int tc;
        if (dx != 0) tc = dx > 0 ? (c == 1 ? 0 : 3) : (c == 0 ? 1 : 2);
        else tc = dz > 0 ? (c == 3 ? 0 : 1) : (c == 0 ? 3 : 2);
        loopPoint(tbi, tbj, cornerP(tbi, tbj, tc), lp);
        q.ex = lp[0]; q.ez = lp[1];
        // the intersection at this corner
        int ii = (c == 1 || c == 2) ? q.bi + 1 : q.bi;
        int jj = (c == 2 || c == 3) ? q.bj + 1 : q.bj;
        q.sigId = city.traffic.id(ii, jj);
        q.sigNs = dz != 0;      // walking along Z: walk with the N-S traffic phase
        q.tbi = tbi; q.tbj = tbj; q.cornerAfter = tc;
        q.t = 0;
        q.idleTime = 0;
        q.state = WAIT;
        q.x = q.sx; q.z = q.sz;
        q.yaw = (float) Math.atan2(dx, dz);
    }
}
