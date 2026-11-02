package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.World;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.tex.Facades.Kind;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_DECAL;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_GROUND;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;
import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_RAISED;

/**
 * Procedural Miami-style city: a grid of streets and palm-lined avenues, raised sidewalks with
 * curbs, lots with art-deco low-rises, apartments, hotels, office towers, garages and
 * warehouses, parks and parking lots, and a beachfront promenade, sand and ocean on the east.
 *
 * +X is east (toward the ocean), +Z is south.
 */
public final class City extends World {
    public static final float CURB = 0.15f, SIDEWALK = 4f;
    public static final float FLOOR_H = 3.4f, SHOP_H = 4.6f;

    private final TextureLibrary lib;
    private final Random rnd;
    public final GeoBuilder gb;

    public final int nbx = 9, nbz = 10;
    public final float[] rx0 = new float[nbx + 1], rx1 = new float[nbx + 1];   // N-S roads (x ranges)
    public final float[] rz0 = new float[nbz + 1], rz1 = new float[nbz + 1];   // E-W roads (z ranges)
    public final boolean[] avenueX = new boolean[nbx + 1], avenueZ = new boolean[nbz + 1];
    public float cityX0, cityX1, cityZ0, cityZ1;
    public float parkX0, sandX0, waterX0;
    public float spawnX, spawnZ;
    public int buildingCount;

    public City(TextureLibrary lib, long seed) {
        this.lib = lib;
        this.rnd = new Random(seed);
        layout();
        float outer = 140f;
        gb = new GeoBuilder(cityX0 - outer, cityZ0 - outer, waterX0 + 420f, cityZ1 + outer);
        minX = cityX0 + 1f; maxX = waterX0 + 6f; minZ = cityZ0 + 1f; maxZ = cityZ1 - 1f;

        roads();
        for (int j = 0; j < nbz; j++)
            for (int i = 0; i < nbx; i++) block(i, j);
        beach(outer);
        outskirts(outer);
        buildIndex();

        // spawn on the Ocean Drive sidewalk, mid-city
        int mj = nbz / 2;
        spawnX = rx1[nbx] + 2f;
        spawnZ = (rz1[mj] + rz0[mj + 1]) * 0.5f;
    }

    // ------------------------------------------------------------------ layout

    private void layout() {
        float x = 0;
        for (int i = 0; i <= nbx; i++) {
            avenueX[i] = i % 3 == 0 && i != nbx;
            rx0[i] = x;
            x += avenueX[i] ? 22f : 13f;
            rx1[i] = x;
            if (i < nbx) x += 52 + 4 * rnd.nextInt(8);
        }
        float z = 0;
        for (int j = 0; j <= nbz; j++) {
            avenueZ[j] = j % 4 == 0;
            rz0[j] = z;
            z += avenueZ[j] ? 22f : 13f;
            rz1[j] = z;
            if (j < nbz) z += 56 + 4 * rnd.nextInt(7);
        }
        cityX0 = rx0[0]; cityX1 = rx1[nbx];
        cityZ0 = rz0[0]; cityZ1 = rz1[nbz];
        parkX0 = cityX1;
        sandX0 = parkX0 + 26f;
        waterX0 = sandX0 + 64f;
    }

    // ------------------------------------------------------------------ roads

    private void roads() {
        float zA = cityZ0, zB = cityZ1;
        // N-S roads (full length, they own the intersections)
        for (int i = 0; i <= nbx; i++) {
            roadSurface(rx0[i], zA, rx1[i], zB);
        }
        // E-W roads between N-S roads
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i < nbx; i++) roadSurface(rx1[i], rz0[j], rx0[i + 1], rz1[j]);

        // markings + medians on N-S roads, per block segment
        for (int i = 0; i <= nbx; i++)
            for (int j = 0; j < nbz; j++) roadSegment(true, rx0[i], rx1[i], rz1[j], rz0[j + 1], avenueX[i]);
        for (int j = 0; j <= nbz; j++)
            for (int i = 0; i < nbx; i++) roadSegment(false, rz0[j], rz1[j], rx1[i], rx0[i + 1], avenueZ[j]);
    }

    private void roadSurface(float ax, float az, float bx, float bz) {
        float cell = 8f;
        for (float z = az; z < bz - 1e-3f; z += cell) {
            float zz = Math.min(bz, z + cell);
            for (float x = ax; x < bx - 1e-3f; x += cell) {
                float xx = Math.min(bx, x + cell);
                int t = rnd.nextInt(14) == 0 ? lib.asphaltPatched : lib.asphalt;
                gb.floor(x, z, xx, zz, 0f, t, 2f, 2f, L_GROUND, GeoBuilder.sunLight(0, 1, 0), 0f);
            }
        }
    }

    /**
     * One road segment between two intersections. {@code alongZ}: road runs N-S, crossing axis is x.
     * w0..w1 = road extent across, s0..s1 = extent along.
     */
    private void roadSegment(boolean alongZ, float w0, float w1, float s0, float s1, boolean avenue) {
        float c = (w0 + w1) * 0.5f;
        float y = 0.02f;
        if (avenue) {
            // raised planted median with palms
            float m0 = c - 1.2f, m1 = c + 1.2f, a = s0 + 6f, b = s1 - 6f;
            if (b > a) {
                if (alongZ) median(m0, a, m1, b, true);
                else median(a, m0, b, m1, false);
            }
            line(alongZ, c - 1.55f, c - 1.37f, s0 + 4f, s1 - 4f, lib.paintYellow, y);
            line(alongZ, c + 1.37f, c + 1.55f, s0 + 4f, s1 - 4f, lib.paintYellow, y);
            dashes(alongZ, c - 5.1f, s0 + 4f, s1 - 4f, y);
            dashes(alongZ, c + 5.1f, s0 + 4f, s1 - 4f, y);
            line(alongZ, w0 + 1.6f, w0 + 1.78f, s0 + 4f, s1 - 4f, lib.paintWhite, y);
            line(alongZ, w1 - 1.78f, w1 - 1.6f, s0 + 4f, s1 - 4f, lib.paintWhite, y);
        } else {
            line(alongZ, c - 0.26f, c - 0.08f, s0 + 4f, s1 - 4f, lib.paintYellow, y);
            line(alongZ, c + 0.08f, c + 0.26f, s0 + 4f, s1 - 4f, lib.paintYellow, y);
            line(alongZ, w0 + 2.4f, w0 + 2.58f, s0 + 4f, s1 - 4f, lib.paintWhite, y);
            line(alongZ, w1 - 2.58f, w1 - 2.4f, s0 + 4f, s1 - 4f, lib.paintWhite, y);
        }
        // crosswalks at both ends + stop lines
        for (int end = 0; end < 2; end++) {
            float cs0 = end == 0 ? s0 + 0.6f : s1 - 3.6f;
            for (float x = w0 + 0.8f; x < w1 - 1.2f; x += 1.2f) {
                if (avenue && x + 0.6f > c - 1.3f && x < c + 1.3f) continue;
                line(alongZ, x, x + 0.6f, cs0, cs0 + 3f, lib.paintWhite, y);
            }
            float sl = end == 0 ? s0 + 4.3f : s1 - 4.7f;
            if (end == 0) line(alongZ, w0 + 0.5f, c - (avenue ? 1.5f : 0.2f), sl, sl + 0.4f, lib.paintWhite, y);
            else line(alongZ, c + (avenue ? 1.5f : 0.2f), w1 - 0.5f, sl, sl + 0.4f, lib.paintWhite, y);
        }
    }

    private void dashes(boolean alongZ, float c, float s0, float s1, float y) {
        for (float s = s0 + 2f; s + 3f < s1; s += 9f) line(alongZ, c - 0.1f, c + 0.1f, s, s + 3f, lib.paintWhite, y);
    }

    /** Painted strip: across range a..b, along range s0..s1. */
    private void line(boolean alongZ, float a, float b, float s0, float s1, int tex, float y) {
        if (s1 <= s0 || b <= a) return;
        float l = GeoBuilder.sunLight(0, 1, 0);
        if (alongZ) gb.floor(a, s0, b, s1, y, tex, 1f, 1f, L_DECAL, l, 0f);
        else gb.floor(s0, a, s1, b, y, tex, 1f, 1f, L_DECAL, l, 0f);
    }

    private void median(float ax, float az, float bx, float bz, boolean alongZ) {
        plate(ax, az, bx, bz, lib.grass, 2f);
        boxes.add(new Box(ax, 0, az, bx, CURB, bz));
        if (alongZ) for (float z = az + 3f; z < bz - 1f; z += 11f) palm((ax + bx) * 0.5f, z, CURB);
        else for (float x = ax + 3f; x < bx - 1f; x += 11f) palm(x, (az + bz) * 0.5f, CURB);
    }

    /** Raised slab top (layer RAISED) + curb faces around it. */
    private void plate(float ax, float az, float bx, float bz, int topTex, float uvScale) {
        gb.floorArea(ax, az, bx, bz, CURB, topTex, uvScale, uvScale, L_RAISED, 8f, 0f);
        curbs(ax, az, bx, bz);
    }

    private void curbs(float ax, float az, float bx, float bz) {
        int t = lib.curb;
        float v = CURB / 2f;
        for (float s = ax; s < bx - 1e-3f; s += 8f) {
            float e = Math.min(bx, s + 8f);
            gb.wall(s, bz, e, bz, 0, CURB, t, s / 2f, e / 2f, 0, v, L_RAISED, 0f, 0.85f);      // south
            gb.wall(e, az, s, az, 0, CURB, t, e / 2f, s / 2f, 0, v, L_RAISED, 0f, 0.85f);      // north
        }
        for (float s = az; s < bz - 1e-3f; s += 8f) {
            float e = Math.min(bz, s + 8f);
            gb.wall(bx, e, bx, s, 0, CURB, t, e / 2f, s / 2f, 0, v, L_RAISED, 0f, 0.85f);      // east
            gb.wall(ax, s, ax, e, 0, CURB, t, s / 2f, e / 2f, 0, v, L_RAISED, 0f, 0.85f);      // west
        }
    }

    // ------------------------------------------------------------------ blocks & lots

    private Kind district(int i, int j) {
        float bx = (rx1[i] + rx0[i + 1]) * 0.5f, bz = (rz1[j] + rz0[j + 1]) * 0.5f;
        float dx = (bx - (cityX0 + (cityX1 - cityX0) * 0.36f)) / (cityX1 - cityX0);
        float dz = (bz - (cityZ0 + (cityZ1 - cityZ0) * 0.42f)) / (cityZ1 - cityZ0);
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (i >= nbx - 2) return rnd.nextInt(3) == 0 ? Kind.HOTEL : Kind.DECO;
        if (i <= 1 && j >= nbz - 3) return Kind.INDUSTRIAL;
        if (d < 0.17f) return Kind.OFFICE;
        if (d < 0.3f) return rnd.nextBoolean() ? Kind.COMMERCIAL : (rnd.nextInt(3) == 0 ? Kind.OFFICE : Kind.RESIDENTIAL);
        return rnd.nextInt(3) == 0 ? Kind.COMMERCIAL : (rnd.nextInt(3) == 0 ? Kind.DECO : Kind.RESIDENTIAL);
    }

    private void block(int i, int j) {
        float bx0 = rx1[i], bx1 = rx0[i + 1], bz0 = rz1[j], bz1 = rz0[j + 1];
        boxes.add(new Box(bx0, 0, bz0, bx1, CURB, bz1));
        curbs(bx0, bz0, bx1, bz1);
        // sidewalk ring
        float s = SIDEWALK;
        int sw = lib.sidewalk;
        gb.floorArea(bx0, bz0, bx1, bz0 + s, CURB, sw, 2f, 2f, L_RAISED, 8f, 0f);
        gb.floorArea(bx0, bz1 - s, bx1, bz1, CURB, sw, 2f, 2f, L_RAISED, 8f, 0f);
        gb.floorArea(bx0, bz0 + s, bx0 + s, bz1 - s, CURB, sw, 2f, 2f, L_RAISED, 8f, 0f);
        gb.floorArea(bx1 - s, bz0 + s, bx1, bz1 - s, CURB, sw, 2f, 2f, L_RAISED, 8f, 0f);

        Kind dist = district(i, j);
        boolean palmStreet = dist == Kind.DECO || dist == Kind.HOTEL || rnd.nextInt(3) == 0;
        streetFurniture(bx0, bz0, bx1, bz1, palmStreet);

        float ix0 = bx0 + s, ix1 = bx1 - s, iz0 = bz0 + s, iz1 = bz1 - s;
        float depth = iz1 - iz0;
        boolean twoRows = depth >= 36f;
        float zMid = twoRows ? iz0 + Math.round(depth * (0.42f + rnd.nextFloat() * 0.16f)) : iz1;
        lotRow(ix0, iz0, ix1, zMid, true, !twoRows, dist);
        if (twoRows) lotRow(ix0, zMid, ix1, iz1, false, true, dist);
    }

    private void streetFurniture(float bx0, float bz0, float bx1, float bz1, boolean palms) {
        float in = 1.0f;
        float step = palms ? 12f : 26f;
        for (int side = 0; side < 4; side++) {
            float a, b;
            boolean horiz = side < 2;
            if (horiz) { a = bx0 + 7f; b = bx1 - 7f; } else { a = bz0 + 7f; b = bz1 - 7f; }
            int k = 0;
            for (float t = a; t <= b; t += step, k++) {
                float x, z;
                if (side == 0) { x = t; z = bz0 + in; }
                else if (side == 1) { x = t; z = bz1 - in; }
                else if (side == 2) { x = bx0 + in; z = t; }
                else { x = bx1 - in; z = t; }
                if (palms) palm(x, z, CURB);
                else if ((k & 1) == 0) streetLight(x, z, side);
            }
        }
    }

    private void lotRow(float x0, float z0, float x1, float z1, boolean facesNorth, boolean facesSouth, Kind dist) {
        float x = x0;
        while (x < x1 - 0.5f) {
            float w = 14f + 2f * rnd.nextInt(10);
            if (dist == Kind.OFFICE) w += 14f;
            if (x1 - (x + w) < 12f) w = x1 - x;
            float lx1 = Math.min(x1, x + w);
            lot(x, z0, lx1, z1, facesNorth, facesSouth, x <= x0 + 0.1f, lx1 >= x1 - 0.1f, dist);
            x = lx1;
        }
    }

    private void lot(float x0, float z0, float x1, float z1, boolean fN, boolean fS, boolean fW, boolean fE, Kind dist) {
        int use = rnd.nextInt(100);
        if (use < 6) { parkingLot(x0, z0, x1, z1); return; }
        if (use < 11) { garden(x0, z0, x1, z1, rnd.nextBoolean()); return; }

        Kind kind = dist;
        if (kind == Kind.OFFICE && rnd.nextInt(4) == 0) kind = rnd.nextBoolean() ? Kind.GARAGE : Kind.HOTEL;
        if (kind == Kind.COMMERCIAL && rnd.nextInt(6) == 0) kind = Kind.GARAGE;
        if (kind == Kind.RESIDENTIAL && rnd.nextInt(5) == 0) kind = Kind.COMMERCIAL;

        float front = (kind == Kind.RESIDENTIAL || kind == Kind.HOTEL) ? 2f + rnd.nextInt(3) : 0f;
        float sideGap = (kind == Kind.RESIDENTIAL || kind == Kind.HOTEL || kind == Kind.OFFICE) ? 1f + rnd.nextInt(3) : 0f;
        float fx0 = x0 + (fW ? 0 : sideGap * 0.5f), fx1 = x1 - (fE ? 0 : sideGap * 0.5f);
        float fz0 = z0 + (fN ? front : 1f), fz1 = z1 - (fS ? front : 1f);
        if (fx1 - fx0 < 6f || fz1 - fz0 < 6f) { garden(x0, z0, x1, z1, false); return; }

        // ground around the footprint (setbacks)
        int lotTex = kind == Kind.RESIDENTIAL || kind == Kind.HOTEL ? lib.grass : lib.concrete;
        if (fz0 > z0 + 0.01f) gb.floorArea(x0, z0, x1, fz0, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0f);
        if (fz1 < z1 - 0.01f) gb.floorArea(x0, fz1, x1, z1, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0f);
        if (fx0 > x0 + 0.01f) gb.floorArea(x0, fz0, fx0, fz1, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0f);
        if (fx1 < x1 - 0.01f) gb.floorArea(fx1, fz0, x1, fz1, CURB, lotTex, 2f, 2f, L_RAISED, 8f, 0f);
        if (front >= 2f && lotTex == lib.grass) {
            if (fN) hedge(fx0, z0 + 0.4f, fx1, z0 + 1.2f);
            if (fS) hedge(fx0, z1 - 1.2f, fx1, z1 - 0.4f);
        }

        new BuildingGen(this, lib, gb, rnd).build(kind, fx0, fz0, fx1, fz1, CURB,
                fN && front < 0.5f, fS && front < 0.5f, fW, fE, fN, fS);
        buildingCount++;
    }

    private void parkingLot(float x0, float z0, float x1, float z1) {
        gb.floorArea(x0, z0, x1, z1, CURB, lib.parking, 2.5f, 5f, L_RAISED, 8f, 0f);
        for (float x = x0 + 2f; x < x1 - 1f; x += 10f) streetLight(x, (z0 + z1) * 0.5f, 0);
    }

    private void garden(float x0, float z0, float x1, float z1, boolean plaza) {
        gb.floorArea(x0, z0, x1, z1, CURB, plaza ? lib.pavers : lib.grass, 2f, 2f, L_RAISED, 8f, 0f);
        for (float z = z0 + 3f; z < z1 - 2f; z += 7f + rnd.nextInt(4))
            for (float x = x0 + 3f; x < x1 - 2f; x += 7f + rnd.nextInt(4)) {
                if (plaza) {
                    planter(x, z);
                } else if (rnd.nextInt(3) > 0) palm(x + rnd.nextFloat() * 2f, z + rnd.nextFloat() * 2f, CURB);
                else hedge(x - 1f, z - 0.6f, x + 1.5f, z + 0.6f);
            }
    }

    // ------------------------------------------------------------------ props

    void palm(float x, float z, float baseY) {
        float h = 7f + rnd.nextFloat() * 4f;
        float lean = 0.3f + rnd.nextFloat() * 0.9f, dir = rnd.nextFloat() * 6.283f;
        float lx = (float) Math.sin(dir) * lean, lz = (float) Math.cos(dir) * lean;
        int segs = 3, sides = 5;
        float[] p = new float[12], t = new float[8], l = new float[4];
        for (int s = 0; s < segs; s++) {
            float t0 = s / (float) segs, t1 = (s + 1) / (float) segs;
            float y0 = baseY + h * t0, y1 = baseY + h * t1;
            float o0 = t0 * t0, o1 = t1 * t1;
            float r0 = 0.22f - 0.07f * t0, r1 = 0.22f - 0.07f * t1;
            for (int k = 0; k < sides; k++) {
                double a0 = Math.PI * 2 * k / sides, a1 = Math.PI * 2 * (k + 1) / sides;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                p[0] = x + lx * o0 + s0 * r0; p[1] = y0; p[2] = z + lz * o0 + c0 * r0;
                p[3] = x + lx * o0 + s1 * r0; p[4] = y0; p[5] = z + lz * o0 + c1 * r0;
                p[6] = x + lx * o1 + s1 * r1; p[7] = y1; p[8] = z + lz * o1 + c1 * r1;
                p[9] = x + lx * o1 + s0 * r1; p[10] = y1; p[11] = z + lz * o1 + c0 * r1;
                float u0 = k / (float) sides, u1 = (k + 1) / (float) sides;
                t[0] = u0; t[1] = (1 - t0) * 3; t[2] = u1; t[3] = (1 - t0) * 3; t[4] = u1; t[5] = (1 - t1) * 3; t[6] = u0; t[7] = (1 - t1) * 3;
                float nx = (float) Math.sin((a0 + a1) / 2), nz = (float) Math.cos((a0 + a1) / 2);
                float lt = GeoBuilder.sunLight(nx, 0, nz);
                l[0] = l[1] = l[2] = l[3] = lt;
                gb.quad(p, t, l, lib.trunk, L_OBJECT, Geo.FLAG_NO_SUBDIV, 0f, false);
            }
        }
        // crown
        float cx = x + lx, cz = z + lz, cy = baseY + h;
        int fronds = 7;
        float rot = rnd.nextFloat() * 6.283f;
        for (int f = 0; f < fronds; f++) {
            float a = rot + 6.283f * f / fronds;
            float dx = (float) Math.sin(a), dz = (float) Math.cos(a);
            float px = -dz, pz = dx;                  // perpendicular
            float len = 2.6f + rnd.nextFloat() * 0.8f, wdt = 0.55f;
            float rise = 0.6f + rnd.nextFloat() * 0.4f, droop = -1.6f - rnd.nextFloat() * 0.8f;
            // inner piece: crown -> mid (rising), outer: mid -> tip (drooping)
            float mx = cx + dx * len * 0.5f, my = cy + rise, mz = cz + dz * len * 0.5f;
            float ex = cx + dx * len, ey = cy + rise + droop, ez = cz + dz * len;
            frondQuad(cx, cy, cz, mx, my, mz, px, pz, wdt * 0.7f, wdt, 0f, 0.5f);
            frondQuad(mx, my, mz, ex, ey, ez, px, pz, wdt, wdt * 0.5f, 0.5f, 1f);
        }
        boxes.add(new Box(x - 0.25f, 0, z - 0.25f, x + 0.25f, baseY + h, z + 0.25f));
    }

    private void frondQuad(float ax, float ay, float az, float bx, float by, float bz, float px, float pz,
                           float wa, float wb, float u0, float u1) {
        float[] p = {
                ax - px * wa, ay, az - pz * wa,
                bx - px * wb, by, bz - pz * wb,
                bx + px * wb, by, bz + pz * wb,
                ax + px * wa, ay, az + pz * wa};
        float[] t = {u0, 0f, u1, 0f, u1, 1f, u0, 1f};
        gb.foliage(p, t, lib.frond, 0.95f);
    }

    void streetLight(float x, float z, int side) {
        float h = 7f, w = 0.09f;
        gb.box(x - w, CURB, z - w, x + w, CURB + h, z + w, lib.pole, -1, 1f, 1f, L_OBJECT, 0f);
        // arm reaching toward the road
        float dx = 0, dz = 0;
        if (side == 0) dz = -1; else if (side == 1) dz = 1; else if (side == 2) dx = -1; else dx = 1;
        float ex = x + dx * 2.2f, ez = z + dz * 2.2f;
        float ax0 = Math.min(x, ex) - 0.06f, ax1 = Math.max(x, ex) + 0.06f, az0 = Math.min(z, ez) - 0.06f, az1 = Math.max(z, ez) + 0.06f;
        gb.box(ax0, CURB + h - 0.12f, az0, ax1, CURB + h, az1, lib.pole, lib.pole, 1f, 1f, L_OBJECT, -0.1f);
        gb.box(ex - 0.3f, CURB + h - 0.3f, ez - 0.3f, ex + 0.3f, CURB + h - 0.1f, ez + 0.3f, lib.lampHead, lib.pole, 1f, 1f, L_OBJECT, -0.2f);
        boxes.add(new Box(x - 0.12f, 0, z - 0.12f, x + 0.12f, CURB + h, z + 0.12f));
    }

    void hedge(float x0, float z0, float x1, float z1) {
        gb.box(x0, CURB, z0, x1, CURB + 1.0f, z1, lib.hedge, lib.hedge, 0.5f, 0.5f, L_OBJECT, 0f);
        boxes.add(new Box(x0, 0, z0, x1, CURB + 1.0f, z1));
    }

    void planter(float x, float z) {
        gb.box(x - 1f, CURB, z - 1f, x + 1f, CURB + 0.6f, z + 1f, lib.planter, lib.hedge, 0.5f, 0.5f, L_OBJECT, 0f);
        boxes.add(new Box(x - 1f, 0, z - 1f, x + 1f, CURB + 0.6f, z + 1f));
        palm(x, z, CURB + 0.6f);
    }

    // ------------------------------------------------------------------ beach & surroundings

    private void beach(float outer) {
        // promenade park strip
        float px0 = parkX0, px1 = sandX0;
        gb.floorArea(px0, cityZ0, px1, cityZ1, CURB, lib.grass, 2f, 2f, L_RAISED, 8f, 0f);
        gb.floorArea(px0 + 10f, cityZ0, px0 + 15f, cityZ1, CURB + 0.01f, lib.pavers, 2f, 2f, L_RAISED, 8f, -0.05f);
        curbs(px0, cityZ0, px1, cityZ1);
        boxes.add(new Box(px0, 0, cityZ0, px1, CURB, cityZ1));
        for (float z = cityZ0 + 4f; z < cityZ1 - 2f; z += 9f + rnd.nextInt(5)) {
            palm(px0 + 4f + rnd.nextFloat() * 4f, z, CURB);
            if (rnd.nextBoolean()) palm(px0 + 18f + rnd.nextFloat() * 5f, z + 3f, CURB);
        }
        // sand
        gb.floorArea(sandX0, cityZ0 - outer, waterX0, cityZ1 + outer, 0f, lib.sand, 2f, 2f, L_GROUND, 8f, 0f);
        // umbrellas and lifeguard huts
        for (float z = cityZ0 + 10f; z < cityZ1 - 10f; z += 14f + rnd.nextInt(10)) {
            if (rnd.nextInt(5) == 0) lifeguard(sandX0 + 40f, z);
            else umbrella(sandX0 + 20f + rnd.nextFloat() * 30f, z);
        }
        // ocean
        float l = GeoBuilder.sunLight(0, 1, 0);
        for (float z = cityZ0 - outer; z < cityZ1 + outer; z += 32f)
            for (float x = waterX0; x < waterX0 + 420f; x += 32f)
                gb.floor(x, z, x + 32f, z + 32f, -0.05f, lib.water, 4f, 4f, L_GROUND, l, 0f);
    }

    private void umbrella(float x, float z) {
        gb.box(x - 0.04f, 0, z - 0.04f, x + 0.04f, 2.2f, z + 0.04f, lib.pole, -1, 1f, 1f, L_OBJECT, 0f);
        int n = 6;
        float r = 1.3f, top = 2.5f, rim = 2.0f;
        for (int k = 0; k < n; k++) {
            double a0 = Math.PI * 2 * k / n, a1 = Math.PI * 2 * (k + 1) / n;
            float[] p = {x, top, z,
                    x + (float) Math.sin(a0) * r, rim, z + (float) Math.cos(a0) * r,
                    x + (float) Math.sin(a1) * r, rim, z + (float) Math.cos(a1) * r};
            float[] t = {0.5f, 0f, 0f, 1f, 1f, 1f};
            float[] l = {1f, 0.85f, 0.85f};
            gb.quad3(p, t, l, lib.umbrella, L_OBJECT);
        }
        boxes.add(new Box(x - 0.1f, 0, z - 0.1f, x + 0.1f, 2.2f, z + 0.1f));
    }

    private void lifeguard(float x, float z) {
        int[] deco = lib.facades(Kind.BLANK);
        int t = deco[rnd.nextInt(deco.length)];
        for (int k = 0; k < 4; k++) {
            float lx = x + ((k & 1) == 0 ? -1.1f : 1.1f), lz = z + ((k & 2) == 0 ? -1.1f : 1.1f);
            gb.box(lx - 0.08f, 0, lz - 0.08f, lx + 0.08f, 1.6f, lz + 0.08f, lib.planter, -1, 1f, 1f, L_OBJECT, 0f);
        }
        gb.box(x - 1.4f, 1.6f, z - 1.4f, x + 1.4f, 3.8f, z + 1.4f, t, lib.roofMembrane, 0.5f, 0.45f, L_OBJECT, 0f);
        boxes.add(new Box(x - 1.4f, 0, z - 1.4f, x + 1.4f, 3.8f, z + 1.4f));
    }

    private void outskirts(float outer) {
        // flat lots beyond the playable grid so the fog has something to fade into
        float x0 = cityX0 - outer, z0 = cityZ0 - outer, z1 = cityZ1 + outer;
        float l = GeoBuilder.sunLight(0, 1, 0);
        for (float z = z0; z < z1; z += 16f)
            for (float x = x0; x < sandX0; x += 16f) {
                boolean inside = x >= cityX0 && x + 16f <= sandX0 && z >= cityZ0 && z + 16f <= cityZ1;
                if (inside) continue;
                boolean overlaps = x + 16f > cityX0 && x < sandX0 && z + 16f > cityZ0 && z < cityZ1;
                if (overlaps) continue;
                gb.floor(x, z, x + 16f, z + 16f, 0f, ((int) (x / 16) + (int) (z / 16)) % 5 == 0 ? lib.concrete : lib.grass,
                        2f, 2f, L_GROUND, l, 0f);
            }
    }
}
