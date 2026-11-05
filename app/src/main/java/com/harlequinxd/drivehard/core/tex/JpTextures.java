package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;
import static com.harlequinxd.drivehard.core.tex.Img.rgb;

/**
 * Modern-Japan texture generators: shopfronts of ~50 kinds (128x64 = one ground-floor unit),
 * building facades (64x128 = one bay x one storey), roofs, signboards, vending machines and
 * railway textures. All names are invented.
 */
public final class JpTextures {
    private JpTextures() {}

    // ------------------------------------------------------------------ shop categories

    public static final int GLASS = 0, NOREN = 1, BAR = 2, NEON = 3, KONBINI = 4, PRODUCE = 5, SHUTTER = 6, OFFICE = 7;

    public static final class Category {
        public final String label;
        public final int style;
        final int[] palette;
        final boolean lanterns;
        Category(String label, int style, boolean lanterns, int... palette) {
            this.label = label; this.style = style; this.lanterns = lanterns; this.palette = palette;
        }
    }

    public static final Category[] CATEGORIES = {
            new Category("RAMEN", NOREN, false, 0xC8202A, 0x1A1A1C, 0xF2C230),
            new Category("UDON", NOREN, false, 0xF4F0E6, 0x1E2E58),
            new Category("SOBA", NOREN, false, 0x2E3A2E, 0xF4F0E6),
            new Category("SUSHI", NOREN, false, 0xF4F0E6, 0x1E2E58, 0x2A2A2A),
            new Category("IZAKAYA", NOREN, true, 0x3A2418, 0xC8202A, 0x1A1A1C),
            new Category("YAKITORI", NOREN, true, 0x1A1A1C, 0xC8202A),
            new Category("TEMPURA", NOREN, false, 0xE8D8B0, 0x3A2418),
            new Category("GYUDON", GLASS, false, 0xF07820, 0xF4F0E6),
            new Category("CURRY", GLASS, false, 0xF2C230, 0x8A3A1A),
            new Category("OKONOMIYAKI", NOREN, true, 0xF4F0E6, 0x8A2A1A),
            new Category("GYOZA", NOREN, false, 0xC8202A, 0xF2C230),
            new Category("BENTO", GLASS, false, 0x2E7A3E, 0xF4F0E6),
            new Category("KISSATEN", GLASS, false, 0x5A3A22, 0xE8D8B0),
            new Category("CAFE", GLASS, false, 0x2A2A2A, 0xF4F0E6, 0x3E6A4A),
            new Category("BAKERY", GLASS, false, 0xE8C890, 0x7A4A2A),
            new Category("CAKES", GLASS, false, 0xF4C8D8, 0xF4F0E6),
            new Category("WAGASHI", NOREN, false, 0x6A2A3A, 0xF4F0E6),
            new Category("TEA", NOREN, false, 0x3E6A3E, 0xE8E0C8),
            new Category("BAR", BAR, true, 0x1A1A1C, 0x5A2A1A),
            new Category("SNACK", BAR, false, 0x6A2A5A, 0x1A1A1C),
            new Category("CLUB", NEON, false, 0x7A2AC8, 0xFF4FA0),
            new Category("KARAOKE", NEON, false, 0xFF2A6A, 0x2AD0FF),
            new Category("PACHINKO", NEON, false, 0xF2C230, 0xFF2A2A),
            new Category("GAME CENTER", NEON, false, 0x2A6AFF, 0xFF4FA0),
            new Category("MAHJONG", BAR, false, 0x2E6A3E, 0xF4F0E6),
            new Category("DAILY MART", KONBINI, false, 0x2E8A4A, 0xF07820, 0xF4F0E6),
            new Category("HAPPY 24", KONBINI, false, 0x2A5AC8, 0xF4F0E6, 0x2A8AD0),
            new Category("LUCKY STORE", KONBINI, false, 0xD02030, 0xF2C230, 0xF4F0E6),
            new Category("DRUG", KONBINI, false, 0xF2D020, 0x2A4AA0),
            new Category("100 YEN", KONBINI, false, 0xE84A8A, 0xF4F0E6),
            new Category("SUPERMARKET", PRODUCE, false, 0xD02030, 0xF4F0E6),
            new Category("YAOYA", PRODUCE, false, 0x3E8A3E, 0xF4F0E6),
            new Category("SAKANA", PRODUCE, false, 0x2A5AC8, 0xF4F0E6),
            new Category("NIKU", PRODUCE, false, 0xC8202A, 0xF4F0E6),
            new Category("TOFU", NOREN, false, 0xF4F0E6, 0x2A2A2A),
            new Category("SAKE", GLASS, false, 0x1E2E58, 0xF4F0E6),
            new Category("OKOME", GLASS, false, 0xE8D8B0, 0x3A2418),
            new Category("FLOWERS", PRODUCE, false, 0xF4F0E6, 0x3E8A3E),
            new Category("BOOKS", GLASS, false, 0x1E2E58, 0xF4F0E6),
            new Category("STATIONERY", GLASS, false, 0xF4F0E6, 0x2A8AD0),
            new Category("ELECTRONICS", KONBINI, false, 0xD02030, 0xF2D020),
            new Category("FASHION", GLASS, false, 0x1A1A1C, 0xF4F0E6),
            new Category("SHOES", GLASS, false, 0x6A4A32, 0xF4F0E6),
            new Category("KIMONO", NOREN, false, 0x6A2A5A, 0xE8D8B0),
            new Category("MEGANE", GLASS, false, 0xF4F0E6, 0x1E2E58),
            new Category("BARBER", GLASS, false, 0xF4F0E6, 0xC8202A),
            new Category("HAIR SALON", GLASS, false, 0xF4F0E6, 0x2A2A2A),
            new Category("COIN LAUNDRY", GLASS, false, 0x2A8AD0, 0xF4F0E6),
            new Category("FUDOSAN", OFFICE, false, 0xF4F0E6, 0x2A5AC8),
            new Category("CLINIC", OFFICE, false, 0xF4F0E6, 0x2E8A4A),
            new Category("DENTAL", OFFICE, false, 0xF4F0E6, 0x2A8AD0),
            new Category("PHARMACY", OFFICE, false, 0xF4F0E6, 0x2E8A4A),
            new Category("BANK", OFFICE, false, 0x1E2E58, 0xF4F0E6),
            new Category("TRAVEL", OFFICE, false, 0x2A8AD0, 0xF4F0E6),
            new Category("JUKU", OFFICE, false, 0xF07820, 0xF4F0E6),
            new Category("CYCLE", PRODUCE, false, 0x2A2A2A, 0xF2C230),
            new Category("PHOTO", GLASS, false, 0xF2C230, 0x1A1A1C),
            new Category("CAPSULE HOTEL", NEON, false, 0x2A2A6A, 0x2AD0FF),
            new Category("CLOSED", SHUTTER, false, 0x8A8A88, 0x4A4A4A),
    };

    private static final String[] SURNAMES = {"TANAKA", "SUZUKI", "SATO", "YAMADA", "KOBAYASHI", "NAKAMURA", "ITO", "KATO",
            "YOSHIDA", "YAMAMOTO", "MATSUMOTO", "INOUE", "KIMURA", "HAYASHI", "SHIMIZU", "MORI", "IKEDA", "HASHIMOTO",
            "ABE", "ISHIKAWA", "OGAWA", "FUJITA", "OKADA", "GOTO", "HASEGAWA", "MURAKAMI", "KONDO", "SAKAMOTO", "ENDO", "AOKI"};
    private static final String[] WORDS = {"SAKURA", "HANA", "KAZE", "TSUKI", "UMI", "YAMA", "HOSHI", "SORA", "AOI",
            "MIDORI", "KOHAKU", "TAIYO", "YUKI", "KUMO", "NAMI", "KAEDE", "MOMIJI", "TAKE", "MATSU", "UME", "KOI", "TORA",
            "TATSU", "KITSUNE", "NEKO", "USAGI", "TSUBAKI", "HIKARI", "AKARI", "NIJI", "KIRI", "SUZU", "HINATA", "MINATO"};
    private static final String[] SUFFIX = {"YA", "TEI", "DO", "AN", "KEN", "", "", ""};

    /** Romaji brand for shop {@code idx}. */
    public static String brand(Random r) {
        switch (r.nextInt(4)) {
            case 0: return SURNAMES[r.nextInt(SURNAMES.length)];
            case 1: return WORDS[r.nextInt(WORDS.length)] + SUFFIX[r.nextInt(SUFFIX.length)];
            case 2: return WORDS[r.nextInt(WORDS.length)];
            default: return SURNAMES[r.nextInt(SURNAMES.length)] + SUFFIX[r.nextInt(5)];
        }
    }

    // ------------------------------------------------------------------ shopfronts

    /** Shop texture 128x64 for category {@code cat}; {@code seed} makes each unique. */
    public static Texture shop(int cat, int seed, String[] nameOut) {
        Category c = CATEGORIES[cat];
        Random r = new Random(seed * 977L + cat * 31L + 7);
        Img m = new Img(128, 64);
        int signBg = 0xFF000000 | c.palette[0], signFg = 0xFF000000 | c.palette[1 % c.palette.length];
        int wall = 0xFF000000 | Facades.NEUTRALS[r.nextInt(Facades.NEUTRALS.length)];
        Facades.wallMaterial(m, r, wall, r.nextInt(3) == 0 ? 3 : 0, seed);
        String brand = brand(r);
        String line2 = c.style == SHUTTER ? "" : (r.nextInt(3) == 0 ? c.label : brand);
        if (nameOut != null) nameOut[0] = brand + " " + c.label;

        // sign band
        boolean dark = c.style == NEON || c.style == BAR;
        m.rect(0, 0, 128, 3, mul(wall, 1.1f));
        m.rect(2, 3, 126, 20, dark ? 0xFF121218 : signBg);
        m.frame(2, 3, 126, 20, 1, mul(signBg, 0.6f));
        int glyphs = 2 + r.nextInt(3);
        int fg = dark ? brightest(c.palette) : signFg;
        if (c.style == SHUTTER) {
            m.tint(2, 3, 126, 20, 0xFF9A9A98, 0.5f);
            Kanji.row(m.px, 128, 64, seed, glyphs, 8, 6, 7, 1, mul(signFg, 0.8f));
        } else if (r.nextBoolean()) {
            int wk = Kanji.row(m.px, 128, 64, seed, glyphs, 6, 5, 7, 1, fg);
            String t = line2.length() > 12 ? line2.substring(0, 12) : line2;
            PixelFont.draw(m.px, 128, 64, t, Math.max(wk + 12, 124 - PixelFont.width(t, 1)), 8, 1, dark ? 0xFFF4F0E6 : fg);
        } else {
            Kanji.row(m.px, 128, 64, seed, Math.min(glyphs, 4), 64 - (Math.min(glyphs, 4) * 16 - 2) / 2, 4, 7, 2, fg);
        }
        if (dark) neonEdge(m, 2, 3, 126, 20, brightest(c.palette) == signBg ? signFg : signBg);

        switch (c.style) {
            case NOREN: noren(m, r, c, seed); break;
            case BAR: bar(m, r, c, seed); break;
            case NEON: neon(m, r, c, seed); break;
            case KONBINI: konbini(m, r, c); break;
            case PRODUCE: produce(m, r, c, cat); break;
            case SHUTTER: shutter(m, r); break;
            case OFFICE: office(m, r, c, cat); break;
            default: glassFront(m, r, c, cat); break;
        }
        if (c.lanterns || (c.style == NOREN && r.nextInt(3) == 0)) {
            lantern(m, 10, 24, seed);
            lantern(m, 112, 24, seed + 1);
        }
        // base / step
        m.rect(0, 60, 128, 64, mul(wall, 0.7f));
        m.shade(0, 60, 128, 61, 1.2f);
        return m.texture();
    }

    private static int brightest(int[] pal) {
        int best = pal[0], bl = -1;
        for (int c : pal) {
            int l = ((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255);
            if (l > bl) { bl = l; best = c; }
        }
        return 0xFF000000 | best;
    }

    private static void neonEdge(Img m, int x0, int y0, int x1, int y1, int c) {
        int glow = mix(0xFF121218, 0xFF000000 | c, 0.45f);
        m.frame(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 1, 0xFF000000 | c);
        m.frame(x0, y0, x1, y1, 1, glow);
    }

    private static void interior(Img m, Random r, int x0, int y0, int x1, int y1, int glass, boolean bright) {
        int deep = bright ? 0xFFE8ECEC : mix(glass, 0xFF14181E, 0.65f);
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) Math.max(1, y1 - y0);
            int c = mix(mix(glass, 0xFFE8F4FF, bright ? 0.6f : 0.25f), deep, 0.35f + t * 0.4f);
            for (int x = x0; x < x1; x++) m.set(x, y, c);
        }
        for (int sy = y0 + 8; sy < y1 - 3; sy += 9) {
            for (int x = x0 + 1; x < x1 - 2; x += 3 + r.nextInt(3)) {
                int gc = mix(0xFF000000 | r.nextInt(0xFFFFFF), deep, bright ? 0.2f : 0.5f);
                m.rect(x, sy - 2 - r.nextInt(4), x + 2, sy, gc);
            }
        }
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (((x - x0) + (y - y0) / 2) % 31 < 3) m.set(x, y, mix(m.get(x, y), 0xFFFFFFFF, 0.2f));
    }

    private static void glassFront(Img m, Random r, Category c, int cat) {
        int frame = r.nextBoolean() ? 0xFF2A2C30 : 0xFFB8BCC0;
        int glass = 0xFF000000 | Facades.GLASS[r.nextInt(Facades.GLASS.length)];
        int top = 22;
        if (r.nextBoolean()) {   // awning
            int a = 0xFF000000 | c.palette[0], b = r.nextBoolean() ? 0xFFF4F2EC : mul(a, 0.75f);
            for (int y = 21; y < 27; y++) for (int x = 2; x < 126; x++) m.set(x, y, ((x / 6) & 1) == 0 ? a : b);
            m.shade(2, 27, 126, 28, 0.7f);
            top = 28;
        }
        interior(m, r, 4, top, 124, 60, glass, false);
        m.frame(3, top - 1, 125, 60, 2, frame);
        int dx = r.nextBoolean() ? 50 : (r.nextBoolean() ? 8 : 96);
        m.rect(dx, top + 2, dx + 22, 60, mix(glass, 0xFF101418, 0.5f));
        m.frame(dx, top + 2, dx + 22, 60, 1, frame);
        for (int x = 36; x < 124; x += 34) if (x < dx - 3 || x > dx + 25) m.rect(x, top, x + 2, 60, frame);
        String label = CATEGORIES[cat].label;
        if (label.equals("BARBER")) {     // spinning barber pole
            for (int y = 26; y < 56; y++) for (int x = 0; x < 4; x++) {
                int k = (y + x * 2) % 9;
                m.set(124 + x - 2, y, k < 3 ? 0xFFD02030 : (k < 6 ? 0xFFF4F4F4 : 0xFF2A4AC8));
            }
        }
        if (label.equals("COIN LAUNDRY")) {
            for (int x = 10; x < 120; x += 16) { m.disc(x + 6, 46, 5, 0xFFE8E8E8); m.disc(x + 6, 46, 3, 0xFF6A8AA8); }
        }
        if (r.nextInt(3) == 0) poster(m, r, dx > 64 ? 14 : 86, top + 6);
        if (r.nextInt(3) == 0) standSign(m, r, c, dx > 64 ? 70 : 30);
    }

    private static void poster(Img m, Random r, int x, int y) {
        int bg = 0xFF000000 | new int[]{0xF4F0E6, 0xF2C230, 0xD02030, 0x2A8AD0, 0xF07820}[r.nextInt(5)];
        m.rect(x, y, x + 14, y + 18, bg);
        Kanji.column(m.px, 128, 64, r.nextInt(9999), 2, x + 4, y + 2, 7, 1, bg == 0xFFF4F0E6 ? 0xFFD02030 : 0xFFFFFFFF);
    }

    private static void standSign(Img m, Random r, Category c, int x) {
        int bg = 0xFF000000 | (r.nextBoolean() ? 0x2A2A2A : c.palette[0]);
        m.rect(x, 44, x + 9, 60, bg);
        m.frame(x, 44, x + 9, 60, 1, 0xFF6A4A32);
        Kanji.column(m.px, 128, 64, r.nextInt(9999), 1, x + 1, 47, 7, 1, 0xFFF4F0E6);
    }

    private static void noren(Img m, Random r, Category c, int seed) {
        // wooden or plaster front with lattice windows and a noren curtain at the door
        boolean wood = r.nextBoolean();
        int face = wood ? 0xFF4A3424 : 0xFFE8E0CC;
        m.rect(0, 20, 128, 60, face);
        if (wood) for (int x = 0; x < 128; x += 4) m.shade(x, 20, x + 1, 60, 0.8f);
        else m.grain(0, 20, 128, 60, 0.04f, 0.04f, seed);
        int glass = 0xFF3A4048;
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 6 : 86, x1 = x0 + 36;
            interior(m, r, x0, 28, x1, 52, glass, false);
            for (int x = x0; x < x1; x += 3) m.rect(x, 28, x + 1, 52, 0xFF6A4A2E);   // koshi lattice
            m.frame(x0 - 1, 27, x1 + 1, 53, 1, 0xFF3A2418);
        }
        // doorway + noren
        m.rect(46, 24, 82, 60, 0xFF1A1410);
        int nc = 0xFF000000 | c.palette[r.nextInt(c.palette.length)];
        int ink = (nc & 0xFFFFFF) == 0xF4F0E6 || (nc & 0xFFFFFF) == 0xE8D8B0 || (nc & 0xFFFFFF) == 0xE8E0C8 ? 0xFF1A1A1C : 0xFFF4F0E6;
        m.rect(46, 24, 82, 40, nc);
        for (int x = 54; x < 82; x += 9) m.rect(x, 30, x + 1, 40, mul(nc, 0.6f));     // slits
        m.rect(46, 24, 82, 25, 0xFF6A4A2E);                                              // pole
        Kanji.glyph(m.px, 128, 64, seed * 3 + 1, 52, 30, 7, 1, ink);
        Kanji.glyph(m.px, 128, 64, seed * 3 + 2, 68, 30, 7, 1, ink);
        if (r.nextBoolean()) standSign(m, r, c, 36);
    }

    private static void lantern(Img m, int cx, int cy, int seed) {
        for (int y = 0; y < 14; y++) {
            float t = (y - 6.5f) / 7f;
            int hw = (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int x = -hw; x <= hw; x++) m.set(cx + x, cy + y, x > hw - 2 ? 0xFFA01818 : 0xFFE02828);
        }
        m.rect(cx - 3, cy - 1, cx + 4, cy + 1, 0xFF1A1A1C);
        m.rect(cx - 3, cy + 13, cx + 4, cy + 15, 0xFF1A1A1C);
        Kanji.glyph(m.px, 128, 64, seed * 5 + 3, cx - 3, cy + 4, 7, 1, 0xFF1A1A1C);
    }

    private static void bar(Img m, Random r, Category c, int seed) {
        int face = 0xFF000000 | new int[]{0x2A2422, 0x1E1E22, 0x3A3A3E, 0x4A3A2E}[r.nextInt(4)];
        m.rect(0, 20, 128, 60, face);
        m.grain(0, 20, 128, 60, 0.06f, 0.05f, seed);
        int dx = 20 + r.nextInt(70);
        m.rect(dx, 26, dx + 20, 60, 0xFF5A3A22);
        for (int y = 28; y < 60; y += 6) m.shade(dx, y, dx + 20, y + 1, 0.7f);
        m.rect(dx + 7, 32, dx + 13, 38, 0xFFE8C880);                         // little window glow
        m.rect(dx + 16, 42, dx + 18, 46, 0xFFC8A040);                         // handle
        // small lit box sign beside the door
        int sx = dx > 64 ? dx - 22 : dx + 26;
        m.rect(sx, 34, sx + 16, 54, 0xFF000000 | c.palette[0]);
        m.frame(sx, 34, sx + 16, 54, 1, 0xFFE8E0D0);
        Kanji.column(m.px, 128, 64, seed + 7, 2, sx + 4, 36, 7, 1, 0xFFF4F0E6);
        if (r.nextBoolean()) for (int x = 6; x < 122; x += 8) m.set(x, 22, 0xFFFFE8A0);   // string lights
    }

    private static void neon(Img m, Random r, Category c, int seed) {
        m.rect(0, 20, 128, 60, 0xFF101018);
        int a = 0xFF000000 | c.palette[0], b = 0xFF000000 | c.palette[1 % c.palette.length];
        for (int y = 21; y < 59; y += 37) for (int x = 2; x < 126; x += 3) m.set(x, y, ((x / 3) & 1) == 0 ? a : b);   // marquee bulbs
        Kanji.row(m.px, 128, 64, seed + 11, 3, 18, 24, 7, 2, b);
        interior(m, r, 34, 42, 94, 60, 0xFF6A2A8A, false);
        m.tint(34, 42, 94, 60, a, 0.25f);
        m.frame(33, 41, 95, 60, 1, a);
        for (int y = 26; y < 58; y += 4) { m.set(4, y, a); m.set(123, y, b); }
    }

    private static void konbini(Img m, Random r, Category c) {
        int[] p = c.palette;
        int band = 0;
        for (int i = 0; i < p.length; i++) { m.rect(0, 20 + band, 128, 23 + band, 0xFF000000 | p[i]); band += 3; }
        interior(m, r, 3, 21 + band, 125, 60, 0xFF8AA8C0, true);
        m.frame(2, 20 + band, 126, 60, 2, 0xFFD8DCE0);
        for (int x = 34; x < 124; x += 32) m.rect(x, 20 + band, x + 2, 60, 0xFFD8DCE0);
        m.rect(52, 24 + band, 76, 60, 0xFFB8D0E0);                               // automatic doors
        m.rect(63, 24 + band, 65, 60, 0xFF9AA8B0);
        if (r.nextBoolean()) { m.rect(100, 40, 112, 60, 0xFF2A5AC8); m.rect(102, 43, 110, 49, 0xFF9AD0F0); }   // ATM/copier
    }

    private static void produce(Img m, Random r, Category c, int cat) {
        int aw = 0xFF000000 | c.palette[0];
        for (int y = 20; y < 28; y++) for (int x = 0; x < 128; x++) m.set(x, y, mul(aw, 1.05f - (y - 20) * 0.03f));
        m.shade(0, 28, 128, 29, 0.6f);
        m.rect(0, 29, 128, 60, 0xFF1E1A16);                                       // open dark interior
        String label = CATEGORIES[cat].label;
        int[] goods;
        if (label.equals("FLOWERS")) goods = new int[]{0xE84A8A, 0xF2C230, 0xF4F4F4, 0xD02030, 0xA070D0};
        else if (label.equals("SAKANA")) goods = new int[]{0xB8C8D0, 0x8AA0B0, 0xD8E0E8, 0xC86A5A};
        else if (label.equals("NIKU")) goods = new int[]{0xC84A4A, 0xE8A0A0, 0xA03030};
        else if (label.equals("CYCLE")) goods = new int[]{0x2A2A2A, 0xC8C8C8};
        else goods = new int[]{0xF07820, 0x5AA040, 0xD02030, 0xF2C230, 0x3E7A2E, 0xE8E0C0};
        for (int row = 0; row < 2; row++) {
            int y0 = 44 + row * 8;
            for (int x = 2; x < 126; x += 12) {
                m.rect(x, y0, x + 11, y0 + 7, 0xFF8A6A42);                         // crate
                int g = 0xFF000000 | goods[r.nextInt(goods.length)];
                for (int k = 0; k < 6; k++) m.disc(x + 2 + r.nextInt(8), y0 + 1 + r.nextInt(3), 1.6f, mul(g, 0.85f + r.nextFloat() * 0.3f));
            }
        }
        for (int k = 0; k < 4; k++) { int x = 8 + r.nextInt(110); m.rect(x, 32, x + 8, 37, 0xFFF2E040); }  // price cards
    }

    private static void shutter(Img m, Random r) {
        int base = 0xFF000000 | new int[]{0x9A9C9E, 0x8A8C8E, 0xA8A8A0, 0x7A7E84}[r.nextInt(4)];
        m.rect(0, 20, 128, 60, base);
        for (int y = 20; y < 60; y += 3) { m.shade(0, y, 128, y + 1, 1.12f); m.shade(0, y + 2, 128, y + 3, 0.82f); }
        m.rect(0, 20, 128, 23, mul(base, 0.7f));
        if (r.nextInt(3) == 0) {   // tags
            int col = 0xFF000000 | new int[]{0x2A2A2A, 0xC83030, 0x3050C0}[r.nextInt(3)];
            for (int i = 0; i < 40; i++) m.set(20 + r.nextInt(80) + i % 9, 34 + (int) (Math.sin(i * 0.7) * 5), col);
        }
    }

    private static void office(Img m, Random r, Category c, int cat) {
        int glass = 0xFF6A8AA0;
        interior(m, r, 4, 22, 124, 60, glass, true);
        m.frame(3, 21, 125, 60, 2, 0xFFB8BCC0);
        m.rect(52, 24, 76, 60, 0xFFC8D8E0);
        String label = CATEGORIES[cat].label;
        if (label.equals("FUDOSAN")) {          // listing sheets in the window
            for (int y = 26; y < 52; y += 12)
                for (int x = 6; x < 124; x += 11) {
                    if (x > 48 && x < 78) continue;
                    m.rect(x, y, x + 9, y + 10, 0xFFF8F8F4);
                    m.rect(x + 1, y + 1, x + 8, y + 3, 0xFF000000 | c.palette[1]);
                    for (int k = 5; k < 9; k += 2) m.rect(x + 1, y + k, x + 8, y + k + 1, 0xFF9A9A9A);
                }
        } else if (label.equals("CLINIC") || label.equals("PHARMACY") || label.equals("DENTAL")) {
            m.tint(4, 22, 124, 46, 0xFFF4F4F4, 0.55f);                              // frosted glass
            int cc = 0xFF000000 | c.palette[1];
            m.rect(18, 28, 22, 40, cc); m.rect(14, 32, 26, 36, cc);
            m.rect(104, 28, 108, 40, cc); m.rect(100, 32, 112, 36, cc);
        } else {
            for (int x = 8; x < 124; x += 30) poster(m, r, x, 28);
        }
    }

    /** Police box (koban) front. */
    public static Texture koban(int seed) {
        Random r = new Random(seed);
        Img m = new Img(128, 64);
        Facades.wallMaterial(m, r, 0xFFE8E4DA, 3, seed);
        m.rect(2, 3, 126, 20, 0xFFF4F4F0);
        m.frame(2, 3, 126, 20, 1, 0xFF2A2A2A);
        PixelFont.draw(m.px, 128, 64, "KOBAN", 64 - PixelFont.width("KOBAN", 2) / 2, 5, 2, 0xFF1E2E58);
        m.disc(64, 26, 3.5f, 0xFFE02020);                                            // red lamp
        interior(m, r, 20, 30, 108, 60, 0xFF6A8AA0, true);
        m.frame(19, 29, 109, 60, 2, 0xFFB8BCC0);
        m.rect(54, 32, 74, 60, 0xFFC8D8E0);
        return m.texture();
    }

    /** Fire station apparatus bay: red roll-up doors. */
    public static Texture fireDoors(int seed) {
        Random r = new Random(seed);
        Img m = new Img(128, 64);
        Facades.wallMaterial(m, r, 0xFFEDEAE3, 3, seed);
        for (int d = 0; d < 2; d++) {
            int x0 = 6 + d * 62, x1 = x0 + 54;
            m.rect(x0, 8, x1, 62, 0xFFC81E24);
            for (int y = 8; y < 62; y += 3) m.shade(x0, y, x1, y + 1, 0.8f);
            m.rect(x0 + 4, 18, x1 - 4, 24, 0xFF8AA8C0);
            for (int x = x0 + 4; x < x1 - 4; x += 8) m.rect(x, 18, x + 1, 24, 0xFFC81E24);
            m.frame(x0 - 1, 7, x1 + 1, 62, 1, 0xFF9A9A98);
            PixelFont.draw(m.px, 128, 64, String.valueOf(d + 1), x0 + 24, 40, 2, 0xFFF4F4F4);
        }
        return m.texture();
    }

    // ------------------------------------------------------------------ facades (64x128)

    public enum Kind { MANSION, CORRIDOR, ZAKKYO, SHOPHOUSE, OFFICE_GRID, GLASS, HOUSE, HOUSE_WOOD, FIRE, WAREHOUSE, BLANK }

    private static final int[] TILES = {0xF0EEE8, 0xE8E0D0, 0xD8D0C0, 0xC8C0B4, 0xB8B0A4, 0xA88870, 0x8A6A58, 0xD0D4D8, 0xE8E4DC};
    private static final int[] SIDING = {0xEEEAE0, 0xE0D8C8, 0xD0CCC4, 0xB8B4AC, 0x9AA4AC, 0xC8B49A, 0x8A7460, 0xF4F0E8};

    public static Texture facade(Random r, Kind k, int seed) {
        Img m = new Img(64, 128);
        switch (k) {
            case MANSION: mansion(m, r, seed); break;
            case CORRIDOR: corridor(m, r, seed); break;
            case ZAKKYO: zakkyo(m, r, seed); break;
            case SHOPHOUSE: shophouse(m, r, seed); break;
            case OFFICE_GRID: officeGrid(m, r, seed); break;
            case GLASS: return Facades.generate(r, Facades.Kind.OFFICE, seed);
            case HOUSE: house(m, r, seed); break;
            case HOUSE_WOOD: houseWood(m, r, seed); break;
            case FIRE: fire(m, r, seed); break;
            case WAREHOUSE: return Facades.generate(r, Facades.Kind.INDUSTRIAL, seed);
            default: blank(m, r, seed); break;
        }
        return m.texture();
    }

    private static int pick(Random r, int[] a) { return 0xFF000000 | a[r.nextInt(a.length)]; }

    private static void mansion(Img m, Random r, int seed) {
        int tile = pick(r, TILES);
        Facades.wallMaterial(m, r, tile, 3, seed);
        int glass = 0xFF000000 | Facades.GLASS[r.nextInt(Facades.GLASS.length)];
        Facades.glass(m, r, 10, 30, 54, 112, glass, 0xFFC8CCD0, 2, true);
        m.rect(31, 30, 33, 112, 0xFFC8CCD0);
        // balcony slab + parapet (frosted glass or solid)
        m.rect(0, 112, 64, 122, mul(tile, 1.08f));
        m.shade(0, 121, 64, 122, 0.7f);
        boolean frosted = r.nextBoolean();
        if (frosted) { m.tint(0, 84, 64, 112, 0xFFDDE6EC, 0.7f); m.rect(0, 82, 64, 84, 0xFFB8BCC0); }
        else { m.rect(0, 84, 64, 112, mul(tile, 0.96f)); m.rect(0, 82, 64, 84, mul(tile, 1.1f)); }
        if (r.nextInt(3) == 0) {   // laundry on a pole
            m.rect(4, 44, 60, 45, 0xFF9A9A9A);
            for (int x = 6; x < 58; x += 5 + r.nextInt(4)) {
                int c = pick(r, new int[]{0xF4F4F4, 0x6A8AC8, 0xE8A0A0, 0xF2D060, 0x8AC09A, 0xF4F4F4});
                m.rect(x, 45, x + 4, 52 + r.nextInt(14), c);
            }
        }
        if (r.nextInt(6) == 0) m.rect(10, 78, 40, 96, pick(r, new int[]{0xF4F0E6, 0xE8C8D8, 0xC8D8F0}));  // futon airing
        if (r.nextInt(2) == 0) { m.rect(46, 100, 60, 112, 0xFFE0E0DA); m.rect(48, 102, 58, 110, 0xFF9A9A94); }  // AC unit
        m.shade(0, 0, 64, 3, 0.8f);
    }

    private static void corridor(Img m, Random r, int seed) {
        int tile = pick(r, TILES);
        Facades.wallMaterial(m, r, mul(tile, 0.9f), 0, seed);
        m.rect(0, 0, 64, 10, mul(tile, 0.55f));                                    // corridor ceiling shadow
        int door = pick(r, new int[]{0xC8B89A, 0x8A7A6A, 0x6A7A8A, 0xD8D4CC});
        m.rect(14, 22, 38, 88, door);
        m.frame(14, 22, 38, 88, 1, mul(door, 0.7f));
        m.rect(33, 52, 35, 60, 0xFFC8C8C8);
        m.rect(44, 30, 58, 50, 0xFF9AA8B4);                                         // small window w/ grille
        for (int x = 44; x < 58; x += 3) m.rect(x, 30, x + 1, 50, 0xFFB8BCC0);
        m.rect(0, 88, 64, 122, tile);                                               // corridor parapet
        m.rect(0, 86, 64, 88, mul(tile, 1.1f));
        m.shade(0, 121, 64, 128, 0.75f);
        m.set(26, 14, 0xFFFFF0C8); m.set(27, 14, 0xFFFFF0C8);
    }

    private static void zakkyo(Img m, Random r, int seed) {
        int wall = pick(r, new int[]{0x3A3A3E, 0x6A6A6E, 0xC8C0B4, 0x8A7A6A, 0xE8E4DC, 0x4A4A56, 0xA8B0B8});
        Facades.wallMaterial(m, r, wall, 3, seed);
        // tenant sign box for this floor
        int sb = pick(r, new int[]{0xF4F0E6, 0xD02030, 0x2A5AC8, 0xF2C230, 0xE84A8A, 0x1A1A1C, 0x7A2AC8, 0x2E8A4A});
        m.rect(6, 8, 58, 34, sb);
        m.frame(6, 8, 58, 34, 1, 0xFFE8E8E8);
        int ink = (sb & 0xFFFFFF) == 0xF4F0E6 || (sb & 0xFFFFFF) == 0xF2C230 ? 0xFF1A1A1C : 0xFFF4F0E6;
        Kanji.row(m.px, 64, 128, seed, 3, 10, 12, 7, 1, ink);
        String b = brand(r);
        if (b.length() > 8) b = b.substring(0, 8);
        PixelFont.draw(m.px, 64, 128, b, 32 - PixelFont.width(b, 1) / 2, 23, 1, ink);
        int glass = 0xFF000000 | Facades.GLASS[r.nextInt(Facades.GLASS.length)];
        Facades.glass(m, r, 8, 46, 56, 104, glass, 0xFF9AA0A6, 2, true);
        if (r.nextBoolean()) m.tint(8, 46, 56, 104, 0xFF000000 | (r.nextInt(0xFFFFFF)), 0.3f);   // coloured blinds / posters
        m.rect(0, 120, 64, 128, mul(wall, 1.1f));
    }

    private static void shophouse(Img m, Random r, int seed) {
        boolean siding = r.nextBoolean();
        int base = siding ? pick(r, SIDING) : pick(r, TILES);
        Facades.wallMaterial(m, r, base, siding ? 4 : 3, seed);
        if (siding) for (int y = 0; y < 128; y += 6) m.shade(0, y, 64, y + 1, 0.85f);
        int frosted = 0xFFC8D4DC;
        Facades.glass(m, r, 8, 34, 56, 80, frosted, 0xFFB8BCC0, 2, false);
        m.rect(31, 34, 33, 80, 0xFFB8BCC0);
        m.tint(8, 34, 56, 80, 0xFFF0F4F4, 0.35f);
        m.rect(6, 82, 58, 85, 0xFFB8BCC0);
        if (r.nextBoolean()) { m.rect(40, 92, 56, 104, 0xFFE0E0DA); m.rect(42, 94, 54, 102, 0xFF9A9A94); }
        if (r.nextInt(3) == 0) for (int x = 8; x < 56; x += 4) m.rect(x, 86, x + 1, 100, 0xFF8A8E92);  // window grille
        m.rect(0, 122, 64, 128, mul(base, 0.9f));
    }

    private static void officeGrid(Img m, Random r, int seed) {
        int con = pick(r, new int[]{0xD8D4CC, 0xC8C4BC, 0xB8B4AC, 0xE8E4DA, 0x9A9690});
        Facades.wallMaterial(m, r, con, 1, seed);
        int glass = 0xFF000000 | Facades.GLASS[r.nextInt(Facades.GLASS.length)];
        Facades.glass(m, r, 0, 30, 64, 96, glass, 0xFF5A5E64, 0, r.nextBoolean());
        for (int x = 0; x < 64; x += 16) m.rect(x, 30, x + 2, 96, con);
        m.shade(0, 96, 64, 98, 0.7f);
    }

    private static void house(Img m, Random r, int seed) {
        int s = pick(r, SIDING);
        m.fill(s);
        m.grain(0, 0, 64, 128, 0.03f, 0.03f, seed);
        for (int y = 0; y < 128; y += 7) m.shade(0, y, 64, y + 1, 0.86f);
        int frame = r.nextBoolean() ? 0xFFB8BCC0 : 0xFF6A6058;
        if (r.nextBoolean()) {   // window with sliding shutter box
            Facades.glass(m, r, 14, 40, 50, 92, 0xFF8AA0B0, frame, 2, true);
            m.rect(31, 40, 33, 92, frame);
            m.rect(12, 30, 52, 38, mul(s, 0.9f));
        } else {                 // small frosted window
            Facades.glass(m, r, 20, 46, 44, 80, 0xFFC8D4DC, frame, 2, false);
            m.tint(20, 46, 44, 80, 0xFFF0F4F4, 0.4f);
        }
        m.rect(0, 124, 64, 128, mul(s, 0.8f));
    }

    private static void houseWood(Img m, Random r, int seed) {
        int wood = pick(r, new int[]{0x3A2A22, 0x2A2420, 0x5A4232, 0x4A3A2C});
        m.fill(wood);
        for (int x = 0; x < 64; x += 5) m.shade(x, 0, x + 1, 128, 0.7f);
        m.grain(0, 0, 64, 128, 0.06f, 0.04f, seed);
        m.rect(0, 0, 64, 26, 0xFFEAE4D6);                                         // plaster band
        m.grain(0, 0, 64, 26, 0.04f, 0.03f, seed + 1);
        Facades.glass(m, r, 12, 44, 52, 96, 0xFF3A4048, 0xFF2A1E16, 2, false);
        for (int x = 12; x < 52; x += 3) m.rect(x, 44, x + 1, 96, 0xFF6A4A2E);   // lattice
    }

    private static void fire(Img m, Random r, int seed) {
        Facades.wallMaterial(m, r, 0xFFEDEAE3, 3, seed);
        Facades.glass(m, r, 8, 36, 56, 88, 0xFF6A8AA8, 0xFFB8BCC0, 2, true);
        m.rect(31, 36, 33, 88, 0xFFB8BCC0);
        m.rect(0, 104, 64, 116, 0xFFC81E24);
        m.rect(0, 116, 64, 120, 0xFFF4F4F4);
    }

    private static void blank(Img m, Random r, int seed) {
        int k = r.nextInt(3);
        int base = k == 0 ? pick(r, TILES) : pick(r, new int[]{0xC8C4BC, 0xB8B4AC, 0xD8D4CC, 0xA8A49C});
        Facades.wallMaterial(m, r, base, k == 0 ? 3 : 1, seed);
        if (k == 1) for (int y = 16; y < 128; y += 32) for (int x = 8; x < 64; x += 16) m.shade(x, y, x + 2, y + 2, 0.55f);
        if (r.nextInt(3) == 0) { int x = 8 + r.nextInt(44); m.rect(x, 0, x + 3, 128, 0xFF9A9A98); m.shade(x + 3, 0, x + 4, 128, 0.7f); }
    }

    // ------------------------------------------------------------------ misc

    /** Kawara roof tiles (64x64). */
    public static Texture roofTiles(int color, int seed) {
        // sangawara: rows of S-profile clay tiles; each row overlaps the one below it (v runs down
        // the slope), each tile glints along its crest and is a shade different from its neighbours
        Img m = new Img(64, 64);
        int c = 0xFF000000 | color;
        for (int y = 0; y < 64; y++) {
            int row = y / 8, ry = y % 8;
            for (int x = 0; x < 64; x++) {
                int cx = (x + (row & 1) * 4) % 8, tile = (x + (row & 1) * 4) / 8;
                float prof = (float) Math.sin(cx / 8f * Math.PI);            // the rounded crest
                float k = 0.7f + 0.38f * prof * prof;
                k += (ry < 2 ? 0.06f : 0f) - ry * 0.018f;                    // lit upper lip of each row
                if (ry == 7) k *= 0.55f;                                      // the shadow line under the overlap
                if (ry == 6) k *= 0.85f;
                k *= 0.93f + Noise.rnd(tile, row, seed) * 0.14f;             // tile to tile variation
                int px = mul(c, k);
                if (prof > 0.9f && ry < 5) px = mix(px, 0xFFFFFFFF, 0.08f); // glaze glint on the crest
                m.px[y * 64 + x] = px;
            }
        }
        Photo.weather(m, 0.06f, seed);
        return m.texture();
    }

    /** Vertical signboard (16x64). */
    public static Texture verticalSign(int seed) {
        Random r = new Random(seed * 131L + 9);
        Img m = new Img(16, 64);
        int bg = pick(r, new int[]{0xF4F0E6, 0xD02030, 0x1A1A1C, 0xF2C230, 0x2A5AC8, 0xE84A8A, 0x2E8A4A, 0x7A2AC8, 0xF07820});
        m.fill(bg);
        m.frame(0, 0, 16, 64, 1, 0xFFE8E8E8);
        int ink = (bg & 0xFFFFFF) == 0xF4F0E6 || (bg & 0xFFFFFF) == 0xF2C230 ? 0xFFD02030 : 0xFFF4F0E6;
        Kanji.column(m.px, 16, 64, seed, 6, 4, 4, 7, 1, ink);
        return m.texture();
    }

    /** Drink vending machine front (32x64). */
    public static Texture vending(int seed) {
        Random r = new Random(seed);
        Img m = new Img(32, 64);
        int body = pick(r, new int[]{0xF4F4F0, 0xD02030, 0x2A5AC8, 0xF2C230, 0x2E8A4A});
        m.fill(body);
        m.rect(2, 4, 30, 34, 0xFFE8F0F4);
        for (int row = 0; row < 3; row++)
            for (int x = 3; x < 29; x += 4) {
                int c = pick(r, new int[]{0xD02030, 0x2A5AC8, 0xF2C230, 0x2E8A4A, 0xF4F4F4, 0x8A4A2A, 0xF07820});
                m.rect(x, 6 + row * 10, x + 3, 12 + row * 10, c);
                m.set(x + 1, 13 + row * 10, 0xFF30E0B0);
            }
        m.rect(4, 38, 20, 46, 0xFF1A1A1C);
        m.rect(22, 38, 28, 50, 0xFF9A9A9A);
        m.rect(4, 52, 28, 60, 0xFF2A2A2E);
        return m.texture();
    }

    /** Commuter train side: stainless body, coloured stripe, window band (64x32 = one window bay). */
    public static Texture trainSide(int stripe) {
        Img m = new Img(64, 32);
        m.fill(0xFFC8CCD0);
        m.grain(0, 0, 64, 32, 0.02f, 0.02f, 3);
        for (int x = 0; x < 64; x += 4) m.shade(x, 0, x + 1, 32, 0.92f);
        m.rect(0, 20, 64, 24, 0xFF000000 | stripe);
        Facades.glass(m, new Random(1), 8, 6, 56, 18, 0xFF3A4E66, 0xFF4A4E54, 1, false);
        m.rect(31, 6, 33, 18, 0xFF4A4E54);
        m.rect(0, 28, 64, 32, 0xFF4A4E54);
        return m.texture();
    }

    public static Texture trainFront(int stripe) {
        Img m = new Img(32, 32);
        m.fill(0xFFC8CCD0);
        m.rect(3, 4, 29, 16, 0xFF1A2430);
        m.rect(0, 20, 32, 24, 0xFF000000 | stripe);
        m.rect(4, 25, 8, 28, 0xFFFFF0C8);
        m.rect(24, 25, 28, 28, 0xFFFFF0C8);
        m.rect(10, 0, 22, 3, 0xFF2A2A2A);
        return m.texture();
    }

    /** Ballast with sleepers and two rails (64x64 = 2 m of track, rails along v). */
    public static Texture track() {
        Img m = new Img(64, 64);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int v = (int) (110 + Noise.rnd(x, y, 77) * 60);
                m.px[y * 64 + x] = rgb(v, v - 4, v - 10);
            }
        for (int y = 2; y < 64; y += 16) m.rect(4, y, 60, y + 6, 0xFF7A766E);
        m.rect(14, 0, 18, 64, 0xFF6A6E74); m.rect(15, 0, 16, 64, 0xFFC8CCD0);
        m.rect(46, 0, 50, 64, 0xFF6A6E74); m.rect(47, 0, 48, 64, 0xFFC8CCD0);
        return m.texture();
    }

    /** Platform surface with yellow tactile strip and white edge (64x64, edge at x=0). */
    public static Texture platform() {
        Img m = new Img(64, 64);
        m.fill(0xFFC8C4BA);
        m.grain(0, 0, 64, 64, 0.05f, 0.04f, 31);
        m.rect(0, 0, 4, 64, 0xFFF4F4F0);
        m.rect(10, 0, 20, 64, 0xFFF2C230);
        for (int y = 2; y < 64; y += 4) for (int x = 11; x < 20; x += 3) m.set(x, y, 0xFFC89A20);
        return m.texture();
    }

    /** Station name board (128x32). */
    public static Texture stationSign(String name, int seed) {
        Img m = new Img(128, 32);
        m.fill(0xFFF4F4F0);
        m.frame(0, 0, 128, 32, 2, 0xFF2A2A2A);
        m.rect(2, 22, 126, 26, 0xFF2E8A4A);
        Kanji.row(m.px, 128, 32, seed, 3, 64 - (3 * 16 - 2) / 2, 4, 7, 2, 0xFF1A1A1C);
        PixelFont.draw(m.px, 128, 32, name, 64 - PixelFont.width(name, 1) / 2, 23, 1, 0xFFF4F4F0);
        return m.texture();
    }

    /** Concrete block wall (64x64). */
    public static Texture blockWall(int seed) {
        Img m = new Img(64, 64);
        m.fill(0xFFB8B4AC);
        m.grain(0, 0, 64, 64, 0.06f, 0.05f, seed);
        for (int y = 0; y < 64; y += 10) {
            m.shade(0, y, 64, y + 1, 0.75f);
            int off = ((y / 10) & 1) * 10;
            for (int x = off; x < 64; x += 20) m.shade(x, y, x + 1, y + 10, 0.78f);
        }
        m.rect(0, 0, 64, 3, 0xFFC8C4BC);
        return m.texture();
    }
}
