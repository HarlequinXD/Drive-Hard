package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.sprite.PixelCars;

/**
 * The traffic's vehicles: the 50 hand-styled pixel-art Japanese cars of {@link PixelCars} (kei
 * cars, hatches, sedans, vans, trucks, taxis, police, fire engines...), each in up to 4 paints.
 * A style is model * PAINTS + paint.
 */
public final class Cars {
    private Cars() {}

    public static final int MODELS = PixelCars.MODELS.length, PAINTS = 4, STYLES = MODELS * PAINTS;

    /** What the game needs to know about a model: its name and footprint. */
    public static final class Spec {
        public final String name;
        public final float L, W;

        Spec(PixelCars.Model m) {
            name = m.name;
            L = m.length();
            W = m.width();
        }
    }

    private static final Spec[] SPECS = new Spec[MODELS];
    static {
        for (int i = 0; i < MODELS; i++) SPECS[i] = new Spec(PixelCars.MODELS[i]);
    }

    public static Spec spec(int style) { return SPECS[Math.floorMod(style / PAINTS, MODELS)]; }

    public static PixelCars.Model model(int style) { return PixelCars.MODELS[Math.floorMod(style / PAINTS, MODELS)]; }

    public static int paint(int style) { return Math.floorMod(style, PAINTS); }
}
