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

    /**
     * An unsealed farm track seen from above: u runs across the track (0..1 = edge to edge), v
     * along it (one tile = 20 m). Gravel: grey-brown stones with two packed wheel ruts. Dirt:
     * brown earth with darker ruts and a grassy strip down the middle, ragged grassy edges.
     */
    public static Texture track(boolean dirt, int seed) {
        Img m = new Img(64, 256);
        java.util.Random r = new java.util.Random(seed);
        int base = dirt ? rgb(132, 104, 72) : rgb(150, 142, 128);
        int rut = dirt ? rgb(104, 80, 54) : rgb(126, 118, 104);
        int grass = rgb(92, 118, 58);
        m.fill(base);
        m.grain(0, 0, 64, 256, 0.10f, 0.10f, seed);
        for (int y = 0; y < 256; y++) {
            // wheel ruts wander a little
            float wob = (float) Math.sin(y * 0.055f + seed) * 1.2f;
            for (int x = 0; x < 64; x++) {
                float u = (x + 0.5f) / 64f;
                float d1 = Math.abs(u - 0.27f - wob / 64f), d2 = Math.abs(u - 0.73f - wob / 64f);
                float dr = Math.min(d1, d2);
                int c = m.get(x, y);
                if (dr < 0.07f) c = mix(c, rut, 0.75f * (1f - dr / 0.07f) + 0.15f);
                if (dirt && Math.abs(u - 0.5f) < 0.09f) c = mix(c, grass, 0.6f + r.nextFloat() * 0.3f);
                float edge = Math.min(u, 1f - u);
                if (edge < 0.07f) c = mix(c, grass, (dirt ? 0.8f : 0.45f) * (1f - edge / 0.07f) + r.nextFloat() * 0.15f);
                m.set(x, y, c);
            }
        }
        // loose stones / clods
        for (int k = 0; k < 880; k++) {
            int x = r.nextInt(64), y = r.nextInt(256);
            int c = m.get(x, y);
            m.set(x, y, r.nextBoolean() ? Img.mul(c, 1.25f) : Img.mul(c, 0.75f));
        }
        return m.texture();
    }

    /** Frames of an expanding ripple ring on water (64x64, cut-out alpha, dithered as it fades). */
    public static final int RIPPLE_FRAMES = 8;

    public static Texture ripple(int frame) {
        Img m = new Img(64, 64);
        float t = frame / (float) (RIPPLE_FRAMES - 1);
        float rr = 6f + t * 23f;                       // ring radius in texels
        float fade = (1f - t) * (1f - t * 0.3f);
        int crest = rgb(226, 240, 242), trough = rgb(52, 128, 150);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float dx = x + 0.5f - 32f, dy = y + 0.5f - 32f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                // the ring is slightly uneven, like a real ripple on a moving surface
                float wob = (float) Math.sin(Math.atan2(dy, dx) * 5 + frame) * 0.6f;
                float c1 = Math.abs(d - rr - wob), c2 = Math.abs(d - (rr - 3f) - wob);
                float n = Noise.rnd(x, y, 4100 + frame);
                int c = 0;
                if (c1 < 0.9f && n < fade * (0.9f - c1 * 0.5f)) c = crest;
                else if (c2 < 0.8f && n < fade * 0.3f) c = trough;
                else if (t < 0.3f && d < rr - 4f && n < (0.3f - t) * 0.9f) c = crest;    // froth in the middle at first
                m.px[y * 64 + x] = c;
            }
        return m.texture();
    }

    /**
     * Splash burst, frame by frame (48x48, anchored at the bottom centre, 32 px per metre):
     * a crown of water thrown up and out, droplets arcing away and falling back.
     */
    public static int[] splash(int frames, int fw, int fh, int seed) {
        int[] out = new int[frames * fw * fh];
        java.util.Random r = new java.util.Random(seed);
        int n = 70;
        float[] vx = new float[n], vy = new float[n], sz = new float[n];
        for (int i = 0; i < n; i++) {
            float a = (float) ((r.nextFloat() - 0.5f) * Math.PI * 0.85f);
            float sp = 2.2f + r.nextFloat() * 3.2f;
            vx[i] = (float) Math.sin(a) * sp;
            vy[i] = (float) Math.cos(a) * sp * (0.7f + r.nextFloat() * 0.5f);
            sz[i] = r.nextFloat() < 0.25f ? 2f : 1f;
        }
        float ppm = 32f;
        for (int f = 0; f < frames; f++) {
            float t = (f + 1) / (float) frames * 0.55f;      // seconds after the splash
            int base = f * fw * fh;
            // the crown: a white column / sheet that rises and collapses
            float ch = Math.max(0f, 0.55f * (float) Math.sin(Math.min(1f, t / 0.42f) * Math.PI)) * ppm;
            float cw = (0.18f + t * 0.6f) * ppm;
            for (int y = 0; y < fh; y++)
                for (int x = 0; x < fw; x++) {
                    float dx = Math.abs(x + 0.5f - fw * 0.5f), hy = fh - 4 - y;
                    if (hy < 0 || hy > ch) continue;
                    float edge = dx / Math.max(1f, cw * (1f - 0.4f * hy / Math.max(1f, ch)));
                    if (edge > 1f) continue;
                    float k = Noise.rnd(x, y, seed + f * 7);
                    if (k > 0.35f + 0.6f * (1f - edge)) continue;
                    out[base + y * fw + x] = mix(rgb(250, 252, 252), rgb(176, 214, 226), edge * 0.7f + k * 0.3f);
                }
            // droplets on ballistic arcs
            for (int i = 0; i < n; i++) {
                float px = fw * 0.5f + vx[i] * t * ppm, py = (fh - 4) - (vy[i] * t - 4.9f * t * t) * ppm;
                if (py > fh - 3) continue;
                int s2 = (int) sz[i];
                for (int a = 0; a < s2; a++)
                    for (int b = 0; b < s2; b++) {
                        int X = (int) px + a, Y = (int) py + b;
                        if (X < 0 || Y < 0 || X >= fw || Y >= fh) continue;
                        out[base + Y * fw + X] = i % 3 == 0 ? rgb(200, 228, 236) : rgb(248, 252, 252);
                    }
            }
        }
        return out;
    }
}
