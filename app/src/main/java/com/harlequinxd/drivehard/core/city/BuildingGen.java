package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.tex.JpTextures.Kind;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Builds Japanese buildings inside a footprint: office towers, department stores, multi-tenant
 * bar buildings (with vertical signboards), apartment mansions, shophouses, convenience stores,
 * pachinko parlours, detached houses with tiled gable roofs, small apartments, fire stations,
 * police boxes and warehouses.
 */
final class BuildingGen {
    static final int N = 0, S = 1, W = 2, E = 3;
    enum Type { TOWER, DEPT_STORE, ZAKKYO, MANSION, SHOPHOUSE, KONBINI, PACHINKO, HOUSE, APAATO, FIRE_STATION, KOBAN, WAREHOUSE }

    private final City city;
    private final TextureLibrary lib;
    private final GeoBuilder gb;
    private final Random r;
    private final Chunk ch;
    private float footMin;

    BuildingGen(City city, TextureLibrary lib, GeoBuilder gb, Random r, Chunk ch) {
        this.city = city;
        this.lib = lib;
        this.gb = gb;
        this.r = r;
        this.ch = ch;
    }

    private int pick(Kind k) {
        int[] a = lib.facades(k);
        return a[r.nextInt(a.length)];
    }

    /**
     * @param street  which sides face a street (N,S,W,E)
     * @param shops   which sides get ground-floor shops
     * @param shopSet shop texture ids to choose from (by district)
     */
    void build(Type type, float x0, float z0, float x1, float z1, boolean[] street, boolean[] shops, int[] shopSet) {
        gb.begin(true);
        float base = foundation(x0, z0, x1, z1);
        switch (type) {
            case TOWER: tower(x0, z0, x1, z1, base, street, shops, shopSet); break;
            case DEPT_STORE: block(x0, z0, x1, z1, base, street, shops, shopSet, Kind.BLANK, Kind.BLANK, 6 + r.nextInt(4), 4.2f, true, 4); break;
            case ZAKKYO: block(x0, z0, x1, z1, base, street, shops, shopSet, Kind.ZAKKYO, Kind.BLANK, 4 + r.nextInt(5), 3.3f, false, 2); break;
            case MANSION: block(x0, z0, x1, z1, base, street, shops, shopSet, Kind.MANSION, Kind.CORRIDOR, 5 + r.nextInt(8), 3.0f, false, 0); break;
            case SHOPHOUSE: block(x0, z0, x1, z1, base, street, shops, shopSet, Kind.SHOPHOUSE, Kind.BLANK, 2 + r.nextInt(3), 3.0f, false, r.nextInt(2)); break;
            case KONBINI: block(x0, z0, x1, z1, base, street, shops, shopSet, Kind.BLANK, Kind.BLANK, 1, 3.6f, false, 0); break;
            case PACHINKO: block(x0, z0, x1, z1, base, street, shops, shopSet, Kind.ZAKKYO, Kind.BLANK, 2, 4.0f, true, 3); break;
            case HOUSE: house(x0, z0, x1, z1, base, street); break;
            case APAATO: block(x0, z0, x1, z1, base, street, new boolean[4], shopSet, Kind.CORRIDOR, Kind.HOUSE, 2, 2.9f, false, 0); break;
            case FIRE_STATION: fireStation(x0, z0, x1, z1, base, street); break;
            case KOBAN: koban(x0, z0, x1, z1, base, street); break;
            default: block(x0, z0, x1, z1, base, street, new boolean[4], shopSet, Kind.WAREHOUSE, Kind.WAREHOUSE, 1 + r.nextInt(2), 5f, false, 0); break;
        }
        gb.end();
    }

    private float foundation(float x0, float z0, float x1, float z1) {
        Terrain t = city.terrain;
        float c = City.CURB;
        float h00 = t.height(x0, z0) + c, h10 = t.height(x1, z0) + c, h01 = t.height(x0, z1) + c, h11 = t.height(x1, z1) + c;
        float base = Math.max(Math.max(h00, h10), Math.max(h01, h11));
        footMin = Math.min(Math.min(h00, h10), Math.min(h01, h11)) - c - 0.3f;
        int tex = lib.concrete;
        gb.wallSloped(x1, z0, x0, z0, h10 - 0.1f, h00 - 0.1f, base, base, tex, 0, (x1 - x0) * 0.5f, 0, 0.5f, L_OBJECT, 0f, 0.8f, 0);
        gb.wallSloped(x0, z1, x1, z1, h01 - 0.1f, h11 - 0.1f, base, base, tex, 0, (x1 - x0) * 0.5f, 0, 0.5f, L_OBJECT, 0f, 0.8f, 0);
        gb.wallSloped(x0, z0, x0, z1, h00 - 0.1f, h01 - 0.1f, base, base, tex, 0, (z1 - z0) * 0.5f, 0, 0.5f, L_OBJECT, 0f, 0.8f, 0);
        gb.wallSloped(x1, z1, x1, z0, h11 - 0.1f, h10 - 0.1f, base, base, tex, 0, (z1 - z0) * 0.5f, 0, 0.5f, L_OBJECT, 0f, 0.8f, 0);
        return base;
    }

    static float[] edge(int side, float x0, float z0, float x1, float z1) {
        switch (side) {
            case N: return new float[]{x1, z0, x0, z0};
            case S: return new float[]{x0, z1, x1, z1};
            case W: return new float[]{x0, z0, x0, z1};
            default: return new float[]{x1, z1, x1, z0};
        }
    }

    // ------------------------------------------------------------------ generic box building

    /**
     * Flat-roofed box: front facade on street sides, back facade elsewhere, optional ground shops,
     * vertical signboards ({@code signs} per street side) and rooftop clutter.
     */
    private void block(float x0, float z0, float x1, float z1, float base, boolean[] street, boolean[] shops, int[] shopSet,
                       Kind front, Kind back, int floors, float floorH, boolean bigSign, int signs) {
        int fTex = pick(front), bTex = pick(back), side = pick(Kind.BLANK);
        boolean anyShop = shops[0] || shops[1] || shops[2] || shops[3];
        float groundH = anyShop ? City.SHOP_H : floorH;
        float top = base + groundH + (floors - 1) * floorH;
        float parapet = 0.8f;
        for (int s = 0; s < 4; s++) {
            int tex = street[s] ? fTex : (s == N || s == S ? bTex : side);
            int ground = shops[s] ? -1 : tex;
            facadeSide(edge(s, x0, z0, x1, z1), base, groundH, top, floorH, tex, ground, parapet, shopSet);
            if (street[s] && signs > 0 && floors > 1) verticalSigns(s, x0, z0, x1, z1, base + groundH + 0.6f, top, signs);
        }
        roof(x0, z0, x1, z1, top, parapet, r.nextBoolean() ? lib.roofMembrane : lib.concrete);
        rooftop(x0, z0, x1, z1, top);
        if (bigSign) rooftopSign(x0, z0, x1, z1, top + parapet, street);
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + parapet, z1));
    }

    private void tower(float x0, float z0, float x1, float z1, float base, boolean[] street, boolean[] shops, int[] shopSet) {
        int pod = pick(Kind.OFFICE_GRID);
        int podFloors = 2 + r.nextInt(3);
        float podTop = base + City.SHOP_H + (podFloors - 1) * City.FLOOR_H;
        for (int s = 0; s < 4; s++) {
            int ground = shops[s] ? -1 : (street[s] ? -2 : pod);
            facadeSide(edge(s, x0, z0, x1, z1), base, City.SHOP_H, podTop, City.FLOOR_H, pod, ground, 0.9f, shopSet);
        }
        roof(x0, z0, x1, z1, podTop, 0.9f, lib.roofGravel);
        float in = 3f + r.nextInt(3);
        float tx0 = x0 + in, tz0 = z0 + in, tx1 = x1 - in, tz1 = z1 - in;
        ch.boxes.add(new Box(x0, footMin, z0, x1, podTop + 0.9f, z1));
        if (tx1 - tx0 < 10f || tz1 - tz0 < 10f) return;
        int tower = pick(r.nextBoolean() ? Kind.GLASS : Kind.OFFICE_GRID);
        int floors = 12 + r.nextInt(28);
        float top = podTop + floors * 3.6f;
        for (int s = 0; s < 4; s++)
            facadeSide(edge(s, tx0, tz0, tx1, tz1), podTop, 0f, top, 3.6f, tower, tower, 1.2f, shopSet);
        roof(tx0, tz0, tx1, tz1, top, 1.2f, lib.roofMembrane);
        rooftop(tx0, tz0, tx1, tz1, top);
        // helipad-style red/white aviation light mast
        gb.box((tx0 + tx1) * 0.5f - 0.15f, top, (tz0 + tz1) * 0.5f - 0.15f, (tx0 + tx1) * 0.5f + 0.15f, top + 6f,
                (tz0 + tz1) * 0.5f + 0.15f, lib.pole, -1, 1f, 1f, L_OBJECT, -0.2f, Geo.FLAG_DETAIL);
        ch.boxes.add(new Box(tx0, footMin, tz0, tx1, top + 1.2f, tz1));
    }

    private void facadeSide(float[] ab, float base, float groundH, float top, float floorH, int tex, int ground,
                            float parapet, int[] shopSet) {
        float ax = ab[0], az = ab[1], bx = ab[2], bz = ab[3];
        float dx = bx - ax, dz = bz - az, len = (float) Math.sqrt(dx * dx + dz * dz);
        float g1 = base + groundH;
        if (groundH > 0f) {
            if (ground == -1 || ground == -2) {
                int units = Math.max(1, Math.round(len / 5.5f));
                for (int u = 0; u < units; u++) {
                    float s0 = u / (float) units, s1 = (u + 1) / (float) units;
                    int t;
                    if (ground == -2) t = u == units / 2 ? lib.lobbies[r.nextInt(lib.lobbies.length)] : lib.facades(Kind.GLASS)[0];
                    else t = shopSet[r.nextInt(shopSet.length)];
                    gb.wall(ax + dx * s0, az + dz * s0, ax + dx * s1, az + dz * s1, base, g1, t, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 0.8f);
                }
            } else {
                gb.wallGrid(ax, az, bx, bz, base, g1, ground, 0.5f, 1f / floorH, 8f, groundH, L_OBJECT, 0f, true);
            }
        }
        if (top > g1 + 0.01f)
            gb.wallGrid(ax, az, bx, bz, g1, top, tex, 0.5f, 1f / floorH, 8f, floorH * 4f, L_OBJECT, 0f, groundH <= 0f);
        if (parapet > 0f) {
            int cols = Math.max(1, (int) Math.ceil(len / 8f));
            for (int c = 0; c < cols; c++) {
                float s0 = c / (float) cols, s1 = (c + 1) / (float) cols;
                gb.wall(ax + dx * s0, az + dz * s0, ax + dx * s1, az + dz * s1, top, top + parapet, tex,
                        len * s0 * 0.5f, len * s1 * 0.5f, 0.955f, 0.995f, L_OBJECT, 0f, 1f);
            }
        }
    }

    private void roof(float x0, float z0, float x1, float z1, float top, float parapet, int roofTex) {
        float l = GeoBuilder.sunLight(0, 1, 0) * 0.95f;
        for (float z = z0; z < z1 - 1e-3f; z += 12f)
            for (float x = x0; x < x1 - 1e-3f; x += 12f)
                gb.floor(x, z, Math.min(x1, x + 12f), Math.min(z1, z + 12f), top, roofTex, 2f, 2f, L_OBJECT, l, 3f);
        if (parapet <= 0f) return;
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

    private void rooftop(float x0, float z0, float x1, float z1, float top) {
        float w = x1 - x0, d = z1 - z0;
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            float ax = x0 + 1.2f + r.nextFloat() * Math.max(0.1f, w - 4f), az = z0 + 1.2f + r.nextFloat() * Math.max(0.1f, d - 3f);
            gb.box(ax, top, az, ax + 1.6f, top + 1.1f, az + 1f, lib.metal, lib.metal, 0.5f, 0.5f, L_OBJECT, -0.3f, Geo.FLAG_DETAIL);
        }
        if (w > 8f && d > 8f && r.nextBoolean()) {     // stair / elevator housing + water tank
            float ax = x0 + w * 0.5f - 1.5f, az = z0 + d * 0.5f - 1.5f;
            gb.box(ax, top, az, ax + 3f, top + 3f, az + 3f, lib.concrete, lib.roofTar, 0.5f, 0.5f, L_OBJECT, -0.3f, Geo.FLAG_DETAIL);
            if (r.nextBoolean())
                gb.box(ax + 0.5f, top + 3f, az + 0.5f, ax + 2.5f, top + 4.6f, az + 2.5f, lib.metal, lib.metal, 0.5f, 0.5f, L_OBJECT, -0.4f, Geo.FLAG_DETAIL);
        }
    }

    /** A big lit billboard on the roof facing the main street. */
    private void rooftopSign(float x0, float z0, float x1, float z1, float y, boolean[] street) {
        int t = lib.shops[r.nextInt(lib.shops.length)];
        float h = 3.2f;
        if (street[S]) gb.wall(x0 + 1, z1 - 0.5f, x1 - 1, z1 - 0.5f, y, y + h, t, 0f, 1f, 0f, 0.3f, L_OBJECT, -0.5f, 1f);
        else if (street[N]) gb.wall(x1 - 1, z0 + 0.5f, x0 + 1, z0 + 0.5f, y, y + h, t, 0f, 1f, 0f, 0.3f, L_OBJECT, -0.5f, 1f);
    }

    /** Vertical signboards sticking out perpendicular to a street facade (both faces lettered). */
    private void verticalSigns(int side, float x0, float z0, float x1, float z1, float yBottom, float top, int count) {
        float h = Math.min(4.5f, top - yBottom - 0.3f);
        if (h < 1.5f) return;
        boolean alongX = side == N || side == S;
        float len = alongX ? x1 - x0 : z1 - z0;
        for (int i = 0; i < count; i++) {
            float t = (i + 0.5f) / count + (r.nextFloat() - 0.5f) * 0.15f;
            int tex = lib.signs[r.nextInt(lib.signs.length)];
            float out = 1.0f, th = 0.12f;
            float y0 = yBottom + r.nextFloat() * Math.max(0f, top - yBottom - h - 0.3f), y1 = y0 + h;
            if (alongX) {
                float x = x0 + len * t;
                float zf = side == N ? z0 : z1, zo = side == N ? z0 - out : z1 + out;
                // faces looking along +X and -X
                gb.wall(x + th, Math.min(zf, zo), x + th, Math.max(zf, zo), y0, y1, tex, 0f, 1f, 0f, 1f, L_OBJECT, -0.4f, 1f);
                gb.wall(x - th, Math.max(zf, zo), x - th, Math.min(zf, zo), y0, y1, tex, 0f, 1f, 0f, 1f, L_OBJECT, -0.4f, 1f);
                ch.boxes.add(new Box(x - th, y0, Math.min(zf, zo), x + th, y1, Math.max(zf, zo)));
            } else {
                float z = z0 + len * t;
                float xf = side == W ? x0 : x1, xo = side == W ? x0 - out : x1 + out;
                gb.wall(Math.max(xf, xo), z + th, Math.min(xf, xo), z + th, y0, y1, tex, 0f, 1f, 0f, 1f, L_OBJECT, -0.4f, 1f);
                gb.wall(Math.min(xf, xo), z - th, Math.max(xf, xo), z - th, y0, y1, tex, 0f, 1f, 0f, 1f, L_OBJECT, -0.4f, 1f);
            }
        }
    }

    // ------------------------------------------------------------------ houses

    private void house(float x0, float z0, float x1, float z1, float base, boolean[] street) {
        boolean wood = r.nextInt(5) == 0;
        int wall = pick(wood ? Kind.HOUSE_WOOD : Kind.HOUSE);
        boolean modern = !wood && r.nextInt(4) == 0;
        float floorH = 2.8f;
        int floors = modern && r.nextBoolean() ? 3 : 2;
        float top = base + floors * floorH;
        for (int s = 0; s < 4; s++)
            gb.wallGrid(edge(s, x0, z0, x1, z1)[0], edge(s, x0, z0, x1, z1)[1], edge(s, x0, z0, x1, z1)[2],
                    edge(s, x0, z0, x1, z1)[3], base, top, wall, 0.5f, 1f / floorH, 8f, floorH * 2f, L_OBJECT, 0f, true);
        // front door on a street side
        for (int s = 0; s < 4; s++) {
            if (!street[s]) continue;
            float[] e = edge(s, x0, z0, x1, z1);
            float dx = e[2] - e[0], dz = e[3] - e[1];
            float t0 = 0.15f, t1 = 0.3f;
            float nx = -dz, nz = dx, nl = (float) Math.sqrt(nx * nx + nz * nz);
            nx = nx / nl * 0.02f; nz = nz / nl * 0.02f;
            gb.wall(e[0] + dx * t0 + nx, e[1] + dz * t0 + nz, e[0] + dx * t1 + nx, e[1] + dz * t1 + nz, base, base + 2.1f,
                    lib.facades(Kind.CORRIDOR)[r.nextInt(lib.facades(Kind.CORRIDOR).length)], 0.2f, 0.62f, 0.17f, 0.69f, L_OBJECT, -0.05f, 1f);
            break;
        }
        if (modern) {
            roof(x0, z0, x1, z1, top, 0.4f, lib.roofMembrane);
            ch.boxes.add(new Box(x0, footMin, z0, x1, top + 0.4f, z1));
        } else {
            int roofTex = lib.roofs[r.nextInt(lib.roofs.length)];
            gable(x0, z0, x1, z1, top, wall, roofTex);
            ch.boxes.add(new Box(x0, footMin, z0, x1, top + 1.2f, z1));
        }
    }

    /** Pitched roof of kawara tiles with gable ends; ridge along the longer side. */
    private void gable(float x0, float z0, float x1, float z1, float top, int wallTex, int roofTex) {
        float ov = 0.45f;
        boolean ridgeX = (x1 - x0) >= (z1 - z0);
        float half = ridgeX ? (z1 - z0) * 0.5f : (x1 - x0) * 0.5f;
        float rh = half * 0.55f, slope = rh / half;
        float ye = top - ov * slope, yr = top + rh;
        float[] p = new float[12], t = new float[8], l = new float[4];
        float slopeLen = (float) Math.sqrt((half + ov) * (half + ov) + (rh + ov * slope) * (rh + ov * slope));
        if (ridgeX) {
            float zc = (z0 + z1) * 0.5f, xa = x0 - ov, xb = x1 + ov;
            quad(p, xb, ye, z0 - ov, xa, ye, z0 - ov, xa, yr, zc, xb, yr, zc);            // north slope
            uvs(t, (xb - xa) * 0.5f, slopeLen * 0.5f);
            light(l, 0, 0.75f, -0.6f);
            gb.quad(p, t, l, roofTex, L_OBJECT, 0, -0.2f, false);
            quad(p, xa, ye, z1 + ov, xb, ye, z1 + ov, xb, yr, zc, xa, yr, zc);            // south slope
            light(l, 0, 0.75f, 0.6f);
            gb.quad(p, t, l, roofTex, L_OBJECT, 0, -0.2f, false);
            gableEnd(x0, z0, x0, z1, top, x0, yr, zc, wallTex);
            gableEnd(x1, z1, x1, z0, top, x1, yr, zc, wallTex);
        } else {
            float xc = (x0 + x1) * 0.5f, za = z0 - ov, zb = z1 + ov;
            quad(p, x0 - ov, ye, za, x0 - ov, ye, zb, xc, yr, zb, xc, yr, za);           // west slope
            uvs(t, (zb - za) * 0.5f, slopeLen * 0.5f);
            light(l, -0.6f, 0.75f, 0);
            gb.quad(p, t, l, roofTex, L_OBJECT, 0, -0.2f, false);
            quad(p, x1 + ov, ye, zb, x1 + ov, ye, za, xc, yr, za, xc, yr, zb);           // east slope
            light(l, 0.6f, 0.75f, 0);
            gb.quad(p, t, l, roofTex, L_OBJECT, 0, -0.2f, false);
            gableEnd(x1, z0, x0, z0, top, xc, yr, z0, wallTex);
            gableEnd(x0, z1, x1, z1, top, xc, yr, z1, wallTex);
        }
    }

    private static void quad(float[] p, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        p[0] = ax; p[1] = ay; p[2] = az; p[3] = bx; p[4] = by; p[5] = bz;
        p[6] = cx; p[7] = cy; p[8] = cz; p[9] = dx; p[10] = dy; p[11] = dz;
    }

    private static void uvs(float[] t, float u, float v) {
        t[0] = 0; t[1] = v; t[2] = u; t[3] = v; t[4] = u; t[5] = 0; t[6] = 0; t[7] = 0;
    }

    private static void light(float[] l, float nx, float ny, float nz) {
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        l[0] = l[1] = l[2] = l[3] = GeoBuilder.sunLight(nx / len, ny / len, nz / len);
    }

    /** Triangle closing the roof at one end; A->B along the wall top (outward winding), apex at the ridge. */
    private void gableEnd(float ax, float az, float bx, float bz, float y, float px, float py, float pz, int tex) {
        float[] p = {ax, y, az, bx, y, bz, px, py, pz};
        float len = (float) Math.hypot(bx - ax, bz - az);
        float[] t = {0, 0.4f, len * 0.5f, 0.4f, len * 0.25f, 0.4f - (py - y) / 2.8f};
        float nx = -(bz - az), nz = bx - ax, nl = (float) Math.sqrt(nx * nx + nz * nz);
        float lt = GeoBuilder.sunLight(nx / nl, 0, nz / nl);
        gb.quad3(p, t, new float[]{lt, lt, lt}, tex, L_OBJECT);
    }

    // ------------------------------------------------------------------ civic

    private void fireStation(float x0, float z0, float x1, float z1, float base, boolean[] street) {
        int fac = pick(Kind.FIRE);
        float groundH = 5f, top = base + groundH + 2 * 3.4f;
        for (int s = 0; s < 4; s++) {
            float[] e = edge(s, x0, z0, x1, z1);
            if (street[s] && (s == N || s == S)) {
                gb.wall(e[0], e[1], e[2], e[3], base, base + groundH, lib.fireDoors[r.nextInt(2)], 0f, 1f, 0f, 1f, L_OBJECT, 0f, 0.85f);
                gb.wallGrid(e[0], e[1], e[2], e[3], base + groundH, top, fac, 0.5f, 1f / 3.4f, 8f, 6.8f, L_OBJECT, 0f, false);
            } else {
                gb.wallGrid(e[0], e[1], e[2], e[3], base, top, fac, 0.5f, 1f / 3.4f, 8f, 6.8f, L_OBJECT, 0f, true);
            }
        }
        roof(x0, z0, x1, z1, top, 0.8f, lib.concrete);
        // hose-drying tower with red top
        float tx = x1 - 3.2f, tz = z0 + 0.2f;
        gb.box(tx, top, tz, tx + 3f, top + 9f, tz + 3f, fac, lib.concrete, 0.5f, 1f / 3.4f, L_OBJECT, -0.3f, 0);
        gb.box(tx - 0.2f, top + 9f, tz - 0.2f, tx + 3.2f, top + 10.5f, tz + 3.2f, lib.paintRed, lib.paintRed, 1f, 1f, L_OBJECT, -0.4f, 0);
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + 0.8f, z1));
    }

    private void koban(float x0, float z0, float x1, float z1, float base, boolean[] street) {
        float top = base + 3.6f;
        for (int s = 0; s < 4; s++) {
            float[] e = edge(s, x0, z0, x1, z1);
            int tex = street[s] ? lib.koban[r.nextInt(2)] : pick(Kind.BLANK);
            if (street[s]) gb.wall(e[0], e[1], e[2], e[3], base, top, tex, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 0.9f);
            else gb.wallGrid(e[0], e[1], e[2], e[3], base, top, tex, 0.5f, 1f / 3.6f, 8f, 3.6f, L_OBJECT, 0f, true);
        }
        gable(x0, z0, x1, z1, top, pick(Kind.BLANK), lib.roofs[2]);
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + 1f, z1));
    }
}
