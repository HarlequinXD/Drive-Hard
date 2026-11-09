package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;
import static com.harlequinxd.drivehard.core.tex.Img.rgb;

/**
 * All world textures, generated at startup. Base materials are 64x64 (= 2 x 2 m), facades
 * 64x128 (one bay x one storey), shopfronts 128x128.
 */
public final class TextureLibrary implements java.io.Serializable {
    public final List<Texture> list = new ArrayList<>();

    public final int asphalt, asphaltPatched, sidewalk, pavers, concrete, curb, grass, sand, water, roofGravel,
            roofMembrane, roofTar, parking, paintWhite, paintYellow, paintRed, trunk, frond, hedge, metal, pole, shadow,
            planter, lampHead, umbrella, rock, hillGrass;

    public final int[][] jp = new int[JpTextures.Kind.values().length][];
    public static final int SHOP_COUNT = 520;
    public final int[] shops;
    public final int[] shopCategory;
    public final int[] lobbies;
    public final int[] roofs, signs, vending, stationSigns, koban, fireDoors;
    /** Countryside: rice paddies (flooded, green, golden), cabbage, ploughed soil, tea, greenhouse vinyl, gravel, guardrail. */
    public final int paddyWater, paddyGreen, paddyGold, cabbage, soil, tea, vinyl, gravel, guardrail;
    /** Lane textures with their painted edge lines (see FarmTextures.lane). */
    /** City lane textures and junction mouths (stop lines, zebra crossings). */
    public final int laneStreet, laneAvInner, laneAvOuter, mouthStreet, mouthAvenue, mouthCountry, mouthHighway, mouthLane;
    public final int laneCountry, laneHwInner, laneHwOuter, laneHwMedian, laneFwInner, laneFwOuter, laneRamp, laneTown, walkStrip, mouthTown;
    public final int blockWall;
    /** The ground's tile set (terrain cells, transitions, asphalt). */
    public final TerrainTiles tiles;
    public final List<String> shopNames = new ArrayList<>();
    public static final String[] STATIONS = {"MINATO-CHUO", "SAKURAGAOKA", "HIGASHI-HAMA"};

    public TextureLibrary(long seed) { this(seed, false); }

    /** @param lowMemory phones: a third of the shopfront variants and half the facade variants */
    public TextureLibrary(long seed, boolean lowMemory) {
        // shopfronts are 256 x 128 layered paintings now: one of each kind on phones, three on desktop
        int shopCount = JpTextures.CATEGORIES.length * (lowMemory ? 1 : 3);
        shops = new int[shopCount];
        shopCategory = new int[shopCount];
        asphalt = add(asphalt(seed, false));
        asphaltPatched = add(asphalt(seed + 1, true));
        sidewalk = add(sidewalk(seed));
        pavers = add(pavers(seed));
        concrete = add(concrete(seed));
        curb = add(curb(seed));
        grass = add(grass(seed));
        sand = add(sand(seed));
        Texture w = water(seed);
        w.scrollU = 0.05f;
        water = add(w);
        roofGravel = add(roof(seed, 0));
        roofMembrane = add(roof(seed, 1));
        roofTar = add(roof(seed, 2));
        parking = add(ParkingTextures.bay(seed, 0));
        parkingBays = new int[]{parking, add(ParkingTextures.bay(seed, 1)), add(ParkingTextures.bay(seed, 2)), add(ParkingTextures.bay(seed, 3))};
        parkingAisle = add(ParkingTextures.aisle(seed));
        paintWhite = add(solid(0xFFEDEDE6, seed));
        paintYellow = add(solid(0xFFE2B33A, seed));
        paintRed = add(solid(0xFFC0282A, seed));
        trunk = add(trunk(seed));
        frond = add(frond(seed));
        hedge = add(hedge(seed));
        metal = add(metal(seed));
        pole = add(solid(0xFF4A5250, seed));
        shadow = add(shadow());
        planter = add(planter(seed));
        lampHead = add(solid(0xFFF4F0D8, seed));
        umbrella = add(umbrella());
        rock = add(rock(seed));
        hillGrass = add(hillGrass(seed));

        Random r = new Random(seed);
        // the old one-picture facades: only a few still used directly (fire station, plain walls)
        int[] counts = {1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 6};
        if (lowMemory) for (int k = 0; k < counts.length; k++) counts[k] = Math.max(2, counts[k] / 2);
        JpTextures.Kind[] kinds = JpTextures.Kind.values();
        int s = 1000;
        for (int k = 0; k < kinds.length; k++) {
            jp[k] = new int[counts[k]];
            for (int i = 0; i < counts[k]; i++) jp[k][i] = add(JpTextures.facade(r, kinds[k], s++));
        }
        buildingStyles(seed, lowMemory);
        String[] name = new String[1];
        for (int i = 0; i < shops.length; i++) {
            int cat = i % JpTextures.CATEGORIES.length;
            shopCategory[i] = cat;
            shops[i] = add(ShopLayers.shop(cat, s++, name));
            shopNames.add(name[0]);
        }
        lobbies = new int[12];
        for (int i = 0; i < lobbies.length; i++) lobbies[i] = add(Storefronts.lobby(r, s++));
        int[] roofColors = {0x6A6E72, 0x2E3034, 0x3A4A6A, 0x6A4A3A, 0x8A8E90};
        roofs = new int[roofColors.length];
        for (int i = 0; i < roofs.length; i++) roofs[i] = add(JpTextures.roofTiles(roofColors[i], s++));
        signs = new int[60];
        for (int i = 0; i < signs.length; i++) signs[i] = add(JpTextures.verticalSign(s++));
        vending = new int[5];
        for (int i = 0; i < vending.length; i++) vending[i] = add(JpTextures.vending(s++));
        stationSigns = new int[STATIONS.length];
        for (int i = 0; i < STATIONS.length; i++) stationSigns[i] = add(JpTextures.stationSign(STATIONS[i], s++));
        koban = new int[]{add(JpTextures.koban(s++)), add(JpTextures.koban(s++))};
        fireDoors = new int[]{add(JpTextures.fireDoors(s++)), add(JpTextures.fireDoors(s++))};
        blockWall = add(JpTextures.blockWall(s++));
        paddyWater = add(FarmTextures.paddy(0, seed));
        paddyGreen = add(FarmTextures.paddy(1, seed));
        paddyGold = add(FarmTextures.paddy(2, seed));
        cabbage = add(FarmTextures.cabbage(seed));
        soil = add(FarmTextures.soil(seed));
        tea = add(FarmTextures.tea(seed));
        vinyl = add(FarmTextures.vinyl(seed));
        gravel = add(FarmTextures.gravel(seed));
        guardrail = add(FarmTextures.guardrail(seed));
        // the ground's 128-texel, 8 m tiles; lanes and junction mouths are cut from the same asphalt
        int terrainFrom = list.size();
        tiles = new TerrainTiles(this, seed);
        int terrainTo = list.size();
        Texture asp = list.get(tiles.asphalt);
        float tm = TerrainTiles.METRES;
        laneCountry = add(FarmTextures.lane(asp, tm, 3.0f, FarmTextures.LINE_DASH5, FarmTextures.LINE_SOLID));
        laneHwInner = add(FarmTextures.lane(asp, tm, 3.25f, FarmTextures.LINE_NONE, FarmTextures.LINE_DASH5));
        laneHwOuter = add(FarmTextures.lane(asp, tm, 3.25f, FarmTextures.LINE_NONE, FarmTextures.LINE_SOLID));
        laneHwMedian = add(FarmTextures.lane(asp, tm, 0.6f, FarmTextures.LINE_DOUBLE_YELLOW, FarmTextures.LINE_NONE));
        laneFwInner = add(FarmTextures.lane(asp, tm, 3.5f, FarmTextures.LINE_YELLOW, FarmTextures.LINE_DASH8));
        laneFwOuter = add(FarmTextures.lane(asp, tm, 3.5f, FarmTextures.LINE_NONE, FarmTextures.LINE_THICK));
        laneStreet = add(FarmTextures.lane(asp, tm, 3.5f, FarmTextures.LINE_NONE, FarmTextures.LINE_SOLID));
        laneAvInner = add(FarmTextures.lane(asp, tm, 3.5f, FarmTextures.LINE_YELLOW, FarmTextures.LINE_DASH5));
        laneAvOuter = add(FarmTextures.lane(asp, tm, 3.5f, FarmTextures.LINE_NONE, FarmTextures.LINE_SOLID));
        mouthStreet = add(FarmTextures.mouth(asp, tm, 13f, 5f, 3.75f, true, true));
        mouthAvenue = add(FarmTextures.mouth(asp, tm, 22f, 5f, 8.3f, true, true));
        mouthCountry = add(FarmTextures.mouth(asp, tm, 7.8f, 3f, 3.1f, false, true));
        mouthHighway = add(FarmTextures.mouth(asp, tm, 16.6f, 3f, 6.8f, false, true));
        mouthLane = add(FarmTextures.mouth(asp, tm, 4.4f, 3f, 2.0f, false, true));
        laneRamp = add(FarmTextures.lane(asp, tm, 3.75f, FarmTextures.LINE_SOLID, FarmTextures.LINE_SOLID));
        laneTown = add(FarmTextures.lane(asp, tm, 2.75f, FarmTextures.LINE_NONE, FarmTextures.LINE_SOLID));
        walkStrip = add(FarmTextures.walkStrip(asp, 1.75f));
        mouthTown = add(FarmTextures.mouth(asp, tm, 9f, 3f, 2.75f, false, true));
        int lanesTo = list.size();
        roadEdge = add(FarmTextures.roadEdge(asp, seed));
        houseTextures((int) seed);
        houseKits(seed, lowMemory);
        // the pixel-art finish: baked relief light and dithered palettes on everything
        java.util.Set<Integer> done = new java.util.HashSet<>();
        done.add(shadow);
        List<Texture> ground = new ArrayList<>(), roads = new ArrayList<>();
        for (int i = terrainFrom; i < terrainTo; i++) {
            if (i == tiles.asphalt) continue;
            ground.add(list.get(i));
            done.add(i);
        }
        roads.add(list.get(tiles.asphalt));
        done.add(tiles.asphalt);
        for (int i = terrainTo; i < lanesTo; i++) { roads.add(list.get(i)); done.add(i); }
        PixelArt.stylizeGroup(ground, (int) seed);
        PixelArt.stylizeGroup(roads, (int) seed + 1);
        PixelArt.stylizeAll(list, done);
    }

    // ------------------------------------------------------------------ house generator parts

    public int[] houseMat, houseWin, houseDoor, houseRoof;
    /** Plank walls and plank doors for barns, sheds and the shrine hall (no windows painted on). */
    public int[] barnWall, barnDoor;
    /** House parts: eave soffit boards, fascia / gutter colours, foundation, window sills, canopies. */
    public int soffit, foundation, sill, canopyMetal;
    public int[] fascia, gutter;
    public HouseTextures.Material[] houseMatKind;
    public HouseTextures.Window[] houseWinKind;
    public HouseTextures.Door[] houseDoorKind;
    public HouseTextures.Roof[] houseRoofKind;
    /** Building props: a/c unit front and casing, balcony front and slab, power line ribbon. */
    /** Car park bays (variants; the first is {@link #parking}) and the aisle between them. */
    public int[] parkingBays;
    public int parkingAisle;
    public int dirt, roadEdge, gravelTrack, dirtTrack, aircon, airconSide, balconyFront, balconySlab, powerLines;
    /** Alley clutter: beer crates, rubbish bins, cardboard, gas bottles. */
    public int crateYellow, crateRed, bin, cardboard, gasBottle;
    /** Road furniture: direction boards, speed limits (40, 50), bus stop plate, curve mirror. */
    public int[] dirSign = new int[3];
    public int speed40, speed50, busStop, curveMirror, paintOrange, signOnsen;
    public int fence, signTownOffice, signPost, signSuper, signSchool, signClinic, signGas, schoolWall, superWall, canopy, field;

    private void houseTextures(int seed) {
        HouseTextures.Material S = HouseTextures.Material.SIDING, U = HouseTextures.Material.STUCCO, B = HouseTextures.Material.BOARDS,
                T = HouseTextures.Material.TILE, K = HouseTextures.Material.BRICK, P = HouseTextures.Material.PANEL,
                L = HouseTextures.Material.PLASTER, C = HouseTextures.Material.CHARRED;
        Object[][] mats = {
                {S, 0xEEEAE0}, {S, 0xE2D8C6}, {S, 0xD0CCC4}, {S, 0xB4B8B0}, {S, 0x8E9AA6}, {S, 0xC8B49A}, {S, 0x7A6A5A}, {S, 0xA8B4A0},
                {S, 0x5E6670}, {S, 0xF2EEE6},
                {U, 0xF0ECE2}, {U, 0xE6DCC8}, {U, 0xD8CCB8}, {U, 0xC8C0B0}, {U, 0xE8E0D8}, {U, 0xBCB4A8},
                {B, 0x9A7452}, {B, 0x6A4E38}, {B, 0xB89670},
                {T, 0xE8E2D6}, {T, 0xC8B8A4}, {T, 0x9A8A7A}, {T, 0xD8D8D2},
                {K, 0xA06048}, {K, 0x8A5A44}, {K, 0xC09078},
                {P, 0xDADAD4}, {P, 0x9AA0A4}, {P, 0x6A6E72}, {P, 0xE6E0D2},
                {L, 0xF2F0E8}, {L, 0xE8E2D2},
                {C, 0x2A2420},
                {HouseTextures.Material.STONE, 0xD8D0C2}, {HouseTextures.Material.STONE, 0xB8B0A4}, {HouseTextures.Material.STONE, 0xC8B8A0}};
        houseMat = new int[mats.length];
        houseMatKind = new HouseTextures.Material[mats.length];
        for (int i = 0; i < mats.length; i++) {
            houseMatKind[i] = (HouseTextures.Material) mats[i][0];
            houseMat[i] = add(HouseTextures.material(houseMatKind[i], (Integer) mats[i][1], seed + 3000 + i));
        }
        soffit = add(HouseTextures.soffit(seed + 3070));
        foundation = add(HouseTextures.foundation(seed + 3071));
        sill = add(HouseTextures.trim(0xB4B8BC, seed + 3072));
        canopyMetal = add(HouseTextures.trim(0x4A4E52, seed + 3073));
        int[] fasciaColours = {0xEEEEEA, 0x3A3A3C, 0x6A5444, 0x8A8E92, 0x2E3A4A};
        fascia = new int[fasciaColours.length];
        for (int i = 0; i < fasciaColours.length; i++) fascia[i] = add(HouseTextures.trim(fasciaColours[i], seed + 3074 + i));
        int[] gutterColours = {0xE8E6E0, 0x5A4A3E, 0x8A8E92, 0x2E2E30};
        gutter = new int[gutterColours.length];
        for (int i = 0; i < gutterColours.length; i++) gutter[i] = add(HouseTextures.trim(gutterColours[i], seed + 3080 + i));
        int[] barnColours = {0x5A4434, 0x7A7068, 0x3A2C24, 0x6A3A2C, 0x8A7A62};
        barnWall = new int[barnColours.length];
        for (int i = 0; i < barnColours.length; i++)
            barnWall[i] = add(HouseTextures.material(HouseTextures.Material.BARN, barnColours[i], seed + 3050 + i));
        int[] barnDoors = {0x4A3A2E, 0x6A6460, 0x7A3A2A};
        barnDoor = new int[barnDoors.length];
        for (int i = 0; i < barnDoors.length; i++)
            barnDoor[i] = add(HouseTextures.door(HouseTextures.Door.BARN, barnDoors[i], seed + 3060 + i));
        int[] frames = {0xB8BCC0, 0x5A4A3E, 0xEEEEEA, 0x2E2E30};
        java.util.List<Integer> wins = new java.util.ArrayList<>();
        java.util.List<HouseTextures.Window> winKinds = new java.util.ArrayList<>();
        for (HouseTextures.Window w : HouseTextures.Window.values())
            for (int f = 0; f < frames.length; f++) {
                if ((w == HouseTextures.Window.LATTICE || w == HouseTextures.Window.SHOJI) && f != 1 && f != 3) continue;
                wins.add(add(recessed(HouseTextures.window(w, w == HouseTextures.Window.LATTICE ? 0x6A4A2E : frames[f], seed + 3100 + wins.size()), true)));
                winKinds.add(w);
            }
        houseWin = new int[wins.size()];
        houseWinKind = winKinds.toArray(new HouseTextures.Window[0]);
        for (int i = 0; i < houseWin.length; i++) houseWin[i] = wins.get(i);
        Object[][] doors = {
                {HouseTextures.Door.WOOD, 0x8A6440}, {HouseTextures.Door.WOOD, 0x5E4430}, {HouseTextures.Door.WOOD, 0xA8845E},
                {HouseTextures.Door.METAL, 0x6E7276}, {HouseTextures.Door.METAL, 0xC8C6C0}, {HouseTextures.Door.METAL, 0x3A4A5A},
                {HouseTextures.Door.DARK, 0x2E2824}, {HouseTextures.Door.DARK, 0x4A3A30},
                {HouseTextures.Door.SLIDING_LATTICE, 0x6A4A2E}, {HouseTextures.Door.SLIDING_LATTICE, 0x3A2A20},
                {HouseTextures.Door.SLIDING_GLASS, 0xB8BCC0}, {HouseTextures.Door.SLIDING_GLASS, 0x5A4A3E},
                {HouseTextures.Door.GARAGE, 0xC8C8C2}, {HouseTextures.Door.GARAGE, 0x8A8E92}, {HouseTextures.Door.GARAGE, 0x6A5A4A}};
        houseDoor = new int[doors.length];
        houseDoorKind = new HouseTextures.Door[doors.length];
        for (int i = 0; i < doors.length; i++) {
            houseDoorKind[i] = (HouseTextures.Door) doors[i][0];
            houseDoor[i] = add(recessed(HouseTextures.door(houseDoorKind[i], (Integer) doors[i][1], seed + 3200 + i), false));
        }
        Object[][] roofsAll = {
                {HouseTextures.Roof.KAWARA, 0x6A6E72}, {HouseTextures.Roof.KAWARA, 0x2E3034}, {HouseTextures.Roof.KAWARA, 0x3A4A6A},
                {HouseTextures.Roof.KAWARA, 0x6A4A3A}, {HouseTextures.Roof.KAWARA, 0x8A8E90}, {HouseTextures.Roof.KAWARA, 0x4A5660},
                {HouseTextures.Roof.KAWARA, 0x8A3A2A},
                {HouseTextures.Roof.METAL, 0x4A3A30}, {HouseTextures.Roof.METAL, 0x2E3A4E}, {HouseTextures.Roof.METAL, 0x3A5A44},
                {HouseTextures.Roof.METAL, 0x6A6E72}, {HouseTextures.Roof.METAL, 0x8A2E26}, {HouseTextures.Roof.METAL, 0x2A2A2C},
                {HouseTextures.Roof.SLATE, 0x2E3034}, {HouseTextures.Roof.SLATE, 0x5A5E62}, {HouseTextures.Roof.SLATE, 0x5A4638},
                {HouseTextures.Roof.SLATE, 0x46525E}, {HouseTextures.Roof.COPPER, 0x5A8A78}};
        houseRoof = new int[roofsAll.length];
        houseRoofKind = new HouseTextures.Roof[roofsAll.length];
        for (int i = 0; i < roofsAll.length; i++) {
            houseRoofKind[i] = (HouseTextures.Roof) roofsAll[i][0];
            houseRoof[i] = add(HouseTextures.roof(houseRoofKind[i], (Integer) roofsAll[i][1], seed + 3300 + i));
        }
        fence = add(FarmTextures.fence(seed + 3399));
        signTownOffice = add(TownTextures.sign("TOWN OFFICE", 0xF4F2EC, 0x1A2A4A, seed + 3400));
        signPost = add(TownTextures.postSign(seed + 3401));
        signSuper = add(TownTextures.sign("SUPERMARKET", 0xD8302A, 0xFFFFFF, seed + 3402));
        signSchool = add(TownTextures.sign("SCHOOL", 0xF4F2EC, 0x2A4A2A, seed + 3403));
        signClinic = add(TownTextures.sign("CLINIC", 0xFFFFFF, 0x2A7A4A, seed + 3404));
        signGas = add(TownTextures.sign("GAS", 0x1A5AB0, 0xFFFFFF, seed + 3405));
        for (int i = 0; i < dirSign.length; i++) dirSign[i] = add(RoadSignTextures.direction(seed + 3460 + i));
        speed40 = add(RoadSignTextures.speed(40, seed + 3465));
        speed50 = add(RoadSignTextures.speed(50, seed + 3466));
        busStop = add(RoadSignTextures.busStop(seed + 3467));
        curveMirror = add(RoadSignTextures.mirror(seed + 3468));
        paintOrange = add(solid(0xFFE0701E, seed + 3469));
        signOnsen = add(TownTextures.sign("ONSEN", 0xB8202A, 0xFFF4E8, seed + 3470));
        schoolWall = add(TownTextures.schoolFacade(seed + 3406));
        superWall = add(TownTextures.superFront(seed + 3407));
        canopy = add(TownTextures.canopy(seed + 3408));
        field = add(TownTextures.field(seed + 3409));
        aircon = add(PropTextures.aircon(seed + 3410));
        airconSide = add(PropTextures.airconSide(seed + 3411));
        balconyFront = add(PropTextures.balcony(0xD8D4CC, seed + 3412));
        balconySlab = add(PropTextures.balconySlab(seed + 3413));
        powerLines = add(PropTextures.powerLines());
        crateYellow = add(PropTextures.crate(0xFFE0B020, seed + 3414));
        crateRed = add(PropTextures.crate(0xFFC03028, seed + 3415));
        bin = add(PropTextures.bin(seed + 3416));
        cardboard = add(PropTextures.cardboard(seed + 3417));
        gasBottle = add(PropTextures.gasBottle(seed + 3418));
        gravelTrack = add(PropTextures.track(false, seed + 3419));
        dirtTrack = add(PropTextures.track(true, seed + 3420));
        dirt = add(FarmTextures.dirt(seed));
    }

    /**
     * Windows and doors are laid on the wall as flat decals: paint the depth they no longer have
     * into them — the head's shadow along the top, the left jamb's down the side (sun from the
     * upper left), a lit sill along the bottom of windows.
     */
    private static Texture recessed(Texture t, boolean sill) {
        int w = t.w, h = t.h;
        int band = Math.max(2, h / 16), side = Math.max(1, w / 24);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float k = 1f;
                if (y < band) k *= 0.55f + 0.45f * y / band;
                if (x < side) k *= 0.72f + 0.28f * x / side;
                if (sill && y >= h - Math.max(2, h / 24)) k *= 1.25f;
                if (k != 1f) t.px[y * w + x] = Img.mul(t.px[y * w + x], k);
            }
        return t;
    }

    public int add(Texture t) {
        list.add(t);
        return list.size() - 1;
    }

    public Texture[] array() { return list.toArray(new Texture[0]); }

    public int[] facades(JpTextures.Kind k) { return jp[k.ordinal()]; }

    /** Whole-house kits (shape + one painted texture for all four walls): suburban and farm. */
    public HouseKits.Kit[] suburbKits, farmKits;

    private int pickIdx(int[] ids, Object[] kinds, Random r, Object... want) {
        java.util.List<Integer> ok = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) for (Object w : want) if (kinds[i] == w) { ok.add(i); break; }
        if (ok.isEmpty()) return r.nextInt(ids.length);
        return ok.get(r.nextInt(ok.size()));
    }

    /** Designs and paints the house kits (in parallel). */
    private void houseKits(long seed, boolean lowMemory) {
        int ns = lowMemory ? 48 : 96, nf = lowMemory ? 16 : 32;
        HouseKits.Kit[] kits = new HouseKits.Kit[ns + nf];
        HouseKits.Parts[] parts = new HouseKits.Parts[ns + nf];
        HouseTextures.Material S = HouseTextures.Material.SIDING, U = HouseTextures.Material.STUCCO, B = HouseTextures.Material.BOARDS,
                T = HouseTextures.Material.TILE, K = HouseTextures.Material.BRICK, P = HouseTextures.Material.PANEL,
                L = HouseTextures.Material.PLASTER, C = HouseTextures.Material.CHARRED;
        int[] trims = {0xEEEEEA, 0x3A3A3C, 0x6A5444, 0x8A8E92, 0x2E3A4A, 0xC8C0B0};
        int[] rails = {0xB8BCC0, 0x3A3A3E, 0xE8E8E4, 0xDDE6EC, 0x6A5444, 0x9AA4AE};
        for (int i = 0; i < kits.length; i++) {
            boolean farm = i >= ns;
            Random r = new Random(seed * 977 + i * 7919);
            HouseKits.Kit k = new HouseKits.Kit();
            k.farm = farm;
            k.floors = farm ? (r.nextInt(3) == 0 ? 1 : 2) : (r.nextInt(4) == 0 ? 1 : 2);
            k.W = farm ? 8.5f + r.nextFloat() * 6f : 6.5f + r.nextFloat() * 6.5f;
            k.D = farm ? 7f + r.nextFloat() * 4.5f : 6f + r.nextFloat() * 4.5f;
            k.floorH = 2.75f + r.nextFloat() * 0.35f;
            int roll = r.nextInt(100);
            k.roof = farm ? (roll < 55 ? HouseKits.HIP : HouseKits.GABLE)
                    : roll < 35 ? HouseKits.GABLE : roll < 50 ? HouseKits.GABLE_FRONT : roll < 75 ? HouseKits.HIP : roll < 88 ? HouseKits.SHED : HouseKits.FLAT;
            float pitch = farm ? 0.5f + r.nextFloat() * 0.2f : 0.35f + r.nextFloat() * 0.25f;
            switch (k.roof) {
                case HouseKits.GABLE: k.rise = pitch * k.D * 0.5f; break;
                case HouseKits.GABLE_FRONT: k.rise = pitch * k.W * 0.5f; break;
                case HouseKits.HIP: k.rise = pitch * Math.min(k.W, k.D) * 0.5f; break;
                case HouseKits.SHED: k.rise = (0.15f + r.nextFloat() * 0.12f) * k.D; break;
                default: k.rise = 0f;
            }
            HouseKits.Parts p = new HouseKits.Parts();
            int m1 = farm ? pickIdx(houseMat, houseMatKind, r, B, L, C, U) : pickIdx(houseMat, houseMatKind, r, S, S, U, T, P, K);
            int m2 = r.nextInt(3) == 0 ? (farm ? pickIdx(houseMat, houseMatKind, r, L, U) : pickIdx(houseMat, houseMatKind, r, S, U, P)) : m1;
            p.mat1 = list.get(houseMat[m1]);
            p.mat2 = list.get(houseMat[m2]);
            p.twoTone = m1 != m2;
            HouseTextures.Window[] wk = houseWinKind;
            p.win = list.get(houseWin[farm ? pickIdx(houseWin, wk, r, HouseTextures.Window.LATTICE, HouseTextures.Window.SHOJI, HouseTextures.Window.SLIDING)
                    : pickIdx(houseWin, wk, r, HouseTextures.Window.SLIDING, HouseTextures.Window.SHUTTER_BOX, HouseTextures.Window.CASEMENT)]);
            p.small = list.get(houseWin[pickIdx(houseWin, wk, r, HouseTextures.Window.FROSTED, HouseTextures.Window.SLIT)]);
            p.garden = list.get(houseWin[pickIdx(houseWin, wk, r, HouseTextures.Window.GARDEN, HouseTextures.Window.SLIDING)]);
            int di = farm ? pickIdx(houseDoor, houseDoorKind, r, HouseTextures.Door.SLIDING_LATTICE, HouseTextures.Door.SLIDING_GLASS)
                    : pickIdx(houseDoor, houseDoorKind, r, HouseTextures.Door.WOOD, HouseTextures.Door.METAL, HouseTextures.Door.DARK);
            p.door = list.get(houseDoor[di]);
            p.wideDoor = houseDoorKind[di] == HouseTextures.Door.SLIDING_LATTICE || houseDoorKind[di] == HouseTextures.Door.SLIDING_GLASS;
            p.foundation = list.get(foundation);
            p.trim = trims[r.nextInt(trims.length)];
            p.rail = rails[r.nextInt(rails.length)];
            p.balcony = !farm && k.floors > 1 && r.nextInt(5) < 2;
            p.hisashi = farm || r.nextInt(4) == 0;
            p.doorLeft = r.nextBoolean();
            p.winH = 0.9f + r.nextFloat() * 0.5f;
            p.garage = !farm && k.W > 8.5f && r.nextInt(3) == 0;
            p.grille = r.nextInt(3) == 0;
            p.shutter = new int[]{0xB8BCBE, 0x9AA0A4, 0xC8C4B8, 0x7A8088, 0xD8D8D2}[r.nextInt(5)];
            k.roofTex = houseRoof[farm ? pickIdx(houseRoof, houseRoofKind, r, HouseTextures.Roof.KAWARA) : r.nextInt(houseRoof.length)];
            kits[i] = k;
            parts[i] = p;
        }
        Texture[] painted = new Texture[kits.length];
        java.util.stream.IntStream.range(0, kits.length).parallel().forEach(i -> painted[i] = HouseKits.paint(kits[i], parts[i], seed * 13 + i));
        for (int i = 0; i < kits.length; i++) kits[i].tex = add(painted[i]);
        suburbKits = java.util.Arrays.copyOf(kits, ns);
        farmKits = java.util.Arrays.copyOfRange(kits, ns, ns + nf);
    }

    /** One building look from {@link FacadeLayers}: texture ids of its parts. */
    public static final class BuildingStyle implements java.io.Serializable {
        public int bay, side, ground, entrance, plain;
        public FacadeRecipes.Type type;
        public boolean balconies, glass;
    }

    /** Building looks by {@link FacadeRecipes.Type} (ordinal). */
    public BuildingStyle[][] buildingStyles;

    public BuildingStyle[] styles(FacadeRecipes.Type t) { return buildingStyles[t.ordinal()]; }

    /** Paints every building look's textures (in parallel: the layer stack takes ~20 ms a look). */
    private void buildingStyles(long seed, boolean lowMemory) {
        FacadeRecipes.Type[] types = FacadeRecipes.Type.values();
        //                    OFFICE CURTAIN MANSION CORRIDOR ZAKKYO SHOPHOUSE DEPT WAREHOUSE CIVIC
        int[] counts = lowMemory ? new int[]{3, 2, 4, 2, 4, 4, 2, 2, 2} : new int[]{6, 4, 7, 4, 7, 7, 3, 3, 4};
        buildingStyles = new BuildingStyle[types.length][];
        java.util.List<int[]> jobs = new java.util.ArrayList<>();
        for (int t = 0; t < types.length; t++) for (int i = 0; i < counts[t]; i++) jobs.add(new int[]{t, i});
        FacadeLayers.Style[] made = new FacadeLayers.Style[jobs.size()];
        java.util.stream.IntStream.range(0, jobs.size()).parallel().forEach(j ->
                made[j] = FacadeLayers.style(FacadeRecipes.make(types[jobs.get(j)[0]], seed * 1000 + jobs.get(j)[1] * 17 + jobs.get(j)[0])));
        for (int t = 0; t < types.length; t++) buildingStyles[t] = new BuildingStyle[counts[t]];
        for (int j = 0; j < jobs.size(); j++) {
            FacadeLayers.Style st = made[j];
            BuildingStyle b = new BuildingStyle();
            b.type = types[jobs.get(j)[0]];
            b.bay = add(st.bay);
            b.side = add(st.side);
            b.ground = add(st.ground);
            b.entrance = add(st.entrance);
            b.plain = add(st.plain);
            b.balconies = st.recipe.balcony;
            b.glass = st.recipe.win == FacadeLayers.Window.CURTAIN_WALL;
            buildingStyles[jobs.get(j)[0]][jobs.get(j)[1]] = b;
        }
    }

    // ------------------------------------------------------------------ base materials

    private static Texture asphalt(long seed, boolean patched) {
        Img m = new Img(64, 64);
        int sd = (int) seed;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 3);
                float k = 0.29f + n * 0.05f + g * 0.06f;
                if (Noise.rnd(x, y, sd + 7) > 0.965f) k += 0.10f;
                int v = (int) (k * 255);
                m.px[y * 64 + x] = rgb(v, v, v + 4);
            }
        // oil stain + hairline crack
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float o = Noise.value(x, y, 64, 64, 4, 4, sd + 11);
                if (o > 0.74f) m.px[y * 64 + x] = mul(m.px[y * 64 + x], 0.94f);
            }
        int cy = 20;
        for (int x = 6; x < 40; x++) {
            cy += Noise.rnd(x, 0, sd + 5) > 0.5f ? 1 : -1;
            cy = Math.max(4, Math.min(60, cy));
            m.shade(x, cy, x + 1, cy + 1, 0.7f);
        }
        if (patched) {
            m.shade(10, 34, 44, 58, 0.9f);
            m.frame(10, 34, 44, 58, 1, rgb(58, 58, 60));
        }
        return m.texture();
    }

    private static Texture sidewalk(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFC8C4BA);
        m.grain(0, 0, 64, 64, 0.06f, 0.05f, (int) seed + 20);
        for (int i = 0; i < 64; i++) {
            m.shade(i, 0, i + 1, 1, 0.72f);
            m.shade(0, i, 1, i + 1, 0.72f);
            m.shade(i, 32, i + 1, 33, 0.8f);
            m.shade(32, i, 33, i + 1, 0.8f);
        }
        for (int i = 0; i < 5; i++) { // gum spots
            int x = (int) (Noise.rnd(i, 1, (int) seed) * 62), y = (int) (Noise.rnd(i, 2, (int) seed) * 62);
            m.shade(x, y, x + 2, y + 1, 0.75f);
        }
        return m.texture();
    }

    private static Texture pavers(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 30;
        for (int y = 0; y < 64; y++) {
            int row = y / 8;
            for (int x = 0; x < 64; x++) {
                int sx = (x + (row & 1) * 8) % 16;
                boolean joint = (y % 8) == 0 || sx == 0;
                float v = Noise.rnd((x + (row & 1) * 8) / 16, row, sd);
                int base = v < 0.33f ? 0xFFC88A6A : (v < 0.66f ? 0xFFB87A5C : 0xFFD49C7C);
                m.px[y * 64 + x] = joint ? 0xFF8A7A6C : mul(base, 0.92f + Noise.rnd(x, y, sd + 1) * 0.12f);
            }
        }
        return m.texture();
    }

    private static Texture concrete(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFBDB9B0);
        m.grain(0, 0, 64, 64, 0.08f, 0.05f, (int) seed + 40);
        m.frame(0, 0, 64, 64, 1, 0xFF8E8A82);
        return m.texture();
    }

    private static Texture curb(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFD4D2CA);
        m.grain(0, 0, 64, 64, 0.05f, 0.05f, (int) seed + 50);
        m.rect(0, 0, 64, 3, 0xFFE8E6DE);
        for (int x = 0; x < 64; x += 32) m.shade(x, 0, x + 1, 64, 0.75f);
        m.shade(0, 48, 64, 64, 0.85f);
        return m.texture();
    }

    private static Texture grass(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 60;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float k = 0.62f + n * 0.35f + g * 0.18f;
                boolean blade = Noise.rnd(x, y, sd + 2) > 0.8f;
                m.px[y * 64 + x] = blade ? rgb((int) (110 * k), (int) (170 * k), (int) (60 * k))
                        : rgb((int) (70 * k), (int) (128 * k), (int) (48 * k));
            }
        return m.texture();
    }

    private static Texture sand(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 70;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float ripple = (float) Math.sin((x * 0.3 + y * 0.9 + n * 6.0)) * 0.03f;
                float k = 0.86f + n * 0.1f + g * 0.07f + ripple;
                m.px[y * 64 + x] = rgb((int) (238 * k), (int) (220 * k), (int) (178 * k));
            }
        return m.texture();
    }

    private static Texture water(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 80;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd);
                float wv = (float) Math.sin((x / 64.0) * Math.PI * 4 + (y / 64.0) * Math.PI * 2 + n * 5) * 0.5f + 0.5f;
                int c = mix(0xFF1E8FA8, 0xFF48C4C8, n * 0.6f + wv * 0.3f);
                if (wv > 0.93f && n > 0.45f) c = mix(c, 0xFFE8FFFF, 0.6f);
                m.px[y * 64 + x] = c;
            }
        return m.texture();
    }

    private static Texture roof(long seed, int kind) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 90 + kind;
        if (kind == 0) {
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++) {
                    int v = (int) (120 + Noise.rnd(x, y, sd) * 70 + Noise.fbm(x, y, 64, 64, sd) * 30);
                    m.px[y * 64 + x] = rgb(v, v - 4, v - 10);
                }
        } else if (kind == 1) {
            m.fill(0xFFE4E6E4);
            m.grain(0, 0, 64, 64, 0.06f, 0.03f, sd);
            for (int x = 0; x < 64; x += 16) m.shade(x, 0, x + 1, 64, 0.85f);
        } else {
            m.fill(0xFF4A4A4E);
            m.grain(0, 0, 64, 64, 0.08f, 0.06f, sd);
            for (int y = 0; y < 64; y += 21) m.shade(0, y, 64, y + 1, 0.7f);
        }
        return m.texture();
    }

    private static Texture solid(int c, long seed) {
        Img m = new Img(8, 8);
        m.fill(c);
        m.grain(0, 0, 8, 8, 0.0f, 0.04f, (int) seed);
        return m.texture();
    }

    private static Texture trunk(long seed) {
        Img m = new Img(32, 64);
        int sd = (int) seed + 100;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 32; x++) {
                float band = ((y + (x / 4)) % 6) < 2 ? 0.78f : 1f;
                float k = (0.85f + Noise.rnd(x, y, sd) * 0.15f) * band;
                m.px[y * 32 + x] = mul(0xFF9A8670, k);
            }
        return m.texture();
    }

    /** One palm frond: rib along u, leaflets fanning out, transparent elsewhere. */
    private static Texture frond(long seed) {
        Img m = new Img(64, 32);
        m.fill(0);
        int sd = (int) seed + 110;
        for (int x = 0; x < 64; x++) {
            float t = x / 63f;
            float width = (float) Math.sin(t * Math.PI) * 14f * (1f - t * 0.3f) + 1f;
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < width; i++) {
                    int y = 16 + side * i;
                    // leaflet gaps
                    if (((x + i) % 5) == 0 && i > 2) continue;
                    float k = 0.75f + Noise.rnd(x, y, sd) * 0.3f - i / width * 0.25f;
                    m.set(x, y, mul(0xFF4E8A3A, k));
                }
            }
            m.set(x, 16, 0xFF8A9A50);
        }
        return m.texture();
    }

    private static Texture rock(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 150;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float strata = (float) Math.sin(y * 0.5 + n * 6) * 0.06f;
                float k = 0.55f + n * 0.3f + g * 0.1f + strata;
                m.px[y * 64 + x] = rgb((int) (150 * k), (int) (140 * k), (int) (122 * k));
            }
        return m.texture();
    }

    private static Texture hillGrass(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 160;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float k = 0.6f + n * 0.35f + g * 0.15f;
                boolean dry = Noise.value(x, y, 64, 64, 4, 4, sd + 2) > 0.6f;
                m.px[y * 64 + x] = dry ? rgb((int) (130 * k), (int) (140 * k), (int) (70 * k))
                        : rgb((int) (64 * k), (int) (116 * k), (int) (46 * k));
            }
        return m.texture();
    }

    private static Texture hedge(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 120;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float k = 0.55f + n * 0.4f + g * 0.25f;
                m.px[y * 64 + x] = rgb((int) (60 * k), (int) (120 * k), (int) (52 * k));
            }
        return m.texture();
    }

    private static Texture metal(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFB4B8BA);
        m.grain(0, 0, 64, 64, 0.04f, 0.03f, (int) seed + 130);
        for (int y = 8; y < 56; y += 3) m.rect(8, y, 56, y + 1, 0xFF7A7E80);
        m.frame(0, 0, 64, 64, 2, 0xFF8A8E90);
        return m.texture();
    }

    private static Texture planter(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFE0D8C8);
        m.grain(0, 0, 64, 64, 0.05f, 0.05f, (int) seed + 140);
        m.rect(0, 0, 64, 4, 0xFFF0EADC);
        return m.texture();
    }

    private static Texture umbrella() {
        Img m = new Img(64, 64);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++)
                m.px[y * 64 + x] = ((x / 8) & 1) == 0 ? 0xFFE8483C : 0xFFF4F0E6;
        return m.texture();
    }

    private static Texture shadow() {
        Img m = new Img(32, 32);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                float dx = (x + 0.5f) / 16f - 1f, dy = (y + 0.5f) / 16f - 1f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = d >= 1f ? 0f : Math.min(1f, (1f - d) * 2.5f) * 0.7f;
                m.px[y * 32 + x] = ((int) (a * 255) << 24) | 0x101418;
            }
        return m.texture();
    }
}
