package com.harlequinxd.drivehard.core.sprite;

import com.harlequinxd.drivehard.core.model.Flora;

import java.util.Random;

/**
 * Hand-drawn pixel-art trees and shrubs.
 *
 * Each species is painted straight onto its sprite frame, not modelled and rendered: trunks and
 * branches are tapered strokes, foliage is clumps of leaf tufts the way a pixel artist stamps
 * them (each tuft a little lit dome over a darker mass), pine pads are layers of needle dashes,
 * bamboo is culms with drooping leaf blades. Every pixel keeps a surface normal, a depth and an
 * occlusion value, so a lighting pass can light them all from one sun (upper left, in front),
 * darken the inside and underside of the crown and the trunk under it, catch a rim of sky light
 * on the top edges, and snap each pixel to its material's hand-picked palette through a 4x4
 * Bayer dither. A selective outline finishes it: dark on the shadow side, soft on the lit side.
 */
public final class PixelTrees {
    // materials
    private static final int LEAF = 1, BLOOM = 2, BARK = 3, CULM = 4, ACCENT = 5;

    private final int W, H;
    private final float ax, ay, ppm;
    private final float[] zb, nx, ny, nz, ao, tex;
    private final byte[] mat;
    private final int[][] ramp = new int[6][];
    private Random r;
    /** The whole crown (centre and radii, metres) whose roundness the clumps share; rx 0 for none. */
    private float crX, crY, crZ, crRx, crRy, crRz;

    private void crownShape(float x, float y, float z, float rx, float ry, float rz) { crX = x; crY = y; crZ = z; crRx = rx; crRy = ry; crRz = rz; }

    private static final float LX, LY, LZ;
    static {
        float x = -0.55f, y = 0.62f, z = 0.56f, l = (float) Math.sqrt(x * x + y * y + z * z);
        LX = x / l; LY = y / l; LZ = z / l;
    }

    private static final float[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private PixelTrees(Flora f) {
        W = f.frameW; H = f.frameH; ax = f.anchorX + 0.5f; ay = f.anchorY; ppm = f.ppm;
        zb = new float[W * H]; nx = new float[W * H]; ny = new float[W * H]; nz = new float[W * H];
        ao = new float[W * H]; tex = new float[W * H]; mat = new byte[W * H];
        java.util.Arrays.fill(zb, -1e9f);
    }

    /** All variants of a species, one frame each (plants look alike from every side). */
    public static SpriteSheet sheet(Flora f) {
        int n = f.variants;
        int[][] frames = new int[n][];
        for (int v = 0; v < n; v++) frames[v] = new PixelTrees(f).paint(f, v);
        int perRow = Math.max(1, Math.min(n, 1024 / f.frameW));
        int Wt = Integer.highestOneBit(perRow * f.frameW);
        if (Wt < perRow * f.frameW) Wt <<= 1;
        int rows = (n + perRow - 1) / perRow, Ht = rows * f.frameH;
        int[] atlas = new int[Wt * Ht];
        for (int i = 0; i < n; i++) {
            int ox = (i % perRow) * f.frameW, oy = (i / perRow) * f.frameH;
            for (int y = 0; y < f.frameH; y++) System.arraycopy(frames[i], y * f.frameW, atlas, (oy + y) * Wt + ox, f.frameW);
        }
        return new SpriteSheet(atlas, Wt, Ht, f.frameW, f.frameH, n, 1, perRow, f.anchorX, f.anchorY, f.ppm);
    }

    // ------------------------------------------------------------------ palettes

    /** Ramps run dark to light, shadows leaning blue and highlights leaning warm, as drawn by hand. */
    private static int[] pal(int... c) { for (int i = 0; i < c.length; i++) c[i] |= 0xFF000000; return c; }

    private static final int[] GREEN = pal(0x14261C, 0x1F3D24, 0x2F5A2A, 0x467A30, 0x67993A, 0x93B850, 0xC6D877);
    private static final int[] DEEP_GREEN = pal(0x0E1C18, 0x16291F, 0x203D26, 0x2D532C, 0x3F6C33, 0x5E8A40, 0x8AAE5A);
    private static final int[] PINE = pal(0x0C1A1A, 0x132824, 0x1C3A2C, 0x284E33, 0x37653B, 0x50804A, 0x7AA163);
    private static final int[] CEDAR_G = pal(0x0E1A16, 0x15281D, 0x1E3923, 0x2A4D2A, 0x3A6331, 0x547E3E, 0x7C9E52);
    private static final int[] YELLOW_GREEN = pal(0x1E321C, 0x2E4A20, 0x456A26, 0x62892E, 0x86A738, 0xAFC64C, 0xDCE380);
    private static final int[] BRIGHT_GREEN = pal(0x163020, 0x234A26, 0x33672C, 0x4A8634, 0x67A43E, 0x92C25A, 0xC3E085);
    private static final int[] SAKURA = pal(0x5A2E4A, 0x84466A, 0xB46A8C, 0xD994AE, 0xF0B8CA, 0xFAD6E0, 0xFFF0F2);
    private static final int[] MAPLE_RED = pal(0x3E1018, 0x611820, 0x8E2422, 0xB8392A, 0xD85A30, 0xEE8638, 0xF8B85A);
    private static final int[] BARK_DARK = pal(0x1C1416, 0x2C2022, 0x3E2F2E, 0x54423C, 0x6C5A50, 0x8A7868);
    private static final int[] BARK_GREY = pal(0x22201E, 0x34302C, 0x4A4540, 0x625C54, 0x7E776C, 0x9C9688);
    private static final int[] BARK_RED = pal(0x26140E, 0x3C2016, 0x55301E, 0x704228, 0x8C5634, 0xA87048);
    private static final int[] BARK_PINE = pal(0x1C1612, 0x2C221C, 0x403028, 0x584236, 0x725846, 0x8E725A);
    private static final int[] BAMBOO = pal(0x203018, 0x304A1E, 0x466A26, 0x5E882E, 0x7AA43A, 0x9EBE52, 0xC6D87A);
    private static final int[] AZALEA_PINK = pal(0x5A1834, 0x8A2652, 0xB83E70, 0xDC5E8C, 0xF086AA, 0xFAB4CA, 0xFFE0EA);
    private static final int[] AZALEA_WHITE = pal(0x5C6270, 0x80889A, 0xA8AEBC, 0xCCD0DA, 0xE6E8EE, 0xF6F6F8, 0xFFFFFF);
    private static final int[] HYD_BLUE = pal(0x1C2450, 0x2A3A7E, 0x3E56A8, 0x5876CA, 0x7C98E0, 0xA6BCF0, 0xD4E0FA);
    private static final int[] HYD_PURPLE = pal(0x2E1A48, 0x48286E, 0x663E96, 0x8458B8, 0xA27AD0, 0xC2A2E4, 0xE4D4F6);
    private static final int[] CAMELLIA_RED = pal(0x48080E, 0x700E16, 0x98161E, 0xC0262A, 0xDC4038, 0xF07058, 0xFCA88A);
    private static final int[] GOLD = pal(0x5A3A08, 0x84580E, 0xB07E16, 0xD8A422, 0xF0C63A, 0xF8E070, 0xFFF4B0);

    // ------------------------------------------------------------------ primitives

    private int px(float x) { return Math.round(ax + x * ppm - 0.5f); }

    private int py(float y) { return Math.round(ay - y * ppm - 0.5f); }

    private void put(int i, float depth, int m, float a, float b, float c, float occ, float t) {
        if (depth <= zb[i]) return;
        zb[i] = depth; mat[i] = (byte) m; nx[i] = a; ny[i] = b; nz[i] = c; ao[i] = occ; tex[i] = t;
    }

    /**
     * A filled ellipsoid (centre and radii in metres), lit as a dome: the solid mass under a
     * clump's tufts. {@code aoLow}/{@code aoHigh}: occlusion at its bottom and top.
     */
    private void ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, int m, float aoLow, float aoHigh) {
        float sx = rx * ppm, sy = ry * ppm;
        int x0 = (int) Math.floor(ax + cx * ppm - sx), x1 = (int) Math.ceil(ax + cx * ppm + sx);
        int y0 = (int) Math.floor(ay - cy * ppm - sy), y1 = (int) Math.ceil(ay - cy * ppm + sy);
        for (int y = Math.max(0, y0); y <= Math.min(H - 1, y1); y++)
            for (int x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) {
                float u = (x + 0.5f - (ax + cx * ppm)) / sx, v = ((ay - cy * ppm) - (y + 0.5f)) / sy;
                float d = u * u + v * v;
                if (d >= 1f) continue;
                float w = (float) Math.sqrt(1f - d);
                float a = u / rx, b = v / ry, c = w / rz, l = (float) Math.sqrt(a * a + b * b + c * c);
                a /= l; b /= l; c /= l;
                if (crRx > 0f) {
                    float wx = cx + u * rx, wy = cy + v * ry;
                    float gx = (wx - crX) / crRx, gy = (wy - crY) / crRy, gz = 0.5f;
                    float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                    a = a * 0.5f + gx / gl * 0.7f; b = b * 0.5f + gy / gl * 0.7f; c = c * 0.5f + gz / gl * 0.7f;
                    l = (float) Math.sqrt(a * a + b * b + c * c);
                    a /= l; b /= l; c /= l;
                }
                put(y * W + x, (cz + w * rz) * ppm, m, a, b, c, aoLow + (aoHigh - aoLow) * (v * 0.5f + 0.5f), 0f);
            }
    }

    /**
     * A leaf tuft: a small dome of leaves, rx by ry pixels, at a point (metres) whose outward
     * direction from its clump is (dx, dy, dz). Its own little dome is blended with the clump's
     * roundness, so tufts are lit as parts of the larger mass. {@code shape}: 0 round, 1 needle
     * dash (wide and thin), 2 drooping leaf.
     */
    private void tuft(float x, float y, float z, float rx, float ry, float dx, float dy, float dz, int m, float occ, int shape) {
        float cx = ax + x * ppm, cy = ay - y * ppm;
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1), y1 = (int) Math.ceil(cy + ry + 1);
        float jitter = (r.nextFloat() - 0.5f) * 0.12f;
        float gx = 0, gy = 0, gz = 0;
        if (crRx > 0f) {
            gx = (x - crX) / crRx; gy = (y - crY) / crRy; gz = Math.max(0f, (z - crZ) / crRz);
            float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz) + 1e-4f;
            gx /= gl; gy /= gl; gz /= gl;
        }
        for (int yy = Math.max(0, y0); yy <= Math.min(H - 1, y1); yy++)
            for (int xx = Math.max(0, x0); xx <= Math.min(W - 1, x1); xx++) {
                float u = (xx + 0.5f - cx) / rx, v = (cy - (yy + 0.5f)) / ry;
                if (shape == 2) u -= v * v * 0.6f * Math.signum(dx == 0 ? 1 : dx);   // the blade curls down and out
                float d = u * u + v * v;
                if (shape == 1) d = u * u + v * v * 1.6f;
                if (d >= 1f) continue;
                float w = (float) Math.sqrt(1f - d);
                float a = dx * 0.5f + u * 0.75f + gx * 0.6f, b = dy * 0.5f + v * 0.75f + gy * 0.6f, c = dz * 0.5f + w * 0.75f + gz * 0.6f;
                float l = (float) Math.sqrt(a * a + b * b + c * c);
                put(yy * W + xx, z * ppm + w * Math.min(rx, ry) * 0.8f, m, a / l, b / l, c / l, occ, jitter);
            }
    }

    /**
     * A clump of foliage: a dark mass with tufts stamped over its front, the tufts' occlusion
     * set by where they sit in the whole crown (crownLo..crownHi in height).
     */
    private void clump(float cx, float cy, float cz, float rx, float ry, float rz, int m, float leafPx, float crownLo, float crownHi, int shape) {
        clump(cx, cy, cz, rx, ry, rz, m, leafPx, crownLo, crownHi, shape, 0, 0f);
    }

    private void clump(float cx, float cy, float cz, float rx, float ry, float rz, int m, float leafPx, float crownLo, float crownHi, int shape,
                       int m2, float frac2) {
        float hgt = (cy - crownLo) / Math.max(0.1f, crownHi - crownLo);
        float base = 0.55f + 0.35f * Math.max(0f, Math.min(1f, hgt));
        ellipsoid(cx, cy, cz, rx * 0.92f, ry * 0.9f, rz * 0.92f, m, base * 0.62f, base * 0.85f);
        float area = (float) (Math.PI * rx * ry * ppm * ppm);
        int n = (int) (area / (leafPx * leafPx) * 1.25f) + 4;
        for (int k = 0; k < n; k++) {
            // a point on the front half of the clump's surface (more round its rim and top)
            float th = (float) (r.nextFloat() * Math.PI * 2), ph = (float) Math.acos(1f - r.nextFloat() * 1.15f);
            float dx = (float) (Math.sin(ph) * Math.cos(th)), dy = (float) (Math.sin(ph) * Math.sin(th)), dz = (float) Math.cos(ph);
            float s = 0.86f + r.nextFloat() * 0.16f;
            float tx = cx + dx * rx * s, ty = cy + dy * ry * s, tz = cz + dz * rz * s;
            float th2 = (ty - crownLo) / Math.max(0.1f, crownHi - crownLo);
            float occ = (0.55f + 0.4f * Math.max(0f, Math.min(1f, th2))) * (0.78f + 0.22f * dz) * (0.86f + 0.14f * Math.max(0f, dy));
            float lr = leafPx * (0.75f + r.nextFloat() * 0.5f);
            int mm = m2 != 0 && r.nextFloat() < frac2 * (0.6f + 0.8f * Math.max(0f, dy * 0.5f + 0.5f)) ? m2 : m;
            if (shape == 1) tuft(tx, ty, tz, lr * 1.9f, lr * 0.55f, dx, dy, dz, mm, occ, 1);
            else if (shape == 2) tuft(tx, ty, tz, lr * 0.55f, lr * 1.2f, dx, dy, dz, mm, occ, 2);
            else tuft(tx, ty, tz, lr, lr * 0.82f, dx, dy, dz, mm, occ, 0);
        }
    }

    /**
     * A tapered limb from (x0, y0) to (x1, y1) (metres) at depth z, radius r0 to r1, lit as a
     * cylinder; {@code bark} chooses the surface pattern.
     */
    private void limb(float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, int m, int bark, float occ) {
        float X0 = ax + x0 * ppm, Y0 = ay - y0 * ppm, X1 = ax + x1 * ppm, Y1 = ay - y1 * ppm;
        float R0 = Math.max(0.6f, r0 * ppm), R1 = Math.max(0.5f, r1 * ppm);
        float dx = X1 - X0, dy = Y1 - Y0, len2 = dx * dx + dy * dy;
        if (len2 < 1e-4f) return;
        float len = (float) Math.sqrt(len2);
        int bx0 = (int) Math.floor(Math.min(X0, X1) - Math.max(R0, R1) - 1), bx1 = (int) Math.ceil(Math.max(X0, X1) + Math.max(R0, R1) + 1);
        int by0 = (int) Math.floor(Math.min(Y0, Y1) - Math.max(R0, R1) - 1), by1 = (int) Math.ceil(Math.max(Y0, Y1) + Math.max(R0, R1) + 1);
        float ux = dx / len, uy = dy / len, qx = -uy, qy = ux;           // along, across (screen)
        for (int y = Math.max(0, by0); y <= Math.min(H - 1, by1); y++)
            for (int x = Math.max(0, bx0); x <= Math.min(W - 1, bx1); x++) {
                float pxv = x + 0.5f - X0, pyv = y + 0.5f - Y0;
                float t = Math.max(0f, Math.min(1f, (pxv * ux + pyv * uy) / len));
                float across = pxv * qx + pyv * qy - 0f;
                float along = pxv * ux + pyv * uy;
                float off = (pxv - ux * t * len) * qx + (pyv - uy * t * len) * qy;
                float R = R0 + (R1 - R0) * t;
                if (Math.abs(off) > R || along < -R * 0.3f || along > len + R * 0.3f) continue;
                float s = off / R, w = (float) Math.sqrt(Math.max(0f, 1f - s * s));
                // across the screen is (qx, -qy) in up-is-positive terms
                float a = qx * s, b = -qy * s, c = w;
                float depth = (z0 + (z1 - z0) * t) * ppm + w * R;
                float wy = y0 + (y1 - y0) * t;
                put(y * W + x, depth, m, a, b, c, occ, barkPattern(bark, x, y, s, wy));
            }
    }

    /** Bark surface: a brightness offset in palette steps. */
    private float barkPattern(int style, int x, int y, float s, float wy) {
        int h = hash(x * 3 + 11, y * 7 + 5);
        switch (style) {
            case 1:  // cherry: horizontal lenticel bands
                return (y % 4 == 0 && (h & 3) != 0) ? -0.9f : ((h & 31) == 0 ? 0.6f : 0f);
            case 2:  // pine: cracked plates
                return ((hash(x / 2, y / 3) & 7) == 0 || (y + (hash(x / 2, 0) & 3)) % 5 == 0) ? -1f : ((h & 15) == 0 ? 0.5f : 0f);
            case 3:  // cedar: long fibrous strips
                return ((x + (y / 9)) % 3 == 0) ? -0.8f : ((h & 7) == 0 ? 0.4f : 0f);
            case 4:  // zelkova / ginkgo: smooth, mottled
                return ((hash(x / 3, y / 3) & 7) < 2) ? 0.5f : ((h & 15) == 0 ? -0.6f : 0f);
            case 5:  // camphor: deep vertical furrows
                return ((x + (hash(0, y / 5) & 1)) % 3 == 1) ? -1f : 0f;
            case 6:  // bamboo: nodes
                return (Math.abs(((wy * 2f) % 1f + 1f) % 1f) < 0.08f) ? -1.4f : ((x & 1) == 0 && (h & 3) == 0 ? 0.3f : 0f);
            default: return (h & 15) == 0 ? -0.6f : 0f;
        }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    // ------------------------------------------------------------------ lighting

    private int[] shade() {
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = y * W + x, m = mat[i];
                if (m == 0) continue;
                int[] p = ramp[m];
                float diff = Math.max(0f, nx[i] * LX + ny[i] * LY + nz[i] * LZ);
                float sky = 0.5f + 0.5f * ny[i];                         // light from the sky dome
                float v = (0.1f + 0.72f * diff + 0.22f * sky) * ao[i];
                // a rim of sky light on top edges against the sky
                if (y > 0 && mat[i - W] == 0) v += 0.12f;
                if (x > 0 && mat[i - 1] == 0 && nx[i] < 0f) v += 0.06f;
                float level = v * (p.length - 1) + tex[i] + 0.15f;
                float d = (BAYER[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f - 0.5f;
                int k = (int) Math.floor(level + d * 0.6f);
                out[i] = p[Math.max(0, Math.min(p.length - 1, k))];
            }
        // selective outline: dark on the shadow (right and lower) side, softer on the lit side
        int[] src = out.clone();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (src[i] != 0) continue;
                int c = 0;
                float k = 0f;
                if (x > 0 && src[i - 1] != 0) { c = src[i - 1]; k = 0.42f; }
                else if (y > 0 && src[i - W] != 0) { c = src[i - W]; k = 0.42f; }
                else if (x < W - 1 && src[i + 1] != 0) { c = src[i + 1]; k = 0.62f; }
                else if (y < H - 1 && src[i + W] != 0) { c = src[i + W]; k = 0.7f; }
                if (c == 0) continue;
                // thin twigs at the very top read as soft edges rather than hard lines
                int rr = (int) (((c >> 16) & 255) * k * 0.9f), gg = (int) (((c >> 8) & 255) * k * 0.95f), bb = (int) ((c & 255) * k * 1.05f);
                out[i] = 0xFF000000 | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
            }
        return out;
    }

    // ------------------------------------------------------------------ species

    private int[] paint(Flora f, int v) {
        r = new Random(f.ordinal() * 7919L + v * 131L + 3L);
        ramp[BARK] = BARK_DARK;
        switch (f) {
            case SAKURA: sakura(v); break;
            case GINKGO: ginkgo(); break;
            case ZELKOVA: zelkova(); break;
            case BLACK_PINE: pine(v); break;
            case MAPLE: maple(v); break;
            case BAMBOO: bamboo(); break;
            case CEDAR: cedar(); break;
            case CAMPHOR: camphor(); break;
            case AZALEA: azalea(v); break;
            case HYDRANGEA: hydrangea(v); break;
            case BOXWOOD: boxwood(); break;
            default: camellia(); break;
        }
        return shade();
    }

    /** A trunk splitting into limbs that reach up into a crown. */
    private void branches(int n, float trunkH, float r0, float reach, float top, int bark, float spreadUp) {
        limb(0, -0.1f, 0, 0.12f, trunkH, 0, r0, r0 * 0.78f, BARK, bark, 0.9f);
        // root flare
        limb(-r0 * 1.15f, 0f, -0.05f, -r0 * 0.2f, 0.45f, -0.05f, r0 * 0.3f, r0 * 0.2f, BARK, bark, 0.8f);
        limb(r0 * 1.15f, 0f, -0.05f, r0 * 0.2f, 0.45f, -0.05f, r0 * 0.3f, r0 * 0.2f, BARK, bark, 0.72f);
        for (int b = 0; b < n; b++) {
            float a = (float) (b * Math.PI * 2 / n + r.nextFloat() * 0.5f);
            float sx = (float) Math.sin(a), sz = (float) Math.cos(a), len = reach * (0.7f + r.nextFloat() * 0.3f);
            float mx = sx * len * 0.45f, my = trunkH + (top - trunkH) * spreadUp;
            limb(0.12f, trunkH - 0.15f, 0, mx, my, sz * len * 0.45f, r0 * 0.55f, r0 * 0.32f, BARK, bark, 0.72f);
            limb(mx, my, sz * len * 0.45f, sx * len * 0.8f, top + 0.3f, sz * len * 0.8f, r0 * 0.32f, r0 * 0.12f, BARK, bark, 0.6f);
        }
    }

    /** A rounded crown of clumps between heights lo..hi, radius rad, flattened by {@code flat}. */
    private void crown(int n, float lo, float hi, float rad, float clumpR, float clumpH, int m, float leafPx, int m2, float frac2) {
        float mid = (lo + hi) * 0.5f;
        crownShape(0f, mid, 0f, rad + clumpR, (hi - lo) * 0.5f + clumpH, rad + clumpR);
        for (int k = 0; k < n; k++) {
            float a = (float) (r.nextFloat() * Math.PI * 2), d = (float) Math.sqrt(r.nextFloat()) * rad;
            float x = (float) Math.sin(a) * d, z = (float) Math.cos(a) * d;
            // the crown's profile: higher in the middle, dropping toward its edge
            float y = mid + (hi - lo) * 0.35f * (1f - (d / rad) * (d / rad)) + (r.nextFloat() - 0.5f) * (hi - lo) * 0.4f;
            float s = 0.8f + r.nextFloat() * 0.4f;
            clump(x, y, z, clumpR * s, clumpH * s, clumpR * s, m, leafPx, lo - clumpH, hi + clumpH, 0, m2, frac2);
        }
    }

    private void sakura(int v) {
        float s = v == 0 ? 1f : 0.82f;
        ramp[BARK] = BARK_DARK;
        ramp[LEAF] = SAKURA;
        ramp[BLOOM] = SAKURA;
        branches(6, 2.0f * s, 0.32f * s, 4.4f * s, 4.6f * s, 1, 0.45f);
        crown(14, 4.4f * s, 6.4f * s, 4.4f * s, 1.6f * s, 1.1f * s, LEAF, 4.2f, 0, 0f);
    }

    private void ginkgo() {
        ramp[BARK] = BARK_GREY;
        ramp[LEAF] = YELLOW_GREEN;
        limb(0, -0.1f, 0, 0.08f, 9.5f, 0, 0.3f, 0.1f, BARK, 4, 0.85f);
        for (int k = 0; k < 6; k++) {                     // short upswept branches inside the column
            float y = 3f + k * 1.3f, sgn = (k & 1) == 0 ? 1 : -1;
            limb(0.05f, y, 0, sgn * (1.6f - k * 0.18f), y + 1.2f, 0.3f, 0.09f, 0.04f, BARK, 4, 0.7f);
        }
        crownShape(0f, 7.5f, 0f, 2.6f, 5.5f, 2.6f);
        for (int k = 0; k < 15; k++) {
            float t = k / 15f, y = 3.0f + t * 9.0f, rad = 2.5f * (1f - t * 0.72f);
            float a = r.nextFloat() * 6.28f;
            clump((float) Math.sin(a) * rad * 0.35f, y, (float) Math.cos(a) * rad * 0.35f, rad, 1.25f, rad, LEAF, 4.0f, 2.5f, 12.5f, 0);
        }
    }

    private void zelkova() {
        ramp[BARK] = BARK_GREY;
        ramp[LEAF] = GREEN;
        limb(0, -0.1f, 0, 0, 2.6f, 0, 0.38f, 0.3f, BARK, 4, 0.9f);
        limb(-0.45f, 0f, -0.05f, -0.08f, 0.5f, -0.05f, 0.12f, 0.08f, BARK, 4, 0.8f);
        limb(0.45f, 0f, -0.05f, 0.08f, 0.5f, -0.05f, 0.12f, 0.08f, BARK, 4, 0.72f);
        for (int b = 0; b < 7; b++) {                     // the vase: limbs rise steeply, then spread
            float a = b * 0.9f + r.nextFloat() * 0.4f, sx = (float) Math.sin(a), sz = (float) Math.cos(a);
            limb(0, 2.5f, 0, sx * 1.4f, 6f, sz * 1.4f, 0.16f, 0.1f, BARK, 4, 0.75f);
            limb(sx * 1.4f, 6f, sz * 1.4f, sx * 4.2f, 10f + r.nextFloat(), sz * 4.2f, 0.1f, 0.04f, BARK, 4, 0.6f);
        }
        crown(16, 8.6f, 11.8f, 4.8f, 2.0f, 1.35f, LEAF, 4.2f, 0, 0f);
    }

    private void pine(int v) {
        ramp[BARK] = BARK_PINE;
        ramp[LEAF] = PINE;
        float lean = v == 0 ? 2.0f : 0.9f;
        float[] pts = {0, 0, lean * 0.15f, 2f, lean * 0.55f, 4f, lean * 0.9f, 6f, lean * 1.05f, 7.4f};
        float[] rad = {0.34f, 0.28f, 0.22f, 0.16f, 0.1f};
        for (int i = 0; i + 1 < rad.length; i++) {
            float wob = (i & 1) == 0 ? 0.18f : -0.18f;     // the twist in the trunk
            limb(pts[i * 2], pts[i * 2 + 1], 0, pts[i * 2 + 2] + wob, pts[i * 2 + 3], 0, rad[i], rad[i + 1], BARK, 2, 0.88f);
        }
        float[][] pads = {{lean * 0.3f, 3.2f, 1.5f, -1}, {lean * 0.65f, 4.6f, 1.7f, 1}, {lean * 0.95f, 6.0f, 1.8f, -1}, {lean * 1.05f, 7.4f, 1.4f, 1}};
        for (float[] pd : pads) {
            float side = pd[3] * (1.1f + r.nextFloat() * 0.5f);
            float cx = pd[0] + side, cy = pd[1] + 0.25f;
            limb(pd[0], pd[1] - 0.3f, 0, cx, cy - 0.15f, 0.2f, 0.09f, 0.05f, BARK, 2, 0.6f);
            // a cloud-pruned pad: needle dashes over a flat dark mass, in two or three lobes
            int lobes = 2 + r.nextInt(2);
            crownShape(cx, cy, 0f, pd[2] * 1.5f, 0.55f, pd[2]);
            for (int k = 0; k < lobes; k++) {
                float lx = cx + (k - (lobes - 1) * 0.5f) * pd[2] * 0.75f + (r.nextFloat() - 0.5f) * 0.3f;
                clump(lx, cy + (r.nextFloat() - 0.3f) * 0.2f, (r.nextFloat() - 0.5f) * 0.8f, pd[2] * 0.68f, 0.5f, pd[2] * 0.6f,
                        LEAF, 3.4f, 2.5f, 8f, 1);
            }
        }
    }

    private void maple(int v) {
        ramp[BARK] = BARK_DARK;
        ramp[LEAF] = v == 0 ? MAPLE_RED : BRIGHT_GREEN;
        ramp[BLOOM] = v == 0 ? GOLD : YELLOW_GREEN;
        branches(5, 1.5f, 0.2f, 2.3f, 3.2f, 4, 0.5f);
        crown(12, 3.1f, 4.6f, 2.3f, 1.2f, 0.85f, LEAF, 4.0f, BLOOM, v == 0 ? 0.22f : 0.15f);
    }

    private void bamboo() {
        ramp[CULM] = BAMBOO;
        ramp[LEAF] = BRIGHT_GREEN;
        // culms back to front, the nearer ones brighter and thicker
        float[][] culms = new float[13][];
        for (int k = 0; k < culms.length; k++)
            culms[k] = new float[]{(k / (culms.length - 1f) - 0.5f) * 3.6f + (r.nextFloat() - 0.5f) * 0.4f, (r.nextFloat() - 0.5f) * 2.4f,
                    9f + r.nextFloat() * 4f, (r.nextFloat() - 0.5f) * 1.4f};
        for (float[] c : culms) {
            float x = c[0], z = c[1], h = c[2], lean = c[3];
            limb(x, -0.1f, z, x + lean * 0.3f, h * 0.5f, z, 0.075f, 0.065f, CULM, 6, 0.75f + 0.1f * z);
            limb(x + lean * 0.3f, h * 0.5f, z, x + lean, h, z, 0.065f, 0.035f, CULM, 6, 0.8f + 0.1f * z);
            // leaf sprays: drooping blades off twigs up the top half
            for (int j = 0; j < 4; j++) {
                float t = 0.55f + j * 0.12f, sx = x + lean * (0.3f + 0.7f * (t - 0.5f) * 2f) * (t > 0.5f ? 1 : 0.6f);
                float sy = h * t;
                for (int q = 0; q < 7; q++) {
                    float a = (float) (r.nextFloat() * Math.PI * 2);
                    float dx = (float) Math.cos(a), dz = (float) Math.sin(a) * 0.6f;
                    float bx = sx + dx * (0.25f + r.nextFloat() * 0.6f), by = sy + (r.nextFloat() - 0.6f) * 0.7f;
                    float occ = (0.6f + 0.35f * t) * (0.85f + 0.15f * dz);
                    tuft(bx, by, z + dz * 0.6f + 0.2f, 1.0f, 4.6f, dx * 0.7f, 0.2f, 0.5f + dz * 0.3f, LEAF, occ, 2);
                }
            }
        }
    }

    private void cedar() {
        ramp[BARK] = BARK_RED;
        ramp[LEAF] = CEDAR_G;
        limb(0, -0.1f, 0, 0, 18.5f, 0, 0.42f, 0.08f, BARK, 3, 0.85f);
        // tiers of drooping sprays, a narrow cone
        crownShape(0f, 11.5f, 0f, 2.6f, 8.5f, 2.6f);
        for (int k = 0; k < 14; k++) {
            float t = k / 14f, y = 4.2f + t * 15f, rad = 2.5f * (1f - t * 0.86f) + 0.35f;
            clump(0, y, 0, rad, 1.0f, rad * 0.9f, LEAF, 3.4f, 4f, 20f, 2);
        }
        clump(0, 19.6f, 0, 0.4f, 0.8f, 0.4f, LEAF, 2f, 4f, 20f, 2);
    }

    private void camphor() {
        ramp[BARK] = BARK_DARK;
        ramp[LEAF] = BRIGHT_GREEN;
        branches(6, 3f, 0.6f, 5.6f, 7.2f, 5, 0.5f);
        crown(20, 7.2f, 11.2f, 6.0f, 2.4f, 1.7f, LEAF, 4.4f, 0, 0f);
    }

    private void mound(float h, float rad, int n, int m, int m2, float frac, float leafPx) {
        for (int k = 0; k < n; k++) {
            float a = (float) (r.nextFloat() * Math.PI * 2), d = (float) Math.sqrt(r.nextFloat()) * rad * 0.55f;
            float y = h * 0.4f + r.nextFloat() * h * 0.3f;
            float s = 0.55f + r.nextFloat() * 0.3f;
            clump((float) Math.sin(a) * d, y, (float) Math.cos(a) * d, rad * s, h * 0.42f, rad * s * 0.8f, m, leafPx, 0f, h, 0, m2, frac);
        }
    }

    private void azalea(int v) {
        ramp[LEAF] = DEEP_GREEN;
        ramp[BLOOM] = v == 0 ? AZALEA_PINK : AZALEA_WHITE;
        crownShape(0f, 0.55f, 0f, 1.2f, 0.6f, 1.0f);
        mound(1.0f, 1.05f, 6, LEAF, BLOOM, 0.62f, 3.4f);
    }

    private void hydrangea(int v) {
        ramp[LEAF] = GREEN;
        ramp[BLOOM] = v == 0 ? HYD_BLUE : HYD_PURPLE;
        crownShape(0f, 0.7f, 0f, 1.1f, 0.7f, 0.9f);
        mound(1.25f, 0.9f, 6, LEAF, 0, 0f, 3.6f);
        // the big round flower heads, sat in the leaves
        for (int k = 0; k < 7; k++) {
            float a = (float) (r.nextFloat() * Math.PI * 2), d = 0.25f + r.nextFloat() * 0.55f;
            float x = (float) Math.sin(a) * d * 1.1f, z = 0.55f + r.nextFloat() * 0.25f;
            float y = 0.6f + r.nextFloat() * 0.65f;
            clump(x, y, z, 0.25f, 0.22f, 0.25f, BLOOM, 2.4f, 0f, 1.3f, 0);
        }
    }

    private void boxwood() {
        ramp[LEAF] = DEEP_GREEN;
        // clipped: one tight dome, small even leaves
        crownShape(0f, 0.75f, 0f, 0.8f, 0.7f, 0.8f);
        clump(0, 0.65f, 0, 0.78f, 0.62f, 0.78f, LEAF, 2.8f, 0f, 1.3f, 0);
        clump(0, 0.98f, 0.05f, 0.58f, 0.46f, 0.58f, LEAF, 2.8f, 0f, 1.3f, 0);
    }

    private void camellia() {
        ramp[BARK] = BARK_GREY;
        ramp[LEAF] = DEEP_GREEN;
        ramp[ACCENT] = CAMELLIA_RED;
        limb(0, -0.05f, 0, 0.05f, 0.6f, 0, 0.08f, 0.06f, BARK, 4, 0.7f);
        crownShape(0f, 1.3f, 0f, 1.0f, 1.0f, 0.8f);
        for (int k = 0; k < 7; k++) {
            float t = k / 7f, y = 0.55f + t * 1.5f, rad = 0.95f * (1f - t * 0.55f);
            clump((r.nextFloat() - 0.5f) * 0.3f, y, 0, rad, 0.42f, rad * 0.8f, LEAF, 3.0f, 0f, 2.2f, 0, ACCENT, 0.13f);
        }
    }
}
