package com.harlequinxd.drivehard.core.sim;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.Town;

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
        int area;
        int bi, bj;          // block
        float p;             // distance along the block loop
        int dir;             // +1 clockwise (seen from above: E along north edge), -1 reverse
        // crossing
        float sx, sz, ex, ez, t;
        int tbi, tbj, cornerAfter;
        int sigId; boolean sigNs;
        float waitDuration;
    }

    public interface StyleSource { int pickReadyStyle(Random r); }

    private static final class WalkArea {
        final float[] x0, x1, z0, z1;
        final int nx, nz;
        final boolean signalized;
        final float minX, maxX, minZ, maxZ;

        WalkArea(float[] x0, float[] x1, float[] z0, float[] z1, boolean signalized) {
            this.x0=x0; this.x1=x1; this.z0=z0; this.z1=z1;
            this.nx=x0.length; this.nz=z0.length; this.signalized=signalized;
            minX=x0[0]; maxX=x1[nx-1]; minZ=z0[0]; maxZ=z1[nz-1];
        }
    }

    public final List<Ped> peds = new ArrayList<>();
    public int target = 40;
    public float spawnMin = 40f, spawnMax = 115f;
    private final City city;
    private final StyleSource styles;
    private final List<WalkArea> areas = new ArrayList<>();
    private final Random rnd = new Random(99);

    public Pedestrians(City city, StyleSource styles) {
        this.city = city;
        this.styles = styles;
        addCityArea();
        for (Town town : city.roads.towns) if (town.grid != null) addTownArea(town);
    }

    private void addCityArea() {
        float[] x0=new float[city.nbx], x1=new float[city.nbx];
        float[] z0=new float[city.nbz], z1=new float[city.nbz];
        for(int i=0;i<city.nbx;i++) { x0[i]=city.rx1[i]+INSET; x1[i]=city.rx0[i+1]-INSET; }
        for(int j=0;j<city.nbz;j++) { z0[j]=city.rz1[j]+INSET; z1[j]=city.rz0[j+1]-INSET; }
        areas.add(new WalkArea(x0,x1,z0,z1,true));
    }

    private void addTownArea(Town town) {
        int nx=town.grid.length-1, nz=town.grid[0].length-1;
        float[] x0=new float[nx], x1=new float[nx], z0=new float[nz], z1=new float[nz];
        float inset=RoadClass.TOWN.laneW+(RoadClass.TOWN.halfWidth()-RoadClass.TOWN.laneW)*0.5f;
        for(int i=0;i<nx;i++) { x0[i]=town.gx[i]+inset; x1[i]=town.gx[i+1]-inset; }
        for(int j=0;j<nz;j++) { z0[j]=town.gz[j]+inset; z1[j]=town.gz[j+1]-inset; }
        areas.add(new WalkArea(x0,x1,z0,z1,false));
    }

    // loop rectangle of block (bi,bj)
    private float lx0(int area, int bi) { return areas.get(area).x0[bi]; }
    private float lx1(int area, int bi) { return areas.get(area).x1[bi]; }
    private float lz0(int area, int bj) { return areas.get(area).z0[bj]; }
    private float lz1(int area, int bj) { return areas.get(area).z1[bj]; }

    private float perimeter(int area, int bi, int bj) {
        return 2f * ((lx1(area,bi) - lx0(area,bi)) + (lz1(area,bj) - lz0(area,bj)));
    }

    /** Corner index c (0=NW,1=NE,2=SE,3=SW) distance along the loop (clockwise from NW). */
    private float cornerP(int area, int bi, int bj, int c) {
        float w = lx1(area,bi) - lx0(area,bi), h = lz1(area,bj) - lz0(area,bj);
        switch (c) { case 0: return 0; case 1: return w; case 2: return w + h; default: return 2 * w + h; }
    }

    private void loopPoint(int area, int bi, int bj, float p, float[] out) {
        float x0 = lx0(area,bi), x1 = lx1(area,bi), z0 = lz0(area,bj), z1 = lz1(area,bj);
        float w = x1 - x0, h = z1 - z0, per = 2 * (w + h);
        p = ((p % per) + per) % per;
        if (p < w) { out[0] = x0 + p; out[1] = z0; out[2] = 1; out[3] = 0; }
        else if (p < w + h) { out[0] = x1; out[1] = z0 + (p - w); out[2] = 0; out[3] = 1; }
        else if (p < 2 * w + h) { out[0] = x1 - (p - w - h); out[1] = z1; out[2] = -1; out[3] = 0; }
        else { out[0] = x0; out[1] = z1 - (p - 2 * w - h); out[2] = 0; out[3] = -1; }
    }

    private final float[] lp = new float[4];
    private final List<Integer> nearbyAreas = new ArrayList<>();

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
        nearbyAreas.clear();
        for(int i=0;i<areas.size();i++) {
            WalkArea area=areas.get(i);
            float dx=Math.max(0f,Math.max(area.minX-px,px-area.maxX));
            float dz=Math.max(0f,Math.max(area.minZ-pz,pz-area.maxZ));
            if(dx*dx+dz*dz<=spawnMax*spawnMax) nearbyAreas.add(i);
        }
        if(nearbyAreas.isEmpty()) return;
        int ai=nearbyAreas.get(0);
        float nearest=Float.MAX_VALUE;
        for(int candidate:nearbyAreas) {
            WalkArea area=areas.get(candidate);
            float dx=Math.max(0f,Math.max(area.minX-px,px-area.maxX));
            float dz=Math.max(0f,Math.max(area.minZ-pz,pz-area.maxZ));
            float d2=dx*dx+dz*dz;
            if(d2<nearest) { nearest=d2; ai=candidate; }
        }
        WalkArea area=areas.get(ai);
        int bi=0,bj=0;
        float nearestBlock=Float.MAX_VALUE;
        for(int j=0;j<area.nz;j++) for(int i=0;i<area.nx;i++) {
            float dx=Math.max(0f,Math.max(lx0(ai,i)-px,px-lx1(ai,i)));
            float dz=Math.max(0f,Math.max(lz0(ai,j)-pz,pz-lz1(ai,j)));
            float d2=dx*dx+dz*dz;
            if(d2<nearestBlock) { nearestBlock=d2; bi=i; bj=j; }
        }
        int firstI=Math.max(0,bi-1), lastI=Math.min(area.nx-1,bi+1);
        int firstJ=Math.max(0,bj-1), lastJ=Math.min(area.nz-1,bj+1);
        bi=firstI+rnd.nextInt(lastI-firstI+1);
        bj=firstJ+rnd.nextInt(lastJ-firstJ+1);
        float per = perimeter(ai,bi,bj);
        float p = rnd.nextFloat() * per;
        loopPoint(ai,bi,bj,p,lp);
        float dx = lp[0] - px, dz = lp[1] - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < spawnMin || d > spawnMax) return;
        Ped q = new Ped();
        q.style = style;
        q.area=ai; q.bi = bi; q.bj = bj; q.p = p;
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
            if(q.sigId<0) {
                if(q.idleTime>=q.waitDuration) q.state=CROSS;
            } else {
                int st = city.traffic.state(q.sigId, q.sigNs, time);
                if (st == TrafficLights.GREEN && city.traffic.timeToChange(q.sigId, q.sigNs, time) > 5f) q.state = CROSS;
            }
        } else if (q.state == CROSS) {
            if (!blocked) {
                float len = (float) Math.hypot(q.ex - q.sx, q.ez - q.sz);
                q.t += q.speed * 1.15f * dt / Math.max(0.5f, len);
                q.phase += q.speed * 1.15f * dt / 1.4f;
            }
            if (q.t >= 1f) {
                q.bi = q.tbi; q.bj = q.tbj;
                q.p = cornerP(q.area,q.bi,q.bj,q.cornerAfter);
                q.state = WALK;
                loopPoint(q.area,q.bi,q.bj,q.p,lp);
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
                    float cp = cornerP(q.area,q.bi,q.bj,c), per = perimeter(q.area,q.bi,q.bj);
                    float a = ((before % per) + per) % per, b = ((q.p % per) + per) % per;
                    boolean passed = q.dir > 0 ? (a < cp && b >= cp) || (c == 0 && b < a)
                            : (a > cp && b <= cp) || (c == 0 && b > a);
                    if (passed && rnd.nextInt(10) < 4) {
                        tryCross(q, c);
                        break;
                    }
                }
            }
            loopPoint(q.area,q.bi,q.bj,q.p,lp);
            q.x = lp[0]; q.z = lp[1];
            q.yaw = (float) Math.atan2(lp[2] * q.dir, lp[3] * q.dir);
        }
        q.y = city.surface(q.x, q.z);
    }

    /** At corner c, cross straight ahead (in the walking direction) to the neighbouring block. */
    private void tryCross(Ped q, int c) {
        WalkArea area=areas.get(q.area);
        // walking direction at this corner (the edge we were on)
        int edgeBefore = q.dir > 0 ? (c + 3) % 4 : c;      // edge index: 0=N(+x) 1=E(+z) 2=S(-x) 3=W(-z) clockwise
        float dx = 0, dz = 0;
        switch (edgeBefore) { case 0: dx = 1; break; case 1: dz = 1; break; case 2: dx = -1; break; default: dz = -1; }
        dx *= q.dir; dz *= q.dir;
        int tbi = q.bi + (int) dx, tbj = q.bj + (int) dz;
        if (tbi < 0 || tbj < 0 || tbi >= area.nx || tbj >= area.nz) return;
        loopPoint(q.area,q.bi,q.bj,cornerP(q.area,q.bi,q.bj,c),lp);
        q.sx = lp[0]; q.sz = lp[1];
        // mirrored corner on the target block
        int tc;
        if (dx != 0) tc = dx > 0 ? (c == 1 ? 0 : 3) : (c == 0 ? 1 : 2);
        else tc = dz > 0 ? (c == 3 ? 0 : 1) : (c == 0 ? 3 : 2);
        loopPoint(q.area,tbi,tbj,cornerP(q.area,tbi,tbj,tc),lp);
        q.ex = lp[0]; q.ez = lp[1];
        // the intersection at this corner
        int ii = (c == 1 || c == 2) ? q.bi + 1 : q.bi;
        int jj = (c == 2 || c == 3) ? q.bj + 1 : q.bj;
        q.sigId = area.signalized ? city.traffic.id(ii, jj) : -1;
        q.sigNs = dz != 0;      // walking along Z: walk with the N-S traffic phase
        q.tbi = tbi; q.tbj = tbj; q.cornerAfter = tc;
        q.t = 0;
        q.idleTime = 0;
        q.waitDuration=0.5f+rnd.nextFloat()*0.8f;
        q.state = WAIT;
        q.x = q.sx; q.z = q.sz;
        q.yaw = (float) Math.atan2(dx, dz);
    }
}
