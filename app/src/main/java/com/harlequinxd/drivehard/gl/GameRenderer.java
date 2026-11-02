package com.harlequinxd.drivehard.gl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.game.TestWorld;
import com.harlequinxd.drivehard.core.gfx.MeshData;
import com.harlequinxd.drivehard.core.gfx.ProcTextures;
import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.CharacterModel;
import com.harlequinxd.drivehard.core.model.CharacterSpec;
import com.harlequinxd.drivehard.core.sprite.SpriteBaker;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import com.harlequinxd.drivehard.input.TouchControls;

import java.nio.FloatBuffer;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renders the 3D world into a low-resolution offscreen buffer (PS1 style), upscales it with
 * nearest filtering, then draws the touch HUD at native resolution.
 */
public final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "DriveHard";
    /** Internal vertical resolution of the 3D view. */
    private static final int LOW_H = 240;
    private static final float[] FOG = {0.66f, 0.82f, 0.93f};

    private final TouchControls controls;
    private final TouchControls.State input = new TouchControls.State();

    // game state (GL thread only)
    private final TestWorld world = new TestWorld();
    private final Player player = new Player(world.spawnX, world.spawnZ);
    private final OrbitCamera cam = new OrbitCamera();
    private final float[] move = new float[2];
    private volatile SpriteSheet playerSheet;
    private volatile String bakeInfo = "baking sprites...";

    // GL objects
    private int worldProg, uMVP, uSnap, uCam, uFog, uTex, uDither, uFogCol, uTint, aPos, aUV, aCol;
    private int blitProg, bTex, bPos;
    private int worldVbo, worldVerts;
    private int atlasTex, ditherTex, shadowTex, spriteTex;
    private int fbo, fboTex, fboDepth, lowW, lowH;
    private int screenW = 1, screenH = 1;
    private final Hud hud = new Hud();
    private final FloatBuffer dyn = GlUtil.floats(6 * MeshData.STRIDE * 4);
    private final FloatBuffer blitQuad = GlUtil.floats(new float[]{-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1}, 12);

    private final float[] proj = new float[16], view = new float[16], mvp = new float[16];
    private long lastNanos;
    private int fpsFrames;
    private long fpsStart;
    private float fps;

    public GameRenderer(TouchControls controls) {
        this.controls = controls;
        Thread baker = new Thread(() -> {
            long t0 = SystemClock.elapsedRealtime();
            SpriteSheet s = new SpriteBaker().bake(new CharacterModel(CharacterSpec.player()));
            bakeInfo = "sprites baked in " + (SystemClock.elapsedRealtime() - t0) + " ms";
            Log.i(TAG, bakeInfo);
            playerSheet = s;
        }, "sprite-baker");
        baker.setPriority(Thread.NORM_PRIORITY);
        baker.start();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        worldProg = GlUtil.program(Shaders.WORLD_VS, Shaders.WORLD_FS);
        uMVP = GLES20.glGetUniformLocation(worldProg, "uMVP");
        uSnap = GLES20.glGetUniformLocation(worldProg, "uSnap");
        uCam = GLES20.glGetUniformLocation(worldProg, "uCam");
        uFog = GLES20.glGetUniformLocation(worldProg, "uFog");
        uTex = GLES20.glGetUniformLocation(worldProg, "uTex");
        uDither = GLES20.glGetUniformLocation(worldProg, "uDither");
        uFogCol = GLES20.glGetUniformLocation(worldProg, "uFogCol");
        uTint = GLES20.glGetUniformLocation(worldProg, "uTint");
        aPos = GLES20.glGetAttribLocation(worldProg, "aPos");
        aUV = GLES20.glGetAttribLocation(worldProg, "aUV");
        aCol = GLES20.glGetAttribLocation(worldProg, "aCol");

        blitProg = GlUtil.program(Shaders.BLIT_VS, Shaders.BLIT_FS);
        bTex = GLES20.glGetUniformLocation(blitProg, "uTex");
        bPos = GLES20.glGetAttribLocation(blitProg, "aPos");

        ProcTextures tex = new ProcTextures();
        atlasTex = GlUtil.texture(tex.pixels, ProcTextures.SIZE, ProcTextures.SIZE, false);
        ditherTex = GlUtil.bayerTexture();
        shadowTex = GlUtil.texture(shadowPixels(32), 32, 32, false);
        spriteTex = 0; // (re)uploaded once the bake finishes

        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        worldVbo = ids[0];
        MeshData m = world.mesh;
        worldVerts = m.vertexCount();
        FloatBuffer fb = GlUtil.floats(m.array(), m.floatCount());
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, worldVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, m.floatCount() * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        hud.init();
        fbo = 0;
        lastNanos = System.nanoTime();
        fpsStart = lastNanos;
    }

    private static int[] shadowPixels(int n) {
        int[] p = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = d >= 1f ? 0f : Math.min(1f, (1f - d) * 2.2f) * 0.6f;
                p[y * n + x] = ((int) (a * 255) << 24) | 0x101418;
            }
        }
        return p;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        screenW = w;
        screenH = h;
        controls.setSize(w, h);
        lowH = LOW_H;
        lowW = Math.max(2, Math.round(LOW_H * (float) w / h) & ~1);
        createFbo();
        Matrix.perspectiveM(proj, 0, 62f, (float) lowW / lowH, 0.1f, 120f);
    }

    private void createFbo() {
        int[] ids = new int[1];
        if (fbo != 0) {
            ids[0] = fbo; GLES20.glDeleteFramebuffers(1, ids, 0);
            ids[0] = fboTex; GLES20.glDeleteTextures(1, ids, 0);
            ids[0] = fboDepth; GLES20.glDeleteRenderbuffers(1, ids, 0);
        }
        GLES20.glGenTextures(1, ids, 0);
        fboTex = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, lowW, lowH, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GlUtil.params(false, false);

        GLES20.glGenRenderbuffers(1, ids, 0);
        fboDepth = ids[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, fboDepth);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, lowW, lowH);

        GLES20.glGenFramebuffers(1, ids, 0);
        fbo = ids[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, fboDepth);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) Log.e(TAG, "FBO incomplete: 0x" + Integer.toHexString(status));
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        fpsFrames++;
        if (now - fpsStart > 500_000_000L) {
            fps = fpsFrames * 1e9f / (now - fpsStart);
            fpsFrames = 0;
            fpsStart = now;
        }

        update(dt);

        if (spriteTex == 0 && playerSheet != null) {
            SpriteSheet s = playerSheet;
            spriteTex = GlUtil.texture(s.pixels, s.width, s.height, false);
        }

        // 1) world at low resolution
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, lowW, lowH);
        GLES20.glClearColor(FOG[0], FOG[1], FOG[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_BLEND);
        drawWorld();

        // 2) upscale to the screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenW, screenH);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(blitProg);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glUniform1i(bTex, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        blitQuad.position(0);
        GLES20.glVertexAttribPointer(bPos, 2, GLES20.GL_FLOAT, false, 8, blitQuad);
        GLES20.glEnableVertexAttribArray(bPos);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(bPos);

        // 3) HUD
        drawHud();
    }

    private void update(float dt) {
        controls.read(input);
        float k = 0.0055f / Math.max(1f, controls.density / 2.5f);
        cam.orbit(-input.camDX * k, input.camDY * k);
        cam.toWorld(input.stickX, input.stickY, move);
        player.update(dt, move[0], move[1], input.jump, world);
        cam.update(player.x, player.y, player.z, dt);
    }

    private void bindWorldAttribs(int vbo, FloatBuffer buf) {
        int stride = MeshData.STRIDE * 4;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        if (vbo != 0) {
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, 12);
            GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, stride, 20);
        } else {
            buf.position(0);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, buf);
            buf.position(3);
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, buf);
            buf.position(5);
            GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, stride, buf);
        }
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glEnableVertexAttribArray(aCol);
    }

    private void drawWorld() {
        Matrix.setLookAtM(view, 0, cam.eyeX, cam.eyeY, cam.eyeZ, cam.tx, cam.ty, cam.tz, 0f, 1f, 0f);
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);

        GLES20.glUseProgram(worldProg);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glUniform2f(uSnap, lowW * 0.5f, lowH * 0.5f);
        GLES20.glUniform3f(uCam, cam.eyeX, cam.eyeY, cam.eyeZ);
        GLES20.glUniform2f(uFog, 22f, 60f);
        GLES20.glUniform3f(uFogCol, FOG[0], FOG[1], FOG[2]);
        GLES20.glUniform3f(uTint, 1f, 1f, 1f);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform1i(uDither, 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ditherTex);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // static level
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex);
        bindWorldAttribs(worldVbo, null);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, worldVerts);

        // camera basis for billboards
        float fx = cam.tx - cam.eyeX, fy = cam.ty - cam.eyeY, fz = cam.tz - cam.eyeZ;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        float rx = -fz, rz = fx;                          // right = forward x worldUp
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        rx /= rl; rz /= rl;
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // up = right x forward

        // blob shadow on whatever is under the player
        float gy = world.groundHeight(player.x, player.z, Player.RADIUS, player.y + 0.01f, 0.01f);
        float air = Math.min(1f, (player.y - gy) / 3f);
        float sr = 0.36f * (1f - air * 0.4f);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowTex);
        dyn.position(0);
        flatQuad(player.x, gy + 0.015f, player.z, sr);
        bindWorldAttribs(0, dyn);
        GLES20.glDepthMask(false);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDepthMask(true);

        // player sprite
        SpriteSheet s = playerSheet;
        if (spriteTex != 0 && s != null) {
            int dir = s.directionFor(player.facing, cam.yaw);
            int col = Animations.firstFrame(player.anim) + player.animFrame;
            float u0 = (col * s.frameW) / (float) s.width, u1 = ((col + 1) * s.frameW) / (float) s.width;
            float v0 = (dir * s.frameH) / (float) s.height, v1 = ((dir + 1) * s.frameH) / (float) s.height;
            float ppm = s.pixelsPerMeter;
            float left = -s.anchorX / ppm, right = (s.frameW - s.anchorX) / ppm;
            float top = s.anchorY / ppm, bottom = -(s.frameH - s.anchorY) / ppm;
            // pull the billboard slightly toward the camera so it doesn't clip into walls behind
            float bx = player.x - fx * 0.3f, by = player.y, bz = player.z - fz * 0.3f;
            dyn.position(0);
            putV(bx + rx * left + ux * bottom, by + uy * bottom, bz + rz * left + uz * bottom, u0, v1);
            putV(bx + rx * right + ux * bottom, by + uy * bottom, bz + rz * right + uz * bottom, u1, v1);
            putV(bx + rx * right + ux * top, by + uy * top, bz + rz * right + uz * top, u1, v0);
            putV(bx + rx * left + ux * bottom, by + uy * bottom, bz + rz * left + uz * bottom, u0, v1);
            putV(bx + rx * right + ux * top, by + uy * top, bz + rz * right + uz * top, u1, v0);
            putV(bx + rx * left + ux * top, by + uy * top, bz + rz * left + uz * top, u0, v0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, spriteTex);
            bindWorldAttribs(0, dyn);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        }

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
        GLES20.glDisableVertexAttribArray(aCol);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void putV(float x, float y, float z, float u, float v) {
        dyn.put(x).put(y).put(z).put(u).put(v).put(1f).put(1f).put(1f);
    }

    private void flatQuad(float x, float y, float z, float r) {
        putV(x - r, y, z + r, 0, 1);
        putV(x + r, y, z + r, 1, 1);
        putV(x + r, y, z - r, 1, 0);
        putV(x - r, y, z + r, 0, 1);
        putV(x + r, y, z - r, 1, 0);
        putV(x - r, y, z - r, 0, 0);
    }

    private void drawHud() {
        hud.begin(screenW, screenH);
        float jr = controls.joyRadius();
        hud.ring(input.joyCX, input.joyCY, jr, input.joyActive ? 0.85f : 0.35f);
        hud.knob(input.knobX, input.knobY, jr * 0.42f, input.joyActive ? 0.9f : 0.4f);
        hud.jump(controls.jumpX(), controls.jumpY(), controls.jumpRadius(), input.jumpHeld ? 1f : 0.6f);
        hud.setText(String.format(Locale.US, "DRIVE HARD  %.0f fps  %dx%d  %s  %s",
                fps, lowW, lowH, player.anim.name(), playerSheet == null ? bakeInfo : ""));
        float ts = Math.max(1f, controls.density / 2f);
        hud.text(8f * controls.density, 6f * controls.density, ts);
        hud.end();
    }
}
