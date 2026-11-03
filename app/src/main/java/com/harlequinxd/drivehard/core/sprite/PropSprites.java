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
        List<List<Part>> v = new ArrayList<>();
        for (int i = 0; i < f.variants; i++) v.add(f.model(i));
        SpriteBaker b = baker(f.frameW, f.frameH, f.anchorX, f.anchorY, f.ppm);
        b.directions = 1;
        return b.bakeStatic(v);
    }

    /** One of the 100 car styles at 16 view angles. */
    public static SpriteSheet car(int style) {
        int[] f = com.harlequinxd.drivehard.core.model.Cars.frame(style);
        SpriteBaker b = baker(f[0], f[1], f[2], f[3], com.harlequinxd.drivehard.core.model.Cars.PPM);
        b.directions = 16;
        b.outline = true;
        List<List<Part>> v = new ArrayList<>();
        v.add(com.harlequinxd.drivehard.core.model.Cars.build(style));
        return b.bakeStatic(v);
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
