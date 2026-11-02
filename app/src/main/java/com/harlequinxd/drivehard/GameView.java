package com.harlequinxd.drivehard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.input.TouchControls;

import java.util.Locale;

/**
 * Presents the software renderer: the game draws into an int[] framebuffer at ~240p, which is
 * copied to a Bitmap and scaled up to the screen with nearest-neighbour sampling. The touch HUD
 * is drawn on top at native resolution. No OpenGL is used by the game itself.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "DriveHard";
    private static final int LOW_H = 240;

    private final TouchControls controls;
    private final TouchControls.State input = new TouchControls.State();
    private volatile Game game;
    private volatile String loading = "Starting";
    private Thread loop;
    private volatile boolean running;
    private int surfW, surfH;
    private volatile boolean sizeChanged;

    private Bitmap frame;
    private final Rect dst = new Rect();
    private final Paint blit = new Paint();
    private final Paint hud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private float fps, frameMs;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        controls = new TouchControls(context.getResources().getDisplayMetrics().density);
        blit.setFilterBitmap(false);
        blit.setAntiAlias(false);
        blit.setDither(false);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.MONOSPACE);
        text.setShadowLayer(3f, 1f, 1f, Color.BLACK);

        Thread loader = new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                Game g = new Game(1234L, s -> loading = s);
                Log.i(TAG, "loaded in " + (System.currentTimeMillis() - t0) + " ms");
                game = g;
            } catch (Throwable t) {
                Log.e(TAG, "load failed", t);
                loading = "Load failed: " + t;
            }
        }, "loader");
        loader.start();
    }

    // ------------------------------------------------------------------ surface lifecycle

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        loop = new Thread(this, "game-loop");
        loop.setPriority(Thread.MAX_PRIORITY);
        loop.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfW = width;
        surfH = height;
        controls.setSize(width, height);
        sizeChanged = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (loop != null) {
            try { loop.join(1000); } catch (InterruptedException ignored) { }
            loop = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        controls.onTouch(event);
        return true;
    }

    // ------------------------------------------------------------------ loop

    @Override
    public void run() {
        long last = System.nanoTime(), fpsStart = last;
        int fpsFrames = 0;
        float msAcc = 0;
        while (running) {
            Game g = game;
            if (g == null) {
                drawLoading();
                sleep(30);
                continue;
            }
            if (sizeChanged || frame == null) {
                if (surfW <= 0 || surfH <= 0) { sleep(16); continue; }
                sizeChanged = false;
                int lw = Math.max(2, Math.round(LOW_H * (float) surfW / surfH)) & ~1;
                g.setResolution(lw, LOW_H);
                frame = Bitmap.createBitmap(lw, LOW_H, Bitmap.Config.ARGB_8888);
                dst.set(0, 0, surfW, surfH);
            }
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;

            controls.read(input);
            float k = 0.0055f / Math.max(1f, controls.density / 2.5f);
            g.update(dt, input.stickX, input.stickY, -input.camDX * k, input.camDY * k, input.jump);
            long r0 = System.nanoTime();
            g.render();
            msAcc += (System.nanoTime() - r0) / 1e6f;
            frame.setPixels(g.renderer.fb, 0, g.renderer.W, 0, 0, g.renderer.W, g.renderer.H);

            Canvas c = lockCanvas();
            if (c == null) continue;
            try {
                c.drawBitmap(frame, null, dst, blit);
                drawHud(c, g);
            } finally {
                getHolder().unlockCanvasAndPost(c);
            }

            fpsFrames++;
            if (now - fpsStart > 500_000_000L) {
                fps = fpsFrames * 1e9f / (now - fpsStart);
                frameMs = msAcc / fpsFrames;
                fpsFrames = 0;
                msAcc = 0;
                fpsStart = now;
            }
        }
    }

    private Canvas lockCanvas() {
        SurfaceHolder h = getHolder();
        try {
            if (Build.VERSION.SDK_INT >= 26) return h.lockHardwareCanvas();
        } catch (Throwable ignored) { }
        return h.lockCanvas();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    private void drawLoading() {
        Canvas c = lockCanvas();
        if (c == null) return;
        try {
            c.drawColor(0xFF10141C);
            float d = controls.density;
            text.setTextSize(28 * d);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFF6FA8);
            c.drawText("DRIVE HARD", c.getWidth() / 2f, c.getHeight() / 2f - 10 * d, text);
            text.setTextSize(14 * d);
            text.setColor(Color.WHITE);
            c.drawText(loading + "...", c.getWidth() / 2f, c.getHeight() / 2f + 24 * d, text);
            text.setTextAlign(Paint.Align.LEFT);
        } finally {
            getHolder().unlockCanvasAndPost(c);
        }
    }

    private void drawHud(Canvas c, Game g) {
        float d = controls.density;
        // joystick
        float jr = controls.joyRadius();
        hud.setStyle(Paint.Style.FILL);
        hud.setColor(input.joyActive ? 0x40FFFFFF : 0x22FFFFFF);
        c.drawCircle(input.joyCX, input.joyCY, jr, hud);
        hud.setStyle(Paint.Style.STROKE);
        hud.setStrokeWidth(3 * d);
        hud.setColor(input.joyActive ? 0xD0FFFFFF : 0x70FFFFFF);
        c.drawCircle(input.joyCX, input.joyCY, jr, hud);
        hud.setStyle(Paint.Style.FILL);
        hud.setColor(input.joyActive ? 0xE6FFFFFF : 0x80FFFFFF);
        c.drawCircle(input.knobX, input.knobY, jr * 0.42f, hud);

        // jump button
        float bx = controls.jumpX(), by = controls.jumpY(), br = controls.jumpRadius();
        hud.setColor(input.jumpHeld ? 0x80FFFFFF : 0x40FFFFFF);
        c.drawCircle(bx, by, br, hud);
        hud.setStyle(Paint.Style.STROKE);
        hud.setColor(0xD0FFFFFF);
        c.drawCircle(bx, by, br, hud);
        hud.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(bx, by - br * 0.55f);
        arrow.lineTo(bx + br * 0.38f, by - br * 0.08f);
        arrow.lineTo(bx + br * 0.14f, by - br * 0.08f);
        arrow.lineTo(bx + br * 0.14f, by + br * 0.25f);
        arrow.lineTo(bx - br * 0.14f, by + br * 0.25f);
        arrow.lineTo(bx - br * 0.14f, by - br * 0.08f);
        arrow.lineTo(bx - br * 0.38f, by - br * 0.08f);
        arrow.close();
        c.drawPath(arrow, hud);
        text.setTextSize(11 * d);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("JUMP", bx, by + br * 0.62f, text);
        text.setTextAlign(Paint.Align.LEFT);

        // stats
        text.setTextSize(12 * d);
        c.drawText(String.format(Locale.US, "DRIVE HARD  %.0f fps  %.1f ms  %dx%d  %d tris  %s",
                fps, frameMs, g.renderer.W, g.renderer.H, g.renderer.statPrims, g.player.anim.name()),
                10 * d, 18 * d, text);
    }
}
