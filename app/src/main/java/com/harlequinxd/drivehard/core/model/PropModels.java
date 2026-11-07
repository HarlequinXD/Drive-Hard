package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural 3D props that get baked into 8-direction sprites. Model space: Y up, base at the
 * origin, the prop's "front" faces +Z.
 */
public final class PropModels {
    private PropModels() {}

    // ------------------------------------------------------------------ palm

    /** A coconut palm. Leans toward +X. */
    public static List<Part> palm(long seed) {
        Random r = new Random(seed);
        List<Part> parts = new ArrayList<>();
        float h = 8.5f + r.nextFloat() * 2.5f;
        float lean = 0.6f + r.nextFloat() * 1.0f;
        int segs = 7;
        float px = 0, py = 0;
        for (int s = 0; s < segs; s++) {
            float t0 = s / (float) segs, t1 = (s + 1) / (float) segs;
            float x0 = lean * t0 * t0, x1 = lean * t1 * t1;
            float y0 = h * t0, y1 = h * t1;
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
            float ang = -(float) Math.atan2(dx, dy);
            float r0 = 0.24f - 0.08f * t0, r1 = 0.24f - 0.08f * t1;
            final int band = s;
            Lathe l = new Lathe().sides(7)
                    .ring(0f, r0 * (s == 0 ? 1.4f : 1f), r0 * (s == 0 ? 1.4f : 1f))
                    .ring(len * 0.5f, (r0 + r1) * 0.5f, (r0 + r1) * 0.5f)
                    .ring(len, r1, r1)
                    .color((x, y, z) -> (((int) ((y + band * 3.1f) / 0.18f)) & 1) == 0 ? 0x8E7A62 : 0x76654F)
                    .openEnds()
                    .transform(Mat4.mul(Mat4.translation(x0, y0, 0), Mat4.rotZ(ang)));
            parts.add(l.build(0));
            px = x1;
            py = y1;
        }
        // crown shaft + coconuts
        parts.add(Lathe.ellipsoid(0.26f, 0.45f, 0.26f, 6, 7).color(0x6E7A40)
                .transform(Mat4.translation(px, py + 0.2f, 0)).build(0));
        for (int i = 0; i < 4; i++) {
            double a = i * 1.7 + r.nextFloat();
            parts.add(Lathe.ellipsoid(0.11f, 0.12f, 0.11f, 4, 6).color(0x5A4A2A)
                    .transform(Mat4.translation(px + (float) Math.sin(a) * 0.22f, py - 0.05f, (float) Math.cos(a) * 0.22f)).build(0));
        }
        // fronds: arching, then drooping chains of flattened leaf segments
        int fronds = 13 + r.nextInt(3);
        float rot = r.nextFloat() * 6.28f;
        for (int f = 0; f < fronds; f++) {
            float yaw = rot + 6.2832f * f / fronds + (r.nextFloat() - 0.5f) * 0.3f;
            float elev = 0.45f + (r.nextFloat() - 0.5f) * 0.6f;
            float cx = px, cy = py + 0.45f, cz = 0;
            int segN = 4;
            float segLen = 0.95f + r.nextFloat() * 0.25f;
            float[] widths = {0.06f, 0.36f, 0.46f, 0.38f, 0.04f};
            for (int k = 0; k < segN; k++) {
                float e = elev - k * (0.42f + r.nextFloat() * 0.12f);
                float dxz = (float) Math.cos(e) * segLen, dyv = (float) Math.sin(e) * segLen;
                final int seg = k;
                // continuous flat strip: lathe along +Y, pointed along the frond direction
                Mat4 m = Mat4.mul(Mat4.translation(cx, cy, cz),
                        Mat4.mul(Mat4.rotY(yaw), Mat4.rotX((float) (Math.PI / 2) - e)));
                parts.add(new Lathe().sides(6)
                        .ring(0f, widths[k], 0.03f)
                        .ring(segLen * 0.5f, (widths[k] + widths[k + 1]) * 0.55f, 0.03f)
                        .ring(segLen, widths[k + 1], 0.03f)
                        .openEnds()
                        .color((x, y, z) -> {
                            double leaflet = Math.sin(y * 38.0 + Math.abs(x) * 20.0);
                            int base = seg == 0 ? 0x4A6E30 : (seg >= 3 ? 0x557F34 : 0x3E7A2E);
                            if (Math.abs(x) > 0.3f) base = mul(base, 0.85f);
                            return leaflet > 0.2 ? mul(base, 1.18f) : (leaflet < -0.6 ? mul(base, 0.7f) : base);
                        })
                        .transform(m).build(0));
                cx += (float) Math.sin(yaw) * dxz;
                cy += dyv;
                cz += (float) Math.cos(yaw) * dxz;
            }
        }
        return parts;
    }

    // ------------------------------------------------------------------ street lamp

    /** Cobra-head street light, arm reaching toward +Z (the road). */
    public static List<Part> streetLamp() {
        List<Part> parts = new ArrayList<>();
        int pole = 0x4A5452;
        parts.add(new Lathe().sides(8)
                .ring(0f, 0.17f, 0.17f).ring(0.45f, 0.15f, 0.15f).ring(0.5f, 0.1f, 0.1f)
                .ring(6.9f, 0.075f, 0.075f).ring(7.05f, 0.04f, 0.04f)
                .color(pole).build(0));
        // arm: from the pole top curving out toward +Z
        Mat4 arm = Mat4.mul(Mat4.translation(0, 6.85f, 0), Mat4.rotX((float) Math.toRadians(80)));
        parts.add(new Lathe().sides(6).ring(0f, 0.055f, 0.055f).ring(2.2f, 0.045f, 0.045f).color(pole).transform(arm).build(0));
        // lamp head at the arm end
        float ex = 0, ey = 6.85f + (float) Math.cos(Math.toRadians(80)) * 2.2f, ez = (float) Math.sin(Math.toRadians(80)) * 2.2f;
        parts.add(Lathe.ellipsoid(0.22f, 0.09f, 0.42f, 5, 8)
                .color((x, y, z) -> y < -0.03f ? 0xFFF6D8 : 0xB8BCB8)
                .transform(Mat4.translation(ex, ey, ez + 0.2f)).build(0));
        return parts;
    }

    // ------------------------------------------------------------------ beach umbrella

    public static final int[][] UMBRELLA_COLORS = {
            {0xE8483C, 0xF4F0E6}, {0x2E7FC8, 0xF4F0E6}, {0xF2C230, 0xE8483C}, {0x3EA87A, 0xF4F0E6}};

    /** Striped beach umbrella, slightly tilted, with a wooden pole. */
    public static List<Part> umbrella(int variant) {
        List<Part> parts = new ArrayList<>();
        final int[] cols = UMBRELLA_COLORS[variant % UMBRELLA_COLORS.length];
        Mat4 tilt = Mat4.rotX(0.18f);
        parts.add(new Lathe().sides(6).ring(0f, 0.035f, 0.035f).ring(2.35f, 0.03f, 0.03f)
                .color(0xE4D8C0).transform(tilt).build(0));
        // canopy: shallow cone with alternating panels
        parts.add(new Lathe().sides(8)
                .ring(1.95f, 1.35f, 1.35f).ring(2.1f, 1.25f, 1.25f).ring(2.35f, 0.7f, 0.7f).ring(2.48f, 0.05f, 0.05f)
                .color((x, y, z) -> {
                    double a = Math.atan2(x, z);
                    int panel = (int) Math.floor((a + Math.PI) / (Math.PI / 4));
                    return (panel & 1) == 0 ? cols[0] : cols[1];
                })
                .transform(tilt).build(0));
        // towel on the sand
        parts.add(new Lathe().sides(4).ring(0f, 0.6f, 1.2f).ring(0.02f, 0.6f, 1.2f).openEnds()
                .color(variant % 2 == 0 ? 0xF0E0A0 : 0x7AC0E0)
                .transform(Mat4.mul(Mat4.translation(0.9f, 0.01f, 0.3f), Mat4.rotY((float) (Math.PI / 4)))).build(0));
        return parts;
    }

    // ------------------------------------------------------------------ traffic signal

    public static final int SIGNAL_GREEN = 0, SIGNAL_YELLOW = 1, SIGNAL_RED = 2;

    /**
     * Japanese traffic signal: grey pole on the corner, short arm reaching along +X over the lane,
     * a horizontal head facing +Z with blue-green / yellow / red lamps (left to right as seen by
     * drivers) under visors, plus a pedestrian signal on the pole.
     */
    public static List<Part> trafficSignal(int state) {
        List<Part> parts = new ArrayList<>();
        int metal = 0xA4AAAE, dark = 0x2A2E32, housing = 0x7C8286;
        // pole on a base collar, capped
        parts.add(new Lathe().sides(8)
                .ring(0f, 0.2f, 0.2f).ring(0.22f, 0.2f, 0.2f).ring(0.26f, 0.13f, 0.13f).ring(0.32f, 0.11f, 0.11f)
                .ring(5.4f, 0.09f, 0.09f).ring(5.48f, 0.11f, 0.11f).ring(5.56f, 0.03f, 0.03f)
                .color(metal).build(0));
        float armY = 5.1f, armLen = 3.8f;
        // the arm and a diagonal brace under it
        parts.add(new Lathe().sides(6).ring(0f, 0.065f, 0.065f).ring(armLen, 0.05f, 0.05f).color(metal)
                .transform(Mat4.mul(Mat4.translation(0, armY, 0), Mat4.rotZ((float) (-Math.PI / 2)))).build(0));
        float bl = (float) Math.hypot(1.4f, 0.7f);
        parts.add(new Lathe().sides(5).ring(0f, 0.03f, 0.03f).ring(bl, 0.03f, 0.03f).color(metal)
                .transform(Mat4.mul(Mat4.translation(0.05f, armY - 0.7f, 0), Mat4.rotZ((float) -Math.atan2(1.4f, 0.7f)))).build(0));
        // the junction's name plate hung under the arm: blue, white border
        parts.add(box(0.5f, 0.15f, 0.025f, 0xF2F4F6, Mat4.translation(1.25f, armY - 0.3f, 0.02f)));
        parts.add(box(0.46f, 0.12f, 0.03f, 0x2A5CB0, Mat4.translation(1.25f, armY - 0.3f, 0.025f)));
        // the signal head: housing, a black backplate with a white rim, three lamps under deep visors
        float hx = armLen - 0.7f, hy = armY - 0.02f;
        parts.add(box(0.68f, 0.22f, 0.14f, housing, Mat4.translation(hx, hy, 0f)));
        parts.add(box(0.8f, 0.3f, 0.02f, 0xD8DCDE, Mat4.translation(hx, hy, -0.15f)));
        parts.add(box(0.77f, 0.27f, 0.025f, 0x18191B, Mat4.translation(hx, hy, -0.16f)));
        int[] lit = {0x38F0C0, 0xFFD030, 0xFF3A28};
        int[] off = {0x1C3C36, 0x3C3618, 0x3C1C1A};
        int[] order = {SIGNAL_GREEN, SIGNAL_YELLOW, SIGNAL_RED};
        for (int i = 0; i < 3; i++) {
            int lamp = order[i];
            boolean on = lamp == state;
            float lx = hx - 0.44f + i * 0.44f;
            parts.add(box(0.17f, 0.17f, 0.012f, dark, Mat4.translation(lx, hy, 0.142f)));
            Lathe l = Lathe.ellipsoid(0.145f, 0.145f, 0.035f, 4, 10).color(on ? lit[lamp] : off[lamp])
                    .transform(Mat4.translation(lx, hy, 0.15f));
            if (on) l.emissive();
            parts.add(l.build(0));
            // visor: a hood over the top and down both sides
            parts.add(box(0.18f, 0.012f, 0.13f, housing, Mat4.translation(lx, hy + 0.175f, 0.27f)));
            parts.add(box(0.012f, 0.1f, 0.11f, housing, Mat4.translation(lx - 0.17f, hy + 0.08f, 0.25f)));
            parts.add(box(0.012f, 0.1f, 0.11f, housing, Mat4.translation(lx + 0.17f, hy + 0.08f, 0.25f)));
        }
        // pedestrian signal: a box with two lamps (walking figure green, standing figure red)
        float py = 2.75f;
        parts.add(box(0.2f, 0.38f, 0.12f, housing, Mat4.translation(0.3f, py, 0f)));
        boolean walk = state == SIGNAL_GREEN;
        parts.add(box(0.14f, 0.14f, 0.02f, walk ? 0x38F0C0 : 0x1C3C36, Mat4.translation(0.3f, py - 0.17f, 0.12f), walk));
        parts.add(box(0.14f, 0.14f, 0.02f, !walk ? 0xFF3A28 : 0x3C1C1A, Mat4.translation(0.3f, py + 0.17f, 0.12f), !walk));
        parts.add(box(0.2f, 0.012f, 0.1f, housing, Mat4.translation(0.3f, py + 0.36f, 0.2f)));
        parts.add(box(0.2f, 0.012f, 0.1f, housing, Mat4.translation(0.3f, py + 0.02f, 0.2f)));
        // the push button box for pedestrians, and a little sign above it
        parts.add(box(0.08f, 0.13f, 0.06f, 0xE8C020, Mat4.translation(0f, 1.15f, 0.14f)));
        parts.add(box(0.11f, 0.08f, 0.015f, 0xF2F4F6, Mat4.translation(0f, 1.42f, 0.13f)));
        return parts;
    }

    // ------------------------------------------------------------------ utility pole

    /**
     * Japanese concrete utility pole: a tiger-striped guard at the foot, a pole number plate and
     * a long advert plate, step bolts up the sides, two cross-arms with white insulators, a
     * transformer drum on its bracket with bushings, and the service cable down the pole.
     */
    public static List<Part> utilityPole() {
        List<Part> parts = new ArrayList<>();
        // the foot in short rings, so the guard's stripes and the casting seams show
        Lathe pole = new Lathe().sides(8);
        for (float y = 0f; y < 10.5f; y += y < 1.8f ? 0.22f : 1.1f)
            pole.ring(y, 0.18f - y * 0.0067f, 0.18f - y * 0.0067f);
        pole.ring(10.5f, 0.11f, 0.11f).ring(10.6f, 0.05f, 0.05f)
                .color((x, y, z) -> {
                    if (y < 1.76f) return (((int) (y / 0.22f)) & 1) == 0 ? 0xF0C420 : 0x1E1E20;
                    return ((int) (y / 2.2f) & 1) == 0 ? 0xBCB8B0 : 0xB2AEA6;      // casting seams
                });
        parts.add(pole.build(0));
        // pole number plate and the advert plate under it
        parts.add(box(0.13f, 0.11f, 0.012f, 0xF2F2EE, Mat4.translation(0f, 1.95f, 0.18f)));
        parts.add(box(0.11f, 0.03f, 0.014f, 0x2A5CB0, Mat4.translation(0f, 2.02f, 0.18f)));
        parts.add(box(0.16f, 0.55f, 0.015f, 0x2E7AC4, Mat4.translation(0f, 2.75f, 0.18f)));
        parts.add(box(0.13f, 0.42f, 0.017f, 0xF6F2E2, Mat4.translation(0f, 2.82f, 0.18f)));
        parts.add(box(0.13f, 0.07f, 0.018f, 0xD82828, Mat4.translation(0f, 2.4f, 0.18f)));
        // step bolts, alternating sides
        for (float y = 2.6f; y < 8.8f; y += 0.45f) {
            float side = ((int) ((y - 2.6f) / 0.45f) & 1) == 0 ? 1f : -1f;
            parts.add(box(0.11f, 0.018f, 0.018f, 0x3A3C3E, Mat4.translation(side * 0.2f, y, 0f)));
        }
        // cross-arms with insulators
        for (float y : new float[]{9.9f, 9.2f}) {
            parts.add(box(0.05f, 0.05f, 0.8f, 0x6E6A66, Mat4.translation(0, y, 0)));
            for (float z = -0.65f; z <= 0.66f; z += 0.65f)
                if (Math.abs(z) > 0.1f || y > 9.5f)
                    parts.add(new Lathe().sides(6).ring(0f, 0.045f, 0.045f).ring(0.05f, 0.06f, 0.06f).ring(0.1f, 0.045f, 0.045f)
                            .ring(0.15f, 0.06f, 0.06f).ring(0.2f, 0.03f, 0.03f).color(0xF0F0EA)
                            .transform(Mat4.translation(0, y + 0.05f, z)).build(0));
        }
        // the transformer: drum, bracket, bushings and its cooling fins
        parts.add(box(0.2f, 0.04f, 0.12f, 0x5A5E60, Mat4.translation(0.22f, 7.3f, 0)));
        parts.add(new Lathe().sides(10).ring(-0.48f, 0.27f, 0.27f).ring(-0.46f, 0.3f, 0.3f).ring(0.42f, 0.3f, 0.3f)
                .ring(0.46f, 0.26f, 0.26f).ring(0.5f, 0.1f, 0.1f).color(0x8E969C)
                .transform(Mat4.translation(0.45f, 7.85f, 0)).build(0));
        for (float z : new float[]{-0.12f, 0.12f})
            parts.add(new Lathe().sides(6).ring(0f, 0.035f, 0.035f).ring(0.18f, 0.03f, 0.03f).color(0x3A3C3E)
                    .transform(Mat4.translation(0.45f, 8.35f, z)).build(0));
        // the service cable from the transformer down the pole
        parts.add(new Lathe().sides(4).ring(0f, 0.025f, 0.025f).ring(3.6f, 0.025f, 0.025f).color(0x1E1E20)
                .transform(Mat4.translation(-0.17f, 3.9f, 0.06f)).build(0));
        parts.add(box(0.07f, 0.16f, 0.06f, 0x5A5E60, Mat4.translation(-0.2f, 3.8f, 0.05f)));
        return parts;
    }

    private static Part box(float hx, float hy, float hz, int color, Mat4 at) {
        return box(hx, hy, hz, color, at, false);
    }

    /** Axis-aligned box from a 4-sided lathe rotated 45 degrees (half extents). */
    private static Part box(float hx, float hy, float hz, int color, Mat4 at, boolean emissive) {
        float k = (float) Math.sqrt(2);
        Lathe l = new Lathe().sides(4)
                .ring(-hy, 1f, 1f).ring(hy, 1f, 1f)
                .color(color)
                .transform(Mat4.mul(at, Mat4.mul(Mat4.scale(hx * k, 1f, hz * k), Mat4.rotY((float) (Math.PI / 4)))));
        if (emissive) l.emissive();
        return l.build(0);
    }

    static int mul(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)),
                b = Math.min(255, (int) ((c & 255) * k));
        return (r << 16) | (g << 8) | b;
    }
}
