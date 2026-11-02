package com.harlequinxd.drivehard.core.sprite;

import com.harlequinxd.drivehard.core.math.Mat4;
import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.CharacterModel;
import com.harlequinxd.drivehard.core.model.Part;
import com.harlequinxd.drivehard.core.model.Pose;

import java.util.List;

/**
 * Renders a procedural 3D character into a low-resolution, multi-direction sprite atlas
 * with a small CPU rasterizer (z-buffered, per-pixel lit, supersampled, 1px dark outline).
 * Pure Java so it runs identically on the phone at startup and on a desktop for previews.
 */
public final class SpriteBaker {
    public int frameW = 48, frameH = 64;
    public int anchorX = 24, anchorY = 60;
    public float pixelsPerMeter = 32f;
    public int directions = 8;
    public int supersample = 3;
    public float pitchDeg = 20f;
    public boolean outline = true;

    // light direction in view space (x right, y up, z toward viewer)
    private static final float LX, LY, LZ;
    static {
        float x = -0.5f, y = 0.75f, z = 0.6f;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        LX = x / l; LY = y / l; LZ = z / l;
    }

    private int leafSeed, partCounter;

    // supersampled buffers
    private int sw, sh;
    private float[] zbuf;
    private int[] cbuf;
    private boolean[] mbuf;

    /** Character: every animation frame in every direction. */
    public SpriteSheet bake(CharacterModel model) {
        return bake(model, Animations.Anim.values());
    }

    /** Character with only the given animations, frames packed in the given order. */
    public SpriteSheet bake(CharacterModel model, Animations.Anim[] anims) {
        int frames = 0;
        for (Animations.Anim a : anims) frames += a.frames;
        int[][] out = new int[frames * directions][];
        int col = 0;
        for (Animations.Anim anim : anims) {
            for (int f = 0; f < anim.frames; f++, col++) {
                Pose pose = Animations.pose(anim, f);
                Mat4[] bones = model.solve(pose);
                if (pose.grounded) {
                    float minY = lowestFootY(model, bones);
                    pose.rootY -= minY;
                    bones = model.solve(pose);
                }
                for (int d = 0; d < directions; d++) {
                    float yaw = (float) (Math.PI * 2 * d / directions);
                    out[d * frames + col] = renderFrame(model.parts, bones, yaw);
                }
            }
        }
        return pack(out, frames);
    }

    /**
     * Static props: each entry of {@code variants} is one frame (a model variant or a state such
     * as a traffic-light colour), all rendered in every direction.
     */
    public SpriteSheet bakeStatic(List<List<Part>> variants) {
        int frames = variants.size();
        int[][] out = new int[frames * directions][];
        for (int f = 0; f < frames; f++)
            for (int d = 0; d < directions; d++)
                out[d * frames + f] = renderFrame(variants.get(f), null, (float) (Math.PI * 2 * d / directions));
        return pack(out, frames);
    }

    /** Packs frames row-major into an atlas whose width is a power of two (at most 1024 when possible). */
    private SpriteSheet pack(int[][] frameData, int frames) {
        int total = frameData.length;
        int maxW = Math.max(1024, Integer.highestOneBit(frameW) << 1);
        int perRow = Math.max(1, Math.min(total, maxW / frameW));
        int W = Integer.highestOneBit(perRow * frameW);
        if (W < perRow * frameW) W <<= 1;
        int rows = (total + perRow - 1) / perRow;
        int H = rows * frameH;
        int[] atlas = new int[W * H];
        for (int i = 0; i < total; i++) {
            int ox = (i % perRow) * frameW, oy = (i / perRow) * frameH;
            for (int y = 0; y < frameH; y++) System.arraycopy(frameData[i], y * frameW, atlas, (oy + y) * W + ox, frameW);
        }
        return new SpriteSheet(atlas, W, H, frameW, frameH, frames, directions, perRow, anchorX, anchorY, pixelsPerMeter);
    }

    private static float lowestFootY(CharacterModel model, Mat4[] bones) {
        float min = Float.MAX_VALUE;
        float[] t = new float[3];
        for (Part p : model.parts) {
            if (!p.isFoot) continue;
            Mat4 m = bones[p.bone];
            for (int v = 0; v < p.vertexCount(); v++) {
                m.transformPoint(p.pos[v * 3], p.pos[v * 3 + 1], p.pos[v * 3 + 2], t, 0);
                if (t[1] < min) min = t[1];
            }
        }
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private int[] renderFrame(List<Part> parts, Mat4[] bones, float yaw) {
        if (zbuf == null || sw != frameW * supersample || sh != frameH * supersample) {
            sw = frameW * supersample;
            sh = frameH * supersample;
            zbuf = new float[sw * sh];
            cbuf = new int[sw * sh];
            mbuf = new boolean[sw * sh];
        }
        int[] out = new int[frameW * frameH];
        java.util.Arrays.fill(zbuf, -Float.MAX_VALUE);
        java.util.Arrays.fill(mbuf, false);

        float pr = (float) Math.toRadians(pitchDeg);
        float cp = (float) Math.cos(pr), spt = (float) Math.sin(pr);
        // view basis: right = +X, up = (0, cp, -sp), toward-viewer = (0, sp, cp)
        float scale = pixelsPerMeter * supersample;
        float ax = (anchorX + 0.5f) * supersample, ay = (anchorY + 0.5f) * supersample;
        Mat4 yawM = Mat4.rotY(yaw);

        float[] wp = new float[3], wn = new float[3];
        for (Part part : parts) {
            Mat4 m = bones == null ? yawM : Mat4.mul(yawM, bones[part.bone]);
            int vc = part.vertexCount();
            float[] sx = new float[vc], sy = new float[vc], sz = new float[vc];
            float[] nx = new float[vc], ny = new float[vc], nz = new float[vc];
            for (int v = 0; v < vc; v++) {
                m.transformPoint(part.pos[v * 3], part.pos[v * 3 + 1], part.pos[v * 3 + 2], wp, 0);
                m.transformDir(part.nrm[v * 3], part.nrm[v * 3 + 1], part.nrm[v * 3 + 2], wn, 0);
                float vy = wp[1] * cp - wp[2] * spt;
                float vz = wp[1] * spt + wp[2] * cp;
                sx[v] = ax + wp[0] * scale;
                sy[v] = ay - vy * scale;
                sz[v] = vz;
                nx[v] = wn[0];
                ny[v] = wn[1] * cp - wn[2] * spt;
                nz[v] = wn[1] * spt + wn[2] * cp;
            }
            int[] tris = part.tris;
            leafSeed = part.foliage ? (++partCounter * 0x9E3779B1) : 0;
            for (int t = 0; t < part.triangleCount(); t++) {
                int a = tris[t * 3], b = tris[t * 3 + 1], c = tris[t * 3 + 2];
                rasterTri(sx, sy, sz, nx, ny, nz, a, b, c, part.colors[t], part.emissive ? -1f : part.shine);
            }
        }

        // Downsample with coverage threshold.
        int ss = supersample, need = (ss * ss) / 2;
        for (int y = 0; y < frameH; y++) {
            for (int x = 0; x < frameW; x++) {
                int cnt = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < ss; j++) {
                    int row = (y * ss + j) * sw + x * ss;
                    for (int i = 0; i < ss; i++) {
                        if (mbuf[row + i]) {
                            int c = cbuf[row + i];
                            r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
                            cnt++;
                        }
                    }
                }
                out[y * frameW + x] = cnt > need
                        ? 0xFF000000 | ((r / cnt) << 16) | ((g / cnt) << 8) | (b / cnt) : 0;
            }
        }
        if (outline) addOutline(out);
        return out;
    }

    private void addOutline(int[] px) {
        int[] src = px.clone();
        for (int y = 0; y < frameH; y++) {
            for (int x = 0; x < frameW; x++) {
                if ((src[y * frameW + x] >>> 24) != 0) continue;
                int n = 0;
                if (x > 0 && (src[y * frameW + x - 1] >>> 24) != 0) n = src[y * frameW + x - 1];
                else if (x < frameW - 1 && (src[y * frameW + x + 1] >>> 24) != 0) n = src[y * frameW + x + 1];
                else if (y > 0 && (src[(y - 1) * frameW + x] >>> 24) != 0) n = src[(y - 1) * frameW + x];
                else if (y < frameH - 1 && (src[(y + 1) * frameW + x] >>> 24) != 0) n = src[(y + 1) * frameW + x];
                if (n != 0) {
                    int r = ((n >> 16) & 255) * 30 / 100, g = ((n >> 8) & 255) * 30 / 100, b = (n & 255) * 34 / 100;
                    px[y * frameW + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    private void rasterTri(float[] sx, float[] sy, float[] sz, float[] nx, float[] ny, float[] nz,
                           int a, int b, int c, int color, float shine) {
        float x0 = sx[a], y0 = sy[a], x1 = sx[b], y1 = sy[b], x2 = sx[c], y2 = sy[c];
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-6f) return;
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(sw - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(sh - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        float inv = 1f / area;
        float br = ((color >> 16) & 255) / 255f, bg = ((color >> 8) & 255) / 255f, bb = (color & 255) / 255f;
        for (int py = minY; py <= maxY; py++) {
            float fy = py + 0.5f;
            for (int px = minX; px <= maxX; px++) {
                float fx = px + 0.5f;
                float w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) * inv;
                float w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) * inv;
                float w2 = 1f - w0 - w1;
                if (w0 < -1e-5f || w1 < -1e-5f || w2 < -1e-5f) continue;
                float z = w0 * sz[a] + w1 * sz[b] + w2 * sz[c];
                int idx = py * sw + px;
                if (z <= zbuf[idx]) continue;
                int jitter = 0;
                if (leafSeed != 0) {
                    int hh = (px * 73856093) ^ (py * 19349663) ^ leafSeed;
                    hh = (hh ^ (hh >>> 13)) * 1274126177;
                    hh ^= hh >>> 16;
                    if ((hh & 255) < 92) continue;                // ragged leafy coverage
                    jitter = ((hh >>> 8) & 63) - 28;            // per-leaf brightness
                }
                zbuf[idx] = z;
                float nnx = w0 * nx[a] + w1 * nx[b] + w2 * nx[c];
                float nny = w0 * ny[a] + w1 * ny[b] + w2 * ny[c];
                float nnz = w0 * nz[a] + w1 * nz[b] + w2 * nz[c];
                float len = (float) Math.sqrt(nnx * nnx + nny * nny + nnz * nnz);
                if (len < 1e-6f) { nnx = 0; nny = 0; nnz = 1; } else { nnx /= len; nny /= len; nnz /= len; }
                if (nnz < 0f) { nnx = -nnx; nny = -nny; nnz = -nnz; }   // two-sided
                int cc = shine < 0f ? (color & 0xFFFFFF) : shade(br, bg, bb, nnx, nny, nnz, shine);
                if (jitter != 0) {
                    int r2 = Math.max(0, Math.min(255, ((cc >> 16) & 255) * (256 + jitter * 4) >> 8));
                    int g2 = Math.max(0, Math.min(255, ((cc >> 8) & 255) * (256 + jitter * 4) >> 8));
                    int b2 = Math.max(0, Math.min(255, (cc & 255) * (256 + jitter * 4) >> 8));
                    cc = (r2 << 16) | (g2 << 8) | b2;
                }
                cbuf[idx] = cc;
                mbuf[idx] = true;
            }
        }
    }

    private static int shade(float r, float g, float b, float nx, float ny, float nz, float shine) {
        float diff = Math.max(0f, nx * LX + ny * LY + nz * LZ);
        float amb = 0.36f + 0.16f * ny;                    // hemisphere ambient
        float rim = (float) Math.pow(1f - nz, 3) * 0.12f;
        // half vector between light and viewer (0,0,1)
        float hx = LX, hy = LY, hz = LZ + 1f;
        float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        float spec = shine * (float) Math.pow(Math.max(0f, (nx * hx + ny * hy + nz * hz) / hl), 20);
        float k = amb + 0.78f * diff;
        int R = clamp((r * k + spec + rim * 0.9f) * 255f);
        int G = clamp((g * k + spec + rim * 0.95f) * 255f);
        int B = clamp((b * k + spec + rim * 1.0f) * 255f);
        return (R << 16) | (G << 8) | B;
    }

    private static int clamp(float v) { return v < 0 ? 0 : (v > 255 ? 255 : (int) v); }
}
