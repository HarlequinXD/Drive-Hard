package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/** Town-centre textures: name boards, the post office mark, school and supermarket fronts. */
public final class TownTextures {
    private TownTextures() { }

    /** Signboard with a lettered name (128x32). */
    public static Texture sign(String text, int bg, int fg, int seed) {
        Img m = new Img(128, 32);
        m.fill(0xFF000000 | bg);
        m.grain(0, 0, 128, 32, 0.02f, 0.02f, seed);
        m.frame(0, 0, 128, 32, 2, mul(0xFF000000 | bg, 0.75f));
        int scale = PixelFont.width(text, 2) <= 116 ? 2 : 1;
        int tw = PixelFont.width(text, scale);
        PixelFont.draw(m.px, 128, 32, text, 64 - tw / 2, 16 + 3 * scale, scale, 0xFF000000 | fg);
        Kanji.row(m.px, 128, 32, seed, 4, 6, 4, 5, 1, mix(0xFF000000 | fg, 0xFF000000 | bg, 0.3f));
        return m.texture();
    }

    /** Post office sign: the red postal mark on white (128x32). */
    public static Texture postSign(int seed) {
        Img m = new Img(128, 32);
        m.fill(0xFFF4F2EC);
        int red = 0xFFD8202A;
        m.rect(0, 0, 128, 4, red);
        m.rect(0, 28, 128, 32, red);
        // the 〒 mark
        m.rect(10, 8, 30, 11, red);
        m.rect(10, 14, 30, 17, red);
        m.rect(18, 14, 22, 26, red);
        PixelFont.draw(m.px, 128, 32, "POST OFFICE", 38, 22, 1, red);
        Kanji.row(m.px, 128, 32, seed, 3, 38, 6, 6, 1, 0xFF2A2A2E);
        return m.texture();
    }

    /** One storey of a school: white concrete, a long band of windows (64x128, tiles per floor). */
    public static Texture schoolFacade(int seed) {
        Img m = new Img(64, 128);
        Random r = new Random(seed);
        Facades.wallMaterial(m, r, 0xFFE8E6E0, 1, seed);
        Facades.glass(m, r, 0, 30, 64, 98, 0xFF7A90A0, 0xFFC8CCD0, 0, false);
        for (int x = 0; x < 64; x += 16) m.rect(x, 30, x + 2, 98, 0xFFC8CCD0);
        m.rect(0, 62, 64, 64, 0xFFC8CCD0);
        m.rect(0, 98, 64, 104, 0xFFD8D6D0);
        m.shade(0, 104, 64, 106, 0.7f);
        return m.texture();
    }

    /** Supermarket shopfront: glass with posters behind it (64x64 per bay). */
    public static Texture superFront(int seed) {
        Img m = new Img(64, 64);
        Random r = new Random(seed);
        m.fill(0xFFD8D6D0);
        Facades.glass(m, r, 2, 8, 62, 62, 0xFF6A8090, 0xFF9A9EA2, 2, false);
        for (int k = 0; k < 3; k++) {
            int x = 6 + r.nextInt(44), y = 12 + r.nextInt(30);
            m.rect(x, y, x + 10, y + 14, 0xFF000000 | (r.nextInt(0xFFFFFF) | 0x404040));
        }
        m.rect(0, 0, 64, 6, 0xFFD8302A);
        return m.texture();
    }

    /** Petrol station canopy fascia: white with coloured bands (64x16). */
    public static Texture canopy(int seed) {
        Img m = new Img(64, 16);
        m.fill(0xFFF4F4F0);
        m.rect(0, 4, 64, 8, 0xFF1A5AB0);
        m.rect(0, 9, 64, 11, 0xFFE8B020);
        m.grain(0, 0, 64, 16, 0.01f, 0.01f, seed);
        return m.texture();
    }

    /** School sports ground: compacted sand (64x64). */
    public static Texture field(int seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFC8B496);
        m.grain(0, 0, 64, 64, 0.06f, 0.08f, seed);
        return m.texture();
    }
}
