package com.harlequinxd.drivehard.core.gl;

import com.harlequinxd.drivehard.core.Environment;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.Ground;
import com.harlequinxd.drivehard.core.render.Renderer;
import com.harlequinxd.drivehard.core.render.SoftRenderer;
import com.harlequinxd.drivehard.core.render.Texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The GPU renderer (OpenGL ES 3.0): draws the same frame as the {@link SoftRenderer} with the
 * phone's graphics chip instead of its CPU, which runs much cooler.
 *
 * <ul>
 * <li>The frame is drawn at the game's low resolution into an off-screen image, then scaled up
 *   unfiltered: the PS1 look, and very little work for the GPU.</li>
 * <li>Sky, ground colours, the animated sea and polygon shading run in shaders ported line for
 *   line from the CPU renderer, with the same 15-bit colour and 4x4 dither.</li>
 * <li>The Mode 7 ground map becomes one textured height-mapped mesh per 64 m tile (its painted
 *   picture uploaded with its mips); the ground writes depth 6% farther than it is, so hills hide
 *   what is behind them while things standing on the ground always show, as on the CPU.</li>
 * <li>Chunk batches are uploaded to video memory the first time they are seen and deleted soon
 *   after they stop being drawn (the chunk streamed out); textures are packed into an atlas.</li>
 * <li>Flat layers (water, ground, decals, shadows) draw in layer order over the ground; objects
 *   and sprites then use the depth buffer.</li>
 * </ul>
 *
 * All calls must come from the thread that owns the GL context.
 */
public final class GlRenderer extends Renderer {
    private static final float FAR_PLANE = 2000f;
    private static final int ATLAS = 2048, ATLAS_MAX_TEX = 512;
    private static final int STRIDE = 36;                     // static / dynamic vertex
    /** Video memory kept for chunk batches before the least recently drawn go. */
    private static final long BATCH_BUDGET = 96L << 20;
    private static final int RGBA16UI = 0x8D76, RGBA_INTEGER = 0x8D99, RGBA32F = 0x8814;

    private final Gl gl;
    private boolean ready;
    private int frame;
    /** PS1 vertex snapping (whole-pixel vertices, the slight wobble of the original hardware). */
    public boolean snap = true;
    /**
     * PlayStation ordering table instead of a depth buffer between objects: every object polygon and
     * sprite is sorted far-to-near by its average depth (its farthest for {@link Geo#FLAG_SORT_FAR}) and
     * painted in that order, so nearer polygons simply cover farther ones, with the PS1's sorting
     * quirks. The ground alone still writes depth, so hills hide what is behind them. This is the
     * same order {@link com.harlequinxd.drivehard.core.render.SoftRenderer} draws in.
     */
    public boolean psxSort = true;
    /** Polygons uploaded per frame at most (keeps streaming smooth). */
    public int uploadBudget = 60000;
    public int tileBudget = 8;
    // stats
    public int statBatches, statTiles, statUploads;
    public long gpuBytes;

    private Program poly, groundProg, sky, flat, blit;
    private int fbo, fboTex, fboDepth, emptyVao, overlayTex, imageTex, imageW, imageH;
    private int cloudTex, waveTex, alongTex, foamTex, infoTex;
    private int gridIbo, gridCount;

    public GlRenderer(Gl gl, int w, int h) {
        super(w, h, null);
        this.gl = gl;
    }

    /** The GL context was lost (the app went to the background): everything is uploaded again. */
    public void contextLost() {
        ready = false;
        batches.clear();
        tiles.clear();
        flats.clear();
        singles.clear();
        atlasRect.clear();
        shelves.clear();
        java.util.Arrays.fill(infoOf, null);
        atlasTex = 0;
        atlasPages = 0;
        gpuBytes = 0;
        otVao = otVbo = cornerTex = 0;
        sandTex = 0;
        sandId = -1;
    }

    @Override public void dispose() {
        // GL objects can only be freed on the thread that owns the context
        if (!ready || Thread.currentThread() != owner) return;
        for (Batch b : batches.values()) freeBatch(b);
        batches.clear();
        for (GTile t : tiles.values()) freeTile(t);
        tiles.clear();
        for (GTile t : flats.values()) freeTile(t);
        flats.clear();
        for (Single s : singles.values()) gl.deleteTexture(s.tex);
        singles.clear();
        if (otVao != 0) { gl.deleteVertexArray(otVao); gl.deleteBuffer(otVbo); otVao = otVbo = 0; }
        if (cornerTex != 0) { gl.deleteTexture(cornerTex); cornerTex = 0; }
    }

    // ------------------------------------------------------------------ set up

    private static final class Program {
        final int id;
        final Map<String, Integer> locs = new HashMap<>();
        final Gl gl;

        Program(Gl gl, String vs, String fs) {
            this.gl = gl;
            int v = compile(gl, Gl.VERTEX_SHADER, vs), f = compile(gl, Gl.FRAGMENT_SHADER, fs);
            id = gl.createProgram();
            gl.attachShader(id, v);
            gl.attachShader(id, f);
            gl.linkProgram(id);
            if (gl.getProgrami(id, Gl.LINK_STATUS) == 0) throw new IllegalStateException("GL link: " + gl.getProgramInfoLog(id));
            gl.deleteShader(v);
            gl.deleteShader(f);
        }

        int loc(String name) {
            Integer l = locs.get(name);
            if (l == null) { l = gl.getUniformLocation(id, name); locs.put(name, l); }
            return l;
        }

        private static int compile(Gl gl, int type, String src) {
            int s = gl.createShader(type);
            gl.shaderSource(s, src);
            gl.compileShader(s);
            if (gl.getShaderi(s, Gl.COMPILE_STATUS) == 0) {
                String log = gl.getShaderInfoLog(s);
                throw new IllegalStateException("GL shader: " + log);
            }
            return s;
        }
    }

    private Thread owner;

    private void init() {
        owner = Thread.currentThread();
        poly = new Program(gl, GlShaders.POLY_VS, GlShaders.POLY_FS);
        groundProg = new Program(gl, GlShaders.GROUND_VS, GlShaders.GROUND_FS);
        sky = new Program(gl, GlShaders.FULLSCREEN_VS, GlShaders.SKY_FS);
        flat = new Program(gl, GlShaders.FLAT_VS, GlShaders.FLAT_FS);
        blit = new Program(gl, GlShaders.FULLSCREEN_VS, GlShaders.BLIT_FS);
        emptyVao = gl.genVertexArray();
        gl.pixelStorei(Gl.UNPACK_ALIGNMENT, 1);
        gl.pixelStorei(Gl.PACK_ALIGNMENT, 1);
        // the off-screen frame
        fboTex = newTexture(Gl.NEAREST, Gl.CLAMP_TO_EDGE);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, W, H, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
        fboDepth = gl.genRenderbuffer();
        gl.bindRenderbuffer(Gl.RENDERBUFFER, fboDepth);
        gl.renderbufferStorage(Gl.RENDERBUFFER, Gl.DEPTH_COMPONENT24, W, H);
        fbo = gl.genFramebuffer();
        gl.bindFramebuffer(Gl.FRAMEBUFFER, fbo);
        gl.framebufferTexture2D(Gl.FRAMEBUFFER, Gl.COLOR_ATTACHMENT0, Gl.TEXTURE_2D, fboTex, 0);
        gl.framebufferRenderbuffer(Gl.FRAMEBUFFER, Gl.DEPTH_ATTACHMENT, Gl.RENDERBUFFER, fboDepth);
        int st = gl.checkFramebufferStatus(Gl.FRAMEBUFFER);
        if (st != Gl.FRAMEBUFFER_COMPLETE) throw new IllegalStateException("GL framebuffer incomplete: 0x" + Integer.toHexString(st));
        overlayTex = newTexture(Gl.NEAREST, Gl.CLAMP_TO_EDGE);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, W, H, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
        // tables of the sky and the sea
        cloudTex = r8(SoftRenderer.CLOUDS, 256, 256, Gl.NEAREST);
        alongTex = r8(SoftRenderer.ALONG, 64, 64, Gl.NEAREST);
        foamTex = r8(SoftRenderer.FOAM, SoftRenderer.WN, SoftRenderer.WN, Gl.NEAREST);
        waveTex = newTexture(Gl.NEAREST, Gl.REPEAT);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RG8, SoftRenderer.WN, SoftRenderer.WN, Gl.RG, Gl.UNSIGNED_BYTE, null);
        // texture id -> atlas page and rectangle (64 x 64 ids)
        scratch();
        infoTex = gl.genTexture();
        gl.bindTexture(Gl.TEXTURE_2D, infoTex);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, Gl.NEAREST);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MAG_FILTER, Gl.NEAREST);
        gl.texImage2D(Gl.TEXTURE_2D, 0, RGBA16UI, 64, 64, RGBA_INTEGER, Gl.UNSIGNED_SHORT, direct(64 * 64 * 8));
        // the ground mesh: 16 x 16 quads per tile (each 8 m height cell split in four)
        IntBuffer idx = direct(16 * 16 * 6 * 4).asIntBuffer();
        for (int j = 0; j < 16; j++)
            for (int i = 0; i < 16; i++) {
                int a = j * 17 + i, b = a + 1, c = a + 17, d = c + 1;
                idx.put(a).put(c).put(b).put(b).put(c).put(d);
            }
        idx.flip();
        gridCount = 16 * 16 * 6;
        gridIbo = gl.genBuffer();
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, gridIbo);
        gl.bufferData(Gl.ELEMENT_ARRAY_BUFFER, gridCount * 4, idx, Gl.STATIC_DRAW);
        dynVbo = gl.genBuffer();
        dynIbo = gl.genBuffer();
        dynVao = gl.genVertexArray();
        flatVbo = gl.genBuffer();
        flatVao = gl.genVertexArray();
        gl.bindVertexArray(flatVao);
        gl.bindBuffer(Gl.ARRAY_BUFFER, flatVbo);
        gl.enableVertexAttribArray(0);
        gl.vertexAttribPointer(0, 2, Gl.FLOAT, false, 12, 0);
        gl.enableVertexAttribArray(1);
        gl.vertexAttribPointer(1, 4, Gl.UNSIGNED_BYTE, true, 12, 8);
        gl.bindVertexArray(0);
        // the atlas, sized for the textures there are now (it grows if more come)
        long area = 0;
        for (Texture t : textures) if (t != null && t.w <= ATLAS_MAX_TEX && t.h <= ATLAS_MAX_TEX) area += (long) t.w * t.h;
        allocateAtlas(Math.max(1, (int) Math.ceil(area * 1.08 / ((double) ATLAS * ATLAS))));
        ready = true;
    }

    /** Uploads go through texture unit 5, so they never disturb what a draw has bound. */
    private void scratch() { gl.activeTexture(Gl.TEXTURE0 + 5); }

    private int newTexture(int filter, int wrap) {
        scratch();
        int t = gl.genTexture();
        gl.bindTexture(Gl.TEXTURE_2D, t);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, filter);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MAG_FILTER, filter == Gl.NEAREST_MIPMAP_NEAREST ? Gl.NEAREST : filter);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_S, wrap);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_T, wrap);
        return t;
    }

    private int r8(float[] v, int w, int h, int filter) {
        ByteBuffer b = direct(w * h);
        for (int i = 0; i < w * h; i++) b.put(i, (byte) Math.max(0, Math.min(255, Math.round(v[i] * 255f))));
        int t = newTexture(filter, Gl.REPEAT);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.R8, w, h, Gl.RED, Gl.UNSIGNED_BYTE, b);
        return t;
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(Math.max(4, bytes)).order(ByteOrder.nativeOrder());
    }

    // ------------------------------------------------------------------ texture atlas

    private int atlasTex, atlasPages;
    private final IdentityHashMap<Texture, int[]> atlasRect = new IdentityHashMap<>();
    private final Texture[] infoOf = new Texture[4096];
    private final ByteBuffer infoTexel = direct(8);
    private static final class Shelf { int page, y, h, x; }
    private final List<Shelf> shelves = new ArrayList<>();
    private int[] pageFill = new int[0];

    private void allocateAtlas(int pages) {
        scratch();
        int t = gl.genTexture();
        gl.bindTexture(Gl.TEXTURE_2D_ARRAY, t);
        gl.texParameteri(Gl.TEXTURE_2D_ARRAY, Gl.TEXTURE_MIN_FILTER, Gl.NEAREST);
        gl.texParameteri(Gl.TEXTURE_2D_ARRAY, Gl.TEXTURE_MAG_FILTER, Gl.NEAREST);
        gl.texImage3D(Gl.TEXTURE_2D_ARRAY, 0, Gl.RGBA8, ATLAS, ATLAS, pages, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
        if (atlasTex != 0) {
            gl.deleteTexture(atlasTex);
            gpuBytes -= (long) atlasPages * ATLAS * ATLAS * 4;
            // a bigger atlas: everything packed so far goes back in the same place
            for (Map.Entry<Texture, int[]> e : atlasRect.entrySet()) uploadToAtlas(t, e.getKey(), e.getValue());
        }
        atlasTex = t;
        atlasPages = pages;
        pageFill = java.util.Arrays.copyOf(pageFill, pages);
        gpuBytes += (long) pages * ATLAS * ATLAS * 4;
    }

    private void uploadToAtlas(int tex, Texture t, int[] r) {
        scratch();
        gl.bindTexture(Gl.TEXTURE_2D_ARRAY, tex);
        IntBuffer px = direct(t.w * t.h * 4).asIntBuffer();
        px.put(t.px, 0, t.w * t.h).flip();
        gl.texSubImage3D(Gl.TEXTURE_2D_ARRAY, 0, r[1], r[2], r[0], t.w, t.h, 1, Gl.RGBA, Gl.UNSIGNED_BYTE, px);
    }

    /** Shelf packing: textures are powers of two, so shelves of one height pack them tightly. */
    private int[] pack(int w, int h) {
        Shelf best = null;
        for (Shelf s : shelves)
            if (s.h >= h && s.h < h * 2 && s.x + w <= ATLAS && (best == null || s.h < best.h)) best = s;
        if (best == null) {
            for (int p = 0; p < atlasPages && best == null; p++)
                if (pageFill[p] + h <= ATLAS) {
                    best = new Shelf();
                    best.page = p; best.y = pageFill[p]; best.h = h;
                    pageFill[p] += h;
                    shelves.add(best);
                }
            if (best == null) {
                allocateAtlas(atlasPages + 1);
                return pack(w, h);
            }
        }
        int[] r = {best.page, best.x, best.y, w, h};
        best.x += w;
        return r;
    }

    /** Makes sure texture {@code id} is in the atlas (true), or that it must be drawn on its own (false). */
    private boolean atlas(int id) {
        if (id < 0 || id >= textures.length) return false;
        Texture t = textures[id];
        if (t == null || t.w > ATLAS_MAX_TEX || t.h > ATLAS_MAX_TEX || id >= infoOf.length) return false;
        if (infoOf[id] == t) return true;
        int[] r = atlasRect.get(t);
        if (r == null) {
            r = pack(t.w, t.h);
            atlasRect.put(t, r);
            uploadToAtlas(atlasTex, t, r);
        }
        scratch();
        infoTexel.clear();
        infoTexel.putShort((short) (r[1] | (r[0] << 11))).putShort((short) r[2]).putShort((short) r[3]).putShort((short) r[4]).flip();
        gl.bindTexture(Gl.TEXTURE_2D, infoTex);
        gl.texSubImage2D(Gl.TEXTURE_2D, 0, id & 63, id >> 6, 1, 1, RGBA_INTEGER, Gl.UNSIGNED_SHORT, infoTexel);
        infoOf[id] = t;
        return true;
    }

    /** Textures drawn on their own (sprite sheets): uploaded when first used, dropped when unused. */
    private static final class Single { int tex, lastUse; }
    private final IdentityHashMap<Texture, Single> singles = new IdentityHashMap<>();

    private int single(Texture t) {
        Single s = singles.get(t);
        if (s == null) {
            s = new Single();
            s.tex = newTexture(Gl.NEAREST, Gl.CLAMP_TO_EDGE);
            IntBuffer px = direct(t.w * t.h * 4).asIntBuffer();
            px.put(t.px, 0, Math.min(t.px.length, t.w * t.h)).flip();
            gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, t.w, t.h, Gl.RGBA, Gl.UNSIGNED_BYTE, px);
            singles.put(t, s);
            gpuBytes += (long) t.w * t.h * 4;
        }
        s.lastUse = frame;
        return s.tex;
    }

    // ------------------------------------------------------------------ frame

    private final float[] waveMod = new float[SoftRenderer.WN * SoftRenderer.WN];
    private final ByteBuffer waveBytes = direct(SoftRenderer.WN * SoftRenderer.WN * 2);
    private float seaChop = 1f;
    private int seaSkyR, seaSkyG, seaSkyB;

    @Override public void begin() {
        if (!ready) init();
        readEnvironment();
        frame++;
        draws.clear();
        dynCount = 0;
        flatCount = 0;
        statPolys = statPrims = statBatches = statTiles = statUploads = 0;
        gl.bindFramebuffer(Gl.FRAMEBUFFER, fbo);
        gl.viewport(0, 0, W, H);
        gl.disable(Gl.BLEND);
        gl.depthMask(true);
        gl.clearColor(fogR / 255f, fogG / 255f, fogB / 255f, 1f);
        gl.clear(Gl.COLOR_BUFFER_BIT | Gl.DEPTH_BUFFER_BIT);
    }

    private void camera(Program p) {
        gl.uniform3f(p.loc("uEye"), ex, ey, ez);
        gl.uniform4f(p.loc("uBasis"), rx, rz, fx, fz);
        gl.uniform4f(p.loc("uProj"), 2f * focal / W, 2f * focal / H, 1f - 2f * hy / H, snap ? 1f : 0f);
        float n = near, f = FAR_PLANE;
        gl.uniform2f(p.loc("uDepth"), (f + n) / (f - n), -2f * f * n / (f - n));
        gl.uniform2f(p.loc("uRes"), W, H);
        gl.uniform1f(p.loc("uRows"), H);
    }

    private void fullscreen(Program p, float x0, float y0, float x1, float y1, boolean flip) {
        gl.uniform4f(p.loc("uRect"), x0, y0, x1, y1);
        gl.uniform1i(p.loc("uFlipV"), flip ? 1 : 0);
        gl.bindVertexArray(emptyVao);
        gl.drawArrays(Gl.TRIANGLE_STRIP, 0, 4);
    }

    @Override public void drawSky() {
        gl.disable(Gl.DEPTH_TEST);
        gl.depthMask(false);
        gl.useProgram(sky.id);
        gl.uniform1f(sky.loc("uRows"), H);
        gl.uniform4f(sky.loc("uView"), cx, hy, focal, env != null ? 1f : 0f);
        gl.uniform4f(sky.loc("uBasis"), rx, rz, fx, fz);
        gl.uniform3f(sky.loc("uEye"), ex, ey, ez);
        gl.uniform3f(sky.loc("uFog"), fogR, fogG, fogB);
        gl.uniform3f(sky.loc("uZen"), zenR, zenG, zenB);
        gl.uniform4f(sky.loc("uSun"), sunX, sunY, sunZ, sunVis);
        gl.uniform4f(sky.loc("uMoon"), moonX, moonY, moonZ, moonVis);
        gl.uniform4f(sky.loc("uSkyFx"), stars, rainbow, cover, skyHaze);
        gl.uniform3f(sky.loc("uCloudLit"), (cloudLit >> 16) & 255, (cloudLit >> 8) & 255, cloudLit & 255);
        gl.uniform3f(sky.loc("uCloudShade"), (cloudShade >> 16) & 255, (cloudShade >> 8) & 255, cloudShade & 255);
        gl.uniform2f(sky.loc("uCloudOff"), cloudOX, cloudOZ);
        gl.activeTexture(Gl.TEXTURE0);
        gl.bindTexture(Gl.TEXTURE_2D, cloudTex);
        gl.uniform1i(sky.loc("uClouds"), 0);
        fullscreen(sky, -1, -1, 1, 1, false);
        lightning();
        gl.enable(Gl.DEPTH_TEST);
        gl.depthMask(true);
    }

    // ------------------------------------------------------------------ ground

    private static final class GTile { int vao, vbo, tex, water, lastUse; float minY, maxY; long bytes; }
    private final IdentityHashMap<Ground.Tile, GTile> tiles = new IdentityHashMap<>();
    /** Flat stand-ins where no tile is loaded yet (most recently used kept). */
    private final LinkedHashMap<Long, GTile> flats = new LinkedHashMap<>(64, 0.75f, true);

    @Override public void drawGround() {
        if (ground == null) return;
        updateWater();
        gl.useProgram(groundProg.id);
        camera(groundProg);
        Program p = groundProg;
        gl.uniform1f(p.loc("uRows"), H);
        gl.uniform2f(p.loc("uMip"), Ground.TEXEL * focal * 1.4f, Ground.MIPS);
        gl.uniform1f(p.loc("uSandM"), Ground.SAND_M);
        gl.uniform4f(p.loc("uTintDry"), tR / 256f, tG / 256f, tB / 256f, dryness);
        gl.uniform3f(p.loc("uFog"), fogR, fogG, fogB);
        gl.uniform3f(p.loc("uFogRange"), fogStart, 1f / (fogEnd - fogStart), far);
        gl.uniform4f(p.loc("uSea"), seaSkyR, seaSkyG, seaSkyB, seaChop);
        gl.uniform2f(p.loc("uTimeEye"), time, ey);
        gl.uniform1i(p.loc("uMap"), 0);
        gl.uniform1i(p.loc("uWater"), 1);
        gl.uniform1i(p.loc("uWaves"), 2);
        gl.uniform1i(p.loc("uAlong"), 3);
        gl.uniform1i(p.loc("uFoam"), 4);
        gl.activeTexture(Gl.TEXTURE0 + 2); gl.bindTexture(Gl.TEXTURE_2D, waveTex);
        gl.activeTexture(Gl.TEXTURE0 + 3); gl.bindTexture(Gl.TEXTURE_2D, alongTex);
        gl.activeTexture(Gl.TEXTURE0 + 4); gl.bindTexture(Gl.TEXTURE_2D, foamTex);
        gl.depthFunc(Gl.LEQUAL);
        int reach = (int) Math.ceil(far / Ground.TILE) + 1;
        int tx0 = (int) Math.floor(ex / Ground.TILE) - reach, tz0 = (int) Math.floor(ez / Ground.TILE) - reach;
        int budget = tileBudget;
        for (int j = 0; j <= reach * 2; j++)
            for (int i = 0; i <= reach * 2; i++) {
                int cx = tx0 + i, cz = tz0 + j;
                float x0 = cx * Ground.TILE, z0 = cz * Ground.TILE;
                // nearest point of the tile within the draw distance?
                float nx = Math.max(x0, Math.min(ex, x0 + Ground.TILE)) - ex, nz = Math.max(z0, Math.min(ez, z0 + Ground.TILE)) - ez;
                if (nx * nx + nz * nz > far * far * 1.1f) continue;
                Ground.Tile t = ground.tile(cx, cz);
                // loaded dry land is all polygons; the pass draws the sea, and flat colours where nothing is loaded yet
                if (t != null && t.water == null) continue;
                GTile g = t == null ? null : tiles.get(t);
                if (t != null && g == null && budget > 0) { g = uploadTile(t); budget--; }
                if (t != null && g == null) continue;
                boolean flatTile = g == null;
                if (flatTile) g = flatTile(cx, cz);
                if (!boxVisible(x0, g.minY, z0, x0 + Ground.TILE, g.maxY, z0 + Ground.TILE)) continue;
                g.lastUse = frame;
                gl.uniform1i(p.loc("uFlat"), flatTile ? 1 : 0);
                gl.uniform1i(p.loc("uHasWater"), !flatTile && g.water != 0 ? 1 : 0);
                // the sea's depth a hair nearer than the terrain tiles' (it wins where both lie)
                gl.uniform1f(p.loc("uGroundS"), flatTile ? 0.94f : 0.95f);
                gl.uniform2f(p.loc("uTile"), x0, z0);
                gl.activeTexture(Gl.TEXTURE0);
                gl.bindTexture(Gl.TEXTURE_2D, flatTile ? 0 : sandTexture());
                gl.activeTexture(Gl.TEXTURE0 + 1);
                gl.bindTexture(Gl.TEXTURE_2D, g.water);
                gl.bindVertexArray(g.vao);
                gl.drawElements(Gl.TRIANGLES, gridCount, Gl.UNSIGNED_INT, 0);
                statTiles++;
            }
        gl.activeTexture(Gl.TEXTURE0);
    }

    private GTile meshTile(float[] pos, int[] colour) {
        GTile g = new GTile();
        ByteBuffer vb = direct(17 * 17 * 16);
        g.minY = Float.MAX_VALUE;
        g.maxY = -Float.MAX_VALUE;
        for (int k = 0; k < 17 * 17; k++) {
            float y = pos[k * 3 + 1];
            g.minY = Math.min(g.minY, y);
            g.maxY = Math.max(g.maxY, y);
            vb.putFloat(pos[k * 3]).putFloat(y).putFloat(pos[k * 3 + 2]);
            int c = colour == null ? 0 : colour[k];
            vb.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 255);
        }
        vb.flip();
        g.vao = gl.genVertexArray();
        gl.bindVertexArray(g.vao);
        g.vbo = gl.genBuffer();
        gl.bindBuffer(Gl.ARRAY_BUFFER, g.vbo);
        gl.bufferData(Gl.ARRAY_BUFFER, 17 * 17 * 16, vb, Gl.STATIC_DRAW);
        gl.enableVertexAttribArray(0);
        gl.vertexAttribPointer(0, 3, Gl.FLOAT, false, 16, 0);
        gl.enableVertexAttribArray(1);
        gl.vertexAttribPointer(1, 4, Gl.UNSIGNED_BYTE, true, 16, 12);
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, gridIbo);
        gl.bindVertexArray(0);
        g.bytes = 17 * 17 * 16;
        return g;
    }

    private GTile uploadTile(Ground.Tile t) {
        float x0 = t.cx * Ground.TILE, z0 = t.cz * Ground.TILE;
        float[] pos = new float[17 * 17 * 3];
        for (int j = 0; j <= 16; j++)
            for (int i = 0; i <= 16; i++) {
                float gx = i * 0.5f, gz = j * 0.5f;          // in 8 m height cells
                int ix = Math.min(Ground.HCELLS - 1, (int) gx), iz = Math.min(Ground.HCELLS - 1, (int) gz);
                float fx2 = gx - ix, fz2 = gz - iz;
                int o = iz * (Ground.HCELLS + 1) + ix;
                float a = t.h[o], b = t.h[o + 1], c = t.h[o + Ground.HCELLS + 1], d = t.h[o + Ground.HCELLS + 2];
                float top = a + (b - a) * fx2, bot = c + (d - c) * fx2;
                int k = (j * 17 + i) * 3;
                pos[k] = x0 + i * 4f; pos[k + 1] = top + (bot - top) * fz2; pos[k + 2] = z0 + j * 4f;
            }
        GTile g = meshTile(pos, null);
        if (t.water != null) {
            int n = Ground.WRES + 1;
            ByteBuffer wb = direct(n * n);
            for (int i = 0; i < n * n; i++) {
                int code = t.water[i] & 255;
                wb.put(i, (byte) (code == 0 ? 0 : Math.min(255, code + 3)));
            }
            g.water = newTexture(Gl.LINEAR, Gl.CLAMP_TO_EDGE);
            gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.R8, n, n, Gl.RED, Gl.UNSIGNED_BYTE, wb);
            g.bytes += n * n;
        }
        tiles.put(t, g);
        gpuBytes += g.bytes;
        return g;
    }

    private int sandTex, sandId = -1;

    /** The ground's sand tile as a repeating texture, for the sea floor in the shallows. */
    private int sandTexture() {
        int id = ground.sandTexture();
        if (sandTex != 0 && sandId == id) return sandTex;
        Texture t = textures[id];
        if (sandTex == 0) sandTex = newTexture(Gl.NEAREST, Gl.REPEAT);
        gl.bindTexture(Gl.TEXTURE_2D, sandTex);
        IntBuffer px = direct(t.w * t.h * 4).asIntBuffer();
        px.put(t.px, 0, t.w * t.h).flip();
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, t.w, t.h, Gl.RGBA, Gl.UNSIGNED_BYTE, px);
        sandId = id;
        return sandTex;
    }

    private GTile flatTile(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        GTile g = flats.get(key);
        if (g != null) return g;
        float x0 = cx * Ground.TILE, z0 = cz * Ground.TILE;
        float[] pos = new float[17 * 17 * 3];
        int[] col = new int[17 * 17];
        for (int j = 0; j <= 16; j++)
            for (int i = 0; i <= 16; i++) {
                float x = x0 + i * 4f, z = z0 + j * 4f;
                int k = j * 17 + i;
                pos[k * 3] = x; pos[k * 3 + 1] = ground.height(x, z); pos[k * 3 + 2] = z;
                col[k] = ground.color(x, z);
            }
        g = meshTile(pos, col);
        flats.put(key, g);
        gpuBytes += g.bytes;
        while (flats.size() > 96) {
            Iterator<Map.Entry<Long, GTile>> it = flats.entrySet().iterator();
            GTile old = it.next().getValue();
            it.remove();
            freeTile(old);
        }
        return g;
    }

    private void freeTile(GTile g) {
        gl.deleteVertexArray(g.vao);
        gl.deleteBuffer(g.vbo);
        if (g.tex != 0) gl.deleteTexture(g.tex);
        if (g.water != 0) gl.deleteTexture(g.water);
        gpuBytes -= g.bytes;
    }

    /** This frame's wave field (as {@link SoftRenderer}): brightness and sun highlight, 16 m tiling. */
    private void updateWater() {
        final int wn = SoftRenderer.WN, wm = wn - 1;
        float t = time;
        Environment e0 = env;
        float wind = e0 == null ? 0.3f : Math.min(1f, 0.25f + e0.cloudCover * 0.5f + e0.rain * 0.6f);
        seaChop = 0.7f + wind * 0.9f;
        int ax = (int) (t * 9f), az = (int) (t * 5f), bx = (int) (-t * 6f), bz = (int) (t * 11f);
        int rx2 = (int) (t * 23f), rz2 = (int) (-t * 17f);
        float amp = seaChop;
        float lx = sunX, ly = Math.max(0.15f, sunY), lz = sunZ;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll; ly /= ll; lz /= ll;
        float vx = -fx, vy = 0.45f, vz = -fz;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl; vy /= vl; vz /= vl;
        float hx = lx + vx, hy2 = ly + vy, hz = lz + vz;
        float hl = (float) Math.sqrt(hx * hx + hy2 * hy2 + hz * hz);
        hx /= hl; hy2 /= hl; hz /= hl;
        float glint = e0 == null ? 1f : sunVis * (1f - e0.cloudCover * 0.7f);
        // the field only depends on its neighbours: build it once, then light it
        for (int y = 0; y < wn; y++)
            for (int x = 0; x < wn; x++) waveMod[y * wn + x] = SoftRenderer.field(x, y, ax, az, bx, bz, rx2, rz2);
        for (int y = 0; y < wn; y++)
            for (int x = 0; x < wn; x++) {
                float dX = waveMod[y * wn + ((x + 1) & wm)] - waveMod[y * wn + ((x - 1) & wm)];
                float dZ = waveMod[((y + 1) & wm) * wn + x] - waveMod[((y - 1) & wm) * wn + x];
                float nx = -dX * 2.2f * amp, nz = -dZ * 2.2f * amp, ny = 1f;
                float nl = 1f / (float) Math.sqrt(nx * nx + 1f + nz * nz);
                nx *= nl; ny *= nl; nz *= nl;
                float diff = nx * lx + ny * ly + nz * lz;
                int mod = (int) (256f * (0.72f + 0.42f * diff));
                float sp = nx * hx + ny * hy2 + nz * hz;
                int spec = 0;
                if (sp > 0.97f) {
                    float g = (sp - 0.97f) / 0.03f;
                    spec = (int) Math.min(255f, g * g * 255f * glint);
                }
                int i = (y * wn + x) * 2;
                waveBytes.put(i, (byte) Math.max(0, Math.min(255, mod >> 1)));
                waveBytes.put(i + 1, (byte) spec);
            }
        seaSkyR = (fogR * 3 + zenR) >> 2;
        seaSkyG = (fogG * 3 + zenG) >> 2;
        seaSkyB = (fogB * 3 + zenB) >> 2;
        scratch();
        gl.bindTexture(Gl.TEXTURE_2D, waveTex);
        waveBytes.position(0);
        gl.texSubImage2D(Gl.TEXTURE_2D, 0, 0, 0, wn, wn, Gl.RG, Gl.UNSIGNED_BYTE, waveBytes);
    }

    // ------------------------------------------------------------------ static batches

    private static final class Batch {
        int vao, vbo, ibo, lastUse;
        /** Each polygon's four corners (3 RGBA32F texels, 256 a row), for whole-polygon GBA spans. */
        int corners;
        final int[] first = new int[LAYERS], count = new int[LAYERS];
        long bytes;
        int polys;
    }
    private final IdentityHashMap<Geo, Batch> batches = new IdentityHashMap<>();
    private static final class Draw { Geo g; float ox, oy, oz; }
    private final ArrayList<Draw> draws = new ArrayList<>();
    private final ArrayList<Draw> drawPool = new ArrayList<>();
    private float scrollRate;

    @Override public void drawGeoOffset(Geo g, float ox, float oy, float oz) {
        if (g.count == 0 || !boxVisible(g.minX + ox, g.minY + oy, g.minZ + oz, g.maxX + ox, g.maxY + oy, g.maxZ + oz)) return;
        Draw d = draws.size() < drawPool.size() ? drawPool.get(draws.size()) : null;
        if (d == null) { d = new Draw(); drawPool.add(d); }
        d.g = g; d.ox = ox; d.oy = oy; d.oz = oz;
        draws.add(d);
    }

    private ByteBuffer uploadBuf = direct(1 << 16);
    private float[] upCorners = new float[4096 * 12];
    private IntBuffer indexBuf = direct(1 << 16).asIntBuffer();

    private Batch upload(Geo g) {
        int n = g.count;
        // polygons in layer order, so each layer is one range of the index buffer
        int[] perLayer = new int[LAYERS];
        for (int i = 0; i < n; i++) perLayer[g.layer(i)]++;
        int[] start = new int[LAYERS];
        for (int l = 1; l < LAYERS; l++) start[l] = start[l - 1] + perLayer[l - 1];
        int[] order = new int[n];
        int[] at = start.clone();
        for (int i = 0; i < n; i++) order[at[g.layer(i)]++] = i;
        int vbytes = n * 4 * STRIDE;
        if (uploadBuf.capacity() < vbytes) uploadBuf = direct(vbytes + (vbytes >> 2));
        if (indexBuf.capacity() < n * 6) indexBuf = direct((n * 6 + (n >> 1)) * 4).asIntBuffer();
        ByteBuffer vb = uploadBuf;
        IntBuffer ib = indexBuf;
        vb.clear();
        ib.clear();
        Batch b = new Batch();
        java.util.Arrays.fill(b.first, -1);
        int v = 0;
        for (int k = 0; k < n; k++) {
            int i = order[k];
            int l = g.layer(i);
            if (b.first[l] < 0) b.first[l] = ib.position();
            int id = g.tex[i];
            if (!atlas(id)) continue;
            Texture t = textures[id];
            int fl = g.flags(i);
            int flags = 0;
            boolean alpha = (fl & Geo.FLAG_ALPHA) != 0, blend = (fl & Geo.FLAG_BLEND) != 0 && g.alpha != null;
            if (blend) flags |= 2 | 16;
            else if (alpha) flags |= 1;
            else flags |= 16;
            if ((fl & Geo.FLAG_DETAIL) != 0) flags |= 4;
            if (t.scrollU != 0f) { flags |= 8; scrollRate = t.scrollU; }
            int nv = g.vertexCount(i);
            if (upCorners.length < (b.polys + 1) * 12) upCorners = java.util.Arrays.copyOf(upCorners, Math.max(upCorners.length * 2, (b.polys + 1) * 12));
            for (int c = 0; c < 4; c++) {
                int cc = Math.min(c, nv - 1);
                upCorners[b.polys * 12 + c * 3] = g.pos[i * 12 + cc * 3];
                upCorners[b.polys * 12 + c * 3 + 1] = g.pos[i * 12 + cc * 3 + 1];
                upCorners[b.polys * 12 + c * 3 + 2] = g.pos[i * 12 + cc * 3 + 2];
            }
            byte nx = (byte) Math.round(g.nrm[i * 3] * 127f), ny = (byte) Math.round(g.nrm[i * 3 + 1] * 127f), nz = (byte) Math.round(g.nrm[i * 3 + 2] * 127f);
            for (int c = 0; c < 4; c++) {
                vb.putFloat(g.pos[i * 12 + c * 3]).putFloat(g.pos[i * 12 + c * 3 + 1]).putFloat(g.pos[i * 12 + c * 3 + 2]);
                vb.putFloat(g.uv[i * 8 + c * 2]).putFloat(g.uv[i * 8 + c * 2 + 1]);
                float cov = blend ? g.alpha[i * 4 + c] : 1f;
                vb.putShort(unorm16(g.light[i * 4 + c] * 0.25f)).putShort(unorm16(cov)).putShort(unorm16(l == L_GROUND ? 0.235f : 0.25f)).putShort((short) 0);
                vb.put(nx).put(ny).put(nz).put((byte) 0);
                vb.putShort((short) id).putShort((short) flags);
            }
            ib.put(v).put(v + 1).put(v + 2);
            if (nv == 4) ib.put(v).put(v + 2).put(v + 3);
            v += 4;
            b.polys++;
            b.count[l] = ib.position() - b.first[l];
        }
        for (int l = 0; l < LAYERS; l++) if (b.first[l] < 0) b.first[l] = 0;
        int indices = ib.position();
        vb.flip();
        ib.flip();
        b.vao = gl.genVertexArray();
        gl.bindVertexArray(b.vao);
        b.vbo = gl.genBuffer();
        gl.bindBuffer(Gl.ARRAY_BUFFER, b.vbo);
        gl.bufferData(Gl.ARRAY_BUFFER, vb.limit(), vb, Gl.STATIC_DRAW);
        b.ibo = gl.genBuffer();
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, b.ibo);
        gl.bufferData(Gl.ELEMENT_ARRAY_BUFFER, indices * 4, ib, Gl.STATIC_DRAW);
        attributes();
        gl.bindVertexArray(0);
        b.bytes = vb.limit() + indices * 4L;
        // the corners, for GBA scanline spans across whole polygons (clipped to the near plane)
        int rows = Math.max(1, (b.polys + 255) >> 8);
        FloatBuffer cb = direct(rows * 256 * 12 * 4).asFloatBuffer();
        cb.put(upCorners, 0, b.polys * 12);
        cb.position(0);
        gl.activeTexture(Gl.TEXTURE0 + 3);
        b.corners = newTexture(Gl.NEAREST, Gl.CLAMP_TO_EDGE);
        gl.texImage2D(Gl.TEXTURE_2D, 0, RGBA32F, 768, rows, Gl.RGBA, Gl.FLOAT, cb);
        gl.activeTexture(Gl.TEXTURE0);
        b.bytes += rows * 256L * 48;
        gpuBytes += b.bytes;
        statUploads += b.polys;
        return b;
    }

    private void attributes() {
        gl.enableVertexAttribArray(0);
        gl.vertexAttribPointer(0, 3, Gl.FLOAT, false, STRIDE, 0);
        gl.enableVertexAttribArray(1);
        gl.vertexAttribPointer(1, 2, Gl.FLOAT, false, STRIDE, 12);
        gl.enableVertexAttribArray(2);
        gl.vertexAttribPointer(2, 4, Gl.UNSIGNED_SHORT, true, STRIDE, 20);
        gl.enableVertexAttribArray(3);
        gl.vertexAttribPointer(3, 4, Gl.BYTE, true, STRIDE, 28);
        gl.enableVertexAttribArray(4);
        gl.vertexAttribIPointer(4, 2, Gl.UNSIGNED_SHORT, STRIDE, 32);
    }

    private static short unorm16(float v) {
        return (short) Math.max(0, Math.min(65535, Math.round(v * 65535f)));
    }

    private void freeBatch(Batch b) {
        if (b.corners != 0) gl.deleteTexture(b.corners);
        gl.deleteVertexArray(b.vao);
        gl.deleteBuffer(b.vbo);
        gl.deleteBuffer(b.ibo);
        gpuBytes -= b.bytes;
    }

    // ------------------------------------------------------------------ dynamic quads and sprites

    private static final int MAX_DYN = 16384;
    private final float[] dynPos = new float[MAX_DYN * 12], dynUv = new float[MAX_DYN * 8], dynLit = new float[MAX_DYN * 3];
    private final int[] dynTex = new int[MAX_DYN], dynLayer = new int[MAX_DYN], dynFlags = new int[MAX_DYN];
    private final long[] dynOrder = new long[MAX_DYN];
    private int dynCount;
    private int dynVbo, dynIbo, dynVao, dynIboQuads;
    private ByteBuffer dynBuf = direct(1024 * 4 * STRIDE);

    private int addQuad(int texId, int layer, int flags) {
        if (dynCount >= MAX_DYN || texId < 0 || texId >= textures.length || textures[texId] == null) return -1;
        int q = dynCount++;
        dynTex[q] = texId;
        dynLayer[q] = layer;
        dynFlags[q] = flags;
        return q;
    }

    @Override public void billboard(int texId, float x, float y, float z, float left, float right, float bottom, float top,
                                    float u0, float v0, float u1, float v1, float bias) {
        float dx = x - ex, dz = z - ez;
        float vz = dx * fx + dz * fz;
        if (vz < near || vz > far) return;
        int q = addQuad(texId, L_OBJECT, 1);
        if (q < 0) return;
        Texture t = textures[texId];
        float iw = 1f / t.w, ih = 1f / t.h;
        // corners: bottom-left, bottom-right, top-right, top-left
        float[] p = dynPos;
        int o = q * 12;
        p[o] = x + rx * left; p[o + 1] = y + bottom; p[o + 2] = z + rz * left;
        p[o + 3] = x + rx * right; p[o + 4] = y + bottom; p[o + 5] = z + rz * right;
        p[o + 6] = x + rx * right; p[o + 7] = y + top; p[o + 8] = z + rz * right;
        p[o + 9] = x + rx * left; p[o + 10] = y + top; p[o + 11] = z + rz * left;
        // texel centres inset a hair so the rows and columns at the frame edges stay inside it
        float[] uv = dynUv;
        int u = q * 8;
        uv[u] = u0 * iw; uv[u + 1] = (v1 - 0.01f) * ih;
        uv[u + 2] = (u1 - 0.01f) * iw; uv[u + 3] = (v1 - 0.01f) * ih;
        uv[u + 4] = (u1 - 0.01f) * iw; uv[u + 5] = v0 * ih;
        uv[u + 6] = u0 * iw; uv[u + 7] = v0 * ih;
        dynLit[q * 3] = 1f;
        dynLit[q * 3 + 1] = 1f;
        dynLit[q * 3 + 2] = Math.max(0.3f, Math.min(3.9f, vz / Math.max(near, vz + bias)));
        statPolys++;
    }

    @Override public void spriteQuad(int texId, float[] corners, float[] uvTexels, float bias) {
        int q = addQuad(texId, L_OBJECT, 1);
        if (q < 0) return;
        Texture t = textures[texId];
        System.arraycopy(corners, 0, dynPos, q * 12, 12);
        for (int k = 0; k < 4; k++) {
            dynUv[q * 8 + k * 2] = uvTexels[k * 2] / t.w;
            dynUv[q * 8 + k * 2 + 1] = uvTexels[k * 2 + 1] / t.h;
        }
        float cxw = (corners[0] + corners[6]) * 0.5f, czw = (corners[2] + corners[8]) * 0.5f;
        float vz = (cxw - ex) * fx + (czw - ez) * fz;
        dynLit[q * 3] = 1f;
        dynLit[q * 3 + 1] = 1f;
        dynLit[q * 3 + 2] = vz > near ? Math.max(0.3f, Math.min(3.9f, vz / Math.max(near, vz + bias))) : 1f;
        statPolys++;
    }

    @Override public void groundQuad(int texId, float x, float y, float z, float r, int mode, int layer, float bias) {
        int q = addQuad(texId, layer, modeFlags(mode));
        if (q < 0) return;
        float[] p = dynPos;
        int o = q * 12;
        p[o] = x - r; p[o + 1] = y; p[o + 2] = z + r;
        p[o + 3] = x + r; p[o + 4] = y; p[o + 5] = z + r;
        p[o + 6] = x + r; p[o + 7] = y; p[o + 8] = z - r;
        p[o + 9] = x - r; p[o + 10] = y; p[o + 11] = z - r;
        quadUv(q);
    }

    @Override public void groundQuadRotated(int texId, float x, float y, float z, float halfLen, float halfWid, float yaw, int mode, int layer) {
        int q = addQuad(texId, layer, modeFlags(mode));
        if (q < 0) return;
        float sx = (float) Math.sin(yaw), sz = (float) Math.cos(yaw);
        float qx = sz, qz = -sx;
        float[] p = dynPos;
        int o = q * 12;
        p[o] = x - sx * halfLen - qx * halfWid; p[o + 1] = y; p[o + 2] = z - sz * halfLen - qz * halfWid;
        p[o + 3] = x - sx * halfLen + qx * halfWid; p[o + 4] = y; p[o + 5] = z - sz * halfLen + qz * halfWid;
        p[o + 6] = x + sx * halfLen + qx * halfWid; p[o + 7] = y; p[o + 8] = z + sz * halfLen + qz * halfWid;
        p[o + 9] = x + sx * halfLen - qx * halfWid; p[o + 10] = y; p[o + 11] = z + sz * halfLen - qz * halfWid;
        quadUv(q);
    }

    private void quadUv(int q) {
        float[] uv = dynUv;
        int u = q * 8;
        uv[u] = 0; uv[u + 1] = 0.999f; uv[u + 2] = 0.999f; uv[u + 3] = 0.999f;
        uv[u + 4] = 0.999f; uv[u + 5] = 0; uv[u + 6] = 0; uv[u + 7] = 0;
        dynLit[q * 3] = 1f;
        dynLit[q * 3 + 1] = 1f;
        dynLit[q * 3 + 2] = 1f;
        statPolys++;
    }

    private static int modeFlags(int mode) {
        switch (mode) {
            case MODE_ALPHA: return 1;
            case MODE_SHADOW: return 32;
            case MODE_BLEND: return 2 | 16;
            default: return 16;
        }
    }

    /** Writes this frame's quads grouped by layer and texture; returns the group boundaries. */
    private int writeDynamic() {
        int n = dynCount;
        for (int q = 0; q < n; q++)
            dynOrder[q] = ((long) dynLayer[q] << 56) | ((long) (dynFlags[q] & 32) << 48) | ((long) dynTex[q] << 20) | q;
        java.util.Arrays.sort(dynOrder, 0, n);
        int bytes = n * 4 * STRIDE;
        if (dynBuf.capacity() < bytes) dynBuf = direct(bytes * 2);
        ByteBuffer vb = dynBuf;
        vb.clear();
        for (int k = 0; k < n; k++) {
            int q = (int) (dynOrder[k] & 0xFFFFF);
            int id = dynTex[q];
            for (int c = 0; c < 4; c++) {
                vb.putFloat(dynPos[q * 12 + c * 3]).putFloat(dynPos[q * 12 + c * 3 + 1]).putFloat(dynPos[q * 12 + c * 3 + 2]);
                vb.putFloat(dynUv[q * 8 + c * 2]).putFloat(dynUv[q * 8 + c * 2 + 1]);
                vb.putShort(unorm16(dynLit[q * 3] * 0.25f)).putShort(unorm16(dynLit[q * 3 + 1]))
                        .putShort(unorm16(dynLit[q * 3 + 2] * 0.25f)).putShort((short) 0);
                vb.putInt(0);
                vb.putShort((short) id).putShort((short) dynFlags[q]);
            }
        }
        vb.flip();
        gl.bindVertexArray(dynVao);
        gl.bindBuffer(Gl.ARRAY_BUFFER, dynVbo);
        gl.bufferData(Gl.ARRAY_BUFFER, Math.max(4, vb.limit()), vb, Gl.STREAM_DRAW);
        quadIndices(n);
        gl.bindVertexArray(dynVao);
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, dynIbo);
        attributes();
        gl.bindVertexArray(0);
        return n;
    }

    /** Grows the shared quad index buffer (0 1 2, 0 2 3 per quad) to at least n quads. */
    private void quadIndices(int n) {
        if (dynIboQuads >= n) return;
        int quads = Math.max(1024, Integer.highestOneBit(Math.max(1, n)) << 1);
        IntBuffer ib = direct(quads * 6 * 4).asIntBuffer();
        for (int q = 0; q < quads; q++) {
            int v = q * 4;
            ib.put(v).put(v + 1).put(v + 2).put(v).put(v + 2).put(v + 3);
        }
        ib.flip();
        gl.bindVertexArray(0);
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, dynIbo);
        gl.bufferData(Gl.ELEMENT_ARRAY_BUFFER, quads * 6 * 4, ib, Gl.STATIC_DRAW);
        dynIboQuads = quads;
    }

    /** Draws this frame's quads of one layer (they are sorted by layer, then texture). */
    private int drawDynamic(int from, int layer) {
        int n = dynCount, k = from;
        while (k < n && (int) (dynOrder[k] >>> 56) == layer) {
            int q = (int) (dynOrder[k] & 0xFFFFF);
            int id = dynTex[q];
            int flags = dynFlags[q];
            int end = k + 1;
            while (end < n && (int) (dynOrder[end] >>> 56) == layer && dynTex[(int) (dynOrder[end] & 0xFFFFF)] == id
                    && dynFlags[(int) (dynOrder[end] & 0xFFFFF)] == flags) end++;
            boolean inAtlas = atlas(id);
            gl.uniform1i(poly.loc("uSingle"), inAtlas ? 0 : 1);
            int one = inAtlas ? 0 : single(textures[id]);
            gl.activeTexture(Gl.TEXTURE0);
            gl.bindTexture(Gl.TEXTURE_2D_ARRAY, atlasTex);       // (it may have grown)
            if (!inAtlas) {
                gl.activeTexture(Gl.TEXTURE0 + 2);
                gl.bindTexture(Gl.TEXTURE_2D, one);
            }
            boolean shadow = (flags & 32) != 0;
            if (shadow) {
                gl.enable(Gl.BLEND);
                gl.blendFunc(Gl.DST_COLOR, Gl.ZERO);
            }
            gl.bindVertexArray(dynVao);
            gl.drawElements(Gl.TRIANGLES, (end - k) * 6, Gl.UNSIGNED_INT, k * 6 * 4);
            if (shadow) gl.disable(Gl.BLEND);
            k = end;
        }
        return k;
    }

    // ------------------------------------------------------------------ ordering table

    private static final boolean LE = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int VI = STRIDE / 4;                 // ints per vertex
    private long[] otKeys = new long[16384];
    private int[] otDraw = new int[16384], otPoly = new int[16384];
    private int otCount, otQuads;
    private int[] otStage = new int[16384 * 4 * VI];
    private int[] runStart = new int[256], runTex = new int[256];
    private boolean[] runShadow = new boolean[256];
    private int runCount;
    private int otVbo, otVao;
    private ByteBuffer otBuf = direct(16384 * 4 * STRIDE);
    /** Each ordered polygon's four corners (world), 3 RGBA32F texels each, 256 polygons a row. */
    private float[] otCorners = new float[16384 * 12];
    private ByteBuffer cornerBuf = direct(16384 * 12 * 4);
    private int cornerTex;

    private static int two(int lo, int hi) {
        return LE ? (lo & 0xFFFF) | (hi << 16) : (hi & 0xFFFF) | (lo << 16);
    }

    private static int four(int a, int b, int c, int d) {
        return LE ? (a & 255) | (b & 255) << 8 | (c & 255) << 16 | d << 24
                  : (d & 255) | (c & 255) << 8 | (b & 255) << 16 | a << 24;
    }

    private void otAdd(float depth, int draw, int poly) {
        if (otCount == otKeys.length) {
            int n = otCount * 2;
            otKeys = java.util.Arrays.copyOf(otKeys, n);
            otDraw = java.util.Arrays.copyOf(otDraw, n);
            otPoly = java.util.Arrays.copyOf(otPoly, n);
        }
        int k = otCount++;
        otKeys[k] = ((long) ~Float.floatToIntBits(Math.max(0f, depth)) << 32) | (k & 0xffffffffL);
        otDraw[k] = draw;
        otPoly[k] = poly;
    }

    /**
     * Fills the ordering table with this frame's object polygons (from the batches' own polygon
     * lists, so nothing waits for an upload) and object-layer sprites, sorts it far to near, and
     * streams the vertices in that order. Runs split only where the texture page or blend changes.
     */
    private void buildOrderingTable() {
        otCount = 0;
        float far2 = far * far, detail2 = detailDist * detailDist;
        for (int di = 0; di < draws.size(); di++) {
            Draw d = draws.get(di);
            Geo g = d.g;
            float ox = d.ox, oy = d.oy, oz = d.oz;
            for (int i = 0; i < g.count; i++) {
                if (g.layer(i) != L_OBJECT) continue;
                int o = i * 12;
                float p0x = g.pos[o] + ox, p0y = g.pos[o + 1] + oy, p0z = g.pos[o + 2] + oz;
                float nx = g.nrm[i * 3], ny = g.nrm[i * 3 + 1], nz = g.nrm[i * 3 + 2];
                // no depth buffer between polygons, so back faces must go before they are painted
                if ((nx != 0f || ny != 0f || nz != 0f) && nx * (ex - p0x) + ny * (ey - p0y) + nz * (ez - p0z) <= 0f) continue;
                float dx = p0x - ex, dz = p0z - ez, d2 = dx * dx + dz * dz;
                if (d2 > far2 * 1.6f) continue;
                int fl = g.flags(i);
                if ((fl & Geo.FLAG_DETAIL) != 0 && d2 > detail2) continue;
                int nv = g.vertexCount(i);
                float zsum = 0f, zmax = -Float.MAX_VALUE;
                boolean allNear = true, allFar = true;
                for (int c = 0; c < nv; c++) {
                    float vz = (g.pos[o + c * 3] + ox - ex) * fx + (g.pos[o + c * 3 + 1] + oy - ey) * fy + (g.pos[o + c * 3 + 2] + oz - ez) * fz;
                    zsum += vz;
                    if (vz > zmax) zmax = vz;
                    if (vz >= near) allNear = false;
                    if (vz <= far) allFar = false;
                }
                if (allNear || allFar) continue;
                otAdd(((fl & Geo.FLAG_SORT_FAR) != 0 ? zmax : zsum / nv) + g.bias[i], di, i);
            }
        }
        for (int q = 0; q < dynCount; q++) {
            if (dynLayer[q] != L_OBJECT) continue;
            int o = q * 12;
            float vz = 0f;
            for (int c = 0; c < 4; c++) vz += (dynPos[o + c * 3] - ex) * fx + (dynPos[o + c * 3 + 1] - ey) * fy + (dynPos[o + c * 3 + 2] - ez) * fz;
            vz *= 0.25f;
            // a sprite's depth bias rides in its light's third channel as vz / (vz + bias)
            float k = dynLit[q * 3 + 2];
            otAdd(k > 0f && k != 1f ? vz / k : vz, -1, q);
        }
        java.util.Arrays.sort(otKeys, 0, otCount);
        statPrims += otCount;

        // vertices in painting order
        if (otStage.length < otCount * 4 * VI) otStage = new int[otCount * 4 * VI + 4096 * VI];
        int[] s = otStage;
        int w = 0;
        runCount = 0;
        int curTex = Integer.MIN_VALUE;
        boolean curShadow = false;
        int lo16 = unorm16(0.25f);
        for (int n = 0; n < otCount; n++) {
            int k = (int) otKeys[n];
            int di = otDraw[k], i = otPoly[k];
            int run;
            boolean shadow = false;
            int base = w * 4 * VI;
            if (di >= 0) {
                Draw d = draws.get(di);
                Geo g = d.g;
                int id = g.tex[i];
                if (!atlas(id)) continue;
                Texture t = textures[id];
                int fl = g.flags(i);
                boolean alpha = (fl & Geo.FLAG_ALPHA) != 0, blend = (fl & Geo.FLAG_BLEND) != 0 && g.alpha != null;
                int flags = blend ? 2 | 16 : (alpha ? 1 : 16);
                if ((fl & Geo.FLAG_DETAIL) != 0) flags |= 4;
                if (t.scrollU != 0f) { flags |= 8; scrollRate = t.scrollU; }
                int nv = g.vertexCount(i);
                int nrm = four(Math.round(g.nrm[i * 3] * 127f), Math.round(g.nrm[i * 3 + 1] * 127f), Math.round(g.nrm[i * 3 + 2] * 127f), 0);
                int texFlags = two(id, flags);
                if (otCorners.length < (w + 1) * 12) otCorners = java.util.Arrays.copyOf(otCorners, otCorners.length * 2);
                for (int c = 0; c < 4; c++) {
                    int cc = Math.min(c, nv - 1);
                    otCorners[w * 12 + c * 3] = g.pos[i * 12 + cc * 3] + d.ox;
                    otCorners[w * 12 + c * 3 + 1] = g.pos[i * 12 + cc * 3 + 1] + d.oy;
                    otCorners[w * 12 + c * 3 + 2] = g.pos[i * 12 + cc * 3 + 2] + d.oz;
                }
                for (int c = 0; c < 4; c++) {
                    int cc = Math.min(c, nv - 1), o = base + c * VI;
                    s[o] = Float.floatToRawIntBits(g.pos[i * 12 + cc * 3] + d.ox);
                    s[o + 1] = Float.floatToRawIntBits(g.pos[i * 12 + cc * 3 + 1] + d.oy);
                    s[o + 2] = Float.floatToRawIntBits(g.pos[i * 12 + cc * 3 + 2] + d.oz);
                    s[o + 3] = Float.floatToRawIntBits(g.uv[i * 8 + cc * 2]);
                    s[o + 4] = Float.floatToRawIntBits(g.uv[i * 8 + cc * 2 + 1]);
                    s[o + 5] = two(unorm16(g.light[i * 4 + cc] * 0.25f), unorm16(blend ? g.alpha[i * 4 + cc] : 1f));
                    s[o + 6] = two(lo16, 0);
                    s[o + 7] = nrm;
                    s[o + 8] = texFlags;
                }
                run = -1;
            } else {
                int q = i, id = dynTex[q];
                boolean inAtlas = atlas(id);
                int flags = dynFlags[q];
                shadow = (flags & 32) != 0;
                int texFlags = two(id, flags);
                if (otCorners.length < (w + 1) * 12) otCorners = java.util.Arrays.copyOf(otCorners, otCorners.length * 2);
                System.arraycopy(dynPos, q * 12, otCorners, w * 12, 12);
                for (int c = 0; c < 4; c++) {
                    int o = base + c * VI;
                    s[o] = Float.floatToRawIntBits(dynPos[q * 12 + c * 3]);
                    s[o + 1] = Float.floatToRawIntBits(dynPos[q * 12 + c * 3 + 1]);
                    s[o + 2] = Float.floatToRawIntBits(dynPos[q * 12 + c * 3 + 2]);
                    s[o + 3] = Float.floatToRawIntBits(dynUv[q * 8 + c * 2]);
                    s[o + 4] = Float.floatToRawIntBits(dynUv[q * 8 + c * 2 + 1]);
                    s[o + 5] = two(unorm16(dynLit[q * 3] * 0.25f), unorm16(dynLit[q * 3 + 1]));
                    s[o + 6] = two(unorm16(dynLit[q * 3 + 2] * 0.25f), 0);
                    s[o + 7] = 0;
                    s[o + 8] = texFlags;
                }
                run = inAtlas ? -1 : id;
            }
            if (run != curTex || shadow != curShadow || runCount == 0) {
                if (runCount == runStart.length) {
                    runStart = java.util.Arrays.copyOf(runStart, runCount * 2);
                    runTex = java.util.Arrays.copyOf(runTex, runCount * 2);
                    runShadow = java.util.Arrays.copyOf(runShadow, runCount * 2);
                }
                runStart[runCount] = w;
                runTex[runCount] = run;
                runShadow[runCount] = shadow;
                runCount++;
                curTex = run;
                curShadow = shadow;
            }
            w++;
        }
        otQuads = w;
        int bytes = w * 4 * STRIDE;
        if (otBuf.capacity() < bytes) otBuf = direct(bytes + (bytes >> 1));
        otBuf.clear();
        otBuf.asIntBuffer().put(s, 0, w * 4 * VI);
        otBuf.limit(bytes);
        if (otVao == 0) {
            otVao = gl.genVertexArray();
            otVbo = gl.genBuffer();
        }
        // the corners, for whole-polygon scanline spans (GBA texture mapping)
        int rows = Math.max(1, (w + 255) >> 8);
        int floats = rows * 256 * 12;
        if (cornerBuf.capacity() < floats * 4) cornerBuf = direct(floats * 4 + (floats << 1));
        cornerBuf.clear();
        cornerBuf.asFloatBuffer().put(otCorners, 0, w * 12);
        cornerBuf.limit(floats * 4);
        gl.activeTexture(Gl.TEXTURE0 + 3);
        if (cornerTex == 0) cornerTex = newTexture(Gl.NEAREST, Gl.CLAMP_TO_EDGE);
        gl.bindTexture(Gl.TEXTURE_2D, cornerTex);
        gl.texImage2D(Gl.TEXTURE_2D, 0, RGBA32F, 768, rows, Gl.RGBA, Gl.FLOAT, cornerBuf);
        gl.activeTexture(Gl.TEXTURE0);
        quadIndices(w);
        gl.bindVertexArray(otVao);
        gl.bindBuffer(Gl.ARRAY_BUFFER, otVbo);
        gl.bufferData(Gl.ARRAY_BUFFER, Math.max(4, bytes), otBuf, Gl.STREAM_DRAW);
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, dynIbo);
        attributes();
        gl.bindVertexArray(0);
    }

    /** Paints the ordering table: far to near, tested against the ground's depth only. */
    private void drawOrderingTable(Program p) {
        gl.depthMask(false);
        gl.uniform3f(p.loc("uOffset"), 0f, 0f, 0f);
        gl.uniform1i(p.loc("uCorners"), 1);
        gl.activeTexture(Gl.TEXTURE0 + 3);
        gl.bindTexture(Gl.TEXTURE_2D, cornerTex);
        gl.bindVertexArray(otVao);
        for (int r = 0; r < runCount; r++) {
            int from = runStart[r], to = r + 1 < runCount ? runStart[r + 1] : otQuads;
            if (to <= from) continue;
            int id = runTex[r];
            gl.uniform1i(p.loc("uSingle"), id < 0 ? 0 : 1);
            gl.activeTexture(Gl.TEXTURE0);
            gl.bindTexture(Gl.TEXTURE_2D_ARRAY, atlasTex);
            if (id >= 0) {
                gl.activeTexture(Gl.TEXTURE0 + 2);
                gl.bindTexture(Gl.TEXTURE_2D, single(textures[id]));
            }
            if (runShadow[r]) {
                gl.enable(Gl.BLEND);
                gl.blendFunc(Gl.DST_COLOR, Gl.ZERO);
            }
            gl.drawElements(Gl.TRIANGLES, (to - from) * 6, Gl.UNSIGNED_INT, from * 6 * 4);
            if (runShadow[r]) gl.disable(Gl.BLEND);
        }
        gl.uniform1i(p.loc("uSingle"), 0);
        gl.uniform1i(p.loc("uCorners"), 0);
    }

    // ------------------------------------------------------------------ flush

    @Override public void flush() {
        // stream in the batches seen for the first time (a budget per frame keeps it smooth)
        int budget = uploadBudget;
        for (Draw d : draws) {
            Batch b = batches.get(d.g);
            if (b == null && budget > 0) {
                b = upload(d.g);
                batches.put(d.g, b);
                budget -= Math.max(1, b.polys);
            }
            if (b != null) b.lastUse = frame;
        }
        int nDyn = writeDynamic();
        if (psxSort) buildOrderingTable();
        Program p = poly;
        gl.useProgram(p.id);
        camera(p);
        gl.uniform3f(p.loc("uTint"), tR / 256f, tG / 256f, tB / 256f);
        gl.uniform3f(p.loc("uFog"), fogR, fogG, fogB);
        gl.uniform2f(p.loc("uFogRange"), fogStart, 1f / (fogEnd - fogStart));
        gl.uniform1f(p.loc("uDetail"), detailDist);
        gl.uniform1i(p.loc("uTexMap"), texMap);
        gl.uniform1i(p.loc("uCorners"), 0);
        gl.uniform1i(p.loc("uCornerTex"), 3);
        gl.uniform1f(p.loc("uNear"), near);
        gl.uniform1f(p.loc("uScroll"), scrollRate * time);
        gl.uniform1i(p.loc("uAtlas"), 0);
        gl.uniform1i(p.loc("uInfo"), 1);
        gl.uniform1i(p.loc("uTex"), 2);
        gl.activeTexture(Gl.TEXTURE0);
        gl.bindTexture(Gl.TEXTURE_2D_ARRAY, atlasTex);
        gl.activeTexture(Gl.TEXTURE0 + 1);
        gl.bindTexture(Gl.TEXTURE_2D, infoTex);
        gl.enable(Gl.DEPTH_TEST);
        gl.depthFunc(Gl.LEQUAL);
        int dyn = 0;
        for (int layer = 0; layer < LAYERS; layer++) {
            // flat layers lie on the ground in layer order; objects and sprites use the depth buffer
            if (psxSort && layer == L_OBJECT) {
                drawOrderingTable(p);
                continue;
            }
            // the terrain tiles are the ground: a depth buffer among themselves, and what everything above tests
            gl.depthMask(layer == L_OBJECT || layer == L_GROUND);
            gl.uniform1i(p.loc("uSingle"), 0);
            gl.uniform1i(p.loc("uCorners"), 1);
            gl.activeTexture(Gl.TEXTURE0 + 3);
            for (Draw d : draws) {
                Batch b = batches.get(d.g);
                if (b == null || b.count[layer] == 0) continue;
                gl.uniform3f(p.loc("uOffset"), d.ox, d.oy, d.oz);
                gl.bindTexture(Gl.TEXTURE_2D, b.corners);
                gl.bindVertexArray(b.vao);
                gl.drawElements(Gl.TRIANGLES, b.count[layer], Gl.UNSIGNED_INT, b.first[layer] * 4);
                statPrims += b.count[layer] / 3;
                if (layer == L_OBJECT) statBatches++;
            }
            gl.activeTexture(Gl.TEXTURE0);
            gl.uniform1i(p.loc("uCorners"), 0);
            if (nDyn > 0) {
                gl.uniform3f(p.loc("uOffset"), 0f, 0f, 0f);
                dyn = drawDynamic(dyn, layer);
            }
        }
        for (Draw d : draws) statPolys += d.g.count;
        gl.depthMask(true);
        gl.bindVertexArray(0);
        gl.activeTexture(Gl.TEXTURE0);
        if (frame % 30 == 0) sweep();
    }

    /** Frees what has not been drawn for a while: chunks streamed out, sprites no longer used. */
    private void sweep() {
        Iterator<Map.Entry<Geo, Batch>> it = batches.entrySet().iterator();
        long batchBytes = 0;
        while (it.hasNext()) {
            Batch b = it.next().getValue();
            if (frame - b.lastUse > 90) { freeBatch(b); it.remove(); }
            else batchBytes += b.bytes;
        }
        if (batchBytes > BATCH_BUDGET) {
            // over budget: the least recently drawn go first
            List<Map.Entry<Geo, Batch>> all = new ArrayList<>(batches.entrySet());
            all.sort((a, b) -> Integer.compare(a.getValue().lastUse, b.getValue().lastUse));
            for (Map.Entry<Geo, Batch> e : all) {
                if (batchBytes <= BATCH_BUDGET * 3 / 4 || e.getValue().lastUse >= frame - 1) break;
                batchBytes -= e.getValue().bytes;
                freeBatch(e.getValue());
                batches.remove(e.getKey());
            }
        }
        Iterator<Map.Entry<Ground.Tile, GTile>> ti = tiles.entrySet().iterator();
        while (ti.hasNext()) {
            GTile g = ti.next().getValue();
            if (frame - g.lastUse > 90) { freeTile(g); ti.remove(); }
        }
        Iterator<Map.Entry<Texture, Single>> si = singles.entrySet().iterator();
        while (si.hasNext()) {
            Map.Entry<Texture, Single> e = si.next();
            if (frame - e.getValue().lastUse > 120) {
                gl.deleteTexture(e.getValue().tex);
                gpuBytes -= (long) e.getKey().w * e.getKey().h * 4;
                si.remove();
            }
        }
    }

    // ------------------------------------------------------------------ weather

    private int flatVbo, flatVao, flatCount;
    private ByteBuffer flatBuf = direct(4096 * 12 * 6);

    private void flatRect(float x0, float y0, float x1, float y1, int argb) {
        if (flatBuf.remaining() < 6 * 12) {
            ByteBuffer b = direct(flatBuf.capacity() * 2);
            flatBuf.flip();
            b.put(flatBuf);
            flatBuf = b;
        }
        float[][] v = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y0}, {x1, y1}, {x0, y1}};
        for (float[] c : v) {
            flatBuf.putFloat(c[0]).putFloat(c[1]);
            flatBuf.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
        }
        flatCount += 6;
    }

    private void drawFlat(boolean blend) {
        if (flatCount == 0) return;
        flatBuf.flip();
        gl.useProgram(flat.id);
        gl.uniform2f(flat.loc("uRes"), W, H);
        gl.bindVertexArray(flatVao);
        gl.bindBuffer(Gl.ARRAY_BUFFER, flatVbo);
        gl.bufferData(Gl.ARRAY_BUFFER, flatBuf.limit(), flatBuf, Gl.STREAM_DRAW);
        if (blend) {
            gl.enable(Gl.BLEND);
            gl.blendFunc(Gl.SRC_ALPHA, Gl.ONE_MINUS_SRC_ALPHA);
        }
        gl.drawArrays(Gl.TRIANGLES, 0, flatCount);
        gl.disable(Gl.BLEND);
        gl.bindVertexArray(0);
        flatBuf.clear();
        flatCount = 0;
    }

    /** The lightning bolt, drawn into the sky (the ground and buildings then cover it). */
    private void lightning() {
        Environment e0 = env;
        if (e0 == null || e0.boltTime <= 0f) return;
        java.util.Random br = new java.util.Random(e0.boltSeed);
        float bx = W * (0.15f + br.nextFloat() * 0.7f);
        int yEnd = (int) Math.min(H - 1, hy + 4);
        flatBuf.clear();
        flatCount = 0;
        for (int y = 0; y < yEnd; y++) {
            if ((y & 3) == 0) bx += (br.nextFloat() - 0.5f) * 6f;
            int x = (int) bx;
            flatRect(x - 1, y, x, y + 1, 0xFFB0B8E0);
            flatRect(x, y, x + 1, y + 1, 0xFFF4F6FF);
            flatRect(x + 1, y, x + 2, y + 1, 0xFFB0B8E0);
        }
        drawFlat(false);
    }

    @Override public void weatherOverlay() {
        Environment e0 = env;
        if (e0 == null) return;
        float rain = e0.rain;
        if (rain <= 0.02f) return;
        int n = (int) (rain * 420f);
        float t = time;
        int slant = e0.windX > 0 ? 1 : -1;
        flatBuf.clear();
        flatCount = 0;
        gl.disable(Gl.DEPTH_TEST);
        for (int i = 0; i < n; i++) {
            int h1 = com.harlequinxd.drivehard.core.tex.Noise.hash(i, 1, 5), h2 = com.harlequinxd.drivehard.core.tex.Noise.hash(i, 2, 5);
            float speed = 260f + (h2 & 127);
            int len = 4 + (h1 >>> 28);
            int y0 = (int) (((h2 >>> 8) & 1023) + t * speed) % (H + 24) - 12;
            int x0 = ((h1 & 0xFFFF) * W) >> 16;
            x0 = (int) ((x0 - slant * t * 40f) % W);
            if (x0 < 0) x0 += W;
            // the streak in runs of the same column
            int runStart = 0, runX = x0 + slant * (y0 / 4) % 3;
            for (int j = 1; j <= len; j++) {
                int x = j < len ? x0 + slant * ((y0 + j) / 4) % 3 : Integer.MIN_VALUE;
                if (x != runX) {
                    flatRect(runX, y0 + runStart, runX + 1, y0 + j, 0x60C8D0DE);
                    runStart = j;
                    runX = x;
                }
            }
        }
        drawFlat(true);
        gl.enable(Gl.DEPTH_TEST);
    }

    // ------------------------------------------------------------------ on screen

    private IntBuffer overlayBuf;
    /** Where {@link #present} draws: 0 is the screen (tools may point it at an off-screen target). */
    public int screenFramebuffer = 0;

    /**
     * Shows the finished frame scaled (unfiltered) into the screen rectangle (x, y, w, h; top-left
     * origin) and the HUD over it: {@code overlay} is W x H premultiplied ARGB (0 = clear), or null.
     */
    public void present(int screenW, int screenH, int x, int y, int w, int h, int[] overlay) {
        gl.bindFramebuffer(Gl.FRAMEBUFFER, screenFramebuffer);
        gl.viewport(0, 0, screenW, screenH);
        gl.disable(Gl.DEPTH_TEST);
        gl.depthMask(false);
        gl.clearColor(0f, 0f, 0f, 1f);
        gl.clear(Gl.COLOR_BUFFER_BIT);
        float x0 = x * 2f / screenW - 1f, x1 = (x + w) * 2f / screenW - 1f;
        float y0 = 1f - (y + h) * 2f / screenH, y1 = 1f - y * 2f / screenH;
        gl.useProgram(blit.id);
        gl.uniform1i(blit.loc("uTex"), 0);
        gl.activeTexture(Gl.TEXTURE0);
        gl.bindTexture(Gl.TEXTURE_2D, fboTex);
        gl.uniform1i(blit.loc("uSwizzle"), 0);
        fullscreen(blit, x0, y0, x1, y1, false);
        if (overlay != null) {
            if (overlayBuf == null || overlayBuf.capacity() < W * H) overlayBuf = direct(W * H * 4).asIntBuffer();
            overlayBuf.clear();
            overlayBuf.put(overlay, 0, W * H).flip();
            gl.bindTexture(Gl.TEXTURE_2D, overlayTex);
            gl.texSubImage2D(Gl.TEXTURE_2D, 0, 0, 0, W, H, Gl.RGBA, Gl.UNSIGNED_BYTE, overlayBuf);
            gl.uniform1i(blit.loc("uSwizzle"), 1);
            gl.enable(Gl.BLEND);
            gl.blendFunc(Gl.ONE, Gl.ONE_MINUS_SRC_ALPHA);
            fullscreen(blit, x0, y0, x1, y1, true);
            gl.disable(Gl.BLEND);
        }
        gl.depthMask(true);
    }

    /** Shows a whole ARGB picture (a loading screen) scaled into the screen rectangle. */
    public void presentImage(int screenW, int screenH, int x, int y, int w, int h, int[] px, int pw, int ph) {
        if (!ready) init();
        gl.bindFramebuffer(Gl.FRAMEBUFFER, screenFramebuffer);
        gl.viewport(0, 0, screenW, screenH);
        gl.disable(Gl.DEPTH_TEST);
        gl.clearColor(0f, 0f, 0f, 1f);
        gl.clear(Gl.COLOR_BUFFER_BIT);
        if (imageTex == 0 || imageW != pw || imageH != ph) {
            if (imageTex != 0) gl.deleteTexture(imageTex);
            imageTex = newTexture(Gl.NEAREST, Gl.CLAMP_TO_EDGE);
            gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, pw, ph, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
            imageW = pw;
            imageH = ph;
        }
        IntBuffer b = direct(pw * ph * 4).asIntBuffer();
        b.put(px, 0, pw * ph).flip();
        gl.activeTexture(Gl.TEXTURE0);
        gl.bindTexture(Gl.TEXTURE_2D, imageTex);
        gl.texSubImage2D(Gl.TEXTURE_2D, 0, 0, 0, pw, ph, Gl.RGBA, Gl.UNSIGNED_BYTE, b);
        gl.useProgram(blit.id);
        gl.uniform1i(blit.loc("uTex"), 0);
        gl.uniform1i(blit.loc("uSwizzle"), 1);
        float x0 = x * 2f / screenW - 1f, x1 = (x + w) * 2f / screenW - 1f;
        float y0 = 1f - (y + h) * 2f / screenH, y1 = 1f - y * 2f / screenH;
        fullscreen(blit, x0, y0, x1, y1, true);
    }

    /** Reads the finished frame back as ARGB, top row first (tools and tests). */
    public void readFrame(int[] out) {
        gl.bindFramebuffer(Gl.FRAMEBUFFER, fbo);
        ByteBuffer b = direct(W * H * 4);
        gl.readPixels(0, 0, W, H, Gl.RGBA, Gl.UNSIGNED_BYTE, b);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int o = ((H - 1 - y) * W + x) * 4;
                out[y * W + x] = 0xFF000000 | ((b.get(o) & 255) << 16) | ((b.get(o + 1) & 255) << 8) | (b.get(o + 2) & 255);
            }
    }

    /** How many batches and tiles are in video memory now. */
    public String memoryReport() {
        return String.format(java.util.Locale.US, "%d batches, %d tiles, %d flat tiles, %d sprites, %.1f MB",
                batches.size(), tiles.size(), flats.size(), singles.size(), gpuBytes / 1048576.0);
    }
}
