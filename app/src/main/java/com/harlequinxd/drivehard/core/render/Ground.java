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
    public static final int RES = 512, HCELLS = 8;
    public static final float TEXEL = TILE / RES, HCELL = TILE / HCELLS;
    public static final int MIPS = 7;

    /** One painted tile: colour mips (lighting baked in) and its corner heights. */
    public static final class Tile {
        public final int cx, cz;
        /**
         * mips[k] is (RES >> k)^2 texels in 15-bit RGB (the screen's colour depth, half the memory
         * of ARGB), z down, stored in 8x8 blocks (see {@link #index}) so that walking the map in
         * any direction stays in cache.
         */
        public final short[][] mips = new short[MIPS][];
        /** (HCELLS+1)^2 heights at the 8 m grid points (shared with the neighbouring tiles). */
        public final float[] h = new float[(HCELLS + 1) * (HCELLS + 1)];

        public Tile(int cx, int cz, int[] base) {
            this.cx = cx;
            this.cz = cz;
            int[] src = base;
            mips[0] = pack(src);
            for (int k = 1; k < MIPS; k++) {
                int n = RES >> k, m = n << 1;
                int[] dst = new int[n * n];
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++) {
                        int a = src[(y * 2) * m + x * 2], b = src[(y * 2) * m + x * 2 + 1];
                        int c = src[(y * 2 + 1) * m + x * 2], d = src[(y * 2 + 1) * m + x * 2 + 1];
                        int r = (((a >> 16) & 255) + ((b >> 16) & 255) + ((c >> 16) & 255) + ((d >> 16) & 255) + 2) >> 2;
                        int g = (((a >> 8) & 255) + ((b >> 8) & 255) + ((c >> 8) & 255) + ((d >> 8) & 255) + 2) >> 2;
                        int bl = ((a & 255) + (b & 255) + (c & 255) + (d & 255) + 2) >> 2;
                        dst[y * n + x] = (r << 16) | (g << 8) | bl;
                    }
                mips[k] = pack(dst);
                src = dst;
            }
        }

        /** Position of texel (px, pz) in a mip of n x n texels. */
        public static int index(int px, int pz, int n) {
            return (((pz >> 3) * (n >> 3) + (px >> 3)) << 6) | ((pz & 7) << 3) | (px & 7);
        }

        private static short[] pack(int[] c) {
            short[] out = new short[c.length];
            int n = (int) Math.sqrt(c.length);
            for (int i = 0; i < c.length; i++) {
                int v = c[i];
                int o = index(i % n, i / n, n);
                int r = Math.min(31, (((v >> 16) & 255) + 4) >> 3), g = Math.min(31, (((v >> 8) & 255) + 4) >> 3), b = Math.min(31, ((v & 255) + 4) >> 3);
                out[o] = (short) ((r << 10) | (g << 5) | b);
            }
            return out;
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
