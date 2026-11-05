package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/**
 * Building facades painted as a stack of layers, like an image editor: each texture is one bay
 * (one window column) by one storey, and repeats across a wall and up its floors with no seams.
 *
 * <ol>
 * <li><b>Base material</b>: concrete (form-tie holes, panel joints), mosaic or porcelain tile,
 * render, precast panels, metal cladding, brick slips, siding or curtain-wall glass.</li>
 * <li><b>Structure</b>: the floor slab band at the bottom of every storey, columns at the bay
 * edges, spandrel panels: lit from the upper left so they read as standing proud.</li>
 * <li><b>Openings</b>: windows set into the wall, with shadowed reveals at the head and left
 * jamb, a light sill, frames and mullions, and glass that reflects the sky and shows what's
 * behind it: curtains, blinds, a lamp, an empty dark room.</li>
 * <li><b>Fittings</b>: balconies (slab, railing or frosted panel, laundry, a futon airing),
 * air-conditioner units, downpipes and vents.</li>
 * <li><b>Weathering</b>: rain streaks running down from sills and slab edges, grime soaking up
 * from each slab, and shade under every ledge.</li>
 * </ol>
 *
 * A {@link Style} is one building's look: its bay, a plain side bay (party and side walls),
 * a ground-floor bay, an entrance and a plain material for parapets and gables, all painted
 * from the same recipe so they belong together.
 */
public final class FacadeLayers {
    private FacadeLayers() {}

    public static final int W = 64, H = 128;

    public enum Material { CONCRETE, TILE, MOSAIC, RENDER, PANEL, METAL, BRICK, SIDING, GLASS }

    public enum Window { SASH, PICTURE, RIBBON, TALL, CURTAIN_WALL, SMALL, BALCONY_DOOR, CORRIDOR_DOOR, SHUTTER, NONE }

    /** Everything a building's facade is painted from. */
    public static final class Recipe {
        public Material mat = Material.TILE;
        public int wall = 0xD8D2C6, accent = 0xB8B0A4, frame = 0xB8BCC0, glassTint = 0x6A8AA8;
        public Window win = Window.SASH;
        /** Window size in texels of the 64x128 bay, and sill height from the bottom. */
        public int winW = 40, winH = 58, sill = 40;
        public boolean slab = true, columns, balcony, ac, pipe, ribbonSpandrel;
        /** Balcony front: 0 bars, 1 frosted glass, 2 solid wall in the accent colour. */
        public int rail;
        /** 0 clean .. 1 well weathered. */
        public float grime = 0.5f;
        public long seed;
    }

    /**
     * One building look: the textures that go together. {@code bay} is 2 bays by 2 storeys
     * (128 x 256), each window painted on its own so the repeat is hard to see; the others are
     * one bay by one storey (64 x 128), and {@code plain} is the bare material (64 x 64).
     */
    public static final class Style {
        public Texture bay, side, ground, entrance, plain;
        public Recipe recipe;
    }

    public static Style style(Recipe rc) {
        Style s = new Style();
        s.recipe = rc;
        s.bay = bay2x2(rc, rc.win);
        Window sideWin = rc.win == Window.CURTAIN_WALL ? Window.NONE
                : (rc.win == Window.BALCONY_DOOR || rc.win == Window.CORRIDOR_DOOR) ? Window.SMALL
                : (new Random(rc.seed + 5).nextInt(3) > 0 ? Window.SMALL : Window.NONE);
        Recipe side = copy(rc);
        side.balcony = false;
        side.ac = false;
        side.columns = false;
        s.side = bay(side, sideWin, 1);
        Recipe g = copy(rc);
        g.balcony = false;
        g.ac = false;
        boolean shed = rc.mat == Material.METAL && rc.slab == false;
        s.ground = bay(g, shed ? Window.SHUTTER : rc.win == Window.CURTAIN_WALL || rc.win == Window.RIBBON ? Window.CURTAIN_WALL : Window.PICTURE, 2);
        s.entrance = shed ? bay(g, Window.SHUTTER, 3) : entrance(g);
        s.plain = plain(rc);
        return s;
    }

    static Recipe copy(Recipe r) {
        Recipe c = new Recipe();
        c.mat = r.mat; c.wall = r.wall; c.accent = r.accent; c.frame = r.frame; c.glassTint = r.glassTint;
        c.win = r.win; c.winW = r.winW; c.winH = r.winH; c.sill = r.sill;
        c.slab = r.slab; c.columns = r.columns; c.balcony = r.balcony; c.ac = r.ac; c.pipe = r.pipe;
        c.ribbonSpandrel = r.ribbonSpandrel; c.rail = r.rail; c.grime = r.grime; c.seed = r.seed;
        return c;
    }

    // ------------------------------------------------------------------ the layer stack

    /**
     * Two bays by two storeys: the base material laid over the whole (seamless), then each of
     * the four cells gets its own structure, window, fittings and weathering.
     */
    static Texture bay2x2(Recipe rc, Window win) {
        Img big = new Img(W * 2, H * 2);
        base(big, rc.mat, rc.wall, (int) rc.seed, 0, 0, W * 2, H * 2);
        for (int cy = 0; cy < 2; cy++)
            for (int cx = 0; cx < 2; cx++) {
                Img cell = new Img(W, H);
                for (int y = 0; y < H; y++) System.arraycopy(big.px, (cy * H + y) * W * 2 + cx * W, cell.px, y * W, W);
                paintCell(cell, rc, win, 0, cx + cy * 2);
                for (int y = 0; y < H; y++) System.arraycopy(cell.px, y * W, big.px, (cy * H + y) * W * 2 + cx * W, W);
            }
        return big.texture();
    }

    /** One storey of one bay. {@code variant} 0 the main bay, 1 a side bay, 2 a ground-floor bay, 3 a ground entrance. */
    static Texture bay(Recipe rc, Window win, int variant) {
        Img m = new Img(W, H);
        // 1. base material
        base(m, rc.mat, rc.wall, (int) rc.seed + variant * 101, 0, 0, W, H);
        paintCell(m, rc, win, variant, 0);
        return m.texture();
    }

    /** Layers 2 to 5 of one cell (its base material already laid). */
    static void paintCell(Img m, Recipe rc, Window win, int variant, int cell) {
        Random r = new Random(rc.seed * 31 + variant * 7 + win.ordinal() + cell * 1009);
        int seed = (int) rc.seed + variant * 101 + cell * 13;
        // 2. structure
        int slabTop = H - 12;
        if (rc.columns && variant != 1) column(m, rc, 0, 6);
        if (rc.ribbonSpandrel && win == Window.RIBBON) spandrel(m, rc, seed);
        // 3. openings
        int sillY = slabTop - rc.sill, headY = sillY - rc.winH;
        int ww = Math.min(rc.winW, W - (rc.columns ? 16 : 8));
        int x0 = (W - ww) / 2, x1 = x0 + ww;
        switch (win) {
            case CURTAIN_WALL: curtainWall(m, rc, r, seed, variant == 2); break;
            case RIBBON: window(m, rc, r, 0, headY, W, sillY, 4, true); break;
            case TALL: window(m, rc, r, x0 + 4, 14, x1 - 4, slabTop - 8, 2, false); break;
            case PICTURE: window(m, rc, r, x0 - 2, variant == 2 ? 26 : headY, x1 + 2, variant == 2 ? slabTop - 6 : sillY, 1, false); break;
            case SMALL: window(m, rc, r, W / 2 - 7, 38, W / 2 + 7, 58, 1, false); break;
            case BALCONY_DOOR: window(m, rc, r, 6, 20, W - 6, slabTop, 2, false); break;
            case CORRIDOR_DOOR: corridorDoor(m, rc, r); break;
            case SHUTTER: shutter(m, rc, r, variant == 3); break;
            case NONE: break;
            default: window(m, rc, r, x0, headY, x1, sillY, 2, false); break;
        }
        // 4. fittings
        if (rc.balcony && win == Window.BALCONY_DOOR) balcony(m, rc, r, seed);
        if (rc.ac && win != Window.BALCONY_DOOR && win != Window.CURTAIN_WALL && r.nextInt(3) > 0) acUnit(m, x1 + 1 <= W - 14 ? x1 + 1 : 2, slabTop - 22);
        if (rc.pipe && variant == 1) pipe(m, W - 6);
        if (rc.slab) slab(m, rc, slabTop, variant == 2);
        // 5. weathering and shading
        weather(m, rc, r, seed, slabTop);
    }

    /** A roller shutter (a loading door when {@code big}) under a steel lintel. */
    static void shutter(Img m, Recipe rc, Random r, boolean big) {
        int x0 = big ? 4 : 10, x1 = big ? W - 4 : W - 10, y0 = big ? 22 : 40, y1 = H - 12;
        int c = new int[]{0xFFB8BCBE, 0xFF9AA0A4, 0xFFC8C4B8, 0xFF7A8088}[r.nextInt(4)];
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int k = (y - y0) % 4;
                m.set(x, y, mul(c, k == 0 ? 1.1f : k == 3 ? 0.78f : 0.96f));
            }
        m.rect(x0 - 2, y0 - 5, x1 + 2, y0, mul(0xFF000000 | rc.accent, 0.8f));
        m.rect(x0 - 2, y0, x0, y1, mul(c, 0.7f));
        m.rect(x1, y0, x1 + 2, y1, mul(c, 0.7f));
        m.shade(x0, y0, x1, y0 + 4, 0.75f);
        m.rect(W / 2 - 3, y1 - 6, W / 2 + 3, y1 - 4, mul(c, 0.6f));
    }

    // ------------------------------------------------------------------ 1. materials

    /** Fills a rectangle with a material (seamless when it is the whole texture). */
    static void base(Img m, Material mat, int col, int seed, int x0, int y0, int x1, int y1) {
        int c = 0xFF000000 | col;
        m.rect(x0, y0, x1, y1, c);
        switch (mat) {
            case CONCRETE: {
                m.grain(x0, y0, x1, y1, 0.07f, 0.04f, seed);
                // formwork: panel joints and the tie holes on a grid
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    if ((y & 63) == 0 || (x & 31) == 0) m.set(x, y, mul(m.get(x, y), 0.9f));
                    if ((x & 15) == 7 && (y & 31) == 15) m.set(x, y, mul(m.get(x, y), 0.6f));
                }
                break;
            }
            case TILE: {
                // 45 x 95 mm porcelain tiles: a fine running bond of light joints
                m.grain(x0, y0, x1, y1, 0.03f, 0.05f, seed);
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int row = y / 3, off = (row & 1) * 2;
                    float tv = (Noise.rnd(((x + off) >> 2) & 15, row & 63, seed + 3) - 0.5f) * 0.07f;
                    int p = mul(m.get(x, y), 1f + tv);
                    if (y % 3 == 0 || ((x + off) & 3) == 0) p = mix(p, 0xFFF0EEE8, 0.25f);
                    m.set(x, y, p);
                }
                break;
            }
            case MOSAIC: {
                m.grain(x0, y0, x1, y1, 0.03f, 0.04f, seed);
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    float tv = (Noise.rnd((x >> 1) & 31, (y >> 1) & 63, seed + 5) - 0.5f) * 0.14f;
                    int p = mul(m.get(x, y), 1f + tv);
                    if ((x & 1) == 0 && (y & 1) == 0) p = mul(p, 0.94f);
                    m.set(x, y, p);
                }
                break;
            }
            case RENDER: m.grain(x0, y0, x1, y1, 0.08f, 0.06f, seed); break;
            case PANEL: {
                m.grain(x0, y0, x1, y1, 0.04f, 0.03f, seed);
                // precast panels: a shadowed joint every 2 m across and at mid storey
                for (int y = y0; y < y1; y++) { m.set(0, y, mul(m.get(0, y), 0.72f)); m.set(1, y, mul(m.get(1, y), 1.08f)); }
                int jy = y0 + (y1 - y0) / 2;
                if (jy + 1 < y1) for (int x = x0; x < x1; x++) { m.set(x, jy, mul(m.get(x, jy), 0.75f)); m.set(x, jy + 1, mul(m.get(x, jy + 1), 1.06f)); }
                break;
            }
            case METAL: {
                // standing-seam / ribbed cladding: vertical ribs every 4 texels
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int k = x & 3;
                    float s = k == 0 ? 1.12f : k == 1 ? 1.02f : k == 3 ? 0.84f : 0.95f;
                    m.set(x, y, mul(m.get(x, y), s));
                }
                m.grain(x0, y0, x1, y1, 0.04f, 0.02f, seed);
                break;
            }
            case BRICK: {
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int row = y / 4, off = (row & 1) * 4;
                    float tv = (Noise.rnd(((x + off) >> 3) & 7, row & 31, seed + 7) - 0.5f) * 0.18f;
                    int p = mul(m.get(x, y), 1f + tv);
                    if (y % 4 == 3 || ((x + off) & 7) == 7) p = mix(p, 0xFFC8C0B4, 0.55f);
                    m.set(x, y, p);
                }
                m.grain(x0, y0, x1, y1, 0.03f, 0.05f, seed);
                break;
            }
            case SIDING: {
                // ceramic siding boards: a shadow line under each 16-texel board
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int k = y & 15;
                    float s = k == 15 ? 0.7f : k == 0 ? 1.08f : 1f - k * 0.004f;
                    m.set(x, y, mul(m.get(x, y), s));
                }
                m.grain(x0, y0, x1, y1, 0.04f, 0.04f, seed);
                break;
            }
            case GLASS: m.grain(x0, y0, x1, y1, 0.02f, 0.02f, seed); break;
        }
    }

    // ------------------------------------------------------------------ 2. structure

    /** The floor slab edge at the bottom of the storey: a band standing proud, lit on top, shadow under. */
    static void slab(Img m, Recipe rc, int top, boolean ground) {
        int c = 0xFF000000 | (rc.mat == Material.GLASS || rc.mat == Material.METAL ? rc.accent : mix(0xFF000000 | rc.wall, 0xFF000000 | rc.accent, 0.5f));
        for (int y = top; y < H; y++) for (int x = 0; x < W; x++) {
            float n = (Noise.rnd(x, y, (int) rc.seed + 41) - 0.5f) * 0.05f;
            float k = y == top ? 1.16f : y == top + 1 ? 1.06f : y >= H - 2 ? 0.86f : 1f;
            m.set(x, y, mul(c, k + n));
        }
        // shade the wall just above the slab (soffit of the balcony above, dirt) - and the slab's drip line
        m.shade(0, top - 2, W, top, 0.92f);
    }

    /** A column at the bay edge (half on each side, so neighbouring bays make a whole one). */
    static void column(Img m, Recipe rc, int x, int w) {
        int c = 0xFF000000 | rc.accent;
        for (int y = 0; y < H; y++)
            for (int k = -w / 2; k < w / 2; k++) {
                int px = (x + k + W) % W;
                float s = k == -w / 2 ? 1.14f : k == w / 2 - 1 ? 0.78f : 1f;
                m.set(px, y, mul(mix(m.get(px, y), c, 0.85f), s));
            }
        // the wall beside the column's shadow side
        for (int y = 0; y < H; y++) { int px = (x + w / 2) % W; m.set(px, y, mul(m.get(px, y), 0.86f)); }
    }

    /** Ribbon windows: a solid spandrel panel between the window bands. */
    static void spandrel(Img m, Recipe rc, int seed) {
        base(m, rc.mat == Material.GLASS ? Material.PANEL : rc.mat, rc.accent, seed + 9, 0, H - 44, W, H);
    }

    // ------------------------------------------------------------------ 3. openings

    /**
     * A window set into the wall: reveals (head and left jamb in shadow, the others lit), a frame
     * with {@code panes} panes, glass, and a sill. {@code ribbon}: runs the full width (bands).
     */
    static void window(Img m, Recipe rc, Random r, int x0, int y0, int x1, int y1, int panes, boolean ribbon) {
        if (x1 - x0 < 4 || y1 - y0 < 4) return;
        int frame = 0xFF000000 | rc.frame;
        // reveal: the opening's depth, 2 texels
        int rv = ribbon ? 0 : 2;
        m.rect(x0, y0, x1, y1, mul(0xFF000000 | rc.wall, 0.62f));
        if (!ribbon) { m.rect(x1 - rv, y0, x1, y1, mul(0xFF000000 | rc.wall, 0.9f)); m.rect(x0, y1 - rv, x1, y1, mul(0xFF000000 | rc.wall, 1.05f)); }
        int gx0 = x0 + rv, gy0 = y0 + rv + 1, gx1 = x1 - rv, gy1 = y1 - (ribbon ? 0 : rv);
        glass(m, rc, r, gx0, gy0, gx1, gy1);
        // frame and glazing bars
        m.frame(gx0, gy0, gx1, gy1, 1, frame);
        if (panes > 1) {
            int bw = (gx1 - gx0) / panes;
            for (int p = 1; p < panes; p++) {
                int bx = gx0 + p * bw;
                m.rect(bx - 1, gy0, bx + 1, gy1, frame);
                m.rect(bx + 1, gy0 + 1, bx + 2, gy1 - 1, mul(frame, 0.7f));
            }
        }
        if (!ribbon && y1 - y0 > 40 && r.nextBoolean()) {
            // a transom bar near the top of tall windows
            int ty = gy0 + (gy1 - gy0) / 4;
            m.rect(gx0, ty, gx1, ty + 1, frame);
        }
        // shadow cast by the head onto the glass: top and left
        m.shade(gx0, gy0, gx1, gy0 + 2, 0.7f);
        m.shade(gx0, gy0, gx0 + 2, gy1, 0.8f);
        // sill: a light lip with a dark drip line under it
        if (!ribbon && y1 < H - 14) {
            m.rect(x0 - 1, y1, x1 + 1, y1 + 2, mul(0xFF000000 | mix(0xFFFFFFFF, frame, 0.6f), 1.05f));
            m.rect(x0 - 1, y1 + 2, x1 + 1, y1 + 3, mul(0xFF000000 | rc.wall, 0.7f));
        }
    }

    /**
     * Glass: the sky reflected (bright at the top, darkening down), a soft cloud, and what's
     * behind it in this room: curtains, blinds, a lamp's glow, or just a dark room.
     */
    static void glass(Img m, Recipe rc, Random r, int x0, int y0, int x1, int y1) {
        int tint = 0xFF000000 | rc.glassTint;
        int top = mix(0xFFC4D8EA, tint, 0.45f), bot = mul(tint, 0.42f);
        int h = Math.max(1, y1 - y0);
        int interior = r.nextInt(10);
        int curtain = new int[]{0xFFE8E2D4, 0xFFC8D4E4, 0xFFE4C8C8, 0xFFD8E4C8, 0xFFE8D8B4, 0xFF9AA4B8}[r.nextInt(6)];
        float split = 0.3f + r.nextFloat() * 0.4f;
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) h;
            for (int x = x0; x < x1; x++) {
                float u = (x - x0) / (float) Math.max(1, x1 - x0);
                int c = mix(top, bot, (float) Math.pow(t, 0.8));
                // a diagonal sheen across the pane
                float d = (u + t * 0.6f) % 1f;
                if (d > 0.62f && d < 0.7f) c = mix(c, 0xFFFFFFFF, 0.18f);
                // what's inside
                if (interior < 3) {                   // curtains drawn part way, from both sides
                    if (u < split * 0.5f || u > 1f - split * 0.5f) {
                        float fold = ((x * 3) & 3) == 0 ? 0.85f : 1f;
                        c = mix(c, mul(curtain, fold * (0.7f + 0.3f * (1f - t))), 0.65f);
                    }
                } else if (interior < 5) {            // horizontal blinds, lowered part way
                    if (t < split && (y & 1) == 0) c = mix(c, 0xFFD8D8D0, 0.55f);
                    else if (t < split) c = mix(c, 0xFFB8B8B0, 0.45f);
                } else if (interior == 5) {           // a lamp in a warm room
                    float lx = u - 0.65f, ly = t - 0.6f;
                    float g = Math.max(0f, 1f - (lx * lx + ly * ly) * 9f);
                    c = mix(c, 0xFFE8C890, g * 0.5f);
                } else if (interior == 6 && t > 0.55f) { // furniture silhouettes in the room
                    if (Noise.rnd(x >> 2, 1, (int) rc.seed) > 0.5f) c = mul(c, 0.75f);
                }
                m.set(x, y, c);
            }
        }
    }

    /** Curtain wall: full-height glass between mullions, a dark spandrel at the slab line. */
    static void curtainWall(Img m, Recipe rc, Random r, int seed, boolean ground) {
        int mull = 0xFF000000 | rc.frame;
        glass(m, rc, r, 0, 0, W, H);
        // spandrel (back-painted glass hiding the slab) and transom
        int sp = ground ? 0 : 22;
        if (sp > 0) {
            for (int y = H - sp; y < H; y++) for (int x = 0; x < W; x++) m.set(x, y, mul(mix(0xFF000000 | rc.glassTint, 0xFF1A1E24, 0.6f), 0.9f + 0.1f * ((y & 7) == 0 ? 1 : 0)));
        }
        // mullions: two per bay, the one at the bay edge heavier
        for (int y = 0; y < H; y++) {
            m.set(0, y, mul(mull, 1.1f)); m.set(1, y, mull); m.set(2, y, mul(mull, 0.7f));
            m.set(W / 2, y, mull); m.set(W / 2 + 1, y, mul(mull, 0.75f));
        }
        for (int x = 0; x < W; x++) { m.set(x, H - sp - 1, mull); m.set(x, H - sp, mul(mull, 0.75f)); m.set(x, 30, mul(mull, 0.95f)); }
    }

    /** Open-air corridor of an apartment block: a parapet, the corridor's shadow, a front door and a small window. */
    static void corridorDoor(Img m, Recipe rc, Random r) {
        int wall = 0xFF000000 | rc.wall;
        m.rect(0, 0, W, 86, mul(wall, 0.78f));                    // the corridor wall, in the shade of the slab above
        m.rect(0, 0, W, 10, mul(wall, 0.5f));                     // the ceiling's shadow
        int door = new int[]{0xFFC8B89A, 0xFF8A7A6A, 0xFF6A7A8A, 0xFFD8D4CC, 0xFF5E6E62}[r.nextInt(5)];
        m.rect(12, 26, 34, 86, door);
        m.frame(11, 25, 35, 86, 1, mul(door, 0.65f));
        m.rect(30, 54, 32, 60, 0xFFD0D0D0);                         // handle
        m.rect(14, 18, 32, 22, 0xFFE8E8E0);                         // nameplate light
        m.rect(42, 34, 58, 52, 0xFF9AA8B4);                         // small window with a grille
        for (int x = 42; x < 58; x += 3) m.rect(x, 34, x + 1, 52, 0xFFC0C4C8);
        m.rect(42, 70, 54, 84, 0xFFB8BCB4);                         // gas meter box
        // the corridor parapet in front
        base(m, rc.mat, rc.wall, (int) rc.seed + 77, 0, 86, W, H);
        m.rect(0, 86, W, 88, mul(wall, 1.14f));
    }

    /** The ground-floor entrance bay: a recessed glass door under a canopy (shop-sized buildings) or a front door. */
    static Texture entrance(Recipe rc) {
        Random r = new Random(rc.seed * 13 + 3);
        Img m = new Img(W, H);
        base(m, rc.mat, rc.wall, (int) rc.seed + 211, 0, 0, W, H);
        int frame = 0xFF000000 | rc.frame;
        // recess
        m.rect(8, 34, W - 8, H - 12, mul(0xFF000000 | rc.wall, 0.45f));
        // glass double doors with a transom
        glass(m, rc, r, 12, 40, W - 12, H - 12);
        m.frame(12, 40, W - 12, H - 12, 1, frame);
        m.rect(W / 2 - 1, 40, W / 2 + 1, H - 12, frame);
        m.rect(12, 54, W - 12, 55, frame);
        m.rect(W / 2 - 5, 80, W / 2 - 3, 92, 0xFFE0E0E0);
        m.rect(W / 2 + 3, 80, W / 2 + 5, 92, 0xFFE0E0E0);
        // canopy slab with its shadow
        m.rect(4, 28, W - 4, 34, mul(0xFF000000 | rc.accent, 1.1f));
        m.rect(4, 33, W - 4, 34, mul(0xFF000000 | rc.accent, 0.7f));
        m.shade(8, 34, W - 8, 42, 0.7f);
        // a door light and the building's number plate
        m.rect(W / 2 - 3, 20, W / 2 + 3, 24, 0xFFF4EED8);
        slab(m, rc, H - 12, true);
        weather(m, rc, r, (int) rc.seed + 5, H - 12);
        return m.texture();
    }

    /** Parapets and gables: the material alone (with a little grime). */
    static Texture plain(Recipe rc) {
        Img m = new Img(W, W);
        base(m, rc.mat == Material.GLASS ? Material.PANEL : rc.mat, rc.mat == Material.GLASS ? rc.accent : rc.wall, (int) rc.seed + 333, 0, 0, W, W);
        return m.texture();
    }

    // ------------------------------------------------------------------ 4. fittings

    /** A balcony across the bay: the slab, its front (bars, frosted glass or a wall) and what's out on it. */
    static void balcony(Img m, Recipe rc, Random r, int seed) {
        int top = H - 12, railTop = top - 36;
        // laundry or a futon airing behind the front
        if (r.nextInt(3) == 0) {
            m.rect(4, railTop - 26, W - 4, railTop - 25, 0xFF9A9A9A);
            for (int x = 6; x < W - 8; x += 5 + r.nextInt(4)) {
                int c = new int[]{0xFFF4F4F4, 0xFF6A8AC8, 0xFFE8A0A0, 0xFFF2D060, 0xFF8AC09A, 0xFFF4F4F4, 0xFF3A3A44}[r.nextInt(7)];
                m.rect(x, railTop - 25, x + 4, railTop - 14 + r.nextInt(12), c);
            }
        }
        boolean futon = r.nextInt(5) == 0;
        // the front
        int acc = 0xFF000000 | rc.accent;
        switch (rc.rail) {
            case 0: {
                for (int x = 0; x < W; x++) {
                    m.set(x, railTop, mul(0xFF000000 | rc.frame, 1.1f));
                    m.set(x, railTop + 1, mul(0xFF000000 | rc.frame, 0.8f));
                    if ((x & 3) == 1) for (int y = railTop + 2; y < top; y++) m.set(x, y, mul(0xFF000000 | rc.frame, 0.9f));
                }
                break;
            }
            case 1: {
                for (int y = railTop; y < top; y++) for (int x = 0; x < W; x++) m.set(x, y, mix(m.get(x, y), 0xFFDDE6EC, 0.72f));
                m.rect(0, railTop, W, railTop + 2, mul(0xFF000000 | rc.frame, 1.05f));
                break;
            }
            default: {
                base(m, rc.mat == Material.GLASS ? Material.PANEL : rc.mat, rc.accent, seed + 17, 0, railTop, W, top);
                m.rect(0, railTop, W, railTop + 2, mul(acc, 1.15f));
                break;
            }
        }
        if (futon) m.rect(10, railTop - 4, 40, railTop + 14, new int[]{0xFFF4F0E6, 0xFFE8C8D8, 0xFFC8D8F0}[r.nextInt(3)]);
        if (rc.ac && r.nextBoolean()) acUnit(m, W - 20, railTop - 2);
        // the shadow the balcony above throws on the glass
        m.shade(0, 0, W, 10, 0.72f);
    }

    /** An air-conditioner's outdoor unit: a white box with a round fan grille. */
    static void acUnit(Img m, int x, int y) {
        m.rect(x, y, x + 14, y + 11, 0xFFE4E4DE);
        m.rect(x, y + 10, x + 14, y + 11, 0xFFA8A8A2);
        m.disc(x + 5.5f, y + 5.5f, 3.6f, 0xFF8A8A86);
        m.disc(x + 5.5f, y + 5.5f, 1.2f, 0xFFC8C8C2);
        m.rect(x + 10, y + 2, x + 13, y + 9, 0xFFC8C8C2);
        m.shade(x + 1, y + 11, x + 15, y + 13, 0.7f);
    }

    static void pipe(Img m, int x) {
        for (int y = 0; y < H; y++) {
            m.set(x, y, mul(0xFFB8BCBE, 1.1f));
            m.set(x + 1, y, 0xFFA0A4A6);
            m.set(x + 2, y, mul(0xFFA0A4A6, 0.7f));
            if (y % 32 == 8) { m.set(x - 1, y, 0xFF8A8E90); m.set(x + 3, y, 0xFF8A8E90); }
        }
    }

    // ------------------------------------------------------------------ 5. weathering

    /**
     * Rain streaks from sill corners and slab edges (darker, in the material's own colour), grime
     * soaking up from the slab, and noise blotches; stronger with {@code rc.grime}.
     */
    static void weather(Img m, Recipe rc, Random r, int seed, int slabTop) {
        float g = rc.grime;
        if (g <= 0.01f) return;
        // streaks: a few columns where water runs off a ledge
        int streaks = 2 + r.nextInt(4);
        for (int s = 0; s < streaks; s++) {
            int x = r.nextInt(W);
            int y0 = r.nextInt(H / 2);
            int len = 20 + r.nextInt(60);
            float k = 0.05f + g * 0.12f * r.nextFloat();
            for (int y = y0; y < Math.min(H, y0 + len); y++) {
                float fade = 1f - (y - y0) / (float) len;
                for (int dx = 0; dx < 2; dx++) {
                    int px = (x + dx) % W;
                    m.set(px, y, mul(m.get(px, y), 1f - k * fade * (dx == 0 ? 1f : 0.5f)));
                }
            }
        }
        // grime soaking up from the slab (and settling just above it)
        for (int y = slabTop - 16; y < slabTop; y++) {
            float k = (y - (slabTop - 16)) / 16f * 0.12f * g;
            for (int x = 0; x < W; x++) m.set(x, y, mul(m.get(x, y), 1f - k * (0.6f + 0.4f * Noise.rnd(x, y, seed + 61))));
        }
        // blotches
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                float n = Noise.fbm(x, y, W, H, seed + 71);
                if (n > 0.62f) m.set(x, y, mul(m.get(x, y), 1f - (n - 0.62f) * g * 0.6f));
            }
    }
}
