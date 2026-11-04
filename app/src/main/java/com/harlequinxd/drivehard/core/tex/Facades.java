package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/**
 * Procedural building facade textures. Each texture is one bay (2 m wide, 64 px) by one
 * storey (128 px), so walls repeat it across bays and floors.
 */
public final class Facades {
    private Facades() {}

    public enum Kind { DECO, RESIDENTIAL, HOTEL, OFFICE, COMMERCIAL, GARAGE, INDUSTRIAL, BLANK }

    public static final int[] PASTELS = {0xF4B6C2, 0xF8C8A0, 0xA8E6CF, 0x8FD8D2, 0xC8B6E2, 0xF6E7A1, 0xF2F0EA,
            0xF5E6CC, 0xF49A80, 0x9CCBEB, 0xFFD4E5, 0xB5EAD7, 0xFFDAC1, 0xE2F0CB, 0xC7CEEA, 0xFCE1A8};
    public static final int[] NEUTRALS = {0xE8E4DA, 0xD9D4C7, 0xC9C5BC, 0xBDB8AE, 0xEDEAE3, 0xD6CFC0, 0xB8B2A6,
            0xCFC8BA, 0xA8A39A, 0xE0D8C8};
    public static final int[] BRICKS = {0x9C4A34, 0xA85A3C, 0x8A3E2E, 0xB0704C, 0x7A4A3A, 0xC08868};
    public static final int[] TRIMS = {0xFFFFFF, 0xF2F0EA, 0x2E7D7A, 0xE86A8A, 0x4A90B8, 0xF0C060, 0x3A3A44, 0x8C6A4A};
    public static final int[] GLASS = {0x5A8FB8, 0x4A7C9C, 0x3F6E7E, 0x6FA8C8, 0x2F5A70, 0x4E8A84, 0x7A8E9A, 0x3A4E66,
            0x8AA6B0, 0x5E7A60};

    static int pick(Random r, int[] a) { return 0xFF000000 | a[r.nextInt(a.length)]; }

    // ------------------------------------------------------------------ walls

    static void wallMaterial(Img m, Random r, int base, int mat, int seed) {
        m.fill(base);
        switch (mat) {
            case 0: // stucco
                m.grain(0, 0, m.w, m.h, 0.05f, 0.035f, seed);
                break;
            case 1: // concrete with form lines and rain stains
                m.grain(0, 0, m.w, m.h, 0.08f, 0.05f, seed);
                for (int x = 0; x < m.w; x++) {
                    float s = Noise.value(x, 0, m.w, 1, 8, 1, seed + 3);
                    if (s > 0.6f) for (int y = 0; y < m.h; y++)
                        m.px[y * m.w + x] = mul(m.px[y * m.w + x], 1f - (s - 0.6f) * 0.25f * (y / (float) m.h));
                }
                m.shade(0, m.h / 2, m.w, m.h / 2 + 1, 0.88f);
                break;
            case 2: { // brick
                int mortar = mix(base, 0xFFD8D0C4, 0.7f);
                for (int y = 0; y < m.h; y++) {
                    int row = y / 4;
                    for (int x = 0; x < m.w; x++) {
                        int bx = (x + (row & 1) * 5) % 10;
                        boolean mt = (y % 4) == 3 || bx == 9;
                        int c = mt ? mortar : mul(base, 0.85f + Noise.rnd((x + (row & 1) * 5) / 10, row, seed) * 0.3f);
                        m.px[y * m.w + x] = c;
                    }
                }
                m.grain(0, 0, m.w, m.h, 0.03f, 0.04f, seed);
                break;
            }
            case 3: { // tile / panel grid
                int grout = mul(base, 0.82f);
                for (int y = 0; y < m.h; y++)
                    for (int x = 0; x < m.w; x++)
                        if (x % 16 == 0 || y % 16 == 0) m.px[y * m.w + x] = grout;
                m.grain(0, 0, m.w, m.h, 0.03f, 0.03f, seed);
                break;
            }
            default: { // metal panel
                for (int y = 0; y < m.h; y++)
                    for (int x = 0; x < m.w; x++)
                        if (x % 32 == 0 || y % 64 == 0) m.px[y * m.w + x] = mul(base, 0.75f);
                m.grain(0, 0, m.w, m.h, 0.04f, 0.02f, seed);
                break;
            }
        }
    }

    /** Glass with sky reflection, interior variation and frame. */
    static void glass(Img m, Random r, int x0, int y0, int x1, int y1, int glass, int frame, int ft, boolean interior) {
        int h = y1 - y0;
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) Math.max(1, h);
            int c = mix(mix(glass, 0xFFE8F4FF, 0.45f), mul(glass, 0.55f), t);
            for (int x = x0; x < x1; x++) {
                int cc = c;
                // diagonal reflection streak
                int d = (x - x0) + (y - y0) / 2;
                if ((d % 23) < 3) cc = mix(cc, 0xFFFFFFFF, 0.25f);
                m.set(x, y, cc);
            }
        }
        if (interior) {
            int k = r.nextInt(4);
            if (k == 0) { // blinds
                int bl = mix(0xFFF0EEE8, glass, 0.35f);
                int by = y0 + 2 + r.nextInt(Math.max(1, h / 2));
                for (int y = y0; y < by; y++) if ((y & 1) == 0) for (int x = x0; x < x1; x++) m.set(x, y, bl);
            } else if (k == 1) { // curtain on one side
                int cu = pick(r, new int[]{0xE8D8B0, 0xC85A5A, 0x5A7AB0, 0xE8E8E0, 0x6A9A6A, 0xD8A0C0});
                int cw = (x1 - x0) / 3;
                boolean left = r.nextBoolean();
                for (int y = y0; y < y1; y++)
                    for (int x = 0; x < cw; x++) {
                        int px = left ? x0 + x : x1 - 1 - x;
                        m.set(px, y, mul(cu, 0.75f + 0.25f * (((x + (left ? 0 : 1)) & 2) >> 1)));
                    }
            } else if (k == 2) { // dark room
                m.tint(x0, y0 + h / 3, x1, y1, 0xFF1A2028, 0.35f);
            }
        }
        if (ft > 0) m.frame(x0 - ft, y0 - ft, x1 + ft, y1 + ft, ft, frame);
    }

    static void sill(Img m, int x0, int x1, int y, int c) {
        m.rect(x0 - 1, y, x1 + 1, y + 2, c);
        m.shade(x0 - 1, y + 2, x1 + 1, y + 4, 0.8f);
    }

    /** Horizontal floor band (slab edge / ledge) at the bottom and soffit shadow at the top. */
    static void floorBand(Img m, int c, int height) {
        m.rect(0, m.h - height, m.w, m.h, c);
        m.shade(0, m.h - height, m.w, m.h - height + 1, 1.12f);
        m.shade(0, 0, m.w, 2, 0.82f);
    }

    // ------------------------------------------------------------------ styles

    public static Texture generate(Random r, Kind kind, int seed) {
        Img m = new Img(64, 128);
        switch (kind) {
            case DECO: deco(m, r, seed); break;
            case RESIDENTIAL: residential(m, r, seed); break;
            case HOTEL: hotel(m, r, seed); break;
            case OFFICE: office(m, r, seed); break;
            case COMMERCIAL: commercial(m, r, seed); break;
            case GARAGE: garage(m, r, seed); break;
            case INDUSTRIAL: industrial(m, r, seed); break;
            default: blank(m, r, seed); break;
        }
        return m.texture();
    }

    static void deco(Img m, Random r, int seed) {
        int base = pick(r, PASTELS);
        int trim = r.nextInt(3) == 0 ? pick(r, TRIMS) : 0xFFF8F6F0;
        int glass = pick(r, GLASS);
        wallMaterial(m, r, base, 0, seed);
        int style = r.nextInt(4);
        if (style == 0) { // ribbon window with eyebrow
            int wy0 = 40 + r.nextInt(12), wy1 = wy0 + 34 + r.nextInt(16);
            glass(m, r, 0, wy0, 64, wy1, glass, trim, 0, true);
            for (int x = 0; x < 64; x += 16) m.rect(x, wy0, x + 2, wy1, trim);
            m.rect(0, wy0 - 2, 64, wy0, trim);
            m.rect(0, wy1, 64, wy1 + 2, trim);
            eyebrow(m, wy0 - 8, trim);
        } else if (style == 1) { // porthole + eyebrow
            glass(m, r, 18, 46, 46, 96, glass, trim, 2, true);
            eyebrow(m, 36, trim);
            m.disc(54, 70, 5, trim);
            m.disc(54, 70, 3.5f, mix(glass, 0xFFFFFFFF, 0.3f));
        } else if (style == 2) { // corner-ish paired windows with vertical fins
            glass(m, r, 6, 40, 28, 100, glass, trim, 2, true);
            glass(m, r, 36, 40, 58, 100, glass, trim, 2, true);
            m.rect(30, 20, 34, 128, mul(trim, 0.95f));
            m.shade(34, 20, 36, 128, 0.85f);
            eyebrow(m, 30, trim);
        } else { // single tall window with decorative bands
            glass(m, r, 16, 34, 48, 104, glass, trim, 3, true);
            for (int i = 0; i < 3; i++) m.rect(0, 112 + i * 4, 64, 114 + i * 4, mul(trim, 0.92f));
            sill(m, 16, 48, 107, trim);
        }
        floorBand(m, mul(base, 1.05f), 4);
    }

    static void eyebrow(Img m, int y, int trim) {
        m.rect(0, y, 64, y + 4, trim);
        m.shade(0, y + 3, 64, y + 4, 0.85f);
        for (int i = 0; i < 6; i++) m.shade(0, y + 4 + i, 64, y + 5 + i, 0.70f + i * 0.05f);
    }

    static void residential(Img m, Random r, int seed) {
        int base = r.nextInt(3) == 0 ? pick(r, NEUTRALS) : pick(r, PASTELS);
        int trim = r.nextBoolean() ? 0xFFF4F2EC : pick(r, TRIMS);
        int glass = pick(r, GLASS);
        wallMaterial(m, r, base, r.nextInt(4) == 0 ? 3 : 0, seed);
        int style = r.nextInt(3);
        if (style == 0) { // sliding door + balcony
            int bx0 = 6 + r.nextInt(6), bx1 = 58 - r.nextInt(6);
            glass(m, r, bx0 + 4, 26, bx1 - 4, 112, glass, 0xFFE0E0DC, 2, true);
            m.rect(bx0 + 4 + (bx1 - bx0 - 8) / 2 - 1, 26, bx0 + 4 + (bx1 - bx0 - 8) / 2 + 1, 112, 0xFFE0E0DC);
            int slab = mul(base, 1.08f);
            m.rect(0, 112, 64, 120, slab);
            m.shade(0, 119, 64, 120, 0.7f);
            int rail = r.nextBoolean() ? 0xFFF8F8F8 : 0xFF2A2A30;
            boolean glassRail = r.nextBoolean();
            if (glassRail) {
                m.tint(0, 88, 64, 112, 0xFFB8D8E8, 0.45f);
                m.rect(0, 86, 64, 88, rail);
            } else {
                m.rect(0, 86, 64, 88, rail);
                for (int x = 1; x < 64; x += 4) m.rect(x, 88, x + 1, 112, rail);
            }
        } else if (style == 1) { // two punched windows, maybe AC unit
            int ww = 16 + r.nextInt(6), wy0 = 34 + r.nextInt(8), wh = 46 + r.nextInt(16);
            int[] xs = {16 - ww / 2, 48 - ww / 2};
            for (int x : xs) {
                glass(m, r, x, wy0, x + ww, wy0 + wh, glass, trim, 2, true);
                sill(m, x, x + ww, wy0 + wh + 2, trim);
                if (r.nextInt(4) == 0) { // window AC
                    int ay = wy0 + wh - 12;
                    m.rect(x + 2, ay, x + ww - 2, ay + 10, 0xFFD8D8D0);
                    for (int y = ay + 2; y < ay + 9; y += 2) m.rect(x + 3, y, x + ww - 3, y + 1, 0xFF9A9A94);
                }
            }
        } else { // wide window with shutters
            int wx0 = 14, wx1 = 50, wy0 = 36, wy1 = 96;
            glass(m, r, wx0, wy0, wx1, wy1, glass, trim, 2, true);
            int sh = pick(r, new int[]{0x2E7D7A, 0x4A90B8, 0xF2F0EA, 0x6A4A3A, 0xE86A8A});
            for (int y = wy0 - 2; y < wy1 + 2; y++) {
                int c = (y & 1) == 0 ? sh : mul(sh, 0.8f);
                m.rect(wx0 - 11, y, wx0 - 2, y + 1, c);
                m.rect(wx1 + 2, y, wx1 + 11, y + 1, c);
            }
            sill(m, wx0, wx1, wy1 + 2, trim);
        }
        floorBand(m, mul(base, 0.96f), 3);
    }

    static void hotel(Img m, Random r, int seed) {
        int base = r.nextBoolean() ? 0xFFF4F2EC : pick(r, PASTELS);
        int glass = pick(r, GLASS);
        wallMaterial(m, r, base, 0, seed);
        int fin = r.nextBoolean() ? 0xFFFFFFFF : pick(r, TRIMS);
        glass(m, r, 8, 24, 56, 106, glass, 0xFFDADAD6, 2, true);
        m.rect(31, 24, 33, 106, 0xFFDADAD6);
        m.rect(0, 0, 6, 128, fin);
        m.rect(58, 0, 64, 128, fin);
        m.shade(5, 0, 6, 128, 0.85f);
        // balcony rail
        m.rect(6, 88, 58, 90, 0xFFF0F0F0);
        m.tint(6, 90, 58, 106, 0xFFC8E4F0, 0.4f);
        floorBand(m, mul(base, 1.05f), 6);
    }

    static void office(Img m, Random r, int seed) {
        int glass = pick(r, GLASS);
        int mull = r.nextBoolean() ? 0xFF2A2E34 : 0xFFC8CCD0;
        int spand = r.nextInt(3) == 0 ? mul(glass, 0.6f) : (r.nextBoolean() ? 0xFF3A4048 : 0xFFD8D8D4);
        int split = 96 + r.nextInt(10);
        glass(m, r, 0, 0, 64, split, glass, mull, 0, r.nextInt(3) == 0);
        // spandrel
        m.rect(0, split, 64, 128, spand);
        m.grain(0, split, 64, 128, 0.03f, 0.02f, seed);
        m.rect(0, split, 64, split + 2, mull);
        m.rect(0, 126, 64, 128, mull);
        int cols = r.nextBoolean() ? 2 : 4;
        for (int i = 0; i < cols; i++) {
            int x = i * 64 / cols;
            m.rect(x, 0, x + 2, 128, mull);
        }
        // occasional lit/dark panes for variety
        if (r.nextBoolean()) {
            int pane = r.nextInt(cols);
            m.tint(pane * 64 / cols + 2, 0, (pane + 1) * 64 / cols, split, 0xFF101820, 0.3f);
        }
    }

    static void commercial(Img m, Random r, int seed) {
        boolean brick = r.nextInt(3) == 0;
        int base = brick ? pick(r, BRICKS) : pick(r, NEUTRALS);
        int glass = pick(r, GLASS);
        int trim = brick ? 0xFFE8E0D0 : pick(r, TRIMS);
        wallMaterial(m, r, base, brick ? 2 : (r.nextBoolean() ? 1 : 0), seed);
        int n = r.nextBoolean() ? 1 : 2;
        int wy0 = 30 + r.nextInt(8), wy1 = 92 + r.nextInt(10);
        if (n == 1) {
            glass(m, r, 12, wy0, 52, wy1, glass, trim, 3, true);
            m.rect(31, wy0, 33, wy1, trim);
            m.rect(12, (wy0 + wy1) / 2 - 1, 52, (wy0 + wy1) / 2 + 1, trim);
            sill(m, 12, 52, wy1 + 3, trim);
        } else {
            glass(m, r, 7, wy0, 27, wy1, glass, trim, 2, true);
            glass(m, r, 37, wy0, 57, wy1, glass, trim, 2, true);
            sill(m, 7, 27, wy1 + 2, trim);
            sill(m, 37, 57, wy1 + 2, trim);
        }
        if (brick) m.rect(0, 0, 64, 6, mul(trim, 0.9f));
        floorBand(m, brick ? trim : mul(base, 1.06f), 4);
    }

    static void garage(Img m, Random r, int seed) {
        int base = pick(r, NEUTRALS);
        wallMaterial(m, r, base, 1, seed);
        int y0 = 46 + r.nextInt(10), y1 = 104 + r.nextInt(8);
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) (y1 - y0);
            int c = mix(0xFF101214, 0xFF2A2C30, t);
            for (int x = 0; x < 64; x++) m.set(x, y, c);
        }
        // columns, ceiling lights and a parked car silhouette in the gloom
        m.rect(0, y0, 5, y1, base);
        m.shade(5, y0, 6, y1, 0.7f);
        m.rect(28, y0 + 2, 36, y0 + 3, 0xFFE8E8D8);
        if (r.nextBoolean()) {
            int cc = mul(pick(r, new int[]{0xC03030, 0x3050A0, 0xE0E0E0, 0x202020, 0x808890}), 0.45f);
            m.rect(14, y1 - 14, 50, y1 - 4, cc);
            m.rect(20, y1 - 20, 44, y1 - 14, cc);
        }
        // rail / barrier cable
        m.rect(0, y0 + (y1 - y0) / 2, 64, y0 + (y1 - y0) / 2 + 1, 0xFF8A8A84);
        m.shade(0, y0 - 2, 64, y0, 0.75f);
    }

    static void industrial(Img m, Random r, int seed) {
        boolean metal = r.nextBoolean();
        int base = metal ? pick(r, new int[]{0xB8BCC0, 0xA8B0B4, 0xD8D0C0, 0x9AA8A0, 0xC8C0B0}) : pick(r, NEUTRALS);
        wallMaterial(m, r, base, metal ? 4 : 1, seed);
        if (r.nextBoolean()) {
            int glass = pick(r, GLASS);
            glass(m, r, 8, 20, 56, 44, mul(glass, 0.8f), 0xFF505458, 2, false);
            for (int x = 8; x < 56; x += 8) m.rect(x, 20, x + 1, 44, 0xFF505458);
        }
        // vertical corrugation shading
        if (metal) for (int x = 0; x < 64; x += 4) m.shade(x, 0, x + 1, 128, 0.88f);
    }

    static void blank(Img m, Random r, int seed) {
        int k = r.nextInt(4);
        int base = k == 2 ? pick(r, BRICKS) : (k == 3 ? pick(r, PASTELS) : pick(r, NEUTRALS));
        wallMaterial(m, r, base, k == 2 ? 2 : (k == 1 ? 1 : (r.nextBoolean() ? 0 : 3)), seed);
        if (k == 1) { // tie holes
            for (int y = 16; y < 128; y += 32) for (int x = 8; x < 64; x += 16) m.shade(x, y, x + 2, y + 2, 0.55f);
        }
        if (r.nextInt(4) == 0) { // vent or pipe
            int x = 8 + r.nextInt(40);
            m.rect(x, 0, x + 3, 128, mul(base, 0.8f));
            m.shade(x + 3, 0, x + 4, 128, 0.7f);
        }
    }
}
