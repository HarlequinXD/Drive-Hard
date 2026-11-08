package com.harlequinxd.drivehard.core.render;

import com.harlequinxd.drivehard.core.Environment;

/**
 * What the game draws with: the camera, culling and the frame's lighting, shared by the CPU
 * {@link SoftRenderer} and the GPU renderer ({@code core.gl.GlRenderer}), so both frame and light
 * the scene identically.
 *
 * The camera never really pitches: like Mode 7 and voxel-space engines it looks horizontally and
 * shifts the horizon up or down instead (a y-shear). Vertical lines stay vertical and upright
 * sprites face the screen exactly.
 */
public abstract class Renderer {
    public static final int L_WATER = 0, L_GROUND = 1, L_DECAL = 2, L_RAISED = 3, L_SHADOW = 4, L_OBJECT = 5, LAYERS = 6;
    public static final int MODE_OPAQUE = 0, MODE_ALPHA = 1, MODE_SHADOW = 2, MODE_BLEND = 3, MODE_SPRITE = 4;

    /** Texture mapping: exact perspective (texels correct everywhere). */
    public static final int TEX_PERSPECTIVE = 0;
    /**
     * Texture mapping the way the GBA's software 3D engines did it (V-Rally 3, Stuntman, Asterix &amp;
     * Obelix XXL, Driv3r). The ARM7 has no divide instruction, so nothing is divided per pixel or
     * per row: each polygon is walked whole, edge by edge, with u and v stepped linearly down its
     * edges on screen and linearly across each row between them. Every polygon warps, roads and
     * floors included, more the closer and bigger it is, but a quad bends as one piece: no seam
     * along its diagonal as on the PS1, which splits it into two triangles.
     */
    public static final int TEX_GBA = 1;
    /** Texture mapping like the PS1: linear across the whole triangle on screen (warps on diagonals). */
    public static final int TEX_PS1 = 2;
    /** How textures sit on polygons: {@link #TEX_GBA}, {@link #TEX_PS1} or {@link #TEX_PERSPECTIVE}. */
    public int texMap = TEX_PERSPECTIVE;

    public final int W, H;
    /** The finished frame (CPU renderer); null when the frame lives on the GPU. */
    public final int[] fb;
    public Texture[] textures = new Texture[0];
    /** The painted ground map; null draws no ground (sky and polygons only). */
    public Ground.Source ground;
    /** Time of day and weather (null: fixed daylight). */
    public Environment env;

    // camera
    public float ex, ey, ez;
    protected float rx, ry, rz, ux, uy, uz, fx, fy, fz;
    protected float focal, cx, cy, tanX;
    /** Screen row of the horizon (the camera's pitch becomes a horizon shift). */
    protected float hy, tanTop, tanBot;
    /** PS1-style draw distances: polygons beyond {@code far} are dropped, small detail earlier. */
    public float near = 0.2f, far = 120f, detailDist = 55f;
    public float fogStart = 45f, fogEnd = 116f;
    protected int fogR = 170, fogG = 205, fogB = 230;
    protected int zenR = 58, zenG = 120, zenB = 200;
    public float time;

    // the frame's lighting and sky, read from the environment by readEnvironment()
    /** Scene light per channel, 256 = daylight. */
    protected int tR = 256, tG = 256, tB = 256;
    protected float dryness;
    protected float sunX, sunY, sunZ = 1f, moonX, moonY, moonZ, sunVis, moonVis, stars, rainbow, cover, cloudOX, cloudOZ;
    protected int cloudLit, cloudShade;
    protected boolean nightLights;
    /** Fog, rain and haze veil the sky (sun, clouds) toward the horizon colour. */
    protected float skyHaze;
    protected int litShare;

    // stats
    public int statPolys, statPrims;

    protected Renderer(int w, int h, int[] fb) {
        W = w;
        H = h;
        this.fb = fb;
        setFov(62f);
    }

    public void setFov(float fovYDeg) {
        cx = W * 0.5f;
        cy = H * 0.5f;
        focal = (H * 0.5f) / (float) Math.tan(Math.toRadians(fovYDeg) * 0.5);
        tanX = cx / focal;
        hy = cy;
        tanTop = hy / focal;
        tanBot = (H - hy) / focal;
    }

    public void setFogColor(int rgb) { fogR = (rgb >> 16) & 255; fogG = (rgb >> 8) & 255; fogB = rgb & 255; }
    public void setZenithColor(int rgb) { zenR = (rgb >> 16) & 255; zenG = (rgb >> 8) & 255; zenB = rgb & 255; }

    public void lookAt(float eyeX, float eyeY, float eyeZ, float tx, float ty, float tz) {
        ex = eyeX; ey = eyeY; ez = eyeZ;
        float dx = tx - eyeX, dy = ty - eyeY, dz = tz - eyeZ;
        float hl = (float) Math.sqrt(dx * dx + dz * dz);
        if (hl > 1e-4f) { fx = dx / hl; fz = dz / hl; }
        else if (fx == 0f && fz == 0f) fz = 1f;          // straight down: keep the last heading
        fy = 0f;
        rx = -fz; ry = 0; rz = fx;                       // forward x worldUp
        ux = 0f; uy = 1f; uz = 0f;
        // pitch becomes a horizon shift (clamped: shearing too far looks wrong)
        float pitch = (float) Math.atan2(dy, Math.max(hl, 1e-4f));
        pitch = Math.max(-0.75f, Math.min(0.6f, pitch));
        hy = cy + focal * (float) Math.tan(pitch);
        tanTop = hy / focal;
        tanBot = (H - hy) / focal;
    }

    /**
     * Projects a world point onto the screen: out = {x, y, depth}. Returns false when it is
     * behind the camera (out still holds the direction, flipped, for edge arrows).
     */
    public boolean project(float x, float y, float z, float[] out) {
        float dx = x - ex, dy = y - ey, dz = z - ez;
        float d = dx * fx + dz * fz, side = dx * rx + dz * rz;
        if (d < 0.1f) {
            out[0] = cx + Math.signum(side) * W; out[1] = hy; out[2] = d;
            return false;
        }
        out[0] = cx + focal * side / d;
        out[1] = hy - focal * dy / d;
        out[2] = d;
        return true;
    }

    /** Screen row of the horizon. */
    public float horizon() { return hy; }
    public float focal() { return focal; }

    public float rightX() { return rx; }
    public float rightZ() { return rz; }
    public float upX() { return ux; }
    public float upY() { return uy; }
    public float upZ() { return uz; }
    public float fwdX() { return fx; }
    public float fwdY() { return fy; }
    public float fwdZ() { return fz; }

    /** Copies this frame's light, sky and weather from the environment. */
    protected void readEnvironment() {
        Environment e0 = env;
        if (e0 == null) return;
        tR = e0.tintR; tG = e0.tintG; tB = e0.tintB;
        dryness = e0.dryness;
        setFogColor(e0.horizon);
        setZenithColor(e0.zenith);
        fogStart = 45f * e0.fogScale;
        fogEnd = 116f * e0.fogScale;
        sunX = e0.sunX; sunY = e0.sunY; sunZ = e0.sunZ; sunVis = e0.sunVis;
        moonX = e0.moonX; moonY = e0.moonY; moonZ = e0.moonZ; moonVis = e0.moonVis;
        stars = e0.stars; rainbow = e0.rainbow; cover = e0.cloudCover;
        cloudLit = e0.cloudLit; cloudShade = e0.cloudShade;
        cloudOX = (float) ((e0.hours * 60.0 * e0.windX) % 100000.0);
        cloudOZ = (float) ((e0.hours * 60.0 * e0.windZ) % 100000.0);
        nightLights = e0.night > 0.3f;
        skyHaze = Math.max(0f, Math.min(1f, (0.85f - e0.fogScale) / 0.55f));
        litShare = (int) (e0.windowsLit * 256f);
    }

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
            if (vy <= vz * tanTop * 1.1f + 0.5f) allT = 0;
            if (vy >= -vz * tanBot * 1.1f - 0.5f) allB = 0;
        }
        return (allL | allR | allT | allB | allN | allF) == 0;
    }

    // ------------------------------------------------------------------ the frame

    /** Starts a frame (after {@link #lookAt}). */
    public abstract void begin();

    public abstract void drawSky();

    public abstract void drawGround();

    /** Draws every visible polygon of a static batch. */
    public void drawGeo(Geo g) { drawGeoOffset(g, 0f, 0f, 0f); }

    /** Draws a batch translated by (ox,oy,oz): moving rigid objects such as trains. */
    public abstract void drawGeoOffset(Geo g, float ox, float oy, float oz);

    /**
     * Upright sprite facing the camera horizontally. Offsets are in metres relative to (x,y,z) along the
     * camera's horizontal right axis and world up; uv in texels.
     */
    public abstract void billboard(int texId, float x, float y, float z, float left, float right, float bottom, float top,
                                   float u0, float v0, float u1, float v1, float bias);

    /** Flat horizontal quad (e.g. a blob shadow) centred at x,z, uv covering the whole texture. */
    public void groundQuad(int texId, float x, float y, float z, float r, int mode, int layer) {
        groundQuad(texId, x, y, z, r, mode, layer, 0f);
    }

    public abstract void groundQuad(int texId, float x, float y, float z, float r, int mode, int layer, float bias);

    /** Rotated flat quad on the ground (e.g. a car's shadow): half length along yaw, half width across. */
    public abstract void groundQuadRotated(int texId, float x, float y, float z, float halfLen, float halfWid, float yaw, int mode, int layer);

    /**
     * A cut-out sprite on any flat quad (puppet parts): corners bottom-left, bottom-right,
     * top-right, top-left (world xyz), uv in texels; sorted with the objects, nearer by -bias metres.
     */
    public abstract void spriteQuad(int texId, float[] corners, float[] uvTexels, float bias);

    /** Draws everything submitted since {@link #begin}. */
    public abstract void flush();

    /** Rain and lightning over the finished frame. */
    public abstract void weatherOverlay();

    /** Uses up to {@code threads} cores (CPU renderer only). */
    public void setThreads(int threads) { }

    public void dispose() { }
}
