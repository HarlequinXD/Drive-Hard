package com.harlequinxd.drivehard.core.render;

/** Power-of-two ARGB texture. Texel lookups wrap, so UVs can repeat freely. */
public final class Texture {
    public final int w, h, wShift, wMask, hMask;
    public final int[] px;
    /** Horizontal scroll speed in texture widths per second (water etc.). */
    public float scrollU;

    public Texture(int w, int h) {
        this(w, h, new int[w * h]);
    }

    public Texture(int w, int h, int[] px) {
        if (Integer.bitCount(w) != 1 || Integer.bitCount(h) != 1)
            throw new IllegalArgumentException("texture must be power of two: " + w + "x" + h);
        this.w = w;
        this.h = h;
        this.px = px;
        this.wShift = Integer.numberOfTrailingZeros(w);
        this.wMask = w - 1;
        this.hMask = h - 1;
    }

    /** Copies a non power-of-two image into the top-left of a padded power-of-two texture. */
    public static Texture padded(int[] src, int sw, int sh) {
        int w = Integer.highestOneBit(sw - 1) << 1, h = Integer.highestOneBit(sh - 1) << 1;
        if (sw == 1) w = 1;
        if (Integer.bitCount(sw) == 1) w = sw;
        if (Integer.bitCount(sh) == 1) h = sh;
        Texture t = new Texture(w, h);
        for (int y = 0; y < sh; y++) System.arraycopy(src, y * sw, t.px, y * w, sw);
        return t;
    }
}
