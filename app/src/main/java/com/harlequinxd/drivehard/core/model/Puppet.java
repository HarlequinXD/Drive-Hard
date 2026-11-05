package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;
import com.harlequinxd.drivehard.core.render.Renderer;
import com.harlequinxd.drivehard.core.render.Texture;

import java.util.ArrayList;
import java.util.List;

/**
 * A sprite puppet: the character is a skeleton (the same one, and the same animations, as
 * {@link CharacterModel}) dressed in hand-pixelled 2D parts. Every frame each part is drawn as a
 * camera-facing sprite pinned to its bone: limbs, body and head stretch from one joint to the
 * next, and each part picks one of 8 views from the angle between its bone and the camera
 * (5 drawn: front, front three-quarter, side, back three-quarter, back; the other 3 mirrored).
 * The depth buffer (or the ordering table) layers them, so an arm behind the body is hidden.
 *
 * Art is at 32 pixels per metre, with a selective outline added around each part's sides.
 */
public final class Puppet {
    public static final float PPM = 32f;

    // ---------------------------------------------------------------- palette

    private static int pal(char c) {
        switch (c) {
            case 'H': return 0xFF4A4A5E;   // hair sheen
            case 'h': return 0xFF26242E;   // black hair
            case 'd': return 0xFF141218;   // hair shadow
            case 'W': return 0xFF7A80A4;   // hair highlight
            case 'S': return 0xFFF2C8A2;   // skin
            case 's': return 0xFFD49C78;   // skin shade
            case 'z': return 0xFFAE7660;   // skin deep shade
            case 'E': return 0xFF2A5AB4;   // blue eyes
            case 'e': return 0xFF1C2A48;   // brows, lashes
            case 'M': return 0xFFC0606A;   // lips
            case 'T': return 0xFFF4F4F0;   // tank top
            case 't': return 0xFFC8CCD8;   // tank top shade
            case 'u': return 0xFF9AA0B4;   // tank top deep shade
            case 'J': return 0xFF41679E;   // jeans
            case 'j': return 0xFF2C4876;   // jeans shade
            case 'n': return 0xFF6E93C8;   // jeans light, seams
            case 'L': return 0xFF5A3C2C;   // belt
            case 'F': return 0xFFF6F6F6;   // trainers
            case 'f': return 0xFFB0B2BE;   // trainer shade
            case 'k': return 0xFF2E3240;   // soles
            case 'R': return 0xFFE0263A;   // hair tie
            default: return 0;
        }
    }

    // ---------------------------------------------------------------- the parts, hand pixelled
    // Views: front, front three-quarter (turned toward screen right), side (facing right),
    // back three-quarter, back. Segment parts are drawn upright: the top row is the far joint.

    private static final String[] HEAD = {
            // front
            ".dhhhd.\n" +
            "dhHWHhd\n" +
            "dhhHhhd\n" +
            "dhSSShd\n" +
            "dSESESd\n" +
            "dsSSSsd\n" +
            ".sSMSs.\n" +
            "..sSs..\n" +
            "..sSs..\n" +
            "..sSs..",
            // front three-quarter
            ".dhhhh.\n" +
            "dhHWHhh\n" +
            "dhhHhhh\n" +
            "dhhSSSh\n" +
            "dhsESES\n" +
            "dhsSSSs\n" +
            ".dssSMs\n" +
            "...sSs.\n" +
            "...sSs.\n" +
            "...sSs.",
            // side
            "..dhhh.\n" +
            ".dhHWHh\n" +
            "dhhhHhh\n" +
            "dhhhhSS\n" +
            "dhhsSeS\n" +
            "dhhsSSS\n" +
            ".dhsSMs\n" +
            "...sSs.\n" +
            "...sS..\n" +
            "...sS..",
            // back three-quarter
            ".dhhhh.\n" +
            "dhHWHhd\n" +
            "dhHHhhd\n" +
            "dhhHhhs\n" +
            "dhhhhsS\n" +
            "ddhhhsS\n" +
            ".sdhdss\n" +
            "..sSs..\n" +
            "..sSs..\n" +
            "..sSs..",
            // back
            ".dhhhd.\n" +
            "dhHWHhd\n" +
            "dhHHHhd\n" +
            "dhhHhhd\n" +
            "dhhhhhd\n" +
            "ddhhhdd\n" +
            ".sdhds.\n" +
            "..sSs..\n" +
            "..sSs..\n" +
            "..sSs..",
    };

    private static final String[] PONY = {
            ".R.\n" +
            "hHh\n" +
            "hHh\n" +
            "hWh\n" +
            "hHd\n" +
            "hHd\n" +
            "dhd\n" +
            ".d.",
    };

    private static final String[] TORSO = {
            // front: a white tank top, bare shoulders
            "....sSSSs....\n" +
            "...ssSSSss...\n" +
            "..sSSSSSSSs..\n" +
            ".sSTSSSSSTSs.\n" +
            "..sTTSsSTTs..\n" +
            "..tTTTTTTTt..\n" +
            "..tTTTuTTTt..\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "....tTTTt....\n" +
            "....tTTTt....\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "...utttttu...",
            // front three-quarter
            "....sSSSs....\n" +
            "...ssSSSss...\n" +
            "..sSSSSSSSs..\n" +
            ".sSTSSSSSTSs.\n" +
            "..sTTSsSTTT..\n" +
            "..tTTTTTTTTt.\n" +
            "..tTTTTTuTTt.\n" +
            "...tTTTTTTt..\n" +
            "...tTTTTTt...\n" +
            "....tTTTTt...\n" +
            "....tTTTTt...\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "...utttttu...",
            // side (facing right)
            ".....sSs.....\n" +
            "....sSSSs....\n" +
            "....sSSSS....\n" +
            "....tTTSS....\n" +
            "...tTTTTTTt..\n" +
            "...tTTTTTTT..\n" +
            "...tTTTTTTt..\n" +
            "....tTTTTt...\n" +
            "....tTTTt....\n" +
            "....tTTTt....\n" +
            "...tTTTTt....\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "...utttttu...",
            // back three-quarter
            "....sSSSs....\n" +
            "...ssSSSss...\n" +
            "..sSSSSSSSs..\n" +
            ".sSTSSSSSTs..\n" +
            "..tTTTTTTTs..\n" +
            "..tTTTTTTTt..\n" +
            "..tTTTTTTTt..\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "....tTTTt....\n" +
            "....tTTTt....\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "...utttttu...",
            // back
            "....sSSSs....\n" +
            "...ssSSSss...\n" +
            "..sSSSSSSSs..\n" +
            ".sSTSSSSSTSs.\n" +
            "..tTTTTTTTt..\n" +
            "..tTTTTTTTt..\n" +
            "..tTTTTTTTt..\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "....tTTTt....\n" +
            "....tTTTt....\n" +
            "...tTTTTTt...\n" +
            "...tTTTTTt...\n" +
            "...utttttu...",
    };

    private static final String[] HIPS = {
            // front: belt, fly, the hips flaring
            ".LLLLLLLLL.\n" +
            "jJJJJnJJJJj\n" +
            "jJJJJnJJJJj\n" +
            "jJJJJnJJJJj\n" +
            "jJJJJjJJJJj\n" +
            "jJJJJjJJJJj\n" +
            ".jJJj.jJJj.\n" +
            ".jjj...jjj.",
            // front three-quarter
            ".LLLLLLLLL.\n" +
            "jJJJJJnJJJj\n" +
            "jJJJJJnJJJj\n" +
            "jJJJJJnJJJj\n" +
            "jJJJJJjJJJj\n" +
            "jJJJJJjJJj.\n" +
            ".jJJj.jJJj.\n" +
            ".jjj...jjj.",
            // side (facing right)
            "..LLLLLLL..\n" +
            "..jJJJJJJ..\n" +
            ".jJJJJJnJ..\n" +
            ".jJJJJJnJ..\n" +
            ".jJJJJJnJ..\n" +
            "..jJJJJnj..\n" +
            "..jJJJJj...\n" +
            "...jjjj....",
            // back three-quarter: pockets
            ".LLLLLLLLL.\n" +
            "jJJJJJJJJJj\n" +
            "jJnnJJJnnJj\n" +
            "jJnJJJJJnJj\n" +
            "jJJJJjJJJJj\n" +
            "jJJJJjJJJJj\n" +
            ".jJJj.jJJj.\n" +
            ".jjj...jjj.",
            // back
            ".LLLLLLLLL.\n" +
            "jJJJJJJJJJj\n" +
            "jJnnJJJnnJj\n" +
            "jJnJJJJJnJj\n" +
            "jJJJJjJJJJj\n" +
            "jJJJJjJJJJj\n" +
            ".jJJj.jJJj.\n" +
            ".jjj...jjj.",
    };

    private static final String[] THIGH = {
            "JJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\nJJJj\n.JJ.",
            "JJJj\nJJJj\nJJnj\nJJJj\nJJJj\nJJnj\nJJJj\nJJJj\nJJnj\nJJJj\nJJJj\nJJnj\nJJnj\nJJnj\n.Jn.",
            "JJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\nJJnj\n.Jn.",
            "jJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njjJj\njJJj\njJJj\njJJj\njJJj\n.JJ.",
            "jJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njJJj\njJjj\njJJj\njJJj\njJJj\njJJj\n.JJ.",
    };

    private static final String[] SHIN = {
            ".J.\nJJj\nJJj\nJJj\nJJj\nJJj\nJJj\nJJj\nJJj\nJJj\nJJj\nJJj\nJJj\njjj\njjj",
            ".J.\nJJj\nJnj\nJJj\nJJj\nJnj\nJJj\nJJj\nJnj\nJJj\nJJj\nJJj\nJJj\njjj\njjj",
            ".n.\nJnj\nJnj\nJnj\nJnj\nJnj\nJnj\nJnj\nJnj\nJnj\nJnj\nJnj\nJnj\njjj\njjj",
            ".J.\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njjj\njjj",
            ".J.\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njJj\njjj\njjj",
    };

    private static final String[] UPPER_ARM = {"SS\nSS\nSs\nSs\nSs\nSs\nSs\nSs\nSs\nSs\nss"};
    private static final String[] FOREARM = {"Ss\nSs\nSs\nSs\nSs\nSs\nSs\nSs\nss\nss"};
    private static final String[] HAND = {"Ss\nSS\nSs\ns."};

    /** Trainers, upright, sole on the bottom row, the ankle at the top. */
    private static final String[] SHOE = {
            ".FF.\n" +
            "FFFF\n" +
            "kkkk",
            ".FF..\n" +
            "FFFFf\n" +
            "kkkkk",
            ".FF....\n" +
            "fFFFFFF\n" +
            "kkkkkkk",
            "..FF.\n" +
            "fFFFF\n" +
            "kkkkk",
            ".ff.\n" +
            "fFFf\n" +
            "kkkk",
    };

    // ---------------------------------------------------------------- rig

    private static final int SEG = 0, UPRIGHT = 1;

    /** One part: which bone, how it is drawn, its views in the atlas. */
    private static final class Part {
        final int bone, kind;
        /** Segment: from (x,y,z)0 to (x,y,z)1 in bone space; upright: the anchor (bottom centre). */
        final float[] a, b;
        /** Rows past each joint (overlap), in pixels. */
        final int padTop, padBottom;
        /** Which bone's facing chooses the view. */
        final int viewBone;
        final float bias;
        /** Rounded, outlined ends (the far end is the top row). */
        boolean capTop, capBottom;
        /** Drawn width against the art's own (build, and the body's scale). */
        float width = 1f;
        int[][] rect;           // per drawn view: x, y, w, h in the atlas

        Part(int bone, int kind, float[] a, float[] b, int padTop, int padBottom, int viewBone, float bias) {
            this.bone = bone; this.kind = kind; this.a = a; this.b = b;
            this.padTop = padTop; this.padBottom = padBottom; this.viewBone = viewBone; this.bias = bias;
        }
    }

    private final List<Part> parts = new ArrayList<>();
    private final CharacterModel rig;
    public final Texture atlas;
    /** The atlas's slot in the renderer's texture table (set by the game). */
    public int tex = -1;
    /** Body scale against the reference 1.68 m woman (parts are placed and sized by it). */
    private final float k;
    /** Colour of each art letter. */
    private final int[] palette = new int[128];

    /** The player: black ponytail, blue eyes, white tank top, jeans, white trainers. */
    public Puppet() { this(null); }

    /**
     * A pedestrian dressed from a {@link CharacterSpec}: the player's hand-pixelled parts are the
     * templates, recoloured and reshaped for the hair style, hat, face, clothes and build.
     */
    public Puppet(CharacterSpec spec) {
        rig = new CharacterModel(spec == null ? CharacterSpec.player() : spec, false);
        k = spec == null ? 1f : rig.scale();
        for (char c = 0; c < 128; c++) palette[c] = pal(c);
        if (spec != null) colours(spec);
        boolean male = spec != null && !spec.female;
        float girth = spec == null ? 1f : 1f + spec.build * 0.3f;
        float bodyW = girth * (male ? 1.1f : 1f), limbW = spec == null ? 1f : 1f + spec.build * 0.22f;
        CharacterSpec.Hair hair = spec == null ? CharacterSpec.Hair.PONYTAIL : spec.hair;

        List<String[]> arts = new ArrayList<>();
        add(CharacterModel.HEAD, SEG, v(0, -0.07f, 0), v(0, 0.245f, 0), CharacterModel.HEAD, -0.03f, 1f);
        arts.add(spec == null ? HEAD : headArt(spec));
        if (hair == CharacterSpec.Hair.PONYTAIL) {
            add(CharacterModel.PONY, SEG, v(0, 0.01f, 0), v(0, -0.21f, 0), CharacterModel.HEAD, 0.04f, 1f);
            arts.add(PONY);
        } else if (hair == CharacterSpec.Hair.BUN && (spec.hat == CharacterSpec.Hat.NONE)) {
            add(CharacterModel.PONY, SEG, v(0, 0.06f, 0.01f), v(0, -0.04f, 0.01f), CharacterModel.HEAD, 0.04f, 1f);
            arts.add(BUN);
        }
        add(CharacterModel.CHEST, SEG, v(0, -0.02f, 0), v(0, 0.42f, 0), CharacterModel.CHEST, 0.03f, bodyW);
        arts.add(spec == null ? TORSO : torsoArt(spec));
        add(CharacterModel.PELVIS, SEG, v(0, -0.155f, 0), v(0, 0.085f, 0), CharacterModel.PELVIS, 0.04f, girth);
        arts.add(spec == null ? HIPS : hipsArt(spec));
        if (spec != null && spec.bottom == CharacterSpec.Bottom.SKIRT) {
            // one panel hanging from the waist over both legs; the legs swing underneath it
            float hem = spec.top == CharacterSpec.Top.DRESS ? -0.52f : -0.47f;
            add(CharacterModel.PELVIS, SEG, v(0, -0.06f, 0.02f), v(0, hem, 0.02f), CharacterModel.PELVIS, -0.13f, girth);
            arts.add(SKIRT);
        }
        int[][] limbs = {
                {CharacterModel.THIGH_L, 0}, {CharacterModel.THIGH_R, 0},
                {CharacterModel.SHIN_L, 1}, {CharacterModel.SHIN_R, 1},
                {CharacterModel.UARM_L, 2}, {CharacterModel.UARM_R, 2},
                {CharacterModel.FARM_L, 3}, {CharacterModel.FARM_R, 3},
                {CharacterModel.FARM_L, 4}, {CharacterModel.FARM_R, 4},
                {CharacterModel.FOOT_L, 5}, {CharacterModel.FOOT_R, 5},
        };
        for (int[] l : limbs) {
            switch (l[1]) {
                // the ends reach a little past each joint, so the rounded caps overlap like a ball joint
                case 0: caps(add(l[0], SEG, v(0, 0.035f, 0), v(0, -0.45f, 0), CharacterModel.PELVIS, 0.0f, limbW), true, false);
                    arts.add(spec == null ? THIGH : thighArt(spec)); break;
                case 1: caps(add(l[0], SEG, v(0, 0.035f, 0), v(0, -0.43f, 0), CharacterModel.PELVIS, -0.005f, limbW), true, true);
                    arts.add(spec == null ? SHIN : shinArt(spec)); break;
                case 2: caps(add(l[0], SEG, v(0, 0.015f, 0), v(0, -0.30f, 0), CharacterModel.CHEST, -0.01f, limbW), true, true);
                    arts.add(spec == null ? UPPER_ARM : upperArmArt(spec)); break;
                case 3: caps(add(l[0], SEG, v(0, 0.035f, 0), v(0, -0.26f, 0), CharacterModel.CHEST, -0.012f, limbW), true, true);
                    arts.add(spec == null ? FOREARM : forearmArt(spec)); break;
                case 4: add(l[0], SEG, v(0, -0.235f, 0), v(0, -0.36f, 0), CharacterModel.CHEST, -0.015f, 1f); arts.add(HAND); break;
                default: add(l[0], UPRIGHT, v(0, -0.07f, 0.045f), null, l[0], -0.008f, 1f); arts.add(SHOE); break;
            }
        }
        atlas = pack(arts);
    }

    // ---------------------------------------------------------------- pedestrian looks

    /** Skirt panel: front, side (facing right), back; row 0 is the hem. */
    private static final String[] SKIRT = {
            "jJJJjJJJJjJJJJj\n" +
            "jJJJjJJJJjJJJJj\n" +
            ".jJJJjJJJjJJJj.\n" +
            ".jJJJjJJJjJJJj.\n" +
            ".jJJJJJJJJJJJj.\n" +
            "..jJJJJJJJJJj..\n" +
            "..jJJJJJJJJJj..\n" +
            "..jJJJJJJJJJj..\n" +
            "..jJJJJJJJJJj..\n" +
            "..jjjjjjjjjjj..",
            "jJJJJJJJJJJJJJj\n" +
            "jJJJJJJJJJJJJJj\n" +
            ".jJJJJJJJJJJJj.\n" +
            ".jJJJJJJJJJJJj.\n" +
            "..jJJJJJJJJJj..\n" +
            "..jJJJJJJJJJj..\n" +
            "...jJJJJJJJj...\n" +
            "...jJJJJJJJj...\n" +
            "...jJJJJJJJj...\n" +
            "...jjjjjjjjj...",
            "jJJJJJJJJJJJJJj\n" +
            "jJJJJJJJJJJJJJj\n" +
            ".jJJJJJJJJJJJj.\n" +
            ".jJJJJJJJJJJJj.\n" +
            ".jJJJJJJJJJJJj.\n" +
            "..jJJJJJJJJJj..\n" +
            "..jJJJJJJJJJj..\n" +
            "..jJJJJJJJJJj..\n" +
            "..jJJJJJJJJJj..\n" +
            "..jjjjjjjjjjj..",
    };

    private static final String[] BUN = {"dhd\nhWh\nhHh\ndhd"};

    private void colours(CharacterSpec s) {
        set('S', s.skin); set('s', shade(s.skin, 0.84f)); set('z', shade(s.skin, 0.7f));
        set('h', s.hairColor); set('H', CharacterSpec.mix(s.hairColor, 0xFFFFFF, 0.2f));
        set('W', CharacterSpec.mix(s.hairColor, 0xA8B4E0, 0.4f)); set('d', shade(s.hairColor, 0.55f));
        set('E', s.eyeColor); set('e', 0x1E1A1C); set('M', s.lipColor);
        set('T', s.topColor); set('t', shade(s.topColor, 0.8f)); set('u', shade(s.topColor, 0.64f));
        set('J', s.bottomColor); set('j', shade(s.bottomColor, 0.72f)); set('n', CharacterSpec.mix(s.bottomColor, 0xFFFFFF, 0.22f));
        set('L', 0x3A2A22);
        set('F', s.shoeColor); set('f', shade(s.shoeColor, 0.74f)); set('k', s.soleColor);
        set('R', s.hatColor); set('C', s.hatColor); set('c', shade(s.hatColor, 0.7f));
        set('m', 0xEEF0F0); set('g', 0x16161A); set('w', 0xF2F2EE);
        set('r', ((s.topColor ^ s.hairColor ^ s.skin) & 1) == 0 ? 0x8A2632 : 0x2A3A6A);
    }

    private void set(char c, int rgb) { palette[c] = 0xFF000000 | rgb; }

    private static int shade(int c, float f) {
        return ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f);
    }

    private static boolean hairy(char c) { return c == 'h' || c == 'H' || c == 'd' || c == 'W'; }
    private static boolean skin(char c) { return c == 'S' || c == 's' || c == 'z'; }

    private static char[][] grid(String art) {
        String[] rows = art.split("\n");
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        char[][] g = new char[rows.length][w];
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < w; x++) g[y][x] = x < rows[y].length() ? rows[y].charAt(x) : '.';
        return g;
    }

    private static String str(char[][] g) {
        StringBuilder b = new StringBuilder();
        for (int y = 0; y < g.length; y++) { if (y > 0) b.append('\n'); b.append(g[y]); }
        return b.toString();
    }

    /** Jeans letters (or any cloth) turned to bare skin. */
    private static char toSkin(char c) {
        switch (c) {
            case 'J': case 'n': case 'T': return 'S';
            case 'j': case 't': return 's';
            case 'u': return 'z';
            default: return c;
        }
    }

    /** Skin letters turned to the top's cloth. */
    private static char toCloth(char c) {
        switch (c) {
            case 'S': return 'T';
            case 's': return 't';
            case 'z': return 'u';
            default: return c;
        }
    }

    private static String[] headArt(CharacterSpec s) {
        String[] out = new String[HEAD.length];
        for (int i = 0; i < HEAD.length; i++) {
            char[][] g = grid(HEAD[i]);
            int h = g.length, w = g[0].length;
            switch (s.hair) {
                case SHORT: case AFRO:
                    for (int y = 6; y < h; y++)
                        for (int x = 0; x < w; x++) if (hairy(g[y][x])) g[y][x] = i >= 3 ? 's' : '.';
                    break;
                case LONG:
                    for (int y = 6; y < h; y++)
                        for (int x = 0; x < w; x++)
                            if (hairy(g[5][x])) g[y][x] = x == 0 || x == w - 1 || g[5][x] == 'd' ? 'd' : 'h';
                    break;
                case BALD:
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++)
                            if (hairy(g[y][x])) g[y][x] = g[y][x] == 'd' ? 's' : 'S';
                    break;
                default: break;
            }
            boolean face = i <= 2;
            if (face && !s.female) for (char[] row : g) for (int x = 0; x < w; x++) if (row[x] == 'M') row[x] = 's';
            if (face && s.beard) for (int x = 0; x < w; x++) if (skin(g[6][x]) || g[6][x] == 'M') g[6][x] = 'h';
            if (face && s.mask)
                for (int y = 5; y <= 6; y++) for (int x = 0; x < w; x++) if (skin(g[y][x]) || g[y][x] == 'M') g[y][x] = 'm';
            if (face && s.sunglasses) {
                int a = -1, b = -1;
                for (int x = 0; x < w; x++) if (g[4][x] == 'E' || g[4][x] == 'e') { if (a < 0) a = x; b = x; }
                if (a >= 0) for (int x = a; x <= b; x++) g[4][x] = 'g';
            }
            if (s.hat == CharacterSpec.Hat.CAP) {
                for (int y = 0; y <= 1; y++)
                    for (int x = 0; x < w; x++) if (g[y][x] != '.') g[y][x] = x == 0 || x == w - 1 || g[y][x] == 'd' ? 'c' : 'C';
                if (i <= 1) { for (int x = 0; x < w; x++) if (hairy(g[2][x]) || skin(g[2][x])) g[2][x] = 'c'; }
                else if (i == 2) g[2][w - 1] = 'c';
            } else if (s.hat == CharacterSpec.Hat.SUNHAT) {
                char[][] p = new char[h][w + 2];
                for (int y = 0; y < h; y++) {
                    p[y][0] = p[y][w + 1] = '.';
                    System.arraycopy(g[y], 0, p[y], 1, w);
                }
                for (int y = 0; y <= 1; y++) for (int x = 1; x <= w; x++) if (p[y][x] != '.') p[y][x] = 'C';
                for (int x = 0; x < w + 2; x++) p[2][x] = x == 0 || x == w + 1 ? 'c' : 'C';
                g = p;
            }
            out[i] = str(g);
        }
        return out;
    }

    private static String[] torsoArt(CharacterSpec s) {
        String[] out = new String[TORSO.length];
        boolean covered = s.top != CharacterSpec.Top.TANK && s.top != CharacterSpec.Top.DRESS;
        for (int i = 0; i < TORSO.length; i++) {
            char[][] g = grid(TORSO[i]);
            int h = g.length, w = g[0].length, mid = w / 2;
            if (covered)
                for (int y = 2; y <= 4; y++)
                    for (int x = 0; x < w; x++) {
                        if (y == 2 && i <= 1 && Math.abs(x - mid) <= 1) continue;      // the neckline
                        g[y][x] = toCloth(g[y][x]);
                    }
            if (!s.female) {
                // a straight, boxy body: the waist filled out to the chest's width
                int x0 = w, x1 = -1;
                for (int x = 0; x < w; x++) if (g[5][x] != '.') { x0 = Math.min(x0, x); x1 = x; }
                for (int y = 5; y < h; y++)
                    for (int x = x0; x <= x1; x++) {
                        boolean edge = x == x0 || x == x1;
                        if (y == h - 1) g[y][x] = edge ? 'u' : 't';
                        else if (g[y][x] == '.' || g[y][x] == 'u' || edge) g[y][x] = edge ? 't' : 'T';
                    }
            }
            if (s.top == CharacterSpec.Top.SUIT && i <= 1) {
                for (int y = 2; y <= 3; y++) for (int x = mid - 1; x <= mid + 1; x++) g[y][x] = 'w';
                for (int y = 4; y <= 10; y++) g[y][mid] = s.female ? 'w' : 'r';
            }
            out[i] = str(g);
        }
        return out;
    }

    private static String[] hipsArt(CharacterSpec s) {
        if (s.bottom != CharacterSpec.Bottom.SKIRT) return HIPS;
        String[] out = new String[HIPS.length];
        for (int i = 0; i < HIPS.length; i++) {
            char[][] g = grid(HIPS[i]);
            int h = g.length, w = g[0].length;
            int x0 = w, x1 = -1;
            for (int x = 0; x < w; x++) if (g[1][x] != '.') { x0 = Math.min(x0, x); x1 = x; }
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    if (g[y][x] == 'L') g[y][x] = 'j';
                    if (g[y][x] == 'n') g[y][x] = 'J';
                    // the hem flares out over both legs
                    if (y >= 5 && x >= x0 - (y == h - 1 ? 1 : 0) && x <= x1 + (y == h - 1 ? 1 : 0) && x >= 0 && x < w)
                        g[y][x] = x <= x0 || x >= x1 ? 'j' : 'J';
                }
            out[i] = str(g);
        }
        return out;
    }

    /** Row 0 of a limb is its far end (knee, ankle, elbow, wrist); the last row is the joint nearest the body. */
    private static String[] limb(String[] views, int fromRow, int toRow, boolean skinToCloth) {
        String[] out = new String[views.length];
        for (int i = 0; i < views.length; i++) {
            char[][] g = grid(views[i]);
            for (int y = Math.max(0, fromRow); y <= Math.min(g.length - 1, toRow); y++)
                for (int x = 0; x < g[y].length; x++) g[y][x] = skinToCloth ? toCloth(g[y][x]) : toSkin(g[y][x]);
            out[i] = str(g);
        }
        return out;
    }

    private static String[] thighArt(CharacterSpec s) {
        switch (s.bottom) {
            case SHORTS: return limb(THIGH, 0, 7, false);
            case SKIRT: return limb(THIGH, 0, 99, false);
            default: return THIGH;
        }
    }

    private static String[] shinArt(CharacterSpec s) {
        return s.bottom == CharacterSpec.Bottom.PANTS ? SHIN : limb(SHIN, 0, 99, false);
    }

    private static String[] upperArmArt(CharacterSpec s) {
        switch (s.top) {
            case TSHIRT: return limb(UPPER_ARM, 6, 99, true);
            case LONGSLEEVE: case SUIT: return limb(UPPER_ARM, 0, 99, true);
            default: return UPPER_ARM;
        }
    }

    private static String[] forearmArt(CharacterSpec s) {
        if (s.top != CharacterSpec.Top.LONGSLEEVE && s.top != CharacterSpec.Top.SUIT) return FOREARM;
        String[] a = limb(FOREARM, 1, 99, true);
        if (s.top == CharacterSpec.Top.SUIT) {
            char[][] g = grid(a[0]);
            for (int x = 0; x < g[1].length; x++) g[1][x] = 'w';     // shirt cuff
            a = new String[]{str(g)};
        }
        return a;
    }

    /** The thigh's top stays open (it tucks under the hips); everything else is capped both ends. */
    private static void caps(Part p, boolean bottom, boolean top) {
        p.capBottom = bottom;
        p.capTop = top;
    }

    private static float[] v(float x, float y, float z) { return new float[]{x, y, z}; }

    private Part add(int bone, int kind, float[] a, float[] b, int viewBone, float bias, float width) {
        if (a != null) for (int i = 0; i < 3; i++) a[i] *= k;
        if (b != null) for (int i = 0; i < 3; i++) b[i] *= k;
        Part p = new Part(bone, kind, a, b, 0, 0, viewBone, bias);
        p.width = width * k;
        parts.add(p);
        return p;
    }

    /** Draws every part's views into one texture, outlined. */
    private Texture pack(List<String[]> arts) {
        // lay out on shelves 128 wide
        int W = 128, x = 1, y = 1, rowH = 0;
        List<int[][]> rects = new ArrayList<>();
        List<int[]> sizes = new ArrayList<>();
        for (String[] views : arts) {
            int[][] r = new int[views.length][];
            for (int k = 0; k < views.length; k++) {
                String[] rows = views[k].split("\n");
                int w = 0;
                for (String row : rows) w = Math.max(w, row.length());
                int ow = w + 2, oh = rows.length;           // outline on the sides
                if (x + ow + 1 > W) { x = 1; y += rowH + 1; rowH = 0; }
                r[k] = new int[]{x, y, ow, oh};
                x += ow + 1;
                rowH = Math.max(rowH, oh);
            }
            rects.add(r);
        }
        int H = y + rowH + 1;
        int[] px = new int[W * H];
        for (int i = 0; i < arts.size(); i++) {
            String[] views = arts.get(i);
            int[][] r = rects.get(i);
            Part part = parts.get(i);
            boolean all = part.kind == UPRIGHT;
            for (int k = 0; k < views.length; k++) draw(px, W, views[k], r[k], all || part.capTop, all || part.capBottom);
        }
        // every left/right limb shares its art: the parts list and the art list line up
        for (int i = 0; i < parts.size(); i++) parts.get(i).rect = rects.get(i);
        return new Texture(W, H, px);
    }

    private void draw(int[] px, int W, String art, int[] r, boolean capTop, boolean capBottom) {
        String[] rows = art.split("\n");
        int h = rows.length, w = r[2] - 2;
        int[] cell = new int[(w + 2) * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < rows[y].length(); x++) cell[y * (w + 2) + x + 1] = palette[rows[y].charAt(x) & 127];
        int[] out = cell.clone();
        // selective outline: a dark shade of the colour beside it (only left and right for the
        // segments: their ends overlap the neighbouring part)
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w + 2; x++) {
                if (cell[y * (w + 2) + x] != 0) continue;
                int c = 0;
                if (x > 0 && cell[y * (w + 2) + x - 1] != 0) c = cell[y * (w + 2) + x - 1];
                else if (x < w + 1 && cell[y * (w + 2) + x + 1] != 0) c = cell[y * (w + 2) + x + 1];
                else if (capBottom && y > 0 && cell[(y - 1) * (w + 2) + x] != 0) c = cell[(y - 1) * (w + 2) + x];
                else if (capTop && y < h - 1 && cell[(y + 1) * (w + 2) + x] != 0) c = cell[(y + 1) * (w + 2) + x];
                if (c != 0) out[y * (w + 2) + x] = darken(c);
            }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w + 2; x++) px[(r[1] + y) * W + r[0] + x] = out[y * (w + 2) + x];
    }

    private static int darken(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        float k = r + g + b > 400 ? 0.42f : 0.5f;
        return 0xFF000000 | ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
    }

    // ---------------------------------------------------------------- drawing

    private final float[] tmp = new float[3], p0 = new float[3], p1 = new float[3], quad = new float[12], uv = new float[8];

    /**
     * Draws the puppet standing at (x, y, z) facing {@code facing} (radians, 0 = +Z) in a pose.
     * Grounded poses are lifted so the lowest sole touches y; parts below {@code clipY} (a water
     * surface) are cut off (pass -infinity for none).
     */
    public void draw(Renderer r, float x, float y, float z, float facing, Pose pose, float clipY, float bias) {
        if (tex < 0) return;
        Mat4[] bones = rig.solve(pose);
        if (pose.grounded) {
            float min = Float.MAX_VALUE;
            for (int f : new int[]{CharacterModel.FOOT_L, CharacterModel.FOOT_R})
                for (float fz : new float[]{-0.05f, 0.17f}) {
                    bones[f].transformPoint(0, -0.07f, fz, tmp, 0);
                    min = Math.min(min, tmp[1]);
                }
            pose.rootY -= min;
            bones = rig.solve(pose);
            pose.rootY += min;
        }
        Mat4 world = Mat4.mul(Mat4.translation(x, y, z), Mat4.rotY(facing));
        float viewYaw = (float) Math.atan2(r.ex - x, r.ez - z);
        float fx = r.fwdX(), fz = r.fwdZ(), rx = r.rightX(), rz = r.rightZ();
        for (Part p : parts) {
            Mat4 m = Mat4.mul(world, bones[p.bone]);
            // the part's view: its bone's facing against the direction to the camera
            Mat4 vm = Mat4.mul(world, bones[p.viewBone]);
            vm.transformDir(0, 0, 1, tmp, 0);
            float partYaw = tmp[0] * tmp[0] + tmp[2] * tmp[2] > 0.09f ? (float) Math.atan2(tmp[0], tmp[2]) : facing;
            int d = Math.round((partYaw - viewYaw) / (float) (Math.PI / 4));
            d = ((d % 8) + 8) % 8;
            int view = d <= 4 ? d : 8 - d;
            boolean mirror = d > 4;
            int[] rect = p.rect[Math.min(view, p.rect.length - 1)];
            if (p.rect.length == 3) rect = p.rect[view == 0 || view == 1 ? 0 : (view == 2 ? 1 : 2)];
            float w = rect[2] / PPM * p.width;
            float u0 = rect[0] + 0.01f, u1 = rect[0] + rect[2] - 0.01f, v0 = rect[1] + 0.01f, v1 = rect[1] + rect[3] - 0.01f;
            if (mirror) { float t = u0; u0 = u1; u1 = t; }
            if (p.kind == UPRIGHT) {
                m.transformPoint(p.a[0], p.a[1], p.a[2], p0, 0);
                float h = rect[3] / PPM * k;
                corners(p0[0], p0[1], p0[2], p0[0], p0[1] + h, p0[2], w, rx, rz, fx, fz);
            } else {
                m.transformPoint(p.a[0], p.a[1], p.a[2], p0, 0);
                m.transformPoint(p.b[0], p.b[1], p.b[2], p1, 0);
                corners(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], w, rx, rz, fx, fz);
            }
            // bottom-left, bottom-right, top-right, top-left
            uv[0] = u0; uv[1] = v1; uv[2] = u1; uv[3] = v1; uv[4] = u1; uv[5] = v0; uv[6] = u0; uv[7] = v0;
            emit(r, clipY, bias + p.bias);
        }
    }

    /**
     * A screen-parallel quad from a (bottom) to b (top), w wide: both ends are moved to the depth of
     * the middle, so the part sorts as one piece.
     */
    private void corners(float ax, float ay, float az, float bx, float by, float bz, float w, float rx, float rz, float fx, float fz) {
        float mx = (ax + bx) * 0.5f, mz = (az + bz) * 0.5f;
        float da = (ax - mx) * fx + (az - mz) * fz, db = (bx - mx) * fx + (bz - mz) * fz;
        ax -= fx * da; az -= fz * da;
        bx -= fx * db; bz -= fz * db;
        // the segment in screen terms (right, up) and its perpendicular
        float sr = (bx - ax) * rx + (bz - az) * rz, su = by - ay;
        float len = (float) Math.sqrt(sr * sr + su * su);
        float min = w * 0.6f;
        if (len < 1e-4f) { sr = 0; su = 1; len = 1; }
        float nr = sr / len, nu = su / len;
        if (len < min) {
            // seen end on: keep a stub of the part rather than a sliver
            float mid = 0.5f;
            float cxr = ((ax + bx) * mid), cy = (ay + by) * mid, czr = (az + bz) * mid;
            ax = cxr - rx * nr * min * 0.5f; az = czr - rz * nr * min * 0.5f; ay = cy - nu * min * 0.5f;
            bx = cxr + rx * nr * min * 0.5f; bz = czr + rz * nr * min * 0.5f; by = cy + nu * min * 0.5f;
        }
        // right-hand perpendicular within the screen plane: (nu, -nr)
        float hw = w * 0.5f;
        float px = rx * nu * hw, pz = rz * nu * hw, py = -nr * hw;
        quad[0] = ax - px; quad[1] = ay - py; quad[2] = az - pz;
        quad[3] = ax + px; quad[4] = ay + py; quad[5] = az + pz;
        quad[6] = bx + px; quad[7] = by + py; quad[8] = bz + pz;
        quad[9] = bx - px; quad[10] = by - py; quad[11] = bz - pz;
    }

    private final float[] cp = new float[8 * 3], cu = new float[8 * 2], tq = new float[12], tu = new float[8];

    /** Sends the quad, cut at the water surface if it reaches below it. */
    private void emit(Renderer r, float clipY, float bias) {
        float lo = Math.min(Math.min(quad[1], quad[4]), Math.min(quad[7], quad[10]));
        if (lo >= clipY) { r.spriteQuad(tex, quad, uv, bias); return; }
        float hi = Math.max(Math.max(quad[1], quad[4]), Math.max(quad[7], quad[10]));
        if (hi <= clipY) return;
        // keep the part above y = clipY (Sutherland-Hodgman against one plane), then fan it
        int n = 0;
        for (int k = 0; k < 4; k++) {
            int j = (k + 1) & 3;
            float ya = quad[k * 3 + 1], yb = quad[j * 3 + 1];
            boolean ina = ya >= clipY, inb = yb >= clipY;
            if (ina) {
                System.arraycopy(quad, k * 3, cp, n * 3, 3);
                cu[n * 2] = uv[k * 2]; cu[n * 2 + 1] = uv[k * 2 + 1];
                n++;
            }
            if (ina != inb) {
                float t = (clipY - ya) / (yb - ya);
                for (int c = 0; c < 3; c++) cp[n * 3 + c] = quad[k * 3 + c] + (quad[j * 3 + c] - quad[k * 3 + c]) * t;
                cu[n * 2] = uv[k * 2] + (uv[j * 2] - uv[k * 2]) * t;
                cu[n * 2 + 1] = uv[k * 2 + 1] + (uv[j * 2 + 1] - uv[k * 2 + 1]) * t;
                n++;
            }
        }
        for (int k = 1; k + 1 < n; k += 2) {
            // quads of the fan (the last may repeat a corner)
            int a = 0, b = k, c = k + 1, d = Math.min(k + 2, n - 1);
            int[] idx = {a, b, c, d};
            for (int q = 0; q < 4; q++) {
                System.arraycopy(cp, idx[q] * 3, tq, q * 3, 3);
                tu[q * 2] = cu[idx[q] * 2]; tu[q * 2 + 1] = cu[idx[q] * 2 + 1];
            }
            r.spriteQuad(tex, tq, tu, bias);
        }
    }
}
