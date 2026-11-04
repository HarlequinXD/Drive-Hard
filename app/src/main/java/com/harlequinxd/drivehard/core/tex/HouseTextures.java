package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/**
 * Textures for the house generator, kept separate so walls can be built around real openings:
 * plain wall materials (no windows: they tile across walls and gable ends), windows and doors
 * (each mapped exactly onto its opening), and roof coverings.
 */
public final class HouseTextures {
    private HouseTextures() { }

    public enum Material { SIDING, STUCCO, BOARDS, TILE, BRICK, PANEL, PLASTER, CHARRED }

    /** Wall material, 64x64, tiles every 2 m. */
    public static Texture material(Material k, int color, int seed) {
        Img m = new Img(64, 64);
        Random r = new Random(seed * 37L + k.ordinal());
        m.fill(0xFF000000 | color);
        switch (k) {
            case SIDING:          // horizontal lap siding, a shadow line under every board
                m.grain(0, 0, 64, 64, 0.03f, 0.025f, seed);
                for (int y = 0; y < 64; y += 6) { m.shade(0, y, 64, y + 1, 0.78f); m.shade(0, y + 1, 64, y + 2, 1.06f); }
                for (int y = 0; y < 64; y += 6) { int x = r.nextInt(64); m.shade(x, y, x + 1, y + 6, 0.9f); }
                break;
            case STUCCO:          // sprayed plaster
                m.grain(0, 0, 64, 64, 0.05f, 0.06f, seed);
                break;
            case BOARDS:          // vertical boards with battens
                m.grain(0, 0, 64, 64, 0.07f, 0.05f, seed);
                for (int x = 0; x < 64; x += 8) { m.shade(x, 0, x + 1, 64, 0.7f); m.shade(x + 1, 0, x + 2, 64, 1.1f); }
                break;
            case TILE:            // small ceramic facade tiles
                m.grain(0, 0, 64, 64, 0.02f, 0.03f, seed);
                for (int y = 0; y < 64; y += 4) m.shade(0, y, 64, y + 1, 0.86f);
                for (int y = 0; y < 64; y += 4) for (int x = (y / 4 % 2) * 4; x < 64; x += 8) m.shade(x, y, x + 1, y + 4, 0.86f);
                break;
            case BRICK:           // brick-look siding
                m.grain(0, 0, 64, 64, 0.06f, 0.05f, seed);
                for (int y = 0; y < 64; y += 5) {
                    m.rect(0, y, 64, y + 1, mix(0xFFD8D0C4, 0xFF000000 | color, 0.5f));
                    for (int x = (y / 5 % 2) * 6; x < 64; x += 12) m.rect(x, y, x + 1, y + 5, mix(0xFFD8D0C4, 0xFF000000 | color, 0.5f));
                    for (int x = 0; x < 64; x += 12) m.shade(x + 1, y + 1, x + 12, y + 5, 0.9f + r.nextFloat() * 0.2f);
                }
                break;
            case PANEL:           // fibre-cement panels with joints
                m.grain(0, 0, 64, 64, 0.03f, 0.03f, seed);
                m.shade(0, 0, 64, 1, 0.75f);
                m.shade(0, 0, 1, 64, 0.75f);
                m.shade(32, 0, 33, 64, 0.8f);
                break;
            case PLASTER:         // traditional white plaster
                m.grain(0, 0, 64, 64, 0.04f, 0.03f, seed);
                break;
            default:              // charred cedar boards (yakisugi)
                m.grain(0, 0, 64, 64, 0.1f, 0.08f, seed);
                for (int x = 0; x < 64; x += 6) m.shade(x, 0, x + 1, 64, 0.6f);
                break;
        }
        return m.texture();
    }

    public enum Window { SLIDING, SHUTTER_BOX, FROSTED, LATTICE, SHOJI, SLIT, CASEMENT, GARDEN }

    /** A window filling its opening: frame, glass, mullions, curtains (32x32, or 32x64 tall ones). */
    public static Texture window(Window k, int frame, int seed) {
        boolean tall = k == Window.GARDEN || k == Window.SLIT;
        Img m = new Img(32, tall ? 64 : 32);
        Random r = new Random(seed * 53L + k.ordinal());
        int w = m.w, h = m.h;
        int glass = mix(0xFF6E8698, 0xFF2E3A46, r.nextFloat() * 0.5f);
        int f = 0xFF000000 | frame;
        m.fill(f);
        switch (k) {
            case FROSTED:
                m.rect(2, 2, w - 2, h - 2, 0xFFC4CED4);
                m.grain(2, 2, w - 2, h - 2, 0.02f, 0.04f, seed);
                m.rect(w / 2 - 1, 2, w / 2 + 1, h - 2, f);
                break;
            case LATTICE:         // wooden grille (koshi) over dark glass
                m.rect(2, 2, w - 2, h - 2, 0xFF2A3036);
                for (int x = 3; x < w - 2; x += 3) m.rect(x, 2, x + 1, h - 2, mul(f, 1.2f));
                break;
            case SHOJI:           // paper screens behind glass
                m.rect(2, 2, w - 2, h - 2, 0xFFE8E4D8);
                for (int x = 2; x < w - 2; x += 7) m.rect(x, 2, x + 1, h - 2, mul(f, 0.9f));
                for (int y = 2; y < h - 2; y += 9) m.rect(2, y, w - 2, y + 1, mul(f, 0.9f));
                break;
            case SHUTTER_BOX:     // sliding window under a storm-shutter box
                m.rect(0, 0, w, 7, mul(f, 1.1f));
                m.shade(0, 6, w, 7, 0.7f);
                glass(m, 2, 9, w - 2, h - 2, glass, f, r);
                break;
            case CASEMENT:
                glass(m, 2, 2, w - 2, h - 2, glass, f, r);
                m.rect(2, h / 2 - 1, w - 2, h / 2, f);
                break;
            default:              // SLIDING, SLIT, GARDEN
                glass(m, 2, 2, w - 2, h - 2, glass, f, r);
                break;
        }
        if (k == Window.GARDEN) { m.rect(0, h - 3, w, h, mul(f, 0.8f)); }
        return m.texture();
    }

    private static void glass(Img m, int x0, int y0, int x1, int y1, int glass, int frame, Random r) {
        m.rect(x0, y0, x1, y1, glass);
        // sky reflection streak and curtains
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((x - y + 40) % 23 < 3) m.px[y * m.w + x] = mix(0xFFDCE6EE, m.px[y * m.w + x], 0.6f);
        if (r.nextBoolean()) {
            int cur = mix(0xFFE8E0D0, 0xFF000000 | r.nextInt(0xFFFFFF), 0.25f);
            m.rect(x0, y0, x0 + (x1 - x0) / 4, y1, cur);
            m.rect(x1 - (x1 - x0) / 4, y0, x1, y1, cur);
        }
        m.rect((x0 + x1) / 2 - 1, y0, (x0 + x1) / 2 + 1, y1, frame);        // meeting rails of the sliding sashes
    }

    public enum Door { WOOD, METAL, DARK, SLIDING_LATTICE, SLIDING_GLASS, GARAGE }

    /** Front door filling its opening (32x64; double sliding doors and garage doors 64x64). */
    public static Texture door(Door k, int color, int seed) {
        boolean wide = k == Door.SLIDING_LATTICE || k == Door.SLIDING_GLASS || k == Door.GARAGE;
        Img m = new Img(wide ? 64 : 32, 64);
        Random r = new Random(seed * 71L + k.ordinal());
        int c = 0xFF000000 | color, w = m.w, h = m.h;
        m.fill(mul(c, 0.7f));
        switch (k) {
            case GARAGE:
                m.fill(c);
                for (int y = 0; y < h; y += 4) m.shade(0, y, w, y + 1, 0.72f);
                m.shade(0, 0, w, 2, 0.6f);
                break;
            case SLIDING_LATTICE:
                for (int p = 0; p < 2; p++) {
                    int x0 = p * 32 + 2, x1 = p * 32 + 30;
                    m.rect(x0, 2, x1, h - 2, 0xFF2E3034);
                    for (int x = x0 + 1; x < x1; x += 3) m.rect(x, 2, x + 1, h - 2, mul(c, 1.15f));
                    m.rect(x0, h / 3, x1, h / 3 + 1, mul(c, 1.1f));
                }
                break;
            case SLIDING_GLASS:
                for (int p = 0; p < 2; p++) {
                    int x0 = p * 32 + 2, x1 = p * 32 + 30;
                    m.rect(x0, 2, x1, h - 2, 0xFFB8C4CC);
                    m.grain(x0, 2, x1, h - 2, 0.02f, 0.04f, seed + p);
                    m.rect(x0, h / 2, x1, h / 2 + 1, mul(c, 1.1f));
                }
                break;
            default: {
                m.rect(2, 2, w - 2, h, c);
                m.grain(2, 2, w - 2, h, k == Door.WOOD ? 0.06f : 0.02f, 0.03f, seed);
                if (k == Door.WOOD) for (int y = 4; y < h; y += 5) m.shade(2, y, w - 2, y + 1, 0.85f);
                // frosted side light and a long vertical handle
                int sx = r.nextBoolean() ? 4 : w - 10;
                m.rect(sx, 6, sx + 6, h - 8, 0xFFC4CED4);
                int hx = sx < w / 2 ? w - 7 : 6;
                m.rect(hx, h / 3, hx + 2, h / 3 + 18, 0xFFC8C8C0);
                break;
            }
        }
        m.shade(0, 0, w, 1, 0.6f);
        return m.texture();
    }

    public enum Roof { KAWARA, METAL, SLATE, COPPER }

    /** Roof covering, 64x64 tiling every 2 m (rows run across the slope, v down the slope). */
    public static Texture roof(Roof k, int color, int seed) {
        if (k == Roof.KAWARA) return JpTextures.roofTiles(color, seed);
        Img m = new Img(64, 64);
        int c = 0xFF000000 | color;
        m.fill(c);
        switch (k) {
            case METAL:           // standing seams down the slope
                m.grain(0, 0, 64, 64, 0.02f, 0.02f, seed);
                for (int x = 0; x < 64; x += 8) { m.shade(x, 0, x + 1, 64, 1.25f); m.shade(x + 1, 0, x + 2, 64, 0.75f); }
                break;
            case SLATE:           // flat cement slates in staggered rows
                m.grain(0, 0, 64, 64, 0.04f, 0.04f, seed);
                for (int y = 0; y < 64; y += 6) {
                    m.shade(0, y, 64, y + 1, 0.65f);
                    for (int x = (y / 6 % 2) * 6; x < 64; x += 12) m.shade(x, y, x + 1, y + 6, 0.8f);
                }
                break;
            default:              // weathered copper
                m.grain(0, 0, 64, 64, 0.08f, 0.06f, seed);
                for (int x = 0; x < 64; x += 10) m.shade(x, 0, x + 1, 64, 0.8f);
                break;
        }
        return m.texture();
    }
}
