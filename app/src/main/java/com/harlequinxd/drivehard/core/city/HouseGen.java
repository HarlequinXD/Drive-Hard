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
 * <p>Each house is a set of procedural masses built by {@link SectorBuilder}. Their facades
 * become textured wall polygons (plain wall, windows, doors and garden windows), while roof
 * surfaces become polygons for gables, hips, mono-pitches and flat parapet roofs.</p>
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
        /** 1 to 7 bedrooms: sets the size of the house (and whether it has an upper floor). */
        int bedrooms;
    }

    /**
     * Sizes a design to its bedroom count: a 1-bedroom cottage is a small single storey, a
     * 7-bedroom family house a large two storeys; never longer than a real house.
     */
    private static void bedrooms(Design d, Random r, int min, int max) {
        // most houses have 2 to 4 bedrooms
        float q = r.nextFloat();
        int b = q < 0.08f ? 1 : q < 0.30f ? 2 : q < 0.62f ? 3 : q < 0.82f ? 4 : q < 0.92f ? 5 : q < 0.97f ? 6 : 7;
        d.bedrooms = Math.max(min, Math.min(max, b));
        if (d.bedrooms <= 2 && d.roof != RoofType.FLAT) d.floors = 1;
        else if (d.bedrooms >= 5) d.floors = Math.max(2, d.floors);
        if (d.floors < 2) d.balcony = false;
    }

    /** Largest footprint (width along the street, depth) for a design's bedroom count. */
    static float maxWidth(Design d) { return (d.floors >= 2 ? 6.5f : 7.5f) + d.bedrooms * (d.floors >= 2 ? 0.95f : 1.4f); }
    static float maxDepth(Design d) { return (d.floors >= 2 ? 6.5f : 7f) + d.bedrooms * (d.floors >= 2 ? 0.75f : 1.0f); }

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
                walls = new HouseTextures.Material[]{HouseTextures.Material.SIDING, HouseTextures.Material.STUCCO, HouseTextures.Material.BRICK, HouseTextures.Material.TILE, HouseTextures.Material.STONE};
                break;
            case 2:
                d.roof = RoofType.HIP;
                d.pitch = 0.38f + r.nextFloat() * 0.18f;
                walls = new HouseTextures.Material[]{HouseTextures.Material.SIDING, HouseTextures.Material.STUCCO, HouseTextures.Material.TILE, HouseTextures.Material.STONE};
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
        bedrooms(d, r, 1, 7);
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
        bedrooms(d, r, 2, 7);
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
        float top;               // wall top (eave line) height
        float roofHigh;          // highest point of its roof where it meets another mass
        /** Sides that abut another mass (0 front v0, 1 back v1, 2 left u0, 3 right u1): no eave there. */
        final boolean[] abut = new boolean[4];

        Mass(float u0, float v0, float u1, float v1, int floors, RoofType roof, boolean ridgeU) {
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
            this.floors = floors; this.roof = roof; this.ridgeU = ridgeU;
        }

        boolean contains(float u, float v) { return u > u0 + 0.05f && u < u1 - 0.05f && v > v0 + 0.05f && v < v1 - 0.05f; }
    }

    /** Height of the ground floor above the lot base (the visible concrete foundation). */
    static final float PLINTH = 0.4f;
    /** Japanese building module (one ken is two of these): openings are planned on this grid. */
    static final float MODULE = 0.91f;

    /** Footprint of the house inside the lot (local u0, v0, u1, v1) and the carport's u range (or NaN). */
    static float[] layout(Design d, float W, float D, boolean lot) {
        if (!lot) {
            float w = Math.min(W, maxWidth(d)), dd = Math.min(D, maxDepth(d));
            float u0 = (W - w) * 0.5f;
            return new float[]{u0, 0f, u0 + w, dd, Float.NaN, Float.NaN};
        }
        // never thinner than a real house: at least 7 m wide and deep where the lot allows
        float w = Math.min(W - 1.2f, Math.max(7f, W * d.widthFrac)), dd = Math.min(D - 3f, Math.max(7f, D * d.depthFrac));
        w = Math.min(w, maxWidth(d));
        dd = Math.min(dd, maxDepth(d));
        float carW = d.carport && W - w > 3.4f ? Math.min(3.2f, W - w - 0.6f) : 0f;
        float u0;
        if (carW > 0f) u0 = d.leftDoor ? W - w - 0.6f : 0.6f;
        else u0 = (W - w) * 0.5f;
        float v0 = Math.min(5f, Math.max(2.5f, 2.5f + (D - dd) * 0.08f));   // near the street, garden behind
        float cu0 = Float.NaN, cu1 = Float.NaN;
        if (carW > 0f) { cu0 = d.leftDoor ? 0.3f : W - carW - 0.3f; cu1 = cu0 + carW; }
        return new float[]{u0, v0, u0 + w, Math.min(D - 1f, v0 + dd), cu0, cu1};
    }

    /**
     * Builds design {@code d} on a lot (world rectangle) whose front faces {@code face}
     * (0=N, 1=S, 2=W, 3=E). With {@code lot} the house sits inside the lot with a garden, carport
     * and fence; otherwise it fills the rectangle.
     *
     * <p>The house is assembled from modules the way Japanese houses are: a main two-storey (or
     * single-storey) block on a visible concrete foundation, optionally with a one-storey
     * lean-to (geya) wing against its front, back or side; walls laid out on the 910 mm module
     * with real openings (windows, full-height garden windows, small frosted windows, the
     * entrance door) and the wall built round them; a roof (gable, hip, irimoya, mono-pitch or
     * flat with a parapet) with overhanging eaves, soffits, fascia boards, gutters and downpipes;
     * an entrance canopy and step; small canopies over traditional windows; a floor band between
     * storeys on two-tone houses; and a balcony built on the same bay as its door.</p>
     */
    void build(Design d, float x0, float z0, float x1, float z1, int face, float base, boolean lot, boolean westNeighbor) {
        lx0 = x0; lz0 = z0; lx1 = x1; lz1 = z1;
        this.face = face;
        float W = face < 2 ? x1 - x0 : z1 - z0, D = face < 2 ? z1 - z0 : x1 - x0;
        float[] lay = layout(d, W, D, lot);
        float u0 = lay[0], v0 = lay[1], u1 = lay[2], v1 = lay[3];
        float w = u1 - u0, dep = v1 - v0;
        float floor0 = base + PLINTH;
        boolean ridgeU = w >= dep * 0.85f;
        List<Mass> masses = new ArrayList<>();
        // the main block, then at most one lean-to wing that only touches it (never overlaps)
        Plan plan = d.plan;
        if ((plan == Plan.L_FRONT || plan == Plan.L_BACK) && (dep < 9f || w < 8f)) plan = Plan.RECT;
        if ((plan == Plan.ANNEX || plan == Plan.UPPER_PART || plan == Plan.OFFSET) && w < 9.5f) plan = Plan.RECT;
        Mass main;
        Mass wing = null;
        switch (plan) {
            case L_FRONT: {
                float fd = Math.min(dep * 0.32f, 3.6f), ww = Math.max(3.6f, w * d.wingFrac);
                main = new Mass(u0, v0 + fd, u1, v1, d.floors, d.roof, ridgeU);
                wing = d.leftDoor ? new Mass(u1 - ww, v0, u1, v0 + fd, 1, RoofType.SHED, true)
                        : new Mass(u0, v0, u0 + ww, v0 + fd, 1, RoofType.SHED, true);
                wing.shedHigh = 1; wing.abut[1] = true; main.abut[0] = false;
                break;
            }
            case L_BACK: {
                float bd = Math.min(dep * 0.35f, 4f), ww = Math.max(3.6f, w * d.wingFrac);
                main = new Mass(u0, v0, u1, v1 - bd, d.floors, d.roof, ridgeU);
                wing = d.leftDoor ? new Mass(u0, v1 - bd, u0 + ww, v1, 1, RoofType.SHED, true)
                        : new Mass(u1 - ww, v1 - bd, u1, v1, 1, RoofType.SHED, true);
                wing.shedHigh = 0; wing.abut[0] = true;
                break;
            }
            case ANNEX: case UPPER_PART: case OFFSET: {
                float aw = Math.min(w * 0.32f, 4.2f);
                boolean left = !d.leftDoor;
                main = left ? new Mass(u0 + aw, v0, u1, v1, d.floors, d.roof, (w - aw) >= dep * 0.85f)
                        : new Mass(u0, v0, u1 - aw, v1, d.floors, d.roof, (w - aw) >= dep * 0.85f);
                float inset = plan == Plan.OFFSET ? 1.2f : 0f;
                wing = left ? new Mass(u0, v0 + inset, u0 + aw, v1, 1, RoofType.SHED, false)
                        : new Mass(u1 - aw, v0 + inset, u1, v1, 1, RoofType.SHED, false);
                wing.shedHigh = left ? 3 : 2;
                wing.abut[left ? 3 : 2] = true;
                break;
            }
            default:
                main = new Mass(u0, v0, u1, v1, d.floors, d.roof, ridgeU);
                break;
        }
        if (d.roof == RoofType.SHED) main.shedHigh = d.shedBack ? 1 : 0;
        // no skinny towers: one storey per 3.5 m of the narrower side at most
        float narrow = Math.min(main.u1 - main.u0, main.v1 - main.v0);
        main.floors = Math.max(1, Math.min(main.floors, (int) (narrow / 3.5f)));
        main.top = floor0 + main.floors * d.floorH;
        main.roofHigh = main.top + 4f;
        masses.add(main);
        if (wing != null) {
            // the lean-to's high side tucks under the main block's eaves or its upper-floor windows
            float high = main.floors >= 2 ? floor0 + d.floorH + 0.45f : main.top - 0.25f;
            float run = wing.shedHigh < 2 ? wing.v1 - wing.v0 : wing.u1 - wing.u0;
            float pitch = Math.min(d.pitch, 0.35f);
            wing.top = Math.max(floor0 + 2.3f, high - pitch * run);   // wall top at the low side
            wingPitch = Math.max(0.08f, (high - wing.top) / Math.max(1f, run));
            wing.roofHigh = wing.top + wingPitch * run;
            masses.add(wing);
        }
        // trims for this house
        trimTex = lib.fascia[Math.floorMod(d.id * 7 + 3, lib.fascia.length)];
        gutterTex = lib.gutter[Math.floorMod(d.id * 5 + 1, lib.gutter.length)];
        for (Mass m : masses) massGeometry(d, m, m == main, masses, floor0, base);
        extras(d, main, base, lot, W, D, lay, westNeighbor);
        for (Mass m : masses) {
            float ax = Math.min(wx(m.u0, m.v0), wx(m.u1, m.v1)), bx = Math.max(wx(m.u0, m.v0), wx(m.u1, m.v1));
            float az = Math.min(wz(m.u0, m.v0), wz(m.u1, m.v1)), bz = Math.max(wz(m.u0, m.v0), wz(m.u1, m.v1));
            ch.boxes.add(new Box(ax, base - 1.5f, az, bx, m.top + 1.5f, bz));
        }
    }

    private float wingPitch;
    private int trimTex, gutterTex;
    /** The front upper-floor opening the balcony is built on: u range, or NaN if none. */
    private float balconyU0 = Float.NaN, balconyU1 = Float.NaN;

    // ------------------------------------------------------------------ geometry helpers

    /**
     * A polygon of 3 or 4 points given in local (u, y, v); its winding is fixed so that it faces
     * the local direction (nu, ny, nv), and it is lit from that normal.
     */
    private void poly(float[] q, float[] uv, int tex, float nu, float ny, float nv, float bias, int flags) {
        int n = q.length / 3;
        float[] p = new float[n * 3];
        for (int i = 0; i < n; i++) {
            p[i * 3] = wx(q[i * 3], q[i * 3 + 2]);
            p[i * 3 + 1] = q[i * 3 + 1];
            p[i * 3 + 2] = wz(q[i * 3], q[i * 3 + 2]);
        }
        float wnx = wx(nu, nv) - wx(0f, 0f), wnz = wz(nu, nv) - wz(0f, 0f);
        float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
        float bx = p[6] - p[0], by = p[7] - p[1], bz = p[8] - p[2];
        float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        if (cx * wnx + cy * ny + cz * wnz < 0f) {
            // reverse the winding (and the uvs with it)
            float[] rp = new float[p.length], ruv = new float[uv.length];
            for (int i = 0; i < n; i++) {
                int j = n - 1 - i;
                rp[i * 3] = p[j * 3]; rp[i * 3 + 1] = p[j * 3 + 1]; rp[i * 3 + 2] = p[j * 3 + 2];
                ruv[i * 2] = uv[j * 2]; ruv[i * 2 + 1] = uv[j * 2 + 1];
            }
            p = rp;
            uv = ruv;
        }
        float nl = (float) Math.sqrt(wnx * wnx + ny * ny + wnz * wnz);
        float lt = GeoBuilder.sunLight(wnx / nl, ny / nl, wnz / nl);
        float[] l = new float[n];
        java.util.Arrays.fill(l, lt);
        gb.quad(p, uv, l, tex, L_OBJECT, flags, bias, false);
    }

    /**
     * A vertical wall rectangle on the local line from (ua, va) to (ub, vb), from y0 to y1, facing
     * outward normal (nu, nv); u texture coordinate measured from {@code s0} metres along the face.
     */
    private void wallRect(float ua, float va, float ub, float vb, float y0, float y1, float nu, float nv,
                          int tex, float s0, float bias, int flags) {
        float len = (float) Math.hypot(ub - ua, vb - va);
        if (len < 0.01f || y1 - y0 < 0.01f) return;
        float sc = 0.5f;                       // wall materials tile every 2 m
        poly(new float[]{ua, y0, va, ub, y0, vb, ub, y1, vb, ua, y1, va},
                new float[]{s0 * sc, -y0 * sc, (s0 + len) * sc, -y0 * sc, (s0 + len) * sc, -y1 * sc, s0 * sc, -y1 * sc},
                tex, nu, 0f, nv, bias, flags);
    }

    /** An axis-aligned (local) box from u0..u1, v0..v1, y0..y1: four sides and a top. */
    private void localBox(float u0, float v0, float u1, float v1, float y0, float y1, int side, int top, float bias) {
        wallRect(u0, v0, u1, v0, y0, y1, 0f, -1f, side, 0f, bias, Geo.FLAG_NO_SUBDIV);
        wallRect(u1, v1, u0, v1, y0, y1, 0f, 1f, side, 0f, bias, Geo.FLAG_NO_SUBDIV);
        wallRect(u0, v1, u0, v0, y0, y1, -1f, 0f, side, 0f, bias, Geo.FLAG_NO_SUBDIV);
        wallRect(u1, v0, u1, v1, y0, y1, 1f, 0f, side, 0f, bias, Geo.FLAG_NO_SUBDIV);
        if (top >= 0) poly(new float[]{u0, y1, v0, u1, y1, v0, u1, y1, v1, u0, y1, v1},
                new float[]{0, 0, (u1 - u0) * 0.5f, 0, (u1 - u0) * 0.5f, (v1 - v0) * 0.5f, 0, (v1 - v0) * 0.5f},
                top, 0f, 1f, 0f, bias, Geo.FLAG_NO_SUBDIV);
    }

    // ------------------------------------------------------------------ openings

    /** An opening in a wall: kind (Sectors.WINDOW, SMALL, GARDEN, DOOR), along-face s0..s1, height y0..y1 above the floor. */
    private static final class Opening {
        byte kind;
        float s0, s1, y0, y1;
        int tex;
        boolean canopy;
    }

    /**
     * Plans the openings of one storey of one face on the 910 mm module: role 0 front, 1 back,
     * 2 side; returns them in order along the face (s measured from the face's start corner).
     */
    private List<Opening> plan(Design d, boolean main, int role, int storey, float len, boolean doorHere, boolean balconyHere) {
        List<Opening> out = new ArrayList<>();
        int mods = (int) (len / MODULE);
        if (mods < 2) return out;
        float margin = (len - mods * MODULE) * 0.5f;
        Random q = new Random(d.id * 131L + role * 17L + storey * 5L + mods);
        int win = lib.houseWin[d.win], small = lib.houseWin[d.winSmall], garden = lib.houseWin[d.winGarden];
        boolean[] used = new boolean[mods];
        // the entrance: two modules at the door end of the front (one if the door is narrow)
        if (doorHere) {
            boolean wide = lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_LATTICE || lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_GLASS;
            int dm = wide ? 2 : 1;
            int at = d.leftDoor ? mods - 1 - dm : 1;
            at = Math.max(0, Math.min(mods - dm, at));
            Opening o = new Opening();
            o.kind = Sectors.DOOR;
            o.tex = lib.houseDoor[d.door];
            o.s0 = margin + at * MODULE + (wide ? 0.12f : 0.0f);
            o.s1 = margin + (at + dm) * MODULE - (wide ? 0.12f : 0.0f);
            if (!wide) { float c = (o.s0 + o.s1) * 0.5f; o.s0 = c - 0.46f; o.s1 = c + 0.46f; }
            o.y0 = 0f; o.y1 = 2.1f;
            out.add(o);
            for (int k = at; k < at + dm; k++) used[k] = true;
            if (at > 0) used[at - 1] = true;
            if (at + dm < mods) used[at + dm] = true;
        }
        // the balcony door: two modules in the middle of the front upper floor
        if (balconyHere && mods >= 4) {
            int at = mods / 2 - 1;
            Opening o = new Opening();
            o.kind = Sectors.GARDEN;
            o.tex = garden;
            o.s0 = margin + at * MODULE + 0.08f;
            o.s1 = margin + (at + 2) * MODULE - 0.08f;
            o.y0 = 0.05f; o.y1 = 2.0f;
            out.add(o);
            for (int k = Math.max(0, at - 1); k < Math.min(mods, at + 3); k++) used[k] = true;
        }
        // windows: two-module windows with at least one module of wall between them; sides get
        // fewer, smaller ones; the back of the ground floor gets full-height garden windows
        int k = 1;
        while (k < mods - 1) {
            if (used[k]) { k++; continue; }
            float roll = q.nextFloat();
            boolean two = k + 1 < mods - 1 && !used[k + 1] && (role != 2 || roll < 0.25f);
            Opening o = new Opening();
            if (role == 2) {
                if (roll > 0.55f) { k += 1; continue; }
                o.kind = roll < 0.3f ? Sectors.SMALL : Sectors.WINDOW;
            } else if (role == 1 && storey == 0 && two && roll < 0.55f) {
                o.kind = Sectors.GARDEN;
            } else if (roll < 0.14f) {
                o.kind = Sectors.SMALL;
                two = false;
            } else o.kind = Sectors.WINDOW;
            int span = o.kind == Sectors.SMALL ? 1 : (two ? 2 : 1);
            if (o.kind == Sectors.WINDOW && span == 1 && role != 2 && q.nextFloat() < 0.5f) { k++; continue; }
            float inset = o.kind == Sectors.SMALL ? 0.2f : 0.1f;
            o.s0 = margin + k * MODULE + inset;
            o.s1 = margin + (k + span) * MODULE - inset;
            switch (o.kind) {
                case Sectors.SMALL: o.y0 = 1.2f; o.y1 = 1.8f; o.tex = small; break;
                case Sectors.GARDEN: o.y0 = 0.05f; o.y1 = 2.0f; o.tex = garden; break;
                default: o.y0 = 2.0f - d.winH; o.y1 = 2.0f; o.tex = win; break;
            }
            o.canopy = d.hisashi && o.kind != Sectors.SMALL;
            out.add(o);
            k += span + 1;
        }
        out.sort((a, b) -> Float.compare(a.s0, b.s0));
        return out;
    }

    /**
     * One storey band of a face, in two layers: the wall as one polygon across the whole band,
     * then each window and door laid over it as one polygon a centimetre proud of the wall
     * (painted with its frame, glass and the shadow of its reveal) — instead of building the wall
     * round every opening out of small pieces.
     */
    private void storeyFace(Design d, float ua, float va, float ub, float vb, float nu, float nv, float y0, float y1,
                            int mat, List<Opening> ops, float sOff, float clip0, float clip1) {
        float len = (float) Math.hypot(ub - ua, vb - va);
        float tu = (ub - ua) / len, tv = (vb - va) / len;
        wallRect(ua + tu * clip0, va + tv * clip0, ua + tu * clip1, va + tv * clip1, y0, y1, nu, nv, mat, sOff + clip0, 0f, 0);
        float off = 0.012f;
        for (Opening o : ops) {
            if (o.s0 < clip0 || o.s1 > clip1) continue;
            float oy0 = y0 + o.y0, oy1 = Math.min(y1, y0 + o.y1);
            float pa = ua + tu * o.s0 + nu * off, qa = va + tv * o.s0 + nv * off;
            float pb = ua + tu * o.s1 + nu * off, qb = va + tv * o.s1 + nv * off;
            poly(new float[]{pa, oy0, qa, pb, oy0, qb, pb, oy1, qb, pa, oy1, qa}, new float[]{0, 1, 1, 1, 1, 0, 0, 0},
                    o.tex, nu, 0f, nv, -0.03f, Geo.FLAG_NO_SUBDIV);
        }
    }

    // ------------------------------------------------------------------ one mass

    /**
     * Foundation, walls (round their openings, only where no other mass stands against them),
     * the floor band and the roof of one mass.
     */
    private void massGeometry(Design d, Mass m, boolean main, List<Mass> all, float floor0, float base) {
        // the four faces in order, walked anticlockwise seen from above; each with its outward normal
        float[][] faces = {
                {m.u1, m.v0, m.u0, m.v0, 0f, -1f},    // front (v0), walked right to left as seen from the street
                {m.u1, m.v1, m.u1, m.v0, 1f, 0f},     // right (u1)
                {m.u0, m.v1, m.u1, m.v1, 0f, 1f},     // back (v1)
                {m.u0, m.v0, m.u0, m.v1, -1f, 0f}};   // left (u0)
        int[] roles = {0, 2, 1, 2};
        int[] sideIdx = {0, 3, 1, 2};                 // index into Mass.abut
        int mat1 = lib.houseMat[d.mat1], mat2 = lib.houseMat[m.floors > 1 ? d.mat2 : d.mat1];
        for (int fi = 0; fi < 4; fi++) {
            float[] f = faces[fi];
            float ua = f[0], va = f[1], ub = f[2], vb = f[3], nu = f[4], nv = f[5];
            float len = (float) Math.hypot(ub - ua, vb - va);
            // the part of this face another mass stands against (hidden up to that mass's wall top)
            float hid0 = Float.NaN, hid1 = Float.NaN, hidTop = 0f;
            for (Mass o : all) {
                if (o == m) continue;
                float[] cov = coverage(m, fi, o);
                if (cov != null) { hid0 = cov[0]; hid1 = cov[1]; hidTop = o.roofHigh; }
            }
            // foundation: from the ground up to the floor, following the slope
            foundationWall(ua, va, ub, vb, nu, nv, floor0, hid0, hid1, len);
            for (int st = 0; st < m.floors; st++) {
                float y0 = floor0 + st * d.floorH, y1 = y0 + d.floorH;
                boolean doorHere = main && fi == 0 && st == 0;
                boolean balc = main && fi == 0 && st == 1 && d.balcony;
                List<Opening> ops = plan(d, main, roles[fi], st, len, doorHere, balc);
                if (balc) for (Opening o : ops) if (o.kind == Sectors.GARDEN) {
                    // remember where the balcony door is: the balcony is built in front of it
                    float a = Math.min(o.s0, o.s1), b = Math.max(o.s0, o.s1);
                    balconyU0 = ua + (ub - ua) / len * b;
                    balconyU1 = ua + (ub - ua) / len * a;
                    if (balconyU0 > balconyU1) { float t = balconyU0; balconyU0 = balconyU1; balconyU1 = t; }
                }
                int mat = st == 0 ? mat1 : mat2;
                if (!Float.isNaN(hid0) && y1 <= hidTop + 0.01f) {
                    // part of the band is covered by the other mass: wall only either side of it
                    List<Opening> keep = new ArrayList<>();
                    for (Opening o : ops) if (o.s1 <= hid0 - 0.2f || o.s0 >= hid1 + 0.2f) keep.add(o);
                    if (hid0 > 0.01f) storeyFace(d, ua, va, ub, vb, nu, nv, y0, y1, mat, keep, 0f, 0f, hid0);
                    if (hid1 < len - 0.01f) storeyFace(d, ua, va, ub, vb, nu, nv, y0, y1, mat, keep, 0f, hid1, len);
                } else storeyFace(d, ua, va, ub, vb, nu, nv, y0, y1, mat, ops, 0f, 0f, len);
            }
            // floor band between the storeys on two-tone houses
            if (m.floors > 1 && d.mat1 != d.mat2 && Float.isNaN(hid0)) {
                float y = floor0 + d.floorH;
                float o = 0.035f;
                wallRect(ua + nu * o, va + nv * o, ub + nu * o, vb + nv * o, y - 0.06f, y + 0.08f, nu, nv, trimTex, 0f, -0.04f, Geo.FLAG_NO_SUBDIV);
                poly(new float[]{ua, y + 0.08f, va, ub, y + 0.08f, vb, ub + nu * o, y + 0.08f, vb + nv * o, ua + nu * o, y + 0.08f, va + nv * o},
                        new float[]{0, 0, len * 0.5f, 0, len * 0.5f, 0.1f, 0, 0.1f}, trimTex, 0f, 1f, 0f, -0.04f, Geo.FLAG_NO_SUBDIV);
            }
        }
        roof(d, m, main);
        // the entrance: canopy over the door and a concrete step
        if (main && d.porch) entrance(d, m, floor0);
    }

    /**
     * Where mass {@code o} stands against face {@code fi} of mass {@code m}: the along-face s range
     * {s0, s1} measured from the face's start corner, or null.
     */
    private static float[] coverage(Mass m, int fi, Mass o) {
        float e = 0.02f;
        float a0, a1, b0, b1;
        switch (fi) {
            case 0: if (Math.abs(o.v1 - m.v0) > e) return null; a0 = Math.max(m.u0, o.u0); a1 = Math.min(m.u1, o.u1); if (a1 - a0 < 0.1f) return null; return new float[]{m.u1 - a1, m.u1 - a0};
            case 1: if (Math.abs(o.u0 - m.u1) > e) return null; b0 = Math.max(m.v0, o.v0); b1 = Math.min(m.v1, o.v1); if (b1 - b0 < 0.1f) return null; return new float[]{m.v1 - b1, m.v1 - b0};
            case 2: if (Math.abs(o.v0 - m.v1) > e) return null; a0 = Math.max(m.u0, o.u0); a1 = Math.min(m.u1, o.u1); if (a1 - a0 < 0.1f) return null; return new float[]{a0 - m.u0, a1 - m.u0};
            default: if (Math.abs(o.u1 - m.u0) > e) return null; b0 = Math.max(m.v0, o.v0); b1 = Math.min(m.v1, o.v1); if (b1 - b0 < 0.1f) return null; return new float[]{b0 - m.v0, b1 - m.v0};
        }
    }

    /** Concrete foundation from the ground (each end follows the terrain) up to the floor. */
    private void foundationWall(float ua, float va, float ub, float vb, float nu, float nv, float floor0, float hid0, float hid1, float len) {
        float tu = (ub - ua) / len, tv = (vb - va) / len;
        float[][] spans = Float.isNaN(hid0) ? new float[][]{{0f, len}} : new float[][]{{0f, hid0}, {hid1, len}};
        for (float[] sp : spans) {
            if (sp[1] - sp[0] < 0.02f) continue;
            int n = Math.max(1, (int) Math.ceil((sp[1] - sp[0]) / 3f));
            for (int k = 0; k < n; k++) {
                float s0 = sp[0] + (sp[1] - sp[0]) * k / n, s1 = sp[0] + (sp[1] - sp[0]) * (k + 1) / n;
                float pu = ua + tu * s0, pv = va + tv * s0, qu = ua + tu * s1, qv = va + tv * s1;
                float g0 = gb.h(wx(pu, pv), wz(pu, pv)) - 0.25f, g1 = gb.h(wx(qu, qv), wz(qu, qv)) - 0.25f;
                poly(new float[]{pu, g0, pv, qu, g1, qv, qu, floor0, qv, pu, floor0, pv},
                        new float[]{s0 * 0.5f, 1f, s1 * 0.5f, 1f, s1 * 0.5f, 0f, s0 * 0.5f, 0f},
                        lib.foundation, nu, 0f, nv, 0f, Geo.FLAG_NO_SUBDIV);
            }
        }
    }

    // ------------------------------------------------------------------ roofs

    /** A roof plane point list in (a, b) roof coordinates to local (u, y, v) with y = A + Ba a + Bb b. */
    private float[] roofPts(boolean ru, float[] ab, float A, float Ba, float Bb, float drop) {
        int n = ab.length / 2;
        float[] q = new float[n * 3];
        for (int i = 0; i < n; i++) {
            float a = ab[i * 2], b = ab[i * 2 + 1];
            q[i * 3] = ru ? a : b;
            q[i * 3 + 1] = A + Ba * a + Bb * b - drop;
            q[i * 3 + 2] = ru ? b : a;
        }
        return q;
    }

    /** One sloping roof surface: covering on top, soffit boards underneath. */
    private void roofFace(boolean ru, float[] ab, float A, float Ba, float Bb, int tex, boolean underside) {
        float[] q = roofPts(ru, ab, A, Ba, Bb, 0f);
        int n = ab.length / 2;
        float[] uv = new float[n * 2];
        // roofing runs down the slope: v along the fall line, u across it
        float gl = (float) Math.hypot(Ba, Bb);
        float fa = gl > 1e-4f ? Ba / gl : 0f, fb = gl > 1e-4f ? Bb / gl : 1f;
        for (int i = 0; i < n; i++) {
            float a = ab[i * 2], b = ab[i * 2 + 1];
            float along = a * fb - b * fa, down = a * fa + b * fb;
            uv[i * 2] = along * 0.5f;
            uv[i * 2 + 1] = -down * 0.5f * (float) Math.sqrt(1f + gl * gl);
        }
        // the outward normal of the plane y = A + Ba a + Bb b is (-Ba, 1, -Bb) in (a, y, b)
        float nu = ru ? -Ba : -Bb, nv = ru ? -Bb : -Ba;
        poly(q, uv, tex, nu, 1f, nv, 0f, 0);
        if (underside) {
            float[] u2 = roofPts(ru, ab, A, Ba, Bb, 0.16f);
            poly(u2, uv, lib.soffit, -nu, -1f, -nv, 0f, 0);
        }
    }

    /** Fascia board along a roof edge from (a0,b0) to (a1,b1) in roof coordinates, facing out (oa, ob). */
    private void fascia(boolean ru, float a0, float b0, float a1, float b1, float A, float Ba, float Bb, float oa, float ob, boolean gutter) {
        float y0 = A + Ba * a0 + Bb * b0, y1 = A + Ba * a1 + Bb * b1;
        float u0 = ru ? a0 : b0, v0 = ru ? b0 : a0, u1 = ru ? a1 : b1, v1 = ru ? b1 : a1;
        float nu = ru ? oa : ob, nv = ru ? ob : oa;
        float len = (float) Math.hypot(u1 - u0, v1 - v0);
        poly(new float[]{u0, y0 - 0.2f, v0, u1, y1 - 0.2f, v1, u1, y1 + 0.03f, v1, u0, y0 + 0.03f, v0},
                new float[]{0, 1, len * 0.5f, 1, len * 0.5f, 0, 0, 0}, trimTex, nu, 0f, nv, -0.03f, Geo.FLAG_NO_SUBDIV);
        if (gutter && DETAILED_EAVES) {
            // half-round gutter hung under the eave: front and bottom
            float g = 0.11f;
            float gu0 = u0 + nu * g, gv0 = v0 + nv * g, gu1 = u1 + nu * g, gv1 = v1 + nv * g;
            poly(new float[]{gu0, y0 - 0.2f, gv0, gu1, y1 - 0.2f, gv1, gu1, y1 - 0.06f, gv1, gu0, y0 - 0.06f, gv0},
                    new float[]{0, 1, len, 1, len, 0, 0, 0}, gutterTex, nu, 0f, nv, -0.06f, Geo.FLAG_NO_SUBDIV);
            poly(new float[]{u0, y0 - 0.2f, v0, u1, y1 - 0.2f, v1, gu1, y1 - 0.2f, gv1, gu0, y0 - 0.2f, gv0},
                    new float[]{0, 0, len, 0, len, 1, 0, 1}, gutterTex, 0f, -1f, 0f, -0.06f, Geo.FLAG_NO_SUBDIV);
        }
    }

    /** Downpipe from an eave corner (local u, v, just off the wall) down to the ground. */
    /** Gutters hung under the eaves (2 polygons an edge): off, the fascia board alone reads as the edge. */
    private static final boolean DETAILED_EAVES = false;

    private void downpipe(float u, float v, float top) {
        float g = gb.h(wx(u, v), wz(u, v)) - 0.1f;
        localBox(u - 0.04f, v - 0.04f, u + 0.04f, v + 0.04f, g, top, gutterTex, -1, -0.07f);
    }

    /** The roof of a mass with its eaves (overhang, soffit, fascia, gutters) and downpipes. */
    private void roof(Design d, Mass m, boolean main) {
        int rt = lib.houseRoof[d.roofTex];
        float top = m.top, o = m.roof == RoofType.FLAT ? 0f : d.overhang, og = Math.min(0.45f, d.overhang);
        switch (m.roof) {
            case FLAT: {
                // parapet round a membrane roof
                float t = 0.22f, h = top + 0.55f;
                int trim = lib.houseMat[d.trim];
                poly(new float[]{m.u0, top + 0.05f, m.v0, m.u1, top + 0.05f, m.v0, m.u1, top + 0.05f, m.v1, m.u0, top + 0.05f, m.v1},
                        new float[]{0, 0, (m.u1 - m.u0) * 0.5f, 0, (m.u1 - m.u0) * 0.5f, (m.v1 - m.v0) * 0.5f, 0, (m.v1 - m.v0) * 0.5f},
                        lib.roofMembrane, 0f, 1f, 0f, 0f, 0);
                // the parapet: the walls carried on up (outside), and their inner faces
                wallRect(m.u1, m.v0, m.u0, m.v0, top, h, 0f, -1f, trim, 0f, 0f, 0);
                wallRect(m.u0, m.v1, m.u1, m.v1, top, h, 0f, 1f, trim, 0f, 0f, 0);
                wallRect(m.u0, m.v0, m.u0, m.v1, top, h, -1f, 0f, trim, 0f, 0f, 0);
                wallRect(m.u1, m.v1, m.u1, m.v0, top, h, 1f, 0f, trim, 0f, 0f, 0);
                wallRect(m.u0, m.v0 + t, m.u1, m.v0 + t, top, h, 0f, 1f, trim, 0f, 0f, 0);
                wallRect(m.u1, m.v1 - t, m.u0, m.v1 - t, top, h, 0f, -1f, trim, 0f, 0f, 0);
                wallRect(m.u0 + t, m.v1, m.u0 + t, m.v0, top, h, 1f, 0f, trim, 0f, 0f, 0);
                wallRect(m.u1 - t, m.v0, m.u1 - t, m.v1, top, h, -1f, 0f, trim, 0f, 0f, 0);
                break;
            }
            case SHED: {
                // mono-pitch: high on one side, the side walls rise to meet it
                float p = m == null ? d.pitch : (main ? d.pitch * 0.7f : wingPitch);
                boolean alongV = m.shedHigh < 2;              // the fall runs along v
                float lo0 = alongV ? m.v0 : m.u0, lo1 = alongV ? m.v1 : m.u1;
                float run = lo1 - lo0;
                boolean highAtStart = m.shedHigh == 0 || m.shedHigh == 2;
                // plane: y = top at the low wall, rising toward the high side
                float slope = highAtStart ? -p : p;
                float A = highAtStart ? top + p * lo1 : top - p * lo0;
                // roof coordinates: a across the fall (u when alongV), b along the fall
                boolean ru = alongV;
                float a0 = alongV ? m.u0 : m.v0, a1 = alongV ? m.u1 : m.v1;
                boolean abutHigh = highAtStart ? (alongV ? m.abut[0] : m.abut[2]) : (alongV ? m.abut[1] : m.abut[3]);
                float oLow = o, oHigh = abutHigh ? 0f : o * 0.6f, oSide = og;
                float b0 = lo0 - (highAtStart ? oHigh : oLow), b1 = lo1 + (highAtStart ? oLow : oHigh);
                boolean sideAbut0 = alongV ? m.abut[2] : m.abut[0], sideAbut1 = alongV ? m.abut[3] : m.abut[1];
                float ea0 = a0 - (sideAbut0 ? 0f : oSide), ea1 = a1 + (sideAbut1 ? 0f : oSide);
                roofFace(ru, new float[]{ea0, b0, ea1, b0, ea1, b1, ea0, b1}, A, 0f, slope, rt, true);
                // fascias: all free edges; gutter on the low edge
                float bLow = highAtStart ? b1 : b0, bHigh = highAtStart ? b0 : b1;
                float sLow = highAtStart ? 1f : -1f;
                fascia(ru, ea0, bLow, ea1, bLow, A, 0f, slope, 0f, sLow, true);
                if (!abutHigh) fascia(ru, ea1, bHigh, ea0, bHigh, A, 0f, slope, 0f, -sLow, false);
                if (!sideAbut0) fascia(ru, ea0, b1, ea0, b0, A, 0f, slope, -1f, 0f, false);
                if (!sideAbut1) fascia(ru, ea1, b0, ea1, b1, A, 0f, slope, 1f, 0f, false);
                // side walls rise from the eave line up under the slope (trapezoids)
                for (int e = 0; e < 2; e++) {
                    if (e == 0 ? sideAbut0 : sideAbut1) continue;
                    float a = e == 0 ? a0 : a1, s = e == 0 ? -1f : 1f;
                    float yLo = A + slope * lo0, yHi = A + slope * lo1;
                    float ua = ru ? a : lo0, va = ru ? lo0 : a, ub = ru ? a : lo1, vb = ru ? lo1 : a;
                    float nu = ru ? s : 0f, nv = ru ? 0f : s;
                    int mat = lib.houseMat[m.floors > 1 ? d.mat2 : d.mat1];
                    poly(new float[]{ua, top, va, ub, top, vb, ub, yHi, vb, ua, yLo, va},
                            new float[]{0, 0, run * 0.5f, 0, run * 0.5f, -(yHi - top) * 0.5f, 0, -(yLo - top) * 0.5f},
                            mat, nu, 0f, nv, 0f, 0);
                }
                // the high wall rises to the slope too
                if (!abutHigh) {
                    float yH = A + slope * (highAtStart ? lo0 : lo1);
                    float ua = ru ? a0 : (highAtStart ? lo0 : lo1), va = ru ? (highAtStart ? lo0 : lo1) : a0;
                    float ub = ru ? a1 : ua, vb = ru ? va : a1;
                    float nu = ru ? 0f : (highAtStart ? -1f : 1f), nv = ru ? (highAtStart ? -1f : 1f) : 0f;
                    wallRect(ua, va, ub, vb, top, yH, nu, nv, lib.houseMat[m.floors > 1 ? d.mat2 : d.mat1], 0f, 0f, 0);
                }
                break;
            }
            default: {
                boolean ru = m.ridgeU;
                float a0 = ru ? m.u0 : m.v0, a1 = ru ? m.u1 : m.v1, b0 = ru ? m.v0 : m.u0, b1 = ru ? m.v1 : m.u1;
                float p = d.pitch, half = (b1 - b0) * 0.5f, bm = (b0 + b1) * 0.5f;
                float A0 = a0 - og, A1 = a1 + og, B0 = b0 - o, B1 = b1 + o;
                float ridge = top + p * half;
                // front slope plane: y = top - p (b - b0) ... rising toward bm: y = top + p (b - b0) for b < bm
                float Af = top - p * b0, Ab = top + p * b1;      // planes y = Af + p b (front), y = Ab - p b (back)
                if (m.roof == RoofType.GABLE || (A1 - A0) < (B1 - B0) * 0.6f) {
                    roofFace(ru, new float[]{A0, B0, A1, B0, A1, bm, A0, bm}, Af, 0f, p, rt, true);
                    roofFace(ru, new float[]{A0, bm, A1, bm, A1, B1, A0, B1}, Ab, 0f, -p, rt, true);
                    // gable walls: triangles from the eave line up to the ridge
                    for (int e = 0; e < 2; e++) {
                        float a = e == 0 ? a0 : a1, s = e == 0 ? -1f : 1f;
                        int mat = lib.houseMat[m.floors > 1 ? d.mat2 : d.mat1];
                        float[] q = ru ? new float[]{a, top, b0, a, top, b1, a, ridge, bm} : new float[]{b0, top, a, b1, top, a, bm, ridge, a};
                        poly(q, new float[]{0, -top * 0.5f, (b1 - b0) * 0.5f, -top * 0.5f, half * 0.5f, -ridge * 0.5f},
                                mat, ru ? s : 0f, 0f, ru ? 0f : s, 0f, 0);
                    }
                    // fascias: eaves with gutters, barge boards up the verges
                    fascia(ru, A1, B0, A0, B0, Af, 0f, p, 0f, -1f, true);
                    fascia(ru, A0, B1, A1, B1, Ab, 0f, -p, 0f, 1f, true);
                    fascia(ru, A0, B0, A0, bm, Af, 0f, p, -1f, 0f, false);
                    fascia(ru, A0, bm, A0, B1, Ab, 0f, -p, -1f, 0f, false);
                    fascia(ru, A1, bm, A1, B0, Af, 0f, p, 1f, 0f, false);
                    fascia(ru, A1, B1, A1, bm, Ab, 0f, -p, 1f, 0f, false);
                } else {
                    // hip / irimoya: eaves all round at the same height
                    float e = top - p * o;
                    float hb = (B1 - B0) * 0.5f;
                    float rk = m.roof == RoofType.IRIMOYA ? hb * 0.55f : hb;   // how far the hip runs in
                    float yk = e + p * rk;
                    // end planes: y = Ae + p (a - A0) at the a0 end, rising inward
                    float Ae0 = e - p * A0, Ae1 = e + p * A1;
                    // front and back slopes (trapezoid up to the hip line, then the gable part to the ridge)
                    roofFace(ru, new float[]{A0, B0, A1, B0, A1 - rk, B0 + rk, A0 + rk, B0 + rk}, Af, 0f, p, rt, true);
                    roofFace(ru, new float[]{A1, B1, A0, B1, A0 + rk, B1 - rk, A1 - rk, B1 - rk}, Ab, 0f, -p, rt, true);
                    if (bm - (B0 + rk) > 0.02f) {
                        roofFace(ru, new float[]{A0 + rk, B0 + rk, A1 - rk, B0 + rk, A1 - rk, bm, A0 + rk, bm}, Af, 0f, p, rt, false);
                        roofFace(ru, new float[]{A1 - rk, B1 - rk, A0 + rk, B1 - rk, A0 + rk, bm, A1 - rk, bm}, Ab, 0f, -p, rt, false);
                    }
                    // the ends: hipped slopes
                    roofFace(ru, new float[]{A0, B1, A0, B0, A0 + rk, B0 + rk, A0 + rk, B1 - rk}, Ae0, p, 0f, rt, true);
                    roofFace(ru, new float[]{A1, B0, A1, B1, A1 - rk, B1 - rk, A1 - rk, B0 + rk}, Ae1, -p, 0f, rt, true);
                    if (m.roof == RoofType.IRIMOYA) {
                        // the small gable (tsuma) standing on each hip
                        float rTop = e + p * hb;
                        int mat = lib.houseMat[d.trim];
                        for (int k = 0; k < 2; k++) {
                            float a = k == 0 ? A0 + rk : A1 - rk, s = k == 0 ? -1f : 1f;
                            float[] q = ru ? new float[]{a, yk, B0 + rk, a, yk, B1 - rk, a, rTop, bm} : new float[]{B0 + rk, yk, a, B1 - rk, yk, a, bm, rTop, a};
                            poly(q, new float[]{0, 1, 1, 1, 0.5f, 0}, mat, ru ? s : 0f, 0f, ru ? 0f : s, 0f, 0);
                        }
                    }
                    // eave fascias with gutters on all four sides
                    fascia(ru, A1, B0, A0, B0, Af, 0f, p, 0f, -1f, true);
                    fascia(ru, A0, B1, A1, B1, Ab, 0f, -p, 0f, 1f, true);
                    fascia(ru, A0, B0, A0, B1, Ae0, p, 0f, -1f, 0f, true);
                    fascia(ru, A1, B1, A1, B0, Ae1, -p, 0f, 1f, 0f, true);
                    ridge = e + p * hb;
                }
                break;
            }
        }
    }

    /** A ridge cap: a narrow box along the ridge (roof coordinates a0..a1 at b = bm). */
    private void ridgeCap(boolean ru, float a0, float a1, float bm, float y, int tex) {
        if (a1 - a0 < 0.05f) return;
        float w = 0.13f, h = 0.12f;
        if (ru) localBox(a0, bm - w, a1, bm + w, y - 0.04f, y + h, tex, tex, -0.02f);
        else localBox(bm - w, a0, bm + w, a1, y - 0.04f, y + h, tex, tex, -0.02f);
    }

    /** Entrance canopy over the front door and a concrete step in front of it. */
    private void entrance(Design d, Mass m, float floor0) {
        float doorU = d.leftDoor ? m.u0 + 1.4f : m.u1 - 1.4f;
        // the door was planned on the module grid from the front face's start (u1, walking to u0)
        float len = m.u1 - m.u0;
        int mods = (int) (len / MODULE);
        float margin = (len - mods * MODULE) * 0.5f;
        boolean wide = lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_LATTICE || lib.houseDoorKind[d.door] == HouseTextures.Door.SLIDING_GLASS;
        int dm = wide ? 2 : 1;
        int at = Math.max(0, Math.min(mods - dm, d.leftDoor ? mods - 1 - dm : 1));
        float s0 = margin + at * MODULE, s1 = margin + (at + dm) * MODULE;
        float c = m.u1 - (s0 + s1) * 0.5f;
        doorU = c;
        float hw = dm * MODULE * 0.5f + 0.35f, dep = 0.95f, y = floor0 + 2.45f;
        float v = m.v0;
        // canopy: a thin sloping slab with a fascia and soffit
        poly(new float[]{doorU - hw, y + 0.1f, v, doorU + hw, y + 0.1f, v, doorU + hw, y, v - dep, doorU - hw, y, v - dep},
                new float[]{0, 0, hw, 0, hw, 0.5f, 0, 0.5f}, lib.canopyMetal, 0f, 1f, 0f, -0.1f, Geo.FLAG_NO_SUBDIV);
        poly(new float[]{doorU - hw, y - 0.08f, v, doorU + hw, y - 0.08f, v, doorU + hw, y - 0.08f, v - dep, doorU - hw, y - 0.08f, v - dep},
                new float[]{0, 0, 1, 0, 1, 1, 0, 1}, lib.soffit, 0f, -1f, 0f, -0.1f, Geo.FLAG_NO_SUBDIV);
        poly(new float[]{doorU - hw, y - 0.08f, v - dep, doorU + hw, y - 0.08f, v - dep, doorU + hw, y, v - dep, doorU - hw, y, v - dep},
                new float[]{0, 1, 1, 1, 1, 0, 0, 0}, trimTex, 0f, 0f, -1f, -0.1f, Geo.FLAG_NO_SUBDIV);
        // two steps up to the door (the floor is above the foundation)
        float g = gb.h(wx(doorU, v - 0.6f), wz(doorU, v - 0.6f));
        localBox(doorU - hw + 0.2f, v - 1.1f, doorU + hw - 0.2f, v, g - 0.2f, floor0 - 0.2f, lib.concrete, lib.concrete, -0.05f);
        localBox(doorU - hw + 0.2f, v - 0.55f, doorU + hw - 0.2f, v, g - 0.2f, floor0, lib.concrete, lib.concrete, -0.06f);
    }

    // ------------------------------------------------------------------ extras

    private void extras(Design d, Mass main, float base, boolean lot, float W, float D, float[] lay, boolean westNeighbor) {
        // a/c outdoor unit against the side wall (on a little stand), one more on the upper floor
        {
            float nx = wx(main.u0 - 1f, main.v0) - wx(main.u0, main.v0), nz = wz(main.u0 - 1f, main.v0) - wz(main.u0, main.v0);
            float v = main.v0 + Math.min(1.6f, (main.v1 - main.v0) * 0.3f);
            gb.wallUnit(wx(main.u0, v), wz(main.u0, v), nx, nz, 0.8f, base + 0.15f, 0.62f, 0.3f, lib.aircon, lib.airconSide);
            if (main.floors >= 2 && (d.id & 1) == 0) {
                float v2 = main.v0 + (main.v1 - main.v0) * 0.65f;
                gb.wallUnit(wx(main.u0, v2), wz(main.u0, v2), nx, nz, 0.8f, base + PLINTH + d.floorH + 0.3f, 0.62f, 0.3f, lib.aircon, lib.airconSide);
            }
        }
        // balcony on the upper floor of the front, built in front of the balcony door it serves
        if (d.balcony && main.floors >= 2 && !Float.isNaN(balconyU0)) {
            float bu0 = Math.max(main.u0 + 0.2f, balconyU0 - 0.55f), bu1 = Math.min(main.u1 - 0.2f, balconyU1 + 0.55f);
            float nx = wx(bu0, main.v0 - 1f) - wx(bu0, main.v0), nz = wz(bu0, main.v0 - 1f) - wz(bu0, main.v0);
            gb.balcony(wx(bu0, main.v0), wz(bu0, main.v0), wx(bu1, main.v0), wz(bu1, main.v0),
                    nx, nz, base + PLINTH + d.floorH, 1.0f, 1.05f, lib.balconyFront, lib.balconySlab);
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
        float fb = base - 0.15f;
        if (face == 0) {
            if (!westNeighbor) fencePlane(0f, 0.2f, 0f, D - 0.2f, fb, fb + 1.2f, lib.blockWall, 1.2f);
            fencePlane(W, 0.2f, W, D - 0.2f, fb, fb + 1.2f, lib.blockWall, 1.2f);
        } else if (face == 1) {
            fencePlane(0f, 0.2f, 0f, D - 0.2f, fb, fb + 1.2f, lib.blockWall, 1.2f);
            if (!westNeighbor) fencePlane(W, 0.2f, W, D - 0.2f, fb, fb + 1.2f, lib.blockWall, 1.2f);
        } else {
            fencePlane(0f, 0.2f, 0f, D - 0.2f, fb, fb + 1.2f, lib.blockWall, 1.2f);
            fencePlane(W, 0.2f, W, D - 0.2f, fb, fb + 1.2f, lib.blockWall, 1.2f);
        }
        fencePlane(0f, D - 0.2f, W, D - 0.2f, fb, fb + 1.2f, lib.blockWall, 1.2f);
        if (d.fence > 0) {
            int tex = d.fence == 1 ? lib.blockWall : lib.hedge;
            float h = d.fence == 1 ? 0.95f : 1.15f, t = d.fence == 1 ? 0.15f : 0.6f;
            float gap0 = Float.isNaN(lay[4]) ? (d.leftDoor ? lay[0] - 0.5f : lay[2] - 3f) : lay[4];
            float gap1 = Float.isNaN(lay[4]) ? gap0 + 3.5f : lay[5];
            if (!Float.isNaN(lay[4])) {
                if (d.leftDoor) gap0 = Math.min(gap0, lay[2] - 2f); else gap1 = Math.max(gap1, lay[0] + 2f);
            }
            gap0 = Math.max(0.3f, gap0); gap1 = Math.min(W - 0.3f, gap1);
            float front = 0.2f + t * 0.5f;
            float scale = d.fence == 1 ? 1.2f : 2f;
            if (gap0 > 0.6f) fencePlane(0f, front, gap0, front, fb, fb + h, tex, scale);
            if (gap1 < W - 0.6f) fencePlane(gap1, front, W, front, fb, fb + h, tex, scale);
        }
    }

    /**
     * A property wall or hedge from local (u0,v0) to (u1,v1), {@code y1 - y0} high, in tiles of
     * at most 2.5 m that follow the ground (never floating above a slope or buried in it).
     */
    private void fencePlane(float u0, float v0, float u1, float v1, float y0, float y1, int tex, float scale) {
        float ax = wx(u0, v0), az = wz(u0, v0), bx = wx(u1, v1), bz = wz(u1, v1);
        float dx = bx - ax, dz = bz - az, len = (float) Math.hypot(dx, dz);
        float hgt = y1 - y0;
        if (len < 0.05f || hgt < 0.05f) return;
        float nx = -dz / len, nz = dx / len;
        float light = GeoBuilder.sunLight(nx, 0f, nz);
        float[] l = {light, light, light, light};
        int n = Math.max(1, (int) Math.ceil(len / 2.5f));
        for (int k = 0; k < n; k++) {
            float t0 = k / (float) n, t1 = (k + 1) / (float) n;
            float px = ax + dx * t0, pz = az + dz * t0, qx = ax + dx * t1, qz = az + dz * t1;
            float g0 = gb.h(px, pz) - 0.15f, g1 = gb.h(qx, qz) - 0.15f;
            float[] p = {px, g0, pz, qx, g1, qz, qx, g1 + hgt + 0.15f, qz, px, g0 + hgt + 0.15f, pz};
            float s0 = len * t0 / scale, s1 = len * t1 / scale;
            float[] uv = {s0, (hgt + 0.15f) / scale, s1, (hgt + 0.15f) / scale, s1, 0f, s0, 0f};
            gb.quad(p, uv, l, tex, L_OBJECT, Geo.FLAG_NO_SUBDIV, 0f, true);
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
