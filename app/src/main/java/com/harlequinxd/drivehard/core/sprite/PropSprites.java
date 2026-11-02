package com.harlequinxd.drivehard.core.sprite;

import com.harlequinxd.drivehard.core.model.Part;
import com.harlequinxd.drivehard.core.model.PropModels;

import java.util.ArrayList;
import java.util.List;

/** Bakes the world prop sprites (8 view directions each). */
public final class PropSprites {
    private PropSprites() {}

    public static final int PALM_VARIANTS = 3;

    public static SpriteSheet palms() {
        List<List<Part>> v = new ArrayList<>();
        for (int i = 0; i < PALM_VARIANTS; i++) v.add(PropModels.palm(1000 + i * 77));
        SpriteBaker b = baker(240, 256, 120, 250, 20f);
        return b.bakeStatic(v);
    }

    public static SpriteSheet lamp() {
        List<List<Part>> v = new ArrayList<>();
        v.add(PropModels.streetLamp());
        return baker(120, 168, 60, 162, 20f).bakeStatic(v);
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
        return baker(200, 140, 100, 134, 20f).bakeStatic(v);
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
