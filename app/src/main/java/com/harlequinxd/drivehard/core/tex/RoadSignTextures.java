package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

/**
 * Japanese road furniture faces: the blue direction board at junctions, the red-ringed speed
 * limit disc, the round bus stop sign, and the orange curve mirror. Round faces are
 * alpha-clipped (transparent outside the disc).
 */
public final class RoadSignTextures {
    private RoadSignTextures() {}

    /**
     * Blue direction board (64x32): white border, a white arrow up the middle branching left and
     * right, place names in kanji either side and above.
     */
    public static Texture direction(int seed) {
        Img m = new Img(64, 32);
        int blue = 0xFF1C4E9C, white = 0xFFF2F4F6;
        m.fill(blue);
        m.grain(0, 0, 64, 32, 0.03f, 0.02f, seed);
        m.frame(1, 1, 63, 31, 1, white);
        // the road ahead and the branches
        m.rect(30, 12, 34, 29, white);
        m.rect(14, 15, 50, 18, white);
        for (int k = 0; k < 4; k++) {
            m.rect(31 - k, 9 + k, 33 + k, 10 + k, white);   // arrowhead up
            m.rect(10 + k, 16 - k, 11 + k, 17 + k, white);   // left
            m.rect(53 - k, 16 - k, 54 - k, 17 + k, white);   // right
        }
        Kanji.row(m.px, 64, 32, seed, 3, 21, 3, 5, 1, white);
        Kanji.row(m.px, 64, 32, seed + 1, 2, 3, 21, 5, 1, white);
        Kanji.row(m.px, 64, 32, seed + 2, 2, 49, 21, 5, 1, white);
        return m.texture();
    }

    /** Speed limit disc (32x32): red ring on white, blue numerals. */
    public static Texture speed(int limit, int seed) {
        Img m = new Img(32, 32);
        m.fill(0x00000000);
        m.disc(15.5f, 15.5f, 15.5f, 0xFFD02828);
        m.disc(15.5f, 15.5f, 11.5f, 0xFFF4F4F2);
        String t = Integer.toString(limit);
        int tw = PixelFont.width(t, 2);
        PixelFont.draw(m.px, 32, 32, t, 16 - tw / 2, 16 + 6, 2, 0xFF1C3C8C);
        return m.texture();
    }

    /** Bus stop sign (32x32): a round plate, white with a blue band and a bus. */
    public static Texture busStop(int seed) {
        Img m = new Img(32, 32);
        m.fill(0x00000000);
        m.disc(15.5f, 15.5f, 15.5f, 0xFF2A2A30);
        m.disc(15.5f, 15.5f, 14.5f, 0xFFF4F4F2);
        m.rect(3, 19, 29, 25, 0xFF1C5AB4);
        // the bus: body, windows, wheels
        m.rect(8, 7, 24, 16, 0xFF1C5AB4);
        for (int x = 9; x < 23; x += 4) m.rect(x, 8, x + 3, 11, 0xFFE0E8F0);
        m.rect(10, 16, 13, 18, 0xFF2A2A30);
        m.rect(19, 16, 22, 18, 0xFF2A2A30);
        Kanji.row(m.px, 32, 32, seed, 3, 7, 20, 5, 1, 0xFFF4F4F2);
        return m.texture();
    }

    /** Curve mirror (32x32): an orange rim round a pale convex mirror with a sky highlight. */
    public static Texture mirror(int seed) {
        Img m = new Img(32, 32);
        m.fill(0x00000000);
        m.disc(15.5f, 15.5f, 15.5f, 0xFFE8701C);
        m.disc(15.5f, 15.5f, 12.5f, 0xFFA8B4BC);
        m.disc(12f, 11f, 6f, 0xFFD4E0E8);
        m.disc(11f, 10f, 2.5f, 0xFFF4F8FA);
        m.grain(0, 0, 32, 32, 0.0f, 0.03f, seed);
        // keep the outside clear after the grain
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                float dx = x - 15.5f, dy = y - 15.5f;
                if (dx * dx + dy * dy > 15.5f * 15.5f) m.px[y * 32 + x] = 0;
            }
        return m.texture();
    }
}
