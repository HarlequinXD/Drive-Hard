package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;
import static com.harlequinxd.drivehard.core.tex.Img.rgb;

/**
 * All world textures, generated at startup. Base materials are 64x64 (= 2 x 2 m), facades
 * 64x128 (one bay x one storey), shopfronts 128x128.
 */
public final class TextureLibrary {
    public final List<Texture> list = new ArrayList<>();

    public final int asphalt, asphaltPatched, sidewalk, pavers, concrete, curb, grass, sand, water, roofGravel,
            roofMembrane, roofTar, parking, paintWhite, paintYellow, trunk, frond, hedge, metal, pole, shadow,
            planter, lampHead, umbrella;

    public final int[][] facades = new int[Facades.Kind.values().length][];
    public final int[] shops;
    public final int[] lobbies;
    public final List<String> shopNames = new ArrayList<>();

    public TextureLibrary(long seed) {
        asphalt = add(asphalt(seed, false));
        asphaltPatched = add(asphalt(seed + 1, true));
        sidewalk = add(sidewalk(seed));
        pavers = add(pavers(seed));
        concrete = add(concrete(seed));
        curb = add(curb(seed));
        grass = add(grass(seed));
        sand = add(sand(seed));
        Texture w = water(seed);
        w.scrollU = 0.05f;
        water = add(w);
        roofGravel = add(roof(seed, 0));
        roofMembrane = add(roof(seed, 1));
        roofTar = add(roof(seed, 2));
        parking = add(parking(seed));
        paintWhite = add(solid(0xFFEDEDE6, seed));
        paintYellow = add(solid(0xFFE2B33A, seed));
        trunk = add(trunk(seed));
        frond = add(frond(seed));
        hedge = add(hedge(seed));
        metal = add(metal(seed));
        pole = add(solid(0xFF4A5250, seed));
        shadow = add(shadow());
        planter = add(planter(seed));
        lampHead = add(solid(0xFFF4F0D8, seed));
        umbrella = add(umbrella());

        Random r = new Random(seed);
        int[] counts = {44, 40, 24, 30, 36, 12, 16, 30}; // DECO RESIDENTIAL HOTEL OFFICE COMMERCIAL GARAGE INDUSTRIAL BLANK
        Facades.Kind[] kinds = Facades.Kind.values();
        int s = 1000;
        for (int k = 0; k < kinds.length; k++) {
            facades[k] = new int[counts[k]];
            for (int i = 0; i < counts[k]; i++) facades[k][i] = add(Facades.generate(r, kinds[k], s++));
        }
        ShopNames names = new ShopNames(seed + 77);
        shops = new int[120];
        for (int i = 0; i < shops.length; i++) {
            String[] n = names.next();
            shopNames.add(n[0] + " " + n[1]);
            shops[i] = add(Storefronts.shop(r, n, s++));
        }
        lobbies = new int[12];
        for (int i = 0; i < lobbies.length; i++) lobbies[i] = add(Storefronts.lobby(r, s++));
    }

    public int add(Texture t) {
        list.add(t);
        return list.size() - 1;
    }

    public Texture[] array() { return list.toArray(new Texture[0]); }

    public int[] facades(Facades.Kind k) { return facades[k.ordinal()]; }

    // ------------------------------------------------------------------ base materials

    private static Texture asphalt(long seed, boolean patched) {
        Img m = new Img(64, 64);
        int sd = (int) seed;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 3);
                float k = 0.29f + n * 0.05f + g * 0.06f;
                if (Noise.rnd(x, y, sd + 7) > 0.965f) k += 0.10f;
                int v = (int) (k * 255);
                m.px[y * 64 + x] = rgb(v, v, v + 4);
            }
        // oil stain + hairline crack
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float o = Noise.value(x, y, 64, 64, 4, 4, sd + 11);
                if (o > 0.74f) m.px[y * 64 + x] = mul(m.px[y * 64 + x], 0.94f);
            }
        int cy = 20;
        for (int x = 6; x < 40; x++) {
            cy += Noise.rnd(x, 0, sd + 5) > 0.5f ? 1 : -1;
            cy = Math.max(4, Math.min(60, cy));
            m.shade(x, cy, x + 1, cy + 1, 0.7f);
        }
        if (patched) {
            m.shade(10, 34, 44, 58, 0.9f);
            m.frame(10, 34, 44, 58, 1, rgb(58, 58, 60));
        }
        return m.texture();
    }

    private static Texture sidewalk(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFC8C4BA);
        m.grain(0, 0, 64, 64, 0.06f, 0.05f, (int) seed + 20);
        for (int i = 0; i < 64; i++) {
            m.shade(i, 0, i + 1, 1, 0.72f);
            m.shade(0, i, 1, i + 1, 0.72f);
            m.shade(i, 32, i + 1, 33, 0.8f);
            m.shade(32, i, 33, i + 1, 0.8f);
        }
        for (int i = 0; i < 5; i++) { // gum spots
            int x = (int) (Noise.rnd(i, 1, (int) seed) * 62), y = (int) (Noise.rnd(i, 2, (int) seed) * 62);
            m.shade(x, y, x + 2, y + 1, 0.75f);
        }
        return m.texture();
    }

    private static Texture pavers(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 30;
        for (int y = 0; y < 64; y++) {
            int row = y / 8;
            for (int x = 0; x < 64; x++) {
                int sx = (x + (row & 1) * 8) % 16;
                boolean joint = (y % 8) == 0 || sx == 0;
                float v = Noise.rnd((x + (row & 1) * 8) / 16, row, sd);
                int base = v < 0.33f ? 0xFFC88A6A : (v < 0.66f ? 0xFFB87A5C : 0xFFD49C7C);
                m.px[y * 64 + x] = joint ? 0xFF8A7A6C : mul(base, 0.92f + Noise.rnd(x, y, sd + 1) * 0.12f);
            }
        }
        return m.texture();
    }

    private static Texture concrete(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFBDB9B0);
        m.grain(0, 0, 64, 64, 0.08f, 0.05f, (int) seed + 40);
        m.frame(0, 0, 64, 64, 1, 0xFF8E8A82);
        return m.texture();
    }

    private static Texture curb(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFD4D2CA);
        m.grain(0, 0, 64, 64, 0.05f, 0.05f, (int) seed + 50);
        m.rect(0, 0, 64, 3, 0xFFE8E6DE);
        for (int x = 0; x < 64; x += 32) m.shade(x, 0, x + 1, 64, 0.75f);
        m.shade(0, 48, 64, 64, 0.85f);
        return m.texture();
    }

    private static Texture grass(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 60;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float k = 0.62f + n * 0.35f + g * 0.18f;
                boolean blade = Noise.rnd(x, y, sd + 2) > 0.8f;
                m.px[y * 64 + x] = blade ? rgb((int) (110 * k), (int) (170 * k), (int) (60 * k))
                        : rgb((int) (70 * k), (int) (128 * k), (int) (48 * k));
            }
        return m.texture();
    }

    private static Texture sand(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 70;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float ripple = (float) Math.sin((x * 0.3 + y * 0.9 + n * 6.0)) * 0.03f;
                float k = 0.86f + n * 0.1f + g * 0.07f + ripple;
                m.px[y * 64 + x] = rgb((int) (238 * k), (int) (220 * k), (int) (178 * k));
            }
        return m.texture();
    }

    private static Texture water(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 80;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd);
                float wv = (float) Math.sin((x / 64.0) * Math.PI * 4 + (y / 64.0) * Math.PI * 2 + n * 5) * 0.5f + 0.5f;
                int c = mix(0xFF1E8FA8, 0xFF48C4C8, n * 0.6f + wv * 0.3f);
                if (wv > 0.93f && n > 0.45f) c = mix(c, 0xFFE8FFFF, 0.6f);
                m.px[y * 64 + x] = c;
            }
        return m.texture();
    }

    private static Texture roof(long seed, int kind) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 90 + kind;
        if (kind == 0) {
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++) {
                    int v = (int) (120 + Noise.rnd(x, y, sd) * 70 + Noise.fbm(x, y, 64, 64, sd) * 30);
                    m.px[y * 64 + x] = rgb(v, v - 4, v - 10);
                }
        } else if (kind == 1) {
            m.fill(0xFFE4E6E4);
            m.grain(0, 0, 64, 64, 0.06f, 0.03f, sd);
            for (int x = 0; x < 64; x += 16) m.shade(x, 0, x + 1, 64, 0.85f);
        } else {
            m.fill(0xFF4A4A4E);
            m.grain(0, 0, 64, 64, 0.08f, 0.06f, sd);
            for (int y = 0; y < 64; y += 21) m.shade(0, y, 64, y + 1, 0.7f);
        }
        return m.texture();
    }

    private static Texture parking(long seed) {
        Img m = new Img(64, 64);
        Texture a = asphalt(seed + 3, false);
        System.arraycopy(a.px, 0, m.px, 0, 64 * 64);
        m.rect(0, 0, 3, 64, 0xFFE8E8E0);       // stall line
        m.rect(0, 0, 64, 3, 0xFFE8E8E0);       // end line
        return m.texture();
    }

    private static Texture solid(int c, long seed) {
        Img m = new Img(8, 8);
        m.fill(c);
        m.grain(0, 0, 8, 8, 0.0f, 0.04f, (int) seed);
        return m.texture();
    }

    private static Texture trunk(long seed) {
        Img m = new Img(32, 64);
        int sd = (int) seed + 100;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 32; x++) {
                float band = ((y + (x / 4)) % 6) < 2 ? 0.78f : 1f;
                float k = (0.85f + Noise.rnd(x, y, sd) * 0.15f) * band;
                m.px[y * 32 + x] = mul(0xFF9A8670, k);
            }
        return m.texture();
    }

    /** One palm frond: rib along u, leaflets fanning out, transparent elsewhere. */
    private static Texture frond(long seed) {
        Img m = new Img(64, 32);
        m.fill(0);
        int sd = (int) seed + 110;
        for (int x = 0; x < 64; x++) {
            float t = x / 63f;
            float width = (float) Math.sin(t * Math.PI) * 14f * (1f - t * 0.3f) + 1f;
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < width; i++) {
                    int y = 16 + side * i;
                    // leaflet gaps
                    if (((x + i) % 5) == 0 && i > 2) continue;
                    float k = 0.75f + Noise.rnd(x, y, sd) * 0.3f - i / width * 0.25f;
                    m.set(x, y, mul(0xFF4E8A3A, k));
                }
            }
            m.set(x, 16, 0xFF8A9A50);
        }
        return m.texture();
    }

    private static Texture hedge(long seed) {
        Img m = new Img(64, 64);
        int sd = (int) seed + 120;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float n = Noise.fbm(x, y, 64, 64, sd), g = Noise.rnd(x, y, sd + 1);
                float k = 0.55f + n * 0.4f + g * 0.25f;
                m.px[y * 64 + x] = rgb((int) (60 * k), (int) (120 * k), (int) (52 * k));
            }
        return m.texture();
    }

    private static Texture metal(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFB4B8BA);
        m.grain(0, 0, 64, 64, 0.04f, 0.03f, (int) seed + 130);
        for (int y = 8; y < 56; y += 3) m.rect(8, y, 56, y + 1, 0xFF7A7E80);
        m.frame(0, 0, 64, 64, 2, 0xFF8A8E90);
        return m.texture();
    }

    private static Texture planter(long seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFE0D8C8);
        m.grain(0, 0, 64, 64, 0.05f, 0.05f, (int) seed + 140);
        m.rect(0, 0, 64, 4, 0xFFF0EADC);
        return m.texture();
    }

    private static Texture umbrella() {
        Img m = new Img(64, 64);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++)
                m.px[y * 64 + x] = ((x / 8) & 1) == 0 ? 0xFFE8483C : 0xFFF4F0E6;
        return m.texture();
    }

    private static Texture shadow() {
        Img m = new Img(32, 32);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                float dx = (x + 0.5f) / 16f - 1f, dy = (y + 0.5f) / 16f - 1f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = d >= 1f ? 0f : Math.min(1f, (1f - d) * 2.5f) * 0.7f;
                m.px[y * 32 + x] = ((int) (a * 255) << 24) | 0x101418;
            }
        return m.texture();
    }
}
