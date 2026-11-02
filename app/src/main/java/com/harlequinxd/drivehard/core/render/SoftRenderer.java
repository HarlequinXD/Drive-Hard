package com.harlequinxd.drivehard.core.render;

import java.util.Arrays;

/**
 * PS1-style software renderer.
 *
 * <ul>
 * <li>No z-buffer. Polygons are sorted with a PlayStation-style <b>ordering table</b>: every
 *   primitive is linked into a depth bucket (average view depth + per-polygon bias) inside a
 *   <b>draw layer</b>, then layers are drawn in order and each layer's buckets far-to-near.
 *   Layers remove the classic z-fighting cases (road markings on roads, sidewalks over roads,
 *   shadows on the ground) and the bias lets roofs sit behind parapets, sprites in front of
 *   walls, etc.</li>
 * <li>Vertices are snapped to whole pixels; textures are mapped affinely (no perspective
 *   correction) with nearest sampling; colour is reduced to 15-bit with 4x4 ordered dither.</li>
 * <li>Large polygons near the camera are subdivided (as PS1 games did) which both limits
 *   affine warping and gives the ordering table finer depth keys.</li>
 * </ul>
 */
public final class SoftRenderer {
    public static final int L_GROUND = 0, L_DECAL = 1, L_RAISED = 2, L_SHADOW = 3, L_OBJECT = 4, LAYERS = 5;
    public static final int MODE_OPAQUE = 0, MODE_ALPHA = 1, MODE_SHADOW = 2;
    private static final int BUCKETS = 4096;
    private static final float BUCKET_SCALE = 24f;     // buckets per metre (~170 m range)
    private static final int VA = 6;                    // per screen vertex: x y u v light fog
    private static final int MAX_PRIMS = 60000;

    public final int W, H;
    public final int[] fb;
    public Texture[] textures = new Texture[0];

    // camera
    public float ex, ey, ez;
    private float rx, ry, rz, ux, uy, uz, fx, fy, fz;
    private float focal, cx, cy, tanX, tanY;
    public float near = 0.2f, far = 130f;
    public float fogStart = 40f, fogEnd = 125f;
    private int fogR = 170, fogG = 205, fogB = 230;
    private int zenR = 58, zenG = 120, zenB = 200;
    private int groundR = 92, groundG = 92, groundB = 94;
    public float time;
    public float subdivDist = 18f;

    // ordering table + primitive pool
    private final int[] head = new int[LAYERS * BUCKETS];
    private final int[] next = new int[MAX_PRIMS];
    private final int[] ptex = new int[MAX_PRIMS];
    private final int[] pmode = new int[MAX_PRIMS];
    private final float[] P = new float[MAX_PRIMS * 3 * VA];
    private int primCount;

    // stats
    public int statPolys, statPrims;

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    public SoftRenderer(int w, int h) {
        W = w;
        H = h;
        fb = new int[w * h];
        setFov(62f);
    }

    public void setFov(float fovYDeg) {
        cx = W * 0.5f;
        cy = H * 0.5f;
        focal = (H * 0.5f) / (float) Math.tan(Math.toRadians(fovYDeg) * 0.5);
        tanX = cx / focal;
        tanY = cy / focal;
    }

    public void setFogColor(int rgb) { fogR = (rgb >> 16) & 255; fogG = (rgb >> 8) & 255; fogB = rgb & 255; }
    public void setZenithColor(int rgb) { zenR = (rgb >> 16) & 255; zenG = (rgb >> 8) & 255; zenB = rgb & 255; }

    public void lookAt(float eyeX, float eyeY, float eyeZ, float tx, float ty, float tz) {
        ex = eyeX; ey = eyeY; ez = eyeZ;
        fx = tx - eyeX; fy = ty - eyeY; fz = tz - eyeZ;
        float l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= l; fy /= l; fz /= l;
        rx = -fz; ry = 0; rz = fx;                       // forward x worldUp
        l = (float) Math.sqrt(rx * rx + rz * rz);
        rx /= l; rz /= l;
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;  // right x forward
    }

    public float rightX() { return rx; }
    public float rightZ() { return rz; }
    public float upX() { return ux; }
    public float upY() { return uy; }
    public float upZ() { return uz; }
    public float fwdX() { return fx; }
    public float fwdY() { return fy; }
    public float fwdZ() { return fz; }

    // ------------------------------------------------------------------ frame

    public void begin() {
        Arrays.fill(head, -1);
        primCount = 0;
        statPolys = 0;
        statPrims = 0;
    }

    /** Fills the background with a dithered sky gradient matching the fog colour at the horizon. */
    public void drawSky() {
        float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, fy)));
        for (int y = 0; y < H; y++) {
            float elev = (float) Math.atan((cy - (y + 0.5f)) / focal) + pitch;
            int r, g, b;
            if (elev >= 0f) {
                float t = (float) Math.sqrt(Math.min(1f, elev / 0.9f));
                r = (int) (fogR + (zenR - fogR) * t); g = (int) (fogG + (zenG - fogG) * t); b = (int) (fogB + (zenB - fogB) * t);
            } else {
                // below the horizon: a ground tone, so hairline cracks between polygons don't flash sky
                float t = Math.min(1f, -elev * 5f);
                r = (int) (fogR + (groundR - fogR) * t); g = (int) (fogG + (groundG - fogG) * t); b = (int) (fogB + (groundB - fogB) * t);
            }
            int row = y * W;
            int dy = (y & 3) << 2;
            int c0 = q(r, g, b, BAYER[dy] >> 1), c1 = q(r, g, b, BAYER[dy + 1] >> 1);
            int c2 = q(r, g, b, BAYER[dy + 2] >> 1), c3 = q(r, g, b, BAYER[dy + 3] >> 1);
            for (int x = 0; x < W; x += 4) {
                fb[row + x] = c0;
                if (x + 1 < W) fb[row + x + 1] = c1;
                if (x + 2 < W) fb[row + x + 2] = c2;
                if (x + 3 < W) fb[row + x + 3] = c3;
            }
        }
    }

    private static int q(int r, int g, int b, int d) {
        r = Math.min(31, (r + d) >> 3);
        g = Math.min(31, (g + d) >> 3);
        b = Math.min(31, (b + d) >> 3);
        return 0xFF000000 | (((r << 3) | (r >> 2)) << 16) | (((g << 3) | (g >> 2)) << 8) | ((b << 3) | (b >> 2));
    }

    // ------------------------------------------------------------------ parallel raster

    private final int[] order = new int[MAX_PRIMS];
    private int orderCount;
    private java.util.concurrent.ExecutorService pool;
    private final java.util.List<java.util.concurrent.Callable<Void>> bands = new java.util.ArrayList<>();

    /** Uses up to {@code threads} cores for rasterisation (1 = single threaded). */
    public void setThreads(int threads) {
        if (pool != null) pool.shutdownNow();
        pool = null;
        bands.clear();
        if (threads <= 1) return;
        pool = java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "raster");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
        int nb = threads * 3;
        for (int i = 0; i < nb; i++) {
            final int y0 = H * i / nb, y1 = H * (i + 1) / nb;
            bands.add(() -> { drawBand(y0, y1); return null; });
        }
    }

    public void dispose() { setThreads(1); }

    /** Draws everything queued in the ordering table: layers in order, buckets far to near. */
    public void flush() {
        int n = 0;
        for (int layer = 0; layer < LAYERS; layer++) {
            int base = layer * BUCKETS;
            for (int b = BUCKETS - 1; b >= 0; b--)
                for (int p = head[base + b]; p >= 0; p = next[p]) order[n++] = p;
        }
        orderCount = n;
        statPrims = n;
        if (pool == null) {
            drawBand(0, H);
            return;
        }
        try {
            pool.invokeAll(bands);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drawBand(int y0, int y1) {
        for (int i = 0; i < orderCount; i++) drawTri(order[i], y0, y1);
    }

    // ------------------------------------------------------------------ culling helpers

    /** Conservative frustum test of a world AABB. */
    public boolean boxVisible(float x0, float y0, float z0, float x1, float y1, float z1) {
        int allL = 1, allR = 1, allT = 1, allB = 1, allN = 1, allF = 1;
        for (int i = 0; i < 8; i++) {
            float px = ((i & 1) == 0 ? x0 : x1) - ex, py = ((i & 2) == 0 ? y0 : y1) - ey, pz = ((i & 4) == 0 ? z0 : z1) - ez;
            float vx = px * rx + py * ry + pz * rz;
            float vy = px * ux + py * uy + pz * uz;
            float vz = px * fx + py * fy + pz * fz;
            if (vz >= near) allN = 0;
            if (vz <= far) allF = 0;
            if (vx >= -vz * tanX * 1.1f) allL = 0;
            if (vx <= vz * tanX * 1.1f) allR = 0;
            if (vy <= vz * tanY * 1.1f) allT = 0;
            if (vy >= -vz * tanY * 1.1f) allB = 0;
        }
        return (allL | allR | allT | allB | allN | allF) == 0;
    }

    // ------------------------------------------------------------------ submission

    private final float[] wp = new float[12], wuv = new float[8], wl = new float[4];

    /** Submits every visible polygon of a static geometry chunk. */
    public void drawGeo(Geo g) {
        if (g.count == 0 || !boxVisible(g.minX, g.minY, g.minZ, g.maxX, g.maxY, g.maxZ)) return;
        float far2 = far * far;
        for (int i = 0; i < g.count; i++) {
            int o = i * 12;
            float p0x = g.pos[o], p0y = g.pos[o + 1], p0z = g.pos[o + 2];
            float nx = g.nrm[i * 3], ny = g.nrm[i * 3 + 1], nz = g.nrm[i * 3 + 2];
            if ((nx != 0f || ny != 0f || nz != 0f) && nx * (ex - p0x) + ny * (ey - p0y) + nz * (ez - p0z) <= 0f) continue;
            // cheap distance reject on the first vertex (+ generous slack for big polys)
            float dx = p0x - ex, dz = p0z - ez;
            if (dx * dx + dz * dz > far2 * 1.6f) continue;
            Texture t = textures[g.tex[i]];
            float su = t.scrollU * time;
            for (int k = 0; k < 12; k++) wp[k] = g.pos[o + k];
            for (int k = 0; k < 4; k++) {
                wuv[k * 2] = (g.uv[i * 8 + k * 2] + su) * t.w;
                wuv[k * 2 + 1] = g.uv[i * 8 + k * 2 + 1] * t.h;
                wl[k] = g.light[i * 4 + k];
            }
            int fl = g.flags(i);
            submit(wp, wuv, wl, g.vertexCount(i), g.tex[i], g.layer(i),
                    (fl & Geo.FLAG_ALPHA) != 0 ? MODE_ALPHA : MODE_OPAQUE, g.bias[i], (fl & Geo.FLAG_NO_SUBDIV) == 0);
        }
    }

    /**
     * Camera-facing sprite. Offsets are in metres relative to (x,y,z) along the camera's
     * right/up axes; uv in texels.
     */
    public void billboard(int texId, float x, float y, float z, float left, float right, float bottom, float top,
                          float u0, float v0, float u1, float v1, float bias) {
        float[] p = wp;
        corner(p, 0, x, y, z, left, bottom);
        corner(p, 1, x, y, z, right, bottom);
        corner(p, 2, x, y, z, right, top);
        corner(p, 3, x, y, z, left, top);
        wuv[0] = u0; wuv[1] = v1; wuv[2] = u1; wuv[3] = v1; wuv[4] = u1; wuv[5] = v0; wuv[6] = u0; wuv[7] = v0;
        wl[0] = wl[1] = wl[2] = wl[3] = 1f;
        // Sort by an upright point at mid-height, not by the tilted quad: a camera-facing quad
        // leans back into walls when the camera looks down, which would sort it behind them.
        float mx = x - ex, my = y + (bottom + top) * 0.5f - ey, mz = z - ez;
        depthOverride = mx * fx + my * fy + mz * fz + bias;
        submit(p, wuv, wl, 4, texId, L_OBJECT, MODE_ALPHA, 0f, false);
        depthOverride = Float.NaN;
    }

    private float depthOverride = Float.NaN;

    private void corner(float[] p, int k, float x, float y, float z, float sx, float sy) {
        p[k * 3] = x + rx * sx + ux * sy;
        p[k * 3 + 1] = y + ry * sx + uy * sy;
        p[k * 3 + 2] = z + rz * sx + uz * sy;
    }

    /** Flat horizontal quad (e.g. a blob shadow) centred at x,z, uv covering the whole texture. */
    public void groundQuad(int texId, float x, float y, float z, float r, int mode, int layer) {
        Texture t = textures[texId];
        float[] p = wp;
        p[0] = x - r; p[1] = y; p[2] = z + r;
        p[3] = x + r; p[4] = y; p[5] = z + r;
        p[6] = x + r; p[7] = y; p[8] = z - r;
        p[9] = x - r; p[10] = y; p[11] = z - r;
        wuv[0] = 0; wuv[1] = t.h; wuv[2] = t.w; wuv[3] = t.h; wuv[4] = t.w; wuv[5] = 0; wuv[6] = 0; wuv[7] = 0;
        wl[0] = wl[1] = wl[2] = wl[3] = 1f;
        submit(p, wuv, wl, 4, texId, layer, mode, 0f, false);
    }

    // view-space scratch, one slot per subdivision level
    private static final int MAX_LEVEL = 4;
    private final float[][] lvl = new float[MAX_LEVEL + 1][9 * VA];
    private final float[][] child = new float[MAX_LEVEL + 1][4 * VA];
    private final float[] vq = new float[4 * VA];

    /**
     * Submits a world-space polygon (3 or 4 verts). UVs in texels. No backface culling here.
     */
    public void submit(float[] p, float[] uvT, float[] l, int nv, int texId, int layer, int mode, float bias, boolean subdiv) {
        int allNear = 1, allFar = 1;
        float zsum = 0f;
        for (int k = 0; k < nv; k++) {
            float dx = p[k * 3] - ex, dy = p[k * 3 + 1] - ey, dz = p[k * 3 + 2] - ez;
            float vx = dx * rx + dy * ry + dz * rz;
            float vy = dx * ux + dy * uy + dz * uz;
            float vz = dx * fx + dy * fy + dz * fz;
            int o = k * VA;
            vq[o] = vx; vq[o + 1] = vy; vq[o + 2] = vz;
            vq[o + 3] = uvT[k * 2]; vq[o + 4] = uvT[k * 2 + 1]; vq[o + 5] = l[k];
            if (vz >= near) allNear = 0;
            if (vz <= far) allFar = 0;
            zsum += vz;
        }
        if (allNear == 1 || allFar == 1) return;
        // side planes
        int allL = 1, allR = 1, allT = 1, allB = 1;
        for (int k = 0; k < nv; k++) {
            float vx = vq[k * VA], vy = vq[k * VA + 1], vz = vq[k * VA + 2];
            if (vx >= -vz * tanX) allL = 0;
            if (vx <= vz * tanX) allR = 0;
            if (vy <= vz * tanY) allT = 0;
            if (vy >= -vz * tanY) allB = 0;
        }
        if ((allL | allR | allT | allB) != 0) return;
        statPolys++;
        if (subdiv && nv == 4) {
            System.arraycopy(vq, 0, child[0], 0, 4 * VA);
            quadRec(0, texId, layer, mode, bias);
        } else {
            emit(vq, nv, Float.isNaN(depthOverride) ? zsum / nv + bias : depthOverride, texId, layer, mode);
        }
    }

    private void quadRec(int level, int texId, int layer, int mode, float bias) {
        float[] q = child[level];
        float minZ = Float.MAX_VALUE, maxEdge = 0f, zsum = 0f;
        for (int k = 0; k < 4; k++) {
            float z = q[k * VA + 2];
            zsum += z;
            if (z < minZ) minZ = z;
            int n = ((k + 1) & 3) * VA;
            float dx = q[n] - q[k * VA], dy = q[n + 1] - q[k * VA + 1], dz = q[n + 2] - q[k * VA + 2];
            float e = dx * dx + dy * dy + dz * dz;
            if (e > maxEdge) maxEdge = e;
        }
        maxEdge = (float) Math.sqrt(maxEdge);
        float limit = Math.max(1.6f, minZ * 0.3f);
        if (level >= MAX_LEVEL || minZ > subdivDist || maxEdge <= limit) {
            emit(q, 4, zsum * 0.25f + bias, texId, layer, mode);
            return;
        }
        // 3x3 grid of points: corners 0..3 = (0,0) (1,0) (1,1) (0,1) in quad param space
        float[] g = lvl[level];
        // grid index gi = j*3+i (i along edge 0->1, j along edge 0->3)
        for (int j = 0; j <= 2; j++) {
            float t = j * 0.5f;
            for (int i = 0; i <= 2; i++) {
                float s = i * 0.5f;
                float w0 = (1 - s) * (1 - t), w1 = s * (1 - t), w2 = s * t, w3 = (1 - s) * t;
                int o = (j * 3 + i) * VA;
                for (int a = 0; a < VA; a++)
                    g[o + a] = q[a] * w0 + q[VA + a] * w1 + q[2 * VA + a] * w2 + q[3 * VA + a] * w3;
            }
        }
        float[] c = child[level + 1];
        for (int cj = 0; cj < 2; cj++) {
            for (int ci = 0; ci < 2; ci++) {
                int g0 = cj * 3 + ci, g1 = g0 + 1, g2 = g0 + 4, g3 = g0 + 3;
                System.arraycopy(g, g0 * VA, c, 0, VA);
                System.arraycopy(g, g1 * VA, c, VA, VA);
                System.arraycopy(g, g2 * VA, c, 2 * VA, VA);
                System.arraycopy(g, g3 * VA, c, 3 * VA, VA);
                quadRec(level + 1, texId, layer, mode, bias);
            }
        }
    }

    private final float[] clipA = new float[10 * VA], clipB = new float[10 * VA];
    private final float[] scr = new float[10 * VA];

    /** Near-clips a view-space polygon, projects it and links its triangles into the table. */
    private void emit(float[] v, int nv, float depth, int texId, int layer, int mode) {
        float[] in = v;
        int n = nv;
        boolean needClip = false;
        for (int k = 0; k < n; k++) if (in[k * VA + 2] < near) { needClip = true; break; }
        if (needClip) {
            int m = 0;
            for (int k = 0; k < n; k++) {
                int a = k * VA, b = ((k + 1) % n) * VA;
                float za = in[a + 2], zb = in[b + 2];
                boolean ina = za >= near, inb = zb >= near;
                if (ina) { System.arraycopy(in, a, clipA, m * VA, VA); m++; }
                if (ina != inb) {
                    float t = (near - za) / (zb - za);
                    for (int c = 0; c < VA; c++) clipA[m * VA + c] = in[a + c] + (in[b + c] - in[a + c]) * t;
                    m++;
                }
            }
            if (m < 3) return;
            in = clipA;
            n = m;
        }
        float fogK = 1f / (fogEnd - fogStart);
        int offL = 1, offR = 1, offT = 1, offB = 1;
        for (int k = 0; k < n; k++) {
            int o = k * VA;
            float z = in[o + 2];
            float iz = focal / z;
            float sx = (float) Math.floor(cx + in[o] * iz + 0.5f);
            float sy = (float) Math.floor(cy - in[o + 1] * iz + 0.5f);
            scr[o] = sx; scr[o + 1] = sy;
            scr[o + 2] = in[o + 3]; scr[o + 3] = in[o + 4]; scr[o + 4] = in[o + 5];
            float f = (z - fogStart) * fogK;
            scr[o + 5] = f < 0f ? 0f : (f > 1f ? 1f : f);
            if (sx >= 0) offL = 0;
            if (sx <= W) offR = 0;
            if (sy >= 0) offT = 0;
            if (sy <= H) offB = 0;
        }
        if ((offL | offR | offT | offB) != 0) return;
        int bucket = (int) (depth * BUCKET_SCALE);
        if (bucket < 0) bucket = 0;
        if (bucket >= BUCKETS) bucket = BUCKETS - 1;
        int hb = layer * BUCKETS + bucket;
        for (int k = 1; k < n - 1; k++) {
            if (primCount >= MAX_PRIMS) return;
            int p = primCount++;
            int o = p * 3 * VA;
            System.arraycopy(scr, 0, P, o, VA);
            System.arraycopy(scr, k * VA, P, o + VA, VA);
            System.arraycopy(scr, (k + 1) * VA, P, o + 2 * VA, VA);
            ptex[p] = texId;
            pmode[p] = mode;
            next[p] = head[hb];
            head[hb] = p;
        }
    }

    // ------------------------------------------------------------------ rasterizer

    private void drawTri(int p, int bandY0, int bandY1) {
        int o = p * 3 * VA;
        float[] A = P;
        // sort the three vertices by y
        int i0 = o, i1 = o + VA, i2 = o + 2 * VA, t;
        if (A[i1 + 1] < A[i0 + 1]) { t = i0; i0 = i1; i1 = t; }
        if (A[i2 + 1] < A[i0 + 1]) { t = i0; i0 = i2; i2 = t; }
        if (A[i2 + 1] < A[i1 + 1]) { t = i1; i1 = i2; i2 = t; }
        float x0 = A[i0], y0 = A[i0 + 1], x1 = A[i1], y1 = A[i1 + 1], x2 = A[i2], y2 = A[i2 + 1];
        if (y2 - y0 < 0.5f || y2 < bandY0 || y0 > bandY1) return;
        float dx1 = x1 - x0, dy1 = y1 - y0, dx2 = x2 - x0, dy2 = y2 - y0;
        float det = dx1 * dy2 - dx2 * dy1;
        if (det > -1e-4f && det < 1e-4f) return;
        float id = 1f / det;

        Texture tex = textures[ptex[p]];
        int[] tp = tex.px;
        int wm = tex.wMask, hm = tex.hMask, ws = tex.wShift;
        // re-base uvs near zero so fixed point never overflows on large repeating polygons
        float u0 = A[i0 + 2], u1 = A[i1 + 2], u2 = A[i2 + 2];
        float v0 = A[i0 + 3], v1 = A[i1 + 3], v2 = A[i2 + 3];
        float ub = (float) Math.floor(Math.min(u0, Math.min(u1, u2)) / tex.w) * tex.w;
        float vb = (float) Math.floor(Math.min(v0, Math.min(v1, v2)) / tex.h) * tex.h;
        u0 -= ub; u1 -= ub; u2 -= ub; v0 -= vb; v1 -= vb; v2 -= vb;
        float l0 = A[i0 + 4], l1 = A[i1 + 4], l2 = A[i2 + 4];
        float f0 = A[i0 + 5], f1 = A[i1 + 5], f2 = A[i2 + 5];

        float dudx = ((u1 - u0) * dy2 - (u2 - u0) * dy1) * id, dudy = ((u2 - u0) * dx1 - (u1 - u0) * dx2) * id;
        float dvdx = ((v1 - v0) * dy2 - (v2 - v0) * dy1) * id, dvdy = ((v2 - v0) * dx1 - (v1 - v0) * dx2) * id;
        float dldx = ((l1 - l0) * dy2 - (l2 - l0) * dy1) * id, dldy = ((l2 - l0) * dx1 - (l1 - l0) * dx2) * id;
        float dfdx = ((f1 - f0) * dy2 - (f2 - f0) * dy1) * id, dfdy = ((f2 - f0) * dx1 - (f1 - f0) * dx2) * id;
        int dU = (int) (dudx * 65536f), dV = (int) (dvdx * 65536f);
        int dL = (int) (dldx * 65536f), dF = (int) (dfdx * 65536f);

        int yStart = (int) Math.ceil(y0 - 0.5f), yEnd = (int) Math.ceil(y2 - 0.5f);
        if (yStart < bandY0) yStart = bandY0;
        if (yEnd > bandY1) yEnd = bandY1;
        if (yStart >= yEnd) return;
        float invL = 1f / dy2;
        float invA = dy1 > 0f ? 1f / dy1 : 0f, invB = (y2 - y1) > 0f ? 1f / (y2 - y1) : 0f;
        int mode = pmode[p];
        int fr = fogR, fg = fogG, fbb = fogB;
        int[] out = fb;
        int width = W;

        for (int y = yStart; y < yEnd; y++) {
            float yc = y + 0.5f;
            float xa = x0 + dx2 * (yc - y0) * invL;
            float xb = yc < y1 ? x0 + dx1 * (yc - y0) * invA : x1 + (x2 - x1) * (yc - y1) * invB;
            float xl = xa < xb ? xa : xb, xr = xa < xb ? xb : xa;
            int xs = (int) Math.ceil(xl - 0.5f), xe = (int) Math.ceil(xr - 0.5f);
            if (xs < 0) xs = 0;
            if (xe > width) xe = width;
            if (xs >= xe) continue;
            float ox = xs + 0.5f - x0, oy = yc - y0;
            int U = (int) ((u0 + dudx * ox + dudy * oy) * 65536f);
            int V = (int) ((v0 + dvdx * ox + dvdy * oy) * 65536f);
            int L = (int) ((l0 + dldx * ox + dldy * oy) * 65536f);
            int F = (int) ((f0 + dfdx * ox + dfdy * oy) * 65536f);
            int row = y * width;
            int db = (y & 3) << 2;
            if (mode == MODE_SHADOW) {
                for (int x = xs; x < xe; x++, U += dU, V += dV) {
                    int c = tp[(((V >> 16) & hm) << ws) | ((U >> 16) & wm)];
                    if ((c >>> 24) > (BAYER[db | (x & 3)] << 4) + 8) {
                        int d = out[row + x];
                        out[row + x] = 0xFF000000 | ((((d >> 16) & 255) * 9 >> 4) << 16)
                                | ((((d >> 8) & 255) * 9 >> 4) << 8) | ((d & 255) * 10 >> 4);
                    }
                }
                continue;
            }
            boolean alpha = mode == MODE_ALPHA;
            for (int x = xs; x < xe; x++, U += dU, V += dV, L += dL, F += dF) {
                int c = tp[(((V >> 16) & hm) << ws) | ((U >> 16) & wm)];
                if (alpha && (c >>> 24) < 128) continue;
                int lv = L >> 8, fv = F >> 8;
                if (lv < 0) lv = 0;
                if (fv < 0) fv = 0; else if (fv > 256) fv = 256;
                int r = (((c >> 16) & 255) * lv) >> 8;
                int g = (((c >> 8) & 255) * lv) >> 8;
                int b = ((c & 255) * lv) >> 8;
                if (r > 255) r = 255;
                if (g > 255) g = 255;
                if (b > 255) b = 255;
                r += ((fr - r) * fv) >> 8;
                g += ((fg - g) * fv) >> 8;
                b += ((fbb - b) * fv) >> 8;
                int d = BAYER[db | (x & 3)] >> 1;
                r = (r + d) >> 3; if (r > 31) r = 31;
                g = (g + d) >> 3; if (g > 31) g = 31;
                b = (b + d) >> 3; if (b > 31) b = 31;
                out[row + x] = 0xFF000000 | (((r << 3) | (r >> 2)) << 16) | (((g << 3) | (g >> 2)) << 8) | ((b << 3) | (b >> 2));
            }
        }
    }
}
