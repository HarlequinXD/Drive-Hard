package com.harlequinxd.drivehard.core.sprite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pixel-art cars drawn from blueprints.
 *
 * A blueprint is a car's side profile (lower body and cabin), width, wheels, windows, lights and
 * extras, in metres. The kit draws it at any of 16 angles on a perspective guide (seen slightly
 * from above, 32 px per metre) with flat colours from a small palette: each panel takes a shade
 * of the paint by which way it faces (lit from the upper left), then the details go on in the
 * hand-drawn style of the first pixel car (glass with a sky band and streaks, door shut lines,
 * handles, rubbing strips, light clusters, grilles, plates, alloys) and a selective outline
 * closes it.
 */
public final class PixelCars {
    public static final float PPM = 32f;
    public static final int FW = 208, FH = 120, GROUND = 108;
    private static final float SINP = 0.18f, COSP = (float) Math.sqrt(1 - SINP * SINP);

    // ---------------------------------------------------------------- palette

    private static final int G1 = 0xFFC4DEF0, G2 = 0xFF7898BE, G3 = 0xFF425678, G4 = 0xFF222A3E;
    private static final int C1 = 0xFFF0F2F6, C2 = 0xFFB6BAC4, C3 = 0xFF787C88, C4 = 0xFF4E505C;
    private static final int K1 = 0xFF34343C, K2 = 0xFF1E1E24, K3 = 0xFF101014;
    private static final int Y1 = 0xFFFFFCE2, Y2 = 0xFFE2DCB6, O1 = 0xFFFFA830;
    private static final int L1 = 0xFFFF5C4A, L2 = 0xFFC41E22, L3 = 0xFF781016;
    private static final int P1 = 0xFFF4F4E8, P2 = 0xFF285C3C;

    /** A paint's shades: highlight, base, shade, deep shade, darkest (shut lines). */
    static int[] ramp(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return new int[]{
                mix(r, g, b, 255, 244, 226, 0.38f),
                0xFF000000 | rgb,
                shade(r, g, b, 0.74f),
                shade(r, g, b, 0.52f),
                shade(r, g, b, 0.34f),
        };
    }

    private static int mix(int r, int g, int b, int r2, int g2, int b2, float t) {
        return 0xFF000000 | ((int) (r + (r2 - r) * t) << 16) | ((int) (g + (g2 - g) * t) << 8) | (int) (b + (b2 - b) * t);
    }

    /** Darker, shifted a little toward blue (pixel-art shadows). */
    private static int shade(int r, int g, int b, float k) {
        int rr = (int) (r * k * 0.94f + 28 * (1 - k) * 0.3f), gg = (int) (g * k * 0.96f + 20 * (1 - k) * 0.3f), bb = (int) (b * k + 70 * (1 - k) * 0.4f);
        return 0xFF000000 | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
    }

    // ---------------------------------------------------------------- blueprints

    /** Edge roles of a profile (each edge runs from point i to i + 1, the last back to 0). */
    static final int SKIP = 0, PLAIN = 1, TOP = 2, FRONT = 3, REAR = 4, BUMP_F = 5, BUMP_R = 6,
            GLASS_F = 7, GLASS_R = 8, ROOF = 9, PANEL_R = 10;

    public static final int HL_RECT = 0, HL_ROUND = 1, HL_SLIM = 2;
    public static final int WH_ALLOY = 0, WH_STEEL = 1, WH_CAP = 2;
    public static final int X_RACK = 1, X_TAXI = 2, X_SPOILER = 4, X_SPARE = 8, X_BED = 16, X_STRIPE = 32, X_BLACK_PILLARS = 64,
            X_SLIDER = 128, X_NO_REAR_SIDE_GLASS = 256, X_SOFTTOP = 512, X_LIGHTBAR = 1024, X_CHROME = 2048,
            X_CARGO = 4096, X_LADDER = 8192, X_TOW = 16384, X_SCOOP = 32768, X_MARKERS = 65536, X_HIGHTOP = 131072,
            X_CRANE = 262144, X_STALL = 524288;
    /** Cargo bodies: a plain box with rear doors, roller shutters (fire engine), a refuse compactor. */
    public static final int BOX_PLAIN = 0, BOX_SHUTTERS = 1, BOX_PACKER = 2;

    public static final class Model {
        public final String name;
        float len, wid, wheelR, axleF, axleR;
        float[] lower; int[] lowerRole;
        float[] cabin; int[] cabinRole;
        float belt, tumble;
        float[] pillars = {};
        float pillarW = 0.1f;
        float[] doors = {};
        float crease = 0.45f, strip = 0.55f;
        int head = HL_RECT, wheel = WH_ALLOY, extras;
        public int[] paints;
        /** Stripe colour (with X_STRIPE); a second colour for the cabin (two-tone), 0 for none. */
        int stripe = 0xFFB8B8B8, cabinColour;
        /** Light bar colours (with X_LIGHTBAR): left and right. */
        int barL = 0xFFE02028, barR = 0xFFE02028;
        /** Cargo body (with X_CARGO): top height, colour, style. */
        float boxTop;
        int boxColour = 0xFFE4E6EA, boxStyle = BOX_PLAIN;
        /** Rim colours (light, dark): chrome by default. */
        int rimLight = C2, rimDark = C3;
        /** Bed walls: top height (0: the belt) and colour (0: the paint). */
        float bedTop;
        int bedColour;

        Model(String name) { this.name = name; }

        /** Overall length and width in metres. */
        public float length() { return len; }
        public float width() { return wid; }
    }

    /**
     * A common shape: bumper, nose, bonnet to the windscreen, the cabin, then a boot deck (or
     * none, for a hatch) and the tail.
     */
    private static Model car(String name, float len, float wid, float sill, float bumper, float noseTop, float hoodLen,
                             float belt, float roofFrontU, float roofRearU, float roof, float rearGlassU, float deckH,
                             float tailBottom, float wheelR, float wheelbase, float axleShift) {
        Model m = new Model(name);
        float f = len / 2, r = -len / 2;
        m.len = len; m.wid = wid; m.wheelR = wheelR; m.belt = belt;
        m.axleF = axleShift + wheelbase / 2; m.axleR = axleShift - wheelbase / 2;
        float ws = f - hoodLen;                       // windscreen base
        m.lower = new float[]{
                f - 0.06f, sill,
                f, sill + bumper,
                f - 0.03f, noseTop,
                ws, belt,
                rearGlassU, deckH,
                r + 0.03f, deckH,
                r, tailBottom,
                r + 0.05f, sill,
        };
        m.lowerRole = new int[]{BUMP_F, FRONT, TOP, SKIP, TOP, REAR, BUMP_R, SKIP};
        m.cabin = new float[]{
                ws, belt,
                roofFrontU, roof,
                roofRearU, roof,
                rearGlassU, deckH,
        };
        m.cabinRole = new int[]{GLASS_F, ROOF, GLASS_R, SKIP};
        m.tumble = 0.12f;
        return m;
    }

    public static final Model[] MODELS = models();

    private static Model[] models() {
        List<Model> out = new ArrayList<>();
        Model m;

        // 1. kei car: a tall little box
        m = car("Kei car", 3.39f, 1.47f, 0.2f, 0.18f, 0.72f, 0.62f, 0.93f, 0.6f, -1.58f, 1.62f, -1.66f, 0.95f, 0.5f, 0.27f, 2.36f, 0.02f);
        m.pillars = new float[]{-0.05f}; m.doors = new float[]{0.82f, -0.05f, -0.86f};
        m.head = HL_SLIM; m.wheel = WH_CAP; m.tumble = 0.08f;
        m.paints = new int[]{0xF2F2EE, 0x9CC6E6, 0xC4C8CE, 0xE8C6C0};
        out.add(m);

        // 2. kei truck: cab over the front wheels, an open bed
        m = car("Kei truck", 3.39f, 1.47f, 0.22f, 0.2f, 0.9f, 0.05f, 0.93f, 1.5f, 0.5f, 1.76f, 0.42f, 0.72f, 0.56f, 0.26f, 1.9f, 0.05f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.lower[8] = 0.42f; m.lower[9] = 0.72f;
        m.pillars = new float[]{}; m.doors = new float[]{1.55f, 0.48f};
        m.head = HL_ROUND; m.wheel = WH_STEEL; m.extras = X_BED; m.tumble = 0.06f; m.crease = 0.5f; m.strip = 0.6f;
        m.paints = new int[]{0xF2F2EE, 0x5A8CC8, 0xC4C8CE, 0x6E9E6A};
        out.add(m);

        // 3. hatchback (the first pixel car)
        m = car("Hatchback", 3.9f, 1.68f, 0.22f, 0.2f, 0.74f, 1.3f, 0.95f, 0.15f, -1.4f, 1.45f, -1.86f, 0.95f, 0.5f, 0.28f, 2.45f, 0.08f);
        m.pillars = new float[]{-0.45f, -1.25f}; m.doors = new float[]{0.62f, -0.45f, -1.3f};
        m.paints = new int[]{0xE23A38, 0xF2F2EE, 0x2E4E9A, 0x1E1E24};
        out.add(m);

        // 4. sedan: three boxes
        m = car("Sedan", 4.4f, 1.69f, 0.2f, 0.2f, 0.72f, 1.3f, 0.92f, 0.3f, -0.95f, 1.38f, -1.4f, 0.96f, 0.5f, 0.29f, 2.55f, 0.08f);
        m.pillars = new float[]{-0.35f}; m.doors = new float[]{0.85f, -0.35f, -1.35f};
        m.paints = new int[]{0xC4C8CE, 0xF2F2EE, 0x2A2A30, 0x6A1E28};
        out.add(m);

        // 5. wagon: a long roof and a roof rack
        m = car("Wagon", 4.55f, 1.69f, 0.2f, 0.2f, 0.72f, 1.3f, 0.92f, 0.38f, -2.08f, 1.46f, -2.2f, 0.95f, 0.5f, 0.29f, 2.6f, 0.12f);
        m.pillars = new float[]{-0.3f, -1.35f}; m.doors = new float[]{0.92f, -0.3f, -1.4f};
        m.extras = X_RACK;
        m.paints = new int[]{0x3A6E5A, 0xC4C8CE, 0xF2F2EE, 0x7A5A3A};
        out.add(m);

        // 6. minivan: one sloping box, a sliding door
        m = car("Minivan", 4.7f, 1.76f, 0.22f, 0.22f, 0.86f, 0.8f, 1.02f, 1.05f, -2.25f, 1.82f, -2.33f, 1.04f, 0.56f, 0.3f, 2.85f, 0.15f);
        m.pillars = new float[]{0.55f, -0.95f}; m.doors = new float[]{1.48f, 0.55f, -0.98f};
        m.extras = X_SLIDER; m.head = HL_SLIM;
        m.paints = new int[]{0xF2F2EE, 0x8A96A8, 0x2E3A5A, 0xB0283A};
        out.add(m);

        // 7. SUV: high, square, a spare wheel on the back
        m = car("SUV", 4.3f, 1.78f, 0.42f, 0.2f, 1.0f, 1.05f, 1.2f, 0.62f, -1.95f, 1.88f, -2.06f, 1.22f, 0.74f, 0.37f, 2.55f, 0.0f);
        m.pillars = new float[]{-0.4f, -1.35f}; m.doors = new float[]{1.0f, -0.4f, -1.4f};
        m.extras = X_SPARE | X_RACK | X_STRIPE; m.wheel = WH_STEEL; m.head = HL_ROUND; m.tumble = 0.08f; m.crease = 0.7f; m.strip = 0.82f;
        m.paints = new int[]{0x2A2A30, 0x6E1E24, 0xC4C8CE, 0x3E5A3E};
        out.add(m);

        // 8. sports coupe: low, long bonnet, pop-up lights, a wing
        m = car("Coupe", 4.4f, 1.7f, 0.17f, 0.18f, 0.6f, 1.55f, 0.8f, -0.1f, -0.75f, 1.28f, -1.65f, 0.86f, 0.42f, 0.29f, 2.5f, 0.0f);
        m.pillars = new float[]{-0.68f}; m.doors = new float[]{0.65f, -0.62f};
        m.extras = X_SPOILER | X_NO_REAR_SIDE_GLASS; m.head = HL_SLIM; m.crease = 0.36f; m.strip = 0.46f;
        m.paints = new int[]{0xF2F2EE, 0xD8282E, 0x1E1E24, 0xF0C020};
        out.add(m);

        // 9. taxi: a square sedan with its roof sign
        m = car("Taxi", 4.6f, 1.69f, 0.2f, 0.22f, 0.76f, 1.3f, 0.95f, 0.32f, -1.05f, 1.48f, -1.5f, 1.0f, 0.52f, 0.29f, 2.68f, 0.08f);
        m.pillars = new float[]{-0.35f}; m.doors = new float[]{0.85f, -0.35f, -1.45f};
        m.extras = X_TAXI | X_STRIPE; m.wheel = WH_CAP; m.stripe = 0xFFF2F2EE;
        m.paints = new int[]{0x1E1E24, 0xF0C028, 0x2E7A4A, 0xF2F2EE};
        out.add(m);

        // 10. delivery van: a tall box with a short nose
        m = car("Delivery van", 4.69f, 1.69f, 0.22f, 0.22f, 0.92f, 0.32f, 1.02f, 1.85f, -2.3f, 1.98f, -2.33f, 1.02f, 0.6f, 0.3f, 2.57f, 0.25f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{1.2f}; m.doors = new float[]{2.0f, 1.2f, 0.3f};
        m.extras = X_NO_REAR_SIDE_GLASS | X_SLIDER | X_STRIPE; m.wheel = WH_STEEL; m.head = HL_RECT; m.tumble = 0.05f; m.stripe = 0xFF2E7A4A;
        m.crease = 0.5f; m.strip = 0.62f;
        m.paints = new int[]{0xF2F2EE, 0xC4C8CE, 0x2E4E9A, 0x2E7A4A};
        out.add(m);

        // ---- batch 2

        // 11. roadster: low, open-top style with a black soft top, pop-up lights
        m = car("Roadster", 3.95f, 1.68f, 0.16f, 0.18f, 0.56f, 1.45f, 0.76f, 0.12f, -0.52f, 1.2f, -0.92f, 0.8f, 0.42f, 0.28f, 2.27f, 0.05f);
        m.pillars = new float[]{}; m.doors = new float[]{0.55f, -0.5f};
        m.extras = X_SOFTTOP | X_NO_REAR_SIDE_GLASS | X_BLACK_PILLARS; m.head = HL_SLIM; m.crease = 0.34f; m.strip = 0.44f; m.tumble = 0.16f;
        m.paints = new int[]{0xC82028, 0x9CB4D2, 0x1E4E36, 0xF2F2EE};
        out.add(m);

        // 12. hot hatch: three doors, a wing, alloys
        m = car("Hot hatch", 4.0f, 1.7f, 0.17f, 0.2f, 0.66f, 1.25f, 0.9f, 0.1f, -1.45f, 1.35f, -1.92f, 0.88f, 0.48f, 0.29f, 2.57f, 0.1f);
        m.pillars = new float[]{-0.72f}; m.doors = new float[]{0.65f, -0.75f};
        m.extras = X_SPOILER; m.crease = 0.38f; m.strip = 0.48f;
        m.paints = new int[]{0xF2F2EE, 0xF0C020, 0x1E1E24, 0x2A4EA8};
        out.add(m);

        // 13. luxury sedan: long, chrome grille
        m = car("Luxury sedan", 4.9f, 1.8f, 0.2f, 0.22f, 0.76f, 1.45f, 0.95f, 0.3f, -1.15f, 1.42f, -1.62f, 1.0f, 0.55f, 0.32f, 2.92f, 0.1f);
        m.pillars = new float[]{-0.42f}; m.doors = new float[]{0.95f, -0.42f, -1.55f};
        m.extras = X_CHROME | X_STRIPE; m.stripe = 0xFFD8DCE4; m.strip = 0.52f;
        m.paints = new int[]{0x16161C, 0xF0EEE6, 0xC8B48A, 0x1E2A48};
        out.add(m);

        // 14. patrol car: black body, white cabin, red light bar
        m = car("Patrol car", 4.7f, 1.7f, 0.2f, 0.22f, 0.76f, 1.32f, 0.95f, 0.32f, -1.08f, 1.46f, -1.52f, 1.0f, 0.52f, 0.3f, 2.73f, 0.08f);
        m.pillars = new float[]{-0.38f}; m.doors = new float[]{0.88f, -0.38f, -1.48f};
        m.extras = X_LIGHTBAR | X_STRIPE; m.cabinColour = 0xF2F2EE; m.stripe = 0xFFF2F2EE; m.wheel = WH_STEEL;
        m.paints = new int[]{0x16161C};
        out.add(m);

        // 15. kei van: a tall box on a stubby nose
        m = car("Kei van", 3.39f, 1.47f, 0.22f, 0.2f, 0.88f, 0.3f, 0.95f, 1.25f, -1.62f, 1.88f, -1.66f, 0.95f, 0.55f, 0.25f, 2.35f, 0.1f);
        m.pillars = new float[]{0.55f, -0.62f}; m.doors = new float[]{1.4f, 0.55f, -0.62f};
        m.extras = X_SLIDER; m.wheel = WH_STEEL; m.tumble = 0.05f; m.crease = 0.5f; m.strip = 0.62f;
        m.paints = new int[]{0xF2F2EE, 0xC4C8CE, 0xA8CCE4, 0xF0D040};
        out.add(m);

        // 16. big box minivan: square, chrome everywhere
        m = car("Box minivan", 4.85f, 1.83f, 0.24f, 0.24f, 0.95f, 0.85f, 1.05f, 0.98f, -2.36f, 1.92f, -2.42f, 1.06f, 0.62f, 0.33f, 2.95f, 0.15f);
        m.pillars = new float[]{0.5f, -0.98f}; m.doors = new float[]{1.55f, 0.5f, -1.0f};
        m.extras = X_SLIDER | X_CHROME; m.head = HL_SLIM; m.tumble = 0.06f;
        m.paints = new int[]{0x16161C, 0xF0EEE6, 0xB8BCC4, 0x5A1A24};
        out.add(m);

        // 17. mini 4x4: tiny, upright, round lamps, spare on the back
        m = car("Mini 4x4", 3.4f, 1.48f, 0.38f, 0.2f, 0.98f, 0.95f, 1.12f, 0.48f, -1.55f, 1.72f, -1.62f, 1.14f, 0.68f, 0.34f, 2.2f, 0.05f);
        m.pillars = new float[]{-0.48f}; m.doors = new float[]{0.75f, -0.48f};
        m.extras = X_SPARE; m.head = HL_ROUND; m.wheel = WH_STEEL; m.tumble = 0.05f; m.crease = 0.68f; m.strip = 0.8f;
        m.paints = new int[]{0x6E7A4A, 0xF2F2EE, 0xF0C028, 0xB8282A};
        out.add(m);

        // 18. microbus: long, rows of windows
        m = car("Microbus", 5.38f, 1.88f, 0.24f, 0.24f, 0.95f, 0.42f, 1.05f, 2.3f, -2.65f, 2.28f, -2.68f, 1.05f, 0.6f, 0.32f, 3.1f, 0.3f);
        m.pillars = new float[]{1.6f, 0.62f, -0.4f, -1.42f}; m.doors = new float[]{2.42f, 1.6f, 0.62f, -0.4f};
        m.extras = X_SLIDER | X_STRIPE; m.stripe = 0xFF2A4E9A; m.wheel = WH_STEEL; m.tumble = 0.05f; m.strip = 0.66f;
        m.paints = new int[]{0xF2F2EE, 0xC4C8CE};
        out.add(m);

        // 19. ambulance: high roof, red stripe, red light bar
        m = car("Ambulance", 5.6f, 1.88f, 0.24f, 0.24f, 0.98f, 0.45f, 1.08f, 2.38f, -2.75f, 2.48f, -2.78f, 1.08f, 0.62f, 0.33f, 3.1f, 0.35f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{1.62f}; m.doors = new float[]{2.45f, 1.62f, 0.4f};
        m.extras = X_STRIPE | X_LIGHTBAR | X_SLIDER | X_NO_REAR_SIDE_GLASS; m.stripe = 0xFFD8242C; m.wheel = WH_STEEL; m.tumble = 0.04f; m.strip = 0.7f;
        m.paints = new int[]{0xF6F6F2};
        out.add(m);

        // 20. pickup: a bonnet, a single cab, a long bed
        m = car("Pickup", 4.7f, 1.69f, 0.3f, 0.22f, 0.86f, 1.35f, 1.05f, 0.95f, 0.02f, 1.62f, -0.05f, 0.88f, 0.6f, 0.33f, 2.85f, 0.4f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{1.2f, -0.02f};
        m.extras = X_BED; m.wheel = WH_STEEL; m.head = HL_RECT; m.crease = 0.6f; m.strip = 0.72f; m.tumble = 0.06f;
        m.paints = new int[]{0xF2F2EE, 0xB8282A, 0xB8BCC4, 0x2E4A36};
        out.add(m);

        // ---- batch 3

        // 21. kei hatch: low and small, five doors
        m = car("Kei hatch", 3.39f, 1.47f, 0.18f, 0.18f, 0.66f, 0.75f, 0.86f, 0.55f, -1.45f, 1.42f, -1.62f, 0.88f, 0.46f, 0.25f, 2.36f, 0.04f);
        m.pillars = new float[]{-0.15f}; m.doors = new float[]{0.72f, -0.15f, -1.0f};
        m.wheel = WH_CAP; m.head = HL_SLIM; m.tumble = 0.1f; m.crease = 0.38f; m.strip = 0.48f;
        m.paints = new int[]{0xE6D0A8, 0x98C8C0, 0xF2F2EE, 0xE87890};
        out.add(m);

        // 22. fire engine: crew cab, roller-shutter lockers, ladder, red lights
        m = car("Fire engine", 5.6f, 1.9f, 0.3f, 0.26f, 1.05f, 0.25f, 1.2f, 2.45f, 1.05f, 2.25f, 0.95f, 1.05f, 0.66f, 0.38f, 3.0f, 0.4f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{1.8f}; m.doors = new float[]{2.55f, 1.8f, 1.0f};
        m.extras = X_CARGO | X_LADDER | X_LIGHTBAR | X_CHROME; m.boxTop = 2.45f; m.boxStyle = BOX_SHUTTERS; m.boxColour = 0xD82028;
        m.wheel = WH_STEEL; m.tumble = 0.04f; m.crease = 0.7f; m.strip = 0.82f;
        m.paints = new int[]{0xD82028};
        out.add(m);

        // 23. garbage truck: cab over, a compactor body
        m = car("Garbage truck", 5.2f, 1.85f, 0.3f, 0.26f, 1.05f, 0.25f, 1.2f, 2.35f, 1.6f, 2.2f, 1.5f, 1.05f, 0.66f, 0.36f, 2.7f, 0.5f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{2.5f, 1.55f};
        m.extras = X_CARGO; m.boxTop = 2.3f; m.boxStyle = BOX_PACKER; m.boxColour = 0x4A86C0; m.stripe = 0xFFF2F2EE;
        m.wheel = WH_STEEL; m.tumble = 0.04f; m.crease = 0.7f; m.strip = 0.82f;
        m.paints = new int[]{0xF2F2EE, 0x4A86C0};
        out.add(m);

        // 24. box truck: a two-tonne cab over with a tall aluminium box
        m = car("Box truck", 5.5f, 1.85f, 0.3f, 0.26f, 1.05f, 0.25f, 1.2f, 2.5f, 1.75f, 2.2f, 1.65f, 1.05f, 0.66f, 0.35f, 2.9f, 0.6f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{2.62f, 1.7f};
        m.extras = X_CARGO; m.boxTop = 2.55f; m.boxStyle = BOX_PLAIN; m.boxColour = 0xD8DCE2; m.stripe = 0xFF2E7A4A;
        m.wheel = WH_STEEL; m.tumble = 0.04f; m.crease = 0.7f; m.strip = 0.82f;
        m.paints = new int[]{0xF2F2EE, 0x2E4E9A, 0x2E7A4A, 0xD8DCE2};
        out.add(m);

        // 25. retro kei: tiny, rounded, round eyes
        m = car("Retro kei", 3.0f, 1.3f, 0.18f, 0.16f, 0.62f, 0.62f, 0.8f, 0.42f, -0.85f, 1.33f, -1.25f, 0.82f, 0.46f, 0.25f, 1.8f, 0.0f);
        m.pillars = new float[]{}; m.doors = new float[]{0.62f, -0.55f};
        m.extras = X_NO_REAR_SIDE_GLASS; m.head = HL_ROUND; m.wheel = WH_CAP; m.tumble = 0.14f; m.crease = 0.36f; m.strip = 0.44f;
        m.paints = new int[]{0xF0E4C8, 0x8CC4D8, 0xE8B848, 0xC84040};
        out.add(m);

        // 26. mid-engine sports car: cabin forward, an engine deck behind
        m = car("Midship sports", 4.4f, 1.81f, 0.14f, 0.16f, 0.52f, 1.25f, 0.74f, 0.3f, -0.42f, 1.17f, -1.0f, 0.86f, 0.44f, 0.31f, 2.53f, 0.0f);
        m.pillars = new float[]{}; m.doors = new float[]{0.95f, -0.45f};
        m.extras = X_SPOILER | X_NO_REAR_SIDE_GLASS | X_BLACK_PILLARS; m.head = HL_SLIM; m.tumble = 0.16f; m.crease = 0.32f; m.strip = 0.42f;
        m.paints = new int[]{0xD8282E, 0xF0C020, 0x1E1E24, 0xE8E8E8};
        out.add(m);

        // 27. crossover SUV: high, a sloping tail
        m = car("Crossover", 4.6f, 1.8f, 0.36f, 0.22f, 0.92f, 1.15f, 1.06f, 0.4f, -1.72f, 1.66f, -2.15f, 1.06f, 0.7f, 0.34f, 2.66f, 0.1f);
        m.pillars = new float[]{-0.4f, -1.55f}; m.doors = new float[]{0.98f, -0.4f, -1.5f};
        m.extras = X_RACK | X_BLACK_PILLARS; m.head = HL_SLIM; m.crease = 0.62f; m.strip = 0.72f;
        m.paints = new int[]{0xE8E6E0, 0x3A3A44, 0x7A2A30, 0x4A6A8A};
        out.add(m);

        // 28. tow truck: a pickup with a boom and an amber bar
        m = car("Tow truck", 4.9f, 1.72f, 0.32f, 0.24f, 0.88f, 1.3f, 1.07f, 1.05f, 0.1f, 1.66f, 0.05f, 0.9f, 0.62f, 0.34f, 2.9f, 0.35f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{1.3f, 0.08f};
        m.extras = X_BED | X_TOW | X_LIGHTBAR | X_STRIPE; m.barL = 0xFFFFA020; m.barR = 0xFFFFA020; m.stripe = 0xFF1E1E24;
        m.wheel = WH_STEEL; m.crease = 0.62f; m.strip = 0.74f; m.tumble = 0.06f;
        m.paints = new int[]{0xF0C028, 0xF2F2EE};
        out.add(m);

        // 29. post van: a red kei van, blind at the back, a white stripe
        m = car("Post van", 3.39f, 1.47f, 0.22f, 0.2f, 0.88f, 0.3f, 0.95f, 1.25f, -1.62f, 1.88f, -1.66f, 0.95f, 0.55f, 0.25f, 2.35f, 0.1f);
        m.pillars = new float[]{0.55f}; m.doors = new float[]{1.4f, 0.55f, -0.62f};
        m.extras = X_SLIDER | X_NO_REAR_SIDE_GLASS | X_STRIPE; m.stripe = 0xFFF2F2EE; m.wheel = WH_STEEL; m.tumble = 0.05f; m.crease = 0.5f; m.strip = 0.66f;
        m.paints = new int[]{0xD8242C};
        out.add(m);

        // 30. kindergarten bus: a short yellow bus with a stripe
        m = car("Kindergarten bus", 4.84f, 1.8f, 0.24f, 0.24f, 0.95f, 0.4f, 1.05f, 1.8f, -2.38f, 2.2f, -2.4f, 1.05f, 0.6f, 0.3f, 2.8f, 0.25f);
        m.pillars = new float[]{1.2f, 0.25f, -0.7f, -1.6f}; m.doors = new float[]{2.0f, 1.2f};
        m.extras = X_STRIPE; m.stripe = 0xFFE86A2A; m.wheel = WH_STEEL; m.tumble = 0.06f; m.strip = 0.62f;
        m.paints = new int[]{0xF6CC30, 0xF2F2EE};
        out.add(m);

        // ---- batch 4

        // 31. dekotora: a decorated truck, chrome box, painted panels, marker lights everywhere
        m = car("Dekotora", 5.6f, 1.88f, 0.3f, 0.3f, 1.05f, 0.25f, 1.2f, 2.5f, 1.75f, 2.2f, 1.65f, 1.05f, 0.66f, 0.35f, 2.9f, 0.6f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{2.62f, 1.7f};
        m.extras = X_CARGO | X_MARKERS | X_CHROME | X_LIGHTBAR; m.boxTop = 2.6f; m.boxColour = 0xE8EAF0; m.stripe = 0xFFE8C040;
        m.barL = 0xFFFFE070; m.barR = 0xFFFFE070; m.wheel = WH_STEEL; m.tumble = 0.04f; m.crease = 0.7f; m.strip = 0.82f;
        m.paints = new int[]{0x2A4EA8, 0xF2F2EE, 0x1E1E24};
        out.add(m);

        // 32. shakotan sedan: slammed to the ground, a wing, deep dish rims
        m = car("Shakotan", 4.55f, 1.7f, 0.08f, 0.18f, 0.6f, 1.3f, 0.82f, 0.25f, -1.0f, 1.26f, -1.45f, 0.88f, 0.4f, 0.27f, 2.62f, 0.08f);
        m.pillars = new float[]{-0.38f}; m.doors = new float[]{0.85f, -0.38f, -1.4f};
        m.extras = X_SPOILER | X_STRIPE; m.stripe = 0xFFE8C040; m.crease = 0.3f; m.strip = 0.42f; m.rimLight = C1; m.rimDark = C2;
        m.paints = new int[]{0x6A1A8A, 0x1E1E24, 0xF2F2EE, 0x1A6A4A};
        out.add(m);

        // 33. sixties sedan: round lamps, chrome, a white roof
        m = car("Sixties sedan", 4.6f, 1.69f, 0.24f, 0.24f, 0.74f, 1.35f, 0.95f, 0.38f, -0.95f, 1.46f, -1.38f, 1.0f, 0.55f, 0.3f, 2.69f, 0.05f);
        m.pillars = new float[]{-0.3f}; m.doors = new float[]{0.92f, -0.3f, -1.32f};
        m.extras = X_CHROME | X_STRIPE; m.stripe = 0xFFE4E8EE; m.cabinColour = 0xF2F0E6; m.head = HL_ROUND; m.wheel = WH_CAP;
        m.paints = new int[]{0x5A8CB8, 0xB83A3A, 0x3A6A5A, 0xD8C49A};
        out.add(m);

        // 34. kei sports coupe: tiny, low, a black hard top
        m = car("Kei sports", 3.3f, 1.4f, 0.13f, 0.16f, 0.52f, 1.15f, 0.72f, 0.0f, -0.55f, 1.18f, -0.95f, 0.76f, 0.4f, 0.25f, 2.06f, 0.0f);
        m.pillars = new float[]{}; m.doors = new float[]{0.5f, -0.45f};
        m.extras = X_SOFTTOP | X_BLACK_PILLARS | X_NO_REAR_SIDE_GLASS; m.head = HL_SLIM; m.tumble = 0.14f; m.crease = 0.3f; m.strip = 0.4f;
        m.paints = new int[]{0xD8282E, 0xF0C020, 0xF2F2EE, 0x2A4EA8};
        out.add(m);

        // 35. tall taxi: the modern one-box cab, deep indigo, roof sign
        m = car("Tall taxi", 4.4f, 1.7f, 0.2f, 0.24f, 0.85f, 0.95f, 1.0f, 0.9f, -1.9f, 1.75f, -2.0f, 1.02f, 0.56f, 0.3f, 2.75f, 0.1f);
        m.pillars = new float[]{0.0f, -1.2f}; m.doors = new float[]{1.25f, 0.0f, -1.15f};
        m.extras = X_TAXI | X_SLIDER | X_BLACK_PILLARS; m.head = HL_SLIM; m.tumble = 0.08f;
        m.paints = new int[]{0x1E2448, 0x1E1E24};
        out.add(m);

        // 36. kei box truck: the kei truck with an aluminium box
        m = car("Kei box truck", 3.39f, 1.47f, 0.22f, 0.2f, 0.9f, 0.05f, 0.93f, 1.5f, 0.5f, 1.76f, 0.42f, 0.72f, 0.56f, 0.26f, 1.9f, 0.05f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{1.55f, 0.48f};
        m.extras = X_CARGO; m.boxTop = 1.95f; m.boxColour = 0xD8DCE2; m.stripe = 0xFF2A4EA8;
        m.head = HL_ROUND; m.wheel = WH_STEEL; m.tumble = 0.06f; m.crease = 0.5f; m.strip = 0.6f;
        m.paints = new int[]{0xF2F2EE, 0x5A8CC8};
        out.add(m);

        // 37. rally sedan: bonnet scoop, a big wing, gold wheels
        m = car("Rally sedan", 4.4f, 1.7f, 0.2f, 0.2f, 0.66f, 1.3f, 0.88f, 0.28f, -0.95f, 1.4f, -1.42f, 0.92f, 0.46f, 0.3f, 2.52f, 0.06f);
        m.pillars = new float[]{-0.35f}; m.doors = new float[]{0.85f, -0.35f, -1.3f};
        m.extras = X_SPOILER | X_SCOOP; m.rimLight = 0xFFE8C048; m.rimDark = 0xFFA88A28; m.crease = 0.36f; m.strip = 0.46f;
        m.paints = new int[]{0x1E3E98, 0xF2F2EE, 0xD8282E, 0x1E1E24};
        out.add(m);

        // 38. limousine: very long, black, all chrome
        m = car("Limousine", 5.3f, 1.9f, 0.2f, 0.24f, 0.8f, 1.55f, 0.98f, 0.4f, -1.4f, 1.48f, -1.85f, 1.04f, 0.58f, 0.33f, 3.0f, 0.12f);
        m.pillars = new float[]{-0.5f}; m.doors = new float[]{1.0f, -0.5f, -1.75f};
        m.extras = X_CHROME | X_STRIPE; m.stripe = 0xFFD8DCE4; m.wheel = WH_CAP; m.strip = 0.56f;
        m.paints = new int[]{0x101016, 0x1E2A48};
        out.add(m);

        // 39. camper: a van with a high roof and an awning stripe
        m = car("Camper", 4.84f, 1.8f, 0.24f, 0.24f, 0.95f, 0.4f, 1.05f, 1.8f, -2.38f, 1.98f, -2.4f, 1.05f, 0.6f, 0.3f, 2.8f, 0.25f);
        m.pillars = new float[]{1.2f, -0.6f}; m.doors = new float[]{2.0f, 1.2f, 0.2f};
        m.extras = X_HIGHTOP | X_STRIPE | X_SLIDER; m.stripe = 0xFF3A8AC8; m.wheel = WH_STEEL; m.tumble = 0.06f; m.strip = 0.64f;
        m.paints = new int[]{0xF2F2EE, 0xE8DCC0};
        out.add(m);

        // 40. dump truck: cab over, a steel tipper with tall walls
        m = car("Dump truck", 5.0f, 1.85f, 0.3f, 0.26f, 1.05f, 0.25f, 1.2f, 2.3f, 1.5f, 2.2f, 1.4f, 1.0f, 0.66f, 0.36f, 2.6f, 0.45f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{2.35f, 1.45f};
        m.extras = X_BED; m.bedTop = 1.72f; m.bedColour = 0xFF8A8E96;
        m.wheel = WH_STEEL; m.tumble = 0.04f; m.crease = 0.7f; m.strip = 0.82f;
        m.paints = new int[]{0xF2F2EE, 0x4A7A3A, 0xE8B828, 0x2A4EA8};
        out.add(m);

        // ---- batch 5

        // 41. hybrid hatch: a wedge with a high, cut-off tail
        m = car("Hybrid hatch", 4.5f, 1.75f, 0.16f, 0.2f, 0.62f, 1.35f, 0.92f, -0.1f, -1.0f, 1.48f, -2.18f, 1.02f, 0.5f, 0.31f, 2.7f, 0.05f);
        m.pillars = new float[]{-0.4f, -1.5f}; m.doors = new float[]{0.92f, -0.4f, -1.45f};
        m.extras = X_BLACK_PILLARS; m.head = HL_SLIM; m.tumble = 0.14f; m.crease = 0.4f; m.strip = 0.5f;
        m.paints = new int[]{0xE8E8E8, 0xB8BCC4, 0x2A4E7A, 0x7A2A30};
        out.add(m);

        // 42. kei EV: a little egg
        m = car("Kei EV", 3.4f, 1.47f, 0.16f, 0.18f, 0.7f, 0.55f, 0.9f, 0.82f, -1.25f, 1.6f, -1.62f, 0.92f, 0.48f, 0.26f, 2.55f, 0.0f);
        m.pillars = new float[]{-0.15f}; m.doors = new float[]{0.95f, -0.15f, -1.1f};
        m.head = HL_SLIM; m.wheel = WH_CAP; m.tumble = 0.16f; m.crease = 0.4f; m.strip = 0.5f;
        m.paints = new int[]{0xF2F2EE, 0x9CC6E6, 0xB8D86A, 0xE87890};
        out.add(m);

        // 43. eighties coupe: long, two doors, angular
        m = car("Eighties coupe", 4.66f, 1.72f, 0.18f, 0.2f, 0.66f, 1.5f, 0.88f, 0.08f, -0.92f, 1.34f, -1.42f, 0.92f, 0.48f, 0.3f, 2.67f, 0.05f);
        m.pillars = new float[]{-0.62f}; m.doors = new float[]{0.72f, -0.6f};
        m.extras = X_STRIPE | X_BLACK_PILLARS; m.stripe = 0xFFC8A050; m.head = HL_SLIM; m.crease = 0.38f; m.strip = 0.5f;
        m.paints = new int[]{0xF0EEE6, 0x1E1E24, 0x8A1E28, 0x6A7A8A};
        out.add(m);

        // 44. cash van: armoured, grey, slit windows, a light bar
        m = car("Cash van", 4.7f, 1.75f, 0.24f, 0.24f, 0.95f, 0.75f, 1.05f, 1.15f, -2.3f, 2.05f, -2.33f, 1.05f, 0.6f, 0.32f, 2.6f, 0.15f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{0.95f}; m.doors = new float[]{1.6f, 0.95f, 0.2f};
        m.extras = X_NO_REAR_SIDE_GLASS | X_STRIPE | X_LIGHTBAR; m.stripe = 0xFF2A5AA8; m.barL = 0xFFFFA020; m.barR = 0xFFFFA020;
        m.wheel = WH_STEEL; m.tumble = 0.04f; m.strip = 0.66f;
        m.paints = new int[]{0x7A7E86, 0xD8DCE2};
        out.add(m);

        // 45. kei patrol car: a small black-and-white with a red bar
        m = car("Kei patrol", 3.39f, 1.47f, 0.2f, 0.18f, 0.72f, 0.62f, 0.93f, 0.6f, -1.58f, 1.62f, -1.66f, 0.95f, 0.5f, 0.27f, 2.36f, 0.02f);
        m.pillars = new float[]{-0.05f}; m.doors = new float[]{0.82f, -0.05f, -0.86f};
        m.extras = X_LIGHTBAR | X_STRIPE; m.cabinColour = 0xF2F2EE; m.stripe = 0xFFF2F2EE; m.head = HL_SLIM; m.wheel = WH_STEEL; m.tumble = 0.08f;
        m.paints = new int[]{0x16161C};
        out.add(m);

        // 46. drift coupe: livery stripes, a wing, a lowered stance
        m = car("Drift coupe", 4.5f, 1.7f, 0.12f, 0.18f, 0.58f, 1.5f, 0.8f, 0.05f, -0.82f, 1.25f, -1.48f, 0.86f, 0.42f, 0.29f, 2.48f, 0.05f);
        m.pillars = new float[]{-0.7f}; m.doors = new float[]{0.7f, -0.65f};
        m.extras = X_SPOILER | X_STRIPE | X_SCOOP; m.stripe = 0xFF1EB8E0; m.head = HL_SLIM; m.crease = 0.32f; m.strip = 0.45f;
        m.rimLight = C1; m.rimDark = K1;
        m.paints = new int[]{0xF2F2EE, 0xE8862A, 0x1E1E24, 0x3A8A3A};
        out.add(m);

        // 47. classic 4x4: short, square, round lamps, a white roof
        m = car("Classic 4x4", 3.85f, 1.66f, 0.42f, 0.22f, 1.02f, 1.15f, 1.18f, 0.45f, -1.7f, 1.9f, -1.85f, 1.2f, 0.75f, 0.36f, 2.28f, 0.1f);
        m.pillars = new float[]{-0.3f}; m.doors = new float[]{0.62f, -0.32f};
        m.extras = X_SPARE; m.cabinColour = 0xF2F2EE; m.head = HL_ROUND; m.wheel = WH_STEEL; m.tumble = 0.03f; m.crease = 0.72f; m.strip = 0.85f;
        m.paints = new int[]{0x7A9AB8, 0xC8A050, 0xB83A2A, 0x5A6A4A};
        out.add(m);

        // 48. EV delivery van: short, smooth, a tall box with a bright stripe
        m = car("EV delivery van", 4.4f, 1.8f, 0.22f, 0.24f, 0.98f, 0.55f, 1.08f, 1.3f, -2.15f, 2.15f, -2.18f, 1.08f, 0.62f, 0.31f, 2.85f, 0.1f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{0.95f}; m.doors = new float[]{1.65f, 0.95f, 0.2f};
        m.extras = X_NO_REAR_SIDE_GLASS | X_STRIPE | X_SLIDER; m.stripe = 0xFF2AB86A; m.head = HL_SLIM; m.tumble = 0.05f; m.strip = 0.7f;
        m.paints = new int[]{0xF2F2EE, 0xD8DCE2};
        out.add(m);

        // 49. yaki-imo truck: a kei truck with a little wooden stall on the back
        m = car("Yaki-imo truck", 3.39f, 1.47f, 0.22f, 0.2f, 0.9f, 0.05f, 0.93f, 1.5f, 0.5f, 1.76f, 0.42f, 0.72f, 0.56f, 0.26f, 1.9f, 0.05f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{1.55f, 0.48f};
        m.extras = X_BED | X_STALL; m.head = HL_ROUND; m.wheel = WH_STEEL; m.tumble = 0.06f; m.crease = 0.5f; m.strip = 0.6f;
        m.paints = new int[]{0xF2F2EE, 0x5A8CC8};
        out.add(m);

        // 50. crane truck: a flatbed with a folded loader crane
        m = car("Crane truck", 5.6f, 1.88f, 0.3f, 0.26f, 1.05f, 0.25f, 1.2f, 2.5f, 1.75f, 2.2f, 1.65f, 1.0f, 0.66f, 0.35f, 3.0f, 0.55f);
        m.cabinRole = new int[]{GLASS_F, ROOF, PANEL_R, SKIP};
        m.pillars = new float[]{}; m.doors = new float[]{2.62f, 1.7f};
        m.extras = X_BED | X_CRANE; m.bedTop = 1.28f; m.bedColour = 0xFF6A6E76;
        m.wheel = WH_STEEL; m.tumble = 0.04f; m.crease = 0.7f; m.strip = 0.82f;
        m.paints = new int[]{0xF2F2EE, 0x2A4EA8, 0xE8B828};
        out.add(m);

        return out.toArray(new Model[0]);
    }

    // ---------------------------------------------------------------- the canvas

    private final int[] px = new int[FW * FH];

    private void fill(float[] xs, float[] ys, int n, int c) {
        if (n < 3) return;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) { minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]); }
        int y0 = Math.max(0, (int) Math.ceil(minY - 0.5f)), y1 = Math.min(FH - 1, (int) Math.floor(maxY - 0.5f));
        float[] xi = new float[n];
        for (int y = y0; y <= y1; y++) {
            float yc = y + 0.5f;
            int k = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ya = ys[i], yb = ys[j];
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) xi[k++] = xs[i] + (yc - ya) / (yb - ya) * (xs[j] - xs[i]);
            }
            Arrays.sort(xi, 0, k);
            for (int q = 0; q + 1 < k; q += 2) {
                int xa = Math.max(0, (int) Math.ceil(xi[q] - 0.5f)), xb = Math.min(FW - 1, (int) Math.floor(xi[q + 1] - 0.5f));
                for (int x = xa; x <= xb; x++) px[y * FW + x] = c;
            }
        }
    }

    private void line(float x0, float y0, float x1, float y1, int c) {
        int ax = Math.round(x0 - 0.5f), ay = Math.round(y0 - 0.5f), bx = Math.round(x1 - 0.5f), by = Math.round(y1 - 0.5f);
        int dx = Math.abs(bx - ax), dy = -Math.abs(by - ay), sx = ax < bx ? 1 : -1, sy = ay < by ? 1 : -1, err = dx + dy;
        while (true) {
            plot(ax, ay, c);
            if (ax == bx && ay == by) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; ax += sx; }
            if (e2 <= dx) { err += dx; ay += sy; }
        }
    }

    private void plot(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < FW && y < FH) px[y * FW + x] = c;
    }

    // ---------------------------------------------------------------- the view

    private float fx, fz, lx, lz;
    private final float[] sp = new float[3];

    /** Car space (u forward, v left, y up, metres) to screen x, y and nearness. */
    private float[] project(float u, float v, float y) {
        float x = u * fx + v * lx, z = u * fz + v * lz;
        sp[0] = FW / 2f + x * PPM;
        sp[1] = GROUND - (y * COSP - z * SINP) * PPM;
        sp[2] = z * COSP + y * SINP;
        return sp;
    }

    /** Is a car-space normal facing the camera? */
    private float facing(float nu, float nv, float ny) {
        float x = nu * fx + nv * lx, z = nu * fz + nv * lz;
        return z * COSP + ny * SINP;
    }

    /** The panel's light (fixed to the screen: from the upper left, in front). */
    private float light(float nu, float nv, float ny) {
        float x = nu * fx + nv * lx, z = nu * fz + nv * lz;
        return (-0.5f * x + 0.75f * ny + 0.45f * z) / 1.0062f;
    }

    private int shadeOf(int[] p, float nu, float nv, float ny) {
        float d = light(nu, nv, ny);
        if (ny > 0.6f || d > 0.72f) return p[0];
        if (d > 0.28f) return p[1];
        if (d > -0.12f) return p[2];
        return p[3];
    }

    // ---------------------------------------------------------------- faces

    /** A flat panel: a polygon in car space and how to draw on it. */
    private abstract static class Face {
        float depth;
        abstract void draw();
    }

    private final float[] qx = new float[64], qy = new float[64];

    /** Fills a car-space polygon (u, v, y triples). */
    private void poly(float[] p, int n, int c) {
        for (int i = 0; i < n; i++) {
            project(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
            qx[i] = sp[0]; qy[i] = sp[1];
        }
        fill(qx, qy, n, c);
    }

    private void line3(float u0, float v0, float y0, float u1, float v1, float y1, int c) {
        project(u0, v0, y0);
        float ax = sp[0], ay = sp[1];
        project(u1, v1, y1);
        line(ax, ay, sp[0], sp[1], c);
    }

    private float centreDepth(float[] p, int n) {
        float d = 0;
        for (int i = 0; i < n; i++) { project(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]); d += sp[2]; }
        return d / n;
    }

    // ---------------------------------------------------------------- drawing a car

    private Model m;
    private int[] paint, cabinRestore;

    /**
     * Draws a model in a paint, its facing {@code angle} 0..15 (0 toward the camera, 4 to the
     * right, 8 away). Returns FW x FH ARGB pixels (ground at row GROUND, centre column FW / 2).
     */
    public int[] draw(Model model, int paintIndex, int angle) {
        Arrays.fill(px, 0);
        m = model;
        paint = ramp(model.paints[paintIndex % model.paints.length]);
        double a = angle * Math.PI / 8;
        fx = (float) Math.sin(a); fz = (float) Math.cos(a);
        lx = (float) Math.cos(a); lz = (float) -Math.sin(a);
        float hw = m.wid / 2;
        // which side faces us
        float sideL = facing(0, 1, 0);
        float near = sideL >= 0 ? 1 : -1;
        List<Face> lower = new ArrayList<>(), upper = new ArrayList<>();
        // far wheels first, the body hides most of them
        wheels(-near);
        sideFace(lower, m.lower, hw, near, false);
        strips(lower, m.lower, m.lowerRole, hw, hw, false);
        // the near wheels sort in with the body panels: the bumpers and the nose are nearer
        // than the wheels in front and back views and must cover them
        for (final float au : new float[]{m.axleF, m.axleR}) {
            Face w = new Face() {
                @Override void draw() { wheel(au, near); }
            };
            w.depth = project(au, near * (m.wid / 2), m.wheelR)[2];
            lower.add(w);
        }
        sortDraw(lower);
        cabinFaces(upper, hw, near);
        // the truck bed's walls sort with the cab, so the cab's back never shows through them
        if ((m.extras & X_BED) != 0) bed(upper, hw);
        if ((m.extras & X_CARGO) != 0) cargo(upper, hw);
        if ((m.extras & X_TOW) != 0) tow(upper, hw);
        if ((m.extras & X_CRANE) != 0) crane(upper, hw);
        if ((m.extras & X_STALL) != 0) stall(upper, hw);
        extras(upper, hw);
        sortDraw(upper);
        if (cabinRestore != null) { paint = cabinRestore; cabinRestore = null; }
        mirrors(hw);
        outline();
        return px.clone();
    }

    private void sortDraw(List<Face> faces) {
        faces.sort((x, y) -> Float.compare(x.depth, y.depth));
        for (Face f : faces) f.draw();
    }

    /** The lower body's visible side, with arches, shading, strips, shut lines and handles. */
    private void sideFace(List<Face> out, final float[] prof, final float hw, final float near, boolean cabin) {
        final int n = prof.length / 2;
        if (Math.abs(facing(0, near, 0)) < 0.02f) return;
        final float v = near * hw;
        final float[] p = new float[n * 3];
        for (int i = 0; i < n; i++) { p[i * 3] = prof[i * 2]; p[i * 3 + 1] = v; p[i * 3 + 2] = prof[i * 2 + 1]; }
        Face f = new Face() {
            @Override void draw() {
                int base = shadeOf(paint, 0, near, 0);
                int lowerShade = darker(base);
                poly(p, n, base);
                // below the crease, a shade darker
                float[] low = clipBelow(prof, m.crease);
                if (low != null) poly(toSide(low, v), low.length / 2, lowerShade);
                line3(prof[0] - 0.05f, v, m.crease, -m.len / 2 + 0.05f, v, m.crease, paint[3]);
                // rubbing strip with a highlight over it
                if ((m.extras & X_STRIPE) != 0) {
                    float[] band = clipBand(prof, m.strip - 0.05f, m.strip + 0.03f);
                    if (band != null) poly(toSide(band, v), band.length / 2, paint == null ? K1 : stripeColour());
                } else {
                    line3(m.len / 2 - 0.08f, v, m.strip, -m.len / 2 + 0.08f, v, m.strip, K1);
                    line3(m.len / 2 - 0.08f, v, m.strip + 0.035f, -m.len / 2 + 0.08f, v, m.strip + 0.035f, paint[0]);
                }
                // wheel arches
                for (float au : new float[]{m.axleF, m.axleR}) arch(au, v);
                // sill
                line3(m.axleF - m.wheelR - 0.1f, v, prof[1] + 0.03f, m.axleR + m.wheelR + 0.1f, v, prof[1] + 0.03f, K1);
                // door shut lines and handles
                for (int i = 0; i < m.doors.length; i++) {
                    float du = m.doors[i];
                    float top = beltAt(du);
                    line3(du, v, top - 0.02f, du, v, prof[1] + 0.08f, paint[4]);
                    if (i + 1 < m.doors.length && !((m.extras & X_SLIDER) != 0 && i == 1 && m.doors.length > 3)) {
                        float hu = du - 0.14f;
                        line3(hu, v, top - 0.13f, hu - 0.1f, v, top - 0.13f, K1);
                    }
                }
                if ((m.extras & X_SLIDER) != 0 && m.doors.length > 2) {
                    // the sliding door's rail
                    line3(m.doors[1], v, m.belt - 0.06f, -m.len / 2 + 0.15f, v, m.belt - 0.06f, paint[3]);
                }
                // side marker
                line3(m.len / 2 - 0.12f, v, m.strip + 0.12f, m.len / 2 - 0.2f, v, m.strip + 0.12f, O1);
            }
        };
        f.depth = -100f;
        out.add(f);
    }

    private int stripeColour() {
        return m.stripe;
    }

    private float[] toSide(float[] prof2, float v) {
        int n = prof2.length / 2;
        float[] p = new float[n * 3];
        for (int i = 0; i < n; i++) { p[i * 3] = prof2[i * 2]; p[i * 3 + 1] = v; p[i * 3 + 2] = prof2[i * 2 + 1]; }
        return p;
    }

    private static int darker(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((int) (r * 0.8f) << 16) | ((int) (g * 0.8f) << 8) | (int) (b * 0.84f);
    }

    /** The height of the lower body's top at u (the belt under the cabin). */
    private float beltAt(float u) {
        float[] p = m.lower;
        float best = m.belt;
        for (int i = 0; i < p.length / 2; i++) {
            int j = (i + 1) % (p.length / 2);
            float ua = p[i * 2], ub = p[j * 2];
            if (m.lowerRole[i] == BUMP_F || m.lowerRole[i] == BUMP_R) continue;
            if ((u <= ua && u >= ub) || (u <= ub && u >= ua)) {
                if (Math.abs(ub - ua) < 1e-4f) continue;
                float t = (u - ua) / (ub - ua);
                float y = p[i * 2 + 1] + (p[j * 2 + 1] - p[i * 2 + 1]) * t;
                if (y > 0.5f) best = Math.min(best, y);
            }
        }
        return best;
    }

    /** The part of a profile polygon below y. */
    private static float[] clipBelow(float[] prof, float y) {
        return clip(prof, y, true);
    }

    private static float[] clipBand(float[] prof, float y0, float y1) {
        float[] a = clip(prof, y1, true);
        return a == null ? null : clip(a, y0, false);
    }

    /** Sutherland-Hodgman against a horizontal line (keep below if {@code below}). */
    private static float[] clip(float[] p, float y, boolean below) {
        int n = p.length / 2;
        float[] out = new float[n * 4];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ya = p[i * 2 + 1], yb = p[j * 2 + 1];
            boolean ia = below ? ya <= y : ya >= y, ib = below ? yb <= y : yb >= y;
            if (ia) { out[k++] = p[i * 2]; out[k++] = ya; }
            if (ia != ib) {
                float t = (y - ya) / (yb - ya);
                out[k++] = p[i * 2] + (p[j * 2] - p[i * 2]) * t;
                out[k++] = y;
            }
        }
        return k < 6 ? null : Arrays.copyOf(out, k);
    }

    private void arch(float au, float v) {
        float r = m.wheelR + 0.07f;
        int n = 14;
        float[] p = new float[(n + 1) * 3];
        float sill = m.lower[1] + 0.02f;
        int k = 0;
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * i / n;
            float u = au + (float) Math.cos(a) * r, y = m.wheelR + (float) Math.sin(a) * r;
            y = Math.max(y, sill);
            p[k * 3] = u; p[k * 3 + 1] = v; p[k * 3 + 2] = y;
            k++;
        }
        poly(p, k, K3);
    }

    /** The panels between a profile's edges, across the car (narrowing to the roof by tumble). */
    private void strips(List<Face> out, final float[] prof, final int[] role, final float hwBottom, final float hwTop, final boolean cabin) {
        final int n = prof.length / 2;
        float yMin = Float.MAX_VALUE, yMax = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) { yMin = Math.min(yMin, prof[i * 2 + 1]); yMax = Math.max(yMax, prof[i * 2 + 1]); }
        final float y0 = yMin, y1 = yMax;
        float cu = 0, cy = 0;
        for (int i = 0; i < n; i++) { cu += prof[i * 2]; cy += prof[i * 2 + 1]; }
        cu /= n; cy /= n;
        for (int i = 0; i < n; i++) {
            final int r = role[i];
            if (r == SKIP) continue;
            final int j = (i + 1) % n;
            final float ua = prof[i * 2], ya = prof[i * 2 + 1], ub = prof[j * 2], yb = prof[j * 2 + 1];
            // outward normal in the u-y plane
            float nu = yb - ya, ny = -(ub - ua);
            float l = (float) Math.sqrt(nu * nu + ny * ny);
            if (l < 1e-5f) continue;
            nu /= l; ny /= l;
            if ((ua + ub) / 2 - cu > 0 ? false : false) { }
            if (nu * ((ua + ub) / 2 - cu) + ny * ((ya + yb) / 2 - cy) < 0) { nu = -nu; ny = -ny; }
            if (facing(nu, 0, ny) <= 0.01f) continue;
            final float wa = halfWidth(ya, y0, y1, hwBottom, hwTop), wb = halfWidth(yb, y0, y1, hwBottom, hwTop);
            final float[] p = {ua, wa, ya, ub, wb, yb, ub, -wb, yb, ua, -wa, ya};
            final float fnu = nu, fny = ny;
            Face f = new Face() {
                @Override void draw() { stripFace(p, r, fnu, fny); }
            };
            f.depth = cabin ? centreDepth(p, 4) : nearestDepth(p, 4);
            out.add(f);
        }
    }

    private float halfWidth(float y, float y0, float y1, float hwb, float hwt) {
        if (y1 - y0 < 1e-4f) return hwb;
        return hwb + (hwt - hwb) * Math.max(0f, Math.min(1f, (y - y0) / (y1 - y0)));
    }

    /** A point on a strip: a across (0 right side .. 1 left side), b along the edge (0 .. 1). */
    private float[] at(float[] p, float a, float b) {
        float u0 = p[9] + (p[0] - p[9]) * a, v0 = p[10] + (p[1] - p[10]) * a, y0 = p[11] + (p[2] - p[11]) * a;
        float u1 = p[6] + (p[3] - p[6]) * a, v1 = p[7] + (p[4] - p[7]) * a, y1 = p[8] + (p[5] - p[8]) * a;
        return new float[]{u0 + (u1 - u0) * b, v0 + (v1 - v0) * b, y0 + (y1 - y0) * b};
    }

    private void rectOn(float[] p, float a0, float a1, float b0, float b1, int c) {
        float[] q = new float[12];
        float[][] cs = {at(p, a0, b0), at(p, a1, b0), at(p, a1, b1), at(p, a0, b1)};
        for (int i = 0; i < 4; i++) System.arraycopy(cs[i], 0, q, i * 3, 3);
        poly(q, 4, c);
    }

    private void lineOn(float[] p, float a0, float b0, float a1, float b1, int c) {
        float[] s = at(p, a0, b0), e = at(p, a1, b1);
        line3(s[0], s[1], s[2], e[0], e[1], e[2], c);
    }

    private void stripFace(float[] p, int role, float nu, float ny) {
        int base = shadeOf(paint, nu, 0, ny);
        switch (role) {
            case BUMP_F:
            case BUMP_R: {
                poly(p, 4, K1);
                lineOn(p, 0, 1, 1, 1, C4);
                if (role == BUMP_F) {
                    rectOn(p, 0.4f, 0.6f, 0.2f, 0.85f, P1);
                    lineOn(p, 0.43f, 0.55f, 0.57f, 0.55f, P2);
                    rectOn(p, 0.08f, 0.16f, 0.45f, 0.65f, Y2);
                    rectOn(p, 0.84f, 0.92f, 0.45f, 0.65f, Y2);
                } else {
                    rectOn(p, 0.12f, 0.2f, 0.0f, 0.25f, C3);
                }
                break;
            }
            case FRONT: {
                poly(p, 4, base);
                // b runs from the bumper (0) up to the bonnet (1)
                switch (m.head) {
                    case HL_ROUND:
                        for (float c0 : new float[]{0.07f, 0.79f}) {
                            rectOn(p, c0, c0 + 0.14f, 0.25f, 0.8f, C3);
                            rectOn(p, c0 + 0.02f, c0 + 0.12f, 0.32f, 0.72f, Y2);
                            lineOn(p, c0 + 0.03f, 0.68f, c0 + 0.1f, 0.68f, Y1);
                        }
                        break;
                    case HL_SLIM:
                        rectOn(p, 0.05f, 0.3f, 0.55f, 0.9f, C3);
                        rectOn(p, 0.06f, 0.29f, 0.6f, 0.85f, Y2);
                        lineOn(p, 0.07f, 0.82f, 0.27f, 0.82f, Y1);
                        rectOn(p, 0.7f, 0.95f, 0.55f, 0.9f, C3);
                        rectOn(p, 0.71f, 0.94f, 0.6f, 0.85f, Y2);
                        lineOn(p, 0.73f, 0.82f, 0.93f, 0.82f, Y1);
                        break;
                    default:
                        rectOn(p, 0.05f, 0.29f, 0.35f, 0.9f, C3);
                        rectOn(p, 0.06f, 0.28f, 0.42f, 0.84f, Y2);
                        lineOn(p, 0.07f, 0.8f, 0.26f, 0.8f, Y1);
                        rectOn(p, 0.71f, 0.95f, 0.35f, 0.9f, C3);
                        rectOn(p, 0.72f, 0.94f, 0.42f, 0.84f, Y2);
                        lineOn(p, 0.74f, 0.8f, 0.93f, 0.8f, Y1);
                        break;
                }
                rectOn(p, 0.0f, 0.05f, 0.35f, 0.8f, O1);
                rectOn(p, 0.95f, 1.0f, 0.35f, 0.8f, O1);
                // grille and badge
                if ((m.extras & X_CHROME) != 0) {
                    rectOn(p, 0.3f, 0.7f, 0.2f, 0.9f, C2);
                    rectOn(p, 0.32f, 0.68f, 0.26f, 0.84f, C4);
                    for (float a = 0.35f; a < 0.68f; a += 0.05f) lineOn(p, a, 0.3f, a, 0.8f, C2);
                    lineOn(p, 0.31f, 0.87f, 0.69f, 0.87f, C1);
                    rectOn(p, 0.46f, 0.54f, 0.48f, 0.66f, C1);
                } else {
                    rectOn(p, 0.33f, 0.67f, 0.3f, 0.8f, K2);
                    for (float a = 0.36f; a < 0.66f; a += 0.06f) lineOn(p, a, 0.35f, a, 0.75f, K1);
                    rectOn(p, 0.46f, 0.54f, 0.45f, 0.65f, C2);
                }
                break;
            }
            case REAR: {
                poly(p, 4, base);
                // b runs from the boot lid (0) down to the bumper (1)
                for (float c0 : new float[]{0.03f, 0.75f}) {
                    rectOn(p, c0, c0 + 0.22f, 0.1f, 0.65f, L3);
                    rectOn(p, c0 + 0.02f, c0 + 0.2f, 0.15f, 0.6f, L2);
                    lineOn(p, c0 + 0.03f, 0.17f, c0 + 0.19f, 0.17f, L1);
                }
                rectOn(p, 0.18f, 0.24f, 0.4f, 0.55f, O1);
                rectOn(p, 0.76f, 0.82f, 0.4f, 0.55f, O1);
                rectOn(p, 0.38f, 0.62f, 0.45f, 0.95f, darker(base));
                rectOn(p, 0.4f, 0.6f, 0.5f, 0.9f, P1);
                lineOn(p, 0.43f, 0.7f, 0.57f, 0.7f, P2);
                if ((m.extras & X_SPARE) != 0) {
                    rectOn(p, 0.3f, 0.7f, 0.0f, 0.75f, K2);
                    rectOn(p, 0.36f, 0.64f, 0.12f, 0.6f, K1);
                    rectOn(p, 0.44f, 0.56f, 0.28f, 0.45f, C3);
                }
                break;
            }
            case TOP: {
                poly(p, 4, base);
                lineOn(p, 0, 0, 1, 0, paint[0]);
                if ((m.extras & X_BED) != 0 && Math.abs(p[2] - m.lower[9]) < 0.01f) {
                    poly(p, 4, K1);                         // the bed floor
                    for (float a = 0.15f; a < 0.9f; a += 0.17f) lineOn(p, a, 0.05f, a, 0.95f, C4);
                }
                break;
            }
            case GLASS_F:
            case GLASS_R: {
                poly(p, 4, darker(base));
                // glass inside its frame: a dark band at the top, a sky band and a streak
                float[] g = inset(p, 0.06f, 0.08f);
                poly(g, 4, G3);
                rectOn(g, 0.0f, 1.0f, 0.78f, 1.0f, G4);
                float[] b0 = at(g, 0.25f, 0.08f), b1 = at(g, 0.45f, 0.08f), b2 = at(g, 0.65f, 0.75f), b3 = at(g, 0.45f, 0.75f);
                poly(new float[]{b0[0], b0[1], b0[2], b1[0], b1[1], b1[2], b2[0], b2[1], b2[2], b3[0], b3[1], b3[2]}, 4, G2);
                lineOn(g, 0.5f, 0.1f, 0.7f, 0.72f, G1);
                lineOn(g, 0.0f, 0.0f, 1.0f, 0.0f, K2);
                if (role == GLASS_F) {
                    lineOn(g, 0.22f, 0.06f, 0.44f, 0.12f, K1);
                    lineOn(g, 0.56f, 0.06f, 0.78f, 0.12f, K1);
                } else {
                    lineOn(g, 0.4f, 0.06f, 0.6f, 0.2f, K1);
                    rectOn(g, 0.35f, 0.65f, 0.92f, 1.0f, L1);
                }
                break;
            }
            case PANEL_R: {
                poly(p, 4, base);
                if ((m.extras & X_BED) != 0) {
                    float[] g = inset(p, 0.18f, 0.2f);
                    poly(g, 4, G3);
                    lineOn(g, 0.3f, 0.1f, 0.5f, 0.9f, G2);
                } else {
                    // van back doors: two windows, a split, tail lights low down
                    float[] g = inset(p, 0.08f, 0.08f);
                    rectOn(g, 0.04f, 0.47f, 0.05f, 0.4f, G3);
                    rectOn(g, 0.53f, 0.96f, 0.05f, 0.4f, G3);
                    lineOn(g, 0.1f, 0.35f, 0.3f, 0.08f, G2);
                    lineOn(g, 0.6f, 0.35f, 0.8f, 0.08f, G2);
                    lineOn(p, 0.5f, 0.0f, 0.5f, 1.0f, paint[4]);
                    rectOn(p, 0.04f, 0.16f, 0.82f, 0.97f, L2);
                    rectOn(p, 0.84f, 0.96f, 0.82f, 0.97f, L2);
                    rectOn(p, 0.4f, 0.6f, 0.85f, 0.97f, P1);
                }
                break;
            }
            case ROOF: {
                if ((m.extras & X_SOFTTOP) != 0) {
                    poly(p, 4, ny > 0.6f ? K1 : K2);
                    for (float b = 0.25f; b < 1f; b += 0.25f) lineOn(p, 0.04f, b, 0.96f, b, K2);
                    break;
                }
                poly(p, 4, base);
                lineOn(p, 0, 0.02f, 1, 0.02f, paint[0]);
                break;
            }
            default:
                poly(p, 4, base);
        }
    }

    /** A strip shrunk toward its middle by fractions across (a) and along (b). */
    private float[] inset(float[] p, float da, float db) {
        float[] q = new float[12];
        float[][] cs = {at(p, 1 - da, db), at(p, 1 - da, 1 - db), at(p, da, 1 - db), at(p, da, db)};
        for (int i = 0; i < 4; i++) System.arraycopy(cs[i], 0, q, i * 3, 3);
        return q;
    }

    // ---------------------------------------------------------------- cabin

    private void cabinFaces(List<Face> out, float hw, final float near) {
        final float hwRoof = hw - m.tumble;
        if (m.cabinColour != 0) {
            final int[] body = paint, top = ramp(m.cabinColour & 0xFFFFFF);
            // swap the paint while the cabin's faces draw
            out.add(new Face() {
                { depth = -1e9f; }
                @Override void draw() { paint = top; }
            });
            cabinRestore = body;
        }
        int before = out.size();
        strips(out, m.cabin, m.cabinRole, hw, hwRoof, true);
        // the side panel goes just behind the cab's other visible panels (so they draw over it)
        // but at the cab's own depth among everything else (the truck bed)
        float sideDepth = Float.MAX_VALUE;
        for (int i = before; i < out.size(); i++) sideDepth = Math.min(sideDepth, out.get(i).depth);
        if (Math.abs(facing(0, near, 0)) < 0.02f) return;
        // the visible side of the cabin: pillars in paint (or black), the side glass
        final int n = m.cabin.length / 2;
        float yMin = Float.MAX_VALUE, yMax = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) { yMin = Math.min(yMin, m.cabin[i * 2 + 1]); yMax = Math.max(yMax, m.cabin[i * 2 + 1]); }
        final float y0 = yMin, y1 = yMax;
        final float[] p = new float[n * 3];
        for (int i = 0; i < n; i++) {
            float y = m.cabin[i * 2 + 1];
            p[i * 3] = m.cabin[i * 2]; p[i * 3 + 1] = near * halfWidth(y, y0, y1, hw, hwRoof); p[i * 3 + 2] = y;
        }
        Face f = new Face() {
            @Override void draw() {
                // the side leans in a little (tumblehome): shade it as a slightly upward face
                int base = shadeOf(paint, 0, near * 0.95f, 0.3f);
                if ((m.extras & X_BLACK_PILLARS) != 0) base = K1;
                poly(p, n, base);
                sideGlass(near, hw, hwRoof, y0, y1);
            }
        };
        f.depth = sideDepth == Float.MAX_VALUE ? -50f : sideDepth - 0.01f;
        out.add(f);
    }

    /** The side windows: the cabin's side shrunk by the pillars, split at the B (and C) pillars. */
    private void sideGlass(float near, float hw, float hwRoof, float y0, float y1) {
        float[] in = offset(m.cabin, 0.075f);
        if (in == null) return;
        float bottom = m.belt + 0.035f;
        float[] cuts = new float[m.pillars.length + 2];
        cuts[0] = 99f;
        for (int i = 0; i < m.pillars.length; i++) cuts[i + 1] = m.pillars[i];
        cuts[cuts.length - 1] = -99f;
        int panes = cuts.length - 1;
        for (int k = 0; k < panes; k++) {
            if ((m.extras & X_NO_REAR_SIDE_GLASS) != 0 && k == panes - 1 && panes > 1) continue;
            float a = cuts[k] - (k == 0 ? 0 : m.pillarW / 2), b = cuts[k + 1] + (k + 1 == panes ? 0 : m.pillarW / 2);
            float[] pane = clipU(clipU(in, a, true), b, false);
            if (pane == null) continue;
            pane = clip(pane, bottom, false);
            if (pane == null) continue;
            int n = pane.length / 2;
            float[] q = new float[n * 3];
            float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                float y = pane[i * 2 + 1];
                q[i * 3] = pane[i * 2]; q[i * 3 + 1] = near * (halfWidth(y, y0, y1, hw, hwRoof) + 0.004f); q[i * 3 + 2] = y;
                minU = Math.min(minU, pane[i * 2]); maxU = Math.max(maxU, pane[i * 2]); maxY = Math.max(maxY, y);
            }
            poly(q, n, G3);
            // sky band and a streak across the pane
            float h = maxY - bottom, w = maxU - minU;
            float vv = near * (halfWidth(bottom + h * 0.5f, y0, y1, hw, hwRoof) + 0.006f);
            float[] band = {
                    maxU - w * 0.05f, vv, bottom + h * 0.55f,
                    maxU - w * 0.05f, vv, bottom + h * 0.8f,
                    minU + w * 0.05f, vv, bottom + h * 0.45f,
                    minU + w * 0.05f, vv, bottom + h * 0.2f,
            };
            float[] bandC = clipPoly3(band, q);
            if (bandC != null) poly(bandC, bandC.length / 3, G2);
            line3(minU + w * 0.25f, vv, bottom + h * 0.1f, minU + w * 0.45f, vv, bottom + h * 0.85f, G1);
            line3(minU, near * (hw + 0.004f), bottom, maxU, near * (hw + 0.004f), bottom, K2);
        }
    }

    /** Keeps the part of polygon a (car-space triples on one side) inside pane q (crudely: by height). */
    private float[] clipPoly3(float[] a, float[] q) {
        float maxY = -Float.MAX_VALUE, minY = Float.MAX_VALUE;
        for (int i = 0; i < q.length / 3; i++) { maxY = Math.max(maxY, q[i * 3 + 2]); minY = Math.min(minY, q[i * 3 + 2]); }
        int n = a.length / 3;
        float[] p2 = new float[n * 2];
        for (int i = 0; i < n; i++) { p2[i * 2] = a[i * 3]; p2[i * 2 + 1] = a[i * 3 + 2]; }
        float[] c = clip(p2, maxY - 0.02f, true);
        if (c == null) return null;
        c = clip(c, minY + 0.01f, false);
        if (c == null) return null;
        int k = c.length / 2;
        float[] out = new float[k * 3];
        for (int i = 0; i < k; i++) { out[i * 3] = c[i * 2]; out[i * 3 + 1] = a[1]; out[i * 3 + 2] = c[i * 2 + 1]; }
        return out;
    }

    /** Keeps u <= limit (keepBelow) or u >= limit. */
    private static float[] clipU(float[] p, float limit, boolean keepBelow) {
        if (p == null) return null;
        int n = p.length / 2;
        float[] out = new float[n * 4];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ua = p[i * 2], ub = p[j * 2];
            boolean ia = keepBelow ? ua <= limit : ua >= limit, ib = keepBelow ? ub <= limit : ub >= limit;
            if (ia) { out[k++] = ua; out[k++] = p[i * 2 + 1]; }
            if (ia != ib) {
                float t = (limit - ua) / (ub - ua);
                out[k++] = limit;
                out[k++] = p[i * 2 + 1] + (p[j * 2 + 1] - p[i * 2 + 1]) * t;
            }
        }
        return k < 6 ? null : Arrays.copyOf(out, k);
    }

    /** A convex profile shrunk by d (each edge moved inward, corners re-intersected). */
    private static float[] offset(float[] p, float d) {
        int n = p.length / 2;
        float cu = 0, cy = 0;
        for (int i = 0; i < n; i++) { cu += p[i * 2]; cy += p[i * 2 + 1]; }
        cu /= n; cy /= n;
        float[] lines = new float[n * 3];     // a u + b y = c, normal pointing inward
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float du = p[j * 2] - p[i * 2], dy = p[j * 2 + 1] - p[i * 2 + 1];
            float a = -dy, b = du, l = (float) Math.sqrt(a * a + b * b);
            a /= l; b /= l;
            float c = a * p[i * 2] + b * p[i * 2 + 1];
            if (a * cu + b * cy < c) { a = -a; b = -b; c = -c; }
            lines[i * 3] = a; lines[i * 3 + 1] = b; lines[i * 3 + 2] = c + d;
        }
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            int h = (i + n - 1) % n;
            float a1 = lines[h * 3], b1 = lines[h * 3 + 1], c1 = lines[h * 3 + 2];
            float a2 = lines[i * 3], b2 = lines[i * 3 + 1], c2 = lines[i * 3 + 2];
            float det = a1 * b2 - a2 * b1;
            if (Math.abs(det) < 1e-6f) return null;
            out[i * 2] = (c1 * b2 - c2 * b1) / det;
            out[i * 2 + 1] = (a1 * c2 - a2 * c1) / det;
        }
        return out;
    }

    // ---------------------------------------------------------------- wheels

    private void wheels(float side) {
        for (float au : new float[]{m.axleF, m.axleR}) wheel(au, side);
    }

    /** The nearest corner of a polygon (body panels sort by it). */
    private float nearestDepth(float[] p, int n) {
        float d = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) d = Math.max(d, project(p[i * 3], p[i * 3 + 1], p[i * 3 + 2])[2]);
        return d;
    }

    private void wheel(float au, float side) {
        float r = m.wheelR, v = side * (m.wid / 2 - 0.07f), tw = 0.1f;
        int n = 18;
        // the tyre: the hull of its two side circles
        float[] xs = new float[n * 2], ys = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            float u = au + (float) Math.cos(a) * r, y = r + (float) Math.sin(a) * r;
            project(u, v + tw, y); xs[i] = sp[0]; ys[i] = sp[1];
            project(u, v - tw, y); xs[n + i] = sp[0]; ys[n + i] = sp[1];
        }
        float[] hull = hull(xs, ys, n * 2);
        fill(Arrays.copyOfRange(hull, 0, hull.length / 2), Arrays.copyOfRange(hull, hull.length / 2, hull.length), hull.length / 2, K2);
        float face = facing(0, side, 0);
        if (face <= 0.15f) return;
        float vo = v + side * tw;
        disc(au, vo, r * 0.86f, K1);
        switch (m.wheel) {
            case WH_STEEL:
                disc(au, vo, r * 0.56f, C4);
                disc(au, vo, r * 0.48f, K1);
                disc(au, vo, r * 0.2f, C3);
                break;
            case WH_CAP:
                disc(au, vo, r * 0.6f, C3);
                disc(au, vo, r * 0.54f, C2);
                disc(au, vo, r * 0.2f, C3);
                break;
            default:
                disc(au, vo, r * 0.58f, m.rimDark);
                disc(au, vo, r * 0.5f, m.rimLight);
                disc(au, vo, r * 0.18f, C4);
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI / 2 + i * Math.PI * 2 / 5;
                    line3(au + (float) Math.cos(a) * r * 0.22f, vo, r + (float) Math.sin(a) * r * 0.22f,
                            au + (float) Math.cos(a) * r * 0.46f, vo, r + (float) Math.sin(a) * r * 0.46f, C4);
                }
                break;
        }
        project(au - r * 0.3f, vo, r + r * 0.3f);
        plot((int) sp[0], (int) sp[1], C1);
    }

    private void disc(float au, float v, float r, int c) {
        int n = 16;
        float[] p = new float[n * 3];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            p[i * 3] = au + (float) Math.cos(a) * r; p[i * 3 + 1] = v; p[i * 3 + 2] = m.wheelR + (float) Math.sin(a) * r;
        }
        poly(p, n, c);
    }

    /** Convex hull (monotone chain); returns xs then ys. */
    private static float[] hull(float[] xs, float[] ys, int n) {
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> xs[a] != xs[b] ? Float.compare(xs[a], xs[b]) : Float.compare(ys[a], ys[b]));
        int[] h = new int[2 * n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(xs, ys, h[k - 2], h[k - 1], idx[i]) <= 0) k--;
            h[k++] = idx[i];
        }
        for (int i = n - 2, t = k + 1; i >= 0; i--) {
            while (k >= t && cross(xs, ys, h[k - 2], h[k - 1], idx[i]) <= 0) k--;
            h[k++] = idx[i];
        }
        k--;
        float[] out = new float[k * 2];
        for (int i = 0; i < k; i++) { out[i] = xs[h[i]]; out[k + i] = ys[h[i]]; }
        return out;
    }

    private static float cross(float[] xs, float[] ys, int o, int a, int b) {
        return (xs[a] - xs[o]) * (ys[b] - ys[o]) - (ys[a] - ys[o]) * (xs[b] - xs[o]);
    }

    // ---------------------------------------------------------------- extras

    /** A small box (u0..u1, v0..v1, y0..y1): its visible faces join the list. */
    private void box(List<Face> out, float u0, float u1, float v0, float v1, float y0, float y1, final int colour, final boolean painted) {
        float[][] faces = {
                {u1, v0, y0, u1, v1, y0, u1, v1, y1, u1, v0, y1, 1, 0, 0},
                {u0, v1, y0, u0, v0, y0, u0, v0, y1, u0, v1, y1, -1, 0, 0},
                {u0, v1, y0, u1, v1, y0, u1, v1, y1, u0, v1, y1, 0, 1, 0},
                {u1, v0, y0, u0, v0, y0, u0, v0, y1, u1, v0, y1, 0, -1, 0},
                {u0, v0, y1, u0, v1, y1, u1, v1, y1, u1, v0, y1, 0, 0, 1},
        };
        for (float[] fc : faces) {
            final float nu = fc[12], nv = fc[13], ny = fc[14];
            if (facing(nu, nv, ny) <= 0.01f) continue;
            final float[] p = Arrays.copyOf(fc, 12);
            Face f = new Face() {
                @Override void draw() {
                    int c = painted ? shadeOf(paint, nu, nv, ny) : (ny > 0.5f ? lighter(colour) : (light(nu, nv, ny) > 0.2f ? colour : darker(colour)));
                    poly(p, 4, c);
                }
            };
            f.depth = centreDepth(p, 4) + 0.001f;
            out.add(f);
        }
    }

    private static int lighter(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | (Math.min(255, r + 40) << 16) | (Math.min(255, g + 40) << 8) | Math.min(255, b + 40);
    }

    private void extras(List<Face> out, float hw) {
        float roof = 0, ru0 = 0, ru1 = 0;
        for (int i = 0; i < m.cabinRole.length; i++)
            if (m.cabinRole[i] == ROOF) {
                roof = m.cabin[i * 2 + 1];
                ru0 = m.cabin[((i + 1) % (m.cabin.length / 2)) * 2];
                ru1 = m.cabin[i * 2];
            }
        float rw = hw - m.tumble;
        if ((m.extras & X_RACK) != 0) {
            box(out, ru0 + 0.1f, ru1 - 0.1f, rw - 0.12f, rw - 0.06f, roof, roof + 0.07f, K1, false);
            box(out, ru0 + 0.1f, ru1 - 0.1f, -rw + 0.06f, -rw + 0.12f, roof, roof + 0.07f, K1, false);
        }
        if ((m.extras & X_TAXI) != 0) {
            float mu = (ru0 + ru1) / 2;
            box(out, mu - 0.12f, mu + 0.12f, -0.25f, 0.25f, roof, roof + 0.17f, 0xFFF0E6C8, false);
            box(out, mu - 0.13f, mu + 0.13f, -0.26f, 0.26f, roof, roof + 0.04f, K1, false);
        }
        if ((m.extras & X_SCOOP) != 0) {
            float u = m.len / 2 - 0.75f, y = beltAt(u) - 0.02f;
            box(out, u - 0.22f, u + 0.18f, -0.2f, 0.2f, y, y + 0.07f, 0, true);
            box(out, u + 0.12f, u + 0.18f, -0.17f, 0.17f, y + 0.01f, y + 0.06f, K2, false);
        }
        if ((m.extras & X_HIGHTOP) != 0) {
            box(out, ru0 + 0.05f, ru1 - 0.35f, -rw + 0.02f, rw - 0.02f, roof, roof + 0.42f, 0, true);
            box(out, ru1 - 0.35f, ru1 - 0.1f, -rw + 0.06f, rw - 0.06f, roof, roof + 0.25f, 0, true);
        }
        if ((m.extras & X_LIGHTBAR) != 0) {
            float mu = ru1 - 0.25f;
            box(out, mu - 0.11f, mu + 0.11f, 0.02f, rw - 0.1f, roof, roof + 0.12f, m.barL, false);
            box(out, mu - 0.11f, mu + 0.11f, -rw + 0.1f, -0.02f, roof, roof + 0.12f, m.barR, false);
            box(out, mu - 0.12f, mu + 0.12f, -rw + 0.08f, rw - 0.08f, roof, roof + 0.03f, K1, false);
        }
        if ((m.extras & X_SPOILER) != 0) {
            float u = -m.len / 2 + 0.12f, y = m.lower[9];
            box(out, u - 0.08f, u + 0.08f, -hw + 0.06f, hw - 0.06f, y + 0.12f, y + 0.16f, 0, true);
            box(out, u - 0.03f, u + 0.02f, hw - 0.3f, hw - 0.24f, y, y + 0.12f, K1, false);
            box(out, u - 0.03f, u + 0.02f, -hw + 0.24f, -hw + 0.3f, y, y + 0.12f, K1, false);
        }
    }

    /** A quad given its corners at (a, b) = (1,0), (1,1), (0,1), (0,0): the layout at() reads. */
    private static float[] quadP(float[] a10, float[] a11, float[] a01, float[] a00) {
        float[] p = new float[12];
        System.arraycopy(a10, 0, p, 0, 3); System.arraycopy(a11, 0, p, 3, 3);
        System.arraycopy(a01, 0, p, 6, 3); System.arraycopy(a00, 0, p, 9, 3);
        return p;
    }

    /** The cargo body behind the cab: five faces with their details. */
    private void cargo(List<Face> out, float hw) {
        final float u0 = -m.len / 2 + 0.02f, u1 = m.lower[8] - 0.04f, y0 = m.lower[9], y1 = m.boxTop, w = hw + 0.04f;
        final int[] body = ramp(m.boxColour & 0xFFFFFF);
        for (final float side : new float[]{-1f, 1f}) {
            if (facing(0, side, 0) <= 0.01f) continue;
            final float v = side * w;
            // a: along the body from the back (0) to the front (1); b: up from the bottom (0)
            final float[] p = quadP(new float[]{u1, v, y0}, new float[]{u1, v, y1}, new float[]{u0, v, y1}, new float[]{u0, v, y0});
            Face f = new Face() {
                @Override void draw() {
                    int c = shadeOf(body, 0, side, 0);
                    poly(p, 4, c);
                    lineOn(p, 0, 0.98f, 1, 0.98f, body[0]);
                    lineOn(p, 0, 0.04f, 1, 0.04f, body[3]);
                    if ((m.extras & X_MARKERS) != 0) {
                        // a decorated truck: painted panels, chrome trim and rows of marker lights
                        rectOn(p, 0.06f, 0.94f, 0.22f, 0.82f, m.stripe);
                        rectOn(p, 0.1f, 0.9f, 0.3f, 0.74f, 0xFF2A3A8A);
                        lineOn(p, 0.12f, 0.6f, 0.4f, 0.36f, 0xFF4A8AE0);
                        lineOn(p, 0.45f, 0.7f, 0.88f, 0.34f, 0xFF4A8AE0);
                        for (float a = 0.03f; a < 1f; a += 0.07f) {
                            rectOn(p, a, a + 0.025f, 0.06f, 0.11f, O1);
                            rectOn(p, a, a + 0.025f, 0.9f, 0.95f, (int) (a * 100) % 2 == 0 ? 0xFFFFE070 : L1);
                        }
                    }
                    if (m.boxStyle == BOX_SHUTTERS) {
                        // roller-shutter lockers in two rows, a chrome rubbing rail
                        for (float a = 0.04f; a < 0.96f; a += 0.24f) {
                            rectOn(p, a, a + 0.22f, 0.12f, 0.88f, darker(c));
                            for (float b = 0.16f; b < 0.86f; b += 0.08f) lineOn(p, a + 0.01f, b, a + 0.21f, b, body[3]);
                            lineOn(p, a + 0.02f, 0.2f, a + 0.2f, 0.2f, C2);
                        }
                        lineOn(p, 0, 0.1f, 1, 0.1f, C2);
                    } else if (m.boxStyle == BOX_PACKER) {
                        // the compactor's tail and a stripe
                        rectOn(p, 0.0f, 0.22f, 0.04f, 0.98f, darker(c));
                        lineOn(p, 0.22f, 0.04f, 0.22f, 0.98f, body[4]);
                        rectOn(p, 0.25f, 1.0f, 0.4f, 0.48f, m.stripe);
                        for (float a = 0.3f; a < 1f; a += 0.14f) lineOn(p, a, 0.08f, a, 0.94f, body[2]);
                    } else {
                        rectOn(p, 0.05f, 0.95f, 0.45f, 0.55f, m.stripe);
                        for (float a = 0.12f; a < 1f; a += 0.17f) lineOn(p, a, 0.06f, a, 0.94f, body[2]);
                    }
                }
            };
            f.depth = centreDepth(p, 4);
            out.add(f);
        }
        // the back: doors (or the compactor's hopper)
        if (facing(-1, 0, 0) > 0.01f) {
            final float[] p = quadP(new float[]{u0, w, y0}, new float[]{u0, w, y1}, new float[]{u0, -w, y1}, new float[]{u0, -w, y0});
            Face f = new Face() {
                @Override void draw() {
                    int c = shadeOf(body, -1, 0, 0);
                    poly(p, 4, c);
                    if (m.boxStyle == BOX_PACKER) {
                        rectOn(p, 0.06f, 0.94f, 0.08f, 0.55f, K1);
                        rectOn(p, 0.12f, 0.88f, 0.12f, 0.45f, K2);
                        lineOn(p, 0.06f, 0.55f, 0.94f, 0.55f, C3);
                    } else {
                        lineOn(p, 0.5f, 0.04f, 0.5f, 0.96f, body[4]);
                        lineOn(p, 0.04f, 0.04f, 0.04f, 0.96f, body[3]);
                        lineOn(p, 0.96f, 0.04f, 0.96f, 0.96f, body[3]);
                        rectOn(p, 0.44f, 0.48f, 0.4f, 0.55f, C3);
                        rectOn(p, 0.52f, 0.56f, 0.4f, 0.55f, C3);
                    }
                    rectOn(p, 0.04f, 0.16f, 0.04f, 0.14f, L2);
                    rectOn(p, 0.84f, 0.96f, 0.04f, 0.14f, L2);
                }
            };
            f.depth = centreDepth(p, 4);
            out.add(f);
        }
        // the front (seen over the cab when the body is taller)
        if (facing(1, 0, 0) > 0.01f) {
            final float[] p = quadP(new float[]{u1, -w, y0}, new float[]{u1, -w, y1}, new float[]{u1, w, y1}, new float[]{u1, w, y0});
            Face f = new Face() {
                @Override void draw() { poly(p, 4, shadeOf(body, 1, 0, 0)); }
            };
            f.depth = centreDepth(p, 4);
            out.add(f);
        }
        // the roof
        final float[] t = quadP(new float[]{u1, w, y1}, new float[]{u0, w, y1}, new float[]{u0, -w, y1}, new float[]{u1, -w, y1});
        Face top = new Face() {
            @Override void draw() {
                poly(t, 4, body[0]);
                lineOn(t, 0.02f, 0, 0.02f, 1, body[2]);
                lineOn(t, 0.98f, 0, 0.98f, 1, body[2]);
            }
        };
        top.depth = centreDepth(t, 4);
        out.add(top);
        if ((m.extras & X_LADDER) != 0) {
            float lu0 = u0 + 0.2f, lu1 = u1 - 0.1f;
            box(out, lu0, lu1, 0.2f, 0.28f, y1, y1 + 0.08f, C2, false);
            box(out, lu0, lu1, -0.28f, -0.2f, y1, y1 + 0.08f, C2, false);
            for (float u = lu0 + 0.1f; u < lu1; u += 0.3f) box(out, u - 0.02f, u + 0.02f, -0.2f, 0.2f, y1 + 0.05f, y1 + 0.08f, C3, false);
        }
    }

    /** A tow truck's boom over the bed. */
    private void tow(List<Face> out, float hw) {
        float u = -m.len / 2 + 0.45f, y0 = m.lower[9];
        box(out, u - 0.08f, u + 0.08f, -0.1f, 0.1f, y0, y0 + 1.05f, 0xFF3C3C44, false);
        box(out, -m.len / 2 - 0.25f, u + 0.05f, -0.07f, 0.07f, y0 + 0.92f, y0 + 1.05f, 0xFF3C3C44, false);
        box(out, -m.len / 2 - 0.25f, -m.len / 2 - 0.2f, -0.02f, 0.02f, y0 + 0.35f, y0 + 0.92f, K2, false);
        box(out, -m.len / 2 - 0.3f, -m.len / 2 - 0.15f, -0.06f, 0.06f, y0 + 0.3f, y0 + 0.36f, 0xFF3C3C44, false);
    }

    /** A loader crane behind the cab, its boom folded along the bed. */
    private void crane(List<Face> out, float hw) {
        float u = m.lower[8] - 0.25f, y0 = m.lower[9];
        int arm = 0xFFE8862A;
        box(out, u - 0.2f, u + 0.2f, -0.28f, 0.28f, y0, y0 + 0.35f, 0xFF3C3C44, false);
        box(out, u - 0.13f, u + 0.13f, -0.13f, 0.13f, y0 + 0.35f, y0 + 1.15f, arm, false);
        box(out, -m.len / 2 + 0.3f, u + 0.1f, -0.11f, 0.11f, y0 + 1.0f, y0 + 1.22f, arm, false);
        box(out, -m.len / 2 + 0.3f, -m.len / 2 + 0.36f, -0.02f, 0.02f, y0 + 0.6f, y0 + 1.0f, K2, false);
        box(out, -m.len / 2 + 0.26f, -m.len / 2 + 0.4f, -0.06f, 0.06f, y0 + 0.52f, y0 + 0.6f, arm, false);
        // outriggers
        box(out, u - 0.06f, u + 0.06f, hw - 0.02f, hw + 0.12f, 0.35f, 0.5f, 0xFF3C3C44, false);
        box(out, u - 0.06f, u + 0.06f, -hw - 0.12f, -hw + 0.02f, 0.35f, 0.5f, 0xFF3C3C44, false);
    }

    /** A yaki-imo stall on a kei truck's bed: a little wooden hut, a red lantern and a chimney. */
    private void stall(List<Face> out, float hw) {
        float u0 = -m.len / 2 + 0.08f, u1 = m.lower[8] - 0.1f, y0 = m.lower[9];
        int wood = 0xFF8A5A34;
        box(out, u0 + 0.3f, u1, -hw + 0.06f, hw - 0.06f, y0, y0 + 0.55f, wood, false);
        box(out, u0, u1 + 0.05f, -hw, hw, y0 + 0.95f, y0 + 1.02f, 0xFF5A3A22, false);
        box(out, u0 + 0.02f, u0 + 0.08f, hw - 0.08f, hw - 0.02f, y0, y0 + 0.95f, 0xFF5A3A22, false);
        box(out, u0 + 0.02f, u0 + 0.08f, -hw + 0.02f, -hw + 0.08f, y0, y0 + 0.95f, 0xFF5A3A22, false);
        box(out, u0 + 0.05f, u0 + 0.25f, -0.15f, 0.15f, y0, y0 + 0.4f, K1, false);
        box(out, u0 + 0.12f, u0 + 0.18f, -0.03f, 0.03f, y0 + 0.4f, y0 + 1.3f, C4, false);
        box(out, u0 + 0.4f, u0 + 0.6f, hw - 0.12f, hw + 0.02f, y0 + 0.62f, y0 + 0.88f, L2, false);
    }

    /** The kei truck's open bed: walls round the floor. */
    private void bed(List<Face> out, float hw) {
        // walls up to the cab's window line, so nothing pokes out past the cab
        float u0 = -m.len / 2 + 0.03f, u1 = m.lower[8] - 0.02f, y0 = m.lower[9], y1 = m.bedTop > 0 ? m.bedTop : m.belt;
        boolean painted = m.bedColour == 0;
        int c = m.bedColour;
        box(out, u0, u1, hw - 0.05f, hw, y0, y1, c, painted);
        box(out, u0, u1, -hw, -hw + 0.05f, y0, y1, c, painted);
        box(out, u0, u0 + 0.05f, -hw, hw, y0, y1, c, painted);
        box(out, u1 - 0.05f, u1, -hw, hw, y0, y1, c, painted);
    }

    private void mirrors(float hw) {
        float u = m.cabin[0] - 0.06f, y = m.belt + 0.06f;
        for (float side : new float[]{-1f, 1f}) {
            if (facing(0, side, 0) < -0.3f) continue;
            project(u, side * (hw + 0.1f), y);
            int x = Math.round(sp[0] - 0.5f), yy = Math.round(sp[1] - 0.5f);
            int base = shadeOf(paint, 0, side, 0);
            for (int dy = -2; dy <= 0; dy++)
                for (int dx = -1; dx <= 1; dx++) plot(x + dx, yy + dy, dy == -2 ? paint[0] : base);
        }
    }

    // ---------------------------------------------------------------- outline

    private void outline() {
        int[] src = px.clone();
        for (int y = 0; y < FH; y++)
            for (int x = 0; x < FW; x++) {
                if (src[y * FW + x] != 0) continue;
                int c = 0;
                if (x > 0 && src[y * FW + x - 1] != 0) c = src[y * FW + x - 1];
                else if (x < FW - 1 && src[y * FW + x + 1] != 0) c = src[y * FW + x + 1];
                else if (y > 0 && src[(y - 1) * FW + x] != 0) c = src[(y - 1) * FW + x];
                else if (y < FH - 1 && src[(y + 1) * FW + x] != 0) c = src[(y + 1) * FW + x];
                if (c != 0) {
                    int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                    px[y * FW + x] = 0xFF000000 | ((int) (r * 0.35f) << 16) | ((int) (g * 0.35f) << 8) | (int) (b * 0.4f);
                }
            }
    }
}
