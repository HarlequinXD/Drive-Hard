package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
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
 * <p>Walls are built around their openings: each storey of a wall is split into solid strips,
 * pieces above and below each window, and the window or door itself, so nothing is layered on
 * top of a wall (no z-fighting) and doors sit in a recess. Gable ends, the parts of side walls
 * under a mono-pitch roof, and irimoya gables are plain wall material without windows.</p>
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
        for (Mass m : masses) plinth(m, base);
        for (int i = 0; i < masses.size(); i++) walls(d, masses.get(i), masses, i == 0, base);
        for (Mass m : masses) roof(d, m);
        extras(d, main, masses, base, lot, W, D, lay);
        for (Mass m : masses) {
            float ax = Math.min(wx(m.u0, m.v0), wx(m.u1, m.v1)), bx = Math.max(wx(m.u0, m.v0), wx(m.u1, m.v1));
            float az = Math.min(wz(m.u0, m.v0), wz(m.u1, m.v1)), bz = Math.max(wz(m.u0, m.v0), wz(m.u1, m.v1));
            ch.boxes.add(new Box(ax, base - 1.5f, az, bx, m.top + 1.5f, bz));
        }
    }

    // ------------------------------------------------------------------ walls

    /** Concrete plinth from the ground up to the floor, all round a mass. */
    private void plinth(Mass m, float base) {
        float[][] w = {{m.u1, m.v0, m.u0, m.v0}, {m.u0, m.v0, m.u0, m.v1}, {m.u0, m.v1, m.u1, m.v1}, {m.u1, m.v1, m.u1, m.v0}};
        for (float[] q : w) {
            float ha = gb.h(wx(q[0], q[1]), wz(q[0], q[1])) - 0.3f, hb = gb.h(wx(q[2], q[3]), wz(q[2], q[3])) - 0.3f;
            if (ha >= base && hb >= base) continue;
            float len = (float) Math.hypot(q[2] - q[0], q[3] - q[1]);
            quadWorld(q[0], Math.min(ha, base), q[1], q[2], Math.min(hb, base), q[3], q[2], base, q[3], q[0], base, q[1], lib.concrete,
                    0f, 0.5f, len / 2f, 0.5f, len / 2f, 0f, 0f, 0f, -0.75f, false);
        }
    }

    /** An opening in a wall storey: along-wall range, heights above the floor, texture, recess. */
    private static final class Opening {
        float s0, s1, y0, y1, recess;
        int tex;
        Opening(float s0, float s1, float y0, float y1, int tex, float recess) {
            this.s0 = s0; this.s1 = s1; this.y0 = y0; this.y1 = y1; this.tex = tex; this.recess = recess;
        }
    }

    /** Walls of one mass: 0 front (v0), 1 right... in order front, side u1, back, side u0. */
    /** Centre of the mass being built (for orienting gable triangles). */
    private float cu, cv;

    private void walls(Design d, Mass m, List<Mass> all, boolean main, float base) {
        cu = (m.u0 + m.u1) * 0.5f;
        cv = (m.v0 + m.v1) * 0.5f;
        // wall endpoints (local), outward winding: A -> B with the outside on the left of travel
        float[][] w = {{m.u1, m.v0, m.u0, m.v0}, {m.u0, m.v0, m.u0, m.v1}, {m.u0, m.v1, m.u1, m.v1}, {m.u1, m.v1, m.u1, m.v0}};
        int[] role = {0, 2, 1, 2};      // front, side, back, side
        for (int k = 0; k < 4; k++) {
            float ax = w[k][0], av = w[k][1], bx = w[k][2], bv = w[k][3];
            float len = (float) Math.hypot(bx - ax, bv - av);
            // skip walls buried inside another mass
            float mu = (ax + bx) * 0.5f, mv = (av + bv) * 0.5f;
            float nu = -(bv - av) / len, nv = (bx - ax) / len;        // outward normal (local)
            boolean buried = false;
            for (Mass o : all) if (o != m && o.contains(mu + nu * 0.3f, mv + nv * 0.3f) && o.floors >= m.floors) buried = true;
            for (int f = 0; f < m.floors; f++) {
                float y0 = base + f * d.floorH, y1 = y0 + d.floorH;
                boolean coveredHere = false;
                for (Mass o : all) if (o != m && o.contains(mu + nu * 0.3f, mv + nv * 0.3f) && o.floors > f) coveredHere = true;
                if (buried || coveredHere) continue;
                List<Opening> ops = openings(d, m, main, role[k], f, len);
                int mat = lib.houseMat[f == 0 ? d.mat1 : d.mat2];
                wallStorey(ax, av, bx, bv, len, y0, y1, ops, mat, f == 0);
            }
            // above the top storey: gable triangle or mono-pitch infill, plain material
            if (!buried) topInfill(d, m, k, ax, av, bx, bv);
        }
    }

    /** Openings of one storey of one wall (role 0 front, 1 back, 2 side). */
    private List<Opening> openings(Design d, Mass m, boolean main, int role, int floor, float len) {
        List<Opening> ops = new ArrayList<>();
        int bays = Math.max(1, (int) (len / d.bay));
        float bw = len / bays;
        int win = lib.houseWin[d.win], small = lib.houseWin[d.winSmall], garden = lib.houseWin[d.winGarden];
        boolean front = role == 0, back = role == 1;
        int doorBay = -1;
        if (front && floor == 0 && main) doorBay = d.leftDoor ? bays - 1 : 0;   // walls run right to left seen from the street
        boolean wideDoor = lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_LATTICE || lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_GLASS;
        for (int b = 0; b < bays; b++) {
            float c = (b + 0.5f) * bw;
            if (b == doorBay) {
                float dw = wideDoor ? 1.7f : 0.95f;
                ops.add(new Opening(c - dw * 0.5f, c + dw * 0.5f, 0f, 2.15f, lib.houseDoor[d.door], 0.35f));
                continue;
            }
            int pattern = (d.id * 31 + b * 7 + floor * 13 + role * 5) & 7;
            if (role == 2) {
                // side walls: a few small windows
                if (pattern < 4 && bw > 1.4f) ops.add(new Opening(c - 0.35f, c + 0.35f, 1.3f, 2.1f, small, 0.08f));
                else if (pattern == 4 && floor > 0) ops.add(new Opening(c - d.winW * 0.4f, c + d.winW * 0.4f, 0.95f, 0.95f + d.winH, win, 0.08f));
                continue;
            }
            if (back && floor == 0 && pattern < 5) {
                float gw = Math.min(bw - 0.6f, 1.8f);
                if (gw > 1f) ops.add(new Opening(c - gw * 0.5f, c + gw * 0.5f, 0.1f, 2.1f, garden, 0.1f));
                continue;
            }
            if (front && floor == 1 && d.balcony && b == bays / 2) {
                float gw = Math.min(bw - 0.6f, 1.7f);
                if (gw > 1f) ops.add(new Opening(c - gw * 0.5f, c + gw * 0.5f, 0.05f, 2.05f, garden, 0.1f));
                continue;
            }
            if (pattern == 7 && !front) continue;
            float ww = Math.min(bw - 0.7f, d.winW);
            if (ww < 0.6f) continue;
            if (pattern == 6 && floor == 0 && front) ops.add(new Opening(c - 0.35f, c + 0.35f, 1.3f, 2.1f, small, 0.08f));
            else ops.add(new Opening(c - ww * 0.5f, c + ww * 0.5f, 0.95f, 0.95f + d.winH, win, 0.08f));
        }
        return ops;
    }

    /**
     * One storey of a wall built around its openings: solid strips between openings, pieces above
     * and below each opening, the opening itself set back by its recess with reveals.
     */
    private void wallStorey(float au, float av, float bu, float bv, float len, float y0, float y1, List<Opening> ops, int mat, boolean ground) {
        float du = (bu - au) / len, dv = (bv - av) / len;
        float nu = -dv, nv = du;                                  // outward
        float s = 0f;
        for (Opening o : ops) {
            if (o.s0 > s) wallQuad(au, av, du, dv, s, o.s0, y0, y1, mat, ground);
            float oy0 = y0 + o.y0, oy1 = Math.min(y1, y0 + o.y1);
            if (oy0 > y0 + 0.01f) wallQuad(au, av, du, dv, o.s0, o.s1, y0, oy0, mat, ground);
            if (oy1 < y1 - 0.01f) wallQuad(au, av, du, dv, o.s0, o.s1, oy1, y1, mat, false);
            // the opening, recessed into the wall
            float rc = o.recess;
            float pu0 = au + du * o.s0 - nu * rc, pv0 = av + dv * o.s0 - nv * rc;
            float pu1 = au + du * o.s1 - nu * rc, pv1 = av + dv * o.s1 - nv * rc;
            quadLocal(pu0, oy0, pv0, pu1, oy0, pv1, pu1, oy1, pv1, pu0, oy1, pv0, o.tex, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0.9f);
            // reveals (jambs and head) in wall material
            float qu0 = au + du * o.s0, qv0 = av + dv * o.s0, qu1 = au + du * o.s1, qv1 = av + dv * o.s1;
            quadLocal(qu0, oy0, qv0, pu0, oy0, pv0, pu0, oy1, pv0, qu0, oy1, qv0, mat, 0f, 0f, rc / 2f, 0f, rc / 2f, -oy1 / 2f, 0f, -oy1 / 2f, 0.7f);
            quadLocal(pu1, oy0, pv1, qu1, oy0, qv1, qu1, oy1, qv1, pu1, oy1, pv1, mat, 0f, 0f, rc / 2f, 0f, rc / 2f, -oy1 / 2f, 0f, -oy1 / 2f, 0.7f);
            quadLocal(pu0, oy1, pv0, pu1, oy1, pv1, qu1, oy1, qv1, qu0, oy1, qv0, mat, 0f, 0f, 1f, 0f, 1f, rc / 2f, 0f, rc / 2f, 0.55f);
            if (o.y0 > 0.2f) quadLocal(qu0, oy0, qv0, qu1, oy0, qv1, pu1, oy0, pv1, pu0, oy0, pv0, lib.houseMat[0], 0f, 0f, 1f, 0f, 1f, rc / 2f, 0f, rc / 2f, 1f);
            s = o.s1;
        }
        if (s < len) wallQuad(au, av, du, dv, s, len, y0, y1, mat, ground);
    }

    /** Solid wall piece between along-wall s0..s1 and heights y0..y1, material mapped in metres. */
    private void wallQuad(float au, float av, float du, float dv, float s0, float s1, float y0, float y1, int mat, boolean ground) {
        if (s1 - s0 < 0.005f || y1 - y0 < 0.005f) return;
        float u0 = au + du * s0, v0 = av + dv * s0, u1 = au + du * s1, v1 = av + dv * s1;
        float ao = ground && y0 < 1f + 0f ? 0.82f : 1f;
        quadLocalAO(u0, y0, v0, u1, y0, v1, u1, y1, v1, u0, y1, v0, mat,
                s0 / 2f, -y0 / 2f, s1 / 2f, -y0 / 2f, s1 / 2f, -y1 / 2f, s0 / 2f, -y1 / 2f, ao);
    }

    /** Gable triangle or mono-pitch infill above a wall's top storey. */
    private void topInfill(Design d, Mass m, int k, float au, float av, float bu, float bv) {
        int mat = lib.houseMat[m.floors > 1 ? d.mat2 : d.mat1];
        float top = m.top;
        boolean front = k == 0, back = k == 2, side = k == 1 || k == 3;
        if (m.roof == RoofType.GABLE) {
            boolean gableWall = m.ridgeU ? side : (front || back);
            if (!gableWall) return;
            float rise = d.pitch * (m.ridgeU ? (m.v1 - m.v0) : (m.u1 - m.u0)) * 0.5f;
            float mu = (au + bu) * 0.5f, mv = (av + bv) * 0.5f;
            float len = (float) Math.hypot(bu - au, bv - av);
            triLocal(au, top, av, bu, top, bv, mu, top + rise, mv, mat, 0f, -top / 2f, len / 2f, -top / 2f, len / 4f, -(top + rise) / 2f);
        } else if (m.roof == RoofType.SHED) {
            float span = m.shedHigh < 2 ? (m.v1 - m.v0) : (m.u1 - m.u0);
            float rise = d.pitch * span;
            // which wall is the high one, which are the sloping sides
            int highWall = m.shedHigh == 0 ? 0 : (m.shedHigh == 1 ? 2 : (m.shedHigh == 2 ? 1 : 3));
            int lowWall = (highWall + 2) % 4;
            float len = (float) Math.hypot(bu - au, bv - av);
            if (k == highWall) {
                quadLocalAO(au, top, av, bu, top, bv, bu, top + rise, bv, au, top + rise, av, mat,
                        0f, -top / 2f, len / 2f, -top / 2f, len / 2f, -(top + rise) / 2f, 0f, -(top + rise) / 2f, 1f);
            } else if (k != lowWall) {
                // side: triangle rising toward the high wall
                boolean aHigh = isOnWall(m, highWall, au, av);
                if (aHigh) triLocal(au, top, av, bu, top, bv, au, top + rise, av, mat, 0f, -top / 2f, len / 2f, -top / 2f, 0f, -(top + rise) / 2f);
                else triLocal(au, top, av, bu, top, bv, bu, top + rise, bv, mat, 0f, -top / 2f, len / 2f, -top / 2f, len / 2f, -(top + rise) / 2f);
            }
        } else if (m.roof == RoofType.FLAT) {
            // parapet
            float len = (float) Math.hypot(bu - au, bv - av);
            quadLocalAO(au, top, av, bu, top, bv, bu, top + 0.6f, bv, au, top + 0.6f, av, mat,
                    0f, -top / 2f, len / 2f, -top / 2f, len / 2f, -(top + 0.6f) / 2f, 0f, -(top + 0.6f) / 2f, 1f);
        }
    }

    private static boolean isOnWall(Mass m, int wall, float u, float v) {
        switch (wall) {
            case 0: return Math.abs(v - m.v0) < 0.01f;
            case 2: return Math.abs(v - m.v1) < 0.01f;
            case 1: return Math.abs(u - m.u0) < 0.01f;
            default: return Math.abs(u - m.u1) < 0.01f;
        }
    }

    // ------------------------------------------------------------------ roofs

    private void roof(Design d, Mass m) {
        cu = (m.u0 + m.u1) * 0.5f;
        cv = (m.v0 + m.v1) * 0.5f;
        int rt = lib.houseRoof[d.roofTex];
        float ov = d.overhang, top = m.top;
        if (m.roof == RoofType.FLAT) {
            float l = GeoBuilder.sunLight(0, 1, 0);
            quadWorld(m.u0, top + 0.05f, m.v0, m.u1, top + 0.05f, m.v0, m.u1, top + 0.05f, m.v1, m.u0, top + 0.05f, m.v1,
                    lib.roofMembrane, 0f, 0f, (m.u1 - m.u0) / 2f, 0f, (m.u1 - m.u0) / 2f, (m.v1 - m.v0) / 2f, 0f, (m.v1 - m.v0) / 2f, l, true);
            // parapet inner faces and cap
            int mat = lib.houseMat[d.trim];
            float t = 0.2f, h = top + 0.6f;
            float[][] e = {{m.u0, m.v0, m.u1, m.v0}, {m.u1, m.v0, m.u1, m.v1}, {m.u1, m.v1, m.u0, m.v1}, {m.u0, m.v1, m.u0, m.v0}};
            for (float[] q : e) {
                float len = (float) Math.hypot(q[2] - q[0], q[3] - q[1]);
                float du = (q[2] - q[0]) / len, dv = (q[3] - q[1]) / len, nu = -dv, nv = du;   // inward for this winding
                quadLocalAO(q[0] + nu * t, top, q[1] + nv * t, q[2] + nu * t, top, q[3] + nv * t, q[2] + nu * t, h, q[3] + nv * t, q[0] + nu * t, h, q[1] + nv * t,
                        mat, 0f, 0f, len / 2f, 0f, len / 2f, -0.3f, 0f, -0.3f, 0.8f);
                quadWorld(q[0], h, q[1], q[2], h, q[3], q[2] + nu * t, h, q[3] + nv * t, q[0] + nu * t, h, q[1] + nv * t,
                        mat, 0f, 0f, len / 2f, 0f, len / 2f, 0.1f, 0f, 0.1f, GeoBuilder.sunLight(0, 1, 0), true);
            }
            return;
        }
        // work in (a along the ridge, b across) roof coordinates
        boolean ru = m.ridgeU;
        float a0 = (ru ? m.u0 : m.v0) - ov, a1 = (ru ? m.u1 : m.v1) + ov;
        float b0 = (ru ? m.v0 : m.u0) - ov, b1 = (ru ? m.v1 : m.u1) + ov;
        if (m.roof == RoofType.SHED) {
            // mono-pitch: slope from the low side up to the high side (in local u/v directly)
            float U0 = m.u0 - ov, U1 = m.u1 + ov, V0 = m.v0 - ov, V1 = m.v1 + ov;
            float span = m.shedHigh < 2 ? m.v1 - m.v0 : m.u1 - m.u0;
            float yLow = top - ov * d.pitch, yHigh = top + d.pitch * span + ov * d.pitch;
            float y00, y10, y11, y01;           // heights at (U0,V0) (U1,V0) (U1,V1) (U0,V1)
            switch (m.shedHigh) {
                case 0: y00 = y10 = yHigh; y11 = y01 = yLow; break;
                case 1: y00 = y10 = yLow; y11 = y01 = yHigh; break;
                case 2: y00 = y01 = yHigh; y10 = y11 = yLow; break;
                default: y00 = y01 = yLow; y10 = y11 = yHigh; break;
            }
            boolean alongU = m.shedHigh < 2;
            float kk = (float) Math.sqrt(1f + 1f / (d.pitch * d.pitch)) * 0.5f;
            quadWorld(U0, y00, V0, U1, y10, V0, U1, y11, V1, U0, y01, V1, rt,
                    (alongU ? U0 : V0) * 0.5f, -y00 * kk, (alongU ? U1 : V0) * 0.5f, -y10 * kk,
                    (alongU ? U1 : V1) * 0.5f, -y11 * kk, (alongU ? U0 : V1) * 0.5f, -y01 * kk, -1f, true);
            int trim = lib.houseMat[d.trim];
            // fascia along the low eave
            switch (m.shedHigh) {
                case 0: edgeBoard(U0, V1, U1, V1, yLow, trim); break;
                case 1: edgeBoard(U0, V0, U1, V0, yLow, trim); break;
                case 2: edgeBoard(U1, V0, U1, V1, yLow, trim); break;
                default: edgeBoard(U0, V0, U0, V1, yLow, trim); break;
            }
            return;
        }
        float half = (b1 - b0) * 0.5f, bm = (b0 + b1) * 0.5f;
        float ye = top - ov * d.pitch, yr = ye + d.pitch * half;
        int trim = lib.houseMat[d.trim];
        if (m.roof == RoofType.GABLE) {
            roofQuad(ru, true, a0, b0, ye, a1, b0, ye, a1, bm, yr, a0, bm, yr, rt, d.pitch);
            roofQuad(ru, true, a1, b1, ye, a0, b1, ye, a0, bm, yr, a1, bm, yr, rt, d.pitch);
            fascia(ru, true, a0, a1, b0, ye, trim);
            fascia(ru, true, a1, a0, b1, ye, trim);
            // barge boards along the rakes
            rake(ru, a0, b0, bm, ye, yr, trim);
            rake(ru, a1, b1, bm, ye, yr, trim);
            ridgeCap(ru, a0, a1, bm, yr, rt);
            return;
        }
        // hip (k = 1) and irimoya (k = 0.5): end planes up to height hk, then a small gable
        float k = m.roof == RoofType.IRIMOYA ? 0.5f : 1f;
        float rr = Math.min(half, (a1 - a0) * 0.5f);
        float rk = rr * k, hk = ye + d.pitch * rk;
        float yTop = ye + d.pitch * Math.min(half, rr);
        // main slopes: trapezoid up to the hk line, then the band up to the ridge
        for (int side = 0; side < 2; side++) {
            float bb = side == 0 ? b0 : b1, sg = side == 0 ? 1f : -1f;
            float bk = bb + sg * rk, br = bm;
            float aa0 = side == 0 ? a0 : a1, aa1 = side == 0 ? a1 : a0, sa = side == 0 ? 1f : -1f;
            roofQuad(ru, true, aa0, bb, ye, aa1, bb, ye, aa1 - sa * rk, bk, hk, aa0 + sa * rk, bk, hk, rt, d.pitch);
            if (k < 1f) roofQuad(ru, true, aa0 + sa * rk, bk, hk, aa1 - sa * rk, bk, hk, aa1 - sa * rk, br, yr, aa0 + sa * rk, br, yr, rt, d.pitch);
            fascia(ru, true, aa0, aa1, bb, ye, trim);
        }
        // end planes
        for (int end = 0; end < 2; end++) {
            float aa = end == 0 ? a0 : a1, sa = end == 0 ? 1f : -1f;
            float bb0 = end == 0 ? b1 : b0, bb1 = end == 0 ? b0 : b1, sb = end == 0 ? -1f : 1f;
            roofQuad(ru, false, aa, bb0, ye, aa, bb1, ye, aa + sa * rk, bb1 - sb * rk, hk, aa + sa * rk, bb0 + sb * rk, hk, rt, d.pitch);
            fasciaEnd(ru, aa, bb0, bb1, ye, trim);
            if (k < 1f) {
                // the irimoya gable: vertical triangle in plain wall material
                float ag = aa + sa * rk;
                float gbs = b0 + rk, gbe = b1 - rk;
                float[] P = ab(ru, ag, end == 0 ? gbe : gbs), Q = ab(ru, ag, end == 0 ? gbs : gbe), R = ab(ru, ag, bm);
                triLocal(P[0], hk, P[1], Q[0], hk, Q[1], R[0], yr, R[1], lib.houseMat[d.mat2], 0f, -hk / 2f, (gbe - gbs) / 2f, -hk / 2f, (gbe - gbs) / 4f, -yr / 2f);
            }
        }
        if (k < 1f || rr < half - 0.01f) ridgeCap(ru, a0 + rk, a1 - rk, bm, k < 1f ? yr : yTop, rt);
    }

    /** Local (u, v) of roof coordinates (a along the ridge, b across). */
    private static float[] ab(boolean ru, float a, float b) { return ru ? new float[]{a, b} : new float[]{b, a}; }

    /**
     * Roof plane through four points given in roof coordinates (a along the ridge, b across).
     * Tiles are mapped in metres: u along {@code alongA ? a : b}, v down the fall line.
     */
    private void roofQuad(boolean ru, boolean alongA, float a0, float b0, float y0, float a1, float b1, float y1,
                          float a2, float b2, float y2, float a3, float b3, float y3, int tex, float pitch) {
        float[] p0 = ab(ru, a0, b0), p1 = ab(ru, a1, b1), p2 = ab(ru, a2, b2), p3 = ab(ru, a3, b3);
        float k = (float) Math.sqrt(1f + 1f / (pitch * pitch)) * 0.5f;     // fall-line metres per metre of rise, /2 m tiles
        quadWorld(p0[0], y0, p0[1], p1[0], y1, p1[1], p2[0], y2, p2[1], p3[0], y3, p3[1], tex,
                (alongA ? a0 : b0) * 0.5f, -y0 * k, (alongA ? a1 : b1) * 0.5f, -y1 * k,
                (alongA ? a2 : b2) * 0.5f, -y2 * k, (alongA ? a3 : b3) * 0.5f, -y3 * k, -1f, true);
    }

    private void fascia(boolean ru, boolean unused, float a0, float a1, float b, float y, int tex) {
        float[] p = ab(ru, a0, b), q = ab(ru, a1, b);
        edgeBoard(p[0], p[1], q[0], q[1], y, tex);
    }

    private void fasciaEnd(boolean ru, float a, float b0, float b1, float y, int tex) {
        float[] p = ab(ru, a, b0), q = ab(ru, a, b1);
        edgeBoard(p[0], p[1], q[0], q[1], y, tex);
    }

    /** Fascia board under an eave edge (local u/v), 20 cm deep. */
    private void edgeBoard(float u0, float v0, float u1, float v1, float y, int tex) {
        float len = (float) Math.hypot(u1 - u0, v1 - v0);
        quadWorld(u0, y - 0.2f, v0, u1, y - 0.2f, v1, u1, y, v1, u0, y, v0, tex, 0f, 0.1f, len / 2f, 0.1f, len / 2f, 0f, 0f, 0f, -0.9f, true);
    }

    /** Barge board along a gable's sloping edge (both slopes). */
    private void rake(boolean ru, float a, float bEave, float bm, float ye, float yr, int tex) {
        float bOther = 2 * bm - bEave;
        for (float be : new float[]{bEave, bOther}) {
            float[] p = ab(ru, a, be), q = ab(ru, a, bm);
            quadWorld(p[0], ye - 0.2f, p[1], q[0], yr - 0.2f, q[1], q[0], yr + 0.02f, q[1], p[0], ye + 0.02f, p[1], tex,
                    0f, 0.1f, 1f, 0.1f, 1f, 0f, 0f, 0f, 0.8f, true);
        }
    }

    private void ridgeCap(boolean ru, float a0, float a1, float bm, float y, int tex) {
        if (a1 - a0 < 0.05f) return;
        float[] p = ab(ru, a0, bm - 0.18f), q = ab(ru, a1, bm - 0.18f), s = ab(ru, a1, bm + 0.18f), t = ab(ru, a0, bm + 0.18f);
        quadWorld(p[0], y + 0.12f, p[1], q[0], y + 0.12f, q[1], s[0], y + 0.12f, s[1], t[0], y + 0.12f, t[1], tex,
                0f, 0.9f, (a1 - a0) / 2f, 0.9f, (a1 - a0) / 2f, 1f, 0f, 1f, GeoBuilder.sunLight(0, 1, 0) * 0.8f, true);
    }

    // ------------------------------------------------------------------ extras

    private void extras(Design d, Mass main, List<Mass> masses, float base, boolean lot, float W, float D, float[] lay) {
        // porch canopy over the door
        if (d.porch && main.floors >= 1) {
            int bays = Math.max(1, (int) ((main.u1 - main.u0) / d.bay));
            float bw = (main.u1 - main.u0) / bays;
            int db = d.leftDoor ? bays - 1 : 0;
            float cs = (db + 0.5f) * bw;                 // along the front wall from u1 toward u0
            float cu = main.u1 - cs;
            boxLocal(cu - 1f, main.v0 - 0.9f, cu + 1f, main.v0, base + 2.45f, base + 2.6f, lib.houseMat[d.trim], lib.houseMat[d.trim]);
        }
        // traditional skirt roof (hisashi) along the front at the first floor line
        if (d.hisashi) {
            float y = base + Math.min(d.floorH, main.top - base) - 0.05f;
            int rt = lib.houseRoof[d.roofTex];
            float u0 = main.u0 - 0.2f, u1 = main.u1 + 0.2f, depth = 1.1f;
            quadWorld(u1, y - depth * 0.45f, main.v0 - depth, u0, y - depth * 0.45f, main.v0 - depth, u0, y, main.v0, u1, y, main.v0,
                    rt, 0f, 0.6f, (u1 - u0) / 2f, 0.6f, (u1 - u0) / 2f, 0f, 0f, 0f, -1f, true);
            fascia(true, true, u0, u1, main.v0 - depth, y - depth * 0.45f, lib.houseMat[d.trim]);
        }
        // balcony on the upper floor
        if (d.balcony && main.floors >= 2) {
            float w = (main.u1 - main.u0) * 0.55f, cu = (main.u0 + main.u1) * 0.5f, y = base + d.floorH;
            int rail = lib.houseMat[d.mat2 == d.mat1 ? d.trim : d.mat2];
            boxLocal(cu - w * 0.5f, main.v0 - 1.1f, cu + w * 0.5f, main.v0, y - 0.15f, y, lib.concrete, lib.concrete);
            boxLocal(cu - w * 0.5f, main.v0 - 1.1f, cu + w * 0.5f, main.v0 - 0.98f, y, y + 1.05f, rail, rail);
            boxLocal(cu - w * 0.5f, main.v0 - 1.1f, cu - w * 0.5f + 0.12f, main.v0, y, y + 1.05f, rail, rail);
            boxLocal(cu + w * 0.5f - 0.12f, main.v0 - 1.1f, cu + w * 0.5f, main.v0, y, y + 1.05f, rail, rail);
        }
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
        // front fence with an opening for the drive and the path
        if (d.fence > 0) {
            int tex = d.fence == 1 ? lib.blockWall : lib.hedge;
            float h = d.fence == 1 ? 0.95f : 1.15f, t = d.fence == 1 ? 0.15f : 0.6f;
            float gap0 = Float.isNaN(lay[4]) ? (d.leftDoor ? lay[0] - 0.5f : lay[2] - 3f) : lay[4];
            float gap1 = Float.isNaN(lay[4]) ? gap0 + 3.5f : lay[5];
            if (!Float.isNaN(lay[4])) {
                // door path next to the carport
                if (d.leftDoor) gap0 = Math.min(gap0, lay[2] - 2f); else gap1 = Math.max(gap1, lay[0] + 2f);
            }
            gap0 = Math.max(0.3f, gap0); gap1 = Math.min(W - 0.3f, gap1);
            if (gap0 > 0.6f) boxLocal(0.2f, 0.2f, gap0, 0.2f + t, base - 0.3f, base + h, tex, tex);
            if (gap1 < W - 0.6f) boxLocal(gap1, 0.2f, W - 0.2f, 0.2f + t, base - 0.3f, base + h, tex, tex);
            if (d.fence == 1) {   // side walls along the boundary
                boxLocal(0.2f, 0.2f, 0.2f + t, D * 0.7f, base - 0.3f, base + h, tex, tex);
                boxLocal(W - 0.2f - t, 0.2f, W - 0.2f, D * 0.7f, base - 0.3f, base + h, tex, tex);
            }
        }
    }

    // ------------------------------------------------------------------ primitives (local to world)

    private final float[] P4 = new float[12], T4 = new float[8], L4 = new float[4];
    private final float[] P3 = new float[9], T3 = new float[6], L3 = new float[3];

    /** Box given in local u/v (axis aligned in the world too: lots are rotated by quarter turns). */
    private void boxLocal(float u0, float v0, float u1, float v1, float y0, float y1, int side, int top) {
        float ax = Math.min(wx(u0, v0), wx(u1, v1)), bx = Math.max(wx(u0, v0), wx(u1, v1));
        float az = Math.min(wz(u0, v0), wz(u1, v1)), bz = Math.max(wz(u0, v0), wz(u1, v1));
        gb.box(ax, y0, az, bx, y1, bz, side, top, 0.5f, 0.5f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
    }

    /** Quad with light from its own (world) normal, scaled by ao. */
    private void quadLocalAO(float u0, float y0, float v0, float u1, float y1, float v1, float u2, float y2, float v2,
                             float u3, float y3, float v3, int tex, float s0, float t0, float s1, float t1, float s2, float t2,
                             float s3, float t3, float ao) {
        quadWorld(u0, y0, v0, u1, y1, v1, u2, y2, v2, u3, y3, v3, tex, s0, t0, s1, t1, s2, t2, s3, t3, -ao, false);
    }

    /** Opening / reveal quad: uv given per corner, light from the normal times k. */
    private void quadLocal(float u0, float y0, float v0, float u1, float y1, float v1, float u2, float y2, float v2,
                           float u3, float y3, float v3, int tex, float s0, float t0, float s1, float t1, float s2, float t2,
                           float s3, float t3, float k) {
        quadWorld(u0, y0, v0, u1, y1, v1, u2, y2, v2, u3, y3, v3, tex, s0, t0, s1, t1, s2, t2, s3, t3, -k, false);
    }

    /**
     * Emits a quad given in local coordinates. light &gt;= 0 is used as is; negative values mean
     * "sun light from the face normal times -light".
     */
    private void quadWorld(float u0, float y0, float v0, float u1, float y1, float v1, float u2, float y2, float v2,
                           float u3, float y3, float v3, int tex, float s0, float t0, float s1, float t1, float s2, float t2,
                           float s3, float t3, float light, boolean doubleSided) {
        float[] p = P4;
        p[0] = wx(u0, v0); p[1] = y0; p[2] = wz(u0, v0);
        p[3] = wx(u1, v1); p[4] = y1; p[5] = wz(u1, v1);
        p[6] = wx(u2, v2); p[7] = y2; p[8] = wz(u2, v2);
        p[9] = wx(u3, v3); p[10] = y3; p[11] = wz(u3, v3);
        T4[0] = s0; T4[1] = t0; T4[2] = s1; T4[3] = t1; T4[4] = s2; T4[5] = t2; T4[6] = s3; T4[7] = t3;
        float lt = light >= 0f ? light : -light * normalLight(p);
        L4[0] = L4[1] = L4[2] = L4[3] = lt;
        gb.quad(p, T4, L4, tex, L_OBJECT, 0, 0f, doubleSided);
    }

    private void triLocal(float u0, float y0, float v0, float u1, float y1, float v1, float u2, float y2, float v2, int tex,
                          float s0, float t0, float s1, float t1, float s2, float t2) {
        // face away from the centre of the current mass (gables and infills are outer walls)
        float nu = -(v1 - v0), nv = u1 - u0;
        float mu = (u0 + u1) * 0.5f - cu, mv = (v0 + v1) * 0.5f - cv;
        if (nu * mu + nv * mv < 0f) {
            float tu = u0, ty = y0, tv = v0, ts = s0, tt = t0;
            u0 = u1; y0 = y1; v0 = v1; s0 = s1; t0 = t1;
            u1 = tu; y1 = ty; v1 = tv; s1 = ts; t1 = tt;
        }
        float[] p = P3;
        p[0] = wx(u0, v0); p[1] = y0; p[2] = wz(u0, v0);
        p[3] = wx(u1, v1); p[4] = y1; p[5] = wz(u1, v1);
        p[6] = wx(u2, v2); p[7] = y2; p[8] = wz(u2, v2);
        T3[0] = s0; T3[1] = t0; T3[2] = s1; T3[3] = t1; T3[4] = s2; T3[5] = t2;
        L3[0] = L3[1] = L3[2] = normalLight(p);
        gb.quad(p, T3, L3, tex, L_OBJECT, 0, 0f, false);
    }

    /** Sun light for the face normal (p0, p1, p2), facing up if the winding points down (roofs). */
    private static float normalLight(float[] p) {
        float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
        float bx = p[6] - p[0], by = p[7] - p[1], bz = p[8] - p[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) return 1f;
        nx /= l; ny /= l; nz /= l;
        if (ny < -0.2f) { nx = -nx; ny = -ny; nz = -nz; }
        return GeoBuilder.sunLight(nx, ny, nz);
    }
}
