package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.tex.Facades.Kind;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/** Builds one building (walls, shopfronts, parapet, roof, rooftop clutter) inside a footprint. */
final class BuildingGen {
    private static final int N = 0, S = 1, W = 2, E = 3;
    private final City city;
    private final TextureLibrary lib;
    private final GeoBuilder gb;
    private final Random r;

    BuildingGen(City city, TextureLibrary lib, GeoBuilder gb, Random r) {
        this.city = city;
        this.lib = lib;
        this.gb = gb;
        this.r = r;
    }

    private int pick(Kind k) {
        int[] a = lib.facades(k);
        return a[r.nextInt(a.length)];
    }

    /**
     * @param shopN/shopS  ground-floor shops on that side (no setback)
     * @param fW/fE/fN/fS  side faces a street
     */
    void build(Kind kind, float x0, float z0, float x1, float z1, float base,
               boolean shopN, boolean shopS, boolean fW, boolean fE, boolean fN, boolean fS) {
        boolean[] street = {fN, fS, fW, fE};
        boolean[] shops = {shopN, shopS, false, false};
        boolean shopsOnCorners = kind == Kind.DECO || kind == Kind.COMMERCIAL || kind == Kind.OFFICE;
        if (shopsOnCorners) {
            shops[W] = fW && (shopN || shopS);
            shops[E] = fE && (shopN || shopS);
        }
        int facade, groundTex = -1, party = pick(Kind.BLANK);
        int floors;
        float floorH = City.FLOOR_H, parapet = 0.9f;
        boolean useShops = true, allSidesFacade = false, lobby = false;
        int roof = r.nextBoolean() ? lib.roofMembrane : lib.roofGravel;

        switch (kind) {
            case DECO:
                facade = pick(Kind.DECO);
                floors = 2 + r.nextInt(3);
                parapet = 1.2f + r.nextInt(3) * 0.4f;
                roof = lib.roofMembrane;
                break;
            case RESIDENTIAL:
                facade = pick(Kind.RESIDENTIAL);
                floors = 3 + r.nextInt(7);
                allSidesFacade = !(shopN || shopS);
                break;
            case HOTEL:
                facade = pick(Kind.HOTEL);
                floors = 8 + r.nextInt(12);
                allSidesFacade = true;
                lobby = true;
                useShops = false;
                break;
            case OFFICE:
                officeTower(x0, z0, x1, z1, base, street, shops);
                return;
            case COMMERCIAL:
                facade = pick(Kind.COMMERCIAL);
                floors = 2 + r.nextInt(5);
                break;
            case GARAGE:
                facade = pick(Kind.GARAGE);
                floors = 4 + r.nextInt(4);
                floorH = 3.0f;
                useShops = false;
                allSidesFacade = true;
                groundTex = facade;
                roof = lib.concrete;
                break;
            default: // INDUSTRIAL
                facade = pick(Kind.INDUSTRIAL);
                floors = 1 + r.nextInt(2);
                floorH = 5.5f;
                useShops = false;
                allSidesFacade = true;
                groundTex = facade;
                roof = lib.roofTar;
                parapet = 0.5f;
                break;
        }

        float groundH = (useShops && (shops[N] || shops[S])) || lobby ? City.SHOP_H : floorH;
        float top = base + groundH + (floors - 1) * floorH;
        int[] sideTex = new int[4];
        for (int s = 0; s < 4; s++) sideTex[s] = (street[s] || allSidesFacade) ? facade : party;
        if (groundTex < 0) groundTex = facade;

        for (int s = 0; s < 4; s++) {
            float[] ab = edge(s, x0, z0, x1, z1);
            int ground;
            if (useShops && shops[s]) ground = -1;
            else if (lobby && street[s]) ground = -2;
            else ground = sideTex[s] == party ? party : groundTex;
            facadeSide(ab, base, groundH, top, floorH, sideTex[s], ground, parapet);
        }
        roofAndParapet(x0, z0, x1, z1, top, parapet, roof, sideTex);
        rooftop(x0, z0, x1, z1, top, kind);
        if (kind == Kind.DECO && r.nextBoolean()) decoFin(x0, z0, x1, z1, base, top + parapet, street);
        city.boxes.add(new Box(x0, 0, z0, x1, top + parapet, z1));
    }

    private void officeTower(float x0, float z0, float x1, float z1, float base, boolean[] street, boolean[] shops) {
        int podFacade = pick(Kind.COMMERCIAL);
        int tower = pick(Kind.OFFICE);
        int podFloors = 2 + r.nextInt(3);
        float podTop = base + City.SHOP_H + (podFloors - 1) * City.FLOOR_H;
        for (int s = 0; s < 4; s++) {
            int ground = shops[s] ? -1 : (street[s] ? -2 : podFacade);
            facadeSide(edge(s, x0, z0, x1, z1), base, City.SHOP_H, podTop, City.FLOOR_H, podFacade, ground, 0.9f);
        }
        float in = 3f + r.nextInt(3);
        float tx0 = x0 + in, tz0 = z0 + in, tx1 = x1 - in, tz1 = z1 - in;
        int[] podSides = {podFacade, podFacade, podFacade, podFacade};
        if (tx1 - tx0 < 10f || tz1 - tz0 < 10f) {
            roofAndParapet(x0, z0, x1, z1, podTop, 0.9f, lib.roofGravel, podSides);
            city.boxes.add(new Box(x0, 0, z0, x1, podTop + 0.9f, z1));
            return;
        }
        roofAndParapet(x0, z0, x1, z1, podTop, 0.9f, lib.roofGravel, podSides);
        float floorH = 3.6f;
        int floors = 10 + r.nextInt(26);
        float top = podTop + floors * floorH;
        for (int s = 0; s < 4; s++)
            facadeSide(edge(s, tx0, tz0, tx1, tz1), podTop, 0f, top, floorH, tower, tower, 1.2f);
        int[] tSides = {tower, tower, tower, tower};
        roofAndParapet(tx0, tz0, tx1, tz1, top, 1.2f, lib.roofMembrane, tSides);
        rooftop(tx0, tz0, tx1, tz1, top, Kind.OFFICE);
        city.boxes.add(new Box(x0, 0, z0, x1, podTop + 0.9f, z1));
        city.boxes.add(new Box(tx0, 0, tz0, tx1, top + 1.2f, tz1));
    }

    /** Outer edge A->B of a footprint side, so that (B-A) x up points outward. */
    static float[] edge(int side, float x0, float z0, float x1, float z1) {
        switch (side) {
            case N: return new float[]{x1, z0, x0, z0};
            case S: return new float[]{x0, z1, x1, z1};
            case W: return new float[]{x0, z0, x0, z1};
            default: return new float[]{x1, z1, x1, z0};
        }
    }

    /**
     * One side: ground storey (shops = -1, lobby = -2, otherwise a texture), upper storeys with
     * the facade texture, and the parapet band.
     */
    private void facadeSide(float[] ab, float base, float groundH, float top, float floorH, int tex, int ground, float parapet) {
        float ax = ab[0], az = ab[1], bx = ab[2], bz = ab[3];
        float dx = bx - ax, dz = bz - az, len = (float) Math.sqrt(dx * dx + dz * dz);
        float g1 = base + groundH;
        if (groundH > 0f) {
            if (ground == -1 || ground == -2) {
                int units = Math.max(1, Math.round(len / 7f));
                for (int u = 0; u < units; u++) {
                    float s0 = u / (float) units, s1 = (u + 1) / (float) units;
                    int t;
                    if (ground == -2 && u == units / 2) t = lib.lobbies[r.nextInt(lib.lobbies.length)];
                    else if (ground == -2) t = lib.facades(Kind.OFFICE)[r.nextInt(lib.facades(Kind.OFFICE).length)];
                    else t = lib.shops[r.nextInt(lib.shops.length)];
                    gb.wall(ax + dx * s0, az + dz * s0, ax + dx * s1, az + dz * s1, base, g1, t,
                            0f, 1f, 0f, 1f, L_OBJECT, 0f, 0.8f);
                }
            } else {
                gb.wallGrid(ax, az, bx, bz, base, g1, ground, 0.5f, 1f / floorH, 8f, groundH, L_OBJECT, 0f, true);
            }
        }
        if (top > g1 + 0.01f)
            gb.wallGrid(ax, az, bx, bz, g1, top, tex, 0.5f, 1f / floorH, 8f, floorH * 4f, L_OBJECT, 0f, groundH <= 0f);
        // parapet: sample only the bottom band of the facade (the slab / ledge colour)
        if (parapet > 0f) {
            int cols = Math.max(1, (int) Math.ceil(len / 8f));
            for (int c = 0; c < cols; c++) {
                float s0 = c / (float) cols, s1 = (c + 1) / (float) cols;
                gb.wall(ax + dx * s0, az + dz * s0, ax + dx * s1, az + dz * s1, top, top + parapet, tex,
                        len * s0 * 0.5f, len * s1 * 0.5f, 0.955f, 0.995f, L_OBJECT, 0f, 1f);
            }
        }
    }

    private void roofAndParapet(float x0, float z0, float x1, float z1, float top, float parapet, int roofTex, int[] sideTex) {
        // roof slab drawn "behind" everything on it via ordering-table bias
        float l = GeoBuilder.sunLight(0, 1, 0) * 0.95f;
        for (float z = z0; z < z1 - 1e-3f; z += 12f)
            for (float x = x0; x < x1 - 1e-3f; x += 12f)
                gb.floor(x, z, Math.min(x1, x + 12f), Math.min(z1, z + 12f), top, roofTex, 2f, 2f, L_OBJECT, l, 3f);
        if (parapet <= 0f) return;
        // inner parapet faces (point inward, visible from above on the far side)
        float[][] inner = {{x0, z0, x1, z0}, {x1, z1, x0, z1}, {x0, z1, x0, z0}, {x1, z0, x1, z1}};
        for (float[] e : inner) {
            float dx = e[2] - e[0], dz = e[3] - e[1], len = (float) Math.sqrt(dx * dx + dz * dz);
            int cols = Math.max(1, (int) Math.ceil(len / 8f));
            for (int c = 0; c < cols; c++) {
                float s0 = c / (float) cols, s1 = (c + 1) / (float) cols;
                gb.wall(e[0] + dx * s0, e[1] + dz * s0, e[0] + dx * s1, e[1] + dz * s1, top, top + parapet, lib.concrete,
                        len * s0 * 0.5f, len * s1 * 0.5f, 0f, parapet * 0.5f, L_OBJECT, 1.5f, 0.8f);
            }
        }
    }

    private void rooftop(float x0, float z0, float x1, float z1, float top, Kind kind) {
        float w = x1 - x0, d = z1 - z0;
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            float ax = x0 + 1.5f + r.nextFloat() * Math.max(0.1f, w - 5f), az = z0 + 1.5f + r.nextFloat() * Math.max(0.1f, d - 4f);
            gb.box(ax, top, az, ax + 1.8f, top + 1.2f, az + 1.2f, lib.metal, lib.metal, 0.5f, 0.5f, L_OBJECT, -0.3f);
        }
        if (w > 10f && d > 10f && r.nextBoolean()) {
            float ax = x0 + w * 0.5f - 1.5f, az = z0 + d * 0.5f - 1.5f;
            gb.box(ax, top, az, ax + 3f, top + 3f, az + 3f, lib.planter, lib.roofTar, 0.5f, 0.5f, L_OBJECT, -0.3f);
        }
    }

    private void decoFin(float x0, float z0, float x1, float z1, float base, float top, boolean[] street) {
        float cx = (x0 + x1) * 0.5f, cz = (z0 + z1) * 0.5f;
        float h = top + 2f + r.nextInt(3);
        float hw = 1.2f, out = 0.7f;
        if (street[N]) gb.box(cx - hw, base + City.SHOP_H, z0 - out, cx + hw, h, z0 + 0.3f, lib.planter, lib.planter, 0.5f, 0.5f, L_OBJECT, -0.2f);
        else if (street[S]) gb.box(cx - hw, base + City.SHOP_H, z1 - 0.3f, cx + hw, h, z1 + out, lib.planter, lib.planter, 0.5f, 0.5f, L_OBJECT, -0.2f);
    }
}
