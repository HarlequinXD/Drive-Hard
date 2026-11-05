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

    /** Which wheels the engine drives. */
    public static final int FWD = 0, RWD = 1, AWD = 2;

    /**
     * What the game needs to know about a model: its name, footprint and handling — mass (kg),
     * engine power per kg (kW/kg at the wheels), top speed (m/s), driven wheels, centre-of-mass
     * height (m, for weight transfer) and tyre grip (friction coefficient).
     */
    public static final class Spec {
        public final String name;
        public final float L, W;
        public final float mass, power, top, cgHeight, grip;
        public final int drive;

        Spec(PixelCars.Model m) {
            name = m.name;
            L = m.length();
            W = m.width();
            String n = name;
            float k = 190f, p = 0.07f, t = 40f, h = 0.55f, g = 1.0f;
            int d = FWD;
            if (is(n, "Roadster", "Coupe", "Drift coupe", "Eighties coupe", "Shakotan", "Midship sports")) {
                k = 175f; p = 0.12f; t = 46f; h = 0.45f; g = 1.06f; d = RWD;
            } else if (is(n, "Kei sports")) {
                k = 165f; p = 0.095f; t = 39f; h = 0.45f; g = 1.04f; d = RWD;
            } else if (is(n, "Hot hatch")) {
                k = 180f; p = 0.105f; t = 42f; h = 0.5f; g = 1.04f;
            } else if (is(n, "Rally sedan")) {
                k = 185f; p = 0.125f; t = 45f; h = 0.5f; g = 1.06f; d = AWD;
            } else if (is(n, "Patrol car", "Luxury sedan", "Limousine")) {
                k = 200f; p = 0.09f; t = 44f; h = 0.55f; d = RWD;
            } else if (is(n, "Sixties sedan", "Taxi", "Tall taxi")) {
                k = 195f; p = 0.055f; t = 36f; h = 0.6f; g = 0.95f; d = RWD;
            } else if (is(n, "Fire engine", "Garbage truck", "Box truck", "Tow truck", "Kindergarten bus",
                    "Dekotora", "Dump truck", "Crane truck", "Microbus", "Camper")) {
                k = 320f; p = 0.034f; t = 27f; h = 1.05f; g = 0.85f; d = RWD;
            } else if (is(n, "Ambulance")) {
                k = 270f; p = 0.05f; t = 34f; h = 0.95f; g = 0.88f; d = RWD;
            } else if (n.startsWith("Kei") || is(n, "Retro kei")) {
                k = 170f; p = 0.05f; t = 34f; h = 0.62f; g = 0.95f;
                if (is(n, "Kei truck", "Kei van", "Kei box truck")) { h = 0.75f; d = RWD; }
                if (is(n, "Kei EV")) p = 0.06f;
            } else if (is(n, "Minivan", "Box minivan", "Delivery van", "Post van", "Cash van", "EV delivery van",
                    "SUV", "Crossover", "Pickup", "Yaki-imo truck")) {
                k = 210f; p = 0.055f; t = 37f; h = 0.8f; g = 0.93f;
                if (is(n, "Pickup", "Yaki-imo truck", "Cash van")) d = RWD;
            } else if (is(n, "Mini 4x4", "Classic 4x4")) {
                k = 215f; p = 0.055f; t = 34f; h = 0.85f; g = 0.92f; d = AWD;
            }
            mass = k * L * W;
            power = p;
            top = t;
            cgHeight = h;
            grip = g;
            drive = d;
        }

        private static boolean is(String n, String... names) {
            for (String s : names) if (s.equals(n)) return true;
            return false;
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
