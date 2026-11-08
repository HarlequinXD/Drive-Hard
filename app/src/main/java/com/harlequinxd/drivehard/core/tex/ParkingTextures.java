package com.harlequinxd.drivehard.core.tex;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.Img.mix;
import static com.harlequinxd.drivehard.core.tex.Img.rgb;

/**
 * A Japanese surface car park, hand painted at 16 texels per metre, after how real ones look:
 * 90-degree bays 2.5 m wide and 5 m deep opening onto a two-way aisle, thin white lines (worn
 * where tyres cross them), a pair of concrete wheel stops near the back of each bay, an oil
 * drip and tyre scuffs where cars stand, and in coin car parks a steel lock flap in the middle of
 * the bay. The aisle is the same asphalt, polished darker along the wheel tracks. Light comes
 * from the upper left; the library's pixel-art finish dithers it to its palette.
 */
public final class ParkingTextures {
    private ParkingTextures() { }

    /** Bay width and depth in metres; texels per metre. */
    public static final float BAY_W = 2.5f, BAY_D = 5f, TPM = 16f;

    private static final int[] ASPHALT = {rgb(46, 47, 52), rgb(56, 57, 62), rgb(66, 67, 72), rgb(77, 78, 82), rgb(90, 90, 94), rgb(106, 106, 108)};

    /** The car park's asphalt: fine aggregate, a few lighter stones, soft patches of wear. */
    private static int asphalt(int x, int y, int w, int h, int sd) {
        float n = Noise.fbm(x, y, w, h, sd);
        float k = 1.4f + n * 2.4f + (Noise.rnd(x, y, sd + 1) - 0.5f) * 1.2f;
        if (Noise.rnd(x, y, sd + 2) > 0.985f) k += 1.6f;                     // a pale stone
        return ASPHALT[Math.max(0, Math.min(ASPHALT.length - 1, Math.round(k)))];
    }

    /**
     * One bay: u across (0 = the side line), v from the open end at the aisle (0) to the back
     * line (1). Variant 0-3 changes the stains; variant 2 has yellow wheel stops, 3 a lock flap.
     */
    public static Texture bay(long seed, int variant) {
        final int W = Math.round(BAY_W * TPM), H = Math.round(BAY_D * TPM);  // 40 x 80
        int sd = (int) seed + 4400 + variant * 37;
        Random r = new Random(sd);
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) px[y * W + x] = asphalt(x, y, W, H, sd);
        // tyre scuffs: the wheels' tracks, darker, from the aisle to the stops
        for (int y = 0; y < H - 14; y++)
            for (int side = 0; side < 2; side++) {
                int cx = side == 0 ? 10 : 30;
                for (int dx = -2; dx <= 2; dx++) {
                    if (Noise.rnd(cx + dx, y, sd + 3) > 0.55f) continue;
                    int i = y * W + cx + dx;
                    px[i] = mix(px[i], ASPHALT[0], 0.45f);
                }
            }
        // the oil drip under the engine (cars reverse in, so it is near the open end), and older ones
        drip(px, W, H, 20 + r.nextInt(5) - 2, 22 + r.nextInt(8), 4 + r.nextInt(3), sd + 4, 0.75f);
        if (variant != 1) drip(px, W, H, 14 + r.nextInt(12), 30 + r.nextInt(20), 2 + r.nextInt(2), sd + 5, 0.5f);
        // the coin car park's lock flap: a ribbed steel plate in the middle of the bay
        if (variant == 3) {
            for (int y = 34; y < 48; y++)
                for (int x = 13; x < 27; x++) {
                    boolean rim = x == 13 || x == 26 || y == 34 || y == 47;
                    int c = rim ? rgb(70, 72, 76) : ((x + y) % 3 == 0 ? rgb(150, 154, 158) : rgb(122, 126, 132));
                    if (y == 34 || x == 13) c = rgb(176, 180, 184);                  // lit edges
                    px[y * W + x] = c;
                }
            for (int x = 13; x < 27; x++) px[48 * W + x] = ASPHALT[0];           // its shadow
        }
        // wheel stops: two concrete blocks 0.8 m in front of the back line
        int stop = variant == 2 ? rgb(214, 182, 56) : rgb(176, 174, 166);
        int stopLit = variant == 2 ? rgb(240, 214, 100) : rgb(214, 212, 204), stopDark = variant == 2 ? rgb(150, 120, 30) : rgb(120, 118, 112);
        for (int b = 0; b < 2; b++) {
            int x0 = b == 0 ? 5 : 25, x1 = x0 + 10;
            for (int y = 64; y < 68; y++)
                for (int x = x0; x < x1; x++) {
                    int c = y == 64 ? stopLit : y == 67 ? stopDark : stop;
                    if (x == x0) c = mix(c, stopLit, 0.4f);
                    if (x == x1 - 1) c = mix(c, stopDark, 0.5f);
                    if (Noise.rnd(x, y, sd + 6) > 0.9f) c = mix(c, stopDark, 0.35f);   // chips
                    px[y * W + x] = c;
                }
            for (int x = x0; x < x1; x++) px[68 * W + x] = ASPHALT[0];          // shadow toward the aisle
            px[64 * W + x1] = mix(px[64 * W + x1], ASPHALT[0], 0.5f);
        }
        // the painted lines: the side line and the back line, worn where tyres run over them
        int paint = rgb(226, 226, 218), worn = rgb(172, 172, 166);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < 2; x++) {
                float wear = Noise.rnd(x, y, sd + 7) + (y < 10 ? 0.25f : 0f);     // the open end is driven over most
                int i = y * W + x;
                px[i] = wear > 0.9f ? mix(px[i], worn, 0.5f) : wear > 0.8f ? worn : paint;
            }
        for (int y = H - 2; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                px[i] = Noise.rnd(x, y, sd + 8) > 0.88f ? worn : paint;
            }
        // painted at 16 texels per metre, kept at 32 x 64 (texture widths are powers of two), lines kept whole
        final int TW = 32, TH = 64;
        int[] out = new int[TW * TH];
        for (int y = 0; y < TH; y++)
            for (int x = 0; x < TW; x++) {
                int sx = x == 0 ? 0 : x * W / TW, sy = y == TH - 1 ? H - 1 : y * H / TH;
                out[y * TW + x] = px[sy * W + sx];
            }
        return new Texture(TW, TH, out);
    }

    private static void drip(int[] px, int W, int H, int cx, int cy, int rad, int sd, float k) {
        for (int y = cy - rad - 2; y <= cy + rad + 2; y++)
            for (int x = cx - rad - 2; x <= cx + rad + 2; x++) {
                if (x < 2 || x >= W || y < 0 || y >= H - 2) continue;
                float dx = x - cx, dy = (y - cy) * 1.3f;
                float d = (float) Math.sqrt(dx * dx + dy * dy) + (Noise.rnd(x, y, sd) - 0.5f) * 2.2f;
                if (d > rad) continue;
                int i = y * W + x;
                px[i] = mix(px[i], rgb(30, 30, 36), k * (d < rad * 0.5f ? 1f : 0.6f));
            }
    }

    /** The aisle: 4 m of the same asphalt, polished darker along the two wheel tracks of each lane. */
    public static Texture aisle(long seed) {
        final int N = 64;
        int sd = (int) seed + 4500;
        int[] px = new int[N * N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                int c = asphalt(x, y, N, N, sd);
                float t = Noise.value(x, y, N, N, 4, 4, sd + 1);
                if (t > 0.62f) c = mix(c, ASPHALT[0], (t - 0.62f) * 1.6f);          // polished, oily patches
                px[y * N + x] = c;
            }
        // a short hairline crack, sealed with tar
        Random r = new Random(sd);
        for (int k = 0; k < 1; k++) {
            float x = r.nextInt(N), y = r.nextInt(N), a = r.nextFloat() * 6.28f;
            for (int s = 0; s < 16; s++) {
                a += (r.nextFloat() - 0.5f) * 0.6f;
                x += (float) Math.cos(a);
                y += (float) Math.sin(a);
                int xi = Math.floorMod(Math.round(x), N), yi = Math.floorMod(Math.round(y), N);
                px[yi * N + xi] = mix(px[yi * N + xi], rgb(32, 32, 36), 0.5f);
            }
        }
        return new Texture(N, N, px);
    }
}
