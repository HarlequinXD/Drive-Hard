package com.harlequinxd.drivehard.core.render;

/** Power-of-two ARGB texture. Texel lookups wrap, so UVs can repeat freely. */
public final class Texture implements java.io.Serializable {
    public final int w, h, wShift, wMask, hMask, hMax;
    public final int[] px;
    /** Horizontal scroll speed in texture widths per second (water etc.). */
    public float scrollU;

    public Texture(int w, int h) {
        this(w, h, new int[w * h]);
    }

    public Texture(int w, int h, int[] px) {
        if (Integer.bitCount(w) != 1)
            throw new IllegalArgumentException("texture width must be power of two: " + w);
        this.w = w;
        this.h = h;
        this.px = px;
        this.wShift = Integer.numberOfTrailingZeros(w);
        this.wMask = w - 1;
        // non power-of-two heights are allowed for sprite atlases (no vertical wrapping)
        this.hMask = Integer.bitCount(h) == 1 ? h - 1 : 0x7FFF;
        this.hMax = h - 1;
    }

    /** Copies an image into a texture whose width is padded to a power of two. */
    public static Texture padded(int[] src, int sw, int sh) {
        int w = Integer.bitCount(sw) == 1 ? sw : Integer.highestOneBit(sw) << 1;
        if (w == sw) return new Texture(sw, sh, src);
        Texture t = new Texture(w, sh);
        for (int y = 0; y < sh; y++) System.arraycopy(src, y * sw, t.px, y * w, sw);
        return t;
    }
}
