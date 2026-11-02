package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/** Ground-floor shopfronts (128x128 = one shop unit, ~4.5 m tall) with generated sign names. */
public final class Storefronts {
    private Storefronts() {}

    private static final int[] SIGN_BG = {0x1A1E2A, 0xF4F0E6, 0xC8323A, 0x1E5A8A, 0x2E7D5A, 0xF2C230, 0xE86A8A,
            0x2A2A2A, 0x6A3A8A, 0x0E7C7B, 0xF08A3C, 0xFFFFFF};
    private static final int[] NEON = {0xFF4FA0, 0x40F0FF, 0xFFE040, 0x7CFF6A, 0xFF7A30, 0xC080FF};

    public static Texture shop(Random r, String[] name, int seed) {
        Img m = new Img(128, 128);
        int wall = 0xFF000000 | (r.nextInt(3) == 0 ? Facades.NEUTRALS[r.nextInt(Facades.NEUTRALS.length)]
                : Facades.PASTELS[r.nextInt(Facades.PASTELS.length)]);
        Facades.wallMaterial(m, r, wall, r.nextInt(4) == 0 ? 3 : 0, seed);

        // cornice
        m.rect(0, 0, 128, 5, mul(wall, 1.1f));
        m.shade(0, 5, 128, 7, 0.75f);

        // sign band
        boolean neon = r.nextInt(3) == 0;
        int bg = neon ? 0xFF14161E : 0xFF000000 | SIGN_BG[r.nextInt(SIGN_BG.length)];
        int sy0 = 9, sy1 = 37;
        m.rect(4, sy0, 124, sy1, bg);
        m.frame(4, sy0, 124, sy1, 1, mul(bg, 0.6f));
        int textC;
        if (neon) textC = 0xFF000000 | NEON[r.nextInt(NEON.length)];
        else {
            float lum = (((bg >> 16) & 255) * 3 + ((bg >> 8) & 255) * 6 + (bg & 255)) / 2550f;
            textC = lum > 0.55f ? 0xFF1A1A22 : 0xFFF8F6F0;
            if (r.nextInt(3) == 0) textC = lum > 0.55f ? 0xFFC8323A : 0xFFF2C230;
        }
        String l1 = name[0], l2 = name[1];
        int s1 = PixelFont.width(l1, 2) <= 114 ? 2 : 1;
        int w1 = PixelFont.width(l1, s1), w2 = PixelFont.width(l2, 1);
        int y1 = s1 == 2 ? sy0 + 3 : sy0 + 5;
        int y2 = sy1 - 10;
        if (neon) {
            int glow = mix(bg, textC, 0.35f);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    PixelFont.draw(m.px, 128, 128, l1, 64 - w1 / 2 + dx, y1 + dy, s1, glow);
                    PixelFont.draw(m.px, 128, 128, l2, 64 - w2 / 2 + dx, y2 + dy, 1, glow);
                }
        }
        PixelFont.draw(m.px, 128, 128, l1, 64 - w1 / 2, y1, s1, textC);
        PixelFont.draw(m.px, 128, 128, l2, 64 - w2 / 2, y2, 1, neon ? mix(textC, 0xFFFFFFFF, 0.3f) : textC);

        // awning
        int gy0 = 40;
        if (r.nextBoolean()) {
            int a = 0xFF000000 | new int[]{0xC8323A, 0x2E7D7A, 0x1E5A8A, 0xF2C230, 0x2A2A2A, 0xE86A8A, 0x3E8A5A}[r.nextInt(7)];
            int b = r.nextBoolean() ? 0xFFF4F2EC : mul(a, 0.75f);
            for (int y = 38; y < 52; y++)
                for (int x = 2; x < 126; x++) {
                    int c = ((x / 8) & 1) == 0 ? a : b;
                    m.set(x, y, mul(c, 1.05f - (y - 38) * 0.02f));
                }
            for (int x = 2; x < 126; x++) { // scalloped edge
                int ph = x % 8;
                int extra = ph > 1 && ph < 6 ? 2 : 0;
                for (int y = 52; y < 52 + extra; y++) m.set(x, y, mul(((x / 8) & 1) == 0 ? a : b, 0.8f));
            }
            gy0 = 54;
        }

        // glazing with door
        int frame = r.nextBoolean() ? 0xFF2A2C30 : 0xFFC8CCD0;
        int glass = 0xFF000000 | Facades.GLASS[r.nextInt(Facades.GLASS.length)];
        int gx0 = 6, gx1 = 122, gy1 = 114;
        interior(m, r, gx0, gy0, gx1, gy1, glass);
        m.frame(gx0 - 2, gy0 - 2, gx1 + 2, gy1 + 1, 2, frame);
        int doorW = 24, dx = r.nextInt(3) == 0 ? 64 - doorW / 2 : (r.nextBoolean() ? gx0 + 6 : gx1 - 6 - doorW);
        int dy0 = gy0 + 6;
        m.rect(dx - 2, dy0 - 2, dx + doorW + 2, gy1, frame);
        m.rect(dx, dy0, dx + doorW, gy1, mix(glass, 0xFF101418, 0.55f));
        m.rect(dx + doorW - 6, dy0 + 24, dx + doorW - 4, dy0 + 40, 0xFFD8D8D0);
        // mullions
        for (int x = gx0 + 36; x < gx1 - 10; x += 36)
            if (x < dx - 4 || x > dx + doorW + 4) m.rect(x, gy0, x + 2, gy1, frame);
        // window decals (hours / promos)
        if (r.nextBoolean()) {
            int px = dx > 64 ? 16 : 82;
            int dbg = 0xFF000000 | SIGN_BG[r.nextInt(SIGN_BG.length)];
            float dl = (((dbg >> 16) & 255) * 3 + ((dbg >> 8) & 255) * 6 + (dbg & 255)) / 2550f;
            m.rect(px, gy0 + 20, px + 28, gy0 + 31, dbg);
            PixelFont.draw(m.px, 128, 128, r.nextBoolean() ? "OPEN" : "SALE", px + 3, gy0 + 22, 1, dl > 0.55f ? 0xFF1A1A22 : 0xFFFFFFFF);
        }
        // kickplate
        int kick = r.nextBoolean() ? 0xFF3A3A40 : mul(wall, 0.78f);
        m.rect(0, 114, 128, 128, kick);
        m.grain(0, 114, 128, 128, 0.05f, 0.05f, seed + 5);
        m.shade(0, 114, 128, 115, 1.2f);
        // pilasters
        m.rect(0, gy0 - 4, 4, 114, mul(wall, 0.95f));
        m.rect(124, gy0 - 4, 128, 114, mul(wall, 0.95f));
        return m.texture();
    }

    /** Lobby / entrance for towers: tall glass, centred doors, street number. */
    public static Texture lobby(Random r, int seed) {
        Img m = new Img(128, 128);
        int stone = 0xFF000000 | Facades.NEUTRALS[r.nextInt(Facades.NEUTRALS.length)];
        Facades.wallMaterial(m, r, stone, 3, seed);
        int glass = 0xFF000000 | Facades.GLASS[r.nextInt(Facades.GLASS.length)];
        int frame = 0xFF2A2C30;
        interior(m, r, 6, 14, 122, 118, glass);
        for (int x = 6; x < 122; x += 29) m.rect(x, 14, x + 2, 118, frame);
        m.rect(6, 12, 122, 14, frame);
        m.rect(46, 52, 82, 118, mix(glass, 0xFF101418, 0.5f));
        m.frame(46, 52, 82, 118, 2, 0xFFC8C0A8);
        String num = String.valueOf(100 + r.nextInt(9800));
        PixelFont.draw(m.px, 128, 128, num, 64 - PixelFont.width(num, 2) / 2, 30, 2, 0xFFF0E8D0);
        m.rect(0, 118, 128, 128, mul(stone, 0.8f));
        return m.texture();
    }

    private static void interior(Img m, Random r, int x0, int y0, int x1, int y1, int glass) {
        int deep = mix(glass, 0xFF14181E, 0.7f);
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) (y1 - y0);
            int c = mix(mix(glass, 0xFFE8F4FF, 0.25f), deep, 0.35f + t * 0.5f);
            for (int x = x0; x < x1; x++) m.set(x, y, c);
        }
        // shelves / goods
        int kind = r.nextInt(3);
        if (kind == 0) {
            for (int sy = y0 + 18; sy < y1 - 6; sy += 14) {
                m.rect(x0, sy, x1, sy + 1, mul(deep, 1.6f));
                for (int x = x0 + 2; x < x1 - 4; x += 4 + r.nextInt(4)) {
                    int gh = 3 + r.nextInt(7);
                    int gc = mix(0xFF000000 | (r.nextInt(0xFFFFFF)), deep, 0.45f);
                    m.rect(x, sy - gh, x + 3, sy, gc);
                }
            }
        } else if (kind == 1) { // tables / counter
            m.rect(x0, y1 - 26, x1, y1 - 22, mul(deep, 1.8f));
            for (int x = x0 + 8; x < x1 - 8; x += 22) {
                m.rect(x, y1 - 14, x + 12, y1 - 12, mul(deep, 2f));
                m.rect(x + 5, y1 - 12, x + 7, y1, mul(deep, 1.5f));
            }
        } else { // mannequins / displays
            for (int x = x0 + 10; x < x1 - 10; x += 26) {
                int cc = mix(0xFF000000 | (r.nextInt(0xFFFFFF)), deep, 0.3f);
                m.disc(x + 4, y1 - 52, 3, mul(deep, 2.2f));
                m.rect(x, y1 - 48, x + 9, y1 - 26, cc);
                m.rect(x + 2, y1 - 26, x + 7, y1 - 8, mul(cc, 0.7f));
            }
        }
        // reflection streaks
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int d = (x - x0) + (y - y0) / 2;
                if (d % 41 < 4) m.set(x, y, mix(m.get(x, y), 0xFFFFFFFF, 0.18f));
            }
    }
}
