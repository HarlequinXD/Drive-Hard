package com.harlequinxd.drivehard.core.render;

/**
 * The ground under the sea and along the coast. The ground itself is tiled polygons (128-texel
 * tiles, one per 8 m cell, in the chunks' batches); what remains here per 64 m chunk is the sea's:
 * the heights on the 8 m grid (the sea surface sits at sea level over them) and, on coasts, a 1 m
 * map of the sea floor / sand that the renderers animate waves, swash and foam over. Beyond the
 * loaded chunks the renderers fill the distance with flat colours ({@link Source#color}).
 */
public final class Ground {
    /** Tile edge in metres, texels per tile edge at mip 0, height samples per tile edge (cells + 1). */
    public static final float TILE = 64f;
    public static final int HCELLS = 8;
    public static final float HCELL = TILE / HCELLS;
    /** Metres per texel of the sea's sand (the ground tiles: 128 texels per 8 m), and its mip count. */
    public static final float TEXEL = 8f / 128f;
    public static final int MIPS = 6;
    /** Metres covered by one sand tile. */
    public static final float SAND_M = 8f;
    /** Cells per tile edge of the water map. */
    public static final int WRES = 64;

    /** One chunk's sea data: its corner heights and, on coasts, the water map. */
    public static final class Tile {
        public final int cx, cz;
        /** (HCELLS+1)^2 heights at the 8 m grid points (shared with the neighbouring tiles), never below the sea surface. */
        public final float[] h = new float[(HCELLS + 1) * (HCELLS + 1)];
        /**
         * Sea and beach, (WRES+1)^2 samples on the 1 m lattice (z down), or null on tiles with no coast: 0 is land
         * the waves never reach, otherwise {@link #waterHeight} of the code is the sea-floor / sand
         * height. The renderer animates the water over it (waves, swash, foam) every frame.
         */
        public byte[] water;

        /** Height (m) of a water code: 1 = 0.6 m above sea level, rising codes go deeper (20 per metre). */
        public static float waterHeight(int code) { return 0.6f - (code - 1) * 0.05f; }

        /** Code for a ground height (0 above the reach of the waves). */
        public static int waterCode(float h) {
            if (h > 0.6f) return 0;
            return 1 + Math.min(254, Math.round((0.6f - h) * 20f));
        }

        // ---------------------------------------------------------------- the painted terrain (GPU)

        /** Terrain mesh vertices per tile edge (4 m apart), samples of the layer weights and road map (1 m), material texels (0.25 m). */
        public static final int MESH = 17, SPLAT = 65, ROAD = 65, MAT = 256, LAYERS = 8, MATS = 32;

        /** True land heights at the mesh vertices (MESH^2, not raised to the sea) and their baked light. */
        public float[] land, lit;
        /** Natural ground layers: texture ids (-1 unused) and metres per texture repeat. */
        public int[] layerTex;
        public float[] layerScale;
        /** Layer weights 0..255, SPLAT^2 samples x LAYERS (sample-major), blended and dithered on the GPU. */
        public byte[] splat;
        /** Painted cover (lots, sidewalks, yards, parks): MAT^2 indices into the material table, 0 = none; null if none. */
        public byte[] mat;
        /** Material table: texture id, metres per repeat across x and z (entry 0 unused). */
        public int[] matTex;
        public float[] matScale;
        /** Per material, the area it was painted over (x0, z0, x1, z1): car parks lay out their bays in it. */
        public float[] matRect;
        /**
         * Roads projected onto the ground, ROAD^2 samples, or null: per sample the signed distance to
         * the nearest asphalt edge (negative on the road; junctions included), and for the road that
         * owns the sample its lateral offset and the distances from its two (trimmed) ends.
         */
        public float[] road;
        /** Per road sample: owner id (low 16 bits), owner high bits | junction flag (0x8000), class | flags << 8, mouth lengths (1/16 m, a | b << 8). */
        public short[] roadInfo;

        public static final int RF_PAINT_A = 1, RF_PAINT_B = 2, RF_PLAIN = 4, RF_SHOULDER = 8, RF_JOIN_A = 16, RF_JOIN_B = 32, RF_GRAVEL = 64, RF_DIRT = 128;

        public Tile(int cx, int cz, float[] h, byte[] water) {
            this.cx = cx;
            this.cz = cz;
            System.arraycopy(h, 0, this.h, 0, Math.min(h.length, this.h.length));
            this.water = water;
        }

        /** Water level code at local metres (lx, lz), bilinear on the 1 m map: the sand / sea-floor height, or NaN on dry land. */
        public float waterAt(float lx, float lz) {
            if (water == null) return Float.NaN;
            int ix = (int) lx, iz = (int) lz;
            if (ix < 0) ix = 0; else if (ix > WRES - 1) ix = WRES - 1;
            if (iz < 0) iz = 0; else if (iz > WRES - 1) iz = WRES - 1;
            float tx = lx - ix, tz = lz - iz;
            int n = WRES + 1, o = iz * n + ix;
            int a = water[o] & 255, b = water[o + 1] & 255, c = water[o + n] & 255, d = water[o + n + 1] & 255;
            if ((a | b | c | d) == 0) return Float.NaN;
            float ha = a == 0 ? 0.8f : waterHeight(a), hb = b == 0 ? 0.8f : waterHeight(b);
            float hc = c == 0 ? 0.8f : waterHeight(c), hd = d == 0 ? 0.8f : waterHeight(d);
            float top = ha + (hb - ha) * tx, bot = hc + (hd - hc) * tx;
            float hh = top + (bot - top) * tz;
            return hh > 0.5f ? Float.NaN : hh;
        }
    }

    /** Supplies tiles (may be missing while streaming) and fallbacks for the unloaded distance. */
    public interface Source {
        Tile tile(int cx, int cz);
        /**
         * How roads are painted, for the GPU: per road class (by ordinal, at most 8) four floats
         * (half width, mouth texture, band count, -) then four bands of four floats (centre-side
         * offset, kerb-side offset, lane texture or -1 for plain asphalt, 1 if one band spans both
         * sides); then the plain asphalt, road-edge, gravel track, dirt track, gravel and dirt texture ids;
         * then the car park's texture, its four bay variants and its aisle.
         */
        float[] roadStyle();
        /** Ground height where no tile is loaded. */
        float height(float x, float z);
        /** Flat colour where no tile is loaded. */
        int color(float x, float z);
        /** Texture id of the sand tile (one tile per {@link #SAND_M} metres) the sea shows through its shallows. */
        int sandTexture();

    }
}
