package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;

import java.util.ArrayList;
import java.util.List;

/**
 * 25 procedural Japanese vehicle models x 4 paint schemes = 100 styles (kei cars, minivans,
 * taxis, police, buses, fire trucks...). Model space: car faces +Z,
 * wheels on y = 0, centred on the origin.
 */
public final class Cars {
    private Cars() {}

    public static final int MODELS = 25, PAINTS = 4, STYLES = MODELS * PAINTS;
    public static final float PPM = 20f;

    // body types
    private static final int HATCH = 0, SEDAN = 1, COUPE = 2, SPORTS = 3, WAGON = 4, SUV = 5, PICKUP = 6, VAN = 7,
            BOXTRUCK = 8, CONVERTIBLE = 9, JEEP = 10, CLASSIC = 11, BUG = 12, BUS = 13;

    /** Model table. */
    public static final class Spec {
        public final String name;
        final int type;
        public final float L, W;
        final float belt, roof, hood, trunk, ws, rw, wheelR, power;
        final int extra;   // 1 taxi, 2 police, 3 spoiler, 4 stripes, 5 lifted, 6 fins, 7 roof rack, 8 fire, 9 ambulance
        final int[] paints;

        Spec(String name, int type, float L, float W, float belt, float roof, float hood, float trunk, float ws, float rw,
             float wheelR, float power, int extra, int... paints) {
            this.name = name; this.type = type; this.L = L; this.W = W; this.belt = belt; this.roof = roof;
            this.hood = hood; this.trunk = trunk; this.ws = ws; this.rw = rw; this.wheelR = wheelR; this.power = power;
            this.extra = extra; this.paints = paints;
        }

        /** Overall height in metres (for sprite frames). */
        public float height() {
            if (type == BOXTRUCK) return extra == 8 ? 3.5f : 3.4f;
            if (type == BUS) return 3.2f;
            return roof + 0.45f;
        }
    }

    private static final int WHITE = 0xF2F2EE, BLACK = 0x1E2024, SILVER = 0xB8BCC2, RED = 0xC8282C, BLUE = 0x2A5AA8,
            TEAL = 0x2E9C98, PINK = 0xF07AA8, YELLOW = 0xF2C530, MINT = 0x9ED8BC, NAVY = 0x1E2E58, MAROON = 0x7A1E2A,
            ORANGE = 0xE8742A, BEIGE = 0xD8C8A6, GREEN = 0x2E6A3E, PURPLE = 0x6A3A8A, GOLD = 0xC8A050, GREY = 0x6A6E74,
            SKY = 0x8EC4E8, LIME = 0xA8D040, CREAM = 0xF0E6CC, BROWN = 0x6A4A32, COPPER = 0xB06A3A;

    private static final int PEARL = 0xF4F2EA, DARKBLUE = 0x1A2440, KEIMINT = 0xB8E0C8, KEIPINK = 0xF4C8D0, KEICREAM = 0xF0E4C4,
            WINE = 0x5A1E2A, GUNMETAL = 0x4A4E54, CHAMPAGNE = 0xD8CCB0, BUSGREEN = 0x3A8A5A, FIRERED = 0xD02020;

    public static final Spec[] MODELS_TABLE = {
            new Spec("Kei hatch", HATCH, 3.4f, 1.48f, 0.98f, 1.62f, 0.55f, 0.1f, 0.65f, 0.15f, 0.28f, 7f, 0, KEIMINT, PEARL, KEIPINK, KEICREAM),
            new Spec("Kei tall wagon", VAN, 3.4f, 1.48f, 1.0f, 1.8f, 0.45f, 0.08f, 0.6f, 0.08f, 0.28f, 10f, 0, PEARL, SILVER, SKY, BLACK),
            new Spec("Kei truck", PICKUP, 3.4f, 1.48f, 0.98f, 1.75f, 0.12f, 1.95f, 0.15f, 0.05f, 0.28f, 10f, 0, WHITE, WHITE, SILVER, SKY),
            new Spec("Kei van", VAN, 3.4f, 1.48f, 1.05f, 1.88f, 0.2f, 0.06f, 0.25f, 0.05f, 0.28f, 11f, 0, WHITE, SILVER, KEICREAM, BLUE),
            new Spec("Compact hatch", HATCH, 3.95f, 1.69f, 0.95f, 1.5f, 0.85f, 0.15f, 0.8f, 0.25f, 0.31f, 5f, 0, RED, PEARL, SILVER, BLUE),
            new Spec("Nineties sedan", SEDAN, 4.5f, 1.7f, 0.95f, 1.4f, 1.15f, 0.95f, 0.6f, 0.45f, 0.31f, 9f, 0, CHAMPAGNE, WINE, SILVER, PEARL),
            new Spec("Hybrid sedan", SEDAN, 4.6f, 1.76f, 0.97f, 1.47f, 1.0f, 0.75f, 1.0f, 0.75f, 0.32f, 4f, 0, PEARL, SILVER, GUNMETAL, BLUE),
            new Spec("Executive sedan", SEDAN, 4.95f, 1.8f, 1.0f, 1.46f, 1.35f, 1.05f, 0.85f, 0.65f, 0.34f, 7f, 0, BLACK, PEARL, DARKBLUE, SILVER),
            new Spec("Street coupe", SPORTS, 4.3f, 1.72f, 0.82f, 1.25f, 1.3f, 0.75f, 0.95f, 0.85f, 0.31f, 5f, 3, WHITE, RED, BLACK, YELLOW),
            new Spec("Rotary wedge", SPORTS, 4.3f, 1.75f, 0.76f, 1.18f, 1.55f, 0.6f, 1.1f, 0.9f, 0.31f, 6f, 0, RED, SILVER, PEARL, BLUE),
            new Spec("Box minivan", VAN, 4.7f, 1.7f, 1.05f, 1.92f, 0.65f, 0.1f, 0.75f, 0.1f, 0.33f, 10f, 0, PEARL, BLACK, SILVER, CHAMPAGNE),
            new Spec("Family minivan", VAN, 4.8f, 1.8f, 1.05f, 1.75f, 0.9f, 0.12f, 1.0f, 0.25f, 0.33f, 5f, 0, SILVER, PEARL, WINE, SKY),
            new Spec("Station wagon", WAGON, 4.7f, 1.72f, 0.97f, 1.48f, 1.05f, 0.12f, 0.85f, 0.2f, 0.32f, 6f, 7, SILVER, GREEN, PEARL, DARKBLUE),
            new Spec("Compact SUV", SUV, 4.3f, 1.8f, 1.1f, 1.62f, 0.95f, 0.12f, 0.8f, 0.3f, 0.36f, 5f, 0, PEARL, ORANGE, GUNMETAL, TEAL),
            new Spec("Land cruiser", SUV, 4.9f, 1.95f, 1.22f, 1.88f, 1.2f, 0.12f, 0.55f, 0.12f, 0.41f, 9f, 7, WHITE, BLACK, CHAMPAGNE, GREEN),
            new Spec("JPN taxi", VAN, 4.4f, 1.7f, 1.0f, 1.72f, 0.95f, 0.12f, 0.9f, 0.2f, 0.31f, 7f, 1, DARKBLUE, DARKBLUE, BLACK, KEIMINT),
            new Spec("Classic taxi", SEDAN, 4.7f, 1.7f, 0.98f, 1.45f, 1.2f, 0.95f, 0.55f, 0.4f, 0.31f, 10f, 1, BLACK, YELLOW, ORANGE, BUSGREEN),
            new Spec("Patrol car", SEDAN, 4.9f, 1.8f, 1.0f, 1.47f, 1.25f, 1.0f, 0.8f, 0.6f, 0.33f, 6f, 2, WHITE, WHITE, WHITE, WHITE),
            new Spec("Delivery van", VAN, 4.7f, 1.7f, 1.12f, 2.0f, 0.3f, 0.08f, 0.35f, 0.05f, 0.31f, 11f, 0, WHITE, SILVER, WHITE, BLUE),
            new Spec("Light truck", BOXTRUCK, 4.7f, 1.7f, 1.2f, 2.0f, 0.3f, 0f, 0.3f, 0f, 0.33f, 10f, 0, WHITE, SKY, YELLOW, GREEN),
            new Spec("Box truck", BOXTRUCK, 6.8f, 2.2f, 1.3f, 2.3f, 0.35f, 0f, 0.3f, 0f, 0.42f, 10f, 0, WHITE, SILVER, BLUE, ORANGE),
            new Spec("Fire engine", BOXTRUCK, 7.2f, 2.3f, 1.3f, 2.4f, 0.4f, 0f, 0.35f, 0f, 0.45f, 10f, 8, FIRERED, FIRERED, FIRERED, FIRERED),
            new Spec("City bus", BUS, 10.5f, 2.5f, 3.0f, 3.0f, 0.2f, 0f, 0f, 0f, 0.48f, 10f, 0, BUSGREEN, WHITE, SKY, ORANGE),
            new Spec("Ambulance", VAN, 5.6f, 1.9f, 1.15f, 2.3f, 0.6f, 0.08f, 0.5f, 0.05f, 0.35f, 10f, 9, WHITE, WHITE, WHITE, WHITE),
            new Spec("Roadster", CONVERTIBLE, 3.95f, 1.69f, 0.84f, 1.15f, 1.1f, 0.85f, 0.6f, 0.3f, 0.3f, 4f, 0, RED, SILVER, BLUE, BLACK),
    };

    private static int SAND() { return 0xD8C08A; }

    public static Spec spec(int style) { return MODELS_TABLE[(style / PAINTS) % MODELS]; }

    /** Sprite frame size for a model (fits every rotation). */
    public static int[] frame(int style) {
        Spec s = spec(style);
        float diag = (float) Math.sqrt(s.L * s.L + s.W * s.W);
        int w = (int) Math.ceil(diag * PPM) + 6;
        w += w & 1;
        float hgt = s.height();
        int h = (int) Math.ceil(hgt * PPM) + 8;
        return new int[]{w, h, w / 2, h - 3};
    }

    // ------------------------------------------------------------------ building

    public static List<Part> build(int style) {
        Spec s = spec(style);
        int paint = s.paints[style % PAINTS];
        int paint2 = paint == WHITE ? BLACK : WHITE;
        List<Part> parts = new ArrayList<>();
        float L = s.L, hw = s.W * 0.5f, z0 = -L * 0.5f, z1 = L * 0.5f;
        float bottom = s.wheelR * 0.75f;
        float belt = s.belt;
        final int extra = s.extra;
        final int glass = 0x283440, bumper = s.type == CLASSIC ? 0xD8DCE0 : 0x2A2C30;

        // lower body
        final float stripeW = 0.22f;
        Lathe.ColorFn bodyC = (x, y, z) -> {
            if (z < z0 + 0.12f || z > z1 - 0.12f) return bumper;
            if (y < bottom + 0.1f) return mul(paint, 0.7f);
            if (extra == 4 && Math.abs(x) < stripeW && y > belt - 0.15f) return paint2;
            if (extra == 2 && y < belt - 0.08f && Math.abs(z) < L * 0.3f) return paint == WHITE ? 0x1E2E58 : WHITE;
            if (extra == 6 && y < belt - 0.25f) return CREAM;
            return paint;
        };
        float deckRear = s.type == PICKUP ? belt : belt - 0.03f;
        float hoodFront = belt - 0.14f;
        if (s.type == SPORTS || s.type == CONVERTIBLE) hoodFront = belt - 0.2f;
        Loft body = new Loft().power(s.power).color(bodyC)
                .station(z0, hw * 0.9f, bottom + 0.05f, deckRear - 0.12f)
                .station(z0 + 0.18f, hw, bottom, deckRear)
                .station(z0 + L * 0.5f, hw, bottom, belt)
                .station(z1 - 0.35f, hw, bottom, hoodFront + 0.05f)
                .station(z1, hw * 0.9f, bottom + 0.06f, hoodFront - 0.1f);
        if (s.type == BUG) {
            body = new Loft().power(s.power).color(bodyC)
                    .station(z0, hw * 0.6f, bottom + 0.1f, belt - 0.3f)
                    .station(z0 + 0.4f, hw, bottom, belt)
                    .station(z1 - 0.5f, hw, bottom, belt - 0.05f)
                    .station(z1, hw * 0.6f, bottom + 0.15f, belt - 0.35f);
        }
        parts.add(body.build());

        if (s.type == BUS) {
            final float wy0 = 1.25f, wy1 = 2.55f;
            parts.set(parts.size() - 1, new Loft().power(12f).color((x, y, z) -> {
                if (z < z0 + 0.1f || z > z1 - 0.1f) return y > 1.1f && y < 2.6f ? glass : bumper;
                if (y > wy0 && y < wy1) return glass;
                if (y < 0.9f) return paint2 == WHITE ? 0xE8E8E2 : paint2;
                return paint;
            }).station(z0, hw, bottom, 3.0f).station(z1, hw, bottom, 3.0f).build());
        }

        // cabin / greenhouse
        float cabFront = z1 - s.hood, cabRear = z0 + s.trunk;
        if (s.type == BOXTRUCK) {
            cabRear = cabFront - 1.6f;
        }
        float rhw = hw * (s.type == SUV || s.type == VAN || s.type == BOXTRUCK || s.type == JEEP ? 0.92f : 0.8f);
        final float roof = s.roof;
        final int roofC = extra == 2 ? (paint == WHITE ? WHITE : paint) : paint;
        Lathe.ColorFn cabC = (x, y, z) -> y > roof - 0.07f ? roofC : glass;
        if (s.type == CONVERTIBLE || s.type == JEEP) {
            // open top: windshield frame + seats
            parts.add(new Loft().power(6f).color(glass)
                    .station(cabFront, hw * 0.88f, belt - 0.02f, belt)
                    .station(cabFront - 0.08f, hw * 0.88f, belt - 0.02f, belt + 0.42f)
                    .station(cabFront - 0.16f, hw * 0.88f, belt - 0.02f, belt + 0.42f).build());
            for (int k = 0; k < 2; k++)
                for (int sd = -1; sd <= 1; sd += 2)
                    parts.add(box(sd * hw * 0.42f, belt + 0.12f, cabFront - 0.8f - k * 0.9f, hw * 0.32f, 0.22f, 0.25f, 0x3A2A24));
            if (s.type == JEEP) // roll bar
                parts.add(box(0, roof - 0.1f, cabRear + 0.5f, hw * 0.9f, 0.05f, 0.05f, 0x2A2A2A));
        } else if (s.type != BUS) {
            float wsTop = cabFront - s.ws, rwTop = cabRear + s.rw;
            if (rwTop > wsTop - 0.3f) rwTop = wsTop - 0.3f;
            parts.add(new Loft().power(s.power + 1f).color(cabC)
                    .station(cabRear, rhw * 1.05f, belt - 0.05f, belt)
                    .station(rwTop, rhw, belt - 0.05f, roof)
                    .station(wsTop, rhw, belt - 0.05f, roof)
                    .station(cabFront, rhw * 1.05f, belt - 0.05f, belt).build());
        }
        if (s.type == BOXTRUCK) {
            final int boxC = extra == 8 ? FIRERED : 0xEEEEEA;
            parts.add(new Loft().power(12f).color((x, y, z) -> Math.abs(y - 2.1f) < 0.25f ? (extra == 8 ? WHITE : paint2) : boxC)
                    .station(z0, hw * 1.02f, belt - 0.3f, 3.2f).station(cabRear - 0.1f, hw * 1.02f, belt - 0.3f, 3.2f).build());
        }
        if (s.type == PICKUP) {
            // dark bed interior
            parts.add(box(0, belt - 0.12f, (z0 + cabRear) * 0.5f, hw * 0.85f, 0.06f, (cabRear - z0) * 0.45f, 0x2A2826));
        }

        // wheels
        float wr = s.wheelR, ww = 0.24f;
        float axleF = z1 - Math.min(1.05f, L * 0.2f), axleR = z0 + Math.min(1.0f, L * 0.19f);
        if (s.type == BOXTRUCK) axleR = z0 + 1.4f;
        for (float az : new float[]{axleF, axleR})
            for (int sd = -1; sd <= 1; sd += 2) {
                float wx = sd * (hw - ww * 0.4f);
                parts.add(new Lathe().sides(10).ring(-ww * 0.5f, wr, wr).ring(ww * 0.5f, wr, wr).color(0x18181A)
                        .transform(Mat4.mul(Mat4.translation(wx, wr, az), Mat4.rotZ((float) (Math.PI / 2)))).build(0));
                parts.add(new Lathe().sides(8).ring(-ww * 0.52f, wr * 0.55f, wr * 0.55f).ring(ww * 0.52f, wr * 0.55f, wr * 0.55f)
                        .color(s.type == CLASSIC ? 0xF0F0F0 : 0xA8ACB0)
                        .transform(Mat4.mul(Mat4.translation(wx, wr, az), Mat4.rotZ((float) (Math.PI / 2)))).build(0));
            }

        // lights
        float ly = hoodFront - 0.12f;
        for (int sd = -1; sd <= 1; sd += 2) {
            parts.add(emissiveBox(sd * hw * 0.68f, ly, z1 - 0.02f, 0.16f, 0.06f, 0.04f, 0xFFF6DC));
            parts.add(emissiveBox(sd * hw * 0.72f, deckRear - 0.14f, z0 + 0.02f, 0.16f, 0.06f, 0.04f, 0xE02420));
        }
        // extras
        if (extra == 1) parts.add(emissiveBox(0, roof + 0.12f, (cabFront - s.ws + cabRear + s.rw) * 0.5f, 0.32f, 0.1f, 0.12f, 0xFFF0A0));
        if (extra == 2) {
            float lz = (cabFront - s.ws + cabRear + s.rw) * 0.5f;
            parts.add(emissiveBox(-0.35f, roof + 0.08f, lz, 0.3f, 0.06f, 0.12f, 0xFF2A2A));
            parts.add(emissiveBox(0.35f, roof + 0.08f, lz, 0.3f, 0.06f, 0.12f, 0x2A5AFF));
        }
        if (extra == 3) {
            parts.add(box(0, deckRear + 0.18f, z0 + 0.25f, hw * 0.85f, 0.03f, 0.14f, mul(paint, 0.8f)));
            for (int sd = -1; sd <= 1; sd += 2) parts.add(box(sd * hw * 0.5f, deckRear + 0.08f, z0 + 0.25f, 0.03f, 0.08f, 0.05f, 0x202020));
        }
        if (extra == 8) {   // fire engine: ladder on top + light bar
            parts.add(box(0, 3.3f, (z0 + cabRear) * 0.5f, hw * 0.35f, 0.08f, (cabRear - z0) * 0.48f, 0xC8CCD0));
            parts.add(emissiveBox(0, roof + 0.08f, cabFront - 0.6f, hw * 0.6f, 0.07f, 0.12f, 0xFF2A2A));
        }
        if (extra == 9) {   // ambulance: red stripe and lights
            parts.add(new Loft().power(12f).color(0xD02020).station(z0 + 0.1f, hw * 1.01f, belt - 0.12f, belt - 0.02f)
                    .station(z1 - 0.3f, hw * 1.01f, belt - 0.12f, belt - 0.02f).build());
            parts.add(emissiveBox(0, roof + 0.08f, cabFront - 0.5f, hw * 0.55f, 0.07f, 0.12f, 0xFF2A2A));
        }
        if (extra == 6) {
            for (int sd = -1; sd <= 1; sd += 2)
                parts.add(new Loft().power(3f).color(paint)
                        .station(z0 - 0.05f, 0.04f, belt, belt + 0.25f).station(z0 + 0.9f, 0.08f, belt - 0.05f, belt + 0.02f)
                        .transform(Mat4.translation(sd * (hw - 0.06f), 0, 0)).build());
        }
        if (extra == 7) {
            float rz0 = cabRear + s.rw, rz1 = cabFront - s.ws;
            for (int sd = -1; sd <= 1; sd += 2) parts.add(box(sd * rhw * 0.85f, roof + 0.05f, (rz0 + rz1) * 0.5f, 0.03f, 0.03f, (rz1 - rz0) * 0.45f, 0x3A3A3A));
        }
        if (s.type == JEEP) // spare tyre on the back
            parts.add(new Lathe().sides(10).ring(-0.12f, wr, wr).ring(0.12f, wr, wr).color(0x18181A)
                    .transform(Mat4.mul(Mat4.translation(0, belt + 0.1f, z0 - 0.1f), Mat4.rotX((float) (Math.PI / 2)))).build(0));
        return parts;
    }

    private static Part box(float x, float y, float z, float hx, float hy, float hz, int color) {
        return new Loft().power(12f).sides(8).color(color)
                .station(z - hz, hx, y - hy, y + hy).station(z + hz, hx, y - hy, y + hy)
                .transform(Mat4.translation(x, 0, 0)).build();
    }

    private static Part emissiveBox(float x, float y, float z, float hx, float hy, float hz, int color) {
        return new Loft().power(10f).sides(8).color(color).emissive()
                .station(z - hz, hx, y - hy, y + hy).station(z + hz, hx, y - hy, y + hy)
                .transform(Mat4.translation(x, 0, 0)).build();
    }

    static int mul(int c, float k) { return PropModels.mul(c, k); }
}
