package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Japanese trees and shrubs, each modelled procedurally in 3D and baked (straight-on, no tilt) to
 * an 8-direction sprite sheet. Frame sizes and pixel densities are per species.
 */
public enum Flora {
    //            variants  frameW frameH anchorX anchorY ppm   tree   trunkRadius
    SAKURA(2, 208, 150, 104, 146, 14f, true, 0.35f),
    GINKGO(1, 128, 200, 64, 196, 14f, true, 0.3f),
    ZELKOVA(1, 192, 184, 96, 180, 13f, true, 0.35f),
    BLACK_PINE(2, 176, 150, 88, 146, 16f, true, 0.3f),
    MAPLE(2, 128, 112, 64, 108, 18f, true, 0.2f),
    BAMBOO(1, 112, 224, 56, 220, 16f, false, 0f),
    CEDAR(1, 112, 256, 56, 252, 12f, true, 0.4f),
    CAMPHOR(1, 224, 176, 112, 172, 12f, true, 0.6f),
    AZALEA(2, 80, 48, 40, 44, 24f, false, 0f),
    HYDRANGEA(2, 72, 56, 36, 52, 24f, false, 0f),
    BOXWOOD(1, 56, 48, 28, 44, 24f, false, 0f),
    CAMELLIA(1, 72, 72, 36, 68, 24f, false, 0f);

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
            case SAKURA: sakura(p, r, v); break;
            case GINKGO: ginkgo(p, r); break;
            case ZELKOVA: zelkova(p, r); break;
            case BLACK_PINE: blackPine(p, r, v); break;
            case MAPLE: maple(p, r, v); break;
            case BAMBOO: bamboo(p, r); break;
            case CEDAR: cedar(p, r); break;
            case CAMPHOR: camphor(p, r); break;
            case AZALEA: shrub(p, r, 1.0f, 1.1f, 7, v == 0 ? 0xF06A9A : 0xF4F0F2, 0.55f, 0x3A6A2A); break;
            case HYDRANGEA: shrub(p, r, 1.3f, 0.9f, 7, v == 0 ? 0x6A8AE0 : 0xA070D0, 0.4f, 0x3E7230); break;
            case BOXWOOD: boxwood(p, r); break;
            case CAMELLIA: shrub(p, r, 2.2f, 1.0f, 8, 0xD02838, 0.12f, 0x24502A); break;
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

    // ------------------------------------------------------------------ trees

    private static void spread(List<Part> p, Random r, float trunkH, float r0, int branches, float reach, float top,
                               Lathe.ColorFn barkC) {
        limb(p, new float[]{0, 0, 0, 0.15f, trunkH, 0}, r0, r0 * 0.75f, barkC, 8);
        for (int b = 0; b < branches; b++) {
            float a = b * 6.283f / branches + r.nextFloat() * 0.5f, len = reach * (0.7f + r.nextFloat() * 0.3f);
            limb(p, new float[]{0.15f, trunkH - 0.2f, 0, (float) Math.sin(a) * len * 0.5f, trunkH + (top - trunkH) * 0.5f,
                    (float) Math.cos(a) * len * 0.5f, (float) Math.sin(a) * len, top, (float) Math.cos(a) * len}, r0 * 0.5f, r0 * 0.15f, barkC, 6);
        }
    }

    private static void canopy(List<Part> p, Random r, int n, float radius, float y0, float y1, float blobR, float blobH,
                               Lathe.ColorFn c) {
        for (int k = 0; k < n; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * radius;
            blob(p, (float) Math.sin(a) * d, y0 + r.nextFloat() * (y1 - y0) - d * 0.12f, (float) Math.cos(a) * d,
                    blobR * (0.8f + r.nextFloat() * 0.4f), blobH * (0.8f + r.nextFloat() * 0.4f), blobR * (0.8f + r.nextFloat() * 0.4f), c);
        }
    }

    private static void sakura(List<Part> p, Random r, int v) {
        float s = v == 0 ? 1f : 0.8f;
        spread(p, r, 2.2f * s, 0.35f * s, 6, 4.5f * s, 4.6f * s, bark(0x4A3E3A, 0.25f));
        canopy(p, r, 22, 5f * s, 4.4f * s, 6.6f * s, 1.7f * s, 1.2f * s, flowers(0xF6C4D2, 0xFCE8EE, 0.35f));
    }

    private static void ginkgo(List<Part> p, Random r) {
        limb(p, new float[]{0, 0, 0, 0.1f, 9f, 0}, 0.32f, 0.12f, bark(0x6A5E52, 0.4f), 8);
        for (int k = 0; k < 16; k++) {
            float t = k / 16f, y = 2.8f + t * 9.5f, rad = 2.6f * (1f - t * 0.75f);
            float a = r.nextFloat() * 6.28f;
            blob(p, (float) Math.sin(a) * rad * 0.4f, y, (float) Math.cos(a) * rad * 0.4f, rad, 1.3f, rad,
                    flowers(0x7AAA3A, 0xC8D050, 0.25f));
        }
    }

    private static void zelkova(List<Part> p, Random r) {
        Lathe.ColorFn barkC = bark(0x7A746A, 0.5f);
        limb(p, new float[]{0, 0, 0, 0, 2.6f, 0}, 0.38f, 0.3f, barkC, 8);
        for (int b = 0; b < 7; b++) {   // vase shape: branches rise steeply then spread
            float a = b * 0.9f + r.nextFloat() * 0.4f;
            limb(p, new float[]{0, 2.5f, 0, (float) Math.sin(a) * 1.5f, 6f, (float) Math.cos(a) * 1.5f,
                    (float) Math.sin(a) * 4.5f, 10f + r.nextFloat(), (float) Math.cos(a) * 4.5f}, 0.16f, 0.05f, barkC, 5);
        }
        canopy(p, r, 20, 5.5f, 8.5f, 12f, 2.0f, 1.4f, leaves(0x4E7E34));
    }

    private static void blackPine(List<Part> p, Random r, int v) {
        // leaning, twisting trunk with cloud-pruned pads of needles
        float lean = v == 0 ? 2.2f : 1.0f;
        Lathe.ColorFn barkC = (x, y, z) -> (((int) (y * 5 + x * 3)) & 1) == 0 ? 0x5A4A40 : 0x46382E;
        float[] pts = {0, 0, 0, lean * 0.2f, 2f, 0.3f, lean * 0.6f, 4f, -0.2f, lean, 6f, 0.2f, lean * 1.1f, 7.5f, 0f};
        limb(p, pts, 0.34f, 0.12f, barkC, 7);
        float[][] pads = {{lean * 0.3f, 3.2f, 1.4f}, {lean * 0.7f, 4.6f, 1.6f}, {lean, 6.0f, 1.8f}, {lean * 1.1f, 7.4f, 1.5f}};
        for (float[] pd : pads) {
            float a = r.nextFloat() * 6.28f;
            float ox = (float) Math.sin(a) * 1.4f, oz = (float) Math.cos(a) * 1.4f;
            limb(p, new float[]{pd[0], pd[1] - 0.3f, 0, pd[0] + ox, pd[1], oz}, 0.08f, 0.05f, barkC, 5);
            for (int k = 0; k < 3; k++)
                blob(p, pd[0] + ox + (r.nextFloat() - 0.5f) * 1.2f, pd[1] + 0.2f, oz + (r.nextFloat() - 0.5f) * 1.2f,
                        pd[2], 0.45f, pd[2], leaves(0x2E4A2A));
        }
    }

    private static void maple(List<Part> p, Random r, int v) {
        spread(p, r, 1.6f, 0.2f, 5, 2.4f, 3.2f, bark(0x5A4E46, 0.3f));
        int c = v == 0 ? 0xC83A2A : 0x5E8E34;
        canopy(p, r, 14, 2.6f, 3.0f, 4.6f, 1.2f, 0.8f, flowers(c, v == 0 ? 0xE8782A : 0x88B040, 0.35f));
    }

    private static void bamboo(List<Part> p, Random r) {
        for (int k = 0; k < 14; k++) {
            float ox = (r.nextFloat() - 0.5f) * 2.4f, oz = (r.nextFloat() - 0.5f) * 2.4f;
            float h = 9f + r.nextFloat() * 4f, lean = (r.nextFloat() - 0.5f) * 1.6f;
            limb(p, new float[]{ox, 0, oz, ox + lean * 0.3f, h * 0.5f, oz, ox + lean, h, oz},
                    0.07f, 0.04f, (x, y, z) -> ((int) (y / 0.5f) & 1) == 0 ? 0x6E9A40 : 0x5E8836, 5);
            for (int j = 0; j < 3; j++)
                blob(p, ox + lean * (0.6f + j * 0.15f), h * (0.62f + j * 0.13f), oz, 0.8f, 0.7f, 0.8f, leaves(0x7AA848));
        }
    }

    private static void cedar(List<Part> p, Random r) {
        limb(p, new float[]{0, 0, 0, 0, 18f, 0}, 0.42f, 0.1f, bark(0x7A4E36, 0.6f), 8);
        for (int k = 0; k < 12; k++) {
            float t = k / 12f, y = 4f + t * 15f, rad = 2.6f * (1f - t * 0.85f) + 0.3f;
            blob(p, 0, y, 0, rad, 1.3f, rad, leaves(0x2A4A2E));
        }
    }

    private static void camphor(List<Part> p, Random r) {
        spread(p, r, 3f, 0.65f, 6, 6f, 7f, bark(0x5A5048, 0.5f));
        canopy(p, r, 26, 7f, 7f, 11.5f, 2.4f, 1.8f, leaves(0x4E8A36));
    }

    private static void boxwood(List<Part> p, Random r) {
        parts(p, Lathe.ellipsoid(0.75f, 0.6f, 0.75f, 6, 10).color(leaves(0x2E5A2A)).foliage().transform(Mat4.translation(0, 0.65f, 0)));
        parts(p, Lathe.ellipsoid(0.6f, 0.5f, 0.6f, 6, 10).color(leaves(0x3A6A30)).foliage().transform(Mat4.translation(0, 0.95f, 0)));
    }

    private static void parts(List<Part> p, Lathe l) { p.add(l.build(0)); }

    private static void shrub(List<Part> p, Random r, float h, float rad, int n, int flower, float frac, int leaf) {
        Lathe.ColorFn c = flowers(leaf, flower, frac);
        for (int k = 0; k < n; k++) {
            float a = r.nextFloat() * 6.28f, d = (float) Math.sqrt(r.nextFloat()) * rad * 0.7f;
            float y = 0.3f + r.nextFloat() * (h - 0.6f);
            float s = 0.5f + r.nextFloat() * 0.35f;
            blob(p, (float) Math.sin(a) * d, y, (float) Math.cos(a) * d, rad * s, h * 0.38f, rad * s, c);
        }
    }
}
