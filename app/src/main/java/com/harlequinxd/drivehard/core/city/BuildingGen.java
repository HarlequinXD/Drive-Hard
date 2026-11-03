package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.Sectors;
import com.harlequinxd.drivehard.core.tex.JpTextures.Kind;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Builds Japanese buildings inside a footprint: office towers, department stores, multi-tenant
 * bar buildings (with vertical signboards), apartment mansions, shophouses, convenience stores,
 * pachinko parlours, detached houses with tiled gable roofs, small apartments, fire stations,
 * police boxes and warehouses. Buildings are sectors (see {@link SectorBuilder}): facades are
 * walls tiled per storey with facade, shopfront and lobby tiles, roofs are flat decks inside a
 * parapet or pitched sectors; only signs, masts and similar props are polygons.
 */
final class BuildingGen {
    static final int N = 0, S = 1, W = 2, E = 3;
    enum Type { TOWER, DEPT_STORE, ZAKKYO, MANSION, SHOPHOUSE, KONBINI, PACHINKO, HOUSE, APAATO, FIRE_STATION, KOBAN, WAREHOUSE,
        FARMHOUSE, SHED, GREENHOUSE, TOWN_HALL, POST_OFFICE, CLINIC, SUPERMARKET, SCHOOL, SHRINE_HALL }

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
            case HOUSE: houseDesign(false, r.nextInt(HouseGen.SUBURB_DESIGNS), x0, z0, x1, z1, base, street, false); break;
            case APAATO: block(x0, z0, x1, z1, base, street, new boolean[4], shopSet, Kind.CORRIDOR, Kind.HOUSE, 2, 2.9f, false, 0); break;
            case FIRE_STATION: fireStation(x0, z0, x1, z1, base, street); break;
            case KOBAN: koban(x0, z0, x1, z1, base, street); break;
            case FARMHOUSE: houseDesign(true, r.nextInt(HouseGen.FARM_DESIGNS), x0, z0, x1, z1, base, street, false); break;
            case SHED: shed(x0, z0, x1, z1, base); break;
            case GREENHOUSE: greenhouse(x0, z0, x1, z1, base); break;
            case TOWN_HALL: block(x0, z0, x1, z1, base, street, street, lib.lobbies, Kind.OFFICE_GRID, Kind.BLANK, 3, 3.6f, false, 0); break;
            case POST_OFFICE: block(x0, z0, x1, z1, base, street, street, shopSet, Kind.BLANK, Kind.BLANK, 2, 3.2f, false, 0); break;
            case CLINIC: block(x0, z0, x1, z1, base, street, new boolean[4], shopSet, Kind.SHOPHOUSE, Kind.BLANK, 2, 3.2f, false, 0); break;
            case SUPERMARKET: block(x0, z0, x1, z1, base, street, street, shopSet, Kind.BLANK, Kind.BLANK, 1, 3.6f, false, 0); break;
            case SCHOOL: school(x0, z0, x1, z1, base); break;
            case SHRINE_HALL: shrineHall(x0, z0, x1, z1, base); break;
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
        sb = new SectorBuilder(ch.sectors);
        sb.world();
        return base;
    }

    /** Sector builder of the building being built (world frame). */
    private SectorBuilder sb;

    static float[] edge(int side, float x0, float z0, float x1, float z1) {
        switch (side) {
            case N: return new float[]{x1, z0, x0, z0};
            case S: return new float[]{x0, z1, x1, z1};
            case W: return new float[]{x0, z0, x0, z1};
            default: return new float[]{x1, z1, x1, z0};
        }
    }

    // ------------------------------------------------------------------ tiles

    /**
     * Tiles of a facade: the ground storey (shopfronts, a lobby, or the facade) if groundH &gt; 0,
     * then {@code storeys} storeys of facade tiles 2 m wide; above the top storey plain concrete
     * (the parapet), the foundation below the base. {@code ground}: a texture, -1 shops, -2 lobby.
     */
    private Sectors.Tiles facade(float len, float groundH, int storeys, float floorH, int tex, int ground, int[] shopSet) {
        boolean hasGround = groundH > 0f;
        int floors = Math.max(0, storeys) + (hasGround ? 1 : 0);
        Sectors.Tiles t = new Sectors.Tiles(floors);
        t.mat = t.matUp = lib.concrete;
        t.found = lib.concrete;
        t.groundH = hasGround ? groundH : floorH;
        t.storeyH = floorH;
        int cols = Math.max(1, Math.round(len / 2f));
        for (int f = 0; f < floors; f++) t.storey(f, cols, Sectors.FULL, tex);
        if (hasGround) {
            if (ground == -1 || ground == -2) {
                int units = Math.max(1, Math.round(len / 5.5f));
                t.storey(0, units, Sectors.FULL, 0);
                t.litGround = true;
                for (int u = 0; u < units; u++)
                    t.tex[0][u] = ground == -2 ? (u == units / 2 ? lib.lobbies[r.nextInt(lib.lobbies.length)] : lib.facades(Kind.GLASS)[0])
                            : shopSet[r.nextInt(shopSet.length)];
            } else t.storey(0, cols, Sectors.FULL, ground);
        }
        return t;
    }

    /** One tile filling the ground storey (fire station doors, police box front), facade above. */
    private Sectors.Tiles single(float groundH, int ground, int floors, float floorH, int tex, float len) {
        Sectors.Tiles t = new Sectors.Tiles(floors);
        t.mat = t.matUp = lib.concrete;
        t.found = lib.concrete;
        t.groundH = groundH;
        t.storeyH = floorH;
        t.storey(0, 1, Sectors.FULL, ground);
        for (int f = 1; f < floors; f++) t.storey(f, Math.max(1, Math.round(len / 2f)), Sectors.FULL, tex);
        return t;
    }

    private SectorBuilder.Face[] faces(float x0, float z0, float x1, float z1, Sectors.Tiles[] t) {
        SectorBuilder.Face[] f = new SectorBuilder.Face[4];
        for (int s = 0; s < 4; s++) {
            float[] e = edge(s, x0, z0, x1, z1);
            f[s] = sb.face(e[0], e[1], e[2], e[3], t[s]);
        }
        return f;
    }

    private static float len(int s, float x0, float z0, float x1, float z1) {
        return s == N || s == S ? x1 - x0 : z1 - z0;
    }

    /** Flat roof: a deck at {@code top} inside a parapet ring {@code parapet} high (none if 0). */
    private void flatRoof(float x0, float z0, float x1, float z1, float base, float top, float parapet, int roofTex,
                          SectorBuilder.Face[] f) {
        Sectors.Tiles cap = SectorBuilder.plain(lib.concrete, lib.concrete);
        if (parapet <= 0f) {
            sb.piece(new float[]{x0, z0, x1, z0, x1, z1, x0, z1}, top, 0f, 0f, base, roofTex, 2f, -1f, f, cap);
        } else {
            float t = 0.3f, h = top + parapet;
            sb.piece(new float[]{x0 + t, z0 + t, x1 - t, z0 + t, x1 - t, z1 - t, x0 + t, z1 - t}, top, 0f, 0f, base, roofTex, 2f, -1f, f, cap);
            sb.piece(new float[]{x0, z0, x1, z0, x1, z0 + t, x0, z0 + t}, h, 0f, 0f, base, lib.concrete, 2f, -1f, f, cap);
            sb.piece(new float[]{x0, z1 - t, x1, z1 - t, x1, z1, x0, z1}, h, 0f, 0f, base, lib.concrete, 2f, -1f, f, cap);
            sb.piece(new float[]{x0, z0 + t, x0 + t, z0 + t, x0 + t, z1 - t, x0, z1 - t}, h, 0f, 0f, base, lib.concrete, 2f, -1f, f, cap);
            sb.piece(new float[]{x1 - t, z0 + t, x1, z0 + t, x1, z1 - t, x1 - t, z1 - t}, h, 0f, 0f, base, lib.concrete, 2f, -1f, f, cap);
        }
        sb.taken(x0, z0, x1, z1);
    }

    /** Pitched roof with gable ends, ridge along the longer side: two sloped sectors. */
    private void gableRoof(float x0, float z0, float x1, float z1, float base, float top, float pitch, int roofTex,
                           SectorBuilder.Face[] f, Sectors.Tiles inner) {
        if (x1 - x0 >= z1 - z0) {
            float zc = (z0 + z1) * 0.5f;
            sb.piece(new float[]{x0, z0, x1, z0, x1, zc, x0, zc}, top - pitch * z0, 0f, pitch, base, roofTex, 2f, -1f, f, inner);
            sb.piece(new float[]{x0, zc, x1, zc, x1, z1, x0, z1}, top + pitch * z1, 0f, -pitch, base, roofTex, 2f, -1f, f, inner);
        } else {
            float xc = (x0 + x1) * 0.5f;
            sb.piece(new float[]{x0, z0, xc, z0, xc, z1, x0, z1}, top - pitch * x0, pitch, 0f, base, roofTex, 2f, -1f, f, inner);
            sb.piece(new float[]{xc, z0, x1, z0, x1, z1, xc, z1}, top + pitch * x1, -pitch, 0f, base, roofTex, 2f, -1f, f, inner);
        }
        sb.taken(x0, z0, x1, z1);
    }

    /** A plain box standing on whatever is below it (roof plant, stair housings, tanks). */
    private void plantBox(float x0, float z0, float x1, float z1, float y0, float y1, int side, int top) {
        Sectors.Tiles t = SectorBuilder.plain(side, side);
        t.matScale = 1.5f;
        SectorBuilder.Face[] f = faces(x0, z0, x1, z1, new Sectors.Tiles[]{t, t, t, t});
        sb.piece(new float[]{x0, z0, x1, z0, x1, z1, x0, z1}, y1, 0f, 0f, y0, top, 2f, -1f, f, t);
        sb.taken(x0, z0, x1, z1);
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
        Sectors.Tiles[] t = new Sectors.Tiles[4];
        for (int s = 0; s < 4; s++) {
            int tex = street[s] ? fTex : (s == N || s == S ? bTex : side);
            t[s] = facade(len(s, x0, z0, x1, z1), groundH, floors - 1, floorH, tex, shops[s] ? -1 : tex, shopSet);
            if (street[s] && signs > 0 && floors > 1) verticalSigns(s, x0, z0, x1, z1, base + groundH + 0.6f, top, signs);
        }
        rooftop(x0, z0, x1, z1, top);
        flatRoof(x0, z0, x1, z1, base, top, parapet, r.nextBoolean() ? lib.roofMembrane : lib.concrete, faces(x0, z0, x1, z1, t));
        if (bigSign) rooftopSign(x0, z0, x1, z1, top + parapet, street);
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + parapet, z1));
    }

    private void tower(float x0, float z0, float x1, float z1, float base, boolean[] street, boolean[] shops, int[] shopSet) {
        int pod = pick(Kind.OFFICE_GRID);
        int podFloors = 2 + r.nextInt(3);
        float podTop = base + City.SHOP_H + (podFloors - 1) * City.FLOOR_H;
        float in = 3f + r.nextInt(3);
        float tx0 = x0 + in, tz0 = z0 + in, tx1 = x1 - in, tz1 = z1 - in;
        if (tx1 - tx0 >= 10f && tz1 - tz0 >= 10f) {
            // the tower first: the podium roof is cut round it
            int tower = pick(r.nextBoolean() ? Kind.GLASS : Kind.OFFICE_GRID);
            int floors = 12 + r.nextInt(28);
            float top = podTop + floors * 3.6f;
            Sectors.Tiles[] t = new Sectors.Tiles[4];
            for (int s = 0; s < 4; s++) t[s] = facade(len(s, tx0, tz0, tx1, tz1), 0f, floors, 3.6f, tower, tower, shopSet);
            rooftop(tx0, tz0, tx1, tz1, top);
            flatRoof(tx0, tz0, tx1, tz1, podTop, top, 1.2f, lib.roofMembrane, faces(tx0, tz0, tx1, tz1, t));
            // aviation light mast
            gb.box((tx0 + tx1) * 0.5f - 0.15f, top, (tz0 + tz1) * 0.5f - 0.15f, (tx0 + tx1) * 0.5f + 0.15f, top + 6f,
                    (tz0 + tz1) * 0.5f + 0.15f, lib.pole, -1, 1f, 1f, L_OBJECT, -0.2f, Geo.FLAG_DETAIL);
            ch.boxes.add(new Box(tx0, footMin, tz0, tx1, top + 1.2f, tz1));
        }
        Sectors.Tiles[] t = new Sectors.Tiles[4];
        for (int s = 0; s < 4; s++)
            t[s] = facade(len(s, x0, z0, x1, z1), City.SHOP_H, podFloors - 1, City.FLOOR_H, pod, shops[s] ? -1 : (street[s] ? -2 : pod), shopSet);
        flatRoof(x0, z0, x1, z1, base, podTop, 0.9f, lib.roofGravel, faces(x0, z0, x1, z1, t));
        ch.boxes.add(new Box(x0, footMin, z0, x1, podTop + 0.9f, z1));
    }

    /** Roof plant: a/c units, and on larger roofs a stair housing with a water tank on it. */
    private void rooftop(float x0, float z0, float x1, float z1, float top) {
        float w = x1 - x0, d = z1 - z0;
        boolean housing = w > 8f && d > 8f && r.nextBoolean();
        float hx = x0 + w * 0.5f - 1.5f, hz = z0 + d * 0.5f - 1.5f;
        if (housing) {
            if (r.nextBoolean()) plantBox(hx + 0.5f, hz + 0.5f, hx + 2.5f, hz + 2.5f, top + 3f, top + 4.6f, lib.metal, lib.metal);
            plantBox(hx, hz, hx + 3f, hz + 3f, top, top + 3f, lib.concrete, lib.roofTar);
        }
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            float ax = x0 + 1.2f + r.nextFloat() * Math.max(0.1f, w - 4f), az = z0 + 1.2f + r.nextFloat() * Math.max(0.1f, d - 3f);
            if (ax + 1.6f > x1 - 0.5f || az + 1f > z1 - 0.5f) continue;
            if (housing && ax < hx + 3.2f && ax + 1.6f > hx - 0.2f && az < hz + 3.2f && az + 1f > hz - 0.2f) continue;
            plantBox(ax, az, ax + 1.6f, az + 1f, top, top + 1.1f, lib.metal, lib.metal);
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

    /** A house from the procedural generator, front toward the first street side. */
    private void houseDesign(boolean farm, int design, float x0, float z0, float x1, float z1, float base, boolean[] street, boolean lot) {
        int face = N;
        for (int s = 0; s < 4; s++) if (street[s]) { face = s; break; }
        new HouseGen(lib, gb, ch, r).build(HouseGen.design(lib, farm, design), x0, z0, x1, z1, face, base, lot);
    }

    /** A house design on a whole lot (garden, carport, fence), for village and town streets. */
    void lotHouse(boolean farm, int design, float x0, float z0, float x1, float z1, int face) {
        gb.begin(true);
        float base = foundationLot(x0, z0, x1, z1);
        new HouseGen(lib, gb, ch, r).build(HouseGen.design(lib, farm, design), x0, z0, x1, z1, face, base, true);
        gb.end();
    }

    /** Base height for a lot: its highest corner (the house stands on a low plinth). */
    private float foundationLot(float x0, float z0, float x1, float z1) {
        Terrain t = city.terrain;
        float base = Math.max(Math.max(t.height(x0, z0), t.height(x1, z0)), Math.max(t.height(x0, z1), t.height(x1, z1)));
        footMin = Math.min(Math.min(t.height(x0, z0), t.height(x1, z0)), Math.min(t.height(x0, z1), t.height(x1, z1))) - 0.3f;
        return base + 0.15f;
    }

    /** Timber barn / tool shed with a corrugated metal roof. */
    private void shed(float x0, float z0, float x1, float z1, float base) {
        int wall = pick(Kind.HOUSE_WOOD);
        float top = base + 3.3f;
        Sectors.Tiles[] t = new Sectors.Tiles[4];
        for (int s = 0; s < 4; s++) {
            t[s] = facade(len(s, x0, z0, x1, z1), 0f, 1, 3.3f, wall, wall, null);
            t[s].mat = t[s].matUp = wall;
        }
        gableRoof(x0, z0, x1, z1, base, top, 0.55f, lib.metal, faces(x0, z0, x1, z1, t), SectorBuilder.plain(wall, lib.concrete));
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + 1f, z1));
    }

    /** Vinyl greenhouse: low milky walls under a pitched plastic roof. */
    private void greenhouse(float x0, float z0, float x1, float z1, float base) {
        float top = base + 1.9f;
        Sectors.Tiles v = SectorBuilder.plain(lib.vinyl, lib.concrete);
        gableRoof(x0, z0, x1, z1, base, top, 0.55f, lib.vinyl, faces(x0, z0, x1, z1, new Sectors.Tiles[]{v, v, v, v}), v);
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + 1f, z1));
    }

    // ------------------------------------------------------------------ civic

    /** Three storeys of classrooms with long window bands all round, flat roof. */
    private void school(float x0, float z0, float x1, float z1, float base) {
        float top = base + 3 * 3.6f;
        Sectors.Tiles[] t = new Sectors.Tiles[4];
        for (int s = 0; s < 4; s++) t[s] = facade(len(s, x0, z0, x1, z1), 0f, 3, 3.6f, lib.schoolWall, lib.schoolWall, null);
        rooftop(x0, z0, x1, z1, top);
        flatRoof(x0, z0, x1, z1, base, top, 0.8f, lib.concrete, faces(x0, z0, x1, z1, t));
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + 0.8f, z1));
    }

    /** Shrine hall: dark timber on a raised stone floor under a deep copper or tiled roof. */
    private void shrineHall(float x0, float z0, float x1, float z1, float base) {
        int wall = pick(Kind.HOUSE_WOOD);
        float floor = base + 0.6f, top = floor + 3.2f;
        Sectors.Tiles[] t = new Sectors.Tiles[4];
        for (int s = 0; s < 4; s++) {
            t[s] = facade(len(s, x0, z0, x1, z1), 0f, 1, 3.2f, wall, wall, null);
            t[s].mat = t[s].matUp = wall;
        }
        int roofTex = lib.houseRoof[lib.houseRoof.length - 1];
        for (int i = 0; i < lib.houseRoofKind.length; i++)
            if (lib.houseRoofKind[i] == com.harlequinxd.drivehard.core.tex.HouseTextures.Roof.KAWARA && r.nextBoolean()) { roofTex = lib.houseRoof[i]; break; }
        gableRoof(x0, z0, x1, z1, floor, top, 0.6f, roofTex, faces(x0, z0, x1, z1, t), SectorBuilder.plain(wall, lib.concrete));
        // the platform round it
        Sectors.Tiles pl = SectorBuilder.plain(lib.concrete, lib.concrete);
        float px0 = x0 - 0.8f, pz0 = z0 - 0.8f, px1 = x1 + 0.8f, pz1 = z1 + 0.8f;
        sb.piece(new float[]{px0, pz0, px1, pz0, px1, pz1, px0, pz1}, floor, 0f, 0f, base - 0.4f, lib.planter, 2f, -1f,
                faces(px0, pz0, px1, pz1, new Sectors.Tiles[]{pl, pl, pl, pl}), pl);
        sb.taken(px0, pz0, px1, pz1);
        ch.boxes.add(new Box(px0, footMin, pz0, px1, top + 2f, pz1));
    }

    private void fireStation(float x0, float z0, float x1, float z1, float base, boolean[] street) {
        int fac = pick(Kind.FIRE);
        float groundH = 5f, top = base + groundH + 2 * 3.4f;
        // hose-drying tower with a red top, standing on the roof
        float tx = x1 - 3.4f, tz = z0 + 0.5f;
        plantBox(tx - 0.2f, tz - 0.2f, tx + 3.2f, tz + 3.2f, top + 9f, top + 10.5f, lib.paintRed, lib.paintRed);
        Sectors.Tiles tw = facade(3f, 0f, 3, 3.4f, fac, fac, null);
        sb.piece(new float[]{tx, tz, tx + 3f, tz, tx + 3f, tz + 3f, tx, tz + 3f}, top + 9f, 0f, 0f, top, lib.concrete, 2f, -1f,
                faces(tx, tz, tx + 3f, tz + 3f, new Sectors.Tiles[]{tw, tw, tw, tw}), tw);
        sb.taken(tx, tz, tx + 3f, tz + 3f);
        Sectors.Tiles[] t = new Sectors.Tiles[4];
        for (int s = 0; s < 4; s++) {
            float l = len(s, x0, z0, x1, z1);
            t[s] = street[s] && (s == N || s == S) ? single(groundH, lib.fireDoors[r.nextInt(2)], 3, 3.4f, fac, l)
                    : facade(l, groundH, 2, 3.4f, fac, fac, null);
        }
        flatRoof(x0, z0, x1, z1, base, top, 0.8f, lib.concrete, faces(x0, z0, x1, z1, t));
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + 0.8f, z1));
    }

    private void koban(float x0, float z0, float x1, float z1, float base, boolean[] street) {
        float top = base + 3.6f;
        int blank = pick(Kind.BLANK);
        Sectors.Tiles[] t = new Sectors.Tiles[4];
        for (int s = 0; s < 4; s++) {
            float l = len(s, x0, z0, x1, z1);
            t[s] = street[s] ? single(3.6f, lib.koban[r.nextInt(2)], 1, 3.6f, blank, l) : facade(l, 0f, 1, 3.6f, blank, blank, null);
        }
        gableRoof(x0, z0, x1, z1, base, top, 0.45f, lib.roofs[2], faces(x0, z0, x1, z1, t), SectorBuilder.plain(lib.concrete, lib.concrete));
        ch.boxes.add(new Box(x0, footMin, z0, x1, top + 1f, z1));
    }
}
