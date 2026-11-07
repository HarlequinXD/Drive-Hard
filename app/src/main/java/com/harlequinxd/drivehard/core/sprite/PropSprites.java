package com.harlequinxd.drivehard.core.sprite;

import com.harlequinxd.drivehard.core.model.Part;
import com.harlequinxd.drivehard.core.model.PropModels;

import java.util.ArrayList;
import java.util.List;

/** Bakes the world prop sprites (plants: one view; props: 8 view directions; cars: 16). */
public final class PropSprites {
    private PropSprites() {}

    public static final int PALM_VARIANTS = 3;

    /** All variants of a plant species, one view each: plants look the same from every side. */
    public static SpriteSheet flora(com.harlequinxd.drivehard.core.model.Flora f) {
        return PixelTrees.sheet(f);
    }

    /** The old modelled-and-rendered plants (kept for comparison tools). */
    public static SpriteSheet floraModelled(com.harlequinxd.drivehard.core.model.Flora f) {
        List<List<Part>> v = new ArrayList<>();
        for (int i = 0; i < f.variants; i++) v.add(f.model(i));
        SpriteBaker b = baker(f.frameW, f.frameH, f.anchorX, f.anchorY, f.ppm);
        b.directions = 1;
        return b.bakeStatic(v);
    }

    /**
     * A car style at 16 view angles: its pixel-art blueprint drawn in every direction, the frames
     * cropped to the car (so the sheet holds no empty margins).
     */
    public static SpriteSheet car(int style) {
        PixelCars kit = new PixelCars();
        PixelCars.Model m = com.harlequinxd.drivehard.core.model.Cars.model(style);
        int paint = com.harlequinxd.drivehard.core.model.Cars.paint(style);
        int FW = PixelCars.FW, FH = PixelCars.FH;
        int[][] views = new int[16][];
        int x0 = FW, y0 = FH, x1 = -1, y1 = -1;
        for (int a = 0; a < 16; a++) {
            int[] px = kit.draw(m, paint, a);
            views[a] = px;
            for (int y = 0; y < FH; y++)
                for (int x = 0; x < FW; x++)
                    if (px[y * FW + x] != 0) {
                        if (x < x0) x0 = x; if (x > x1) x1 = x;
                        if (y < y0) y0 = y; if (y > y1) y1 = y;
                    }
        }
        if (x1 < 0) { x0 = 0; y0 = 0; x1 = FW - 1; y1 = FH - 1; }
        x0 = Math.max(0, x0 - 1); y0 = Math.max(0, y0 - 1);
        x1 = Math.min(FW - 1, x1 + 1); y1 = Math.min(FH - 1, y1 + 1);
        int fw = x1 - x0 + 1, fh = y1 - y0 + 1;
        int W = 256;
        while (W < fw * 4) W <<= 1;
        int perRow = W / fw, rows = (16 + perRow - 1) / perRow, H = rows * fh;
        int[] atlas = new int[W * H];
        for (int a = 0; a < 16; a++) {
            int ox = (a % perRow) * fw, oy = (a / perRow) * fh;
            int[] px = views[a];
            for (int y = 0; y < fh; y++) System.arraycopy(px, (y0 + y) * FW + x0, atlas, (oy + y) * W + ox, fw);
        }
        return new SpriteSheet(atlas, W, H, fw, fh, 1, 16, perRow, FW / 2 - x0, PixelCars.GROUND - y0, PixelCars.PPM);
    }

    /** Pedestrian frames: idle (4) then walk (8). */
    public static final com.harlequinxd.drivehard.core.model.Animations.Anim[] PED_ANIMS = {
            com.harlequinxd.drivehard.core.model.Animations.Anim.IDLE, com.harlequinxd.drivehard.core.model.Animations.Anim.WALK};

    public static SpriteSheet pedestrian(int style) {
        com.harlequinxd.drivehard.core.model.CharacterSpec spec = com.harlequinxd.drivehard.core.model.CharacterSpec.randomPedestrian(style);
        SpriteBaker b = new SpriteBaker();
        b.supersample = 2;
        return b.bake(new com.harlequinxd.drivehard.core.model.CharacterModel(spec), PED_ANIMS);
    }

    public static SpriteSheet palms() {
        List<List<Part>> v = new ArrayList<>();
        for (int i = 0; i < PALM_VARIANTS; i++) v.add(PropModels.palm(1000 + i * 77));
        SpriteBaker b = baker(240, 256, 120, 250, 20f);
        b.directions = 1;
        return b.bakeStatic(v);
    }

    public static SpriteSheet lamp() {
        List<List<Part>> v = new ArrayList<>();
        v.add(PropModels.streetLamp());
        return baker(120, 168, 60, 162, 20f).bakeStatic(v);
    }

    public static SpriteSheet utilityPole() {
        List<List<Part>> v = new ArrayList<>();
        v.add(PropModels.utilityPole());
        return baker(48, 232, 24, 226, 20f).bakeStatic(v);
    }

    public static final int UMBRELLA_VARIANTS = 4;

    public static SpriteSheet umbrellas() {
        List<List<Part>> v = new ArrayList<>();
        for (int i = 0; i < UMBRELLA_VARIANTS; i++) v.add(PropModels.umbrella(i));
        return baker(112, 80, 56, 70, 20f).bakeStatic(v);
    }

    /** Frames: 0 green, 1 yellow, 2 red. */
    public static SpriteSheet signals() {
        List<List<Part>> v = new ArrayList<>();
        v.add(PropModels.trafficSignal(PropModels.SIGNAL_GREEN));
        v.add(PropModels.trafficSignal(PropModels.SIGNAL_YELLOW));
        v.add(PropModels.trafficSignal(PropModels.SIGNAL_RED));
        return baker(176, 128, 88, 122, 20f).bakeStatic(v);
    }

    private static SpriteBaker baker(int w, int h, int ax, int ay, float ppm) {
        SpriteBaker b = new SpriteBaker();
        b.frameW = w;
        b.frameH = h;
        b.anchorX = ax;
        b.anchorY = ay;
        b.pixelsPerMeter = ppm;
        b.supersample = 2;
        return b;
    }
}
