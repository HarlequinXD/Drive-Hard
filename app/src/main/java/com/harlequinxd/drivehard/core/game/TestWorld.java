package com.harlequinxd.drivehard.core.game;

import com.harlequinxd.drivehard.core.gfx.MeshData;
import com.harlequinxd.drivehard.core.gfx.ProcTextures;

import static com.harlequinxd.drivehard.core.gfx.ProcTextures.*;

/**
 * Movement test level: a 64x64 m flat block with a crossroads, sidewalks, a plaza, grass and
 * sand, plus blocks, stairs, crates, pillars and a couple of buildings to judge scale,
 * jumping and collision against.
 */
public final class TestWorld extends World {
    public final float spawnX = 10f, spawnZ = 8f;

    public TestWorld() {
        halfSize = 32f;
        buildGround();

        // NE plaza
        add(Box.at(12, 12, 2, 1, 0, 0.5f, CONCRETE_WALL, CONCRETE));
        add(Box.at(15, 12, 1, 1, 0, 0.9f, CONCRETE_WALL, CONCRETE));
        for (int k = 0; k < 5; k++) add(Box.at(20, 10.3f + k * 0.6f, 3, 0.6f, 0, (k + 1) * 0.3f, CONCRETE_WALL, CONCRETE));
        add(Box.at(20, 14.5f, 3, 3, 0, 1.5f, CONCRETE_WALL, CONCRETE));
        add(Box.at(10, 20, 1, 1, 0, 1, CRATE, CRATE));
        add(Box.at(11, 20, 1, 1, 0, 1, CRATE, CRATE));
        add(Box.at(10.5f, 20, 1, 1, 1, 1, CRATE, CRATE));
        add(Box.at(25, 25, 1, 1, 0, 4, STUCCO_PINK, STUCCO_PINK));
        add(Box.at(28, 25, 1, 1, 0, 4, STUCCO_PINK, STUCCO_PINK));
        add(Box.at(14, 27, 6, 0.4f, 0, 2.5f, CONCRETE_WALL, CONCRETE));
        add(Box.at(27, 12, 6, 6, 0, 7, STUCCO_TEAL, CONCRETE));

        // SW block
        add(Box.at(-18, -18, 10, 8, 0, 9, STUCCO_CREAM, CONCRETE));
        add(Box.at(-10, -12, 1, 1, 0, 1, CRATE, CRATE));
        // NW park benches / planters
        add(Box.at(-14, 14, 3, 1, 0, 0.45f, CONCRETE, CONCRETE));
        add(Box.at(-20, 20, 2, 2, 0, 0.6f, CONCRETE_WALL, GRASS));
        // SE beach-side kiosk
        add(Box.at(16, -16, 4, 3, 0, 3, STUCCO_PINK, METAL));
    }

    private void add(Box b) {
        boxes.add(b);
        buildBox(mesh, b);
    }

    // ------------------------------------------------------------------ ground

    private static boolean road(int a) { return a >= -4 && a <= 4; }
    private static boolean walk(int a) { return (a >= -7 && a <= -5) || (a >= 5 && a <= 7); }

    private void buildGround() {
        int h = (int) halfSize;
        for (int j = -h; j < h; j++) {
            for (int i = -h; i < h; i++) {
                int tile, rot = 0;
                boolean ri = road(i), rj = road(j);
                if (ri && rj) tile = ASPHALT;
                else if (rj) {
                    if (i == 5 || i == 6 || i == -6 || i == -7) tile = CROSSWALK;
                    else if (j == 0) tile = ASPHALT_CENTER;
                    else if ((j == -2 || j == 2) && (i & 1) == 0) tile = ASPHALT_DASH;
                    else tile = ASPHALT;
                } else if (ri) {
                    rot = 1;
                    if (j == 5 || j == 6 || j == -6 || j == -7) tile = CROSSWALK;
                    else if (i == 0) tile = ASPHALT_CENTER;
                    else if ((i == -2 || i == 2) && (j & 1) == 0) tile = ASPHALT_DASH;
                    else tile = ASPHALT;
                } else if (walk(i) || walk(j)) tile = PAVERS;
                else if (i > 0 && j > 0) tile = CONCRETE;
                else if (i < 0 && j > 0) tile = (Math.abs(i + j) <= 1) ? SAND : GRASS;
                else if (i > 0) tile = SAND;
                else tile = GRASS;
                float[] c = {vb(i, j + 1), vb(i + 1, j + 1), vb(i + 1, j), vb(i, j)};
                mesh.quad(new float[]{i, 0, j + 1}, new float[]{i + 1, 0, j + 1},
                        new float[]{i + 1, 0, j}, new float[]{i, 0, j}, tile, rot, c);
            }
        }
    }

    /** Per-vertex brightness jitter shared by neighbouring tiles (PS1-style vertex lighting). */
    private static float vb(int x, int z) {
        int hsh = x * 73856093 ^ z * 19349663;
        hsh = (hsh ^ (hsh >>> 13)) * 1274126177;
        return 0.9f + ((hsh >>> 8) & 255) / 255f * 0.1f;
    }

    // ------------------------------------------------------------------ boxes

    /** Emits a box with faces split into 1 m cells (limits PS1 affine texture warping). */
    public static void buildBox(MeshData m, Box b) {
        float x0 = b.minX, x1 = b.maxX, y0 = b.minY, y1 = b.maxY, z0 = b.minZ, z1 = b.maxZ;
        // top
        for (float z = z0; z < z1 - 1e-4f; z += 1f) {
            float zz = Math.min(z + 1f, z1);
            for (float x = x0; x < x1 - 1e-4f; x += 1f) {
                float xx = Math.min(x + 1f, x1);
                cell(m, b.topTile, new float[]{x, y1, zz}, new float[]{xx, y1, zz}, new float[]{xx, y1, z}, new float[]{x, y1, z},
                        xx - x, zz - z, 1f, 1f);
            }
        }
        // sides: (axis, fixed coord, brightness)
        side(m, b, 0, z1, 0.86f);  // +Z
        side(m, b, 1, z0, 0.62f);  // -Z
        side(m, b, 2, x1, 0.76f);  // +X
        side(m, b, 3, x0, 0.70f);  // -X
    }

    private static void side(MeshData m, Box b, int face, float fixed, float light) {
        boolean alongX = face < 2;
        float a0 = alongX ? b.minX : b.minZ, a1 = alongX ? b.maxX : b.maxZ;
        for (float y = b.minY; y < b.maxY - 1e-4f; y += 1f) {
            float yy = Math.min(y + 1f, b.maxY);
            for (float a = a0; a < a1 - 1e-4f; a += 1f) {
                float aa = Math.min(a + 1f, a1);
                float[] p0, p1, p2, p3;
                if (alongX) {
                    p0 = new float[]{a, y, fixed}; p1 = new float[]{aa, y, fixed};
                    p2 = new float[]{aa, yy, fixed}; p3 = new float[]{a, yy, fixed};
                } else {
                    p0 = new float[]{fixed, y, a}; p1 = new float[]{fixed, y, aa};
                    p2 = new float[]{fixed, yy, aa}; p3 = new float[]{fixed, yy, a};
                }
                // darken near the ground for a cheap ambient-occlusion feel
                float ao0 = y < 0.01f ? 0.72f : 1f;
                cell(m, b.sideTile, p0, p1, p2, p3, aa - a, yy - y, light * ao0, light);
            }
        }
    }

    /** One quad cell; fu/fv = fraction of the tile used; lower/upper brightness. */
    private static void cell(MeshData m, int tile, float[] p0, float[] p1, float[] p2, float[] p3,
                             float fu, float fv, float lo, float hi) {
        float[] r = ProcTextures.uv(tile);
        float du = (r[2] - r[0]), dv = (r[3] - r[1]);
        float u0 = r[0], u1 = r[0] + du * fu, v1 = r[3], v0 = r[3] - dv * fv;
        float[][] p = {p0, p1, p2, p3};
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        float[] l = {lo, lo, hi, hi};
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int i : order) m.vertex(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1], l[i], l[i], l[i]);
    }
}
