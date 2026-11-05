package com.harlequinxd.drivehard.core.ui;

import com.harlequinxd.drivehard.core.tex.PixelFont;

/**
 * The loading screen, drawn in software into its own ARGB buffer so the desktop and Android
 * front ends show exactly the same thing: a dusk skyline over a road running to the horizon,
 * the game title, what the generator is doing right now and a progress bar.
 *
 * Call {@link #set} from the loading thread whenever there is news and {@link #render} from the
 * UI thread as often as it likes (it animates on its own clock and eases the bar toward the
 * reported value, so a load that reports in big jumps still moves smoothly).
 */
public final class LoadScreen {
    public final int w, h;
    public final int[] px;
    private final int[] backdrop;
    private volatile String step = "Starting up", heading = "", detail = "";
    private volatile float target;
    private float shown;
    private long last = -1;
    private final long start = System.nanoTime();
    private static final String[] TIPS = {
            "TIP: OPEN THE MAP AND PICK A SUBURB TO FAST TRAVEL THERE",
            "TIP: TRAFFIC KEEPS LEFT - WATCH THE LIGHTS AT CITY JUNCTIONS",
            "TIP: SUBURBS SPREAD OUT AROUND THE CITY, FARMS LIE BEYOND",
            "TIP: STREET LIGHTS AND WINDOWS COME ON AS THE SUN GOES DOWN",
            "TIP: COUNTRY ROADS CLIMB HILLS AND CROSS RIVERS ON BRIDGES",
    };

    public LoadScreen(int w, int h) {
        this.w = w;
        this.h = h;
        px = new int[w * h];
        backdrop = new int[w * h];
        paintBackdrop();
    }

    /** News from the loader: what is happening and how far along the whole job is (0..1). */
    public void set(String what, float done) {
        if (what != null) step = what;
        target = Math.max(0f, Math.min(1f, done));
    }

    /** A title above the bar ("" for the first load, "TRAVELLING TO ..." for fast travel). */
    public void heading(String heading, String detail) {
        this.heading = heading == null ? "" : heading;
        this.detail = detail == null ? "" : detail;
    }

    /** Starts a new job: bar back to zero. */
    public void reset(String what) {
        step = what;
        target = 0f;
        shown = 0f;
        last = -1;
    }

    public float progress() { return target; }

    /** Draws the current frame into {@link #px} and returns it. */
    public int[] render() {
        long now = System.nanoTime();
        float t = (now - start) / 1e9f;
        float dt = last < 0 ? 0f : Math.min(0.25f, (now - last) / 1e9f);
        last = now;
        float goal = target;
        shown = goal < shown ? goal : shown + (goal - shown) * Math.min(1f, dt * 6f) + Math.min(goal - shown, dt * 0.05f);
        System.arraycopy(backdrop, 0, px, 0, px.length);
        drawRoad(t);
        drawTitle();
        drawPanel(t);
        return px;
    }

    // ------------------------------------------------------------------ static backdrop

    private int horizon() { return (int) (h * 0.60f); }

    private void paintBackdrop() {
        int hy = horizon();
        // dusk sky: deep navy overhead, violet, then a burnt orange band on the horizon
        for (int y = 0; y < h; y++) {
            int c;
            if (y < hy) {
                float v = y / (float) hy;
                c = v < 0.55f ? mix(0xFF070B1C, 0xFF2A1B3F, v / 0.55f)
                        : v < 0.85f ? mix(0xFF2A1B3F, 0xFF7A3346, (v - 0.55f) / 0.30f)
                        : mix(0xFF7A3346, 0xFFE0794A, (v - 0.85f) / 0.15f);
            } else {
                float v = (y - hy) / (float) (h - hy);
                c = mix(0xFF1A1418, 0xFF07080B, (float) Math.sqrt(v));
            }
            for (int x = 0; x < w; x++) backdrop[y * w + x] = c;
        }
        // a few stars
        long r = 0x5DEECE66DL;
        for (int i = 0; i < w * hy / 260; i++) {
            r = r * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((r >>> 33) % w), y = (int) ((r >>> 17) % (hy * 0.55f));
            int a = 40 + (int) ((r >>> 50) & 0x7F);
            blend(backdrop, x, y, 0xFFFFFFFF, a);
        }
        // the sun sinking behind the city, with a soft glow
        int sx = (int) (w * 0.66f), sy = hy - 6, sr = h / 7;
        for (int y = sy - sr * 3; y < hy; y++)
            for (int x = sx - sr * 3; x < sx + sr * 3; x++) {
                if (x < 0 || x >= w || y < 0) continue;
                float d = (float) Math.hypot(x - sx, (y - sy) * 1.1f) / sr;
                if (d < 1f) blend(backdrop, x, y, mix(0xFFFFD27A, 0xFFFF7A4A, (sy - y + sr) / (2f * sr)), 255);
                else if (d < 3f) blend(backdrop, x, y, 0xFFFF8A50, (int) (90 * (1f - (d - 1f) / 2f) * (1f - (d - 1f) / 2f)));
            }
        // two layers of skyline: hazy far towers, then dark near blocks with lit windows
        skyline(0xFF3B2442, hy, 0.20f, 0.42f, 7, 18, 11L, false);
        skyline(0xFF0D0E16, hy, 0.08f, 0.30f, 10, 30, 23L, true);
        // ground haze line
        for (int x = 0; x < w; x++) blend(backdrop, x, hy, 0xFFE0794A, 90);
    }

    private void skyline(int color, int base, float minH, float maxH, int minW, int maxW, long seed, boolean windows) {
        long r = seed * 0x9E3779B97F4A7C15L;
        int x = -5;
        while (x < w) {
            r = r * 6364136223846793005L + 1442695040888963407L;
            int bw = minW + (int) ((r >>> 33) % (maxW - minW + 1));
            float f = ((r >>> 20) & 0xFFFF) / 65535f;
            // taller towers toward the middle of the city
            float centre = 1f - Math.abs((x + bw * 0.5f) / w - 0.5f) * 1.4f;
            int bh = (int) (h * (minH + (maxH - minH) * f * Math.max(0.25f, centre)));
            int top = base - bh;
            boolean antenna = ((r >>> 40) & 7) == 0 && bh > h * 0.22f;
            for (int y = Math.max(0, top); y < base; y++)
                for (int xx = Math.max(0, x); xx < Math.min(w, x + bw); xx++) backdrop[y * w + xx] = color;
            if (antenna) {
                int ax = x + bw / 2;
                for (int y = Math.max(0, top - h / 20); y < top; y++) if (ax >= 0 && ax < w) backdrop[y * w + ax] = color;
                if (ax >= 0 && ax < w && top - h / 20 >= 0) backdrop[(top - h / 20) * w + ax] = 0xFFFF4040;
            }
            if (windows) {
                for (int y = top + 3; y < base - 2; y += 3)
                    for (int xx = x + 2; xx < x + bw - 2; xx += 3) {
                        r = r * 6364136223846793005L + 1442695040888963407L;
                        if (((r >>> 33) & 7) > 1 || xx < 0 || xx >= w) continue;
                        int lit = ((r >>> 40) & 3) == 0 ? 0xFF9FD8FF : 0xFFFFC870;
                        blend(backdrop, xx, y, lit, 120 + (int) ((r >>> 45) & 0x7F));
                    }
            }
            x += bw + (int) ((r >>> 52) % 3);
        }
    }

    // ------------------------------------------------------------------ animated road

    private void drawRoad(float t) {
        int hy = horizon();
        int vx = w / 2;
        float travel = t * 1.6f;
        for (int y = hy + 1; y < h; y++) {
            float d = (y - hy) / (float) (h - hy);       // 0 at horizon, 1 at the bottom
            float half = 4f + d * w * 0.62f;              // road half width on screen
            float z = 1f / Math.max(0.02f, d);            // distance (perspective)
            int l = (int) (vx - half), r = (int) (vx + half);
            int asphalt = mix(0xFF1C1B20, 0xFF2C2A2F, d);
            for (int x = Math.max(0, l); x <= Math.min(w - 1, r); x++) px[y * w + x] = asphalt;
            // edge lines
            int edge = Math.max(1, (int) (half * 0.025f));
            for (int k = 0; k < edge; k++) {
                plot(l + 2 + k, y, 0xFFCFCBC0, (int) (90 + 120 * d));
                plot(r - 2 - k, y, 0xFFCFCBC0, (int) (90 + 120 * d));
            }
            // centre dashes, scrolling toward the viewer
            float phase = (z * 0.35f + travel) % 1f;
            if (phase < 0.5f) {
                int dw = Math.max(1, (int) (half * 0.022f));
                for (int k = -dw; k <= dw; k++) plot(vx + k, y, 0xFFF2C14E, (int) (110 + 140 * d));
            }
            // street lights on both kerbs every few "metres"
            float lp = (z * 0.12f + travel * 0.33f) % 1f;
            if (lp < 0.06f) {
                int glow = Math.max(1, (int) (d * 6));
                for (int k = 0; k < glow; k++) {
                    plot(l - 3 - k, y, 0xFFFFD58A, 200);
                    plot(r + 3 + k, y, 0xFFFFD58A, 200);
                }
            }
        }
        // a pair of tail lights far up the road
        float bob = (float) Math.sin(t * 0.7f) * 0.02f;
        float d = 0.16f + bob;
        int y = hy + (int) (d * (h - hy));
        float half = 4f + d * w * 0.62f;
        int cx = vx - (int) (half * 0.3f);
        for (int k = -1; k <= 1; k++) {
            glowDot(cx - (int) (half * 0.12f), y + k, 0xFFFF2A2A);
            glowDot(cx + (int) (half * 0.12f), y + k, 0xFFFF2A2A);
        }
    }

    private void glowDot(int x, int y, int c) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) {
                int a = dx == 0 && dy == 0 ? 255 : 255 / (1 + dx * dx + dy * dy) / 2;
                plot(x + dx, y + dy, c, a);
            }
    }

    // ------------------------------------------------------------------ title and panel

    private void drawTitle() {
        String title = "DRIVE HARD";
        int scale = Math.max(2, w / 92);
        float slant = 0.28f;
        int tw = PixelFont.width(title, scale);
        int x = (w - tw) / 2, y = (int) (h * 0.12f);
        // shadow, outline, then a chrome fill (bright top, warm bottom)
        bigText(title, x + scale, y + scale, scale, slant, 0xFF3A0610, 0xFF3A0610, 255);
        for (int oy = -1; oy <= 1; oy++)
            for (int ox = -1; ox <= 1; ox++)
                if (ox != 0 || oy != 0) bigText(title, x + ox, y + oy, scale, slant, 0xFF0A0A10, 0xFF0A0A10, 255);
        bigText(title, x, y, scale, slant, 0xFFFFFFFF, 0xFFFFB347, 255);
        // a red speed stripe with the strapline
        int sy = y + 7 * scale + scale;
        int sw = tw + scale * 4, sx = (w - sw) / 2;
        for (int yy = sy; yy < sy + Math.max(2, scale / 2); yy++)
            for (int xx = sx; xx < sx + sw; xx++) {
                float f = (xx - sx) / (float) sw;
                plot(xx, yy, 0xFFE0263A, (int) (255 * Math.min(1f, Math.min(f, 1f - f) * 6f)));
            }
        String strap = "OPEN WORLD  -  CITY  -  SUBURBS  -  COUNTRY";
        text(strap, (w - PixelFont.width(strap, 1)) / 2, sy + Math.max(2, scale / 2) + 4, 1, 0xFFF4E6D0, 220);
    }

    private void drawPanel(float t) {
        int ph = Math.max(46, h / 5);
        int top = h - ph;
        // dark glass panel fading in from above
        for (int y = top - 18; y < h; y++) {
            int a = y < top ? (int) (170 * (y - top + 18) / 18f) : 200;
            for (int x = 0; x < w; x++) blendIn(px, y * w + x, 0xFF05060A, a);
        }
        int margin = Math.max(12, w / 24);
        String head = heading;
        int y = top + 6;
        if (!head.isEmpty()) {
            text(head, margin, y - 16, 1, 0xFFFFC870, 255);
            if (!detail.isEmpty()) text(detail, margin + PixelFont.width(head, 1) + 8, y - 16, 1, 0xFFB9B2A8, 220);
        }
        // step text with animated dots and a spinner, percent on the right
        int dots = (int) (t * 2.5f) % 4;
        String s = step.toUpperCase() + "...".substring(0, dots);
        spinner(margin + 3, y + 3, t);
        text(s, margin + 10, y, 1, 0xFFF2EFE8, 255);
        int pct = Math.round(shown * 100f);
        String p = pct + "%";
        text(p, w - margin - PixelFont.width(p, 1), y, 1, 0xFFFFC870, 255);
        // the bar
        int by = y + 12, bh = Math.max(5, h / 48), bx0 = margin, bx1 = w - margin;
        for (int yy = by - 1; yy <= by + bh; yy++)
            for (int xx = bx0 - 1; xx <= bx1; xx++) {
                boolean frame = yy == by - 1 || yy == by + bh || xx == bx0 - 1 || xx == bx1;
                plot(xx, yy, frame ? 0xFF5A5660 : 0xFF141318, 255);
            }
        int fill = bx0 + (int) ((bx1 - bx0) * shown);
        float sheen = (t * 0.6f) % 1.6f - 0.3f;
        for (int yy = by; yy < by + bh; yy++) {
            float v = (yy - by) / (float) Math.max(1, bh - 1);
            int base = v < 0.35f ? mix(0xFFFFE08A, 0xFFFFB347, v / 0.35f) : mix(0xFFFFB347, 0xFFD9542B, (v - 0.35f) / 0.65f);
            for (int xx = bx0; xx < fill; xx++) {
                float f = (xx - bx0) / (float) (bx1 - bx0);
                int c = base;
                float sd = Math.abs(f - sheen);
                if (sd < 0.06f) c = mix(c, 0xFFFFFFFF, (1f - sd / 0.06f) * 0.45f);
                plot(xx, yy, c, 255);
            }
        }
        // tip line
        String tip = TIPS[(int) (t / 5f) % TIPS.length];
        text(tip, margin, by + bh + 8, 1, 0xFF8E8A94, 255);
    }

    private void spinner(int cx, int cy, float t) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int x = cx + (int) Math.round(Math.cos(a) * 3), y = cy + (int) Math.round(Math.sin(a) * 3);
            int lead = (int) (t * 10f) % 8;
            int age = (lead - i + 8) % 8;
            plot(x, y, 0xFFFFC870, 255 - age * 30);
        }
    }

    // ------------------------------------------------------------------ pixel helpers

    private void text(String s, int x, int y, int scale, int color, int alpha) {
        for (int i = 0; i < s.length(); i++) {
            String g = PixelFont.bits(s.charAt(i));
            for (int gy = 0; gy < 7; gy++)
                for (int gx = 0; gx < 5; gx++) {
                    if (g.charAt(gy * 5 + gx) != '1') continue;
                    for (int sy = 0; sy < scale; sy++)
                        for (int sx = 0; sx < scale; sx++) {
                            // a 1 px drop shadow keeps small text legible over the scene
                            plot(x + (i * 6 + gx) * scale + sx + 1, y + gy * scale + sy + 1, 0xFF000000, alpha / 2);
                            plot(x + (i * 6 + gx) * scale + sx, y + gy * scale + sy, color, alpha);
                        }
                }
        }
    }

    private void bigText(String s, int x, int y, int scale, float slant, int top, int bottom, int alpha) {
        int rows = 7 * scale;
        for (int i = 0; i < s.length(); i++) {
            String g = PixelFont.bits(s.charAt(i));
            for (int gy = 0; gy < 7; gy++)
                for (int gx = 0; gx < 5; gx++) {
                    if (g.charAt(gy * 5 + gx) != '1') continue;
                    for (int sy = 0; sy < scale; sy++) {
                        int ry = gy * scale + sy;
                        int c = mix(top, bottom, ry / (float) rows);
                        int shift = (int) ((rows - ry) * slant);
                        for (int sx = 0; sx < scale; sx++)
                            plot(x + (i * 6 + gx) * scale + sx + shift, y + ry, c, alpha);
                    }
                }
        }
    }

    private void plot(int x, int y, int c, int a) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0) return;
        blendIn(px, y * w + x, c, Math.min(255, a));
    }

    private void blend(int[] buf, int x, int y, int c, int a) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0) return;
        blendIn(buf, y * w + x, c, Math.min(255, a));
    }

    private static void blendIn(int[] buf, int i, int c, int a) {
        int d = buf[i];
        int r = ((d >> 16) & 255) + ((((c >> 16) & 255) - ((d >> 16) & 255)) * a >> 8);
        int g = ((d >> 8) & 255) + ((((c >> 8) & 255) - ((d >> 8) & 255)) * a >> 8);
        int b = (d & 255) + (((c & 255) - (d & 255)) * a >> 8);
        buf[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
