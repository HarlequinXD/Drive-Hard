package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * South Florida / Miami plant species, each modelled procedurally in 3D and baked to an
 * 8-direction sprite sheet. Frame sizes and pixel densities are per species so tall palms and
 * wide banyans fit while shrubs stay cheap.
 */
public enum Flora {
    //            variants  frameW frameH anchorX anchorY ppm   tree   trunkRadius
    COCONUT(3, 240, 256, 120, 250, 20f, true, 0.25f),
    ROYAL_PALM(2, 160, 272, 80, 268, 13.5f, true, 0.3f),
    SABAL_PALM(2, 144, 192, 72, 186, 18f, true, 0.25f),
    WASHINGTONIA(2, 96, 256, 48, 252, 13f, true, 0.22f),
    DATE_PALM(1, 176, 176, 88, 170, 16f, true, 0.5f),
    FOXTAIL_PALM(1, 128, 176, 64, 170, 18f, true, 0.2f),
    BANYAN(1, 256, 176, 128, 170, 11f, true, 0.9f),
    LIVE_OAK(1, 208, 160, 104, 154, 12f, true, 0.6f),
    POINCIANA(1, 200, 144, 100, 138, 14f, true, 0.35f),
    GUMBO_LIMBO(1, 176, 160, 88, 154, 14f, true, 0.35f),
    SEA_GRAPE(2, 128, 96, 64, 92, 20f, false, 0f),
    MANGROVE(1, 144, 112, 72, 104, 18f, false, 0f),
    BOUGAINVILLEA(2, 96, 80, 48, 76, 24f, false, 0f),
    HIBISCUS(2, 80, 64, 40, 60, 24f, false, 0f),
    CROTON(1, 56, 48, 28, 44, 24f, false, 0f),
    BIRD_OF_PARADISE(1, 64, 64, 32, 60, 24f, false, 0f),
    SEA_OATS(1, 56, 48, 28, 44, 24f, false, 0f),
    AGAVE(1, 64, 48, 32, 44, 24f, false, 0f);

    public final int variants, frameW, frameH, anchorX, anchorY;
    public final float ppm, trunkRadius;
    /** Trees: drawn far, have a solid trunk. Shrubs: short draw distance, walk-through. */
    public final boolean tree;

    Flora(int variants, int frameW, int frameH, int anchorX, int anchorY, float ppm, boolean tree, float trunkRadius) {
        this.variants = variants;
        this.frameW = frameW;
        this.frameH = frameH;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.ppm = ppm;
        this.tree = tree;
        this.trunkRadius = trunkRadius;
    }

    /** Builds variant {@code v} of this species. */
    public List<Part> model(int v) {
        Random r = new Random(ordinal() * 1000L + v * 37L + 5L);
        List<Part> p = new ArrayList<>();
        switch (this) {
            case COCONUT: return PropModels.palm(1000 + v * 77);
            case ROYAL_PALM: royal(p, r, v); break;
            case SABAL_PALM: sabal(p, r, v); break;
            case WASHINGTONIA: washingtonia(p, r, v); break;
            case DATE_PALM: date(p, r); break;
            case FOXTAIL_PALM: foxtail(p, r); break;
            case BANYAN: banyan(p, r); break;
            case LIVE_OAK: liveOak(p, r); break;
            case POINCIANA: poinciana(p, r); break;
            case GUMBO_LIMBO: gumboLimbo(p, r); break;
            case SEA_GRAPE: seaGrape(p, r, v); break;
            case MANGROVE: mangrove(p, r); break;
            case BOUGAINVILLEA: shrub(p, r, 2.2f, 1.6f, 9, v == 0 ? 0xD8308A : 0xF08A40, 0.65f, 0x3E6A2E); break;
            case HIBISCUS: shrub(p, r, 1.7f, 1.1f, 7, v == 0 ? 0xE02030 : 0xF27095, 0.14f, 0x34622A); break;
            case CROTON: croton(p, r); break;
            case BIRD_OF_PARADISE: birdOfParadise(p, r); break;
            case SEA_OATS: seaOats(p, r); break;
            case AGAVE: agave(p, r); break;
        }
        return p;
    }

    // ------------------------------------------------------------------ helpers

    /** Tapered limb along a polyline of points (x,y,z triples). */
    static void limb(List<Part> parts, float[] pts, float r0, float r1, Lathe.ColorFn color, int sides) {
        int n = pts.length / 3 - 1;
        for (int i = 0; i < n; i++) {
            float x0 = pts[i * 3], y0 = pts[i * 3 + 1], z0 = pts[i * 3 + 2];
            float dx = pts[i * 3 + 3] - x0, dy = pts[i * 3 + 4] - y0, dz = pts[i * 3 + 5] - z0;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-4f) continue;
            float a = (float) Math.acos(Math.max(-1f, Math.min(1f, dy / len)));
            float b = (float) Math.atan2(dx, dz);
            float ra = r0 + (r1 - r0) * i / n, rb = r0 + (r1 - r0) * (i + 1) / n;
            parts.add(new Lathe().sides(sides).ring(-0.02f, ra, ra).ring(len + 0.02f, rb, rb).openEnds()
                    .color(color).transform(Mat4.mul(Mat4.translation(x0, y0, z0), Mat4.mul(Mat4.rotY(b), Mat4.rotX(a))))
                    .build(0));
        }
    }

    static void blob(List<Part> parts, float x, float y, float z, float rx, float ry, float rz, Lathe.ColorFn c) {
        parts.add(Lathe.ellipsoid(rx, ry, rz, 5, 8).color(c).foliage().transform(Mat4.translation(x, y, z)).build(0));
    }

    /** A frond / leaf strip: a chain of flat segments bending by {@code bend} per segment. */
    static void frond(List<Part> parts, float x, float y, float z, float yaw, float elev, float bend, float segLen,
                      float[] widths, Lathe.ColorFn color, boolean leafy) {
        float cx = x, cy = y, cz = z;
        for (int k = 0; k < widths.length - 1; k++) {
            float e = elev - k * bend;
            Mat4 m = Mat4.mul(Mat4.translation(cx, cy, cz), Mat4.mul(Mat4.rotY(yaw), Mat4.rotX((float) (Math.PI / 2) - e)));
            Lathe l = new Lathe().sides(6)
                    .ring(0f, widths[k], 0.03f)
                    .ring(segLen * 0.5f, (widths[k] + widths[k + 1]) * 0.55f, 0.03f)
                    .ring(segLen, widths[k + 1], 0.03f).openEnds().color(color).transform(m);
            if (leafy) l.foliage();
            parts.add(l.build(0));
            float dxz = (float) Math.cos(e) * segLen;
            cx += (float) Math.sin(yaw) * dxz;
            cy += (float) Math.sin(e) * segLen;
            cz += (float) Math.cos(yaw) * dxz;
        }
    }

    static Lathe.ColorFn leaves(int base) {
        return (x, y, z) -> {
            double s = Math.sin(x * 37.0 + y * 23.0 + z * 31.0);
            return s > 0.4 ? mul(base, 1.18f) : (s < -0.5 ? mul(base, 0.78f) : base);
        };
    }

    /** Leaves with a fraction of flower colour. */
    static Lathe.ColorFn flowers(int leaf, int flower, float frac) {
        return (x, y, z) -> {
            int h = (int) (x * 977) * 31 + (int) (y * 613) * 17 + (int) (z * 829);
            h = (h ^ (h >>> 13)) * 1274126177;
            float t = ((h >>> 8) & 1023) / 1023f;
            return t < frac ? (t < frac * 0.5f ? flower : mul(flower, 0.8f)) : leaves(leaf).color(x, y, z);
        };
    }

    static Lathe.ColorFn bark(int c, float band) {
        return (x, y, z) -> (((int) Math.floor(y / band)) & 1) == 0 ? c : mul(c, 0.85f);
    }

    static int mul(int c, float k) { return PropModels.mul(c, k); }

    static float[] curve(float h, float lean, int n) {
        float[] pts = new float[(n + 1) * 3];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            pts[i * 3] = lean * t * t;
            pts[i * 3 + 1] = h * t;
            pts[i * 3 + 2] = 0;
        }
        return pts;
    }

    // ------------------------------------------------------------------ palms

    private static void royal(List<Part> p, Random r, int v) {
        float h = 12.5f + v * 2.5f + r.nextFloat();
        float[] pts = curve(h, 0.25f, 6);
        // smooth grey concrete-like trunk with a mid bulge
        for (int i = 0; i < 6; i++) {
            float t0 = i / 6f, t1 = (i + 1) / 6f;
            float r0 = 0.3f + 0.08f * (float) Math.sin(t0 * Math.PI * 0.9) - 0.1f * t0;
            float r1 = 0.3f + 0.08f * (float) Math.sin(t1 * Math.PI * 0.9) - 0.1f * t1;
            limb(p, new float[]{pts[i * 3], pts[i * 3 + 1], 0, pts[i * 3 + 3], pts[i * 3 + 4], 0}, r0, r1,
                    bark(0xBEB8AE, 0.5f), 8);
        }
        float tx = pts[18], ty = pts[19];
        // bright green crownshaft
        limb(p, new float[]{tx, ty, 0, tx + 0.05f, ty + 2.4f, 0}, 0.24f, 0.2f, (x, y, z) -> 0x6E9A3A, 8);
        float rot = r.nextFloat() * 6.28f;
        for (int f = 0; f < 15; f++) {
            float yaw = rot + 6.283f * f / 15 + (r.nextFloat() - 0.5f) * 0.3f;
            frond(p, tx, ty + 2.4f, 0, yaw, 0.75f + (r.nextFloat() - 0.5f) * 0.6f, 0.38f, 1.25f,
                    new float[]{0.06f, 0.38f, 0.48f, 0.4f, 0.05f}, leaves(0x3C7A2E), false);
        }
    }

    private static void sabal(List<Part> p, Random r, int v) {
        float h = 6f + v * 2f + r.nextFloat();
        float[] pts = curve(h, 0.2f + r.nextFloat() * 0.3f, 4);
        limb(p, pts, 0.26f, 0.24f, (x, y, z) -> ((((int) (y * 6)) + ((int) ((x + z) * 8))) & 1) == 0 ? 0x7A6A54 : 0x5E5240, 7);
        float tx = pts[12], ty = pts[13];
        blob(p, tx, ty + 0.6f, 0, 0.9f, 0.9f, 0.9f, leaves(0x557F34));
        float rot = r.nextFloat() * 6.28f;
        for (int f = 0; f < 22; f++) {
            float yaw = rot + 6.283f * f / 11f + r.nextFloat() * 0.3f;
            float elev = f < 11 ? 0.9f : -0.1f + r.nextFloat() * 0.3f;
            frond(p, tx, ty + 0.4f, 0, yaw, elev, 0.25f, 0.75f,
                    new float[]{0.05f, 0.45f, 0.6f, 0.3f}, leaves(0x5E8A38), true);
        }
    }

    private static void washingtonia(List<Part> p, Random r, int v) {
        float h = 14f + v * 3f + r.nextFloat() * 1.5f;
        float[] pts = curve(h, 0.3f, 6);
        limb(p, pts, 0.24f, 0.17f, bark(0x7E6E5C, 0.4f), 7);
        float tx = pts[18], ty = pts[19];
        // shaggy skirt of dead fronds
        parts(p, new Lathe().sides(9).ring(ty - 3.2f, 0.35f, 0.35f).ring(ty - 1.5f, 0.75f, 0.75f).ring(ty, 0.8f, 0.8f)
                .ring(ty + 0.3f, 0.5f, 0.5f).color(leaves(0x8A6C44)).foliage().transform(Mat4.translation(tx, 0, 0)));
        float rot = r.nextFloat() * 6.28f;
        for (int f = 0; f < 14; f++) {
            float yaw = rot + 6.283f * f / 14;
            frond(p, tx, ty + 0.3f, 0, yaw, 0.6f + (f % 2) * 0.5f, 0.35f, 0.7f,
                    new float[]{0.05f, 0.4f, 0.55f, 0.25f}, leaves(0x5A8A3A), true);
        }
    }

    private static void parts(List<Part> p, Lathe l) { p.add(l.build(0)); }

    private static void date(List<Part> p, Random r) {
        float h = 6.5f + r.nextFloat();
        // thick trunk with a diamond "pineapple" pattern
        parts(p, new Lathe().sides(10).ring(0f, 0.55f, 0.55f).ring(h * 0.5f, 0.5f, 0.5f).ring(h, 0.52f, 0.52f)
                .ring(h + 0.4f, 0.4f, 0.4f).openEnds()
                .color((x, y, z) -> ((((int) (y * 4 + Math.atan2(x, z) * 3)) + ((int) (y * 4 - Math.atan2(x, z) * 3))) & 1) == 0
                        ? 0x8A7050 : 0x6A5038));
        blob(p, 0, h + 0.5f, 0, 0.9f, 0.7f, 0.9f, leaves(0x5A7E4A));
        float rot = r.nextFloat() * 6.28f;
        for (int f = 0; f < 34; f++) {
            float yaw = rot + 6.283f * f / 17f + r.nextFloat() * 0.2f;
            float elev = f < 17 ? 0.95f : 0.2f;
            frond(p, 0, h + 0.4f, 0, yaw, elev + (r.nextFloat() - 0.5f) * 0.3f, 0.32f, 1.25f,
                    new float[]{0.05f, 0.3f, 0.36f, 0.3f, 0.04f}, leaves(0x5C8050), false);
        }
    }

    private static void foxtail(List<Part> p, Random r) {
        float h = 6.5f + r.nextFloat();
        float[] pts = curve(h, 0.15f, 4);
        limb(p, pts, 0.22f, 0.18f, bark(0xA8A49A, 0.3f), 8);
        float tx = pts[12], ty = pts[13];
        limb(p, new float[]{tx, ty, 0, tx, ty + 1.4f, 0}, 0.19f, 0.15f, (x, y, z) -> 0x7AA04A, 8);
        float rot = r.nextFloat() * 6.28f;
        for (int f = 0; f < 11; f++) {
            float yaw = rot + 6.283f * f / 11;
            frond(p, tx, ty + 1.4f, 0, yaw, 0.7f + (r.nextFloat() - 0.5f) * 0.5f, 0.4f, 1.0f,
                    new float[]{0.08f, 0.5f, 0.62f, 0.5f, 0.1f}, leaves(0x4E8A34), true);
        }
    }

    // ------------------------------------------------------------------ broadleaf trees

    private static void banyan(List<Part> p, Random r) {
        Lathe.ColorFn barkC = bark(0x8A8478, 0.6f);
        limb(p, new float[]{0, 0, 0, 0.2f, 3.5f, 0}, 1.0f, 0.8f, barkC, 10);
        for (int b = 0; b < 6; b++) {
            float a = b * 1.05f + r.nextFloat() * 0.4f, len = 5f + r.nextFloat() * 2.5f;
            limb(p, new float[]{0.2f, 3.2f, 0, (float) Math.sin(a) * len * 0.5f, 5.5f, (float) Math.cos(a) * len * 0.5f,
                    (float) Math.sin(a) * len, 7f + r.nextFloat(), (float) Math.cos(a) * len}, 0.45f, 0.18f, barkC, 6);
        }
        // aerial prop roots
        for (int k = 0; k < 14; k++) {
            float a = r.nextFloat() * 6.28f, d = 2f + r.nextFloat() * 5f;
            float x = (float) Math.sin(a) * d, z = (float) Math.cos(a) * d;
            limb(p, new float[]{x, 0, z, x * 1.02f, 6.5f + r.nextFloat() * 1.5f, z * 1.02f}, 0.14f, 0.06f, barkC, 5);
        }
        for (int k = 0; k < 26; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * 8f;
            blob(p, (float) Math.sin(a) * d, 7.5f + r.nextFloat() * 3.5f - d * 0.15f, (float) Math.cos(a) * d,
                    2.4f + r.nextFloat(), 1.6f + r.nextFloat() * 0.6f, 2.4f + r.nextFloat(), leaves(0x3A6A2C));
        }
    }

    private static void liveOak(List<Part> p, Random r) {
        Lathe.ColorFn barkC = bark(0x5E5248, 0.35f);
        limb(p, new float[]{0, 0, 0, 0.3f, 2.6f, 0.1f}, 0.65f, 0.5f, barkC, 9);
        for (int b = 0; b < 5; b++) {
            float a = b * 1.25f + r.nextFloat() * 0.5f, len = 5f + r.nextFloat() * 2f;
            limb(p, new float[]{0.3f, 2.5f, 0.1f,
                    (float) Math.sin(a) * len * 0.45f, 4.2f, (float) Math.cos(a) * len * 0.45f,
                    (float) Math.sin(a) * len, 5.2f + r.nextFloat(), (float) Math.cos(a) * len}, 0.35f, 0.12f, barkC, 6);
        }
        for (int k = 0; k < 20; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * 6.5f;
            blob(p, (float) Math.sin(a) * d, 6.2f + r.nextFloat() * 2.2f - d * 0.12f, (float) Math.cos(a) * d,
                    2.0f + r.nextFloat() * 0.8f, 1.3f + r.nextFloat() * 0.5f, 2.0f + r.nextFloat() * 0.8f, leaves(0x46642E));
        }
        // Spanish moss hanging from the canopy
        for (int k = 0; k < 16; k++) {
            float a = r.nextFloat() * 6.28f, d = 2.5f + r.nextFloat() * 4f;
            blob(p, (float) Math.sin(a) * d, 4.6f + r.nextFloat(), (float) Math.cos(a) * d, 0.22f, 0.75f, 0.22f, leaves(0x9AA08A));
        }
    }

    private static void poinciana(List<Part> p, Random r) {
        Lathe.ColorFn barkC = bark(0x7A7266, 0.5f);
        limb(p, new float[]{0, 0, 0, 0.1f, 2.4f, 0}, 0.38f, 0.3f, barkC, 8);
        for (int b = 0; b < 5; b++) {
            float a = b * 1.26f + r.nextFloat() * 0.4f, len = 4.5f + r.nextFloat() * 1.5f;
            limb(p, new float[]{0.1f, 2.3f, 0, (float) Math.sin(a) * len * 0.6f, 4.2f, (float) Math.cos(a) * len * 0.6f,
                    (float) Math.sin(a) * len, 4.9f, (float) Math.cos(a) * len}, 0.2f, 0.08f, barkC, 6);
        }
        Lathe.ColorFn c = flowers(0x4E7A30, 0xE6402A, 0.7f);
        for (int k = 0; k < 18; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * 5.5f;
            blob(p, (float) Math.sin(a) * d, 5.2f + r.nextFloat() * 0.8f, (float) Math.cos(a) * d,
                    1.8f + r.nextFloat() * 0.6f, 0.9f + r.nextFloat() * 0.3f, 1.8f + r.nextFloat() * 0.6f, c);
        }
    }

    private static void gumboLimbo(List<Part> p, Random r) {
        Lathe.ColorFn barkC = (x, y, z) -> ((int) (y * 3 + x * 5) & 1) == 0 ? 0xB86A4E : 0xA05A40;
        limb(p, new float[]{0, 0, 0, 0.3f, 3f, 0}, 0.4f, 0.3f, barkC, 8);
        for (int b = 0; b < 4; b++) {
            float a = b * 1.6f + r.nextFloat() * 0.5f, len = 3.5f + r.nextFloat() * 1.5f;
            limb(p, new float[]{0.3f, 2.9f, 0, (float) Math.sin(a) * len * 0.5f, 5f, (float) Math.cos(a) * len * 0.5f,
                    (float) Math.sin(a) * len, 7f + r.nextFloat(), (float) Math.cos(a) * len}, 0.22f, 0.08f, barkC, 6);
        }
        for (int k = 0; k < 11; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * 4.5f;
            blob(p, (float) Math.sin(a) * d, 7f + r.nextFloat() * 1.8f, (float) Math.cos(a) * d,
                    1.6f + r.nextFloat() * 0.5f, 1.1f, 1.6f + r.nextFloat() * 0.5f, leaves(0x6A9A40));
        }
    }

    // ------------------------------------------------------------------ coastal & shrubs

    private static void seaGrape(List<Part> p, Random r, int v) {
        float s = v == 0 ? 1f : 0.65f;
        Lathe.ColorFn barkC = bark(0x6E5E4A, 0.3f);
        for (int k = 0; k < 4; k++) {
            float a = k * 1.6f + r.nextFloat();
            limb(p, new float[]{0, 0, 0, (float) Math.sin(a) * 1.2f * s, 2.2f * s, (float) Math.cos(a) * 1.2f * s}, 0.1f, 0.06f, barkC, 5);
        }
        Lathe.ColorFn c = flowers(0x6A8A3A, 0x9A5A3A, 0.15f);
        for (int k = 0; k < 12; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * 2.2f * s;
            blob(p, (float) Math.sin(a) * d, (1.2f + r.nextFloat() * 1.8f) * s, (float) Math.cos(a) * d,
                    0.9f * s, 0.75f * s, 0.9f * s, c);
        }
    }

    private static void mangrove(List<Part> p, Random r) {
        Lathe.ColorFn barkC = bark(0x5A4A3A, 0.25f);
        for (int k = 0; k < 11; k++) {
            float a = k * 0.57f + r.nextFloat() * 0.3f;
            float sx = (float) Math.sin(a), sz = (float) Math.cos(a);
            limb(p, new float[]{sx * 0.5f, 1.8f, sz * 0.5f, sx * 2.0f, 1.3f, sz * 2.0f, sx * 2.8f, 0f, sz * 2.8f}, 0.07f, 0.05f, barkC, 5);
        }
        limb(p, new float[]{0, 1.2f, 0, 0, 3f, 0}, 0.2f, 0.15f, barkC, 6);
        for (int k = 0; k < 10; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * 2.4f;
            blob(p, (float) Math.sin(a) * d, 3.2f + r.nextFloat() * 1.3f, (float) Math.cos(a) * d, 1.3f, 0.9f, 1.3f, leaves(0x2E5A2A));
        }
    }

    private static void shrub(List<Part> p, Random r, float h, float rad, int n, int flower, float frac, int leaf) {
        Lathe.ColorFn c = flowers(leaf, flower, frac);
        for (int k = 0; k < n; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * rad * 0.7f;
            float y = 0.4f + r.nextFloat() * (h - 0.8f);
            float s = 0.5f + r.nextFloat() * 0.35f;
            blob(p, (float) Math.sin(a) * d, y, (float) Math.cos(a) * d, rad * s, h * 0.35f, rad * s, c);
        }
    }

    private static void croton(List<Part> p, Random r) {
        int[] cols = {0xE0C030, 0xC04020, 0x3A6A2A, 0xE07A2A};
        for (int k = 0; k < 7; k++) {
            final int c = cols[k % cols.length];
            float a = r.nextFloat() * 6.28f, d = r.nextFloat() * 0.4f;
            blob(p, (float) Math.sin(a) * d, 0.4f + r.nextFloat() * 0.6f, (float) Math.cos(a) * d, 0.35f, 0.35f, 0.35f, leaves(c));
        }
    }

    private static void birdOfParadise(List<Part> p, Random r) {
        for (int k = 0; k < 10; k++) {
            float yaw = k * 0.63f + r.nextFloat() * 0.3f;
            frond(p, 0, 0.1f, 0, yaw, 1.3f - r.nextFloat() * 0.25f, 0.25f, 0.65f,
                    new float[]{0.03f, 0.04f, 0.2f, 0.02f}, leaves(0x4A7A5A), false);
        }
        for (int k = 0; k < 3; k++) {
            float a = k * 2.1f;
            parts(p, Lathe.ellipsoid(0.08f, 0.06f, 0.2f, 4, 6).color(k == 1 ? 0x3050C0 : 0xF08020).emissive()
                    .transform(Mat4.mul(Mat4.translation((float) Math.sin(a) * 0.3f, 1.2f, (float) Math.cos(a) * 0.3f), Mat4.rotY(a))));
        }
    }

    private static void seaOats(List<Part> p, Random r) {
        for (int k = 0; k < 16; k++) {
            float yaw = r.nextFloat() * 6.28f;
            float ox = (r.nextFloat() - 0.5f) * 0.8f, oz = (r.nextFloat() - 0.5f) * 0.8f;
            frond(p, ox, 0, oz, yaw, 1.35f, 0.2f, 0.45f, new float[]{0.03f, 0.03f, 0.02f, 0.01f},
                    (x, y, z) -> y > 0.9f ? 0xD8C890 : 0x9AA060, false);
        }
    }

    private static void agave(List<Part> p, Random r) {
        for (int k = 0; k < 15; k++) {
            float yaw = k * 0.42f + r.nextFloat() * 0.2f;
            frond(p, 0, 0.1f, 0, yaw, 0.35f + (k % 3) * 0.4f, -0.1f, 0.55f, new float[]{0.14f, 0.12f, 0.0f},
                    leaves(0x7A9A8A), false);
        }
    }
}
