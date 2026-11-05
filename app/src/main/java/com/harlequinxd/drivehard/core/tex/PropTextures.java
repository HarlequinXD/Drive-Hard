package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.rgb;

/** Small building props: air-conditioner outdoor units, balcony fronts, and power line ribbons. */
public final class PropTextures {
    private PropTextures() {}

    /** Front of an outdoor a/c unit: off-white casing, a round fan grille on the left, vents on the right. */
    public static Texture aircon(int seed) {
        Img m = new Img(32, 32);
        int casing = rgb(222, 222, 214), edge = rgb(170, 170, 164), dark = rgb(58, 60, 62);
        m.fill(casing);
        m.grain(0, 0, 32, 32, 0.04f, 0.03f, seed);
        m.frame(0, 0, 32, 32, 1, edge);
        // fan: dark disc with a grille of rings and a hub
        m.disc(12f, 16f, 10f, dark);
        for (int r = 3; r <= 9; r += 3)
            for (int a = 0; a < 64; a++) {
                double t = a * Math.PI * 2 / 64;
                int x = (int) Math.round(12 + Math.cos(t) * r), y = (int) Math.round(16 + Math.sin(t) * r);
                if (x >= 0 && y >= 0 && x < 32 && y < 32) m.set(x, y, rgb(140, 142, 144));
            }
        m.disc(12f, 16f, 2f, rgb(150, 150, 150));
        // louvres
        for (int y = 6; y < 27; y += 3) m.rect(25, y, 30, y + 1, edge);
        return m.texture();
    }

    /** Top or side of an a/c unit casing. */
    public static Texture airconSide(int seed) {
        Img m = new Img(16, 16);
        m.fill(rgb(214, 214, 206));
        m.grain(0, 0, 16, 16, 0.04f, 0.03f, seed);
        m.frame(0, 0, 16, 16, 1, rgb(170, 170, 164));
        return m.texture();
    }

    /** Balcony front: a concrete upright edge, a frosted panel and a metal handrail on top. */
    public static Texture balcony(int wall, int seed) {
        Img m = new Img(64, 32);
        int panel = mix(rgb(196, 206, 212), wall, 0.25f);
        m.fill(panel);
        m.grain(0, 0, 64, 32, 0.05f, 0.03f, seed);
        int rail = rgb(110, 112, 116);
        m.rect(0, 0, 64, 3, rail);            // handrail
        m.rect(0, 3, 64, 4, rgb(70, 72, 76));
        m.rect(0, 28, 64, 32, mix(wall, rgb(120, 120, 120), 0.3f));   // the slab edge
        for (int x = 0; x < 64; x += 16) m.rect(x, 3, x + 2, 28, rail);  // posts
        return m.texture();
    }

    /** Underside / floor of a balcony slab. */
    public static Texture balconySlab(int seed) {
        Img m = new Img(32, 32);
        m.fill(rgb(176, 176, 170));
        m.grain(0, 0, 32, 32, 0.08f, 0.05f, seed);
        return m.texture();
    }

    /**
     * Power line ribbon: transparent with three sagging-free dark wires across it (the ribbon
     * itself sags), tiled along the span. 64 x 16, wires at rows 2, 8 and 14.
     */
    public static Texture powerLines() {
        Img m = new Img(64, 16);
        java.util.Arrays.fill(m.px, 0x00000000);
        int wire = 0xFF2A2C2E, hi = 0xFF5A5C60;
        for (int x = 0; x < 64; x++) {
            for (int wy : new int[]{2, 8, 14}) {
                m.px[wy * 64 + x] = wire;
                m.px[(wy - 1) * 64 + x] = hi;
            }
        }
        return m.texture();
    }

    /** Stackable plastic beer crate side: coloured frame with hand holes and a ribbed grid. */
    public static Texture crate(int color, int seed) {
        Img m = new Img(32, 32);
        int dark = Img.mul(color, 0.6f);
        m.fill(color);
        m.grain(0, 0, 32, 32, 0.05f, 0.04f, seed);
        for (int x = 3; x < 32; x += 6) m.rect(x, 9, x + 2, 28, dark);
        m.rect(0, 26, 32, 28, dark);
        m.rect(10, 3, 22, 7, 0xFF1A1A1A);      // hand hole
        m.frame(0, 0, 32, 32, 1, dark);
        return m.texture();
    }

    /** Wheeled rubbish bin: dark green plastic with a lid line and a stencilled label band. */
    public static Texture bin(int seed) {
        Img m = new Img(32, 32);
        int green = rgb(46, 92, 64);
        m.fill(green);
        m.grain(0, 0, 32, 32, 0.06f, 0.04f, seed);
        m.rect(0, 0, 32, 4, Img.mul(green, 0.7f));
        m.rect(4, 12, 28, 18, rgb(220, 220, 210));
        for (int x = 6; x < 26; x += 3) m.rect(x, 14, x + 2, 16, rgb(60, 60, 60));
        return m.texture();
    }

    /** Flattened and stacked cardboard boxes. */
    public static Texture cardboard(int seed) {
        Img m = new Img(32, 32);
        int c = rgb(176, 138, 96);
        m.fill(c);
        m.grain(0, 0, 32, 32, 0.12f, 0.06f, seed);
        m.rect(0, 15, 32, 17, Img.mul(c, 0.7f));
        m.rect(14, 0, 18, 32, rgb(200, 186, 150));    // packing tape
        return m.texture();
    }

    /** Propane cylinder: pale grey steel with a band. */
    public static Texture gasBottle(int seed) {
        Img m = new Img(16, 32);
        int c = rgb(200, 200, 196);
        m.fill(c);
        m.grain(0, 0, 16, 32, 0.05f, 0.04f, seed);
        m.rect(0, 8, 16, 11, rgb(60, 110, 180));
        for (int x = 0; x < 16; x++) m.px[x] = Img.mul(c, 0.6f);
        return m.texture();
    }
}
