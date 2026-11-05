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

    public enum Material { SIDING, STUCCO, BOARDS, TILE, BRICK, PANEL, PLASTER, CHARRED, BARN }

    /** Wall material, 64x64, tiles every 2 m. */
    public static Texture material(Material k, int color, int seed) {
        Img m = new Img(64, 64);
        Random r = new Random(seed * 37L + k.ordinal());
        m.fill(0xFF000000 | color);
        switch (k) {
            case SIDING:          // horizontal lap siding: each board darker under the lap, lit along its lower edge
                m.grain(0, 0, 64, 64, 0.03f, 0.025f, seed);
                for (int y = 0; y < 64; y += 6) {
                    m.shade(0, y, 64, y + 1, 0.66f);
                    m.shade(0, y + 1, 64, y + 2, 0.86f);
                    m.shade(0, y + 4, 64, y + 6, 1.05f);
                }
                for (int y = 0; y < 64; y += 6) { int x = r.nextInt(64); m.shade(x, y, x + 1, y + 6, 0.82f); m.shade(x + 1, y + 1, x + 2, y + 6, 1.08f); }
                Photo.weather(m, 0.05f, seed);
                break;
            case STUCCO:          // sprayed plaster: fine speckle over soft blotches, faint hairline cracks
                m.grain(0, 0, 64, 64, 0.05f, 0.07f, seed);
                Photo.weather(m, 0.08f, seed);
                for (int crack = 0; crack < 2; crack++) {
                    int x = r.nextInt(64), y = r.nextInt(64);
                    for (int s2 = 0; s2 < 10 + r.nextInt(10); s2++) {
                        m.set(x & 63, y & 63, mul(m.get(x & 63, y & 63), 0.86f));
                        x += r.nextInt(3) - 1;
                        y += 1;
                    }
                }
                break;
            case BOARDS:          // vertical boards with battens and wood grain
                m.grain(0, 0, 64, 64, 0.07f, 0.05f, seed);
                for (int x = 0; x < 64; x++) {
                    float g = 0.94f + 0.08f * (float) Math.sin(x * 2.3f + Noise.rnd(x / 8, 0, seed) * 6f);
                    m.shade(x, 0, x + 1, 64, g);
                }
                for (int x = 0; x < 64; x += 8) { m.shade(x, 0, x + 1, 64, 0.62f); m.shade(x + 1, 0, x + 2, 64, 1.14f); m.shade(x + 2, 0, x + 3, 64, 1.05f); }
                Photo.weather(m, 0.06f, seed);
                break;
            case TILE:            // small glazed facade tiles, each slightly different, with a glint
                m.grain(0, 0, 64, 64, 0.02f, 0.03f, seed);
                for (int y = 0; y < 64; y += 4)
                    for (int x = (y / 4 % 2) * 4 - 4; x < 64; x += 8) {
                        int bx0 = Math.max(0, x), bx1 = Math.min(64, x + 8);
                        m.shade(bx0, y, bx1, y + 4, 0.92f + r.nextFloat() * 0.14f);
                        if (r.nextInt(5) == 0) m.shade(bx0 + 1, y + 1, Math.min(64, bx0 + 3), y + 2, 1.15f);
                    }
                for (int y = 0; y < 64; y += 4) m.shade(0, y, 64, y + 1, 0.8f);
                for (int y = 0; y < 64; y += 4) for (int x = (y / 4 % 2) * 4; x < 64; x += 8) m.shade(x, y, x + 1, y + 4, 0.82f);
                Photo.weather(m, 0.04f, seed);
                break;
            case BRICK:           // bricks: each its own shade, recessed mortar, lit top edges
                m.grain(0, 0, 64, 64, 0.06f, 0.06f, seed);
                {
                    int mortar = mix(0xFFCFC8BC, 0xFF000000 | color, 0.3f);
                    for (int y = 0; y < 64; y += 4) {
                        int off = (y / 4 % 2) * 4;
                        for (int x = -8; x < 64; x += 8) {
                            float v = 0.82f + r.nextFloat() * 0.3f;
                            int bx0 = Math.max(0, x + off), bx1 = Math.min(64, x + off + 8);
                            m.shade(bx0, y, bx1, y + 4, v);
                            if (r.nextInt(9) == 0) m.tint(bx0, y, bx1, y + 4, 0xFF3A2A22, 0.18f);   // burnt brick
                            m.shade(bx0, y + 1, bx1, y + 2, 1.06f);
                        }
                        m.rect(0, y, 64, y + 1, mortar);
                        m.shade(0, y + 1, 64, y + 2, 0.9f);                                          // shadow under the mortar line
                        for (int x = off; x < 64; x += 8) m.rect(x, y, x + 1, y + 4, mortar);
                    }
                    Photo.weather(m, 0.06f, seed);
                }
                break;
            case PANEL:           // fibre-cement panels: recessed joints, fixings, a stone-like finish
                m.grain(0, 0, 64, 64, 0.04f, 0.04f, seed);
                Photo.weather(m, 0.06f, seed);
                m.shade(0, 0, 64, 1, 0.68f); m.shade(0, 1, 64, 2, 1.08f);
                m.shade(0, 0, 1, 64, 0.68f); m.shade(1, 0, 2, 64, 1.08f);
                m.shade(32, 0, 33, 64, 0.72f); m.shade(33, 0, 34, 64, 1.06f);
                for (int y = 6; y < 64; y += 26) for (int x = 6; x < 64; x += 26) m.set(x, y, mul(m.get(x, y), 0.7f));
                break;
            case PLASTER:         // traditional white plaster, a little sun-faded and rain-marked
                m.grain(0, 0, 64, 64, 0.04f, 0.03f, seed);
                Photo.weather(m, 0.07f, seed);
                break;
            case BARN: {          // barn / shed planks: wide boards, dark gaps, knots, sun-greyed and stained
                for (int x = 0; x < 64; x++) {
                    int board = x / 10;
                    float tone = 0.86f + Noise.rnd(board, 3, seed) * 0.26f;
                    m.shade(x, 0, x + 1, 64, tone);
                }
                m.grain(0, 0, 64, 64, 0.09f, 0.06f, seed);
                for (int x = 0; x < 64; x++) {               // vertical grain lines
                    float g = 0.93f + 0.09f * (float) Math.sin(x * 2.9f + Noise.rnd(x / 10, 1, seed) * 7f);
                    for (int y = 0; y < 64; y++) m.px[y * 64 + x] = mul(m.px[y * 64 + x], g + (Noise.rnd(x, y / 6, seed + 5) - 0.5f) * 0.06f);
                }
                for (int x = 0; x < 64; x += 10) {           // gaps between the boards
                    m.shade(x, 0, x + 1, 64, 0.45f);
                    m.shade(x + 1, 0, x + 2, 64, 1.08f);
                }
                for (int knot = 0; knot < 4; knot++) {      // knots
                    int kx = 3 + r.nextInt(58), ky = r.nextInt(64);
                    m.disc(kx, ky, 1.2f, mul(m.get(kx, ky), 0.6f));
                }
                Photo.weather(m, 0.1f, seed);
                Photo.streaks(m, 0, 64, 0, 64, 0.35f, seed + 9);
                break;
            }
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
                for (int y = 2; y < h - 2; y++) m.rect(2, y, w - 2, y + 1, mix(0xFFD4DCE2, 0xFFA8B4BC, (y - 2) / (float) (h - 4)));
                m.grain(2, 2, w - 2, h - 2, 0.03f, 0.05f, seed);
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
        Photo.frame(m, 0, 0, w, h, 2, f);
        return m.texture();
    }

    private static void glass(Img m, int x0, int y0, int x1, int y1, int glass, int frame, Random r) {
        Photo.glass(m, x0, y0, x1, y1, glass, r);
        int k = r.nextInt(5);
        if (k <= 1) {
            int cur = mix(0xFFE8E0D0, 0xFF000000 | r.nextInt(0xFFFFFF), 0.25f);
            Photo.curtains(m, x0, y0, x1, y1, cur, 0.35f + r.nextFloat() * 0.4f, k == 0);
        } else if (k == 2) {
            Photo.blinds(m, x0, y0, x1, y1, mix(0xFFEDEBE4, glass, 0.2f), 0.2f + r.nextFloat() * 0.6f);
        }
        // meeting rails of the sliding sashes, with a lit edge
        int mx = (x0 + x1) / 2;
        m.rect(mx - 1, y0, mx + 1, y1, frame);
        m.rect(mx - 1, y0, mx, y1, mul(frame, 1.15f));
    }

    public enum Door { WOOD, METAL, DARK, SLIDING_LATTICE, SLIDING_GLASS, GARAGE, BARN }

    /** Front door filling its opening (32x64; double sliding doors and garage doors 64x64). */
    public static Texture door(Door k, int color, int seed) {
        boolean wide = k == Door.SLIDING_LATTICE || k == Door.SLIDING_GLASS || k == Door.GARAGE || k == Door.BARN;
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
            case BARN:            // two big plank leaves with Z bracing, on a track
                m.fill(c);
                for (int x = 0; x < w; x++) m.shade(x, 0, x + 1, h, 0.88f + Noise.rnd(x / 6, 2, seed) * 0.22f);
                m.grain(0, 0, w, h, 0.08f, 0.06f, seed);
                for (int x = 0; x < w; x += 6) m.shade(x, 0, x + 1, h, 0.55f);
                for (int p = 0; p < 2; p++) {
                    int x0 = p * 32 + 2, x1 = p * 32 + 30;
                    int brace = mul(c, 1.15f);
                    m.rect(x0, 6, x1, 10, brace); m.shade(x0, 10, x1, 11, 0.6f);
                    m.rect(x0, h - 12, x1, h - 8, brace); m.shade(x0, h - 8, x1, h - 7, 0.6f);
                    for (int y = 10; y < h - 12; y++) {        // the diagonal of the Z
                        int x = x0 + (int) ((y - 10) / (float) (h - 22) * (x1 - x0 - 4));
                        m.rect(x, y, x + 4, y + 1, brace);
                    }
                    m.frame(x0, 2, x1, h, 1, mul(c, 0.6f));
                }
                m.rect(0, 0, w, 3, 0xFF3A3A3C);                 // the sliding track
                m.rect(30, h / 2 - 2, 34, h / 2 + 2, 0xFF2A2A2A);  // latch
                Photo.weather(m, 0.08f, seed);
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
