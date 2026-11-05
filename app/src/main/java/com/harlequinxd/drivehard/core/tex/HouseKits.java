package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.mul;

/**
 * House kits: a house designed whole and painted whole. A kit is a simple closed shape (four
 * walls rising from a concrete plinth straight into a roof of tiles, with no overhangs or
 * separate parts), and one texture holding all four of its walls with everything on them
 * painted in: wall materials, windows and their sills and canopies, the front door under its
 * canopy, a balcony rail, gable vents, air-conditioner units, the shadow under the roof edge,
 * and weathering.
 *
 * <p>The texture is 256 x 256: the front wall in the top-left quarter, the back in the top-right,
 * the left wall (as seen from outside) bottom-left and the right bottom-right. Each quarter maps
 * its wall's full width across and its full height (plinth to the top of any gable) up, so a
 * kit stretched a little to fit a lot keeps its windows in place.</p>
 */
public final class HouseKits {
    private HouseKits() {}

    public static final int SIZE = 256, Q = 128;
    public static final int FRONT = 0, BACK = 1, LEFT = 2, RIGHT = 3;
    /** Roofs: ridge along the front (gables at the sides), ridge front to back (gables front and back), hipped, mono-pitch rising to the back, flat. */
    public static final int GABLE = 0, GABLE_FRONT = 1, HIP = 2, SHED = 3, FLAT = 4;
    public static final float PLINTH = 0.4f, MODULE = 0.91f;

    /** One house kit: its shape, its texture and its roof covering. */
    public static final class Kit implements java.io.Serializable {
        public int tex = -1, roofTex = -1;
        public float W, D, floorH, rise;
        public int floors, roof;
        public boolean farm;

        /** Height of the eaves above the lot base (the top of the walls under the roof edge). */
        public float eave() { return PLINTH + floors * floorH + (roof == FLAT ? 0.45f : 0f); }

        /** Top of wall {@code face} at fraction {@code t} (0..1) across it, left to right seen from outside. */
        public float top(int face, float t) {
            float e = eave();
            switch (roof) {
                case GABLE: return face >= LEFT ? e + rise * (1f - Math.abs(2f * t - 1f)) : e;
                case GABLE_FRONT: return face <= BACK ? e + rise * (1f - Math.abs(2f * t - 1f)) : e;
                case SHED:
                    if (face == FRONT) return e;
                    if (face == BACK) return e + rise;
                    return face == LEFT ? e + rise * t : e + rise * (1f - t);
                default: return e;
            }
        }

        /** The tallest point of a wall (its texture quarter spans plinth to here). */
        public float height(int face) {
            return Math.max(Math.max(top(face, 0f), top(face, 1f)), top(face, 0.5f));
        }

        public float width(int face) { return face <= BACK ? W : D; }
    }

    // ------------------------------------------------------------------ designing a kit

    /**
     * The materials and openings a kit is painted from: chosen by the library from its house
     * wall materials, windows and doors (textures it passes in).
     */
    public static final class Parts {
        public Texture mat1, mat2, win, small, garden, door, foundation;
        public int trim, rail;
        public boolean twoTone, balcony, hisashi, doorLeft, wideDoor;
        /** A built-in garage (roller shutter) at the far end of the front from the door. */
        public boolean garage;
        /** Security grilles over the ground-floor windows (men-goshi), and their colour. */
        public boolean grille;
        public int shutter = 0xB8BCBE;
        public float winH = 1.2f;
    }

    // ------------------------------------------------------------------ painting

    public static Texture paint(Kit k, Parts p, long seed) {
        Img m = new Img(SIZE, SIZE);
        Random r = new Random(seed);
        for (int f = 0; f < 4; f++) face(m, k, p, f, new Random(seed * 31 + f));
        return m.texture();
    }

    /** Pixel x of {@code s} metres across face f (of width w), and pixel y of height {@code y} above the base. */
    private static int px(int f, float s, float w) { return (f & 1) * Q + Math.round(s / w * (Q - 1)); }
    private static int py(int f, float y, float h) { return (f >> 1) * Q + Math.round((1f - y / h) * (Q - 1)); }

    private static void face(Img m, Kit k, Parts p, int f, Random r) {
        float w = k.width(f), h = k.height(f);
        int qx = (f & 1) * Q, qy = (f >> 1) * Q;
        float mx = w / Q, my = h / Q;               // metres per texel
        // 1. wall materials by storey, the plinth, the floor band of two-tone houses
        float floor1 = PLINTH + k.floorH;
        for (int y = 0; y < Q; y++) {
            float hy = (1f - (y + 0.5f) / Q) * h;
            for (int x = 0; x < Q; x++) {
                float s = (x + 0.5f) * mx;
                int c;
                if (hy < PLINTH) c = sample(p.foundation, s, hy, 2f);
                else c = sample(hy < floor1 || !p.twoTone ? p.mat1 : p.mat2, s, hy, 2f);
                m.set(qx + x, qy + y, c);
            }
        }
        // the plinth's top edge catches the light
        line(m, f, w, h, 0f, w, PLINTH, 0.05f, mul(0xFFC8C4BC, 1.05f));
        if (p.twoTone && k.floors > 1) {
            line(m, f, w, h, 0f, w, floor1 - 0.06f, 0.14f, 0xFF000000 | p.trim);
            line(m, f, w, h, 0f, w, floor1 - 0.08f, 0.03f, mul(0xFF000000 | p.trim, 0.6f));
        }
        // 2. openings, storey by storey
        for (int st = 0; st < k.floors; st++) {
            float y0 = PLINTH + st * k.floorH;
            for (float[] o : plan(k, p, f, st, r)) opening(m, k, p, f, w, h, o, y0, r);
        }
        // a vent high in a gable
        if (k.height(f) - k.eave() > 1.2f && f != BACK) {
            float cs = w * 0.5f, vy = k.eave() + (k.height(f) - k.eave()) * 0.35f;
            rectM(m, f, w, h, cs - 0.3f, vy, cs + 0.3f, vy + 0.4f, mul(0xFF000000 | p.trim, 0.9f));
            for (float yy = vy + 0.05f; yy < vy + 0.38f; yy += 0.1f) line(m, f, w, h, cs - 0.26f, cs + 0.26f, yy, 0.03f, mul(0xFF000000 | p.trim, 0.55f));
        }
        // an air-conditioner unit beside the house
        if (f >= LEFT && r.nextInt(3) > 0) {
            float s = w * (0.25f + r.nextFloat() * 0.5f);
            acUnit(m, f, w, h, s, 0.15f);
            if (k.floors > 1 && r.nextBoolean()) acUnit(m, f, w, h, w - s, PLINTH + k.floorH + 0.3f);
        }
        // 3. the roof edge: its shadow on the wall, following the gable's slope
        for (int x = 0; x < Q; x++) {
            float t = (x + 0.5f) / Q;
            float top = k.top(f, t);
            int yt = py(f, top, h) - qy;
            int band = Math.max(2, Math.round(0.45f / my));
            // above the wall's top: never seen (the roof) - fill with the roof's dark underside
            for (int y = 0; y < Math.max(0, yt); y++) m.set(qx + x, qy + y, 0xFF2A2624);
            for (int y = Math.max(0, yt); y < Math.min(Q, yt + band); y++) {
                float u = (y - yt) / (float) band;
                m.set(qx + x, qy + y, mul(m.get(qx + x, qy + y), 0.5f + 0.5f * u));
            }
            if (k.roof == FLAT && yt >= 0 && yt + 2 < Q) {
                // parapet coping
                m.set(qx + x, qy + yt, mul(0xFF000000 | p.trim, 1.15f));
                m.set(qx + x, qy + yt + 1, 0xFF000000 | p.trim);
            }
        }
        // 4. weathering: grime soaking up from the plinth, streaks under the sills, blotches
        for (int y = 0; y < Q; y++) {
            float hy = (1f - (y + 0.5f) / Q) * h;
            if (hy > PLINTH && hy < PLINTH + 0.6f) {
                float g = (1f - (hy - PLINTH) / 0.6f) * 0.12f;
                for (int x = 0; x < Q; x++) m.set(qx + x, qy + y, mul(m.get(qx + x, qy + y), 1f - g * (0.5f + Noise.rnd(x, y, f + 5))));
            }
        }
        int seed = r.nextInt();
        for (int y = 0; y < Q; y++)
            for (int x = 0; x < Q; x++) {
                float n = Noise.fbm(x, y, Q, Q, seed);
                if (n > 0.62f) m.set(qx + x, qy + y, mul(m.get(qx + x, qy + y), 1f - (n - 0.62f) * 0.25f));
            }
    }

    /**
     * The openings of one storey of one face, on the 910 mm module: {kind, s0, s1, y0, y1} with s
     * in metres from the face's left (seen from outside), y above the storey floor. Kinds:
     * 0 window, 1 small window, 2 full-height garden window, 3 door, 4 balcony door.
     */
    static java.util.List<float[]> plan(Kit k, Parts p, int f, int st, Random r) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        float len = k.width(f);
        int mods = (int) (len / MODULE);
        if (mods < 2) return out;
        float margin = (len - mods * MODULE) * 0.5f;
        boolean[] used = new boolean[mods];
        float winTop = 2.0f, winBot = winTop - p.winH;
        if (f == FRONT && st == 0) {
            int dm = p.wideDoor ? 2 : 1;
            int at = p.doorLeft ? 1 : mods - 1 - dm;
            at = Math.max(0, Math.min(mods - dm, at));
            float c = margin + (at + dm * 0.5f) * MODULE, half = p.wideDoor ? 0.95f : 0.6f;
            out.add(new float[]{3, c - half, c + half, 0f, 2.1f});
            for (int i = Math.max(0, at - 1); i < Math.min(mods, at + dm + 1); i++) used[i] = true;
        }
        if (f == FRONT && st == 0 && p.garage && mods >= 7) {
            // the garage: about 2.7 m of shutter at the other end from the door
            int gm = 3, at = p.doorLeft ? mods - gm : 0;
            boolean free = true;
            for (int i = at; i < at + gm; i++) free &= !used[i];
            if (free) {
                out.add(new float[]{5, margin + at * MODULE + 0.1f, margin + (at + gm) * MODULE - 0.1f, 0f, 2.25f});
                for (int i = Math.max(0, at - 1); i < Math.min(mods, at + gm + 1); i++) used[i] = true;
            }
        }
        if (f == FRONT && st == 1 && p.balcony && mods >= 4) {
            int at = mods / 2 - 1;
            out.add(new float[]{4, margin + at * MODULE + 0.08f, margin + (at + 2) * MODULE - 0.08f, 0.05f, 2.0f});
            for (int i = Math.max(0, at - 1); i < Math.min(mods, at + 3); i++) used[i] = true;
        }
        boolean side = f >= LEFT;
        int i = 1;
        while (i < mods - 1) {
            if (used[i]) { i++; continue; }
            float roll = r.nextFloat();
            // windows are two modules wide (about 1.6 m): where only one module is free, a small
            // square window instead - never a tall slot
            boolean two = i + 1 < mods - 1 && !used[i + 1];
            int kind;
            if (side) {
                if (roll > 0.55f) { i++; continue; }
                kind = roll < 0.25f || !two ? 1 : 0;
            } else if (f == BACK && st == 0 && two && roll < 0.55f) kind = 2;
            else if (roll < 0.12f || !two) kind = 1;
            else kind = 0;
            int span = kind == 1 ? 1 : 2;
            float inset = kind == 1 ? 0.15f : 0.1f;
            float y0 = kind == 1 ? 1.2f : kind == 2 ? 0.05f : winBot, y1 = kind == 1 ? 1.8f : 2.0f;
            out.add(new float[]{kind, margin + i * MODULE + inset, margin + (i + span) * MODULE - inset, y0, y1});
            i += span + 1;
        }
        return out;
    }

    private static void opening(Img m, Kit k, Parts p, int f, float w, float h, float[] o, float floorY, Random r) {
        int kind = (int) o[0];
        float s0 = o[1], s1 = o[2], y0 = floorY + o[3], y1 = Math.min(k.top(f, 0.5f) - 0.2f, floorY + o[4]);
        if (y1 - y0 < 0.3f) return;
        if (kind == 5) {
            // garage: a roller shutter in a dark opening under a steel lintel
            int c = 0xFF000000 | p.shutter;
            rectM(m, f, w, h, s0 - 0.08f, y0, s1 + 0.08f, y1 + 0.18f, 0xFF2E2C2A);
            int x0 = px(f, s0, w), x1 = px(f, s1, w), yt = py(f, y1, h), yb = py(f, y0, h);
            for (int y = yt; y < yb; y++) for (int x = x0; x < x1; x++) m.set(x, y, mul(c, (y - yt) % 3 == 0 ? 1.1f : (y - yt) % 3 == 2 ? 0.82f : 0.97f));
            rectM(m, f, w, h, s0 - 0.15f, y1, s1 + 0.15f, y1 + 0.16f, mul(0xFF000000 | p.trim, 0.8f));
            shadeM(m, f, w, h, s0, y1 - 0.2f, s1, y1, 0.72f);
            rectM(m, f, w, h, (s0 + s1) / 2 - 0.15f, y0 + 0.1f, (s0 + s1) / 2 + 0.15f, y0 + 0.16f, mul(c, 0.6f));
            return;
        }
        Texture t = kind == 1 ? p.small : kind == 2 || kind == 4 ? p.garden : kind == 3 ? p.door : p.win;
        int x0 = px(f, s0, w), x1 = px(f, s1, w), yt = py(f, y1, h), yb = py(f, y0, h);
        // the window or door itself (its own texture already carries the reveal's shading)
        for (int y = yt; y < yb; y++)
            for (int x = x0; x < x1; x++) {
                int tx = (int) ((x - x0 + 0.5f) / Math.max(1, x1 - x0) * t.w), ty = (int) ((y - yt + 0.5f) / Math.max(1, yb - yt) * t.h);
                m.set(x, y, t.px[Math.min(t.h - 1, ty) * t.w + Math.min(t.w - 1, tx)]);
            }
        // security grilles over ground-floor windows: vertical bars in a frame
        if (p.grille && kind == 0 && floorY < PLINTH + 0.1f) {
            int gc = 0xFF000000 | p.rail;
            rectM(m, f, w, h, s0 - 0.04f, y0, s1 + 0.04f, y0 + 0.05f, gc);
            rectM(m, f, w, h, s0 - 0.04f, y1 - 0.05f, s1 + 0.04f, y1, gc);
            for (float s = s0; s <= s1; s += 0.12f) rectM(m, f, w, h, s, y0, s + 0.035f, y1, mul(gc, 0.9f));
        }
        // a sill under windows, with its drip shadow
        if (kind == 0 || kind == 1) {
            rectM(m, f, w, h, s0 - 0.06f, y0 - 0.06f, s1 + 0.06f, y0, 0xFFC8CCD0);
            rectM(m, f, w, h, s0 - 0.06f, y0 - 0.12f, s1 + 0.06f, y0 - 0.06f, mul(sampleAt(m, f, w, h, (s0 + s1) / 2, y0 - 0.2f), 0.7f));
            // a rain streak running down from each sill end
            streak(m, f, w, h, s0, y0 - 0.12f, 0.9f);
            streak(m, f, w, h, s1 - 0.04f, y0 - 0.12f, 0.9f);
        }
        // a little canopy over windows (traditional houses) and over the front door
        if ((p.hisashi && kind == 0) || kind == 3) {
            float cy = y1 + 0.12f, ext = kind == 3 ? 0.4f : 0.15f;
            rectM(m, f, w, h, s0 - ext, cy, s1 + ext, cy + (kind == 3 ? 0.16f : 0.1f), mul(0xFF000000 | p.trim, 0.85f));
            rectM(m, f, w, h, s0 - ext, cy - (kind == 3 ? 0.3f : 0.14f), s1 + ext, cy, 0x00000000);   // marker: shaded below
            shadeM(m, f, w, h, s0 - ext, cy - (kind == 3 ? 0.3f : 0.14f), s1 + ext, cy, 0.62f);
        }
        // the balcony: its slab, and the rail in front of the balcony door
        if (kind == 4) {
            float a = s0 - 0.5f, b = s1 + 0.5f, fy = floorY;
            float railTop = fy + 1.05f;
            int rail = 0xFF000000 | p.rail;
            if ((p.rail & 1) == 0) {
                for (float s = a; s < b; s += 0.12f) rectM(m, f, w, h, s, fy, s + 0.04f, railTop, mul(rail, 0.9f));
            } else {
                for (int y = py(f, railTop, h); y < py(f, fy, h); y++)
                    for (int x = px(f, a, w); x < px(f, b, w); x++) m.set(x, y, mix(m.get(x, y), rail, 0.8f));
            }
            rectM(m, f, w, h, a, railTop - 0.06f, b, railTop, mul(rail, 1.15f));
            rectM(m, f, w, h, a, fy - 0.18f, b, fy, mul(0xFFC8C4BC, 0.95f));
            shadeM(m, f, w, h, a, fy - 0.5f, b, fy - 0.18f, 0.65f);
            if (r.nextInt(3) == 0) {
                // laundry hung out over the rail
                for (float s = a + 0.2f; s < b - 0.3f; s += 0.3f + r.nextFloat() * 0.2f)
                    rectM(m, f, w, h, s, railTop - 0.1f - r.nextFloat() * 0.5f, s + 0.22f, railTop + 0.6f,
                            new int[]{0xFFF4F4F4, 0xFF6A8AC8, 0xFFE8A0A0, 0xFFF2D060, 0xFF8AC09A}[r.nextInt(5)]);
            }
        }
    }

    private static void acUnit(Img m, int f, float w, float h, float s, float y) {
        rectM(m, f, w, h, s - 0.4f, y, s + 0.4f, y + 0.6f, 0xFFE4E4DE);
        rectM(m, f, w, h, s - 0.4f, y, s + 0.4f, y + 0.06f, 0xFFA8A8A2);
        rectM(m, f, w, h, s - 0.3f, y + 0.12f, s + 0.08f, y + 0.5f, 0xFF8A8A86);
        shadeM(m, f, w, h, s - 0.38f, y - 0.15f, s + 0.45f, y, 0.7f);
    }

    private static void streak(Img m, int f, float w, float h, float s, float y, float len) {
        int x = px(f, s, w), yt = py(f, y, h), yb = py(f, Math.max(PLINTH, y - len), h);
        for (int yy = yt; yy < yb; yy++) {
            float fade = 1f - (yy - yt) / (float) Math.max(1, yb - yt);
            m.set(x, yy, mul(m.get(x, yy), 1f - 0.12f * fade));
        }
    }

    // ------------------------------------------------------------------ helpers (metres on a face)

    private static int sample(Texture t, float s, float y, float tileM) {
        // box-filter 2 x 2 texels of a 2 m tile at this point (the kit is painted coarser than the tile)
        int u = (int) (s / tileM * t.w), v = (int) ((tileM - y % tileM) / tileM * t.h);
        int a = t.px[(Math.floorMod(v, t.h)) * t.w + Math.floorMod(u, t.w)], b = t.px[(Math.floorMod(v, t.h)) * t.w + Math.floorMod(u + 1, t.w)];
        int c = t.px[(Math.floorMod(v + 1, t.h)) * t.w + Math.floorMod(u, t.w)], d = t.px[(Math.floorMod(v + 1, t.h)) * t.w + Math.floorMod(u + 1, t.w)];
        return mix(mix(a, b, 0.5f), mix(c, d, 0.5f), 0.5f);
    }

    private static int sampleAt(Img m, int f, float w, float h, float s, float y) {
        int x = Math.max((f & 1) * Q, Math.min((f & 1) * Q + Q - 1, px(f, s, w)));
        int yy = Math.max((f >> 1) * Q, Math.min((f >> 1) * Q + Q - 1, py(f, y, h)));
        return m.get(x, yy);
    }

    private static void rectM(Img m, int f, float w, float h, float s0, float y0, float s1, float y1, int c) {
        if ((c >>> 24) == 0) return;
        int qx = (f & 1) * Q, qy = (f >> 1) * Q;
        int x0 = Math.max(qx, px(f, s0, w)), x1 = Math.min(qx + Q, px(f, s1, w) + 1);
        int ya = Math.max(qy, py(f, y1, h)), yb = Math.min(qy + Q, py(f, y0, h) + 1);
        for (int y = ya; y < yb; y++) for (int x = x0; x < x1; x++) m.set(x, y, c);
    }

    private static void shadeM(Img m, int f, float w, float h, float s0, float y0, float s1, float y1, float k) {
        int qx = (f & 1) * Q, qy = (f >> 1) * Q;
        int x0 = Math.max(qx, px(f, s0, w)), x1 = Math.min(qx + Q, px(f, s1, w) + 1);
        int ya = Math.max(qy, py(f, y1, h)), yb = Math.min(qy + Q, py(f, y0, h) + 1);
        for (int y = ya; y < yb; y++) for (int x = x0; x < x1; x++) m.set(x, y, mul(m.get(x, y), k));
    }

    private static void line(Img m, int f, float w, float h, float s0, float s1, float y, float t, int c) {
        rectM(m, f, w, h, s0, y, s1, y + t, c);
    }
}
