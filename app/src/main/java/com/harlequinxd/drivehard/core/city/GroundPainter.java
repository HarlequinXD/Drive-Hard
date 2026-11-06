package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Ground;
import com.harlequinxd.drivehard.core.tex.Noise;

/**
 * The ground tile of one 64 m chunk, for the animated sea: the sea-floor / beach heights on the
 * 8 m grid and, on coasts, the 1 m water map the renderers animate the waves, swash and foam
 * over. The ground itself is tiled polygons (see {@link City}); this holds no picture.
 */
final class GroundPainter {
    private static final int HN = Ground.HCELLS + 1;

    /** What covers one 8 m cell: texture (-1 = city), metres per texture tile, crop. */
    static final class Surface {
        int tex = -1;
        float uv = 2f;
        Countryside.Crop crop = Countryside.Crop.NONE;
    }

    private final Terrain terrain;

    GroundPainter(City city) {
        this.terrain = city.terrain;
    }

    /** Smooth value noise in 0..1. */
    private static float vn(float x, float z, int s) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        float a = Noise.rnd(ix, iz, s), b = Noise.rnd(ix + 1, iz, s), c = Noise.rnd(ix, iz + 1, s), d = Noise.rnd(ix + 1, iz + 1, s);
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    Ground.Tile tile(int cx, int cz) {
        float x0 = cx * Ground.TILE, z0 = cz * Ground.TILE;
        int gx0 = cx * Ground.HCELLS, gz0 = cz * Ground.HCELLS;
        float[] h = new float[HN * HN];
        float hmin = Float.MAX_VALUE;
        for (int j = 0; j < HN; j++)
            for (int i = 0; i < HN; i++) {
                float v = terrain.grid(gx0 + i, gz0 + j);
                h[j * HN + i] = v;
                hmin = Math.min(hmin, v);
            }
        byte[] water = null;
        if (hmin < 0.6f) {
            // samples on the 1 m lattice corners (WRES+1 per side), so neighbouring tiles share their edges
            int wn = Ground.WRES + 1;
            byte[] wm = new byte[wn * wn];
            float cellW = Ground.TILE / Ground.WRES;
            boolean any = false;
            for (int j = 0; j < wn; j++)
                for (int i = 0; i < wn; i++) {
                    float wx = x0 + i * cellW, wz = z0 + j * cellW;
                    // the true terrain (not the 8 m grid) plus a little ripple in the sand: a natural waterline
                    float th = terrain.natural(wx, wz) + (vn(wx / 4.3f, wz / 4.3f, 77) - 0.5f) * 0.07f
                            + (vn(wx / 1.7f, wz / 1.7f, 78) - 0.5f) * 0.025f;
                    int code = Ground.Tile.waterCode(th);
                    wm[j * wn + i] = (byte) code;
                    any |= code != 0;
                }
            if (any) water = wm;
        }
        // the sea is drawn as a flat surface at sea level
        for (int k = 0; k < h.length; k++) h[k] = Math.max(h[k], -0.05f);
        return new Ground.Tile(cx, cz, h, water);
    }

    /** Flat colour for the far distance where no tile is loaded yet. */
    static int farColor(Terrain terrain, float x, float z) {
        float h = terrain.height(x, z);
        if (h < 0.05f) return 0xFF3F7290;
        if (terrain.isSand(x, z, h)) return 0xFFC8B98E;
        return h > 22f ? 0xFF4E6E38 : 0xFF557A3A;
    }
}
