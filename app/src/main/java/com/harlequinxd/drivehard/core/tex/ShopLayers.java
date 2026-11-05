package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/**
 * Shopfronts painted as a stack of layers (the house kits' way): one 256 x 128 texture is one
 * ground-floor unit, 5.5 m wide and 4.6 m tall, drawn in metres so its proportions hold.
 *
 * <ol>
 * <li><b>Shell</b>: clad pilasters at the unit's edges, a stone threshold, a tiled stall riser.</li>
 * <li><b>Interior</b>: what's behind the glass, lit from inside: shelves of goods, dining tables
 * and a counter, clothes rails, bookcases, bottles, pachinko machines, property listings, a
 * clinic's waiting room.</li>
 * <li><b>Glazing</b>: aluminium frames and mullions, the glass reflecting sky and street, a
 * sliding door with its sensor box (or a heavy bar door, or noren at a traditional entrance).</li>
 * <li><b>Frontage</b>: by kind - a timber lattice front with noren and lanterns; an open
 * grocer's front with produce in tiers of crates; neon tubes; a plastic-food display case.</li>
 * <li><b>Signs</b>: the fascia box (lit face, shadowed underside, trim), kanji and romaji, a
 * striped awning with a scalloped valance, nobori banners, an A-frame board.</li>
 * <li><b>Weathering</b>: the shadow under the sign and awning, grime at the base, streaks.</li>
 * </ol>
 */
public final class ShopLayers {
    private ShopLayers() {}

    public static final int TW = 256, TH = 128;
    /** A unit's width and the height of the shop storey it fills (City.SHOP_H), metres. */
    static final float WM = 5.5f, HM = 4.6f;

    // heights (metres above the pavement)
    static final float STEP = 0.15f, RISER = 0.6f, HEAD = 2.85f, SIGN0 = 3.2f, SIGN1 = 4.2f;
    static final float PIL = 0.28f;

    // ------------------------------------------------------------------ metre canvas

    private static final class C {
        final Img m = new Img(TW, TH);
        int x(float s) { return Math.round(s / WM * TW); }
        int y(float h) { return Math.round((1f - h / HM) * TH); }
        void rect(float s0, float h0, float s1, float h1, int c) { m.rect(x(s0), y(h1), x(s1), y(h0), c); }
        void shade(float s0, float h0, float s1, float h1, float k) { m.shade(x(s0), y(h1), x(s1), y(h0), k); }
        void tint(float s0, float h0, float s1, float h1, int c, float t) { m.tint(x(s0), y(h1), x(s1), y(h0), c, t); }
        void grain(float s0, float h0, float s1, float h1, float a, float b, int seed) { m.grain(x(s0), y(h1), x(s1), y(h0), a, b, seed); }
    }

    private static final int[] CLADDING = {0xD8D4CC, 0xC8C0B4, 0x9A968E, 0x6E6A66, 0xE4E0D8, 0x8A7E70, 0x3A3836};

    // ------------------------------------------------------------------ one shopfront

    public static Texture shop(int cat, int seed, String[] nameOut) {
        JpTextures.Category c = JpTextures.CATEGORIES[cat];
        Random r = new Random(seed * 977L + cat * 31L + 7);
        C k = new C();
        String brand = JpTextures.brand(r);
        if (nameOut != null) nameOut[0] = brand + " " + c.label;
        int signBg = 0xFF000000 | c.palette[0], signFg = 0xFF000000 | c.palette[1 % c.palette.length];
        int clad = 0xFF000000 | CLADDING[r.nextInt(CLADDING.length)];
        boolean dark = c.style == JpTextures.NEON || c.style == JpTextures.BAR;
        // 1. shell: the wall the shop is set into, pilasters, threshold
        k.rect(0, 0, WM, HM, clad);
        k.grain(0, 0, WM, HM, 0.05f, 0.04f, seed);
        for (float s : new float[]{0f, WM - PIL}) {
            k.rect(s, 0, s + PIL, HM, mul(clad, 1.04f));
            // stone panel joints up the pilaster
            for (float h = 0.6f; h < HM; h += 0.6f) k.rect(s, h, s + PIL, h + 0.02f, mul(clad, 0.8f));
            k.shade(s + PIL - 0.04f, 0, s + PIL, HM, 0.75f);
        }
        k.rect(0, HM - 0.4f, WM, HM, mul(clad, 0.9f));                  // the slab of the floor above
        k.shade(0, HM - 0.42f, WM, HM - 0.4f, 0.6f);
        // 2-4. the frontage, by kind
        switch (c.style) {
            case JpTextures.NOREN: traditional(k, r, c, seed, brand); break;
            case JpTextures.PRODUCE: openFront(k, r, c, cat, seed); break;
            case JpTextures.BAR: barFront(k, r, c, seed); break;
            case JpTextures.NEON: neonFront(k, r, c, seed); break;
            case JpTextures.SHUTTER: shutterFront(k, r); break;
            default: glassFront(k, r, c, cat, seed); break;
        }
        // 5. the sign
        signBox(k, r, c, seed, brand, signBg, signFg, dark);
        if (awning(c, r)) awning(k, r, c);
        if ((c.style == JpTextures.NOREN || c.style == JpTextures.KONBINI || c.style == JpTextures.PRODUCE) && r.nextInt(3) > 0) nobori(k, r, c, seed);
        if ((c.style == JpTextures.NOREN || c.style == JpTextures.GLASS) && r.nextInt(3) == 0) aFrame(k, r, c, seed);
        // 6. weathering
        k.tint(0, 0, WM, STEP, 0xFF8A8680, 0.5f);
        k.rect(0, 0, WM, 0.04f, 0xFF5A5854);
        for (int x = 0; x < TW; x++) {
            int y0 = k.y(0.5f), y1 = TH;
            for (int y = y0; y < y1; y++) {
                float g = (y - y0) / (float) (y1 - y0) * 0.12f;
                k.m.set(x, y, mul(k.m.get(x, y), 1f - g * (0.6f + 0.4f * Noise.rnd(x, y, seed + 3))));
            }
        }
        for (int i = 0; i < 3; i++) {
            float s = PIL + r.nextFloat() * (WM - 2 * PIL);
            streak(k, s, SIGN0 - 0.02f, 0.5f + r.nextFloat() * 0.8f, 0.08f);
        }
        return k.m.texture();
    }

    // ------------------------------------------------------------------ frontages

    /** A modern glass front: stall riser, the lit interior behind framed glass, a sliding door. */
    private static void glassFront(C k, Random r, JpTextures.Category c, int cat, int seed) {
        String l = c.label;
        boolean bright = c.style == JpTextures.KONBINI || l.contains("DRUG") || l.contains("100") || l.equals("CLINIC") || l.contains("ELECTRON");
        int frame = r.nextBoolean() ? 0xFF2A2C30 : 0xFFB4B8BC;
        float a = PIL + 0.05f, b = WM - PIL - 0.05f;
        // stall riser: small tiles
        int riser = mix(0xFF000000 | c.palette[0], 0xFF8A8A88, 0.6f);
        k.rect(a, STEP, b, RISER, riser);
        for (float s = a; s < b; s += 0.1f) k.rect(s, STEP, s + 0.01f, RISER, mul(riser, 0.8f));
        k.rect(a, RISER - 0.03f, b, RISER, mul(riser, 1.2f));
        // the interior seen through the glass
        int doorAt = r.nextInt(3);
        float dw = 1.7f, d0 = doorAt == 0 ? a + 0.2f : doorAt == 1 ? (WM - dw) / 2 : b - dw - 0.2f, d1 = d0 + dw;
        interior(k, r, c, cat, a, STEP, b, HEAD, bright, seed);
        // windows: glass sheen, mullions, the door
        glass(k, a, RISER, d0, HEAD, bright);
        glass(k, d1, RISER, b, HEAD, bright);
        glass(k, d0, STEP, d1, HEAD, bright);
        k.rect(a, HEAD, b, HEAD + 0.12f, frame);                               // head
        k.rect(a, HEAD + 0.12f, b, SIGN0, mul(frame, 0.85f));                  // transom panel
        k.rect(a, RISER - 0.04f, d0, RISER, frame);
        k.rect(d1, RISER - 0.04f, b, RISER, frame);
        for (float s : new float[]{a, d0, d1, b - 0.06f}) k.rect(s, STEP, s + 0.06f, HEAD, frame);
        // mullions in the side windows
        if (d0 - a > 1.6f) k.rect((a + d0) / 2 - 0.03f, RISER, (a + d0) / 2 + 0.03f, HEAD, frame);
        if (b - d1 > 1.6f) k.rect((d1 + b) / 2 - 0.03f, RISER, (d1 + b) / 2 + 0.03f, HEAD, frame);
        // the sliding door: two leaves, push bars, sensor box above
        k.rect((d0 + d1) / 2 - 0.02f, STEP, (d0 + d1) / 2 + 0.02f, HEAD - 0.1f, frame);
        k.rect(d0 + 0.05f, 0.95f, d0 + 0.08f, 1.25f, 0xFFD8D8D8);
        k.rect(d1 - 0.08f, 0.95f, d1 - 0.05f, 1.25f, 0xFFD8D8D8);
        k.rect((d0 + d1) / 2 - 0.25f, HEAD + 0.12f, (d0 + d1) / 2 + 0.25f, HEAD + 0.24f, 0xFF3A3C40);
        // posters and notices taped inside the glass
        int posters = c.style == JpTextures.KONBINI || c.style == JpTextures.OFFICE ? 2 + r.nextInt(3) : r.nextInt(2);
        for (int i = 0; i < posters; i++) {
            boolean left = d0 - a > 0.8f && (r.nextBoolean() || b - d1 <= 0.8f);
            if (!left && b - d1 <= 0.8f) continue;
            float lo = left ? a + 0.12f : d1 + 0.12f, hi = left ? d0 - 0.54f : b - 0.54f;
            float s0 = lo + r.nextFloat() * Math.max(0f, hi - lo);
            float h0 = 1.3f + r.nextFloat() * 0.8f;
            poster(k, r, s0, h0, s0 + 0.42f, h0 + 0.6f, c);
        }
        if (l.equals("BARBER")) barberPole(k, b + 0.02f);
    }

    /** A traditional front: dark timber, lattice (koshi) windows, an entrance hung with noren. */
    private static void traditional(C k, Random r, JpTextures.Category c, int seed, String brand) {
        int wood = new int[]{0xFF4A3424, 0xFF3A2A1E, 0xFF5E4430, 0xFF2E2420}[r.nextInt(4)];
        float a = PIL, b = WM - PIL;
        k.rect(a, STEP, b, SIGN0, wood);
        for (int y = k.y(SIGN0); y < k.y(STEP); y++)
            for (int x = k.x(a); x < k.x(b); x++)
                k.m.set(x, y, mul(k.m.get(x, y), 0.92f + 0.12f * Noise.rnd(x / 3, y, seed + 11)));
        float e0 = (WM - 1.6f) / 2, e1 = e0 + 1.6f;
        // lattice windows either side, a warm lit room behind
        for (float[] w : new float[][]{{a + 0.2f, e0 - 0.25f}, {e1 + 0.25f, b - 0.2f}}) {
            if (w[1] - w[0] < 0.4f) continue;
            k.rect(w[0], 0.8f, w[1], 2.5f, 0xFFE8C88A);
            for (int y = k.y(2.5f); y < k.y(0.8f); y++)
                for (int x = k.x(w[0]); x < k.x(w[1]); x++) {
                    float t = (y - k.y(2.5f)) / (float) Math.max(1, k.y(0.8f) - k.y(2.5f));
                    k.m.set(x, y, mix(0xFFF0D8A0, 0xFF8A5A30, t * 0.8f));
                }
            for (float s = w[0]; s < w[1]; s += 0.07f) k.rect(s, 0.8f, s + 0.035f, 2.5f, mul(wood, 0.8f));
            k.rect(w[0], 2.45f, w[1], 2.55f, mul(wood, 0.7f));
            k.rect(w[0], 0.75f, w[1], 0.85f, mul(wood, 1.2f));
        }
        // the entrance: a dark doorway, noren hung across it
        k.rect(e0, STEP, e1, 2.4f, 0xFF1A1410);
        int noren = 0xFF000000 | c.palette[0], ink = 0xFF000000 | c.palette[1 % c.palette.length];
        int panels = 3 + r.nextInt(2);
        float pw = (e1 - e0) / panels;
        for (int i = 0; i < panels; i++) {
            float s0 = e0 + i * pw + 0.015f, s1 = e0 + (i + 1) * pw - 0.015f;
            k.rect(s0, 1.45f, s1, 2.4f, noren);
            k.shade(s1 - 0.03f, 1.45f, s1, 2.4f, 0.8f);
            // a big glyph down the middle of each panel
            Kanji.glyph(k.m.px, TW, TH, seed * 7 + i, k.x((s0 + s1) / 2) - 7, k.y(2.2f), 7, 2, ink);
        }
        k.rect(e0 - 0.05f, 2.38f, e1 + 0.05f, 2.45f, 0xFF9A7A50);           // the noren rod
        // a food display case or a menu board beside the door
        if (r.nextBoolean()) foodCase(k, r, a + 0.15f, Math.max(a + 0.9f, e0 - 0.35f));
        else menuBoard(k, r, e1 + 0.15f, Math.min(b - 0.1f, e1 + 0.75f));
        if (c.lanterns || r.nextInt(3) == 0) { lantern(k, a + 0.25f, seed); lantern(k, b - 0.25f, seed + 1); }
    }

    /** An open grocer's front: the shop open to the street, produce in tiers, lamps hanging. */
    private static void openFront(C k, Random r, JpTextures.Category c, int cat, int seed) {
        float a = PIL, b = WM - PIL;
        // the shop inside: dark, with hanging lamps
        for (int y = k.y(SIGN0); y < k.y(STEP); y++)
            for (int x = k.x(a); x < k.x(b); x++) {
                float t = (y - k.y(SIGN0)) / (float) Math.max(1, k.y(STEP) - k.y(SIGN0));
                k.m.set(x, y, mix(0xFF2A2420, 0xFF4A4038, t));
            }
        for (float s = a + 0.5f; s < b - 0.2f; s += 1.1f) {
            k.rect(s - 0.015f, 2.4f, s + 0.015f, SIGN0, 0xFF1A1A1A);
            k.rect(s - 0.12f, 2.25f, s + 0.12f, 2.4f, 0xFFF8EEC8);
            k.tint(s - 0.45f, 1.4f, s + 0.45f, 2.25f, 0xFFF8E8B0, 0.18f);
        }
        String l = c.label;
        int[] goods = l.equals("SAKANA") ? new int[]{0xFFC8D0D8, 0xFF8A98A8, 0xFFE8E8E4, 0xFFD8A098, 0xFF6A7A8A}
                : l.equals("NIKU") ? new int[]{0xFFC84848, 0xFFE89090, 0xFFF0E0D0, 0xFFA83030}
                : l.equals("FLOWERS") ? new int[]{0xFFE84A6A, 0xFFF2D060, 0xFFF4F4F4, 0xFFB86AD8, 0xFFF08A3C, 0xFF6ACB6A}
                : new int[]{0xFFE84A2A, 0xFF6AB83A, 0xFFF2C030, 0xFFF08A3C, 0xFF3E8A3E, 0xFFE8E0C8, 0xFF8A4AA0};
        // three tiers of crates stepping back
        float[][] tiers = {{STEP, 0.75f}, {0.75f, 1.3f}, {1.3f, 1.8f}};
        for (int t = 0; t < tiers.length; t++) {
            float h0 = tiers[t][0], h1 = tiers[t][1], inset = t * 0.1f;
            for (float s = a + 0.05f + inset; s < b - 0.4f - inset; s += 0.42f) {
                k.rect(s, h0, s + 0.4f, h1 - 0.05f, 0xFF8A6A44);                // crate
                k.rect(s, h1 - 0.12f, s + 0.4f, h1 - 0.05f, mul(0xFF8A6A44, 1.2f));
                int g = goods[r.nextInt(goods.length)];
                for (int y = k.y(h1 + 0.02f); y < k.y(h1 - 0.12f); y++)
                    for (int x = k.x(s + 0.02f); x < k.x(s + 0.38f); x++)
                        if (Noise.rnd(x, y, seed + t) > 0.15f) k.m.set(x, y, mul(g, 0.75f + 0.4f * Noise.rnd(x / 2, y / 2, seed + 7)));
                if (r.nextInt(3) == 0) k.rect(s + 0.12f, h1 - 0.05f, s + 0.28f, h1 + 0.08f, 0xFFF4F4F0);   // a price card
            }
        }
    }

    /** A bar: dark tiled front, a heavy door, a small lit sign, a frosted glass slit. */
    private static void barFront(C k, Random r, JpTextures.Category c, int seed) {
        float a = PIL, b = WM - PIL;
        int wall = new int[]{0xFF2A2624, 0xFF3A3028, 0xFF1E1E22, 0xFF4A3A2E}[r.nextInt(4)];
        k.rect(a, STEP, b, SIGN0, wall);
        for (int y = k.y(SIGN0); y < k.y(STEP); y++)
            for (int x = k.x(a); x < k.x(b); x++)
                if (y % 6 == 0 || (x + (y / 6) * 5) % 14 == 0) k.m.set(x, y, mul(k.m.get(x, y), 0.75f));
        float d0 = a + 0.8f + r.nextFloat() * (b - a - 2.4f), d1 = d0 + 0.95f;
        int door = new int[]{0xFF5A3A24, 0xFF2A2A2E, 0xFF6A4A2A}[r.nextInt(3)];
        k.rect(d0, STEP, d1, 2.25f, door);
        k.rect(d0 + 0.08f, 0.4f, d1 - 0.08f, 2.1f, mul(door, 0.85f));
        k.rect(d1 - 0.18f, 1.0f, d1 - 0.12f, 1.4f, 0xFFC8A060);
        k.rect(d0 - 0.05f, 2.25f, d1 + 0.05f, 2.32f, mul(door, 0.6f));
        // the little lit sign by the door
        float s0 = d1 + 0.25f;
        if (s0 + 0.6f < b) {
            k.rect(s0, 1.5f, s0 + 0.55f, 2.2f, 0xFFF4EED8);
            Kanji.column(k.m.px, TW, TH, seed, 2, k.x(s0 + 0.27f) - 6, k.y(2.12f), 6, 2, 0xFF000000 | c.palette[0]);
            k.tint(s0 - 0.2f, 1.3f, s0 + 0.75f, 2.4f, 0xFFF4E0B0, 0.15f);
        }
        k.rect(a + 0.2f, 1.9f, d0 - 0.2f, 2.3f, 0xFFD8D0B8);                  // a frosted slit window, warm inside
        k.tint(a + 0.2f, 1.9f, d0 - 0.2f, 2.3f, 0xFFE8B070, 0.3f);
    }

    /** Pachinko / karaoke / game centre: a dark front blazing with light, neon tubes, a bright entrance. */
    private static void neonFront(C k, Random r, JpTextures.Category c, int seed) {
        float a = PIL, b = WM - PIL;
        k.rect(a, STEP, b, SIGN0, 0xFF141418);
        int n1 = 0xFF000000 | c.palette[0], n2 = 0xFF000000 | c.palette[1 % c.palette.length];
        float d0 = (WM - 2f) / 2, d1 = d0 + 2f;
        // the bright inside: rows of machines
        for (int y = k.y(2.6f); y < k.y(STEP); y++)
            for (int x = k.x(d0); x < k.x(d1); x++) {
                float t = (y - k.y(2.6f)) / (float) Math.max(1, k.y(STEP) - k.y(2.6f));
                k.m.set(x, y, mix(0xFFF8F0E0, mix(n1, 0xFFFFFFFF, 0.5f), 0.3f + t * 0.3f));
            }
        for (float s = d0 + 0.1f; s < d1 - 0.2f; s += 0.3f) {
            k.rect(s, 0.6f, s + 0.24f, 1.6f, mix(n2, 0xFF202028, 0.4f));
            k.rect(s + 0.04f, 1.2f, s + 0.2f, 1.5f, 0xFFF8F8E0);
            k.rect(s + 0.04f, 0.7f, s + 0.2f, 0.75f, n1);
        }
        glass(k, d0, STEP, d1, 2.6f, true);
        k.rect(d0 - 0.06f, STEP, d0, 2.6f, 0xFFB8BCC0);
        k.rect(d1, STEP, d1 + 0.06f, 2.6f, 0xFFB8BCC0);
        k.rect(d0, 2.6f, d1, 2.68f, 0xFFB8BCC0);
        // neon tubes: borders and a big glyph column each side
        neonRect(k, a + 0.1f, 0.3f, d0 - 0.15f, SIGN0 - 0.15f, n1);
        neonRect(k, d1 + 0.15f, 0.3f, b - 0.1f, SIGN0 - 0.15f, n2);
        Kanji.column(k.m.px, TW, TH, seed, 3, k.x((a + d0) / 2) - 7, k.y(2.85f), 7, 2, n2);
        Kanji.column(k.m.px, TW, TH, seed + 5, 3, k.x((d1 + b) / 2) - 7, k.y(2.85f), 7, 2, n1);
        // chase lights along the top
        for (float s = a + 0.1f; s < b; s += 0.18f) k.rect(s, SIGN0 - 0.1f, s + 0.06f, SIGN0 - 0.04f, (int) (s * 10) % 2 == 0 ? 0xFFFFF0A0 : n1);
    }

    private static void shutterFront(C k, Random r) {
        float a = PIL, b = WM - PIL;
        int c = new int[]{0xFFB8BCBE, 0xFF9AA0A4, 0xFFC8C4B8}[r.nextInt(3)];
        for (int y = k.y(SIGN0 - 0.2f); y < k.y(STEP); y++)
            for (int x = k.x(a); x < k.x(b); x++) {
                int t = (y - k.y(SIGN0 - 0.2f)) % 4;
                k.m.set(x, y, mul(c, t == 0 ? 1.1f : t == 3 ? 0.8f : 0.96f));
            }
        k.rect(a, SIGN0 - 0.2f, b, SIGN0, mul(c, 0.7f));
        k.rect(WM / 2 - 0.2f, 0.25f, WM / 2 + 0.2f, 0.32f, mul(c, 0.55f));
        k.grain(a, STEP, b, SIGN0, 0.06f, 0.03f, r.nextInt());
    }

    // ------------------------------------------------------------------ interiors

    /**
     * What's behind the glass for this kind of shop, lit from inside: brighter toward the ceiling
     * lights, darker at the back.
     */
    private static void interior(C k, Random r, JpTextures.Category c, int cat, float a, float h0, float b, float h1, boolean bright, int seed) {
        String l = c.label;
        int light = bright ? 0xFFF4F8F8 : l.equals("CAFE") || l.equals("KISSATEN") || l.equals("BAKERY") || l.equals("CAKES") ? 0xFFF0D8A8 : 0xFFE8E4DA;
        int back = mix(light, 0xFF3A3632, bright ? 0.25f : 0.55f);
        for (int y = k.y(h1); y < k.y(h0); y++) {
            float t = (y - k.y(h1)) / (float) Math.max(1, k.y(h0) - k.y(h1));
            int col = mix(light, back, 0.3f + 0.5f * Math.abs(t - 0.25f));
            for (int x = k.x(a); x < k.x(b); x++) k.m.set(x, y, col);
        }
        // ceiling lights
        for (float s = a + 0.4f; s < b; s += 0.9f) k.rect(s, h1 - 0.12f, s + 0.5f, h1 - 0.06f, 0xFFFFFFF4);
        int[] pal = c.palette;
        if (c.style == JpTextures.KONBINI || l.contains("DRUG") || l.contains("100") || l.equals("ELECTRONICS") || l.equals("STATIONERY")) {
            shelves(k, r, a, h0, b, h1, seed, bright);
        } else if (l.equals("BOOKS")) {
            for (float hh = 0.5f; hh < 2.3f; hh += 0.42f)
                for (float s = a; s < b; s += 0.05f + r.nextFloat() * 0.03f)
                    k.rect(s, hh, s + 0.04f, hh + 0.28f + r.nextFloat() * 0.08f, mix(0xFF000000 | r.nextInt(0xFFFFFF), 0xFF3A3632, 0.35f));
        } else if (l.equals("FASHION") || l.equals("KIMONO")) {
            for (float s = a + 0.2f; s < b - 0.3f; s += 1.3f) {
                k.rect(s, 2.1f, s + 1.0f, 2.13f, 0xFFB8B8B8);
                for (float g = s + 0.05f; g < s + 0.95f; g += 0.1f)
                    k.rect(g, 1.0f + r.nextFloat() * 0.3f, g + 0.08f, 2.08f, mix(0xFF000000 | r.nextInt(0xFFFFFF), 0xFF2A2A2A, 0.3f));
            }
            mannequin(k, (a + b) / 2 + (r.nextFloat() - 0.5f) * 1.5f, mix(0xFF000000 | pal[0], 0xFF888888, 0.3f));
        } else if (l.equals("SAKE") || l.equals("OKOME") || l.equals("TEA")) {
            for (float hh = 0.6f; hh < 2.4f; hh += 0.5f)
                for (float s = a + 0.05f; s < b - 0.1f; s += 0.13f) {
                    int col = new int[]{0xFF2A5A2A, 0xFF4A2A1A, 0xFF1A2A4A, 0xFFE8E4D8}[r.nextInt(4)];
                    k.rect(s, hh, s + 0.08f, hh + 0.32f, col);
                    k.rect(s + 0.02f, hh + 0.32f, s + 0.06f, hh + 0.4f, col);
                }
        } else if (c.style == JpTextures.OFFICE) {
            if (l.equals("FUDOSAN")) {
                for (float hh = 0.9f; hh < 2.6f; hh += 0.45f)
                    for (float s = a + 0.1f; s < b - 0.35f; s += 0.38f) poster(k, r, s, hh, s + 0.32f, hh + 0.4f, c);
            } else {
                // a waiting room: a counter, chairs, frosted glass with a sign
                k.rect(a + 0.3f, 0.6f, a + 2.2f, 1.1f, 0xFFE8E8E4);
                for (float s = a + 2.6f; s < b - 0.4f; s += 0.6f) k.rect(s, 0.45f, s + 0.45f, 0.9f, mix(0xFF000000 | pal[0], 0xFF8A8A8A, 0.4f));
                k.tint(a, 0.9f, b, 1.9f, 0xFFF4F4F4, 0.6f);                     // frosted band
                if (l.equals("CLINIC") || l.contains("DRUG")) {
                    float cx = (a + b) / 2;
                    k.rect(cx - 0.3f, 1.25f, cx + 0.3f, 1.55f, 0xFF2A9A4A);
                    k.rect(cx - 0.12f, 1.07f, cx + 0.12f, 1.73f, 0xFF2A9A4A);
                }
            }
        } else if (l.equals("BAKERY") || l.equals("CAKES") || l.equals("WAGASHI")) {
            // a glass display counter of bread or cakes, shelves behind
            shelves(k, r, a, 1.2f, b, h1, seed, true);
            k.rect(a + 0.2f, h0, b - 0.2f, 1.1f, 0xFFE8E0D0);
            for (float s = a + 0.3f; s < b - 0.4f; s += 0.22f)
                k.rect(s, 0.85f, s + 0.16f, 0.98f, l.equals("CAKES") ? new int[]{0xFFF4E8E8, 0xFFE8B0B8, 0xFF7A4A3A, 0xFFF2D8A0}[r.nextInt(4)] : 0xFFC8904A);
        } else if (l.equals("CAFE") || l.equals("KISSATEN") || l.equals("GYUDON") || l.equals("CURRY") || l.equals("BENTO")) {
            // dining: a counter with stools, tables and chairs, a menu on the back wall
            k.rect(a, 1.0f, b, 1.08f, 0xFF6A4A30);
            k.rect(a, h0, b, 1.0f, mul(0xFF6A4A30, 0.7f));
            for (float s = a + 0.3f; s < b - 0.3f; s += 0.55f) k.rect(s, 0.5f, s + 0.3f, 0.6f, 0xFF3A3A3A);
            k.rect(a + 0.4f, 1.9f, b - 0.4f, 2.35f, 0xFF2A2A2A);
            for (float s = a + 0.5f; s < b - 0.6f; s += 0.5f) k.rect(s, 2.0f, s + 0.35f, 2.25f, mix(0xFF000000 | r.nextInt(0xFFFFFF), 0xFFF0E8D0, 0.5f));
        } else if (l.equals("BARBER") || l.equals("HAIR SALON") || l.equals("MEGANE")) {
            for (float s = a + 0.3f; s < b - 0.6f; s += 1.1f) {
                k.rect(s, 1.0f, s + 0.7f, 2.2f, 0xFFC8D4DC);                      // mirrors
                k.rect(s + 0.1f, 0.5f, s + 0.6f, 0.95f, 0xFF2A2A2E);              // chairs
            }
        } else {
            shelves(k, r, a, h0, b, h1, seed, bright);
        }
    }

    /** Store shelving: three or four shelves of colourful goods, lit from above. */
    private static void shelves(C k, Random r, float a, float h0, float b, float h1, int seed, boolean bright) {
        for (float hh = h0 + 0.25f; hh < h1 - 0.5f; hh += 0.45f) {
            k.rect(a, hh, b, hh + 0.04f, 0xFFC8C8C4);
            for (float s = a + 0.02f; s < b - 0.08f; s += 0.06f + r.nextFloat() * 0.05f) {
                float gh = 0.12f + r.nextFloat() * 0.22f;
                int col = mix(0xFF000000 | Color.saturated(r), bright ? 0xFFFFFFFF : 0xFF4A4642, bright ? 0.15f : 0.35f);
                k.rect(s, hh + 0.04f, s + 0.05f, hh + 0.04f + gh, col);
                k.rect(s, hh + 0.04f + gh - 0.02f, s + 0.05f, hh + 0.04f + gh, mul(col, 1.2f));
            }
        }
    }

    private static final class Color {
        static int saturated(Random r) {
            float h = r.nextFloat() * 6f, s = 0.5f + r.nextFloat() * 0.5f, v = 0.6f + r.nextFloat() * 0.4f;
            int i = (int) h;
            float f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
            float rr, gg, bb;
            switch (i % 6) {
                case 0: rr = v; gg = t; bb = p; break;
                case 1: rr = q; gg = v; bb = p; break;
                case 2: rr = p; gg = v; bb = t; break;
                case 3: rr = p; gg = q; bb = v; break;
                case 4: rr = t; gg = p; bb = v; break;
                default: rr = v; gg = p; bb = q; break;
            }
            return ((int) (rr * 255) << 16) | ((int) (gg * 255) << 8) | (int) (bb * 255);
        }
    }

    private static void mannequin(C k, float s, int dress) {
        k.rect(s - 0.08f, 1.75f, s + 0.08f, 1.95f, 0xFFE8E4E0);            // head
        k.rect(s - 0.2f, 1.0f, s + 0.2f, 1.75f, dress);
        k.rect(s - 0.12f, 0.25f, s - 0.04f, 1.0f, 0xFFE8E4E0);
        k.rect(s + 0.04f, 0.25f, s + 0.12f, 1.0f, 0xFFE8E4E0);
    }

    /** Glass over whatever is behind it: the sky reflected at the top, the street at the bottom, a sheen. */
    private static void glass(C k, float s0, float h0, float s1, float h1, boolean bright) {
        int x0 = k.x(s0), x1 = k.x(s1), y0 = k.y(h1), y1 = k.y(h0);
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) Math.max(1, y1 - y0);
            for (int x = x0; x < x1; x++) {
                int c = k.m.get(x, y);
                c = mix(c, 0xFFC8DCEC, (1f - t) * (bright ? 0.12f : 0.28f));       // the sky
                c = mix(c, 0xFF3A3E44, t * t * (bright ? 0.1f : 0.25f));          // the street
                float d = ((x - x0) + (y - y0) * 1.2f) % 90f;
                if (d < 10f) c = mix(c, 0xFFFFFFFF, 0.14f);
                k.m.set(x, y, c);
            }
        }
    }

    // ------------------------------------------------------------------ signs and street things

    /** The fascia sign box: lit face, trim, shadowed underside; kanji and the name in romaji. */
    private static void signBox(C k, Random r, JpTextures.Category c, int seed, String brand, int bg, int fg, boolean dark) {
        float a = 0.05f, b = WM - 0.05f;
        int face = dark ? 0xFF121218 : bg;
        k.rect(a, SIGN0, b, SIGN1, face);
        // a soft vertical gradient: lit sign boxes are brighter in the middle
        for (int y = k.y(SIGN1); y < k.y(SIGN0); y++) {
            float t = (y - k.y(SIGN1)) / (float) Math.max(1, k.y(SIGN0) - k.y(SIGN1));
            float g = 1.08f - Math.abs(t - 0.45f) * 0.25f;
            for (int x = k.x(a); x < k.x(b); x++) k.m.set(x, y, mul(k.m.get(x, y), g));
        }
        int trim = dark ? 0xFF000000 | c.palette[0] : mul(bg, 0.6f);
        k.rect(a, SIGN1 - 0.05f, b, SIGN1, mul(trim, 1.2f));
        k.rect(a, SIGN0, b, SIGN0 + 0.05f, trim);
        k.rect(a, SIGN0 - 0.06f, b, SIGN0, 0xFF2A2828);                        // the box's underside
        k.shade(PIL, SIGN0 - 0.35f, WM - PIL, SIGN0 - 0.06f, 0.7f);             // its shadow on the front
        int ink = dark ? brightestOf(c.palette) : fg;
        int glyphs = 2 + r.nextInt(3);
        String name = r.nextInt(3) == 0 ? c.label : brand;
        if (name.length() > 14) name = name.substring(0, 14);
        int gy = k.y(SIGN1 - 0.12f);
        boolean centred = true;
        if (c.style == JpTextures.SHUTTER) {
            Kanji.row(k.m.px, TW, TH, seed, glyphs, 12, gy, 8, 2, mul(fg, 0.85f));
        } else if (r.nextBoolean()) {
            centred = false;
            int wk = Kanji.row(k.m.px, TW, TH, seed, glyphs, 10, gy, 8, 2, ink);
            int nw = PixelFont.width(name, 2);
            PixelFont.draw(k.m.px, TW, TH, name, Math.max(wk + 20, TW - 10 - nw), gy + 4, 2, dark ? 0xFFF4F0E6 : ink);
        } else {
            int n = Math.min(glyphs, 4);
            Kanji.row(k.m.px, TW, TH, seed, n, TW / 2 - (n * 27 - 3) / 2, gy - 4, 8, 3, ink);
            if (dark) neonRect(k, a + 0.08f, SIGN0 + 0.08f, b - 0.08f, SIGN1 - 0.08f, 0xFF000000 | c.palette[1 % c.palette.length]);
        }
        // a small projecting logo disc at one end
        if (centred && c.style != JpTextures.SHUTTER && r.nextInt(3) == 0) {
            float s = r.nextBoolean() ? 0.3f : WM - 0.75f;
            k.rect(s, SIGN0 + 0.12f, s + 0.42f, SIGN1 - 0.12f, mix(0xFFFFFFFF, bg, 0.2f));
            Kanji.glyph(k.m.px, TW, TH, seed + 99, k.x(s + 0.21f) - 7, k.y(SIGN1 - 0.22f), 7, 2, mul(bg, 0.7f));
        }
    }

    private static int brightestOf(int[] pal) {
        int best = pal[0], bl = -1;
        for (int p : pal) {
            int l = ((p >> 16) & 255) * 3 + ((p >> 8) & 255) * 6 + (p & 255);
            if (l > bl) { bl = l; best = p; }
        }
        return 0xFF000000 | best;
    }

    private static boolean awning(JpTextures.Category c, Random r) {
        if (c.style == JpTextures.PRODUCE) return true;
        if (c.style == JpTextures.GLASS) return r.nextInt(5) < 2;
        return false;
    }

    /** A fabric awning under the sign: stripes, a scalloped valance, its shadow on the front. */
    private static void awning(C k, Random r, JpTextures.Category c) {
        int c1 = 0xFF000000 | c.palette[0], c2 = r.nextBoolean() ? 0xFFF4F0E6 : 0xFF000000 | c.palette[1 % c.palette.length];
        float a = PIL - 0.05f, b = WM - PIL + 0.05f, top = SIGN0 - 0.06f, bot = HEAD - 0.05f;
        for (int y = k.y(top); y < k.y(bot); y++) {
            float t = (y - k.y(top)) / (float) Math.max(1, k.y(bot) - k.y(top));
            for (int x = k.x(a); x < k.x(b); x++) {
                boolean stripe = ((x - k.x(a)) / 9) % 2 == 0;
                k.m.set(x, y, mul(stripe ? c1 : c2, 1.05f - t * 0.2f));
            }
        }
        // scalloped valance
        int yb = k.y(bot);
        for (int x = k.x(a); x < k.x(b); x++) {
            int dip = (int) (Math.abs(Math.sin((x - k.x(a)) * Math.PI / 18)) * 3);
            for (int y = yb; y < yb + dip + 2 && y < TH; y++) k.m.set(x, y, mul(((x - k.x(a)) / 9) % 2 == 0 ? c1 : c2, 0.85f));
        }
        k.shade(PIL, bot - 0.45f, WM - PIL, bot - 0.1f, 0.72f);
    }

    /** Nobori: tall cloth banners on poles at the shop's edge, lettered top to bottom. */
    private static void nobori(C k, Random r, JpTextures.Category c, int seed) {
        int n = 1 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            float s = i == 0 ? PIL + 0.1f : WM - PIL - 0.45f;
            int col = 0xFF000000 | c.palette[(i + 1) % c.palette.length], ink = 0xFF000000 | c.palette[i % c.palette.length];
            if (col == ink) ink = 0xFFF4F0E6;
            k.rect(s + 0.33f, STEP, s + 0.36f, 2.65f, 0xFF9A9A9A);
            k.rect(s, 0.9f, s + 0.33f, 2.6f, col);
            k.rect(s, 2.5f, s + 0.33f, 2.6f, mul(col, 0.8f));
            Kanji.column(k.m.px, TW, TH, seed + i * 3, 3, k.x(s + 0.165f) - 6, k.y(2.45f), 6, 2, ink);
            k.shade(s + 0.28f, 0.9f, s + 0.33f, 2.5f, 0.85f);
        }
    }

    private static void aFrame(C k, Random r, JpTextures.Category c, int seed) {
        float s = WM / 2 + (r.nextFloat() - 0.5f) * 3f;
        k.rect(s, STEP, s + 0.5f, 0.95f, 0xFF2A2A2A);
        k.rect(s + 0.04f, 0.2f, s + 0.46f, 0.9f, 0xFF1E2E22);
        for (float h = 0.3f; h < 0.85f; h += 0.12f) k.rect(s + 0.08f, h, s + 0.08f + 0.1f + r.nextFloat() * 0.25f, h + 0.04f, 0xFFF4F0E6);
    }

    private static void foodCase(C k, Random r, float s0, float s1) {
        k.rect(s0, 0.7f, s1, 1.5f, 0xFF3A3A3A);
        k.rect(s0 + 0.04f, 0.74f, s1 - 0.04f, 1.46f, 0xFFF0EAD8);
        for (float s = s0 + 0.08f; s < s1 - 0.2f; s += 0.22f)
            for (float h : new float[]{0.8f, 1.12f}) {
                k.rect(s, h, s + 0.18f, h + 0.06f, 0xFFF4F4F4);
                k.rect(s + 0.02f, h + 0.06f, s + 0.16f, h + 0.14f, new int[]{0xFFC88A4A, 0xFFE8C060, 0xFFB84A2A, 0xFF6A9A4A, 0xFFE8E0C8}[r.nextInt(5)]);
            }
        k.tint(s0 + 0.04f, 0.74f, s1 - 0.04f, 1.46f, 0xFFFFFFFF, 0.12f);
    }

    private static void menuBoard(C k, Random r, float s0, float s1) {
        k.rect(s0, 1.0f, s1, 1.9f, 0xFF4A3424);
        k.rect(s0 + 0.04f, 1.04f, s1 - 0.04f, 1.86f, 0xFFF0E6D0);
        for (float h = 1.12f; h < 1.8f; h += 0.1f) k.rect(s0 + 0.08f, h, s0 + 0.08f + (s1 - s0 - 0.16f) * (0.5f + r.nextFloat() * 0.5f), h + 0.04f, 0xFF3A2A1E);
    }

    private static void lantern(C k, float s, int seed) {
        k.rect(s - 0.01f, 2.6f, s + 0.01f, 2.8f, 0xFF2A2A2A);
        int red = 0xFFC8282A;
        for (int y = k.y(2.6f); y < k.y(1.95f); y++)
            for (int x = k.x(s - 0.17f); x < k.x(s + 0.17f); x++) {
                float u = (x - k.x(s)) / (float) Math.max(1, k.x(s + 0.17f) - k.x(s));
                float shade = 1.15f - Math.abs(u) * 0.45f;
                k.m.set(x, y, mul((y - k.y(2.6f)) % 3 == 0 ? mul(red, 0.85f) : red, shade));
            }
        k.rect(s - 0.12f, 2.55f, s + 0.12f, 2.6f, 0xFF1A1A1A);
        k.rect(s - 0.12f, 1.95f, s + 0.12f, 2.0f, 0xFF1A1A1A);
        Kanji.glyph(k.m.px, TW, TH, seed + 31, k.x(s) - 4, k.y(2.42f), 5, 2, 0xFF1A1A1A);
    }

    private static void poster(C k, Random r, float s0, float h0, float s1, float h1, JpTextures.Category c) {
        k.rect(s0, h0, s1, h1, 0xFFF4F2EC);
        k.rect(s0, h1 - 0.12f, s1, h1, 0xFF000000 | c.palette[r.nextInt(c.palette.length)]);
        for (float h = h0 + 0.06f; h < h1 - 0.16f; h += 0.07f) k.rect(s0 + 0.04f, h, s0 + 0.04f + (s1 - s0 - 0.08f) * (0.4f + r.nextFloat() * 0.6f), h + 0.03f, 0xFF6A6A6A);
    }

    private static void barberPole(C k, float s) {
        for (int y = k.y(2.6f); y < k.y(1.4f); y++)
            for (int x = k.x(s); x < k.x(s + 0.16f); x++) {
                int band = ((y + x) / 4) % 3;
                k.m.set(x, y, band == 0 ? 0xFFD8282A : band == 1 ? 0xFFF4F4F4 : 0xFF2A4AC8);
            }
    }

    private static void neonRect(C k, float s0, float h0, float s1, float h1, int c) {
        int glow = mix(0xFF141418, c, 0.4f);
        k.rect(s0, h0, s1, h0 + 0.04f, c); k.rect(s0, h1 - 0.04f, s1, h1, c);
        k.rect(s0, h0, s0 + 0.04f, h1, c); k.rect(s1 - 0.04f, h0, s1, h1, c);
        k.rect(s0 - 0.03f, h0 - 0.03f, s1 + 0.03f, h0, glow); k.rect(s0 - 0.03f, h1, s1 + 0.03f, h1 + 0.03f, glow);
    }

    private static void streak(C k, float s, float h, float len, float strength) {
        int x = k.x(s), y0 = k.y(h), y1 = k.y(Math.max(0f, h - len));
        for (int y = y0; y < y1; y++) {
            float f = 1f - (y - y0) / (float) Math.max(1, y1 - y0);
            if (x >= 0 && x < TW && y >= 0 && y < TH) k.m.set(x, y, mul(k.m.get(x, y), 1f - strength * f));
        }
    }
}
