package com.harlequinxd.drivehard.core.ui;

import com.harlequinxd.drivehard.core.tex.PixelFont;

/**
 * Drawing for the front-end screens (main menu, mission briefings and results, the mission HUD):
 * rectangles, gradients, slanted bars and pixel-font text with shadows. Colours blend with
 * coverage the same way as the HUD's (alpha accumulates), so it works over the CPU renderer's
 * frame and over the GPU's clear overlay layer alike.
 */
public class Paint {
    protected int[] fb;
    protected int w, h;

    protected void begin(int[] fb, int w, int h) { this.fb = fb; this.w = w; this.h = h; }

    protected static int blend(int d, int c, int a) {
        if (a >= 256) a = 256;
        int al = (d >>> 24) + (((255 - (d >>> 24)) * a) >> 8);
        int r = ((d >> 16) & 255) + ((((c >> 16) & 255) - ((d >> 16) & 255)) * a >> 8);
        int g = ((d >> 8) & 255) + ((((c >> 8) & 255) - ((d >> 8) & 255)) * a >> 8);
        int b = (d & 255) + (((c & 255) - (d & 255)) * a >> 8);
        return (Math.min(255, al) << 24) | (r << 16) | (g << 8) | b;
    }

    protected static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return blend(a, b, (int) (t * 256f)) | 0xFF000000;
    }

    protected void plot(int x, int y, int c, int a) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0) return;
        int i = y * w + x;
        fb[i] = blend(fb[i], c, a);
    }

    protected void rect(int x0, int y0, int x1, int y1, int c, int a) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        for (int y = y0; y < y1; y++) {
            int row = y * w;
            for (int x = x0; x < x1; x++) fb[row + x] = blend(fb[row + x], c, a);
        }
    }

    /** A horizontal fade: alpha a0 at x0 to a1 at x1. */
    protected void hfade(int x0, int y0, int x1, int y1, int c, int a0, int a1) {
        for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) {
            int a = a0 + (a1 - a0) * (x - x0) / Math.max(1, x1 - x0);
            for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) plot(x, y, c, a);
        }
    }

    /** A vertical fade: alpha a0 at y0 to a1 at y1. */
    protected void vfade(int x0, int y0, int x1, int y1, int c, int a0, int a1) {
        for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) {
            int a = a0 + (a1 - a0) * (y - y0) / Math.max(1, y1 - y0);
            for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) plot(x, y, c, a);
        }
    }

    /** A bar with slanted ends (a parallelogram leaning right), the menu's highlight. */
    protected void slant(int x0, int y0, int x1, int y1, int c, int a) {
        int hgt = y1 - y0;
        for (int y = y0; y < y1; y++) {
            int off = (y1 - 1 - y) / 2;
            for (int x = x0 + off; x < x1 + off - hgt / 2; x++) plot(x, y, c, a);
        }
    }

    protected void frame(int x0, int y0, int x1, int y1, int c, int a) {
        for (int x = x0; x < x1; x++) { plot(x, y0, c, a); plot(x, y1 - 1, c, a); }
        for (int y = y0; y < y1; y++) { plot(x0, y, c, a); plot(x1 - 1, y, c, a); }
    }

    protected static int textW(String s, int scale) { return PixelFont.width(s, scale); }

    /** Pixel text with a drop shadow; italic leans the glyphs (the logo). */
    protected void text(String s, int x, int y, int scale, int c, int a, boolean italic, int shadow) {
        for (int i = 0; i < s.length(); i++) {
            String gl = PixelFont.bits(s.charAt(i));
            for (int gy = 0; gy < 7; gy++) {
                int lean = italic ? (6 - gy) * scale / 3 : 0;
                for (int gx = 0; gx < 5; gx++) {
                    if (gl.charAt(gy * 5 + gx) != '1') continue;
                    for (int sy = 0; sy < scale; sy++)
                        for (int sx = 0; sx < scale; sx++) {
                            int X = x + (i * 6 + gx) * scale + sx + lean, Y = y + gy * scale + sy;
                            if (shadow != 0) plot(X + Math.max(1, scale / 2), Y + Math.max(1, scale / 2), shadow, (a * 3) >> 2);
                            plot(X, Y, c, a);
                        }
                }
            }
        }
    }

    protected void text(String s, int x, int y, int scale, int c) { text(s, x, y, scale, c, 256, false, 0xFF000000); }

    protected void centred(String s, int cx, int y, int scale, int c) { text(s, cx - textW(s, scale) / 2, y, scale, c); }

    /** Word-wraps text into lines no wider than maxW at this scale. */
    protected static java.util.List<String> wrap(String s, int maxW, int scale) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (textW(t, scale) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(t);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** True if (x, y) is inside the box {x0, y0, x1, y1}. */
    protected static boolean in(int[] b, float x, float y) { return x >= b[0] && x < b[2] && y >= b[1] && y < b[3]; }
}
