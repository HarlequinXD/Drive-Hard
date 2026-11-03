package com.harlequinxd.drivehard.core.render;

/**
 * The ground as a Mode 7 style map: the world is a flat, top-down painted picture cut into square
 * tiles, plus a height map. {@link SoftRenderer#drawGround()} projects it in perspective column by
 * column (a height-mapped Mode 7, as in voxel-space renderers), so hills rise out of the map and hide
 * what lies behind them. Only buildings, props, cars and people are real polygons, standing on it.
 */
public final class Ground {
    /** Tile edge in metres, texels per tile edge at mip 0, height samples per tile edge (cells + 1). */
    public static final float TILE = 64f;
    public static final int RES = 256, HCELLS = 8;
    public static final float TEXEL = TILE / RES, HCELL = TILE / HCELLS;
    public static final int MIPS = 6;

    /** One painted tile: colour mips (lighting baked in) and its corner heights. */
    public static final class Tile {
        public final int cx, cz;
        /** mips[k] is (RES >> k)^2 ARGB texels, row-major, z down. */
        public final int[][] mips = new int[MIPS][];
        /** (HCELLS+1)^2 heights at the 8 m grid points (shared with the neighbouring tiles). */
        public final float[] h = new float[(HCELLS + 1) * (HCELLS + 1)];

        public Tile(int cx, int cz, int[] base) {
            this.cx = cx;
            this.cz = cz;
            mips[0] = base;
            for (int k = 1; k < MIPS; k++) {
                int n = RES >> k, m = n << 1;
                int[] src = mips[k - 1], dst = new int[n * n];
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++) {
                        int a = src[(y * 2) * m + x * 2], b = src[(y * 2) * m + x * 2 + 1];
                        int c = src[(y * 2 + 1) * m + x * 2], d = src[(y * 2 + 1) * m + x * 2 + 1];
                        int r = (((a >> 16) & 255) + ((b >> 16) & 255) + ((c >> 16) & 255) + ((d >> 16) & 255) + 2) >> 2;
                        int g = (((a >> 8) & 255) + ((b >> 8) & 255) + ((c >> 8) & 255) + ((d >> 8) & 255) + 2) >> 2;
                        int bl = ((a & 255) + (b & 255) + (c & 255) + (d & 255) + 2) >> 2;
                        dst[y * n + x] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                    }
                mips[k] = dst;
            }
        }
    }

    /** Supplies tiles (may be missing while streaming) and fallbacks for the unloaded distance. */
    public interface Source {
        Tile tile(int cx, int cz);
        /** Ground height where no tile is loaded. */
        float height(float x, float z);
        /** Flat colour where no tile is loaded. */
        int color(float x, float z);
    }
}
