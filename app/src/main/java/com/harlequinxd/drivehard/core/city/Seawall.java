package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.road.Town;
import com.harlequinxd.drivehard.core.tex.Noise;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * The armoured shore of a Japanese coastal town: a concrete sea wall along the water's edge in
 * front of the town (not on the sand beaches or under the cliffs), with banks of tetrapods
 * piled in the sea in front of it. Traced along the coastline with marching squares on a 4 m
 * grid, chunk by chunk.
 */
final class Seawall {
    private static final float STEP = 4f;
    private final City city;
    private final Terrain terrain;
    private final Chunk ch;
    private final GeoBuilder gb;
    private final float[] p = new float[12], uv = new float[8], l = {0.8f, 0.8f, 0.95f, 0.95f};

    Seawall(City city, Chunk ch, GeoBuilder gb) {
        this.city = city;
        this.terrain = city.terrain;
        this.ch = ch;
        this.gb = gb;
    }

    void build(float x0, float z0, float x1, float z1) {
        float cx = (x0 + x1) * 0.5f, cz = (z0 + z1) * 0.5f;
        // only near the coast, in front of a coastal settlement
        if (Math.abs(terrain.coastOver(cx, cz)) > 90f) return;
        if (!nearCoastalTown(cx, cz)) return;
        gb.begin(false);
        int n = (int) ((x1 - x0) / STEP);
        float[] v = new float[(n + 1) * (n + 1)];
        // the visible waterline: where the ground goes under the sea
        for (int j = 0; j <= n; j++)
            for (int i = 0; i <= n; i++) v[j * (n + 1) + i] = 0.25f - terrain.height(x0 + i * STEP, z0 + j * STEP);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                float a = v[j * (n + 1) + i], b = v[j * (n + 1) + i + 1], c = v[(j + 1) * (n + 1) + i + 1], d = v[(j + 1) * (n + 1) + i];
                int mask = (a > 0 ? 1 : 0) | (b > 0 ? 2 : 0) | (c > 0 ? 4 : 0) | (d > 0 ? 8 : 0);
                if (mask == 0 || mask == 15) continue;
                float bx = x0 + i * STEP, bz = z0 + j * STEP;
                // the crossing points on the cell's four sides
                float[] e = new float[8];
                int k = 0;
                if ((a > 0) != (b > 0)) { float t = a / (a - b); e[k++] = bx + t * STEP; e[k++] = bz; }
                if ((b > 0) != (c > 0)) { float t = b / (b - c); e[k++] = bx + STEP; e[k++] = bz + t * STEP; }
                if ((c > 0) != (d > 0)) { float t = c / (c - d); e[k++] = bx + STEP - t * STEP; e[k++] = bz + STEP; }
                if ((d > 0) != (a > 0)) { float t = d / (d - a); e[k++] = bx; e[k++] = bz + STEP - t * STEP; }
                if (k < 4) continue;
                segment(e[0], e[1], e[2], e[3]);
            }
        gb.end();
    }

    private boolean nearCoastalTown(float x, float z) {
        for (Town t : city.roads.towns) {
            if (!t.coastal && !t.spur) continue;
            if (t.contains(x, z, t.grid != null ? 700f : 350f)) return true;
        }
        return false;
    }

    /** One piece of wall along the waterline from (ax, az) to (bx, bz), tetrapods in front. */
    private void segment(float ax, float az, float bx, float bz) {
        float mx = (ax + bx) * 0.5f, mz = (az + bz) * 0.5f;
        float len = (float) Math.hypot(bx - ax, bz - az);
        if (len < 0.3f) return;
        // not on the sand, not under cliffs, not across a harbour quay or a road
        if (terrain.cliff(mx, mz) > 0.4f || city.countryside.inHarbour(mx, mz)) return;
        float land = 0.5f;
        if (terrain.isSand(mx, mz, land) && terrain.eastness(mx, mz) > 0.5f) return;
        if (city.roads.roadClearance(mx, mz, 8f) < 3f) return;
        // seaward: the side where the coast measure grows
        float tx = (bx - ax) / len, tz = (bz - az) / len, nx = tz, nz = -tx;
        if (terrain.height(mx + nx * 3f, mz + nz * 3f) > terrain.height(mx - nx * 3f, mz - nz * 3f)) { nx = -nx; nz = -nz; }
        float top = Math.max(2.6f, land + 1.3f);
        int conc = city.lib.concrete;
        // the sloping sea face and the vertical land face, with a cap along the top
        quad(ax + nx * 1.2f, az + nz * 1.2f, bx + nx * 1.2f, bz + nz * 1.2f, -2.5f, ax, az, bx, bz, top, conc, len);
        quad(ax - nx * 0.6f, az - nz * 0.6f, bx - nx * 0.6f, bz - nz * 0.6f, land - 0.4f, ax - nx * 0.6f, az - nz * 0.6f, bx - nx * 0.6f, bz - nz * 0.6f, top, conc, len);
        quad(ax, az, bx, bz, top, ax - nx * 0.6f, az - nz * 0.6f, bx - nx * 0.6f, bz - nz * 0.6f, top, conc, len);
        ch.boxes.add(new Box(Math.min(ax, bx) - 0.7f, -3f, Math.min(az, bz) - 0.7f, Math.max(ax, bx) + 0.7f, top, Math.max(az, bz) + 0.7f));
        // tetrapods heaped in the water in front
        int ix = (int) Math.floor(mx / 3f), iz = (int) Math.floor(mz / 3f);
        if (Noise.rnd(ix, iz, 4417) < 0.55f) {
            float off = 3f + Noise.rnd(ix, iz, 4418) * 3f;
            tetrapod(mx + nx * off, mz + nz * off, Noise.rnd(ix, iz, 4419) * 6.2832f);
        }
    }

    /** A tetrapod: four concrete legs from a centre, one up, three splayed down. */
    private void tetrapod(float x, float z, float yaw) {
        float y = Math.max(-0.6f, terrain.height(x, z) + 0.9f);
        int tex = city.lib.concrete;
        // the up leg
        gb.box(x - 0.3f, y, z - 0.3f, x + 0.3f, y + 1.3f, z + 0.3f, tex, tex, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        // three legs splayed out and down, as low wide boxes
        for (int k = 0; k < 3; k++) {
            double a = yaw + k * Math.PI * 2 / 3;
            float lx = x + (float) Math.cos(a) * 0.8f, lz = z + (float) Math.sin(a) * 0.8f;
            gb.box(lx - 0.35f, y - 0.7f, lz - 0.35f, lx + 0.35f, y + 0.1f, lz + 0.35f, tex, tex, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        }
    }

    /** A quad from the edge (ax,az)-(bx,bz) at height ya to the edge (cx,cz)-(dx,dz) at height yb. */
    private void quad(float ax, float az, float bx, float bz, float ya, float cx, float cz, float dx, float dz, float yb, int tex, float len) {
        p[0] = ax; p[1] = ya; p[2] = az;
        p[3] = bx; p[4] = ya; p[5] = bz;
        p[6] = dx; p[7] = yb; p[8] = dz;
        p[9] = cx; p[10] = yb; p[11] = cz;
        uv[0] = 0f; uv[1] = 1f; uv[2] = len / 4f; uv[3] = 1f; uv[4] = len / 4f; uv[5] = 0f; uv[6] = 0f; uv[7] = 0f;
        gb.quad(p, uv, l, tex, L_OBJECT, 0, -0.1f, true);
    }
}
