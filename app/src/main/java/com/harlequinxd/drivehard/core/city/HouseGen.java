package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.Sectors;
import com.harlequinxd.drivehard.core.tex.HouseTextures;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Procedural house generator.
 *
 * <p>A {@link Design} is a set of parameters drawn from a seed: plan (rectangle, L with a wing in
 * front or behind, two offset blocks, main house with a one-storey annex, upper floor over part of
 * the ground floor), storeys, roof (gable, hip, hip-and-gable irimoya, mono-pitch, flat with a
 * parapet), pitch and overhang, wall materials (one or two-tone), roof covering, window and door
 * styles, balcony, porch canopy, carport and front fence. There are {@value #SUBURB_DESIGNS}
 * suburban designs and {@value #FARM_DESIGNS} farmhouse designs; any lot picks one by hash.</p>
 *
 * <p>Houses are not polygons: each is a set of sectors built from the ground up by
 * {@link SectorBuilder}. A mass's walls are tile faces (per storey: plain wall, window, door,
 * garden window) and its roof is made of sloped sectors: two for a gable, four for a hip, a
 * hipped lower part with a gable wall above for irimoya, one for a mono-pitch, a deck inside
 * a parapet ring for a flat roof. Gable ends and parapets are plain wall material.</p>
 */
final class HouseGen {
    static final int SUBURB_DESIGNS = 250, FARM_DESIGNS = 150;

    enum RoofType { GABLE, HIP, IRIMOYA, SHED, FLAT }

    enum Plan { RECT, L_FRONT, L_BACK, OFFSET, ANNEX, UPPER_PART }

    /** Parameters of one house design (texture choices are indices into the library's house sets). */
    static final class Design {
        int id;
        boolean farm;
        Plan plan;
        int floors;
        RoofType roof;
        boolean ridgeAlongFront;
        float pitch, overhang, floorH;
        int mat1, mat2, trim, roofTex;
        int win, winSmall, winGarden, door;
        boolean balcony, carport, porch, hisashi, leftDoor;
        int fence;               // 0 none, 1 block wall, 2 hedge
        float widthFrac, depthFrac, winW, winH, bay, wingFrac, offset;
        boolean wingLower, shedBack;
    }

    private static Design[] suburb, farmDesigns;
    private static TextureLibrary designLib;

    static synchronized Design design(TextureLibrary lib, boolean farm, int id) {
        if (designLib != lib) {
            designLib = lib;
            suburb = new Design[SUBURB_DESIGNS];
            farmDesigns = new Design[FARM_DESIGNS];
            for (int i = 0; i < SUBURB_DESIGNS; i++) suburb[i] = makeSuburb(lib, i);
            for (int i = 0; i < FARM_DESIGNS; i++) farmDesigns[i] = makeFarm(lib, i);
        }
        return farm ? farmDesigns[Math.floorMod(id, FARM_DESIGNS)] : suburb[Math.floorMod(id, SUBURB_DESIGNS)];
    }

    private static int pickMat(TextureLibrary lib, Random r, HouseTextures.Material... kinds) {
        for (int k = 0; k < 40; k++) {
            int i = r.nextInt(lib.houseMat.length);
            for (HouseTextures.Material m : kinds) if (lib.houseMatKind[i] == m) return i;
        }
        return 0;
    }

    private static int pickWin(TextureLibrary lib, Random r, HouseTextures.Window... kinds) {
        for (int k = 0; k < 60; k++) {
            int i = r.nextInt(lib.houseWin.length);
            for (HouseTextures.Window w : kinds) if (lib.houseWinKind[i] == w) return i;
        }
        return 0;
    }

    private static int pickDoor(TextureLibrary lib, Random r, HouseTextures.Door... kinds) {
        for (int k = 0; k < 60; k++) {
            int i = r.nextInt(lib.houseDoor.length);
            for (HouseTextures.Door d : kinds) if (lib.houseDoorKind[i] == d) return i;
        }
        return 0;
    }

    private static int pickRoof(TextureLibrary lib, Random r, HouseTextures.Roof... kinds) {
        for (int k = 0; k < 60; k++) {
            int i = r.nextInt(lib.houseRoof.length);
            for (HouseTextures.Roof d : kinds) if (lib.houseRoofKind[i] == d) return i;
        }
        return 0;
    }

    private static Design makeSuburb(TextureLibrary lib, int id) {
        Random r = new Random(id * 7919L + 104729L);
        Design d = new Design();
        d.id = id;
        float s = r.nextFloat();
        int style = s < 0.16f ? 0 : (s < 0.48f ? 1 : (s < 0.66f ? 2 : (s < 0.84f ? 3 : 4)));  // box, gable, hip, shed, traditional
        float p = r.nextFloat();
        d.plan = p < 0.34f ? Plan.RECT : p < 0.52f ? Plan.L_FRONT : p < 0.64f ? Plan.L_BACK : p < 0.78f ? Plan.OFFSET : p < 0.9f ? Plan.ANNEX : Plan.UPPER_PART;
        d.floors = r.nextFloat() < 0.14f ? 1 : 2;
        d.floorH = 2.75f + r.nextFloat() * 0.3f;
        d.ridgeAlongFront = r.nextFloat() < 0.75f;
        d.overhang = 0.3f + r.nextFloat() * 0.35f;
        HouseTextures.Material[] walls;
        switch (style) {
            case 0:
                d.roof = RoofType.FLAT;
                d.floors = r.nextFloat() < 0.3f ? 3 : 2;
                d.overhang = 0f;
                walls = new HouseTextures.Material[]{HouseTextures.Material.PANEL, HouseTextures.Material.TILE, HouseTextures.Material.STUCCO};
                break;
            case 1:
                d.roof = RoofType.GABLE;
                d.pitch = 0.42f + r.nextFloat() * 0.25f;
                walls = new HouseTextures.Material[]{HouseTextures.Material.SIDING, HouseTextures.Material.STUCCO, HouseTextures.Material.BRICK, HouseTextures.Material.TILE};
                break;
            case 2:
                d.roof = RoofType.HIP;
                d.pitch = 0.38f + r.nextFloat() * 0.18f;
                walls = new HouseTextures.Material[]{HouseTextures.Material.SIDING, HouseTextures.Material.STUCCO, HouseTextures.Material.TILE};
                break;
            case 3:
                d.roof = RoofType.SHED;
                d.pitch = 0.18f + r.nextFloat() * 0.16f;
                d.shedBack = r.nextBoolean();
                walls = new HouseTextures.Material[]{HouseTextures.Material.PANEL, HouseTextures.Material.SIDING, HouseTextures.Material.BOARDS, HouseTextures.Material.CHARRED};
                break;
            default:
                d.roof = r.nextBoolean() ? RoofType.IRIMOYA : RoofType.GABLE;
                d.pitch = 0.5f + r.nextFloat() * 0.2f;
                walls = new HouseTextures.Material[]{HouseTextures.Material.PLASTER, HouseTextures.Material.BOARDS, HouseTextures.Material.CHARRED};
                break;
        }
        d.mat1 = pickMat(lib, r, walls);
        d.mat2 = r.nextFloat() < 0.35f ? pickMat(lib, r, walls) : d.mat1;
        d.trim = pickMat(lib, r, HouseTextures.Material.PANEL, HouseTextures.Material.CHARRED);
        if (style == 4) d.roofTex = pickRoof(lib, r, HouseTextures.Roof.KAWARA);
        else if (style == 3) d.roofTex = pickRoof(lib, r, HouseTextures.Roof.METAL);
        else {
            float q = r.nextFloat();
            d.roofTex = q < 0.45f ? pickRoof(lib, r, HouseTextures.Roof.KAWARA) : (q < 0.8f ? pickRoof(lib, r, HouseTextures.Roof.SLATE) : pickRoof(lib, r, HouseTextures.Roof.METAL, HouseTextures.Roof.COPPER));
        }
        if (style == 4) {
            d.win = pickWin(lib, r, HouseTextures.Window.LATTICE, HouseTextures.Window.SHOJI);
            d.door = pickDoor(lib, r, HouseTextures.Door.SLIDING_LATTICE, HouseTextures.Door.SLIDING_GLASS);
        } else {
            d.win = style == 0 ? pickWin(lib, r, HouseTextures.Window.CASEMENT, HouseTextures.Window.SLIDING)
                    : pickWin(lib, r, HouseTextures.Window.SLIDING, HouseTextures.Window.SHUTTER_BOX, HouseTextures.Window.CASEMENT);
            d.door = pickDoor(lib, r, HouseTextures.Door.WOOD, HouseTextures.Door.METAL, HouseTextures.Door.DARK);
        }
        d.winSmall = pickWin(lib, r, style == 0 ? HouseTextures.Window.SLIT : HouseTextures.Window.FROSTED, HouseTextures.Window.FROSTED);
        d.winGarden = pickWin(lib, r, HouseTextures.Window.GARDEN);
        d.balcony = d.floors >= 2 && style != 4 && r.nextFloat() < 0.45f;
        d.porch = r.nextFloat() < 0.55f;
        d.carport = r.nextFloat() < 0.4f;
        float f = r.nextFloat();
        d.fence = f < 0.4f ? 1 : (f < 0.65f ? 2 : 0);
        d.widthFrac = 0.66f + r.nextFloat() * 0.22f;
        d.depthFrac = 0.58f + r.nextFloat() * 0.2f;
        d.winW = 1.2f + r.nextFloat() * 0.6f;
        d.winH = 1.1f + r.nextFloat() * 0.25f;
        d.bay = 2.5f + r.nextFloat() * 0.9f;
        d.wingFrac = 0.38f + r.nextFloat() * 0.17f;
        d.wingLower = r.nextFloat() < 0.6f;
        d.offset = 1.2f + r.nextFloat() * 1.6f;
        d.leftDoor = r.nextBoolean();
        return d;
    }

    private static Design makeFarm(TextureLibrary lib, int id) {
        Random r = new Random(id * 6151L + 7741L);
        Design d = new Design();
        d.id = id;
        d.farm = true;
        boolean modern = r.nextFloat() < 0.3f;
        float p = r.nextFloat();
        d.plan = p < 0.45f ? Plan.RECT : p < 0.65f ? Plan.L_BACK : p < 0.8f ? Plan.L_FRONT : Plan.ANNEX;
        d.floors = modern ? 2 : (r.nextFloat() < 0.5f ? 1 : 2);
        d.floorH = 2.85f + r.nextFloat() * 0.3f;
        d.ridgeAlongFront = true;
        d.overhang = 0.5f + r.nextFloat() * 0.35f;
        if (modern) {
            d.roof = r.nextBoolean() ? RoofType.GABLE : RoofType.HIP;
            d.pitch = 0.42f + r.nextFloat() * 0.2f;
            d.mat1 = pickMat(lib, r, HouseTextures.Material.SIDING, HouseTextures.Material.STUCCO);
            d.mat2 = d.mat1;
            d.roofTex = pickRoof(lib, r, HouseTextures.Roof.SLATE, HouseTextures.Roof.METAL, HouseTextures.Roof.KAWARA);
            d.win = pickWin(lib, r, HouseTextures.Window.SLIDING, HouseTextures.Window.SHUTTER_BOX);
            d.door = pickDoor(lib, r, HouseTextures.Door.WOOD, HouseTextures.Door.SLIDING_GLASS);
        } else {
            float q = r.nextFloat();
            d.roof = q < 0.5f ? RoofType.IRIMOYA : (q < 0.8f ? RoofType.GABLE : RoofType.HIP);
            d.pitch = 0.5f + r.nextFloat() * 0.25f;
            d.mat1 = pickMat(lib, r, HouseTextures.Material.BOARDS, HouseTextures.Material.CHARRED, HouseTextures.Material.PLASTER);
            d.mat2 = r.nextFloat() < 0.6f ? pickMat(lib, r, HouseTextures.Material.PLASTER) : d.mat1;
            d.roofTex = pickRoof(lib, r, HouseTextures.Roof.KAWARA, HouseTextures.Roof.KAWARA, HouseTextures.Roof.COPPER);
            d.win = pickWin(lib, r, HouseTextures.Window.LATTICE, HouseTextures.Window.SHOJI, HouseTextures.Window.SLIDING);
            d.door = pickDoor(lib, r, HouseTextures.Door.SLIDING_LATTICE, HouseTextures.Door.SLIDING_GLASS);
            d.hisashi = r.nextFloat() < 0.7f;
        }
        d.trim = pickMat(lib, r, HouseTextures.Material.CHARRED, HouseTextures.Material.BOARDS);
        d.winSmall = pickWin(lib, r, HouseTextures.Window.FROSTED, HouseTextures.Window.LATTICE);
        d.winGarden = pickWin(lib, r, HouseTextures.Window.GARDEN);
        d.porch = modern && r.nextBoolean();
        d.widthFrac = 0.86f + r.nextFloat() * 0.14f;
        d.depthFrac = 0.86f + r.nextFloat() * 0.14f;
        d.winW = 1.4f + r.nextFloat() * 0.5f;
        d.winH = 1.1f + r.nextFloat() * 0.3f;
        d.bay = 2.6f + r.nextFloat() * 0.8f;
        d.wingFrac = 0.35f + r.nextFloat() * 0.15f;
        d.wingLower = r.nextFloat() < 0.7f;
        d.offset = 1.5f;
        d.leftDoor = r.nextBoolean();
        return d;
    }

    // ------------------------------------------------------------------ building

    private final TextureLibrary lib;
    private final GeoBuilder gb;
    private final Chunk ch;
    private final Random r;
    // lot frame: local (u along the frontage, v away from the road) to world
    private float lx0, lz0, lx1, lz1;
    private int face;

    HouseGen(TextureLibrary lib, GeoBuilder gb, Chunk ch, Random r) {
        this.lib = lib;
        this.gb = gb;
        this.ch = ch;
        this.r = r;
    }

    private float wx(float u, float v) {
        switch (face) {
            case 0: return lx0 + u;
            case 1: return lx1 - u;
            case 2: return lx0 + v;
            default: return lx1 - v;
        }
    }

    private float wz(float u, float v) {
        switch (face) {
            case 0: return lz0 + v;
            case 1: return lz1 - v;
            case 2: return lz1 - u;
            default: return lz0 + u;
        }
    }

    /** A block of the house: footprint, storeys and roof. */
    private static final class Mass {
        float u0, v0, u1, v1;
        int floors;
        RoofType roof;
        boolean ridgeU;          // ridge runs along u (parallel to the frontage)
        int shedHigh;            // mono-pitch: high side 0=v0 1=v1 2=u0 3=u1
        float top;

        Mass(float u0, float v0, float u1, float v1, int floors, RoofType roof, boolean ridgeU) {
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
            this.floors = floors; this.roof = roof; this.ridgeU = ridgeU;
        }

        boolean contains(float u, float v) { return u > u0 + 0.05f && u < u1 - 0.05f && v > v0 + 0.05f && v < v1 - 0.05f; }
    }

    /** Footprint of the house inside the lot (local u0, v0, u1, v1) and the carport's u range (or NaN). */
    static float[] layout(Design d, float W, float D, boolean lot) {
        if (!lot) return new float[]{0f, 0f, W, D, Float.NaN, Float.NaN};
        float w = Math.min(W - 2f, W * d.widthFrac), dd = Math.min(D - 4f, D * d.depthFrac);
        float carW = d.carport && W - w > 3.4f ? Math.min(3.2f, W - w - 0.6f) : 0f;
        float u0;
        if (carW > 0f) u0 = d.leftDoor ? W - w - 0.6f : 0.6f;
        else u0 = (W - w) * 0.5f;
        float v0 = Math.max(2.5f, (D - dd) * 0.4f);
        float cu0 = Float.NaN, cu1 = Float.NaN;
        if (carW > 0f) { cu0 = d.leftDoor ? 0.3f : W - carW - 0.3f; cu1 = cu0 + carW; }
        return new float[]{u0, v0, u0 + w, Math.min(D - 1f, v0 + dd), cu0, cu1};
    }

    /**
     * Builds design {@code d} on a lot (world rectangle) whose front faces {@code face}
     * (0=N, 1=S, 2=W, 3=E). With {@code lot} the house sits inside the lot with a garden, carport
     * and fence; otherwise it fills the rectangle.
     */
    void build(Design d, float x0, float z0, float x1, float z1, int face, float base, boolean lot) {
        lx0 = x0; lz0 = z0; lx1 = x1; lz1 = z1;
        this.face = face;
        float W = face < 2 ? x1 - x0 : z1 - z0, D = face < 2 ? z1 - z0 : x1 - x0;
        float[] lay = layout(d, W, D, lot);
        float u0 = lay[0], v0 = lay[1], u1 = lay[2], v1 = lay[3];
        float w = u1 - u0, dep = v1 - v0;
        List<Mass> masses = new ArrayList<>();
        boolean ridgeU = d.ridgeAlongFront ? w >= dep * 0.7f : w > dep * 1.4f;
        int wingFloors = d.wingLower ? Math.max(1, d.floors - 1) : d.floors;
        RoofType wingRoof = d.roof == RoofType.FLAT ? RoofType.FLAT : (d.roof == RoofType.SHED ? RoofType.SHED : (d.roof == RoofType.IRIMOYA ? RoofType.HIP : d.roof));
        switch (d.plan) {
            case L_FRONT: {
                float fd = Math.min(dep * 0.35f, 4.5f), ww = w * d.wingFrac;
                masses.add(new Mass(u0, v0 + fd, u1, v1, d.floors, d.roof, ridgeU));
                Mass wing = d.leftDoor ? new Mass(u1 - ww, v0, u1, v0 + fd + 2f, wingFloors, wingRoof, false)
                        : new Mass(u0, v0, u0 + ww, v0 + fd + 2f, wingFloors, wingRoof, false);
                wing.shedHigh = 1;
                masses.add(wing);
                break;
            }
            case L_BACK: {
                float bd = Math.min(dep * 0.4f, 5f), ww = w * d.wingFrac;
                masses.add(new Mass(u0, v0, u1, v1 - bd, d.floors, d.roof, ridgeU));
                Mass wing = d.leftDoor ? new Mass(u0, v1 - bd - 2f, u0 + ww, v1, wingFloors, wingRoof, false)
                        : new Mass(u1 - ww, v1 - bd - 2f, u1, v1, wingFloors, wingRoof, false);
                wing.shedHigh = 0;
                masses.add(wing);
                break;
            }
            case OFFSET: {
                float um = u0 + w * (0.45f + d.wingFrac * 0.3f), o = d.offset;
                masses.add(new Mass(u0, v0, um, v1 - o, d.floors, d.roof, false));
                Mass b = new Mass(um - 0.3f, v0 + o, u1, v1, wingFloors, d.roof, false);
                masses.add(b);
                break;
            }
            case ANNEX: {
                float aw = Math.min(w * 0.3f, 3.6f);
                boolean left = !d.leftDoor;
                masses.add(left ? new Mass(u0 + aw, v0, u1, v1, d.floors, d.roof, ridgeU) : new Mass(u0, v0, u1 - aw, v1, d.floors, d.roof, ridgeU));
                Mass a = left ? new Mass(u0, v0 + 1.2f, u0 + aw + 0.3f, v1 - 0.8f, 1, d.roof == RoofType.FLAT ? RoofType.FLAT : RoofType.SHED, false)
                        : new Mass(u1 - aw - 0.3f, v0 + 1.2f, u1, v1 - 0.8f, 1, d.roof == RoofType.FLAT ? RoofType.FLAT : RoofType.SHED, false);
                a.shedHigh = left ? 3 : 2;
                masses.add(a);
                break;
            }
            case UPPER_PART: {
                if (d.floors < 2) { masses.add(new Mass(u0, v0, u1, v1, d.floors, d.roof, ridgeU)); break; }
                Mass low = new Mass(u0, v0, u1, v1, 1, d.roof == RoofType.FLAT ? RoofType.FLAT : RoofType.HIP, ridgeU);
                masses.add(low);
                float uw = w * 0.62f;
                masses.add(d.leftDoor ? new Mass(u0, v0 + 0.8f, u0 + uw, v1 - 0.8f, d.floors, d.roof, uw >= dep * 0.7f)
                        : new Mass(u1 - uw, v0 + 0.8f, u1, v1 - 0.8f, d.floors, d.roof, uw >= dep * 0.7f));
                break;
            }
            default:
                masses.add(new Mass(u0, v0, u1, v1, d.floors, d.roof, ridgeU));
                break;
        }
        if (d.roof == RoofType.SHED) for (Mass m : masses) if (m == masses.get(0)) m.shedHigh = d.shedBack ? 1 : 0;
        Mass main = masses.get(0);
        for (Mass m : masses) m.top = base + m.floors * d.floorH;
        sb = new SectorBuilder(ch.sectors);
        switch (face) {
            case 0: sb.frame(lx0, lz0, 1f, 0f, 0f, 1f); break;
            case 1: sb.frame(lx1, lz1, -1f, 0f, 0f, -1f); break;
            case 2: sb.frame(lx0, lz1, 0f, -1f, 1f, 0f); break;
            default: sb.frame(lx1, lz0, 0f, 1f, -1f, 0f); break;
        }
        // tallest masses first: lower ones are cut away where a taller one stands
        List<Mass> order = new ArrayList<>(masses);
        order.sort((p, q) -> q.floors - p.floors);
        for (Mass m : order) mass(d, m, m == main, base);
        extras(d, main, base, lot, W, D, lay);
        for (Mass m : masses) {
            float ax = Math.min(wx(m.u0, m.v0), wx(m.u1, m.v1)), bx = Math.max(wx(m.u0, m.v0), wx(m.u1, m.v1));
            float az = Math.min(wz(m.u0, m.v0), wz(m.u1, m.v1)), bz = Math.max(wz(m.u0, m.v0), wz(m.u1, m.v1));
            ch.boxes.add(new Box(ax, base - 1.5f, az, bx, m.top + 1.5f, bz));
        }
    }

    private SectorBuilder sb;

    // ------------------------------------------------------------------ walls

    /**
     * Tiling of one wall face of a mass (role 0 front, 1 back, 2 side): each storey is split
     * into bays, each bay a tile: plain wall, window, small window, garden window or the door.
     * Above the top storey (gables, mono-pitch ends, parapets) the wall is plain material.
     */
    private Sectors.Tiles faceTiles(Design d, Mass m, boolean main, int role, float len) {
        Sectors.Tiles t = new Sectors.Tiles(m.floors);
        t.mat = lib.houseMat[d.mat1];
        t.matUp = lib.houseMat[m.floors > 1 ? d.mat2 : d.mat1];
        t.found = lib.concrete;
        t.groundH = t.storeyH = d.floorH;
        t.winW = d.winW;
        t.winH = d.winH;
        boolean wideDoor = lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_LATTICE || lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_GLASS;
        t.doorW = wideDoor ? 1.7f : 0.95f;
        int win = lib.houseWin[d.win], small = lib.houseWin[d.winSmall], garden = lib.houseWin[d.winGarden];
        boolean front = role == 0, back = role == 1;
        int bays = Math.max(1, (int) (len / d.bay));
        float bw = len / bays;
        for (int f = 0; f < m.floors; f++) {
            t.storey(f, bays, Sectors.PLAIN, 0);
            int doorBay = front && f == 0 && main ? (d.leftDoor ? bays - 1 : 0) : -1;   // faces run right to left seen from the street
            for (int b = 0; b < bays; b++) {
                byte k = Sectors.PLAIN;
                int tex = 0;
                int pattern = (d.id * 31 + b * 7 + f * 13 + role * 5) & 7;
                if (b == doorBay) { k = Sectors.DOOR; tex = lib.houseDoor[d.door]; }
                else if (role == 2) {
                    if (pattern < 4 && bw > 1.4f) { k = Sectors.SMALL; tex = small; }
                    else if (pattern == 4 && f > 0) { k = Sectors.WINDOW; tex = win; }
                } else if (back && f == 0 && pattern < 5) {
                    if (bw > 1.6f) { k = Sectors.GARDEN; tex = garden; }
                } else if (front && f == 1 && d.balcony && b == bays / 2) {
                    if (bw > 1.6f) { k = Sectors.GARDEN; tex = garden; }
                } else if (!(pattern == 7 && !front) && bw > 1.3f) {
                    if (pattern == 6 && f == 0 && front) { k = Sectors.SMALL; tex = small; }
                    else { k = Sectors.WINDOW; tex = win; }
                }
                t.kind[f][b] = k;
                t.tex[f][b] = tex;
            }
        }
        return t;
    }

    /** One mass: its four tiled faces and its roof pieces. */
    private void mass(Design d, Mass m, boolean main, float base) {
        float w = m.u1 - m.u0, dep = m.v1 - m.v0;
        SectorBuilder.Face[] faces = sb.rectFaces(m.u0, m.v0, m.u1, m.v1,
                faceTiles(d, m, main, 0, w), faceTiles(d, m, main, 2, dep), faceTiles(d, m, main, 1, w), faceTiles(d, m, main, 2, dep));
        Sectors.Tiles inner = SectorBuilder.plain(lib.houseMat[m.floors > 1 ? d.mat2 : d.mat1], lib.concrete);
        int rt = lib.houseRoof[d.roofTex];
        float top = m.top, p = d.pitch;
        switch (m.roof) {
            case FLAT: {
                float t = 0.25f, h = top + 0.6f;
                int trim = lib.houseMat[d.trim];
                Sectors.Tiles cap = SectorBuilder.plain(trim, lib.concrete);
                float u0 = m.u0, v0 = m.v0, u1 = m.u1, v1 = m.v1;
                sb.piece(new float[]{u0 + t, v0 + t, u1 - t, v0 + t, u1 - t, v1 - t, u0 + t, v1 - t}, top + 0.05f, 0f, 0f, base, lib.roofMembrane, 2f, -1f, faces, cap);
                sb.piece(new float[]{u0, v0, u1, v0, u1, v0 + t, u0, v0 + t}, h, 0f, 0f, base, trim, 2f, -1f, faces, cap);
                sb.piece(new float[]{u0, v1 - t, u1, v1 - t, u1, v1, u0, v1}, h, 0f, 0f, base, trim, 2f, -1f, faces, cap);
                sb.piece(new float[]{u0, v0 + t, u0 + t, v0 + t, u0 + t, v1 - t, u0, v1 - t}, h, 0f, 0f, base, trim, 2f, -1f, faces, cap);
                sb.piece(new float[]{u1 - t, v0 + t, u1, v0 + t, u1, v1 - t, u1 - t, v1 - t}, h, 0f, 0f, base, trim, 2f, -1f, faces, cap);
                break;
            }
            case SHED: {
                float A, B = 0f, C = 0f;
                switch (m.shedHigh) {
                    case 0: A = top + p * m.v1; C = -p; break;
                    case 1: A = top - p * m.v0; C = p; break;
                    case 2: A = top + p * m.u1; B = -p; break;
                    default: A = top - p * m.u0; B = p; break;
                }
                sb.piece(new float[]{m.u0, m.v0, m.u1, m.v0, m.u1, m.v1, m.u0, m.v1}, A, B, C, base, rt, 2f, -1f, faces, inner);
                break;
            }
            default: {
                // roof coordinates: a along the ridge, b across
                boolean ru = m.ridgeU;
                float a0 = ru ? m.u0 : m.v0, a1 = ru ? m.u1 : m.v1, b0 = ru ? m.v0 : m.u0, b1 = ru ? m.v1 : m.u1;
                float half = (b1 - b0) * 0.5f, bm = (b0 + b1) * 0.5f;
                float k = m.roof == RoofType.GABLE ? 0f : (m.roof == RoofType.IRIMOYA ? 0.5f : 1f);
                float rk = Math.min(half, (a1 - a0) * 0.5f) * k;
                float ma0 = a0 + rk, ma1 = a1 - rk;
                // the gable middle: two slopes from the eaves up to the ridge
                if (ma1 - ma0 > 0.01f) {
                    roofPiece(ru, new float[]{ma0, b0, ma1, b0, ma1, bm, ma0, bm}, top - p * b0, 0f, p, base, rt, faces, inner);
                    roofPiece(ru, new float[]{ma0, bm, ma1, bm, ma1, b1, ma0, b1}, top + p * b1, 0f, -p, base, rt, faces, inner);
                }
                if (rk > 0.01f) {
                    for (int e = 0; e < 2; e++) {
                        float aE = e == 0 ? a0 : a1, s = e == 0 ? 1f : -1f, aK = aE + s * rk;
                        // hipped end (for irimoya only its lower part: the gable wall stands above it)
                        roofPiece(ru, new float[]{aE, b0, aE, b1, aK, b1 - rk, aK, b0 + rk}, top - p * s * aE, p * s, 0f, base, rt, faces, inner);
                        roofPiece(ru, new float[]{aE, b0, aK, b0 + rk, aK, b0}, top - p * b0, 0f, p, base, rt, faces, inner);
                        roofPiece(ru, new float[]{aE, b1, aK, b1, aK, b1 - rk}, top + p * b1, 0f, -p, base, rt, faces, inner);
                    }
                }
                break;
            }
        }
        sb.taken(m.u0, m.v0, m.u1, m.v1);
    }

    /** A roof piece given in roof coordinates (a, b) with plane y = A + Ba a + Bb b. */
    private void roofPiece(boolean ru, float[] ab, float A, float Ba, float Bb, float base, int tex, SectorBuilder.Face[] faces, Sectors.Tiles inner) {
        float[] uv = new float[ab.length];
        for (int i = 0; i < ab.length; i += 2) {
            uv[i] = ru ? ab[i] : ab[i + 1];
            uv[i + 1] = ru ? ab[i + 1] : ab[i];
        }
        sb.piece(uv, A, ru ? Ba : Bb, ru ? Bb : Ba, base, tex, 2f, -1f, faces, inner);
    }

    // ------------------------------------------------------------------ extras

    private void extras(Design d, Mass main, float base, boolean lot, float W, float D, float[] lay) {
        // a/c outdoor unit beside the house
        {
            float u = main.u0 - 0.45f;
            boxLocal(u - 0.3f, main.v0 + 1f, u + 0.05f, main.v0 + 1.8f, base, base + 0.6f, lib.metal, lib.metal);
        }
        if (!lot) return;
        // carport: four posts and a flat polycarbonate roof
        if (!Float.isNaN(lay[4])) {
            float c0 = lay[4], c1 = lay[5], v0 = 0.8f, v1 = Math.min(D - 1f, lay[1] + 5.4f);
            int post = lib.pole;
            for (int k = 0; k < 4; k++) {
                float pu = (k & 1) == 0 ? c0 + 0.1f : c1 - 0.2f, pv = (k & 2) == 0 ? v0 + 0.2f : v1 - 0.3f;
                boxLocal(pu, pv, pu + 0.1f, pv + 0.1f, base - 0.3f, base + 2.3f, post, post);
            }
            boxLocal(c0, v0, c1, v1, base + 2.3f, base + 2.42f, lib.vinyl, lib.vinyl);
        }
        // property walls: block walls down both sides and across the back, and along the front a
        // block wall or hedge (or nothing) with an opening for the drive and the path
        Sectors.Tiles bw = SectorBuilder.plain(lib.blockWall, lib.blockWall);
        bw.matScale = 1.2f;
        float fb = base - 0.15f, bt = 0.15f;
        sb.block(0.2f, 0.4f, 0.2f + bt, D - 0.2f - bt, fb, fb + 1.2f, lib.concrete, bw);
        sb.block(W - 0.2f - bt, 0.4f, W - 0.2f, D - 0.2f - bt, fb, fb + 1.2f, lib.concrete, bw);
        sb.block(0.2f, D - 0.2f - bt, W - 0.2f, D - 0.2f, fb, fb + 1.2f, lib.concrete, bw);
        if (d.fence > 0) {
            int tex = d.fence == 1 ? lib.blockWall : lib.hedge;
            float h = d.fence == 1 ? 0.95f : 1.15f, t = d.fence == 1 ? 0.15f : 0.6f;
            float gap0 = Float.isNaN(lay[4]) ? (d.leftDoor ? lay[0] - 0.5f : lay[2] - 3f) : lay[4];
            float gap1 = Float.isNaN(lay[4]) ? gap0 + 3.5f : lay[5];
            if (!Float.isNaN(lay[4])) {
                if (d.leftDoor) gap0 = Math.min(gap0, lay[2] - 2f); else gap1 = Math.max(gap1, lay[0] + 2f);
            }
            gap0 = Math.max(0.3f, gap0); gap1 = Math.min(W - 0.3f, gap1);
            Sectors.Tiles ft = SectorBuilder.plain(tex, tex);
            ft.matScale = d.fence == 1 ? 1.2f : 2f;
            int cap = d.fence == 1 ? lib.concrete : tex;
            if (gap0 > 0.6f) sb.block(0.2f, 0.2f, gap0, 0.2f + t, fb, fb + h, cap, ft);
            if (gap1 < W - 0.6f) sb.block(gap1, 0.2f, W - 0.2f, 0.2f + t, fb, fb + h, cap, ft);
        }
    }

    // ------------------------------------------------------------------ props (local to world)

    /** Box given in local u/v (axis aligned in the world too: lots are rotated by quarter turns). */
    private void boxLocal(float u0, float v0, float u1, float v1, float y0, float y1, int side, int top) {
        float ax = Math.min(wx(u0, v0), wx(u1, v1)), bx = Math.max(wx(u0, v0), wx(u1, v1));
        float az = Math.min(wz(u0, v0), wz(u1, v1)), bz = Math.max(wz(u0, v0), wz(u1, v1));
        gb.box(ax, y0, az, bx, y1, bz, side, top, 0.5f, 0.5f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
    }
}
